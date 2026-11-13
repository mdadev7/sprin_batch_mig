package com.example.batch.processor;

import com.example.batch.duplicate.DuplicateChecker;
import com.example.batch.model.PersonCsv;
import com.example.batch.model.PersonRow;
import com.example.crypto.CryptoService;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

@Component
public class PersonRowProcessor
        implements ItemProcessor<PersonCsv, PersonRow> {

    private final CryptoService cryptoService;
    private final DuplicateChecker duplicateChecker;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);

    public PersonRowProcessor(CryptoService cryptoService,
                              DuplicateChecker duplicateChecker) {
        this.cryptoService = cryptoService;
        this.duplicateChecker = duplicateChecker;
    }

    @Override
    public PersonRow process(PersonCsv csv) {

        try {

            if (csv.getId() == null || csv.getId() <= 0) {
                throw new IllegalArgumentException("ID invalide");
            }

            String nom = csv.getNom();
            String prenom = csv.getPrenom();

            if (nom == null || nom.trim().isEmpty()
                    || nom.length() > 100) {
                throw new IllegalArgumentException("Nom invalide");
            }

            if (prenom == null || prenom.trim().isEmpty()
                    || prenom.length() > 100) {
                throw new IllegalArgumentException("Prénom invalide");
            }

            BigDecimal salaire = new BigDecimal(csv.getSalaire());

            if (salaire.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Salaire invalide");
            }

            LocalDate date = LocalDate.parse(
                    csv.getDateNaissance(), FORMATTER);

            if (csv.getVille() != null
                    && csv.getVille().length() > 100) {
                throw new IllegalArgumentException(
                        "Ville trop longue");
            }

            // --- Détection de doublon (RG3 étendu) ---
            // Une ligne est un doublon si la combinaison
            // (nom, prénom, date_naissance, ville) a déjà été vue.
            if (duplicateChecker.isDuplicate(
                    nom, prenom, csv.getDateNaissance(),
                    csv.getVille())) {
                throw new IllegalArgumentException(
                        "Doublon (nom/prénom/date/ville) : "
                        + nom + " " + prenom + " "
                        + csv.getDateNaissance() + " "
                        + csv.getVille());
            }

            PersonRow row = new PersonRow();

            row.setId(csv.getId());
            row.setNom(nom.trim());
            row.setPrenom(prenom.trim());

            // Conversion décimale sans perte de précision
            String salaryValue = salaire.toPlainString();

            // Chiffrement obligatoire avant insertion
            String encrypted = cryptoService.encrypt(salaryValue);

            if (encrypted == null ||
                    !encrypted.startsWith("v1:")) {
                throw new IllegalArgumentException(
                        "Échec de vérification du chiffrement");
            }

            row.setSalaireEncrypted(encrypted);
            row.setDateNaissance(Date.valueOf(date));
            row.setVille(csv.getVille());
            row.setLineNumber(csv.getLineNumber());

            return row;

        } catch (Exception e) {

            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }

            throw new IllegalArgumentException(
                    "Enregistrement invalide", e);
        }
    }
}