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

package android.net.vcn.cts;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.ipsec.ike.cts.IkeTunUtils.PortPair;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.DETECTION_METHOD_DNS_EVENTS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_CBS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_RCS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.vcn.VcnGatewayConnectionConfig.VCN_GATEWAY_OPTION_ENABLE_DATA_STALL_RECOVERY_WITH_MOBILITY;
import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_ANY;
import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_FORBIDDEN;
import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_REQUIRED;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.internal.util.HexDump.hexStringToByteArray;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ipsec.ike.cts.IkeTunUtils;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.vcn.VcnCellUnderlyingNetworkTemplate;
import android.net.vcn.VcnConfig;
import android.net.vcn.VcnManager;
import android.net.vcn.VcnNetworkPolicyResult;
import android.net.vcn.VcnUnderlyingNetworkTemplate;
import android.net.vcn.VcnWifiUnderlyingNetworkTemplate;
import android.net.vcn.cts.TestNetworkWrapper.VcnTestNetworkCallback;
import android.net.vcn.cts.TestNetworkWrapper.VcnTestNetworkCallback.CapabilitiesChangedEvent;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.SubscriptionGroupUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.CarrierPrivilegeUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class VcnManagerTest extends VcnTestBase {
    private static final String TAG = VcnManagerTest.class.getSimpleName();

    private static final int TIMEOUT_MS = 500;
    private static final long SAFEMODE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(35);

    private static final Executor INLINE_EXECUTOR = Runnable::run;

    private static final int TEST_NETWORK_MTU = 1500;

    private static final InetAddress LOCAL_ADDRESS =
            InetAddresses.parseNumericAddress("198.51.100.1");
    private static final InetAddress SECONDARY_LOCAL_ADDRESS =
            InetAddresses.parseNumericAddress("198.51.100.2");

    private static final long IKE_DETERMINISTIC_INITIATOR_SPI =
            Long.parseLong("46B8ECA1E0D72A18", 16);

    private final Context mContext;
    private final VcnManager mVcnManager;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyManager mTelephonyManager;
    private final ConnectivityManager mConnectivityManager;

    public VcnManagerTest() {
        mContext = InstrumentationRegistry.getContext();
        mVcnManager = mContext.getSystemService(VcnManager.class);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY));

        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
    }

    @After
    public void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    private VcnConfig.Builder buildVcnConfigBase() {
        return buildVcnConfigBase(new ArrayList<VcnUnderlyingNetworkTemplate>());
    }

    private VcnConfig.Builder buildVcnConfigBase(List<VcnUnderlyingNetworkTemplate> nwTemplate) {
        // TODO(b/191371669): remove the exposed MMS capability and use
        // VcnGatewayConnectionConfigTest.buildVcnGatewayConnectionConfig() instead
        return new VcnConfig.Builder(mContext)
                .addGatewayConnectionConfig(
                        VcnGatewayConnectionConfigTest.buildVcnGatewayConnectionConfigBase()
                                .addExposedCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                                .setVcnUnderlyingNetworkPriorities(nwTemplate)
                                .addGatewayOption(
                                        VCN_GATEWAY_OPTION_ENABLE_DATA_STALL_RECOVERY_WITH_MOBILITY)
                                .build());
    }

    private VcnConfig buildVcnConfig() {
        return buildVcnConfigBase().build();
    }

    private VcnConfig buildTestModeVcnConfig() {
        return buildVcnConfigBase().setIsTestModeProfile().build();
    }

    private int verifyAndGetValidDataSubId() {
        final int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        assertNotEquals(
                "There must be an active data subscription to complete CTS",
                INVALID_SUBSCRIPTION_ID,
                dataSubId);
        return dataSubId;
    }

    @Test(expected = SecurityException.class)
    public void testSetVcnConfig_noCarrierPrivileges() throws Exception {
        mVcnManager.setVcnConfig(new ParcelUuid(UUID.randomUUID()), buildVcnConfig());
    }

    @Test
    public void testSetVcnConfig_withCarrierPrivileges() throws Exception {
        final int dataSubId = verifyAndGetValidDataSubId();
        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, dataSubId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, dataSubId, (subGrp) -> {
                mVcnManager.setVcnConfig(subGrp, buildVcnConfig());
            });
        });

        assertFalse(mTelephonyManager.createForSubscriptionId(dataSubId).hasCarrierPrivileges());
    }

    @Test(expected = SecurityException.class)
    public void testClearVcnConfig_noCarrierPrivileges() throws Exception {
        mVcnManager.clearVcnConfig(new ParcelUuid(UUID.randomUUID()));
    }

    @Test
    public void testClearVcnConfig_withCarrierPrivileges() throws Exception {
        final int dataSubId = verifyAndGetValidDataSubId();

        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, dataSubId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, dataSubId, (subGrp) -> {
                mVcnManager.clearVcnConfig(subGrp);
            });
        });
    }

    /** Test implementation of VcnNetworkPolicyChangeListener for verification purposes. */
    private static class TestVcnNetworkPolicyChangeListener
            implements VcnManager.VcnNetworkPolicyChangeListener {
        private final CompletableFuture<Void> mFutureOnPolicyChanged = new CompletableFuture<>();

        @Override
        public void onPolicyChanged() {
            mFutureOnPolicyChanged.complete(null /* unused */);
        }

        public void awaitOnPolicyChanged() throws Exception {
            mFutureOnPolicyChanged.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Test(expected = SecurityException.class)
    public void testAddVcnNetworkPolicyChangeListener_noNetworkFactoryPermission()
            throws Exception {
        // Drop shell permission identity to test unpermissioned behavior.
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

        final TestVcnNetworkPolicyChangeListener listener =
                new TestVcnNetworkPolicyChangeListener();

        try {
            mVcnManager.addVcnNetworkPolicyChangeListener(INLINE_EXECUTOR, listener);
        } finally {
            mVcnManager.removeVcnNetworkPolicyChangeListener(listener);
        }
    }

    @Test
    public void testRemoveVcnNetworkPolicyChangeListener_noNetworkFactoryPermission() {
        final TestVcnNetworkPolicyChangeListener listener =
                new TestVcnNetworkPolicyChangeListener();

        mVcnManager.removeVcnNetworkPolicyChangeListener(listener);
    }

    @Test(expected = SecurityException.class)
    public void testApplyVcnNetworkPolicy_noNetworkFactoryPermission() throws Exception {
        // Drop shell permission identity to test unpermissioned behavior.
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

        final NetworkCapabilities nc = new NetworkCapabilities.Builder().build();
        final LinkProperties lp = new LinkProperties();

        mVcnManager.applyVcnNetworkPolicy(nc, lp);
    }

    @Test
    public void testApplyVcnNetworkPolicy_manageTestNetworkRequiresTransportTest()
            throws Exception {
        final NetworkCapabilities nc =
                new NetworkCapabilities.Builder().addTransportType(TRANSPORT_CELLULAR).build();
        final LinkProperties lp = new LinkProperties();

        runWithShellPermissionIdentity(
                () -> {
                    try {
                        mVcnManager.applyVcnNetworkPolicy(nc, lp);
                        fail("Expected IllegalStateException for applyVcnNetworkPolicy");
                    } catch (IllegalStateException e) {
                    }
                },
                android.Manifest.permission.MANAGE_TEST_NETWORKS);
    }

    private TestNetworkWrapper createTestNetworkWrapperForPolicyTest(
            boolean isRestricted, int subId) throws Exception {
        final Set<Integer> capabilities = new HashSet<>();
        capabilities.add(NET_CAPABILITY_CBS);
        if (!isRestricted) {
            capabilities.add(NET_CAPABILITY_NOT_RESTRICTED);
        }

        return createTestNetworkWrapper(subId, LOCAL_ADDRESS, capabilities);
    }

    private VcnConfig buildVcnConfigWithTransportTestRestricted() {
        return buildVcnConfigBase()
                .setIsTestModeProfile()
                .setRestrictedUnderlyingNetworkTransports(Set.of(TRANSPORT_TEST))
                .build();
    }

    @Test
    public void testApplyVcnNetworkPolicyDuringVcnSetup_onUnrestrictedNetwork() throws Exception {
        final int subId = verifyAndGetValidDataSubId();
        final VcnConfig vcnConfig = buildVcnConfigWithTransportTestRestricted();

        try (TestNetworkWrapper networkWrapperUnrestricted =
                createTestNetworkWrapperForPolicyTest(false /* isRestricted */, subId)) {
            verifyUnderlyingCellAndRunTest(
                    subId,
                    (subGrp, cellNetwork, cellNetworkCb) -> {
                        cellNetworkCb.waitForAvailable();

                        // Attempt VCN setup on an unrestricted network; expect the network to
                        // change to be restricted
                        mVcnManager.setVcnConfig(subGrp, vcnConfig);

                        VcnNetworkPolicyResult policyResult =
                                networkWrapperUnrestricted.awaitVcnNetworkPolicyChange();

                        // Expect teardown due to restriction capability change
                        assertTrue(policyResult.isTeardownRequested());
                        assertFalse(
                                policyResult
                                        .getNetworkCapabilities()
                                        .hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

                        // Verify underlying network is lost
                        networkWrapperUnrestricted.vcnNetworkCallback.waitForLost();

                        mVcnManager.clearVcnConfig(subGrp);
                    });
        }
    }

    @Test
    public void testApplyVcnNetworkPolicyDuringVcnSetup_onRestrictedNetwork() throws Exception {
        final int subId = verifyAndGetValidDataSubId();
        final VcnConfig vcnConfig = buildVcnConfigWithTransportTestRestricted();

        try (TestNetworkWrapper networkWrapperRestricted =
                createTestNetworkWrapperForPolicyTest(true /* isRestricted */, subId)) {

            verifyUnderlyingCellAndRunTest(
                    subId,
                    (subGrp, cellNetwork, cellNetworkCb) -> {
                        // Set up VCN on a restricted network
                        final VcnSetupResult vcnSetupResult =
                                setupAndGetVcnNetwork(
                                        subGrp,
                                        cellNetwork,
                                        cellNetworkCb,
                                        vcnConfig,
                                        networkWrapperRestricted);

                        VcnNetworkPolicyResult policyResult =
                                networkWrapperRestricted.awaitVcnNetworkPolicyChange();

                        // Do not expect teardown since the restriction capability does not change
                        assertFalse(policyResult.isTeardownRequested());
                        assertFalse(
                                policyResult
                                        .getNetworkCapabilities()
                                        .hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

                        clearVcnConfigsAndVerifyNetworkTeardown(
                                subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
                    });
        }
    }

    private void waitForSafeMode(TestNetworkWrapper networkWrapper) throws Exception {
        // Once VCN starts, the test network should lose NOT_VCN_MANAGED
        waitForExpectedUnderlyingNetworkWithCapabilities(
                networkWrapper,
                false /* expectNotVcnManaged */,
                false /* expectNotMetered */,
                TestNetworkWrapper.NETWORK_CB_TIMEOUT_MS);

        // After VCN has started up, wait for safemode to kick in and expect the
        // underlying Test Network to regain NOT_VCN_MANAGED.
        waitForExpectedUnderlyingNetworkWithCapabilities(
                networkWrapper,
                true /* expectNotVcnManaged */,
                false /* expectNotMetered */,
                SAFEMODE_TIMEOUT_MILLIS);
    }

    private void verifyApplyVcnNetworkPolicyPostVcnSetupChangeNetworkRestriction(
            boolean isSafeMode, boolean isRestrictedBefore, boolean expectRestrictedAfter)
            throws Exception {
        final int subId = verifyAndGetValidDataSubId();
        final VcnConfig vcnConfig = buildVcnConfigWithTransportTestRestricted();

        try (TestNetworkWrapper networkWrapperRestricted =
                createTestNetworkWrapperForPolicyTest(true /* isRestricted */, subId)) {

            verifyUnderlyingCellAndRunTest(
                    subId,
                    (subGrp, cellNetwork, cellNetworkCb) -> {
                        // Set up VCN on a restricted network
                        final VcnSetupResult vcnSetupResult =
                                setupAndGetVcnNetwork(
                                        subGrp,
                                        cellNetwork,
                                        cellNetworkCb,
                                        vcnConfig,
                                        networkWrapperRestricted);

                        if (isSafeMode) {
                            waitForSafeMode(networkWrapperRestricted);
                        }

                        // Bring up another test network and verify its restriction capability
                        // change.
                        try (TestNetworkWrapper testNetworkWrapper =
                                createTestNetworkWrapperForPolicyTest(isRestrictedBefore, subId)) {

                            // The requested NetworkCapabilities should have been changed by
                            // VcnManager before the test network was brought up. Verify it by
                            // checking the NetworkCapabilities after the network setup.
                            final NetworkCapabilities nc =
                                    mConnectivityManager.getNetworkCapabilities(
                                            testNetworkWrapper.tunNetwork);
                            assertEquals(
                                    !expectRestrictedAfter,
                                    nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
                        }

                        clearVcnConfigsAndVerifyNetworkTeardown(
                                subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
                    });
        }
    }

    @Test
    public void testApplyVcnNetworkPolicy_activeMode_onRestrictedNetwork() throws Exception {
        verifyApplyVcnNetworkPolicyPostVcnSetupChangeNetworkRestriction(
                false /* isSafeMode */,
                true /* isRestrictedBefore */,
                true /* expectRestrictedAfter */);
    }

    @Test
    public void testApplyVcnNetworkPolicy_safeMode_onRestrictedNetwork() throws Exception {
        verifyApplyVcnNetworkPolicyPostVcnSetupChangeNetworkRestriction(
                true /* isSafeMode */,
                true /* isRestrictedBefore */,
                true /* expectRestrictedAfter */);
    }

    @Test
    public void testApplyVcnNetworkPolicy_activeMode_onUnrestrictedNetwork() throws Exception {
        verifyApplyVcnNetworkPolicyPostVcnSetupChangeNetworkRestriction(
                false /* isSafeMode */,
                false /* isRestrictedBefore */,
                true /* expectRestrictedAfter */);
    }

    @Test
    public void testApplyVcnNetworkPolicy_safeMode_onUnrestrictedNetwork() throws Exception {
        verifyApplyVcnNetworkPolicyPostVcnSetupChangeNetworkRestriction(
                true /* isSafeMode */,
                false /* isRestrictedBefore */,
                false /* expectRestrictedAfter */);
    }

    /** Test implementation of VcnStatusCallback for verification purposes. */
    private static class TestVcnStatusCallback extends VcnManager.VcnStatusCallback {
        private final CompletableFuture<Integer> mFutureOnStatusChanged =
                new CompletableFuture<>();
        private final CompletableFuture<GatewayConnectionError> mFutureOnGatewayConnectionError =
                new CompletableFuture<>();

        @Override
        public void onStatusChanged(int statusCode) {
            mFutureOnStatusChanged.complete(statusCode);
        }

        @Override
        public void onGatewayConnectionError(
                @NonNull String gatewayConnectionName, int errorCode, @Nullable Throwable detail) {
            mFutureOnGatewayConnectionError.complete(
                    new GatewayConnectionError(gatewayConnectionName, errorCode, detail));
        }

        public int awaitOnStatusChanged() throws Exception {
            return mFutureOnStatusChanged.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public GatewayConnectionError awaitOnGatewayConnectionError() throws Exception {
            return mFutureOnGatewayConnectionError.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** Info class for organizing VcnStatusCallback#onGatewayConnectionError response data. */
    private static class GatewayConnectionError {
        @NonNull public final String gatewayConnectionName;
        public final int errorCode;
        @Nullable public final Throwable detail;

        public GatewayConnectionError(
                @NonNull String gatewayConnectionName, int errorCode, @Nullable Throwable detail) {
            this.gatewayConnectionName = gatewayConnectionName;
            this.errorCode = errorCode;
            this.detail = detail;
        }
    }

    private void registerVcnStatusCallbackForSubId(
            @NonNull TestVcnStatusCallback callback, int subId) throws Exception {
        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, subId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, subId, (subGrp) -> {
                mVcnManager.registerVcnStatusCallback(subGrp, INLINE_EXECUTOR, callback);
            });
        });
    }

    @Test
    public void testRegisterVcnStatusCallback() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();
        final int subId = verifyAndGetValidDataSubId();

        try {
            registerVcnStatusCallbackForSubId(callback, subId);

            final int statusCode = callback.awaitOnStatusChanged();
            assertEquals(VcnManager.VCN_STATUS_CODE_NOT_CONFIGURED, statusCode);
        } finally {
            mVcnManager.unregisterVcnStatusCallback(callback);
        }
    }

    @Test
    public void testRegisterVcnStatusCallback_reuseUnregisteredCallback() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();
        final int subId = verifyAndGetValidDataSubId();

        try {
            registerVcnStatusCallbackForSubId(callback, subId);
            mVcnManager.unregisterVcnStatusCallback(callback);
            registerVcnStatusCallbackForSubId(callback, subId);
        } finally {
            mVcnManager.unregisterVcnStatusCallback(callback);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testRegisterVcnStatusCallback_duplicateRegister() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();
        final int subId = verifyAndGetValidDataSubId();

        try {
            registerVcnStatusCallbackForSubId(callback, subId);
            registerVcnStatusCallbackForSubId(callback, subId);
        } finally {
            mVcnManager.unregisterVcnStatusCallback(callback);
        }
    }

    @Test
    public void testUnregisterVcnStatusCallback() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();

        mVcnManager.unregisterVcnStatusCallback(callback);
    }

    private TestNetworkWrapper createTestNetworkWrapper(
            int subId, InetAddress localAddress, Set<Integer> capabilities) throws Exception {
        TestNetworkWrapper testNetworkWrapper =
                new TestNetworkWrapper(
                        mContext,
                        TEST_NETWORK_MTU,
                        capabilities,
                        Collections.singleton(subId),
                        localAddress);
        assertNotNull("No test network found", testNetworkWrapper.tunNetwork);
        return testNetworkWrapper;
    }

    private TestNetworkWrapper createTestNetworkWrapper(
            boolean isMetered, int subId, InetAddress localAddress) throws Exception {
        final Set<Integer> capabilities = new HashSet<>();
        capabilities.add(NET_CAPABILITY_CBS);
        if (!isMetered) {
            capabilities.add(NET_CAPABILITY_NOT_METERED);
        }

        return createTestNetworkWrapper(subId, localAddress, capabilities);
    }

    @Test
    public void testVcnManagedNetworkLosesNotVcnManagedCapability() throws Exception {
        final int subId = verifyAndGetValidDataSubId();
        try (TestNetworkWrapper testNetworkWrapper =
                createTestNetworkWrapper(true /* isMetered */, subId, LOCAL_ADDRESS)) {
            // Before the VCN starts, the test network should have NOT_VCN_MANAGED
            waitForExpectedUnderlyingNetworkWithCapabilities(
                    testNetworkWrapper,
                    true /* expectNotVcnManaged */,
                    false /* expectNotMetered */,
                    TestNetworkWrapper.NETWORK_CB_TIMEOUT_MS);

            CarrierPrivilegeUtils.withCarrierPrivilegesForShell(mContext, subId, () -> {
                SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, subId, (subGrp) -> {
                    mVcnManager.setVcnConfig(subGrp, buildVcnConfig());

                    // Once VCN starts, the test network should lose NOT_VCN_MANAGED
                    waitForExpectedUnderlyingNetworkWithCapabilities(
                            testNetworkWrapper,
                            false /* expectNotVcnManaged */,
                            false /* expectNotMetered */,
                            TestNetworkWrapper.NETWORK_CB_TIMEOUT_MS);

                    mVcnManager.clearVcnConfig(subGrp);

                    // After the VCN tears down, the test network should have
                    // NOT_VCN_MANAGED again
                    waitForExpectedUnderlyingNetworkWithCapabilities(
                            testNetworkWrapper,
                            true /* expectNotVcnManaged */,
                            false /* expectNotMetered */,
                            TestNetworkWrapper.NETWORK_CB_TIMEOUT_MS);
                });
            });
        }
    }

    private void waitForExpectedUnderlyingNetworkWithCapabilities(
            TestNetworkWrapper testNetworkWrapper,
            boolean expectNotVcnManaged,
            boolean expectNotMetered,
            long timeoutMillis)
            throws Exception {
        final long start = SystemClock.elapsedRealtime();

        // Wait for NetworkCapabilities changes until they match the expected capabilities
        do {
            final CapabilitiesChangedEvent capabilitiesChangedEvent =
                    testNetworkWrapper.vcnNetworkCallback.waitForOnCapabilitiesChanged(
                            timeoutMillis);
            assertNotNull("Failed to receive NetworkCapabilities change", capabilitiesChangedEvent);

            final NetworkCapabilities nc = capabilitiesChangedEvent.networkCapabilities;
            if (testNetworkWrapper.tunNetwork.equals(capabilitiesChangedEvent.network)
                    && nc.hasCapability(NET_CAPABILITY_VALIDATED)
                    && expectNotVcnManaged == nc.hasCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                    && expectNotMetered == nc.hasCapability(NET_CAPABILITY_NOT_METERED)) {
                return;
            }
        } while (SystemClock.elapsedRealtime() - start < timeoutMillis);

        fail(
                "Expected update for network="
                        + testNetworkWrapper.tunNetwork.getNetId()
                        + ". Wanted NOT_VCN_MANAGED="
                        + expectNotVcnManaged
                        + " NOT_METERED="
                        + expectNotMetered);
    }

    private interface VcnTestRunnable {
        void runTest(ParcelUuid subGrp, Network cellNetwork, VcnTestNetworkCallback cellNetworkCb)
                throws Exception;
    }

    private void verifyUnderlyingCellAndRunTest(int subId, VcnTestRunnable test) throws Exception {
        // Get current cell Network then wait for it to drop (due to losing NOT_VCN_MANAGED)
        // before waiting for VCN Network.
        final NetworkRequest cellNetworkReq =
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_CELLULAR)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
        final VcnTestNetworkCallback cellNetworkCb = new VcnTestNetworkCallback();
        mConnectivityManager.requestNetwork(cellNetworkReq, cellNetworkCb);
        final Network cellNetwork = cellNetworkCb.waitForAvailable();
        assertNotNull("No cell network found", cellNetwork);

        CarrierPrivilegeUtils.withCarrierPrivilegesForShell(mContext, subId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(
                mContext,
                subId,
                (subGrp) -> {
                    test.runTest(subGrp, cellNetwork, cellNetworkCb);
                }
            );
        });
        mConnectivityManager.unregisterNetworkCallback(cellNetworkCb);
    }

    @Test
    public void testSetVcnConfigOnTestNetwork() throws Exception {
        final int subId = verifyAndGetValidDataSubId();

        try (TestNetworkWrapper testNetworkWrapper =
                createTestNetworkWrapper(true /* isMetered */, subId, LOCAL_ADDRESS)) {
            verifyUnderlyingCellAndRunTest(subId, (subGrp, cellNetwork, cellNetworkCb) -> {
                final VcnSetupResult vcnSetupResult =
                    setupAndGetVcnNetwork(subGrp, cellNetwork, cellNetworkCb, testNetworkWrapper);

                clearVcnConfigsAndVerifyNetworkTeardown(
                        subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
            });
        }
    }

    @Test
    public void testSetVcnConfigOnTestNetworkAndHandleDataStall() throws Exception {
        final int subId = verifyAndGetValidDataSubId();

        try (TestNetworkWrapper testNetworkWrapper =
                createTestNetworkWrapper(true /* isMetered */, subId, LOCAL_ADDRESS)) {
            verifyUnderlyingCellAndRunTest(
                    subId,
                    (subGrp, cellNetwork, cellNetworkCb) -> {
                        final VcnSetupResult vcnSetupResult =
                                setupAndGetVcnNetwork(
                                        subGrp, cellNetwork, cellNetworkCb, testNetworkWrapper);

                        mConnectivityManager.simulateDataStall(
                                DETECTION_METHOD_DNS_EVENTS,
                                System.currentTimeMillis(),
                                vcnSetupResult.vcnNetwork,
                                new PersistableBundle() /* extra data stall info; unused */);

                        injectAndVerifyIkeMobikePackets(testNetworkWrapper.ikeTunUtils);

                        clearVcnConfigsAndVerifyNetworkTeardown(
                                subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
                    });
        }
    }

    private TestNetworkWrapper createTestNetworkForNetworkSelection(
            int subId, Set<Integer> capabilities) throws Exception {
        return createTestNetworkWrapper(subId, LOCAL_ADDRESS, capabilities);
    }

    private void verifyVcnMigratesToPreferredUnderlyingNetwork(
            VcnConfig vcnConfig, Set<Integer> capSetLessPreferred, Set<Integer> capSetPreferred)
            throws Exception {
        final int subId = verifyAndGetValidDataSubId();

        // Start on a less preferred network.
        try (TestNetworkWrapper testNetworkWrapperLessPreferred =
                createTestNetworkForNetworkSelection(subId, capSetLessPreferred)) {
            verifyUnderlyingCellAndRunTest(
                    subId,
                    (subGrp, cellNetwork, cellNetworkCb) -> {
                        final VcnSetupResult vcnSetupResult =
                                setupAndGetVcnNetwork(
                                        subGrp,
                                        cellNetwork,
                                        cellNetworkCb,
                                        vcnConfig,
                                        testNetworkWrapperLessPreferred);

                        // Then bring up a more preferred network, and expect to switch to it.
                        try (TestNetworkWrapper testNetworkWrapperPreferred =
                                createTestNetworkForNetworkSelection(subId, capSetPreferred)) {
                            injectAndVerifyIkeMobikePackets(
                                    testNetworkWrapperPreferred.ikeTunUtils);

                            clearVcnConfigsAndVerifyNetworkTeardown(
                                    subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
                        }
                    });
        }
    }

    private void verifyVcnDoesNotSelectLessPreferredUnderlyingNetwork(
            VcnConfig vcnConfig, Set<Integer> capSetLessPreferred, Set<Integer> capSetPreferred)
            throws Exception {
        final int subId = verifyAndGetValidDataSubId();

        // Start on a more preferred network.
        try (TestNetworkWrapper testNetworkWrapperPreferred =
                createTestNetworkForNetworkSelection(subId, capSetPreferred)) {
            verifyUnderlyingCellAndRunTest(
                    subId,
                    (subGrp, cellNetwork, cellNetworkCb) -> {
                        final VcnSetupResult vcnSetupResult =
                                setupAndGetVcnNetwork(
                                        subGrp,
                                        cellNetwork,
                                        cellNetworkCb,
                                        vcnConfig,
                                        testNetworkWrapperPreferred);

                        // Then bring up a less preferred network, and expect the VCN underlying
                        // network does not change.
                        try (TestNetworkWrapper testNetworkWrapperLessPreferred =
                                createTestNetworkForNetworkSelection(subId, capSetLessPreferred)) {
                            injectAndVerifyIkeDpdPackets(
                                    testNetworkWrapperPreferred.ikeTunUtils,
                                    vcnSetupResult.ikeExchangePortPair);

                            clearVcnConfigsAndVerifyNetworkTeardown(
                                    subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
                        }
                    });
        }
    }

    private void verifyVcnMigratesAfterPreferredUnderlyingNetworkDies(
            VcnConfig vcnConfig, Set<Integer> capSetLessPreferred, Set<Integer> capSetPreferred)
            throws Exception {
        final int subId = verifyAndGetValidDataSubId();

        // Start on a more preferred network
        try (TestNetworkWrapper testNetworkWrapperPreferred =
                createTestNetworkForNetworkSelection(subId, capSetPreferred)) {
            verifyUnderlyingCellAndRunTest(
                    subId,
                    (subGrp, cellNetwork, cellNetworkCb) -> {
                        final VcnSetupResult vcnSetupResult =
                                setupAndGetVcnNetwork(
                                        subGrp,
                                        cellNetwork,
                                        cellNetworkCb,
                                        vcnConfig,
                                        testNetworkWrapperPreferred);

                        // Bring up a less preferred network
                        try (TestNetworkWrapper testNetworkWrapperLessPreferred =
                                createTestNetworkForNetworkSelection(subId, capSetLessPreferred)) {
                            // Teardown the preferred network
                            testNetworkWrapperPreferred.close();
                            testNetworkWrapperPreferred.vcnNetworkCallback.waitForLost();

                            // Verify the VCN switches to the remaining less preferred network
                            injectAndVerifyIkeMobikePackets(
                                    testNetworkWrapperLessPreferred.ikeTunUtils);

                            clearVcnConfigsAndVerifyNetworkTeardown(
                                    subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
                        }
                    });
        }
    }

    private VcnCellUnderlyingNetworkTemplate.Builder createCellTemplateBaseBuilder()
            throws Exception {
        return new VcnCellUnderlyingNetworkTemplate.Builder().setInternet(MATCH_ANY);
    }

    private VcnConfig createVcnConfigPrefersMetered() throws Exception {
        final List<VcnUnderlyingNetworkTemplate> nwTemplates = new ArrayList<>();
        nwTemplates.add(
                createCellTemplateBaseBuilder()
                        .setCbs(MATCH_REQUIRED)
                        .setMetered(MATCH_REQUIRED)
                        .build());
        nwTemplates.add(
                createCellTemplateBaseBuilder()
                        .setCbs(MATCH_REQUIRED)
                        .setMetered(MATCH_FORBIDDEN)
                        .build());
        return buildVcnConfigBase(nwTemplates).setIsTestModeProfile().build();
    }

    @Test
    public void testVcnMigratesToPreferredUnderlyingNetwork_preferMetered() throws Exception {
        verifyVcnMigratesToPreferredUnderlyingNetwork(
                createVcnConfigPrefersMetered(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_CBS),
                Set.of(NET_CAPABILITY_CBS));
    }

    @Test
    public void testVcnDoesNotSelectLessPreferredUnderlyingNetwork_preferMetered()
            throws Exception {
        verifyVcnDoesNotSelectLessPreferredUnderlyingNetwork(
                createVcnConfigPrefersMetered(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_CBS),
                Set.of(NET_CAPABILITY_CBS));
    }

    @Test
    public void testVcnMigratesAfterPreferredUnderlyingNetworkDies_preferMetered()
            throws Exception {
        verifyVcnMigratesAfterPreferredUnderlyingNetworkDies(
                createVcnConfigPrefersMetered(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_CBS),
                Set.of(NET_CAPABILITY_CBS));
    }

    private VcnConfig createVcnConfigPrefersCbs() throws Exception {
        final List<VcnUnderlyingNetworkTemplate> nwTemplates = new ArrayList<>();
        nwTemplates.add(createCellTemplateBaseBuilder().setCbs(MATCH_REQUIRED).build());
        nwTemplates.add(createCellTemplateBaseBuilder().setRcs(MATCH_REQUIRED).build());

        return buildVcnConfigBase(nwTemplates).setIsTestModeProfile().build();
    }

    @Test
    public void testVcnMigratesToPreferredUnderlyingNetwork_preferCbs() throws Exception {
        verifyVcnMigratesToPreferredUnderlyingNetwork(
                createVcnConfigPrefersCbs(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_CBS));
    }

    @Test
    public void testVcnDoesNotSelectLessPreferredUnderlyingNetwork_preferCbs() throws Exception {
        verifyVcnDoesNotSelectLessPreferredUnderlyingNetwork(
                createVcnConfigPrefersCbs(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_CBS));
    }

    @Test
    public void testVcnMigratesAfterPreferredUnderlyingNetworkDies_preferCbs() throws Exception {
        verifyVcnMigratesAfterPreferredUnderlyingNetworkDies(
                createVcnConfigPrefersCbs(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_CBS));
    }

    private VcnConfig createVcnConfigPrefersNonCbs() throws Exception {
        final List<VcnUnderlyingNetworkTemplate> nwTemplates = new ArrayList<>();
        nwTemplates.add(
                createCellTemplateBaseBuilder()
                        .setRcs(MATCH_REQUIRED)
                        .setCbs(MATCH_FORBIDDEN)
                        .build());
        nwTemplates.add(
                createCellTemplateBaseBuilder().setRcs(MATCH_REQUIRED).setCbs(MATCH_ANY).build());

        return buildVcnConfigBase(nwTemplates).setIsTestModeProfile().build();
    }

    @Test
    public void testVcnMigratesToPreferredUnderlyingNetwork_preferNonCbs() throws Exception {
        verifyVcnMigratesToPreferredUnderlyingNetwork(
                createVcnConfigPrefersNonCbs(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS, NET_CAPABILITY_CBS),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS));
    }

    @Test
    public void testVcnDoesNotSelectLessPreferredUnderlyingNetwork_preferNonCbs() throws Exception {
        verifyVcnDoesNotSelectLessPreferredUnderlyingNetwork(
                createVcnConfigPrefersNonCbs(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS, NET_CAPABILITY_CBS),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS));
    }

    @Test
    public void testVcnMigratesAfterPreferredUnderlyingNetworkDies_preferNonCbs() throws Exception {
        verifyVcnMigratesAfterPreferredUnderlyingNetworkDies(
                createVcnConfigPrefersNonCbs(),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS, NET_CAPABILITY_CBS),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS));
    }

    @Test
    public void testSetVcnWithCbsMatchAny_preferCbsNetworkOverUnmatchedNetwork() throws Exception {
        final List<VcnUnderlyingNetworkTemplate> nwTemplates = new ArrayList<>();
        nwTemplates.add(
                createCellTemplateBaseBuilder().setRcs(MATCH_REQUIRED).setCbs(MATCH_ANY).build());

        final VcnConfig vcnConfig = buildVcnConfigBase(nwTemplates).setIsTestModeProfile().build();

        verifyVcnMigratesToPreferredUnderlyingNetwork(
                vcnConfig,
                Set.of(NET_CAPABILITY_NOT_METERED),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS, NET_CAPABILITY_CBS));
    }

    @Test
    public void testSetVcnWithCbsMatchAny_preferNonCbsNetworkOverUnmatchedNetwork()
            throws Exception {
        final List<VcnUnderlyingNetworkTemplate> nwTemplates = new ArrayList<>();
        nwTemplates.add(
                createCellTemplateBaseBuilder().setRcs(MATCH_REQUIRED).setCbs(MATCH_ANY).build());

        final VcnConfig vcnConfig = buildVcnConfigBase(nwTemplates).setIsTestModeProfile().build();

        verifyVcnMigratesToPreferredUnderlyingNetwork(
                vcnConfig,
                Set.of(NET_CAPABILITY_NOT_METERED),
                Set.of(NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_RCS));
    }

    @Test
    public void testVcnNoUnderlyingNetworkSelectedFallback() throws Exception {
        final int subId = verifyAndGetValidDataSubId();
        final List<VcnUnderlyingNetworkTemplate> nwTemplates = new ArrayList<>();
        nwTemplates.add(
                new VcnWifiUnderlyingNetworkTemplate.Builder().setMetered(MATCH_REQUIRED).build());
        final VcnConfig vcnConfig = buildVcnConfigBase(nwTemplates).setIsTestModeProfile().build();

        // Bring up a network that does not match any of the configured network templates
        try (TestNetworkWrapper testNetworkWrapper =
                createTestNetworkWrapper(false /* isMetered */, subId, LOCAL_ADDRESS)) {
            verifyUnderlyingCellAndRunTest(subId, (subGrp, cellNetwork, cellNetworkCb) -> {
                // Verify the VCN can still be set up on the only one underlying network
                final VcnSetupResult vcnSetupResult =
                        setupAndGetVcnNetwork(
                                subGrp,
                                cellNetwork,
                                cellNetworkCb,
                                vcnConfig,
                                testNetworkWrapper);

                clearVcnConfigsAndVerifyNetworkTeardown(
                        subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
            });
        }
    }

    private static class VcnSetupResult {
        public final Network vcnNetwork;
        public final PortPair ikeExchangePortPair;

        VcnSetupResult(Network vcnNetwork, PortPair ikeExchangePortPair) {
            this.vcnNetwork = vcnNetwork;
            this.ikeExchangePortPair = ikeExchangePortPair;
        }
    }

    private VcnSetupResult setupAndGetVcnNetwork(
            @NonNull ParcelUuid subGrp,
            @NonNull Network cellNetwork,
            @NonNull VcnTestNetworkCallback cellNetworkCb,
            @NonNull VcnConfig testModeVcnConfig,
            @NonNull TestNetworkWrapper testNetworkWrapper)
            throws Exception {
        cellNetworkCb.waitForAvailable();
        mVcnManager.setVcnConfig(subGrp, testModeVcnConfig);

        // Wait until the cell Network is lost (due to losing NOT_VCN_MANAGED) to wait for
        // VCN network
        final Network lostCellNetwork = cellNetworkCb.waitForLost();
        assertEquals(cellNetwork, lostCellNetwork);

        final PortPair ikeExchangePortPair =
                injectAndVerifyIkeSessionNegotiationPackets(testNetworkWrapper.ikeTunUtils);

        final Network vcnNetwork = cellNetworkCb.waitForAvailable();
        assertNotNull("VCN network did not come up", vcnNetwork);
        return new VcnSetupResult(vcnNetwork, ikeExchangePortPair);
    }

    private VcnSetupResult setupAndGetVcnNetwork(
            @NonNull ParcelUuid subGrp,
            @NonNull Network cellNetwork,
            @NonNull VcnTestNetworkCallback cellNetworkCb,
            @NonNull TestNetworkWrapper testNetworkWrapper)
            throws Exception {
        return setupAndGetVcnNetwork(
                subGrp, cellNetwork, cellNetworkCb, buildTestModeVcnConfig(), testNetworkWrapper);
    }

    private PortPair injectAndVerifyIkeSessionNegotiationPackets(@NonNull IkeTunUtils ikeTunUtils)
            throws Exception {
        // Generated by forcing IKE to use Test Mode (RandomnessFactory#mIsTestModeEnabled) and
        // capturing IKE packets with a live server.
        final String ikeInitResp =
                "46b8eca1e0d72a189b9f8e0158e1c0a52120222000000000000001d022000030"
                        + "0000002c010100040300000c0100000c800e0080030000080300000803000008"
                        + "02000008000000080400000e28000108000e0000164d3413d855a1642d4d6355"
                        + "a8ef6666bfaa28a4b5264600c9ffbaef7930bd33af49022926013aae0a48d764"
                        + "750ccb3987605957e31a2ef0e6838cfa67af989933c2879434081c4e9787f0d4"
                        + "4da0d7dacca5589702a4537ee4fb18e8db21a948b245260f55212a1c619f61c6"
                        + "fa1caaff4474082f9714b14ef4bcc7b2b8f43fcb939931119e53b05274faec65"
                        + "2816c563529e60c1a88183eba9c456ecb644faf57b726b83e3242e08489d95e9"
                        + "81e59c7ad82cf3cdfb00fe0213c4e65d61e88bbefbd536261027da722a2bbf89"
                        + "c6378e63ce6fbcef282421e5576bba1b2faa3c4c2d41028f91df7ba165a24a18"
                        + "fcba4f96db3e5e0eed76dc7c3c432362dd4a82d32900002461cbd03c08819730"
                        + "f1060ed0c0446f784eb8dd884d3f73f54eb2b0c3071cc4f32900001c00004004"
                        + "07150f3fd9584dbebb7e88ad256c7bfb9b0bb55a2900001c00004005e3aa3788"
                        + "7040e38dbb4de8fd435161cce904ec59290000080000402e290000100000402f"
                        + "00020003000400050000000800004014";
        final String ikeAuthResp =
                "46b8eca1e0d72a189b9f8e0158e1c0a52e20232000000001000000fc240000e0"
                        + "1a666eb2a02b37682436a18fff5e9cef67b9096d6c7887ed235f8b5173c9469e"
                        + "361621b66849de2dbcabf956b3d055cafafd503530543540e81dac9bf8fb8826"
                        + "e08bc99e9ed2185d8f1322c8885abe4f98a9832c694da775eaa4ae69f17b8cbf"
                        + "b009bf82b4bf4012bca489595631c3168cd417f813e7d177d2ceb70766a0773c"
                        + "8819d8763627ddc9455ae3d5a5a03224020a66c8e58c8073c4a1fcf5d67cfa95"
                        + "15de86b392a63ff54ff5572302b9ce7725085b05839252794c3680f5d8f34019"
                        + "fa1930ea045d2a9987850e2049235c7328ef148370b6a3403408b987";

        ikeTunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                0 /* expectedMsgId */,
                false /* expectedUseEncap */,
                ikeInitResp);

        byte[] ikeAuthReqPkt =
                ikeTunUtils.awaitReqAndInjectResp(
                        IKE_DETERMINISTIC_INITIATOR_SPI,
                        1 /* expectedMsgId */,
                        true /* expectedUseEncap */,
                        ikeAuthResp);

        return IkeTunUtils.getSrcDestPortPair(ikeAuthReqPkt);
    }

    private void clearVcnConfigsAndVerifyNetworkTeardown(
            @NonNull ParcelUuid subGrp,
            @NonNull VcnTestNetworkCallback cellNetworkCb,
            @NonNull Network vcnNetwork)
            throws Exception {
        // Clear the history to remove other networks have been matched to the request
        cellNetworkCb.clearLostHistory();

        mVcnManager.clearVcnConfig(subGrp);

        // Expect VCN Network to disappear after VcnConfig is cleared.
        if (mConnectivityManager.getNetworkCapabilities(vcnNetwork) != null) {

            // If not already torn down, wait for teardown. In the event that the underlying network
            // has already regained the NOT_VCN_MANAGED bit (before the VCN's NetworkAgent teardown)
            // the VCN network MAY be immediately replaced with the underlying Cell, which only
            // fires an onAvailable for the new network, as opposed to an onLost() for the VCN
            // network. In that case, check that the VCN network has been unregistered.
            //
            // An alternative approach is to monitor #onAvailable as an indicator of potential
            // network loss. However, since #onAvailable can mean either 1) the new network has
            // higher priority, or 2) the old network is disconnected, this approach will introduce
            // a lot more complexities.
            final Network lostVcnNetwork = cellNetworkCb.waitForLost();
            if (lostVcnNetwork != null) {
                assertEquals(vcnNetwork, lostVcnNetwork);
            } else {
                assertNull(mConnectivityManager.getNetworkCapabilities(vcnNetwork));
            }
        } // Else already torn down, pass.
    }

    @Test
    public void testVcnMigrationAfterNetworkDies() throws Exception {
        final int subId = verifyAndGetValidDataSubId();

        try (TestNetworkWrapper testNetworkWrapper =
                createTestNetworkWrapper(true /* isMetered */, subId, LOCAL_ADDRESS)) {
            verifyUnderlyingCellAndRunTest(subId, (subGrp, cellNetwork, cellNetworkCb) -> {
                final VcnSetupResult vcnSetupResult =
                    setupAndGetVcnNetwork(subGrp, cellNetwork, cellNetworkCb, testNetworkWrapper);

                testNetworkWrapper.close();
                testNetworkWrapper.vcnNetworkCallback.waitForLost();

            try (TestNetworkWrapper secondaryTestNetworkWrapper =
                    createTestNetworkWrapper(true /* isMetered */, subId, LOCAL_ADDRESS)) {
                try {
                    injectAndVerifyIkeMobikePackets(secondaryTestNetworkWrapper.ikeTunUtils);

                    clearVcnConfigsAndVerifyNetworkTeardown(
                            subGrp, cellNetworkCb, vcnSetupResult.vcnNetwork);
                } finally {
                    secondaryTestNetworkWrapper.close();
                }
            }
            });
        }
    }

    private void injectAndVerifyIkeMobikePackets(@NonNull IkeTunUtils ikeTunUtils)
            throws Exception {
        // Generated by forcing IKE to use Test Mode (RandomnessFactory#mIsTestModeEnabled) and
        // capturing IKE packets with a live server. To force the mobility event, use
        // IkeSession#setNetwork with the new desired Network.
        final String ikeUpdateSaResp =
                "46b8eca1e0d72a189b9f8e0158e1c0a52e202520000000020000007c29000060"
                        + "a1fd35f112d92d1df19ce734f6edf56ccda1bfd44ef6de428a097e04d5b40b28"
                        + "3897e42f23dd53e444dc6c676cf9a7d9d73bb3975d663ec351fb5ae4e56a55d8"
                        + "cbcf376a3b99cc6fd858621cc78b3017d895e4309f09a444028dba85";
        final String ikeCreateChildResp =
                "46b8eca1e0d72a189b9f8e0158e1c0a52e20242000000003000000cc210000b0"
                        + "e6bb78203dbe2189806c5cecef5040b8c4c0253895c7c0acea6483a1f0f72425"
                        + "77ab46e18d553329d4ae1bd31cf57eec6ec31ceb1f2ed6b1195cac98b4b97a25"
                        + "115d14c414e44dba8ebbdaf502e43f98a09036bee0ea2a621176300874a3eae8"
                        + "c988357255b4e5923928d335b0ef62a565333fae6a64c85ac30e7da34ceeade4"
                        + "1a161bcad0b51f8209ee1fdaf53d50359ad6b986ecd4290c9f69a34c64ddc0eb"
                        + "73b8f3231f3f4e057404c18d";
        final String ikeDeleteChildResp =
                "46b8eca1e0d72a189b9f8e0158e1c0a52e202520000000040000004c2a000030"
                        + "53d97806d48ce44e0d4e1adf1de36778f77c3823bfaf8186cc71d4dc73497099"
                        + "a9049e7be8a2013affd56ab7";

        ikeTunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                2 /* expectedMsgId */,
                true /* expectedUseEncap */,
                ikeUpdateSaResp);

        // If Kernel migration enabled, it will be used instead of MOBIKE-rekey
        // TODO (b/277939911): Decouple VCN CTS from IKE implementation behavior
        if (!mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_IPSEC_TUNNEL_MIGRATION)) {
            ikeTunUtils.awaitReqAndInjectResp(
                    IKE_DETERMINISTIC_INITIATOR_SPI,
                    3 /* expectedMsgId */,
                    true /* expectedUseEncap */,
                    ikeCreateChildResp);

            ikeTunUtils.awaitReqAndInjectResp(
                    IKE_DETERMINISTIC_INITIATOR_SPI,
                    4 /* expectedMsgId */,
                    true /* expectedUseEncap */,
                    ikeDeleteChildResp);
        }
    }

    private void injectAndVerifyIkeDpdPackets(
            @NonNull IkeTunUtils ikeTunUtils, PortPair localRemotePorts) throws Exception {
        // Generated by forcing IKE to use Test Mode (RandomnessFactory#mIsTestModeEnabled) and
        // capturing IKE packets with a live server.
        final String ikeDpdRequestHex =
                "46b8eca1e0d72a189b9f8e0158e1c0a52E202500000000000000004c00000030"
                        + "3A31D5FAC230FEA67246B0C1A049A28944C341301979EB7B52FC669274B77D5F"
                        + "A6CFE8D768CF390536436D08";

        byte[] ikeDpdRequest =
                IkeTunUtils.buildIkePacket(
                        REMOTE_ADDRESS,
                        LOCAL_ADDRESS,
                        localRemotePorts.dstPort,
                        localRemotePorts.srcPort,
                        true /* useEncap */,
                        hexStringToByteArray(ikeDpdRequestHex));

        ikeTunUtils.injectPacket(ikeDpdRequest);
        ikeTunUtils.awaitResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                0 /* expectedMsgId */,
                true /* expectedUseEncap */);
    }

    @Test
    public void testVcnSafemodeOnTestNetwork() throws Exception {
        final int subId = verifyAndGetValidDataSubId();

        try (TestNetworkWrapper testNetworkWrapper =
                createTestNetworkWrapper(true /* isMetered */, subId, LOCAL_ADDRESS)) {
            // Before the VCN starts, the test network should have NOT_VCN_MANAGED
            waitForExpectedUnderlyingNetworkWithCapabilities(
                    testNetworkWrapper,
                    true /* expectNotVcnManaged */,
                    false /* expectNotMetered */,
                    TestNetworkWrapper.NETWORK_CB_TIMEOUT_MS);
            verifyUnderlyingCellAndRunTest(subId, (subGrp, cellNetwork, cellNetworkCb) -> {
                final VcnSetupResult vcnSetupResult =
                    setupAndGetVcnNetwork(subGrp, cellNetwork, cellNetworkCb, testNetworkWrapper);

                // TODO(b/191801185): use VcnStatusCallbacks to verify safemode

                // Once VCN starts, the test network should lose NOT_VCN_MANAGED
                waitForExpectedUnderlyingNetworkWithCapabilities(
                        testNetworkWrapper,
                        false /* expectNotVcnManaged */,
                        false /* expectNotMetered */,
                        TestNetworkWrapper.NETWORK_CB_TIMEOUT_MS);

                // After VCN has started up, wait for safemode to kick in and expect the
                // underlying Test Network to regain NOT_VCN_MANAGED.
                waitForExpectedUnderlyingNetworkWithCapabilities(
                        testNetworkWrapper,
                        true /* expectNotVcnManaged */,
                        false /* expectNotMetered */,
                        SAFEMODE_TIMEOUT_MILLIS);

                // Verify that VCN Network is also lost in safemode
                final Network lostVcnNetwork = cellNetworkCb.waitForLost();
                assertEquals(vcnSetupResult.vcnNetwork, lostVcnNetwork);

                mVcnManager.clearVcnConfig(subGrp);
            });
        }
    }
}
