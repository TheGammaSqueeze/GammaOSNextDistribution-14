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

package com.android.performance.tests;

import com.android.tradefed.device.metric.EmulatorMemoryCpuCapturer;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BaseEmulatorPreparer;
import com.android.tradefed.testtype.AndroidJUnitTest;

import java.nio.file.Path;

/**
 * A performance test that does repeated emulator launch + run instrumentation test.
 *
 * <p>Intended to be paired with a metrics collector like EmulatorMemoryCpuCollector to measure
 */
public class EmulatorInstrumentationPerfTest extends BaseEmulatorPerfTest {
    @Override
    protected void performIteration(
            TestInformation testInfo,
            BaseEmulatorPreparer emulatorLauncher,
            AndroidJUnitTest delegateTest,
            Path apkPath,
            DataRecorder dataRecorder,
            ITestInvocationListener listener)
            throws Exception {
        emulatorLauncher.setUp(testInfo);

        EmulatorMemoryCpuCapturer capturer = new EmulatorMemoryCpuCapturer(testInfo.getDevice());
        dataRecorder.recordMetric("initial_memory_pss", capturer.getPssMemory());
        dataRecorder.recordMetric("initial_cpu_usage", capturer.getCpuUsage());

        delegateTest.setDevice(testInfo.getDevice());
        testInfo.getDevice().installPackage(apkPath.toFile(), true);
        delegateTest.run(testInfo, listener);

        dataRecorder.recordMetric("end_memory_pss", capturer.getPssMemory());
        dataRecorder.recordMetric("overall_cpu_usage", capturer.getCpuUsage());
    }
}
