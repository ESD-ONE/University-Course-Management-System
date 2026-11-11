package com.ucms.base.web;

import com.ucms.base.domain.ReferenceStatus;
import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.CreateGradingScaleRequest;
import com.ucms.base.web.dto.GradingScaleResponse;
import com.ucms.base.web.dto.ReferenceCounts;
import com.ucms.base.web.dto.UpdateGradingScaleRequest;
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
@RequestMapping("/grading-scales")
public class GradingScaleController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final BaseService baseService;

    public GradingScaleController(BaseService baseService) {
        this.baseService = baseService;
    }

    /** status/q are optional and combinable — q is a case-insensitive name/bands search. */
    @GetMapping
    public Page<GradingScaleResponse> list(@RequestParam(name = "status", required = false) ReferenceStatus status,
                                            @RequestParam(name = "q", required = false) String q,
                                            @RequestParam(name = "page", defaultValue = "0") int page,
                                            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("name").ascending());
        return baseService.listGradingScales(status, q, pageable);
    }

    /** Total/active/inactive, independent of the list's current page/filter. */
    @GetMapping("/counts")
    public ReferenceCounts counts() {
        return baseService.gradingScaleCounts();
    }

    @PostMapping
    public ResponseEntity<GradingScaleResponse> create(@Valid @RequestBody CreateGradingScaleRequest request,
                                                         @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(baseService.createGradingScale(request, currentUserId));
    }

    @PutMapping("/{id}")
    public GradingScaleResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateGradingScaleRequest request,
                                        @AuthenticationPrincipal UUID currentUserId) {
        return baseService.updateGradingScale(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        baseService.deleteGradingScale(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
