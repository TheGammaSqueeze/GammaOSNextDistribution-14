/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.cts.statsdatom.statsd;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.AppOpEnum;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.WakeLockLevelEnum;
import android.server.ErrorSource;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.internal.os.StatsdConfigProto.FieldValueMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.ANROccurred;
import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.android.os.AtomsProto.AppCrashOccurred;
import com.android.os.AtomsProto.AppUsageEventOccurred;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.AudioStateChanged;
import com.android.os.AtomsProto.CameraStateChanged;
import com.android.os.AtomsProto.FlashlightStateChanged;
import com.android.os.AtomsProto.ForegroundServiceAppOpSessionEnded;
import com.android.os.AtomsProto.ForegroundServiceStateChanged;
import com.android.os.AtomsProto.LmkKillOccurred;
import com.android.os.AtomsProto.MediaCodecStateChanged;
import com.android.os.AtomsProto.OverlayStateChanged;
import com.android.os.AtomsProto.SyncStateChanged;
import com.android.os.AtomsProto.TestAtomReported;
import com.android.os.AtomsProto.UiEventReported;
import com.android.os.AtomsProto.WakelockStateChanged;
import com.android.os.AttributionNode;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.gps.GpsScanStateChanged;
import com.android.os.statsd.StatsdExtensionAtoms;
import com.android.os.statsd.StatsdExtensionAtoms.TestExtensionAtomReported;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.Pair;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Statsd atom tests that are done via app, for atoms that report a uid.
 */
public class UidAtomTests extends DeviceTestCase implements IBuildReceiver {

    private static final String TAG = "Statsd.UidAtomTests";

    private static final String TEST_PACKAGE_NAME = "com.android.server.cts.device.statsd";

    private static final String ACTION_SHOW_APPLICATION_OVERLAY = "action.show_application_overlay";

    private static final String FEATURE_AUDIO_OUTPUT = "android.hardware.audio.output";
    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_CAMERA_FLASH = "android.hardware.camera.flash";
    private static final String FEATURE_CAMERA_FRONT = "android.hardware.camera.front";
    private static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";
    private static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    private static final String FEATURE_PICTURE_IN_PICTURE = "android.software.picture_in_picture";
    private static final String FEATURE_TV = "android.hardware.type.television";

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

    /**
     * Tests that statsd correctly maps isolated uids to host uids by verifying that atoms logged
     * from an isolated process are seen as coming from their host process.
     */
    public void testIsolatedToHostUidMapping() throws Exception {
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER, /*uidInAttributionChain=*/false);

