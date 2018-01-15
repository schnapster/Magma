package net.dv8tion.jda.manager;

/**
 * Created by Repulser
 * https://github.com/Repulser
 */
public interface ConnectionManager {

    void queueAudioConnect(String guildId, String channelId);

    void removeAudioConnection(String guildId);

    void shouldReconnect(String guildId);
}
