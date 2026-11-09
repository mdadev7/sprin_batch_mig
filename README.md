



# Spécification fonctionnelle et technique
## Solution de migration de données avec chiffrement du salaire

**Version** : 1.0
**Statut** : Proposition
**Date** : 2026-11-06

---

## 1. Contexte et objectifs

### 1.1 Contexte

L'entreprise dispose de données RH structurées dans des fichiers plats (CSV `;`) contenant des informations personnelles — dont le **salaire**, donnée sensible au sens RGPD. Ces données doivent être migrées vers une base de données relationnelle commune à deux applications existantes :

| Application | Stack | Rôle |
|---|---|---|
| **Batch de migration** | Spring Boot 3.1.3 / Java 17 / Spring Batch 5 | Consommation du fichier plat, transformation, insertion en base |
| **Application métier** | Java 8 / JSF PrimeFaces 10 / JPA (Hibernate 5 + EclipseLink) | Consultation et gestion des données migrées |

Le **salaire** doit être **chiffré au repos** en base, conformément à la politique de sécurité des données personnelles.

### 1.2 Objectifs

| # | Objectif | Critère de succès |
|---|---|---|
| O1 | Migrer les fichiers plats vers la base relationnelle | 100 % des lignes valides migrées |
| O2 | Garantir le chiffrement du salaire en base | Aucun salaire en clair en base, y compris en backup |
| O3 | Assurer la compatibilité de lecture entre batch et applicatif | Une valeur chiffrée par le batch est déchiffrable par l'UI, et vice-versa |
| O4 | Supporter des fichiers volumineux | ≥ 100 000 lignes/minute sur infrastructure cible |
| O5 | Permettre la rotation de clé sans downtime | Procédure documentée, applicative |
| O6 | Isoler la logique cryptographique | Un seul module partagé, aucune duplication de code AES |

### 1.3 Hors périmètre

- Migration des données historiques antérieures au CSV
- Modification du schéma de l'application métier (hors colonne `salaire_encrypted`)
- Chiffrement d'autres colonnes (nom, prénom, ville) — non demandé
- Interface utilisateur de migration (batch en ligne de commande)
- Archivage des fichiers CSV après migration

---

## 2. Spécifications fonctionnelles

### 2.1 Format d'entrée

**Fichier plat CSV** avec séparateur `;`, encodage UTF-8, avec en-tête.

**Colonnes attendues** :

| Position | Champ | Type | Contrainte | Exemple |
|---|---|---|---|---|
| 1 | `id` | Entier | > 0, unique | `1` |
| 2 | `nom` | Chaîne | non vide, ≤ 100 | `Dupont` |
| 3 | `prenom` | Chaîne | non vide, ≤ 100 | `Jean` |
| 4 | `salaire` | Décimal | > 0, format `1234.56` | `3200.50` |
| 5 | `date_naissance` | Date | format `dd/MM/yyyy` | `15/03/1985` |
| 6 | `ville` | Chaîne | ≤ 100 | `Paris` |

**Exemple** :
```
id;nom;prenom;salaire;date_naissance;ville
1;Dupont;Jean;3200.50;15/03/1985;Paris
2;Martin;Sophie;4100.00;22/07/1990;Lyon
```

### 2.2 Règles de gestion

| # | Règle | Comportement |
|---|---|---|
| RG1 | Ligne vide (aucun caractère) | Ignorée silencieusement |
| RG2 | Ligne avec moins de 6 colonnes | Rejetée, comptée en erreur |
| RG3 | `id` en doublon dans le fichier | Rejeté, comptée en erreur |
| RG4 | `salaire` non numérique | Rejetée, comptée en erreur |
| RG5 | `date_naissance` non parsable | Rejetée, comptée en erreur |
| RG6 | Le salaire doit être **chiffré** avant insertion en base | Obligatoire |
| RG7 | Un échec de chiffrement | Rejette l'enregistrement, compté en erreur |
| RG8 | En deçà de 100 erreurs cumulées | Le job continue |
| RG9 | Au-delà de 100 erreurs cumulées | Le job s'arrête en échec |
| RG10 | Un id déjà présent en base | Rejeté (comportement par défaut du batch) |

