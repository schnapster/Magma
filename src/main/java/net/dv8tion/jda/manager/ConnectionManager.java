package net.dv8tion.jda.manager;

import org.apache.commons.lang3.tuple.MutablePair;

import java.util.HashMap;

/**
 * Created by Repulser
 * https://github.com/Repulser
 */
public interface ConnectionManager {

    void queueAudioConnect(String guildId, String channelId);

    HashMap<String, MutablePair<Long, String>> getQueuedAudioConnectionMap();

}
