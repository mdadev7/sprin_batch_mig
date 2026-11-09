# Spécification technique et fonctionnelle
## Migration d'un fichier texte vers une base de données avec Spring Boot, Spring Batch et chiffrement modulaire

**Version** : 1.1
**Statut** : Proposition consolidée
**Date** : 2026-11-09

---

## 1. Contexte et objectifs

### 1.1 Contexte

Une entreprise dispose de données RH dans un fichier plat CSV (`;`, UTF-8, avec en-tête) contenant des données personnelles, dont le **salaire**, donnée sensible au sens du RGPD. Ces données doivent être migrées vers une base relationnelle commune à deux applications :

| Application | Stack | Rôle |
|---|---|---|
| **Batch de migration** | Spring Boot 3.1.3 / Java 17 / Spring Batch 5 | Lit le CSV, chiffre le salaire, insère en base |
| **Application métier** | Java 8 / JSF PrimeFaces 10 / JPA javax | Consulte et modifie les données migrées |

Le salaire doit être **chiffré au repos** en base, tout en restant lisible en clair dans l'UI.

### 1.2 Objectifs

| # | Objectif | Critère de succès |
|---|---|---|
| O1 | Migrer les fichiers plats vers la base | 100 % des lignes valides migrées |
| O2 | Chiffrer le salaire au repos | Aucun salaire en clair en base, y compris en backup |
| O3 | Compatibilité de lecture inter-applications | Une valeur chiffrée par le batch est déchiffrable par l'UI, et vice-versa |
| O4 | Supporter des fichiers volumineux | ≥ 1 000 000 lignes en moins de 30 s (mesuré : 994 147 lignes en 13,5 s) |
| O5 | Permettre la rotation de clé sans downtime | Format `v1:`/`v2:`, procédure documentée |
| O6 | Isoler la logique cryptographique | Module `crypto-core` partagé, sans duplication AES |
| O7 | Consulter l'historique des exécutions | Deux écrans JSF lisant les tables `BATCH_*` |

### 1.3 Hors périmètre

- Migration de données antérieures au CSV
- Chiffrement d'autres colonnes que le salaire
- Interface de lancement du batch (exécution en ligne de commande)
- Archivage des CSV après migration

---

## 2. Spécifications fonctionnelles

### 2.1 Format d'entrée

Fichier CSV, séparateur `;`, encodage UTF-8, avec en-tête. **Emplacement** : `batch-java17/src/main/resources/data/person.csv` (empaqueté dans le JAR, accédé via `classpath:`).

| Position | Champ | Type | Contrainte | Exemple |
|---|---|---|---|---|
| 1 | `id` | Entier | > 0, unique | `1` |
| 2 | `nom` | Chaîne | non vide, ≤ 100 | `Dupont` |
| 3 | `prenom` | Chaîne | non vide, ≤ 100 | `Jean` |
| 4 | `salaire` | Décimal | > 0, format `1234.56` | `3200.50` |
| 5 | `date_naissance` | Date | `dd/MM/yyyy` | `15/03/1985` |
| 6 | `ville` | Chaîne | ≤ 100 | `Paris` |

Exemple :

```
id;nom;prenom;salaire;date_naissance;ville
1;Dupont;Jean;3200.50;15/03/1985;Paris
2;Martin;Sophie;4100.00;22/07/1990;Lyon
```

### 2.2 Règles de gestion

| # | Règle | Comportement |
|---|---|---|
| RG1 | Ligne vide | Ignorée silencieusement |
| RG2 | Moins de 6 colonnes | Rejetée, comptée en erreur |
| RG3 | `id` en doublon dans le fichier | Rejetée (contrainte PK en base) |
| RG4 | `salaire` non numérique | Rejetée, comptée en erreur |
| RG5 | `date_naissance` non parsable | Rejetée, comptée en erreur |
| RG6 | Chiffrement du salaire avant insertion | Obligatoire |
| RG7 | Échec de chiffrement | Rejette l'enregistrement |
| RG8 | En deçà de 100 erreurs cumulées | Le job continue |
| RG9 | Au-delà de 100 erreurs cumulées | Le job s'arrête en échec |
| RG10 | `id` déjà présent en base | Rejeté (contrainte PK) |

