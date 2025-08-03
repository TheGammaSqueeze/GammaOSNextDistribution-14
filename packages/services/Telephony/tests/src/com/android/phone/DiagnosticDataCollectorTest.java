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

package com.android.phone;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.DropBoxManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;

/**
 * Unit Tests for DiagnosticDataCollector.
 */
@RunWith(JUnit4.class)
public class DiagnosticDataCollectorTest {

    private static final String[] TELECOM_DUMPSYS_COMMAND =
            {"/system/bin/dumpsys", "telecom", "EmergencyDiagnostics"};
    private static final String[] TELEPHONY_DUMPSYS_COMMAND =
            {"/system/bin/dumpsys", "telephony.registry", "EmergencyDiagnostics"};
    private static final String[] LOGCAT_BINARY = {"/system/bin/logcat"};


    @Mock
    DataCollectorConfig.Adapter mConfig;
    private Runtime mRuntime;

    @Mock
    private DropBoxManager mDropBoxManager;

    private DiagnosticDataCollector mDiagnosticDataCollector;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRuntime = spy(Runtime.getRuntime());
        mDiagnosticDataCollector = new DiagnosticDataCollector(mRuntime, Runnable::run,
                mDropBoxManager, false);
    }

    @After
    public void tearDown() throws Exception {
    }

    private void verifyCmdAndDropboxTag(String[] cmd, String tag, boolean startsWithMatch)
            throws InterruptedException, IOException {
        ArgumentCaptor<String[]> textArrayCaptor = ArgumentCaptor.forClass(String[].class);

        //verify cmd passed to runtime
        verify(mRuntime).exec(textArrayCaptor.capture());
        String[] argList = textArrayCaptor.getValue();
        if (startsWithMatch) {
            assertEquals(cmd[0], argList[0]);
        } else {
            assertEquals(Arrays.toString(cmd), Arrays.toString(argList));
        }
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        //make sure logcat output does not have errors
        verify(mDropBoxManager, times(1)).addText(eq(tag), textCaptor.capture());
        assertFalse(textCaptor.getValue().contains(DiagnosticDataCollector.ERROR_MSG));
    }

    @Test
    public void testPersistForTelecomDumpsys() throws IOException, InterruptedException {
        TelephonyManager.EmergencyCallDiagnosticData.Builder callDiagnosticBuilder =
                new TelephonyManager.EmergencyCallDiagnosticData.Builder();
        TelephonyManager.EmergencyCallDiagnosticData ecdData =
                callDiagnosticBuilder.setTelecomDumpsysCollectionEnabled(true).build();
        mDiagnosticDataCollector.persistEmergencyDianosticData(
                mConfig, ecdData, "test_tag_telecom");

        verifyCmdAndDropboxTag(TELECOM_DUMPSYS_COMMAND, "test_tag_telecom", false);
    }

    @Test
    public void testPersistForTelephonyDumpsys() throws IOException, InterruptedException {
        TelephonyManager.EmergencyCallDiagnosticData.Builder callDiagnosticBuilder =
                new TelephonyManager.EmergencyCallDiagnosticData.Builder();
        TelephonyManager.EmergencyCallDiagnosticData ecdData =
                callDiagnosticBuilder.setTelephonyDumpsysCollectionEnabled(true).build();
        mDiagnosticDataCollector.persistEmergencyDianosticData(
                mConfig, ecdData, "test_tag_telephony");

        verifyCmdAndDropboxTag(TELEPHONY_DUMPSYS_COMMAND, "test_tag_telephony", false);
    }

    @Test
    public void testPersistForLogcat() throws IOException, InterruptedException {
        TelephonyManager.EmergencyCallDiagnosticData.Builder callDiagnosticBuilder =
                new TelephonyManager.EmergencyCallDiagnosticData.Builder();
        TelephonyManager.EmergencyCallDiagnosticData ecdData =
                callDiagnosticBuilder.setLogcatCollectionStartTimeMillis(
                        SystemClock.elapsedRealtime()).build();
        mDiagnosticDataCollector.persistEmergencyDianosticData(
                mConfig, ecdData, "test_tag_logcat");

        verifyCmdAndDropboxTag(LOGCAT_BINARY, "test_tag_logcat", true);
    }

}
