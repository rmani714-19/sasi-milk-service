package com.sasimilkservice.producer.spi;

import java.util.Optional;

/** Published SPI of the {@code producer} module. */
public interface ProducerDirectory {

    Optional<ProducerView> findById(Long id);

    /** Resolve by producer number, QR token, or public id — used by milk collection. */
    Optional<ProducerView> resolve(String reference);

    /** Name + contact channels — used by notifications. */
    Optional<ProducerContact> contact(Long producerId);

    record ProducerView(
            Long id,
            String publicId,
            String producerNumber,
            String name,
            Long regionId,
            boolean active) {
    }

    record ProducerContact(Long id, String name, String mobileNumber, String email) {
    }
}
