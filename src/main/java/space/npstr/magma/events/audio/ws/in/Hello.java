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

import org.immutables.value.Value;
import space.npstr.magma.events.audio.ws.OpCode;
import space.npstr.magma.immutables.ImmutableWsEvent;

/**
 * Created by napster on 20.04.18.
 * <p>
 * Received the heartbeat interval
 */
@Value.Immutable
@ImmutableWsEvent
public abstract class Hello implements InboundWsEvent {

    @Override
    public int getOpCode() {
        return OpCode.HELLO;
    }

    /**
     * @return The heartbeat interval that we received from Discord's Hello event (op 8)
     */
    public abstract int getHeartbeatIntervalMillis();
}
