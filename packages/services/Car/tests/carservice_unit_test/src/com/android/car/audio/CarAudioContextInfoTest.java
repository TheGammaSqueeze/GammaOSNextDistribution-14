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

import android.media.AudioAttributes;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CarAudioContextInfoTest {

    public static final int TEST_CONTEXT_ID_INVALID = -1;
    public static final int TEST_CONTEXT_ID_MIN_VALUE = 0;
    public static final int TEST_CONTEXT_ID_100 = 100;
    public static final int TEST_CONTEXT_ID_1000 = 1000;
    public static final String TEST_CONTEXT_NAME_MUSIC = "music";
    public static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE = CarAudioContext
            .getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA);

    public static final AudioAttributes TEST_NAV_AUDIO_ATTRIBUTE = CarAudioContext
            .getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

    public static final AudioAttributes TEST_NOTIFICATION_AUDIO_ATTRIBUTE = CarAudioContext
            .getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION);
    public static final AudioAttributes[] TEST_AUDIO_ATTRIBUTES_ARRAY =
            {TEST_MEDIA_AUDIO_ATTRIBUTE};

    public static final AudioAttributes[] TEST_ALL_AUDIO_ATTRIBUTES_ARRAY =
            {TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_NOTIFICATION_AUDIO_ATTRIBUTE,
                    TEST_NAV_AUDIO_ATTRIBUTE};

    @Test
    public void constructor_withNullAttributes_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarAudioContextInfo(/* audioAttributes= */ null,
                    TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_MIN_VALUE);
        });

        assertWithMessage("Null audio attribute exception").that(thrown)
                .hasMessageThat().contains("Car audio context's audio attributes can not be null");
    }

    @Test
    public void constructor_withEmptyAudioAttributes_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            new CarAudioContextInfo(new AudioAttributes[0],
                    TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_MIN_VALUE);
        });

        assertWithMessage("Empty audio attributes exception").that(thrown)
                .hasMessageThat().contains("Car audio context's audio attributes can not be empty");
    }

    @Test
    public void constructor_withNullName_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarAudioContextInfo(TEST_AUDIO_ATTRIBUTES_ARRAY,
                    /* name= */ null, TEST_CONTEXT_ID_MIN_VALUE);
        });

        assertWithMessage("Null name string exception").that(thrown)
                .hasMessageThat().contains("Car audio context's name can not be null");
    }

    @Test
    public void constructor_withEmptyName_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            new CarAudioContextInfo(TEST_AUDIO_ATTRIBUTES_ARRAY,
                    /* name= */ "", TEST_CONTEXT_ID_MIN_VALUE);
        });

        assertWithMessage("Null name string exception").that(thrown)
                .hasMessageThat().contains("Car audio context's name can not be empty");
    }

    @Test
    public void constructor_withNegativeId_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            new CarAudioContextInfo(TEST_AUDIO_ATTRIBUTES_ARRAY,
                    TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_INVALID);
        });

        assertWithMessage("Negative id exception").that(thrown)
                .hasMessageThat().contains("Car audio context's id can not be negative");
    }

    @Test
    public void getId_withValidId() {
        CarAudioContextInfo info = new CarAudioContextInfo(
                TEST_AUDIO_ATTRIBUTES_ARRAY, TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_1000);

        assertWithMessage("Car audio context info id")
                .that(info.getId()).isEqualTo(TEST_CONTEXT_ID_1000);
    }

    @Test
    public void geName_withValidName() {
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_MIN_VALUE);

        assertWithMessage("Car audio context info name")
                .that(info.getName()).isEqualTo(TEST_CONTEXT_NAME_MUSIC);
    }

    @Test
    public void geAudioAttributes_withValidAudioAttributes() {
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_MIN_VALUE);

        assertWithMessage("Car audio context info audio attributes")
                .that(info.getAudioAttributes()).asList()
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
    }

    @Test
    public void toString_withValidParameters() {
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);

        assertWithMessage("Car audio context info string with context name")
                .that(info.toString()).contains(TEST_CONTEXT_NAME_MUSIC);
        assertWithMessage("Car audio context info string with context id")
                .that(info.toString()).contains(Integer.toString(TEST_CONTEXT_ID_100));
        assertWithMessage("Car audio context info string with audio attribute")
                .that(info.toString()).contains(AudioAttributes
                        .usageToString(AudioAttributes.USAGE_MEDIA));
    }

    @Test
    public void equals_forSameInfo() {
        CarAudioContextInfo info1 = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);

        assertWithMessage("Car audio context info equality").that(info.equals(info1)).isTrue();
    }

    @Test
    public void equals_forSameObject() {
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);
        CarAudioContextInfo info2 = info;

        assertWithMessage("Car audio context info equality for same object")
                .that(info).isEqualTo(info2);
    }

    @Test
    public void equals_forNull() {
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);

        assertWithMessage("Car audio context info equality for null object")
                .that(info.equals(null)).isFalse();
    }

    @Test
    public void equals_forDifferentId() {
        CarAudioContextInfo info1 = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_1000);

        assertWithMessage("Car audio context info equality for different id")
                .that(info.equals(info1)).isFalse();
    }

    @Test
    public void equals_forDifferentName() {
        CarAudioContextInfo info1 = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);
        String contextName2 = "nav_audio";
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                contextName2, TEST_CONTEXT_ID_100);

        assertWithMessage("Car audio context info equality for different name")
                .that(info.equals(info1)).isFalse();
    }

    @Test
    public void equals_forDifferentAttributes() {
        CarAudioContextInfo info1 = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);
        CarAudioContextInfo info = new CarAudioContextInfo(
                new AudioAttributes[] {TEST_NAV_AUDIO_ATTRIBUTE, TEST_NAV_AUDIO_ATTRIBUTE},
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);

        assertWithMessage("Car audio context info equality for different attributes")
                .that(info.equals(info1)).isFalse();
    }

    @Test
    public void hashCode_forSameInfo() {
        CarAudioContextInfo info1 = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);
        CarAudioContextInfo info = new CarAudioContextInfo(TEST_ALL_AUDIO_ATTRIBUTES_ARRAY,
                TEST_CONTEXT_NAME_MUSIC, TEST_CONTEXT_ID_100);

        assertWithMessage("Car audio context info hash code for same info")
                .that(info.hashCode()).isEqualTo(info1.hashCode());
    }
}
