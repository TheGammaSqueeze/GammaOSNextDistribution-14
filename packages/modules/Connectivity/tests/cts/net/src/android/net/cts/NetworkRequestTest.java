/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_FOTA;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK;
import static android.net.NetworkCapabilities.NET_CAPABILITY_MMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_SUPL;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED;
import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static com.android.testutils.DevSdkIgnoreRuleKt.VANILLA_ICE_CREAM;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.net.MacAddress;
import android.net.MatchAllNetworkSpecifier;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.PatternMatcher;
import android.os.Process;
import android.util.ArraySet;
import android.util.Range;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.NetworkRequestShimImpl;
import com.android.networkstack.apishim.common.NetworkRequestShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.testutils.ConnectivityModuleTest;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
@ConnectivityModuleTest
public class NetworkRequestTest {
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final String TEST_SSID = "TestSSID";
    private static final String OTHER_SSID = "OtherSSID";
    private static final int TEST_UID = 2097;
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final MacAddress ARBITRARY_ADDRESS = MacAddress.fromString("3:5:8:12:9:2");

    private class LocalNetworkSpecifier extends NetworkSpecifier {
        private final int mId;

        LocalNetworkSpecifier(int id) {
            mId = id;
        }

        @Override
        public boolean canBeSatisfiedBy(NetworkSpecifier other) {
            return other instanceof LocalNetworkSpecifier
                && mId == ((LocalNetworkSpecifier) other).mId;
        }
    }

    @Test
    public void testCapabilities() {
        assertTrue(new NetworkRequest.Builder().addCapability(NET_CAPABILITY_MMS).build()
                .hasCapability(NET_CAPABILITY_MMS));
        assertFalse(new NetworkRequest.Builder().removeCapability(NET_CAPABILITY_MMS).build()
                .hasCapability(NET_CAPABILITY_MMS));

        final NetworkRequest nr = new NetworkRequest.Builder().clearCapabilities().build();
        // Verify request has no capabilities
        verifyNoCapabilities(nr);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testForbiddenCapabilities() {
        final NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addForbiddenCapability(NET_CAPABILITY_MMS);
        assertTrue(builder.build().hasForbiddenCapability(NET_CAPABILITY_MMS));
        builder.removeForbiddenCapability(NET_CAPABILITY_MMS);
        assertFalse(builder.build().hasCapability(NET_CAPABILITY_MMS));
        builder.addCapability(NET_CAPABILITY_MMS);
        assertFalse(builder.build().hasForbiddenCapability(NET_CAPABILITY_MMS));
        assertTrue(builder.build().hasCapability(NET_CAPABILITY_MMS));
        builder.addForbiddenCapability(NET_CAPABILITY_MMS);
        assertTrue(builder.build().hasForbiddenCapability(NET_CAPABILITY_MMS));
        assertFalse(builder.build().hasCapability(NET_CAPABILITY_MMS));
        builder.clearCapabilities();
        verifyNoCapabilities(builder.build());
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testTemporarilyNotMeteredCapability() {
        assertTrue(new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED).build()
                .hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertFalse(new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED).build()
                .hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED));
    }

    private void verifyNoCapabilities(NetworkRequest nr) {
        // NetworkCapabilities.mNetworkCapabilities is defined as type long
        final int MAX_POSSIBLE_CAPABILITY = Long.SIZE;
        for(int bit = 0; bit < MAX_POSSIBLE_CAPABILITY; bit++) {
            assertFalse(nr.hasCapability(bit));
        }
    }

