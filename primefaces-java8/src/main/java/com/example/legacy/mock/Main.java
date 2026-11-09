package com.example.legacy.mock;

public class Main {

    public static void main(String[] args) throws Exception {

        PersonCsvGenerator.generate(
            1_000_000,
            "persons.csv"
        );
    }
}
