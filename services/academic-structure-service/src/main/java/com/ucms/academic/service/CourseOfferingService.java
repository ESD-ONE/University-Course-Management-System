package com.ucms.academic.service;

import com.ucms.academic.domain.AcademicYear;
import com.ucms.academic.domain.AcademicYearStatus;
import com.ucms.academic.domain.Course;
import com.ucms.academic.domain.CourseAcademicYear;
import com.ucms.academic.domain.CourseCatalog;
import com.ucms.academic.domain.DegreeProgram;
import com.ucms.academic.domain.Semester;
import com.ucms.academic.exception.ArchivedYearException;
import com.ucms.academic.exception.DuplicateEntityException;
import com.ucms.academic.repository.AcademicYearRepository;
import com.ucms.academic.repository.CourseAcademicYearRepository;
import com.ucms.academic.repository.CourseCatalogRepository;
import com.ucms.academic.repository.CourseRepository;
import com.ucms.academic.repository.DegreeProgramRepository;
import com.ucms.academic.repository.SemesterRepository;
import com.ucms.academic.security.AssignmentClaim;
import com.ucms.academic.security.CurrentAssignments;
import com.ucms.academic.security.CurrentRole;
import com.ucms.academic.security.PermissionResolver;
import com.ucms.academic.web.dto.CourseAcademicYearResponse;
import com.ucms.academic.web.dto.CourseResponse;
import com.ucms.academic.web.dto.CreateCourseAcademicYearRequest;
import com.ucms.academic.web.dto.CreateCourseRequest;
import com.ucms.academic.web.dto.UpdateCourseAcademicYearRequest;
import com.ucms.academic.web.dto.UpdateCourseRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Matches html-wireframes/index.html's adminCourses() (read-only oversight) and
 * lecturerCreateCourse() (self-service creation), extended so a course is a reusable
 * template (tied directly to a degree program, with a begin date and year-of-study
 * instead of a single academic year) that gets separately assigned to one or more
 * academic years via CourseAcademicYear. Enrollment count / "Full" status isn't
 * tracked here — that's Enrollment service data, which doesn't exist yet.
 */
@Service
public class CourseOfferingService {

    private static final String MODULE = "Courses";

    private final CourseRepository courseRepository;
    private final CourseCatalogRepository courseCatalogRepository;
    private final DegreeProgramRepository degreeProgramRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SemesterRepository semesterRepository;
    private final CourseAcademicYearRepository courseAcademicYearRepository;
    private final PermissionResolver permissionResolver;

    public CourseOfferingService(CourseRepository courseRepository, CourseCatalogRepository courseCatalogRepository,
                                  DegreeProgramRepository degreeProgramRepository,
                                  AcademicYearRepository academicYearRepository, SemesterRepository semesterRepository,
                                  CourseAcademicYearRepository courseAcademicYearRepository,
                                  PermissionResolver permissionResolver) {
        this.courseRepository = courseRepository;
        this.courseCatalogRepository = courseCatalogRepository;
        this.degreeProgramRepository = degreeProgramRepository;
        this.academicYearRepository = academicYearRepository;
        this.semesterRepository = semesterRepository;
        this.courseAcademicYearRepository = courseAcademicYearRepository;
        this.permissionResolver = permissionResolver;
    }

    /** Admin oversight listing, optionally narrowed by degreeProgramId (the Courses
     * page's new Degree Program filter). */
    @Transactional(readOnly = true)
    public List<CourseResponse> list(UUID degreeProgramId) {
        List<Course> courses = degreeProgramId == null
                ? courseRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()
                : courseRepository.findByDegreeProgramIdAndDeletedAtIsNullOrderByCreatedAtDesc(degreeProgramId);
        return courses.stream().map(CourseResponse::from).toList();
    }

