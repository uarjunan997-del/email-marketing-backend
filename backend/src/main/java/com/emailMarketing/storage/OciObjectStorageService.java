package com.emailMarketing.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;

/** Simplified abstraction for OCI Object Storage (stub). Replace with real SDK or spring-cloud-oci as needed. */
@Component
@ConditionalOnProperty(value = "oci.objectstorage.enabled", havingValue = "true")
public class OciObjectStorageService {
  @Value("${oci.objectstorage.enabled:false}") private boolean enabled;
  @Value("${oci.objectstorage.bucket.name:}") private String bucket;
  public boolean isEnabled(){ return enabled; }
  public String putBytes(String key, byte[] data, String contentType){ if(!enabled) throw new IllegalStateException("Disabled"); /* upload logic */ return key; }
  public byte[] getBytes(String key){ if(!enabled) throw new IllegalStateException("Disabled"); return new byte[0]; }
  public String generateReadUrl(String key, int expiryMinutes){ if(!enabled) throw new IllegalStateException("Disabled"); return "https://objectstorage.example/"+bucket+"/"+key+"?exp="+expiryMinutes; }
}
