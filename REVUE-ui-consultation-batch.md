# Revue de code — UI de consultation des exécutions de batch

Périmètre : module **`primefaces-java8`** (écrans `batch-executions.xhtml` et
`batch-execution-detail.xhtml`), avec les corrections de dépendances
(`crypto-core`, schéma Flyway `V2`) nécessaires pour que ces écrans fonctionnent.
Spec de référence : `spec.md` v1.2, § 3.4.2 et § 3.5.

Le projet **ne compilait pas** et **ne se déployait pas** : les deux pages
étaient rejetées par Facelets, les entités pointaient sur des colonnes
inexistantes et le schéma Flyway ne correspondait pas à Spring Batch 5.
Toutes les corrections ci-dessous sont appliquées et le WAR se construit.

---

## 1. Bugs bloquants (page inutilisable)

### B1 — `p:grid` n'existe pas dans PrimeFaces 10
**Fichier** : `src/main/webapp/batch-executions.xhtml`
**Symptôme** : `TagLibraryException` / composant inconnu, page liste en erreur 500.
**Correction** : remplacé par `p:panelGrid layout="grid"` (les autres attributs
`columns`, `columnClasses`, `style` restent valides).

### B2 — Commentaires XHTML invalides (`--` à l'intérieur d'un commentaire)
**Fichier** : `src/main/webapp/batch-execution-detail.xhtml`
**Symptôme** : `Un commentaire XML ne peut pas contenir '--' … Ligne 37`.
La page détail ne se compilait pas du tout.
**Correction** : tous les séparateurs `<!-- ---------- … ---------- -->` sont
remplacés par `<!-- ================= … ================= -->`.
**Vérification** : les deux XHTML passent maintenant le parseur XML.

### B3 — `p:message` sans attribut `text`
**Fichier** : `batch-execution-detail.xhtml`
**Symptôme** : attribut inexistant → erreur de rendu de la page détail.
**Correction** : messages statiques rendus par `p:outputPanel` + `h:outputText`
(classes `ui-message ui-message-warn` / `-info`), et suppression des
`p:message` imbriqués dans les cellules du tableau.

