package space.npstr.magma.events.audio.lifecycle;

import net.dv8tion.jda.core.audio.AudioSendHandler;
import org.immutables.value.Value;
import space.npstr.magma.immutables.ImmutableLcEvent;

import java.util.Optional;

/**
 * Created by napster on 24.04.18.
 */
@Value.Immutable
@ImmutableLcEvent
public abstract class UpdateSendHandler implements LifecycleEvent {

    @Override
    public abstract String getUserId();

    @Override
    public abstract String getGuildId();

    public abstract Optional<AudioSendHandler> getAudioSendHandler();
}
