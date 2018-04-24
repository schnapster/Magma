package space.npstr.magma.events.audio.ws.out;

import org.immutables.value.Value;
import org.json.JSONObject;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 21.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class SelectProtocol implements OutboundWsEvent {

    public abstract String getProtocol();

    public abstract String getHost();

    public abstract int getPort();

    public abstract String getMode();

    @Override
    public int getOpCode() {
        return OpCode.SELECT_PROTOCOL;
    }

    @Override
    public JSONObject getData() {
        return new JSONObject()
                .put("protocol", this.getProtocol())
                .put("data", new JSONObject()
                        .put("address", this.getHost())
                        .put("port", this.getPort())
                        .put("mode", this.getMode()));
    }
}
