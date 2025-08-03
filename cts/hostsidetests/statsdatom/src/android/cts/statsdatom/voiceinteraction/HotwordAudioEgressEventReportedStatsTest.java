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

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.hotword.HotwordAudioEgressEventReported.Event;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for HotwordAudioEgressEventReported logging.
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class HotwordAudioEgressEventReportedStatsTest extends DeviceTestCase implements
        IBuildReceiver {

    protected IBuildInfo mCtsBuild;

    private static final String TEST_METHOD_DSP_STARTED_AND_ENDED =
            "testHotwordDetectionServiceDspWithAudioEgress";
    private static final String TEST_METHOD_DSP_EMPTY_AUDIO_STREAM_LIST =
            "testHotwordDetectionService_onDetectFromDsp_success";
    private static final String TEST_METHOD_DSP_ILLEGAL_COPY_BUFFER_SIZE =
            "testHotwordDetectionServiceDspWithAudioEgressWrongCopyBufferSize";
    private static final String TEST_METHOD_SOFTWARE_STARTED_AND_ENDED =
            "testHotwordDetectionService_softwareDetectorWithAudioEgress";
    private static final String TEST_METHOD_SOFTWARE_EMPTY_AUDIO_STREAM_LIST =
            "testHotwordDetectionService_onDetectFromMic_success";
    private static final String TEST_METHOD_SOFTWARE_ILLEGAL_COPY_BUFFER_SIZE =
            "testHotwordDetectionService_softwareDetectorWithAudioEgressWrongCopyBufferSize";
    private static final String TEST_METHOD_EXTERNAL_SOURCE_STARTED_AND_ENDED =
            "testHotwordDetectionService_onDetectFromExternalSourceWithAudioEgress";
    private static final String TEST_METHOD_EXTERNAL_SOURCE_EMPTY_AUDIO_STREAM_LIST =
            "testHotwordDetectionService_onDetectFromExternalSource_success";
    private static final String TEST_METHOD_EXTERNAL_SOURCE_ILLEGAL_COPY_BUFFER_SIZE =
            "testHotwordDetectionService_externalSourceWithAudioEgressWrongCopyBufferSize";

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

        // Upload config to collect HotwordAudioEgressEventReported event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.HOTWORD_AUDIO_EGRESS_EVENT_REPORTED_FIELD_NUMBER);
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

    public void testDspStartedAndEnded() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_STARTED_AND_ENDED);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(2);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.STARTED);
        assertHotwordAudioEgressEventReported(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ENDED);
    }

    public void testDspEmptyAudiostreamList() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_EMPTY_AUDIO_STREAM_LIST);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.EMPTY_AUDIO_STREAM_LIST);
    }

    public void testDspIllegalCopyBufferSize() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_ILLEGAL_COPY_BUFFER_SIZE);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(3);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ILLEGAL_COPY_BUFFER_SIZE);
        assertHotwordAudioEgressEventReported(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.STARTED);
        assertHotwordAudioEgressEventReported(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ENDED);
    }

    public void testSoftwareDetectorStartedAndEnded() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_STARTED_AND_ENDED);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(2);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.STARTED);
        assertHotwordAudioEgressEventReported(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ENDED);
    }

    public void testSoftwareDetectorEmptyAudiostreamList() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_EMPTY_AUDIO_STREAM_LIST);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.EMPTY_AUDIO_STREAM_LIST);
    }

    public void testSoftwareDetectorIllegalCopyBufferSize() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_ILLEGAL_COPY_BUFFER_SIZE);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(3);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.ILLEGAL_COPY_BUFFER_SIZE);
        assertHotwordAudioEgressEventReported(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.STARTED);
        assertHotwordAudioEgressEventReported(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ENDED);
    }

    public void testExternalSourceStartedAndEnded() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_EXTERNAL_SOURCE_STARTED_AND_ENDED);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(2);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.STARTED);
        assertHotwordAudioEgressEventReported(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ENDED);
    }

    public void testExternalSourceEmptyAudiostreamList() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_EXTERNAL_SOURCE_EMPTY_AUDIO_STREAM_LIST);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.EMPTY_AUDIO_STREAM_LIST);
    }

    public void testExternalSourceIllegalCopyBufferSize() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_EXTERNAL_SOURCE_ILLEGAL_COPY_BUFFER_SIZE);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(3);

        // Verify metric
        assertHotwordAudioEgressEventReported(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ILLEGAL_COPY_BUFFER_SIZE);
        assertHotwordAudioEgressEventReported(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.STARTED);
        assertHotwordAudioEgressEventReported(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ENDED);
    }

    private void assertHotwordAudioEgressEventReported(StatsLog.EventMetricData metric,
            Enums.HotwordDetectorType expectedDetectorType, Event expectedEvent) {
        Enums.HotwordDetectorType detectorType =
                metric.getAtom().getHotwordAudioEgressEventReported().getDetectorType();
        assertThat(detectorType).isEqualTo(expectedDetectorType);

        Event event = metric.getAtom().getHotwordAudioEgressEventReported().getEvent();
        assertThat(event).isEqualTo(expectedEvent);
    }

    private List<StatsLog.EventMetricData> filterTestAppMetrics(int appId,
            List<StatsLog.EventMetricData> metricData) {
        List<StatsLog.EventMetricData> data = new ArrayList<>();
        for (StatsLog.EventMetricData metric:  metricData) {
            if (metric.getAtom().getHotwordAudioEgressEventReported().getUid() == appId) {
                data.add(metric);
            }
        }
        return data;
    }
}
