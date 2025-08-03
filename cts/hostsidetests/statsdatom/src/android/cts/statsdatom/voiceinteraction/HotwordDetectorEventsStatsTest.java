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
import com.android.os.hotword.HotwordDetectorEvents.Event;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for HotwordDetectorEvents logging.
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class HotwordDetectorEventsStatsTest extends DeviceTestCase implements IBuildReceiver {

    protected IBuildInfo mCtsBuild;

    private static final String TEST_METHOD_DSP_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_validHotwordDetectionComponentName_triggerSuccess";
    private static final String TEST_METHOD_DSP_APP_REQUEST_UPDATE_STATE_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_processDied_triggerOnError";
    private static final String TEST_METHOD_EXTERNAL_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromExternalSource_success";
    private static final String TEST_METHOD_SOFTWARE_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromMic_success";
    private static final String TEST_METHOD_SOFTWARE_APP_REQUEST_UPDATE_STATE_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectedTwice_clientOnlyOneOnDetected";
    private static final String TEST_METHOD_DSP_EXTERNAL_SECURITY_EXCEPTION_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromExternalSourceSecurityException_onFailure";
    private static final String TEST_METHOD_SW_EXTERNAL_SECURITY_EXCEPTION_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_software_externalSourceSecurityException_onFailure";
    private static final String TEST_METHOD_DSP_EXTERNAL_DETECTION_REJECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_dspDetector_onDetectFromExternalSource_rejected";
    private static final String TEST_METHOD_SW_EXTERNAL_DETECTION_REJECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_softwareDetector_onDetectFromExternalSource_rejected";

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

        // Upload config to collect HotwordDetectorEvents event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.HOTWORD_DETECTOR_EVENTS_FIELD_NUMBER);
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

    public void testLogHotwordDetectorEventsForDspDetection() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(4);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ON_DISCONNECTED);
    }

    public void testLogHotwordDetectorEventsConnectedAppUpdateState() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_APP_REQUEST_UPDATE_STATE_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        // If the testHotwordDetectionService_softwareDetector_serviceScheduleRestarted is run
        // before this test, the ON_DISCONNECTED will be logged, then there are 5 events here.
        // The test will only focus on the previous 4 events.
        assertThat(filteredData.size()).isAtLeast(4);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.APP_REQUEST_UPDATE_STATE);
    }

    public void testLogHotwordDetectorEventsForExternalDetection() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_EXTERNAL_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(6);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP,
                Event.START_EXTERNAL_SOURCE_DETECTION);
        assertHotwordDetectorType(filteredData.get(4),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.EXTERNAL_SOURCE_DETECTED);
        assertHotwordDetectorType(filteredData.get(5),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ON_DISCONNECTED);
    }

    public void testLogHotwordDetectorEventsForSoftwareDetection() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(5);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.START_SOFTWARE_DETECTION);
        assertHotwordDetectorType(filteredData.get(4),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ON_DISCONNECTED);
    }

    public void testLogHotwordDetectorEventsSoftwareConnectedAppUpdateState() throws Exception {
        if (!isSupportedDevice(getDevice())) {
            return;
        }

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_APP_REQUEST_UPDATE_STATE_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(6);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.APP_REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(4),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.START_SOFTWARE_DETECTION);
        assertHotwordDetectorType(filteredData.get(5),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ON_DISCONNECTED);
    }

    public void testLogHotwordDetectorEventsForDspDetectorExternalDetectionSecurityException()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_EXTERNAL_SECURITY_EXCEPTION_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(6);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP,
                Event.START_EXTERNAL_SOURCE_DETECTION);
        assertHotwordDetectorType(filteredData.get(4),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.EXTERNAL_SOURCE_DETECTED);
        assertHotwordDetectorType(filteredData.get(5),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP,
                Event.EXTERNAL_SOURCE_DETECT_SECURITY_EXCEPTION);
    }

    public void testLogHotwordDetectorEventsForSoftwareDetectorExternalDetectionSecurityException()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SW_EXTERNAL_SECURITY_EXCEPTION_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(6);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.START_EXTERNAL_SOURCE_DETECTION);
        assertHotwordDetectorType(filteredData.get(4),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.EXTERNAL_SOURCE_DETECTED);
        assertHotwordDetectorType(filteredData.get(5),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.EXTERNAL_SOURCE_DETECT_SECURITY_EXCEPTION);
    }

    public void testLogHotwordDetectorEventsForDspDetectorExternalDetectionRejected()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_EXTERNAL_DETECTION_REJECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(5);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP,
                Event.START_EXTERNAL_SOURCE_DETECTION);
        assertHotwordDetectorType(filteredData.get(4),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Event.EXTERNAL_SOURCE_REJECTED);
    }

    public void testLogHotwordDetectorEventsForSoftwareDetectorExternalDetectionRejected()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SW_EXTERNAL_DETECTION_REJECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);

        assertThat(filteredData.size()).isAtLeast(5);
        assertHotwordDetectorType(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_BIND_SERVICE);
        assertHotwordDetectorType(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.ON_CONNECTED);
        assertHotwordDetectorType(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Event.REQUEST_UPDATE_STATE);
        assertHotwordDetectorType(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.START_EXTERNAL_SOURCE_DETECTION);
        assertHotwordDetectorType(filteredData.get(4),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Event.EXTERNAL_SOURCE_REJECTED);
    }

    private List<StatsLog.EventMetricData> filterTestAppMetrics(int appId,
            List<StatsLog.EventMetricData> metricData) {
        List<StatsLog.EventMetricData> data = new ArrayList<>();
        for (StatsLog.EventMetricData metric:  metricData) {
            if (metric.getAtom().getHotwordDetectorEvents().getUid() == appId) {
                data.add(metric);
            }
        }
        return data;
    }

    private void assertHotwordDetectorType(StatsLog.EventMetricData metric,
            Enums.HotwordDetectorType expectedDetectorType, Event expectedEvent) {
        Enums.HotwordDetectorType detectorType =
                metric.getAtom().getHotwordDetectorEvents().getDetectorType();

        assertThat(detectorType).isEqualTo(expectedDetectorType);
        assertThat(metric.getAtom().getHotwordDetectorEvents().getEvent()).isEqualTo(expectedEvent);
    }
}