package com.sasimilkservice.payout.provider.razorpayx;

/** A non-2xx response (or transport failure) from the RazorpayX API. */
public class RazorpayXException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final boolean retryable;

    public RazorpayXException(int httpStatus, String code, String description) {
        super("RazorpayX " + httpStatus + " " + code + ": " + description);
        this.httpStatus = httpStatus;
        this.code = code;
        RazorpayXErrorMapper.MappedError m = RazorpayXErrorMapper.map(httpStatus, code, description);
        this.retryable = m.retryable();
    }

    public RazorpayXException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
        this.code = "TRANSPORT_ERROR";
        this.retryable = true; // an unknown transport failure is retried through fetchStatus, never assumed failed
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
