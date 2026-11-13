package com.dev.generateur.modele;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeParseException;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString; 
	
@Setter
@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
@AllArgsConstructor

public class AttestationRow {
	private String id;
	private String mois;
	private String annee;
	private String jobId; 
	private String dateCreation;  
	private String codePosition;
///////////// Attributes for Frwk Entities

	/**
	 * Nom et prénom
	 */

	private String nomAgent;
	/**
	 * N° Somme
	 */


	private String numSomme;

	/**
	 * N° CNIE
	 */

	private String num_Cnie;
	/**
	 * Localité
	 */

	private String codeLocalite;
	/**
	 * Adresse
	 */
	private String adresse;
	/**
	 * Sexe
	 */

	private String codeSexe;
	/**
	 * Code Pays
	 */

	private String codePays;
	/**
	 * Date Naissance
	 */

	private String dateNaissance;
	/**
	 * Date Recrutement
	 */

	private String dateRecrutement;
	/**
	 * Date rachat
	 */

	private String dateRachat;
	/**
	 * Date limite
	 */

	private String dateLimite;
	/**
	 * Motif radiation
	 */

	private String motifRadiation;
	/**
	 * Imputation standard
	 */

	private String codeImputation;
	/**
	 * Résidence
	 */

	private String residence;
	/**
	 * Poste (GIPE)
	 */

	private String codePoste;
	/**
	 * Mode règlement
	 */

	private String modeReglement;
	/**
	 * Base imposable
	 */
	private String baseImposable;
	/**
	 * Saisissable
	 */
	private String saisissable;
	/**
	 * Brut annuel
	 */
	private String brutAnnuel;
	/**
	 * Net annuel
	 */
	private String netAnnuel;
	/**
	 * Date effet situation
	 */
	private String dateEffetSituation;
	/**
	 * Statut de l'agent
	 */
	private String codeStatut;
	/**
	 * Grade de l'agent
	 */
	private String codeGrade;
	/**
	 * Echelon
	 */
	private String echelon;
	/**
	 * Point d'indice
	 */
	private String pointIndice;
	/**
	 * Zone
	 */
	private String codeZone;
	/**
	 * Fonction de l'agent
	 */
	private String codeFonction;
	/**
	 * Position en cas de détachement
	 */
	private String codePositionDetach;
	/**
	 * Code département
	 */
	private String codeDepartement;
	/**
	 * Avance
	 */
	private String avance;
	/**
	 * Pension
	 */
	private String pension;
	/**
	 * Grade de pension
	 */
	private String codeGradePension;
	private String indicePension;
	/**
	 * Echelon de pension
	 */
	private String echelonPension;
	/**
	 * Indice de pension
	 */
	/**
	 * Situation familiale
	 */
	private String codeSituatFamil;
	/**
	 * Nombre d'enfants
	 */
	private String nbreEnfant;
	/**
	 * Déductions
	 */
	private String deductions;
	/**
	 * Code Mutuelle SC
	 */
	private String codeMutuelSC;
	/**
	 * Taux Mutuelle SC
	 */
	private String tauxMutuelSC;
	/**
	 * Code Mutuelle SM
	 */
	private String codeMutuelleSM;
	/**
	 * Taux Mutuelle SM
	 */
	private String tauxMutuelleSM;
	/**
	 * Date du dernier mouvement
	 */

	private String dateDernierMouvement;
	/**
	 * Prime d'ancienneté
	 */

	private String tauxPrimeAnci;
	/**
	 * numCompte
	 */

	private String numCompte;

	/**
	 * Token trace
	 */
	private String dateLastUpdate; 
  
 

	public String toString() {
		return "" + numSomme + " - " + nomAgent;
	}

	public String getId() {
		return id;
	}

	public void setId(String id_) {
		this.id = id_;
	}

	public String getNomAgent() {
		return this.nomAgent;
	}

	public void setNomAgent(String nomAgent) {
		this.nomAgent = nomAgent;
	}

	public String getNumSomme() {
		return this.numSomme;
	}

	public void setNumSomme(String numSomme) {
		this.numSomme = numSomme;
	}

	public String getNum_Cnie() {
		return this.num_Cnie;
	}

	public void setNum_Cnie(String num_Cnie) {
		this.num_Cnie = num_Cnie;
	}

