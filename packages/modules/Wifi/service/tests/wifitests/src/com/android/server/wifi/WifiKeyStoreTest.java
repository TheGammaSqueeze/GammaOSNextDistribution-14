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

import static com.android.server.wifi.WifiConfigurationTestUtil.TEST_UID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.UserHandle;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConfigManager}.
 */
@SmallTest
public class WifiKeyStoreTest extends WifiBaseTest {
    @Mock private WifiEnterpriseConfig mWifiEnterpriseConfig;
    @Mock private WifiEnterpriseConfig mExistingWifiEnterpriseConfig;
    @Mock private KeyStore mKeyStore;
    @Mock private Context mContext;
    @Mock private FrameworkFacade mFrameworkFacade;

    private WifiKeyStore mWifiKeyStore;
    private static final String TEST_KEY_ID = "blah";
    private static final String USER_CERT_ALIAS = "aabbccddee";
    private static final String USER_CA_CERT_ALIAS = "aacccddd";
    private static final String USER_CA_CERT_ALIAS2 = "bbbccccaaa";
    private static final String [] USER_CA_CERT_ALIASES = {"aacccddd", "bbbccccaaa"};
    private static final String TEST_PACKAGE_NAME = "TestApp";
    private static final String KEYCHAIN_ALIAS = "kc-alias";
    private static final String KEYCHAIN_KEY_GRANT = "kc-grant";
    public static final UserHandle TEST_USER_HANDLE = UserHandle.getUserHandleForUid(TEST_UID);

    /**
     * Setup the mocks and an instance of WifiConfigManager before each test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiKeyStore = new WifiKeyStore(mContext, mKeyStore, mFrameworkFacade);

        when(mWifiEnterpriseConfig.getClientCertificateAlias()).thenReturn(USER_CERT_ALIAS);
        when(mWifiEnterpriseConfig.getCaCertificateAlias()).thenReturn(USER_CA_CERT_ALIAS);
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(USER_CA_CERT_ALIASES);
        when(mWifiEnterpriseConfig.getClientPrivateKey()).thenReturn(FakeKeys.RSA_KEY1);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(FakeKeys.CLIENT_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_CERT0);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[] {FakeKeys.CLIENT_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[] {FakeKeys.CA_CERT0});
        when(mWifiEnterpriseConfig.getKeyId(any())).thenReturn(TEST_KEY_ID);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Verifies that keys and certs are removed when they were installed by an app.
     */
    @Test
    public void testRemoveKeysForAppInstalledCerts() throws Exception {
        when(mWifiEnterpriseConfig.isAppInstalledDeviceKeyAndCert()).thenReturn(true);
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(true);
        mWifiKeyStore.removeKeys(mWifiEnterpriseConfig, false);

        // Method calls the KeyStore#delete method 4 times, user key, user cert, and 2 CA cert
        verify(mKeyStore).deleteEntry(USER_CERT_ALIAS);
        verify(mKeyStore).deleteEntry(USER_CA_CERT_ALIASES[0]);
    }

    /**
     * Verifies that keys and certs are removed when they were installed by an app and not removed
     * when CA certs are installed by the user.
     */
    @Test
    public void testRemoveKeysForMixedInstalledCerts1() throws Exception {
        when(mWifiEnterpriseConfig.isAppInstalledDeviceKeyAndCert()).thenReturn(true);
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(false);
        mWifiKeyStore.removeKeys(mWifiEnterpriseConfig, false);

        // Method calls the KeyStore#deleteEntry method: user key and user cert
        verify(mKeyStore).deleteEntry(USER_CERT_ALIAS);
        verifyNoMoreInteractions(mKeyStore);
    }

    /**
     * Verifies that keys and certs are not removed when they were installed by the user and
     * removed when CA certs are installed by the app.
     */
    @Test
    public void testRemoveKeysForMixedInstalledCerts2() throws Exception {
        when(mWifiEnterpriseConfig.isAppInstalledDeviceKeyAndCert()).thenReturn(false);
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(true);
        mWifiKeyStore.removeKeys(mWifiEnterpriseConfig, false);

        // Method calls the KeyStore#delete method 2 times: 2 CA certs
        verify(mKeyStore).deleteEntry(USER_CA_CERT_ALIASES[0]);
        verify(mKeyStore).deleteEntry(USER_CA_CERT_ALIASES[1]);
        verifyNoMoreInteractions(mKeyStore);
    }

