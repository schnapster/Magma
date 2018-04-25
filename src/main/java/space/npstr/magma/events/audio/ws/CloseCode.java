package space.npstr.magma.events.audio.ws;

public final class CloseCode {

    public static final int HEARTBEAT_TIMEOUT = 1000; //according to jda-audio

    // https://discordapp.com/developers/docs/topics/opcodes-and-status-codes#voice-voice-close-event-codes
    public static final int UNKNOWN_OP_CODE = 4001;
    public static final int NOT_AUTHENTICATED = 4003;
    public static final int AUTHENTICATION_FAILED = 4004;
    public static final int ALREADY_AUTHENTICATED = 4005;
    public static final int SESSION_NO_LONGER_VALID = 4006;
    public static final int SESSION_TIMEOUT = 4009;
    public static final int SERVER_NOT_FOUND = 4011;
    public static final int UNKNOWN_PROTOCOL = 4012;
    public static final int DISCONNECTED = 4014;
    public static final int VOICE_SERVER_CRASHED = 4015;
    public static final int UNKNOWN_ENCRYPTION_MODE = 4016;

}
