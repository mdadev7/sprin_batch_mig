Spec Java

Excellente idée — c'est la suite logique. Spring Batch expose tout dans ses tables BATCH_*, et on peut construire une UI de consultation (écran de suivi) sans toucher au code métier. Voici une proposition complète, alignée sur votre stack existante (PrimeFaces Java 8 côté legacy ou une page Spring Boot côté batch).
1. Ce que les tables Spring Batch contiennent

Les tables utiles pour la consultation :
Table	Contenu
BATCH_JOB_INSTANCE	Une ligne par "job logique" (identifié par ses paramètres identifiants)
BATCH_JOB_EXECUTION	Une ligne par exécution (avec STATUS, START_TIME, END_TIME, EXIT_CODE, EXIT_MESSAGE)
BATCH_JOB_EXECUTION_PARAMS	Paramètres passés au job (run.id, etc.)
BATCH_STEP_EXECUTION	Une ligne par step (dont chaque workerStep:partition-N) avec READ_COUNT, WRITE_COUNT, COMMIT_COUNT, FILTER_COUNT, READ_SKIP_COUNT, PROCESS_SKIP_COUNT, WRITE_SKIP_COUNT
BATCH_STEP_EXECUTION_CONTEXT	Contexte sérialisé (peu utile en direct)
BATCH_JOB_EXECUTION_CONTEXT	Idem

Pour votre cas (994k lignes, 8 partitions), une exécution produit :

    1 ligne dans BATCH_JOB_EXECUTION

    1 ligne masterStep + 8 lignes workerStep:partition-N dans BATCH_STEP_EXECUTION

C'est exactement ce qu'il faut afficher : vue agrégée par job + détail par step + compteurs de rejets.
2. Architecture proposée

Deux options selon l'endroit où vous voulez l'UI :
Option A — Intégrer dans primefaces-java8 (recommandé)

Vous avez déjà une UI PrimeFaces/JSF connectée à la base dotations_db. Ajouter deux pages de consultation est trivial :

    batch-executions.xhtml : liste des exécutions

    batch-execution-detail.xhtml : détail d'une exécution (steps, compteurs, rejets)

Avantages : pas de nouvelle app, même base, même auth (si vous en avez), même charte.
Option B — Ajouter un petit module Spring Boot Admin

Un module batch-admin (Spring Boot + Thymeleaf ou simple REST + page HTML) qui lit les tables BATCH_*. Plus isolé, mais duplique l'authentification et l'infra.

Je recommande l'Option A — c'est le moins coûteux.
3. Modèle de données côté Java (JPA, dans primefaces-java8)

Créez trois entités en lecture seule (pas de @Transactional en écriture) :
java

package com.example.legacy.batch;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "BATCH_JOB_EXECUTION")
public class BatchJobExecution {

    @Id
    @Column(name = "JOB_EXECUTION_ID")
    private Long id;

    @Column(name = "JOB_INSTANCE_ID")
    private Long jobInstanceId;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "EXIT_CODE")
    private String exitCode;

    @Column(name = "EXIT_MESSAGE")
    private String exitMessage;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    // getters / setters
}

java

@Entity
@Table(name = "BATCH_STEP_EXECUTION")
public class BatchStepExecution {

    @Id
    @Column(name = "STEP_EXECUTION_ID")
    private Long id;

    @Column(name = "STEP_NAME")
    private String stepName;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "READ_COUNT")
    private Long readCount;

    @Column(name = "WRITE_COUNT")
    private Long writeCount;

    @Column(name = "COMMIT_COUNT")
    private Long commitCount;

    @Column(name = "ROLLBACK_COUNT")
    private Long rollbackCount;

    @Column(name = "READ_SKIP_COUNT")
    private Long readSkipCount;

    @Column(name = "PROCESS_SKIP_COUNT")
    private Long processSkipCount;

    @Column(name = "WRITE_SKIP_COUNT")
    private Long writeSkipCount;

    @Column(name = "FILTER_COUNT")
    private Long filterCount;

    @Column(name = "EXIT_CODE")
    private String exitCode;

    @Column(name = "EXIT_MESSAGE")
    private String exitMessage;

    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    @Column(name = "JOB_EXECUTION_ID")
    private Long jobExecutionId;

    // getters / setters
}

    ⚠️ Les noms de colonnes Oracle/MariaDB sont en majuscules, mais JPA est insensible à la casse sur les identifiants non quotés. Vérifiez avec @Column(name = "...") exact si votre provider est strict.

