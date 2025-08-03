/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.csuite.core;

import android.service.dropbox.DropBoxManagerServiceDumpProto;
import android.service.dropbox.DropBoxManagerServiceDumpProto.Entry;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A utility class that contains common methods to interact with the test device. */
public class DeviceUtils {
    @VisibleForTesting static final String UNKNOWN = "Unknown";
    @VisibleForTesting static final String VERSION_CODE_PREFIX = "versionCode=";
    @VisibleForTesting static final String VERSION_NAME_PREFIX = "versionName=";
    @VisibleForTesting static final String RESET_PACKAGE_COMMAND_PREFIX = "pm clear ";
    public static final Set<String> DROPBOX_APP_CRASH_TAGS =
            Set.of(
                    "SYSTEM_TOMBSTONE",
                    "system_app_anr",
                    "system_app_native_crash",
                    "system_app_crash",
                    "data_app_anr",
                    "data_app_native_crash",
                    "data_app_crash");

    private static final String VIDEO_PATH_ON_DEVICE_TEMPLATE = "/sdcard/screenrecord_%s.mp4";
    private static final DateTimeFormatter DROPBOX_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss_SSS");
    // Pattern for finding a package name following one of the tags such as "Process:" or
    // "Package:".
    private static final Pattern DROPBOX_PACKAGE_NAME_PATTERN =
            Pattern.compile(
                    "\\b(Process|Cmdline|Package|Cmd line):("
                            + " *)([a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+)");

    @VisibleForTesting
    static final int WAIT_FOR_SCREEN_RECORDING_START_STOP_TIMEOUT_MILLIS = 10 * 1000;

    @VisibleForTesting static final int WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS = 500;

    private final ITestDevice mDevice;
    private final Sleeper mSleeper;
    private final Clock mClock;
    private final RunUtilProvider mRunUtilProvider;
    private final TempFileSupplier mTempFileSupplier;

    public static DeviceUtils getInstance(ITestDevice device) {
        return new DeviceUtils(
                device,
                duration -> {
                    Thread.sleep(duration);
                },
                () -> System.currentTimeMillis(),
                () -> RunUtil.getDefault(),
                () -> Files.createTempFile(TestUtils.class.getName(), ".tmp"));
    }

    @VisibleForTesting
    DeviceUtils(
            ITestDevice device,
            Sleeper sleeper,
            Clock clock,
            RunUtilProvider runUtilProvider,
            TempFileSupplier tempFileSupplier) {
        mDevice = device;
        mSleeper = sleeper;
        mClock = clock;
        mRunUtilProvider = runUtilProvider;
        mTempFileSupplier = tempFileSupplier;
    }

    /**
     * A runnable that throws DeviceNotAvailableException. Use this interface instead of Runnable so
     * that the DeviceNotAvailableException won't need to be handled inside the run() method.
     */
    public interface RunnableThrowingDeviceNotAvailable {
        void run() throws DeviceNotAvailableException;
    }

    /**
     * Grants additional permissions for installed an installed app
     *
     * <p>If the package is not installed or the command failed, there will be no error thrown
     * beyond debug logging.
     *
     * @param packageName the package name to grant permission.
     * @throws DeviceNotAvailableException
     */
    public void grantExternalStoragePermissions(String packageName)
            throws DeviceNotAvailableException {
        CommandResult cmdResult =
                mDevice.executeShellV2Command(
                        String.format("appops set %s MANAGE_EXTERNAL_STORAGE allow", packageName));
        if (cmdResult.getStatus() != CommandStatus.SUCCESS) {
            CLog.d(
                    "Granting MANAGE_EXTERNAL_STORAGE permissions for package %s was unsuccessful."
                            + " Reason: %s.",
                    packageName, cmdResult.toString());
        }
    }

