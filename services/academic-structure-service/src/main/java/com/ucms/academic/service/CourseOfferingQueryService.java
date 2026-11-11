package com.ucms.academic.service;

import com.ucms.academic.domain.AcademicYear;
import com.ucms.academic.domain.AcademicYearStatus;
import com.ucms.academic.domain.CourseAcademicYear;
import com.ucms.academic.domain.CourseStatus;
import com.ucms.academic.domain.DegreeProgram;
import com.ucms.academic.domain.DegreeProgramAcademicYear;
import com.ucms.academic.domain.ProgramCourse;
import com.ucms.academic.repository.AcademicYearRepository;
import com.ucms.academic.repository.CourseAcademicYearRepository;
import com.ucms.academic.repository.DegreeProgramAcademicYearRepository;
import com.ucms.academic.repository.ProgramCourseRepository;
import com.ucms.academic.security.AssignmentClaim;
import com.ucms.academic.security.CurrentAssignments;
import com.ucms.academic.security.CurrentRole;
import com.ucms.academic.security.PermissionResolver;
import com.ucms.academic.web.dto.LearningTreeResponse;
import com.ucms.academic.web.dto.LearningTreeResponse.OfferingNode;
import com.ucms.academic.web.dto.LearningTreeResponse.ProgramNode;
import com.ucms.academic.web.dto.LearningTreeResponse.YearNode;
import com.ucms.academic.web.dto.OfferingAccessResponse;
import com.ucms.academic.web.dto.CourseOfferingResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only, cross-service view of course offerings. Readable by any authenticated
 * caller: this is the course catalogue a student browses before enrolling, and the
 * lookup Enrollment / Assignment & Grading / Analytics use to resolve an offering's
 * capacity, lecturer and open/closed state — not department-administrative data, so it
 * deliberately isn't department-scoped the way Degree Program management is.
 */
@Service
public class CourseOfferingQueryService {

    private final CourseAcademicYearRepository offeringRepository;
    private final ProgramCourseRepository programCourseRepository;
    private final AcademicYearRepository academicYearRepository;
    private final DegreeProgramAcademicYearRepository degreeProgramAcademicYearRepository;
    private final PermissionResolver permissions;

    public CourseOfferingQueryService(CourseAcademicYearRepository offeringRepository,
                                       ProgramCourseRepository programCourseRepository,
                                       AcademicYearRepository academicYearRepository,
                                       DegreeProgramAcademicYearRepository degreeProgramAcademicYearRepository,
                                       PermissionResolver permissions) {
        this.offeringRepository = offeringRepository;
        this.programCourseRepository = programCourseRepository;
        this.academicYearRepository = academicYearRepository;
        this.degreeProgramAcademicYearRepository = degreeProgramAcademicYearRepository;
        this.permissions = permissions;
    }

    /**
     * The offerings a staff member works with — the learning hub's tree is grouped from
     * this. Admins: all. Anyone else: offerings they teach, plus every offering of a
     * program/department they hold a scoped role on (Program Head, Department Head).
     * Students get nothing here; theirs come from their enrollments.
     */
    @Transactional(readOnly = true)
    public List<CourseOfferingResponse> accessible(UUID callerId) {
        if (CurrentRole.is("STUDENT")) return List.of();
        boolean admin = CurrentRole.is("ADMIN");
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        Map<String, ProgramCourse> curriculum = curriculumIndex();
        return offeringRepository.findAllLiveWithDetails().stream()
                .filter(o -> admin || callerId.equals(resolvedLecturer(o)) || scoped(assignments, o, "Courses", "View"))
                .map(o -> toResponse(o, curriculum))
                .sorted(Comparator.comparing(CourseOfferingResponse::code))
                .toList();
    }

