package com.emailMarketing.contact;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.emailMarketing.subscription.UserRepository;

import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/contacts")
public class ContactController {
    private final ContactService contactService; 
    private final UserRepository userRepository;
    private final ContactWebSocketService webSocketService;
    
    public ContactController(ContactService contactService, UserRepository userRepository, ContactWebSocketService webSocketService){
        this.contactService=contactService; 
        this.userRepository=userRepository; 
        this.webSocketService=webSocketService;
    }

    public record CreateContact(@Email String email, String firstName, String lastName, String segment){}

    @GetMapping
    public List<Contact> list(
        @AuthenticationPrincipal UserDetails principal, 
        @RequestParam(required=false) String segment,
        @RequestParam(required=false) String search,
        @RequestParam(required=false) String filters
    ){
        return contactService.list(resolveUserId(principal), segment, search, filters);
    }

    @PostMapping
    public Contact create(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody CreateContact req){
        Contact c = new Contact();
        c.setUserId(resolveUserId(principal));
        c.setEmail(req.email()); c.setFirstName(req.firstName()); c.setLastName(req.lastName()); c.setSegment(req.segment());
        Contact savedContact = contactService.add(c);
        webSocketService.notifyContactCreated(savedContact);
        return savedContact;
    }

    @PutMapping("/{id}")
    public Contact update(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @Valid @RequestBody CreateContact req){
        Contact updatedContact = contactService.update(resolveUserId(principal), id, req);
        webSocketService.notifyContactUpdated(updatedContact);
        return updatedContact;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @RequestParam(required=false, defaultValue="false") boolean erase){
        Long userId = resolveUserId(principal);
        contactService.delete(userId, id, erase);
        webSocketService.notifyContactDeleted(userId, id);
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    // GDPR request + purge
    @PostMapping("/{id}/request-delete")
    public ResponseEntity<?> requestDelete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id){
        contactService.requestDelete(resolveUserId(principal), id);
        return ResponseEntity.ok(Map.of("status","ok"));
    }
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<?> purge(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id){
        contactService.purgeContact(resolveUserId(principal), id);
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    // Create an import job (metadata + mapping). The actual file upload can be done to /contacts/import
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkImport(@AuthenticationPrincipal UserDetails principal, @RequestBody Map<String,Object> body){
        var job = contactService.createImportJob(resolveUserId(principal), body);
        return ResponseEntity.ok(job);
    }

    // Multipart file upload to trigger import processing
    @PostMapping("/import")
    public ResponseEntity<?> uploadImport(@AuthenticationPrincipal UserDetails principal, @RequestParam("file") MultipartFile file, @RequestParam(required=false) String mapping){
        var job = contactService.handleImportUpload(resolveUserId(principal), file, mapping);
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping("/{id}/activity")
    public List<java.util.Map<String,Object>> activity(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id){
        return contactService.getActivity(resolveUserId(principal), id);
    }

    // CSV export (streaming) - explicit mapping to avoid HttpMessageConverter issues
    @GetMapping(value = "/export", params = "format=csv", produces = "text/csv")
    public StreamingResponseBody exportCsv(
        @AuthenticationPrincipal UserDetails principal,
        @RequestParam(required=false) String fields,
        @RequestParam(required=false) List<Long> contactIds,
        @RequestParam(required=false) String filters,
        HttpServletResponse response
    ){
        // Parse filters JSON if provided
        List<Map<String,Object>> filterList = null;
        if(filters != null && !filters.trim().isEmpty()) {
            try {
                filterList = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(filters, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){});
            } catch(Exception ignored) {}
        }

        // Set headers for file download
        response.setHeader("Content-Disposition", "attachment; filename=contacts.csv");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        return contactService.exportCsv(resolveUserId(principal), fields, contactIds, filterList);
    }

    @GetMapping(value = "/export", produces = {"application/json"})
    public ResponseEntity<?> export(
        @AuthenticationPrincipal UserDetails principal, 
        @RequestParam(required=false) String format,
        @RequestParam(required=false) String fields,
        @RequestParam(required=false) List<Long> contactIds,
        @RequestParam(required=false) String filters
    ){
        String fmt = format==null?"xlsx":format; // default to non-csv here

        // Parse filters JSON if provided
        List<Map<String,Object>> filterList = null;
        if(filters != null && !filters.trim().isEmpty()) {
            try {
                filterList = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(filters, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){});
            } catch(Exception ignored) {}
        }

        // For non-CSV formats, return a link (or implement other logic)
        var link = contactService.export(resolveUserId(principal), fmt, fields, contactIds, filterList);
        return ResponseEntity.ok(Map.of("downloadUrl", link));
    }

    // Advanced export with request body for complex filters (non-CSV only)
    public record ExportRequest(String format, String fields, List<Long> contactIds, List<Map<String,Object>> filters){}

