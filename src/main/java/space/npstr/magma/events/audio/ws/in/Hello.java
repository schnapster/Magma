package space.npstr.magma.events.audio.ws.in;

import org.immutables.value.Value;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 20.04.18.
 * <p>
 * Received the heartbeat interval
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Hello implements InboundWsEvent {

    @Override
    public int getOpCode() {
        return OpCode.HELLO;
    }

    /**
     * @return The heartbeat interval that we received from Discord's Hello event (op 8)
     */
    public abstract int getHeartbeatIntervalMillis();
}
