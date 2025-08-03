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

package android.content.pm.cts;

import static android.Manifest.permission.INSTALL_PACKAGES;
import static android.Manifest.permission.USE_INSTALLER_V2;
import static android.Manifest.permission.USE_SYSTEM_DATA_LOADERS;
import static android.content.pm.Checksum.TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256;
import static android.content.pm.Checksum.TYPE_PARTIAL_MERKLE_ROOT_1M_SHA512;
import static android.content.pm.Checksum.TYPE_WHOLE_MD5;
import static android.content.pm.Checksum.TYPE_WHOLE_MERKLE_ROOT_4K_SHA256;
import static android.content.pm.Checksum.TYPE_WHOLE_SHA1;
import static android.content.pm.Checksum.TYPE_WHOLE_SHA256;
import static android.content.pm.Checksum.TYPE_WHOLE_SHA512;
import static android.content.pm.PackageInstaller.LOCATION_DATA_APP;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.TRUST_ALL;
import static android.content.pm.PackageManager.TRUST_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApkChecksum;
import android.content.pm.Checksum;
import android.content.pm.DataLoaderParams;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.cts.util.AbandonAllPackageSessionsRule;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ExceptionUtils;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CpuFeatures;
import com.android.compatibility.common.util.FileUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.internal.util.HexDump;
import com.android.server.pm.ApkChecksums;
import com.android.server.pm.PackageManagerShellCommandDataLoader;
import com.android.server.pm.PackageManagerShellCommandDataLoader.Metadata;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class ChecksumsTest {
    private static final String CTS_PACKAGE_NAME = "android.content.cts";
    private static final String V2V3_PACKAGE_NAME = "android.content.cts";
    private static final String V4_PACKAGE_NAME = "com.example.helloworld";
    private static final String FIXED_PACKAGE_NAME = "android.appsecurity.cts.tinyapp";
    private static final String FIXED_FSVERITY_PACKAGE_NAME =
            "android.appsecurity.cts.apkveritytestapp";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";

    private static final String TEST_V4_APK = "HelloWorld5.apk";
    private static final String TEST_V4_SPLIT0 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_V4_SPLIT1 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_V4_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";
    private static final String TEST_V4_SPLIT3 = "HelloWorld5_xxhdpi-v4.apk";
    private static final String TEST_V4_SPLIT4 = "HelloWorld5_xxxhdpi-v4.apk";

    private static final String TEST_FIXED_APK = "CtsPkgInstallTinyAppV2V3V4.apk";
    private static final String TEST_FIXED_APK_DIGESTS_FILE =
            "CtsPkgInstallTinyAppV2V3V4.digests";
    private static final String TEST_FIXED_APK_DIGESTS_SIGNATURE =
            "CtsPkgInstallTinyAppV2V3V4.digests.signature";
    private static final String TEST_CERTIFICATE = "test-cert.x509.pem";
    private static final String TEST_FIXED_APK_V1 = "CtsPkgInstallTinyAppV1.apk";
    private static final String TEST_FIXED_APK_SHA512 =
            "CtsPkgInstallTinyAppV2V3V4-Sha512withEC.apk";
    private static final String TEST_FIXED_APK_VERITY = "CtsPkgInstallTinyAppV2V3V4-Verity.apk";

    private static final String TEST_FIXED_APK_FSVERITY = "CtsApkVerityTestAppPrebuilt.apk";
    private static final String TEST_FIXED_APK_FSVERITY_FSVSIG =
            "CtsApkVerityTestAppPrebuilt.apk.fsv_sig";
    private static final String TEST_FIXED_APK_FSVERITY_SHA256_ARM64 =
            "84c9974e74258a9c5abbc8a797358fe8ebf8918edf62383ea621e9e9e6461864";
    private static final String TEST_FIXED_APK_FSVERITY_SHA256_X86_64 =
            "5073070ee9e9a7821b4044b457d95a2bb81f349ba32d31acd0952e4a2617075a";

    private static final String TEST_FIXED_APK_V2_SHA256 =
            "1eec9e86e322b8d7e48e255fc3f2df2dbc91036e63982ff9850597c6a37bbeb3";
    private static final String TEST_FIXED_APK_SHA256 =
            "91aa30c1ce8d0474052f71cb8210691d41f534989c5521e27e794ec4f754c5ef";
    private static final String TEST_FIXED_APK_MD5 = "c19868da017dc01467169f8ea7c5bc57";
    private static final Checksum[] TEST_FIXED_APK_DIGESTS = new Checksum[]{
            new Checksum(TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256,
                    hexStringToBytes(TEST_FIXED_APK_V2_SHA256)),
            new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(TEST_FIXED_APK_SHA256)),
            new Checksum(TYPE_WHOLE_MD5, hexStringToBytes(TEST_FIXED_APK_MD5))};
    private static final Checksum[] TEST_FIXED_APK_WRONG_DIGESTS = new Checksum[]{
            new Checksum(TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256, hexStringToBytes("850597c6a37bbeb3")),
            new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(TEST_FIXED_APK_SHA256)),
            new Checksum(TYPE_WHOLE_MD5, hexStringToBytes(TEST_FIXED_APK_MD5))};

    /** Default is to not use fs-verity since it depends on kernel support. */
    private static final int FSVERITY_DISABLED = 0;

    /** Standard fs-verity. */
    private static final int FSVERITY_ENABLED = 2;

    private static final byte[] NO_SIGNATURE = null;

    private static final int ALL_CHECKSUMS =
            TYPE_WHOLE_MERKLE_ROOT_4K_SHA256 | TYPE_WHOLE_MD5 | TYPE_WHOLE_SHA1 | TYPE_WHOLE_SHA256
                    | TYPE_WHOLE_SHA512
                    | TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256 | TYPE_PARTIAL_MERKLE_ROOT_1M_SHA512;

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private static PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    private static PackageInstaller getPackageInstaller() {
        return getPackageManager().getPackageInstaller();
    }

    @Rule
    public AbandonAllPackageSessionsRule mAbandonSessionsRule = new AbandonAllPackageSessionsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @AfterClass
    public static void onAfterClass() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        uninstallPackageSilently(FIXED_FSVERITY_PACKAGE_NAME);
    }

    @Test
    public void testNameNotFound() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);
        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> pm.requestChecksums(V4_PACKAGE_NAME, true, 0, TRUST_NONE, receiver));
    }

    @Test
    public void testReadWriteChecksums() throws Exception {
        // Read checksums from file and confirm they are the same as hardcoded.
        checkStoredChecksums(TEST_FIXED_APK_DIGESTS, TEST_FIXED_APK_DIGESTS_FILE);

        // Write checksums and confirm that the file stays the same.
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(os)) {
            for (Checksum checksum : TEST_FIXED_APK_DIGESTS) {
                Checksum.writeToStream(dos, checksum);
            }
            final byte[] fileBytes = Files.readAllBytes(
                    Paths.get(createApkPath(TEST_FIXED_APK_DIGESTS_FILE)));
            final byte[] localBytes = os.toByteArray();
            Assert.assertArrayEquals(fileBytes, localBytes);
        }
    }

    @Test
    public void testDefaultChecksums() throws Exception {
        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(V2V3_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
    }

    @Test
    public void testSplitsDefaultChecksums() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);
        installSplits(new String[]{TEST_V4_APK, TEST_V4_SPLIT0, TEST_V4_SPLIT1, TEST_V4_SPLIT2,
                TEST_V4_SPLIT3, TEST_V4_SPLIT4});
        assertTrue(isAppInstalled(V4_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(V4_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 6, Arrays.toString(checksums));
        // v2/v3 signature use 1M merkle tree.
        assertEquals(checksums[0].getSplitName(), null);
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[1].getSplitName(), "config.hdpi");
        assertEquals(checksums[1].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[2].getSplitName(), "config.mdpi");
        assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[3].getSplitName(), "config.xhdpi");
        assertEquals(checksums[3].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[4].getSplitName(), "config.xxhdpi");
        assertEquals(checksums[4].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[5].getSplitName(), "config.xxxhdpi");
        assertEquals(checksums[5].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
    }

    @Test
    public void testFixedDefaultChecksums() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackage(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        // v2/v3 signature use 1M merkle tree.
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[0].getInstallerCertificate());
    }

    @Test
    public void testFixedV1DefaultChecksums() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackage(TEST_FIXED_APK_V1);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 0, Arrays.toString(checksums));
    }

    @Test
    public void testFixedSha512DefaultChecksums() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackage(TEST_FIXED_APK_SHA512);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        // v2/v3 signature use 1M merkle tree.
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA512);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "6b866e8a54a3e358dfc20007960fb96123845f6c6d6c45f5fddf88150d71677f"
                        + "4c3081a58921c88651f7376118aca312cf764b391cdfb8a18c6710f9f27916a0");
        assertNull(checksums[0].getInstallerCertificate());
    }

    @LargeTest
    @Test
    @RequiresFlagsDisabled(android.security.Flags.FLAG_DEPRECATE_FSV_SIG)
    public void testFixedFSVerityDefaultChecksums() throws Exception {
        assumeTrue(isApkVerityEnabled());
        uninstallPackageSilently(FIXED_FSVERITY_PACKAGE_NAME);
        installApkWithFSVerity(TEST_FIXED_APK_FSVERITY, TEST_FIXED_APK_FSVERITY_FSVSIG);
        assertTrue(isAppInstalled(FIXED_FSVERITY_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_FSVERITY_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 2, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MERKLE_ROOT_4K_SHA256);
        if (CpuFeatures.isArm64Cpu() || CpuFeatures.isArmCpu()) {
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    TEST_FIXED_APK_FSVERITY_SHA256_ARM64);
            assertEquals(bytesToHexString(checksums[1].getValue()),
                    "8c61bc2548521aa0005276af68e42253957e1e24c122f7d8bf10f1832d4014e5");
        } else if (CpuFeatures.isX86_64Cpu() || CpuFeatures.isX86Cpu()) {
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    TEST_FIXED_APK_FSVERITY_SHA256_X86_64);
            assertEquals(bytesToHexString(checksums[1].getValue()),
                    "6f7cfa569c4a25d7241e26c1c8ff274badbdefd7854d91b842b1a97a985d5917");
        } else {
            Assert.fail("Unsupported CPU ABI");
        }
        assertEquals(checksums[1].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
    }

    @LargeTest
    @Test
    @RequiresFlagsDisabled(android.security.Flags.FLAG_DEPRECATE_FSV_SIG)
    public void testFixedFSVerityDefaultChecksumsIncremental() throws Exception {
        assumeTrue(checkIncrementalDeliveryFeature());

        uninstallPackageSilently(FIXED_FSVERITY_PACKAGE_NAME);
        installFilesIncrementally(
                new String[]{TEST_FIXED_APK_FSVERITY, TEST_FIXED_APK_FSVERITY_FSVSIG});
        assertTrue(isAppInstalled(FIXED_FSVERITY_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_FSVERITY_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MERKLE_ROOT_4K_SHA256);
        if (CpuFeatures.isArm64Cpu() || CpuFeatures.isArmCpu()) {
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    TEST_FIXED_APK_FSVERITY_SHA256_ARM64);
        } else if (CpuFeatures.isX86_64Cpu() || CpuFeatures.isX86Cpu()) {
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    TEST_FIXED_APK_FSVERITY_SHA256_X86_64);
        } else {
            Assert.fail("Unsupported CPU ABI");
        }
    }

    @Test
    public void testFixedVerityDefaultChecksums() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackage(TEST_FIXED_APK_VERITY);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        // No usable hashes as verity-in-v2-signature does not cover the whole file.
        assertEquals(checksums.length, 0, Arrays.toString(checksums));
    }

    @Test
    @RequiresFlagsEnabled(android.content.pm.Flags.FLAG_ARCHIVING)
    public void testArchivedDefaultChecksums() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackage(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        byte[] archivedPackage = SystemUtil.runShellCommandByteOutput(
                getUiAutomation(),
                "pm get-archived-package-metadata " + FIXED_PACKAGE_NAME);
        uninstallPackageSilently(FIXED_PACKAGE_NAME);

        // Install as archived.
        Assert.assertEquals("Success\n", executeShellCommand(
                String.format("pm install-archived --bypass-low-target-sdk-block -r -i %s -t -S %s",
                        getContext().getPackageName(), archivedPackage.length), archivedPackage));
        assertTrue(isPackagePresent(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        assertThrows(PackageManager.NameNotFoundException.class, () ->
                pm.requestChecksums(
                        FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver));
    }

    @LargeTest
    @Test
    public void testFixedAllChecksums() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackage(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, ALL_CHECKSUMS, TRUST_NONE,
                receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 7, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "759626c33083fbf43215cb5b17156977d963d4c6850c0cb4e73162a665db560b");
        assertEquals(checksums[1].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[1].getValue()), TEST_FIXED_APK_MD5);
        assertEquals(checksums[2].getType(), TYPE_WHOLE_SHA1);
        assertEquals(bytesToHexString(checksums[2].getValue()),
                "331eef6bc57671de28cbd7e32089d047285ade6a");
        assertEquals(checksums[3].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[3].getValue()), TEST_FIXED_APK_SHA256);
        assertEquals(checksums[4].getType(), TYPE_WHOLE_SHA512);
        assertEquals(bytesToHexString(checksums[4].getValue()),
                "b59467fe578ebc81974ab3aaa1e0d2a76fef3e4ea7212a6f2885cec1af5253571"
                        + "1e2e94496224cae3eba8dc992144ade321540ebd458ec5b9e6a4cc51170e018");
        assertEquals(checksums[5].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[5].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertEquals(checksums[6].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA512);
        assertEquals(bytesToHexString(checksums[6].getValue()),
                "ef80a8630283f60108e8557c924307d0ccdfb6bbbf2c0176bd49af342f43bc84"
                        + "5f2888afcb71524196dda0d6dd16a6a3292bb75b431b8ff74fb60d796e882f80");
    }

    @LargeTest
    @Test
    public void testFixedV1AllChecksums() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackage(TEST_FIXED_APK_V1);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, ALL_CHECKSUMS, TRUST_NONE,
                receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 5, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "0b9bd6ef683e0c4e8940aba6460382b33e607c0fcf487f3dc6a44b715615d166");
        assertEquals(checksums[1].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[1].getValue()), "78e51e8c51e4adc6870cd71389e0f3db");
        assertEquals(checksums[2].getType(), TYPE_WHOLE_SHA1);
        assertEquals(bytesToHexString(checksums[2].getValue()),
                "f6654505f2274fd9bfc098b660cdfdc2e4da6d53");
        assertEquals(checksums[3].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[3].getValue()),
                "43755d36ec944494f6275ee92662aca95079b3aa6639f2d35208c5af15adff78");
        assertEquals(checksums[4].getType(), TYPE_WHOLE_SHA512);
        assertEquals(bytesToHexString(checksums[4].getValue()),
                "030fc815a4957c163af2bc6f30dd5b48ac09c94c25a824a514609e1476f91421"
                        + "e2c8b6baa16ef54014ad6c5b90c37b26b0f5c8aeb01b63a1db2eca133091c8d1");
    }

    @Test
    public void testDefaultIncrementalChecksums() throws Exception {
        assumeTrue(checkIncrementalDeliveryFeature());
        uninstallPackageSilently(V4_PACKAGE_NAME);

        installPackageIncrementally(TEST_V4_APK);
        assertTrue(isAppInstalled(V4_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(V4_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MERKLE_ROOT_4K_SHA256);
    }

    @Test
    public void testFixedDefaultIncrementalChecksums() throws Exception {
        assumeTrue(checkIncrementalDeliveryFeature());
        uninstallPackageSilently(FIXED_PACKAGE_NAME);

        installPackageIncrementally(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "759626c33083fbf43215cb5b17156977d963d4c6850c0cb4e73162a665db560b");
    }

    @LargeTest
    @Test
    public void testFixedAllIncrementalChecksums() throws Exception {
        assumeTrue(checkIncrementalDeliveryFeature());
        uninstallPackageSilently(FIXED_PACKAGE_NAME);

        installPackageIncrementally(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, ALL_CHECKSUMS, TRUST_NONE,
                receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 7, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "759626c33083fbf43215cb5b17156977d963d4c6850c0cb4e73162a665db560b");
        assertEquals(checksums[1].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[1].getValue()), TEST_FIXED_APK_MD5);
        assertEquals(checksums[2].getType(), TYPE_WHOLE_SHA1);
        assertEquals(bytesToHexString(checksums[2].getValue()),
                "331eef6bc57671de28cbd7e32089d047285ade6a");
        assertEquals(checksums[3].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[3].getValue()), TEST_FIXED_APK_SHA256);
        assertEquals(checksums[4].getType(), TYPE_WHOLE_SHA512);
        assertEquals(bytesToHexString(checksums[4].getValue()),
                "b59467fe578ebc81974ab3aaa1e0d2a76fef3e4ea7212a6f2885cec1af5253571"
                        + "1e2e94496224cae3eba8dc992144ade321540ebd458ec5b9e6a4cc51170e018");
        assertEquals(checksums[5].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[5].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertEquals(checksums[6].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA512);
        assertEquals(bytesToHexString(checksums[6].getValue()),
                "ef80a8630283f60108e8557c924307d0ccdfb6bbbf2c0176bd49af342f43bc84"
                        + "5f2888afcb71524196dda0d6dd16a6a3292bb75b431b8ff74fb60d796e882f80");
    }


    @Test
    public void testInstallerChecksumsTrustNone() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installApkWithChecksums(TEST_FIXED_APK_DIGESTS);

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[0].getInstallerPackageName());
        assertNull(checksums[0].getInstallerCertificate());
    }

    @Test
    public void testInstallerWrongChecksumsTrustAll() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installApkWithChecksums(TEST_FIXED_APK_WRONG_DIGESTS);

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_ALL, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[0].getInstallerPackageName());
        assertNull(checksums[0].getInstallerCertificate());
    }

    @Test
    public void testInstallerSignedChecksumsInvalidSignature() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
            params.installFlags |= PackageManager.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK;

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);
            writeFileToSession(session, "file", TEST_FIXED_APK);

            assertThrows(IllegalArgumentException.class, () -> session.setChecksums("file",
                    Arrays.asList(TEST_FIXED_APK_DIGESTS), hexStringToBytes("1eec9e86")));
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private static byte[] createRandomArray(int size) {
        byte[] array = new byte[size];
        (new SecureRandom()).nextBytes(array);
        return array;
    }

    @Test
    public void testInstallerSignedChecksumsSignatureTooBig() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);
            writeFileToSession(session, "file", TEST_FIXED_APK);

            try {
                session.setChecksums("file",
                        Arrays.asList(TEST_FIXED_APK_DIGESTS), createRandomArray(50000));
                Assert.fail("setChecksums should throw exception.");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("Invalid signature");
            }
        } finally {
            dropShellPermissionIdentity();
        }
    }

    @Test
    public void testInstallerSignedChecksumsInvalidChecksum() throws Exception {
        final Checksum[] invalidChecksums = new Checksum[]{new Checksum(
                TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256, hexStringToBytes(
                "6b866e8a54a3e358dfc20007960fb96123845f6c6d6c45f5fddf8"
                        + "8150d71677f4c3081a58921c88651f7376118aca312cf764"
                        + "b391cdfb8a18c6710f9f27916a0a0"))};

        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
            params.installFlags |= PackageManager.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK;

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);
            writeFileToSession(session, "file", TEST_FIXED_APK);
            assertThrows(IllegalArgumentException.class, () -> session.setChecksums("file",
                    Arrays.asList(invalidChecksums), NO_SIGNATURE));
        } finally {
            dropShellPermissionIdentity();
        }
    }

    @Test
    public void testInstallerSignedChecksumsTrustNone() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        final byte[] signature = readSignature();

        CommitIntentReceiver.checkSuccess(
                installApkWithChecksums(TEST_FIXED_APK, "file", "file", TEST_FIXED_APK_DIGESTS,
                        signature));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[0].getInstallerPackageName());
        assertNull(checksums[0].getInstallerCertificate());
    }

    @Test
    public void testInstallerSignedChecksumsTrustAll() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        final byte[] signature = readSignature();
        final Certificate certificate = readCertificate();

        CommitIntentReceiver.checkSuccess(
                installApkWithChecksums(TEST_FIXED_APK, "file", "file", TEST_FIXED_APK_DIGESTS,
                        signature));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_ALL, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        // v2/v3+installer provided.
        assertEquals(checksums.length, 3, Arrays.toString(checksums));

        assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_MD5);
        assertEquals(checksums[0].getSplitName(), null);
        assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[0].getInstallerCertificate(), certificate);
        assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[1].getValue()), TEST_FIXED_APK_SHA256);
        assertEquals(checksums[1].getSplitName(), null);
        assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[1].getInstallerCertificate(), certificate);
        assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[2].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertEquals(checksums[2].getSplitName(), null);
        assertNull(checksums[2].getInstallerPackageName());
        assertNull(checksums[2].getInstallerCertificate());
    }

    @Test
    public void testInstallerChecksumsTrustAll() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installApkWithChecksums(TEST_FIXED_APK_DIGESTS);

        final Certificate installerCertificate = getInstallerCertificate();

        LocalListener receiver = new LocalListener();
        getPackageManager().requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_ALL,
                receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        // v2/v3+installer provided.
        assertEquals(checksums.length, 3, Arrays.toString(checksums));

        assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_MD5);
        assertEquals(checksums[0].getSplitName(), null);
        assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[0].getInstallerCertificate(), installerCertificate);
        assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[1].getValue()), TEST_FIXED_APK_SHA256);
        assertEquals(checksums[1].getSplitName(), null);
        assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[1].getInstallerCertificate(), installerCertificate);
        assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[2].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertEquals(checksums[2].getSplitName(), null);
        assertNull(checksums[2].getInstallerPackageName());
        assertNull(checksums[2].getInstallerCertificate());
    }

    @Test
    public void testInstallerChecksumsTrustInstaller() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installApkWithChecksums(TEST_FIXED_APK_DIGESTS);

        // Using the installer's certificate(s).
        PackageManager pm = getPackageManager();
        PackageInfo packageInfo = pm.getPackageInfo(CTS_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(GET_SIGNING_CERTIFICATES));
        final List<Certificate> signatures = convertSignaturesToCertificates(
                packageInfo.signingInfo.getApkContentsSigners());

        LocalListener receiver = new LocalListener();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, signatures, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 3, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_MD5);
        assertEquals(checksums[0].getSplitName(), null);
        assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[0].getInstallerCertificate(), signatures.get(0));
        assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[1].getValue()), TEST_FIXED_APK_SHA256);
        assertEquals(checksums[1].getSplitName(), null);
        assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[1].getInstallerCertificate(), signatures.get(0));
        assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[2].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertEquals(checksums[2].getSplitName(), null);
        assertNull(checksums[2].getInstallerPackageName());
        assertNull(checksums[2].getInstallerCertificate());
    }

    @LargeTest
    @Test
    public void testInstallerFileChecksumsDuringInstall() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);
        Checksum[] digestsBase = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("dd93e23bb8cdab0382fdca0d21a4f1cb"))};
        Checksum[] digestsSplit0 = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("f6430e1b795ce2658c49e68d15316b2d"))};

        final Certificate installerCertificate = getInstallerCertificate();

        adoptShellPermissionIdentity();
        PackageInstaller installer = null;
        int sessionId = -1;
        try {
            installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;

            sessionId = installer.createSession(params);
            final Session session = installer.openSession(sessionId);

            writeFileToSession(session, "hw5.apk", TEST_V4_APK);
            session.setChecksums("hw5.apk", Arrays.asList(digestsBase), NO_SIGNATURE);

            writeFileToSession(session, "hw5_split0.apk", TEST_V4_SPLIT0);
            session.setChecksums("hw5_split0.apk", Arrays.asList(digestsSplit0), NO_SIGNATURE);

            // Workaround to emulate .digests file present in installation.
            writeChecksumsToSession(session, "hw5.digests", digestsBase);
            writeChecksumsToSession(session, "hw5_split0.digests", digestsSplit0);

            File dataApp = Environment.getDataAppDirectory(null);

            {
                LocalListener receiver = new LocalListener();

                session.requestChecksums("hw5.apk", 0, TRUST_ALL, getContext().getMainExecutor(),
                        receiver);
                ApkChecksum[] checksums = receiver.getResult();
                assertNotNull(checksums);
                assertEquals(checksums.length, 3, Arrays.toString(checksums));
                // base
                assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
                assertEquals(checksums[0].getSplitName(), null);
                assertEquals(bytesToHexString(checksums[0].getValue()),
                        "dd93e23bb8cdab0382fdca0d21a4f1cb");
                assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
                assertEquals(checksums[0].getInstallerCertificate(), installerCertificate);
                assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
                assertEquals(checksums[1].getSplitName(), null);
                assertEquals(bytesToHexString(checksums[1].getValue()),
                        "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e");
                assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
                assertEquals(checksums[1].getInstallerCertificate(), installerCertificate);
                assertEquals(checksums[2].getSplitName(), null);
                assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
                assertNull(checksums[2].getInstallerPackageName());
                assertNull(checksums[2].getInstallerCertificate());
            }
            {
                LocalListener receiver = new LocalListener();

                session.requestChecksums("hw5_split0.apk", 0, TRUST_ALL,
                        getContext().getMainExecutor(), receiver);
                ApkChecksum[] checksums = receiver.getResult();
                assertNotNull(checksums);
                assertEquals(checksums.length, 3, Arrays.toString(checksums));
                // split0
                assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
                assertEquals(checksums[0].getSplitName(), null);
                assertEquals(bytesToHexString(checksums[0].getValue()),
                        "f6430e1b795ce2658c49e68d15316b2d");
                assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
                assertEquals(checksums[0].getInstallerCertificate(), installerCertificate);
                assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
                assertEquals(checksums[1].getSplitName(), null);
                assertEquals(bytesToHexString(checksums[1].getValue()),
                        "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e");
                assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
                assertEquals(checksums[1].getInstallerCertificate(), installerCertificate);
                assertEquals(checksums[2].getSplitName(), null);
                assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
                assertNull(checksums[2].getInstallerPackageName());
                assertNull(checksums[2].getInstallerCertificate());
            }
        } finally {
            installer.abandonSession(sessionId);
            dropShellPermissionIdentity();
        }
    }

    @Test
    public void testInstallerChecksumsTrustWrongInstaller() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installApkWithChecksums(TEST_FIXED_APK_DIGESTS);

        // Using certificates from a security app, not the installer (us).
        PackageManager pm = getPackageManager();
        PackageInfo packageInfo = pm.getPackageInfo(FIXED_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(GET_SIGNING_CERTIFICATES));
        final List<Certificate> signatures = convertSignaturesToCertificates(
                packageInfo.signingInfo.getApkContentsSigners());

        LocalListener receiver = new LocalListener();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, signatures, receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[0].getInstallerPackageName());
        assertNull(checksums[0].getInstallerCertificate());
    }

    @Test
    public void testInstallerChecksumsTrustAllWrongName() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        // NB: "Invalid checksum name(s):" is used in Play to report checksum related failures.
        // Please consult with them before changing.
        CommitIntentReceiver.checkFailure(
                installApkWithChecksums(TEST_FIXED_APK, "apk", "wrong_name",
                        TEST_FIXED_APK_DIGESTS),
                "INSTALL_FAILED_SESSION_INVALID: Invalid checksum name(s): wrong_name");
    }

    @Test
    public void testInstallerChecksumsUpdate() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);

        Checksum[] digestsBase = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("dd93e23bb8cdab0382fdca0d21a4f1cb"))};
        Checksum[] digestsSplit0 = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("f6430e1b795ce2658c49e68d15316b2d"))};
        Checksum[] digestsSplit1 = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "f16898f43990c14585a900eda345c3a236c6224f63920d69cfe8a7afbc0c0ccf")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("d1f4b00d034994663e84f907fe4bb664"))};

        final Certificate installerCertificate = getInstallerCertificate();

        // Original package checksums: base + split0.
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);

            writeFileToSession(session, "hw5", TEST_V4_APK);
            session.setChecksums("hw5", Arrays.asList(digestsBase), NO_SIGNATURE);

            writeFileToSession(session, "hw5_split0", TEST_V4_SPLIT0);
            session.setChecksums("hw5_split0", Arrays.asList(digestsSplit0), NO_SIGNATURE);

            CommitIntentReceiver receiver = new CommitIntentReceiver();
            session.commit(receiver.getIntentSender());
            CommitIntentReceiver.checkSuccess(receiver.getResult());
        } finally {
            dropShellPermissionIdentity();
        }

        {
            LocalListener receiver = new LocalListener();
            PackageManager pm = getPackageManager();
            pm.requestChecksums(V4_PACKAGE_NAME, true, 0, TRUST_ALL, receiver);
            ApkChecksum[] checksums = receiver.getResult();
            assertNotNull(checksums);
            assertEquals(checksums.length, 6, Arrays.toString(checksums));
            // base
            assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[0].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    "dd93e23bb8cdab0382fdca0d21a4f1cb");
            assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[0].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[1].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[1].getValue()),
                    "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e");
            assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[1].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[2].getSplitName(), null);
            assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[2].getInstallerPackageName());
            assertNull(checksums[2].getInstallerCertificate());
            // split0
            assertEquals(checksums[3].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[3].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[3].getValue()),
                    "f6430e1b795ce2658c49e68d15316b2d");
            assertEquals(checksums[3].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[3].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[4].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[4].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[4].getValue()),
                    "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e");
            assertEquals(checksums[4].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[4].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[5].getSplitName(), "config.hdpi");
            assertEquals(checksums[5].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[5].getInstallerPackageName());
            assertNull(checksums[5].getInstallerCertificate());
        }

        // Update the package with one split+checksums and another split without checksums.
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
            params.setAppPackageName(V4_PACKAGE_NAME);

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);

            writeFileToSession(session, "hw5_split1", TEST_V4_SPLIT1);
            session.setChecksums("hw5_split1", Arrays.asList(digestsSplit1), NO_SIGNATURE);

            writeFileToSession(session, "hw5_split2", TEST_V4_SPLIT2);

            CommitIntentReceiver receiver = new CommitIntentReceiver();
            session.commit(receiver.getIntentSender());
            CommitIntentReceiver.checkSuccess(receiver.getResult());
        } finally {
            dropShellPermissionIdentity();
        }

        {
            LocalListener receiver = new LocalListener();
            PackageManager pm = getPackageManager();
            pm.requestChecksums(V4_PACKAGE_NAME, true, 0, TRUST_ALL, receiver);
            ApkChecksum[] checksums = receiver.getResult();
            assertNotNull(checksums);
            assertEquals(checksums.length, 10, Arrays.toString(checksums));
            // base
            assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[0].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    "dd93e23bb8cdab0382fdca0d21a4f1cb");
            assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[0].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[1].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[1].getValue()),
                    "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e");
            assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[1].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[2].getSplitName(), null);
            assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[2].getInstallerPackageName());
            assertNull(checksums[2].getInstallerCertificate());
            // split0
            assertEquals(checksums[3].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[3].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[3].getValue()),
                    "f6430e1b795ce2658c49e68d15316b2d");
            assertEquals(checksums[3].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[3].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[4].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[4].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[4].getValue()),
                    "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e");
            assertEquals(checksums[4].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[4].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[5].getSplitName(), "config.hdpi");
            assertEquals(checksums[5].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[5].getInstallerPackageName());
            assertNull(checksums[5].getInstallerCertificate());
            // split1
            assertEquals(checksums[6].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[6].getSplitName(), "config.mdpi");
            assertEquals(bytesToHexString(checksums[6].getValue()),
                    "d1f4b00d034994663e84f907fe4bb664");
            assertEquals(checksums[6].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[6].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[7].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[7].getSplitName(), "config.mdpi");
            assertEquals(bytesToHexString(checksums[7].getValue()),
                    "f16898f43990c14585a900eda345c3a236c6224f63920d69cfe8a7afbc0c0ccf");
            assertEquals(checksums[7].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[7].getInstallerCertificate(), installerCertificate);
            assertEquals(checksums[8].getSplitName(), "config.mdpi");
            assertEquals(checksums[8].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[8].getInstallerPackageName());
            assertNull(checksums[8].getInstallerCertificate());
            // split2
            assertEquals(checksums[9].getSplitName(), "config.xhdpi");
            assertEquals(checksums[9].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[9].getInstallerPackageName());
            assertNull(checksums[9].getInstallerCertificate());
        }
    }

    @Test
    public void testInstallerSignedChecksumsUpdate() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);
        Checksum[] digestsBase = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("dd93e23bb8cdab0382fdca0d21a4f1cb"))};
        Checksum[] digestsSplit0 = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("f6430e1b795ce2658c49e68d15316b2d"))};
        Checksum[] digestsSplit1 = new Checksum[]{new Checksum(TYPE_WHOLE_SHA256, hexStringToBytes(
                "f16898f43990c14585a900eda345c3a236c6224f63920d69cfe8a7afbc0c0ccf")),
                new Checksum(TYPE_WHOLE_MD5, hexStringToBytes("d1f4b00d034994663e84f907fe4bb664"))};

        String digestBaseFile = ApkChecksums.buildDigestsPathForApk(TEST_V4_APK);
        String digestSplit0File = ApkChecksums.buildDigestsPathForApk(TEST_V4_SPLIT0);
        String digestSplit1File = ApkChecksums.buildDigestsPathForApk(TEST_V4_SPLIT1);

        checkStoredChecksums(digestsBase, digestBaseFile);
        checkStoredChecksums(digestsSplit0, digestSplit0File);
        checkStoredChecksums(digestsSplit1, digestSplit1File);

        byte[] digestBaseSignature = readSignature(
                ApkChecksums.buildSignaturePathForDigests(digestBaseFile));
        byte[] digestSplit0Signature = readSignature(
                ApkChecksums.buildSignaturePathForDigests(digestSplit0File));
        byte[] digestSplit1Signature = readSignature(
                ApkChecksums.buildSignaturePathForDigests(digestSplit1File));

        final Certificate certificate = readCertificate();

        // Original package checksums: base + split0.
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);

            writeFileToSession(session, "hw5", TEST_V4_APK);
            session.setChecksums("hw5", Arrays.asList(digestsBase), digestBaseSignature);

            writeFileToSession(session, "hw5_split0", TEST_V4_SPLIT0);
            session.setChecksums("hw5_split0", Arrays.asList(digestsSplit0), digestSplit0Signature);

            CommitIntentReceiver receiver = new CommitIntentReceiver();
            session.commit(receiver.getIntentSender());
            CommitIntentReceiver.checkSuccess(receiver.getResult());
        } finally {
            dropShellPermissionIdentity();
        }

        {
            LocalListener receiver = new LocalListener();
            PackageManager pm = getPackageManager();
            pm.requestChecksums(V4_PACKAGE_NAME, true, 0, TRUST_ALL, receiver);
            ApkChecksum[] checksums = receiver.getResult();
            assertNotNull(checksums);
            assertEquals(checksums.length, 6, Arrays.toString(checksums));
            // base
            assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[0].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    "dd93e23bb8cdab0382fdca0d21a4f1cb");
            assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[0].getInstallerCertificate(), certificate);
            assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[1].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[1].getValue()),
                    "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e");
            assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[1].getInstallerCertificate(), certificate);
            assertEquals(checksums[2].getSplitName(), null);
            assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[2].getInstallerPackageName());
            assertNull(checksums[2].getInstallerCertificate());
            // split0
            assertEquals(checksums[3].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[3].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[3].getValue()),
                    "f6430e1b795ce2658c49e68d15316b2d");
            assertEquals(checksums[3].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[3].getInstallerCertificate(), certificate);
            assertEquals(checksums[4].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[4].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[4].getValue()),
                    "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e");
            assertEquals(checksums[4].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[4].getInstallerCertificate(), certificate);
            assertEquals(checksums[5].getSplitName(), "config.hdpi");
            assertEquals(checksums[5].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[5].getInstallerPackageName());
            assertNull(checksums[5].getInstallerCertificate());
        }

        // Update the package with one split+checksums and another split without checksums.
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
            params.setAppPackageName(V4_PACKAGE_NAME);

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);

            writeFileToSession(session, "hw5_split1", TEST_V4_SPLIT1);
            session.setChecksums("hw5_split1", Arrays.asList(digestsSplit1), digestSplit1Signature);

            writeFileToSession(session, "hw5_split2", TEST_V4_SPLIT2);

            CommitIntentReceiver receiver = new CommitIntentReceiver();
            session.commit(receiver.getIntentSender());
            CommitIntentReceiver.checkSuccess(receiver.getResult());
        } finally {
            dropShellPermissionIdentity();
        }

        {
            LocalListener receiver = new LocalListener();
            PackageManager pm = getPackageManager();
            pm.requestChecksums(V4_PACKAGE_NAME, true, 0, TRUST_ALL, receiver);
            ApkChecksum[] checksums = receiver.getResult();
            assertNotNull(checksums);
            assertEquals(checksums.length, 10, Arrays.toString(checksums));
            // base
            assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[0].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[0].getValue()),
                    "dd93e23bb8cdab0382fdca0d21a4f1cb");
            assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[0].getInstallerCertificate(), certificate);
            assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[1].getSplitName(), null);
            assertEquals(bytesToHexString(checksums[1].getValue()),
                    "ed8c7ae1220fe16d558e00cfc37256e6f7088ab90eb04c1bfcb39922a8a5248e");
            assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[1].getInstallerCertificate(), certificate);
            assertEquals(checksums[2].getSplitName(), null);
            assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[2].getInstallerPackageName());
            assertNull(checksums[2].getInstallerCertificate());
            // split0
            assertEquals(checksums[3].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[3].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[3].getValue()),
                    "f6430e1b795ce2658c49e68d15316b2d");
            assertEquals(checksums[3].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[3].getInstallerCertificate(), certificate);
            assertEquals(checksums[4].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[4].getSplitName(), "config.hdpi");
            assertEquals(bytesToHexString(checksums[4].getValue()),
                    "bd9b095a49a9068498b018ce8cb7cc18d411b13a5a5f7fb417d2ff9808ae838e");
            assertEquals(checksums[4].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[4].getInstallerCertificate(), certificate);
            assertEquals(checksums[5].getSplitName(), "config.hdpi");
            assertEquals(checksums[5].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[5].getInstallerPackageName());
            assertNull(checksums[5].getInstallerCertificate());
            // split1
            assertEquals(checksums[6].getType(), TYPE_WHOLE_MD5);
            assertEquals(checksums[6].getSplitName(), "config.mdpi");
            assertEquals(bytesToHexString(checksums[6].getValue()),
                    "d1f4b00d034994663e84f907fe4bb664");
            assertEquals(checksums[6].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[6].getInstallerCertificate(), certificate);
            assertEquals(checksums[7].getType(), TYPE_WHOLE_SHA256);
            assertEquals(checksums[7].getSplitName(), "config.mdpi");
            assertEquals(bytesToHexString(checksums[7].getValue()),
                    "f16898f43990c14585a900eda345c3a236c6224f63920d69cfe8a7afbc0c0ccf");
            assertEquals(checksums[7].getInstallerPackageName(), CTS_PACKAGE_NAME);
            assertEquals(checksums[7].getInstallerCertificate(), certificate);
            assertEquals(checksums[8].getSplitName(), "config.mdpi");
            assertEquals(checksums[8].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[8].getInstallerPackageName());
            assertNull(checksums[8].getInstallerCertificate());
            // split2
            assertEquals(checksums[9].getSplitName(), "config.xhdpi");
            assertEquals(checksums[9].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
            assertNull(checksums[9].getInstallerPackageName());
            assertNull(checksums[9].getInstallerCertificate());
        }
    }

    @Test
    public void testInstallerChecksumsIncremental() throws Exception {
        assumeTrue(checkIncrementalDeliveryFeature());

        final Certificate installerCertificate = getInstallerCertificate();

        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackageIncrementally(TEST_FIXED_APK);

        PackageManager pm = getPackageManager();
        PackageInfo packageInfo = pm.getPackageInfo(FIXED_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0));
        final String inPath = packageInfo.applicationInfo.getBaseCodePath();

        installApkWithChecksumsIncrementally(inPath);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_ALL,
                receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 3, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_MD5);
        assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[0].getInstallerCertificate(), installerCertificate);
        assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[1].getValue()), TEST_FIXED_APK_SHA256);
        assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[1].getInstallerCertificate(), installerCertificate);
        assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[2].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[2].getInstallerPackageName());
        assertNull(checksums[2].getInstallerCertificate());
    }

    @Test
    public void testInstallerSignedChecksumsIncremental() throws Exception {
        assumeTrue(checkIncrementalDeliveryFeature());

        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackageIncrementally(TEST_FIXED_APK);

        PackageInfo packageInfo = getPackageManager().getPackageInfo(FIXED_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0));
        final String inPath = packageInfo.applicationInfo.getBaseCodePath();

        final byte[] signature = readSignature();
        final Certificate certificate = readCertificate();

        installApkWithChecksumsIncrementally(inPath, TEST_FIXED_APK, TEST_FIXED_APK_DIGESTS,
                signature);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_ALL,
                receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 3, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_MD5);
        assertEquals(checksums[0].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[0].getInstallerCertificate(), certificate);
        assertEquals(checksums[1].getType(), TYPE_WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[1].getValue()), TEST_FIXED_APK_SHA256);
        assertEquals(checksums[1].getInstallerPackageName(), CTS_PACKAGE_NAME);
        assertEquals(checksums[1].getInstallerCertificate(), certificate);
        assertEquals(checksums[2].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[2].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[2].getInstallerPackageName());
        assertNull(checksums[2].getInstallerCertificate());
    }

    @Test
    public void testInstallerChecksumsIncrementalTrustNone() throws Exception {
        assumeTrue(checkIncrementalDeliveryFeature());

        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        installPackageIncrementally(TEST_FIXED_APK);

        PackageInfo packageInfo = getPackageManager().getPackageInfo(FIXED_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(0));
        final String inPath = packageInfo.applicationInfo.getBaseCodePath();

        installApkWithChecksumsIncrementally(inPath);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalListener receiver = new LocalListener();
        PackageManager pm = getPackageManager();
        pm.requestChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE,
                receiver);
        ApkChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1, Arrays.toString(checksums));
        assertEquals(checksums[0].getType(), TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()), TEST_FIXED_APK_V2_SHA256);
        assertNull(checksums[0].getInstallerPackageName());
        assertNull(checksums[0].getInstallerCertificate());
    }

    @Test
    public void testInstallerChecksumsDuplicate() throws Exception {
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);
            writeFileToSession(session, "file", TEST_FIXED_APK);
            session.setChecksums("file", Arrays.asList(TEST_FIXED_APK_DIGESTS), NO_SIGNATURE);
            try {
                session.setChecksums("file", Arrays.asList(TEST_FIXED_APK_DIGESTS), NO_SIGNATURE);
                Assert.fail("setChecksums should throw exception.");
            } catch (IllegalStateException e) {
                // expected
            }
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private List<Certificate> convertSignaturesToCertificates(Signature[] signatures) {
        try {
            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ArrayList<Certificate> certs = new ArrayList<>(signatures.length);
            for (Signature signature : signatures) {
                final InputStream is = new ByteArrayInputStream(signature.toByteArray());
                final X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                certs.add(cert);
            }
            return certs;
        } catch (CertificateException e) {
            throw ExceptionUtils.propagate(e);
        }
    }

    private void installApkWithChecksums(Checksum[] checksums) throws Exception {
        installApkWithChecksums("file", "file", checksums);
    }

    private void installApkWithChecksums(String apkName, String checksumsName, Checksum[] checksums)
            throws Exception {
        CommitIntentReceiver.checkSuccess(
                installApkWithChecksums(TEST_FIXED_APK, apkName, checksumsName, checksums));
    }

    private Intent installApkWithChecksums(String apk, String apkName,
            String checksumsName, Checksum[] checksums) throws Exception {
        return installApkWithChecksums(apk, apkName, checksumsName, checksums, NO_SIGNATURE);
    }

    private Intent installApkWithChecksums(String apk, String apkName,
            String checksumsName, Checksum[] checksums, byte[] signature) throws Exception {
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.installFlags |= PackageManager.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK;

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);
            writeFileToSession(session, apkName, apk);
            session.setChecksums(checksumsName, Arrays.asList(checksums), signature);

            CommitIntentReceiver receiver = new CommitIntentReceiver();
            session.commit(receiver.getIntentSender());
            return receiver.getResult();
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private void installApkWithFSVerity(String apk, String fsv) throws Exception {
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.installFlags |= PackageManager.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK;

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);
            writeFileToSession(session, "file", apk);
            writeFileToSession(session, "file.fsv_sig", fsv);

            CommitIntentReceiver receiver = new CommitIntentReceiver();
            session.commit(receiver.getIntentSender());
            CommitIntentReceiver.checkSuccess(receiver.getResult());
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private void installApkWithChecksumsIncrementally(final String inPath) throws Exception {
        installApkWithChecksumsIncrementally(inPath, TEST_FIXED_APK, TEST_FIXED_APK_DIGESTS,
                NO_SIGNATURE);
    }

    private void installApkWithChecksumsIncrementally(final String inPath, final String apk,
            final Checksum[] checksums, final byte[] signature) throws Exception {
        adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
            params.installFlags |= PackageManager.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK;
            params.setDataLoaderParams(DataLoaderParams.forIncremental(new ComponentName("android",
                    PackageManagerShellCommandDataLoader.class.getName()), ""));

            final int sessionId = installer.createSession(params);
            Session session = installer.openSession(sessionId);

            final File file = new File(inPath);
            final String name = file.getName();
            final long size = file.length();
            final Metadata metadata = Metadata.forLocalFile(inPath);

            session.addFile(LOCATION_DATA_APP, name, size, metadata.toByteArray(), null);
            session.setChecksums(name, Arrays.asList(checksums), signature);

            CommitIntentReceiver receiver = new CommitIntentReceiver();
            session.commit(receiver.getIntentSender());
            CommitIntentReceiver.checkSuccess(receiver.getResult());
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, -1);
        return result.toString("UTF-8");
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            Assert.assertEquals(expected, total);
        }
    }

    private static String executeShellCommand(String command) throws IOException {
        final ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(command);
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private String executeShellCommand(String command, byte[] input) throws IOException {
        final ParcelFileDescriptor[] pfds =
                getUiAutomation().executeShellCommandRw(command);
        ParcelFileDescriptor stdout = pfds[0];
        ParcelFileDescriptor stdin = pfds[1];
        try (FileOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                stdin)) {
            outputStream.write(input);
        }
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return new String(FileUtils.readInputStreamFully(inputStream));
        }
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    private void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        Assert.assertEquals("Success\n", executeShellCommand(
                "pm install -t -g --bypass-low-target-sdk-block " + file.getPath()));
    }

    private void installPackageIncrementally(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        Assert.assertEquals("Success\n", executeShellCommand(
                "pm install-incremental -t -g --bypass-low-target-sdk-block " + file.getPath()));
    }

    private void installSplits(String[] baseNames) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        Assert.assertEquals("Success\n",
                executeShellCommand(
                        "pm install -t -g --bypass-low-target-sdk-block " + String.join(" ",
                                splits)));
    }

    private void installFilesIncrementally(String[] baseNames) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        Assert.assertEquals("Success\n",
                executeShellCommand(
                        "pm install-incremental -t -g --bypass-low-target-sdk-block " + String.join(
                                " ", splits)));
    }

    private static void writeFileToSession(PackageInstaller.Session session, String name,
            String apk) throws IOException {
        File file = new File(createApkPath(apk));
        try (OutputStream os = session.openWrite(name, 0, file.length());
             InputStream is = new FileInputStream(file)) {
            writeFullStream(is, os, file.length());
        }
    }

    private static void writeChecksumsToSession(PackageInstaller.Session session, String name,
            Checksum[] checksums) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(session.openWrite(name, 0, -1))) {
            for (Checksum checksum : checksums) {
                Checksum.writeToStream(dos, checksum);
            }
        }
    }

    private static String uninstallPackageSilently(String packageName) throws IOException {
        return executeShellCommand("pm uninstall " + packageName);
    }

    private static boolean isAppInstalled(String packageName) throws IOException {
        return isPackagePresent(packageName, /*matchAllPackages=*/false);
    }

    private static boolean isPackagePresent(String packageName) throws IOException {
        return isPackagePresent(packageName, /*matchAllPackages=*/true);
    }

    private static boolean isPackagePresent(String packageName, boolean matchAllPackages)
            throws IOException {
        final String commandResult =
                executeShellCommand("pm list packages" + (matchAllPackages ? " -a" : ""));
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.substring(prefixLength).equals(packageName));
    }

    private String getAppCodePath(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm dump " + packageName);
        final String prefix = "    codePath=";
        final int prefixLength = prefix.length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefixLength))
                .findFirst().get();
    }

    @Nonnull
    private static String bytesToHexString(byte[] bytes) {
        return HexDump.toHexString(bytes, 0, bytes.length, /*upperCase=*/ false);
    }

    @Nonnull
    private static byte[] hexStringToBytes(String hexString) {
        return HexDump.hexStringToByteArray(hexString);
    }

    private boolean checkIncrementalDeliveryFeature() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_INCREMENTAL_DELIVERY);
    }

    // From PackageManagerServiceUtils.
    private static boolean isApkVerityEnabled() {
        return Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.R
                || SystemProperties.getInt("ro.apk_verity.mode", FSVERITY_DISABLED)
                == FSVERITY_ENABLED;
    }

    private byte[] readSignature() throws IOException {
        return readSignature(TEST_FIXED_APK_DIGESTS_SIGNATURE);
    }

    private byte[] readSignature(String file) throws IOException {
        return Files.readAllBytes(Paths.get(createApkPath(file)));
    }

    private Certificate readCertificate() throws Exception {
        try (InputStream is = new FileInputStream(createApkPath(TEST_CERTIFICATE))) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return certFactory.generateCertificate(is);
        }
    }

    private Certificate getInstallerCertificate() throws Exception {
        PackageManager pm = getPackageManager();
        PackageInfo installerPackageInfo = pm.getPackageInfo(CTS_PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(GET_SIGNING_CERTIFICATES));
        final List<Certificate> signatures = convertSignaturesToCertificates(
                installerPackageInfo.signingInfo.getApkContentsSigners());
        return signatures.get(0);
    }

    private void checkStoredChecksums(Checksum[] checksums, String fileName) throws Exception {
        ArrayList<Checksum> storedChecksumsList = new ArrayList<>();
        try (InputStream is = new FileInputStream(createApkPath(fileName));
             DataInputStream dis = new DataInputStream(is)) {
            for (int i = 0; i < 100; ++i) {
                try {
                    storedChecksumsList.add(Checksum.readFromStream(dis));
                } catch (EOFException e) {
                    break;
                }
            }
        }
        final Checksum[] storedChecksums = storedChecksumsList.toArray(
                new Checksum[storedChecksumsList.size()]);

        final String message = fileName + " needs to be updated: ";
        Assert.assertEquals(message, storedChecksums.length, checksums.length);
        for (int i = 0, size = storedChecksums.length; i < size; ++i) {
            Assert.assertEquals(message, storedChecksums[i].getType(), checksums[i].getType());
            Assert.assertArrayEquals(message, storedChecksums[i].getValue(),
                    checksums[i].getValue());
        }
    }

    private void adoptShellPermissionIdentity() {
        // For non-shell/non-root/non-system uids, USE_SYSTEM_DATA_LOADERS means
        // "install-related test adapted shell identity".
        getUiAutomation().adoptShellPermissionIdentity(INSTALL_PACKAGES, USE_SYSTEM_DATA_LOADERS,
                USE_INSTALLER_V2);
    }

    private void dropShellPermissionIdentity() {
        getUiAutomation().dropShellPermissionIdentity();
    }

    private static class LocalListener implements PackageManager.OnChecksumsReadyListener {
        private final LinkedBlockingQueue<ApkChecksum[]> mResult = new LinkedBlockingQueue<>();

        @Override
        public void onChecksumsReady(@NonNull List<ApkChecksum> checksumsList) {
            if (checksumsList == null) {
                mResult.offer(null);
                return;
            }
            ApkChecksum[] checksums = checksumsList.toArray(new ApkChecksum[checksumsList.size()]);
            Arrays.sort(checksums, (ApkChecksum lhs, ApkChecksum rhs) ->  {
                final String lhsSplit = lhs.getSplitName();
                final String rhsSplit = rhs.getSplitName();
                if (Objects.equals(lhsSplit, rhsSplit)) {
                    return Integer.signum(lhs.getType() - rhs.getType());
                }
                if (lhsSplit == null) {
                    return -1;
                }
                if (rhsSplit == null) {
                    return +1;
                }
                return lhsSplit.compareTo(rhsSplit);
            });
            mResult.offer(checksums);
        }

        public ApkChecksum[] getResult() {
            try {
                return mResult.poll(6, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class CommitIntentReceiver {
        private final LinkedBlockingQueue<Intent> mResult = new LinkedBlockingQueue<>();

        private IIntentSender.Stub mLocalSender = new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType, IBinder allowlistToken,
                    IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) {
                try {
                    mResult.offer(intent, 5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        public IntentSender getIntentSender() {
            return new IntentSender((IIntentSender) mLocalSender);
        }

        public Intent getResult() {
            try {
                return mResult.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public static void checkSuccess(Intent result) {
            final int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            assertEquals(status, PackageInstaller.STATUS_SUCCESS,
                    result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) + " OR "
                            + result.getExtras().get(Intent.EXTRA_INTENT));
        }

        public static void checkFailure(Intent result, int expectedStatus,
                String expectedStatusMessage) {
            final int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            assertEquals(status, expectedStatus);
            assertEquals(result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                    expectedStatusMessage);
        }

        public static void checkFailure(Intent result, String expectedStatusMessage) {
            checkFailure(result, PackageInstaller.STATUS_FAILURE, expectedStatusMessage);
        }
    }
}
