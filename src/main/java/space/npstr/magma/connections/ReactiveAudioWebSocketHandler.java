package space.npstr.magma.connections;

import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.events.audio.ws.in.InboundWsEvent;
import space.npstr.magma.events.audio.ws.out.OutboundWsEvent;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Created by napster on 21.04.18.
 */
public class ReactiveAudioWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ReactiveAudioWebSocketHandler.class);
    private final Subscriber<InboundWsEvent> inbound;
    private final Function<String, InboundWsEvent> inboundEventCreator;

    @SuppressWarnings("NullableProblems") //is never actually null
    private volatile Flux<OutboundWsEvent> intermediaryOutbound;
    @SuppressWarnings("NullableProblems") //is never actually null
    private volatile FluxSink<OutboundWsEvent> intermediaryOutboundSink;
    @Nullable
    private WebSocketSession session;

    public ReactiveAudioWebSocketHandler(final Flux<OutboundWsEvent> outbound, final Subscriber<InboundWsEvent> inbound,
                                         final Function<String, InboundWsEvent> inboundEventCreator) {
        this.prepareConnect();
        outbound.subscribe(this::process);
        this.inbound = inbound;
        this.inboundEventCreator = inboundEventCreator;
    }

    public void close() {
        if (this.session != null) {
            this.session.close()
                    .subscribeOn(Schedulers.single())
                    .subscribe();
        }
    }

    private void process(final OutboundWsEvent event) {
        this.intermediaryOutboundSink.next(event);
    }

    /**
     * Call this when planning to reuse this handler for another session.
     * We need to replace the intermediary processor so that the new session can be subscribed to the original
     * {@code Flux<OutboundWsEvent> outbound} constructor parameter.
     * <p>
     * Any buffered outbound events will be lost.
     */
    public void prepareConnect() {
        final UnicastProcessor<OutboundWsEvent> intermediaryProcessor = UnicastProcessor.create();
        this.intermediaryOutboundSink = intermediaryProcessor.sink();
        this.intermediaryOutbound = intermediaryProcessor;
    }

    @Override
    public Mono<Void> handle(final WebSocketSession session) {

        // * * *
        // ATTENTION: Live testing code for resuming. Do not commit uncommented
//        Mono.delay(Duration.ofSeconds(30))
//                .subscribe(tick -> {
//                    session.close(new CloseStatus(CloseCode.VOICE_SERVER_CRASHED, "lol"));
//                    ((ReactiveAudioWebSocket) this.inbound).hookOnNext(WebSocketClosedWsEvent.builder()
//                            .code(CloseCode.VOICE_SERVER_CRASHED)
//                            .reason("lol")
//                            .build());
//                });
        // * * *

        this.session = session;
        log.trace(session.getHandshakeInfo().toString());
        session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .log(log.getName() + ".>>>", Level.FINEST) //FINEST = TRACE
                .map(this.inboundEventCreator)
                .doOnTerminate(() -> log.trace("Receiving terminated"))
                .subscribeOn(Schedulers.single())
                .subscribe(this.inbound);

        return session
                .send(this.intermediaryOutbound
                        .map(OutboundWsEvent::asMessage)
                        .log(log.getName() + ".<<<", Level.FINEST) //FINEST = TRACE
                        .map(session::textMessage)
                )
                .doOnTerminate(() -> log.trace("Sending terminated"));
    }
}
