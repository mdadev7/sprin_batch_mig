package com.example.batch; 
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.batch.legacy.util.GenericInsertGenerator;
import com.example.batch.legacy.util.GenericInsertGenerator.InsertQuery;
import com.example.batch.model.Person;
import com.example.batch.model.PersonCsv;
import com.example.batch.processor.PersonRowProcessor;

import jakarta.persistence.EntityManagerFactory;

//@SpringBootApplication
public class BatchMigrationApplication implements CommandLineRunner {

    private final EntityManagerFactory entityManagerFactory;

    public BatchMigrationApplication(
            EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public static void main(String[] args) {
        SpringApplication.run(BatchMigrationApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

    	PersonCsv row = new PersonCsv();

        row.setId(1L);
        row.setNom("Dupont");
        row.setPrenom("Jean");
        row.setSalaire("3200.50"); //new BigDecimal("3200.50"));

        row.setDateNaissance("15/03/1985");
       /*     new SimpleDateFormat("dd/MM/yyyy")
                .parse("15/03/1985")
        );*/
        row.setVille("Paris");

        GenericInsertGenerator generator =
            new GenericInsertGenerator(
                entityManagerFactory.getMetamodel()
            );

        InsertQuery query =
            generator.generate(Person.class, row);

        System.out.println("\n========== SQL INSERT ==========");
        System.out.println(query.getSql());

        System.out.println("\n========== PARAMETERS ==========");

        for (int i = 0; i < query.getParameters().size(); i++) {
            System.out.println(
                (i + 1) + " = " + query.getParameters().get(i)
            );
        }
    }
}