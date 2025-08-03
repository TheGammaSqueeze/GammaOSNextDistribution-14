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

package com.android.devicelockcontroller.storage;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class SetupParametersServiceTest extends AbstractSetupParametersTestBase {
    private ISetupParametersService mISetupParametersService;

    @Before
    public void setUp() {
        final ServiceController<SetupParametersService> setupParametersServiceController =
                Robolectric.buildService(SetupParametersService.class);
        final SetupParametersService setupParametersService =
                setupParametersServiceController.create().get();
        mISetupParametersService =
                (ISetupParametersService) setupParametersService.onBind(new Intent());
    }

    @Test
    public void createPrefs_shouldPopulatePrefs() throws RemoteException {
        final Bundle bundle = createParamsBundle();
        mISetupParametersService.createPrefs(bundle);

        assertThat(mISetupParametersService.getKioskPackage()).isEqualTo(KIOSK_PACKAGE);
        assertThat(mISetupParametersService.getOutgoingCallsDisabled()).isTrue();
        assertThat(mISetupParametersService.isNotificationsInLockTaskModeEnabled()).isTrue();

        List<String> expectedKioskAllowlist = new ArrayList<>();
        expectedKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_0);
        expectedKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_1);
        assertThat(mISetupParametersService.getKioskAllowlist())
                .containsExactlyElementsIn(expectedKioskAllowlist);

        assertThat(mISetupParametersService.getTermsAndConditionsUrl())
                .isEqualTo(TERMS_AND_CONDITIONS_URL);
        assertThat(mISetupParametersService.getSupportUrl()).isEqualTo(SUPPORT_URL);
    }

    @Test
    public void createPrefs_whenCalledTwice_doesNotOverwrite() throws RemoteException {
        final Bundle bundle = createParamsBundle();
        mISetupParametersService.createPrefs(bundle);

        assertThat(mISetupParametersService.getKioskPackage()).isEqualTo(KIOSK_PACKAGE);

        final Bundle newBundle = createParamsOverrideBundle();
        mISetupParametersService.createPrefs(newBundle);
        assertThat(mISetupParametersService.getKioskPackage()).isEqualTo(KIOSK_PACKAGE);
    }
}
