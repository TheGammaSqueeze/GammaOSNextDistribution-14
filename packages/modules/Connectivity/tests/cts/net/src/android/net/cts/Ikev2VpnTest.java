/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.cts;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.modules.utils.build.SdkLevel.isAtLeastU;
import static com.android.modules.utils.build.SdkLevel.isAtLeastT;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.annotation.NonNull;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Ikev2VpnProfile;
import android.net.IpSecAlgorithm;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.ProxyInfo;
import android.net.TestNetworkInterface;
import android.net.VpnManager;
import android.net.cts.util.CtsNetUtils;
import android.net.cts.util.IkeSessionTestUtils;
import android.net.ipsec.ike.IkeTunnelConnectionParams;
import android.os.Build;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;

import com.android.internal.util.HexDump;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.VpnManagerShimImpl;
import com.android.networkstack.apishim.common.VpnManagerShim;
import com.android.networkstack.apishim.common.VpnProfileStateShim;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.RecorderCallback.CallbackEntry;
import com.android.testutils.TestableNetworkCallback;

import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

@RunWith(DevSdkIgnoreRunner.class)
@IgnoreUpTo(Build.VERSION_CODES.Q)
@AppModeFull(reason = "Appops state changes disallowed for instant apps (OP_ACTIVATE_PLATFORM_VPN)")
public class Ikev2VpnTest {
    private static final String TAG = Ikev2VpnTest.class.getSimpleName();

    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    // Test vectors for IKE negotiation in test mode.
    private static final String SUCCESSFUL_IKE_INIT_RESP_V4 =
            "46b8eca1e0d72a18b2b5d9006d47a0022120222000000000000002d0220000300000002c01010004030000"
                    + "0c0100000c800e0100030000080300000c030000080200000400000008040000102800020800"
                    + "100000b8070f159fe5141d8754ca86f72ecc28d66f514927e96cbe9eec0adb42bf2c276a0ab7"
                    + "a97fa93555f4be9218c14e7f286bb28c6b4fb13825a420f2ffc165854f200bab37d69c8963d4"
                    + "0acb831d983163aa50622fd35c182efe882cf54d6106222abcfaa597255d302f1b95ab71c142"
                    + "c279ea5839a180070bff73f9d03fab815f0d5ee2adec7e409d1e35979f8bd92ffd8aab13d1a0"
                    + "0657d816643ae767e9ae84d2ccfa2bcce1a50572be8d3748ae4863c41ae90da16271e014270f"
                    + "77edd5cd2e3299f3ab27d7203f93d770bacf816041cdcecd0f9af249033979da4369cb242dd9"
                    + "6d172e60513ff3db02de63e50eb7d7f596ada55d7946cad0af0669d1f3e2804846ab3f2a930d"
                    + "df56f7f025f25c25ada694e6231abbb87ee8cfd072c8481dc0b0f6b083fdc3bd89b080e49feb"
                    + "0288eef6fdf8a26ee2fc564a11e7385215cf2deaf2a9965638fc279c908ccdf04094988d91a2"
                    + "464b4a8c0326533aff5119ed79ecbd9d99a218b44f506a5eb09351e67da86698b4c58718db25"
                    + "d55f426fb4c76471b27a41fbce00777bc233c7f6e842e39146f466826de94f564cad8b92bfbe"
                    + "87c99c4c7973ec5f1eea8795e7da82819753aa7c4fcfdab77066c56b939330c4b0d354c23f83"
                    + "ea82fa7a64c4b108f1188379ea0eb4918ee009d804100e6bf118771b9058d42141c847d5ec37"
                    + "6e5ec591c71fc9dac01063c2bd31f9c783b28bf1182900002430f3d5de3449462b31dd28bc27"
                    + "297b6ad169bccce4f66c5399c6e0be9120166f2900001c0000400428b8df2e66f69c8584a186"
                    + "c5eac66783551d49b72900001c000040054e7a622e802d5cbfb96d5f30a6e433994370173529"
                    + "0000080000402e290000100000402f00020003000400050000000800004014";
    private static final String SUCCESSFUL_IKE_INIT_RESP_V6 =
            "46b8eca1e0d72a1800d9ea1babce26bf2120222000000000000002d0220000300000002c01010004030000"
                    + "0c0100000c800e0100030000080300000c030000080200000400000008040000102800020800"
                    + "100000ea0e6dd9ca5930a9a45c323a41f64bfd8cdef7730f5fbff37d7c377da427f489a42aa8"
                    + "c89233380e6e925990d49de35c2cdcf63a61302c731a4b3569df1ee1bf2457e55a6751838ede"
                    + "abb75cc63ba5c9e4355e8e784f383a5efe8a44727dc14aeaf8dacc2620fb1c8875416dc07739"
                    + "7fe4decc1bd514a9c7d270cf21fd734c63a25c34b30b68686e54e8a198f37f27cb491fe27235"
                    + "fab5476b036d875ccab9a68d65fbf3006197f9bebbf94de0d3802b4fafe1d48d931ce3a1a346"
                    + "2d65bd639e9bd7fa46299650a9dbaf9b324e40b466942d91a59f41ef8042f8474c4850ed0f63"
                    + "e9238949d41cd8bbaea9aefdb65443a6405792839563aa5dc5c36b5ce8326ccf8a94d9622b85"
                    + "038d390d5fc0299e14e1f022966d4ac66515f6108ca04faec44821fe5bbf2ed4f84ff5671219"
                    + "608cb4c36b44a31ba010c9088f8d5ff943bb9ff857f74be1755f57a5783874adc57f42bb174e"
                    + "4ad3215de628707014dbcb1707bd214658118fdd7a42b3e1638b991ce5b812a667f1145be811"
                    + "685e3cd3baf9b18d062657b64c206a4d19a531c252a6a51a04aeaf42c618620cdbab65baca23"
                    + "82c57ed888422aeaacf7f1bc3fe2247ff7e7eaca218b74d7b31d02f2b0afa123f802529e7e6c"
                    + "3259d418290740ddbf55686e26998d7edcbbf895664972fed666f2f20af40503aa2af436ec6d"
                    + "4ec981ab19b9088755d94ae7a7c2066ea331d4e56e290000243fefe5555fce552d57a84e682c"
                    + "d4a6dfb3f2f94a94464d5bec3d88b88e9559642900001c00004004eb4afff764e7b79bca78b1"
                    + "3a89100d36d678ae982900001c00004005d177216a3c26f782076e12570d40bfaaa148822929"
                    + "0000080000402e290000100000402f00020003000400050000000800004014";
    private static final String SUCCESSFUL_IKE_AUTH_RESP_V4 =
            "46b8eca1e0d72a18b2b5d9006d47a0022e20232000000001000000e0240000c420a2500a3da4c66fa6929e"
                    + "600f36349ba0e38de14f78a3ad0416cba8c058735712a3d3f9a0a6ed36de09b5e9e02697e7c4"
                    + "2d210ac86cfbd709503cfa51e2eab8cfdc6427d136313c072968f6506a546eb5927164200592"
                    + "6e36a16ee994e63f029432a67bc7d37ca619e1bd6e1678df14853067ecf816b48b81e8746069"
                    + "406363e5aa55f13cb2afda9dbebee94256c29d630b17dd7f1ee52351f92b6e1c3d8551c513f1"
                    + "d74ac52a80b2041397e109fe0aeb3c105b0d4be0ae343a943398764281";
    private static final String SUCCESSFUL_IKE_AUTH_RESP_V6 =
            "46b8eca1e0d72a1800d9ea1babce26bf2e20232000000001000000f0240000d4aaf6eaa6c06b50447e6f54"
                    + "827fd8a9d9d6ac8015c1ebb3e8cb03fc6e54b49a107441f50004027cc5021600828026367f03"
                    + "bc425821cd7772ee98637361300c9b76056e874fea2bd4a17212370b291894264d8c023a01d1"
                    + "c3b691fd4b7c0b534e8c95af4c4638e2d125cb21c6267e2507cd745d72e8da109c47b9259c6c"
                    + "57a26f6bc5b337b9b9496d54bdde0333d7a32e6e1335c9ee730c3ecd607a8689aa7b0577b74f"
                    + "3bf437696a9fd5fc0aee3ed346cd9e15d1dda293df89eb388a8719388a60ca7625754de12cdb"
                    + "efe4c886c5c401";
    private static final long IKE_INITIATOR_SPI = Long.parseLong("46B8ECA1E0D72A18", 16);

