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

package space.npstr.magma.processing;

import com.iwebpp.crypto.TweetNaclFast;
import com.sun.jna.ptr.PointerByReference;
import space.npstr.magma.EncryptionMode;
import space.npstr.magma.connections.AudioConnection;
import tomp2p.opuswrapper.Opus;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.function.Supplier;

/**
 * Created by napster on 23.06.18.
 */
public class PacketUtil {

    private PacketUtil() {
    }

    /**
     * The code of this method has been copied almost fully from the AudioConnection class of JDA-Audio
     */
    public static byte[] encodeToOpus(final byte[] rawAudio, final PointerByReference opusEncoder) {
        final ShortBuffer nonEncodedBuffer = ShortBuffer.allocate(rawAudio.length / 2);
        final ByteBuffer encoded = ByteBuffer.allocate(4096);
        for (int i = 0; i < rawAudio.length; i += 2) {
            final int firstByte = (0x000000FF & rawAudio[i]);      //Promotes to int and handles the fact that it was unsigned.
            final int secondByte = (0x000000FF & rawAudio[i + 1]);  //

            //Combines the 2 bytes into a short. Opus deals with unsigned shorts, not bytes.
            final short toShort = (short) ((firstByte << 8) | secondByte);

            nonEncodedBuffer.put(toShort);
        }
        nonEncodedBuffer.flip();

        //TODO: check for 0 / negative value for error.
        final int result = Opus.INSTANCE.opus_encode(opusEncoder,
                nonEncodedBuffer,
                AudioConnection.OPUS_FRAME_SIZE,
                encoded,
                encoded.capacity()
        );

        //ENCODING STOPS HERE

        final byte[] audio = new byte[result];
        encoded.get(audio);
        return audio;
    }

    @Nullable
    public static byte[] getNonceData(final EncryptionMode encryptionMode, final Supplier<Long> nonceSupplier) {
        switch (encryptionMode) {
            case XSALSA20_POLY1305:
                return null;
            case XSALSA20_POLY1305_LITE:
                return getNonceBytes(nonceSupplier.get());
            case XSALSA20_POLY1305_SUFFIX:
                return TweetNaclFast.randombytes(TweetNaclFast.SecretBox.nonceLength);
            default:
                throw new IllegalStateException("Encryption mode [" + encryptionMode + "] is not supported!");
        }
    }

    //@formatter:off
    public static byte[] getNonceBytes(final long nonce) {
        final byte[] data = new byte[4];
        data[0] = (byte) ((nonce >>> 24) & 0xFF);
        data[1] = (byte) ((nonce >>> 16) & 0xFF);
        data[2] = (byte) ((nonce >>>  8) & 0xFF);
        data[3] = (byte) ( nonce         & 0xFF);
        return data;
    }
    //@formatter:on
}
