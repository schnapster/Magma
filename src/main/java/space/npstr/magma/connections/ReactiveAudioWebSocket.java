package space.npstr.magma.connections;

import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.AudioStackLifecyclePipeline;
import space.npstr.magma.events.audio.lifecycle.CloseWebSocketLcEvent;
import space.npstr.magma.events.audio.ws.SpeakingWsEvent;
import space.npstr.magma.events.audio.ws.in.ClientDisconnect;
import space.npstr.magma.events.audio.ws.in.HeartbeatAck;
import space.npstr.magma.events.audio.ws.in.Hello;
import space.npstr.magma.events.audio.ws.in.Ignored;
import space.npstr.magma.events.audio.ws.in.InboundWsEvent;
import space.npstr.magma.events.audio.ws.in.Ready;
import space.npstr.magma.events.audio.ws.in.SessionDescription;
import space.npstr.magma.events.audio.ws.in.WebSocketClosed;
import space.npstr.magma.events.audio.ws.out.HeartbeatWsEvent;
import space.npstr.magma.events.audio.ws.out.IdentifyWsEvent;
import space.npstr.magma.events.audio.ws.out.OutboundWsEvent;
import space.npstr.magma.events.audio.ws.out.SelectProtocolWsEvent;
import space.npstr.magma.immutables.SessionInfo;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.logging.Level;

/**
 * Created by napster on 19.04.18.
 */
public class ReactiveAudioWebSocket extends BaseSubscriber<InboundWsEvent> {

    private static final Logger log = LoggerFactory.getLogger(ReactiveAudioWebSocket.class);

    public static final String V3_ENCRYPTION_MODE = "xsalsa20_poly1305";
    public static final int DISCORD_SECRET_KEY_LENGTH = 32;

    private final ReactiveAudioConnection audioConnection;
    private final AudioStackLifecyclePipeline lifecyclePipeline;

    private final SessionInfo session;

    @Nullable
    private Disposable heartbeatSubscription;

    private final ReactiveAudioWebSocketHandler webSocketHandler;
    private final Disposable webSocketConnection;


    //drop events into this thing to have them sent to discord
    private final FluxSink<OutboundWsEvent> audioWebSocketSink;


    public ReactiveAudioWebSocket(final IAudioSendFactory sendFactory, final SessionInfo session,
                                  final WebSocketClient webSocketClient, final AudioStackLifecyclePipeline lifecyclePipeline) {
        this.session = session;

        this.audioConnection = new ReactiveAudioConnection(this, sendFactory);
        this.lifecyclePipeline = lifecyclePipeline;

        final URI wssEndpoint;
        try {
            wssEndpoint = new URI(String.format("wss://%s/?v=3", session.getVoiceServerUpdate().getEndpoint()));
        } catch (final URISyntaxException e) {
            throw new RuntimeException("Endpoint " + session.getVoiceServerUpdate().getEndpoint() + " is not a valid URI", e);
        }


        // send events into this thing
        // the UnicastProcessor is thread-safe, we can call next() on it as much and from wherever we want
        final UnicastProcessor<OutboundWsEvent> webSocketProcessor = UnicastProcessor.create();
        this.audioWebSocketSink = webSocketProcessor.sink();

        this.webSocketHandler = new ReactiveAudioWebSocketHandler(webSocketProcessor, this, InboundWsEvent::from);

        this.webSocketConnection = webSocketClient.execute(wssEndpoint, this.webSocketHandler)
                .log(log.getName() + ".WebSocketConnection", Level.FINEST) //FINEST = TRACE
                .doOnTerminate(this::closeEverything)
                .doOnError(t -> log.error("Exception in websocket connection", t))
                .subscribeOn(Schedulers.single())
                .subscribe();
    }

    public ReactiveAudioConnection getAudioConnection() {
        return this.audioConnection;
    }

    public void setSpeaking(final boolean isSpeaking) {
        this.send(SpeakingWsEvent.builder()
                .isSpeaking(isSpeaking)
                .build());
    }

    public void close() {
        this.closeEverything();
    }

    // ################################################################################
    // #                        Inbound event handlers
    // ################################################################################

    /**
     * This place processes the incoming events from the websocket.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    protected void hookOnNext(final InboundWsEvent inboundEvent) {
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
        } else if (inboundEvent instanceof Ignored) {
            log.trace("Ignored OP {} event", inboundEvent.getOpCode());
        } else {
            log.warn("WebSocket has no handler for websocket event of class {}", inboundEvent.getClass().getSimpleName());
        }
    }

    private void handleHello(final Hello hello) {
        this.heartbeatSubscription = Flux.interval(Duration.ofMillis(hello.getHeartbeatIntervalMillis() / 2)) //FIXME v4 sends correct heartbeat intervals
                .doOnNext(tick -> log.trace("Sending heartbeat {}", tick))
                .subscribeOn(Schedulers.single())
                .subscribe(tick -> this.audioWebSocketSink.next(HeartbeatWsEvent.builder()
                        .nonce(tick.intValue())
                        .build())
                );

        this.audioWebSocketSink.next(IdentifyWsEvent.builder()
                .userId(this.session.getUserId())
                .guildId(this.session.getVoiceServerUpdate().getGuildId())
                .sessionId(this.session.getVoiceServerUpdate().getSessionId())
                .token(this.session.getVoiceServerUpdate().getToken())
                .build()
        );
    }

    private void handleReady(final Ready ready) {
        final InetSocketAddress udpTargetAddress = new InetSocketAddress(this.session.getVoiceServerUpdate().getEndpoint(), ready.getPort());
        this.audioConnection.handleUdpDiscovery(udpTargetAddress, ready.getSsrc())
                .subscribeOn(Schedulers.single())
                .subscribe(externalAddress -> this.audioWebSocketSink.next(
                        SelectProtocolWsEvent.builder()
                                .protocol("udp")
                                .host(externalAddress.getHostString())
                                .port(externalAddress.getPort())
                                .mode(ReactiveAudioWebSocket.V3_ENCRYPTION_MODE)
                                .build()));
    }

    private void handleSessionDescription(final SessionDescription sessionDescription) {
        this.audioConnection.updateSecretKey(sessionDescription.getSecretKey());
    }

    private void handleWebSocketClosed(final WebSocketClosed webSocketClosed) {
        final int code = webSocketClosed.getCode();
        log.trace("Websocket closed with code {} and reason {}", code, webSocketClosed.getReason());
        this.lifecyclePipeline.next(CloseWebSocketLcEvent.builder()
                .userId(this.session.getUserId())
                .guildId(this.session.getVoiceServerUpdate().getGuildId())
                .build());
    }

    // ################################################################################
    // #                                Internals
    // ################################################################################

    private void send(final OutboundWsEvent outboundWsEvent) {
        this.audioWebSocketSink.next(outboundWsEvent);
    }

    private void closeEverything() {
        log.trace("Closing everything");
        this.webSocketHandler.close();
        this.webSocketConnection.dispose();
        if (this.heartbeatSubscription != null) {
            this.heartbeatSubscription.dispose();
        }
        this.audioConnection.shutdown();
    }
}