### B4 — Facettes `title`/`subtitle` non supportées par `p:card`
**Fichier** : `batch-execution-detail.xhtml`
**Symptôme** : les 9 cartes de compteurs s'affichaient sans titre.
**Correction** : utilisation des attributs `title` des `p:card`.
**Ajout** : le libellé « Job » de l'en-tête est remplacé par « Instance du job »
(la colonne `JOB_NAME` n'existe pas dans `BATCH_JOB_EXECUTION`).

### B5 — Entités JPA mappées sur des colonnes inexistantes
**Fichiers** : `batch/BatchJobExecution.java`, `batch/BatchStepExecution.java`
**Symptôme** : `SQLSyntaxErrorException: Unknown column '…JOB_NAME'`, et échec de
l'`EntityManagerFactory` → aucun écran ne fonctionne.
**Corrections** :
- `BatchJobExecution` : suppression de `JOB_NAME`, `JOB_KEY` et
  `JOB_CONFIGURATION_LOCATION` (absents du schéma Spring Batch 5).
- `BatchStepExecution` : `COMMIT_CODE` → `COMMIT_COUNT` (`Long`), ajout de
  `CREATE_TIME` (colonnes réelles du schéma officiel).
Toutes les colonnes restantes ont été recoupées une à une avec
`schema-mysql.sql` de `spring-batch-core` 5.x et avec `V2__batch_schema.sql`.

### B6 — Schéma Flyway `V2` invalide et non conforme à Spring Batch 5
**Fichier** : `batch-java17/src/main/resources/db/migration/V2__batch_schema.sql`
**Symptôme** : DDL des tables de séquence syntaxiquement invalide (migration
irrattrapable), et schéma incompatible avec `JdbcJobRepository` de Spring Batch 5
(colonnes `PARAMETER_TYPE` / `IDENTIFYING`, `COMMIT_COUNT`, `CREATE_TIME` de
l'étape, tables de séquence MySQL).
**Correction** : fichier réécrit à l'identique du schéma officiel
`org/springframework/batch/core/schema-mysql.sql`.
**Conséquence** : le checksum Flyway de `V2` change → voir § 4.

### B7 — Pagination et tri inopérants (chargement complet + `first` non lié)
**Fichiers** : `bean/BatchListBean.java`, `batch-executions.xhtml`
**Symptôme** : la liste chargeait toutes les exécutions, le paginator ne
paginait rien, et le retour en page 1 après un filtre était impossible.
**Correction** : `BatchListBean` devient un `LazyDataModel<BatchJobExecution>`
(`load`, `getRowKey`, `getRowData`, `getLazyModel`), la `p:dataTable` passe en
`lazy="true"` avec `value="#{batchListBean.lazyModel}"`, et `first` / `rows` sont
liés à `#{batchListBean.first}` / `#{batchListBean.pageSize}`
(`10,20,50,100`). Le tri multiple (`sortMode="multiple"`) est transmis au DAO.

### B8 — `LazyDataModel.setPageSize` écrasé sans délégation → tableau vide
**Fichier** : `bean/BatchListBean.java`
**Symptôme** : `LazyDataModel.setRowIndex()` calcule `rowIndex % pageSize` sur son
champ privé. En redéfinissant `setPageSize()` sans appeler `super`, ce champ
restait à `0`, donc `rowIndex` était forcé à `-1` : **aucune ligne ne
s'affichait** malgré des données présentes.
**Correction** : plus de champ `pageSize` local ; la valeur par défaut (20) est
posée dans le constructeur via `super.setPageSize(...)`, et `load()` met à jour
la valeur héritée. Les accesseurs sont ceux de `LazyDataModel`.

### B9 — Filtres sans remise à zéro de la pagination
**Fichier** : `bean/BatchListBean.java`
**Symptôme** : après un filtre, on restait sur la page 5 → liste vide alors que
des résultats existent.
**Correction** : `filter()`, `clearFilters()` et `refresh()` remettent
`first = 0` et recalculent le `rowCount` ; le binding de `first` propage la
valeur au paginator.

### B10 — Filtre « Au » excluant la journée choisie
**Fichier** : `batch/BatchConsultationDao.java`
**Symptôme** : une exécution du jour même à 14 h n'apparaissait pas (borne
`<= dateTo` à 00:00).
**Correction** : `startTime < début du jour suivant` (borne inclusive réelle),
avec `Calendar` plutôt qu'un `SimpleDateFormat` partagé.

### B11 — Injection JPQL / LIKE, et filtres mal normalisés
**Fichier** : `batch/BatchConsultationDao.java`
**Symptômes** :
- tri par n'importe quel champ transmis par le client (expression arbitraire) ;
- `%` et `_` saisis dans le mot-clé interprétés comme jokers ;
- filtres numériques/texte non `trim()`és (`\" 12 \"` ne trouvait rien).
**Corrections** : liste blanche de tri (`id`, `status`, `startTime`, `endTime`,
`exitCode`), `LIKE` échappé (`\\%`, `\\_`, `\\\\`) avec clause `ESCAPE`,
`lower()` en `Locale.ROOT`, et normalisation `trimToNull` de tous les filtres.
Tri par défaut stable : `startTime DESC, id DESC`.

### B12 — Taux de succès : division par zéro et format incohérent
**Fichier** : `batch/BatchCounters.java`
**Symptômes** : `ArithmeticException`/`NaN` quand `readCount` vaut 0, taux
supérieur à 100 % si les rejets dépassent les lignes lues, et affichage
`100.0%` mêlant point et virgule.
**Corrections** : null-safe, bornage `[0 ; 100]`, `Locale.FRANCE` et format
`%.2f` constant (« 100,00 % » y compris pour 100).

### B13 — `EntityManagerFactory` recréé à chaque appel et jamais fermé
**Fichier** : `batch/BatchConsultationDao.java`
**Symptôme** : une fabrique JPA (pool de connexions complet) par requête HTTP →
saturation de MariaDB puis fuite à chaque redéploiement.
**Corrections** : fabrique unique, créée **paresseusement** et de façon
thread-safe (`volatile` + double vérification), donc une base momentanément
indisponible ne rend plus la classe définitivement inutilisable ; fermeture via
`close()` appelé par le nouveau `listener/PersistenceContextListener`
(enregistré dans `web.xml`).

### B14 — Démarrage du contexte web bloqué par le chiffrement
**Fichier** : `listener/CryptoContextListener.java`
**Symptômes** :
- `@WebListener` **et** déclaration dans `web.xml` → double initialisation →
  « CryptoService déjà initialisé » au démarrage ;
- `crypto.properties` absent → exception → contexte web en échec, donc les deux
  écrans de consultation (qui n'utilisent pas le chiffrement) étaient
  inaccessibles ;
- clé `APP_CRYPTO_KEY` absente → même effet.
**Corrections** : annotation retirée, configuration par défaut
(`CryptoConfig.defaults()`) en l'absence du fichier, et échec d'initialisation
crypto journalisé sans bloquer le déploiement (l'erreur reste explicite dans les
logs, les accès à `person` restant indisponibles).

### B15 — `SimpleDateFormat` statique (non thread-safe)
**Fichiers** : `bean/BatchListBean.java`, `bean/BatchDetailBean.java`
**Symptôme** : dates erronées ou exceptions de parsing sous charge, la même
instance étant partagée par toutes les requêtes de tous les utilisateurs.
**Correction** : formatage par instance locale (`new SimpleDateFormat(...)`),
et formats d'affichage délégués à `f:convertDateTime` dans les vues.

### B16 — Pilote JDBC incohérent avec l'URL (WAR non déployable)
**Fichiers** : `pom.xml`, `META-INF/persistence.xml`
**Symptômes** : l'URL est `jdbc:mariadb://…` mais aucun pilote MariaDB n'était
embarqué (`ClassNotFoundException: org.mariadb.jdbc.Driver`) ; le port de
`persistence.xml` ne correspondait pas à `.env` / au batch.
**Corrections** : ajout de `org.mariadb.jdbc:mariadb-java-client:3.3.3`, port
aligné sur `3306`, utilisateur `myuser` / base `dotations_db` identiques au
module batch.

### B17 — JSF 2.2.20 face à PrimeFaces 10 (API JSF 2.3)
**Fichier** : `pom.xml`
**Symptôme** : PrimeFaces 10 est compilé contre `jakarta.faces:jakarta.faces:2.3.14`
(packages `javax.faces`, Jakarta EE 8). Avec Mojarra 2.2.20, les méthodes ajoutées
en JSF 2.3 (`getPassThroughAttributes`, gestionnaire d'expressions de recherche,
etc.) provoquent des `NoSuchMethodError` à l'exécution.
**Correction** : Mojarra **2.3.9** (`org.glassfish:javax.faces`), compatible
Java 8 et toujours en `javax.*` (aucune migration Jakarta requise).

### B18 — Beans déclarés deux fois
**Fichiers** : `bean/BatchListBean.java`, `bean/BatchDetailBean.java`,
`WEB-INF/faces-config.xml`
**Symptôme** : `@ManagedBean` / `@ViewScoped` **et** `<managed-bean>` dans
`faces-config.xml` → deux définitions concurrentes, scope effectif imprévisible.
**Correction** : `faces-config.xml` reste l'unique déclaration (scope `view`,
injection du DAO par `<managed-property>`), les annotations redondantes sont
retirées. L'annotation `@PostConstruct` est conservée (nécessite
`javax.annotation-api` en scope `provided`, ajouté au `pom.xml`).

