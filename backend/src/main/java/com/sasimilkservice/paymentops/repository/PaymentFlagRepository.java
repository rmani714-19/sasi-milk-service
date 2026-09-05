package com.sasimilkservice.paymentops.repository;

import com.sasimilkservice.paymentops.domain.PaymentFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentFlagRepository extends JpaRepository<PaymentFlag, Long> {

    Optional<PaymentFlag> findBySubjectTypeAndSubjectIdAndStatus(
            PaymentFlag.SubjectType subjectType, Long subjectId, PaymentFlag.Status status);

    List<PaymentFlag> findByStatusOrderByIdDesc(PaymentFlag.Status status);

    List<PaymentFlag> findBySubjectTypeAndSubjectIdOrderByIdDesc(PaymentFlag.SubjectType subjectType, Long subjectId);
}
