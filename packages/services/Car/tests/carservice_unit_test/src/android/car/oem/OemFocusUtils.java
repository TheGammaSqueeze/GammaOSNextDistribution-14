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

package android.car.oem;

import static android.os.Build.VERSION.SDK_INT;

import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;

final class OemFocusUtils {

    public static final int MEDIA_EMPTY_FLAG = 0;
    public static final int TEST_VOLUME_GROUP_ID = 3;
    public static final int TEST_AUDIO_CONTEXT = 1;
    public static final int MEDIA_APP_UID = 100000;
    public static final String MEDIA_CLIENT_ID = "client-id";
    public static final String MEDIA_PACKAGE_NAME = "android.car.oem";

    static AudioFocusEntry getAudioFocusEntry(int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        builder.setUsage(usage);

        AudioFocusInfo info = new AudioFocusInfo(builder.build(), MEDIA_APP_UID, MEDIA_CLIENT_ID,
                MEDIA_PACKAGE_NAME, AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_NONE,
                MEDIA_EMPTY_FLAG, SDK_INT);

        return new AudioFocusEntry.Builder(info, TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID,
                AudioManager.AUDIOFOCUS_NONE).build();
    }
}
