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

package space.npstr.magma.connections;

import com.iwebpp.crypto.TweetNaclFast;
import com.sun.jna.ptr.PointerByReference;
import net.dv8tion.jda.core.audio.AudioPacket;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import net.dv8tion.jda.core.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.core.audio.factory.IPacketProvider;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.EncryptionMode;
import space.npstr.magma.events.audio.lifecycle.UpdateSendHandler;
import tomp2p.opuswrapper.Opus;

import javax.annotation.Nullable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Created by napster on 20.04.18.
 *
 * Glue together the send handler and the send system.
 */
public class AudioConnection {

    private static final Logger log = LoggerFactory.getLogger(AudioConnection.class);

    // * * * values and documentation taken from jda-audio (Apache 2.0)
    public static final int DISCORD_SECRET_KEY_LENGTH = 32;
    public static final int OPUS_SAMPLE_RATE = 48000;   //(Hz) We want to use the highest of qualities! All the bandwidth!
    public static final int OPUS_FRAME_SIZE = 960;      //An opus frame size of 960 at 48000hz represents 20 milliseconds of audio.
    public static final int OPUS_CHANNEL_COUNT = 2;     //We want to use stereo. If the audio given is mono, the encoder promotes it
    // to Left and Right mono (stereo that is the same on both sides)
    // * * *

    public static final long MAX_UINT_32 = 4294967295L;

    private final IAudioSendFactory sendFactory;
    private final AudioWebSocket webSocket;
    private final DatagramSocket udpSocket;
    private final FluxSink<UpdateSendHandler> sendHandlerSink;
    private final Disposable audioConnectionSubscription;

    //udp connection info
    @Nullable
    private volatile InetSocketAddress udpTargetAddress;
    @Nullable
    private volatile Integer ssrc;
    @Nullable
    private volatile byte[] secretKey;
    @Nullable
    private volatile EncryptionMode encryptionMode;


    @Nullable
    private volatile AudioSendHandler sendHandler;
    @Nullable
    private PointerByReference opusEncoder;
    @Nullable
    private IAudioSendSystem sendSystem;

    private final AtomicLong nonce = new AtomicLong(0);

    private volatile boolean speaking = false;

    public AudioConnection(final AudioWebSocket webSocket, final IAudioSendFactory sendFactory) {
        try {
            this.udpSocket = new DatagramSocket();
        } catch (final SocketException e) {
            throw new RuntimeException("Failed to create udpSocket", e);
        }

        this.webSocket = webSocket;
        this.sendFactory = sendFactory;

        final UnicastProcessor<UpdateSendHandler> sendHandlerProcessor = UnicastProcessor.create();

        this.sendHandlerSink = sendHandlerProcessor.sink();
        this.audioConnectionSubscription = sendHandlerProcessor
                .subscribeOn(Schedulers.single())
                .subscribe(this::handleSendHandlerUpdate);
    }

    //todo eventify calls in this class?

    public void updateSecretKeyAndEncryptionMode(final byte[] secretKey, final EncryptionMode encryptionMode) {
        this.secretKey = secretKey;
        this.encryptionMode = encryptionMode;
        this.startSendSystemIfReady();
    }

    public void updateSendHandler(final UpdateSendHandler updateSendHandler) {
        this.sendHandlerSink.next(updateSendHandler);
    }

    void shutdown() {
        this.audioConnectionSubscription.dispose();
        this.setSpeaking(false);
        if (this.sendSystem != null) {
            this.sendSystem.shutdown();
            this.sendSystem = null;
        }
        if (this.opusEncoder != null) {
            Opus.INSTANCE.opus_encoder_destroy(this.opusEncoder);
            this.opusEncoder = null;
        }

        this.udpTargetAddress = null;
        this.ssrc = null;
        this.secretKey = null;
        this.sendHandler = null;
    }

