package com.ucms.identity.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AssignmentPermissionOverrideId implements Serializable {
    private UUID assignment;
    private UUID permission;

    public AssignmentPermissionOverrideId() {}
    public AssignmentPermissionOverrideId(UUID assignment, UUID permission) {
        this.assignment = assignment;
        this.permission = permission;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssignmentPermissionOverrideId that)) return false;
        return Objects.equals(assignment, that.assignment) && Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() { return Objects.hash(assignment, permission); }
}
