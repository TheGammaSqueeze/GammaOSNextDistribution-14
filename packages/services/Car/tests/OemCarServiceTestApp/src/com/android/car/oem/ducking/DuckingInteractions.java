/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.oem.ducking;

import static com.android.car.oem.utils.AudioUtils.getAudioAttributeFromUsage;

import android.car.oem.OemCarAudioVolumeRequest;
import android.media.AudioAttributes;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;

import com.android.car.oem.utils.AudioUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Class for evaluating the focus interactions
 */
public final class DuckingInteractions {

    private static final String TAG = DuckingInteractions.class.getSimpleName();
    public static final ArrayMap<AudioAttributes, List<AudioAttributes>> DEFAULT_INTERACTION =
            new ArrayMap<>();

    static {
        List<AudioAttributes> duckedPriorities = List.of(
                getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANT),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_VEHICLE_STATUS),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_ALARM),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_UNKNOWN),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_GAME),
                getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA));

        for (int i = 0; i < duckedPriorities.size(); i++) {
            AudioAttributes holder = duckedPriorities.get(i);
            List<AudioAttributes> attributesToDuck = new ArrayList<>();
            for (int j = i + 1; j < duckedPriorities.size(); j++) {
                attributesToDuck.add(duckedPriorities.get(j));
            }
            DEFAULT_INTERACTION.put(holder, attributesToDuck);
        }
    }

    public final ArrayMap<AudioAttributes, List<AudioAttributes>> mDuckingInteractions;

    public DuckingInteractions(ArrayMap<AudioAttributes, List<AudioAttributes>>
            duckingInteractions) {
        mDuckingInteractions = duckingInteractions;
    }

    /**
     * Returns a list of audio attributes to duck from the active list
     *
     * @param duckingRequest ducking request to evaluate
     *
     * @return audio attributes to duck
     */
    public List<AudioAttributes> getDuckedAudioAttributes(
            OemCarAudioVolumeRequest duckingRequest) {
        List<AudioAttributes> activeAudioAttributes = duckingRequest.getActivePlaybackAttributes();
        // Nothing to duck if there is only one or fewer items
        if (activeAudioAttributes.size() < 2) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Not ducking any audio attributes");
            }
            return Collections.EMPTY_LIST;
        }
        Set<AudioAttributes> activeAudioAttributesSet = new ArraySet<>(duckingRequest
                .getActivePlaybackAttributes());
        List<AudioAttributes> duckedAttributes = new ArrayList<>();
        for (int i = 0; i < activeAudioAttributes.size(); i++) {
            AudioAttributes currentActiveAttributes = activeAudioAttributes.get(i);
            List<AudioAttributes> audioAttributesToDuck = mDuckingInteractions.get(
                    currentActiveAttributes);
            for (int j = 0; j < audioAttributesToDuck.size(); j++) {
                if (activeAudioAttributesSet.contains(audioAttributesToDuck.get(j))) {
                    duckedAttributes.add(audioAttributesToDuck.get(j));
                }
            }
        }
        return duckedAttributes;
    }

    public void dump(PrintWriter writer, String indent) {
        writer.printf("%sDucking priorities: \n", indent);
        for (int index = 0; index < mDuckingInteractions.size(); index++) {
            String holderUsageString = AudioUtils.getUsageString(mDuckingInteractions.keyAt(index));
            writer.printf("%s%sHolder: %s \n", indent, indent, holderUsageString);
            List<AudioAttributes> incomingDuckingInteractions =
                    mDuckingInteractions.valueAt(index);
            for (int j = 0; j < incomingDuckingInteractions.size(); j++) {
                String incomingUsageString = AudioUtils.getUsageString(
                        incomingDuckingInteractions.get(j));
                writer.printf("%s%s%sIncoming: %s \n", indent, indent, indent, incomingUsageString);
            }
        }
    }
}
