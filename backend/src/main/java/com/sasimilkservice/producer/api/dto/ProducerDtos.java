package com.sasimilkservice.producer.api.dto;

import com.sasimilkservice.producer.domain.Producer;
import com.sasimilkservice.producer.domain.ProducerBankAccount;
import com.sasimilkservice.producer.service.ProducerService.AddBankAccountCommand;
import com.sasimilkservice.producer.service.ProducerService.CreateProducerCommand;
import com.sasimilkservice.producer.service.ProducerService.UpdateProducerCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** Producer + bank-account API payloads. Full account / Aadhaar numbers are never returned. */
public final class ProducerDtos {

    private ProducerDtos() {}

    private static final String IFSC = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$";
    private static final String MOBILE = "^[0-9]{10}$";
    private static final String AADHAAR = "^[0-9]{12}$";

    public record BankBlock(
            @NotBlank String accountHolderName,
            @NotBlank String accountNumber,
            @NotBlank String bankName,
            String bankBranch,
            @Pattern(regexp = IFSC, message = "invalid IFSC code") @NotBlank String ifscCode) {

        AddBankAccountCommand toCommand() {
            return new AddBankAccountCommand(accountHolderName, accountNumber, ifscCode, bankName, bankBranch);
        }
    }

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
            @NotNull(message = "bank details are required") @Valid BankBlock bank) {

        public CreateProducerCommand toCommand() {
            return new CreateProducerCommand(producerName, mobileNumber, alternateMobile,
                    email, address, upiId, aadhaarNumber, regionId, joiningDate, notes,
                    bank == null ? null : bank.toCommand());
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
            String notes) {

        public UpdateProducerCommand toCommand() {
            return new UpdateProducerCommand(producerName, mobileNumber, alternateMobile, email,
                    address, upiId, aadhaarNumber, regionId, regionChangeReason, notes);
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
            String alternateMobile,
            String email,
            String address,
            String upiId,
            String aadhaarMasked,
            Long regionId,
            LocalDate joiningDate,
            String status,
            String qrCode) {

        public static ProducerResponse from(Producer p) {
            return new ProducerResponse(p.getId(), p.getPublicId().toString(), p.getProducerNumber(),
                    p.getProducerName(), p.getMobileNumber(), p.getAlternateMobile(), p.getEmail(),
                    p.getAddress(), p.getUpiId(),
                    p.getAadhaarLast4() == null ? null : "XXXXXXXX" + p.getAadhaarLast4(),
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
