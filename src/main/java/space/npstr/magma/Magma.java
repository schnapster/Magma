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

import club.minnced.opus.util.NativeUtil;
import com.sun.jna.Platform;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;
import space.npstr.magma.connections.hax.ClosingUndertowWebSocketClient;
import space.npstr.magma.connections.hax.ClosingWebSocketClient;
import space.npstr.magma.events.audio.lifecycle.CloseWebSocketLcEvent;
import space.npstr.magma.events.audio.lifecycle.Shutdown;
import space.npstr.magma.events.audio.lifecycle.UpdateSendHandlerLcEvent;
import space.npstr.magma.events.audio.lifecycle.VoiceServerUpdateLcEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public class Magma implements MagmaApi {

    private static final Logger log = LoggerFactory.getLogger(Magma.class);

    private final AudioStackLifecyclePipeline lifecyclePipeline;

    /**
     * @see MagmaApi
     */
    Magma(final Function<Member, IAudioSendFactory> sendFactoryProvider, final OptionMap xnioOptions) {
        if (!init()) {
            throw new RuntimeException("Failed to load opus lib. See log output for more info.");
        }

        final ClosingWebSocketClient webSocketClient;
        try {
            final XnioWorker xnioWorker = Xnio.getInstance().createWorker(xnioOptions);
            final XnioSsl xnioSsl = new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY);
            webSocketClient = new ClosingUndertowWebSocketClient(xnioWorker, builder -> builder.setSsl(xnioSsl));
        } catch (final Exception e) {
            throw new RuntimeException("Failed to set up websocket client", e);
        }

        this.lifecyclePipeline = new AudioStackLifecyclePipeline(sendFactoryProvider, webSocketClient);
    }

    // ################################################################################
    // #                            Public API
    // ################################################################################


    @Override
    public void shutdown() {
        this.lifecyclePipeline.next(Shutdown.INSTANCE);
    }

    @Override
    public void provideVoiceServerUpdate(final Member member, final ServerUpdate serverUpdate) {
        this.lifecyclePipeline.next(VoiceServerUpdateLcEvent.builder()
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
        this.lifecyclePipeline.next(CloseWebSocketLcEvent.builder()
                .member(member)
                .build());
    }

    // ################################################################################
    // #                             Internals
    // ################################################################################

    private void updateSendHandler(final Member member, @Nullable final AudioSendHandler sendHandler) {
        this.lifecyclePipeline.next(UpdateSendHandlerLcEvent.builder()
                .member(member)
                .audioSendHandler(Optional.ofNullable(sendHandler))
                .build());
    }

    // ################################################################################
    // #                             Opus Check
    // ################################################################################

    //The code below has been taken mostly as-is from jda-audio, Apache 2.0

    private static boolean initialized = false;
    private static boolean audioPossible = false;

    //Load the Opus library.
    private static synchronized boolean init() {
        if (initialized) {
            return audioPossible;
        }
        initialized = true;

        String nativesRoot;
        try {
            //The libraries that this is referencing are available in the src/main/resources/opus/ folder.
            //Of course, when JDA is compiled that just becomes /opus/
            nativesRoot = "/natives/" + Platform.RESOURCE_PREFIX + "/%s";
            if (nativesRoot.contains("darwin")) //Mac
                nativesRoot += ".dylib";
            else if (nativesRoot.contains("win"))
                nativesRoot += ".dll";
            else if (nativesRoot.contains("linux"))
                nativesRoot += ".so";
            else
                throw new UnsupportedOperationException();

            NativeUtil.loadLibraryFromJar(String.format(nativesRoot, "libopus"));
        } catch (final Throwable e) {
            if (e instanceof UnsupportedOperationException)
                log.error("Sorry, JDA's audio system doesn't support this system.\n" +
                        "Supported Systems: Windows(x86, x64), Mac(x86, x64) and Linux(x86, x64)\n" +
                        "Operating system: {}", Platform.RESOURCE_PREFIX);
            else if (e instanceof NoClassDefFoundError) {
                log.error("Missing opus dependency, unable to initialize audio!");
            } else if (e instanceof IOException) {
                log.error("There was an IO Exception when setting up the temp files for audio.", e);
            } else if (e instanceof UnsatisfiedLinkError) {
                log.error("JDA encountered a problem when attempting to load the Native libraries. Contact a DEV.", e);
            } else {
                log.error("An unknown error occurred while attempting to setup JDA's audio system!", e);
            }

            nativesRoot = null;
        }

        audioPossible = nativesRoot != null;
        System.setProperty("opus.lib", audioPossible ? String.format(nativesRoot, "libopus") : "");

        if (audioPossible) {
            log.info("Audio System successfully setup!");
        } else {
            log.info("Audio System encountered problems while loading, thus, is disabled.");
        }
        return audioPossible;
    }
}
