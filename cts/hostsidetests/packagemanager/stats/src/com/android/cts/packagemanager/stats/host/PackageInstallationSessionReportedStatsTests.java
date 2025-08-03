/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PackageInstallationSessionReportedStatsTests extends PackageManagerStatsTestsBase {
    private static final int STEP_FREEZE_INSTALL = 6;
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomEmptyApp.apk";
    private static final String TEST_INSTALL_APK_V2 = "CtsStatsdAtomEmptyAppV2.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.emptyapp";
    private static final String HELPER_PACKAGE = "com.android.cts.packagemanager.stats.device";
    private static final String HELPER_CLASS =
            ".PackageInstallationSessionReportedStatsTestsHelper";
    private static final String GET_USER_TYPES_HELPER_METHOD = "getUserTypeIntegers";
    private static final String GET_USER_TYPES_HELPER_ARG_USER_IDS = "userIds";
    private static final String GET_USER_TYPES_HELPER_ARG_USER_TYPES = "userTypes";
    private static final String TEST_INSTALL_STATIC_SHARED_LIB_V1_APK =
            "CtsStatsdAtomStaticSharedLibProviderV1.apk";
    private static final String TEST_INSTALL_STATIC_SHARED_LIB_V2_APK =
            "CtsStatsdAtomStaticSharedLibProviderV2.apk";
    private static final String TEST_INSTALL_STATIC_SHARED_LIB_V1_PACKAGE =
            "com.android.cts.packagemanager.stats.emptystaticsharedlib";
    private static final String TEST_INSTALL_STATIC_SHARED_LIB_NAME = "test.stats.lib";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        getDevice().uninstallPackage(TEST_INSTALL_STATIC_SHARED_LIB_V1_PACKAGE);
        super.tearDown();
    }

    public void testPackageInstallationSessionReportedForApkSuccessWithReplace() throws Exception {
        if (!Utils.hasIncrementalFeature(getDevice())) {
            return;
        }
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_INSTALLATION_SESSION_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK, TEST_INSTALL_PACKAGE, mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();
        installPackageUsingIncremental(new String[]{TEST_INSTALL_APK_V2});
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        List<AtomsProto.PackageInstallationSessionReported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallationSessionReported()) {
                reports.add(data.getAtom().getPackageInstallationSessionReported());
            }
        }
        assertThat(reports.size()).isEqualTo(2);
        final int expectedUid = getAppUid(TEST_INSTALL_PACKAGE);
        final int expectedUser = getDevice().getCurrentUser();
        final long expectedApksSizeBytes = getTestFileSize(TEST_INSTALL_APK);
        // TODO(b/249294752): check installer in the report
        checkReportResult(reports.get(0), expectedUid, Collections.singletonList(expectedUser),
                Collections.emptyList(), 1 /* success */, 0 /* internalErrorCode */,
                1 /* versionCode */, expectedApksSizeBytes, 0 /* dataLoaderType */,
                0 /* expectedUserActionRequiredType */, false,
                false, false, false, false, false, false);
        checkDurationResult(reports.get(0));

        checkReportResult(reports.get(1), expectedUid, Collections.singletonList(expectedUser),
                Collections.singletonList(expectedUser), 1 /* success */, 0 /* internalErrorCode */,
                2 /* versionCode */, getTestFileSize(TEST_INSTALL_APK_V2), 2 /* dataLoaderType */,
                0 /* expectedUserActionRequiredType */, false,
                true, false, false, false, false, false);
        checkDurationResult(reports.get(1));

        // No uninstall log from app update
        List<AtomsProto.PackageUninstallationReported> uninstallReports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageUninstallationReported()) {
                uninstallReports.add(data.getAtom().getPackageUninstallationReported());
            }
        }
        assertThat(uninstallReports).isEmpty();
    }

    private void checkDurationResult(AtomsProto.PackageInstallationSessionReported report) {
        final long totalDuration = report.getTotalDurationMillis();
        assertThat(totalDuration).isGreaterThan(0);
        // TODO(b/308138823): Use hortenInstallFreeze(), meet java.lang.NoClassDefFoundError:
        // android/provider/DeviceConfig error
        // @RequiresFlagsEnabled still runs even the flag is false.
        //int expectedSteps = shortenInstallFreeze() ? 5 : 4;
        assertThat(report.getInstallStepsCount()).isAtLeast(4);
        long sumStepDurations = 0;
        int stepCount = report.getInstallStepsCount();
        for (int i = 0; i < stepCount; i++) {
            int step = report.getInstallSteps(i);
            // Don't count freeze step time
            if (step != STEP_FREEZE_INSTALL) {
                long duration = report.getStepDurationMillis(i);
                assertThat(duration).isAtLeast(0);
                sumStepDurations += duration;
            }
        }
        assertThat(sumStepDurations).isGreaterThan(0);
        assertThat(sumStepDurations).isLessThan(totalDuration);
    }

    private void checkReportResult(AtomsProto.PackageInstallationSessionReported report,
            int expectedUid, List<Integer> expectedUserIds,
            List<Integer> expectedOriginalUserIds, int expectedPublicReturnCode,
            int expectedInternalErrorCode,
            long expectedVersionCode, long expectedApksSizeBytes, int expectedDataLoaderType,
            int expectedUserActionRequiredType,
            boolean expectedIsInstant, boolean expectedIsReplace, boolean expectedIsSystem,
            boolean expectedIsInherit, boolean expectedInstallingExistingAsUser,
            boolean expectedIsMoveInstall, boolean expectedIsStaged)
            throws DeviceNotAvailableException {
        assertThat(report.getSessionId()).isNotEqualTo(-1);
        assertThat(report.getUid()).isEqualTo(expectedUid);
        assertThat(report.getUserIdsList()).containsAtLeastElementsIn(expectedUserIds);
        checkUserTypes(expectedUserIds, report.getUserTypesList());
        assertThat(report.getOriginalUserIdsList()).containsAtLeastElementsIn(
                expectedOriginalUserIds);
        assertThat(report.getPublicReturnCode()).isEqualTo(expectedPublicReturnCode);
        assertThat(report.getInternalErrorCode()).isEqualTo(expectedInternalErrorCode);
        assertThat(report.getVersionCode()).isEqualTo(expectedVersionCode);
        assertThat(report.getApksSizeBytes()).isEqualTo(expectedApksSizeBytes);
        assertThat(report.getOriginalInstallerPackageUid()).isEqualTo(-1);
        assertThat(report.getDataLoaderType()).isEqualTo(expectedDataLoaderType);
        assertThat(report.getUserActionRequiredType()).isEqualTo(expectedUserActionRequiredType);
        assertThat(report.getIsInstant()).isEqualTo(expectedIsInstant);
        assertThat(report.getIsReplace()).isEqualTo(expectedIsReplace);
        assertThat(report.getIsSystem()).isEqualTo(expectedIsSystem);
        assertThat(report.getIsInherit()).isEqualTo(expectedIsInherit);
        assertThat(report.getIsInstallingExistingAsUser()).isEqualTo(
                expectedInstallingExistingAsUser);
        assertThat(report.getIsMoveInstall()).isEqualTo(expectedIsMoveInstall);
        assertThat(report.getIsStaged()).isEqualTo(expectedIsStaged);
        // assert that package name is not set if install comes from adb
        if ((report.getInstallFlags() & 0x00000020 /* INSTALL_FROM_ADB */) != 0) {
            assertThat(report.getPackageName()).isEmpty();
        }
    }

    private void checkUserTypes(List<Integer> userIds, List<Integer> reportedUserTypes)
            throws DeviceNotAvailableException {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        final HashMap<String, String> testArgs = new HashMap<>();
        testArgs.put(GET_USER_TYPES_HELPER_ARG_USER_IDS,
                userIds.stream().map(Object::toString).collect(Collectors.joining(","))
        );
        Map<String, String> testResult = Utils.runDeviceTests(getDevice(), HELPER_PACKAGE,
                HELPER_CLASS, GET_USER_TYPES_HELPER_METHOD, testArgs);
        assertNotNull(testResult);
        assertEquals(1, testResult.size());
        String[] userTypesStrings = testResult.get(GET_USER_TYPES_HELPER_ARG_USER_TYPES).split(",");
        List<Integer> expectedUserTypes = Arrays.stream(userTypesStrings).map(
                Integer::valueOf).collect(Collectors.toList());
        assertThat(reportedUserTypes).containsAtLeastElementsIn(expectedUserTypes);
    }

    public void testPackageUninstalledReported() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_UNINSTALLATION_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK, TEST_INSTALL_PACKAGE, mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();
        final int expectedUid = getAppUid(TEST_INSTALL_PACKAGE);
        DeviceUtils.uninstallTestApp(getDevice(), TEST_INSTALL_PACKAGE);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isFalse();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        List<AtomsProto.PackageUninstallationReported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageUninstallationReported()) {
                reports.add(data.getAtom().getPackageUninstallationReported());
            }
        }
        assertThat(reports.size()).isEqualTo(1);
        AtomsProto.PackageUninstallationReported report = reports.get(0);
        assertThat(report.getUid()).isEqualTo(expectedUid);
        final List<Integer> users = Collections.singletonList(getDevice().getCurrentUser());
        assertThat(report.getUserIdsList()).containsAtLeastElementsIn(users);
        assertThat(report.getOriginalUserIdsList()).containsAtLeastElementsIn(users);
        assertThat(report.getUninstallFlags()).isEqualTo(2 /* DELETE_ALL_USERS */);
        assertThat(report.getReturnCode()).isEqualTo(1);
        assertThat(report.getIsSystem()).isFalse();
        assertThat(report.getIsUninstallForUsers()).isFalse();
    }

    public void testPackageInstallationFailedVersionDowngradeReported() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_INSTALLATION_SESSION_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK_V2, TEST_INSTALL_PACKAGE,
                mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();
        // Second install should fail because of version downgrade
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final String result = getDevice().installPackage(buildHelper.getTestFile(TEST_INSTALL_APK),
                /*reinstall=*/true, /*grantPermissions=*/true);
        assertThat(result).isNotNull();

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        List<AtomsProto.PackageInstallationSessionReported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallationSessionReported()) {
                reports.add(data.getAtom().getPackageInstallationSessionReported());
            }
        }
        assertThat(reports.size()).isEqualTo(2);
        final int expectedUid = getAppUid(TEST_INSTALL_PACKAGE);
        final int expectedUser = getDevice().getCurrentUser();
        checkReportResult(reports.get(0), expectedUid, Collections.singletonList(expectedUser),
                Collections.emptyList(), 1 /* success */, 0 /* internalErrorCode */,
                2 /* versionCode */, getTestFileSize(TEST_INSTALL_APK_V2), 0 /* dataLoaderType */,
                0 /* expectedUserActionRequiredType */, false,
                false, false, false, false, false, false);
        checkDurationResult(reports.get(0));
        checkReportResult(
                reports.get(1), -1 /* uid */, Collections.emptyList(), Collections.emptyList(),
                -25 /* INSTALL_FAILED_VERSION_DOWNGRADE */, 0 /* internalErrorCode */,
                0 /* versionCode */, 0, 0 /* dataLoaderType */,
                0 /* expectedUserActionRequiredType */, false,
                false, false, false, false, false, false);
    }

    public void testPackageInstallationFailedInternalErrorReported() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_INSTALLATION_SESSION_REPORTED_FIELD_NUMBER);
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        String result = getDevice().installPackage(buildHelper.getTestFile(
                TEST_INSTALL_STATIC_SHARED_LIB_V1_APK),
                /*reinstall=*/true, /*grantPermissions=*/true);
        assertThat(result).isNull();
        assertThat(isLibraryInstalled(TEST_INSTALL_STATIC_SHARED_LIB_NAME)).isTrue();
        // Second install should fail because of static shared lib version order mismatch
        result = getDevice().installPackage(buildHelper.getTestFile(
                TEST_INSTALL_STATIC_SHARED_LIB_V2_APK),
                /*reinstall=*/true, /*grantPermissions=*/true);
        assertThat(result).isNotNull();

        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        List<AtomsProto.PackageInstallationSessionReported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallationSessionReported()) {
                reports.add(data.getAtom().getPackageInstallationSessionReported());
            }
        }
        assertThat(reports.size()).isEqualTo(2);
        final int expectedUid = getAppUid(TEST_INSTALL_STATIC_SHARED_LIB_V1_PACKAGE);
        final int expectedUser = getDevice().getCurrentUser();
        checkReportResult(reports.get(0), expectedUid, Collections.singletonList(expectedUser),
                Collections.emptyList(), 1 /* success */, 0 /* internalErrorCode */,
                1 /* versionCode */, getTestFileSize(TEST_INSTALL_APK_V2), 0 /* dataLoaderType */,
                0 /* expectedUserActionRequiredType */, false,
                false, false, false, false, false, false);
        checkDurationResult(reports.get(0));
        checkReportResult(
                reports.get(1), -1 /* uid */, Collections.emptyList(), Collections.emptyList(),
                -110 /* INSTALL_FAILED_INTERNAL_ERROR */,
                -14 /* INTERNAL_ERROR_STATIC_SHARED_LIB_VERSION_CODES_ORDER */,
                0 /* versionCode */,
                0, 0 /* dataLoaderType */,
                0 /* expectedUserActionRequiredType */, false,
                false, false, false, false, false, false);
    }
}
