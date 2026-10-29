package com.ucms.identity.web;

import com.ucms.identity.service.AssignmentService;
import com.ucms.identity.web.dto.AssignmentClaim;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Service-to-service endpoints; SecurityConfig admits only service tokens here. */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final AssignmentService assignmentService;

    public InternalController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** A user's current scope assignments, in the shape academic-structure-service's
     *  PermissionResolver works on — see AssignmentCacheInvalidator. */
    @GetMapping("/assignments/{userId}")
    public List<AssignmentClaim> assignments(@PathVariable("userId") UUID userId) {
        return assignmentService.resolveClaimsForUser(userId);
    }
}
