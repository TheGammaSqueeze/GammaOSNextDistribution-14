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

package android.healthconnect.cts.dailyjob;

import static android.healthconnect.cts.HostSideTestUtil.DAILY_LOG_TESTS_ACTIVITY;
import static android.healthconnect.cts.HostSideTestUtil.clearData;
import static android.healthconnect.cts.HostSideTestUtil.increaseDeviceTimeByDays;
import static android.healthconnect.cts.HostSideTestUtil.isHardwareSupported;
import static android.healthconnect.cts.HostSideTestUtil.resetTime;
import static android.healthconnect.cts.HostSideTestUtil.triggerDailyJob;
import static android.healthconnect.cts.HostSideTestUtil.triggerTestInTestApp;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.time.Instant;

public class DailyDeleteAccessLogTest extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;
    private Instant mTestStartTime;
    private Instant mDeviceStartTime;

    @Override
    protected void setUp() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        super.setUp();
        mTestStartTime = Instant.now();
        mDeviceStartTime = Instant.ofEpochMilli(getDevice().getDeviceDate());
        assertThat(mCtsBuild).isNotNull();
        clearData(getDevice());
    }

    @Override
    protected void tearDown() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        clearData(getDevice());
        resetTime(getDevice(), mTestStartTime, mDeviceStartTime);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testAccessLogsAreDeleted() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }

        triggerTestInTestApp(getDevice(), DAILY_LOG_TESTS_ACTIVITY, "testInsertRecordsSucceed");
        triggerTestInTestApp(
                getDevice(), DAILY_LOG_TESTS_ACTIVITY, "testHealthConnectAccessLogsEqualsOne");

        increaseDeviceTimeByDays(getDevice(), 5);
        triggerTestInTestApp(getDevice(), DAILY_LOG_TESTS_ACTIVITY, "testInsertRecordsSucceed");
        triggerTestInTestApp(
                getDevice(), DAILY_LOG_TESTS_ACTIVITY, "testHealthConnectAccessLogsEqualsTwo");

        // Only the first access log should have been deleted after 5 days.
        increaseDeviceTimeByDays(getDevice(), 5);
        triggerDailyJob(getDevice());
        triggerTestInTestApp(
                getDevice(), DAILY_LOG_TESTS_ACTIVITY, "testHealthConnectAccessLogsEqualsOne");

        // The other access log should also be deleted after 5 days.
        increaseDeviceTimeByDays(getDevice(), 5);
        triggerDailyJob(getDevice());
        triggerTestInTestApp(
                getDevice(), DAILY_LOG_TESTS_ACTIVITY, "testHealthConnectAccessLogsEqualsZero");
    }
}
