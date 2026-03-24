package com.rentalops.iam.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a user in the RentalOps system.
 *
 * <p>A single User entity covers both Admin and Operator roles, distinguished
 * by the {@code role} field. This is a deliberate MVP simplification — there
 * is no separate Invitation or Profile entity.
 *
 * <p>The {@code tenantId} is stored as a plain UUID reference rather than a
 * JPA @ManyToOne association. This keeps tenant isolation explicit and avoids
 * accidental cross-tenant data access through lazy-loaded joins.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // The Java field is named 'password' but the column is 'password_hash'
    // because the entity stores an already-hashed value. Hashing itself
    // belongs in the service layer via PasswordEncoder.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    // A new user is always active at creation. Disabling happens explicitly later.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    // Nullable: only meaningful for operators. Admins will have this as null.
    // Used by the pool visibility filter to match operators to task categories.
    @Enumerated(EnumType.STRING)
    @Column(name = "specialization_category")
    private TaskCategory specializationCategory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // Required by JPA. Not for use in application code.
    }

    public User(UUID id,
                UUID tenantId,
                String fullName,
                String email,
                String password,
                UserRole role,
                UserStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
        this.status = status;
    }

    // Small expressive helpers so service code reads clearly:
    // "if (user.isAdmin())" is cleaner than repeating enum comparisons.
    public boolean isAdmin() { return role == UserRole.ADMIN; }
    public boolean isOperator() { return role == UserRole.OPERATOR; }
    public boolean isActive() { return status == UserStatus.ACTIVE; }
    public boolean isDisabled() { return status == UserStatus.DISABLED; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public TaskCategory getSpecializationCategory() { return specializationCategory; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters for mutable fields. Status and specialization were already mutable;
    // fullName, email and password are added here to support the operator update use case.
    // tenantId and role are deliberately excluded — they must never change after creation.
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Password must always be stored pre-hashed. The caller (service layer) is
    // responsible for encoding before invoking this setter.
    public void setPassword(String passwordHash) {
        this.password = passwordHash;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setSpecializationCategory(TaskCategory specializationCategory) {
        this.specializationCategory = specializationCategory;
    }
}
