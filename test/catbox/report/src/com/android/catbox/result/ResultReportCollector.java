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

package com.android.catbox.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.util.CollectorUtil;

import com.android.ddmlib.Log.LogLevel;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.android.tradefed.log.LogUtil.CLog;

import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.base.Strings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.HashMap;
import java.util.Map;

/**
 * ResultReportCollector is an {@link ITargetPreparer} that pulls the test result reports from the
 * device and adds it to the Results.
 */
@OptionClass(alias = "result-report-collector")
public class ResultReportCollector implements ITargetPreparer {
    @Option(
            name = "pull-file-content-uri",
            description = "Copy the src files from device to destination using content uri")
    private Map<String, String> mFileContentUriMap = new HashMap<String, String>();

    @Option(
            name = "pull-file-path",
            description = "Copy the src files from device to destination using file path")
    private Map<String, String> mFilePathMap = new HashMap<String, String>();

    @Option(
            name = "pull-dir-path",
            description = "Copy the src directory from device to destination using directory path")
    private Map<String, String> mDirPathMap = new HashMap<String, String>();

    private static final String NO_RESULTS_STRING = "No result found.";
    private static final String ERROR_MESSAGE_TAG = "[ERROR]";

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        // Nothing To Do
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        // Pull files from device using Content URI
        pullFilesUsingContentUri(device, buildInfo, mFileContentUriMap);

        // Pull files from device using File Paths
        pullFilesUsingFilePaths(device, buildInfo, mFilePathMap);

