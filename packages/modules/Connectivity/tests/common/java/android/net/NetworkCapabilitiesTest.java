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

package android.net;

import static android.net.NetworkCapabilities.LINK_BANDWIDTH_UNSPECIFIED;
import static android.net.NetworkCapabilities.MAX_TRANSPORT;
import static android.net.NetworkCapabilities.MIN_TRANSPORT;
import static android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL;
import static android.net.NetworkCapabilities.NET_CAPABILITY_CBS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_EIMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_ENTERPRISE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK;
import static android.net.NetworkCapabilities.NET_CAPABILITY_MMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH;
import static android.net.NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_RCS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_SUPL;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_WIFI_P2P;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_1;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_2;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_3;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_4;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_5;
import static android.net.NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION;
import static android.net.NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS;
import static android.net.NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS;
import static android.net.NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_SATELLITE;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.NetworkCapabilities.TRANSPORT_USB;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE;
import static android.os.Process.INVALID_UID;
import static com.android.modules.utils.build.SdkLevel.isAtLeastS;
import static com.android.modules.utils.build.SdkLevel.isAtLeastT;
import static com.android.modules.utils.build.SdkLevel.isAtLeastV;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.MiscAsserts.assertEmpty;
import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.ParcelUtils.assertParcelingIsLossless;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.os.Build;
import android.util.ArraySet;
import android.util.Range;
import androidx.test.filters.SmallTest;
import com.android.testutils.CompatUtil;
import com.android.testutils.ConnectivityModuleTest;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@SmallTest
@RunWith(DevSdkIgnoreRunner.class)
// NetworkCapabilities is only updatable on S+, and this test covers behavior which implementation
// is self-contained within NetworkCapabilities.java, so it does not need to be run on, or
// compatible with, earlier releases.
@IgnoreUpTo(Build.VERSION_CODES.R)
@ConnectivityModuleTest
public class NetworkCapabilitiesTest {
    private static final String TEST_SSID = "TEST_SSID";
    private static final String DIFFERENT_TEST_SSID = "DIFFERENT_TEST_SSID";
    private static final int TEST_SUBID1 = 1;
    private static final int TEST_SUBID2 = 2;
    private static final int TEST_SUBID3 = 3;

    private static final Set<Integer> TEST_NETWORKS_EXTRA_ALLOWED_CAPS_ON_NON_CELL =
            Set.of(NET_CAPABILITY_CBS, NET_CAPABILITY_DUN, NET_CAPABILITY_RCS);

    @Rule
    public DevSdkIgnoreRule mDevSdkIgnoreRule = new DevSdkIgnoreRule();

    private DiscoverySession mDiscoverySession = Mockito.mock(DiscoverySession.class);
    private PeerHandle mPeerHandle = Mockito.mock(PeerHandle.class);

