package com.ucms.academic.service;

import com.ucms.academic.domain.CourseCatalog;
import com.ucms.academic.exception.DuplicateEntityException;
import com.ucms.academic.exception.HasDependentsException;
import com.ucms.academic.repository.CourseCatalogRepository;
import com.ucms.academic.repository.CourseRepository;
import com.ucms.academic.repository.ProgramCourseRepository;
import com.ucms.academic.web.dto.CourseCatalogResponse;
import com.ucms.academic.web.dto.CreateCourseCatalogRequest;
import com.ucms.academic.web.dto.UpdateCourseCatalogRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CourseCatalogService {

    private final CourseCatalogRepository courseCatalogRepository;
    private final CourseRepository courseRepository;
    private final ProgramCourseRepository programCourseRepository;

    public CourseCatalogService(CourseCatalogRepository courseCatalogRepository,
                                 CourseRepository courseRepository,
                                 ProgramCourseRepository programCourseRepository) {
        this.courseCatalogRepository = courseCatalogRepository;
        this.courseRepository = courseRepository;
        this.programCourseRepository = programCourseRepository;
    }

    /** departmentId: optional filter on owningDepartmentId — needed by
     * base-service's GET /course-catalog?departmentId= call (G-05's parallel
     * fix there). Omitted => every entry, unchanged from before. */
    @Transactional(readOnly = true)
    public List<CourseCatalogResponse> list(UUID departmentId) {
        List<CourseCatalog> all = departmentId == null
                ? courseCatalogRepository.findByDeletedAtIsNullOrderByCodeAsc()
                : courseCatalogRepository.findByOwningDepartmentIdAndDeletedAtIsNullOrderByCodeAsc(departmentId);
        return all.stream().map(CourseCatalogResponse::from).toList();
    }

    @Transactional
    public CourseCatalogResponse create(CreateCourseCatalogRequest request, UUID createdBy) {
        if (courseCatalogRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
            throw new DuplicateEntityException("A catalog subject with code \"" + request.code() + "\" already exists");
        }
        CourseCatalog c = new CourseCatalog();
        c.setCode(request.code());
        c.setName(request.name());
        c.setDefaultCredits(request.defaultCredits());
        c.setOwningDepartmentId(request.owningDepartmentId());
        c.setCreatedBy(createdBy);
        c.setUpdatedBy(createdBy);
        return CourseCatalogResponse.from(courseCatalogRepository.save(c));
    }

    /** code and owningDepartmentId aren't editable — see UpdateCourseCatalogRequest's
     * doc comment. */
    @Transactional
    public CourseCatalogResponse update(UUID id, UpdateCourseCatalogRequest request, UUID actorId) {
        CourseCatalog c = courseCatalogRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Catalog subject not found"));
        c.setName(request.name());
        c.setDefaultCredits(request.defaultCredits());
        c.setUpdatedBy(actorId);
        return CourseCatalogResponse.from(courseCatalogRepository.save(c));
    }

    /** Blocked while this catalog subject is still referenced by a non-deleted Course
     * (a course template built from it) or a ProgramCourse (a curriculum entry) —
     * mirrors DegreeProgramService.delete's dependents-guard pattern. */
    @Transactional
    public void delete(UUID id, UUID actorId) {
        CourseCatalog c = courseCatalogRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Catalog subject not found"));
        if (courseRepository.existsByCatalogCourseIdAndDeletedAtIsNull(id)) {
            throw new HasDependentsException(c.getName() + " still has courses built from it — remove them first");
        }
        if (programCourseRepository.existsByCatalogCourseId(id)) {
            throw new HasDependentsException(c.getName() + " is still used in a program's curriculum — remove that first");
        }
        c.setDeletedAt(OffsetDateTime.now());
        c.setDeletedBy(actorId);
        courseCatalogRepository.save(c);
    }
}