    /** "mine=true" resolves to: courses where the caller is the literal lecturer_id
     * (self-service creation, unchanged), UNION courses in a degree program's
     * department the caller has a DEPARTMENT-scoped "Courses:View" grant for — e.g. a
     * Department Head sees every course in their department, not just ones they
     * personally teach. See docs/SYSTEM_ARCHITECTURE_REPORT.md section 7.2. */
    @Transactional(readOnly = true)
    public List<CourseResponse> listForLecturer(UUID lecturerId, UUID degreeProgramId) {
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        List<Course> all = degreeProgramId == null
                ? courseRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()
                : courseRepository.findByDegreeProgramIdAndDeletedAtIsNullOrderByCreatedAtDesc(degreeProgramId);
        return all.stream()
                .filter(c -> c.getLecturerId().equals(lecturerId)
                        || permissionResolver.isDepartmentScoped(assignments, c.getDegreeProgram().getDepartmentId(), MODULE, "View"))
                .map(CourseResponse::from)
                .toList();
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request, UUID callerId) {
        CourseCatalog catalogCourse = courseCatalogRepository.findByIdAndDeletedAtIsNull(request.catalogCourseId())
                .orElseThrow(() -> new EntityNotFoundException("Catalog course not found"));
        DegreeProgram program = degreeProgramRepository.findByIdAndDeletedAtIsNull(request.degreeProgramId())
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));

        UUID lecturerId = request.lecturerId() != null ? request.lecturerId() : callerId;
        boolean actingForSelf = lecturerId.equals(callerId);
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        boolean allowed = actingForSelf
                ? permissionResolver.canOnDepartment(assignments, program.getDepartmentId(), MODULE, "Create")
                : CurrentRole.is("ADMIN") || permissionResolver.isDepartmentScoped(assignments, program.getDepartmentId(), MODULE, "Create");
        if (!allowed) {
            throw new AccessDeniedException(actingForSelf
                    ? "No permission to create a course in this program's department"
                    : "No permission to create a course on another lecturer's behalf in this department");
        }

        Course course = new Course();
        course.setCatalogCourse(catalogCourse);
        course.setDegreeProgram(program);
        course.setBeginDate(request.beginDate());
        course.setYearOfStudy(request.yearOfStudy());
        course.setLecturerId(lecturerId);
        course.setCapacity(request.capacity());
        course.setStatus(request.status());
        course.setCreatedBy(callerId);
        course.setUpdatedBy(callerId);
        return CourseResponse.from(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse update(UUID courseId, UpdateCourseRequest request, UUID callerId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Course not found"));

        UUID targetLecturerId = request.lecturerId() != null ? request.lecturerId() : course.getLecturerId();
        boolean actingForSelf = course.getLecturerId().equals(callerId) && targetLecturerId.equals(callerId);
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        boolean allowed = actingForSelf
                ? permissionResolver.canOnDepartment(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Edit")
                : CurrentRole.is("ADMIN") || permissionResolver.isDepartmentScoped(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Edit");
        if (!allowed) {
            throw new AccessDeniedException("No permission to edit this course");
        }

        course.setBeginDate(request.beginDate());
        course.setYearOfStudy(request.yearOfStudy());
        course.setCapacity(request.capacity());
        course.setStatus(request.status());
        course.setLecturerId(targetLecturerId);
        course.setUpdatedBy(callerId);
        return CourseResponse.from(courseRepository.save(course));
    }

    /** Also removes the course's own academic-year assignments — they have no meaning
     * once the course template they point at is gone. */
    @Transactional
    public void delete(UUID courseId, UUID callerId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Course not found"));

        boolean actingForSelf = course.getLecturerId().equals(callerId);
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        boolean allowed = actingForSelf
                ? permissionResolver.canOnDepartment(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Delete")
                : CurrentRole.is("ADMIN") || permissionResolver.isDepartmentScoped(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Delete");
        if (!allowed) {
            throw new AccessDeniedException("No permission to delete this course");
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (CourseAcademicYear cay : courseAcademicYearRepository.findByCourseIdAndDeletedAtIsNull(courseId)) {
            cay.setDeletedAt(now);
            cay.setDeletedBy(callerId);
            courseAcademicYearRepository.save(cay);
        }
        course.setDeletedAt(now);
        course.setDeletedBy(callerId);
        courseRepository.save(course);
    }

    // ---- Per-academic-year assignment ----

    @Transactional(readOnly = true)
    public List<CourseAcademicYearResponse> listAcademicYearAssignments(UUID courseId) {
        return courseAcademicYearRepository.findByCourseIdAndDeletedAtIsNull(courseId).stream()
                .map(CourseAcademicYearResponse::from)
                .toList();
    }

    @Transactional
    public CourseAcademicYearResponse assignToAcademicYear(UUID courseId, CreateCourseAcademicYearRequest request, UUID callerId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Course not found"));

        boolean actingForSelf = course.getLecturerId().equals(callerId);
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        boolean allowed = actingForSelf
                ? permissionResolver.canOnDepartment(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Edit")
                : CurrentRole.is("ADMIN") || permissionResolver.isDepartmentScoped(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Edit");
        if (!allowed) {
            throw new AccessDeniedException("No permission to assign this course to an academic year");
        }

        AcademicYear year = academicYearRepository.findByIdAndDeletedAtIsNull(request.academicYearId())
                .orElseThrow(() -> new EntityNotFoundException("Academic year not found"));
        if (year.getStatus() == AcademicYearStatus.ARCHIVED) {
            throw new ArchivedYearException(year.getName() + " is archived and read-only");
        }
        if (courseAcademicYearRepository.existsByCourseIdAndAcademicYearIdAndDeletedAtIsNull(courseId, request.academicYearId())) {
            throw new DuplicateEntityException(course.getCatalogCourse().getCode() + " is already assigned to " + year.getName());
        }
        Semester semester = null;
        if (request.semesterId() != null) {
            semester = semesterRepository.findById(request.semesterId())
                    .orElseThrow(() -> new EntityNotFoundException("Semester not found"));
        }

        CourseAcademicYear cay = new CourseAcademicYear();
        cay.setCourse(course);
        cay.setAcademicYear(year);
        cay.setSemester(semester);
        cay.setCapacity(request.capacity());
        cay.setLecturerId(request.lecturerId());
        cay.setStatus(request.status());
        cay.setCreatedBy(callerId);
        cay.setUpdatedBy(callerId);
        return CourseAcademicYearResponse.from(courseAcademicYearRepository.save(cay));
    }

    @Transactional
    public CourseAcademicYearResponse updateAcademicYearAssignment(UUID courseId, UUID assignmentId,
                                                                     UpdateCourseAcademicYearRequest request, UUID callerId) {
        CourseAcademicYear cay = courseAcademicYearRepository.findByIdAndCourseIdAndDeletedAtIsNull(assignmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        Course course = cay.getCourse();

        boolean actingForSelf = course.getLecturerId().equals(callerId);
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        boolean allowed = actingForSelf
                ? permissionResolver.canOnDepartment(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Edit")
                : CurrentRole.is("ADMIN") || permissionResolver.isDepartmentScoped(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Edit");
        if (!allowed) {
            throw new AccessDeniedException("No permission to edit this course's year assignment");
        }
        if (cay.getAcademicYear().getStatus() == AcademicYearStatus.ARCHIVED) {
            throw new ArchivedYearException(cay.getAcademicYear().getName() + " is archived and read-only");
        }

        Semester semester = null;
        if (request.semesterId() != null) {
            semester = semesterRepository.findById(request.semesterId())
                    .orElseThrow(() -> new EntityNotFoundException("Semester not found"));
        }
        cay.setSemester(semester);
        cay.setCapacity(request.capacity());
        cay.setLecturerId(request.lecturerId());
        cay.setStatus(request.status());
        cay.setUpdatedBy(callerId);
        return CourseAcademicYearResponse.from(courseAcademicYearRepository.save(cay));
    }

    @Transactional
    public void deleteAcademicYearAssignment(UUID courseId, UUID assignmentId, UUID callerId) {
        CourseAcademicYear cay = courseAcademicYearRepository.findByIdAndCourseIdAndDeletedAtIsNull(assignmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        Course course = cay.getCourse();

        boolean actingForSelf = course.getLecturerId().equals(callerId);
        List<AssignmentClaim> assignments = CurrentAssignments.get();
        boolean allowed = actingForSelf
                ? permissionResolver.canOnDepartment(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Delete")
                : CurrentRole.is("ADMIN") || permissionResolver.isDepartmentScoped(assignments, course.getDegreeProgram().getDepartmentId(), MODULE, "Delete");
        if (!allowed) {
            throw new AccessDeniedException("No permission to remove this course's year assignment");
        }
        cay.setDeletedAt(OffsetDateTime.now());
        cay.setDeletedBy(callerId);
        courseAcademicYearRepository.save(cay);
    }

    /** DegreeProgramService.listCoursesForAssignment delegates here — every course of
     * a degree program that's assigned to a given academic year, each with its
     * resolved (overridden-or-default) lecturer. No extra permission check: the
     * caller (DegreeProgramController) already resolved and permission-checked the
     * assignment row this is scoped to. */
    @Transactional(readOnly = true)
    public List<CourseAcademicYearResponse> listForDegreeProgramYear(UUID academicYearId, UUID degreeProgramId) {
        return courseAcademicYearRepository.findByAcademicYearIdAndCourseDegreeProgramIdAndDeletedAtIsNull(academicYearId, degreeProgramId)
                .stream()
                .map(CourseAcademicYearResponse::from)
                .toList();
    }

    /** Used by DegreeProgramService when assigning a whole reference degree to an
     * academic year, to cascade the same year onto the program's courses. Silently
     * skips a course that's deleted or already assigned to this year — this is a
     * best-effort bulk side-effect of an already-authorized action, not an independent
     * entry point, so it doesn't re-run the per-course permission check or throw on
     * a partial overlap. */
    @Transactional
    public void assignCourseToYearIfAbsent(UUID courseId, AcademicYear year, UUID actorId) {
        Course course = courseRepository.findById(courseId).filter(c -> c.getDeletedAt() == null).orElse(null);
        if (course == null) return;
        if (courseAcademicYearRepository.existsByCourseIdAndAcademicYearIdAndDeletedAtIsNull(courseId, year.getId())) return;

        CourseAcademicYear cay = new CourseAcademicYear();
        cay.setCourse(course);
        cay.setAcademicYear(year);
        cay.setSemester(null);
        cay.setCapacity(null);
        cay.setStatus(course.getStatus());
        cay.setCreatedBy(actorId);
        cay.setUpdatedBy(actorId);
        courseAcademicYearRepository.save(cay);
    }
}
