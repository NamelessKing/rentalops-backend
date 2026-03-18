package com.rentalops.properties.application;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.properties.api.dto.CreatePropertyRequest;
import com.rentalops.properties.api.dto.DeactivatePropertyResponse;
import com.rentalops.properties.api.dto.PropertyDetailResponse;
import com.rentalops.properties.api.dto.PropertyListItemResponse;
import com.rentalops.properties.api.dto.UpdatePropertyRequest;
import com.rentalops.properties.domain.model.Property;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles property use cases for Slice 2 foundation.
 *
 * <p>The service keeps tenant isolation in one place by resolving tenantId from
 * authenticated context and applying it to every repository operation.
 */
@Service
public class PropertyApplicationService {

    private final PropertyRepository propertyRepository;
    private final CurrentUserProvider currentUserProvider;

    public PropertyApplicationService(PropertyRepository propertyRepository,
                                      CurrentUserProvider currentUserProvider) {
        this.propertyRepository = propertyRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Returns all properties of the current tenant (active and inactive).
     */
    @Transactional(readOnly = true)
    public List<PropertyListItemResponse> listProperties() {
        UUID tenantId = currentUserProvider.getCurrentTenantId();

        return propertyRepository.findAllByTenantId(tenantId)
                .stream()
                .map(property -> new PropertyListItemResponse(
                        property.getId(),
                        property.getPropertyCode(),
                        property.getName(),
                        property.getCity(),
                        property.isActive()
                ))
                .toList();
    }

    /**
     * Creates a property in the authenticated admin's tenant.
     *
     * <p>propertyCode is normalized to uppercase before uniqueness check and save
     * so codes differing only by letter case are treated as duplicates.
     */
    @Transactional
    public PropertyDetailResponse createProperty(CreatePropertyRequest request) {
        assertCurrentUserIsAdmin();
        UUID tenantId = currentUserProvider.getCurrentTenantId();

        String normalizedCode = normalizePropertyCode(request.propertyCode());

        if (propertyRepository.existsByTenantIdAndPropertyCode(tenantId, normalizedCode)) {
            throw new BusinessConflictException("Property code already exists in this tenant.");
        }

        Property property = propertyRepository.save(new Property(
                UUID.randomUUID(),
                tenantId,
                normalizedCode,
                request.name().trim(),
                request.address().trim(),
                request.city().trim(),
                normalizeNotes(request.notes())
        ));

        return toDetailResponse(property);
    }

    /**
     * Returns property detail only if it belongs to the current tenant.
     */
    @Transactional(readOnly = true)
    public PropertyDetailResponse getPropertyDetail(UUID propertyId) {
        UUID tenantId = currentUserProvider.getCurrentTenantId();

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found in this tenant."));

        return toDetailResponse(property);
    }

    /**
     * Updates a property owned by the current tenant.
     *
     * <p>The propertyCode is normalized and can remain unchanged. If changed,
     * it must still be unique in the same tenant.
     */
    @Transactional
    public PropertyDetailResponse updateProperty(UUID propertyId, UpdatePropertyRequest request) {
        assertCurrentUserIsAdmin();
        UUID tenantId = currentUserProvider.getCurrentTenantId();

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found in this tenant."));

        String normalizedCode = normalizePropertyCode(request.propertyCode());

        // Only check uniqueness against other records when the code changes.
        if (!normalizedCode.equals(property.getPropertyCode())
                && propertyRepository.existsByTenantIdAndPropertyCode(tenantId, normalizedCode)) {
            throw new BusinessConflictException("Property code already exists in this tenant.");
        }

        property.setPropertyCode(normalizedCode);
        property.setName(request.name().trim());
        property.setAddress(request.address().trim());
        property.setCity(request.city().trim());
        property.setNotes(normalizeNotes(request.notes()));

        property = propertyRepository.save(property);

        return toDetailResponse(property);
    }

    /**
     * Deactivates a property in the current tenant.
     *
     * <p>This operation is idempotent: already inactive properties still return
     * success with {@code active=false}.
     */
    @Transactional
    public DeactivatePropertyResponse deactivateProperty(UUID propertyId) {
        assertCurrentUserIsAdmin();
        UUID tenantId = currentUserProvider.getCurrentTenantId();

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found in this tenant."));

        property.setActive(false);
        property = propertyRepository.save(property);

        return new DeactivatePropertyResponse(property.getId(), property.isActive());
    }

    private void assertCurrentUserIsAdmin() {
        if (currentUserProvider.getCurrentUserRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Only admins can manage properties.");
        }
    }

    private String normalizePropertyCode(String propertyCode) {
        String normalized = propertyCode == null ? "" : propertyCode.trim().toUpperCase();
        if (normalized.isBlank()) {
            throw new DomainValidationException("Property code is required.");
        }
        return normalized;
    }

    private String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PropertyDetailResponse toDetailResponse(Property property) {
        return new PropertyDetailResponse(
                property.getId(),
                property.getPropertyCode(),
                property.getName(),
                property.getAddress(),
                property.getCity(),
                property.getNotes(),
                property.isActive()
        );
    }
}

