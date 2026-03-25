package com.rentalops.iam.infrastructure.persistence;

import com.rentalops.iam.domain.model.TaskCategory;
import com.rentalops.iam.domain.model.Tenant;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence tests for IAM entities and repositories against PostgreSQL.
 *
 * <p>These checks protect the real database mapping used by the auth flow,
 * especially explicit column names and enum-as-string storage that can silently
 * drift during refactors while still compiling.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class IamPersistenceIntegrationTest {

    @SuppressWarnings("unused") // Read by Testcontainers extension via reflection.
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void userRepository_shouldPersistAndLoadUser_withExpectedColumnMappings() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(tenantId, "Mario Rentals"));

        userRepository.saveAndFlush(new User(
                userId,
                tenant.getId(),
                "Mario Rossi",
                "mario@example.com",
                "hashed-password",
                UserRole.ADMIN,
                UserStatus.ACTIVE
        ));

        Optional<User> loaded = userRepository.findByEmail("mario@example.com");
        assertThat(loaded).isPresent();
        assertThat(userRepository.existsByEmail("mario@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();

        User persistedUser = loaded.orElseThrow();
        assertThat(persistedUser.getId()).isEqualTo(userId);
        assertThat(persistedUser.getTenantId()).isEqualTo(tenantId);
        assertThat(persistedUser.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(persistedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(persistedUser.getCreatedAt()).isNotNull();
        assertThat(persistedUser.getUpdatedAt()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select tenant_id, password_hash, role, status, specialization_category from users where id = ?",
                userId
        );

        assertThat(row.get("tenant_id")).isEqualTo(tenantId);
        assertThat(row.get("password_hash")).isEqualTo("hashed-password");
        assertThat(row.get("role")).isEqualTo("ADMIN");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("specialization_category")).isNull();
    }

    @Test
    void tenantRepository_shouldPersistTenant_withDefaultActiveAndTimestamp() {
        UUID tenantId = UUID.randomUUID();

        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(tenantId, "Ocean Rentals"));

        assertThat(tenantRepository.findById(tenantId)).isPresent();
        assertThat(tenant.isActive()).isTrue();
        assertThat(tenant.getCreatedAt()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select name, active, created_at from tenants where id = ?",
                tenantId
        );

        assertThat(row.get("name")).isEqualTo("Ocean Rentals");
        assertThat(row.get("active")).isEqualTo(Boolean.TRUE);
        assertThat(row.get("created_at")).isInstanceOf(Timestamp.class);
    }

    @Test
    void repositoryShouldMapSpecializationCategory_fromStoredStringValue() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        tenantRepository.saveAndFlush(new Tenant(tenantId, "Update Rentals"));

        jdbcTemplate.update(
                """
                insert into users (
                    id,
                    tenant_id,
                    full_name,
                    email,
                    password_hash,
                    role,
                    status,
                    specialization_category,
                    created_at,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                userId,
                tenantId,
                "Operator Updated",
                "operator@example.com",
                "hashed-password",
                UserRole.OPERATOR.name(),
                UserStatus.ACTIVE.name(),
                TaskCategory.CLEANING.name()
        );

        User reloaded = userRepository.findByEmail("operator@example.com").orElseThrow();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select full_name, specialization_category, updated_at from users where id = ?",
                userId
        );

        assertThat(row.get("full_name")).isEqualTo("Operator Updated");
        assertThat(row.get("specialization_category")).isEqualTo("CLEANING");
        assertThat(row.get("updated_at")).isInstanceOf(Timestamp.class);
        assertThat(reloaded.getSpecializationCategory()).isEqualTo(TaskCategory.CLEANING);
    }
}


