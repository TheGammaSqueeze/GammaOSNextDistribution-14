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

package com.android.car.audio;

import static android.car.hardware.power.PowerComponent.AUDIO;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.power.CarPowerManagementService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

class CarAudioPowerListener {
    private static final String TAG = CarLog.tagFor(CarAudioPowerListener.class);

    private final Object mLock = new Object();
    private final CarAudioService mCarAudioService;
    private final CarPowerManagementService mCarPowerManagementService;

    private final ICarPowerPolicyListener mChangeListener =
            new ICarPowerPolicyListener.Stub() {
                @Override
                public void onPolicyChanged(CarPowerPolicy policy,
                        CarPowerPolicy accumulatedPolicy) {
                    synchronized (mLock) {
                        if (mIsAudioEnabled != accumulatedPolicy.isComponentEnabled(AUDIO)) {
                            updateAudioPowerStateLocked(accumulatedPolicy);
                        }
                    }
                }
            };

    @GuardedBy("mLock")
    private boolean mIsAudioEnabled;

    static CarAudioPowerListener newCarAudioPowerListener(
            @NonNull CarAudioService carAudioService) {
        CarPowerManagementService carPowerService = CarLocalServices.getService(
                CarPowerManagementService.class);
        return new CarAudioPowerListener(carAudioService, carPowerService);
    }

    @VisibleForTesting
    CarAudioPowerListener(@NonNull CarAudioService carAudioService,
            CarPowerManagementService carPowerManagementService) {
        mCarAudioService = Objects.requireNonNull(carAudioService);
        mCarPowerManagementService = carPowerManagementService;
    }

    boolean isAudioEnabled() {
        synchronized (mLock) {
            return mIsAudioEnabled;
        }
    }

    void startListeningForPolicyChanges() {
        if (mCarPowerManagementService == null) {
            Slogf.w(TAG, "Cannot find CarPowerManagementService");
            return;
        }

        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(AUDIO).build();
        mCarPowerManagementService.addPowerPolicyListener(filter, mChangeListener);
        initializePowerState();
    }

    void stopListeningForPolicyChanges() {
        if (mCarPowerManagementService == null) {
            return;
        }
        mCarPowerManagementService.removePowerPolicyListener(mChangeListener);
    }

    private void initializePowerState() {
        CarPowerPolicy policy = mCarPowerManagementService.getCurrentPowerPolicy();

        if (policy == null) {
            Slogf.w(TAG, "Policy is null. Defaulting to enabled");
            return;
        }

        synchronized (mLock) {
            updateAudioPowerStateLocked(policy);
        }
    }

    @GuardedBy("mLock")
    private void updateAudioPowerStateLocked(CarPowerPolicy policy) {
        mIsAudioEnabled = policy.isComponentEnabled(AUDIO);
        mCarAudioService.setAudioEnabled(mIsAudioEnabled);
    }
}
