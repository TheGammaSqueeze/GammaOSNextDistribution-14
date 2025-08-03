/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.sdksandbox;

import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__TOTAL;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__TOTAL_WITH_LOAD_SANDBOX;

import android.app.sdksandbox.SandboxLatencyInfo;
import android.os.Process;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

@RunWith(JUnit4.class)
public class SdkSandboxStatsdLoggerUnitTest {
    private MockitoSession mStaticMockSession;
    private SdkSandboxStatsdLogger mSdkSandboxStatsdLogger;
    private int mClientAppUid;

    @Before
    public void setup() {
        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession().mockStatic(SdkSandboxStatsLog.class);
        mStaticMockSession = mockitoSessionBuilder.startMocking();

        mSdkSandboxStatsdLogger = new SdkSandboxStatsdLogger();
        mClientAppUid = Process.myUid();
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    // loadSdk() API is chosen as it covers all call stages
    @Test
    public void testlogSandboxApiLatency_LoadSdk_AllStagesSet_CallsStatsdForAllStages() {
        SandboxLatencyInfo sandboxLatencyInfo = createSandboxLatencyInfoWithDefaultTimestamps();

        mSdkSandboxStatsdLogger.logSandboxApiLatency(sandboxLatencyInfo);

        // Verify data is reported to StatsD for each stage (except for TOTAL as it is mutually
        // exclusive with TOTAL_WITH_LOAD_SANDBOX).
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                sandboxLatencyInfo.getAppToSystemServerLatency());
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                sandboxLatencyInfo.getSystemServerAppToSandboxLatency());
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                sandboxLatencyInfo.getLoadSandboxLatency());
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX,
                sandboxLatencyInfo.getSystemServerToSandboxLatency());
        verifySuccessfulSandboxApiCallForStage(
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                sandboxLatencyInfo.getSandboxLatency());
        verifySuccessfulSandboxApiCallForStage(
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK,
                sandboxLatencyInfo.getSdkLatency());
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER,
                sandboxLatencyInfo.getSandboxToSystemServerLatency());
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                sandboxLatencyInfo.getSystemServerSandboxToAppLatency());
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP,
                sandboxLatencyInfo.getSystemServerToAppLatency());
        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__TOTAL_WITH_LOAD_SANDBOX,
                sandboxLatencyInfo.getTotalCallLatency());
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__TOTAL),
                                Mockito.eq(mClientAppUid)),
                Mockito.never());
    }

    @Test
    public void testlogSandboxApiLatency_LoadSdk_SdkStageMissing_CallsStatsdWithoutSdkStage() {
        SandboxLatencyInfo sandboxLatencyInfo = createSandboxLatencyInfoWithDefaultTimestamps();
        sandboxLatencyInfo.setTimeSandboxCalledSdk(-1);
        sandboxLatencyInfo.setTimeSdkCallCompleted(-1);

        mSdkSandboxStatsdLogger.logSandboxApiLatency(sandboxLatencyInfo);

        verifySuccessfulSandboxApiCallForStage(
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                sandboxLatencyInfo.getSandboxLatency());
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK),
                                Mockito.eq(mClientAppUid)),
                Mockito.never());
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.anyInt(),
                                Mockito.anyInt()),
                Mockito.times(9));
    }

    @Test
    public void
            testlogSandboxApiLatency_LoadSdk_LoadSandboxStageMissing_CallsStatsdWithTotalLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = createSandboxLatencyInfoWithDefaultTimestamps();
        sandboxLatencyInfo.setTimeLoadSandboxStarted(-1);
        sandboxLatencyInfo.setTimeSandboxLoaded(-1);

        mSdkSandboxStatsdLogger.logSandboxApiLatency(sandboxLatencyInfo);

        verifySuccessfulSandboxApiCallForStage(
                SANDBOX_API_CALLED__STAGE__TOTAL, sandboxLatencyInfo.getTotalCallLatency());
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.anyInt(),
                                Mockito.anyInt()),
                Mockito.times(9));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__TOTAL_WITH_LOAD_SANDBOX),
                                Mockito.eq(mClientAppUid)),
                Mockito.never());
    }

    @Test
    public void testlogSandboxActivityApiLatency_CallsStatsd() {
        mSdkSandboxStatsdLogger.logSandboxActivityApiLatency(
                SdkSandboxStatsLog
                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__START_SDK_SANDBOX_ACTIVITY,
                SdkSandboxStatsLog.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                0,
                mClientAppUid);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_ACTIVITY_EVENT_OCCURRED,
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__START_SDK_SANDBOX_ACTIVITY,
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                                0,
                                mClientAppUid,
                                /*sdkUid=*/ -1));
    }

    private void verifySuccessfulSandboxApiCallForStage(int stage, int latency) {
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                latency,
                                /* success= */ true,
                                stage,
                                mClientAppUid));
    }

    // Creates a SandboxLatencyInfo object with all start and finish times filled with consecutive
    // values with step 1. In this case, latencies for all stages except for
    // SYSTEM_SERVER_APP_TO_SANDBOX, SANDBOX, TOTAL and TOTAL_WITH_LOAD_SANDBOX will be equal to 1.
    private SandboxLatencyInfo createSandboxLatencyInfoWithDefaultTimestamps() {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        sandboxLatencyInfo.setTimeAppCalledSystemServer(1);
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(2);
        sandboxLatencyInfo.setTimeLoadSandboxStarted(3);
        sandboxLatencyInfo.setTimeSandboxLoaded(4);
        sandboxLatencyInfo.setTimeSystemServerCallFinished(5);
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(6);
        sandboxLatencyInfo.setTimeSandboxCalledSdk(7);
        sandboxLatencyInfo.setTimeSdkCallCompleted(8);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(9);
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(10);
        sandboxLatencyInfo.setTimeSystemServerCalledApp(11);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(12);
        return sandboxLatencyInfo;
    }
}
