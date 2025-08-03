/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.cts.statsdatom.gnss;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

/**
 * Test for GnssPsdsDownload stats
 */
public class GnssPsdsDownloadStatsTest extends DeviceTestCase implements IBuildReceiver {
    private static final String LOCATION_DUMPSYS_COMMAND = "dumpsys location -a";
    private static final String FORCE_PSDS_DOWNLOAD_COMMAND =
            "cmd location providers send-extra-command gps force_psds_injection";
    private static final String PSDS_SUPPORTED = "mSupportsPsds=true";
    private static final String PSDS_SERVER_CONFIGURED = "PsdsServerConfigured=true";
    private static final long PSDS_DOWNLOAD_TIMEOUT_MILLIS = 10000;
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

    public void testGnssPsdsDownload() throws Exception {
        String dumpsysOut = getDevice().executeShellCommand(LOCATION_DUMPSYS_COMMAND);
        if (!dumpsysOut.contains(PSDS_SUPPORTED) || !dumpsysOut.contains(PSDS_SERVER_CONFIGURED)) {
            CLog.i("Skipping the test since GNSS PSDS is not supported or PSDS server is not "
                    + "configured.");
            return;
        }
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GNSS_PSDS_DOWNLOAD_REPORTED_FIELD_NUMBER);

        getDevice().executeShellCommand(FORCE_PSDS_DOWNLOAD_COMMAND);
        RunUtil.getDefault().sleep(PSDS_DOWNLOAD_TIMEOUT_MILLIS);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("GNSS PSDS must be downloaded").that(data.size()).isAtLeast(1);
    }
}
