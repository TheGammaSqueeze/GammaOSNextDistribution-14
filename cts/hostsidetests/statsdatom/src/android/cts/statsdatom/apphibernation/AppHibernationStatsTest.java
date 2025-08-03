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

package android.cts.statsdatom.apphibernation;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class AppHibernationStatsTest extends DeviceTestCase implements IBuildReceiver {
    private static final String CMD_APP_HIBERNATION_SET_STATE =
            "cmd app_hibernation set-state %s %s %s";
    private static final String CMD_ENABLE_APP_HIBERNATION =
            "device_config put app_hibernation app_hibernation_enabled true";
    private static final String GLOBAL_OPTION = "--global";
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        getDevice().executeShellCommand(CMD_ENABLE_APP_HIBERNATION);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testUserLevelAppHibernationStateChanged_fromNotHibernatingToHibernating()
            throws Exception {
        getDevice().executeShellCommand(
                getHibernationCommand(DeviceUtils.STATSD_ATOM_TEST_PKG,
                        /* isGlobal */ false, /* isHibernating */ false));
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.USER_LEVEL_HIBERNATION_STATE_CHANGED_FIELD_NUMBER,
                /*uidInAttributionChain=*/false);

        getDevice().executeShellCommand(
                getHibernationCommand(DeviceUtils.STATSD_ATOM_TEST_PKG,
                        /* isGlobal */ false, /* isHibernating */ true));

        // Sorted list of events in order in which they occurred.
        final List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        assertUserLevelHibernationStateChangedEvent(data, /* isHibernating */ true,
                getDevice().getCurrentUser());
    }

    public void testUserLevelAppHibernationStateChanged_fromHibernatingToNotHibernating()
            throws Exception {
        getDevice().executeShellCommand(
                getHibernationCommand(DeviceUtils.STATSD_ATOM_TEST_PKG,
                        /* isGlobal */ false, /* isHibernating */ true));
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.USER_LEVEL_HIBERNATION_STATE_CHANGED_FIELD_NUMBER,
                /*uidInAttributionChain=*/false);

        getDevice().executeShellCommand(
                getHibernationCommand(DeviceUtils.STATSD_ATOM_TEST_PKG,
                        /* isGlobal */ false, /* isHibernating */ false));

        // Sorted list of events in order in which they occurred.
        final List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        assertUserLevelHibernationStateChangedEvent(data, /* isHibernating */ false,
                getDevice().getCurrentUser());
    }

    public void testUserLevelHibernatedApps() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.USER_LEVEL_HIBERNATED_APPS_FIELD_NUMBER);
        getDevice().executeShellCommand(
                getHibernationCommand(DeviceUtils.STATSD_ATOM_TEST_PKG,
                        /* isGlobal */ false, /* isHibernating */ true));

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        final List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        final int userId = getDevice().getCurrentUser();
        for (AtomsProto.Atom atom : atoms) {
            AtomsProto.UserLevelHibernatedApps apps = atom.getUserLevelHibernatedApps();
            if (apps.getUserId() == userId) {
                assertThat(apps.getHibernatedAppCount()).isAtLeast(1);
                return;
            }
        }
        fail(String.format("Did not find a matching atom for user %d", userId));
    }

    public void testGlobalHibernatedApps() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GLOBAL_HIBERNATED_APPS_FIELD_NUMBER);
        getDevice().executeShellCommand(
                getHibernationCommand(DeviceUtils.STATSD_ATOM_TEST_PKG,
                        /* isGlobal */ true, /* isHibernating */ true));

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        final List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(atoms.size()).isEqualTo(1);
        AtomsProto.GlobalHibernatedApps apps = atoms.get(0).getGlobalHibernatedApps();
        assertThat(apps.getHibernatedAppCount()).isAtLeast(1);
    }

    private static void assertUserLevelHibernationStateChangedEvent(
            List<StatsLog.EventMetricData> data, boolean isHibernating, int currentUser) {
        for (StatsLog.EventMetricData d : data) {
            AtomsProto.UserLevelHibernationStateChanged atom =
                    d.getAtom().getUserLevelHibernationStateChanged();
            if (atom.getPackageName().equals(DeviceUtils.STATSD_ATOM_TEST_PKG)) {
                assertThat(atom.getUserId()).isEqualTo(currentUser);
                assertThat(atom.getIsHibernating()).isEqualTo(isHibernating);
                return;
            }
        }
        fail(String.format("Did not find a matching event for package %s",
                DeviceUtils.STATSD_ATOM_TEST_PKG));
    }

    private static String getHibernationCommand(
            String packageName, boolean isGlobal, boolean isHibernating) {
        return String.format(CMD_APP_HIBERNATION_SET_STATE,
                (isGlobal ? GLOBAL_OPTION : ""), packageName, isHibernating);
    }
}
