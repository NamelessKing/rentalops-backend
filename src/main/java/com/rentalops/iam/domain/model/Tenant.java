package com.rentalops.iam.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a tenant workspace in RentalOps.
 *
 * <p>Every registered admin creates exactly one tenant. All domain entities
 * (users, properties, tasks, issue reports) are scoped to a tenant, which is
 * why this entity is the root of the multi-tenancy model.
 *
 * <p>The tenant is never passed by the client in HTTP requests. It is always
 * derived from the authenticated context after login.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    // Logical soft-disable for the tenant.
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Tenant() {
        // Required by JPA. Not for use in application code.
    }

    public Tenant(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.active = true;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
