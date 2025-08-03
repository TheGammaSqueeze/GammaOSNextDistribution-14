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

package android.host.systemui;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.AppModeFull;

import com.android.internal.os.StatsdConfigProto;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.Collections;
import java.util.List;

@AppModeFull(reason = "Flaky in Instant mode")
public class StatsdNotificationAtomTest extends DeviceTestCase implements IBuildReceiver {
    private static final String NOTIFICATION_TEST_APK = "CtsSystemUiDeviceApp.apk";
    private static final String NOTIFICATION_TEST_PKG = "android.systemui.cts";

    private static final String TEST_NOTIFICATION_ACTIVITY = "TestNotificationActivity";
    private static final String ACTION_KEY = "action";
    private static final String ACTION_VALUE = "action.show_notification";
    private static final String NOTIFICATION_CHANNEL_ID = "SystemUiCtsChannel";

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testNotificationReported() throws Exception {
        StatsdConfigProto.StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(
                NOTIFICATION_TEST_PKG);
        StatsdConfigProto.FieldValueMatcher.Builder fvm = ConfigUtils.createFvm(
                AtomsProto.NotificationReported.PACKAGE_NAME_FIELD_NUMBER).setEqString(
                NOTIFICATION_TEST_PKG);
        ConfigUtils.addEventMetric(config, AtomsProto.Atom.NOTIFICATION_REPORTED_FIELD_NUMBER,
                Collections.singletonList(fvm));
        ConfigUtils.uploadConfig(getDevice(), config);

        DeviceUtils.runActivity(getDevice(), NOTIFICATION_TEST_PKG,
                TEST_NOTIFICATION_ACTIVITY, ACTION_KEY, ACTION_VALUE);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAtom().hasNotificationReported()).isTrue();
        AtomsProto.NotificationReported n = data.get(0).getAtom().getNotificationReported();
        assertThat(n.getPackageName()).isEqualTo(NOTIFICATION_TEST_PKG);
        assertThat(n.getUid()).isEqualTo(DeviceUtils.getAppUid(getDevice(), NOTIFICATION_TEST_PKG));
        assertThat(n.getNotificationIdHash()).isEqualTo(0);  // smallHash(0x7F020000)
        assertThat(n.getChannelIdHash()).isEqualTo(SmallHash.hash(NOTIFICATION_CHANNEL_ID));
        assertThat(n.getGroupIdHash()).isEqualTo(0);
        assertFalse(n.getIsGroupSummary());
        assertThat(n.getCategory()).isEmpty();
        assertThat(n.getStyle()).isEqualTo(0);
        assertThat(n.getNumPeople()).isEqualTo(0);
    }
}
