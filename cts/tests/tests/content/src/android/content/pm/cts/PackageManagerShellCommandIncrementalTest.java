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

package android.content.pm.cts;

import static android.Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.cts.util.AbandonAllPackageSessionsRule;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.provider.DeviceConfig;
import android.service.dataloader.DataLoaderService;
import android.system.Os;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.incfs.install.IBlockFilter;
import com.android.incfs.install.IBlockTransformer;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.PendingBlock;

import com.google.common.truth.Truth;

import libcore.io.IoUtils;

import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(AndroidJUnit4.class)
@AppModeFull
@AppModeNonSdkSandbox
@LargeTest
@Presubmit
public class PackageManagerShellCommandIncrementalTest {
    private static final String TAG = "PackageManagerShellCommandIncrementalTest";

    private static final String CTS_PACKAGE_NAME = "android.content.cts";
    private static final String TEST_APP_PACKAGE = "com.example.helloworld";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_APK = "HelloWorld5.apk";
    private static final String TEST_APK_IDSIG = "HelloWorld5.apk.idsig";
    private static final String TEST_APK_PROFILEABLE = "HelloWorld5Profileable.apk";
    private static final String TEST_APK_SYSTEM = "HelloWorldSystem.apk";
    private static final String TEST_APK_SPLIT0 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_APK_SPLIT0_IDSIG = "HelloWorld5_mdpi-v4.apk.idsig";
    private static final String TEST_APK_SPLIT1 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_APK_SPLIT1_IDSIG = "HelloWorld5_hdpi-v4.apk.idsig";
    private static final String TEST_APK_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";
    private static final String TEST_APK_SPLIT2_IDSIG = "HelloWorld5_xhdpi-v4.apk.idsig";
    private static final String TEST_APK_MALFORMED = "malformed.apk";

    private static final String TEST_HW7 = "HelloWorld7.apk";
    private static final String TEST_HW7_IDSIG = "HelloWorld7.apk.idsig";
    private static final String TEST_HW7_SPLIT0 = "HelloWorld7_hdpi-v4.apk";
    private static final String TEST_HW7_SPLIT0_IDSIG = "HelloWorld7_hdpi-v4.apk.idsig";
    private static final String TEST_HW7_SPLIT1 = "HelloWorld7_mdpi-v4.apk";
    private static final String TEST_HW7_SPLIT1_IDSIG = "HelloWorld7_mdpi-v4.apk.idsig";
    private static final String TEST_HW7_SPLIT2 = "HelloWorld7_xhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT2_IDSIG = "HelloWorld7_xhdpi-v4.apk.idsig";
    private static final String TEST_HW7_SPLIT3 = "HelloWorld7_xxhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT3_IDSIG = "HelloWorld7_xxhdpi-v4.apk.idsig";
    private static final String TEST_HW7_SPLIT4 = "HelloWorld7_xxxhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT4_IDSIG = "HelloWorld7_xxxhdpi-v4.apk.idsig";

    private static final boolean CHECK_BASE_APK_DIGESTION = false;

    private static final long EXPECTED_READ_TIME = 1000L;

    @Rule
    public AbandonAllPackageSessionsRule mAbandonSessionsRule = new AbandonAllPackageSessionsRule();

    private IncrementalInstallSession mSession = null;

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    @Before
    public void onBefore() throws Exception {
        checkIncrementalDeliveryFeature();
        uninstallPackageSilently(TEST_APP_PACKAGE);
    }

    @After
    public void onAfter() throws Exception {
        uninstallPackageSilently(TEST_APP_PACKAGE);
        setDeviceProperty("incfs_default_timeouts", null);
        setDeviceProperty("known_digesters_list", null);
        setSystemProperty("debug.incremental.enforce_readlogs_max_interval_for_system_dataloaders",
                "0");
        setSystemProperty("debug.incremental.readlogs_max_interval_sec", "10000");
        setSystemProperty("debug.incremental.always_enable_read_timeouts_for_system_dataloaders",
                "1");
        IoUtils.closeQuietly(mSession);
        mSession = null;
    }

