package com.ucms.base.service;

import com.ucms.base.client.AcademicStructureClient;
import com.ucms.base.domain.CourseCategory;
import com.ucms.base.domain.Department;
import com.ucms.base.domain.Faculty;
import com.ucms.base.domain.GradingScale;
import com.ucms.base.domain.ReferenceStatus;
import com.ucms.base.exception.CodeAlreadyInUseException;
import com.ucms.base.exception.HasDependentsException;
import com.ucms.base.repository.CourseCategoryRepository;
import com.ucms.base.repository.DepartmentRepository;
import com.ucms.base.repository.FacultyRepository;
import com.ucms.base.repository.GradingScaleRepository;
import com.ucms.base.web.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One service for all four reference-data resource types — each is small (a handful of
 * rows, admin-maintained) and the UI treats them as one screen with tabs, not separate
 * workflows. Full CRUD per type; Faculty delete is blocked while it still has
 * departments (checked in-service). Department/GradingScale/CourseCategory deletes
 * don't check cross-service references (e.g. a department referenced by
 * academic-structure-service's degree programs) — those are unenforced UUID
 * references, not FKs, by this system's cross-service design (see
 * docs/architecture/DATA_GOVERNANCE.md); a soft-deleted department simply stops
 * resolving to a name there.
 */
