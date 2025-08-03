package com.google.attestationexample;

import android.os.AsyncTask;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import com.google.common.collect.ImmutableSet;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static com.google.attestationexample.RootOfTrust.KM_VERIFIED_BOOT_VERIFIED;

/**
 * AttestationTest generates an EC Key pair, with attestation, and displays the result in the
 * TextView provided to its constructor.
 */
public class AttestationTest extends AsyncTask<Void, String, Void> {
    private static final int ORIGINATION_TIME_OFFSET = 1000000;
    private static final int CONSUMPTION_TIME_OFFSET = 2000000;

    private static final int KEY_USAGE_BITSTRING_LENGTH = 9;
    private static final int KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET = 0;
    private static final int KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET = 2;
    private static final int KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET = 3;

    private static final int OS_MAJOR_VERSION_MATCH_GROUP_NAME = 1;
    private static final int OS_MINOR_VERSION_MATCH_GROUP_NAME = 2;
    private static final int OS_SUBMINOR_VERSION_MATCH_GROUP_NAME = 3;
    private static final Pattern OS_VERSION_STRING_PATTERN = Pattern
            .compile("([0-9]{1,2})(?:\\.([0-9]{1,2}))?(?:\\.([0-9]{1,2}))?(?:[^0-9.]+.*)?");

    private static final int OS_PATCH_LEVEL_YEAR_GROUP_NAME = 1;
    private static final int OS_PATCH_LEVEL_MONTH_GROUP_NAME = 2;
    private static final Pattern OS_PATCH_LEVEL_STRING_PATTERN = Pattern
            .compile("([0-9]{4})-([0-9]{2})-[0-9]{2}");

    private static final int KM_ERROR_INVALID_INPUT_LENGTH = -21;

    private static final String FINISHED = "AttestationFinished";
    private static final String FAIL = "AttestationFail";
    private static final String INFO = "AttestationInfo";
    private static final String KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17";
    private static final String KEY_USAGE_OID = "2.5.29.15";  // Standard key usage extension.

    private static final String GOOGLE_ROOT_CERTIFICATE =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIFYDCCA0igAwIBAgIJAOj6GWMU0voYMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV"
                    + "BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTYwNTI2MTYyODUyWhcNMjYwNTI0MTYy"
                    + "ODUyWjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B"
                    + "AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS"
                    + "Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7"
                    + "tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj"
                    + "nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq"
                    + "C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ"
                    + "oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O"
                    + "JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg"
                    + "sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi"
                    + "igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M"
                    + "RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E"
                    + "aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um"
                    + "AGMCAwEAAaOBpjCBozAdBgNVHQ4EFgQUNmHhAHyIBQlRi0RsR/8aTMnqTxIwHwYD"
                    + "VR0jBBgwFoAUNmHhAHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAO"
                    + "BgNVHQ8BAf8EBAMCAYYwQAYDVR0fBDkwNzA1oDOgMYYvaHR0cHM6Ly9hbmRyb2lk"
                    + "Lmdvb2dsZWFwaXMuY29tL2F0dGVzdGF0aW9uL2NybC8wDQYJKoZIhvcNAQELBQAD"
                    + "ggIBACDIw41L3KlXG0aMiS//cqrG+EShHUGo8HNsw30W1kJtjn6UBwRM6jnmiwfB"
                    + "Pb8VA91chb2vssAtX2zbTvqBJ9+LBPGCdw/E53Rbf86qhxKaiAHOjpvAy5Y3m00m"
                    + "qC0w/Zwvju1twb4vhLaJ5NkUJYsUS7rmJKHHBnETLi8GFqiEsqTWpG/6ibYCv7rY"
                    + "DBJDcR9W62BW9jfIoBQcxUCUJouMPH25lLNcDc1ssqvC2v7iUgI9LeoM1sNovqPm"
                    + "QUiG9rHli1vXxzCyaMTjwftkJLkf6724DFhuKug2jITV0QkXvaJWF4nUaHOTNA4u"
                    + "JU9WDvZLI1j83A+/xnAJUucIv/zGJ1AMH2boHqF8CY16LpsYgBt6tKxxWH00XcyD"
                    + "CdW2KlBCeqbQPcsFmWyWugxdcekhYsAWyoSf818NUsZdBWBaR/OukXrNLfkQ79Iy"
                    + "ZohZbvabO/X+MVT3rriAoKc8oE2Uws6DF+60PV7/WIPjNvXySdqspImSN78mflxD"
                    + "qwLqRBYkA3I75qppLGG9rp7UCdRjxMl8ZDBld+7yvHVgt1cVzJx9xnyGCC23Uaic"
                    + "MDSXYrB4I4WHXPGjxhZuCuPBLTdOLU8YRvMYdEvYebWHMpvwGCF6bAx3JBpIeOQ1"
                    + "wDB5y0USicV3YgYGmi+NZfhA4URSh77Yd6uuJOJENRaNVTzk\n"
                    + "-----END CERTIFICATE-----";

