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

package android.net.wifi;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.validateMockitoUsage;

import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.util.ScanResultUtil;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link android.net.wifi.WifiScanner}.
 */
@SmallTest
public class ScanResultTest {
    public static final String TEST_SSID = "\"test_ssid\"";
    public static final String TEST_SSID_NON_UTF_8 = "b9c8b8e8";
    public static final String TEST_BSSID = "04:ac:fe:45:34:10";
    public static final String TEST_CAPS = "CCMP";
    public static final int TEST_LEVEL = -56;
    public static final int TEST_FREQUENCY = 2412;
    public static final long TEST_TSF = 04660l;
    public static final @WifiAnnotations.WifiStandard int TEST_WIFI_STANDARD =
            ScanResult.WIFI_STANDARD_11AC;
    public static final String TEST_IFACE_NAME = "test_ifname";

    /**
     * Frequency to channel map. This include some frequencies used outside the US.
     * Representing it using a vector (instead of map) for simplification.
     */
    private static final int[] FREQUENCY_TO_CHANNEL_MAP = {
            2412, WifiScanner.WIFI_BAND_24_GHZ, 1,
            2417, WifiScanner.WIFI_BAND_24_GHZ, 2,
            2422, WifiScanner.WIFI_BAND_24_GHZ, 3,
            2427, WifiScanner.WIFI_BAND_24_GHZ, 4,
            2432, WifiScanner.WIFI_BAND_24_GHZ, 5,
            2437, WifiScanner.WIFI_BAND_24_GHZ, 6,
            2442, WifiScanner.WIFI_BAND_24_GHZ, 7,
            2447, WifiScanner.WIFI_BAND_24_GHZ, 8,
            2452, WifiScanner.WIFI_BAND_24_GHZ, 9,
            2457, WifiScanner.WIFI_BAND_24_GHZ, 10,
            2462, WifiScanner.WIFI_BAND_24_GHZ, 11,
            /* 12, 13 are only legitimate outside the US. */
            2467, WifiScanner.WIFI_BAND_24_GHZ, 12,
            2472, WifiScanner.WIFI_BAND_24_GHZ, 13,
            /* 14 is for Japan, DSSS and CCK only. */
            2484, WifiScanner.WIFI_BAND_24_GHZ, 14,
            /* 34 valid in Japan. */
            5170, WifiScanner.WIFI_BAND_5_GHZ, 34,
            5180, WifiScanner.WIFI_BAND_5_GHZ, 36,
            5190, WifiScanner.WIFI_BAND_5_GHZ, 38,
            5200, WifiScanner.WIFI_BAND_5_GHZ, 40,
            5210, WifiScanner.WIFI_BAND_5_GHZ, 42,
            5220, WifiScanner.WIFI_BAND_5_GHZ, 44,
            5230, WifiScanner.WIFI_BAND_5_GHZ, 46,
            5240, WifiScanner.WIFI_BAND_5_GHZ, 48,
            5260, WifiScanner.WIFI_BAND_5_GHZ, 52,
            5280, WifiScanner.WIFI_BAND_5_GHZ, 56,
            5300, WifiScanner.WIFI_BAND_5_GHZ, 60,
            5320, WifiScanner.WIFI_BAND_5_GHZ, 64,
            5500, WifiScanner.WIFI_BAND_5_GHZ, 100,
            5520, WifiScanner.WIFI_BAND_5_GHZ, 104,
            5540, WifiScanner.WIFI_BAND_5_GHZ, 108,
            5560, WifiScanner.WIFI_BAND_5_GHZ, 112,
            5580, WifiScanner.WIFI_BAND_5_GHZ, 116,
            /* 120, 124, 128 valid in Europe/Japan. */
            5600, WifiScanner.WIFI_BAND_5_GHZ, 120,
            5620, WifiScanner.WIFI_BAND_5_GHZ, 124,
            5640, WifiScanner.WIFI_BAND_5_GHZ, 128,
            /* 132+ valid in US. */
            5660, WifiScanner.WIFI_BAND_5_GHZ, 132,
            5680, WifiScanner.WIFI_BAND_5_GHZ, 136,
            5700, WifiScanner.WIFI_BAND_5_GHZ, 140,
            /* 144 is supported by a subset of WiFi chips. */
            5720, WifiScanner.WIFI_BAND_5_GHZ, 144,
            5745, WifiScanner.WIFI_BAND_5_GHZ, 149,
            5765, WifiScanner.WIFI_BAND_5_GHZ, 153,
            5785, WifiScanner.WIFI_BAND_5_GHZ, 157,
            5805, WifiScanner.WIFI_BAND_5_GHZ, 161,
            5825, WifiScanner.WIFI_BAND_5_GHZ, 165,
            5845, WifiScanner.WIFI_BAND_5_GHZ, 169,
            5865, WifiScanner.WIFI_BAND_5_GHZ, 173,
            /* Now some 6GHz channels */
            5955, WifiScanner.WIFI_BAND_6_GHZ, 1,
            5935, WifiScanner.WIFI_BAND_6_GHZ, 2,
            5970, WifiScanner.WIFI_BAND_6_GHZ, 4,
            6110, WifiScanner.WIFI_BAND_6_GHZ, 32
    };