    @Test
    public void testMaybeMarkCapabilitiesRestricted() {
        // check that internet does not get restricted
        NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // metered-ness shouldn't matter
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // add EIMS - bundled with unrestricted means it's unrestricted
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // just a restricted cap should be restricted regardless of meteredness
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // try 2 restricted caps
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_CBS);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_CBS);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
    }

    @Test
    public void testDescribeImmutableDifferences() {
        NetworkCapabilities nc1;
        NetworkCapabilities nc2;

        // Transports changing
        nc1 = new NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR);
        nc2 = new NetworkCapabilities().addTransportType(TRANSPORT_WIFI);
        assertNotEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // Mutable capability changing
        nc1 = new NetworkCapabilities().addCapability(NET_CAPABILITY_VALIDATED);
        nc2 = new NetworkCapabilities();
        assertEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // NOT_METERED changing (http://b/63326103)
        nc1 = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .addCapability(NET_CAPABILITY_INTERNET);
        nc2 = new NetworkCapabilities().addCapability(NET_CAPABILITY_INTERNET);
        assertEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // Immutable capability changing
        nc1 = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        nc2 = new NetworkCapabilities().addCapability(NET_CAPABILITY_INTERNET);
        assertNotEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // Specifier changing
        nc1 = new NetworkCapabilities().addTransportType(TRANSPORT_WIFI);
        nc2 = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(CompatUtil.makeEthernetNetworkSpecifier("eth42"));
        assertNotEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));
    }

    @Test
    public void testLinkBandwidthUtils() {
        assertEquals(LINK_BANDWIDTH_UNSPECIFIED, NetworkCapabilities
                .minBandwidth(LINK_BANDWIDTH_UNSPECIFIED, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(10, NetworkCapabilities
                .minBandwidth(LINK_BANDWIDTH_UNSPECIFIED, 10));
        assertEquals(10, NetworkCapabilities
                .minBandwidth(10, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(10, NetworkCapabilities
                .minBandwidth(10, 20));

        assertEquals(LINK_BANDWIDTH_UNSPECIFIED, NetworkCapabilities
                .maxBandwidth(LINK_BANDWIDTH_UNSPECIFIED, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(10, NetworkCapabilities
                .maxBandwidth(LINK_BANDWIDTH_UNSPECIFIED, 10));
        assertEquals(10, NetworkCapabilities
                .maxBandwidth(10, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(20, NetworkCapabilities
                .maxBandwidth(10, 20));
    }

    @Test
    public void testSetUids() {
        final NetworkCapabilities netCap = new NetworkCapabilities();
        // Null uids match all UIDs
        netCap.setUids(null);
        assertTrue(netCap.appliesToUid(10));
        assertTrue(netCap.appliesToUid(200));
        assertTrue(netCap.appliesToUid(3000));
        assertTrue(netCap.appliesToUid(10010));
        assertTrue(netCap.appliesToUidRange(new UidRange(50, 100)));
        assertTrue(netCap.appliesToUidRange(new UidRange(70, 72)));
        assertTrue(netCap.appliesToUidRange(new UidRange(3500, 3912)));
        assertTrue(netCap.appliesToUidRange(new UidRange(1, 100000)));

        if (isAtLeastS()) {
            final Set<Range<Integer>> uids = new ArraySet<>();
            uids.add(uidRange(50, 100));
            uids.add(uidRange(3000, 4000));
            netCap.setUids(uids);
            assertTrue(netCap.appliesToUid(50));
            assertTrue(netCap.appliesToUid(80));
            assertTrue(netCap.appliesToUid(100));
            assertTrue(netCap.appliesToUid(3000));
            assertTrue(netCap.appliesToUid(3001));
            assertFalse(netCap.appliesToUid(10));
            assertFalse(netCap.appliesToUid(25));
            assertFalse(netCap.appliesToUid(49));
            assertFalse(netCap.appliesToUid(101));
            assertFalse(netCap.appliesToUid(2000));
            assertFalse(netCap.appliesToUid(100000));

            assertTrue(netCap.appliesToUidRange(new UidRange(50, 100)));
            assertTrue(netCap.appliesToUidRange(new UidRange(70, 72)));
            assertTrue(netCap.appliesToUidRange(new UidRange(3500, 3912)));
            assertFalse(netCap.appliesToUidRange(new UidRange(1, 100)));
            assertFalse(netCap.appliesToUidRange(new UidRange(49, 100)));
            assertFalse(netCap.appliesToUidRange(new UidRange(1, 10)));
            assertFalse(netCap.appliesToUidRange(new UidRange(60, 101)));
            assertFalse(netCap.appliesToUidRange(new UidRange(60, 3400)));

            NetworkCapabilities netCap2 = new NetworkCapabilities();
            // A new netcap object has null UIDs, so anything will satisfy it.
            assertTrue(netCap2.satisfiedByUids(netCap));
            // Still not equal though.
            assertFalse(netCap2.equalsUids(netCap));
            netCap2.setUids(uids);
            assertTrue(netCap2.satisfiedByUids(netCap));
            assertTrue(netCap.equalsUids(netCap2));
            assertTrue(netCap2.equalsUids(netCap));

            uids.add(uidRange(600, 700));
            netCap2.setUids(uids);
            assertFalse(netCap2.satisfiedByUids(netCap));
            assertFalse(netCap.appliesToUid(650));
            assertTrue(netCap2.appliesToUid(650));
            netCap.setUids(uids);
            assertTrue(netCap2.satisfiedByUids(netCap));
            assertTrue(netCap.appliesToUid(650));
            assertFalse(netCap.appliesToUid(500));

            // Null uids satisfies everything.
            netCap.setUids(null);
            assertTrue(netCap2.satisfiedByUids(netCap));
            assertTrue(netCap.satisfiedByUids(netCap2));
            netCap2.setUids(null);
            assertTrue(netCap2.satisfiedByUids(netCap));
            assertTrue(netCap.satisfiedByUids(netCap2));
        }
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testSetAllowedUids() {
        final NetworkCapabilities nc = new NetworkCapabilities();
        assertThrows(NullPointerException.class, () -> nc.setAllowedUids(null));
        assertFalse(nc.hasAllowedUids());
        assertFalse(nc.isUidWithAccess(0));
        assertFalse(nc.isUidWithAccess(1000));
        assertEquals(0, nc.getAllowedUids().size());
        nc.setAllowedUids(new ArraySet<>());
        assertFalse(nc.hasAllowedUids());
        assertFalse(nc.isUidWithAccess(0));
        assertFalse(nc.isUidWithAccess(1000));
        assertEquals(0, nc.getAllowedUids().size());

        final ArraySet<Integer> uids = new ArraySet<>();
        uids.add(200);
        uids.add(250);
        uids.add(-1);
        uids.add(Integer.MAX_VALUE);
        nc.setAllowedUids(uids);
        assertNotEquals(nc, new NetworkCapabilities());
        assertTrue(nc.hasAllowedUids());

        final List<Integer> includedList = List.of(-2, 0, 199, 700, 901, 1000, Integer.MIN_VALUE);
        final List<Integer> excludedList = List.of(-1, 200, 250, Integer.MAX_VALUE);
        for (final int uid : includedList) {
            assertFalse(nc.isUidWithAccess(uid));
        }
        for (final int uid : excludedList) {
            assertTrue(nc.isUidWithAccess(uid));
        }

        final Set<Integer> outUids = nc.getAllowedUids();
        assertEquals(4, outUids.size());
        for (final int uid : includedList) {
            assertFalse(outUids.contains(uid));
        }
        for (final int uid : excludedList) {
            assertTrue(outUids.contains(uid));
        }
    }

    @Test
    public void testParcelNetworkCapabilities() {
        final Set<Range<Integer>> uids = new ArraySet<>();
        uids.add(uidRange(50, 100));
        uids.add(uidRange(3000, 4000));
        final NetworkCapabilities netCap = new NetworkCapabilities()
            .addCapability(NET_CAPABILITY_INTERNET)
            .addCapability(NET_CAPABILITY_EIMS)
            .addCapability(NET_CAPABILITY_NOT_METERED);
        if (isAtLeastV()) {
            netCap.addCapability(NET_CAPABILITY_LOCAL_NETWORK);
        }
        if (isAtLeastS()) {
            final ArraySet<Integer> allowedUids = new ArraySet<>();
            allowedUids.add(4);
            allowedUids.add(9);
            netCap.setAllowedUids(allowedUids);
            netCap.setSubscriptionIds(Set.of(TEST_SUBID1, TEST_SUBID2));
            netCap.setUids(uids);
        }

        netCap.setOwnerUid(123);
        netCap.setAdministratorUids(new int[] {5, 11});
        assertParcelingIsLossless(netCap);
        netCap.setSSID(TEST_SSID);
        testParcelSane(netCap);
    }

    @Test
    public void testParcelNetworkCapabilitiesWithRequestorUidAndPackageName() {
        final NetworkCapabilities netCap = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_EIMS)
                .addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.setRequestorPackageName("com.android.test");
        netCap.setRequestorUid(9304);
        assertParcelingIsLossless(netCap);
        netCap.setSSID(TEST_SSID);
        testParcelSane(netCap);
    }

    private void testParcelSane(NetworkCapabilities cap) {
        assertParcelingIsLossless(cap);
    }

    private static NetworkCapabilities createNetworkCapabilitiesWithTransportInfo() {
        return new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_EIMS)
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .setSSID(TEST_SSID)
                .setTransportInfo(new TestTransportInfo())
                .setRequestorPackageName("com.android.test")
                .setRequestorUid(9304);
    }

    @Test
    public void testNetworkCapabilitiesCopyWithNoRedactions() {
        assumeTrue(isAtLeastS());

        final NetworkCapabilities netCap = createNetworkCapabilitiesWithTransportInfo();
        final NetworkCapabilities netCapWithNoRedactions =
                new NetworkCapabilities(netCap, NetworkCapabilities.REDACT_NONE);
        TestTransportInfo testTransportInfo =
                (TestTransportInfo) netCapWithNoRedactions.getTransportInfo();
        assertFalse(testTransportInfo.locationRedacted);
        assertFalse(testTransportInfo.localMacAddressRedacted);
        assertFalse(testTransportInfo.settingsRedacted);
    }

    @Test
    public void testNetworkCapabilitiesCopyWithoutLocationSensitiveFields() {
        assumeTrue(isAtLeastS());

        final NetworkCapabilities netCap = createNetworkCapabilitiesWithTransportInfo();
        final NetworkCapabilities netCapWithNoRedactions =
                new NetworkCapabilities(netCap, REDACT_FOR_ACCESS_FINE_LOCATION);
        TestTransportInfo testTransportInfo =
                (TestTransportInfo) netCapWithNoRedactions.getTransportInfo();
        assertTrue(testTransportInfo.locationRedacted);
        assertFalse(testTransportInfo.localMacAddressRedacted);
        assertFalse(testTransportInfo.settingsRedacted);
    }

    @Test
    public void testOemPaid() {
        NetworkCapabilities nc = new NetworkCapabilities();
        // By default OEM_PAID is neither in the required or forbidden lists and the network is not
        // restricted.
        if (isAtLeastS()) {
            assertFalse(nc.hasForbiddenCapability(NET_CAPABILITY_OEM_PAID));
        }
        assertFalse(nc.hasCapability(NET_CAPABILITY_OEM_PAID));
        nc.maybeMarkCapabilitiesRestricted();
        assertTrue(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // Adding OEM_PAID to capability list should make network restricted.
        nc.addCapability(NET_CAPABILITY_OEM_PAID);
        nc.addCapability(NET_CAPABILITY_INTERNET);  // Combine with unrestricted capability.
        nc.maybeMarkCapabilitiesRestricted();
        assertTrue(nc.hasCapability(NET_CAPABILITY_OEM_PAID));
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // Now let's make request for OEM_PAID network.
        NetworkCapabilities nr = new NetworkCapabilities();
        nr.addCapability(NET_CAPABILITY_OEM_PAID);
        nr.maybeMarkCapabilitiesRestricted();
        assertTrue(nr.satisfiedByNetworkCapabilities(nc));

        // Request fails for network with the default capabilities.
        assertFalse(nr.satisfiedByNetworkCapabilities(new NetworkCapabilities()));
    }

    @Test @IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
    public void testPrioritizeLatencyAndBandwidth() {
        NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_PRIORITIZE_LATENCY);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_PRIORITIZE_LATENCY);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_PRIORITIZE_BANDWIDTH);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_PRIORITIZE_BANDWIDTH);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
    }

    @Test
    public void testOemPrivate() {
        NetworkCapabilities nc = new NetworkCapabilities();
        // By default OEM_PRIVATE is neither in the required or forbidden lists and the network is
        // not restricted.
        assertFalse(nc.hasForbiddenCapability(NET_CAPABILITY_OEM_PRIVATE));
        assertFalse(nc.hasCapability(NET_CAPABILITY_OEM_PRIVATE));
        nc.maybeMarkCapabilitiesRestricted();
        assertTrue(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // Adding OEM_PRIVATE to capability list should make network restricted.
        nc.addCapability(NET_CAPABILITY_OEM_PRIVATE);
        nc.addCapability(NET_CAPABILITY_INTERNET);  // Combine with unrestricted capability.
        nc.maybeMarkCapabilitiesRestricted();
        assertTrue(nc.hasCapability(NET_CAPABILITY_OEM_PRIVATE));
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // Now let's make request for OEM_PRIVATE network.
        NetworkCapabilities nr = new NetworkCapabilities();
        nr.addCapability(NET_CAPABILITY_OEM_PRIVATE);
        nr.maybeMarkCapabilitiesRestricted();
        assertTrue(nr.satisfiedByNetworkCapabilities(nc));

        // Request fails for network with the default capabilities.
        assertFalse(nr.satisfiedByNetworkCapabilities(new NetworkCapabilities()));
    }

    @Test
    public void testForbiddenCapabilities() {
        NetworkCapabilities network = new NetworkCapabilities();

        NetworkCapabilities request = new NetworkCapabilities();
        assertTrue("Request: " + request + ", Network:" + network,
                request.satisfiedByNetworkCapabilities(network));

        // Requesting absence of capabilities that network doesn't have. Request should satisfy.
        request.addForbiddenCapability(NET_CAPABILITY_WIFI_P2P);
        request.addForbiddenCapability(NET_CAPABILITY_NOT_METERED);
        assertTrue(request.satisfiedByNetworkCapabilities(network));
        assertArrayEquals(new int[]{NET_CAPABILITY_WIFI_P2P,
                        NET_CAPABILITY_NOT_METERED},
                request.getForbiddenCapabilities());

        // This is a default capability, just want to make sure its there because we use it below.
        assertTrue(network.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // Verify that adding forbidden capability will effectively remove it from capability list.
        request.addForbiddenCapability(NET_CAPABILITY_NOT_RESTRICTED);
        assertTrue(request.hasForbiddenCapability(NET_CAPABILITY_NOT_RESTRICTED));
        assertFalse(request.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // Now this request won't be satisfied because network contains NOT_RESTRICTED.
        assertFalse(request.satisfiedByNetworkCapabilities(network));
        network.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        assertTrue(request.satisfiedByNetworkCapabilities(network));

        // Verify that adding capability will effectively remove it from forbidden list
        request.addCapability(NET_CAPABILITY_NOT_RESTRICTED);
        assertTrue(request.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        assertFalse(request.hasForbiddenCapability(NET_CAPABILITY_NOT_RESTRICTED));

        assertFalse(request.satisfiedByNetworkCapabilities(network));
        network.addCapability(NET_CAPABILITY_NOT_RESTRICTED);
        assertTrue(request.satisfiedByNetworkCapabilities(network));
    }

    @Test
    public void testConnectivityManagedCapabilities() {
        NetworkCapabilities nc = new NetworkCapabilities();
        assertFalse(nc.hasConnectivityManagedCapability());
        // Check every single system managed capability.
        nc.addCapability(NET_CAPABILITY_CAPTIVE_PORTAL);
        assertTrue(nc.hasConnectivityManagedCapability());
        nc.removeCapability(NET_CAPABILITY_CAPTIVE_PORTAL);
        nc.addCapability(NET_CAPABILITY_FOREGROUND);
        assertTrue(nc.hasConnectivityManagedCapability());
        nc.removeCapability(NET_CAPABILITY_FOREGROUND);
        nc.addCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY);
        assertTrue(nc.hasConnectivityManagedCapability());
        nc.removeCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY);
        nc.addCapability(NET_CAPABILITY_VALIDATED);
        assertTrue(nc.hasConnectivityManagedCapability());
    }

    @Test
    public void testEqualsNetCapabilities() {
        NetworkCapabilities nc1 = new NetworkCapabilities();
        NetworkCapabilities nc2 = new NetworkCapabilities();
        assertTrue(nc1.equalsNetCapabilities(nc2));
        assertEquals(nc1, nc2);

        nc1.addCapability(NET_CAPABILITY_MMS);
        assertFalse(nc1.equalsNetCapabilities(nc2));
        assertNotEquals(nc1, nc2);
        nc2.addCapability(NET_CAPABILITY_MMS);
        assertTrue(nc1.equalsNetCapabilities(nc2));
        assertEquals(nc1, nc2);

        if (isAtLeastS()) {
            nc1.addForbiddenCapability(NET_CAPABILITY_INTERNET);
            assertFalse(nc1.equalsNetCapabilities(nc2));
            nc2.addForbiddenCapability(NET_CAPABILITY_INTERNET);
            assertTrue(nc1.equalsNetCapabilities(nc2));

            // Remove a required capability doesn't affect forbidden capabilities.
            // This is a behaviour change from R to S.
            nc1.removeCapability(NET_CAPABILITY_INTERNET);
            assertTrue(nc1.equalsNetCapabilities(nc2));

            nc1.removeForbiddenCapability(NET_CAPABILITY_INTERNET);
            assertFalse(nc1.equalsNetCapabilities(nc2));
            nc2.removeForbiddenCapability(NET_CAPABILITY_INTERNET);
            assertTrue(nc1.equalsNetCapabilities(nc2));
        }
    }

    @Test
    public void testSSID() {
        NetworkCapabilities nc1 = new NetworkCapabilities();
        NetworkCapabilities nc2 = new NetworkCapabilities();
        assertTrue(nc2.satisfiedBySSID(nc1));

        nc1.setSSID(TEST_SSID);
        assertTrue(nc2.satisfiedBySSID(nc1));
        nc2.setSSID("different " + TEST_SSID);
        assertFalse(nc2.satisfiedBySSID(nc1));

        assertTrue(nc1.satisfiedByImmutableNetworkCapabilities(nc2));
        assertFalse(nc1.satisfiedByNetworkCapabilities(nc2));
    }

    private ArraySet<Range<Integer>> uidRanges(int from, int to) {
        final ArraySet<Range<Integer>> range = new ArraySet<>(1);
        range.add(uidRange(from, to));
        return range;
    }

    private Range<Integer> uidRange(int from, int to) {
        return new Range<Integer>(from, to);
    }

    @Test
    public void testSetAdministratorUids() {
        NetworkCapabilities nc =
                new NetworkCapabilities().setAdministratorUids(new int[] {2, 1, 3});

        assertArrayEquals(new int[] {1, 2, 3}, nc.getAdministratorUids());
    }

    @Test
    public void testSetAdministratorUidsWithDuplicates() {
        try {
            new NetworkCapabilities().setAdministratorUids(new int[] {1, 1});
            fail("Expected IllegalArgumentException for duplicate uids");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetCapabilities() {
        final int[] REQUIRED_CAPABILITIES = new int[] {
                NET_CAPABILITY_INTERNET, NET_CAPABILITY_NOT_VPN };

        NetworkCapabilities nc1 = new NetworkCapabilities();
        NetworkCapabilities nc2 = new NetworkCapabilities();

        nc1.setCapabilities(REQUIRED_CAPABILITIES);
        assertArrayEquals(REQUIRED_CAPABILITIES, nc1.getCapabilities());

        // Verify that setting and adding capabilities leads to the same object state.
        nc2.clearAll();
        for (int cap : REQUIRED_CAPABILITIES) {
            nc2.addCapability(cap);
        }
        assertEquals(nc1, nc2);

        if (isAtLeastS()) {
            final int[] forbiddenCapabilities = new int[]{
                    NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_NOT_RESTRICTED };

            nc1.setCapabilities(REQUIRED_CAPABILITIES, forbiddenCapabilities);
            assertArrayEquals(REQUIRED_CAPABILITIES, nc1.getCapabilities());
            assertArrayEquals(forbiddenCapabilities, nc1.getForbiddenCapabilities());

            nc2.clearAll();
            for (int cap : REQUIRED_CAPABILITIES) {
                nc2.addCapability(cap);
            }
            for (int cap : forbiddenCapabilities) {
                nc2.addForbiddenCapability(cap);
            }
            assertEquals(nc1, nc2);
        }
    }

    @Test
    public void testUnderlyingNetworks() {
        assumeTrue(isAtLeastT());
        final NetworkCapabilities nc = new NetworkCapabilities();
        final Network network1 = new Network(100);
        final Network network2 = new Network(101);
        final ArrayList<Network> inputNetworks = new ArrayList<>();
        inputNetworks.add(network1);
        inputNetworks.add(network2);
        nc.setUnderlyingNetworks(inputNetworks);
        final ArrayList<Network> outputNetworks = new ArrayList<>(nc.getUnderlyingNetworks());
        assertEquals(network1, outputNetworks.get(0));
        assertEquals(network2, outputNetworks.get(1));
        nc.setUnderlyingNetworks(null);
        assertNull(nc.getUnderlyingNetworks());
    }

    @Test
    public void testEqualsForUnderlyingNetworks() {
        assumeTrue(isAtLeastT());
        final NetworkCapabilities nc1 = new NetworkCapabilities();
        final NetworkCapabilities nc2 = new NetworkCapabilities();
        assertEquals(nc1, nc2);
        final Network network = new Network(100);
        final ArrayList<Network> inputNetworks = new ArrayList<>();
        final ArrayList<Network> emptyList = new ArrayList<>();
        inputNetworks.add(network);
        nc1.setUnderlyingNetworks(inputNetworks);
        assertNotEquals(nc1, nc2);
        nc2.setUnderlyingNetworks(inputNetworks);
        assertEquals(nc1, nc2);
        nc1.setUnderlyingNetworks(emptyList);
        assertNotEquals(nc1, nc2);
        nc2.setUnderlyingNetworks(emptyList);
        assertEquals(nc1, nc2);
        nc1.setUnderlyingNetworks(null);
        assertNotEquals(nc1, nc2);
        nc2.setUnderlyingNetworks(null);
        assertEquals(nc1, nc2);
    }

    @Test
    public void testSetNetworkSpecifierOnMultiTransportNc() {
        // Sequence 1: Transport + Transport + NetworkSpecifier
        NetworkCapabilities.Builder nc1 = new NetworkCapabilities.Builder();
        nc1.addTransportType(TRANSPORT_CELLULAR).addTransportType(TRANSPORT_WIFI);
        final NetworkSpecifier specifier = CompatUtil.makeEthernetNetworkSpecifier("eth0");
        assertThrows("Cannot set NetworkSpecifier on a NetworkCapability with multiple transports!",
                IllegalStateException.class,
                () -> nc1.build().setNetworkSpecifier(specifier));
        assertThrows("Cannot set NetworkSpecifier on a NetworkCapability with multiple transports!",
                IllegalStateException.class,
                () -> nc1.setNetworkSpecifier(specifier));

        // Sequence 2: Transport + NetworkSpecifier + Transport
        NetworkCapabilities.Builder nc2 = new NetworkCapabilities.Builder();
        nc2.addTransportType(TRANSPORT_CELLULAR).setNetworkSpecifier(specifier);

        assertThrows("Cannot set a second TransportType of a network which has a NetworkSpecifier!",
                IllegalStateException.class,
                () -> nc2.build().addTransportType(TRANSPORT_WIFI));
        assertThrows("Cannot set a second TransportType of a network which has a NetworkSpecifier!",
                IllegalStateException.class,
                () -> nc2.addTransportType(TRANSPORT_WIFI));
    }

    @Test
    public void testSetNetworkSpecifierWithCellularAndSatelliteMultiTransportNc() {
        final TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier(1);
        NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addTransportType(TRANSPORT_SATELLITE)
                .setNetworkSpecifier(specifier)
                .build();
        // Adding a specifier did not crash with 2 transports if it is cellular + satellite
        assertEquals(specifier, nc.getNetworkSpecifier());
    }

    @Test
    public void testSetNetworkSpecifierWithWifiAndSatelliteMultiTransportNc() {
        final TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier(1);
        NetworkCapabilities.Builder nc1 = new NetworkCapabilities.Builder();
        nc1.addTransportType(TRANSPORT_SATELLITE).addTransportType(TRANSPORT_WIFI);
        // Adding multiple transports specifier to crash, apart from cellular + satellite
        // combination
        assertThrows("Cannot set NetworkSpecifier on a NetworkCapability with multiple transports!",
                IllegalStateException.class,
                () -> nc1.build().setNetworkSpecifier(specifier));
        assertThrows("Cannot set NetworkSpecifier on a NetworkCapability with multiple transports!",
                IllegalStateException.class,
                () -> nc1.setNetworkSpecifier(specifier));
    }

    @Test
    public void testSetNetworkSpecifierOnTestWithCellularAndSatelliteMultiTransportNc() {
        final TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier(1);
        NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_TEST)
                .addTransportType(TRANSPORT_CELLULAR)
                .addTransportType(TRANSPORT_SATELLITE)
                .setNetworkSpecifier(specifier)
                .build();
        // Adding a specifier did not crash with 3 transports , TEST + CELLULAR + SATELLITE and if
        // one is test
        assertEquals(specifier, nc.getNetworkSpecifier());
    }

    @Test
    public void testSetNetworkSpecifierOnTestMultiTransportNc() {
        final NetworkSpecifier specifier = CompatUtil.makeEthernetNetworkSpecifier("eth0");
        NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_TEST)
                .addTransportType(TRANSPORT_ETHERNET)
                .setNetworkSpecifier(specifier)
                .build();
        // Adding a specifier did not crash with 2 transports if one is TEST
        assertEquals(specifier, nc.getNetworkSpecifier());
    }

    @Test
    public void testSetTransportInfoOnMultiTransportNc() {
        // Sequence 1: Transport + Transport + TransportInfo
        NetworkCapabilities nc1 = new NetworkCapabilities();
        nc1.addTransportType(TRANSPORT_CELLULAR).addTransportType(TRANSPORT_WIFI)
                .setTransportInfo(new TestTransportInfo());

        // Sequence 2: Transport + NetworkSpecifier + Transport
        NetworkCapabilities nc2 = new NetworkCapabilities();
        nc2.addTransportType(TRANSPORT_CELLULAR).setTransportInfo(new TestTransportInfo())
                .addTransportType(TRANSPORT_WIFI);
    }

    @Test
    public void testSet() {
        NetworkCapabilities nc1 = new NetworkCapabilities();
        NetworkCapabilities nc2 = new NetworkCapabilities();

        if (isAtLeastS()) {
            nc1.addForbiddenCapability(NET_CAPABILITY_CAPTIVE_PORTAL);
        }
        nc1.addCapability(NET_CAPABILITY_NOT_ROAMING);
        assertNotEquals(nc1, nc2);
        nc2.set(nc1);
        assertEquals(nc1, nc2);
        assertTrue(nc2.hasCapability(NET_CAPABILITY_NOT_ROAMING));
        if (isAtLeastS()) {
            assertTrue(nc2.hasForbiddenCapability(NET_CAPABILITY_CAPTIVE_PORTAL));
        }

        if (isAtLeastS()) {
            // This will effectively move NOT_ROAMING capability from required to forbidden for nc1.
            nc1.addForbiddenCapability(NET_CAPABILITY_NOT_ROAMING);
        }
        nc1.setSSID(TEST_SSID);
        nc2.set(nc1);
        assertEquals(nc1, nc2);
        if (isAtLeastS()) {
            // Contrary to combineCapabilities, set() will have removed the NOT_ROAMING capability
            // from nc2.
            assertFalse(nc2.hasCapability(NET_CAPABILITY_NOT_ROAMING));
            assertTrue(nc2.hasForbiddenCapability(NET_CAPABILITY_NOT_ROAMING));
        }

        assertTrue(TEST_SSID.equals(nc2.getSsid()));
        nc1.setSSID(DIFFERENT_TEST_SSID);
        nc2.set(nc1);
        assertEquals(nc1, nc2);
        assertTrue(DIFFERENT_TEST_SSID.equals(nc2.getSsid()));

        if (isAtLeastS()) {
            nc1.setUids(uidRanges(10, 13));
        } else {
            nc1.setUids(null);
        }
        nc2.set(nc1);  // Overwrites, as opposed to combineCapabilities
        assertEquals(nc1, nc2);

        if (isAtLeastS()) {
            assertThrows(NullPointerException.class, () -> nc1.setSubscriptionIds(null));
            nc1.setSubscriptionIds(Set.of());
            nc2.set(nc1);
            assertEquals(nc1, nc2);

            nc1.setSubscriptionIds(Set.of(TEST_SUBID1));
            nc2.set(nc1);
            assertEquals(nc1, nc2);

            nc2.setSubscriptionIds(Set.of(TEST_SUBID2, TEST_SUBID1));
            nc2.set(nc1);
            assertEquals(nc1, nc2);

            nc2.setSubscriptionIds(Set.of(TEST_SUBID3, TEST_SUBID2));
            assertNotEquals(nc1, nc2);
        }
    }

    @Test
    public void testGetTransportTypes() {
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.addTransportType(TRANSPORT_CELLULAR);
        nc.addTransportType(TRANSPORT_WIFI);
        nc.addTransportType(TRANSPORT_VPN);
        nc.addTransportType(TRANSPORT_TEST);

        final int[] transportTypes = nc.getTransportTypes();
        assertEquals(4, transportTypes.length);
        assertEquals(TRANSPORT_CELLULAR, transportTypes[0]);
        assertEquals(TRANSPORT_WIFI, transportTypes[1]);
        assertEquals(TRANSPORT_VPN, transportTypes[2]);
        assertEquals(TRANSPORT_TEST, transportTypes[3]);
    }

    @Test
    public void testTelephonyNetworkSpecifier() {
        final TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier(1);
        final NetworkCapabilities nc1 = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();
        assertEquals(specifier, nc1.getNetworkSpecifier());
        try {
            final NetworkCapabilities nc2 = new NetworkCapabilities.Builder()
                    .setNetworkSpecifier(specifier)
                    .build();
            fail("Must have a single transport type. Without transport type or multiple transport"
                    + " types is invalid.");
        } catch (IllegalStateException expected) { }
    }

    @Test @IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
    public void testEnterpriseId() {
        final NetworkCapabilities nc1 = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_ENTERPRISE)
                .addEnterpriseId(NET_ENTERPRISE_ID_1)
                .build();
        assertEquals(1, nc1.getEnterpriseIds().length);
        assertEquals(NET_ENTERPRISE_ID_1,
                nc1.getEnterpriseIds()[0]);
        final NetworkCapabilities nc2 = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_ENTERPRISE)
                .addEnterpriseId(NET_ENTERPRISE_ID_1)
                .addEnterpriseId(NET_ENTERPRISE_ID_2)
                .build();
        assertEquals(2, nc2.getEnterpriseIds().length);
        assertEquals(NET_ENTERPRISE_ID_1,
                nc2.getEnterpriseIds()[0]);
        assertEquals(NET_ENTERPRISE_ID_2,
                nc2.getEnterpriseIds()[1]);
        final NetworkCapabilities nc3 = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_ENTERPRISE)
                .addEnterpriseId(NET_ENTERPRISE_ID_1)
                .addEnterpriseId(NET_ENTERPRISE_ID_2)
                .addEnterpriseId(NET_ENTERPRISE_ID_3)
                .addEnterpriseId(NET_ENTERPRISE_ID_4)
                .addEnterpriseId(NET_ENTERPRISE_ID_5)
                .build();
        assertEquals(5, nc3.getEnterpriseIds().length);
        assertEquals(NET_ENTERPRISE_ID_1,
                nc3.getEnterpriseIds()[0]);
        assertEquals(NET_ENTERPRISE_ID_2,
                nc3.getEnterpriseIds()[1]);
        assertEquals(NET_ENTERPRISE_ID_3,
                nc3.getEnterpriseIds()[2]);
        assertEquals(NET_ENTERPRISE_ID_4,
                nc3.getEnterpriseIds()[3]);
        assertEquals(NET_ENTERPRISE_ID_5,
                nc3.getEnterpriseIds()[4]);

        final Class<IllegalArgumentException> illegalArgumentExceptionClass =
                IllegalArgumentException.class;
        assertThrows(illegalArgumentExceptionClass, () -> new NetworkCapabilities.Builder()
                .addEnterpriseId(6)
                .build());
        assertThrows(illegalArgumentExceptionClass, () -> new NetworkCapabilities.Builder()
                .removeEnterpriseId(6)
                .build());

        final Class<IllegalStateException> illegalStateException =
                IllegalStateException.class;
        assertThrows(illegalStateException, () -> new NetworkCapabilities.Builder()
                .addEnterpriseId(NET_ENTERPRISE_ID_1)
                .build());

        final NetworkCapabilities nc4 = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_ENTERPRISE)
                .addEnterpriseId(NET_ENTERPRISE_ID_1)
                .addEnterpriseId(NET_ENTERPRISE_ID_2)
                .removeEnterpriseId(NET_ENTERPRISE_ID_1)
                .removeEnterpriseId(NET_ENTERPRISE_ID_2)
                .build();
        assertEquals(1, nc4.getEnterpriseIds().length);
        assertTrue(nc4.hasEnterpriseId(NET_ENTERPRISE_ID_1));

        final NetworkCapabilities nc5 = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_CBS)
                .addEnterpriseId(NET_ENTERPRISE_ID_1)
                .addEnterpriseId(NET_ENTERPRISE_ID_2)
                .removeEnterpriseId(NET_ENTERPRISE_ID_1)
                .removeEnterpriseId(NET_ENTERPRISE_ID_2)
                .build();

        assertTrue(nc4.satisfiedByNetworkCapabilities(nc1));
        assertTrue(nc1.satisfiedByNetworkCapabilities(nc4));

        assertFalse(nc3.satisfiedByNetworkCapabilities(nc2));
        assertTrue(nc2.satisfiedByNetworkCapabilities(nc3));

        assertFalse(nc1.satisfiedByNetworkCapabilities(nc5));
        assertFalse(nc5.satisfiedByNetworkCapabilities(nc1));
    }

    @Test
    public void testWifiAwareNetworkSpecifier() {
        final NetworkCapabilities nc = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI_AWARE);
        // If NetworkSpecifier is not set, the default value is null.
        assertNull(nc.getNetworkSpecifier());
        final WifiAwareNetworkSpecifier specifier = new WifiAwareNetworkSpecifier.Builder(
                mDiscoverySession, mPeerHandle).build();
        nc.setNetworkSpecifier(specifier);
        assertEquals(specifier, nc.getNetworkSpecifier());
    }

    @Test
    public void testAdministratorUidsAndOwnerUid() {
        // Test default owner uid.
        // If the owner uid is not set, the default value should be Process.INVALID_UID.
        final NetworkCapabilities nc1 = new NetworkCapabilities.Builder().build();
        assertEquals(INVALID_UID, nc1.getOwnerUid());
        // Test setAdministratorUids and getAdministratorUids.
        final int[] administratorUids = {1001, 10001};
        final NetworkCapabilities nc2 = new NetworkCapabilities.Builder()
                .setAdministratorUids(administratorUids)
                .build();
        assertTrue(Arrays.equals(administratorUids, nc2.getAdministratorUids()));
        // Test setOwnerUid and getOwnerUid.
        // The owner UID must be included in administrator UIDs, or throw IllegalStateException.
        try {
            final NetworkCapabilities nc3 = new NetworkCapabilities.Builder()
                    .setOwnerUid(1001)
                    .build();
            fail("The owner UID must be included in administrator UIDs.");
        } catch (IllegalStateException expected) { }
        final NetworkCapabilities nc4 = new NetworkCapabilities.Builder()
                .setAdministratorUids(administratorUids)
                .setOwnerUid(1001)
                .build();
        assertEquals(1001, nc4.getOwnerUid());
        try {
            final NetworkCapabilities nc5 = new NetworkCapabilities.Builder()
                    .setAdministratorUids(null)
                    .build();
            fail("Should not set null into setAdministratorUids");
        } catch (NullPointerException expected) { }
    }

    private static NetworkCapabilities capsWithSubIds(Integer ... subIds) {
        // Since the NetworkRequest would put NOT_VCN_MANAGED capabilities in general, for
        // every NetworkCapabilities that simulates networks needs to add it too in order to
        // satisfy these requests.
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .setSubscriptionIds(new ArraySet<>(subIds)).build();
        assertEquals(new ArraySet<>(subIds), nc.getSubscriptionIds());
        return nc;
    }

    @Test
    public void testSubIds() throws Exception {
        final NetworkCapabilities ncWithoutId = capsWithSubIds();
        final NetworkCapabilities ncWithId = capsWithSubIds(TEST_SUBID1);
        final NetworkCapabilities ncWithOtherIds = capsWithSubIds(TEST_SUBID1, TEST_SUBID3);
        final NetworkCapabilities ncWithoutRequestedIds = capsWithSubIds(TEST_SUBID3);

        final NetworkRequest requestWithoutId = new NetworkRequest.Builder().build();
        assertEmpty(requestWithoutId.networkCapabilities.getSubscriptionIds());
        final NetworkRequest requestWithIds = new NetworkRequest.Builder()
                .setSubscriptionIds(Set.of(TEST_SUBID1, TEST_SUBID2)).build();
        assertEquals(Set.of(TEST_SUBID1, TEST_SUBID2),
                requestWithIds.networkCapabilities.getSubscriptionIds());

        assertFalse(requestWithIds.canBeSatisfiedBy(ncWithoutId));
        assertTrue(requestWithIds.canBeSatisfiedBy(ncWithOtherIds));
        assertFalse(requestWithIds.canBeSatisfiedBy(ncWithoutRequestedIds));
        assertTrue(requestWithIds.canBeSatisfiedBy(ncWithId));
        assertTrue(requestWithoutId.canBeSatisfiedBy(ncWithoutId));
        assertTrue(requestWithoutId.canBeSatisfiedBy(ncWithId));
    }

    @Test
    public void testEqualsSubIds() throws Exception {
        assertEquals(capsWithSubIds(), capsWithSubIds());
        assertNotEquals(capsWithSubIds(), capsWithSubIds(TEST_SUBID1));
        assertEquals(capsWithSubIds(TEST_SUBID1), capsWithSubIds(TEST_SUBID1));
        assertNotEquals(capsWithSubIds(TEST_SUBID1), capsWithSubIds(TEST_SUBID2));
        assertNotEquals(capsWithSubIds(TEST_SUBID1), capsWithSubIds(TEST_SUBID2, TEST_SUBID1));
        assertEquals(capsWithSubIds(TEST_SUBID1, TEST_SUBID2),
                capsWithSubIds(TEST_SUBID2, TEST_SUBID1));
    }

    @Test
    public void testLinkBandwidthKbps() {
        final NetworkCapabilities nc = new NetworkCapabilities();
        // The default value of LinkDown/UpstreamBandwidthKbps should be LINK_BANDWIDTH_UNSPECIFIED.
        assertEquals(LINK_BANDWIDTH_UNSPECIFIED, nc.getLinkDownstreamBandwidthKbps());
        assertEquals(LINK_BANDWIDTH_UNSPECIFIED, nc.getLinkUpstreamBandwidthKbps());
        nc.setLinkDownstreamBandwidthKbps(512);
        nc.setLinkUpstreamBandwidthKbps(128);
        assertEquals(512, nc.getLinkDownstreamBandwidthKbps());
        assertNotEquals(128, nc.getLinkDownstreamBandwidthKbps());
        assertEquals(128, nc.getLinkUpstreamBandwidthKbps());
        assertNotEquals(512, nc.getLinkUpstreamBandwidthKbps());
    }

    private int getMaxTransport() {
        if (!isAtLeastS() && MAX_TRANSPORT == TRANSPORT_USB) return MAX_TRANSPORT - 1;
        return MAX_TRANSPORT;
    }

    @Test
    public void testSignalStrength() {
        final NetworkCapabilities nc = new NetworkCapabilities();
        // The default value of signal strength should be SIGNAL_STRENGTH_UNSPECIFIED.
        assertEquals(SIGNAL_STRENGTH_UNSPECIFIED, nc.getSignalStrength());
        nc.setSignalStrength(-80);
        assertEquals(-80, nc.getSignalStrength());
        assertNotEquals(-50, nc.getSignalStrength());
    }

    private void assertNoTransport(NetworkCapabilities nc) {
        for (int i = MIN_TRANSPORT; i <= getMaxTransport(); i++) {
            assertFalse(nc.hasTransport(i));
        }
    }

    // Checks that all transport types from MIN_TRANSPORT to maxTransportType are set and all
    // transport types from maxTransportType + 1 to MAX_TRANSPORT are not set when positiveSequence
    // is true. If positiveSequence is false, then the check sequence is opposite.
    private void checkCurrentTransportTypes(NetworkCapabilities nc, int maxTransportType,
            boolean positiveSequence) {
        for (int i = MIN_TRANSPORT; i <= maxTransportType; i++) {
            if (positiveSequence) {
                assertTrue(nc.hasTransport(i));
            } else {
                assertFalse(nc.hasTransport(i));
            }
        }
        for (int i = getMaxTransport(); i > maxTransportType; i--) {
            if (positiveSequence) {
                assertFalse(nc.hasTransport(i));
            } else {
                assertTrue(nc.hasTransport(i));
            }
        }
    }

    @Test
    public void testMultipleTransportTypes() {
        final NetworkCapabilities nc = new NetworkCapabilities();
        assertNoTransport(nc);
        // Test adding multiple transport types.
        for (int i = MIN_TRANSPORT; i <= getMaxTransport(); i++) {
            nc.addTransportType(i);
            checkCurrentTransportTypes(nc, i, true /* positiveSequence */);
        }
        // Test removing multiple transport types.
        for (int i = MIN_TRANSPORT; i <= getMaxTransport(); i++) {
            nc.removeTransportType(i);
            checkCurrentTransportTypes(nc, i, false /* positiveSequence */);
        }
        assertNoTransport(nc);
        nc.addTransportType(TRANSPORT_WIFI);
        assertTrue(nc.hasTransport(TRANSPORT_WIFI));
        assertFalse(nc.hasTransport(TRANSPORT_VPN));
        nc.addTransportType(TRANSPORT_VPN);
        assertTrue(nc.hasTransport(TRANSPORT_WIFI));
        assertTrue(nc.hasTransport(TRANSPORT_VPN));
        nc.removeTransportType(TRANSPORT_WIFI);
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        assertTrue(nc.hasTransport(TRANSPORT_VPN));
        nc.removeTransportType(TRANSPORT_VPN);
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        assertFalse(nc.hasTransport(TRANSPORT_VPN));
        assertNoTransport(nc);
    }

    @Test
    public void testAddAndRemoveTransportType() {
        final NetworkCapabilities nc = new NetworkCapabilities();
        try {
            nc.addTransportType(-1);
            fail("Should not set invalid transport type into addTransportType");
        } catch (IllegalArgumentException expected) { }
        try {
            nc.removeTransportType(-1);
            fail("Should not set invalid transport type into removeTransportType");
        } catch (IllegalArgumentException e) { }
    }

    /**
     * Test TransportInfo to verify redaction mechanism.
     */
    private static class TestTransportInfo implements TransportInfo {
        public final boolean locationRedacted;
        public final boolean localMacAddressRedacted;
        public final boolean settingsRedacted;

        TestTransportInfo() {
            locationRedacted = false;
            localMacAddressRedacted = false;
            settingsRedacted = false;
        }

        TestTransportInfo(boolean locationRedacted,
                boolean localMacAddressRedacted,
                boolean settingsRedacted) {
            this.locationRedacted = locationRedacted;
            this.localMacAddressRedacted =
                    localMacAddressRedacted;
            this.settingsRedacted = settingsRedacted;
        }

        @Override
        public TransportInfo makeCopy(@NetworkCapabilities.RedactionType long redactions) {
            return new TestTransportInfo(
                    (redactions & NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION) != 0,
                    (redactions & REDACT_FOR_LOCAL_MAC_ADDRESS) != 0,
                    (redactions & REDACT_FOR_NETWORK_SETTINGS) != 0
            );
        }

        @Override
        public @NetworkCapabilities.RedactionType long getApplicableRedactions() {
            return REDACT_FOR_ACCESS_FINE_LOCATION | REDACT_FOR_LOCAL_MAC_ADDRESS
                    | REDACT_FOR_NETWORK_SETTINGS;
        }
    }

    @Test
    public void testBuilder() {
        final int ownerUid = 1001;
        final int signalStrength = -80;
        final int requestUid = 10100;
        final int[] administratorUids = {ownerUid, 10001};
        final TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier(1);
        final TransportInfo transportInfo = new TransportInfo() {};
        final String ssid = "TEST_SSID";
        final String packageName = "com.google.test.networkcapabilities";
        final NetworkCapabilities.Builder capBuilder = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addTransportType(TRANSPORT_CELLULAR)
                .removeTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_EIMS)
                .addCapability(NET_CAPABILITY_CBS)
                .removeCapability(NET_CAPABILITY_CBS)
                .setAdministratorUids(administratorUids)
                .setOwnerUid(ownerUid)
                .setLinkDownstreamBandwidthKbps(512)
                .setLinkUpstreamBandwidthKbps(128)
                .setNetworkSpecifier(specifier)
                .setTransportInfo(transportInfo)
                .setSignalStrength(signalStrength)
                .setSsid(ssid)
                .setRequestorUid(requestUid)
                .setRequestorPackageName(packageName);
        final Network network1 = new Network(100);
        final Network network2 = new Network(101);
        final List<Network> inputNetworks = List.of(network1, network2);
        if (isAtLeastT()) {
            capBuilder.setUnderlyingNetworks(inputNetworks);
        }
        final NetworkCapabilities nc = capBuilder.build();
        assertEquals(1, nc.getTransportTypes().length);
        assertEquals(TRANSPORT_WIFI, nc.getTransportTypes()[0]);
        assertTrue(nc.hasCapability(NET_CAPABILITY_EIMS));
        assertFalse(nc.hasCapability(NET_CAPABILITY_CBS));
        assertTrue(Arrays.equals(administratorUids, nc.getAdministratorUids()));
        assertEquals(ownerUid, nc.getOwnerUid());
        assertEquals(512, nc.getLinkDownstreamBandwidthKbps());
        assertNotEquals(128, nc.getLinkDownstreamBandwidthKbps());
        assertEquals(128, nc.getLinkUpstreamBandwidthKbps());
        assertNotEquals(512, nc.getLinkUpstreamBandwidthKbps());
        assertEquals(specifier, nc.getNetworkSpecifier());
        assertEquals(transportInfo, nc.getTransportInfo());
        assertEquals(signalStrength, nc.getSignalStrength());
        assertNotEquals(-50, nc.getSignalStrength());
        assertEquals(ssid, nc.getSsid());
        assertEquals(requestUid, nc.getRequestorUid());
        assertEquals(packageName, nc.getRequestorPackageName());
        if (isAtLeastT()) {
            final List<Network> outputNetworks = nc.getUnderlyingNetworks();
            assertEquals(network1, outputNetworks.get(0));
            assertEquals(network2, outputNetworks.get(1));
        }
        // Cannot assign null into NetworkCapabilities.Builder
        try {
            final NetworkCapabilities.Builder builder = new NetworkCapabilities.Builder(null);
            fail("Should not set null into NetworkCapabilities.Builder");
        } catch (NullPointerException expected) { }
        assertEquals(nc, new NetworkCapabilities.Builder(nc).build());

        if (isAtLeastS()) {
            final NetworkCapabilities nc2 = new NetworkCapabilities.Builder()
                    .setSubscriptionIds(Set.of(TEST_SUBID1)).build();
            assertEquals(Set.of(TEST_SUBID1), nc2.getSubscriptionIds());
        }
    }

    @Test
    public void testBuilderWithoutDefaultCap() {
        final NetworkCapabilities nc =
                NetworkCapabilities.Builder.withoutDefaultCapabilities().build();
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        assertFalse(nc.hasCapability(NET_CAPABILITY_TRUSTED));
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_VPN));
        // Ensure test case fails if new net cap is added into default cap but no update here.
        assertEquals(0, nc.getCapabilities().length);
    }

    @Test
    public void testRestrictCapabilitiesForTestNetworkByNotOwnerWithNonRestrictedNc() {
        testRestrictCapabilitiesForTestNetworkWithNonRestrictedNc(false /* isOwner */);
    }

    @Test
    public void testRestrictCapabilitiesForTestNetworkByOwnerWithNonRestrictedNc() {
        testRestrictCapabilitiesForTestNetworkWithNonRestrictedNc(true /* isOwner */);
    }

    private void testRestrictCapabilitiesForTestNetworkWithNonRestrictedNc(boolean isOwner) {
        final int ownerUid = 1234;
        final int signalStrength = -80;
        final int[] administratorUids = {1001, ownerUid};
        final TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier(TEST_SUBID1);
        final TransportInfo transportInfo = new TransportInfo() {};
        final NetworkCapabilities nonRestrictedNc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_MMS)
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .setAdministratorUids(administratorUids)
                .setOwnerUid(ownerUid)
                .setNetworkSpecifier(specifier)
                .setSignalStrength(signalStrength)
                .setTransportInfo(transportInfo)
                .setSubscriptionIds(Set.of(TEST_SUBID1)).build();
        final int creatorUid = isOwner ? ownerUid : INVALID_UID;
        nonRestrictedNc.restrictCapabilitiesForTestNetwork(creatorUid);

        final NetworkCapabilities.Builder expectedNcBuilder = new NetworkCapabilities.Builder();
        // Non-UNRESTRICTED_TEST_NETWORKS_ALLOWED_TRANSPORTS will be removed and TRANSPORT_TEST will
        // be appended for non-restricted net cap.
        expectedNcBuilder.addTransportType(TRANSPORT_TEST);
        // Only TEST_NETWORKS_ALLOWED_CAPABILITIES will be kept. SubIds are only allowed for Test
        // Networks that only declare TRANSPORT_TEST.
        expectedNcBuilder.addCapability(NET_CAPABILITY_NOT_METERED)
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .setSubscriptionIds(Set.of(TEST_SUBID1));

        expectedNcBuilder.setNetworkSpecifier(specifier)
                .setSignalStrength(signalStrength).setTransportInfo(transportInfo);
        if (creatorUid == ownerUid) {
            // Only retain the owner and administrator UIDs if they match the app registering the
            // remote caller that registered the network.
            expectedNcBuilder.setAdministratorUids(new int[]{ownerUid}).setOwnerUid(ownerUid);
        }

        assertEquals(expectedNcBuilder.build(), nonRestrictedNc);
    }

    @Test
    public void testRestrictCapabilitiesForTestNetworkRestrictedNc_NotOwner_NotCell() {
        testRestrictCapabilitiesForTestNetworkWithRestrictedNc(
                false /* isOwner */, false /* isCell */);
    }

    @Test
    public void testRestrictCapabilitiesForTestNetworkRestrictedNc_Owner_NotCell() {
        testRestrictCapabilitiesForTestNetworkWithRestrictedNc(
                true /* isOwner */, false /* isCell */);
    }

    @Test
    public void testRestrictCapabilitiesForTestNetworkRestrictedNc_NotOwner_Cell() {
        testRestrictCapabilitiesForTestNetworkWithRestrictedNc(
                false /* isOwner */, true /* isCell */);
    }

    @Test
    public void testRestrictCapabilitiesForTestNetworkRestrictedNc_Owner_Cell() {
        testRestrictCapabilitiesForTestNetworkWithRestrictedNc(
                true /* isOwner */, false /* isCell */);
    }

    private void testRestrictCapabilitiesForTestNetworkWithRestrictedNc(
            boolean isOwner, boolean isCell) {
        final int ownerUid = 1234;
        final int signalStrength = -80;
        final int[] administratorUids = {1001, ownerUid};
        final TransportInfo transportInfo = new TransportInfo() {};
        // No NetworkSpecifier is set because after performing restrictCapabilitiesForTestNetwork
        // the networkCapabilities will contain more than one transport type. However,
        // networkCapabilities must have a single transport specified to use NetworkSpecifier. Thus,
        // do not verify this part since it's verified in other tests.
        final NetworkCapabilities.Builder restrictedNcBuilder = new NetworkCapabilities.Builder()
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NET_CAPABILITY_MMS)
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .setAdministratorUids(administratorUids)
                .setOwnerUid(ownerUid)
                .setSignalStrength(signalStrength)
                .setTransportInfo(transportInfo)
                .setSubscriptionIds(Set.of(TEST_SUBID1));
        for (int cap : TEST_NETWORKS_EXTRA_ALLOWED_CAPS_ON_NON_CELL) {
            restrictedNcBuilder.addCapability(cap);
        }

        if (isCell) {
            restrictedNcBuilder.addTransportType(TRANSPORT_CELLULAR);
        }
        final NetworkCapabilities restrictedNc = restrictedNcBuilder.build();

        final int creatorUid = isOwner ? ownerUid : INVALID_UID;
        restrictedNc.restrictCapabilitiesForTestNetwork(creatorUid);

        final NetworkCapabilities.Builder expectedNcBuilder = new NetworkCapabilities.Builder()
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);

        if (isCell) {
            // If the test network is restricted, then the network may declare any transport, and
            // appended with TRANSPORT_TEST.
            expectedNcBuilder.addTransportType(TRANSPORT_CELLULAR);
        } else {
            // If the test network only has TRANSPORT_TEST, then it can keep the subscription IDs.
            expectedNcBuilder.setSubscriptionIds(Set.of(TEST_SUBID1));
        }
        expectedNcBuilder.addTransportType(TRANSPORT_TEST);

        // Only TEST_NETWORKS_ALLOWED_CAPABILITIES will be kept.
        expectedNcBuilder.addCapability(NET_CAPABILITY_NOT_METERED);
        expectedNcBuilder.removeCapability(NET_CAPABILITY_TRUSTED);

        if (!isCell) {
            for (int cap : TEST_NETWORKS_EXTRA_ALLOWED_CAPS_ON_NON_CELL) {
                expectedNcBuilder.addCapability(cap);
            }
        }

        expectedNcBuilder.setSignalStrength(signalStrength).setTransportInfo(transportInfo);
        if (creatorUid == ownerUid) {
            // Only retain the owner and administrator UIDs if they match the app registering the
            // remote caller that registered the network.
            expectedNcBuilder.setAdministratorUids(new int[]{ownerUid}).setOwnerUid(ownerUid);
        }

        assertEquals(expectedNcBuilder.build(), restrictedNc);
    }

    @Test
    public void testDescribeCapsDifferences() throws Exception {
        final NetworkCapabilities nc1 = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_MMS)
                .addCapability(NET_CAPABILITY_OEM_PAID)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final NetworkCapabilities nc2 = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_CAPTIVE_PORTAL)
                .addCapability(NET_CAPABILITY_SUPL)
                .addCapability(NET_CAPABILITY_VALIDATED)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        assertEquals("-MMS-OEM_PAID+SUPL+VALIDATED+CAPTIVE_PORTAL",
                nc2.describeCapsDifferencesFrom(nc1));
        assertEquals("-SUPL-VALIDATED-CAPTIVE_PORTAL+MMS+OEM_PAID",
                nc1.describeCapsDifferencesFrom(nc2));
    }
}
