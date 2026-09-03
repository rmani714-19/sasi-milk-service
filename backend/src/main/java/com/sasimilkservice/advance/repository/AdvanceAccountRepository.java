package com.sasimilkservice.advance.repository;

import com.sasimilkservice.advance.domain.AdvanceAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvanceAccountRepository extends JpaRepository<AdvanceAccount, Long> {

    List<AdvanceAccount> findByProducerIdOrderByAdvanceDateAscIdAsc(Long producerId);

    List<AdvanceAccount> findAllByOrderByAdvanceDateAscIdAsc();

    List<AdvanceAccount> findByStatus(AdvanceAccount.Status status);

    List<AdvanceAccount> findByProducerIdAndStatusOrderByAdvanceDateAscIdAsc(
            Long producerId, AdvanceAccount.Status status);
}
