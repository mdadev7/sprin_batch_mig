package com.example.legacy.bean;

import com.example.legacy.batch.BatchConsultationDao;
import com.example.legacy.batch.BatchCounters;
import com.example.legacy.batch.BatchJobExecution;
import com.example.legacy.batch.BatchStepExecution;
import com.example.legacy.batch.PersonRejection;

import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Backing bean du détail d'une exécution de batch.
 * <p>
 * Bean déclaré dans {@code faces-config.xml} (scope view) : pas d'annotations
 * {@code @ManagedBean}/{@code @ViewScoped} pour éviter un double enregistrement.
 */
public class BatchDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String DATE_PATTERN = "dd/MM/yyyy HH:mm:ss";

    private BatchConsultationDao dao;

    private Long id;
    private BatchJobExecution execution;
    private List<BatchStepExecution> steps = Collections.emptyList();
    private BatchCounters counters = new BatchCounters();
    private List<PersonRejection> rejections = Collections.emptyList();

    /** Renseigné si la base est indisponible (affiché par la vue). */
    private String loadErrorMessage;

    @PostConstruct
    public void init() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap();
        String rawId = params.get("id");
        if (rawId == null || rawId.trim().isEmpty() || dao == null) {
            return;
        }

        try {
            id = Long.valueOf(rawId.trim());
            execution = dao.findExecution(id);
            if (execution != null) {
                List<BatchStepExecution> loadedSteps = dao.findSteps(id);
                if (loadedSteps != null) {
                    steps = loadedSteps;
                }

                BatchCounters loadedCounters = dao.aggregate(id);
                if (loadedCounters != null) {
                    counters = loadedCounters;
                }

                List<PersonRejection> loadedRejections = dao.findRejections(id);
                if (loadedRejections != null) {
                    rejections = loadedRejections;
                }
            }
        } catch (NumberFormatException ignored) {
            // id invalide -> page vide (message affiché par la vue)
        } catch (RuntimeException e) {
            // Base momentanément indisponible : on affiche un message plutôt
            // qu'une page d'erreur JSF, et l'utilisateur peut revenir à la liste.
            execution = null;
            steps = Collections.emptyList();
            counters = new BatchCounters();
            rejections = Collections.emptyList();
            loadErrorMessage = "Consultation de la base impossible : " + e.getMessage();
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
        return format(execution.getStartTime());
    }

    public String getEndTimeFormatted() {
        if (execution == null || execution.getEndTime() == null) {
            return "—";
        }
        return format(execution.getEndTime());
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

    /**
     * Un {@link SimpleDateFormat} n'est pas thread-safe : on en crée un par appel.
     */
    private static String format(Date date) {
        return new SimpleDateFormat(DATE_PATTERN).format(date);
    }

    // ---------- Getters / Setters ----------

    public void setDao(BatchConsultationDao dao) { this.dao = dao; }

    public Long getId() { return id; }

    public String getLoadErrorMessage() { return loadErrorMessage; }

    public BatchJobExecution getExecution() { return execution; }

    public List<BatchStepExecution> getSteps() { return steps; }

    public BatchCounters getCounters() { return counters; }

    public List<PersonRejection> getRejections() { return rejections; }
}
