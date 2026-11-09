package com.example.batch.writer;

import com.example.batch.model.PersonRow;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PersonJdbcWriter implements ItemWriter<PersonRow> {

    private final JdbcTemplate jdbcTemplate;

    private static final String SQL =
            "INSERT INTO person " +
            "(id, nom, prenom, salaire_encrypted, " +
            "date_naissance, ville) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    public PersonJdbcWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends PersonRow> chunk) {

        jdbcTemplate.batchUpdate(
                SQL,
                chunk.getItems(),
                chunk.size(),
                (ps, person) -> {

                    ps.setLong(1, person.getId());
                    ps.setString(2, person.getNom());
                    ps.setString(3, person.getPrenom());
                    ps.setString(4, person.getSalaireEncrypted());
                    ps.setDate(5, person.getDateNaissance());
                    ps.setString(6, person.getVille());
                }
        );
    }
}
