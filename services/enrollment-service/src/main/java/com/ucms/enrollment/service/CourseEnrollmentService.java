package com.ucms.enrollment.service;

import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.domain.CourseEnrollment;
import com.ucms.enrollment.domain.CourseEnrollmentStatus;
import com.ucms.enrollment.domain.ProgramEnrollmentStatus;
import com.ucms.enrollment.domain.RegistrationLog;
import com.ucms.enrollment.domain.StudentProgramEnrollment;
import com.ucms.enrollment.domain.TargetType;
import com.ucms.enrollment.exception.ConflictException;
import com.ucms.enrollment.messaging.EventPublisher;
import com.ucms.enrollment.repository.CourseEnrollmentRepository;
import com.ucms.enrollment.repository.RegistrationLogRepository;
import com.ucms.enrollment.repository.StudentProgramEnrollmentRepository;
import com.ucms.enrollment.security.Caller;
import com.ucms.enrollment.web.dto.Dtos.CatalogEntryResponse;
import com.ucms.enrollment.web.dto.Dtos.CourseEnrollmentResponse;
import com.ucms.enrollment.web.dto.Dtos.EnrollRequest;
import com.ucms.enrollment.web.dto.Dtos.EnrollmentCheckResponse;
import com.ucms.enrollment.web.dto.Dtos.MyStudentResponse;
import com.ucms.enrollment.web.dto.Dtos.SeatCountResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * flow-01-student-enrollment.mmd. A student enrolls themselves; an admin (any offering)
 * or a lecturer (offerings they teach) can register a student on their behalf — same
 * write path, only the actor and the registration-log entry differ (flow-03's note).
 *
 * Seat rule: an offering's resolved capacity (from Academic Structure) bounds ENROLLED
 * rows. When full, the request either fails with 409 COURSE_FULL or — if the caller
 * asked to — joins the waitlist. Dropping an enrolled seat promotes the longest-waiting
 * student, first come first served.
 */
@Service
public class CourseEnrollmentService {

    private final CourseEnrollmentRepository repository;
    private final StudentProgramEnrollmentRepository programRepository;
    private final RegistrationLogRepository logRepository;
    private final AcademicClient academic;
    private final IdentityClient identity;
    private final EventPublisher events;
    private final SeatLock seatLock;

    public CourseEnrollmentService(CourseEnrollmentRepository repository, StudentProgramEnrollmentRepository programRepository,
                                   RegistrationLogRepository logRepository, AcademicClient academic, IdentityClient identity,
                                   EventPublisher events, SeatLock seatLock) {
        this.repository = repository;
        this.programRepository = programRepository;
        this.logRepository = logRepository;
        this.academic = academic;
        this.identity = identity;
        this.events = events;
        this.seatLock = seatLock;
    }

    @Transactional
    public CourseEnrollmentResponse enroll(EnrollRequest request, Caller caller) {
        AcademicClient.Offering offering = academic.getOffering(request.offeringId());
        UUID studentId = resolveStudent(request.studentId(), caller, offering);

        if (caller.isStudent()) {
            if (!offering.openForEnrollment()) {
                throw new ConflictException("NOT_OPEN", offering.code() + " isn't open for enrollment right now.");
            }
        } else if (!"PUBLISHED".equals(offering.status()) || "ARCHIVED".equals(offering.academicYearStatus())) {
            // Admins/lecturers may register late (outside the window), but never into a
            // draft, cancelled or archived offering.
            throw new ConflictException("NOT_OPEN", offering.code() + " isn't published for an active academic year.");
        }

        IdentityClient.User student = identity.getUser(studentId);
        if (!"STUDENT".equals(student.role())) {
            throw new IllegalArgumentException(student.name() + " isn't a student account.");
        }
        if (!"ACTIVE".equals(student.status())) {
            throw new ConflictException("STUDENT_NOT_ACTIVE", student.name() + "'s account isn't active.");
        }

        seatLock.lock(offering.id());

        Optional<CourseEnrollment> existing = repository.findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(studentId, offering.id());
        if (existing.isPresent()) {
            throw existing.get().getStatus() == CourseEnrollmentStatus.WAITLISTED
                    ? new ConflictException("ALREADY_WAITLISTED", "Already on the waitlist for " + offering.code() + ".")
                    : new ConflictException("ALREADY_ENROLLED", "Already enrolled in " + offering.code() + ".");
        }

        long enrolled = repository.countByCourseOfferingIdAndStatusAndDeletedAtIsNull(offering.id(), CourseEnrollmentStatus.ENROLLED);
        boolean full = enrolled >= offering.capacity();
        if (full && !request.joinWaitlistIfFull()) {
            throw new ConflictException("COURSE_FULL", offering.code() + " is full — join the waitlist?");
        }

        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(studentId);
        e.setCourseOfferingId(offering.id());
        e.setEnrolledOn(LocalDate.now());
        e.setStatus(full ? CourseEnrollmentStatus.WAITLISTED : CourseEnrollmentStatus.ENROLLED);
        e.setCreatedBy(caller.id());
        e.setUpdatedBy(caller.id());
        e = repository.save(e);

        logRepository.save(new RegistrationLog(studentId, TargetType.COURSE, offering.id(), caller.id(), caller.role(),
                full ? "COURSE_WAITLIST" : "COURSE_ENROLL"));
        events.publish(full ? "enrollment.student-waitlisted" : "enrollment.student-enrolled",
                courseEvent(e, offering, caller.id()));

        return toResponse(e, student, offering, null, null);
    }

    @Transactional
    public void drop(UUID enrollmentId, Caller caller) {
        CourseEnrollment e = repository.findByIdAndDeletedAtIsNull(enrollmentId)
                .orElseThrow(() -> new EntityNotFoundException("Enrollment not found"));
        AcademicClient.Offering offering = academic.getOffering(e.getCourseOfferingId());
        boolean allowed = caller.isAdmin()
                || (caller.isStudent() && e.getStudentId().equals(caller.id()))
                || (caller.isLecturer() && caller.id().equals(offering.lecturerId()));
        if (!allowed) throw new AccessDeniedException("Not your enrollment");

        seatLock.lock(e.getCourseOfferingId());
        boolean freedASeat = e.getStatus() == CourseEnrollmentStatus.ENROLLED;
        e.setStatus(CourseEnrollmentStatus.DROPPED);
        e.setDeletedAt(OffsetDateTime.now());
        e.setDeletedBy(caller.id());
        e.setUpdatedBy(caller.id());
        repository.save(e);
        logRepository.save(new RegistrationLog(e.getStudentId(), TargetType.COURSE, e.getCourseOfferingId(), caller.id(),
                caller.role(), "COURSE_DROP"));
        events.publish("enrollment.course-dropped", courseEvent(e, offering, caller.id()));

        if (freedASeat) promoteFromWaitlist(offering, caller);
    }

    private void promoteFromWaitlist(AcademicClient.Offering offering, Caller caller) {
        long enrolled = repository.countByCourseOfferingIdAndStatusAndDeletedAtIsNull(offering.id(), CourseEnrollmentStatus.ENROLLED);
        if (enrolled >= offering.capacity()) return;
        repository.findFirstByCourseOfferingIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(offering.id(), CourseEnrollmentStatus.WAITLISTED)
                .ifPresent(next -> {
                    next.setStatus(CourseEnrollmentStatus.ENROLLED);
                    next.setEnrolledOn(LocalDate.now());
                    next.setUpdatedBy(caller.id());
                    repository.save(next);
                    logRepository.save(new RegistrationLog(next.getStudentId(), TargetType.COURSE, offering.id(),
                            caller.id(), caller.role(), "WAITLIST_PROMOTED"));
                    events.publish("enrollment.waitlist-promoted", courseEvent(next, offering, caller.id()));
                });
    }

    /** STUDENT: always themselves. LECTURER: someone else, only on an offering they teach.
     *  ADMIN: anyone, must say who. */
    private static UUID resolveStudent(UUID requested, Caller caller, AcademicClient.Offering offering) {
        if (caller.isStudent()) {
            if (requested != null && !requested.equals(caller.id())) {
                throw new AccessDeniedException("Students can only enroll themselves");
            }
            return caller.id();
        }
        if (requested == null) throw new IllegalArgumentException("studentId is required when registering someone else.");
        if (caller.isAdmin()) return requested;
        if (caller.isLecturer() && caller.id().equals(offering.lecturerId())) return requested;
        throw new AccessDeniedException("You can only register students into courses you teach");
    }

    @Transactional(readOnly = true)
    public List<CourseEnrollmentResponse> mine(Caller caller) {
        List<CourseEnrollment> rows = repository.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(caller.id());
        if (rows.isEmpty()) return List.of();
        Map<UUID, AcademicClient.Offering> offerings = offeringsById(rows.stream().map(CourseEnrollment::getCourseOfferingId).toList());
        Map<UUID, IdentityClient.User> lecturers = identity.lookup(offerings.values().stream()
                .map(AcademicClient.Offering::lecturerId).filter(java.util.Objects::nonNull).toList());
        return rows.stream().map(e -> {
            AcademicClient.Offering o = offerings.get(e.getCourseOfferingId());
            IdentityClient.User lecturer = o == null ? null : lecturers.get(o.lecturerId());
            return toResponse(e, null, o, lecturer, null);
        }).toList();
    }

    /** The course roster: the offering's lecturer or an admin. */
    @Transactional(readOnly = true)
    public List<CourseEnrollmentResponse> roster(UUID offeringId, Caller caller) {
        AcademicClient.Offering offering = academic.getOffering(offeringId);
        if (!caller.isAdmin() && !caller.id().equals(offering.lecturerId())) {
            throw new AccessDeniedException("Not your course");
        }
        List<CourseEnrollment> rows = repository.findByCourseOfferingIdAndDeletedAtIsNullOrderByCreatedAtAsc(offeringId);
        if (rows.isEmpty()) return List.of();
        List<UUID> studentIds = rows.stream().map(CourseEnrollment::getStudentId).toList();
        Map<UUID, IdentityClient.User> students = identity.lookup(studentIds);
        Map<UUID, StudentProgramEnrollment> programs = activePrograms(studentIds);
        Map<UUID, String> programNames = programNames(programs.values());
        return rows.stream().map(e -> {
            StudentProgramEnrollment p = programs.get(e.getStudentId());
            String requirement = p != null && p.getDegreeProgramId().equals(offering.degreeProgramId())
                    ? (offering.requirementType() == null ? "ELECTIVE" : offering.requirementType())
                    : "ELECTIVE";
            CourseEnrollmentResponse base = toResponse(e, students.get(e.getStudentId()), offering, null, p);
            return new CourseEnrollmentResponse(base.id(), base.studentId(), base.studentName(), base.studentNumber(),
                    base.studentEmail(), p == null ? null : programNames.get(p.getDegreeProgramId()),
                    base.studentYearOfStudy(), base.offeringId(), base.courseCode(), base.courseName(), base.credits(),
                    requirement, base.academicYearName(), base.semesterName(), base.lecturerId(), base.lecturerName(),
                    base.enrolledOn(), base.status());
        }).toList();
    }

    /** Every student across every offering the calling lecturer teaches (lecturer
     *  "Students" screen). An admin sees all enrolled students. */
    @Transactional(readOnly = true)
    public List<MyStudentResponse> myStudents(Caller caller) {
        List<AcademicClient.Offering> offerings = caller.isAdmin()
                ? academic.searchOfferings(null, null, null, null)
                : academic.searchOfferings(null, null, caller.id(), null);
        if (offerings.isEmpty()) return List.of();
        Map<UUID, AcademicClient.Offering> byId = offerings.stream()
                .collect(Collectors.toMap(AcademicClient.Offering::id, Function.identity()));
        List<CourseEnrollment> rows = repository.findByCourseOfferingIdInAndDeletedAtIsNull(byId.keySet()).stream()
                .filter(e -> e.getStatus() == CourseEnrollmentStatus.ENROLLED)
                .toList();
        Map<UUID, List<CourseEnrollment>> byStudent = rows.stream()
                .collect(Collectors.groupingBy(CourseEnrollment::getStudentId, LinkedHashMap::new, Collectors.toList()));
        if (byStudent.isEmpty()) return List.of();
        Map<UUID, IdentityClient.User> students = identity.lookup(byStudent.keySet());
        Map<UUID, StudentProgramEnrollment> programs = activePrograms(byStudent.keySet());
        Map<UUID, String> programNames = programNames(programs.values());
        List<MyStudentResponse> result = new ArrayList<>();
        byStudent.forEach((studentId, enrollments) -> {
            IdentityClient.User s = students.get(studentId);
            StudentProgramEnrollment p = programs.get(studentId);
            result.add(new MyStudentResponse(studentId, s == null ? null : s.name(), s == null ? null : s.studentId(),
                    s == null ? null : s.email(), p == null ? null : programNames.get(p.getDegreeProgramId()),
                    p == null ? null : p.getCurrentYearOfStudy(),
                    enrollments.stream().map(e -> byId.get(e.getCourseOfferingId()).code()).distinct().toList(),
                    enrollments.stream().map(CourseEnrollment::getCourseOfferingId).toList()));
        });
        result.sort(java.util.Comparator.comparing(r -> r.name() == null ? "" : r.name()));
        return result;
    }

    /** Assignment & Grading's "GRADE->ENR: confirm student is enrolled" (flow-02). */
    @Transactional(readOnly = true)
    public EnrollmentCheckResponse check(UUID studentId, UUID offeringId) {
        return repository.findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(studentId, offeringId)
                .map(e -> new EnrollmentCheckResponse(e.getStatus() == CourseEnrollmentStatus.ENROLLED, e.getStatus().name()))
                .orElse(new EnrollmentCheckResponse(false, null));
    }

    @Transactional(readOnly = true)
    public List<SeatCountResponse> seats(Collection<UUID> offeringIds) {
        Map<UUID, long[]> counts = new HashMap<>();
        offeringIds.forEach(id -> counts.put(id, new long[2]));
        for (CourseEnrollment e : repository.findByCourseOfferingIdInAndDeletedAtIsNull(offeringIds)) {
            long[] c = counts.get(e.getCourseOfferingId());
            if (e.getStatus() == CourseEnrollmentStatus.ENROLLED) c[0]++;
            else if (e.getStatus() == CourseEnrollmentStatus.WAITLISTED) c[1]++;
        }
        return offeringIds.stream().map(id -> new SeatCountResponse(id, counts.get(id)[0], counts.get(id)[1])).toList();
    }

    /** Browse Courses: every published offering (optionally one academic year), with live
     *  seat counts and the caller's own status in each. */
    @Transactional(readOnly = true)
    public List<CatalogEntryResponse> catalog(UUID academicYearId, Caller caller) {
        List<AcademicClient.Offering> offerings = academic.searchOfferings("PUBLISHED", academicYearId, null, null).stream()
                .filter(o -> !"ARCHIVED".equals(o.academicYearStatus()))
                .toList();
        if (offerings.isEmpty()) return List.of();
        Map<UUID, SeatCountResponse> seatCounts = seats(offerings.stream().map(AcademicClient.Offering::id).toList()).stream()
                .collect(Collectors.toMap(SeatCountResponse::offeringId, Function.identity()));
        Map<UUID, CourseEnrollment> mine = repository.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(caller.id()).stream()
                .collect(Collectors.toMap(CourseEnrollment::getCourseOfferingId, Function.identity(), (a, b) -> a));
        Map<UUID, IdentityClient.User> lecturers = identity.lookup(offerings.stream()
                .map(AcademicClient.Offering::lecturerId).filter(java.util.Objects::nonNull).toList());
        return offerings.stream().map(o -> {
            SeatCountResponse s = seatCounts.get(o.id());
            CourseEnrollment my = mine.get(o.id());
            IdentityClient.User lecturer = lecturers.get(o.lecturerId());
            return new CatalogEntryResponse(o.id(), o.code(), o.name(), o.credits(), o.requirementType(),
                    o.degreeProgramId(), o.degreeProgramCode(), o.degreeProgramName(), o.yearOfStudy(),
                    o.academicYearId(), o.academicYearName(), o.semesterName(), o.lecturerId(),
                    lecturer == null ? null : lecturer.name(), o.capacity(), s.enrolled(), s.waitlisted(),
                    Math.max(0, o.capacity() - s.enrolled()), o.openForEnrollment(),
                    my == null ? null : my.getStatus().name(), my == null ? null : my.getId());
        }).toList();
    }

    private Map<UUID, AcademicClient.Offering> offeringsById(Collection<UUID> ids) {
        return academic.searchOfferings(null, null, null, ids).stream()
                .collect(Collectors.toMap(AcademicClient.Offering::id, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, StudentProgramEnrollment> activePrograms(Collection<UUID> studentIds) {
        return programRepository.findByStudentIdInAndStatusAndDeletedAtIsNull(studentIds, ProgramEnrollmentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(StudentProgramEnrollment::getStudentId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, String> programNames(Collection<StudentProgramEnrollment> enrollments) {
        if (enrollments.isEmpty()) return Map.of();
        return academic.listPrograms().stream()
                .collect(Collectors.toMap(AcademicClient.Program::id, AcademicClient.Program::name, (a, b) -> a));
    }

    private static Map<String, Object> courseEvent(CourseEnrollment e, AcademicClient.Offering o, UUID actorId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enrollmentId", e.getId().toString());
        data.put("studentId", e.getStudentId().toString());
        data.put("offeringId", o.id().toString());
        data.put("courseCode", o.code());
        data.put("courseName", o.name());
        data.put("degreeProgramId", o.degreeProgramId().toString());
        data.put("actorId", actorId.toString());
        data.put("onBehalf", !actorId.equals(e.getStudentId()));
        return data;
    }

    private static CourseEnrollmentResponse toResponse(CourseEnrollment e, IdentityClient.User student,
                                                       AcademicClient.Offering o, IdentityClient.User lecturer,
                                                       StudentProgramEnrollment program) {
        return new CourseEnrollmentResponse(e.getId(), e.getStudentId(),
                student == null ? null : student.name(), student == null ? null : student.studentId(),
                student == null ? null : student.email(), null,
                program == null ? null : program.getCurrentYearOfStudy(),
                e.getCourseOfferingId(), o == null ? null : o.code(), o == null ? null : o.name(),
                o == null ? null : o.credits(), o == null ? null : o.requirementType(),
                o == null ? null : o.academicYearName(), o == null ? null : o.semesterName(),
                o == null ? null : o.lecturerId(), lecturer == null ? null : lecturer.name(),
                e.getEnrolledOn(), e.getStatus().name());
    }
}
