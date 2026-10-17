package com.ucms.identity.web;

import com.ucms.identity.service.RoleService;
import com.ucms.identity.web.dto.CreateRoleRequest;
import com.ucms.identity.web.dto.PermissionResponse;
import com.ucms.identity.web.dto.RoleResponse;
import com.ucms.identity.web.dto.UpdateRolePermissionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    public List<RoleResponse> listRoles() {
        return roleService.listRoles();
    }

    @GetMapping("/permissions")
    public List<PermissionResponse> listPermissions() {
        return roleService.listPermissions();
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request,
                                                    @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request, currentUserId));
    }

    @PatchMapping("/roles/{roleId}/permissions")
    public RoleResponse updateRolePermission(@PathVariable("roleId") UUID roleId,
                                              @Valid @RequestBody UpdateRolePermissionRequest request,
                                              @AuthenticationPrincipal UUID currentUserId) {
        return roleService.updateRolePermission(roleId, request, currentUserId);
    }
}
