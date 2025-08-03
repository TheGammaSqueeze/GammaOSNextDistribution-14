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

package android.content.pm.cts;

import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.checkIncrementalDeliveryFeature;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.installNonIncremental;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.isAppInstalledForUser;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.setDeviceProperty;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.setSystemProperty;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.uninstallPackageSilently;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.util.ArrayMap;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.MatcherUtils;
import com.android.incfs.install.IBlockFilter;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.PendingBlock;

import com.example.helloworld.lib.TestUtils;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
@AppModeFull
@AppModeNonSdkSandbox
@LargeTest
public class ResourcesHardeningTest {
    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String[] TEST_APKS = {
            "HelloWorldResHardening.apk",
            "HelloWorldResHardening_mdpi-v4.apk",
            "HelloWorldResHardening_hdpi-v4.apk"
    };

    private static final String RES_TABLE_PATH = "resources.arsc";
    private static final int INCFS_BLOCK_SIZE = 4096;

    private final Map<String, List<RestrictedBlockRange>> mRestrictedRanges = new ArrayMap<>();

    @Before
    public void onBefore() throws Exception {
        // TODO(b/280484615): remove once test is deflaked.
        assumeTrue(false);
        checkIncrementalDeliveryFeature();

        setDeviceProperty("incfs_default_timeouts", "1:1:1");
        setDeviceProperty("known_digesters_list", TestUtils.TEST_APP_PACKAGE);
        setSystemProperty("debug.incremental.always_enable_read_timeouts_for_system_dataloaders",
                "1");
        setSystemProperty("debug.incremental.enable_read_timeouts_after_install", "0");

        // Set up the blocks that need to be restricted in order to test resource hardening.
        if (!mRestrictedRanges.isEmpty()) {
            return;
        }
        for (final String apk : TEST_APKS) {
            try (ZipFile zip = new ZipFile(TEST_APK_PATH + apk)) {
                final List<RestrictedBlockRange> infos = new ArrayList<>();
                RestrictedBlockRange info;
                info = restrictZipEntry(zip, RES_TABLE_PATH);
                if (info != null) {
                    infos.add(info);
                }
                // Restrict only the middle block of the compiled xml to test that the whole
                // file needs to be present just to open the xml file.
                info = restrictOnlyMiddleBlock(restrictZipEntry(zip, TestUtils.RES_XML_PATH));
                if (info != null) {
                    infos.add(info);
                }
                // Restrict only the middle block of this file to test that the whole file does
                // NOT need to be present just to create an input stream or fd.
                info = restrictOnlyMiddleBlock(
                        restrictZipEntry(zip, TestUtils.RES_DRAWABLE_MDPI_PATH));
                if (info != null) {
                    infos.add(info);
                }
                // Test that FileNotFoundExceptions are thrown when the file is missing.
                info = restrictZipEntry(zip, TestUtils.RES_DRAWABLE_HDPI_PATH);
                if (info != null) {
                    infos.add(info);
                }
                assertFalse(infos.isEmpty());
                mRestrictedRanges.put(apk, infos);
            }
        }
    }

    @After
    public void onAfter() throws Exception {
        setSystemProperty("debug.incremental.always_enable_read_timeouts_for_system_dataloaders",
                "1");
        setSystemProperty("debug.incremental.enable_read_timeouts_after_install", "1");
    }

