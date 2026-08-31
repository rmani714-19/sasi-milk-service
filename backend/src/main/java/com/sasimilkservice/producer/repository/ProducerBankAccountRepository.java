package com.sasimilkservice.producer.repository;

import com.sasimilkservice.producer.domain.ProducerBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProducerBankAccountRepository extends JpaRepository<ProducerBankAccount, Long> {

    List<ProducerBankAccount> findByProducerIdOrderByIdDesc(Long producerId);

    Optional<ProducerBankAccount> findByProducerIdAndPrimaryTrueAndStatus(
            Long producerId, ProducerBankAccount.Status status);
}
