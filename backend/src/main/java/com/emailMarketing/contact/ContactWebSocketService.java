package com.emailMarketing.contact;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContactWebSocketService {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public void notifyContactCreated(Contact contact) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "CONTACT_CREATED");
        message.put("contact", contact);
        message.put("userId", contact.getUserId());
        
        sendContactUpdate(contact.getUserId(), message);
    }
    
    public void notifyContactUpdated(Contact contact) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "CONTACT_UPDATED");
        message.put("contact", contact);
        message.put("userId", contact.getUserId());
        
        sendContactUpdate(contact.getUserId(), message);
    }
    
    public void notifyContactDeleted(Long userId, Long contactId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "CONTACT_DELETED");
        message.put("contactIds", List.of(contactId));
        message.put("userId", userId);
        
        sendContactUpdate(userId, message);
    }
    
    public void notifyBulkContactsDeleted(Long userId, List<Long> contactIds) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "CONTACT_DELETED");
        message.put("contactIds", contactIds);
        message.put("userId", userId);
        
        sendContactUpdate(userId, message);
    }
    
    public void notifyBulkContactsUpdated(Long userId, List<Long> contactIds, Map<String, Object> changes) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "BULK_UPDATE");
        message.put("contactIds", contactIds);
        message.put("changes", changes);
        message.put("userId", userId);
        
        sendContactUpdate(userId, message);
    }
    
    private void sendContactUpdate(Long userId, Map<String, Object> message) {
        try {
            String destination = "/topic/contacts/" + userId;
            messagingTemplate.convertAndSend(destination, message);
            System.out.println("Sent contact update to " + destination + ": " + objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            System.err.println("Failed to send contact update: " + e.getMessage());
        }
    }
}
