/*
 *     Copyright 2015-2018 Austin Keener & Michael Ritter & Florian Spieß
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

package net.dv8tion.jda.api.audio;

import com.iwebpp.crypto.TweetNaclFast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Represents the contents of a audio packet that was either received from Discord or
 * will be sent to discord.
 */
public class AudioPacket
{
    private static final Logger log = LoggerFactory.getLogger(AudioPacket.class);

    public static final int RTP_HEADER_BYTE_LENGTH = 12;

    /**
     * Bit index 0 and 1 represent the RTP Protocol version used. Discord uses the latest RTP protocol version, 2.<br>
     * Bit index 2 represents whether or not we pad. Opus uses an internal padding system, so RTP padding is not used.<br>
     * Bit index 3 represents if we use extensions. Discord does not use RTP extensions.<br>
     * Bit index 4 to 7 represent the CC or CSRC count. CSRC is Combined SSRC. Discord doesn't combine audio streams,
     *      so the Combined count will always be 0 (binary: 0000).<br>
     * This byte should always be the same, no matter the library implementation.
     */
    public static final byte RTP_VERSION_PAD_EXTEND = (byte) 0x80;  //Binary: 1000 0000

    /**
     * This is Discord's RTP Profile Payload type.<br>
     * I've yet to find actual documentation on what the bits inside this value represent.<br>
     * As far as I can tell, this byte will always be the same, no matter the library implementation.<br>
     */
    public static final byte RTP_PAYLOAD_TYPE = (byte) 0x78;        //Binary: 0100 1000

    private final char seq;
    private final int timestamp;
    private final int ssrc;
    private final ByteBuffer encodedAudio;
    private final byte[] rawPacket;

    public AudioPacket(final char seq, final int timestamp, final int ssrc, final ByteBuffer encodedAudio)
    {
        this.seq = seq;
        this.ssrc = ssrc;
        this.timestamp = timestamp;
        this.encodedAudio = encodedAudio;
        this.rawPacket = generateRawPacket(seq, timestamp, ssrc, encodedAudio);
    }

    public byte[] getNoncePadded()
    {
        final byte[] nonce = new byte[TweetNaclFast.SecretBox.nonceLength];
        //The first 12 bytes are the rawPacket are the RTP Discord Nonce.
        System.arraycopy(this.rawPacket, 0, nonce, 0, RTP_HEADER_BYTE_LENGTH);
        return nonce;
    }

    //this may reallocate the passed bytebuffer if it is too small
    public ByteBuffer asEncryptedPacket(final ByteBuffer buffer, final byte[] secretKey, final byte[] nonce, @Nullable final int nonceLength)
    {

        ByteBuffer outputBuffer = buffer;
        //Xsalsa20's Nonce is 24 bytes long, however RTP (and consequently Discord)'s nonce is a different length
        // so we need to create a 24 byte array, and copy the nonce into it.
        // we will leave the extra bytes as nulls. (Java sets non-populated bytes as 0).
        byte[] extendedNonce = nonce;
        if (nonce == null) {
            extendedNonce = getNoncePadded();
        }
        final byte[] array = encodedAudio.array();
        final int arrayOffset = encodedAudio.arrayOffset();
        final int length = encodedAudio.remaining();

        //Create our SecretBox encoder with the secretKey provided by Discord.
        final TweetNaclFast.SecretBox boxer = new TweetNaclFast.SecretBox(secretKey);
        final byte[] encryptedAudio = boxer.box(array, arrayOffset, length, extendedNonce);
        outputBuffer.clear();
        final int capacity = RTP_HEADER_BYTE_LENGTH + encryptedAudio.length + nonceLength;
        if (capacity > outputBuffer.remaining()) {
            log.trace("Allocating byte buffer with capacity " + capacity);
            outputBuffer = ByteBuffer.allocate(capacity);
        }
        populateBuffer(this.seq, this.timestamp, this.ssrc, ByteBuffer.wrap(encryptedAudio), outputBuffer);
        if (nonce != null) {
            outputBuffer.put(nonce, 0, nonceLength);
        }

        ((Buffer) outputBuffer).flip();
        return outputBuffer;
    }

    private static byte[] generateRawPacket(final char seq, final int timestamp, final int ssrc, final ByteBuffer data)
    {
        final ByteBuffer buffer = ByteBuffer.allocate(RTP_HEADER_BYTE_LENGTH + data.remaining());
        populateBuffer(seq, timestamp, ssrc, data, buffer);
        return buffer.array();
    }

    private static void populateBuffer(final char seq, final int timestamp, final int ssrc, final ByteBuffer data, final ByteBuffer buffer)
    {
        buffer.put(RTP_VERSION_PAD_EXTEND);
        buffer.put(RTP_PAYLOAD_TYPE);
        buffer.putChar(seq);
        buffer.putInt(timestamp);
        buffer.putInt(ssrc);
        buffer.put(data);
        ((Buffer) data).flip();
    }
}
