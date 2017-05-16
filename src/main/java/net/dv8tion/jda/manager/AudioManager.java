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

package net.dv8tion.jda.manager;

import com.sun.jna.Platform;
import net.dv8tion.jda.Core;
import net.dv8tion.jda.audio.AudioConnection;
import net.dv8tion.jda.audio.AudioReceiveHandler;
import net.dv8tion.jda.audio.AudioSendHandler;
import net.dv8tion.jda.audio.hooks.ConnectionListener;
import net.dv8tion.jda.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.audio.hooks.ListenerProxy;
import net.dv8tion.jda.utils.NativeUtil;
import net.dv8tion.jda.utils.SimpleLog;
import org.json.JSONObject;

import java.io.IOException;

public class AudioManager
{
    //These values are set at the bottom of this file.
    public static boolean AUDIO_SUPPORTED;
    public static String OPUS_LIB_NAME;

    public static SimpleLog LOG = SimpleLog.getLog("JDAAudioManager");
    public long DEFAULT_CONNECTION_TIMEOUT = 10000;

    protected static boolean initialized = false;

    public final Object CONNECTION_LOCK = new Object();

    protected final Core core;
    protected String guildId;
    protected AudioConnection audioConnection = null;
    protected String queuedAudioConnectionId = null; //String id of VoiceChannel

    protected AudioSendHandler sendHandler;
    protected AudioReceiveHandler receiveHandler;
    protected ListenerProxy connectionListener = new ListenerProxy();
    protected long queueTimeout = 100;
    protected boolean shouldReconnect = true;

    protected boolean selfMuted = false;
    protected boolean selfDeafened = false;

    protected long timeout = DEFAULT_CONNECTION_TIMEOUT;

    public AudioManager(Core core, String guildId)
    {
        this.core = core;
        this.guildId = guildId;
        init(); //Just to make sure that the audio libs have been initialized.
    }

    public void setGuild(String guildId)
    {
        this.guildId = guildId;
    }

    
    public void openAudioConnection(String channelId)
    {

        if (!AUDIO_SUPPORTED)
            throw new UnsupportedOperationException("Sorry! Audio is disabled due to an internal JDA error! Contact Dev!");

        if (audioConnection == null)
        {
            //Start establishing connection, joining provided channel
            queuedAudioConnectionId = channelId;
            core.getConnectionManager().queueAudioConnect(guildId, channelId);
        }
        else
        {
            //Connection is already established, move to specified channel

            //If we are already connected to this VoiceChannel, then do nothing.
            if (channelId.equals(audioConnection.getChannelId()))
                return;

            core.getConnectionManager().queueAudioConnect(guildId, channelId);
            audioConnection.setChannelId(channelId);
        }
    }

    
    public void closeAudioConnection()
    {
        closeAudioConnection(ConnectionStatus.NOT_CONNECTED);
    }

    public void closeAudioConnection(ConnectionStatus reason)
    {
        synchronized (CONNECTION_LOCK)
        {
            core.getConnectionManager().getQueuedAudioConnectionMap().remove(guildId);
            this.queuedAudioConnectionId = null;
            if (audioConnection == null)
                return;
            this.audioConnection.close(reason);
            this.audioConnection = null;
        }
    }

    
    public String getGuildId()
    {
        return guildId;
    }

    
    public boolean isAttemptingToConnect()
    {
        return queuedAudioConnectionId != null;
    }

    
    public String getQueuedAudioConnectionId()
    {
        return queuedAudioConnectionId;
    }

    
    public String getConnectedChannel()
    {
        return audioConnection == null ? null : audioConnection.getChannelId();
    }

    
    public boolean isConnected()
    {
        return audioConnection != null;
    }

    
    public void setConnectTimeout(long timeout)
    {
        this.timeout = timeout;
    }

    
    public long getConnectTimeout()
    {
        return timeout;
    }

    
    public void setSendingHandler(AudioSendHandler handler)
    {
        sendHandler = handler;
        if (audioConnection != null)
            audioConnection.setSendingHandler(handler);
    }

    
    public AudioSendHandler getSendingHandler()
    {
        return sendHandler;
    }

    
    public void setReceivingHandler(AudioReceiveHandler handler)
    {
        receiveHandler = handler;
        if (audioConnection != null)
            audioConnection.setReceivingHandler(handler);
    }

    
    public AudioReceiveHandler getReceiveHandler()
    {
        return receiveHandler;
    }

    
    public void setConnectionListener(ConnectionListener listener)
    {
        this.connectionListener.setListener(listener);
    }

    
    public ConnectionListener getConnectionListener()
    {
        return connectionListener.getListener();
    }

    
    public ConnectionStatus getConnectionStatus()
    {
        if (audioConnection != null)
            return audioConnection.getWebSocket().getConnectionStatus();
        else
            return ConnectionStatus.NOT_CONNECTED;
    }

    
    public void setAutoReconnect(boolean shouldReconnect)
    {
        this.shouldReconnect = shouldReconnect;
        if (audioConnection != null)
            audioConnection.getWebSocket().setAutoReconnect(shouldReconnect);
    }

    
    public boolean isAutoReconnect()
    {
        return shouldReconnect;
    }

    
    public void setSelfMuted(boolean muted)
    {
        if (selfMuted != muted)
        {
            this.selfMuted = muted;
            updateVoiceState();
        }
    }

    
    public boolean isSelfMuted()
    {
        return selfMuted;
    }

    
    public void setSelfDeafened(boolean deafened)
    {
        if (selfDeafened != deafened)
        {
            this.selfDeafened = deafened;
            updateVoiceState();
        }

    }

    
    public boolean isSelfDeafened()
    {
        return selfDeafened;
    }

