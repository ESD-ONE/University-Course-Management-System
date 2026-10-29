package com.ucms.identity.keycloak;

import com.ucms.identity.domain.User;
import com.ucms.identity.repository.UserRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Links every account that has no Keycloak user yet. On the first start after this
 * change that migrates all existing accounts (passwords included); afterwards it finds
 * nothing to do unless Keycloak was unreachable when an account was created. Runs shortly
 * after startup and then every minute, so it recovers by itself if Keycloak starts late.
 */
@Configuration
@EnableScheduling
public class KeycloakReconciler {

    private static final Logger log = LoggerFactory.getLogger(KeycloakReconciler.class);

    private final UserRepository userRepository;
    private final KeycloakUserSync sync;
    private final TransactionTemplate transactions;

    public KeycloakReconciler(UserRepository userRepository, KeycloakUserSync sync, TransactionTemplate transactions) {
        this.userRepository = userRepository;
        this.sync = sync;
        this.transactions = transactions;
    }

    @Scheduled(initialDelayString = "${ucms.keycloak.reconcile-initial-delay-ms:5000}", fixedDelay = 60_000)
    @SchedulerLock(name = "keycloak-reconcile", lockAtLeastFor = "PT30S")
    public void linkUnlinkedAccounts() {
        List<User> pending = userRepository.findByKeycloakIdIsNullAndDeletedAtIsNull();
        if (pending.isEmpty()) return;
        log.info("Linking {} account(s) to Keycloak", pending.size());
        int linked = 0;
        for (User user : pending) {
            try {
                transactions.executeWithoutResult(status -> {
                    User fresh = userRepository.findById(user.getId()).orElseThrow();
                    sync.link(fresh);
                    userRepository.save(fresh);
                });
                linked++;
            } catch (Exception ex) {
                log.warn("Could not link {} to Keycloak yet: {}", user.getEmail(), ex.getMessage());
            }
        }
        log.info("Linked {}/{} account(s) to Keycloak", linked, pending.size());
    }
}
