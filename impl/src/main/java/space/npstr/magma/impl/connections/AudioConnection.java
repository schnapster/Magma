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

package space.npstr.magma.impl.connections;

import edu.umd.cs.findbugs.annotations.Nullable;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import net.dv8tion.jda.core.audio.factory.IAudioSendSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.api.MdcKey;
import space.npstr.magma.api.SpeakingMode;
import space.npstr.magma.impl.EncryptionMode;
import space.npstr.magma.impl.events.audio.conn.ConnectionEvent;
import space.npstr.magma.impl.events.audio.conn.SetEncryptionMode;
import space.npstr.magma.impl.events.audio.conn.SetSecretKey;
import space.npstr.magma.impl.events.audio.conn.SetSsrc;
import space.npstr.magma.impl.events.audio.conn.SetTargetAddress;
import space.npstr.magma.impl.events.audio.conn.Shutdown;
import space.npstr.magma.impl.events.audio.conn.UpdateSendHandler;
import space.npstr.magma.impl.events.audio.conn.UpdateSpeaking;
import space.npstr.magma.impl.processing.PacketProvider;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Created by napster on 20.04.18.
 * <p>
 * Glue together the send handler and the send system, as well as all udp and related stateful information of the
 * connection.
 */
public class AudioConnection extends BaseSubscriber<ConnectionEvent> {

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
    private final FluxSink<ConnectionEvent> audioConnectionEventSink;
    private Set<SpeakingMode> speakingModes = EnumSet.of(SpeakingMode.VOICE);

    // udp connection info
    @Nullable
    private EncryptionMode encryptionMode;
    @Nullable
    private byte[] secretKey;
    @Nullable
    private Integer ssrc;
    @Nullable
    private InetSocketAddress udpTargetAddress;


    // audio processing/sending components
    @Nullable
    private AudioSendHandler sendHandler;
    @Nullable
    private IAudioSendSystem sendSystem;

    // stateful information of an ongoing connection
    private final AtomicLong nonce = new AtomicLong(0);
    private final Supplier<Long> nonceSupplier;
    private boolean speaking = false;

    public AudioConnection(final AudioWebSocket webSocket, final IAudioSendFactory sendFactory) {
        try {
            this.udpSocket = new DatagramSocket();
        } catch (final SocketException e) {
            throw new RuntimeException("Failed to create udpSocket", e);
        }

        this.webSocket = webSocket;
        this.sendFactory = sendFactory;

        final UnicastProcessor<ConnectionEvent> audioConnectionProcessor = UnicastProcessor.create();
        this.audioConnectionEventSink = audioConnectionProcessor.sink();

        this.nonceSupplier = () -> this.nonce.updateAndGet(n -> n >= AudioConnection.MAX_UINT_32 ? 0 : n + 1);

        audioConnectionProcessor
                .publishOn(Schedulers.parallel())
                .subscribe(this);
    }


    // ################################################################################
    // #                            API of this class
    // ################################################################################

    public Set<SpeakingMode> getSpeakingModes() {
        return this.speakingModes;
    }

    public void setSpeakingModes(@Nullable Set<SpeakingMode> speakingModes) {
        this.speakingModes = (speakingModes == null || speakingModes.isEmpty())
                ? EnumSet.of(SpeakingMode.VOICE)
                : speakingModes;
    }

    public DatagramSocket getUdpSocket() {
        return this.udpSocket;
    }

    @Nullable
    public EncryptionMode getEncryptionMode() {
        return this.encryptionMode;
    }

    @Nullable
    public byte[] getSecretKey() {
        return this.secretKey;
    }

    @Nullable
    public Integer getSsrc() {
        return this.ssrc;
    }

    @Nullable
    public InetSocketAddress getUdpTargetAddress() {
        return this.udpTargetAddress;
    }

    @Nullable
    public AudioSendHandler getSendHandler() {
        return this.sendHandler;
    }

    public boolean isSpeaking() {
        return this.speaking;
    }

    public void setEncryptionMode(final EncryptionMode value) {
        this.audioConnectionEventSink.next(((SetEncryptionMode) () -> value));
    }

    public void setSecretKey(final byte[] value) {
        this.audioConnectionEventSink.next(((SetSecretKey) () -> value));
    }

    public void setSsrc(final int ssrc) {
        this.audioConnectionEventSink.next(((SetSsrc) () -> ssrc));
    }

    public void setTargetAddress(final InetSocketAddress targetAddress) {
        this.audioConnectionEventSink.next(((SetTargetAddress) () -> targetAddress));
    }

    public void updateSendHandler(@Nullable final AudioSendHandler sendHandler) {
        this.audioConnectionEventSink.next(((UpdateSendHandler) () -> Optional.ofNullable(sendHandler)));
    }

    /**
     * This may be repeatedly called, but will only result in an event sent when the requested speaking state actually
     * differs from the speaking state at the time this event is processed by the connection.
     */
    public void updateSpeaking(final boolean shouldSpeak) {
        this.audioConnectionEventSink.next(new UpdateSpeaking(shouldSpeak, this.speakingModes));
    }

    public void shutdown() {
        this.audioConnectionEventSink.next(Shutdown.INSTANCE);
    }


    // ################################################################################
    // #                            Event handling
    // ################################################################################

