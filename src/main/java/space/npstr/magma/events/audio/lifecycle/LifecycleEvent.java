package space.npstr.magma.events.audio.lifecycle;

/**
 * Created by napster on 23.04.18.
 *
 * @see space.npstr.magma.AudioStackLifecyclePipeline
 */
public interface LifecycleEvent {

    String getUserId();

    String getGuildId();
}
