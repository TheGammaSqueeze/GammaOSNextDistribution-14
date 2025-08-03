/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.devicelockcontroller.activities;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import androidx.fragment.app.FragmentManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DeviceLockSettingsActivityTest {

    @Test
    public void onCreate_setDeviceInfoSettingsFragment() {
        Intent intent = new Intent();
        DeviceLockSettingsActivity activity = Robolectric.buildActivity(
                DeviceLockSettingsActivity.class,
                intent).setup().get();

        FragmentManager fragmentManager = activity.getSupportFragmentManager();

        assertThat(fragmentManager.getFragments().get(0)).isInstanceOf(
                DeviceInfoSettingsFragment.class);
    }
}
