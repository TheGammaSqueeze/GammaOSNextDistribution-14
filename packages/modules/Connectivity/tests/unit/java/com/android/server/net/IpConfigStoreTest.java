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

package com.android.server.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.InetAddresses;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.os.Build;
import android.os.HandlerThread;
import android.util.ArrayMap;

import androidx.test.InstrumentationRegistry;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link IpConfigStore}
 */
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class IpConfigStoreTest {
    private static final int TIMEOUT_MS = 5_000;
    private static final int KEY_CONFIG = 17;
    private static final String IFACE_1 = "eth0";
    private static final String IFACE_2 = "eth1";
    private static final String IP_ADDR_1 = "192.168.1.10/24";
    private static final String IP_ADDR_2 = "192.168.1.20/24";
    private static final String DNS_IP_ADDR_1 = "1.2.3.4";
    private static final String DNS_IP_ADDR_2 = "5.6.7.8";

    private static final ArrayList<InetAddress> DNS_SERVERS = new ArrayList<>(List.of(
            InetAddresses.parseNumericAddress(DNS_IP_ADDR_1),
            InetAddresses.parseNumericAddress(DNS_IP_ADDR_2)));
    private static final StaticIpConfiguration STATIC_IP_CONFIG_1 =
            new StaticIpConfiguration.Builder()
                    .setIpAddress(new LinkAddress(IP_ADDR_1))
                    .setDnsServers(DNS_SERVERS)
                    .build();
    private static final StaticIpConfiguration STATIC_IP_CONFIG_2 =
            new StaticIpConfiguration.Builder()
                    .setIpAddress(new LinkAddress(IP_ADDR_2))
                    .setDnsServers(DNS_SERVERS)
                    .build();
    private static final ProxyInfo PROXY_INFO =
            ProxyInfo.buildDirectProxy("10.10.10.10", 88, Arrays.asList("host1", "host2"));

    @Test
    public void backwardCompatibility2to3() throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(byteStream);

        final IpConfiguration expectedConfig =
                newIpConfiguration(IpAssignment.DHCP, ProxySettings.NONE, null, null);

        // Emulate writing to old format.
        writeDhcpConfigV2(outputStream, KEY_CONFIG, expectedConfig);

        InputStream in = new ByteArrayInputStream(byteStream.toByteArray());
        ArrayMap<String, IpConfiguration> configurations = IpConfigStore.readIpConfigurations(in);

        assertNotNull(configurations);
        assertEquals(1, configurations.size());
        IpConfiguration actualConfig = configurations.get(String.valueOf(KEY_CONFIG));
        assertNotNull(actualConfig);
        assertEquals(expectedConfig, actualConfig);
    }

    @Test
    public void staticIpMultiNetworks() throws Exception {
        final IpConfiguration expectedConfig1 = newIpConfiguration(IpAssignment.STATIC,
                ProxySettings.STATIC, STATIC_IP_CONFIG_1, PROXY_INFO);
        final IpConfiguration expectedConfig2 = newIpConfiguration(IpAssignment.STATIC,
                ProxySettings.STATIC, STATIC_IP_CONFIG_2, PROXY_INFO);

        final ArrayMap<String, IpConfiguration> expectedNetworks = new ArrayMap<>();
        expectedNetworks.put(IFACE_1, expectedConfig1);
        expectedNetworks.put(IFACE_2, expectedConfig2);

        final MockedDelayedDiskWrite writer = new MockedDelayedDiskWrite();
        final IpConfigStore store = new IpConfigStore(writer);
        store.writeIpConfigurations("file/path/not/used/", expectedNetworks);

        final InputStream in = new ByteArrayInputStream(writer.mByteStream.toByteArray());
        final ArrayMap<String, IpConfiguration> actualNetworks =
                IpConfigStore.readIpConfigurations(in);
        assertNotNull(actualNetworks);
        assertEquals(2, actualNetworks.size());
        assertEquals(expectedNetworks.get(IFACE_1), actualNetworks.get(IFACE_1));
        assertEquals(expectedNetworks.get(IFACE_2), actualNetworks.get(IFACE_2));
    }

    @Test
    public void readIpConfigurationFromFilePath() throws Exception {
        final HandlerThread testHandlerThread = new HandlerThread("IpConfigStoreTest");
        final DelayedDiskWrite.Dependencies dependencies =
                new DelayedDiskWrite.Dependencies() {
                    @Override
                    public HandlerThread makeHandlerThread() {
                        return testHandlerThread;
                    }
                    @Override
                    public void quitHandlerThread(HandlerThread handlerThread) {
                        // Don't join in here, quitHandlerThread runs on the
                        // handler thread itself.
                        testHandlerThread.quitSafely();
                    }
        };

        final IpConfiguration ipConfig = newIpConfiguration(IpAssignment.STATIC,
                ProxySettings.STATIC, STATIC_IP_CONFIG_1, PROXY_INFO);
        final ArrayMap<String, IpConfiguration> expectedNetworks = new ArrayMap<>();
        expectedNetworks.put(IFACE_1, ipConfig);

        // Write IP config to specific file path and read it later.
        final Context context = InstrumentationRegistry.getContext();
        final File configFile = new File(context.getFilesDir().getPath(),
                "IpConfigStoreTest-ipconfig.txt");
        final DelayedDiskWrite writer = new DelayedDiskWrite(dependencies);
        final IpConfigStore store = new IpConfigStore(writer);
        store.writeIpConfigurations(configFile.getPath(), expectedNetworks);
        testHandlerThread.join();

        // Read IP config from the file path.
        final ArrayMap<String, IpConfiguration> actualNetworks =
                IpConfigStore.readIpConfigurations(configFile.getPath());
        assertNotNull(actualNetworks);
        assertEquals(1, actualNetworks.size());
        assertEquals(expectedNetworks.get(IFACE_1), actualNetworks.get(IFACE_1));

        // Return an empty array when reading IP configuration from an non-exist config file.
        final ArrayMap<String, IpConfiguration> emptyNetworks =
                IpConfigStore.readIpConfigurations("/dev/null");
        assertNotNull(emptyNetworks);
        assertEquals(0, emptyNetworks.size());

        configFile.delete();
    }

    private IpConfiguration newIpConfiguration(IpAssignment ipAssignment,
            ProxySettings proxySettings, StaticIpConfiguration staticIpConfig, ProxyInfo info) {
        final IpConfiguration config = new IpConfiguration();
        config.setIpAssignment(ipAssignment);
        config.setProxySettings(proxySettings);
        config.setStaticIpConfiguration(staticIpConfig);
        config.setHttpProxy(info);
        return config;
    }

    // This is simplified snapshot of code that was used to store values in V2 format (key as int).
    private static void writeDhcpConfigV2(DataOutputStream out, int configKey,
            IpConfiguration config) throws IOException {
        out.writeInt(2);  // VERSION 2
        switch (config.getIpAssignment()) {
            case DHCP:
                out.writeUTF("ipAssignment");
                out.writeUTF(config.getIpAssignment().toString());
                break;
            default:
                fail("Not supported in test environment");
        }

        out.writeUTF("id");
        out.writeInt(configKey);
        out.writeUTF("eos");
    }

    /** Synchronously writes into given byte steam */
    private static class MockedDelayedDiskWrite extends DelayedDiskWrite {
        final ByteArrayOutputStream mByteStream = new ByteArrayOutputStream();

        @Override
        public void write(String filePath, Writer w) {
            DataOutputStream outputStream = new DataOutputStream(mByteStream);

            try {
                w.onWriteCalled(outputStream);
            } catch (IOException e) {
                fail();
            }
        }
    }
}