    @Test
    public void testTransports() {
        assertTrue(new NetworkRequest.Builder().addTransportType(TRANSPORT_BLUETOOTH).build()
                .hasTransport(TRANSPORT_BLUETOOTH));
        assertFalse(new NetworkRequest.Builder().removeTransportType(TRANSPORT_BLUETOOTH).build()
                .hasTransport(TRANSPORT_BLUETOOTH));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testSpecifier() {
        assertNull(new NetworkRequest.Builder().build().getNetworkSpecifier());
        final WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsidPattern(new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL))
                .setBssidPattern(ARBITRARY_ADDRESS, ARBITRARY_ADDRESS)
                .build();
        final NetworkSpecifier obtainedSpecifier = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()
                .getNetworkSpecifier();
        assertEquals(obtainedSpecifier, specifier);

        assertNull(new NetworkRequest.Builder()
                .clearCapabilities()
                .build()
                .getNetworkSpecifier());
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S)
    public void testSubscriptionIds() {
        int[] subIds = {1, 2};
        assertTrue(
                new NetworkRequest.Builder().build()
                        .getSubscriptionIds().isEmpty());
        assertThat(new NetworkRequest.Builder()
                .setSubscriptionIds(Set.of(subIds[0], subIds[1]))
                .build()
                .getSubscriptionIds())
                .containsExactly(subIds[0], subIds[1]);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testRequestorPackageName() {
        assertNull(new NetworkRequest.Builder().build().getRequestorPackageName());
        final String pkgName = "android.net.test";
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .setRequestorPackageName(pkgName)
                .build();
        final NetworkRequest nr = new NetworkRequest.Builder()
                .setCapabilities(nc)
                .build();
        assertEquals(pkgName, nr.getRequestorPackageName());
        assertNull(new NetworkRequest.Builder()
                .clearCapabilities()
                .build()
                .getRequestorPackageName());
    }

    private void addNotVcnManagedCapability(@NonNull NetworkCapabilities nc) {
        if (SdkLevel.isAtLeastS()) {
            nc.addCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED);
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testCanBeSatisfiedBy() {
        final LocalNetworkSpecifier specifier1 = new LocalNetworkSpecifier(1234 /* id */);
        final LocalNetworkSpecifier specifier2 = new LocalNetworkSpecifier(5678 /* id */);

        // Some requests are adding NOT_VCN_MANAGED capability automatically. Add it to the
        // capabilities below for bypassing the check.
        final NetworkCapabilities capCellularMmsInternet = new NetworkCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_MMS)
                .addCapability(NET_CAPABILITY_INTERNET);
        addNotVcnManagedCapability(capCellularMmsInternet);
        final NetworkCapabilities capCellularVpnMmsInternet =
                new NetworkCapabilities(capCellularMmsInternet).addTransportType(TRANSPORT_VPN);
        addNotVcnManagedCapability(capCellularVpnMmsInternet);
        final NetworkCapabilities capCellularMmsInternetSpecifier1 =
                new NetworkCapabilities(capCellularMmsInternet).setNetworkSpecifier(specifier1);
        addNotVcnManagedCapability(capCellularMmsInternetSpecifier1);
        final NetworkCapabilities capVpnInternetSpecifier1 = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_VPN)
                .setNetworkSpecifier(specifier1);
        addNotVcnManagedCapability(capVpnInternetSpecifier1);
        final NetworkCapabilities capCellularMmsInternetMatchallspecifier =
                new NetworkCapabilities(capCellularMmsInternet)
                        .setNetworkSpecifier(new MatchAllNetworkSpecifier());
        addNotVcnManagedCapability(capCellularMmsInternetMatchallspecifier);
        final NetworkCapabilities capCellularMmsInternetSpecifier2 =
                new NetworkCapabilities(capCellularMmsInternet)
                        .setNetworkSpecifier(specifier2);
        addNotVcnManagedCapability(capCellularMmsInternetSpecifier2);

        final NetworkRequest requestCellularInternetSpecifier1 = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier1)
                .build();
        assertFalse(requestCellularInternetSpecifier1.canBeSatisfiedBy(null));
        assertFalse(requestCellularInternetSpecifier1.canBeSatisfiedBy(new NetworkCapabilities()));
        assertTrue(requestCellularInternetSpecifier1.canBeSatisfiedBy(
                capCellularMmsInternetMatchallspecifier));
        assertFalse(requestCellularInternetSpecifier1.canBeSatisfiedBy(capCellularMmsInternet));
        assertTrue(requestCellularInternetSpecifier1.canBeSatisfiedBy(
                capCellularMmsInternetSpecifier1));
        assertFalse(requestCellularInternetSpecifier1.canBeSatisfiedBy(capCellularVpnMmsInternet));
        assertFalse(requestCellularInternetSpecifier1.canBeSatisfiedBy(
                capCellularMmsInternetSpecifier2));

