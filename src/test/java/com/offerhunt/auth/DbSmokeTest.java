package com.offerhunt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.offerhunt.auth.support.PostgresTCBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DbSmokeTest extends PostgresTCBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsApplied_andSchemaIsValid() {
        assertThat(pg().isRunning()).isTrue();

        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);

        Integer tables = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'auth'
            """, Integer.class);
        assertThat(tables).isNotNull();
        assertThat(tables).isGreaterThan(0);

        Integer exists = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'auth' AND table_name = 'auth_users'
            """, Integer.class);
        assertThat(exists).isEqualTo(1);
    }
}
