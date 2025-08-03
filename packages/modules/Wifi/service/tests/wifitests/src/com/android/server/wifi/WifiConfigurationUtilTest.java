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

import static android.net.wifi.WifiEnterpriseConfig.OCSP_NONE;
import static android.net.wifi.WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS;
import static android.net.wifi.hotspot2.PasspointConfiguration.MAX_URL_BYTES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.pm.UserInfo;
import android.net.IpConfiguration;
import android.net.MacAddress;
import android.net.ProxyInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiScanner;
import android.os.PatternMatcher;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConfigurationUtil}.
 */
@SmallTest
public class WifiConfigurationUtilTest extends WifiBaseTest {
    static final int CURRENT_USER_ID = 0;
    static final int CURRENT_USER_MANAGED_PROFILE_USER_ID = 10;
    static final int OTHER_USER_ID = 11;
    static final int TEST_UID = 10000;
    static final String TEST_PACKAGE = "com.test";
    static final String TEST_SSID = "\"test_ssid\"";
    static final String TEST_SSID_1 = "\"test_ssid_1\"";
    static final String TEST_BSSID = "aa:aa:11:22:cc:dd";
    static final String TEST_BSSID_1 = "11:22:11:22:cc:dd";
    static final List<UserInfo> PROFILES = Arrays.asList(
            new UserInfo(CURRENT_USER_ID, "owner", 0),
            new UserInfo(CURRENT_USER_MANAGED_PROFILE_USER_ID, "managed profile", 0));
    private static final long SUPPORTED_FEATURES_ALL = Long.MAX_VALUE;
    private final String mGeneratedString256 = "a".repeat(256);

    private MockitoSession mSession;

