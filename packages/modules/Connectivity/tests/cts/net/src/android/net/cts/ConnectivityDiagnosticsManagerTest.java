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

package android.net.cts;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_PROBES_ATTEMPTED_BITMASK;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_PROBES_SUCCEEDED_BITMASK;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_VALIDATION_RESULT;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.NETWORK_VALIDATION_RESULT_SKIPPED;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.NETWORK_VALIDATION_RESULT_VALID;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.DETECTION_METHOD_DNS_EVENTS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.DETECTION_METHOD_TCP_METRICS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.KEY_DNS_CONSECUTIVE_TIMEOUTS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.KEY_TCP_METRICS_COLLECTION_PERIOD_MILLIS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.KEY_TCP_PACKET_FAIL_RATE;
import static android.net.ConnectivityDiagnosticsManager.persistableBundleEquals;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.testutils.Cleanup.testAndCleanup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityDiagnosticsManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.ArrayUtils;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.ArrayTrackRecord;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(DevSdkIgnoreRunner.class)
@IgnoreUpTo(Build.VERSION_CODES.Q) // ConnectivityDiagnosticsManager did not exist in Q
@AppModeFull(reason = "CHANGE_NETWORK_STATE, MANAGE_TEST_NETWORKS not grantable to instant apps")
public class ConnectivityDiagnosticsManagerTest {
    private static final String TAG = ConnectivityDiagnosticsManagerTest.class.getSimpleName();

    private static final int CALLBACK_TIMEOUT_MILLIS = 5000;
    private static final int NO_CALLBACK_INVOKED_TIMEOUT = 500;
    private static final long TIMESTAMP = 123456789L;
    private static final int DNS_CONSECUTIVE_TIMEOUTS = 5;
    private static final int COLLECTION_PERIOD_MILLIS = 5000;
    private static final int FAIL_RATE_PERCENTAGE = 100;
    private static final int UNKNOWN_DETECTION_METHOD = 4;
    private static final int FILTERED_UNKNOWN_DETECTION_METHOD = 0;
    private static final int CARRIER_CONFIG_CHANGED_BROADCAST_TIMEOUT = 10000;
    private static final int DELAY_FOR_BROADCAST_IDLE = 30_000;

    private static final Executor INLINE_EXECUTOR = x -> x.run();

    private static final NetworkRequest TEST_NETWORK_REQUEST =
            new NetworkRequest.Builder()
                    .addTransportType(TRANSPORT_TEST)
                    .removeCapability(NET_CAPABILITY_TRUSTED)
                    .removeCapability(NET_CAPABILITY_NOT_VPN)
                    .build();

    private static final String SHA_256 = "SHA-256";

    private static final NetworkRequest CELLULAR_NETWORK_REQUEST =
            new NetworkRequest.Builder()
                    .addTransportType(TRANSPORT_CELLULAR)
                    .addCapability(NET_CAPABILITY_INTERNET)
                    .build();

    private static final IBinder BINDER = new Binder();

    // Lock for accessing Shell Permissions. Use of this lock around adoptShellPermissionIdentity,
    // runWithShellPermissionIdentity, and callWithShellPermissionIdentity ensures Shell Permission
    // is not interrupted by another operation (which would drop all previously adopted
    // permissions).
    private final Object mShellPermissionsIdentityLock = new Object();

    private Context mContext;
    private ConnectivityManager mConnectivityManager;
    private ConnectivityDiagnosticsManager mCdm;
    private CarrierConfigManager mCarrierConfigManager;
    private PackageManager mPackageManager;
    private TelephonyManager mTelephonyManager;

    // Callback used to keep TestNetworks up when there are no other outstanding NetworkRequests
    // for it.
    private TestNetworkCallback mTestNetworkCallback;
    private Network mTestNetwork;
    private ParcelFileDescriptor mTestNetworkFD;

    private List<TestConnectivityDiagnosticsCallback> mRegisteredCallbacks;

