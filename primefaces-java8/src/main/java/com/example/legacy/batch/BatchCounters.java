package com.example.legacy.batch;

import java.io.Serializable;
import java.util.Locale;

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
        return safe(readSkip) + safe(processSkip) + safe(writeSkip);
    }

    /**
     * Taux de succès = lignes non rejetées / lignes lues, borné à [0, 100].
     * Le formatage utilise la locale française (virgule décimale) de façon
     * cohérente, y compris pour le cas particulier 100 %.
     */
    public String getSuccessRate() {
        if (readCount == null || readCount <= 0) {
            return String.format(Locale.FRANCE, "%.2f", 100.0d);
        }

        long total = readCount;
        long successful = total - getTotalSkips();
        if (successful < 0) {
            successful = 0;
        } else if (successful > total) {
            successful = total;
        }

        double rate = (double) successful / (double) total * 100.0d;
        return String.format(Locale.FRANCE, "%.2f", rate);
    }

    private static long safe(Long value) {
        return value != null ? value : 0L;
    }
}