    /**
     * Setup before tests.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Clean up after tests.
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Verify the logic that determines whether a frequency is PSC.
     */
    @Test
    public void testIs6GHzPsc() {
        int test2G = 2412;
        int test6GNonPsc = ScanResult.BAND_6_GHZ_PSC_START_MHZ
                + ScanResult.BAND_6_GHZ_PSC_STEP_SIZE_MHZ - 20;
        int test6GPsc = ScanResult.BAND_6_GHZ_PSC_START_MHZ
                + ScanResult.BAND_6_GHZ_PSC_STEP_SIZE_MHZ;
        assertFalse(ScanResult.is6GHzPsc(test2G));
        assertFalse(ScanResult.is6GHzPsc(test6GNonPsc));
        assertTrue(ScanResult.is6GHzPsc(test6GPsc));
    }

    /**
     * Verify parcel read/write for ScanResult.
     */
    @Test
    public void verifyScanResultParcelWithoutRadioChainInfo() throws Exception {
        ScanResult writeScanResult = createScanResult();
        ScanResult readScanResult = parcelReadWrite(writeScanResult);
        assertScanResultEquals(writeScanResult, readScanResult);
    }

    /**
     * Verify parcel read/write for ScanResult with non-UTF-8 SSID.
     */
    @Test
    public void verifyScanResultParcelWithNonUtf8Ssid() throws Exception {
        ScanResult writeScanResult = createScanResult();
        writeScanResult.setWifiSsid(WifiSsid.fromString(TEST_SSID_NON_UTF_8));
        ScanResult readScanResult = parcelReadWrite(writeScanResult);
        assertScanResultEquals(writeScanResult, readScanResult);
    }


    /**
     * Verify parcel read/write for ScanResult.
     */
    @Test
    public void verifyScanResultParcelWithZeroRadioChainInfo() throws Exception {
        ScanResult writeScanResult = createScanResult();
        writeScanResult.radioChainInfos = new ScanResult.RadioChainInfo[0];
        ScanResult readScanResult = parcelReadWrite(writeScanResult);
        assertNull(readScanResult.radioChainInfos);
    }

    /**
     * Verify parcel read/write for ScanResult.
     */
    @Test
    public void verifyScanResultParcelWithRadioChainInfo() throws Exception {
        ScanResult writeScanResult = createScanResult();
        writeScanResult.radioChainInfos = new ScanResult.RadioChainInfo[2];
        writeScanResult.radioChainInfos[0] = new ScanResult.RadioChainInfo();
        writeScanResult.radioChainInfos[0].id = 0;
        writeScanResult.radioChainInfos[0].level = -45;
        writeScanResult.radioChainInfos[1] = new ScanResult.RadioChainInfo();
        writeScanResult.radioChainInfos[1].id = 1;
        writeScanResult.radioChainInfos[1].level = -54;
        ScanResult readScanResult = parcelReadWrite(writeScanResult);
        assertScanResultEquals(writeScanResult, readScanResult);
    }

    /**
     * Verify copy constructor for ScanResult.
     */
    @Test
    public void verifyScanResultCopyWithoutRadioChainInfo() throws Exception {
        ScanResult scanResult = createScanResult();
        ScanResult copyScanResult = new ScanResult(scanResult);
        assertScanResultEquals(scanResult, copyScanResult);
    }

    /**
     * Verify copy constructor for ScanResult.
     */
    @Test
    public void verifyScanResultCopyWithRadioChainInfo() throws Exception {
        ScanResult scanResult = createScanResult();
        scanResult.radioChainInfos = new ScanResult.RadioChainInfo[2];
        scanResult.radioChainInfos[0] = new ScanResult.RadioChainInfo();
        scanResult.radioChainInfos[0].id = 0;
        scanResult.radioChainInfos[0].level = -45;
        scanResult.radioChainInfos[1] = new ScanResult.RadioChainInfo();
        scanResult.radioChainInfos[1].id = 1;
        scanResult.radioChainInfos[1].level = -54;
        ScanResult copyScanResult = new ScanResult(scanResult);
        assertScanResultEquals(scanResult, copyScanResult);
    }