    // CSV export via POST (streaming). Use Accept: text/csv
    @PostMapping(value = "/export", produces = "text/csv", consumes = "application/json")
    public StreamingResponseBody exportCsvPost(
        @AuthenticationPrincipal UserDetails principal,
        @RequestBody ExportRequest req,
        HttpServletResponse response
    ){
        // Set headers for file download
        response.setHeader("Content-Disposition", "attachment; filename=contacts.csv");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        return contactService.exportCsv(
            resolveUserId(principal),
            req.fields(),
            req.contactIds(),
            req.filters()
        );
    }

    @PostMapping(value = "/export", produces = {"application/json"})
    public ResponseEntity<?> exportAdvanced(@AuthenticationPrincipal UserDetails principal, @RequestBody ExportRequest req){
        String fmt = req.format() == null ? "xlsx" : req.format();
        if ("csv".equalsIgnoreCase(fmt)) {
            // For CSV via POST use Accept: text/csv (this method returns JSON). Alternatively, use GET /contacts/export?format=csv
            return ResponseEntity.status(415).body(Map.of(
                "error",
                "For CSV via POST, set Accept: text/csv (streaming). Or use GET /contacts/export?format=csv."
            ));
        }
        var link = contactService.export(resolveUserId(principal), fmt, req.fields(), req.contactIds(), req.filters());
        return ResponseEntity.ok(Map.of("downloadUrl", link));
    }

    // Export job status
    @GetMapping("/export/{exportId}/status")
    public Map<String,Object> getExportStatus(@AuthenticationPrincipal UserDetails principal, @PathVariable String exportId){
        return contactService.getExportJob(resolveUserId(principal), exportId);
    }

    // Import job status
    @GetMapping("/imports")
    public List<Map<String,Object>> listJobs(@AuthenticationPrincipal UserDetails principal){
        return contactService.listImportJobs(resolveUserId(principal));
    }

    @GetMapping("/imports/{jobId}")
    public Map<String,Object> getJob(@AuthenticationPrincipal UserDetails principal, @PathVariable Long jobId){
        return contactService.getImportJob(resolveUserId(principal), jobId);
    }

    // Simple polling endpoint for specific job progress (redundant with getJob but explicit)
    @GetMapping("/imports/{jobId}/progress")
    public Map<String,Object> getJobProgress(@AuthenticationPrincipal UserDetails principal, @PathVariable Long jobId){
        return contactService.getImportJob(resolveUserId(principal), jobId);
    }

    @GetMapping("/imports/{jobId}/errors")
    public List<Map<String,Object>> getJobErrors(@AuthenticationPrincipal UserDetails principal, @PathVariable Long jobId){
        return contactService.getImportErrors(resolveUserId(principal), jobId);
    }

    // Suppression list management
    @GetMapping("/suppression")
    public List<Map<String,Object>> listSuppression(@AuthenticationPrincipal UserDetails principal){
        return contactService.listSuppression(resolveUserId(principal));
    }

    public record SuppressRequest(List<String> emails, String reason){}