### 2.3 Rapport d'exécution

Accessible via les tables Spring Batch (`BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`) et complété par des logs applicatifs :

- Date/heure de début et de fin
- Nombre total de lignes lues, écrites, rejetées
- Répartition par partition (8 workers)
- Statut final : `COMPLETED` / `FAILED` / `STOPPED`

### 2.4 Critères d'acceptation

| # | Critère | Vérification |
|---|---|---|
| CAF1 | 100 lignes valides → 100 lignes en base | `SELECT COUNT(*)` |
| CAF2 | 100 lignes dont 5 invalides → 95 + 5 rejets | Rapport + `COUNT(*)` |
| CAF3 | Aucun salaire en clair en base | `SELECT ... WHERE salaire_encrypted NOT LIKE 'v1:%'` → 0 |
| CAF4 | Une ligne migrée est lisible par l'UI avec le bon salaire | Test manuel de bout en bout |
| CAF5 | Un salaire modifié dans l'UI est relisible par le batch | Test croisé |
| CAF6 | 1M lignes migrées en < 30 s | Chronométrage (mesuré : 13,5 s) |
| CAF7 | Les 8 partitions ont des durées à ±5 % | Logs `BATCH_STEP_EXECUTION` |

---

## 3. Spécifications techniques

### 3.1 Architecture applicative

```
┌───────────────────────────────────────────────────────────────────────┐
│                         crypto-core (Java 8)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────────────┐  │
│  │CryptoService │  │ KeyProvider  │  │    CryptoBootstrap          │  │
│  │ AES-256-GCM  │←─│  interface   │←─│    EnvKeyProvider           │  │
│  │ format v1:   │  │              │  │    CryptoHolder             │  │
│  │ Rotating     │  │              │  │    CryptoConfig             │  │
│  └──────────────┘  └──────────────┘  └─────────────────────────────┘  │
└───────────────────────────────┬───────────────────────────────────────┘
                                │  dépendance Maven partagée
              ┌─────────────────┴─────────────────┐
              │                                   │
┌─────────────▼──────────────────┐   ┌────────────▼────────────────────┐
│  batch-java17                  │   │  primefaces-java8               │
│  Spring Boot 3.1.3             │   │  JSF / Hibernate 5 / EclipseLink│
│  Spring Batch 5 (partitionné)  │   │                                 │
│  JDBC batch + Flyway           │   │  JPA javax XML mapping          │
│  TaskExecutor 8 threads        │   │  Batch consultation UI          │
└───────────────┬────────────────┘   └────────────┬────────────────────┘
                │                                 │
                └───────────────┬─────────────────┘
                                ▼
                    ┌───────────────────────┐
                    │  MariaDB / Oracle     │
                    │  table person         │
                    │  tables BATCH_*       │
                    └───────────────────────┘
```

### 3.2 Module `crypto-core`

**Rôle** : unique source de vérité pour l'algorithme, le format et la clé.

| Élément | Détail |
|---|---|
| **Langage** | Java 8 (`maven.compiler.release=8`) |
| **Dépendances** | JDK pur (aucune dépendance JPA/Spring) |
| **Algorithme** | AES-256-GCM (`AES/GCM/NoPadding`) |
| **Taille de clé** | 32 octets (AES-256) |
| **IV** | 12 octets, `SecureRandom` par opération |
| **Tag** | 128 bits |
| **Format de sortie** | `{formatVersion}:Base64(IV ‖ ciphertext ‖ tag)` |
| **Thread-safety** | Oui (aucun état mutable partagé) |
| **Portabilité** | Hibernate 5, EclipseLink 2.7+, Spring Boot 3 |

**Classes principales** :

