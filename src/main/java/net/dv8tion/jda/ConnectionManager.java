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

import net.dv8tion.jda.audio.hooks.ConnectionListener;
import net.dv8tion.jda.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.manager.AudioManager;
import net.dv8tion.jda.utils.SimpleLog;
import org.apache.commons.lang3.tuple.MutablePair;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ConnectionManager
{
    public static final SimpleLog LOG = SimpleLog.getLog("ConnectionManager");

    public static final ThreadGroup AUDIO_THREADS = new ThreadGroup("jda-audio");
    //GuildId, <TimeOfNextAttempt, AudioConnection (ChannelId)>
    private final HashMap<String, MutablePair<Long, String>> queuedAudioConnections = new HashMap<>();
    private final Core core;

    private volatile Thread ratelimitThread = null;
    protected volatile long ratelimitResetTime;
    protected volatile int messagesSent;
    protected volatile boolean printedRateLimitMessage = false;

    private int maxWebsocketMessagesPerMinute = 115;

    public ConnectionManager(Core core)
    {
        this.core = core;
        setupSendingThread();
    }

    public void queueAudioConnect(String guildId, String channelId)
    {
        queuedAudioConnections.put(guildId, new MutablePair<>(System.currentTimeMillis(), channelId));
    }

    public HashMap<String, MutablePair<Long, String>> getQueuedAudioConnectionMap()
    {
        return queuedAudioConnections;
    }

    public int getMaxWebsocketMessagesPerMinute()
    {
        return maxWebsocketMessagesPerMinute;
    }

    public void setMaxWebsocketMessagesPerMinute(int max)
    {
        if (max > 120 || max <= 0)
            throw new IllegalArgumentException("Provided max must be between 1 and 120");
    }

    private Map.Entry<String, MutablePair<Long, String>> getNextAudioConnectRequest()
    {
        synchronized (queuedAudioConnections)
        {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, MutablePair<Long, String>>> it =  queuedAudioConnections.entrySet().iterator();
            while (it.hasNext())
            {
                Map.Entry<String, MutablePair<Long, String>> entry = it.next();
                MutablePair<Long, String> audioRequest = entry.getValue();
                if (audioRequest.getLeft() < now)
                {
                    String channelId = audioRequest.getRight();
                    String guildId = entry.getKey();
                    ConnectionListener listener = core.getAudioManager(guildId).getConnectionListener();

                    if (!core.getClient().inGuild(guildId))
                    {
                        it.remove();
                        if (listener != null)
                            listener.onStatusChange(ConnectionStatus.DISCONNECTED_REMOVED_FROM_GUILD);
                        continue;
                    }

                    if (!core.getClient().voiceChannelExists(channelId))
                    {
                        it.remove();
                        if (listener != null)
                            listener.onStatusChange(ConnectionStatus.DISCONNECTED_CHANNEL_DELETED);
                        continue;
                    }

                    if (!core.getClient().hasPermissionInChannel(channelId, 1 << 20)) //VOICE_CONNECT
                    {
                        it.remove();
                        if (listener != null)
                            listener.onStatusChange(ConnectionStatus.DISCONNECTED_LOST_PERMISSION);
                        continue;
                    }

                    return entry;
                }
            }
        }

        return null;
    }

    private boolean send(String message)
    {
        if (!core.getClient().isConnected())
            return false;

        long now = System.currentTimeMillis();

        if (this.ratelimitResetTime <= now)
        {
            this.messagesSent = 0;
            this.ratelimitResetTime = now + 60000;//60 seconds
            this.printedRateLimitMessage = false;
        }

        //Allows 115 messages to be sent before limiting. (unless changed)
        if (this.messagesSent <= maxWebsocketMessagesPerMinute) //technically we could go to 120, but we aren't going to chance it
        {
            LOG.trace("<- " + message);
            core.getClient().sendWS(message);
            this.messagesSent++;
            return true;
        }
        else
        {
            if (!printedRateLimitMessage)
            {
                LOG.warn("Hit the WebSocket RateLimit! If you see this message a lot then you might need to talk to DV8FromTheWorld.");
                printedRateLimitMessage = true;
            }
            return false;
        }
    }

    private void setupSendingThread()
    {
        ratelimitThread = new Thread("Core ConnectionManager Thread")
        {

            @Override
            public void run()
            {
                boolean needRatelimit;
                boolean attemptedToSend;
                while (!this.isInterrupted())
                {
                    try
                    {
                        //Make sure that we don't send any packets before sending auth info.
//                        if (!sentAuthInfo)
//                        {
//                            Thread.sleep(500);
//                            continue;
//                        }
                        attemptedToSend = false;
                        needRatelimit = false;

                        Map.Entry<String, MutablePair<Long, String>> requestEntry = getNextAudioConnectRequest();

                        System.out.println(requestEntry);
                        if (requestEntry != null)
                        {
                            String guildId = requestEntry.getKey();
                            MutablePair<Long, String> audioRequest = requestEntry.getValue();
                            String channelId = audioRequest.getRight();
                            AudioManager audioManager = core.getAudioManager(guildId);
                            JSONObject audioConnectPacket = new JSONObject()
                                    .put("op", 4)
                                    .put("d", new JSONObject()
                                            .put("guild_id", guildId)
                                            .put("channel_id", channelId)
                                            .put("self_mute", audioManager.isSelfMuted())
                                            .put("self_deaf", audioManager.isSelfDeafened())
                                    );
                            needRatelimit = !send(audioConnectPacket.toString());
                            System.out.println(needRatelimit);
                            if (!needRatelimit)
                            {
                                //If we didn't get RateLimited, Next allowed connect request will be 2 seconds from now
                                audioRequest.setLeft(System.currentTimeMillis() + 2000);

                                //If the connection is already established, then the packet just sent
                                // was a move channel packet, thus, it won't trigger the removal from
                                // queuedAudioConnections in VoiceServerUpdateHandler because we won't receive
                                // that event just for a move, so we remove it here after successfully sending.
                                if (audioManager.isConnected())
                                {
                                    queuedAudioConnections.remove(guildId);
                                }
                            }
                            attemptedToSend = true;
                        }

                        if (needRatelimit || !attemptedToSend)
                        {
                            Thread.sleep(1000);
                        }
                    }
                    catch (InterruptedException ignored)
                    {
                        LOG.debug("ConnectionManager thread interrupted. Most likely shutting down.");
                        break;
                    }
                }
            }
        };
        ratelimitThread.start();
    }
}
