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

package com.android.helpers;

import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper to run the generic collector that runs binary files that output metrics in a fixed format.
 * <a href="http://go/generic-collector">(Design doc)</a>
 */
public class GenericExecutableCollectorHelper implements ICollectorHelper<String> {
    private static final String TAG = GenericExecutableCollectorHelper.class.getSimpleName();
    private static final String CSV_SEPARATOR = ",";
    private static final String METRIC_KEY_SEPARATOR = "_";

    private Path mExecutableDir;
    private UiDevice mUiDevice;
    private List<Path> mExecutableFilePaths;

    /**
     * Setup
     *
     * @param executableDir a string of executable directory
     */
    public void setUp(String executableDir) {
        mExecutableDir = Paths.get(executableDir);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        if (mExecutableDir == null || !Files.isDirectory(mExecutableDir)) {
            throw new IllegalArgumentException(
                    "Executable directory was not a directory or was not specified.");
        }
        mExecutableFilePaths = listFilesInAllSubdirs(mExecutableDir);
        Log.i(
                TAG,
                String.format(
                        "Found the following files: %s",
                        mExecutableFilePaths.stream()
                                .map(Path::toString)
                                .collect(Collectors.joining(", "))));
        if (mExecutableFilePaths.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No test file found in the directory %s", mExecutableDir));
        }
    }

    @Override
    public boolean startCollecting() {
        return true;
    }

    @Override
    public Map<String, String> getMetrics() {
        Map<String, String> results = new HashMap<>();
        mExecutableFilePaths.forEach(
                (path) -> {
                    try {
                        results.putAll(execAndGetResults(path));
                    } catch (IOException e) {
                        Log.e(TAG, String.format("Failed to execute file: %s", path), e);
                    }
                });
        return results;
    }

    @Override
    public boolean stopCollecting() {
        return true;
    }

    /**
     * List all files from a directory, including all levels of sub-directories.
     *
     * @param dir: a path of directory
     * @return return: a list of paths of executable files
     */
    private List<Path> listFilesInAllSubdirs(Path dir) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> allFilesAndDirs = Files.walk(dir)) {
            result = allFilesAndDirs.filter(Files::isRegularFile).collect(Collectors.toList());
        } catch (IOException e) {
            Log.e(TAG, String.format("Failed to walk the files under path %s", dir), e);
        }
        return result;
    }

    /**
     * Running the binary by shell command and reformatting the output.
     *
     * <p>Example of output = "name,binder_use,binder_started,count\n" + "DockObserver,0,32,2\n" +
     * "SurfaceFlinger,0,5,8\n" + "SurfaceFlingerAIDL,0,5,8\n";
     *
     * <p>Example of lines = ["name,binder_use,binder_started,count", "DockObserver,0,32,2",
     * "SurfaceFlinger,0,5,8", "SurfaceFlingerAIDL,0,5,8"]
     *
     * <p>Example of headers = ["name", "binder_use", "binder_started", "count"]
     *
     * <p>Example of result = { "DockObserver_binder_use" : 0 "DockObserver_binder_started" : 32
     * "DockObserver_count" : 2 }
     *
     * @param executable: a path of the executable file path
     * @return result: a map including the metrics and values from the output
     * @throws IOException if the shell command runs into errors
     */
    private Map<String, String> execAndGetResults(Path executable) throws IOException {
        String prefix = mExecutableDir.relativize(executable).toString();
        Map<String, String> result = new HashMap<>();
        String output = executeShellCommand(executable.toString());
        if (output.length() <= 0) {
            return result;
        }
        String[] lines = output.split(System.lineSeparator());
        String[] headers = lines[0].split(CSV_SEPARATOR);
        for (int row = 1; row < lines.length; row++) {
            String[] l = lines[row].split(CSV_SEPARATOR);
            for (int col = 1; col < l.length; col++) {
                result.put(String.join(METRIC_KEY_SEPARATOR, prefix, l[0], headers[col]), l[col]);
            }
        }
        return result;
    }

    /**
     * Execute a shell command and return its output.
     *
     * @param command a string of command
     */
    @VisibleForTesting
    public String executeShellCommand(String command) throws IOException {
        return mUiDevice.executeShellCommand(command);
    }
}
