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

import static org.mockito.Mockito.when;

import android.net.ConnectivityManager;
import android.net.ProxyInfo;

import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class GlobalHttpProxyPreferenceControllerTest extends
        BaseEnterprisePrivacyPreferenceControllerTestCase {
    private GlobalHttpProxyPreferenceController mGlobalHttpProxyPreferenceController;

    @Mock
    private ConnectivityManager mConnectivityManager;

    @Before
    public void setUp() {
        when(mSpiedContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mConnectivityManager);
        mGlobalHttpProxyPreferenceController = new GlobalHttpProxyPreferenceController(
                mSpiedContext, mPreferenceKey, mFragmentController, mUxRestrictions);
    }

    @Test
    public void testGlobalProxyNotSet_disablesPreference() {
        when(mConnectivityManager.getGlobalProxy()).thenReturn(
                ProxyInfo.buildDirectProxy("test.com", 43));

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void testGlobalProxyNotSet_disablesPreference_zoneWrite() {
        mGlobalHttpProxyPreferenceController.setAvailabilityStatusForZone("write");
        when(mConnectivityManager.getGlobalProxy()).thenReturn(
                ProxyInfo.buildDirectProxy("test.com", 43));

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void testGlobalProxyNotSet_disablesPreference_zoneRead() {
        mGlobalHttpProxyPreferenceController.setAvailabilityStatusForZone("read");
        when(mConnectivityManager.getGlobalProxy()).thenReturn(
                ProxyInfo.buildDirectProxy("test.com", 43));

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGlobalProxyNotSet_disablesPreference_zoneHidden() {
        mGlobalHttpProxyPreferenceController.setAvailabilityStatusForZone("hidden");
        when(mConnectivityManager.getGlobalProxy()).thenReturn(
                ProxyInfo.buildDirectProxy("test.com", 43));

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGlobalProxySet_enablesPreference() {
        when(mConnectivityManager.getGlobalProxy()).thenReturn(null);

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGlobalProxySet_enablesPreference_zoneWrite() {
        mGlobalHttpProxyPreferenceController.setAvailabilityStatusForZone("write");
        when(mConnectivityManager.getGlobalProxy()).thenReturn(null);

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGlobalProxySet_enablesPreference_zoneRead() {
        mGlobalHttpProxyPreferenceController.setAvailabilityStatusForZone("read");
        when(mConnectivityManager.getGlobalProxy()).thenReturn(null);

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGlobalProxySet_enablesPreference_zoneHidden() {
        mGlobalHttpProxyPreferenceController.setAvailabilityStatusForZone("hidden");
        when(mConnectivityManager.getGlobalProxy()).thenReturn(null);

        PreferenceControllerTestUtil.assertAvailability(
                mGlobalHttpProxyPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }
}
