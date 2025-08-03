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
import static android.car.builtin.media.AudioManagerHelper.usageToXsdString;
import static android.car.builtin.media.AudioManagerHelper.xsdStringToUsage;

import static com.android.car.audio.CarHalAudioUtils.usageToMetadata;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.os.ServiceManagerHelper;
import android.car.builtin.util.Slogf;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.DuckingInfo;
import android.hardware.automotive.audiocontrol.IAudioControl;
import android.hardware.automotive.audiocontrol.IAudioGainCallback;
import android.hardware.automotive.audiocontrol.IFocusListener;
import android.hardware.automotive.audiocontrol.IModuleChangeCallback;
import android.hardware.automotive.audiocontrol.MutingInfo;
import android.media.audio.common.AudioPort;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioGainConfigInfo;
import com.android.car.audio.CarDuckingInfo;
import com.android.car.audio.CarHalAudioUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.annotation.AttributeUsage;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Wrapper for AIDL interface for AudioControl HAL */
public final class AudioControlWrapperAidl implements AudioControlWrapper, IBinder.DeathRecipient {
    static final String TAG = CarLog.tagFor(AudioControlWrapperAidl.class);

    private static final String AUDIO_CONTROL_SERVICE =
            "android.hardware.automotive.audiocontrol.IAudioControl/default";

    private static final int AIDL_AUDIO_CONTROL_VERSION_1 = 1;
    private static final int AIDL_AUDIO_CONTROL_VERSION_2 = 2;

    private IBinder mBinder;
    private IAudioControl mAudioControl;
    private boolean mListenerRegistered = false;
    private boolean mGainCallbackRegistered = false;
    private boolean mModuleChangeCallbackRegistered;

    private AudioControlDeathRecipient mDeathRecipient;

    private Executor mExecutor = Executors.newSingleThreadExecutor();

    public static @Nullable IBinder getService() {
        return ServiceManagerHelper.waitForDeclaredService(AUDIO_CONTROL_SERVICE);
    }

