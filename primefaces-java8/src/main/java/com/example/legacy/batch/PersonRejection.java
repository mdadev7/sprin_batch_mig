package com.example.legacy.batch;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "person_rejection")
public class PersonRejection implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "partition_id")
    private String partitionId;

    @Column(name = "line_number")
    private Long lineNumber;

    @Column(name = "raw_content")
    private String rawContent;

    @Column(name = "reason")
    private String reason;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "rejection_date")
    private Date rejectionDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getJobExecutionId() { return jobExecutionId; }
    public void setJobExecutionId(Long jobExecutionId) { this.jobExecutionId = jobExecutionId; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getPartitionId() { return partitionId; }
    public void setPartitionId(String partitionId) { this.partitionId = partitionId; }

    public Long getLineNumber() { return lineNumber; }
    public void setLineNumber(Long lineNumber) { this.lineNumber = lineNumber; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Date getRejectionDate() { return rejectionDate; }
    public void setRejectionDate(Date rejectionDate) { this.rejectionDate = rejectionDate; }
}
