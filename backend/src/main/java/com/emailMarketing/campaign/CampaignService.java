package com.emailMarketing.campaign;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailMarketing.contact.ContactRepository;

import java.util.List;

@Service
public class CampaignService {
    private final CampaignRepository repo;
    private final ContactRepository contactRepository;
    private final CampaignABTestRepository abRepo;
    private final CampaignRecipientRepository recipientRepo;
    private final SendingQueueRepository sendingQueueRepository;
    private final CampaignScheduleRepository scheduleRepository;
    private final com.emailMarketing.template.EmailTemplateService emailTemplateService;
    private final com.emailMarketing.subscription.SubscriptionRepository subscriptionRepository;
    private final com.emailMarketing.deliverability.EmailBounceRepository bounceRepository;
    private final com.emailMarketing.deliverability.EmailComplaintRepository complaintRepository;
    private final com.emailMarketing.deliverability.SuppressionRepository suppressionRepository;
    private final com.emailMarketing.deliverability.UnsubscribeTokenService unsubscribeTokenService;
    private final CampaignRoiRepository roiRepository;

    public CampaignService(CampaignRepository repo, ContactRepository contactRepository,
            CampaignABTestRepository abRepo, CampaignRecipientRepository recipientRepo,
            SendingQueueRepository sendingQueueRepository, CampaignScheduleRepository scheduleRepository,
            com.emailMarketing.template.EmailTemplateService emailTemplateService,
            com.emailMarketing.subscription.SubscriptionRepository subscriptionRepository,
            com.emailMarketing.deliverability.EmailBounceRepository bounceRepository,
            com.emailMarketing.deliverability.EmailComplaintRepository complaintRepository,
            com.emailMarketing.deliverability.SuppressionRepository suppressionRepository,
            com.emailMarketing.deliverability.UnsubscribeTokenService unsubscribeTokenService,
            CampaignRoiRepository roiRepository) {
        this.repo = repo;
        this.contactRepository = contactRepository;
        this.abRepo = abRepo;
        this.recipientRepo = recipientRepo;
        this.sendingQueueRepository = sendingQueueRepository;
        this.scheduleRepository = scheduleRepository;
        this.emailTemplateService = emailTemplateService;
        this.subscriptionRepository = subscriptionRepository;
        this.bounceRepository = bounceRepository;
        this.complaintRepository = complaintRepository;
        this.suppressionRepository = suppressionRepository;
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.roiRepository = roiRepository;
    }

    public List<Campaign> list(Long userId) {
        return repo.findByUserId(userId);
    }

    @Transactional
    public Campaign create(Campaign c) {
        int recipients = resolveRecipientsCount(c.getUserId(), c.getSegment());
        c.setTotalRecipients(recipients);
        return repo.save(c);
    }

    public Campaign getForUser(Long userId, Long id) {
        Campaign c = repo.findById(id).orElseThrow();
        if (!c.getUserId().equals(userId))
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        return c;
    }

    @Transactional
    public Campaign update(Long userId, Long id, com.emailMarketing.campaign.CampaignController.CampaignRequest req) {
        Campaign c = getForUser(userId, id);
        if (!c.getStatus().equals("DRAFT") && !c.getStatus().equals("REVIEW"))
            throw new IllegalStateException("Cannot modify in status " + c.getStatus());
        c.setName(req.name());
        c.setSegment(req.segment());
        c.setTemplateId(req.templateId());
        c.setSubject(req.subject());
        c.setPreheader(req.preheader());
        // Recompute recipients if segment changed
        c.setTotalRecipients(resolveRecipientsCount(userId, req.segment()));
        return c;
    }

    @Transactional
    public Campaign schedule(Long userId, Long id, com.emailMarketing.campaign.CampaignController.ScheduleRequest req) {
        Campaign c = getForUser(userId, id);
        if (!(c.getStatus().equals("DRAFT") || c.getStatus().equals("REVIEW")))
            throw new IllegalStateException("Only draft/review campaigns can be scheduled");
        c.setScheduledAt(req.scheduledAt());
        c.setStatus("SCHEDULED");
        // Persist schedule row
        CampaignSchedule sch = new CampaignSchedule();
        sch.setCampaignId(c.getId());
        sch.setTimezone(req.timezone() == null ? "UTC" : req.timezone());
        sch.setScheduledTime(req.scheduledAt());
        sch.setSendWindowStart(req.windowStart());
        sch.setSendWindowEnd(req.windowEnd());
        sch.setOptimizationStrategy(req.optimizationStrategy());
        scheduleRepository.save(sch);
        return c;
    }

