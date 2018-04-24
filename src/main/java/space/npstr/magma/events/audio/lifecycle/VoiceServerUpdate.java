package space.npstr.magma.events.audio.lifecycle;

import org.immutables.value.Value;
import space.npstr.magma.immutables.ImmutableLcEvent;

/**
 * Created by napster on 22.04.18.
 */
@Value.Immutable
@ImmutableLcEvent
public abstract class VoiceServerUpdate implements LifecycleEvent {

    public abstract String getSessionId();

    @Override
    public abstract String getGuildId();

    @Override
    public abstract String getUserId();

    public abstract String getEndpoint();

    public abstract String getToken();
}
