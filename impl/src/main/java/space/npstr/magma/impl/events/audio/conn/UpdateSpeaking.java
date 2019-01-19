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

package space.npstr.magma.impl.events.audio.conn;

import space.npstr.magma.api.SpeakingMode;

import java.util.Set;

/**
 * Created by napster on 21.06.18.
 */
public class UpdateSpeaking implements ConnectionEvent {
    private final boolean shouldSpeak;
    private final Set<SpeakingMode> modes;

    public UpdateSpeaking(boolean shouldSpeak, Set<SpeakingMode> modes) {
        this.shouldSpeak = shouldSpeak;
        this.modes = modes;
    }

    public boolean shouldSpeak() {
        return this.shouldSpeak;
    }

    public int getSpeakingMode() {
        return shouldSpeak ? SpeakingMode.toMask(modes) : 0;
    }
}
