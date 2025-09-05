package com.emailMarketing.timeseries;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="admin_backfill_audit")
public class AdminBackfillAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Instant triggeredAt; private String adminUsername; private Long userId; private Instant startInstant; private Instant endInstant; private Long windowHours; private String status; @Column(length=4000) private String message;
    @PrePersist void pre(){ if(triggeredAt==null) triggeredAt=Instant.now(); }
    public Long getId(){return id;} public Instant getTriggeredAt(){return triggeredAt;} public void setTriggeredAt(Instant t){this.triggeredAt=t;}
    public String getAdminUsername(){return adminUsername;} public void setAdminUsername(String a){this.adminUsername=a;}
    public Long getUserId(){return userId;} public void setUserId(Long u){this.userId=u;}
    public Instant getStartInstant(){return startInstant;} public void setStartInstant(Instant s){this.startInstant=s;}
    public Instant getEndInstant(){return endInstant;} public void setEndInstant(Instant e){this.endInstant=e;}
    public Long getWindowHours(){return windowHours;} public void setWindowHours(Long w){this.windowHours=w;}
    public String getStatus(){return status;} public void setStatus(String s){this.status=s;}
    public String getMessage(){return message;} public void setMessage(String m){this.message=m;}
}