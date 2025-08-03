/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.telephony.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.SystemProperties;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test MockModemService interfaces. */
public class ConnectivityManagerTestOnMockModem {
    private static final String TAG = "ConnectivityManagerTestOnMockModem";
    private static final int TIMEOUT_NETWORK_VALIDATION = 20000;
    private static final int WAIT_MSEC = 500;
    private static final int NETWORK_AVAILABLE_SEC = 60;
    private static boolean sIsValidate;
    private static boolean sIsOnAvailable;
    private static Network sDefaultNetwork;
    private static Object sIsValidateLock = new Object();
    private static Object sIsOnAvailableLock = new Object();
    private static CMNetworkCallback sNetworkCallback;
    private static MockModemManager sMockModemManager;
    private static TelephonyManager sTelephonyManager;
    private static SubscriptionManager sSubscriptionManager;
    private static ConnectivityManager sConnectivityManager;
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final String BOOT_ALLOW_MOCK_MODEM_PROPERTY = "ro.boot.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final String RESOURCE_PACKAGE_NAME = "android";
    private static boolean sIsMultiSimDevice;

    private static class CMNetworkCallback extends NetworkCallback {
        final CountDownLatch mNetworkLatch = new CountDownLatch(1);

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
            sDefaultNetwork = network;
            Log.d(
                    TAG,
                    "Network capabilities changed. network: "
                            + network
                            + ", NetworkCapabilities: "
                            + nc);

