/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiMigration;
import android.net.wifi.WifiSsid;
import android.util.SparseIntArray;
import android.util.Xml;

import androidx.test.filters.SmallTest;

import com.android.internal.util.FastXmlSerializer;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.EncryptedData;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.server.wifi.util.SettingsMigrationDataHolder;
import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link com.android.server.wifi.SoftApStoreData}.
 */
@SmallTest
public class SoftApStoreDataTest extends WifiBaseTest {
    private static final String TEST_SSID = "SSID";
    private static final WifiSsid TEST_WIFI_SSID = WifiSsid.fromUtf8Text(TEST_SSID);
    private static final String TEST_BSSID = "aa:22:33:aa:bb:cc";
    private static final String TEST_PASSPHRASE = "TestPassphrase";
    private static final String TEST_WPA2_PASSPHRASE = "Wpa2Test";
    private static final int TEST_CHANNEL = 0;
    private static final int TEST_CHANNEL_2G = 1;
    private static final int TEST_CHANNEL_5G = 149;
    private static final boolean TEST_HIDDEN = false;
    private static final int TEST_BAND = SoftApConfiguration.BAND_2GHZ
            | SoftApConfiguration.BAND_5GHZ;
    private static final int TEST_BAND_2G = SoftApConfiguration.BAND_2GHZ;
    private static final int TEST_BAND_5G = SoftApConfiguration.BAND_5GHZ;
    private static final int TEST_OLD_BAND = WifiConfiguration.AP_BAND_ANY;
    private static final int TEST_SECURITY = SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
    private static final boolean TEST_CLIENT_CONTROL_BY_USER = false;
    private static final boolean TEST_AUTO_SHUTDOWN_ENABLED = true;
    private static final int TEST_MAX_NUMBER_OF_CLIENTS = 10;
    private static final long TEST_SHUTDOWN_TIMEOUT_MILLIS = 600_000;
    private static final long TEST_BRIDTED_MODE_SHUTDOWN_TIMEOUT_MILLIS = 300_000;
    private static final ArrayList<MacAddress> TEST_BLOCKEDLIST = new ArrayList<>();
    private static final String TEST_BLOCKED_CLIENT = "11:22:33:44:55:66";
    private static final ArrayList<MacAddress> TEST_ALLOWEDLIST = new ArrayList<>();
    private static final String TEST_ALLOWED_CLIENT = "aa:bb:cc:dd:ee:ff";
    private static final boolean TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED = false;
    private static final int TEST_MAC_RANDOMIZATIONSETTING =
            SoftApConfiguration.RANDOMIZATION_NONE;
    private static final SparseIntArray TEST_CHANNELS = new SparseIntArray() {{
            put(TEST_BAND_2G, TEST_CHANNEL_2G);
            put(TEST_BAND_5G, TEST_CHANNEL_5G);
            }};
    private static final SparseIntArray TEST_CHANNELS_IN_R_CONFIG = new SparseIntArray() {{
            put(TEST_BAND, TEST_CHANNEL);
            }};

    private static final boolean TEST_80211AX_ENABLED = false;
    private static final boolean TEST_80211BE_ENABLED = false;
    private static final boolean TEST_USER_CONFIGURATION = false;
    private static final String TEST_TWO_VENDOR_ELEMENTS_HEX = "DD04AABBCCDDDD0401020304";

    private static final String TEST_CONFIG_STRING_FROM_WIFICONFIGURATION =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<int name=\"Band\" value=\"" + TEST_OLD_BAND + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL + "\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Wpa2Passphrase\">" + TEST_WPA2_PASSPHRASE + "</string>\n";

    private static final String TEST_CONFIG_STRING_WITH_OLD_SSID_DESIGN =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<int name=\"Band\" value=\"" + TEST_OLD_BAND + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL + "\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Wpa2Passphrase\">" + TEST_WPA2_PASSPHRASE + "</string>\n";

    private static final String TEST_CONFIG_STRING_WITH_NEW_BAND_DESIGN_IN_R =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<int name=\"ApBand\" value=\"" + TEST_BAND + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL + "\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Passphrase\">" + TEST_PASSPHRASE + "</string>\n";

