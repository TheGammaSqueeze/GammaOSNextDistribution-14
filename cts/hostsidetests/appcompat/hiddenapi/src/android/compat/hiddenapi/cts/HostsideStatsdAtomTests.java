/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.compat.hiddenapi.cts;

import com.android.tradefed.util.RunUtil;

import static com.google.common.truth.Truth.assertThat;

import android.cts.host.utils.DisableDeviceConfigSyncRule;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.HiddenApiUsed;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import javax.annotation.Nullable;

@RunWith(DeviceJUnit4ClassRunner.class)
public class HostsideStatsdAtomTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String TEST_PKG = "android.compat.hiddenapi.cts";
    private static final String TEST_APK = "CtsHiddenApiApp.apk";

    private IBuildInfo mCtsBuild;

    @Rule(order = 0)
    public DisableDeviceConfigSyncRule mDisableDeviceConfigSync =
            new DisableDeviceConfigSyncRule(this);

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @NonApiTest(exemptionReasons = {}, justification = "Testing hidden API access logging, which "
            + "is not a CDD requirement")
    @Test
    public void hiddenApiUsed() throws Exception {
        String oldRate = getDevice().executeShellCommand(
                "device_config get app_compat hidden_api_access_statslog_sampling_rate").trim();

        getDevice().executeShellCommand(
                "device_config put app_compat hidden_api_access_statslog_sampling_rate 65536");

        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);

        try {
            final int atomTag = Atom.HIDDEN_API_USED_FIELD_NUMBER;

             // Upload the config.
            final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(TEST_PKG);
            ConfigUtils.addEventMetricForUidAtom(config,  Atom.HIDDEN_API_USED_FIELD_NUMBER,
                    /*uidInAttributionChain=*/false, TEST_PKG);
            ConfigUtils.uploadConfig(getDevice(), config);

            // Trigger hidden api event.
            runActivity(getDevice(), TEST_PKG, "HiddenApiUsedActivity",
                    /*actionKey=*/null, /*actionValue=*/null);
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

            List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
            assertThat(data).hasSize(1);

            HiddenApiUsed atom = data.get(0).getAtom().getHiddenApiUsed();

            final int appUid = DeviceUtils.getAppUid(getDevice(), TEST_PKG);
            assertThat(atom.getUid()).isEqualTo(appUid);
            assertThat(atom.getAccessDenied()).isFalse();
            assertThat(atom.getSignature())
                .isEqualTo("Landroid/app/Activity;->mWindow:Landroid/view/Window;");
        } finally {
            if (!oldRate.equals("null")) {
                getDevice().executeShellCommand(
                        "device_config put app_compat hidden_api_access_statslog_sampling_rate "
                        + oldRate);
            } else {
                getDevice().executeShellCommand(
                        "device_config delete hidden_api_access_statslog_sampling_rate");
            }
            DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        }
    }
        /**
     * Runs an activity in a particular app.
     */
    public static void runActivity(ITestDevice device, String pkgName, String activity,
            @Nullable String actionKey, @Nullable String actionValue) throws Exception {
        runActivity(device, pkgName, activity, actionKey, actionValue,
                AtomTestUtils.WAIT_TIME_LONG);
    }

    /**
     * Runs an activity in a particular app for a certain period of time.
     *
     * @param pkgName name of package that contains the Activity
     * @param activity name of the Activity class
     * @param actionKey key of extra data that is passed to the Activity via an Intent
     * @param actionValue value of extra data that is passed to the Activity via an Intent
     * @param waitTimeMs duration that the activity runs for
     */
    public static void runActivity(ITestDevice device, String pkgName, String activity,
            @Nullable String actionKey, @Nullable String actionValue, long waitTimeMs)
            throws Exception {
        try (AutoCloseable a = withActivity(device, pkgName, activity, actionKey, actionValue)) {
            RunUtil.getDefault().sleep(waitTimeMs);
        }
    }

    /**
     * Starts the specified activity and returns an {@link AutoCloseable} that stops the activity
     * when closed.
     *
     * <p>Example usage:
     * <pre>
     *     try (AutoClosable a = withActivity("activity", "action", "action-value")) {
     *         doStuff();
     *     }
     * </pre>
     */
    public static AutoCloseable withActivity(ITestDevice device, String pkgName, String activity,
            @Nullable String actionKey, @Nullable String actionValue) throws Exception {
        String intentString;
        if (actionKey != null && actionValue != null) {
            intentString = actionKey + " " + actionValue;
        } else {
            intentString = null;
        }

        String cmd = "am start -n " + pkgName + "/." + activity;
        if (intentString != null) {
            cmd += " -e " + intentString;
        }
        device.executeShellCommand(cmd);

        return () -> {
            device.executeShellCommand("am force-stop " + pkgName);
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        };
    }

}
