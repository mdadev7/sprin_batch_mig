package com.dev.writer;

import javax.sql.DataSource;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.dev.model.AttestationRow;

@Component
public class AttestationJdbcWriter implements ItemWriter<AttestationRow> {

    private static final String SQL = "INSERT INTO attestation (id, mois, annee, job_id, date_creation, code_position, nom_agent, num_somme, num_cnie, code_localite, adresse, code_sexe, code_pays, date_naissance, date_recrutement, date_rachat, date_limite, motif_radiation, code_imputation, residence, code_poste, mode_reglement, base_imposable, saisissable, brut_annuel, net_annuel, date_effet_situation, code_statut, code_grade, echelon, point_indice, code_zone, code_fonction, code_position_detach, code_departement, avance, pension, code_grade_pension, indice_pension, echelon_pension, code_situat_famil, nbre_enfant, deductions, code_mutuel_sc, taux_mutuel_sc, code_mutuelle_sm, taux_mutuelle_sm, date_dernier_mouvement, taux_prime_anci, num_compte, date_last_update) VALUES (:id, :mois, :annee, :jobId, :dateCreation, :codePosition, :nomAgent, :numSomme, :num_Cnie, :codeLocalite, :adresse, :codeSexe, :codePays, :dateNaissance, :dateRecrutement, :dateRachat, :dateLimite, :motifRadiation, :codeImputation, :residence, :codePoste, :modeReglement, :baseImposable, :saisissable, :brutAnnuel, :netAnnuel, :dateEffetSituation, :codeStatut, :codeGrade, :echelon, :pointIndice, :codeZone, :codeFonction, :codePositionDetach, :codeDepartement, :avance, :pension, :codeGradePension, :indicePension, :echelonPension, :codeSituatFamil, :nbreEnfant, :deductions, :codeMutuelSC, :tauxMutuelSC, :codeMutuelleSM, :tauxMutuelleSM, :dateDernierMouvement, :tauxPrimeAnci, :numCompte, :dateLastUpdate)";

    private final NamedParameterJdbcTemplate jdbc;

    public AttestationJdbcWriter(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void write(Chunk<? extends AttestationRow> chunk) {
        for (AttestationRow r : chunk.getItems()) {
            jdbc.update(SQL, new MapSqlParameterSource()
                .addValue("id", r.getId())
                .addValue("mois", r.getMois())
                .addValue("annee", r.getAnnee())
                .addValue("jobId", r.getJobId())
                .addValue("dateCreation", r.getDateCreation())
                .addValue("codePosition", r.getCodePosition())
                .addValue("nomAgent", r.getNomAgent())
                .addValue("numSomme", r.getNumSomme())
                .addValue("num_Cnie", r.getNum_Cnie())
                .addValue("codeLocalite", r.getCodeLocalite())
                .addValue("adresse", r.getAdresse())
                .addValue("codeSexe", r.getCodeSexe())
                .addValue("codePays", r.getCodePays())
                .addValue("dateNaissance", r.getDateNaissance())
                .addValue("dateRecrutement", r.getDateRecrutement())
                .addValue("dateRachat", r.getDateRachat())
                .addValue("dateLimite", r.getDateLimite())
                .addValue("motifRadiation", r.getMotifRadiation())
                .addValue("codeImputation", r.getCodeImputation())
                .addValue("residence", r.getResidence())
                .addValue("codePoste", r.getCodePoste())
                .addValue("modeReglement", r.getModeReglement())
                .addValue("baseImposable", r.getBaseImposable())
                .addValue("saisissable", r.getSaisissable())
                .addValue("brutAnnuel", r.getBrutAnnuel())
                .addValue("netAnnuel", r.getNetAnnuel())
                .addValue("dateEffetSituation", r.getDateEffetSituation())
                .addValue("codeStatut", r.getCodeStatut())
                .addValue("codeGrade", r.getCodeGrade())
                .addValue("echelon", r.getEchelon())
                .addValue("pointIndice", r.getPointIndice())
                .addValue("codeZone", r.getCodeZone())
                .addValue("codeFonction", r.getCodeFonction())
                .addValue("codePositionDetach", r.getCodePositionDetach())
                .addValue("codeDepartement", r.getCodeDepartement())
                .addValue("avance", r.getAvance())
                .addValue("pension", r.getPension())
                .addValue("codeGradePension", r.getCodeGradePension())
                .addValue("indicePension", r.getIndicePension())
                .addValue("echelonPension", r.getEchelonPension())
                .addValue("codeSituatFamil", r.getCodeSituatFamil())
                .addValue("nbreEnfant", r.getNbreEnfant())
                .addValue("deductions", r.getDeductions())
                .addValue("codeMutuelSC", r.getCodeMutuelSC())
                .addValue("tauxMutuelSC", r.getTauxMutuelSC())
                .addValue("codeMutuelleSM", r.getCodeMutuelleSM())
                .addValue("tauxMutuelleSM", r.getTauxMutuelleSM())
                .addValue("dateDernierMouvement", r.getDateDernierMouvement())
                .addValue("tauxPrimeAnci", r.getTauxPrimeAnci())
                .addValue("numCompte", r.getNumCompte())
                .addValue("dateLastUpdate", r.getDateLastUpdate())
                );
        }
    }
}
