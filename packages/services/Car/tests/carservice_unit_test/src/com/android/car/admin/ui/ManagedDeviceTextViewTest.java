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

package com.android.car.admin.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;


/** Unit tests for {@link ManagedDeviceTextView}. */
@RunWith(MockitoJUnitRunner.class)
public final class ManagedDeviceTextViewTest {

    @Rule
    public ActivityScenarioRule<CarAdminUiTestActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(CarAdminUiTestActivity.class);

    private CarAdminUiTestActivity mCarAdminUiTestActivity;
    private PackageManager mSpyPackageManager;
    private DevicePolicyManager mMockDevicePolicyManager;

    @Before
    public void setup() {
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            mCarAdminUiTestActivity = (CarAdminUiTestActivity) activity;
            mSpyPackageManager = mCarAdminUiTestActivity.mSpyPackageManager;
            mMockDevicePolicyManager = mCarAdminUiTestActivity.mMockDevicePolicyManager;
        });
    }

    @Test
    public void deviceAdminFeatureMissing_visibilityGone() {
        mockHasDeviceAdminFeature(false);

        ManagedDeviceTextView managedDeviceTextView = new ManagedDeviceTextView(
                mCarAdminUiTestActivity, null);

        assertThat(managedDeviceTextView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void deviceNotManaged_visibilityGone() {
        mockHasDeviceAdminFeature(true);
        mockIsDeviceManaged(false);

        ManagedDeviceTextView managedDeviceTextView = new ManagedDeviceTextView(
                mCarAdminUiTestActivity, null);

        assertThat(managedDeviceTextView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void deviceManagedByOrg() {
        mockHasDeviceAdminFeature(true);
        mockIsDeviceManaged(true);
        when(mMockDevicePolicyManager.getDeviceOwnerOrganizationName()).thenReturn("My org");

        ManagedDeviceTextView managedDeviceTextView = new ManagedDeviceTextView(
                mCarAdminUiTestActivity, null);

        assertThat(managedDeviceTextView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(managedDeviceTextView.getText().toString())
                .isEqualTo(managedDeviceTextView.getResources().getString(
                        R.string.car_admin_ui_managed_device_message_by_org, "My org"));
    }

    @Test
    public void deviceManaged_orgNameAbsent_genericMessage() {
        mockHasDeviceAdminFeature(true);
        mockIsDeviceManaged(true);
        when(mMockDevicePolicyManager.getDeviceOwnerOrganizationName()).thenReturn(null);

        ManagedDeviceTextView managedDeviceTextView = new ManagedDeviceTextView(
                mCarAdminUiTestActivity, null);

        assertThat(managedDeviceTextView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(managedDeviceTextView.getText().toString())
                .isEqualTo(managedDeviceTextView.getResources().getString(
                        R.string.car_admin_ui_managed_device_message_generic));
    }

    @Test
    public void deviceManaged_dpmFailure_genericMessage() {
        mockHasDeviceAdminFeature(true);
        mockIsDeviceManaged(true);
        when(mMockDevicePolicyManager.getDeviceOwnerOrganizationName())
                .thenThrow(new RuntimeException("failure"));

        ManagedDeviceTextView managedDeviceTextView = new ManagedDeviceTextView(
                mCarAdminUiTestActivity, null);

        assertThat(managedDeviceTextView.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(managedDeviceTextView.getText().toString())
                .isEqualTo(managedDeviceTextView.getResources().getString(
                        R.string.car_admin_ui_managed_device_message_generic));
    }

    private void mockHasDeviceAdminFeature(boolean hasFeature) {
        doReturn(hasFeature).when(mSpyPackageManager)
                .hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
    }

    private void mockIsDeviceManaged(boolean isManaged) {
        doReturn(isManaged).when(mMockDevicePolicyManager).isDeviceManaged();
    }
}

