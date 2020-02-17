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

import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.Nullable;
import net.dv8tion.jda.api.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BaseSubscriber;
import space.npstr.magma.api.MagmaMember;
import space.npstr.magma.api.MagmaWebsocketConnectionState;
import space.npstr.magma.api.Member;
import space.npstr.magma.api.WebsocketConnectionState;
import space.npstr.magma.api.event.MagmaEvent;
import space.npstr.magma.impl.connections.AudioConnection;
import space.npstr.magma.impl.connections.AudioWebSocket;
import space.npstr.magma.impl.connections.hax.ClosingWebSocketClient;
import space.npstr.magma.impl.events.audio.lifecycle.CloseWebSocket;
import space.npstr.magma.impl.events.audio.lifecycle.ConnectWebSocketLcEvent;
import space.npstr.magma.impl.events.audio.lifecycle.LifecycleEvent;
import space.npstr.magma.impl.events.audio.lifecycle.Shutdown;
import space.npstr.magma.impl.events.audio.lifecycle.UpdateSendHandler;
import space.npstr.magma.impl.events.audio.lifecycle.UpdateSpeakingMode;
import space.npstr.magma.impl.events.audio.lifecycle.VoiceServerUpdate;
import space.npstr.magma.impl.immutables.ImmutableSessionInfo;

import java.net.DatagramSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by napster on 22.04.18.
 * <p>
 * This class manages the lifecycles of various AudioStack objects.
 * <p>
 * The {@link AudioStack} consists of:
 * <ul>
 *   <li>A websocket connection ( {@literal ->} {@link AudioWebSocket}</li>
 *   <li>A voice packet emitter ( {@literal ->} {@link AudioConnection}</li>
 *   <li>A send handler         ( {@literal ->} {@link net.dv8tion.jda.api.audio.AudioSendHandler}, provided by user code)</li>
 *   <li>A send system          ( {@literal ->} {@link net.dv8tion.jda.api.audio.factory.IAudioSendSystem}, provided by user code)</li>
 * </ul>
 *
 * <h2>Lifecycle Events</h2>
 * <ul>
 *   <li>Constructive Events
 *   <ul>
 *     <li>VoiceServerUpdate telling us to connect to a voice server</li>
 *     <li>Reconnects following certain close events</li>
 *   </ul>
 *   </li>
 *   <li>Destructive Events:
 *   <ul>
 *     <li>Websocket close events</li>
 *     <li>Shutdown</li>
 *   </ul>
 *   </li>
 *   <li>Neutral Events
 *   <ul>
 *     <li>Setting and removing a send handler</li>
 *   </ul>
 *   </li>
 * </ul>
 */
public class AudioStackLifecyclePipeline extends BaseSubscriber<LifecycleEvent> {

    private static final Logger log = LoggerFactory.getLogger(AudioStackLifecyclePipeline.class);

    // userId <-> guildId <-> audio stack
    private final Map<String, Map<String, AudioStack>> audioStacks = new ConcurrentHashMap<>();

    private final Function<Member, IAudioSendFactory> sendFactoryProvider;
    private final ClosingWebSocketClient webSocketClient;
    private final Consumer<MagmaEvent> apiEventConsumer;
    private final DatagramSocket udpSocket;

    public AudioStackLifecyclePipeline(final Function<Member, IAudioSendFactory> sendFactoryProvider,
                                       final ClosingWebSocketClient webSocketClient,
                                       final Consumer<MagmaEvent> apiEventConsumer,
                                       final DatagramSocket udpSocket) {
        this.sendFactoryProvider = sendFactoryProvider;
        this.webSocketClient = webSocketClient;
        this.apiEventConsumer = apiEventConsumer;
        this.udpSocket = udpSocket;
    }

    @Override
    protected void hookOnNext(final LifecycleEvent event) {
        if (event instanceof VoiceServerUpdate) {
            final VoiceServerUpdate voiceServerUpdate = (VoiceServerUpdate) event;
            this.getOrCreateAudioStack(event)
                    .next(ConnectWebSocketLcEvent.builder()
                            .sessionInfo(ImmutableSessionInfo.builder()
                                    .voiceServerUpdate(voiceServerUpdate)
                                    .build())
                            .build()
                    );
        } else if (event instanceof UpdateSendHandler) {
            this.getOrCreateAudioStack(event)
                    .next(event);
        } else if (event instanceof CloseWebSocket) {
            //pass it on
            AudioStack audioStack = this.removeAudioStack(event);
            if (audioStack != null){
                this.apiEventConsumer.accept(((CloseWebSocket) event).getApiEvent());
                audioStack.next(event);
            }
        } else if (event instanceof Shutdown) {
            this.dispose();

            this.audioStacks.values().stream().flatMap(map -> map.values().stream()).forEach(
                    audioStack -> audioStack.next(event)
            );
        } else if (event instanceof UpdateSpeakingMode) {
            this.getOrCreateAudioStack(event)
                    .next(event);
        } else {
            log.warn("Unhandled lifecycle event of class {}", event.getClass().getSimpleName());
        }
    }

    @CheckReturnValue
    public List<WebsocketConnectionState> getAudioConnectionStates() {
        return this.audioStacks.entrySet().stream()
                .flatMap(outerEntry -> {
                    final String userId = outerEntry.getKey();
                    return outerEntry.getValue().entrySet().stream()
                            .map(innerEntry -> {
                                final String guildId = innerEntry.getKey();
                                final AudioStack audioStack = innerEntry.getValue();
                                return MagmaWebsocketConnectionState.builder()
                                        .member(MagmaMember.builder()
                                                .userId(userId)
                                                .guildId(guildId)
                                                .build())
                                        .phase(audioStack.getConnectionPhase())
                                        .build();
                            });
                })
                .collect(Collectors.toList());
    }

    @CheckReturnValue
    @SuppressWarnings("squid:S00117")
    private AudioStack getOrCreateAudioStack(final LifecycleEvent lifecycleEvent) {
        final AudioStack[] audioStackArray = {null};
        this.audioStacks.compute(lifecycleEvent.getUserId(), (__, userAudioStack) -> {
            Map<String, AudioStack> newUserAudioStack = userAudioStack;
            if (newUserAudioStack == null) newUserAudioStack = new ConcurrentHashMap<>();
            audioStackArray[0] = newUserAudioStack.computeIfAbsent(lifecycleEvent.getGuildId(), (___) ->
                    new AudioStack(lifecycleEvent.getMember(),
                            this.sendFactoryProvider.apply(lifecycleEvent.getMember()),
                            this.webSocketClient,
                            this.apiEventConsumer,
                            this.udpSocket));
            return newUserAudioStack;
        });
        return audioStackArray[0];
    }

    @CheckReturnValue
    @Nullable
    private AudioStack removeAudioStack(final LifecycleEvent lifecycleEvent) {
        final AudioStack[] removedAudioStack = {null};
        this.audioStacks.computeIfPresent(lifecycleEvent.getUserId(), (__, userAudioStacks) -> {
            removedAudioStack[0] = userAudioStacks.remove(lifecycleEvent.getGuildId());
            if (userAudioStacks.isEmpty()) {
                return null;
            } else {
                return userAudioStacks;
            }
        });
        return removedAudioStack[0];
    }
}
