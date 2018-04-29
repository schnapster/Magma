package space.npstr.magma;

import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

import java.util.function.BiFunction;

/**
 * Created by napster on 24.04.18.
 * <p>
 * Public API. These methods may be called by users of Magma.
 */
public interface MagmaApi {

    /**
     * Please see full factory documentation below. Missing parameters on this factory method are optional.
     */
    static MagmaApi of(final BiFunction<String, String, IAudioSendFactory> sendFactoryProvider) {
        return of(sendFactoryProvider, OptionMap.builder().getMap());
    }

    /**
     * Create a new Magma instance. More than one of these is not necessary, even if you are managing several shards and
     * several bot accounts. A single instance of this scales automatically according to your needs and hardware.
     *
     * @param sendFactoryProvider
     *         a provider of {@link IAudioSendFactory}s. It will have userIds and guildIds applied to it.
     * @param xnioOptions
     *         options to build the {@link XnioWorker} that will be used for the websocket connections
     */
    static MagmaApi of(final BiFunction<String, String, IAudioSendFactory> sendFactoryProvider,
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
     *         Id of the bot account that this update belongs to
     * @param sessionId
     *         The session id of the voice state of member (= bot account in a guild) to which this voice update belongs.
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
     * Set the {@link AudioSendHandler} for a bot member.
     *
     * @param userId
     *         user id of the bot member for which the send handler shall be set
     * @param guildId
     *         guild id of the bot member for which the send handler shall be set
     * @param sendHandler
     *         The send handler to be set. You need to implement this yourself. This is a JDA interface so if you have
     *         written voice code with JDA before you reuse your existing code.
     */
    void setSendHandler(final String userId, final String guildId, final AudioSendHandler sendHandler);

    /**
     * Remove the {@link AudioSendHandler} for a bot member.
     *
     * @param userId
     *         user id of the bot member for which the send handler shall be removed
     * @param guildId
     *         guild id of the bot member for which the send handler shall be removed
     */
    void removeSendHandler(final String userId, final String guildId);

    /**
     * Close the audio connection for a bot member.
     *
     * @param userId
     *         user id of the bot member for which the audio connection shall be closed
     * @param guildId
     *         guild id of the bot member for which the audio connection shall be closed
     */
    void closeConnection(final String userId, final String guildId);

}
