package com.codeops.vault.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that processes expired dynamic secret leases.
 *
 * <p>Runs every 30 seconds and delegates to
 * {@link DynamicSecretService#processExpiredLeases()}.
 * Only active in non-test profiles.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class LeaseExpiryScheduler {

    private final DynamicSecretService dynamicSecretService;

    /**
     * Checks for and processes expired leases every 30 seconds.
     */
    @Scheduled(fixedDelay = 30_000)
    public void processExpiredLeases() {
        try {
            int count = dynamicSecretService.processExpiredLeases();
            if (count > 0) {
                log.info("Expired {} dynamic lease(s)", count);
            }
        } catch (Exception e) {
            log.error("Error in lease expiry scheduler", e);
        }
    }
}