    public ConnectionListener getListenerProxy()
    {
        return connectionListener;
    }

    public void setAudioConnection(AudioConnection audioConnection)
    {
        this.audioConnection = audioConnection;
        if (audioConnection == null)
            return;

        this.queuedAudioConnectionId = null;
        audioConnection.setSendingHandler(sendHandler);
        audioConnection.setReceivingHandler(receiveHandler);
        audioConnection.setQueueTimeout(queueTimeout);
        audioConnection.ready(timeout);
    }

    public void prepareForRegionChange()
    {
        String queuedChannel = audioConnection.getChannelId();
        closeAudioConnection(ConnectionStatus.AUDIO_REGION_CHANGE);
        this.queuedAudioConnectionId = queuedChannel;
    }

    public void setQueuedAudioConnectionId(String channelId)
    {
        queuedAudioConnectionId = channelId;
    }

    public void setConnectedChannel(String channelId)
    {
        if (audioConnection != null)
            audioConnection.setChannelId(channelId);
    }

    public void setQueueTimeout(long queueTimeout)
    {
        this.queueTimeout = queueTimeout;
        if (audioConnection != null)
            audioConnection.setQueueTimeout(queueTimeout);
    }

    protected void updateVoiceState()
    {
        if (isConnected() || isAttemptingToConnect())
        {
            String channelId = isConnected() ? getConnectedChannel() : getQueuedAudioConnectionId();

            //This is technically equivalent to an audio open/move packet.
            JSONObject voiceStateChange = new JSONObject()
                    .put("op", 4)
                    .put("d", new JSONObject()
                            .put("guild_id", guildId)
                            .put("channel_id", channelId)
                            .put("self_mute", isSelfMuted())
                            .put("self_deaf", isSelfDeafened())
                    );
            core.getClient().sendWS(voiceStateChange.toString());
        }
    }

    //Load the Opus library.
    public static synchronized boolean init()
    {
        if(initialized)
            return AUDIO_SUPPORTED;
        initialized = true;
        String nativesRoot  = null;
        try
        {
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
        }
        catch (Throwable e)
        {
            if (e instanceof UnsupportedOperationException)
                LOG.fatal("Sorry, JDA's audio system doesn't support this system.\n" +
                        "Supported Systems: Windows(x86, x64), Mac(x86, x64) and Linux(x86, x64)\n" +
                        "Operating system: " + Platform.RESOURCE_PREFIX);
            else if (e instanceof  IOException)
            {
                LOG.fatal("There was an IO Exception when setting up the temp files for audio.");
                LOG.log(e);
            }
            else if (e instanceof UnsatisfiedLinkError)
            {
                LOG.fatal("JDA encountered a problem when attempting to load the Native libraries. Contact a DEV.");
                LOG.log(e);
            }
            else
            {
                LOG.fatal("An unknown error occurred while attempting to setup JDA's audio system!");
                LOG.log(e);
            }

            nativesRoot = null;
        }
        finally
        {
            OPUS_LIB_NAME = nativesRoot != null ? String.format(nativesRoot, "libopus") : null;
            AUDIO_SUPPORTED = nativesRoot != null;

            if (AUDIO_SUPPORTED)
                LOG.info("Audio System successfully setup!");
            else
                LOG.info("Audio System encountered problems while loading, thus, is disabled.");
            return AUDIO_SUPPORTED;
        }

    }
}
