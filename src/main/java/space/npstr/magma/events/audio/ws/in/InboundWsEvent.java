/*
 * Copyright 2018 Dennis Neufeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package space.npstr.magma.events.audio.ws.in;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.magma.connections.AudioConnection;
import space.npstr.magma.connections.AudioWebSocket;
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
                        .ip(readyD.getString("ip"))
                        .port(readyD.getInt("port"))
                        .build();
            case OpCode.SESSION_DESCRIPTION:
                final JSONObject sessionD = content.getJSONObject("d");
                final String mode = sessionD.getString("mode");
                if (!mode.equalsIgnoreCase(AudioWebSocket.V3_ENCRYPTION_MODE)) {
                    log.warn("Received unknown encryption mode {}", mode);
                }
                final JSONArray keyArray = sessionD.getJSONArray("secret_key");
                final byte[] secretKey = new byte[AudioConnection.DISCORD_SECRET_KEY_LENGTH];
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
                        .speakingMask(speakingD.getInt("speaking"))
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
