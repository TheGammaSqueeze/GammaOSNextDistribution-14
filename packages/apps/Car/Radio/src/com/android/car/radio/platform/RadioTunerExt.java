/**
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.radio.platform;

import android.content.Context;
import android.hardware.radio.ProgramList;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.HwAudioSource;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.android.car.radio.util.Log;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Proposed extensions to android.hardware.radio.RadioTuner.
 *
 * They might eventually get pushed to the framework.
 */
public final class RadioTunerExt {
    private static final String TAG = "BcRadioApp.tunerext";

    private final Object mLock = new Object();
    private final RadioTuner mTuner;

    @GuardedBy("mLock")
    private HwAudioSource mHwAudioSource;

    @GuardedBy("mLock")
    @Nullable private ProgramSelector mOperationSelector;  // null for seek operations

    @GuardedBy("mLock")
    @Nullable private TuneCallback mOperationResultCb;

    /**
     * A callback handling tune/seek operation result.
     */
    public interface TuneCallback {
        /**
         * Called when tune operation finished.
         *
         * @param succeeded States whether the operation succeeded or not.
         */
        void onFinished(boolean succeeded);

        /**
         * Chains other result callbacks.
         */
        default TuneCallback alsoCall(TuneCallback other) {
            return succeeded -> {
                onFinished(succeeded);
                other.onFinished(succeeded);
            };
        }
    }

    RadioTunerExt(Context context, RadioTuner tuner, TunerCallbackAdapterExt cbExt) {
        mTuner = Objects.requireNonNull(tuner, "Tuner cannot be null");
        cbExt.setTuneFailedCallback(this::onTuneFailed);
        cbExt.setProgramInfoCallback(this::onProgramInfoChanged);

        AudioDeviceInfo tunerDevice = findTunerDevice(context, /* address= */ null);
        if (tunerDevice == null) {
            Log.e(TAG, "No TUNER_DEVICE found on board");
        } else {
            mHwAudioSource = new HwAudioSource.Builder()
                .setAudioDeviceInfo(tunerDevice)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build())
                .build();
        }
    }

    public boolean setMuted(boolean muted) {
        synchronized (mLock) {
            if (mHwAudioSource == null) {
                Log.e(TAG, "No TUNER_DEVICE found on board when setting muted");
                return false;
            }
            if (muted) {
                mHwAudioSource.stop();
            } else {
                mHwAudioSource.start();
            }
            return true;
        }
    }

    /**
     * See {@link RadioTuner#scan}.
     */
    public void seek(boolean forward, @Nullable TuneCallback resultCb) {
        synchronized (mLock) {
            markOperationFinishedLocked(/* succeeded= */ false);
            mOperationResultCb = resultCb;
        }

        int direction =  forward ? RadioTuner.DIRECTION_UP : RadioTuner.DIRECTION_DOWN;
        boolean skipSubChannel = false;
        int res;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            res = mTuner.seek(direction, skipSubChannel);
        } else {
            res = mTuner.scan(direction, skipSubChannel);
        }
        if (res != RadioManager.STATUS_OK) {
            throw new RuntimeException("Seek failed with result of " + res);
        }
    }

    /**
     * See {@link RadioTuner#step}.
     */
    public void step(boolean forward, @Nullable TuneCallback resultCb) {
        synchronized (mLock) {
            markOperationFinishedLocked(/* succeeded= */ false);
            mOperationResultCb = resultCb;
        }

        int res =
                mTuner.step(forward ? RadioTuner.DIRECTION_UP : RadioTuner.DIRECTION_DOWN,
                        /* skipSubChannel= */ false);
        if (res != RadioManager.STATUS_OK) {
            throw new RuntimeException("Step failed with result of " + res);
        }
    }

    /**
     * See {@link RadioTuner#tune}.
     */
    public void tune(ProgramSelector selector, @Nullable TuneCallback resultCb) {
        synchronized (mLock) {
            markOperationFinishedLocked(/* succeeded= */ false);
            mOperationSelector = selector;
            mOperationResultCb = resultCb;
        }

        mTuner.tune(selector);
    }

    /**
     * Get the {@link AudioDeviceInfo} instance with {@link AudioDeviceInfo#TYPE_FM_TUNER}
     * by a given address. If the given address is null, returns the first found one.
     */
    private AudioDeviceInfo findTunerDevice(Context context, @Nullable String address) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_FM_TUNER) {
                if (TextUtils.isEmpty(address) || address.equals(device.getAddress())) {
                    return device;
                }
            }
        }
        return null;
    }

    @GuardedBy("mLock")
    private void markOperationFinishedLocked(boolean succeeded) {
        if (mOperationResultCb == null) {
            return;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Tune operation for " + mOperationSelector
                    + (succeeded ? " succeeded" : " failed"));
        }

        TuneCallback cb = mOperationResultCb;
        mOperationSelector = null;
        mOperationResultCb = null;

        cb.onFinished(succeeded);

        if (mOperationSelector != null) {
            throw new IllegalStateException("Can't tune in callback's failed branch. It might "
                    + "interfere with tune operation that requested current one cancellation");
        }
    }

    private boolean isMatching(ProgramSelector currentOperation, ProgramSelector event) {
        ProgramSelector.Identifier pri = currentOperation.getPrimaryId();
        return Stream.of(event.getAllIds(pri.getType())).anyMatch(id -> pri.equals(id));
    }

    private void onProgramInfoChanged(RadioManager.ProgramInfo info) {
        synchronized (mLock) {
            if (mOperationResultCb == null) {
                return;
            }
            // if we're seeking, all program info chanes does match
            if (mOperationSelector != null) {
                if (!isMatching(mOperationSelector, info.getSelector())) {
                    return;
                }
            }
            markOperationFinishedLocked(/* succeeded= */ true);
        }
    }

    private void onTuneFailed(int result, @Nullable ProgramSelector selector) {
        synchronized (mLock) {
            if (mOperationResultCb == null) {
                return;
            }
            // if we're seeking and got a failed tune (or vice versa), that's a mismatch
            if ((mOperationSelector == null) != (selector == null)) {
                return;
            }
            if (mOperationSelector != null) {
                if (!isMatching(mOperationSelector, selector)) {
                    return;
                }
            }
            markOperationFinishedLocked(/* succeeded= */ false);
        }
    }

    /**
     * See {@link RadioTuner#cancel}.
     */
    public void cancel() {
        synchronized (mLock) {
            markOperationFinishedLocked(/* succeeded= */ false);
        }

        int res = mTuner.cancel();
        if (res != RadioManager.STATUS_OK) {
            Log.e(TAG, "Cancel failed with result of " + res);
        }
    }

    /**
     * See {@link RadioTuner#getDynamicProgramList}.
     */
    @Nullable
    public ProgramList getDynamicProgramList(@Nullable ProgramList.Filter filter) {
        return mTuner.getDynamicProgramList(filter);
    }

    public void close() {
        synchronized (mLock) {
            markOperationFinishedLocked(/* succeeded= */ false);
            if (mHwAudioSource != null) {
                mHwAudioSource.stop();
                mHwAudioSource = null;
            }
        }

        mTuner.close();
    }
}