        // Pull directories from device using Directory Paths
        pullDirUsingFilePaths(device, buildInfo, mDirPathMap);
    }

    /** Pull files from the device using Content URI */
    private void pullFilesUsingContentUri(ITestDevice device, IBuildInfo buildInfo,
            Map<String, String> contentUriMap) throws DeviceNotAvailableException {
        if (contentUriMap.isEmpty()) {
            // No Content URI provided
            return;
        }
        CLog.logAndDisplay(
                LogLevel.INFO,
                "Started pulling file using Content URI.");

        // Iterate over given Content URIs
        for (Map.Entry<String, String> entry: contentUriMap.entrySet()) {
            // Pull file using Content URI
            pullFileUsingContentUri(device, buildInfo, entry.getKey(), entry.getValue());
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                "Completed pulling file using Content URI.");
    }

    private void pullFileUsingContentUri(ITestDevice device, IBuildInfo buildInfo,
            String fileContentUri, String destFilePath) throws DeviceNotAvailableException {
        // Validate Source and Destination File Path
        if (Strings.isNullOrEmpty(fileContentUri) || Strings.isNullOrEmpty(destFilePath) ||
                !fileContentUri.startsWith("content://")) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Either Src or Dest Path is invalid. Source: %s, Destination: %s",
                            fileContentUri, destFilePath));
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                String.format("Started pulling file. Source File: %s, Destination File: %s",
                        fileContentUri, destFilePath));

        // Check if file exist on the device
        if (!doesFileExist(device, fileContentUri)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("File %s does not exist on the device.",
                            fileContentUri));
            return;
        }

        // Get Destination File using filepath
        File destinationFile = getDestinationFile(buildInfo, destFilePath);

        if (destinationFile == null) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to get destination file path for %s.",
                            destFilePath));
            return;
        }

        // Create the result directory if it does not exist
        // isDestFile=true : Since destination is a file
        if (!createResultDir(buildInfo, destinationFile, true /* isDestFile */)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to create results directory %s.",
                            destFilePath));
            return;
        }

        // Create Pull Command
        String pullCommand =
                String.format("content read --user %d --uri %s",
                        device.getCurrentUser(),
                        fileContentUri);

        // Open the output stream to the local file.
        // try-with-resource should close the stream once the try block completes
        // so we don't need a finally block to close the stream
        try (OutputStream localFileStream = new FileOutputStream(destinationFile)) {
            CommandResult pullResult = device.executeShellV2Command(pullCommand,
                    localFileStream);
            if (!isSuccessful(pullResult)) {
                String stderr = pullResult.getStderr();
                CLog.logAndDisplay(
                        LogLevel.ERROR,
                        String.format(
                                "Failed to pull a file at '%s' to %s. Error: '%s'",
                                fileContentUri, destFilePath, stderr));
            }
        } catch(IOException ex) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Failed to open OutputStream to the local file %s. Error: %s",
                            destFilePath, ex.getMessage()));
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                String.format("Completed pulling file. Source File: %s, Destination File: %s",
                        fileContentUri, destFilePath));
    }

    /** Verify if file exists  */
    private boolean doesFileExist(ITestDevice device, String contentUri)
            throws DeviceNotAvailableException {
        String queryContentCommand =
                String.format(
                        "content query --user %d --uri %s", device.getCurrentUser(), contentUri);

        String listCommandResult = device.executeShellCommand(queryContentCommand);

        return (!NO_RESULTS_STRING.equals(listCommandResult.trim()));
    }

    /** Verify Command Result for success  */
    private boolean isSuccessful(CommandResult result) {
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            return false;
        }
        String stdout = result.getStdout();
        if (stdout.contains(ERROR_MESSAGE_TAG)) {
            return false;
        }
        return Strings.isNullOrEmpty(result.getStderr());
    }

    /** Get Destination File using file path */
    private File getDestinationFile(IBuildInfo buildInfo, String destPath) {
        File destinationFile = null;
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        try {
            // get results directory
            destinationFile = buildHelper.getResultDir();
            // get destination file using path
            destinationFile = new File(destinationFile, destPath);
        } catch (IOException ex) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to get destination file %s: Error: %s.",
                            destPath, ex.getMessage()));
            return null;
        }
        return destinationFile;
    }

    /** Create directory for results */
    private boolean createResultDir(IBuildInfo buildInfo, File destination, boolean isDestFile) {
        File resultDir = destination;
        if (isDestFile) {
            // if filepath, get the parent for creating the directory
            resultDir = destination.getParentFile();
        }
        if (!resultDir.exists() && !resultDir.mkdirs()) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to create %s directory", resultDir.getAbsolutePath()));
            return false;
        }
        if (!resultDir.isDirectory()) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("%s is not a directory", resultDir.getAbsolutePath()));
            return false;
        }
        return true;
    }

    /** Pull files from the device using File Path */
    private void pullFilesUsingFilePaths(ITestDevice device, IBuildInfo buildInfo,
            Map<String, String> filePathMap) throws DeviceNotAvailableException {
        if (filePathMap.isEmpty()) {
            // No File Paths provided
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                "Started pulling file using file path.");

        // Iterate over given Paths
        for (Map.Entry<String, String> entry: filePathMap.entrySet()) {
            // Pull file using File Path
            pullFileUsingFilePath(device, buildInfo, entry.getKey(), entry.getValue());
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                "Completed pulling file using file path.");
    }

    /** Pull file using File Path */
    private void pullFileUsingFilePath(ITestDevice device, IBuildInfo buildInfo, String filePath,
            String destPath) throws DeviceNotAvailableException {
        // Validate Source and Destination File Path
        if (Strings.isNullOrEmpty(filePath) || Strings.isNullOrEmpty(destPath)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Either Src or Dest Path is invalid. Source: %s, Destination: %s",
                            filePath, destPath));
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                String.format("Started pulling file. Source File: %s, Destination File: %s",
                        filePath, destPath));

        // Check if file exist on the device
        if (!device.doesFileExist(filePath)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("File %s does not exist on the device.",
                            filePath));
            return;
        }

        // Get Destination File using filepath
        File destinationFile = getDestinationFile(buildInfo, destPath);

        if (destinationFile == null) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to get destination file path for %s.",
                            destPath));
            return;
        }

        // Create the result directory if it does not exist
        // isDestFile=true : Since destination is a file
        if (!createResultDir(buildInfo, destinationFile, true /* isDestFile */)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to create results directory %s.",
                            destPath));
            return;
        }

        // Pull File
        boolean isSuccess = device.pullFile(filePath, destinationFile);

        if (!isSuccess) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Failed to pull the file. Source File: %s, Destination File: %s",
                            filePath, destPath));
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                String.format("Completed pulling file. Source File: %s, Destination File: %s",
                        filePath, destPath));
    }

    /** Pull directories from the device using File Path */
    private void pullDirUsingFilePaths(ITestDevice device, IBuildInfo buildInfo,
            Map<String, String> dirPathMap) throws DeviceNotAvailableException {
        if (dirPathMap.isEmpty())  {
            // No Dir Paths provided
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                "Started pulling directory using file path.");

        // Iterate over given Paths
        for (Map.Entry<String, String> entry: dirPathMap.entrySet()) {
            // Pull directory using Dir Path
            pullDirectoryUsingDirPath(device, buildInfo, entry.getKey(), entry.getValue());
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                "Completed pulling directory using file path.");
    }

    /** Pull Dir using Dir Path */
    private void pullDirectoryUsingDirPath(ITestDevice device, IBuildInfo buildInfo, String dirPath,
            String destPath) throws DeviceNotAvailableException {
        // Validate Source and Destination File Path
        if (Strings.isNullOrEmpty(dirPath) || Strings.isNullOrEmpty(destPath)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Either Src or Dest Path is invalid. Source: %s, Destination: %s",
                            dirPath, destPath));
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                String.format("Started pulling directory. Source File: %s, Destination File: %s",
                        dirPath, destPath));

        // Check if directory exist on the device
        if (!device.doesFileExist(dirPath)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("File %s does not exist on the device.",
                            dirPath));
            return;
        }

        // Get Destination Dir using filepath
        File destinationDir = getDestinationFile(buildInfo, destPath);

        if (destinationDir == null) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to get destination file path for %s.",
                            destPath));
            return;
        }

        // Create the result directory if it does not exist
        // isDestFile=false : Since destination is a directory
        if (!createResultDir(buildInfo, destinationDir, false /* isDestFile */)) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Unable to create results directory %s.",
                            destPath));
            return;
        }

        // Pull Dir
        boolean isSuccess = device.pullDir(dirPath, destinationDir);

        if (!isSuccess) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    String.format("Failed to pull directory. Source Dir: %s, Destination File: %s",
                            dirPath, destPath));
            return;
        }

        CLog.logAndDisplay(
                LogLevel.INFO,
                String.format("Completed pulling directory. Source File: %s, Destination File: %s",
                        dirPath, destPath));
    }
}
