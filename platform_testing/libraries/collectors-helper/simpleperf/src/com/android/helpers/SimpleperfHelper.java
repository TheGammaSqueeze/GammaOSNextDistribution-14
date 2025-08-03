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

package com.android.helpers;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * SimpleperfHelper is used to start and stop simpleperf sample collection and move the output
 * sample file to the destination folder.
 */
public class SimpleperfHelper {

    private static final String LOG_TAG = SimpleperfHelper.class.getSimpleName();
    private static final String SIMPLEPERF_TMP_FILE_PATH = "/data/local/tmp/perf.data";
    private static final String SIMPLEPERF_REPORT_TMP_FILE_PATH = "/data/local/tmp/perf_report.txt";

    private static final String SIMPLEPERF_START_CMD = "simpleperf %s -o %s %s";
    private static final String SIMPLEPERF_STOP_CMD = "pkill -INT simpleperf";
    private static final String SIMPLEPERF_PROC_ID_CMD = "pidof simpleperf";
    private static final String REMOVE_CMD = "rm %s";
    private static final String MOVE_CMD = "mv %s %s";

    private static final int SIMPLEPERF_START_WAIT_COUNT = 3;
    private static final int SIMPLEPERF_START_WAIT_TIME = 1000;
    private static final int SIMPLEPERF_STOP_WAIT_COUNT = 60;
    private static final long SIMPLEPERF_STOP_WAIT_TIME = 15000;

    private final UiDevice mUiDevice;

    /** Constructor to receive visible UiDevice. Should not be used except for testing. */
    @VisibleForTesting
    public SimpleperfHelper(UiDevice uidevice) {
        mUiDevice = uidevice;
    }

