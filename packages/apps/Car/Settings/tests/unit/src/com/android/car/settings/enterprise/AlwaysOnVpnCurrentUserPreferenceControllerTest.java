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

package com.android.car.settings.enterprise;


import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.net.VpnManager;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class AlwaysOnVpnCurrentUserPreferenceControllerTest extends
        BaseEnterprisePrivacyPreferenceControllerTestCase {
    private AlwaysOnVpnCurrentUserPreferenceController mAlwaysOnVpnCurrentUserPreferenceController;
    private Preference mPreference;

    @Mock
    private VpnManager mVpnManager;

    @Before
    public void setUp() {
        when(mSpiedContext.getSystemService(VpnManager.class)).thenReturn(mVpnManager);
        mockHasDeviceAdminFeature();
        mPreference = new Preference(mSpiedContext);
        mAlwaysOnVpnCurrentUserPreferenceController =
                new AlwaysOnVpnCurrentUserPreferenceController(
                        mSpiedContext, mPreferenceKey, mFragmentController, mUxRestrictions);
    }

    @Test
    public void testAlwaysOnVpnPackageSet_preferenceEnabled() {
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn("com.test");

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void testAlwaysOnVpnPackageSet_preferenceEnabled_zoneWrite() {
        mAlwaysOnVpnCurrentUserPreferenceController.setAvailabilityStatusForZone("write");
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn("com.test");

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void testAlwaysOnVpnPackageSet_preferenceEnabled_zoneRead() {
        mAlwaysOnVpnCurrentUserPreferenceController.setAvailabilityStatusForZone("read");
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn("com.test");

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testAlwaysOnVpnPackageSet_preferenceEnabled_zoneHidden() {
        mAlwaysOnVpnCurrentUserPreferenceController.setAvailabilityStatusForZone("hidden");
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn("com.test");

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testAlwaysOnVpnPackageNotSet_preferenceDisabled() {
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn(null);

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testAlwaysOnVpnPackageNotSet_preferenceDisabled_zoneWrite() {
        mAlwaysOnVpnCurrentUserPreferenceController.setAvailabilityStatusForZone("write");
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn(null);

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testAlwaysOnVpnPackageNotSet_preferenceDisabled_zoneRead() {
        mAlwaysOnVpnCurrentUserPreferenceController.setAvailabilityStatusForZone("read");
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn(null);

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testAlwaysOnVpnPackageNotSet_preferenceDisabled_zoneHidden() {
        mAlwaysOnVpnCurrentUserPreferenceController.setAvailabilityStatusForZone("hidden");
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn(null);

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mAlwaysOnVpnCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testNoCompMode_showsRegularTitle() {
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn("com.test");

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        assertThat(mPreference.getTitle()).isEqualTo(mRealContext.getResources()
                .getString(R.string.enterprise_privacy_always_on_vpn_device));
    }

    @Test
    public void testCompMode_showsPersonalProfileTitle() {
        mockCompMode();
        when(mVpnManager.getAlwaysOnVpnPackageForUser(anyInt())).thenReturn("com.test");

        mAlwaysOnVpnCurrentUserPreferenceController.updateState(mPreference);

        assertThat(mPreference.getTitle()).isEqualTo(mRealContext.getResources()
                .getString(R.string.enterprise_privacy_always_on_vpn_personal));
    }
}
