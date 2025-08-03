/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.view.WindowInsets;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;

import com.android.devicelockcontroller.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class LandingActivityTest {

    @Test
    public void landingActivity_setsProvisionInfoFragment() {
        Intent intent = new Intent();
        intent.setAction(ACTION_START_DEVICE_FINANCING_PROVISIONING);
        LandingActivity activity = Robolectric.buildActivity(LandingActivity.class,
                intent).setup().get();
        FragmentContainerView fragmentContainerView = activity.findViewById(
                R.id.fragment_container);

        assertThat((Fragment) fragmentContainerView.getFragment()).isInstanceOf(
                ProvisionInfoFragment.class);
    }

    @Test
    public void landingActivity_hideSystemBar() {
        Intent intent = new Intent();
        intent.setAction(ACTION_START_DEVICE_FINANCING_PROVISIONING);
        LandingActivity activity = Robolectric.buildActivity(LandingActivity.class,
                intent).setup().get();
        assertThat(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(
                WindowInsets.Type.systemBars())).isFalse();
    }
}
