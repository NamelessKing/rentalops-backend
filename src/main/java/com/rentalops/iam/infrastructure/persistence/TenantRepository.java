package com.rentalops.iam.infrastructure.persistence;

import com.rentalops.iam.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persistence adapter for Tenant entities.
 *
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
