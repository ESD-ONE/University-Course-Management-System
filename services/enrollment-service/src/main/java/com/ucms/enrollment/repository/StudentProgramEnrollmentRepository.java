package com.ucms.enrollment.repository;

import com.ucms.enrollment.domain.ProgramEnrollmentStatus;
import com.ucms.enrollment.domain.StudentProgramEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentProgramEnrollmentRepository extends JpaRepository<StudentProgramEnrollment, UUID> {
    Optional<StudentProgramEnrollment> findByIdAndDeletedAtIsNull(UUID id);
    List<StudentProgramEnrollment> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<StudentProgramEnrollment> findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID studentId);
    List<StudentProgramEnrollment> findByDegreeProgramIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID degreeProgramId);
    Optional<StudentProgramEnrollment> findFirstByStudentIdAndStatusAndDeletedAtIsNull(UUID studentId, ProgramEnrollmentStatus status);
    List<StudentProgramEnrollment> findByStudentIdInAndStatusAndDeletedAtIsNull(Collection<UUID> studentIds, ProgramEnrollmentStatus status);
    List<StudentProgramEnrollment> findByStatusAndDeletedAtIsNull(ProgramEnrollmentStatus status);
}