### B19 — Colonnes de tri non triables et compteur d'étapes incohérent
**Fichiers** : `batch-executions.xhtml`, `batch-execution-detail.xhtml`
**Symptômes** : `sortBy` présent sur des colonnes sans objet côté serveur,
affichage `#{s.commitCode}` (accesseur supprimé), et absence de tri sur le
tableau des étapes.
**Corrections** : `sortBy` limité aux cinq champs autorisés côté DAO (`id`,
`status`, `startTime`, `endTime`, `exitCode`), `#{s.commitCount}`, et tri
client activé sur les colonnes « Étape » et « Statut » du détail.

---

## 2. Conformité à la spécification v1.2 (§ 3.4.2)

| Exigence | État |
|---|---|
| Liste paginée 10/20/50/100 | OK (`rowsPerPageTemplate`, `LazyDataModel`) |
| Filtres ID, statut, dates du/au, code sortie, mot-clé | OK |
| Tri multiple | OK (`sortMode="multiple"`, liste blanche serveur) |
| Sélection d'une ligne → détail | OK (`selection` + bouton « Détail ») |
| En-tête détail (statut, durée, code sortie, message) | OK |
| 9 cartes de compteurs agrégés | OK |
| Tableau des 9 étapes avec compteurs détaillés | OK |
| Tableau paginé des rejets (ligne, contenu, étape, motif, date) | OK (20/page) |
| DAO : `findExecutions`, `countExecutions`, `findExecution`, `findSteps`, `aggregate`, `findRejections`, `findRejectionsByStep` | OK |
| Helpers `durationOf`, `severityOf`, `truncate`, `hasRejections` | OK |

