package com.dev.generateur.mode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Générateur de batch de migration à partir d'une classe modèle.
 *
 * Usage :
 *   java BatchGenerator \
 *     com.example.batch.model.PersonneRow \
 *     --package com.example.batch \
 *     --basename Personne \
 *     --table personne \
 *     --source DATABASE \
 *     --out ./generated \
 *     --delimiter ';' \
 *     --partitioned false \
 *     --key numSomme \
 *     --dates dateNaissance,dateRecrutement
 */
	
public class BatchGenerator {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: BatchGenerator <fqcn> [options]");
            System.exit(1);
        }
        String fqcn = args[0];
        String basePackage = "com.example.batch";
        String baseName = null;
        String tableName = null;
        SourceType sourceType = SourceType.FILE;
        Path out = Path.of("generated");
        char delimiter = ';';
        boolean partitioned = true;
        String keyField = "id";
        String[] dateFields = new String[0];

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--package"     -> basePackage = args[++i];
                case "--basename"    -> baseName = args[++i];
                case "--table"       -> tableName = args[++i];
                case "--source"      -> sourceType = SourceType.valueOf(args[++i].toUpperCase());
                case "--out"         -> out = Path.of(args[++i]);
                case "--delimiter"   -> delimiter = args[++i].charAt(0);
                case "--partitioned" -> partitioned = Boolean.parseBoolean(args[++i]);
                case "--key"         -> keyField = args[++i];
                case "--dates"       -> dateFields = args[++i].split(",");
                default -> throw new IllegalArgumentException("Option inconnue : " + args[i]);
            }
        }

        Class<?> model = Class.forName(fqcn);
        if (baseName == null) baseName = model.getSimpleName().replaceAll("(Row|Csv)$", "");
        if (tableName == null) tableName = camelToSnake(baseName);

        List<Field> fields = new ArrayList<>();
        for (Field f : model.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getName().equals("resolvedPersonneId")) continue; // champ transient
            fields.add(f);
        }

        GeneratorContext ctx = new GeneratorContext(
                model, basePackage, baseName, tableName, sourceType,
                out, delimiter, partitioned, fields, keyField, dateFields);

        Files.createDirectories(out);

        // 1. Modèle d'entrée (pour source BASE ou FILE, on garde le modèle tel quel)
        write(out.resolve(model.getSimpleName() + ".java"), modelTemplate(ctx));

        // 2. Reader (dépend du type source)
        if (sourceType == SourceType.FILE) {
            write(out.resolve("LineRange" + baseName + "Reader.java"), fileReaderTemplate(ctx));
        } else {
            write(out.resolve(baseName + "DbItemReader.java"), dbReaderTemplate(ctx));
        }

        // 3. Processor
        write(out.resolve(baseName + "Processor.java"), processorTemplate(ctx));

        // 4. Writer
        write(out.resolve(baseName + "Writer.java"), writerTemplate(ctx));

        // 5. Skip listener
        write(out.resolve(baseName + "SkipListener.java"), skipListenerTemplate(ctx));

        // 6. Repository (si source BASE ou writer avec upsert)
        if (sourceType == SourceType.DATABASE) {
            write(out.resolve(baseName + "Repository.java"), repositoryTemplate(ctx));
        }

        // 7. Configuration
        write(out.resolve(baseName + "BatchConfiguration.java"), configTemplate(ctx));

        // 8. SQL
        write(out.resolve(tableName + "_schema.sql"), sqlTemplate(ctx));

        System.out.println("✔ Génération terminée dans " + out.toAbsolutePath());
    }

    // ---------- Écriture ----------

    private static void write(Path p, String content) throws IOException {
        Files.writeString(p, content);
        System.out.println("  → " + p.getFileName());
    }

    // ---------- Templates ----------

    private static String modelTemplate(GeneratorContext c) {
        // Le modèle est déjà fourni par l'utilisateur — on ne le régénère pas.
        // Mais on peut générer une version "propre" pour référence.
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(c.basePackage).append(".model;\n\n");
        sb.append("import lombok.Getter;\nimport lombok.Setter;\n\n");
        sb.append("@Getter\n@Setter\n");
        sb.append("public class ").append(c.model.getSimpleName()).append(" {\n");
        for (Field f : c.fields) {
            sb.append("    private ").append(f.getType().getSimpleName())
              .append(" ").append(f.getName()).append(";\n");
        }
        sb.append("\n    private transient Long resolvedPersonneId;\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String fileReaderTemplate(GeneratorContext c) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < c.fields.size(); i++) {
            names.append('"').append(c.fields.get(i).getName()).append('"');
            if (i < c.fields.size() - 1) names.append(", ");
        }
        return """
            package %s.reader;

            import org.springframework.batch.item.file.FlatFileItemReader;
            import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
            import org.springframework.batch.item.file.mapping.DefaultLineMapper;
            import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
            import org.springframework.core.io.FileSystemResource;

            import %s.model.%s;

            public class LineRange%sReader extends FlatFileItemReader<%s> {

                public LineRange%sReader(String inputFile, int startLine, int endLine) {
                    setResource(new FileSystemResource(inputFile));
                    setLinesToSkip(startLine - 1);
                    setMaxItemCount(endLine - startLine + 1);
                    setStrict(false);

                    DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer("%c");
                    tokenizer.setNames(%s);
                    tokenizer.setStrict(false);

                    BeanWrapperFieldSetMapper<%s> mapper = new BeanWrapperFieldSetMapper<>();
                    mapper.setTargetType(%s.class);
                    mapper.setDistanceLimit(0);

                    DefaultLineMapper<%s> lm = new DefaultLineMapper<>();
                    lm.setLineTokenizer(tokenizer);
                    lm.setFieldSetMapper(mapper);
                    setLineMapper(lm);
                }
            }
            """.formatted(c.basePackage, c.basePackage, c.model.getSimpleName(),
                          c.baseName, c.model.getSimpleName(),
                          c.baseName, c.csvDelimiter, names.toString(),
                          c.model.getSimpleName(), c.model.getSimpleName(),
                          c.model.getSimpleName());
    }

    private static String dbReaderTemplate(GeneratorContext c) {
        String selectCols = c.fields.stream()
                .map(f -> camelToSnake(f.getName()).toUpperCase())
                .reduce((a, b) -> a + ", " + b).orElse("");
        String rowMapperSetters = c.fields.stream()
                .map(f -> String.format(
                    "                r.set%s(rs.getString(\"%s\"));",
                    capitalize(f.getName()), camelToSnake(f.getName()).toUpperCase()))
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return """
            package %s.reader;

            import java.util.HashMap;
            import java.util.Map;

            import javax.sql.DataSource;

            import org.springframework.batch.item.database.JdbcPagingItemReader;
            import org.springframework.batch.item.database.Order;
            import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;

            import %s.model.%s;

            public class %sDbItemReader extends JdbcPagingItemReader<%s> {

                public %sDbItemReader(DataSource sourceDataSource,
                                              String schema, String table, int pageSize) {
                    try {
                        SqlPagingQueryProviderFactoryBean provider =
                                new SqlPagingQueryProviderFactoryBean();
                        provider.setDataSource(sourceDataSource);
                        provider.setSelectClause("%s");
                        provider.setFromClause(schema + "." + table);
                        provider.setSortKey("%s");
                        Map<String, Order> sortKeys = new HashMap<>();
                        sortKeys.put("%s", Order.ASCENDING);
                        provider.setSortKeys(sortKeys);
                        provider.afterPropertiesSet();

                        setDataSource(sourceDataSource);
                        setQueryProvider(provider.getObject());
                        setPageSize(pageSize);
                        setRowMapper((rs, i) -> {
                            %s r = new %s();
            %s
                            return r;
                        });
                        setName("%sDbItemReader");
                    } catch (Exception e) {
                        throw new IllegalStateException("Erreur init %sDbItemReader", e);
                    }
                }
            }
            """.formatted(c.basePackage, c.basePackage, c.model.getSimpleName(),
                          c.baseName, c.model.getSimpleName(),
                          c.baseName,
                          selectCols,
                          camelToSnake(c.keyField).toUpperCase(),
                          camelToSnake(c.keyField).toUpperCase(),
                          c.model.getSimpleName(), c.model.getSimpleName(),
                          rowMapperSetters,
                          c.baseName, c.baseName);
    }

    private static String processorTemplate(GeneratorContext c) {
        String dateSetters = (c.dateFields.length == 0) ? "" :
                java.util.Arrays.stream(c.dateFields)
                    .map(d -> String.format(
                        "        item.set%s(%s.formatedStringDate(item.get%s()));",
                        capitalize(d), c.model.getSimpleName(), capitalize(d)))
                    .reduce((a, b) -> a + "\n" + b).orElse("");
        return """
            package %s.processor;

            import org.springframework.batch.item.ItemProcessor;
            import org.springframework.stereotype.Component;

            import %s.model.%s;

            @Component
            public class %sProcessor implements ItemProcessor<%s, %s> {

                @Override
                public %s process(%s item) {
                    if (item.get%s() == null || item.get%s().isBlank()) {
                        throw new IllegalArgumentException("%s manquant");
                    }
                    // TODO : validations métier supplémentaires
            %s
                    return item;
                }
            }
            """.formatted(c.basePackage, c.basePackage, c.model.getSimpleName(),
                          c.baseName, c.model.getSimpleName(), c.model.getSimpleName(),
                          c.model.getSimpleName(), c.model.getSimpleName(),
                          capitalize(c.keyField), capitalize(c.keyField),
                          c.keyField, dateSetters);
    }

    private static String writerTemplate(GeneratorContext c) {
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        StringBuilder setters = new StringBuilder();
        for (int i = 0; i < c.fields.size(); i++) {
            String name = c.fields.get(i).getName();
            cols.append(camelToSnake(name));
            vals.append(':').append(name);
            setters.append("                    .addValue(\"%s\", r.get%s())\n"
                    .formatted(name, capitalize(name)));
            if (i < c.fields.size() - 1) { cols.append(", "); vals.append(", "); }
        }
        return """
            package %s.writer;

            import org.springframework.batch.item.Chunk;
            import org.springframework.batch.item.ItemWriter;
            import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
            import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
            import org.springframework.stereotype.Component;

            import %s.model.%s;

            @Component
            public class %sWriter implements ItemWriter<%s> {

                private static final String SQL =
                    "INSERT INTO %s (%s) VALUES (%s)";

                private final NamedParameterJdbcTemplate jdbc;

                public %sWriter(NamedParameterJdbcTemplate jdbc) {
                    this.jdbc = jdbc;
                }

                @Override
                public void write(Chunk<? extends %s> chunk) {
                    for (%s r : chunk.getItems()) {
                        jdbc.update(SQL, new MapSqlParameterSource()
            %s                );
                    }
                }
            }
            """.formatted(c.basePackage, c.basePackage, c.model.getSimpleName(),
                          c.baseName, c.model.getSimpleName(),
                          c.tableName, cols, vals,
                          c.baseName, c.model.getSimpleName(), c.model.getSimpleName(),
                          setters.toString());
    }

    private static String skipListenerTemplate(GeneratorContext c) {
        return """
            package %s.listener;

            import %s.model.%s;
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            import org.springframework.batch.core.ExitStatus;
            import org.springframework.batch.core.SkipListener;
            import org.springframework.batch.core.StepExecution;
            import org.springframework.batch.core.StepExecutionListener;
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.transaction.PlatformTransactionManager;
            import org.springframework.transaction.TransactionDefinition;
            import org.springframework.transaction.support.TransactionTemplate;

            public class %sSkipListener
                    implements SkipListener<%s, %s>, StepExecutionListener {

                private static final Logger log = LoggerFactory.getLogger(%sSkipListener.class);
                private final JdbcTemplate jdbcTemplate;
                private final TransactionTemplate requiresNew;
                private Long jobExecutionId;
                private String stepName;

                public %sSkipListener(JdbcTemplate jdbcTemplate,
                                              PlatformTransactionManager txManager) {
                    this.jdbcTemplate = jdbcTemplate;
                    this.requiresNew = new TransactionTemplate(txManager);
                    this.requiresNew.setPropagationBehavior(
                            TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                }

                @Override public void beforeStep(StepExecution s) {
                    this.jobExecutionId = s.getJobExecutionId();
                    this.stepName = s.getStepName();
                }
                @Override public ExitStatus afterStep(StepExecution s) { return null; }

                @Override public void onSkipInRead(Throwable t) {
                    insert(null, t.getMessage());
                }
                @Override public void onSkipInProcess(%s item, Throwable t) {
                    insert(item != null ? item.get%s() : null, t.getMessage());
                }
                @Override public void onSkipInWrite(%s item, Throwable t) {
                    insert(item != null ? item.get%s() : null, t.getMessage());
                }

                private void insert(String key, String reason) {
                    if (jobExecutionId == null || stepName == null) {
                        log.warn("Rejet non persisté : {}", reason); return;
                    }
                    try {
                        requiresNew.executeWithoutResult(st ->
                            jdbcTemplate.update(
                                "INSERT INTO %s_rejection " +
                                "(job_execution_id, step_name, %s, reason) " +
                                "VALUES (?, ?, ?, ?)",
                                jobExecutionId, stepName, key, reason));
                        log.info("Rejet persisté : step={} key={} raison={}",
                                stepName, key, reason);
                    } catch (Exception e) {
                        log.error("Impossible d'enregistrer le rejet [{}] : {}",
                                reason, e.getMessage(), e);
                    }
                }
            }
            """.formatted(c.basePackage, c.basePackage, c.model.getSimpleName(),
                          c.baseName, c.model.getSimpleName(), c.model.getSimpleName(),
                          c.baseName,
                          c.baseName,
                          c.model.getSimpleName(), capitalize(c.keyField),
                          c.model.getSimpleName(), capitalize(c.keyField),
                          c.tableName, camelToSnake(c.keyField));
    }

    private static String repositoryTemplate(GeneratorContext c) {
        return """
            package %s.repository;

            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.jdbc.support.GeneratedKeyHolder;
            import org.springframework.jdbc.support.KeyHolder;
            import org.springframework.stereotype.Repository;

            import java.sql.PreparedStatement;
            import java.sql.Statement;
            import java.util.List;

            @Repository
            public class %sRepository {

                private final JdbcTemplate jdbc;

                public %sRepository(JdbcTemplate jdbc) {
                    this.jdbc = jdbc;
                }

                /** Upsert : retourne l'id existant ou insère et retourne le nouvel id. */
                public long upsert(String keyValue) {
                    List<Long> ids = jdbc.queryForList(
                            "SELECT id FROM %s WHERE %s = ?",
                            Long.class, keyValue);
                    if (!ids.isEmpty()) return ids.get(0);

                    KeyHolder kh = new GeneratedKeyHolder();
                    jdbc.update(con -> {
                        PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO %s (%s) VALUES (?)",
                            Statement.RETURN_GENERATED_KEYS);
                        ps.setString(1, keyValue);
                        return ps;
                    }, kh);

                    Number key = kh.getKey();
                    if (key == null) {
                        return jdbc.queryForObject(
                            "SELECT id FROM %s WHERE %s = ?",
                            Long.class, keyValue);
                    }
                    return key.longValue();
                }
            }
            """.formatted(c.basePackage, c.baseName, c.baseName,
                          c.tableName, camelToSnake(c.keyField),
                          c.tableName, camelToSnake(c.keyField),
                          c.tableName, camelToSnake(c.keyField));
    }

    private static String configTemplate(GeneratorContext c) {
        boolean file = c.sourceType == SourceType.FILE;
        String var = Character.toLowerCase(c.baseName.charAt(0)) + c.baseName.substring(1);
        String readerBean = file
                ? """
                  @Bean @StepScope
                  public LineRange%sReader %sReader(
                          @Value("#{stepExecutionContext['startLine']}") Integer startLine,
                          @Value("#{stepExecutionContext['endLine']}") Integer endLine) {
                      int s = startLine != null ? startLine : 2;
                      int e = endLine != null ? endLine : Integer.MAX_VALUE;
                      return new LineRange%sReader(inputFile, s, e);
                  }
                  """.formatted(c.baseName, var, c.baseName)
                : """
                  @Bean @StepScope
                  public %sDbItemReader %sReader(
                          @Qualifier("personneSourceDataSource") DataSource sourceDs) {
                      return new %sDbItemReader(sourceDs, sourceSchema, sourceTable, pageSize);
                  }
                  """.formatted(c.baseName, var, c.baseName);

        String partitionerBean = file && c.partitioned
                ? """
                  @Bean
                  public Step %sMasterStep(JobRepository jobRepository,
                                           LineRangePartitioner partitioner,
                                           @Qualifier("%sWorkerStep") Step workerStep,
                                           @Qualifier("%sPartitionTaskExecutor") TaskExecutor executor) {
                      return new StepBuilder("%sMasterStep", jobRepository)
                              .partitioner("%sWorkerStep", partitioner)
                              .step(workerStep).gridSize(gridSize).taskExecutor(executor).build();
                  }
                  """.formatted(var, var, var, var, var)
                : "";

        String jobStart = file && c.partitioned
                ? "@Qualifier(\"" + var + "MasterStep\") Step masterStep"
                : "@Qualifier(\"" + var + "WorkerStep\") Step workerStep";

        String jobBean = file && c.partitioned
                ? ".start(masterStep)"
                : ".start(workerStep)";

        return """
            package %s.config;

            import java.util.HashMap;
            import java.util.Map;

            import javax.sql.DataSource;

            import org.springframework.batch.core.Job;
            import org.springframework.batch.core.Step;
            import org.springframework.batch.core.configuration.annotation.ScopeConfiguration;
            import org.springframework.batch.core.configuration.annotation.StepScope;
            import org.springframework.batch.core.job.builder.JobBuilder;
            import org.springframework.batch.core.launch.support.RunIdIncrementer;
            import org.springframework.batch.core.repository.JobRepository;
            import org.springframework.batch.core.step.builder.StepBuilder;
            import org.springframework.batch.core.step.skip.LimitCheckingItemSkipPolicy;
            import org.springframework.batch.core.step.skip.SkipPolicy;
            import org.springframework.batch.item.file.FlatFileParseException;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Import;
            import org.springframework.context.annotation.Scope;
            import org.springframework.context.annotation.ScopedProxyMode;
            import org.springframework.core.task.TaskExecutor;
            import org.springframework.dao.DuplicateKeyException;
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
            import org.springframework.transaction.PlatformTransactionManager;

            import %s.listener.%sSkipListener;
            import %s.model.%s;
            import %s.partitioner.LineRangePartitioner;
            import %s.processor.%sProcessor;
            %s
            import %s.writer.%sWriter;

            @Configuration
            @Import(ScopeConfiguration.class)
            public class %sBatchConfiguration {

                @Value("${batch.%s.input-file:}") private String inputFile;
                @Value("${batch.%s.source-schema:}") private String sourceSchema;
                @Value("${batch.%s.source-table:}") private String sourceTable;
                @Value("${batch.%s.page-size:1000}") private int pageSize;
                @Value("${batch.chunk-size:500}") private int chunkSize;
                @Value("${batch.grid-size:8}") private int gridSize;

                @Bean
                public TaskExecutor %sPartitionTaskExecutor() {
                    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
                    ex.setCorePoolSize(gridSize); ex.setMaxPoolSize(gridSize);
                    ex.setQueueCapacity(0); ex.setThreadNamePrefix("%s-part-");
                    ex.initialize(); return ex;
                }

                @Bean @StepScope @Scope(value = "step", proxyMode = ScopedProxyMode.TARGET_CLASS)
                public %sSkipListener %sSkipListener(
                        @Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate,
                        @Qualifier("transactionManager") PlatformTransactionManager txManager) {
                    return new %sSkipListener(jdbcTemplate, txManager);
                }

            %s

                @Bean
                public Step %sWorkerStep(
                        JobRepository jobRepository,
                        @Qualifier("transactionManager") PlatformTransactionManager txManager,
                        %sProcessor processor,
                        %sWriter writer,
                        %sSkipListener skipListener) {
                    Map<Class<? extends Throwable>, Boolean> skippable = new HashMap<>();
                    skippable.put(IllegalArgumentException.class, true);
                    skippable.put(FlatFileParseException.class, true);
                    skippable.put(DuplicateKeyException.class, true);
                    SkipPolicy skipPolicy = new LimitCheckingItemSkipPolicy(Integer.MAX_VALUE, skippable);

                    return new StepBuilder("%sWorkerStep", jobRepository)
                            .<%s, %s>chunk(chunkSize, txManager)
                            .reader(%sReader(null%s))
                            .processor(processor)
                            .writer(writer)
                            .faultTolerant()
                            .skipPolicy(skipPolicy)
                            .listener(skipListener)
                            .build();
                }

            %s

                @Bean
                public Job %sMigrationJob(JobRepository jobRepository,
                                          %s) {
                    return new JobBuilder("%sMigrationJob", jobRepository)
                            .incrementer(new RunIdIncrementer())
                            %s
                            .build();
                }
            }
            """.formatted(
                c.basePackage, c.basePackage, c.baseName,
                c.basePackage, c.model.getSimpleName(),
                c.basePackage, c.basePackage, c.baseName,
                file ? "import " + c.basePackage + ".reader.LineRange" + c.baseName + "Reader;"
                     : "import " + c.basePackage + ".reader." + c.baseName + "DbItemReader;",
                c.basePackage, c.baseName,
                c.baseName,
                var, var, var, var,
                var, var,
                c.baseName, var, c.baseName, var,
                readerBean,
                var, c.baseName, c.baseName, c.baseName,
                var, c.model.getSimpleName(), c.model.getSimpleName(),
                var, file ? ", null" : "",
                partitionerBean,
                var, jobStart, var, jobBean);
    }

    private static String sqlTemplate(GeneratorContext c) {
        StringBuilder cols = new StringBuilder();
        for (Field f : c.fields) {
            cols.append("    ").append(camelToSnake(f.getName())).append(" VARCHAR(255),\n");
        }
        return """
            CREATE TABLE IF NOT EXISTS %s (
            %s    PRIMARY KEY (%s)
            );

            CREATE TABLE IF NOT EXISTS %s_rejection (
                id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                job_execution_id  BIGINT       NOT NULL,
                step_name         VARCHAR(255) NOT NULL,
                %s                VARCHAR(64),
                reason            TEXT,
                created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.formatted(c.tableName, cols, camelToSnake(c.keyField),
                          c.tableName, camelToSnake(c.keyField));
    }

    // ---------- Utilitaires ----------

    static String camelToSnake(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}