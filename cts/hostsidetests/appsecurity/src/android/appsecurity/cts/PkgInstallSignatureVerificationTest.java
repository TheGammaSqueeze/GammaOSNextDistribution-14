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

package android.appsecurity.cts;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.DeviceParameterizedRunner;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import junitparams.Parameters;

/**
 * Tests for APK signature verification during installation.
 */
@Presubmit
@RunWith(DeviceParameterizedRunner.class)
public class PkgInstallSignatureVerificationTest extends BaseAppSecurityTest {

    private static final String TEST_PKG = "android.appsecurity.cts.tinyapp";
    private static final String TEST_PKG2 = "android.appsecurity.cts.tinyapp2";
    private static final String COMPANION_TEST_PKG = "android.appsecurity.cts.tinyapp_companion";
    private static final String COMPANION2_TEST_PKG = "android.appsecurity.cts.tinyapp_companion2";
    private static final String COMPANION3_TEST_PKG = "android.appsecurity.cts.tinyapp_companion3";
    private static final String DEVICE_TESTS_APK = "CtsV3SigningSchemeRotationTest.apk";
    private static final String DEVICE_TESTS_PKG = "android.appsecurity.cts.v3rotationtests";
    private static final String DEVICE_TESTS_CLASS = DEVICE_TESTS_PKG + ".V3RotationTest";
    private static final String SERVICE_PKG = "android.appsecurity.cts.keyrotationtest";
    private static final String SERVICE_TEST_PKG = "android.appsecurity.cts.keyrotationtest.test";
    private static final String SERVICE_TEST_CLASS =
            SERVICE_TEST_PKG + ".SignatureQueryServiceInstrumentationTest";
    private static final String TEST_APK_RESOURCE_PREFIX = "/pkgsigverify/";
    private static final String INSTALL_ARG_FORCE_QUERYABLE = "--force-queryable";
    private static final String INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK =
            "--bypass-low-target-sdk-block";

    private static final String[] DSA_KEY_NAMES = {"1024", "2048", "3072"};
    private static final String[] EC_KEY_NAMES = {"p256", "p384", "p521"};
    private static final String[] RSA_KEY_NAMES = {"1024", "2048", "3072", "4096", "8192", "16384"};
    private static final String[] RSA_KEY_NAMES_2048_AND_LARGER =
            {"2048", "3072", "4096", "8192", "16384"};

    private static final boolean INCREMENTAL = true;
    private static final boolean NON_INCREMENTAL = false;

    private static Object[] installOnIncremental() {
        // Incremental and Non-Incremental.
        return new Boolean[][]{{INCREMENTAL}, {NON_INCREMENTAL}};
    }

