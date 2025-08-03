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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.net.MacAddress;
import android.net.NetworkCapabilities;
import android.net.wifi.util.HexEncoding;
import android.os.Parcel;
import android.telephony.SubscriptionManager;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link android.net.wifi.WifiInfo}.
 */
@SmallTest
public class WifiInfoTest {
    private static final long TEST_TX_SUCCESS = 1;
    private static final long TEST_TX_RETRIES = 2;
    private static final long TEST_TX_BAD = 3;
    private static final long TEST_RX_SUCCESS = 4;
    private static final String TEST_PACKAGE_NAME = "com.test.example";
    private static final String TEST_FQDN = "test.com";
    private static final String TEST_PROVIDER_NAME = "test";
    private static final int TEST_WIFI_STANDARD = ScanResult.WIFI_STANDARD_11AC;
    private static final int TEST_MAX_SUPPORTED_TX_LINK_SPEED_MBPS = 866;
    private static final int TEST_MAX_SUPPORTED_RX_LINK_SPEED_MBPS = 1200;
    private static final String TEST_SSID = "Test123";
    private static final String TEST_BSSID = "12:12:12:12:12:12";
    private static final int TEST_RSSI = -60;
    private static final int TEST_NETWORK_ID = 5;
    private static final int TEST_NETWORK_ID2 = 6;
    private static final int TEST_SUB_ID = 1;
    private static final String TEST_NETWORK_KEY = "TestNetworkKey";
    private static final String AP_MLD_MAC_ADDRESS = "22:33:44:55:66:77";
    private static final String MLO_LINK_STA_MAC_ADDRESS = "12:34:56:78:9a:bc";
    private static final String MLO_LINK_AP_MAC_ADDRESS = "bc:9a:78:56:34:12";
    private static final int TEST_MLO_LINK_ID = 3;
    private static final int TEST_CHANNEL = 36;
    private static final int TEST_LINK_SPEED = 300;
    private static final List<OuiKeyedData> TEST_VENDOR_DATA =
            OuiKeyedDataUtil.createTestOuiKeyedDataList(5);

    private void addMloInfo(WifiInfo info) {
        info.setApMldMacAddress(MacAddress.fromString(AP_MLD_MAC_ADDRESS));
        info.enableApTidToLinkMappingNegotiationSupport(true);
        List<MloLink> links = new ArrayList<>();
        MloLink link = new MloLink();
        link.setStaMacAddress(MacAddress.fromString(MLO_LINK_STA_MAC_ADDRESS));
        link.setApMacAddress(MacAddress.fromString(MLO_LINK_AP_MAC_ADDRESS));
        links.add(link);
        info.setAffiliatedMloLinks(links);
        link.setRssi(TEST_RSSI);
        link.setLinkId(TEST_MLO_LINK_ID);
        link.setBand(WifiScanner.WIFI_BAND_5_GHZ);
        link.setChannel(TEST_CHANNEL);
        link.setRxLinkSpeedMbps(TEST_LINK_SPEED);
        link.setTxLinkSpeedMbps(TEST_LINK_SPEED);
        link.setState(MloLink.MLO_LINK_STATE_UNASSOCIATED);
    }

    private void assertMloNoRedaction(WifiInfo info) {
        assertTrue(info.isApTidToLinkMappingNegotiationSupported());
        assertNotNull(info.getApMldMacAddress());
        assertEquals(AP_MLD_MAC_ADDRESS, info.getApMldMacAddress().toString());
        List<MloLink> links = info.getAffiliatedMloLinks();
        assertEquals(1, links.size());
        for (MloLink link : links) {
            assertNotNull(link.getApMacAddress());
            assertEquals(MLO_LINK_AP_MAC_ADDRESS, link.getApMacAddress().toString());
            assertNotNull(link.getStaMacAddress());
            assertEquals(MLO_LINK_STA_MAC_ADDRESS, link.getStaMacAddress().toString());
            assertEquals(TEST_RSSI, link.getRssi());
            assertEquals(TEST_CHANNEL, link.getChannel());
            assertEquals(TEST_LINK_SPEED, link.getRxLinkSpeedMbps());
            assertEquals(TEST_LINK_SPEED, link.getTxLinkSpeedMbps());
            assertEquals(TEST_MLO_LINK_ID, link.getLinkId());
            assertEquals(WifiScanner.WIFI_BAND_5_GHZ, link.getBand());
            assertEquals(MloLink.MLO_LINK_STATE_UNASSOCIATED, link.getState());
        }
    }

