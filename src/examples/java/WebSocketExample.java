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

import com.neovisionaries.ws.client.*;
import net.dv8tion.jda.Core;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSocketExample extends WebSocketAdapter
{
    public static void main(String[] args) throws IOException, WebSocketException
    {
        //Provide user id of the logged in account.
        String userId = "";

        new WebSocketExample(userId);
    }

    // =============================================

    private final HashMap<String, Core> cores = new HashMap<>();
    private final String userId;
    private WebSocket socket;

    public WebSocketExample(String userId) throws IOException, WebSocketException
    {
        this.userId = userId;
        socket = new WebSocketFactory()
                .createSocket("ws://localhost/")
                .addListener(this);
        socket.connect();
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception
    {
        JSONObject obj = new JSONObject(text);

        String identifier = obj.getString("identifier");
        String nonce = obj.getString("nonce");
        String action = obj.getString("action");

        Core core = getCore(identifier);

        switch (action)
        {
            case "OPEN_CONNECTION":
            {
                String guildId = obj.getString("guild_id");
                String channelId = obj.getString("channel_id");

                core.getAudioManager(guildId).openAudioConnection(channelId);
                break;
            }
            case "VOICE_SERVER_UPDATE":
            {
                String sessionId = obj.getString("session_id");
                String vsuString = obj.getString("vsu");
                JSONObject vsuObject = new JSONObject(vsuString);

                core.provideVoiceServerUpdate(sessionId, vsuObject);
                break;
            }
            case "CLOSE_CONNECTION":
            {
                String guildId = obj.getString("guild_id");

                core.getAudioManager(guildId).closeAudioConnection();
                break;
            }
        }
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception
    {
        System.out.println("Successfully connected to localhost WS!");
    }


    @Override
    public void onDisconnected(WebSocket websocket,
                               WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame,
                               boolean closedByServer) throws Exception
    {
        System.out.println("Disconnected from localhost WS. Might wanna build a reconnect system!");
    }

    private Core getCore(String identifier)
    {
        Core core = cores.get(identifier);
        if (core == null)
        {
            synchronized (cores)
            {
                core = cores.get(identifier);
                if (core == null)
                {
                    core = new Core(userId, null);
                    cores.put(identifier, core);
                }
            }
        }

        return core;
    }
}
