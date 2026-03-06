package com.test.financialtracker.identity.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
public class FlywayConfig {

    /**
     * Replaces Spring Boot's auto-configured Flyway bean.
     * Explicitly sets schema to 'app' so the history table and all
     * migrations land in the correct schema regardless of datasource defaults.
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("app")
                .defaultSchema("app")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .table("flyway_schema_history")
                .initSql("SET search_path TO app, public")
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load();
    }
}