    private boolean mUseIncrementalForInstallWithIdsig;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        Utils.prepareSingleUser(getDevice());
        uninstallPackage();
        uninstallCompanionPackages();
        installDeviceTestPkg();
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackages();
    }

    @Test
    public void testInstallOriginalSucceeds() throws Exception {
        // APK signed with v1 and v2 schemes. Obtained by building
        // cts/hostsidetests/appsecurity/test-apps/tinyapp.
        assertInstallSucceeds("original.apk");
    }

    @Test
    public void testInstallV1OneSignerMD5withRSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-md5-1.2.840.113549.1.1.1-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-md5-1.2.840.113549.1.1.4-%s.apk", RSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA1withRSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha1-1.2.840.113549.1.1.1-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha1-1.2.840.113549.1.1.5-%s.apk", RSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA224withRSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha224-1.2.840.113549.1.1.1-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha224-1.2.840.113549.1.1.14-%s.apk", RSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA256withRSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha256-1.2.840.113549.1.1.1-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha256-1.2.840.113549.1.1.11-%s.apk", RSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA384withRSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha384-1.2.840.113549.1.1.1-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha384-1.2.840.113549.1.1.12-%s.apk", RSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA512withRSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha512-1.2.840.113549.1.1.1-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-rsa-pkcs1-sha512-1.2.840.113549.1.1.13-%s.apk", RSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA1withECDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha1-1.2.840.10045.2.1-%s.apk", EC_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha1-1.2.840.10045.4.1-%s.apk", EC_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA224withECDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha224-1.2.840.10045.2.1-%s.apk", EC_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha224-1.2.840.10045.4.3.1-%s.apk", EC_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA256withECDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha256-1.2.840.10045.2.1-%s.apk", EC_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha256-1.2.840.10045.4.3.2-%s.apk", EC_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA384withECDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha384-1.2.840.10045.2.1-%s.apk", EC_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha384-1.2.840.10045.4.3.3-%s.apk", EC_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA512withECDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha512-1.2.840.10045.2.1-%s.apk", EC_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-ecdsa-sha512-1.2.840.10045.4.3.4-%s.apk", EC_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA1withDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-dsa-sha1-1.2.840.10040.4.1-%s.apk", DSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-dsa-sha1-1.2.840.10040.4.3-%s.apk", DSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA224withDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-dsa-sha224-1.2.840.10040.4.1-%s.apk", DSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-dsa-sha224-2.16.840.1.101.3.4.3.1-%s.apk", DSA_KEY_NAMES);
    }

    @Test
    public void testInstallV1OneSignerSHA256withDSA() throws Exception {
        // APK signed with v1 scheme only, one signer.
        assertInstallSucceedsForEach(
                "v1-only-with-dsa-sha256-1.2.840.10040.4.1-%s.apk", DSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v1-only-with-dsa-sha256-2.16.840.1.101.3.4.3.2-%s.apk", DSA_KEY_NAMES);
    }

//  Android platform doesn't support DSA with SHA-384 and SHA-512.
//    @Test
//    public void testInstallV1OneSignerSHA384withDSA() throws Exception {
//        // APK signed with v1 scheme only, one signer.
//        assertInstallSucceedsForEach(
//                "v1-only-with-dsa-sha384-2.16.840.1.101.3.4.3.3-%s.apk", DSA_KEY_NAMES);
//    }
//
//    @Test
//    public void testInstallV1OneSignerSHA512withDSA() throws Exception {
//        // APK signed with v1 scheme only, one signer.
//        assertInstallSucceedsForEach(
//                "v1-only-with-dsa-sha512-2.16.840.1.101.3.4.3.3-%s.apk", DSA_KEY_NAMES);
//    }

    @Test
    public void testInstallV2StrippedFails() throws Exception {
        // APK signed with v1 and v2 schemes, but v2 signature was stripped from the file (by using
        // zipalign).
        // This should fail because the v1 signature indicates that the APK was supposed to be
        // signed with v2 scheme as well, making the platform's anti-stripping protections reject
        // the APK.
        assertInstallFailsWithError("v2-stripped.apk", "Signature stripped");

        // Similar to above, but the X-Android-APK-Signed anti-stripping header in v1 signature
        // lists unknown signature schemes in addition to APK Signature Scheme v2. Unknown schemes
        // should be ignored.
        assertInstallFailsWithError(
                "v2-stripped-with-ignorable-signing-schemes.apk", "Signature stripped");
    }

    @Test
    public void testInstallV2OneSignerOneSignature() throws Exception {
        // APK signed with v2 scheme only, one signer, one signature.
        assertInstallSucceedsForEach("v2-only-with-dsa-sha256-%s.apk", DSA_KEY_NAMES);
        assertInstallSucceedsForEach("v2-only-with-ecdsa-sha256-%s.apk", EC_KEY_NAMES);
        assertInstallSucceedsForEach("v2-only-with-rsa-pkcs1-sha256-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach("v2-only-with-rsa-pss-sha256-%s.apk", RSA_KEY_NAMES);

        // DSA with SHA-512 is not supported by Android platform and thus APK Signature Scheme v2
        // does not support that either
        // assertInstallSucceedsForEach("v2-only-with-dsa-sha512-%s.apk", DSA_KEY_NAMES);
        assertInstallSucceedsForEach("v2-only-with-ecdsa-sha512-%s.apk", EC_KEY_NAMES);
        assertInstallSucceedsForEach("v2-only-with-rsa-pkcs1-sha512-%s.apk", RSA_KEY_NAMES);
        assertInstallSucceedsForEach(
                "v2-only-with-rsa-pss-sha512-%s.apk",
                RSA_KEY_NAMES_2048_AND_LARGER // 1024-bit key is too short for PSS with SHA-512
        );
    }

    @Test
    public void testInstallV1SignatureOnlyDoesNotVerify() throws Exception {
        // APK signed with v1 scheme only, but not all digests match those recorded in
        // META-INF/MANIFEST.MF.
        String error = "META-INF/MANIFEST.MF has invalid digest";

        // Bitflip in classes.dex of otherwise good file.
        assertInstallFailsWithError(
                "v1-only-with-tampered-classes-dex.apk", error);
    }

    @Test
    public void testInstallV2SignatureDoesNotVerify() throws Exception {
        // APK signed with v2 scheme only, but the signature over signed-data does not verify.
        String error = "signature did not verify";

        // Bitflip in certificate field inside signed-data. Based on
        // v2-only-with-dsa-sha256-1024.apk.
        assertInstallFailsWithError("v2-only-with-dsa-sha256-1024-sig-does-not-verify.apk", error);

        // Signature claims to be RSA PKCS#1 v1.5 with SHA-256, but is actually using SHA-512.
        // Based on v2-only-with-rsa-pkcs1-sha256-2048.apk.
        assertInstallFailsWithError(
                "v2-only-with-rsa-pkcs1-sha256-2048-sig-does-not-verify.apk", error);

        // Signature claims to be RSA PSS with SHA-256 and 32 bytes of salt, but is actually using 0
        // bytes of salt. Based on v2-only-with-rsa-pkcs1-sha256-2048.apk. Obtained by modifying APK
        // signer to use the wrong amount of salt.
        assertInstallFailsWithError(
                "v2-only-with-rsa-pss-sha256-2048-sig-does-not-verify.apk", error);

        // Bitflip in the ECDSA signature. Based on v2-only-with-ecdsa-sha256-p256.apk.
        assertInstallFailsWithError(
                "v2-only-with-ecdsa-sha256-p256-sig-does-not-verify.apk", error);
    }

    @Test
    public void testInstallV2ContentDigestMismatch() throws Exception {
        // APK signed with v2 scheme only, but the digest of contents does not match the digest
        // stored in signed-data.
        String error = "digest of contents did not verify";

        // Based on v2-only-with-rsa-pkcs1-sha512-4096.apk. Obtained by modifying APK signer to
        // flip the leftmost bit in content digest before signing signed-data.
        assertInstallFailsWithError(
                "v2-only-with-rsa-pkcs1-sha512-4096-digest-mismatch.apk", error);

        // Based on v2-only-with-ecdsa-sha256-p256.apk. Obtained by modifying APK signer to flip the
        // leftmost bit in content digest before signing signed-data.
        assertInstallFailsWithError(
                "v2-only-with-ecdsa-sha256-p256-digest-mismatch.apk", error);
    }

    @Test
    public void testInstallNoApkSignatureSchemeBlock() throws Exception {
        // APK signed with v2 scheme only, but the rules for verifying APK Signature Scheme v2
        // signatures say that this APK must not be verified using APK Signature Scheme v2.

        // Obtained from v2-only-with-rsa-pkcs1-sha512-4096.apk by flipping a bit in the magic
        // field in the footer of APK Signing Block. This makes the APK Signing Block disappear.
        assertInstallFails("v2-only-wrong-apk-sig-block-magic.apk");

        // Obtained by modifying APK signer to insert "GARBAGE" between ZIP Central Directory and
        // End of Central Directory. The APK is otherwise fine and is signed with APK Signature
        // Scheme v2. Based on v2-only-with-rsa-pkcs1-sha256.apk.
        assertInstallFails("v2-only-garbage-between-cd-and-eocd.apk");

        // Obtained by modifying APK signer to truncate the ZIP Central Directory by one byte. The
        // APK is otherwise fine and is signed with APK Signature Scheme v2. Based on
        // v2-only-with-rsa-pkcs1-sha256.apk
        assertInstallFails("v2-only-truncated-cd.apk");

        // Obtained by modifying the size in APK Signature Block header. Based on
        // v2-only-with-ecdsa-sha512-p521.apk.
        assertInstallFails("v2-only-apk-sig-block-size-mismatch.apk");

        // Obtained by modifying the ID under which APK Signature Scheme v2 Block is stored in
        // APK Signing Block and by modifying the APK signer to not insert anti-stripping
        // protections into JAR Signature. The APK should appear as having no APK Signature Scheme
        // v2 Block and should thus successfully verify using JAR Signature Scheme.
        assertInstallSucceeds("v1-with-apk-sig-block-but-without-apk-sig-scheme-v2-block.apk");
    }

    @Test
    public void testInstallV2UnknownPairIgnoredInApkSigningBlock() throws Exception {
        // Obtained by modifying APK signer to emit an unknown ID-value pair into APK Signing Block
        // before the ID-value pair containing the APK Signature Scheme v2 Block. The unknown
        // ID-value should be ignored.
        assertInstallSucceeds("v2-only-unknown-pair-in-apk-sig-block.apk");
    }

    @Test
    public void testInstallV2IgnoresUnknownSignatureAlgorithms() throws Exception {
        // APK is signed with a known signature algorithm and with a couple of unknown ones.
        // Obtained by modifying APK signer to use "unknown" signature algorithms in addition to
        // known ones.
        assertInstallSucceeds("v2-only-with-ignorable-unsupported-sig-algs.apk");
    }

    @Test
    public void testInstallV2RejectsMismatchBetweenSignaturesAndDigestsBlocks() throws Exception {
        // APK is signed with a single signature algorithm, but the digests block claims that it is
        // signed with two different signature algorithms. Obtained by modifying APK Signer to
        // emit an additional digest record with signature algorithm 0x12345678.
        assertInstallFailsWithError(
                "v2-only-signatures-and-digests-block-mismatch.apk",
                "Signature algorithms don't match between digests and signatures records");
    }

    @Test
    public void testInstallV2RejectsMismatchBetweenPublicKeyAndCertificate() throws Exception {
        // APK is signed with v2 only. The public key field does not match the public key in the
        // leaf certificate. Obtained by modifying APK signer to write out a modified leaf
        // certificate where the RSA modulus has a bitflip.
        assertInstallFailsWithError(
                "v2-only-cert-and-public-key-mismatch.apk",
                "Public key mismatch between certificate and signature record");
    }

    @Test
    public void testInstallV2RejectsSignerBlockWithNoCertificates() throws Exception {
        // APK is signed with v2 only. There are no certificates listed in the signer block.
        // Obtained by modifying APK signer to output no certificates.
        assertInstallFailsWithError("v2-only-no-certs-in-sig.apk", "No certificates listed");
    }

    @Test
    public void testInstallTwoSigners() throws Exception {
        // APK signed by two different signers.
        assertInstallSucceeds("two-signers.apk");
        // Because the install attempt below is an update, it also tests that the signing
        // certificates exposed by v2 signatures above are the same as the one exposed by v1
        // signatures in this APK.
        assertInstallSucceeds("v1-only-two-signers.apk");
        assertInstallSucceeds("v2-only-two-signers.apk");
    }

    @Test
    public void testInstallNegativeModulus() throws Exception {
        // APK signed with a certificate that has a negative RSA modulus.
        assertInstallSucceeds("v1-only-negative-modulus.apk");
        assertInstallSucceeds("v2-only-negative-modulus.apk");
        assertInstallSucceeds("v3-only-negative-modulus.apk");
    }

    @Test
    public void testInstallV2TwoSignersRejectsWhenOneBroken() throws Exception {
        // Bitflip in the ECDSA signature of second signer. Based on two-signers.apk.
        // This asserts that breakage in any signer leads to rejection of the APK.
        assertInstallFailsWithError(
                "two-signers-second-signer-v2-broken.apk", "signature did not verify");
    }

    @Test
    public void testInstallV2TwoSignersRejectsWhenOneWithoutSignatures() throws Exception {
        // APK v2-signed by two different signers. However, there are no signatures for the second
        // signer.
        assertInstallFailsWithError(
                "v2-only-two-signers-second-signer-no-sig.apk", "No signatures");
    }

    @Test
    public void testInstallV2TwoSignersRejectsWhenOneWithoutSupportedSignatures() throws Exception {
        // APK v2-signed by two different signers. However, there are no supported signatures for
        // the second signer.
        assertInstallFailsWithError(
                "v2-only-two-signers-second-signer-no-supported-sig.apk",
                "No supported signatures");
    }

    @Test
    public void testInstallV2RejectsWhenMissingCode() throws Exception {
        // Obtained by removing classes.dex from original.apk and then signing with v2 only.
        // Although this has nothing to do with v2 signature verification, package manager wants
        // signature verification / certificate collection to reject APKs with missing code
        // (classes.dex) unless requested otherwise.
        assertInstallFailsWithError("v2-only-missing-classes.dex.apk", "code is missing");
    }

    @Test
    public void testCorrectCertUsedFromPkcs7SignedDataCertsSet() throws Exception {
        // Obtained by prepending the rsa-1024 certificate to the PKCS#7 SignedData certificates set
        // of v1-only-with-rsa-pkcs1-sha1-1.2.840.113549.1.1.1-2048.apk META-INF/CERT.RSA. The certs
        // (in the order of appearance in the file) are thus: rsa-1024, rsa-2048. The package's
        // signing cert is rsa-2048.
        assertInstallSucceeds("v1-only-pkcs7-cert-bag-first-cert-not-used.apk");

        // Check that rsa-1024 was not used as the previously installed package's signing cert.
        assertInstallFailsWithError(
                "v1-only-with-rsa-pkcs1-sha1-1.2.840.113549.1.1.1-1024.apk",
                "signatures do not match");

        // Check that rsa-2048 was used as the previously installed package's signing cert.
        assertInstallSucceeds("v1-only-with-rsa-pkcs1-sha1-1.2.840.113549.1.1.1-2048.apk");
    }

    @Test
    public void testV1SchemeSignatureCertNotReencoded() throws Exception {
        // Regression test for b/30148997 and b/18228011. When PackageManager does not preserve the
        // original encoded form of signing certificates, bad things happen, such as rejection of
        // completely valid updates to apps. The issue in b/30148997 and b/18228011 was that
        // PackageManager started re-encoding signing certs into DER. This normally produces exactly
        // the original form because X.509 certificates are supposed to be DER-encoded. However, a
        // small fraction of Android apps uses X.509 certificates which are not DER-encoded. For
        // such apps, re-encoding into DER changes the serialized form of the certificate, creating
        // a mismatch with the serialized form stored in the PackageManager database, leading to the
        // rejection of updates for the app.
        //
        // The signing certs of the two APKs differ only in how the cert's signature is encoded.
        // From Android's perspective, these two APKs are signed by different entities and thus
        // cannot be used to update one another. If signature verification code re-encodes certs
        // into DER, both certs will be exactly the same and Android will accept these APKs as
        // updates of each other. This test is thus asserting that the two APKs are not accepted as
        // updates of each other.
        //
        // * v1-only-with-rsa-1024.apk cert's signature is DER-encoded
        // * v1-only-with-rsa-1024-cert-not-der.apk cert's signature is not DER-encoded. It is
        //   BER-encoded, with length encoded as two bytes instead of just one.
        //   v1-only-with-rsa-1024-cert-not-der.apk META-INF/CERT.RSA was obtained from
        //   v1-only-with-rsa-1024.apk META-INF/CERT.RSA by manually modifying the ASN.1 structure.
        assertInstallSucceeds("v1-only-with-rsa-1024.apk");
        assertInstallFailsWithError(
                "v1-only-with-rsa-1024-cert-not-der.apk", "signatures do not match");

        uninstallPackage();
        assertInstallSucceeds("v1-only-with-rsa-1024-cert-not-der.apk");
        assertInstallFailsWithError("v1-only-with-rsa-1024.apk", "signatures do not match");
    }

    @Test
    public void testV2SchemeSignatureCertNotReencoded() throws Exception {
        // This test is here to catch something like b/30148997 and b/18228011 happening to the
        // handling of APK Signature Scheme v2 signatures by PackageManager. When PackageManager
        // does not preserve the original encoded form of signing certificates, bad things happen,
        // such as rejection of completely valid updates to apps. The issue in b/30148997 and
        // b/18228011 was that PackageManager started re-encoding signing certs into DER. This
        // normally produces exactly the original form because X.509 certificates are supposed to be
        // DER-encoded. However, a small fraction of Android apps uses X.509 certificates which are
        // not DER-encoded. For such apps, re-encoding into DER changes the serialized form of the
        // certificate, creating a mismatch with the serialized form stored in the PackageManager
        // database, leading to the rejection of updates for the app.
        //
        // The signing certs of the two APKs differ only in how the cert's signature is encoded.
        // From Android's perspective, these two APKs are signed by different entities and thus
        // cannot be used to update one another. If signature verification code re-encodes certs
        // into DER, both certs will be exactly the same and Android will accept these APKs as
        // updates of each other. This test is thus asserting that the two APKs are not accepted as
        // updates of each other.
        //
        // * v2-only-with-rsa-pkcs1-sha256-1024.apk cert's signature is DER-encoded
        // * v2-only-with-rsa-pkcs1-sha256-1024-cert-not-der.apk cert's signature is not DER-encoded
        //   It is BER-encoded, with length encoded as two bytes instead of just one.
        assertInstallSucceeds("v2-only-with-rsa-pkcs1-sha256-1024.apk");
        assertInstallFailsWithError(
                "v2-only-with-rsa-pkcs1-sha256-1024-cert-not-der.apk", "signatures do not match");

        uninstallPackage();
        assertInstallSucceeds("v2-only-with-rsa-pkcs1-sha256-1024-cert-not-der.apk");
        assertInstallFailsWithError(
                "v2-only-with-rsa-pkcs1-sha256-1024.apk", "signatures do not match");
    }

    @Test
    public void testInstallEphemeralRequiresV2Signature() throws Exception {
        assertInstallEphemeralFailsWithError("unsigned-ephemeral.apk",
                "Failed to collect certificates");
        assertInstallEphemeralFailsWithError("v1-only-ephemeral.apk",
                "must be signed with APK Signature Scheme v2 or greater");
        assertInstallEphemeralSucceeds("v2-only-ephemeral.apk");
        assertInstallEphemeralSucceeds("v1-v2-ephemeral.apk"); // signed with both schemes
    }

    @Test
    public void testInstallEmpty() throws Exception {
        assertInstallFailsWithError("empty-unsigned.apk", "Unknown failure");
        assertInstallFailsWithError("v1-only-empty.apk", "Unknown failure");
        assertInstallFailsWithError("v2-only-empty.apk", "Unknown failure");
    }

    @AsbSecurityTest(cveBugId = 64211847)
    @Test
    public void testInstallApkWhichDoesNotStartWithZipLocalFileHeaderMagic() throws Exception {
        // The APKs below are competely fine except they don't start with ZIP Local File Header
        // magic. Thus, these APKs will install just fine unless Package Manager requires that APKs
        // start with ZIP Local File Header magic.
        String error = "Unknown failure";

        // Obtained by modifying apksigner to output four unused 0x00 bytes at the start of the APK
        assertInstallFailsWithError("v1-only-starts-with-00000000-magic.apk", error);
        assertInstallFailsWithError("v2-only-starts-with-00000000-magic.apk", error);

        // Obtained by modifying apksigner to output 8 unused bytes (DEX magic and version) at the
        // start of the APK
        assertInstallFailsWithError("v1-only-starts-with-dex-magic.apk", error);
        assertInstallFailsWithError("v2-only-starts-with-dex-magic.apk", error);
    }

    @Test
    public void testInstallV3KeyRotation() throws Exception {
        // tests that a v3 signed APK with RSA key can rotate to a new key
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
    }

    @Test
    public void testInstallV3KeyRotationToAncestor() throws Exception {
        // tests that a v3 signed APK with RSA key cannot be upgraded by one of its past certs
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
        assertInstallFails("v3-rsa-pkcs1-sha256-2048-1.apk");
    }

    @Test
    public void testInstallV3KeyRotationToAncestorWithRollback() throws Exception {
        // tests that a v3 signed APK with RSA key can be upgraded by one of its past certs if it
        // has granted that cert the rollback capability
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-and-roll-caps.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1.apk");
    }

    @Test
    public void testInstallV3KeyRotationMultipleHops() throws Exception {
        // tests that a v3 signed APK with RSA key can rotate to a new key which is the result of
        // multiple rotations from the original: APK signed with key 1 can be updated by key 3, when
        // keys were: 1 -> 2 -> 3
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-3-with-por_1_2_3-full-caps.apk");
    }

    @Test
    public void testInstallV3PorSignerMismatch() throws Exception {
        // tests that an APK with a proof-of-rotation struct that doesn't include the current
        // signing certificate fails to install
        assertInstallFails("v3-rsa-pkcs1-sha256-2048-3-with-por_1_2-full-caps.apk");
    }

    @Test
    public void testInstallV3KeyRotationWrongPor() throws Exception {
        // tests that a valid APK with a proof-of-rotation record can't upgrade an APK with a
        // signing certificate that isn't in the proof-of-rotation record
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1.apk");
        assertInstallFails("v3-rsa-pkcs1-sha256-2048-3-with-por_2_3-full-caps.apk");
    }

    @Test
    public void testInstallV3KeyRotationSharedUid() throws Exception {
        // tests that a v3 signed sharedUid APK can still be sharedUid with apps with its older
        // signing certificate, if it so desires
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-sharedUid.apk");
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps-sharedUid-companion.apk");
    }

    @Test
    public void testInstallV3KeyRotationOlderSharedUid() throws Exception {
        // tests that a sharedUid APK can still install with another app that is signed by a newer
        // signing certificate, but which allows sharedUid with the older one
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps-sharedUid-companion.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-sharedUid.apk");
    }

    @Test
    public void testInstallV3KeyRotationSharedUidNoCap() throws Exception {
        // tests that a v3 signed sharedUid APK cannot be sharedUid with apps with its older
        // signing certificate, when it has not granted that certificate the sharedUid capability
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-sharedUid.apk");
        assertInstallFails(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-no-shUid-cap-sharedUid-companion.apk");
    }

    @Test
    public void testInstallV3KeyRotationOlderSharedUidNoCap() throws Exception {
        // tests that a sharedUid APK signed with an old certificate cannot install with
        // an app having a proof-of-rotation structure that hasn't granted the older
        // certificate the sharedUid capability
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-no-shUid-cap-sharedUid-companion.apk");
        assertInstallFails("v3-rsa-pkcs1-sha256-2048-1-sharedUid.apk");
    }

    @Test
    public void testInstallV3NoRotationSharedUid() throws Exception {
        // tests that a sharedUid APK signed with a new certificate installs with
        // an app having a proof-of-rotation structure that hasn't granted an older
        // certificate the sharedUid capability
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-no-shUid-cap-sharedUid-companion.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-sharedUid.apk");
    }

    @Test
    public void testInstallV3MultipleAppsOneDeniesOldKeySharedUid() throws Exception {
        // If two apps are installed as part of a sharedUid, one granting access to the sharedUid
        // to the previous key and the other revoking access to the sharedUid, then when an app
        // signed with the old key attempts to join the sharedUid the installation should be blocked
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid.apk");
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-default-caps-sharedUid-companion.apk");
        assertInstallFromBuildFails("v3-ec-p256-1-sharedUid-companion2.apk");
    }

    @Test
    public void testInstallV3MultipleAppsOneUpdatedToDenyOldKeySharedUid() throws Exception {
        // Similar to the test above if two apps are installed as part of a sharedUid with both
        // granting access to the sharedUid to the previous key then an app signed with the previous
        // key should be allowed to install and join the sharedUid. If one of the first two apps
        // is then updated with a lineage that denies access to the sharedUid for the old key, all
        // subsequent installs / updates with that old key should be blocked.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-default-caps-sharedUid.apk");
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-default-caps-sharedUid-companion.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-1-sharedUid-companion2.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid.apk");
        assertInstallFromBuildFails("v3-ec-p256-1-sharedUid-companion2.apk");
    }

    @Test
    public void testInstallV3SharedUidDeniedOnlyRotatedUpdateAllowed() throws Exception {
        // To allow rotation after a signing key compromise, an APK that is already part of a
        // shareddUserId can rotate to a new key with the old key being denied the SHARED_USER_ID
        // capability and still be updated in the sharedUserId. Another app signed with this same
        // lineage and capabilities that is not currently part of the sharedUserId will not be
        // allowed to join as long as any apps signed with the untrusted key are still part of
        // the sharedUserId.
        assertInstallFromBuildSucceeds("v3-ec-p256-1-sharedUid.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-1-sharedUid-companion2.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid.apk");
        // An app signed with the untrusted key is still part of the sharedUserId, so a new app
        // that does not trust this key is not allowed to join the sharedUserId.
        assertInstallFromBuildFails("v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid-companion.apk");
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid-companion2.apk");
        // Once all apps have rotated away from the untrusted key, a new app that also does not
        // trust the previous key can now join the sharedUserId.
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid-companion.apk");
    }

    @Test
    public void testInstallV3FirstAppOnlySignedByNewKeyLastAppOldKey() throws Exception {
        // This test verifies the following scenario:
        // - First installed app in sharedUid only signed with new key without lineage.
        // - Second installed app in sharedUid signed with new key and includes lineage granting
        //   access to the old key to join the sharedUid.
        // - Last installed app in sharedUid signed with old key.
        // The lineage should be updated when the second app is installed to allow the installation
        // of the app signed with the old key.
        assertInstallFromBuildSucceeds("v3-ec-p256-2-sharedUid-companion.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-default-caps-sharedUid.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-1-sharedUid-companion2.apk");
    }

    @Test
    public void testInstallV3AppSignedWithOldKeyUpdatedLineageDeniesShUidCap() throws Exception {
        // If an app is installed as part of a sharedUid, and then that app is signed with a new key
        // that rejects the previous key in the lineage the update should be allowed to proceed
        // as the app is being updated to the newly rotated key.
        assertInstallFromBuildSucceeds("v3-ec-p256-1-sharedUid.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid.apk");
    }

    @Test
    public void testInstallV3TwoSharedUidAppsWithDivergedLineages() throws Exception {
        // Apps that are installed as part of the sharedUserId with a lineage must have common
        // ancestors; the platform will allow the installation if the lineage of an app being
        // installed as part of the sharedUserId is the same, a subset, or a superset of the
        // existing lineage, but if the lineage diverges then the installation should be blocked.
        assertInstallFromBuildSucceeds("v3-por_Y_1_2-default-caps-sharedUid.apk");
        assertInstallFromBuildFails("v3-por_Z_1_2-default-caps-sharedUid-companion.apk");
    }

    @Test
    public void testInstallV3WithRestoredCapabilityInSharedUserId() throws Exception {
        // A sharedUserId contains the shared signing lineage for all packages in the UID; this
        // shared lineage contain the full signing history for all packages along with the merged
        // capabilities for each signer shared between the packages. This test verifies if one
        // package revokes a capability from a previous signer, but subsequently restores that
        // capability, then since all packages have granted the capability, it is restored to the
        // previous signer in the shared lineage.

        // Install a package with the SHARED_USER_ID capability revoked for the original signer
        // in the lineage; verify that a package signed with only the original signer cannot join
        // the sharedUserId.
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-default-caps-sharedUid-companion.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid.apk");
        assertInstallFromBuildFails("v3-ec-p256-1-sharedUid-companion2.apk");

        // Update the package that revoked the SHARED_USER_ID with an updated lineage that restores
        // this capability to the original signer; verify the package signed with the original
        // signing key can now join the sharedUserId since all existing packages in the UID grant
        // this capability to the original signer.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-default-caps-sharedUid.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-1-sharedUid-companion2.apk");
    }

    @Test
    public void testInstallV3WithRevokedCapabilityInSharedUserId() throws Exception {
        // While a capability can be restored to a common signer in the shared signing lineage, if
        // one package has revoked a capability from a common signer and another package is
        // installed / updated which restores the capability to that signer, the revocation of
        // the capability by the existing package should take precedence. A capability can only
        // be restored to a common signer if all packages in the sharedUserId have granted this
        // capability to the signer.

        // Install a package with the SHARED_USER_ID capability revoked from the original signer,
        // then install another package in the sharedUserId that grants this capability to the
        // original signer. Since a package exists in the sharedUserId that has revoked this
        // capability, another package signed with this capability shouldn't be able to join the
        // sharedUserId.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-shUid-cap-sharedUid.apk");
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-default-caps-sharedUid-companion.apk");
        assertInstallFromBuildFails("v3-ec-p256-1-sharedUid-companion2.apk");

        // Install the same package that grants the SHARED_USER_ID capability to the original
        // signer; when iterating over the existing packages in the packages in the sharedUserId,
        // the original version of this package should be skipped since the lineage from the
        // updated package is used when merging with the shared lineage.
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-default-caps-sharedUid-companion.apk");
        assertInstallFromBuildFails("v3-ec-p256-1-sharedUid-companion2.apk");

        // Install another package that has granted the SHARED_USER_ID to the original signer; this
        // should trigger another merge with all packages in the sharedUserId. Since one still
        // remains that revokes the capability, the capability should be revoked in the shared
        // lineage.
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2-default-caps-sharedUid-companion3.apk");
        assertInstallFromBuildFails("v3-ec-p256-1-sharedUid-companion2.apk");
    }

    @Test
    public void testInstallV3UpdateAfterRotation() throws Exception {
        // This test performs an end to end verification of the update of an app with a rotated
        // key. The app under test exports a bound service that performs its own PackageManager key
        // rotation API verification, and the instrumentation test binds to the service and invokes
        // the verifySignatures method to verify that the key rotation APIs return the expected
        // results. The instrumentation test app is signed with the same key and lineage as the
        // app under test to also provide a second app that can be used for the checkSignatures
        // verification.

        // Install the initial versions of the apps; the test method verifies the app under test is
        // signed with the original signing key.
        assertInstallFromBuildSucceeds("CtsSignatureQueryService.apk");
        assertInstallFromBuildSucceeds("CtsSignatureQueryServiceTest.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_noRotation_succeeds");

        // Install the second version of the app signed with the rotated key. This test verifies the
        // app still functions as expected after the update with the rotated key. The
        // instrumentation test app is not updated here to allow verification of the pre-key
        // rotation behavior for the checkSignatures APIs. These APIs should behave similar to the
        // GET_SIGNATURES flag in that if one or both apps have a signing lineage if the oldest
        // signers in the lineage match then the methods should return that the signatures match
        // even if one is signed with a newer key in the lineage.
        assertInstallFromBuildSucceeds("CtsSignatureQueryService_v2.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");

        // Installs the third version of the app under test and the instrumentation test, both
        // signed with the same rotated key and lineage. This test is intended to verify that the
        // app can still be updated and function as expected after an update with a rotated key.
        assertInstallFromBuildSucceeds("CtsSignatureQueryService_v3.apk");
        assertInstallFromBuildSucceeds("CtsSignatureQueryServiceTest_v2.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testInstallV31UpdateAfterRotation() throws Exception {
        // This test is the same as above, but using the v3.1 signature scheme for rotation.
        assertInstallFromBuildSucceeds("CtsSignatureQueryService.apk");
        assertInstallFromBuildSucceeds("CtsSignatureQueryServiceTest.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_noRotation_succeeds");

        assertInstallFromBuildSucceeds("CtsSignatureQueryService_v2-tgt-33.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");

        assertInstallFromBuildSucceeds("CtsSignatureQueryService_v3-tgt-33.apk");
        assertInstallFromBuildSucceeds("CtsSignatureQueryServiceTest_v2-tgt-33.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");
    }

    @CddTest(requirement="4/C-0-9")
    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV41UpdateAfterRotation(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // This test is the same as above, but using the v4.1 signature scheme for rotation.
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService.apk");
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryServiceTest.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_noRotation_succeeds");

        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService_v2-tgt-33.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");

        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService_v3-tgt-33.apk");
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryServiceTest_v2-tgt-33.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");
    }

    @CddTest(requirement="4/C-0-9")
    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV41WrongBlockId(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // This test is the same as above, but using the v4.1 signature scheme for rotation.
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService.apk");
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryServiceTest.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_noRotation_succeeds");

        assertInstallV4FailsWithError("CtsSignatureQueryService_v2-tgt-33-wrongV41Block.apk",
                "Failed to find V4 signature block corresponding to V3 blockId: 462663009");
    }

    @CddTest(requirement="4/C-0-9")
    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV41LegacyV4(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // This test is the same as above, but using the v4.1 signature scheme for rotation.
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService.apk");
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryServiceTest.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_noRotation_succeeds");

        assertInstallV4FailsWithError("CtsSignatureQueryService_v2-tgt-33-legacyV4.apk",
                "Failed to find V4 signature block corresponding to V3 blockId: 462663009");
    }

    @CddTest(requirement="4/C-0-9")
    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV41WrongDigest(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // This test is the same as above, but using the v4.1 signature scheme for rotation.
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService.apk");
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryServiceTest.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_noRotation_succeeds");

        assertInstallV4FailsWithError("CtsSignatureQueryService_v2-tgt-33-wrongDigest.apk",
                "APK digest in V4 signature does not match V2/V3");
    }

    @Test
    public void testInstallV3KeyRotationSigPerm() throws Exception {
        // tests that a v3 signed APK can still get a signature permission from an app with its
        // older signing certificate.
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-permdef.apk");
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps-permcli-companion.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @Test
    public void testInstallV3KeyRotationOlderSigPerm() throws Exception {
        // tests that an apk with an older signing certificate than the one which defines a
        // signature permission it wants gets the permission if the defining APK grants the
        // capability
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps-permdef.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-permcli-companion.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @Test
    public void testInstallV3KeyRotationSigPermNoCap() throws Exception {
        // tests that an APK signed by an older signing certificate is unable to get a requested
        // signature permission when the defining APK has rotated to a newer signing certificiate
        // and does not grant the permission capability to the older cert
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-no-perm-cap-permdef.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-permcli-companion.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasNoPerm");
    }

    @Test
    public void testInstallV3KeyRotationOlderSigPermNoCap() throws Exception {
        // tests that an APK signed by a newer signing certificate than the APK which defines a
        // signature permission is able to get that permission, even if the newer APK does not
        // grant the permission capability to the older signing certificate.
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-permdef.apk");
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-no-perm-cap-permcli-companion.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @Test
    public void testInstallV3NoRotationSigPerm() throws Exception {
        // make sure that an APK, which wants to use a signature permission defined by an APK, which
        // has not granted that capability to older signing certificates, can still install
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-no-perm-cap-permdef.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-permcli-companion.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @Test
    public void testInstallV3CommonSignerInLineageWithPermCap() throws Exception {
        // If an APK requesting a signature permission has a common signer in the lineage with the
        // APK declaring the permission, and that signer is granted the permission capability in
        // the declaring APK, then the permission should be granted to the requesting app even
        // if their signers have diverged.
        assertInstallFromBuildSucceeds(
                "v3-ec-p256-with-por_1_2_3-1-no-caps-2-default-declperm.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2_4-companion-usesperm.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @Test
    public void testInstallV3CommonSignerInLineageNoCaps() throws Exception {
        // If an APK requesting a signature permission has a common signer in the lineage with the
        // APK declaring the permission, but the signer in the lineage has not been granted the
        // permission capability the permission should not be granted to the requesting app.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2_3-no-caps-declperm.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2_4-companion-usesperm.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasNoPerm");
    }

    @Test
    public void testKnownSignerPermGrantedWhenCurrentSignerInResource() throws Exception {
        // The knownSigner protection flag allows an app to declare other trusted signing
        // certificates in an array resource; if a requesting app's current signer is in this array
        // of trusted certificates then the permission should be granted.
        assertInstallFromBuildSucceeds("v3-rsa-2048-decl-knownSigner-ec-p256-1-3.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256_3-companion-uses-knownSigner.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");

        // If the declaring app changes the trusted certificates on an update any requesting app
        // that no longer meets the requirements based on its signing identity should have the
        // permission revoked. This app update only trusts ec-p256_1 but the app that was previously
        // granted the permission based on its signing identity is signed by ec-p256_3.
        assertInstallFromBuildSucceeds("v3-rsa-2048-decl-knownSigner-str-res-ec-p256-1.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasNoPerm");
    }

    @Test
    public void testKnownSignerPermCurrentSignerNotInResource() throws Exception {
        // If an app requesting a knownSigner permission does not meet the requirements for a
        // signature permission and is not signed by any of the trusted certificates then the
        // permission should not be granted.
        assertInstallFromBuildSucceeds("v3-rsa-2048-decl-knownSigner-ec-p256-1-3.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256_2-companion-uses-knownSigner.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasNoPerm");
    }

    @Test
    public void testKnownSignerPermGrantedWhenSignerInLineageInResource() throws Exception {
        // If an app requesting a knownSigner permission was previously signed by a certificate
        // that is trusted by the declaring app then the permission should be granted.
        assertInstallFromBuildSucceeds("v3-rsa-2048-decl-knownSigner-ec-p256-1-3.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-companion-uses-knownSigner.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");

        // If the declaring app changes the permission to no longer use the knownSigner flag then
        // any app granted the permission based on a signing identity from the set of trusted
        // certificates should have the permission revoked.
        assertInstallFromBuildSucceeds("v3-rsa-2048-declperm.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasNoPerm");
    }

    @Test
    public void testKnownSignerPermSignerInLineageMatchesStringResource() throws Exception {
        // The knownSigner protection flag allows an app to declare a single known trusted
        // certificate digest using a string resource instead of a string-array resource. This test
        // verifies the knownSigner permission is granted to a requesting app if the single trusted
        // cert is in the requesting app's lineage.
        assertInstallFromBuildSucceeds("v3-rsa-2048-decl-knownSigner-str-res-ec-p256-1.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-companion-uses-knownSigner.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @Test
    public void testKnownSignerPermSignerInLineageMatchesStringConst() throws Exception {
        // The knownSigner protection flag allows an app to declare a single known trusted
        // certificate digest using a string constant as the knownCerts attribute value instead of a
        // resource. This test verifies the knownSigner permission is granted to a requesting app if
        // the single trusted cert is in the requesting app's lineage.
        assertInstallFromBuildSucceeds("v3-rsa-2048-decl-knownSigner-str-const-ec-p256-1.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-companion-uses-knownSigner.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @Test
    public void testInstallV3SigPermDoubleDefNewerSucceeds() throws Exception {
        // make sure that if an app defines a signature permission already defined by another app,
        // it successfully installs if the other app's signing cert is in its past signing certs and
        // the signature permission capability is granted
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-permdef.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with_por_1_2-permdef-companion.apk");
    }

    @Test
    public void testInstallV3SigPermDoubleDefOlderSucceeds() throws Exception {
        // make sure that if an app defines a signature permission already defined by another app,
        // it successfully installs if it is in the other app's past signing certs and the signature
        // permission capability is granted
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with_por_1_2-permdef-companion.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-permdef.apk");
    }

    @Test
    public void testInstallV3SigPermDoubleDefNewerNoCapFails() throws Exception {
        // make sure that if an app defines a signature permission already defined by another app,
        // it fails to install if the other app's signing cert is in its past signing certs but the
        // signature permission capability is not granted
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1-permdef.apk");
        assertInstallFails(
                "v3-rsa-pkcs1-sha256-2048-2-with_por_1_2-no-perm-cap-permdef-companion.apk");
    }

    @Test
    public void testInstallV3SigPermDoubleDefOlderNoCapFails() throws Exception {
        // make sure that if an app defines a signature permission already defined by another app,
        // it fails to install if it is in the other app's past signing certs but the signature
        // permission capability is not granted
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with_por_1_2-no-perm-cap-permdef-companion.apk");
        assertInstallFails("v3-rsa-pkcs1-sha256-2048-1-permdef.apk");
    }

    @Test
    public void testInstallV3SigPermDoubleDefSameNoCapSucceeds() throws Exception {
        // make sure that if an app defines a signature permission already defined by another app,
        // it installs successfully when signed by the same certificate, even if the original app
        // does not grant signature capabilities to its past certs
        assertInstallSucceeds(
                "v3-rsa-pkcs1-sha256-2048-2-with_por_1_2-no-perm-cap-permdef-companion.apk");
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-permdef.apk");
    }

    @Test
    public void testInstallV3KeyRotationGetSignatures() throws Exception {
        // tests that a PackageInfo w/GET_SIGNATURES flag returns the older cert
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testGetSignaturesShowsOld");
    }

    @Test
    public void testInstallV3KeyRotationGetSigningCertificates() throws Exception {
        // tests that a PackageInfo w/GET_SIGNING_CERTIFICATES flag returns the old and new certs
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testGetSigningCertificatesShowsAll");
    }

    @Test
    public void testInstallV3KeyRotationGetApkContentsSigners() throws Exception {
        // The GET_SIGNING_CERTIFICATES flag results in a PackageInfo object returned with a
        // SigningInfo instance that can be used to query all certificates in the lineage or only
        // the current signer(s) via getApkContentsSigners. This test verifies when a V3 signed
        // package with a rotated key is queried getApkContentsSigners only returns the current
        // signer.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-default-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testGetApkContentsSignersShowsCurrent");
    }

    @Test
    public void testInstallV2MultipleSignersGetApkContentsSigners() throws Exception {
        // Similar to the above test, but verifies when an APK is signed with two V2 signers
        // getApkContentsSigners returns both of the V2 signers.
        assertInstallFromBuildSucceeds("v1v2-ec-p256-two-signers-targetSdk-30.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testGetApkContentsSignersShowsMultipleSigners");
    }

    @Test
    public void testInstallV3MultipleSignersInLineageGetSigningCertificateHistory()
            throws Exception {
        // The APK used for this test is signed with a lineage containing 5 keys in the signing
        // history; this test verifies SigningInfo#getSigningCertificateHistory returns all of an
        // APKs signers in their order of rotation.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por-1_2_3_4_5-default-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testGetSigningCertificateHistoryReturnsSignersInOrder");
    }

    @Test
    public void testInstallV3KeyRotationHasSigningCertificate() throws Exception {
        // tests that hasSigningCertificate() recognizes past and current signing certs
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testHasSigningCertificate");
    }

    @Test
    public void testInstallV3KeyRotationHasSigningCertificateSha256() throws Exception {
        // tests that hasSigningCertificate() recognizes past and current signing certs by sha256
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testHasSigningCertificateSha256");
    }

    @Test
    public void testInstallV3KeyRotationHasSigningCertificateByUid() throws Exception {
        // tests that hasSigningCertificate() recognizes past and current signing certs by uid
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testHasSigningCertificateByUid");
    }

    @Test
    public void testInstallV3KeyRotationHasSigningCertificateByUidSha256() throws Exception {
        // tests that hasSigningCertificate() recognizes past and current signing certs by uid
        // and sha256
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2-full-caps.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testHasSigningCertificateByUidSha256");
    }

    @Test
    public void testInstallV3KeyRotationHasDuplicateSigningCertificateHistory() throws Exception {
        // tests that an app's proof-of-rotation signing history cannot contain the same certificate
        // more than once.
        assertInstallFails("v3-rsa-pkcs1-sha256-2048-2-with-por_1_2_2-full-caps.apk");
    }

    @Test
    public void testInstallV3HasMultipleSigners() throws Exception {
        // tests that an app can't be signed by multiple signers when using v3 signature scheme
        assertInstallFails("v3-rsa-pkcs1-sha256-2048-1_and_2.apk");
    }

    @Test
    public void testInstallV3HasMultiplePlatformSigners() throws Exception {
        // tests that an app can be signed by multiple v3 signers if they target different platform
        // versions
        assertInstallSucceeds("v3-rsa-pkcs1-sha256-2048-1_P_and_2_Qplus.apk");
    }

    @Test
    public void testSharedKeyInSeparateLineageRetainsDeclaredCapabilities() throws Exception {
        // This test verifies when a key is used in the signing lineage of multiple apps each
        // instance of the key retains its declared capabilities.

        // This app has granted the PERMISSION capability to the previous signer in the lineage
        // but has revoked the SHARED_USER_ID capability.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-shUid-cap-declperm2.apk");
        // This app has granted the SHARED_USER_ID capability to the previous signer in the lineage
        // but has revoked the PERMISSION capability.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-no-perm-cap-sharedUid.apk");

        // Reboot the device to ensure that the capabilities written to packages.xml are properly
        // assigned to the packages installed above; it's possible immediately after a package
        // install the capabilities are as declared, but then during the reboot shared signing
        // keys also share the initial declared capabilities.
        getDevice().reboot();

        // This app is signed with the original shared signing key in the lineage and is part of the
        // sharedUserId; since the other app in this sharedUserId has granted the required
        // capability in the lineage the install should succeed.
        assertInstallFromBuildSucceeds("v3-ec-p256-1-sharedUid-companion2.apk");
        // This app is signed with the original shared signing key in the lineage and requests the
        // signature permission declared by the test app above. Since that app granted the
        // PERMISSION capability to the previous signer in the lineage this app should have the
        // permission granted.
        assertInstallFromBuildSucceeds("v3-ec-p256-1-companion-usesperm.apk");
        Utils.runDeviceTests(getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS, "testHasPerm");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31TargetTPlatformUsesRotatedKey() throws Exception {
        // The v3.1 signature block is intended to allow applications to target T+ for APK signing
        // key rotation without needing multi-targeting APKs. This test verifies a standard APK
        // install with the rotated key in the v3.1 signing block targeting T is recognized by the
        // platform, and this rotated key is used as the signing identity.
        assertInstallSucceeds("v31-ec-p256_2-tgt-33.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testUsingRotatedSigner");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31TargetLaterThanDevicePlatformUsesOriginalKey() throws Exception {
        // The v3.1 signature block allows targeting SDK versions later than T for rotation; for
        // this test a target of 100001 is used assuming it will be beyond the platform's version.
        // Since the target version for rotation is beyond the platform's version the original
        // signer from the v3.0 block should be used.
        assertInstallSucceeds("v31-ec-p256_2-tgt-100001.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testUsingOriginalSigner");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31SignersTargetPAnd100001PlatformUsesTargetPSigner() throws Exception {
        // The v3.1 signature scheme allows signer configs to target SDK versions; if a rotated
        // signer config is targeting P, the v3.0 block will include a signature with that rotated
        // config. This test verifies when the v3.1 signer is targeting an SDK version beyond that
        // of the platform's, the rotated signing config from the v3.0 block is used by the
        // platform.
        assertInstallSucceeds("v31-ec-p256_2-tgt-28-ec-p256_3-tgt-100001.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testUsingRotatedSigner");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31BlockStrippedWithV3StrippingProtectionAttrSet() throws Exception {
        // With the introduction of the v3.1 signature scheme, a new stripping protection attribute
        // has been added to the v3.0 signer to protect against stripping and modification of the
        // v3.1 signing block. This test verifies a stripped v3.1 block is detected when the v3.0
        // stripping protection attribute is set.
        assertInstallFails("v31-block-stripped-v3-attr-value-33.apk");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31BlockWithMultipleSignersUsesCorrectSigner() throws Exception {
        // All of the APKs for this test use multiple v3.1 signers; those targeting SDK versions
        // expected to be outside the version of a device under test use the original signer, and
        // those targeting an expected range for a device use the rotated key. This test is
        // intended to ensure the signer with the min / max SDK version that matches the device
        // SDK version is used.

        // The APK used for this test contains two signers in the v3.1 signing block. The first
        // has a range from 100001 to Integer.MAX_VALUE and is using the original signing key;
        // the second targets 33 to 100000 using the rotated key.
        assertInstallSucceeds("v31-ec-p256_2-tgt-33-ec-p256-tgt-100001.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testUsingRotatedSigner");
        uninstallPackage();

        // The APK for this test contains two signers in the v3.1 block, one targeting SDK versions
        // 1 to 27 using the original signer, and the other targeting 33+ using the rotated signer.
        assertInstallSucceeds("v31-ec-p256_2-tgt-33-ec-p256-tgt-1-27.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testUsingRotatedSigner");
        uninstallPackage();

        // This APK combines the extra signers from the APKs above, one targeting 1 to 27 with the
        // original signing key, another targeting 100001+ with the original signing key, and the
        // last targeting 33 to 100000 with the rotated key.
        assertInstallSucceeds("v31-ec-p256_2-tgt-33-ec-p256-tgt-1-27-and-100001.apk");
        Utils.runDeviceTests(
                getDevice(), DEVICE_TESTS_PKG, DEVICE_TESTS_CLASS,
                "testUsingRotatedSigner");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31UpdateV3ToFromV31Succeeds() throws Exception {
        // Since the v3.1 block is just intended to allow targeting SDK versions T and later for
        // rotation, an APK signed with the rotated key in a v3.0 signing block should support
        // updates to an APK signed with the same signing key in a v3.1 signing block.
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-default-caps.apk");
        assertInstallSucceeds("v31-ec-p256_2-tgt-33.apk");
        uninstallPackage();

        // Similarly an APK signed with the rotated key in a v3.1 signing block should support
        // updates to an APK signed with the same signing key in a v3.0 signing block.
        assertInstallSucceeds("v31-ec-p256_2-tgt-33.apk");
        assertInstallFromBuildSucceeds("v3-ec-p256-with-por_1_2-default-caps.apk");
        uninstallPackage();
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31RotationTargetModifiedReportedByV3() throws Exception {
        // When determining if a signer in the v3.1 signing block should be applied, the min / max
        // SDK versions from the signer are compared against the device's SDK version; if the device
        // is not within the signer's range then the block is skipped, other v3.1 blocks are
        // checked, and finally the v3.0 block is used. The v3.0 signer block contains an additional
        // attribute with the rotation-min-sdk-version that was expected in the v3.1 signing
        // block; if this attribute's value does not match what was found in the v3.1 block the
        // APK should fail to install.
        assertInstallFails("v31-ec-p256_2-tgt-33-modified.apk");
    }

    @CddTest(requirement="4/C-0-2")
    @Test
    public void testV31RotationTargetsDevRelease() throws Exception {
        // The v3.1 signature scheme allows targeting a platform release under development through
        // the use of a rotation-targets-dev-release additional attribute. Since a platform under
        // development shares the same SDK version as the most recently released platform, the
        // attribute is used by the platform to determine if a signer block should be applied. If
        // the signer's minSdkVersion is the same as the device's SDK version and this attribute
        // is set, then the platform will check the value of ro.build.version.codename; a value of
        // "REL" indicates the platform is a release platform, so the current signer block will not
        // be used. During T's development, the SDK version is 31 and the codename is not "REL", so
        // this test APK will install on T during development as well as after its release since
        // the SDK version will be bumped at that point.
        assertInstallSucceeds("v31-ec-p256_2-tgt-31-dev-release.apk");
    }

    @Test
    public void testInstallTargetSdk30WithV1Signers() throws Exception {
        // An app targeting SDK version >= 30 must have at least a V2 signature; this test verifies
        // an app targeting SDK version 30 with only a V1 signature fails to install.
        assertInstallFails("v1-ec-p256-two-signers-targetSdk-30.apk");
    }

    @Test
    public void testInstallTargetSdk30WithV1V2Signers() throws Exception {
        // An app targeting SDK version >= 30 must have at least a V2 signature; this test verifies
        // that an app targeting SDK version 30 with both a V1 and V2 signature installs
        // successfully.
        installApkFromBuild("v1v2-ec-p256-two-signers-targetSdk-30.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV2Signer(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APK generated with:
        // apksigner sign --v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled
        assertInstallV4Succeeds("v4-digest-v2.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV3Signer(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APK generated with:
        // apksigner sign --v2-signing-enabled false --v3-signing-enabled true --v4-signing-enabled
        assertInstallV4Succeeds("v4-digest-v3.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV2V3Signer(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APK generated with:
        // apksigner sign --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled
        assertInstallV4Succeeds("v4-digest-v2v3.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV2NoVeritySigner(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APK generated with:
        // --v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled
        // Full commands in generate-apks.sh
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha256withDSA.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha256withEC.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha256withRSA.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha512withEC.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha512withRSA.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV2VeritySigner(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APK generated with:
        // --v2-signing-enabled true --v3-signing-enabled false
        // --v4-signing-enabled --verity-enabled
        // Full commands in generate-apks.sh
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha256withDSA-Verity.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha256withEC-Verity.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v2-Sha256withRSA-Verity.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV3NoVeritySigner(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APK generated with:
        // --v2-signing-enabled false --v3-signing-enabled true --v4-signing-enabled
        // Full commands in generate-apks.sh
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha256withDSA.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha256withEC.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha256withRSA.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha512withEC.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha512withRSA.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV3VeritySigner(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APK generated with:
        // --v2-signing-enabled false --v3-signing-enabled true
        // --v4-signing-enabled --verity-enabled
        // Full commands in generate-apks.sh
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha256withDSA-Verity.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha256withEC-Verity.apk");
        assertInstallV4SucceedsAndUninstall("v4-digest-v3-Sha256withRSA-Verity.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV2SignerDoesNotVerify(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APKs generated with:
        // apksigner sign -v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled

        // Malformed v4 signature - first byte of v4 signing_info.signature is flipped
        assertInstallV4FailsWithError("v4-digest-v2-badv4signature.apk", "did not verify");
        // Malformed digest - first byte of v4 signing_info.apk_digest is flipped
        assertInstallV4FailsWithError("v4-digest-v2-badv2digest.apk", "did not verify");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV3SignerDoesNotVerify(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APKs generated with:
        // apksigner sign -v2-signing-enabled false --v3-signing-enabled true --v4-signing-enabled

        // Malformed v4 signature - first byte of v4 signing_info.signature is flipped
        assertInstallV4FailsWithError("v4-digest-v3-badv4signature.apk", "did not verify");

        // Malformed digest - first byte of v4 signing_info.apk_digest is flipped
        assertInstallV4FailsWithError("v4-digest-v3-badv3digest.apk", "did not verify");

    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithV2V3SignerDoesNotVerify(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // APKs generated with:
        // apksigner sign -v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled

        // Malformed v4 signature - first byte of v4 signing_info.signature is flipped
        assertInstallV4FailsWithError("v4-digest-v2v3-badv4signature.apk", "did not verify");

        // Malformed digest - first byte of v4 signing_info.apk_digest is flipped
        assertInstallV4FailsWithError("v4-digest-v2v3-badv2v3digest.apk", "did not verify");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4With128BytesAdditionalDataSucceeds(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner to fill additional data of size 128 bytes.
        assertInstallV4Succeeds("v4-digest-v3-128bytes-additional-data.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4With256BytesAdditionalDataFails(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner to fill additional data of size 256 bytes.
        if (onIncremental) {
            // For incremental, a bad Merkle tree will fail.
            assertInstallV4FailsWithError("v4-digest-v3-256bytes-additional-data.apk",
                    "additionalData has to be at most 128 bytes");
        } else {
            // For non-incremental, Merkle tree in idsig is not used.
            assertInstallV4Succeeds("v4-digest-v3-256bytes-additional-data.apk");
        }
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4With10MBytesAdditionalDataFails(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner to fill additional data of size 10 * 1024 * 1024 bytes.
        if (onIncremental) {
            // For incremental, a bad Merkle tree will fail.
            assertInstallV4FailsWithError("v4-digest-v3-10mbytes-additional-data.apk",
                    "Failure");
        } else {
            // For non-incremental, Merkle tree in idsig is not used.
            assertInstallV4Succeeds("v4-digest-v3-10mbytes-additional-data.apk");
        }
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithWrongBlockSize(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner with the wrong block size in the v4 signature.
        assertInstallV4FailsWithError("v4-digest-v3-wrong-block-size.apk",
                "did not verify");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithDifferentBlockSize(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner with the different block size (2048 instead of 4096).
        assertInstallV4FailsWithError("v4-digest-v3-merkle-tree-different-block-size.apk",
                "Unsupported log2BlockSize: 11");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithWrongRawRootHash(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner with the wrong raw root hash in the v4 signature.
        assertInstallV4FailsWithError("v4-digest-v3-wrong-raw-root-hash.apk", "Failure");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithWrongSignatureBytes(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner with the wrong signature bytes in the v4 signature.
        assertInstallV4FailsWithError("v4-digest-v3-wrong-sig-bytes.apk",
                "did not verify");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithWrongSignatureBytesSize(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner with the wrong signature byte size in the v4 signature.
        assertInstallV4FailsWithError("v4-digest-v3-wrong-sig-bytes-size.apk",
                "Failure");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithNoMerkleTree(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        if (onIncremental) {
            // Editing apksigner to not include the Merkle tree.
            assertInstallV4FailsWithError("v4-digest-v3-no-merkle-tree.apk",
                    "Failure");
        } else {
            assertInstallV4Succeeds("v4-digest-v3-no-merkle-tree.apk");
        }
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithWithTrailingDataInMerkleTree(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner to add trailing data after the Merkle tree.
        if (onIncremental) {
            // For incremental, a bad Merkle tree will fail.
            assertInstallV4FailsWithError("v4-digest-v3-merkle-tree-1mb-trailing-data.apk",
                    "Failure");
        } else {
            // For non-incremental, Merkle tree in idsig is not used.
            assertInstallV4Succeeds("v4-digest-v3-merkle-tree-1mb-trailing-data.apk");
        }
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4WithMerkleTreeBitsFlipped(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // Editing apksigner to flip few bits in the only node of the Merkle tree of a small app.
        if (onIncremental) {
            // For incremental, a bad Merkle tree will fail.
            assertInstallV4FailsWithError("v4-digest-v3-merkle-tree-bit-flipped.apk",
                    "Failed to parse");
        } else {
            // For non-incremental, Merkle tree in idsig is not used.
            assertInstallV4Succeeds("v4-digest-v3-merkle-tree-bit-flipped.apk");
        }
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testV4IncToV3NonIncSameKeyUpgradeSucceeds(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // See cts/hostsidetests/appsecurity/res/pkgsigverify/generate-apks.sh for the command
        // to generate the apks
        assertInstallV4Succeeds("v4-inc-to-v3-noninc-ec-p256-appv1.apk");

        // non-incremental upgrade with the same key.
        assertInstallSucceeds("v4-inc-to-v3-noninc-ec-p256-appv2.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testV4IncToV3NonIncMismatchingKeyUpgradeFails(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // See cts/hostsidetests/appsecurity/res/pkgsigverify/generate-apks.sh for the command
        // to generate the apks
        assertInstallV4Succeeds("v4-inc-to-v3-noninc-ec-p256-appv1.apk");

        // non-incremental upgrade with a mismatching key.
        assertInstallFailsWithError("v4-inc-to-v3-noninc-ec-p384-appv2.apk",
                "signatures do not match newer version");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testV4IncToV3NonIncRotatedKeyUpgradeSucceeds(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // See cts/hostsidetests/appsecurity/res/pkgsigverify/generate-apks.sh for the command
        // to generate the apks
        assertInstallV4Succeeds("v4-inc-to-v3-noninc-ec-p256-appv1.apk");

        // non-incremental upgrade with key rotation.
        assertInstallSucceeds("v4-inc-to-v3-noninc-ec-p384-rotated-ec-p256-appv2.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testV4IncToV3NonIncMismatchedRotatedKeyUpgradeFails(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // See cts/hostsidetests/appsecurity/res/pkgsigverify/generate-apks.sh for the command
        // to generate the apks
        assertInstallV4Succeeds("v4-inc-to-v3-noninc-dsa-3072-appv1.apk");

        // non-incremental upgrade with key rotation mismatch with key used in app v1.
        assertInstallFailsWithError("v4-inc-to-v3-noninc-ec-p384-rotated-ec-p256-appv2.apk",
                "signatures do not match newer version");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testV4IncToV2NonIncSameKeyUpgradeSucceeds(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // See cts/hostsidetests/appsecurity/res/pkgsigverify/generate-apks.sh for the command
        // to generate the apks
        assertInstallV4Succeeds("v4-inc-to-v2-noninc-ec-p256-appv1.apk");

        // non-incremental upgrade with the same key.
        assertInstallSucceeds("v4-inc-to-v2-noninc-ec-p256-appv2.apk");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testV4IncToV2NonIncMismatchingKeyUpgradeFails(boolean onIncremental)
            throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // See cts/hostsidetests/appsecurity/res/pkgsigverify/generate-apks.sh for the command
        // to generate the apks
        assertInstallV4Succeeds("v4-inc-to-v2-noninc-ec-p256-appv1.apk");

        // non-incremental upgrade with a mismatching key.
        assertInstallFailsWithError("v4-inc-to-v2-noninc-ec-p384-appv2.apk",
                "signatures do not match newer version");
    }

    @Test
    @Parameters(method = "installOnIncremental")
    public void testInstallV4UpdateAfterRotation(boolean onIncremental) throws Exception {
        checkAssumptionAndSetIdsigInstallMode(onIncremental);

        // This test performs an end to end verification of the update of an app with a rotated
        // key. The app under test exports a bound service that performs its own PackageManager key
        // rotation API verification, and the instrumentation test binds to the service and invokes
        // the verifySignatures method to verify that the key rotation APIs return the expected
        // results. The instrumentation test app is signed with the same key and lineage as the
        // app under test to also provide a second app that can be used for the checkSignatures
        // verification.

        // Install the initial versions of the apps; the test method verifies the app under test is
        // signed with the original signing key.
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService.apk");
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryServiceTest.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_noRotation_succeeds");

        // Install the second version of the app signed with the rotated key. This test verifies the
        // app still functions as expected after the update with the rotated key. The
        // instrumentation test app is not updated here to allow verification of the pre-key
        // rotation behavior for the checkSignatures APIs. These APIs should behave similar to the
        // GET_SIGNATURES flag in that if one or both apps have a signing lineage if the oldest
        // signers in the lineage match then the methods should return that the signatures match
        // even if one is signed with a newer key in the lineage.
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService_v2.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");

        // Installs the third version of the app under test and the instrumentation test, both
        // signed with the same rotated key and lineage. This test is intended to verify that the
        // app can still be updated and function as expected after an update with a rotated key.
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryService_v3.apk");
        assertInstallV4FromBuildSucceeds("CtsSignatureQueryServiceTest_v2.apk");
        Utils.runDeviceTests(getDevice(), SERVICE_TEST_PKG, SERVICE_TEST_CLASS,
                "verifySignatures_withRotation_succeeds");
    }

    private boolean hasIncrementalFeature() throws Exception {
        return "true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery"));
    }

    private void checkAssumptionAndSetIdsigInstallMode(boolean onIncremental) throws Exception {
        // Due to the limitation of existing test runners (i.e. DeviceParameterizedRunner and
        // DeviceJUnit4Parameterized), there's no easy way to parameterize only some of the test
        // methods while avoid recording the parameter manually.
        mUseIncrementalForInstallWithIdsig = onIncremental;

        if (onIncremental) {
            // V4 is only enabled on devices with Incremental feature
            assumeTrue(hasIncrementalFeature());
        } else {
            // Install V4 in classic install session
            assumeTrue(android.security.Flags.extendVbChainToUpdatedApk());
        }
    }

    private void assertInstallSucceeds(String apkFilenameInResources) throws Exception {
        String installResult = installPackageFromResource(apkFilenameInResources);
        if (installResult != null) {
            fail("Failed to install " + apkFilenameInResources + ": " + installResult);
        }
    }

    private void assertInstallEphemeralSucceeds(String apkFilenameInResources) throws Exception {
        String installResult = installEphemeralPackageFromResource(apkFilenameInResources);
        if (installResult != null) {
            fail("Failed to install " + apkFilenameInResources + ": " + installResult);
        }
    }

    private void assertInstallSucceedsForEach(
            String apkFilenamePatternInResources, String[] args) throws Exception {
        for (String arg : args) {
            String apkFilenameInResources =
                    String.format(Locale.US, apkFilenamePatternInResources, arg);
            String installResult = installPackageFromResource(apkFilenameInResources);
            if (installResult != null) {
                fail("Failed to install " + apkFilenameInResources + ": " + installResult);
            }
            try {
                uninstallPackage();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to uninstall after installing " + apkFilenameInResources, e);
            }
        }
    }

    private void assertInstallV4Succeeds(String apkFilenameInResources) throws Exception {
        String installResult = installV4PackageFromResource(apkFilenameInResources);
        if (!installResult.equals("Success")) {
            fail("Failed to install " + apkFilenameInResources + ": " + installResult);
        }
    }

    private void assertInstallV4FromBuildSucceeds(String apkName) throws Exception {
        String installResult = installV4PackageFromBuild(apkName);
        if (!installResult.equals("Success")) {
            fail("Failed to install " + apkName + ": " + installResult);
        }
    }

    private void assertInstallV4SucceedsAndUninstall(String apkFilenameInResources)
            throws Exception {
        assertInstallV4Succeeds(apkFilenameInResources);
        try {
            uninstallPackage();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to uninstall after installing " + apkFilenameInResources, e);
        }
    }

    private void assertInstallV4FailsWithError(String apkFilenameInResources, String errorSubstring)
            throws Exception {
        String installResult = installV4PackageFromResource(apkFilenameInResources);
        if (installResult.equals("Success")) {
            fail("Install of " + apkFilenameInResources + " succeeded but was expected to fail"
                    + " with \"" + errorSubstring + "\"");
        }
        assertContains(
                "Install failure message of " + apkFilenameInResources,
                errorSubstring,
                installResult);
    }

    private void assertInstallFailsWithError(
            String apkFilenameInResources, String errorSubstring) throws Exception {
        String installResult = installPackageFromResource(apkFilenameInResources);
        if (installResult == null) {
            fail("Install of " + apkFilenameInResources + " succeeded but was expected to fail"
                    + " with \"" + errorSubstring + "\"");
        }
        assertContains(
                "Install failure message of " + apkFilenameInResources,
                errorSubstring,
                installResult);
    }

    private void assertInstallEphemeralFailsWithError(
            String apkFilenameInResources, String errorSubstring) throws Exception {
        String installResult = installEphemeralPackageFromResource(apkFilenameInResources);
        if (installResult == null) {
            fail("Install of " + apkFilenameInResources + " succeeded but was expected to fail"
                    + " with \"" + errorSubstring + "\"");
        }
        assertContains(
                "Install failure message of " + apkFilenameInResources,
                errorSubstring,
                installResult);
    }

    private void assertInstallFails(String apkFilenameInResources) throws Exception {
        String installResult = installPackageFromResource(apkFilenameInResources);
        if (installResult == null) {
            fail("Install of " + apkFilenameInResources + " succeeded but was expected to fail");
        }
    }

    private static void assertContains(String message, String expectedSubstring, String actual) {
        String errorPrefix = ((message != null) && (message.length() > 0)) ? (message + ": ") : "";
        if (actual == null) {
            fail(errorPrefix + "Expected to contain \"" + expectedSubstring + "\", but was null");
        }
        if (!actual.contains(expectedSubstring)) {
            fail(errorPrefix + "Expected to contain \"" + expectedSubstring + "\", but was \""
                    + actual + "\"");
        }
    }

    private void installDeviceTestPkg() throws Exception {
        assertInstallFromBuildSucceeds(DEVICE_TESTS_APK);
    }

    private void assertInstallFromBuildSucceeds(String apkName) throws Exception {
        String result = installApkFromBuild(apkName);
        assertNull("failed to install " + apkName + ", Reason: " + result, result);
    }

    private void assertInstallFromBuildFails(String apkName) throws Exception {
        String result = installApkFromBuild(apkName);
        assertNotNull("Successfully installed " + apkName + " when failure was expected", result);
    }

    private String installApkFromBuild(String apkName) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        File apk = buildHelper.getTestFile(apkName);
        try {
            return getDevice().installPackage(apk, true, INSTALL_ARG_FORCE_QUERYABLE,
                    INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK);
        } finally {
            getDevice().deleteFile("/data/local/tmp/" + apk.getName());
        }
    }

    private String installPackageFromResource(String apkFilenameInResources, boolean ephemeral)
            throws IOException, DeviceNotAvailableException {
        // ITestDevice.installPackage API requires the APK to be install to be a File. We thus
        // copy the requested resource into a temporary file, attempt to install it, and delete the
        // file during cleanup.
        File apkFile = null;
        try {
            apkFile = getFileFromResource(apkFilenameInResources);
            if (ephemeral) {
                return getDevice().installPackage(apkFile, true, "--ephemeral",
                        INSTALL_ARG_FORCE_QUERYABLE, INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK);
            } else {
                return getDevice().installPackage(apkFile, true, INSTALL_ARG_FORCE_QUERYABLE,
                        INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK);
            }
        } finally {
            cleanUpFile(apkFile);
            getDevice().deleteFile("/data/local/tmp/" + apkFile.getName());
        }
    }

    private String installV4PackageFromResource(String apkFilenameInResources)
            throws IOException, DeviceNotAvailableException {
        File apkFile = null;
        File v4SignatureFile = null;
        String remoteApkFilePath = null, remoteV4SignaturePath = null;
        try {
            apkFile = getFileFromResource(apkFilenameInResources);
            v4SignatureFile = getFileFromResource(apkFilenameInResources + ".idsig");
            remoteApkFilePath = pushFileToRemote(apkFile);
            remoteV4SignaturePath = pushFileToRemote(v4SignatureFile);
            return installV4Package(remoteApkFilePath);
        } finally {
            cleanUpFile(apkFile);
            cleanUpFile(v4SignatureFile);
            getDevice().deleteFile(remoteApkFilePath);
            getDevice().deleteFile(remoteV4SignaturePath);
        }
    }

    private String installV4PackageFromBuild(String apkName)
            throws IOException, DeviceNotAvailableException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        File apkFile = buildHelper.getTestFile(apkName);
        File v4SignatureFile = buildHelper.getTestFile(apkName + ".idsig");
        String remoteApkFilePath = pushFileToRemote(apkFile);
        String remoteV4SignaturePath = pushFileToRemote(v4SignatureFile);
        try {
            return installV4Package(remoteApkFilePath);
        } finally {
            getDevice().deleteFile(remoteApkFilePath);
            getDevice().deleteFile(remoteV4SignaturePath);
        }
    }

    private String pushFileToRemote(File localFile) throws DeviceNotAvailableException {
        String remotePath = "/data/local/tmp/pkginstalltest-" + localFile.getName();
        getDevice().pushFile(localFile, remotePath);
        return remotePath;
    }

    private String installV4Package(String remoteApkPath)
            throws DeviceNotAvailableException {
        var installer = new InstallMultiple();
        if (mUseIncrementalForInstallWithIdsig) {
            installer.useIncremental();
        } else {
            // Add to the install session
            installer.addRemoteFile(remoteApkPath + ".idsig");
        }
        return installer
            .bypassLowTargetSdkBlock()
            .forceQueryable()
            .addRemoteFile(remoteApkPath)
            .runForResult();
    }

    private File getFileFromResource(String filenameInResources)
            throws IOException, IllegalArgumentException {
        String fullResourceName = TEST_APK_RESOURCE_PREFIX + filenameInResources;
        File tempDir = FileUtil.createTempDir("pkginstalltest");
        File file = new File(tempDir, filenameInResources);
        InputStream in = getClass().getResourceAsStream(fullResourceName);
        if (in == null) {
            throw new IllegalArgumentException("Resource not found: " + fullResourceName);
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buf = new byte[65536];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            out.write(buf, 0, chunkSize);
        }
        out.close();
        return file;
    }

    private void cleanUpFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
            // Delete the parent dir as well which is a temp dir
            File parent = file.getParentFile();
            if (parent.exists()) {
                parent.delete();
            }
        }
    }

    private String installPackageFromResource(String apkFilenameInResources)
            throws IOException, DeviceNotAvailableException {
        return installPackageFromResource(apkFilenameInResources, false);
    }

    private String installEphemeralPackageFromResource(String apkFilenameInResources)
            throws IOException, DeviceNotAvailableException {
        return installPackageFromResource(apkFilenameInResources, true);
    }

    private String uninstallPackage() throws DeviceNotAvailableException {
        String result1 = getDevice().uninstallPackage(TEST_PKG);
        String result2 = getDevice().uninstallPackage(TEST_PKG2);
        return result1 != null ? result1 : result2;
    }

    private String uninstallCompanionPackages() throws DeviceNotAvailableException {
        String result1 = getDevice().uninstallPackage(COMPANION_TEST_PKG);
        String result2 = getDevice().uninstallPackage(COMPANION2_TEST_PKG);
        String result3 = getDevice().uninstallPackage(COMPANION3_TEST_PKG);
        return Stream.of(result1, result2, result3)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String uninstallDeviceTestPackage() throws DeviceNotAvailableException {
        return getDevice().uninstallPackage(DEVICE_TESTS_PKG);
    }

    private void uninstallServicePackages() throws DeviceNotAvailableException {
        getDevice().uninstallPackage(SERVICE_PKG);
        getDevice().uninstallPackage(SERVICE_TEST_PKG);
    }

    private void uninstallPackages() throws DeviceNotAvailableException {
        uninstallPackage();
        uninstallCompanionPackages();
        uninstallDeviceTestPackage();
        uninstallServicePackages();
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        InstallMultiple() {
            super(getDevice(), getBuild(), getAbi(), /* grantPermissions */ true);
        }

        @Override
        protected String deriveRemoteName(String originalName, int index) {
            return originalName;
        }
    }
}
