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

package android.cts.statsdatom.appexit;

import com.android.tradefed.util.RunUtil;
import static android.app.AppExitReasonCode.REASON_OTHER;
import static android.app.AppExitReasonCode.REASON_PERMISSION_CHANGE;
import static android.app.AppExitSubReasonCode.SUBREASON_ISOLATED_NOT_NEEDED;
import static android.app.Importance.IMPORTANCE_CACHED;
import static android.app.Importance.IMPORTANCE_SERVICE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class AppExitHostTest extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String TEST_PKG = "android.app.cts.appexit";
    private static final String HELPER_PKG1 = "android.externalservice.service";
    private static final String HELPER_PKG2 = "com.android.cts.launcherapps.simpleapp";
    private static final String TEST_APK = "CtsAppExitTestCases.apk";
    private static final String HELPER_APK1 = "CtsExternalServiceService.apk";
    private static final String HELPER_APK2 = "CtsSimpleApp.apk";
    private static final String TEST_CLASS = "android.app.cts.ActivityManagerAppExitInfoTest";
    private static final long APP_EXIT_INFO_STATSD_LOG_DEBOUNCE_MSEC = 15_000;
    private static final String PERM_PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS";
    private static final String PERM_READ_LOGS = "android.permission.READ_LOGS";

    private IBuildInfo mCtsBuild;

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), HELPER_APK1, HELPER_PKG1, mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), HELPER_APK2, HELPER_PKG2, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        getDevice().executeShellCommand("cmd deviceidle tempwhitelist -r " + HELPER_PKG1);
        getDevice().executeShellCommand("cmd deviceidle tempwhitelist -r " + HELPER_PKG2);
        getDevice().executeShellCommand("pm grant " + TEST_PKG + " " + PERM_PACKAGE_USAGE_STATS);
        getDevice().executeShellCommand("pm grant " + TEST_PKG + " " + PERM_READ_LOGS);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        getDevice().executeShellCommand("pm revoke " + TEST_PKG + " " + PERM_PACKAGE_USAGE_STATS);
        getDevice().executeShellCommand("pm revoke " + TEST_PKG + " " + PERM_READ_LOGS);
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        DeviceUtils.uninstallTestApp(getDevice(), HELPER_PKG1);
        DeviceUtils.uninstallTestApp(getDevice(), HELPER_PKG2);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    public void testLogStatsdPermChanged() throws Exception {
        final String helperPackage = HELPER_PKG2;
        final int expectedUid = getAppUid(helperPackage);
        performLogStatsdTest("testPermissionChange", helperPackage, 1, appDied -> {
            assertThat(appDied.getUid()).isEqualTo(expectedUid);
            assertThat(appDied.getProcessName()).isEqualTo("");
            assertThat(appDied.getReason()).isEqualTo(REASON_PERMISSION_CHANGE);
            assertThat(appDied.getImportance()).isEqualTo(IMPORTANCE_CACHED);
            assertThat(appDied.getPss()).isAtLeast(0);
            assertThat(appDied.getRss()).isAtLeast(0);
        });
    }

    @Test
    public void testLogStatsdOther() throws Exception {
        final String helperPackage = HELPER_PKG1;
        final int expectedUid = getAppUid(TEST_PKG);
        performLogStatsdTest("testOther", TEST_PKG, 2, appDied -> {
            assertThat(appDied.getUid()).isEqualTo(expectedUid);
            assertThat(appDied.getProcessName()).isEqualTo(
                    helperPackage + ":android.externalservice.service.ExternalServiceWithZygote");
            assertThat(appDied.getReason()).isEqualTo(REASON_OTHER);
            assertThat(appDied.getSubReason()).isEqualTo(SUBREASON_ISOLATED_NOT_NEEDED);
            assertThat(appDied.getImportance()).isEqualTo(IMPORTANCE_SERVICE);
        });
    }

    private void performLogStatsdTest(String testMethod, String targetPackage, int expectedSize,
            Consumer<AtomsProto.AppProcessDied> verifier) throws Exception {
        final int atomTag = AtomsProto.Atom.APP_PROCESS_DIED_FIELD_NUMBER;

        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), targetPackage,
                atomTag, /* useUidAttributionChain= */ false);

        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, testMethod);
        RunUtil.getDefault().sleep(APP_EXIT_INFO_STATSD_LOG_DEBOUNCE_MSEC);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(expectedSize);

        verifier.accept(data.get(0).getAtom().getAppProcessDied());
    }

    private int getAppUid(String pkgName) throws Exception {
        final int currentUser = getDevice().getCurrentUser();
        final String uidLine = getDevice().executeShellCommand(
                "cmd package list packages -U --user " + currentUser + " " + pkgName);
        final Pattern pattern = Pattern.compile("package:" + pkgName + " uid:(\\d+)");
        final Matcher matcher = pattern.matcher(uidLine);
        assertWithMessage("Pkg not found: " + pkgName).that(matcher.find()).isTrue();
        final int appUid = Integer.parseInt(matcher.group(1));
        return appUid;
    }
}
