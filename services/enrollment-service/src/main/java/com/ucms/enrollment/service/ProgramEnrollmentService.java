package com.ucms.enrollment.service;

import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.domain.ProgramEnrollmentStatus;
import com.ucms.enrollment.domain.RegistrationLog;
import com.ucms.enrollment.domain.StudentProgramEnrollment;
import com.ucms.enrollment.domain.TargetType;
import com.ucms.enrollment.exception.ConflictException;
import com.ucms.enrollment.messaging.EventPublisher;
import com.ucms.enrollment.repository.RegistrationLogRepository;
import com.ucms.enrollment.repository.StudentProgramEnrollmentRepository;
import com.ucms.enrollment.security.Caller;
import com.ucms.enrollment.web.dto.Dtos.CreateProgramEnrollmentRequest;
import com.ucms.enrollment.web.dto.Dtos.ProgramEnrollmentResponse;
import com.ucms.enrollment.web.dto.Dtos.UpdateProgramEnrollmentRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** flow-03-admin-registration.mmd: validate the program with Academic Structure, insert
 *  the enrollment and a registration-log row, publish enrollment.program-registered. */
@Service
public class ProgramEnrollmentService {

    private final StudentProgramEnrollmentRepository repository;
    private final RegistrationLogRepository logRepository;
    private final AcademicClient academic;
    private final IdentityClient identity;
    private final EventPublisher events;

    public ProgramEnrollmentService(StudentProgramEnrollmentRepository repository, RegistrationLogRepository logRepository,
                                    AcademicClient academic, IdentityClient identity, EventPublisher events) {
        this.repository = repository;
        this.logRepository = logRepository;
        this.academic = academic;
        this.identity = identity;
        this.events = events;
    }

    @Transactional
    public ProgramEnrollmentResponse register(CreateProgramEnrollmentRequest request, Caller caller) {
        IdentityClient.User student = requireActiveStudent(request.studentId());
        AcademicClient.Program program = academic.getProgram(request.degreeProgramId());
        if (!"ACTIVE".equals(program.status())) {
            throw new ConflictException("PROGRAM_INACTIVE", program.name() + " isn't accepting registrations (inactive).");
        }
        if (request.currentYearOfStudy() > program.durationYears()) {
            throw new IllegalArgumentException("Year of study can't exceed the program's " + program.durationYears() + "-year duration.");
        }
        AcademicClient.AcademicYear intake = academic.listAcademicYears().stream()
                .filter(y -> y.id().equals(request.intakeAcademicYearId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Intake academic year not found"));
        if ("ARCHIVED".equals(intake.status())) {
            throw new ConflictException("YEAR_ARCHIVED", "Can't register into an archived intake year.");
        }
        repository.findFirstByStudentIdAndStatusAndDeletedAtIsNull(student.id(), ProgramEnrollmentStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new ConflictException("ALREADY_REGISTERED", student.name()
                            + " already has an active program registration — withdraw it first to transfer.");
                });

        StudentProgramEnrollment e = new StudentProgramEnrollment();
        e.setStudentId(student.id());
        e.setDegreeProgramId(program.id());
        e.setIntakeAcademicYearId(intake.id());
        e.setCurrentYearOfStudy(request.currentYearOfStudy());
        e.setStatus(ProgramEnrollmentStatus.ACTIVE);
        e.setCreatedBy(caller.id());
        e.setUpdatedBy(caller.id());
        e = repository.save(e);

        logRepository.save(new RegistrationLog(student.id(), TargetType.PROGRAM, program.id(), caller.id(),
                caller.role(), "PROGRAM_REGISTER"));
        events.publish("enrollment.program-registered", Map.of(
                "programEnrollmentId", e.getId().toString(),
                "studentId", student.id().toString(),
                "degreeProgramId", program.id().toString(),
                "programCode", program.code(),
                "programName", program.name(),
                "actorId", caller.id().toString()));

        return toResponse(e, student, program, intake.name());
    }

    @Transactional
    public ProgramEnrollmentResponse update(UUID id, UpdateProgramEnrollmentRequest request, Caller caller) {
        StudentProgramEnrollment e = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Program enrollment not found"));
        if (request.status() == ProgramEnrollmentStatus.ACTIVE && e.getStatus() != ProgramEnrollmentStatus.ACTIVE) {
            repository.findFirstByStudentIdAndStatusAndDeletedAtIsNull(e.getStudentId(), ProgramEnrollmentStatus.ACTIVE)
                    .ifPresent(other -> {
                        throw new ConflictException("ALREADY_REGISTERED", "This student already has another active program registration.");
                    });
        }
        if (request.currentYearOfStudy() != null) e.setCurrentYearOfStudy(request.currentYearOfStudy());
        if (request.status() != null) e.setStatus(request.status());
        e.setUpdatedBy(caller.id());
        e = repository.save(e);
        logRepository.save(new RegistrationLog(e.getStudentId(), TargetType.PROGRAM, e.getDegreeProgramId(), caller.id(),
                caller.role(), "PROGRAM_UPDATE"));
        return enrich(List.of(e)).get(0);
    }

    @Transactional(readOnly = true)
    public List<ProgramEnrollmentResponse> list(UUID studentId, UUID degreeProgramId) {
        List<StudentProgramEnrollment> rows;
        if (studentId != null) rows = repository.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(studentId);
        else if (degreeProgramId != null) rows = repository.findByDegreeProgramIdAndDeletedAtIsNullOrderByCreatedAtDesc(degreeProgramId);
        else rows = repository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        return enrich(rows);
    }

    @Transactional(readOnly = true)
    public List<ProgramEnrollmentResponse> mine(Caller caller) {
        return enrich(repository.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(caller.id()));
    }

    private IdentityClient.User requireActiveStudent(UUID studentId) {
        IdentityClient.User student = identity.getUser(studentId);
        if (!"STUDENT".equals(student.role())) {
            throw new IllegalArgumentException(student.name() + " isn't a student account.");
        }
        if (!"ACTIVE".equals(student.status())) {
            throw new ConflictException("STUDENT_NOT_ACTIVE", student.name() + "'s account isn't active.");
        }
        return student;
    }

    private List<ProgramEnrollmentResponse> enrich(List<StudentProgramEnrollment> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, IdentityClient.User> students = identity.lookup(rows.stream().map(StudentProgramEnrollment::getStudentId).toList());
        Map<UUID, AcademicClient.Program> programs = academic.listPrograms().stream()
                .collect(Collectors.toMap(AcademicClient.Program::id, Function.identity(), (a, b) -> a));
        Map<UUID, String> years = new HashMap<>();
        academic.listAcademicYears().forEach(y -> years.put(y.id(), y.name()));
        return rows.stream()
                .map(e -> toResponse(e, students.get(e.getStudentId()), programs.get(e.getDegreeProgramId()),
                        years.get(e.getIntakeAcademicYearId())))
                .toList();
    }

    private static ProgramEnrollmentResponse toResponse(StudentProgramEnrollment e, IdentityClient.User student,
                                                        AcademicClient.Program program, String intakeName) {
        return new ProgramEnrollmentResponse(e.getId(), e.getStudentId(),
                student == null ? null : student.name(), student == null ? null : student.studentId(),
                e.getDegreeProgramId(), program == null ? null : program.code(), program == null ? null : program.name(),
                program == null ? null : program.durationYears(), program == null ? null : program.totalCredits(),
                e.getIntakeAcademicYearId(), intakeName, e.getCurrentYearOfStudy(), e.getStatus().name(), e.getCreatedAt());
    }
}
