/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.MutingInfo;
import android.hardware.automotive.audiocontrol.V1_0.IAudioControl;
import android.os.RemoteException;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioContext;
import com.android.car.audio.CarDuckingInfo;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Wrapper for IAudioControl@1.0.
 */
public final class AudioControlWrapperV1 implements AudioControlWrapper {
    private static final String TAG = CarLog.tagFor(AudioControlWrapperV1.class);

    private IAudioControl mAudioControlV1;
    private AudioControlDeathRecipient mDeathRecipient;

    /**
     * Gets IAudioControl@1.0 service if registered.
     */
    static @Nullable IAudioControl getService() {
        try {
            return IAudioControl.getService(true);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to get IAudioControl@1.0 service", e);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @VisibleForTesting
    AudioControlWrapperV1(IAudioControl audioControlV1) {
        mAudioControlV1 = Objects.requireNonNull(audioControlV1);
    }

    @Override
    public void registerFocusListener(HalFocusListener focusListener) {
        throw new UnsupportedOperationException(
                "Focus listener is unsupported for IAudioControl@1.0");
    }

    @Override
    public void unregisterFocusListener() {
        throw new UnsupportedOperationException(
                "Focus listener is unsupported for IAudioControl@1.0");
    }

    @Override
    public void registerAudioGainCallback(HalAudioGainCallback gainCallback) {
        throw new UnsupportedOperationException(
                "Audio Gain Callback is unsupported for IAudioControl@1.0");
    }

    @Override
    public void unregisterAudioGainCallback() {
        throw new UnsupportedOperationException(
                "Audio Gain Callback is unsupported for IAudioControl@1.0");
    }

    @Override
    public boolean supportsFeature(int feature) {
        return false;
    }

    @Override
    public void onAudioFocusChange(PlaybackTrackMetadata metaData, int zoneId, int focusChange) {
        throw new UnsupportedOperationException(
                "Focus listener is unsupported for IAudioControl@1.0");
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*AudioControlWrapperV1*");
        writer.println("Supported Features - none");
    }

    @Override
    public void setFadeTowardFront(float value) {
        try {
            mAudioControlV1.setFadeTowardFront(value);
        } catch (RemoteException e) {
            Slogf.e(TAG, "setFadeTowardFront failed", e);
        }
    }

    @Override
    public void setBalanceTowardRight(float value) {
        try {
            mAudioControlV1.setBalanceTowardRight(value);
        } catch (RemoteException e) {
            Slogf.e(TAG, "setBalanceTowardRight failed", e);
        }
    }

    @Override
    public void onDevicesToDuckChange(List<CarDuckingInfo> carDuckingInfos) {
        throw new UnsupportedOperationException("HAL ducking is unsupported for IAudioControl@1.0");
    }

    @Override
    public void onDevicesToMuteChange(@NonNull List<MutingInfo> carZonesMutingInfo) {
        throw new UnsupportedOperationException("HAL muting is unsupported for IAudioControl@1.0");
    }

    @Override
    public void setModuleChangeCallback(HalAudioModuleChangeCallback moduleChangeCallback) {
        throw new UnsupportedOperationException("Module change callback is unsupported for"
                + " IAudioControl@1.0");
    }

    @Override
    public void clearModuleChangeCallback() {
        throw new UnsupportedOperationException("Module change callback is unsupported for"
                + " IAudioControl@1.0");
    }

    /**
     * Gets the bus associated with CarAudioContext.
     *
     * <p>This API is used along with car_volume_groups.xml to configure volume groups and routing.
     *
     * @param audioContext {@code CarAudioContext} to get a context for.
     * @return int bus number. Should be part of the prefix for the device's address. For example,
     * bus001_media would be bus 1.
     * @deprecated Volume and routing configuration has been replaced by
     * car_audio_configuration.xml. Starting with IAudioControl@V2.0, getBusForContext is no longer
     * supported.
     */
    @Deprecated
    public int getBusForContext(@CarAudioContext.AudioContext int audioContext) {
        try {
            return mAudioControlV1.getBusForContext(audioContext);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to query IAudioControl HAL to get bus for context", e);
            throw new IllegalStateException("Failed to query IAudioControl#getBusForContext", e);
        }
    }

    @Override
    public void linkToDeath(@Nullable AudioControlDeathRecipient deathRecipient) {
        try {
            mAudioControlV1.linkToDeath(this::serviceDied, 0);
            mDeathRecipient = deathRecipient;
        } catch (RemoteException e) {
            throw new IllegalStateException("Call to IAudioControl@1.0#linkToDeath failed", e);
        }
    }

    @Override
    public void unlinkToDeath() {
        try {
            mAudioControlV1.unlinkToDeath(this::serviceDied);
            mDeathRecipient = null;
        } catch (RemoteException e) {
            throw new IllegalStateException("Call to IAudioControl@1.0#unlinkToDeath failed", e);
        }
    }

    private void serviceDied(long cookie) {
        Slogf.w(TAG, "IAudioControl@1.0 died. Fetching new handle");
        mAudioControlV1 = AudioControlWrapperV1.getService();
        linkToDeath(mDeathRecipient);
        if (mDeathRecipient != null) {
            mDeathRecipient.serviceDied();
        }
    }


}
