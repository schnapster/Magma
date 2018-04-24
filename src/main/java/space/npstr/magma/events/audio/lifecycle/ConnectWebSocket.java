package space.npstr.magma.events.audio.lifecycle;

import org.immutables.value.Value;
import space.npstr.magma.immutables.ImmutableLcEvent;
import space.npstr.magma.immutables.SessionInfo;

/**
 * Created by napster on 24.04.18.
 */
@Value.Immutable
@ImmutableLcEvent
public abstract class ConnectWebSocket implements LifecycleEvent {

    @Override
    public String getUserId() {
        return this.getSessionInfo().getUserId();
    }

    @Override
    public String getGuildId() {
        return this.getSessionInfo().getVoiceServerUpdate().getGuildId();
    }

    public abstract SessionInfo getSessionInfo();

}