    public SimpleperfHelper() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public boolean startCollecting(String subcommand, String arguments) {
        try {
            // Cleanup any running simpleperf sessions.
            Log.i(LOG_TAG, "Cleanup simpleperf before starting.");
            if (isSimpleperfRunning()) {
                Log.i(LOG_TAG, "Simpleperf is already running. Stopping simpleperf.");
                if (!stopSimpleperf()) {
                    return false;
                }
            }

            Log.i(LOG_TAG, String.format("Starting simpleperf"));
            new Thread() {
                @Override
                public void run() {
                    String startCommand =
                            String.format(
                                    SIMPLEPERF_START_CMD,
                                    subcommand,
                                    SIMPLEPERF_TMP_FILE_PATH,
                                    arguments);
                    Log.i(LOG_TAG, String.format("Start command: %s", startCommand));
                    try {
                        String startOutput = mUiDevice.executeShellCommand(startCommand);
                        Log.i(
                                LOG_TAG,
                                String.format("Simpleperf start command output - %s", startOutput));
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Failed to start simpleperf.");
                    }
                }
            }.start();

            int waitCount = 0;
            while (!isSimpleperfRunning()) {
                if (waitCount < SIMPLEPERF_START_WAIT_COUNT) {
                    SystemClock.sleep(SIMPLEPERF_START_WAIT_TIME);
                    waitCount++;
                    continue;
                }
                Log.e(LOG_TAG, "Simpleperf sampling failed to start.");
                return false;
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to start simpleperf sampling due to :" + e.getMessage());
            return false;
        }
        Log.i(LOG_TAG, "Simpleperf sampling started successfully.");
        return true;
    }

    /**
     * Stop the simpleperf sample collection under /data/local/tmp/perf.data and copy the output to
     * the destination file.
     *
     * @param destinationFile file to copy the simpleperf sample file to.
     * @return true if the trace collection is successful otherwise false.
     */
    public boolean stopCollecting(String destinationFile) {
        Log.i(LOG_TAG, "Stopping simpleperf.");
        try {
            if (stopSimpleperf()) {
                if (!copyFileOutput(destinationFile)) {
                    return false;
                }
            } else {
                Log.e(LOG_TAG, "Simpleperf failed to stop");
                return false;
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to stop the simpleperf samping due to " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Utility method for sending the signal to stop simpleperf.
     *
     * @return true if simpleperf is successfully stopped.
     */
    public boolean stopSimpleperf() throws IOException {
        if (!isSimpleperfRunning()) {
            Log.e(LOG_TAG, "Simpleperf stop called, but simpleperf is not running.");
            return false;
        }

        String stopOutput = mUiDevice.executeShellCommand(SIMPLEPERF_STOP_CMD);
        Log.i(LOG_TAG, String.format("Simpleperf stop command ran: %s", SIMPLEPERF_STOP_CMD));
        int waitCount = 0;
        while (isSimpleperfRunning()) {
            if (waitCount < SIMPLEPERF_STOP_WAIT_COUNT) {
                SystemClock.sleep(SIMPLEPERF_STOP_WAIT_TIME);
                waitCount++;
                continue;
            }
            Log.e(LOG_TAG, "Simpleperf failed to stop");
            return false;
        }
        Log.i(LOG_TAG, "Simpleperf stopped successfully.");
        return true;
    }

    /**
     * Method for generating simpleperf report and getting report metrics.
     *
     * @param path Path to read binary record from.
     * @param processToPid Map with process names and PIDs to look for in record file.
     * @param symbols Symbols to report events from the processes recorded
     * @return Map containing recorded processes and nested map of symbols and event count for each
     *     symbol.
     */
    public Map<String /*event-process-symbol*/, String /*eventCount*/> getSimpleperfReport(
            String path,
            Map.Entry<String, String> processToPid,
            Map<String, String> symbols,
            int testIterations) {
        try {
            String reportCommand =
                    String.format(
                            "simpleperf report -i %s --pids %s --sort pid,symbol -o %s"
                                    + " --print-event-count --children",
                            path, processToPid.getValue(), SIMPLEPERF_REPORT_TMP_FILE_PATH);
            Log.i(LOG_TAG, String.format("Report command: %s", reportCommand));
            mUiDevice.executeShellCommand(reportCommand);
            return getMetrics(processToPid.getKey(), symbols, testIterations);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not generate report: " + e.getMessage());
        }
        return new HashMap<>();
    }

    /**
     * Utility method for extracting metrics from given simpleperf report.
     *
     * @param process Individually extracted processes recorded in binary record file.
     * @param symbols Symbols to report events from the processes recorded.
     * @return Map containing recorded event counts from symbols within process
     */
    private Map<String, String> getMetrics(
            String process, Map<String, String> symbols, int testIterations) {
        Map<String, String> results = new HashMap<>();
        try {
            String eventName = "";
            BufferedReader reader =
                    new BufferedReader(
                            new FileReader(SimpleperfHelper.SIMPLEPERF_REPORT_TMP_FILE_PATH));
            for (String line; (line = reader.readLine()) != null; ) {
                // Checking for top of the report to find event name and event count.
                // Event count: 3498520605
                if (line.contains(": ")) {
                    String[] splitLine = line.split(": ");
                    if (splitLine[0].equals("Event")) {
                        eventName = splitLine[1].split(" ")[0];
                    } else if (splitLine[0].equals("Event count")) {
                        String key = String.join("-", process, eventName);
                        long count = Long.parseLong(splitLine[1]) / testIterations;
                        results.put(key, String.valueOf(count));
                    }
                }
                // Parsing lines for specific symbols in report to store with event count to results
                // Children  Self    AccEventCount  SelfEventCount  Pid   Symbol
                // 54.20%    0.00%   122803507      0               2510  __start_thread
                else if (line.contains("%")) {
                    final String[] splitLine = line.split("\\s+", 6);
                    final String parsedSymbol = splitLine[5].trim();
                    final String matchedSymbol = getMatchingSymbol(symbols, parsedSymbol);
                    if (matchedSymbol == null) {
                        continue;
                    }
                    String key = String.join("-", process, matchedSymbol, eventName);
                    if (results.containsKey(key + "-percentage")) {
                        // We are searching for symbols with partial matches so only include the
                        // first hit if we get multiple matches.
                        continue;
                    }

                    // Remove trailing %
                    String percentage = splitLine[0].substring(0, splitLine[0].length() - 1);
                    results.put(key + "-percentage", percentage);
                    String eventCount = splitLine[2].trim();
                    long count = Long.parseLong(eventCount) / testIterations;
                    results.put(key + "-count", String.valueOf(count));
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not open report file: " + e.getMessage());
        }
        return results;
    }

    private static String getMatchingSymbol(Map<String, String> symbols, String parsedSymbol) {
        for (String candidate : symbols.keySet()) {
            if (parsedSymbol.contains(candidate)) {
                return symbols.get(candidate);
            }
        }
        return null;
    }

    /**
     * Convert process name into process ID usable for simpleperf commands
     *
     * @param process the name of a running process
     * @return String containing the process ID
     */
    public String getPID(String process) {
        try {
            return mUiDevice.executeShellCommand("pidof " + process).trim();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not resolve PID for " + process, e);
            return "";
        }
    }

    /**
     * Check if there is a simpleperf instance running.
     *
     * @return true if there is a running simpleperf instance, otherwise false.
     */
    private boolean isSimpleperfRunning() {
        try {
            String simpleperfProcId = mUiDevice.executeShellCommand(SIMPLEPERF_PROC_ID_CMD);
            Log.i(LOG_TAG, String.format("Simpleperf process id - %s", simpleperfProcId));
            if (simpleperfProcId.isEmpty()) {
                return false;
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to check simpleperf status: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Copy the temporary simpleperf output file to the given destinationFile.
     *
     * @param destinationFile file to copy simpleperf output into.
     * @return true if the simpleperf file copied successfully, otherwise false.
     */
    public boolean copyFileOutput(String destinationFile) {
        Path path = Paths.get(destinationFile);
        String destDirectory = path.getParent().toString();
        // Check if directory already exists
        File directory = new File(destDirectory);
        if (!directory.exists()) {
            boolean success = directory.mkdirs();
            if (!success) {
                Log.e(
                        LOG_TAG,
                        String.format(
                                "Result output directory %s not created successfully.",
                                destDirectory));
                return false;
            }
        }

        // Copy the collected trace from /data/local/tmp to the destinationFile.
        try {
            String moveResult =
                    mUiDevice.executeShellCommand(
                            String.format(MOVE_CMD, SIMPLEPERF_TMP_FILE_PATH, destinationFile));
            if (!moveResult.isEmpty()) {
                Log.e(
                        LOG_TAG,
                        String.format(
                                "Unable to move simpleperf output file from %s to %s due to %s",
                                SIMPLEPERF_TMP_FILE_PATH, destinationFile, moveResult));
                return false;
            }
        } catch (IOException e) {
            Log.e(
                    LOG_TAG,
                    "Unable to move the simpleperf sample file to destination file."
                            + e.getMessage());
            return false;
        }
        return true;
    }
}
