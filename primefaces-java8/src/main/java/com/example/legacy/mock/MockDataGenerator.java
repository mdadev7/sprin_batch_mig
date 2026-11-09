
package com.example.legacy.mock;

import com.example.legacy.entity.Person;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class MockDataGenerator {

    private static final Random RANDOM = new Random();

    private static final String[] NOMS = {
        "Dupont", "Martin", "Bernard", "Petit",
        "Robert", "Richard", "Durand", "Moreau",
        "Simon", "Laurent", "Michel", "Garcia",
        "Thomas", "David", "Bertrand", "Roux",
        "Leroy", "Fournier", "Girard", "Bonnet"
    };

    private static final String[] PRENOMS = {
        "Jean", "Marie", "Pierre", "Sophie",
        "Paul", "Julie", "Thomas", "Emma",
        "Lucas", "Sarah", "Nicolas", "Laura",
        "Antoine", "Camille", "Alexandre", "Lea",
        "Hugo", "Alice", "Louis", "Claire"
    };

    private static final String[] VILLES = {
        "Paris", "Lyon", "Marseille", "Toulouse",
        "Bordeaux", "Lille", "Nantes", "Nice",
        "Strasbourg", "Montpellier", "Rennes",
        "Grenoble", "Casablanca", "Rabat", "Marrakech"
    };

    private static final long MIN_DATE =
        new java.util.GregorianCalendar(
            1960, java.util.Calendar.JANUARY, 1
        ).getTimeInMillis();

    private static final long MAX_DATE =
        new java.util.GregorianCalendar(
            2005, java.util.Calendar.DECEMBER, 31
        ).getTimeInMillis();

    /**
     * Génère une Person avec des données aléatoires.
     */
    public static Person generatePerson(Long id) {

        Person person = new Person();

        person.setId(id);
        person.setNom(randomElement(NOMS));
        person.setPrenom(randomElement(PRENOMS));

        person.setSalaire(generateSalaire());

        person.setDateNaissance(generateDateNaissance());

        person.setVille(randomElement(VILLES));

        return person;
    }

    /**
     * Génération salaire entre 2000.00 et 15000.00
     */
    private static BigDecimal generateSalaire() {

        long salaire = ThreadLocalRandom.current()
                .nextLong(200000, 1500001);

        return BigDecimal.valueOf(salaire, 2);
    }

    /**
     * Génération date de naissance aléatoire.
     */
    private static Date generateDateNaissance() {

        long timestamp = ThreadLocalRandom.current()
                .nextLong(MIN_DATE, MAX_DATE + 1);

        return new Date(timestamp);
    }

    /**
     * Choix aléatoire dans un tableau.
     */
    private static String randomElement(String[] values) {

        return values[RANDOM.nextInt(values.length)];
    }
}
