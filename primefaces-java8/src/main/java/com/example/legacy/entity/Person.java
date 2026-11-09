package com.example.legacy.entity;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.*;

@Entity
@Table(name = "person")
public class Person {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "prenom", nullable = false)
    private String prenom;

    @Column(name = "salaire_encrypted")
    private BigDecimal salaire;

    @Temporal(TemporalType.DATE)
    @Column(name = "date_naissance")
    private Date dateNaissance;

    @Column(name = "ville")
    private String ville;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public BigDecimal getSalaire() { return salaire; }
    public void setSalaire(BigDecimal salaire) {
        this.salaire = salaire;
    }

    public Date getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(Date dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
}