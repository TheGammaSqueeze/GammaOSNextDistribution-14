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

package android.ipsec.ike.cts;

import android.content.Context;
import android.ipsec.ike.cts.TestNetworkUtils.TestNetworkCallback;
import android.net.ConnectivityManager;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.net.LinkAddress;
import android.net.Network;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.ipsec.ike.ChildSessionConfiguration;
import android.net.ipsec.ike.IkeSessionConnectionInfo;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.util.CloseGuard;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
abstract class IkeTestNetworkBase extends IkeTestBase {
    // Static state to reduce setup/teardown
    static Context sContext = InstrumentationRegistry.getContext();
    static ConnectivityManager sCM = sContext.getSystemService(ConnectivityManager.class);
    static TestNetworkManager sTNM;

    // This method is guaranteed to run in subclasses and will run before subclasses' @BeforeClass
    // methods.
    @BeforeClass
    public static void setUpPermissionBeforeClass() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        sTNM = sContext.getSystemService(TestNetworkManager.class);
    }

    // This method is guaranteed to run in subclasses and will run after subclasses' @AfterClass
    // methods.
    @AfterClass
    public static void tearDownPermissionAfterClass() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    // Package private for use in IkeExceptionTest
    static class TunNetworkContext implements Closeable {
        public final ParcelFileDescriptor tunFd;
        public final TestNetworkCallback tunNetworkCallback;
        public final Network tunNetwork;
        public final IkeTunUtils tunUtils;

        TunNetworkContext(InetAddress... addresses) throws Exception {
            final LinkAddress[] linkAddresses = new LinkAddress[addresses.length];
            for (int i = 0; i < linkAddresses.length; i++) {
                InetAddress addr = addresses[i];
                if (addr instanceof Inet4Address) {
                    linkAddresses[i] = new LinkAddress(addr, IP4_PREFIX_LEN);
                } else {
                    linkAddresses[i] = new LinkAddress(addr, IP6_PREFIX_LEN);
                }
            }

            try {
                final TestNetworkInterface testIface =
                        SdkLevel.isAtLeastS()
                                ? sTNM.createTunInterface(Arrays.asList(linkAddresses))
                                // createTunInterface(LinkAddress[]) was TestApi until R.
                                // Wrap linkAddresses in an Object[], so Method#invoke(Object,
                                // Object...) doesn't treat linkAddresses as the varargs input.
                                : (TestNetworkInterface)
                                        sTNM.getClass()
                                                .getMethod(
                                                        "createTunInterface", LinkAddress[].class)
                                                .invoke(sTNM, new Object[] {linkAddresses});

                tunFd = testIface.getFileDescriptor();
                tunNetworkCallback =
                        TestNetworkUtils.setupAndGetTestNetwork(
                                sCM, sTNM, testIface.getInterfaceName(), new Binder());
                tunNetwork = tunNetworkCallback.getNetworkBlocking();
            } catch (Exception e) {
                close();
                throw e;
            }

            tunUtils = new IkeTunUtils(tunFd);
        }

        @Override
        public void close() throws IOException {
            if (tunNetworkCallback != null) {
                sCM.unregisterNetworkCallback(tunNetworkCallback);
            }

            if (tunNetwork != null) {
                sTNM.teardownTestNetwork(tunNetwork);
            }

            if (tunFd != null) {
                tunFd.close();
            }
        }
    }

    static class TunIpSecNetworkWrapper implements AutoCloseable {
        public final IpSecManager.IpSecTunnelInterface tunnelIface;
        public final TestNetworkCallback networkCallback;
        public final Network ipSecNetwork;

        private final CloseGuard mCloseGuard = new CloseGuard();

        TunIpSecNetworkWrapper(
                IkeSessionConnectionInfo connectionInfo,
                ChildSessionConfiguration childConf,
                IpSecTransform inTransform,
                IpSecTransform outTransform)
                throws Exception {
            final IpSecManager ipsecMgr = sContext.getSystemService(IpSecManager.class);

            tunnelIface =
                    ipsecMgr.createIpSecTunnelInterface(
                            connectionInfo.getLocalAddress(),
                            connectionInfo.getRemoteAddress(),
                            connectionInfo.getNetwork());

            final LinkAddress localInner = childConf.getInternalAddresses().get(0);
            tunnelIface.addAddress(localInner.getAddress(), localInner.getPrefixLength());

            ipsecMgr.applyTunnelModeTransform(tunnelIface, IpSecManager.DIRECTION_IN, inTransform);
            ipsecMgr.applyTunnelModeTransform(tunnelIface, IpSecManager.DIRECTION_OUT, inTransform);

            networkCallback =
                    TestNetworkUtils.setupAndGetTestNetwork(
                            sCM, sTNM, tunnelIface.getInterfaceName(), new Binder());
            ipSecNetwork = networkCallback.getNetworkBlocking();
        }

        @Override
        public void close() {
            mCloseGuard.close();

            if (tunnelIface != null) {
                tunnelIface.close();
            }
            if (networkCallback != null) {
                sCM.unregisterNetworkCallback(networkCallback);
            }
            if (ipSecNetwork != null) {
                sTNM.teardownTestNetwork(ipSecNetwork);
            }
        }

        @Override
        public void finalize() {
            mCloseGuard.warnIfOpen();
            close();
        }
    }
}
