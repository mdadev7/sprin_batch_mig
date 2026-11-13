package com.example.batch.model;

import java.sql.Date;

public class PersonRow {

    private Long id;
    private String nom;
    private String prenom;
    private String salaireEncrypted;
    private Date dateNaissance;
    private String ville;
    /** Numéro de la ligne d'origine dans le CSV (traçabilité des rejets). */
    private Long lineNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getSalaireEncrypted() {
        return salaireEncrypted;
    }

    public void setSalaireEncrypted(String value) {
        this.salaireEncrypted = value;
    }

    public Date getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(Date dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public Long getLineNumber() { return lineNumber; }
    public void setLineNumber(Long lineNumber) { this.lineNumber = lineNumber; }
}