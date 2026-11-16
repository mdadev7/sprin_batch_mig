package com.dev.generateur.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttestationRow {
    private String id;
    private String mois;
    private String annee;
    private String jobId;
    private String dateCreation;
    private String codePosition;
    private String nomAgent;
    private String numSomme;
    private String num_Cnie;
    private String codeLocalite;
    private String adresse;
    private String codeSexe;
    private String codePays;
    private String dateNaissance;
    private String dateRecrutement;
    private String dateRachat;
    private String dateLimite;
    private String motifRadiation;
    private String codeImputation;
    private String residence;
    private String codePoste;
    private String modeReglement;
    private String baseImposable;
    private String saisissable;
    private String brutAnnuel;
    private String netAnnuel;
    private String dateEffetSituation;
    private String codeStatut;
    private String codeGrade;
    private String echelon;
    private String pointIndice;
    private String codeZone;
    private String codeFonction;
    private String codePositionDetach;
    private String codeDepartement;
    private String avance;
    private String pension;
    private String codeGradePension;
    private String indicePension;
    private String echelonPension;
    private String codeSituatFamil;
    private String nbreEnfant;
    private String deductions;
    private String codeMutuelSC;
    private String tauxMutuelSC;
    private String codeMutuelleSM;
    private String tauxMutuelleSM;
    private String dateDernierMouvement;
    private String tauxPrimeAnci;
    private String numCompte;
    private String dateLastUpdate;

    private transient Long resolvedPersonneId;
}