    public AudioControlWrapperAidl(IBinder binder) {
        mBinder = Objects.requireNonNull(binder);
        mAudioControl = IAudioControl.Stub.asInterface(binder);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public void unregisterFocusListener() {
        // Focus listener will be unregistered by HAL automatically
    }

    @Override
    public boolean supportsFeature(int feature) {
        switch (feature) {
            case AUDIOCONTROL_FEATURE_AUDIO_FOCUS:
            case AUDIOCONTROL_FEATURE_AUDIO_DUCKING:
            case AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING:
                return true;
            case AUDIOCONTROL_FEATURE_AUDIO_FOCUS_WITH_METADATA:
            case AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK:
                try {
                    return mAudioControl.getInterfaceVersion() > AIDL_AUDIO_CONTROL_VERSION_1;
                } catch (RemoteException e) {
                    Slogf.w("supportsFeature Failed to get version for feature: " + feature, e);
                }
                return false;
            case AUDIOCONTROL_FEATURE_AUDIO_MODULE_CALLBACK:
                try {
                    return mAudioControl.getInterfaceVersion() > AIDL_AUDIO_CONTROL_VERSION_2;
                } catch (RemoteException e) {
                    Slogf.w("supportsFeature Failed to get version for feature: " + feature, e);
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public void registerFocusListener(HalFocusListener focusListener) {
        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "Registering focus listener on AudioControl HAL");
        }
        IFocusListener listenerWrapper = new FocusListenerWrapper(focusListener);
        try {
            mAudioControl.registerFocusListener(listenerWrapper);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to register focus listener");
            throw new IllegalStateException("IAudioControl#registerFocusListener failed", e);
        }
        mListenerRegistered = true;
    }

    @Override
    public void registerAudioGainCallback(HalAudioGainCallback gainCallback) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "Registering Audio Gain Callback on AudioControl HAL");
        }
        Objects.requireNonNull(gainCallback, "Audio Gain Callback can not be null");
        IAudioGainCallback agc = new AudioGainCallbackWrapper(gainCallback);
        try {
            mAudioControl.registerGainCallback(agc);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to register gain callback");
            throw new IllegalStateException("IAudioControl#registerAudioGainCallback failed", e);
        }
        mGainCallbackRegistered = true;
    }

    @Override
    public void unregisterAudioGainCallback() {
        // Audio Gain Callback will be unregistered by HAL automatically
    }

    @Override
    public void onAudioFocusChange(PlaybackTrackMetadata metaData, int zoneId, int focusChange) {
        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "onAudioFocusChange: metadata %s, zoneId %d, focusChanged %d", metaData,
                    zoneId, focusChange);
        }
        try {
            mAudioControl.onAudioFocusChangeWithMetaData(metaData, zoneId, focusChange);
        } catch (RemoteException e) {
            Slogf.d(TAG, "onAudioFocusChange: failed with metadata, retry with usage.");
            onAudioFocusChange(metaData.usage, zoneId, focusChange);
        }
    }

    private void onAudioFocusChange(@AttributeUsage int usage, int zoneId, int focusChange) {
        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "onAudioFocusChange: usage %s, zoneId %d, focusChanged %d",
                    usageToString(usage), zoneId, focusChange);
        }
        try {
            String usageName = usageToXsdString(usage);
            mAudioControl.onAudioFocusChange(usageName, zoneId, focusChange);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to query IAudioControl#onAudioFocusChange", e);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*AudioControlWrapperAidl*");
        writer.increaseIndent();
        try {
            writer.printf("Aidl Version: %d\n", mAudioControl.getInterfaceVersion());
        } catch (RemoteException e) {
            Slogf.e(TAG, "dump getInterfaceVersion error", e);
            writer.printf("Version: Could not be retrieved\n");
        }
        writer.printf("Focus listener registered on HAL? %b\n", mListenerRegistered);
        writer.printf("Audio Gain Callback registered on HAL? %b\n", mGainCallbackRegistered);
        writer.printf("Module change Callback set on HAL? %b\n", mModuleChangeCallbackRegistered);

        writer.println("Supported Features");
        writer.increaseIndent();
        writer.println("- AUDIOCONTROL_FEATURE_AUDIO_FOCUS");
        writer.println("- AUDIOCONTROL_FEATURE_AUDIO_DUCKING");
        if (supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_FOCUS_WITH_METADATA)) {
            writer.println("- AUDIOCONTROL_FEATURE_AUDIO_FOCUS_WITH_METADATA");
            writer.println("- AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK");
        }
        if (supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_MODULE_CALLBACK)) {
            writer.println("- AUDIOCONTROL_FEATURE_AUDIO_MODULE_CALLBACK");
        }
        writer.decreaseIndent();

        writer.decreaseIndent();
    }

    @Override
    public void setFadeTowardFront(float value) {
        try {
            mAudioControl.setFadeTowardFront(value);
        } catch (RemoteException e) {
            Slogf.e(TAG, "setFadeTowardFront with " + value + " failed", e);
        }
    }

    @Override
    public void setBalanceTowardRight(float value) {
        try {
            mAudioControl.setBalanceTowardRight(value);
        } catch (RemoteException e) {
            Slogf.e(TAG, "setBalanceTowardRight with " + value + " failed", e);
        }
    }

    @Override
    public void onDevicesToDuckChange(@NonNull List<CarDuckingInfo> carDuckingInfos) {
        Objects.requireNonNull(carDuckingInfos);
        DuckingInfo[] duckingInfos = new DuckingInfo[carDuckingInfos.size()];
        for (int i = 0; i < carDuckingInfos.size(); i++) {
            CarDuckingInfo info = Objects.requireNonNull(carDuckingInfos.get(i));
            duckingInfos[i] = CarHalAudioUtils.generateDuckingInfo(info);
        }

        try {
            mAudioControl.onDevicesToDuckChange(duckingInfos);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "onDevicesToDuckChange failed");
        }
    }

    @Override
    public void onDevicesToMuteChange(@NonNull List<MutingInfo> carZonesMutingInfo) {
        Objects.requireNonNull(carZonesMutingInfo, "Muting info can not be null");
        Preconditions.checkArgument(!carZonesMutingInfo.isEmpty(), "Muting info can not be empty");
        MutingInfo[] mutingInfoToHal = carZonesMutingInfo
                .toArray(new MutingInfo[carZonesMutingInfo.size()]);
        try {
            mAudioControl.onDevicesToMuteChange(mutingInfoToHal);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "onDevicesToMuteChange failed");
        }
    }

    @Override
    public void setModuleChangeCallback(HalAudioModuleChangeCallback moduleChangeCallback) {
        Objects.requireNonNull(moduleChangeCallback, "Module change callback can not be null");

        IModuleChangeCallback callback = new ModuleChangeCallbackWrapper(moduleChangeCallback);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mAudioControl.setModuleChangeCallback(callback);
                    mModuleChangeCallbackRegistered = true;
                } catch (RemoteException e) {
                    throw new IllegalStateException(
                            "IAudioControl#setModuleChangeCallback failed", e);
                } catch (UnsupportedOperationException e) {
                    Slogf.w(TAG, "Failed to set module change callback, feature not supported");
                } catch (IllegalStateException e) {
                    // we hit this if car service crashed and restarted. lets clear callbacks and
                    // try again one more time.
                    Slogf.w(TAG, "Module change callback already set, retry after clearing");
                    try {
                        mAudioControl.clearModuleChangeCallback();
                        mAudioControl.setModuleChangeCallback(callback);
                        mModuleChangeCallbackRegistered = true;
                    } catch (RemoteException ex) {
                        throw new IllegalStateException(
                                "IAudioControl#setModuleChangeCallback failed (after retry)", ex);
                    } catch (IllegalStateException ex) {
                        Slogf.e(TAG, ex, "Failed to set module change callback (after retry)");
                        // lets  not throw any exception since it may lead to car service failure
                    }
                }
            }
        });
    }

    @Override
    public void clearModuleChangeCallback() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mAudioControl.clearModuleChangeCallback();
                    mModuleChangeCallbackRegistered = false;
                } catch (RemoteException e) {
                    throw new IllegalStateException(
                            "IAudioControl#clearModuleChangeCallback failed", e);
                } catch (UnsupportedOperationException e) {
                    Slogf.w(TAG, "Failed to clear module change callback, feature not supported");
                }
            }
        });
    }

    @Override
    public void linkToDeath(@Nullable AudioControlDeathRecipient deathRecipient) {
        try {
            mBinder.linkToDeath(this, 0);
            mDeathRecipient = deathRecipient;
        } catch (RemoteException e) {
            throw new IllegalStateException("Call to IAudioControl#linkToDeath failed", e);
        }
    }

    @Override
    public void unlinkToDeath() {
        mBinder.unlinkToDeath(this, 0);
        mDeathRecipient = null;
    }

    @Override
    public void binderDied() {
        Slogf.w(TAG, "AudioControl HAL died. Fetching new handle");
        mBinder.unlinkToDeath(this, 0);
        mListenerRegistered = false;
        mGainCallbackRegistered = false;
        mModuleChangeCallbackRegistered = false;
        mBinder = AudioControlWrapperAidl.getService();
        mAudioControl = IAudioControl.Stub.asInterface(mBinder);
        // TODO(b/284043199): Refactor the retry logic out and add delay between retry.
        try {
            mBinder.linkToDeath(this, 0);
        } catch (RemoteException e) {
            // Avoid crashing the binder thread.
            Slogf.e(TAG, "Call to IAudioControl#linkToDeath failed", e);
        }
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
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public int getInterfaceVersion() {
            return this.VERSION;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public String getInterfaceHash() {
            return this.HASH;
        }

        @Override
        public void requestAudioFocus(String usage, int zoneId, int focusGain) {
            @AttributeUsage int usageValue = xsdStringToUsage(usage);
            requestAudioFocus(usageValue, zoneId, focusGain);
        }

        @Override
        public void abandonAudioFocus(String usage, int zoneId) {
            @AttributeUsage int usageValue = xsdStringToUsage(usage);
            abandonAudioFocus(usageValue, zoneId);
        }

        @Override
        public void requestAudioFocusWithMetaData(
                PlaybackTrackMetadata playbackMetaData, int zoneId, int focusGain) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "requestAudioFocusWithMetaData metadata=%s, zoneId=%d, focusGain=%d",
                        playbackMetaData, zoneId, focusGain);
            }
            mListener.requestAudioFocus(playbackMetaData, zoneId, focusGain);
        }

        @Override
        public void abandonAudioFocusWithMetaData(
                PlaybackTrackMetadata playbackMetaData, int zoneId) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "abandonAudioFocusWithMetaData metadata=%s, zoneId=%d",
                        playbackMetaData, zoneId);
            }
            mListener.abandonAudioFocus(playbackMetaData, zoneId);
        }

        private void abandonAudioFocus(int usage, int zoneId) {
            abandonAudioFocusWithMetaData(usageToMetadata(usage), zoneId);
        }

        private void requestAudioFocus(int usage, int zoneId, int focusGain) {
            requestAudioFocusWithMetaData(usageToMetadata(usage), zoneId, focusGain);
        }
    }

    private static final class AudioGainCallbackWrapper extends IAudioGainCallback.Stub {
        private @NonNull final HalAudioGainCallback mCallback;

        AudioGainCallbackWrapper(@NonNull HalAudioGainCallback gainCallback) {
            mCallback = gainCallback;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public int getInterfaceVersion() {
            return VERSION;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public String getInterfaceHash() {
            return HASH;
        }

        @Override
        public void onAudioDeviceGainsChanged(int[] halReasons, AudioGainConfigInfo[] gains) {
            List<CarAudioGainConfigInfo> carAudioGainConfigs = new ArrayList<>();
            for (int index = 0; index < gains.length; index++) {
                AudioGainConfigInfo gain = gains[index];
                carAudioGainConfigs.add(new CarAudioGainConfigInfo(gain));
            }
            List<Integer> reasonsList = new ArrayList<>();
            for (int index = 0; index < halReasons.length; index++) {
                int halReason = halReasons[index];
                if (!HalAudioGainCallback.isReasonValid(halReason)) {
                    Slogf.e(
                            TAG,
                            "onAudioDeviceGainsChanged invalid reasons %d reported, skipped",
                            halReason);
                    continue;
                }
                reasonsList.add(halReason);
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                List<String> gainsString = new ArrayList<>();
                for (int i = 0; i < carAudioGainConfigs.size(); i++) {
                    gainsString.add(carAudioGainConfigs.get(i).toString());
                }
                String gainsLiteral = String.join(",", gainsString);

                List<String> reasonsString = new ArrayList<>();
                for (int i = 0; i < reasonsString.size(); i++) {
                    reasonsString.add(HalAudioGainCallback.reasonToString(reasonsList.get(i)));
                }
                String reasonsLiteral = String.join(",", reasonsString);
                Slogf.d(
                        TAG,
                        "onAudioDeviceGainsChanged for reasons=[%s], gains=[%s]",
                        reasonsLiteral,
                        gainsLiteral);
            }
            mCallback.onAudioDeviceGainsChanged(reasonsList, carAudioGainConfigs);
        }
    }

    private static final class ModuleChangeCallbackWrapper extends IModuleChangeCallback.Stub {
        private final HalAudioModuleChangeCallback mCallback;

        ModuleChangeCallbackWrapper(HalAudioModuleChangeCallback callback) {
            mCallback = callback;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public int getInterfaceVersion() {
            return this.VERSION;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public String getInterfaceHash() {
            return this.HASH;
        }

        @Override
        public void onAudioPortsChanged(AudioPort[] audioPorts) {
            List<HalAudioDeviceInfo> halAudioDeviceInfos = new ArrayList<>();
            for (int index = 0; index < audioPorts.length; index++) {
                AudioPort port = audioPorts[index];
                halAudioDeviceInfos.add(new HalAudioDeviceInfo(port));
            }
            mCallback.onAudioPortsChanged(halAudioDeviceInfos);
        }
    }
}
