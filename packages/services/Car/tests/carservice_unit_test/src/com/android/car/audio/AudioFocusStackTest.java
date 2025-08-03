/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class AudioFocusStackTest {

    private static final String MEDIA_CLIENT_ID = "media-client-id";
    private static final String NAVIGATION_CLIENT_ID = "nav-client-id";

    private static final int MEDIA_CLIENT_UID = 1000009;
    private static final int NAVIGATION_CLIENT_UID = 1010101;

    public static final AudioFocusInfo NAVIGATION_FOCUS_INFO =
            createAudioFocusInfo(MEDIA_CLIENT_ID, MEDIA_CLIENT_UID, USAGE_MEDIA);

    public static final AudioFocusInfo MEDIA_FOCUS_INFO =
            createAudioFocusInfo(NAVIGATION_CLIENT_ID, NAVIGATION_CLIENT_UID,
                    USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

    @Test
    public void constructor_withNullActiveList_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new AudioFocusStack(/* activeFocusList= */ null, List.of());
        });

        assertWithMessage("Invalid active foci exception").that(thrown)
                .hasMessageThat().contains("Active foci info");
    }

    @Test
    public void constructor_withNullInactiveList_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new AudioFocusStack(List.of(), /* inactiveFocusList= */ null);
        });

        assertWithMessage("Invalid inactive foci exception").that(thrown)
                .hasMessageThat().contains("Inactive foci info");
    }

    @Test
    public void getActiveFocusList() {
        AudioFocusStack stack =
                new AudioFocusStack(List.of(MEDIA_FOCUS_INFO, NAVIGATION_FOCUS_INFO), List.of());

        List<AudioFocusInfo> activeFoci = stack.getActiveFocusList();

        assertWithMessage("Active foci stack").that(activeFoci)
                .containsExactly(MEDIA_FOCUS_INFO, NAVIGATION_FOCUS_INFO);
    }

    @Test
    public void getActiveFocusList_withEmptyInfo() {
        AudioFocusStack stack =
                new AudioFocusStack(List.of(), List.of(MEDIA_FOCUS_INFO, NAVIGATION_FOCUS_INFO));

        List<AudioFocusInfo> activeFoci = stack.getActiveFocusList();

        assertWithMessage("Empty active foci stack").that(activeFoci).isEmpty();
    }

    @Test
    public void getInactiveFocusList() {
        AudioFocusStack stack =
                new AudioFocusStack(List.of(), List.of(MEDIA_FOCUS_INFO, NAVIGATION_FOCUS_INFO));

        List<AudioFocusInfo> inactiveFoci = stack.getInactiveFocusList();

        assertWithMessage("Inactive foci stack").that(inactiveFoci)
                .containsExactly(MEDIA_FOCUS_INFO, NAVIGATION_FOCUS_INFO);
    }

    @Test
    public void getInactiveFocusList_withEmptyInfo() {
        AudioFocusStack stack =
                new AudioFocusStack(List.of(MEDIA_FOCUS_INFO, NAVIGATION_FOCUS_INFO), List.of());

        List<AudioFocusInfo> inactiveFoci = stack.getInactiveFocusList();

        assertWithMessage("Empty inactive foci stack").that(inactiveFoci).isEmpty();
    }

    private static AudioFocusInfo createAudioFocusInfo(String clientId, int clientUid, int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        if (AudioAttributes.isSystemUsage(usage)) {
            builder.setSystemUsage(usage);
        } else {
            builder.setUsage(usage);
        }

        return new AudioFocusInfo(builder.build(), clientUid, clientId,
                /* packageName= */ "com.android.car.audio", AudioManager.AUDIOFOCUS_GAIN,
                AudioManager.AUDIOFOCUS_NONE, /* flags= */ 0, Build.VERSION.SDK_INT);
    }
}
