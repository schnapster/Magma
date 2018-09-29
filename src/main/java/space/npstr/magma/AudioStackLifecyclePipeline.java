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

import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BaseSubscriber;
import space.npstr.magma.connections.AudioConnection;
import space.npstr.magma.connections.AudioWebSocket;
import space.npstr.magma.connections.hax.ClosingWebSocketClient;
import space.npstr.magma.events.api.MagmaEvent;
import space.npstr.magma.events.audio.lifecycle.CloseWebSocket;
import space.npstr.magma.events.audio.lifecycle.ConnectWebSocketLcEvent;
import space.npstr.magma.events.audio.lifecycle.LifecycleEvent;
import space.npstr.magma.events.audio.lifecycle.Shutdown;
import space.npstr.magma.events.audio.lifecycle.UpdateSendHandler;
import space.npstr.magma.events.audio.lifecycle.VoiceServerUpdate;
import space.npstr.magma.immutables.ImmutableSessionInfo;

import javax.annotation.CheckReturnValue;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by napster on 22.04.18.
 * <p>
 * This class manages the lifecycles of various AudioStack objects.
 * <p>
 * The {@link AudioStack} consists of:
 * - A websocket connection ( -> {@link AudioWebSocket}
 * - A voice packet emitter ( -> {@link AudioConnection}
 * - A send handler         ( -> {@link net.dv8tion.jda.core.audio.AudioSendHandler}, provided by user code)
 * - A send system          ( -> {@link net.dv8tion.jda.core.audio.factory.IAudioSendSystem} , provided by user code)
 * <p>
 * <p>
 * Lifecycle Events:
 * <p>
 * - Constructive Events:
 * -- VoiceServerUpdate telling us to connect to a voice server
 * -- Reconnects following certain close events
 * <p>
 * - Destructive Events:
 * -- Websocket close events
 * -- Shutdown
 * <p>
 * Neutral Events:
 * -- Setting and removing a send handler
 */
public class AudioStackLifecyclePipeline extends BaseSubscriber<LifecycleEvent> {

    private static final Logger log = LoggerFactory.getLogger(AudioStackLifecyclePipeline.class);

    // userId <-> guildId <-> audio stack
    // concurrency is handled by modifying this through a single thread eventloop only
    private final Map<String, Map<String, AudioStack>> audioStacks = new HashMap<>();

    private final Function<Member, IAudioSendFactory> sendFactoryProvider;
    private final ClosingWebSocketClient webSocketClient;
    private final Consumer<MagmaEvent> apiEventConsumer;

    public AudioStackLifecyclePipeline(final Function<Member, IAudioSendFactory> sendFactoryProvider,
                                       final ClosingWebSocketClient webSocketClient,
                                       final Consumer<MagmaEvent> apiEventConsumer) {
        this.sendFactoryProvider = sendFactoryProvider;
        this.webSocketClient = webSocketClient;
        this.apiEventConsumer = apiEventConsumer;
    }

    @Override
    protected void hookOnNext(final LifecycleEvent event) {
        if (event instanceof VoiceServerUpdate) {
            final VoiceServerUpdate voiceServerUpdate = (VoiceServerUpdate) event;
            this.getAudioStack(event)
                    .next(ConnectWebSocketLcEvent.builder()
                            .sessionInfo(ImmutableSessionInfo.builder()
                                    .voiceServerUpdate(voiceServerUpdate)
                                    .build())
                            .build()
                    );
        } else if (event instanceof UpdateSendHandler) {
            this.getAudioStack(event)
                    .next(event);
        } else if (event instanceof CloseWebSocket) {
            //pass it on
            apiEventConsumer.accept(((CloseWebSocket) event).getApiEvent());
            this.getAudioStack(event)
                    .next(event);
        } else if (event instanceof Shutdown) {
            this.dispose();

            this.audioStacks.values().stream().flatMap(map -> map.values().stream()).forEach(
                    audioStack -> audioStack.next(event)
            );
        } else {
            log.warn("Unhandled lifecycle event of class {}", event.getClass().getSimpleName());
        }
    }

    @CheckReturnValue
    private AudioStack getAudioStack(final LifecycleEvent lifecycleEvent) {
        return this.audioStacks
                .computeIfAbsent(lifecycleEvent.getUserId(), __ -> new HashMap<>())
                .computeIfAbsent(lifecycleEvent.getGuildId(), __ ->
                        new AudioStack(lifecycleEvent.getMember(),
                                this.sendFactoryProvider.apply(lifecycleEvent.getMember()),
                                this.webSocketClient,
                                apiEventConsumer));
    }
}