    private void handleSendHandlerUpdate(final UpdateSendHandler event) {
        final Optional<AudioSendHandler> audioSendHandler = event.getAudioSendHandler();
        if (audioSendHandler.isPresent()) {
            this.setupSendSystem(audioSendHandler.get());
        } else {
            this.tearDownSendSystem();
        }
    }

    private void tearDownSendSystem() {
        if (this.sendSystem != null) {
            this.sendSystem.shutdown();
            this.sendSystem = null;
        }

        if (this.opusEncoder != null) {
            Opus.INSTANCE.opus_encoder_destroy(this.opusEncoder);
            this.opusEncoder = null;
        }
    }


    private void setupSendSystem(final AudioSendHandler sendHandler) {
        this.sendHandler = sendHandler;
        if (this.sendSystem == null) {
            final IntBuffer error = IntBuffer.allocate(4);
            if (this.opusEncoder != null) {
                Opus.INSTANCE.opus_encoder_destroy(this.opusEncoder);
            }
            this.opusEncoder = Opus.INSTANCE.opus_encoder_create(OPUS_SAMPLE_RATE, OPUS_CHANNEL_COUNT, Opus.OPUS_APPLICATION_AUDIO, error);

            this.sendSystem = this.sendFactory.createSendSystem(new PacketProvider());
        }
        this.startSendSystemIfReady();
    }

    private void startSendSystemIfReady() {
        final IAudioSendSystem sendSystem = this.sendSystem;
        if (this.sendHandler == null) {
            log.trace("Not ready cause no send handler");
            return;
        } else if (this.udpTargetAddress == null) {
            log.trace("Not ready cause no udp target address");
            return;
        } else if (this.ssrc == null) {
            log.trace("Not ready cause no ssrc");
            return;
        } else if (this.secretKey == null) {
            log.trace("Not ready cause no secret key");
            return;
        } else if (sendSystem == null) {
            log.trace("Not ready cause no send system");
            return;
        }

        log.trace("Ready, starting send system");
        sendSystem.start();
    }

    private void setSpeaking(final boolean isSpeaking) {
        this.speaking = isSpeaking;
        this.webSocket.setSpeaking(isSpeaking);
    }

    private class PacketProvider implements IPacketProvider {
        char seq = 0;           //Sequence of audio packets. Used to determine the order of the packets.
        int timestamp = 0;      //Used to sync up our packets within the same timeframe of other people talking.

        public PacketProvider() {
        }

        @Override
        public String getIdentifier() {
            throw new UnsupportedOperationException("This information is not available");
        }

        @Override
        public String getConnectedChannel() {
            throw new UnsupportedOperationException("This information is not available");
        }

        @Override
        public DatagramSocket getUdpSocket() {
            throw new UnsupportedOperationException("This information is not available");
        }

        //realistially, this is the only thing that is ever called by the NativeAudioSystem
        @Nullable
        @Override
        public DatagramPacket getNextPacket(final boolean changeTalking) {
            DatagramPacket nextPacket = null;

            final InetSocketAddress udpTargetAddress = AudioConnection.this.udpTargetAddress;
            final Integer ssrc = AudioConnection.this.ssrc;
            final byte[] secretKey = AudioConnection.this.secretKey;
            final EncryptionMode encryptionMode = AudioConnection.this.encryptionMode;
            final AudioSendHandler sendHandler = AudioConnection.this.sendHandler;

            try {
                if (udpTargetAddress != null
                        && ssrc != null
                        && secretKey != null
                        && encryptionMode != null
                        && sendHandler != null
                        && sendHandler.canProvide()) {
                    byte[] rawAudio = sendHandler.provide20MsAudio();
                    if (rawAudio == null || rawAudio.length == 0) {
                        if (AudioConnection.this.speaking && changeTalking)
                            AudioConnection.this.setSpeaking(false);
                    } else {
                        if (!sendHandler.isOpus()) {
                            rawAudio = AudioConnection.this.encodeToOpus(rawAudio);
                        }
                        nextPacket = this.getDatagramPacket(rawAudio, ssrc, encryptionMode);
                        if (!AudioConnection.this.speaking) {
                            AudioConnection.this.setSpeaking(true);
                        }

                        if (this.seq + 1 > Character.MAX_VALUE) {
                            this.seq = 0;
                        } else {
                            this.seq++;
                        }
                    }
                } else if (AudioConnection.this.speaking && changeTalking) {
                    AudioConnection.this.setSpeaking(false);
                }
            } catch (final Exception e) {
                log.error("Failed to get next packet", e);
            }

            if (nextPacket != null) {
                this.timestamp += AudioConnection.OPUS_FRAME_SIZE;
            }

            return nextPacket;
        }

