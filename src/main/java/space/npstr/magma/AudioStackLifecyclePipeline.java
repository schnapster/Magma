package space.npstr.magma;

import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
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
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Created by napster on 22.04.18.
 * <p>
 * This class manages the lifecycles of various audiostack elements.
 * <p>
 * The AudioStack consists of:
 * - A websocket connection ( -> {@link space.npstr.magma.connections.ReactiveAudioWebSocket}
 * - A voice packet emitter ( -> {@link space.npstr.magma.connections.ReactiveAudioConnection}
 * - A send handler          ( -> {@link net.dv8tion.jda.core.audio.AudioSendHandler}, provided by user code)
 * - A (native) send system ( -> {@link net.dv8tion.jda.core.audio.factory.IAudioSendSystem}
 * - Another object that glues these things together, mostly to keep track of the association sendhandler <-> guild
 * <p>
 * <p>
 * Lifecycle Events:
 * <p>
 * - Constructive Events:
 * -- VoiceServerUpdate telling us to join a channel
 * -- Reconnects following certain close events
 * <p>
 * - Destructive Events:
 * -- VSU telling us to leave a channel (null id)
 * -- Websocket close events
 * <p>
 * Neutral Events:
 * -- Setting and removing a send handler
 */
public class AudioStackLifecyclePipeline {

    private static final Logger log = LoggerFactory.getLogger(AudioStackLifecyclePipeline.class);

    // userId <-> guildId <-> audio stack
    // concurrency is handled by modifying this through a single thread eventloop only
    private final Map<String, Map<String, AudioStack>> audioStacks = new HashMap<>();

    private final Function<String, IAudioSendFactory> sendFactoryProvider;
    private final WebSocketClient webSocketClient;

    private final FluxSink<LifecycleEvent> lifecycleEventSink;
    private final Disposable lifecycleSubscription;

    public AudioStackLifecyclePipeline(final Function<String, IAudioSendFactory> sendFactoryProvider,
                                       final WebSocketClient webSocketClient) {
        this.sendFactoryProvider = sendFactoryProvider;
        this.webSocketClient = webSocketClient;


        final UnicastProcessor<LifecycleEvent> processor = UnicastProcessor.create();

        this.lifecycleEventSink = processor.sink();


        this.lifecycleSubscription = processor
                .log(log.getName() + ".Inbound", Level.FINEST) //FINEST = TRACE
                .subscribeOn(Schedulers.single())
                .subscribe(this::onEvent);
    }

    /**
     * Call this to drop lifecycle events into this thing for processing
     */
    public void next(final LifecycleEvent lifecycleEvent) {
        this.lifecycleEventSink.next(lifecycleEvent);
    }


    private void onEvent(final LifecycleEvent event) {

        if (event instanceof VoiceServerUpdate) {
            final VoiceServerUpdate voiceServerUpdate = (VoiceServerUpdate) event;
            this.getAudioStack(event)
                    .next(ConnectWebSocketLcEvent.builder()
                            .sessionInfo(ImmutableSessionInfo.builder()
                                    .userId(voiceServerUpdate.getUserId())
                                    .voiceServerUpdate(voiceServerUpdate)
                                    .build())
                            .build()
                    );
        } else if (event instanceof UpdateSendHandler) {
            this.getAudioStack(event)
                    .next(event);
        } else if (event instanceof CloseWebSocket) {
            //pass it on
            this.getAudioStack(event)
                    .next(event);
        } else if (event instanceof Shutdown) {
            this.lifecycleSubscription.dispose();

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
                .computeIfAbsent(lifecycleEvent.getUserId(), uId -> new HashMap<>())
                .computeIfAbsent(lifecycleEvent.getGuildId(), gId ->
                        new AudioStack(gId, this.sendFactoryProvider.apply(gId), this.webSocketClient, this)
                );
    }
}
