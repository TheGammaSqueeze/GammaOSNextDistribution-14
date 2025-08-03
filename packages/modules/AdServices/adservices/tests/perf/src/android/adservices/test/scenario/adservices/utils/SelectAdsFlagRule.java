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

package android.adservices.test.scenario.adservices.utils;

import android.Manifest;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class SelectAdsFlagRule implements TestRule {
    private Boolean mUsePublicCoordinator = false;

    public SelectAdsFlagRule() {}

    public SelectAdsFlagRule(Boolean useServerAuctionPublicCoordinator) {
        this.mUsePublicCoordinator = useServerAuctionPublicCoordinator;
    }

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests().setCompatModeFlags();

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                setupFlags();
                base.evaluate();
            }
        };
    }

    private void setupFlags() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
        enableAdservicesApi();
        disableApiThrottling();
        disablePhenotypeFlagUpdates();
        extendAuctionTimeouts();
        // Disable backoff since we will be killing the process between tests
        disableBackoff();
        modifyServerAuctionFlags();
    }

    private void modifyServerAuctionFlags() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_auction_server_ad_render_id_enabled "
                        + "true");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_auction_server_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_auction_server_enabled true");
        String coordinatorUri =
                mUsePublicCoordinator
                        ? "https://publickeyservice.pa.gcp.privacysandboxservices.com/.well-known/protected-auction/v1/public-keys"
                        : "https://ba-kv-service-5jyy5ulagq-uc.a.run.app/keys/2";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_auction_server_auction_key_fetch_uri",
                coordinatorUri,
                false);
    }

    private static void disableBackoff() {
        String packageName =
                AdservicesTestHelper.getAdServicesPackageName(
                        ApplicationProvider.getApplicationContext());
        ShellUtils.runShellCommand("am service-restart-backoff disable " + packageName);
    }

    private static void extendAuctionTimeouts() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_ca_ms "
                        + "120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_scoring_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_overall_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_buyer_ms "
                        + "120000");
    }

    private static void disableApiThrottling() {
        ShellUtils.runShellCommand(
                "device_config put adservices sdk_request_permits_per_second 1000");
    }

    private static void disablePhenotypeFlagUpdates() {
        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests persistent");
    }

    private static void enableAdservicesApi() {
        ShellUtils.runShellCommand(
                "device_config put adservices disable_fledge_enrollment_check true");
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put fledge_schedule_custom_audience_update_enabled true");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_service_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_select_ads_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices adservice_system_service_enabled true");
        ShellUtils.runShellCommand("device_config put adservices enable_back_compat true");
    }
}
