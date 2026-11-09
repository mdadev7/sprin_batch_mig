package com.example.legacy.bean;

import com.example.legacy.batch.BatchConsultationDao;
import com.example.legacy.batch.BatchCounters;
import com.example.legacy.batch.BatchJobExecution;
import com.example.legacy.batch.BatchStepExecution;
import com.example.legacy.batch.PersonRejection;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@ManagedBean(name = "batchDetailBean")
@ViewScoped
public class BatchDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    @ManagedProperty(value = "#{batchConsultationDao}")
    private BatchConsultationDao dao;

    private Long id;
    private BatchJobExecution execution;
    private List<BatchStepExecution> steps;
    private BatchCounters counters;
    private List<PersonRejection> rejections;

    @SuppressWarnings("unchecked")
    @javax.annotation.PostConstruct
    public void init() {
        Map<String, String> params =
            FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap();
        String p = params.get("id");
        if (p != null && !p.isEmpty()) {
            try {
                id = Long.valueOf(p);
                execution = dao.findExecution(id);
                if (execution != null) {
                    steps = dao.findSteps(id);
                    counters = dao.aggregate(id);
                    rejections = dao.findRejections(id);
                }
            } catch (NumberFormatException ignored) {
                // id invalide -> page vide
            }
        }
    }

    public String backToList() {
        return "/batch-executions.xhtml?faces-redirect=true";
    }

    public String getDuration() {
        Long seconds = execution != null ? execution.getDurationSeconds() : null;
        if (seconds == null) {
            return "—";
        }
        return seconds + " s";
    }

    public String getStartTimeFormatted() {
        if (execution == null || execution.getStartTime() == null) {
            return "—";
        }
        return DATE_FMT.format(execution.getStartTime());
    }

    public String getEndTimeFormatted() {
        if (execution == null || execution.getEndTime() == null) {
            return "—";
        }
        return DATE_FMT.format(execution.getEndTime());
    }

    public String severityOf(String status) {
        if (status == null) return "info";
        switch (status) {
            case "COMPLETED": return "success";
            case "FAILED":    return "danger";
            case "STOPPED":   return "warning";
            case "STARTED":   return "info";
            default:          return "info";
        }
    }

    public String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public boolean hasRejections() {
        return rejections != null && !rejections.isEmpty();
    }

    // ---------- Getters / Setters ----------

    public void setDao(BatchConsultationDao dao) { this.dao = dao; }

    public Long getId() { return id; }

    public BatchJobExecution getExecution() { return execution; }

    public List<BatchStepExecution> getSteps() {
        return steps != null ? steps : java.util.Collections.emptyList();
    }

    public BatchCounters getCounters() {
        return counters != null ? counters : new BatchCounters();
    }

    public List<PersonRejection> getRejections() {
        return rejections != null ? rejections : java.util.Collections.emptyList();
    }
}