    @Transactional
    public Campaign sendNow(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        if (c.getStatus().equals("SENDING"))
            return c;
        if (!(c.getStatus().equals("DRAFT") || c.getStatus().equals("REVIEW") || c.getStatus().equals("SCHEDULED")))
            throw new IllegalStateException("Cannot send in status " + c.getStatus());
        // Approval gate
        if ("PENDING".equals(c.getApprovalStatus()))
            throw new IllegalStateException("Awaiting approval");
        c.setStatus("SENDING");
        seedRecipientsIfEmpty(c);
        enqueueInitialBatch(c);
        return c;
    }

    @Transactional
    public Campaign cancel(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        if (c.getStatus().equals("SCHEDULED") || c.getStatus().equals("REVIEW") || c.getStatus().equals("DRAFT")) {
            c.setStatus("CANCELLED");
            // purge queue items
            var items = sendingQueueRepository.findByCampaignIdAndStatusIn(id, java.util.List.of("PENDING", "QUEUED"));
            for (var it : items) {
                it.setStatus("CANCELLED");
            }
        } else {
            throw new IllegalStateException("Cannot cancel campaign in status " + c.getStatus());
        }
        return c;
    }

    public java.util.Map<String, Object> preview(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        // Placeholder; integrate template rendering service to show merged HTML
        return java.util.Map.of(
                "id", c.getId(),
                "subject", c.getSubject(),
                "status", c.getStatus(),
                "sampleHtml", "<p>Preview not implemented</p>");
    }

    @Transactional
    public void setupAbTest(Long userId, Long id, com.emailMarketing.campaign.CampaignController.ABTestRequest req) {
        Campaign c = getForUser(userId, id);
        if (!c.getStatus().equals("DRAFT"))
            throw new IllegalStateException("A/B test can only be added in DRAFT");
        // Clear existing variants first (simple approach)
        abRepo.findByCampaignId(id).forEach(v -> abRepo.delete(v));
        if (req.variants() != null) {
            for (var v : req.variants()) {
                CampaignABTest ab = new CampaignABTest();
                ab.setCampaignId(id);
                ab.setVariantCode(v.code());
                ab.setSubjectLine(v.subject());
                ab.setTemplateId(v.templateId());
                if (v.splitPercent() != null)
                    ab.setSendSplitPercent(v.splitPercent());
                abRepo.save(ab);
            }
        }
    }

    public record Progress(String status, int total, int sent, int opens, int clicks) {
    }

    public Progress progress(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        long sent = recipientRepo.countByCampaignIdAndStatus(id, "SENT");
        return new Progress(c.getStatus(), c.getTotalRecipients(), (int) sent, c.getOpenCount(), c.getClickCount());
    }

    // Analytics aggregation record
    public record Analytics(String status, int sent, int opens, int clicks, Double revenue, Integer orders, Double ctr,
            Double openRate, Double roi) {
    }

    public Analytics analytics(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        long sent = c.getSentCount();
        int opens = c.getOpenCount();
        int clicks = c.getClickCount();
        double openRate = sent == 0 ? 0d : (double) opens / (double) sent;
        double ctr = sent == 0 ? 0d : (double) clicks / (double) sent;
        CampaignRoi roi = roiRepository.findByCampaignId(id);
        Double revenue = roi == null ? null : roi.getRevenueAmount();
        Integer orders = roi == null ? null : roi.getAttributedOrders();
        Double cost = sent == 0 ? 0d : estimateCostPerEmail(c.getUserId()) * sent; // simplistic cost estimation
        Double roiValue = (revenue == null || cost == 0d) ? null : (revenue - cost) / cost;
        return new Analytics(c.getStatus(), (int) sent, opens, clicks, revenue, orders, ctr, openRate, roiValue);
    }

    @Transactional
    public void runAnalysis(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        // Placeholder revenue model: assume each click yields fixed value; if external
        // order system integrated replace this.
        double assumedRevenuePerClick = 1.25; // USD
        double revenue = c.getClickCount() * assumedRevenuePerClick;
        int orders = (int) Math.round(c.getClickCount() * 0.15); // 15% of clicks convert (placeholder)
        CampaignRoi roi = roiRepository.findByCampaignId(id);
        if (roi == null) {
            roi = new CampaignRoi();
            roi.setCampaignId(id);
        }
        roi.setRevenueAmount(revenue);
        roi.setAttributedOrders(orders);
        roi.setLastCalculatedAt(java.time.LocalDateTime.now());
        roiRepository.save(roi);
    }

