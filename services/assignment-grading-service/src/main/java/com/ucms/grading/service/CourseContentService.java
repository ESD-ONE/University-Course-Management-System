package com.ucms.grading.service;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.domain.ContentItem;
import com.ucms.grading.domain.CourseWeek;
import com.ucms.grading.domain.LectureSession;
import com.ucms.grading.exception.ConflictException;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.repository.ContentItemRepository;
import com.ucms.grading.repository.CourseWeekRepository;
import com.ucms.grading.repository.LectureSessionRepository;
import com.ucms.grading.security.Caller;
import com.ucms.grading.security.DeliveryAccess;
import com.ucms.grading.storage.FileStorage;
import com.ucms.grading.web.dto.ContentDtos.CreateLinkItemRequest;
import com.ucms.grading.web.dto.ContentDtos.CreateSessionRequest;
import com.ucms.grading.web.dto.ContentDtos.CreateWeekRequest;
import com.ucms.grading.web.dto.ContentDtos.ItemResponse;
import com.ucms.grading.web.dto.ContentDtos.SessionResponse;
import com.ucms.grading.web.dto.ContentDtos.UpdateItemRequest;
import com.ucms.grading.web.dto.ContentDtos.UpdateSessionRequest;
import com.ucms.grading.web.dto.ContentDtos.UpdateWeekRequest;
import com.ucms.grading.web.dto.ContentDtos.WeekResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Week-by-week delivery of one course offering: weeks, the lecture sessions within
 * them, and their materials (notes/slides/readings/videos/links). See
 * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 7. Assignments stay owned by
 * AssignmentService; the frontend merges them into this same week-by-week view by
 * weekId, fetched separately.
 */
@Service
public class CourseContentService {

    private final CourseWeekRepository weeks;
    private final LectureSessionRepository sessions;
    private final ContentItemRepository items;
    private final AcademicClient academic;
    private final EnrollmentClient enrollment;
    private final DeliveryAccess access;
    private final FileStorage storage;
    private final EventPublisher events;

    public CourseContentService(CourseWeekRepository weeks, LectureSessionRepository sessions,
                                ContentItemRepository items, AcademicClient academic, EnrollmentClient enrollment,
                                DeliveryAccess access, FileStorage storage, EventPublisher events) {
        this.weeks = weeks;
        this.sessions = sessions;
        this.items = items;
        this.academic = academic;
        this.enrollment = enrollment;
        this.access = access;
        this.storage = storage;
        this.events = events;
    }

    /** The whole workspace for one offering: every week with its sessions and materials.
     * Staff who may view it (owner, admin, or a scoped Program/Department Head) see
     * drafts too; a student sees only published weeks/sessions/items, and only once
     * enrolled — this is a learning resource, not a public catalog. */
    @Transactional(readOnly = true)
    public List<WeekResponse> listWeeks(UUID offeringId, Caller caller) {
        AcademicClient.Offering offering = academic.getOffering(offeringId);
        boolean staff = !caller.isStudent();
        if (staff) {
            access.requireStaffView(offering, caller);
        } else if (!enrollment.isEnrolled(caller.id(), offeringId)) {
            throw new AccessDeniedException("Not enrolled in this course");
        }

        List<CourseWeek> weekRows = weeks.findByCourseOfferingIdAndDeletedAtIsNullOrderByWeekNumberAsc(offeringId);
        if (!staff) weekRows = weekRows.stream().filter(w -> w.getStatus() == CourseWeek.Status.PUBLISHED).toList();
        if (weekRows.isEmpty()) return List.of();

        List<LectureSession> sessionRows = sessions.findByCourseOfferingIdAndDeletedAtIsNullOrderByStartsAtAsc(offeringId);
        List<ContentItem> itemRows = items.findByCourseOfferingIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(offeringId);
        if (!staff) {
            sessionRows = sessionRows.stream().filter(s -> s.getStatus() == LectureSession.Status.PUBLISHED).toList();
            itemRows = itemRows.stream().filter(i -> i.getStatus() == ContentItem.Status.PUBLISHED).toList();
        }

        Map<UUID, List<SessionResponse>> sessionsByWeek = sessionRows.stream()
                .collect(Collectors.groupingBy(s -> s.getWeek().getId(), LinkedHashMap::new,
                        Collectors.mapping(SessionResponse::from, Collectors.toList())));
        Map<UUID, List<ItemResponse>> itemsByWeek = itemRows.stream()
                .collect(Collectors.groupingBy(i -> i.getWeek().getId(), LinkedHashMap::new,
                        Collectors.mapping(ItemResponse::from, Collectors.toList())));

        return weekRows.stream()
                .map(w -> WeekResponse.from(w, sessionsByWeek.getOrDefault(w.getId(), List.of()), itemsByWeek.getOrDefault(w.getId(), List.of())))
                .toList();
    }

