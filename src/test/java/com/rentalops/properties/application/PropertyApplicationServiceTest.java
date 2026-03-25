package com.rentalops.properties.application;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.properties.api.dto.CreatePropertyRequest;
import com.rentalops.properties.api.dto.PropertyDetailResponse;
import com.rentalops.properties.api.dto.UpdatePropertyRequest;
import com.rentalops.properties.domain.model.Property;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for property business rules in service layer.
 */
@ExtendWith(MockitoExtension.class)
class PropertyApplicationServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PropertyApplicationService propertyApplicationService;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
    }

    @Test
    void createProperty_shouldPersistNormalizedCode_whenUniqueInTenant() {
        CreatePropertyRequest request = new CreatePropertyRequest(
                " apt-001 ",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                "Check-in autonomo"
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(currentUserProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(propertyRepository.existsByTenantIdAndPropertyCode(tenantA, "APT-001")).thenReturn(false);
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyDetailResponse response = propertyApplicationService.createProperty(request);

        assertThat(response.propertyCode()).isEqualTo("APT-001");
        assertThat(response.name()).isEqualTo("Milano Centrale Loft");

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantA);
        assertThat(captor.getValue().getPropertyCode()).isEqualTo("APT-001");
    }

    @Test
    void createProperty_shouldThrowConflict_whenCodeAlreadyExistsInSameTenant() {
        CreatePropertyRequest request = new CreatePropertyRequest(
                "APT-001",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                null
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(currentUserProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(propertyRepository.existsByTenantIdAndPropertyCode(tenantA, "APT-001")).thenReturn(true);

        assertThatThrownBy(() -> propertyApplicationService.createProperty(request))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Property code already exists in this tenant.");
    }

    @Test
    void getPropertyDetail_shouldThrowNotFound_whenPropertyBelongsToDifferentTenant() {
        UUID propertyId = UUID.randomUUID();

        when(currentUserProvider.getCurrentTenantId()).thenReturn(tenantB);
        when(propertyRepository.findByIdAndTenantId(propertyId, tenantB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyApplicationService.getPropertyDetail(propertyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Property not found in this tenant.");
    }

    @Test
    void createProperty_shouldThrowForbidden_whenCurrentUserIsNotAdmin() {
        CreatePropertyRequest request = new CreatePropertyRequest(
                "APT-001",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                null
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.OPERATOR);

        assertThatThrownBy(() -> propertyApplicationService.createProperty(request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only admins can manage properties.");
    }

    @Test
    void updateProperty_shouldPersistNormalizedCode_whenPropertyBelongsToTenantAndCodeChanges() {
        UUID propertyId = UUID.randomUUID();
        Property existing = new Property(
                propertyId,
                tenantA,
                "APT-001",
                "Old name",
                "Old address",
                "Milano",
                null
        );

        UpdatePropertyRequest request = new UpdatePropertyRequest(
                " apt-002 ",
                "New name",
                "New address",
                "Roma",
                "Updated notes"
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(currentUserProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(propertyRepository.findByIdAndTenantId(propertyId, tenantA)).thenReturn(Optional.of(existing));
        when(propertyRepository.existsByTenantIdAndPropertyCode(tenantA, "APT-002")).thenReturn(false);
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyDetailResponse response = propertyApplicationService.updateProperty(propertyId, request);

        assertThat(response.propertyCode()).isEqualTo("APT-002");
        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.city()).isEqualTo("Roma");
    }
}

