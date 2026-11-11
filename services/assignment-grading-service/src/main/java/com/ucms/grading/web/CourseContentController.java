package com.ucms.grading.web;

import com.ucms.grading.domain.ContentItem;
import com.ucms.grading.security.Caller;
import com.ucms.grading.service.CourseContentService;
import com.ucms.grading.service.Files;
import com.ucms.grading.web.dto.ContentDtos.CreateLinkItemRequest;
import com.ucms.grading.web.dto.ContentDtos.CreateSessionRequest;
import com.ucms.grading.web.dto.ContentDtos.CreateWeekRequest;
import com.ucms.grading.web.dto.ContentDtos.ItemResponse;
import com.ucms.grading.web.dto.ContentDtos.SessionResponse;
import com.ucms.grading.web.dto.ContentDtos.StatusRequest;
import com.ucms.grading.web.dto.ContentDtos.UpdateItemRequest;
import com.ucms.grading.web.dto.ContentDtos.UpdateSessionRequest;
import com.ucms.grading.web.dto.ContentDtos.UpdateWeekRequest;
import com.ucms.grading.web.dto.ContentDtos.WeekResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** The learning hub's per-course workspace: weeks, their lecture sessions, and their
 * materials. See docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 7.3. */
@RestController
public class CourseContentController {

    private final CourseContentService content;

    public CourseContentController(CourseContentService content) {
        this.content = content;
    }

    @GetMapping("/offerings/{offeringId}/weeks")
    public List<WeekResponse> listWeeks(@PathVariable("offeringId") UUID offeringId) {
        return content.listWeeks(offeringId, Caller.current());
    }

    @PostMapping("/offerings/{offeringId}/weeks")
    public ResponseEntity<WeekResponse> createWeek(@PathVariable("offeringId") UUID offeringId,
                                                    @Valid @RequestBody CreateWeekRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(content.createWeek(offeringId, request, Caller.current()));
    }

    @PutMapping("/weeks/{weekId}")
    public WeekResponse updateWeek(@PathVariable("weekId") UUID weekId, @Valid @RequestBody UpdateWeekRequest request) {
        return content.updateWeek(weekId, request, Caller.current());
    }

    @DeleteMapping("/weeks/{weekId}")
    public ResponseEntity<Void> deleteWeek(@PathVariable("weekId") UUID weekId) {
        content.deleteWeek(weekId, Caller.current());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/weeks/{weekId}/sessions")
    public ResponseEntity<SessionResponse> createSession(@PathVariable("weekId") UUID weekId,
                                                          @Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(content.createSession(weekId, request, Caller.current()));
    }

    @PutMapping("/sessions/{sessionId}")
    public SessionResponse updateSession(@PathVariable("sessionId") UUID sessionId,
                                         @Valid @RequestBody UpdateSessionRequest request) {
        return content.updateSession(sessionId, request, Caller.current());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable("sessionId") UUID sessionId) {
        content.deleteSession(sessionId, Caller.current());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/content-items/link")
    public ResponseEntity<ItemResponse> createLink(@Valid @RequestBody CreateLinkItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(content.createLinkItem(request, Caller.current()));
    }

    @PostMapping(path = "/content-items/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemResponse> uploadFile(@RequestParam("weekId") UUID weekId,
                                                   @RequestParam(name = "sessionId", required = false) UUID sessionId,
                                                   @RequestParam("type") ContentItem.Type type,
                                                   @RequestParam("title") String title,
                                                   @RequestParam(name = "description", required = false) String description,
                                                   @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(content.uploadFileItem(weekId, sessionId, type, title, description, file, Caller.current()));
    }

    @PutMapping("/content-items/{itemId}")
    public ItemResponse updateItem(@PathVariable("itemId") UUID itemId, @Valid @RequestBody UpdateItemRequest request) {
        return content.updateItem(itemId, request, Caller.current());
    }

    @PatchMapping("/content-items/{itemId}/status")
    public ItemResponse setStatus(@PathVariable("itemId") UUID itemId, @Valid @RequestBody StatusRequest request) {
        return content.setItemStatus(itemId, request.status(), Caller.current());
    }

    @DeleteMapping("/content-items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable("itemId") UUID itemId) {
        content.deleteItem(itemId, Caller.current());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/content-items/{itemId}/file")
    public ResponseEntity<InputStreamResource> download(@PathVariable("itemId") UUID itemId) {
        return AssignmentController.asAttachment(content.download(itemId, Caller.current()));
    }
}
