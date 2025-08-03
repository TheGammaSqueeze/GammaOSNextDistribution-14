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

package android.healthconnect.cts.logging;

import static android.healthconnect.cts.HostSideTestUtil.isHardwareSupported;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.healthconnect.cts.HostSideTestUtil;
import android.healthfitness.api.ApiMethod;
import android.healthfitness.api.ApiStatus;
import android.healthfitness.api.ForegroundState;
import android.healthfitness.api.RateLimit;

import com.android.os.StatsLog;
import com.android.os.healthfitness.api.ApiExtensionAtoms;
import com.android.os.healthfitness.api.HealthConnectApiCalled;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import com.google.protobuf.ExtensionRegistry;

import java.util.List;

public class HealthConnectServiceStatsTests extends DeviceTestCase implements IBuildReceiver {

    public static final String TEST_APP_PKG_NAME = "android.healthconnect.cts.testhelper";
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        // TODO(b/313055175): Do not disable rate limiting once b/300238889 is resolved.
        HostSideTestUtil.setupRateLimitingFeatureFlag(getDevice());
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Override
    protected void tearDown() throws Exception {
        // TODO(b/313055175): Do not disable rate limiting once b/300238889 is resolved.
        HostSideTestUtil.restoreRateLimitingFeatureFlag(getDevice());
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testInsertRecords() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectInsertRecords");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.INSERT_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(atom.getErrorCode()).isEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(2);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
    }

    public void testInsertRecordsError() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectInsertRecordsError");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.INSERT_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.ERROR);
        assertThat(atom.getErrorCode()).isNotEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(2);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
    }

    public void testUpdateRecords() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectUpdateRecords");

        assertThat(data.size()).isAtLeast(3);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.UPDATE_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(atom.getErrorCode()).isEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(3);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
    }

    public void testUpdateRecordsError() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectUpdateRecordsError");

        assertThat(data.size()).isAtLeast(3);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.UPDATE_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.ERROR);
        assertThat(atom.getErrorCode()).isNotEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(1);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
    }

    public void testDeleteRecords() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectDeleteRecords");
        assertThat(data.size()).isAtLeast(3);
        int deletedRecords = 0;
        for (StatsLog.EventMetricData datum : data) {
            HealthConnectApiCalled atom =
                    datum.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);

            if (atom.getApiMethod().equals(ApiMethod.DELETE_DATA)
                    && atom.getNumberOfRecords() == 1) {
                deletedRecords++;
                assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.SUCCESS);
                assertThat(atom.getErrorCode()).isEqualTo(0);
                assertThat(atom.getDurationMillis()).isAtLeast(0);
                assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
            }
        }
        assertThat(deletedRecords).isAtLeast(2);
    }

    public void testDeleteRecordsError() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectDeleteRecordsError");
        assertThat(data.size()).isAtLeast(3);
        StatsLog.EventMetricData event =
                getEventForApiMethod(data, ApiMethod.DELETE_DATA, ApiStatus.ERROR);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.ERROR);
        assertThat(atom.getErrorCode()).isNotEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
    }

    public void testReadRecords() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectReadRecords");
        assertThat(data.size()).isAtLeast(3);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.READ_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(atom.getErrorCode()).isEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(1);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
        assertThat(atom.getCallerForegroundState()).isEqualTo(ForegroundState.FOREGROUND);
    }

    public void testReadRecordsError() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectReadRecordsError");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.READ_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.ERROR);
        assertThat(atom.getErrorCode()).isNotEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
        assertThat(atom.getCallerForegroundState()).isEqualTo(ForegroundState.FOREGROUND);
    }

    public void testChangeLogTokenRequest() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectGetChangeLogToken");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.GET_CHANGES_TOKEN);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(atom.getErrorCode()).isEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(0);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
    }

    public void testChangeLogTokenRequestError() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectGetChangeLogTokenError");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.GET_CHANGES_TOKEN);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.ERROR);
        assertThat(atom.getErrorCode()).isNotEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(0);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
    }

    public void testChangeLogsRequest() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectGetChangeLogs");
        assertThat(data.size()).isAtLeast(5);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.GET_CHANGES);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(atom.getErrorCode()).isEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(1);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
        assertThat(atom.getCallerForegroundState()).isEqualTo(ForegroundState.FOREGROUND);
    }

    public void testChangeLogsRequestError() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectGetChangeLogsError");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.GET_CHANGES);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.ERROR);
        assertThat(atom.getErrorCode()).isNotEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
        assertThat(atom.getCallerForegroundState()).isEqualTo(ForegroundState.FOREGROUND);
    }

    public void testAggregatedDataRequest() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectAggregatedData");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.READ_AGGREGATED_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(atom.getErrorCode()).isEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(1);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
        assertThat(atom.getCallerForegroundState()).isEqualTo(ForegroundState.FOREGROUND);
    }

    public void testAggregatedDataRequestError() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        List<StatsLog.EventMetricData> data =
                uploadAtomConfigAndTriggerTest("testHealthConnectAggregatedDataError");
        assertThat(data.size()).isAtLeast(2);
        StatsLog.EventMetricData event = getEventForApiMethod(data, ApiMethod.READ_AGGREGATED_DATA);
        assertThat(event).isNotNull();
        HealthConnectApiCalled atom =
                event.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);
        assertThat(atom.getApiStatus()).isEqualTo(ApiStatus.ERROR);
        assertThat(atom.getErrorCode()).isNotEqualTo(0);
        assertThat(atom.getDurationMillis()).isAtLeast(0);
        assertThat(atom.getNumberOfRecords()).isEqualTo(1);
        assertThat(atom.getRateLimit()).isEqualTo(RateLimit.NOT_USED);
        assertThat(atom.getCallerForegroundState()).isEqualTo(ForegroundState.FOREGROUND);
    }

    private List<StatsLog.EventMetricData> uploadAtomConfigAndTriggerTest(String testName)
            throws Exception {
        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_API_CALLED_FIELD_NUMBER});

        DeviceUtils.runDeviceTests(
                getDevice(), TEST_APP_PKG_NAME, ".HealthConnectServiceLogsTests", testName);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        ApiExtensionAtoms.registerAllExtensions(registry);

        return ReportUtils.getEventMetricDataList(getDevice(), registry);
    }

    private StatsLog.EventMetricData getEventForApiMethod(
            List<StatsLog.EventMetricData> data, ApiMethod apiMethod, ApiStatus status) {
        for (StatsLog.EventMetricData datum : data) {
            HealthConnectApiCalled atom =
                    datum.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);

            if (atom.getApiMethod().equals(apiMethod) && atom.getApiStatus().equals(status)) {
                return datum;
            }
        }
        return null;
    }

    private StatsLog.EventMetricData getEventForApiMethod(
            List<StatsLog.EventMetricData> data, ApiMethod apiMethod) {
        for (StatsLog.EventMetricData datum : data) {
            HealthConnectApiCalled atom =
                    datum.getAtom().getExtension(ApiExtensionAtoms.healthConnectApiCalled);

            if (atom.getApiMethod().equals(apiMethod)) {
                return datum;
            }
        }
        return null;
    }
}
