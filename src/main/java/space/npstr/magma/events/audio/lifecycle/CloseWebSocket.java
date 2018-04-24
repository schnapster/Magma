package space.npstr.magma.events.audio.lifecycle;

import org.immutables.value.Value;
import space.npstr.magma.immutables.ImmutableLcEvent;

/**
 * Created by napster on 24.04.18.
 */
@Value.Immutable
@ImmutableLcEvent
public abstract class CloseWebSocket implements LifecycleEvent {

    @Override
    public abstract String getUserId();

    @Override
    public abstract String getGuildId();

}
