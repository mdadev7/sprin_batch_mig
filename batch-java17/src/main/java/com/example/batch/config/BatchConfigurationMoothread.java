package com.example.batch.config;

import com.example.batch.model.PersonCsv;
import com.example.batch.model.PersonRow;
import com.example.batch.processor.PersonRowProcessor;
import com.example.batch.reader.PersonCsvReaderFactory;
import com.example.batch.writer.PersonJdbcWriter;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.core.step.skip.LimitCheckingItemSkipPolicy;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.core.launch.support.RunIdIncrementer;

import java.util.HashMap;
import java.util.Map;

//@Configuration
public class BatchConfigurationMoothread {

    @Value("${batch.input-file}")
    private String inputFile;

    @Value("${batch.chunk-size:500}")
    private int chunkSize;

    @Value("${batch.skip-limit:100}")
    private int skipLimit;

    @Bean
    public Step migrationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            PersonRowProcessor processor,
            PersonJdbcWriter writer) {

        Map<Class<? extends Throwable>, Boolean> skippable =
                new HashMap<>();

        skippable.put(IllegalArgumentException.class, true);
        skippable.put(FlatFileParseException.class, true);

        SkipPolicy skipPolicy =
                new LimitCheckingItemSkipPolicy(
                        skipLimit, skippable);

        return new StepBuilder("migrationStep", jobRepository)
                .<PersonCsv, PersonRow>chunk(
                        chunkSize, transactionManager)
                .reader(PersonCsvReaderFactory.create(inputFile))
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipPolicy(skipPolicy)
                .build();
    }

    @Bean
    public Job migrationJob(
            JobRepository jobRepository,
            Step migrationStep) {

        return new JobBuilder("personMigrationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(migrationStep)
                .build();
    }
}