    /**
     * Verify parcel read/write for ScanResult with Information Element
     */
    @Test
    public void verifyScanResultParcelWithInformationElement() throws Exception {
        ScanResult writeScanResult = createScanResult();
        writeScanResult.informationElements = new ScanResult.InformationElement[2];
        writeScanResult.informationElements[0] = new ScanResult.InformationElement();
        writeScanResult.informationElements[0].id = InformationElement.EID_HT_OPERATION;
        writeScanResult.informationElements[0].idExt = 0;
        writeScanResult.informationElements[0].bytes = new byte[]{0x11, 0x22, 0x33};
        writeScanResult.informationElements[1] = new ScanResult.InformationElement();
        writeScanResult.informationElements[1].id = InformationElement.EID_EXTENSION_PRESENT;
        writeScanResult.informationElements[1].idExt = InformationElement.EID_EXT_HE_OPERATION;
        writeScanResult.informationElements[1].bytes = new byte[]{0x44, 0x55, 0x66};
        ScanResult readScanResult = new ScanResult(writeScanResult);
        assertScanResultEquals(writeScanResult, readScanResult);
    }

    /**
     * Verify toString for ScanResult.
     */
    @Test
    public void verifyScanResultToStringWithoutRadioChainInfo() throws Exception {
        ScanResult scanResult = createScanResult();
        assertEquals("SSID: \"test_ssid\", BSSID: 04:ac:fe:45:34:10, capabilities: CCMP, "
                + "level: -56, frequency: 2412, timestamp: 2480, "
                + "distance: 0(cm), distanceSd: 0(cm), "
                + "passpoint: no, ChannelBandwidth: 0, centerFreq0: 0, centerFreq1: 0, "
                + "standard: 11ac, "
                + "80211mcResponder: is not supported, "
                + "80211azNtbResponder: is not supported, "
                + "TWT Responder: no, "
                + "Radio Chain Infos: null, interface name: test_ifname", scanResult.toString());
    }

    /**
     * Verify toString for ScanResult.
     */
    @Test
    public void verifyScanResultToStringWithRadioChainInfo() throws Exception {
        ScanResult scanResult = createScanResult();
        scanResult.radioChainInfos = new ScanResult.RadioChainInfo[2];
        scanResult.radioChainInfos[0] = new ScanResult.RadioChainInfo();
        scanResult.radioChainInfos[0].id = 0;
        scanResult.radioChainInfos[0].level = -45;
        scanResult.radioChainInfos[1] = new ScanResult.RadioChainInfo();
        scanResult.radioChainInfos[1].id = 1;
        scanResult.radioChainInfos[1].level = -54;
        assertEquals("SSID: \"test_ssid\", BSSID: 04:ac:fe:45:34:10, capabilities: CCMP, "
                + "level: -56, frequency: 2412, timestamp: 2480, distance: 0(cm), "
                + "distanceSd: 0(cm), "
                + "passpoint: no, ChannelBandwidth: 0, centerFreq0: 0, centerFreq1: 0, "
                + "standard: 11ac, "
                + "80211mcResponder: is not supported, "
                + "80211azNtbResponder: is not supported, "
                + "TWT Responder: no, "
                + "Radio Chain Infos: [RadioChainInfo: id=0, level=-45, "
                + "RadioChainInfo: id=1, level=-54], interface name: test_ifname",
                scanResult.toString());
    }

    /**
     * Verify toString for ScanResult with non-UTF-8 SSID.
     */
    @Test
    public void verifyScanResultToStringWithNonUtf8Ssid() throws Exception {
        ScanResult scanResult = createScanResult();
        scanResult.setWifiSsid(WifiSsid.fromString(TEST_SSID_NON_UTF_8));
        assertEquals("SSID: b9c8b8e8, BSSID: 04:ac:fe:45:34:10, capabilities: CCMP, "
                + "level: -56, frequency: 2412, timestamp: 2480, "
                + "distance: 0(cm), distanceSd: 0(cm), "
                + "passpoint: no, ChannelBandwidth: 0, centerFreq0: 0, centerFreq1: 0, "
                + "standard: 11ac, "
                + "80211mcResponder: is not supported, "
                + "80211azNtbResponder: is not supported, "
                + "TWT Responder: no, "
                + "Radio Chain Infos: null, interface name: test_ifname", scanResult.toString());
    }

    /**
     * verify frequency to channel conversion for all possible frequencies.
     */
    @Test
    public void convertFrequencyToChannel() throws Exception {
        for (int i = 0; i < FREQUENCY_TO_CHANNEL_MAP.length; i += 3) {
            assertEquals(FREQUENCY_TO_CHANNEL_MAP[i + 2],
                    ScanResult.convertFrequencyMhzToChannelIfSupported(
                    FREQUENCY_TO_CHANNEL_MAP[i]));
        }
    }

    /**
     * Verify frequency to channel conversion failed for an invalid frequency.
     */
    @Test
    public void convertFrequencyToChannelWithInvalidFreq() throws Exception {
        assertEquals(-1, ScanResult.convertFrequencyMhzToChannelIfSupported(8000));
    }

