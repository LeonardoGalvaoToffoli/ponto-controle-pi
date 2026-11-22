package com.leonardogalvao.pontocontrole.employees;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, UUID> {

    boolean existsByRegistrationNumber(String registrationNumber);

    List<EmployeeProfile> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select employee from EmployeeProfile employee where employee.userId = :id")
    java.util.Optional<EmployeeProfile> findByIdForUpdate(@Param("id") UUID id);
}
