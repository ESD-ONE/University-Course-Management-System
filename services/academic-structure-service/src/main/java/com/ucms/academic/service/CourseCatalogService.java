package com.ucms.academic.service;

import com.ucms.academic.domain.CourseCatalog;
import com.ucms.academic.exception.DuplicateEntityException;
import com.ucms.academic.repository.CourseCatalogRepository;
import com.ucms.academic.web.dto.CourseCatalogResponse;
import com.ucms.academic.web.dto.CreateCourseCatalogRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CourseCatalogService {

    private final CourseCatalogRepository courseCatalogRepository;

    public CourseCatalogService(CourseCatalogRepository courseCatalogRepository) {
        this.courseCatalogRepository = courseCatalogRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseCatalogResponse> list() {
        return courseCatalogRepository.findByDeletedAtIsNullOrderByCodeAsc().stream()
                .map(CourseCatalogResponse::from)
                .toList();
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
}
