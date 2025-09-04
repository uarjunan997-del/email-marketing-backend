package com.emailMarketing.campaign;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailMarketing.contact.ContactRepository;

import java.util.List;

@Service
public class CampaignService {
    private final CampaignRepository repo; private final ContactRepository contactRepository;
    public CampaignService(CampaignRepository repo, ContactRepository contactRepository){this.repo=repo; this.contactRepository=contactRepository;}

    public List<Campaign> list(Long userId){ return repo.findByUserId(userId);}    

    @Transactional
    public Campaign create(Campaign c){
        int recipients = c.getSegment()==null ? (int)contactRepository.countByUserIdAndUnsubscribedFalse(c.getUserId()) : contactRepository.findByUserIdAndSegment(c.getUserId(), c.getSegment()).size();
        c.setTotalRecipients(recipients);
        return repo.save(c);
    }

    @Transactional
    public void markSending(Long id){ repo.findById(id).ifPresent(c->{c.setStatus("SENDING");}); }
}
