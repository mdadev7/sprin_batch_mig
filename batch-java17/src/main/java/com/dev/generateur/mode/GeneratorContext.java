package com.dev.generateur.mode;

import java.lang.reflect.Field;
import java.nio.file.Path; 
import java.util.List;

public class GeneratorContext {

    public final Class<?> model;          // classe modèle (PersonneRow, ...)
    public final String basePackage;      // com.example.batch
    public final String baseName;         // Personne, Attestation...
    public final String tableName;        // personne, attestation...
    public final SourceType sourceType;
    public final Path outputDir;
    public final char csvDelimiter;
    public final boolean partitioned;
    public final List<Field> fields;      // champs d'identité (numSomme, nom...) pour le skip listener
    public final String keyField;         // champ clé (numSomme)
    public final String[] dateFields;     // champs à formater

    public GeneratorContext(Class<?> model, String basePackage, String baseName,
                            String tableName, SourceType sourceType, Path outputDir,
                            char csvDelimiter, boolean partitioned,
                            List<Field> fields, String keyField, String[] dateFields) {
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
    }
}