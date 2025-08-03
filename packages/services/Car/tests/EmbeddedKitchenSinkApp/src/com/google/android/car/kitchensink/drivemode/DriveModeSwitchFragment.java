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

package com.google.android.car.kitchensink.drivemode;

import android.annotation.Nullable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ToggleButton;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

public final class DriveModeSwitchFragment extends Fragment {

    private DriveModeSwitchController mDriveModeSwitchController;
    private ToggleButton mDriveModeToggle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDriveModeSwitchController = new DriveModeSwitchController(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.drive_mode_switch, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mDriveModeToggle = view.findViewById(R.id.drive_mode_toggle);
        mDriveModeToggle.setOnClickListener(v -> toggleDriveModeState());
        mDriveModeToggle.setChecked(mDriveModeSwitchController.isDriveModeActive());
    }

    private void toggleDriveModeState() {
        boolean newState = mDriveModeToggle.isChecked();
        mDriveModeSwitchController.setDriveMode(newState);
    }
}
