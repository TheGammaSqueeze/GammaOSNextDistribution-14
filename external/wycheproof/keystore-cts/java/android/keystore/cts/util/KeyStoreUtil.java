/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.keystore.cts.util;

import static org.junit.Assume.assumeTrue;
import android.content.Context;
import android.security.keystore.KeyProtection;
import android.keystore.cts.util.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/** Keystore utilities */
public class KeyStoreUtil {
    // Known KeyMaster/KeyMint versions. This is the version number
    // which appear in the keymasterVersion field.
    public static final int KM_VERSION_KEYMASTER_1 = 10;
    public static final int KM_VERSION_KEYMASTER_1_1 = 11;
    public static final int KM_VERSION_KEYMASTER_2 = 20;
    public static final int KM_VERSION_KEYMASTER_3 = 30;
    public static final int KM_VERSION_KEYMASTER_4 = 40;
    public static final int KM_VERSION_KEYMASTER_4_1 = 41;
    public static final int KM_VERSION_KEYMINT_1 = 100;
    public static final int KM_VERSION_KEYMINT_2 = 200;
    public static final int KM_VERSION_KEYMINT_3 = 300;

    private static final List kmSupportedDigests = List.of("md5","sha-1","sha-224","sha-384",
                                                        "sha-256","sha-512");

    public static KeyStore saveKeysToKeystore(String alias, PublicKey pubKey, PrivateKey privKey,
            KeyProtection keyProtection) throws Exception {
        KeyPair keyPair = new KeyPair(pubKey, privKey);
        X509Certificate certificate = createCertificate(keyPair,
                                                        new X500Principal("CN=Test1"),
                                                        new X500Principal("CN=Test1"));
        Certificate[] certChain = new Certificate[]{certificate};
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.setEntry(alias,
                        new KeyStore.PrivateKeyEntry(privKey, certChain),
                        keyProtection);
        return keyStore;
    }

    public static KeyStore saveSecretKeyToKeystore(String alias, SecretKeySpec keySpec,
            KeyProtection keyProtection) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.setEntry(alias,
                        new KeyStore.SecretKeyEntry(keySpec),
                        keyProtection);
         return keyStore;
    }

    public static void cleanUpKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        for (Enumeration<String> aliases = keyStore.aliases(); aliases.hasMoreElements();) {
            String alias = aliases.nextElement();
            keyStore.deleteEntry(alias);
        }
    }

    public static int getFeatureVersionKeystore(boolean isStrongBox) {
        if (isStrongBox) {
            return TestUtils.getFeatureVersionKeystoreStrongBox(
            ApplicationProvider.getApplicationContext());
        }
        return TestUtils.getFeatureVersionKeystore(ApplicationProvider.getApplicationContext());
    }

    public static boolean hasStrongBox() {
        Context context = ApplicationProvider.getApplicationContext();
        return TestUtils.hasStrongBox(context);
    }

    public static void assumeStrongBox() {
        TestUtils.assumeStrongBox();
    }

    public static boolean isSupportedDigest(String digest, boolean isStrongBox) {
        if (isStrongBox) {
            return digest.equalsIgnoreCase("sha-256");
        }
        return kmSupportedDigests.contains(digest.toLowerCase());
    }

    public static boolean isSupportedMgfDigest(String digest, boolean isStrongBox) {
        if (isStrongBox) {
            return digest.equalsIgnoreCase("sha-1")
                    || digest.equalsIgnoreCase("sha-256");
        }
        return kmSupportedDigests.contains(digest.toLowerCase());
    }

    public static boolean isSupportedRsaKeySize(int keySize, boolean isStrongBox) {
        if (isStrongBox) {
            return keySize == 2048;
        }
        return keySize == 2048 || keySize == 3072 || keySize == 4096;
    }

    public static X509Certificate createCertificate(
            KeyPair keyPair, X500Principal subject, X500Principal issuer)
            throws OperatorCreationException, CertificateException, IOException {
        // Make the certificate valid for two days.
        long millisPerDay = 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        Date start = new Date(now - millisPerDay);
        Date end = new Date(now + millisPerDay);

        // Assign a random serial number.
        byte[] serialBytes = new byte[16];
        new SecureRandom().nextBytes(serialBytes);
        BigInteger serialNumber = new BigInteger(1, serialBytes);

        // Create the certificate builder
        X509v3CertificateBuilder x509cg =
                new X509v3CertificateBuilder(
                        X500Name.getInstance(issuer.getEncoded()),
                        serialNumber,
                        start,
                        end,
                        X500Name.getInstance(subject.getEncoded()),
                        SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));

        // Choose a signature algorithm matching the key format.
        String keyAlgorithm = keyPair.getPrivate().getAlgorithm();
        String signatureAlgorithm;
        if (keyAlgorithm.equals("RSA")) {
            signatureAlgorithm = "SHA256withRSA";
        } else if (keyAlgorithm.equals("EC")) {
            signatureAlgorithm = "SHA256withECDSA";
        } else {
            throw new IllegalArgumentException("Unknown key algorithm " + keyAlgorithm);
        }

        // Sign the certificate and generate it.
        X509CertificateHolder x509holder =
                x509cg.build(
                        new JcaContentSignerBuilder(signatureAlgorithm)
                                .build(keyPair.getPrivate()));
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate x509c =
                (X509Certificate)
                        certFactory.generateCertificate(
                                new ByteArrayInputStream(x509holder.getEncoded()));
        return x509c;
    }

    public static void assumeKeyMintV1OrNewer(boolean isStrongBox) {
        assumeTrue("Test can only run on KeyMint v1 and above",
            KeyStoreUtil.getFeatureVersionKeystore(isStrongBox) >= KM_VERSION_KEYMINT_1);
    }
}
