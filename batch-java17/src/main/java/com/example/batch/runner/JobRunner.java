package com.example.batch.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Lance le job de migration au démarrage de l'application.
 * <p>
 * Spring Boot n'exécute plus automatiquement les jobs
 * ({@code spring.batch.job.enabled=false}) : ce runner est l'unique point de
 * lancement, ce qui évite une double exécution. Il peut être désactivé avec
 * {@code batch.run-on-startup=false} (ou {@code --batch.run-on-startup=false})
 * pour vérifier le démarrage sans lancer le traitement.
 */
@Component
@ConditionalOnProperty(name = "batch.run-on-startup", havingValue = "true", matchIfMissing = true)
public class JobRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

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

        log.info("Lancement du job '{}' avec les paramètres {}", migrationJob.getName(), params);
        JobExecution execution = jobLauncher.run(migrationJob, params);
        log.info("Job '{}' terminé avec le statut {} (exitCode={})",
                migrationJob.getName(), execution.getStatus(), execution.getExitStatus().getExitCode());

        if (execution.getStatus() == BatchStatus.FAILED) {
            // Code de sortie non nul : indispensable pour un batch planifié.
            throw new IllegalStateException("Le job '" + migrationJob.getName()
                    + "' a échoué : " + execution.getExitStatus().getExitDescription());
        }
    }
}
