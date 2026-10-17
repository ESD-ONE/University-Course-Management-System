package com.ucms.academic.service;

import com.ucms.academic.domain.AcademicYear;
import com.ucms.academic.domain.AcademicYearStatus;
import com.ucms.academic.domain.Semester;
import com.ucms.academic.domain.SemesterStatus;
import com.ucms.academic.exception.ArchivedYearException;
import com.ucms.academic.exception.DuplicateEntityException;
import com.ucms.academic.exception.HasDependentsException;
import com.ucms.academic.repository.AcademicYearRepository;
import com.ucms.academic.repository.CourseAcademicYearRepository;
import com.ucms.academic.repository.DegreeProgramAcademicYearRepository;
import com.ucms.academic.repository.SemesterRepository;
import com.ucms.academic.web.dto.AcademicYearResponse;
import com.ucms.academic.web.dto.CreateAcademicYearRequest;
import com.ucms.academic.web.dto.CreateSemesterRequest;
import com.ucms.academic.web.dto.SemesterResponse;
import com.ucms.academic.web.dto.UpdateAcademicYearRequest;
import com.ucms.academic.web.dto.UpdateSemesterRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Matches html-wireframes/index.html's adminCalendar() / newYear() / addSemester(). */
@Service
public class AcademicCalendarService {

    private final AcademicYearRepository academicYearRepository;
    private final SemesterRepository semesterRepository;
    private final DegreeProgramAcademicYearRepository degreeProgramAcademicYearRepository;
    private final CourseAcademicYearRepository courseAcademicYearRepository;

