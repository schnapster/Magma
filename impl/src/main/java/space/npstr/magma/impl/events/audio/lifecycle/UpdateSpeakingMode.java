package space.npstr.magma.impl.events.audio.lifecycle;

import edu.umd.cs.findbugs.annotations.Nullable;
import org.immutables.value.Value;
import space.npstr.magma.api.Member;
import space.npstr.magma.api.SpeakingMode;
import space.npstr.magma.impl.immutables.ImmutableLcEvent;

import java.util.Set;

@Value.Immutable
@ImmutableLcEvent
public abstract class UpdateSpeakingMode implements LifecycleEvent {

	@Override
	public abstract Member getMember();

	@Nullable
	public abstract Set<SpeakingMode> getSpeakingModes();
}
