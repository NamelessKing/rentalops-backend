package com.rentalops.iam.infrastructure.persistence;

import com.rentalops.iam.domain.model.Tenant;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for IAM persistence methods introduced in Slice 2.
 *
 * <p>These tests verify tenant isolation at repository level for operator queries.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class OperatorPersistenceIntegrationTest {

    @SuppressWarnings("unused")
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void findAllByTenantIdAndRole_shouldReturnOnlyOperatorsOfRequestedTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        tenantRepository.saveAndFlush(new Tenant(tenantA, "Tenant A"));
        tenantRepository.saveAndFlush(new Tenant(tenantB, "Tenant B"));

        User operatorA = userRepository.saveAndFlush(new User(
                UUID.randomUUID(),
                tenantA,
                "Operator A",
                "operator.a@example.com",
                "hashed-pass",
                UserRole.OPERATOR,
                UserStatus.ACTIVE
        ));

        userRepository.saveAndFlush(new User(
                UUID.randomUUID(),
                tenantB,
                "Operator B",
                "operator.b@example.com",
                "hashed-pass",
                UserRole.OPERATOR,
                UserStatus.ACTIVE
        ));

        // Admin in tenant A should not appear in operator query (role filter).
        userRepository.saveAndFlush(new User(
                UUID.randomUUID(),
                tenantA,
                "Admin A",
                "admin.a@example.com",
                "hashed-pass",
                UserRole.ADMIN,
                UserStatus.ACTIVE
        ));

        List<User> tenantAOperators = userRepository.findAllByTenantIdAndRole(tenantA, UserRole.OPERATOR);

        assertThat(tenantAOperators).hasSize(1);
        assertThat(tenantAOperators.getFirst().getId()).isEqualTo(operatorA.getId());
        assertThat(tenantAOperators.getFirst().getTenantId()).isEqualTo(tenantA);
        assertThat(tenantAOperators.getFirst().getRole()).isEqualTo(UserRole.OPERATOR);
    }

    @Test
    void findByIdAndTenantId_shouldRespectTenantOwnership() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        tenantRepository.saveAndFlush(new Tenant(tenantA, "Tenant A"));
        tenantRepository.saveAndFlush(new Tenant(tenantB, "Tenant B"));

        User operatorA = userRepository.saveAndFlush(new User(
                UUID.randomUUID(),
                tenantA,
                "Operator A",
                "operator.a@example.com",
                "hashed-pass",
                UserRole.OPERATOR,
                UserStatus.ACTIVE
        ));

        Optional<User> foundInOwnTenant = userRepository.findByIdAndTenantId(operatorA.getId(), tenantA);
        Optional<User> foundInOtherTenant = userRepository.findByIdAndTenantId(operatorA.getId(), tenantB);

        assertThat(foundInOwnTenant).isPresent();
        assertThat(foundInOtherTenant).isEmpty();
    }
}