    private double estimateCostPerEmail(Long userId) {
        // Very rough cost estimation based on plan; could query provider billing later
        return subscriptionRepository.findByUserId(userId).map(sub -> {
            if (sub.getPlanType() == null)
                return 0.0008; // default CPM approximated
            switch (sub.getPlanType()) {
                case FREE:
                    return 0.0010; // higher per email
                case PRO:
                    return 0.0007;
                case PREMIUM:
                    return 0.0005;
                default:
                    return 0.0008;
            }
        }).orElse(0.0012);
    }

    @Transactional
    public void markSending(Long id) {
        repo.findById(id).ifPresent(c -> {
            c.setStatus("SENDING");
        });
    }

    private int resolveRecipientsCount(Long userId, String segment) {
        return segment == null ? (int) contactRepository.countByUserIdAndUnsubscribedFalse(userId)
                : contactRepository.findByUserIdAndSegment(userId, segment).size();
    }

    @Transactional
    protected void seedRecipientsIfEmpty(Campaign c) {
        if (recipientRepo.countByCampaignIdAndStatus(c.getId(), "PENDING") > 0)
            return; // basic guard
        List<com.emailMarketing.contact.Contact> contacts = c.getSegment() == null
                ? contactRepository.findByUserId(c.getUserId())
                : contactRepository.findByUserIdAndSegment(c.getUserId(), c.getSegment());
        // Deduplicate by email (hash set)
        java.util.Set<String> seen = new java.util.HashSet<>();
        var variants = abRepo.findByCampaignId(c.getId());
        boolean hasVariants = !variants.isEmpty();
        java.util.List<CampaignABTest> orderedVariants = new java.util.ArrayList<>(variants);
        // Normalize split percentages; if none provided distribute equally
        if (hasVariants) {
            int totalSplit = orderedVariants.stream()
                    .map(v -> v.getSendSplitPercent() == null ? 0 : v.getSendSplitPercent()).reduce(0, Integer::sum);
            if (totalSplit == 0) {
                int equal = 100 / orderedVariants.size();
                for (var v : orderedVariants) {
                    v.setSendSplitPercent(equal);
                }
            }
        }
        int variantPointer = 0;
        int[] cumulative = null;
        if (hasVariants) {
            cumulative = new int[orderedVariants.size()];
            int running = 0;
            for (int i = 0; i < orderedVariants.size(); i++) {
                running += orderedVariants.get(i).getSendSplitPercent();
                cumulative[i] = running;
            }
        }
        for (var ct : contacts) {
            if (ct.isUnsubscribed())
                continue;
            if (!seen.add(ct.getEmail().toLowerCase()))
                continue; // skip duplicates
            // Suppression & bounce/complaint checks
            if (suppressionRepository.existsByUserIdAndEmail(c.getUserId(), ct.getEmail()))
                continue;
            if (bounceRepository.existsByUserIdAndEmail(c.getUserId(), ct.getEmail()))
                continue;
            if (complaintRepository.existsByUserIdAndEmail(c.getUserId(), ct.getEmail()))
                continue;
            CampaignRecipient r = new CampaignRecipient();
            r.setCampaignId(c.getId());
            r.setContactId(ct.getId());
            r.setEmail(ct.getEmail());
            if (hasVariants) {
                // simple round-robin weighted by cumulative percentages relative to 100
                int mod = (int) ((seen.size() * 100.0) / c.getTotalRecipients()); // approximate progress percent
                if (cumulative != null) {
                    for (int i = 0; i < cumulative.length; i++) {
                        if (mod < cumulative[i]) {
                            variantPointer = i;
                            break;
                        }
                    }
                }
                r.setVariantCode(orderedVariants.get(variantPointer).getVariantCode());
            }
            recipientRepo.save(r);
        }
    }

