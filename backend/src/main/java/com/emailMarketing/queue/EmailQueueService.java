package com.emailMarketing.queue;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmailQueueService {
    private final EmailQueueRepository repo;
    private final JavaMailSender mailSender;

    public EmailQueueService(EmailQueueRepository repo, JavaMailSender mailSender) {
        this.repo = repo;
        this.mailSender = mailSender;
    }

    @Transactional
    public void enqueue(Long campaignId, Long userId, String recipient, String subject, String body) {
        EmailQueueItem item = new EmailQueueItem();
        item.setCampaignId(campaignId);
        item.setUserId(userId);
        item.setRecipient(recipient);
        item.setSubject(subject);
        item.setBody(body);
        repo.save(item);
    }

    @Transactional
    public int processPending() {
        List<EmailQueueItem> items = repo.findTop50ByStatusAndNextAttemptAtBeforeOrderByIdAsc("PENDING",
                LocalDateTime.now());
        int sent = 0;
        for (var item : items) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setTo(item.getRecipient());
                helper.setSubject(item.getSubject());
                helper.setText(item.getBody(), true);
                mailSender.send(message);
                item.setStatus("SENT");
                sent++;
            } catch (Exception ex) {
                item.setAttempt(item.getAttempt() + 1);
                if (item.getAttempt() > 3) {
                    item.setStatus("FAILED");
                } else {
                    item.setNextAttemptAt(LocalDateTime.now().plusMinutes(2));
                }
            }
        }
        return sent;
    }
}
