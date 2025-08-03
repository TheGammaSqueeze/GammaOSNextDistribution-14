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

package com.android.tests.loadingprogress.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.LargeTest;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.adb.ddmlib.DeviceConnection;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * atest com.android.tests.loadingprogress.host.IncrementalLoadingProgressTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class IncrementalLoadingProgressTest extends BaseHostJUnit4Test {
    private static final String DEVICE_TEST_PACKAGE_NAME =
            "com.android.tests.loadingprogress.device";
    private static final String TEST_APK = "CtsInstalledLoadingProgressTestsApp.apk";
    private static final String TEST_SPLIT_APK =
            "CtsInstalledLoadingProgressTestsApp_hdpi-v4.apk";
    private static final String TEST_APP_PACKAGE_NAME = "com.android.tests.loadingprogress.app";
    private static final String TEST_CLASS_NAME = DEVICE_TEST_PACKAGE_NAME + ".LoadingProgressTest";
    private static final String IDSIG_SUFFIX = ".idsig";
    private static final int WAIT_FOR_LOADING_PROGRESS_UPDATE_MS = 2000;
    private IncrementalInstallSession mSession;
    private static final int PACKAGE_SETTING_WRITE_SLEEP_MS = 10000;

    @Before
    public void setUp() throws Exception {
        // Only enable this test on devices with Incremental Delivery V2 features
        assumeTrue("true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery 2")));
        getDevice().uninstallPackage(TEST_APP_PACKAGE_NAME);
        // Before the test app is installed, launch a helper app to register a LauncherApps callback
        // This ensures the loading progress listener is activated when the test app is installed
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "registerFirstLauncherAppsCallback"));

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File base_apk = buildHelper.getTestFile(TEST_APK);
        assertNotNull(base_apk);
        final File base_v4Signature = buildHelper.getTestFile(
                TEST_APK + IDSIG_SUFFIX);
        assertNotNull(base_v4Signature);
        final File split_apk = buildHelper.getTestFile(TEST_SPLIT_APK);
        assertNotNull(split_apk);
        final File split_v4Signature = buildHelper.getTestFile(
                TEST_SPLIT_APK + IDSIG_SUFFIX);
        assertNotNull(split_v4Signature);
        mSession = new IncrementalInstallSession.Builder()
                .addApk(Paths.get(base_apk.getAbsolutePath()),
                        Paths.get(base_v4Signature.getAbsolutePath()))
                .addApk(Paths.get(split_apk.getAbsolutePath()),
                        Paths.get(split_v4Signature.getAbsolutePath()))
                .addExtraArgs("-t")
                .build();

        mSession.start(Executors.newCachedThreadPool(),
                DeviceConnection.getFactory(getDevice().getSerialNumber()));
        mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
        assertTrue(getDevice().isPackageInstalled(TEST_APP_PACKAGE_NAME));
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.close();
        }
        getDevice().uninstallPackage(TEST_APP_PACKAGE_NAME);
        assertFalse(getDevice().isPackageInstalled(TEST_APP_PACKAGE_NAME));
    }

    @LargeTest
    @Test
    public void testGetLoadingProgressSuccess() throws Exception {
        // Check partial loading progress
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testGetPartialLoadingProgress"));
        // Trigger full download
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testReadAllBytes"));
        // Wait for loading progress to update
        RunUtil.getDefault().sleep(WAIT_FOR_LOADING_PROGRESS_UPDATE_MS);
        // Check full loading progress
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testGetFullLoadingProgress"));
    }

    @LargeTest
    @Test
    public void testGetLoadingProgressDuringMigration() throws Exception {
        // Check partial loading progress
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testGetPartialLoadingProgress"));
        final List<File> apks = new ArrayList<>(2);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File base_apk = buildHelper.getTestFile(TEST_APK);
        assertNotNull(base_apk);
        apks.add(base_apk);
        final File split_apk = buildHelper.getTestFile(TEST_SPLIT_APK);
        assertNotNull(split_apk);
        apks.add(split_apk);
        // Trigger app migration through normal package installation.
        getDevice().installPackages(apks, false, "-t");
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testGetFullLoadingProgress"));
    }

    @LargeTest
    @Test
    @Ignore("b/229901433")
    public void testOnPackageLoadingProgressChangedCalledWithPartialLoaded() throws Exception {
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testOnPackageLoadingProgressChangedCalledWithPartialLoaded"));
    }

    @LargeTest
    @Test
    public void testOnPackageLoadingProgressChangedCalledWithFullyLoaded() throws Exception {
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testOnPackageLoadingProgressChangedCalledWithFullyLoaded"));
    }

    @Test
    public void testLoadingProgressInDumpsysWhenPartiallyLoaded() throws Exception {
        // Wait for loading progress to update
        RunUtil.getDefault().sleep(WAIT_FOR_LOADING_PROGRESS_UPDATE_MS);
        // Check that "loadingProgress" is shown in the dumpsys of on a partially loaded app
        final String loadingPercentageString = getLoadingProgressFromDumpsys();
        assertNotNull(loadingPercentageString);
        final int loadingPercentage = Integer.parseInt(loadingPercentageString);
        assertTrue(loadingPercentage > 0 && loadingPercentage < 100);
    }

    private String getLoadingProgressFromDumpsys() throws Exception {
        return getStringFromDumpsys("loadingProgress=(\\d+)%");
    }

    private long getLoadingCompletedTimeSecondsFromDumpsys() throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = getStringFromDumpsys("loadingCompletedTime=(.*)");
        if (dateString == null) {
            return -1;
        }
        Date date = df.parse(dateString);
        return Math.floorDiv(date.getTime(), 1000);
    }

    private String getStringFromDumpsys(String regex) throws Exception {
        final CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        getDevice().executeShellCommand("dumpsys package " + TEST_APP_PACKAGE_NAME,
                receiver);
        final String output = receiver.getOutput();
        // Expecting output like "loadingProgress=50%"
        final Matcher matcher = Pattern.compile(regex).matcher(output);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        return matcher.group(1);
    }
}