        private DatagramPacket getDatagramPacket(final byte[] rawAudio, final int ssrc, final EncryptionMode encryptionMode) {
            final AudioPacket packet = new AudioPacket(this.seq, this.timestamp, ssrc, rawAudio);

            final byte[] nonceData;
            switch (encryptionMode) {
                case XSALSA20_POLY1305:
                    nonceData = null;
                    break;
                case XSALSA20_POLY1305_LITE:
                    final long nextNonce = AudioConnection.this.nonce.updateAndGet(n -> n >= MAX_UINT_32 ? 0 : n + 1);
                    nonceData = this.getNonceBytes(nextNonce);
                    break;
                case XSALSA20_POLY1305_SUFFIX:
                    nonceData = TweetNaclFast.randombytes(TweetNaclFast.SecretBox.nonceLength);
                    break;
                default:
                    throw new IllegalStateException("Encryption mode [" + encryptionMode + "] is not supported!");
            }
            return packet.asEncryptedUdpPacket(AudioConnection.this.udpTargetAddress, AudioConnection.this.secretKey, nonceData);
        }

        //@formatter:off
        private byte[] getNonceBytes(final long nonce) {
            final byte[] data = new byte[4];
            data[0] = (byte) ((nonce >>> 24) & 0xFF);
            data[1] = (byte) ((nonce >>> 16) & 0xFF);
            data[2] = (byte) ((nonce >>>  8) & 0xFF);
            data[3] = (byte) ( nonce         & 0xFF);
            return data;
        }
        //@formatter:on

        @Override
        public void onConnectionError(final ConnectionStatus status) {
            throw new UnsupportedOperationException("Connection error, on a udp connection...that's not a real thing.");
        }

        @Override
        public void onConnectionLost() {
            throw new UnsupportedOperationException("Connection lost, on a udp connection...that's not a real thing.");
        }
    }


    /**
     * The code of this method has been copied almost fully from the AudioConnection class of JDA-Audio
     */
    private byte[] encodeToOpus(final byte[] rawAudio) {
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
        final int result = Opus.INSTANCE.opus_encode(this.opusEncoder, nonEncodedBuffer, OPUS_FRAME_SIZE, encoded, encoded.capacity());

        //ENCODING STOPS HERE

        final byte[] audio = new byte[result];
        encoded.get(audio);
        return audio;
    }


    // ################################################################################
    // #                             Udp Discovery
    // ################################################################################

