/*
 * Copyright 2018-2019 Dennis Neufeld
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

package space.npstr.magma.api.event;

import org.immutables.value.Value;
import space.npstr.magma.api.Member;

/**
 * This event is fired when an audio web socket is closed. However, this event is not fired if we are trying to
 * resume (i.e reconnect) automatically unless resuming causes a new socket to close.
 */
@SuppressWarnings("unused")
@Value.Immutable
@ImmutableApiEvent
public abstract class WebSocketClosed implements MagmaEvent {

    public abstract Member getMember();

    public abstract int getCloseCode();

    public abstract String getReason();

    public abstract boolean isByRemote();
}
