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
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.impl.events.audio.ws.in.InboundWsEvent;
import space.npstr.magma.impl.events.audio.ws.out.OutboundWsEvent;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Created by napster on 21.04.18.
 */
public class AudioWebSocketSessionHandler extends BaseSubscriber<OutboundWsEvent> implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AudioWebSocketSessionHandler.class);
    private final Subscriber<InboundWsEvent> inbound;

    private final IntermediaryPipeHolder pipes = new IntermediaryPipeHolder();
    @Nullable
    private WebSocketSession session;

    /**
     * @param inbound
     *         Subcriber to the events we will receive from Discord
     */
    public AudioWebSocketSessionHandler(final Subscriber<InboundWsEvent> inbound) {
        this.prepareConnect();
        this.inbound = inbound;
    }

    /**
     * Close the session of this handler, if there is any.
     */
    public void close() {
        if (this.session != null) {
            this.session.close()
                    .publishOn(Schedulers.parallel())
                    .subscribe();
        }
    }

    /**
     * Call this when planning to reuse this handler for another session.
     * We need to replace the intermediary processor so that the new session can be subscribed to the original
     * {@code Flux<OutboundWsEvent> outbound} constructor parameter.
     * <p>
     * Any outbound events buffered in the old processor will be lost upon calling this, which is ok,
     * given that this method is expected to be called when the connection has been closed.
     */
    public void prepareConnect() {
        final UnicastProcessor<OutboundWsEvent> intermediaryProcessor = UnicastProcessor.create();
        this.pipes.setIntermediaryOutboundSink(intermediaryProcessor.sink());
        this.pipes.setIntermediaryOutboundFlux(intermediaryProcessor);
    }

    @Override
    public Mono<Void> handle(final WebSocketSession session) {

        // * * *
        // ATTENTION: Live testing code for resuming. Do not commit uncommented
//        Mono.delay(Duration.ofSeconds(30))
//                .subscribe(tick -> {
//                    session.close(new CloseStatus(CloseCode.VOICE_SERVER_CRASHED, "lol"));
//                    ((AudioWebSocket) this.inbound).hookOnNext(WebSocketClosedWsEvent.builder()
//                            .code(CloseCode.VOICE_SERVER_CRASHED)
//                            .reason("lol")
//                            .build());
//                });
        // * * *

        this.session = session;
        log.trace("Handshake: {}", session.getHandshakeInfo());
        session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .log(log.getName() + ".>>>", Level.FINEST) //FINEST = TRACE
                .map(InboundWsEvent::from)
                .doOnTerminate(() -> log.trace("Receiving terminated"))
                .publishOn(Schedulers.parallel())
                .subscribe(this.inbound);

        return session
                .send(this.pipes.getIntermediaryOutboundFlux()
                        .map(OutboundWsEvent::asMessage)
                        .log(log.getName() + ".<<<", Level.FINEST) //FINEST = TRACE
                        .map(session::textMessage)
                )
                .doOnTerminate(() -> log.trace("Sending terminated"));
    }

    @Override
    protected void hookOnNext(final OutboundWsEvent event) {
        this.pipes.getIntermediaryOutboundSink().next(event);
    }

    /**
     * Helper class to take care of volatile & null checks
     */
    private static class IntermediaryPipeHolder {
        @Nullable
        private volatile Flux<OutboundWsEvent> intermediaryOutboundFlux;
        @Nullable
        private volatile FluxSink<OutboundWsEvent> intermediaryOutboundSink;

        private Flux<OutboundWsEvent> getIntermediaryOutboundFlux() {
            return Objects.requireNonNull(this.intermediaryOutboundFlux, "Using the intermediary outbound flux before it has been prepared");
        }

        private void setIntermediaryOutboundFlux(final Flux<OutboundWsEvent> intermediaryOutboundFlux) {
            this.intermediaryOutboundFlux = intermediaryOutboundFlux;
        }

        private FluxSink<OutboundWsEvent> getIntermediaryOutboundSink() {
            return Objects.requireNonNull(this.intermediaryOutboundSink, "Using the intermediary outbound sink before it has been prepared");
        }

        private void setIntermediaryOutboundSink(final FluxSink<OutboundWsEvent> intermediaryOutboundSink) {
            this.intermediaryOutboundSink = intermediaryOutboundSink;
        }
    }
}
