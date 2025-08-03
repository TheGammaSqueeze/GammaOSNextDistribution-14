/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

import android.net.wifi.WifiManager;
import android.util.ArrayMap;
import android.util.Xml;

import androidx.test.filters.SmallTest;

import com.android.internal.util.FastXmlSerializer;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.XmlUtil;

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
import java.util.Map;

/**
 * Unit tests for {@link com.android.server.wifi.WifiRoamingConfigStoreTest}.
 */
@SmallTest
public class WifiRoamingConfigStoreTest extends WifiBaseTest {
    @Mock
    private WifiConfigStore mWifiConfigStore;
    @Mock
    private WifiConfigManager mWifiConfigManager;
    private WifiRoamingConfigStore mWifiRoamingConfigStore;
    private static final String TEST_SSID = "SSID1";
    private static final int TEST_ROAMING_MODE = WifiManager.ROAMING_MODE_AGGRESSIVE;
    private static final int TEST_DEFAULT_ROAMING_MODE = WifiManager.ROAMING_MODE_NORMAL;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiRoamingConfigStore =
                new WifiRoamingConfigStore(mWifiConfigManager, mWifiConfigStore);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void testRoamingModeByDeviceAdmin() {
        assumeTrue(SdkLevel.isAtLeastV());
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_DEFAULT_ROAMING_MODE);
        mWifiRoamingConfigStore.addRoamingMode(TEST_SSID, TEST_ROAMING_MODE, true);
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(true).get(TEST_SSID)
                == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(false).isEmpty());

        mWifiRoamingConfigStore.removeRoamingMode(TEST_SSID, true);
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_DEFAULT_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(true).isEmpty());

        verify(mWifiConfigManager, times(2)).saveToStore(true);
    }

    @Test
    public void testRoamingModeByNonAdmin() {
        assumeTrue(SdkLevel.isAtLeastV());
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_DEFAULT_ROAMING_MODE);
        mWifiRoamingConfigStore.addRoamingMode(TEST_SSID, TEST_ROAMING_MODE, false);
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(false).get(TEST_SSID)
                == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(true).isEmpty());

        mWifiRoamingConfigStore.removeRoamingMode(TEST_SSID, false);
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_DEFAULT_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(false).isEmpty());

        verify(mWifiConfigManager, times(2)).saveToStore(true);
    }

    @Test
    public void testSaveAndLoadFromStore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore).registerStoreData(storeDataCaptor.capture());
        assertNotNull(storeDataCaptor.getValue());

        XmlPullParser in = createRoamingPolicyTestXmlForParsing(
                TEST_SSID, TEST_ROAMING_MODE, true);
        storeDataCaptor.getValue().resetData();
        storeDataCaptor.getValue().deserializeData(in, in.getDepth(), -1, null);
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(true).get(TEST_SSID)
                == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(false).isEmpty());

        in = createRoamingPolicyTestXmlForParsing(
                TEST_SSID, TEST_ROAMING_MODE, false);
        storeDataCaptor.getValue().resetData();
        storeDataCaptor.getValue().deserializeData(in, in.getDepth(), -1, null);
        assertTrue(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID) == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(false).get(TEST_SSID)
                == TEST_ROAMING_MODE);
        assertTrue(mWifiRoamingConfigStore.getPerSsidRoamingModes(true).isEmpty());
    }

    private XmlPullParser createRoamingPolicyTestXmlForParsing(String ssid, Integer roamingMode,
            boolean isDeviceOwner)
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        Map<String, Integer> roamingPolicies = new ArrayMap<>();
        // Serialize
        roamingPolicies.put(ssid, roamingMode);
        final XmlSerializer out = new FastXmlSerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        out.setOutput(outputStream, StandardCharsets.UTF_8.name());
        XmlUtil.writeDocumentStart(out, "RoamingPolicies");
        XmlUtil.writeNextValue(out, isDeviceOwner ? "DeviceAdminPolicies" : "NonAdminPolicies",
                roamingPolicies);
        XmlUtil.writeDocumentEnd(out, "RoamingPolicies");

        // Start Deserializing
        final XmlPullParser in = Xml.newPullParser();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        in.setInput(inputStream, StandardCharsets.UTF_8.name());
        XmlUtil.gotoDocumentStart(in, "RoamingPolicies");
        return in;
    }
}
