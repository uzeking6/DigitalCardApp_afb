package com.afriland.approval.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_setting")
public class AppSetting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "setting_key", unique = true, nullable = false) private String settingKey;
    @Column(name = "setting_value") private String settingValue;

    public Long getId() { return id; }
    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String k) { this.settingKey = k; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String v) { this.settingValue = v; }
}
