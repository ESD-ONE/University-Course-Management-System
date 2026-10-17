package com.ucms.academic.service;

import com.ucms.academic.domain.AcademicYear;
import com.ucms.academic.domain.Course;
import com.ucms.academic.domain.CourseCatalog;
import com.ucms.academic.domain.DegreeProgram;
import com.ucms.academic.domain.DegreeProgramAcademicYear;
import com.ucms.academic.domain.ProgramCourse;
import com.ucms.academic.exception.DuplicateEntityException;
import com.ucms.academic.exception.HasDependentsException;
import com.ucms.academic.repository.CourseCatalogRepository;
import com.ucms.academic.repository.AcademicYearRepository;
import com.ucms.academic.repository.CourseRepository;
import com.ucms.academic.repository.DegreeProgramAcademicYearRepository;
import com.ucms.academic.repository.DegreeProgramRepository;
import com.ucms.academic.repository.ProgramCourseRepository;
import com.ucms.academic.security.AssignmentClaim;
import com.ucms.academic.security.CurrentAssignments;
import com.ucms.academic.security.PermissionResolver;
import com.ucms.academic.web.dto.CreateDegreeProgramAcademicYearRequest;
import com.ucms.academic.web.dto.CreateDegreeProgramRequest;
import com.ucms.academic.web.dto.CreateProgramCourseRequest;
import com.ucms.academic.web.dto.DegreeProgramAcademicYearResponse;
import com.ucms.academic.web.dto.DegreeProgramResponse;
import com.ucms.academic.web.dto.ProgramCourseResponse;
import com.ucms.academic.web.dto.UpdateDegreeProgramAcademicYearRequest;
import com.ucms.academic.web.dto.UpdateDegreeProgramRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Matches html-wireframes/index.html's adminPrograms() / newProgram() / addCurriculum(),
 * extended with the "Reference Degree Details" page (department-filtered catalog
 * management) and the per-academic-year assignment that the "Degree Programs" screen
 * now also manages — some degrees don't continue into the next academic year.
 */
@Service
public class DegreeProgramService {

    private static final String MODULE = "Degree Programs";

    private final DegreeProgramRepository degreeProgramRepository;
    private final ProgramCourseRepository programCourseRepository;
    private final CourseCatalogRepository courseCatalogRepository;
    private final CourseRepository courseRepository;
    private final DegreeProgramAcademicYearRepository academicYearAssignmentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final PermissionResolver permissionResolver;
    private final CourseOfferingService courseOfferingService;

    public DegreeProgramService(DegreeProgramRepository degreeProgramRepository,
                                 ProgramCourseRepository programCourseRepository,
                                 CourseCatalogRepository courseCatalogRepository,
                                 CourseRepository courseRepository,
                                 DegreeProgramAcademicYearRepository academicYearAssignmentRepository,
                                 AcademicYearRepository academicYearRepository,
                                 PermissionResolver permissionResolver,
                                 CourseOfferingService courseOfferingService) {
        this.degreeProgramRepository = degreeProgramRepository;
        this.programCourseRepository = programCourseRepository;
        this.courseCatalogRepository = courseCatalogRepository;
        this.courseRepository = courseRepository;
        this.academicYearAssignmentRepository = academicYearAssignmentRepository;
        this.academicYearRepository = academicYearRepository;
        this.permissionResolver = permissionResolver;
        this.courseOfferingService = courseOfferingService;
    }

