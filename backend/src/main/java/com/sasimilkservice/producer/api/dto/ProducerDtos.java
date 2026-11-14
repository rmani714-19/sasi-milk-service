package com.sasimilkservice.producer.api.dto;

import com.sasimilkservice.producer.domain.Producer;
import com.sasimilkservice.producer.service.ProducerService.CreateProducerCommand;
import com.sasimilkservice.producer.service.ProducerService.UpdateProducerCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** Producer API payloads. Full account / Aadhaar numbers are never returned. */
public final class ProducerDtos {

    private ProducerDtos() {}

    private static final String MOBILE = "^[0-9]{10}$";
    private static final String AADHAAR = "^[0-9]{12}$";
    private static final String FREQUENCY = "^(WEEKLY|MONTHLY)$";

    public record CreateProducerRequest(
            @NotBlank String producerName,
            @Pattern(regexp = MOBILE, message = "must be a 10-digit mobile number") String mobileNumber,
            @Pattern(regexp = MOBILE, message = "must be a 10-digit mobile number") String alternateMobile,
            @Email String email,
            String address,
            String upiId,
            @Pattern(regexp = AADHAAR, message = "Aadhaar must be 12 digits") String aadhaarNumber,
            Long regionId,
            LocalDate joiningDate,
            String notes,
            @Pattern(regexp = FREQUENCY, message = "paymentFrequency must be WEEKLY or MONTHLY") String paymentFrequency) {

        public CreateProducerCommand toCommand() {
            return new CreateProducerCommand(producerName, mobileNumber, alternateMobile,
                    email, address, upiId, aadhaarNumber, regionId, joiningDate, notes,
                    paymentFrequency);
        }
    }

    public record UpdateProducerRequest(
            @NotBlank String producerName,
            @Pattern(regexp = MOBILE, message = "must be a 10-digit mobile number") String mobileNumber,
            @Pattern(regexp = MOBILE, message = "must be a 10-digit mobile number") String alternateMobile,
            @Email String email,
            String address,
            String upiId,
            @Pattern(regexp = AADHAAR, message = "Aadhaar must be 12 digits") String aadhaarNumber,
            Long regionId,
            String regionChangeReason,
            String notes,
            @Pattern(regexp = FREQUENCY, message = "paymentFrequency must be WEEKLY or MONTHLY") String paymentFrequency) {

        public UpdateProducerCommand toCommand() {
            return new UpdateProducerCommand(producerName, mobileNumber, alternateMobile, email,
                    address, upiId, aadhaarNumber, regionId, regionChangeReason, notes,
                    paymentFrequency);
        }
    }

    public record ProducerResponse(
            Long id,
            String publicId,
            String producerNumber,
            String producerName,
            String mobileNumber,
            String alternateMobile,
            String email,
            String address,
            String upiId,
            String aadhaarMasked,
            Long regionId,
            LocalDate joiningDate,
            String status,
            String paymentFrequency,
            String qrCode) {

        public static ProducerResponse from(Producer p) {
            return new ProducerResponse(p.getId(), p.getPublicId().toString(), p.getProducerNumber(),
                    p.getProducerName(), p.getMobileNumber(), p.getAlternateMobile(), p.getEmail(),
                    p.getAddress(), p.getUpiId(),
                    p.getAadhaarLast4() == null ? null : "XXXXXXXX" + p.getAadhaarLast4(),
                    p.getRegionId(), p.getJoiningDate(), p.getStatus().name(),
                    p.getPaymentFrequency().name(), p.getQrCode());
        }
    }
}