### 2.3 Rapport d'exécution

Le batch produit en fin d'exécution un rapport contenant :

- Date/heure de début et de fin
- Nombre total de lignes lues
- Nombre de lignes migrées avec succès
- Nombre de lignes rejetées (par motif)
- Liste des 10 premières erreurs avec numéro de ligne
- Statut final : `COMPLETED` / `FAILED` / `STOPPED`

Le rapport est accessible via les tables Spring Batch (`BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`) et complété par des logs applicatifs.

### 2.4 Critères d'acceptation fonctionnels

| # | Critère | Vérification |
|---|---|---|
| CAF1 | Un fichier de 100 lignes valides → 100 lignes en base | Requête `COUNT(*)` |
| CAF2 | Un fichier de 100 lignes dont 5 invalides → 95 lignes en base + 5 rejets | Rapport + `COUNT(*)` |
| CAF3 | Aucune valeur de salaire en clair en base | `SELECT` + inspection |
| CAF4 | Une ligne migrée par le batch est lisible par l'UI avec le bon salaire | Test manuel de bout en bout |
| CAF5 | Un salaire modifié dans l'UI est relisible par le batch dans une migration ultérieure | Test croisé |

---

## 3. Spécifications techniques

### 3.1 Architecture applicative

```
┌───────────────────────────────────────────────────────────────────────┐
│                         crypto-core (Java 8)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────────────┐  │
│  │CryptoService │  │ KeyProvider  │  │    CryptoBootstrap          │  │
│  │ AES-256-GCM  │←─│  interface   │←─│    EnvKeyProvider           │  │
│  │ format v1:   │  └──────────────┘  │    CryptoHolder             │  │
│  └──────────────┘                    └─────────────────────────────┘  │
└───────────────────────────────┬───────────────────────────────────────┘
                                │  dépendance Maven partagée
              ┌─────────────────┴─────────────────┐
              │                                   │
┌─────────────▼──────────────────┐   ┌────────────▼────────────────────┐
│  batch-java17                  │   │  primefaces-java8               │
│  Spring Boot 3.1.3             │   │  JSF / Hibernate 5 / EclipseLink│
│  Spring Batch 5                │   │                                 │
│  JPA (jakarta) + JDBC          │   │  JPA (javax) XML mapping        │
└───────────────┬────────────────┘   └────────────┬────────────────────┘
                │                                 │
                └───────────────┬─────────────────┘
                                ▼
                    ┌───────────────────────┐
                    │  Base PostgreSQL      │
                    │  table person         │
                    └───────────────────────┘
```

### 3.2 Module `crypto-core`

**Rôle** : unique source de vérité pour l'algorithme de chiffrement, le format, et l'accès à la clé.

| Élément | Détail |
|---|---|
| **Langage** | Java 8 (`maven.compiler.release=8`) — compatible Java 8 et Java 17 |
| **Dépendances** | JDK pur (pas de JPA, pas de Spring, pas de SDK cloud) |
| **Algorithme** | AES-256-GCM (`AES/GCM/NoPadding`) |
| **Taille de clé** | 32 octets (AES-256) |
| **IV** | 12 octets, **aléatoire par opération** (`SecureRandom`) |
| **Tag d'authentification** | 128 bits |
| **Format de sortie** | `v1:Base64(IV || ciphertext || tag)` |
| **Thread-safety** | Oui (aucun état mutable partagé) |
| **Interface publique** | `String encrypt(String)`, `String decrypt(String)` |
| **Portabilité** | Compatible Hibernate 5, EclipseLink 2.7+, Spring Boot 3 |

**Versionnement du format** : le préfixe `v1:` permet d'introduire ultérieurement `v2:` (nouvelle clé ou algorithme) tout en continuant à déchiffrer `v1:`. Toute introduction d'une nouvelle version impose un **déploiement simultané** des deux applications.