    private static final InetAddress LOCAL_OUTER_4 = InetAddress.parseNumericAddress("192.0.2.1");
    private static final InetAddress LOCAL_OUTER_6 =
            InetAddress.parseNumericAddress("2001:db8::1");

    private static final int IP4_PREFIX_LEN = 32;
    private static final int IP6_PREFIX_LEN = 128;

    // TODO: Use IPv6 address when we can generate test vectors (GCE does not allow IPv6 yet).
    private static final String TEST_SERVER_ADDR_V4 = "192.0.2.2";
    private static final String TEST_SERVER_ADDR_V6 = "2001:db8::2";
    private static final String TEST_IDENTITY = "client.cts.android.com";
    private static final List<String> TEST_ALLOWED_ALGORITHMS =
            Arrays.asList(IpSecAlgorithm.AUTH_CRYPT_AES_GCM);

    private static final ProxyInfo TEST_PROXY_INFO =
            ProxyInfo.buildDirectProxy("proxy.cts.android.com", 1234);
    private static final int TEST_MTU = 1300;

    private static final byte[] TEST_PSK = "ikeAndroidPsk".getBytes();
    private static final String TEST_USER = "username";
    private static final String TEST_PASSWORD = "pa55w0rd";

    // Static state to reduce setup/teardown
    private static final Context sContext = InstrumentationRegistry.getContext();
    private static final ConnectivityManager sCM =
            (ConnectivityManager) sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    private static final VpnManager sVpnMgr =
            (VpnManager) sContext.getSystemService(Context.VPN_MANAGEMENT_SERVICE);
    private static final CtsNetUtils mCtsNetUtils = new CtsNetUtils(sContext);
    private static final long TIMEOUT_MS = 15_000;

