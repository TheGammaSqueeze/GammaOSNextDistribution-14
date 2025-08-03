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

package android.device.collectors;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.helpers.PerfettoHelper;

import org.junit.runner.Description;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A base {@link PerfettoTracingPerTestStrategy} that allows capturing traces for each test method
 * during testing and save the perfetto trace files under
 * <root>/<test_name>/PerfettoTracingStrategy/<test_name>-<invocation_count>.perfetto-trace
 */
public class PerfettoTracingPerTestStrategy extends PerfettoTracingStrategy {
    private Map<String, Integer> mTestIdInvocationCount = new HashMap<>();

    PerfettoTracingPerTestStrategy(Instrumentation instr) {
        super(instr);
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    public PerfettoTracingPerTestStrategy(PerfettoHelper helper, Instrumentation instr) {
        super(helper, instr);
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    PerfettoTracingPerTestStrategy(
            PerfettoHelper helper,
            Instrumentation instr,
            Map invocationMap,
            WakeLockContext wakeLockContext,
            Supplier<PowerManager.WakeLock> wakelockSupplier,
            WakeLockAcquirer wakeLockAcquirer,
            WakeLockReleaser wakeLockReleaser) {
        super(helper, instr, wakeLockContext, wakelockSupplier, wakeLockAcquirer, wakeLockReleaser);
        mTestIdInvocationCount = invocationMap;
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    protected PerfettoTracingPerTestStrategy(
            PerfettoHelper helper, Instrumentation instr, Map invocationMap) {
        super(instr);
        mTestIdInvocationCount = invocationMap;
    }

    @Override
    void testStart(DataRecord testData, Description description) {
        super.testStart(testData, description);
        Runnable task =
                () -> {
                    mTestIdInvocationCount.compute(
                            getTestFileName(description),
                            (key, value) -> (value == null) ? 1 : value + 1);
                    Log.i(getTag(), "Starting perfetto before test started.");
                    startPerfettoTracing();
                };

        runTask(task, "Holding a wakelock at onTestStart.");
    }

    @Override
    void testEnd(DataRecord testData, Description description) {
        if (!isPerfettoStartSuccess()) {
            Log.i(
                    getTag(),
                    "Skipping perfetto stop attempt onTestEnd because perfetto did not "
                            + "start successfully.");
            return;
        }

        if (skipMetric()) {
            stopPerfettoTracingWithoutMetric();
            return;
        }

        Runnable task =
                () -> {
                    Log.i(getTag(), "Stopping perfetto after test ended.");
                    // Construct test output directory in the below format
                    // <root>/<test_name>/PerfettoListener/<test_name>-<count>.perfetto-trace
                    Path path = getOutputPathModeTest(description);
                    stopPerfettoTracingAndReportMetric(path, testData);
                };

        runTask(task, "Holding a wakelock at onTestEnd.");
    }

    @SuppressLint("DefaultLocale")
    private Path getOutputPathModeTest(Description description) {
        return Paths.get(
                getTestOutputRoot(),
                getTestFileName(description),
                this.getClass().getSimpleName(),
                String.format(
                        "%s%s-%d.perfetto-trace",
                        getOutputFilePrefix(),
                        getTestFileName(description),
                        mTestIdInvocationCount.get(getTestFileName(description))));
    }
}
