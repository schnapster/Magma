package space.npstr.magma.events.audio.ws;

public final class OpCode {

    // https://discordapp.com/developers/docs/topics/opcodes-and-status-codes#voice-opcodes
    public static final int IDENTIFY = 0;
    public static final int SELECT_PROTOCOL = 1;
    public static final int READY = 2;
    public static final int HEARTBEAT = 3;
    public static final int SESSION_DESCRIPTION = 4;
    public static final int SPEAKING = 5;
    public static final int HEARTBEAT_ACK = 6;
    public static final int RESUME = 7;
    public static final int HELLO = 8;
    public static final int RESUMED = 9;
    public static final int NO_IDEA = 12;                   //this one is not documented, but we do receive it
    public static final int CLIENT_DISCONNECT = 13;

    // Custom codes
    public static final int WEBSOCKET_CLOSE = 9001;
}
