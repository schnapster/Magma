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

package space.npstr.magma.impl.events.audio.lifecycle;

import org.immutables.value.Value;
import space.npstr.magma.api.Member;
import space.npstr.magma.impl.immutables.ImmutableLcEvent;

/**
 * Created by napster on 22.04.18.
 */
@Value.Immutable
@ImmutableLcEvent
public abstract class VoiceServerUpdate implements LifecycleEvent {

    @Override
    public abstract Member getMember();

    public abstract String getSessionId();

    public abstract String getEndpoint();

    public abstract String getToken();
}