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

package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;
import java.util.stream.Stream;

/** A helper class for activating Python 3 virtual environment. */
public class PythonVirtualenvHelper {

    private static final String PATH = "PATH";
    private static final String PYTHONHOME = "PYTHONHOME";
    private static final String PYTHONPATH = "PYTHONPATH";
    public static final String VIRTUAL_ENV = "VIRTUAL_ENV";

    /**
     * Gets python bin directory path.
     *
     * <p>This method will check the directory existence.
     *
     * @return str, the path to the python bin directory in venv.
     * @throws NullPointerException if arg virtualenvPath is null.
     * @throws RuntimeException if /path/to/venv/bin does not exist.
     */
    public static String getPythonBinDir(String virtualenvPath) {
        if (virtualenvPath == null) {
            throw new NullPointerException(
                    "Path to the Python virtual environment should not be null");
        }
        File res = new File(virtualenvPath, "bin");
        if (!res.exists()) {
            throw new RuntimeException("Invalid python virtualenv path " + res.getAbsolutePath());
        }
        return res.getAbsolutePath();
    }

    /**
     * Activate virtualenv for a RunUtil.
     *
     * @param runUtil an utility object for running virtualenv activation commands.
     * @param virtualenvDir a File object representing the created virtualenv directory.
     */
    public static void activate(IRunUtil runUtil, File virtualenvDir) {
        activate(runUtil, virtualenvDir.getAbsolutePath());
    }

    /**
     * Activate virtualenv for a RunUtil.
     *
     * <p>This method will check for python bin directory existence
     *
     * @param runUtil an utility object for running virtualenv activation commands.
     * @param virtualenvPath the path to the created virtualenv directory.
     */
    public static void activate(IRunUtil runUtil, String virtualenvPath) {
        String pythonBinDir = getPythonBinDir(virtualenvPath);
        String separater = ":";
        String pythonPath =
                getPackageInstallLocation(runUtil, virtualenvPath)
                        + separater
                        + System.getenv(PYTHONPATH);
        runUtil.setEnvVariable(PATH, pythonBinDir + separater + System.getenv().get(PATH));
        runUtil.setEnvVariable(VIRTUAL_ENV, virtualenvPath);
        runUtil.setEnvVariable(PYTHONPATH, pythonPath);
        runUtil.unsetEnvVariable(PYTHONHOME);
        CLog.d("Activating virtual environment:");
        CLog.d("%s: %s", PATH, pythonBinDir + separater + System.getenv().get(PATH));
        CLog.d("%s: %s", VIRTUAL_ENV, virtualenvPath);
        CLog.d("%s: %s", PYTHONPATH, pythonPath);
    }

    /**
     * Gets the absolute path to the pip3 binary in the given venv directory.
     *
     * @param virtualenvPath the path to the venv directory.
     * @return a string representing the absolute path to the pip3 binary.
     */
    private static String getPipPath(String virtualenvPath) {
        File pipFile = new File(PythonVirtualenvHelper.getPythonBinDir(virtualenvPath), "pip3");
        return pipFile.getAbsolutePath();
    }

    /**
     * Gets python package install location.
     *
     * <p>This method will call /path/to/venv/bin/pip3 show pip and parse out package location from
     * stdout output.
     *
     * @param runUtil an utility object for running for running commands.
     * @param virtualenvPath the path to the created virtualenv directory.
     * @return a string representing the absolute path to the location where Python packages are
     *     installed.
     */
    public static String getPackageInstallLocation(IRunUtil runUtil, String virtualenvPath) {
        CommandResult result =
                runUtil.runTimedCmd(60000, getPipPath(virtualenvPath), "show", "pip");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new RuntimeException(
                    String.format(
                            "Fail to run command: %s show pip.\nStatus:%s\nStdout:%s\nStderr:%s",
                            getPipPath(virtualenvPath),
                            result.getStatus(),
                            result.getStdout(),
                            result.getStderr()));
        }
        String stdout = result.getStdout();
        String[] lines = stdout.split("\n");
        String locationLine =
                Stream.of(lines).filter(x -> x.startsWith("Location")).findFirst().orElse("");
        return locationLine.split(" ")[1];
    }
}
