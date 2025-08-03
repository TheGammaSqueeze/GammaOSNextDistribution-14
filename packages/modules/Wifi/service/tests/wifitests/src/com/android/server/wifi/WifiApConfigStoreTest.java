/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.net.wifi.SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT;
import static android.net.wifi.SoftApConfiguration.RANDOMIZATION_PERSISTENT;
import static android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
import static android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA3_SAE;
import static android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION;

import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApConfiguration.Builder;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Handler;
import android.os.test.TestLooper;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Unit tests for {@link com.android.server.wifi.WifiApConfigStore}.
 */
@SmallTest
public class WifiApConfigStoreTest extends WifiBaseTest {

    private static final String TAG = "WifiApConfigStoreTest";

    private static final String TEST_DEFAULT_2G_CHANNEL_LIST = "1,2,3,4,5,6";
    private static final String TEST_DEFAULT_AP_SSID = "TestAP";
    private static final String TEST_DEFAULT_HOTSPOT_SSID = "TestShare";
    private static final int RAND_SSID_INT_MIN = 1000;
    private static final int RAND_SSID_INT_MAX = 9999;
    private static final String TEST_CHAR_SET_AS_STRING = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String TEST_STRING_UTF8_WITH_30_BYTES = "智者務其實愚者爭虛名";
    private static final String TEST_STRING_UTF8_WITH_32_BYTES = "ΣωκράτηςΣωκράτης";
    private static final String TEST_STRING_UTF8_WITH_33_BYTES = "一片汪洋大海中的一條魚";
    private static final String TEST_STRING_UTF8_WITH_34_BYTES = "Ευπροσηγοροςγινου";
    private static final MacAddress TEST_RANDOMIZED_MAC =
            MacAddress.fromString("d2:11:19:34:a5:20");
    private static final MacAddress TEST_SAP_BSSID_MAC =
            MacAddress.fromString("aa:bb:cc:11:22:33");

    private final int mBand25G = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ;
    private final int mBand256G = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
            | SoftApConfiguration.BAND_6GHZ;
    private final int mBand25660G = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
            | SoftApConfiguration.BAND_6GHZ | SoftApConfiguration.BAND_60GHZ;

    @Mock private Context mContext;
    @Mock private WifiInjector mWifiInjector;
    @Mock private WifiNative mWifiNative;
    @Mock private WifiMetrics mWifiMetrics;
    private TestLooper mLooper;
    private Handler mHandler;
    @Mock private BackupManagerProxy mBackupManagerProxy;
    @Mock private WifiConfigStore mWifiConfigStore;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private ActiveModeWarden mActiveModeWarden;
    @Mock private PackageManager mPackageManager;
    @Mock private SoftApCapability mSoftApCapability;

    private Random mRandom;
    private MockResources mResources;
    @Mock private ApplicationInfo mMockApplInfo;
    @Mock private MacAddressUtil mMacAddressUtil;
    private SoftApStoreData.DataSource mDataStoreSource;
    private ArrayList<Integer> mKnownGood2GChannelList;

