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

import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.reactive.socket.WebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.api.MdcKey;
import space.npstr.magma.api.Member;
import space.npstr.magma.api.WebsocketConnectionState;
import space.npstr.magma.api.event.WebSocketClosedApiEvent;
import space.npstr.magma.impl.EncryptionMode;
import space.npstr.magma.impl.connections.hax.ClosingWebSocketClient;
import space.npstr.magma.impl.events.audio.lifecycle.CloseWebSocket;
import space.npstr.magma.impl.events.audio.lifecycle.CloseWebSocketLcEvent;
import space.npstr.magma.impl.events.audio.ws.CloseCode;
import space.npstr.magma.impl.events.audio.ws.Speaking;
import space.npstr.magma.impl.events.audio.ws.SpeakingWsEvent;
import space.npstr.magma.impl.events.audio.ws.in.ClientDisconnect;
import space.npstr.magma.impl.events.audio.ws.in.HeartbeatAck;
import space.npstr.magma.impl.events.audio.ws.in.Hello;
import space.npstr.magma.impl.events.audio.ws.in.Ignored;
import space.npstr.magma.impl.events.audio.ws.in.InboundWsEvent;
import space.npstr.magma.impl.events.audio.ws.in.Ready;
import space.npstr.magma.impl.events.audio.ws.in.Resumed;
import space.npstr.magma.impl.events.audio.ws.in.SessionDescription;
import space.npstr.magma.impl.events.audio.ws.in.Unknown;
import space.npstr.magma.impl.events.audio.ws.in.WebSocketClosed;
import space.npstr.magma.impl.events.audio.ws.out.HeartbeatWsEvent;
import space.npstr.magma.impl.events.audio.ws.out.IdentifyWsEvent;
import space.npstr.magma.impl.events.audio.ws.out.OutboundWsEvent;
import space.npstr.magma.impl.events.audio.ws.out.ResumeWsEvent;
import space.npstr.magma.impl.events.audio.ws.out.SelectProtocolWsEvent;
import space.npstr.magma.impl.immutables.SessionInfo;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Created by napster on 19.04.18.
 * <p>
 * Handle the lifecycle of the Discord voice websocket connection.
 */
public class AudioWebSocket extends BaseSubscriber<InboundWsEvent> {

    private static final Logger log = LoggerFactory.getLogger(AudioWebSocket.class);

    private final SessionInfo session;
    private final URI wssEndpoint;
    private final AudioConnection audioConnection;
    private final Consumer<CloseWebSocket> closeCallback;
    private final ClosingWebSocketClient webSocketClient;

    private final UnicastProcessor<OutboundWsEvent> webSocketProcessor;
    private final FluxSink<OutboundWsEvent> webSocketSink;
    private final UnicastProcessor<OutboundWsEvent> readyWebsocketProcessor;
    private final FluxSink<OutboundWsEvent> readyWebsocketSink;
    private final AudioWebSocketSessionHandler webSocketHandler;


    @Nullable
    private Disposable heartbeatSubscription;
    private Disposable webSocketConnection;

    private WebsocketConnectionState.Phase connectionPhase = WebsocketConnectionState.Phase.CONNECTING;


    public AudioWebSocket(final IAudioSendFactory sendFactory, final SessionInfo session,
                          final ClosingWebSocketClient webSocketClient, final Consumer<CloseWebSocket> closeCallback) {
        this.session = session;
        try {
            this.wssEndpoint = new URI(String.format("wss://%s/?v=4", session.getVoiceServerUpdate().getEndpoint()));
        } catch (final URISyntaxException e) {
            throw new RuntimeException("Endpoint " + session.getVoiceServerUpdate().getEndpoint() + " is not a valid URI", e);
        }
        this.audioConnection = new AudioConnection(this, sendFactory);
        this.closeCallback = closeCallback;
        this.webSocketClient = webSocketClient;


        this.webSocketProcessor = UnicastProcessor.create();
        this.webSocketSink = this.webSocketProcessor.sink();

        this.readyWebsocketProcessor = UnicastProcessor.create();
        this.readyWebsocketSink = this.readyWebsocketProcessor.sink();

        this.webSocketHandler = new AudioWebSocketSessionHandler(this);
        this.webSocketProcessor.subscribe(this.webSocketHandler);
        this.webSocketConnection = this.connect(this.webSocketClient, this.wssEndpoint, this.webSocketHandler);
    }

