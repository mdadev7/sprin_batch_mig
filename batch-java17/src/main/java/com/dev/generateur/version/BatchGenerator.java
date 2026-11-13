package com.dev.generateur.version;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.dev.generateur.modele.AttestationRow;

/**
 * Génère les fichiers d'un batch de migration (reader, processor, writer,
 * skip listener, configuration, SQL) à partir d'une classe modèle.
 *
 * Usage :
 *   java BatchGenerator com.example.batch.model.PersonneRow
 *
 * Prérequis :
 *   - la classe modèle doit être sur le classpath (compilée)
 *   - elle doit avoir un constructeur sans argument + getters/setters (Lombok OK)
 */
public class BatchGenerator {

    public static void main(String[] args) throws Exception {
       /* if (args.length < 1) {
            System.err.println("Usage: BatchGenerator <fully.qualified.ModelClass>");
            System.exit(1);
        }
        String fqcn = args[0];*/
        Class<?> model = Class.forName(AttestationRow.class.getName());
        String simpleName = model.getSimpleName();               // PersonneRow
        String baseName = simpleName.replaceAll("Row$", "");     // Personne
        String pkg = model.getPackageName();
        String basePkg = pkg.substring(0, pkg.lastIndexOf('.')); // com.example.batch

        Path out = Path.of("generated");
        Files.createDirectories(out);

        // 1. Champs (nom java + type)
        List<Field> fields = new ArrayList<>();
        for (Field f : model.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            fields.add(f);
        }

        // 2. Reader
        write(out.resolve("LineRange" + baseName + "Reader.java"),
              readerTemplate(basePkg, baseName, simpleName, fields));

        // 3. Processor
        write(out.resolve(baseName + "RowProcessor.java"),
              processorTemplate(basePkg, baseName, simpleName));

        // 4. Writer
        write(out.resolve(baseName + "JdbcWriter.java"),
              writerTemplate(basePkg, baseName, simpleName, fields));

        // 5. Skip listener
        write(out.resolve(baseName + "SkipListener.java"),
              skipListenerTemplate(basePkg, baseName, simpleName));

        // 6. Configuration
        write(out.resolve(baseName + "BatchConfiguration.java"),
              configTemplate(basePkg, baseName, simpleName));

        // 7. SQL
        write(out.resolve(baseName.toLowerCase() + "_schema.sql"),
              sqlTemplate(baseName, fields));

        System.out.println("Fichiers générés dans " + out.toAbsolutePath());
    }

    private static void write(Path p, String content) throws IOException {
        Files.writeString(p, content);
        System.out.println("  → " + p);
    }

    // ---------- Templates ----------

    private static String readerTemplate(String basePkg, String baseName,
                                         String model, List<Field> fields) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            names.append('"').append(fields.get(i).getName()).append('"');
            if (i < fields.size() - 1) names.append(", ");
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

                    DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(";");
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
            """.formatted(basePkg, basePkg, model, baseName, model,
                          baseName, names.toString(), model, model, model);
    }

    private static String processorTemplate(String basePkg, String baseName, String model) {
        return """
            package %s.processor;

            import org.springframework.batch.item.ItemProcessor;
            import org.springframework.stereotype.Component;

            import %s.model.%s;

            @Component
            public class %sRowProcessor implements ItemProcessor<%s, %s> {
                @Override
                public %s process(%s item) {
                    // TODO : validations métier — lever IllegalArgumentException pour skip
                    return item;
                }
            }
            """.formatted(basePkg, basePkg, model, baseName, model, model, model, model);
    }

    private static String writerTemplate(String basePkg, String baseName,
                                         String model, List<Field> fields) {
        String table = baseName.toLowerCase();
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        StringBuilder setters = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            String name = fields.get(i).getName();
            String col = camelToSnake(name);
            cols.append(col);
            vals.append(':').append(name);
            setters.append("                .addValue(\"%s\", r.%s())\n"
                    .formatted(name, getterName(name)));
            if (i < fields.size() - 1) { cols.append(", "); vals.append(", "); }
        }
        return """
            package %s.writer;

            import javax.sql.DataSource;

            import org.springframework.batch.item.Chunk;
            import org.springframework.batch.item.ItemWriter;
            import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
            import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
            import org.springframework.stereotype.Component;

            import %s.model.%s;

            @Component
            public class %sJdbcWriter implements ItemWriter<%s> {

                private static final String SQL = "INSERT INTO %s (%s) VALUES (%s)";

                private final NamedParameterJdbcTemplate jdbc;

                public %sJdbcWriter(DataSource dataSource) {
                    this.jdbc = new NamedParameterJdbcTemplate(dataSource);
                }

