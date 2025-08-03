/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.nn.crashtest.app;

import android.content.Intent;

import com.android.nn.benchmark.app.AcceleratorSpecificTestSupport;
import com.android.nn.benchmark.core.NnApiDelegationFailure;
import com.android.nn.crashtest.core.test.RunModelsInMultipleProcesses;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class NNMultipleProcessModelLoadTest extends NNMultipleProcessTest {
    public NNMultipleProcessModelLoadTest(int processCount, int threadCount, Duration duration,
            int failureRatePercent, String acceleratorName) {
        super(processCount, threadCount, duration, failureRatePercent, acceleratorName);
    }

    @Parameters(name = "{0} processes, {1} threads for {2} on accelerator({4}) with early "
            + "termination rate({3}%)")
    public static List<Object[]> testConfiguration() {
        return AcceleratorSpecificTestSupport.perAcceleratorTestConfig(
                Arrays.asList(new Object[]{6, 10, Duration.ofMinutes(20), 0},
                        new Object[]{6, 10, Duration.ofMinutes(20), 50},
                        new Object[]{6, 10, Duration.ofMinutes(10), 100}));
    }

    @Override
    protected Intent getRunModelsInMultipleProcessesConfigIntent() {
        try {
            Intent result = new Intent();
            RunModelsInMultipleProcesses
                    .intentInitializer(mTestName.getMethodName(),
                            findModelForLivenessTest().get().mModelName,
                            mProcessCount, mThreadCount, mDuration, mAcceleratorName,
                            /*justCompileModel=*/true, mFailureRatePercent)
                    .addIntentParams(result);
            return result;
        } catch (NnApiDelegationFailure nnApiDelegationFailure) {
            throw new RuntimeException(
                    "Cannot initialize test, failure looking for supported models, please check "
                            + "the driver status",
                    nnApiDelegationFailure);
        }
    }
}