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

package android.cts.statsdatom.notification;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;

public class NotificationStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
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

    public void testNotificationPackagePreferenceExtraction() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_NOTIFICATION_PREFERENCES_FIELD_NUMBER);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.show_notification");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.PackageNotificationPreferences> allPreferences = new ArrayList<>();
        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            if (atom.hasPackageNotificationPreferences()) {
                allPreferences.add(atom.getPackageNotificationPreferences());
            }
        }
        assertThat(allPreferences.size()).isGreaterThan(0);

        boolean foundTestPackagePreferences = false;
        int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
        for (AtomsProto.PackageNotificationPreferences pref : allPreferences) {
            assertThat(pref.getUid()).isGreaterThan(0);
            assertTrue(pref.hasImportance());
            assertTrue(pref.hasVisibility());
            assertTrue(pref.hasUserLockedFields());
            if (pref.getUid() == uid) {
                assertThat(pref.getImportance()).isEqualTo(3);  //IMPORTANCE_EDULT
                assertThat(pref.getVisibility()).isEqualTo(-1000);  //UNSPECIFIED_VISIBILITY
                foundTestPackagePreferences = true;
            }
        }
        assertTrue(foundTestPackagePreferences);
    }

    public void testNotificationChannelPreferencesExtraction() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_NOTIFICATION_CHANNEL_PREFERENCES_FIELD_NUMBER);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.show_notification");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.PackageNotificationChannelPreferences> allChannelPreferences =
                new ArrayList<>();
        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            if (atom.hasPackageNotificationChannelPreferences()) {
                allChannelPreferences.add(atom.getPackageNotificationChannelPreferences());
            }
        }
        assertThat(allChannelPreferences.size()).isGreaterThan(0);

        boolean foundTestPackagePreferences = false;
        int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
        for (AtomsProto.PackageNotificationChannelPreferences pref : allChannelPreferences) {
            assertThat(pref.getUid()).isGreaterThan(0);
            assertTrue(pref.hasChannelId());
            assertTrue(pref.hasChannelName());
            assertTrue(pref.hasDescription());
            assertTrue(pref.hasImportance());
            assertTrue(pref.hasUserLockedFields());
            assertTrue(pref.hasIsDeleted());
            if (uid == pref.getUid() && pref.getChannelId().equals("StatsdCtsChannel")) {
                assertThat(pref.getChannelName()).isEqualTo("Statsd Cts");
                assertThat(pref.getDescription()).isEqualTo("Statsd Cts Channel");
                assertThat(pref.getImportance()).isEqualTo(3);  // IMPORTANCE_DEFAULT
                foundTestPackagePreferences = true;
            }
        }
        assertTrue(foundTestPackagePreferences);
    }

    public void testNotificationChannelGroupPreferencesExtraction() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_NOTIFICATION_CHANNEL_GROUP_PREFERENCES_FIELD_NUMBER);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.create_channel_group");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.PackageNotificationChannelGroupPreferences> allGroupPreferences =
                new ArrayList<>();
        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            if (atom.hasPackageNotificationChannelGroupPreferences()) {
                allGroupPreferences.add(atom.getPackageNotificationChannelGroupPreferences());
            }
        }
        assertThat(allGroupPreferences.size()).isGreaterThan(0);

        boolean foundTestPackagePreferences = false;
        int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
        for (AtomsProto.PackageNotificationChannelGroupPreferences pref : allGroupPreferences) {
            assertThat(pref.getUid()).isGreaterThan(0);
            assertTrue(pref.hasGroupId());
            assertTrue(pref.hasGroupName());
            assertTrue(pref.hasDescription());
            assertTrue(pref.hasIsBlocked());
            assertTrue(pref.hasUserLockedFields());
            if (uid == pref.getUid() && pref.getGroupId().equals("StatsdCtsGroup")) {
                assertThat(pref.getGroupName()).isEqualTo("Statsd Cts Group");
                assertThat(pref.getDescription()).isEqualTo("StatsdCtsGroup Description");
                assertThat(pref.getIsBlocked()).isFalse();
                foundTestPackagePreferences = true;
            }
        }
        assertTrue(foundTestPackagePreferences);
    }
}
