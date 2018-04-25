package space.npstr.magma.events.audio.ws.out;

import org.immutables.value.Value;
import org.json.JSONObject;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 25.04.18.
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Resume implements OutboundWsEvent {

    public abstract String getGuildId();

    public abstract String getSessionId();

    public abstract String getToken();

    @Override
    public int getOpCode() {
        return OpCode.RESUME;
    }

    @Override
    public JSONObject getData() {
        return new JSONObject()
                .put("server_id", this.getGuildId())
                .put("session_id", this.getSessionId())
                .put("token", this.getToken());
    }
}
