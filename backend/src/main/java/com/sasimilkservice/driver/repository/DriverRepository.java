package com.sasimilkservice.driver.repository;

import com.sasimilkservice.driver.domain.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    List<Driver> findAllByOrderByNameAsc();

    List<Driver> findByStatusOrderByNameAsc(Driver.Status status);

    boolean existsByCodeIgnoreCase(String code);

    @Query(value = "select nextval('driver_number_seq')", nativeQuery = true)
    long nextDriverNumberValue();
}
