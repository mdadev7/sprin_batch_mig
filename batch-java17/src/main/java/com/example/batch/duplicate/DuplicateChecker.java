package com.example.batch.duplicate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Store thread-safe partagé entre les 8 workers pour détecter les doublons
 * (même nom, prénom, date de naissance, ville) dans le fichier CSV.
 * <p>
 * La clé est une chaîne normalisée : "nom|prenom|dateNaissance|ville".
 * Une ligne dont la clé a déjà été vue est rejetée comme doublon.
 */
@Component
public class DuplicateChecker {

    private final ConcurrentMap<String, Boolean> seen =
            new ConcurrentHashMap<>();

    /**
     * @return true si cette combinaison a déjà été vue (donc doublon),
     *         false si c'est la première occurrence.
     */
    public boolean isDuplicate(String nom, String prenom,
                               String dateNaissance, String ville) {
        String key = buildKey(nom, prenom, dateNaissance, ville);
        return seen.putIfAbsent(key, Boolean.TRUE) != null;
    }

    private String buildKey(String nom, String prenom,
                            String dateNaissance, String ville) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(nom != null ? nom.trim().toLowerCase() : "");
        sb.append('|');
        sb.append(prenom != null ? prenom.trim().toLowerCase() : "");
        sb.append('|');
        sb.append(dateNaissance != null ? dateNaissance.trim() : "");
        sb.append('|');
        sb.append(ville != null ? ville.trim().toLowerCase() : "");
        return sb.toString();
    }

    /** Nombre de clés uniques enregistrées (pour monitoring). */
    public int size() {
        return seen.size();
    }

    /** Vide le store (pour tests ou réexécution). */
    public void clear() {
        seen.clear();
    }
}