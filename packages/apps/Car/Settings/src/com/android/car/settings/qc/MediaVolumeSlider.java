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

package com.android.car.settings.qc;

import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.settings.qc.SettingsQCRegistry.MEDIA_VOLUME_SLIDER_URI;

import android.content.Context;
import android.net.Uri;

/**
 * Quick control for showing a media volume slider.
 */
public class MediaVolumeSlider extends BaseVolumeSlider {
    public MediaVolumeSlider(Context context) {
        super(context);
    }

    @Override
    protected int[] getUsages() {
        return new int[]{USAGE_MEDIA};
    }

    @Override
    Uri getUri() {
        return MEDIA_VOLUME_SLIDER_URI;
    }

    @Override
    Class getBackgroundWorkerClass() {
        return MediaVolumeSliderWorker.class;
    }
}
