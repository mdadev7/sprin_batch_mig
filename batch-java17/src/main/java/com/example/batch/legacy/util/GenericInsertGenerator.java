package com.example.batch.legacy.util; 

import jakarta.persistence.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
  
 

import jakarta.persistence.*;
import jakarta.persistence.metamodel.*;

import java.lang.reflect.Field;
import java.util.*;

public class GenericInsertGenerator {

    private final Metamodel metamodel;

    public GenericInsertGenerator(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    public static class InsertQuery {

        private final String sql;
        private final List<Object> parameters;

        public InsertQuery(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }

        public String getSql() {
            return sql;
        }

        public List<Object> getParameters() {
            return parameters;
        }
    }

    public InsertQuery generate(
            Class<?> entityClass,
            Object dto) throws Exception {

        EntityType<?> entityType =
                metamodel.entity(entityClass);

        Table table = entityClass.getAnnotation(Table.class);

        String tableName = table != null && !table.name().isEmpty()
                ? table.name()
                : entityClass.getSimpleName();

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Attribute<?, ?> attribute :
                entityType.getAttributes()) {

            if (attribute.isCollection()) {
                continue;
            }

            String attributeName = attribute.getName();

            Field field = findField(entityClass, attributeName);

            if (field == null ||
                    field.isAnnotationPresent(Transient.class)) {
                continue;
            }

            Column column = field.getAnnotation(Column.class);

            if (column != null && !column.insertable()) {
                continue;
            }

            String columnName =
                    column != null && !column.name().isEmpty()
                            ? column.name()
                            : attributeName;

            Field dtoField = findField(dto.getClass(), attributeName);

            if (dtoField == null) {
                throw new IllegalArgumentException(
                    "Attribut absent dans DTO : " + attributeName
                );
            }

            field.setAccessible(true);
            dtoField.setAccessible(true);

            Object value = dtoField.get(dto);

            columns.add(columnName);
            values.add(value);
        }

        String placeholders = String.join(
                ", ",
                Collections.nCopies(columns.size(), "?")
        );

        String sql = "INSERT INTO " + tableName
                + " (" + String.join(", ", columns) + ")"
                + " VALUES (" + placeholders + ")";

        return new InsertQuery(sql, values);
    }

    private Field findField(Class<?> clazz, String name) {

        while (clazz != null && clazz != Object.class) {

            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }

        return null;
    }
}