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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link SandboxLatencyInfo} APIs. */
@RunWith(JUnit4.class)
public class SandboxLatencyInfoUnitTest {

    private static final long TIME_APP_CALLED_SYSTEM_SERVER = 1;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP = 2;
    private static final long TIME_LOAD_SANDBOX_STARTED = 3;
    private static final long TIME_SANDBOX_LOADED = 4;
    private static final long TIME_SYSTEM_SERVER_CALL_FINISHED = 5;
    private static final long TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER = 6;
    private static final long TIME_SANDBOX_CALLED_SDK = 7;
    private static final long TIME_SDK_CALL_COMPLETED = 8;
    private static final long TIME_SANDBOX_CALLED_SYSTEM_SERVER = 9;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX = 10;
    private static final long TIME_SYSTEM_SERVER_CALLED_APP = 11;
    private static final long TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER = 12;

    @Test
    public void testDescribeContents() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.describeContents()).isEqualTo(0);
    }

    @Test
    public void testIsParcelable() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        Parcel sandboxLatencyInfoParcel = Parcel.obtain();
        sandboxLatencyInfo.writeToParcel(sandboxLatencyInfoParcel, /*flags=*/ 0);

        sandboxLatencyInfoParcel.setDataPosition(0);
        SandboxLatencyInfo sandboxLatencyInfoCreatedWithParcel =
                SandboxLatencyInfo.CREATOR.createFromParcel(sandboxLatencyInfoParcel);

        assertThat(sandboxLatencyInfo).isEqualTo(sandboxLatencyInfoCreatedWithParcel);
    }

    @Test
    public void testMethodIsSet() {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(SandboxLatencyInfo.METHOD_LOAD_SDK);
    }

    @Test
    public void testMethodIsUnspecified() {
        SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(SandboxLatencyInfo.METHOD_UNSPECIFIED);
    }

    @Test
    public void testGetAppToSystemServerLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int appToSystemServerLatency =
                (int) (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP - TIME_APP_CALLED_SYSTEM_SERVER);
        assertThat(sandboxLatencyInfo.getAppToSystemServerLatency())
                .isEqualTo(appToSystemServerLatency);
    }

    @Test
    public void testGetSystemServerAppToSandboxLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int systemServerAppToSandboxLatency =
                (int)
                        (TIME_SYSTEM_SERVER_CALL_FINISHED
                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                - (TIME_SANDBOX_LOADED - TIME_LOAD_SANDBOX_STARTED));
        assertThat(sandboxLatencyInfo.getSystemServerAppToSandboxLatency())
                .isEqualTo(systemServerAppToSandboxLatency);
    }

    @Test
    public void testGetLoadSandboxLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int loadSandboxLatency = (int) (TIME_SANDBOX_LOADED - TIME_LOAD_SANDBOX_STARTED);
        assertThat(sandboxLatencyInfo.getLoadSandboxLatency()).isEqualTo(loadSandboxLatency);
    }

    @Test
    public void testGetSystemServerToSandboxLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int systemServerToSandboxLatency =
                (int)
                        (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                - TIME_SYSTEM_SERVER_CALL_FINISHED);
        assertThat(sandboxLatencyInfo.getSystemServerToSandboxLatency())
                .isEqualTo(systemServerToSandboxLatency);
    }

    @Test
    public void testGetSandboxLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int sdkLatency = (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK);
        int sandboxLatency =
                (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER)
                        - sdkLatency;
        assertThat(sandboxLatencyInfo.getSandboxLatency()).isEqualTo(sandboxLatency);
    }

    @Test
    public void testGetSandboxLatency_timeFieldsNotSetForSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Reset the values
        sandboxLatencyInfo.setTimeSandboxCalledSdk(-1);
        sandboxLatencyInfo.setTimeSdkCallCompleted(-1);

        int sandboxLatency =
                (int)
                        (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);

        assertThat(sandboxLatencyInfo.getSandboxLatency()).isEqualTo(sandboxLatency);
    }

    @Test
    public void testGetSdkLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int sdkLatency = (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK);
        assertThat(sandboxLatencyInfo.getSdkLatency()).isEqualTo(sdkLatency);
    }

    @Test
    public void testGetSdkLatency_timeFieldsNotSetForSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Reset the values
        sandboxLatencyInfo.setTimeSandboxCalledSdk(-1);
        sandboxLatencyInfo.setTimeSdkCallCompleted(-1);

        assertThat(sandboxLatencyInfo.getSdkLatency()).isEqualTo(-1);
    }

    @Test
    public void testGetSandboxToSystemServerLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int sandboxToSystemServerLatency =
                (int)
                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX
                                - TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        assertThat(sandboxLatencyInfo.getSandboxToSystemServerLatency())
                .isEqualTo(sandboxToSystemServerLatency);
    }

    @Test
    public void testGetSystemServerSandboxToAppLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int systemServerSandboxToAppLatency =
                (int)
                        (TIME_SYSTEM_SERVER_CALLED_APP
                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX);
        assertThat(sandboxLatencyInfo.getSystemServerSandboxToAppLatency())
                .isEqualTo(systemServerSandboxToAppLatency);
    }

    @Test
    public void testGetSystemServerToAppLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int systemServerToAppLatency =
                (int) (TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER - TIME_SYSTEM_SERVER_CALLED_APP);
        assertThat(sandboxLatencyInfo.getSystemServerToAppLatency())
                .isEqualTo(systemServerToAppLatency);
    }

    @Test
    public void testGetTotalCallLatency_finishesAtAppReceivedCallFromSystemServerStage() {
        SandboxLatencyInfo sandboxLatencyInfo =
                getSandboxLatencyObjectWithAllFieldsSet(SandboxLatencyInfo.METHOD_LOAD_SDK);
        int totalCallLatency =
                (int) (TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER - TIME_APP_CALLED_SYSTEM_SERVER);

        assertThat(sandboxLatencyInfo.getTotalCallLatency()).isEqualTo(totalCallLatency);
    }

    @Test
    public void testGetTotalCallLatency_finishesAtSystemServerCalledSandboxStage() {
        SandboxLatencyInfo sandboxLatencyInfo =
                getSandboxLatencyObjectWithAllFieldsSet(
                        SandboxLatencyInfo.METHOD_GET_SANDBOXED_SDKS);
        int totalCallLatency =
                (int) (TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_APP_CALLED_SYSTEM_SERVER);

        assertThat(sandboxLatencyInfo.getTotalCallLatency()).isEqualTo(totalCallLatency);
    }

    @Test
    public void testGetTotalCallLatency_finishesAtSystemServerReceivedCallFromAppStage() {
        SandboxLatencyInfo sandboxLatencyInfo =
                getSandboxLatencyObjectWithAllFieldsSet(
                        SandboxLatencyInfo.METHOD_SYNC_DATA_FROM_CLIENT);
        int totalCallLatency =
                (int) (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP - TIME_APP_CALLED_SYSTEM_SERVER);

        assertThat(sandboxLatencyInfo.getTotalCallLatency()).isEqualTo(totalCallLatency);
    }

    @Test
    public void testGetTotalCallLatency_finishesAtSystemServerCalledAppStage() {
        SandboxLatencyInfo sandboxLatencyInfo =
                getSandboxLatencyObjectWithAllFieldsSet(SandboxLatencyInfo.METHOD_UNLOAD_SDK);
        int totalCallLatency =
                (int) (TIME_SYSTEM_SERVER_CALLED_APP - TIME_APP_CALLED_SYSTEM_SERVER);

        assertThat(sandboxLatencyInfo.getTotalCallLatency()).isEqualTo(totalCallLatency);
    }

    @Test
    public void testSandboxStatus_isSuccessfulAtAppToSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtAppToSystemServer()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtAppToSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtAppToSystemServer()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_APP_TO_SYSTEM_SERVER);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtAppToSystemServer()).isFalse();
    }

    @Test
    public void testSandboxStatus_isSuccessfulAtSystemServerAppToSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerAppToSandbox()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtSystemServerAppToSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerAppToSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerAppToSandbox()).isFalse();
    }

    @Test
    public void testSandboxStatus_isSuccessfulAtLoadSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtLoadSandbox()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtLoadSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtLoadSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_LOAD_SANDBOX);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtLoadSandbox()).isFalse();
    }

    @Test
    public void testSandboxStatus_isSuccessfulAtSystemServerToSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToSandbox()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtSystemServerToSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_TO_SANDBOX);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToSandbox()).isFalse();
    }

    @Test
    public void testIsSuccessfulAtSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SDK);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isFalse();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();
    }

    @Test
    public void testIsSuccessfulAtSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isFalse();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
    }

    @Test
    public void testSandboxStatus_isSuccessfulAtSandboxToSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandboxToSystemServer()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtSandboxToSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandboxToSystemServer()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX_TO_SYSTEM_SERVER);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandboxToSystemServer()).isFalse();
    }

    @Test
    public void testSandboxStatus_isSuccessfulAtSystemServerSandboxToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerSandboxToApp()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtSystemServerSandboxToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerSandboxToApp()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_SANDBOX_TO_APP);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerSandboxToApp()).isFalse();
    }

    @Test
    public void testSandboxStatus_isSuccessfulAtSystemServerToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedAtSystemServerToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_TO_APP);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isFalse();
    }

    @Test
    public void testSandboxStatus_isTotalCallSuccessful() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isTotalCallSuccessful()).isTrue();
    }

    @Test
    public void testSandboxStatus_failedTotalCall() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isTotalCallSuccessful()).isTrue();

        // Total call is considered failed if any of the call stages failed
        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_APP_TO_SYSTEM_SERVER);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isTotalCallSuccessful()).isFalse();
    }

    @Test
    public void testGetTimeSystemServerCallFinished() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.getTimeSystemServerCallFinished())
                .isEqualTo(TIME_SYSTEM_SERVER_CALL_FINISHED);
    }

    @Test
    public void testGetTimeSandboxCalledSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    private SandboxLatencyInfo getSandboxLatencyObjectWithAllFieldsSet() {
        return getSandboxLatencyObjectWithAllFieldsSet(SandboxLatencyInfo.METHOD_UNSPECIFIED);
    }

    private SandboxLatencyInfo getSandboxLatencyObjectWithAllFieldsSet(int method) {
        SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo(method);
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(
                TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP);
        sandboxLatencyInfo.setTimeLoadSandboxStarted(TIME_LOAD_SANDBOX_STARTED);
        sandboxLatencyInfo.setTimeSandboxLoaded(TIME_SANDBOX_LOADED);
        sandboxLatencyInfo.setTimeSystemServerCallFinished(TIME_SYSTEM_SERVER_CALL_FINISHED);
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSandboxCalledSdk(TIME_SANDBOX_CALLED_SDK);
        sandboxLatencyInfo.setTimeSdkCallCompleted(TIME_SDK_CALL_COMPLETED);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(
                TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX);
        sandboxLatencyInfo.setTimeSystemServerCalledApp(TIME_SYSTEM_SERVER_CALLED_APP);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        return sandboxLatencyInfo;
    }
}
