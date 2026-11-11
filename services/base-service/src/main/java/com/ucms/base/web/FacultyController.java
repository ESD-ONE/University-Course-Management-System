package com.ucms.base.web;

import com.ucms.base.domain.ReferenceStatus;
import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.CreateFacultyRequest;
import com.ucms.base.web.dto.FacultyResponse;
import com.ucms.base.web.dto.ReferenceCounts;
import com.ucms.base.web.dto.UpdateFacultyRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/faculties")
public class FacultyController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final BaseService baseService;

    public FacultyController(BaseService baseService) {
        this.baseService = baseService;
    }

    /** status/q are optional and combinable — q is a case-insensitive name/code search. */
    @GetMapping
    public Page<FacultyResponse> list(@RequestParam(name = "status", required = false) ReferenceStatus status,
                                       @RequestParam(name = "q", required = false) String q,
                                       @RequestParam(name = "page", defaultValue = "0") int page,
                                       @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("name").ascending());
        return baseService.listFaculties(status, q, pageable);
    }

    /** Total/active/inactive, independent of the list's current page/filter. */
    @GetMapping("/counts")
    public ReferenceCounts counts() {
        return baseService.facultyCounts();
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
