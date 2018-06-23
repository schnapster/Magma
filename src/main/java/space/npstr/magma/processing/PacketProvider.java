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

import com.sun.jna.ptr.PointerByReference;
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
import java.util.function.Supplier;

/**
 * Created by napster on 23.06.18.
 */
public class PacketProvider implements IPacketProvider {

    private static final Logger log = LoggerFactory.getLogger(PacketProvider.class);
    private static final String INFORMATION_NOT_AVAILABLE = "This information is not available";

    private final AudioConnection audioConnection;
    private final Supplier<Long> nonceSupplier;

    private char seq = 0;           //Sequence of audio packets. Used to determine the order of the packets.
    private int timestamp = 0;      //Used to sync up our packets within the same timeframe of other people talking.

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
        throw new UnsupportedOperationException(INFORMATION_NOT_AVAILABLE);
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
        DatagramPacket nextPacket = null;

        final EncryptionMode encryptionMode = this.audioConnection.getEncryptionMode();
        final byte[] secretKey = this.audioConnection.getSecretKey();
        final Integer ssrc = this.audioConnection.getSsrc();
        final InetSocketAddress udpTargetAddress = this.audioConnection.getUdpTargetAddress();
        final PointerByReference opusEncoder = this.audioConnection.getOpusEncoder();
        final AudioSendHandler sendHandler = this.audioConnection.getSendHandler();

        try {
            if (encryptionMode != null
                    && secretKey != null
                    && ssrc != null
                    && udpTargetAddress != null
                    && opusEncoder != null
                    && sendHandler != null
                    && sendHandler.canProvide()) {
                byte[] rawAudio = sendHandler.provide20MsAudio();
                if (rawAudio == null || rawAudio.length == 0) {
                    if (this.audioConnection.isSpeaking() && changeTalking)
                        this.audioConnection.updateSpeaking(false);
                } else {
                    if (!sendHandler.isOpus()) {
                        rawAudio = PacketUtil.encodeToOpus(rawAudio, opusEncoder);
                    }
                    nextPacket = new AudioPacket(this.seq, this.timestamp, ssrc, rawAudio)
                            .asEncryptedUdpPacket(udpTargetAddress, secretKey,
                                    PacketUtil.getNonceData(encryptionMode, this.nonceSupplier));
                    if (!this.audioConnection.isSpeaking()) {
                        this.audioConnection.updateSpeaking(true);
                    }
                    if (this.seq + 1 > Character.MAX_VALUE) {
                        this.seq = 0;
                    } else {
                        this.seq++;
                    }
                }
            } else if (this.audioConnection.isSpeaking() && changeTalking) {
                this.audioConnection.updateSpeaking(false);
            }
        } catch (final Exception e) {
            log.error("Failed to get next packet", e);
        }

        if (nextPacket != null) {
            this.timestamp += AudioConnection.OPUS_FRAME_SIZE;
        }

        return nextPacket;
    }
}
