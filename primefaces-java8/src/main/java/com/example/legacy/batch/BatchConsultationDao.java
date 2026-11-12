package com.example.legacy.batch;

import javax.faces.bean.ManagedBean;
import javax.persistencecriteria.CriteriaBuilder;
import javax.persistence.CriteriaQuery;
import javax.persistence.EntityTransaction;
import javax.persistence.Predicate;
import javax.persistence.Root;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * DAO de consultation (lecture seule) des tables BATCH_* et person_rejection.
 * <p>
 * Crée son propre EntityManager via Persistence.createEntityManagerFactory("legacyPU")
 * car @PersistenceContext n'est pas disponible dans un @ManagedBean JSF pur
 * (sans conteneur EJB / Spring). Chaque méthode ouvre un contexte, exécute la requête,
 * puis ferme l'EntityManager.
 */
@ManagedBean(name = "batchConsultationDao")
public class BatchConsultationDao {

    private static final String PU = "legacyPU";

    private javax.persistence EntityManager getManager() {
        return javax.persistence.Persistence
                .createEntityManagerFactory(PU)
                .createEntityManager();
    }

    /**
     * Recherche paginée et filtrée des exécutions de batch.
     */
    public List<BatchJobExecution> findExecutions(
            Long jobExecutionId,
            String status,
            Date dateFrom,
            Date dateTo,
            String exitCode,
            String keyword,
            int first,
            int pageSize) {

        javax.persistence EntityManager em = getManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<BatchJobExecution> cq = cb.createQuery(BatchJobExecution.class);
            Root<BatchJobExecution> root = cq.from(BatchJobExecution.class);

            List<Predicate> predicates = buildPredicates(cb, root,
                    jobExecutionId, status, dateFrom, dateTo, exitCode, keyword);

            cq.where(predicates.toArray(new Predicate[0]));
            cq.orderBy(cb.desc(root.get("startTime")));

            TypedQuery<BatchJobExecution> query = em.createQuery(cq);
            query.setFirstResult(first);
            query.setMaxResults(pageSize);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Compte le nombre total d'exécutions correspondant aux filtres (pour pagination).
     */
    public long countExecutions(
            Long jobExecutionId,
            String status,
            Date dateFrom,
            Date dateTo,
            String exitCode,
            String keyword) {

        javax.persistence EntityManager em = getManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<BatchJobExecution> root = cq.from(BatchJobExecution.class);

            List<Predicate> predicates = buildPredicates(cb, root,
                    jobExecutionId, status, dateFrom, dateTo, exitCode, keyword);

            cq.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
            return em.createQuery(cq).getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * Retourne une exécution par son identifiant.
     */
    public BatchJobExecution findExecution(Long id) {
        javax.persistence EntityManager em = getManager();
        try {
            return em.find(BatchJobExecution.class, id);
        } finally {
            em.close();
        }
    }

    /**
     * Retourne les étapes d'une exécution, triées par heure de début.
     */
    public List<BatchStepExecution> findSteps(Long jobExecutionId) {
        javax.persistence EntityManager em = getManager();
        try {
            TypedQuery<BatchStepExecution> q = em.createQuery(
                "SELECT s FROM BatchStepExecution s " +
                "WHERE s.jobExecutionId = :id ORDER BY s.startTime",
                BatchStepExecution.class);
            q.setParameter("id", jobExecutionId);
            return q.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Calcule les compteurs agrégés pour une exécution.
     */
    public BatchCounters aggregate(Long jobExecutionId) {
        javax.persistence EntityManager em = getManager();
        try {
            Object[] r = (Object[]) em.createQuery(
                "SELECT COALESCE(SUM(s.readCount),0), " +
                "       COALESCE(SUM(s.writeCount),0), " +
                "       COALESCE(SUM(s.readSkipCount),0), " +
                "       COALESCE(SUM(s.processSkipCount),0), " +
                "       COALESCE(SUM(s.writeSkipCount),0), " +
                "       COALESCE(SUM(s.commitCount),0), " +
                "       COALESCE(SUM(s.rollbackCount),0) " +
                "FROM BatchStepExecution s WHERE s.jobExecutionId = :id")
                .setParameter("id", jobExecutionId)
                .getSingleResult();

            BatchCounters c = new BatchCounters();
            c.setReadCount(((Number) r[0]).longValue());
            c.setWriteCount(((Number) r[1]).longValue());
            c.setReadSkip(((Number) r[2]).longValue());
            c.setProcessSkip(((Number) r[3]).longValue());
            c.setWriteSkip(((Number) r[4]).longValue());
            c.setCommits(((Number) r[5]).longValue());
            c.setRollbacks(((Number) r[6]).longValue());
            return c;
        } finally {
            em.close();
        }
    }

    /**
     * Retourne les rejets d'une exécution.
     */
    public List<PersonRejection> findRejections(Long jobExecutionId) {
        javax.persistence EntityManager em = getManager();
        try {
            TypedQuery<PersonRejection> q = em.createQuery(
                "SELECT r FROM PersonRejection r " +
                "WHERE r.jobExecutionId = :id ORDER BY r.rejectionDate DESC",
                PersonRejection.class);
            q.setParameter("id", jobExecutionId);
            return q.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Retourne les rejets d'une étape spécifique.
     */
    public List<PersonRejection> findRejectionsByStep(Long jobExecutionId, String stepName) {
        javax.persistence EntityManager em = getManager();
        try {
            TypedQuery<PersonRejection> q = em.createQuery(
                "SELECT r FROM PersonRejection r " +
                "WHERE r.jobExecutionId = :id AND r.stepName = :step " +
                "ORDER BY r.rejectionDate DESC",
                PersonRejection.class);
            q.setParameter("id", jobExecutionId);
            q.setParameter("step", stepName);
            return q.getResultList();
        } finally {
            em.close();
        }
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<BatchJobExecution> root,
            Long jobExecutionId,
            String status,
            Date dateFrom,
            Date dateTo,
            String exitCode,
            String keyword) {

        List<Predicate> predicates = new ArrayList<>();

        if (jobExecutionId != null) {
            predicates.add(cb.equal(root.get("id"), jobExecutionId));
        }
        if (status != null && !status.isEmpty()) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (dateFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), dateFrom));
        }
        if (dateTo != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), dateTo));
        }
        if (exitCode != null && !exitCode.isEmpty()) {
            predicates.add(cb.equal(root.get("exitCode"), exitCode));
        }
        if (keyword != null && !keyword.isEmpty()) {
            predicates.add(cb.like(
                cb.lower(root.get("exitMessage")),
                "%" + keyword.toLowerCase() + "%"
            ));
        }
        return predicates;
    }
}