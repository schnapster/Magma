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

package space.npstr.magma;

import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.websockets.client.WebSocketClient;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.connections.hax.ClosingUndertowWebSocketClient;
import space.npstr.magma.connections.hax.ClosingWebSocketClient;
import space.npstr.magma.events.api.MagmaEvent;
import space.npstr.magma.events.api.WebSocketClosed;
import space.npstr.magma.events.api.WebSocketClosedApiEvent;
import space.npstr.magma.events.audio.lifecycle.CloseWebSocketLcEvent;
import space.npstr.magma.events.audio.lifecycle.LifecycleEvent;
import space.npstr.magma.events.audio.lifecycle.Shutdown;
import space.npstr.magma.events.audio.lifecycle.UpdateSendHandlerLcEvent;
import space.npstr.magma.events.audio.lifecycle.VoiceServerUpdateLcEvent;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public class Magma implements MagmaApi {

    private static final Logger log = LoggerFactory.getLogger(Magma.class);

    // 16kb as recommended in
    // http://undertow.io/undertow-docs/undertow-docs-2.0.0/index.html#buffer-pool
    // and
    // http://undertow.io/undertow-docs/undertow-docs-2.0.0/index.html#the-undertow-buffer-pool
    // for direct buffers
    private static final int DEFAULT_POOL_BUFFER_SIZE = 16 * 1024;

    private final FluxSink<LifecycleEvent> lifecycleSink;
    @Nullable
    private FluxSink<MagmaEvent> apiEventSink = null;
    private final Flux<MagmaEvent> apiEventFlux = Flux.create(sink -> apiEventSink = sink);

    /**
     * @see MagmaApi
     */
    Magma(final Function<Member, IAudioSendFactory> sendFactoryProvider, final OptionMap xnioOptions) {
        final ClosingWebSocketClient webSocketClient;
        try {
            final XnioWorker xnioWorker = Xnio.getInstance().createWorker(xnioOptions);
            final ByteBufferPool bufferPool = new DefaultByteBufferPool(true, DEFAULT_POOL_BUFFER_SIZE);
            final XnioSsl xnioSsl = new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY);
            final Consumer<WebSocketClient.ConnectionBuilder> builderConsumer = builder -> builder.setSsl(xnioSsl);
            webSocketClient = new ClosingUndertowWebSocketClient(xnioWorker, bufferPool, builderConsumer);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to set up websocket client", e);
        }

        final Subscriber<LifecycleEvent> lifecyclePipeline = new AudioStackLifecyclePipeline(
                sendFactoryProvider,
                webSocketClient,
                magmaEvent -> {
                    if (this.apiEventSink != null) this.apiEventSink.next(magmaEvent);
                }
        );

        final UnicastProcessor<LifecycleEvent> processor = UnicastProcessor.create();
        this.lifecycleSink = processor.sink();
        processor
                .log(log.getName(), Level.FINEST) //FINEST = TRACE
                .publishOn(Schedulers.parallel())
                .subscribe(lifecyclePipeline);
    }

    // ################################################################################
    // #                            Public API
    // ################################################################################


    @Override
    public void shutdown() {
        this.lifecycleSink.next(Shutdown.INSTANCE);
        if (apiEventSink != null) apiEventSink.complete();
    }

    @Override
    public Flux<MagmaEvent> getEventStream() {
        return this.apiEventFlux;
    }

    @Override
    public void provideVoiceServerUpdate(final Member member, final ServerUpdate serverUpdate) {
        this.lifecycleSink.next(VoiceServerUpdateLcEvent.builder()
                .member(member)
                .sessionId(serverUpdate.getSessionId())
                .endpoint(serverUpdate.getEndpoint().replace(":80", "")) //Strip the port from the endpoint.
                .token(serverUpdate.getToken())
                .build());
    }

    @Override
    public void setSendHandler(final Member member, final AudioSendHandler sendHandler) {
        this.updateSendHandler(member, sendHandler);
    }

    @Override
    public void removeSendHandler(final Member member) {
        this.updateSendHandler(member, null);
    }

    @Override
    public void closeConnection(final Member member) {
        this.lifecycleSink.next(CloseWebSocketLcEvent.builder()
                .member(member)
                .apiEvent(WebSocketClosedApiEvent.builder()
                        .member(member)
                        .closeCode(1000)
                        .reason("Closed by client")
                        .isByRemote(false)
                        .build())
                .build());
    }

    // ################################################################################
    // #                             Internals
    // ################################################################################

    private void updateSendHandler(final Member member, @Nullable final AudioSendHandler sendHandler) {
        this.lifecycleSink.next(UpdateSendHandlerLcEvent.builder()
                .member(member)
                .audioSendHandler(Optional.ofNullable(sendHandler))
                .build());
    }
}
