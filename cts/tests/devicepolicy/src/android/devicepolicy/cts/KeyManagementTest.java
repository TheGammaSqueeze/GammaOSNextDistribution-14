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

package android.devicepolicy.cts;

import static android.security.KeyChain.ACTION_KEYCHAIN_CHANGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import static java.util.Collections.singleton;

import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.security.KeyChain;
import android.security.KeyChainException;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.KeyManagement;
import com.android.bedstead.harrier.policies.KeyManagementWithAdminReceiver;
import com.android.bedstead.harrier.policies.KeySelection;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.certificates.Certificates;
import com.android.bedstead.nene.packages.ProcessReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.BlockingCallback;
import com.android.compatibility.common.util.FakeKeys;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Test that a DPC can manage keys and certificate on a device by installing, generating and
 * removing key pairs via DevicePolicyManager APIs. The instrumented test app can use the installed
 * keys by requesting access to them and retrieving them via KeyChain APIs.
 */
@RunWith(BedsteadJUnit4.class)
public final class KeyManagementTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final int KEYCHAIN_CALLBACK_TIMEOUT_SECONDS = 540;
    private static final String RSA = "RSA";
    private static final String RSA_ALIAS = "com.android.test.valid-rsa-key-1";
    private static final PrivateKey PRIVATE_KEY =
            TestApis.certificates().generatePrivateKey(FakeKeys.FAKE_RSA_1.privateKey,
                    Certificates.KeyAlgorithmType.RSA);
    private static final Certificate CERTIFICATE =
            TestApis.certificates().generateCertificate(FakeKeys.FAKE_RSA_1.caCertificate);
    private static final Certificate[] CERTIFICATES = new Certificate[]{CERTIFICATE};
    private static final String NON_EXISTENT_ALIAS = "KeyManagementTest-nonexistent";
    private static final Context sContext = TestApis.context().instrumentedContext();

    private static Uri getUri(String alias) {
        try {
            return Uri.parse("https://example.org/?alias=" + URLEncoder.encode(alias, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("Unable to parse URI." + e);
        }
    }

    /**
     * This requires launching an activity so can't be called by a HSUM DO user.
     * No user apps run on HSUM DO user so there is no need to test KeyChain.choosePrivateKeyAlias
     * in that case anyway. Use {@code assumeFalse(isHeadlessDoMode())} to skip those test cases.
     */
    private static void choosePrivateKeyAlias(KeyChainAliasCallback callback, String alias) {
        /* Pass the alias as a GET to an imaginary server instead of explicitly asking for it,
         * to make sure the DPC actually has to do some work to grant the cert.
         */
        try {
            ActivityContext.runWithContext(
                    (activity) -> KeyChain.choosePrivateKeyAlias(activity, callback, /* keyTypes= */
                            null, /* issuers= */ null, getUri(alias), /* alias = */ null)
            );
        } catch (InterruptedException e) {
            throw new AssertionError("Unable to choose private key alias." + e);
        }
    }

    private static PrivateKey getPrivateKey(Context context, String alias) {
        try {
            return KeyChain.getPrivateKey(context, alias);
        } catch (KeyChainException | InterruptedException e) {
            throw new AssertionError("Failed to get private key." + e);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void installKeyPair_validRsaKeyPair_success() {
        try {
            // Install keypair
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(sDeviceState.dpc().componentName(), PRIVATE_KEY, CERTIFICATE,
                            RSA_ALIAS)).isTrue();
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class, singleTestOnly = true)
    public void installKeyPair_nullPrivateKey_throwException() {
        assertThrows(NullPointerException.class,
                () -> sDeviceState.dpc().devicePolicyManager().installKeyPair(
                        sDeviceState.dpc().componentName(),
                        /* privKey = */ null, CERTIFICATE, RSA_ALIAS));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class, singleTestOnly = true)
    public void installKeyPair_nullCertificate_throwException() {
        assertThrows(NullPointerException.class,
                () -> sDeviceState.dpc().devicePolicyManager().installKeyPair(
                        sDeviceState.dpc().componentName(),
                        PRIVATE_KEY, /* cert = */ null, RSA_ALIAS));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class, singleTestOnly = true)
    public void installKeyPair_nullAdminComponent_throwException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().installKeyPair(
                        /* admin = */ null, PRIVATE_KEY, CERTIFICATE, RSA_ALIAS));
    }

    @Ignore("TODO(b/204544463): Enable when the key can be serialized")
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void installKeyPair_withAutomatedAccess_aliasIsGranted() throws Exception {
        try {
            // Install keypair with automated access
            sDeviceState.dpc().devicePolicyManager().installKeyPair(
                    sDeviceState.dpc().componentName(), PRIVATE_KEY,
                    CERTIFICATES, RSA_ALIAS, /* requestAccess = */ true);

            // TODO(b/204544478): Remove the null context
            assertThat(sDeviceState.dpc().keyChain().getPrivateKey(/* context= */ null, RSA_ALIAS))
                    .isNotNull();
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager().removeKeyPair(
                    sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void installKeyPair_withoutAutomatedAccess_aliasIsNotGranted() throws Exception {
        try {
            // Install keypair with automated access
            sDeviceState.dpc().devicePolicyManager().installKeyPair(
                    sDeviceState.dpc().componentName(), PRIVATE_KEY,
                    CERTIFICATES, RSA_ALIAS, /* requestAccess = */ false);

            // TODO(b/204544478): Remove the null context
            assertThat(sDeviceState.dpc().keyChain().getPrivateKey(/* context= */ null, RSA_ALIAS))
                    .isNull();
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager().removeKeyPair(
                    sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void removeKeyPair_validRsaKeyPair_success() {
        try {
            // Install keypair
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(
                            sDeviceState.dpc().componentName(),
                            PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);
        } finally {
            // Remove keypair
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS)).isTrue();
        }
    }


    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = KeyManagement.class)
    public void hasKeyPair_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().hasKeyPair(RSA_ALIAS));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void hasKeyPair_nonExistentAlias_false() {
        assertThat(
                sDeviceState.dpc().devicePolicyManager().hasKeyPair(NON_EXISTENT_ALIAS)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void hasKeyPair_installedAlias_true() {
        try {
            // Install keypair
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(sDeviceState.dpc().componentName(),
                            PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);

            assertThat(sDeviceState.dpc().devicePolicyManager().hasKeyPair(RSA_ALIAS)).isTrue();
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void hasKeyPair_removedAlias_false() {
        try {
            // Install keypair
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(sDeviceState.dpc().componentName(),
                            PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);

            assertThat(sDeviceState.dpc().devicePolicyManager().hasKeyPair(RSA_ALIAS)).isFalse();
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeyManagementWithAdminReceiver.class)
    public void choosePrivateKeyAlias_aliasIsSelectedByAdmin_returnAlias() throws Exception {
        // Test doesn't apply to HSUM DO case as no app on that user is expected to request keypair.
        assumeFalse(isHeadlessDoMode());

        try {
            // Install keypair
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(sDeviceState.dpc().componentName(),
                            PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);
            KeyChainAliasCallback callback = new KeyChainAliasCallback();

            choosePrivateKeyAlias(callback, RSA_ALIAS);

            assertThat(callback.await(KEYCHAIN_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(RSA_ALIAS);
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeyManagementWithAdminReceiver.class)
    public void choosePrivateKeyAlias_NonexistentAliasSelectedByAdmin_returnNull()
            throws Exception {
        // Test doesn't apply to HSUM DO case as no app on that user is expected to request keypair.
        assumeFalse(isHeadlessDoMode());

        KeyChainAliasCallback callback = new KeyChainAliasCallback();

        choosePrivateKeyAlias(callback, NON_EXISTENT_ALIAS);

        assertThat(callback.await(KEYCHAIN_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(null);
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeyManagementWithAdminReceiver.class)
    public void choosePrivateKeyAlias_adminDenySelection_returnNull()
            throws Exception {
        // Test doesn't apply to HSUM DO case as no app on that user is expected to request keypair.
        assumeFalse(isHeadlessDoMode());

        KeyChainAliasCallback callback = new KeyChainAliasCallback();

        choosePrivateKeyAlias(callback, KeyChain.KEY_ALIAS_SELECTION_DENIED);

        assertThat(callback.await(KEYCHAIN_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(null);
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeyManagementWithAdminReceiver.class)
    public void choosePrivateKeyAlias_nonUserSelectedAliasIsSelectedByAdmin_returnAlias()
            throws Exception {
        // Test doesn't apply to HSUM DO case as no app on that user is expected to request keypair.
        assumeFalse(isHeadlessDoMode());

        try {
            // Install keypair which is not user selectable
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(sDeviceState.dpc().componentName(), PRIVATE_KEY,
                    CERTIFICATES, RSA_ALIAS, /* flags = */ 0);
            KeyChainAliasCallback callback = new KeyChainAliasCallback();

            choosePrivateKeyAlias(callback, RSA_ALIAS);

            assertThat(callback.await(KEYCHAIN_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(RSA_ALIAS);
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeyManagementWithAdminReceiver.class)
    public void getPrivateKey_aliasIsGranted_returnPrivateKey() throws Exception {
        // Test doesn't apply to HSUM DO case as no app on that user is expected to request keypair.
        assumeFalse(isHeadlessDoMode());

        try {
            // Install keypair

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiver(ACTION_KEYCHAIN_CHANGED)
                                 .register()) {
                sDeviceState.dpc().devicePolicyManager()
                        .installKeyPair(sDeviceState.dpc().componentName(),
                                PRIVATE_KEY, CERTIFICATE, RSA_ALIAS);
            }

            // Grant alias via {@code KeyChain.choosePrivateKeyAlias}
            KeyChainAliasCallback callback = new KeyChainAliasCallback();
            choosePrivateKeyAlias(callback, RSA_ALIAS);
            callback.await(KEYCHAIN_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Get private key for the granted alias
            final PrivateKey privateKey =
                    getPrivateKey(TestApis.context().instrumentedContext(), RSA_ALIAS);

            assertThat(privateKey).isNotNull();
            assertThat(privateKey.getAlgorithm()).isEqualTo(
                    Certificates.KeyAlgorithmType.RSA.getValue());

        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeyManagement.class)
    public void install_wasPreviouslyGrantedOnPreviousInstall_grantDoesNotPersist()
            throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(sDeviceState.dpc().componentName(),
                            PRIVATE_KEY, CERTIFICATES, RSA_ALIAS, true);
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);

            sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(
                            sDeviceState.dpc().componentName(),
                            PRIVATE_KEY, CERTIFICATES, RSA_ALIAS,
                            false);

            assertThat(sDeviceState.dpc().keyChain().getPrivateKey(
                    TestApis.context().instrumentedContext(), RSA_ALIAS))
                    .isNull();
        } finally {
            // Remove keypair
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = KeySelection.class)
    public void getKeyPairGrants_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .getKeyPairGrants(RSA_ALIAS));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class, singleTestOnly = true)
    public void getKeyPairGrants_nonExistent_throwsIllegalArgumentException() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .getKeyPairGrants(NON_EXISTENT_ALIAS));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class)
    public void getKeyPairGrants_doesNotIncludeNotGranted() {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);

            assertThat(
                    sDeviceState.dpc().devicePolicyManager().getKeyPairGrants(RSA_ALIAS)).isEmpty();
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class)
    public void getKeyPairGrants_includesGrantedAtInstall() {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ true);
            ProcessReference dpcProcess =
                    Poll.forValue("DPC Uid", () -> sDeviceState.dpcOnly().process())
                            .toNotBeNull()
                            .errorOnFail()
                            .await();

            assertThat(sDeviceState.dpc().devicePolicyManager().getKeyPairGrants(RSA_ALIAS))
                    .isEqualTo(Map.of(dpcProcess.uid(),
                            singleton(sDeviceState.dpcOnly().packageName())));
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeySelection.class)
    public void getKeyPairGrants_includesGrantedExplicitly() {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);
            sDeviceState.dpc().devicePolicyManager().grantKeyPairToApp(
                    sDeviceState.dpc().componentName(), RSA_ALIAS,
                    sContext.getPackageName());

            assertThat(sDeviceState.dpc().devicePolicyManager().getKeyPairGrants(RSA_ALIAS))
                    .isEqualTo(Map.of(Process.myUid(),
                            singleton(sContext.getPackageName())));
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class)
    public void getKeyPairGrants_doesNotIncludeRevoked() {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ true);
            sDeviceState.dpc().devicePolicyManager().revokeKeyPairFromApp(
                    sDeviceState.dpc().componentName(), RSA_ALIAS,
                    sDeviceState.dpcOnly().packageName());

            assertThat(
                    sDeviceState.dpc().devicePolicyManager().getKeyPairGrants(RSA_ALIAS)).isEmpty();
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    // TODO(b/199148889): To be tested with targetSDKVersion U+.
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class, singleTestOnly = true)
    public void grantKeyPairToWifiAuth_nonExistent_throwsIllegalArgumentException() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .grantKeyPairToWifiAuth(NON_EXISTENT_ALIAS));
    }

    @Ignore("TODO(b/199148889): To be tested with targetSDKVersion pre U.")
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class, singleTestOnly = true)
    public void grantKeyPairToWifiAuth_nonExistent_returnsFalse() {
        assertThat(sDeviceState.dpc().devicePolicyManager()
                .grantKeyPairToWifiAuth(NON_EXISTENT_ALIAS)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class)
    public void isKeyPairGrantedToWifiAuth_default_returnsFalse() {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);

            assertThat(
                    sDeviceState.dpc().devicePolicyManager().isKeyPairGrantedToWifiAuth(RSA_ALIAS))
                    .isFalse();
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class)
    public void isKeyPairGrantedToWifiAuth_granted_returnsTrue() {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);
            sDeviceState.dpc().devicePolicyManager().grantKeyPairToWifiAuth(RSA_ALIAS);

            assertThat(
                    sDeviceState.dpc().devicePolicyManager().isKeyPairGrantedToWifiAuth(RSA_ALIAS))
                    .isTrue();
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class)
    public void isKeyPairGrantedToWifiAuth_revoked_returnsFalse() {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);
            sDeviceState.dpc().devicePolicyManager().grantKeyPairToWifiAuth(RSA_ALIAS);
            sDeviceState.dpc().devicePolicyManager().revokeKeyPairFromWifiAuth(RSA_ALIAS);

            assertThat(
                    sDeviceState.dpc().devicePolicyManager().isKeyPairGrantedToWifiAuth(RSA_ALIAS))
                    .isFalse();
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }
    // TODO(b/199148889): To be tested with targetSDKVersion U+.
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class, singleTestOnly = true)
    public void grantKeyPairToApp_nonExistent_throwsIllegalArgumentException() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .grantKeyPairToApp(sDeviceState.dpc().componentName(), NON_EXISTENT_ALIAS,
                                sContext.getPackageName()));
    }

    @Ignore("TODO(b/199148889): To be tested with targetSDKVersions pre U.")
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = KeySelection.class, singleTestOnly = true)
    public void grantKeyPairToApp_nonExistent_returnsFalse() {
        assertThat(sDeviceState.dpc().devicePolicyManager()
                        .grantKeyPairToApp(sDeviceState.dpc().componentName(), NON_EXISTENT_ALIAS,
                                sContext.getPackageName())).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeySelection.class)
    public void grantKeyPair_keyUsable() throws Exception {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);
            sDeviceState.dpc().devicePolicyManager().grantKeyPairToApp(
                    sDeviceState.dpc().componentName(), RSA_ALIAS, sContext.getPackageName()
            );

            PrivateKey key = KeyChain.getPrivateKey(sContext, RSA_ALIAS);

            signDataWithKey("SHA256withRSA", key); // Doesn't throw exception
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeySelection.class)
    public void grantKeyPair_validCertificate() throws Exception {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);
            sDeviceState.dpc().devicePolicyManager().grantKeyPairToApp(
                    sDeviceState.dpc().componentName(), RSA_ALIAS, sContext.getPackageName()
            );

            Certificate[] certificates = KeyChain.getCertificateChain(sContext, RSA_ALIAS);

            assertThat(certificates).asList().containsExactly(CERTIFICATE);
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = KeySelection.class)
    public void revokeKeyPairFromApp_keyNotUsable() throws Exception {
        try {
            sDeviceState.dpcOnly().devicePolicyManager().installKeyPair(
                    sDeviceState.dpcOnly().componentName(), PRIVATE_KEY, CERTIFICATES,
                    RSA_ALIAS, /* requestAccess= */ false);
            sDeviceState.dpc().devicePolicyManager().grantKeyPairToApp(
                    sDeviceState.dpc().componentName(), RSA_ALIAS, sContext.getPackageName()
            );
            // Key is requested from KeyChain prior to revoking the grant.
            PrivateKey key = KeyChain.getPrivateKey(sContext, RSA_ALIAS);

            sDeviceState.dpc().devicePolicyManager().revokeKeyPairFromApp(
                    sDeviceState.dpc().componentName(), RSA_ALIAS, sContext.getPackageName());

            // Key shouldn't be valid after the grant is revoked.
            Assert.assertThrows(
                    InvalidKeyException.class, () -> signDataWithKey("SHA256withRSA", key));
        } finally {
            // Remove keypair
            sDeviceState.dpcOnly().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpcOnly().componentName(), RSA_ALIAS);
        }
    }

    private byte[] signDataWithKey(String algoIdentifier, PrivateKey privateKey) throws Exception {
        byte[] data = "hello".getBytes();
        Signature sign = Signature.getInstance(algoIdentifier);
        sign.initSign(privateKey);
        sign.update(data);
        return sign.sign();
    }

    private static class KeyChainAliasCallback extends BlockingCallback<String> implements
            android.security.KeyChainAliasCallback {

        @Override
        public void alias(final String chosenAlias) {
            callbackTriggered(chosenAlias);
        }
    }

    // Returns true if the test is currently running as (user 0) DO on a HSUM build.
    private boolean isHeadlessDoMode() {
        return TestApis.users().isHeadlessSystemUserMode()
                && sDeviceState.dpc().user().equals(TestApis.users().system());
    }
}
