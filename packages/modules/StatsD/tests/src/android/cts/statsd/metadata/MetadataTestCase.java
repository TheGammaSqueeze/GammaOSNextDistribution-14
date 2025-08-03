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

import android.cts.statsd.atom.AtomTestCase;

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.StatsdStatsReport;
import com.android.tradefed.log.LogUtil;

public class MetadataTestCase extends AtomTestCase {
    public static final String DUMP_METADATA_CMD = "cmd stats print-stats";

    public static final String DEVICE_SIDE_TEST_APK = "CtsStatsdApp.apk";
    public static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.device.statsd";

    protected StatsdStatsReport getStatsdStatsReport() throws Exception {
        try {
            StatsdStatsReport report = getDump(StatsdStatsReport.parser(),
                    String.join(" ", DUMP_METADATA_CMD, "--proto"));
            return report;
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            LogUtil.CLog.e("Failed to fetch and parse the statsdstats output report.");
            throw (e);
        }
    }

    protected final StatsdConfig.Builder getBaseConfig() throws Exception {
      StatsdConfig.Builder builder = createConfigBuilder();
      addAtomEvent(builder, Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER);
      return builder;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
        installPackage(DEVICE_SIDE_TEST_APK, true);
        Thread.sleep(1000);
    }
}
