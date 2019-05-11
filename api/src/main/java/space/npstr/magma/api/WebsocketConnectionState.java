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

package space.npstr.magma.api;

import org.immutables.value.Value;

/**
 * Created by napster on 25.10.18.
 */
@Value.Immutable
@Value.Style(
        typeAbstract = "*",
        typeImmutable = "Magma*"
)
public abstract class WebsocketConnectionState {

    /**
     * @return user and guild coordinates of this audio connection
     */
    public abstract Member getMember();

    /**
     * @return phase the websocket connection of this member finds itself in, see {@link Phase}
     */
    public abstract Phase getPhase();


    public enum Phase {

        /**
         * The connection is connecting initially.
         */
        CONNECTING,

        /**
         * We have received a Ready or Resume payload and have not disconnected yet.
         */
        CONNECTED,

        /**
         * Websocket connection has been closed, either by Discord, due to internet issues, or by ourselves.
         */
        DISCONNECTED,

        /**
         * We are attempting to resume the connection.
         */
        RESUMING,

        /**
         * No websocket connection is present, even though there is an audio stack present for this member.
         */
        NO_CONNECTION,
    }
}