        // Create an isolated service from which an AppBreadcrumbReported atom is logged.
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                "testIsolatedProcessService");

        // Verify correctness of data.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(1);
        AppBreadcrumbReported atom = data.get(0).getAtom().getAppBreadcrumbReported();
        assertThat(atom.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(atom.getLabel()).isEqualTo(0);
        assertThat(atom.getState()).isEqualTo(AppBreadcrumbReported.State.START);
    }

    private boolean shouldTestLmkdStats() throws Exception {
        boolean hasKernel = DeviceUtils.isKernelGreaterEqual(getDevice(), Pair.create(4, 19));
        boolean hasFirstApiLevel = PropertyUtil.getFirstApiLevel(getDevice()) > 30;
        return (hasKernel && hasFirstApiLevel)
                || "true".equals(DeviceUtils.getProperty(getDevice(), "ro.lmk.log_stats"));
    }

    public void testLmkKillOccurred() throws Exception {
        if (!shouldTestLmkdStats()) {
            LogUtil.CLog.d("Skipping lmkd stats test.");
            return;
        }

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.LMK_KILL_OCCURRED_FIELD_NUMBER,  /*uidInAttributionChain=*/false);
        int appUid = DeviceUtils.getStatsdTestAppUid(getDevice());

        // Start the victim process (service running in process :lmk_victim)
        // We rely on a victim process (instead of expecting the allocating process to die)
        // because 1. it is likely to be less flaky (higher oom score processes will be killed
        // faster, making less likely for the OOM reaper to trigger and 2. we need two processes
        // to be able to force evictions on 32-bit userspace devices with > 4 GB RAM.
        DeviceUtils.executeServiceAction(getDevice(), "LmkVictimBackgroundService",
                "action.allocate_memory");
        // Start fg activity and allocate
        try (AutoCloseable a = DeviceUtils.withActivity(
                getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.lmk")) {
            // Sorted list of events in order in which they occurred.
            List<LmkKillOccurred> atoms = null;
            for (int i = 0; i < 60; ++i) {
                RunUtil.getDefault().sleep(1_000);
                atoms = ReportUtils.getEventMetricDataList(getDevice()).stream()
                        .map(EventMetricData::getAtom)
                        .filter(Atom::hasLmkKillOccurred)
                        .map(Atom::getLmkKillOccurred)
                        .filter(atom -> atom.getUid() == appUid)
                        .collect(Collectors.toList());
                if (!atoms.isEmpty()) {
                    break;
                }
            }

            assertThat(atoms).isNotEmpty();
            // Even though both processes might have died, the non-fg one (victim)
            // must have been first.
            assertThat(atoms.get(0).getProcessName())
                    .isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG + ":lmk_victim");
            assertThat(atoms.get(0).getOomAdjScore()).isGreaterThan(0);
            assertThat(atoms.get(0).getRssInBytes() + atoms.get(0).getSwapInBytes())
                    .isGreaterThan(0);
        }
    }

    public void testAppCrashOccurred() throws Exception {
        final int atomTag = Atom.APP_CRASH_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.crash");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        AppCrashOccurred atom = data.get(0).getAtom().getAppCrashOccurred();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        assertThat(atom.getEventType()).isEqualTo("crash");
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(AppCrashOccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(AppCrashOccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        assertFalse(atom.getIsIncremental());
        assertTrue((1 - atom.getLoadingProgress()) < 0.001);
        assertEquals(-1, atom.getMillisSinceOldestPendingRead());
    }

    public void testAppCrashOccurredNative() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_TV)
                && DeviceUtils.isDebuggable(getDevice())) {
            // Skip TVs that are debuggable because ActivityManager does not properly terminate
            // the activity in the event of a native crash.
            return;
        }
        final int atomTag = Atom.APP_CRASH_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.native_crash",
                /* waitTimeMs= */ 5000L);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        AppCrashOccurred atom = data.get(0).getAtom().getAppCrashOccurred();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        assertThat(atom.getEventType()).isEqualTo("native_crash");
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(AppCrashOccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(AppCrashOccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        // TODO(b/172866626): add tests for incremental packages that crashed during loading
        assertFalse(atom.getIsIncremental());
        assertTrue((1 - atom.getLoadingProgress()) < 0.001);
        assertEquals(-1, atom.getMillisSinceOldestPendingRead());
    }


    public void testAudioState() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_AUDIO_OUTPUT)) return;

        final int atomTag = Atom.AUDIO_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testAudioState";

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(AudioStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(AudioStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<Integer> atomStates = data.stream().map(
                eventMetricData -> eventMetricData.getAtom().getAudioStateChanged()
                                .getState().getNumber())
                .collect(Collectors.toList());
        // Because the timestamp is truncated, we skip checking time differences between state
        // changes.
        assertThat(data.size()).isEqualTo(2);
        assertThat(new ArrayList<>(Arrays.asList(AudioStateChanged.State.ON_VALUE,
                AudioStateChanged.State.OFF_VALUE))).containsExactlyElementsIn(atomStates);
        // Check that timestamp is truncated
        for (EventMetricData metric : data) {
            long elapsedTimestampNs = metric.getElapsedTimestampNanos();
            AtomTestUtils.assertTimestampIsTruncated(elapsedTimestampNs);
        }
    }

    public void testCameraState() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_CAMERA) && !DeviceUtils.hasFeature(
                getDevice(), FEATURE_CAMERA_FRONT)) {
            return;
        }

        final int atomTag = Atom.CAMERA_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> cameraOn = new HashSet<>(Arrays.asList(CameraStateChanged.State.ON_VALUE));
        Set<Integer> cameraOff = new HashSet<>(Arrays.asList(CameraStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(cameraOn, cameraOff);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useAttributionChain=*/ true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testCameraState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_LONG,
                atom -> atom.getCameraStateChanged().getState().getNumber());
    }

    public void testDeviceCalculatedPowerUse() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) {
            // Skip TVs because they do not have batteries.
            return;
        }
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_LEANBACK_ONLY)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.DEVICE_CALCULATED_POWER_USE_FIELD_NUMBER);
        DeviceUtils.unplugDevice(getDevice());

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        Atom atom = ReportUtils.getGaugeMetricAtoms(getDevice()).get(0);
        assertThat(atom.getDeviceCalculatedPowerUse().getComputedPowerNanoAmpSecs())
                .isGreaterThan(0L);
        DeviceUtils.resetBatteryStatus(getDevice());
    }

    public void testFlashlightState() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_CAMERA_FLASH)) return;

        final int atomTag = Atom.FLASHLIGHT_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testFlashlight";

        Set<Integer> flashlightOn = new HashSet<>(
                Arrays.asList(FlashlightStateChanged.State.ON_VALUE));
        Set<Integer> flashlightOff = new HashSet<>(
                Arrays.asList(FlashlightStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(flashlightOn, flashlightOff);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getFlashlightStateChanged().getState().getNumber());
    }

    public void testForegroundServiceState() throws Exception {
        final int atomTag = Atom.FOREGROUND_SERVICE_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testForegroundService";

        Set<Integer> enterForeground = new HashSet<>(
                Arrays.asList(ForegroundServiceStateChanged.State.ENTER_VALUE));
        Set<Integer> exitForeground = new HashSet<>(
                Arrays.asList(ForegroundServiceStateChanged.State.EXIT_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(enterForeground, exitForeground);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getForegroundServiceStateChanged().getState().getNumber());
    }


    public void testForegroundServiceAccessAppOp() throws Exception {
        final int atomTag = Atom.FOREGROUND_SERVICE_APP_OP_SESSION_ENDED_FIELD_NUMBER;
        final String name = "testForegroundServiceAccessAppOp";

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertWithMessage("Wrong atom size").that(data.size()).isEqualTo(3);
        for (int i = 0; i < data.size(); i++) {
            ForegroundServiceAppOpSessionEnded atom
                    = data.get(i).getAtom().getForegroundServiceAppOpSessionEnded();
            final int opName = atom.getAppOpName().getNumber();
            final int acceptances = atom.getCountOpsAccepted();
            final int rejections = atom.getCountOpsRejected();
            final int count = acceptances + rejections;
            int expectedCount = 0;
            switch (opName) {
                case AppOpEnum.APP_OP_CAMERA_VALUE:
                    expectedCount = 3;
                    break;
                case AppOpEnum.APP_OP_FINE_LOCATION_VALUE:
                    expectedCount = 1;
                    break;
                case AppOpEnum.APP_OP_RECORD_AUDIO_VALUE:
                    expectedCount = 2;
                    break;
                case AppOpEnum.APP_OP_COARSE_LOCATION_VALUE:
                    // fall-through
                default:
                    fail("Unexpected opName " + opName);
            }
            assertWithMessage("Wrong count for " + opName).that(count).isEqualTo(expectedCount);
        }
    }

    public void testGpsScan() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_LOCATION_GPS)) return;
        // Allowlist this app against background location request throttling
        String origWhitelist = getDevice().executeShellCommand(
                "settings get global location_background_throttle_package_whitelist").trim();
        getDevice().executeShellCommand(String.format(
                "settings put global location_background_throttle_package_whitelist %s",
                DeviceUtils.STATSD_ATOM_TEST_PKG));

        try {
            final int atom = Atom.GPS_SCAN_STATE_CHANGED_FIELD_NUMBER;
            final int key = GpsScanStateChanged.STATE_FIELD_NUMBER;
            final int stateOn = GpsScanStateChanged.State.ON_VALUE;
            final int stateOff = GpsScanStateChanged.State.OFF_VALUE;
            final int minTimeDiffMillis = 500;
            final int maxTimeDiffMillis = 60_000;

            ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(),
                    DeviceUtils.STATSD_ATOM_TEST_PKG,
                    atom, /*useUidAttributionChain=*/true);

            DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                    "testGpsScan");

            List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
            assertThat(data).hasSize(2);
            GpsScanStateChanged a0 = data.get(0).getAtom().getGpsScanStateChanged();
            GpsScanStateChanged a1 = data.get(1).getAtom().getGpsScanStateChanged();
            AtomTestUtils.assertTimeDiffBetween(data.get(0), data.get(1), minTimeDiffMillis,
                    maxTimeDiffMillis);
            assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
            assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
        } finally {
            if ("null".equals(origWhitelist) || "".equals(origWhitelist)) {
                getDevice().executeShellCommand(
                        "settings delete global location_background_throttle_package_whitelist");
            } else {
                getDevice().executeShellCommand(String.format(
                        "settings put global location_background_throttle_package_whitelist %s",
                        origWhitelist));
            }
        }
    }

    public void testMediaCodecActivity() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH)) return;
        final int atomTag = Atom.MEDIA_CODEC_STATE_CHANGED_FIELD_NUMBER;

        // 5 seconds. Starting video tends to be much slower than most other
        // tests on slow devices. This is unfortunate, because it leaves a
        // really big slop in assertStatesOccurred.  It would be better if
        // assertStatesOccurred had a tighter range on large timeouts.
        final int waitTime = 5000;

        // From {@link VideoPlayerActivity#DELAY_MILLIS}
        final int videoDuration = 2000;

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(MediaCodecStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(MediaCodecStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "VideoPlayerActivity", "action", "action.play_video",
                waitTime);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, videoDuration,
                atom -> atom.getMediaCodecStateChanged().getState().getNumber());
    }

    public void testOverlayState() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH)) return;
        final int atomTag = Atom.OVERLAY_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> entered = new HashSet<>(
                Arrays.asList(OverlayStateChanged.State.ENTERED_VALUE));
        Set<Integer> exited = new HashSet<>(
                Arrays.asList(OverlayStateChanged.State.EXITED_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(entered, exited);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.show_application_overlay",
                5_000);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        // The overlay box should appear about 2sec after the app start
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, 0,
                atom -> atom.getOverlayStateChanged().getState().getNumber());
    }

    public void testPictureInPictureState() throws Exception {
        String supported = getDevice().executeShellCommand("am supports-multiwindow");
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH) ||
                !DeviceUtils.hasFeature(getDevice(), FEATURE_PICTURE_IN_PICTURE) ||
                !supported.contains("true")) {
            LogUtil.CLog.d("Skipping picture in picture atom test.");
            return;
        }

        StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        FieldValueMatcher.Builder uidFvm = ConfigUtils.createUidFvm(/*uidInAttributionChain=*/false,
                DeviceUtils.STATSD_ATOM_TEST_PKG);

        // PictureInPictureStateChanged atom is used prior to rvc-qpr
        ConfigUtils.addEventMetric(config, Atom.PICTURE_IN_PICTURE_STATE_CHANGED_FIELD_NUMBER,
                Collections.singletonList(uidFvm));
        // Picture-in-picture logs' been migrated to UiEvent since rvc-qpr
        FieldValueMatcher.Builder pkgMatcher = ConfigUtils.createFvm(
                        UiEventReported.PACKAGE_NAME_FIELD_NUMBER)
                .setEqString(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetric(config, Atom.UI_EVENT_REPORTED_FIELD_NUMBER,
                Arrays.asList(pkgMatcher));
        ConfigUtils.uploadConfig(getDevice(), config);

        LogUtil.CLog.d("Playing video in Picture-in-Picture mode");
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "VideoPlayerActivity", "action", "action.play_video_picture_in_picture_mode");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Filter out the PictureInPictureStateChanged and UiEventReported atom
        List<EventMetricData> pictureInPictureStateChangedData = data.stream()
                .filter(e -> e.getAtom().hasPictureInPictureStateChanged())
                .collect(Collectors.toList());
        List<EventMetricData> uiEventReportedData = data.stream()
                .filter(e -> e.getAtom().hasUiEventReported())
                .collect(Collectors.toList());

        assertThat(pictureInPictureStateChangedData).isEmpty();
        assertThat(uiEventReportedData).isNotEmpty();

        // See PipUiEventEnum for definitions
        final int enterPipEventId = 603;
        // Assert that log for entering PiP happens exactly once, we do not use
        // assertStateOccurred here since PiP may log something else when activity finishes.
        List<EventMetricData> entered = uiEventReportedData.stream()
                .filter(e -> e.getAtom().getUiEventReported().getEventId() == enterPipEventId)
                .collect(Collectors.toList());
        assertThat(entered).hasSize(1);
    }

    //Note: this test does not have uid, but must run on the device
    public void testScreenBrightness() throws Exception {
        int initialBrightness = getScreenBrightness();
        boolean isInitialManual = isScreenBrightnessModeManual();
        setScreenBrightnessMode(true);
        setScreenBrightness(150);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        final int atomTag = Atom.SCREEN_BRIGHTNESS_CHANGED_FIELD_NUMBER;

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testScreenBrightness");

        List<Integer> expectedValues = Arrays.asList(47, 100);

        // Sorted list of brightness values in order in which they occurred, filtered to only
        // contain expectedValues if they are present.
        List<Integer> data = ReportUtils.getEventMetricDataList(getDevice())
                .stream()
                .map(e -> e.getAtom().getScreenBrightnessChanged().getLevel())
                .filter(expectedValues::contains)
                .collect(Collectors.toList());

        // Restore initial screen brightness
        setScreenBrightness(initialBrightness);
        setScreenBrightnessMode(isInitialManual);

        assertThat(data).containsExactlyElementsIn(expectedValues).inOrder();
    }

    public void testSyncState() throws Exception {
        final int atomTag = Atom.SYNC_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> syncOn = new HashSet<>(Arrays.asList(SyncStateChanged.State.ON_VALUE));
        Set<Integer> syncOff = new HashSet<>(Arrays.asList(SyncStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(syncOn, syncOff, syncOn, syncOff);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.allowImmediateSyncs(getDevice());
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSyncState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data,
                /* wait = */ 0 /* don't verify time differences between state changes */,
                atom -> atom.getSyncStateChanged().getState().getNumber());
    }

    public void testWakelockState() throws Exception {
        final int atomTag = Atom.WAKELOCK_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> wakelockOn = new HashSet<>(Arrays.asList(
                WakelockStateChanged.State.ACQUIRE_VALUE,
                WakelockStateChanged.State.CHANGE_ACQUIRE_VALUE));
        Set<Integer> wakelockOff = new HashSet<>(Arrays.asList(
                WakelockStateChanged.State.RELEASE_VALUE,
                WakelockStateChanged.State.CHANGE_RELEASE_VALUE));

        final String EXPECTED_TAG = "StatsdPartialWakelock";
        final WakeLockLevelEnum EXPECTED_LEVEL = WakeLockLevelEnum.PARTIAL_WAKE_LOCK;

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(wakelockOn, wakelockOff);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWakelockState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getWakelockStateChanged().getState().getNumber());

        for (EventMetricData event : data) {
            String tag = event.getAtom().getWakelockStateChanged().getTag();
            WakeLockLevelEnum type = event.getAtom().getWakelockStateChanged().getType();
            assertThat(tag).isEqualTo(EXPECTED_TAG);
            assertThat(type).isEqualTo(EXPECTED_LEVEL);
        }
    }

    public void testANROccurred() throws Exception {
        final int atomTag = Atom.ANR_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "ANRActivity", null, null)) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG * 2);
            getDevice().executeShellCommand(
                    "am broadcast -a action_anr -p " + DeviceUtils.STATSD_ATOM_TEST_PKG);
            RunUtil.getDefault().sleep(20_000);
        }

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAtom().hasAnrOccurred()).isTrue();
        ANROccurred atom = data.get(0).getAtom().getAnrOccurred();
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(ANROccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(ANROccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertFalse(atom.getIsIncremental());
        assertTrue((1 - atom.getLoadingProgress()) < 0.001);
        assertEquals(-1, atom.getMillisSinceOldestPendingRead());
    }

    public void testWriteExtensionTestAtom() throws Exception {
        final int atomTag = StatsdExtensionAtoms.TEST_EXTENSION_ATOM_REPORTED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                "testWriteExtensionTestAtom");

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        StatsdExtensionAtoms.registerAllExtensions(registry);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);
        assertThat(data).hasSize(4);

        TestExtensionAtomReported atom = data.get(0).getAtom().getExtension(
                StatsdExtensionAtoms.testExtensionAtomReported);
        List<AttributionNode> attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(1234);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");
        assertThat(attrChain.get(1).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(1).getTag()).isEqualTo("tag2");

        assertThat(atom.getIntField()).isEqualTo(42);
        assertThat(atom.getLongField()).isEqualTo(Long.MAX_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(3.14f);
        assertThat(atom.getStringField()).isEqualTo("This is a basic test!");
        assertThat(atom.getBooleanField()).isFalse();
        assertThat(atom.getState().getNumber()).isEqualTo(TestExtensionAtomReported.State.ON_VALUE);
        assertThat(atom.getBytesField().getLongFieldList())
                .containsExactly(1L, 2L, 3L).inOrder();

        assertThat(atom.getRepeatedIntFieldList()).containsExactly(3, 6).inOrder();
        assertThat(atom.getRepeatedLongFieldList()).containsExactly(1000L, 1002L).inOrder();
        assertThat(atom.getRepeatedFloatFieldList()).containsExactly(0.3f, 0.09f).inOrder();
        assertThat(atom.getRepeatedStringFieldList()).containsExactly("str1", "str2").inOrder();
        assertThat(atom.getRepeatedBooleanFieldList()).containsExactly(true, false).inOrder();
        assertThat(atom.getRepeatedEnumFieldList())
                .containsExactly(TestExtensionAtomReported.State.OFF,
                    TestExtensionAtomReported.State.ON).inOrder();

        atom = data.get(1).getAtom().getExtension(
            StatsdExtensionAtoms.testExtensionAtomReported);
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(9999);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag9999");
        assertThat(attrChain.get(1).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(1).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(100);
        assertThat(atom.getLongField()).isEqualTo(Long.MIN_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(-2.5f);
        assertThat(atom.getStringField()).isEqualTo("Test null uid");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(
                TestExtensionAtomReported.State.UNKNOWN_VALUE);
        assertThat(atom.getBytesField().getLongFieldList())
                .containsExactly(1L, 2L, 3L).inOrder();
        assertThat(atom.getRepeatedIntFieldList()).containsExactly(3, 6).inOrder();
        assertThat(atom.getRepeatedLongFieldList()).containsExactly(1000L, 1002L).inOrder();
        assertThat(atom.getRepeatedFloatFieldList()).containsExactly(0.3f, 0.09f).inOrder();
        assertThat(atom.getRepeatedStringFieldList()).containsExactly("str1", "str2").inOrder();
        assertThat(atom.getRepeatedBooleanFieldList()).containsExactly(true, false).inOrder();
        assertThat(atom.getRepeatedEnumFieldList())
                .containsExactly(TestExtensionAtomReported.State.OFF,
                        TestExtensionAtomReported.State.ON)
                .inOrder();

        atom = data.get(2).getAtom().getExtension(
            StatsdExtensionAtoms.testExtensionAtomReported);
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");

        assertThat(atom.getIntField()).isEqualTo(-256);
        assertThat(atom.getLongField()).isEqualTo(-1234567890L);
        assertThat(atom.getFloatField()).isEqualTo(42.01f);
        assertThat(atom.getStringField()).isEqualTo("Test non chained");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(
                TestExtensionAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getLongFieldList())
                .containsExactly(1L, 2L, 3L).inOrder();
        assertThat(atom.getRepeatedIntFieldList()).isEmpty();
        assertThat(atom.getRepeatedLongFieldList()).isEmpty();
        assertThat(atom.getRepeatedFloatFieldList()).isEmpty();
        assertThat(atom.getRepeatedStringFieldList()).isEmpty();
        assertThat(atom.getRepeatedBooleanFieldList()).isEmpty();
        assertThat(atom.getRepeatedEnumFieldList()).isEmpty();

        atom = data.get(3).getAtom().getExtension(
            StatsdExtensionAtoms.testExtensionAtomReported);
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(0).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(0);
        assertThat(atom.getLongField()).isEqualTo(0L);
        assertThat(atom.getFloatField()).isEqualTo(0f);
        assertThat(atom.getStringField()).isEmpty();
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(
                TestExtensionAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getLongFieldList()).isEmpty();
        assertThat(atom.getRepeatedIntFieldList()).isEmpty();
        assertThat(atom.getRepeatedLongFieldList()).isEmpty();
        assertThat(atom.getRepeatedFloatFieldList()).isEmpty();
        assertThat(atom.getRepeatedStringFieldList()).isEmpty();
        assertThat(atom.getRepeatedBooleanFieldList()).isEmpty();
        assertThat(atom.getRepeatedEnumFieldList()).isEmpty();
    }

    public void testWriteRawTestAtom() throws Exception {
        final int atomTag = Atom.TEST_ATOM_REPORTED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWriteRawTestAtom");

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(4);

        TestAtomReported atom = data.get(0).getAtom().getTestAtomReported();
        List<AttributionNode> attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(1234);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");
        assertThat(attrChain.get(1).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(1).getTag()).isEqualTo("tag2");

        assertThat(atom.getIntField()).isEqualTo(42);
        assertThat(atom.getLongField()).isEqualTo(Long.MAX_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(3.14f);
        assertThat(atom.getStringField()).isEqualTo("This is a basic test!");
        assertThat(atom.getBooleanField()).isFalse();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.ON_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
                .containsExactly(1L, 2L, 3L).inOrder();

        assertThat(atom.getRepeatedIntFieldList()).containsExactly(3, 6).inOrder();
        assertThat(atom.getRepeatedLongFieldList()).containsExactly(1000L, 1002L).inOrder();
        assertThat(atom.getRepeatedFloatFieldList()).containsExactly(0.3f, 0.09f).inOrder();
        assertThat(atom.getRepeatedStringFieldList()).containsExactly("str1", "str2").inOrder();
        assertThat(atom.getRepeatedBooleanFieldList()).containsExactly(true, false).inOrder();
        assertThat(atom.getRepeatedEnumFieldList())
                .containsExactly(TestAtomReported.State.OFF, TestAtomReported.State.ON)
                .inOrder();

        atom = data.get(1).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(9999);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag9999");
        assertThat(attrChain.get(1).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(1).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(100);
        assertThat(atom.getLongField()).isEqualTo(Long.MIN_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(-2.5f);
        assertThat(atom.getStringField()).isEqualTo("Test null uid");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.UNKNOWN_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
                .containsExactly(1L, 2L, 3L).inOrder();

        assertThat(atom.getRepeatedIntFieldList()).containsExactly(3, 6).inOrder();
        assertThat(atom.getRepeatedLongFieldList()).containsExactly(1000L, 1002L).inOrder();
        assertThat(atom.getRepeatedFloatFieldList()).containsExactly(0.3f, 0.09f).inOrder();
        assertThat(atom.getRepeatedStringFieldList()).containsExactly("str1", "str2").inOrder();
        assertThat(atom.getRepeatedBooleanFieldList()).containsExactly(true, false).inOrder();
        assertThat(atom.getRepeatedEnumFieldList())
                .containsExactly(TestAtomReported.State.OFF, TestAtomReported.State.ON)
                .inOrder();

        atom = data.get(2).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");

        assertThat(atom.getIntField()).isEqualTo(-256);
        assertThat(atom.getLongField()).isEqualTo(-1234567890L);
        assertThat(atom.getFloatField()).isEqualTo(42.01f);
        assertThat(atom.getStringField()).isEqualTo("Test non chained");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
                .containsExactly(1L, 2L, 3L).inOrder();

        assertThat(atom.getRepeatedIntFieldList()).isEmpty();
        assertThat(atom.getRepeatedLongFieldList()).isEmpty();
        assertThat(atom.getRepeatedFloatFieldList()).isEmpty();
        assertThat(atom.getRepeatedStringFieldList()).isEmpty();
        assertThat(atom.getRepeatedBooleanFieldList()).isEmpty();
        assertThat(atom.getRepeatedEnumFieldList()).isEmpty();

        atom = data.get(3).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(0).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(0);
        assertThat(atom.getLongField()).isEqualTo(0L);
        assertThat(atom.getFloatField()).isEqualTo(0f);
        assertThat(atom.getStringField()).isEmpty();
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList()).isEmpty();

        assertThat(atom.getRepeatedIntFieldList()).isEmpty();
        assertThat(atom.getRepeatedLongFieldList()).isEmpty();
        assertThat(atom.getRepeatedFloatFieldList()).isEmpty();
        assertThat(atom.getRepeatedStringFieldList()).isEmpty();
        assertThat(atom.getRepeatedBooleanFieldList()).isEmpty();
        assertThat(atom.getRepeatedEnumFieldList()).isEmpty();
    }

    public void testAppForegroundBackground() throws Exception {
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_FOREGROUND_VALUE));
        Set<Integer> offStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_BACKGROUND_VALUE));

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.APP_USAGE_EVENT_OCCURRED_FIELD_NUMBER, /*useUidAttributionChain=*/false);

        // Overlay may need to sit there a while.
        final int waitTime = 10_500;
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", ACTION_SHOW_APPLICATION_OVERLAY, waitTime);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        Function<Atom, Integer> appUsageStateFunction =
                atom -> atom.getAppUsageEventOccurred().getEventType().getNumber();
        // clear out initial appusage states
        AtomTestUtils.popUntilFind(data, onStates, appUsageStateFunction);
        AtomTestUtils.assertStatesOccurredInOrder(stateSet, data, 0, appUsageStateFunction);
    }
