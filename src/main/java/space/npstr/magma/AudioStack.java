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

import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import space.npstr.magma.connections.AudioWebSocket;
import space.npstr.magma.connections.hax.ClosingWebSocketClient;
import space.npstr.magma.events.api.MagmaEvent;
import space.npstr.magma.events.audio.lifecycle.CloseWebSocket;
import space.npstr.magma.events.audio.lifecycle.ConnectWebSocket;
import space.npstr.magma.events.audio.lifecycle.LifecycleEvent;
import space.npstr.magma.events.audio.lifecycle.Shutdown;
import space.npstr.magma.events.audio.lifecycle.UpdateSendHandler;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Created by napster on 23.04.18.
 * <p>
 * One of these per user and guild. Glues together the SendHandler and the Connections (websocket + udp).
 *
 * @see AudioStackLifecyclePipeline
 */
public class AudioStack extends BaseSubscriber<LifecycleEvent> {

    private static final Logger log = LoggerFactory.getLogger(AudioStack.class);

    private final Member member;
    private final IAudioSendFactory sendFactory;
    private final ClosingWebSocketClient webSocketClient;
    private final Consumer<MagmaEvent> apiEventConsumer;

    private final FluxSink<LifecycleEvent> lifecycleSink;

    @Nullable
    private AudioWebSocket webSocket;
    @Nullable
    private AudioSendHandler sendHandler;


    public AudioStack(final Member member, final IAudioSendFactory sendFactory,
                      final ClosingWebSocketClient webSocketClient, Consumer<MagmaEvent> apiEventConsumer) {
        this.member = member;
        this.sendFactory = sendFactory;
        this.webSocketClient = webSocketClient;
        this.apiEventConsumer = apiEventConsumer;

        final UnicastProcessor<LifecycleEvent> lifecycleProcessor = UnicastProcessor.create();
        this.lifecycleSink = lifecycleProcessor.sink();
        lifecycleProcessor
                .publishOn(Schedulers.parallel())
                .subscribe(this);
    }


    public void next(final LifecycleEvent event) {
        if (!(event instanceof Shutdown)
                && !event.getGuildId().equals(this.member.getGuildId())) {
            throw new IllegalArgumentException(String.format("Passed a lifecycle event for guild %s to the audio stack of guild %s",
                    event.getGuildId(), this.member.getGuildId()));
        }

        this.lifecycleSink.next(event);
    }

    public WebsocketConnectionState.Phase getConnectionPhase() {
        final AudioWebSocket socket = this.webSocket;
        if (socket != null) {
            return socket.getConnectionPhase();
        }
        return WebsocketConnectionState.Phase.NO_CONNECTION;
    }

    @Override
    protected void hookOnNext(final LifecycleEvent event) {
        try (
                final MDC.MDCCloseable ignored = MDC.putCloseable(MdcKey.GUILD, this.member.getGuildId());
                final MDC.MDCCloseable ignored2 = MDC.putCloseable(MdcKey.BOT, this.member.getUserId())
        ) {
            if (event instanceof ConnectWebSocket) {
                this.handleConnectWebSocket((ConnectWebSocket) event);
            } else if (event instanceof UpdateSendHandler) {
                this.handleUpdateSendHandler((UpdateSendHandler) event);
            } else if (event instanceof CloseWebSocket) {
                this.handleCloseWebSocket((CloseWebSocket) event);
            } else if (event instanceof Shutdown) {
                this.handleShutdown();
            } else {
                log.warn("Audiostack has no handler for lifecycle event of class {}", event.getClass().getSimpleName());
            }
        }

    }


    private void handleConnectWebSocket(final ConnectWebSocket connectWebSocket) {
        log.trace("Connecting");

        if (this.webSocket != null) {
            if (connectWebSocket.getSessionInfo().equals(connectWebSocket.getSessionInfo())) {
                log.info("Discarding received connection request because it is identical to the already existing connection." +
                        " If you really want to reconnect, send a disconnect request first.");
                return;
            } else {
                this.webSocket.close();
            }
        }

        this.webSocket = new AudioWebSocket(this.sendFactory, connectWebSocket.getSessionInfo(),
                this.webSocketClient, this::next);
        if (this.sendHandler != null) {
            this.webSocket.getAudioConnection().updateSendHandler(this.sendHandler);
        }
    }

    private void handleUpdateSendHandler(final UpdateSendHandler updateSendHandler) {
        log.trace("Updating send handler");
        final AudioSendHandler sendHandlerInstance = updateSendHandler.getAudioSendHandler().orElse(null);
        this.sendHandler = sendHandlerInstance;

        if (this.webSocket != null) {
            this.webSocket.getAudioConnection().updateSendHandler(sendHandlerInstance);
        }
    }

    private void handleCloseWebSocket(CloseWebSocket event) {
        log.trace("Closing websocket");
        apiEventConsumer.accept(event.getApiEvent());
        if (this.webSocket != null) {
            this.webSocket.close();
            this.webSocket = null;
        }
    }

    private void handleShutdown() {
        log.trace("Shutting down");
        this.dispose();
        if (this.webSocket != null) {
            this.webSocket.close();
            this.webSocket = null;
        }
        this.sendHandler = null;
    }
}
