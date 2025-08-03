/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.tethering.mts;

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_SETTINGS;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;

import static com.android.testutils.TestNetworkTrackerKt.initTestNetwork;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.TetheringInterface;
import android.net.cts.util.CtsTetheringUtils;
import android.net.cts.util.CtsTetheringUtils.TestTetheringEventCallback;
import android.provider.DeviceConfig;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.TestNetworkTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TetheringModuleTest {
    private Context mContext;
    private CtsTetheringUtils mCtsTetheringUtils;
    private final long mRestartTimeOutMs = 5_000;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mCtsTetheringUtils = new CtsTetheringUtils(mContext);
    }

    @Test
    public void testSwitchBasePrefixRangeWhenConflict() throws Exception {
        addressConflictTest(true);
    }

    @Test
    public void testSwitchPrefixRangeWhenConflict() throws Exception {
        addressConflictTest(false);
    }

    private void addressConflictTest(final boolean wholeRangeConflict) throws Exception {
        final TestTetheringEventCallback tetherEventCallback =
                mCtsTetheringUtils.registerTetheringEventCallback();

        TestNetworkTracker tnt = null;
        try {
            tetherEventCallback.assumeWifiTetheringSupported(mContext);
            tetherEventCallback.expectNoTetheringActive();

            final TetheringInterface tetheredIface =
                    mCtsTetheringUtils.startWifiTethering(tetherEventCallback);

            assertNotNull(tetheredIface);
            final String wifiTetheringIface = tetheredIface.getInterface();

            NetworkInterface nif = NetworkInterface.getByName(wifiTetheringIface);
            // Tethering downstream only have one ipv4 address.
            final LinkAddress hotspotAddr = getFirstIpv4Address(nif);
            assertNotNull(hotspotAddr);

            final IpPrefix testPrefix = getConflictingPrefix(hotspotAddr, wholeRangeConflict);
            assertNotNull(testPrefix);

            tnt = setUpTestNetwork(
                    new LinkAddress(testPrefix.getAddress(), testPrefix.getPrefixLength()));

            tetherEventCallback.expectNoTetheringActive();
            final List<String> wifiRegexs =
                    tetherEventCallback.getTetheringInterfaceRegexps().getTetherableWifiRegexs();

            final TetheringInterface restartedIface =
                    tetherEventCallback.pollTetheredInterfacesChanged(wifiRegexs, TETHERING_WIFI,
                    mRestartTimeOutMs);
            final TetheringInterface newIface;
            if (restartedIface != null) {
                newIface = restartedIface;
            } else {
                // Because of race inside tethering module, there is no guarantee wifi tethering
                // would restart successfully. If tethering don't auto restarted, restarting it
                // manually. TODO(b/242649651): remove this when tethering auto restart is reliable.
                newIface = mCtsTetheringUtils.startWifiTethering(tetherEventCallback);
            }

            nif = NetworkInterface.getByName(newIface.getInterface());
            final LinkAddress newHotspotAddr = getFirstIpv4Address(nif);
            assertNotNull(newHotspotAddr);

            assertFalse(testPrefix.containsPrefix(
                    new IpPrefix(newHotspotAddr.getAddress(), newHotspotAddr.getPrefixLength())));

            mCtsTetheringUtils.stopWifiTethering(tetherEventCallback);
        } finally {
            teardown(tnt);
            mCtsTetheringUtils.stopAllTethering();
            mCtsTetheringUtils.unregisterTetheringEventCallback(tetherEventCallback);
        }
    }

    private LinkAddress getFirstIpv4Address(final NetworkInterface nif) {
        for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
            final LinkAddress addr = new LinkAddress(ia.getAddress(), ia.getNetworkPrefixLength());
            if (addr.isIpv4()) return addr;
        }
        return null;
    }

    @NonNull
    private IpPrefix getConflictingPrefix(final LinkAddress address,
            final boolean wholeRangeConflict) {
        if (!wholeRangeConflict) {
            return new IpPrefix(address.getAddress(), address.getPrefixLength());
        }

        final ArrayList<IpPrefix> prefixPool = new ArrayList<>(Arrays.asList(
                new IpPrefix("192.168.0.0/16"),
                new IpPrefix("172.16.0.0/12"),
                new IpPrefix("10.0.0.0/8")));

        for (IpPrefix prefix : prefixPool) {
            if (prefix.contains(address.getAddress())) return prefix;
        }

        fail("Could not find sutiable conflict prefix");

        // Never go here.
        return null;
    }

    private TestNetworkTracker setUpTestNetwork(final LinkAddress address) throws Exception {
        return runAsShell(MANAGE_TEST_NETWORKS, WRITE_SETTINGS,
                () -> initTestNetwork(mContext, address, 10_000L /* test timeout ms*/));

    }

    private void teardown(TestNetworkTracker tracker) throws Exception {
        if (tracker == null) return;

        runAsShell(MANAGE_TEST_NETWORKS, () -> tracker.teardown());
    }

    public static boolean isFeatureEnabled(final String name, final boolean defaultValue) {
        return runAsShell(READ_DEVICE_CONFIG,
                () -> DeviceConfig.getBoolean(NAMESPACE_CONNECTIVITY, name, defaultValue));
    }
}