    /**
     * Verifies that keys and certs are not removed when they were installed by the user.
     */
    @Test
    public void testRemoveKeysForUserInstalledCerts() {
        when(mWifiEnterpriseConfig.isAppInstalledDeviceKeyAndCert()).thenReturn(false);
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(false);
        mWifiKeyStore.removeKeys(mWifiEnterpriseConfig, false);
        verifyNoMoreInteractions(mKeyStore);
    }

    /**
     * Verifies that keys and certs are removed when they were not installed by the user
     * when forceRemove is true.
     */
    @Test
    public void testForceRemoveKeysForUserInstalledCerts() throws Exception {
        when(mWifiEnterpriseConfig.isAppInstalledDeviceKeyAndCert()).thenReturn(false);
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(false);
        mWifiKeyStore.removeKeys(mWifiEnterpriseConfig, true);

        // KeyStore#deleteEntry() is called three time for user cert, and 2 CA cert.
        verify(mKeyStore).deleteEntry(USER_CERT_ALIAS);
        verify(mKeyStore).deleteEntry(USER_CA_CERT_ALIASES[0]);
        verify(mKeyStore).deleteEntry(USER_CA_CERT_ALIASES[1]);
        verifyNoMoreInteractions(mKeyStore);
    }

    /**
     * Verifies that keys and certs are added when they were installed by an app and verifies the
     * alias used.
     */
    @Test
    public void testAddKeysForAppInstalledCerts() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        config.enterpriseConfig = mWifiEnterpriseConfig;
        assertTrue(mWifiKeyStore.updateNetworkKeys(config, null));

