package space.npstr.magma.immutables;

import org.immutables.value.Value;
import space.npstr.magma.events.audio.lifecycle.VoiceServerUpdate;

/**
 * Created by napster on 20.04.18.
 */
@Value.Immutable
@Value.Style(stagedBuilder = true)
public abstract class SessionInfo {

    public abstract String getUserId();

    public abstract VoiceServerUpdate getVoiceServerUpdate();
}
