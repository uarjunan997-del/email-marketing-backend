package com.emailMarketing.storage;

import com.oracle.cloud.spring.storage.Storage;
import com.oracle.cloud.spring.storage.OracleStorageResource;
import com.oracle.cloud.spring.storage.StorageObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * OCI Object Storage wrapper now leveraging Spring Cloud Oracle Storage starter.
 * Relies on auto-configured {@link Storage} bean for bucket/object operations.
 */
@Component
@ConditionalOnProperty(value = "oci.objectstorage.enabled", havingValue = "true")
public class OciObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(OciObjectStorageService.class);

    private final Storage storage;

    @Value("${oci.objectstorage.enabled:false}")
    private boolean enabled;
    @Value("${oci.objectstorage.bucket.name:}")
    private String bucketName; // logical bucket name
    @Value("${oci.region:ap-hyderabad-1}")
    private String region;

    public OciObjectStorageService(Storage storage) {
        this.storage = storage;
    }

    public boolean isEnabled() { return enabled; }

    /** Internal helper accessors for maintenance jobs (e.g., PAR cleanup). */
    public com.oracle.bmc.objectstorage.ObjectStorage getStorageClient() { return storage.getClient(); }
    public String getNamespace() { return storage.getNamespaceName(); }

    public String putBytes(String key, byte[] data, String contentType) {
        if (!enabled) throw new IllegalStateException("OCI Object Storage disabled");
        try (var is = new ByteArrayInputStream(data)) {
            storage.upload(bucketName, key, is,
                    StorageObjectMetadata.builder()
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .contentLength((long) data.length)
                            .build());
            log.debug("Uploaded object to bucket={} key={} length={}", bucketName, key, data.length);
            return key;
        } catch (Exception e) {
            throw new RuntimeException("OCI putObject failed", e);
        }
    }

    public byte[] getBytes(String key) {
        if (!enabled) throw new IllegalStateException("OCI Object Storage disabled");
        try {
            OracleStorageResource resource = storage.download(bucketName, key);
            try (var in = resource.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                log.debug("Downloaded object bucket={} key={} length={}", bucketName, key, bytes.length);
                return bytes;
            }
        } catch (IOException e) {
            throw new RuntimeException("OCI getObject IO failed", e);
        } catch (Exception e) {
            throw new RuntimeException("OCI getObject failed", e);
        }
    }

    /**
     * Generate a time-limited Pre-Authenticated Request (PAR) URL (default 5 minutes if <=0).
     */
    public String generateReadUrl(String key, int expiryMinutes) {
        if (!enabled) throw new IllegalStateException("OCI Object Storage disabled");
        int mins = expiryMinutes <= 0 ? 5 : expiryMinutes;
        try {
            var client = storage.getClient();
            String namespace = storage.getNamespaceName();
            CreatePreauthenticatedRequestDetails details = CreatePreauthenticatedRequestDetails.builder()
                    .name("par-" + System.currentTimeMillis())
                    .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
                    .objectName(key)
                    .timeExpires(Date.from(Instant.now().plus(Duration.ofMinutes(mins))))
                    .build();
            var resp = client.createPreauthenticatedRequest(CreatePreauthenticatedRequestRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .createPreauthenticatedRequestDetails(details)
                    .build());
            PreauthenticatedRequest par = resp.getPreauthenticatedRequest();
            String accessUri = par.getAccessUri();
            log.debug("Generated PAR for key={} expiresInMins={} uri={}", key, mins, accessUri);
            return String.format("https://objectstorage.%s.oraclecloud.com%s", region, accessUri);
        } catch (Exception e) {
            throw new RuntimeException("OCI generate pre-auth URL failed", e);
        }
    }

    /** Delete a single object by key from the configured bucket. */
    public void deleteObject(String key) {
        if (!enabled) throw new IllegalStateException("OCI Object Storage disabled");
        try {
            var client = storage.getClient();
            String namespace = storage.getNamespaceName();
            client.deleteObject(DeleteObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(key)
                    .build());
            log.debug("Deleted object bucket={} key={}", bucketName, key);
        } catch (Exception e) {
            throw new RuntimeException("OCI deleteObject failed for key=" + key, e);
        }
    }
}
