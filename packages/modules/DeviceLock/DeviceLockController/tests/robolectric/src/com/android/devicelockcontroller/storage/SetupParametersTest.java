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

package com.android.devicelockcontroller.storage;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class SetupParametersTest extends AbstractSetupParametersTestBase {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void createPrefs_shouldPopulatePreferences() {
        final Bundle bundle = createParamsBundle();
        SetupParameters.createPrefs(mContext, bundle);

        assertThat(SetupParameters.getKioskPackage(mContext)).isEqualTo(KIOSK_PACKAGE);
        assertThat(SetupParameters.getOutgoingCallsDisabled(mContext)).isTrue();
        assertThat(SetupParameters.isNotificationsInLockTaskModeEnabled(mContext)).isTrue();

        final List<String> expectedKioskAllowlist = new ArrayList<>();
        expectedKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_0);
        expectedKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_1);
        assertThat(SetupParameters.getKioskAllowlist(mContext))
                .containsExactlyElementsIn(expectedKioskAllowlist);
        assertThat(SetupParameters.getProvisioningType(mContext)).isEqualTo(PROVISIONING_TYPE);
        assertThat(SetupParameters.isProvisionMandatory(mContext)).isEqualTo(MANDATORY_PROVISION);
        assertThat(SetupParameters.getKioskAppProviderName(mContext)).isEqualTo(
                KIOSK_APP_PROVIDER_NAME);
        assertThat(SetupParameters.isInstallingFromUnknownSourcesDisallowed(mContext)).isTrue();
        assertThat(SetupParameters.getTermsAndConditionsUrl(mContext))
                .isEqualTo(TERMS_AND_CONDITIONS_URL);
        assertThat(SetupParameters.getSupportUrl(mContext)).isEqualTo(SUPPORT_URL);
    }

    @Test
    public void createPrefs_whenCalledTwice_doesNotOverwrite() {
        final Bundle bundle = createParamsBundle();
        SetupParameters.createPrefs(mContext, bundle);

        assertThat(SetupParameters.getKioskPackage(mContext)).isEqualTo(KIOSK_PACKAGE);

        final Bundle newBundle = createParamsOverrideBundle();
        SetupParameters.createPrefs(mContext, newBundle);
        assertThat(SetupParameters.getKioskPackage(mContext)).isEqualTo(KIOSK_PACKAGE);
    }
}
