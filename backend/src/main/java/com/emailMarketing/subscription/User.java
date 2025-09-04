package com.emailMarketing.subscription;

import jakarta.persistence.*;

@Entity
@Table(name="users")
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, unique=true) private String username;
  @Column(nullable=false) private String password;
  @Column(nullable=false, unique=true) private String email;
  private String firstName; private String lastName; private String currency = "USD"; private String locale = "en-US";
  private boolean emailVerified = false; private boolean active = true; private boolean locked = false;
  @ElementCollection(fetch=FetchType.EAGER)
  @CollectionTable(name="user_roles", joinColumns=@JoinColumn(name="user_id"))
  @Column(name="role")
  private java.util.Set<String> roles = new java.util.HashSet<>();
  @OneToOne(mappedBy="user", cascade=CascadeType.ALL, fetch=FetchType.LAZY) private Subscription subscription;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getUsername(){return username;} public void setUsername(String u){this.username=u;}
  public String getPassword(){return password;} public void setPassword(String p){this.password=p;}
  public String getEmail(){return email;} public void setEmail(String e){this.email=e;}
  public String getFirstName(){return firstName;} public void setFirstName(String f){this.firstName=f;}
  public String getLastName(){return lastName;} public void setLastName(String l){this.lastName=l;}
  public String getCurrency(){return currency;} public void setCurrency(String c){this.currency=c;}
  public String getLocale(){return locale;} public void setLocale(String l){this.locale=l;}
  public boolean isEmailVerified(){return emailVerified;} public void setEmailVerified(boolean v){this.emailVerified=v;}
  public boolean isActive(){return active;} public void setActive(boolean a){this.active=a;}
  public boolean isLocked(){return locked;} public void setLocked(boolean l){this.locked=l;}
  public java.util.Set<String> getRoles(){return roles;} public void setRoles(java.util.Set<String> r){this.roles=r;}
  public Subscription getSubscription(){return subscription;} public void setSubscription(Subscription s){this.subscription=s;}
}
