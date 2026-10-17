package com.ucms.identity.repository;

import com.ucms.identity.domain.AssignmentPermissionOverride;
import com.ucms.identity.domain.AssignmentPermissionOverrideId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentPermissionOverrideRepository
        extends JpaRepository<AssignmentPermissionOverride, AssignmentPermissionOverrideId> {
    List<AssignmentPermissionOverride> findByAssignmentId(UUID assignmentId);
}
