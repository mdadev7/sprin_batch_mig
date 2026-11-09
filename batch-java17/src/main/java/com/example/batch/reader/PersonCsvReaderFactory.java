package com.example.batch.reader;

import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.example.batch.model.PersonCsv;

public final class PersonCsvReaderFactory {

    private static final ResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();

    private PersonCsvReaderFactory() {
    }

    public static FlatFileItemReader<PersonCsv> create(String location) {
        Resource resource = RESOURCE_LOADER.getResource(location);

        BeanWrapperFieldSetMapper<PersonCsv> mapper = new BeanWrapperFieldSetMapper<>();
        mapper.setTargetType(PersonCsv.class);

        return new FlatFileItemReaderBuilder<PersonCsv>()
                .name("personCsvReader")
                .resource(resource)
                .encoding("UTF-8")
                .linesToSkip(1)
                .delimited()
                .delimiter(";")
                .names("id", "nom", "prenom", "salaire", "dateNaissance", "ville")
                .fieldSetMapper(mapper)
                .build();
    }
}