    private void assertMloLocalMacRedaction(WifiInfo info) {
        assertNotNull(info.getApMldMacAddress());
        assertEquals(AP_MLD_MAC_ADDRESS, info.getApMldMacAddress().toString());
        List<MloLink> links = info.getAffiliatedMloLinks();
        assertEquals(1, links.size());
        for (MloLink link : links) {
            assertNotNull(link.getApMacAddress());
            assertEquals(MLO_LINK_AP_MAC_ADDRESS, link.getApMacAddress().toString());
            assertNull(link.getStaMacAddress());
        }
    }

    private void assertMloSensitiveLocationRedaction(WifiInfo info) {
        assertNull(info.getApMldMacAddress());
        List<MloLink> links = info.getAffiliatedMloLinks();
        assertEquals(1, links.size());
        for (MloLink link : links) {
            assertNull(link.getApMacAddress());
            assertNotNull(link.getStaMacAddress());
            assertEquals(MLO_LINK_STA_MAC_ADDRESS, link.getStaMacAddress().toString());
        }
    }

    private WifiInfo makeWifiInfoForRedactionTest(
            List<ScanResult.InformationElement> informationElements) {
        WifiInfo info = new WifiInfo();
        info.txSuccess = TEST_TX_SUCCESS;
        info.txRetries = TEST_TX_RETRIES;
        info.txBad = TEST_TX_BAD;
        info.rxSuccess = TEST_RX_SUCCESS;
        info.setSSID(WifiSsid.fromUtf8Text(TEST_SSID));
        info.setBSSID(TEST_BSSID);
        info.setNetworkId(TEST_NETWORK_ID);
        info.setTrusted(true);
        info.setOemPaid(true);
        info.setOemPrivate(true);
        info.setCarrierMerged(true);
        info.setOsuAp(true);
        info.setFQDN(TEST_FQDN);
        info.setProviderFriendlyName(TEST_PROVIDER_NAME);
        info.setRequestingPackageName(TEST_PACKAGE_NAME);
        info.setWifiStandard(TEST_WIFI_STANDARD);
        info.setMaxSupportedTxLinkSpeedMbps(TEST_MAX_SUPPORTED_TX_LINK_SPEED_MBPS);
        info.setMaxSupportedRxLinkSpeedMbps(TEST_MAX_SUPPORTED_RX_LINK_SPEED_MBPS);
        info.setSubscriptionId(TEST_SUB_ID);
        info.setInformationElements(informationElements);
        info.setIsPrimary(true);
        info.setMacAddress(TEST_BSSID);
        if (SdkLevel.isAtLeastT()) {
            addMloInfo(info);
        }
        if (SdkLevel.isAtLeastV()) {
            info.setVendorData(TEST_VENDOR_DATA);
        }

        return info;
    }

    private void assertNoRedaction(WifiInfo info,
            List<ScanResult.InformationElement> informationElements) {
        assertEquals(TEST_TX_SUCCESS, info.txSuccess);
        assertEquals(TEST_TX_RETRIES, info.txRetries);
        assertEquals(TEST_TX_BAD, info.txBad);
        assertEquals(TEST_RX_SUCCESS, info.rxSuccess);
        assertEquals("\"" + TEST_SSID + "\"", info.getSSID());
        assertEquals(TEST_BSSID, info.getBSSID());
        assertEquals(TEST_NETWORK_ID, info.getNetworkId());
        assertTrue(info.isTrusted());
        assertFalse((info.isRestricted()));
        assertTrue(info.isOsuAp());
        assertTrue(info.isPasspointAp());
        assertEquals(TEST_PACKAGE_NAME, info.getRequestingPackageName());
        assertEquals(TEST_FQDN, info.getPasspointFqdn());
        assertEquals(TEST_PROVIDER_NAME, info.getPasspointProviderFriendlyName());
        assertEquals(TEST_WIFI_STANDARD, info.getWifiStandard());
        assertEquals(TEST_MAX_SUPPORTED_TX_LINK_SPEED_MBPS, info.getMaxSupportedTxLinkSpeedMbps());
        assertEquals(TEST_MAX_SUPPORTED_RX_LINK_SPEED_MBPS, info.getMaxSupportedRxLinkSpeedMbps());
        assertEquals(TEST_BSSID, info.getMacAddress());
        assertEquals(2, info.getInformationElements().size());
        assertEquals(informationElements.get(0).id,
                info.getInformationElements().get(0).id);
        assertEquals(informationElements.get(0).idExt,
                info.getInformationElements().get(0).idExt);
        assertArrayEquals(informationElements.get(0).bytes,
                info.getInformationElements().get(0).bytes);
        assertEquals(informationElements.get(1).id,
                info.getInformationElements().get(1).id);
        assertEquals(informationElements.get(1).idExt,
                info.getInformationElements().get(1).idExt);
        assertArrayEquals(informationElements.get(1).bytes,
                info.getInformationElements().get(1).bytes);
        if (SdkLevel.isAtLeastS()) {
            assertTrue(info.isOemPaid());
            assertTrue(info.isOemPrivate());
            assertTrue(info.isCarrierMerged());
            assertEquals(TEST_SUB_ID, info.getSubscriptionId());
            assertTrue(info.isPrimary());
        }
        if (SdkLevel.isAtLeastT()) {
            assertMloNoRedaction(info);
        }
        if (SdkLevel.isAtLeastV()) {
            assertTrue(TEST_VENDOR_DATA.equals(info.getVendorData()));
        }
    }

