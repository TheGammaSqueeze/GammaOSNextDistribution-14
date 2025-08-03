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
package com.android.server.wifi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.hardware.wifi.common.OuiKeyedData;
import android.hardware.wifi.supplicant.GroupCipherMask;
import android.hardware.wifi.supplicant.GsmRand;
import android.hardware.wifi.supplicant.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.ISupplicantStaNetworkCallback;
import android.hardware.wifi.supplicant.KeyMgmtMask;
import android.hardware.wifi.supplicant.NetworkRequestEapSimGsmAuthParams;
import android.hardware.wifi.supplicant.NetworkRequestEapSimUmtsAuthParams;
import android.hardware.wifi.supplicant.NetworkResponseEapSimGsmAuthParams;
import android.hardware.wifi.supplicant.NetworkResponseEapSimUmtsAuthParams;
import android.hardware.wifi.supplicant.PairwiseCipherMask;
import android.hardware.wifi.supplicant.ProtoMask;
import android.hardware.wifi.supplicant.SaeH2eMode;
import android.hardware.wifi.supplicant.SupplicantStatusCode;
import android.hardware.wifi.supplicant.TlsVersion;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Unit tests for SupplicantStaNetworkHalAidlImpl
 */
@SmallTest
public class SupplicantStaNetworkHalAidlImplTest extends WifiBaseTest {
    private static final String IFACE_NAME = "wlan0";
    private static final Map<String, String> NETWORK_EXTRAS_VALUES = new HashMap<>();
    static {
        NETWORK_EXTRAS_VALUES.put("key1", "value1");
        NETWORK_EXTRAS_VALUES.put("key2", "value2");
    }
    private static final String NETWORK_EXTRAS_SERIALIZED =
            "%7B%22key1%22%3A%22value1%22%2C%22key2%22%3A%22value2%22%7D";
    private static final String ANONYMOUS_IDENTITY = "aaa@bbb.cc.ddd";
    private static final String TEST_DECORATED_IDENTITY_PREFIX = "androidwifi.dev!";
    private static final byte[] TEST_SELECTED_RCOI_BYTE_ARRAY =
            {(byte) 0xaa, (byte) 0xbb, (byte) 0xcc};
    private static final long TEST_SELECTED_RCOI_LONG = 0xaabbccL;

    private SupplicantStaNetworkHalAidlImpl mSupplicantNetwork;
    @Mock private ISupplicantStaNetwork mISupplicantStaNetworkMock;
    @Mock private Context mContext;
    @Mock private WifiMonitor mWifiMonitor;
    @Mock private WifiGlobals mWifiGlobals;
    private long mAdvanceKeyMgmtFeatures = 0;
    private long mWpaDriverFeatures = 0;

    private SupplicantNetworkVariables mSupplicantVariables;
    private MockResources mResources;
    private ISupplicantStaNetworkCallback mISupplicantStaNetworkCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSupplicantVariables = new SupplicantNetworkVariables();
        setupISupplicantNetworkMock();