**Test de non-régression** : un vecteur de chiffrement figé (`v1:...`) est stocké dans les tests unitaires ; son déchiffrement doit rester valide à chaque version du module.

### 3.3 Batch de migration (Java 17 / Spring Boot 3.1.3)

#### 3.3.1 Architecture interne

```
Fichier CSV
    │
    ▼
LineRangePartitioner  ──► 8 partitions (plages de lignes)
    │
    ▼
MasterStep (orchestrateur)  ──► ThreadPoolTaskExecutor (8 threads)
    │
    ├── Worker 1 : Reader → Processor → Writer
    ├── Worker 2 : Reader → Processor → Writer
    └── ...
```

#### 3.3.2 Composants

| Composant | Type | Rôle |
|---|---|---|
| `LineRangePartitioner` | `Partitioner` | Compte les lignes une fois, crée 8 sous-ensembles |
| `LineRangeFlatFileReader` | `ItemStreamReader<PersonCsv>` | Lit une plage `[startLine, endLine)`, `@StepScope` |
| `PersonRowProcessor` | `ItemProcessor<PersonCsv, PersonRow>` | Mappe et **chiffre explicitement** le salaire |
| `JdbcBatchItemWriter` | `ItemWriter<PersonRow>` | Insertion JDBC batchée, `@StepScope` |

#### 3.3.3 Paramètres d'exécution

| Paramètre | Valeur par défaut | Justification |
|---|---|---|
| `batch.chunk-size` | 500 | Compromis mémoire / débit |
| `batch.grid-size` | 8 | Égal au nombre de cœurs disponibles |
| `batch.throttle-limit` | 8 | Aligné sur grid-size |
| `spring.datasource.hikari.maximum-pool-size` | 12 | grid-size + marge |
| `spring.batch.jdbc.initialize-schema` | `always` (dev) / `never` (prod) | Flyway en prod |

#### 3.3.4 Choix technologique : JDBC vs JPA

**Décision retenue** : **JDBC** (`JdbcBatchItemWriter` + `DataSource`).

**Justification** :

| Critère | JDBC | JPA |
|---|---|---|
| Débit d'écriture | 20–35 k lignes/s | 8–13 k lignes/s |
| Empreinte mémoire | Constante | Persistence context à gérer |
| Risque OOM sur gros volume | Nul | Présent sans `flush/clear` |
| Chiffrement | **Explicite** dans le processor | Automatique (converter) mais implicite |
| Auditabilité | Visible dans le code | Effet de bord du flush |

**Conséquence** : le batch ne dépend **pas** d'Hibernate pour l'écriture. Le converter JPA (`FloatCryptedConverter`) reste présent dans le projet batch uniquement pour les éventuels usages JPA internes (lecture, tests), mais n'est **pas** sur le chemin de migration.

### 3.4 Application métier (Java 8 / PrimeFaces 10)

#### 3.4.1 Intégration du chiffrement

| Élément | Détail |
|---|---|
| **Converter** | `javax.persistence.AttributeConverter<Float, String>` |
| **Déclaration** | Dans `orm.xml` via `<convert converter="..."/>` |
| **Compatibilité** | Hibernate 5.x et EclipseLink 2.7+ (API JPA 2.1+) |
| **Accès au service** | `CryptoHolder.get()` (état statique initialisé au démarrage) |
| **Initialisation** | `ServletContextListener` ou `@Startup` CDI, une seule fois |
| **Variable d'environnement** | `APP_CRYPTO_KEY` (identique au batch) |

#### 3.4.2 Format de mappage

```xml
<basic name="salaire">
    <column name="salaire_encrypted"/>
    <convert converter="com.example.legacy.crypto.FloatCryptedConverter"/>
</basic>
```

Le champ Java reste `Float` dans l'entité ; la colonne base est `TEXT` (contenant la chaîne `v1:...`).

### 3.5 Modèle de données

#### 3.5.1 Table `person`

