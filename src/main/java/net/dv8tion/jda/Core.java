/*
 *     Copyright 2015-2017 Austin Keener & Michael Ritter
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

package net.dv8tion.jda;

import gnu.trove.map.TLongObjectMap;
import net.dv8tion.jda.audio.AudioWebSocket;
import net.dv8tion.jda.handle.VoiceServerUpdateHandler;
import net.dv8tion.jda.manager.AudioManager;
import net.dv8tion.jda.utils.SimpleLog;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class Core
{
    public static SimpleLog LOG = SimpleLog.getLog("Core");

    private final HashMap<String, AudioManager> audioManagers = new HashMap<>();
    private final ScheduledThreadPoolExecutor audioKeepAlivePool;
    private final VoiceServerUpdateHandler handler;
    private final String userId;


    /**
     * Creates a new Core instance. You should probably have one of these for each shard, but you do you.
     *
     * @param userId The UserId of the bot.
     */
    public Core(String userId)
    {
        this.userId = userId;
        this.handler = new VoiceServerUpdateHandler(this);
        this.audioKeepAlivePool = new ScheduledThreadPoolExecutor(1, new AudioWebSocket.KeepAliveThreadFactory());
    }

    // ==================================================================
    // =                     Methods that you can call
    // ==================================================================

    //Also used to move a preexisting connection from one channel to another!
    public void openConnection(String guildId, String channelId)
    {
        AudioManager manager = getAudioManager(guildId);
        manager.openAudioConnection(channelId);
    }

    public void startConnection(String sessionId, JSONObject serverUpdateEvent)
    {
        //More to do here.
        handler.handle(sessionId, serverUpdateEvent);
    }

    public void closeConnection(String guildId)
    {
        if (audioManagers.containsKey(guildId))
        {
            audioManagers.get(guildId).closeAudioConnection();
        }
    }

    // ====================================================================
    // =                         Helper Methods
    // ====================================================================

    public AudioManager getAudioManager(String guildId)
    {
        AudioManager manager = audioManagers.get(guildId);
        if (manager == null)
        {
            synchronized (audioManagers)
            {
                manager = audioManagers.get(guildId);
                if (manager == null)
                {
                    manager = new AudioManager(this, guildId);
                    audioManagers.put(guildId, manager);
                }
            }
        }

        return manager;
    }

    public ScheduledThreadPoolExecutor getAudioKeepAlivePool()
    {
        return audioKeepAlivePool;
    }

    public String getUserId()
    {
        return userId;
    }

    // ===============================================================
    // =  Methods that you must filled in that are used internally
    // ===============================================================

    public void sendWS(String message)
    {
        //Needs to send the content of the message through the MainWS

    }
}