        mResources = new MockResources();
        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);
        when(mWifiGlobals.isWpaPersonalDeprecated()).thenReturn(false);

        mAdvanceKeyMgmtFeatures |= WifiManager.WIFI_FEATURE_WPA3_SUITE_B;
        mSupplicantNetwork = new SupplicantStaNetworkHalAidlImpl(1,
                mISupplicantStaNetworkMock, IFACE_NAME, mContext, mWifiMonitor,
                mWifiGlobals, mAdvanceKeyMgmtFeatures, mWpaDriverFeatures);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testOweNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOweNetwork();
        config.updateIdentifier = "46";
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testOpenNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        config.updateIdentifier = "45";
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant with SAE password.
     */
    @Test
    public void testSaePasswordNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        testWifiConfigurationSaveLoad(config);
        verify(mISupplicantStaNetworkMock).setSaePassword(any(String.class));
        verify(mISupplicantStaNetworkMock, never()).getSaePassword();
        verify(mISupplicantStaNetworkMock, never()).setPsk(any(byte[].class));
        verify(mISupplicantStaNetworkMock, never()).getPsk();
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant with psk passphrase.
     */
    @Test
    public void testPskPassphraseNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();

        // Set the new defaults
        testWifiConfigurationSaveLoad(config);
        verify(mISupplicantStaNetworkMock).setPskPassphrase(anyString());
        verify(mISupplicantStaNetworkMock).getPskPassphrase();
        verify(mISupplicantStaNetworkMock, never()).setPsk(any(byte[].class));
        verify(mISupplicantStaNetworkMock, never()).getPsk();
        verify(mISupplicantStaNetworkMock)
                .setPairwiseCipher(PairwiseCipherMask.TKIP
                        | PairwiseCipherMask.CCMP);
        verify(mISupplicantStaNetworkMock)
                .setGroupCipher(GroupCipherMask.WEP40
                        | GroupCipherMask.WEP104
                        | GroupCipherMask.TKIP
                        | GroupCipherMask.CCMP);
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant with raw psk.
     */
    @Test
    public void testPskNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.preSharedKey = "945ef00c463c2a7c2496376b13263d1531366b46377179a4b17b393687450779";
        testWifiConfigurationSaveLoad(config);
        verify(mISupplicantStaNetworkMock).setPsk(any(byte[].class));
        verify(mISupplicantStaNetworkMock).getPsk();
        verify(mISupplicantStaNetworkMock, never()).setPskPassphrase(anyString());
        verify(mISupplicantStaNetworkMock).getPskPassphrase();
        verify(mISupplicantStaNetworkMock).setProto(ProtoMask.RSN | ProtoMask.WPA);
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant with raw psk - WPA
     * deprecated.
     */
    @Test
    public void testPskNetworkWifiConfigurationSaveLoadWpaDeprecated() throws Exception {
        when(mWifiGlobals.isWpaPersonalDeprecated()).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.preSharedKey = "945ef00c463c2a7c2496376b13263d1531366b46377179a4b17b393687450779";
        testWifiConfigurationSaveLoad(config);
        verify(mISupplicantStaNetworkMock).setPsk(any(byte[].class));
        verify(mISupplicantStaNetworkMock).getPsk();
        verify(mISupplicantStaNetworkMock, never()).setPskPassphrase(anyString());
        verify(mISupplicantStaNetworkMock).getPskPassphrase();
        verify(mISupplicantStaNetworkMock).setProto(ProtoMask.RSN);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant removes enclosing quotes of psk
     * passphrase
     */
    @Test
    public void testPskNetworkWifiConfigurationSaveRemovesPskQuotes() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.preSharedKey = "\"quoted_psd\"";
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        assertEquals(mSupplicantVariables.pskPassphrase,
                NativeUtil.removeEnclosingQuotes(config.preSharedKey));
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testWepNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createWepHiddenNetwork();
        config.BSSID = " *NOT USED* "; // we want the other bssid!
        config.getNetworkSelectionStatus().setNetworkSelectionBSSID("34:45:19:09:45:66");
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testEapPeapGtcNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig =
                WifiConfigurationTestUtil.createPEAPWifiEnterpriseConfigWithGTCPhase2();
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testEapTlsNoneNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig =
                WifiConfigurationTestUtil.createTLSWifiEnterpriseConfigWithNonePhase2();
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testEapTlsNoneClientCertNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig =
                WifiConfigurationTestUtil.createTLSWifiEnterpriseConfigWithNonePhase2();
        config.enterpriseConfig.setClientCertificateAlias("test_alias");
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testEapTlsNoneClientCertNetworkWithOcspWifiConfigurationSaveLoad()
            throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig =
                WifiConfigurationTestUtil.createTLSWifiEnterpriseConfigWithNonePhase2();
        config.enterpriseConfig.setClientCertificateAlias("test_alias");
        config.enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS);
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testEapTlsAkaNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig =
                WifiConfigurationTestUtil.createTLSWifiEnterpriseConfigWithAkaPhase2();
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant with Suite-B-192
     */
    @Test
    public void testEapSuiteBRsaNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapSuiteBNetwork();
        config.enableSuiteBCiphers(false, true);

        testWifiConfigurationSaveLoad(config);
        verify(mISupplicantStaNetworkMock, never()).enableSuiteBEapOpenSslCiphers();
        verify(mISupplicantStaNetworkMock).enableTlsSuiteBEapPhase1Param(anyBoolean());
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant with Suite-B-192
     */
    @Test
    public void testEapSuiteBEcdsaNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapSuiteBNetwork();
        config.enableSuiteBCiphers(true, false);

        testWifiConfigurationSaveLoad(config);
        verify(mISupplicantStaNetworkMock).enableSuiteBEapOpenSslCiphers();
        verify(mISupplicantStaNetworkMock, never())
                .enableTlsSuiteBEapPhase1Param(anyBoolean());
    }

    /**
     * Tests the saving/loading of WifiConfiguration with FILS AKM to wpa_supplicant.
     */
    @Test
    public void testTLSWifiEnterpriseConfigWithFilsEapErp() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig =
                WifiConfigurationTestUtil.createTLSWifiEnterpriseConfigWithNonePhase2();
        config.enableFils(true, false);
        config.enterpriseConfig.setFieldValue(WifiEnterpriseConfig.EAP_ERP, "1");
        testWifiConfigurationSaveLoad(config);
        // Check the supplicant variables to ensure that we have added the FILS AKM.
        assertTrue((mSupplicantVariables.keyMgmtMask & KeyMgmtMask.FILS_SHA256)
                == KeyMgmtMask.FILS_SHA256);
        verify(mISupplicantStaNetworkMock).setEapErp(eq(true));
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testWapiPskNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createWapiPskNetwork();
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testWapiPskHexNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createWapiPskNetwork();
        config.preSharedKey =
                "1234567890abcdef0"
                        + "1234567890abcdef0";
        // WAPI should accept a hex bytes whose length is not exact 32.
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the saving of WifiConfiguration to wpa_supplicant.
     */
    @Test
    public void testWapiCertNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createWapiCertNetwork();
        testWifiConfigurationSaveLoad(config);
    }

    /**
     * Tests the failure to save ssid.
     */
    @Test
    public void testSsidSaveFailure() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createWepHiddenNetwork();
        // Assume that the default params are used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());

        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantStaNetworkMock).setSsid(any(byte[].class));
        assertFalse(mSupplicantNetwork.saveWifiConfiguration(config));
    }

    /**
     * Tests the failure to save invalid bssid (less than 6 bytes in the
     * {@link WifiConfiguration#BSSID} being saved).
     */
    @Test
    public void testInvalidBssidSaveFailure() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createWepHiddenNetwork();
        config.getNetworkSelectionStatus().setNetworkSelectionBSSID("45:34:23:12");
        // Assume that the default params are used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        try {
            assertFalse(mSupplicantNetwork.saveWifiConfiguration(config));
        } catch (IllegalArgumentException e) {
            // Expect exception to be thrown
            return;
        }
        assertTrue(false);
    }

    /**
     * Tests the parsing of GSM auth response parameters.
     */
    @Test
    public void testSendNetworkEapSimGsmAuthResponseWith2KcSresPair() throws Exception {
        final byte[] kc = new byte[]{0x45, 0x45, 0x32, 0x34, 0x45, 0x10, 0x34, 0x12};
        final byte[] sres = new byte[]{0x12, 0x10, 0x32, 0x23};

        // Send 2 kc/sres pairs for this request.
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(kc)
                + ":" + NativeUtil.hexStringFromByteArray(sres)
                + ":" + NativeUtil.hexStringFromByteArray(kc)
                + ":" + NativeUtil.hexStringFromByteArray(sres);

        ArgumentCaptor<NetworkResponseEapSimGsmAuthParams[]> paramCaptor =
                ArgumentCaptor.forClass(NetworkResponseEapSimGsmAuthParams[].class);
        doNothing().when(mISupplicantStaNetworkMock).sendNetworkEapSimGsmAuthResponse(
                paramCaptor.capture());
        assertTrue(mSupplicantNetwork.sendNetworkEapSimGsmAuthResponse(paramsStr));

        NetworkResponseEapSimGsmAuthParams[] captured = paramCaptor.getValue();
        assertEquals(2, captured.length);
        assertArrayEquals(kc, captured[0].kc);
        assertArrayEquals(sres, captured[0].sres);
        assertArrayEquals(kc, captured[1].kc);
        assertArrayEquals(sres, captured[1].sres);
    }

    /**
     * Tests the parsing of GSM auth response parameters.
     */
    @Test
    public void testSendNetworkEapSimGsmAuthResponseWith3KcSresPair() throws Exception {
        final byte[] kc1 = new byte[]{0x45, 0x45, 0x32, 0x34, 0x45, 0x10, 0x34, 0x12};
        final byte[] sres1 = new byte[]{0x12, 0x10, 0x32, 0x23};
        final byte[] kc2 = new byte[]{0x45, 0x34, 0x12, 0x34, 0x45, 0x10, 0x34, 0x12};
        final byte[] sres2 = new byte[]{0x12, 0x23, 0x12, 0x23};
        final byte[] kc3 = new byte[]{0x25, 0x34, 0x12, 0x14, 0x45, 0x10, 0x34, 0x12};
        final byte[] sres3 = new byte[]{0x42, 0x23, 0x22, 0x23};

        // Send 3 kc/sres pairs for this request.
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(kc1)
                + ":" + NativeUtil.hexStringFromByteArray(sres1)
                + ":" + NativeUtil.hexStringFromByteArray(kc2)
                + ":" + NativeUtil.hexStringFromByteArray(sres2)
                + ":" + NativeUtil.hexStringFromByteArray(kc3)
                + ":" + NativeUtil.hexStringFromByteArray(sres3);

        ArgumentCaptor<NetworkResponseEapSimGsmAuthParams[]> paramCaptor =
                ArgumentCaptor.forClass(NetworkResponseEapSimGsmAuthParams[].class);
        doNothing().when(mISupplicantStaNetworkMock).sendNetworkEapSimGsmAuthResponse(
                paramCaptor.capture());
        assertTrue(mSupplicantNetwork.sendNetworkEapSimGsmAuthResponse(paramsStr));

        NetworkResponseEapSimGsmAuthParams[] captured = paramCaptor.getValue();
        assertEquals(3, captured.length);
        assertArrayEquals(kc1, captured[0].kc);
        assertArrayEquals(sres1, captured[0].sres);
        assertArrayEquals(kc2, captured[1].kc);
        assertArrayEquals(sres2, captured[1].sres);
        assertArrayEquals(kc3, captured[2].kc);
        assertArrayEquals(sres3, captured[2].sres);
    }

    /**
     * Tests the parsing of invalid GSM auth response parameters (invalid kc & sres lengths).
     */
    @Test
    public void testSendInvalidKcSresLenNetworkEapSimGsmAuthResponse() throws Exception {
        final byte[] kc1 = new byte[]{0x45, 0x45, 0x32, 0x34, 0x45, 0x10, 0x34};
        final byte[] sres1 = new byte[]{0x12, 0x10, 0x23};
        final byte[] kc2 = new byte[]{0x45, 0x34, 0x12, 0x34, 0x45, 0x10, 0x34, 0x12};
        final byte[] sres2 = new byte[]{0x12, 0x23, 0x12, 0x23};

        // Send 2 kc/sres pairs for this request.
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(kc1)
                + ":" + NativeUtil.hexStringFromByteArray(sres1)
                + ":" + NativeUtil.hexStringFromByteArray(kc2)
                + ":" + NativeUtil.hexStringFromByteArray(sres2);

        assertFalse(mSupplicantNetwork.sendNetworkEapSimGsmAuthResponse(paramsStr));
        verify(mISupplicantStaNetworkMock, never())
                .sendNetworkEapSimGsmAuthResponse(any(NetworkResponseEapSimGsmAuthParams[].class));
    }

    /**
     * Tests the parsing of invalid GSM auth response parameters (invalid number of kc/sres pairs).
     */
    @Test
    public void testSendInvalidKcSresPairNumNetworkEapSimGsmAuthResponse() throws Exception {
        final byte[] kc = new byte[]{0x45, 0x34, 0x12, 0x34, 0x45, 0x10, 0x34, 0x12};
        final byte[] sres = new byte[]{0x12, 0x23, 0x12, 0x23};

        // Send 1 kc/sres pair for this request.
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(kc)
                + ":" + NativeUtil.hexStringFromByteArray(sres);

        assertFalse(mSupplicantNetwork.sendNetworkEapSimGsmAuthResponse(paramsStr));
        verify(mISupplicantStaNetworkMock, never())
                .sendNetworkEapSimGsmAuthResponse(any(NetworkResponseEapSimGsmAuthParams[].class));
    }

    /**
     * Tests the parsing of UMTS auth response parameters.
     */
    @Test
    public void testSendNetworkEapSimUmtsAuthResponse() throws Exception {
        final byte[] ik = new byte[]{0x45, 0x45, 0x32, 0x34, 0x45, 0x10, 0x34, 0x12, 0x23, 0x34,
                0x33, 0x23, 0x34, 0x10, 0x40, 0x34};
        final byte[] ck = new byte[]{0x12, 0x10, 0x32, 0x23, 0x45, 0x10, 0x34, 0x12, 0x23, 0x34,
                0x33, 0x23, 0x34, 0x10, 0x40, 0x34};
        final byte[] res = new byte[]{0x12, 0x10, 0x32, 0x23, 0x45, 0x10, 0x34, 0x12, 0x23, 0x34};
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(ik)
                + ":" + NativeUtil.hexStringFromByteArray(ck)
                + ":" + NativeUtil.hexStringFromByteArray(res);

        ArgumentCaptor<NetworkResponseEapSimUmtsAuthParams> paramCaptor =
                ArgumentCaptor.forClass(NetworkResponseEapSimUmtsAuthParams.class);
        doNothing().when(mISupplicantStaNetworkMock).sendNetworkEapSimUmtsAuthResponse(
                paramCaptor.capture());
        assertTrue(mSupplicantNetwork.sendNetworkEapSimUmtsAuthResponse(paramsStr));
        assertArrayEquals(ik, paramCaptor.getValue().ik);
        assertArrayEquals(ck, paramCaptor.getValue().ck);
        assertArrayEquals(res, paramCaptor.getValue().res);
    }

    /**
     * Tests the parsing of invalid UMTS auth response parameters (invalid ik, ck lengths).
     */
    @Test
    public void testSendInvalidNetworkEapSimUmtsAuthResponse() throws Exception {
        final byte[] ik = new byte[]{0x45, 0x45, 0x32, 0x34, 0x45, 0x10, 0x34, 0x12};
        final byte[] ck = new byte[]{0x12, 0x10, 0x32, 0x23, 0x45, 0x10, 0x34, 0x12, 0x23, 0x34,
                0x33, 0x23, 0x34, 0x10, 0x40};
        final byte[] res = new byte[]{0x12, 0x10, 0x32, 0x23, 0x45, 0x10, 0x34, 0x12, 0x23, 0x34};
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(ik)
                + ":" + NativeUtil.hexStringFromByteArray(ck)
                + ":" + NativeUtil.hexStringFromByteArray(res);

        assertFalse(mSupplicantNetwork.sendNetworkEapSimUmtsAuthResponse(paramsStr));
        verify(mISupplicantStaNetworkMock, never())
                .sendNetworkEapSimUmtsAuthResponse(any(NetworkResponseEapSimUmtsAuthParams.class));
    }

    /**
     * Tests the parsing of UMTS auts response parameters.
     */
    @Test
    public void testSendNetworkEapSimUmtsAutsResponse() throws Exception {
        final byte[] auts = new byte[]{0x45, 0x45, 0x32, 0x34, 0x45, 0x10, 0x34, 0x12, 0x23, 0x34,
                0x33, 0x23, 0x34, 0x10};
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(auts);

        ArgumentCaptor<byte[]> autsCaptor = ArgumentCaptor.forClass(byte[].class);
        doNothing().when(mISupplicantStaNetworkMock).sendNetworkEapSimUmtsAutsResponse(
                autsCaptor.capture());
        assertTrue(mSupplicantNetwork.sendNetworkEapSimUmtsAutsResponse(paramsStr));
        assertArrayEquals(auts, autsCaptor.getValue());
    }

    /**
     * Tests the parsing of invalid UMTS auts response parameters (invalid auts length).
     */
    @Test
    public void testSendInvalidNetworkEapSimUmtsAutsResponse() throws Exception {
        final byte[] auts = new byte[]{0x45, 0x45, 0x32, 0x34, 0x45, 0x10, 0x34, 0x12, 0x23};
        String paramsStr = ":" + NativeUtil.hexStringFromByteArray(auts);

        assertFalse(mSupplicantNetwork.sendNetworkEapSimUmtsAutsResponse(paramsStr));
        verify(mISupplicantStaNetworkMock, never())
                .sendNetworkEapSimUmtsAutsResponse(any(byte[].class));
    }

    /**
     * Tests the parsing of identity string.
     */
    @Test
    public void testSendNetworkEapIdentityResponse() throws Exception {
        final String identityStr = "test@test.com";
        final String encryptedIdentityStr = "test2@test.com";

        ArgumentCaptor<byte[]> idCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> encryptedIdCaptor = ArgumentCaptor.forClass(byte[].class);
        doNothing().when(mISupplicantStaNetworkMock).sendNetworkEapIdentityResponse(
                idCaptor.capture(), encryptedIdCaptor.capture());
        assertTrue(mSupplicantNetwork.sendNetworkEapIdentityResponse(identityStr,
                encryptedIdentityStr));

        assertEquals(identityStr, NativeUtil.stringFromByteArray(idCaptor.getValue()));
        assertEquals(encryptedIdentityStr,
                NativeUtil.stringFromByteArray(encryptedIdCaptor.getValue()));
    }

    /**
     * Tests the addition of FT flags when the device supports it.
     */
    @Test
    public void testAddFtPskFlags() throws Exception {
        mResources.setBoolean(R.bool.config_wifi_fast_bss_transition_enabled, true);
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        // Assume that the default params are used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // Check the supplicant variables to ensure that we have added the FT flags.
        assertTrue((mSupplicantVariables.keyMgmtMask & KeyMgmtMask.FT_PSK)
                == KeyMgmtMask.FT_PSK);

        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        // The FT flags should be stripped out when reading it back.
        WifiConfigurationTestUtil.assertConfigurationEqualForSupplicant(config, loadConfig);
    }

    /**
     * Tests the addition of FT flags when the device supports it.
     */
    @Test
    public void testAddFtEapFlags() throws Exception {
        mResources.setBoolean(R.bool.config_wifi_fast_bss_transition_enabled, true);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // Check the supplicant variables to ensure that we have added the FT flags.
        assertTrue((mSupplicantVariables.keyMgmtMask & KeyMgmtMask.FT_EAP)
                == KeyMgmtMask.FT_EAP);

        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        // The FT flags should be stripped out when reading it back.
        WifiConfigurationTestUtil.assertConfigurationEqualForSupplicant(config, loadConfig);
    }

    /**
     * Tests the addition of SHA256 flags (WPA_PSK_SHA256)
     */
    @Test
    public void testAddPskSha256Flags() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // Check the supplicant variables to ensure that we have added the SHA256 flags.
        assertTrue((mSupplicantVariables.keyMgmtMask & KeyMgmtMask.WPA_PSK_SHA256)
                == KeyMgmtMask.WPA_PSK_SHA256);

        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        // The SHA256 flags should be stripped out when reading it back.
        WifiConfigurationTestUtil.assertConfigurationEqualForSupplicant(config, loadConfig);
    }

    /**
     * Tests the addition of SHA256 flags (WPA_EAP_SHA256)
     */
    @Test
    public void testAddEapSha256Flags() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // Check the supplicant variables to ensure that we have added the SHA256 flags.
        assertTrue((mSupplicantVariables.keyMgmtMask & KeyMgmtMask.WPA_EAP_SHA256)
                == KeyMgmtMask.WPA_EAP_SHA256);

        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        // The SHA256 flags should be stripped out when reading it back.
        WifiConfigurationTestUtil.assertConfigurationEqualForSupplicant(config, loadConfig);
    }

    /**
     * Tests the addition of multiple AKM when the device supports it.
     */
    @Test
    public void testAddPskSaeAkmWhenAutoUpgradeOffloadIsSupported() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // Check the supplicant variables to ensure that we have added the FT flags.
        assertEquals(KeyMgmtMask.WPA_PSK, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.WPA_PSK));
        assertEquals(KeyMgmtMask.SAE, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.SAE));

        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        // The additional SAE AMK should be stripped out when reading it back.
        WifiConfigurationTestUtil.assertConfigurationEqualForSupplicant(config, loadConfig);
    }

    /**
     * Tests the PMF is disabled when the device supports multiple AKMs.
     */
    @Test
    public void testPmfDisabledWhenAutoUpgradeOffloadIsSupportedAndSaeSelected() throws Exception {
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE));
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // PSK and SAE is set and PMF is disable when SAE is selected.
        assertEquals(KeyMgmtMask.WPA_PSK, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.WPA_PSK));
        assertEquals(KeyMgmtMask.SAE, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.SAE));
        assertEquals(false, mSupplicantVariables.requirePmf);
    }

    /**
     * Tests the PMF is kept when the device does not support multiple AKMs.
     */
    @Test
    public void testPmfEnabledWhenAutoUpgradeOffloadNotSupportedAndSaeSelected() throws Exception {
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE));
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // Only SAE is set and PMF is enabled when SAE is selected.
        assertEquals(0, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.WPA_PSK));
        assertEquals(KeyMgmtMask.SAE, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.SAE));
        assertEquals(true, mSupplicantVariables.requirePmf);
    }

    /**
     * Tests the addition of multiple AKM when the device does not support it.
     */
    @Test
    public void testAddPskSaeAkmWhenAutoUpgradeOffloadIsNotSupported() throws Exception {
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        // Check the supplicant variables to ensure that we have added the FT flags.
        assertEquals(KeyMgmtMask.WPA_PSK, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.WPA_PSK));
        assertEquals(0, (mSupplicantVariables.keyMgmtMask & KeyMgmtMask.SAE));

        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        WifiConfigurationTestUtil.assertConfigurationEqualForSupplicant(config, loadConfig);
    }

    /**
     * Tests ciphers are merged when the device supports auto upgrade offload feature
     * and when candidate security type is PSK.
     */
    @Test
    public void testCiphersMergedWhenAutoUpgradeOffloadIsSupportedAndPskSelected()
            throws Exception {
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        assertEquals(PairwiseCipherMask.CCMP | PairwiseCipherMask.TKIP
                | PairwiseCipherMask.GCMP_128 | PairwiseCipherMask.GCMP_256,
                mSupplicantVariables.pairwiseCipherMask);
        assertEquals(GroupCipherMask.CCMP | GroupCipherMask.TKIP
                | GroupCipherMask.WEP40 | GroupCipherMask.WEP104
                | GroupCipherMask.GCMP_128 | GroupCipherMask.GCMP_256,
                mSupplicantVariables.groupCipherMask);
    }

    /**
     * Tests ciphers are not changed when the device does not supports auto upgrade offload feature
     * and when candidate security type is PSK.
     */
    @Test
    public void testCiphersNotChangedWhenAutoUpgradeOffloadNotSupportedAndPskSelected()
            throws Exception {
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        assertEquals(PairwiseCipherMask.CCMP | PairwiseCipherMask.TKIP,
                mSupplicantVariables.pairwiseCipherMask);
        assertEquals(GroupCipherMask.CCMP | GroupCipherMask.TKIP
                | GroupCipherMask.WEP40 | GroupCipherMask.WEP104,
                mSupplicantVariables.groupCipherMask);
    }

    /**
     * Tests ciphers are merged when the device supports auto upgrade offload feature
     * and when candidate security type is SAE.
     */
    @Test
    public void testCiphersMergedWhenAutoUpgradeOffloadIsSupportedAndSaeSelected()
            throws Exception {
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE));
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        assertEquals(PairwiseCipherMask.CCMP | PairwiseCipherMask.TKIP
                | PairwiseCipherMask.GCMP_128 | PairwiseCipherMask.GCMP_256,
                mSupplicantVariables.pairwiseCipherMask);
        assertEquals(GroupCipherMask.CCMP | GroupCipherMask.TKIP
                | GroupCipherMask.WEP40 | GroupCipherMask.WEP104
                | GroupCipherMask.GCMP_128 | GroupCipherMask.GCMP_256,
                mSupplicantVariables.groupCipherMask);
    }

    /**
     * Tests ciphers are not changed when the device does not supports auto upgrade offload feature
     * and when candidate security type is SAE.
     */
    @Test
    public void testCiphersNotChangedWhenAutoUpgradeOffloadNotSupportedAndSaeSelected()
            throws Exception {
        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE));
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        assertEquals(PairwiseCipherMask.CCMP | PairwiseCipherMask.GCMP_128
                | PairwiseCipherMask.GCMP_256,
                mSupplicantVariables.pairwiseCipherMask);
        assertEquals(GroupCipherMask.CCMP | GroupCipherMask.GCMP_128 | GroupCipherMask.GCMP_256,
                mSupplicantVariables.groupCipherMask);
    }

    /**
     * Tests the retrieval of WPS NFC token.
     */
    @Test
    public void testGetWpsNfcConfigurationToken() throws Exception {
        byte[] token = {0x45, 0x34};
        when(mISupplicantStaNetworkMock.getWpsNfcConfigurationToken()).thenReturn(token);
        assertEquals("4534", mSupplicantNetwork.getWpsNfcConfigurationToken());
    }

    /**
     * Tests that callback registration failure triggers a failure in saving network config.
     */
    @Test
    public void testSaveFailureDueToCallbackReg() throws Exception {
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantStaNetworkMock)
                .registerCallback(any(ISupplicantStaNetworkCallback.class));
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertFalse(mSupplicantNetwork.saveWifiConfiguration(config));
    }

    /**
     * Tests the network gsm auth callback.
     */
    @Test
    public void testNetworkEapGsmAuthCallback() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        assertNotNull(mISupplicantStaNetworkCallback);

        // Now trigger eap gsm callback and ensure that the event is broadcast via WifiMonitor.
        NetworkRequestEapSimGsmAuthParams params = new NetworkRequestEapSimGsmAuthParams();
        GsmRand[] rands = new GsmRand[3];
        String[] expectedRands = new String[3];
        Random random = new Random();
        for (int i = 0; i < rands.length; i++) {
            rands[i] = new GsmRand();
            rands[i].data = new byte[16];
            random.nextBytes(rands[i].data);
            expectedRands[i] = NativeUtil.hexStringFromByteArray(rands[i].data);
        }
        params.rands = rands;

        mISupplicantStaNetworkCallback.onNetworkEapSimGsmAuthRequest(params);
        verify(mWifiMonitor).broadcastNetworkGsmAuthRequestEvent(
                eq(IFACE_NAME), eq(config.networkId), eq(config.SSID), eq(expectedRands));
    }

    /**
     * Tests the network umts auth callback.
     */
    @Test
    public void testNetworkEapUmtsAuthCallback() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        assertNotNull(mISupplicantStaNetworkCallback);

        // Now trigger eap gsm callback and ensure that the event is broadcast via WifiMonitor.
        NetworkRequestEapSimUmtsAuthParams params = new NetworkRequestEapSimUmtsAuthParams();
        Random random = new Random();
        params.autn = new byte[16];
        params.rand = new byte[16];
        random.nextBytes(params.autn);
        random.nextBytes(params.rand);

        String[] expectedRands = {
                NativeUtil.hexStringFromByteArray(params.rand),
                NativeUtil.hexStringFromByteArray(params.autn)
        };

        mISupplicantStaNetworkCallback.onNetworkEapSimUmtsAuthRequest(params);
        verify(mWifiMonitor).broadcastNetworkUmtsAuthRequestEvent(
                eq(IFACE_NAME), eq(config.networkId), eq(config.SSID), eq(expectedRands));
    }

    /**
     * Tests the network identity callback.
     */
    @Test
    public void testNetworkIdentityCallback() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        assertNotNull(mISupplicantStaNetworkCallback);

        // Now trigger identity request callback and ensure that the event is broadcast via
        // WifiMonitor.
        mISupplicantStaNetworkCallback.onNetworkEapIdentityRequest();
        verify(mWifiMonitor).broadcastNetworkIdentityRequestEvent(
                eq(IFACE_NAME), eq(config.networkId), eq(config.SSID));
    }

    private void testWifiConfigurationSaveLoad(WifiConfiguration config) {
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        // Save the configuration using the supplicant network HAL
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        WifiConfigurationTestUtil.assertConfigurationEqualForSupplicant(config, loadConfig);
        assertEquals(config.getProfileKey(),
                networkExtras.get(SupplicantStaNetworkHalHidlImpl.ID_STRING_KEY_CONFIG_KEY));
        assertEquals(
                config.creatorUid,
                Integer.parseInt(networkExtras.get(
                        SupplicantStaNetworkHalHidlImpl.ID_STRING_KEY_CREATOR_UID)));
        // There is no getter for this one, so check the supplicant variable.
        if (!TextUtils.isEmpty(config.updateIdentifier)) {
            assertEquals(Integer.parseInt(config.updateIdentifier),
                    mSupplicantVariables.updateIdentifier);
        }
        // There is no getter for this one, so check the supplicant variable.
        String oppKeyCaching =
                config.enterpriseConfig.getFieldValue(WifiEnterpriseConfig.OPP_KEY_CACHING);
        if (!TextUtils.isEmpty(oppKeyCaching)) {
            assertEquals(
                    Integer.parseInt(oppKeyCaching) == 1 ? true : false,
                    mSupplicantVariables.eapProactiveKeyCaching);
        }
        // There is no getter for this one, so check the supplicant variable.
        String eapErp =
                config.enterpriseConfig.getFieldValue(WifiEnterpriseConfig.EAP_ERP);
        if (!TextUtils.isEmpty(eapErp)) {
            assertEquals(
                    Integer.parseInt(eapErp) == 1 ? true : false,
                    mSupplicantVariables.eapErp);
        }
    }

    /**
     * Verifies that createNetworkExtra() & parseNetworkExtra correctly writes a serialized and
     * URL-encoded JSON object.
     */
    @Test
    public void testNetworkExtra() {
        assertEquals(NETWORK_EXTRAS_SERIALIZED,
                SupplicantStaNetworkHalHidlImpl.createNetworkExtra(NETWORK_EXTRAS_VALUES));
        assertEquals(NETWORK_EXTRAS_VALUES,
                SupplicantStaNetworkHalHidlImpl.parseNetworkExtra(NETWORK_EXTRAS_SERIALIZED));
    }

    /**
     * Verifies that fetachEapAnonymousIdentity() can get the anonymous identity from supplicant.
     */
    @Test
    public void testFetchEapAnonymousIdentity() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setAnonymousIdentity(ANONYMOUS_IDENTITY);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        assertEquals(ANONYMOUS_IDENTITY, mSupplicantNetwork.fetchEapAnonymousIdentity());
    }

    /**
     * Verifies that setPmkCache can set PMK cache
     */
    @Test
    public void testSetPmkCache() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig =
                WifiConfigurationTestUtil.createTLSWifiEnterpriseConfigWithNonePhase2();
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));

        byte[] serializedData = {0x12, 0x34, 0x56};
        assertTrue(mSupplicantNetwork.setPmkCache(serializedData));
        assertEquals(serializedData, mSupplicantVariables.serializedPmkCache);
    }

    /** Verifies that setSaeH2eMode works with SaeH2eOnlyMode enabled */
    @Test
    public void testEnableSaeH2eOnlyMode() throws Exception {
        when(mWifiGlobals.isWpa3SaeH2eSupported()).thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        config.enableSaeH2eOnlyMode(true);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock).setSaeH2eMode(eq(SaeH2eMode.H2E_MANDATORY));
    }

    /** Verifies that setSaeH2eMode works with SaeH2eOnlyMode disabled */
    @Test
    public void testDisableSaeH2eOnlyMode() throws Exception {
        when(mWifiGlobals.isWpa3SaeH2eSupported()).thenReturn(true);
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        config.enableSaeH2eOnlyMode(false);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock).setSaeH2eMode(eq(SaeH2eMode.H2E_OPTIONAL));
    }

    /** Verifies that setSaeH2eMode works when Wpa3SaeH2e is not supported */
    @Test
    public void testDisableSaeH2eOnlyModeWhenH2eNotSupported() throws Exception {
        when(mWifiGlobals.isWpa3SaeH2eSupported()).thenReturn(false);
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        config.enableSaeH2eOnlyMode(false);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock).setSaeH2eMode(eq(SaeH2eMode.DISABLED));
    }

    /**
     * Tests the saving/loading of WifiConfiguration to wpa_supplicant with psk passphrase
     */
    @Test
    public void testSaeNetworkWifiConfigurationSaveLoad() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();

        // Set the new defaults
        testWifiConfigurationSaveLoad(config);
        verify(mISupplicantStaNetworkMock).setSaePassword(anyString());
        verify(mISupplicantStaNetworkMock, never()).setPsk(any(byte[].class));
        verify(mISupplicantStaNetworkMock, never()).getPsk();
        verify(mISupplicantStaNetworkMock)
                .setPairwiseCipher(PairwiseCipherMask.CCMP
                        | PairwiseCipherMask.GCMP_128
                        | PairwiseCipherMask.GCMP_256);
        verify(mISupplicantStaNetworkMock)
                .setGroupCipher(GroupCipherMask.CCMP
                        | GroupCipherMask.GCMP_128
                        | GroupCipherMask.GCMP_256);
    }

    private int getExpectedPairwiseCiphers(WifiConfiguration config) {
        int halMaskValue = 0;

        // The default security params are used in the test.
        BitSet allowedPairwiseCiphers = config.getDefaultSecurityParams()
                .getAllowedPairwiseCiphers();

        if (allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.GCMP_128)) {
            halMaskValue |= PairwiseCipherMask.GCMP_128;
        }

        if (allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.SMS4)) {
            halMaskValue |= PairwiseCipherMask.SMS4;
        }

        if (allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.GCMP_256)) {
            halMaskValue |= PairwiseCipherMask.GCMP_256;
        }

        if (allowedPairwiseCiphers.get(WifiConfiguration.PairwiseCipher.CCMP)) {
            halMaskValue |= PairwiseCipherMask.CCMP;
        }

        return halMaskValue;
    }

    private int getExpectedGroupCiphers(WifiConfiguration config) {
        int halMaskValue = 0;

        // The default security params are used in the test.
        BitSet allowedGroupCiphers = config.getDefaultSecurityParams().getAllowedGroupCiphers();

        if (allowedGroupCiphers.get(WifiConfiguration.GroupCipher.GCMP_128)) {
            halMaskValue |= GroupCipherMask.GCMP_128;
        }

        if (allowedGroupCiphers.get(WifiConfiguration.GroupCipher.GCMP_256)) {
            halMaskValue |= GroupCipherMask.GCMP_256;
        }

        if (allowedGroupCiphers.get(WifiConfiguration.GroupCipher.CCMP)) {
            halMaskValue |= GroupCipherMask.CCMP;
        }

        return halMaskValue;
    }

    /**
     * Tests the saving/loading of WifiConfiguration supported cipher values
     */
    @Test
    public void testSupportedCiphers() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        int expectedHalPairwiseCiphers = getExpectedPairwiseCiphers(config);
        int expectedHalGroupCiphers = getExpectedGroupCiphers(config);

        // Assume that the default params are used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        verify(mISupplicantStaNetworkMock).setPairwiseCipher(expectedHalPairwiseCiphers);
        verify(mISupplicantStaNetworkMock).setGroupCipher(expectedHalGroupCiphers);
    }

    /**
     * Tests the saving/loading of WifiConfiguration supported cipher values
     * when the GCMP-256 cipher is not supported.
     *
     * GCMP-256 is supported only if WPA3 SUITE-B is supported.
     */
    @Test
    public void testSupportedCiphersNoGcmp256() throws Exception {
        // Reinitialize mSupplicantNetwork without support for WPA3 SUITE-B
        mAdvanceKeyMgmtFeatures = 0;
        mSupplicantNetwork = new SupplicantStaNetworkHalAidlImpl(1,
                mISupplicantStaNetworkMock, IFACE_NAME, mContext, mWifiMonitor,
                mWifiGlobals, mAdvanceKeyMgmtFeatures, mWpaDriverFeatures);
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        int expectedHalPairwiseCiphers = getExpectedPairwiseCiphers(config);
        expectedHalPairwiseCiphers &= ~PairwiseCipherMask.GCMP_256;
        int expectedHalGroupCiphers = getExpectedGroupCiphers(config);
        expectedHalGroupCiphers &= ~GroupCipherMask.GCMP_256;

        // Assume that the default params are used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        verify(mISupplicantStaNetworkMock).setPairwiseCipher(expectedHalPairwiseCiphers);
        verify(mISupplicantStaNetworkMock).setGroupCipher(expectedHalGroupCiphers);
    }

    /**
     * Tests the appending decorated identity prefix to anonymous identity and saving to
     * wpa_supplicant.
     */
    @Test
    public void testEapNetworkSetsDecoratedIdentityPrefix() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setAnonymousIdentity(ANONYMOUS_IDENTITY);
        config.enterpriseConfig.setDecoratedIdentityPrefix(TEST_DECORATED_IDENTITY_PREFIX);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        WifiConfiguration loadConfig = new WifiConfiguration();
        Map<String, String> networkExtras = new HashMap<>();
        assertTrue(mSupplicantNetwork.loadWifiConfiguration(loadConfig, networkExtras));
        assertEquals(TEST_DECORATED_IDENTITY_PREFIX
                        + config.enterpriseConfig.getAnonymousIdentity(),
                loadConfig.enterpriseConfig.getAnonymousIdentity());
        assertEquals(TEST_DECORATED_IDENTITY_PREFIX + ANONYMOUS_IDENTITY,
                mSupplicantNetwork.fetchEapAnonymousIdentity());
    }

    /**
     * Tests setting the selected RCOI on Passpoint networks and saving to wpa_supplicant.
     */
    @Test
    public void testEapNetworkSetsSelectedRcoi() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        config.enterpriseConfig.setAnonymousIdentity(ANONYMOUS_IDENTITY);
        config.enterpriseConfig.setSelectedRcoi(TEST_SELECTED_RCOI_LONG);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        assertTrue(Arrays.equals(TEST_SELECTED_RCOI_BYTE_ARRAY, mSupplicantVariables.selectedRcoi));
    }

    @Test
    public void testSetStrictConservativePeerModeToNonEapSimConfiguration() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil
                .createEapNetwork(WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.AKA);
        config.enterpriseConfig.setStrictConservativePeerMode(true);
        when(mISupplicantStaNetworkMock.getInterfaceVersion()).thenReturn(2);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock, never()).setStrictConservativePeerMode(anyBoolean());
    }

    @Test
    public void testStrictConservativePeerModeIsSupported() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil
                .createEapNetwork(WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        config.enterpriseConfig.setStrictConservativePeerMode(true);
        when(mISupplicantStaNetworkMock.getInterfaceVersion()).thenReturn(2);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock).setStrictConservativePeerMode(eq(true));
    }

    @Test
    public void testStrictConservativePeerModeIsNotSupported() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil
                .createEapNetwork(WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        config.enterpriseConfig.setStrictConservativePeerMode(true);
        when(mISupplicantStaNetworkMock.getInterfaceVersion()).thenReturn(1);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock, never()).setStrictConservativePeerMode(anyBoolean());
    }

    @Test
    public void testStrictConservativePeerModeNeverSetFalse() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil
                .createEapNetwork(WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.NONE);
        config.enterpriseConfig.setStrictConservativePeerMode(false);
        when(mISupplicantStaNetworkMock.getInterfaceVersion()).thenReturn(2);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock, never()).setStrictConservativePeerMode(anyBoolean());
    }

    /**
     * Tests setting TLS minimum version API with AIDL v1
     */
    @Test
    public void testEapMinimumTlsVersionWifiConfigurationSaveLoadWithAidlV1() throws Exception {
        // Default is AIDL v1
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setMinimumTlsVersion(WifiEnterpriseConfig.TLS_V1_3);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        verify(mISupplicantStaNetworkMock, never()).setMinimumTlsVersionEapPhase1Param(anyInt());
    }

    /**
     * Tests setting TLS minimum version API with AIDL v2, but TLS v1.3 is not supported.
     */
    @Test
    public void testEapMinimumTlsVersionWifiConfigurationSaveLoadWithAidlV2TlsV13NotSupported()
            throws Exception {
        // Re-init mock to AIDL v2 without TLS v1.3 support.
        mSupplicantNetwork = new SupplicantStaNetworkHalAidlImpl(2,
                mISupplicantStaNetworkMock, IFACE_NAME, mContext, mWifiMonitor,
                mWifiGlobals, mAdvanceKeyMgmtFeatures, 0);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setMinimumTlsVersion(WifiEnterpriseConfig.TLS_V1_3);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        // Should fallback to TLS v1.2
        verify(mISupplicantStaNetworkMock).setMinimumTlsVersionEapPhase1Param(TlsVersion.TLS_V1_2);
    }

    /**
     * Tests setting TLS minimum version API with AIDL v2, and TLS v1.3 is supported.
     */
    @Test
    public void testEapMinimumTlsVersionWifiConfigurationSaveLoadWithAidlV2TlsV13Supported()
            throws Exception {
        // Re-init mock to AIDL v2 with TLS v1.3 support.
        mSupplicantNetwork = new SupplicantStaNetworkHalAidlImpl(2,
                mISupplicantStaNetworkMock, IFACE_NAME, mContext, mWifiMonitor,
                mWifiGlobals, mAdvanceKeyMgmtFeatures,
                WifiManager.WIFI_FEATURE_TLS_V1_3);
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setMinimumTlsVersion(WifiEnterpriseConfig.TLS_V1_3);
        // Assume that the default params is used for this test.
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                config.getDefaultSecurityParams());
        assertTrue(mSupplicantNetwork.saveWifiConfiguration(config));
        // Should set minimum TLS version to TLS v1.3
        verify(mISupplicantStaNetworkMock).setMinimumTlsVersionEapPhase1Param(TlsVersion.TLS_V1_3);
    }

    /**
     * Tests that vendor data is sent to the HAL if included in the WifiConfiguration.
     */
    @Test
    public void testSetVendorData() throws Exception {
        // Re-initialize DUT to HAL service version 3
        assumeTrue(SdkLevel.isAtLeastV());
        mSupplicantNetwork = new SupplicantStaNetworkHalAidlImpl(3,
                mISupplicantStaNetworkMock, IFACE_NAME, mContext, mWifiMonitor,
                mWifiGlobals, mAdvanceKeyMgmtFeatures, mWpaDriverFeatures);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt("intFieldKey", 1337);
        android.net.wifi.OuiKeyedData ouiKeyedData =
                new android.net.wifi.OuiKeyedData.Builder(0x00aabbcc, bundle).build();
        List<android.net.wifi.OuiKeyedData> frameworkVendorData = Arrays.asList(ouiKeyedData);

        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.setVendorData(frameworkVendorData);
        testWifiConfigurationSaveLoad(config);

        verify(mISupplicantStaNetworkMock).setVendorData(any(OuiKeyedData[].class));
        OuiKeyedData[] halVendorData = mSupplicantVariables.vendorData;
        assertEquals(frameworkVendorData.size(), halVendorData.length);
        assertEquals(frameworkVendorData.get(0).getOui(), halVendorData[0].oui);
        assertTrue(frameworkVendorData.get(0).getData().equals(halVendorData[0].vendorData));
    }

    /**
     * Sets up the AIDL interface mock with all the setters/getter values.
     * Note: This only sets up the mock to return success on all methods.
     */
    private void setupISupplicantNetworkMock() throws Exception {
        /** SSID */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] ssid) throws RemoteException {
                mSupplicantVariables.ssid = ssid;
            }
        }).when(mISupplicantStaNetworkMock).setSsid(any(byte[].class));
        doAnswer(new AnswerWithArguments() {
            public byte[] answer() throws RemoteException {
                return mSupplicantVariables.ssid;
            }
        }).when(mISupplicantStaNetworkMock).getSsid();

        /** Network Id */
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.networkId;
            }
        }).when(mISupplicantStaNetworkMock).getId();

        /** BSSID */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] bssid) throws RemoteException {
                mSupplicantVariables.bssid = bssid;
            }
        }).when(mISupplicantStaNetworkMock).setBssid(any(byte[].class));
        doAnswer(new AnswerWithArguments() {
            public byte[] answer() throws RemoteException {
                return mSupplicantVariables.bssid;
            }
        }).when(mISupplicantStaNetworkMock).getBssid();

        /** Scan SSID (Is Hidden Network?) */
        doAnswer(new AnswerWithArguments() {
            public void answer(boolean enable) throws RemoteException {
                mSupplicantVariables.scanSsid = enable;
            }
        }).when(mISupplicantStaNetworkMock).setScanSsid(any(boolean.class));
        doAnswer(new AnswerWithArguments() {
            public boolean answer() throws RemoteException {
                return mSupplicantVariables.scanSsid;
            }
        }).when(mISupplicantStaNetworkMock).getScanSsid();

        /** Require PMF*/
        doAnswer(new AnswerWithArguments() {
            public void answer(boolean enable) throws RemoteException {
                mSupplicantVariables.requirePmf = enable;
            }
        }).when(mISupplicantStaNetworkMock).setRequirePmf(any(boolean.class));
        doAnswer(new AnswerWithArguments() {
            public boolean answer() throws RemoteException {
                return mSupplicantVariables.requirePmf;
            }
        }).when(mISupplicantStaNetworkMock).getRequirePmf();

        /** SAE password */
        doAnswer(new AnswerWithArguments() {
            public void answer(String saePassword) throws RemoteException {
                mSupplicantVariables.pskPassphrase = saePassword;
            }
        }).when(mISupplicantStaNetworkMock).setSaePassword(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.pskPassphrase;
            }
        }).when(mISupplicantStaNetworkMock).getSaePassword();

        /** PSK passphrase */
        doAnswer(new AnswerWithArguments() {
            public void answer(String pskPassphrase) throws RemoteException {
                mSupplicantVariables.pskPassphrase = pskPassphrase;
            }
        }).when(mISupplicantStaNetworkMock).setPskPassphrase(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.pskPassphrase;
            }
        }).when(mISupplicantStaNetworkMock).getPskPassphrase();

        /** PSK */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] psk) throws RemoteException {
                mSupplicantVariables.psk = psk;
            }
        }).when(mISupplicantStaNetworkMock).setPsk(any(byte[].class));
        doAnswer(new AnswerWithArguments() {
            public byte[] answer() throws RemoteException {
                return mSupplicantVariables.psk;
            }
        }).when(mISupplicantStaNetworkMock).getPsk();

        /** WEP keys **/
        doAnswer(new AnswerWithArguments() {
            public void answer(int keyIdx, byte[] key) throws RemoteException {
                mSupplicantVariables.wepKey[keyIdx] = NativeUtil.byteArrayToArrayList(key);
            }
        }).when(mISupplicantStaNetworkMock).setWepKey(any(int.class), any(byte[].class));
        doAnswer(new AnswerWithArguments() {
            public byte[] answer(int keyIdx) throws RemoteException {
                ArrayList<Byte> key = mSupplicantVariables.wepKey[keyIdx];
                if (key != null) {
                    return NativeUtil.byteArrayFromArrayList(key);
                } else {
                    return new byte[0];
                }
            }
        }).when(mISupplicantStaNetworkMock).getWepKey(any(int.class));

        doAnswer(new AnswerWithArguments() {
            public void answer(int keyIdx) throws RemoteException {
                mSupplicantVariables.wepTxKeyIdx = keyIdx;
            }
        }).when(mISupplicantStaNetworkMock).setWepTxKeyIdx(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.wepTxKeyIdx;
            }
        }).when(mISupplicantStaNetworkMock).getWepTxKeyIdx();

        /** allowedKeyManagement */
        doAnswer(new AnswerWithArguments() {
            public void answer(int mask) throws RemoteException {
                mSupplicantVariables.keyMgmtMask = mask;
            }
        }).when(mISupplicantStaNetworkMock).setKeyMgmt(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.keyMgmtMask;
            }
        }).when(mISupplicantStaNetworkMock).getKeyMgmt();

        /** allowedProtocols */
        doAnswer(new AnswerWithArguments() {
            public void answer(int mask) throws RemoteException {
                mSupplicantVariables.protoMask = mask;
            }
        }).when(mISupplicantStaNetworkMock).setProto(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.protoMask;
            }
        }).when(mISupplicantStaNetworkMock).getProto();

        /** allowedAuthAlgorithms */
        doAnswer(new AnswerWithArguments() {
            public void answer(int mask) throws RemoteException {
                mSupplicantVariables.authAlgMask = mask;
            }
        }).when(mISupplicantStaNetworkMock).setAuthAlg(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.authAlgMask;
            }
        }).when(mISupplicantStaNetworkMock).getAuthAlg();

        /** allowedGroupCiphers */
        doAnswer(new AnswerWithArguments() {
            public void answer(int mask) throws RemoteException {
                mSupplicantVariables.groupCipherMask = mask;
            }
        }).when(mISupplicantStaNetworkMock).setGroupCipher(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.groupCipherMask;
            }
        }).when(mISupplicantStaNetworkMock).getGroupCipher();

        /** allowedPairwiseCiphers */
        doAnswer(new AnswerWithArguments() {
            public void answer(int mask) throws RemoteException {
                mSupplicantVariables.pairwiseCipherMask = mask;
            }
        }).when(mISupplicantStaNetworkMock).setPairwiseCipher(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.pairwiseCipherMask;
            }
        }).when(mISupplicantStaNetworkMock).getPairwiseCipher();

        /** allowedGroupManagementCiphers */
        doAnswer(new AnswerWithArguments() {
            public void answer(int mask) throws RemoteException {
                mSupplicantVariables.groupManagementCipherMask = mask;
            }
        }).when(mISupplicantStaNetworkMock).setGroupMgmtCipher(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.groupManagementCipherMask;
            }
        }).when(mISupplicantStaNetworkMock).getGroupMgmtCipher();

        /** metadata: idstr */
        doAnswer(new AnswerWithArguments() {
            public void answer(String idStr) throws RemoteException {
                mSupplicantVariables.idStr = idStr;
            }
        }).when(mISupplicantStaNetworkMock).setIdStr(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.idStr;
            }
        }).when(mISupplicantStaNetworkMock).getIdStr();

        /** UpdateIdentifier */
        doAnswer(new AnswerWithArguments() {
            public void answer(int identifier) throws RemoteException {
                mSupplicantVariables.updateIdentifier = identifier;
            }
        }).when(mISupplicantStaNetworkMock).setUpdateIdentifier(any(int.class));

        /** EAP method */
        doAnswer(new AnswerWithArguments() {
            public void answer(int method) throws RemoteException {
                Log.i("TwilightTest", "Received EAP method: " + String.valueOf(method));
                mSupplicantVariables.eapMethod = method;
            }
        }).when(mISupplicantStaNetworkMock).setEapMethod(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                // Return failure if not set
                if (mSupplicantVariables.eapMethod == -1) {
                    throw new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN);
                } else {
                    return mSupplicantVariables.eapMethod;
                }
            }
        }).when(mISupplicantStaNetworkMock).getEapMethod();

        /** EAP Phase 2 method */
        doAnswer(new AnswerWithArguments() {
            public void answer(int method) throws RemoteException {
                mSupplicantVariables.eapPhase2Method = method;
            }
        }).when(mISupplicantStaNetworkMock).setEapPhase2Method(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                // Return failure if not set
                if (mSupplicantVariables.eapPhase2Method == -1) {
                    throw new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN);
                } else {
                    return mSupplicantVariables.eapPhase2Method;
                }
            }
        }).when(mISupplicantStaNetworkMock).getEapPhase2Method();

        /** EAP Identity */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] identity) throws RemoteException {
                mSupplicantVariables.eapIdentity = identity;
            }
        }).when(mISupplicantStaNetworkMock).setEapIdentity(any(byte[].class));
        doAnswer(new AnswerWithArguments() {
            public byte[] answer() throws RemoteException {
                return mSupplicantVariables.eapIdentity;
            }
        }).when(mISupplicantStaNetworkMock).getEapIdentity();

        /** EAP Anonymous Identity */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] identity) throws RemoteException {
                mSupplicantVariables.eapAnonymousIdentity = identity;
            }
        }).when(mISupplicantStaNetworkMock).setEapAnonymousIdentity(any(byte[].class));
        doAnswer(new AnswerWithArguments() {
            public byte[] answer() throws RemoteException {
                return mSupplicantVariables.eapAnonymousIdentity;
            }
        }).when(mISupplicantStaNetworkMock).getEapAnonymousIdentity();

        /** EAP Password */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] password) throws RemoteException {
                mSupplicantVariables.eapPassword = password;
            }
        }).when(mISupplicantStaNetworkMock).setEapPassword(any(byte[].class));
        doAnswer(new AnswerWithArguments() {
            public byte[] answer() throws RemoteException {
                return mSupplicantVariables.eapPassword;
            }
        }).when(mISupplicantStaNetworkMock).getEapPassword();

        /** EAP Client Cert */
        doAnswer(new AnswerWithArguments() {
            public void answer(String cert) throws RemoteException {
                mSupplicantVariables.eapClientCert = cert;
            }
        }).when(mISupplicantStaNetworkMock).setEapClientCert(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapClientCert;
            }
        }).when(mISupplicantStaNetworkMock).getEapClientCert();

        /** EAP CA Cert */
        doAnswer(new AnswerWithArguments() {
            public void answer(String cert) throws RemoteException {
                mSupplicantVariables.eapCACert = cert;
            }
        }).when(mISupplicantStaNetworkMock).setEapCACert(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapCACert;
            }
        }).when(mISupplicantStaNetworkMock).getEapCACert();

        /** EAP Subject Match */
        doAnswer(new AnswerWithArguments() {
            public void answer(String match) throws RemoteException {
                mSupplicantVariables.eapSubjectMatch = match;
            }
        }).when(mISupplicantStaNetworkMock).setEapSubjectMatch(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapSubjectMatch;
            }
        }).when(mISupplicantStaNetworkMock).getEapSubjectMatch();

        /** EAP Engine */
        doAnswer(new AnswerWithArguments() {
            public void answer(boolean enable) throws RemoteException {
                mSupplicantVariables.eapEngine = enable;
            }
        }).when(mISupplicantStaNetworkMock).setEapEngine(any(boolean.class));
        doAnswer(new AnswerWithArguments() {
            public boolean answer() throws RemoteException {
                return mSupplicantVariables.eapEngine;
            }
        }).when(mISupplicantStaNetworkMock).getEapEngine();

        /** EAP Engine ID */
        doAnswer(new AnswerWithArguments() {
            public void answer(String id) throws RemoteException {
                mSupplicantVariables.eapEngineID = id;
            }
        }).when(mISupplicantStaNetworkMock).setEapEngineID(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapEngineID;
            }
        }).when(mISupplicantStaNetworkMock).getEapEngineId();

        /** EAP Private Key */
        doAnswer(new AnswerWithArguments() {
            public void answer(String key) throws RemoteException {
                mSupplicantVariables.eapPrivateKeyId = key;
            }
        }).when(mISupplicantStaNetworkMock).setEapPrivateKeyId(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapPrivateKeyId;
            }
        }).when(mISupplicantStaNetworkMock).getEapPrivateKeyId();

        /** EAP Alt Subject Match */
        doAnswer(new AnswerWithArguments() {
            public void answer(String match) throws RemoteException {
                mSupplicantVariables.eapAltSubjectMatch = match;
            }
        }).when(mISupplicantStaNetworkMock).setEapAltSubjectMatch(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapAltSubjectMatch;
            }
        }).when(mISupplicantStaNetworkMock).getEapAltSubjectMatch();

        /** EAP Domain Suffix Match */
        doAnswer(new AnswerWithArguments() {
            public void answer(String match) throws RemoteException {
                mSupplicantVariables.eapDomainSuffixMatch = match;
            }
        }).when(mISupplicantStaNetworkMock).setEapDomainSuffixMatch(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapDomainSuffixMatch;
            }
        }).when(mISupplicantStaNetworkMock).getEapDomainSuffixMatch();

        /** EAP CA Path*/
        doAnswer(new AnswerWithArguments() {
            public void answer(String path) throws RemoteException {
                mSupplicantVariables.eapCAPath = path;
            }
        }).when(mISupplicantStaNetworkMock).setEapCAPath(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.eapCAPath;
            }
        }).when(mISupplicantStaNetworkMock).getEapCAPath();

        /** EAP Proactive Key Caching */
        doAnswer(new AnswerWithArguments() {
            public void answer(boolean enable) throws RemoteException {
                mSupplicantVariables.eapProactiveKeyCaching = enable;
            }
        }).when(mISupplicantStaNetworkMock).setProactiveKeyCaching(any(boolean.class));

        /** Callback registration */
        doAnswer(new AnswerWithArguments() {
            public void answer(ISupplicantStaNetworkCallback cb) throws RemoteException {
                mISupplicantStaNetworkCallback = cb;
            }
        }).when(mISupplicantStaNetworkMock)
                .registerCallback(any(ISupplicantStaNetworkCallback.class));

        /** OCSP */
        doAnswer(new AnswerWithArguments() {
            public void answer(int ocsp) throws RemoteException {
                mSupplicantVariables.ocsp = ocsp;
            }
        }).when(mISupplicantStaNetworkMock).setOcsp(any(int.class));
        doAnswer(new AnswerWithArguments() {
            public int answer() throws RemoteException {
                return mSupplicantVariables.ocsp;
            }
        }).when(mISupplicantStaNetworkMock).getOcsp();

        /** PMK cache */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] serializedData) throws RemoteException {
                mSupplicantVariables.serializedPmkCache = serializedData;
            }
        }).when(mISupplicantStaNetworkMock).setPmkCache(any(byte[].class));

        /** WAPI Cert */
        doAnswer(new AnswerWithArguments() {
            public void answer(String cert) throws RemoteException {
                mSupplicantVariables.wapiCertSuite = cert;
            }
        }).when(mISupplicantStaNetworkMock).setWapiCertSuite(any(String.class));
        doAnswer(new AnswerWithArguments() {
            public String answer() throws RemoteException {
                return mSupplicantVariables.wapiCertSuite;
            }
        }).when(mISupplicantStaNetworkMock).getWapiCertSuite();

        /** EAP ERP */
        doAnswer(new AnswerWithArguments() {
            public void answer(boolean enable) throws RemoteException {
                mSupplicantVariables.eapErp = enable;
            }
        }).when(mISupplicantStaNetworkMock).setEapErp(any(boolean.class));

        /** setSaeH2eMode */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte mode) throws RemoteException {
                mSupplicantVariables.saeH2eMode = mode;
            }
        }).when(mISupplicantStaNetworkMock).setSaeH2eMode(any(byte.class));

        /** Selected RCOI */
        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] selectedRcoi) throws RemoteException {
                mSupplicantVariables.selectedRcoi = selectedRcoi;
            }
        }).when(mISupplicantStaNetworkMock).setRoamingConsortiumSelection(any(byte[].class));

        /** Vendor data */
        doAnswer(new AnswerWithArguments() {
            public void answer(OuiKeyedData[] vendorData) throws RemoteException {
                mSupplicantVariables.vendorData = vendorData;
            }
        }).when(mISupplicantStaNetworkMock).setVendorData(any(OuiKeyedData[].class));
    }

    // Private class to to store/inspect values set via the AIDL mock.
    private class SupplicantNetworkVariables {
        public byte[] ssid;
        public int networkId;
        public byte[/* 6 */] bssid;
        public int keyMgmtMask;
        public int protoMask;
        public int authAlgMask;
        public int groupCipherMask;
        public int pairwiseCipherMask;
        public int groupManagementCipherMask;
        public boolean scanSsid;
        public boolean requirePmf;
        public String idStr;
        public int updateIdentifier;
        public String pskPassphrase;
        public byte[] psk;
        public ArrayList<Byte>[] wepKey = new ArrayList[4];
        public int wepTxKeyIdx;
        public int eapMethod = -1;
        public int eapPhase2Method = -1;
        public byte[] eapIdentity;
        public byte[] eapAnonymousIdentity;
        public byte[] eapPassword;
        public String eapCACert;
        public String eapCAPath;
        public String eapClientCert;
        public String eapPrivateKeyId;
        public String eapSubjectMatch;
        public String eapAltSubjectMatch;
        public boolean eapEngine;
        public String eapEngineID;
        public String eapDomainSuffixMatch;
        public boolean eapProactiveKeyCaching;
        public int ocsp;
        public byte[] serializedPmkCache;
        public String wapiCertSuite;
        public boolean eapErp;
        public byte saeH2eMode;
        public byte[] selectedRcoi;
        public OuiKeyedData[] vendorData;
    }
}
