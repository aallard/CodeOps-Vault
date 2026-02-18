package com.codeops.vault.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that processes due secret rotations.
 *
 * <p>Runs every 60 seconds and delegates to {@link RotationService#processDueRotations()}.
 * Only active in non-test profiles.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class RotationScheduler {

    private final RotationService rotationService;

    /**
     * Checks for and executes due rotations every 60 seconds.
     */
    @Scheduled(fixedDelay = 60_000)
    public void checkAndRotate() {
        try {
            int count = rotationService.processDueRotations();
            if (count > 0) {
                log.info("Processed {} due rotation(s)", count);
            }
        } catch (Exception e) {
            log.error("Error in rotation scheduler", e);
        }
    }
}
