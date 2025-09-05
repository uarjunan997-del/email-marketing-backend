package com.emailMarketing.tracking;

import com.emailMarketing.campaign.CampaignService;
import com.emailMarketing.campaign.CampaignRecipientRepository;
import com.emailMarketing.campaign.CampaignABTestRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/track")
public class TrackingController {
    private final CampaignService campaignService;
    public TrackingController(CampaignService campaignService, CampaignRecipientRepository recipientRepository, CampaignABTestRepository abRepo){ this.campaignService=campaignService; }

    // 1x1 transparent gif (base64) \n
    private static final byte[] PIXEL = new byte[]{
            (byte)0x47,(byte)0x49,(byte)0x46,(byte)0x38,(byte)0x39,(byte)0x61,(byte)0x01,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x80,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0x21,(byte)0xf9,(byte)0x04,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x2c,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x02,(byte)0x02,(byte)0x44,(byte)0x01,(byte)0x00,(byte)0x3b
    };

    @GetMapping(value="/open")
    public ResponseEntity<byte[]> open(@RequestParam("c") Long campaignId, @RequestParam("r") String recipient){
        campaignService.recordOpen(campaignId, recipient);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, proxy-revalidate")
                .body(PIXEL);
    }

    @GetMapping("/click")
    public ResponseEntity<Void> click(@RequestParam("c") Long campaignId, @RequestParam("r") String recipient, @RequestParam("u") String target){
        campaignService.recordClick(campaignId, recipient);
        // Basic open redirect safeguard: only allow http/https and limit length
        if(target==null || target.length()>2000 || !(target.startsWith("http://") || target.startsWith("https://"))){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
