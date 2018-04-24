package space.npstr.magma.events.audio.ws.in;

import org.immutables.value.Value;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 20.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class SessionDescription implements InboundWsEvent {

    @Override
    public int getOpCode() {
        return OpCode.SESSION_DESCRIPTION;
    }

    /**
     * @return the encryption mode of this session
     */
    public abstract String getMode();

    /**
     * @return the secret key sent to us by the Session Description voice websocket event (op 4)
     */
    public abstract byte[] getSecretKey();
}
