package com.example.legacy.batch;

import java.io.Serializable;

public class BatchCounters implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long readCount;
    private Long writeCount;
    private Long readSkip;
    private Long processSkip;
    private Long writeSkip;
    private Long commits;
    private Long rollbacks;

    public Long getReadCount() { return readCount; }
    public void setReadCount(Long readCount) { this.readCount = readCount; }

    public Long getWriteCount() { return writeCount; }
    public void setWriteCount(Long writeCount) { this.writeCount = writeCount; }

    public Long getReadSkip() { return readSkip; }
    public void setReadSkip(Long readSkip) { this.readSkip = readSkip; }

    public Long getProcessSkip() { return processSkip; }
    public void setProcessSkip(Long processSkip) { this.processSkip = processSkip; }

    public Long getWriteSkip() { return writeSkip; }
    public void setWriteSkip(Long writeSkip) { this.writeSkip = writeSkip; }

    public Long getCommits() { return commits; }
    public void setCommits(Long commits) { this.commits = commits; }

    public Long getRollbacks() { return rollbacks; }
    public void setRollbacks(Long rollbacks) { this.rollbacks = rollbacks; }

    public Long getTotalSkips() {
        Long r = readSkip != null ? readSkip : 0L;
        Long p = processSkip != null ? processSkip : 0L;
        Long w = writeSkip != null ? writeSkip : 0L;
        return r + p + w;
    }

    public String getSuccessRate() {
        if (readCount == null || readCount == 0) {
            return "100,00";
        }
        Long total = readCount;
        Long written = writeCount != null ? writeCount : 0L;
        Long skipped = getTotalSkips();
        Long success = total - skipped;
        double rate = (double) success / (double) total * 100.0;
        return String.format("%.2f", rate);
    }
}
