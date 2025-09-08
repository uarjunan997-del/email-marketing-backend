package com.emailMarketing.contact;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.sql.Types;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.jdbc.core.SqlParameterValue;

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
                // sanitize lengths to fit DB column definitions
                email = sanitize(email, 320);
                fn = sanitize(fn, 200);
                ln = sanitize(ln, 200);
                phone = sanitize(phone, 50);
                country = sanitize(country, 100);
                city = sanitize(city, 150);
                segment = sanitize(segment, 200);
                Number unsubscribed = (Number) r.get("parsed_unsubscribed");
                Integer unsubInt = unsubscribed == null ? null : Integer.valueOf(unsubscribed.intValue());
                DefaultLobHandler lob = new DefaultLobHandler();
                SqlLobValue customClob = custom == null ? null : new SqlLobValue(custom, lob);
                if("MERGE".equalsIgnoreCase(dedupe) || dedupe == null){
                    String mergeSql =
                        "MERGE INTO contacts t " +
                        "USING (SELECT ? AS user_id, ? AS email, ? AS first_name, ? AS last_name, ? AS custom_fields, ? AS phone, ? AS country, ? AS city, ? AS segment, ? AS unsubscribed FROM DUAL) s " +
                        "ON (t.user_id = s.user_id AND LOWER(t.email) = LOWER(s.email)) " +
                        "WHEN MATCHED THEN UPDATE SET " +
                        " t.first_name = COALESCE(s.first_name, t.first_name), " +
                        " t.last_name = COALESCE(s.last_name, t.last_name), " +
                        " t.custom_fields = CASE WHEN s.custom_fields IS NOT NULL THEN s.custom_fields ELSE t.custom_fields END, " +
                        " t.phone = COALESCE(s.phone, t.phone), " +
                        " t.country = COALESCE(s.country, t.country), " +
                        " t.city = COALESCE(s.city, t.city), " +
                        " t.segment = COALESCE(s.segment, t.segment), " +
                        " t.unsubscribed = COALESCE(s.unsubscribed, t.unsubscribed), " +
                        " t.updated_at = SYSTIMESTAMP " +
                        "WHEN NOT MATCHED THEN INSERT (user_id, email, first_name, last_name, custom_fields, phone, country, city, segment, unsubscribed, created_at) " +
                        " VALUES (s.user_id, s.email, s.first_name, s.last_name, s.custom_fields, s.phone, s.country, s.city, s.segment, NVL(s.unsubscribed,0), SYSTIMESTAMP)";
                    Object[] args = new Object[]{
                        userId, email, fn, ln,
                        customClob != null ? customClob : new SqlParameterValue(Types.CLOB, null),
                        phone, country, city, segment,
                        new SqlParameterValue(Types.INTEGER, unsubInt)
                    };
                    int[] types = new int[]{
                        Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.CLOB,
                        Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER
                    };
                    jdbc.update(mergeSql, args, types);
                } else if("SKIP".equalsIgnoreCase(dedupe)){
                    Integer exists = jdbc.queryForObject("SELECT COUNT(1) FROM contacts WHERE user_id=? AND LOWER(email)=LOWER(?)", Integer.class, userId, email);
                    if(exists == null || exists == 0){
                        String sql = "INSERT INTO contacts (user_id, email, first_name, last_name, custom_fields, phone, country, city, segment, unsubscribed, created_at) VALUES (?,?,?,?,?,?,?,?,?,NVL(?,0), SYSTIMESTAMP)";
                        Object[] args = new Object[]{
                            userId, email, fn, ln,
                            customClob != null ? customClob : new SqlParameterValue(Types.CLOB, null),
                            phone, country, city, segment,
                            new SqlParameterValue(Types.INTEGER, unsubInt)
                        };
                        int[] types = new int[]{
                            Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.CLOB,
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER
                        };
                        jdbc.update(sql, args, types);
                    }
                } else if("OVERWRITE".equalsIgnoreCase(dedupe)){
                    Integer exists = jdbc.queryForObject("SELECT COUNT(1) FROM contacts WHERE user_id=? AND LOWER(email)=LOWER(?)", Integer.class, userId, email);
                    if(exists == null || exists == 0){
                        String sql = "INSERT INTO contacts (user_id, email, first_name, last_name, custom_fields, phone, country, city, segment, unsubscribed, created_at) VALUES (?,?,?,?,?,?,?,?,?,NVL(?,0), SYSTIMESTAMP)";
                        Object[] args = new Object[]{
                            userId, email, fn, ln,
                            customClob != null ? customClob : new SqlParameterValue(Types.CLOB, null),
                            phone, country, city, segment,
                            new SqlParameterValue(Types.INTEGER, unsubInt)
                        };
                        int[] types = new int[]{
                            Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.CLOB,
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER
                        };
                        jdbc.update(sql, args, types);
                    } else {
                        String sql = "UPDATE contacts SET first_name=?, last_name=?, custom_fields=CASE WHEN ? IS NOT NULL THEN ? ELSE custom_fields END, phone=COALESCE(?, phone), country=COALESCE(?, country), city=COALESCE(?, city), segment=COALESCE(?, segment), unsubscribed=COALESCE(?, unsubscribed), updated_at=SYSTIMESTAMP WHERE user_id=? AND LOWER(email)=LOWER(?)";
                        Object[] args = new Object[]{
                            fn, ln,
                            customClob != null ? customClob : new SqlParameterValue(Types.CLOB, null),
                            customClob != null ? customClob : new SqlParameterValue(Types.CLOB, null),
                            phone, country, city, segment,
                            new SqlParameterValue(Types.INTEGER, unsubInt),
                            userId, email
                        };
                        int[] types = new int[]{
                            Types.VARCHAR, Types.VARCHAR, Types.CLOB, Types.CLOB,
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER,
                            Types.NUMERIC, Types.VARCHAR
                        };
                        jdbc.update(sql, args, types);
                    }
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

    // Trim, truncate to max length, convert empty to null
    private String sanitize(String v, int max){
        if(v == null) return null;
        String s = v.trim();
        if(s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
