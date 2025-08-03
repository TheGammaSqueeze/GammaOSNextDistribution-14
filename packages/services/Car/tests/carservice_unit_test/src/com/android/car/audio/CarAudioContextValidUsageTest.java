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

package com.android.car.audio;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.car.builtin.media.AudioManagerHelper;
import android.media.AudioAttributes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public final class CarAudioContextValidUsageTest {
    private static final int BELOW_RANGE_OF_USAGES = -1;
    private static final int ABOVE_RANGE_OF_NON_CAR_USAGES =
            AudioAttributes.USAGE_CALL_ASSISTANT + 1;
    private static final int ABOVE_RANGE_OF_CAR_USAGES = AudioAttributes.USAGE_ANNOUNCEMENT + 1;

    @Parameterized.Parameters
    public static Collection provideParams() {
        return List.of(
                new Object[][]{
                        {AudioAttributes.USAGE_UNKNOWN, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_MEDIA, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_VOICE_COMMUNICATION, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
                                /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_ALARM, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_NOTIFICATION, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_NOTIFICATION_RINGTONE, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
                                /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
                                /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
                                /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_NOTIFICATION_EVENT, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
                                /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                                /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                                /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_GAME, /* expectedValidity= */ true},
                        {AudioManagerHelper.getUsageVirtualSource(), /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_ASSISTANT, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_CALL_ASSISTANT, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_EMERGENCY, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_SAFETY, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_VEHICLE_STATUS, /* expectedValidity= */ true},
                        {AudioAttributes.USAGE_ANNOUNCEMENT, /* expectedValidity= */ true},
                        {BELOW_RANGE_OF_USAGES, /* expectedValidity= */ false},
                        {ABOVE_RANGE_OF_NON_CAR_USAGES, /* expectedValidity= */ false},
                        {ABOVE_RANGE_OF_CAR_USAGES, /* expectedValidity= */ false},
                        {ABOVE_RANGE_OF_CAR_USAGES, /* expectedValidity= */ false},
                });
    }

    @AudioAttributes.AttributeUsage private final int mAudioAttributeUsage;
    private final boolean mExpectedValid;

    public CarAudioContextValidUsageTest(@AudioAttributes.AttributeUsage int audioAttributeUsage,
            boolean expectedValid) {
        mAudioAttributeUsage = audioAttributeUsage;
        mExpectedValid = expectedValid;
    }

    @Test
    public void isValidAudioAttributeUsage_withValidAttributeUsage_succeeds() {
        boolean isValidUsage = CarAudioContext.isValidAudioAttributeUsage(mAudioAttributeUsage);

        assertWithMessage("Valid result for audio attribute usage %s",
                mAudioAttributeUsage).that(isValidUsage).isEqualTo(mExpectedValid);
    }

    @Test
    public void checkAudioAttributeUsage() {
        if (mExpectedValid) {
            checkAudioAttributeUsageSucceeds();
            return;
        }
        checkAudioAttributeUsageFails();
    }

    private void checkAudioAttributeUsageSucceeds() {
        CarAudioContext.checkAudioAttributeUsage(mAudioAttributeUsage);
    }

    private void checkAudioAttributeUsageFails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            CarAudioContext.checkAudioAttributeUsage(mAudioAttributeUsage);
        });

        assertWithMessage("Exception for check audio attribute usage %s", mAudioAttributeUsage)
                .that(thrown).hasMessageThat().contains("Invalid audio attribute "
                        + mAudioAttributeUsage);
    }
}
