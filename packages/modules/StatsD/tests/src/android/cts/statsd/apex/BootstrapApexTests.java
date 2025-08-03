/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.cts.statsd.apex;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsd.atom.BaseTestCase;
import com.android.apex.ApexInfo;
import com.android.apex.XmlParser;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.tradefed.log.LogUtil;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

/**
 * Verify statsd is not in the bootstrap apexes
 */
public class BootstrapApexTests extends BaseTestCase {
    private static final String TAG = "Statsd.BootstrapApexTests";

    // Constants for the paths to apex-info-list.xml
    // - new location
    private static final String BOOTSTRAP_APEX_FILE1 = "/bootstrap-apex/apex-info-list.xml";
    // - legacy location
    private static final String BOOTSTRAP_APEX_FILE2 = "/apex/.bootstrap-apex-info-list.xml";

    private boolean sdkLevelAtLeast(int sdkLevel, String codename) throws Exception {
        return ApiLevelUtil.isAtLeast(getDevice(), sdkLevel)
                || ApiLevelUtil.codenameEquals(getDevice(), codename);
    }

    // TODO: use InstallUtilsHost#isApexUpdateSupported after migrating to JUnit4
    private final boolean isApexUpdateSupported() throws Exception {
        return getDevice().getBooleanProperty("ro.apex.updatable", false);
    }

    private List<ApexInfo> readBootstrapApexes() throws Exception {
        File file = getDevice().pullFile(BOOTSTRAP_APEX_FILE1);
        if (file == null) {
            file = getDevice().pullFile(BOOTSTRAP_APEX_FILE2);
        }
        try (FileInputStream stream = new FileInputStream(file)) {
            return XmlParser.readApexInfoList(stream).getApexInfo();
        }
    }

    public void testStatsdNotPresent() throws Exception {
        if (!sdkLevelAtLeast(31, "S")) {
            return;
        }
        if (!isApexUpdateSupported()) {
            return;
        }

        List<ApexInfo> apexInfoList = readBootstrapApexes();
        LogUtil.CLog.d(TAG + " Received " + apexInfoList.size() + " apexes in bootstrap apexes");
        assertThat(apexInfoList.size()).isGreaterThan(0);
        for (ApexInfo apexInfo : apexInfoList) {
            LogUtil.CLog.d(TAG + " APEX name is " + apexInfo.getModuleName());
            assertThat(apexInfo.getModuleName()).isNotEqualTo("com.android.os.statsd");
        }
    }
}
