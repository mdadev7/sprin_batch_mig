package com.example.batch.listener;

import com.example.batch.model.PersonCsv;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

@Component
public class PersonSkipListener
        implements SkipListener<PersonCsv, Object>, StepExecutionListener {

    private final JdbcTemplate jdbcTemplate;

    private Long jobExecutionId;
    private String stepName;
    private String partitionId;

    public PersonSkipListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.jobExecutionId = stepExecution.getJobExecution().getId();
        this.stepName = stepExecution.getStepName();
        // Récupère l'ID de partition depuis le contexte d'exécution
        this.partitionId = stepExecution.getExecutionContext()
                .getString("partitionId");
        if (this.partitionId == null) {
            this.partitionId = stepExecution.getStepName();
        }
    }

    @Override
    public void onSkipInRead(Throwable t) {
        insertRejection(null, null, null, t.getMessage());
    }

    @Override
    public void onSkipInProcess(PersonCsv item, Throwable t) {
        if (item == null) {
            insertRejection(null, null, null, t.getMessage());
            return;
        }
        String rawContent = toCsvLine(item);
        Long lineNo = item.getLineNumber();
        insertRejection(lineNo != null ? lineNo : 0, rawContent, null, t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        String rawContent = item != null ? item.toString() : null;
        insertRejection(null, rawContent, null, t.getMessage());
    }

    private void insertRejection(Long lineNumber, String rawContent,
                                 String partitionId, String reason) {
        if (jobExecutionId == null || stepName == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                "INSERT INTO person_rejection " +
                "(job_execution_id, step_name, partition_id, line_number, raw_content, reason) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                jobExecutionId, stepName, partitionId,
                lineNumber != null ? lineNumber : 0,
                rawContent, reason
            );
        } catch (Exception e) {
            // Ne jamais faire échouer le batch à cause de l'enregistrement du rejet
            System.err.println("Impossible d'enregistrer le rejet : " + e.getMessage());
        }
    }

    private String toCsvLine(PersonCsv item) {
        StringJoiner joiner = new StringJoiner(";");
        joiner.add(String.valueOf(item.getId()));
        joiner.add(item.getNom() != null ? item.getNom() : "");
        joiner.add(item.getPrenom() != null ? item.getPrenom() : "");
        joiner.add(item.getSalaire() != null ? item.getSalaire() : "");
        joiner.add(item.getDateNaissance() != null ? item.getDateNaissance() : "");
        joiner.add(item.getVille() != null ? item.getVille() : "");
        return joiner.toString();
    }
}
