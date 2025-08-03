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

import static com.android.car.settings.qc.BaseVolumeSlider.QC_VOLUME_SELF_CHANGE;

import android.car.media.CarAudioManager;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.CarSettingsApplication;

import java.io.IOException;

/**
 * Base worker class for {@link BaseVolumeSlider} instances.
 * @param <E> QCItem class that extends {@link BaseVolumeSlider}
 */
public abstract class BaseVolumeSliderWorker<E extends BaseVolumeSlider>
        extends SettingsQCBackgroundWorker<E> {
    private final Context mContext;

    private final CarAudioManager.CarVolumeCallback mVolumeChangeCallback =
            new CarAudioManager.CarVolumeCallback() {
                @Override
                public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
                    if (flags != QC_VOLUME_SELF_CHANGE) {
                        updateVolumeAndMute(zoneId, groupId);
                    }
                }

                @Override
                public void onMasterMuteChanged(int zoneId, int flags) {
                    // Mute is not being used yet
                }

                @Override
                public void onGroupMuteChanged(int zoneId, int groupId, int flags) {
                    if (flags != QC_VOLUME_SELF_CHANGE) {
                        updateVolumeAndMute(zoneId, groupId);
                    }

                }
            };

    public BaseVolumeSliderWorker(Context context, Uri uri) {
        super(context, uri);
        mContext = context;
    }

    protected abstract int[] getUsages();

    @Override
    protected void onQCItemSubscribe() {
        CarAudioManager carAudioManager = getCarAudioManager();
        if (carAudioManager != null) {
            carAudioManager.registerCarVolumeCallback(mVolumeChangeCallback);
        }
    }

    @Override
    protected void onQCItemUnsubscribe() {
        CarAudioManager carAudioManager = getCarAudioManager();
        if (carAudioManager != null) {
            carAudioManager.unregisterCarVolumeCallback(mVolumeChangeCallback);
        }
    }

    @Override
    public void close() throws IOException {
    }

    @VisibleForTesting
    CarAudioManager.CarVolumeCallback getVolumeChangeCallback() {
        return mVolumeChangeCallback;
    }

    private void updateVolumeAndMute(int zoneId, int groupId) {
        // Settings only handles my audio zone changes
        if (zoneId != getMyAudioZoneId()) {
            return;
        }
        CarAudioManager carAudioManager = getCarAudioManager();
        if (carAudioManager != null) {
            for (int usage : getUsages()) {
                if (carAudioManager
                        .getVolumeGroupIdForUsage(getMyAudioZoneId(), usage) == groupId) {
                    notifyQCItemChange();
                    break;
                }
            }
        }
    }

    private int getMyAudioZoneId() {
        return ((CarSettingsApplication) mContext.getApplicationContext())
                .getMyAudioZoneId();
    }

    private CarAudioManager getCarAudioManager() {
        return ((CarSettingsApplication) mContext.getApplicationContext())
                .getCarAudioManager();
    }
}
