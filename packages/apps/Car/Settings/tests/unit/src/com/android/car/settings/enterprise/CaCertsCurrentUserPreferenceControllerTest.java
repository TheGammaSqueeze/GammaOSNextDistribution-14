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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.net.ConnectivityManager;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;

public final class CaCertsCurrentUserPreferenceControllerTest extends
        BaseEnterprisePrivacyPreferenceControllerTestCase {
    private CaCertsCurrentUserPreferenceController mCaCertsCurrentUserPreferenceController;
    private Preference mPreference;

    @Mock
    private ConnectivityManager mConnectivityManager;

    @Before
    public void setUp() {
        when(mSpiedContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mConnectivityManager);
        mPreference = new Preference(mSpiedContext);
        mCaCertsCurrentUserPreferenceController = new CaCertsCurrentUserPreferenceController(
                mSpiedContext, mPreferenceKey, mFragmentController, mUxRestrictions);
    }

    @Test
    public void testNoInstalledCaCerts_preferenceDisabled() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(Collections.emptyList());

        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertThat(mPreference.getSummary()).isEqualTo(
                mRealContext.getResources().getQuantityString(
                        R.plurals.enterprise_privacy_number_ca_certs, 0, 0));
    }

    @Test
    public void testNoInstalledCaCerts_preferenceDisabled_zoneWrite() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(Collections.emptyList());

        mCaCertsCurrentUserPreferenceController.setAvailabilityStatusForZone("write");
        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertThat(mPreference.getSummary()).isEqualTo(
                mRealContext.getResources().getQuantityString(
                        R.plurals.enterprise_privacy_number_ca_certs, 0, 0));
    }

    @Test
    public void testNoInstalledCaCerts_preferenceDisabled_zoneRead() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(Collections.emptyList());

        mCaCertsCurrentUserPreferenceController.setAvailabilityStatusForZone("read");
        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertThat(mPreference.getSummary()).isEqualTo(
                mRealContext.getResources().getQuantityString(
                        R.plurals.enterprise_privacy_number_ca_certs, 0, 0));
    }

    @Test
    public void testNoInstalledCaCerts_preferenceDisabled_zoneHidden() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(Collections.emptyList());

        mCaCertsCurrentUserPreferenceController.setAvailabilityStatusForZone("hidden");
        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertThat(mPreference.getSummary()).isEqualTo(
                mRealContext.getResources().getQuantityString(
                        R.plurals.enterprise_privacy_number_ca_certs, 0, 0));
    }

    @Test
    public void testInstalledCaCerts_preferenceEnabled() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(ImmutableList.of("cert1", "cert2"));

        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(), AVAILABLE);
        assertThat(mPreference.getSummary()).isEqualTo(mRealContext.getResources()
                .getQuantityString(R.plurals.enterprise_privacy_number_ca_certs, 2, 2));
    }

    @Test
    public void testInstalledCaCerts_preferenceEnabled_zoneWrite() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(ImmutableList.of("cert1", "cert2"));

        mCaCertsCurrentUserPreferenceController.setAvailabilityStatusForZone("write");
        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(), AVAILABLE);
        assertThat(mPreference.getSummary()).isEqualTo(mRealContext.getResources()
                .getQuantityString(R.plurals.enterprise_privacy_number_ca_certs, 2, 2));
    }

    @Test
    public void testInstalledCaCerts_preferenceEnabled_zoneRead() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(ImmutableList.of("cert1", "cert2"));

        mCaCertsCurrentUserPreferenceController.setAvailabilityStatusForZone("read");
        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.getSummary()).isEqualTo(mRealContext.getResources()
                .getQuantityString(R.plurals.enterprise_privacy_number_ca_certs, 2, 2));
    }

    @Test
    public void testInstalledCaCerts_preferenceEnabled_zoneHidden() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(ImmutableList.of("cert1", "cert2"));

        mCaCertsCurrentUserPreferenceController.setAvailabilityStatusForZone("hidden");
        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        PreferenceControllerTestUtil.assertAvailability(
                mCaCertsCurrentUserPreferenceController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
        assertThat(mPreference.getSummary()).isEqualTo(mRealContext.getResources()
                .getQuantityString(R.plurals.enterprise_privacy_number_ca_certs, 2, 2));
    }

    @Test
    public void testNoCompMode_showsRegularTitle() {
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(ImmutableList.of("cert1", "cert2"));

        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        assertThat(mPreference.getTitle()).isEqualTo(mRealContext.getResources()
                .getString(R.string.enterprise_privacy_ca_certs_device));
    }

    @Test
    public void testCompMode_showsPersonalProfileTitle() {
        mockCompMode();
        when(mDpm.getOwnerInstalledCaCerts(any())).thenReturn(ImmutableList.of("cert1", "cert2"));

        mCaCertsCurrentUserPreferenceController.updateState(mPreference);

        assertThat(mPreference.getTitle()).isEqualTo(mRealContext.getResources()
                .getString(R.string.enterprise_privacy_ca_certs_personal));
    }
}
