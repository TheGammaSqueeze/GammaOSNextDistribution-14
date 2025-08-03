/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.InstallReceiver;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.IDeviceSelection.BaseDeviceType;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.Bugreport;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.KeyguardControllerState;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/**
 * Implementation of a {@link ITestDevice} for a full stack android device
 */
public class TestDevice extends NativeDevice {

    /** number of attempts made to clear dialogs */
    private static final int NUM_CLEAR_ATTEMPTS = 5;
    /** the command used to dismiss a error dialog. Currently sends a DPAD_CENTER key event */
    static final String DISMISS_DIALOG_CMD = "input keyevent 23";

    static final String DISMISS_DIALOG_BROADCAST =
            "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOG";
    // Collapse notifications
    private static final String COLLAPSE_STATUS_BAR = "cmd statusbar collapse";

    /** Commands that can be used to dismiss the keyguard. */
    public static final String DISMISS_KEYGUARD_CMD = "input keyevent 82";

    /**
     * Alternative command to dismiss the keyguard by requesting the Window Manager service to do
     * it. Api 23 and after.
     */
    static final String DISMISS_KEYGUARD_WM_CMD = "wm dismiss-keyguard";

    /** Maximum time to wait for keyguard to be dismissed. */
    private static final long DISMISS_KEYGUARD_TIMEOUT = 3 * 1000;

    /** Command to construct KeyguardControllerState. */
    static final String KEYGUARD_CONTROLLER_CMD =
            "dumpsys activity activities | grep -A3 KeyguardController:";

    /** Timeout to wait for input dispatch to become ready **/
    private static final long INPUT_DISPATCH_READY_TIMEOUT = 5 * 1000;
    /** command to test input dispatch readiness **/
    private static final String TEST_INPUT_CMD = "dumpsys input";

    private static final long AM_COMMAND_TIMEOUT = 10 * 1000;
    private static final long CHECK_NEW_USER = 1000;

    static final String LIST_PACKAGES_CMD = "pm list packages -f";
    private static final Pattern PACKAGE_REGEX = Pattern.compile("package:(.*)=(.*)");

    static final String LIST_APEXES_CMD = "pm list packages --apex-only --show-versioncode -f";
    private static final Pattern APEXES_WITH_PATH_REGEX =
            Pattern.compile("package:(.*)=(.*) versionCode:(.*)");

    static final String GET_MODULEINFOS_CMD = "pm get-moduleinfo --all";
    private static final Pattern MODULEINFO_REGEX =
            Pattern.compile("ModuleInfo\\{(.*)\\} packageName: (.*)");

    /**
     * Regexp to match on old versions of platform (before R), where {@code -f} flag for the {@code
     * pm list packages apex-only} command wasn't supported.
     */
    private static final Pattern APEXES_WITHOUT_PATH_REGEX =
            Pattern.compile("package:(.*) versionCode:(.*)");

    private static final int FLAG_PRIMARY = 1; // From the UserInfo class

    private static final int FLAG_MAIN = 0x00004000; // From the UserInfo class

    private static final String[] SETTINGS_NAMESPACE = {"system", "secure", "global"};

    /** user pattern in the output of "pm list users" = TEXT{<id>:<name>:<flags>} TEXT * */
    private static final String USER_PATTERN = "(.*?\\{)(\\d+)(:)(.*)(:)(\\w+)(\\}.*)";
    /** Pattern to find the display ids of "dumpsys SurfaceFlinger" */
    private static final String DISPLAY_ID_PATTERN = "(Display )(?<id>\\d+)( color modes:)";

    private static final int API_LEVEL_GET_CURRENT_USER = 24;
    /** Timeout to wait for a screenshot before giving up to avoid hanging forever */
    private static final long MAX_SCREENSHOT_TIMEOUT = 5 * 60 * 1000; // 5 min

    /** adb shell am dumpheap <service pid> <dump file path> */
    private static final String DUMPHEAP_CMD = "am dumpheap %s %s";
    /** Time given to a file to be dumped on device side */
    private static final long DUMPHEAP_TIME = 5000L;

    /** Timeout in minutes for the package installation */
    static final long INSTALL_TIMEOUT_MINUTES = 4;
    /** Max timeout to output for package installation */
    static final long INSTALL_TIMEOUT_TO_OUTPUT_MINUTES = 3;

    private boolean mWasWifiHelperInstalled = false;

    private static final String APEX_SUFFIX = ".apex";
    private static final String APEX_ARG = "--apex";

    /** Contains a set of Microdroid instances running in this TestDevice, and their resources. */
    private Map<Process, MicrodroidTracker> mStartedMicrodroids = new HashMap<>();

    private static final String TEST_ROOT = "/data/local/tmp/virt/";
    private static final String VIRT_APEX = "/apex/com.android.virt/";
    private static final String INSTANCE_ID_FILE = "instance_id";
    private static final String INSTANCE_IMG = "instance.img";

    // This is really slow on GCE (2m 40s) but fast on localhost or actual Android phones (< 10s).
    // Then there is time to run the actual task. Set the maximum timeout value big enough.
    private static final long MICRODROID_MAX_LIFETIME_MINUTES = 20;

    private static final long MICRODROID_DEFAULT_ADB_CONNECT_TIMEOUT_MINUTES = 5;

    private static final String EARLY_REBOOT = "Too early to call shutdown() or reboot()";

    /**
     * Allow pauses of up to 2 minutes while receiving bugreport.
     *
     * <p>Note that dumpsys may pause up to a minute while waiting for unresponsive components. It
     * still should bail after that minute, if it will ever terminate on its own.
     */
    private static final int BUGREPORT_TIMEOUT = 2 * 60 * 1000;

    private static final String BUGREPORT_CMD = "bugreport";
    private static final String BUGREPORTZ_CMD = "bugreportz";
    private static final Pattern BUGREPORTZ_RESPONSE_PATTERN = Pattern.compile("(OK:)(.*)");

    /** Number of attempts made to get user info. */
    private static final int NUM_USER_INFO_ATTEMPTS = 3;

    /** Track microdroid and its resources */
    private class MicrodroidTracker {
        ExecutorService executor;
        String cid;
    }

    /**
     * @param device
     * @param stateMonitor
     * @param allocationMonitor
     */
    public TestDevice(IDevice device, IDeviceStateMonitor stateMonitor,
            IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
    }

    @Override
    public boolean isAppEnumerationSupported() throws DeviceNotAvailableException {
        if (!checkApiLevelAgainstNextRelease(30)) {
            return false;
        }
        return hasFeature("android.software.app_enumeration");
    }

