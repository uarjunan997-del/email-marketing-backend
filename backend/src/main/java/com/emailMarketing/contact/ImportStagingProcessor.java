package com.emailMarketing.contact;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

@Component
public class ImportStagingProcessor {
    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate messaging;
    public ImportStagingProcessor(JdbcTemplate jdbc, SimpMessagingTemplate messaging){ this.jdbc = jdbc; this.messaging = messaging; }

    // Run every 5 seconds
    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void run() {
        // pick jobs ready for processing
        List<Map<String,Object>> jobs = jdbc.queryForList("SELECT id, user_id, dedupe_strategy FROM import_jobs WHERE status='READY_FOR_PROCESSING' ORDER BY created_at FETCH FIRST 5 ROWS ONLY");
        for(var job: jobs){
            Long jobId = ((Number)job.get("id")).longValue();
            Long userId = ((Number)job.get("user_id")).longValue();
            String dedupe = job.get("dedupe_strategy") == null ? "MERGE" : job.get("dedupe_strategy").toString();
            // mark running
            jdbc.update("UPDATE import_jobs SET status='RUNNING' WHERE id=?", jobId);
            processJobBatch(jobId, userId, dedupe);
            // check remaining
            Integer remaining = jdbc.queryForObject("SELECT COUNT(1) FROM import_staging WHERE job_id=? AND status='PENDING'", Integer.class, jobId);
            if(remaining == null || remaining == 0){ jdbc.update("UPDATE import_jobs SET status='COMPLETED' WHERE id=?", jobId); }
            else { jdbc.update("UPDATE import_jobs SET status='READY_FOR_PROCESSING' WHERE id=?", jobId); }
            // push progress
            Map<String,Object> status = jdbc.queryForMap("SELECT id, processed_rows, failed_rows, total_rows, status FROM import_jobs WHERE id=?", jobId);
            messaging.convertAndSend("/topic/imports/"+userId, status);
        }
    }

