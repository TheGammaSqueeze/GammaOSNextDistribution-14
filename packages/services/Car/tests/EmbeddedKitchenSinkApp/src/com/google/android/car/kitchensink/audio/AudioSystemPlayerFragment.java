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

import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;

import static com.google.android.car.kitchensink.R.raw.well_worth_the_wait;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AudioSystemPlayerFragment extends AudioPlayersFragment {

    private static final List<Integer> CAR_SOUND_USAGES = new ArrayList<>(Arrays.asList(
            USAGE_EMERGENCY, USAGE_SAFETY,
            USAGE_VEHICLE_STATUS, USAGE_ANNOUNCEMENT
    ));

    AudioSystemPlayerFragment() {
    }

    int getPlayerResource(int usage) {
        return well_worth_the_wait;
    }

    @Override
    List<Integer> getUsages() {
        return CAR_SOUND_USAGES;
    }
}