        String expectedAlias = config.getKeyIdForCredentials(null);
        String expectedCaAlias = expectedAlias + "_0";
        // Method calls the KeyStore#delete method 4 times, user key, user cert, and 2 CA cert
        verify(mKeyStore).setKeyEntry(
                eq(expectedAlias), eq(FakeKeys.RSA_KEY1), eq(null),
                aryEq(new X509Certificate[] {FakeKeys.CLIENT_CERT}));
        verify(mKeyStore).setCertificateEntry(eq(expectedCaAlias), eq(FakeKeys.CA_CERT0));
        verify(mWifiEnterpriseConfig).setClientCertificateAlias(eq(expectedAlias));
        verify(mWifiEnterpriseConfig).setCaCertificateAliases(
                aryEq(new String[] {expectedCaAlias}));
    }

    /**
     * Add two same network credential one is from user saved, the other is from suggestion.
     * Both oh them should be installed successfully and has different alias, and will not override
     * each other.
     */
    @Test
    public void testAddRemoveFromBothSavedAndSuggestionNetwork() throws Exception {
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration suggestionNetwork = new WifiConfiguration(savedNetwork);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        suggestionNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        suggestionNetwork.fromWifiNetworkSuggestion = true;
        suggestionNetwork.creatorName = TEST_PACKAGE_NAME;

        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
        assertTrue(mWifiKeyStore.updateNetworkKeys(suggestionNetwork, null));

        String savedNetworkAlias = savedNetwork.getKeyIdForCredentials(null);
        String savedNetworkCaAlias = savedNetworkAlias + "_0";

        String suggestionNetworkAlias = suggestionNetwork.getKeyIdForCredentials(null);
        String suggestionNetworkCaAlias = suggestionNetworkAlias + "_0";

        assertNotEquals(savedNetworkAlias, suggestionNetworkAlias);

        verify(mKeyStore).setKeyEntry(
                eq(savedNetworkAlias), eq(FakeKeys.RSA_KEY1), eq(null),
                aryEq(new X509Certificate[] {FakeKeys.CLIENT_CERT}));
        verify(mKeyStore).setCertificateEntry(eq(savedNetworkCaAlias), eq(FakeKeys.CA_CERT0));
        verify(mWifiEnterpriseConfig).setClientCertificateAlias(eq(savedNetworkAlias));
        verify(mWifiEnterpriseConfig).setCaCertificateAliases(
                aryEq(new String[] {savedNetworkCaAlias}));

        verify(mKeyStore).setKeyEntry(
                eq(suggestionNetworkAlias), eq(FakeKeys.RSA_KEY1), eq(null),
                aryEq(new X509Certificate[] {FakeKeys.CLIENT_CERT}));
        verify(mKeyStore).setCertificateEntry(eq(suggestionNetworkCaAlias), eq(FakeKeys.CA_CERT0));
        verify(mWifiEnterpriseConfig).setClientCertificateAlias(eq(suggestionNetworkAlias));
        verify(mWifiEnterpriseConfig).setCaCertificateAliases(
                aryEq(new String[] {suggestionNetworkCaAlias}));
    }

    @Test
    public void test_remove_empty_alias_enterprise_config() throws Exception {
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration suggestionNetwork = new WifiConfiguration(savedNetwork);
        suggestionNetwork.fromWifiNetworkSuggestion = true;
        suggestionNetwork.creatorName = TEST_PACKAGE_NAME;
        mWifiKeyStore.removeKeys(savedNetwork.enterpriseConfig, false);
        mWifiKeyStore.removeKeys(suggestionNetwork.enterpriseConfig, false);
        verify(mKeyStore, never()).deleteEntry(any());
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for RSA 3072 correctly when CA and client
     * certificates are of RSA 3072 type and the network is Suite-B.
     */
    @Test
    public void testConfigureSuiteBRsa3072() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
        assertTrue(savedNetwork.allowedSuiteBCiphers.get(WifiConfiguration.SuiteBCipher.ECDHE_RSA));
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for ECDSA correctly when CA and client
     * certificates are of ECDSA type and the network is Suite-B.
     */
    @Test
    public void testConfigureSuiteBEcdsa() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_ECC_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_ECDSA_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_ECDSA_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_ECDSA_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_ECDSA_CERT});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_ECDSA_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_ECDSA_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_ECDSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
        assertTrue(
                savedNetwork.allowedSuiteBCiphers.get(WifiConfiguration.SuiteBCipher.ECDHE_ECDSA));
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for RSA 3072 correctly when CA and client
     * certificates are of RSA 3072 type and the network is Suite-B.
     */
    @Test
    public void testConfigureSuiteBRsa3072UserAndTofu() throws Exception {
        // No Root CA certificates, but TOFU is on - Setting the suite-b mode by the user cert
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(null);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(null);
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(null);
        when(mWifiEnterpriseConfig.isTrustOnFirstUseEnabled()).thenReturn(true);

        when(mWifiEnterpriseConfig.isEapMethodServerCertUsed()).thenReturn(true);
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
        assertTrue(savedNetwork.allowedSuiteBCiphers.get(WifiConfiguration.SuiteBCipher.ECDHE_RSA));
    }


    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for RSA 3072 fails when CA and client
     * certificates are not of the same type.
     */
    @Test
    public void testConfigurationFailureSuiteB() throws Exception {
        // Create a configuration with RSA client cert and ECDSA CA cert
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_ECDSA_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_ECDSA_CERT});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_ECDSA_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_ECDSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertFalse(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for RSA 3072 fails when CA is RSA but not
     * with the required security
     */
    @Test
    public void testConfigurationFailureSuiteBNon3072Rsa() throws Exception {
        // Create a configuration with RSA client cert and weak RSA CA cert
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_CERT0);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_CERT0});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_CERT0);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertFalse(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for RSA 3072 fails when a client certificate
     * key length is less than 3072 bits
     */
    @Test
    public void testConfigurationFailureSuiteB2048Rsa() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_BAD_SUITE_B_RSA2048_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_BAD_SUITE_B_RSA2048_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_BAD_SUITE_B_RSA2048_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertFalse(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for RSA 3072 fails when one CA in the list
     * is RSA but not with the required security
     */
    @Test
    public void testConfigurationFailureSuiteBNon3072RsaInList() throws Exception {
        // Create a configuration with RSA client cert and weak RSA CA cert
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(
                        new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT, FakeKeys.CA_CERT0});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[1]))).thenReturn(
                FakeKeys.CA_CERT0);
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(USER_CA_CERT_ALIASES);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertFalse(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for RSA 3072 fails when one CA in the list
     * is RSA and the other is ECDSA
     */
    @Test
    public void testConfigurationFailureSuiteBRsaAndEcdsaInList() throws Exception {
        // Create a configuration with RSA client cert and weak RSA CA cert
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(
                        new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT,
                                FakeKeys.CA_SUITE_B_ECDSA_CERT});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[1]))).thenReturn(
                FakeKeys.CA_SUITE_B_ECDSA_CERT);
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(USER_CA_CERT_ALIASES);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertFalse(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
    }

    /**
     * Test configuring WPA3-Enterprise in 192-bit mode for ECDSA fails when a client
     * certificate key bit length is less than 384 bits.
     */
    @Test
    public void testConfigureFailureSuiteBEcdsa256() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_BAD_SUITE_B_ECC_256_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_BAD_SUITE_B_ECDSA_256_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_ECDSA_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_BAD_SUITE_B_ECDSA_256_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_ECDSA_CERT});
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_BAD_SUITE_B_ECDSA_256_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_ECDSA_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_ECDSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        assertFalse(mWifiKeyStore.updateNetworkKeys(savedNetwork, null));
    }
    /**
     * Test to confirm that old CA alias was removed only if the certificate was installed
     * by the app.
     */
    @Test
    public void testConfirmCaCertAliasRemoved() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(true);
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;

        mExistingWifiEnterpriseConfig = mWifiEnterpriseConfig;
        when(mExistingWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS2});
        WifiConfiguration existingNetwork = savedNetwork;
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[1]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        existingNetwork.enterpriseConfig = mExistingWifiEnterpriseConfig;

        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, existingNetwork));
        verify(mKeyStore).deleteEntry(eq(USER_CA_CERT_ALIAS2));
    }

    /**
     * Test to confirm that CA alias is not removed when the certificate was installed
     * by the app and an update is coming from manually editing the network from Settings.
     */
    @Test
    public void testConfirmCaCertAliasByAppNotRemoved() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(true);
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;
        mExistingWifiEnterpriseConfig = mWifiEnterpriseConfig;
        when(mExistingWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS2});
        WifiConfiguration existingNetwork = savedNetwork;
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[1]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        existingNetwork.enterpriseConfig = mExistingWifiEnterpriseConfig;
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(null);
        when(mWifiEnterpriseConfig.getCaCertificates()).thenReturn(null);
        when(mWifiEnterpriseConfig.getClientCertificateChain()).thenReturn(null);

        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, existingNetwork));
        verify(mKeyStore, never()).deleteEntry(anyString());
    }

    /**
     * Test to confirm that old CA alias was not removed when the certificate was not installed
     * by the app.
     */
    @Test
    public void testConfirmCaCertAliasNotRemoved() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.isAppInstalledCaCert()).thenReturn(false);
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;

        mExistingWifiEnterpriseConfig = mWifiEnterpriseConfig;
        when(mExistingWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS2});
        WifiConfiguration existingNetwork = savedNetwork;
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[1]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        existingNetwork.enterpriseConfig = mExistingWifiEnterpriseConfig;

        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, existingNetwork));
        verify(mKeyStore, never()).deleteEntry(eq(USER_CA_CERT_ALIAS2));
    }

    /**
     * Test to confirm that old client certificate alias was removed only if the certificate was
     * installed by the app.
     */
    @Test
    public void testConfirmClientCertAliasRemoved() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.isAppInstalledDeviceKeyAndCert()).thenReturn(true);
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;

        mExistingWifiEnterpriseConfig = mWifiEnterpriseConfig;
        when(mExistingWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS2});
        WifiConfiguration existingNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[1]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        existingNetwork.enterpriseConfig = mExistingWifiEnterpriseConfig;

        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, existingNetwork));
        verify(mKeyStore).deleteEntry(eq(existingNetwork.getKeyIdForCredentials(existingNetwork)));
    }

    /**
     * Test to confirm that old client certificate alias was not removed if the certificate was not
     * installed by the app.
     */
    @Test
    public void testConfirmClientCertAliasNotRemoved() throws Exception {
        when(mWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS});
        when(mWifiEnterpriseConfig.getClientPrivateKey())
                .thenReturn(FakeKeys.CLIENT_SUITE_B_RSA3072_KEY);
        when(mWifiEnterpriseConfig.getClientCertificate()).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getCaCertificate()).thenReturn(FakeKeys.CA_SUITE_B_RSA3072_CERT);
        when(mWifiEnterpriseConfig.getClientCertificateChain())
                .thenReturn(new X509Certificate[]{FakeKeys.CLIENT_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.getCaCertificates())
                .thenReturn(new X509Certificate[]{FakeKeys.CA_SUITE_B_RSA3072_CERT});
        when(mWifiEnterpriseConfig.isAppInstalledDeviceKeyAndCert()).thenReturn(false);
        when(mKeyStore.getCertificate(eq(USER_CERT_ALIAS))).thenReturn(
                FakeKeys.CLIENT_SUITE_B_RSA3072_CERT);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[0]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        WifiConfiguration savedNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        savedNetwork.enterpriseConfig = mWifiEnterpriseConfig;

        mExistingWifiEnterpriseConfig = mWifiEnterpriseConfig;
        when(mExistingWifiEnterpriseConfig.getCaCertificateAliases())
                .thenReturn(new String[]{USER_CA_CERT_ALIAS2});
        WifiConfiguration existingNetwork = WifiConfigurationTestUtil.createEapSuiteBNetwork(
                WifiConfiguration.SuiteBCipher.ECDHE_RSA);
        when(mKeyStore.getCertificate(eq(USER_CA_CERT_ALIASES[1]))).thenReturn(
                FakeKeys.CA_SUITE_B_RSA3072_CERT);
        existingNetwork.enterpriseConfig = mExistingWifiEnterpriseConfig;

        assertTrue(mWifiKeyStore.updateNetworkKeys(savedNetwork, existingNetwork));
        verify(mKeyStore, never()).deleteEntry(eq(USER_CERT_ALIAS));
    }

    @Test
    public void testUpdateKeysKeyChainAliasNotGranted() {
        assumeTrue(SdkLevel.isAtLeastS());

        final WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        when(mWifiEnterpriseConfig.getClientKeyPairAliasInternal()).thenReturn(KEYCHAIN_ALIAS);
        when(mFrameworkFacade.getWifiKeyGrantAsUser(
                any(Context.class), any(UserHandle.class), any(String.class))).thenReturn(null);
        config.enterpriseConfig = mWifiEnterpriseConfig;

        assertFalse(mWifiKeyStore.updateNetworkKeys(config, null));
    }

    @Test
    public void testUpdateKeysKeyChainAliasGranted() {
        assumeTrue(SdkLevel.isAtLeastS());

        final WifiConfiguration config = WifiConfigurationTestUtil.createEapNetwork();
        when(mWifiEnterpriseConfig.getClientKeyPairAliasInternal()).thenReturn(KEYCHAIN_ALIAS);
        when(mFrameworkFacade.getWifiKeyGrantAsUser(
                any(Context.class), eq(TEST_USER_HANDLE), eq(KEYCHAIN_ALIAS)))
                .thenReturn(KEYCHAIN_KEY_GRANT);
        config.enterpriseConfig = mWifiEnterpriseConfig;

        assertTrue(mWifiKeyStore.updateNetworkKeys(config, null));
        verify(mWifiEnterpriseConfig).setClientCertificateAlias(eq(KEYCHAIN_KEY_GRANT));
    }

    @Test
    public void testValidateKeyChainAliasNotGranted() {
        assumeTrue(SdkLevel.isAtLeastS());

        when(mFrameworkFacade.hasWifiKeyGrantAsUser(
                any(Context.class), any(UserHandle.class), any(String.class))).thenReturn(false);

        assertFalse(mWifiKeyStore.validateKeyChainAlias(KEYCHAIN_ALIAS, TEST_UID));
    }

    @Test
    public void testValidateKeyChainAliasEmpty() {
        assumeTrue(SdkLevel.isAtLeastS());

        when(mFrameworkFacade.hasWifiKeyGrantAsUser(
                any(Context.class), any(UserHandle.class), any(String.class))).thenReturn(true);

        assertFalse(mWifiKeyStore.validateKeyChainAlias("", TEST_UID));
    }

    @Test
    public void testValidateKeyChainAliasGranted() {
        assumeTrue(SdkLevel.isAtLeastS());

        when(mFrameworkFacade.hasWifiKeyGrantAsUser(
                any(Context.class), eq(TEST_USER_HANDLE), eq(KEYCHAIN_ALIAS))).thenReturn(true);

        assertTrue(mWifiKeyStore.validateKeyChainAlias(KEYCHAIN_ALIAS, TEST_UID));
    }
}
