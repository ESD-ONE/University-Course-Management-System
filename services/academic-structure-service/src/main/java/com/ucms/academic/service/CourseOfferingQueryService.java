package com.ucms.academic.service;

import com.ucms.academic.domain.AcademicYearStatus;
import com.ucms.academic.domain.CourseAcademicYear;
import com.ucms.academic.domain.CourseStatus;
import com.ucms.academic.domain.ProgramCourse;
import com.ucms.academic.repository.CourseAcademicYearRepository;
import com.ucms.academic.repository.ProgramCourseRepository;
import com.ucms.academic.web.dto.CourseOfferingResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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

    public CourseOfferingQueryService(CourseAcademicYearRepository offeringRepository,
                                       ProgramCourseRepository programCourseRepository) {
        this.offeringRepository = offeringRepository;
        this.programCourseRepository = programCourseRepository;
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