    @Transactional(readOnly = true)
    public OfferingAccessResponse access(UUID offeringId, UUID callerId) {
        CourseAcademicYear o = offeringRepository.findAllLiveWithDetails().stream()
                .filter(x -> x.getId().equals(offeringId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Course offering not found"));
        if (CurrentRole.is("STUDENT")) return new OfferingAccessResponse(false, false);
        if (CurrentRole.is("ADMIN") || callerId.equals(resolvedLecturer(o))) return new OfferingAccessResponse(true, true);
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        boolean manage = scoped(assignments, o, "Course Content", "Edit");
        return new OfferingAccessResponse(manage || scoped(assignments, o, "Courses", "View"), manage);
    }

    /** The Learning Hub tree for staff: every accessible offering, grouped by academic
     * year then degree program, in display order (newest year first, program name,
     * course code). Admins instead get the full catalogue (every academic year and
     * every degree program assigned to it, whether or not it has offerings yet) — see
     * learningTreeForAdmin. */
    @Transactional(readOnly = true)
    public LearningTreeResponse learningTree(UUID callerId) {
        if (CurrentRole.is("ADMIN")) return learningTreeForAdmin();
        List<CourseOfferingResponse> offerings = accessible(callerId);
        return new LearningTreeResponse(groupByYearThenProgram(offerings));
    }

    /** Admin's Learning Hub view: the whole catalogue, not just what already has course
     * offerings. Built from academic years and their degree-program assignments (the
     * same data Program Management manages) with any existing offerings attached —
     * empty where a program/year combination hasn't had offerings created yet. This
     * lets an admin browse and manage the catalogue end to end from one place, unlike
     * staff/students whose tree is intentionally limited to what they're assigned to. */
    private LearningTreeResponse learningTreeForAdmin() {
        Map<String, ProgramCourse> curriculum = curriculumIndex();
        Map<String, List<CourseOfferingResponse>> offeringsByYearProgram = offeringRepository.findAllLiveWithDetails().stream()
                .map(o -> toResponse(o, curriculum))
                .collect(Collectors.groupingBy(o -> o.academicYearId() + "|" + o.degreeProgramId()));

        Map<UUID, List<DegreeProgramAcademicYear>> assignmentsByYear = degreeProgramAcademicYearRepository.findByDeletedAtIsNull().stream()
                .collect(Collectors.groupingBy(a -> a.getAcademicYear().getId()));

        List<YearNode> years = academicYearRepository.findByDeletedAtIsNullOrderByStartDateDesc().stream()
                .map(y -> {
                    List<ProgramNode> programs = assignmentsByYear.getOrDefault(y.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(a -> a.getDegreeProgram().getName()))
                            .map(a -> {
                                DegreeProgram p = a.getDegreeProgram();
                                List<OfferingNode> nodes = offeringsByYearProgram.getOrDefault(y.getId() + "|" + p.getId(), List.of()).stream()
                                        .sorted(Comparator.comparing(CourseOfferingResponse::code))
                                        .map(o -> new OfferingNode(o.id(), o.code(), o.name(), o.credits(), o.requirementType(),
                                                o.yearOfStudy(), o.status(), o.lecturerId(), o.openForEnrollment()))
                                        .toList();
                                return new ProgramNode(p.getId(), p.getCode(), p.getName(), nodes);
                            })
                            .toList();
                    return new YearNode(y.getId(), y.getName(), y.getStatus().name(), programs);
                })
                .toList();
        return new LearningTreeResponse(years);
    }

    static List<YearNode> groupByYearThenProgram(List<CourseOfferingResponse> offerings) {
        Map<UUID, List<CourseOfferingResponse>> byYear = offerings.stream()
                .collect(Collectors.groupingBy(CourseOfferingResponse::academicYearId, LinkedHashMap::new, Collectors.toList()));
        List<YearNode> years = new ArrayList<>();
        for (var yearEntry : byYear.entrySet()) {
            List<CourseOfferingResponse> inYear = yearEntry.getValue();
            CourseOfferingResponse any = inYear.get(0);
            Map<UUID, List<CourseOfferingResponse>> byProgram = inYear.stream()
                    .collect(Collectors.groupingBy(CourseOfferingResponse::degreeProgramId, LinkedHashMap::new, Collectors.toList()));
            List<ProgramNode> programs = byProgram.values().stream()
                    .map(list -> {
                        CourseOfferingResponse p = list.get(0);
                        List<OfferingNode> nodes = list.stream()
                                .sorted(Comparator.comparing(CourseOfferingResponse::code))
                                .map(o -> new OfferingNode(o.id(), o.code(), o.name(), o.credits(), o.requirementType(),
                                        o.yearOfStudy(), o.status(), o.lecturerId(), o.openForEnrollment()))
                                .toList();
                        return new ProgramNode(p.degreeProgramId(), p.degreeProgramCode(), p.degreeProgramName(), nodes);
                    })
                    .sorted(Comparator.comparing(ProgramNode::degreeProgramName))
                    .toList();
            years.add(new YearNode(any.academicYearId(), any.academicYearName(), any.academicYearStatus(), programs));
        }
        years.sort(Comparator.comparing(YearNode::academicYearName).reversed());
        return years;
    }

    private boolean scoped(List<AssignmentClaim> assignments, CourseAcademicYear o, String module, String action) {
        var program = o.getCourse().getDegreeProgram();
        return permissions.isScopedToProgram(assignments, program.getDepartmentId(), program.getId(), module, action);
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingResponse> search(UUID academicYearId, String status, UUID lecturerId,
                                                UUID degreeProgramId, Collection<UUID> ids) {
        Map<String, ProgramCourse> curriculum = curriculumIndex();
        return offeringRepository.findAllLiveWithDetails().stream()
                .filter(o -> academicYearId == null || o.getAcademicYear().getId().equals(academicYearId))
                .filter(o -> status == null || o.getStatus().name().equalsIgnoreCase(status))
                .filter(o -> degreeProgramId == null || o.getCourse().getDegreeProgram().getId().equals(degreeProgramId))
                .filter(o -> lecturerId == null || lecturerId.equals(resolvedLecturer(o)))
                .filter(o -> ids == null || ids.isEmpty() || ids.contains(o.getId()))
                .map(o -> toResponse(o, curriculum))
                .sorted(Comparator.comparing(CourseOfferingResponse::code))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseOfferingResponse get(UUID offeringId) {
        return offeringRepository.findAllLiveWithDetails().stream()
                .filter(o -> o.getId().equals(offeringId))
                .findFirst()
                .map(o -> toResponse(o, curriculumIndex()))
                .orElseThrow(() -> new EntityNotFoundException("Course offering not found"));
    }

    private Map<String, ProgramCourse> curriculumIndex() {
        Map<String, ProgramCourse> index = new HashMap<>();
        for (ProgramCourse pc : programCourseRepository.findAll()) {
            index.put(pc.getDegreeProgram().getId() + "|" + pc.getCatalogCourse().getId(), pc);
        }
        return index;
    }

    private static UUID resolvedLecturer(CourseAcademicYear o) {
        return o.getLecturerId() != null ? o.getLecturerId() : o.getCourse().getLecturerId();
    }

    private static CourseOfferingResponse toResponse(CourseAcademicYear o, Map<String, ProgramCourse> curriculum) {
        var course = o.getCourse();
        var catalog = course.getCatalogCourse();
        var program = course.getDegreeProgram();
        var semester = o.getSemester();
        ProgramCourse pc = curriculum.get(program.getId() + "|" + catalog.getId());
        int credits = pc != null ? pc.getCreditValue() : catalog.getDefaultCredits();
        OffsetDateTime openAt = semester == null ? null : semester.getEnrollmentOpenAt();
        OffsetDateTime closeAt = semester == null ? null : semester.getEnrollmentCloseAt();
        OffsetDateTime now = OffsetDateTime.now();
        boolean open = o.getStatus() == CourseStatus.PUBLISHED
                && o.getAcademicYear().getStatus() != AcademicYearStatus.ARCHIVED
                && (openAt == null || !now.isBefore(openAt))
                && (closeAt == null || !now.isAfter(closeAt));
        return new CourseOfferingResponse(o.getId(), course.getId(), catalog.getId(), catalog.getCode(), catalog.getName(),
                credits, pc == null ? null : pc.getRequirementType().name(),
                program.getId(), program.getCode(), program.getName(), program.getDepartmentId(), course.getYearOfStudy(),
                o.getAcademicYear().getId(), o.getAcademicYear().getName(), o.getAcademicYear().getStatus().name(),
                semester == null ? null : semester.getId(), semester == null ? null : semester.getName(),
                openAt, closeAt,
                o.getCapacity() != null ? o.getCapacity() : course.getCapacity(),
                resolvedLecturer(o), o.getStatus().name(), open);
    }
}
