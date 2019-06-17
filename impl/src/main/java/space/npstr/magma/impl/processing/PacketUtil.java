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

package space.npstr.magma.impl.processing;

import com.iwebpp.crypto.TweetNaclFast;
import net.dv8tion.jda.api.audio.AudioPacket;
import space.npstr.magma.impl.EncryptionMode;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Created by napster on 23.06.18.
 */
public class PacketUtil {

    private PacketUtil() {
    }

    //this may reallocate the passed ByteBuffer if it is too small
    public static ByteBuffer encryptPacket(final AudioPacket audioPacket, final ByteBuffer packetBuffer,
                                           final EncryptionMode encryptionMode, final byte[] secretKey,
                                           final Supplier<Long> nonceSupplier, final byte[] nonceBuffer) {

        final int nonceLength;
        switch (encryptionMode) {
            case XSALSA20_POLY1305:
                nonceLength = 0;
                break;
            case XSALSA20_POLY1305_LITE:
                writeNonce(nonceSupplier.get(), nonceBuffer);
                nonceLength = 4;
                break;
            case XSALSA20_POLY1305_SUFFIX:
                ThreadLocalRandom.current().nextBytes(nonceBuffer);
                nonceLength = TweetNaclFast.SecretBox.nonceLength;
                break;
            default:
                throw new IllegalStateException("Encryption mode [" + encryptionMode + "] is not supported!");
        }

        return audioPacket.asEncryptedPacket(packetBuffer, secretKey, nonceBuffer, nonceLength);
    }

    //@formatter:off
    public static void writeNonce(final long nonce, final byte[] nonceBuffer) {
        nonceBuffer[0] = (byte) ((nonce >>> 24) & 0xFF);
        nonceBuffer[1] = (byte) ((nonce >>> 16) & 0xFF);
        nonceBuffer[2] = (byte) ((nonce >>>  8) & 0xFF);
        nonceBuffer[3] = (byte) ( nonce         & 0xFF);
    }
    //@formatter:on
}
