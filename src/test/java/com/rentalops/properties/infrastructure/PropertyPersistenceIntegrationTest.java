package com.rentalops.properties.infrastructure;

import com.rentalops.properties.domain.model.Property;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for property persistence and tenant boundaries.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class PropertyPersistenceIntegrationTest {

    @SuppressWarnings("unused")
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private PropertyRepository propertyRepository;

    @BeforeEach
    void setUp() {
        propertyRepository.deleteAll();
    }

    @Test
    void shouldSaveAndLoadProperty_byTenantAwareQueries() {
        UUID tenantId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        propertyRepository.saveAndFlush(new Property(
                propertyId,
                tenantId,
                "APT-001",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                "Check-in autonomo"
        ));

        List<Property> byTenant = propertyRepository.findAllByTenantId(tenantId);
        Optional<Property> byIdAndTenant = propertyRepository.findByIdAndTenantId(propertyId, tenantId);

        assertThat(byTenant).hasSize(1);
        assertThat(byIdAndTenant).isPresent();
        assertThat(byIdAndTenant.orElseThrow().getPropertyCode()).isEqualTo("APT-001");
    }

    @Test
    void shouldEnforceUniqueConstraint_onTenantAndPropertyCode() {
        UUID tenantId = UUID.randomUUID();

        propertyRepository.saveAndFlush(new Property(
                UUID.randomUUID(),
                tenantId,
                "APT-001",
                "Property A",
                "Address A",
                "Milano",
                null
        ));

        assertThatThrownBy(() -> propertyRepository.saveAndFlush(new Property(
                UUID.randomUUID(),
                tenantId,
                "APT-001",
                "Property B",
                "Address B",
                "Milano",
                null
        )))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldKeepTenantIsolation_whenSameCodeUsedInDifferentTenants() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID propertyAId = UUID.randomUUID();

        propertyRepository.saveAndFlush(new Property(
                propertyAId,
                tenantA,
                "APT-001",
                "Tenant A Property",
                "Address A",
                "Milano",
                null
        ));

        propertyRepository.saveAndFlush(new Property(
                UUID.randomUUID(),
                tenantB,
                "APT-001",
                "Tenant B Property",
                "Address B",
                "Roma",
                null
        ));

        List<Property> tenantAProperties = propertyRepository.findAllByTenantId(tenantA);
        Optional<Property> visibleFromTenantB = propertyRepository.findByIdAndTenantId(propertyAId, tenantB);

        assertThat(tenantAProperties).hasSize(1);
        assertThat(tenantAProperties.getFirst().getName()).isEqualTo("Tenant A Property");
        assertThat(visibleFromTenantB).isEmpty();
    }
}