4. DAO de consultation (JPA EntityManager)
java

package com.example.legacy.batch;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

public class BatchConsultationDao {

    @PersistenceContext(unitName = "legacyPU")
    private EntityManager em;

    /** Les 50 dernières exécutions, les plus récentes d'abord. */
    public List<BatchJobExecution> findRecentExecutions(int limit) {
        TypedQuery<BatchJobExecution> q = em.createQuery(
            "SELECT e FROM BatchJobExecution e ORDER BY e.startTime DESC",
            BatchJobExecution.class);
        q.setMaxResults(limit);
        return q.getResultList();
    }

    /** Détail des steps d'une exécution. */
    public List<BatchStepExecution> findSteps(Long jobExecutionId) {
        return em.createQuery(
            "SELECT s FROM BatchStepExecution s " +
            "WHERE s.jobExecutionId = :id ORDER BY s.startTime",
            BatchStepExecution.class)
            .setParameter("id", jobExecutionId)
            .getResultList();
    }

    /** Compteurs agrégés (somme des workers). */
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
}

Et une classe BatchCounters (POJO) avec getTotalSkips() = readSkip + processSkip + writeSkip, getSuccessRate() = writeCount / readCount.
5. Bean JSF — vue liste
java

package com.example.legacy.bean;

import com.example.legacy.batch.BatchConsultationDao;
import com.example.legacy.batch.BatchJobExecution;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import java.util.List;

@ManagedBean(name = "batchListBean")
@ViewScoped
public class BatchListBean {

    @ManagedProperty(value = "#{batchConsultationDao}")
    private BatchConsultationDao dao;

    private List<BatchJobExecution> executions;

    @PostConstruct
    public void init() {
        executions = dao.findRecentExecutions(50);
    }

    public List<BatchJobExecution> getExecutions() { return executions; }
    public void setDao(BatchConsultationDao dao) { this.dao = dao; }

    public String viewDetail(Long jobExecutionId) {
        return "/batch-execution-detail.xhtml?faces-redirect=true&id=" + jobExecutionId;
    }
}

    Si vous êtes en CDI (@Named), adaptez les annotations, mais la logique est identique.

6. Vue XHTML — liste des exécutions
xml

<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="http://java.sun.com/jsf/html"
      xmlns:p="http://primefaces.org/ui">
<h:head>
    <title>Exécutions de batch</title>
</h:head>
<h:body>
    <h:form>
        <p:dataTable value="#{batchListBean.executions}" var="e"
                     rows="20" paginator="true"
                     emptyMessage="Aucune exécution">
            <p:column headerText="ID">#{e.id}</p:column>
            <p:column headerText="Statut">
                <p:badge value="#{e.status}"
                         severity="#{e.status == 'COMPLETED' ? 'success' : (e.status == 'FAILED' ? 'danger' : 'warning')}"/>
            </p:column>
            <p:column headerText="Début">#{e.startTime}</p:column>
            <p:column headerText="Fin">#{e.endTime}</p:column>
            <p:column headerText="Code sortie">#{e.exitCode}</p:column>
            <p:column headerText="">
                <p:commandButton value="Détail" icon="pi pi-search"
                                 action="#{batchListBean.viewDetail(e.id)}"
                                 ajax="false"/>
            </p:column>
        </p:dataTable>
    </h:form>
</h:body>
</html>

7. Bean JSF — vue détail
java

@ManagedBean(name = "batchDetailBean")
@ViewScoped
public class BatchDetailBean implements Serializable {

    @ManagedProperty(value = "#{batchConsultationDao}")
    private BatchConsultationDao dao;

    @ManagedProperty(value = "#{param.id}")
    private Long id;

    private BatchJobExecution execution;
    private List<BatchStepExecution> steps;
    private BatchCounters counters;

