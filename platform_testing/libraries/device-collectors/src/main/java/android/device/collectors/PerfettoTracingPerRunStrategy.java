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
import org.junit.runner.Result;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A base {@link PerfettoTracingPerRunStrategy} that allows capturing traces for each run during
 * testing and save the perfetto trace files under
 * <root>/<test_name>/PerfettoTracingStrategy/<random UUID>-<invocation_count>.perfetto-trace
 */
public class PerfettoTracingPerRunStrategy extends PerfettoTracingStrategy {
    PerfettoTracingPerRunStrategy(Instrumentation instr) {
        super(instr);
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    public PerfettoTracingPerRunStrategy(PerfettoHelper helper, Instrumentation instr) {
        super(helper, instr);
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    PerfettoTracingPerRunStrategy(
            PerfettoHelper helper,
            Instrumentation instr,
            WakeLockContext wakeLockContext,
            Supplier<PowerManager.WakeLock> wakelockSupplier,
            WakeLockAcquirer wakeLockAcquirer,
            WakeLockReleaser wakeLockReleaser) {
        super(helper, instr, wakeLockContext, wakelockSupplier, wakeLockAcquirer, wakeLockReleaser);
    }

    @Override
    void testRunStart(DataRecord runData, Description description) {
        super.testRunStart(runData, description);

        Runnable task =
                () -> {
                    Log.i(getTag(), "Starting perfetto before test run started.");
                    startPerfettoTracing();
                };
        runTask(task, "Holding a wakelock at onTestRunSTart.");
    }

    @Override
    void testRunEnd(DataRecord runData, Result result) {
        if (!isPerfettoStartSuccess()) {
            Log.i(
                    getTag(),
                    "Skipping perfetto stop attempt because perfetto did not "
                            + "start successfully.");
            return;
        }

        Runnable task =
                () -> {
                    Log.i(getTag(), "Stopping perfetto after test run ended.");
                    // Construct test output directory in the below format
                    // <root_folder>/PerfettoListener/<className>.perfetto-trace
                    Path path = getOutputPathModeRun();
                    stopPerfettoTracingAndReportMetric(path, runData);
                };

        runTask(task, "Holding a wakelock at onTestRunEnd.");
    }

    @SuppressLint("DefaultLocale")
    private Path getOutputPathModeRun() {
        return Paths.get(
                getTestOutputRoot(),
                this.getClass().getSimpleName(),
                String.format(
                        "%s%d.perfetto-trace",
                        getOutputFilePrefix(), UUID.randomUUID().hashCode()));
    }
}
