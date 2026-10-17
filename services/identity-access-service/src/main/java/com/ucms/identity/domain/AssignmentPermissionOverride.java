package com.ucms.identity.domain;

import jakarta.persistence.*;

/**
 * Per-assignment permission override. If a row exists for (assignment, permission), it
 * wins over the role's baseline role_permissions grant for that permission, for that one
 * assignment only. See docs/SYSTEM_ARCHITECTURE_REPORT.md section 5.2/5.3.
 */
@Entity
@Table(name = "assignment_permission_overrides")
@IdClass(AssignmentPermissionOverrideId.class)
public class AssignmentPermissionOverride extends AuditableNoSoftDelete {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private RoleScopeAssignment assignment;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(nullable = false)
    private boolean granted;

    public RoleScopeAssignment getAssignment() { return assignment; }
    public void setAssignment(RoleScopeAssignment assignment) { this.assignment = assignment; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