    public AudioConnection getAudioConnection() {
        return this.audioConnection;
    }

    public void setSpeaking(final boolean isSpeaking, final int ssrc) {
        final int speakingMask = isSpeaking ? 1 : 0;
        sendWhenReady(SpeakingWsEvent.builder()
                .speakingMask(speakingMask)
                .ssrc(ssrc)
                .build());
    }

    public SessionInfo getSession() {
        return this.session;
    }

    public void close() {
        this.closeEverything();
    }

    public WebsocketConnectionState.Phase getConnectionPhase() {
        return this.connectionPhase;
    }

    // ################################################################################
    // #                        Inbound event handlers
    // ################################################################################

    /**
     * Process the incoming events from the websocket.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    protected void hookOnNext(final InboundWsEvent inboundEvent) {
        try (
                final MDC.MDCCloseable ignored = MDC.putCloseable(MdcKey.GUILD, this.session.getVoiceServerUpdate().getGuildId());
                final MDC.MDCCloseable ignored2 = MDC.putCloseable(MdcKey.BOT, this.session.getUserId())
        ) {
            if (inboundEvent instanceof Hello) {
                this.handleHello((Hello) inboundEvent);
            } else if (inboundEvent instanceof Ready) {
                this.handleReady((Ready) inboundEvent);
            } else if (inboundEvent instanceof SessionDescription) {
                this.handleSessionDescription((SessionDescription) inboundEvent);
            } else if (inboundEvent instanceof HeartbeatAck) {
                // noop
            } else if (inboundEvent instanceof WebSocketClosed) {
                this.handleWebSocketClosed((WebSocketClosed) inboundEvent);
            } else if (inboundEvent instanceof ClientDisconnect) {
                // noop
            } else if (inboundEvent instanceof Speaking) {
                // noop
            } else if (inboundEvent instanceof Resumed) {
                this.handleResumed();
            } else if (inboundEvent instanceof Ignored) {
                log.trace("Ignored OP {}, payload: {}", inboundEvent.getOpCode(), ((Ignored) inboundEvent).getPayload());
            } else if (inboundEvent instanceof Unknown) {
                log.warn("Unknown OP {}, payload: {}", inboundEvent.getOpCode(), ((Unknown) inboundEvent).getPayload());
            } else {
                log.warn("WebSocket has no handler for event of class {}", inboundEvent.getClass().getSimpleName());
            }
        }
    }

    private void handleHello(final Hello hello) {
        log.trace("Hello");
        this.heartbeatSubscription = Flux.interval(Duration.ofMillis(hello.getHeartbeatIntervalMillis()))
                .doOnNext(tick -> {
                    try (
                            final MDC.MDCCloseable ignored = MDC.putCloseable(MdcKey.GUILD, this.session.getVoiceServerUpdate().getGuildId());
                            final MDC.MDCCloseable ignored2 = MDC.putCloseable(MdcKey.BOT, this.session.getUserId())
                    ) {
                        log.trace("Sending heartbeat {}", tick);
                    }
                })
                .publishOn(Schedulers.parallel())
                .subscribe(tick -> send(HeartbeatWsEvent.builder()
                        .nonce(tick.intValue())
                        .build())
                );

        send(IdentifyWsEvent.builder()
                .userId(this.session.getUserId())
                .guildId(this.session.getVoiceServerUpdate().getGuildId())
                .sessionId(this.session.getVoiceServerUpdate().getSessionId())
                .token(this.session.getVoiceServerUpdate().getToken())
                .build()
        );
    }

    private void handleReady(final Ready ready) {
        log.trace("Ready");
        this.connectionPhase = WebsocketConnectionState.Phase.CONNECTED;
        final InetSocketAddress udpTargetAddress = new InetSocketAddress(ready.getIp(), ready.getPort());
        final List<EncryptionMode> receivedModes = ready.getEncryptionModes();
        final Optional<EncryptionMode> preferredModeOpt = EncryptionMode.getPreferredMode(receivedModes);
        if (!preferredModeOpt.isPresent()) {
            final String modes = receivedModes.isEmpty()
                    ? "empty list"
                    : receivedModes.stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new RuntimeException("Failed to select encryption modes from " + modes); //todo how are exceptions handled?
        }
        final EncryptionMode preferredMode = preferredModeOpt.get();
        log.debug("Selecting encryption mode {}", preferredMode);

        //attach ready event sink to the full event sink
        this.readyWebsocketProcessor.subscribe(this.webSocketProcessor);

        this.audioConnection.handleUdpDiscovery(udpTargetAddress, ready.getSsrc())
                .publishOn(Schedulers.parallel())
                .subscribe(externalAddress -> sendWhenReady(
                        SelectProtocolWsEvent.builder()
                                .protocol("udp")
                                .host(externalAddress.getHostString())
                                .port(externalAddress.getPort())
                                .encryptionMode(preferredMode)
                                .build()));
    }

    private void handleSessionDescription(final SessionDescription sessionDescription) {
        log.trace("Session description");
        this.audioConnection.setSecretKey(sessionDescription.getSecretKey());
        this.audioConnection.setEncryptionMode(sessionDescription.getEncryptionMode());
    }

    private void handleWebSocketClosed(final WebSocketClosed webSocketClosed) {
        this.connectionPhase = WebsocketConnectionState.Phase.DISCONNECTED;
        final int code = webSocketClosed.getCode();
        final String reason = webSocketClosed.getReason();
        log.info("Websocket to {} closed with code {} and reason {}",
                this.wssEndpoint, code, reason);

        final Optional<CloseCode> closeCodeOpt = CloseCode.parse(code);
        final boolean resume;
        if (closeCodeOpt.isPresent()) {
            final CloseCode closeCode = closeCodeOpt.get();
            resume = closeCode.shouldResume();
            if (closeCode.shouldWarn()) {
                if (closeCode == CloseCode.BROKEN) {
                    log.warn("Connection closed due to internet issues?");
                } else {
                    log.warn("Connection closed due to {} {}. This could indicate an issue with the magma library or "
                                    + "your usage of it. Please get in touch. https://github.com/napstr/Magma/issues",
                            closeCode, reason);
                }
            }
        } else {
            log.error("Received unknown close code {} with reason {}", code, reason);
            resume = false;
        }

        if (resume) {
            log.info("Resuming");
            this.connectionPhase = WebsocketConnectionState.Phase.RESUMING;
            this.webSocketConnection.dispose();
            this.webSocketHandler.prepareConnect();
            this.webSocketConnection = this.connect(this.webSocketClient, this.wssEndpoint, this.webSocketHandler);
            send(ResumeWsEvent.builder()
                    .guildId(this.session.getVoiceServerUpdate().getGuildId())
                    .sessionId(this.session.getVoiceServerUpdate().getSessionId())
                    .token(this.session.getVoiceServerUpdate().getToken())
                    .build());
        } else {
            log.info("Closing");
            final Member member = this.session.getVoiceServerUpdate().getMember();
            this.closeCallback.accept(CloseWebSocketLcEvent.builder()
                    .member(this.session.getVoiceServerUpdate().getMember())
                    .apiEvent(WebSocketClosedApiEvent.builder()
                            .member(member)
                            .closeCode(code)
                            .reason(reason)
                            .isByRemote(true)
                            .build())
                    .build());
        }
    }

    private void handleResumed() {
        this.connectionPhase = WebsocketConnectionState.Phase.CONNECTED;
    }

    // ################################################################################
    // #                                Internals
    // ################################################################################

    private Disposable connect(final ClosingWebSocketClient client, final URI endpoint, final WebSocketHandler handler) {
        return client.execute(endpoint, handler)
                .log(log.getName() + ".WebSocketConnection", Level.FINEST) //FINEST = TRACE
                .doOnError(t -> {
                    log.error("Exception in websocket connection, closing", t);
                    this.closeEverything();
                })
                .publishOn(Schedulers.parallel())
                .subscribe();
    }

    private void send(final OutboundWsEvent outboundWsEvent) {
        this.webSocketSink.next(outboundWsEvent);
    }

    //use for events that should only happen after we have identified (=after we receive the ready event) to avoid 4003s
    private void sendWhenReady(final OutboundWsEvent outboundWsEvent) {
        this.readyWebsocketSink.next(outboundWsEvent);
    }

    private void closeEverything() {
        log.trace("Closing everything");
        this.connectionPhase = WebsocketConnectionState.Phase.DISCONNECTED;
        this.webSocketHandler.close();
        this.webSocketConnection.dispose();
        if (this.heartbeatSubscription != null) {
            this.heartbeatSubscription.dispose();
        }
        this.audioConnection.shutdown();
    }
}
