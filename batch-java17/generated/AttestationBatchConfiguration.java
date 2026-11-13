package com.dev.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.ScopeConfiguration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.LimitCheckingItemSkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.dev.listener.AttestationSkipListener;
import com.dev.model.AttestationRow;
import com.dev.partitioner.LineRangePartitioner;
import com.dev.processor.AttestationRowProcessor;
import com.dev.reader.LineRangeAttestationReader;
import com.dev.writer.AttestationJdbcWriter;

@Configuration
@Import(ScopeConfiguration.class)
public class AttestationBatchConfiguration {

    @Value("${batch.input-file}") private String inputFile;
    @Value("${batch.chunk-size:500}") private int chunkSize;
    @Value("${batch.grid-size:8}") private int gridSize;

    @Bean
    public TaskExecutor attestationPartitionTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(gridSize); ex.setMaxPoolSize(gridSize);
        ex.setQueueCapacity(0); ex.setThreadNamePrefix("attestation-part-");
        ex.initialize(); return ex;
    }

    @Bean @StepScope @Scope(value = "step", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public AttestationSkipListener attestationSkipListener(JdbcTemplate jdbcTemplate,
                                                  PlatformTransactionManager txManager) {
        return new AttestationSkipListener(jdbcTemplate, txManager);
    }

    @Bean @StepScope
    public LineRangeAttestationReader attestationReader(
            @Value("#{stepExecutionContext['startLine']}") Integer startLine,
            @Value("#{stepExecutionContext['endLine']}") Integer endLine) {
        int s = startLine != null ? startLine : 2;
        int e = endLine != null ? endLine : Integer.MAX_VALUE;
        return new LineRangeAttestationReader(inputFile, s, e);
    }

    @Bean
    public Step attestationWorkerStep(JobRepository jobRepository,
                             PlatformTransactionManager txManager,
                             AttestationRowProcessor processor,
                             AttestationJdbcWriter writer,
                             AttestationSkipListener skipListener) {
        Map<Class<? extends Throwable>, Boolean> skippable = new HashMap<>();
        skippable.put(IllegalArgumentException.class, true);
        skippable.put(FlatFileParseException.class, true);
        skippable.put(DuplicateKeyException.class, true);
        SkipPolicy skipPolicy = new LimitCheckingItemSkipPolicy(Integer.MAX_VALUE, skippable);

        return new StepBuilder("AttestationWorkerStep", jobRepository)
                .<Attestation, AttestationRow>chunk(chunkSize, txManager)
                .reader(AttestationRowReader(null, null))
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipPolicy(skipPolicy)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Step attestationMasterStep(JobRepository jobRepository,
                             LineRangePartitioner partitioner,
                             @Qualifier("attestationWorkerStep") Step workerStep,
                             @Qualifier("attestationPartitionTaskExecutor") TaskExecutor executor) {
        return new StepBuilder("attestationMasterStep", jobRepository)
                .partitioner("attestationWorkerStep", partitioner)
                .step(workerStep).gridSize(gridSize).taskExecutor(executor).build();
    }

    @Bean
    public Job attestationMigrationJob(JobRepository jobRepository,
                              @Qualifier("attestationMasterStep") Step masterStep) {
        return new JobBuilder("attestationMigrationJob", jobRepository)
                .incrementer(new RunIdIncrementer()).start(masterStep).build();
    }
}