    @LargeTest
    @Test
    public void checkGetIdentifier() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkGetIdentifier);
    }

    @Test
    public void checkGetResourceName() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkGetResourceName);
    }

    @Test
    public void checkGetString() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkGetString);
    }

    @Test
    public void checkGetStringArray() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkGetStringArray);
    }

    @Test
    public void checkOpenXmlResourceParser() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkOpenXmlResourceParser);
    }

    @Test
    public void checkApplyStyle() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkApplyStyle);
    }

    @Test
    public void checkXmlAttributes() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkXmlAttributes);
    }

    @Test
    public void checkOpenMissingFile() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkOpenMissingFile);
    }

    @Test
    public void checkOpenMissingFdFile() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkOpenMissingFdFile);
    }

    @Test
    public void checkOpen() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkOpen);
    }

    @Test
    public void checkOpenFd() throws Exception {
        testIncrementalForeignPackageResources(TestUtils::checkOpenFd);
    }

    @Test
    public void checkGetIdentifierRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_GET_IDENTIFIER);
    }

    @Test
    public void checkGetResourceNameRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_GET_RESOURCE_NAME);
    }

    @Test
    public void checkGetStringRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_GET_STRING);
    }

    @Test
    public void checkGetStringArrayRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_GET_STRING_ARRAY);
    }

    @Test
    public void checkOpenXmlResourceParserRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_OPEN_XML);
    }

    @Test
    public void checkApplyStyleRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_APPLY_STYLE);
    }

    @Test
    public void checkXmlAttributesRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_XML_ATTRIBUTES);
    }

    @Test
    public void checkOpenMissingFileRemote() throws Exception {
        // If a zip entry local header is missing, libziparchive hardening causes a
        // FileNotFoundException to be thrown regardless of whether a process queries its own
        // resources or the resources of another package.
        testIncrementalOwnPackageResources(TestUtils.TEST_OPEN_FILE_MISSING,
                false /* expectCrash */);
    }

    @Test
    public void checkOpenMissingFdFileRemote() throws Exception {
        // If a zip entry local header is missing, libziparchive hardening causes a
        // FileNotFoundException to be thrown regardless of whether a process queries its own
        // resources or the resources of another package.
        testIncrementalOwnPackageResources(TestUtils.TEST_OPEN_FILE_FD_MISSING,
                false /* expectCrash */);
    }

    @Test
    public void checkOpenRemote() throws Exception {
        testIncrementalOwnPackageResources(TestUtils.TEST_OPEN_FILE);
    }

    @Test
    public void checkOpenFdRemote() throws Exception {
        // Failing to read missing blocks through a file descriptor using read/pread causes an
        // IOException to be thrown.
        testIncrementalOwnPackageResources(TestUtils.TEST_OPEN_FILE_FD, false /* expectCrash */);
    }

    private interface TestFunction {
        void apply(Resources res, TestUtils.AssertionType type) throws Exception;
    }

    /**
     * Installs a package incrementally and tests that retrieval of that package's resources from
     * within this process does not crash this process and instead falls back to some default
     * behavior.
     */
    private void testIncrementalForeignPackageResources(TestFunction test) throws Exception {
        try (ShellInstallSession session = startInstallSession()) {
            test.apply(session.getPackageResources(), TestUtils.AssertionType.ASSERT_SUCCESS);
        }
        // To disable verification.
        installNonIncremental(TEST_APKS[0]);
        try (ShellInstallSession session = startInstallSession()) {
            session.enableBlockRestrictions();
            test.apply(session.getPackageResources(), TestUtils.AssertionType.ASSERT_READ_FAILURE);
        }
    }

    /**
     * Installs a package incrementally and tests that the package crashes when it fails to retrieve
     * its own resources due to incremental installation.
     */
    private void testIncrementalOwnPackageResources(String testName, boolean expectCrash)
            throws Exception {
        try (RemoteTest session = new RemoteTest(startInstallSession(), testName)) {
            session.mSession.getPackageResources();
            session.start(true /* assertSuccess */);
        }
        // To disable verification.
        installNonIncremental(TEST_APKS[0]);
        try (RemoteTest session = new RemoteTest(startInstallSession(), testName)) {
            session.mSession.getPackageResources();
            session.mSession.enableBlockRestrictions();
            if (expectCrash) {
                MatcherUtils.assertThrows(instanceOf(RemoteProcessCrashedException.class),
                        () -> session.start(false /* assertSuccess */));
            } else {
                session.start(false /* assertSuccess */);
            }
        }
    }

    private void testIncrementalOwnPackageResources(String testName) throws Exception {
        testIncrementalOwnPackageResources(testName, true /* expectCrash */);
    }

    private static class RemoteProcessCrashedException extends RuntimeException {
    }

    private static class RemoteTest implements AutoCloseable {
        private static final int SPIN_SLEEP_MS = 500;
        private static final long RESPONSE_TIMEOUT_MS = 120 * 1000;

        private final ShellInstallSession mSession;
        private final String mTestName;

        RemoteTest(ShellInstallSession session, String testName) {
            mSession = session;
            mTestName = testName;
        }

        public void start(boolean assertSuccess) throws Exception {
            final AtomicInteger pid = new AtomicInteger();
            final IntentFilter statusFilter = new IntentFilter(TestUtils.TEST_STATUS_ACTION);

            final TestUtils.BroadcastDetector pidDetector = new TestUtils.BroadcastDetector(
                    getContext(), statusFilter, (Context context, Intent intent) -> {
                if (intent.hasExtra(TestUtils.PID_STATUS_PID_KEY)) {
                    pid.set(intent.getIntExtra(TestUtils.PID_STATUS_PID_KEY, -1));
                    return true;
                }
                return false;
            });

            final TestUtils.BroadcastDetector finishDetector = new TestUtils.BroadcastDetector(
                    getContext(), statusFilter, (Context context, Intent intent) -> {
                if (intent.hasExtra(TestUtils.TEST_STATUS_RESULT_KEY)) {
                    final String reason = intent.getStringExtra(TestUtils.TEST_STATUS_RESULT_KEY);
                    if (!reason.equals(TestUtils.TEST_STATUS_RESULT_SUCCESS)) {
                        throw new IllegalStateException("Remote test failed: " + reason);
                    }
                    return true;
                }
                return false;
            });

            // Start the test app and indicate which test to run.
            try (pidDetector; finishDetector) {
                final Intent launchIntent = new Intent(Intent.ACTION_MAIN);
                launchIntent.setClassName(TestUtils.TEST_APP_PACKAGE, TestUtils.TEST_ACTIVITY_NAME);
                launchIntent.putExtra(TestUtils.TEST_NAME_EXTRA_KEY, mTestName);
                launchIntent.putExtra(TestUtils.TEST_ASSERT_SUCCESS_EXTRA_KEY, assertSuccess);
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                getContext().startActivity(launchIntent);

                // The test app must respond with a broadcast containing its pid so this test can
                // check if the test app crashes.
                assertTrue("Timed out while waiting for pid",
                        pidDetector.waitForBroadcast(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS));

                // Wait for the test app to finish testing or crash.
                final ActivityManager am = getActivityManager();
                final int remotePid = pid.get();
                for (int i = 0; i < (RESPONSE_TIMEOUT_MS / SPIN_SLEEP_MS); i++) {
                    if (am.getRunningAppProcesses().stream().noneMatch(
                            info -> info.pid == remotePid)) {
                        throw new RemoteProcessCrashedException();
                    }
                    if (finishDetector.waitForBroadcast(SPIN_SLEEP_MS, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                }
                throw new TimeoutException("Timed out while waiting for remote test to finish");
            }
        }

        @Override
        public void close() throws Exception {
            mSession.close();
        }
    }

    private ShellInstallSession startInstallSession() throws IOException,
            InterruptedException {
        return startInstallSession(TEST_APKS, TestUtils.TEST_APP_PACKAGE);
    }

    private ShellInstallSession startInstallSession(String[] apks, String packageName)
            throws IOException, InterruptedException {
        final String v4SignatureSuffix = ".idsig";
        final TestBlockFilter filter = new TestBlockFilter();
        final IncrementalInstallSession.Builder builder = new IncrementalInstallSession.Builder()
                .addExtraArgs("--user", String.valueOf(getContext().getUserId()),
                              "-t", "-i", getContext().getPackageName(),
                              "--skip-verification")
                .setLogger(new IncrementalDeviceConnection.Logger())
                .setBlockFilter(filter);
        for (final String apk : apks) {
            final String path = TEST_APK_PATH + apk;
            builder.addApk(Paths.get(path), Paths.get(path + v4SignatureSuffix));
        }
        final ShellInstallSession session = new ShellInstallSession(
                builder.build(), filter, packageName);
        session.session.start(Executors.newSingleThreadExecutor(),
                IncrementalDeviceConnection.Factory.reliable());
        session.session.waitForInstallCompleted(10, TimeUnit.SECONDS);
        assertTrue(isAppInstalledForUser(packageName, getContext().getUserId()));
        return session;
    }

    /**
     * A wrapper for {@link IncrementalInstallSession} that uninstalls the installed package when
     * testing is finished.
     */
    private static class ShellInstallSession implements AutoCloseable {
        public final IncrementalInstallSession session;
        private final TestBlockFilter mFilter;
        private final String mPackageName;

        private ShellInstallSession(IncrementalInstallSession session,
                TestBlockFilter filter, String packageName) {
            this.session = session;
            this.mFilter = filter;
            this.mPackageName = packageName;
            getUiAutomation().adoptShellPermissionIdentity();
        }

        public void enableBlockRestrictions() {
            mFilter.enableBlockRestrictions();
        }

        public Resources getPackageResources() throws PackageManager.NameNotFoundException {
            return getContext().createPackageContext(mPackageName, 0).getResources();
        }

        @Override
        public void close() throws IOException {
            session.close();
            getUiAutomation().dropShellPermissionIdentity();
            uninstallPackageSilently(mPackageName);
        }
    }

    private class TestBlockFilter implements IBlockFilter {
        private final AtomicBoolean mRestrictBlocks = new AtomicBoolean(false);

        @Override
        public boolean shouldServeBlock(PendingBlock block) {
            if (!mRestrictBlocks.get() || block.getType() == PendingBlock.Type.SIGNATURE_TREE) {
                // Always send signature blocks and always send blocks when enableBlockRestrictions
                // has not been called.
                return true;
            }

            // Allow the block to be served if it does not reside in a restricted range.
            final String apkFileName = block.getPath().getFileName().toString();
            return mRestrictedRanges.get(apkFileName).stream().noneMatch(
                    info -> info.dataStartBlockIndex <= block.getBlockIndex()
                            && block.getBlockIndex() <= info.dataEndBlockIndex);
        }

        public void enableBlockRestrictions() {
            mRestrictBlocks.set(true);
        }
    }

    private static class RestrictedBlockRange {
        public final String entryName;
        public final int dataStartBlockIndex;
        public final int dataEndBlockIndex;

        RestrictedBlockRange(String zipEntryName, int dataStartBlockIndex,
                int dataEndBlockIndex) {
            this.entryName = zipEntryName;
            this.dataStartBlockIndex = dataStartBlockIndex;
            this.dataEndBlockIndex = dataEndBlockIndex;
        }
    }

    private static RestrictedBlockRange restrictZipEntry(ZipFile file, String entryFileName) {
        final ZipArchiveEntry info = file.getEntry(entryFileName);
        if (info == null) return null;
        final long headerSize = entryFileName.getBytes(StandardCharsets.UTF_8).length + 30;
        final int dataStartBlock = (int) (info.getDataOffset() - headerSize) / INCFS_BLOCK_SIZE;
        final int dataEndBlock = (int) (info.getDataOffset() + info.getCompressedSize())
                / INCFS_BLOCK_SIZE;
        return new RestrictedBlockRange(entryFileName, dataStartBlock, dataEndBlock);
    }

    private static RestrictedBlockRange restrictOnlyMiddleBlock(RestrictedBlockRange info) {
        if (info == null) return null;
        assertTrue(info.dataEndBlockIndex - info.dataStartBlockIndex > 2);
        final int middleBlock = (info.dataStartBlockIndex + info.dataEndBlockIndex) / 2;
        return new RestrictedBlockRange(info.entryName, middleBlock, middleBlock);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static ActivityManager getActivityManager() {
        return (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
    }
}
