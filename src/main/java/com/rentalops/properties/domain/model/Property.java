package com.rentalops.properties.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a property managed inside a tenant workspace.
 *
 * <p>The business key is {@code propertyCode}, but uniqueness is tenant-scoped:
 * two different tenants may reuse the same code, while duplicates inside the same
 * tenant are rejected as business conflicts.
 */
@Entity
@Table(
        name = "properties",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_properties_tenant_code", columnNames = {"tenant_id", "property_code"})
        }
)
public class Property {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "property_code", nullable = false, length = 64)
    private String propertyCode;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Property() {
        // Required by JPA. Not for use in application code.
    }

    public Property(UUID id,
                    UUID tenantId,
                    String propertyCode,
                    String name,
                    String address,
                    String city,
                    String notes) {
        this.id = id;
        this.tenantId = tenantId;
        this.propertyCode = propertyCode;
        this.name = name;
        this.address = address;
        this.city = city;
        this.notes = notes;
        this.active = true;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPropertyCode() { return propertyCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setPropertyCode(String propertyCode) {
        this.propertyCode = propertyCode;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

