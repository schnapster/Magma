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

package space.npstr.magma.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Created by napster on 23.05.18.
 */
public class ServerUpdateTest {

    @Test
    public void emptySessionId() {
        final Executable ex = () -> MagmaServerUpdate.builder()
                .sessionId("")
                .endpoint("127.0.0.1")
                .token("top_secret")
                .build();

        assertThrows(IllegalArgumentException.class, ex, "Accepted empty session id");
    }

    @Test
    public void emptyEndpoint() {
        final Executable ex = () -> MagmaServerUpdate.builder()
                .sessionId("blargh")
                .endpoint("")
                .token("top_secret")
                .build();

        assertThrows(IllegalArgumentException.class, ex, "Accepted empty endpoint");
    }

    @Test
    public void emptyToken() {
        final Executable ex = () -> MagmaServerUpdate.builder()
                .sessionId("blargh")
                .endpoint("127.0.0.1")
                .token("")
                .build();

        assertThrows(IllegalArgumentException.class, ex, "Accepted empty token");
    }

    @Test
    public void valid() {
        final String sessionId = "blargh";
        final String endpoint = "127.0.0.1";
        final String token = "top_secret";
        final ServerUpdate serverUpdate = MagmaServerUpdate.builder()
                .sessionId(sessionId)
                .endpoint(endpoint)
                .token(token)
                .build();

        assertEquals(sessionId, serverUpdate.getSessionId(), "Session id modified by builder");
        assertEquals(endpoint, serverUpdate.getEndpoint(), "Endpoint modified by builder");
        assertEquals(token, serverUpdate.getToken(), "Token modified by builder");
    }

}
