/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static android.content.pm.PackageManager.FEATURE_WIFI;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkUtils;
import android.net.cts.util.CtsNetUtils;
import android.platform.test.annotations.AppModeFull;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.testutils.DeviceConfigRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class MultinetworkApiTest {
    @Rule
    public final DeviceConfigRule mDeviceConfigRule = new DeviceConfigRule();

    static {
        System.loadLibrary("nativemultinetwork_jni");
    }

    private static final String TAG = "MultinetworkNativeApiTest";
    static final String GOOGLE_PRIVATE_DNS_SERVER = "dns.google";

    /**
     * @return 0 on success
     */
    private static native int runGetaddrinfoCheck(long networkHandle);
    private static native int runSetprocnetwork(long networkHandle);
    private static native int runSetsocknetwork(long networkHandle);
    private static native int runDatagramCheck(long networkHandle);
    private static native void runResNapiMalformedCheck(long networkHandle);
    private static native void runResNcancelCheck(long networkHandle);
    private static native void runResNqueryCheck(long networkHandle);
    private static native void runResNsendCheck(long networkHandle);
    private static native void runResNnxDomainCheck(long networkHandle);


    private ContentResolver mCR;
    private ConnectivityManager mCM;
    private CtsNetUtils mCtsNetUtils;
    private Context mContext;
    private Network mRequestedCellNetwork;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mCM = mContext.getSystemService(ConnectivityManager.class);
        mCR = mContext.getContentResolver();
        mCtsNetUtils = new CtsNetUtils(mContext);
    }

    @After
    public void tearDown() {
        if (mCtsNetUtils.cellConnectAttempted()) {
            mCtsNetUtils.disconnectFromCell();
        }
    }

    @Test
    public void testGetaddrinfo() throws Exception {
        for (Network network : getTestableNetworks()) {
            int errno = runGetaddrinfoCheck(network.getNetworkHandle());
            if (errno != 0) {
                throw new ErrnoException(
                        "getaddrinfo on " + mCM.getNetworkInfo(network), -errno);
            }
        }
    }

    @Test
    @AppModeFull(reason = "CHANGE_NETWORK_STATE permission can't be granted to instant apps")
    public void testSetprocnetwork() throws Exception {
        // Hopefully no prior test in this process space has set a default network.
        assertNull(mCM.getProcessDefaultNetwork());
        assertEquals(0, NetworkUtils.getBoundNetworkForProcess());

        for (Network network : getTestableNetworks()) {
            mCM.setProcessDefaultNetwork(null);
            assertNull(mCM.getProcessDefaultNetwork());

            int errno = runSetprocnetwork(network.getNetworkHandle());
            if (errno != 0) {
                throw new ErrnoException(
                        "setprocnetwork on " + mCM.getNetworkInfo(network), -errno);
            }
            Network processDefault = mCM.getProcessDefaultNetwork();
            assertNotNull(processDefault);
            assertEquals(network, processDefault);
            // TODO: open DatagramSockets, connect them to 192.0.2.1 and 2001:db8::,
            // and ensure that the source address is in fact on this network as
            // determined by mCM.getLinkProperties(network).

            mCM.setProcessDefaultNetwork(null);
        }

        for (Network network : getTestableNetworks()) {
            NetworkUtils.bindProcessToNetwork(0);
            assertNull(mCM.getBoundNetworkForProcess());

            int errno = runSetprocnetwork(network.getNetworkHandle());
            if (errno != 0) {
                throw new ErrnoException(
                        "setprocnetwork on " + mCM.getNetworkInfo(network), -errno);
            }
            assertEquals(network, new Network(mCM.getBoundNetworkForProcess()));
            // TODO: open DatagramSockets, connect them to 192.0.2.1 and 2001:db8::,
            // and ensure that the source address is in fact on this network as
            // determined by mCM.getLinkProperties(network).

            NetworkUtils.bindProcessToNetwork(0);
        }
    }

    @Test
    @AppModeFull(reason = "CHANGE_NETWORK_STATE permission can't be granted to instant apps")
    public void testSetsocknetwork() throws Exception {
        for (Network network : getTestableNetworks()) {
            int errno = runSetsocknetwork(network.getNetworkHandle());
            if (errno != 0) {
                throw new ErrnoException(
                        "setsocknetwork on " + mCM.getNetworkInfo(network), -errno);
            }
        }
    }

    @Test
    public void testNativeDatagramTransmission() throws Exception {
        for (Network network : getTestableNetworks()) {
            int errno = runDatagramCheck(network.getNetworkHandle());
            if (errno != 0) {
                throw new ErrnoException(
                        "DatagramCheck on " + mCM.getNetworkInfo(network), -errno);
            }
        }
    }

    @Test
    public void testNoSuchNetwork() throws Exception {
        final Network eNoNet = new Network(54321);
        assertNull(mCM.getNetworkInfo(eNoNet));

        final long eNoNetHandle = eNoNet.getNetworkHandle();
        assertEquals(-OsConstants.ENONET, runSetsocknetwork(eNoNetHandle));
        assertEquals(-OsConstants.ENONET, runSetprocnetwork(eNoNetHandle));
        // TODO: correct test permissions so this call is not silently re-mapped
        // to query on the default network.
        // assertEquals(-OsConstants.ENONET, runGetaddrinfoCheck(eNoNetHandle));
    }

    @Test
    public void testNetworkHandle() throws Exception {
        // Test Network -> NetworkHandle -> Network results in the same Network.
        for (Network network : getTestableNetworks()) {
            long networkHandle = network.getNetworkHandle();
            Network newNetwork = Network.fromNetworkHandle(networkHandle);
            assertEquals(newNetwork, network);
        }

        // Test that only obfuscated handles are allowed.
        try {
            Network.fromNetworkHandle(100);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            Network.fromNetworkHandle(-1);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            Network.fromNetworkHandle(0);
            fail();
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testResNApi() throws Exception {
        for (Network network : getTestableNetworks()) {
            // Throws AssertionError directly in jni function if test fail.
            runResNqueryCheck(network.getNetworkHandle());
            runResNsendCheck(network.getNetworkHandle());
            runResNcancelCheck(network.getNetworkHandle());
            runResNapiMalformedCheck(network.getNetworkHandle());

            final NetworkCapabilities nc = mCM.getNetworkCapabilities(network);
            // Some cellular networks configure their DNS servers never to return NXDOMAIN, so don't
            // test NXDOMAIN on these DNS servers.
            // b/144521720
            if (nc != null && !nc.hasTransport(TRANSPORT_CELLULAR)) {
                runResNnxDomainCheck(network.getNetworkHandle());
            }
        }
    }

    @Test
    @AppModeFull(reason = "WRITE_SECURE_SETTINGS permission can't be granted to instant apps")
    public void testResNApiNXDomainPrivateDns() throws Exception {
        // Use async private DNS resolution to avoid flakes due to races applying the setting
        mDeviceConfigRule.setConfig(NAMESPACE_CONNECTIVITY,
                "networkmonitor_async_privdns_resolution", "1");
        mCtsNetUtils.reconnectWifiIfSupported();
        mCtsNetUtils.reconnectCellIfSupported();

        mCtsNetUtils.storePrivateDnsSetting();

        mDeviceConfigRule.runAfterNextCleanup(() -> {
            mCtsNetUtils.reconnectWifiIfSupported();
            mCtsNetUtils.reconnectCellIfSupported();
        });
        // Enable private DNS strict mode and set server to dns.google before doing NxDomain test.
        // b/144521720
        try {
            mCtsNetUtils.setPrivateDnsStrictMode(GOOGLE_PRIVATE_DNS_SERVER);
            for (Network network : getTestableNetworks()) {
              // Wait for private DNS setting to propagate.
              mCtsNetUtils.awaitPrivateDnsSetting("NxDomain test wait private DNS setting timeout",
                        network, GOOGLE_PRIVATE_DNS_SERVER, true);
              runResNnxDomainCheck(network.getNetworkHandle());
            }
        } finally {
            mCtsNetUtils.restorePrivateDnsSetting();
        }
    }

    /**
     * Get all testable Networks with internet capability.
     */
    private Set<Network> getTestableNetworks() throws InterruptedException {
        // Obtain cell and Wi-Fi through CtsNetUtils (which uses NetworkCallbacks), as they may have
        // just been reconnected by the test using NetworkCallbacks, so synchronous calls may not
        // yet return them (synchronous calls and callbacks should not be mixed for a given
        // Network).
        final Set<Network> testableNetworks = new ArraySet<>();
        if (mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            if (!mCtsNetUtils.cellConnectAttempted()) {
                mRequestedCellNetwork = mCtsNetUtils.connectToCell();
            }
            assertNotNull("Cell network requested but not obtained", mRequestedCellNetwork);
            testableNetworks.add(mRequestedCellNetwork);
        }

        if (mContext.getPackageManager().hasSystemFeature(FEATURE_WIFI)) {
            testableNetworks.add(mCtsNetUtils.ensureWifiConnected());
        }

        // Obtain other networks through the synchronous API, if any.
        for (Network network : mCtsNetUtils.getTestableNetworks()) {
            final NetworkCapabilities nc = mCM.getNetworkCapabilities(network);
            if (nc != null
                    && !nc.hasTransport(TRANSPORT_WIFI)
                    && !nc.hasTransport(TRANSPORT_CELLULAR)) {
                testableNetworks.add(network);
            }
        }

        // In practice this should not happen as getTestableNetworks throws if there is no network
        // at all.
        assertFalse("This device does not support WiFi nor cell data, and does not have any other "
                        + "network connected. This test requires at least one internet-providing "
                        + "network.",
                testableNetworks.isEmpty());
        return testableNetworks;
    }
}
