package com.ucms.identity.repository;

import com.ucms.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByStudentId(String studentId);

    @Query("select count(u) from User u where u.deletedAt is null")
    long countActiveRecords();

    @Query("select count(u) from User u where u.deletedAt is null and u.status = com.ucms.identity.domain.UserStatus.ACTIVE")
    long countByStatusActive();

    @Query("select count(u) from User u where u.deletedAt is null and u.role.kind = com.ucms.identity.domain.RoleKind.STUDENT")
    long countStudents();

    @Query("select count(u) from User u where u.deletedAt is null and u.role.kind <> com.ucms.identity.domain.RoleKind.STUDENT")
    long countStaff();
}
