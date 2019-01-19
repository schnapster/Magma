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

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public enum EncryptionMode {

    XSALSA20_POLY1305_LITE(30),    // uses 4 byte nonce instead of 24 bytes
    XSALSA20_POLY1305_SUFFIX(20),  // "official" implementation using random 24 byte nonce
    XSALSA20_POLY1305(10);         // unofficial implementation using time stamps (?) as nonce (24 bytes total)

    private static final Logger log = LoggerFactory.getLogger(EncryptionMode.class);

    private final int preference;
    private final String key;

    EncryptionMode(final int preference) {
        this.preference = preference;
        this.key = this.name().toLowerCase();
    }

    /**
     * @return the key that discord recognizes this mode under
     */
    public String getKey() {
        return this.key;
    }

    /**
     * @return The encryption mode corresponding to the given input, or nothing.
     */
    public static Optional<EncryptionMode> parse(final String input) {
        try {
            return Optional.of(EncryptionMode.valueOf(input.toUpperCase()));
        } catch (final IllegalArgumentException e) {
            log.debug("Could not parse encryption mode: {}", input);
        }

        return Optional.empty();
    }

    /**
     * @return parse a JSONArray into a list of encryption modes
     */
    public static List<EncryptionMode> fromJson(final JSONArray array) {
        final List<EncryptionMode> result = new ArrayList<>();
        for (final Object o : array) {
            try {
                result.add(EncryptionMode.valueOf(((String) o).toUpperCase()));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    /**
     * @return parse a JSONArray into a list of encryption modes
     */
    public static Optional<EncryptionMode> getPreferredMode(final Collection<EncryptionMode> encryptionModes) {
        if (encryptionModes.isEmpty()) {
            log.warn("Can not pick a preferred encryption mode from an empty collection");
            return Optional.empty();
        }
        final List<EncryptionMode> sort = new ArrayList<>(encryptionModes);
        sort.sort((e1, e2) -> e2.preference - e1.preference);
        return Optional.of(sort.get(0));
    }
}