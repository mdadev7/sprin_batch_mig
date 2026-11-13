package com.dev.listener;

import com.dev.model.AttestationRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class AttestationSkipListener
        implements SkipListener<AttestationRow, AttestationRow>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AttestationSkipListener.class);
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNew;
    private Long jobExecutionId;
    private String stepName;

    public AttestationSkipListener(JdbcTemplate jdbcTemplate,
                                  PlatformTransactionManager txManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public void beforeStep(StepExecution s) {
        this.jobExecutionId = s.getJobExecutionId();
        this.stepName = s.getStepName();
    }
    @Override public ExitStatus afterStep(StepExecution s) { return null; }

    @Override public void onSkipInRead(Throwable t) {
        String raw = (t instanceof org.springframework.batch.item.file.FlatFileParseException f)
                ? f.getInput() : null;
        insert(raw, t.getMessage());
    }
    @Override public void onSkipInProcess(AttestationRow item, Throwable t) {
        insert(item != null ? item.toString() : null, t.getMessage());
    }
    @Override public void onSkipInWrite(AttestationRow item, Throwable t) {
        insert(item != null ? item.toString() : null, t.getMessage());
    }

    private void insert(String rawContent, String reason) {
        if (jobExecutionId == null || stepName == null) {
            log.warn("Rejet non persisté : {}", reason); return;
        }
        try {
            requiresNew.executeWithoutResult(st ->
                jdbcTemplate.update(
                    "INSERT INTO attestation_rejection (job_execution_id, step_name, raw_content, reason) " +
                    "VALUES (?, ?, ?, ?)",
                    jobExecutionId, stepName, rawContent, reason));
            log.info("Rejet persisté : step={} raison={}", stepName, reason);
        } catch (Exception e) {
            log.error("Impossible d'enregistrer le rejet [{}] : {}", reason, e.getMessage(), e);
        }
    }
}
