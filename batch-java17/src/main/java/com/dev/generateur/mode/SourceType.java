package com.dev.generateur.mode;

public enum SourceType {
    FILE,               // CSV → INSERT simple
    DATABASE,           // base externe → INSERT simple
    FILE_WITH_UPSERT    // CSV → upsert (personne)
}