/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.cts.statsd.metadata;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.StatsdStatsReport;
import com.android.os.StatsLog.StatsdStatsReport.ConfigStats;
import com.android.os.StatsLog.StatsdStatsReport.LogLossStats;
import com.android.os.StatsLog.StatsdStatsReport.SocketLossStats;
import com.android.os.StatsLog.StatsdStatsReport.SocketLossStats.LossStatsPerUid;
import com.android.os.StatsLog.StatsdStatsReport.SocketLossStats.LossStatsPerUid.AtomIdLossStats;
import com.android.tradefed.log.LogUtil;
import java.util.HashSet;

/**
 * Statsd Metadata tests.
 */
public class MetadataTests extends MetadataTestCase {

    private static final String TAG = "Statsd.MetadataTests";

    // Tests that the statsd config is reset after the specified ttl.
    public void testConfigTtl() throws Exception {
        final int TTL_TIME_SEC = 8;
        StatsdConfig.Builder config = getBaseConfig();
        config.setTtlInSeconds(TTL_TIME_SEC); // should reset in this many seconds.

        uploadConfig(config);
        long startTime = System.currentTimeMillis();
        Thread.sleep(WAIT_TIME_SHORT);
        doAppBreadcrumbReportedStart(/* irrelevant val */ 6); // Event, within < TTL_TIME_SEC secs.
        Thread.sleep(WAIT_TIME_SHORT);
        StatsdStatsReport report = getStatsdStatsReport(); // Has only been 1 second
        LogUtil.CLog.d("got following statsdstats report: " + report.toString());
        boolean foundActiveConfig = false;
        int creationTime = 0;
        for (ConfigStats stats: report.getConfigStatsList()) {
            if (stats.getId() == CONFIG_ID && stats.getUid() == getHostUid()) {
                if(!stats.hasDeletionTimeSec()) {
                    assertWithMessage("Found multiple active CTS configs!")
                            .that(foundActiveConfig).isFalse();
                    foundActiveConfig = true;
                    creationTime = stats.getCreationTimeSec();
                }
            }
        }
        assertWithMessage("Did not find an active CTS config").that(foundActiveConfig).isTrue();

        while(System.currentTimeMillis() - startTime < 8_000) {
            Thread.sleep(10);
        }
        doAppBreadcrumbReportedStart(/* irrelevant val */ 6); // Event, after TTL_TIME_SEC secs.
        Thread.sleep(WAIT_TIME_LONG);
        report = getStatsdStatsReport();
        LogUtil.CLog.d("got following statsdstats report: " + report.toString());
        foundActiveConfig = false;
        int expectedTime = creationTime + TTL_TIME_SEC;
        for (ConfigStats stats: report.getConfigStatsList()) {
            if (stats.getId() == CONFIG_ID && stats.getUid() == getHostUid()) {
                // Original config should be TTL'd
                if (stats.getCreationTimeSec() == creationTime) {
                    assertWithMessage("Config should have TTL'd but is still active")
                            .that(stats.hasDeletionTimeSec()).isTrue();
                    assertWithMessage(
                            "Config deletion time should be about %s after creation", TTL_TIME_SEC
                    ).that(Math.abs(stats.getDeletionTimeSec() - expectedTime)).isAtMost(2);
                }
                // There should still be one active config, that is marked as reset.
                if(!stats.hasDeletionTimeSec()) {
                    assertWithMessage("Found multiple active CTS configs!")
                            .that(foundActiveConfig).isFalse();
                    foundActiveConfig = true;
                    creationTime = stats.getCreationTimeSec();
                    assertWithMessage("Active config after TTL should be marked as reset")
                            .that(stats.hasResetTimeSec()).isTrue();
                    assertWithMessage("Reset and creation time should be equal for TTl'd configs")
                            .that(stats.getResetTimeSec()).isEqualTo(stats.getCreationTimeSec());
                    assertWithMessage(
                            "Reset config should be created when the original config TTL'd"
                    ).that(Math.abs(stats.getCreationTimeSec() - expectedTime)).isAtMost(2);
                }
            }
        }
        assertWithMessage("Did not find an active CTS config after the TTL")
                .that(foundActiveConfig).isTrue();
    }

    private static final int LIB_STATS_SOCKET_QUEUE_OVERFLOW_ERROR_CODE = 1;
    private static final int EVENT_STORM_ITERATIONS_COUNT = 10;

    /** Tests that logging many atoms back to back leads to socket overflow and data loss. */
    public void testAtomLossInfoCollection() throws Exception {
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".StatsdStressLogging", "testLogAtomsBackToBack");

        StatsdStatsReport report = getStatsdStatsReport();
        assertThat(report).isNotNull();
        boolean detectedLossEventForAppBreadcrumbAtom = false;
        for (LogLossStats lossStats : report.getDetectedLogLossList()) {
            if (lossStats.getLastTag() == Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER) {
                detectedLossEventForAppBreadcrumbAtom = true;
            }
        }

