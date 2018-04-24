package space.npstr.magma.events.audio.ws.in;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.magma.connections.ReactiveAudioWebSocket;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.events.audio.ws.SpeakingWsEvent;
import space.npstr.magma.events.audio.ws.WsEvent;
import space.npstr.magma.events.audio.ws.out.OutboundWsEvent;

/**
 * Created by napster on 20.04.18.
 * <p>
 * Events that may be received from Discord.
 * Counterpart to {@link OutboundWsEvent}
 */
public interface InboundWsEvent extends WsEvent {

    Logger log = LoggerFactory.getLogger(InboundWsEvent.class);

    /**
     * This method may throw if Discord sends us bogus json data. This is not unlikely given Discord's history api. todo figure out error handling for it
     *
     * @param payload
     *         the payload of the websocket message as a string
     *
     * @return a parsed WsEvent that we understand
     */
    static InboundWsEvent from(final String payload) {
        final JSONObject content = new JSONObject(payload);
        final int opCode = content.getInt("op");

        switch (opCode) {
            case OpCode.HELLO:
                final JSONObject helloD = content.getJSONObject("d");
                return HelloWsEvent.builder()
                        .heartbeatIntervalMillis(helloD.getInt("heartbeat_interval"))
                        .build();
            case OpCode.READY:
                final JSONObject readyD = content.getJSONObject("d");
                return ReadyWsEvent.builder()
                        .ssrc(readyD.getInt("ssrc"))
                        .port(readyD.getInt("port"))
                        .build();
            case OpCode.SESSION_DESCRIPTION:
                final JSONObject sessionD = content.getJSONObject("d");
                final String mode = sessionD.getString("mode");
                if (!mode.equalsIgnoreCase(ReactiveAudioWebSocket.V3_ENCRYPTION_MODE)) {
                    log.warn("Received unknown encryption mode {}", mode);
                }
                final JSONArray keyArray = sessionD.getJSONArray("secret_key");
                final byte[] secretKey = new byte[ReactiveAudioWebSocket.DISCORD_SECRET_KEY_LENGTH];
                for (int i = 0; i < keyArray.length(); i++) {
                    secretKey[i] = (byte) keyArray.getInt(i);
                }

                return SessionDescriptionWsEvent.builder()
                        .mode(mode)
                        .secretKey(secretKey)
                        .build();
            case OpCode.SPEAKING:
                final JSONObject speakingD = content.getJSONObject("d");
                return SpeakingWsEvent.builder()
                        .isSpeaking(speakingD.getBoolean("speaking"))
                        .build();
            case OpCode.HEARTBEAT_ACK:
                return HeartbeatAckWsEvent.builder()
                        .build();
            case OpCode.RESUMED:
                return ResumedWsEvent.builder()
                        .build();
            case OpCode.NO_IDEA:
                return IgnoredWsEvent.builder()
                        .opCode(OpCode.NO_IDEA)
                        .build();
            case OpCode.CLIENT_DISCONNECT:
                return ClientDisconnectWsEvent.builder()
                        .build();
            case OpCode.WEBSOCKET_CLOSE:
                final JSONObject closedD = content.getJSONObject("d");
                return WebSocketClosedWsEvent.builder()
                        .code(closedD.getInt("code"))
                        .reason(closedD.getString("reason"))
                        .build();

            default:
                log.warn("Received unexpected op code {}, full message: {}", opCode, payload);
                return UnknownWsEvent.builder()
                        .payload(payload)
                        .opCode(opCode)
                        .build();
        }
    }
}