    @Transactional
    protected void enqueueInitialBatch(Campaign c) {
        // Convert recipients into sending queue entries (initial slice prioritizing
        // first 500 for rate throttling)
        int batchSize = determineInitialBatchSize(c.getUserId());
        List<CampaignRecipient> batch = recipientRepo.findTop500ByCampaignIdAndStatusOrderByIdAsc(c.getId(), "PENDING");
        if (batch.size() > batchSize)
            batch = batch.subList(0, batchSize);
        for (var r : batch) {
            SendingQueueItem item = new SendingQueueItem();
            item.setCampaignId(c.getId());
            item.setUserId(c.getUserId());
            item.setRecipient(r.getEmail());
            item.setSubject(c.getSubject());
            // Render template (placeholder data map)
            String body = resolveRenderedBody(c, r);
            item.setBody(body);
            sendingQueueRepository.save(item);
            r.setStatus("QUEUED");
        }
    }

    @Transactional
    protected void refillQueueIfNeeded(Long campaignId) {
        repo.findById(campaignId).ifPresent(c -> {
            if (!"SENDING".equals(c.getStatus()))
                return; // only top up while sending
            long queued = sendingQueueRepository.countByCampaignIdAndStatus(c.getId(), "PENDING");
            // Threshold: if fewer than 50 pending items and there are still unqueued
            // recipients, top up next slice
            if (queued < 50) {
                int slice = 300; // refill slice size; can adapt per plan
                List<CampaignRecipient> next = recipientRepo.findTop500ByCampaignIdAndStatusOrderByIdAsc(c.getId(),
                        "PENDING");
                if (next.isEmpty())
                    return;
                if (next.size() > slice)
                    next = next.subList(0, slice);
                for (var r : next) {
                    SendingQueueItem item = new SendingQueueItem();
                    item.setCampaignId(c.getId());
                    item.setUserId(c.getUserId());
                    item.setRecipient(r.getEmail());
                    item.setSubject(c.getSubject());
                    item.setBody(resolveRenderedBody(c, r));
                    sendingQueueRepository.save(item);
                    r.setStatus("QUEUED");
                }
            }
        });
    }

    private int determineInitialBatchSize(Long userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(sub -> {
                    if (sub.getPlanType() == null)
                        return 200;
                    switch (sub.getPlanType()) {
                        case FREE:
                            return 100;
                        case PRO:
                            return 500;
                        case PREMIUM:
                            return 1000;
                        default:
                            return 200;
                    }
                }).orElse(100); // default for no subscription
    }

    private String resolveRenderedBody(Campaign c, CampaignRecipient r) {
        if (c.getTemplateId() == null)
            return "<p>No template selected</p>";
        return emailTemplateService.get(c.getTemplateId())
                .map(t -> emailTemplateService.preview(t, java.util.Map.of(
                        "email", r.getEmail(),
                        "first_name", "Subscriber",
                        "campaign_id", c.getId())) + complianceFooter(c, r))
                .orElse("<p>Template missing</p>");
    }

    private String complianceFooter(Campaign c, CampaignRecipient r) {
        String token = unsubscribeTokenService.generate(c.getUserId(), r.getEmail(), c.getId());
        String link = "https://example.com/public/unsubscribe?token=" + token;
        return "<div style='margin-top:24px;font-size:12px;color:#666;'>You are receiving this email because you opted in. <a href='"
                + link + "'>Unsubscribe</a></div>";
    }

    // Approval workflow
    @Transactional
    public Campaign requestApproval(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        if (!c.getStatus().equals("DRAFT"))
            throw new IllegalStateException("Can only request approval in DRAFT");
        c.setStatus("REVIEW");
        c.setApprovalStatus("PENDING");
        c.setReviewRequestedAt(java.time.LocalDateTime.now());
        return c;
    }

    @Transactional
    public Campaign approve(Long userId, Long id) {
        Campaign c = getForUser(userId, id);
        if (!"PENDING".equals(c.getApprovalStatus()))
            throw new IllegalStateException("Not awaiting approval");
        c.setApprovalStatus("APPROVED");
        c.setApprovedAt(java.time.LocalDateTime.now());
        return c;
    }

    @Transactional
    public Campaign reject(Long userId, Long id, String notes) {
        Campaign c = getForUser(userId, id);
        if (!"PENDING".equals(c.getApprovalStatus()))
            throw new IllegalStateException("Not awaiting approval");
        c.setApprovalStatus("REJECTED");
        c.setStatus("DRAFT");
        // Optionally persist notes in approval table (future enhancement)
        return c;
    }

