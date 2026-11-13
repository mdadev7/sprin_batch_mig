package com.dev.generateur.mode;

public enum SourceType {
    FILE,       // source = fichier CSV (reader partitionné)
    DATABASE    // source = base externe (JdbcPagingItemReader)
}