    /**
     * Get the current device timestamp in milliseconds.
     *
     * @return The device time
     * @throws DeviceNotAvailableException When the device is not available.
     * @throws DeviceRuntimeException When the command to get device time failed or failed to parse
     *     the timestamp.
     */
    public DeviceTimestamp currentTimeMillis()
            throws DeviceNotAvailableException, DeviceRuntimeException {
        CommandResult result = mDevice.executeShellV2Command("echo ${EPOCHREALTIME:0:14}");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new DeviceRuntimeException(
                    "Failed to get device time: " + result,
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        try {
            return new DeviceTimestamp(Long.parseLong(result.getStdout().replace(".", "").trim()));
        } catch (NumberFormatException e) {
            CLog.e("Cannot parse device time string: " + result.getStdout());
            throw new DeviceRuntimeException(
                    "Cannot parse device time string: " + result.getStdout(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    /**
     * Get the device's build sdk level.
     *
     * @return The Sdk level, or -1 if failed to get it.
     * @throws DeviceNotAvailableException When the device is lost during test
     */
    public int getSdkLevel() throws DeviceNotAvailableException {
        CommandResult result = mDevice.executeShellV2Command("getprop ro.build.version.sdk");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            CLog.e(
                    "Failed to get device build sdk level: " + result,
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            return -1;
        }
        try {
            return Integer.parseInt(result.getStdout().trim());
        } catch (NumberFormatException e) {
            CLog.e("Cannot parse device build sdk level: " + result.getStdout());
            return -1;
        }
    }

    /**
     * Record the device screen while running a task.
     *
     * <p>This method will not throw exception when the screenrecord command failed unless the
     * device is unresponsive.
     *
     * @param action A runnable job that throws DeviceNotAvailableException.
     * @param handler A file handler that process the output screen record mp4 file located on the
     *     host.
     * @throws DeviceNotAvailableException When the device is unresponsive.
     */
    public void runWithScreenRecording(
            RunnableThrowingDeviceNotAvailable action, ScreenrecordFileHandler handler)
            throws DeviceNotAvailableException {
        String videoPath = String.format(VIDEO_PATH_ON_DEVICE_TEMPLATE, new Random().nextInt());
        mDevice.deleteFile(videoPath);

        // Start screen recording
        Process recordingProcess = null;
        DeviceTimestamp recordingDeviceStartTime = null;
        try {
            CLog.i("Starting screen recording at %s", videoPath);
            recordingProcess =
                    mRunUtilProvider
                            .get()
                            .runCmdInBackground(
                                    String.format(
                                                    "adb -s %s shell screenrecord%s %s",
                                                    mDevice.getSerialNumber(),
                                                    getSdkLevel() >= 34 ? " --time-limit 600" : "",
                                                    videoPath)
                                            .split("\\s+"));
        } catch (IOException ioException) {
            CLog.e("Exception is thrown when starting screen recording process: %s", ioException);
        }

        try {
            // Not exact video start time. The exact time can be found in the logcat but for
            // simplicity we use shell command to get the current time as the approximate of
            // the video start time.
            recordingDeviceStartTime = currentTimeMillis();
            long hostStartTime = mClock.currentTimeMillis();
            // Wait for the recording to start since it may take time for the device to start
            // recording
            while (recordingProcess != null) {
                CommandResult result = mDevice.executeShellV2Command("ls " + videoPath);
                if (result.getStatus() == CommandStatus.SUCCESS) {
                    break;
                }

                CLog.d(
                        "Screenrecord not started yet. Waiting %s milliseconds.",
                        WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS);

                try {
                    mSleeper.sleep(WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (mClock.currentTimeMillis() - hostStartTime
                        > WAIT_FOR_SCREEN_RECORDING_START_STOP_TIMEOUT_MILLIS) {
                    CLog.e(
                            "Screenrecord did not start within %s milliseconds.",
                            WAIT_FOR_SCREEN_RECORDING_START_STOP_TIMEOUT_MILLIS);
                    break;
                }
            }

            action.run();
        } finally {
            if (recordingProcess != null) {
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                WAIT_FOR_SCREEN_RECORDING_START_STOP_TIMEOUT_MILLIS,
                                "kill",
                                "-SIGINT",
                                Long.toString(recordingProcess.pid()));
                try {
                    recordingProcess.waitFor(
                            WAIT_FOR_SCREEN_RECORDING_START_STOP_TIMEOUT_MILLIS,
                            TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    recordingProcess.destroyForcibly();
                }
            }

            CommandResult sizeResult = mDevice.executeShellV2Command("ls -sh " + videoPath);
            if (sizeResult != null && sizeResult.getStatus() == CommandStatus.SUCCESS) {
                CLog.d(
                        "Completed screenrecord %s, video size: %s",
                        videoPath, sizeResult.getStdout());
            }
            CommandResult hashResult = mDevice.executeShellV2Command("md5sum " + videoPath);
            if (hashResult != null && hashResult.getStatus() == CommandStatus.SUCCESS) {
                CLog.d("Video file md5 sum: %s", hashResult.getStdout());
            }
            // Try to pull, handle, and delete the video file from the device anyway.
            handler.handleScreenRecordFile(mDevice.pullFile(videoPath), recordingDeviceStartTime);
            mDevice.deleteFile(videoPath);
        }
    }

    /** A file handler for screen record results. */
    public interface ScreenrecordFileHandler {
        /**
         * Handles the screen record mp4 file located on the host.
         *
         * @param screenRecord The mp4 file located on the host. If screen record failed then the
         *     input could be null.
         * @param recordingStartTime The device time when the screen record started..
         */
        void handleScreenRecordFile(File screenRecord, DeviceTimestamp recordingStartTime);
    }

    /**
     * Freeze the screen rotation to the default orientation.
     *
     * @return True if succeed; False otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean freezeRotation() throws DeviceNotAvailableException {
        CommandResult result =
                mDevice.executeShellV2Command(
                        "content insert --uri content://settings/system --bind"
                                + " name:s:accelerometer_rotation --bind value:i:0");
        if (result.getStatus() != CommandStatus.SUCCESS || result.getExitCode() != 0) {
            CLog.e("The command to disable auto screen rotation failed: %s", result);
            return false;
        }

        return true;
    }

    /**
     * Unfreeze the screen rotation to the default orientation.
     *
     * @return True if succeed; False otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean unfreezeRotation() throws DeviceNotAvailableException {
        CommandResult result =
                mDevice.executeShellV2Command(
                        "content insert --uri content://settings/system --bind"
                                + " name:s:accelerometer_rotation --bind value:i:1");
        if (result.getStatus() != CommandStatus.SUCCESS || result.getExitCode() != 0) {
            CLog.e("The command to enable auto screen rotation failed: %s", result);
            return false;
        }

        return true;
    }

    /**
     * Launches a package on the device.
     *
     * @param packageName The package name to launch.
     * @throws DeviceNotAvailableException When device was lost.
     * @throws DeviceUtilsException When failed to launch the package.
     */
    public void launchPackage(String packageName)
            throws DeviceUtilsException, DeviceNotAvailableException {
        CommandResult monkeyResult =
                mDevice.executeShellV2Command(
                        String.format(
                                "monkey -p %s -c android.intent.category.LAUNCHER 1", packageName));
        if (monkeyResult.getStatus() == CommandStatus.SUCCESS) {
            return;
        }
        CLog.w(
                "Continuing to attempt using am command to launch the package %s after the monkey"
                        + " command failed: %s",
                packageName, monkeyResult);

        CommandResult pmResult =
                mDevice.executeShellV2Command(String.format("pm dump %s", packageName));
        if (pmResult.getStatus() != CommandStatus.SUCCESS || pmResult.getExitCode() != 0) {
            if (isPackageInstalled(packageName)) {
                throw new DeviceUtilsException(
                        String.format(
                                "The command to dump package info for %s failed: %s",
                                packageName, pmResult));
            } else {
                throw new DeviceUtilsException(
                        String.format("Package %s is not installed on the device.", packageName));
            }
        }

        String activity = getLaunchActivity(pmResult.getStdout());

        CommandResult amResult =
                mDevice.executeShellV2Command(String.format("am start -n %s", activity));
        if (amResult.getStatus() != CommandStatus.SUCCESS
                || amResult.getExitCode() != 0
                || amResult.getStdout().contains("Error")) {
            throw new DeviceUtilsException(
                    String.format(
                            "The command to start the package %s with activity %s failed: %s",
                            packageName, activity, amResult));
        }
    }

    /**
     * Extracts the launch activity from a pm dump output.
     *
     * <p>This method parses the package manager dump, extracts the activities and filters them
     * based on the categories and actions defined in the Android framework. The activities are
     * sorted based on these attributes, and the first activity that is either the main action or a
     * launcher category is returned.
     *
     * @param pmDump the pm dump output to parse.
     * @return a activity that can be used to launch the package.
     * @throws DeviceUtilsException if the launch activity cannot be found in the
     *     dump. @VisibleForTesting
     */
    @VisibleForTesting
    String getLaunchActivity(String pmDump) throws DeviceUtilsException {
        class Activity {
            String mName;
            int mIndex;
            List<String> mActions = new ArrayList<>();
            List<String> mCategories = new ArrayList<>();
        }

        Pattern activityNamePattern =
                Pattern.compile(
                        "([a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"
                                + "\\/([a-zA-Z][a-zA-Z0-9_]*)*(\\.[a-zA-Z][a-zA-Z0-9_]*)+)");
        Pattern actionPattern =
                Pattern.compile(
                        "Action:([^a-zA-Z0-9_\\.]*)([a-zA-Z][a-zA-Z0-9_]*"
                                + "(\\.[a-zA-Z][a-zA-Z0-9_]*)+)");
        Pattern categoryPattern =
                Pattern.compile(
                        "Category:([^a-zA-Z0-9_\\.]*)([a-zA-Z][a-zA-Z0-9_]*"
                                + "(\\.[a-zA-Z][a-zA-Z0-9_]*)+)");

        Matcher activityNameMatcher = activityNamePattern.matcher(pmDump);

        List<Activity> activities = new ArrayList<>();
        while (activityNameMatcher.find()) {
            Activity activity = new Activity();
            activity.mName = activityNameMatcher.group(0);
            activity.mIndex = activityNameMatcher.start();
            activities.add(activity);
        }

        int endIdx = pmDump.length();
        ListIterator<Activity> iterator = activities.listIterator(activities.size());
        while (iterator.hasPrevious()) {
            Activity activity = iterator.previous();
            Matcher actionMatcher =
                    actionPattern.matcher(pmDump.substring(activity.mIndex, endIdx));
            while (actionMatcher.find()) {
                activity.mActions.add(actionMatcher.group(2));
            }
            Matcher categoryMatcher =
                    categoryPattern.matcher(pmDump.substring(activity.mIndex, endIdx));
            while (categoryMatcher.find()) {
                activity.mCategories.add(categoryMatcher.group(2));
            }
            endIdx = activity.mIndex;
        }

        String categoryDefault = "android.intent.category.DEFAULT";
        String categoryLauncher = "android.intent.category.LAUNCHER";
        String actionMain = "android.intent.action.MAIN";

        class AndroidActivityComparator implements Comparator<Activity> {
            @Override
            public int compare(Activity a1, Activity a2) {
                if (a1.mCategories.contains(categoryLauncher)
                        && !a2.mCategories.contains(categoryLauncher)) {
                    return -1;
                }
                if (!a1.mCategories.contains(categoryLauncher)
                        && a2.mCategories.contains(categoryLauncher)) {
                    return 1;
                }
                if (a1.mActions.contains(actionMain) && !a2.mActions.contains(actionMain)) {
                    return -1;
                }
                if (!a1.mActions.contains(actionMain) && a2.mActions.contains(actionMain)) {
                    return 1;
                }
                if (a1.mCategories.contains(categoryDefault)
                        && !a2.mCategories.contains(categoryDefault)) {
                    return -1;
                }
                if (!a1.mCategories.contains(categoryDefault)
                        && a2.mCategories.contains(categoryDefault)) {
                    return 1;
                }
                return Integer.compare(a2.mCategories.size(), a1.mCategories.size());
            }
        }

        Collections.sort(activities, new AndroidActivityComparator());
        List<Activity> filteredActivities =
                activities.stream()
                        .filter(
                                activity ->
                                        activity.mActions.contains(actionMain)
                                                || activity.mCategories.contains(categoryLauncher))
                        .collect(Collectors.toList());
        if (filteredActivities.isEmpty()) {
            throw new DeviceUtilsException(
                    String.format(
                            "Cannot find an activity to launch the package. Number of activities"
                                    + " parsed: %s",
                            activities.size()));
        }

        Activity res = filteredActivities.get(0);

        if (!res.mCategories.contains(categoryLauncher)) {
            CLog.d("Activity %s is not specified with a LAUNCHER category.", res.mName);
        }
        if (!res.mActions.contains(actionMain)) {
            CLog.d("Activity %s is not specified with a MAIN action.", res.mName);
        }

        return res.mName;
    }

    /**
     * Gets the version name of a package installed on the device.
     *
     * @param packageName The full package name to query
     * @return The package version name, or 'Unknown' if the package doesn't exist or the adb
     *     command failed.
     * @throws DeviceNotAvailableException
     */
    public String getPackageVersionName(String packageName) throws DeviceNotAvailableException {
        CommandResult cmdResult =
                mDevice.executeShellV2Command(
                        String.format("dumpsys package %s | grep versionName", packageName));

        if (cmdResult.getStatus() != CommandStatus.SUCCESS
                || !cmdResult.getStdout().trim().startsWith(VERSION_NAME_PREFIX)) {
            return UNKNOWN;
        }

        return cmdResult.getStdout().trim().substring(VERSION_NAME_PREFIX.length());
    }

    /**
     * Gets the version code of a package installed on the device.
     *
     * @param packageName The full package name to query
     * @return The package version code, or 'Unknown' if the package doesn't exist or the adb
     *     command failed.
     * @throws DeviceNotAvailableException
     */
    public String getPackageVersionCode(String packageName) throws DeviceNotAvailableException {
        CommandResult cmdResult =
                mDevice.executeShellV2Command(
                        String.format("dumpsys package %s | grep versionCode", packageName));

        if (cmdResult.getStatus() != CommandStatus.SUCCESS
                || !cmdResult.getStdout().trim().startsWith(VERSION_CODE_PREFIX)) {
            return UNKNOWN;
        }

        return cmdResult.getStdout().trim().split(" ")[0].substring(VERSION_CODE_PREFIX.length());
    }

    /**
     * Stops a running package on the device.
     *
     * @param packageName
     * @throws DeviceNotAvailableException
     */
    public void stopPackage(String packageName) throws DeviceNotAvailableException {
        mDevice.executeShellV2Command("am force-stop " + packageName);
    }

    /**
     * Resets a package's data storage on the device.
     *
     * @param packageName The package name of an app to reset.
     * @return True if the package exists and its data was reset; False otherwise.
     * @throws DeviceNotAvailableException If the device was lost.
     */
    public boolean resetPackage(String packageName) throws DeviceNotAvailableException {
        return mDevice.executeShellV2Command(RESET_PACKAGE_COMMAND_PREFIX + packageName).getStatus()
                == CommandStatus.SUCCESS;
    }

    /**
     * Checks whether a package is installed on the device.
     *
     * @param packageName The name of the package to check
     * @return True if the package is installed on the device; false otherwise.
     * @throws DeviceUtilsException If the adb shell command failed.
     * @throws DeviceNotAvailableException If the device was lost.
     */
    public boolean isPackageInstalled(String packageName)
            throws DeviceUtilsException, DeviceNotAvailableException {
        CommandResult commandResult =
                executeShellCommandOrThrow(
                        String.format("pm list packages %s", packageName),
                        "Failed to execute pm command");

        if (commandResult.getStdout() == null) {
            throw new DeviceUtilsException(
                    String.format(
                            "Failed to get pm command output: %s", commandResult.getStdout()));
        }

        return Arrays.asList(commandResult.getStdout().split("\\r?\\n"))
                .contains(String.format("package:%s", packageName));
    }

    private CommandResult executeShellCommandOrThrow(String command, String failureMessage)
            throws DeviceUtilsException, DeviceNotAvailableException {
        CommandResult commandResult = mDevice.executeShellV2Command(command);

        if (commandResult.getStatus() != CommandStatus.SUCCESS) {
            throw new DeviceUtilsException(
                    String.format("%s; Command result: %s", failureMessage, commandResult));
        }

        return commandResult;
    }

    /**
     * Gets dropbox entries from the device filtered by the provided tags.
     *
     * @param tags Dropbox tags to query.
     * @return A list of dropbox entries.
     * @throws IOException when failed to dump or read the dropbox protos.
     */
    public List<DropboxEntry> getDropboxEntries(Set<String> tags) throws IOException {
        List<DropboxEntry> entries = new ArrayList<>();

        for (String tag : tags) {
            Path dumpFile = mTempFileSupplier.get();

            CommandResult res =
                    mRunUtilProvider
                            .get()
                            .runTimedCmd(
                                    12L * 1000,
                                    "sh",
                                    "-c",
                                    String.format(
                                            "adb -s %s shell dumpsys dropbox --proto %s > %s",
                                            mDevice.getSerialNumber(), tag, dumpFile));

            if (res.getStatus() != CommandStatus.SUCCESS) {
                throw new IOException("Dropbox dump command failed: " + res);
            }

            DropBoxManagerServiceDumpProto proto;
            try {
                proto = DropBoxManagerServiceDumpProto.parseFrom(Files.readAllBytes(dumpFile));
            } catch (InvalidProtocolBufferException e) {
                // If dumping proto format is not supported such as in Android 10, the command will
                // still succeed with exit code 0 and output strings instead of protobuf bytes,
                // causing parse error. In this case we fallback to dumping dropbox --print option.
                return getDropboxEntriesFromStdout(tags);
            }
            Files.delete(dumpFile);

            for (Entry entry : proto.getEntriesList()) {
                entries.add(
                        new DropboxEntry(entry.getTimeMs(), tag, entry.getData().toStringUtf8()));
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(DropboxEntry::getTime))
                .collect(Collectors.toList());
    }

    /**
     * Gets dropbox entries from the device filtered by the provided tags.
     *
     * @param tags Dropbox tags to query.
     * @param packageName package name for filtering the entries. Can be null.
     * @param startTime entry start timestamp to filter the results. Can be null.
     * @param endTime entry end timestamp to filter the results. Can be null.
     * @return A list of dropbox entries.
     * @throws IOException when failed to dump or read the dropbox protos.
     */
    public List<DropboxEntry> getDropboxEntries(
            Set<String> tags,
            String packageName,
            DeviceTimestamp startTime,
            DeviceTimestamp endTime)
            throws IOException {
        return getDropboxEntries(tags).stream()
                .filter(
                        entry ->
                                ((startTime == null || entry.getTime() >= startTime.get())
                                        && (endTime == null || entry.getTime() < endTime.get())))
                .filter(
                        entry ->
                                packageName == null
                                        || isDropboxEntryFromPackageProcess(
                                                entry.getData(), packageName))
                .collect(Collectors.toList());
    }

    /* Checks whether a dropbox entry is logged from the given package name. */
    @VisibleForTesting
    boolean isDropboxEntryFromPackageProcess(String entryData, String packageName) {
        Matcher m = DROPBOX_PACKAGE_NAME_PATTERN.matcher(entryData);

        boolean matched = false;
        while (m.find()) {
            matched = true;
            if (m.group(3).equals(packageName)) {
                return true;
            }
        }

        // Package/process name is identified but not equal to the packageName provided
        if (matched) {
            return false;
        }

        // If the process name is not identified, fall back to checking if the package name is
        // present in the entry. This is because the process name detection logic above does not
        // guarantee to identify the process name.
        return Pattern.compile(
                        String.format(
                                // Pattern for checking whether a given package name exists.
                                "(.*(?:[^a-zA-Z0-9_\\.]+)|^)%s((?:[^a-zA-Z0-9_\\.]+).*|$)",
                                packageName.replaceAll("\\.", "\\\\.")))
                .matcher(entryData)
                .find();
    }

    @VisibleForTesting
    List<DropboxEntry> getDropboxEntriesFromStdout(Set<String> tags) throws IOException {
        HashMap<String, DropboxEntry> entries = new HashMap<>();

        // The first step is to read the entry names and timestamps from the --file dump option
        // output because the --print dump option does not contain timestamps.
        CommandResult res;
        Path fileDumpFile = mTempFileSupplier.get();
        res =
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                6000,
                                "sh",
                                "-c",
                                String.format(
                                        "adb -s %s shell dumpsys dropbox --file  > %s",
                                        mDevice.getSerialNumber(), fileDumpFile));
        if (res.getStatus() != CommandStatus.SUCCESS) {
            throw new IOException("Dropbox dump command failed: " + res);
        }

        String lastEntryName = null;
        for (String line : Files.readAllLines(fileDumpFile)) {
            if (DropboxEntry.isDropboxEntryName(line)) {
                lastEntryName = line.trim();
                entries.put(lastEntryName, DropboxEntry.fromEntryName(line));
            } else if (DropboxEntry.isDropboxFilePath(line) && lastEntryName != null) {
                entries.get(lastEntryName).parseTimeFromFilePath(line);
            }
        }
        Files.delete(fileDumpFile);

        // Then we get the entry data from the --print dump output. Entry names parsed from the
        // --print dump output are verified against the entry names from the --file dump output to
        // ensure correctness.
        Path printDumpFile = mTempFileSupplier.get();
        res =
                mRunUtilProvider
                        .get()
                        .runTimedCmd(
                                6000,
                                "sh",
                                "-c",
                                String.format(
                                        "adb -s %s shell dumpsys dropbox --print > %s",
                                        mDevice.getSerialNumber(), printDumpFile));
        if (res.getStatus() != CommandStatus.SUCCESS) {
            throw new IOException("Dropbox dump command failed: " + res);
        }

        lastEntryName = null;
        for (String line : Files.readAllLines(printDumpFile)) {
            if (DropboxEntry.isDropboxEntryName(line)) {
                lastEntryName = line.trim();
            }

            if (lastEntryName != null && entries.containsKey(lastEntryName)) {
                entries.get(lastEntryName).addData(line);
                entries.get(lastEntryName).addData("\n");
            }
        }
        Files.delete(printDumpFile);

        return entries.values().stream()
                .filter(entry -> tags.contains(entry.getTag()))
                .collect(Collectors.toList());
    }

    /** A class that stores the information of a dropbox entry. */
    public static final class DropboxEntry {
        private long mTime;
        private String mTag;
        private final StringBuilder mData = new StringBuilder();
        private static final Pattern ENTRY_NAME_PATTERN =
                Pattern.compile(
                        "\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2} .+ \\(.+, [0-9]+ .+\\)");
        private static final Pattern DATE_PATTERN =
                Pattern.compile("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        private static final Pattern FILE_NAME_PATTERN = Pattern.compile(" +/.+@[0-9]+\\..+");

        /** Returns the entrt's time stamp on device. */
        public long getTime() {
            return mTime;
        }

        private void addData(String data) {
            mData.append(data);
        }

        private void parseTimeFromFilePath(String input) {
            mTime = Long.parseLong(input.substring(input.indexOf('@') + 1, input.indexOf('.')));
        }

        /** Returns the entrt's tag. */
        public String getTag() {
            return mTag;
        }

        /** Returns the entrt's data. */
        public String getData() {
            return mData.toString();
        }

        @Override
        public String toString() {
            long time = getTime();
            String formattedTime =
                    DROPBOX_TIME_FORMATTER.format(
                            Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
            return String.format(
                    "Dropbox entry tag: %s\n"
                            + "Dropbox entry timestamp: %s\n"
                            + "Dropbox entry time: %s\n%s",
                    getTag(), time, formattedTime, getData());
        }

        @VisibleForTesting
        DropboxEntry(long time, String tag, String data) {
            mTime = time;
            mTag = tag;
            addData(data);
        }

        private DropboxEntry() {
            // Intentionally left blank;
        }

        private static DropboxEntry fromEntryName(String name) {
            DropboxEntry entry = new DropboxEntry();
            Matcher matcher = DATE_PATTERN.matcher(name);
            if (!matcher.find()) {
                throw new RuntimeException("Unexpected entry name: " + name);
            }
            entry.mTag = name.trim().substring(matcher.group().length()).trim().split(" ")[0];
            return entry;
        }

        private static boolean isDropboxEntryName(String input) {
            return ENTRY_NAME_PATTERN.matcher(input).find();
        }

        private static boolean isDropboxFilePath(String input) {
            return FILE_NAME_PATTERN.matcher(input).find();
        }
    }

    /** A general exception class representing failed device utility operations. */
    public static final class DeviceUtilsException extends Exception {
        /**
         * Constructs a new {@link DeviceUtilsException} with a meaningful error message.
         *
         * @param message A error message describing the cause of the error.
         */
        private DeviceUtilsException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@link DeviceUtilsException} with a meaningful error message, and a
         * cause.
         *
         * @param message A detailed error message.
         * @param cause A {@link Throwable} capturing the original cause of the {@link
         *     DeviceUtilsException}.
         */
        private DeviceUtilsException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new {@link DeviceUtilsException} with a cause.
         *
         * @param cause A {@link Throwable} capturing the original cause of the {@link
         *     DeviceUtilsException}.
         */
        private DeviceUtilsException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * A class to contain a device timestamp.
     *
     * <p>Use this class instead of long to pass device timestamps so that they are less likely to
     * be confused with host timestamps.
     */
    public static class DeviceTimestamp {
        private final long mTimestamp;

        public DeviceTimestamp(long timestamp) {
            mTimestamp = timestamp;
        }

        /** Gets the time stamp on a device. */
        public long get() {
            return mTimestamp;
        }

        /**
         * Gets the time stamp in formatted string
         *
         * @param format date format
         * @return A formatted string representing the device time stamp
         */
        public String getFormatted(String format) {
            return DateTimeFormatter.ofPattern(format)
                    .format(Instant.ofEpochMilli(get()).atZone(ZoneId.systemDefault()));
        }
    }

    @VisibleForTesting
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    @VisibleForTesting
    interface Clock {
        long currentTimeMillis();
    }

    @VisibleForTesting
    interface RunUtilProvider {
        IRunUtil get();
    }

    @VisibleForTesting
    interface TempFileSupplier {
        Path get() throws IOException;
    }
}