    public Mono<InetSocketAddress> handleUdpDiscovery(final InetSocketAddress targetAddress, final int ssrc) {

        final Supplier<InetSocketAddress> externalUdpAddressSupplier = () -> {
            InetSocketAddress externalAddress;
            int attempt = 0;
            do {
                log.trace("Attempt {} to discover udp", ++attempt);
                externalAddress = this.discoverExternalUdpAddress(targetAddress, ssrc);
                if (externalAddress == null) {
                    try {
                        Thread.sleep(100); //dont flood in case of erors
                    } catch (final InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            } while (externalAddress == null && attempt < 100);

            if (externalAddress == null) {
                log.error("Failed to discover external udp address");
                return null;
            }

            log.trace("Udp discovered: {}", externalAddress);
            this.udpTargetAddress = targetAddress;
            this.ssrc = ssrc;
            this.startSendSystemIfReady();
            return externalAddress;
        };

        return Mono.fromSupplier(externalUdpAddressSupplier)
                .subscribeOn(Schedulers.elastic());//elastic scheduler is the correct choice for legacy blocking calls
    }

    /**
     * The code of this method has been copied almost fully from the AudioWebSocket class of JDA-Audio
     * <p>
     * NOTE: It does blocking things.
     */
    @Nullable
    private InetSocketAddress discoverExternalUdpAddress(final InetSocketAddress remoteAddress, final int ssrc) {
        //We will now send a packet to discord to punch a port hole in the NAT wall.
        //This is called UDP hole punching.
        try {

            //Create a byte array of length 70 containing our ssrc.
            final ByteBuffer buffer = ByteBuffer.allocate(70);    //70 taken from https://github.com/Rapptz/discord.py/blob/async/discord/voice_client.py#L208
            buffer.putInt(ssrc);                            //Put the ssrc that we were given into the packet to send back to discord.

            //Construct our packet to be sent loaded with the byte buffer we store the ssrc in.
            final DatagramPacket discoveryPacket = new DatagramPacket(buffer.array(), buffer.array().length, remoteAddress);
            this.udpSocket.send(discoveryPacket);

            //Discord responds to our packet, returning a packet containing our external ip and the port we connected through.
            final DatagramPacket receivedPacket = new DatagramPacket(new byte[70], 70);   //Give a buffer the same size as the one we sent.
            this.udpSocket.setSoTimeout(1000);
            this.udpSocket.receive(receivedPacket);

            //The byte array returned by discord containing our external ip and the port that we used
            //to connect to discord with.
            final byte[] received = receivedPacket.getData();

            //Example string:"   121.83.253.66                                                   ��"
            //You'll notice that there are 4 leading nulls and a large amount of nulls between the the ip and
            // the last 2 bytes. Not sure why these exist.  The last 2 bytes are the port. More info below.
            String ourIP = new String(received);//Puts the entire byte array in. nulls are converted to spaces.
            ourIP = ourIP.substring(4, ourIP.length() - 2); //Removes the port that is stuck on the end of this string. (last 2 bytes are the port)
            ourIP = ourIP.trim();                           //Removes the extra whitespace(nulls) attached to both sides of the IP

            //The port exists as the last 2 bytes in the packet data, and is encoded as an UNSIGNED short.
            //Furthermore, it is stored in Little Endian instead of normal Big Endian.
            //We will first need to convert the byte order from Little Endian to Big Endian (reverse the order)
            //Then we will need to deal with the fact that the bytes represent an unsigned short.
            //Java cannot deal with unsigned types, so we will have to promote the short to a higher type.
            //Options:  char or int.  I will be doing int because it is just easier to work with.
            final byte[] portBytes = new byte[2];                 //The port is exactly 2 bytes in size.
            portBytes[0] = received[received.length - 1];   //Get the second byte and store as the first
            portBytes[1] = received[received.length - 2];   //Get the first byte and store as the second.
            //We have now effectively converted from Little Endian -> Big Endian by reversing the order.

            //For more information on how this is converting from an unsigned short to an int refer to:
            //http://www.darksleep.com/player/JavaAndUnsignedTypes.html
            final int firstByte = (0x000000FF & ((int) portBytes[0]));    //Promotes to int and handles the fact that it was unsigned.
            final int secondByte = (0x000000FF & ((int) portBytes[1]));   //

            //Combines the 2 bytes back together.
            final int ourPort = (firstByte << 8) | secondByte;

            return new InetSocketAddress(ourIP, ourPort);
        } catch (final Exception e) {
            log.trace("Exception when discovering udp", e);
            return null;
        }
    }

}
