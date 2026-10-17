package com.ucms.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * A user holding a role at a specific point in the org/academic hierarchy.
 * scopeId is a plain uuid ("ref Reference Data or Academic Structure, no FK") — the
 * target lives in a different schema/service. NULL scopeId = GLOBAL.
 * See docs/SYSTEM_ARCHITECTURE_REPORT.md section 5.2.
 */
@Entity
@Table(name = "role_scope_assignments")
public class RoleScopeAssignment extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "scope_id")
    private UUID scopeId;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public ScopeType getScopeType() { return scopeType; }
    public void setScopeType(ScopeType scopeType) { this.scopeType = scopeType; }
    public UUID getScopeId() { return scopeId; }
    public void setScopeId(UUID scopeId) { this.scopeId = scopeId; }
}
