package com.yourdomain.subscription;

import jakarta.persistence.*;

@Entity
@Table(name="users")
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, unique=true) private String username;
  @Column(nullable=false) private String password;
  @Column(nullable=false, unique=true) private String email;
  private String firstName; private String lastName; private String currency = "USD"; private String locale = "en-US";
  @OneToOne(mappedBy="user", cascade=CascadeType.ALL, fetch=FetchType.LAZY) private Subscription subscription;
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public String getUsername(){return username;} public void setUsername(String u){this.username=u;}
  public String getPassword(){return password;} public void setPassword(String p){this.password=p;}
  public String getEmail(){return email;} public void setEmail(String e){this.email=e;}
  public String getFirstName(){return firstName;} public void setFirstName(String f){this.firstName=f;}
  public String getLastName(){return lastName;} public void setLastName(String l){this.lastName=l;}
  public String getCurrency(){return currency;} public void setCurrency(String c){this.currency=c;}
  public String getLocale(){return locale;} public void setLocale(String l){this.locale=l;}
  public Subscription getSubscription(){return subscription;} public void setSubscription(Subscription s){this.subscription=s;}
}
