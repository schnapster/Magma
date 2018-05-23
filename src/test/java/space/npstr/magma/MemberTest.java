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

package space.npstr.magma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Created by napster on 23.05.18.
 */
public class MemberTest {

    @Test
    public void emptyUserId() {
        final Executable ex = () -> MagmaMember.builder()
                .userId("")
                .guildId("174820236481134592")
                .build();

        assertThrows(IllegalArgumentException.class, ex, "Accepted empty user id");
    }

    @Test
    public void emptyGuildId() {
        final Executable ex = () -> MagmaMember.builder()
                .userId("166604053629894657")
                .guildId("")
                .build();

        assertThrows(IllegalArgumentException.class, ex, "Accepted empty guild id");
    }


    @Test
    public void userIdNotASnowflake() {
        final Executable ex = () -> MagmaMember.builder()
                .userId("this is not a valid snowflake")
                .guildId("174820236481134592")
                .build();

        assertThrows(IllegalArgumentException.class, ex, "Accepted obviously invalid snowflake as a user id");
    }


    @Test
    public void guildIdNotASnowflake() {
        final Executable ex = () -> MagmaMember.builder()
                .userId("166604053629894657")
                .guildId("totally valid snowflake kappa")
                .build();

        assertThrows(IllegalArgumentException.class, ex, "Accepted obviously invalid snowflake as a guild id");
    }

    @Test
    public void valid() {
        final String userId = "166604053629894657";
        final String guildId = "174820236481134592";
        final Member member = MagmaMember.builder()
                .userId(userId)
                .guildId(guildId)
                .build();

        assertEquals(userId, member.getUserId(), "User id modified by builder");
        assertEquals(guildId, member.getGuildId(), "Guild id modified by builder");
    }
}
