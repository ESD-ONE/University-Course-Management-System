package com.ucms.enrollment.repository;

import com.ucms.enrollment.domain.RegistrationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrationLogRepository extends JpaRepository<RegistrationLog, UUID> {
    List<RegistrationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<RegistrationLog> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);
    List<RegistrationLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);
}