    private static final String TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<string name=\"Bssid\">" + TEST_BSSID + "</string>\n"
                    + "<int name=\"ApBand\" value=\"" + TEST_BAND + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL + "\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Passphrase\">" + TEST_PASSPHRASE + "</string>\n"
                    + "<int name=\"MaxNumberOfClients\" value=\""
                    + TEST_MAX_NUMBER_OF_CLIENTS + "\" />\n"
                    + "<boolean name=\"ClientControlByUser\" value=\""
                    + TEST_CLIENT_CONTROL_BY_USER + "\" />\n"
                    + "<boolean name=\"AutoShutdownEnabled\" value=\""
                    + TEST_AUTO_SHUTDOWN_ENABLED + "\" />\n"
                    + "<long name=\"ShutdownTimeoutMillis\" value=\""
                    + TEST_SHUTDOWN_TIMEOUT_MILLIS + "\" />\n"
                    + "<BlockedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_BLOCKED_CLIENT + "</string>\n"
                    + "</BlockedClientList>\n"
                    + "<AllowedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_ALLOWED_CLIENT + "</string>\n"
                    + "</AllowedClientList>\n";

    private static final String TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R_EXCEPT_AUTO_SHUTDOWN =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<int name=\"ApBand\" value=\"" + TEST_BAND + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL + "\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Passphrase\">" + TEST_PASSPHRASE + "</string>\n"
                    + "<int name=\"MaxNumberOfClients\" value=\""
                    + TEST_MAX_NUMBER_OF_CLIENTS + "\" />\n"
                    + "<boolean name=\"ClientControlByUser\" value=\""
                    + TEST_CLIENT_CONTROL_BY_USER + "\" />\n"
                    + "<long name=\"ShutdownTimeoutMillis\" value=\""
                    + TEST_SHUTDOWN_TIMEOUT_MILLIS + "\" />\n"
                    + "<BlockedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_BLOCKED_CLIENT + "</string>\n"
                    + "</BlockedClientList>\n"
                    + "<AllowedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_ALLOWED_CLIENT + "</string>\n"
                    + "</AllowedClientList>\n";

    private static final String TEST_CONFIG_STRING_WITH_INT_TYPE_SHUTDOWNTIMOUTMILLIS =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<int name=\"ApBand\" value=\"" + TEST_BAND + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL + "\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Passphrase\">" + TEST_PASSPHRASE + "</string>\n"
                    + "<int name=\"MaxNumberOfClients\" value=\""
                    + TEST_MAX_NUMBER_OF_CLIENTS + "\" />\n"
                    + "<boolean name=\"ClientControlByUser\" value=\""
                    + TEST_CLIENT_CONTROL_BY_USER + "\" />\n"
                    + "<boolean name=\"AutoShutdownEnabled\" value=\""
                    + TEST_AUTO_SHUTDOWN_ENABLED + "\" />\n"
                    + "<int name=\"ShutdownTimeoutMillis\" value=\""
                    + TEST_SHUTDOWN_TIMEOUT_MILLIS + "\" />\n"
                    + "<BlockedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_BLOCKED_CLIENT + "</string>\n"
                    + "</BlockedClientList>\n"
                    + "<AllowedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_ALLOWED_CLIENT + "</string>\n"
                    + "</AllowedClientList>\n";

    private static final String TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R_EXCEPT_BSSID =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<int name=\"ApBand\" value=\"" + TEST_BAND + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL + "\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Passphrase\">" + TEST_PASSPHRASE + "</string>\n"
                    + "<int name=\"MaxNumberOfClients\" value=\""
                    + TEST_MAX_NUMBER_OF_CLIENTS + "\" />\n"
                    + "<boolean name=\"ClientControlByUser\" value=\""
                    + TEST_CLIENT_CONTROL_BY_USER + "\" />\n"
                    + "<boolean name=\"AutoShutdownEnabled\" value=\""
                    + TEST_AUTO_SHUTDOWN_ENABLED + "\" />\n"
                    + "<int name=\"ShutdownTimeoutMillis\" value=\""
                    + TEST_SHUTDOWN_TIMEOUT_MILLIS + "\" />\n"
                    + "<BlockedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_BLOCKED_CLIENT + "</string>\n"
                    + "</BlockedClientList>\n"
                    + "<AllowedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_ALLOWED_CLIENT + "</string>\n"
                    + "</AllowedClientList>\n";

