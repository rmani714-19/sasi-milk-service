package com.sasimilkservice.producer.api.dto;

import com.sasimilkservice.producer.domain.Producer;
import com.sasimilkservice.producer.domain.ProducerBankAccount;
import com.sasimilkservice.producer.service.ProducerService.AddBankAccountCommand;
import com.sasimilkservice.producer.service.ProducerService.CreateProducerCommand;
import com.sasimilkservice.producer.service.ProducerService.UpdateProducerCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** Producer + bank-account API payloads. Full account numbers are never returned. */
public final class ProducerDtos {

    private ProducerDtos() {}

    private static final String IFSC = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$";
    private static final String MOBILE = "^[0-9]{10}$";

    public record CreateProducerRequest(
            @NotBlank String producerName,
            @Pattern(regexp = MOBILE, message = "must be a 10-digit mobile number") String mobileNumber,
            @Email String email,
            String address,
            Long regionId,
            LocalDate joiningDate,
            String notes) {

        public CreateProducerCommand toCommand() {
            return new CreateProducerCommand(producerName, mobileNumber, email, address,
                    regionId, joiningDate, notes);
        }
    }

    public record UpdateProducerRequest(
            @NotBlank String producerName,
            @Pattern(regexp = MOBILE, message = "must be a 10-digit mobile number") String mobileNumber,
            @Email String email,
            String address,
            Long regionId,
            String regionChangeReason,
            String notes) {

        public UpdateProducerCommand toCommand() {
            return new UpdateProducerCommand(producerName, mobileNumber, email, address,
                    regionId, regionChangeReason, notes);
        }
    }

    public record CreateBankAccountRequest(
            @NotBlank String accountHolderName,
            @NotBlank String accountNumber,
            @Pattern(regexp = IFSC, message = "invalid IFSC code") String ifscCode,
            String bankName,
            String bankBranch) {

        public AddBankAccountCommand toCommand() {
            return new AddBankAccountCommand(accountHolderName, accountNumber, ifscCode, bankName, bankBranch);
        }
    }

    public record ProducerResponse(
            Long id,
            String publicId,
            String producerNumber,
            String producerName,
            String mobileNumber,
            String email,
            String address,
            Long regionId,
            LocalDate joiningDate,
            String status,
            String qrCode) {

        public static ProducerResponse from(Producer p) {
            return new ProducerResponse(p.getId(), p.getPublicId().toString(), p.getProducerNumber(),
                    p.getProducerName(), p.getMobileNumber(), p.getEmail(), p.getAddress(),
                    p.getRegionId(), p.getJoiningDate(), p.getStatus().name(), p.getQrCode());
        }
    }

    public record BankAccountResponse(
            Long id,
            String accountHolderName,
            String accountNumberMasked,
            String ifscCode,
            String bankName,
            String bankBranch,
            boolean primary,
            String status) {

        public static BankAccountResponse from(ProducerBankAccount a) {
            return new BankAccountResponse(a.getId(), a.getAccountHolderName(), a.masked(),
                    a.getIfscCode(), a.getBankName(), a.getBankBranch(), a.isPrimary(),
                    a.getStatus().name());
        }
    }
}
