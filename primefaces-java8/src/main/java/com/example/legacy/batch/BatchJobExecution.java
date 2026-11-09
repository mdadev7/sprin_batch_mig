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
@Table(name = "BATCH_JOB_EXECUTION")
public class BatchJobExecution implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "JOB_EXECUTION_ID")
    private Long id;

    @Column(name = "VERSION")
    private Long version;

    @Column(name = "JOB_INSTANCE_ID")
    private Long jobInstanceId;

    @Column(name = "JOB_NAME")
    private String jobName;

    @Column(name = "JOB_KEY")
    private String jobKey;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "EXIT_CODE")
    private String exitCode;

    @Column(name = "EXIT_MESSAGE")
    private String exitMessage;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATE_TIME")
    private Date createTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "START_TIME")
    private Date startTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "END_TIME")
    private Date endTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "LAST_UPDATED")
    private Date lastUpdated;

    @Column(name = "JOB_CONFIGURATION_LOCATION")
    private String jobConfigurationLocation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Long getJobInstanceId() { return jobInstanceId; }
    public void setJobInstanceId(Long jobInstanceId) { this.jobInstanceId = jobInstanceId; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getJobKey() { return jobKey; }
    public void setJobKey(String jobKey) { this.jobKey = jobKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExitCode() { return exitCode; }
    public void setExitCode(String exitCode) { this.exitCode = exitCode; }

    public String getExitMessage() { return exitMessage; }
    public void setExitMessage(String exitMessage) { this.exitMessage = exitMessage; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getJobConfigurationLocation() { return jobConfigurationLocation; }
    public void setJobConfigurationLocation(String jobConfigurationLocation) {
        this.jobConfigurationLocation = jobConfigurationLocation;
    }

    public Long getDurationSeconds() {
        if (startTime == null || endTime == null) {
            return null;
        }
        return (endTime.getTime() - startTime.getTime()) / 1000;
    }
}