    @PostMapping("/suppression")
    public ResponseEntity<?> suppress(@AuthenticationPrincipal UserDetails principal, @RequestBody SuppressRequest body){
        contactService.suppressEmails(resolveUserId(principal), body.emails(), body.reason());
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    @DeleteMapping("/suppression")
    public ResponseEntity<?> unsuppress(@AuthenticationPrincipal UserDetails principal, @RequestParam String email){
        contactService.unsuppressEmail(resolveUserId(principal), email);
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    // Contact lists (segments)
    @GetMapping("/lists")
    public List<Map<String,Object>> lists(@AuthenticationPrincipal UserDetails principal){
        return contactService.listLists(resolveUserId(principal));
    }

    public record CreateListReq(String name, String description, Boolean isDynamic, String dynamicQuery){}
    @PostMapping("/lists")
    public Map<String,Object> createList(@AuthenticationPrincipal UserDetails principal, @RequestBody CreateListReq req){
        return contactService.createList(resolveUserId(principal), req.name(), req.description(), req.isDynamic()!=null && req.isDynamic(), req.dynamicQuery());
    }

    @PutMapping("/lists/{listId}")
    public ResponseEntity<?> updateList(@AuthenticationPrincipal UserDetails principal, @PathVariable Long listId, @RequestBody CreateListReq req){
        contactService.updateList(resolveUserId(principal), listId, req.name(), req.description(), req.isDynamic(), req.dynamicQuery());
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    @DeleteMapping("/lists/{listId}")
    public ResponseEntity<?> deleteList(@AuthenticationPrincipal UserDetails principal, @PathVariable Long listId){
        contactService.deleteList(resolveUserId(principal), listId);
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    @GetMapping("/lists/{listId}/members")
    public List<Map<String,Object>> listMembers(@AuthenticationPrincipal UserDetails principal, @PathVariable Long listId){
        return contactService.listMembers(resolveUserId(principal), listId);
    }

    public record EmailsReq(List<String> emails){}
    @PostMapping("/lists/{listId}/members")
    public ResponseEntity<?> addMembers(@AuthenticationPrincipal UserDetails principal, @PathVariable Long listId, @RequestBody EmailsReq req){
        contactService.addMembersByEmails(resolveUserId(principal), listId, req.emails());
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    @DeleteMapping("/lists/{listId}/members")
    public ResponseEntity<?> removeMembers(@AuthenticationPrincipal UserDetails principal, @PathVariable Long listId, @RequestBody EmailsReq req){
        contactService.removeMembersByEmails(resolveUserId(principal), listId, req.emails());
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    @PostMapping("/lists/{listId}/materialize")
    public Map<String,Object> materialize(@AuthenticationPrincipal UserDetails principal, @PathVariable Long listId){
        int count = contactService.materializeList(resolveUserId(principal), listId);
        return Map.of("inserted", count);
    }

    // Custom fields metadata
    @GetMapping("/custom-fields")
    public List<Map<String,Object>> listCustomFields(@AuthenticationPrincipal UserDetails principal){
        return contactService.listCustomFields(resolveUserId(principal));
    }
    public record CustomFieldReq(String fieldKey, String label, String dataType, String schemaJson){}
    @PostMapping("/custom-fields")
    public Map<String,Object> createCustomField(@AuthenticationPrincipal UserDetails principal, @RequestBody CustomFieldReq req){
        return contactService.createCustomField(resolveUserId(principal), req.fieldKey(), req.label(), req.dataType(), req.schemaJson());
    }
    @PutMapping("/custom-fields/{id}")
    public ResponseEntity<?> updateCustomField(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @RequestBody CustomFieldReq req){
        contactService.updateCustomField(resolveUserId(principal), id, req.label(), req.dataType(), req.schemaJson());
        return ResponseEntity.ok(Map.of("status","ok"));
    }
    @DeleteMapping("/custom-fields/{id}")
    public ResponseEntity<?> deleteCustomField(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id){
        contactService.deleteCustomField(resolveUserId(principal), id);
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    // Contact-level custom fields
    public record ContactCustomReq(String customJson){}
    @PutMapping("/{id}/custom-fields")
    public ResponseEntity<?> setContactCustom(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @RequestBody ContactCustomReq req){
        contactService.setContactCustomFields(resolveUserId(principal), id, req.customJson());
        return ResponseEntity.ok(Map.of("status","ok"));
    }

    // Preview dynamic list
    @GetMapping("/lists/{listId}/preview")
    public Map<String,Object> preview(@AuthenticationPrincipal UserDetails principal, @PathVariable Long listId){
        return contactService.previewList(resolveUserId(principal), listId);
    }

    // Preview segment with filters
    public record SegmentPreviewRequest(List<Map<String,Object>> filters){}
    @PostMapping("/segments/preview")
    public Map<String,Object> previewSegment(@AuthenticationPrincipal UserDetails principal, @RequestBody SegmentPreviewRequest req){
        return contactService.previewSegment(resolveUserId(principal), req.filters());
    }

    // Bulk operations
    public record BulkDeleteRequest(List<Long> ids, Boolean erase){}
    @DeleteMapping("/bulk")
    public ResponseEntity<?> bulkDelete(@AuthenticationPrincipal UserDetails principal, @RequestBody BulkDeleteRequest req){
        Long userId = resolveUserId(principal);
        contactService.bulkDelete(userId, req.ids(), req.erase() != null && req.erase());
        webSocketService.notifyBulkContactsDeleted(userId, req.ids());
        return ResponseEntity.ok(Map.of("status", "ok", "deleted", req.ids().size()));
    }

    public record BulkUpdateRequest(List<Long> ids, Map<String,Object> updates){}
    @PutMapping("/bulk")
    public ResponseEntity<?> bulkUpdate(@AuthenticationPrincipal UserDetails principal, @RequestBody BulkUpdateRequest req){
        Long userId = resolveUserId(principal);
        contactService.bulkUpdate(userId, req.ids(), req.updates());
        webSocketService.notifyBulkContactsUpdated(userId, req.ids(), req.updates());
        return ResponseEntity.ok(Map.of("status", "ok", "updated", req.ids().size()));
    }

    public record BulkAddToSegmentRequest(List<Long> contactIds, String segment){}
    @PostMapping("/bulk/add-to-segment")
    public ResponseEntity<?> bulkAddToSegment(@AuthenticationPrincipal UserDetails principal, @RequestBody BulkAddToSegmentRequest req){
        int updated = contactService.bulkAddToSegment(resolveUserId(principal), req.contactIds(), req.segment());
        return ResponseEntity.ok(Map.of("status", "ok", "updated", updated));
    }

    private Long resolveUserId(UserDetails principal){
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }
        return userRepository.findByUsername(principal.getUsername())
                .or(() -> userRepository.findByEmail(principal.getUsername()))
                .map(com.emailMarketing.subscription.User::getId)
                .orElseThrow(() -> new IllegalStateException("USER_NOT_FOUND"));
    }
}
