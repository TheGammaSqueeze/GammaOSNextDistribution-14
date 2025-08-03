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

import static android.car.builtin.media.AudioManagerHelper.usageToString;

import static com.android.car.audio.CarHalAudioUtils.usageToMetadata;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.MutingInfo;
import android.hardware.automotive.audiocontrol.V2_0.IAudioControl;
import android.hardware.automotive.audiocontrol.V2_0.ICloseHandle;
import android.hardware.automotive.audiocontrol.V2_0.IFocusListener;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.audio.CarDuckingInfo;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Wrapper for IAudioControl@2.0.
 */
public final class AudioControlWrapperV2 implements AudioControlWrapper {
    private static final String TAG = CarLog.tagFor(AudioControlWrapperV2.class);

    private IAudioControl mAudioControlV2;

    private AudioControlDeathRecipient mDeathRecipient;
    private ICloseHandle mCloseHandle;

    static @Nullable IAudioControl getService() {
        try {
            return IAudioControl.getService(true);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to get IAudioControl@2.0 service", e);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    AudioControlWrapperV2(IAudioControl audioControlV2) {
        mAudioControlV2 = Objects.requireNonNull(audioControlV2);
    }

    @Override
    public void unregisterFocusListener() {
        if (mCloseHandle != null) {
            try {
                mCloseHandle.close();
            } catch (RemoteException e) {
                Slogf.e(TAG, "Failed to close focus listener", e);
            } finally {
                mCloseHandle = null;
            }
        }
    }

    @Override
    public boolean supportsFeature(int feature) {
        return feature == AUDIOCONTROL_FEATURE_AUDIO_FOCUS;
    }

    @Override
    public void registerFocusListener(HalFocusListener focusListener) {
        Slogf.d(TAG, "Registering focus listener on AudioControl HAL");
        IFocusListener listenerWrapper = new FocusListenerWrapper(focusListener);
        try {
            mCloseHandle = mAudioControlV2.registerFocusListener(listenerWrapper);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to register focus listener");
            throw new IllegalStateException("IAudioControl#registerFocusListener failed", e);
        }
    }

    @Override
    public void registerAudioGainCallback(HalAudioGainCallback gainCallback) {
        throw new UnsupportedOperationException(
                "Audio Gain Callback is unsupported for IAudioControl@2.0");
    }

    @Override
    public void unregisterAudioGainCallback() {
        throw new UnsupportedOperationException(
                "Audio Gain Callback is unsupported for IAudioControl@2.0");
    }

    @Override
    public void onAudioFocusChange(PlaybackTrackMetadata metaData, int zoneId, int focusChange) {
        int usage = metaData.usage;
        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "onAudioFocusChange: usage " + usageToString(usage)
                    + ", zoneId " + zoneId + ", focusChange " + focusChange);
        }
        try {
            mAudioControlV2.onAudioFocusChange(usage, zoneId, focusChange);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to query IAudioControl#onAudioFocusChange", e);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*AudioControlWrapperV2*");
        writer.increaseIndent();
        writer.printf("Focus listener registered on HAL? %b\n", (mCloseHandle != null));

        writer.println("Supported Features");
        writer.increaseIndent();
        writer.println("- AUDIOCONTROL_FEATURE_AUDIO_FOCUS");
        writer.decreaseIndent();

        writer.decreaseIndent();
    }

    @Override
    public void setFadeTowardFront(float value) {
        try {
            mAudioControlV2.setFadeTowardFront(value);
        } catch (RemoteException e) {
            Slogf.e(TAG, "setFadeTowardFront failed", e);
        }
    }

    @Override
    public void setBalanceTowardRight(float value) {
        try {
            mAudioControlV2.setBalanceTowardRight(value);
        } catch (RemoteException e) {
            Slogf.e(TAG, "setBalanceTowardRight failed", e);
        }
    }

    @Override
    public void onDevicesToDuckChange(List<CarDuckingInfo> carDuckingInfos) {
        throw new UnsupportedOperationException("HAL ducking is unsupported for IAudioControl@2.0");
    }

    @Override
    public void onDevicesToMuteChange(@NonNull List<MutingInfo> carZonesMutingInfo) {
        throw new UnsupportedOperationException("HAL muting is unsupported for IAudioControl@2.0");
    }

    @Override
    public void setModuleChangeCallback(HalAudioModuleChangeCallback moduleChangeCallback) {
        throw new UnsupportedOperationException("Module change callback is unsupported for"
                + " IAudioControl@2.0");
    }

    @Override
    public void clearModuleChangeCallback() {
        throw new UnsupportedOperationException("Module change callback is unsupported for"
                + " IAudioControl@2.0");
    }

    @Override
    public void linkToDeath(@Nullable AudioControlDeathRecipient deathRecipient) {
        try {
            mAudioControlV2.linkToDeath(this::serviceDied, 0);
            mDeathRecipient = deathRecipient;
        } catch (RemoteException e) {
            throw new IllegalStateException("Call to IAudioControl@2.0#linkToDeath failed", e);
        }
    }

    @Override
    public void unlinkToDeath() {
        try {
            mAudioControlV2.unlinkToDeath(this::serviceDied);
            mDeathRecipient = null;
        } catch (RemoteException e) {
            throw new IllegalStateException("Call to IAudioControl@2.0#unlinkToDeath failed", e);
        }
    }

    private void serviceDied(long cookie) {
        Slogf.w(TAG, "IAudioControl@2.0 died. Fetching new handle");
        mAudioControlV2 = AudioControlWrapperV2.getService();
        linkToDeath(mDeathRecipient);
        if (mDeathRecipient != null) {
            mDeathRecipient.serviceDied();
        }
    }

    private static final class FocusListenerWrapper extends IFocusListener.Stub {
        private final HalFocusListener mListener;

        FocusListenerWrapper(HalFocusListener halFocusListener) {
            mListener = halFocusListener;
        }

        @Override
        public void requestAudioFocus(int usage, int zoneId, int focusGain) throws RemoteException {
            mListener.requestAudioFocus(usageToMetadata(usage), zoneId, focusGain);
        }

        @Override
        public void abandonAudioFocus(int usage, int zoneId) throws RemoteException {
            mListener.abandonAudioFocus(usageToMetadata(usage), zoneId);
        }
    }
}