    @Mock
    private WifiInjector mWifiInjector;
    @Mock
    private WifiGlobals mWifiGlobals;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // static mocking
        mSession = ExtendedMockito.mockitoSession().strictness(Strictness.LENIENT)
                .mockStatic(WifiInjector.class, withSettings().lenient())
                .spyStatic(MacAddress.class)
                .startMocking();
        when(WifiInjector.getInstance()).thenReturn(mWifiInjector);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
    }

    @After
    public void cleanUp() throws Exception {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     * Verify that new WifiEnterpriseConfig is detected.
     */
    @Test
    public void testEnterpriseConfigAdded() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(
                null, eapConfig.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig eap change is detected.
     */
    @Test
    public void testEnterpriseConfigEapChangeDetected() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS);
        EnterpriseConfig peapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.PEAP);

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                peapConfig.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig phase2 method change is detected.
     */
    @Test
    public void testEnterpriseConfigPhase2ChangeDetected() {
        EnterpriseConfig eapConfig =
                new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                        .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2);
        EnterpriseConfig papConfig =
                new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                        .setPhase2(WifiEnterpriseConfig.Phase2.PAP);

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                papConfig.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig added Certificate is detected.
     */
    @Test
    public void testCaCertificateAddedDetected() {
        EnterpriseConfig eapConfigNoCerts = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password");

        EnterpriseConfig eapConfig1Cert = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(
                eapConfigNoCerts.enterpriseConfig, eapConfig1Cert.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig Certificate change is detected.
     */
    @Test
    public void testDifferentCaCertificateDetected() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        EnterpriseConfig eapConfigNewCert = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT1});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                eapConfigNewCert.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig added Certificate changes are detected.
     */
    @Test
    public void testCaCertificateChangesDetected() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        EnterpriseConfig eapConfigAddedCert = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0, FakeKeys.CA_CERT1});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                eapConfigAddedCert.enterpriseConfig));
    }

    /**
     * Verify that WifiEnterpriseConfig does not detect changes for identical configs.
     */
    @Test
    public void testWifiEnterpriseConfigNoChanges() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0, FakeKeys.CA_CERT1});

        // Just to be clear that check is not against the same object
        EnterpriseConfig eapConfigSame = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0, FakeKeys.CA_CERT1});

        assertFalse(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                eapConfigSame.enterpriseConfig));
    }

    /**
     * Verify that the validate method successfully validates good WifiConfigurations with ASCII
     * values.
     */
    @Test
    public void testValidatePositiveCases_AsciiSsidString() {
        assertTrue(WifiConfigurationUtil.validate(
                WifiConfigurationTestUtil.createOpenNetwork(), SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                WifiConfigurationTestUtil.createPskNetwork(), SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                WifiConfigurationTestUtil.createWepNetwork(), SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                WifiConfigurationTestUtil.createEapNetwork(), SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                WifiConfigurationTestUtil.createOweNetwork(), SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                WifiConfigurationTestUtil.createSaeNetwork(), SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                WifiConfigurationTestUtil.createEapSuiteBNetwork(), SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method successfully validates good WifiConfigurations with hex
     * values.
     */
    @Test
    public void testValidatePositiveCases_HexSsidString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.SSID = "abcd1234555a";
        config.preSharedKey = "abcd123455151234556788990034556667332345667322344556676743233445";
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method validates WifiConfiguration with "any" in the BSSID field.
     */
    @Test
    public void testValidatePositiveCases_AnyBssidString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        config.BSSID = "any";
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                config, SUPPORTED_FEATURES_ALL, WifiConfigurationUtil.VALIDATE_FOR_UPDATE));
    }

    /**
     * Verify that the validate method validates WifiConfiguration with masked psk string only for
     * an update.
     */
    @Test
    public void testValidatePositiveCases_MaskedPskString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = WifiConfigurationUtil.PASSWORD_MASK;
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                config, SUPPORTED_FEATURES_ALL, WifiConfigurationUtil.VALIDATE_FOR_UPDATE));
    }

    /**
     * Verify that the validate method validates WifiConfiguration with null ssid only for an
     * update.
     */
    @Test
    public void testValidatePositiveCases_OnlyUpdateIgnoresNullSsid() {
        WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                config, SUPPORTED_FEATURES_ALL, WifiConfigurationUtil.VALIDATE_FOR_UPDATE));

    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with malformed ssid
     * string.
     */
    @Test
    public void testValidateNegativeCases_MalformedPlaintextSsidString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.SSID = "\"ab";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with a too-long ssid
     * string.
     */
    @Test
    public void testValidateNegativeCases_TooLongPlaintextSsidString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.SSID = "\"012345678901234567890123456789012\"";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad ssid length.
     */
    @Test
    public void testValidateNegativeCases_BadHexSsidLength() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.SSID = "abcdfe012345632423343543453456464545656464545646454ace34534545634535";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        config.SSID = "";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with malformed ssid
     * string.
     */
    @Test
    public void testValidateNegativeCases_MalformedHexSsidString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.SSID = "hello";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad psk length.
     */
    @Test
    public void testValidateNegativeCases_BadAsciiPskLength() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "\"abcdffeeretretyetreteteteabe34tetrertertrsraaaaaaaaaaa345eqwrweewq"
                + "weqe\"";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        config.preSharedKey = "\"454\"";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testValidateNegativeCases_BadAsciiPskLengthWapi() {
        WifiConfiguration config = WifiConfigurationTestUtil.createWapiPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "\"abcdffeeretretyetreteteteabe34tetrertertrsraaaaaaaaaaa345eqwrweewq"
                + "weqe\"";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        config.preSharedKey = "\"454\"";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad sae length.
     */
    @Test
    public void testValidateNegativeCases_BadAsciiSaeLength() {
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "\"abcdffeeretretyetreteteteabe34tetrertertrsraaaaaaaaaaa345eqwrweewq"
                + "weqe\"";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        config.preSharedKey = "\"\"";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with malformed psk
     * string.
     */
    @Test
    public void testValidateNegativeCases_MalformedAsciiPskString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "\"abcdfefeeretrety";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testValidateNegativeCases_MalformedAsciiPskStringWapi() {
        WifiConfiguration config = WifiConfigurationTestUtil.createWapiPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "\"abcdfefeeretrety";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with malformed sae
     * string.
     */
    @Test
    public void testValidateNegativeCases_MalformedAsciiSaeString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "\"abcdfefeeretrety";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad psk length.
     */
    @Test
    public void testValidateNegativeCases_BadHexPskLength() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "abcd123456788990013453445345465465476546";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        config.preSharedKey = "";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testValidateNegativeCases_BadHexPskLengthWapi() {
        WifiConfiguration config = WifiConfigurationTestUtil.createWapiPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "01234567890123456789012345678901234567890123456789012345678901234567"
                + "890123456789012345678901234567890";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        config.preSharedKey = "";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with malformed psk
     * string.
     */
    @Test
    public void testValidateNegativeCases_MalformedHexPskString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "adbdfgretrtyrtyrty";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testValidateNegativeCases_MalformedHexPskStringWapi() {
        WifiConfiguration config = WifiConfigurationTestUtil.createWapiPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "adbdfgretrtyrtyrty";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with malformed sae
     * string.
     */
    @Test
    public void testValidateNegativeCases_MalformedHexSaeString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createSaeNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.preSharedKey = "adbdfgretrtyrtyrty";
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method validates WifiConfiguration with masked wep key only for
     * an update.
     */
    @Test
    public void testValidatePositiveCases_MaskedWepKeysString() {
        WifiConfiguration config = WifiConfigurationTestUtil.createWepNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.wepKeys = new String[]{ WifiConfigurationUtil.PASSWORD_MASK,
                WifiConfigurationUtil.PASSWORD_MASK,
                WifiConfigurationUtil.PASSWORD_MASK,
                WifiConfigurationUtil.PASSWORD_MASK};
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(
                config, SUPPORTED_FEATURES_ALL, WifiConfigurationUtil.VALIDATE_FOR_UPDATE));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad wep length.
     */
    @Test
    public void testValidateNegativeCases_BadWepKeysLength() {
        WifiConfiguration config = WifiConfigurationTestUtil.createWepNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.wepKeys = new String[] {"\"abcd\""};
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        config.wepKeys = new String[] {"456"};
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        // Error scenario in b/169638868.
        config.wepKeys = new String[] {""};
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad wep tx key idx.
     */
    @Test
    public void testValidateNegativeCases_BadWepTxKeysIndex() {
        WifiConfiguration config = WifiConfigurationTestUtil.createWepNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        // Should be < wepKeys.length
        config.wepTxKeyIndex = config.wepKeys.length;
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad key mgmt values.
     */
    @Test
    public void testValidateNegativeCases_BadKeyMgmtPskEap() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad key mgmt values.
     */
    @Test
    public void testValidateNegativeCases_BadKeyMgmtOpenPsk() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad key mgmt values.
     */
    @Test
    public void testValidateNegativeCases_BadKeyMgmt() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad key mgmt values.
     */
    @Test
    public void testValidateNegativeCases_BadSuiteBKeyMgmt() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapSuiteBNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedKeyManagement.clear(WifiConfiguration.KeyMgmt.IEEE8021X);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad ipconfiguration
     * values.
     */
    @Test
    public void testValidateNegativeCases_BadIpconfiguration() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        IpConfiguration ipConfig =
                WifiConfigurationTestUtil.createStaticIpConfigurationWithPacProxy();
        config.setIpConfiguration(ipConfig);
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        ipConfig.setStaticIpConfiguration(null);
        config.setIpConfiguration(ipConfig);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad KeyMgmt value.
     */
    @Test
    public void testValidateNegativeCases_InvalidKeyMgmt() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.FT_EAP + 1);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad KeyMgmt value.
     */
    @Test
    public void testValidateNegativeCases_InvalidKeyMgmtWithPreSharedKey() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OSEN);
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        // Verify we reset the KeyMgmt
        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK));
        assertFalse(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.OSEN));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad Protocol value.
     */
    @Test
    public void testValidateNegativeCases_InvalidProtocol() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedProtocols.set(4);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad AuthAlgorithm
     * value.
     */
    @Test
    public void testValidateNegativeCases_InvalidAuthAlgorithm() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.LEAP + 3);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad GroupCipher
     * value.
     */
    @Test
    public void testValidateNegativeCases_InvalidGroupCipher() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.GTK_NOT_USED + 4);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with bad PairwiseCipher
     * value.
     */
    @Test
    public void testValidateNegativeCases_InvalidPairwiseCipher() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP + 4);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method successfully validates good WifiNetworkSpecifier with
     * only ssid pattern set.
     */
    @Test
    public void testValidateNetworkSpecifierPositiveCases_SsidPattern() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL),
                Pair.create(WifiManager.ALL_ZEROS_MAC_ADDRESS, WifiManager.ALL_ZEROS_MAC_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertTrue(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method successfully validates good WifiNetworkSpecifier with
     * only bssid pattern set.
     */
    @Test
    public void testValidateNetworkSpecifierPositiveCases_BssidPattern() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(".*", PatternMatcher.PATTERN_SIMPLE_GLOB),
                Pair.create(MacAddress.fromString(TEST_BSSID), MacAddress.BROADCAST_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertTrue(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method successfully validates good WifiNetworkSpecifier with
     * both ssid & bssid patterns set.
     */
    @Test
    public void testValidateNetworkSpecifierPositiveCases_BothSsidPatternAndBssidPattern() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL),
                Pair.create(MacAddress.fromString(TEST_BSSID), MacAddress.BROADCAST_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertTrue(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method validates a WifiNetworkSpecifier that specifies ssid, bssid,
     * and band. Note that such requests will currently still be rejected by WifiNetworkFactory, but
     * requesting specific bands may be supported in future releases.
     */
    @Test
    public void testValidateNetworkSpecifierPositiveCases_SsidPatternAndBssidPatternAndBand() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL),
                Pair.create(MacAddress.fromString(TEST_BSSID), MacAddress.BROADCAST_ADDRESS),
                ScanResult.WIFI_BAND_5_GHZ,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertTrue(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }


    /**
     * Verify that the validate method fails to validate WifiNetworkSpecifier with no
     * ssid/bssid info.
     */
    @Test
    public void testValidateNetworkSpecifierNegativeCases_NoSsidBssid() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(".*", PatternMatcher.PATTERN_SIMPLE_GLOB),
                Pair.create(WifiManager.ALL_ZEROS_MAC_ADDRESS, WifiManager.ALL_ZEROS_MAC_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertFalse(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method fails to validate WifiNetworkSpecifier with invalid SSID
     * match pattern.
     */
    @Test
    public void testValidateNetworkSpecifierNegativeCases_MatchNoneSsidPattern() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher("", PatternMatcher.PATTERN_LITERAL),
                Pair.create(WifiManager.ALL_ZEROS_MAC_ADDRESS, WifiManager.ALL_ZEROS_MAC_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertFalse(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method fails to validate WifiNetworkSpecifier with illegal
     * pattern.
     */
    @Test
    public void testValidateNetworkSpecifierNegativeCases_MatchNoneBssidPattern() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL),
                Pair.create(MacAddress.BROADCAST_ADDRESS, MacAddress.BROADCAST_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertFalse(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method fails to validate WifiNetworkSpecifier with illegal
     * pattern.
     */
    @Test
    public void testValidateNetworkSpecifierNegativeCases_InvalidBssidPattern() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL),
                Pair.create(MacAddress.fromString(TEST_BSSID), WifiManager.ALL_ZEROS_MAC_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertFalse(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method fails to validate WifiNetworkSpecifier with SSID pattern
     * for hidden network.
     */
    @Test
    public void testValidateNetworkSpecifierNegativeCases_NoSsidPatternForHiddenNetwork() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_PREFIX),
                Pair.create(WifiManager.ALL_ZEROS_MAC_ADDRESS, WifiManager.ALL_ZEROS_MAC_ADDRESS),
                ScanResult.UNSPECIFIED,
                WifiConfigurationTestUtil.createOpenHiddenNetwork(), new int[0]);
        assertFalse(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify that the validate method fails to validate WifiNetworkSpecifier with an invalid band.
     */
    @Test
    public void testValidateNetworkSpecifierNegativeCases_InvalidBand() {
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier(
                new PatternMatcher(TEST_SSID, PatternMatcher.PATTERN_LITERAL),
                Pair.create(MacAddress.fromString(TEST_BSSID), MacAddress.BROADCAST_ADDRESS),
                42,  // invalid
                WifiConfigurationTestUtil.createOpenNetwork(), new int[0]);
        assertFalse(WifiConfigurationUtil.validateNetworkSpecifier(specifier, 5));
    }

    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for an open network using {@link WifiConfigurationUtil#createPnoNetwork(
     * WifiConfiguration)}.
     */
    @Test
    public void testCreatePnoNetworkWithOpenNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_OPEN, pnoNetwork.authBitField);
    }

    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for an open hidden network using {@link WifiConfigurationUtil#createPnoNetwork(
     * WifiConfiguration)}.
     */
    @Test
    public void testCreatePnoNetworkWithOpenHiddenNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_DIRECTED_SCAN, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_OPEN, pnoNetwork.authBitField);
    }

    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for a PSK network using {@link WifiConfigurationUtil#createPnoNetwork(WifiConfiguration)
     * }.
     */
    @Test
    public void testCreatePnoNetworkWithPskNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_PSK, pnoNetwork.authBitField);
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns true when two WifiConfiguration
     * objects have the same parameters.
     */
    @Test
    public void testIsSameNetworkReturnsTrueOnSameNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        assertTrue(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns true when two WifiConfiguration
     * objects have the same parameters but different network selection BSSID's.
     */
    @Test
    public void testIsSameNetworkReturnsTrueOnSameNetworkWithDifferentBSSID() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        network.getNetworkSelectionStatus().setNetworkSelectionBSSID(TEST_BSSID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        network1.getNetworkSelectionStatus().setNetworkSelectionBSSID(TEST_BSSID_1);
        assertTrue(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns false when two WifiConfiguration
     * objects have the different SSIDs.
     */
    @Test
    public void testIsSameNetworkReturnsFalseOnDifferentSSID() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID_1);
        assertFalse(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns false when two WifiConfiguration
     * objects have the different security type.
     */
    @Test
    public void testIsSameNetworkReturnsFalseOnDifferentSecurityType() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        assertFalse(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns false when two WifiConfiguration
     * objects have the different EAP identity.
     */
    @Test
    public void testIsSameNetworkReturnsFalseOnDifferentEapIdentity() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        network1.enterpriseConfig.setIdentity("Identity1");
        network2.enterpriseConfig.setIdentity("Identity2");
        assertFalse(WifiConfigurationUtil.isSameNetwork(network1, network2));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns false when two WifiConfiguration
     * objects have the different EAP anonymous identity.
     */
    @Test
    public void testIsSameNetworkReturnsFalseOnDifferentEapAnonymousIdentity() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        network1.enterpriseConfig.setAnonymousIdentity("Identity1");
        network2.enterpriseConfig.setAnonymousIdentity("Identity2");
        assertFalse(WifiConfigurationUtil.isSameNetwork(network1, network2));
    }

    /**
     * Verify WifiConfigurationUtil.isSameNetwork return vale when two WifiConfiguration
     * objects have the different EAP anonymous(pseudonym) identity in EAP-SIM.
     */
    @Test
    public void testIsSameNetworkReturnValueOnDifferentEapAnonymousIdentityInEapSim() {
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        network1.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        network2.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        network1.enterpriseConfig.setAnonymousIdentity("Identity1");
        network2.enterpriseConfig.setAnonymousIdentity("Identity2");
        if (SdkLevel.isAtLeastT()) {
            assertTrue(WifiConfigurationUtil.isSameNetwork(network1, network2));
        } else {
            assertFalse(WifiConfigurationUtil.isSameNetwork(network1, network2));
        }
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns true when two WifiConfiguration
     * objects have the same candidate security params.
     */
    @Test
    public void testIsSameNetworkReturnsTrueOnSameNetworkWithSameCandidateSecurityParams() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        network.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        network1.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        assertTrue(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns false when two WifiConfiguration
     * objects have the different candidate security params.
     */
    @Test
    public void testIsSameNetworkReturnsTrueOnSameNetworkWithDifferentCandidateSecurityParams() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        network.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        network1.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE));
        assertFalse(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for a EAP network using {@link WifiConfigurationUtil#createPnoNetwork(WifiConfiguration)
     * }.
     */
    @Test
    public void testCreatePnoNetworkWithEapNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_EAPOL, pnoNetwork.authBitField);
    }

    /**
     * Verify that the generalized
     * {@link com.android.server.wifi.WifiConfigurationUtil.WifiConfigurationComparator}
     * can be used to sort a List given a 'compareNetworkWithSameStatus' method.
     */
    @Test
    public void testPnoListComparator() {
        List<WifiConfiguration> networks = new ArrayList<>();
        final WifiConfiguration enabledNetwork1 = WifiConfigurationTestUtil.createEapNetwork();
        enabledNetwork1.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLED);
        final WifiConfiguration enabledNetwork2 = WifiConfigurationTestUtil.createEapNetwork();
        enabledNetwork2.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLED);
        final WifiConfiguration tempDisabledNetwork1 = WifiConfigurationTestUtil.createEapNetwork();
        tempDisabledNetwork1.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        final WifiConfiguration tempDisabledNetwork2 = WifiConfigurationTestUtil.createEapNetwork();
        tempDisabledNetwork2.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        WifiConfiguration permDisabledNetwork = WifiConfigurationTestUtil.createEapNetwork();
        permDisabledNetwork.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);

        // Add all the networks to the list.
        networks.add(tempDisabledNetwork1);
        networks.add(enabledNetwork1);
        networks.add(permDisabledNetwork);
        networks.add(tempDisabledNetwork2);
        networks.add(enabledNetwork2);

        // Prefer |enabledNetwork1| over |enabledNetwork2| and |tempDisabledNetwork1| over
        // |tempDisabledNetwork2|.
        WifiConfigurationUtil.WifiConfigurationComparator comparator =
                new WifiConfigurationUtil.WifiConfigurationComparator() {
                    @Override
                    public int compareNetworksWithSameStatus(
                            WifiConfiguration a, WifiConfiguration b) {
                        if (a == enabledNetwork1 && b == enabledNetwork2) {
                            return -1;
                        } else if (b == enabledNetwork1 && a == enabledNetwork2) {
                            return 1;
                        } else if (a == tempDisabledNetwork1 && b == tempDisabledNetwork2) {
                            return -1;
                        } else if (b == tempDisabledNetwork1 && a == tempDisabledNetwork2) {
                            return 1;
                        }
                        return 0;
                    }
                };
        Collections.sort(networks, comparator);

        // Now ensure that the networks were sorted correctly.
        assertEquals(enabledNetwork1, networks.get(0));
        assertEquals(enabledNetwork2, networks.get(1));
        assertEquals(tempDisabledNetwork1, networks.get(2));
        assertEquals(tempDisabledNetwork2, networks.get(3));
        assertEquals(permDisabledNetwork, networks.get(4));
    }

    /**
     * Verifies that when the existing configuration is null and macRandomizationSetting in the
     * newConfig is the default value, then hasMacRandomizationSettingsChanged returns false.
     */
    @Test
    public void testHasMacRandomizationSettingsChangedNullExistingConfigDefaultNewConfig() {
        WifiConfiguration newConfig = new WifiConfiguration();
        assertFalse(WifiConfigurationUtil.hasMacRandomizationSettingsChanged(null, newConfig));
    }

    /**
     * Verifies that when the existing configuration is null and macRandomizationSetting in the
     * newConfig is not the default value, then hasMacRandomizationSettingsChanged returns true.
     */
    @Test
    public void testHasMacRandomizationSettingsChangedNullExistingConfigModifiedNewConfig() {
        WifiConfiguration newConfig = new WifiConfiguration();
        newConfig.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        assertTrue(WifiConfigurationUtil.hasMacRandomizationSettingsChanged(null, newConfig));
    }

    /**
     * Verifies that when macRandomizationSetting in the newConfig is different from existingConfig
     * hasMacRandomizationSettingsChanged returns true.
     */
    @Test
    public void testHasMacRandomizationSettingsChangedFieldsDifferent() {
        WifiConfiguration existingConfig = new WifiConfiguration();
        WifiConfiguration newConfig = new WifiConfiguration();
        newConfig.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        assertTrue(WifiConfigurationUtil.hasMacRandomizationSettingsChanged(
                existingConfig, newConfig));
    }

    /**
     * Verifies that when macRandomizationSetting in the newConfig is the same as existingConfig
     * hasMacRandomizationSettingsChanged returns false.
     */
    @Test
    public void testHasMacRandomizationSettingsChangedFieldsSame() {
        WifiConfiguration existingConfig = new WifiConfiguration();
        existingConfig.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        WifiConfiguration newConfig = new WifiConfiguration();
        newConfig.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        assertFalse(WifiConfigurationUtil.hasMacRandomizationSettingsChanged(
                existingConfig, newConfig));
    }

    /**
     * Verifies that isConfigForEnterpriseNetwork() returns true for SECURITY_TYPE_EAP,
     * SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT, SECURITY_TYPE_PASSPOINT_R1_R2 and
     * SECURITY_TYPE_PASSPOINT_R3 WiFi configurations.
     */
    @Test
    public void testisConfigForEnterpriseNetworkReturnTrue() {
        WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        assertTrue(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        assertTrue(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT);
        assertTrue(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PASSPOINT_R1_R2);
        assertTrue(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PASSPOINT_R3);
        assertTrue(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));
    }

    /**
     * Verifies that isConfigForEnterpriseNetwork() returns false for SECURITY_TYPE_OPEN,
     * SECURITY_TYPE_PSK, SECURITY_TYPE_OWE and SECURITY_TYPE_SAE WiFi configurations.
     */
    @Test
    public void testisConfigForEnterpriseNetworkReturnFalse() {
        WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        assertFalse(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        assertFalse(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
        assertFalse(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        assertFalse(WifiConfigurationUtil.isConfigForEnterpriseNetwork(config));
    }

    private static class EnterpriseConfig {
        public String eap;
        public String phase2;
        public String identity;
        public String password;
        public X509Certificate[] caCerts;
        public WifiEnterpriseConfig enterpriseConfig;
        public String wapiCertSuite;

        EnterpriseConfig(int eapMethod) {
            enterpriseConfig = new WifiEnterpriseConfig();
            enterpriseConfig.setEapMethod(eapMethod);
            eap = WifiEnterpriseConfig.Eap.strings[eapMethod];
        }

        public EnterpriseConfig setPhase2(int phase2Method) {
            enterpriseConfig.setPhase2Method(phase2Method);
            phase2 = "auth=" + WifiEnterpriseConfig.Phase2.strings[phase2Method];
            return this;
        }

        public EnterpriseConfig setIdentity(String identity, String password) {
            enterpriseConfig.setIdentity(identity);
            enterpriseConfig.setPassword(password);
            this.identity = identity;
            this.password = password;
            return this;
        }

        public EnterpriseConfig setCaCerts(X509Certificate[] certs) {
            enterpriseConfig.setCaCertificates(certs);
            caCerts = certs;
            return this;
        }

        public EnterpriseConfig setWapiCertSuite(String certSuite) {
            enterpriseConfig.setWapiCertSuite(certSuite);
            wapiCertSuite = certSuite;
            return this;
        }
    }

    /**
     * Verify WifiEnterpriseConfig CA Certificate alias changes are detected.
     */
    @Test
    public void testCaCertificateAliasChangesDetected() {
        EnterpriseConfig eapConfig1 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password");
        eapConfig1.enterpriseConfig.setCaCertificateAlias("ALIAS_1");

        EnterpriseConfig eapConfig2 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password");
        eapConfig2.enterpriseConfig.setCaCertificateAlias("ALIAS_2");

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig1.enterpriseConfig,
                eapConfig2.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig Client Certificate alias changes are detected.
     */
    @Test
    public void testClientCertificateAliasChangesDetected() {
        EnterpriseConfig eapConfig1 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TLS);
        eapConfig1.enterpriseConfig.setCaCertificateAlias("ALIAS_1");
        eapConfig1.enterpriseConfig.setClientCertificateAlias("CLIENT_ALIAS_1");

        EnterpriseConfig eapConfig2 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS);
        eapConfig2.enterpriseConfig.setCaCertificateAlias("ALIAS_1");
        eapConfig2.enterpriseConfig.setClientCertificateAlias("CLIENT_ALIAS_2");

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig1.enterpriseConfig,
                eapConfig2.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig OCSP changes are detected.
     */
    @Test
    public void testOcspChangesDetected() {
        EnterpriseConfig eapConfig1 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});
        eapConfig1.enterpriseConfig.setOcsp(OCSP_NONE);

        EnterpriseConfig eapConfig2 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});
        eapConfig2.enterpriseConfig.setOcsp(OCSP_REQUIRE_CERT_STATUS);

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig1.enterpriseConfig,
                eapConfig2.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig Domain changes are detected.
     */
    @Test
    public void testDomainNameChangesDetected() {
        EnterpriseConfig eapConfig1 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TLS);
        eapConfig1.enterpriseConfig.setDomainSuffixMatch("wlan.android.com");

        EnterpriseConfig eapConfig2 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TLS);
        eapConfig1.enterpriseConfig.setDomainSuffixMatch("wifi.android.com");

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig1.enterpriseConfig,
                eapConfig2.enterpriseConfig));
    }

    /** Verify WifiEnterpriseConfig Minimum TLS Version changes are detected. */
    @Test
    public void testMinimumTlsVersionChangesDetected() {
        EnterpriseConfig eapConfig1 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TLS);
        eapConfig1.enterpriseConfig.setMinimumTlsVersion(WifiEnterpriseConfig.TLS_V1_3);

        EnterpriseConfig eapConfig2 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TLS);
        eapConfig2.enterpriseConfig.setMinimumTlsVersion(WifiEnterpriseConfig.TLS_V1_0);

        assertTrue(
                WifiConfigurationUtil.hasEnterpriseConfigChanged(
                        eapConfig1.enterpriseConfig, eapConfig2.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig subject match changes are detected.
     */
    @Test
    public void testSubjectMatchChangesDetected() {
        EnterpriseConfig eapConfig1 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});
        eapConfig1.enterpriseConfig.setAltSubjectMatch("domain1.com");

        EnterpriseConfig eapConfig2 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});
        eapConfig1.enterpriseConfig.setAltSubjectMatch("domain2.com");

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig1.enterpriseConfig,
                eapConfig2.enterpriseConfig));
    }

    /**
     * Verify that new WifiEnterpriseConfig is detected.
     */
    @Test
    public void testEnterpriseConfigWapiCertChanged() {
        EnterpriseConfig eapConfig1 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.WAPI_CERT)
                .setWapiCertSuite("WapiCertSuite1");
        EnterpriseConfig eapConfig2 = new EnterpriseConfig(WifiEnterpriseConfig.Eap.WAPI_CERT)
                .setWapiCertSuite("WapiCertSuite2");

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(
                eapConfig1.enterpriseConfig, eapConfig2.enterpriseConfig));
    }

    /**
     * Verify that a WAPI config is not considered an OPEN config.
     */
    @Test
    public void testWapiConfigNotOpenConfig() {
        WifiConfiguration wapiPskConfig = new WifiConfiguration();
        wapiPskConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WAPI_CERT);
        assertFalse(WifiConfigurationUtil.isConfigForOpenNetwork(wapiPskConfig));

        WifiConfiguration wapiCertConfig = new WifiConfiguration();
        wapiCertConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WAPI_CERT);
        assertFalse(WifiConfigurationUtil.isConfigForOpenNetwork(wapiCertConfig));
    }

    /**
     * Verify that a Passpoint config is not considered an OPEN config.
     */
    @Test
    public void testPasspointConfigNotOpenConfig() {
        WifiConfiguration passpointR1R2Config = new WifiConfiguration();
        passpointR1R2Config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PASSPOINT_R1_R2);
        assertFalse(WifiConfigurationUtil.isConfigForOpenNetwork(passpointR1R2Config));

        WifiConfiguration passpointR3Config = new WifiConfiguration();
        passpointR3Config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PASSPOINT_R3);
        assertFalse(WifiConfigurationUtil.isConfigForOpenNetwork(passpointR3Config));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with malformed
     * enterprise configuration
     */
    @Test
    public void testValidateNegativeCases_MalformedEnterpriseConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"someNetwork\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        // EAP method is kept as Eap.NONE - should not crash, but return invalid ID
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with enterprise
     * configuration that is missing the identity and/or password.
     */
    @Test
    public void testValidateNegativeCases_NoIdentityOrPasswordEnterpriseConfig() {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setIdentity(null);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_UPDATE));

        config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig.setPassword(null);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_UPDATE));

        config = WifiConfigurationTestUtil.createWpa3EnterpriseNetwork(TEST_SSID);
        config.enterpriseConfig.setIdentity(null);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_UPDATE));

        config = WifiConfigurationTestUtil.createWpa3EnterpriseNetwork(TEST_SSID);
        config.enterpriseConfig.setPassword(null);
        assertFalse(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(config, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_UPDATE));
    }

    /**
     * Verify that the validate method fails to validate WifiConfiguration with DPP AKM
     * security type if DPP AKM is not supported by the device, and succeeds if it is supported.
     */
    @Test
    public void testValidateSecurityTypeDppAkm() {
        long supportedFeatures = SUPPORTED_FEATURES_ALL & ~WifiManager.WIFI_FEATURE_DPP_AKM;
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.setSecurityParams(WifiInfo.SECURITY_TYPE_DPP);

        assertFalse(WifiConfigurationUtil.validate(config, supportedFeatures,
                  WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertFalse(WifiConfigurationUtil.validate(config, supportedFeatures,
                  WifiConfigurationUtil.VALIDATE_FOR_UPDATE));

        supportedFeatures = WifiManager.WIFI_FEATURE_DPP_AKM;
        assertTrue(WifiConfigurationUtil.validate(config, supportedFeatures,
                  WifiConfigurationUtil.VALIDATE_FOR_ADD));
        assertTrue(WifiConfigurationUtil.validate(config, supportedFeatures,
                  WifiConfigurationUtil.VALIDATE_FOR_UPDATE));
    }

    @Test
    public void testConvertMultiTypeConfigsToLegacyConfigs() {
        assumeTrue(SdkLevel.isAtLeastS());
        List<WifiConfiguration> expectedResult = new ArrayList<>();
        List<WifiConfiguration> multiTypeConfigs = new ArrayList<>();

        // Genreate configurations
        WifiConfiguration pskSaeConfig = new WifiConfiguration();
        pskSaeConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        // PSK is disabled in this configuration.
        pskSaeConfig.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_PSK, false);
        pskSaeConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);

        WifiConfiguration openOweConfig = new WifiConfiguration();
        openOweConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        openOweConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);

        WifiConfiguration pskConfig = new WifiConfiguration();
        pskConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        pskConfig.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_PSK, false);
        pskConfig.getNetworkSelectionStatus().setNetworkSelectionDisableReason(
                WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_TRANSITION_DISABLE_INDICATION);
        pskConfig.getNetworkSelectionStatus().setDisableReasonCounter(
                WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_TRANSITION_DISABLE_INDICATION, 1);

        WifiConfiguration saeConfig = new WifiConfiguration();
        saeConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);

        WifiConfiguration openConfig = new WifiConfiguration();
        openConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);

        WifiConfiguration oweConfig = new WifiConfiguration();
        oweConfig.addSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);

        // Prepare the input
        multiTypeConfigs.add(pskSaeConfig);
        multiTypeConfigs.add(openOweConfig);

        // Prepare expected results
        expectedResult.clear();
        expectedResult.add(pskConfig);
        expectedResult.add(saeConfig);
        expectedResult.add(openConfig);
        expectedResult.add(oweConfig);
        List<WifiConfiguration> singleTypeConfigsWithDisabledType =
                WifiConfigurationUtil.convertMultiTypeConfigsToLegacyConfigs(
                        multiTypeConfigs, false);
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedResult, singleTypeConfigsWithDisabledType);

        // Prepare expected results
        expectedResult.clear();
        expectedResult.add(saeConfig);
        expectedResult.add(openConfig);
        expectedResult.add(oweConfig);
        List<WifiConfiguration> singleTypeConfigsWithoutDisabledType =
                WifiConfigurationUtil.convertMultiTypeConfigsToLegacyConfigs(
                        multiTypeConfigs, true);
        WifiConfigurationTestUtil.assertConfigurationsEqualForBackup(
                expectedResult, singleTypeConfigsWithoutDisabledType);
    }

    @Test
    public void testIsConfigLinkable() throws Exception {
        WifiConfiguration openConfig = WifiConfigurationTestUtil.createOpenNetwork();

        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();

        WifiConfiguration pskDisabledConfig = WifiConfigurationTestUtil.createPskNetwork();
        pskDisabledConfig.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_PSK, false);

        WifiConfiguration saeConfig = WifiConfigurationTestUtil.createSaeNetwork();

        WifiConfiguration saeDisabledConfig = WifiConfigurationTestUtil.createSaeNetwork();
        saeDisabledConfig.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_SAE, false);

        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(false);
        assertFalse(WifiConfigurationUtil.isConfigLinkable(openConfig));
        assertTrue(WifiConfigurationUtil.isConfigLinkable(pskConfig));
        assertFalse(WifiConfigurationUtil.isConfigLinkable(pskDisabledConfig));
        assertFalse(WifiConfigurationUtil.isConfigLinkable(saeConfig));
        assertFalse(WifiConfigurationUtil.isConfigLinkable(saeDisabledConfig));

        when(mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);
        assertFalse(WifiConfigurationUtil.isConfigLinkable(openConfig));
        assertTrue(WifiConfigurationUtil.isConfigLinkable(pskConfig));
        assertFalse(WifiConfigurationUtil.isConfigLinkable(pskDisabledConfig));
        assertTrue(WifiConfigurationUtil.isConfigLinkable(saeConfig));
        assertFalse(WifiConfigurationUtil.isConfigLinkable(saeDisabledConfig));
    }

    @Test
    public void testWepKeyOnNonWepConfig() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        pskConfig.wepKeys = new String[4];
        pskConfig.wepKeys[0] = mGeneratedString256;
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testInvalidFqdnAndFriendlyName() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();

        pskConfig.FQDN = mGeneratedString256;
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));

        pskConfig.FQDN = null;
        pskConfig.providerFriendlyName = mGeneratedString256;
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testInvalidDhcpAndGtw() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        pskConfig.dhcpServer = TEST_BSSID;
        pskConfig.defaultGwMacAddress = TEST_BSSID;
        assertTrue(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        pskConfig.dhcpServer = mGeneratedString256;
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
        pskConfig.dhcpServer = TEST_BSSID;
        pskConfig.defaultGwMacAddress = mGeneratedString256;
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testInvalidSecurityParameter() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        List<SecurityParams> securityParamsList = new ArrayList<>();
        securityParamsList.add(SecurityParams.createSecurityParamsBySecurityType(
                WifiConfiguration.SECURITY_TYPE_PSK));
        securityParamsList.add(SecurityParams.createSecurityParamsBySecurityType(
                WifiConfiguration.SECURITY_TYPE_PSK));

        pskConfig.setSecurityParams(securityParamsList);
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testInvalidUserConnectChoice() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        String generatedString513 = "a".repeat(513);
        pskConfig.getNetworkSelectionStatus().setConnectChoice(generatedString513);

        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testInvalidDppConfig() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        String generatedString = "a".repeat(MAX_URL_BYTES + 1);
        pskConfig.setDppConfigurator(generatedString.getBytes(StandardCharsets.UTF_8));
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }

    @Test
    public void testInvalidStaticIpConfig() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        IpConfiguration ipConfig =
                WifiConfigurationTestUtil.createStaticIpConfigurationWithPacProxy();
        ipConfig.getStaticIpConfiguration().domains = "a".repeat(513);
        pskConfig.setIpConfiguration(ipConfig);
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }
    @Test
    public void testInvalidProxyInfo() {
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        IpConfiguration ipConfig =
                WifiConfigurationTestUtil.createStaticIpConfigurationWithStaticProxy();
        ProxyInfo proxyInfo = ProxyInfo.buildDirectProxy(ipConfig.getHttpProxy().getHost(),
                ipConfig.getHttpProxy().getPort(), List.of("a".repeat(513)));
        ipConfig.setHttpProxy(proxyInfo);
        pskConfig.setIpConfiguration(ipConfig);
        assertFalse(WifiConfigurationUtil.validate(pskConfig, SUPPORTED_FEATURES_ALL,
                WifiConfigurationUtil.VALIDATE_FOR_ADD));
    }
}
