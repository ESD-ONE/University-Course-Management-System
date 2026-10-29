package com.ucms.base.web.dto;

import com.ucms.base.domain.Department;
import java.util.UUID;

public record DepartmentResponse(UUID id, UUID facultyId, String facultyName, String name, String code, String status) {
    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.getId(), d.getFaculty().getId(), d.getFaculty().getName(),
                d.getName(), d.getCode(), d.getStatus().name());
    }
}
