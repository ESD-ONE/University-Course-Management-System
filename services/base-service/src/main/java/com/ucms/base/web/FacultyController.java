package com.ucms.base.web;

import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.CreateFacultyRequest;
import com.ucms.base.web.dto.FacultyResponse;
import com.ucms.base.web.dto.UpdateFacultyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/faculties")
public class FacultyController {

    private final BaseService baseService;

    public FacultyController(BaseService baseService) {
        this.baseService = baseService;
    }

    @GetMapping
    public List<FacultyResponse> list() {
        return baseService.listFaculties();
    }

    @PostMapping
    public ResponseEntity<FacultyResponse> create(@Valid @RequestBody CreateFacultyRequest request,
                                                   @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(baseService.createFaculty(request, currentUserId));
    }

    @PutMapping("/{id}")
    public FacultyResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateFacultyRequest request,
                                   @AuthenticationPrincipal UUID currentUserId) {
        return baseService.updateFaculty(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        baseService.deleteFaculty(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
