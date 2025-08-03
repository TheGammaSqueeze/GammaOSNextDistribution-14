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

package com.android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.adservices.common.AdServicesHostSideDeviceSupportedRule;
import com.android.adservices.common.AdServicesHostSideFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.BackgroundLogReceiver;
import com.android.adservices.common.HostSideSdkLevelSupportRule;
import com.android.adservices.service.FlagsConstants;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.AdServicesSettingsUsageReported;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Test to check that Ui API logging to StatsD
 *
 * <p>The activity simply called Ui Settings Page which trigger the log event, and then check it in
 * statsD.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class UiApiLoggingHostTest extends AdServicesHostSideTestCase {
    private static final String CLASS =
            "com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity";
    private static final String TARGET_PACKAGE_SUFFIX_TPLUS = "android.adservices.api";
    private static final String TARGET_PACKAGE_SUFFIX_SMINUS = "android.ext.services";
    private static final String LOGCAT_COMMAND = "logcat -s adservices";
    private static final String LOG_FROM_BOOTCOMPLETE_RECEIVER =
            "AdExtBootCompletedReceiver onReceive invoked";
    public static final int WAIT_TIME_LONG = 2000;

    private String mTargetPackage;

    @Rule(order = 0)
    public final HostSideSdkLevelSupportRule sdkLevel = HostSideSdkLevelSupportRule.forAnyLevel();

    @Rule(order = 1)
    public final AdServicesHostSideDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesHostSideDeviceSupportedRule();

    @Rule(order = 2)
    public final AdServicesHostSideFlagsSetterRule flags =
            AdServicesHostSideFlagsSetterRule.forCompatModeEnabledTests()
                    .setTopicsKillSwitch(false)
                    .setAdServicesEnabled(true)
                    .setMddBackgroundTaskKillSwitch(true)
                    .setSystemProperty(FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE, true)
                    .setFlag(FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true)
                    .setFlag(FlagsConstants.KEY_GA_UX_FEATURE_ENABLED, true);

    @Rule(order = 3)
    public TestMetrics metricsRule = new TestMetrics();

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        // Set flags for test to run on devices with api level lower than 33 (S-)
        String suffix =
                sdkLevel.isAtLeastT() ? TARGET_PACKAGE_SUFFIX_TPLUS : TARGET_PACKAGE_SUFFIX_SMINUS;
        mTargetPackage = findPackageName(suffix);
        assertThat(mTargetPackage).isNotNull();
        restartAdservices(getDevice());
    }
    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Test
    public void testStartSettingMainActivityAndGetUiLog() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        rebootIfSMinus();
        startSettingMainActivity(mTargetPackage, device);

        // Fetch a list of happened log events and their data
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(device);

        // We trigger only one event from activity, should only see one event in the list
        assertThat(data).hasSize(1);

        // Verify the log event data
        AtomsProto.AdServicesSettingsUsageReported adServicesSettingsUsageReported =
                data.get(0).getAtom().getAdServicesSettingsUsageReported();
        assertThat(adServicesSettingsUsageReported.getAction())
                .isEqualTo(
                        AdServicesSettingsUsageReported.AdServiceSettingsName
                                .PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED);
    }

    private void rebootIfSMinus() throws DeviceNotAvailableException, InterruptedException {
        if (sdkLevel.isAtLeastT()) {
            return;
        }

        ITestDevice device = getDevice();
        device.reboot();
        device.waitForDeviceAvailable();

        // Start log collection, keep going until the boot complete receiver runs or times out.
        // Wait for up to 5 minutes for AdBootCompletedReceiver execution
        BackgroundLogReceiver logcatReceiver =
                new BackgroundLogReceiver.Builder()
                        .setDevice(mDevice)
                        .setLogCatCommand(LOGCAT_COMMAND)
                        .setEarlyStopCondition(stopIfBootCompleteReceiverLogOccurs())
                        .build();
        logcatReceiver.collectLogs(/* timeoutMilliseconds= */ 5 * 60 * 1000);
    }

    private Predicate<String[]> stopIfBootCompleteReceiverLogOccurs() {
        return (s) -> Arrays.stream(s).anyMatch(t -> t.contains(LOG_FROM_BOOTCOMPLETE_RECEIVER));
    }

    private void startSettingMainActivity(String apiName, ITestDevice device) throws Exception {
        // Upload the config.
        final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(apiName);

        ConfigUtils.addEventMetric(config, Atom.AD_SERVICES_SETTINGS_USAGE_REPORTED_FIELD_NUMBER);
        ConfigUtils.uploadConfig(device, config);
        // Start the ui main activity, it will make a ui log call
        startUiMainActivity(device);
        Thread.sleep(WAIT_TIME_LONG);
    }

    public void startUiMainActivity(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand("am start -n " + mTargetPackage + "/" + CLASS);
    }

    private String findPackageName(String suffix) throws DeviceNotAvailableException {
        return mDevice.getInstalledPackageNames().stream()
                .filter(s -> s.endsWith(suffix))
                .findFirst()
                .orElse(null);
    }

    private void restartAdservices(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand("am force-stop com.google.android.adservices.api");
        device.executeShellCommand("am force-stop com.android.adservices.api");
        device.executeShellCommand("am force-stop com.google.android.ext.adservices.api");
        device.executeShellCommand("am force-stop com.android.ext.adservices.api");
    }
}
