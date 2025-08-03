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

package android.permission.cts;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.EthernetManager;
import android.net.EthernetNetworkUpdateRequest;
import android.net.IpConfiguration;
import android.net.NetworkCapabilities;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Test protected android.net.EthernetManager methods cannot be called without permissions.
 */
@RunWith(AndroidJUnit4.class)
public class EthernetManagerPermissionTest {
    private static final String TEST_IFACE = "test123abc789";
    private EthernetManager mEthernetManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mEthernetManager = mContext.getSystemService(EthernetManager.class);
        // mEthernetManager may be null depending on the device's configuration.
        assumeNotNull(mEthernetManager);
    }

    private EthernetNetworkUpdateRequest buildUpdateRequest() {
        return new EthernetNetworkUpdateRequest.Builder()
                .setIpConfiguration(new IpConfiguration.Builder().build())
                .setNetworkCapabilities(new NetworkCapabilities.Builder().build())
                .build();
    }

    private EthernetNetworkUpdateRequest buildUpdateRequestWithoutCapabilities() {
        return new EthernetNetworkUpdateRequest.Builder()
                .setIpConfiguration(new IpConfiguration.Builder().build())
                .build();
    }

    /**
     * Verify that calling {@link EthernetManager#updateConfiguration(String,
     * EthernetNetworkUpdateRequest, Executor, BiConsumer)} requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#MANAGE_ETHERNET_NETWORKS}.
     */
    @Test
    public void testUpdateConfigurationRequiresPermissionManageEthernetNetworks() {
        assertThrows("Should not be able to call updateConfiguration without permission",
                SecurityException.class,
                () -> mEthernetManager.updateConfiguration(TEST_IFACE,
                        buildUpdateRequestWithoutCapabilities(), null, null));
    }

    /**
     * Verify that calling {@link EthernetManager#enableInterface}
     * requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#MANAGE_ETHERNET_NETWORKS}.
     */
    @Test
    public void testEnableInterface() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call enableInterface without permission",
                SecurityException.class,
                () -> mEthernetManager.enableInterface(TEST_IFACE, null, null));
    }

    /**
     * Verify that calling {@link EthernetManager#disableInterface}
     * requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#MANAGE_ETHERNET_NETWORKS}.
     */
    @Test
    public void testDisableInterface() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call disableInterface without permission",
                SecurityException.class,
                () -> mEthernetManager.disableInterface(TEST_IFACE, null, null));
    }
}
