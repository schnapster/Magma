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

package space.npstr.magma.events.audio.ws;

public final class OpCode {

    // https://discordapp.com/developers/docs/topics/opcodes-and-status-codes#voice-opcodes
    public static final int IDENTIFY = 0;
    public static final int SELECT_PROTOCOL = 1;
    public static final int READY = 2;
    public static final int HEARTBEAT = 3;
    public static final int SESSION_DESCRIPTION = 4;
    public static final int SPEAKING = 5;
    public static final int HEARTBEAT_ACK = 6;
    public static final int RESUME = 7;
    public static final int HELLO = 8;
    public static final int RESUMED = 9;
    public static final int OP_12 = 12;                     //not documented, but we do receive it
    public static final int CLIENT_DISCONNECT = 13;
    public static final int OP_14 = 14;                     //not documented, but we do receive it

    // Custom codes
    public static final int WEBSOCKET_CLOSE = 9001;

    private OpCode() {}
}
