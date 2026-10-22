package com.ucms.referencedata.web;

import com.ucms.referencedata.service.ReferenceDataService;
import com.ucms.referencedata.web.dto.CreateGradingScaleRequest;
import com.ucms.referencedata.web.dto.GradingScaleResponse;
import com.ucms.referencedata.web.dto.UpdateGradingScaleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/grading-scales")
public class GradingScaleController {

    private final ReferenceDataService referenceDataService;

    public GradingScaleController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public List<GradingScaleResponse> list() {
        return referenceDataService.listGradingScales();
    }

    @PostMapping
    public ResponseEntity<GradingScaleResponse> create(@Valid @RequestBody CreateGradingScaleRequest request,
                                                         @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceDataService.createGradingScale(request, currentUserId));
    }

    @PutMapping("/{id}")
    public GradingScaleResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateGradingScaleRequest request,
                                        @AuthenticationPrincipal UUID currentUserId) {
        return referenceDataService.updateGradingScale(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        referenceDataService.deleteGradingScale(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
