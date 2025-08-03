/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.mediaparser.cts;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.internal.os.StatsdConfigProto;
import com.android.internal.os.StatsdConfigProto.AtomMatcher;
import com.android.internal.os.StatsdConfigProto.SimpleAtomMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.MediametricsMediaParserReported;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import com.google.common.io.Files;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Test for checking that the MediaParser CTS tests produce the expected media metrics. */
public class MediaParserHostSideTest extends DeviceTestCase implements IBuildReceiver {

    private static final String MEDIAPARSER_TEST_APK = "CtsMediaParserTestCasesApp.apk";
    private static final String MEDIAPARSER_TEST_APP_PACKAGE = "android.media.mediaparser.cts";
    private static final String MEDIAPARSER_TEST_CLASS_NAME =
            "android.media.mediaparser.cts.MediaParserTest";
    private static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    private static final long CONFIG_ID = "cts_config".hashCode();
    private static final String MEDIAPARSER_METRICS_SEPARATOR = "\\|";
    private static final double MEDIAPARSER_METRICS_DITHER_VALUE = .02f;

    private IBuildInfo mCtsBuildInfo;

    // Resource management.

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuildInfo = buildInfo;
    }

    @Override
    public void setUp() throws Exception {
        File apk = new CompatibilityBuildHelper(mCtsBuildInfo).getTestFile(MEDIAPARSER_TEST_APK);
        assertThat(getDevice().installPackage(apk, /* reinstall= */ true)).isNull();
        removeConfig();
        createAndUploadConfig();
        getAndClearReportList(); // Clear existing reports.
    }

    @Override
    public void tearDown() throws Exception {
        removeConfig();
        getDevice().uninstallPackage(MEDIAPARSER_TEST_APP_PACKAGE);
    }

    // Tests.

    public void testDefaultLogSessionId() throws Exception {
        runDeviceTest("testDefaultLogSessionId");
        assertThat(getSingleMediaParserReportedEvent().getLogSessionId()).isEmpty();
    }

    public void testSetLogSessionId() throws Exception {
        runDeviceTest("testSetLogSessionId");
        assertThat(getSingleMediaParserReportedEvent().getLogSessionId()).isNotEmpty();
    }

    public void testCreationByNameMetrics() throws Exception {
        String[] expectedParserNames = {
            "android.media.mediaparser.MatroskaParser",
            "android.media.mediaparser.FragmentedMp4Parser",
            "android.media.mediaparser.Mp4Parser",
            "android.media.mediaparser.Mp3Parser",
            "android.media.mediaparser.AdtsParser",
            "android.media.mediaparser.Ac3Parser",
            "android.media.mediaparser.TsParser",
            "android.media.mediaparser.FlvParser",
            "android.media.mediaparser.OggParser",
            "android.media.mediaparser.PsParser",
            "android.media.mediaparser.WavParser",
            "android.media.mediaparser.AmrParser",
            "android.media.mediaparser.Ac4Parser",
            "android.media.mediaparser.FlacParser",
        };
        // All of the above are created by name.
        int[] expectedCreatedByName =
                Arrays.stream(expectedParserNames).mapToInt(unusedArgument -> 1).toArray();
        runDeviceTest("testCreationByName");
        List<MediametricsMediaParserReported> mediaParserReportedEvents =
                getMediaParserReportedEvents();
        String[] observedParserNames =
                mediaParserReportedEvents.stream()
                        .map(MediametricsMediaParserReported::getParserName)
                        .toArray(String[]::new);
        int[] observedCreatedByName =
                mediaParserReportedEvents.stream()
                        .mapToInt(MediametricsMediaParserReported::getCreatedByName)
                        .toArray();
        assertThat(observedParserNames).isEqualTo(expectedParserNames);
        assertThat(observedCreatedByName).isEqualTo(expectedCreatedByName);
    }

    public void testParserPool() throws Exception {
        runDeviceTest("testMp4");
        String[] expectedParserNamesInPool = {
            "android.media.mediaparser.MatroskaParser",
            "android.media.mediaparser.FragmentedMp4Parser",
            "android.media.mediaparser.Mp4Parser",
            "android.media.mediaparser.Mp3Parser",
            "android.media.mediaparser.AdtsParser",
            "android.media.mediaparser.Ac3Parser",
            "android.media.mediaparser.TsParser",
            "android.media.mediaparser.FlvParser",
            "android.media.mediaparser.OggParser",
            "android.media.mediaparser.PsParser",
            "android.media.mediaparser.WavParser",
            "android.media.mediaparser.AmrParser",
            "android.media.mediaparser.Ac4Parser",
            "android.media.mediaparser.FlacParser",
        };
        String parserPool = getSingleMediaParserReportedEvent().getParserPool();
        List<String> parserNamesInParserPool =
                Arrays.asList(parserPool.split(MEDIAPARSER_METRICS_SEPARATOR));
        // We do not assert the order in the pool in order to allow test robustness against future
        // mainline changes.
        assertThat(parserNamesInParserPool).containsExactlyElementsIn(expectedParserNamesInPool);
    }

    public void testLastException() throws Exception {
        runDeviceTest("testOggInvalidHeaderSniff");
        List<MediametricsMediaParserReported> mediaParserReportedEvents =
                getMediaParserReportedEvents();
        assertThat(mediaParserReportedEvents).hasSize(2);
        for (MediametricsMediaParserReported event : mediaParserReportedEvents) {
            assertThat(event.getLastException())
                    .isEqualTo("android.media.MediaParser$UnrecognizedInputFormatException");
        }
    }

    public void testResourceByteCount() throws Exception {
        long actualInputSize = 101597;
        long minimumExpectedResourceByteCount =
                (long) (actualInputSize * (1 - MEDIAPARSER_METRICS_DITHER_VALUE));
        long maximumExpectedResourceByteCount =
                (long) (actualInputSize * (1 + MEDIAPARSER_METRICS_DITHER_VALUE));
        runDeviceTest("testMp4");
        long reportedByteCount = getSingleMediaParserReportedEvent().getResourceByteCount();
        assertThat(reportedByteCount).isAtLeast(minimumExpectedResourceByteCount);
        assertThat(reportedByteCount).isAtMost(maximumExpectedResourceByteCount);
    }

    public void testDurationMillis() throws Exception {
        long actualDurationMillis = 1024;
        long minimumExpectedResourceByteCount =
                (long) (actualDurationMillis * (1 - MEDIAPARSER_METRICS_DITHER_VALUE));
        long maximumExpectedResourceByteCount =
                (long) (actualDurationMillis * (1 + MEDIAPARSER_METRICS_DITHER_VALUE));
        runDeviceTest("testMp4");
        long reportedDurationMillis = getSingleMediaParserReportedEvent().getDurationMillis();
        assertThat(reportedDurationMillis).isAtLeast(minimumExpectedResourceByteCount);
        assertThat(reportedDurationMillis).isAtMost(maximumExpectedResourceByteCount);
    }

    public void testTrackMimeTypes() throws Exception {
        String[] expectedTrackMimeTypes = new String[] {"video/avc", "audio/mp4a-latm"};
        runDeviceTest("testMp4");
        String trackMimeTypesField = getSingleMediaParserReportedEvent().getTrackMimeTypes();
        List<String> actualTrackMimeTypes =
                Arrays.asList(trackMimeTypesField.split(MEDIAPARSER_METRICS_SEPARATOR));
        assertThat(actualTrackMimeTypes).containsExactlyElementsIn(expectedTrackMimeTypes);
    }

    public void testTrackCodecs() throws Exception {
        runDeviceTest("testMp4");
        String trackCodecs = getSingleMediaParserReportedEvent().getTrackCodecs();
        List<String> actualTrackCodecs =
                Arrays.asList(trackCodecs.split(MEDIAPARSER_METRICS_SEPARATOR));
        // In one of MediaParser's updates, the backing ExoPlayer version added support for video
        // codec parsing, so this test must support both old and new MediaParser versions.
        // The old version would output an empty video codec string, and the new version reports the
        // correct "avc1.64001F" value. The test should pass only in these two cases.
        if (actualTrackCodecs.contains("avc1.64001F")) {
            assertThat(actualTrackCodecs).containsExactly("avc1.64001F", "mp4a.40.2");
        } else {
            assertThat(actualTrackCodecs).containsExactly("", "mp4a.40.2");
        }
    }

    public void testAlteredParameters() throws Exception {
        runDeviceTest("testTsWithH264DtsAudio");
        assertThat(getSingleMediaParserReportedEvent().getAlteredParameters())
                .isEqualTo("android.media.mediaparser.ts.enableHdmvDtsAudioStreams");
    }

    public void testVideoSize() throws Exception {
        runDeviceTest("testMp4");
        MediametricsMediaParserReported reportedEvent = getSingleMediaParserReportedEvent();
        assertThat(reportedEvent.getVideoWidth()).isEqualTo(1080);
        assertThat(reportedEvent.getVideoHeight()).isEqualTo(720);
    }

    // Internal methods.

    /** Creates the statsd config and passes it to statsd. */
    private void createAndUploadConfig() throws Exception {
        StatsdConfig.Builder configBuilder =
                StatsdConfigProto.StatsdConfig.newBuilder()
                        .setId(CONFIG_ID)
                        .addAllowedLogSource(MEDIAPARSER_TEST_APP_PACKAGE)
                        .addWhitelistedAtomIds(
                                AtomsProto.Atom.MEDIAMETRICS_MEDIAPARSER_REPORTED_FIELD_NUMBER);
        addAtomEvent(configBuilder);
        uploadConfig(configBuilder.build());
    }

    /** Removes any existing config with id {@link #CONFIG_ID}. */
    private void removeConfig() throws Exception {
        getDevice().executeShellCommand("cmd stats config remove " + CONFIG_ID);
    }

    /** Writes the given config into a file and passes is to statsd via standard input. */
    private void uploadConfig(StatsdConfig config) throws Exception {
        File configFile = File.createTempFile("statsdconfig", ".config");
        configFile.deleteOnExit();
        Files.write(config.toByteArray(), configFile);
        String remotePath = "/data/local/tmp/" + configFile.getName();
        // Make sure a config file with the same name doesn't exist already.
        getDevice().deleteFile(remotePath);
        assertThat(getDevice().pushFile(configFile, remotePath)).isTrue();
        getDevice()
                .executeShellCommand(
                        "cat " + remotePath + " | cmd stats config update " + CONFIG_ID);
        getDevice().deleteFile(remotePath);
    }

    /**
     * Asserts that there is only one MediaParser reported metric event, and returns it.
     *
     * <p>Note: Calls {@link #getAndClearReportList()} to obtain the statsd report.
     */
    private MediametricsMediaParserReported getSingleMediaParserReportedEvent() throws Exception {
        List<MediametricsMediaParserReported> mediaParserReportedEvents =
                getMediaParserReportedEvents();
        assertThat(mediaParserReportedEvents).hasSize(1);
        return mediaParserReportedEvents.get(0);
    }

    /**
     * Returns all MediaParser reported metric events sorted by timestamp.
     *
     * <p>Note: Calls {@link #getAndClearReportList()} to obtain the statsd report.
     */
    private List<MediametricsMediaParserReported> getMediaParserReportedEvents() throws Exception {
        ConfigMetricsReportList reportList = getAndClearReportList();
        assertThat(reportList.getReportsCount()).isEqualTo(1);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(reportList);
        return data.stream()
                .map(event -> event.getAtom().getMediametricsMediaparserReported())
                .collect(Collectors.toList());
    }

    /** Gets a statsd report and removes it from the device. */
    private ConfigMetricsReportList getAndClearReportList() throws Exception {
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        getDevice()
                .executeShellCommand(
                        "cmd stats dump-report " + CONFIG_ID + " --include_current_bucket --proto",
                        receiver);
        return ConfigMetricsReportList.parser().parseFrom(receiver.getOutput());
    }

    /** Runs the test with the given name from the MediaParser CTS apk. */
    private void runDeviceTest(String testMethodName) throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner =
                new RemoteAndroidTestRunner(
                        MEDIAPARSER_TEST_APP_PACKAGE, TEST_RUNNER, getDevice().getIDevice());
        testRunner.setMethodName(MEDIAPARSER_TEST_CLASS_NAME, testMethodName);
        CollectingTestListener listener = new CollectingTestListener();
        assertThat(getDevice().runInstrumentationTests(testRunner, listener)).isTrue();
        TestRunResult result = listener.getCurrentRunResults();
        assertThat(result.isRunFailure()).isFalse();
        assertThat(result.getNumTests()).isEqualTo(1);
        assertThat(result.hasFailedTests()).isFalse();
    }

    /** Adds an event to the config in order to match MediaParser reported atoms. */
    private static void addAtomEvent(StatsdConfig.Builder config) {
        String atomName = "Atom" + System.nanoTime();
        String eventName = "Event" + System.nanoTime();
        SimpleAtomMatcher.Builder sam =
                SimpleAtomMatcher.newBuilder()
                        .setAtomId(AtomsProto.Atom.MEDIAMETRICS_MEDIAPARSER_REPORTED_FIELD_NUMBER);
        config.addAtomMatcher(
                AtomMatcher.newBuilder().setId(atomName.hashCode()).setSimpleAtomMatcher(sam));
        config.addEventMetric(
                StatsdConfigProto.EventMetric.newBuilder()
                        .setId(eventName.hashCode())
                        .setWhat(atomName.hashCode()));
    }
}
