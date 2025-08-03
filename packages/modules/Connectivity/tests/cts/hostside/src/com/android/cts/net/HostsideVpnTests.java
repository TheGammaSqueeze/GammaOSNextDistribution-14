/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.net;

import android.platform.test.annotations.RequiresDevice;

import com.android.testutils.SkipPresubmit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HostsideVpnTests extends HostsideNetworkTestCase {

    @Before
    public void setUp() throws Exception {
        uninstallPackage(TEST_APP2_PKG, false);
        installPackage(TEST_APP2_APK);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP2_PKG, true);
    }

    @SkipPresubmit(reason = "Out of SLO flakiness")
    @Test
    public void testChangeUnderlyingNetworks() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testChangeUnderlyingNetworks");
    }

    @SkipPresubmit(reason = "Out of SLO flakiness")
    @Test
    public void testDefault() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testDefault");
    }

    @Test
    public void testAppAllowed() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testAppAllowed");
    }

    @Test
    public void testAppDisallowed() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testAppDisallowed");
    }

    @Test
    public void testSocketClosed() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testSocketClosed");
    }

    @Test
    public void testGetConnectionOwnerUidSecurity() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testGetConnectionOwnerUidSecurity");
    }

    @Test
    public void testSetProxy() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testSetProxy");
    }

    @Test
    public void testSetProxyDisallowedApps() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testSetProxyDisallowedApps");
    }

    @Test
    public void testNoProxy() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testNoProxy");
    }

    @Test
    public void testBindToNetworkWithProxy() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testBindToNetworkWithProxy");
    }

    @Test
    public void testVpnMeterednessWithNoUnderlyingNetwork() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_PKG + ".VpnTest", "testVpnMeterednessWithNoUnderlyingNetwork");
    }

    @Test
    public void testVpnMeterednessWithNullUnderlyingNetwork() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_PKG + ".VpnTest", "testVpnMeterednessWithNullUnderlyingNetwork");
    }

    @Test
    public void testVpnMeterednessWithNonNullUnderlyingNetwork() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_PKG + ".VpnTest", "testVpnMeterednessWithNonNullUnderlyingNetwork");
    }

    @Test
    public void testAlwaysMeteredVpnWithNullUnderlyingNetwork() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_PKG + ".VpnTest", "testAlwaysMeteredVpnWithNullUnderlyingNetwork");
    }

    @RequiresDevice // Keepalive is not supported on virtual hardware
    @Test
    public void testAutomaticOnOffKeepaliveModeClose() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_PKG + ".VpnTest", "testAutomaticOnOffKeepaliveModeClose");
    }

    @RequiresDevice // Keepalive is not supported on virtual hardware
    @Test
    public void testAutomaticOnOffKeepaliveModeNoClose() throws Exception {
        runDeviceTests(
                TEST_PKG, TEST_PKG + ".VpnTest", "testAutomaticOnOffKeepaliveModeNoClose");
    }

    @Test
    public void testAlwaysMeteredVpnWithNonNullUnderlyingNetwork() throws Exception {
        runDeviceTests(
                TEST_PKG,
                TEST_PKG + ".VpnTest",
                "testAlwaysMeteredVpnWithNonNullUnderlyingNetwork");
    }

    @Test
    public void testB141603906() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testB141603906");
    }

    @Test
    public void testDownloadWithDownloadManagerDisallowed() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest",
                "testDownloadWithDownloadManagerDisallowed");
    }

    @Test
    public void testExcludedRoutes() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testExcludedRoutes");
    }

    @Test
    public void testIncludedRoutes() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testIncludedRoutes");
    }

    @Test
    public void testInterleavedRoutes() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testInterleavedRoutes");
    }

    @Test
    public void testBlockIncomingPackets() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testBlockIncomingPackets");
    }

    @SkipPresubmit(reason = "Out of SLO flakiness")
    @Test
    public void testSetVpnDefaultForUids() throws Exception {
        runDeviceTests(TEST_PKG, TEST_PKG + ".VpnTest", "testSetVpnDefaultForUids");
    }
}
