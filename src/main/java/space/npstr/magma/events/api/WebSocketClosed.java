package space.npstr.magma.events.api;

import org.immutables.value.Value;
import space.npstr.magma.Member;
import space.npstr.magma.immutables.ImmutableApiEvent;

/**
 * This event is fired when an audio web socket is closed. However, this event is not fired if we are trying to
 * resume (i.e reconnect) automatically unless resuming causes a new socket to close.
 */
@SuppressWarnings("unused")
@Value.Immutable
@ImmutableApiEvent
public abstract class WebSocketClosed implements MagmaEvent {

    public abstract Member getMember();

    public abstract int getCloseCode();

    public abstract String getReason();

    public abstract boolean isByRemote();
}
