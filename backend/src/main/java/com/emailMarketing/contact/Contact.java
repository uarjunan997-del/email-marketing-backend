package com.emailMarketing.contact;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="contacts",
       indexes = {
           @Index(name="idx_contacts_user", columnList="user_id"),
           @Index(name="idx_contacts_email_lower", columnList="email"),
           @Index(name="idx_contacts_segment", columnList="segment")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","email"}))
public class Contact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(nullable=false, length=320) private String email;
    @Column(length=200) private String firstName;
    @Column(length=200) private String lastName;
    @Column(length=200) private String segment;
    @Column(length=50) private String phone;
    @Column(length=100) private String country;
    @Column(length=150) private String city;
    @Column(name="unsubscribed") private boolean unsubscribed=false;
    @Column(name="suppressed") private boolean suppressed=false;
    @Lob @Column(name="custom_fields") private String customFields; // store JSON as CLOB
    @Column(name="is_deleted") private boolean isDeleted=false;
    @Column(name="delete_requested_at") private LocalDateTime deleteRequestedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
    public String getFirstName(){return firstName;} public void setFirstName(String firstName){this.firstName=firstName;}
    public String getLastName(){return lastName;} public void setLastName(String lastName){this.lastName=lastName;}
    public String getSegment(){return segment;} public void setSegment(String segment){this.segment=segment;}
    public String getPhone(){return phone;} public void setPhone(String phone){this.phone=phone;}
    public String getCountry(){return country;} public void setCountry(String country){this.country=country;}
    public String getCity(){return city;} public void setCity(String city){this.city=city;}
    public boolean isUnsubscribed(){return unsubscribed;} public void setUnsubscribed(boolean unsubscribed){this.unsubscribed=unsubscribed;}
    public boolean isSuppressed(){return suppressed;} public void setSuppressed(boolean suppressed){this.suppressed=suppressed;}
    public String getCustomFields(){return customFields;} public void setCustomFields(String customFields){this.customFields=customFields;}
    public boolean isDeleted(){return isDeleted;} public void setDeleted(boolean deleted){isDeleted = deleted;}
    public LocalDateTime getDeleteRequestedAt(){return deleteRequestedAt;} public void setDeleteRequestedAt(LocalDateTime t){this.deleteRequestedAt=t;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
