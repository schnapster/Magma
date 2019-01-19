/*
 *     Copyright 2015-2019 Florian Spieß
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

package space.npstr.magma.api;

import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Set;

public enum SpeakingMode {
	VOICE(1),
	SOUNDSHARE(2),
	PRIORITY(4);

	private final int key;

	SpeakingMode(int key) {
		this.key = key;
	}

	public int getKey() {
		return key;
	}

	public static int toMask(@Nullable Set<SpeakingMode> mode) {
		if (mode == null || mode.isEmpty()) {
			return 0;
		}
		int mask = 0;
		for (SpeakingMode m : mode) {
			mask |= m.getKey();
		}
		return mask;
	}
}
