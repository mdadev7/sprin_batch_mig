package com.example.batch.legacy.util;

import com.example.batch.legacy.util.GenericInsertGenerator.InsertQuery;
import com.example.batch.model.Person;
import com.example.batch.model.PersonCsv;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class Main {

    public static void main(String[] args) throws Exception {

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

        EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("myPU");

        try {
            GenericInsertGenerator generator =
                new GenericInsertGenerator(emf.getMetamodel());

            InsertQuery query =
                generator.generate(Person.class, row);

            System.out.println("SQL INSERT:");
            System.out.println(query.getSql());

            System.out.println("\nPARAMETERS:");

            for (int i = 0; i < query.getParameters().size(); i++) {
                System.out.println(
                    (i + 1) + " = " + query.getParameters().get(i)
                );
            }

        } finally {
            emf.close();
        }
    }
}
