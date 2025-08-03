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
import com.android.os.hotword.HotwordDetectorKeyphraseTriggered.Result;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for HotwordDetectorKeyphraseTriggered logging.
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class HotwordDetectorKeyphraseTriggeredStatsTest extends DeviceTestCase implements
        IBuildReceiver {

    protected IBuildInfo mCtsBuild;

    // Because the voice test may have some hardware dependency, some cases are hard to test. CTS
    // doesn't test all enum cases.
    private static final String TEST_METHOD_DSP_DETECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromDsp_success";
    private static final String TEST_METHOD_DSP_REJECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromDsp_rejection";
    private static final String TEST_METHOD_SOFTWARE_DETECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromMic_success";
    private static final String TEST_METHOD_DSP_UNEXPECTED_DETECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_dspDetector_onDetectedTwice_clientOnlyOneOnDetected";
    private static final String TEST_METHOD_SOFTWARE_UNEXPECTED_DETECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectedTwice_clientOnlyOneOnDetected";
    private static final String TEST_METHOD_DSP_UNEXPECTED_REJECTED_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_dspDetector_onRejectedTwice_clientOnlyOneOnRejected";
    private static final String TEST_METHOD_DSP_DETECT_TIMEOUT_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromDsp_timeout";
    private static final String TEST_METHOD_DSP_SERVICE_CRASH_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_processDied_triggerOnError";
    private static final String TEST_METHOD_SOFTWARE_SERVICE_CRASH_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_softwareDetector_processDied_triggerOnFailure";
    private static final String TEST_METHOD_DSP_DETECT_SECURITY_EXCEPTION_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromDspSecurityException_onFailure";
    private static final String TEST_METHOD_SOFTWARE_DETECT_SECURITY_EXCEPTION_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromMicSecurityException_onFailure";
    private static final String TEST_METHOD_DSP_REJECTED_FROM_RESTART_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_dspDetector_duringOnDetect_serviceRestart";

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

        // Upload config to collect HotwordDetectorKeyphraseTriggered event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.HOTWORD_DETECTOR_KEYPHRASE_TRIGGERED_FIELD_NUMBER);
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

    public void testLogHotwordDetectorKeyphraseTriggeredDspDetected() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_DETECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(2);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.KEYPHRASE_TRIGGER);
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.DETECTED);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredSoftwareDetectorDetected()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_DETECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Result.DETECTED);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredDspDetectorUnexpectedDetected()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_UNEXPECTED_DETECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(4);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.KEYPHRASE_TRIGGER);

        assertHotwordDetectorKeyphraseTriggered(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.DETECTED);

        assertHotwordDetectorKeyphraseTriggered(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.DETECTED);

        assertHotwordDetectorKeyphraseTriggered(filteredData.get(3),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.DETECT_UNEXPECTED_CALLBACK);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredSoftwareDetectorUnexpectedDetected()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_UNEXPECTED_DETECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(3);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Result.DETECTED);

        assertHotwordDetectorKeyphraseTriggered(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Result.DETECTED);

        assertHotwordDetectorKeyphraseTriggered(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Result.DETECT_UNEXPECTED_CALLBACK);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredDspDetectorUnexpectedRejected()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_UNEXPECTED_REJECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(3);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.REJECTED);

        assertHotwordDetectorKeyphraseTriggered(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.REJECTED);

        assertHotwordDetectorKeyphraseTriggered(filteredData.get(2),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.REJECT_UNEXPECTED_CALLBACK);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredDspRejected() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_REJECTED_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.REJECTED);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredDspDetectTimeout() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_DETECT_TIMEOUT_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.DETECT_TIMEOUT);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredDspDetectorServiceCrash() throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_SERVICE_CRASH_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.SERVICE_CRASH);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredSoftwareDetectorServiceCrash()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_SERVICE_CRASH_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(1);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Result.SERVICE_CRASH);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredDspDetectedSecurityException()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_DETECT_SECURITY_EXCEPTION_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(2);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.DETECTED);
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.DETECT_SECURITY_EXCEPTION);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredSoftwareDetectorDetectedSecurityException()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_DETECT_SECURITY_EXCEPTION_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isEqualTo(2);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, Result.DETECTED);
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE,
                Result.DETECT_SECURITY_EXCEPTION);
    }

    public void testLogHotwordDetectorKeyphraseTriggeredDspRejectedFromRestart()
            throws Exception {
        if (!isSupportedDevice(getDevice())) return;

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_REJECTED_FROM_RESTART_FOR_METRIC_COLLECT);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();

        int appId = getTestAppUid(getDevice());
        // After the voice CTS test executes completely, the test will switch to original VIS
        // Focus on our expected app metrics
        List<StatsLog.EventMetricData> filteredData = filterTestAppMetrics(appId, data);
        assertThat(filteredData.size()).isAtLeast(2);

        // Verify metric
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(0),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.KEYPHRASE_TRIGGER);
        assertHotwordDetectorKeyphraseTriggered(filteredData.get(1),
                Enums.HotwordDetectorType.TRUSTED_DETECTOR_DSP, Result.REJECTED_FROM_RESTART);
    }

    private void assertHotwordDetectorKeyphraseTriggered(StatsLog.EventMetricData metric,
            Enums.HotwordDetectorType expectedDetectorType, Result expectedResult) {
        Enums.HotwordDetectorType detectorType =
                metric.getAtom().getHotwordDetectorKeyphraseTriggered().getDetectorType();
        assertThat(detectorType).isEqualTo(expectedDetectorType);

        Result result = metric.getAtom().getHotwordDetectorKeyphraseTriggered().getResult();
        assertThat(result).isEqualTo(expectedResult);
    }

    private List<StatsLog.EventMetricData> filterTestAppMetrics(int appId,
            List<StatsLog.EventMetricData> metricData) {
        List<StatsLog.EventMetricData> data = new ArrayList<>();
        for (StatsLog.EventMetricData metric:  metricData) {
            if (metric.getAtom().getHotwordDetectorKeyphraseTriggered().getUid() == appId) {
                data.add(metric);
            }
        }
        return data;
    }
}
