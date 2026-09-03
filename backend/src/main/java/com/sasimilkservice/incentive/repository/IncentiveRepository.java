package com.sasimilkservice.incentive.repository;

import com.sasimilkservice.incentive.domain.Incentive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncentiveRepository extends JpaRepository<Incentive, Long> {

    List<Incentive> findAllByOrderByCreatedAtDescIdDesc();
}
