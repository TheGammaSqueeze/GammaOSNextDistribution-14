/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.cts.statsdatom.voiceinteraction;

import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.TEST_APK;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.TEST_CLASS;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.TEST_PKG;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.getTestAppUid;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.isSupportedDevice;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.hotword.Enums;
import android.hotword.Enums.HotwordDetectorType;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.hotword.HotwordDetectionServiceRestarted;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for HotwordDetectionServiceRestarted logging.
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class HotwordDetectionServiceRestartedStatsTests extends DeviceTestCase implements
        IBuildReceiver {
    private static final String TEST_METHOD_DSP_SERVICE_SCHEDULE_RESTART_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_dspDetector_serviceScheduleRestarted";
    private static final String TEST_METHOD_SOFTWARE_SERVICE_SCHEDULE_RESTART_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_softwareDetector_serviceScheduleRestarted";

    protected IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!isSupportedDevice(getDevice())) return;

        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Upload config to collect HotwordDetectionServiceRestarted event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.HOTWORD_DETECTION_SERVICE_RESTARTED_FIELD_NUMBER);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        super.tearDown();
    }

    public void testLogHotwordDetectionServiceRestartedDspScheduleRestartService()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_SERVICE_SCHEDULE_RESTART_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        StatsLog.EventMetricData metric = filteredData.get(0);
        Enums.HotwordDetectorType detectorType =
                metric.getAtom().getHotwordDetectionServiceRestarted().getDetectorType();
        assertThat(detectorType).isEqualTo(Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP);

        HotwordDetectionServiceRestarted.Reason reason =
                metric.getAtom().getHotwordDetectionServiceRestarted().getReason();
        assertThat(reason).isEqualTo(HotwordDetectionServiceRestarted.Reason.SCHEDULE);
    }

    public void testLogHotwordDetectionServiceRestartedSoftwareScheduleRestartService()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_SERVICE_SCHEDULE_RESTART_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        StatsLog.EventMetricData metric = filteredData.get(0);
        Enums.HotwordDetectorType detectorType =
                metric.getAtom().getHotwordDetectionServiceRestarted().getDetectorType();
        assertThat(detectorType).isEqualTo(HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE);

        HotwordDetectionServiceRestarted.Reason reason =
                metric.getAtom().getHotwordDetectionServiceRestarted().getReason();
        assertThat(reason).isEqualTo(HotwordDetectionServiceRestarted.Reason.SCHEDULE);
    }

    private List<StatsLog.EventMetricData> filterTestAppMetrics(int appId,
            List<StatsLog.EventMetricData> metricData) {
        List<StatsLog.EventMetricData> data = new ArrayList<>();
        for (StatsLog.EventMetricData metric:  metricData) {
            if (metric.getAtom().getHotwordDetectionServiceRestarted().getUid() == appId) {
                data.add(metric);
            }
        }
        return data;
    }
}
