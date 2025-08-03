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

package com.android.cts.managedprofile.owner.app;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.PackagePolicy;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ManagedProfileOwnerAppTest {

    private Context mContext;
    protected DevicePolicyManager mDevicePolicyManager;

    public static class BasicAdminReceiver extends DeviceAdminReceiver {
    }

    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            BasicAdminReceiver.class.getPackage().getName(), BasicAdminReceiver.class.getName());


    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();

        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);

        assertThat(mDevicePolicyManager).isNotNull();
        assertThat(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT)).isTrue();
        assertThat(mDevicePolicyManager.isProfileOwnerApp(
                ADMIN_RECEIVER_COMPONENT.getPackageName())).isTrue();
        assertThat(mDevicePolicyManager.isManagedProfile(ADMIN_RECEIVER_COMPONENT)).isTrue();
    }

    @Test
    public void testSetDisallowWorkContactsAccessPolicy() {
        PackagePolicy policy = new PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST);
        mDevicePolicyManager.setManagedProfileContactsAccessPolicy(policy);
        assertThat(mDevicePolicyManager.getManagedProfileContactsAccessPolicy())
                .isEqualTo(policy);
    }

    @Test
    public void testEnableWorkContactsAccess() {
        PackagePolicy policy = new PackagePolicy(PackagePolicy.PACKAGE_POLICY_BLOCKLIST);
        mDevicePolicyManager.setManagedProfileContactsAccessPolicy(policy);
        assertThat(mDevicePolicyManager.getManagedProfileContactsAccessPolicy())
                .isEqualTo(policy);
    }
}
