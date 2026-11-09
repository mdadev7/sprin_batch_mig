package com.example.batch.reader; 

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import com.example.batch.model.PersonCsv;

public class LineRangeFlatFileReader implements ItemStreamReader<PersonCsv> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String DELIMITER = ";";

    private final String location;
    private final int startLine;
    private final int endLine;

    private BufferedReader reader;
    private int currentLine;

    public LineRangeFlatFileReader(String location, int startLine, int endLine) {
        this.location = location;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    @Override
    public void open(ExecutionContext ctx) throws ItemStreamException {
        try {
            Resource resource = new DefaultResourceLoader().getResource(location);
            InputStream in = resource.getInputStream();
            reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            // Ignorer l'en-tête + les lignes avant startLine
            for (int i = 0; i < startLine; i++) {
                if (reader.readLine() == null) break;
            }
            currentLine = startLine;
        } catch (Exception e) {
            throw new ItemStreamException("Ouverture impossible : " + location, e);
        }
    }

    @Override
    public PersonCsv read() throws Exception {
        if (currentLine >= endLine) return null;

        String line;
        while (true) {
            line = reader.readLine();
            if (line == null) return null;
            currentLine++;
            if (!line.isBlank()) break;
            if (currentLine >= endLine) return null;
        }
        return parse(line);
    }

    private PersonCsv parse(String line) {
        String[] f = line.split(DELIMITER, -1);
        if (f.length < 6) {
            throw new IllegalArgumentException(
                    "Ligne invalide (colonnes=" + f.length + ") : " + line);
        }
        PersonCsv p = new PersonCsv();
        p.setId(Long.parseLong(f[0].trim()));
        p.setNom(f[1].trim());
        p.setPrenom(f[2].trim());
        p.setSalaire(f[3].trim()); //new BigDecimal(f[3].trim()));
        p.setDateNaissance( f[4].trim() ) ; //LocalDate.parse(f[4].trim(), DATE_FMT));
        p.setVille(f[5].trim());
        return p;
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (reader != null) reader.close();
        } catch (Exception e) {
            throw new ItemStreamException(e);
        }
    }

    @Override
    public void update(ExecutionContext e) { /* no-op */ }
}