    private static final String TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S_EXCEPT_USER_CONFIGURATION =
            "<string name=\"WifiSsid\">"
                    + TEST_WIFI_SSID.toString().replace("\"", "&quot;") + "</string>\n"
                    + "<string name=\"Bssid\">" + TEST_BSSID + "</string>\n"
                    + "<boolean name=\"HiddenSSID\" value=\"" + TEST_HIDDEN + "\" />\n"
                    + "<int name=\"SecurityType\" value=\"" + TEST_SECURITY + "\" />\n"
                    + "<string name=\"Passphrase\">" + TEST_PASSPHRASE + "</string>\n"
                    + "<int name=\"MaxNumberOfClients\" value=\""
                    + TEST_MAX_NUMBER_OF_CLIENTS + "\" />\n"
                    + "<boolean name=\"ClientControlByUser\" value=\""
                    + TEST_CLIENT_CONTROL_BY_USER + "\" />\n"
                    + "<boolean name=\"AutoShutdownEnabled\" value=\""
                    + TEST_AUTO_SHUTDOWN_ENABLED + "\" />\n"
                    + "<long name=\"ShutdownTimeoutMillis\" value=\""
                    + TEST_SHUTDOWN_TIMEOUT_MILLIS + "\" />\n"
                    + "<BlockedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_BLOCKED_CLIENT + "</string>\n"
                    + "</BlockedClientList>\n"
                    + "<AllowedClientList>\n"
                    + "<string name=\"ClientMacAddress\">" + TEST_ALLOWED_CLIENT + "</string>\n"
                    + "</AllowedClientList>\n"
                    + "<boolean name=\"BridgedModeOpportunisticShutdownEnabled\" value=\""
                    + TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED + "\" />\n"
                    + "<int name=\"MacRandomizationSetting\" value=\""
                    + TEST_MAC_RANDOMIZATIONSETTING + "\" />\n"
                    + "<BandChannelMap>\n"
                    + "<BandChannel>\n"
                    + "<int name=\"Band\" value=\"" + TEST_BAND_2G + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL_2G + "\" />\n"
                    + "</BandChannel>\n"
                    + "<BandChannel>\n"
                    + "<int name=\"Band\" value=\"" + TEST_BAND_5G + "\" />\n"
                    + "<int name=\"Channel\" value=\"" + TEST_CHANNEL_5G + "\" />\n"
                    + "</BandChannel>\n"
                    + "</BandChannelMap>\n"
                    + "<boolean name=\"80211axEnabled\" value=\""
                    + TEST_80211AX_ENABLED + "\" />\n";

    private static final String TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S =
            TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S_EXCEPT_USER_CONFIGURATION
                    + "<boolean name=\"UserConfiguration\" value=\""
                    + TEST_USER_CONFIGURATION + "\" />\n";

    private static final String TEST_CONFIG_STRING_WITH_ALL_CONFIG_LAST_VERSION =
            TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S
                    + "<long name=\"BridgedModeOpportunisticShutdownTimeoutMillis\" value=\""
                    + TEST_BRIDTED_MODE_SHUTDOWN_TIMEOUT_MILLIS + "\" />\n"
                    + "<VendorElements>\n"
                    + "<string name=\"VendorElement\">DD04AABBCCDD</string>\n"
                    + "<string name=\"VendorElement\">DD0401020304</string>\n"
                    + "</VendorElements>\n"
                    + "<boolean name=\"80211beEnabled\" value=\""
                    + TEST_80211BE_ENABLED + "\" />\n";