    public AcademicCalendarService(AcademicYearRepository academicYearRepository, SemesterRepository semesterRepository,
                                    DegreeProgramAcademicYearRepository degreeProgramAcademicYearRepository,
                                    CourseAcademicYearRepository courseAcademicYearRepository) {
        this.academicYearRepository = academicYearRepository;
        this.semesterRepository = semesterRepository;
        this.degreeProgramAcademicYearRepository = degreeProgramAcademicYearRepository;
        this.courseAcademicYearRepository = courseAcademicYearRepository;
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> listYears() {
        return academicYearRepository.findByDeletedAtIsNullOrderByStartDateDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AcademicYearResponse createYear(CreateAcademicYearRequest request, UUID createdBy) {
        if (academicYearRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new DuplicateEntityException("\"" + request.name() + "\" already exists");
        }
        AcademicYear year = new AcademicYear();
        year.setName(request.name());
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        year.setStatus(request.status());
        year.setCreatedBy(createdBy);
        year.setUpdatedBy(createdBy);
        year = academicYearRepository.save(year);

        if (request.createDefaultSemesters()) {
            createSemesterInternal(year, "Semester 1", 1, SemesterStatus.UPCOMING, createdBy);
            createSemesterInternal(year, "Semester 2", 2, SemesterStatus.UPCOMING, createdBy);
        }
        return toResponse(year);
    }

    /** Marks an UPCOMING year ACTIVE. Multiple academic years can be ACTIVE at once —
     * degree programs run 3-4 years, so students in different years of study have
     * concurrently live coursework across several academic years simultaneously. This
     * does NOT deactivate any other year (see V7 migration). */
    @Transactional
    public AcademicYearResponse activate(UUID yearId, UUID updatedBy) {
        AcademicYear target = academicYearRepository.findByIdAndDeletedAtIsNull(yearId)
                .orElseThrow(() -> new EntityNotFoundException("Academic year not found"));
        if (target.getStatus() != AcademicYearStatus.ACTIVE) {
            target.setStatus(AcademicYearStatus.ACTIVE);
            target.setUpdatedBy(updatedBy);
            target = academicYearRepository.save(target);
        }
        return toResponse(target);
    }

    /** General-purpose edit — name/dates/status. Any number of years may be ACTIVE at
     * once (see activate()'s Javadoc); this never touches any other year's status. */
    @Transactional
    public AcademicYearResponse updateYear(UUID yearId, UpdateAcademicYearRequest request, UUID updatedBy) {
        AcademicYear year = academicYearRepository.findByIdAndDeletedAtIsNull(yearId)
                .orElseThrow(() -> new EntityNotFoundException("Academic year not found"));
        if (academicYearRepository.existsByNameAndDeletedAtIsNullAndIdNot(request.name(), yearId)) {
            throw new DuplicateEntityException("\"" + request.name() + "\" already exists");
        }
        year.setName(request.name());
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        year.setStatus(request.status());
        year.setUpdatedBy(updatedBy);
        return toResponse(academicYearRepository.save(year));
    }

    /** Blocked while ACTIVE (switch another year active first) or while degree
     * programs/courses are still assigned to it — removes its semesters too, since they
     * have no meaning independent of the year. */
    @Transactional
    public void deleteYear(UUID yearId, UUID deletedBy) {
        AcademicYear year = academicYearRepository.findByIdAndDeletedAtIsNull(yearId)
                .orElseThrow(() -> new EntityNotFoundException("Academic year not found"));
        if (year.getStatus() == AcademicYearStatus.ACTIVE) {
            throw new HasDependentsException("Cannot delete the ACTIVE academic year — activate a different year first");
        }
        if (!degreeProgramAcademicYearRepository.findByAcademicYearIdAndDeletedAtIsNull(yearId).isEmpty()) {
            throw new HasDependentsException(year.getName() + " still has degree programs assigned — remove them first");
        }
        if (!courseAcademicYearRepository.findByAcademicYearIdAndDeletedAtIsNull(yearId).isEmpty()) {
            throw new HasDependentsException(year.getName() + " still has courses assigned — remove them first");
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (Semester semester : semesterRepository.findByAcademicYearIdAndDeletedAtIsNullOrderBySequenceAsc(yearId)) {
            semester.setDeletedAt(now);
            semester.setDeletedBy(deletedBy);
            semesterRepository.save(semester);
        }
        year.setDeletedAt(now);
        year.setDeletedBy(deletedBy);
        academicYearRepository.save(year);
    }

    @Transactional
    public SemesterResponse updateSemester(UUID yearId, UUID semesterId, UpdateSemesterRequest request, UUID updatedBy) {
        AcademicYear year = academicYearRepository.findByIdAndDeletedAtIsNull(yearId)
                .orElseThrow(() -> new EntityNotFoundException("Academic year not found"));
        if (year.getStatus() == AcademicYearStatus.ARCHIVED) {
            throw new ArchivedYearException(year.getName() + " is archived and read-only");
        }
        Semester semester = semesterRepository.findByIdAndAcademicYearIdAndDeletedAtIsNull(semesterId, yearId)
                .orElseThrow(() -> new EntityNotFoundException("Semester not found"));
        if (semesterRepository.existsByAcademicYearIdAndNameAndDeletedAtIsNullAndIdNot(yearId, request.name(), semesterId)) {
            throw new DuplicateEntityException(year.getName() + " already has a \"" + request.name() + "\"");
        }
        semester.setName(request.name());
        semester.setSequence(request.sequence());
        semester.setEnrollmentOpenAt(request.enrollmentOpenAt());
        semester.setEnrollmentCloseAt(request.enrollmentCloseAt());
        semester.setStatus(request.status());
        semester.setUpdatedBy(updatedBy);
        return SemesterResponse.from(semesterRepository.save(semester));
    }

    @Transactional
    public void deleteSemester(UUID yearId, UUID semesterId, UUID deletedBy) {
        Semester semester = semesterRepository.findByIdAndAcademicYearIdAndDeletedAtIsNull(semesterId, yearId)
                .orElseThrow(() -> new EntityNotFoundException("Semester not found"));
        if (courseAcademicYearRepository.existsBySemesterIdAndDeletedAtIsNull(semesterId)) {
            throw new HasDependentsException(semester.getName() + " still has courses assigned — remove them first");
        }
        semester.setDeletedAt(OffsetDateTime.now());
        semester.setDeletedBy(deletedBy);
        semesterRepository.save(semester);
    }

    @Transactional
    public SemesterResponse addSemester(UUID yearId, CreateSemesterRequest request, UUID createdBy) {
        AcademicYear year = academicYearRepository.findByIdAndDeletedAtIsNull(yearId)
                .orElseThrow(() -> new EntityNotFoundException("Academic year not found"));
        if (year.getStatus() == AcademicYearStatus.ARCHIVED) {
            throw new ArchivedYearException(year.getName() + " is archived and read-only");
        }
        if (semesterRepository.existsByAcademicYearIdAndNameAndDeletedAtIsNull(yearId, request.name())) {
            throw new DuplicateEntityException(year.getName() + " already has a \"" + request.name() + "\"");
        }
        Semester semester = createSemesterInternal(year, request.name(), request.sequence(), request.status(), createdBy);
        semester.setEnrollmentOpenAt(request.enrollmentOpenAt());
        semester.setEnrollmentCloseAt(request.enrollmentCloseAt());
        return SemesterResponse.from(semesterRepository.save(semester));
    }

    private Semester createSemesterInternal(AcademicYear year, String name, int sequence, SemesterStatus status, UUID createdBy) {
        Semester semester = new Semester();
        semester.setAcademicYear(year);
        semester.setName(name);
        semester.setSequence(sequence);
        semester.setStatus(status);
        semester.setCreatedBy(createdBy);
        semester.setUpdatedBy(createdBy);
        return semesterRepository.save(semester);
    }

    private AcademicYearResponse toResponse(AcademicYear year) {
        List<SemesterResponse> semesters = semesterRepository
                .findByAcademicYearIdAndDeletedAtIsNullOrderBySequenceAsc(year.getId()).stream()
                .map(SemesterResponse::from)
                .toList();
        return AcademicYearResponse.from(year, semesters);
    }
}