@Service
public class BaseService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final AcademicStructureClient academicStructureClient;

    public BaseService(FacultyRepository facultyRepository,
                                 DepartmentRepository departmentRepository,
                                 GradingScaleRepository gradingScaleRepository,
                                 CourseCategoryRepository courseCategoryRepository,
                                 AcademicStructureClient academicStructureClient) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.courseCategoryRepository = courseCategoryRepository;
        this.academicStructureClient = academicStructureClient;
    }

    @Transactional(readOnly = true)
    public List<FacultyResponse> listFaculties() {
        return facultyRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(FacultyResponse::from).toList();
    }

    @Transactional
    public FacultyResponse createFaculty(CreateFacultyRequest request, UUID createdBy) {
        if (facultyRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
            throw new CodeAlreadyInUseException("A faculty with code \"" + request.code() + "\" already exists");
        }
        Faculty f = new Faculty();
        f.setName(request.name());
        f.setCode(request.code());
        f.setStatus(request.status() == null ? ReferenceStatus.ACTIVE : request.status());
        f.setCreatedBy(createdBy);
        f.setUpdatedBy(createdBy);
        return FacultyResponse.from(facultyRepository.save(f));
    }

    @Transactional
    public FacultyResponse updateFaculty(UUID id, UpdateFacultyRequest request, UUID actorId) {
        Faculty f = facultyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Faculty not found"));
        if (facultyRepository.existsByCodeAndDeletedAtIsNullAndIdNot(request.code(), id)) {
            throw new CodeAlreadyInUseException("A faculty with code \"" + request.code() + "\" already exists");
        }
        f.setName(request.name());
        f.setCode(request.code());
        f.setStatus(request.status());
        f.setUpdatedBy(actorId);
        return FacultyResponse.from(facultyRepository.save(f));
    }

    @Transactional
    public void deleteFaculty(UUID id, UUID actorId) {
        Faculty f = facultyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Faculty not found"));
        if (departmentRepository.existsByFacultyIdAndDeletedAtIsNull(id)) {
            throw new HasDependentsException(f.getName() + " still has departments — remove them first");
        }
        f.setDeletedAt(OffsetDateTime.now());
        f.setDeletedBy(actorId);
        facultyRepository.save(f);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments(UUID facultyId) {
        List<Department> departments = facultyId == null
                ? departmentRepository.findByDeletedAtIsNullOrderByNameAsc()
                : departmentRepository.findByFacultyIdAndDeletedAtIsNullOrderByNameAsc(facultyId);
        return departments.stream().map(DepartmentResponse::from).toList();
    }

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request, UUID createdBy) {
        if (departmentRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
            throw new CodeAlreadyInUseException("A department with code \"" + request.code() + "\" already exists");
        }
        Faculty faculty = facultyRepository.findById(request.facultyId())
                .filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Faculty not found"));
        Department d = new Department();
        d.setFaculty(faculty);
        d.setName(request.name());
        d.setCode(request.code());
        d.setStatus(request.status() == null ? ReferenceStatus.ACTIVE : request.status());
        d.setCreatedBy(createdBy);
        d.setUpdatedBy(createdBy);
        return DepartmentResponse.from(departmentRepository.save(d));
    }

    @Transactional
    public DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request, UUID actorId) {
        Department d = departmentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        if (departmentRepository.existsByCodeAndDeletedAtIsNullAndIdNot(request.code(), id)) {
            throw new CodeAlreadyInUseException("A department with code \"" + request.code() + "\" already exists");
        }
        Faculty faculty = facultyRepository.findById(request.facultyId())
                .filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Faculty not found"));
        d.setFaculty(faculty);
        d.setName(request.name());
        d.setCode(request.code());
        d.setStatus(request.status());
        d.setUpdatedBy(actorId);
        return DepartmentResponse.from(departmentRepository.save(d));
    }

    @Transactional
    public void deleteDepartment(UUID id, UUID actorId) {
        Department d = departmentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        // Fail closed: if academic-structure-service can't be reached, the exception
        // from hasDependents() propagates here uncaught and the delete below never runs
        // — an unreachable dependents-check must never be treated as "no dependents".
        if (academicStructureClient.hasDependents(id)) {
            throw new HasDependentsException(d.getName() + " still has degree programs or course-catalog entries — remove them first");
        }
        d.setDeletedAt(OffsetDateTime.now());
        d.setDeletedBy(actorId);
        departmentRepository.save(d);
    }

    @Transactional(readOnly = true)
    public List<GradingScaleResponse> listGradingScales() {
        return gradingScaleRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(GradingScaleResponse::from).toList();
    }

    @Transactional
    public GradingScaleResponse createGradingScale(CreateGradingScaleRequest request, UUID createdBy) {
        if (gradingScaleRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new CodeAlreadyInUseException("A grading scale named \"" + request.name() + "\" already exists");
        }
        GradingScale g = new GradingScale();
        g.setName(request.name());
        g.setBands(request.bands());
        g.setStatus(request.status() == null ? ReferenceStatus.ACTIVE : request.status());
        g.setCreatedBy(createdBy);
        g.setUpdatedBy(createdBy);
        return GradingScaleResponse.from(gradingScaleRepository.save(g));
    }

    @Transactional
    public GradingScaleResponse updateGradingScale(UUID id, UpdateGradingScaleRequest request, UUID actorId) {
        GradingScale g = gradingScaleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Grading scale not found"));
        if (gradingScaleRepository.existsByNameAndDeletedAtIsNullAndIdNot(request.name(), id)) {
            throw new CodeAlreadyInUseException("A grading scale named \"" + request.name() + "\" already exists");
        }
        g.setName(request.name());
        g.setBands(request.bands());
        g.setStatus(request.status());
        g.setUpdatedBy(actorId);
        return GradingScaleResponse.from(gradingScaleRepository.save(g));
    }

    @Transactional
    public void deleteGradingScale(UUID id, UUID actorId) {
        GradingScale g = gradingScaleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Grading scale not found"));
        g.setDeletedAt(OffsetDateTime.now());
        g.setDeletedBy(actorId);
        gradingScaleRepository.save(g);
    }

    @Transactional(readOnly = true)
    public List<CourseCategoryResponse> listCourseCategories() {
        return courseCategoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(CourseCategoryResponse::from).toList();
    }

    @Transactional
    public CourseCategoryResponse createCourseCategory(CreateCourseCategoryRequest request, UUID createdBy) {
        if (courseCategoryRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new CodeAlreadyInUseException("A course category named \"" + request.name() + "\" already exists");
        }
        CourseCategory c = new CourseCategory();
        c.setName(request.name());
        c.setDescription(request.description());
        c.setStatus(request.status() == null ? ReferenceStatus.ACTIVE : request.status());
        c.setCreatedBy(createdBy);
        c.setUpdatedBy(createdBy);
        return CourseCategoryResponse.from(courseCategoryRepository.save(c));
    }

    @Transactional
    public CourseCategoryResponse updateCourseCategory(UUID id, UpdateCourseCategoryRequest request, UUID actorId) {
        CourseCategory c = courseCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Course category not found"));
        if (courseCategoryRepository.existsByNameAndDeletedAtIsNullAndIdNot(request.name(), id)) {
            throw new CodeAlreadyInUseException("A course category named \"" + request.name() + "\" already exists");
        }
        c.setName(request.name());
        c.setDescription(request.description());
        c.setStatus(request.status());
        c.setUpdatedBy(actorId);
        return CourseCategoryResponse.from(courseCategoryRepository.save(c));
    }

    @Transactional
    public void deleteCourseCategory(UUID id, UUID actorId) {
        CourseCategory c = courseCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Course category not found"));
        c.setDeletedAt(OffsetDateTime.now());
        c.setDeletedBy(actorId);
        courseCategoryRepository.save(c);
    }
}
