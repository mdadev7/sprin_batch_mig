package com.example.batch.listener;

import com.example.batch.model.PersonCsv;
import com.example.batch.model.PersonRow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

/**
 * Enregistre chaque ligne rejetée dans {@code person_rejection}.
 * <p>
 * Le listener est un singleton partagé par les 8 workers : il ne conserve donc
 * aucun état d'étape. L'{@link StepExecution} courante est lue à chaque appel
 * depuis {@link StepSynchronizationManager} (thread-local du worker), ce qui
 * garantit l'absence de mélange entre partitions.
 */
@Component
public class PersonSkipListener implements SkipListener<PersonCsv, Object> {

    private static final Logger log = LoggerFactory.getLogger(PersonSkipListener.class);

    /** person_rejection.reason est un VARCHAR(1000). */
    private static final int MAX_REASON_LENGTH = 1000;

    /** person_rejection.raw_content est un TEXT (64 Ko max). */
    private static final int MAX_RAW_LENGTH = 60_000;

    private final JdbcTemplate jdbcTemplate;

    public PersonSkipListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        insertRejection(null, null, reasonOf(t));
    }

    @Override
    public void onSkipInProcess(PersonCsv item, Throwable t) {
        if (item == null) {
            insertRejection(null, null, reasonOf(t));
            return;
        }
        insertRejection(item.getLineNumber(), toCsvLine(item), reasonOf(t));
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        if (item instanceof PersonRow) {
            PersonRow row = (PersonRow) item;
            insertRejection(row.getLineNumber(), toCsvLine(row), reasonOf(t));
        } else if (item instanceof PersonCsv) {
            PersonCsv csv = (PersonCsv) item;
            insertRejection(csv.getLineNumber(), toCsvLine(csv), reasonOf(t));
        } else {
            insertRejection(null, item != null ? item.toString() : null, reasonOf(t));
        }
    }

    private void insertRejection(Long lineNumber, String rawContent, String reason) {
        StepExecution stepExecution = currentStepExecution();
        if (stepExecution == null) {
            log.warn("Rejet non enregistré (hors exécution d'étape) : {}", reason);
            return;
        }

        Long jobExecutionId = stepExecution.getJobExecutionId();
        String stepName = stepExecution.getStepName();
        String partitionId = stepExecution.getExecutionContext().getString("partitionId");
        if (partitionId == null) {
            partitionId = stepName;
        }

        try {
            jdbcTemplate.update(
                "INSERT INTO person_rejection " +
                "(job_execution_id, step_name, partition_id, line_number, raw_content, reason) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                jobExecutionId, stepName, partitionId,
                lineNumber != null ? lineNumber : 0L,
                truncate(rawContent, MAX_RAW_LENGTH),
                truncate(reason, MAX_REASON_LENGTH)
            );
        } catch (Exception e) {
            // Ne jamais faire échouer le batch à cause de l'enregistrement du rejet.
            log.error("Impossible d'enregistrer le rejet (jobExecutionId={}, step={}) : {}",
                    jobExecutionId, stepName, e.getMessage());
        }
    }

    private static StepExecution currentStepExecution() {
        if (StepSynchronizationManager.getContext() == null) {
            return null;
        }
        return StepSynchronizationManager.getContext().getStepExecution();
    }

    private static String reasonOf(Throwable t) {
        if (t == null) {
            return "Erreur inconnue";
        }
        String message = t.getMessage();
        return (message == null || message.trim().isEmpty())
                ? t.getClass().getSimpleName()
                : message;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String toCsvLine(PersonCsv item) {
        StringJoiner joiner = new StringJoiner(";");
        joiner.add(item.getId() != null ? String.valueOf(item.getId()) : "");
        joiner.add(item.getNom() != null ? item.getNom() : "");
        joiner.add(item.getPrenom() != null ? item.getPrenom() : "");
        joiner.add(item.getSalaire() != null ? item.getSalaire() : "");
        joiner.add(item.getDateNaissance() != null ? item.getDateNaissance() : "");
        joiner.add(item.getVille() != null ? item.getVille() : "");
        return joiner.toString();
    }

    private static String toCsvLine(PersonRow row) {
        StringJoiner joiner = new StringJoiner(";");
        joiner.add(row.getId() != null ? String.valueOf(row.getId()) : "");
        joiner.add(row.getNom() != null ? row.getNom() : "");
        joiner.add(row.getPrenom() != null ? row.getPrenom() : "");
        // Le salaire est déjà chiffré ici : on n'écrit jamais le clair en base.
        joiner.add(row.getSalaireEncrypted() != null ? row.getSalaireEncrypted() : "");
        joiner.add(row.getDateNaissance() != null ? row.getDateNaissance().toString() : "");
        joiner.add(row.getVille() != null ? row.getVille() : "");
        return joiner.toString();
    }
}
