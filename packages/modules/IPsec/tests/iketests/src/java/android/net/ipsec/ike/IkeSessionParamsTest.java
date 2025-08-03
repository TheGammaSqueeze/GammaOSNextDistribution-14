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

package android.net.ipsec.test.ike;

import static android.net.ipsec.test.ike.IkeSessionParams.ESP_ENCAP_TYPE_NONE;
import static android.net.ipsec.test.ike.IkeSessionParams.ESP_ENCAP_TYPE_UDP;
import static android.net.ipsec.test.ike.IkeSessionParams.ESP_IP_VERSION_IPV4;
import static android.net.ipsec.test.ike.IkeSessionParams.ESP_IP_VERSION_IPV6;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_DPD_DELAY_SEC_DEFAULT;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_HARD_LIFETIME_SEC_DEFAULT;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_HARD_LIFETIME_SEC_MAXIMUM;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_HARD_LIFETIME_SEC_MINIMUM;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_ACCEPT_ANY_REMOTE_ID;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_EAP_ONLY_AUTH;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_INITIAL_CONTACT;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_RETRANS_TIMEOUT_MS_LIST_DEFAULT;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_SOFT_LIFETIME_SEC_DEFAULT;
import static android.net.ipsec.test.ike.IkeSessionParams.IkeAuthConfig;
import static android.net.ipsec.test.ike.IkeSessionParams.IkeAuthDigitalSignLocalConfig;
import static android.net.ipsec.test.ike.IkeSessionParams.IkeAuthDigitalSignRemoteConfig;
import static android.net.ipsec.test.ike.IkeSessionParams.IkeAuthEapConfig;
import static android.net.ipsec.test.ike.IkeSessionParams.IkeAuthPskConfig;
import static android.net.ipsec.test.ike.IkeSessionParams.LIVENESS_RETRANS_MAX_ATTEMPTS_MAX;
import static android.net.ipsec.test.ike.IkeSessionParams.LIVENESS_RETRANS_TIMEOUT_MS_LIST_DEFAULT;
import static android.net.ipsec.test.ike.IkeSessionParams.LIVENESS_RETRANS_TIMEOUT_MS_MAX;
import static android.net.ipsec.test.ike.IkeSessionParams.LIVENESS_RETRANS_TIMEOUT_MS_MIN;
import static android.net.ipsec.test.ike.IkeSessionParams.LIVENESS_RETRANS_TIMEOUT_MS_TOTAL;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;

import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_IP4_PCSCF;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_IP6_PCSCF;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.ConfigAttribute;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.Network;
import android.net.eap.test.EapSessionConfig;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppExtension;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppExtension.Ike3gppDataListener;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppParams;
import android.os.PersistableBundle;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import com.android.internal.net.TestUtils;
import com.android.internal.net.ipsec.test.ike.testutils.CertUtils;

