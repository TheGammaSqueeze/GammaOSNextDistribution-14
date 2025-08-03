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

package android.appsecurity.cts;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Set of tests that verify atoms are correctly sent to statsd.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class StatsdAppSecurityAtomTest extends BaseHostJUnit4Test {
    private static final String STATSD_APP_APK = "CtsStatsSecurityApp.apk";
    private static final String STATSD_APP_PKG = "com.android.cts.statsdsecurityapp";

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        installPackage(STATSD_APP_APK);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        getDevice().uninstallPackage(STATSD_APP_PKG);
    }

    @Test
    public void testRoleHolder() throws Exception {
        // Make device side test package a role holder
        String callScreenAppRole = "android.app.role.CALL_SCREENING";
        getDevice().executeShellCommand(
                "cmd role add-role-holder " + callScreenAppRole + " "
                        + STATSD_APP_PKG);

        // Set up what to collect
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), STATSD_APP_PKG,
                AtomsProto.Atom.ROLE_HOLDER_FIELD_NUMBER);

        boolean verifiedKnowRoleState = false;

        // Pull a report
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        int testAppId = getAppId(DeviceUtils.getAppUid(getDevice(), STATSD_APP_PKG));

        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            AtomsProto.RoleHolder roleHolder = atom.getRoleHolder();

            assertThat(roleHolder.getPackageName()).isNotNull();
            assertThat(roleHolder.getUid()).isAtLeast(0);
            assertThat(roleHolder.getRole()).isNotNull();

            if (roleHolder.getPackageName().equals(STATSD_APP_PKG)) {
                assertThat(getAppId(roleHolder.getUid())).isEqualTo(testAppId);
                assertThat(roleHolder.getPackageName()).isEqualTo(STATSD_APP_PKG);
                assertThat(roleHolder.getRole()).isEqualTo(callScreenAppRole);

                verifiedKnowRoleState = true;
            }
        }

        assertThat(verifiedKnowRoleState).isTrue();
    }

    /**
     * The app id from a uid.
     *
     * @param uid The uid of the app
     * @return The app id of the app
     * @see android.os.UserHandle#getAppId
     */
    private static int getAppId(int uid) {
        return uid % 100000;
    }
}
