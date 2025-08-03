/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.keystore.cts.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemProperties;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.test.MoreAsserts;
import android.text.TextUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.util.HexDump;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

public class TestUtils {

    public static final String EXPECTED_CRYPTO_OP_PROVIDER_NAME = "AndroidKeyStoreBCWorkaround";
    public static final String EXPECTED_PROVIDER_NAME = "AndroidKeyStore";

    public static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;

    private TestUtils() {}

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    public static File getFilesDir() {
        Context context = getContext();
        final String packageName = context.getPackageName();
        return new File(context.getFilesDir(), packageName);
    }

    static public void assumeStrongBox() {
        PackageManager packageManager =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
        assumeTrue("Can only test if we have StrongBox",
                packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE));
    }

    /**
     * Returns 0 if not implemented. Otherwise returns the feature version.
     */
    public static int getFeatureVersionKeystore(Context appContext, boolean useStrongbox) {
        if (useStrongbox) {
            return getFeatureVersionKeystoreStrongBox(appContext);
        }
        return getFeatureVersionKeystore(appContext);
    }

    // Returns 0 if not implemented. Otherwise returns the feature version.
    //
    public static int getFeatureVersionKeystore(Context appContext) {
        PackageManager pm = appContext.getPackageManager();

        int featureVersionFromPm = 0;
        if (pm.hasSystemFeature(PackageManager.FEATURE_HARDWARE_KEYSTORE)) {
            FeatureInfo info = null;
            FeatureInfo[] infos = pm.getSystemAvailableFeatures();
            for (int n = 0; n < infos.length; n++) {
                FeatureInfo i = infos[n];
                if (i.name.equals(PackageManager.FEATURE_HARDWARE_KEYSTORE)) {
                    info = i;
                    break;
                }
            }
            if (info != null) {
                featureVersionFromPm = info.version;
            }
        }

        return featureVersionFromPm;
    }

    // Returns 0 if not implemented. Otherwise returns the feature version.
    //
    public static int getFeatureVersionKeystoreStrongBox(Context appContext) {
        PackageManager pm = appContext.getPackageManager();

        int featureVersionFromPm = 0;
        if (pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            FeatureInfo info = null;
            FeatureInfo[] infos = pm.getSystemAvailableFeatures();
            for (int n = 0; n < infos.length; n++) {
                FeatureInfo i = infos[n];
                if (i.name.equals(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
                    info = i;
                    break;
                }
            }
            if (info != null) {
                featureVersionFromPm = info.version;
            }
        }

        return featureVersionFromPm;
    }

    /**
     * Asserts that the given key is supported by KeyMint after a given (inclusive) version. The
     * assertion checks that:
     * 1. The current keystore feature version is less than <code>version</code> and
     *    <code>keyInfo</code> is implemented in software.
     *    OR
     * 2. The current keystore feature version is greater than or equal to <code>version</code>,
     *    and <code>keyInfo</code> is implemented by KeyMint.
     */
    public static void assertImplementedByKeyMintAfter(KeyInfo keyInfo, int version)
            throws Exception {
        // ECDSA keys are always implemented in keymaster since v1, so we can use an ECDSA
        // to check whether the backend is implemented in HW or is SW-emulated.
        int ecdsaSecurityLevel;
        try {
            KeyPairGenerator kpg =
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            kpg.initialize(
                    new KeyGenParameterSpec.Builder("ecdsa-test-key",
                            KeyProperties.PURPOSE_SIGN).build());
            KeyPair kp = kpg.generateKeyPair();
            KeyFactory factory = KeyFactory.getInstance(kp.getPrivate().getAlgorithm(),
                    "AndroidKeyStore");
            ecdsaSecurityLevel = factory.getKeySpec(kp.getPrivate(),
                    KeyInfo.class).getSecurityLevel();
        } finally {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry("ecdsa-test-key");
        }

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (getFeatureVersionKeystore(context) >= version) {
            Assert.assertEquals(keyInfo.getSecurityLevel(), ecdsaSecurityLevel);
        } else {
            Assert.assertEquals(keyInfo.getSecurityLevel(),
                    KeyProperties.SECURITY_LEVEL_SOFTWARE);
        }
    }


    /**
     * Returns whether 3DES KeyStore tests should run on this device. 3DES support was added in
     * KeyMaster 4.0 and there should be no software fallback on earlier KeyMaster versions.
     */
    public static boolean supports3DES() {
        return "true".equals(SystemProperties.get("ro.hardware.keystore_desede"));
    }

    /**
     * Returns ro.vendor.api_level.
     */
    public static int getVendorApiLevel() {
        return SystemProperties.getInt("ro.vendor.api_level", 0);
    }

    /**
     * Returns whether the device has a StrongBox backed KeyStore.
     */
    public static boolean hasStrongBox(Context context) {
        return context.getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);
    }

    /**
     * Asserts the the key algorithm and algorithm-specific parameters of the two keys in the
     * provided pair match.
     */
    public static void assertKeyPairSelfConsistent(KeyPair keyPair) {
        assertKeyPairSelfConsistent(keyPair.getPublic(), keyPair.getPrivate());
    }

    /**
     * Asserts the the key algorithm and public algorithm-specific parameters of the two provided
     * keys match.
     */
    public static void assertKeyPairSelfConsistent(PublicKey publicKey, PrivateKey privateKey) {
        assertNotNull(publicKey);
        assertNotNull(privateKey);
        assertEquals(publicKey.getAlgorithm(), privateKey.getAlgorithm());
        String keyAlgorithm = publicKey.getAlgorithm();
        if ("EC".equalsIgnoreCase(keyAlgorithm)) {
            assertTrue("EC public key must be instanceof ECKey: "
                    + publicKey.getClass().getName(),
                    publicKey instanceof ECKey);
            assertTrue("EC private key must be instanceof ECKey: "
                    + privateKey.getClass().getName(),
                    privateKey instanceof ECKey);
            assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                    "Private key must have the same EC parameters as public key",
                    ((ECKey) publicKey).getParams(), ((ECKey) privateKey).getParams());
        } else if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            assertTrue("RSA public key must be instance of RSAKey: "
                    + publicKey.getClass().getName(),
                    publicKey instanceof RSAKey);
            assertTrue("RSA private key must be instance of RSAKey: "
                    + privateKey.getClass().getName(),
                    privateKey instanceof RSAKey);
            assertEquals("Private and public key must have the same RSA modulus",
                    ((RSAKey) publicKey).getModulus(), ((RSAKey) privateKey).getModulus());
        } else if ("XDH".equalsIgnoreCase(keyAlgorithm)) {
            // TODO This block should verify that public and private keys are instance of
            //  java.security.interfaces.XECKey, And below code should be uncommented once
            //  com.android.org.conscrypt.OpenSSLX25519PublicKey implements XECKey (b/214203951)
            /*assertTrue("XDH public key must be instance of XECKey: "
                            + publicKey.getClass().getName(),
                    publicKey instanceof XECKey);
            assertTrue("XDH private key must be instance of XECKey: "
                            + privateKey.getClass().getName(),
                    privateKey instanceof XECKey);*/
            assertFalse("XDH public key must not be instance of RSAKey: "
                            + publicKey.getClass().getName(),
                    publicKey instanceof RSAKey);
            assertFalse("XDH private key must not be instance of RSAKey: "
                            + privateKey.getClass().getName(),
                    privateKey instanceof RSAKey);
            assertFalse("XDH public key must not be instanceof ECKey: "
                            + publicKey.getClass().getName(),
                    publicKey instanceof ECKey);
            assertFalse("XDH private key must not be instanceof ECKey: "
                            + privateKey.getClass().getName(),
                    privateKey instanceof ECKey);
        } else {
            fail("Unsuported key algorithm: " + keyAlgorithm);
        }
    }

    public static int getKeySizeBits(Key key) {
        if (key instanceof ECKey) {
            return ((ECKey) key).getParams().getCurve().getField().getFieldSize();
        } else if (key instanceof RSAKey) {
            return ((RSAKey) key).getModulus().bitLength();
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + key.getClass());
        }
    }

    public static void assertKeySize(int expectedSizeBits, KeyPair keyPair) {
        assertEquals(expectedSizeBits, getKeySizeBits(keyPair.getPrivate()));
        assertEquals(expectedSizeBits, getKeySizeBits(keyPair.getPublic()));
    }

    /**
     * Asserts that the provided key pair is an Android Keystore key pair stored under the provided
     * alias.
     */
    public static void assertKeyStoreKeyPair(KeyStore keyStore, String alias, KeyPair keyPair) {
        assertKeyMaterialExportable(keyPair.getPublic());
        assertKeyMaterialNotExportable(keyPair.getPrivate());
        assertTransparentKey(keyPair.getPublic());
        assertOpaqueKey(keyPair.getPrivate());

        KeyStore.Entry entry;
        Certificate cert;
        try {
            entry = keyStore.getEntry(alias, null);
            cert = keyStore.getCertificate(alias);
        } catch (KeyStoreException | UnrecoverableEntryException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to load entry: " + alias, e);
        }
        assertNotNull(entry);

        assertTrue(entry instanceof KeyStore.PrivateKeyEntry);
        KeyStore.PrivateKeyEntry privEntry = (KeyStore.PrivateKeyEntry) entry;
        assertEquals(cert, privEntry.getCertificate());
        assertTrue("Certificate must be an X.509 certificate: " + cert.getClass(),
                cert instanceof X509Certificate);
        final X509Certificate x509Cert = (X509Certificate) cert;

        PrivateKey keystorePrivateKey = privEntry.getPrivateKey();
        PublicKey keystorePublicKey = cert.getPublicKey();
        assertEquals(keyPair.getPrivate(), keystorePrivateKey);
        assertTrue("Key1:\n" + HexDump.dumpHexString(keyPair.getPublic().getEncoded())
                + "\nKey2:\n" + HexDump.dumpHexString(keystorePublicKey.getEncoded()) + "\n",
                Arrays.equals(keyPair.getPublic().getEncoded(), keystorePublicKey.getEncoded()));


        assertEquals(
                "Public key used to sign certificate should have the same algorithm as in KeyPair",
                keystorePublicKey.getAlgorithm(), x509Cert.getPublicKey().getAlgorithm());

        Certificate[] chain = privEntry.getCertificateChain();
        if (chain.length == 0) {
            fail("Empty certificate chain");
            return;
        }
        assertEquals(cert, chain[0]);
    }


    private static void assertKeyMaterialExportable(Key key) {
        if (key instanceof PublicKey) {
            assertEquals("X.509", key.getFormat());
        } else if (key instanceof PrivateKey) {
            assertEquals("PKCS#8", key.getFormat());
        } else if (key instanceof SecretKey) {
            assertEquals("RAW", key.getFormat());
        } else {
            fail("Unsupported key type: " + key.getClass().getName());
        }
        byte[] encodedForm = key.getEncoded();
        assertNotNull(encodedForm);
        if (encodedForm.length == 0) {
            fail("Empty encoded form");
        }
    }

    private static void assertKeyMaterialNotExportable(Key key) {
        assertEquals(null, key.getFormat());
        assertEquals(null, key.getEncoded());
    }

    private static void assertOpaqueKey(Key key) {
        assertFalse(key.getClass().getName() + " is a transparent key", isTransparentKey(key));
    }

    private static void assertTransparentKey(Key key) {
        assertTrue(key.getClass().getName() + " is not a transparent key", isTransparentKey(key));
    }

    private static boolean isTransparentKey(Key key) {
        if (key instanceof PrivateKey) {
            return (key instanceof ECPrivateKey) || (key instanceof RSAPrivateKey);
        } else if (key instanceof PublicKey) {
            return (key instanceof ECPublicKey) || (key instanceof RSAPublicKey);
        } else if (key instanceof SecretKey) {
            return (key instanceof SecretKeySpec);
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + key.getClass().getName());
        }
    }

    public static void assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
            ECParameterSpec expected, ECParameterSpec actual) {
        assertECParameterSpecEqualsIgnoreSeedIfNotPresent(null, expected, actual);
    }

    public static void assertECParameterSpecEqualsIgnoreSeedIfNotPresent(String message,
            ECParameterSpec expected, ECParameterSpec actual) {
        EllipticCurve expectedCurve = expected.getCurve();
        EllipticCurve actualCurve = actual.getCurve();
        String msgPrefix = (message != null) ? message + ": " : "";
        assertEquals(msgPrefix + "curve field", expectedCurve.getField(), actualCurve.getField());
        assertEquals(msgPrefix + "curve A", expectedCurve.getA(), actualCurve.getA());
        assertEquals(msgPrefix + "curve B", expectedCurve.getB(), actualCurve.getB());
        assertEquals(msgPrefix + "order", expected.getOrder(), actual.getOrder());
        assertEquals(msgPrefix + "generator",
                expected.getGenerator(), actual.getGenerator());
        assertEquals(msgPrefix + "cofactor", expected.getCofactor(), actual.getCofactor());

        // If present, the seed must be the same
        byte[] expectedSeed = expectedCurve.getSeed();
        byte[] actualSeed = expectedCurve.getSeed();
        if ((expectedSeed != null) && (actualSeed != null)) {
            MoreAsserts.assertEquals(expectedSeed, actualSeed);
        }
    }

    public static KeyInfo getKeyInfo(Key key) throws InvalidKeySpecException, NoSuchAlgorithmException,
            NoSuchProviderException {
        if ((key instanceof PrivateKey) || (key instanceof PublicKey)) {
            return KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore")
                    .getKeySpec(key, KeyInfo.class);
        } else if (key instanceof SecretKey) {
            return (KeyInfo) SecretKeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore")
                    .getKeySpec((SecretKey) key, KeyInfo.class);
        } else {
            throw new IllegalArgumentException("Unexpected key type: " + key.getClass());
        }
    }

    public static <T> void assertContentsInAnyOrder(Iterable<T> actual, T... expected) {
        assertContentsInAnyOrder(null, actual, expected);
    }

    public static <T> void assertContentsInAnyOrder(String message, Iterable<T> actual, T... expected) {
        Map<T, Integer> actualFreq = getFrequencyTable(actual);
        Map<T, Integer> expectedFreq = getFrequencyTable(expected);
        if (actualFreq.equals(expectedFreq)) {
            return;
        }

        Map<T, Integer> extraneousFreq = new HashMap<T, Integer>();
        for (Map.Entry<T, Integer> actualEntry : actualFreq.entrySet()) {
            int actualCount = actualEntry.getValue();
            Integer expectedCount = expectedFreq.get(actualEntry.getKey());
            int diff = actualCount - ((expectedCount != null) ? expectedCount : 0);
            if (diff > 0) {
                extraneousFreq.put(actualEntry.getKey(), diff);
            }
        }

        Map<T, Integer> missingFreq = new HashMap<T, Integer>();
        for (Map.Entry<T, Integer> expectedEntry : expectedFreq.entrySet()) {
            int expectedCount = expectedEntry.getValue();
            Integer actualCount = actualFreq.get(expectedEntry.getKey());
            int diff = expectedCount - ((actualCount != null) ? actualCount : 0);
            if (diff > 0) {
                missingFreq.put(expectedEntry.getKey(), diff);
            }
        }

        List<T> extraneous = frequencyTableToValues(extraneousFreq);
        List<T> missing = frequencyTableToValues(missingFreq);
        StringBuilder result = new StringBuilder();
        String delimiter = "";
        if (message != null) {
            result.append(message).append(".");
            delimiter = " ";
        }
        if (!missing.isEmpty()) {
            result.append(delimiter).append("missing: " + missing);
            delimiter = ", ";
        }
        if (!extraneous.isEmpty()) {
            result.append(delimiter).append("extraneous: " + extraneous);
        }
        fail(result.toString());
    }

    private static <T> Map<T, Integer> getFrequencyTable(Iterable<T> values) {
        Map<T, Integer> result = new HashMap<T, Integer>();
        for (T value : values) {
            Integer count = result.get(value);
            if (count == null) {
                count = 1;
            } else {
                count++;
            }
            result.put(value, count);
        }
        return result;
    }

    private static <T> Map<T, Integer> getFrequencyTable(T... values) {
        Map<T, Integer> result = new HashMap<T, Integer>();
        for (T value : values) {
            Integer count = result.get(value);
            if (count == null) {
                count = 1;
            } else {
                count++;
            }
            result.put(value, count);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static <T> List<T> frequencyTableToValues(Map<T, Integer> table) {
        if (table.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<T>();
        boolean comparableValues = true;
        for (Map.Entry<T, Integer> entry : table.entrySet()) {
            T value = entry.getKey();
            if (!(value instanceof Comparable)) {
                comparableValues = false;
            }
            int frequency = entry.getValue();
            for (int i = 0; i < frequency; i++) {
                result.add(value);
            }
        }

        if (comparableValues) {
            sortAssumingComparable(result);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void sortAssumingComparable(List<?> values) {
        Collections.sort((List<Comparable>)values);
    }

    public static String[] toLowerCase(String... values) {
        if (values == null) {
            return null;
        }
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            result[i] = (value != null) ? value.toLowerCase() : null;
        }
        return result;
    }

    public static PrivateKey getRawResPrivateKey(Context context, int resId) throws Exception {
        byte[] pkcs8EncodedForm;
        try (InputStream in = context.getResources().openRawResource(resId)) {
            pkcs8EncodedForm = drain(in);
        }
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pkcs8EncodedForm);

        String[] algorithms = new String[] {"EC", "RSA", "XDH"};
        for (String algo : algorithms) {
            try {
                return KeyFactory.getInstance(algo).generatePrivate(privateKeySpec);
            } catch (InvalidKeySpecException e) {
            }
        }
        throw new InvalidKeySpecException(
                "The key should be one of " + Arrays.toString(algorithms));
    }

    public static X509Certificate getRawResX509Certificate(Context context, int resId) throws Exception {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        }
    }

    public static KeyPair importIntoAndroidKeyStore(
            String alias,
            PrivateKey privateKey,
            Certificate certificate,
            KeyProtection keyProtection) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.setEntry(alias,
                new KeyStore.PrivateKeyEntry(privateKey, new Certificate[] {certificate}),
                keyProtection);
        return new KeyPair(
                keyStore.getCertificate(alias).getPublicKey(),
                (PrivateKey) keyStore.getKey(alias, null));
    }

    public static ImportedKey importIntoAndroidKeyStore(
            String alias,
            SecretKey key,
            KeyProtection keyProtection) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.setEntry(alias,
                new KeyStore.SecretKeyEntry(key),
                keyProtection);
        return new ImportedKey(alias, key, (SecretKey) keyStore.getKey(alias, null));
    }

    public static ImportedKey importIntoAndroidKeyStore(
            String alias, Context context, int privateResId, int certResId, KeyProtection params)
                    throws Exception {
        Certificate originalCert = TestUtils.getRawResX509Certificate(context, certResId);
        PublicKey originalPublicKey = originalCert.getPublicKey();
        PrivateKey originalPrivateKey = TestUtils.getRawResPrivateKey(context, privateResId);

        // Check that the domain parameters match between the private key and the public key. This
        // is to catch accidental errors where a test provides the wrong resource ID as one of the
        // parameters.
        if (!originalPublicKey.getAlgorithm().equalsIgnoreCase(originalPrivateKey.getAlgorithm())) {
            throw new IllegalArgumentException("Key algorithm mismatch."
                    + " Public: " + originalPublicKey.getAlgorithm()
                    + ", private: " + originalPrivateKey.getAlgorithm());
        }
        assertKeyPairSelfConsistent(originalPublicKey, originalPrivateKey);

        KeyPair keystoreBacked = TestUtils.importIntoAndroidKeyStore(
                alias, originalPrivateKey, originalCert,
                params);
        assertKeyPairSelfConsistent(keystoreBacked);
        assertKeyPairSelfConsistent(keystoreBacked.getPublic(), originalPrivateKey);
        return new ImportedKey(
                alias,
                new KeyPair(originalCert.getPublicKey(), originalPrivateKey),
                keystoreBacked);
    }

    /** Returns true if a key with the given alias exists. */
    public static boolean keyExists(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        try {
            return keyStore.getEntry(alias, null) != null;
        } catch (UnrecoverableKeyException e) {
            return false;
        }
    }

    public static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int chunkSize;
        while ((chunkSize = in.read(buffer)) != -1) {
            result.write(buffer, 0, chunkSize);
        }
        return result.toByteArray();
    }

    public static KeyProtection.Builder buildUpon(KeyProtection params) {
        return buildUponInternal(params, null);
    }

    public static KeyProtection.Builder buildUpon(KeyProtection params, int newPurposes) {
        return buildUponInternal(params, newPurposes);
    }

    public static KeyProtection.Builder buildUpon(
            KeyProtection.Builder builder) {
        return buildUponInternal(builder.build(), null);
    }

    public static KeyProtection.Builder buildUpon(
            KeyProtection.Builder builder, int newPurposes) {
        return buildUponInternal(builder.build(), newPurposes);
    }

    private static KeyProtection.Builder buildUponInternal(
            KeyProtection spec, Integer newPurposes) {
        int purposes = (newPurposes == null) ? spec.getPurposes() : newPurposes;
        KeyProtection.Builder result = new KeyProtection.Builder(purposes);
        result.setBlockModes(spec.getBlockModes());
        if (spec.isDigestsSpecified()) {
            result.setDigests(spec.getDigests());
        }
        result.setEncryptionPaddings(spec.getEncryptionPaddings());
        result.setSignaturePaddings(spec.getSignaturePaddings());
        result.setKeyValidityStart(spec.getKeyValidityStart());
        result.setKeyValidityForOriginationEnd(spec.getKeyValidityForOriginationEnd());
        result.setKeyValidityForConsumptionEnd(spec.getKeyValidityForConsumptionEnd());
        result.setRandomizedEncryptionRequired(spec.isRandomizedEncryptionRequired());
        result.setUserAuthenticationRequired(spec.isUserAuthenticationRequired());
        result.setUserAuthenticationValidityDurationSeconds(
                spec.getUserAuthenticationValidityDurationSeconds());
        result.setBoundToSpecificSecureUserId(spec.getBoundToSpecificSecureUserId());
        return result;
    }

    public static KeyGenParameterSpec.Builder buildUpon(KeyGenParameterSpec spec) {
        return buildUponInternal(spec, null);
    }

    public static KeyGenParameterSpec.Builder buildUpon(KeyGenParameterSpec spec, int newPurposes) {
        return buildUponInternal(spec, newPurposes);
    }

    public static KeyGenParameterSpec.Builder buildUpon(
            KeyGenParameterSpec.Builder builder) {
        return buildUponInternal(builder.build(), null);
    }

    public static KeyGenParameterSpec.Builder buildUpon(
            KeyGenParameterSpec.Builder builder, int newPurposes) {
        return buildUponInternal(builder.build(), newPurposes);
    }

    private static KeyGenParameterSpec.Builder buildUponInternal(
            KeyGenParameterSpec spec, Integer newPurposes) {
        int purposes = (newPurposes == null) ? spec.getPurposes() : newPurposes;
        KeyGenParameterSpec.Builder result =
                new KeyGenParameterSpec.Builder(spec.getKeystoreAlias(), purposes);
        if (spec.getKeySize() >= 0) {
            result.setKeySize(spec.getKeySize());
        }
        if (spec.getAlgorithmParameterSpec() != null) {
            result.setAlgorithmParameterSpec(spec.getAlgorithmParameterSpec());
        }
        result.setCertificateNotBefore(spec.getCertificateNotBefore());
        result.setCertificateNotAfter(spec.getCertificateNotAfter());
        result.setCertificateSerialNumber(spec.getCertificateSerialNumber());
        result.setCertificateSubject(spec.getCertificateSubject());
        result.setBlockModes(spec.getBlockModes());
        if (spec.isDigestsSpecified()) {
            result.setDigests(spec.getDigests());
        }
        result.setEncryptionPaddings(spec.getEncryptionPaddings());
        result.setSignaturePaddings(spec.getSignaturePaddings());
        result.setKeyValidityStart(spec.getKeyValidityStart());
        result.setKeyValidityForOriginationEnd(spec.getKeyValidityForOriginationEnd());
        result.setKeyValidityForConsumptionEnd(spec.getKeyValidityForConsumptionEnd());
        result.setRandomizedEncryptionRequired(spec.isRandomizedEncryptionRequired());
        result.setUserAuthenticationRequired(spec.isUserAuthenticationRequired());
        result.setUserAuthenticationValidityDurationSeconds(
                spec.getUserAuthenticationValidityDurationSeconds());
        return result;
    }

    public static KeyPair getKeyPairForKeyAlgorithm(String keyAlgorithm, Iterable<KeyPair> keyPairs) {
        for (KeyPair keyPair : keyPairs) {
            if (keyAlgorithm.equalsIgnoreCase(keyPair.getPublic().getAlgorithm())) {
                return keyPair;
            }
        }
        throw new IllegalArgumentException("No KeyPair for key algorithm " + keyAlgorithm);
    }

    public static Key getKeyForKeyAlgorithm(String keyAlgorithm, Iterable<? extends Key> keys) {
        for (Key key : keys) {
            if (keyAlgorithm.equalsIgnoreCase(key.getAlgorithm())) {
                return key;
            }
        }
        throw new IllegalArgumentException("No Key for key algorithm " + keyAlgorithm);
    }

    public static byte[] generateLargeKatMsg(byte[] seed, int msgSizeBytes) throws Exception {
        byte[] result = new byte[msgSizeBytes];
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        int resultOffset = 0;
        int resultRemaining = msgSizeBytes;
        while (resultRemaining > 0) {
            seed = digest.digest(seed);
            int chunkSize = Math.min(seed.length, resultRemaining);
            System.arraycopy(seed, 0, result, resultOffset, chunkSize);
            resultOffset += chunkSize;
            resultRemaining -= chunkSize;
        }
        return result;
    }

    public static byte[] leftPadWithZeroBytes(byte[] array, int length) {
        if (array.length >= length) {
            return array;
        }
        byte[] result = new byte[length];
        System.arraycopy(array, 0, result, result.length - array.length, array.length);
        return result;
    }

    public static boolean contains(int[] array, int value) {
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHmacAlgorithm(String algorithm) {
        return algorithm.toUpperCase(Locale.US).startsWith("HMAC");
    }

    public static String getHmacAlgorithmDigest(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        if (!algorithmUpperCase.startsWith("HMAC")) {
            return null;
        }
        String result = algorithmUpperCase.substring("HMAC".length());
        if (result.startsWith("SHA")) {
            result = "SHA-" + result.substring("SHA".length());
        }
        return result;
    }

    public static String getKeyAlgorithm(String transformation) {
        try {
            return getCipherKeyAlgorithm(transformation);
        } catch (IllegalArgumentException e) {

        }
        try {
            return getSignatureAlgorithmKeyAlgorithm(transformation);
        } catch (IllegalArgumentException e) {

        }
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.equals("EC")) {
            return KeyProperties.KEY_ALGORITHM_EC;
        }
        if (transformationUpperCase.equals("RSA")) {
            return KeyProperties.KEY_ALGORITHM_RSA;
        }
        if (transformationUpperCase.equals("DESEDE")) {
            return KeyProperties.KEY_ALGORITHM_3DES;
        }
        if (transformationUpperCase.equals("AES")) {
            return KeyProperties.KEY_ALGORITHM_AES;
        }
        if (transformationUpperCase.startsWith("HMAC")) {
            if (transformation.endsWith("SHA1")) {
                return KeyProperties.KEY_ALGORITHM_HMAC_SHA1;
            } else if (transformation.endsWith("SHA224")) {
                return KeyProperties.KEY_ALGORITHM_HMAC_SHA224;
            } else if (transformation.endsWith("SHA256")) {
                return KeyProperties.KEY_ALGORITHM_HMAC_SHA256;
            } else if (transformation.endsWith("SHA384")) {
                return KeyProperties.KEY_ALGORITHM_HMAC_SHA384;
            } else if (transformation.endsWith("SHA512")) {
                return KeyProperties.KEY_ALGORITHM_HMAC_SHA512;
            }
        }
        throw new IllegalArgumentException("Unsupported transformation: " + transformation);
    }

    public static String getCipherKeyAlgorithm(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.startsWith("AES/")) {
            return KeyProperties.KEY_ALGORITHM_AES;
        } else if (transformationUpperCase.startsWith("DESEDE/")) {
            return KeyProperties.KEY_ALGORITHM_3DES;
        } else if (transformationUpperCase.startsWith("RSA/")) {
            return KeyProperties.KEY_ALGORITHM_RSA;
        } else {
            throw new IllegalArgumentException("Unsupported transformation: " + transformation);
        }
    }

    public static boolean isCipherSymmetric(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.startsWith("AES/") || transformationUpperCase.startsWith(
                "DESEDE/")) {
            return true;
        } else if (transformationUpperCase.startsWith("RSA/")) {
            return false;
        } else {
            throw new IllegalArgumentException("YYZ: Unsupported transformation: " + transformation);
        }
    }

    public static String getCipherDigest(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.contains("/OAEP")) {
            if (transformationUpperCase.endsWith("/OAEPPADDING")) {
                return KeyProperties.DIGEST_SHA1;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-1ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA1;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-224ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA224;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-256ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA256;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-384ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA384;
            } else if (transformationUpperCase.endsWith(
                    "/OAEPWITHSHA-512ANDMGF1PADDING")) {
                return KeyProperties.DIGEST_SHA512;
            } else {
                throw new RuntimeException("Unsupported OAEP padding scheme: "
                        + transformation);
            }
        } else {
            return null;
        }
    }

    public static String getCipherEncryptionPadding(String transformation) {
        String transformationUpperCase = transformation.toUpperCase(Locale.US);
        if (transformationUpperCase.endsWith("/NOPADDING")) {
            return KeyProperties.ENCRYPTION_PADDING_NONE;
        } else if (transformationUpperCase.endsWith("/PKCS7PADDING")) {
            return KeyProperties.ENCRYPTION_PADDING_PKCS7;
        } else if (transformationUpperCase.endsWith("/PKCS1PADDING")) {
            return KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;
        } else if (transformationUpperCase.split("/")[2].startsWith("OAEP")) {
            return KeyProperties.ENCRYPTION_PADDING_RSA_OAEP;
        } else {
            throw new IllegalArgumentException("Unsupported transformation: " + transformation);
        }
    }

    public static String getCipherBlockMode(String transformation) {
        return transformation.split("/")[1].toUpperCase(Locale.US);
    }

    public static String getSignatureAlgorithmDigest(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        int withIndex = algorithmUpperCase.indexOf("WITH");
        if (withIndex == -1) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
        String digest = algorithmUpperCase.substring(0, withIndex);
        if (digest.startsWith("SHA")) {
            digest = "SHA-" + digest.substring("SHA".length());
        }
        return digest;
    }

    public static String getSignatureAlgorithmPadding(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        if (algorithmUpperCase.endsWith("WITHECDSA")) {
            return null;
        } else if (algorithmUpperCase.endsWith("WITHRSA")) {
            return KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
        } else if (algorithmUpperCase.endsWith("WITHRSA/PSS")) {
            return KeyProperties.SIGNATURE_PADDING_RSA_PSS;
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }

    public static String getSignatureAlgorithmKeyAlgorithm(String algorithm) {
        String algorithmUpperCase = algorithm.toUpperCase(Locale.US);
        if (algorithmUpperCase.endsWith("WITHECDSA")) {
            return KeyProperties.KEY_ALGORITHM_EC;
        } else if ((algorithmUpperCase.endsWith("WITHRSA"))
                || (algorithmUpperCase.endsWith("WITHRSA/PSS"))) {
            return KeyProperties.KEY_ALGORITHM_RSA;
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }

    public static boolean isKeyLongEnoughForSignatureAlgorithm(String algorithm, int keySizeBits) {
        String keyAlgorithm = getSignatureAlgorithmKeyAlgorithm(algorithm);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            // No length restrictions for ECDSA
            return true;
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            String digest = getSignatureAlgorithmDigest(algorithm);
            int digestOutputSizeBits = getDigestOutputSizeBits(digest);
            if (digestOutputSizeBits == -1) {
                // No digesting -- assume the key is long enough for the message
                return true;
            }
            String paddingScheme = getSignatureAlgorithmPadding(algorithm);
            int paddingOverheadBytes;
            if (KeyProperties.SIGNATURE_PADDING_RSA_PKCS1.equalsIgnoreCase(paddingScheme)) {
                paddingOverheadBytes = 30;
            } else if (KeyProperties.SIGNATURE_PADDING_RSA_PSS.equalsIgnoreCase(paddingScheme)) {
                int saltSizeBytes = (digestOutputSizeBits + 7) / 8;
                paddingOverheadBytes = saltSizeBytes + 1;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported signature padding scheme: " + paddingScheme);
            }
            int minKeySizeBytes = paddingOverheadBytes + (digestOutputSizeBits + 7) / 8 + 1;
            int keySizeBytes = keySizeBits / 8;
            return keySizeBytes >= minKeySizeBytes;
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    public static boolean isKeyLongEnoughForSignatureAlgorithm(String algorithm, Key key) {
        return isKeyLongEnoughForSignatureAlgorithm(algorithm, getKeySizeBits(key));
    }

    public static int getMaxSupportedPlaintextInputSizeBytes(String transformation, int keySizeBits) {
        String encryptionPadding = getCipherEncryptionPadding(transformation);
        int modulusSizeBytes = (keySizeBits + 7) / 8;
        if (KeyProperties.ENCRYPTION_PADDING_NONE.equalsIgnoreCase(encryptionPadding)) {
            return modulusSizeBytes - 1;
        } else if (KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1.equalsIgnoreCase(
                encryptionPadding)) {
            return modulusSizeBytes - 11;
        } else if (KeyProperties.ENCRYPTION_PADDING_RSA_OAEP.equalsIgnoreCase(
                encryptionPadding)) {
            String digest = getCipherDigest(transformation);
            int digestOutputSizeBytes = (getDigestOutputSizeBits(digest) + 7) / 8;
            return modulusSizeBytes - 2 * digestOutputSizeBytes - 2;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported encryption padding scheme: " + encryptionPadding);
        }

    }

    public static int getMaxSupportedPlaintextInputSizeBytes(String transformation, Key key) {
        String keyAlgorithm = getCipherKeyAlgorithm(transformation);
        if (KeyProperties.KEY_ALGORITHM_AES.equalsIgnoreCase(keyAlgorithm)
                || KeyProperties.KEY_ALGORITHM_3DES.equalsIgnoreCase(keyAlgorithm)) {
            return Integer.MAX_VALUE;
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            return getMaxSupportedPlaintextInputSizeBytes(transformation, getKeySizeBits(key));
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    public static int getDigestOutputSizeBits(String digest) {
        if (KeyProperties.DIGEST_NONE.equals(digest)) {
            return -1;
        } else if (KeyProperties.DIGEST_MD5.equals(digest)) {
            return 128;
        } else if (KeyProperties.DIGEST_SHA1.equals(digest)) {
            return 160;
        } else if (KeyProperties.DIGEST_SHA224.equals(digest)) {
            return 224;
        } else if (KeyProperties.DIGEST_SHA256.equals(digest)) {
            return 256;
        } else if (KeyProperties.DIGEST_SHA384.equals(digest)) {
            return 384;
        } else if (KeyProperties.DIGEST_SHA512.equals(digest)) {
            return 512;
        } else {
            throw new IllegalArgumentException("Unsupported digest: " + digest);
        }
    }

    public static byte[] concat(byte[] arr1, byte[] arr2) {
        return concat(arr1, 0, (arr1 != null) ? arr1.length : 0,
                arr2, 0, (arr2 != null) ? arr2.length : 0);
    }

    public static byte[] concat(byte[] arr1, int offset1, int len1,
            byte[] arr2, int offset2, int len2) {
        if (len1 == 0) {
            return subarray(arr2, offset2, len2);
        } else if (len2 == 0) {
            return subarray(arr1, offset1, len1);
        }
        byte[] result = new byte[len1 + len2];
        if (len1 > 0) {
            System.arraycopy(arr1, offset1, result, 0, len1);
        }
        if (len2 > 0) {
            System.arraycopy(arr2, offset2, result, len1, len2);
        }
        return result;
    }

    public static byte[] subarray(byte[] arr, int offset, int len) {
        if (len == 0) {
            return EmptyArray.BYTE;
        }
        if ((offset == 0) && (arr.length == len)) {
            return arr;
        }
        byte[] result = new byte[len];
        System.arraycopy(arr, offset, result, 0, len);
        return result;
    }

    public static KeyProtection getMinimalWorkingImportParametersForSigningingWith(
            String signatureAlgorithm) {
        String keyAlgorithm = getSignatureAlgorithmKeyAlgorithm(signatureAlgorithm);
        String digest = getSignatureAlgorithmDigest(signatureAlgorithm);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            return new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                    .setDigests(digest)
                    .build();
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            String padding = getSignatureAlgorithmPadding(signatureAlgorithm);
            return new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                    .setDigests(digest)
                    .setSignaturePaddings(padding)
                    .build();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported signature algorithm: " + signatureAlgorithm);
        }
    }

    public static KeyProtection getMinimalWorkingImportParametersWithLimitedUsageForSigningingWith(
            String signatureAlgorithm, int maxUsageCount) {
        String keyAlgorithm = getSignatureAlgorithmKeyAlgorithm(signatureAlgorithm);
        String digest = getSignatureAlgorithmDigest(signatureAlgorithm);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            return new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                    .setDigests(digest)
                    .setMaxUsageCount(maxUsageCount)
                    .build();
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            String padding = getSignatureAlgorithmPadding(signatureAlgorithm);
            return new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                    .setDigests(digest)
                    .setSignaturePaddings(padding)
                    .setMaxUsageCount(maxUsageCount)
                    .build();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported signature algorithm: " + signatureAlgorithm);
        }
    }

    public static KeyProtection getMinimalWorkingImportParametersForCipheringWith(
            String transformation, int purposes) {
        return getMinimalWorkingImportParametersForCipheringWith(transformation, purposes, false);
    }

    public static KeyProtection getMinimalWorkingImportParametersForCipheringWith(
            String transformation, int purposes, boolean ivProvidedWhenEncrypting) {
        return getMinimalWorkingImportParametersForCipheringWith(transformation, purposes,
            ivProvidedWhenEncrypting, false, false);
    }

    public static KeyProtection getMinimalWorkingImportParametersForCipheringWith(
            String transformation, int purposes, boolean ivProvidedWhenEncrypting,
            boolean isUnlockedDeviceRequired, boolean isUserAuthRequired) {
        String keyAlgorithm = TestUtils.getCipherKeyAlgorithm(transformation);
        if (KeyProperties.KEY_ALGORITHM_AES.equalsIgnoreCase(keyAlgorithm)
            || KeyProperties.KEY_ALGORITHM_3DES.equalsIgnoreCase(keyAlgorithm)) {
            String encryptionPadding = TestUtils.getCipherEncryptionPadding(transformation);
            String blockMode = TestUtils.getCipherBlockMode(transformation);
            boolean randomizedEncryptionRequired = true;
            if (KeyProperties.BLOCK_MODE_ECB.equalsIgnoreCase(blockMode)) {
                randomizedEncryptionRequired = false;
            } else if ((ivProvidedWhenEncrypting)
                    && ((purposes & KeyProperties.PURPOSE_ENCRYPT) != 0)) {
                randomizedEncryptionRequired = false;
            }
            return new KeyProtection.Builder(
                    purposes)
                    .setBlockModes(blockMode)
                    .setEncryptionPaddings(encryptionPadding)
                    .setRandomizedEncryptionRequired(randomizedEncryptionRequired)
                    .setUnlockedDeviceRequired(isUnlockedDeviceRequired)
                    .setUserAuthenticationRequired(isUserAuthRequired)
                    .setUserAuthenticationValidityDurationSeconds(3600)
                    .build();
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            String digest = TestUtils.getCipherDigest(transformation);
            String encryptionPadding = TestUtils.getCipherEncryptionPadding(transformation);
            boolean randomizedEncryptionRequired =
                    !KeyProperties.ENCRYPTION_PADDING_NONE.equalsIgnoreCase(encryptionPadding);
            // For algorithm transformation such as "RSA/ECB/OAEPWithSHA-224AndMGF1Padding",
            // Some providers uses same digest for OAEP main digest(SHA-224) and
            // MGF1 digest(SHA-224), and some providers uses main digest as (SHA-224) and
            // MGF1 digest as (SHA-1) hence adding both digest for MGF1 digest.
            String[] mgf1DigestList = null;
            if (digest != null) {
                mgf1DigestList = digest.equalsIgnoreCase("SHA-1")
                        ? new String[] {digest} : new String[] {digest, "SHA-1"};
            }
            KeyProtection.Builder keyProtectionBuilder = new KeyProtection.Builder(
                    purposes)
                    .setDigests((digest != null) ? new String[] {digest} : EmptyArray.STRING)
                    .setEncryptionPaddings(encryptionPadding)
                    .setRandomizedEncryptionRequired(randomizedEncryptionRequired)
                    .setUserAuthenticationRequired(isUserAuthRequired)
                    .setUserAuthenticationValidityDurationSeconds(3600)
                    .setUnlockedDeviceRequired(isUnlockedDeviceRequired);
            if (mgf1DigestList != null && android.security.Flags.mgf1DigestSetterV2()) {
                keyProtectionBuilder.setMgf1Digests(mgf1DigestList);
            }
            return keyProtectionBuilder.build();
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    public static byte[] getBigIntegerMagnitudeBytes(BigInteger value) {
        return removeLeadingZeroByteIfPresent(value.toByteArray());
    }

    private static byte[] removeLeadingZeroByteIfPresent(byte[] value) {
        if ((value.length < 1) || (value[0] != 0)) {
            return value;
        }
        return TestUtils.subarray(value, 1, value.length - 1);
    }

    public static byte[] generateRandomMessage(int messageSize) {
        byte[] message = new byte[messageSize];
        new SecureRandom().nextBytes(message);
        return message;
    }

    public static boolean isAttestationSupported() {
        return Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean isPropertyEmptyOrUnknown(String property) {
        return TextUtils.isEmpty(property) || property.equals(Build.UNKNOWN);
    }

    public static boolean hasSecureLockScreen(Context context) {
        PackageManager pm = context.getPackageManager();
        return (pm != null && pm.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN));
    }

    /**
     * Determines whether running build is GSI or not.
     * @return true if running build is GSI, false otherwise.
     */
    public static boolean isGsiImage() {
        final File initGsiRc = new File("/system/system_ext/etc/init/init.gsi.rc");
        return initGsiRc.exists();
    }
}
