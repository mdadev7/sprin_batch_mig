package com.example.legacy.mock; 

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;

import com.example.legacy.entity.Person;

public class PersonCsvGenerator {

    public static void generate(int nombre, String filename)
            throws IOException {

        SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd/MM/yyyy");

        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(filename),
                StandardCharsets.UTF_8)) {

            // Header CSV
            writer.write(
                "id;nom;prenom;salaire;date_naissance;ville"
            );
            writer.newLine();

            for (int i = 1; i <= nombre; i++) {

                Person person =
                        MockDataGenerator.generatePerson((long) i);

                writer.write(
                    person.getId() + ";" +
                    person.getNom() + ";" +
                    person.getPrenom() + ";" +
                    person.getSalaire().toPlainString() + ";" +
                    dateFormat.format(person.getDateNaissance()) + ";" +
                    person.getVille()
                );

                writer.newLine();

                // Affichage progression chaque 100 000 lignes
                if (i % 100000 == 0) {
                    System.out.println(
                        "Progression : " + i + " / " + nombre
                    );
                }
            }
        }

        System.out.println("Fichier CSV généré : " + filename);
    }
}