        final NetworkRequest requestCellularInternet = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        assertTrue(requestCellularInternet.canBeSatisfiedBy(capCellularMmsInternet));
        assertTrue(requestCellularInternet.canBeSatisfiedBy(capCellularMmsInternetSpecifier1));
        assertTrue(requestCellularInternet.canBeSatisfiedBy(capCellularMmsInternetSpecifier2));
        assertFalse(requestCellularInternet.canBeSatisfiedBy(capVpnInternetSpecifier1));
        assertTrue(requestCellularInternet.canBeSatisfiedBy(capCellularVpnMmsInternet));
    }

    private void setUids(NetworkRequest.Builder builder, Set<Range<Integer>> ranges)
            throws UnsupportedApiLevelException {
        if (SdkLevel.isAtLeastS()) {
            final NetworkRequestShim networkRequestShim = NetworkRequestShimImpl.newInstance();
            networkRequestShim.setUids(builder, ranges);
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testInvariantInCanBeSatisfiedBy() {
        // Test invariant that result of NetworkRequest.canBeSatisfiedBy() should be the same with
        // NetworkCapabilities.satisfiedByNetworkCapabilities().
        final LocalNetworkSpecifier specifier1 = new LocalNetworkSpecifier(1234 /* id */);
        final int uid = Process.myUid();
        final NetworkRequest.Builder nrBuilder = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .setLinkUpstreamBandwidthKbps(1000)
                .setNetworkSpecifier(specifier1)
                .setSignalStrength(-123);

        // The uid ranges should be set into the request, but setUids() takes a set of UidRange
        // that is hidden and inaccessible from shims. Before, S setUids will be a no-op. But
        // because NetworkRequest.Builder sets the UID of the request to the current UID, the
        // request contains the current UID both on S and before S.
        final Set<Range<Integer>> ranges = new ArraySet<>();
        ranges.add(new Range<Integer>(uid, uid));
        try {
            setUids(nrBuilder, ranges);
        } catch (UnsupportedApiLevelException e) {
            // Not supported before API31.
        }
        final NetworkRequest requestCombination = nrBuilder.build();

        final NetworkCapabilities capCell = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR).build();
        assertCorrectlySatisfies(false, requestCombination, capCell);

        final NetworkCapabilities capCellInternet = new NetworkCapabilities.Builder(capCell)
                .addCapability(NET_CAPABILITY_INTERNET).build();
        assertCorrectlySatisfies(false, requestCombination, capCellInternet);

        final NetworkCapabilities capCellInternetBW =
                new NetworkCapabilities.Builder(capCellInternet)
                    .setLinkUpstreamBandwidthKbps(1024).build();
        assertCorrectlySatisfies(false, requestCombination, capCellInternetBW);

        final NetworkCapabilities capCellInternetBWSpecifier1 =
                new NetworkCapabilities.Builder(capCellInternetBW)
                    .setNetworkSpecifier(specifier1).build();
        assertCorrectlySatisfies(false, requestCombination, capCellInternetBWSpecifier1);

        final NetworkCapabilities capCellInternetBWSpecifier1Signal =
                new NetworkCapabilities.Builder(capCellInternetBWSpecifier1)
                        .setSignalStrength(-123).build();
        addNotVcnManagedCapability(capCellInternetBWSpecifier1Signal);
        assertCorrectlySatisfies(true, requestCombination,
                capCellInternetBWSpecifier1Signal);

        final NetworkCapabilities capCellInternetBWSpecifier1SignalUid =
                new NetworkCapabilities.Builder(capCellInternetBWSpecifier1Signal)
                    .setOwnerUid(uid)
                    .setAdministratorUids(new int [] {uid}).build();
        assertCorrectlySatisfies(true, requestCombination,
                capCellInternetBWSpecifier1SignalUid);
    }

    private void assertCorrectlySatisfies(boolean expect, NetworkRequest request,
            NetworkCapabilities nc) {
        assertEquals(expect, request.canBeSatisfiedBy(nc));
        assertEquals(
                request.canBeSatisfiedBy(nc),
                request.networkCapabilities.satisfiedByNetworkCapabilities(nc));
    }

    private static Set<Range<Integer>> uidRangesForUid(int uid) {
        final Range<Integer> range = new Range<>(uid, uid);
        return Set.of(range);
    }

    @Test
    public void testSetIncludeOtherUidNetworks() throws Exception {
        assumeTrue(TestUtils.shouldTestSApis());
        final NetworkRequestShim shim = NetworkRequestShimImpl.newInstance();

        final NetworkRequest.Builder builder = new NetworkRequest.Builder();
        // NetworkRequests have NET_CAPABILITY_NOT_VCN_MANAGED by default.
        builder.removeCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED);
        shim.setIncludeOtherUidNetworks(builder, false);
        final NetworkRequest request = builder.build();

        final NetworkRequest.Builder otherUidsBuilder = new NetworkRequest.Builder();
        otherUidsBuilder.removeCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED);
        shim.setIncludeOtherUidNetworks(otherUidsBuilder, true);
        final NetworkRequest otherUidsRequest = otherUidsBuilder.build();

        assertNotEquals(Process.SYSTEM_UID, Process.myUid());
        final NetworkCapabilities ncWithMyUid = new NetworkCapabilities()
                .setUids(uidRangesForUid(Process.myUid()));
        final NetworkCapabilities ncWithOtherUid = new NetworkCapabilities()
                .setUids(uidRangesForUid(Process.SYSTEM_UID));

        assertTrue(request + " should be satisfied by " + ncWithMyUid,
                request.canBeSatisfiedBy(ncWithMyUid));
        assertTrue(otherUidsRequest + " should be satisfied by " + ncWithMyUid,
                otherUidsRequest.canBeSatisfiedBy(ncWithMyUid));
        assertFalse(request + " should not be satisfied by " +  ncWithOtherUid,
                request.canBeSatisfiedBy(ncWithOtherUid));
        assertTrue(otherUidsRequest + " should be satisfied by " + ncWithOtherUid,
                otherUidsRequest.canBeSatisfiedBy(ncWithOtherUid));
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testRequestorUid() {
        final NetworkCapabilities nc = new NetworkCapabilities();
        // Verify default value is INVALID_UID
        assertEquals(Process.INVALID_UID, new NetworkRequest.Builder()
                 .setCapabilities(nc).build().getRequestorUid());

        nc.setRequestorUid(1314);
        final NetworkRequest nr = new NetworkRequest.Builder().setCapabilities(nc).build();
        assertEquals(1314, nr.getRequestorUid());

        assertEquals(Process.INVALID_UID, new NetworkRequest.Builder()
                .clearCapabilities().build().getRequestorUid());
    }

    // TODO: 1. Refactor test cases with helper method.
    //       2. Test capability that does not yet exist.
    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBypassingVcn() {
        // Make an empty request. Verify the NOT_VCN_MANAGED is added.
        final NetworkRequest emptyRequest = new NetworkRequest.Builder().build();
        assertTrue(emptyRequest.hasCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make a request explicitly add NOT_VCN_MANAGED. Verify the NOT_VCN_MANAGED is preserved.
        final NetworkRequest mmsAddNotVcnRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_MMS)
                .addCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        assertTrue(mmsAddNotVcnRequest.hasCapability(
                ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Similar to above, but the opposite order.
        final NetworkRequest mmsAddNotVcnRequest2 = new NetworkRequest.Builder()
                .addCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addCapability(NET_CAPABILITY_MMS)
                .build();
        assertTrue(mmsAddNotVcnRequest2.hasCapability(
                ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make a request explicitly remove NOT_VCN_MANAGED. Verify the NOT_VCN_MANAGED is removed.
        final NetworkRequest removeNotVcnRequest = new NetworkRequest.Builder()
                .removeCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED).build();
        assertFalse(removeNotVcnRequest.hasCapability(
                ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make a request add some capability inside VCN supported capabilities.
        // Verify the NOT_VCN_MANAGED is added.
        final NetworkRequest notRoamRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_NOT_ROAMING).build();
        assertTrue(notRoamRequest.hasCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make an internet request. Verify the NOT_VCN_MANAGED is added.
        final NetworkRequest internetRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET).build();
        assertTrue(internetRequest.hasCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make an internet request which explicitly removed NOT_VCN_MANAGED.
        // Verify the NOT_VCN_MANAGED is removed.
        final NetworkRequest internetRemoveNotVcnRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .removeCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED).build();
        assertFalse(internetRemoveNotVcnRequest.hasCapability(
                ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make a normal MMS request. Verify the request could bypass VCN.
        final NetworkRequest mmsRequest =
                new NetworkRequest.Builder().addCapability(NET_CAPABILITY_MMS).build();
        assertFalse(mmsRequest.hasCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make a SUPL request along with internet. Verify NOT_VCN_MANAGED is not added since
        // SUPL is not in the supported list.
        final NetworkRequest suplWithInternetRequest = new NetworkRequest.Builder()
                        .addCapability(NET_CAPABILITY_SUPL)
                        .addCapability(NET_CAPABILITY_INTERNET).build();
        assertFalse(suplWithInternetRequest.hasCapability(
                ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make a FOTA request with explicitly add NOT_VCN_MANAGED capability. Verify
        // NOT_VCN_MANAGED is preserved.
        final NetworkRequest fotaRequest = new NetworkRequest.Builder()
                        .addCapability(NET_CAPABILITY_FOTA)
                        .addCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED).build();
        assertTrue(fotaRequest.hasCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make a DUN request, which is in {@code VCN_SUPPORTED_CAPABILITIES}.
        // Verify NOT_VCN_MANAGED is preserved.
        final NetworkRequest dunRequest = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_DUN).build();
        assertTrue(dunRequest.hasCapability(ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));

        // Make an internet request but with NetworkSpecifier. Verify the NOT_VCN_MANAGED is not
        // added.
        final NetworkRequest internetWithSpecifierRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI).addCapability(NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(makeTestWifiSpecifier()).build();
        assertFalse(internetWithSpecifierRequest.hasCapability(
                ConstantsShim.NET_CAPABILITY_NOT_VCN_MANAGED));
    }

    private void verifyEqualRequestBuilt(NetworkRequest orig) {
        try {
            final NetworkRequestShim shim = NetworkRequestShimImpl.newInstance();
            final NetworkRequest copy = shim.newBuilder(orig).build();
            assertEquals(orig, copy);
        } catch (UnsupportedApiLevelException e) {
            fail("NetworkRequestShim.newBuilder should be supported in this SDK version");
        }
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testGetCapabilities() {
        final int[] netCapabilities = new int[] {
                NET_CAPABILITY_INTERNET,
                NET_CAPABILITY_NOT_ROAMING };
        final NetworkCapabilities.Builder builder = NetworkCapabilities.Builder
                .withoutDefaultCapabilities();
        for (int capability : netCapabilities) builder.addCapability(capability);
        final NetworkRequest nr = new NetworkRequest.Builder()
                .clearCapabilities()
                .setCapabilities(builder.build())
                .build();
        assertArrayEquals(netCapabilities, nr.getCapabilities());
    }

    @Test @IgnoreUpTo(VANILLA_ICE_CREAM)
    public void testDefaultCapabilities() {
        final NetworkRequest defaultNR = new NetworkRequest.Builder().build();
        assertTrue(defaultNR.hasForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK));
        assertFalse(defaultNR.hasCapability(NET_CAPABILITY_LOCAL_NETWORK));
        assertTrue(defaultNR.hasCapability(NET_CAPABILITY_NOT_VPN));

        final NetworkCapabilities emptyNC =
                NetworkCapabilities.Builder.withoutDefaultCapabilities().build();
        assertFalse(defaultNR.canBeSatisfiedBy(emptyNC));

        // defaultNC represent the capabilities of a network agent, so they must not contain
        // forbidden capabilities by default.
        final NetworkCapabilities defaultNC = new NetworkCapabilities.Builder().build();
        assertArrayEquals(new int[0], defaultNC.getForbiddenCapabilities());
        // A default NR can be satisfied by default NC.
        assertTrue(defaultNR.canBeSatisfiedBy(defaultNC));

        // Conversely, network requests have forbidden capabilities by default to manage
        // backward compatibility, so test that these forbidden capabilities are in place.
        // Starting in V, NET_CAPABILITY_LOCAL_NETWORK is introduced but is not seen by
        // default, thanks to a default forbidden capability in NetworkRequest.
        defaultNC.addCapability(NET_CAPABILITY_LOCAL_NETWORK);
        assertFalse(defaultNR.canBeSatisfiedBy(defaultNC));
    }

    @Test
    public void testBuildRequestFromExistingRequestWithBuilder() {
        assumeTrue(TestUtils.shouldTestSApis());
        final NetworkRequest.Builder builder = new NetworkRequest.Builder();

        final NetworkRequest baseRequest = builder.build();
        verifyEqualRequestBuilt(baseRequest);

        final NetworkRequest requestCellMms = builder
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_MMS)
                .setSignalStrength(-99).build();
        verifyEqualRequestBuilt(requestCellMms);

        final NetworkRequest requestWifi = builder
                .addTransportType(TRANSPORT_WIFI)
                .removeTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .removeCapability(NET_CAPABILITY_MMS)
                .setNetworkSpecifier(makeTestWifiSpecifier())
                .setSignalStrength(-33).build();
        verifyEqualRequestBuilt(requestWifi);
    }

    private WifiNetworkSpecifier makeTestWifiSpecifier() {
        return new WifiNetworkSpecifier.Builder()
                .setSsidPattern(new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL))
                .setBssidPattern(ARBITRARY_ADDRESS, ARBITRARY_ADDRESS)
                .build();
    }
}
