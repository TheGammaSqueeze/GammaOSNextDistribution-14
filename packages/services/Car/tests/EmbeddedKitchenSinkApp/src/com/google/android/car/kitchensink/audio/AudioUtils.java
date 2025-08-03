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

package com.google.android.car.kitchensink.audio;

import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.annotation.Nullable;

import java.util.List;

final class AudioUtils {

    private AudioUtils() {
        throw new UnsupportedOperationException();
    }

    static int getCurrentZoneId(Context context, CarAudioManager manager) throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                context.getPackageName(), /* flags= */ 0);
        return manager.getZoneIdForUid(info.uid);
    }

    @Nullable
    static CarAudioZoneConfigInfo getCarAudioZoneConfigInfoOrNull(
            List<CarAudioZoneConfigInfo> configs, int zoneId, int configId) {
        CarAudioZoneConfigInfo info = configs.stream()
                .filter(c -> c.getZoneId() == zoneId && c.getConfigId() == configId)
                .findFirst().orElse(null);
        return info;
    }
}