    // Queue processing (basic)
    @Transactional
    public int processQueueBatch(java.util.function.BiConsumer<SendingQueueItem, Boolean> sender) {
        var items = sendingQueueRepository.findTop100ByStatusAndNextAttemptAtBeforeOrderByPriorityAscIdAsc("PENDING",
                java.time.LocalDateTime.now());
        java.util.Map<Long, Integer> sentPerCampaign = new java.util.HashMap<>();
        for (var it : items) {
            try {
                if (exceedsDailyQuota(it.getUserId())) {
                    continue;
                }
                sender.accept(it, Boolean.TRUE);
                it.setStatus("SENT");
                sentPerCampaign.merge(it.getCampaignId(), 1, Integer::sum);
                // update recipient status if exists
                // (recipient table may not reflect QUEUED -> SENT yet, simplistic approach to
                // find by email)
                // Skipping heavy query loops for performance; can batch later.
            } catch (TransientEmailSendException tex) {
                it.setAttempt(it.getAttempt() + 1);
                it.setLastError(tex.getMessage());
                if (it.getAttempt() >= it.getMaxAttempts()) {
                    it.setStatus("FAILED");
                } else {
                    long delaySeconds = (long) Math.min(300, Math.pow(2, it.getAttempt())); // exponential up to 5m
                    it.setNextAttemptAt(java.time.LocalDateTime.now().plusSeconds(delaySeconds));
                }
            } catch (PermanentEmailSendException pex) {
                it.setAttempt(it.getAttempt() + 1);
                it.setLastError(pex.getMessage());
                it.setStatus("FAILED");
            } catch (Exception ex) {
                it.setAttempt(it.getAttempt() + 1);
                it.setLastError(ex.getMessage());
                if (it.getAttempt() >= it.getMaxAttempts())
                    it.setStatus("FAILED");
                else
                    it.setNextAttemptAt(java.time.LocalDateTime.now().plusMinutes(2));
            }
        }
        // Basic metrics per campaign
        for (var entry : sentPerCampaign.entrySet()) {
            Long cid = entry.getKey();
            int count = entry.getValue();
            repo.findById(cid).ifPresent(c -> {
                c.setSentCount(c.getSentCount() + count);
                if (c.getSentCount() >= c.getTotalRecipients())
                    c.setStatus("SENT");
            });
            // Trigger queue top-up after sending a batch
            refillQueueIfNeeded(cid);
            // If A/B test exists and campaign not finished, evaluate interim winner
            // (placeholder rule: after 30% sent)
            evaluateAbWinnerIfEligible(cid);
        }
        return sentPerCampaign.values().stream().mapToInt(Integer::intValue).sum();
    }

    private boolean exceedsDailyQuota(Long userId) {
        int quota = subscriptionRepository.findByUserId(userId).map(sub -> {
            if (sub.getPlanType() == null)
                return 500;
            switch (sub.getPlanType()) {
                case FREE:
                    return 500;
                case PRO:
                    return 5000;
                case PREMIUM:
                    return 20000;
                default:
                    return 500;
            }
        }).orElse(300);
        java.time.LocalDateTime since = java.time.LocalDate.now().atStartOfDay();
        long sentToday = sendingQueueRepository.countByUserIdAndStatusAndCreatedAtAfter(userId, "SENT", since);
        return sentToday >= quota;
    }

    @Transactional
    protected void evaluateAbWinnerIfEligible(Long campaignId) {
        var variants = abRepo.findByCampaignId(campaignId);
        if (variants.isEmpty())
            return;
        repo.findById(campaignId).ifPresent(c -> {
            if (c.getTotalRecipients() == 0)
                return;
            // simple heuristic: once 30% of total recipients have been SENT across
            // variants, pick winner by open rate (fallback to click rate)
            if (c.getSentCount() < (int) (c.getTotalRecipients() * 0.30))
                return; // wait for sample size
            boolean alreadyPicked = variants.stream().anyMatch(v -> Boolean.TRUE.equals(v.getWinner()));
            if (alreadyPicked)
                return;
            CampaignABTest best = null;
            double bestScore = -1;
            for (var v : variants) {
                double openRate = v.getSentCount() == 0 ? 0 : (double) v.getOpenCount() / (double) v.getSentCount();
                double clickRate = v.getSentCount() == 0 ? 0 : (double) v.getClickCount() / (double) v.getSentCount();
                double composite = openRate * 0.7 + clickRate * 0.3;
                if (composite > bestScore) {
                    bestScore = composite;
                    best = v;
                }
            }
            if (best != null) {
                best.setWinner(true);
                allocateRemainingRecipientsToWinner(c.getId(), best.getVariantCode());
            }
        });
    }