| Classe | Rôle |
|---|---|
| `CryptoConfig` | Paramètres immuables (transformation, IV, tag, version, nom de variable d'env). Validation stricte. `formatPrefix()` centralise `version + ":"`. |
| `CryptoService` | Chiffrement/déchiffrement pour une version. Constructeur `(KeyProvider, CryptoConfig)`. |
| `RotatingCryptoService` | Agrège plusieurs `CryptoService` (v1, v2) ; chiffre avec `currentVersion()`, déchiffre selon le préfixe. |
| `KeyProvider` | Interface `SecretKey getKey()`. |
| `EnvKeyProvider` | Lit la clé Base64 depuis une variable d'environnement. Valide longueur 32. |
| `CryptoHolder` | Point d'accès statique (`encrypt`, `decrypt`), mode simple ou rotation. |
| `CryptoBootstrap` | Fabriques `create`, `initGlobal`, `initRotating`. |
| `CryptoException` | `RuntimeException` (jamais `IllegalArgumentException` pour éviter le re-wrap). |

**Versionnement du format** : préfixe `v1:` en production initiale ; `v2:` introduit lors d'une rotation. Le format versionné permet la **rotation sans downtime**.

**Test de non-régression** : une fixture `fixture-v1.txt` (plaintext + ciphertext) est stockée dans `src/test/resources`. Le test `decrypt_knownFixture()` échoue si le format change.

### 3.3 Batch de migration (Java 17 / Spring Boot 3.1.3 / Spring Batch 5)

#### 3.3.1 Architecture interne — partitionnement

```
Fichier CSV (classpath:data/person.csv)
    │
    ▼
LineRangePartitioner  ──► 8 partitions (plages de lignes)
    │
    ▼
masterStep (orchestrateur)  ──► TaskExecutor 8 threads
    │
    ├── worker-0 : LineRangeFlatFileReader(plage 0) → Processor → JdbcWriter
    ├── worker-1 : LineRangeFlatFileReader(plage 1) → Processor → JdbcWriter
    └── ... (8 workers en parallèle)
```

Chaque worker a **son propre `ItemReader`** (`@StepScope`), ce qui élimine tout problème de thread-safety.

#### 3.3.2 Composants

| Composant | Type | Rôle |
|---|---|---|
| `LineRangePartitioner` | `Partitioner` | Compte les lignes via `DefaultResourceLoader`, crée 8 `ExecutionContext` `{startLine, endLine}` |
| `LineRangeFlatFileReader` | `ItemStreamReader<PersonCsv>` | Lit une plage, via `InputStream` (compatible `classpath:` et JAR) |
| `PersonRowProcessor` | `ItemProcessor<PersonCsv, PersonRow>` | Mappe et **chiffre explicitement** le salaire |
| `PersonJdbcWriter` | `JdbcBatchItemWriter<PersonRow>` | Insertion JDBC batchée |
| `PersonSkipListener` | `SkipListener` | Loggue les rejets (RG4, RG5) |

#### 3.3.3 Paramètres d'exécution

| Paramètre | Valeur par défaut | Justification |
|---|---|---|
| `batch.chunk-size` | 500 (MariaDB) / 2000-5000 (Oracle) | Compromis mémoire/débit |
| `batch.grid-size` | 8 | Nombre de workers = cœurs disponibles |
| `batch.throttle-limit` | 8 | Aligné sur grid-size |
| `spring.datasource.hikari.maximum-pool-size` | 12 | grid-size + marge |
| `spring.batch.jdbc.initialize-schema` | `never` (Flyway) | Gestion explicite du schéma |

#### 3.3.4 Choix technologique — JDBC vs JPA

**Décision retenue** : **JDBC** (`JdbcBatchItemWriter` + `DataSource`).

| Critère | JDBC | JPA |
|---|---|---|
| Débit d'écriture | 20–35 k lignes/s | 8–13 k lignes/s |
| Empreinte mémoire | Constante | Persistence context à gérer |
| Risque OOM | Nul | Présent sans `flush/clear` |
| Chiffrement | **Explicite** dans le processor | Automatique (converter) implicite |
| Auditabilité | Visible dans le code | Effet de bord du flush |

Le batch ne dépend **pas** d'Hibernate pour l'écriture. Le converter JPA reste dans le projet batch uniquement pour d'éventuels usages internes.

#### 3.3.5 Configuration Spring

```yaml
spring:
  application:
    name: batch-migration
  main:
    web-application-type: none
  config:
    import: optional:file:./.env[.properties]
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: ${DB_DRIVER}
    hikari:
      maximum-pool-size: 12
      minimum-idle: 4
  batch:
    job:
      enabled: false          # lancement via JobRunner
    jdbc:
      initialize-schema: never
  flyway:
    enabled: true
    locations: classpath:db/migration

crypto:
  transformation: ${CRYPTO_TRANSFORMATION:AES/GCM/NoPadding}
  key-environment-variable: ${CRYPTO_KEY_ENVIRONMENT_VARIABLE:APP_CRYPTO_KEY}
  iv-length: ${CRYPTO_IV_LENGTH:12}
  tag-length: ${CRYPTO_TAG_LENGTH:128}
  format-version: ${CRYPTO_FORMAT_VERSION:v1}

batch:
  input-file: ${BATCH_INPUT_FILE:classpath:data/person.csv}
  chunk-size: ${BATCH_CHUNK_SIZE:500}
  grid-size: ${BATCH_GRID_SIZE:8}
  throttle-limit: ${BATCH_GRID_SIZE:8}
  skip-limit: ${BATCH_SKIP_LIMIT:100}
  retry-limit: 3
```

#### 3.3.6 Configuration des beans

`CryptoConfiguration` :

```java
@Bean
public CryptoConfig cryptoConfig(
        @Value("${crypto.transformation:AES/GCM/NoPadding}") String transformation,
        @Value("${crypto.key-environment-variable:APP_CRYPTO_KEY}") String envName,
        @Value("${crypto.iv-length:12}") int ivLength,
        @Value("${crypto.tag-length:128}") int tagLength,
        @Value("${crypto.format-version:v1}") String version) {
    return new CryptoConfig(transformation.trim(), envName.trim(), ivLength, tagLength, version.trim());
}

@Bean
public KeyProvider keyProvider(CryptoConfig config) { return new EnvKeyProvider(config); }

@Bean
public CryptoService cryptoService(KeyProvider provider, CryptoConfig config) {
    return new CryptoService(provider, config);
}
```

`BatchConfiguration` (extrait) : `TaskExecutor` (8 threads), `workerStep` avec `lineRangeReader(null, null)`, `masterStep` partitionné, `Job` avec `RunIdIncrementer`.

#### 3.3.7 Résultats mesurés

| Métrique | Valeur |
|---|---|
| Volume | 994 147 lignes |
| Durée totale | 13,535 s |
| Débit | ~73 000 lignes/s |
| Équilibrage partitions | 12,78 s à 13,00 s (écart max 221 ms, soit 1,7 %) |
| Commits | ~1 989 (un par chunk de 500) |

### 3.4 Application métier (Java 8 / PrimeFaces 10)

#### 3.4.1 Intégration du chiffrement

| Élément | Détail |
|---|---|
| **Converter** | `javax.persistence.AttributeConverter<Float, String>` |
| **Déclaration** | Dans `orm.xml` via `<convert converter="..."/>` |
| **Compatibilité** | Hibernate 5.x et EclipseLink 2.7+ |
| **Accès au service** | `CryptoHolder.encrypt(...)` / `CryptoHolder.decrypt(...)` |
| **Initialisation** | `ServletContextListener` → `CryptoBootstrap.initGlobal(...)` |
| **Variable** | `APP_CRYPTO_KEY` (identique au batch) |

Le champ Java reste `Float` ; la colonne base est `TEXT` (`v1:...`).

#### 3.4.2 UI de consultation des exécutions batch

Deux pages XHTML lisant les tables `BATCH_*` en **lecture seule** :

| Page | Contenu |
|---|---|
| `batch-executions.xhtml` | 50 dernières exécutions (`ID`, `Statut`, `Début`, `Fin`, `Durée`, `Code sortie`, `Message`), filtre par statut, tri, pagination, sélection → détail |
| `batch-execution-detail.xhtml` | En-tête (statut, durées, code sortie, message), 9 cartes de compteurs agrégés (lues, écrites, taux de succès, rejets lecture/traitement/écriture, commits, rollbacks, total rejets), tableau des 9 étapes (`masterStep` + 8 `workerStep:partition-N`) avec compteurs détaillés |

**Entités JPA** (lecture seule) : `BatchJobExecution`, `BatchStepExecution` (mappées sur `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`).

**DAO** : `findRecentExecutions(limit)`, `findExecution(id)`, `findSteps(jobExecutionId)`, `aggregate(jobExecutionId)`.

**Beans JSF** : `BatchListBean`, `BatchDetailBean` (helpers `durationOf`, `severityOf`, `truncate`).

### 3.5 Modèle de données

#### 3.5.1 Table `person`

```sql
CREATE TABLE person (
    id                BIGINT       PRIMARY KEY,
    nom               VARCHAR(100) NOT NULL,
    prenom            VARCHAR(100) NOT NULL,
    salaire_encrypted TEXT         NOT NULL,
    date_naissance    DATE,
    ville             VARCHAR(100)
);
```

**Index** : uniquement sur `id` (PK). **Aucun index** sur `salaire_encrypted` (données chiffrées non-déterministes → index inutile).

#### 3.5.2 Tables Spring Batch

Créées par Flyway (`V2__batch_schema.sql`), **jamais** par `initialize-schema: always` en production. Aucune modification manuelle.

### 3.6 Gestion de la clé

#### 3.6.1 Format

| Caractéristique | Valeur |
|---|---|
| Longueur | 32 octets (AES-256) |
| Encodage | Base64 (44 caractères) |
| Génération | `openssl rand -base64 32` |
| Stockage | **Jamais** dans le code ni dans un fichier versionné |

#### 3.6.2 Injection

Variable d'environnement `APP_CRYPTO_KEY`, identique dans les deux déploiements.

| Environnement | Mécanisme |
|---|---|
| Développement STS | `Run Configurations` → `Environment` |
| Développement terminal | `export APP_CRYPTO_KEY=$(cat .env.key)` |
| Fichier `.env` | `spring.config.import: optional:file:./.env[.properties]` (suffixe **obligatoire**) |
| Docker | `environment: APP_CRYPTO_KEY: ${APP_CRYPTO_KEY}` |
| Kubernetes | `Secret` + `secretKeyRef` |
| systemd | `EnvironmentFile=/etc/myapp/crypto.env` (chmod 600) |

**Pièges** :

- `spring.config.import` sans `[.properties]` → erreur `Unable to load config data`
- `.env.key` (extension `.key`) non lu par Spring → utiliser `.env` ou exporter en shell
- `cut -d= -f2` coupe le `=` final de la clé Base64 → utiliser `cut -d= -f2-`
- Clé tronquée (< 44 caractères) → `Clé AES invalide (Base64/longueur)`

#### 3.6.3 Rotation

Procédure avec `RotatingCryptoService` :

1. Ajouter la config `v2` avec la nouvelle clé, `currentVersion = "v2"` dans `CryptoBootstrap.initRotating(...)`
2. Déployer simultanément les deux applications
3. Re-chiffrer les données par lots (`serviceFor("v1").decrypt()` puis `serviceFor("v2").encrypt()`)
4. Retirer `v1` une fois `SELECT COUNT(*) WHERE salaire_encrypted LIKE 'v1:%'` = 0
5. Supprimer `APP_CRYPTO_KEY` (v1) de l'environnement

**Contrainte** : déploiement synchronisé des deux applications. Le format versionné rend la rotation sans downtime.

### 3.7 Dépendances inter-projets

| Projet | Dépendance | Contrainte |
|---|---|---|
| `batch-java17` | `com.example:crypto-core:1.0.0` | Version **figée** |
| `primefaces-java8` | `com.example:crypto-core:1.0.0` | Version **identique** |

**Gouvernance** : toute modification du format impose :

- Incrément de version du module
- Test de non-régression (fixture figée)
- Déploiement simultané des deux applications
- Publication dans le dépôt Maven interne

**Piège Maven** : ne pas déclarer `crypto-core` deux fois (une fois `${crypto-core.version}`, une fois `1.0.0`) → erreur `dependencies.dependency must be unique`.

---

## 4. Exigences non-fonctionnelles

### 4.1 Performance

| Métrique | Cible | Mesuré |
|---|---|---|
| Débit batch | ≥ 100 000 lignes/min (8 partitions) | ~4 400 000 lignes/min |
| Temps migration 1M lignes | ≤ 1 min | 13,5 s |
| Latence lecture applicative | < 50 ms | — |
| Empreinte mémoire batch | < 1 Go | — |

### 4.2 Sécurité

| Exigence | Mise en œuvre |
|---|---|
| Chiffrement authentifié | AES-256-GCM |
| Aléa par enregistrement | IV aléatoire (non-déterminisme) |
| Clé hors du code source | Variable d'environnement / Vault |
| Pas de log du salaire en clair | Filtres logs, revue de code |
| Pas d'index sur colonne chiffrée | Vérifié au schéma |
| Suppression du CSV après migration | Procédure d'exploitation |
| `.env` et `.env.key` dans `.gitignore` | Vérifié par `git check-ignore` |

### 4.3 Robustesse

| Exigence | Mise en œuvre |
|---|---|
| Tolérance aux lignes invalides | `skipPolicy` limité à 100 |
| Retry sur erreur transitoire BDD | `retry(TransientDataAccessException, limit=3)` |
| Reprise après arrêt | `RunIdIncrementer` + tables Spring Batch |
| Rollback par chunk | Transaction par `chunk-size` |
| Équilibrage des partitions | ±5 % (mesuré : ±1,7 %) |

### 4.4 Maintenabilité

| Exigence | Mise en œuvre |
|---|---|
| Code cryptographique unique | Module `crypto-core` partagé |
| Testabilité | `CryptoService` testable sans infra |
| Versionnement explicite | Pin de `crypto-core` dans les deux `pom.xml` |
| Lecture seule des tables `BATCH_*` | Aucun `persist`/`merge` dans l'UI |
| Configuration externalisée | `.env` + `application.yml` |

### 4.5 Portabilité base de données

| Aspect | MariaDB (mesuré) | Oracle (à valider) |
|---|---|---|
| Partitionnement Spring Batch | ✅ | ✅ |
| `JdbcBatchItemWriter` | ✅ | ✅ |
| `rewriteBatchedStatements` | ✅ Gain 30-50 % | ❌ Inexistant |
| `chunk-size` optimal | 500 | 2000-5000 |
| Paramètres JDBC | `rewriteBatchedStatements=true` | `useSharedExtendedAttributes=true`, `isUseColumnMetaData=false` |
| Schéma `BATCH_*` | `dotations_db` | Attention utilisateur = schéma |

---

## 5. Tests

### 5.1 Tests unitaires (`crypto-core`)

| Test | Vérifie |
|---|---|
| `roundTrip()` | `decrypt(encrypt(x)) == x` |
| `nonDeterministic()` | Deux chiffrements produisent des résultats différents |
| `decrypt_knownFixture()` | Un ciphertext figé se déchiffre toujours |
| `decrypt_rejectsUnknownFormat()` | Format invalide → `CryptoException` |
| `tamperDetection()` | Altération du ciphertext → `CryptoException` |
| `wrongKeyCannotDecrypt()` | Mauvaise clé → `CryptoException` |
| `encrypt_uses_current_version()` | `RotatingCryptoService` chiffre en v2 |
| `decrypt_routes_by_prefix()` | `RotatingCryptoService` déchiffre v1 et v2 |

### 5.2 Tests d'intégration (batch)

| Test | Vérifie |
|---|---|
| Migration de 100 lignes valides | 100 lignes en base, aucune en erreur |
| Migration avec 5 lignes invalides | 95 lignes + 5 rejets |
| Vérification en base | `salaire_encrypted` commence par `v1:` |
| Fichier 100k lignes | Durée < 1 minute |
| 8 partitions équilibrées | Écart de durée < 5 % |

### 5.3 Test de compatibilité inter-applications (obligatoire)

1. Batch migre `id=1, salaire=3200.50`
2. `SELECT salaire_encrypted FROM person WHERE id=1` → commence par `v1:`
3. UI PrimeFaces affiche `3200.50`
4. UI modifie à `3500.00`, enregistre
5. `SELECT salaire_encrypted` → toujours `v1:`
6. Re-migration avec `id=1` → ligne rejetée (doublon)

**Ce test est le seul garant de la non-divergence cryptographique.**

---

## 6. Déploiement

### 6.1 Prérequis

| Élément | Détail |
|---|---|
| Base de données | MariaDB 10.10+ ou Oracle 19c+ |
| Schéma `person` | Créé par Flyway |
| Schéma `BATCH_*` | Créé par Flyway (jamais `initialize-schema: always`) |
| Clé AES-256 | Générée via `openssl rand -base64 32`, 44 caractères |
| Variable `APP_CRYPTO_KEY` | Configurée dans les deux déploiements |
| Dépôt Maven | Contient `crypto-core:1.0.0` |
| `crypto-core` installé | `mvn -pl crypto-core install -DskipTests` |

### 6.2 Séquence de déploiement

```
1. Déployer crypto-core dans Nexus
2. Builder batch-java17 et primefaces-java8 contre cette version
3. Déployer primefaces-java8 (UI)
4. Déployer batch-java17
5. Exécuter le batch de migration (via JobRunner)
6. Vérifier les tables BATCH_JOB_EXECUTION / BATCH_STEP_EXECUTION
7. Exécuter le test de compatibilité inter-applications
8. Consulter batch-executions.xhtml et batch-execution-detail.xhtml
9. Archiver le CSV source (chiffré ou supprimé)
```

### 6.3 Rollback

| Situation | Action |
|---|---|
| Erreur de format ciphertext | Rollback des deux applications vers la version précédente de `crypto-core` |
| Migration partielle | Relancer le job (`RunIdIncrementer`) ou restaurer un snapshot |
| Corruption de clé | Restauration depuis le secret manager |
| Échec Flyway | Rollback Flyway (`flyway undo` ou restauration snapshot) |

---

## 7. Risques et mitigations

| # | Risque | Impact | Probabilité | Mitigation |
|---|---|---|---|---|
| R1 | Divergence de version `crypto-core` | Élevé | Moyenne | Pin de version, CI croisée, test de compatibilité |
| R2 | Clé compromise | Critique | Faible | Rotation `v1:`/`v2:`, stockage Vault/K8s |
| R3 | OOM sur gros fichier | Élevé | Faible | Lecture par plages, chunk borné, pas de persistence context |
| R4 | Lenteur de migration | Moyen | Moyenne | Partitionnement 8 threads, `JdbcBatchItemWriter`, Hikari adapté |
| R5 | Échec de chiffrement silencieux | Critique | Faible | Test `v1:` post-migration, monitoring |
| R6 | Lignes invalides bloquantes | Moyen | Élevé | `skipLimit=100`, `SkipListener` |
| R7 | Conflit transaction Spring Batch / JPA | Moyen | Faible | Pas de `@Transactional` sur le writer, JDBC batch |
| R8 | Converter legacy non déclaré dans `orm.xml` | Élevé | Moyenne | Test de compatibilité bout en bout |
| R9 | Clé tronquée par `cut -d= -f2` | Élevé | Moyenne | `cut -d= -f2-` + vérification `wc -c` = 44 |
| R10 | `classpath:` non résolu par `FileSystemResource` | Moyen | Élevée | `DefaultResourceLoader` ou `ClassPathResource` |
| R11 | Tables `BATCH_*` absentes | Élevé | Moyenne | Flyway `V2__batch_schema.sql` |

---

## 8. Livrables

| Livrable | Format | Responsable |
|---|---|---|
| Module `crypto-core` | JAR Maven | Équipe batch |
| Projet `batch-java17` (partitionné) | JAR exécutable | Équipe batch |
| Projet `primefaces-java8` (converter + UI batch) | WAR | Équipe legacy |
| Schéma SQL `person` | Script Flyway `V1__person.sql` | Équipe batch |
| Schéma SQL `BATCH_*` | Script Flyway `V2__batch_schema.sql` | Équipe batch |
| Pages `batch-executions.xhtml`, `batch-execution-detail.xhtml` | XHTML | Équipe legacy |
| Beans `BatchListBean`, `BatchDetailBean`, DAO | Java | Équipe legacy |
| `PersonSkipListener` + table `person_rejection` | Java + SQL | Équipe batch |
| Procédure de déploiement | Document | DevOps |
| Procédure de rotation de clé | Document | Sécurité + DevOps |
| Rapport de test de compatibilité | Document | QA |

---

## 9. Glossaire

| Terme | Définition |
|---|---|
| **AES-256-GCM** | Chiffrement symétrique authentifié, clé 256 bits |
| **IV** | Initialization Vector, aléa unique par opération |
| **Tag** | Code d'intégrité ajouté au ciphertext |
| **Ciphertext** | Donnée chiffrée, seule forme stockée |
| **Format `v1:`** | Préfixe versionné, permet la rotation |
| **Chunk** | Unité transactionnelle Spring Batch (500 lignes) |
| **Partition** | Sous-ensemble du fichier traité par un thread |
| **Converter JPA** | Transformation champ↔colonne (Hibernate/EclipseLink) |
| **`RotatingCryptoService`** | Service agrégateur v1/v2 pour rotation sans downtime |
| **`DefaultResourceLoader`** | Résolveur Spring qui gère `classpath:` et `file:` |
| **`BATCH_*`** | Tables Spring Batch d'historique des exécutions |

---

## 10. Synthèse

La solution repose sur **quatre piliers** :

1. **Un module partagé `crypto-core`** (Java 8), unique source de vérité pour AES-256-GCM et le format `v1:Base64(IV‖CT‖TAG)`. Il encapsule l'algorithme (`CryptoService`), la rotation (`RotatingCryptoService`), la source de clé (`KeyProvider`), la configuration (`CryptoConfig`) et l'accès statique (`CryptoHolder`).

2. **Un batch Spring Boot 3.1.3 / Java 17 partitionné** : `LineRangePartitioner` découpe le fichier en 8 plages, `TaskExecutor` lance 8 `workerStep` en parallèle, chacun avec son propre `LineRangeFlatFileReader`. Le chiffrement est **explicite** dans `PersonRowProcessor`, l'écriture est **JDBC batchée**. Résultat mesuré : **994 147 lignes en 13,5 s** (~73 000 lignes/s).

3. **Une application PrimeFaces / Java 8** avec un **converter JPA javax** déclaré dans `orm.xml`, transparent pour le code métier, et **deux écrans de consultation** des exécutions batch (`batch-executions.xhtml`, `batch-execution-detail.xhtml`) lisant les tables `BATCH_*` en lecture seule.

4. **Un contrat cryptographique** garanti par le partage du même module `crypto-core`, de la même version, et de la même clé injectée par `APP_CRYPTO_KEY`. Le format versionné `v1:` prépare la rotation sans downtime via `RotatingCryptoService`.

Le **test de compatibilité bout en bout** (batch migre → UI affiche en clair → UI modifie → batch relit) est le seul garde-fou contre une divergence silencieuse. Il est **obligatoire après chaque déploiement**.

Les mesures de performance (13,5 s pour 1M lignes, équilibrage à 1,7 %) confirment que l'architecture partitionnée est adaptée aux volumes cibles, et sa portabilité vers Oracle est assurée par un simple ajustement de `chunk-size` et des paramètres JDBC.