    /**
     *  Verify redaction of WifiInfo with REDACT_NONE.
     */
    @Test
    public void testWifiInfoRedactNoRedactions() throws Exception {
        List<ScanResult.InformationElement> informationElements = generateIes();
        WifiInfo writeWifiInfo = makeWifiInfoForRedactionTest(informationElements);

        // Make a copy which allows parcelling of location sensitive data.
        WifiInfo redactedWifiInfo = writeWifiInfo.makeCopy(NetworkCapabilities.REDACT_NONE);

        Parcel parcel = Parcel.obtain();
        redactedWifiInfo.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        WifiInfo readWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);

        // Verify that redaction did not affect the original WifiInfo
        assertNoRedaction(writeWifiInfo, informationElements);

        assertNoRedaction(redactedWifiInfo, informationElements);
        assertNoRedaction(readWifiInfo, informationElements);

        if (SdkLevel.isAtLeastS()) {
            // equals() was only introduced in S.
            assertEquals(readWifiInfo, redactedWifiInfo);
        }
    }

    private void assertLocationSensitiveRedaction(WifiInfo info) {
        assertNotNull(info);
        assertEquals(TEST_TX_SUCCESS, info.txSuccess);
        assertEquals(TEST_TX_RETRIES, info.txRetries);
        assertEquals(TEST_TX_BAD, info.txBad);
        assertEquals(TEST_RX_SUCCESS, info.rxSuccess);
        assertEquals(WifiManager.UNKNOWN_SSID, info.getSSID());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, info.getBSSID());
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, info.getNetworkId());
        assertTrue(info.isTrusted());
        assertFalse(info.isRestricted());
        assertTrue(info.isOsuAp());
        assertFalse(info.isPasspointAp()); // fqdn & friendly name is masked.
        assertEquals(TEST_PACKAGE_NAME, info.getRequestingPackageName());
        assertNull(info.getPasspointFqdn());
        assertNull(info.getPasspointProviderFriendlyName());
        assertEquals(TEST_WIFI_STANDARD, info.getWifiStandard());
        assertEquals(TEST_MAX_SUPPORTED_TX_LINK_SPEED_MBPS, info.getMaxSupportedTxLinkSpeedMbps());
        assertEquals(TEST_MAX_SUPPORTED_RX_LINK_SPEED_MBPS, info.getMaxSupportedRxLinkSpeedMbps());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, info.getMacAddress());
        assertNull(info.getInformationElements());
        if (SdkLevel.isAtLeastS()) {
            assertTrue(info.isOemPaid());
            assertTrue(info.isOemPrivate());
            assertTrue(info.isCarrierMerged());
            assertEquals(TEST_SUB_ID, info.getSubscriptionId());
            assertTrue(info.isPrimary());
        }
        assertEquals(null, info.getNetworkKey());
        if (SdkLevel.isAtLeastT()) {
            assertMloSensitiveLocationRedaction(info);
        }
    }

    /**
     *  Verify redaction of WifiInfo with REDACT_FOR_ACCESS_FINE_LOCATION.
     */
    @Test
    public void testWifiInfoRedactLocationSensitiveInfo() throws Exception {
        List<ScanResult.InformationElement> informationElements = generateIes();
        WifiInfo writeWifiInfo = makeWifiInfoForRedactionTest(informationElements);

        WifiInfo redactedWifiInfo =
                writeWifiInfo.makeCopy(NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION);

        Parcel parcel = Parcel.obtain();
        redactedWifiInfo.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        WifiInfo readWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);

        // Verify that redaction did not affect the original WifiInfo
        assertNoRedaction(writeWifiInfo, informationElements);

        assertLocationSensitiveRedaction(redactedWifiInfo);
        assertLocationSensitiveRedaction(readWifiInfo);

        if (SdkLevel.isAtLeastS()) {
            // equals() was only introduced in S.
            assertEquals(redactedWifiInfo, readWifiInfo);
        }
    }

    private void assertLocalMacAddressInfoRedaction(WifiInfo info) {
        assertNotNull(info);
        assertEquals(TEST_TX_SUCCESS, info.txSuccess);
        assertEquals(TEST_TX_RETRIES, info.txRetries);
        assertEquals(TEST_TX_BAD, info.txBad);
        assertEquals(TEST_RX_SUCCESS, info.rxSuccess);
        assertEquals("\"" + TEST_SSID + "\"", info.getSSID());
        assertEquals(TEST_BSSID, info.getBSSID());
        assertEquals(TEST_NETWORK_ID, info.getNetworkId());
        assertTrue(info.isTrusted());
        assertFalse(info.isRestricted());
        assertTrue(info.isOsuAp());
        assertTrue(info.isPasspointAp());
        assertEquals(TEST_PACKAGE_NAME, info.getRequestingPackageName());

        assertEquals(TEST_FQDN, info.getPasspointFqdn());
        assertEquals(TEST_PROVIDER_NAME, info.getPasspointProviderFriendlyName());
        assertEquals(TEST_WIFI_STANDARD, info.getWifiStandard());
        assertEquals(TEST_MAX_SUPPORTED_TX_LINK_SPEED_MBPS, info.getMaxSupportedTxLinkSpeedMbps());
        assertEquals(TEST_MAX_SUPPORTED_RX_LINK_SPEED_MBPS, info.getMaxSupportedRxLinkSpeedMbps());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, info.getMacAddress());
        if (SdkLevel.isAtLeastS()) {
            assertTrue(info.isOemPaid());
            assertTrue(info.isOemPrivate());
            assertTrue(info.isCarrierMerged());
            assertEquals(TEST_SUB_ID, info.getSubscriptionId());
            assertTrue(info.isPrimary());
        }
        assertEquals(null, info.getNetworkKey());
        if (SdkLevel.isAtLeastT()) {
            assertMloLocalMacRedaction(info);
        }
    }

    /**
     *  Verify redaction of WifiInfo with REDACT_FOR_LOCAL_MAC_ADDRESS.
     */
    @Test
    public void testWifiInfoRedactLocalMacAddressInfo() throws Exception {
        List<ScanResult.InformationElement> informationElements = generateIes();
        WifiInfo writeWifiInfo = makeWifiInfoForRedactionTest(informationElements);

        WifiInfo redactedWifiInfo =
                writeWifiInfo.makeCopy(NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS);

        Parcel parcel = Parcel.obtain();
        redactedWifiInfo.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        WifiInfo readWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);

        // Verify that redaction did not affect the original WifiInfo
        assertNoRedaction(writeWifiInfo, informationElements);

        assertLocalMacAddressInfoRedaction(redactedWifiInfo);
        assertLocalMacAddressInfoRedaction(readWifiInfo);
        if (SdkLevel.isAtLeastS()) {
            // equals() was only introduced in S.
            assertEquals(redactedWifiInfo, readWifiInfo);
        }
    }

    private void assertIsPrimaryThrowsSecurityException(WifiInfo info) {
        try {
            // Should generate a security exception if caller does not have network settings
            // permission.
            info.isPrimary();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    /**
     *  Verify redaction of WifiInfo with REDACT_FOR_NETWORK_SETTINGS.
     */
    @Test
    public void testWifiInfoRedactNetworkSettingsInfo() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        WifiInfo writeWifiInfo = new WifiInfo();
        writeWifiInfo.setIsPrimary(true);
        writeWifiInfo.setRequestingPackageName(TEST_PACKAGE_NAME);
        writeWifiInfo.setIsPrimary(true);
        assertTrue(writeWifiInfo.isPrimary());

        WifiInfo redactedWifiInfo =
                writeWifiInfo.makeCopy(NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS);
        assertNull(redactedWifiInfo.getRequestingPackageName());
        assertThrows(SecurityException.class, () -> redactedWifiInfo.isPrimary());

        Parcel parcel = Parcel.obtain();
        redactedWifiInfo.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        WifiInfo readWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);

        assertNotNull(redactedWifiInfo);
        assertIsPrimaryThrowsSecurityException(redactedWifiInfo);
        assertNotNull(readWifiInfo);
        assertIsPrimaryThrowsSecurityException(readWifiInfo);

        if (SdkLevel.isAtLeastS()) {
            // equals() was only introduced in S.
            assertEquals(redactedWifiInfo, readWifiInfo);
        }
    }

    @Test
    public void testWifiInfoGetApplicableRedactions() throws Exception {
        long redactions = new WifiInfo().getApplicableRedactions();
        assertEquals(NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION
                | NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS
                | NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS, redactions);
    }

    private void assertLocationSensitiveAndLocalMacAddressRedaction(WifiInfo info) {
        assertNotNull(info);
        assertEquals(WifiManager.UNKNOWN_SSID, info.getSSID());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, info.getBSSID());
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, info.getNetworkId());
        assertNull(info.getPasspointFqdn());
        assertNull(info.getPasspointProviderFriendlyName());
        assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, info.getMacAddress());
        assertNull(info.getInformationElements());
        assertNull(info.getNetworkKey());
    }

    @Test
    public void testWifiInfoRedactLocationAndLocalMacAddressSensitiveInfo()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        List<ScanResult.InformationElement> informationElements = generateIes();
        WifiInfo writeWifiInfo = makeWifiInfoForRedactionTest(informationElements);

        WifiInfo redactedWifiInfo =
                writeWifiInfo.makeCopy(NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION
                        | NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS);

        Parcel parcel = Parcel.obtain();
        redactedWifiInfo.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        WifiInfo readWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);

        assertLocationSensitiveAndLocalMacAddressRedaction(redactedWifiInfo);
        assertLocationSensitiveAndLocalMacAddressRedaction(readWifiInfo);

        if (SdkLevel.isAtLeastS()) {
            // equals() was only introduced in S.
            assertEquals(redactedWifiInfo, readWifiInfo);
        }
    }

    /**
     *  Verify parcel write/read with null information elements.
     */
    @Test
    public void testWifiInfoParcelWriteReadWithNullInfoElements() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        WifiInfo writeWifiInfo = new WifiInfo();
        writeWifiInfo.setInformationElements(null);

        // Make a copy which allows parcelling of location sensitive data.
        WifiInfo writeWifiInfoCopy = writeWifiInfo.makeCopy(NetworkCapabilities.REDACT_NONE);

        Parcel parcel = Parcel.obtain();
        writeWifiInfoCopy.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        WifiInfo readWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);
        assertNull(readWifiInfo.getInformationElements());
    }

    /**
     *  Verify parcel write/read with empty information elements.
     */
    @Test
    public void testWifiInfoParcelWriteReadWithEmptyInfoElements() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        WifiInfo writeWifiInfo = new WifiInfo();
        writeWifiInfo.setInformationElements(new ArrayList<>());

        // Make a copy which allows parcelling of location sensitive data.
        WifiInfo writeWifiInfoCopy = writeWifiInfo.makeCopy(NetworkCapabilities.REDACT_NONE);

        Parcel parcel = Parcel.obtain();
        writeWifiInfoCopy.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        WifiInfo readWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);
        assertTrue(readWifiInfo.getInformationElements().isEmpty());
    }

    @Test
    public void testWifiInfoCopyConstructor() throws Exception {
        WifiInfo writeWifiInfo = new WifiInfo();
        writeWifiInfo.txSuccess = TEST_TX_SUCCESS;
        writeWifiInfo.txRetries = TEST_TX_RETRIES;
        writeWifiInfo.txBad = TEST_TX_BAD;
        writeWifiInfo.rxSuccess = TEST_RX_SUCCESS;
        writeWifiInfo.setTrusted(true);
        writeWifiInfo.setOemPaid(true);
        writeWifiInfo.setOemPrivate(true);
        writeWifiInfo.setCarrierMerged(true);
        writeWifiInfo.setOsuAp(true);
        writeWifiInfo.setFQDN(TEST_FQDN);
        writeWifiInfo.setProviderFriendlyName(TEST_PROVIDER_NAME);
        writeWifiInfo.setRequestingPackageName(TEST_PACKAGE_NAME);
        writeWifiInfo.setWifiStandard(TEST_WIFI_STANDARD);
        writeWifiInfo.setMaxSupportedTxLinkSpeedMbps(TEST_MAX_SUPPORTED_TX_LINK_SPEED_MBPS);
        writeWifiInfo.setMaxSupportedRxLinkSpeedMbps(TEST_MAX_SUPPORTED_RX_LINK_SPEED_MBPS);
        writeWifiInfo.setSubscriptionId(TEST_SUB_ID);
        writeWifiInfo.setIsPrimary(true);
        writeWifiInfo.setRestricted(true);
        writeWifiInfo.enableApTidToLinkMappingNegotiationSupport(true);
        if (SdkLevel.isAtLeastV()) {
            writeWifiInfo.setVendorData(TEST_VENDOR_DATA);
        }

        WifiInfo readWifiInfo = new WifiInfo(writeWifiInfo);

        assertEquals(TEST_TX_SUCCESS, readWifiInfo.txSuccess);
        assertEquals(TEST_TX_RETRIES, readWifiInfo.txRetries);
        assertEquals(TEST_TX_BAD, readWifiInfo.txBad);
        assertEquals(TEST_RX_SUCCESS, readWifiInfo.rxSuccess);
        assertTrue(readWifiInfo.isTrusted());
        assertTrue(readWifiInfo.isOsuAp());
        assertTrue(readWifiInfo.isPasspointAp());
        assertEquals(TEST_PACKAGE_NAME, readWifiInfo.getRequestingPackageName());
        assertEquals(TEST_FQDN, readWifiInfo.getPasspointFqdn());
        assertEquals(TEST_PROVIDER_NAME, readWifiInfo.getPasspointProviderFriendlyName());
        assertEquals(TEST_WIFI_STANDARD, readWifiInfo.getWifiStandard());
        assertEquals(TEST_MAX_SUPPORTED_TX_LINK_SPEED_MBPS,
                readWifiInfo.getMaxSupportedTxLinkSpeedMbps());
        assertEquals(TEST_MAX_SUPPORTED_RX_LINK_SPEED_MBPS,
                readWifiInfo.getMaxSupportedRxLinkSpeedMbps());
        assertTrue(readWifiInfo.isRestricted());
        if (SdkLevel.isAtLeastS()) {
            assertTrue(readWifiInfo.isOemPaid());
            assertTrue(readWifiInfo.isOemPrivate());
            assertTrue(readWifiInfo.isCarrierMerged());
            assertEquals(TEST_SUB_ID, readWifiInfo.getSubscriptionId());
            assertTrue(readWifiInfo.isPrimary());
        }
        assertTrue(readWifiInfo.isApTidToLinkMappingNegotiationSupported());
        if (SdkLevel.isAtLeastV()) {
            assertTrue(TEST_VENDOR_DATA.equals(readWifiInfo.getVendorData()));
        }
    }

    /**
     *  Verify values after reset()
     */
    @Test
    public void testWifiInfoResetValue() throws Exception {
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.reset();
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, wifiInfo.getMaxSupportedTxLinkSpeedMbps());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, wifiInfo.getMaxSupportedRxLinkSpeedMbps());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, wifiInfo.getTxLinkSpeedMbps());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, wifiInfo.getRxLinkSpeedMbps());
        assertEquals(WifiInfo.INVALID_RSSI, wifiInfo.getRssi());
        assertEquals(WifiManager.UNKNOWN_SSID, wifiInfo.getSSID());
        assertNull(wifiInfo.getBSSID());
        assertEquals(-1, wifiInfo.getNetworkId());
        if (SdkLevel.isAtLeastS()) {
            assertFalse(wifiInfo.isOemPaid());
            assertFalse(wifiInfo.isOemPrivate());
            assertFalse(wifiInfo.isCarrierMerged());
            assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID, wifiInfo.getSubscriptionId());
            assertFalse(wifiInfo.isPrimary());
        }
        assertNull(wifiInfo.getNetworkKey());
        assertEquals(MloLink.INVALID_MLO_LINK_ID, wifiInfo.getApMloLinkId());
        assertNull(wifiInfo.getApMldMacAddress());
        assertEquals(0, wifiInfo.getAffiliatedMloLinks().size());
        assertFalse(wifiInfo.isApTidToLinkMappingNegotiationSupported());
        if (SdkLevel.isAtLeastV()) {
            assertNotNull(wifiInfo.getVendorData());
            assertTrue(wifiInfo.getVendorData().isEmpty());
        }
    }

    /**
     * Test that the WifiInfo Builder returns the same values that was set, and that
     * calling build multiple times returns different instances.
     */
    @Test
    public void testWifiInfoBuilder() throws Exception {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);

        WifiInfo info1 = builder.build();

        assertEquals("\"" + TEST_SSID + "\"", info1.getSSID());
        assertEquals(TEST_BSSID, info1.getBSSID());
        assertEquals(TEST_RSSI, info1.getRssi());
        assertEquals(TEST_NETWORK_ID, info1.getNetworkId());

        WifiInfo info2 = builder
                .setNetworkId(TEST_NETWORK_ID2)
                .build();

        // different instances
        assertNotSame(info1, info2);

        // assert that info1 didn't change
        assertEquals("\"" + TEST_SSID + "\"", info1.getSSID());
        assertEquals(TEST_BSSID, info1.getBSSID());
        assertEquals(TEST_RSSI, info1.getRssi());
        assertEquals(TEST_NETWORK_ID, info1.getNetworkId());

        // assert that info2 changed
        assertEquals("\"" + TEST_SSID + "\"", info2.getSSID());
        assertEquals(TEST_BSSID, info2.getBSSID());
        assertEquals(TEST_RSSI, info2.getRssi());
        assertEquals(TEST_NETWORK_ID2, info2.getNetworkId());
    }

    @Test
    public void testSetSsid() throws Exception {
        WifiInfo.Builder builder = new WifiInfo.Builder();

        // Null
        assertEquals(WifiManager.UNKNOWN_SSID, builder.build().getSSID());

        // Empty
        builder.setSsid(new byte[0]);
        assertEquals(WifiManager.UNKNOWN_SSID, builder.build().getSSID());

        // UTF-8
        builder.setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8));
        assertEquals("\"" + TEST_SSID + "\"", builder.build().getSSID());

        // Non-UTF-8
        byte[] gbkBytes = "服務集識別碼".getBytes(Charset.forName("GBK"));
        builder.setSsid(gbkBytes);
        assertEquals(HexEncoding.encodeToString(gbkBytes, false), builder.build().getSSID());
    }

    @Test
    public void testWifiInfoEquals() throws Exception {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);

        WifiInfo info1 = builder.build();
        WifiInfo info2 = builder.build();
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1, info2);
        } else {
            // On R devices, reference equality.
            assertNotEquals(info1, info2);
        }

        info1.setSubscriptionId(TEST_SUB_ID);
        assertNotEquals(info1, info2);

        info2.setSubscriptionId(TEST_SUB_ID);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1, info2);
        } else {
            // On R devices, reference equality.
            assertNotEquals(info1, info2);
        }

        info1.setSSID(WifiSsid.fromBytes(null));
        assertNotEquals(info1, info2);

        info2.setSSID(WifiSsid.fromBytes(null));
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1, info2);
        } else {
            // On R devices, reference equality.
            assertNotEquals(info1, info2);
        }
    }

    @Test
    public void testWifiInfoEqualsWithInfoElements() throws Exception {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);

        WifiInfo info1 = builder.build();
        WifiInfo info2 = builder.build();
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1, info2);
        } else {
            // On R devices, reference equality.
            assertNotEquals(info1, info2);
        }

        info1.setInformationElements(generateIes());
        info2.setInformationElements(generateIes());

        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1, info2);
        } else {
            // On R devices, reference equality.
            assertNotEquals(info1, info2);
        }
    }

    @Test
    public void testWifiInfoHashcode() throws Exception {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);

        WifiInfo info1 = builder.build();
        WifiInfo info2 = builder.build();
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1.hashCode(), info2.hashCode());
        } else {
            // On R devices, system generated hashcode.
            assertNotEquals(info1.hashCode(), info2.hashCode());
        }

        info1.setSubscriptionId(TEST_SUB_ID);
        assertNotEquals(info1.hashCode(), info2.hashCode());

        info2.setSubscriptionId(TEST_SUB_ID);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1.hashCode(), info2.hashCode());
        } else {
            // On R devices, system generated hashcode.
            assertNotEquals(info1.hashCode(), info2.hashCode());
        }

        info1.setSSID(WifiSsid.fromBytes(null));
        assertNotEquals(info1.hashCode(), info2.hashCode());

        info2.setSSID(WifiSsid.fromBytes(null));
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info1.hashCode(), info2.hashCode());
        } else {
            // On R devices, system generated hashcode.
            assertNotEquals(info1.hashCode(), info2.hashCode());
        }
    }

    @Test
    public void testWifiInfoCurrentSecurityType() throws Exception {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID)
                .setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_SAE);

        WifiInfo info = new WifiInfo();
        assertEquals(WifiInfo.SECURITY_TYPE_UNKNOWN, info.getCurrentSecurityType());

        info = builder.build();
        assertEquals(WifiInfo.SECURITY_TYPE_SAE, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_OPEN).build();
        assertEquals(WifiInfo.SECURITY_TYPE_OPEN, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_WEP).build();
        assertEquals(WifiInfo.SECURITY_TYPE_WEP, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PSK).build();
        assertEquals(WifiInfo.SECURITY_TYPE_PSK, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_EAP).build();
        assertEquals(WifiInfo.SECURITY_TYPE_EAP, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_OWE).build();
        assertEquals(WifiInfo.SECURITY_TYPE_OWE, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_PSK).build();
        assertEquals(WifiInfo.SECURITY_TYPE_WAPI_PSK, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_CERT).build();
        assertEquals(WifiInfo.SECURITY_TYPE_WAPI_CERT, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE).build();
        assertEquals(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT).build();
        assertEquals(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
                info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(
                WifiConfiguration.SECURITY_TYPE_PASSPOINT_R1_R2).build();
        assertEquals(WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2, info.getCurrentSecurityType());

        info = builder.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PASSPOINT_R3).build();
        assertEquals(WifiInfo.SECURITY_TYPE_PASSPOINT_R3, info.getCurrentSecurityType());

        info.clearCurrentSecurityType();
        assertEquals(WifiInfo.SECURITY_TYPE_UNKNOWN, info.getCurrentSecurityType());
    }

    private static List<ScanResult.InformationElement> generateIes() {
        List<ScanResult.InformationElement> informationElements = new ArrayList<>();
        ScanResult.InformationElement informationElement = new ScanResult.InformationElement();
        informationElement.id = ScanResult.InformationElement.EID_HT_OPERATION;
        informationElement.idExt = 0;
        informationElement.bytes = new byte[]{0x11, 0x22, 0x33};
        informationElements.add(informationElement);

        informationElement = new ScanResult.InformationElement();
        informationElement.id = ScanResult.InformationElement.EID_EXTENSION_PRESENT;
        informationElement.idExt = ScanResult.InformationElement.EID_EXT_HE_OPERATION;
        informationElement.bytes = new byte[]{0x44, 0x55, 0x66};
        informationElements.add(informationElement);

        return informationElements;
    }

    @Test
    public void testMloLink() throws Exception {
        // Create an MLO link and set parameters.
        MloLink link1 = new MloLink();
        link1.setStaMacAddress(MacAddress.fromString(MLO_LINK_STA_MAC_ADDRESS));
        link1.setApMacAddress(MacAddress.fromString(MLO_LINK_AP_MAC_ADDRESS));
        link1.setRssi(TEST_RSSI);
        link1.setLinkId(TEST_MLO_LINK_ID);
        link1.setBand(WifiScanner.WIFI_BAND_5_GHZ);
        link1.setChannel(TEST_CHANNEL);
        link1.setRxLinkSpeedMbps(TEST_LINK_SPEED);
        link1.setTxLinkSpeedMbps(TEST_LINK_SPEED);
        link1.setState(MloLink.MLO_LINK_STATE_UNASSOCIATED);
        link1.setLostTxPacketsPerSecond(10);
        link1.setRetriedTxPacketsRate(20);
        link1.setSuccessfulTxPacketsPerSecond(30);
        link1.setSuccessfulRxPacketsPerSecond(40);

        // Make sure all parameters are set.
        assertNotNull(link1.getApMacAddress());
        assertEquals(MLO_LINK_AP_MAC_ADDRESS, link1.getApMacAddress().toString());
        assertNotNull(link1.getStaMacAddress());
        assertEquals(MLO_LINK_STA_MAC_ADDRESS, link1.getStaMacAddress().toString());
        assertEquals(TEST_RSSI, link1.getRssi());
        assertEquals(TEST_CHANNEL, link1.getChannel());
        assertEquals(TEST_LINK_SPEED, link1.getRxLinkSpeedMbps());
        assertEquals(TEST_LINK_SPEED, link1.getTxLinkSpeedMbps());
        assertEquals(TEST_MLO_LINK_ID, link1.getLinkId());
        assertEquals(WifiScanner.WIFI_BAND_5_GHZ, link1.getBand());
        assertEquals(MloLink.MLO_LINK_STATE_UNASSOCIATED, link1.getState());
        assertEquals(10, link1.getLostTxPacketsPerSecond(), 0);
        assertEquals(20, link1.getRetriedTxPacketsPerSecond(), 0);
        assertEquals(30, link1.getSuccessfulTxPacketsPerSecond(), 0);
        assertEquals(40, link1.getSuccessfulRxPacketsPerSecond(), 0);
        // Check default values
        assertEquals(0, link1.txBad);
        assertEquals(0, link1.txRetries);
        assertEquals(0, link1.txSuccess);
        assertEquals(0, link1.rxSuccess);

        // Test RSSI range.
        link1.setRssi(WifiInfo.INVALID_RSSI - 1);
        assertEquals(WifiInfo.INVALID_RSSI, link1.getRssi());
        link1.setRssi(WifiInfo.MAX_RSSI + 1);
        assertEquals(WifiInfo.MAX_RSSI, link1.getRssi());

        // Copy link
        MloLink link2 = new MloLink(link1, 0);

        // Check links are equal.
        assertTrue(link1.equals(link2));
        assertEquals(link2.hashCode(), link1.hashCode());
        assertEquals(link2.toString(), link1.toString());

        // Change one parameter and check the links are not equal.
        link1.setState(MloLink.MLO_LINK_STATE_INVALID);
        assertFalse(link1.equals(link2));
        assertNotEquals(link2.hashCode(), link1.hashCode());
        assertNotEquals(link2.toString(), link1.toString());

        // Validate states.
        assertTrue(MloLink.isValidState(MloLink.MLO_LINK_STATE_INVALID));
        assertTrue(MloLink.isValidState(MloLink.MLO_LINK_STATE_UNASSOCIATED));
        assertTrue(MloLink.isValidState(MloLink.MLO_LINK_STATE_ACTIVE));
        assertTrue(MloLink.isValidState(MloLink.MLO_LINK_STATE_IDLE));
        assertFalse(MloLink.isValidState(MloLink.MLO_LINK_STATE_INVALID - 1));
    }
}
