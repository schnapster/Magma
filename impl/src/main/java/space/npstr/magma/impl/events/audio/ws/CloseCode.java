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

package space.npstr.magma.impl.events.audio.ws;

import java.util.Optional;

/**
 * Sources:
 * <a href="https://s.gus.host/flowchart.svg">Flowchart</a>
 * <a href="https://discordapp.com/developers/docs/topics/opcodes-and-status-codes#voice-voice-close-event-codes">Discord Documentation</a>
 */
public enum CloseCode {
    //@formatter:off                   warn     resume
    HEARTBEAT_TIMEOUT           (1000, false,   true),
    CLOUDFLARE                  (1001, false,   true),
    ABNORMAL                    (1006, true,    true),

    UNKNOWN_OP_CODE             (4001, true,    true),
    NOT_AUTHENTICATED           (4003, true,    false),
    AUTHENTICATION_FAILED       (4004, true,    false),
    ALREADY_AUTHENTICATED       (4005, true,    false),
    SESSION_NO_LONGER_VALID     (4006, true,    false),
    SESSION_TIMEOUT             (4009, true,    false),
    SERVER_NOT_FOUND            (4011, true,    false),
    UNKNOWN_PROTOCOL            (4012, true,    false),
    DISCONNECTED                (4014, false,   false),
    VOICE_SERVER_CRASHED        (4015, false,   true),
    UNKNOWN_ENCRYPTION_MODE     (4016, true,    false),
    //@formatter:on
    ;

    public static Optional<CloseCode> parse(final int code) {
        for (final CloseCode closeCode : CloseCode.values()) {
            if (closeCode.code == code) {
                return Optional.of(closeCode);
            }
        }
        return Optional.empty();
    }

    private final int code;
    private final boolean shouldWarn;
    private final boolean shouldResume;

    CloseCode(final int code, final boolean shouldWarn, final boolean shouldResume) {
        this.code = code;
        this.shouldWarn = shouldWarn;
        this.shouldResume = shouldResume;
    }

    public int getCode() {
        return this.code;
    }

    public boolean shouldWarn() {
        return this.shouldWarn;
    }

    public boolean shouldResume() {
        return this.shouldResume;
    }


    @Override
    public String toString() {
        return "[" + this.code + " " + this.name() + "]";
    }
}
