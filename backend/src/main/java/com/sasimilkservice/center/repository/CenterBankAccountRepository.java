package com.sasimilkservice.center.repository;

import com.sasimilkservice.center.domain.CenterBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CenterBankAccountRepository extends JpaRepository<CenterBankAccount, Long> {

    List<CenterBankAccount> findAllByOrderByPrimaryDescIdAsc();

    List<CenterBankAccount> findByStatusAndVerifiedTrueOrderByPrimaryDescIdAsc(CenterBankAccount.Status status);

    Optional<CenterBankAccount> findByPrimaryTrueAndStatus(CenterBankAccount.Status status);
}