    static void checkIncrementalDeliveryFeature() {
        Assume.assumeTrue(getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY));
    }

    private static void checkIncrementalDeliveryV2Feature() throws Exception {
        checkIncrementalDeliveryFeature();
        Assume.assumeTrue(getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY, 2));
    }

    @Test
    public void testAndroid12RequiresIncFsV2() throws Exception {
        // IncFS is a kernel feature, which is a subject to vendor freeze. That's why
        // the test verifies the vendor API level here, not the system's one.
        // Note: vendor API level getter returns either the frozen API level, or the current one for
        //  non-vendor-freeze devices; need to verify both the system first API level and vendor
        //  level to make the final decision.
        final boolean v2ReqdForSystem = PropertyUtil.getFirstApiLevel() > 30;
        final boolean v2ReqdForVendor = PropertyUtil.isVendorApiLevelNewerThan(30);
        final boolean v2Required = v2ReqdForSystem && v2ReqdForVendor;
        if (v2Required) {
            Assert.assertTrue("Devices launched at API 31+ with a vendor partition of API 31+ need "
                    + "to support Incremental Delivery version 2 or higher",
                    getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_INCREMENTAL_DELIVERY, 2));
        }
    }

    @Test
    public void testInstallWithIdSig() throws Exception {
        installPackage(TEST_APK);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testBug183952694Fixed() throws Exception {
        // first ensure the IncFS is up and running, e.g. if it's a module
        installPackage(TEST_APK);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        // the bug is fixed in the v2 version, or when the specific marker feature is present
        final String[] validValues = {"v2", "mounter_context_for_backing_rw"};
        final String features = executeShellCommand("ls /sys/fs/incremental-fs/features/");
        assertTrue(
                "Missing all of required IncFS features [" + TextUtils.join(",", validValues) + "]",
                Arrays.stream(features.split("\\s+")).anyMatch(
                        f -> Arrays.stream(validValues).anyMatch(f::equals)));
    }

    @Test
    public void testBug270117845Fixed() throws Exception {
        // first ensure the IncFS is up and running, e.g. if it's a module
        installPackage(TEST_APK);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        // the bug is fixed when the specific marker feature is present
        final String[] validValues = {"bugfix_inode_eviction"};
        final String features = executeShellCommand("ls /sys/fs/incremental-fs/features/");
        assertTrue(
                "Missing required IncFS features [" + TextUtils.join(",", validValues) + "]",
                Arrays.stream(features.split("\\s+")).anyMatch(
                        f -> Arrays.stream(validValues).anyMatch(f::equals)));
    }

    @LargeTest
    @Test
    @FlakyTest
    public void testSpaceAllocatedForPackage() throws Exception {
        final String apk = createApkPath(TEST_APK);
        final String idsig = createApkPath(TEST_APK_IDSIG);
        final long appFileSize = new File(apk).length();
        final AtomicBoolean firstTime = new AtomicBoolean(true);

        getUiAutomation().adoptShellPermissionIdentity();

        final long blockSize = Os.statvfs("/data/incremental").f_bsize;
        final long preAllocatedBlocks = Os.statvfs("/data/incremental").f_bfree;

        final AtomicLong freeSpaceDifference = new AtomicLong(-1L);

        mSession =
                new IncrementalInstallSession.Builder()
                        .addApk(Paths.get(apk), Paths.get(idsig))
                        .addExtraArgs("-t", "-i", CTS_PACKAGE_NAME)
                        .setLogger(new IncrementalDeviceConnection.Logger())
                        .setBlockFilter((block -> {
                            // Skip allocation check after first iteration.
                            if (!firstTime.getAndSet(false)) {
                                return true;
                            }

                            try {
                                final long postAllocatedBlocks =
                                        Os.statvfs("/data/incremental").f_bfree;
                                freeSpaceDifference.set(
                                        (preAllocatedBlocks - postAllocatedBlocks) * blockSize);
                            } catch (Exception e) {
                                Log.i(TAG, "ErrnoException: ", e);
                                throw new AssertionError(e);
                            }
                            return true;
                        }))
                        .setBlockTransformer(new CompressingBlockTransformer())
                        .build();

        try {
            mSession.start(Executors.newSingleThreadExecutor(),
                    IncrementalDeviceConnection.Factory.reliable());
            mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }

        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        final double freeSpaceExpectedDifference = ((appFileSize * 1.015) + blockSize * 8);
        assertTrue(freeSpaceDifference.get() + " >= " + freeSpaceExpectedDifference,
                freeSpaceDifference.get() >= freeSpaceExpectedDifference);

        String installPath = executeShellCommand(String.format("pm path %s", TEST_APP_PACKAGE))
                                        .replaceFirst("package:", "")
                                        .trim();

        // Retrieve size of APK.
        Long apkTrimResult = Os.stat(installPath).st_size;

        // Verify trim was applied. v2+ incfs version required for valid allocation results.
        if (getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY, 2)) {
            assertTrue(apkTrimResult <= appFileSize);
        }
    }

    @Test
    public void testSplitInstallWithIdSig() throws Exception {
        // First fully install the apk.
        {
            installPackage(TEST_APK);
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        }

        installSplit(TEST_APK_SPLIT0);
        assertEquals("base, config.mdpi", getSplits(TEST_APP_PACKAGE));

        installSplit(TEST_APK_SPLIT1);
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSystemInstallWithIdSig() throws Exception {
        final String baseName = TEST_APK_SYSTEM;
        final File file = new File(createApkPath(baseName));
        assertThat(executeShellCommand("pm install-incremental -t -g " + file.getPath()))
                .startsWith("Failure [INSTALL_FAILED_SESSION_INVALID");
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigAndSplit() throws Exception {
        File apkfile = new File(createApkPath(TEST_APK));
        File splitfile = new File(createApkPath(TEST_APK_SPLIT0));
        File[] files = new File[]{apkfile, splitfile};
        String param = Arrays.stream(files).map(
                file -> file.getName() + ":" + file.length()).collect(Collectors.joining(" "));
        assertEquals("Success\n", executeShellCommand(
                String.format("pm install-incremental -t -g -S %s %s",
                        (apkfile.length() + splitfile.length()), param),
                files));
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.mdpi", getSplits(TEST_APP_PACKAGE));
    }

    @LargeTest
    @Test
    public void testInstallWithStreaming() throws Exception {
        final String apk = createApkPath(TEST_APK);
        final String idsig = createApkPath(TEST_APK_IDSIG);
        mSession =
                new IncrementalInstallSession.Builder()
                        .addApk(Paths.get(apk), Paths.get(idsig))
                        .addExtraArgs("-t", "-i", CTS_PACKAGE_NAME)
                        .setLogger(new IncrementalDeviceConnection.Logger())
                        .build();
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            mSession.start(Executors.newSingleThreadExecutor(),
                    IncrementalDeviceConnection.Factory.reliable());
            mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @LargeTest
    @Test
    @FlakyTest // This test is flaky by design
    public void testInstallWithMissingBlocks() throws Exception {
        setDeviceProperty("incfs_default_timeouts", "0:0:0");
        setDeviceProperty("known_digesters_list", CTS_PACKAGE_NAME);
        setSystemProperty("debug.incremental.always_enable_read_timeouts_for_system_dataloaders",
                "0");

        final long randomSeed = System.currentTimeMillis();
        Log.i(TAG, "Randomizing missing blocks with seed: " + randomSeed);
        final Random random = new Random(randomSeed);

        // TODO: add detection of orphaned IncFS instances after failed installations

        final int blockSize = 4096;
        final int retries = 7; // 7 * 3s + leeway ~= 30secs of test timeout

        final File apk = new File(createApkPath(TEST_APK));
        final int blocks = (int) (apk.length() / blockSize);

        for (int i = 0; i < retries; ++i) {
            final int skipBlock = random.nextInt(blocks);
            Log.i(TAG, "skipBlock: " + skipBlock + " out of " + blocks);
            try {
                installWithBlockFilter((block -> block.getType() == PendingBlock.Type.SIGNATURE_TREE
                        || block.getBlockIndex() != skipBlock));
                if (isAppInstalled(TEST_APP_PACKAGE)) {
                    uninstallPackageSilently(TEST_APP_PACKAGE);
                }
            } catch (RuntimeException re) {
                Log.i(TAG, "RuntimeException: ", re);
                assertTrue(re.toString(), re.getCause() instanceof IOException);
            } catch (IOException e) {
                Log.i(TAG, "IOException: ", e);
                throw new IOException("Skipped block: " + skipBlock + ", randomSeed: " + randomSeed,
                        e);
            }
        }
    }

    public void installWithBlockFilter(IBlockFilter blockFilter) throws Exception {
        final String apk = createApkPath(TEST_APK);
        final String idsig = createApkPath(TEST_APK_IDSIG);
        mSession =
                new IncrementalInstallSession.Builder()
                        .addApk(Paths.get(apk), Paths.get(idsig))
                        .addExtraArgs("-t", "-i", CTS_PACKAGE_NAME)
                        .setLogger(new IncrementalDeviceConnection.Logger())
                        .setBlockFilter(blockFilter)
                        .build();
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            mSession.start(Executors.newSingleThreadExecutor(),
                    IncrementalDeviceConnection.Factory.reliableExpectInstallationFailure());
            mSession.waitForAnyCompletion(30, TimeUnit.SECONDS);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Compress the data if the compressed size is < original size, otherwise return the original
     * data.
     */
    private static ByteBuffer maybeCompressPage(ByteBuffer pageData) {
        pageData.mark();
        ByteArrayOutputStream compressedByteStream = new ByteArrayOutputStream();
        try (BlockLZ4CompressorOutputStream compressor =
                     new BlockLZ4CompressorOutputStream(compressedByteStream)) {
            Channels.newChannel(compressor).write(pageData);
            // This is required to make sure the bytes are written to the output
            compressor.finish();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        } finally {
            pageData.reset();
        }

        byte[] compressedBytes = compressedByteStream.toByteArray();
        if (compressedBytes.length < pageData.remaining()) {
            return ByteBuffer.wrap(compressedBytes);
        }
        return pageData;
    }

    static final class CompressedPendingBlock extends PendingBlock {
        final ByteBuffer mPageData;

        CompressedPendingBlock(PendingBlock block) throws IOException {
            super(block);

            final ByteBuffer buffer = ByteBuffer.allocate(super.getBlockSize());
            super.readBlockData(buffer);
            buffer.flip(); // switch to read mode

            if (super.getType() == Type.APK_DATA) {
                mPageData = maybeCompressPage(buffer);
            } else {
                mPageData = buffer;
            }
        }

        public Compression getCompression() {
            return this.getBlockSize() < super.getBlockSize() ? Compression.LZ4 : Compression.NONE;
        }

        public short getBlockSize() {
            return (short) mPageData.remaining();
        }

        public void readBlockData(ByteBuffer buffer) throws IOException {
            mPageData.mark();
            buffer.put(mPageData);
            mPageData.reset();
        }
    }

    static final class CompressingBlockTransformer implements IBlockTransformer {
        @Override
        @NonNull
        public PendingBlock transform(@NonNull PendingBlock block) throws IOException {
            return new CompressedPendingBlock(block);
        }
    }

    @LargeTest
    @Test
    public void testInstallWithStreamingAndCompression() throws Exception {
        final String apk = createApkPath(TEST_APK);
        final String idsig = createApkPath(TEST_APK_IDSIG);
        mSession =
                new IncrementalInstallSession.Builder()
                        .addApk(Paths.get(apk), Paths.get(idsig))
                        .addExtraArgs("-t", "-i", CTS_PACKAGE_NAME)
                        .setLogger(new IncrementalDeviceConnection.Logger())
                        .setBlockTransformer(new CompressingBlockTransformer())
                        .build();
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            mSession.start(Executors.newSingleThreadExecutor(),
                    IncrementalDeviceConnection.Factory.reliable());
            mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @LargeTest
    @Test
    public void testInstallWithStreamingUnreliableConnection() throws Exception {
        final String apk = createApkPath(TEST_APK);
        final String idsig = createApkPath(TEST_APK_IDSIG);
        mSession =
                new IncrementalInstallSession.Builder()
                        .addApk(Paths.get(apk), Paths.get(idsig))
                        .addExtraArgs("-t", "-i", CTS_PACKAGE_NAME)
                        .setLogger(new IncrementalDeviceConnection.Logger())
                        .build();
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            mSession.start(Executors.newSingleThreadExecutor(),
                    IncrementalDeviceConnection.Factory.ureliable());
            mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Ignore, we are looking for crashes anyway.
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testInstallWithIdSigInvalidLength() throws Exception {
        File file = new File(createApkPath(TEST_APK));
        Truth.assertThat(
                executeShellCommand("pm install-incremental -t -g -S " + (file.length() - 1),
                        new File[]{file})).contains(
                        "Failure");
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testInstallWithInvalidIdSig() throws Exception {
        File file = new File(createApkPath(TEST_APK_MALFORMED));
        Truth.assertThat(
                executeShellCommand("pm install-incremental -t -g " + file.getPath())).contains(
                "Failure");
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigStreamIncompleteData() throws Exception {
        File file = new File(createApkPath(TEST_APK));
        long length = file.length();
        // Streaming happens in blocks of 1024 bytes, new length will not stream the last block.
        long newLength = length - (length % 1024 == 0 ? 1024 : length % 1024);
        Truth.assertThat(
                executeShellCommand(
                        "pm install-incremental -t -g -S " + length,
                        new File[]{file},
                        new long[]{newLength})).contains("Failure");
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigNoMissingPages() throws Exception {
        final int installIterations = 1;
        final int atraceDumpIterations = 3;
        final int atraceDumpDelayMs = 1000;
        final String missingPageReads = "|missing_page_reads: count=";

        final ArrayList<String> missingPages = new ArrayList<>();

        checkSysTrace(
                installIterations,
                atraceDumpIterations,
                atraceDumpDelayMs,
                () -> {
                    // Install multiple splits so that digesters won't kick in.
                    installPackage(TEST_APK);
                    installSplit(TEST_APK_SPLIT0);
                    installSplit(TEST_APK_SPLIT1);
                    installSplit(TEST_APK_SPLIT2);
                    // Now read it as fast as we can.
                    readSplitInChunks("base.apk");
                    readSplitInChunks("split_config.mdpi.apk");
                    readSplitInChunks("split_config.hdpi.apk");
                    readSplitInChunks("split_config.xhdpi.apk");
                    return null;
                },
                (stdout) -> {
                    try (Scanner scanner = new Scanner(stdout)) {
                        ReadLogEntry prevLogEntry = null;
                        while (scanner.hasNextLine()) {
                            final String line = scanner.nextLine();

                            final ReadLogEntry readLogEntry = ReadLogEntry.parse(line);
                            if (readLogEntry != null) {
                                prevLogEntry = readLogEntry;
                                continue;
                            }

                            int missingPageIdx = line.indexOf(missingPageReads);
                            if (missingPageIdx == -1) {
                                continue;
                            }
                            String missingBlocks = line.substring(
                                    missingPageIdx + missingPageReads.length());

                            int prvTimestamp = prevLogEntry != null ? extractTimestamp(
                                    prevLogEntry.line) : -1;
                            int curTimestamp = extractTimestamp(line);
                            if (prvTimestamp == -1 || curTimestamp == -1) {
                                missingPages.add("count=" + missingBlocks);
                                continue;
                            }

                            int delta = curTimestamp - prvTimestamp;
                            missingPages.add(
                                    "count=" + missingBlocks + ", timestamp delta=" + delta + "ms");
                        }
                        return false;
                    }
                });

        assertTrue("Missing page reads found in atrace dump: " + String.join("\n", missingPages),
                missingPages.isEmpty());
    }

    static class ReadLogEntry {
        public final String line;
        public final int blockIdx;
        public final int count;
        public final int fileIdx;
        public final int appId;
        public final int userId;

        private ReadLogEntry(String line, int blockIdx, int count, int fileIdx, int appId,
                int userId) {
            this.line = line;
            this.blockIdx = blockIdx;
            this.count = count;
            this.fileIdx = fileIdx;
            this.appId = appId;
            this.userId = userId;
        }

        public String toString() {
            return blockIdx + "/" + count + "/" + fileIdx + "/" + appId + "/" + userId;
        }

        static final String BLOCK_PREFIX = "|page_read: index=";
        static final String COUNT_PREFIX = " count=";
        static final String FILE_PREFIX = " file=";
        static final String APP_ID_PREFIX = " appid=";
        static final String USER_ID_PREFIX = " userid=";

        private static int parseInt(String line, int prefixIdx, int prefixLen, int endIdx) {
            if (prefixIdx == -1) {
                return -1;
            }
            final String intStr;
            if (endIdx != -1) {
                intStr = line.substring(prefixIdx + prefixLen, endIdx);
            } else {
                intStr = line.substring(prefixIdx + prefixLen);
            }

            return Integer.parseInt(intStr);
        }

        static ReadLogEntry parse(String line) {
            int blockIdx = line.indexOf(BLOCK_PREFIX);
            if (blockIdx == -1) {
                return null;
            }
            int countIdx = line.indexOf(COUNT_PREFIX, blockIdx + BLOCK_PREFIX.length());
            if (countIdx == -1) {
                return null;
            }
            int fileIdx = line.indexOf(FILE_PREFIX, countIdx + COUNT_PREFIX.length());
            if (fileIdx == -1) {
                return null;
            }
            int appIdIdx = line.indexOf(APP_ID_PREFIX, fileIdx + FILE_PREFIX.length());
            final int userIdIdx;
            if (appIdIdx != -1) {
                userIdIdx = line.indexOf(USER_ID_PREFIX, appIdIdx + APP_ID_PREFIX.length());
            } else {
                userIdIdx = -1;
            }

            return new ReadLogEntry(
                    line,
                    parseInt(line, blockIdx, BLOCK_PREFIX.length(), countIdx),
                    parseInt(line, countIdx, COUNT_PREFIX.length(), fileIdx),
                    parseInt(line, fileIdx, FILE_PREFIX.length(), appIdIdx),
                    parseInt(line, appIdIdx, APP_ID_PREFIX.length(), userIdIdx),
                    parseInt(line, userIdIdx, USER_ID_PREFIX.length(), -1));
        }
    }

    @Test
    public void testReadLogParser() throws Exception {
        assertEquals(null, ReadLogEntry.parse("# tracer: nop\n"));
        assertEquals(
                "178/290/0/10184/0",
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.227110: tracing_mark_write: "
                                + "B|1639|page_read: index=178 count=290 file=0 appid=10184 "
                                + "userid=0")
                        .toString());
        assertEquals(
                null,
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.227111: tracing_mark_write: E|1639"));
        assertEquals(
                "468/337/0/10184/2",
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.243227: tracing_mark_write: "
                                + "B|1639|page_read: index=468 count=337 file=0 appid=10184 "
                                + "userid=2")
                        .toString());
        assertEquals(
                null,
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.243229: tracing_mark_write: E|1639"));
        assertEquals(
                "18/9/3/-1/-1",
                ReadLogEntry.parse(
                        "           <...>-2777  ( 1639) [006] ....  2764.227095: "
                                + "tracing_mark_write: B|1639|page_read: index=18 count=9 file=3")
                        .toString());
    }

    static int extractTimestamp(String line) {
        final String timestampEnd = ": tracing_mark_write:";
        int timestampEndIdx = line.indexOf(timestampEnd);
        if (timestampEndIdx == -1) {
            return -1;
        }

        int timestampBegIdx = timestampEndIdx - 1;
        for (; timestampBegIdx >= 0; --timestampBegIdx) {
            char ch = line.charAt(timestampBegIdx);
            if ('0' <= ch && ch <= '9' || ch == '.') {
                continue;
            }
            break;
        }
        double timestamp = Double.parseDouble(line.substring(timestampBegIdx, timestampEndIdx));
        return (int) (timestamp * 1000);
    }

    @Test
    public void testExtractTimestamp() throws Exception {
        assertEquals(-1, extractTimestamp("# tracer: nop\n"));
        assertEquals(14255168, extractTimestamp(
                "<...>-10355 ( 1636) [006] .... 14255.168694: tracing_mark_write: "
                        + "B|1636|page_read: index=1 count=16 file=0 appid=10184 userid=0"));
        assertEquals(2764243, extractTimestamp(
                "<...>-2777  ( 1639) [006] ....  2764.243225: tracing_mark_write: "
                        + "B|1639|missing_page_reads: count=132"));
        assertEquals(114176, extractTimestamp(
                "DataLoaderManag-8339    (   1780) [004] ....   114.176342: tracing_mark_write: "
                        + "B|1780|page_read: index=1846 count=21 file=0 appid=10151 userid=0"));
    }
    static class AppReads {
        public final String packageName;
        public final int reads;

        AppReads(String packageName, int reads) {
            this.packageName = packageName;
            this.reads = reads;
        }
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigNoDigesting() throws Exception {
        // Overall timeout of 3secs in 100ms intervals.
        final int installIterations = 1;
        final int atraceDumpIterations = 30;
        final int atraceDumpDelayMs = 100;
        final int blockSize = 4096;

        final String[] apks =
                new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                        TEST_HW7_SPLIT3, TEST_HW7_SPLIT4};
        final boolean[][] touched = new boolean[apks.length][];
        final int[] blocks = new int[apks.length];
        final AtomicLong[] totalTouchedBlocks = new AtomicLong[apks.length];
        for (int i = 0, size = apks.length; i < size; ++i) {
            final String apkName = apks[i];
            final File apkfile = new File(createApkPath(apkName));
            blocks[i] = (int) ((apkfile.length() + blockSize - 1) / blockSize);
            touched[i] = new boolean[blocks[i]];
            totalTouchedBlocks[i] = new AtomicLong(0);
        }

        final ArrayMap<Integer, Integer> uids = new ArrayMap<>();

        checkSysTrace(
                installIterations,
                atraceDumpIterations,
                atraceDumpDelayMs,
                () -> {
                    mSession =
                            new IncrementalInstallSession.Builder()
                                    .addApk(Paths.get(createApkPath(TEST_HW7)),
                                            Paths.get(createApkPath(TEST_HW7_IDSIG)))
                                    .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT0)),
                                            Paths.get(createApkPath(TEST_HW7_SPLIT0_IDSIG)))
                                    .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT1)),
                                            Paths.get(createApkPath(TEST_HW7_SPLIT1_IDSIG)))
                                    .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT2)),
                                            Paths.get(createApkPath(TEST_HW7_SPLIT2_IDSIG)))
                                    .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT3)),
                                            Paths.get(createApkPath(TEST_HW7_SPLIT3_IDSIG)))
                                    .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT4)),
                                            Paths.get(createApkPath(TEST_HW7_SPLIT4_IDSIG)))
                                    .addExtraArgs("-t", "-i", CTS_PACKAGE_NAME,
                                            "--skip-verification")
                                    .setLogger(new IncrementalDeviceConnection.Logger())
                                    .build();
                    getUiAutomation().adoptShellPermissionIdentity();
                    try {
                        mSession.start(Executors.newSingleThreadExecutor(),
                                IncrementalDeviceConnection.Factory.reliable());
                        mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
                        assertEquals(
                                "base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, "
                                        + "config.xxxhdpi",
                                getSplits(TEST_APP_PACKAGE));
                    } finally {
                        getUiAutomation().dropShellPermissionIdentity();
                    }
                    return null;
                },
                (stdout) -> {
                    try (Scanner scanner = new Scanner(stdout)) {
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine();
                            final ReadLogEntry readLogEntry = ReadLogEntry.parse(line);
                            if (readLogEntry == null) {
                                continue;
                            }
                            int fileIdx = readLogEntry.fileIdx;
                            for (int i = 0, count = readLogEntry.count; i < count; ++i) {
                                int blockIdx = readLogEntry.blockIdx + i;
                                if (touched[fileIdx][blockIdx]) {
                                    continue;
                                }

                                touched[fileIdx][blockIdx] = true;

                                int uid = UserHandle.getUid(readLogEntry.userId,
                                        readLogEntry.appId);
                                Integer touchedByUid = uids.get(uid);
                                uids.put(uid, touchedByUid == null ? 1 : touchedByUid + 1);

                                long totalTouched = totalTouchedBlocks[fileIdx].incrementAndGet();
                                if (totalTouched >= blocks[fileIdx]) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                });

        int firstFileIdx = CHECK_BASE_APK_DIGESTION ? 0 : 1;

        boolean found = false;
        for (int i = firstFileIdx, size = blocks.length; i < size; ++i) {
            if (totalTouchedBlocks[i].get() >= blocks[i]) {
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }

        PackageManager pm = getPackageManager();

        AppReads[] appIdReads = new AppReads[uids.size()];
        for (int i = 0, size = uids.size(); i < size; ++i) {
            final int uid = uids.keyAt(i);
            final int appId = UserHandle.getAppId(uid);
            final int userId = UserHandle.getUserId(uid);

            final String packageName;
            if (appId < Process.FIRST_APPLICATION_UID) {
                packageName = "<system>";
            } else {
                String[] packages = pm.getPackagesForUid(uid);
                if (packages == null || packages.length == 0) {
                    packageName = "<unknown package, appId=" + appId + ", userId=" + userId + ">";
                } else {
                    packageName = "[" + String.join(",", packages) + "]";
                }
            }
            appIdReads[i] = new AppReads(packageName, uids.valueAt(i));
        }
        Arrays.sort(appIdReads, (lhs, rhs) -> Integer.compare(rhs.reads, lhs.reads));

        final String packages = String.join("\n", Arrays.stream(appIdReads).map(
                item -> item.packageName + " : " + item.reads + " blocks").toArray(String[]::new));
        fail("Digesting detected, list of packages: " + packages);
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigPerUidTimeouts() throws Exception {
        executeShellCommand("atrace --async_start -b 1024 -c adb");
        try {
            setDeviceProperty("incfs_default_timeouts", "5000000:5000000:5000000");
            setDeviceProperty("known_digesters_list", CTS_PACKAGE_NAME);

            installPackage(TEST_APK);
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        } finally {
            executeShellCommand("atrace --async_stop");
        }
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigStreamPerUidTimeoutsIncompleteData() throws Exception {
        // To disable verification.
        installNonIncremental(TEST_APK);

        checkIncrementalDeliveryV2Feature();

        mSession =
                new IncrementalInstallSession.Builder()
                        .addApk(Paths.get(createApkPath(TEST_HW7)),
                                Paths.get(createApkPath(TEST_HW7_IDSIG)))
                        .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT0)),
                                Paths.get(createApkPath(TEST_HW7_SPLIT0_IDSIG)))
                        .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT1)),
                                Paths.get(createApkPath(TEST_HW7_SPLIT1_IDSIG)))
                        .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT2)),
                                Paths.get(createApkPath(TEST_HW7_SPLIT2_IDSIG)))
                        .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT3)),
                                Paths.get(createApkPath(TEST_HW7_SPLIT3_IDSIG)))
                        .addApk(Paths.get(createApkPath(TEST_HW7_SPLIT4)),
                                Paths.get(createApkPath(TEST_HW7_SPLIT4_IDSIG)))
                        .addExtraArgs("-t", "-i", CTS_PACKAGE_NAME, "--skip-verification")
                        .setLogger(new IncrementalDeviceConnection.Logger())
                        .build();

        executeShellCommand("atrace --async_start -b 10240 -c adb");
        try {
            setDeviceProperty("incfs_default_timeouts", "20000000:20000000:20000000");
            setDeviceProperty("known_digesters_list", CTS_PACKAGE_NAME);

            final int beforeReadDelayMs = 1000;
            Thread.currentThread().sleep(beforeReadDelayMs);

            // Partially install the apk+split0/1/2/3/4.
            getUiAutomation().adoptShellPermissionIdentity();
            try {
                mSession.start(Executors.newSingleThreadExecutor(),
                        IncrementalDeviceConnection.Factory.reliable());
                mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
                assertEquals(
                        "base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config"
                                + ".xxxhdpi",
                        getSplits(TEST_APP_PACKAGE));
            } finally {
                getUiAutomation().dropShellPermissionIdentity();
            }

            final String packagePath = getCodePath(TEST_APP_PACKAGE);

            // Try to read splits and see if we are throttled at least once.
            long maxReadTime = 0;
            for (String splitName : new String[]{"split_config.hdpi.apk", "split_config.mdpi.apk",
                    "split_config.xhdpi.apk", "split_config.xxxhdpi.apk",
                    "split_config.xxxhdpi.apk"}) {
                final File apkToRead = new File(packagePath, splitName);
                final long readTime0 = readAndReportTime(apkToRead, 1000);

                if (readTime0 < EXPECTED_READ_TIME) {
                    executeShellCommand("atrace --async_dump");
                }
                maxReadTime = Math.max(maxReadTime, readTime0);
                if (maxReadTime >= EXPECTED_READ_TIME) {
                    break;
                }
            }
            assertTrue("Must take longer than " + EXPECTED_READ_TIME + "ms: time0=" + maxReadTime
                    + "ms", maxReadTime >= EXPECTED_READ_TIME);
        } finally {
            executeShellCommand("atrace --async_stop");
        }
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigPerUidTimeoutsIgnored() throws Exception {
        // Timeouts would be ignored as there are no readlogs collected.
        final int beforeReadDelayMs = 5000;
        setDeviceProperty("incfs_default_timeouts", "5000000:5000000:5000000");
        setDeviceProperty("known_digesters_list", CTS_PACKAGE_NAME);

        // First fully install the apk and a split0.
        {
            installPackage(TEST_APK);
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
            installSplit(TEST_APK_SPLIT0);
            assertEquals("base, config.mdpi", getSplits(TEST_APP_PACKAGE));
            installSplit(TEST_APK_SPLIT1);
            assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));
        }

        // Allow IncrementalService to update the timeouts after full download.
        Thread.currentThread().sleep(beforeReadDelayMs);

        // Try to read a split and see if we are throttled.
        final long readTime = readAndReportTime(getSplit("split_config.mdpi.apk"), 1000);
        assertTrue("Must take less than " + EXPECTED_READ_TIME + "ms vs " + readTime + "ms",
                readTime < EXPECTED_READ_TIME);
    }

    @Test
    public void testInstallWithIdSigStreamIncompleteDataForSplit() throws Exception {
        File apkfile = new File(createApkPath(TEST_APK));
        File splitfile = new File(createApkPath(TEST_APK_SPLIT0));
        long splitLength = splitfile.length();
        // Don't fully stream the split.
        long newSplitLength = splitLength - (splitLength % 1024 == 0 ? 1024 : splitLength % 1024);
        File[] files = new File[]{apkfile, splitfile};
        String param = Arrays.stream(files).map(
                file -> file.getName() + ":" + file.length()).collect(Collectors.joining(" "));
        Truth.assertThat(executeShellCommand(
                String.format("pm install-incremental -t -g -S %s %s",
                        (apkfile.length() + splitfile.length()), param),
                files, new long[]{apkfile.length(), newSplitLength})).contains("Failure");
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    static class TestDataLoaderService extends DataLoaderService {
    }

    @Test
    public void testDataLoaderServiceDefaultImplementation() {
        DataLoaderService service = new TestDataLoaderService();
        assertEquals(null, service.onCreateDataLoader(null));
        IBinder binder = service.onBind(null);
        assertNotEquals(null, binder);
        assertEquals(binder, service.onBind(new Intent()));
    }

    @LargeTest
    @Test
    public void testInstallSysTraceDebuggable() throws Exception {
        doTestInstallSysTrace(TEST_APK);
    }

    @LargeTest
    @Test
    public void testInstallSysTraceProfileable() throws Exception {
        doTestInstallSysTrace(TEST_APK_PROFILEABLE);
    }

    @LargeTest
    @Test
    public void testInstallSysTraceNoReadlogs() throws Exception {
        setSystemProperty("debug.incremental.enforce_readlogs_max_interval_for_system_dataloaders",
                "1");
        setSystemProperty("debug.incremental.readlogs_max_interval_sec", "0");

        final int atraceDumpIterations = 30;
        final int atraceDumpDelayMs = 100;
        final String expected = "|page_read:";

        // We don't expect any readlogs with 0sec interval.
        assertFalse(
                "Page reads (" + expected + ") were found in atrace dump",
                checkSysTraceForSubstring(TEST_APK, expected, atraceDumpIterations,
                        atraceDumpDelayMs));
    }

    private boolean checkSysTraceForSubstring(String testApk, final String expected,
            int atraceDumpIterations, int atraceDumpDelayMs) throws Exception {
        final int installIterations = 3;
        return checkSysTrace(
                installIterations,
                atraceDumpIterations,
                atraceDumpDelayMs,
                () -> installPackage(testApk),
                (stdout) -> stdout.contains(expected));
    }

    private boolean checkSysTrace(
            int installIterations,
            int atraceDumpIterations,
            int atraceDumpDelayMs,
            final Callable<Void> installer,
            final Function<String, Boolean> checker)
            throws Exception {
        final int beforeReadDelayMs = 1000;

        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        final Thread readFromProcess = new Thread(() -> {
            try {
                executeShellCommand("atrace --async_start -b 10240 -c adb");
                try {
                    for (int i = 0; i < atraceDumpIterations; ++i) {
                        final String stdout = executeShellCommand("atrace --async_dump");
                        try {
                            if (checker.apply(stdout)) {
                                result.complete(true);
                                break;
                            }
                            Thread.currentThread().sleep(atraceDumpDelayMs);
                        } catch (InterruptedException ignored) {
                        }
                    }
                } finally {
                    executeShellCommand("atrace --async_stop");
                }
            } catch (IOException ignored) {
            }
        });
        readFromProcess.start();

        for (int i = 0; i < installIterations && !result.isDone(); ++i) {
            installer.call();
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
            Thread.currentThread().sleep(beforeReadDelayMs);
            uninstallPackageSilently(TEST_APP_PACKAGE);
        }

        readFromProcess.join();
        return result.getNow(false);
    }

    private void doTestInstallSysTrace(String testApk) throws Exception {
        // Async atrace dump uses less resources but requires periodic pulls.
        // Overall timeout of 10secs in 100ms intervals should be enough.
        final int atraceDumpIterations = 100;
        final int atraceDumpDelayMs = 100;
        final String expected = "|page_read:";

        assertTrue(
                "No page reads (" + expected + ") found in atrace dump",
                checkSysTraceForSubstring(testApk, expected, atraceDumpIterations,
                        atraceDumpDelayMs));
    }

    static boolean isAppInstalled(String packageName) throws IOException {
        return isAppInstalledForUser(packageName, -1);
    }

    static boolean isAppInstalledForUser(String packageName, int userId) throws IOException {
        final String command = userId < 0 ? "pm list packages " + packageName :
                "pm list packages --user " + userId + " " + packageName;
        final String commandResult = executeShellCommand(command);
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.equals("package:" + packageName));
    }

    private String getSplits(String packageName) throws IOException {
        final String result = parsePackageDump(packageName, "    splits=[");
        if (TextUtils.isEmpty(result)) {
            return null;
        }
        return result.substring(0, result.length() - 1);
    }

    private String getCodePath(String packageName) throws IOException {
        return parsePackageDump(packageName, "    codePath=");
    }

    private File getSplit(String splitName) throws Exception {
        return new File(getCodePath(TEST_APP_PACKAGE), splitName);
    }

    static String parsePackageDump(String packageName, String prefix) throws IOException {
        final String commandResult = executeShellCommand("pm dump-package " + packageName);
        final int prefixLength = prefix.length();
        Optional<String> maybeSplits = Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix)).findFirst();
        if (!maybeSplits.isPresent()) {
            return null;
        }
        String splits = maybeSplits.get();
        return splits.substring(prefixLength);
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    static void installNonIncremental(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm install -t -g " + file.getPath()));
    }

    static Void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm install-incremental -t -g " + file.getPath()));
        return null;
    }

    private void installSplit(String splitName) throws Exception {
        final File splitfile = new File(createApkPath(splitName));

        try (InputStream inputStream = executeShellCommandStream(
                "pm install-incremental -t -g -p " + TEST_APP_PACKAGE + " "
                        + splitfile.getPath())) {
            assertEquals("Success\n", readFullStream(inputStream));
        }
    }

    private void readSplitInChunks(String splitName) throws Exception {
        final int chunks = 2;
        final int waitBetweenChunksMs = 100;
        final File file = getSplit(splitName);

        assertTrue(file.toString(), file.exists());
        final long totalSize = file.length();
        final long chunkSize = totalSize / chunks;
        try (InputStream baseApkStream = new FileInputStream(file)) {
            final byte[] buffer = new byte[4 * 1024];
            long readSoFar = 0;
            long maxToRead = 0;
            for (int i = 0; i < chunks; ++i) {
                maxToRead += chunkSize;
                int length;
                while ((length = baseApkStream.read(buffer)) != -1) {
                    readSoFar += length;
                    if (readSoFar >= maxToRead) {
                        break;
                    }
                }
                if (readSoFar < totalSize) {
                    Thread.currentThread().sleep(waitBetweenChunksMs);
                }
            }
        }
    }

    private long readAndReportTime(File file, long borderTime) throws Exception {
        final long startTime = SystemClock.uptimeMillis();
        assertTrue(file.toString(), file.exists());
        try (InputStream baseApkStream = new FileInputStream(file)) {
            final byte[] buffer = new byte[128 * 1024];
            while (baseApkStream.read(buffer) != -1) {
                long readTime = SystemClock.uptimeMillis() - startTime;
                if (readTime >= borderTime) {
                    break;
                }
            }
        }
        return SystemClock.uptimeMillis() - startTime;
    }

    static String uninstallPackageSilently(String packageName) throws IOException {
        return executeShellCommand("pm uninstall " + packageName);
    }

    interface Result {
        boolean await() throws Exception;
    }

    static String executeShellCommand(String command) throws IOException {
        try (InputStream inputStream = executeShellCommandStream(command)) {
            return readFullStream(inputStream);
        }
    }

    private static InputStream executeShellCommandStream(String command) throws IOException {
        final ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(command);
        return new ParcelFileDescriptor.AutoCloseInputStream(stdout);
    }

    private static String executeShellCommand(String command, File[] inputs)
            throws IOException {
        return executeShellCommand(command, inputs, Stream.of(inputs).mapToLong(
                File::length).toArray());
    }

    private static String executeShellCommand(String command, File[] inputs, long[] expected)
            throws IOException {
        try (InputStream inputStream = executeShellCommandRw(command, inputs, expected)) {
            return readFullStream(inputStream);
        }
    }

    private static InputStream executeShellCommandRw(String command, File[] inputs, long[] expected)
            throws IOException {
        assertEquals(inputs.length, expected.length);
        final ParcelFileDescriptor[] pfds =
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommandRw(command);
        ParcelFileDescriptor stdout = pfds[0];
        ParcelFileDescriptor stdin = pfds[1];
        try (FileOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                stdin)) {
            for (int i = 0; i < inputs.length; i++) {
                try (FileInputStream inputStream = new FileInputStream(inputs[i])) {
                    writeFullStream(inputStream, outputStream, expected[i]);
                }
            }
        }
        return new ParcelFileDescriptor.AutoCloseInputStream(stdout);
    }

    static String readFullStream(InputStream inputStream, long expected)
            throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, expected);
        return result.toString("UTF-8");
    }

    static String readFullStream(InputStream inputStream) throws IOException {
        return readFullStream(inputStream, -1);
    }

    static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        final byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1 && (expected < 0 || total < expected)) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            assertEquals(expected, total);
        }
    }

    static void setDeviceProperty(String name, String value) {
        getUiAutomation().adoptShellPermissionIdentity(WRITE_ALLOWLISTED_DEVICE_CONFIG);
        try {
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name, value,
                    false);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    static void setSystemProperty(String name, String value) throws Exception {
        executeShellCommand("setprop " + name + " " + value);
    }

}

