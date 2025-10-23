package com.offerhunt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DbSmokeTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("authdb")
        .withUsername("auth_user")
        .withPassword("auth_pass");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.flyway.schemas", () -> "auth");
        r.add("spring.flyway.default-schema", () -> "auth");
    }

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsApplied_andSchemaIsValid() {
        assertThat(pg.isRunning()).isTrue();

        Integer one = jdbc.queryForObject("select 1", Integer.class);
        assertThat(one).isEqualTo(1);

        Integer tables = jdbc.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'auth'
            """, Integer.class);
        assertThat(tables).isNotNull();
        assertThat(tables).isGreaterThan(0);

        Integer exists = jdbc.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'auth' and table_name = 'auth_users'
            """, Integer.class);
        assertThat(exists).isEqualTo(1);
    }
}