package com.sasimilkservice.paymentops.repository;

import com.sasimilkservice.paymentops.domain.BankBalanceCheck;
import com.sasimilkservice.paymentops.domain.BankBalanceCheck.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankBalanceCheckRepository extends JpaRepository<BankBalanceCheck, Long> {

    Optional<BankBalanceCheck> findFirstBySubjectTypeAndSubjectIdOrderByIdDesc(SubjectType subjectType, Long subjectId);
}