    @Transactional
    public WeekResponse createWeek(UUID offeringId, CreateWeekRequest request, Caller caller) {
        AcademicClient.Offering offering = academic.getOffering(offeringId);
        access.requireManage(offering, caller);
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date can't be before the start date.");
        }
        if (weeks.existsByCourseOfferingIdAndWeekNumberAndDeletedAtIsNull(offeringId, request.weekNumber())) {
            throw new ConflictException("WEEK_EXISTS", "Week " + request.weekNumber() + " already exists for this course.");
        }
        CourseWeek w = new CourseWeek();
        w.setCourseOfferingId(offeringId);
        w.setWeekNumber(request.weekNumber());
        w.setStartDate(request.startDate());
        w.setEndDate(request.endDate());
        w.setTitle(request.title());
        w.setStatus(CourseWeek.Status.DRAFT);
        w.setCreatedBy(caller.id());
        w.setUpdatedBy(caller.id());
        return WeekResponse.from(weeks.save(w), List.of(), List.of());
    }

    @Transactional
    public WeekResponse updateWeek(UUID weekId, UpdateWeekRequest request, Caller caller) {
        CourseWeek w = findWeek(weekId);
        access.requireManage(academic.getOffering(w.getCourseOfferingId()), caller);
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date can't be before the start date.");
        }
        w.setStartDate(request.startDate());
        w.setEndDate(request.endDate());
        w.setTitle(request.title());
        w.setStatus(request.status());
        w.setUpdatedBy(caller.id());
        return WeekResponse.from(weeks.save(w),
                sessions.findByWeekIdAndDeletedAtIsNull(weekId).stream().map(SessionResponse::from).toList(),
                items.findByWeekIdAndDeletedAtIsNull(weekId).stream().map(ItemResponse::from).toList());
    }

    @Transactional
    public void deleteWeek(UUID weekId, Caller caller) {
        CourseWeek w = findWeek(weekId);
        access.requireManage(academic.getOffering(w.getCourseOfferingId()), caller);
        w.setDeletedAt(OffsetDateTime.now());
        w.setDeletedBy(caller.id());
        w.setUpdatedBy(caller.id());
        weeks.save(w);
    }

    @Transactional
    public SessionResponse createSession(UUID weekId, CreateSessionRequest request, Caller caller) {
        CourseWeek w = findWeek(weekId);
        AcademicClient.Offering offering = academic.getOffering(w.getCourseOfferingId());
        access.requireManage(offering, caller);
        requireOrder(request.startsAt(), request.endsAt());
        LectureSession s = new LectureSession();
        s.setCourseOfferingId(w.getCourseOfferingId());
        s.setWeek(w);
        s.setTitle(request.title());
        s.setDescription(request.description());
        s.setStartsAt(request.startsAt());
        s.setEndsAt(request.endsAt());
        s.setDeliveryMode(request.deliveryMode());
        s.setMeetingUrl(request.meetingUrl());
        s.setLocation(request.location());
        s.setStatus(LectureSession.Status.DRAFT);
        s.setCreatedBy(caller.id());
        s.setUpdatedBy(caller.id());
        s = sessions.save(s);
        return SessionResponse.from(s);
    }

    @Transactional
    public SessionResponse updateSession(UUID sessionId, UpdateSessionRequest request, Caller caller) {
        LectureSession s = findSession(sessionId);
        AcademicClient.Offering offering = academic.getOffering(s.getCourseOfferingId());
        access.requireManage(offering, caller);
        requireOrder(request.startsAt(), request.endsAt());
        boolean publishing = request.status() == LectureSession.Status.PUBLISHED && s.getStatus() != LectureSession.Status.PUBLISHED;
        s.setTitle(request.title());
        s.setDescription(request.description());
        s.setStartsAt(request.startsAt());
        s.setEndsAt(request.endsAt());
        s.setDeliveryMode(request.deliveryMode());
        s.setMeetingUrl(request.meetingUrl());
        s.setLocation(request.location());
        s.setRecordingUrl(request.recordingUrl());
        s.setStatus(request.status());
        s.setUpdatedBy(caller.id());
        s = sessions.save(s);
        if (publishing) publishLectureEvent(s, offering);
        return SessionResponse.from(s);
    }

    @Transactional
    public void deleteSession(UUID sessionId, Caller caller) {
        LectureSession s = findSession(sessionId);
        access.requireManage(academic.getOffering(s.getCourseOfferingId()), caller);
        s.setDeletedAt(OffsetDateTime.now());
        s.setDeletedBy(caller.id());
        s.setUpdatedBy(caller.id());
        sessions.save(s);
    }

    @Transactional
    public ItemResponse createLinkItem(CreateLinkItemRequest request, Caller caller) {
        CourseWeek w = findWeek(request.weekId());
        AcademicClient.Offering offering = academic.getOffering(w.getCourseOfferingId());
        access.requireManage(offering, caller);
        ContentItem item = new ContentItem();
        item.setCourseOfferingId(w.getCourseOfferingId());
        item.setWeek(w);
        item.setSession(resolveSession(request.sessionId(), w.getId()));
        item.setType(request.type());
        item.setTitle(request.title());
        item.setDescription(request.description());
        item.setExternalUrl(request.externalUrl());
        item.setSortOrder(nextSortOrder(w.getId()));
        item.setStatus(ContentItem.Status.DRAFT);
        item.setCreatedBy(caller.id());
        item.setUpdatedBy(caller.id());
        return ItemResponse.from(items.save(item));
    }

    @Transactional
    public ItemResponse uploadFileItem(UUID weekId, UUID sessionId, ContentItem.Type type, String title,
                                       String description, MultipartFile file, Caller caller) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload.");
        CourseWeek w = findWeek(weekId);
        AcademicClient.Offering offering = academic.getOffering(w.getCourseOfferingId());
        access.requireManage(offering, caller);
        String name = Files.safeName(file.getOriginalFilename());
        String key = "content/" + w.getCourseOfferingId() + "/" + UUID.randomUUID() + "-" + name;
        try {
            storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read the uploaded file.");
        }
        ContentItem item = new ContentItem();
        item.setCourseOfferingId(w.getCourseOfferingId());
        item.setWeek(w);
        item.setSession(resolveSession(sessionId, w.getId()));
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setObjectKey(key);
        item.setFileName(name);
        item.setContentType(file.getContentType());
        item.setSizeBytes(file.getSize());
        item.setSortOrder(nextSortOrder(w.getId()));
        item.setStatus(ContentItem.Status.DRAFT);
        item.setCreatedBy(caller.id());
        item.setUpdatedBy(caller.id());
        return ItemResponse.from(items.save(item));
    }

    @Transactional
    public ItemResponse updateItem(UUID itemId, UpdateItemRequest request, Caller caller) {
        ContentItem item = findItem(itemId);
        access.requireManage(academic.getOffering(item.getCourseOfferingId()), caller);
        item.setTitle(request.title());
        item.setDescription(request.description());
        if (request.sortOrder() != null) item.setSortOrder(request.sortOrder());
        item.setUpdatedBy(caller.id());
        return ItemResponse.from(items.save(item));
    }

    @Transactional
    public ItemResponse setItemStatus(UUID itemId, ContentItem.Status status, Caller caller) {
        ContentItem item = findItem(itemId);
        AcademicClient.Offering offering = academic.getOffering(item.getCourseOfferingId());
        access.requireManage(offering, caller);
        boolean publishing = status == ContentItem.Status.PUBLISHED && item.getStatus() != ContentItem.Status.PUBLISHED;
        item.setStatus(status);
        if (publishing && item.getPublishedAt() == null) item.setPublishedAt(OffsetDateTime.now());
        item.setUpdatedBy(caller.id());
        item = items.save(item);
        if (publishing) publishContentEvent(item, offering);
        return ItemResponse.from(item);
    }

    @Transactional
    public void deleteItem(UUID itemId, Caller caller) {
        ContentItem item = findItem(itemId);
        access.requireManage(academic.getOffering(item.getCourseOfferingId()), caller);
        item.setDeletedAt(OffsetDateTime.now());
        item.setDeletedBy(caller.id());
        item.setUpdatedBy(caller.id());
        items.save(item);
    }

    @Transactional(readOnly = true)
    public Files.Download download(UUID itemId, Caller caller) {
        ContentItem item = findItem(itemId);
        if (item.getObjectKey() == null) throw new EntityNotFoundException("This item has no file attached");
        AcademicClient.Offering offering = academic.getOffering(item.getCourseOfferingId());
        if (caller.isStudent()) {
            if (item.getStatus() != ContentItem.Status.PUBLISHED || !enrollment.isEnrolled(caller.id(), offering.id())) {
                throw new AccessDeniedException("Not enrolled in this course");
            }
        } else {
            access.requireStaffView(offering, caller);
        }
        return new Files.Download(item.getFileName(), item.getContentType(), storage.get(item.getObjectKey()));
    }

    private int nextSortOrder(UUID weekId) {
        return items.findByWeekIdAndDeletedAtIsNull(weekId).stream()
                .map(ContentItem::getSortOrder).max(Comparator.naturalOrder()).orElse(-1) + 1;
    }

    private LectureSession resolveSession(UUID sessionId, UUID weekId) {
        if (sessionId == null) return null;
        LectureSession s = findSession(sessionId);
        if (!s.getWeek().getId().equals(weekId)) {
            throw new IllegalArgumentException("That lecture session belongs to a different week.");
        }
        return s;
    }

    private static void requireOrder(OffsetDateTime starts, OffsetDateTime ends) {
        if (!ends.isAfter(starts)) throw new IllegalArgumentException("End time must be after the start time.");
    }

    private void publishLectureEvent(LectureSession s, AcademicClient.Offering offering) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offeringId", offering.id().toString());
        data.put("courseCode", offering.code());
        data.put("title", s.getTitle());
        data.put("startsAt", s.getStartsAt().toString());
        events.publish("lecture.scheduled", data);
    }

    private void publishContentEvent(ContentItem item, AcademicClient.Offering offering) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offeringId", offering.id().toString());
        data.put("courseCode", offering.code());
        data.put("title", item.getTitle());
        data.put("weekTitle", item.getWeek().getTitle() != null ? item.getWeek().getTitle() : "Week " + item.getWeek().getWeekNumber());
        events.publish("content.item.published", data);
    }

    CourseWeek findWeek(UUID id) {
        return weeks.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new EntityNotFoundException("Week not found"));
    }

    private LectureSession findSession(UUID id) {
        return sessions.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new EntityNotFoundException("Lecture session not found"));
    }

    private ContentItem findItem(UUID id) {
        return items.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new EntityNotFoundException("Content item not found"));
    }
}
