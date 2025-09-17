package com.emailMarketing.storage;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequestSummary;
import com.oracle.bmc.objectstorage.requests.DeletePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.ListPreauthenticatedRequestsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodically deletes expired OCI Object Storage Pre-Authenticated Requests (PARs) created by this service.
 * PARs are generated with name prefix "par-" in {@link OciObjectStorageService}. They accumulate in the bucket
 * unless explicitly deleted. This job lists PARs for the configured bucket and removes those whose expiration
 * time is in the past (plus a small grace period) or which are older than a max age fallback.
 */
@Component
@ConditionalOnProperty(value = "oci.objectstorage.par.cleanup.enabled", havingValue = "true")
public class ParCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(ParCleanupJob.class);

    private final OciObjectStorageService ociService;

    @Value("${oci.objectstorage.bucket.name:}")
    private String bucketName;
    @Value("${oci.objectstorage.par.cleanup.max-deletes-per-run:100}")
    private int maxDeletesPerRun;
    @Value("${oci.objectstorage.par.cleanup.grace-seconds:60}")
    private long graceSeconds; // extra seconds past expiration before delete
    @Value("${oci.objectstorage.par.cleanup.fallback-max-age-minutes:1440}")
    private long fallbackMaxAgeMinutes; // delete even if not expired (defensive) after this age

    public ParCleanupJob(OciObjectStorageService ociService) {
        this.ociService = ociService;
    }

    // Default: run every 30 minutes; override via property 'oci.objectstorage.par.cleanup.cron'
    @Scheduled(cron = "${oci.objectstorage.par.cleanup.cron:0 0/30 * * * *}")
    public void cleanupExpiredPars() {
        if (!ociService.isEnabled()) return;
        try {
            ObjectStorage client = ociService.getStorageClient();
            String namespace = ociService.getNamespace();
            if (bucketName == null || bucketName.isBlank()) {
                log.warn("PAR cleanup skipped: bucket name not configured");
                return;
            }
            Instant now = Instant.now();
            List<PreauthenticatedRequestSummary> toDelete = new ArrayList<>();
            String page = null;
            do {
                var req = ListPreauthenticatedRequestsRequest.builder()
                        .namespaceName(namespace)
                        .bucketName(bucketName)
                        .page(page)
                        .build();
                var resp = client.listPreauthenticatedRequests(req);
                for (var summary : resp.getItems()) {
                    if (toDelete.size() >= maxDeletesPerRun) break;
                    String name = summary.getName();
                    // Only manage those we created (prefix par-)
                    if (name == null || !name.startsWith("par-")) continue;
                    Instant expires = summary.getTimeExpires().toInstant();
                    Instant created = summary.getTimeCreated().toInstant();
                    boolean expired = expires.isBefore(now.minusSeconds(graceSeconds));
                    boolean tooOld = created.plusSeconds(fallbackMaxAgeMinutes * 60).isBefore(now);
                    if (expired || tooOld) {
                        toDelete.add(summary);
                    }
                }
                page = (toDelete.size() >= maxDeletesPerRun) ? null : resp.getOpcNextPage();
            } while (page != null);

            int deleted = 0;
            for (var summary : toDelete) {
                try {
                    client.deletePreauthenticatedRequest(DeletePreauthenticatedRequestRequest.builder()
                            .namespaceName(ociService.getNamespace())
                            .bucketName(bucketName)
                            .parId(summary.getId())
                            .build());
                    deleted++;
                } catch (Exception ex) {
                    log.warn("Failed deleting PAR id={} name={}: {}", summary.getId(), summary.getName(), ex.getMessage());
                }
            }
            if (deleted > 0) {
                log.info("PAR cleanup removed {} expired entries (bucket={})", deleted, bucketName);
            }
        } catch (Exception e) {
            log.error("PAR cleanup failed: {}", e.getMessage());
        }
    }
}