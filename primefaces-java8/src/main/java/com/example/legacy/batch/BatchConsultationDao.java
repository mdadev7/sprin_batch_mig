package com.example.legacy.batch;

import javax.faces.bean.ManagedBean;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@ManagedBean(name = "batchConsultationDao")
public class BatchConsultationDao {

    @PersistenceContext(unitName = "legacyPU")
    private EntityManager em;

    /**
     * Recherche paginée et filtrée des exécutions de batch.
     *
     * @param jobExecutionId filtre par identifiant d'exécution (exact, optionnel)
     * @param status         filtre par statut (exact, optionnel)
     * @param dateFrom       date de début (inclusive, optionnel)
     * @param dateTo         date de fin (inclusive, optionnel)
     * @param exitCode       filtre par code de sortie (exact, optionnel)
     * @param keyword        mot-clé recherché dans EXIT_MESSAGE (contient, optionnel)
     * @param first          index du premier résultat (pagination)
     * @param pageSize       nombre de résultats par page
     * @return liste des exécutions correspondant aux filtres
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

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BatchJobExecution> cq = cb.createQuery(BatchJobExecution.class);
        Root<BatchJobExecution> root = cq.from(BatchJobExecution.class);

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

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("startTime")));

        TypedQuery<BatchJobExecution> query = em.createQuery(cq);
        query.setFirstResult(first);
        query.setMaxResults(pageSize);
        return query.getResultList();
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

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<BatchJobExecution> root = cq.from(BatchJobExecution.class);

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

        cq.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
        return em.createQuery(cq).getSingleResult();
    }

    /**
     * Retourne une exécution par son identifiant.
     */
    public BatchJobExecution findExecution(Long id) {
        return em.find(BatchJobExecution.class, id);
    }

    /**
     * Retourne les étapes d'une exécution, triées par heure de début.
     */
    public List<BatchStepExecution> findSteps(Long jobExecutionId) {
        TypedQuery<BatchStepExecution> q = em.createQuery(
            "SELECT s FROM BatchStepExecution s " +
            "WHERE s.jobExecutionId = :id ORDER BY s.startTime",
            BatchStepExecution.class);
        q.setParameter("id", jobExecutionId);
        return q.getResultList();
    }

    /**
     * Calcule les compteurs agrégés pour une exécution.
     */
    public BatchCounters aggregate(Long jobExecutionId) {
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
    }

    /**
     * Retourne les rejets d'une exécution.
     */
    public List<PersonRejection> findRejections(Long jobExecutionId) {
        TypedQuery<PersonRejection> q = em.createQuery(
            "SELECT r FROM PersonRejection r " +
            "WHERE r.jobExecutionId = :id ORDER BY r.rejectionDate DESC",
            PersonRejection.class);
        q.setParameter("id", jobExecutionId);
        return q.getResultList();
    }

    /**
     * Retourne les rejets d'une étape spécifique.
     */
    public List<PersonRejection> findRejectionsByStep(Long jobExecutionId, String stepName) {
        TypedQuery<PersonRejection> q = em.createQuery(
            "SELECT r FROM PersonRejection r " +
            "WHERE r.jobExecutionId = :id AND r.stepName = :step " +
            "ORDER BY r.rejectionDate DESC",
            PersonRejection.class);
        q.setParameter("id", jobExecutionId);
        q.setParameter("step", stepName);
        return q.getResultList();
    }
}
