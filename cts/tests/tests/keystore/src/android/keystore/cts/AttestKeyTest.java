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

package android.keystore.cts;

import static android.keystore.cts.KeyAttestationTest.verifyCertificateChain;
import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA;
import static android.security.keystore.KeyProperties.PURPOSE_ATTEST_KEY;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PSS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.compatibility.common.util.CddTest;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AttestKeyTest {
    private static final String TAG = AttestKeyTest.class.getSimpleName();

    private KeyStore mKeyStore;
    private ArrayList<String> mAliasesToDelete = new ArrayList();

    @Before
    public void setUp() throws Exception {
        mKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mKeyStore.load(null);

        // Assume attest key support for all tests in this class.
        assumeAttestKey();
    }

    @After
    public void tearDown() throws Exception {
        for (String alias : mAliasesToDelete) {
            try {
                mKeyStore.deleteEntry(alias);
            } catch (Throwable t) {
                // Ignore any exception and delete the other aliases in the list.
            }
        }
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testEcAttestKey() throws Exception {
        testEcAttestKey(false /* useStrongBox */);
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testEcAttestKey_StrongBox() throws Exception {
        testEcAttestKey(true /* useStrongBox */);
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testRsaAttestKey() throws Exception {
        testRsaAttestKey(false /* useStrongBox */);
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testRsaAttestKey_StrongBox() throws Exception {
        testRsaAttestKey(true /* useStrongBox */);
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testAttestationWithNonAttestKey() throws Exception {
        final String nonAttestKeyAlias = "nonAttestKey";
        final String attestedKeyAlias = "attestedKey";
        generateKeyPair(KEY_ALGORITHM_EC,
                new KeyGenParameterSpec.Builder(nonAttestKeyAlias, PURPOSE_SIGN).build());

        try {
            generateKeyPair(KEY_ALGORITHM_EC,
                    new KeyGenParameterSpec.Builder(attestedKeyAlias, PURPOSE_SIGN)
                            .setAttestationChallenge("challenge".getBytes())
                            .setAttestKeyAlias(nonAttestKeyAlias)
                            .build());
            fail("Expected exception.");
        } catch (InvalidAlgorithmParameterException e) {
            assertThat(e.getMessage(), is("Invalid attestKey, does not have PURPOSE_ATTEST_KEY"));
        }
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testAttestKeyWithoutChallenge() throws Exception {
        final String attestKeyAlias = "attestKey";
        final String attestedKeyAlias = "attestedKey";
        generateKeyPair(KEY_ALGORITHM_EC,
                new KeyGenParameterSpec.Builder(attestKeyAlias, PURPOSE_ATTEST_KEY).build());

        try {
            generateKeyPair(KEY_ALGORITHM_EC,
                    new KeyGenParameterSpec
                            .Builder(attestedKeyAlias, PURPOSE_SIGN)
                            // Don't set attestation challenge
                            .setAttestKeyAlias(attestKeyAlias)
                            .build());
            fail("Expected exception.");
        } catch (InvalidAlgorithmParameterException e) {
            assertThat(e.getMessage(),
                    is("AttestKey specified but no attestation challenge provided"));
        }
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testStrongBoxCannotAttestToTeeKey() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Can only test with strongbox keymint",
                TestUtils.getFeatureVersionKeystoreStrongBox(context)
                        >= Attestation.KM_VERSION_KEYMINT_1);

        final String strongBoxAttestKeyAlias = "nonAttestKey";
        final String attestedKeyAlias = "attestedKey";
        generateKeyPair(KEY_ALGORITHM_EC,
                new KeyGenParameterSpec.Builder(strongBoxAttestKeyAlias, PURPOSE_ATTEST_KEY)
                        .setIsStrongBoxBacked(true)
                        .build());

        try {
            generateKeyPair(KEY_ALGORITHM_EC,
                    new KeyGenParameterSpec.Builder(attestedKeyAlias, PURPOSE_SIGN)
                            .setAttestationChallenge("challenge".getBytes())
                            .setAttestKeyAlias(strongBoxAttestKeyAlias)
                            .build());
            fail("Expected exception.");
        } catch (InvalidAlgorithmParameterException e) {
            assertThat(e.getMessage(),
                    is("Invalid security level: Cannot sign non-StrongBox key with StrongBox "
                            + "attestKey"));
        }
    }

    @Test
    @CddTest(requirements = {"9.11/C-1-6"})
    public void testTeeCannotAttestToStrongBoxKey() throws Exception {
        TestUtils.assumeStrongBox();

        final String teeAttestKeyAlias = "nonAttestKey";
        generateKeyPair(KEY_ALGORITHM_EC,
                new KeyGenParameterSpec.Builder(teeAttestKeyAlias, PURPOSE_ATTEST_KEY).build());

        try {
            generateKeyPair(KEY_ALGORITHM_EC,
                    new KeyGenParameterSpec.Builder("attestedKey", PURPOSE_SIGN)
                            .setAttestationChallenge("challenge".getBytes())
                            .setAttestKeyAlias(teeAttestKeyAlias)
                            .setIsStrongBoxBacked(true)
                            .build());
            fail("Expected exception.");
        } catch (InvalidAlgorithmParameterException e) {
            assertThat(e.getMessage(),
                    is("Invalid security level: Cannot sign StrongBox key with non-StrongBox "
                            + "attestKey"));
        }
    }

    private void assumeAttestKey() {
        PackageManager packageManager =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
        assumeTrue("Can only test if we have the attest key feature.",
                packageManager.hasSystemFeature(PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY));
    }

    private void testEcAttestKey(boolean useStrongBox) throws Exception {
        if (useStrongBox) {
            TestUtils.assumeStrongBox();
        }

        final String attestKeyAlias = "attestKey";

        Certificate attestKeyCertChain[] = generateKeyPair(KEY_ALGORITHM_EC,
                new KeyGenParameterSpec.Builder(attestKeyAlias, PURPOSE_ATTEST_KEY)
                        .setAttestationChallenge("challenge".getBytes())
                        .setIsStrongBoxBacked(useStrongBox)
                        .build());
        assertThat(attestKeyCertChain.length, greaterThan(1));

        testAttestKey(useStrongBox, attestKeyAlias, attestKeyCertChain);
    }

    private void testRsaAttestKey(boolean useStrongBox) throws Exception {
        if (useStrongBox) {
            TestUtils.assumeStrongBox();
        }

        final String attestKeyAlias = "attestKey";

        Certificate attestKeyCertChain[] = generateKeyPair(KEY_ALGORITHM_RSA,
                new KeyGenParameterSpec.Builder(attestKeyAlias, PURPOSE_ATTEST_KEY)
                        .setAttestationChallenge("challenge".getBytes())
                        .setIsStrongBoxBacked(useStrongBox)
                        .build());
        assertThat(attestKeyCertChain.length, greaterThan(1));

        testAttestKey(useStrongBox, attestKeyAlias, attestKeyCertChain);
    }

    private void testAttestKey(boolean useStrongBox, String attestKeyAlias,
            Certificate[] attestKeyCertChain) throws Exception {
        final String attestedEcKeyAlias = "attestedEcKey";
        final Certificate attestedEcKeyCertChain[] = generateKeyPair(KEY_ALGORITHM_EC,
                new KeyGenParameterSpec.Builder(attestedEcKeyAlias, PURPOSE_SIGN)
                        .setAttestationChallenge("challenge".getBytes())
                        .setAttestKeyAlias(attestKeyAlias)
                        .setIsStrongBoxBacked(useStrongBox)
                        .build());

        // Even though we asked for an attestation, we only get one cert.
        assertThat(attestedEcKeyCertChain.length, is(1));

        verifyCombinedChain(useStrongBox, attestKeyCertChain, attestedEcKeyCertChain);

        final X509Certificate attestationEcKeyCert = (X509Certificate) attestedEcKeyCertChain[0];
        final Attestation ecKeyAttestation = Attestation.loadFromCertificate(attestationEcKeyCert);

        final String attestedRsaKeyAlias = "attestedRsaKey";
        final Certificate attestedRsaKeyCertChain[] = generateKeyPair(KEY_ALGORITHM_RSA,
                new KeyGenParameterSpec.Builder(attestedRsaKeyAlias, PURPOSE_SIGN)
                        .setAttestationChallenge("challenge".getBytes())
                        .setAttestKeyAlias(attestKeyAlias)
                        .setIsStrongBoxBacked(useStrongBox)
                        .setDigests(DIGEST_SHA256)
                        .setSignaturePaddings(SIGNATURE_PADDING_RSA_PSS)
                        .build());

        // Even though we asked for an attestation, we only get one cert.
        assertThat(attestedRsaKeyCertChain.length, is(1));

        verifyCombinedChain(useStrongBox, attestKeyCertChain, attestedRsaKeyCertChain);

        final X509Certificate attestationRsaKeyCert = (X509Certificate) attestedRsaKeyCertChain[0];
        final Attestation rsaKeyAttestation =
                Attestation.loadFromCertificate(attestationRsaKeyCert);
    }

    private Certificate[] generateKeyPair(String algorithm, KeyGenParameterSpec spec)
            throws NoSuchAlgorithmException, NoSuchProviderException,
                   InvalidAlgorithmParameterException, KeyStoreException {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance(algorithm, "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        keyPairGenerator.generateKeyPair();
        mAliasesToDelete.add(spec.getKeystoreAlias());

        return mKeyStore.getCertificateChain(spec.getKeystoreAlias());
    }

    private void verifyCombinedChain(boolean useStrongBox, Certificate[] attestKeyCertChain,
            Certificate[] attestedKeyCertChain) throws GeneralSecurityException {
        Certificate[] combinedChain = Stream.concat(Arrays.stream(attestedKeyCertChain),
                                                    Arrays.stream(attestKeyCertChain))
                                              .toArray(Certificate[] ::new);

        int i = 0;
        for (Certificate cert : combinedChain) {
            Log.e(TAG, "Certificate " + i + ": " + cert);
            ++i;
        }

        verifyCertificateChain((Certificate[]) combinedChain, useStrongBox);
    }
}
