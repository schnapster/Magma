package space.npstr.magma.events.api;

import space.npstr.magma.Member;
import space.npstr.magma.events.audio.ws.CloseCode;

import javax.annotation.Nullable;

/**
 * This event is fired when an audio web socket is closed. However, this event is not fired if we are trying to
 * resume (i.e reconnect) automatically unless resuming causes a new socket to close.
 */
@SuppressWarnings("unused")
public class WebSocketClosedEvent implements MagmaEvent {

    private final Member member;
    private final int closeCode;
    private final CloseCode closeCodeEnum;
    private final String reason;
    private final boolean byRemote;

    public WebSocketClosedEvent(Member member, int closeCode, String reason, boolean byRemote) {
        this.member = member;
        this.closeCode = closeCode;
        closeCodeEnum = CloseCode.parse(closeCode).orElse(null);
        this.reason = reason;
        this.byRemote = byRemote;
    }

    public Member getMember() {
        return member;
    }

    public int getCloseCode() {
        return closeCode;
    }

    @Nullable
    public CloseCode getCloseCodeEnum() {
        return closeCodeEnum;
    }

    public String getReason() {
        return reason;
    }

    public boolean isByRemote() {
        return byRemote;
    }
}