/*
    public void testAppForceStopUsageEvent() throws Exception {
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_FOREGROUND_VALUE));
        Set<Integer> offStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_BACKGROUND_VALUE));

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        createAndUploadConfig(Atom.APP_USAGE_EVENT_OCCURRED_FIELD_NUMBER, false);
        RunUtil.getDefault().sleep(WAIT_TIME_FOR_CONFIG_UPDATE_MS);

        getDevice().executeShellCommand(String.format(
                "am start -n '%s' -e %s %s",
                "com.android.server.cts.device.statsd/.StatsdCtsForegroundActivity",
                "action", ACTION_LONG_SLEEP_WHILE_TOP));
        final int waitTime = EXTRA_WAIT_TIME_MS + 5_000;
        RunUtil.getDefault().sleep(waitTime);

        getDevice().executeShellCommand(String.format(
                "am force-stop %s",
                "com.android.server.cts.device.statsd/.StatsdCtsForegroundActivity"));
        RunUtil.getDefault().sleep(waitTime + STATSD_REPORT_WAIT_TIME_MS);

        List<EventMetricData> data = getEventMetricDataList();
        Function<Atom, Integer> appUsageStateFunction =
                atom -> atom.getAppUsageEventOccurred().getEventType().getNumber();
        popUntilFind(data, onStates, appUsageStateFunction); // clear out initial appusage states.
        assertStatesOccurred(stateSet, data, 0, appUsageStateFunction);
    }
*/

    private int getScreenBrightness() throws Exception {
        return Integer.parseInt(
                getDevice().executeShellCommand("settings get system screen_brightness").trim());
    }

    private boolean isScreenBrightnessModeManual() throws Exception {
        String mode = getDevice().executeShellCommand("settings get system screen_brightness_mode");
        return Integer.parseInt(mode.trim()) == 0;
    }

    private void setScreenBrightnessMode(boolean manual) throws Exception {
        getDevice().executeShellCommand(
                "settings put system screen_brightness_mode " + (manual ? 0 : 1));
    }

    private void setScreenBrightness(int brightness) throws Exception {
        getDevice().executeShellCommand("settings put system screen_brightness " + brightness);
    }
}
