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

package com.android.systemui.car.drivemode;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * The state manager for the Drive Mode feature.
 */
public interface DriveModeManager {

    /**
     * Gets the active drive mode name.
     *
     * @return Drive Mode name
     */
    @NonNull String getDriveMode();

    /**
     * Sets the new active drive mode.
     *
     * @param driveMode new active drive mode name
     */
    void setDriveMode(@NonNull String driveMode);

    /**
     * Returns all the available drive modes.
     *
     * @return List of drive mode names
     */
    @NonNull List<String> getAvailableDriveModes();


    /**
     * Adds a drive mode listener. Adding a listener immediately triggers
     * [Callback.onDriveModeChanged] call on it.
     *
     * @param callback The callback to add
     */
    void addCallback(@NonNull Callback callback);

    /**
     * Removes the given listener from the list.
     *
     * @param callback The callback to remove
     */
    void removeCallback(@NonNull Callback callback);

    /**
     * Callback interface for listening to Drive Mode state changes.
     */
    interface Callback {
        /**
         * Triggered on all the listeners when a new drive mode is activated.
         *
         * @param newDriveMode the new active drive mode
         */
        void onDriveModeChanged(@NonNull String newDriveMode);
    }
}
