package com.ucms.enrollment.repository;

import com.ucms.enrollment.domain.RegistrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface RegistrationLogRepository extends JpaRepository<RegistrationLog, UUID>, JpaSpecificationExecutor<RegistrationLog> {
}