                @Override
                public void write(Chunk<? extends %s> chunk) {
                    for (%s r : chunk.getItems()) {
                        jdbc.update(SQL, new MapSqlParameterSource()
            %s                );
                    }
                }
            }
            """.formatted(basePkg, basePkg, model, baseName, model, table, cols, vals,
                          baseName, model, model, setters.toString());
    }

    private static String skipListenerTemplate(String basePkg, String baseName, String model) {
        String table = baseName.toLowerCase() + "_rejection";
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
                    this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                }

                @Override public void beforeStep(StepExecution s) {
                    this.jobExecutionId = s.getJobExecutionId();
                    this.stepName = s.getStepName();
                }
                @Override public ExitStatus afterStep(StepExecution s) { return null; }

                @Override public void onSkipInRead(Throwable t) {
                    String raw = (t instanceof org.springframework.batch.item.file.FlatFileParseException f)
                            ? f.getInput() : null;
                    insert(raw, t.getMessage());
                }
                @Override public void onSkipInProcess(%s item, Throwable t) {
                    insert(item != null ? item.toString() : null, t.getMessage());
                }
                @Override public void onSkipInWrite(%s item, Throwable t) {
                    insert(item != null ? item.toString() : null, t.getMessage());
                }

                private void insert(String rawContent, String reason) {
                    if (jobExecutionId == null || stepName == null) {
                        log.warn("Rejet non persisté : {}", reason); return;
                    }
                    try {
                        requiresNew.executeWithoutResult(st ->
                            jdbcTemplate.update(
                                "INSERT INTO %s (job_execution_id, step_name, raw_content, reason) " +
                                "VALUES (?, ?, ?, ?)",
                                jobExecutionId, stepName, rawContent, reason));
                        log.info("Rejet persisté : step={} raison={}", stepName, reason);
                    } catch (Exception e) {
                        log.error("Impossible d'enregistrer le rejet [{}] : {}", reason, e.getMessage(), e);
                    }
                }
            }
            """.formatted(basePkg, basePkg, model, baseName, model, model, baseName,
                          baseName, model, model, table);
    }

    private static String configTemplate(String basePkg, String baseName, String model) {
        String var = Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        return """
            package %s.config;

            import java.util.HashMap;
            import java.util.Map;

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
            import %s.processor.%sRowProcessor;
            import %s.reader.LineRange%sReader;
            import %s.writer.%sJdbcWriter;

            @Configuration
            @Import(ScopeConfiguration.class)
            public class %sBatchConfiguration {

                @Value("${batch.input-file}") private String inputFile;
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
                public %sSkipListener %sSkipListener(JdbcTemplate jdbcTemplate,
                                                              PlatformTransactionManager txManager) {
                    return new %sSkipListener(jdbcTemplate, txManager);
                }

                @Bean @StepScope
                public LineRange%sReader %sReader(
                        @Value("#{stepExecutionContext['startLine']}") Integer startLine,
                        @Value("#{stepExecutionContext['endLine']}") Integer endLine) {
                    int s = startLine != null ? startLine : 2;
                    int e = endLine != null ? endLine : Integer.MAX_VALUE;
                    return new LineRange%sReader(inputFile, s, e);
                }

                @Bean
                public Step %sWorkerStep(JobRepository jobRepository,
                                         PlatformTransactionManager txManager,
                                         %sRowProcessor processor,
                                         %sJdbcWriter writer,
                                         %sSkipListener skipListener) {
                    Map<Class<? extends Throwable>, Boolean> skippable = new HashMap<>();
                    skippable.put(IllegalArgumentException.class, true);
                    skippable.put(FlatFileParseException.class, true);
                    skippable.put(DuplicateKeyException.class, true);
                    SkipPolicy skipPolicy = new LimitCheckingItemSkipPolicy(Integer.MAX_VALUE, skippable);

                    return new StepBuilder("%sWorkerStep", jobRepository)
                            .<%s, %s>chunk(chunkSize, txManager)
                            .reader(%sReader(null, null))
                            .processor(processor)
                            .writer(writer)
                            .faultTolerant()
                            .skipPolicy(skipPolicy)
                            .listener(skipListener)
                            .build();
                }

                @Bean
                public Step %sMasterStep(JobRepository jobRepository,
                                         LineRangePartitioner partitioner,
                                         @Qualifier("%sWorkerStep") Step workerStep,
                                         @Qualifier("%sPartitionTaskExecutor") TaskExecutor executor) {
                    return new StepBuilder("%sMasterStep", jobRepository)
                            .partitioner("%sWorkerStep", partitioner)
                            .step(workerStep).gridSize(gridSize).taskExecutor(executor).build();
                }

                @Bean
                public Job %sMigrationJob(JobRepository jobRepository,
                                          @Qualifier("%sMasterStep") Step masterStep) {
                    return new JobBuilder("%sMigrationJob", jobRepository)
                            .incrementer(new RunIdIncrementer()).start(masterStep).build();
                }
            }
            """.formatted(
                basePkg, basePkg, baseName, basePkg, model, basePkg, basePkg, baseName,
                basePkg, baseName, basePkg, baseName, baseName,
                var, var,
                baseName, var, baseName,
                baseName, var, baseName,
                var, baseName, baseName, baseName, baseName,
                baseName, model, model, var,
                var, var, var,
                var, var,
                var, var, var);
    }

    private static String sqlTemplate(String baseName, List<Field> fields) {
        String table = baseName.toLowerCase();
        StringBuilder cols = new StringBuilder();
        for (Field f : fields) {
            cols.append("    ").append(camelToSnake(f.getName())).append(" VARCHAR(255),\n");
        }
        return """
            CREATE TABLE IF NOT EXISTS %s (
            %s    PRIMARY KEY (id)
            );

            CREATE TABLE IF NOT EXISTS %s_rejection (
                id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                job_execution_id  BIGINT       NOT NULL,
                step_name         VARCHAR(255) NOT NULL,
                raw_content       TEXT,
                reason            TEXT,
                created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.formatted(table, cols, table);
    }

    // ---------- Utilitaires ----------

    private static String camelToSnake(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String getterName(String field) {
        return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }
}