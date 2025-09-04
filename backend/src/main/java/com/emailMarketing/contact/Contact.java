package com.emailMarketing.contact;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="contacts", indexes = {@Index(name="idx_contacts_user", columnList="user_id"), @Index(name="idx_contacts_email", columnList="email")},
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","email"}))
public class Contact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(nullable=false, length=320) private String email;
    private String firstName; private String lastName; private String segment; private boolean unsubscribed=false;
    private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
    public String getFirstName(){return firstName;} public void setFirstName(String firstName){this.firstName=firstName;}
    public String getLastName(){return lastName;} public void setLastName(String lastName){this.lastName=lastName;}
    public String getSegment(){return segment;} public void setSegment(String segment){this.segment=segment;}
    public boolean isUnsubscribed(){return unsubscribed;} public void setUnsubscribed(boolean unsubscribed){this.unsubscribed=unsubscribed;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