```sql
CREATE TABLE person (
    id               BIGINT       PRIMARY KEY,
    nom              VARCHAR(100) NOT NULL,
    prenom           VARCHAR(100) NOT NULL,
    salaire_encrypted TEXT        NOT NULL,
    date_naissance   DATE,
    ville            VARCHAR(100)
);
```

| Colonne | Type | Contrainte | Commentaire |
|---|---|---|---|
| `id` | BIGINT | PK | Affecté manuellement (vient du CSV) |
| `nom` | VARCHAR(100) | NOT NULL | |
| `prenom` | VARCHAR(100) | NOT NULL | |
| `salaire_encrypted` | TEXT | NOT NULL | Chaîne `v1:Base64(...)` |
| `date_naissance` | DATE | | |
| `ville` | VARCHAR(100) | | |

**Index** : uniquement sur `id` (clé primaire). **Aucun index** sur `salaire_encrypted` — un index sur données chiffrées non-déterministes est inutile et coûteux.

#### 3.5.2 Tables Spring Batch

Créées par `spring.batch.jdbc.initialize-schema`. En production, gérées par Flyway/Liquibase. Aucune modification manuelle.

### 3.6 Gestion de la clé

#### 3.6.1 Format

| Caractéristique | Valeur |
|---|---|
| Longueur | 32 octets (AES-256) |
| Encodage de transport | Base64 |
| Génération | `openssl rand -base64 32` |
| Stockage | **Jamais** dans le code source, jamais dans un fichier versionné |

#### 3.6.2 Injection

**Option retenue pour ce projet** : **variable d'environnement** `APP_CRYPTO_KEY`, identique dans les deux déploiements.

| Environnement | Mécanisme |
|---|---|
| Développement | `export APP_CRYPTO_KEY=$(cat .env.dev.key)` |
| Docker | `environment: APP_CRYPTO_KEY: ${APP_CRYPTO_KEY}` |
| Kubernetes | `Secret` + `secretKeyRef` |
| Serveur systemd | `EnvironmentFile=/etc/myapp/crypto.env` (chmod 600) |

**Alternative** : `VaultKeyProvider` (module séparé `crypto-vault-starter`) si la politique interne l'impose.

#### 3.6.3 Rotation

Procédure documentée :
1. Ajouter la nouvelle clé (version `v2:`) dans `CryptoService` tout en conservant la clé `v1:` pour la lecture
2. Déployer simultanément les deux applications
3. Re-chiffrer les données par lots (job dédié)
4. Retirer la clé `v1:` une fois la migration terminée

**Contrainte** : la rotation exige un **déploiement synchronisé** des deux applications. Le format versionné `v1:`/`v2:` rend la procédure sans downtime.

### 3.7 Dépendances inter-projets

| Projet | Dépendance `crypto-core` | Contrainte |
|---|---|---|
| `batch-java17` | `com.example:crypto-core:1.0.0` | Version **figée** |
| `primefaces-java8` | `com.example:crypto-core:1.0.0` | Version **identique** |

**Règle de gouvernance** : toute modification du format de chiffrement dans `crypto-core` impose :
- Incrément de version du module
- Test de non-régression (fixture figée)
- Déploiement simultané des deux applications
- Publication dans le dépôt Maven interne (Nexus/Artifactory)

---

## 4. Exigences non-fonctionnelles

### 4.1 Performance

| Métrique | Cible |
|---|---|
| Débit batch | ≥ 100 000 lignes/minute (8 partitions) |
| Temps de migration 1M lignes | ≤ 1 minute |
| Latence de lecture applicative | < 50 ms par enregistrement |
| Empreinte mémoire batch | < 1 Go (constante quelle que soit la taille du fichier) |

### 4.2 Sécurité

| Exigence | Mise en œuvre |
|---|---|
| Chiffrement authentifié | AES-256-GCM |
| Aléa par enregistrement | IV aléatoire (non-déterminisme) |
| Clé hors du code source | Variable d'environnement / Vault |
| Pas de log du salaire en clair | Filtres de logs, revue de code |
| Pas d'index sur colonne chiffrée | Vérifié au niveau schéma |
| Suppression du CSV après migration | Procédure d'exploitation |