    @Override
    protected Void doInBackground(Void... params) {
        try {
            testEcAttestation();
        } catch (Exception e) {
            Log.e(FAIL, "Something is wrong, check detailed logcat logs:\n", e);
        }
        return null;
    }

    private void testEcAttestation() throws Exception {
        String ecCurve = "secp256r1";
        int keySize = 256;
        byte[] challenge = "challenge".getBytes();
        String keystoreAlias = "test_key";

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        keyStore.deleteEntry(keystoreAlias);

        Log.d(INFO,"Generating key pair...");
        Date startTime = new Date(new Date().getTime() - 1000);
        Log.d("****", "Start Time is: " + startTime.toString());
        Date originationEnd = new Date(startTime.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(startTime.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keystoreAlias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec(ecCurve))
                .setDigests(DIGEST_SHA256)
                .setAttestationChallenge(challenge);

        builder.setKeyValidityStart(startTime)
                .setKeyValidityForOriginationEnd(originationEnd)
                .setKeyValidityForConsumptionEnd(consumptionEnd);

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());
        Log.d(INFO, "Key pair generated\n\n");

        Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
        Log.d(INFO, "Retrieved certificate chain of length " + certificates.length + "\n");
        try {
            verifyCertificateSignatures(certificates);
        } catch (GeneralSecurityException e) {
            Log.e(FAIL, "Certificate chain does not verify.", e);
        }

        X509Certificate attestationCert = (X509Certificate) certificates[0];
        X509Certificate secureRoot = (X509Certificate) CertificateFactory
                .getInstance("X.509").generateCertificate(
                        new ByteArrayInputStream(
                                GOOGLE_ROOT_CERTIFICATE.getBytes()));
        X509Certificate rootCert = (X509Certificate) certificates[certificates.length - 1];
        if (!Arrays.equals(secureRoot.getPublicKey().getEncoded(),
                rootCert.getPublicKey().getEncoded())) {
            Log.e(FAIL, "root certificate public key does not match Google public key");
        }
        printKeyUsage(attestationCert);

        ImmutableSet<String> unexpectedExtensionOids =
                (ImmutableSet) retrieveUnexpectedExtensionOids(attestationCert);
        if (!unexpectedExtensionOids.isEmpty()) {
            Log.e(FAIL, "attestation certificate contains unexpected OIDs");
            for (String oid : unexpectedExtensionOids.toArray(new String[unexpectedExtensionOids.size()])) {
                Log.e(FAIL, "Unexpected OID: " + oid);
            }
        }

        Attestation attestation = new Attestation(attestationCert);
        if (!Arrays.equals(attestation.getAttestationChallenge(), challenge)) {
            Utils.logError("challenge mismatch\nExpected:",
                    challenge, attestation.getAttestationChallenge());
        }

        if (attestation.getAttestationSecurityLevel() != 1) {
            Utils.logError("Attestation cert reporting non-TEE security level",
                    1, attestation.getAttestationSecurityLevel());
        }
        if (attestation.getKeymasterSecurityLevel() != 1) {
            Utils.logError("Keymaster reporting non-TEE security level",
                    1, attestation.getKeymasterSecurityLevel());
        }

        checkRootOfTrust(attestation);

        Signature signer = Signature.getInstance("SHA256WithECDSA");
        KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);