    @Transactional
    public DegreeProgramResponse update(UUID programId, UpdateDegreeProgramRequest request, UUID actorId) {
        DegreeProgram program = degreeProgramRepository.findByIdAndDeletedAtIsNull(programId)
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));
        if (!permissionResolver.canOnDepartment(program.getDepartmentId(), MODULE, "Edit")) {
            throw new AccessDeniedException("No permission to edit this reference degree");
        }
        program.setName(request.name());
        program.setDurationYears(request.durationYears());
        program.setTotalCredits(request.totalCredits());
        program.setStatus(request.status());
        program.setUpdatedBy(actorId);
        return DegreeProgramResponse.from(degreeProgramRepository.save(program));
    }

    /** Blocked while this reference degree still has courses, curriculum entries, or
     * academic-year assignments — all of those would otherwise point at a deleted
     * degree. Remove them (or the degree's courses) first. */
    @Transactional
    public void delete(UUID programId, UUID actorId) {
        DegreeProgram program = degreeProgramRepository.findByIdAndDeletedAtIsNull(programId)
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));
        if (!permissionResolver.canOnDepartment(program.getDepartmentId(), MODULE, "Delete")) {
            throw new AccessDeniedException("No permission to delete this reference degree");
        }
        if (courseRepository.existsByDegreeProgramIdAndDeletedAtIsNull(programId)) {
            throw new HasDependentsException(program.getName() + " still has courses — remove them first");
        }
        if (!academicYearAssignmentRepository.findByDegreeProgramIdAndDeletedAtIsNull(programId).isEmpty()) {
            throw new HasDependentsException(program.getName() + " is still assigned to an academic year — remove that first");
        }
        if (programCourseRepository.existsByDegreeProgramId(programId)) {
            throw new HasDependentsException(program.getName() + " still has curriculum entries — remove them first");
        }
        program.setDeletedAt(java.time.OffsetDateTime.now());
        program.setDeletedBy(actorId);
        degreeProgramRepository.save(program);
    }

    /** ADMIN (or anyone with a GLOBAL "Degree Programs:View" grant) sees every program,
     * optionally narrowed by departmentId; everyone else sees only programs in a
     * department they have View access to — e.g. a Department Head's DEPARTMENT-scoped
     * assignment. See docs/SYSTEM_ARCHITECTURE_REPORT.md section 6/7.2. */
    @Transactional(readOnly = true)
    public List<DegreeProgramResponse> list(UUID departmentId) {
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        List<DegreeProgram> all = departmentId == null
                ? degreeProgramRepository.findByDeletedAtIsNullOrderByNameAsc()
                : degreeProgramRepository.findByDepartmentIdAndDeletedAtIsNullOrderByNameAsc(departmentId);
        if (permissionResolver.isGlobal(assignments, MODULE, "View")) {
            return all.stream().map(DegreeProgramResponse::from).toList();
        }
        return all.stream()
                .filter(p -> permissionResolver.canOnDepartment(assignments, p.getDepartmentId(), MODULE, "View"))
                .map(DegreeProgramResponse::from)
                .toList();
    }

    @Transactional
    public DegreeProgramResponse create(CreateDegreeProgramRequest request, UUID createdBy) {
        if (!permissionResolver.canOnDepartment(request.departmentId(), MODULE, "Create")) {
            throw new AccessDeniedException("No permission to create a program in this department");
        }
        if (degreeProgramRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
            throw new DuplicateEntityException("Code \"" + request.code() + "\" is already in use");
        }
        DegreeProgram p = new DegreeProgram();
        p.setCode(request.code());
        p.setName(request.name());
        p.setDepartmentId(request.departmentId());
        p.setDurationYears(request.durationYears());
        p.setTotalCredits(request.totalCredits());
        p.setStatus(request.status() == null ? com.ucms.academic.domain.ProgramStatus.ACTIVE : request.status());
        p.setCreatedBy(createdBy);
        p.setUpdatedBy(createdBy);
        return DegreeProgramResponse.from(degreeProgramRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<ProgramCourseResponse> listCurriculum(UUID programId) {
        return programCourseRepository.findByDegreeProgramIdOrderByRecommendedYearAscRecommendedSemesterAsc(programId).stream()
                .map(ProgramCourseResponse::from)
                .toList();
    }

    @Transactional
    public ProgramCourseResponse addCurriculum(UUID programId, CreateProgramCourseRequest request, UUID createdBy) {
        DegreeProgram program = degreeProgramRepository.findByIdAndDeletedAtIsNull(programId)
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));
        if (!permissionResolver.canOnDepartment(program.getDepartmentId(), MODULE, "Edit")) {
            throw new AccessDeniedException("No permission to edit this program's curriculum");
        }
        CourseCatalog catalogCourse = courseCatalogRepository.findByIdAndDeletedAtIsNull(request.catalogCourseId())
                .orElseThrow(() -> new EntityNotFoundException("Catalog course not found"));
        if (programCourseRepository.existsByDegreeProgramIdAndCatalogCourseId(programId, request.catalogCourseId())) {
            throw new DuplicateEntityException(catalogCourse.getCode() + " is already in this program's curriculum");
        }
        ProgramCourse pc = new ProgramCourse();
        pc.setDegreeProgram(program);
        pc.setCatalogCourse(catalogCourse);
        pc.setRequirementType(request.requirementType());
        pc.setRecommendedYear(request.recommendedYear());
        pc.setRecommendedSemester(request.recommendedSemester());
        pc.setCreditValue(request.creditValue());
        pc.setCreatedBy(createdBy);
        pc.setUpdatedBy(createdBy);
        return ProgramCourseResponse.from(programCourseRepository.save(pc));
    }

    // ---- Per-academic-year assignment ("Degree Programs" screen) ----

    /** academicYearId == null returns every assignment across every year (the screen's
     * "All Years" filter, which needs the Academic Year column to make sense). */
    @Transactional(readOnly = true)
    public List<DegreeProgramAcademicYearResponse> listForAcademicYear(UUID academicYearId) {
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        List<DegreeProgramAcademicYear> all = academicYearId == null
                ? academicYearAssignmentRepository.findByDeletedAtIsNull()
                : academicYearAssignmentRepository.findByAcademicYearIdAndDeletedAtIsNull(academicYearId);
        boolean global = permissionResolver.isGlobal(assignments, MODULE, "View");
        return all.stream()
                .filter(a -> global || permissionResolver.canOnDepartment(assignments, a.getDegreeProgram().getDepartmentId(), MODULE, "View"))
                .sorted(Comparator.comparing((DegreeProgramAcademicYear a) -> a.getAcademicYear().getName()).reversed()
                        .thenComparing(a -> a.getDegreeProgram().getName()))
                .map(DegreeProgramAcademicYearResponse::from)
                .toList();
    }

    /** courseIds in the request: null defaults to "every course currently in this
     * program" (the Assign Degree to Year dialog's "select all" default); an explicit
     * list — including an empty one — assigns exactly those, so a course can be dropped
     * for this year without touching the program's course catalog. Courses already
     * assigned to this year are skipped, not duplicated. */
    @Transactional
    public DegreeProgramAcademicYearResponse assignToAcademicYear(UUID programId, CreateDegreeProgramAcademicYearRequest request, UUID actorId) {
        DegreeProgram program = degreeProgramRepository.findByIdAndDeletedAtIsNull(programId)
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));
        if (!permissionResolver.canOnDepartment(program.getDepartmentId(), MODULE, "Edit")) {
            throw new AccessDeniedException("No permission to assign this program to an academic year");
        }
        AcademicYear year = academicYearRepository.findByIdAndDeletedAtIsNull(request.academicYearId())
                .orElseThrow(() -> new EntityNotFoundException("Academic year not found"));
        if (academicYearAssignmentRepository.existsByDegreeProgramIdAndAcademicYearIdAndDeletedAtIsNull(programId, request.academicYearId())) {
            throw new DuplicateEntityException(program.getName() + " is already assigned to " + year.getName());
        }
        DegreeProgramAcademicYear a = new DegreeProgramAcademicYear();
        a.setDegreeProgram(program);
        a.setAcademicYear(year);
        a.setDurationYears(request.durationYears());
        a.setTotalCredits(request.totalCredits());
        a.setStatus(request.status());
        a.setCreatedBy(actorId);
        a.setUpdatedBy(actorId);
        DegreeProgramAcademicYearResponse response = DegreeProgramAcademicYearResponse.from(academicYearAssignmentRepository.save(a));

        List<UUID> courseIds = request.courseIds() != null
                ? request.courseIds()
                : courseRepository.findByDegreeProgramIdAndDeletedAtIsNullOrderByCreatedAtDesc(programId).stream().map(Course::getId).toList();
        for (UUID courseId : courseIds) {
            courseOfferingService.assignCourseToYearIfAbsent(courseId, year, actorId);
        }
        return response;
    }

    @Transactional
    public DegreeProgramAcademicYearResponse updateAssignment(UUID programId, UUID assignmentId,
                                                               UpdateDegreeProgramAcademicYearRequest request, UUID actorId) {
        DegreeProgramAcademicYear a = academicYearAssignmentRepository.findByIdAndDegreeProgramIdAndDeletedAtIsNull(assignmentId, programId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        if (!permissionResolver.canOnDepartment(a.getDegreeProgram().getDepartmentId(), MODULE, "Edit")) {
            throw new AccessDeniedException("No permission to edit this assignment");
        }
        a.setDurationYears(request.durationYears());
        a.setTotalCredits(request.totalCredits());
        a.setStatus(request.status());
        a.setUpdatedBy(actorId);
        return DegreeProgramAcademicYearResponse.from(academicYearAssignmentRepository.save(a));
    }

    @Transactional
    public void deleteAssignment(UUID programId, UUID assignmentId, UUID actorId) {
        DegreeProgramAcademicYear a = academicYearAssignmentRepository.findByIdAndDegreeProgramIdAndDeletedAtIsNull(assignmentId, programId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        if (!permissionResolver.canOnDepartment(a.getDegreeProgram().getDepartmentId(), MODULE, "Delete")) {
            throw new AccessDeniedException("No permission to remove this assignment");
        }
        a.setDeletedAt(java.time.OffsetDateTime.now());
        a.setDeletedBy(actorId);
        academicYearAssignmentRepository.save(a);
    }

    /** The "Degree Programs" screen's per-row collapse — every course of this
     * assignment's degree program that's assigned to its academic year, with each
     * course's lecturer for that year (overridden-or-default). See
     * CourseOfferingService.listForDegreeProgramYear. */
    @Transactional(readOnly = true)
    public List<com.ucms.academic.web.dto.CourseAcademicYearResponse> listCoursesForAssignment(UUID programId, UUID assignmentId) {
        DegreeProgramAcademicYear a = academicYearAssignmentRepository.findByIdAndDegreeProgramIdAndDeletedAtIsNull(assignmentId, programId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        if (!permissionResolver.canOnDepartment(a.getDegreeProgram().getDepartmentId(), MODULE, "View")) {
            throw new AccessDeniedException("No permission to view this program's courses");
        }
        return courseOfferingService.listForDegreeProgramYear(a.getAcademicYear().getId(), programId);
    }
}
