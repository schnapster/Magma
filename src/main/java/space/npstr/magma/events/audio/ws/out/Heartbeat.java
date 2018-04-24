package space.npstr.magma.events.audio.ws.out;

import org.immutables.value.Value;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 21.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Heartbeat implements OutboundWsEvent {

    public abstract int getNonce();

    @Override
    public int getOpCode() {
        return OpCode.HEARTBEAT;
    }

    @Override
    public Integer getData() {
        return this.getNonce();
    }
}
