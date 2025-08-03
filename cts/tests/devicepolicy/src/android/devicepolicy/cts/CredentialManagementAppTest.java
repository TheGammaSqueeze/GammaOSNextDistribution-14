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

package android.devicepolicy.cts;

import static android.Manifest.permission.MANAGE_CREDENTIAL_MANAGEMENT_APP;
import static android.app.admin.DevicePolicyManager.INSTALLKEY_SET_USER_SELECTABLE;

import static com.android.bedstead.nene.appops.AppOpsMode.ALLOWED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.Uri;
import android.security.AppUriAuthenticationPolicy;
import android.security.AttestedKeyPair;
import android.security.KeyChain;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveAppOp;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.BlockingCallback;
import com.android.compatibility.common.util.FakeKeys;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public final class CredentialManagementAppTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final int KEYCHAIN_CALLBACK_TIMEOUT_SECONDS = 300;
    private static final PrivateKey PRIVATE_KEY =
            getPrivateKey(FakeKeys.FAKE_RSA_1.privateKey, "RSA");
    private static final Certificate CERTIFICATE =
            getCertificate(FakeKeys.FAKE_RSA_1.caCertificate);
    private static final Certificate[] CERTIFICATES = new Certificate[]{CERTIFICATE};

    private static final Context sContext = TestApis.context().instrumentationContext();
    private static final String MANAGE_CREDENTIALS = "android:manage_credentials";
    private static final String ALIAS = "com.android.test.rsa";
    private static final String NOT_IN_USER_POLICY_ALIAS = "anotherAlias";
    private final static Uri URI = Uri.parse("https://test.com");
    private final static AppUriAuthenticationPolicy AUTHENTICATION_POLICY =
            new AppUriAuthenticationPolicy.Builder()
                    .addAppAndUriMapping(sContext.getPackageName(), URI, ALIAS)
                    .build();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static PrivateKey getPrivateKey(final byte[] key, String type) {
        try {
            return KeyFactory.getInstance(type).generatePrivate(
                    new PKCS8EncodedKeySpec(key));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new AssertionError("Unable to get certificate." + e);
        }
    }

    private static Certificate getCertificate(byte[] cert) {
        try {
            return CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(cert));
        } catch (CertificateException e) {
            throw new AssertionError("Unable to get certificate." + e);
        }
    }

    @Test
    @EnsureDoesNotHaveAppOp(MANAGE_CREDENTIALS)
    public void installKeyPair_withoutManageCredentialAppOp_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.installKeyPair(/* admin = */ null, PRIVATE_KEY,
                        CERTIFICATES,
                        ALIAS, /* flags = */ 0));
    }

    @Test
    @EnsureDoesNotHaveAppOp(MANAGE_CREDENTIALS)
    public void removeKeyPair_withoutManageCredentialAppOp_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.removeKeyPair(/* admin = */ null, ALIAS));
    }

    @Test
    @EnsureDoesNotHaveAppOp(MANAGE_CREDENTIALS)
    public void generateKeyPair_withoutManageCredentialAppOp_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.generateKeyPair(/* admin = */ null, "RSA",
                        buildRsaKeySpec(ALIAS, /* useStrongBox = */ false),
                        /* idAttestationFlags = */ 0));
    }

    @Test
    @EnsureDoesNotHaveAppOp(MANAGE_CREDENTIALS)
    public void setKeyPairCertificate_withoutManageCredentialAppOp_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.setKeyPairCertificate(/* admin = */ null, ALIAS,
                        Arrays.asList(CERTIFICATE), /* isUserSelectable = */ false));
    }

    @Test
    public void installKeyPair_isUserSelectableFlagSet_throwsException() throws Exception {
        setCredentialManagementApp();

        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.installKeyPair(/* admin = */ null, PRIVATE_KEY,
                        CERTIFICATES,
                        ALIAS, /* flags = */ INSTALLKEY_SET_USER_SELECTABLE));
    }

    @Test
    public void installKeyPair_aliasIsNotInAuthenticationPolicy_throwsException() throws Exception {
        setCredentialManagementApp();

        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.installKeyPair(/* admin = */ null, PRIVATE_KEY,
                        CERTIFICATES,
                        NOT_IN_USER_POLICY_ALIAS, /* flags = */ 0));
    }

    @Test
    public void installKeyPair_isCredentialManagementApp_success() throws Exception {
        setCredentialManagementApp();

        try {
            // Install keypair as credential management app
            assertThat(sDevicePolicyManager.installKeyPair(/* admin = */ null, PRIVATE_KEY,
                    CERTIFICATES,
                    ALIAS, 0)).isTrue();
        } finally {
            // Remove keypair as credential management app
            sDevicePolicyManager.removeKeyPair(/* admin = */ null, ALIAS);
            removeCredentialManagementApp();
        }
    }

    @Test
    public void hasKeyPair_aliasIsNotInAuthenticationPolicy_throwsException() {
        setCredentialManagementApp();

        try {
            assertThrows(SecurityException.class,
                    () -> sDevicePolicyManager.hasKeyPair(NOT_IN_USER_POLICY_ALIAS));
        } finally {
            removeCredentialManagementApp();
        }
    }

    @Test
    public void hasKeyPair_isCredentialManagementApp_success() throws Exception {
        setCredentialManagementApp();

        try {
            sDevicePolicyManager.installKeyPair(/* admin = */ null, PRIVATE_KEY, CERTIFICATES,
                    ALIAS,
                    /* flags = */0);

            assertThat(sDevicePolicyManager.hasKeyPair(ALIAS)).isTrue();
        } finally {
            sDevicePolicyManager.removeKeyPair(/* admin = */ null, ALIAS);
            removeCredentialManagementApp();
        }
    }

    @Test
    public void removeKeyPair_isCredentialManagementApp_success() throws Exception {
        setCredentialManagementApp();

        try {
            // Install keypair as credential management app
            sDevicePolicyManager.installKeyPair(/* admin = */ null, PRIVATE_KEY, CERTIFICATES,
                    ALIAS, 0);
        } finally {
            // Remove keypair as credential management app
            assertThat(sDevicePolicyManager.removeKeyPair(/* admin = */ null, ALIAS)).isTrue();
            removeCredentialManagementApp();
        }
    }

    @Test
    public void generateKeyPair_isCredentialManagementApp_success() throws Exception {
        setCredentialManagementApp();

        try {
            // Generate keypair as credential management app
            AttestedKeyPair generated = sDevicePolicyManager.generateKeyPair(/* admin = */ null,
                    "RSA",
                    buildRsaKeySpec(ALIAS, /* useStrongBox = */ false),
                    /* idAttestationFlags = */ 0);

            assertThat(generated).isNotNull();
            verifySignatureOverData("SHA256withRSA", generated.getKeyPair());
        } finally {
            // Remove keypair as credential management app
            sDevicePolicyManager.removeKeyPair(/* admin = */ null, ALIAS);
            removeCredentialManagementApp();
        }
    }

    @Test
    public void setKeyPairCertificate_isCredentialManagementApp_success() throws Exception {
        setCredentialManagementApp();

        try {
            // Generate keypair and aet keypair certificate as credential management app
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY).setDigests(
                    KeyProperties.DIGEST_SHA256).build();
            AttestedKeyPair generated = sDevicePolicyManager.generateKeyPair(/* admin = */ null,
                    "EC", spec, 0);
            List<Certificate> certificates = Arrays.asList(CERTIFICATE);
            sDevicePolicyManager.setKeyPairCertificate(/* admin = */ null, ALIAS, certificates,
                    false);

            // Make sure certificates can be retrieved from KeyChain
            Certificate[] fetchedCerts = KeyChain.getCertificateChain(sContext, ALIAS);

            assertThat(generated).isNotNull();
            assertThat(fetchedCerts).isNotNull();
            assertThat(fetchedCerts.length).isEqualTo(certificates.size());
            assertThat(fetchedCerts[0].getEncoded()).isEqualTo(certificates.get(0).getEncoded());
        } finally {
            // Remove keypair as credential management app
            sDevicePolicyManager.removeKeyPair(/* admin = */ null, ALIAS);
            removeCredentialManagementApp();
        }
    }

    @Test
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @EnsureHasNoDpc // Existing DPC means the Credential Management App is ignored
    public void choosePrivateKeyAlias_isCredentialManagementApp_aliasSelected() throws Exception {
        setCredentialManagementApp();

        try {
            // Install keypair as credential management app
            sDevicePolicyManager.installKeyPair(null, PRIVATE_KEY, new Certificate[]{CERTIFICATE},
                    ALIAS, 0);
            KeyChainAliasCallback callback = new KeyChainAliasCallback();

            ActivityContext.runWithContext((activity) ->
                    KeyChain.choosePrivateKeyAlias(activity, callback,
                            /* keyTypes= */ null, /* issuers= */ null, URI, /* alias = */ null)
            );

            assertThat(callback.await(KEYCHAIN_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(ALIAS);
        } finally {
            // Remove keypair as credential management app
            sDevicePolicyManager.removeKeyPair(/* admin = */ null, ALIAS);
            removeCredentialManagementApp();
        }
    }

    @Test
    public void isCredentialManagementApp_isNotCredentialManagementApp_returnFalse()
            throws Exception {
        removeCredentialManagementApp();

        assertThat(KeyChain.isCredentialManagementApp(sContext)).isFalse();
    }

    @Test
    public void isCredentialManagementApp_isCredentialManagementApp_returnTrue() throws Exception {
        setCredentialManagementApp();
        try {
            assertThat(KeyChain.isCredentialManagementApp(sContext)).isTrue();
        } finally {
            removeCredentialManagementApp();
        }
    }

    @Test
    public void getCredentialManagementAppPolicy_isNotCredentialManagementApp_throwException()
            throws Exception {
        removeCredentialManagementApp();
        assertThrows(SecurityException.class,
                () -> KeyChain.getCredentialManagementAppPolicy(sContext));
    }

    @Test
    public void getCredentialManagementAppPolicy_isCredentialManagementApp_returnPolicy()
            throws Exception {
        setCredentialManagementApp();
        try {
            assertThat(KeyChain.getCredentialManagementAppPolicy(sContext))
                    .isEqualTo(AUTHENTICATION_POLICY);
        } finally {
            removeCredentialManagementApp();
        }
    }

    @Test
    public void unregisterAsCredentialManagementApp_returnTrue()
            throws Exception {
        setCredentialManagementApp();

        try {
            boolean wasRemoved = KeyChain.removeCredentialManagementApp(sContext);

            assertThat(wasRemoved).isTrue();
            assertThat(KeyChain.isCredentialManagementApp(sContext)).isFalse();
        } catch (Exception e) {
            removeCredentialManagementApp();
        }
    }

    // TODO (b/174677062): Move this into infrastructure
    private void setCredentialManagementApp() {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_CREDENTIAL_MANAGEMENT_APP)) {
            boolean wasSet = KeyChain.setCredentialManagementApp(
                    sContext, sContext.getPackageName(), AUTHENTICATION_POLICY);

            assertWithMessage("Unable to set credential management app").that(wasSet).isTrue();
        }

        assertThat(KeyChain.isCredentialManagementApp(sContext)).isTrue();
        assertThat(TestApis.packages().instrumented().appOps().get(MANAGE_CREDENTIALS))
                .isEqualTo(ALLOWED);
    }

    // TODO (b/174677062): Move this into infrastructure
    private void removeCredentialManagementApp() {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_CREDENTIAL_MANAGEMENT_APP)) {
            boolean wasRemoved = KeyChain.removeCredentialManagementApp(sContext);
            assertWithMessage("Unable to remove credential management app")
                    .that(wasRemoved).isTrue();
        }
    }

    void verifySignature(String algoIdentifier, PublicKey publicKey, byte[] signature)
            throws Exception {
        byte[] data = "hello".getBytes();
        Signature verify = Signature.getInstance(algoIdentifier);
        verify.initVerify(publicKey);
        verify.update(data);
        assertThat(verify.verify(signature)).isTrue();
    }

    private void verifySignatureOverData(String algoIdentifier, KeyPair keyPair) throws Exception {
        verifySignature(algoIdentifier, keyPair.getPublic(),
                signDataWithKey(algoIdentifier, keyPair.getPrivate()));
    }

    private byte[] signDataWithKey(String algoIdentifier, PrivateKey privateKey) throws Exception {
        byte[] data = "hello".getBytes();
        Signature sign = Signature.getInstance(algoIdentifier);
        sign.initSign(privateKey);
        sign.update(data);
        return sign.sign();
    }

    private KeyGenParameterSpec buildRsaKeySpec(String alias, boolean useStrongBox) {
        return new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setIsStrongBoxBacked(useStrongBox)
                .build();
    }

    // TODO(scottjonathan): Using either code generation or reflection we could remove the need for
    //  these boilerplate classes
    private static class KeyChainAliasCallback extends BlockingCallback<String> implements
            android.security.KeyChainAliasCallback {
        @Override
        public void alias(final String chosenAlias) {
            callbackTriggered(chosenAlias);
        }
    }
}
