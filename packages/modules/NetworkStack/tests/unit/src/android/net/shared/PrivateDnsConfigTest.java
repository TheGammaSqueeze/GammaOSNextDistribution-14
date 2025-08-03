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

package android.net.shared;

import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.net.PrivateDnsConfigParcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.InetAddress;

@RunWith(JUnit4.class)
public final class PrivateDnsConfigTest {
    private static final int OFF_MODE = PRIVATE_DNS_MODE_OFF;
    private static final int OPPORTUNISTIC_MODE = PRIVATE_DNS_MODE_OPPORTUNISTIC;
    private static final int STRICT_MODE = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

    private static final InetAddress[] TEST_ADDRS = new InetAddress[] {
        InetAddress.parseNumericAddress("1.2.3.4"),
        InetAddress.parseNumericAddress("2001:db8::2"),
    };

    private String[] toStringArray(InetAddress[] ips) {
        String[] out = new String[ips.length];
        int i = 0;
        for (InetAddress ip : ips) {
            out[i++] = ip.getHostAddress();
        }
        return out;
    }

    private void assertPrivateDnsConfigEquals(PrivateDnsConfig a, PrivateDnsConfig b) {
        assertEquals(a.mode, b.mode);
        assertEquals(a.hostname, b.hostname);
        assertArrayEquals(a.ips, b.ips);
        assertEquals(a.dohName, b.dohName);
        assertArrayEquals(a.dohIps, b.dohIps);
        assertEquals(a.dohPath, b.dohPath);
        assertEquals(a.dohPort, b.dohPort);
    }

    private void assertParcelEquals(PrivateDnsConfig cfg, PrivateDnsConfigParcel parcel) {
        assertEquals(parcel.privateDnsMode, cfg.mode);
        assertEquals(parcel.hostname, cfg.hostname);
        assertArrayEquals(parcel.ips, toStringArray(cfg.ips));
        assertEquals(parcel.dohName, cfg.dohName);
        assertEquals(parcel.dohPath, cfg.dohPath);
        assertEquals(parcel.dohPort, cfg.dohPort);
        assertArrayEquals(parcel.dohIps, toStringArray(cfg.dohIps));
    }

    // Tests both toParcel() and fromParcel() together.
    private void testPrivateDnsConfigConversion(PrivateDnsConfig cfg) {
        final PrivateDnsConfigParcel parcel = cfg.toParcel();
        assertParcelEquals(cfg, parcel);

        final PrivateDnsConfig convertedCfg = PrivateDnsConfig.fromParcel(parcel);
        assertPrivateDnsConfigEquals(cfg, convertedCfg);
    }

    // Tests that a PrivateDnsConfig and a PrivateDnsConfig that is converted from
    // PrivateDnsConfigParcel are equal.
    @Test
    public void testParcelableConversion() {
        // Test the constructor: PrivateDnsConfig()
        testPrivateDnsConfigConversion(new PrivateDnsConfig());

        // Test the constructor: PrivateDnsConfig(boolean useTls)
        testPrivateDnsConfigConversion(new PrivateDnsConfig(true));
        testPrivateDnsConfigConversion(new PrivateDnsConfig(false));

        // Test the constructor: PrivateDnsConfig(String hostname, InetAddress[] ips)
        testPrivateDnsConfigConversion(new PrivateDnsConfig(null, null));
        testPrivateDnsConfigConversion(new PrivateDnsConfig(null, TEST_ADDRS));
        testPrivateDnsConfigConversion(new PrivateDnsConfig("dns.com", null));
        testPrivateDnsConfigConversion(new PrivateDnsConfig("dns.com", TEST_ADDRS));

        // Test the constructor:
        // PrivateDnsConfig(int mode, String hostname, InetAddress[] ips,
        //                  String dohName, InetAddress[] dohIps, String dohPath, int dohPort)
        for (int mode : new int[] { OFF_MODE, OPPORTUNISTIC_MODE, STRICT_MODE }) {
            testPrivateDnsConfigConversion(new PrivateDnsConfig(mode, null, null,
                    null, null, null, -1));
            testPrivateDnsConfigConversion(new PrivateDnsConfig(mode, "dns.com", null,
                    null, null, null, -1));
            testPrivateDnsConfigConversion(new PrivateDnsConfig(mode, "dns.com", TEST_ADDRS,
                    null, null, null, -1));
            testPrivateDnsConfigConversion(new PrivateDnsConfig(mode, "dns.com", TEST_ADDRS,
                    "doh.com", null, null, -1));
            testPrivateDnsConfigConversion(new PrivateDnsConfig(mode, "dns.com", TEST_ADDRS,
                    "doh.com", TEST_ADDRS, null, -1));
            testPrivateDnsConfigConversion(new PrivateDnsConfig(mode, "dns.com", TEST_ADDRS,
                    "doh.com", TEST_ADDRS, "dohpath=/some-path{?dns}", -1));
            testPrivateDnsConfigConversion(new PrivateDnsConfig(mode, "dns.com", TEST_ADDRS,
                    "doh.com", TEST_ADDRS, "dohpath=/some-path{?dns}", 443));
        }
    }

    @Test
    public void testIpAddressArrayIsCopied() {
        final InetAddress ip = InetAddress.parseNumericAddress("1.2.3.4");
        final InetAddress[] ipArray = new InetAddress[] { ip };
        final PrivateDnsConfig cfg = new PrivateDnsConfig(OPPORTUNISTIC_MODE, null /* hostname */,
                ipArray /* ips */, null /* dohName */, ipArray /* dohIps */, null /* dohPath */,
                -1 /* dohPort */);

        ipArray[0] = InetAddress.parseNumericAddress("2001:db8::2");
        assertArrayEquals(new InetAddress[] { ip }, cfg.ips);
        assertArrayEquals(new InetAddress[] { ip }, cfg.dohIps);
    }
}