import org.junit.Before;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.RSAKey;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class IkeSessionParamsTest {
    private static final int IKE_OPTION_INVALID = -1;
    private static final int SUB_ID = 0;

    private static final String PSK_HEX_STRING = "6A756E69706572313233";
    private static final byte[] PSK = TestUtils.hexStringToByteArray(PSK_HEX_STRING);

    private static final String LOCAL_IPV4_HOST_ADDRESS = "192.0.2.100";
    private static final String REMOTE_IPV4_HOST_ADDRESS = "192.0.2.100";
    private static final String REMOTE_HOSTNAME = "server.test.android.net";

    private static final Inet4Address LOCAL_IPV4_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress(LOCAL_IPV4_HOST_ADDRESS);
    private static final Inet4Address REMOTE_IPV4_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress(REMOTE_IPV4_HOST_ADDRESS);

    private static final Inet4Address PCSCF_IPV4_ADDRESS_1 =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.1");
    private static final Inet4Address PCSCF_IPV4_ADDRESS_2 =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.2");
    private static final Inet6Address PCSCF_IPV6_ADDRESS_1 =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:DB8::1");
    private static final Inet6Address PCSCF_IPV6_ADDRESS_2 =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:DB8::2");

    private static final String EAP_MSCHAP_V2_USERNAME = "username";
    private static final String EAP_MSCHAP_V2_PASSWORD = "password";

    private static final String DEVICE_IDENTITY_IMEI = "123456789123456";

    private Context mMockContext;
    private ConnectivityManager mMockConnectManager;
    private Network mMockDefaultNetwork;
    private Network mMockUserConfigNetwork;

    private IkeSaProposal mIkeSaProposal;
    private IkeIdentification mLocalIdentification;
    private IkeIdentification mRemoteIdentification;

    private interface TestRSAPrivateKey extends PrivateKey, RSAKey {}

    private X509Certificate mMockServerCaCert;
    private X509Certificate mMockClientEndCert;
    private TestRSAPrivateKey mMockRsaPrivateKey;

    @Before
    public void setUp() throws Exception {
        mMockContext = mock(Context.class);
        mMockConnectManager = mock(ConnectivityManager.class);
        mMockDefaultNetwork = mock(Network.class);
        mMockUserConfigNetwork = mock(Network.class);

        doReturn(Context.CONNECTIVITY_SERVICE)
                .when(mMockContext)
                .getSystemServiceName(ConnectivityManager.class);
        doReturn(mMockConnectManager)
                .when(mMockContext)
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        when(mMockConnectManager.getActiveNetwork()).thenReturn(mMockDefaultNetwork);

        mIkeSaProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_8,
                                SaProposal.KEY_LEN_AES_128)
                        .addPseudorandomFunction(SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC)
                        .addDhGroup(SaProposal.DH_GROUP_1024_BIT_MODP)
                        .build();
        mLocalIdentification = new IkeIpv4AddrIdentification(LOCAL_IPV4_ADDRESS);
        mRemoteIdentification = new IkeIpv4AddrIdentification(REMOTE_IPV4_ADDRESS);

        mMockServerCaCert = mock(X509Certificate.class);
        mMockClientEndCert = mock(X509Certificate.class);
        mMockRsaPrivateKey = mock(TestRSAPrivateKey.class);
    }

    private void verifyIkeParamsWithSeverIpAndDefaultValues(IkeSessionParams sessionParams) {
        verifyIkeSessionParamsCommon(sessionParams);

        assertEquals(REMOTE_IPV4_HOST_ADDRESS, sessionParams.getServerHostname());
        assertFalse(sessionParams.hasIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID));
        assertEquals(IKE_DPD_DELAY_SEC_DEFAULT, sessionParams.getDpdDelaySeconds());
        assertArrayEquals(
                IKE_RETRANS_TIMEOUT_MS_LIST_DEFAULT,
                sessionParams.getRetransmissionTimeoutsMillis());
    }

    private void verifyIkeSessionParamsCommon(IkeSessionParams sessionParams) {
        assertArrayEquals(
                new SaProposal[] {mIkeSaProposal}, sessionParams.getSaProposalsInternal());

        assertEquals(mLocalIdentification, sessionParams.getLocalIdentification());
        assertEquals(mRemoteIdentification, sessionParams.getRemoteIdentification());

        assertTrue(sessionParams.isIkeFragmentationSupported());
    }

    private void verifyAuthPskConfig(IkeSessionParams sessionParams) {
        IkeAuthConfig localConfig = sessionParams.getLocalAuthConfig();
        assertTrue(localConfig instanceof IkeAuthPskConfig);
        assertEquals(IkeSessionParams.IKE_AUTH_METHOD_PSK, localConfig.mAuthMethod);
        assertArrayEquals(PSK, ((IkeAuthPskConfig) localConfig).mPsk);

        IkeAuthConfig remoteConfig = sessionParams.getRemoteAuthConfig();
        assertTrue(remoteConfig instanceof IkeAuthPskConfig);
        assertEquals(IkeSessionParams.IKE_AUTH_METHOD_PSK, remoteConfig.mAuthMethod);
        assertArrayEquals(PSK, ((IkeAuthPskConfig) remoteConfig).mPsk);
    }

    private IkeSessionParams.Builder buildWithPskCommon(String hostname) {
        return new IkeSessionParams.Builder(mMockConnectManager)
                .setServerHostname(hostname)
                .addSaProposal(mIkeSaProposal)
                .setLocalIdentification(mLocalIdentification)
                .setRemoteIdentification(mRemoteIdentification)
                .setAuthPsk(PSK);
    }

    @Test
    public void testBuildWithPsk() throws Exception {
        IkeSessionParams sessionParams = buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS).build();

        verifyIkeParamsWithSeverIpAndDefaultValues(sessionParams);
        verifyAuthPskConfig(sessionParams);

        assertEquals(mMockDefaultNetwork, sessionParams.getNetwork());
        assertNull(sessionParams.getConfiguredNetwork());

        assertEquals(IKE_HARD_LIFETIME_SEC_DEFAULT, sessionParams.getHardLifetimeSeconds());
        assertEquals(IKE_SOFT_LIFETIME_SEC_DEFAULT, sessionParams.getSoftLifetimeSeconds());
    }

    @Test
    public void testBuildWithPskAndLifetime() throws Exception {
        int hardLifetimeSec = (int) TimeUnit.HOURS.toSeconds(20L);
        int softLifetimeSec = (int) TimeUnit.HOURS.toSeconds(10L);

        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setLifetimeSeconds(hardLifetimeSec, softLifetimeSec)
                        .build();

        verifyIkeParamsWithSeverIpAndDefaultValues(sessionParams);
        verifyAuthPskConfig(sessionParams);

        assertEquals(hardLifetimeSec, sessionParams.getHardLifetimeSeconds());
        assertEquals(softLifetimeSec, sessionParams.getSoftLifetimeSeconds());
    }

    @Test
    public void testBuildWithPskAndHostname() throws Exception {
        IkeSessionParams sessionParams = buildWithPskCommon(REMOTE_HOSTNAME).build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);

        assertEquals(REMOTE_HOSTNAME, sessionParams.getServerHostname());
    }

    @Test
    public void testAddIkeOption() throws Exception {
        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .addIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID)
                        .build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);

        assertTrue(sessionParams.hasIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID));
    }

    @Test
    public void testAddInitialContactIkeOption() throws Exception {
        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .addIkeOption(IKE_OPTION_INITIAL_CONTACT)
                        .build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);

        assertTrue(sessionParams.hasIkeOption(IKE_OPTION_INITIAL_CONTACT));
    }

    @Test
    public void testAddAndRemoveIkeOption() throws Exception {
        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .addIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID)
                        .removeIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID)
                        .build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);

        assertFalse(sessionParams.hasIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID));
    }

    @Test
    public void testAddAndRemoveInitialContactIkeOption() throws Exception {
        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .addIkeOption(IKE_OPTION_INITIAL_CONTACT)
                        .removeIkeOption(IKE_OPTION_INITIAL_CONTACT)
                        .build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);

        assertFalse(sessionParams.hasIkeOption(IKE_OPTION_INITIAL_CONTACT));
    }

    private IkeSessionParams.Builder createIkeParamsBuilderMinimum() {
        return new IkeSessionParams.Builder()
                .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                .addSaProposal(mIkeSaProposal)
                .setLocalIdentification(mLocalIdentification)
                .setRemoteIdentification(mRemoteIdentification)
                .setAuthPsk(PSK);
    }

    @Test
    public void testIkeSessionParamsEncodeDecodeIsLossLess() throws Exception {
        IkeSessionParams sessionParams = createIkeParamsBuilderMinimum().build();

        PersistableBundle bundle = sessionParams.toPersistableBundle();
        IkeSessionParams result = IkeSessionParams.fromPersistableBundle(bundle);

        assertEquals(sessionParams, result);
    }

    @Test(expected = IllegalStateException.class)
    public void testEncodeIkeSessionParamsWithConfiguredNetwork() throws Exception {
        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setNetwork(mMockUserConfigNetwork)
                        .build();

        PersistableBundle bundle = sessionParams.toPersistableBundle();
    }

    @Test(expected = IllegalStateException.class)
    public void testEncodeIkeSessionParamsWith3gppExtension() throws Exception {
        Ike3gppExtension ike3gppExtension =
                new Ike3gppExtension(
                        new Ike3gppParams.Builder().build(), mock(Ike3gppDataListener.class));

        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setIke3gppExtension(ike3gppExtension)
                        .build();

        PersistableBundle bundle = sessionParams.toPersistableBundle();
    }

    @Test
    public void testAddInvalidIkeOption() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager).addIkeOption(IKE_OPTION_INVALID);
            fail("Expected to fail due to invalid ikeOption");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testRemoveInvalidIkeOption() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager).removeIkeOption(IKE_OPTION_INVALID);
            fail("Expected to fail due to invalid ikeOption");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testCheckInvalidIkeOption() throws Exception {
        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .addIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID)
                        .build();

        try {
            sessionParams.hasIkeOption(IKE_OPTION_INVALID);
            fail("Expected to fail due to invalid ikeOption");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithPskAndDpdDelay() throws Exception {
        final int dpdDelaySec = 100;

        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setDpdDelaySeconds(dpdDelaySec)
                        .build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);
        assertEquals(REMOTE_IPV4_HOST_ADDRESS, sessionParams.getServerHostname());

        // Verify default values
        assertFalse(sessionParams.hasIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID));
        assertArrayEquals(
                IKE_RETRANS_TIMEOUT_MS_LIST_DEFAULT,
                sessionParams.getRetransmissionTimeoutsMillis());

        // Verify DPD delay
        assertEquals(dpdDelaySec, sessionParams.getDpdDelaySeconds());
    }

    @Test
    public void testBuildWithInvalidDpdDelay() throws Exception {
        final int invalidDpdDelay = 1;
        try {
            new IkeSessionParams.Builder(mMockConnectManager).setDpdDelaySeconds(invalidDpdDelay);
            fail("Expected to fail due to invalid DPD delay");
        } catch (IllegalArgumentException expected) {
        }
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testBuildWithInValidIpProtocol_v4ESP() throws Exception {
        buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                .setIpVersion(ESP_IP_VERSION_IPV4)
                .setEncapType(ESP_ENCAP_TYPE_NONE)
                .build();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testBuildWithInValidIpProtocol_v6UDP() throws Exception {
        buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                .setIpVersion(ESP_IP_VERSION_IPV6)
                .setEncapType(ESP_ENCAP_TYPE_UDP)
                .build();
    }

    @Test
    public void testBuildWithPskAndNattKeepaliveDelay() throws Exception {
        final int nattKeepaliveDelay = 100;

        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setNattKeepAliveDelaySeconds(nattKeepaliveDelay)
                        .build();

        // Verify NATT keepalive delay
        assertEquals(nattKeepaliveDelay, sessionParams.getNattKeepAliveDelaySeconds());
    }

    @Test
    public void testNattKeepaliveRange() {
        // SocketKeepalive#start is documented to require an interval between 10 and 3600 seconds.
        assertTrue(10 <= IkeSessionParams.IKE_NATT_KEEPALIVE_DELAY_SEC_MIN);
        assertTrue(3600 >= IkeSessionParams.IKE_NATT_KEEPALIVE_DELAY_SEC_MAX);
    }

    @Test
    public void testBuildWithPskAndDscp() throws Exception {
        final int dscp = 38;

        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS).setDscp(dscp).build();

        // Verify DSCP value
        assertEquals(dscp, sessionParams.getDscp());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetInvalidDscp() throws Exception {
        final int invalidDscp = 100;
        buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS).setDscp(invalidDscp).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithNattKeepaliveDelayTooShort() throws Exception {
        final int lowNattKeepaliveDelay = 1;
        new IkeSessionParams.Builder(mMockConnectManager)
                .setNattKeepAliveDelaySeconds(lowNattKeepaliveDelay);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithNattKeepaliveDelayTooLong() throws Exception {
        final int highNattKeepaliveDelay = 9999;
        new IkeSessionParams.Builder(mMockConnectManager)
                .setNattKeepAliveDelaySeconds(highNattKeepaliveDelay);
    }

    @Test
    public void testBuildWithPskAndRetransmission() throws Exception {
        final int[] retransmissionTimeoutList = new int[] {1000, 2000, 3000, 4000};

        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setRetransmissionTimeoutsMillis(retransmissionTimeoutList)
                        .build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);
        assertEquals(REMOTE_IPV4_HOST_ADDRESS, sessionParams.getServerHostname());

        // Verify default values
        assertEquals(IKE_DPD_DELAY_SEC_DEFAULT, sessionParams.getDpdDelaySeconds());
        assertFalse(sessionParams.hasIkeOption(IKE_OPTION_ACCEPT_ANY_REMOTE_ID));

        // Verify retransmission configuration
        assertEquals(retransmissionTimeoutList, sessionParams.getRetransmissionTimeoutsMillis());
    }

    @Test
    public void testBuildWithInvalidRetransmissionTimeout() throws Exception {
        final int[] invalidRetransTimeoutMsList = new int[] {1000, 2000, 3000, Integer.MAX_VALUE};
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setRetransmissionTimeoutsMillis(invalidRetransTimeoutMsList);
            fail("Expected to fail due to invalid retransmission timeout");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithInvalidMaxRetransmissionAttempts() throws Exception {
        final int[] invalidRetransTimeoutMsList = new int[20];
        for (int t : invalidRetransTimeoutMsList) t = 500;

        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setRetransmissionTimeoutsMillis(invalidRetransTimeoutMsList);
            fail("Expected to fail due to invalid max retransmission times");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithEap() throws Exception {
        EapSessionConfig eapConfig = mock(EapSessionConfig.class);

        IkeSessionParams sessionParams =
                new IkeSessionParams.Builder(mMockConnectManager)
                        .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                        .addSaProposal(mIkeSaProposal)
                        .setLocalIdentification(mLocalIdentification)
                        .setRemoteIdentification(mRemoteIdentification)
                        .setAuthEap(mMockServerCaCert, eapConfig)
                        .build();

        verifyIkeParamsWithSeverIpAndDefaultValues(sessionParams);
        assertEquals(mMockDefaultNetwork, sessionParams.getNetwork());
        assertNull(sessionParams.getConfiguredNetwork());

        IkeAuthConfig localConfig = sessionParams.getLocalAuthConfig();
        assertTrue(localConfig instanceof IkeAuthEapConfig);
        assertEquals(IkeSessionParams.IKE_AUTH_METHOD_EAP, localConfig.mAuthMethod);
        assertEquals(eapConfig, ((IkeAuthEapConfig) localConfig).mEapConfig);

        IkeAuthConfig remoteConfig = sessionParams.getRemoteAuthConfig();
        assertTrue(remoteConfig instanceof IkeAuthDigitalSignRemoteConfig);
        assertEquals(IkeSessionParams.IKE_AUTH_METHOD_PUB_KEY_SIGNATURE, remoteConfig.mAuthMethod);
        assertEquals(
                mMockServerCaCert,
                ((IkeAuthDigitalSignRemoteConfig) remoteConfig).mTrustAnchor.getTrustedCert());
    }

    @Test
    public void testBuildWithDigitalSignatureAuth() throws Exception {
        IkeSessionParams sessionParams =
                new IkeSessionParams.Builder(mMockConnectManager)
                        .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                        .setNetwork(mMockUserConfigNetwork)
                        .addSaProposal(mIkeSaProposal)
                        .setLocalIdentification(mLocalIdentification)
                        .setRemoteIdentification(mRemoteIdentification)
                        .setAuthDigitalSignature(
                                mMockServerCaCert, mMockClientEndCert, mMockRsaPrivateKey)
                        .build();

        verifyIkeParamsWithSeverIpAndDefaultValues(sessionParams);
        assertEquals(mMockUserConfigNetwork, sessionParams.getNetwork());
        assertEquals(mMockUserConfigNetwork, sessionParams.getConfiguredNetwork());

        IkeAuthConfig localConfig = sessionParams.getLocalAuthConfig();
        assertTrue(localConfig instanceof IkeAuthDigitalSignLocalConfig);

        IkeAuthDigitalSignLocalConfig localAuthConfig = (IkeAuthDigitalSignLocalConfig) localConfig;
        assertEquals(
                IkeSessionParams.IKE_AUTH_METHOD_PUB_KEY_SIGNATURE, localAuthConfig.mAuthMethod);
        assertEquals(mMockClientEndCert, localAuthConfig.mEndCert);
        assertTrue(localAuthConfig.mIntermediateCerts.isEmpty());
        assertEquals(mMockRsaPrivateKey, localAuthConfig.mPrivateKey);

        IkeAuthConfig remoteConfig = sessionParams.getRemoteAuthConfig();
        assertTrue(remoteConfig instanceof IkeAuthDigitalSignRemoteConfig);
        assertEquals(IkeSessionParams.IKE_AUTH_METHOD_PUB_KEY_SIGNATURE, remoteConfig.mAuthMethod);
        assertEquals(
                mMockServerCaCert,
                ((IkeAuthDigitalSignRemoteConfig) remoteConfig).mTrustAnchor.getTrustedCert());
    }

    @Test
    public void testBuildWithDsaDigitalSignatureAuth() throws Exception {
        try {
            IkeSessionParams sessionParams =
                    new IkeSessionParams.Builder(mMockConnectManager)
                            .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                            .addSaProposal(mIkeSaProposal)
                            .setLocalIdentification(mLocalIdentification)
                            .setRemoteIdentification(mRemoteIdentification)
                            .setAuthDigitalSignature(
                                    mMockServerCaCert,
                                    mMockClientEndCert,
                                    mock(DSAPrivateKey.class))
                            .build();
            fail("Expected to fail because DSA is not supported");
        } catch (IllegalArgumentException expected) {

        }
    }

    private void verifyAttrTypes(SparseArray expectedAttrCntMap, IkeSessionParams ikeParams) {
        ConfigAttribute[] configAttributes = ikeParams.getConfigurationAttributesInternal();

        SparseArray<Integer> atrrCntMap = expectedAttrCntMap.clone();

        for (int i = 0; i < configAttributes.length; i++) {
            int attType = configAttributes[i].attributeType;
            assertNotNull(atrrCntMap.get(attType));

            atrrCntMap.put(attType, atrrCntMap.get(attType) - 1);
            if (atrrCntMap.get(attType) == 0) atrrCntMap.remove(attType);
        }

        assertEquals(0, atrrCntMap.size());
    }

    @Test
    public void testBuildWithPcscfAddress() throws Exception {
        IkeSessionParams sessionParams =
                new IkeSessionParams.Builder(mMockConnectManager)
                        .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                        .addSaProposal(mIkeSaProposal)
                        .setLocalIdentification(mLocalIdentification)
                        .setRemoteIdentification(mRemoteIdentification)
                        .setAuthPsk(PSK)
                        .addPcscfServerRequest(AF_INET)
                        .addPcscfServerRequest(PCSCF_IPV4_ADDRESS_1)
                        .addPcscfServerRequest(PCSCF_IPV6_ADDRESS_2)
                        .addPcscfServerRequest(AF_INET6)
                        .addPcscfServerRequest(PCSCF_IPV4_ADDRESS_1)
                        .addPcscfServerRequest(PCSCF_IPV6_ADDRESS_2)
                        .build();

        SparseArray<Integer> expectedAttrCounts = new SparseArray<>();
        expectedAttrCounts.put(CONFIG_ATTR_IP4_PCSCF, 3);
        expectedAttrCounts.put(CONFIG_ATTR_IP6_PCSCF, 3);

        verifyAttrTypes(expectedAttrCounts, sessionParams);
    }

    @Test
    public void testBuildWithoutPcscfAddress() throws Exception {
        IkeSessionParams sessionParams =
                new IkeSessionParams.Builder(mMockConnectManager)
                        .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                        .addSaProposal(mIkeSaProposal)
                        .setLocalIdentification(mLocalIdentification)
                        .setRemoteIdentification(mRemoteIdentification)
                        .setAuthPsk(PSK)
                        .build();

        SparseArray<Integer> expectedAttrCounts = new SparseArray<>();

        verifyAttrTypes(expectedAttrCounts, sessionParams);
    }

    @Test
    public void testBuildWithoutSaProposal() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                    .build();
            fail("Expected to fail due to absence of SA proposal.");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithoutLocalId() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                    .addSaProposal(mIkeSaProposal)
                    .setRemoteIdentification(mRemoteIdentification)
                    .setAuthPsk(PSK)
                    .build();
            fail("Expected to fail because local identification is not set.");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithoutSetAuth() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                    .addSaProposal(mIkeSaProposal)
                    .setLocalIdentification(mLocalIdentification)
                    .setRemoteIdentification(mRemoteIdentification)
                    .build();
            fail("Expected to fail because authentiction method is not set.");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testNonAsciiFqdnAuthentication() throws Exception {
        try {
            new IkeFqdnIdentification("¯\\_(ツ)_/¯");
            fail("Expected failure based on non-ASCII characters.");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetHardLifetimeTooLong() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLifetimeSeconds(
                            IKE_HARD_LIFETIME_SEC_MAXIMUM + 1, IKE_SOFT_LIFETIME_SEC_DEFAULT);
            fail("Expected failure because hard lifetime is too long");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetHardLifetimeTooShort() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLifetimeSeconds(
                            IKE_HARD_LIFETIME_SEC_MINIMUM - 1, IKE_SOFT_LIFETIME_SEC_DEFAULT);
            fail("Expected failure because hard lifetime is too short");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetSoftLifetimeTooLong() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLifetimeSeconds(
                            IKE_HARD_LIFETIME_SEC_DEFAULT, IKE_HARD_LIFETIME_SEC_DEFAULT);
            fail("Expected failure because soft lifetime is too long");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetSoftLifetimeTooShort() throws Exception {
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLifetimeSeconds(IKE_HARD_LIFETIME_SEC_DEFAULT, 0);
            fail("Expected failure because soft lifetime is too short");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testExpceptionOnEapOnlyOptionWithoutEapAuth() throws Exception {
        try {
            IkeSessionParams sessionParams =
                    buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                            .addIkeOption(IKE_OPTION_EAP_ONLY_AUTH)
                            .build();
            fail("Expected failure because eap only option added without setting auth config");
        } catch (IllegalArgumentException expected) {

        }
    }

    IkeSessionParams buildIkeSessionParamsWithDeviceIdentity(EapSessionConfig eapSessionConfig) {
        Ike3gppParams ike3gppParams =
                new Ike3gppParams.Builder().setMobileDeviceIdentity(DEVICE_IDENTITY_IMEI).build();

        Ike3gppExtension ike3gppExtension =
                new Ike3gppExtension(ike3gppParams, mock(Ike3gppDataListener.class));

        return buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                .setAuthEap(null, eapSessionConfig)
                .setIke3gppExtension(ike3gppExtension)
                .build();
    }

    @Test
    public void testExpceptionOnDeviceIdentitySetWithoutEapAkaAuth() throws Exception {
        try {
            EapSessionConfig eapSessionConfig =
                    new EapSessionConfig.Builder()
                            .setEapMsChapV2Config(EAP_MSCHAP_V2_USERNAME, EAP_MSCHAP_V2_PASSWORD)
                            .build();

            IkeSessionParams sessionParams =
                    buildIkeSessionParamsWithDeviceIdentity(eapSessionConfig);

            fail("Expected failure because device identity is set and auth is not EAP AKA");
        } catch (IllegalArgumentException expected) {

        }
    }

    @Test
    public void testBuildWithDeviceIdentityandEapAkaAuth() throws Exception {
        EapSessionConfig eapSessionConfig =
                new EapSessionConfig.Builder()
                        .setEapAkaConfig(0, TelephonyManager.APPTYPE_ISIM)
                        .build();

        IkeSessionParams sessionParams = buildIkeSessionParamsWithDeviceIdentity(eapSessionConfig);

        assertNotNull(sessionParams.getIke3gppExtension());
    }

    @Test
    public void testExceptionOnEapOnlyOptionWithEapOnlyUnsafeMethod() throws Exception {
        try {
            EapSessionConfig eapSessionConfig =
                    new EapSessionConfig.Builder()
                            .setEapMsChapV2Config(EAP_MSCHAP_V2_USERNAME, EAP_MSCHAP_V2_PASSWORD)
                            .setEapAkaConfig(0, 0)
                            .build();

            IkeSessionParams sessionParams =
                    buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                            .addIkeOption(IKE_OPTION_EAP_ONLY_AUTH)
                            .setAuthEap(null, eapSessionConfig)
                            .build();
            fail("Expected failure because eap only option added without eap only safe method");
        } catch (IllegalArgumentException expected) {

        }
    }

    @Test
    public void testBuildWithEapOnlyOption() throws Exception {
        EapSessionConfig eapSessionConfig = new EapSessionConfig.Builder()
                .setEapAkaConfig(0, TelephonyManager.APPTYPE_ISIM)
                .build();

        IkeSessionParams sessionParams = buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                .addIkeOption(IKE_OPTION_EAP_ONLY_AUTH)
                .setAuthEap(null, eapSessionConfig)
                .build();

        assertNotNull(sessionParams);
        verifyIkeSessionParamsCommon(sessionParams);
        assertTrue(sessionParams.hasIkeOption(IKE_OPTION_EAP_ONLY_AUTH));
        IkeAuthConfig localConfig = sessionParams.getLocalAuthConfig();
        assertTrue(localConfig instanceof IkeAuthEapConfig);
    }

    @Test
    public void testBuildWithIke3gppExtension() throws Exception {
        Ike3gppExtension ike3gppExtension =
                new Ike3gppExtension(
                        new Ike3gppParams.Builder().build(), mock(Ike3gppDataListener.class));

        IkeSessionParams sessionParams =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setIke3gppExtension(ike3gppExtension)
                        .build();
        assertEquals(ike3gppExtension, sessionParams.getIke3gppExtension());
    }

    private static void verifyPersistableBundleEncodeDecodeIsLossless(IkeAuthConfig config) {
        PersistableBundle bundle = config.toPersistableBundle();
        IkeAuthConfig result = IkeAuthConfig.fromPersistableBundle(bundle);

        assertEquals(config, result);
    }

    @Test
    public void testPersistableBundleEncodeDecodePskAuth() {
        verifyPersistableBundleEncodeDecodeIsLossless(new IkeAuthPskConfig(PSK));
    }

    @Test
    public void testPersistableBundleEncodeDecodeAuthDigitalSignRemote() throws Exception {
        X509Certificate caCert = CertUtils.createCertFromPemFile("self-signed-ca-b.pem");
        verifyPersistableBundleEncodeDecodeIsLossless(new IkeAuthDigitalSignRemoteConfig(caCert));
    }

    @Test
    public void testPersistableBundleEncodeDecodeAuthDigitalSignRemoteWithoutCaCert()
            throws Exception {
        verifyPersistableBundleEncodeDecodeIsLossless(new IkeAuthDigitalSignRemoteConfig(null));
    }

    @Test
    public void testEqualsAuthConfigDigitalSignRemote() throws Exception {
        X509Certificate caCert = CertUtils.createCertFromPemFile("self-signed-ca-b.pem");
        assertEquals(
                new IkeAuthDigitalSignRemoteConfig(caCert),
                new IkeAuthDigitalSignRemoteConfig(caCert));
        assertEquals(
                new IkeAuthDigitalSignRemoteConfig(null), new IkeAuthDigitalSignRemoteConfig(null));
        assertNotEquals(
                new IkeAuthDigitalSignRemoteConfig(caCert),
                new IkeAuthDigitalSignRemoteConfig(null));
    }

    @Test
    public void testPersistableBundleEncodeDecodeAuthDigitalSignLocal() throws Exception {
        X509Certificate endCert = CertUtils.createCertFromPemFile("end-cert-b.pem");
        X509Certificate intermediateCertOne =
                CertUtils.createCertFromPemFile("intermediate-ca-b-one.pem");
        X509Certificate intermediateCertTwo =
                CertUtils.createCertFromPemFile("intermediate-ca-b-two.pem");
        PrivateKey key = CertUtils.createRsaPrivateKeyFromKeyFile("end-cert-key-a.key");

        verifyPersistableBundleEncodeDecodeIsLossless(
                new IkeAuthDigitalSignLocalConfig(
                        endCert, Arrays.asList(intermediateCertOne, intermediateCertTwo), key));
    }

    @Test
    public void testPersistableBundleEncodeDecodeAuthEap() {
        EapSessionConfig eapSessionConfig =
                new EapSessionConfig.Builder()
                        .setEapAkaConfig(SUB_ID, TelephonyManager.APPTYPE_ISIM)
                        .build();
        verifyPersistableBundleEncodeDecodeIsLossless(new IkeAuthEapConfig(eapSessionConfig));
    }

    @Test
    public void testConstructBuilderWithIkeSessionParams() throws Exception {
        IkeSessionParams sessionParams = createIkeParamsBuilderMinimum().build();

        IkeSessionParams result = new IkeSessionParams.Builder(sessionParams).build();
        assertEquals(sessionParams, result);
    }

    @Test
    public void testCreateCopyWithNetworkCleared() throws Exception {
        IkeSessionParams sessionParams =
                createIkeParamsBuilderMinimum().setNetwork(mMockUserConfigNetwork).build();

        IkeSessionParams result =
                new IkeSessionParams.Builder(sessionParams).setNetwork(null).build();
        assertNull(result.getConfiguredNetwork());
    }

    @Test
    public void testCreateWithAndWithoutConnectivityMgr() throws Exception {
        IkeSessionParams withConnectivityMgr =
                new IkeSessionParams.Builder(mMockConnectManager)
                        .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                        .addSaProposal(mIkeSaProposal)
                        .setLocalIdentification(mLocalIdentification)
                        .setRemoteIdentification(mRemoteIdentification)
                        .setAuthPsk(PSK)
                        .build();

        IkeSessionParams withoutConnectivityMgr =
                new IkeSessionParams.Builder()
                        .setServerHostname(REMOTE_IPV4_HOST_ADDRESS)
                        .addSaProposal(mIkeSaProposal)
                        .setLocalIdentification(mLocalIdentification)
                        .setRemoteIdentification(mRemoteIdentification)
                        .setAuthPsk(PSK)
                        .build();
        assertEquals(withConnectivityMgr, withoutConnectivityMgr);
    }

    @Test
    public void testCreateAndSetNetworkWithoutConnectivityMgr() throws Exception {
        IkeSessionParams sessionParams =
                createIkeParamsBuilderMinimum().setNetwork(mMockUserConfigNetwork).build();
        assertEquals(mMockUserConfigNetwork, sessionParams.getNetwork());
        assertEquals(mMockUserConfigNetwork, sessionParams.getConfiguredNetwork());
    }

    @Test
    public void testNotEqualsWhenNattKeepaliveDelaysAreDifferent() throws Exception {
        IkeSessionParams sessionParamsA =
                createIkeParamsBuilderMinimum().setNattKeepAliveDelaySeconds(100).build();
        IkeSessionParams sessionParamsB =
                createIkeParamsBuilderMinimum().setNattKeepAliveDelaySeconds(200).build();

        assertNotEquals(sessionParamsA, sessionParamsB);
    }

    @Test
    public void testNotEqualsWhenDscpsAreDifferent() throws Exception {
        IkeSessionParams sessionParamsA = createIkeParamsBuilderMinimum().setDscp(8).build();
        IkeSessionParams sessionParamsB = createIkeParamsBuilderMinimum().setDscp(48).build();

        assertNotEquals(sessionParamsA, sessionParamsB);
    }

    @Test
    public void testBuildWithPskAndLivenessRetransmission() throws Exception {
        final int[] retransmissionTimeoutList = new int[] {1000, 2000, 3000, 4000};
        final int[] livenessRetransmissionTimeoutList = new int[] {4000, 3000, 2000, 1000};

        IkeSessionParams.Builder builder =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setRetransmissionTimeoutsMillis(retransmissionTimeoutList);
        IkeSessionParams sessionParams = builder.build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);

        // Verify retransmission configuration
        assertEquals(retransmissionTimeoutList, sessionParams.getRetransmissionTimeoutsMillis());

        // Verify retransmission configuration
        assertArrayEquals(
                LIVENESS_RETRANS_TIMEOUT_MS_LIST_DEFAULT,
                sessionParams.getLivenessRetransmissionTimeoutsMillis());

        builder.setLivenessRetransmissionTimeoutsMillis(livenessRetransmissionTimeoutList);
        sessionParams = builder.build();

        verifyIkeSessionParamsCommon(sessionParams);
        verifyAuthPskConfig(sessionParams);

        // Verify retransmission configuration
        assertEquals(retransmissionTimeoutList, sessionParams.getRetransmissionTimeoutsMillis());

        // Verify retransmission configuration
        assertArrayEquals(
                livenessRetransmissionTimeoutList,
                sessionParams.getLivenessRetransmissionTimeoutsMillis());
    }

    @Test
    public void testBuildWithInvalidLivenessRetransmissionTimeout() throws Exception {
        final int[] invalidRetransTimeoutMsListMaxInt =
                new int[] {1000, 2000, 3000, Integer.MAX_VALUE};
        final int[] invalidRetransTimeoutMsListMoreThanMaxTimeout =
                new int[] {1000, 2000, 3000, LIVENESS_RETRANS_TIMEOUT_MS_MAX + 1};
        final int[] invalidRetransTimeoutMsListLessThanMinTimeout =
                new int[] {LIVENESS_RETRANS_TIMEOUT_MS_MIN - 1, 2000, 3000, 4000};
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLivenessRetransmissionTimeoutsMillis(invalidRetransTimeoutMsListMaxInt);
            fail("Expected to fail due to invalid retransmission timeout");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLivenessRetransmissionTimeoutsMillis(
                            invalidRetransTimeoutMsListMoreThanMaxTimeout);
            fail("Expected to fail due to invalid retransmission timeout");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLivenessRetransmissionTimeoutsMillis(
                            invalidRetransTimeoutMsListLessThanMinTimeout);
            fail("Expected to fail due to invalid retransmission timeout");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithInvalidMaxLivenessRetransmissionAttempts() throws Exception {
        final int[] validRetransTimeoutMsList = new int[LIVENESS_RETRANS_MAX_ATTEMPTS_MAX];
        final int[] invalidRetransTimeoutMsList = new int[LIVENESS_RETRANS_MAX_ATTEMPTS_MAX + 1];
        Arrays.fill(validRetransTimeoutMsList, LIVENESS_RETRANS_TIMEOUT_MS_MIN);
        Arrays.fill(invalidRetransTimeoutMsList, LIVENESS_RETRANS_TIMEOUT_MS_MIN);

        new IkeSessionParams.Builder(mMockConnectManager)
                .setLivenessRetransmissionTimeoutsMillis(validRetransTimeoutMsList);

        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLivenessRetransmissionTimeoutsMillis(invalidRetransTimeoutMsList);
            fail("Expected to fail due to invalid max retransmission times");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithInvalidTotalLivenessRetransmissionTimeouts() throws Exception {
        final int[] validTotalRetransTimeoutMsList = new int[LIVENESS_RETRANS_MAX_ATTEMPTS_MAX];
        final int[] invalidTotalRetransTimeoutMsList = new int[LIVENESS_RETRANS_MAX_ATTEMPTS_MAX];
        Arrays.fill(
                validTotalRetransTimeoutMsList,
                LIVENESS_RETRANS_TIMEOUT_MS_TOTAL / LIVENESS_RETRANS_MAX_ATTEMPTS_MAX);
        Arrays.fill(
                invalidTotalRetransTimeoutMsList,
                (LIVENESS_RETRANS_TIMEOUT_MS_TOTAL + LIVENESS_RETRANS_TIMEOUT_MS_MIN)
                        / (LIVENESS_RETRANS_MAX_ATTEMPTS_MAX));

        new IkeSessionParams.Builder(mMockConnectManager)
                .setLivenessRetransmissionTimeoutsMillis(validTotalRetransTimeoutMsList);

        try {
            new IkeSessionParams.Builder(mMockConnectManager)
                    .setLivenessRetransmissionTimeoutsMillis(invalidTotalRetransTimeoutMsList);
            fail("Expected to fail due to invalid total retransmission times");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildWithPacerableLivenessRetransmissionTimeouts() throws Exception {
        final int[] validRetransTimeoutMsList = new int[LIVENESS_RETRANS_MAX_ATTEMPTS_MAX];
        Arrays.fill(validRetransTimeoutMsList, LIVENESS_RETRANS_TIMEOUT_MS_MIN);

        IkeSessionParams paramsToBundle =
                buildWithPskCommon(REMOTE_IPV4_HOST_ADDRESS)
                        .setLivenessRetransmissionTimeoutsMillis(validRetransTimeoutMsList)
                        .build();

        IkeSessionParams paramsFromBundle =
                IkeSessionParams.fromPersistableBundle(paramsToBundle.toPersistableBundle());

        assertEquals(
                paramsToBundle.getLivenessRetransmissionTimeoutsMillis(),
                paramsFromBundle.getLivenessRetransmissionTimeoutsMillis());
    }
}
