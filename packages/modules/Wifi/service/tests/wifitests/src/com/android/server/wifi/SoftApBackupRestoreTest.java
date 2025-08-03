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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiMigration;
import android.net.wifi.WifiSsid;
import android.util.BackupUtils;
import android.util.SparseIntArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.ApConfigUtil;
import com.android.server.wifi.util.SettingsMigrationDataHolder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.SoftApBackupRestore}.
 */
@SmallTest
public class SoftApBackupRestoreTest extends WifiBaseTest {

    @Mock private Context mContext;
    @Mock private SettingsMigrationDataHolder mSettingsMigrationDataHolder;
    @Mock private WifiMigration.SettingsMigrationData mOemMigrationData;
    private SoftApBackupRestore mSoftApBackupRestore;
    private final ArrayList<MacAddress> mTestBlockedList = new ArrayList<>();
    private final ArrayList<MacAddress> mTestAllowedList = new ArrayList<>();
    private static final int LAST_WIFICOFIGURATION_BACKUP_VERSION = 3;
    private static final boolean TEST_CLIENTCONTROLENABLE = false;
    private static final int TEST_MAXNUMBEROFCLIENTS = 10;
    private static final long TEST_SHUTDOWNTIMEOUTMILLIS = 600_000;
    private static final String TEST_BLOCKED_CLIENT = "11:22:33:44:55:66";
    private static final String TEST_ALLOWED_CLIENT = "aa:bb:cc:dd:ee:ff";
    private static final String TEST_SSID = "TestAP";
    private static final String TEST_PASSPHRASE = "TestPskPassphrase";
    private static final int TEST_SECURITY = SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION;
    private static final int TEST_BAND = SoftApConfiguration.BAND_5GHZ;
    private static final int TEST_CHANNEL = 40;
    private static final boolean TEST_HIDDEN = false;
    private static final int TEST_CHANNEL_2G = 1;
    private static final int TEST_CHANNEL_5G = 149;
    private static final int TEST_BAND_2G = SoftApConfiguration.BAND_2GHZ;
    private static final int TEST_BAND_5G = SoftApConfiguration.BAND_5GHZ;
    private static final boolean TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED = false;
    private static final boolean TEST_AUTO_SHUTDOWN_ENABLED = false;
    private static final int TEST_MAC_RANDOMIZATIONSETTING =
            SoftApConfiguration.RANDOMIZATION_NONE;
    private static final SparseIntArray TEST_CHANNELS = new SparseIntArray() {{
            put(TEST_BAND_2G, TEST_CHANNEL_2G);
            put(TEST_BAND_5G, TEST_CHANNEL_5G);
            }};
    private static final boolean TEST_80211AX_ENABLED = false;

    /**
     * Asserts that the WifiConfigurations equal to SoftApConfiguration.
     * This only compares the elements saved
     * for softAp used.
     */
    public static void assertWifiConfigurationEqualSoftApConfiguration(
            WifiConfiguration backup, SoftApConfiguration restore) {
        assertEquals(backup.SSID, restore.getWifiSsid().getUtf8Text());
        assertEquals(backup.BSSID, restore.getBssid());
        assertEquals(ApConfigUtil.convertWifiConfigBandToSoftApConfigBand(backup.apBand),
                restore.getBand());
        assertEquals(backup.apChannel, restore.getChannel());
        assertEquals(backup.preSharedKey, restore.getPassphrase());
        int authType = backup.getAuthType();
        if (backup.getAuthType() == WifiConfiguration.KeyMgmt.WPA2_PSK) {
            assertEquals(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK, restore.getSecurityType());
        } else {
            assertEquals(SoftApConfiguration.SECURITY_TYPE_OPEN, restore.getSecurityType());
        }
        assertEquals(backup.hiddenSSID, restore.isHiddenSsid());
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mSettingsMigrationDataHolder.retrieveData()).thenReturn(mOemMigrationData);
        when(mOemMigrationData.isSoftApTimeoutEnabled()).thenReturn(true);

