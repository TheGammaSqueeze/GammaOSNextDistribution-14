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

package com.android.ondevicepersonalization.services;

import static com.android.ondevicepersonalization.services.Flags.APP_REQUEST_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.Flags.DEFAULT_CALLER_APP_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.Flags.DEFAULT_CLIENT_ERROR_LOGGING_ENABLED;
import static com.android.ondevicepersonalization.services.Flags.DEFAULT_ISOLATED_SERVICE_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.Flags.DEFAULT_OUTPUT_DATA_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.Flags.DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
import static com.android.ondevicepersonalization.services.Flags.DEFAULT_TRUSTED_PARTNER_APPS_LIST;
import static com.android.ondevicepersonalization.services.Flags.DOWNLOAD_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.Flags.ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.Flags.EXAMPLE_STORE_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.Flags.GLOBAL_KILL_SWITCH;
import static com.android.ondevicepersonalization.services.Flags.IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
import static com.android.ondevicepersonalization.services.Flags.PERSONALIZATION_STATUS_OVERRIDE_VALUE;
import static com.android.ondevicepersonalization.services.Flags.RENDER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.Flags.WEB_TRIGGER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.Flags.WEB_VIEW_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_CALLER_APP_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_GLOBAL_KILL_SWITCH;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ISOLATED_SERVICE_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ISOLATED_SERVICE_DEBUGGING_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ODP_BACKGROUND_JOBS_LOGGING_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ODP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ODP_ENABLE_CLIENT_ERROR_LOGGING;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_OUTPUT_DATA_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_RENDER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_TRUSTED_PARTNER_APPS_LIST;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link com.android.ondevicepersonalization.service.PhFlags} */
@RunWith(AndroidJUnit4.class)
public class PhFlagsTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    /**
     * Get necessary permissions to access Setting.Config API and set up context
     */
    @Before
    public void setUpContext() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidStableFlags() {
        FlagsFactory.getFlags().getStableFlag("INVALID_FLAG_NAME");
    }

    @Test
    public void testValidStableFlags() {
        Object isSipFeatureEnabled = FlagsFactory.getFlags()
                .getStableFlag(KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED);

        assertThat(isSipFeatureEnabled).isNotNull();
    }

    @Test
    public void testGetGlobalKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(GLOBAL_KILL_SWITCH),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().getGlobalKillSwitch()).isEqualTo(GLOBAL_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getGlobalKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testIsPersonalizationStatusOverrideEnabled() {
        PhFlagsTestUtil.disableGlobalKillSwitch();
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                Boolean.toString(ENABLE_PERSONALIZATION_STATUS_OVERRIDE),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().isPersonalizationStatusOverrideEnabled()).isEqualTo(
                ENABLE_PERSONALIZATION_STATUS_OVERRIDE);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isPersonalizationStatusOverrideEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetPersonalizationStatusOverrideValue() {
        PhFlagsTestUtil.disableGlobalKillSwitch();
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                Boolean.toString(PERSONALIZATION_STATUS_OVERRIDE_VALUE),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().getPersonalizationStatusOverrideValue()).isEqualTo(
                PERSONALIZATION_STATUS_OVERRIDE_VALUE);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getPersonalizationStatusOverrideValue()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testWebTriggerFlowDeadlineSeconds() {
        assertThat(FlagsFactory.getFlags().getWebTriggerFlowDeadlineSeconds())
                .isEqualTo(WEB_TRIGGER_FLOW_DEADLINE_SECONDS);

        final int test_deadline = 10;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS,
                String.valueOf(test_deadline),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getWebTriggerFlowDeadlineSeconds())
                .isEqualTo(test_deadline);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS,
                String.valueOf(WEB_TRIGGER_FLOW_DEADLINE_SECONDS),
                /* makeDefault */ false);
    }

    @Test
    public void testWebViewFlowDeadlineSeconds() {
        assertThat(FlagsFactory.getFlags().getWebViewFlowDeadlineSeconds())
                .isEqualTo(WEB_VIEW_FLOW_DEADLINE_SECONDS);

        final int test_deadline = 10;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS,
                String.valueOf(test_deadline),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getWebViewFlowDeadlineSeconds())
                .isEqualTo(test_deadline);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS,
                String.valueOf(WEB_VIEW_FLOW_DEADLINE_SECONDS),
                /* makeDefault */ false);
    }

    @Test
    public void testRenderFlowDeadlineSeconds() {
        assertThat(FlagsFactory.getFlags().getRenderFlowDeadlineSeconds())
                .isEqualTo(RENDER_FLOW_DEADLINE_SECONDS);

        final int test_deadline = 10;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_RENDER_FLOW_DEADLINE_SECONDS,
                String.valueOf(test_deadline),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getRenderFlowDeadlineSeconds())
                .isEqualTo(test_deadline);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_RENDER_FLOW_DEADLINE_SECONDS,
                String.valueOf(RENDER_FLOW_DEADLINE_SECONDS),
                /* makeDefault */ false);
    }

    @Test
    public void testAppRequestFlowDeadlineSeconds() {
        assertThat(FlagsFactory.getFlags().getAppRequestFlowDeadlineSeconds())
                .isEqualTo(APP_REQUEST_FLOW_DEADLINE_SECONDS);

        final int test_deadline = 10;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS,
                String.valueOf(test_deadline),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getAppRequestFlowDeadlineSeconds())
                .isEqualTo(test_deadline);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS,
                String.valueOf(APP_REQUEST_FLOW_DEADLINE_SECONDS),
                /* makeDefault */ false);
    }

    @Test
    public void testExampleStoreFlowDeadlineSeconds() {
        assertThat(FlagsFactory.getFlags().getExampleStoreFlowDeadlineSeconds())
                .isEqualTo(EXAMPLE_STORE_FLOW_DEADLINE_SECONDS);

        final int test_deadline = 10;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS,
                String.valueOf(test_deadline),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getExampleStoreFlowDeadlineSeconds())
                .isEqualTo(test_deadline);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS,
                String.valueOf(EXAMPLE_STORE_FLOW_DEADLINE_SECONDS),
                /* makeDefault */ false);
    }

    @Test
    public void testDownloadFlowDeadlineSeconds() {
        assertThat(FlagsFactory.getFlags().getExampleStoreFlowDeadlineSeconds())
                .isEqualTo(DOWNLOAD_FLOW_DEADLINE_SECONDS);

        final int test_deadline = 10;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS,
                String.valueOf(test_deadline),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getDownloadFlowDeadlineSeconds())
                .isEqualTo(test_deadline);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS,
                String.valueOf(DOWNLOAD_FLOW_DEADLINE_SECONDS),
                /* makeDefault */ false);
    }

    @Test
    public void testGetTrustedPartnerAppsList() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_TRUSTED_PARTNER_APPS_LIST,
                DEFAULT_TRUSTED_PARTNER_APPS_LIST,
                /* makeDefault */ false);

        if (SdkLevel.isAtLeastU()) {
            assertThat(FlagsFactory.getFlags().getTrustedPartnerAppsList())
                    .isEqualTo(DEFAULT_TRUSTED_PARTNER_APPS_LIST);
        } else {
            assertThat(FlagsFactory.getFlags().getTrustedPartnerAppsList())
                    .isEqualTo("");
        }

        final String testTrustedPartnerAppsList =
                "trusted_test_app_1, trusted_test_app_2, trusted_test_app_3";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_TRUSTED_PARTNER_APPS_LIST,
                testTrustedPartnerAppsList,
                /* makeDefault */ false);

        if (SdkLevel.isAtLeastU()) {
            assertThat(FlagsFactory.getFlags().getTrustedPartnerAppsList())
                    .isEqualTo(testTrustedPartnerAppsList);
        } else {
            assertThat(FlagsFactory.getFlags().getTrustedPartnerAppsList())
                    .isEqualTo("");
        }
    }

    @Test
    public void testSharedIsolatedProcessFeature() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                Boolean.toString(DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED),
                /* makeDefault */ false);

        if (SdkLevel.isAtLeastU()) {
            assertThat(FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled())
                    .isEqualTo(DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED);
        } else {
            assertThat(FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled())
                    .isFalse();
        }

        final boolean testIsolatedProcessFeatureEnabled =
                !DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                Boolean.toString(testIsolatedProcessFeatureEnabled),
                /* makeDefault */ false);

        if (SdkLevel.isAtLeastU()) {
            assertThat(FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled())
                    .isEqualTo(testIsolatedProcessFeatureEnabled);
        } else {
            assertThat(FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled())
                    .isFalse();
        }
    }

    @Test
    public void testGetCallerAppAllowList() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_CALLER_APP_ALLOW_LIST,
                DEFAULT_CALLER_APP_ALLOW_LIST,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getCallerAppAllowList())
                .isEqualTo(DEFAULT_CALLER_APP_ALLOW_LIST);

        final String testCallerAppAllowList =
                "com.example.odpclient";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_CALLER_APP_ALLOW_LIST,
                testCallerAppAllowList,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getCallerAppAllowList())
                .isEqualTo(testCallerAppAllowList);
    }

    @Test
    public void testIsolatedServiceDebuggingEnabled() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ISOLATED_SERVICE_DEBUGGING_ENABLED,
                Boolean.toString(false),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().isIsolatedServiceDebuggingEnabled())
                .isEqualTo(false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ISOLATED_SERVICE_DEBUGGING_ENABLED,
                Boolean.toString(true),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().isIsolatedServiceDebuggingEnabled())
                .isEqualTo(true);
    }

    @Test
    public void testIsArtImageLoadingOptimizationEnabled() {
        assertThat(FlagsFactory.getFlags().isArtImageLoadingOptimizationEnabled())
                .isEqualTo(IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED);

        boolean testValue = !IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED,
                String.valueOf(testValue),
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().isArtImageLoadingOptimizationEnabled())
                .isEqualTo(testValue);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED,
                String.valueOf(IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED),
                /* makeDefault */ false);
    }

    @Test
    public void testGetIsolatedServiceAllowList() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ISOLATED_SERVICE_ALLOW_LIST,
                DEFAULT_ISOLATED_SERVICE_ALLOW_LIST,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getIsolatedServiceAllowList())
                .isEqualTo(DEFAULT_ISOLATED_SERVICE_ALLOW_LIST);

        final String testIsolatedServiceAllowList =
                "com.example.odpsamplenetwork";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ISOLATED_SERVICE_ALLOW_LIST,
                testIsolatedServiceAllowList,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getIsolatedServiceAllowList())
                .isEqualTo(testIsolatedServiceAllowList);
    }

    @Test
    public void testGetOutputDataAllowList() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_OUTPUT_DATA_ALLOW_LIST,
                DEFAULT_OUTPUT_DATA_ALLOW_LIST,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getOutputDataAllowList())
                .isEqualTo(DEFAULT_OUTPUT_DATA_ALLOW_LIST);

        final String testOutputDataAllowList =
                "com.example.odpclient;com.example.odpsamplenetwork";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_OUTPUT_DATA_ALLOW_LIST,
                testOutputDataAllowList,
                /* makeDefault */ false);

        assertThat(FlagsFactory.getFlags().getOutputDataAllowList())
                .isEqualTo(testOutputDataAllowList);
    }

    @Test
    public void testGetEnableClientErrorLogging() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ODP_ENABLE_CLIENT_ERROR_LOGGING,
                Boolean.toString(DEFAULT_CLIENT_ERROR_LOGGING_ENABLED),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getEnableClientErrorLogging())
                .isEqualTo(DEFAULT_CLIENT_ERROR_LOGGING_ENABLED);

        // Overriding the value in device config.
        boolean overrideEnable = !DEFAULT_CLIENT_ERROR_LOGGING_ENABLED;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ODP_ENABLE_CLIENT_ERROR_LOGGING,
                Boolean.toString(overrideEnable),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getEnableClientErrorLogging())
                .isEqualTo(overrideEnable);
    }

    @Test
    public void testGetBackgroundJobsLoggingEnabled() {
        // read a stable flag value
        boolean stableValue = FlagsFactory.getFlags().getBackgroundJobsLoggingEnabled();

        // override the value in device config.
        boolean overrideEnabled = !stableValue;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ODP_BACKGROUND_JOBS_LOGGING_ENABLED,
                Boolean.toString(overrideEnabled),
                /* makeDefault= */ false);

        // the flag value remains stable
        assertThat(FlagsFactory.getFlags().getBackgroundJobsLoggingEnabled())
                .isEqualTo(stableValue);
    }

    @Test
    public void testGetBackgroundJobSamplingLoggingRate() {
        int currentValue = 10;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ODP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE,
                Integer.toString(currentValue),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getBackgroundJobSamplingLoggingRate())
                .isEqualTo(currentValue);

        // Override the value in device config.
        int overrideRate = 1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ODP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE,
                Integer.toString(overrideRate),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getBackgroundJobSamplingLoggingRate())
                .isEqualTo(overrideRate);
    }
}
