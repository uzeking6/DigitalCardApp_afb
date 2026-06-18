package com.afriland.approval.model;

import jakarta.persistence.*;

@Entity
@Table(name = "smtp_config")
public class SmtpConfig {
    @Id private Long id = 1L;
    private boolean enabled = false;
    private String host = "smtp.gmail.com";
    private int port = 587;
    private String protocol = "STARTTLS";
    private String username = "";
    private String password = "";
    @Column(name = "from_email") private String fromEmail = "";
    @Column(name = "from_name") private String fromName = "Afriland First Bank RH";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }
    public int getPort() { return port; }
    public void setPort(int v) { this.port = v; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String v) { this.protocol = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String v) { this.fromEmail = v; }
    public String getFromName() { return fromName; }
    public void setFromName(String v) { this.fromName = v; }
}
