package com.ucms.enrollment.service;

import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.domain.CourseEnrollment;
import com.ucms.enrollment.domain.CourseEnrollmentStatus;
import com.ucms.enrollment.domain.ProgramEnrollmentStatus;
import com.ucms.enrollment.domain.RegistrationLog;
import com.ucms.enrollment.domain.StudentProgramEnrollment;
import com.ucms.enrollment.domain.TargetType;
import com.ucms.enrollment.repository.CourseEnrollmentRepository;
import com.ucms.enrollment.repository.RegistrationLogRepository;
import com.ucms.enrollment.repository.StudentProgramEnrollmentRepository;
import com.ucms.enrollment.security.Caller;
import com.ucms.enrollment.web.dto.Dtos.MonthCount;
import com.ucms.enrollment.web.dto.Dtos.OfferingCount;
import com.ucms.enrollment.web.dto.Dtos.ProgramCount;
import com.ucms.enrollment.web.dto.Dtos.RegistrationResponse;
import com.ucms.enrollment.web.dto.Dtos.StatsResponse;
import com.ucms.enrollment.web.dto.Dtos.StudentOption;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RegistrationQueryService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final RegistrationLogRepository logRepository;
    private final StudentProgramEnrollmentRepository programRepository;
    private final CourseEnrollmentRepository courseRepository;
    private final AcademicClient academic;
    private final IdentityClient identity;

    public RegistrationQueryService(RegistrationLogRepository logRepository, StudentProgramEnrollmentRepository programRepository,
                                    CourseEnrollmentRepository courseRepository, AcademicClient academic, IdentityClient identity) {
        this.logRepository = logRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
        this.academic = academic;
        this.identity = identity;
    }

    /** Recent Registrations: an admin sees everything; a lecturer sees what they did. */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> recent(int limit, UUID studentId, Caller caller) {
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        List<RegistrationLog> rows;
        if (studentId != null) rows = logRepository.findByStudentIdOrderByCreatedAtDesc(studentId, page);
        else if (caller.isAdmin()) rows = logRepository.findAllByOrderByCreatedAtDesc(page);
        else rows = logRepository.findByActorIdOrderByCreatedAtDesc(caller.id(), page);
        if (rows.isEmpty()) return List.of();

        Set<UUID> people = new HashSet<>();
        rows.forEach(r -> { people.add(r.getStudentId()); people.add(r.getActorId()); });
        Map<UUID, IdentityClient.User> users = identity.lookup(people);

        Map<UUID, String> programLabels = academic.listPrograms().stream()
                .collect(Collectors.toMap(AcademicClient.Program::id, p -> p.name() + " (Program)", (a, b) -> a));
        List<UUID> offeringIds = rows.stream().filter(r -> r.getTargetType() == TargetType.COURSE)
                .map(RegistrationLog::getTargetId).distinct().toList();
        Map<UUID, String> offeringLabels = offeringIds.isEmpty() ? Map.of()
                : academic.searchOfferings(null, null, null, offeringIds).stream()
                .collect(Collectors.toMap(AcademicClient.Offering::id, o -> o.code() + " — " + o.name(), (a, b) -> a));

        return rows.stream().map(r -> {
            IdentityClient.User s = users.get(r.getStudentId());
            IdentityClient.User a = users.get(r.getActorId());
            String label = r.getTargetType() == TargetType.PROGRAM ? programLabels.get(r.getTargetId()) : offeringLabels.get(r.getTargetId());
            return new RegistrationResponse(r.getId(), r.getStudentId(), s == null ? null : s.name(),
                    s == null ? null : s.studentId(), r.getTargetType().name(), r.getTargetId(), label,
                    r.getActorId(), a == null ? "System" : a.name(), r.getActorRole(), r.getAction(), r.getCreatedAt());
        }).toList();
    }

    /**
     * Student picker for the Registrations screen. Identity & Access keeps its user list
     * admin-only; lecturers also register students into their own courses (wireframe),
     * so this exposes just active students' names/IDs, plus their active program so the
     * screen can group that program's curriculum first.
     */
    @Transactional(readOnly = true)
    public List<StudentOption> searchStudents(String q) {
        List<IdentityClient.User> students = identity.searchStudents(q);
        if (students.isEmpty()) return List.of();
        Map<UUID, StudentProgramEnrollment> programs = programRepository
                .findByStudentIdInAndStatusAndDeletedAtIsNull(students.stream().map(IdentityClient.User::id).toList(),
                        ProgramEnrollmentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(StudentProgramEnrollment::getStudentId, p -> p, (a, b) -> a));
        Map<UUID, String> programNames = programs.isEmpty() ? Map.of() : academic.listPrograms().stream()
                .collect(Collectors.toMap(AcademicClient.Program::id, AcademicClient.Program::name, (a, b) -> a));
        return students.stream().map(s -> {
            StudentProgramEnrollment p = programs.get(s.id());
            return new StudentOption(s.id(), s.name(), s.studentId(), s.email(),
                    p == null ? null : p.getDegreeProgramId(), p == null ? null : programNames.get(p.getDegreeProgramId()));
        }).toList();
    }

    /** Aggregates for Analytics & Reporting. */
    @Transactional(readOnly = true)
    public StatsResponse stats() {
        List<StudentProgramEnrollment> active = programRepository.findByStatusAndDeletedAtIsNull(ProgramEnrollmentStatus.ACTIVE);
        List<CourseEnrollment> live = courseRepository.findByDeletedAtIsNull();

        Map<UUID, Long> perProgram = active.stream()
                .collect(Collectors.groupingBy(StudentProgramEnrollment::getDegreeProgramId, Collectors.counting()));
        Map<UUID, String> programNames = perProgram.isEmpty() ? Map.of() : academic.listPrograms().stream()
                .collect(Collectors.toMap(AcademicClient.Program::id, AcademicClient.Program::name, (a, b) -> a));
        List<ProgramCount> byProgram = perProgram.entrySet().stream()
                .map(e -> new ProgramCount(e.getKey(), programNames.get(e.getKey()), e.getValue()))
                .sorted((a, b) -> Long.compare(b.activeStudents(), a.activeStudents()))
                .toList();

        Map<UUID, long[]> perOffering = new TreeMap<>();
        for (CourseEnrollment e : live) {
            long[] c = perOffering.computeIfAbsent(e.getCourseOfferingId(), k -> new long[2]);
            if (e.getStatus() == CourseEnrollmentStatus.ENROLLED) c[0]++;
            else if (e.getStatus() == CourseEnrollmentStatus.WAITLISTED) c[1]++;
        }
        List<OfferingCount> byOffering = perOffering.entrySet().stream()
                .map(e -> new OfferingCount(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();

        Map<String, Long> monthly = new TreeMap<>(courseRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(e -> e.getCreatedAt()).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(t -> t.format(MONTH), Collectors.counting())));
        List<MonthCount> byMonth = monthly.entrySet().stream().map(e -> new MonthCount(e.getKey(), e.getValue())).toList();

        long enrolled = live.stream().filter(e -> e.getStatus() == CourseEnrollmentStatus.ENROLLED).count();
        long waitlisted = live.stream().filter(e -> e.getStatus() == CourseEnrollmentStatus.WAITLISTED).count();
        return new StatsResponse(active.size(), enrolled, waitlisted, byProgram, byOffering, byMonth);
    }
}
