package space.npstr.magma;

import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

import java.util.function.Function;

/**
 * Created by napster on 24.04.18.
 * <p>
 * Public API. These methods may be called by users of the lib.
 */
public interface MagmaApi {

    /**
     * See full factory documentation below. Missing parameters on this factory method are optional.
     */
    static MagmaApi of(final Function<String, IAudioSendFactory> sendFactoryProvider) {
        return of(sendFactoryProvider, OptionMap.builder().getMap());
    }

    /**
     * Create a new Magma instance. More than one of these is not necessary.
     *
     * @param sendFactoryProvider
     *         a provider of {@link IAudioSendFactory}s. It will have guildIds applied to it.
     * @param xnioOptions
     *         options to build the {@link XnioWorker} that will be used for the websocket connections
     */
    static MagmaApi of(final Function<String, IAudioSendFactory> sendFactoryProvider,
                       final OptionMap xnioOptions) {
        return new Magma(sendFactoryProvider, xnioOptions);
    }

    /**
     * Release all resources held.
     */
    void shutdown();

    /**
     * Also see: https://discordapp.com/developers/docs/topics/voice-connections#retrieving-voice-server-information-example-voice-server-update-payload
     *
     * @param userId
     *         id of the bot account
     * @param sessionId
     *         The session id of the **main websocket** of your shard that handles the guild to which this voice update
     *         belongs.
     * @param guildId
     *         Id of the guild whose voice server shall be updated. Can be extracted from the op 0 VOICE_SERVER_UPDATE
     *         event that should be triggering a call to this method in the first place.
     * @param endpoint
     *         The endpoint to connect to. If the event you received from Discord has no endpoint, you can safely
     *         discard it, until you received one with a valid endpoint. Can be extracted from the op 0
     *         VOICE_SERVER_UPDATE event that should be triggering a call to this method in the first place
     * @param token
     *         Can be extracted from the op 0 VOICE_SERVER_UPDATE event that should be triggering a call to this method
     *         in the first place
     */
    //todo: too many string parameters. this is error prone.
    void provideVoiceServerUpdate(final String userId, final String sessionId, final String guildId,
                                  final String endpoint, final String token);

    /**
     * @param userId
     * @param guildId
     * @param sendHandler
     */
    void setSendHandler(final String userId, final String guildId, final AudioSendHandler sendHandler);

    /**
     * @param userId
     * @param guildId
     */
    void removeSendHandler(final String userId, final String guildId);

    /**
     * @param userId
     * @param guildId
     */
    void closeConnection(final String userId, final String guildId);

}
