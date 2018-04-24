package space.npstr.magma.events.audio.ws.in;

import org.immutables.value.Value;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 24.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Ignored implements InboundWsEvent {

    @Override
    public abstract int getOpCode();
}