        mSoftApBackupRestore = new SoftApBackupRestore(mContext, mSettingsMigrationDataHolder);
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Copy from WifiConfiguration for test backup/restore is backward compatible.
     */
    private byte[] getBytesForBackup(WifiConfiguration wificonfig) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(LAST_WIFICOFIGURATION_BACKUP_VERSION);
        BackupUtils.writeString(out, wificonfig.SSID);
        out.writeInt(wificonfig.apBand);
        out.writeInt(wificonfig.apChannel);
        BackupUtils.writeString(out, wificonfig.preSharedKey);
        out.writeInt(wificonfig.getAuthType());
        out.writeBoolean(wificonfig.hiddenSSID);
        return baos.toByteArray();
    }

    /**
     * Verifies that the serialization/de-serialization for wpa2 softap config works.
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWithWpa2Config() throws Exception {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid("TestAP");
        configBuilder.setChannel(40, SoftApConfiguration.BAND_5GHZ);
        configBuilder.setPassphrase("TestPskPassphrase",
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configBuilder.setHiddenSsid(true);

        SoftApConfiguration config = configBuilder.build();

        byte[] data = mSoftApBackupRestore.retrieveBackupDataFromSoftApConfiguration(config);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertThat(config).isEqualTo(restoredConfig);
    }

    /**
     * Verifies that the serialization/de-serialization for open security softap config works.
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWithOpenSecurityConfig() throws Exception {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid("TestAP");
        configBuilder.setChannel(12, SoftApConfiguration.BAND_2GHZ);
        configBuilder.setHiddenSsid(false);
        SoftApConfiguration config = configBuilder.build();

        byte[] data = mSoftApBackupRestore.retrieveBackupDataFromSoftApConfiguration(config);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertThat(config).isEqualTo(restoredConfig);
    }

    /**
     * Verifies that the serialization/de-serialization for 6G OWE config
     * works.
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWith6GOWEConfig()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid("TestAP");
        configBuilder.setBand(SoftApConfiguration.BAND_6GHZ);
        configBuilder.setPassphrase(null, SoftApConfiguration.SECURITY_TYPE_WPA3_OWE);
        SoftApConfiguration config = configBuilder.build();

        byte[] data = mSoftApBackupRestore.retrieveBackupDataFromSoftApConfiguration(config);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertThat(config).isEqualTo(restoredConfig);
    }

    /**
     * Verifies that the serialization/de-serialization for old softap config works.
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWithOldConfig() throws Exception {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "TestAP";
        wifiConfig.apBand = WifiConfiguration.AP_BAND_2GHZ;
        wifiConfig.apChannel = 12;
        wifiConfig.hiddenSSID = true;
        wifiConfig.preSharedKey = "test_pwd";
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        byte[] data = getBytesForBackup(wifiConfig);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertWifiConfigurationEqualSoftApConfiguration(wifiConfig, restoredConfig);
    }

    /**
     * Verifies that the serialization/de-serialization for wpa3-sae softap config works.
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWithWpa3SaeConfig() throws Exception {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid("TestAP");
        configBuilder.setBand(SoftApConfiguration.BAND_5GHZ);
        configBuilder.setChannel(40, SoftApConfiguration.BAND_5GHZ);
        configBuilder.setPassphrase("TestPskPassphrase",
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        configBuilder.setHiddenSsid(true);
        configBuilder.setAutoShutdownEnabled(true);
        SoftApConfiguration config = configBuilder.build();

        byte[] data = mSoftApBackupRestore.retrieveBackupDataFromSoftApConfiguration(config);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertThat(config).isEqualTo(restoredConfig);
    }

    /**
     * Verifies that the serialization/de-serialization for wpa3-sae-transition softap config.
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWithWpa3SaeTransitionConfig() throws Exception {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid("TestAP");
        configBuilder.setBand(SoftApConfiguration.BAND_5GHZ);
        configBuilder.setChannel(40, SoftApConfiguration.BAND_5GHZ);
        configBuilder.setPassphrase("TestPskPassphrase",
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
        configBuilder.setHiddenSsid(true);
        configBuilder.setAutoShutdownEnabled(false);
        SoftApConfiguration config = configBuilder.build();

        byte[] data = mSoftApBackupRestore.retrieveBackupDataFromSoftApConfiguration(config);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertThat(config).isEqualTo(restoredConfig);
    }

    /**
     * Verifies that the serialization/de-serialization for wpa3-sae-transition softap config.
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWithMaxShutdownClientList() throws Exception {
        mTestBlockedList.add(MacAddress.fromString(TEST_BLOCKED_CLIENT));
        mTestAllowedList.add(MacAddress.fromString(TEST_ALLOWED_CLIENT));
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid(TEST_SSID);
        configBuilder.setBand(SoftApConfiguration.BAND_5GHZ);
        configBuilder.setChannel(40, SoftApConfiguration.BAND_5GHZ);
        configBuilder.setPassphrase(TEST_PASSPHRASE, TEST_SECURITY);
        configBuilder.setHiddenSsid(TEST_HIDDEN);
        configBuilder.setMaxNumberOfClients(TEST_MAXNUMBEROFCLIENTS);
        configBuilder.setShutdownTimeoutMillis(TEST_SHUTDOWNTIMEOUTMILLIS);
        configBuilder.setClientControlByUserEnabled(TEST_CLIENTCONTROLENABLE);
        configBuilder.setBlockedClientList(mTestBlockedList);
        configBuilder.setAllowedClientList(mTestAllowedList);
        SoftApConfiguration config = configBuilder.build();

        byte[] data = mSoftApBackupRestore.retrieveBackupDataFromSoftApConfiguration(config);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertThat(config).isEqualTo(restoredConfig);
    }

    /**
     * Verifies that the serialization/de-serialization for all customized configure field in .
     */
    @Test
    public void testSoftApConfigBackupAndRestoreWithAllConfigInT() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        SoftApConfiguration config = generateExpectedSoftApConfigurationWithTestData(9);

        byte[] data = mSoftApBackupRestore.retrieveBackupDataFromSoftApConfiguration(config);
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(data);

        assertThat(config).isEqualTo(restoredConfig);
    }

    /**
     * Verifies that the restore of version 5 backup data will read the auto shutdown enable/disable
     * tag from {@link WifiMigration#loadFromSettings(Context)}
     */
    @Test
    public void testSoftApConfigRestoreFromVersion5() throws Exception {
        SoftApConfiguration.Builder configBuilder =
                new SoftApConfiguration.Builder(generateExpectedSoftApConfigurationWithTestData(5));

        // Toggle on when migrating.
        when(mOemMigrationData.isSoftApTimeoutEnabled()).thenReturn(true);
        SoftApConfiguration expectedConfig = configBuilder.setAutoShutdownEnabled(true).build();
        SoftApConfiguration restoredConfig =
                mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(
                retrieveSpecificVersionBackupDataFromSoftApConfiguration(expectedConfig, 5));
        assertEquals(expectedConfig, restoredConfig);

        // Toggle off when migrating.
        when(mOemMigrationData.isSoftApTimeoutEnabled()).thenReturn(false);
        expectedConfig = configBuilder.setAutoShutdownEnabled(false).build();
        restoredConfig = mSoftApBackupRestore.retrieveSoftApConfigurationFromBackupData(
                retrieveSpecificVersionBackupDataFromSoftApConfiguration(expectedConfig, 5));
        assertEquals(expectedConfig, restoredConfig);
    }

    /**
     * Verifies that the restore of version 6 backup data will read the auto shutdown with int.
     */
    @Test
    public void testSoftApConfigRestoreFromVersion6() throws Exception {
        SoftApConfiguration expectedConfig = generateExpectedSoftApConfigurationWithTestData(6);
        SoftApConfiguration restoredConfig = mSoftApBackupRestore
                .retrieveSoftApConfigurationFromBackupData(
                retrieveSpecificVersionBackupDataFromSoftApConfiguration(expectedConfig, 6));
        assertEquals(expectedConfig, restoredConfig);
    }

    /**
     * Verifies that the restore of version 7
     */
    @Test
    public void testSoftApConfigRestoreFromVersion7() throws Exception {
        SoftApConfiguration expectedConfig = generateExpectedSoftApConfigurationWithTestData(7);
        SoftApConfiguration restoredConfig = mSoftApBackupRestore
                .retrieveSoftApConfigurationFromBackupData(
                retrieveSpecificVersionBackupDataFromSoftApConfiguration(expectedConfig, 7));
        assertEquals(expectedConfig, restoredConfig);
    }

    /**
     * Verifies that the restore of version 8 (Android S)
     */
    @Test
    public void testSoftApConfigRestoreFromVersion8() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        SoftApConfiguration expectedConfig = generateExpectedSoftApConfigurationWithTestData(8);
        SoftApConfiguration restoredConfig = mSoftApBackupRestore
                .retrieveSoftApConfigurationFromBackupData(
                retrieveSpecificVersionBackupDataFromSoftApConfiguration(expectedConfig, 8));
        assertEquals(expectedConfig, restoredConfig);
    }

    // Test util methods
    private SoftApConfiguration generateExpectedSoftApConfigurationWithTestData(int version) {
        mTestBlockedList.add(MacAddress.fromString(TEST_BLOCKED_CLIENT));
        mTestAllowedList.add(MacAddress.fromString(TEST_ALLOWED_CLIENT));
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid(TEST_SSID);
        configBuilder.setBand(TEST_BAND);
        configBuilder.setChannel(TEST_CHANNEL, TEST_BAND);
        configBuilder.setPassphrase(TEST_PASSPHRASE, TEST_SECURITY);
        configBuilder.setHiddenSsid(TEST_HIDDEN);
        configBuilder.setMaxNumberOfClients(TEST_MAXNUMBEROFCLIENTS);
        configBuilder.setShutdownTimeoutMillis(TEST_SHUTDOWNTIMEOUTMILLIS);
        configBuilder.setClientControlByUserEnabled(TEST_CLIENTCONTROLENABLE);
        configBuilder.setBlockedClientList(mTestBlockedList);
        configBuilder.setAllowedClientList(mTestAllowedList);
        if (version > 5) {
            configBuilder.setAutoShutdownEnabled(TEST_AUTO_SHUTDOWN_ENABLED);
        }
        if (version > 7) { // Android S
            configBuilder.setChannels(TEST_CHANNELS);
            configBuilder.setMacRandomizationSetting(TEST_MAC_RANDOMIZATIONSETTING);
            configBuilder.setBridgedModeOpportunisticShutdownEnabled(
                    TEST_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLED);
            configBuilder.setIeee80211axEnabled(TEST_80211AX_ENABLED);
        }
        return configBuilder.build();
    }


    /**
     * Util method to write SoftApConfiguration to OutputStream for V5 to V8.
     *
     * Some release notes for each android.
     *
     * Android R: Version#4 ~ Version#7 (Start to use SoftApConfiguration)
     * Android S: Version#8
     * Android T: Version#9, start to support XML backup, no need to call this function.
     */
    private byte[] retrieveSpecificVersionBackupDataFromSoftApConfiguration(
            SoftApConfiguration config, int version) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(version);
        WifiSsid wifiSsid = config.getWifiSsid();
        if (wifiSsid != null) {
            CharSequence utf8Ssid = wifiSsid.getUtf8Text();
            BackupUtils.writeString(out, utf8Ssid != null
                    ? utf8Ssid.toString() : WifiManager.UNKNOWN_SSID);
        } else {
            BackupUtils.writeString(out, null);
        }
        out.writeInt(config.getBand());
        out.writeInt(config.getChannel());
        BackupUtils.writeString(out, config.getPassphrase());
        out.writeInt(config.getSecurityType());
        out.writeBoolean(config.isHiddenSsid());
        out.writeInt(config.getMaxNumberOfClients());
        // Start from version#7, ShutdownTimeoutMillis changed from int to Long.
        if (version > 6) {
            out.writeLong(config.getShutdownTimeoutMillis());
        } else {
            out.writeInt((int) config.getShutdownTimeoutMillis());
        }
        out.writeBoolean(config.isClientControlByUserEnabled());
        writeMacAddressList(out, config.getBlockedClientList());
        writeMacAddressList(out, config.getAllowedClientList());
        // Start from version#6, AutoShutdownEnabled stored in configuration
        if (version > 5) {
            out.writeBoolean(config.isAutoShutdownEnabled());
        }
        if (version > 7) { // Version#8 is backup version in android S.
            out.writeBoolean(config.isBridgedModeOpportunisticShutdownEnabled());
            out.writeInt(config.getMacRandomizationSetting());
            SparseIntArray channels = config.getChannels();
            int numOfChannels = channels.size();
            out.writeInt(numOfChannels);
            for (int i = 0; i < numOfChannels; i++) {
                out.writeInt(channels.keyAt(i));
                out.writeInt(channels.valueAt(i));
            }
            out.writeBoolean(config.isIeee80211axEnabled());
        }

        return baos.toByteArray();
    }

    private void writeMacAddressList(DataOutputStream out, List<MacAddress> macList)
            throws IOException {
        out.writeInt(macList.size());
        Iterator<MacAddress> iterator = macList.iterator();
        while (iterator.hasNext()) {
            byte[] mac = iterator.next().toByteArray();
            out.write(mac, 0,  6);
        }
    }
}
