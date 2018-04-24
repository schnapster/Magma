package space.npstr.magma.events.audio.ws.out;

import org.json.JSONObject;
import space.npstr.magma.events.audio.ws.WsEvent;
import space.npstr.magma.events.audio.ws.in.InboundWsEvent;

/**
 * Created by napster on 21.04.18.
 * <p>
 * Payloads that we may send to Discord.
 * Counterpart to {@link InboundWsEvent}
 */
public interface OutboundWsEvent extends WsEvent {

    /**
     * @return Data payload
     * Should be an object that json understands and correctly parses -> strings get double quoted for example
     */
    Object getData();

    /**
     * Build a message that can be send to Discord over the websocket.
     */
    default String asMessage() {
        return new JSONObject()
                .put("op", this.getOpCode())
                .put("d", this.getData())
                .toString();
    }
}
