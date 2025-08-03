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
package com.android.tradefed.util;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A utility class to help various test runners. */
public class TestRunnerUtil {

    @VisibleForTesting static TestRunnerUtil singleton = new TestRunnerUtil();

    /**
     * Get the value of an environment variable.
     *
     * <p>The wrapper function is created for mock in unit test.
     *
     * @param name the name of the environment variable.
     * @return {@link String} value of the given environment variable.
     */
    @VisibleForTesting
    String getEnv(String name) {
        return System.getenv(name);
    }

    /**
     * Return LD_LIBRARY_PATH for hostside tests that require native library.
     *
     * @param testFile the {@link File} of the test module
     * @return a string specifying the colon separated library path.
     */
    public static String getLdLibraryPath(File testFile) {
        List<String> paths = new ArrayList<>();

        String libs[] = {"lib", "lib64"};
        String androidHostOut = singleton.getEnv("ANDROID_HOST_OUT");
        String testcasesFolderPath = getTestcasesFolderPath(testFile);
        for (String lib : libs) {
            File libFile = new File(testFile.getParentFile().getAbsolutePath(), lib);
            if (libFile.exists()) {
                paths.add(libFile.getAbsolutePath());
            }
            // Include `testcases` directory for running tests based on test zip.
            if (testcasesFolderPath != null) {
                libFile = new File(testcasesFolderPath, lib);
                if (libFile.exists()) {
                    paths.add(libFile.getAbsolutePath());
                }
            }
            // Include ANDROID_HOST_OUT/lib to support local case.
            if (androidHostOut != null) {
                libFile = new File(androidHostOut, lib);
                if (libFile.exists()) {
                    paths.add(libFile.getAbsolutePath());
                }
            }
        }
        if (paths.isEmpty()) {
            return null;
        }
        String ldLibraryPath = String.join(java.io.File.pathSeparator, paths);
        CLog.d("Identify LD_LIBRARY_PATH to be used: %s", ldLibraryPath);
        return ldLibraryPath;
    }

    /** Return the path to the testcases folder or null if it doesn't exist. */
    private static String getTestcasesFolderPath(File testFile) {
        // Assume test binary path is like
        // /tmp/tf-workfolder/testsdir/host/testcases/test_name/x86_64/binary_name
        File folder = testFile.getParentFile();
        if (folder != null) {
            folder = folder.getParentFile();
            if (folder != null) {
                folder = folder.getParentFile();
                if (folder != null) return folder.getAbsolutePath();
            }
        }
        return null;
    }
}
