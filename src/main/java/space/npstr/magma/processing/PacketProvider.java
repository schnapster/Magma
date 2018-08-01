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
import net.dv8tion.jda.core.audio.AudioPacket;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IPacketProvider;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.magma.EncryptionMode;
import space.npstr.magma.connections.AudioConnection;

import javax.annotation.Nullable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Created by napster on 23.06.18.
 */
public class PacketProvider implements IPacketProvider {

    private static final Logger log = LoggerFactory.getLogger(PacketProvider.class);
    private static final String INFORMATION_NOT_AVAILABLE = "This information is not available";
    private static final byte[] SILENCE_BYTES = new byte[]{(byte) 0xF8, (byte) 0xFF, (byte) 0xFE};
    private static final int EMPTY_FRAMES_COUNT = 5;

    private final AudioConnection audioConnection;
    private final Supplier<Long> nonceSupplier;
    private ByteBuffer packetBuffer = ByteBuffer.allocate(512); //packets usually take up about 400-500 bytes
    private final byte[] nonceBuffer = new byte[TweetNaclFast.SecretBox.nonceLength];

    private char seq = 0;           //Sequence of audio packets. Used to determine the order of the packets.
    private int timestamp = 0;      //Used to sync up our packets within the same timeframe of other people talking.

    // opus interpolation handling
    // https://discordapp.com/developers/docs/topics/voice-connections#voice-data-interpolation
    private int sendSilentFrames = EMPTY_FRAMES_COUNT;

    public PacketProvider(final AudioConnection audioConnection, final Supplier<Long> nonceSupplier) {
        this.audioConnection = audioConnection;
        this.nonceSupplier = nonceSupplier;
    }

    @Override
    public String getIdentifier() {
        throw new UnsupportedOperationException(INFORMATION_NOT_AVAILABLE);
    }

    @Override
    public String getConnectedChannel() {
        throw new UnsupportedOperationException(INFORMATION_NOT_AVAILABLE);
    }

    @Override
    public DatagramSocket getUdpSocket() {
        return this.audioConnection.getUdpSocket();
    }

    @Nullable
    @Override
    public InetSocketAddress getSocketAddress() {
        return this.audioConnection.getUdpTargetAddress();
    }

    @Override
    public void onConnectionError(final ConnectionStatus status) {
        throw new UnsupportedOperationException("Connection error, on a udp connection...that's not a real thing.");
    }

    @Override
    public void onConnectionLost() {
        throw new UnsupportedOperationException("Connection lost, on a udp connection...that's not a real thing.");
    }

    //realistically, this is the only thing that is ever called by the NativeAudioSystem
    @Nullable
    @Override
    public DatagramPacket getNextPacket(final boolean changeTalking) {
        final InetSocketAddress targetAddress = this.audioConnection.getUdpTargetAddress();
        if (targetAddress == null) {
            return null;
        }
        final ByteBuffer nextPacket = getNextPacketRaw(changeTalking);
        return nextPacket == null ? null : asDatagramPacket(nextPacket, targetAddress);
    }

    @Nullable
    @Override
    public ByteBuffer getNextPacketRaw(final boolean changeTalking) {
        try {
            return this.buildNextPacket(changeTalking);
        } catch (final Exception e) {
            log.error("Failed to get next packet", e);
            return null;
        }
    }

    @Nullable
    private ByteBuffer buildNextPacket(final boolean changeTalking) {

        final EncryptionMode encryptionMode = this.audioConnection.getEncryptionMode();
        final byte[] secretKey = this.audioConnection.getSecretKey();
        final Integer ssrc = this.audioConnection.getSsrc();
        final AudioSendHandler sendHandler = this.audioConnection.getSendHandler();

        //preconditions fulfilled?
        if (encryptionMode == null
                || secretKey == null
                || ssrc == null
                || sendHandler == null
                || !sendHandler.canProvide()) {
            if (this.audioConnection.isSpeaking() && changeTalking) {
                this.audioConnection.updateSpeaking(false);
            }
            this.sendSilentFrames = EMPTY_FRAMES_COUNT;
            return null;
        }

        final AudioPacket nextAudioPacket;
        if (this.sendSilentFrames <= 0) {
            //audio data provided?
            final byte[] rawAudio = sendHandler.provide20MsAudio();
            if (rawAudio == null || rawAudio.length == 0) {
                if (this.audioConnection.isSpeaking() && changeTalking) {
                    this.audioConnection.updateSpeaking(false);
                }
                this.sendSilentFrames = EMPTY_FRAMES_COUNT;
                return null;
            }
            nextAudioPacket = new AudioPacket(this.seq, this.timestamp, ssrc, rawAudio);
        } else {
            nextAudioPacket = new AudioPacket(this.seq, this.timestamp, ssrc, SILENCE_BYTES);
            this.sendSilentFrames--;
            log.trace("Sending silent frame, silent frames left {}", this.sendSilentFrames);
        }

        final ByteBuffer nextPacket = this.packetBuffer = PacketUtil.encryptPacket(nextAudioPacket, this.packetBuffer,
                encryptionMode, secretKey, this.nonceSupplier, this.nonceBuffer);

        if (!this.audioConnection.isSpeaking()) {
            this.audioConnection.updateSpeaking(true);
        }

        if (this.seq + 1 > Character.MAX_VALUE) {
            this.seq = 0;
        } else {
            this.seq++;
        }

        this.timestamp += AudioConnection.OPUS_FRAME_SIZE;

        return nextPacket;
    }

    private DatagramPacket asDatagramPacket(final ByteBuffer buffer, final InetSocketAddress targetAddress) {
        final byte[] data = buffer.array();
        final int offset = buffer.arrayOffset();
        final int position = buffer.position();
        return new DatagramPacket(data, offset, position - offset, targetAddress);
    }
}
