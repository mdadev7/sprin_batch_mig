package com.dev.generateur.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DataSourceConfig {

    // =========================================================
    // 1. DataSource PRIMAIRE — base métier (JPA + batch cible)
    // =========================================================
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    // =========================================================
    // 2. DataSource EXTERNE — source référentiel (base RH)
    // =========================================================
    @Bean
    @ConfigurationProperties("spring.batch-personne-datasource")
    public DataSource personneSourceDataSource() {
        return DataSourceBuilder.create().build();
    }

    // =========================================================
    // 3. JdbcTemplate PRIMAIRE — base métier
    // =========================================================
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(
            @Qualifier("primaryDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // =========================================================
    // 4. JdbcTemplate SOURCE EXTERNE — base RH (lecture seule)
    // =========================================================
    @Bean
    public JdbcTemplate personneSourceJdbcTemplate(
            @Qualifier("personneSourceDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // =========================================================
    // 5. NamedParameterJdbcTemplate PRIMAIRE — pour les writers
    // =========================================================
    @Bean
    @Primary
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            @Qualifier("primaryDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    // =========================================================
    // 6. TransactionManager PRIMAIRE — base métier
    //    ⚠️ Si vous avez déjà un JpaTransactionManager @Primary,
    //    NE PAS redéclarer ce bean (voir note ci-dessous).
    // =========================================================
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("primaryDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}