        PrivateKey key = (PrivateKey) keystore.getKey(keystoreAlias, null);
        try {
            signer.initSign(key);
            signer.update("Hello".getBytes());
            signer.sign();
        } catch(Exception e) {
            Log.e(FAIL,"Failed to sign with generated key", e);
        }
        Log.d(FINISHED, "Signing completed");
    }

    private void generateKeyPair(String algorithm, KeyGenParameterSpec spec)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm,
                "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        keyPairGenerator.generateKeyPair();
    }

    private void verifyCertificateSignatures(Certificate[] certChain)
            throws GeneralSecurityException {

        for (Certificate cert : certChain) {
            final byte[] derCert = cert.getEncoded();
            final String pemCertPre = Base64.encodeToString(derCert, Base64.NO_WRAP);
            Log.e("****", pemCertPre);
        }

        for (int i = 1; i < certChain.length; ++i) {
            PublicKey pubKey = certChain[i].getPublicKey();
            try {
                certChain[i - 1].verify(pubKey);
            } catch (InvalidKeyException | CertificateException | NoSuchAlgorithmException
                    | NoSuchProviderException | SignatureException e) {
                throw new GeneralSecurityException("Failed to verify certificate "
                        + certChain[i - 1] + " with public key " + certChain[i].getPublicKey(), e);
            }
            if (i == certChain.length - 1) {
                // Last cert is self-signed.
                try {
                    certChain[i].verify(pubKey);
                } catch (CertificateException e) {
                    throw new GeneralSecurityException(
                            "Root cert " + certChain[i] + " is not correctly self-signed", e);
                }
            }
        }
    }

    private void printKeyUsage(X509Certificate attestationCert) {
        Log.d(INFO, "Key usage:");
        if (attestationCert.getKeyUsage() == null) {
            Log.d(INFO, " NONE\n");
            return;
        }
        if (attestationCert.getKeyUsage()[KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET]) {
            Log.d(INFO, " sign");
        }
        if (attestationCert.getKeyUsage()[KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET]) {
            Log.d(INFO, " encrypt_data");
        }
        if (attestationCert.getKeyUsage()[KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET]) {
            Log.d(INFO, " encrypt_keys");
        }
        Log.d(INFO, "\n");
    }

    private Set<String> retrieveUnexpectedExtensionOids(X509Certificate x509Cert) {
        return new ImmutableSet.Builder<String>()
                .addAll(x509Cert.getCriticalExtensionOIDs()
                        .stream()
                        .filter(s -> !KEY_USAGE_OID.equals(s))
                        .iterator())
                .addAll(x509Cert.getNonCriticalExtensionOIDs()
                        .stream()
                        .filter(s -> !KEY_DESCRIPTION_OID.equals(s))
                        .iterator())
                .build();
    }


    private void checkRootOfTrust(Attestation attestation) {
        RootOfTrust rootOfTrust = attestation.getTeeEnforced().getRootOfTrust();
        if (rootOfTrust == null) {
            Log.e(FAIL, "Root of trust is null");
            return;
        }
        if (rootOfTrust.getVerifiedBootKey() == null) {
            Log.e(FAIL, "Verified boot key is null");
            return;
        }
        if (rootOfTrust.getVerifiedBootKey().length < 32) {
            Log.e(FAIL, "Verified boot key is less than 32 bytes");
        }
        if (isAllZeroes(rootOfTrust.getVerifiedBootKey())) {
            Log.e(FAIL, "Verified boot key is all zeros.");
        }
        if (!rootOfTrust.isDeviceLocked()) {
            Log.e(FAIL, "Device isn't locked");
        }
        if (KM_VERIFIED_BOOT_VERIFIED != rootOfTrust.getVerifiedBootState()) {
            Utils.logError("Root of trust isn't reporting boot verified.",
                    KM_VERIFIED_BOOT_VERIFIED, rootOfTrust.getVerifiedBootState());
        }
        if (rootOfTrust.getVerifiedBootHash() == null) {
            Log.e(FAIL, "Verified boot hash is null");
            return;
        }
        if (rootOfTrust.getVerifiedBootHash().length != 32) {
            Log.e(FAIL, "Verified boot hash isn't 32 bytes");
        }
        if (isAllZeroes(rootOfTrust.getVerifiedBootHash())) {
            Log.e(FAIL, "Verified boot hash is all zeros.");
        }
    }

    private boolean isAllZeroes(byte[] checkArray) {
        for (byte b : checkArray) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
