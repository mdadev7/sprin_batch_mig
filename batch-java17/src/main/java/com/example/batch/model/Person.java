package com.example.batch.model;  

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "person")

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Person {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "prenom", nullable = false)
    private String prenom;

    @Column(name = "salaire_encrypted")
    private String salaire;

    @Temporal(TemporalType.DATE)
    @Column(name = "date_naissance")
    private Date dateNaissance;

    @Column(name = "ville")
    private String ville;

    // Getters et setters
}
