/*
 * Copyright 2018-2019 Dennis Neufeld
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

package space.npstr.magma.impl;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.api.MagmaApi;
import space.npstr.magma.api.Member;
import space.npstr.magma.api.ServerUpdate;
import space.npstr.magma.api.SpeakingMode;
import space.npstr.magma.api.WebsocketConnectionState;
import space.npstr.magma.api.event.MagmaEvent;
import space.npstr.magma.api.event.WebSocketClosedApiEvent;
import space.npstr.magma.impl.connections.hax.ClosingReactorNettyWebSocketClient;
import space.npstr.magma.impl.connections.hax.ClosingWebSocketClient;
import space.npstr.magma.impl.events.audio.lifecycle.CloseWebSocketLcEvent;
import space.npstr.magma.impl.events.audio.lifecycle.LifecycleEvent;
import space.npstr.magma.impl.events.audio.lifecycle.Shutdown;
import space.npstr.magma.impl.events.audio.lifecycle.UpdateSendHandlerLcEvent;
import space.npstr.magma.impl.events.audio.lifecycle.UpdateSpeakingModeLcEvent;
import space.npstr.magma.impl.events.audio.lifecycle.VoiceServerUpdateLcEvent;

public class Magma implements MagmaApi {

    private static final Logger log = LoggerFactory.getLogger(Magma.class);

    private final FluxSink<LifecycleEvent> lifecycleSink;
    @Nullable
    private FluxSink<MagmaEvent> apiEventSink = null;
    private final Flux<MagmaEvent> apiEventFlux = Flux.create(sink -> this.apiEventSink = sink);
    private final AudioStackLifecyclePipeline lifecyclePipeline;
    private final DatagramSocket udpSocket;

    /**
     * @see MagmaApi
     */
    public Magma(final Function<Member, IAudioSendFactory> sendFactoryProvider) {
        final ClosingWebSocketClient webSocketClient = new ClosingReactorNettyWebSocketClient();
        try {
            this.udpSocket = new DatagramSocket();
        } catch (final SocketException e) {
            throw new RuntimeException("Failed to set up datagram socket", e);
        }

        this.lifecyclePipeline = new AudioStackLifecyclePipeline(
                sendFactoryProvider,
                webSocketClient,
                magmaEvent -> {
                    if (this.apiEventSink != null) this.apiEventSink.next(magmaEvent);
                },
                this.udpSocket
        );

        final UnicastProcessor<LifecycleEvent> processor = UnicastProcessor.create();
        this.lifecycleSink = processor.sink();
        processor
                .log(log.getName(), Level.FINEST) //FINEST = TRACE
                .publishOn(Schedulers.parallel())
                .subscribe(this.lifecyclePipeline);
    }

    // ################################################################################
    // #                            Public API
    // ################################################################################

    @Override
    public DatagramSocket getDatagramSocket() {
        return this.udpSocket;
    }

    @Override
    public void shutdown() {
        this.lifecycleSink.next(Shutdown.INSTANCE);
        if (this.apiEventSink != null) this.apiEventSink.complete();
        this.udpSocket.close();
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
    public void setSpeakingMode(final Member member, @Nullable final Set<SpeakingMode> mode) {
        this.lifecycleSink.next(UpdateSpeakingModeLcEvent.builder()
                .member(member)
                .speakingModes(mode)
                .build());
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

    @Override
    public List<WebsocketConnectionState> getAudioConnectionStates() {
        return this.lifecyclePipeline.getAudioConnectionStates();
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
