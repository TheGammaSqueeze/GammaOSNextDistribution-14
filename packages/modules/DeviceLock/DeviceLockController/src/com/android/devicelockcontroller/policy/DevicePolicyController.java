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

package com.android.devicelockcontroller.policy;

import android.content.Intent;

import androidx.annotation.IntDef;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interface for the policy controller that is responsible for applying policies based
 * on state.
 */
public interface DevicePolicyController {

    /**
     * Factory resets the device when the setup has failed and cannot continue.
     * Returns true if action was successful.
     * <p>
     * Using the new {@code DevicePolicyManager#wipeDevice()} introduced in Android U to
     * reset the device. This is because the {@code DevicePolicyManager#wipeData()} no longer resets
     * the device when called as the device owner, as it used to do in earlier Android versions.
     */
    boolean wipeDevice();

    /**
     * Enforce current policies.
     */
    ListenableFuture<Void> enforceCurrentPolicies();

    /**
     * Enforce current policies. This is only used in an attempts to restore previous enforced
     * policies in the case enforceCurrentPolicies() fails.
     */
    ListenableFuture<Void> enforceCurrentPoliciesForCriticalFailure();

    /**
     * Get the launch intent for current enforced state.
     */
    ListenableFuture<Intent> getLaunchIntentForCurrentState();

    /**
     * Called by {@link com.android.devicelockcontroller.DeviceLockControllerService} to call
     * encryption-aware components.
     */
    ListenableFuture<Void> onUserUnlocked();

    /**
     * Called when a user has completed set-up wizard.
     */
    ListenableFuture<Void> onUserSetupCompleted();

    /**
     * Called by {@link com.android.devicelockcontroller.DeviceLockControllerService} when the
     * controller or kiosk app crashed.
     */
    ListenableFuture<Void> onAppCrashed(boolean isKiosk);

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            LockTaskType.UNDEFINED,
            LockTaskType.NOT_IN_LOCK_TASK,
            LockTaskType.LANDING_ACTIVITY,
            LockTaskType.CRITICAL_ERROR,
            LockTaskType.KIOSK_SETUP_ACTIVITY,
            LockTaskType.KIOSK_LOCK_ACTIVITY
    })
    @interface LockTaskType {

        int UNDEFINED = -1;
        /* Not in lock task mode */
        int NOT_IN_LOCK_TASK = 0;

        /* Device lock controller landing activity */
        int LANDING_ACTIVITY = 1;

        /* Hit a critical error during policy enforcement, device will be reset */
        int CRITICAL_ERROR = 2;

        /* Kiosk app setup activity */
        int KIOSK_SETUP_ACTIVITY = 3;

        /* Kiosk app lock activity */
        int KIOSK_LOCK_ACTIVITY = 4;
    }
}
