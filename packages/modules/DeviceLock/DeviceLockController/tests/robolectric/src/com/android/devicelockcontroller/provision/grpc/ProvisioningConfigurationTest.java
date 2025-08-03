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

package com.android.devicelockcontroller.provision.grpc;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class ProvisioningConfigurationTest {
    @Test
    public void configurationToBundle_withOutgoingCallsDisabled_shouldStoreCorrectState() {
        final Bundle configurationBundle =
                createProvisioningConfiguration(/* enableOutgoingCalls= */ false).toBundle();
        assertThat(configurationBundle.getBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS)).isTrue();
    }

    @Test
    public void configurationToBundle_withOutgoingCallsEnabled_shouldStoreCorrectState() {
        final Bundle configurationBundle =
                createProvisioningConfiguration(/* enableOutgoingCalls= */ true).toBundle();
        assertThat(configurationBundle.getBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS)).isFalse();
    }

    private static ProvisioningConfiguration createProvisioningConfiguration(
            boolean enableOutgoingCalls) {
        return new ProvisioningConfiguration(
                /* kioskAppProviderName= */ "test_provider",
                /* kioskAppPackageName= */ "test_package",
                /* kioskAppAllowlistPackages= */ List.of("test_allowed_app1", "test_allowed_app2"),
                enableOutgoingCalls,
                /* kioskAppEnableEnableNotifications= */ true,
                /* disallowInstallingFromUnknownSources= */ false,
                /* termsAndConditionsUrl= */ "test_terms_and_configurations_url",
                /* supportUrl= */ "test_support_url"
        );
    }
}