    @PostConstruct
    public void init() {
        if (id != null) {
            execution = dao.findExecution(id);
            steps = dao.findSteps(id);
            counters = dao.aggregate(id);
        }
    }

    // getters / setters
}

8. Vue XHTML — détail d'une exécution
xml

<h:body>
    <h:form>
        <p:panel header="Exécution ##{batchDetailBean.execution.id}">
            <p:outputPanel layout="block" styleClass="counters">
                <p><strong>Statut :</strong> #{batchDetailBean.execution.status}</p>
                <p><strong>Début :</strong> #{batchDetailBean.execution.startTime}</p>
                <p><strong>Fin :</strong> #{batchDetailBean.execution.endTime}</p>
                <p><strong>Lignes lues :</strong> #{batchDetailBean.counters.readCount}</p>
                <p><strong>Lignes écrites :</strong> #{batchDetailBean.counters.writeCount}</p>
                <p><strong>Rejets (total) :</strong> #{batchDetailBean.counters.totalSkips}</p>
                <ul>
                    <li>Lecture : #{batchDetailBean.counters.readSkip}</li>
                    <li>Traitement : #{batchDetailBean.counters.processSkip}</li>
                    <li>Écriture : #{batchDetailBean.counters.writeSkip}</li>
                </ul>
                <p><strong>Commits :</strong> #{batchDetailBean.counters.commits}</p>
                <p><strong>Rollbacks :</strong> #{batchDetailBean.counters.rollbacks}</p>
            </p:outputPanel>
        </p:panel>

        <p:panel header="Détail par étape (partitions)">
            <p:dataTable value="#{batchDetailBean.steps}" var="s">
                <p:column headerText="Étape">#{s.stepName}</p:column>
                <p:column headerText="Statut">#{s.status}</p:column>
                <p:column headerText="Lues">#{s.readCount}</p:column>
                <p:column headerText="Écrites">#{s.writeCount}</p:column>
                <p:column headerText="Commits">#{s.commitCount}</p:column>
                <p:column headerText="Rejets lecture">#{s.readSkipCount}</p:column>
                <p:column headerText="Rejets traitement">#{s.processSkipCount}</p:column>
                <p:column headerText="Rejets écriture">#{s.writeSkipCount}</p:column>
            </p:dataTable>
        </p:panel>
    </h:form>
</h:body>

9. Pour voir les rejets détaillés (lignes rejetées)

Attention : Spring Batch ne stocke pas les lignes rejetées dans ses tables. Il stocke seulement les compteurs. Pour voir le détail (quelle ligne, quelle erreur), il faut logger explicitement dans le SkipListener du batch.
Ajouter un SkipListener côté batch
java

package com.example.batch.listener;

import com.example.batch.model.PersonCsv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

@Component
public class PersonSkipListener implements SkipListener<PersonCsv, Object> {

