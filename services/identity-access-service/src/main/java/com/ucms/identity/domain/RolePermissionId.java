package com.ucms.identity.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RolePermissionId implements Serializable {
    private UUID role;
    private UUID permission;

    public RolePermissionId() {}
    public RolePermissionId(UUID role, UUID permission) {
        this.role = role;
        this.permission = permission;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermissionId that)) return false;
        return Objects.equals(role, that.role) && Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() { return Objects.hash(role, permission); }
}
