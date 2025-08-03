/*
 * Copyright 2022 The Android Open Source Project
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

import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MemLeaksHelper parses unreachable memory from dumpsys meminfo --unreachable <PID>. */
public class MemLeaksHelper implements ICollectorHelper<Long> {
    private static final String TAG = MemLeaksHelper.class.getSimpleName();
    private static final String MEM_NAME_PATTERN = "MEMINFO in pid %d \\[(?<processname>.*)\\]";
    private static final String MEM_LEAKS_PATTERN =
            "(?<bytes>[0-9]+) bytes in (?<allocations>[0-9]+) unreachable allocations";

    @VisibleForTesting public static final String ALL_PROCESS_CMD = "ps -A";
    @VisibleForTesting
    public static final String DUMPSYS_MEMIFNO_CMD = "dumpsys meminfo --unreachable %d";
    @VisibleForTesting public static final String PIDOF_CMD = "pidof %s";
    @VisibleForTesting public static final String PROC_MEM_BYTES = "proc_unreachable_memory_bytes_";
    @VisibleForTesting
    public static final String PROC_ALLOCATIONS = "proc_unreachable_allocations_";

    private boolean mDiffOnFlag = true;
    private boolean mCollectAllProcFlag = true;
    private String[] mProcessNames;
    private String mPidOutput;
    private UiDevice mUiDevice;
    private Map<String, Long> mPrevious = new HashMap<>();

    /**
     * Sets up the helper before it starts collecting.
     *
     * @param procNames process names to collect
     */
    public void setUp(boolean diffOn, boolean collectAllflag, String[] procNames) {
        mDiffOnFlag = diffOn;
        mCollectAllProcFlag = collectAllflag;
        mProcessNames = procNames;
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Override
    public boolean startCollecting() {
        if (mDiffOnFlag) {
            mPrevious = getMeminfo();
        }
        return true;
    }

    @Override
    public boolean stopCollecting() {
        return true;
    }

    @Override
    public Map<String, Long> getMetrics() {
        Map<String, Long> current = getMeminfo();
        Map<String, Long> results = new HashMap<>();

        if (mDiffOnFlag) {
            for (String processName : current.keySet()) {
                if (mPrevious.containsKey(processName)) {
                    results.put(processName, current.get(processName) - mPrevious.get(processName));
                } else {
                    results.put(processName, current.get(processName));
                }
            }
        } else {
            return current;
        }
        return results;
    }

    /**
     * Get unreachable memory information
     *
     * @return a Map<String, Long> meminfo - a pair of process name and its values
     */
    private Map<String, Long> getMeminfo() {
        // Get all the process PIDs first
        Map<Integer, String> pids = getPids();
        Map<String, Long> results = new HashMap<>();

        if (pids.size() == 0) {
            Log.e(TAG, "Failed to get all the valid process PIDs");
            return results;
        }

        for (Integer pid : pids.keySet()) {
            String dumpOutput;
            try {
                dumpOutput = executeShellCommand(String.format(DUMPSYS_MEMIFNO_CMD, pid));
                Log.i(TAG, "dumpsys meminfo --unreachable: " + dumpOutput);
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to run " + String.format(DUMPSYS_MEMIFNO_CMD, pid) + ".", ioe);
                continue;
            }

            Pattern patternName =
                    Pattern.compile(
                            String.format(MEM_NAME_PATTERN, pid),
                            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Pattern patternLeak =
                    Pattern.compile(
                            MEM_LEAKS_PATTERN, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

            Matcher matcherName = patternName.matcher(dumpOutput);
            Matcher matcherLeak = patternLeak.matcher(dumpOutput);
            boolean nameFound = matcherName.find();
            boolean byteFound = matcherLeak.find();

            // If process name does not show in the output, which is identified as an
            // non-java process. We can simply skip it.
            if (!nameFound) {
                continue;
            }

            String processName = pids.get(pid);
            if (byteFound) {
                results.put(PROC_MEM_BYTES + processName, Long.parseLong(matcherLeak.group(1)));
                results.put(PROC_ALLOCATIONS + processName, Long.parseLong(matcherLeak.group(2)));
            } else {
                // If we don't find unreachable memory and allocations, report 0
                // If the process name shows in the output, we should also see its unreachable
                // memory info even unreachable memory or allocations is in zero.
                Log.w(TAG, "Unreachable memory info is missing when querying the " + processName);
                results.put(PROC_MEM_BYTES + processName, 0L);
                results.put(PROC_ALLOCATIONS + processName, 0L);
            }
        }
        return results;
    }

    /**
     * Get pid of all processes excluding process names enclosed in "[]"
     *
     * @return a Map<Integer, String> pids - a pair of processes PID and its name
     */
    private Map<Integer, String> getPids() {
        // return pids
        Map<Integer, String> pids = new HashMap<>();
        if (mCollectAllProcFlag) {
            try {
                String pidOutput = executeShellCommand(ALL_PROCESS_CMD);
                // Sample output for the process info
                // Sample command : "ps -A"
                // Sample output :
                // system   4533   410 13715708 78536 do_freezer_trap 0 S    com.android.keychain
                // root    32552     2        0     0   worker_thread 0 I [kworker/6:0-memlat_wq]
                String[] lines = pidOutput.split(System.lineSeparator());
                for (String line : lines) {
                    String[] splitLine = line.split("\\s+");
                    // Skip the first (i.e header) line from "ps -A" output.
                    if (splitLine[1].equalsIgnoreCase("PID")) {
                        continue;
                    }
                    String processName = splitLine[splitLine.length - 1].replace("\n", "").trim();
                    // Skip the process names enclosed in "[]"
                    if (processName.startsWith("[") && processName.endsWith("]")) {
                        continue;
                    }
                    pids.put(Integer.parseInt(splitLine[1]), processName);
                }
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to get pid of all processes.", ioe);
                return new HashMap<>();
            }
        } else if (mProcessNames.length > 0) {
            for (int i = 0; i < mProcessNames.length; i++) {
                try {
                    mPidOutput = executeShellCommand(String.format(PIDOF_CMD, mProcessNames[i]));
                } catch (IOException ioe) {
                    Log.e(TAG, "Failed to run " + String.format(PIDOF_CMD, mProcessNames[i]), ioe);
                    continue;
                }
                pids.put(Integer.parseInt(mPidOutput.replace("\n", "").trim()), mProcessNames[i]);
            }
        } else {
            Log.w(TAG, "No process names were provided.");
            return new HashMap<>();
        }
        return pids;
    }

    /* Execute a shell command and return its output. */
    @VisibleForTesting
    public String executeShellCommand(String command) throws IOException {
        return mUiDevice.executeShellCommand(command);
    }
}