---

## 3. Vérifications effectuées

- Parsing XML des deux XHTML : **OK**.
- Validation des balises/attributs `p:*` contre le taglib PrimeFaces 10 : **OK**
  (c'est ce contrôle qui a révélé `p:grid`, `p:message text=` et les facettes de
  `p:card`).
- `mvn -f crypto-core/pom.xml install -DskipTests` : **OK**.
- `mvn -f primefaces-java8/pom.xml clean package -DskipTests` : **OK**,
  `target/primefaces-java8.war` produit.
- Contenu du WAR contrôlé : `WEB-INF/web.xml`, `faces-config.xml`,
  `classes/META-INF/{persistence,orm}.xml`, `javax.faces-2.3.9.jar`,
  `mariadb-java-client-3.3.3.jar`, les deux XHTML.
- Colonnes de chaque entité (`BatchJobExecution`, `BatchStepExecution`,
  `PersonRejection`) recoupées avec `V2` / `V3`.

---

## 4. Points d'attention restants (hors code)

1. **Checksum Flyway** : `V2__batch_schema.sql` a été modifié. Sur une base déjà
   migrée, exécuter `flyway repair` (ou supprimer la ligne `V2` de
   `flyway_schema_history`) **avant** de relancer le batch. Sur une base neuve,
   aucun problème. L'ancien `V2` étant syntaxiquement invalide, il est peu
   probable qu'il ait déjà été appliqué avec succès.
2. **Clé de chiffrement** : `APP_CRYPTO_KEY` reste obligatoire pour lire la table
   `person`. Sans elle, l'UI de consultation fonctionne (dégradation
   volontaire), mais les écrans/personnes chiffrées non.
3. **Base** : MariaDB accessible sur `localhost:3306`, base `dotations_db`,
   utilisateur `myuser` / `secret123` (valeurs de `.env` reprises telles quelles
   dans `persistence.xml` — à externaliser via JNDI en production).
4. **`hibernate.show_sql=true`** est conservé dans `persistence.xml` : à passer à
   `false` en production (deux requêtes SQL par affichage de page : liste + count).
5. **`batch-java17/src/main/resources/application.yml`** contient un bloc
   `hibernate:` résiduel mal indenté (ligne 32 et suivantes), sans effet mais
   trompeur : à supprimer.
6. Le plugin `jetty-maven-plugin` n'est plus présent dans `primefaces-java8/pom.xml`
   (modification déjà dans l'arbre de travail) : le déploiement se fait par WAR
   dans Tomcat 9. À rétablir si le lancement `mvn jetty:run` était utilisé.
7. `crypto.properties` n'est pas versionné (`**/*properties*` dans `.gitignore`) :
   le listener retombe sur `CryptoConfig.defaults()`, donc aucune action requise.

---

## 5. Fichiers modifiés

```
batch-java17/src/main/resources/application.yml
batch-java17/src/main/resources/db/migration/V2__batch_schema.sql
primefaces-java8/pom.xml
primefaces-java8/src/main/resources/META-INF/persistence.xml
primefaces-java8/src/main/webapp/WEB-INF/web.xml
primefaces-java8/src/main/webapp/batch-executions.xhtml
primefaces-java8/src/main/webapp/batch-execution-detail.xhtml
primefaces-java8/src/main/java/com/example/legacy/batch/BatchConsultationDao.java
primefaces-java8/src/main/java/com/example/legacy/batch/BatchCounters.java
primefaces-java8/src/main/java/com/example/legacy/batch/BatchJobExecution.java
primefaces-java8/src/main/java/com/example/legacy/batch/BatchStepExecution.java
primefaces-java8/src/main/java/com/example/legacy/bean/BatchListBean.java
primefaces-java8/src/main/java/com/example/legacy/bean/BatchDetailBean.java
primefaces-java8/src/main/java/com/example/legacy/listener/CryptoContextListener.java
primefaces-java8/src/main/java/com/example/legacy/listener/PersistenceContextListener.java   (nouveau)
```
