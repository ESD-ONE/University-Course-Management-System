package com.ucms.grading.web;

import com.ucms.grading.security.Caller;
import com.ucms.grading.service.GradingService;
import com.ucms.grading.web.dto.Dtos.AssignmentAnalyticsResponse;
import com.ucms.grading.web.dto.Dtos.CourseResultRow;
import com.ucms.grading.web.dto.Dtos.OfferingAnalyticsResponse;
import com.ucms.grading.web.dto.Dtos.GradeHistoryResponse;
import com.ucms.grading.web.dto.Dtos.OfferingSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class GradeController {

    private final GradingService grading;

    public GradeController(GradingService grading) {
        this.grading = grading;
    }

    @GetMapping("/grades/me")
    public GradeHistoryResponse mine() {
        return grading.history(Caller.current());
    }

    @GetMapping("/grades/offerings/{offeringId}/summary")
    public OfferingSummaryResponse summary(@PathVariable("offeringId") UUID offeringId) {
        return grading.offeringSummary(offeringId, Caller.current());
    }

    @GetMapping("/stats/offering-summaries")
    public List<OfferingSummaryResponse> summaries(@RequestParam(name = "ids") List<UUID> ids) {
        return grading.offeringSummaries(ids);
    }

    @GetMapping("/stats/course-results")
    public List<CourseResultRow> courseResults() {
        return grading.courseResults();
    }

    /** Un-hides any grades the lecturer previously held back on this assignment. */
    @PatchMapping("/assignments/{id}/grades/release")
    public ResponseEntity<Void> releaseGrades(@PathVariable("id") UUID id) {
        grading.releaseGrades(id, Caller.current());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignments/{id}/analytics")
    public AssignmentAnalyticsResponse assignmentAnalytics(@PathVariable("id") UUID id) {
        return grading.assignmentAnalytics(id, Caller.current());
    }

    @GetMapping("/stats/offering-analytics")
    public OfferingAnalyticsResponse offeringAnalytics(@RequestParam(name = "offeringId") UUID offeringId) {
        return grading.offeringAnalytics(offeringId, Caller.current());
    }
}