            if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                Log.d(
                        TAG,
                        "Network capabilities changed. network: "
                                + network
                                + " ,validation: Pass!");
                synchronized (sIsValidateLock) {
                    sIsValidate = true;
                    sIsValidateLock.notify();
                }
            } else {
                Log.d(
                        TAG,
                        "Network capabilities changed. network: "
                                + network
                                + " ,validation: Fail!");
                synchronized (sIsValidateLock) {
                    sIsValidate = false;
                }
            }
        }

        @Override
        public void onLost(Network network) {
            sDefaultNetwork = network;
            Log.d(TAG, "onLost(): network: " + network);
            synchronized (sIsOnAvailableLock) {
                sIsOnAvailable = false;
            }
        }

        @Override
        public void onAvailable(Network network) {
            sDefaultNetwork = network;
            Log.d(TAG, "onAvailable(): network: " + network);
            synchronized (sIsOnAvailableLock) {
                sIsOnAvailable = true;
                mNetworkLatch.countDown();
            }
        }

        public void awaitNetwork() throws InterruptedException {
            Log.d(TAG, "awaitNetwork(): " +  NETWORK_AVAILABLE_SEC + " sec");
            mNetworkLatch.await(NETWORK_AVAILABLE_SEC, TimeUnit.SECONDS);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {}
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        TimeUnit.SECONDS.sleep(10);
        Log.d(TAG, "ConnectivityManagerTestOnMockModem#beforeAllTests()");

        if (!hasTelephonyFeature()) {
            return;
        }

        enforceMockModemDeveloperSetting();
        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        sIsMultiSimDevice = isMultiSim(sTelephonyManager);

        sConnectivityManager =
                (ConnectivityManager) getContext().getSystemService(ConnectivityManager.class);

        sSubscriptionManager =
                InstrumentationRegistry.getInstrumentation().getContext()
                        .getSystemService(SubscriptionManager.class);

        registerNetworkCallback();

        Network activeNetwork = sConnectivityManager.getActiveNetwork();
        NetworkCapabilities nc;
        if (activeNetwork == null) {
            fail("This test requires there is an active network. But the active network is null.");
        }

        nc = sConnectivityManager.getNetworkCapabilities(activeNetwork);

        if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            fail("This test requires there is a transport type with TRANSPORT_CELLULAR.");
        }

        if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            fail(
                    "This test requires there is a network capabilities with"
                            + " NET_CAPABILITY_INTERNET.");
        }

        if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            fail(
                    "This test requires there is a network capabilities with"
                            + " NET_CAPABILITY_VALIDATED.");
        }

        unregisterNetworkCallback();

        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        Log.d(TAG, "ConnectivityManagerTestOnMockModem#afterAllTests()");

        if (!hasTelephonyFeature()) {
            return;
        }
        // Rebind all interfaces which is binding to MockModemService to default.
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.disconnectMockModemService());
        sMockModemManager = null;
    }

    @Before
    public void beforeTest() throws Exception {
        assumeTrue(hasTelephonyFeature());
        registerNetworkCallback();
    }

    @After
    public void afterTest() {
        // unregister the network call back
        if (sNetworkCallback != null) {
            unregisterNetworkCallback();
        }
    }

    private static boolean isMultiSim(TelephonyManager tm) {
        return tm != null && tm.getPhoneCount() > 1;
    }

    private static boolean isSimHotSwapCapable() {
        boolean isSimHotSwapCapable = false;
        int resourceId =
                getContext()
                        .getResources()
                        .getIdentifier("config_hotswapCapable", "bool", RESOURCE_PACKAGE_NAME);

        if (resourceId > 0) {
            isSimHotSwapCapable = getContext().getResources().getBoolean(resourceId);
        } else {
            Log.d(TAG, "Fail to get the resource Id, using default.");
        }

        Log.d(TAG, "isSimHotSwapCapable = " + (isSimHotSwapCapable ? "true" : "false"));

        return isSimHotSwapCapable;
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static boolean hasTelephonyFeature() {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return false;
        }
        return true;
    }

    private static void enforceMockModemDeveloperSetting() throws Exception {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        boolean isAllowedForBoot =
                SystemProperties.getBoolean(BOOT_ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        if (!(isAllowed || isAllowedForBoot) && !DEBUG) {
            throw new IllegalStateException(
                    "!! Enable Mock Modem before running this test !! "
                            + "Developer options => Allow Mock Modem");
        }
    }

    private int getRegState(int domain) {
        int reg;

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PHONE_STATE");

        ServiceState ss = sTelephonyManager.getServiceState();
        assertNotNull(ss);

        NetworkRegistrationInfo nri =
                ss.getNetworkRegistrationInfo(domain, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertNotNull(nri);

        reg = nri.getRegistrationState();
        Log.d(TAG, "SS: " + nri.registrationStateToString(reg));

        return reg;
    }

    private static synchronized boolean getNetworkValidated() {
        Log.d(TAG, "getNetworkValidated: " + sIsValidate);
        return sIsValidate;
    }

    private static synchronized boolean getNetworkOnAvailable() {
        Log.d(TAG, "getNetworkOnAvailable: " + sIsOnAvailable);
        return sIsOnAvailable;
    }

    private static synchronized Network getDefaultNetwork() {
        Log.d(TAG, "getDefaultNetwork: enter ");
        return sDefaultNetwork;
    }

    private static void registerNetworkCallback() {
        sNetworkCallback = new CMNetworkCallback();
        try {
            sConnectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build(),
                    sNetworkCallback);
            Log.d(TAG, "registered networkCallback");
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception during registerNetworkCallback():" + e);
        }
    }

    private static void unregisterNetworkCallback() {
        try {
            sConnectivityManager.unregisterNetworkCallback(sNetworkCallback);
            Log.d(TAG, "unregisterNetworkCallback");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException during unregisterNetworkCallback(): ", e);
        } finally {
            sNetworkCallback = null;
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.net.ConnectivityManager.NetworkCallback#onCapabilitiesChanged",
                "android.net.ConnectivityManager.NetworkCallback#onAvailable"
            })
    public void testNetworkValidated() throws Throwable {
        Log.d(TAG, "ConnectivityManagerTestOnMockModem#testNetworkValidated");

        assumeTrue(isSimHotSwapCapable());

        int slotId = 0;

        // Insert a SIM
        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);

        // Enter Service
        Log.d(TAG, "testNetworkValidated: Enter Service");
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);

        // Get the list of available subscriptions
        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        sSubscriptionManager, (sm) -> sm.getActiveSubscriptionInfoList());

        Log.d(TAG, "subscriptionInfoList: " + subscriptionInfoList);

        for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
            if (slotId == subscriptionInfo.getSimSlotIndex()) {
                sSubscriptionManager.setDefaultDataSubId(subscriptionInfo.getSubscriptionId());
                sTelephonyManager.setDataEnabled(subscriptionInfo.getSubscriptionId(), true);
                Log.d(TAG, "Set Data on slot: " + slotId);
            }
        }

        // make sure the network is available
        sNetworkCallback.awaitNetwork();
        assertTrue(getNetworkOnAvailable());

        // make sure the network is validated
        sConnectivityManager.reportNetworkConnectivity(getDefaultNetwork(), false);
        waitForExpectedValidationState(true, TIMEOUT_NETWORK_VALIDATION);
        assertTrue(getNetworkValidated());

        // Leave Service
        Log.d(TAG, "testNetworkValidated: Leave Service");
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, false);

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId);
    }

    private static void waitForExpectedValidationState(boolean validated, long timeout)
            throws InterruptedException {
        Log.d(
                TAG,
                "Wait For Expected ValidationState: expected: "
                        + validated
                        + ", timeout: "
                        + timeout
                        + "ms");
        synchronized (sIsValidateLock) {
            long expectedTimeout = System.currentTimeMillis() + timeout;
            boolean expectedResult = validated;
            while (System.currentTimeMillis() < expectedTimeout
                    && getNetworkValidated() != expectedResult) {
                sIsValidateLock.wait(WAIT_MSEC);
            }
        }
    }

    @Test
    public void testDDSChange() throws Throwable {
        Log.d(TAG, "ConnectivityManagerTestOnMockModem#testDDSChange");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);

        int slotId_0 = 0;
        int slotId_1 = 1;

        // Insert a SIM
        sMockModemManager.insertSimCard(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.insertSimCard(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET);

        // Enter Service
        Log.d(TAG, "testDsdsServiceStateChange: Enter Service");
        sMockModemManager.changeNetworkService(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        sMockModemManager.changeNetworkService(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET, true);

        for (int slotIndex = 0; slotIndex < 2; slotIndex++) {
            boolean isDataEnabled = sTelephonyManager.getDataEnabled(slotIndex);
            Log.d(TAG, "Data enabled status for SIM slot " + slotIndex + ":  " + isDataEnabled);
        }

        String packageName = getContext().getPackageName();
        Log.d(TAG, "packageName: " + packageName);

        // Get the list of available subscriptions
        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        sSubscriptionManager, (sm) -> sm.getActiveSubscriptionInfoList());

        Log.d(TAG, "subscriptionInfoList: " + subscriptionInfoList);

        // Loop through the subscriptions to get the phone id
        // Set the data enable by phone id
        for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
            int slotId = subscriptionInfo.getSimSlotIndex();
            sSubscriptionManager.setDefaultDataSubId(subscriptionInfo.getSubscriptionId());
            sTelephonyManager.setDataEnabled(subscriptionInfo.getSubscriptionId(), true);
            Log.d(TAG, "Set Data on slot: " + slotId);
            // make sure the network is available
            sNetworkCallback.awaitNetwork();
            Log.d(TAG, "Check Data : " + getNetworkOnAvailable());
            assertTrue(getNetworkOnAvailable());
        }

        // Leave Service
        Log.d(TAG, "testDsdsServiceStateChange: Leave Service");
        sMockModemManager.changeNetworkService(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
        sMockModemManager.changeNetworkService(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET, false);

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId_0);
        sMockModemManager.removeSimCard(slotId_1);
    }
}

