/*
 * Copyright 2018-2019 Dennis Neufeld
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

import java.util.function.Function;
import net.dv8tion.jda.api.audio.factory.IAudioSendFactory;
import space.npstr.magma.api.MagmaApi;
import space.npstr.magma.api.Member;
import space.npstr.magma.impl.Magma;

/**
 * Created by napster on 08.05.19.
 */
public class MagmaFactory {

    /**
     * Create a new Magma instance. More than one of these is not necessary, even if you are managing several shards and
     * several bot accounts. A single instance of this scales automatically according to your needs and hardware.
     *
     * @param sendFactoryProvider a provider of {@link IAudioSendFactory}s. It will have members applied to it.
     */
    public static MagmaApi of(final Function<Member, IAudioSendFactory> sendFactoryProvider) {
        return new Magma(sendFactoryProvider);
    }

    private MagmaFactory() {}
}