    /**
     * Verify that getSecurityTypes returns the types derived from the generated security params
     */
    @Test
    public void verifyGetSecurityTypesDerivedFromSecurityParams() {
        List<Integer> wifiConfigSecurityTypes = List.of(
                WifiConfiguration.SECURITY_TYPE_OPEN,
                WifiConfiguration.SECURITY_TYPE_WEP,
                WifiConfiguration.SECURITY_TYPE_PSK,
                WifiConfiguration.SECURITY_TYPE_EAP,
                WifiConfiguration.SECURITY_TYPE_SAE,
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
                WifiConfiguration.SECURITY_TYPE_OWE,
                WifiConfiguration.SECURITY_TYPE_WAPI_PSK,
                WifiConfiguration.SECURITY_TYPE_WAPI_CERT,
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
                WifiConfiguration.SECURITY_TYPE_OSEN,
                WifiConfiguration.SECURITY_TYPE_PASSPOINT_R1_R2,
                WifiConfiguration.SECURITY_TYPE_PASSPOINT_R3);
        List<SecurityParams> securityParamsList = wifiConfigSecurityTypes.stream()
                .map(SecurityParams::createSecurityParamsBySecurityType)
                .collect(Collectors.toList());
        List<Integer> wifiInfoSecurityTypes = wifiConfigSecurityTypes.stream()
                .map(WifiInfo::convertWifiConfigurationSecurityType)
                .collect(Collectors.toList());

        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(ScanResultUtil.class).startMocking();
        try {
            ScanResult scanResult = new ScanResult();
            scanResult.capabilities = "";
            doReturn(securityParamsList).when(
                    () -> ScanResultUtil.generateSecurityParamsListFromScanResult(scanResult));
            assertThat(scanResult.getSecurityTypes())
                    .asList().containsExactlyElementsIn(wifiInfoSecurityTypes);
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Write the provided {@link ScanResult} to a parcel and deserialize it.
     */
    private static ScanResult parcelReadWrite(ScanResult writeResult) throws Exception {
        Parcel parcel = Parcel.obtain();
        writeResult.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        return ScanResult.CREATOR.createFromParcel(parcel);
    }

    private static ScanResult createScanResult() {
        ScanResult result = new ScanResult();
        result.setWifiSsid(WifiSsid.fromString(TEST_SSID));
        result.BSSID = TEST_BSSID;
        result.capabilities = TEST_CAPS;
        result.level = TEST_LEVEL;
        result.frequency = TEST_FREQUENCY;
        result.timestamp = TEST_TSF;
        result.setWifiStandard(TEST_WIFI_STANDARD);
        result.ifaceName = TEST_IFACE_NAME;

        return result;
    }

    private static void assertScanResultEquals(ScanResult expected, ScanResult actual) {
        assertEquals(expected.SSID, actual.SSID);
        assertEquals(expected.getWifiSsid(), actual.getWifiSsid());
        assertEquals(expected.BSSID, actual.BSSID);
        assertEquals(expected.capabilities, actual.capabilities);
        assertEquals(expected.level, actual.level);
        assertEquals(expected.frequency, actual.frequency);
        assertEquals(expected.timestamp, actual.timestamp);
        assertEquals(expected.getWifiStandard(), actual.getWifiStandard());
        assertArrayEquals(expected.radioChainInfos, actual.radioChainInfos);
        assertArrayEquals(expected.informationElements, actual.informationElements);
    }

    /**
     * Test ScanResult.getBand() function.
     */
    @Test
    public void testScanResultGetBand() throws Exception {
        ScanResult scanResult = createScanResult();
        assertEquals(WifiScanner.WIFI_BAND_24_GHZ, scanResult.getBand());
    }

    /**
     * Test ScanResult.toBand() function.
     */
    @Test
    public void testScanResultToBand() throws Exception {
        assertEquals(WifiScanner.WIFI_BAND_24_GHZ, ScanResult.toBand(TEST_FREQUENCY));
    }

    /**
     * Test ScanResult.getBandFromOpClass() function.
     */
    @Test
    public void testScanResultGetBandFromOpCalss() throws Exception {
        assertEquals(WifiScanner.WIFI_BAND_24_GHZ, ScanResult.getBandFromOpClass(81, 11));
        assertEquals(WifiScanner.WIFI_BAND_UNSPECIFIED, ScanResult.getBandFromOpClass(81, 36));
        assertEquals(WifiScanner.WIFI_BAND_5_GHZ, ScanResult.getBandFromOpClass(120, 149));
        assertEquals(WifiScanner.WIFI_BAND_6_GHZ, ScanResult.getBandFromOpClass(131, 32));
    }
}
