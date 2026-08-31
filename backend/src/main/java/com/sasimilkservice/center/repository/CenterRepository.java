package com.sasimilkservice.center.repository;

import com.sasimilkservice.center.domain.Center;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CenterRepository extends JpaRepository<Center, Long> {

    Optional<Center> findTopByOrderByIdAsc();
}
