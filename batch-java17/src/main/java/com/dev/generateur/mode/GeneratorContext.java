package com.dev.generateur.mode;

import java.lang.reflect.Field;
import java.nio.file.Path; 
import java.util.List;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

public class GeneratorContext {

    public final Class<?> model;
    public final String basePackage;
    public final String baseName;
    public final String tableName;
    public final SourceType sourceType;
    public final Path outputDir;
    public final char csvDelimiter;
    public final boolean partitioned;
    public final List<Field> fields;
    public final String keyField;
    public final String[] dateFields;
    public final String primaryDataSourceBean;
    public final String sourceDataSourceBean;
    public final String transactionManagerBean;
    public final String jdbcTemplateBean;

    public GeneratorContext(Class<?> model, String basePackage, String baseName,
                            String tableName, SourceType sourceType, Path outputDir,
                            char csvDelimiter, boolean partitioned,
                            List<Field> fields, String keyField, String[] dateFields,
                            String primaryDataSourceBean, String sourceDataSourceBean,
                            String transactionManagerBean, String jdbcTemplateBean) {
        this.model = model;
        this.basePackage = basePackage;
        this.baseName = baseName;
        this.tableName = tableName;
        this.sourceType = sourceType;
        this.outputDir = outputDir;
        this.csvDelimiter = csvDelimiter;
        this.partitioned = partitioned;
        this.fields = fields;
        this.keyField = keyField;
        this.dateFields = dateFields;
        this.primaryDataSourceBean = primaryDataSourceBean;
        this.sourceDataSourceBean = sourceDataSourceBean;
        this.transactionManagerBean = transactionManagerBean;
        this.jdbcTemplateBean = jdbcTemplateBean;
    }

    public boolean isFile() {
        return sourceType == SourceType.FILE || sourceType == SourceType.FILE_WITH_UPSERT;
    }

    public boolean isDatabase() {
        return sourceType == SourceType.DATABASE;
    }

    public boolean isUpsert() {
        return sourceType == SourceType.FILE_WITH_UPSERT;
    }
}