    private void processJobBatch(Long jobId, Long userId, String dedupe){
        int batchSize = 200;
    List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, parsed_email, parsed_first_name, parsed_last_name, parsed_custom_fields, parsed_phone, parsed_country, parsed_city, parsed_segment, parsed_unsubscribed FROM import_staging WHERE job_id=? AND status='PENDING' ORDER BY id FETCH FIRST ? ROWS ONLY", jobId, batchSize);
        for(var r: rows){
            Long id = ((Number)r.get("id")).longValue();
            String email = (String) r.get("parsed_email");
            String fn = (String) r.get("parsed_first_name");
            String ln = (String) r.get("parsed_last_name");
            try {
                if(email == null || !isValidEmail(email)){
                    jdbc.update("UPDATE import_staging SET status='FAILED', error_message=? WHERE id=?", "Invalid email", id);
                    jdbc.update("UPDATE import_jobs SET failed_rows = NVL(failed_rows,0) + 1 WHERE id=?", jobId);
                    continue;
                }
                // check suppression
                Integer suppressed = jdbc.queryForObject("SELECT COUNT(1) FROM suppression_list WHERE user_id=? AND LOWER(email)=LOWER(?)", Integer.class, userId, email);
                if(suppressed != null && suppressed > 0){
                    jdbc.update("UPDATE import_staging SET status='FAILED', error_message=? WHERE id=?", "Suppressed email", id);
                    jdbc.update("UPDATE import_jobs SET failed_rows = NVL(failed_rows,0) + 1 WHERE id=?", jobId);
                    continue;
                }
                // perform dedupe/upsert
                String custom = (String) r.get("parsed_custom_fields");
                String phone = (String) r.get("parsed_phone");
                String country = (String) r.get("parsed_country");
                String city = (String) r.get("parsed_city");
                String segment = (String) r.get("parsed_segment");
                Number unsubscribed = (Number) r.get("parsed_unsubscribed");
                if("MERGE".equalsIgnoreCase(dedupe) || dedupe == null){
                    String mergeSql = "MERGE INTO contacts t USING (SELECT ? AS user_id, ? AS email, ? AS first_name, ? AS last_name, ? AS custom_fields, ? AS phone, ? AS country, ? AS city, ? AS segment, ? AS unsubscribed FROM DUAL) s ON (t.user_id = s.user_id AND LOWER(t.email) = LOWER(s.email)) WHEN MATCHED THEN UPDATE SET t.first_name = COALESCE(s.first_name, t.first_name), t.last_name = COALESCE(s.last_name, t.last_name), t.custom_fields = COALESCE(s.custom_fields, t.custom_fields), t.phone = COALESCE(s.phone, t.phone), t.country = COALESCE(s.country, t.country), t.city = COALESCE(s.city, t.city), t.segment = COALESCE(s.segment, t.segment), t.unsubscribed = COALESCE(s.unsubscribed, t.unsubscribed), t.updated_at = SYSTIMESTAMP WHEN NOT MATCHED THEN INSERT (id, user_id, email, first_name, last_name, custom_fields, phone, country, city, segment, unsubscribed, created_at) VALUES (NULL, s.user_id, s.email, s.first_name, s.last_name, s.custom_fields, s.phone, s.country, s.city, s.segment, NVL(s.unsubscribed,0), SYSTIMESTAMP)";
                    jdbc.update(mergeSql, userId, email, fn, ln, custom, phone, country, city, segment, unsubscribed);
                } else if("SKIP".equalsIgnoreCase(dedupe)){
                    Integer exists = jdbc.queryForObject("SELECT COUNT(1) FROM contacts WHERE user_id=? AND LOWER(email)=LOWER(?)", Integer.class, userId, email);
                    if(exists == null || exists == 0){ jdbc.update("INSERT INTO contacts (user_id, email, first_name, last_name, custom_fields, phone, country, city, segment, unsubscribed, created_at) VALUES (?,?,?,?,?,?,?,?,?,NVL(?,0),SYSTIMESTAMP)", userId, email, fn, ln, custom, phone, country, city, segment, unsubscribed); }
                } else if("OVERWRITE".equalsIgnoreCase(dedupe)){
                    Integer exists = jdbc.queryForObject("SELECT COUNT(1) FROM contacts WHERE user_id=? AND LOWER(email)=LOWER(?)", Integer.class, userId, email);
                    if(exists == null || exists == 0){ jdbc.update("INSERT INTO contacts (user_id, email, first_name, last_name, custom_fields, phone, country, city, segment, unsubscribed, created_at) VALUES (?,?,?,?,?,?,?,?,?,NVL(?,0),SYSTIMESTAMP)", userId, email, fn, ln, custom, phone, country, city, segment, unsubscribed); }
                    else { jdbc.update("UPDATE contacts SET first_name=?, last_name=?, custom_fields=COALESCE(?, custom_fields), phone=COALESCE(?, phone), country=COALESCE(?, country), city=COALESCE(?, city), segment=COALESCE(?, segment), unsubscribed=COALESCE(?, unsubscribed), updated_at=SYSTIMESTAMP WHERE user_id=? AND LOWER(email)=LOWER(?)", fn, ln, custom, phone, country, city, segment, unsubscribed, userId, email); }
                }
                // mark staging row completed
                jdbc.update("UPDATE import_staging SET status='COMPLETED' WHERE id=?", id);
                jdbc.update("UPDATE import_jobs SET processed_rows = NVL(processed_rows,0) + 1 WHERE id=?", jobId);
            } catch(Exception ex){
                jdbc.update("UPDATE import_staging SET status='FAILED', error_message=? WHERE id=?", ex.getMessage(), id);
                jdbc.update("UPDATE import_jobs SET failed_rows = NVL(failed_rows,0) + 1 WHERE id=?", jobId);
            }
        }
    }

    private boolean isValidEmail(String email){
        if(email == null) return false;
        return email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
