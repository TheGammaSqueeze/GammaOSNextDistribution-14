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

import androidx.annotation.IntDef;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interface for lock/unlock and clear state machine.
 * All users share the same state.
 */
public interface DeviceStateController {

    /**
     * Lock the device.
     */
    ListenableFuture<Void> lockDevice();

    /**
     * Unlock the device.
     */
    ListenableFuture<Void> unlockDevice();

    /**
     * Clear the device restrictions.
     */
    ListenableFuture<Void> clearDevice();

    /** Returns true if the device is in locked state. */
    ListenableFuture<Boolean> isLocked();

    /**
     * Returns the current {@link DeviceState}.
     */
    ListenableFuture<Integer> getDeviceState();

    /** Returns true if the device restrictions have been cleared. */
    ListenableFuture<Boolean> isCleared();

    /** Device state definitions. */
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceState.UNDEFINED,
            DeviceState.UNLOCKED,
            DeviceState.LOCKED,
            DeviceState.CLEARED,
    })
    @interface DeviceState {
        int UNDEFINED = 0;
        int UNLOCKED = 1;
        int LOCKED = 2;
        int CLEARED = 3;
    }
}
