package com.emailMarketing.contact;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ContactService {
    private final ContactRepository contactRepository;
    public ContactService(ContactRepository contactRepository){this.contactRepository=contactRepository;}

    public List<Contact> list(Long userId, String segment) {
        if(segment!=null) return contactRepository.findByUserIdAndSegment(userId, segment);
        return contactRepository.findByUserId(userId);
    }

    @Transactional
    public Contact add(Contact c){ return contactRepository.save(c); }
}
