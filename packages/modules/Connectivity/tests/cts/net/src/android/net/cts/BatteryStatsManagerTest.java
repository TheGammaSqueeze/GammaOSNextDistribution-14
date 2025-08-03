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

package android.net.cts;

import static android.Manifest.permission.UPDATE_DEVICE_STATS;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.content.pm.PackageManager.FEATURE_WIFI;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.android.compatibility.common.util.BatteryUtils.hasBattery;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.cts.util.CtsNetUtils;
import android.net.wifi.WifiManager;
import android.os.BatteryStatsManager;
import android.os.Build;
import android.os.connectivity.CellularBatteryStats;
import android.os.connectivity.WifiBatteryStats;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.filters.RequiresDevice;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Test for BatteryStatsManager.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R) // BatteryStatsManager did not exist on Q
public class BatteryStatsManagerTest{
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();
    private static final String TAG = BatteryStatsManagerTest.class.getSimpleName();
    private static final String TEST_URL = "https://connectivitycheck.gstatic.com/generate_204";
    // This value should be the same as BatteryStatsManager.BATTERY_STATUS_DISCHARGING.
    // TODO: Use the constant once it's available in all branches
    private static final int BATTERY_STATUS_DISCHARGING = 3;

    private Context mContext;
    private BatteryStatsManager mBsm;
    private ConnectivityManager mCm;
    private WifiManager mWm;
    private PackageManager mPm;
    private CtsNetUtils mCtsNetUtils;

    @Before
    public void setUp() throws Exception {
        mContext = getContext();
        mBsm = mContext.getSystemService(BatteryStatsManager.class);
        mCm = mContext.getSystemService(ConnectivityManager.class);
        mWm = mContext.getSystemService(WifiManager.class);
        mPm = mContext.getPackageManager();
        mCtsNetUtils = new CtsNetUtils(mContext);
    }

    // reportNetworkInterfaceForTransports classifies one network interface as wifi or mobile, so
    // check that the interface is classified properly by checking the data usage is reported
    // properly.
    @Test
    @AppModeFull(reason = "Cannot get CHANGE_NETWORK_STATE to request wifi/cell in instant mode")
    @RequiresDevice // Virtual hardware does not support wifi battery stats
    public void testReportNetworkInterfaceForTransports() throws Exception {
        try {
            assumeTrue("Battery is not present. Ignore test.", hasBattery());
            // Simulate the device being unplugged from charging.
            executeShellCommand("cmd battery unplug");
            executeShellCommand("cmd battery set status " + BATTERY_STATUS_DISCHARGING);
            // Reset all current stats before starting test.
            executeShellCommand("dumpsys batterystats --reset");
            // Do not automatically reset the stats when the devices are unplugging after the
            // battery was last full or the level is 100, or have gone through a significant
            // charge.
            executeShellCommand("dumpsys batterystats enable no-auto-reset");
            // Upon calling "cmd battery unplug" a task is scheduled on the battery
            // stats worker thread. Because network battery stats are only recorded
            // when the device is on battery, this test needs to wait until the
            // battery status is recorded because causing traffic.
            // Writing stats to disk is unnecessary, but --write waits for the worker
            // thread to finish processing the enqueued tasks as a side effect. This
            // side effect is the point of using --write here.
            executeShellCommand("dumpsys batterystats --write");

            if (mPm.hasSystemFeature(FEATURE_WIFI)) {
                // Make sure wifi is disabled.
                mCtsNetUtils.ensureWifiDisconnected(null /* wifiNetworkToCheck */);
            }

            verifyGetCellBatteryStats();
            verifyGetWifiBatteryStats();

        } finally {
            // Reset battery settings.
            executeShellCommand("dumpsys batterystats disable no-auto-reset");
            executeShellCommand("cmd battery reset");
            if (mPm.hasSystemFeature(FEATURE_WIFI)) {
                mCtsNetUtils.ensureWifiConnected();
            }
        }
    }