        assertThat(detectedLossEventForAppBreadcrumbAtom).isTrue();
    }

    /** Tests that SystemServer logged atoms in case of loss event has error code 1. */
    public void testSystemServerLossErrorCode() throws Exception {
        // Starting from VanillaIceCream libstatssocket uses worker thread & dedicated logging queue
        // to handle atoms for system server (logged with UID 1000)
        // this test might fail for previous versions due to loss stats last error code check
        // will not pass

        // Due to info about system server atom loss could be overwritten by APP_BREADCRUMB_REPORTED
        // loss info run several iterations of this test
        for (int i = 0; i < EVENT_STORM_ITERATIONS_COUNT; i++) {
            LogUtil.CLog.d("testSystemServerLossErrorCode iteration #" + i);
            // logging back to back many atoms to force socket overflow
            runDeviceTests(
                    DEVICE_SIDE_TEST_PACKAGE, ".StatsdStressLogging", "testLogAtomsBackToBack");

            // Delay to allow statsd socket recover after overflow
            Thread.sleep(500);

            // There is some un-deterministic component in AtomLossStats propagation:
            // - the dumpAtomsLossStats() from the libstatssocket happens ONLY after the
            //   next successful atom write to socket.
            // - to avoid socket flood there is also cooldown timer incorporated. If no new atoms -
            //   loss info will not be propagated, which is intention by design.
            // Log atoms into socket successfully to trigger libstatsocket dumpAtomsLossStats()
            doAppBreadcrumbReportedStart(6);

            // Delay to allow libstatssocket loss info to be propagated to statsdstats
            Thread.sleep(1000);

            StatsdStatsReport report = getStatsdStatsReport();
            assertThat(report).isNotNull();
            boolean detectedLossEventForAppBreadcrumbAtom = false;
            boolean detectedLossEventForSystemServer = false;
            for (LogLossStats lossStats : report.getDetectedLogLossList()) {
                if (lossStats.getLastTag() == Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER) {
                    detectedLossEventForAppBreadcrumbAtom = true;
                }

                // it should not happen due to atoms from system servers logged via queue
                // which should be sufficient to hold them for some time to overcome the
                // socket overflow time frame
                if (lossStats.getUid() == 1000) {
                    detectedLossEventForSystemServer = true;
                    // but if loss happens it should be annotated with predefined error code == 1
                    assertThat(lossStats.getLastError())
                            .isEqualTo(LIB_STATS_SOCKET_QUEUE_OVERFLOW_ERROR_CODE);
                }
            }

            assertThat(detectedLossEventForAppBreadcrumbAtom).isTrue();
            assertThat(detectedLossEventForSystemServer).isFalse();

            boolean detectedLossEventForAppBreadcrumbAtomViaSocketLossStats = false;
            for (LossStatsPerUid lossStats : report.getSocketLossStats().getLossStatsPerUidList()) {
                for (AtomIdLossStats atomLossStats : lossStats.getAtomIdLossStatsList()) {
                    if (atomLossStats.getAtomId() == Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER) {
                        detectedLossEventForAppBreadcrumbAtomViaSocketLossStats = true;
                    }
                }
            }
            assertThat(detectedLossEventForAppBreadcrumbAtomViaSocketLossStats).isTrue();
        }
    }

    /** Test libstatssocket logging queue atom id distribution collection */
    public void testAtomIdLossDistributionCollection() throws Exception {
        if (!ApiLevelUtil.codenameEquals(getDevice(), "VanillaIceCream")) {
            return;
        }

        final String appTestApk = "StatsdAtomStormApp.apk";
        final String app2TestApk = "StatsdAtomStormApp2.apk";

        final String appTestPkg = "com.android.statsd.app.atomstorm";
        final String app2TestPkg = "com.android.statsd.app.atomstorm.copy";

        getDevice().uninstallPackage(appTestPkg);
        getDevice().uninstallPackage(app2TestPkg);
        installPackage(appTestApk, true);
        installPackage(app2TestApk, true);

        // run reference test app with UID 1
        runDeviceTests(appTestPkg, null, null);
        // run reference test app with UID 2
        runDeviceTests(app2TestPkg, null, null);

        StatsdStatsReport report = getStatsdStatsReport();
        assertThat(report).isNotNull();
        HashSet<Integer> reportedUids = new HashSet<Integer>();
        for (LossStatsPerUid lossStats : report.getSocketLossStats().getLossStatsPerUidList()) {
            reportedUids.add(lossStats.getUid());
        }
        assertThat(reportedUids.size()).isGreaterThan(1);

        getDevice().uninstallPackage(appTestPkg);
        getDevice().uninstallPackage(app2TestPkg);
    }
}
