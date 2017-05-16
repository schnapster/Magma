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

import net.dv8tion.jda.Core;
import net.dv8tion.jda.CoreClient;
import org.json.JSONObject;

public class Tester
{
    public static void main(String[] args)
    {
        String userId = "111761808640978944"; //This is Yui's Id, just as an example. Should be id of bot connected to MainWS
        Core core = new Core(userId, new CoreClient() {
            @Override
            public void sendWS(String message)
            {

            }

            @Override
            public boolean isConnected()
            {
                return false;
            }

            @Override
            public boolean inGuild(String guildId)
            {
                return false;
            }

            @Override
            public boolean voiceChannelExists(String channelId)
            {
                return false;
            }

            @Override
            public boolean hasPermissionInChannel(String channelId, long permission)
            {
                return false;
            }
        });

        //  received indication that channel will be opened
        String guildId = "81384788765712384";       //D-API Guild Id
        String channelId = "131937933270712320";    //#Music voice channel in D-API
        core.openConnection(guildId, channelId);



        // receive the VOICE_SERVER_UPDATE event from MainWS
        String sessionId = "";                      //This is retrieved from VOICE_STATE_UPDATE from MainWS
        String event = "";                          //This is populated with the VOICE_SERVER_UPDATE from MainWS
        JSONObject jsEvent = new JSONObject(event); //Changes from String to Json Object
        core.startConnection(sessionId, jsEvent);



        // close down connection due to bot wanting to sever audio connection to provided guild.
        String differentGuildId = "125227483518861312"; //JDA Guild.
        core.closeConnection(differentGuildId);

    }
}
