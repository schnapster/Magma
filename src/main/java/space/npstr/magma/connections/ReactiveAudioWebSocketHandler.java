package space.npstr.magma.connections;

import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
    private final Flux<OutboundWsEvent> outgoing;
    private final Subscriber<InboundWsEvent> incoming;
    private final Function<String, InboundWsEvent> inboundEventCreator;

    @Nullable
    private WebSocketSession session;

    public ReactiveAudioWebSocketHandler(final Flux<OutboundWsEvent> outgoing, final Subscriber<InboundWsEvent> incoming,
                                         final Function<String, InboundWsEvent> inboundEventCreator) {
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.inboundEventCreator = inboundEventCreator;
    }

    public void close() {
        if (this.session != null) {
            this.session.close().subscribe();
        }
    }

    @Override
    public Mono<Void> handle(final WebSocketSession session) {
        this.session = session;
        log.trace(session.getHandshakeInfo().toString());
        session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .log(log.getName() + ".>>>", Level.FINEST) //FINEST = TRACE
                .map(this.inboundEventCreator)
                .doOnTerminate(() -> log.trace("Receiving terminated"))
                .subscribeOn(Schedulers.single())
                .subscribe(this.incoming);

        return session
                .send(this.outgoing
                        .map(OutboundWsEvent::asMessage)
                        .log(log.getName() + ".<<<", Level.FINEST) //FINEST = TRACE
                        .map(session::textMessage)
                )
                .doOnTerminate(() -> log.trace("Sending terminated"));
    }
}
