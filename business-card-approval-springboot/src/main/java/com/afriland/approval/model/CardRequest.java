package com.afriland.approval.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "requests")
public class CardRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name") private String firstName;
    @Column(name = "last_name") private String lastName;
    private String email;
    private String mobile;
    private String phone;
    private String fax;
    @Column(name = "department_fr") private String departmentFr;
    @Column(name = "department_en") private String departmentEn;
    @Column(name = "job_title_fr") private String jobTitleFr;
    @Column(name = "job_title_en") private String jobTitleEn;
    private String title;
    @Column(nullable = false) private String status = "Pending";
    @Column(name = "reject_reason") private String rejectReason;
    @Column(name = "submitted_at") private LocalDateTime submittedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getMobile() { return mobile; }
    public void setMobile(String v) { this.mobile = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getFax() { return fax; }
    public void setFax(String v) { this.fax = v; }
    public String getDepartmentFr() { return departmentFr; }
    public void setDepartmentFr(String v) { this.departmentFr = v; }
    public String getDepartmentEn() { return departmentEn; }
    public void setDepartmentEn(String v) { this.departmentEn = v; }
    public String getJobTitleFr() { return jobTitleFr; }
    public void setJobTitleFr(String v) { this.jobTitleFr = v; }
    public String getJobTitleEn() { return jobTitleEn; }
    public void setJobTitleEn(String v) { this.jobTitleEn = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String v) { this.rejectReason = v; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime v) { this.submittedAt = v; }
    public String getFullName() { return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""); }
}
