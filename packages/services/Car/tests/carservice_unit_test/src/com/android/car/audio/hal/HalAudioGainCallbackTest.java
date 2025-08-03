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

package com.android.car.audio.hal;

import static com.google.common.truth.Truth.assertWithMessage;

import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.Reasons;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class HalAudioGainCallbackTest {
    private static final int PRIMARY_ZONE_ID = 0;
    private static final String PRIMARY_ZONE_ID_LITERAL = "0";
    private static final String PRIMARY_MUSIC_ADDRESS = "primary music";

    @Test
    public void isReasonValid_withValidReasons_succeeds() {
        List<Integer> validReasons =
                List.of(
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.REMOTE_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.ADAS_DUCKING,
                        Reasons.NAV_DUCKING,
                        Reasons.PROJECTION_DUCKING,
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                        Reasons.EXTERNAL_AMP_VOL_FEEDBACK,
                        Reasons.OTHER);

        for (int index = 0; index < validReasons.size(); index++) {
            int validReason = validReasons.get(index);
            assertWithMessage("Reason " + HalAudioGainCallback.reasonToString(validReason))
                    .that(HalAudioGainCallback.isReasonValid(validReason))
                    .isTrue();
        }
    }

    @Test
    public void isReasonValid_withInvalidReasons_fails() {
        List<Integer> invalidReasons = List.of(-1, -10, 666);

        for (int index = 0; index < invalidReasons.size(); index++) {
            int invalidReason = invalidReasons.get(index);
            assertWithMessage("Reason " + HalAudioGainCallback.reasonToString(invalidReason))
                    .that(HalAudioGainCallback.isReasonValid(invalidReason))
                    .isFalse();
        }
    }

    @Test
    public void reasonToString_validReasons_succeeds() {
        List<Integer> reasons =
                List.of(
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.REMOTE_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.ADAS_DUCKING,
                        Reasons.NAV_DUCKING,
                        Reasons.PROJECTION_DUCKING,
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                        Reasons.EXTERNAL_AMP_VOL_FEEDBACK,
                        Reasons.OTHER);
        for (int index = 0; index < reasons.size(); index++) {
            int reason = reasons.get(index);
            String literalReason = HalAudioGainCallback.reasonToString(reasons.get(index));
            assertWithMessage("Valid Reason " + reason).that(literalReason).isNotNull();
            assertWithMessage("Valid Reason " + reason)
                    .that(literalReason)
                    .doesNotContain("Unsupported reason");
        }
    }

    @Test
    public void reasonToString_invalidReasons_succeeds() {
        List<Integer> reasons = List.of(-1, -10, 666);
        for (int index = 0; index < reasons.size(); index++) {
            int reason = reasons.get(index);
            String literalReason = HalAudioGainCallback.reasonToString(reasons.get(index));
            assertWithMessage("Invalid Reason " + reason).that(literalReason).isNotNull();
            assertWithMessage("Invalid Reason " + reason)
                    .that(literalReason)
                    .contains("Unsupported reason");
        }
    }

    @Test
    public void gainToString_succeeds() {
        AudioGainConfigInfo audioGainConfigInfo = new AudioGainConfigInfo();
        audioGainConfigInfo.zoneId = PRIMARY_ZONE_ID;
        audioGainConfigInfo.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        audioGainConfigInfo.volumeIndex = 666;

        String literalGain = HalAudioGainCallback.gainToString(audioGainConfigInfo);
        assertWithMessage("Audio Gain Config Info Literal").that(literalGain).isNotNull();
        assertWithMessage("Audio Gain Config Info Literal").that(literalGain).contains("zone:");
        assertWithMessage("Audio Gain Config Info Literal")
                .that(literalGain)
                .contains(PRIMARY_ZONE_ID_LITERAL);
        assertWithMessage("Audio Gain Config Info Literal").that(literalGain).contains("address:");
        assertWithMessage("Audio Gain Config Info Literal")
                .that(literalGain)
                .contains(PRIMARY_MUSIC_ADDRESS);
        assertWithMessage("Audio Gain Config Info Literal")
                .that(literalGain)
                .contains("Volume Index:");
        assertWithMessage("Audio Gain Config Info Literal").that(literalGain).contains("666");
    }
}
