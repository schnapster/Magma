package space.npstr.magma.events.audio.ws;

import org.immutables.value.Value;
import org.json.JSONObject;
import space.npstr.magma.events.audio.ws.in.InboundWsEvent;
import space.npstr.magma.events.audio.ws.out.OutboundWsEvent;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 21.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Speaking implements InboundWsEvent, OutboundWsEvent {

    @Override
    public int getOpCode() {
        return OpCode.SPEAKING;
    }

    public abstract boolean isSpeaking();

    @Override
    public Object getData() {
        return new JSONObject()
                .put("speaking", this.isSpeaking())
                .put("delay", 0);
    }
}
