package com.example.batch.runner;

import com.example.batch.config.BatchConfiguration;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class JobRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;

    private final Job migrationJob;

    public JobRunner(JobLauncher jobLauncher,
                     @Qualifier("migrationJob") Job migrationJob) {
        this.jobLauncher = jobLauncher;
        this.migrationJob = migrationJob;
    }

    @Override
    public void run(String... args) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(migrationJob, params);
        System.out.println("Job terminé avec le statut : "
                + execution.getStatus());
    }
}
