package com.example.batch.model;

public class PersonCsv {

    private Long id;
    private String nom;
    private String prenom;
    private String salaire;
    private String dateNaissance;
    private String ville;
    private Long lineNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getSalaire() { return salaire; }
    public void setSalaire(String salaire) { this.salaire = salaire; }

    public String getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(String dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public Long getLineNumber() { return lineNumber; }
    public void setLineNumber(Long lineNumber) { this.lineNumber = lineNumber; }
}