    private static void waitForBroadcastIdle(final long timeoutMs) throws InterruptedException {
        final long st = SystemClock.elapsedRealtime();
        // am wait-for-broadcast-idle will return immediately if the queue is already idle.
        final Thread t = new Thread(() -> runShellCommand("am wait-for-broadcast-idle"));
        t.start();
        // Two notes about the case where join() times out :
        // • It is fine to continue running the test. The broadcast queue might still be busy, but
        //   there is no way as of now to wait for a particular broadcast to have been been
        //   processed so it's possible the one the caller is interested in is in fact done,
        //   making it worth running the rest of the test.
        // • The thread will continue running its course in the test process. In this case it is
        //   fine because the wait-for-broadcast-idle command doesn't have side effects, and the
        //   thread does nothing else.
        t.join(timeoutMs);
        Log.i(TAG, "Waited for broadcast idle for " + (SystemClock.elapsedRealtime() - st) + "ms");
    }

    private void runWithShellPermissionIdentity(ThrowingRunnable runnable,
            String... permissions) {
        synchronized (mShellPermissionsIdentityLock) {
            SystemUtil.runWithShellPermissionIdentity(runnable, permissions);
        }
    }

    private <T> T callWithShellPermissionIdentity(Callable<T> callable, String... permissions)
            throws Exception {
        synchronized (mShellPermissionsIdentityLock) {
            return SystemUtil.callWithShellPermissionIdentity(callable, permissions);
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mCdm = mContext.getSystemService(ConnectivityDiagnosticsManager.class);
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        mPackageManager = mContext.getPackageManager();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);

        mTestNetworkCallback = new TestNetworkCallback();
        mConnectivityManager.requestNetwork(TEST_NETWORK_REQUEST, mTestNetworkCallback);

        mRegisteredCallbacks = new ArrayList<>();
    }

    @After
    public void tearDown() throws Exception {
        mConnectivityManager.unregisterNetworkCallback(mTestNetworkCallback);
        if (mTestNetwork != null) {
            runWithShellPermissionIdentity(() -> {
                final TestNetworkManager tnm = mContext.getSystemService(TestNetworkManager.class);
                tnm.teardownTestNetwork(mTestNetwork);
            }, android.Manifest.permission.MANAGE_TEST_NETWORKS);
            mTestNetwork = null;
        }

        if (mTestNetworkFD != null) {
            mTestNetworkFD.close();
            mTestNetworkFD = null;
        }

        for (TestConnectivityDiagnosticsCallback cb : mRegisteredCallbacks) {
            mCdm.unregisterConnectivityDiagnosticsCallback(cb);
        }
    }

    @Test
    public void testRegisterConnectivityDiagnosticsCallback() throws Exception {
        mTestNetworkFD = setUpTestNetwork().getFileDescriptor();
        mTestNetwork = mTestNetworkCallback.waitForAvailable();

        final TestConnectivityDiagnosticsCallback cb =
                createAndRegisterConnectivityDiagnosticsCallback(TEST_NETWORK_REQUEST);

        final String interfaceName =
                mConnectivityManager.getLinkProperties(mTestNetwork).getInterfaceName();

        cb.expectOnConnectivityReportAvailable(mTestNetwork, interfaceName);
        cb.assertNoCallback();
    }

    @Test
    public void testRegisterCallbackWithCarrierPrivileges() throws Exception {
        assumeTrue(mPackageManager.hasSystemFeature(FEATURE_TELEPHONY));

        final int subId = SubscriptionManager.getDefaultSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            fail("Need an active subscription. Please ensure that the device has working mobile"
                    + " data.");
        }

