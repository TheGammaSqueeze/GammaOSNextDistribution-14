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

package android.net.dhcp;

import static android.net.InetAddresses.parseNumericAddress;
import static android.net.dhcp.DhcpResultsParcelableUtil.toStableParcelable;
import static android.net.shared.IpConfigurationParcelableUtil.unparcelAddress;

import static com.android.testutils.MiscAsserts.assertFieldCountEquals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.DhcpResults;
import android.net.DhcpResultsParcelable;
import android.net.LinkAddress;
import android.net.shared.IpConfigurationParcelableUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.util.Arrays;

/**
 * Tests for {@link IpConfigurationParcelableUtil}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DhcpResultsParcelableUtilTest {
    private DhcpResults mDhcpResults;

    @Before
    public void setUp() {
        mDhcpResults = new DhcpResults();
        mDhcpResults.ipAddress = new LinkAddress(parseNumericAddress("192.168.42.19"), 25);
        mDhcpResults.gateway = parseNumericAddress("192.168.42.42");
        mDhcpResults.dnsServers.add(parseNumericAddress("8.8.8.8"));
        mDhcpResults.dnsServers.add(parseNumericAddress("192.168.43.43"));
        mDhcpResults.domains = "example.com";
        mDhcpResults.serverAddress = (Inet4Address) parseNumericAddress("192.168.44.44");
        mDhcpResults.vendorInfo = "TEST_VENDOR_INFO";
        mDhcpResults.leaseDuration = 3600;
        mDhcpResults.serverHostName = "dhcp.example.com";
        mDhcpResults.mtu = 1450;
        mDhcpResults.captivePortalApiUrl = "https://example.com/testapi";
        mDhcpResults.dmnsrchList.addAll(Arrays.asList("google.com", "example.com"));
        // Any added DhcpResults field must be included in equals() to be tested properly
        assertFieldCountEquals(11, DhcpResults.class);
    }

    @Test
    public void testParcelDhcpResults() {
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_NullIpAddress() {
        mDhcpResults.ipAddress = null;
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_NullGateway() {
        mDhcpResults.gateway = null;
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_NullDomains() {
        mDhcpResults.domains = null;
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_EmptyDomains() {
        mDhcpResults.domains = "";
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_NullServerAddress() {
        mDhcpResults.serverAddress = null;
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_NullVendorInfo() {
        mDhcpResults.vendorInfo = null;
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_NullServerHostName() {
        mDhcpResults.serverHostName = null;
        doDhcpResultsParcelTest();
    }

    @Test
    public void testParcelUnparcelDhcpResults_NullCaptivePortalApiUrl() {
        mDhcpResults.captivePortalApiUrl = null;
        doDhcpResultsParcelTest();
    }

    private void doDhcpResultsParcelTest() {
        final DhcpResults unparceled = fromStableParcelable(toStableParcelable(mDhcpResults));
        setFieldsLostWhileParceling(unparceled);
        assertEquals(mDhcpResults, unparceled);
    }

    private void setFieldsLostWhileParceling(@NonNull DhcpResults unparceledResults) {
        // TODO: add other fields that are not part of DhcpResultsParcelable here
        // e.g. if the dmnsrchList field is added,
        unparceledResults.dmnsrchList.clear();
        unparceledResults.dnsServers.clear();
        unparceledResults.dmnsrchList.addAll(mDhcpResults.dmnsrchList);
        unparceledResults.domains = mDhcpResults.domains;
        unparceledResults.dnsServers.addAll(mDhcpResults.dnsServers);
        unparceledResults.gateway = mDhcpResults.gateway;
        unparceledResults.ipAddress = mDhcpResults.ipAddress;
    }

    /**
     * Convert a DhcpResultsParcelable to DhcpResults.
     */
    private static DhcpResults fromStableParcelable(@Nullable DhcpResultsParcelable p) {
        if (p == null) return null;
        final DhcpResults results = new DhcpResults(p.baseConfiguration);
        results.leaseDuration = p.leaseDuration;
        results.mtu = p.mtu;
        results.serverAddress = (Inet4Address) unparcelAddress(p.serverAddress);
        results.vendorInfo = p.vendorInfo;
        results.serverHostName = p.serverHostName;
        results.captivePortalApiUrl = p.captivePortalApiUrl;
        // DhcpResultsParcelable is only used to fill the legacy DhcpInfo class in Wifi, so it
        // should not be extended with any new field. Some fields maybe part of DhcpResults, but
        // not DhcpResultsParcelable, as DhcpResults is used internally in NetworkStack, but
        // DhcpResultsParcelable is used to provide info to wifi (for building DhcpInfo)

        return results;
    }

    @Test
    public void testToString() {
        final String str = toStableParcelable(mDhcpResults).toString();

        // check a few fields. Comprehensive toString tests exist in aidl_integration_test,
        // but we want to make sure that the toString function requested in the AIDL file
        // is there
        assertTrue(str, str.contains("baseConfiguration"));
        assertTrue(str, str.contains("IP address 192.168.42.19/25"));
        assertTrue(str, str.contains("serverAddress"));
        assertTrue(str, str.contains("192.168.44.44"));
    }
}
