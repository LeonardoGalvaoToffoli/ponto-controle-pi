package com.leonardogalvao.pontocontrole.employees;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "employees")
public class EmployeeProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "registration_number", nullable = false, unique = true, length = 30)
    private String registrationNumber;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "job_title", nullable = false, length = 80)
    private String jobTitle;

    @Column(name = "profile_photo_key", nullable = false, length = 255)
    private String profilePhotoKey;

    protected EmployeeProfile() {
    }
}