        final CarrierConfigReceiver carrierConfigReceiver = new CarrierConfigReceiver(subId);
        mContext.registerReceiver(
                carrierConfigReceiver,
                new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        final TestNetworkCallback testNetworkCallback = new TestNetworkCallback();

        testAndCleanup(() -> {
            doBroadcastCarrierConfigsAndVerifyOnConnectivityReportAvailable(
                    subId, carrierConfigReceiver, testNetworkCallback);
        }, () -> {
                runWithShellPermissionIdentity(
                    () -> mCarrierConfigManager.overrideConfig(subId, null),
                    android.Manifest.permission.MODIFY_PHONE_STATE);
            mConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            mContext.unregisterReceiver(carrierConfigReceiver);
            });
    }

    private String getCertHashForThisPackage() throws Exception {
        final PackageInfo pkgInfo =
                mPackageManager.getPackageInfo(
                        mContext.getOpPackageName(), PackageManager.GET_SIGNATURES);
        final MessageDigest md = MessageDigest.getInstance(SHA_256);
        final byte[] certHash = md.digest(pkgInfo.signatures[0].toByteArray());
        return IccUtils.bytesToHexString(certHash);
    }

    private void doBroadcastCarrierConfigsAndVerifyOnConnectivityReportAvailable(
            int subId,
            @NonNull CarrierConfigReceiver carrierConfigReceiver,
            @NonNull TestNetworkCallback testNetworkCallback)
            throws Exception {
        final PersistableBundle carrierConfigs = new PersistableBundle();
        carrierConfigs.putStringArray(
                CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY,
                new String[] {getCertHashForThisPackage()});

        runWithShellPermissionIdentity(
                () -> {
                    mCarrierConfigManager.overrideConfig(subId, carrierConfigs);
                    mCarrierConfigManager.notifyConfigChangedForSubId(subId);
                },
                android.Manifest.permission.MODIFY_PHONE_STATE);

        assertTrue("Didn't receive broadcast for ACTION_CARRIER_CONFIG_CHANGED for subId=" + subId,
                carrierConfigReceiver.waitForCarrierConfigChanged());

        // Wait for CarrierPrivilegesTracker to receive the ACTION_CARRIER_CONFIG_CHANGED
        // broadcast. CPT then needs to update the corresponding DataConnection, which then
        // updates ConnectivityService. Unfortunately, this update to the NetworkCapabilities in
        // CS does not trigger NetworkCallback#onCapabilitiesChanged as changing the
        // administratorUids is not a publicly visible change. Start by waiting for broadcast
        // idle to make sure Telephony has received the carrier config change broadcast ; the
        // delay to pass this information to CS is accounted in the delay in waiting for the
        // callback.
        waitForBroadcastIdle(DELAY_FOR_BROADCAST_IDLE);

        Thread.sleep(5_000);

        // TODO(b/157779832): This should use android.permission.CHANGE_NETWORK_STATE. However, the
        // shell does not have CHANGE_NETWORK_STATE, so use CONNECTIVITY_INTERNAL until the shell
        // permissions are updated.
        runWithShellPermissionIdentity(
                () -> mConnectivityManager.requestNetwork(
                        CELLULAR_NETWORK_REQUEST, testNetworkCallback),
                android.Manifest.permission.CONNECTIVITY_INTERNAL);

        final Network network = testNetworkCallback.waitForAvailable();
        assertNotNull(network);

        // TODO(b/217559768): Receiving carrier config change and immediately checking carrier
        //  privileges is racy, as the CP status is updated after receiving the same signal. Move
        //  the CP check after sleep to temporarily reduce the flakiness. This will soon be fixed
        //  by switching to CarrierPrivilegesListener.
        assertTrue("Don't have Carrier Privileges after adding cert for this package",
                mTelephonyManager.createForSubscriptionId(subId).hasCarrierPrivileges());

        final TestConnectivityDiagnosticsCallback connDiagsCallback =
                createAndRegisterConnectivityDiagnosticsCallback(CELLULAR_NETWORK_REQUEST);

        final String interfaceName =
                mConnectivityManager.getLinkProperties(network).getInterfaceName();
        connDiagsCallback.maybeVerifyConnectivityReportAvailable(
                network, interfaceName, TRANSPORT_CELLULAR, NETWORK_VALIDATION_RESULT_VALID);
        connDiagsCallback.assertNoCallback();
    }

