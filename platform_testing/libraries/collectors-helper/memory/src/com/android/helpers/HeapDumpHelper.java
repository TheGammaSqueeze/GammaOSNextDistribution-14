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

package com.android.helpers;

import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * HeapDumpHelper is a helper used to collect the heapdump and store the output in a
 * file created using the given id.
 */
public class HeapDumpHelper implements ICollectorHelper<String> {
    private static final String TAG = HeapDumpHelper.class.getSimpleName();
    private static final String HEAPDUMP_MANAGED_OUTPUT_FILE_METRIC_NAME = "managed_heapdump_file_";
    private static final String HEAPDUMP_NATIVE_OUTPUT_FILE_METRIC_NAME = "native_heapdump_file_";
    private static final String HEAPDUMP_CMD = "am dumpheap %s %s";
    private static final String NATIVE_HEAPDUMP_CMD = "am dumpheap -n %s %s";
    private static final String PIDOF_CMD = "pidof %s";
    private static final String MV_CMD = "mv %s %s";

    @VisibleForTesting
    static final String MANAGED_HEAPDUMP_EMPTY_FILES_COUNT_METRIC =
            "managed_heapdump_empty_files_count";

    @VisibleForTesting
    static final String NATIVE_HEAPDUMP_EMPTY_FILES_COUNT_METRIC =
            "native_heapdump_empty_files_count";

    String mId = null;
    File mResultsFile = null;
    private String[] mProcessNames = null;
    private Path mTestOutputDir;
    private boolean mNativeHeapDumpEnabled = false;
    private UiDevice mUiDevice;
    HashMap<String, String> mHeapDumpFinalMap;

    @Override
    public boolean startCollecting() {
        return true;
    }

    public void setUp(String testOutputDir, String... processNames) {
        mProcessNames = processNames;
        mTestOutputDir = Paths.get(testOutputDir);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
      }

    @Override
    public boolean startCollecting(String id) {
        mId = id;
        mHeapDumpFinalMap = new HashMap<>();
        if (!collectHeapDump()) {
            return false;
        }
        return true;
    }

    @Override
    public Map<String, String> getMetrics() {
        Log.i(TAG, "Metric collector enabled. Dumping the hprof.");
        int processCount = 0;
        int managedEmptyFilesCount = 0;
        int nativeEmptyFilesCount = 0;
        for (String processName : mProcessNames) {
            String pid = getPid(processName);
            // If the PID doesn't exist, we don't need to run the dumpheap command
            if (pid.isEmpty()) {
                continue;
            }
            if (mId != null && !mId.isEmpty()) {
                try {
                    processCount++;
                    String fileName =
                            String.format(
                                    "%s%s_%s.hprof",
                                    HEAPDUMP_MANAGED_OUTPUT_FILE_METRIC_NAME,
                                    processName.replace("/", "#"),
                                    mId);
                    String finalHeapDumpPath = mTestOutputDir.resolve(fileName).toString();
                    execHeapDump(pid, processName, finalHeapDumpPath, false);
                    if (isEmptyFile(finalHeapDumpPath)) {
                        managedEmptyFilesCount++;
                        finalHeapDumpPath = renameEmptyFile(mTestOutputDir, fileName);
                    }
                    mHeapDumpFinalMap.put(
                            HEAPDUMP_MANAGED_OUTPUT_FILE_METRIC_NAME + processCount,
                            finalHeapDumpPath);
                    if (mNativeHeapDumpEnabled) {
                        String nativeFileName =
                                String.format(
                                        "%s%s_%s.txt",
                                        HEAPDUMP_NATIVE_OUTPUT_FILE_METRIC_NAME,
                                        processName.replace("/", "#"),
                                        mId);
                        String finalNativeHeapDumpPath =
                                mTestOutputDir.resolve(nativeFileName).toString();
                        execHeapDump(pid, processName, finalNativeHeapDumpPath, true);
                        if (isEmptyFile(finalNativeHeapDumpPath)) {
                            nativeEmptyFilesCount++;
                            finalNativeHeapDumpPath =
                                    renameEmptyFile(mTestOutputDir, nativeFileName);
                        }
                        mHeapDumpFinalMap.put(
                                HEAPDUMP_NATIVE_OUTPUT_FILE_METRIC_NAME + processCount,
                                finalNativeHeapDumpPath);
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "dumpheap command failed", e);
                }
            } else {
                Log.e(TAG, "Metric collector is enabled but the heap dump file id is not valid.");
            }
        }
        mHeapDumpFinalMap.put(
                MANAGED_HEAPDUMP_EMPTY_FILES_COUNT_METRIC, String.valueOf(managedEmptyFilesCount));
        mHeapDumpFinalMap.put(
                NATIVE_HEAPDUMP_EMPTY_FILES_COUNT_METRIC, String.valueOf(nativeEmptyFilesCount));
        return mHeapDumpFinalMap;
    }

    /** Get the pid of a process name */
    private String getPid(String processName) {
        String output = "";
        try {
            output = mUiDevice.executeShellCommand(String.format(PIDOF_CMD, processName));
            Log.i(TAG, String.format("The PID of %s is %s.", processName, output));
        } catch (IOException e) {
            Log.e(TAG, String.format("Failed to get the pid of %s", processName), e);
        }
        return output;
    }

    private String execHeapDump(
            String pid, String processName, String filePath, boolean isNativeHeapDump)
            throws IOException {
        try {
            String heapdumpCommand =
                    isNativeHeapDump
                            ? String.format(NATIVE_HEAPDUMP_CMD, pid, filePath)
                            : String.format(HEAPDUMP_CMD, pid, filePath);
            Log.i(TAG, "Running heapdump command :" + heapdumpCommand);
            return mUiDevice.executeShellCommand(heapdumpCommand);
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Unable to execute heapdump command for %s ", processName), e);
        }
    }

    /**
     * Returns true if heap dump collection is enabled and heap dump file name is valid.
     */
    private boolean collectHeapDump() {
        if (mId == null || mId.isEmpty()) {
            return false;
        }
        return true;
    }

    /** Returns true if heapdump file is empty */
    private boolean isEmptyFile(String path) throws IOException {
        long bytes = Files.size(Paths.get(path));
        Log.i(TAG, String.format("File size of %s is %s bytes", path, bytes));
        return bytes < 10;
    }

    /** Rename an empty file */
    private String renameEmptyFile(Path dir, String fileName) {
        String oldFile = dir.resolve(fileName).toString();
        String newFile = dir.resolve("EMPTY-" + fileName).toString();
        try {
            mUiDevice.executeShellCommand(String.format(MV_CMD, oldFile, newFile));
            return newFile;
        } catch (IOException e) {
            Log.i(TAG, String.format("Rename %s failed.", oldFile), e);
        }
        return oldFile;
    }

    @Override
    public boolean stopCollecting() {
        mId = null;
        return true;
    }

    public void enableNativeHeapDump() {
        mNativeHeapDumpEnabled = true;
    }

    public Map<String,String> getFinalResultsMap() {
        return mHeapDumpFinalMap;
    }
}