    private static final Logger log = LoggerFactory.getLogger(PersonSkipListener.class);

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[SKIP-READ] {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(PersonCsv item, Throwable t) {
        log.warn("[SKIP-PROCESS] id={} raison={}", item.getId(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        log.warn("[SKIP-WRITE] {} raison={}", item, t.getMessage());
    }
}

Et dans BatchConfiguration, ajoutez au workerStep :
java

.listener(new PersonSkipListener())  // ou injection du bean

Avec un logback-spring.xml configuré pour écrire ces logs dans un fichier dédié (batch-skips.log), vous pourrez consulter les rejets.
Si vous voulez les afficher dans l'UI

Deux approches :

    Table dédiée person_rejection (id, ligne_csv, raison, date_execution). Remplie par le SkipListener via JDBC. Consultable comme n'importe quelle table métier.

    Lecture des logs (fichier) exposés par un endpoint REST (/batch/logs/{executionId}). Plus simple mais moins structuré.

Je recommande l'approche 1 pour la production : c'est traçable, requêtable, et ne dépend pas du système de fichiers.
10. Sécurité
Point	Recommandation
Lecture seule	Les entités Batch* ne doivent jamais être modifiées par l'UI (pas de merge/persist)
Rôle	Restreindre l'accès aux pages /batch-* aux administrateurs
EXIT_MESSAGE	Peut contenir des traces (noms de tables, chemins). Filtrer côté UI (troncature à 500 caractères)
Logs	Ne jamais exposer la clé APP_CRYPTO_KEY (elle n'apparaît pas dans les tables, mais pourrait apparaître dans EXIT_MESSAGE si une exception la contient — à vérifier)
11. Résultat attendu

Vous obtenez deux écrans :

    Liste : ID | Statut | Début | Fin | Code sortie | Détail

    Détail : en-tête (statut, durées, compteurs agrégés) + tableau des 8 partitions avec leurs compteurs respectifs

Pour votre exécution en 13,5 s, l'écran détail affichera :
text

Statut        : COMPLETED
Début         : 2026-11-08T23:34:14
Fin           : 2026-11-08T23:34:28
Lignes lues   : 994 147
Lignes écrites: 994 147
Rejets (total): 0
Commits       : ~1 989
Rollbacks     : 0

[workerStep:partition-0] COMPLETED  lues=124268 écrites=124268 commits=249 rejets=0
[workerStep:partition-1] COMPLETED  lues=124268 écrites=124268 commits=249 rejets=0
...

12. Ce que je peux générer si vous voulez

    Le script Flyway complet (V2__batch_schema.sql) pour créer les tables BATCH_* si vous passez en production.

    Le SkipListener + la table person_rejection + le DAO associé.

    Les deux pages XHTML complètes (batch-executions.xhtml, batch-execution-detail.xhtml).

    Le BatchDetailBean complet avec gestion du param.id.

Dites-moi lequel vous intéresse, et je vous le fournis en version copiable telle quelle. 👍
exploitation des dones via les deux pages XHTML complètes (batch-executions.xhtml, batch-execution-detail.xhtml).

Voici les deux pages XHTML complètes, prêtes à copier dans primefaces-java8/src/main/webapp/. Elles s'appuient sur les beans et le DAO décrits précédemment — je les rappelle en fin de message pour que tout soit cohérent.
1. batch-executions.xhtml

Liste des 50 dernières exécutions, avec filtres rapides (statut, plage de dates), tri, pagination et navigation vers le détail.
xml

<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="http://java.sun.com/jsf/html"
      xmlns:f="http://java.sun.com/jsf/core"
      xmlns:p="http://primefaces.org/ui">

<h:head>
    <title>Exécutions de batch</title>
</h:head>

<h:body>
    <h:form id="batchListForm">

        <p:growl id="growl" life="3000"/>

        <p:panel header="Exécutions de batch" style="margin-bottom: 1em">

            <p:toolbar style="margin-bottom: 1em">
                <p:toolbarGroup>
                    <p:commandButton value="Rafraîchir"
                                     icon="pi pi-refresh"
                                     action="#{batchListBean.refresh}"
                                     update="batchListForm"
                                     ajax="true"/>
                </p:toolbarGroup>
                <p:toolbarGroup align="right">
                    <p:outputLabel value="Statut :"/>
                    <p:selectOneMenu value="#{batchListBean.statusFilter}"
                                     style="margin-left: .5em; margin-right: 1em">
                        <f:selectItem itemLabel="Tous" itemValue=""/>
                        <f:selectItem itemLabel="COMPLETED" itemValue="COMPLETED"/>
                        <f:selectItem itemLabel="FAILED"    itemValue="FAILED"/>
                        <f:selectItem itemLabel="STOPPED"   itemValue="STOPPED"/>
                        <f:selectItem itemLabel="STARTED"   itemValue="STARTED"/>
                        <p:ajax update="batchListForm" listener="#{batchListBean.filter}"/>
                    </p:selectOneMenu>
                </p:toolbarGroup>
            </p:toolbar>

            <p:dataTable id="executionsTable"
                         value="#{batchListBean.executions}"
                         var="e"
                         rows="20"
                         paginator="true"
                         paginatorPosition="bottom"
                         paginatorTemplate="{CurrentPageReport} {FirstPageLink} {PreviousPageLink} {PageLinks} {NextPageLink} {LastPageLink} {RowsPerPageDropdown}"
                         rowsPerPageTemplate="10,20,50,100"
                         sortMode="multiple"
                         emptyMessage="Aucune exécution trouvée"
                         rowKey="#{e.id}"
                         selectionMode="single"
                         selection="#{batchListBean.selected}"
                         lazy="false">

                <p:ajax event="rowSelect"
                        listener="#{batchListBean.onRowSelect}"
                        update=":batchListForm:detailButton"/>

                <p:column headerText="ID" sortBy="#{e.id}" width="80">
                    #{e.id}
                </p:column>

                <p:column headerText="Statut" sortBy="#{e.status}" width="130">
                    <p:tag value="#{e.status}"
                           severity="#{batchListBean.severityOf(e.status)}"/>
                </p:column>

                <p:column headerText="Début" width="180">
                    <h:outputText value="#{e.startTime}">
                        <f:convertDateTime pattern="dd/MM/yyyy HH:mm:ss"/>
                    </h:outputText>
                </p:column>

                <p:column headerText="Fin" width="180">
                    <h:outputText value="#{e.endTime}">
                        <f:convertDateTime pattern="dd/MM/yyyy HH:mm:ss"/>
                    </h:outputText>
                </p:column>

                <p:column headerText="Durée" width="110">
                    #{batchListBean.durationOf(e)}
                </p:column>

                <p:column headerText="Code de sortie" width="130">
                    <p:tag value="#{e.exitCode}"
                           severity="#{e.exitCode == 'COMPLETED' ? 'success' : 'danger'}"/>
                </p:column>

                <p:column headerText="Message" style="max-width: 400px">
                    <h:outputText value="#{batchListBean.truncate(e.exitMessage, 120)}"
                                  title="#{e.exitMessage}"/>
                </p:column>

                <p:column headerText="" width="100">
                    <p:commandButton value="Détail"
                                     icon="pi pi-search"
                                     action="#{batchListBean.viewDetail(e.id)}"
                                     ajax="false"
                                     styleClass="ui-button-info"/>
                </p:column>
            </p:dataTable>

            <p:commandButton id="detailButton"
                             value="Ouvrir le détail sélectionné"
                             icon="pi pi-external-link"
                             disabled="#{empty batchListBean.selected}"
                             action="#{batchListBean.viewSelected}"
                             ajax="false"
                             style="margin-top: 1em"/>
        </p:panel>
    </h:form>
</h:body>
</html>

2. batch-execution-detail.xhtml

Vue détaillée : en-tête de l'exécution, compteurs agrégés, tableau des étapes (partitions), et zone de rejets/rejets détaillés si vous avez ajouté la table person_rejection.
xml

<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="http://java.sun.com/jsf/html"
      xmlns:f="http://java.sun.com/jsf/core"
      xmlns:p="http://primefaces.org/ui">

<h:head>
    <title>Détail de l'exécution</title>
</h:head>

<h:body>
    <h:form id="detailForm">

        <p:growl id="growl" life="3000"/>

        <p:breadCrumb style="margin-bottom: 1em">
            <p:menuitem value="Exécutions"
                        action="#{batchDetailBean.backToList}"
                        ajax="false"
                        icon="pi pi-list"/>
            <p:menuitem value="Exécution ##{batchDetailBean.id}"
                        disabled="true"/>
        </p:breadCrumb>

        <h:panelGroup rendered="#{empty batchDetailBean.execution}">
            <p:message severity="warn"
                       text="Exécution introuvable pour cet identifiant."/>
        </h:panelGroup>

        <h:panelGroup rendered="#{not empty batchDetailBean.execution}">

            <!-- ================= En-tête ================= -->
            <p:panel header="Informations générales" style="margin-bottom: 1em">
                <p:panelGrid columns="4"
                             layout="grid"
                             columnClasses="label,value,label,value"
                             style="width: 100%">

                    <h:outputText value="ID :"/>
                    <h:outputText value="#{batchDetailBean.execution.id}"/>

                    <h:outputText value="Statut :"/>
                    <p:tag value="#{batchDetailBean.execution.status}"
                           severity="#{batchDetailBean.severityOf(batchDetailBean.execution.status)}"/>

                    <h:outputText value="Début :"/>
                    <h:outputText value="#{batchDetailBean.execution.startTime}">
                        <f:convertDateTime pattern="dd/MM/yyyy HH:mm:ss"/>
                    </h:outputText>

                    <h:outputText value="Fin :"/>
                    <h:outputText value="#{batchDetailBean.execution.endTime}">
                        <f:convertDateTime pattern="dd/MM/yyyy HH:mm:ss"/>
                    </h:outputText>

                    <h:outputText value="Durée :"/>
                    <h:outputText value="#{batchDetailBean.duration}"/>

                    <h:outputText value="Code de sortie :"/>
                    <p:tag value="#{batchDetailBean.execution.exitCode}"
                           severity="#{batchDetailBean.execution.exitCode == 'COMPLETED' ? 'success' : 'danger'}"/>

                    <h:outputText value="Paramètres :"/>
                    <h:outputText value="#{batchDetailBean.execution.parameters}"/>

                    <h:outputText value="Message :"/>
                    <h:outputText value="#{batchDetailBean.execution.exitMessage}"
                                  style="word-break: break-all"/>
                </p:panelGrid>
            </p:panel>

            <!-- ================= Compteurs agrégés ================= -->
            <p:panel header="Compteurs agrégés" style="margin-bottom: 1em">
                <p:panelGrid columns="3"
                             layout="grid"
                             style="width: 100%">

                    <p:card>
                        <f:facet name="title">Lignes lues</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.readCount}</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Lignes écrites</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.writeCount}</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Taux de succès</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.successRate}%</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Rejets lecture</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.readSkip}</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Rejets traitement</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.processSkip}</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Rejets écriture</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.writeSkip}</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Commits</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.commits}</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Rollbacks</f:facet>
                        <h2 style="margin:0">#{batchDetailBean.counters.rollbacks}</h2>
                    </p:card>

                    <p:card>
                        <f:facet name="title">Total rejets</f:facet>
                        <h2 style="margin:0; color:#c0392b">
                            #{batchDetailBean.counters.totalSkips}
                        </h2>
                    </p:card>
                </p:panelGrid>
            </p:panel>

            <!-- ================= Détail par étape ================= -->
            <p:panel header="Détail par étape (partitions)" style="margin-bottom: 1em">
                <p:dataTable value="#{batchDetailBean.steps}"
                             var="s"
                             emptyMessage="Aucune étape enregistrée"
                             rowKey="#{s.id}">

                    <p:column headerText="Étape" sortBy="#{s.stepName}" style="min-width: 220px">
                        #{s.stepName}
                    </p:column>

                    <p:column headerText="Statut" sortBy="#{s.status}" width="130">
                        <p:tag value="#{s.status}"
                               severity="#{batchDetailBean.severityOf(s.status)}"/>
                    </p:column>

                    <p:column headerText="Début" width="170">
                        <h:outputText value="#{s.startTime}">
                            <f:convertDateTime pattern="dd/MM/yyyy HH:mm:ss"/>
                        </h:outputText>
                    </p:column>

                    <p:column headerText="Fin" width="170">
                        <h:outputText value="#{s.endTime}">
                            <f:convertDateTime pattern="dd/MM/yyyy HH:mm:ss"/>
                        </h:outputText>
                    </p:column>

                    <p:column headerText="Lues" styleClass="num" style="text-align:right">
                        #{s.readCount}
                    </p:column>

                    <p:column headerText="Écrites" styleClass="num" style="text-align:right">
                        #{s.writeCount}
                    </p:column>

                    <p:column headerText="Commits" styleClass="num" style="text-align:right">
                        #{s.commitCount}
                    </p:column>

                    <p:column headerText="Rollbacks" styleClass="num" style="text-align:right">
                        #{s.rollbackCount}
                    </p:column>

                    <p:column headerText="Rej. lecture" styleClass="num" style="text-align:right">
                        #{s.readSkipCount}
                    </p:column>

                    <p:column headerText="Rej. traitement" styleClass="num" style="text-align:right">
                        #{s.processSkipCount}
                    </p:column>

                    <p:column headerText="Rej. écriture" styleClass="num" style="text-align:right">
                        #{s.writeSkipCount}
                    </p:column>

                    <p:column headerText="Code de sortie" width="130">
                        <p:tag value="#{s.exitCode}"
                               severity="#{s.exitCode == 'COMPLETED' ? 'success' : 'danger'}"/>
                    </p:column>

                    <p:column headerText="Message" style="max-width: 380px">
                        <h:outputText value="#{batchDetailBean.truncate(s.exitMessage, 120)}"
                                      title="#{s.exitMessage}"/>
                    </p:column>
                </p:dataTable>
            </p:panel>

            <!-- ================= Rejets détaillés (si table dédiée) ================= -->
            <h:panelGroup rendered="#{not empty batchDetailBean.rejections}">
                <p:panel header="Lignes rejetées (détail)" style="margin-bottom: 1em">
                    <p:dataTable value="#{batchDetailBean.rejections}"
                                 var="r"
                                 rows="20"
                                 paginator="true"
                                 emptyMessage="Aucun rejet">

                        <p:column headerText="Ligne CSV" width="100">
                            #{r.lineNumber}
                        </p:column>
                        <p:column headerText="Contenu" style="max-width: 400px">
                            <h:outputText value="#{r.rawContent}"/>
                        </p:column>
                        <p:column headerText="Motif">
                            #{r.reason}
                        </p:column>
                        <p:column headerText="Étape">
                            #{r.stepName}
                        </p:column>
                    </p:dataTable>
                </p:panel>
            </h:panelGroup>

            <h:panelGroup rendered="#{empty batchDetailBean.rejections}">
                <p:messages severity="info"
                            closable="false"
                            showDetail="false"
                            rendered="true"
                            style="display:block; margin-bottom:1em">
                    <p:message severity="info"
                               text="Aucun rejet détaillé enregistré pour cette exécution."/>
                </p:messages>
            </h:panelGroup>

            <p:commandButton value="Retour à la liste"
                             icon="pi pi-arrow-left"
                             action="#{batchDetailBean.backToList}"
                             ajax="false"
                             style="margin-top: 1em"/>
        </h:panelGroup>
    </h:form>
