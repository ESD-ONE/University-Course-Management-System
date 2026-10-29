package com.ucms.academic.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implements the effective-permission resolution algorithm from
 * docs/SYSTEM_ARCHITECTURE_REPORT.md section 5.3: a GLOBAL assignment or a
 * DEPARTMENT-scoped assignment matching the target department grants access if its
 * effective grant (override, else the role's baseline) is true. Allowed if ANY matching
 * assignment resolves to granted=true — one assignment's narrowing override never
 * silently blocks a different, broader grant.
 *
 * Course/Course-Module scope types aren't resolved here yet — course_modules don't
 * exist (Phase 5), so only GLOBAL and DEPARTMENT scoping is live in this phase; COURSE
 * scope assignments still only matter via Course.lecturerId's existing literal check.
 *
 * G-09: per-assignment permission overrides (AssignmentClaim.overrides()) ARE now
 * consulted — see effectiveGrant() below — rather than deferred.
 */
@Component
public class PermissionResolver {

    private final RoleCatalog roleCatalog;

    public PermissionResolver(RoleCatalog roleCatalog) {
        this.roleCatalog = roleCatalog;
    }

    public boolean canOnDepartment(UUID departmentId, String module, String actionKey) {
        return canOnDepartment(CurrentAssignments.get(), departmentId, module, actionKey);
    }

    public boolean canOnDepartment(List<AssignmentClaim> assignments, UUID departmentId, String module, String actionKey) {
        for (AssignmentClaim a : assignments) {
            boolean matchesScope = "GLOBAL".equals(a.scopeType())
                    || ("DEPARTMENT".equals(a.scopeType()) && a.scopeId() != null && a.scopeId().equals(departmentId));
            if (!matchesScope) continue;
            if (effectiveGrant(a, module, actionKey)) return true;
        }
        return false;
    }

    /** Unlike canOnDepartment, does NOT treat a GLOBAL assignment as a wildcard match —
     * every account holds a baseline GLOBAL assignment by default (see UserService /
     * the V5 backfill migration), so using canOnDepartment here would make every plain
     * Lecturer appear to be responsible for every department's courses. Only a genuine
     * DEPARTMENT-scoped assignment (e.g. a Department Head) counts for "which of my own
     * departments' courses am I responsible for" — see
     * docs/SYSTEM_ARCHITECTURE_REPORT.md section 7.2. */
    public boolean isDepartmentScoped(List<AssignmentClaim> assignments, UUID departmentId, String module, String actionKey) {
        for (AssignmentClaim a : assignments) {
            if ("DEPARTMENT".equals(a.scopeType()) && departmentId.equals(a.scopeId()) && effectiveGrant(a, module, actionKey)) {
                return true;
            }
        }
        return false;
    }

    public boolean isGlobal(List<AssignmentClaim> assignments, String module, String actionKey) {
        for (AssignmentClaim a : assignments) {
            if ("GLOBAL".equals(a.scopeType()) && effectiveGrant(a, module, actionKey)) return true;
        }
        return false;
    }

    /** Resolves the role's baseline grant for (module, actionKey), then consults this
     * assignment's per-permission overrides (G-09): overrides are keyed by permission
     * id rather than module/actionKey, so the pair is first resolved to a permission id
     * via roleCatalog.permissionIdOf(...). If this assignment's overrides map has an
     * entry for that permission id, it takes precedence over the baseline — true
     * overrides a false baseline denial, false overrides a true baseline grant. No
     * entry (or an unresolvable permission id) falls back to the baseline unchanged. */
    private boolean effectiveGrant(AssignmentClaim a, String module, String actionKey) {
        boolean baseline = roleCatalog.baselineGrant(a.roleId(), module, actionKey);
        UUID permissionId = roleCatalog.permissionIdOf(module, actionKey);
        if (permissionId != null && a.overrides() != null && a.overrides().containsKey(permissionId)) {
            return a.overrides().get(permissionId);
        }
        return baseline;
    }
}
