package com.leonardogalvao.pontocontrole.employees;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, UUID> {

    boolean existsByRegistrationNumber(String registrationNumber);

    List<EmployeeProfile> findAllByOrderByNameAsc();
}