</h:body>
</html>

3. Les beans JSF qui vont avec
BatchListBean
java

package com.example.legacy.bean;

import com.example.legacy.batch.BatchConsultationDao;
import com.example.legacy.batch.BatchJobExecution;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@ManagedBean(name = "batchListBean")
@ViewScoped
public class BatchListBean implements Serializable {

    @ManagedProperty(value = "#{batchConsultationDao}")
    private BatchConsultationDao dao;

    private List<BatchJobExecution> all;
    private List<BatchJobExecution> executions;
    private String statusFilter = "";
    private BatchJobExecution selected;

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        all = dao.findRecentExecutions(50);
        applyFilter();
    }

    public void filter() {
        applyFilter();
    }

    private void applyFilter() {
        if (statusFilter == null || statusFilter.isEmpty()) {
            executions = all;
        } else {
            executions = all.stream()
                .filter(e -> statusFilter.equals(e.getStatus()))
                .collect(Collectors.toList());
        }
    }

    public void onRowSelect() {
        // no-op, sert à activer le bouton "Détail"
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

    // --- helpers d'affichage ---

    public String durationOf(BatchJobExecution e) {
        if (e.getStartTime() == null || e.getEndTime() == null) return "—";
        Duration d = Duration.between(e.getStartTime(), e.getEndTime());
        return d.toSeconds() + " s";
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

    // getters / setters

    public void setDao(BatchConsultationDao dao) { this.dao = dao; }
    public List<BatchJobExecution> getExecutions() { return executions; }
    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) { this.statusFilter = statusFilter; }
    public BatchJobExecution getSelected() { return selected; }
    public void setSelected(BatchJobExecution selected) { this.selected = selected; }
}

