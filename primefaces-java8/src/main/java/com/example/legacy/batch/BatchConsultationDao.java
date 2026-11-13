package com.example.legacy.batch;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DAO de consultation (lecture seule) des tables BATCH_* et person_rejection.
 * <p>
 * L'{@link EntityManagerFactory} est créé une seule fois pour toute l'application
 * (coûteux et thread-safe) ; seuls les {@link EntityManager} sont ouverts/fermés
 * à chaque opération. La fermeture de la fabrique est assurée par
 * {@code PersistenceContextListener} à l'arrêt du contexte web.
 */
public class BatchConsultationDao {

    private static final String PU = "legacyPU";

    /** Propriétés sur lesquelles le tri est autorisé (protection contre l'injection JPQL). */
    private static final Set<String> SORTABLE_FIELDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("id", "status", "startTime", "endTime", "exitCode")));

    /**
     * Fabrique paresseuse : une panne de base au démarrage ne doit pas rendre la
     * classe définitivement inutilisable (échec de l'initialisation statique) et
     * l'application doit pouvoir réessayer une fois la base revenue.
     */
    private static volatile EntityManagerFactory emf;

    private static EntityManagerFactory entityManagerFactory() {
        EntityManagerFactory local = emf;
        if (local == null) {
            synchronized (BatchConsultationDao.class) {
                local = emf;
                if (local == null) {
                    local = Persistence.createEntityManagerFactory(PU);
                    emf = local;
                }
            }
        }
        return local;
    }

    private EntityManager getManager() {
        return entityManagerFactory().createEntityManager();
    }

    /**
     * Ferme la fabrique au shutdown du contexte web (hot redeploy, arrêt du serveur).
     * Sans effet si elle n'a jamais été créée.
     */
    public static void close() {
        EntityManagerFactory local = emf;
        emf = null;
        if (local != null && local.isOpen()) {
            local.close();
        }
    }

    /**
     * Recherche paginée et filtrée des exécutions de batch, triée par défaut
     * sur {@code startTime} décroissant.
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
        return findExecutions(jobExecutionId, status, dateFrom, dateTo,
                exitCode, keyword, first, pageSize, null);
    }

    /**
     * Recherche paginée et filtrée.
     *
     * @param sortOrder champ technique de l'entité (id, status, startTime, endTime,
     *                  exitCode) associé à {@code true} (ascendant) ou {@code false}
     *                  (descendant). Les champs inconnus sont ignorés.
     */
    public List<BatchJobExecution> findExecutions(
            Long jobExecutionId,
            String status,
            Date dateFrom,
            Date dateTo,
            String exitCode,
            String keyword,
            int first,
            int pageSize,
            Map<String, Boolean> sortOrder) {

        EntityManager em = getManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<BatchJobExecution> cq = cb.createQuery(BatchJobExecution.class);
            Root<BatchJobExecution> root = cq.from(BatchJobExecution.class);

            List<Predicate> predicates = buildPredicates(cb, root,
                    jobExecutionId, status, dateFrom, dateTo, exitCode, keyword);

            cq.where(predicates.toArray(new Predicate[0]));
            cq.orderBy(buildOrders(cb, root, sortOrder));

            TypedQuery<BatchJobExecution> query = em.createQuery(cq);
            query.setFirstResult(Math.max(first, 0));
            query.setMaxResults(pageSize > 0 ? pageSize : 20);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Compte le nombre total d'exécutions correspondant aux filtres (pagination).
     */
    public long countExecutions(
            Long jobExecutionId,
            String status,
            Date dateFrom,
            Date dateTo,
            String exitCode,
            String keyword) {

        EntityManager em = getManager();
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
        EntityManager em = getManager();
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
        EntityManager em = getManager();
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
        EntityManager em = getManager();
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
        EntityManager em = getManager();
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
        EntityManager em = getManager();
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

    private List<Order> buildOrders(
            CriteriaBuilder cb,
            Root<BatchJobExecution> root,
            Map<String, Boolean> sortOrder) {

        List<Order> orders = new ArrayList<>();
        if (sortOrder != null) {
            for (Map.Entry<String, Boolean> entry : sortOrder.entrySet()) {
                String field = entry.getKey();
                if (field == null || !SORTABLE_FIELDS.contains(field)) {
                    continue;
                }
                boolean ascending = Boolean.TRUE.equals(entry.getValue());
                orders.add(ascending ? cb.asc(root.get(field)) : cb.desc(root.get(field)));
            }
        }
        if (orders.isEmpty()) {
            orders.add(cb.desc(root.get("startTime")));
            orders.add(cb.desc(root.get("id")));
        }
        return orders;
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

        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null) {
            predicates.add(cb.equal(root.get("status"), normalizedStatus));
        }

        if (dateFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), dateFrom));
        }
        if (dateTo != null) {
            // La date choisie dans le calendrier est à 00:00 : on inclut toute la journée.
            predicates.add(cb.lessThan(root.get("startTime"), startOfNextDay(dateTo)));
        }

        String normalizedExitCode = trimToNull(exitCode);
        if (normalizedExitCode != null) {
            predicates.add(cb.equal(root.get("exitCode"), normalizedExitCode));
        }

        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            String escaped = normalizedKeyword.toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            predicates.add(cb.like(
                    cb.lower(root.get("exitMessage")),
                    "%" + escaped + "%",
                    '\\'));
        }
        return predicates;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Date startOfNextDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }
}
