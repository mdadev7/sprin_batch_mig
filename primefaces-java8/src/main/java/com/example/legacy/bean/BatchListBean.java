package com.example.legacy.bean;

import com.example.legacy.batch.BatchConsultationDao;
import com.example.legacy.batch.BatchJobExecution;

import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backing bean de la liste des exécutions de batch.
 * <p>
 * Bean déclaré dans {@code faces-config.xml} (scope view) : pas d'annotations
 * {@code @ManagedBean}/{@code @ViewScoped} pour éviter un double enregistrement.
 * La pagination et le tri sont délégués au DAO via un {@link LazyDataModel}
 * (chargement côté serveur).
 */
public class BatchListBean extends LazyDataModel<BatchJobExecution>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private BatchConsultationDao dao;

    // ---------- Filtres ----------
    private String jobExecutionIdFilter;
    private String statusFilter;
    private Date dateFromFilter;
    private Date dateToFilter;
    private String exitCodeFilter;
    private String keywordFilter;

    // ---------- Pagination ----------
    private int first = 0;
    private long totalCount;

    // ---------- Données ----------
    private List<BatchJobExecution> executions = Collections.emptyList();
    private BatchJobExecution selected;

    /**
     * Renseigné lorsque la base ne peut pas être interrogée : la page reste
     * affichable et l'erreur est présentée à l'utilisateur au lieu d'une
     * exception JSF brute.
     */
    private String loadErrorMessage;

    private Map<String, Boolean> sortOrder = defaultSortOrder();

    public BatchListBean() {
        // LazyDataModel#setRowIndex calcule rowIndex % pageSize : la taille de page
        // héritée doit donc être initialisée, sinon aucune ligne n'est rendue.
        super.setPageSize(DEFAULT_PAGE_SIZE);
    }

    @PostConstruct
    public void init() {
        totalCount = countMatching();
        setRowCount(toRowCount(totalCount));
    }

    // ---------- LazyDataModel ----------

    @Override
    public List<BatchJobExecution> load(int first, int pageSize,
                                        Map<String, SortMeta> sortBy,
                                        Map<String, FilterMeta> filterBy) {

        this.first = Math.max(first, 0);
        if (pageSize > 0) {
            super.setPageSize(pageSize);
        }
        this.sortOrder = adaptSortOrder(sortBy);

        if (dao == null) {
            this.executions = Collections.emptyList();
            setRowCount(0);
            return this.executions;
        }

        Long id = parseId();
        try {
            this.executions = dao.findExecutions(
                    id,
                    trimToNull(statusFilter),
                    dateFromFilter,
                    dateToFilter,
                    trimToNull(exitCodeFilter),
                    trimToNull(keywordFilter),
                    this.first,
                    getPageSize(),
                    this.sortOrder);
            this.loadErrorMessage = null;
        } catch (RuntimeException e) {
            // Base momentanément indisponible : la page reste affichable.
            this.executions = Collections.emptyList();
            this.loadErrorMessage = "Consultation de la base impossible : " + e.getMessage();
            setRowCount(0);
            return this.executions;
        }

        this.totalCount = dao.countExecutions(
                id,
                trimToNull(statusFilter),
                dateFromFilter,
                dateToFilter,
                trimToNull(exitCodeFilter),
                trimToNull(keywordFilter));
        setRowCount(toRowCount(totalCount));

        return this.executions;
    }

    @Override
    public String getRowKey(BatchJobExecution execution) {
        if (execution == null || execution.getId() == null) {
            return null;
        }
        return execution.getId().toString();
    }

    @Override
    public BatchJobExecution getRowData(String rowKey) {
        if (rowKey == null || executions == null) {
            return null;
        }
        for (BatchJobExecution execution : executions) {
            if (execution != null && rowKey.equals(String.valueOf(execution.getId()))) {
                return execution;
            }
        }
        return null;
    }

    /**
     * Exposé au XHTML : {@code value="#{batchListBean.lazyModel}"}.
     */
    public LazyDataModel<BatchJobExecution> getLazyModel() {
        return this;
    }

    // ---------- Actions ----------

    public void refresh() {
        resetPaging();
    }

    public void filter() {
        resetPaging();
    }

    public void clearFilters() {
        jobExecutionIdFilter = null;
        statusFilter = null;
        dateFromFilter = null;
        dateToFilter = null;
        exitCodeFilter = null;
        keywordFilter = null;
        resetPaging();
    }

    public void onRowSelect() {
        // no-op : sert à activer le bouton « Détail »
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

    private void resetPaging() {
        first = 0;
        totalCount = countMatching();
        setRowCount(toRowCount(totalCount));
    }

    private long countMatching() {
        if (dao == null) {
            return 0L;
        }
        try {
            long count = dao.countExecutions(
                    parseId(),
                    trimToNull(statusFilter),
                    dateFromFilter,
                    dateToFilter,
                    trimToNull(exitCodeFilter),
                    trimToNull(keywordFilter));
            loadErrorMessage = null;
            return count;
        } catch (RuntimeException e) {
            // Base indisponible : on n'empêche pas l'affichage de la page.
            loadErrorMessage = "Consultation de la base impossible : " + e.getMessage();
            return 0L;
        }
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

    // ---------- Utilitaires ----------

    private Long parseId() {
        String value = trimToNull(jobExecutionIdFilter);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int toRowCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private static Map<String, Boolean> defaultSortOrder() {
        Map<String, Boolean> order = new LinkedHashMap<>();
        order.put("startTime", Boolean.FALSE);
        return order;
    }

    private static Map<String, Boolean> adaptSortOrder(Map<String, SortMeta> sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultSortOrder();
        }

        List<SortMeta> metas = new ArrayList<>(sortBy.values());
        Collections.sort(metas);

        Map<String, Boolean> order = new LinkedHashMap<>();
        for (SortMeta meta : metas) {
            SortOrder direction = meta.getOrder();
            if (direction == null || direction.isUnsorted()) {
                continue;
            }
            order.put(meta.getField(), direction.isAscending());
        }
        return order.isEmpty() ? defaultSortOrder() : order;
    }

    // ---------- Getters / Setters ----------

    public void setDao(BatchConsultationDao dao) { this.dao = dao; }

    public String getLoadErrorMessage() { return loadErrorMessage; }

    public List<BatchJobExecution> getExecutions() { return executions; }

    public String getJobExecutionIdFilter() { return jobExecutionIdFilter; }
    public void setJobExecutionIdFilter(String jobExecutionIdFilter) {
        this.jobExecutionIdFilter = jobExecutionIdFilter;
    }

    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) {
        this.statusFilter = statusFilter;
    }

    public Date getDateFromFilter() { return dateFromFilter; }
    public void setDateFromFilter(Date dateFromFilter) {
        this.dateFromFilter = dateFromFilter;
    }

    public Date getDateToFilter() { return dateToFilter; }
    public void setDateToFilter(Date dateToFilter) {
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

    public int getFirst() { return first; }
    public void setFirst(int first) {
        this.first = Math.max(first, 0);
    }

    public long getTotalCount() { return totalCount; }

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
        long end = (long) first + executions.size();
        return end > totalCount ? (int) totalCount : (int) end;
    }

    public BatchJobExecution getSelected() { return selected; }
    public void setSelected(BatchJobExecution selected) { this.selected = selected; }
}
