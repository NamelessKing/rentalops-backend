package com.rentalops.properties.infrastructure.persistence;

import com.rentalops.properties.domain.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for tenant-scoped property access.
 *
 * <p>Every query includes tenant filtering because property endpoints must never
 * leak data across workspaces.
 */
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    List<Property> findAllByTenantId(UUID tenantId);

    Optional<Property> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPropertyCode(UUID tenantId, String propertyCode);

    /**
     * Tenant-scoped batch lookup for a set of property IDs.
     * Used to batch-load property names without crossing tenant boundaries.
     */
    List<Property> findAllByTenantIdAndIdIn(UUID tenantId, java.util.Collection<UUID> ids);

    /**
     * Count all properties in a tenant.
     * Used by the dashboard summary query.
     */
    long countByTenantId(UUID tenantId);
}