    /**
     * Core implementation of package installation, with retries around
     * {@link IDevice#installPackage(String, boolean, String...)}
     * @param packageFile
     * @param reinstall
     * @param extraArgs
     * @return the response from the installation
     * @throws DeviceNotAvailableException
     */
    private String internalInstallPackage(
            final File packageFile, final boolean reinstall, final List<String> extraArgs)
                    throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        try {
            List<String> args = new ArrayList<>(extraArgs);
            if (packageFile.getName().endsWith(APEX_SUFFIX)) {
                args.add(APEX_ARG);
            }
            // use array to store response, so it can be returned to caller
            final String[] response = new String[1];
            DeviceAction installAction =
                    new DeviceAction() {
                        @Override
                        public boolean run() throws InstallException {
                            try {
                                InstallReceiver receiver = createInstallReceiver();
                                getIDevice()
                                        .installPackage(
                                                packageFile.getAbsolutePath(),
                                                reinstall,
                                                receiver,
                                                INSTALL_TIMEOUT_MINUTES,
                                                INSTALL_TIMEOUT_TO_OUTPUT_MINUTES,
                                                TimeUnit.MINUTES,
                                                args.toArray(new String[] {}));
                                response[0] = handleInstallReceiver(receiver, packageFile);
                            } catch (InstallException e) {
                                response[0] = handleInstallationError(e);
                            }
                            return response[0] == null;
                        }
                    };
            CLog.v(
                    "Installing package file %s with args %s on %s",
                    packageFile.getAbsolutePath(), extraArgs.toString(), getSerialNumber());
            performDeviceAction(
                    String.format("install %s", packageFile.getAbsolutePath()),
                    installAction,
                    MAX_RETRY_ATTEMPTS);
            List<File> packageFiles = new ArrayList<>();
            packageFiles.add(packageFile);
            allowLegacyStorageForApps(packageFiles);
            return response[0];
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PACKAGE_INSTALL_COUNT, 1);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PACKAGE_INSTALL_TIME,
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Creates and return an {@link InstallReceiver} for {@link #internalInstallPackage(File,
     * boolean, List)} and {@link #installPackage(File, File, boolean, String...)} testing.
     */
    @VisibleForTesting
    InstallReceiver createInstallReceiver() {
        return new InstallReceiver();
    }

    /** {@inheritDoc} */
    @Override
    public InputStreamSource getBugreport() {
        if (getApiLevelSafe() < 24) {
            InputStreamSource bugreport = getBugreportInternal();
            if (bugreport == null) {
                // Safe call so we don't return null but an empty resource.
                return new ByteArrayInputStreamSource("".getBytes());
            }
            return bugreport;
        }
        CLog.d("Api level above 24, using bugreportz instead.");
        File mainEntry = null;
        File bugreportzFile = null;
        long startTime = System.currentTimeMillis();
        try {
            bugreportzFile = getBugreportzInternal();
            if (bugreportzFile == null) {
                // return empty buffer
                return new ByteArrayInputStreamSource("".getBytes());
            }
            try (ZipFile zip = new ZipFile(bugreportzFile)) {
                // We get the main_entry.txt that contains the bugreport name.
                mainEntry = ZipUtil2.extractFileFromZip(zip, "main_entry.txt");
                String bugreportName = FileUtil.readStringFromFile(mainEntry).trim();
                CLog.d("bugreport name: '%s'", bugreportName);
                File bugreport = ZipUtil2.extractFileFromZip(zip, bugreportName);
                return new FileInputStreamSource(bugreport, true);
            }
        } catch (IOException e) {
            CLog.e("Error while unzipping bugreportz");
            CLog.e(e);
            return new ByteArrayInputStreamSource("corrupted bugreport.".getBytes());
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.BUGREPORT_TIME, System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.BUGREPORT_COUNT, 1);
            FileUtil.deleteFile(bugreportzFile);
            FileUtil.deleteFile(mainEntry);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean logBugreport(String dataName, ITestLogger listener) {
        InputStreamSource bugreport = null;
        LogDataType type = null;
        try {
            bugreport = getBugreportz();
            type = LogDataType.BUGREPORTZ;
            // log what we managed to capture.
            if (bugreport != null && bugreport.size() > 0L) {
                listener.testLog(dataName, type, bugreport);
                return true;
            }
        } finally {
            StreamUtil.cancel(bugreport);
        }
        CLog.d(
                "logBugreport() was not successful in collecting and logging the bugreport "
                        + "for device %s",
                getSerialNumber());
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public Bugreport takeBugreport() {
        File bugreportFile = null;
        int apiLevel = getApiLevelSafe();
        if (apiLevel == UNKNOWN_API_LEVEL) {
            return null;
        }
        long startTime = System.currentTimeMillis();
        try {
            if (apiLevel >= 24) {
                CLog.d("Api level above 24, using bugreportz.");
                bugreportFile = getBugreportzInternal();
                if (bugreportFile != null) {
                    return new Bugreport(bugreportFile, true);
                }
                return null;
            }
            // fall back to regular bugreport
            InputStreamSource bugreport = getBugreportInternal();
            if (bugreport == null) {
                CLog.e("Error when collecting the bugreport.");
                return null;
            }
            try {
                bugreportFile = FileUtil.createTempFile("bugreport", ".txt");
                FileUtil.writeToFile(bugreport.createInputStream(), bugreportFile);
                return new Bugreport(bugreportFile, false);
            } catch (IOException e) {
                CLog.e("Error when writing the bugreport file");
                CLog.e(e);
            }
            return null;
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.BUGREPORT_TIME, System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.BUGREPORT_COUNT, 1);
        }
    }

    /** {@inheritDoc} */
    @Override
    public InputStreamSource getBugreportz() {
        if (getApiLevelSafe() < 24) {
            return null;
        }
        CLog.d("Start getBugreportz()");
        long startTime = System.currentTimeMillis();
        try {
            File bugreportZip = getBugreportzInternal();
            if (bugreportZip != null) {
                return new FileInputStreamSource(bugreportZip, true);
            }
            return null;
        } finally {
            CLog.d("Done with getBugreportz()");
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.BUGREPORT_TIME, System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.BUGREPORT_COUNT, 1);
        }
    }

    /** Internal Helper method to get the bugreportz zip file as a {@link File}. */
    @VisibleForTesting
    protected File getBugreportzInternal() {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        // Does not rely on {@link ITestDevice#executeAdbCommand(String...)} because it does not
        // provide a timeout.
        try {
            executeShellCommand(
                    BUGREPORTZ_CMD,
                    receiver,
                    getOptions().getBugreportzTimeout(),
                    TimeUnit.MILLISECONDS,
                    0 /* don't retry */);
            String output = receiver.getOutput().trim();
            Matcher match = BUGREPORTZ_RESPONSE_PATTERN.matcher(output);
            if (!match.find()) {
                CLog.e("Something went went wrong during bugreportz collection: '%s'", output);
                return null;
            } else {
                String remoteFilePath = match.group(2);
                if (Strings.isNullOrEmpty(remoteFilePath)) {
                    CLog.e("Invalid bugreportz path found from output: %s", output);
                    return null;
                }
                File zipFile = null;
                try {
                    if (!doesFileExist(remoteFilePath)) {
                        CLog.e("Did not find bugreportz at: '%s'", remoteFilePath);
                        return null;
                    }
                    // Create a placeholder to replace the file
                    zipFile = FileUtil.createTempFile("bugreportz", ".zip");
                    // pull
                    pullFile(remoteFilePath, zipFile);
                    String bugreportDir =
                            remoteFilePath.substring(0, remoteFilePath.lastIndexOf('/'));
                    if (!bugreportDir.isEmpty()) {
                        // clean bugreport files directory on device
                        deleteFile(String.format("%s/*", bugreportDir));
                    }

                    return zipFile;
                } catch (IOException e) {
                    CLog.e("Failed to create the temporary file.");
                    return null;
                }
            }
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device %s became unresponsive while retrieving bugreportz", getSerialNumber());
            CLog.e(e);
        }
        return null;
    }

    protected InputStreamSource getBugreportInternal() {
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        try {
            executeShellCommand(
                    BUGREPORT_CMD,
                    receiver,
                    BUGREPORT_TIMEOUT,
                    TimeUnit.MILLISECONDS,
                    0 /* don't retry */);
        } catch (DeviceNotAvailableException e) {
            // Log, but don't throw, so the caller can get the bugreport contents even
            // if the device goes away
            CLog.e("Device %s became unresponsive while retrieving bugreport", getSerialNumber());
            return null;
        }
        return new ByteArrayInputStreamSource(receiver.getOutput());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(final File packageFile, final boolean reinstall,
            final String... extraArgs) throws DeviceNotAvailableException {
        boolean runtimePermissionSupported = isRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        // grant all permissions by default if feature is supported
        if (runtimePermissionSupported) {
            args.add("-g");
        }
        return internalInstallPackage(packageFile, reinstall, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(File packageFile, boolean reinstall, boolean grantPermissions,
            String... extraArgs) throws DeviceNotAvailableException {
        ensureRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        if (grantPermissions) {
            args.add("-g");
        }
        return internalInstallPackage(packageFile, reinstall, args);
    }

    public String installPackage(final File packageFile, final File certFile,
            final boolean reinstall, final String... extraArgs) throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        try {
            // use array to store response, so it can be returned to caller
            final String[] response = new String[1];
            DeviceAction installAction =
                    new DeviceAction() {
                        @Override
                        public boolean run()
                                throws InstallException, SyncException, IOException,
                                        TimeoutException, AdbCommandRejectedException {
                            // TODO: create a getIDevice().installPackage(File, File...) method when
                            // the
                            // dist cert functionality is ready to be open sourced
                            String remotePackagePath =
                                    getIDevice().syncPackageToDevice(packageFile.getAbsolutePath());
                            String remoteCertPath =
                                    getIDevice().syncPackageToDevice(certFile.getAbsolutePath());
                            // trick installRemotePackage into issuing a 'pm install <apk> <cert>'
                            // command, by adding apk path to extraArgs, and using cert as the
                            // 'apk file'.
                            String[] newExtraArgs = new String[extraArgs.length + 1];
                            System.arraycopy(extraArgs, 0, newExtraArgs, 0, extraArgs.length);
                            newExtraArgs[newExtraArgs.length - 1] =
                                    String.format("\"%s\"", remotePackagePath);
                            try {
                                InstallReceiver receiver = createInstallReceiver();
                                getIDevice()
                                        .installRemotePackage(
                                                remoteCertPath,
                                                reinstall,
                                                receiver,
                                                INSTALL_TIMEOUT_MINUTES,
                                                INSTALL_TIMEOUT_TO_OUTPUT_MINUTES,
                                                TimeUnit.MINUTES,
                                                newExtraArgs);
                                response[0] = handleInstallReceiver(receiver, packageFile);
                            } catch (InstallException e) {
                                response[0] = handleInstallationError(e);
                            } finally {
                                getIDevice().removeRemotePackage(remotePackagePath);
                                getIDevice().removeRemotePackage(remoteCertPath);
                            }
                            return true;
                        }
                    };
            performDeviceAction(
                    String.format("install %s", packageFile.getAbsolutePath()),
                    installAction,
                    MAX_RETRY_ATTEMPTS);
            List<File> packageFiles = new ArrayList<>();
            packageFiles.add(packageFile);
            allowLegacyStorageForApps(packageFiles);
            return response[0];
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PACKAGE_INSTALL_COUNT, 1);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PACKAGE_INSTALL_TIME,
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall, int userId,
            String... extraArgs) throws DeviceNotAvailableException {
        boolean runtimePermissionSupported = isRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        // grant all permissions by default if feature is supported
        if (runtimePermissionSupported) {
            args.add("-g");
        }
        args.add("--user");
        args.add(Integer.toString(userId));
        return internalInstallPackage(packageFile, reinstall, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall,
            boolean grantPermissions, int userId, String... extraArgs)
                    throws DeviceNotAvailableException {
        ensureRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        if (grantPermissions) {
            args.add("-g");
        }
        args.add("--user");
        args.add(Integer.toString(userId));
        return internalInstallPackage(packageFile, reinstall, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uninstallPackage(final String packageName) throws DeviceNotAvailableException {
        // use array to store response, so it can be returned to caller
        return uninstallPackage(packageName, /* extraArgs= */ null);
    }

    private String uninstallPackage(String packageName, @Nullable String extraArgs)
            throws DeviceNotAvailableException {
        final String finalExtraArgs = (extraArgs == null) ? "" : extraArgs;

        // use array to store response, so it can be returned to caller
        final String[] response = new String[1];
        DeviceAction uninstallAction =
                () -> {
                    CLog.d("Uninstalling %s with extra args %s", packageName, finalExtraArgs);

                    String result = getIDevice().uninstallApp(packageName, finalExtraArgs);
                    response[0] = result;
                    return result == null;
                };

        performDeviceAction(
                String.format("uninstall %s with extra args %s", packageName, finalExtraArgs),
                uninstallAction,
                MAX_RETRY_ATTEMPTS);
        return response[0];
    }

    /** {@inheritDoc} */
    @Override
    public String uninstallPackageForUser(final String packageName, int userId)
            throws DeviceNotAvailableException {
        return uninstallPackage(packageName, "--user " + userId);
    }

    /**
     * Core implementation for installing application with split apk files {@link
     * IDevice#installPackages(List, boolean, List)} See
     * "https://developer.android.com/studio/build/configure-apk-splits" on how to split apk to
     * several files.
     *
     * @param packageFiles the local apk files
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm -h' for available
     *     options.
     * @return the response from the installation <code>null</code> if installation succeeds.
     * @throws DeviceNotAvailableException
     */
    private String internalInstallPackages(
            final List<File> packageFiles, final boolean reinstall, final List<String> extraArgs)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        try {
            // use array to store response, so it can be returned to caller
            final String[] response = new String[1];
            DeviceAction installAction =
                    new DeviceAction() {
                        @Override
                        public boolean run() throws InstallException {
                            try {
                                getIDevice()
                                        .installPackages(
                                                packageFiles,
                                                reinstall,
                                                extraArgs,
                                                INSTALL_TIMEOUT_MINUTES,
                                                TimeUnit.MINUTES);
                                response[0] = null;
                                return true;
                            } catch (InstallException e) {
                                response[0] = handleInstallationError(e);
                                return false;
                            }
                        }
                    };
            performDeviceAction(
                    String.format("install %s", packageFiles.toString()),
                    installAction,
                    MAX_RETRY_ATTEMPTS);
            allowLegacyStorageForApps(packageFiles);
            return response[0];
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PACKAGE_INSTALL_COUNT, 1);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PACKAGE_INSTALL_TIME,
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Allows Legacy External Storage access for apps that request for it.
     *
     * <p>Apps that request for legacy external storage access are granted the access by setting
     * MANAGE_EXTERNAL_STORAGE App Op. This gives the app File manager privileges, File managers
     * have legacy external storage access.
     *
     * @param appFiles List of Files. Apk Files of the apps that are installed.
     */
    private void allowLegacyStorageForApps(List<File> appFiles) throws DeviceNotAvailableException {
        for (File appFile : appFiles) {
            AaptParser aaptParser = createParser(appFile);
            if (aaptParser != null
                    && aaptParser.getTargetSdkVersion() > 29
                    && aaptParser.isRequestingLegacyStorage()) {
                if (!aaptParser.isUsingPermissionManageExternalStorage()) {
                    CLog.w(
                            "App is requesting legacy storage and targets R or above, but didn't"
                                    + " request the MANAGE_EXTERNAL_STORAGE permission so the"
                                    + " associated app op cannot be automatically granted and the"
                                    + " app won't have legacy external storage access: "
                                    + aaptParser.getPackageName());
                    continue;
                }
                // Set the MANAGE_EXTERNAL_STORAGE App Op to MODE_ALLOWED (Code = 0)
                // for all users.
                ArrayList<Integer> userIds = listUsers();
                for (int userId : userIds) {
                    CommandResult setFileManagerAppOpResult =
                            executeShellV2Command(
                                    "appops set --user "
                                            + userId
                                            + " --uid "
                                            + aaptParser.getPackageName()
                                            + " MANAGE_EXTERNAL_STORAGE 0");
                    if (!CommandStatus.SUCCESS.equals(setFileManagerAppOpResult.getStatus())) {
                        CLog.e(
                                "Failed to set MANAGE_EXTERNAL_STORAGE App Op to"
                                        + " allow legacy external storage for: %s ; stderr: %s",
                                aaptParser.getPackageName(), setFileManagerAppOpResult.getStderr());
                    }
                }
            }
        }
        CommandResult persistFileManagerAppOpResult =
                executeShellV2Command("appops write-settings");
        if (!CommandStatus.SUCCESS.equals(persistFileManagerAppOpResult.getStatus())) {
            CLog.e(
                    "Failed to persist MANAGE_EXTERNAL_STORAGE App Op over `adb reboot`: %s",
                    persistFileManagerAppOpResult.getStderr());
        }
    }

    @VisibleForTesting
    protected AaptParser createParser(File appFile) {
        return AaptParser.parse(appFile);
    }

    /** {@inheritDoc} */
    @Override
    public String installPackages(
            final List<File> packageFiles, final boolean reinstall, final String... extraArgs)
            throws DeviceNotAvailableException {
        // Grant all permissions by default if feature is supported
        return installPackages(packageFiles, reinstall, isRuntimePermissionSupported(), extraArgs);
    }

    /** {@inheritDoc} */
    @Override
    public String installPackages(
            List<File> packageFiles,
            boolean reinstall,
            boolean grantPermissions,
            String... extraArgs)
            throws DeviceNotAvailableException {
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        if (grantPermissions) {
            ensureRuntimePermissionSupported();
            args.add("-g");
        }
        return internalInstallPackages(packageFiles, reinstall, args);
    }

    /** {@inheritDoc} */
    @Override
    public String installPackagesForUser(
            List<File> packageFiles, boolean reinstall, int userId, String... extraArgs)
            throws DeviceNotAvailableException {
        // Grant all permissions by default if feature is supported
        return installPackagesForUser(
                packageFiles, reinstall, isRuntimePermissionSupported(), userId, extraArgs);
    }

    /** {@inheritDoc} */
    @Override
    public String installPackagesForUser(
            List<File> packageFiles,
            boolean reinstall,
            boolean grantPermissions,
            int userId,
            String... extraArgs)
            throws DeviceNotAvailableException {
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        if (grantPermissions) {
            ensureRuntimePermissionSupported();
            args.add("-g");
        }
        args.add("--user");
        args.add(Integer.toString(userId));
        return internalInstallPackages(packageFiles, reinstall, args);
    }

    /**
     * Core implementation for split apk remote installation {@link IDevice#installPackage(String,
     * boolean, String...)} See "https://developer.android.com/studio/build/configure-apk-splits" on
     * how to split apk to several files.
     *
     * @param remoteApkPaths the remote apk file paths
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm -h' for available
     *     options.
     * @return the response from the installation <code>null</code> if installation succeeds.
     * @throws DeviceNotAvailableException
     */
    private String internalInstallRemotePackages(
            final List<String> remoteApkPaths,
            final boolean reinstall,
            final List<String> extraArgs)
            throws DeviceNotAvailableException {
        // use array to store response, so it can be returned to caller
        final String[] response = new String[1];
        DeviceAction installAction =
                new DeviceAction() {
                    @Override
                    public boolean run() throws InstallException {
                        try {
                            getIDevice()
                                    .installRemotePackages(
                                            remoteApkPaths,
                                            reinstall,
                                            extraArgs,
                                            INSTALL_TIMEOUT_MINUTES,
                                            TimeUnit.MINUTES);
                            response[0] = null;
                            return true;
                        } catch (InstallException e) {
                            response[0] = handleInstallationError(e);
                            return false;
                        }
                    }
                };
        performDeviceAction(
                String.format("install %s", remoteApkPaths.toString()),
                installAction,
                MAX_RETRY_ATTEMPTS);
        return response[0];
    }

    /** {@inheritDoc} */
    @Override
    public String installRemotePackages(
            final List<String> remoteApkPaths, final boolean reinstall, final String... extraArgs)
            throws DeviceNotAvailableException {
        // Grant all permissions by default if feature is supported
        return installRemotePackages(
                remoteApkPaths, reinstall, isRuntimePermissionSupported(), extraArgs);
    }

    /** {@inheritDoc} */
    @Override
    public String installRemotePackages(
            List<String> remoteApkPaths,
            boolean reinstall,
            boolean grantPermissions,
            String... extraArgs)
            throws DeviceNotAvailableException {
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        if (grantPermissions) {
            ensureRuntimePermissionSupported();
            args.add("-g");
        }
        return internalInstallRemotePackages(remoteApkPaths, reinstall, args);
    }

    /** {@inheritDoc} */
    @Override
    public InputStreamSource getScreenshot() throws DeviceNotAvailableException {
        return getScreenshot("PNG");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot(String format) throws DeviceNotAvailableException {
        return getScreenshot(format, true);
    }

    /** {@inheritDoc} */
    @Override
    public InputStreamSource getScreenshot(String format, boolean rescale)
            throws DeviceNotAvailableException {
        if (!format.equalsIgnoreCase("PNG") && !format.equalsIgnoreCase("JPEG")){
            CLog.e("Screenshot: Format %s is not supported, defaulting to PNG.", format);
            format = "PNG";
        }
        ScreenshotAction action = new ScreenshotAction();
        if (performDeviceAction("screenshot", action, MAX_RETRY_ATTEMPTS)) {
            byte[] imageData =
                    compressRawImage(action.mRawScreenshot, format.toUpperCase(), rescale);
            if (imageData != null) {
                return new ByteArrayInputStreamSource(imageData);
            }
        }
        // Return an error in the buffer
        return new ByteArrayInputStreamSource(
                "Error: device reported null for screenshot.".getBytes());
    }

    /** {@inheritDoc} */
    @Override
    public InputStreamSource getScreenshot(long displayId) throws DeviceNotAvailableException {
        final String tmpDevicePath = String.format("/data/local/tmp/display_%s.png", displayId);
        CommandResult result =
                executeShellV2Command(
                        String.format("screencap -p -d %s %s", displayId, tmpDevicePath));
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            // Return an error in the buffer
            CLog.e("Error: device reported error for screenshot:");
            CLog.e("stdout: %s\nstderr: %s", result.getStdout(), result.getStderr());
            return null;
        }
        try {
            File tmpScreenshot = pullFile(tmpDevicePath);
            if (tmpScreenshot == null) {
                return null;
            }
            return new FileInputStreamSource(tmpScreenshot, true);
        } finally {
            deleteFile(tmpDevicePath);
        }
    }

    private class ScreenshotAction implements DeviceAction {

        RawImage mRawScreenshot;

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException, InstallException, SyncException {
            mRawScreenshot =
                    getIDevice().getScreenshot(MAX_SCREENSHOT_TIMEOUT, TimeUnit.MILLISECONDS);
            return mRawScreenshot != null;
        }
    }

    /**
     * Helper to compress a rawImage obtained from the screen.
     *
     * @param rawImage {@link RawImage} to compress.
     * @param format resulting format of compressed image. PNG and JPEG are supported.
     * @param rescale if rescaling should be done to further reduce size of compressed image.
     * @return compressed image.
     */
    @VisibleForTesting
    byte[] compressRawImage(RawImage rawImage, String format, boolean rescale) {
        BufferedImage image = rawImageToBufferedImage(rawImage, format);

        // Rescale to reduce size if needed
        // Screenshot default format is 1080 x 1920, 8-bit/color RGBA
        // By cutting in half we can easily keep good quality and smaller size
        if (rescale) {
            image = rescaleImage(image);
        }

        return getImageData(image, format);
    }

    /**
     * Converts {@link RawImage} to {@link BufferedImage} in specified format.
     *
     * @param rawImage {@link RawImage} to convert.
     * @param format resulting format of image. PNG and JPEG are supported.
     * @return converted image.
     */
    @VisibleForTesting
    BufferedImage rawImageToBufferedImage(RawImage rawImage, String format) {
        BufferedImage image = null;

        if ("JPEG".equalsIgnoreCase(format)) {
            //JPEG does not support ARGB without a special encoder
            image =
                    new BufferedImage(
                            rawImage.width, rawImage.height, BufferedImage.TYPE_3BYTE_BGR);
        }
        else {
            image = new BufferedImage(rawImage.width, rawImage.height, BufferedImage.TYPE_INT_ARGB);
        }

        // borrowed conversion logic from platform/sdk/screenshot/.../Screenshot.java
        int index = 0;
        int IndexInc = rawImage.bpp >> 3;
        for (int y = 0 ; y < rawImage.height ; y++) {
            for (int x = 0 ; x < rawImage.width ; x++) {
                int value = rawImage.getARGB(index);
                index += IndexInc;
                image.setRGB(x, y, value);
            }
        }

        return image;
    }

    /**
     * Rescales image cutting it in half.
     *
     * @param image source {@link BufferedImage}.
     * @return resulting scaled image.
     */
    @VisibleForTesting
    BufferedImage rescaleImage(BufferedImage image) {
        int shortEdge = Math.min(image.getHeight(), image.getWidth());
        if (shortEdge > 720) {
            Image resized =
                    image.getScaledInstance(
                            image.getWidth() / 2, image.getHeight() / 2, Image.SCALE_SMOOTH);
            image =
                    new BufferedImage(
                            image.getWidth() / 2, image.getHeight() / 2, Image.SCALE_REPLICATE);
            image.getGraphics().drawImage(resized, 0, 0, null);
        }
        return image;
    }

    /**
     * Gets byte array representation of {@link BufferedImage}.
     *
     * @param image source {@link BufferedImage}.
     * @param format resulting format of image. PNG and JPEG are supported.
     * @return byte array representation of the image.
     */
    @VisibleForTesting
    byte[] getImageData(BufferedImage image, String format) {
        // store compressed image in memory, and let callers write to persistent storage
        // use initial buffer size of 128K
        byte[] imageData = null;
        ByteArrayOutputStream imageOut = new ByteArrayOutputStream(128*1024);
        try {
            if (ImageIO.write(image, format, imageOut)) {
                imageData = imageOut.toByteArray();
            } else {
                CLog.e("Failed to compress screenshot to png");
            }
        } catch (IOException e) {
            CLog.e("Failed to compress screenshot to png");
            CLog.e(e);
        }
        StreamUtil.close(imageOut);
        return imageData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean clearErrorDialogs() throws DeviceNotAvailableException {
        CommandResult dismissResult =
                executeShellV2Command(
                        DISMISS_DIALOG_BROADCAST, 60000L, TimeUnit.MILLISECONDS, 0 /*retry*/);
        if (!CommandStatus.SUCCESS.equals(dismissResult.getStatus())) {
            CLog.w("Issue with dimissing dialog broadcast. %s", dismissResult);
        }
        executeShellCommand(COLLAPSE_STATUS_BAR);
        // attempt to clear error dialogs multiple times
        for (int i = 0; i < NUM_CLEAR_ATTEMPTS; i++) {
            int numErrorDialogs = getErrorDialogCount();
            if (numErrorDialogs == 0) {
                return true;
            }
            doClearDialogs(numErrorDialogs);
        }
        if (getErrorDialogCount() > 0) {
            // at this point, all attempts to clear error dialogs completely have failed
            // it might be the case that the process keeps showing new dialogs immediately after
            // clearing. There's really no workaround, but to dump an error
            CLog.e("error dialogs still exist on %s.", getSerialNumber());
            return false;
        }
        return true;
    }

    /**
     * Detects the number of crash or ANR dialogs currently displayed.
     * <p/>
     * Parses output of 'dump activity processes'
     *
     * @return count of dialogs displayed
     * @throws DeviceNotAvailableException
     */
    private int getErrorDialogCount() throws DeviceNotAvailableException {
        int errorDialogCount = 0;
        Pattern crashPattern = Pattern.compile(".*crashing=true.*AppErrorDialog.*");
        Pattern anrPattern = Pattern.compile(".*notResponding=true.*AppNotRespondingDialog.*");
        String systemStatusOutput =
                executeShellCommand(
                        "dumpsys activity processes | grep -e .*crashing=true.*AppErrorDialog.* -e"
                                + " .*notResponding=true.*AppNotRespondingDialog.*");
        Matcher crashMatcher = crashPattern.matcher(systemStatusOutput);
        while (crashMatcher.find()) {
            errorDialogCount++;
        }
        Matcher anrMatcher = anrPattern.matcher(systemStatusOutput);
        while (anrMatcher.find()) {
            errorDialogCount++;
        }

        return errorDialogCount;
    }

    private void doClearDialogs(int numDialogs) throws DeviceNotAvailableException {
        CLog.i("Attempted to clear %d dialogs on %s", numDialogs, getSerialNumber());
        for (int i=0; i < numDialogs; i++) {
            // send DPAD_CENTER
            executeShellCommand(DISMISS_DIALOG_CMD);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableKeyguard() throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        while (true) {
            Boolean ready = isDeviceInputReady();
            if (ready == null) {
                // unsupported API level, bail
                break;
            }
            if (ready) {
                // input dispatch is ready, bail
                break;
            }
            long timeSpent = System.currentTimeMillis() - start;
            if (timeSpent > INPUT_DISPATCH_READY_TIMEOUT) {
                CLog.w("Timeout after waiting %dms on enabling of input dispatch", timeSpent);
                // break & proceed anyway
                break;
            } else {
                getRunUtil().sleep(1000);
            }
        }
        if (getApiLevel() >= 23) {
            CLog.i(
                    "Attempting to disable keyguard on %s using %s",
                    getSerialNumber(), DISMISS_KEYGUARD_WM_CMD);
            String output = executeShellCommand(DISMISS_KEYGUARD_WM_CMD);
            CLog.i("output of %s: %s", DISMISS_KEYGUARD_WM_CMD, output);
        } else {
            CLog.i("Command: %s, is not supported, falling back to %s", DISMISS_KEYGUARD_WM_CMD,
                    DISMISS_KEYGUARD_CMD);
            executeShellCommand(DISMISS_KEYGUARD_CMD);
        }
        verifyKeyguardDismissed();
    }

    private void verifyKeyguardDismissed() throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        while (true) {
            KeyguardControllerState state = getKeyguardState();
            if (state == null) {
                return; // unsupported
            }
            if (!state.isKeyguardShowing()) {
                return; // keyguard dismissed successfully
            }
            long timeSpent = System.currentTimeMillis() - start;
            if (timeSpent > DISMISS_KEYGUARD_TIMEOUT) {
                if (state.isKeyguardGoingAway()) {
                    CLog.w("Keyguard still going away %dms after being dismissed", timeSpent);
                } else {
                    CLog.w("No response from keyguard %dms after being dismissed", timeSpent);
                }
                return; // proceed anyway, may be dismissed in a later step
            }
            getRunUtil().sleep(500);
        }
    }

    /** {@inheritDoc} */
    @Override
    public KeyguardControllerState getKeyguardState() throws DeviceNotAvailableException {
        String output = executeShellCommand(KEYGUARD_CONTROLLER_CMD);
        CLog.d("Output from KeyguardController: %s", output);
        KeyguardControllerState state =
                KeyguardControllerState.create(Arrays.asList(output.trim().split("\n")));
        return state;
    }

    /**
     * Tests the device to see if input dispatcher is ready
     *
     * @return <code>null</code> if not supported by platform, or the actual readiness state
     * @throws DeviceNotAvailableException
     */
    Boolean isDeviceInputReady() throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(TEST_INPUT_CMD, receiver);
        String output = receiver.getOutput();
        Matcher m = INPUT_DISPATCH_STATE_REGEX.matcher(output);
        if (!m.find()) {
            // output does not contain the line at all, implying unsupported API level, bail
            return null;
        }
        return "1".equals(m.group(1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prePostBootSetup() throws DeviceNotAvailableException {
        if (mOptions.isDisableKeyguard()) {
            disableKeyguard();
        }
    }

    /**
     * Performs an reboot via framework power manager
     *
     * <p>Must have root access, device must be API Level 18 or above
     *
     * @param rebootMode a mode of this reboot.
     * @param reason for this reboot.
     * @return <code>true</code> if the device rebooted, <code>false</code> if not successful or
     *     unsupported
     * @throws DeviceNotAvailableException
     */
    private boolean doAdbFrameworkReboot(RebootMode rebootMode, @Nullable final String reason)
            throws DeviceNotAvailableException {
        // use framework reboot when:
        // 1. device API level >= 18
        // 2. has adb root
        // 3. framework is running
        if (!isEnableAdbRoot()) {
            CLog.i("framework reboot is not supported; when enable root is disabled");
            return false;
        }
        boolean isRoot = enableAdbRoot();
        if (getApiLevel() >= 18 && isRoot) {
            try {
                // check framework running
                String output = executeShellCommand("pm path android");
                if (output == null || !output.contains("package:")) {
                    CLog.v("framework reboot: can't detect framework running");
                    return false;
                }
                notifyRebootStarted();
                String command = "svc power reboot " + rebootMode.formatRebootCommand(reason);
                CommandResult result = executeShellV2Command(command);
                if (result.getStdout().contains(EARLY_REBOOT)
                        || result.getStderr().contains(EARLY_REBOOT)) {
                    CLog.e(
                            "Reboot was called too early: stdout: %s.\nstderr: %s.",
                            result.getStdout(), result.getStderr());
                    // notify of this reboot end, since reboot will be retried again at later stage.
                    notifyRebootEnded();
                    return false;
                }
            } catch (DeviceUnresponsiveException due) {
                CLog.v("framework reboot: device unresponsive to shell command, using fallback");
                return false;
            }
            postAdbReboot();
            return true;
        } else {
            CLog.v("framework reboot: not supported");
            return false;
        }
    }

    /**
     * Perform a adb reboot.
     *
     * @param rebootMode a mode of this reboot.
     * @param reason for this reboot.
     * @throws DeviceNotAvailableException
     */
    @Override
    protected void doAdbReboot(RebootMode rebootMode, @Nullable final String reason)
            throws DeviceNotAvailableException {
        getConnection().notifyAdbRebootCalled();
        if (!TestDeviceState.ONLINE.equals(getDeviceState())
                || !doAdbFrameworkReboot(rebootMode, reason)) {
            super.doAdbReboot(rebootMode, reason);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getInstalledPackageNames() throws DeviceNotAvailableException {
        return getInstalledPackageNames(null, null);
    }

    // TODO: convert this to use DumpPkgAction
    private Set<String> getInstalledPackageNames(String packageNameSearched, String userId)
            throws DeviceNotAvailableException {
        Set<String> packages= new HashSet<String>();
        String command = LIST_PACKAGES_CMD;
        if (userId != null) {
            command += String.format(" --user %s", userId);
        }
        if (packageNameSearched != null) {
            command += (" | grep " + packageNameSearched);
        }
        String output = executeShellCommand(command);
        if (output != null) {
            Matcher m = PACKAGE_REGEX.matcher(output);
            while (m.find()) {
                String packageName = m.group(2);
                if (packageNameSearched != null && packageName.equals(packageNameSearched)) {
                    packages.add(packageName);
                } else if (packageNameSearched == null) {
                    packages.add(packageName);
                }
            }
        }
        return packages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPackageInstalled(String packageName) throws DeviceNotAvailableException {
        return getInstalledPackageNames(packageName, null).contains(packageName);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPackageInstalled(String packageName, String userId)
            throws DeviceNotAvailableException {
        return getInstalledPackageNames(packageName, userId).contains(packageName);
    }

    /** {@inheritDoc} */
    @Override
    public Set<ApexInfo> getActiveApexes() throws DeviceNotAvailableException {
        String output = executeShellCommand(LIST_APEXES_CMD);
        // Optimistically parse expecting platform to return paths. If it doesn't, empty set will
        // be returned.
        Set<ApexInfo> ret = parseApexesFromOutput(output, true /* withPath */);
        if (ret.isEmpty()) {
            ret = parseApexesFromOutput(output, false /* withPath */);
        }
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getMainlineModuleInfo() throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease(GET_MODULEINFOS_CMD, 29);
        Set<String> ret = new HashSet<>();
        String output = executeShellCommand(GET_MODULEINFOS_CMD);
        if (output != null) {
            Matcher m = MODULEINFO_REGEX.matcher(output);
            while (m.find()) {
                String packageName = m.group(2);
                ret.add(packageName);
            }
        }
        return ret;
    }

    private Set<ApexInfo> parseApexesFromOutput(final String output, boolean withPath) {
        Set<ApexInfo> ret = new HashSet<>();
        Matcher matcher =
                withPath
                        ? APEXES_WITH_PATH_REGEX.matcher(output)
                        : APEXES_WITHOUT_PATH_REGEX.matcher(output);
        while (matcher.find()) {
            if (withPath) {
                String sourceDir = matcher.group(1);
                String name = matcher.group(2);
                long version = Long.valueOf(matcher.group(3));
                ret.add(new ApexInfo(name, version, sourceDir));
            } else {
                String name = matcher.group(1);
                long version = Long.valueOf(matcher.group(2));
                ret.add(new ApexInfo(name, version));
            }
        }
        return ret;
    }

    /**
     * A {@link com.android.tradefed.device.NativeDevice.DeviceAction}
     * for retrieving package system service info, and do retries on
     * failures.
     */
    private class DumpPkgAction implements DeviceAction {

        Map<String, PackageInfo> mPkgInfoMap;

        DumpPkgAction() {
        }

        @Override
        public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException, InstallException, SyncException {
            DumpsysPackageReceiver receiver = new DumpsysPackageReceiver();
            getIDevice().executeShellCommand("dumpsys package p", receiver);
            mPkgInfoMap = receiver.getPackages();
            if (mPkgInfoMap.size() == 0) {
                // Package parsing can fail if package manager is currently down. throw exception
                // to retry
                CLog.w("no packages found from dumpsys package p.");
                throw new IOException();
            }
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getUninstallablePackageNames() throws DeviceNotAvailableException {
        DumpPkgAction action = new DumpPkgAction();
        performDeviceAction("dumpsys package p", action, MAX_RETRY_ATTEMPTS);

        Set<String> pkgs = new HashSet<String>();
        for (PackageInfo pkgInfo : action.mPkgInfoMap.values()) {
            if (!pkgInfo.isSystemApp() || pkgInfo.isUpdatedSystemApp()) {
                CLog.d("Found uninstallable package %s", pkgInfo.getPackageName());
                pkgs.add(pkgInfo.getPackageName());
            }
        }
        return pkgs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageInfo getAppPackageInfo(String packageName) throws DeviceNotAvailableException {
        DumpPkgAction action = new DumpPkgAction();
        performDeviceAction("dumpsys package", action, MAX_RETRY_ATTEMPTS);
        return action.mPkgInfoMap.get(packageName);
    }

    /** {@inheritDoc} */
    @Override
    public List<PackageInfo> getAppPackageInfos() throws DeviceNotAvailableException {
        DumpPkgAction action = new DumpPkgAction();
        performDeviceAction("dumpsys package", action, MAX_RETRY_ATTEMPTS);
        return new ArrayList<>(action.mPkgInfoMap.values());
    }

    /** {@inheritDoc} */
    @Override
    public boolean doesFileExist(String deviceFilePath) throws DeviceNotAvailableException {
        int currentUser = 0;
        if (deviceFilePath.startsWith(SD_CARD)) {
            if (getApiLevel() > 23) {
                // Don't trigger the current logic if unsupported
                currentUser = getCurrentUser();
            }
        }
        return doesFileExist(deviceFilePath, currentUser);
    }

    /** {@inheritDoc} */
    @Override
    public boolean doesFileExist(String deviceFilePath, int userId)
            throws DeviceNotAvailableException {
        if (deviceFilePath.startsWith(SD_CARD)) {
            deviceFilePath =
                    deviceFilePath.replaceFirst(
                            SD_CARD, String.format("/storage/emulated/%s/", userId));
        }
        return super.doesFileExist(deviceFilePath, userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
        ArrayList<String[]> users = tokenizeListUsers();
        ArrayList<Integer> userIds = new ArrayList<Integer>(users.size());
        for (String[] user : users) {
            userIds.add(Integer.parseInt(user[1]));
        }
        return userIds;
    }

    /** {@inheritDoc} */
    @Override
    public Map<Integer, UserInfo> getUserInfos() throws DeviceNotAvailableException {
        ArrayList<String[]> lines = tokenizeListUsers();
        Map<Integer, UserInfo> result = new HashMap<Integer, UserInfo>(lines.size());
        for (String[] tokens : lines) {
            if (getApiLevel() < 33) {
                UserInfo userInfo =
                        new UserInfo(
                                /* userId= */ Integer.parseInt(tokens[1]),
                                /* userName= */ tokens[2],
                                /* flag= */ Integer.parseInt(tokens[3], 16),
                                /* isRunning= */ tokens.length >= 5
                                        ? tokens[4].contains("running")
                                        : false);
                result.put(userInfo.userId(), userInfo);
            } else {
                UserInfo userInfo =
                        new UserInfo(
                                /* userId= */ Integer.parseInt(tokens[1]),
                                /* userName= */ tokens[2],
                                /* flag= */ Integer.parseInt(tokens[3], 16),
                                /* isRunning= */ tokens.length >= 5
                                        ? tokens[4].contains("running")
                                        : false,
                                /* userType= */ tokens[5]);
                result.put(userInfo.userId(), userInfo);
            }
        }
        return result;
    }

    /**
     * Tokenizes the output of 'pm list users' pre-T and 'cmd user list -v' post-T.
     *
     * <p>Pre-T: The returned tokens for each user have the form: {"\tUserInfo",
     * Integer.toString(id), name, Integer.toHexString(flag), "[running]"}; (the last one being
     * optional)
     *
     * <p>Post-T: The returned tokens for each user have the form: {"\tUserInfo", Integer
     * .toString(id), name, Integer.toHexString(flag), "[running]", type}; (the last two being
     * optional)
     *
     * @return a list of arrays of strings, each element of the list representing the tokens for a
     *     user, or {@code null} if there was an error while tokenizing the adb command output.
     */
    private ArrayList<String[]> tokenizeListUsers() throws DeviceNotAvailableException {
        if (getApiLevel() < 33) { // Android-T
            return tokenizeListUsersPreT();
        } else {
            return tokenizeListUserPostT();
        }
    }

    private ArrayList<String[]> tokenizeListUserPostT() throws DeviceNotAvailableException {
        String command = "cmd user list -v";
        String commandOutput = null;
        for (int i = 0; i < NUM_USER_INFO_ATTEMPTS; i++) {
            commandOutput = executeShellCommand(command);
            if (commandOutput == null || commandOutput.trim().isEmpty()) {
                CLog.d("Command `%s` executed with no output. (attempt %d)", command, i);
                // Throw exception if the last attempt failed too.
                if (i == NUM_USER_INFO_ATTEMPTS - 1) {
                    throw new DeviceRuntimeException(
                            String.format(
                                    "Command `%s` executed with no output. Attempts made = %d.",
                                    command, NUM_USER_INFO_ATTEMPTS),
                            DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
                }
            } else {
                break;
            }
            // wait before retrying
            RunUtil.getDefault().sleep(1000);
        }
        // Extract the id of all existing users.
        List<String> lines =
                Arrays.stream(commandOutput.split("\\r?\\n"))
                        .filter(line -> line != null && line.trim().length() != 0)
                        .collect(Collectors.toList());

        if (!lines.get(0).contains("users:")) {
            if (commandOutput.contains("cmd: Can't find service: package")) {
                throw new DeviceNotAvailableException(
                        String.format(
                                "'%s' in not a valid output for 'user list -v'", commandOutput),
                        getSerialNumber());
            }
            throw new DeviceRuntimeException(
                    String.format("'%s' in not a valid output for 'user list -v'", commandOutput),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        ArrayList<String[]> users = new ArrayList<String[]>(lines.size() - 1);

        String pattern = ".id=(.*), name=(.*), type=(.*), flags=(.*)";
        Pattern r = Pattern.compile(pattern);
        for (int i = 1; i < lines.size(); i++) {
            // Individual user is printed out like this:
            // idx: id=$id, name=$name, type=$type, flags=AAA|BBB|XXX (running) (current) (visible)
            Matcher m = r.matcher(lines.get(i));
            if (m.find()) {
                String id = m.group(1);
                String name = m.group(2);
                // example: full.SYSTEM, profile.XXX
                String type = m.group(3);
                // AAA|BBB|XXX (running) (current) (visible)
                String flags_and_status = m.group(4);

                String flags = "";
                String status = "";
                if (flags_and_status != null) {
                    // Split flags and convert to hex
                    // output: [AAA, BBB, XXX (running) (current) (visible)]
                    String[] flagsArr = flags_and_status.split("\\|");
                    // XXX (running) (current) (visible)
                    String last_flag_and_status =
                            flagsArr.length > 0 ? flagsArr[flagsArr.length - 1] : "";
                    String[] arr = last_flag_and_status.split("\\s", 2);
                    if (arr.length > 0) {
                        flags = Integer.toHexString(convertToHex(flagsArr, arr[0]));
                    }
                    if (arr.length > 1) {
                        status = arr[1] != null ? arr[1] : "";
                    }
                }
                // Maintain same sequence as per-Q output, add type at the end.
                users.add(new String[] {"", id, name, flags, status, type});
            }
        }
        return users;
    }

    private ArrayList<String[]> tokenizeListUsersPreT() throws DeviceNotAvailableException {
        String command = "pm list users";
        String commandOutput = null;
        for (int i = 0; i < NUM_USER_INFO_ATTEMPTS; i++) {
            commandOutput = executeShellCommand(command);
            if (commandOutput == null || commandOutput.trim().isEmpty()) {
                CLog.d("Command `%s` executed with no output. (attempt %d)", command, i);
                // Throw exception if the last attempt failed too.
                if (i == NUM_USER_INFO_ATTEMPTS - 1) {
                    throw new DeviceRuntimeException(
                            String.format(
                                    "Command `%s` executed with no output. Attempts made = %d.",
                                    command, NUM_USER_INFO_ATTEMPTS),
                            DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
                }
            } else {
                break;
            }
            // wait before retrying
            RunUtil.getDefault().sleep(1000);
        }
        // Extract the id of all existing users.
        String[] lines = commandOutput.split("\\r?\\n");
        if (!lines[0].equals("Users:")) {
            throw new DeviceRuntimeException(
                    String.format("'%s' in not a valid output for 'pm list users'", commandOutput),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        ArrayList<String[]> users = new ArrayList<String[]>(lines.length - 1);
        for (int i = 1; i < lines.length; i++) {
            // Individual user is printed out like this:
            // \tUserInfo{$id$:$name$:$Integer.toHexString(flags)$} [running]
            String[] tokens = lines[i].split("\\{|\\}|:");
            if (tokens.length != 4 && tokens.length != 5) {
                throw new DeviceRuntimeException(
                        String.format(
                                "device output: '%s' \nline: '%s' was not in the expected "
                                        + "format for user info.",
                                commandOutput, lines[i]),
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            }
            users.add(tokens);
        }
        return users;
    }

    private int convertToHex(String[] arr, String str) {
        int res = 0;

        for (int i = 0; i < arr.length - 1; i++) {
            res |= getHexaDecimalValue(arr[i]);
        }
        res |= getHexaDecimalValue(str);

        return res;
    }

    private int getHexaDecimalValue(String flag) {
        switch (flag) {
            case "PRIMARY":
                return 0x00000001;
            case "ADMIN":
                return 0x00000002;
            case "GUEST":
                return 0x00000004;
            case "RESTRICTED":
                return 0x00000008;
            case "INITIALIZED":
                return 0x00000010;
            case "MANAGED_PROFILE":
                return 0x00000020;
            case "DISABLED":
                return 0x00000040;
            case "QUIET_MODE":
                return 0x00000080;
            case "EPHEMERAL":
                return 0x00000100;
            case "DEMO":
                return 0x00000200;
            case "FULL":
                return 0x00000400;
            case "SYSTEM":
                return 0x00000800;
            case "PROFILE":
                return 0x00001000;
            case "EPHEMERAL_ON_CREATE":
                return 0x00002000;
            case "MAIN":
                return 0x00004000;
            case "FOR_TESTING":
                return 0x00008000;
            default:
                CLog.e("Flag %s not found.", flag);
                return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxNumberOfUsersSupported() throws DeviceNotAvailableException {
        String command = "pm get-max-users";
        String commandOutput = executeShellCommand(command);
        try {
            return Integer.parseInt(commandOutput.substring(commandOutput.lastIndexOf(" ")).trim());
        } catch (NumberFormatException e) {
            CLog.e("Failed to parse result: %s", commandOutput);
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxNumberOfRunningUsersSupported() throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("get-max-running-users", 28);
        String command = "pm get-max-running-users";
        String commandOutput = executeShellCommand(command);
        try {
            return Integer.parseInt(commandOutput.substring(commandOutput.lastIndexOf(" ")).trim());
        } catch (NumberFormatException e) {
            CLog.e("Failed to parse result: %s", commandOutput);
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultiUserSupported() throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("get-max-running-users", 28);
        final int apiLevel = getApiLevel();
        if (apiLevel > 33) {
            String command = "pm supports-multiple-users";
            String commandOutput = executeShellCommand(command).trim();
            try {
                String parsedOutput =
                        commandOutput.substring(commandOutput.lastIndexOf(" ")).trim();
                Boolean retValue = Boolean.valueOf(parsedOutput);
                return retValue;
            } catch (NumberFormatException e) {
                CLog.e("Failed to parse result: %s", commandOutput);
                return false;
            }
        }
        return getMaxNumberOfUsersSupported() > 1;
    }

    @Override
    public boolean isHeadlessSystemUserMode() throws DeviceNotAvailableException {
        checkApiLevelAgainst("isHeadlessSystemUserMode", 29);
        return checkApiLevelAgainstNextRelease(34)
                ? executeShellV2CommandThatReturnsBooleanSafe(
                        "cmd user is-headless-system-user-mode")
                : getBooleanProperty("ro.fw.mu.headless_system_user", false);
    }

    /** {@inheritDoc} */
    @Override
    public boolean canSwitchToHeadlessSystemUser() throws DeviceNotAvailableException {
        checkApiLevelAgainst("canSwitchToHeadlessSystemUser", 34);
        return executeShellV2CommandThatReturnsBooleanSafe(
                "cmd user can-switch-to-headless-system-user");
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMainUserPermanentAdmin() throws DeviceNotAvailableException {
        checkApiLevelAgainst("isMainUserPermanentAdmin", 34);
        return executeShellV2CommandThatReturnsBooleanSafe("cmd user is-main-user-permanent-admin");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int createUser(String name) throws DeviceNotAvailableException, IllegalStateException {
        return createUser(name, false, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int createUser(String name, boolean guest, boolean ephemeral)
            throws DeviceNotAvailableException, IllegalStateException {
        return createUser(name, guest, ephemeral, /* forTesting= */ false);
    }

    /** {@inheritDoc} */
    @Override
    public int createUser(String name, boolean guest, boolean ephemeral, boolean forTesting)
            throws DeviceNotAvailableException, IllegalStateException {
        String command =
                "pm create-user "
                        + (guest ? "--guest " : "")
                        + (ephemeral ? "--ephemeral " : "")
                        + (forTesting && getApiLevel() >= 34 ? "--for-testing " : "")
                        + name;
        final String output = executeShellCommand(command);
        if (output.startsWith("Success")) {
            try {
                resetContentProviderSetup();
                return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            } catch (NumberFormatException e) {
                CLog.e("Failed to parse result: %s", output);
            }
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }

    /** {@inheritDoc} */
    @Override
    public int createUserNoThrow(String name) throws DeviceNotAvailableException {
        try {
            return createUser(name);
        } catch (IllegalStateException e) {
            CLog.e("Error creating user: " + e.toString());
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeUser(int userId) throws DeviceNotAvailableException {
        final String output = executeShellCommand(String.format("pm remove-user %s", userId));
        if (output.startsWith("Error")) {
            CLog.e("Failed to remove user %d on device %s: %s", userId, getSerialNumber(), output);
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean startUser(int userId) throws DeviceNotAvailableException {
        return startUser(userId, false);
    }

    /** {@inheritDoc} */
    @Override
    public boolean startUser(int userId, boolean waitFlag) throws DeviceNotAvailableException {
        if (waitFlag) {
            checkApiLevelAgainstNextRelease("start-user -w", 29);
        }
        String cmd = "am start-user " + (waitFlag ? "-w " : "") + userId;

        CLog.d("Starting user with command: %s", cmd);
        final String output = executeShellCommand(cmd);
        if (output.startsWith("Error")) {
            CLog.e("Failed to start user: %s", output);
            return false;
        }
        if (waitFlag) {
            String state = executeShellCommand("am get-started-user-state " + userId);
            if (!state.contains("RUNNING_UNLOCKED")) {
                CLog.w("User %s is not RUNNING_UNLOCKED after start-user -w. (%s).", userId, state);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean startVisibleBackgroundUser(int userId, int displayId, boolean waitFlag)
            throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("startVisibleBackgroundUser", 34);

        String cmd =
                String.format(
                        "am start-user%s --display %d %d",
                        (waitFlag ? " -w" : ""), displayId, userId);
        CommandResult res = executeShellV2Command(cmd);
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            throw new DeviceRuntimeException(
                    "Command  '" + cmd + "' failed: " + res,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
        return res.getStdout().trim().startsWith("Success");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stopUser(int userId) throws DeviceNotAvailableException {
        // No error or status code is returned.
        return stopUser(userId, false, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stopUser(int userId, boolean waitFlag, boolean forceFlag)
            throws DeviceNotAvailableException {
        final int apiLevel = getApiLevel();
        if (waitFlag && apiLevel < 23) {
            throw new IllegalArgumentException("stop-user -w requires API level >= 23");
        }
        if (forceFlag && apiLevel < 24) {
            throw new IllegalArgumentException("stop-user -f requires API level >= 24");
        }
        StringBuilder cmd = new StringBuilder("am stop-user ");
        if (waitFlag) {
            cmd.append("-w ");
        }
        if (forceFlag) {
            cmd.append("-f ");
        }
        cmd.append(userId);

        CLog.d("stopping user with command: %s", cmd.toString());
        final String output = executeShellCommand(cmd.toString());
        if (output.contains("Error: Can't stop system user")) {
            CLog.e("Cannot stop System user.");
            return false;
        }
        if (output.contains("Can't stop current user")) {
            CLog.e("Cannot stop current user.");
            return false;
        }
        if (isUserRunning(userId)) {
            CLog.w("User Id: %s is still running after the stop-user command.", userId);
            return false;
        }
        return true;
    }

    @Override
    public boolean isVisibleBackgroundUsersSupported() throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("isHeadlessSystemUserMode", 34);

        return executeShellV2CommandThatReturnsBoolean(
                "cmd user is-visible-background-users-supported");
    }

    @Override
    public boolean isVisibleBackgroundUsersOnDefaultDisplaySupported()
            throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("isVisibleBackgroundUsersOnDefaultDisplaySupported", 34);

        return executeShellV2CommandThatReturnsBoolean(
                "cmd user is-visible-background-users-on-default-display-supported");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getPrimaryUserId() throws DeviceNotAvailableException {
        return getUserIdByFlag(FLAG_PRIMARY);
    }

    /** {@inheritDoc} */
    @Override
    public Integer getMainUserId() throws DeviceNotAvailableException {
        return getUserIdByFlag(FLAG_MAIN);
    }

    private Integer getUserIdByFlag(int requiredFlag) throws DeviceNotAvailableException {
        ArrayList<String[]> users = tokenizeListUsers();
        for (String[] user : users) {
            int flag = Integer.parseInt(user[3], 16);
            if ((flag & requiredFlag) != 0) {
                return Integer.parseInt(user[1]);
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public int getCurrentUser() throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("get-current-user", API_LEVEL_GET_CURRENT_USER);
        final String output = executeShellCommand("am get-current-user");
        try {
            int userId = Integer.parseInt(output.trim());
            if (userId >= 0) {
                return userId;
            }
            CLog.e("Invalid user id '%s' was returned for get-current-user", userId);
        } catch (NumberFormatException e) {
            CLog.e("Invalid string was returned for get-current-user: %s.", output);
        }
        return INVALID_USER_ID;
    }

    @Override
    public boolean isUserVisible(int userId) throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("isUserVisible", 34);

        return executeShellV2CommandThatReturnsBoolean("cmd user is-user-visible %d", userId);
    }

    @Override
    public boolean isUserVisibleOnDisplay(int userId, int displayId)
            throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("isUserVisibleOnDisplay", 34);

        return executeShellV2CommandThatReturnsBoolean(
                "cmd user is-user-visible --display %d %d", displayId, userId);
    }

    private Matcher findUserInfo(String pmListUsersOutput) {
        Pattern pattern = Pattern.compile(USER_PATTERN);
        Matcher matcher = pattern.matcher(pmListUsersOutput);
        return matcher;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUserFlags(int userId) throws DeviceNotAvailableException {
        checkApiLevelAgainst("getUserFlags", 22);
        final String commandOutput = executeShellCommand("pm list users");
        Matcher matcher = findUserInfo(commandOutput);
        while(matcher.find()) {
            if (Integer.parseInt(matcher.group(2)) == userId) {
                return Integer.parseInt(matcher.group(6), 16);
            }
        }
        CLog.w("Could not find any flags for userId: %d in output: %s", userId, commandOutput);
        return INVALID_USER_ID;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isUserSecondary(int userId) throws DeviceNotAvailableException {
        if (userId == UserInfo.USER_SYSTEM) {
            return false;
        }
        int flags = getUserFlags(userId);
        if (flags == INVALID_USER_ID) {
            return false;
        }
        return (flags & UserInfo.FLAGS_NOT_SECONDARY) == 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUserRunning(int userId) throws DeviceNotAvailableException {
        checkApiLevelAgainst("isUserIdRunning", 22);
        final String commandOutput = executeShellCommand("pm list users");
        Matcher matcher = findUserInfo(commandOutput);
        while(matcher.find()) {
            if (Integer.parseInt(matcher.group(2)) == userId) {
                if (matcher.group(7).contains("running")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUserSerialNumber(int userId) throws DeviceNotAvailableException {
        checkApiLevelAgainst("getUserSerialNumber", 22);
        final String commandOutput = executeShellCommand("dumpsys user");
        // example: UserInfo{0:Test:13} serialNo=0
        String userSerialPatter = "(.*\\{)(\\d+)(.*\\})(.*=)(\\d+)";
        Pattern pattern = Pattern.compile(userSerialPatter);
        Matcher matcher = pattern.matcher(commandOutput);
        while(matcher.find()) {
            if (Integer.parseInt(matcher.group(2)) == userId) {
                return Integer.parseInt(matcher.group(5));
            }
        }
        CLog.w("Could not find user serial number for userId: %d, in output: %s",
                userId, commandOutput);
        return INVALID_USER_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchUser(int userId) throws DeviceNotAvailableException {
        return switchUser(userId, AM_COMMAND_TIMEOUT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchUser(int userId, long timeout) throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("switchUser", API_LEVEL_GET_CURRENT_USER);
        if (userId == getCurrentUser()) {
            CLog.w("Already running as user id: %s. Nothing to be done.", userId);
            return true;
        }

        String switchCommand =
                checkApiLevelAgainstNextRelease(30)
                        ? String.format("am switch-user -w %d", userId)
                        : String.format("am switch-user %d", userId);

        resetContentProviderSetup();
        long initialTime = getHostCurrentTime();
        String output = executeShellCommand(switchCommand);
        boolean success = userId == getCurrentUser();

        while (!success && (getHostCurrentTime() - initialTime <= timeout)) {
            // retry
            RunUtil.getDefault().sleep(getCheckNewUserSleep());
            output = executeShellCommand(String.format(switchCommand));
            success = userId == getCurrentUser();
        }

        CLog.d("switchUser took %d ms", getHostCurrentTime() - initialTime);
        if (success) {
            prePostBootSetup();
            return true;
        } else {
            CLog.e("User did not switch in the given %d timeout: %s", timeout, output);
            return false;
        }
    }

    /**
     * Exposed for testing.
     */
    protected long getCheckNewUserSleep() {
        return CHECK_NEW_USER;
    }

    /**
     * Exposed for testing
     */
    protected long getHostCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFeature(String feature) throws DeviceNotAvailableException {
        // Add support for directly checking a feature and match the pm output.
        if (!feature.startsWith("feature:")) {
            feature = "feature:" + feature;
        }
        final String versionedFeature = feature + "=";
        CommandResult commandResult = executeShellV2Command("pm list features");
        if (!CommandStatus.SUCCESS.equals(commandResult.getStatus())) {
            throw new DeviceRuntimeException(
                    String.format(
                            "Failed to list features, command returned: stdout: %s, stderr: %s",
                            commandResult.getStdout(), commandResult.getStderr()),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        String commandOutput = commandResult.getStdout();
        for (String line: commandOutput.split("\\s+")) {
            // Each line in the output of the command has the format
            // "feature:{FEATURE_VALUE}[={FEATURE_VERSION}]".
            if (line.equals(feature)) {
                return true;
            }
            if (line.startsWith(versionedFeature)) {
                return true;
            }
        }
        CLog.w("Feature: %s is not available on %s", feature, getSerialNumber());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSetting(String namespace, String key) throws DeviceNotAvailableException {
        return getSettingInternal("", namespace.trim(), key.trim());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSetting(int userId, String namespace, String key)
            throws DeviceNotAvailableException {
        return getSettingInternal(String.format("--user %d", userId), namespace.trim(), key.trim());
    }

    /**
     * Internal Helper to get setting with or without a userId provided.
     */
    private String getSettingInternal(String userFlag, String namespace, String key)
            throws DeviceNotAvailableException {
        namespace = namespace.toLowerCase();
        if (Arrays.asList(SETTINGS_NAMESPACE).contains(namespace)) {
            String cmd = String.format("settings %s get %s %s", userFlag, namespace, key);
            String output = executeShellCommand(cmd);
            if ("null".equals(output)) {
                CLog.w("settings returned null for command: %s. "
                        + "please check if the namespace:key exists", cmd);
                return null;
            }
            return output.trim();
        }
        CLog.e("Namespace requested: '%s' is not part of {system, secure, global}", namespace);
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getAllSettings(String namespace) throws DeviceNotAvailableException {
        return getAllSettingsInternal(namespace.trim());
    }

    /** Internal helper to get all settings */
    private Map<String, String> getAllSettingsInternal(String namespace)
            throws DeviceNotAvailableException {
        namespace = namespace.toLowerCase();
        if (Arrays.asList(SETTINGS_NAMESPACE).contains(namespace)) {
            Map<String, String> map = new HashMap<>();
            String cmd = String.format("settings list %s", namespace);
            String output = executeShellCommand(cmd);
            for (String line : output.split("\\n")) {
                // Setting's value could be empty
                String[] pair = line.trim().split("=", -1);
                if (pair.length > 1) {
                    map.putIfAbsent(pair[0], pair[1]);
                } else {
                    CLog.e("Unable to get setting from string: %s", line);
                }
            }
            return map;
        }
        CLog.e("Namespace requested: '%s' is not part of {system, secure, global}", namespace);
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSetting(String namespace, String key, String value)
            throws DeviceNotAvailableException {
        setSettingInternal("", namespace.trim(), key.trim(), value.trim());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSetting(int userId, String namespace, String key, String value)
            throws DeviceNotAvailableException {
        setSettingInternal(String.format("--user %d", userId), namespace.trim(), key.trim(),
                value.trim());
    }

    /**
     * Internal helper to set a setting with or without a userId provided.
     */
    private void setSettingInternal(String userFlag, String namespace, String key, String value)
            throws DeviceNotAvailableException {
        checkApiLevelAgainst("Changing settings", 22);
        if (Arrays.asList(SETTINGS_NAMESPACE).contains(namespace.toLowerCase())) {
            executeShellCommand(String.format("settings %s put %s %s %s",
                    userFlag, namespace, key, value));
        } else {
            throw new IllegalArgumentException("Namespace must be one of system, secure, global."
                    + " You provided: " + namespace);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAndroidId(int userId) throws DeviceNotAvailableException {
        if (isAdbRoot()) {
            String cmd = String.format(
                    "sqlite3 /data/user/%d/*/databases/gservices.db "
                    + "'select value from main where name = \"android_id\"'", userId);
            String output = executeShellCommand(cmd).trim();
            if (!output.contains("unable to open database")) {
                return output;
            }
            CLog.w("Couldn't find android-id, output: %s", output);
        } else {
            CLog.w("adb root is required.");
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Integer, String> getAndroidIds() throws DeviceNotAvailableException {
        ArrayList<Integer> userIds = listUsers();
        if (userIds == null) {
            return null;
        }
        Map<Integer, String> androidIds = new HashMap<Integer, String>();
        for (Integer id : userIds) {
            String androidId = getAndroidId(id);
            androidIds.put(id, androidId);
        }
        return androidIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    IWifiHelper createWifiHelper() throws DeviceNotAvailableException {
        return createWifiHelper(false);
    }

    @Override
    IWifiHelper createWifiHelper(boolean useV2) throws DeviceNotAvailableException {
        if (useV2) {
            CLog.d("Using WifiHelper V2. WifiUtil apk installation skipped.");
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.WIFI_HELPER_V2, "true");
            return createWifiHelper(useV2, false);
        } else {
            return createWifiHelper(useV2, true);
        }
    }

    /**
     * Alternative to {@link #createWifiHelper()} where we can choose whether to do the wifi helper
     * setup or not.
     */
    @VisibleForTesting
    IWifiHelper createWifiHelper(boolean useV2, boolean doSetup)
            throws DeviceNotAvailableException {
        if (doSetup) {
            mWasWifiHelperInstalled = true;
            // Ensure device is ready before attempting wifi setup
            waitForDeviceAvailable();
        }
        return new WifiHelper(this, mOptions.getWifiUtilAPKPath(), doSetup, useV2);
    }

    /** {@inheritDoc} */
    @Override
    public void postInvocationTearDown(Throwable exception) {
        super.postInvocationTearDown(exception);
        // If wifi was installed and it's a real device, attempt to clean it.
        if (mWasWifiHelperInstalled) {
            mWasWifiHelperInstalled = false;
            if (getIDevice() instanceof StubDevice) {
                return;
            }
            if (!TestDeviceState.ONLINE.equals(getDeviceState())) {
                return;
            }
            if (exception instanceof DeviceNotAvailableException) {
                CLog.e("Skip WifiHelper teardown due to DeviceNotAvailableException.");
                return;
            }
            try {
                // Uninstall the wifi utility if it was installed.
                IWifiHelper wifi = createWifiHelper(false, false);
                wifi.cleanUp();
            } catch (DeviceNotAvailableException e) {
                CLog.e("Device became unavailable while uninstalling wifi util.");
                CLog.e(e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean setDeviceOwner(String componentName, int userId)
            throws DeviceNotAvailableException {
        final String command = "dpm set-device-owner --user " + userId + " '" + componentName + "'";
        final String commandOutput = executeShellCommand(command);
        return commandOutput.startsWith("Success:");
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAdmin(String componentName, int userId)
            throws DeviceNotAvailableException {
        final String command =
                "dpm remove-active-admin --user " + userId + " '" + componentName + "'";
        final String commandOutput = executeShellCommand(command);
        return commandOutput.startsWith("Success:");
    }

    /** {@inheritDoc} */
    @Override
    public void removeOwners() throws DeviceNotAvailableException {
        String command = "dumpsys device_policy";
        String commandOutput = executeShellCommand(command);
        String[] lines = commandOutput.split("\\r?\\n");
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (line.contains("Profile Owner")) {
                // Line is "Profile owner (User <id>):
                String[] tokens = line.split("\\(|\\)| ");
                int userId = Integer.parseInt(tokens[4]);

                i = moveToNextIndexMatchingRegex(".*admin=.*", lines, i);
                line = lines[i].trim();
                // Line is admin=ComponentInfo{<component>}
                tokens = line.split("\\{|\\}");
                String componentName = tokens[1];
                CLog.d("Cleaning up profile owner " + userId + " " + componentName);
                removeAdmin(componentName, userId);
            } else if (line.contains("Device Owner:")) {
                i = moveToNextIndexMatchingRegex(".*admin=.*", lines, i);
                line = lines[i].trim();
                // Line is admin=ComponentInfo{<component>}
                String[] tokens = line.split("\\{|\\}");
                String componentName = tokens[1];

                // Skip to user id line.
                i = moveToNextIndexMatchingRegex(".*User ID:.*", lines, i);
                line = lines[i].trim();
                // Line is User ID: <N>
                tokens = line.split(":");
                int userId = Integer.parseInt(tokens[1].trim());
                CLog.d("Cleaning up device owner " + userId + " " + componentName);
                removeAdmin(componentName, userId);
            }
        }
    }

    /**
     * Search forward from the current index to find a string matching the given regex.
     *
     * @param regex The regex to match each line against.
     * @param lines An array of strings to be searched.
     * @param currentIndex the index to start searching from.
     * @return The index of a string beginning with the regex.
     * @throws IllegalStateException if the line cannot be found.
     */
    private int moveToNextIndexMatchingRegex(String regex, String[] lines, int currentIndex) {
        while (currentIndex < lines.length && !lines[currentIndex].matches(regex)) {
            currentIndex++;
        }

        if (currentIndex >= lines.length) {
            throw new IllegalStateException(
                    "The output of 'dumpsys device_policy' was not as expected. Owners have not "
                            + "been removed. This will leave the device in an unstable state and "
                            + "will lead to further test failures.");
        }

        return currentIndex;
    }

    /**
     * Helper for Api level checking of features in the new release before we incremented the api
     * number.
     */
    private void checkApiLevelAgainstNextRelease(String feature, int strictMinLevel)
            throws DeviceNotAvailableException {
        if (checkApiLevelAgainstNextRelease(strictMinLevel)) {
            return;
        }
        throw new IllegalArgumentException(
                String.format(
                        "%s not supported on %s. Must be API %d.",
                        feature, getSerialNumber(), strictMinLevel));
    }

    @Override
    public File dumpHeap(String process, String devicePath) throws DeviceNotAvailableException {
        if (Strings.isNullOrEmpty(devicePath) || Strings.isNullOrEmpty(process)) {
            throw new IllegalArgumentException("devicePath or process cannot be null or empty.");
        }
        String pid = getProcessPid(process);
        if (pid == null) {
            return null;
        }
        File dump = dumpAndPullHeap(pid, devicePath);
        // Clean the device.
        deleteFile(devicePath);
        return dump;
    }

    /** Dump the heap file and pull it from the device. */
    private File dumpAndPullHeap(String pid, String devicePath) throws DeviceNotAvailableException {
        executeShellCommand(String.format(DUMPHEAP_CMD, pid, devicePath));
        // Allow a little bit of time for the file to populate on device side.
        int attempt = 0;
        // TODO: add an API to check device file size
        while (!doesFileExist(devicePath) && attempt < 3) {
            getRunUtil().sleep(DUMPHEAP_TIME);
            attempt++;
        }
        File dumpFile = pullFile(devicePath);
        return dumpFile;
    }

    /** {@inheritDoc} */
    @Override
    public Set<Long> listDisplayIds() throws DeviceNotAvailableException {
        Set<Long> displays = new HashSet<>();
        CommandResult res = executeShellV2Command("dumpsys SurfaceFlinger | grep 'color modes:'");
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            CLog.e("Something went wrong while listing displays: %s", res.getStderr());
            return displays;
        }
        String output = res.getStdout();
        Pattern p = Pattern.compile(DISPLAY_ID_PATTERN);
        for (String line : output.split("\n")) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                displays.add(Long.parseLong(m.group("id")));
            }
        }

        // If the device is older and did not report any displays
        // then add the default.
        // Note: this assumption breaks down if the device also has multiple displays
        if (displays.isEmpty()) {
            // Zero is the default display
            displays.add(0L);
        }

        return displays;
    }

    @Override
    public Set<Integer> listDisplayIdsForStartingVisibleBackgroundUsers()
            throws DeviceNotAvailableException {
        checkApiLevelAgainstNextRelease("getDisplayIdsForStartingVisibleBackgroundUsers", 34);

        String cmd = "cmd activity list-displays-for-starting-users";
        CommandResult res = executeShellV2Command(cmd);
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            throw new DeviceRuntimeException(
                    "Command  '" + cmd + "' failed: " + res,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
        String output = res.getStdout().trim();

        if (output.equalsIgnoreCase("none")) {
            return Collections.emptySet();
        }

        // TODO: reuse some helper to parse the list
        if (!output.startsWith("[") || !output.endsWith("]")) {
            throw new DeviceRuntimeException(
                    "Invalid output for command '" + cmd + "': " + output,
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        String contents = output.substring(1, output.length() - 1);
        try {
            String[] ids = contents.split(",");
            return Arrays.asList(ids).stream()
                    .map(id -> Integer.parseInt(id.trim()))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new DeviceRuntimeException(
                    "Invalid output for command '" + cmd + "': " + output,
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    @Override
    public Set<DeviceFoldableState> getFoldableStates() throws DeviceNotAvailableException {
        if (getIDevice() instanceof StubDevice) {
            return new HashSet<>();
        }
        try (CloseableTraceScope foldable = new CloseableTraceScope("getFoldableStates")) {
            CommandResult result = executeShellV2Command("cmd device_state print-states");
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                // Can't throw an exception since it would fail on non-supported version
                return new HashSet<>();
            }
            Set<DeviceFoldableState> foldableStates = new LinkedHashSet<>();
            Pattern deviceStatePattern =
                    Pattern.compile(
                            "DeviceState\\{identifier=(\\d+), name='(\\S+)'"
                                    + "(?:, app_accessible=)?(\\S+)?"
                                    + "(?:, cancel_when_requester_not_on_top=)?(\\S+)?"
                                    + "\\}\\S*");
            for (String line : result.getStdout().split("\n")) {
                Matcher m = deviceStatePattern.matcher(line.trim());
                if (m.matches()) {
                    // Move onto the next state if the device state is not accessible by apps
                    if (m.groupCount() > 2
                            && m.group(3) != null
                            && !Boolean.parseBoolean(m.group(3))) {
                        continue;
                    }
                    // Move onto the next state if the device state is canceled when the requesting
                    // app
                    // is not on top.
                    if (m.groupCount() > 3
                            && m.group(4) != null
                            && Boolean.parseBoolean(m.group(4))) {
                        continue;
                    }
                    foldableStates.add(
                            new DeviceFoldableState(Integer.parseInt(m.group(1)), m.group(2)));
                }
            }
            return foldableStates;
        }
    }

    @Override
    public DeviceFoldableState getCurrentFoldableState() throws DeviceNotAvailableException {
        if (getIDevice() instanceof StubDevice) {
            return null;
        }
        CommandResult result = executeShellV2Command("cmd device_state state");
        Pattern deviceStatePattern =
                Pattern.compile(
                        "Committed state: DeviceState\\{identifier=(\\d+), name='(\\S+)'"
                                + "(?:, app_accessible=)?(\\S+)?"
                                + "(?:, cancel_when_requester_not_on_top=)?(\\S+)?"
                                + "\\}\\S*");
        for (String line : result.getStdout().split("\n")) {
            Matcher m = deviceStatePattern.matcher(line.trim());
            if (m.matches()) {
                return new DeviceFoldableState(Integer.parseInt(m.group(1)), m.group(2));
            }
        }
        return null;
    }

    /**
     * Checks the preconditions to run a microdroid.
     *
     * @param protectedVm true if microdroid is intended to run on protected VM.
     * @return returns true if the preconditions are satisfied, false otherwise.
     */
    public boolean supportsMicrodroid(boolean protectedVm) throws Exception {
        CommandResult result = executeShellV2Command("getprop ro.product.cpu.abi");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            return false;
        }
        String abi = result.getStdout().trim();

        if (abi.isEmpty() || (!abi.startsWith("arm64") && !abi.startsWith("x86_64"))) {
            CLog.d("Unsupported ABI: " + abi);
            return false;
        }

        if (protectedVm) {
            // check if device supports protected virtual machines.
            boolean pVMSupported =
                    getBooleanProperty("ro.boot.hypervisor.protected_vm.supported", false);
            if (!pVMSupported) {
                CLog.i("Device does not support protected virtual machines.");
                return false;
            }
        } else {
            // check if device supports non protected virtual machines.
            boolean nonProtectedVMSupported =
                    getBooleanProperty("ro.boot.hypervisor.vm.supported", false);
            if (!nonProtectedVMSupported) {
                CLog.i("Device does not support non protected virtual machines.");
                return false;
            }
        }

        if (!doesFileExist("/apex/com.android.virt")) {
            CLog.i(
                    "com.android.virt APEX was not pre-installed. Command Failed: 'ls"
                            + " /apex/com.android.virt/bin/crosvm'");
            return false;
        }
        return true;
    }

    /**
     * Checks the preconditions to run a microdroid.
     *
     * @return returns true if the preconditions are satisfied, false otherwise.
     */
    public boolean supportsMicrodroid() throws Exception {
        // Micrdroid can run on protected and non-protected VMs
        return supportsMicrodroid(false) || supportsMicrodroid(true);
    }

    /**
     * Forwards contents of a file to log. To be used when testing microdroid, to forward console
     * and log outputs to the host device's log.
     */
    private void forwardFileToLog(String logPath, String tag) {
        try (CloseableTraceScope ignored = new CloseableTraceScope("forward_to_log:" + tag)) {
            String logwrapperCmd =
                    "logwrapper "
                            + "sh "
                            + "-c "
                            + "\"$'tail -f -n +0 "
                            + logPath
                            + " | sed \\'s/^/"
                            + tag
                            + ": /g\\''\""; // add tags in front of lines
            getRunUtil().allowInterrupt(true);
            // Manually execute the adb action to avoid any kind of recovery
            // since it hard to interrupt the forwarding
            final String[] fullCmd = buildAdbShellCommand(logwrapperCmd, false);
            AdbShellAction adbActionV2 =
                    new AdbShellAction(
                            fullCmd,
                            null,
                            null,
                            null,
                            TimeUnit.MINUTES.toMillis(MICRODROID_MAX_LIFETIME_MINUTES));
            adbActionV2.run();
        } catch (Exception e) {
            // Consume
        }
    }

    private boolean isVirtFeatureEnabled(String feature) throws DeviceNotAvailableException {
        String result = executeShellCommand(VIRT_APEX + "bin/vm check-feature-enabled " + feature);
        return result.contains("enabled");
    }

    /**
     * Starts a Microdroid TestDevice.
     *
     * @param builder A {@link MicrodroidBuilder} with required properties to start a microdroid.
     * @return returns a ITestDevice for the microdroid, can return null.
     */
    private ITestDevice startMicrodroid(MicrodroidBuilder builder)
            throws DeviceNotAvailableException {
        IDeviceManager deviceManager = GlobalConfiguration.getDeviceManagerInstance();

        if (!mStartedMicrodroids.isEmpty())
            throw new IllegalStateException(
                    String.format(
                            "Microdroid with cid '%s' already exists in device. Cannot create"
                                    + " another one.",
                            mStartedMicrodroids.values().iterator().next().cid));

        // remove any leftover files under test root
        executeShellV2Command("rm -rf " + TEST_ROOT + "*");

        CommandResult result = executeShellV2Command("mkdir -p " + TEST_ROOT);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new DeviceRuntimeException(
                    "mkdir -p " + TEST_ROOT + " has failed: " + result,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
        for (File localFile : builder.mBootFiles.keySet()) {
            String remoteFileName = builder.mBootFiles.get(localFile);
            pushFile(localFile, TEST_ROOT + remoteFileName);
        }

        // Push the apk file to the test directory
        if (builder.mApkFile != null) {
            pushFile(builder.mApkFile, TEST_ROOT + builder.mApkFile.getName());
            builder.mApkPath = TEST_ROOT + builder.mApkFile.getName();
        } else if (builder.mApkPath == null) {
            // if both apkFile and apkPath is null, we can not start a microdroid device
            throw new IllegalArgumentException(
                    "apkFile and apkPath is both null. Can not start microdroid.");
        }

        // This file is not what we provide. It will be created by the vm tool.
        final String outApkIdsigPath =
                TEST_ROOT
                        + (builder.mApkFile != null ? builder.mApkFile.getName() : "NULL")
                        + ".idsig";
        final String instanceIdFile = TEST_ROOT + INSTANCE_ID_FILE;
        final String instanceImg = TEST_ROOT + INSTANCE_IMG;
        final String consolePath = TEST_ROOT + "console.txt";
        final String logPath = TEST_ROOT + "log.txt";
        final String debugFlag =
                Strings.isNullOrEmpty(builder.mDebugLevel) ? "" : "--debug " + builder.mDebugLevel;
        final String cpuFlag = builder.mNumCpus == null ? "" : "--cpus " + builder.mNumCpus;
        final String cpuAffinityFlag =
                Strings.isNullOrEmpty(builder.mCpuAffinity)
                        ? ""
                        : "--cpu-affinity " + builder.mCpuAffinity;
        final String cpuTopologyFlag =
                Strings.isNullOrEmpty(builder.mCpuTopology)
                        ? ""
                        : "--cpu-topology " + builder.mCpuTopology;
        final String gkiFlag = Strings.isNullOrEmpty(builder.mGki) ? "" : "--gki " + builder.mGki;

        List<String> args =
                new ArrayList<>(
                        Arrays.asList(
                                deviceManager.getAdbPath(),
                                "-s",
                                getSerialNumber(),
                                "shell",
                                VIRT_APEX + "bin/vm",
                                "run-app",
                                "--console " + consolePath,
                                "--log " + logPath,
                                "--mem " + builder.mMemoryMib,
                                debugFlag,
                                cpuFlag,
                                cpuAffinityFlag,
                                cpuTopologyFlag,
                                gkiFlag,
                                builder.mApkPath,
                                outApkIdsigPath,
                                instanceImg,
                                "--config-path",
                                builder.mConfigPath));
        if (isVirtFeatureEnabled("com.android.kvm.LLPVM_CHANGES")) {
            args.add("--instance-id-file");
            args.add(instanceIdFile);
        }
        if (builder.mProtectedVm) {
            args.add("--protected");
        }
        for (String path : builder.mExtraIdsigPaths) {
            args.add("--extra-idsig");
            args.add(path);
        }
        for (String path : builder.mAssignedDevices) {
            args.add("--devices");
            args.add(path);
        }

        // Run the VM
        String cid;
        Process process;
        try {
            PipedInputStream pipe = new PipedInputStream();
            process = getRunUtil().runCmdInBackground(args, new PipedOutputStream(pipe));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(pipe));

            // Retrieve the CID from the vm tool output
            Pattern pattern = Pattern.compile("with CID (\\d+)");
            while ((cid = stdout.readLine()) != null) {
                Matcher matcher = pattern.matcher(cid);
                if (matcher.find()) {
                    cid = matcher.group(1);
                    break;
                }
            }
            if (cid == null) {
                throw new DeviceRuntimeException(
                        "Failed to find the CID of the VM",
                        DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
            }
        } catch (IOException ex) {
            throw new DeviceRuntimeException(
                    "IOException trying to start a VM",
                    ex,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }

        // Redirect log.txt to logd using logwrapper
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(
                () -> {
                    forwardFileToLog(consolePath, "MicrodroidConsole");
                });
        executor.execute(
                () -> {
                    forwardFileToLog(logPath, "MicrodroidLog");
                });

        int vmAdbPort = forwardMicrodroidAdbPort(cid);
        String microdroidSerial = "localhost:" + vmAdbPort;

        DeviceSelectionOptions microSelection = new DeviceSelectionOptions();
        microSelection.setSerial(microdroidSerial);
        microSelection.setBaseDeviceTypeRequested(BaseDeviceType.NATIVE_DEVICE);

        NativeDevice microdroid = (NativeDevice) deviceManager.allocateDevice(microSelection);
        if (microdroid == null) {
            process.destroy();
            try {
                process.waitFor();
                executor.shutdownNow();
                executor.awaitTermination(2L, TimeUnit.MINUTES);
            } catch (InterruptedException ex) {
            }
            throw new DeviceRuntimeException(
                    "Unable to force allocate the microdroid device",
                    InfraErrorIdentifier.RUNNER_ALLOCATION_ERROR);
        }
        // microdroid can be slow to become unavailable after root. (b/259208275)
        microdroid.getOptions().setAdbRootUnavailableTimeout(4 * 1000);
        builder.mTestDeviceOptions.put("enable-device-connection", "true");
        builder.mTestDeviceOptions.put(
                TestDeviceOptions.INSTANCE_TYPE_OPTION, getOptions().getInstanceType().toString());
        microdroid.setTestDeviceOptions(builder.mTestDeviceOptions);
        ((IManagedTestDevice) microdroid).setIDevice(new RemoteAvdIDevice(microdroidSerial));
        adbConnectToMicrodroid(cid, microdroidSerial, vmAdbPort, builder.mAdbConnectTimeoutMs);
        microdroid.setMicrodroidProcess(process);
        try {
            // TODO: Pass the build info
            microdroid.initializeConnection(null, null);
        } catch (DeviceNotAvailableException | TargetSetupError e) {
            CLog.e(e);
        }
        MicrodroidTracker tracker = new MicrodroidTracker();
        tracker.executor = executor;
        tracker.cid = cid;
        mStartedMicrodroids.put(process, tracker);
        return microdroid;
    }

    /** Find an unused port and forward microdroid's adb connection. Returns the port number. */
    private int forwardMicrodroidAdbPort(String cid) {
        IDeviceManager deviceManager = GlobalConfiguration.getDeviceManagerInstance();
        for (int trial = 0; trial < 10; trial++) {
            int vmAdbPort;
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                vmAdbPort = serverSocket.getLocalPort();
            } catch (IOException e) {
                throw new DeviceRuntimeException(
                        "Unable to get an unused port for Microdroid.",
                        e,
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            }
            String from = "tcp:" + vmAdbPort;
            String to = "vsock:" + cid + ":5555";

            CommandResult result =
                    getRunUtil()
                            .runTimedCmd(
                                    10000,
                                    deviceManager.getAdbPath(),
                                    "-s",
                                    getSerialNumber(),
                                    "forward",
                                    from,
                                    to);
            if (result.getStatus() == CommandStatus.SUCCESS) {
                return vmAdbPort;
            }

            if (result.getStderr().contains("Address already in use")) {
                // retry with other ports
                continue;
            } else {
                throw new DeviceRuntimeException(
                        "Unable to forward vsock:" + cid + ":5555: " + result.getStderr(),
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            }
        }
        throw new DeviceRuntimeException(
                "Unable to get an unused port for Microdroid.",
                DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
    }

    /**
     * Establish an adb connection to microdroid by letting Android forward the connection to
     * microdroid. Wait until the connection is established and microdroid is booted.
     */
    private void adbConnectToMicrodroid(
            String cid, String microdroidSerial, int vmAdbPort, long adbConnectTimeoutMs) {
        MicrodroidHelper microdroidHelper = new MicrodroidHelper();
        IDeviceManager deviceManager = GlobalConfiguration.getDeviceManagerInstance();

        long start = System.currentTimeMillis();
        long timeoutMillis = adbConnectTimeoutMs;
        long elapsed = 0;

        final String serial = getSerialNumber();
        final String from = "tcp:" + vmAdbPort;
        final String to = "vsock:" + cid + ":5555";
        getRunUtil()
                .runTimedCmd(10000, deviceManager.getAdbPath(), "-s", serial, "forward", from, to);

        boolean disconnected = true;
        while (disconnected) {
            elapsed = System.currentTimeMillis() - start;
            timeoutMillis -= elapsed;
            start = System.currentTimeMillis();
            CommandResult result =
                    getRunUtil()
                            .runTimedCmd(
                                    timeoutMillis,
                                    deviceManager.getAdbPath(),
                                    "connect",
                                    microdroidSerial);
            if (result.getStatus() != CommandStatus.SUCCESS) {
                throw new DeviceRuntimeException(
                        deviceManager.getAdbPath()
                                + " connect "
                                + microdroidSerial
                                + " has failed: "
                                + result,
                        DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
            }
            disconnected =
                    result.getStdout().trim().equals("failed to connect to " + microdroidSerial);
            if (disconnected) {
                // adb demands us to disconnect if the prior connection was a failure.
                // b/194375443: this somtimes fails, thus 'try*'.
                getRunUtil()
                        .runTimedCmd(
                                10000, deviceManager.getAdbPath(), "disconnect", microdroidSerial);
            }
        }

        elapsed = System.currentTimeMillis() - start;
        timeoutMillis -= elapsed;
        getRunUtil()
                .runTimedCmd(
                        timeoutMillis,
                        deviceManager.getAdbPath(),
                        "-s",
                        microdroidSerial,
                        "wait-for-device");

        boolean dataAvailable = false;
        while (!dataAvailable && timeoutMillis >= 0) {
            elapsed = System.currentTimeMillis() - start;
            timeoutMillis -= elapsed;
            start = System.currentTimeMillis();
            final String checkCmd = "if [ -d /data/local/tmp ]; then echo 1; fi";
            dataAvailable =
                    microdroidHelper.runOnMicrodroid(microdroidSerial, checkCmd).equals("1");
        }
        // Check if it actually booted by reading a sysprop.
        if (!microdroidHelper
                .runOnMicrodroid(microdroidSerial, "getprop", "ro.hardware")
                .equals("microdroid")) {
            throw new DeviceRuntimeException(
                    String.format("Device '%s' was not booted.", microdroidSerial),
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
    }

    /**
     * Shuts down the microdroid device, if one exist.
     *
     * @throws DeviceNotAvailableException
     */
    public void shutdownMicrodroid(@Nonnull ITestDevice microdroidDevice)
            throws DeviceNotAvailableException {
        Process process = ((NativeDevice) microdroidDevice).getMicrodroidProcess();
        if (process == null) {
            throw new IllegalArgumentException("Process is null. TestDevice is not a Microdroid. ");
        }
        if (!mStartedMicrodroids.containsKey(process)) {
            throw new IllegalArgumentException(
                    "Microdroid device was not started in this TestDevice.");
        }

        process.destroy();
        try {
            process.waitFor();
        } catch (InterruptedException ex) {
        }

        // disconnect from microdroid
        getRunUtil()
                .runTimedCmd(
                        10000,
                        GlobalConfiguration.getDeviceManagerInstance().getAdbPath(),
                        "disconnect",
                        microdroidDevice.getSerialNumber());

        GlobalConfiguration.getDeviceManagerInstance()
                .freeDevice(microdroidDevice, FreeDeviceState.AVAILABLE);
        MicrodroidTracker tracker = mStartedMicrodroids.remove(process);
        getRunUtil().allowInterrupt(true);
        try {
            tracker.executor.shutdownNow();
            tracker.executor.awaitTermination(1L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            CLog.e(e);
        }
    }

    // TODO (b/274941025): remove when shell commands using this method are merged in AOSP
    private boolean executeShellV2CommandThatReturnsBooleanSafe(
            String cmdFormat, Object... cmdArgs) {
        try {
            return executeShellV2CommandThatReturnsBoolean(cmdFormat, cmdArgs);
        } catch (Exception e) {
            CLog.e(e);
            return false;
        }
    }

    private boolean executeShellV2CommandThatReturnsBoolean(String cmdFormat, Object... cmdArgs)
            throws DeviceNotAvailableException {
        String cmd = String.format(cmdFormat, cmdArgs);
        CommandResult res = executeShellV2Command(cmd);
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            throw new DeviceRuntimeException(
                    "Command  '" + cmd + "' failed: " + res,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
        String output = res.getStdout();
        switch (output.trim().toLowerCase()) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new DeviceRuntimeException(
                        "Non-boolean result for '" + cmd + "': " + output,
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    /** A builder used to create a Microdroid TestDevice. */
    public static class MicrodroidBuilder {
        private File mApkFile;
        private String mApkPath;
        private String mConfigPath;
        private String mDebugLevel;
        private int mMemoryMib;
        private Integer mNumCpus;
        private String mCpuAffinity;
        private String mCpuTopology;
        private List<String> mExtraIdsigPaths;
        private boolean mProtectedVm;
        private Map<String, String> mTestDeviceOptions;
        private Map<File, String> mBootFiles;
        private long mAdbConnectTimeoutMs;
        private List<String> mAssignedDevices;
        private String mGki;

        /** Creates a builder for the given APK/apkPath and the payload config file in APK. */
        private MicrodroidBuilder(File apkFile, String apkPath, @Nonnull String configPath) {
            mApkFile = apkFile;
            mApkPath = apkPath;
            mConfigPath = configPath;
            mDebugLevel = null;
            mMemoryMib = 0;
            mNumCpus = null;
            mCpuAffinity = null;
            mExtraIdsigPaths = new ArrayList<>();
            mProtectedVm = false; // Vm is unprotected by default.
            mTestDeviceOptions = new LinkedHashMap<>();
            mBootFiles = new LinkedHashMap<>();
            mAdbConnectTimeoutMs = MICRODROID_DEFAULT_ADB_CONNECT_TIMEOUT_MINUTES * 60 * 1000;
            mAssignedDevices = new ArrayList<>();
        }

        /** Creates a Microdroid builder for the given APK and the payload config file in APK. */
        public static MicrodroidBuilder fromFile(
                @Nonnull File apkFile, @Nonnull String configPath) {
            return new MicrodroidBuilder(apkFile, null, configPath);
        }

        /**
         * Creates a Microdroid builder for the given apkPath and the payload config file in APK.
         */
        public static MicrodroidBuilder fromDevicePath(
                @Nonnull String apkPath, @Nonnull String configPath) {
            return new MicrodroidBuilder(null, apkPath, configPath);
        }

        /**
         * Sets the debug level.
         *
         * <p>Supported values: "none" and "full". Android T also supports "app_only".
         */
        public MicrodroidBuilder debugLevel(String debugLevel) {
            mDebugLevel = debugLevel;
            return this;
        }

        /**
         * Sets the amount of RAM to give the VM. If this is zero or negative then the default will
         * be used.
         */
        public MicrodroidBuilder memoryMib(int memoryMib) {
            mMemoryMib = memoryMib;
            return this;
        }

        /**
         * Sets the number of vCPUs in the VM. Defaults to 1.
         *
         * <p>Only supported in Android T.
         */
        public MicrodroidBuilder numCpus(int num) {
            mNumCpus = num;
            return this;
        }

        /**
         * Sets on which host CPUs the vCPUs can run. The format is a comma-separated list of CPUs
         * or CPU ranges to run vCPUs on. e.g. "0,1-3,5" to choose host CPUs 0, 1, 2, 3, and 5. Or
         * this can be a colon-separated list of assignments of vCPU to host CPU assignments. e.g.
         * "0=0:1=1:2=2" to map vCPU 0 to host CPU 0, and so on.
         *
         * <p>Only supported in Android T.
         */
        public MicrodroidBuilder cpuAffinity(String affinity) {
            mCpuAffinity = affinity;
            return this;
        }

        /** Sets the CPU topology configuration. Supported values: "one_cpu" and "match_host". */
        public MicrodroidBuilder cpuTopology(String cpuTopology) {
            mCpuTopology = cpuTopology;
            return this;
        }

        /** Sets whether the VM will be protected or not. */
        public MicrodroidBuilder protectedVm(boolean isProtectedVm) {
            mProtectedVm = isProtectedVm;
            return this;
        }

        /** Adds extra idsig file to the list. */
        public MicrodroidBuilder addExtraIdsigPath(String extraIdsigPath) {
            if (!Strings.isNullOrEmpty(extraIdsigPath)) {
                mExtraIdsigPaths.add(extraIdsigPath);
            }
            return this;
        }

        /**
         * Sets a {@link TestDeviceOptions} for the microdroid TestDevice.
         *
         * @param optionName The name of the TestDeviceOption to set
         * @param valueText The value
         * @return the microdroid builder.
         */
        public MicrodroidBuilder addTestDeviceOption(String optionName, String valueText) {
            mTestDeviceOptions.put(optionName, valueText);
            return this;
        }

        /**
         * Adds a file for booting to be pushed to {@link #TEST_ROOT}.
         *
         * <p>Use this method if an file is required for booting microdroid. Otherwise use {@link
         * TestDevice#pushFile}.
         *
         * @param localFile The local file on the host
         * @param remoteFileName The remote file name on the device
         * @return the microdroid builder.
         */
        public MicrodroidBuilder addBootFile(File localFile, String remoteFileName) {
            mBootFiles.put(localFile, remoteFileName);
            return this;
        }

        /**
         * Adds a device to assign to microdroid.
         *
         * @param sysfsNode The path to the sysfs node to assign
         * @return the microdroid builder.
         */
        public MicrodroidBuilder addAssignableDevice(String sysfsNode) {
            mAssignedDevices.add(sysfsNode);
            return this;
        }

        /**
         * Sets the timeout for adb connect to microdroid TestDevice in millis.
         *
         * @param timeoutMs The timeout in millis
         */
        public MicrodroidBuilder setAdbConnectTimeoutMs(long timeoutMs) {
            mAdbConnectTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Uses GKI kernel instead of microdroid kernel
         *
         * @param version The GKI version to use
         */
        public MicrodroidBuilder gki(String version) {
            mGki = version;
            return this;
        }

        /** Starts a Micrdroid TestDevice on the given TestDevice. */
        public ITestDevice build(@Nonnull TestDevice device) throws DeviceNotAvailableException {
            if (mNumCpus != null) {
                if (device.getApiLevel() != 33) {
                    throw new IllegalStateException(
                            "Setting number of CPUs only supported with API level 33");
                }
                if (mNumCpus < 1) {
                    throw new IllegalArgumentException("Number of vCPUs can not be less than 1.");
                }
            }

            if (!Strings.isNullOrEmpty(mCpuTopology)) {
                device.checkApiLevelAgainstNextRelease("vm-cpu-topology", 34);
            }

            if (mCpuAffinity != null) {
                if (device.getApiLevel() != 33) {
                    throw new IllegalStateException(
                            "Setting CPU affinity only supported with API level 33");
                }
                if (!Pattern.matches("[\\d]+(-[\\d]+)?(,[\\d]+(-[\\d]+)?)*", mCpuAffinity)
                        && !Pattern.matches("[\\d]+=[\\d]+(:[\\d]+=[\\d]+)*", mCpuAffinity)) {
                    throw new IllegalArgumentException(
                            "CPU affinity [" + mCpuAffinity + "]" + " is invalid");
                }
            }

            return device.startMicrodroid(this);
        }
    }

    private String handleInstallationError(InstallException e) {
        String message = e.getMessage();
        if (message == null) {
            message =
                    String.format(
                            "InstallException during package installation. " + "cause: %s",
                            StreamUtil.getStackTrace(e));
        }
        return message;
    }

    private String handleInstallReceiver(InstallReceiver receiver, File packageFile) {
        if (receiver.isSuccessfullyCompleted()) {
            return null;
        }
        if (receiver.getErrorMessage() == null) {
            return String.format("Installation of %s timed out", packageFile.getAbsolutePath());
        }
        String error = receiver.getErrorMessage();
        if (error.contains("cmd: Failure calling service package")
                || error.contains("Can't find service: package")) {
            String message =
                    String.format(
                            "Failed to install '%s'. Device might have"
                                    + " crashed, it returned: %s",
                            packageFile.getName(), error);
            throw new DeviceRuntimeException(message, DeviceErrorIdentifier.DEVICE_CRASHED);
        }
        return error;
    }
}
