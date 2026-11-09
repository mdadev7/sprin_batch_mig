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
@Table(name = "BATCH_STEP_EXECUTION")
public class BatchStepExecution implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "STEP_EXECUTION_ID")
    private Long id;

    @Column(name = "VERSION")
    private Long version;

    @Column(name = "STEP_NAME")
    private String stepName;

    @Column(name = "JOB_EXECUTION_ID")
    private Long jobExecutionId;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "COMMIT_CODE")
    private String commitCode;

    @Column(name = "EXIT_CODE")
    private String exitCode;

    @Column(name = "EXIT_MESSAGE")
    private String exitMessage;

    @Column(name = "READ_COUNT")
    private Long readCount;

    @Column(name = "FILTER_COUNT")
    private Long filterCount;

    @Column(name = "WRITE_COUNT")
    private Long writeCount;

    @Column(name = "READ_SKIP_COUNT")
    private Long readSkipCount;

    @Column(name = "WRITE_SKIP_COUNT")
    private Long writeSkipCount;

    @Column(name = "PROCESS_SKIP_COUNT")
    private Long processSkipCount;

    @Column(name = "ROLLBACK_COUNT")
    private Long rollbackCount;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "START_TIME")
    private Date startTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "END_TIME")
    private Date endTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "LAST_UPDATED")
    private Date lastUpdated;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public Long getJobExecutionId() { return jobExecutionId; }
    public void setJobExecutionId(Long jobExecutionId) { this.jobExecutionId = jobExecutionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCommitCode() { return commitCode; }
    public void setCommitCode(String commitCode) { this.commitCode = commitCode; }

    public String getExitCode() { return exitCode; }
    public void setExitCode(String exitCode) { this.exitCode = exitCode; }

    public String getExitMessage() { return exitMessage; }
    public void setExitMessage(String exitMessage) { this.exitMessage = exitMessage; }

    public Long getReadCount() { return readCount; }
    public void setReadCount(Long readCount) { this.readCount = readCount; }

    public Long getFilterCount() { return filterCount; }
    public void setFilterCount(Long filterCount) { this.filterCount = filterCount; }

    public Long getWriteCount() { return writeCount; }
    public void setWriteCount(Long writeCount) { this.writeCount = writeCount; }

    public Long getReadSkipCount() { return readSkipCount; }
    public void setReadSkipCount(Long readSkipCount) { this.readSkipCount = readSkipCount; }

    public Long getWriteSkipCount() { return writeSkipCount; }
    public void setWriteSkipCount(Long writeSkipCount) { this.writeSkipCount = writeSkipCount; }

    public Long getProcessSkipCount() { return processSkipCount; }
    public void setProcessSkipCount(Long processSkipCount) { this.processSkipCount = processSkipCount; }

    public Long getRollbackCount() { return rollbackCount; }
    public void setRollbackCount(Long rollbackCount) { this.rollbackCount = rollbackCount; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }

    public Long getDurationSeconds() {
        if (startTime == null || endTime == null) {
            return null;
        }
        return (endTime.getTime() - startTime.getTime()) / 1000;
    }

    public Long getTotalSkips() {
        Long r = readSkipCount != null ? readSkipCount : 0L;
        Long p = processSkipCount != null ? processSkipCount : 0L;
        Long w = writeSkipCount != null ? writeSkipCount : 0L;
        return r + p + w;
    }
}