### 4.3 Robustesse

| Exigence | Mise en œuvre |
|---|---|
| Tolérance aux lignes invalides | `skip(IllegalArgumentException, limit=100)` |
| Retry sur erreur transitoire BDD | `retry(TransientDataAccessException, limit=3)` |
| Reprise après arrêt | `RunIdIncrementer` + tables Spring Batch |
| Rollback par chunk | Transaction par `chunk-size` |

### 4.4 Maintenabilité

| Exigence | Mise en œuvre |
|---|---|
| Code cryptographique unique | Module `crypto-core` partagé |
| Testabilité | `CryptoService` testable sans infrastructure |
| Versionnement explicite | Pin de la version `crypto-core` dans les deux `pom.xml` |

---

## 5. Tests

### 5.1 Tests unitaires (`crypto-core`)

| Test | Vérifie |
|---|---|
| `roundTrip()` | `decrypt(encrypt(x)) == x` |
| `nonDeterministic()` | Deux chiffrements produisent des résultats différents |
| `decrypt_knownFixture()` | Un ciphertext figé se déchiffre toujours |
| `decrypt_rejectsUnknownFormat()` | Un format invalide lève une exception |

### 5.2 Tests d'intégration (batch)

| Test | Vérifie |
|---|---|
| Migration de 100 lignes valides | 100 lignes en base, aucune en erreur |
| Migration avec lignes invalides | Bon compte de rejets |
| Vérification en base | `salaire_encrypted` commence par `v1:` |
| Fichier volumineux (100k lignes) | Durée < 1 minute |

### 5.3 Test de compatibilité inter-applications

**Test manuel obligatoire après chaque déploiement** :

1. Batch migre une ligne `id=1, salaire=3200.50`
2. Vérifier en base : `SELECT salaire_encrypted FROM person WHERE id=1` → commence par `v1:`
3. Ouvrir l'UI PrimeFaces, consulter la personne 1
4. Vérifier que le salaire affiché est `3200.50`
5. Modifier le salaire dans l'UI à `3500.00`, enregistrer
6. Vérifier en base que la nouvelle valeur commence toujours par `v1:`
7. Relancer une migration contenant `id=1` → la ligne doit être rejetée (doublon)

**Ce test est le seul garant de la non-divergence cryptographique entre les deux applications.**

---

## 6. Déploiement

### 6.1 Prérequis

| Élément | Détail |
|---|---|
| Base de données | PostgreSQL (ou compatible JDBC) |
| Schéma `person` | Créé par migration Flyway |
| Clé AES-256 | Générée, stockée dans le secret manager |
| Variable `APP_CRYPTO_KEY` | Configurée sur les deux déploiements |
| Dépôt Maven | Contient `crypto-core:1.0.0` |

### 6.2 Séquence de déploiement

```
1. Déployer crypto-core dans Nexus
2. Builder batch-java17 et primefaces-java8 contre cette version
3. Déployer primefaces-java8 (UI)
4. Déployer batch-java17
5. Exécuter le batch de migration
6. Exécuter le test de compatibilité inter-applications
7. Archiver le CSV source (chiffré ou supprimé selon procédure)
```

### 6.3 Rollback

| Situation | Action |
|---|---|
| Erreur de format de ciphertext | Rollback des deux applications vers la version précédente de `crypto-core` |
| Erreur de migration partielle | Relancer le job (`RunIdIncrementer`) ou restaurer un snapshot |
| Corruption de clé | Restauration de la clé depuis le secret manager |

---

## 7. Risques et mitigations

