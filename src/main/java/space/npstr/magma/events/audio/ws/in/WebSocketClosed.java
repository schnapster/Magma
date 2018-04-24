package space.npstr.magma.events.audio.ws.in;

import org.immutables.value.Value;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 22.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class WebSocketClosed implements InboundWsEvent {

    @Override
    public int getOpCode() {
        return OpCode.WEBSOCKET_CLOSE;
    }

    public abstract int getCode();

    public abstract String getReason();
}