BatchDetailBean
java

package com.example.legacy.bean;

import com.example.legacy.batch.BatchConsultationDao;
import com.example.legacy.batch.BatchCounters;
import com.example.legacy.batch.BatchJobExecution;
import com.example.legacy.batch.BatchStepExecution;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ManagedBean(name = "batchDetailBean")
@ViewScoped
public class BatchDetailBean implements Serializable {

    @ManagedProperty(value = "#{batchConsultationDao}")
    private BatchConsultationDao dao;

    private Long id;
    private BatchJobExecution execution;
    private List<BatchStepExecution> steps = Collections.emptyList();
    private BatchCounters counters = new BatchCounters();
    private List<?> rejections = Collections.emptyList();

    @PostConstruct
    public void init() {
        Map<String, String> params =
            FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String p = params.get("id");
        if (p != null && !p.isEmpty()) {
            try {
                id = Long.valueOf(p);
                execution = dao.findExecution(id);
                if (execution != null) {
                    steps = dao.findSteps(id);
                    counters = dao.aggregate(id);
                    rejections = dao.findRejections(id); // à ajouter si table dédiée
                }
            } catch (NumberFormatException ignored) { /* id invalide -> page vide */ }
        }
    }

    public String backToList() {
        return "/batch-executions.xhtml?faces-redirect=true";
    }

