package com.ucms.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "student_id", unique = true)
    private String studentId;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = true;

    @Column(name = "notify_assignments", nullable = false)
    private boolean notifyAssignments = true;

    @Column(name = "notify_grades", nullable = false)
    private boolean notifyGrades = true;

    @Column(name = "notify_registrations", nullable = false)
    private boolean notifyRegistrations = true;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public boolean isNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(boolean notifyEmail) { this.notifyEmail = notifyEmail; }
    public boolean isNotifyAssignments() { return notifyAssignments; }
    public void setNotifyAssignments(boolean notifyAssignments) { this.notifyAssignments = notifyAssignments; }
    public boolean isNotifyGrades() { return notifyGrades; }
    public void setNotifyGrades(boolean notifyGrades) { this.notifyGrades = notifyGrades; }
    public boolean isNotifyRegistrations() { return notifyRegistrations; }
    public void setNotifyRegistrations(boolean notifyRegistrations) { this.notifyRegistrations = notifyRegistrations; }
}
