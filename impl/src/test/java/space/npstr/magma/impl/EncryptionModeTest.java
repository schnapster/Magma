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

package space.npstr.magma.impl;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by napster on 07.05.18.
 */
public class EncryptionModeTest {

    @Test
    public void testPreference() {
        final List<EncryptionMode> allModes = List.of(EncryptionMode.XSALSA20_POLY1305,
                EncryptionMode.XSALSA20_POLY1305_LITE,
                EncryptionMode.XSALSA20_POLY1305_SUFFIX);

        final Optional<EncryptionMode> preferredMode = EncryptionMode.getPreferredMode(allModes);
        assertTrue(preferredMode.isPresent(), "return a preferred mode");
        assertEquals(EncryptionMode.XSALSA20_POLY1305_LITE, preferredMode.get(), "prefer lite over all others");


        final List<EncryptionMode> empty = Collections.emptyList();
        assertFalse(EncryptionMode.getPreferredMode(empty).isPresent(), "empty list returns empty optional");
    }
}
