package com.ucms.notification.service;

import com.ucms.notification.client.IdentityClient;
import com.ucms.notification.domain.NotificationPreference;
import com.ucms.notification.repository.NotificationPreferenceRepository;
import com.ucms.notification.security.Caller;
import com.ucms.notification.web.dto.Dtos.PreferencesRequest;
import com.ucms.notification.web.dto.Dtos.PreferencesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * This service owns notification preferences (tables-05). Before it existed, the Profile
 * screen saved them as notify_* columns on identity-access-service's users table; the
 * first time a user opens their preferences here, those values are copied over (using
 * the user's own token) so nobody's earlier choices are lost. From then on this table is
 * the source of truth — identity-access-service's columns are a legacy seed only.
 */
@Service
public class PreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceService.class);

    private final NotificationPreferenceRepository repository;
    private final IdentityClient identity;

    public PreferenceService(NotificationPreferenceRepository repository, IdentityClient identity) {
        this.repository = repository;
        this.identity = identity;
    }

    @Transactional
    public PreferencesResponse mine(Caller caller) {
        return PreferencesResponse.from(repository.findByUserId(caller.id()).orElseGet(() -> seed(caller)));
    }

    @Transactional
    public PreferencesResponse update(PreferencesRequest request, Caller caller) {
        NotificationPreference p = repository.findByUserId(caller.id()).orElseGet(() -> seed(caller));
        p.setEmailEnabled(request.emailEnabled());
        p.setAssignmentAlerts(request.assignmentAlerts());
        p.setGradeAlerts(request.gradeAlerts());
        p.setRegistrationAlerts(request.registrationAlerts());
        p.setUpdatedBy(caller.id());
        return PreferencesResponse.from(repository.save(p));
    }

    /** For event delivery: the stored row, or all-on defaults (not persisted, so the
     *  user's legacy values still get seeded the first time they open the screen). */
    @Transactional(readOnly = true)
    public NotificationPreference forUser(UUID userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            NotificationPreference defaults = new NotificationPreference();
            defaults.setUserId(userId);
            return defaults;
        });
    }

    private NotificationPreference seed(Caller caller) {
        NotificationPreference p = new NotificationPreference();
        p.setUserId(caller.id());
        try {
            IdentityClient.Profile legacy = identity.profile(caller.token());
            if (legacy != null) {
                p.setEmailEnabled(legacy.notifyEmail());
                p.setAssignmentAlerts(legacy.notifyAssignments());
                p.setGradeAlerts(legacy.notifyGrades());
                p.setRegistrationAlerts(legacy.notifyRegistrations());
            }
        } catch (Exception ex) {
            log.warn("Couldn't read legacy preferences for {}, using defaults: {}", caller.id(), ex.getMessage());
        }
        p.setUpdatedBy(caller.id());
        return repository.save(p);
    }
}
