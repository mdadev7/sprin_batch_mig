package com.example.batch.config;

import com.example.batch.listener.PersonSkipListener;
import com.example.batch.model.PersonCsv;
import com.example.batch.model.PersonRow;
import com.example.batch.partitioner.LineRangePartitioner;
import com.example.batch.processor.PersonRowProcessor;
import com.example.batch.reader.LineRangeFlatFileReader;
import com.example.batch.writer.PersonJdbcWriter;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.LimitCheckingItemSkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class BatchConfiguration {

    @Value("${batch.input-file}")
    private String inputFile;

    @Value("${batch.chunk-size:500}")
    private int chunkSize;

    @Value("${batch.skip-limit:100}")
    private int skipLimit;

    @Value("${batch.grid-size:8}")
    private int gridSize;

    // ---------- Threads de partitionnement ----------

    @Bean
    public TaskExecutor partitionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(gridSize);
        executor.setMaxPoolSize(gridSize);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("partition-worker-");
        executor.initialize();
        return executor;
    }

    // ---------- Worker step (une plage de lignes) ----------

    @Bean
    public Step workerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            PersonRowProcessor processor,
            PersonJdbcWriter writer,
            PersonSkipListener skipListener) {

        Map<Class<? extends Throwable>, Boolean> skippable = new HashMap<>();
        skippable.put(IllegalArgumentException.class, true);
        skippable.put(FlatFileParseException.class, true);
        // RG3 / RG10 : un id déjà présent (dans le fichier ou en base) viole la
        // contrainte de clé primaire. La ligne est rejetée et tracée, sans
        // interrompre le job.
        skippable.put(DuplicateKeyException.class, true);

        SkipPolicy skipPolicy =
                new LimitCheckingItemSkipPolicy(skipLimit, skippable);

        return new StepBuilder("workerStep", jobRepository)
                .<PersonCsv, PersonRow>chunk(chunkSize, transactionManager)
                .reader(lineRangeReader(null, null))
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipPolicy(skipPolicy)
                .listener(skipListener)
                .build();
    }

    // ---------- Master step (orchestrateur) ----------

    @Bean
    public Step masterStep(
            JobRepository jobRepository,
            LineRangePartitioner partitioner,
            Step workerStep,
            TaskExecutor partitionTaskExecutor) {

        return new StepBuilder("masterStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .step(workerStep)
                .gridSize(gridSize)
                .taskExecutor(partitionTaskExecutor)
                .build();
    }

    // ---------- Reader @StepScope (reçoit startLine/endLine) ----------

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public LineRangeFlatFileReader lineRangeReader(
            @Value("#{stepExecutionContext['startLine']}") Integer startLine,
            @Value("#{stepExecutionContext['endLine']}") Integer endLine) {
        int s = (startLine != null) ? startLine : 1;
        int e = (endLine != null) ? endLine : Integer.MAX_VALUE;
        return new LineRangeFlatFileReader(inputFile, s, e);
    }

    // ---------- Job ----------

    @Bean
    public Job migrationJob(
            JobRepository jobRepository,
            Step masterStep) {

        return new JobBuilder("personMigrationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(masterStep)
                .build();
    }
}