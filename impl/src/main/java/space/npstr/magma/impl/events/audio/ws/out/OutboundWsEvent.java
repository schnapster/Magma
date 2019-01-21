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

package space.npstr.magma.impl.events.audio.ws.out;

import org.json.JSONObject;
import space.npstr.magma.impl.events.audio.ws.WsEvent;
import space.npstr.magma.impl.events.audio.ws.in.InboundWsEvent;

/**
 * Created by napster on 21.04.18.
 * <p>
 * Payloads that we may send to Discord.
 * Counterpart to {@link InboundWsEvent}
 */
public interface OutboundWsEvent extends WsEvent {

    /**
     * @return Data payload
     * Should be an object that json understands and correctly parses {@literal ->} strings get double quoted for example
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