    @Mock private Context mContext;
    @Mock SoftApStoreData.DataSource mDataSource;
    @Mock private WifiMigration.SettingsMigrationData mOemMigrationData;
    @Mock private SettingsMigrationDataHolder mSettingsMigrationDataHolder;
    SoftApStoreData mSoftApStoreData;
    @Mock private WifiConfigStoreEncryptionUtil mWifiConfigStoreEncryptionUtil;
    private Map<EncryptedData, byte[]> mEncryptedDataMap = new HashMap<>();
    private boolean mShouldEncrypt = false;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mSettingsMigrationDataHolder.retrieveData())
                .thenReturn(mOemMigrationData);
        when(mOemMigrationData.isSoftApTimeoutEnabled()).thenReturn(true);

        mSoftApStoreData = new SoftApStoreData(mContext, mSettingsMigrationDataHolder, mDataSource);
        TEST_BLOCKEDLIST.add(MacAddress.fromString(TEST_BLOCKED_CLIENT));
        TEST_ALLOWEDLIST.add(MacAddress.fromString(TEST_ALLOWED_CLIENT));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public EncryptedData answer(byte[] data) {
                EncryptedData encryptedData = new EncryptedData(data, data);
                mEncryptedDataMap.put(encryptedData, data);
                return encryptedData;
            }
        }).when(mWifiConfigStoreEncryptionUtil).encrypt(any());
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public byte[] answer(EncryptedData data) {
                return mEncryptedDataMap.get(data);
            }
        }).when(mWifiConfigStoreEncryptionUtil).decrypt(any());
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        TEST_BLOCKEDLIST.clear();
        TEST_ALLOWEDLIST.clear();
    }

    /**
     * Helper function for serializing configuration data to a XML block.
     *
     * @return byte[] of the XML data
     * @throws Exception
     */
    private byte[] serializeData() throws Exception {
        final XmlSerializer out = new FastXmlSerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        out.setOutput(outputStream, StandardCharsets.UTF_8.name());
        mSoftApStoreData.serializeData(out, mShouldEncrypt ? mWifiConfigStoreEncryptionUtil : null);
        out.flush();
        return outputStream.toByteArray();
    }

    /**
     * Helper function for parsing configuration data from a XML block.
     *
     * @param data XML data to parse from
     * @throws Exception
     */
    private void deserializeData(byte[] data) throws Exception {
        final XmlPullParser in = Xml.newPullParser();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        in.setInput(inputStream, StandardCharsets.UTF_8.name());
        mSoftApStoreData.deserializeData(in, in.getDepth(),
                WifiConfigStore.ENCRYPT_CREDENTIALS_CONFIG_STORE_DATA_VERSION,
                mShouldEncrypt ? mWifiConfigStoreEncryptionUtil : null);
    }

    /**
     * Verify that parsing an empty data doesn't cause any crash and no configuration should
     * be deserialized.
     *
     * @throws Exception
     */
    @Test
    public void deserializeEmptyStoreData() throws Exception {
        deserializeData(new byte[0]);
        verify(mDataSource, never()).fromDeserialized(any());
    }

    /**
     * Verify that SoftApStoreData is written to
     * {@link WifiConfigStore#STORE_FILE_SHARED_SOFTAP}.
     *
     * @throws Exception
     */
    @Test
    public void getUserStoreFileId() throws Exception {
        assertEquals(WifiConfigStore.STORE_FILE_SHARED_SOFTAP,
                mSoftApStoreData.getStoreFileId());
    }

    /**
     * Verify that the store data is serialized correctly, matches the predefined test XML data.
     *
     * @throws Exception
     */
    @Test
    public void serializeSoftAp() throws Exception {
        SoftApConfiguration.Builder softApConfigBuilder = new SoftApConfiguration.Builder();
        softApConfigBuilder.setSsid(TEST_SSID);
        softApConfigBuilder.setBssid(MacAddress.fromString(TEST_BSSID));
        softApConfigBuilder.setPassphrase(TEST_PASSPHRASE,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        if (SdkLevel.isAtLeastS()) {
            softApConfigBuilder.setChannels(TEST_CHANNELS);
        } else {
            softApConfigBuilder.setBand(TEST_BAND);
        }
        softApConfigBuilder.setClientControlByUserEnabled(TEST_CLIENT_CONTROL_BY_USER);
        softApConfigBuilder.setMaxNumberOfClients(TEST_MAX_NUMBER_OF_CLIENTS);
        softApConfigBuilder.setAutoShutdownEnabled(true);
        softApConfigBuilder.setShutdownTimeoutMillis(TEST_SHUTDOWN_TIMEOUT_MILLIS);
        softApConfigBuilder.setAllowedClientList(TEST_ALLOWEDLIST);
        softApConfigBuilder.setBlockedClientList(TEST_BLOCKEDLIST);
        if (SdkLevel.isAtLeastS()) {
            softApConfigBuilder.setMacRandomizationSetting(TEST_MAC_RANDOMIZATIONSETTING);
            softApConfigBuilder.setBridgedModeOpportunisticShutdownEnabled(
                    TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED);
            softApConfigBuilder.setIeee80211axEnabled(TEST_80211AX_ENABLED);
            softApConfigBuilder.setUserConfiguration(TEST_USER_CONFIGURATION);
        }
        if (SdkLevel.isAtLeastT()) {
            softApConfigBuilder.setBridgedModeOpportunisticShutdownTimeoutMillis(
                    TEST_BRIDTED_MODE_SHUTDOWN_TIMEOUT_MILLIS);
            softApConfigBuilder.setVendorElements(Arrays.asList(
                    InformationElementUtil.parseInformationElements(
                            TEST_TWO_VENDOR_ELEMENTS_HEX)));
            softApConfigBuilder.setIeee80211beEnabled(TEST_80211BE_ENABLED);
        }
        when(mDataSource.toSerialize()).thenReturn(softApConfigBuilder.build());
        byte[] actualData = serializeData();
        if (SdkLevel.isAtLeastT()) {
            assertEquals(TEST_CONFIG_STRING_WITH_ALL_CONFIG_LAST_VERSION, new String(actualData));
        } else if (SdkLevel.isAtLeastS()) {
            assertEquals(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S, new String(actualData));
        } else {
            assertEquals(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R, new String(actualData));
        }
    }

    /**
     * Verify that the store data is serialized correctly, matches the predefined test XML data
     * for a non-UTF-8 SSID.
     *
     * @throws Exception
     */
    @Test
    public void serializeSoftApNonUtf8() throws Exception {

        SoftApConfiguration.Builder softApConfigBuilder = new SoftApConfiguration.Builder();
        softApConfigBuilder.setSsid(TEST_SSID);
        softApConfigBuilder.setBssid(MacAddress.fromString(TEST_BSSID));
        softApConfigBuilder.setPassphrase(TEST_PASSPHRASE,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        if (SdkLevel.isAtLeastS()) {
            softApConfigBuilder.setChannels(TEST_CHANNELS);
        } else {
            softApConfigBuilder.setBand(TEST_BAND);
        }
        softApConfigBuilder.setClientControlByUserEnabled(TEST_CLIENT_CONTROL_BY_USER);
        softApConfigBuilder.setMaxNumberOfClients(TEST_MAX_NUMBER_OF_CLIENTS);
        softApConfigBuilder.setAutoShutdownEnabled(true);
        softApConfigBuilder.setShutdownTimeoutMillis(TEST_SHUTDOWN_TIMEOUT_MILLIS);
        softApConfigBuilder.setAllowedClientList(TEST_ALLOWEDLIST);
        softApConfigBuilder.setBlockedClientList(TEST_BLOCKEDLIST);
        if (SdkLevel.isAtLeastS()) {
            softApConfigBuilder.setMacRandomizationSetting(TEST_MAC_RANDOMIZATIONSETTING);
            softApConfigBuilder.setBridgedModeOpportunisticShutdownEnabled(
                    TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED);
            softApConfigBuilder.setIeee80211axEnabled(TEST_80211AX_ENABLED);
            softApConfigBuilder.setUserConfiguration(TEST_USER_CONFIGURATION);
        }
        if (SdkLevel.isAtLeastT()) {
            softApConfigBuilder.setBridgedModeOpportunisticShutdownTimeoutMillis(
                    TEST_BRIDTED_MODE_SHUTDOWN_TIMEOUT_MILLIS);
            softApConfigBuilder.setVendorElements(Arrays.asList(
                    InformationElementUtil.parseInformationElements(
                            TEST_TWO_VENDOR_ELEMENTS_HEX)));
            softApConfigBuilder.setIeee80211beEnabled(TEST_80211BE_ENABLED);
        }
        when(mDataSource.toSerialize()).thenReturn(softApConfigBuilder.build());
        byte[] actualData = serializeData();
        if (SdkLevel.isAtLeastT()) {
            assertEquals(TEST_CONFIG_STRING_WITH_ALL_CONFIG_LAST_VERSION, new String(actualData));
        } else if (SdkLevel.isAtLeastS()) {
            assertEquals(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S, new String(actualData));
        } else {
            assertEquals(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R, new String(actualData));
        }
    }


    /**
     * Verify that the store data is deserialized correctly using the predefined test XML data.
     *
     * @throws Exception
     */
    @Test
    public void deserializeSoftAp() throws Exception {
        if (SdkLevel.isAtLeastT()) {
            deserializeData(TEST_CONFIG_STRING_WITH_ALL_CONFIG_LAST_VERSION.getBytes());
        } else if (SdkLevel.isAtLeastS()) {
            deserializeData(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S.getBytes());
        } else {
            deserializeData(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R.getBytes());
        }

        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getBssid().toString(), TEST_BSSID);
        assertEquals(softApConfig.getPassphrase(), TEST_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(softApConfig.getBand(), TEST_BAND_2G);
            assertEquals(softApConfig.getChannel(), TEST_CHANNEL_2G);
        } else {
            assertEquals(softApConfig.getBand(), TEST_BAND);
            assertEquals(softApConfig.getChannel(), TEST_CHANNEL);
        }
        assertEquals(softApConfig.isClientControlByUserEnabled(), TEST_CLIENT_CONTROL_BY_USER);
        assertEquals(softApConfig.getMaxNumberOfClients(), TEST_MAX_NUMBER_OF_CLIENTS);
        assertTrue(softApConfig.isAutoShutdownEnabled());
        assertEquals(softApConfig.getShutdownTimeoutMillis(), TEST_SHUTDOWN_TIMEOUT_MILLIS);
        assertEquals(softApConfig.getBlockedClientList(), TEST_BLOCKEDLIST);
        assertEquals(softApConfig.getAllowedClientList(), TEST_ALLOWEDLIST);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(softApConfig.getChannels().toString(), TEST_CHANNELS.toString());
            assertEquals(softApConfig.getMacRandomizationSetting(), TEST_MAC_RANDOMIZATIONSETTING);
            assertEquals(softApConfig.isBridgedModeOpportunisticShutdownEnabled(),
                    TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED);
            assertEquals(softApConfig.isIeee80211axEnabled(), TEST_80211AX_ENABLED);
            assertEquals(softApConfig.isUserConfiguration(), TEST_USER_CONFIGURATION);
        }
        if (SdkLevel.isAtLeastT()) {
            assertEquals(softApConfig.isIeee80211beEnabled(), TEST_80211BE_ENABLED);
        }
    }

    /**
     * Verify that the store data is deserialized correctly using the predefined test XML data.
     *
     * @throws Exception
     */
    @Test
    public void deserializeOldSoftApXMLWhichShutdownTimeoutIsInt() throws Exception {
        deserializeData(TEST_CONFIG_STRING_WITH_INT_TYPE_SHUTDOWNTIMOUTMILLIS
                .getBytes());

        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getPassphrase(), TEST_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        assertEquals(softApConfig.getBand(), TEST_BAND);
        assertEquals(softApConfig.isClientControlByUserEnabled(), TEST_CLIENT_CONTROL_BY_USER);
        assertEquals(softApConfig.getMaxNumberOfClients(), TEST_MAX_NUMBER_OF_CLIENTS);
        assertTrue(softApConfig.isAutoShutdownEnabled());
        assertEquals(softApConfig.getShutdownTimeoutMillis(), TEST_SHUTDOWN_TIMEOUT_MILLIS);
        assertEquals(softApConfig.getBlockedClientList(), TEST_BLOCKEDLIST);
        assertEquals(softApConfig.getAllowedClientList(), TEST_ALLOWEDLIST);
    }

    /**
     * Verify that the old format for SSID is deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void deserializeOldSsidSoftAp() throws Exception {
        // Start with the old serialized data
        deserializeData(TEST_CONFIG_STRING_WITH_OLD_SSID_DESIGN.getBytes());

        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getPassphrase(), TEST_WPA2_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        assertEquals(softApConfig.getBand(), TEST_BAND);
    }

    /**
     * Verify that the old format for band is deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void deserializeOldBandSoftAp() throws Exception {
        // Start with the old serialized data
        deserializeData(TEST_CONFIG_STRING_FROM_WIFICONFIGURATION.getBytes());

        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getPassphrase(), TEST_WPA2_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        assertEquals(softApConfig.getBand(), TEST_BAND);
    }

    /**
     * Verify that the old format is deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void deserializeNewBandSoftApButNoNewConfig() throws Exception {
        // Start with the old serialized data
        deserializeData(TEST_CONFIG_STRING_WITH_NEW_BAND_DESIGN_IN_R.getBytes());

        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getPassphrase(), TEST_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        assertEquals(softApConfig.getBand(), TEST_BAND);
    }

    /**
     * Verify that the store data is serialized/deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void serializeDeserializeSoftAp() throws Exception {
        SoftApConfiguration.Builder softApConfigBuilder = new SoftApConfiguration.Builder();
        softApConfigBuilder.setSsid(TEST_SSID);
        softApConfigBuilder.setPassphrase(TEST_PASSPHRASE,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        softApConfigBuilder.setBand(TEST_BAND);
        SoftApConfiguration softApConfig = softApConfigBuilder.build();

        // Serialize first.
        when(mDataSource.toSerialize()).thenReturn(softApConfigBuilder.build());
        byte[] serializedData = serializeData();

        // Now deserialize first.
        deserializeData(serializedData);
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfigDeserialized = softapConfigCaptor.getValue();
        assertNotNull(softApConfigDeserialized);

        assertEquals(softApConfig.getWifiSsid(), softApConfigDeserialized.getWifiSsid());
        assertEquals(softApConfig.getPassphrase(),
                softApConfigDeserialized.getPassphrase());
        assertEquals(softApConfig.getSecurityType(),
                softApConfigDeserialized.getSecurityType());
        assertEquals(softApConfig.isHiddenSsid(), softApConfigDeserialized.isHiddenSsid());
        assertEquals(softApConfig.getBand(), softApConfigDeserialized.getBand());
        assertEquals(softApConfig.getChannel(), softApConfigDeserialized.getChannel());
    }

    /**
     * Verify that the stored wpa3-sae data is serialized/deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void serializeDeserializeSoftApWpa3Sae() throws Exception {
        SoftApConfiguration.Builder softApConfigBuilder = new SoftApConfiguration.Builder();
        softApConfigBuilder.setSsid(TEST_SSID);
        softApConfigBuilder.setPassphrase(TEST_PASSPHRASE,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        softApConfigBuilder.setBand(TEST_BAND);

        SoftApConfiguration softApConfig = softApConfigBuilder.build();

        // Serialize first.
        when(mDataSource.toSerialize()).thenReturn(softApConfigBuilder.build());
        byte[] serializedData = serializeData();

        // Now deserialize first.
        deserializeData(serializedData);
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfigDeserialized = softapConfigCaptor.getValue();
        assertNotNull(softApConfigDeserialized);

        assertEquals(softApConfig.getWifiSsid(), softApConfigDeserialized.getWifiSsid());
        assertEquals(softApConfig.getPassphrase(),
                softApConfigDeserialized.getPassphrase());
        assertEquals(softApConfig.getSecurityType(),
                softApConfigDeserialized.getSecurityType());
        assertEquals(softApConfig.isHiddenSsid(), softApConfigDeserialized.isHiddenSsid());
        assertEquals(softApConfig.getBand(), softApConfigDeserialized.getBand());
        assertEquals(softApConfig.getChannel(), softApConfigDeserialized.getChannel());
    }

    /**
     * Verify that the stored wpa3-sae-transition data is serialized/deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void serializeDeserializeSoftApWpa3SaeTransition() throws Exception {
        SoftApConfiguration.Builder softApConfigBuilder = new SoftApConfiguration.Builder();
        softApConfigBuilder.setSsid(TEST_SSID);
        softApConfigBuilder.setPassphrase(TEST_PASSPHRASE,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
        softApConfigBuilder.setBand(TEST_BAND);

        SoftApConfiguration softApConfig = softApConfigBuilder.build();

        // Serialize first.
        when(mDataSource.toSerialize()).thenReturn(softApConfigBuilder.build());
        byte[] serializedData = serializeData();

        // Now deserialize first.
        deserializeData(serializedData);
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfigDeserialized = softapConfigCaptor.getValue();
        assertNotNull(softApConfigDeserialized);

        assertEquals(softApConfig.getWifiSsid(), softApConfigDeserialized.getWifiSsid());
        assertEquals(softApConfig.getPassphrase(),
                softApConfigDeserialized.getPassphrase());
        assertEquals(softApConfig.getSecurityType(),
                softApConfigDeserialized.getSecurityType());
        assertEquals(softApConfig.isHiddenSsid(), softApConfigDeserialized.isHiddenSsid());
        assertEquals(softApConfig.getBand(), softApConfigDeserialized.getBand());
        assertEquals(softApConfig.getChannel(), softApConfigDeserialized.getChannel());
    }

    /**
     * Verify that the store data is deserialized correctly using the predefined test XML data
     * when the auto shutdown tag is retrieved from
     * {@link WifiMigration.loadFromSettings(Context)}.
     *
     * @throws Exception
     */
    @Test
    public void deserializeSoftApWithNoAutoShutdownTag() throws Exception {

        // Toggle on when migrating.
        when(mOemMigrationData.isSoftApTimeoutEnabled()).thenReturn(true);
        deserializeData(
                TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R_EXCEPT_AUTO_SHUTDOWN.getBytes());
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertTrue(softApConfig.isAutoShutdownEnabled());

        // Toggle off when migrating.
        when(mOemMigrationData.isSoftApTimeoutEnabled()).thenReturn(false);
        deserializeData(
                TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R_EXCEPT_AUTO_SHUTDOWN.getBytes());
        verify(mDataSource, times(2)).fromDeserialized(softapConfigCaptor.capture());
        softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertFalse(softApConfig.isAutoShutdownEnabled());
    }

    /**
     * Verify that the old format is deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void deserializeSoftApWithNoBssidTag() throws Exception {
        // Start with the old serialized data
        deserializeData(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R_EXCEPT_BSSID
                .getBytes());
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getPassphrase(), TEST_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        assertEquals(softApConfig.getBand(), TEST_BAND);
        assertEquals(softApConfig.isClientControlByUserEnabled(), TEST_CLIENT_CONTROL_BY_USER);
        assertEquals(softApConfig.getMaxNumberOfClients(), TEST_MAX_NUMBER_OF_CLIENTS);
        assertTrue(softApConfig.isAutoShutdownEnabled());
        assertEquals(softApConfig.getShutdownTimeoutMillis(), TEST_SHUTDOWN_TIMEOUT_MILLIS);
        assertEquals(softApConfig.getBlockedClientList(), TEST_BLOCKEDLIST);
        assertEquals(softApConfig.getAllowedClientList(), TEST_ALLOWEDLIST);
    }

    /**
     * Verify that the old format is deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void deserializeSoftApWithAllConfigInR() throws Exception {
        // Start with the old serialized data
        deserializeData(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_R
                .getBytes());
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getBssid().toString(), TEST_BSSID);
        assertEquals(softApConfig.getPassphrase(), TEST_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        assertEquals(softApConfig.getBand(), TEST_BAND);
        assertEquals(softApConfig.isClientControlByUserEnabled(), TEST_CLIENT_CONTROL_BY_USER);
        assertEquals(softApConfig.getMaxNumberOfClients(), TEST_MAX_NUMBER_OF_CLIENTS);
        assertTrue(softApConfig.isAutoShutdownEnabled());
        assertEquals(softApConfig.getShutdownTimeoutMillis(), TEST_SHUTDOWN_TIMEOUT_MILLIS);
        assertEquals(softApConfig.getBlockedClientList(), TEST_BLOCKEDLIST);
        assertEquals(softApConfig.getAllowedClientList(), TEST_ALLOWEDLIST);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(softApConfig.getChannels().toString(),
                    TEST_CHANNELS_IN_R_CONFIG.toString());
        }
    }

    /**
     * Verify that the old format is deserialized correctly.
     *
     * @throws Exception
     */
    @Test
    public void deserializeSoftApWithAllConfigInSExceptUserConfiguration() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // Start with the old serialized data
        deserializeData(TEST_CONFIG_STRING_WITH_ALL_CONFIG_IN_S_EXCEPT_USER_CONFIGURATION
                .getBytes());
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfig = softapConfigCaptor.getValue();
        assertNotNull(softApConfig);
        assertEquals(softApConfig.getWifiSsid(), TEST_WIFI_SSID);
        assertEquals(softApConfig.getBssid().toString(), TEST_BSSID);
        assertEquals(softApConfig.getPassphrase(), TEST_PASSPHRASE);
        assertEquals(softApConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertEquals(softApConfig.isHiddenSsid(), TEST_HIDDEN);
        assertEquals(softApConfig.getBand(), TEST_BAND_2G);
        assertEquals(softApConfig.getChannel(), TEST_CHANNEL_2G);
        assertEquals(softApConfig.getChannels().toString(), TEST_CHANNELS.toString());
        assertEquals(softApConfig.isClientControlByUserEnabled(), TEST_CLIENT_CONTROL_BY_USER);
        assertEquals(softApConfig.getMaxNumberOfClients(), TEST_MAX_NUMBER_OF_CLIENTS);
        assertTrue(softApConfig.isAutoShutdownEnabled());
        assertEquals(softApConfig.getShutdownTimeoutMillis(), TEST_SHUTDOWN_TIMEOUT_MILLIS);
        assertEquals(softApConfig.getBlockedClientList(), TEST_BLOCKEDLIST);
        assertEquals(softApConfig.getAllowedClientList(), TEST_ALLOWEDLIST);
        assertEquals(softApConfig.getMacRandomizationSetting(), TEST_MAC_RANDOMIZATIONSETTING);
        assertEquals(softApConfig.isBridgedModeOpportunisticShutdownEnabled(),
                TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED);
        assertEquals(softApConfig.isIeee80211axEnabled(), TEST_80211AX_ENABLED);
        assertEquals(softApConfig.isUserConfiguration(), true);
    }

    /**
     * Verify that the store data is serialized/deserialized correctly.
     *
     * @throws Exception when test fails it throws exception
     */
    @Test
    public void serializeDeserializeSoftApEncrypted() throws Exception {
        SoftApConfiguration softApConfig = new SoftApConfiguration.Builder().setSsid(TEST_SSID)
                .setPassphrase(TEST_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(TEST_BAND).build();
        when(mDataSource.toSerialize()).thenReturn(softApConfig);
        mShouldEncrypt = true;
        byte[] serializedData = serializeData();
        deserializeData(serializedData);
        ArgumentCaptor<SoftApConfiguration> softapConfigCaptor =
                ArgumentCaptor.forClass(SoftApConfiguration.class);
        verify(mDataSource).fromDeserialized(softapConfigCaptor.capture());
        SoftApConfiguration softApConfigDeserialized = softapConfigCaptor.getValue();
        assertNotNull(softApConfigDeserialized);
        assertEquals(softApConfig.getPassphrase(),
                softApConfigDeserialized.getPassphrase());
        assertEquals(softApConfig.getSecurityType(),
                softApConfigDeserialized.getSecurityType());
    }
}
