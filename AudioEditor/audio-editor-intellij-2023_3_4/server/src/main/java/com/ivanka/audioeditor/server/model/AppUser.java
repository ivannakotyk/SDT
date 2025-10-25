package com.ivanka.audioeditor.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userName;

    @Column(unique = true, nullable = false)
    private String userEmail;

    public AppUser() {}

    public AppUser(String userName, String userEmail) {
        this.userName = userName;
        this.userEmail = userEmail;
    }

    public Long getId() { return id; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
}