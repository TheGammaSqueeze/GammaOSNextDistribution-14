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

package com.android.cts.packagemanager.stats.host;

import com.android.tradefed.util.RunUtil;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;

import java.util.ArrayList;
import java.util.List;

public class PackageInstallerV2StatsTests extends PackageManagerStatsTestsBase {
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomEmptyApp.apk";
    private static final String TEST_INSTALL_APK_BASE = "CtsStatsdAtomEmptySplitApp.apk";
    private static final String TEST_INSTALL_APK_SPLIT = "CtsStatsdAtomEmptySplitApp_pl.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.emptyapp";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        super.tearDown();
    }

    public void testPackageInstallerV2MetricsReported() throws Throwable {
        if (!Utils.hasIncrementalFeature(getDevice())) {
            return;
        }
        final AtomsProto.PackageInstallerV2Reported report = installPackageUsingV2AndGetReport(
                new String[]{TEST_INSTALL_APK});
        assertTrue(report.getIsIncremental());
        // tests are ran using SHELL_UID and installation will be treated as adb install
        assertEquals("", report.getPackageName());
        assertEquals(1, report.getReturnCode());
        assertTrue(report.getDurationMillis() > 0);
        assertEquals(getTestFileSize(TEST_INSTALL_APK), report.getApksSizeBytes());
        assertTrue(report.getUid() != 0);
        assertEquals(getAppUid(TEST_INSTALL_PACKAGE), report.getUid());
    }

    public void testPackageInstallerV2MetricsReportedForSplits() throws Throwable {
        if (!Utils.hasIncrementalFeature(getDevice())) {
            return;
        }
        final AtomsProto.PackageInstallerV2Reported report = installPackageUsingV2AndGetReport(
                new String[]{TEST_INSTALL_APK_BASE, TEST_INSTALL_APK_SPLIT});
        assertTrue(report.getIsIncremental());
        // tests are ran using SHELL_UID and installation will be treated as adb install
        assertEquals("", report.getPackageName());
        assertEquals(1, report.getReturnCode());
        assertTrue(report.getDurationMillis() > 0);
        assertEquals(
                getTestFileSize(TEST_INSTALL_APK_BASE) + getTestFileSize(TEST_INSTALL_APK_SPLIT),
                report.getApksSizeBytes());
        assertTrue(report.getUid() != 0);
        assertEquals(getAppUid(TEST_INSTALL_PACKAGE), report.getUid());
    }

    private AtomsProto.PackageInstallerV2Reported installPackageUsingV2AndGetReport(
            String[] apkNames) throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_INSTALLER_V2_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        installPackageUsingIncremental(apkNames);
        assertTrue(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser())));
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.PackageInstallerV2Reported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallerV2Reported()) {
                reports.add(data.getAtom().getPackageInstallerV2Reported());
            }
        }
        assertEquals(1, reports.size());
        return reports.get(0);
    }
}
