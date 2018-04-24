package space.npstr.magma.events.audio.lifecycle;

/**
 * Created by napster on 24.04.18.
 * <p>
 * This is a lifecycle event for the whole Magma API
 */
public class Shutdown implements LifecycleEvent {

    public static final Shutdown INSTANCE = new Shutdown();

    private Shutdown() {
    }

    @Override
    public String getUserId() {
        return "";
    }

    @Override
    public String getGuildId() {
        return "";
    }

}