| # | Risque | Impact | Probabilité | Mitigation |
|---|---|---|---|---|
| R1 | Divergence de version `crypto-core` entre les deux projets | Élevé (données illisibles) | Moyenne | Pin de version, CI croisée, test de compatibilité |
| R2 | Clé compromise | Critique | Faible | Rotation via format `v1:`/`v2:`, stockage dans Vault/K8s Secret |
| R3 | OOM sur gros fichier | Élevé | Faible | Lecture par plages, `chunk-size` borné, pas de persistence context |
| R4 | Lenteur de migration | Moyen | Moyenne | Partitionnement 8 threads, `JdbcBatchItemWriter`, Hikari adapté |
| R5 | Échec de chiffrement silencieux | Critique | Faible | Test de vérification `v1:` après migration, monitoring |
| R6 | Lignes invalides bloquant le job | Moyen | Élevé | `skip` limité à 100, rapport d'erreurs |
| R7 | Conflit de transaction Spring Batch / JPA | Moyen | Faible | Pas de `@Transactional` sur le writer, JDBC batch |
| R8 | Converter legacy non déclaré dans `orm.xml` | Élevé (lecture en clair) | Moyenne | Test de compatibilité bout en bout |

---

## 8. Livrables

| Livrable | Format | Responsable |
|---|---|---|
| Module `crypto-core` | JAR Maven | Équipe batch |
| Projet `batch-java17` | JAR exécutable | Équipe batch |
| Projet `primefaces-java8` (modifications) | WAR | Équipe legacy |
| Schéma SQL `person` | Script Flyway | Équipe batch |
| Procédure de déploiement | Document | DevOps |
| Procédure de rotation de clé | Document | Sécurité + DevOps |
| Rapport de test de compatibilité | Document | QA |

---

## 9. Glossaire

| Terme | Définition |
|---|---|
| **AES-256-GCM** | Algorithme de chiffrement symétrique authentifié à clé de 256 bits |
| **IV** | Initialization Vector, aléa unique par opération de chiffrement |
| **Tag d'authentification** | Code d'intégrité ajouté au ciphertext, détecte toute altération |
| **Ciphertext** | Donnée chiffrée, seule forme stockée en base |
| **Format `v1:`** | Préfixe versionné du ciphertext, permet la rotation de clé |
| **Chunk** | Unité transactionnelle de Spring Batch (500 lignes) |
| **Partition** | Sous-ensemble du fichier traité par un thread |
| **Converter JPA** | Mécanisme Hibernate/EclipseLink de transformation champ↔colonne |

---

## 10. Synthèse

La solution repose sur **trois piliers** :

1. **Un module partagé `crypto-core`** (Java 8), unique source de vérité pour l'algorithme AES-256-GCM et le format `v1:Base64(IV||CT||TAG)`. Il encapsule l'algorithme et la source de clé (`KeyProvider`), sans dépendance JPA ni SDK externe.

2. **Un batch Spring Boot 3.1.3 / Java 17** qui migre les fichiers par **partitionnement multi-thread** et **écriture JDBC batchée**, avec chiffrement **explicite** dans le processor. Choix retenu pour ses performances (2–3× JPA) et son absence de risque OOM.

3. **Une application PrimeFaces / Java 8** qui accède aux données via un **converter JPA `javax`** déclaré dans `orm.xml`, transparent pour le code métier.

Le **contrat cryptographique** entre les deux applications est garanti par le partage du même module `crypto-core`, de la même version, et de la même clé injectée par variable d'environnement. Le **format versionné `v1:`** prépare la rotation de clé sans downtime. Le **test de compatibilité bout en bout** est le seul garde-fou contre une divergence silencieuse — il est obligatoire après chaque déploiement.# Migration Crypto Project

Ce projet gère la couche de chiffrement/déchiffrement commune (`crypto-core`) partageable entre des applications legacy Java 8 (`primefaces-java8`) et des batchs modernes Java 17 (`batch-java17`).

# 5.3 Génération de la clé

bash
openssl rand -base64 32


# 7. Compilation des trois modules
Dans l'ordre :

bash
# 1. Module cryptographique
cd crypto-core
mvn clean test install

# 2. Batch Java 17
cd ../batch-java17
mvn clean package

# 3. Application PrimeFaces Java 8
cd ../primefaces-java8
mvn clean package

openssl rand -base64 32
