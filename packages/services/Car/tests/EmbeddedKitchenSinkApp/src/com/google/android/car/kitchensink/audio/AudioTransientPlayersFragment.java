/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.car.kitchensink.audio;

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;

import static com.google.android.car.kitchensink.R.raw.turnright;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AudioTransientPlayersFragment extends AudioPlayersFragment {

    private static final List<Integer> TRANSIENT_SOUND_USAGES = new ArrayList<>(Arrays.asList(
            USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, USAGE_ASSISTANT, USAGE_NOTIFICATION_RINGTONE,
            USAGE_VOICE_COMMUNICATION, USAGE_ALARM, USAGE_NOTIFICATION));

    AudioTransientPlayersFragment() {
    }

    int getPlayerResource(int usage) {
        if (usage == USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) {
            return turnright;
        }
        return super.getPlayerResource(usage);
    }

    @Override
    List<Integer> getUsages() {
        return TRANSIENT_SOUND_USAGES;
    }
}
