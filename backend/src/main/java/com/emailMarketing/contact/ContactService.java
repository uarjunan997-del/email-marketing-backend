package com.emailMarketing.contact;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import com.opencsv.CSVReader;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.dao.EmptyResultDataAccessException;

@Service
public class ContactService {
    private final ContactRepository contactRepository;
    private final JdbcTemplate jdbc;
    private final TaskExecutor importExecutor;
    public ContactService(ContactRepository contactRepository, JdbcTemplate jdbc, @Qualifier("importExecutor") TaskExecutor importExecutor){this.contactRepository=contactRepository; this.jdbc = jdbc; this.importExecutor = importExecutor;}

    public List<Contact> list(Long userId, String segment) {
        if(segment!=null) return contactRepository.findByUserIdAndSegment(userId, segment);
        return contactRepository.findByUserId(userId);
    }

    @Transactional
    public Contact add(Contact c){ return contactRepository.save(c); }

    @Transactional
    public Contact update(Long userId, Long id, com.emailMarketing.contact.ContactController.CreateContact req){
        var c = contactRepository.findById(id).orElseThrow();
        if(!c.getUserId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        c.setEmail(req.email()); c.setFirstName(req.firstName()); c.setLastName(req.lastName()); c.setSegment(req.segment());
        // Contact entity uses LocalDateTime createdAt only; update createdAt to now for simplicity of last change tracking
        c.setCreatedAt(java.time.LocalDateTime.now());
        return contactRepository.save(c);
    }

    @Transactional
    public void delete(Long userId, Long id, boolean erase){
        var c = contactRepository.findById(id).orElseThrow();
        if(!c.getUserId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        if(erase){ contactRepository.delete(c); } else { /* mark as unsubscribed as soft-delete fallback */ c.setUnsubscribed(true); contactRepository.save(c); }
    }

    // GDPR helpers
    @Transactional
    public void requestDelete(Long userId, Long id){
        var c = contactRepository.findById(id).orElseThrow();
        if(!c.getUserId().equals(userId)) throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        jdbc.update("UPDATE contacts SET is_deleted=1, delete_requested_at=SYSTIMESTAMP, unsubscribed=1 WHERE id=? AND user_id=?", id, userId);
    }

    @Transactional
    public void purgeContact(Long userId, Long id){
        // remove child rows first then contact
        jdbc.update("DELETE FROM contact_list_members WHERE contact_id IN (SELECT id FROM contacts WHERE id=? AND user_id=?)", id, userId);
        jdbc.update("DELETE FROM contact_activities WHERE contact_id IN (SELECT id FROM contacts WHERE id=? AND user_id=?)", id, userId);
        jdbc.update("DELETE FROM contact_scores WHERE contact_id IN (SELECT id FROM contacts WHERE id=? AND user_id=?)", id, userId);
        jdbc.update("DELETE FROM contacts WHERE id=? AND user_id=?", id, userId);
    }

    public Map<String,Object> createImportJob(Long userId, Map<String,Object> body){
        String mapping = body==null?null:String.valueOf(body.getOrDefault("mapping", null));
        String dedupe = body==null?"MERGE":String.valueOf(body.getOrDefault("dedupe_strategy", "MERGE"));
        String filename = body==null?null:String.valueOf(body.getOrDefault("filename", null));
        jdbc.update("INSERT INTO import_jobs (user_id, filename, source, status, mapping, dedupe_strategy, created_at, updated_at) VALUES (?,?,?,?,?,?,SYSTIMESTAMP,SYSTIMESTAMP)", userId, filename, "UI", "CREATED", mapping, dedupe);
        Long jobId = jdbc.queryForObject("SELECT id FROM import_jobs WHERE ROWID = (SELECT MAX(ROWID) FROM import_jobs WHERE user_id=?)", Long.class, userId);
        java.util.Map<String,Object> r = new java.util.HashMap<>();
        r.put("jobId", jobId);
        r.put("status", "CREATED");
        r.put("dedupe_strategy", dedupe);
        return r;
    }

    public Map<String,Object> handleImportUpload(Long userId, org.springframework.web.multipart.MultipartFile file, String mapping){
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        Long jobId;
        try {
            jdbc.update("INSERT INTO import_jobs (user_id, filename, source, status, mapping, created_at, updated_at) VALUES (?,?,?,?,?,SYSTIMESTAMP,SYSTIMESTAMP)", userId, file.getOriginalFilename(), "UI", "PENDING", mapping);
            jobId = jdbc.queryForObject("SELECT id FROM import_jobs WHERE ROWID = (SELECT MAX(ROWID) FROM import_jobs WHERE user_id=?)", Long.class, userId);
        } catch (org.springframework.dao.DataAccessException dae){
            throw dae;
        }

        // Save multipart file to temp file
        try {
            File tmp = File.createTempFile("import_", ".csv");
            try (FileOutputStream fos = new FileOutputStream(tmp)){
                fos.write(file.getBytes());
            }
            // Launch async processing
            importExecutor.execute(() -> { processCsvToStaging(jobId, tmp); });
        } catch(Exception ex){
            // mark job failed
            jdbc.update("UPDATE import_jobs SET status=?, errors=? WHERE id=?", "FAILED", ex.getMessage(), jobId);
        }

    Map<String,Object> r = new java.util.HashMap<>(); r.put("jobId", jobId); r.put("status","accepted"); return r;
    }

    private void processCsvToStaging(Long jobId, File tmpFile){
    int batchSize = 500; List<Object[]> batch = new ArrayList<>();
        // Load mapping JSON (optional) from import_jobs
        String mappingJson = null;
        try {
            mappingJson = jdbc.queryForObject("SELECT mapping FROM import_jobs WHERE id=?", String.class, jobId);
        } catch(Exception ignored) {}
        ObjectMapper om = new ObjectMapper();
        try (CSVReader reader = new CSVReader(new InputStreamReader(new java.io.FileInputStream(tmpFile), StandardCharsets.UTF_8))){
            String[] header = reader.readNext();
            int emailIdx = 0, fnIdx = 1, lnIdx = 2, phoneIdx=-1, countryIdx=-1, cityIdx=-1, segmentIdx=-1, unsubIdx=-1;
            java.util.Set<String> customKeys = new java.util.HashSet<>();
            if(header != null && mappingJson != null && !mappingJson.isBlank()){
                try {
                    JsonNode map = om.readTree(mappingJson);
                    // Option A: header name mapping
                    if(map.hasNonNull("email") || map.hasNonNull("firstName") || map.hasNonNull("lastName")){
                        emailIdx = indexOfIgnoreCase(header, map.path("email").asText(null));
                        fnIdx = indexOfIgnoreCase(header, map.path("firstName").asText(null));
                        lnIdx = indexOfIgnoreCase(header, map.path("lastName").asText(null));
                        phoneIdx = indexOfIgnoreCase(header, map.path("phone").asText(null));
                        countryIdx = indexOfIgnoreCase(header, map.path("country").asText(null));
                        cityIdx = indexOfIgnoreCase(header, map.path("city").asText(null));
                        segmentIdx = indexOfIgnoreCase(header, map.path("segment").asText(null));
                        unsubIdx = indexOfIgnoreCase(header, map.path("unsubscribed").asText(null));
                    }
                    // Option B: explicit indexes
                    if(map.has("emailIndex")) emailIdx = map.path("emailIndex").asInt(emailIdx);
                    if(map.has("firstNameIndex")) fnIdx = map.path("firstNameIndex").asInt(fnIdx);
                    if(map.has("lastNameIndex")) lnIdx = map.path("lastNameIndex").asInt(lnIdx);
                    if(map.has("phoneIndex")) phoneIdx = map.path("phoneIndex").asInt(phoneIdx);
                    if(map.has("countryIndex")) countryIdx = map.path("countryIndex").asInt(countryIdx);
                    if(map.has("cityIndex")) cityIdx = map.path("cityIndex").asInt(cityIdx);
                    if(map.has("segmentIndex")) segmentIdx = map.path("segmentIndex").asInt(segmentIdx);
                    if(map.has("unsubscribedIndex")) unsubIdx = map.path("unsubscribedIndex").asInt(unsubIdx);
                    // Custom field header mappings: { custom: { key1: "Header A", key2: "Header B" } }
                    if(map.has("custom")){
                        var fields = map.get("custom");
                        fields.fieldNames().forEachRemaining(k -> customKeys.add(k));
                    }
                } catch(Exception ignored) {}
            }
            String[] row;
            int rowNum = 0;
            while((row = reader.readNext())!=null){
                rowNum++;
                String raw = String.join(",", row);
                String email = getAt(row, emailIdx);
                String fn = getAt(row, fnIdx);
                String ln = getAt(row, lnIdx);
                String customJson = null;
                if(mappingJson!=null && !mappingJson.isBlank()){
                    try {
                        JsonNode map = om.readTree(mappingJson);
                        if(map.has("custom")){
                            ObjectMapper inner = new ObjectMapper();
                            com.fasterxml.jackson.databind.node.ObjectNode obj = inner.createObjectNode();
                            JsonNode cMap = map.get("custom");
                            final String[] rowRef = row;
                            cMap.fieldNames().forEachRemaining(key -> {
                                String headerName = cMap.path(key).asText(null);
                                int idx = indexOfIgnoreCase(header, headerName);
                                String val = getAt(rowRef, idx);
                                if(val!=null) obj.put(key, val);
                            });
                            customJson = obj.size()>0? inner.writeValueAsString(obj): null;
                        }
                    } catch(Exception ignored){}
                }
                String phone = getAt(row, phoneIdx);
                String country = getAt(row, countryIdx);
                String city = getAt(row, cityIdx);
                String segment = getAt(row, segmentIdx);
                String unsubStr = getAt(row, unsubIdx);
                Integer unsubscribed = null;
                if(unsubStr!=null){
                    String s = unsubStr.trim().toLowerCase();
                    if(s.equals("1")||s.equals("true")||s.equals("yes")) unsubscribed = 1; else unsubscribed = 0;
                }
                batch.add(new Object[]{jobId, rowNum, raw, email, fn, ln, customJson, phone, country, city, segment, unsubscribed, "PENDING"});
                if(batch.size()>=batchSize){
                    flushBatchToStagingWithMore(batch);
                    jdbc.update("UPDATE import_jobs SET total_rows = NVL(total_rows,0) + ? WHERE id=?", batch.size(), jobId);
                    batch.clear();
                }
            }
            if(!batch.isEmpty()){
                flushBatchToStagingWithMore(batch);
                jdbc.update("UPDATE import_jobs SET total_rows = NVL(total_rows,0) + ? WHERE id=?", batch.size(), jobId);
            }
            jdbc.update("UPDATE import_jobs SET status='READY_FOR_PROCESSING' WHERE id=?", jobId);
        } catch(Exception e){ jdbc.update("UPDATE import_jobs SET status='FAILED', errors=? WHERE id=?", e.getMessage(), jobId); }
        try { tmpFile.delete(); } catch(Exception ignored){}
    }

    private int indexOfIgnoreCase(String[] header, String name){
        if(header==null || name==null) return -1;
        for(int i=0;i<header.length;i++) if(name.equalsIgnoreCase(header[i])) return i;
        return -1;
    }

    private String getAt(String[] arr, int idx){
        if(idx<0 || arr==null || idx>=arr.length) return null;
        String v = arr[idx];
        return v==null?null:v.trim();
    }

    private void flushBatchToStagingWithMore(List<Object[]> batch){
        String sql = "INSERT INTO import_staging (job_id, row_number, raw_data, parsed_email, parsed_first_name, parsed_last_name, parsed_custom_fields, parsed_phone, parsed_country, parsed_city, parsed_segment, parsed_unsubscribed, status, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,SYSTIMESTAMP)";
        jdbc.batchUpdate(sql, batch);
    }

    public java.util.List<java.util.Map<String,Object>> getActivity(Long userId, Long contactId){
        String sql = "SELECT type, metadata, created_at FROM contact_activities WHERE user_id=? AND contact_id=? ORDER BY created_at DESC FETCH FIRST 100 ROWS ONLY";
        return jdbc.query(sql, (rs, i) -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("type", rs.getString("type"));
            m.put("metadata", rs.getString("metadata"));
            m.put("createdAt", rs.getObject("created_at"));
            return m;
        }, userId, contactId);
    }

    public String export(Long userId, String format){
        // return a presigned url or a local download link placeholder
        return "/download/exports/" + java.util.UUID.randomUUID().toString() + "." + format;
    }

    public StreamingResponseBody exportCsv(Long userId){
        return new StreamingResponseBody(){
            @Override public void writeTo(@org.springframework.lang.NonNull OutputStream os){
                try(BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))){
            // header
            w.write("email,first_name,last_name,phone,country,city,segment,unsubscribed,created_at,updated_at\n");
            String sql = "SELECT email, first_name, last_name, phone, country, city, segment, unsubscribed, created_at, updated_at FROM contacts WHERE user_id=? ORDER BY created_at";
                    jdbc.query(sql, rs -> {
                        try {
                String line = csv(rs.getString(1)) + "," + csv(rs.getString(2)) + "," + csv(rs.getString(3)) + "," + csv(rs.getString(4)) + "," + csv(rs.getString(5)) + "," + csv(rs.getString(6)) + "," + csv(rs.getString(7)) + "," + String.valueOf(rs.getInt(8)) + "," + csv(String.valueOf(rs.getObject(9))) + "," + csv(String.valueOf(rs.getObject(10))) + "\n";
                            w.write(line);
                        } catch(Exception ignored){}
                    }, userId);
                } catch(Exception ignored){}
            }
        };
    }

    private String csv(String s){
        if(s==null) return "";
        boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String val = s.replace("\"", "\"\"");
        return needQuote? ("\""+val+"\"") : val;
    }

    // Custom fields metadata
    public List<Map<String,Object>> listCustomFields(Long userId){
        return jdbc.queryForList("SELECT id, field_key, label, data_type, schema_json, created_at FROM contact_custom_fields WHERE user_id=? ORDER BY created_at DESC", userId);
    }

    @Transactional
    public Map<String,Object> createCustomField(Long userId, String fieldKey, String label, String dataType, String schemaJson){
        jdbc.update("INSERT INTO contact_custom_fields (user_id, field_key, label, data_type, schema_json, created_at) VALUES (?,?,?,?,?,SYSTIMESTAMP)", userId, fieldKey, label, dataType, schemaJson);
        Long id = jdbc.queryForObject("SELECT id FROM contact_custom_fields WHERE ROWID=(SELECT MAX(ROWID) FROM contact_custom_fields WHERE user_id=?)", Long.class, userId);
        return Map.of("id", id, "field_key", fieldKey);
    }

    @Transactional
    public void updateCustomField(Long userId, Long id, String label, String dataType, String schemaJson){
        jdbc.update("UPDATE contact_custom_fields SET label=COALESCE(?,label), data_type=COALESCE(?,data_type), schema_json=COALESCE(?,schema_json) WHERE id=? AND user_id=?", label, dataType, schemaJson, id, userId);
    }

    @Transactional
    public void deleteCustomField(Long userId, Long id){
        jdbc.update("DELETE FROM contact_custom_fields WHERE id=? AND user_id=?", id, userId);
    }

    // Update a contact's custom_fields JSON
    @Transactional
    public void setContactCustomFields(Long userId, Long contactId, String customJson){
        jdbc.update("UPDATE contacts SET custom_fields=? , updated_at=SYSTIMESTAMP WHERE id=? AND user_id=?", customJson, contactId, userId);
    }

    // Segment preview for dynamic lists
    public Map<String,Object> previewList(Long userId, Long listId){
        Map<String,Object> row = jdbc.queryForMap("SELECT is_dynamic, dynamic_query FROM contact_lists WHERE id=? AND user_id=?", listId, userId);
        Number dyn = (Number)row.get("IS_DYNAMIC");
        String q = (String)row.get("DYNAMIC_QUERY");
        if(dyn==null || dyn.intValue()==0){
            int count = jdbc.queryForObject("SELECT COUNT(1) FROM contact_list_members WHERE list_id=?", Integer.class, listId);
            List<Map<String,Object>> sample = jdbc.queryForList("SELECT c.id, c.email FROM contact_list_members m JOIN contacts c ON c.id=m.contact_id WHERE m.list_id=? FETCH FIRST 10 ROWS ONLY", listId);
            return Map.of("count", count, "sample", sample);
        }
        String filterSegment = null; String startsWith = null; String contains = null; Integer unsub = null;
        try {
            if(q!=null && !q.isBlank()){
                ObjectMapper om = new ObjectMapper();
                JsonNode node = om.readTree(q);
                filterSegment = node.path("segmentEquals").asText(null);
                startsWith = node.path("segmentStartsWith").asText(null);
                contains = node.path("segmentContains").asText(null);
                if(node.has("unsubscribed")) unsub = node.get("unsubscribed").asBoolean(false)?1:0;
            }
        } catch(Exception ignored){}
        if(filterSegment==null && startsWith==null && contains==null && unsub==null) return Map.of("count", 0, "sample", List.of());
        StringBuilder where = new StringBuilder(" WHERE user_id=?");
        java.util.List<Object> args = new java.util.ArrayList<>(); args.add(userId);
        if(filterSegment!=null){ where.append(" AND segment=?"); args.add(filterSegment); }
        if(startsWith!=null){ where.append(" AND segment LIKE ?"); args.add(startsWith + "%"); }
        if(contains!=null){ where.append(" AND LOWER(segment) LIKE ?"); args.add("%" + contains.toLowerCase() + "%"); }
        if(unsub!=null){ where.append(" AND unsubscribed=?"); args.add(unsub); }
        int count = jdbc.queryForObject("SELECT COUNT(1) FROM contacts" + where, Integer.class, args.toArray());
        List<Map<String,Object>> sample = jdbc.queryForList("SELECT id, email FROM contacts" + where + " FETCH FIRST 10 ROWS ONLY", args.toArray());
        return Map.of("count", count, "sample", sample);
    }

    // Import job status endpoints
    public List<Map<String,Object>> listImportJobs(Long userId){
        String sql = "SELECT id, filename, source, total_rows, processed_rows, failed_rows, status, dedupe_strategy, created_at, updated_at FROM import_jobs WHERE user_id=? ORDER BY created_at DESC FETCH FIRST 50 ROWS ONLY";
        return jdbc.queryForList(sql, userId);
    }

    public Map<String,Object> getImportJob(Long userId, Long jobId){
        String sql = "SELECT id, filename, source, total_rows, processed_rows, failed_rows, status, dedupe_strategy, mapping, errors, created_at, updated_at FROM import_jobs WHERE user_id=? AND id=?";
        List<Map<String,Object>> r = jdbc.queryForList(sql, userId, jobId);
        return r.isEmpty()? java.util.Map.of(): r.get(0);
    }

    public List<Map<String,Object>> getImportErrors(Long userId, Long jobId){
        String sql = "SELECT s.row_number, s.parsed_email, s.parsed_first_name, s.parsed_last_name, s.error_message FROM import_staging s JOIN import_jobs j ON j.id=s.job_id WHERE j.user_id=? AND s.job_id=? AND s.status='FAILED' ORDER BY s.row_number";
        return jdbc.queryForList(sql, userId, jobId);
    }

    // Suppression list management
    public List<Map<String,Object>> listSuppression(Long userId){
        return jdbc.queryForList("SELECT email, reason, added_at FROM suppression_list WHERE user_id=? ORDER BY added_at DESC", userId);
    }

    @Transactional
    public void suppressEmails(Long userId, List<String> emails, String reason){
        if(emails==null || emails.isEmpty()) return;
        List<Object[]> batch = new ArrayList<>();
        for(String e: emails){ if(e!=null) batch.add(new Object[]{userId, e, reason}); }
        jdbc.batchUpdate("INSERT INTO suppression_list (user_id, email, reason, added_at) VALUES (?,?,?,SYSTIMESTAMP)", batch);
    }

    @Transactional
    public void unsuppressEmail(Long userId, String email){
        jdbc.update("DELETE FROM suppression_list WHERE user_id=? AND LOWER(email)=LOWER(?)", userId, email);
    }

    // Contact Lists (segments)
    public List<Map<String,Object>> listLists(Long userId){
        String sql = "SELECT id, name, description, is_dynamic, created_at, updated_at FROM contact_lists WHERE user_id=? ORDER BY created_at DESC";
        return jdbc.queryForList(sql, userId);
    }

    @Transactional
    public Map<String,Object> createList(Long userId, String name, String description, boolean isDynamic, String dynamicQuery){
        jdbc.update("INSERT INTO contact_lists (user_id, name, description, is_dynamic, dynamic_query, created_at, updated_at) VALUES (?,?,?,?,?,SYSTIMESTAMP,SYSTIMESTAMP)", userId, name, description, isDynamic?1:0, dynamicQuery);
        Long id = jdbc.queryForObject("SELECT id FROM contact_lists WHERE ROWID=(SELECT MAX(ROWID) FROM contact_lists WHERE user_id=?)", Long.class, userId);
        return Map.of("id", id, "name", name, "description", description, "is_dynamic", isDynamic);
    }

    @Transactional
    public void updateList(Long userId, Long listId, String name, String description, Boolean isDynamic, String dynamicQuery){
        jdbc.update("UPDATE contact_lists SET name=COALESCE(?,name), description=COALESCE(?,description), is_dynamic=COALESCE(?,is_dynamic), dynamic_query=COALESCE(?,dynamic_query), updated_at=SYSTIMESTAMP WHERE id=? AND user_id=?",
            name, description, isDynamic==null?null:(isDynamic?1:0), dynamicQuery, listId, userId);
    }

    @Transactional
    public void deleteList(Long userId, Long listId){
        jdbc.update("DELETE FROM contact_list_members WHERE list_id=?", listId);
        jdbc.update("DELETE FROM contact_lists WHERE id=? AND user_id=?", listId, userId);
    }

    public List<Map<String,Object>> listMembers(Long userId, Long listId){
        String sql = "SELECT c.id, c.email, c.first_name, c.last_name FROM contact_list_members m JOIN contacts c ON c.id=m.contact_id WHERE m.list_id=? AND c.user_id=? ORDER BY m.added_at DESC";
        return jdbc.queryForList(sql, listId, userId);
    }

    @Transactional
    public void addMembersByEmails(Long userId, Long listId, List<String> emails){
        if(emails==null || emails.isEmpty()) return;
        List<Map<String,Object>> ids = jdbc.queryForList("SELECT id FROM contacts WHERE user_id=? AND LOWER(email) IN (" + inPlaceholders(emails.size()) + ")", buildArgs(userId, emails));
        List<Object[]> batch = new ArrayList<>();
        for(Map<String,Object> row: ids){ batch.add(new Object[]{listId, ((Number)row.get("ID")).longValue()}); }
        jdbc.batchUpdate("INSERT /*+ IGNORE_ROW_ON_DUPKEY_INDEX(contact_list_members(list_id, contact_id)) */ INTO contact_list_members (list_id, contact_id, added_at) VALUES (?,?,SYSTIMESTAMP)", batch);
    }

    @Transactional
    public void removeMembersByEmails(Long userId, Long listId, List<String> emails){
        if(emails==null || emails.isEmpty()) return;
        jdbc.update("DELETE FROM contact_list_members WHERE list_id=? AND contact_id IN (SELECT id FROM contacts WHERE user_id=? AND LOWER(email) IN (" + inPlaceholders(emails.size()) + "))", buildArgs(listId, userId, emails));
    }

    @Transactional
    public int materializeList(Long userId, Long listId){
        // fetch list and dynamic_query
        Map<String,Object> row;
        try { row = jdbc.queryForMap("SELECT is_dynamic, dynamic_query FROM contact_lists WHERE id=? AND user_id=?", listId, userId); } catch(EmptyResultDataAccessException ex){ return 0; }
        Number dyn = (Number)row.get("IS_DYNAMIC");
        String q = (String)row.get("DYNAMIC_QUERY");
        if(dyn==null || dyn.intValue()==0) return 0; // not dynamic
        // filters: { segmentEquals, segmentStartsWith, segmentContains, unsubscribed }
        String filterSegment = null; String startsWith = null; String contains = null; Integer unsub = null;
        try {
            if(q!=null && !q.isBlank()){
                ObjectMapper om = new ObjectMapper();
                JsonNode node = om.readTree(q);
                filterSegment = node.path("segmentEquals").asText(null);
                startsWith = node.path("segmentStartsWith").asText(null);
                contains = node.path("segmentContains").asText(null);
                if(node.has("unsubscribed")) unsub = node.get("unsubscribed").asBoolean(false)?1:0;
            }
        } catch(Exception ignored){}
        if(filterSegment==null && startsWith==null && contains==null && unsub==null) return 0;
        // rebuild membership
        jdbc.update("DELETE FROM contact_list_members WHERE list_id=?", listId);
        StringBuilder sql = new StringBuilder("INSERT INTO contact_list_members (list_id, contact_id, added_at) SELECT ?, id, SYSTIMESTAMP FROM contacts WHERE user_id=?");
        java.util.List<Object> args = new java.util.ArrayList<>(); args.add(listId); args.add(userId);
        if(filterSegment!=null){ sql.append(" AND segment=?"); args.add(filterSegment); }
        if(startsWith!=null){ sql.append(" AND segment LIKE ?"); args.add(startsWith + "%"); }
        if(contains!=null){ sql.append(" AND LOWER(segment) LIKE ?"); args.add("%" + contains.toLowerCase() + "%"); }
        if(unsub!=null){ sql.append(" AND unsubscribed=?"); args.add(unsub); }
        int inserted = jdbc.update(sql.toString(), args.toArray());
        return inserted;
    }

    private Object[] buildArgs(Object first, List<String> emails){
        Object[] args = new Object[1+emails.size()]; args[0]=first; for(int i=0;i<emails.size();i++) args[i+1]=emails.get(i).toLowerCase(); return args;
    }
    private Object[] buildArgs(Object a, Object b, List<String> emails){
        Object[] args = new Object[2+emails.size()]; args[0]=a; args[1]=b; for(int i=0;i<emails.size();i++) args[i+2]=emails.get(i).toLowerCase(); return args;
    }
    private String inPlaceholders(int n){ StringBuilder sb=new StringBuilder(); for(int i=0;i<n;i++){ if(i>0) sb.append(','); sb.append('?'); } return sb.toString(); }
}
