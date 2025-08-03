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

package com.android.tradefed.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PerfettoTraceRecorderFuncTest implements IDeviceTest {

    private ITestDevice mTestDevice;
    private PerfettoTraceRecorder mPerfettoTraceRecorder;
    private List<File> mTraceFiles;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Before
    public void setup() throws IOException {
        mPerfettoTraceRecorder = new PerfettoTraceRecorder();
        mTraceFiles = new ArrayList<>();
    }

    @After
    public void teardown() {
        // delete trace files
        for (File tracefile : mTraceFiles) {
            tracefile.delete();
        }
    }

    @Test
    public void testPerfettoTraceRecorded() throws IOException {
        mPerfettoTraceRecorder.startTrace(getDevice(), null);
        RunUtil.getDefault().sleep(5000); // collect trace for five seconds
        File traceFile = mPerfettoTraceRecorder.stopTrace(getDevice());

        // Check if the perfetto trace file was recorded and not empty
        assertNotNull(traceFile);
        assertTrue(traceFile.exists());
        mTraceFiles.add(traceFile);
        assertTrue(traceFile.length() > 0);
    }
}
