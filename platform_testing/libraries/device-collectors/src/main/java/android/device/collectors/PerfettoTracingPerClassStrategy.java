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

import android.app.Instrumentation;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.helpers.PerfettoHelper;

import org.junit.runner.Description;
import org.junit.runner.Result;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A base {@link PerfettoTracingPerRunStrategy} that allows capturing traces for each test class
 * during testing and save the perfetto trace files under
 * <root>/<test_name>/PerfettoTracingStrategy/<test class>.perfetto-trace
 */
public class PerfettoTracingPerClassStrategy extends PerfettoTracingStrategy {
    private List<String> mFileList = new ArrayList<>();
    private Description mLastDescription;

    PerfettoTracingPerClassStrategy(Instrumentation instr) {
        super(instr);
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    public PerfettoTracingPerClassStrategy(PerfettoHelper helper, Instrumentation instr) {
        super(helper, instr);
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    PerfettoTracingPerClassStrategy(
            PerfettoHelper helper,
            Instrumentation instr,
            WakeLockContext wakeLockContext,
            Supplier<PowerManager.WakeLock> wakelockSupplier,
            WakeLockAcquirer wakeLockAcquirer,
            WakeLockReleaser wakeLockReleaser) {
        super(helper, instr, wakeLockContext, wakelockSupplier, wakeLockAcquirer, wakeLockReleaser);
    }

    @Override
    void testStart(DataRecord testData, Description description) {
        boolean isClassChanging =
                mLastDescription == null
                        || !description.getClassName().equals(mLastDescription.getClassName());
        if (!isClassChanging) {
            return;
        }

        if (isPerfettoStartSuccess()) {
            Log.d(getTag(), "Stopping perfetto on test end");
            doStopTrace(testData, /* isTestRunEnd */ false);
        }

        doStartOnClassChange(description);
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

        Log.d(getTag(), "Stopping perfetto on test run end");
        doStopTrace(runData, /* isTestRunEnd */ true);
    }

    private void doStopTrace(DataRecord dataRecord, Boolean isTestRunEnd) {
        if (skipMetric()) {
            stopPerfettoTracingWithoutMetric();
        } else {
            Runnable task =
                    () -> {
                        // Construct test output directory in the below format
                        // <root>/<test_name>/PerfettoListener/<test_name>-<count>.perfetto-trace
                        Path path = getOutputPathModeClass();

                        if (!stopPerfettoTracing(path)) {
                            return;
                        }

                        mFileList.add(path.toString());
                        if (isTestRunEnd) {
                            uploadMetrics(dataRecord);
                        }
                    };
            runTask(task, "Holding a wakelock when stopping trace");
        }
    }

    private void uploadMetrics(DataRecord dataRecord) {
        int counter = 0;
        for (String filePath : mFileList) {
            String metricName = PERFETTO_FILE_PATH + counter++;
            dataRecord.addStringMetric(metricName, filePath);
        }
    }

    private void doStartOnClassChange(Description description) {
        mLastDescription = description;

        Runnable task =
                () -> {
                    Log.i(getTag(), "Starting perfetto before test started.");
                    startPerfettoTracing();
                };

        runTask(task, "Holding a wakelock at onTestStart.");

        if (isPerfettoStartSuccess()) {
            Log.e(
                    getTag(),
                    String.format(
                            "Unable to start perfetto onTestStart (%s) because tracing was active",
                            description.toString()));
        }
    }

    private Path getOutputPathModeClass() {
        return Paths.get(
                getTestOutputRoot(),
                this.getClass().getSimpleName(),
                String.format(
                        "%s%s.perfetto-trace",
                        getOutputFilePrefix(), sanitizeString(mLastDescription.getClassName())));
    }
}
