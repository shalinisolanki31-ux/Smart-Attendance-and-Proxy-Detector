package com.azuresight;

/**
 * What happened after one student tried to check in.
 */
public class CheckInResult {
    private boolean success;     // true = marked present
    private boolean proxyAlert;  // true = something looked fake
    private String message;

    public CheckInResult(boolean success, boolean proxyAlert, String message) {
        this.success = success;
        this.proxyAlert = proxyAlert;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public boolean isProxyAlert() { return proxyAlert; }
    public String getMessage() { return message; }
}
