package com.emailMarketing.attribution;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="email_interactions")
public class EmailInteraction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private Long campaignId; private Long contactId; private String email; private String channel; private String eventType; private LocalDateTime eventTime; private String variantCode; private String utmSource; private String utmMedium; private String utmCampaign; private String userAgent; private String ipAddress;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public Long getCampaignId(){return campaignId;} public void setCampaignId(Long campaignId){this.campaignId=campaignId;}
    public Long getContactId(){return contactId;} public void setContactId(Long contactId){this.contactId=contactId;}
    public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
    public String getChannel(){return channel;} public void setChannel(String channel){this.channel=channel;}
    public String getEventType(){return eventType;} public void setEventType(String eventType){this.eventType=eventType;}
    public LocalDateTime getEventTime(){return eventTime;} public void setEventTime(LocalDateTime eventTime){this.eventTime=eventTime;}
    public String getVariantCode(){return variantCode;} public void setVariantCode(String variantCode){this.variantCode=variantCode;}
    public String getUtmSource(){return utmSource;} public void setUtmSource(String utmSource){this.utmSource=utmSource;}
    public String getUtmMedium(){return utmMedium;} public void setUtmMedium(String utmMedium){this.utmMedium=utmMedium;}
    public String getUtmCampaign(){return utmCampaign;} public void setUtmCampaign(String utmCampaign){this.utmCampaign=utmCampaign;}
    public String getUserAgent(){return userAgent;} public void setUserAgent(String userAgent){this.userAgent=userAgent;}
    public String getIpAddress(){return ipAddress;} public void setIpAddress(String ipAddress){this.ipAddress=ipAddress;}
}
