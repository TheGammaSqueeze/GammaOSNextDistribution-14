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

package com.android.car.settings.bluetooth;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.testutils.EnterpriseTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class BluetoothUtilsTest {

    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;
    private static final String SYSTEM_UI_PACKAGE_NAME = "com.package.systemui";
    private static final String SYSTEM_UI_COMPONENT_NAME = "com.package.systemui/testclass";
    private static final String ALLOWED_PACKAGE_NAME = "com.allowed.package";
    private static final String DISALLOWED_PACKAGE_NAME = "not.real.package";
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

    @Mock
    private UserManager mMockUserManager;
    @Mock
    private Resources mMockResources;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getString(anyInt())).thenReturn(SYSTEM_UI_COMPONENT_NAME);

        String[] allowedPackages = new String[1];
        allowedPackages[0] = ALLOWED_PACKAGE_NAME;
        when(mMockResources.getStringArray(anyInt())).thenReturn(allowedPackages);
    }

    @Test
    public void testGetAvailabilityStatusRestricted_unrestricted_available() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);

        assertThat(BluetoothUtils.getAvailabilityStatusRestricted(mContext)).isEqualTo(AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatusRestricted_restrictedByUm_disabled() {
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        assertThat(BluetoothUtils.getAvailabilityStatusRestricted(mContext))
                .isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatusRestricted_restrictedByDpm_viewing() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        assertThat(BluetoothUtils.getAvailabilityStatusRestricted(mContext))
                .isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatusRestricted_restrictedByBothUmAndDpm_disabled() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        EnterpriseTestUtils.mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        assertThat(BluetoothUtils.getAvailabilityStatusRestricted(mContext))
                .isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void isSystemCallingPackage_shouldEnableBluetoothScanning() {
        String settingsPackage = mContext.getPackageName();

        assertThat(BluetoothUtils.shouldEnableBTScanning(mContext, settingsPackage))
                .isEqualTo(true);
        assertThat(BluetoothUtils.shouldEnableBTScanning(mContext, SYSTEM_UI_PACKAGE_NAME))
                .isEqualTo(true);
    }

    @Test
    public void isAllowedCallingPackage_shouldEnableBluetoothScanning() {
        assertThat(BluetoothUtils.shouldEnableBTScanning(mContext, ALLOWED_PACKAGE_NAME))
                .isEqualTo(true);
    }

    @Test
    public void isNotAllowedCallingPackage_shouldNotEnableBluetoothScanning() {
        assertThat(BluetoothUtils.shouldEnableBTScanning(mContext, DISALLOWED_PACKAGE_NAME))
                .isEqualTo(false);
    }
}