    public String getDuration() {
        if (execution == null
                || execution.getStartTime() == null
                || execution.getEndTime() == null) {
            return "—";
        }
        Duration d = Duration.between(execution.getStartTime(), execution.getEndTime());
        return d.toSeconds() + " s";
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

    // getters / setters
    public Long getId() { return id; }
    public BatchJobExecution getExecution() { return execution; }
    public List<BatchStepExecution> getSteps() { return steps; }
    public BatchCounters getCounters() { return counters; }
    public List<?> getRejections() { return rejections; }
    public void setDao(BatchConsultationDao dao) { this.dao = dao; }
}

4. Le DAO complété

Il manque la méthode findExecution(Long) dans le DAO précédent. Ajoutez-la :
java

public BatchJobExecution findExecution(Long id) {
    return em.find(BatchJobExecution.class, id);
}

public List<?> findRejections(Long jobExecutionId) {
    // Si vous n'avez pas encore la table person_rejection, retournez une liste vide :
    return java.util.Collections.emptyList();
}

Et assurez-vous que les beans soient accessibles en JSF. Si vous êtes en @ManagedBean, ajoutez @ManagedBean(name = "batchConsultationDao") sur BatchConsultationDao (ou exposez-le via un producer CDI si vous êtes en @Named).
5. Points d'attention avant déploiement
Point	Détail
Accès aux tables BATCH_*	L'utilisateur de la base utilisé par primefaces-java8 doit avoir SELECT sur ces tables. Vérifiez les droits.
Persistence unit	Les entités Batch* doivent être dans un package scanné par persistence.xml (<exclude-unlisted-classes>false</exclude-unlisted-classes>), ou explicitement listées.
Lecture seule	Ne jamais appeler em.persist/em.merge sur ces entités. Ce sont des tables d'audit gérées par Spring Batch.
EXIT_MESSAGE	Peut être long et contenir des stack traces. L'UI tronque à 120 caractères avec un title pour le texte complet.
parameters	La colonne BATCH_JOB_EXECUTION_PARAMS n'est pas encore mappée. Si vous voulez afficher les paramètres, ajoutez une entité BatchJobExecutionParams et une jointure.
Performance	Si vous avez des milliers d'exécutions, passez en lazy="true" sur la p:dataTable avec un LazyDataModel, sinon la page chargera tout en mémoire.
6. Test rapide

    Déployez primefaces-java8.

    Ouvrez http://localhost:8080/legacy/batch-executions.xhtml.

    Vous devez voir les exécutions récentes, triées par date de début décroissante.

    Cliquez sur "Détail" pour une exécution COMPLETED : vous devez voir les 9 lignes (1 masterStep + 8 workerStep:partition-N) avec leurs compteurs.

Souhaitez-vous que je vous fournisse aussi :

    Le LazyDataModel pour la pagination serveur (si vous avez beaucoup d'exécutions) ?

    L'entité BatchJobExecutionParams et son affichage dans le détail ?

    La table person_rejection avec le SkipListener qui la remplit et l'affichage dans le détail ?