    @Transactional
    protected void allocateRemainingRecipientsToWinner(Long campaignId, String winnerCode) {
        var remaining = recipientRepo.findTop500ByCampaignIdAndStatusOrderByIdAsc(campaignId, "PENDING");
        for (var r : remaining) {
            r.setVariantCode(winnerCode);
        }
    }

    public record SummaryAnalytics(int campaigns, int totalSent, int totalOpens, int totalClicks, double avgOpenRate,
            double avgCtr, Double totalRevenue, Double avgRoi) {
    }

    public SummaryAnalytics analyticsSummary(Long userId) {
        var campaigns = repo.findByUserId(userId);
        int totalSent = 0, totalOpens = 0, totalClicks = 0;
        double openRateAccum = 0, ctrAccum = 0;
        int rateCount = 0;
        double roiAccum = 0;
        int roiCount = 0;
        double revenueSum = 0;
        boolean anyRevenue = false;
        for (var c : campaigns) {
            totalSent += c.getSentCount();
            totalOpens += c.getOpenCount();
            totalClicks += c.getClickCount();
            if (c.getSentCount() > 0) {
                openRateAccum += (double) c.getOpenCount() / c.getSentCount();
                ctrAccum += (double) c.getClickCount() / c.getSentCount();
                rateCount++;
            }
            CampaignRoi roi = roiRepository.findByCampaignId(c.getId());
            if (roi != null && roi.getRevenueAmount() != null) {
                revenueSum += roi.getRevenueAmount();
                anyRevenue = true;
                double cost = estimateCostPerEmail(c.getUserId()) * Math.max(1, c.getSentCount());
                roiAccum += (roi.getRevenueAmount() - cost) / cost;
                roiCount++;
            }
        }
        double avgOpenRate = rateCount == 0 ? 0 : openRateAccum / rateCount;
        double avgCtr = rateCount == 0 ? 0 : ctrAccum / rateCount;
        Double avgRoi = roiCount == 0 ? null : roiAccum / roiCount;
        Double totalRevenue = anyRevenue ? revenueSum : null;
        return new SummaryAnalytics(campaigns.size(), totalSent, totalOpens, totalClicks, avgOpenRate, avgCtr,
                totalRevenue, avgRoi);
    }

    // Engagement tracking hooks (to be invoked by tracking pixel / click redirect
    // controllers later)
    @Transactional
    public void recordOpen(Long campaignId, String recipientEmail) {
        var rec = recipientRepo.findFirstByCampaignIdAndEmail(campaignId, recipientEmail);
        if (rec != null) {
            if (rec.getFirstOpenAt() == null) {
                rec.setFirstOpenAt(java.time.LocalDateTime.now());
                rec.setEngagementScore((rec.getEngagementScore() == null ? 0 : rec.getEngagementScore()) + 5);
                repo.findById(campaignId).ifPresent(c -> c.setOpenCount(c.getOpenCount() + 1));
            }
            // also attribute to variant if subject line variant used (not yet tracked per
            // recipient; future: store variantCode on recipient)
        }
    }

    @Transactional
    public void recordClick(Long campaignId, String recipientEmail) {
        var rec = recipientRepo.findFirstByCampaignIdAndEmail(campaignId, recipientEmail);
        if (rec != null) {
            if (rec.getFirstClickAt() == null) {
                rec.setFirstClickAt(java.time.LocalDateTime.now());
                rec.setEngagementScore((rec.getEngagementScore() == null ? 0 : rec.getEngagementScore()) + 10);
                repo.findById(campaignId).ifPresent(c -> c.setClickCount(c.getClickCount() + 1));
            }
        }
    }

    // Activate scheduled campaigns whose time has arrived
    @Transactional
    public int activateDueScheduledCampaigns() {
        var due = repo.findByStatusAndScheduledAtBefore("SCHEDULED", java.time.LocalDateTime.now());
        int activated = 0;
        for (var c : due) {
            c.setStatus("SENDING");
            seedRecipientsIfEmpty(c);
            enqueueInitialBatch(c);
            activated++;
        }
        return activated;
    }
}
