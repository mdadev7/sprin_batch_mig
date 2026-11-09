-- V3__person_rejection.sql
CREATE TABLE person_rejection (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    job_execution_id BIGINT       NOT NULL,
    step_name        VARCHAR(100) NOT NULL,
    partition_id     VARCHAR(50),
    line_number      BIGINT,
    raw_content      TEXT,
    reason           VARCHAR(1000),
    rejection_date   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT FK_REJECTION_JOB_EXEC
        FOREIGN KEY (job_execution_id)
        REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX IDX_REJECTION_JOB ON person_rejection(job_execution_id);
CREATE INDEX IDX_REJECTION_STEP ON person_rejection(step_name);
CREATE INDEX IDX_REJECTION_DATE ON person_rejection(rejection_date);
