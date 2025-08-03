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

package com.android.cts.packagemanager.stats.host;

import static com.android.cts.packagemanager.stats.host.Utils.SIGNATURE_FILE_SUFFIX;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.LargeTest;
import android.server.ErrorSource;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.adb.ddmlib.DeviceConnection;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class IncrementalAppErrorStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String TEST_REMOTE_DIR = "/data/local/tmp/appErrorTest";
    private static final String HELPER_PACKAGE = "com.android.cts.packagemanager.stats.device";
    private static final String HELPER_CLASS = ".IncrementalAppErrorStatsTestsHelper";
    private static final String HELPER_METHOD_GET_PAGE_INDEX_TO_BLOCK = "getPageIndexToBlock";
    private static final String HELPER_METHOD_LOAD_APKS = "loadingApks";
    private static final String HELPER_ARG_APK_PATH = "remoteApkPath";
    private static final String HELPER_ARG_PKG_NAME = "packageName";
    private static final String PAGE_INDEX_TO_BLOCK = "pageIndexToBlock";
    private static final int INSTALL_TIMEOUT_SECONDS = 10;
    private static final int METRICS_WAIT_MILLISECONDS = 1_000;

    private IBuildInfo mCtsBuild;
    private IncrementalInstallSession mSession;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Before
    public void setUp() throws Exception {
        if (!Utils.hasIncrementalFeature(getDevice())) {
            return;
        }
        super.setUp();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        String remoteApkPath = Utils.pushApkToRemote(
                DeviceUtils.STATSD_ATOM_TEST_APK, TEST_REMOTE_DIR, mCtsBuild, getDevice());
        final HashMap<String, String> testArgs = new HashMap<>();
        testArgs.put(HELPER_ARG_APK_PATH, remoteApkPath);
        Map<String, String> testResult = Utils.runDeviceTests(getDevice(), HELPER_PACKAGE,
                HELPER_CLASS, HELPER_METHOD_GET_PAGE_INDEX_TO_BLOCK, testArgs);
        assertNotNull(testResult);
        assertEquals(1, testResult.size());

        int blockedPageIndex = Integer.parseInt(testResult.get(PAGE_INDEX_TO_BLOCK));
        assertTrue(blockedPageIndex > 0);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final File apk = buildHelper.getTestFile(DeviceUtils.STATSD_ATOM_TEST_APK);
        assertNotNull(apk);
        final File v4Signature = buildHelper.getTestFile(
                DeviceUtils.STATSD_ATOM_TEST_APK + SIGNATURE_FILE_SUFFIX);
        assertNotNull(v4Signature);
        LogUtil.CLog.i("Blocking page at index: " + blockedPageIndex);
        mSession = new IncrementalInstallSession.Builder()
                .addApk(Paths.get(apk.getAbsolutePath()),
                        Paths.get(v4Signature.getAbsolutePath()))
                .addExtraArgs("-g") // grant permissions
                .setBlockFilter(block -> {
                    // block a page from res/raw; does not affect test run
                    return block.getBlockIndex() != blockedPageIndex;
                })
                .build();
        mSession.start(Executors.newCachedThreadPool(),
                DeviceConnection.getFactory(getDevice().getSerialNumber()));
        mSession.waitForInstallCompleted(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(getDevice().isPackageInstalled(DeviceUtils.STATSD_ATOM_TEST_PKG));
        // Preload most of the pages to make sure the test can run but it also causes pending reads
        final HashMap<String, String> testArgsForLoading = new HashMap<>();
        testArgsForLoading.put(HELPER_ARG_PKG_NAME, DeviceUtils.STATSD_ATOM_TEST_PKG);
        Utils.runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                HELPER_METHOD_LOAD_APKS, testArgsForLoading);
    }

    private boolean hasIncrementalDeliveryV2Feature() throws Exception {
        return "true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery 2"));
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.close();
        }
        getDevice().uninstallPackage(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertFalse(getDevice().isPackageInstalled(DeviceUtils.STATSD_ATOM_TEST_PKG));
        super.tearDown();
    }

    @LargeTest
    public void testAppCrashOnIncremental() throws Exception {
        if (!Utils.hasIncrementalFeature(getDevice())) {
            return;
        }
        final int atomTag = AtomsProto.Atom.APP_CRASH_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.crash");
        RunUtil.getDefault().sleep(METRICS_WAIT_MILLISECONDS);
        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        AtomsProto.AppCrashOccurred atom = data.get(0).getAtom().getAppCrashOccurred();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        assertThat(atom.getEventType()).isEqualTo("crash");
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(AtomsProto.AppCrashOccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(AtomsProto.AppCrashOccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        assertTrue(atom.getIsIncremental());
        assertFalse((1.0f - atom.getLoadingProgress()) < 0.0000001f);
        assertTrue(atom.getMillisSinceOldestPendingRead() > 0);
        assertEquals(3 /* HEALTH_STATUS_UNHEALTHY */, atom.getStorageHealthCode());
        assertEquals(6 /* DATA_LOADER_IMAGE_READY */, atom.getDataLoaderStatusCode());
        assertFalse(atom.getReadLogsEnabled());
        assertTrue(atom.getMillisSinceLastDataLoaderBind() > 0);
        assertEquals(0, atom.getDataLoaderBindDelayMillis());
        if (!hasIncrementalDeliveryV2Feature()) {
            // Skip kernel stats check if it's not supported
            return;
        }
        assertTrue(atom.getTotalDelayedReads() > 0);
        assertTrue(atom.getTotalFailedReads() > 0);
        assertTrue(atom.getLastReadErrorMillisSince() > 0);
        assertEquals(-62 /* -ETIME */, atom.getLastReadErrorCode());
        assertTrue(atom.getTotalDelayedReadsDurationMillis() > 0);
    }

    @LargeTest
    public void testAppAnrIncremental() throws Exception {
        if (!Utils.hasIncrementalFeature(getDevice())) {
            return;
        }
        final int atomTag = AtomsProto.Atom.ANR_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);
        final int ANR_WAIT_MILLS = 15_000;

        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "ANRActivity", null, null)) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            getDevice().executeShellCommand(
                    "am broadcast -a action_anr -p " + DeviceUtils.STATSD_ATOM_TEST_PKG);
            RunUtil.getDefault().sleep(ANR_WAIT_MILLS);
        }

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAtom().hasAnrOccurred()).isTrue();
        AtomsProto.ANROccurred atom = data.get(0).getAtom().getAnrOccurred();
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(AtomsProto.ANROccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(AtomsProto.ANROccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertTrue(atom.getIsIncremental());
        assertFalse((1.0f - atom.getLoadingProgress()) < 0.0000001f);
    }
}
