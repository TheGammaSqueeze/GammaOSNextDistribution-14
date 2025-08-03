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

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * In-memory implementation for [DriveModeManager], the state is not saved and gets resetted
 * at every system start.
 */
public class InMemoryDriveModeManager implements DriveModeManager {

    private final List<String> mAvailableModes;
    private Set<Callback> mDriveModeCallbacks;
    private String mActiveDriveMode;

    @Inject
    public InMemoryDriveModeManager(Context context) {
        mDriveModeCallbacks = new HashSet<>();

        Resources res = context.getResources();
        mAvailableModes = new ArrayList<>();
        mAvailableModes.add(res.getString(R.string.drive_mode_modes_comfort));
        mAvailableModes.add(res.getString(R.string.drive_mode_modes_eco));
        mAvailableModes.add(res.getString(R.string.drive_mode_modes_sport));

        //"Comfort" is the default driving state in this implementation
        setDriveMode(context.getResources().getString(R.string.drive_mode_modes_comfort));
    }

    @Override
    @NonNull
    public String getDriveMode() {
        return mActiveDriveMode;
    }

    @Override
    public void setDriveMode(@NonNull String driveState) {
        mActiveDriveMode = driveState;
        for (Callback listener : mDriveModeCallbacks) {
            listener.onDriveModeChanged(mActiveDriveMode);
        }
    }

    /*
     * In this first implementation this method contains hardcoded states, most likely migrating to
     * a vhal integration in the future.
     */
    @Override
    @NonNull
    public List<String> getAvailableDriveModes() {
        return mAvailableModes;
    }

    @Override
    public void addCallback(Callback callback) {
        mDriveModeCallbacks.add(callback);
        callback.onDriveModeChanged(mActiveDriveMode);
    }

    @Override
    public void removeCallback(Callback callback) {
        mDriveModeCallbacks.remove(callback);
    }
}
