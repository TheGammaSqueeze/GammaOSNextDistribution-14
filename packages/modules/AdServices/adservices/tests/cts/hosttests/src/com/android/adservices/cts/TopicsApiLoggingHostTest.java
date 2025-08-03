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

package com.android.adservices.cts;

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.FlakyTest;

import com.android.adservices.common.AdServicesHostSideDeviceSupportedRule;
import com.android.adservices.common.AdServicesHostSideFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.HostSideSdkLevelSupportRule;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.AdServicesApiCalled;
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

import java.util.List;

/**
 * Test to check that Topics API logging to StatsD
 *
 * <p>When this test builds, it also builds {@link com.android.adservices.cts.TopicsApiLogActivity}
 * into an APK which it then installed at runtime and started. The activity simply called getTopics
 * service which trigger the log event, and then gets uninstalled.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TopicsApiLoggingHostTest extends AdServicesHostSideTestCase {
    private static final String PACKAGE = "com.android.adservices.cts";
    private static final String CLASS = "TopicsApiLogActivity";
    private static final String SDK_NAME = "AdservicesCtsSdk";
    private static final String TARGET_PACKAGE_SUFFIX_TPLUS = "android.adservices.api";
    private static final String TARGET_PACKAGE_SUFFIX_SMINUS = "android.ext.services";
    private static final int PPAPI_ONLY_SOURCE_OF_TRUTH = 1;
    private static final int PPAPI_AND_SYSTEM_SERVER_SOURCE_OF_TRUTH = 2;
    private static final String TARGET_PACKAGE = "com.google.android.adservices.api";
    private static final String TARGET_PACKAGE_AOSP = "com.android.adservices.api";
    private static final String TARGET_EXT_ADSERVICES_PACKAGE =
            "com.google.android.ext.adservices.api";
    private static final String TARGET_EXT_ADSERVICES_PACKAGE_AOSP =
            "com.android.ext.adservices.api";
    private static final String LOW_RAM_DEVICE_CONFIG = "ro.config.low_ram";

    // Topics are not going to be implemented on Android R, so this test shouldn't run on R.
    // If that decision changes, this will need to be enabled. TODO(b/269798827).
    @Rule(order = 0)
    public final HostSideSdkLevelSupportRule sdkLevel = HostSideSdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesHostSideDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesHostSideDeviceSupportedRule();

    @Rule(order = 2)
    public final AdServicesHostSideFlagsSetterRule flags =
            AdServicesHostSideFlagsSetterRule.forCompatModeEnabledTests()
                    .setTopicsKillSwitch(false)
                    .setAdServicesEnabled(true)
                    .setMddBackgroundTaskKillSwitch(true)
                    .setSystemProperty(KEY_CONSENT_MANAGER_DEBUG_MODE, true)
                    .setFlag(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true);

    @Rule(order = 3)
    public TestMetrics mMetrics = new TestMetrics();

    private String mTargetPackage;

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        // Set flags for test to run on devices with api level lower than 33 (S-)
        String suffix =
                sdkLevel.isAtLeastT() ? TARGET_PACKAGE_SUFFIX_TPLUS : TARGET_PACKAGE_SUFFIX_SMINUS;
        mTargetPackage = findPackageName(suffix);
        assertThat(mTargetPackage).isNotNull();
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Test
    @FlakyTest(bugId = 305089304)
    public void testGetTopicsLog() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);
        boolean enforceForegroundStatus = getEnforceForeground();
        setEnforceForeground(false);

        callTopicsAPI(mTargetPackage, device);

        // Fetch a list of happened log events and their data
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(device);

        setEnforceForeground(enforceForegroundStatus);

        // We trigger only one event from activity, should only see one event in the list
        assertThat(data).hasSize(1);

        // Verify the log event data
        AdServicesApiCalled adServicesApiCalled = data.get(0).getAtom().getAdServicesApiCalled();
        assertThat(adServicesApiCalled.getSdkPackageName()).isEqualTo(SDK_NAME);
        assertThat(adServicesApiCalled.getAppPackageName()).isEqualTo(PACKAGE);
        assertThat(adServicesApiCalled.getApiClass())
                .isEqualTo(AdServicesApiCalled.AdServicesApiClassType.TARGETING);
        assertThat(adServicesApiCalled.getApiName())
                .isEqualTo(AdServicesApiCalled.AdServicesApiName.GET_TOPICS);
    }

    private void callTopicsAPI(String apiName, ITestDevice device) throws Exception {
        // Upload the config.
        final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(apiName);

        ConfigUtils.addEventMetric(config, Atom.AD_SERVICES_API_CALLED_FIELD_NUMBER);
        ConfigUtils.uploadConfig(device, config);

        // Run the get topic activity that has logging event on the devices
        // 4th argument is actionKey and 5th is actionValue, which is the extra data that passed
        // to the activity via an Intent, we don't need to provide extra values, thus passing
        // in null here
        DeviceUtils.runActivity(
                device, PACKAGE, CLASS, /* actionKey */ null, /* actionValue */ null);

        // Wait for activity to finish and logging event to happen
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
    }

    // Set enforce foreground execution.
    private void setEnforceForeground(boolean enableForegound) throws DeviceNotAvailableException {
        getDevice()
                .executeShellCommand(
                        "device_config put adservices topics_enforce_foreground_status "
                                + enableForegound);
    }

    private boolean getEnforceForeground() throws DeviceNotAvailableException {
        String enforceForegroundStatus =
                getDevice()
                        .executeShellCommand(
                                "device_config get adservices topics_enforce_foreground_status");
        return enforceForegroundStatus.equals("true\n");
    }

    private String findPackageName(String suffix) throws DeviceNotAvailableException {
        return mDevice.getInstalledPackageNames().stream()
                .filter(s -> s.endsWith(suffix))
                .findFirst()
                .orElse(null);
    }

    /**
     * The test is unsupported if the device is configured as a low-RAM device.
     */
    private boolean isDeviceSupported() throws DeviceNotAvailableException {
        return !"true".equals(getDevice().getProperty(LOW_RAM_DEVICE_CONFIG));
    }
}