    private void verifyGetCellBatteryStats() throws Exception {
        final boolean isTelephonySupported = mPm.hasSystemFeature(FEATURE_TELEPHONY);

        if (!isTelephonySupported) {
            Log.d(TAG, "Skip cell battery stats test because device does not support telephony.");
            return;
        }

        final Network cellNetwork = mCtsNetUtils.connectToCell();
        final URL url = new URL(TEST_URL);

        // Get cellular battery stats
        CellularBatteryStats cellularStatsBefore = runAsShell(UPDATE_DEVICE_STATS,
                mBsm::getCellularBatteryStats);

        // Generate traffic on cellular network.
        Log.d(TAG, "Generate traffic on cellular network.");
        generateNetworkTraffic(cellNetwork, url);

        // The mobile battery stats are updated when a network stops being the default network.
        // ConnectivityService will call BatteryStatsManager.reportMobileRadioPowerState when
        // removing data activity tracking.
        try {
            mCtsNetUtils.setMobileDataEnabled(false);

            // There's rate limit to update mobile battery so if ConnectivityService calls
            // BatteryStatsManager.reportMobileRadioPowerState when default network changed,
            // the mobile stats might not be updated. But if the mobile update due to other
            // reasons (plug/unplug, battery level change, etc) will be unaffected. Thus here
            // dumps the battery stats to trigger a full sync of data.
            executeShellCommand("dumpsys batterystats");

            // Check cellular battery stats are updated.
            runAsShell(UPDATE_DEVICE_STATS,
                    () -> assertStatsEventually(mBsm::getCellularBatteryStats,
                        cellularStatsAfter -> cellularBatteryStatsIncreased(
                        cellularStatsBefore, cellularStatsAfter)));
        } finally {
            mCtsNetUtils.setMobileDataEnabled(true);
        }
    }

    private void verifyGetWifiBatteryStats() throws Exception {
        if (!mPm.hasSystemFeature(FEATURE_WIFI)) {
            return;
        }

        final Network wifiNetwork = mCtsNetUtils.ensureWifiConnected();
        final URL url = new URL(TEST_URL);

        if (!mWm.isEnhancedPowerReportingSupported()) {
            Log.d(TAG, "Skip wifi stats test because wifi does not support link layer stats.");
            return;
        }

        WifiBatteryStats wifiStatsBefore = runAsShell(UPDATE_DEVICE_STATS,
                mBsm::getWifiBatteryStats);

        // Generate traffic on wifi network.
        Log.d(TAG, "Generate traffic on wifi network.");
        generateNetworkTraffic(wifiNetwork, url);
        // Wifi battery stats are updated when wifi on.
        mCtsNetUtils.disableWifi();
        mCtsNetUtils.ensureWifiConnected();

        // Check wifi battery stats are updated.
        runAsShell(UPDATE_DEVICE_STATS,
                () -> assertStatsEventually(mBsm::getWifiBatteryStats,
                    wifiStatsAfter -> wifiBatteryStatsIncreased(wifiStatsBefore,
                    wifiStatsAfter)));
    }

    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    @AppModeFull(reason = "Cannot get WifiManager in instant app mode")
    @Test
    public void testReportNetworkInterfaceForTransports_throwsSecurityException()
            throws Exception {
        final Network network = mCm.getActiveNetwork();
        final String iface = mCm.getLinkProperties(network).getInterfaceName();
        final int[] transportType = mCm.getNetworkCapabilities(network).getTransportTypes();
        assertThrows(SecurityException.class,
                () -> mBsm.reportNetworkInterfaceForTransports(iface, transportType));
    }

    private void generateNetworkTraffic(Network network, URL url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) network.openConnection(url);
            assertEquals(204, connection.getResponseCode());
        } catch (IOException e) {
            Log.e(TAG, "Generate traffic failed with exception " + e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static <T> void assertStatsEventually(Supplier<T> statsGetter,
            Predicate<T> statsChecker) throws Exception {
        // Wait for updating mobile/wifi stats, and check stats every 10ms.
        final int maxTries = 1000;
        T result = null;
        for (int i = 1; i <= maxTries; i++) {
            result = statsGetter.get();
            if (statsChecker.test(result)) return;
            Thread.sleep(10);
        }
        final String stats = result instanceof CellularBatteryStats
                ? "Cellular" : "Wifi";
        fail(stats + " battery stats did not increase.");
    }

    private static boolean cellularBatteryStatsIncreased(CellularBatteryStats before,
            CellularBatteryStats after) {
        return (after.getNumBytesTx() > before.getNumBytesTx())
                && (after.getNumBytesRx() > before.getNumBytesRx())
                && (after.getNumPacketsTx() > before.getNumPacketsTx())
                && (after.getNumPacketsRx() > before.getNumPacketsRx());
    }

    private static boolean wifiBatteryStatsIncreased(WifiBatteryStats before,
            WifiBatteryStats after) {
        return (after.getNumBytesTx() > before.getNumBytesTx())
                && (after.getNumBytesRx() > before.getNumBytesRx())
                && (after.getNumPacketsTx() > before.getNumPacketsTx())
                && (after.getNumPacketsRx() > before.getNumPacketsRx());
    }

    private static String executeShellCommand(String command) {
        final String result = runShellCommand(command).trim();
        Log.d(TAG, "Output of '" + command + "': '" + result + "'");
        return result;
    }
}
