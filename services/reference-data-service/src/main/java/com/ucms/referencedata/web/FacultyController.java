package com.ucms.referencedata.web;

import com.ucms.referencedata.service.ReferenceDataService;
import com.ucms.referencedata.web.dto.CreateFacultyRequest;
import com.ucms.referencedata.web.dto.FacultyResponse;
import com.ucms.referencedata.web.dto.UpdateFacultyRequest;
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

    private final ReferenceDataService referenceDataService;

    public FacultyController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public List<FacultyResponse> list() {
        return referenceDataService.listFaculties();
    }

    @PostMapping
    public ResponseEntity<FacultyResponse> create(@Valid @RequestBody CreateFacultyRequest request,
                                                   @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceDataService.createFaculty(request, currentUserId));
    }

    @PutMapping("/{id}")
    public FacultyResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateFacultyRequest request,
                                   @AuthenticationPrincipal UUID currentUserId) {
        return referenceDataService.updateFaculty(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        referenceDataService.deleteFaculty(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
