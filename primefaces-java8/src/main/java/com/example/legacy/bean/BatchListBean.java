package com.example.legacy.bean;

import com.example.legacy.batch.BatchConsultationDao;
import com.example.legacy.batch.BatchCounters;
import com.example.legacy.batch.BatchJobExecution;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@ManagedBean(name = "batchListBean")
@ViewScoped
public class BatchListBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM/yyyy");

    @ManagedProperty(value = "#{batchConsultationDao}")
    private BatchConsultationDao dao;

    // ---------- Filtres ----------
    private String jobExecutionIdFilter;
    private String statusFilter;
    private String dateFromFilter;
    private String dateToFilter;
    private String exitCodeFilter;
    private String keywordFilter;

    // ---------- Pagination ----------
    private int page = 1;
    private int pageSize = 20;
    private int first = 0;
    private long totalCount;
    private int totalPages;

    // ---------- Données ----------
    private List<BatchJobExecution> executions;
    private BatchJobExecution selected;

    public void init() {
        refresh();
    }

    public void refresh() {
        applyFilter();
    }

    public void filter() {
        page = 1;
        first = 0;
        applyFilter();
    }

    private void applyFilter() {
        Long idFilter = null;
        if (jobExecutionIdFilter != null && !jobExecutionIdFilter.trim().isEmpty()) {
            try {
                idFilter = Long.valueOf(jobExecutionIdFilter.trim());
            } catch (NumberFormatException e) {
                idFilter = null;
            }
        }

        Date dateFrom = parseDate(dateFromFilter);
        Date dateTo = parseDate(dateToFilter);

        executions = dao.findExecutions(
            idFilter,
            statusFilter,
            dateFrom,
            dateTo,
            exitCodeFilter,
            keywordFilter,
            first,
            pageSize
        );

        totalCount = dao.countExecutions(
            idFilter,
            statusFilter,
            dateFrom,
            dateTo,
            exitCodeFilter,
            keywordFilter
        );

        totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages == 0) totalPages = 1;
    }

    private Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return DATE_FMT.parse(value.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    public void nextPage() {
        if (page < totalPages) {
            page++;
            first = (page - 1) * pageSize;
            applyFilter();
        }
    }

    public void previousPage() {
        if (page > 1) {
            page--;
            first = (page - 1) * pageSize;
            applyFilter();
        }
    }

    public void firstPage() {
        if (page != 1) {
            page = 1;
            first = 0;
            applyFilter();
        }
    }

    public void lastPage() {
        if (page != totalPages) {
            page = totalPages;
            first = (totalPages - 1) * pageSize;
            applyFilter();
        }
    }

    public boolean isHasNextPage() {
        return page < totalPages;
    }

    public boolean isHasPreviousPage() {
        return page > 1;
    }

    public int getStartRecord() {
        if (executions == null || executions.isEmpty()) {
            return 0;
        }
        return first + 1;
    }

    public int getEndRecord() {
        if (executions == null || executions.isEmpty()) {
            return 0;
        }
        return Math.min(first + executions.size(), (int) totalCount);
    }

    public String viewDetail(Long id) {
        return "/batch-execution-detail.xhtml?faces-redirect=true&id=" + id;
    }

    public String viewSelected() {
        if (selected == null) {
            return null;
        }
        return viewDetail(selected.getId());
    }

    public void onRowSelect() {
        // no-op, sert à activer le bouton "Détail"
    }

    public void clearFilters() {
        jobExecutionIdFilter = null;
        statusFilter = null;
        dateFromFilter = null;
        dateToFilter = null;
        exitCodeFilter = null;
        keywordFilter = null;
        page = 1;
        first = 0;
        applyFilter();
    }

    // ---------- Helpers d'affichage ----------

    public String durationOf(BatchJobExecution e) {
        Long seconds = e.getDurationSeconds();
        if (seconds == null) {
            return "—";
        }
        return seconds + " s";
    }

    public String severityOf(String status) {
        if (status == null) {
            return "info";
        }
        switch (status) {
            case "COMPLETED": return "success";
            case "FAILED":    return "danger";
            case "STOPPED":   return "warning";
            case "STARTED":   return "info";
            default:          return "info";
        }
    }

    public String statusLabel(String status) {
        if (status == null) return "—";
        switch (status) {
            case "COMPLETED": return "Terminé";
            case "FAILED":    return "Échec";
            case "STOPPED":   return "Arrêté";
            case "STARTED":   return "En cours";
            default:          return status;
        }
    }

    public String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ---------- Getters / Setters ----------

    public void setDao(BatchConsultationDao dao) { this.dao = dao; }

    public List<BatchJobExecution> getExecutions() { return executions; }

    public String getJobExecutionIdFilter() { return jobExecutionIdFilter; }
    public void setJobExecutionIdFilter(String jobExecutionIdFilter) {
        this.jobExecutionIdFilter = jobExecutionIdFilter;
    }

    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) {
        this.statusFilter = statusFilter;
    }

    public String getDateFromFilter() {
        return dateFromFilter != null ? DATE_FMT.format(dateFromFilter) : null;
    }
    public void setDateFromFilter(String dateFromFilter) {
        this.dateFromFilter = dateFromFilter;
    }

    public String getDateToFilter() {
        return dateToFilter != null ? DATE_FMT.format(dateToFilter) : null;
    }
    public void setDateToFilter(String dateToFilter) {
        this.dateToFilter = dateToFilter;
    }

    public String getExitCodeFilter() { return exitCodeFilter; }
    public void setExitCodeFilter(String exitCodeFilter) {
        this.exitCodeFilter = exitCodeFilter;
    }

    public String getKeywordFilter() { return keywordFilter; }
    public void setKeywordFilter(String keywordFilter) {
        this.keywordFilter = keywordFilter;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        this.first = (page - 1) * pageSize;
    }

    public int getFirst() { return first; }

    public long getTotalCount() { return totalCount; }

    public int getTotalPages() { return totalPages; }

    public BatchJobExecution getSelected() { return selected; }
    public void setSelected(BatchJobExecution selected) { this.selected = selected; }

    public String[] getPageSizeOptions() {
        return new String[]{"10", "20", "50", "100"};
    }
}