	public String getCodeLocalite() {
		return this.codeLocalite;
	}

	public void setCodeLocalite(String codeLocalite) {
		this.codeLocalite = codeLocalite;
	}

	public String getAdresse() {
		return this.adresse;
	}

	public void setAdresse(String adresse) {
		this.adresse = adresse;
	}

	public String getCodeSexe() {
		return this.codeSexe;
	}

	public void setCodeSexe(String codeSexe) {
		this.codeSexe = codeSexe;
	}

	public String getCodePays() {
		return this.codePays;
	}

	public void setCodePays(String codePays) {
		this.codePays = codePays;
	}

	public String getDateNaissance() {
		return formatedStringDate(this.dateNaissance);
	}

	public String getDateNaissanceFortmated() {

		return formatedStringDate(this.dateNaissance);

	}

	public void setDateNaissance(String dateNaissance) {
		this.dateNaissance = dateNaissance;
	}

	public String getDateRecrutement() {
		return formatedStringDate(this.dateRecrutement);
	}

	public void setDateRecrutement(String dateRecrutement) {
		this.dateRecrutement = dateRecrutement;
	}

	public String getDateRachat() {
		return formatedStringDate(this.dateRachat);
	}

	public void setDateRachat(String dateRachat) {
		this.dateRachat = dateRachat;
	}

	public String getDateLimite() {
		return formatedStringDate(this.dateLimite);
	}

	public void setDateLimite(String dateLimite) {
		this.dateLimite = dateLimite;
	}

	public String getMotifRadiation() {
		return this.motifRadiation;
	}

	public void setMotifRadiation(String motifRadiation) {
		this.motifRadiation = motifRadiation;
	}

	public String getCodeImputation() {
		return this.codeImputation;
	}

	public void setCodeImputation(String codeImputation) {
		this.codeImputation = codeImputation;
	}

	public String getResidence() {
		return this.residence;
	}

	public void setResidence(String residence) {
		this.residence = residence;
	}

	public String getCodePoste() {
		return this.codePoste;
	}

	public void setCodePoste(String codePoste) {
		this.codePoste = codePoste;
	}

	public String getModeReglement() {
		return this.modeReglement;
	}

	public void setModeReglement(String modeReglement) {
		this.modeReglement = modeReglement;
	}

	public String getBaseImposable() {
		return this.baseImposable;
	}

	public void setBaseImposable(String baseImposable) {
		this.baseImposable = baseImposable;
	}

	public String getSaisissable() {
		return this.saisissable;
	}

	public void setSaisissable(String saisissable) {
		this.saisissable = saisissable;
	}

	public String getBrutAnnuel() {
		return this.brutAnnuel;
	}

	public void setBrutAnnuel(String brutAnnuel) {
		this.brutAnnuel = brutAnnuel;
	}

	public String getNetAnnuel() {
		return this.netAnnuel;
	}

	public void setNetAnnuel(String netAnnuel) {
		this.netAnnuel = netAnnuel;
	}

	public String getDateEffetSituation() {
		return this.dateEffetSituation;
	}

	public void setDateEffetSituation(String dateEffetSituation) {
		this.dateEffetSituation = dateEffetSituation;
	}

	public String getCodeStatut() {
		return this.codeStatut;
	}

	public void setCodeStatut(String codeStatut) {
		this.codeStatut = codeStatut;
	}

	public String getCodeGrade() {
		return this.codeGrade;
	}

	public void setCodeGrade(String codeGrade) {
		this.codeGrade = codeGrade;
	}

	public String getEchelon() {
		return this.echelon;
	}

	public void setEchelon(String echelon) {
		this.echelon = echelon;
	}

	public String getPointIndice() {
		return this.pointIndice;
	}

	public void setPointIndice(String pointIndice) {
		this.pointIndice = pointIndice;
	}

	public String getCodeZone() {
		return this.codeZone;
	}

	public void setCodeZone(String codeZone) {
		this.codeZone = codeZone;
	}

	public String getCodeFonction() {
		return this.codeFonction;
	}

	public void setCodeFonction(String codeFonction) {
		this.codeFonction = codeFonction;
	}

	public String getCodePositionDetach() {
		return this.codePositionDetach;
	}