    private VpnManagerShim mVmShim = VpnManagerShimImpl.newInstance(sContext);

    private final X509Certificate mServerRootCa;
    private final CertificateAndKey mUserCertKey;
    private final List<TestableNetworkCallback> mCallbacksToUnregister = new ArrayList<>();

    public Ikev2VpnTest() throws Exception {
        // Build certificates
        mServerRootCa = generateRandomCertAndKeyPair().cert;
        mUserCertKey = generateRandomCertAndKeyPair();
    }

    @Before
    public void setUp() {
        assumeFalse("Skipping test because watches don't support VPN",
            sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));
    }

    @After
    public void tearDown() {
        for (TestableNetworkCallback callback : mCallbacksToUnregister) {
            sCM.unregisterNetworkCallback(callback);
        }
        setAppop(AppOpsManager.OP_ACTIVATE_VPN, false);
        setAppop(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, false);

        // Make sure the VpnProfile is not provisioned already.
        sVpnMgr.stopProvisionedVpnProfile();

        try {
            sVpnMgr.startProvisionedVpnProfile();
            fail("Expected SecurityException for missing consent");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Sets the given appop using shell commands
     *
     * <p>This method must NEVER be called from within a shell permission, as it will attempt to
     * acquire, and then drop the shell permission identity. This results in the caller losing the
     * shell permission identity due to these calls not being reference counted.
     */
    public void setAppop(int appop, boolean allow) {
        // Requires shell permission to update appops.
        runWithShellPermissionIdentity(() -> {
            mCtsNetUtils.setAppopPrivileged(appop, allow);
        }, Manifest.permission.MANAGE_TEST_NETWORKS);
    }

    private Ikev2VpnProfile buildIkev2VpnProfileCommon(
            @NonNull Ikev2VpnProfile.Builder builder, boolean isRestrictedToTestNetworks,
            boolean requiresValidation, boolean automaticIpVersionSelectionEnabled,
            boolean automaticNattKeepaliveTimerEnabled) throws Exception {

        builder.setBypassable(true)
                .setAllowedAlgorithms(TEST_ALLOWED_ALGORITHMS)
                .setProxy(TEST_PROXY_INFO)
                .setMaxMtu(TEST_MTU)
                .setMetered(false);
        if (isAtLeastT()) {
            builder.setRequiresInternetValidation(requiresValidation);
        }

        if (isAtLeastU()) {
            builder.setAutomaticIpVersionSelectionEnabled(automaticIpVersionSelectionEnabled);
            builder.setAutomaticNattKeepaliveTimerEnabled(automaticNattKeepaliveTimerEnabled);
        }

        // TODO: replace it in alternative way to remove the hidden method usage
        if (isRestrictedToTestNetworks) {
            builder.restrictToTestNetworks();
        }

        return builder.build();
    }

    private Ikev2VpnProfile buildIkev2VpnProfileIkeTunConnParams(
            final boolean isRestrictedToTestNetworks, final boolean requiresValidation,
            final boolean testIpv6) throws Exception {
        final IkeTunnelConnectionParams params =
                new IkeTunnelConnectionParams(testIpv6
                        ? IkeSessionTestUtils.IKE_PARAMS_V6 : IkeSessionTestUtils.IKE_PARAMS_V4,
                        IkeSessionTestUtils.CHILD_PARAMS);

        final Ikev2VpnProfile.Builder builder =
                new Ikev2VpnProfile.Builder(params)
                        .setRequiresInternetValidation(requiresValidation)
                        .setProxy(TEST_PROXY_INFO)
                        .setMaxMtu(TEST_MTU)
                        .setMetered(false);

        // TODO: replace it in alternative way to remove the hidden method usage
        if (isRestrictedToTestNetworks) {
            builder.restrictToTestNetworks();
        }
        return builder.build();
    }

    private Ikev2VpnProfile buildIkev2VpnProfilePsk(@NonNull String remote,
            boolean isRestrictedToTestNetworks, boolean requiresValidation)
            throws Exception {
        final Ikev2VpnProfile.Builder builder =
                new Ikev2VpnProfile.Builder(remote, TEST_IDENTITY)
                        .setAuthPsk(TEST_PSK);
        return buildIkev2VpnProfileCommon(builder, isRestrictedToTestNetworks,
                requiresValidation, false /* automaticIpVersionSelectionEnabled */,
                false /* automaticNattKeepaliveTimerEnabled */);
    }

    private Ikev2VpnProfile buildIkev2VpnProfileUsernamePassword(boolean isRestrictedToTestNetworks)
            throws Exception {
        final Ikev2VpnProfile.Builder builder =
                new Ikev2VpnProfile.Builder(TEST_SERVER_ADDR_V6, TEST_IDENTITY)
                        .setAuthUsernamePassword(TEST_USER, TEST_PASSWORD, mServerRootCa);
        return buildIkev2VpnProfileCommon(builder, isRestrictedToTestNetworks,
                false /* requiresValidation */, false /* automaticIpVersionSelectionEnabled */,
                false /* automaticNattKeepaliveTimerEnabled */);
    }

    private Ikev2VpnProfile buildIkev2VpnProfileDigitalSignature(boolean isRestrictedToTestNetworks)
            throws Exception {
        final Ikev2VpnProfile.Builder builder =
                new Ikev2VpnProfile.Builder(TEST_SERVER_ADDR_V6, TEST_IDENTITY)
                        .setAuthDigitalSignature(
                                mUserCertKey.cert, mUserCertKey.key, mServerRootCa);
        return buildIkev2VpnProfileCommon(builder, isRestrictedToTestNetworks,
                false /* requiresValidation */, false /* automaticIpVersionSelectionEnabled */,
                false /* automaticNattKeepaliveTimerEnabled */);
    }

    private void checkBasicIkev2VpnProfile(@NonNull Ikev2VpnProfile profile) throws Exception {
        assertEquals(TEST_SERVER_ADDR_V6, profile.getServerAddr());
        assertEquals(TEST_IDENTITY, profile.getUserIdentity());
        assertEquals(TEST_PROXY_INFO, profile.getProxyInfo());
        assertEquals(TEST_ALLOWED_ALGORITHMS, profile.getAllowedAlgorithms());
        assertTrue(profile.isBypassable());
        assertFalse(profile.isMetered());
        assertEquals(TEST_MTU, profile.getMaxMtu());
        assertFalse(profile.isRestrictedToTestNetworks());
    }

    public void doTestBuildIkev2VpnProfilePsk(final boolean requiresValidation) throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        final Ikev2VpnProfile profile = buildIkev2VpnProfilePsk(TEST_SERVER_ADDR_V6,
                false /* isRestrictedToTestNetworks */, requiresValidation);

        checkBasicIkev2VpnProfile(profile);
        assertArrayEquals(TEST_PSK, profile.getPresharedKey());

        // Verify nothing else is set.
        assertNull(profile.getUsername());
        assertNull(profile.getPassword());
        assertNull(profile.getServerRootCaCert());
        assertNull(profile.getRsaPrivateKey());
        assertNull(profile.getUserCert());
        if (isAtLeastT()) {
            assertEquals(requiresValidation, profile.isInternetValidationRequired());
        }
    }

    @IgnoreUpTo(SC_V2)
    @Test
    public void testBuildIkev2VpnProfileWithIkeTunnelConnectionParams() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        final IkeTunnelConnectionParams expectedParams = new IkeTunnelConnectionParams(
                IkeSessionTestUtils.IKE_PARAMS_V6, IkeSessionTestUtils.CHILD_PARAMS);
        final Ikev2VpnProfile.Builder ikeProfileBuilder =
                new Ikev2VpnProfile.Builder(expectedParams);
        // Verify the other Ike options could not be set with IkeTunnelConnectionParams.
        final Class<IllegalArgumentException> expected = IllegalArgumentException.class;
        assertThrows(expected, () -> ikeProfileBuilder.setAuthPsk(TEST_PSK));
        assertThrows(expected, () ->
                ikeProfileBuilder.setAuthUsernamePassword(TEST_USER, TEST_PASSWORD, mServerRootCa));
        assertThrows(expected, () -> ikeProfileBuilder.setAuthDigitalSignature(
                mUserCertKey.cert, mUserCertKey.key, mServerRootCa));

        final Ikev2VpnProfile profile = ikeProfileBuilder.build();

        assertEquals(expectedParams, profile.getIkeTunnelConnectionParams());
    }

    @Test
    public void testBuildIkev2VpnProfilePsk() throws Exception {
        doTestBuildIkev2VpnProfilePsk(true /* requiresValidation */);
        doTestBuildIkev2VpnProfilePsk(false /* requiresValidation */);
    }

    @Test
    public void testBuildIkev2VpnProfileUsernamePassword() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        final Ikev2VpnProfile profile =
                buildIkev2VpnProfileUsernamePassword(false /* isRestrictedToTestNetworks */);

        checkBasicIkev2VpnProfile(profile);
        assertEquals(TEST_USER, profile.getUsername());
        assertEquals(TEST_PASSWORD, profile.getPassword());
        assertEquals(mServerRootCa, profile.getServerRootCaCert());

        // Verify nothing else is set.
        assertNull(profile.getPresharedKey());
        assertNull(profile.getRsaPrivateKey());
        assertNull(profile.getUserCert());
    }

    @Test
    public void testBuildIkev2VpnProfileDigitalSignature() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        final Ikev2VpnProfile profile =
                buildIkev2VpnProfileDigitalSignature(false /* isRestrictedToTestNetworks */);

        checkBasicIkev2VpnProfile(profile);
        assertEquals(mUserCertKey.cert, profile.getUserCert());
        assertEquals(mUserCertKey.key, profile.getRsaPrivateKey());
        assertEquals(mServerRootCa, profile.getServerRootCaCert());

        // Verify nothing else is set.
        assertNull(profile.getUsername());
        assertNull(profile.getPassword());
        assertNull(profile.getPresharedKey());
    }

    private void verifyProvisionVpnProfile(
            boolean hasActivateVpn, boolean hasActivatePlatformVpn, boolean expectIntent)
            throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        setAppop(AppOpsManager.OP_ACTIVATE_VPN, hasActivateVpn);
        setAppop(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, hasActivatePlatformVpn);

        final Ikev2VpnProfile profile = buildIkev2VpnProfilePsk(TEST_SERVER_ADDR_V6,
                false /* isRestrictedToTestNetworks */, false /* requiresValidation */);
        final Intent intent = sVpnMgr.provisionVpnProfile(profile);
        assertEquals(expectIntent, intent != null);
    }

    @Test
    public void testProvisionVpnProfileNoPreviousConsent() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        verifyProvisionVpnProfile(false /* hasActivateVpn */,
                false /* hasActivatePlatformVpn */, true /* expectIntent */);
    }

    @Test
    public void testProvisionVpnProfilePlatformVpnConsented() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        verifyProvisionVpnProfile(false /* hasActivateVpn */,
                true /* hasActivatePlatformVpn */, false /* expectIntent */);
    }

    @Test
    public void testProvisionVpnProfileVpnServiceConsented() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        verifyProvisionVpnProfile(true /* hasActivateVpn */,
                false /* hasActivatePlatformVpn */, false /* expectIntent */);
    }

    @Test
    public void testProvisionVpnProfileAllPreConsented() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        verifyProvisionVpnProfile(true /* hasActivateVpn */,
                true /* hasActivatePlatformVpn */, false /* expectIntent */);
    }

    @Test
    public void testDeleteVpnProfile() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        setAppop(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, true);

        final Ikev2VpnProfile profile = buildIkev2VpnProfilePsk(TEST_SERVER_ADDR_V6,
                false /* isRestrictedToTestNetworks */, false /* requiresValidation */);
        assertNull(sVpnMgr.provisionVpnProfile(profile));

        // Verify that deleting the profile works (even without the appop)
        setAppop(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, false);
        sVpnMgr.deleteProvisionedVpnProfile();

        // Test that the profile was deleted - starting it should throw an IAE.
        try {
            setAppop(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, true);
            sVpnMgr.startProvisionedVpnProfile();
            fail("Expected IllegalArgumentException due to missing profile");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testStartVpnProfileNoPreviousConsent() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        setAppop(AppOpsManager.OP_ACTIVATE_VPN, false);
        setAppop(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, false);

        // Make sure the VpnProfile is not provisioned already.
        sVpnMgr.stopProvisionedVpnProfile();

        try {
            sVpnMgr.startProvisionedVpnProfile();
            fail("Expected SecurityException for missing consent");
        } catch (SecurityException expected) {
        }
    }

    private void checkStartStopVpnProfileBuildsNetworks(@NonNull IkeTunUtils tunUtils,
            boolean testIpv6, boolean requiresValidation, boolean testSessionKey,
            boolean testIkeTunConnParams)
            throws Exception {
        String serverAddr = testIpv6 ? TEST_SERVER_ADDR_V6 : TEST_SERVER_ADDR_V4;
        String initResp = testIpv6 ? SUCCESSFUL_IKE_INIT_RESP_V6 : SUCCESSFUL_IKE_INIT_RESP_V4;
        String authResp = testIpv6 ? SUCCESSFUL_IKE_AUTH_RESP_V6 : SUCCESSFUL_IKE_AUTH_RESP_V4;
        boolean hasNat = !testIpv6;

        // Requires MANAGE_TEST_NETWORKS to provision a test-mode profile.
        mCtsNetUtils.setAppopPrivileged(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, true);

        final Ikev2VpnProfile profile = testIkeTunConnParams
                ? buildIkev2VpnProfileIkeTunConnParams(true /* isRestrictedToTestNetworks */,
                        requiresValidation, testIpv6)
                : buildIkev2VpnProfilePsk(serverAddr, true /* isRestrictedToTestNetworks */,
                        requiresValidation);
        assertNull(sVpnMgr.provisionVpnProfile(profile));

        final TestableNetworkCallback cb = new TestableNetworkCallback(TIMEOUT_MS);
        final NetworkRequest nr = new NetworkRequest.Builder()
                .clearCapabilities().addTransportType(TRANSPORT_VPN).build();
        registerNetworkCallback(nr, cb);

        if (testSessionKey) {
            // testSessionKey will never be true if running on <T
            // startProvisionedVpnProfileSession() should return a non-null & non-empty random UUID.
            final String sessionId = mVmShim.startProvisionedVpnProfileSession();
            assertFalse(TextUtils.isEmpty(sessionId));
            final VpnProfileStateShim profileState = mVmShim.getProvisionedVpnProfileState();
            assertNotNull(profileState);
            assertEquals(ConstantsShim.VPN_PROFILE_STATE_CONNECTING, profileState.getState());
            assertEquals(sessionId, profileState.getSessionId());
            assertFalse(profileState.isAlwaysOn());
            assertFalse(profileState.isLockdownEnabled());
        } else {
            sVpnMgr.startProvisionedVpnProfile();
        }

        // Inject IKE negotiation
        int expectedMsgId = 0;
        tunUtils.awaitReqAndInjectResp(IKE_INITIATOR_SPI, expectedMsgId++, false /* isEncap */,
                HexDump.hexStringToByteArray(initResp));
        tunUtils.awaitReqAndInjectResp(IKE_INITIATOR_SPI, expectedMsgId++, hasNat /* isEncap */,
                HexDump.hexStringToByteArray(authResp));

        // Verify the VPN network came up
        final Network vpnNetwork = cb.expect(CallbackEntry.AVAILABLE).getNetwork();

        if (testSessionKey) {
            final VpnProfileStateShim profileState = mVmShim.getProvisionedVpnProfileState();
            assertNotNull(profileState);
            assertEquals(ConstantsShim.VPN_PROFILE_STATE_CONNECTED, profileState.getState());
            assertFalse(profileState.isAlwaysOn());
            assertFalse(profileState.isLockdownEnabled());
        }

        cb.expectCaps(vpnNetwork, TIMEOUT_MS, c -> c.hasTransport(TRANSPORT_VPN)
                && c.hasCapability(NET_CAPABILITY_INTERNET)
                && !c.hasCapability(NET_CAPABILITY_VALIDATED)
                && Process.myUid() == c.getOwnerUid());
        cb.expect(CallbackEntry.LINK_PROPERTIES_CHANGED, vpnNetwork);
        cb.expect(CallbackEntry.BLOCKED_STATUS, vpnNetwork);

        // A VPN that requires validation is initially not validated, while one that doesn't
        // immediately validate automatically. Because this VPN can't actually access Internet,
        // the VPN only validates if it doesn't require validation. If the VPN requires validation
        // but unexpectedly sends this callback, expecting LOST below will fail because the next
        // callback will be the validated capabilities instead.
        // In S and below, |requiresValidation| is ignored, so this callback is always sent
        // regardless of its value. However, there is a race in Vpn(see b/228574221) that VPN may
        // misuse VPN network itself as the underlying network. The fix is not available without
        // SDK > T platform. Thus, verify this only on T+ platform.
        if (!requiresValidation && isAtLeastT()) {
            cb.eventuallyExpect(CallbackEntry.NETWORK_CAPS_UPDATED, TIMEOUT_MS,
                    entry -> ((CallbackEntry.CapabilitiesChanged) entry).getCaps()
                            .hasCapability(NET_CAPABILITY_VALIDATED));
        }

        sVpnMgr.stopProvisionedVpnProfile();
        // Using expectCallback may cause the test to be flaky since test may receive other
        // callbacks such as linkproperties change.
        cb.eventuallyExpect(CallbackEntry.LOST, TIMEOUT_MS,
                lost -> vpnNetwork.equals(lost.getNetwork()));
    }

    private void registerNetworkCallback(NetworkRequest request, TestableNetworkCallback callback) {
        sCM.registerNetworkCallback(request, callback);
        mCallbacksToUnregister.add(callback);
    }

    private class VerifyStartStopVpnProfileTest implements TestNetworkRunnable.Test {
        private final boolean mTestIpv6Only;
        private final boolean mRequiresValidation;
        private final boolean mTestSessionKey;
        private final boolean mTestIkeTunConnParams;

        /**
         * Constructs the test
         *
         * @param testIpv6Only if true, builds a IPv6-only test; otherwise builds a IPv4-only test
         * @param requiresValidation whether this VPN should request platform validation
         * @param testSessionKey if true, start VPN by calling startProvisionedVpnProfileSession()
         */
        VerifyStartStopVpnProfileTest(boolean testIpv6Only, boolean requiresValidation,
                boolean testSessionKey, boolean testIkeTunConnParams) {
            mTestIpv6Only = testIpv6Only;
            mRequiresValidation = requiresValidation;
            mTestSessionKey = testSessionKey;
            mTestIkeTunConnParams = testIkeTunConnParams;
        }

        @Override
        public void runTest(TestNetworkInterface testIface, TestNetworkCallback tunNetworkCallback)
                throws Exception {
            final IkeTunUtils tunUtils = new IkeTunUtils(testIface.getFileDescriptor());

            checkStartStopVpnProfileBuildsNetworks(tunUtils, mTestIpv6Only, mRequiresValidation,
                    mTestSessionKey, mTestIkeTunConnParams);
        }

        @Override
        public void cleanupTest() {
            sVpnMgr.stopProvisionedVpnProfile();
        }

        @Override
        public InetAddress[] getTestNetworkAddresses() {
            if (mTestIpv6Only) {
                return new InetAddress[] {LOCAL_OUTER_6};
            } else {
                return new InetAddress[] {LOCAL_OUTER_4};
            }
        }
    }

    private void doTestStartStopVpnProfile(boolean testIpv6Only, boolean requiresValidation,
            boolean testSessionKey, boolean testIkeTunConnParams) throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        // Requires shell permission to update appops.
        runWithShellPermissionIdentity(
                new TestNetworkRunnable(new VerifyStartStopVpnProfileTest(
                        testIpv6Only, requiresValidation, testSessionKey , testIkeTunConnParams)));
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileV4() throws Exception {
        doTestStartStopVpnProfile(false /* testIpv6Only */, false /* requiresValidation */,
                false /* testSessionKey */, false /* testIkeTunConnParams */);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileV4WithValidation() throws Exception {
        doTestStartStopVpnProfile(false /* testIpv6Only */, true /* requiresValidation */,
                false /* testSessionKey */, false /* testIkeTunConnParams */);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileV6() throws Exception {
        doTestStartStopVpnProfile(true /* testIpv6Only */, false /* requiresValidation */,
                false /* testSessionKey */, false /* testIkeTunConnParams */);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileV6WithValidation() throws Exception {
        doTestStartStopVpnProfile(true /* testIpv6Only */, true /* requiresValidation */,
                false /* testSessionKey */, false /* testIkeTunConnParams */);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileIkeTunConnParamsV4() throws Exception {
        doTestStartStopVpnProfile(false /* testIpv6Only */, false /* requiresValidation */,
                false /* testSessionKey */, true /* testIkeTunConnParams */);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileIkeTunConnParamsV4WithValidation() throws Exception {
        doTestStartStopVpnProfile(false /* testIpv6Only */, true /* requiresValidation */,
                false /* testSessionKey */, true /* testIkeTunConnParams */);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileIkeTunConnParamsV6() throws Exception {
        doTestStartStopVpnProfile(true /* testIpv6Only */, false /* requiresValidation */,
                false /* testSessionKey */, true /* testIkeTunConnParams */);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testStartStopVpnProfileIkeTunConnParamsV6WithValidation() throws Exception {
        doTestStartStopVpnProfile(true /* testIpv6Only */, true /* requiresValidation */,
                false /* testSessionKey */, true /* testIkeTunConnParams */);
    }

    @IgnoreUpTo(SC_V2)
    @Test
    public void testStartProvisionedVpnV4ProfileSession() throws Exception {
        doTestStartStopVpnProfile(false /* testIpv6Only */, false /* requiresValidation */,
                true /* testSessionKey */, false /* testIkeTunConnParams */);
    }

    @IgnoreUpTo(SC_V2)
    @Test
    public void testStartProvisionedVpnV6ProfileSession() throws Exception {
        doTestStartStopVpnProfile(true /* testIpv6Only */, false /* requiresValidation */,
                true /* testSessionKey */, false /* testIkeTunConnParams */);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testBuildIkev2VpnProfileWithAutomaticNattKeepaliveTimerEnabled() throws Exception {
        final Ikev2VpnProfile profileWithDefaultValue = buildIkev2VpnProfilePsk(TEST_SERVER_ADDR_V6,
                false /* isRestrictedToTestNetworks */, false /* requiresValidation */);
        assertFalse(profileWithDefaultValue.isAutomaticNattKeepaliveTimerEnabled());

        final Ikev2VpnProfile.Builder builder =
                new Ikev2VpnProfile.Builder(TEST_SERVER_ADDR_V6, TEST_IDENTITY)
                        .setAuthPsk(TEST_PSK);
        final Ikev2VpnProfile profile = buildIkev2VpnProfileCommon(builder,
                false /* isRestrictedToTestNetworks */,
                false /* requiresValidation */,
                false /* automaticIpVersionSelectionEnabled */,
                true /* automaticNattKeepaliveTimerEnabled */);
        assertTrue(profile.isAutomaticNattKeepaliveTimerEnabled());
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testBuildIkev2VpnProfileWithAutomaticIpVersionSelectionEnabled() throws Exception {
        final Ikev2VpnProfile profileWithDefaultValue = buildIkev2VpnProfilePsk(TEST_SERVER_ADDR_V6,
                false /* isRestrictedToTestNetworks */, false /* requiresValidation */);
        assertFalse(profileWithDefaultValue.isAutomaticIpVersionSelectionEnabled());

        final Ikev2VpnProfile.Builder builder =
                new Ikev2VpnProfile.Builder(TEST_SERVER_ADDR_V6, TEST_IDENTITY)
                        .setAuthPsk(TEST_PSK);
        final Ikev2VpnProfile profile = buildIkev2VpnProfileCommon(builder,
                false /* isRestrictedToTestNetworks */,
                false /* requiresValidation */,
                true /* automaticIpVersionSelectionEnabled */,
                false /* automaticNattKeepaliveTimerEnabled */);
        assertTrue(profile.isAutomaticIpVersionSelectionEnabled());
    }

    private static class CertificateAndKey {
        public final X509Certificate cert;
        public final PrivateKey key;

        CertificateAndKey(X509Certificate cert, PrivateKey key) {
            this.cert = cert;
            this.key = key;
        }
    }

    private static CertificateAndKey generateRandomCertAndKeyPair() throws Exception {
        final Date validityBeginDate =
                new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1L));
        final Date validityEndDate =
                new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1L));

        // Generate a keypair
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(512);
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();

        final X500Principal dnName = new X500Principal("CN=test.android.com");
        final X509V1CertificateGenerator certGen = new X509V1CertificateGenerator();
        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setSubjectDN(dnName);
        certGen.setIssuerDN(dnName);
        certGen.setNotBefore(validityBeginDate);
        certGen.setNotAfter(validityEndDate);
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");

        final X509Certificate cert = certGen.generate(keyPair.getPrivate(), "AndroidOpenSSL");
        return new CertificateAndKey(cert, keyPair.getPrivate());
    }
}
