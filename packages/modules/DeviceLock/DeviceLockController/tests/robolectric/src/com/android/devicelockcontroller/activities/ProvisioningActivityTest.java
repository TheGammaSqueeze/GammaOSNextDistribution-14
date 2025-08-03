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

import static com.android.devicelockcontroller.activities.ProvisioningActivity.EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START;
import static com.android.devicelockcontroller.activities.ProvisioningActivity.EXTRA_SHOW_PROVISION_FAILED_UI_ON_START;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.common.DeviceLockConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public final class ProvisioningActivityTest {

    @Test
    public void noExtraSet_showDevicePoliciesFragment() {
        ProvisioningActivity activity = Robolectric.buildActivity(
                ProvisioningActivity.class).create().get();

        shadowOf(Looper.getMainLooper()).idle();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        assertThat(fragment).isInstanceOf(DevicePoliciesFragment.class);
    }

    @Test
    public void
            withCriticalFailedUIExtra_setMandatoryFailedProvisionProgressAndShowProgressFragment() {
        ProvisioningActivity activity = Robolectric.buildActivity(ProvisioningActivity.class,
                new Intent().putExtra(EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START,
                        true)).setup().get();

        ShadowLooper.runUiThreadTasks();

        ProvisioningProgress actual = new ViewModelProvider(activity).get(
                ProvisioningProgressViewModel.class).getProvisioningProgressLiveData().getValue();
        assertThat(actual).isEqualTo(ProvisioningProgress.getMandatoryProvisioningFailedProgress(
                DeviceLockConstants.ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED));

        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        assertThat(fragment).isInstanceOf(ProgressFragment.class);
    }

    @Test
    public void withNonCriticalUIExtra_setNonMandatoryFailedProgressAndShowProgressFragment() {
        ProvisioningActivity activity = Robolectric.buildActivity(ProvisioningActivity.class,
                new Intent().putExtra(EXTRA_SHOW_PROVISION_FAILED_UI_ON_START,
                        true)).setup().get();

        ShadowLooper.runUiThreadTasks();

        ProvisioningProgress actual = new ViewModelProvider(activity).get(
                ProvisioningProgressViewModel.class).getProvisioningProgressLiveData().getValue();
        assertThat(actual).isEqualTo(ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON));
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        assertThat(fragment).isInstanceOf(ProgressFragment.class);
    }
}