    @Test
    public void testRegisterDuplicateConnectivityDiagnosticsCallback() {
        final TestConnectivityDiagnosticsCallback cb =
                createAndRegisterConnectivityDiagnosticsCallback(TEST_NETWORK_REQUEST);

        try {
            mCdm.registerConnectivityDiagnosticsCallback(TEST_NETWORK_REQUEST, INLINE_EXECUTOR, cb);
            fail("Registering the same callback twice should throw an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testUnregisterConnectivityDiagnosticsCallback() {
        final TestConnectivityDiagnosticsCallback cb = new TestConnectivityDiagnosticsCallback();
        mCdm.registerConnectivityDiagnosticsCallback(TEST_NETWORK_REQUEST, INLINE_EXECUTOR, cb);
        mCdm.unregisterConnectivityDiagnosticsCallback(cb);
    }

    @Test
    public void testUnregisterUnknownConnectivityDiagnosticsCallback() {
        // Expected to silently ignore the unregister() call
        mCdm.unregisterConnectivityDiagnosticsCallback(new TestConnectivityDiagnosticsCallback());
    }

    @Test
    public void testOnConnectivityReportAvailable() throws Exception {
        final TestConnectivityDiagnosticsCallback cb =
                createAndRegisterConnectivityDiagnosticsCallback(TEST_NETWORK_REQUEST);

        mTestNetworkFD = setUpTestNetwork().getFileDescriptor();
        mTestNetwork = mTestNetworkCallback.waitForAvailable();

        final String interfaceName =
                mConnectivityManager.getLinkProperties(mTestNetwork).getInterfaceName();

        cb.expectOnConnectivityReportAvailable(mTestNetwork, interfaceName);
        cb.assertNoCallback();
    }

    @Test
    public void testOnDataStallSuspected_DnsEvents() throws Exception {
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(KEY_DNS_CONSECUTIVE_TIMEOUTS, DNS_CONSECUTIVE_TIMEOUTS);

        verifyOnDataStallSuspected(DETECTION_METHOD_DNS_EVENTS, TIMESTAMP, extras);
    }

    @Test
    public void testOnDataStallSuspected_TcpMetrics() throws Exception {
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(KEY_TCP_METRICS_COLLECTION_PERIOD_MILLIS, COLLECTION_PERIOD_MILLIS);
        extras.putInt(KEY_TCP_PACKET_FAIL_RATE, FAIL_RATE_PERCENTAGE);

        verifyOnDataStallSuspected(DETECTION_METHOD_TCP_METRICS, TIMESTAMP, extras);
    }

    @Test
    public void testOnDataStallSuspected_UnknownDetectionMethod() throws Exception {
        verifyOnDataStallSuspected(
                UNKNOWN_DETECTION_METHOD,
                FILTERED_UNKNOWN_DETECTION_METHOD,
                TIMESTAMP,
                PersistableBundle.EMPTY);
    }

    private void verifyOnDataStallSuspected(
            int detectionMethod, long timestampMillis, @NonNull PersistableBundle extras)
            throws Exception {
        // Input detection method is expected to match received detection method
        verifyOnDataStallSuspected(detectionMethod, detectionMethod, timestampMillis, extras);
    }

    private void verifyOnDataStallSuspected(
            int inputDetectionMethod,
            int expectedDetectionMethod,
            long timestampMillis,
            @NonNull PersistableBundle extras)
            throws Exception {
        mTestNetworkFD = setUpTestNetwork().getFileDescriptor();
        mTestNetwork = mTestNetworkCallback.waitForAvailable();

        final TestConnectivityDiagnosticsCallback cb =
                createAndRegisterConnectivityDiagnosticsCallback(TEST_NETWORK_REQUEST);

        final String interfaceName =
                mConnectivityManager.getLinkProperties(mTestNetwork).getInterfaceName();

        cb.expectOnConnectivityReportAvailable(mTestNetwork, interfaceName);

        runWithShellPermissionIdentity(
                () -> mConnectivityManager.simulateDataStall(
                        inputDetectionMethod, timestampMillis, mTestNetwork, extras),
                android.Manifest.permission.MANAGE_TEST_NETWORKS);

        cb.expectOnDataStallSuspected(
                mTestNetwork, interfaceName, expectedDetectionMethod, timestampMillis, extras);
        cb.assertNoCallback();
    }

    @Test
    public void testOnNetworkConnectivityReportedTrue() throws Exception {
        verifyOnNetworkConnectivityReported(true /* hasConnectivity */);
    }

    @Test
    public void testOnNetworkConnectivityReportedFalse() throws Exception {
        verifyOnNetworkConnectivityReported(false /* hasConnectivity */);
    }

    private void verifyOnNetworkConnectivityReported(boolean hasConnectivity) throws Exception {
        mTestNetworkFD = setUpTestNetwork().getFileDescriptor();
        mTestNetwork = mTestNetworkCallback.waitForAvailable();

        final TestConnectivityDiagnosticsCallback cb =
                createAndRegisterConnectivityDiagnosticsCallback(TEST_NETWORK_REQUEST);

        // onConnectivityReportAvailable always invoked when the test network is established
        final String interfaceName =
                mConnectivityManager.getLinkProperties(mTestNetwork).getInterfaceName();
        cb.expectOnConnectivityReportAvailable(mTestNetwork, interfaceName);
        cb.assertNoCallback();

        mConnectivityManager.reportNetworkConnectivity(mTestNetwork, hasConnectivity);

        cb.expectOnNetworkConnectivityReported(mTestNetwork, hasConnectivity);

        // All calls to #onNetworkConnectivityReported are expected to be accompanied by a call to
        // #onConnectivityReportAvailable for T+ (for R, ConnectivityReports were only sent when the
        // Network was re-validated - when reported connectivity != known connectivity). On S,
        // recent module versions will have the callback, but not the earliest ones.
        if (!hasConnectivity) {
            cb.expectOnConnectivityReportAvailable(mTestNetwork, interfaceName);
        } else if (SdkLevel.isAtLeastS()) {
            cb.maybeVerifyConnectivityReportAvailable(mTestNetwork, interfaceName, TRANSPORT_TEST,
                    getPossibleDiagnosticsValidationResults(),
                    SdkLevel.isAtLeastT() /* requireCallbackFired */);
        }

        cb.assertNoCallback();
    }

    private TestConnectivityDiagnosticsCallback createAndRegisterConnectivityDiagnosticsCallback(
            NetworkRequest request) {
        final TestConnectivityDiagnosticsCallback cb = new TestConnectivityDiagnosticsCallback();
        mCdm.registerConnectivityDiagnosticsCallback(request, INLINE_EXECUTOR, cb);
        mRegisteredCallbacks.add(cb);
        return cb;
    }

    /**
     * Registers a test NetworkAgent with ConnectivityService with limited capabilities, which leads
     * to the Network being validated.
     */
    @NonNull
    private TestNetworkInterface setUpTestNetwork() throws Exception {
        final int[] administratorUids = new int[] {Process.myUid()};
        return callWithShellPermissionIdentity(
                () -> {
                    final TestNetworkManager tnm =
                            mContext.getSystemService(TestNetworkManager.class);
                    final TestNetworkInterface tni = tnm.createTunInterface(new LinkAddress[0]);
                    tnm.setupTestNetwork(tni.getInterfaceName(), administratorUids, BINDER);
                    return tni;
                }, android.Manifest.permission.MANAGE_TEST_NETWORKS);
    }

    private static class TestConnectivityDiagnosticsCallback
            extends ConnectivityDiagnosticsCallback {
        private final ArrayTrackRecord<Object>.ReadHead mHistory =
                new ArrayTrackRecord<Object>().newReadHead();

        @Override
        public void onConnectivityReportAvailable(ConnectivityReport report) {
            mHistory.add(report);
        }

        @Override
        public void onDataStallSuspected(DataStallReport report) {
            mHistory.add(report);
        }

        @Override
        public void onNetworkConnectivityReported(Network network, boolean hasConnectivity) {
            mHistory.add(new Pair<Network, Boolean>(network, hasConnectivity));
        }

        public void expectOnConnectivityReportAvailable(
                @NonNull Network network, @NonNull String interfaceName) {
            // Test Networks both do not require validation and are not tested for validation. This
            // results in the validation result being reported as SKIPPED for S+ (for R, the
            // platform marked these Networks as VALID).

            maybeVerifyConnectivityReportAvailable(network, interfaceName, TRANSPORT_TEST,
                    getPossibleDiagnosticsValidationResults(), true);
        }

        public void maybeVerifyConnectivityReportAvailable(@NonNull Network network,
                @NonNull String interfaceName, int transportType, int expectedValidationResult) {
            maybeVerifyConnectivityReportAvailable(network, interfaceName, transportType,
                    new ArraySet<>(Collections.singletonList(expectedValidationResult)), true);
        }

        public void maybeVerifyConnectivityReportAvailable(@NonNull Network network,
                @NonNull String interfaceName, int transportType,
                Set<Integer> possibleValidationResults, boolean requireCallbackFired) {
            final ConnectivityReport result =
                    (ConnectivityReport) mHistory.poll(CALLBACK_TIMEOUT_MILLIS, x -> true);
            if (!requireCallbackFired && result == null) {
                return;
            }
            assertEquals(network, result.getNetwork());

            final NetworkCapabilities nc = result.getNetworkCapabilities();
            assertNotNull(nc);
            assertTrue(nc.hasTransport(transportType));
            assertNotNull(result.getLinkProperties());
            assertEquals(interfaceName, result.getLinkProperties().getInterfaceName());

            final PersistableBundle extras = result.getAdditionalInfo();
            assertTrue(extras.containsKey(KEY_NETWORK_VALIDATION_RESULT));
            final int actualValidationResult = extras.getInt(KEY_NETWORK_VALIDATION_RESULT);
            assertTrue("Network validation result is incorrect: " + actualValidationResult,
                    possibleValidationResults.contains(actualValidationResult));

            assertTrue(extras.containsKey(KEY_NETWORK_PROBES_SUCCEEDED_BITMASK));
            final int probesSucceeded = extras.getInt(KEY_NETWORK_VALIDATION_RESULT);
            assertTrue("PROBES_SUCCEEDED mask not in expected range", probesSucceeded >= 0);

            assertTrue(extras.containsKey(KEY_NETWORK_PROBES_ATTEMPTED_BITMASK));
            final int probesAttempted = extras.getInt(KEY_NETWORK_PROBES_ATTEMPTED_BITMASK);
            assertTrue("PROBES_ATTEMPTED mask not in expected range", probesAttempted >= 0);
        }

        public void expectOnDataStallSuspected(
                @NonNull Network network,
                @NonNull String interfaceName,
                int detectionMethod,
                long timestampMillis,
                @NonNull PersistableBundle extras) {
            final DataStallReport result =
                    (DataStallReport) mHistory.poll(CALLBACK_TIMEOUT_MILLIS, x -> true);
            assertEquals(network, result.getNetwork());
            assertEquals(detectionMethod, result.getDetectionMethod());
            assertEquals(timestampMillis, result.getReportTimestamp());

            final NetworkCapabilities nc = result.getNetworkCapabilities();
            assertNotNull(nc);
            assertTrue(nc.hasTransport(TRANSPORT_TEST));
            assertNotNull(result.getLinkProperties());
            assertEquals(interfaceName, result.getLinkProperties().getInterfaceName());

            assertTrue(persistableBundleEquals(extras, result.getStallDetails()));
        }

        public void expectOnNetworkConnectivityReported(
                @NonNull Network network, boolean hasConnectivity) {
            final Pair<Network, Boolean> result =
                    (Pair<Network, Boolean>) mHistory.poll(CALLBACK_TIMEOUT_MILLIS, x -> true);
            assertEquals(network, result.first /* network */);
            assertEquals(hasConnectivity, result.second /* hasConnectivity */);
        }

        public void assertNoCallback() {
            // If no more callbacks exist, there should be nothing left in the ReadHead
            assertNull("Unexpected event in history",
                    mHistory.poll(NO_CALLBACK_INVOKED_TIMEOUT, x -> true));
        }
    }

    private static Set<Integer> getPossibleDiagnosticsValidationResults() {
        final Set<Integer> possibleValidationResults = new ArraySet<>();
        possibleValidationResults.add(NETWORK_VALIDATION_RESULT_SKIPPED);

        // In S, some early module versions will return NETWORK_VALIDATION_RESULT_VALID.
        // Starting from T, all module versions should only return SKIPPED. For platform < T,
        // accept both values.
        if (!SdkLevel.isAtLeastT()) {
            possibleValidationResults.add(NETWORK_VALIDATION_RESULT_VALID);
        }
        return possibleValidationResults;
    }

    private class CarrierConfigReceiver extends BroadcastReceiver {
        // CountDownLatch used to wait for this BroadcastReceiver to be notified of a CarrierConfig
        // change. This latch will be counted down if a broadcast indicates this package has carrier
        // configs, or if an Exception occurs in #onReceive.
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final int mSubId;

        // #onReceive may encounter Exceptions while running on the Process' main Thread and
        // #waitForCarrierConfigChanged checks the cached Exception from the test Thread. These
        // Exceptions must be cached and thrown later, as throwing on the Process' main Thread will
        // crash the process and cause other tests to fail.
        private Exception mOnReceiveException;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                // Received an incorrect broadcast - ignore
                return;
            }

            final int subId =
                    intent.getIntExtra(
                            CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (mSubId != subId) {
                // Received a broadcast for the wrong subId - ignore
                return;
            }

            final PersistableBundle carrierConfigs;
            try {
                carrierConfigs = callWithShellPermissionIdentity(
                        () -> mCarrierConfigManager.getConfigForSubId(subId),
                        android.Manifest.permission.READ_PHONE_STATE);
            } catch (Exception exception) {
                // callWithShellPermissionIdentity() threw an Exception - cache it and allow
                // waitForCarrierConfigChanged() to throw it
                mOnReceiveException = exception;
                mLatch.countDown();
                return;
            }

            if (!CarrierConfigManager.isConfigForIdentifiedCarrier(carrierConfigs)) {
                // Configs are not for an identified carrier (meaning they are defaults) - ignore
                return;
            }

            final String[] certs = carrierConfigs.getStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY);
            try {
                if (ArrayUtils.contains(certs, getCertHashForThisPackage())) {
                    // Received an update for this package's cert hash - countdown and exit
                    mLatch.countDown();
                }
                // Broadcast is for the right subId, but does not show this package as Carrier
                // Privileged. Keep waiting for a broadcast that indicates Carrier Privileges.
            } catch (Exception exception) {
                // getCertHashForThisPackage() threw an Exception - cache it and allow
                // waitForCarrierConfigChanged() to throw it
                mOnReceiveException = exception;
                mLatch.countDown();
            }
        }

        /**
         * Waits for the CarrierConfig changed broadcast to reach this CarrierConfigReceiver.
         *
         * <p>Must be called from the Test Thread.
         *
         * @throws Exception if an Exception occurred during any #onReceive invocation
         */
        boolean waitForCarrierConfigChanged() throws Exception {
            final boolean result = mLatch.await(CARRIER_CONFIG_CHANGED_BROADCAST_TIMEOUT,
                    TimeUnit.MILLISECONDS);

            if (mOnReceiveException != null) {
                throw mOnReceiveException;
            }

            return result;
        }
    }
}
