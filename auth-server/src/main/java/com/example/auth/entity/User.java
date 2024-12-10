package com.example.auth.entity;

import javax.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(length = 20)
    private String userId;

    @Column(length = 88)
    private String userToken;

    @Column(length = 8)
    private String dailyTrafficLimit;

    @Column(length = 8)
    private String dailyTrafficUsage;

    @Column(length = 8)
    private String expirationDate;

    @Column(columnDefinition = "boolean default false")
    private boolean isMember;

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUserToken() { return userToken; }
    public void setUserToken(String userToken) { this.userToken = userToken; }
    
    public String getDailyTrafficLimit() { return dailyTrafficLimit; }
    public void setDailyTrafficLimit(String dailyTrafficLimit) { this.dailyTrafficLimit = dailyTrafficLimit; }
    
    public String getDailyTrafficUsage() { return dailyTrafficUsage; }
    public void setDailyTrafficUsage(String dailyTrafficUsage) { this.dailyTrafficUsage = dailyTrafficUsage; }
    
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
    
    public boolean isMember() { return isMember; }
    public void setMember(boolean member) { isMember = member; }
}