	public void setCodePositionDetach(String codePositionDetach) {
		this.codePositionDetach = codePositionDetach;
	}

	public String getCodeDepartement() {
		return this.codeDepartement;
	}

	public void setCodeDepartement(String codeDepartement) {
		this.codeDepartement = codeDepartement;
	}

	public String getAvance() {
		return this.avance;
	}

	public void setAvance(String avance) {
		this.avance = avance;
	}

	public String getPension() {
		return this.pension;
	}

	public void setPension(String pension) {
		this.pension = pension;
	}

	public String getCodeGradePension() {
		return this.codeGradePension;
	}

	public void setCodeGradePension(String codeGradePension) {
		this.codeGradePension = codeGradePension;
	}
 

	public String getIndicePension() {
		return this.indicePension;
	}

	public void setIndicePension(String indicePension) {
		this.indicePension = indicePension;
	}

	public String getCodeSituatFamil() {
		return this.codeSituatFamil;
	}

	public void setCodeSituatFamil(String codeSituatFamil) {
		this.codeSituatFamil = codeSituatFamil;
	}

	public String getNbreEnfant() {
		return this.nbreEnfant;
	}

	public void setNbreEnfant(String nbreEnfant) {
		this.nbreEnfant = nbreEnfant;
	}

	public String getDeductions() {
		return this.deductions;
	}

	public void setDeductions(String deductions) {
		this.deductions = deductions;
	}

	public String getCodeMutuelSC() {
		return this.codeMutuelSC;
	}

	public void setCodeMutuelSC(String codeMutuelSC) {
		this.codeMutuelSC = codeMutuelSC;
	}

	public String getTauxMutuelSC() {
		return this.tauxMutuelSC;
	}

	public void setTauxMutuelSC(String tauxMutuelSC) {
		this.tauxMutuelSC = tauxMutuelSC;
	}

	public String getCodeMutuelleSM() {
		return this.codeMutuelleSM;
	}

	public void setCodeMutuelleSM(String codeMutuelleSM) {
		this.codeMutuelleSM = codeMutuelleSM;
	}

	public String getTauxMutuelleSM() {
		return this.tauxMutuelleSM;
	}

	public void setTauxMutuelleSM(String tauxMutuelleSM) {
		this.tauxMutuelleSM = tauxMutuelleSM;
	}

	public String getDateDernierMouvement() {
		return this.dateDernierMouvement;
	}

	public String getDateDernierMouvementFormated() {
		return formatedStringDate(this.dateDernierMouvement);
	}

	public void setDateDernierMouvement(String dateDernierMouvement) {
		this.dateDernierMouvement = dateDernierMouvement;
	}

	public String getTauxPrimeAnci() {
		return this.tauxPrimeAnci;
	}

	public void setTauxPrimeAnci(String tauxPrimeAnci) {
		this.tauxPrimeAnci = tauxPrimeAnci;
	}

	public String getNumCompte() {
		return this.numCompte;
	}

	public void setNumCompte(String numCompte) {
		this.numCompte = numCompte;
	}

	public String getMois() {
		return mois;
	}

	public void setMois(String mois) {
		this.mois = mois;
	}

	public String getAnnee() {
		return annee;
	}

	public void setAnnee(String annee) {
		this.annee = annee;
	}
 
	public String getCodePosition() {
		return codePosition;
	}

	public void setCodePosition(String codePosition) {
		this.codePosition = codePosition;
	}

 
	public String getEchelonPension() {
		return echelonPension;
	}

	public void setEchelonPension(String echelonPension) {
		this.echelonPension = echelonPension;
	}

	
	public String getDateLastUpdate() {
		return dateLastUpdate;
	}

	public void setDateLastUpdate(String dateLastUpdate) {
		this.dateLastUpdate = dateLastUpdate;
	}

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getDateCreation() {
		return dateCreation;
	}

	public void setDateCreation(String dateCreation) {
		this.dateCreation = dateCreation;
	}

	public static String  formatedStringDate(String lDate) {
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		try {
			return formatter.format("dd/MM/yyyy");
		} catch (DateTimeParseException e) {
		    System.out.println("Erreur lors du parsing de la date : " +lDate);
			e.printStackTrace();
		}
		return lDate;
		
	}
	

}