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

import android.content.Context;
import android.media.AudioManager;

import java.util.List;
import java.util.Map;

public class AudioManagerCompat {
    private final AudioManager mAudioManager;

    public AudioManagerCompat(Context context) {
        this.mAudioManager = new AudioManager(context);
    }

    public Map<Integer, Boolean> getSurroundFormats() {
        return mAudioManager.getSurroundFormats();
    }

    public List<Integer> getReportedSurroundFormats() {
        return mAudioManager.getReportedSurroundFormats();
    }

    public void setSurroundFormatEnabled(int format, boolean enabled) {
        mAudioManager.setSurroundFormatEnabled(format, enabled);
    }

    public void setEncodedSurroundMode(int mode) {
        mAudioManager.setEncodedSurroundMode(mode);
    }
}