    @Before
    public void setUp() throws Exception {
        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        MockitoAnnotations.initMocks(this);
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.P;
        when(mContext.getApplicationInfo()).thenReturn(mMockApplInfo);

        /* Setup expectations for Resources to return some default settings. */
        mResources = new MockResources();
        mResources.setString(R.string.config_wifiSoftap2gChannelList,
                             TEST_DEFAULT_2G_CHANNEL_LIST);
        mResources.setString(R.string.wifi_tether_configure_ssid_default,
                             TEST_DEFAULT_AP_SSID);
        mResources.setString(R.string.wifi_localhotspot_configure_ssid_default,
                             TEST_DEFAULT_HOTSPOT_SSID);
        mResources.setBoolean(R.bool.config_wifiSoftapPassphraseAsciiEncodableCheck, true);
        setupAllBandsSupported();

        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        // build the known good 2G channel list: TEST_DEFAULT_2G_CHANNEL_LIST
        mKnownGood2GChannelList = new ArrayList(Arrays.asList(1, 2, 3, 4, 5, 6));

        mRandom = new Random();
        when(mWifiInjector.getMacAddressUtil()).thenReturn(mMacAddressUtil);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mMacAddressUtil.calculatePersistentMacForSap(any(), anyInt()))
                .thenReturn(TEST_RANDOMIZED_MAC);
        when(mWifiNative.canDeviceSupportCreateTypeCombo(any()))
                .thenAnswer(answer -> {
                    SparseArray<Integer> combo = answer.getArgument(0);
                    if (combo.contentEquals(new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_AP_BRIDGE, 1);
                        }})) {
                        return true;
                    }
                    return false;
                });

        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        mResources.setBoolean(
                R.bool.config_wifiSoftapAutoAppendLowerBandsToBandConfigurationEnabled, true);
    }

    private void setupAllBandsSupported() {
        mResources.setBoolean(R.bool.config_wifi24ghzSupport, true);
        mResources.setBoolean(R.bool.config_wifiSoftap24ghzSupported, true);
        mResources.setBoolean(R.bool.config_wifi5ghzSupport, true);
        mResources.setBoolean(R.bool.config_wifiSoftap5ghzSupported, true);
        mResources.setBoolean(R.bool.config_wifi6ghzSupport, true);
        mResources.setBoolean(R.bool.config_wifiSoftap6ghzSupported, true);
        mResources.setBoolean(R.bool.config_wifi60ghzSupport, true);
        mResources.setBoolean(R.bool.config_wifiSoftap60ghzSupported, true);
    }

    /**
     * Helper method to create and verify actions for the ApConfigStore used in the following tests.
     */
    private WifiApConfigStore createWifiApConfigStore() throws Exception {
        WifiApConfigStore store = new WifiApConfigStore(
                mContext, mWifiInjector, mHandler, mBackupManagerProxy,
                mWifiConfigStore, mWifiConfigManager, mActiveModeWarden, mWifiMetrics);
        verify(mWifiConfigStore).registerStoreData(any());
        ArgumentCaptor<SoftApStoreData.DataSource> dataStoreSourceArgumentCaptor =
                ArgumentCaptor.forClass(SoftApStoreData.DataSource.class);
        verify(mWifiInjector).makeSoftApStoreData(dataStoreSourceArgumentCaptor.capture());
        mDataStoreSource = dataStoreSourceArgumentCaptor.getValue();

        return store;
    }

    /**
     * Generate a SoftApConfiguration based on the specified parameters.
     */
    private SoftApConfiguration setupApConfig(
            String ssid, String preSharedKey, int keyManagement, int band, int channel,
            boolean hiddenSSID) {
        Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid(ssid);
        configBuilder.setPassphrase(preSharedKey, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        if (channel == 0) {
            configBuilder.setBand(band);
        } else {
            configBuilder.setChannel(channel, band);
        }
        configBuilder.setHiddenSsid(hiddenSSID);
        return configBuilder.build();
    }

    private void verifyApConfig(SoftApConfiguration config1, SoftApConfiguration config2) {
        assertEquals(config1.getWifiSsid(), config2.getWifiSsid());
        assertEquals(config1.getPassphrase(), config2.getPassphrase());
        assertEquals(config1.getSecurityType(), config2.getSecurityType());
        assertEquals(config1.getBand(), config2.getBand());
        assertEquals(config1.getChannel(), config2.getChannel());
        assertEquals(config1.isHiddenSsid(), config2.isHiddenSsid());
    }

    private void verifyDefaultApConfig(SoftApConfiguration config, String expectedSsid,
            boolean isSaeSupport) {
        verifyDefaultApConfig(config, expectedSsid, isSaeSupport, true, false);
    }


    private void verifyDefaultApConfig(SoftApConfiguration config, String expectedSsid,
            boolean isSaeSupport, boolean isMacRandomizationSupport, boolean isBridgedApSupport) {
        String[] splitSsid = config.getWifiSsid().getUtf8Text().toString().split("_");
        assertEquals(2, splitSsid.length);
        assertEquals(expectedSsid, splitSsid[0]);
        assertEquals(SoftApConfiguration.BAND_2GHZ, config.getBand());
        assertFalse(config.isHiddenSsid());
        int randomPortion = Integer.parseInt(splitSsid[1]);
        assertTrue(randomPortion >= RAND_SSID_INT_MIN && randomPortion <= RAND_SSID_INT_MAX);
        if (isSaeSupport) {
            assertEquals(SECURITY_TYPE_WPA3_SAE_TRANSITION, config.getSecurityType());
        } else {
            assertEquals(SECURITY_TYPE_WPA2_PSK, config.getSecurityType());
        }
        assertEquals(15, config.getPassphrase().length());
        if (isMacRandomizationSupport) {
            if (SdkLevel.isAtLeastT()) {
                assertEquals(config.getMacRandomizationSettingInternal(),
                        SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT);
            } else {
                assertEquals(config.getMacRandomizationSettingInternal(),
                        SoftApConfiguration.RANDOMIZATION_PERSISTENT);
            }
        } else {
            assertEquals(config.getMacRandomizationSettingInternal(),
                    SoftApConfiguration.RANDOMIZATION_NONE);
        }
        if (isBridgedApSupport) {
            int[] defaultDualBands = new int[] {
                    SoftApConfiguration.BAND_2GHZ,
                    SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ};
            assertArrayEquals(config.getBands(), defaultDualBands);
        } else {
            assertEquals(config.getBand(), SoftApConfiguration.BAND_2GHZ);
            assertEquals(config.getBands().length, 1);
        }
    }

    private void verifyDefaultApConfig(SoftApConfiguration config, String expectedSsid) {
        // Old test cases will just verify WPA2.
        verifyDefaultApConfig(config, expectedSsid, false);
    }

    private void verifyDefaultLocalOnlyApConfig(SoftApConfiguration config, String expectedSsid,
            int expectedApBand, boolean isSaeSupport) {
        verifyDefaultLocalOnlyApConfig(config, expectedSsid, expectedApBand, isSaeSupport, true);
    }

    private void verifyDefaultLocalOnlyApConfig(SoftApConfiguration config, String expectedSsid,
            int expectedApBand, boolean isSaeSupport, boolean isMacRandomizationSupport) {
        String[] splitSsid = config.getWifiSsid().getUtf8Text().toString().split("_");
        assertEquals(2, splitSsid.length);
        assertEquals(expectedSsid, splitSsid[0]);
        assertEquals(expectedApBand, config.getBand());
        int randomPortion = Integer.parseInt(splitSsid[1]);
        assertTrue(randomPortion >= RAND_SSID_INT_MIN && randomPortion <= RAND_SSID_INT_MAX);
        if (isSaeSupport) {
            assertEquals(SECURITY_TYPE_WPA3_SAE_TRANSITION, config.getSecurityType());
        } else {
            assertEquals(SECURITY_TYPE_WPA2_PSK, config.getSecurityType());
        }
        assertEquals(15, config.getPassphrase().length());
        assertFalse(config.isAutoShutdownEnabled());
        if (isMacRandomizationSupport) {
            if (SdkLevel.isAtLeastT()) {
                assertEquals(config.getMacRandomizationSettingInternal(),
                        SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT);
            } else {
                assertEquals(config.getMacRandomizationSettingInternal(),
                        SoftApConfiguration.RANDOMIZATION_PERSISTENT);
            }
        } else {
            assertEquals(config.getMacRandomizationSettingInternal(),
                    SoftApConfiguration.RANDOMIZATION_NONE);
        }
    }

    private void verifyDefaultLocalOnlyApConfig(SoftApConfiguration config, String expectedSsid,
            int expectedApBand) {
        verifyDefaultLocalOnlyApConfig(config, expectedSsid, expectedApBand, false);
    }

    /**
     * AP Configuration is not specified in the config file,
     * WifiApConfigStore should fallback to use the default configuration.
     */
    @Test
    public void initWithDefaultConfiguration() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);
    }


    /**
     * Verify the handling of setting a null ap configuration.
     * WifiApConfigStore should fallback to the default configuration when
     * null ap configuration is provided.
     */
    @Test
    public void setNullApConfiguration() throws Exception {
        /* Initialize WifiApConfigStore with existing configuration. */
        SoftApConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",           /* SSID */
                "randomKey",              /* preshared key */
                SECURITY_TYPE_WPA2_PSK,   /* security type */
                SoftApConfiguration.BAND_5GHZ, /* AP band (5GHz) */
                40,                       /* AP channel */
                true                      /* Hidden SSID */);
        WifiApConfigStore store = createWifiApConfigStore();
        mDataStoreSource.fromDeserialized(expectedConfig);
        verifyApConfig(expectedConfig, store.getApConfiguration());
        assertTrue(store.getApConfiguration().isUserConfigurationInternal());
        String lassPassphrase = store.getLastConfiguredTetheredApPassphraseSinceBoot();

        store.setApConfiguration(null);
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        verifyDefaultApConfig(mDataStoreSource.toSerialize(), TEST_DEFAULT_AP_SSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);
        verify(mBackupManagerProxy).notifyDataChanged();
        assertFalse(store.getApConfiguration().isUserConfigurationInternal());
        assertNotEquals(lassPassphrase, store.getLastConfiguredTetheredApPassphraseSinceBoot());
        assertEquals(store.getLastConfiguredTetheredApPassphraseSinceBoot(),
                store.getApConfiguration().getPassphrase());
    }

    /**
     * Verify AP configuration is correctly updated via setApConfiguration call.
     */
    @Test
    public void updateApConfiguration() throws Exception {
        /* Initialize WifiApConfigStore with default configuration. */
        WifiApConfigStore store = createWifiApConfigStore();

        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        assertFalse(store.getApConfiguration().isUserConfigurationInternal());
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);

        /* Update with a valid configuration. */
        SoftApConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",                   /* SSID */
                "randomKey",                      /* preshared key */
                SECURITY_TYPE_WPA2_PSK,           /* security type */
                SoftApConfiguration.BAND_2GHZ,    /* AP band */
                0,                                /* AP channel */
                true                              /* Hidden SSID */);
        store.setApConfiguration(expectedConfig);
        verifyApConfig(expectedConfig, store.getApConfiguration());
        // Verify the persistent randomized MAC address has been added
        assertEquals(TEST_RANDOMIZED_MAC, store.getApConfiguration()
                .getPersistentRandomizedMacAddress());
        verifyApConfig(expectedConfig, mDataStoreSource.toSerialize());
        mLooper.dispatchAll();
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        verify(mBackupManagerProxy, times(2)).notifyDataChanged();
        assertTrue(store.getApConfiguration().isUserConfigurationInternal());
    }

    /**
     * Due to different countries capabilities, some bands are not available.
     * This test verifies that a device will have apBand =
     * 5GHz converted to include 2.4GHz.
     */
    @Test
    public void convertDevice5GhzToAny() throws Exception {
        /* Initialize WifiApConfigStore with default configuration. */
        WifiApConfigStore store = createWifiApConfigStore();
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);

        /* Update with a valid configuration. */
        SoftApConfiguration providedConfig = setupApConfig(
                "ConfiguredAP",                 /* SSID */
                "randomKey",                    /* preshared key */
                SECURITY_TYPE_WPA2_PSK,         /* security type */
                SoftApConfiguration.BAND_5GHZ,  /* AP band */
                0,                              /* AP channel */
                false                           /* Hidden SSID */);

        SoftApConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",                       /* SSID */
                "randomKey",                          /* preshared key */
                SECURITY_TYPE_WPA2_PSK,               /* security type */
                SoftApConfiguration.BAND_2GHZ
                | SoftApConfiguration.BAND_5GHZ,      /* AP band */
                0,                                    /* AP channel */
                false                                 /* Hidden SSID */);
        store.setApConfiguration(providedConfig);
        verifyApConfig(expectedConfig, store.getApConfiguration());
        verifyApConfig(expectedConfig, mDataStoreSource.toSerialize());
        mLooper.dispatchAll();
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        verify(mBackupManagerProxy, times(2)).notifyDataChanged();
    }

    /**
     * Due to different countries capabilities, some bands are not available.
     * This test verifies that a device does not need to conver apBand since it already
     * included 2.4G.
     */
    @Test
    public void deviceAnyNotConverted() throws Exception {
        /* Initialize WifiApConfigStore with default configuration. */
        WifiApConfigStore store = createWifiApConfigStore();
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);

        /* Update with a valid configuration. */
        SoftApConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",                 /* SSID */
                "randomKey",                    /* preshared key */
                SECURITY_TYPE_WPA2_PSK,         /* security type */
                mBand256G,                      /* AP band */
                0,                              /* AP channel */
                false                           /* Hidden SSID */);
        store.setApConfiguration(expectedConfig);
        verifyApConfig(expectedConfig, store.getApConfiguration());
        verifyApConfig(expectedConfig, mDataStoreSource.toSerialize());
        mLooper.dispatchAll();
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        verify(mBackupManagerProxy, times(2)).notifyDataChanged();
    }

    /**
     * Due to different countries capabilities, some bands are not available.
     * This test verifies that a device does not convert apBand because channel is specific.
     */
    @Test
    public void deviceWithChannelNotConverted() throws Exception {
        /* Initialize WifiApConfigStore with default configuration. */
        WifiApConfigStore store = createWifiApConfigStore();
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);

        /* Update with a valid configuration. */
        SoftApConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",                 /* SSID */
                "randomKey",                    /* preshared key */
                SECURITY_TYPE_WPA2_PSK,         /* security type */
                SoftApConfiguration.BAND_5GHZ,  /* AP band */
                40,                             /* AP channel */
                false                           /* Hidden SSID */);
        store.setApConfiguration(expectedConfig);
        verifyApConfig(expectedConfig, store.getApConfiguration());
        verifyApConfig(expectedConfig, mDataStoreSource.toSerialize());
        mLooper.dispatchAll();
        verify(mWifiConfigManager, times(2)).saveToStore(true);
        verify(mBackupManagerProxy, times(2)).notifyDataChanged();
    }

    /**
     * Due to different countries capabilities, some bands are not available.
     * This test verifies that a device converts a persisted ap
     * config with 5GHz only set for the apBand to include 2.4GHz.
     */
    @Test
    public void device5GhzConvertedToAnyAtRetrieval() throws Exception {
        SoftApConfiguration persistedConfig = setupApConfig(
                "ConfiguredAP",                  /* SSID */
                "randomKey",                     /* preshared key */
                SECURITY_TYPE_WPA2_PSK,          /* security type */
                SoftApConfiguration.BAND_5GHZ,   /* AP band */
                0,                               /* AP channel */
                false                            /* Hidden SSID */);
        SoftApConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",                       /* SSID */
                "randomKey",                          /* preshared key */
                SECURITY_TYPE_WPA2_PSK,               /* security type */
                mBand25G,                             /* AP band */
                0,                                    /* AP channel */
                false                                 /* Hidden SSID */);

        WifiApConfigStore store = createWifiApConfigStore();
        mDataStoreSource.fromDeserialized(persistedConfig);
        verifyApConfig(expectedConfig, store.getApConfiguration());
        verifyApConfig(expectedConfig, mDataStoreSource.toSerialize());
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);
        verify(mBackupManagerProxy).notifyDataChanged();
    }

    /**
     * Due to different countries capabilities, some bands are not available.
     * This test verifies that a device does not convert
     * a persisted ap config with ANY  since it already
     * included 2.4G.
     */
    @Test
    public void deviceNotConvertedAtRetrieval() throws Exception {
        SoftApConfiguration persistedConfig = setupApConfig(
                "ConfiguredAP",                 /* SSID */
                "randomKey",                    /* preshared key */
                SECURITY_TYPE_WPA2_PSK,         /* security type */
                mBand256G,                      /* AP band */
                0,                              /* AP channel */
                false                           /* Hidden SSID */);

        WifiApConfigStore store = createWifiApConfigStore();
        mDataStoreSource.fromDeserialized(persistedConfig);
        verifyApConfig(persistedConfig, store.getApConfiguration());
        mLooper.dispatchAll();
        verify(mWifiConfigManager, never()).saveToStore(true);
        verify(mBackupManagerProxy, never()).notifyDataChanged();
    }

    /**
     * Verify a proper SoftApConfiguration is generate by getDefaultApConfiguration().
     */
    @Test
    public void getDefaultApConfigurationIsValid() throws Exception {
        // Default, new feature doesn't supported
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.getApConfiguration();
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    /**
     * Verify a proper local only hotspot config is generated when called properly with the valid
     * context.
     */
    @Test
    public void generateLocalOnlyHotspotConfigIsValid() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    /**
     * Verify a proper local only hotspot config is generated for 5Ghz band when overlay configured
     */
    @Test
    public void generateLocalOnlyHotspotConfigWhenOverlayConfigureTo5G() throws Exception {
        //leslea
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(true);
        when(mSoftApCapability.getSupportedChannelList(SoftApConfiguration.BAND_5GHZ))
                .thenReturn(new int[] {36});
        mResources.setBoolean(R.bool.config_wifi_local_only_hotspot_5ghz, true);
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    /**
     * Verify a proper local only hotspot config is generated for 2Ghz band when overlay configured
     * but there is no available 5g channel.
     */
    @Test
    public void generateLocalOnlyHotspotConfigWhenOverlayConfigureTo5GButNoAvailable5GChannel()
            throws Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(true);
        mResources.setBoolean(R.bool.config_wifi_local_only_hotspot_5ghz, true);
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    /**
     * Verify a proper local only hotspot config is generated for 6Ghz band when overlay configured
     */
    @Test
    public void generateLocalOnlyHotspotConfigWhenOverlayConfigureTo6G() throws Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(true);
        mResources.setBoolean(R.bool.config_wifiLocalOnlyHotspot6ghz, true);
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_6GHZ);

        SoftApConfiguration updatedConfig = new SoftApConfiguration.Builder(config)
                .setPassphrase("somepassword", SECURITY_TYPE_WPA3_SAE)
                .build();

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(updatedConfig, true, mContext,
                mWifiNative));
    }

    /**
     * Verify a proper local only hotspot config is generated for 5Ghz and 6Ghz band when overlay
     * configured
     */
    @Test
    public void generateLocalOnlyHotspotConfigWhenOverlayConfigureTo5GAnd6G() throws Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(true);
        mResources.setBoolean(R.bool.config_wifiLocalOnlyHotspot6ghz, true);
        mResources.setBoolean(R.bool.config_wifi_local_only_hotspot_5ghz, true);
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
                        | SoftApConfiguration.BAND_6GHZ);

        SoftApConfiguration updatedConfig = new SoftApConfiguration.Builder(config)
                .setPassphrase("somepassword", SECURITY_TYPE_WPA3_SAE)
                .build();

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    @Test
    public void generateLohsConfig_forwardsCustomBand() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration.Builder customConfigBuilder = new SoftApConfiguration.Builder()
                .setBand(SoftApConfiguration.BAND_5GHZ);
        SoftApConfiguration softApConfig = store.generateLocalOnlyHotspotConfig(
                mContext, customConfigBuilder.build(), mSoftApCapability);
        assertThat(softApConfig.getBand()).isEqualTo(SoftApConfiguration.BAND_5GHZ);

        // Test 6G band
        customConfigBuilder.setBand(SoftApConfiguration.BAND_6GHZ);
        softApConfig = store.generateLocalOnlyHotspotConfig(
                mContext, customConfigBuilder.build(), mSoftApCapability);
        assertThat(softApConfig.getBand()).isEqualTo(SoftApConfiguration.BAND_6GHZ);
    }

    @Test
    public void generateLohsConfig_forwardsCustomMac() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration.Builder customConfigBuilder = new SoftApConfiguration.Builder()
                .setBssid(TEST_SAP_BSSID_MAC);
        if (SdkLevel.isAtLeastS()) {
            customConfigBuilder.setMacRandomizationSetting(
                    SoftApConfiguration.RANDOMIZATION_NONE);
        }
        SoftApConfiguration customConfig = customConfigBuilder.build();
        SoftApConfiguration softApConfig = store.generateLocalOnlyHotspotConfig(
                mContext, customConfig, mSoftApCapability);
        assertThat(softApConfig.getBssid().toString()).isNotEmpty();
        assertThat(softApConfig.getBssid()).isEqualTo(TEST_SAP_BSSID_MAC);
    }

    @Test
    public void randomizeBssid_randomizesPersistWhenEnabled() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        SoftApConfiguration baseConfig = new SoftApConfiguration.Builder()
                .setMacRandomizationSetting(RANDOMIZATION_PERSISTENT).build();

        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.randomizeBssidIfUnset(mContext, baseConfig);

        assertEquals(TEST_RANDOMIZED_MAC, config.getBssid());

        // The MAC is persist when configuration doesn't be changed.
        config = store.randomizeBssidIfUnset(mContext, baseConfig);

        assertEquals(TEST_RANDOMIZED_MAC, config.getBssid());
    }

    @Test
    public void randomizeBssid_randomizesNonPersistWhenEnabled() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        SoftApConfiguration baseConfig = new SoftApConfiguration.Builder()
                .setMacRandomizationSetting(RANDOMIZATION_NON_PERSISTENT).build();

        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.randomizeBssidIfUnset(mContext, baseConfig);

        // Verify that some randomized MAC address is still generated
        MacAddress firstRandomizedMAC = config.getBssid();
        assertNotNull(config.getBssid());
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, firstRandomizedMAC.toString());

        // Call again and verify it will randomize again.
        config = store.randomizeBssidIfUnset(mContext, baseConfig);
        assertNotNull(config.getBssid());
        assertNotEquals(firstRandomizedMAC, config.getBssid());
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, config.getBssid().toString());
    }

    @Test
    public void randomizeBssid_fallbackPathWhenMacCalculationFails() throws Exception {
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        // Setup the MAC calculation to fail.
        when(mMacAddressUtil.calculatePersistentMacForSap(any(), anyInt())).thenReturn(null);
        SoftApConfiguration baseConfig = new SoftApConfiguration.Builder().build();

        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.randomizeBssidIfUnset(mContext, baseConfig);

        // Verify that some randomized MAC address is still generated
        assertNotNull(config.getBssid());
        assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, config.getBssid().toString());
    }

    @Test
    public void randomizeBssid_usesFactoryMacWhenRandomizationOff() throws Exception {
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, false);
        SoftApConfiguration baseConfig = new SoftApConfiguration.Builder().build();

        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.randomizeBssidIfUnset(mContext, baseConfig);

        assertThat(config.getBssid()).isNull();
    }

    @Test
    public void randomizeBssid_forwardsCustomMac() throws Exception {
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        Builder baseConfigBuilder = new SoftApConfiguration.Builder();
        baseConfigBuilder.setBssid(TEST_SAP_BSSID_MAC);
        if (SdkLevel.isAtLeastS()) {
            baseConfigBuilder.setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
        }

        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.randomizeBssidIfUnset(mContext,
                baseConfigBuilder.build());

        assertThat(config.getBssid().toString()).isNotEmpty();
        assertThat(config.getBssid()).isEqualTo(TEST_SAP_BSSID_MAC);
    }

    @Test
    public void randomizeBssid__usesFactoryMacWhenRandomizationOffInConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        SoftApConfiguration baseConfig = new SoftApConfiguration.Builder()
                .setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE).build();

        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.randomizeBssidIfUnset(mContext, baseConfig);

        assertThat(config.getBssid()).isNull();
    }

    /**
     * Helper method to generate random SSIDs.
     *
     * Note: this method has limited use as a random SSID generator.  The characters used in this
     * method do no not cover all valid inputs.
     * @param length number of characters to generate for the name
     * @return String generated string of random characters
     */
    private String generateRandomString(int length) {

        StringBuilder stringBuilder = new StringBuilder(length);
        int index = -1;
        while (stringBuilder.length() < length) {
            index = mRandom.nextInt(TEST_CHAR_SET_AS_STRING.length());
            stringBuilder.append(TEST_CHAR_SET_AS_STRING.charAt(index));
        }
        return stringBuilder.toString();
    }

    /**
     * Verify the SSID check in validateApWifiConfiguration.
     */
    @Test
    public void testSsidVerificationInValidateApWifiConfigurationCheck() {
        Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid(null);
        // Invalid due to null SSID.
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));

        // SSID is set, so the config is now valid.
        configBuilder.setSsid("ssid");
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));
    }

    /**
     * Verify the 6GHz restrictions on security types.
     */
    @Test
    public void test6ghzRestrictionsSecurityTypes() {
        Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid("ssid");
        configBuilder.setBand(SoftApConfiguration.BAND_6GHZ);

        // OPEN
        configBuilder.setPassphrase(null, SoftApConfiguration.SECURITY_TYPE_OPEN);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));

        // WPA2
        configBuilder.setPassphrase("somepassword", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));

        // WPA3 SAE (should succeed)
        configBuilder.setPassphrase("somepassword", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));

        // WPA3 SAE-Transition
        configBuilder.setPassphrase("somepassword",
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));

        if (SdkLevel.isAtLeastT()) {
            // WPA3 OWE-Transition
            if (SdkLevel.isAtLeastT()) {
                configBuilder.setPassphrase(null,
                        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION);
                assertFalse(WifiApConfigStore.validateApWifiConfiguration(
                        configBuilder.build(), true, mContext, mWifiNative));
            }
        }
    }

    /**
     * Verify the Open network checks in validateApWifiConfiguration.
     *
     * If the configured network is open, it should not have a password set.
     *
     * Additionally verify a valid open network passes verification.
     */
    @Test
    public void testOpenNetworkConfigInValidateApWifiConfigurationCheck() {
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(
                new SoftApConfiguration.Builder()
                .setSsid(TEST_DEFAULT_HOTSPOT_SSID)
                .build(), true, mContext, mWifiNative));
    }

    /**
     * Verify the WPA2_PSK network checks in validateApWifiConfiguration.
     *
     * If the configured network is configured with a preSharedKey, verify that the passwork is set
     * and it meets length requirements.
     */
    @Test
    public void testWpa2PskNetworkConfigInValidateApWifiConfigurationCheck() {
        Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid(TEST_DEFAULT_HOTSPOT_SSID);
        // Builder will auto check auth type and passphrase

        // test random (valid length)
        int maxLen = WifiApConfigStore.PSK_SAE_ASCII_MAX_LEN;
        int minLen = WifiApConfigStore.PSK_ASCII_MIN_LEN;
        configBuilder.setPassphrase(
                generateRandomString(mRandom.nextInt(maxLen - minLen) + minLen),
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));
    }

    @Test
    public void testSaeNetworkConfigInValidateApWifiConfigurationCheck() {
        Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid(TEST_DEFAULT_HOTSPOT_SSID);

        // test random (invalid length)
        int maxLen = WifiApConfigStore.PSK_SAE_ASCII_MAX_LEN;
        configBuilder.setPassphrase(
                generateRandomString(maxLen + 1),
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));

    }

    /**
     * Verify the WPA2_PSK network checks in validateApWifiConfiguration.
     *
     * If the configured network is configured with a preSharedKey, verify that the passwork is set
     * and it meets ascii encoding when ascii encodable check enabled.
     */
    @Test
    public void testWpa2PskNonAsciiPassphraseConfigInValidateApWifiConfigurationCheck() {
        Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setSsid(TEST_DEFAULT_HOTSPOT_SSID);
        // Builder will auto check auth type and passphrase

        configBuilder.setPassphrase(
                "測試測試測試測試",
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));
        // Disable ascii encodable check
        mResources.setBoolean(R.bool.config_wifiSoftapPassphraseAsciiEncodableCheck, false);
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(
                configBuilder.build(), true, mContext, mWifiNative));
    }


    /**
     * Verify the default configuration security when SAE support.
     */
    @Test
    public void testDefaultConfigurationSecurityTypeIsWpa3SaeTransitionWhenSupport()
            throws Exception {
        mResources.setBoolean(R.bool.config_wifi_softap_sae_supported, true);
        WifiApConfigStore store = createWifiApConfigStore();
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID, true);
    }

    /**
     * Verify the LOHS default configuration security when SAE support.
     */
    @Test
    public void testLohsDefaultConfigurationSecurityTypeIsWpa3SaeTransitionWhenSupport()
            throws Exception {
        mResources.setBoolean(R.bool.config_wifi_softap_sae_supported, true);
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ, true);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

    }

    /**
     * Verify the LOHS default configuration when MAC randomization support.
     */
    @Test
    public void testLohsDefaultConfigurationWhenMacRandomizationSupport() throws Exception {
        mResources.setBoolean(R.bool.config_wifi_softap_sae_supported, true);
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ, true, true);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    /**
     * Verify the LOHS default configuration when MAC randomization doesn't support.
     */
    @Test
    public void testLohsDefaultConfigurationWhenMacRandomizationDoesntSupport()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mResources.setBoolean(R.bool.config_wifi_softap_sae_supported, true);
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, false);
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ, true, false);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    /**
     * Verify the LOHS default configuration when MAC randomization support but it was disabled in
     * tethered configuration.
     */
    @Test
    public void testLohsDefaultConfigurationWhenMacRandomizationDisabledInTetheredCongig()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mResources.setBoolean(R.bool.config_wifi_softap_sae_supported, true);
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        WifiApConfigStore storeMacRandomizationSupported = createWifiApConfigStore();
        SoftApConfiguration disableMacRandomizationConfig = new SoftApConfiguration
                .Builder(storeMacRandomizationSupported.getApConfiguration())
                .setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE).build();
        storeMacRandomizationSupported.setApConfiguration(disableMacRandomizationConfig);
        SoftApConfiguration config = storeMacRandomizationSupported
                .generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability);
        verifyDefaultLocalOnlyApConfig(config, TEST_DEFAULT_HOTSPOT_SSID,
                SoftApConfiguration.BAND_2GHZ, true, false);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }


    /**
     * Verify the default configuration when MAC randomization support.
     */
    @Test
    public void testDefaultConfigurationWhenMacRandomizationSupport()
            throws Exception {
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        WifiApConfigStore storeMacRandomizationSupported = createWifiApConfigStore();
        verifyDefaultApConfig(storeMacRandomizationSupported.getApConfiguration(),
                TEST_DEFAULT_AP_SSID, false, true, false);
    }

    /**
     * Verify the default configuration when bridged AP support.
     */
    @Test
    public void testDefaultConfigurationWhenBridgedSupport()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mResources.setBoolean(R.bool.config_wifiBridgedSoftApSupported, true);
        WifiApConfigStore storeMacRandomizationAndBridgedApSupported = createWifiApConfigStore();
        verifyDefaultApConfig(storeMacRandomizationAndBridgedApSupported.getApConfiguration(),
                TEST_DEFAULT_AP_SSID, false, true, true);
    }

    @Test
    public void testResetToDefaultForUnsupportedConfig() throws Exception {
        mResources.setBoolean(R.bool.config_wifiSofapClientForceDisconnectSupported, true);
        mResources.setBoolean(R.bool.config_wifi_softap_sae_supported, true);
        mResources.setBoolean(R.bool.config_wifi5ghzSupport, true);
        mResources.setBoolean(R.bool.config_wifi6ghzSupport, true);
        mResources.setBoolean(R.bool.config_wifi60ghzSupport, true);
        mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, true);
        mResources.setBoolean(R.bool.config_wifiBridgedSoftApSupported, true);

        // Test all of the features support and all of the reset config are false.
        String testPassphrase = "secretsecret";
        int[] testDualBands = new int[] {
                SoftApConfiguration.BAND_2GHZ, SoftApConfiguration.BAND_5GHZ};
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setPassphrase(testPassphrase, SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        if (SdkLevel.isAtLeastS()) {
            configBuilder.setBands(testDualBands);
        }
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration resetedConfig = store.resetToDefaultForUnsupportedConfig(
                configBuilder.build());
        assertEquals(resetedConfig, configBuilder.build());

        verify(mWifiMetrics).noteSoftApConfigReset(configBuilder.build(), resetedConfig);


        // Test band is 6Ghz but new device doesn't support 60Ghz
        mResources.setBoolean(R.bool.config_wifi60ghzSupport, false);
        configBuilder.setBand(SoftApConfiguration.BAND_60GHZ);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(resetedConfig.getBand(), SoftApConfiguration.BAND_2GHZ);

        // Test band is 6Ghz but new device doesn't support 6Ghz
        mResources.setBoolean(R.bool.config_wifi6ghzSupport, false);
        configBuilder.setBand(SoftApConfiguration.BAND_6GHZ);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(resetedConfig.getBand(), SoftApConfiguration.BAND_2GHZ);

        // Test bridged mode reset because the band is not valid.
        if (SdkLevel.isAtLeastS()) {
            mResources.setBoolean(R.bool.config_wifi5ghzSupport, false);
            resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
            assertEquals(resetedConfig.getBand(), SoftApConfiguration.BAND_2GHZ);
            assertEquals(resetedConfig.getBands().length, 1);

            // Test bridged mode reset
            mResources.setBoolean(R.bool.config_wifiBridgedSoftApSupported, false);
            configBuilder.setBands(testDualBands);
            resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
            assertEquals(resetedConfig.getBand(), SoftApConfiguration.BAND_2GHZ);
            assertEquals(resetedConfig.getBands().length, 1);

            // Test AP MAC randomization not support case.
            mResources.setBoolean(R.bool.config_wifi_ap_mac_randomization_supported, false);
            resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
            assertEquals(resetedConfig.getMacRandomizationSettingInternal(),
                    SoftApConfiguration.RANDOMIZATION_NONE);
        }
        // Test SAE not support case.
        mResources.setBoolean(R.bool.config_wifi_softap_sae_supported, false);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(resetedConfig.getSecurityType(), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        // Test force channel
        mResources.setBoolean(R.bool.config_wifi5ghzSupport, true);
        configBuilder.setChannel(149, SoftApConfiguration.BAND_5GHZ);
        mResources.setBoolean(
                R.bool.config_wifiSoftapResetChannelConfig, true);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(resetedConfig.getChannel(), 0);
        assertEquals(resetedConfig.getBand(),
                SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ);

        // Test forced channel band doesn't support.
        mResources.setBoolean(R.bool.config_wifi5ghzSupport, false);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(resetedConfig.getChannel(), 0);
        assertEquals(resetedConfig.getBand(),
                SoftApConfiguration.BAND_2GHZ);

        // Test band not support with auto channel
        configBuilder.setBand(SoftApConfiguration.BAND_5GHZ);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(resetedConfig.getBand(), SoftApConfiguration.BAND_2GHZ);

        // Test reset hidden network
        mResources.setBoolean(
                R.bool.config_wifiSoftapResetHiddenConfig, true);
        configBuilder.setHiddenSsid(true);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertFalse(resetedConfig.isHiddenSsid());

        // Test auto shutdown timer
        mResources.setBoolean(
                R.bool.config_wifiSoftapResetAutoShutdownTimerConfig, true);
        configBuilder.setShutdownTimeoutMillis(8888);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(-1, resetedConfig.getShutdownTimeoutMillis());

        // Test max client setting when force client disconnect doesn't support
        mResources.setBoolean(R.bool.config_wifiSofapClientForceDisconnectSupported, false);
        configBuilder.setMaxNumberOfClients(10);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertEquals(resetedConfig.getMaxNumberOfClients(), 0);

        // Test client control setting when force client disconnect doesn't support
        mResources.setBoolean(R.bool.config_wifiSofapClientForceDisconnectSupported, false);
        ArrayList<MacAddress> blockedClientList = new ArrayList<>();
        ArrayList<MacAddress> allowedClientList = new ArrayList<>();
        blockedClientList.add(MacAddress.fromString("11:22:33:44:55:66"));
        allowedClientList.add(MacAddress.fromString("aa:bb:cc:dd:ee:ff"));
        configBuilder.setBlockedClientList(blockedClientList);
        configBuilder.setAllowedClientList(allowedClientList);

        configBuilder.setClientControlByUserEnabled(true);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertFalse(resetedConfig.isClientControlByUserEnabled());
        // The blocked list will be clean
        assertEquals(resetedConfig.getBlockedClientList().size(), 0);
        // The allowed list will be keep
        assertEquals(resetedConfig.getAllowedClientList(), allowedClientList);

        // Test max client setting when reset enabled but force client disconnect supported
        mResources.setBoolean(R.bool.config_wifiSofapClientForceDisconnectSupported, true);
        mResources.setBoolean(
                R.bool.config_wifiSoftapResetMaxClientSettingConfig, true);
        assertEquals(resetedConfig.getMaxNumberOfClients(), 0);

        // Test client control setting when reset enabled but force client disconnect supported
        mResources.setBoolean(R.bool.config_wifiSofapClientForceDisconnectSupported, true);
        mResources.setBoolean(
                R.bool.config_wifiSoftapResetUserControlConfig, true);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertFalse(resetedConfig.isClientControlByUserEnabled());
        assertEquals(resetedConfig.getBlockedClientList().size(), 0);
        assertEquals(resetedConfig.getAllowedClientList(), allowedClientList);

        // Test blocked list setting will be reset even if client control disabled
        mResources.setBoolean(
                R.bool.config_wifiSoftapResetUserControlConfig, true);
        configBuilder.setClientControlByUserEnabled(false);
        resetedConfig = store.resetToDefaultForUnsupportedConfig(configBuilder.build());
        assertFalse(resetedConfig.isClientControlByUserEnabled());
        assertEquals(resetedConfig.getBlockedClientList().size(), 0);
        assertEquals(resetedConfig.getAllowedClientList(), allowedClientList);
    }

    /**
     * Verify Bssid field deny to set if caller without setting permission.
     */
    @Test
    public void testBssidDenyIfCallerWithoutPrivileged() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration.Builder configBuilder =
                new SoftApConfiguration.Builder(store.getApConfiguration())
                .setBssid(TEST_SAP_BSSID_MAC);
        if (SdkLevel.isAtLeastS()) {
            configBuilder.setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
        }
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(configBuilder.build(),
                false, mContext, mWifiNative));
    }

    /**
     * Verify Bssid field only allow to set if caller own setting permission.
     */
    @Test
    public void testBssidAllowIfCallerOwnPrivileged() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration.Builder configBuilder =
                new SoftApConfiguration.Builder(store.getApConfiguration())
                .setBssid(TEST_SAP_BSSID_MAC);
        if (SdkLevel.isAtLeastS()) {
            configBuilder.setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
        }
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(configBuilder.build(),
                true, mContext, mWifiNative));
    }

    @Test
    public void testUnSupportedBandConfigurd() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = new SoftApConfiguration.Builder(store.getApConfiguration())
                .setBand(mBand25660G).build();
        // Default is all bands supported.
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        mResources.setBoolean(R.bool.config_wifi24ghzSupport, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        setupAllBandsSupported();
        mResources.setBoolean(R.bool.config_wifiSoftap24ghzSupported, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        setupAllBandsSupported();
        mResources.setBoolean(R.bool.config_wifi5ghzSupport, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        setupAllBandsSupported();
        mResources.setBoolean(R.bool.config_wifiSoftap5ghzSupported, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        setupAllBandsSupported();
        mResources.setBoolean(R.bool.config_wifi6ghzSupport, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        setupAllBandsSupported();
        mResources.setBoolean(R.bool.config_wifiSoftap6ghzSupported, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        setupAllBandsSupported();
        mResources.setBoolean(R.bool.config_wifi60ghzSupport, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));

        setupAllBandsSupported();
        mResources.setBoolean(R.bool.config_wifiSoftap60ghzSupported, false);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config, true, mContext,
                mWifiNative));
    }

    @Test
    public void testSanitizePersistentApConfig() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config5Gonly = setupApConfig(
                "ConfiguredAP",                   /* SSID */
                "randomKey",                      /* preshared key */
                SECURITY_TYPE_WPA2_PSK,           /* security type */
                SoftApConfiguration.BAND_5GHZ,    /* AP band */
                0,                                /* AP channel */
                true                              /* Hidden SSID */);
        // Test 5G only band config will be appended 2.4G band into config
        SoftApConfiguration.Builder testConfigBuilder =
                new SoftApConfiguration.Builder(config5Gonly);
        SoftApConfiguration.Builder expectedConfigBuilder =
                new SoftApConfiguration.Builder(testConfigBuilder.build())
                .setBand(SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ);
        store.setApConfiguration(testConfigBuilder.build());
        verifyApConfig(expectedConfigBuilder.build(), store.getApConfiguration());

        // Test 6G only band config will be appended 2.4G & 5G band into config.
        testConfigBuilder.setBand(SoftApConfiguration.BAND_6GHZ);

        expectedConfigBuilder.setBand(SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
                | SoftApConfiguration.BAND_6GHZ);
        store.setApConfiguration(testConfigBuilder.build());
        verifyApConfig(expectedConfigBuilder.build(), store.getApConfiguration());

        // Dual bands test case
        if (SdkLevel.isAtLeastS()) {
            testConfigBuilder.setBands(new int[] {SoftApConfiguration.BAND_2GHZ,
                    SoftApConfiguration.BAND_5GHZ}).build();

            expectedConfigBuilder.setBands(new int[] {SoftApConfiguration.BAND_2GHZ,
                    SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ});
            store.setApConfiguration(testConfigBuilder.build());
            verifyApConfig(expectedConfigBuilder.build(), store.getApConfiguration());

            testConfigBuilder.setBands(new int[] {SoftApConfiguration.BAND_2GHZ,
                    SoftApConfiguration.BAND_6GHZ}).build();

            expectedConfigBuilder.setBands(new int[] {SoftApConfiguration.BAND_2GHZ,
                    SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
                            | SoftApConfiguration.BAND_6GHZ});
            store.setApConfiguration(testConfigBuilder.build());
            verifyApConfig(expectedConfigBuilder.build(), store.getApConfiguration());
        }
    }

    @Test
    public void testSanitizePersistentApConfigWhenAutoAppendDisabled() throws Exception {
        // Test when resource disabled
        mResources.setBoolean(
                R.bool.config_wifiSoftapAutoAppendLowerBandsToBandConfigurationEnabled,
                false);
        SoftApConfiguration config5Gonly = setupApConfig(
                "ConfiguredAP",                   /* SSID */
                "randomKey",                      /* preshared key */
                SECURITY_TYPE_WPA2_PSK,           /* security type */
                SoftApConfiguration.BAND_5GHZ,    /* AP band */
                0,                                /* AP channel */
                true                              /* Hidden SSID */);
        WifiApConfigStore store_disableAutoAppendBand = createWifiApConfigStore();
        store_disableAutoAppendBand.setApConfiguration(config5Gonly);
        verifyApConfig(config5Gonly, store_disableAutoAppendBand.getApConfiguration());

        SoftApConfiguration config6Gonly =
                new SoftApConfiguration.Builder(config5Gonly)
                .setBand(SoftApConfiguration.BAND_6GHZ).build();

        store_disableAutoAppendBand.setApConfiguration(config6Gonly);
        verifyApConfig(config6Gonly, store_disableAutoAppendBand.getApConfiguration());
    }

    @Test
    public void testForceApBandChannel() throws Exception {
        int testBand = SoftApConfiguration.BAND_5GHZ; // Not default
        int testChannal = 149;
        WifiApConfigStore store = createWifiApConfigStore();
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).saveToStore(true);

        // Test to enable forced AP band
        store.enableForceSoftApBandOrChannel(testBand, 0, SoftApInfo.CHANNEL_WIDTH_AUTO);
        SoftApConfiguration expectedConfig = store.getApConfiguration();
        assertEquals(testBand, expectedConfig.getBand());
        assertEquals(0, expectedConfig.getChannel());
        if (SdkLevel.isAtLeastT()) {
            assertEquals(expectedConfig.getMaxChannelBandwidth(), SoftApInfo.CHANNEL_WIDTH_AUTO);
        }
        // Disable forced AP band
        store.disableForceSoftApBandOrChannel();
        expectedConfig = store.getApConfiguration();
        assertEquals(SoftApConfiguration.BAND_2GHZ, expectedConfig.getBand());
        assertEquals(0, expectedConfig.getChannel());
        if (SdkLevel.isAtLeastT()) {
            assertEquals(SoftApInfo.CHANNEL_WIDTH_AUTO, expectedConfig.getMaxChannelBandwidth());
        }

        // Test to enable forced AP band
        store.enableForceSoftApBandOrChannel(testBand, testChannal, SoftApInfo.CHANNEL_WIDTH_40MHZ);
        expectedConfig = store.getApConfiguration();
        assertEquals(testBand, expectedConfig.getBand());
        assertEquals(testChannal, expectedConfig.getChannel());
        if (SdkLevel.isAtLeastT()) {
            assertEquals(SoftApInfo.CHANNEL_WIDTH_40MHZ, expectedConfig.getMaxChannelBandwidth());
        }
        // Disable forced AP band
        store.disableForceSoftApBandOrChannel();
        expectedConfig = store.getApConfiguration();
        assertEquals(SoftApConfiguration.BAND_2GHZ, expectedConfig.getBand());
        assertEquals(0, expectedConfig.getChannel());
        if (SdkLevel.isAtLeastT()) {
            assertEquals(SoftApInfo.CHANNEL_WIDTH_AUTO, expectedConfig.getMaxChannelBandwidth());
        }
    }

    private void verifyUpgradeConfiguration(WifiApConfigStore store, boolean isBridgedSupported,
            boolean isUpgradeEnabledInOverlayConfig) throws Exception {
        mResources.setBoolean(R.bool.config_wifiBridgedSoftApSupported, isBridgedSupported);
        mResources.setBoolean(R.bool.config_wifiSoftapAutoUpgradeToBridgedConfigWhenSupported,
                isUpgradeEnabledInOverlayConfig);
        SoftApConfiguration singleConfig2G = setupApConfig(
                "ConfiguredAP",                   /* SSID */
                "randomKey",                      /* preshared key */
                SECURITY_TYPE_WPA2_PSK,           /* security type */
                SoftApConfiguration.BAND_2GHZ,    /* AP band */
                0,                                /* AP channel */
                true                              /* Hidden SSID */);
        SoftApConfiguration bridgedConfig2GAnd5G = new SoftApConfiguration.Builder(singleConfig2G)
                .setBands(new int[] {SoftApConfiguration.BAND_2GHZ,
                        SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ})
                .build();

        final SoftApConfiguration upgradedConfig = store.upgradeSoftApConfiguration(singleConfig2G);
        if (isBridgedSupported && isUpgradeEnabledInOverlayConfig) {
            assertEquals(bridgedConfig2GAnd5G, upgradedConfig);
        } else {
            assertEquals(singleConfig2G, upgradedConfig);
        }
    }

    @Test
    public void testUpgradeSoftApConfiguration() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiApConfigStore store = createWifiApConfigStore();
        verifyUpgradeConfiguration(store, true, true);
        verifyUpgradeConfiguration(store, false, true);
        verifyUpgradeConfiguration(store, true, false);
        verifyUpgradeConfiguration(store, false, false);
    }

    @Test
    public void testPersistentRandomizedMacAddress() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        assertEquals(TEST_RANDOMIZED_MAC, store.getApConfiguration()
                .getPersistentRandomizedMacAddress());
        assertEquals(TEST_RANDOMIZED_MAC,
                store.generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability)
                .getPersistentRandomizedMacAddress());
        assertEquals(TEST_RANDOMIZED_MAC,
                store.generateLocalOnlyHotspotConfig(mContext, store.getApConfiguration(),
                mSoftApCapability).getPersistentRandomizedMacAddress());
    }

    @Test
    public void testPersistentRandomizedMacAddressWhenCalculatedMacIsNull() throws Exception {
        when(mMacAddressUtil.calculatePersistentMacForSap(any(), anyInt())).thenReturn(null);
        WifiApConfigStore store = createWifiApConfigStore();
        assertNotNull(store.getApConfiguration().getPersistentRandomizedMacAddress());
        assertNotNull(
                store.generateLocalOnlyHotspotConfig(mContext, null, mSoftApCapability)
                .getPersistentRandomizedMacAddress());
        assertNotNull(
                store.generateLocalOnlyHotspotConfig(mContext, store.getApConfiguration(),
                mSoftApCapability).getPersistentRandomizedMacAddress());
    }

    @Test
    public void testLastPassphraseIsNonNull() throws Exception {
        WifiApConfigStore store = createWifiApConfigStore();
        SoftApConfiguration config = store.getApConfiguration();
        String lastPassphrase = store.getLastConfiguredTetheredApPassphraseSinceBoot();
        assertNotNull(lastPassphrase);
        assertEquals(config.getPassphrase(),
                store.getLastConfiguredTetheredApPassphraseSinceBoot());
        // Set to none security
        store.setApConfiguration(new SoftApConfiguration.Builder()
                .setSsid(TEST_DEFAULT_HOTSPOT_SSID).build());
        assertEquals(lastPassphrase, store.getLastConfiguredTetheredApPassphraseSinceBoot());
    }
}
