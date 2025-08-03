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

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.cts.util.CtsNetUtils;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ThrowingRunnable;

import java.net.Inet4Address;
import java.net.InetAddress;

/** This class supports running a test with a test network. */
public class TestNetworkRunnable implements ThrowingRunnable {
    private static final int IP4_PREFIX_LEN = 32;
    private static final int IP6_PREFIX_LEN = 128;

    private static final InetAddress DEFAULT_ADDRESS_4 =
            InetAddress.parseNumericAddress("192.2.0.2");
    private static final InetAddress DEFAULT_ADDRESS_6 =
            InetAddress.parseNumericAddress("2001:db8:1::2");

    private static final Context sContext = InstrumentationRegistry.getContext();
    private static final ConnectivityManager sCm =
            sContext.getSystemService(ConnectivityManager.class);

    private final Test mTest;

    public TestNetworkRunnable(Test test) {
        mTest = test;
    }

    private void runTest() throws Exception {
        final TestNetworkManager tnm = sContext.getSystemService(TestNetworkManager.class);

        // Non-final; these variables ensure we clean up properly after our test if we
        // have allocated test network resources
        TestNetworkInterface testIface = null;
        TestNetworkCallback tunNetworkCallback = null;

        final CtsNetUtils ctsNetUtils = new CtsNetUtils(sContext);
        final InetAddress[] addresses = mTest.getTestNetworkAddresses();
        final LinkAddress[] linkAddresses = new LinkAddress[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            InetAddress address = addresses[i];
            if (address instanceof Inet4Address) {
                linkAddresses[i] = new LinkAddress(address, IP4_PREFIX_LEN);
            } else {
                linkAddresses[i] = new LinkAddress(address, IP6_PREFIX_LEN);
            }
        }

        try {
            // Build underlying test network
            testIface = tnm.createTunInterface(linkAddresses);

            // Hold on to this callback to ensure network does not get reaped.
            tunNetworkCallback = ctsNetUtils.setupAndGetTestNetwork(testIface.getInterfaceName());

            mTest.runTest(testIface, tunNetworkCallback);
        } finally {
            try {
                mTest.cleanupTest();
            } catch (Exception e) {
                // No action
            }

            if (testIface != null) {
                testIface.getFileDescriptor().close();
            }


            final Network testNetwork = tunNetworkCallback.currentNetwork;
            if (testNetwork != null) {
                tnm.teardownTestNetwork(testNetwork);
            }
            // Ensure test network being torn down.
            tunNetworkCallback.waitForLost();

            if (tunNetworkCallback != null) {
                sCm.unregisterNetworkCallback(tunNetworkCallback);
            }
        }
    }

    @Override
    public void run() throws Exception {
        if (sContext.checkSelfPermission(MANAGE_TEST_NETWORKS) == PERMISSION_GRANTED) {
            runTest();
        } else {
            runWithShellPermissionIdentity(this::runTest, MANAGE_TEST_NETWORKS);
        }
    }

    /** Interface for test caller to configure the test that will be run with a test network */
    public interface Test {
        /** Runs the test with a test network */
        void runTest(TestNetworkInterface testIface, TestNetworkCallback tunNetworkCallback)
                throws Exception;

        /** Cleans up when the test is finished or interrupted */
        void cleanupTest();

        /** Returns the IP addresses that will be used by the test network */
        default InetAddress[] getTestNetworkAddresses() {
            return new InetAddress[] {DEFAULT_ADDRESS_4, DEFAULT_ADDRESS_6};
        }
    }
}
