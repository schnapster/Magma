package space.npstr.magma.events.audio.ws.in;

import org.immutables.value.Value;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 20.04.18.
 * <p>
 * Unknown event received
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Unknown implements InboundWsEvent {

    public abstract String getPayload();
}
