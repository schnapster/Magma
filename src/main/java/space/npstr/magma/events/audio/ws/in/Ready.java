package space.npstr.magma.events.audio.ws.in;

import org.immutables.value.Value;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 21.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Ready implements InboundWsEvent {

    @Override
    public int getOpCode() {
        return OpCode.READY;
    }

    /**
     * @return our ssrc
     */
    public abstract int getSsrc();

    /**
     * @return the udp port that we should connect our udp connection to
     */
    public abstract int getPort();
}
