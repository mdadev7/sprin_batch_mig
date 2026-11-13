package com.dev.reader;

import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.FileSystemResource;

import com.dev.model.AttestationRow;

public class LineRangeAttestationReader extends FlatFileItemReader<AttestationRow> {
    public LineRangeAttestationReader(String inputFile, int startLine, int endLine) {
        setResource(new FileSystemResource(inputFile));
        setLinesToSkip(startLine - 1);
        setMaxItemCount(endLine - startLine + 1);
        setStrict(false);

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(";");
        tokenizer.setNames("id", "mois", "annee", "jobId", "dateCreation", "codePosition", "nomAgent", "numSomme", "num_Cnie", "codeLocalite", "adresse", "codeSexe", "codePays", "dateNaissance", "dateRecrutement", "dateRachat", "dateLimite", "motifRadiation", "codeImputation", "residence", "codePoste", "modeReglement", "baseImposable", "saisissable", "brutAnnuel", "netAnnuel", "dateEffetSituation", "codeStatut", "codeGrade", "echelon", "pointIndice", "codeZone", "codeFonction", "codePositionDetach", "codeDepartement", "avance", "pension", "codeGradePension", "indicePension", "echelonPension", "codeSituatFamil", "nbreEnfant", "deductions", "codeMutuelSC", "tauxMutuelSC", "codeMutuelleSM", "tauxMutuelleSM", "dateDernierMouvement", "tauxPrimeAnci", "numCompte", "dateLastUpdate");
        tokenizer.setStrict(false);

        BeanWrapperFieldSetMapper<AttestationRow> mapper = new BeanWrapperFieldSetMapper<>();
        mapper.setTargetType(AttestationRow.class);
        mapper.setDistanceLimit(0);

        DefaultLineMapper<AttestationRow> lm = new DefaultLineMapper<>();
        lm.setLineTokenizer(tokenizer);
        lm.setFieldSetMapper(mapper);
        setLineMapper(lm);
    }
}
