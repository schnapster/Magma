package space.npstr.magma.events.audio.ws;

/**
 * Whole class taken almost as-is from jda-audio (Apache 2.0)
 */
public enum CloseCode {
    HEARTBEAT_TIMEOUT(1000, "We did not heartbeat in time"),
    UNKNOWN_OP_CODE(4001, "Sent an invalid op code"),
    NOT_AUTHENTICATED(4003, "Tried to send payload before authenticating session"),
    AUTHENTICATION_FAILED(4004, "The token sent in the identify payload is incorrect"),
    ALREADY_AUTHENTICATED(4005, "Tried to authenticate when already authenticated"),
    INVALID_SESSION(4006, "The session with which we attempted to resume is invalid"),
    SESSION_TIMEOUT(4009, "Heartbeat timed out"),
    SERVER_NOT_FOUND(4011, "The server we attempted to connect to was not found"),
    UNKNOWN_PROTOCOL(4012, "The selected protocol is not supported"),
    DISCONNECTED(4014, "The connection has been dropped normally"),
    SERVER_CRASH(4015, "The server we were connected to has crashed"),
    UNKNOWN_ENCRYPTION_MODE(4016, "The specified encryption method is not supported"),

    UNKNOWN(0, "Unknown code");

    private final int code;
    private final String meaning;

    CloseCode(final int code, final String meaning) {
        this.code = code;
        this.meaning = meaning;
    }

    public static CloseCode from(final int code) {
        for (final CloseCode c : CloseCode.values()) {
            if (c.code == code)
                return c;
        }
        return CloseCode.UNKNOWN;
    }

    public int getCode() {
        return this.code;
    }

    public String getMeaning() {
        return this.meaning;
    }
}
