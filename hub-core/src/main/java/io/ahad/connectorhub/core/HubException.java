package io.ahad.connectorhub.core;
/** Stable, localizable error code. Details must never contain credentials or upstream bodies. */
public final class HubException extends RuntimeException {
    private final String code;
    private final int status;
    private final boolean transientFailure;
    public HubException(String code, int status) {
        this(code, status, false);
    }
    public HubException(String code, int status, boolean transientFailure) {
        super(code);
        this.code=code;
        this.status=status;
        this.transientFailure=transientFailure;
    }
    public String code() {
        return code;
    }
    public int status() {
        return status;
    }
    public boolean transientFailure() {
        return transientFailure;
    }
    public static HubException invalid() {
        return new HubException("INVALID_INPUT",400);
    }
}
