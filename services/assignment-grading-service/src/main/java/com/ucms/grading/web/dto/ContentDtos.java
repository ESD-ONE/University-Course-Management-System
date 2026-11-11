package com.ucms.grading.web.dto;

import com.ucms.grading.domain.ContentItem;
import com.ucms.grading.domain.CourseWeek;
import com.ucms.grading.domain.LectureSession;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ContentDtos {
    private ContentDtos() {}

    private static final String HTTPS = "^https://.+";

    public record CreateWeekRequest(@NotNull @Min(1) Integer weekNumber, @NotNull LocalDate startDate,
                                    @NotNull LocalDate endDate, @Size(max = 200) String title) {}

    public record UpdateWeekRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                    @Size(max = 200) String title, @NotNull CourseWeek.Status status) {}

    public record SessionResponse(UUID id, UUID weekId, String title, String description, OffsetDateTime startsAt,
                                  OffsetDateTime endsAt, String deliveryMode, String meetingUrl, String location,
                                  String recordingUrl, String status) {
        public static SessionResponse from(LectureSession s) {
            return new SessionResponse(s.getId(), s.getWeek().getId(), s.getTitle(), s.getDescription(), s.getStartsAt(),
                    s.getEndsAt(), s.getDeliveryMode().name(), s.getMeetingUrl(), s.getLocation(), s.getRecordingUrl(),
                    s.getStatus().name());
        }
    }

    public record CreateSessionRequest(@NotBlank @Size(max = 200) String title, @Size(max = 10000) String description,
                                       @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt,
                                       @NotNull LectureSession.DeliveryMode deliveryMode,
                                       @Pattern(regexp = HTTPS, message = "must be an https URL") String meetingUrl,
                                       @Size(max = 200) String location) {}

    public record UpdateSessionRequest(@NotBlank @Size(max = 200) String title, @Size(max = 10000) String description,
                                       @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt,
                                       @NotNull LectureSession.DeliveryMode deliveryMode,
                                       @Pattern(regexp = HTTPS, message = "must be an https URL") String meetingUrl,
                                       @Size(max = 200) String location,
                                       @Pattern(regexp = HTTPS, message = "must be an https URL") String recordingUrl,
                                       @NotNull LectureSession.Status status) {}

    public record ItemResponse(UUID id, UUID weekId, UUID sessionId, String type, String title, String description,
                               String fileName, long sizeBytes, String externalUrl, int sortOrder, String status,
                               OffsetDateTime publishedAt) {
        public static ItemResponse from(ContentItem i) {
            return new ItemResponse(i.getId(), i.getWeek().getId(), i.getSession() == null ? null : i.getSession().getId(),
                    i.getType().name(), i.getTitle(), i.getDescription(), i.getFileName(),
                    i.getSizeBytes() == null ? 0 : i.getSizeBytes(), i.getExternalUrl(), i.getSortOrder(),
                    i.getStatus().name(), i.getPublishedAt());
        }
    }

    /** Link items only — file items go through the multipart endpoint. */
    public record CreateLinkItemRequest(@NotNull UUID weekId, UUID sessionId, @NotNull ContentItem.Type type,
                                        @NotBlank @Size(max = 200) String title, @Size(max = 10000) String description,
                                        @NotBlank @Pattern(regexp = HTTPS, message = "must be an https URL") String externalUrl) {}

    public record UpdateItemRequest(@NotBlank @Size(max = 200) String title, @Size(max = 10000) String description,
                                    Integer sortOrder) {}

    public record StatusRequest(@NotNull ContentItem.Status status) {}

    public record WeekResponse(UUID id, int weekNumber, LocalDate startDate, LocalDate endDate, String title,
                               String status, List<SessionResponse> sessions, List<ItemResponse> items) {
        public static WeekResponse from(CourseWeek w, List<SessionResponse> sessions, List<ItemResponse> items) {
            return new WeekResponse(w.getId(), w.getWeekNumber(), w.getStartDate(), w.getEndDate(), w.getTitle(),
                    w.getStatus().name(), sessions, items);
        }
    }
}
