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

package com.android.server.ethernet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.InetAddresses;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.util.ArrayMap;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class EthernetConfigStoreTest {
    private static final LinkAddress LINKADDR = new LinkAddress("192.168.1.100/25");
    private static final InetAddress GATEWAY = InetAddresses.parseNumericAddress("192.168.1.1");
    private static final InetAddress DNS1 = InetAddresses.parseNumericAddress("8.8.8.8");
    private static final InetAddress DNS2 = InetAddresses.parseNumericAddress("8.8.4.4");
    private static final StaticIpConfiguration STATIC_IP_CONFIG =
            new StaticIpConfiguration.Builder()
                    .setIpAddress(LINKADDR)
                    .setGateway(GATEWAY)
                    .setDnsServers(new ArrayList<InetAddress>(
                            List.of(DNS1, DNS2)))
                    .build();
    private static final ProxyInfo PROXY_INFO = ProxyInfo.buildDirectProxy("test", 8888);
    private static final IpConfiguration APEX_IP_CONFIG =
            new IpConfiguration(IpAssignment.DHCP, ProxySettings.NONE, null, null);
    private static final IpConfiguration LEGACY_IP_CONFIG =
            new IpConfiguration(IpAssignment.STATIC, ProxySettings.STATIC, STATIC_IP_CONFIG,
                    PROXY_INFO);

    private EthernetConfigStore mEthernetConfigStore;
    private File mApexTestDir;
    private File mLegacyTestDir;
    private File mApexConfigFile;
    private File mLegacyConfigFile;

    private void createTestDir() {
        final Context context = InstrumentationRegistry.getContext();
        final File baseDir = context.getFilesDir();
        mApexTestDir = new File(baseDir.getPath() + "/apex");
        mApexTestDir.mkdirs();

        mLegacyTestDir = new File(baseDir.getPath() + "/legacy");
        mLegacyTestDir.mkdirs();
    }

    @Before
    public void setUp() {
        createTestDir();
        mEthernetConfigStore = new EthernetConfigStore();
    }

    @After
    public void tearDown() {
        mApexTestDir.delete();
        mLegacyTestDir.delete();
    }

    private void assertConfigFileExist(final String filepath) {
        assertTrue(new File(filepath).exists());
    }

    /** Wait for the delayed write operation completes. */
    private void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            fail("Thread was interrupted");
        }
    }

    @Test
    public void testWriteIpConfigToApexFilePathAndRead() throws Exception {
        // Write the config file to the apex file path, pretend the config file exits and
        // check if IP config should be read from apex file path.
        mApexConfigFile = new File(mApexTestDir.getPath(), "test.txt");
        mEthernetConfigStore.write("eth0", APEX_IP_CONFIG, mApexConfigFile.getPath());
        waitForMs(50);

        mEthernetConfigStore.read(mApexTestDir.getPath(), mLegacyTestDir.getPath(), "/test.txt");
        final ArrayMap<String, IpConfiguration> ipConfigurations =
                mEthernetConfigStore.getIpConfigurations();
        assertEquals(APEX_IP_CONFIG, ipConfigurations.get("eth0"));

        mApexConfigFile.delete();
    }

    @Test
    public void testWriteIpConfigToLegacyFilePathAndRead() throws Exception {
        // Write the config file to the legacy file path, pretend the config file exits and
        // check if IP config should be read from legacy file path.
        mLegacyConfigFile = new File(mLegacyTestDir, "test.txt");
        mEthernetConfigStore.write("0", LEGACY_IP_CONFIG, mLegacyConfigFile.getPath());
        waitForMs(50);

        mEthernetConfigStore.read(mApexTestDir.getPath(), mLegacyTestDir.getPath(), "/test.txt");
        final ArrayMap<String, IpConfiguration> ipConfigurations =
                mEthernetConfigStore.getIpConfigurations();
        assertEquals(LEGACY_IP_CONFIG, ipConfigurations.get("0"));

        // Check the same config file in apex file path is created.
        assertConfigFileExist(mApexTestDir.getPath() + "/test.txt");

        final File apexConfigFile = new File(mApexTestDir.getPath() + "/test.txt");
        apexConfigFile.delete();
        mLegacyConfigFile.delete();
    }
}