    @Override
    protected void hookOnNext(final ConnectionEvent event) {
        try (
                final MDC.MDCCloseable ignored = MDC.putCloseable(MdcKey.GUILD, this.webSocket.getSession().getVoiceServerUpdate().getGuildId());
                final MDC.MDCCloseable ignored2 = MDC.putCloseable(MdcKey.BOT, this.webSocket.getSession().getUserId())
        ) {
            if (event instanceof SetEncryptionMode) {
                this.encryptionMode = ((SetEncryptionMode) event).getEncryptionMode();
                this.startSendSystemIfReady();
            } else if (event instanceof SetSecretKey) {
                this.secretKey = ((SetSecretKey) event).getSecretKey();
                this.startSendSystemIfReady();
            } else if (event instanceof SetSsrc) {
                this.ssrc = ((SetSsrc) event).getSsrc();
                this.startSendSystemIfReady();
            } else if (event instanceof SetTargetAddress) {
                this.udpTargetAddress = ((SetTargetAddress) event).getTargetAddress();
                this.startSendSystemIfReady();
            } else if (event instanceof UpdateSendHandler) {
                this.handleSendHandlerUpdate((UpdateSendHandler) event);
            } else if (event instanceof UpdateSpeaking) {
                this.handleSpeakingUpdate((UpdateSpeaking) event);
            } else if (event instanceof Shutdown) {
                this.handleShutdown();
            } else {
                log.warn("AudioConnection has no handler for event of class {}", event.getClass().getSimpleName());
            }
        }
    }

    private void handleSendHandlerUpdate(final UpdateSendHandler event) {
        final Optional<AudioSendHandler> audioSendHandler = event.getAudioSendHandler();
        if (audioSendHandler.isPresent()) {
            this.setupSendComponents(audioSendHandler.get());
            this.startSendSystemIfReady();
        } else {
            this.tearDownSendComponents();
        }
    }

    private void handleSpeakingUpdate(final UpdateSpeaking event) {
        if (this.speaking != event.shouldSpeak()) {
            this.setSpeaking(event.getSpeakingMode());
        }
    }

    private void setSpeaking(final int speaking) {
        if (this.ssrc != null) {
            log.trace("Setting speaking to {}", speaking);
            this.speaking = speaking != 0;
            this.webSocket.setSpeaking(speaking, this.ssrc);
        } else {
            log.trace("Not setting speaking to {} due to missing ssrc", speaking);
        }
    }

    private void handleShutdown() {
        log.trace("Shutting down");
        this.setSpeaking(0);
        this.tearDownSendComponents();

        this.encryptionMode = null;
        this.secretKey = null;
        this.ssrc = null;
        this.udpTargetAddress = null;

        this.dispose();
    }


    private void tearDownSendComponents() {
        log.trace("Thread {} is tearing down audio components", Thread.currentThread().getName());
        this.sendHandler = null;
        if (this.sendSystem != null) {
            this.sendSystem.shutdown();
            this.sendSystem = null;
        }
    }

    private void setupSendComponents(final AudioSendHandler sendHandler) {
        log.trace("Thread {} is setting up audio components", Thread.currentThread().getName());
        if (!sendHandler.isOpus()) {
            throw new IllegalArgumentException("Magma does not support non-opus audio providers. Please use lavaplayer.");
        }
        this.sendHandler = sendHandler;
        if (this.sendSystem == null) {
            final PacketProvider packetProvider = new PacketProvider(this, this.nonceSupplier);
            this.sendSystem = this.sendFactory.createSendSystem(packetProvider);
        }
    }

    private void startSendSystemIfReady() {
        //check udp connection info
        if (this.encryptionMode == null) {
            log.trace("Not ready cause no encryption mode");
            return;
        } else if (this.secretKey == null) {
            log.trace("Not ready cause no secret key");
            return;
        } else if (this.ssrc == null) {
            log.trace("Not ready cause no ssrc");
            return;
        } else if (this.udpTargetAddress == null) {
            log.trace("Not ready cause no udp target address");
            return;
        }

        //check audio processing/sending components
        if (this.sendHandler == null) {
            log.trace("Not ready cause no send handler");
            return;
        } else if (this.sendSystem == null) {
            log.trace("Not ready cause no send system");
            return;
        }

        log.trace("Ready, starting send system");
        this.sendSystem.start();
    }


    // ################################################################################
    // #                             Udp Discovery
    // ################################################################################

    public Mono<InetSocketAddress> handleUdpDiscovery(final InetSocketAddress targetAddress, final int ssrc) {

        final Supplier<InetSocketAddress> externalUdpAddressSupplier = () -> {
            log.trace("Discovering udp on thread {}", Thread.currentThread().getName());
            if (Schedulers.isInNonBlockingThread()) {
                log.warn("Blocking udp discovery running in non-blocking thread {}.", Thread.currentThread().getName());
            }
            InetSocketAddress externalAddress;
            int attempt = 0;
            do {
                log.trace("Attempt {} to discover udp", ++attempt);
                externalAddress = this.discoverExternalUdpAddress(targetAddress, ssrc);
                if (externalAddress == null) {
                    try {
                        Thread.sleep(100); //dont flood in case of errors
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
            this.setTargetAddress(targetAddress);
            this.setSsrc(ssrc);
            return externalAddress;
        };

        return Mono.fromSupplier(externalUdpAddressSupplier)
                .publishOn(Schedulers.elastic());//elastic scheduler is the correct choice for legacy blocking calls
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
