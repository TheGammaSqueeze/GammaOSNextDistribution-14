/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.BundletoolUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * A {@link TargetPreparer} that attempts to install mainline modules to device
 * and verify install success.
 */
@OptionClass(alias = "mainline-module-installer")
public class InstallApexModuleTargetPreparer extends SuiteApkInstaller {

    private static final String APEX_DATA_DIR = "/data/apex/active/";
    private static final String STAGING_DATA_DIR = "/data/app-staging/";
    private static final String SESSION_DATA_DIR = "/data/apex/sessions/";
    private static final String MODULE_PUSH_REMOTE_PATH = "/data/local/tmp/";
    private static final String TRAIN_WITH_APEX_INSTALL_OPTION = "install-multi-package";
    private static final String ENABLE_ROLLBACK_INSTALL_OPTION = "--enable-rollback";
    private static final String STAGED_INSTALL_OPTION = "--staged";
    private static final String ACTIVATED_APEX_SOURCEDIR_PREFIX = "data";
    private static final int R_SDK_INT = 30;
    // Pattern used to identify the package names from adb shell pm path.
    private static final Pattern PACKAGE_REGEX = Pattern.compile("package:(.*)");
    private static final String MODULE_VERSION_PROP_SUFFIX = "_version_used";
    protected static final String APEX_SUFFIX = ".apex";
    protected static final String APK_SUFFIX = ".apk";
    protected static final String SPLIT_APKS_SUFFIX = ".apks";
    protected static final String PARENT_SESSION_CREATION_CMD = "pm install-create --multi-package";
    protected static final String CHILD_SESSION_CREATION_CMD = "pm install-create";
    protected static final String APEX_OPTION = "--apex";
    protected static final String APK_ZIP_OPTION = "--apks-zip";
    // The dump logic in {@link com.android.server.pm.ComputerEngine#generateApexPackageInfo} is
    // invalid.
    private static final ImmutableList<String> PACKAGES_WITH_INVALID_DUMP_INFO =
            ImmutableList.of("com.google.mainline.primary.libs");
    private static final String STAGED_READY_TIMEOUT_OPTION = "--staged-ready-timeout";
    private static final String TIMEOUT_MILLIS_OPTION = "--timeout-millis=";

    private List<ApexInfo> mTestApexInfoList = new ArrayList<>();
    private List<String> mApexModulesToUninstall = new ArrayList<>();
    private List<String> mApkModulesToUninstall = new ArrayList<>();
    private Set<String> mMainlineModuleInfos = new HashSet<>();
    private Set<String> mApkToInstall = new LinkedHashSet<>();
    private List<String> mApkInstalled = new ArrayList<>();
    private List<String> mSplitsInstallArgs = new ArrayList<>();
    private List<File> mSplitsDir = new ArrayList<>();
    private BundletoolUtil mBundletoolUtil;
    private String mDeviceSpecFilePath = "";
    private boolean mOptimizeMainlineTest = false;

    @Option(name = "bundletool-file-name", description = "The file name of the bundletool jar.")
    private String mBundletoolFilename;

    @Option(name = "train-path", description = "The absolute path of the train folder.")
    protected File mTrainFolderPath;

    @Option(
            name = "apex-staging-wait-time",
            description = "The time in ms to wait for apex staged session ready.",
            isTimeVal = true)
    private long mApexStagingWaitTime = 0;

    @Option(
            name = "apex-rollback-wait-time",
            description = "The time in ms to wait for apex rollback success.",
            isTimeVal = true)
    private long mApexRollbackWaitTime = 1 * 60 * 1000;

    @Option(
            name="extra-booting-wait-time",
            description = "The extra time in ms to wait for device ready.",
            isTimeVal = true)
    private long mExtraBootingWaitTime = 0;

    @Option(
            name = "ignore-if-module-not-preloaded",
            description =
                    "Skip installing the module(s) when the module(s) that are not "
                            + "preloaded on device. Otherwise an exception will be thrown.")
    private boolean mIgnoreIfNotPreloaded = false;

    @Option(
            name = "skip-apex-teardown",
            description =
                    "Skip teardown if all files to be installed are apex files. "
                            + "Currently, this option is only used for Test Mapping use case.")
    private boolean mSkipApexTearDown = false;

    @Option(
            name = "enable-rollback",
            description = "Add the '--enable-rollback' flag when installing modules.")
    private boolean mEnableRollback = true;

    @Option(
            name = "apks-zip-file-name",
            description = "Install modules from apk zip file. Accepts a single file.")
    private String mApksZipFileName = null;

    @Option(
            name = "staged-ready-timeout-ms",
            description =
                    "Time option in millis to wait for session stage. It will be passed to"
                            + " --staged-ready-timeout for adb install-multi-package and"
                            + " --timeout-millis for bundletool install-apks.")
    private long mStagedReadyTimeoutMs = 0;

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        setTestInformation(testInfo);
        ITestDevice device = testInfo.getDevice();

        if (mTrainFolderPath != null) {
            addApksToTestFiles();
        }

        List<File> moduleFileNames = getTestsFileName();
        if (moduleFileNames.isEmpty() && Strings.isNullOrEmpty(mApksZipFileName)) {
            CLog.i("No apk/apex module file to install. Skipping.");
            return;
        }

        if (!mSkipApexTearDown) {
            // Cleanup the device if skip-apex-teardown isn't set. It will always run with the
            // target preparer.
            cleanUpStagedAndActiveSession(device);
        } else {
            mOptimizeMainlineTest = true;
        }

        Set<ApexInfo> activatedApexes = device.getActiveApexes();

        CLog.i("Activated apex packages list before module/train installation:");
        for (ApexInfo info : activatedApexes) {
            CLog.i("Activated apex: %s", info.toString());
        }

        if (!Strings.isNullOrEmpty(mApksZipFileName)) {
            CLog.i("Installing modules using apks zip %s", mApksZipFileName);
            installModulesFromZipUsingBundletool(testInfo, mApksZipFileName);
            activateStagedInstall(device);
            CLog.i("Required modules are installed from zip");
            return;
        }

        List<File> testAppFiles = getModulesToInstall(testInfo);
        if (testAppFiles.isEmpty()) {
            CLog.i("No modules are preloaded on the device, so no modules will be installed.");
            return;
        }

        if (mOptimizeMainlineTest) {
            CLog.i("Optimizing modules that are already activated in the previous test.");
            testAppFiles = optimizeModuleInstallation(activatedApexes, testAppFiles, device);
            if (testAppFiles.isEmpty()) {
                if (!mApexModulesToUninstall.isEmpty() || !mApkModulesToUninstall.isEmpty()) {
                    activateStagedInstall(device);
                }
                // If both the list of files to be installed and uninstalled are empty, that means
                // the mainline modules are the same as the previous ones.
                CLog.i("All required modules are installed");
                return;
            }
        }

        // Store the version info of each module for updatiing mainline invocation property in ATI.
        // Supported for non test mapping runs which has invocation level module installation.
        if (!mOptimizeMainlineTest && !containsApks(testAppFiles)) {
            for (File f : testAppFiles) {
                ApexInfo apexInfo = retrieveApexInfo(f, testInfo.getDevice().getDeviceDescriptor());
                testInfo.getBuildInfo()
                        .addBuildAttribute(
                                apexInfo.name + MODULE_VERSION_PROP_SUFFIX,
                                String.valueOf(apexInfo.versionCode));
            }
        }

        if (containsApks(testAppFiles)) {
            installUsingBundleTool(testInfo, testAppFiles);
        } else {
            Map<File, String> appFilesAndPackages = resolveApkFiles(testInfo, testAppFiles);
            CLog.i("Staging install for " + appFilesAndPackages);
            installer(testInfo, appFilesAndPackages);
        }

        activateStagedInstall(device);
        if (!mTestApexInfoList.isEmpty()) {
            checkApexActivation(device);
        }
        CLog.i("Train activation succeed.");
    }

    /**
     * Boot the device to activate the updated apex modules.
     *
     * @param device under test.
     * @throws DeviceNotAvailableException if reboot fails.
     */
    private void activateStagedInstall(ITestDevice device) throws DeviceNotAvailableException {
        if (mApexStagingWaitTime > 0 && device.getApiLevel() == 29) {
            RunUtil.getDefault().sleep(mApexStagingWaitTime);
        }
        device.reboot();
        // Some devices need extra waiting time after reboot to get fully ready.
        if (mExtraBootingWaitTime > 0) {
            RunUtil.getDefault().sleep(mExtraBootingWaitTime);
            device.waitForDeviceAvailable();
            // Do a second post-boot setup (by default it is just adb root)
            // in case its first execution inside reboot() was not at a right time.
            device.postBootSetup();
        }
    }

    /**
     * Check if all apexes are activated.
     *
     * @param device under test.
     * @throws TargetSetupError if activation failed.
     */
    protected void checkApexActivation(ITestDevice device)
            throws DeviceNotAvailableException, TargetSetupError {
        Set<ApexInfo> activatedApexes;
        activatedApexes = device.getActiveApexes();

        if (activatedApexes.isEmpty()) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to retrieve activated apex on device %s. Empty set returned.",
                            device.getSerialNumber()),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        } else {
            CLog.i("Activated apex packages list after module/train installation:");
            for (ApexInfo info : activatedApexes) {
                CLog.i("Activated apex: %s", info.toString());
            }
        }

        List<ApexInfo> failToActivateApex = getModulesFailToActivate(activatedApexes);

        if (!failToActivateApex.isEmpty()) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to activate %s on device %s.",
                            listApexInfo(failToActivateApex).toString(), device.getSerialNumber()),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.FAIL_ACTIVATE_APEX);
        }
        CLog.i("Train activation succeed.");
    }

    /**
     * Optimization for modules to reuse those who are already activated in the previous test.
     *
     * @param activatedApex The set of the active apexes on device
     * @param testFiles List<File> of the modules that will be installed on the device.
     * @param device the {@link ITestDevice}
     * @return A List<File> of the modules that will be installed on the device.
     */
    private List<File> optimizeModuleInstallation(Set<ApexInfo> activatedApex, List<File> testFiles,
            ITestDevice device) throws DeviceNotAvailableException, TargetSetupError {
        // Get apexes that got activated in the previous test invocation.
        Set<String> apexInData = getApexInData(activatedApex);

        // Get the apk files that are already installed on the device.
        Set<String> apkModuleInData = getApkModuleInData(activatedApex, device);

        // Get the apex files that are not used by the current test and will be uninstalled.
        mApexModulesToUninstall.addAll(getModulesToUninstall(apexInData, testFiles, device));

        // Get the apk files that are not used by the current test and will be uninstalled.
        mApkModulesToUninstall.addAll(getModulesToUninstall(apkModuleInData, testFiles, device));

        for (String m : mApexModulesToUninstall) {
            CLog.i("Uninstalling apex module: %s", m);
            uninstallPackage(device, m);
        }

        for (String packageName : mApkModulesToUninstall) {
            CLog.i("Uninstalling apk module: %s", packageName);
            uninstallPackage(device, packageName);
        }

        return testFiles;
    }

    /**
     * Get a set of modules that will be uninstalled.
     *
     * @param modulesInData A Set<String> of modules that are installed on the /data directory.
     * @param testFiles A List<File> of modules that will be installed on the device.
     * @param device the {@link ITestDevice}
     * @return A Set<String> of modules that will be uninstalled on the device.
     */
    Set<String> getModulesToUninstall(Set<String> modulesInData,
        List<File> testFiles, ITestDevice device) throws TargetSetupError {
        Set<String> unInstallModules = new HashSet<>(modulesInData);
        List<File> filesToSkipInstall = new ArrayList<>();
        for (File testFile : testFiles) {
            String packageName = parsePackageName(testFile);
            for (String moduleInData : modulesInData) {
                if (moduleInData.equals(packageName)) {
                    unInstallModules.remove(moduleInData);
                    filesToSkipInstall.add(testFile);
                }
            }
        }
        // Update the modules to be installed based on what will not be installed.
        testFiles.removeAll(filesToSkipInstall);
        return unInstallModules;
    }

    /**
     * Return a set of apex files that are already installed on the /data directory.
     */
    Set<String> getApexInData(Set<ApexInfo> activatedApexes) {
        Set<String> apexInData = new HashSet<>();
        for (ApexInfo apex : activatedApexes) {
            if (apex.sourceDir.startsWith(APEX_DATA_DIR, 0) ||
                apex.sourceDir.startsWith(STAGING_DATA_DIR, 0) ||
                apex.sourceDir.startsWith(SESSION_DATA_DIR, 0)) {
                apexInData.add(apex.name);
            }
        }
        return apexInData;
    }

    /**
     * Return a set of apk modules by excluding the apex modules from the given mainline modules.
     */
    Set<String> getApkModules(Set<String> moduleInfos, Set<ApexInfo> activatedApexes) {
        Set<String> apexModules = new HashSet<>();
        for (ApexInfo apex : activatedApexes) {
            apexModules.add(apex.name);
        }
        moduleInfos.removeAll(apexModules);
        return moduleInfos;
    }

    /**
     * Return a set of apk modules that are already installed on the /data directory.
     */
    Set<String> getApkModuleInData(Set<ApexInfo> activatedApexes, ITestDevice device)
        throws DeviceNotAvailableException {
        Set<String> apkModuleInData = new HashSet<>();
        try {
            // Get all mainline modules based on the MODULE METADATA on the device.
            mMainlineModuleInfos = device.getMainlineModuleInfo();
        } catch (UnsupportedOperationException usoe) {
            CLog.e("Failed to query modules based on the MODULE_METADATA on the device - "
                    + "unsupported operation, returning an empty list of apk modules.");
            return apkModuleInData;
        }
        // Get the apk modules based on mainline module info and the activated apex modules.
        Set<String> apkModules = getApkModules(mMainlineModuleInfos, activatedApexes);
        for (String apkModule : apkModules) {
            String output = device.executeShellCommand(String.format("pm path %s", apkModule));
            if (output != null) {
                Matcher m = PACKAGE_REGEX.matcher(output);
                while (m.find()) {
                    String packageName = m.group(1);
                    CLog.i("Activates apk module: %s, path: %s", apkModule, packageName);
                    if (packageName.startsWith("/data/app/")) {
                        apkModuleInData.add(apkModule);
                    }
                }
            }
        }
        return apkModuleInData;
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        for (File dir : mSplitsDir) {
            FileUtil.recursiveDelete(dir);
        }
        mSplitsDir.clear();
        if (mOptimizeMainlineTest) {
            CLog.d("Skipping tearDown as the installed modules may be used for the next test.");
            return;
        }
        ITestDevice device = testInfo.getDevice();
        if (e instanceof DeviceNotAvailableException) {
            CLog.e("Device %s is not available. Teardown() skipped.", device.getSerialNumber());
            return;
        } else {
            if (mTestApexInfoList.isEmpty() && getApkInstalled().isEmpty()) {
                super.tearDown(testInfo, e);
            } else {
                if (mTestApexInfoList.isEmpty()) {
                    for (String apkPkgName : getApkInstalled()) {
                        uninstallPackage(device, apkPkgName);
                    }
                } else {
                    for (ApexInfo apex : mTestApexInfoList) {
                        String output =
                                device.executeShellCommand(
                                        String.format("pm rollback-app %s", apex.name));
                        // Rolling back one newly installed module will rollback all other newly
                        // installed modules.
                        if (output.contains("Success")) {
                            break;
                        } else {
                            throw new HarnessRuntimeException(
                                    String.format(
                                            "Failed to rollback %s, Output: %s", apex.name, output),
                                    DeviceErrorIdentifier.APEX_ROLLBACK_FAILED);
                        }
                    }
                    CLog.i("Wait for rollback fully done.");
                    RunUtil.getDefault().sleep(mApexRollbackWaitTime);
                    CLog.i("Clean up staged and active session for mainline test mapping.");
                    cleanUpStagedAndActiveSession(device);
                }
            }
        }
    }

    private File resolveFilePath(TestInformation testInfo, String path, String notFoundMessage)
            throws TargetSetupError {
        File resolvedFile;
        File f = new File(path);

        if (!f.isAbsolute()) {
            resolvedFile = getLocalPathForFilename(testInfo, path);
        } else {
            resolvedFile = f;
        }
        if (resolvedFile == null) {
            throw new TargetSetupError(
                    String.format(notFoundMessage),
                    testInfo.getDevice().getDeviceDescriptor(),
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }
        return resolvedFile;
    }

    /**
     * Initializes the bundletool util for this class.
     *
     * @param testInfo the {@link TestInformation} for the invocation.
     * @throws TargetSetupError if bundletool cannot be found.
     */
    protected void initBundletoolUtil(TestInformation testInfo) throws TargetSetupError {
        if (mBundletoolUtil != null) {
            return;
        }

        mBundletoolUtil =
                new BundletoolUtil(
                        resolveFilePath(
                                testInfo,
                                getBundletoolFileName(),
                                String.format(
                                        "Failed to find bundletool jar %s.",
                                        getBundletoolFileName())));
    }

    /**
     * Initializes the path to the device spec file.
     *
     * @param device the {@link ITestDevice} to install the train.
     * @throws TargetSetupError if fails to generate the device spec file.
     */
    private void initDeviceSpecFilePath(ITestDevice device) throws TargetSetupError {
        if (!"".equals(mDeviceSpecFilePath)) {
            return;
        }
        try {
            // Sets to be the initial value (which is "") if failed to generateDeviceSpecFile.
            mDeviceSpecFilePath =
                    Strings.nullToEmpty(getBundletoolUtil().generateDeviceSpecFile(device));
        } catch (IOException e) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to generate device spec file on %s.", device.getSerialNumber()),
                    e,
                    device.getDeviceDescriptor());
        }
    }

    /**
     * Extracts and returns splits for the specified apks.
     *
     * @param testInfo the {@link TestInformation}
     * @param moduleFile The module file to extract the splits from.
     * @return a File[] containing the splits.
     * @throws TargetSetupError if bundletool cannot be found or device spec file fails to generate.
     */
    protected List<File> getSplitsForApks(TestInformation testInfo, File moduleFile)
            throws TargetSetupError {
        initBundletoolUtil(testInfo);
        initDeviceSpecFilePath(testInfo.getDevice());

        File splitsDir =
                getBundletoolUtil()
                        .extractSplitsFromApks(
                                moduleFile,
                                mDeviceSpecFilePath,
                                testInfo.getDevice(),
                                testInfo.getBuildInfo());
        if (splitsDir == null || splitsDir.listFiles() == null) {
            return null;
        }
        mSplitsDir.add(splitsDir);
        return Arrays.asList(splitsDir.listFiles());
    }

    /**
     * Gets the modules that should be installed on the train, based on the modules preloaded on the
     * device. Modules that are not preloaded will not be installed.
     *
     * @param testInfo the {@link TestInformation}
     * @return List<String> of the modules that should be installed on the device.
     * @throws DeviceNotAvailableException when device is not available.
     * @throws TargetSetupError when mandatory modules are not installed, or module cannot be
     *     installed.
     */
    public List<File> getModulesToInstall(TestInformation testInfo)
            throws DeviceNotAvailableException, TargetSetupError {
        // Get all preloaded modules for the device.
        ITestDevice device = testInfo.getDevice();
        Set<String> installedPackages = new HashSet<>(device.getInstalledPackageNames());
        Set<ApexInfo> installedApexes = new HashSet<>(device.getActiveApexes());
        for (ApexInfo installedApex : installedApexes) {
            installedPackages.add(installedApex.name);
        }
        Set<String> trainInstalledPackages = new HashSet<>();
        List<File> moduleFileNames = getTestsFileName();
        List<File> moduleNamesToInstall = new ArrayList<>();
        for (File moduleFileName : moduleFileNames) {
            // getLocalPathForFilename throws if apk not found
            File moduleFile = moduleFileName;
            if (!moduleFile.isAbsolute()) {
                moduleFile = getLocalPathForFilename(testInfo, moduleFileName.getName());
            }
            String modulePackageName = "";
            if (moduleFile.getName().endsWith(SPLIT_APKS_SUFFIX)) {
                List<File> splits = getSplitsForApks(testInfo, moduleFile);
                if (splits == null) {
                    // Bundletool failed to extract splits.
                    CLog.w(
                            "Apks %s is not available on device %s and will not be installed.",
                            moduleFileName, mDeviceSpecFilePath);
                    continue;
                }
                modulePackageName = parsePackageName(splits.get(0));
            } else {
                modulePackageName = parsePackageName(moduleFile);
            }
            if (installedPackages.contains(modulePackageName)) {
                CLog.i("Found preloaded module for %s.", modulePackageName);
                moduleNamesToInstall.add(moduleFile);
                if (trainInstalledPackages.contains(modulePackageName)) {
                    throw new TargetSetupError(
                            String.format(
                                    "Mainline module %s is listed for install more than once.",
                                    modulePackageName),
                            InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
                }
                trainInstalledPackages.add(modulePackageName);
            } else {
                if (!mIgnoreIfNotPreloaded) {
                    CLog.i(
                            "The following modules are preloaded on the device %s",
                            installedPackages);
                    throw new TargetSetupError(
                            String.format(
                                    "Mainline module %s is not preloaded on the device "
                                            + "but is in the input lists.",
                                    modulePackageName),
                            device.getDeviceDescriptor(),
                            DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
                }
                CLog.i(
                        "The module package %s is not preloaded on the device but is included in "
                                + "the train.",
                        modulePackageName);
            }
        }
        // Log the modules that are not included in the train.
        Set<String> nonTrainPackages = new HashSet<>(installedPackages);
        nonTrainPackages.removeAll(trainInstalledPackages);
        if (!nonTrainPackages.isEmpty()) {
            CLog.i(
                    "The following modules are preloaded on the device, but not included in the "
                            + "train: %s",
                    nonTrainPackages);
        }
        return moduleNamesToInstall;
    }

    // TODO(b/124461631): Remove after ddmlib supports install-multi-package.
    @Override
    protected void installer(TestInformation testInfo, Map<File, String> testAppFileNames)
            throws TargetSetupError, DeviceNotAvailableException {
        if (containsApex(testAppFileNames.keySet())) {
            mTestApexInfoList = collectApexInfoFromApexModules(testAppFileNames, testInfo);
        }
        installTrain(testInfo, new ArrayList<>(testAppFileNames.keySet()));
    }

    /**
     * Attempts to install a mainline train containing apex on the device.
     *
     * @param testInfo the {@link TestInformation}
     * @param moduleFilenames List of String. The list of filenames of the mainline modules to be
     *     installed.
     */
    protected void installTrain(
            TestInformation testInfo, List<File> moduleFilenames)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        List<String> apkPackageNames = new ArrayList<>();

        //This is a short term fix for atomic install issue on Q. We will either remove the fix
        //after Q support is deprecated or a long term fix is provided(b/257675597).
        if (device.getApiLevel() == 29) {
            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add(TRAIN_WITH_APEX_INSTALL_OPTION);
            trainInstallCmd.add(STAGED_INSTALL_OPTION);
            if (mEnableRollback) {
                trainInstallCmd.add(ENABLE_ROLLBACK_INSTALL_OPTION);
            }
            addStagedReadyTimeoutForAdb(trainInstallCmd);
            for (File moduleFile : moduleFilenames) {
                trainInstallCmd.add(moduleFile.getAbsolutePath());
                if (moduleFile.getName().endsWith(APK_SUFFIX)) {
                    String packageName = parsePackageName(moduleFile);
                    apkPackageNames.add(packageName);
                }
            }
            String log = device.executeAdbCommand(trainInstallCmd.toArray(new String[0]));
            if (log == null) {
                throw new TargetSetupError(
                    trainInstallCmd.toString(),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.APK_INSTALLATION_FAILED);
            }
            if (mApexStagingWaitTime > 0) {
                RunUtil.getDefault().sleep(mApexStagingWaitTime);
            }
            if (log.contains("Success")) {
                CLog.d(
                    "Train is staged successfully. Cmd: %s, Output: %s.",
                    trainInstallCmd.toString(), log);
            } else {
                throw new TargetSetupError(
                    String.format(
                        "Failed to install %s on %s. Error log: '%s'",
                        moduleFilenames.toString(), device.getSerialNumber(), log),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.APK_INSTALLATION_FAILED);
            }
            mApkInstalled.addAll(apkPackageNames);
            return;
        }

        for (File moduleFile : moduleFilenames) {
            if (!device.pushFile(moduleFile, MODULE_PUSH_REMOTE_PATH + moduleFile.getName())) {
                throw new TargetSetupError(
                        String.format(
                                "Failed to push local '%s' to remote '%s'",
                                moduleFile.getAbsolutePath(), MODULE_PUSH_REMOTE_PATH + moduleFile.getName()),
                        device.getDeviceDescriptor(),
                        DeviceErrorIdentifier.FAIL_PUSH_FILE);
            } else {
                CLog.d(
                        "%s pushed successfully to %s.",
                        moduleFile.getName(), MODULE_PUSH_REMOTE_PATH + moduleFile.getName());
            }
            if (moduleFile.getName().endsWith(APK_SUFFIX)) {
                String packageName = parsePackageName(moduleFile);
                apkPackageNames.add(packageName);
            }
        }

        String cmd = PARENT_SESSION_CREATION_CMD + " " + STAGED_INSTALL_OPTION;
        if (mEnableRollback) {
            cmd += " " + ENABLE_ROLLBACK_INSTALL_OPTION;
        }
        CommandResult res = device.executeShellV2Command(cmd + " | egrep -o -e '[0-9]+'");
        String parentSessionId;
        if (res.getStatus() == CommandStatus.SUCCESS) {
            parentSessionId = res.getStdout();
            CLog.d("Parent session %s created successfully. ", parentSessionId);
        } else {
            throw new TargetSetupError(
                    String.format(
                            "Failed to create parent session. Error: %s, Stdout: %s",
                            res.getStderr(), res.getStdout()),
                    device.getDeviceDescriptor());
        }

        for (File moduleFile : moduleFilenames) {
            String childSessionId = null;
            if (moduleFile.getName().endsWith(APEX_SUFFIX)) {
                if (mEnableRollback) {
                    res =
                            device.executeShellV2Command(
                                    String.format(
                                            "%s %s %s %s | egrep -o -e '[0-9]+'",
                                            CHILD_SESSION_CREATION_CMD,
                                            APEX_OPTION,
                                            STAGED_INSTALL_OPTION,
                                            ENABLE_ROLLBACK_INSTALL_OPTION));
                } else {
                    res =
                            device.executeShellV2Command(
                                    String.format(
                                            "%s %s %s | egrep -o -e '[0-9]+'",
                                            CHILD_SESSION_CREATION_CMD,
                                            APEX_OPTION,
                                            STAGED_INSTALL_OPTION));
                }
            } else {
                if (mEnableRollback) {
                    res =
                            device.executeShellV2Command(
                                    String.format(
                                            "%s %s %s | egrep -o -e '[0-9]+'",
                                            CHILD_SESSION_CREATION_CMD,
                                            STAGED_INSTALL_OPTION,
                                            ENABLE_ROLLBACK_INSTALL_OPTION));
                } else {
                    res =
                            device.executeShellV2Command(
                                    String.format(
                                            "%s %s | egrep -o -e '[0-9]+'",
                                            CHILD_SESSION_CREATION_CMD, STAGED_INSTALL_OPTION));
                }
            }
            if (res.getStatus() == CommandStatus.SUCCESS) {
                childSessionId = res.getStdout();
                CLog.d(
                        "Child session %s created successfully for %s. ",
                        childSessionId, moduleFile.getName());
            } else {
                throw new TargetSetupError(
                        String.format(
                                "Failed to create child session for %s. Error: %s, Stdout: %s",
                            moduleFile.getName(), res.getStderr(), res.getStdout()),
                        device.getDeviceDescriptor());
            }
            res =
                    device.executeShellV2Command(
                            String.format(
                                    "pm install-write -S %d %s %s %s",
                                    moduleFile.length(),
                                    childSessionId,
                                    parsePackageName(moduleFile),
                                    MODULE_PUSH_REMOTE_PATH + moduleFile.getName()));
            if (res.getStatus() == CommandStatus.SUCCESS) {
                CLog.d(
                        "Successfully wrote %s to session %s. ",
                        moduleFile.getName(), childSessionId);
            } else {
                throw new TargetSetupError(
                        String.format("Failed to write %s to session %s. Error: %s, Stdout: %s",
                            moduleFile.getName(), childSessionId, res.getStderr(), res.getStdout()),
                    device.getDeviceDescriptor());
            }
            res =
                    device.executeShellV2Command(
                            String.format(
                                    "pm install-add-session "
                                            + parentSessionId
                                            + " "
                                            + childSessionId));
            if (res.getStatus() != CommandStatus.SUCCESS) {
                throw new TargetSetupError(
                        String.format(
                                "Failed to add child session %s to parent session %s. Error: %s,"
                                        + " Stdout: %s",
                                childSessionId, parentSessionId, res.getStderr(), res.getStdout()),
                        device.getDeviceDescriptor());
            }
        }
        res = device.executeShellV2Command("pm install-commit " + parentSessionId);

        if (res.getStatus() == CommandStatus.SUCCESS) {
            CLog.d("Train is staged successfully. Stdout: %s.", res.getStdout());
        } else {
            throw new TargetSetupError(
                    String.format(
                            "Failed to commit %s on %s. Error: %s, Output: %s",
                            parentSessionId, device.getSerialNumber(), res.getStderr(), res.getStdout()),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.APK_INSTALLATION_FAILED);
        }
        mApkInstalled.addAll(apkPackageNames);
    }

    /**
     * Attempts to install mainline module(s) using bundletool.
     *
     * @param testInfo the {@link TestInformation}
     * @param testAppFileNames the filenames of the preloaded modules to install.
     */
    protected void installUsingBundleTool(TestInformation testInfo, List<File> testAppFileNames)
            throws TargetSetupError, DeviceNotAvailableException {
        initBundletoolUtil(testInfo);
        initDeviceSpecFilePath(testInfo.getDevice());

        if (testAppFileNames.size() == 1) {
            // Installs single .apks module.
            installSingleModuleUsingBundletool(
                    testInfo, mDeviceSpecFilePath, testAppFileNames.get(0));
        } else {
            installMultipleModuleUsingBundletool(testInfo, mDeviceSpecFilePath, testAppFileNames);
        }

        mApkInstalled.addAll(mApkToInstall);
    }

    /**
     * Attempts to install a single mainline module(.apks) using bundletool.
     *
     * @param testInfo the {@link TestInformation}
     * @param deviceSpecFilePath the spec file of the test device
     * @param apkFile the file of the .apks
     */
    private void installSingleModuleUsingBundletool(
            TestInformation testInfo, String deviceSpecFilePath, File apkFile)
            throws TargetSetupError, DeviceNotAvailableException {
        // No need to resolve we have the single .apks file needed.
        File apks = apkFile;
        // Rename the extracted files and add the file to filename list.
        List<File> splits = getSplitsForApks(testInfo, apks);
        ITestDevice device = testInfo.getDevice();
        if (splits == null || splits.isEmpty()) {
            throw new TargetSetupError(
                    String.format("Extraction for %s failed. No apk/apex is extracted.", apkFile),
                    device.getDeviceDescriptor());
        }
        // Install .apks that contain apex module.
        if (containsApex(splits)) {
            Map<File, String> appFilesAndPackages = new LinkedHashMap<>();
            appFilesAndPackages.put(splits.get(0), parsePackageName(splits.get(0)));
            super.installer(testInfo, appFilesAndPackages);
            mTestApexInfoList = collectApexInfoFromApexModules(appFilesAndPackages, testInfo);
        } else {
            // Install .apks that contain apk module.
            List<String> extraArgs = new ArrayList<>();
            addTimeoutMillisForBundletool(extraArgs);
            getBundletoolUtil().installApks(apks, device, extraArgs);
            mApkToInstall.add(parsePackageName(splits.get(0)));
        }
        return;
    }

    /**
     * Attempts to install mainline modules contained in a zip file
     *
     * @param testInfo the {@link TestInformation}
     * @param zipFileName the name of the zip file containing the train
     */
    private void installModulesFromZipUsingBundletool(TestInformation testInfo, String zipFilePath)
            throws TargetSetupError, DeviceNotAvailableException {
        initBundletoolUtil(testInfo);
        initDeviceSpecFilePath(testInfo.getDevice());

        File apksZipFile =
                resolveFilePath(
                        testInfo,
                        zipFilePath,
                        String.format("Failed to find apks zip file %s", mApksZipFileName));

        ITestDevice device = testInfo.getDevice();

        List<String> extraOptions = new ArrayList<>();
        extraOptions.add("--update-only");

        if (mEnableRollback) {
            extraOptions.add(ENABLE_ROLLBACK_INSTALL_OPTION);
        }
        addTimeoutMillisForBundletool(extraOptions);

        device.waitForDeviceAvailable();

        getBundletoolUtil().installApksFromZip(apksZipFile, device, extraOptions);
    }

    /**
     * Attempts to install multiple mainline modules using bundletool. Modules can be any
     * combination of .apk, .apex or .apks.
     *
     * @param testInfo the {@link TestInformation}
     * @param deviceSpecFilePath the spec file of the test device
     * @param testAppFileNames the list of preloaded modules to install.
     */
    private void installMultipleModuleUsingBundletool(
            TestInformation testInfo, String deviceSpecFilePath, List<File> testAppFileNames)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        for (File moduleFileName : testAppFileNames) {
            File moduleFile;
            if (!moduleFileName.isAbsolute()) {
                moduleFile = getLocalPathForFilename(testInfo, moduleFileName.getName());
            } else {
                moduleFile = moduleFileName;
            }
            if (moduleFileName.getName().endsWith(SPLIT_APKS_SUFFIX)) {
                List<File> splits = getSplitsForApks(testInfo, moduleFile);
                String splitsArgs = createInstallArgsForSplit(splits, device);
                mSplitsInstallArgs.add(splitsArgs);
            } else {
                if (moduleFileName.getName().endsWith(APEX_SUFFIX)) {
                    ApexInfo apexInfo = retrieveApexInfo(moduleFile, device.getDeviceDescriptor());
                    mTestApexInfoList.add(apexInfo);
                } else {
                    mApkToInstall.add(parsePackageName(moduleFile));
                }
                mSplitsInstallArgs.add(moduleFile.getAbsolutePath());
            }
        }

        List<String> installCmd = new ArrayList<>();

        installCmd.add(TRAIN_WITH_APEX_INSTALL_OPTION);
        if (mEnableRollback) {
            installCmd.add(ENABLE_ROLLBACK_INSTALL_OPTION);
        }
        addStagedReadyTimeoutForAdb(installCmd);
        for (String arg : mSplitsInstallArgs) {
            installCmd.add(arg);
        }
        device.waitForDeviceAvailable();

        String log = device.executeAdbCommand(installCmd.toArray(new String[0]));
        if (log.contains("Success")) {
            CLog.d("Train is staged successfully. Output: %s.", log);
        } else {
            throw new TargetSetupError(
                    String.format(
                            "Failed to stage train on device %s. Cmd is: %s. Error log: %s.",
                            device.getSerialNumber(), installCmd.toString(), log),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.FAIL_ACTIVATE_APEX);
        }
    }

    /**
     * Retrieves ApexInfo which contains packageName and versionCode from the given apex file.
     *
     * @param testApexFile The apex file we retrieve information from.
     * @return an {@link ApexInfo} containing the packageName and versionCode of the given file
     * @throws TargetSetupError if aapt parser failed to parse the file.
     */
    @VisibleForTesting
    protected ApexInfo retrieveApexInfo(File testApexFile, DeviceDescriptor deviceDescriptor)
            throws TargetSetupError {
        AaptParser parser = AaptParser.parse(testApexFile);
        if (parser == null) {
            throw new TargetSetupError(
                    "apex installed but AaptParser failed",
                    deviceDescriptor,
                    DeviceErrorIdentifier.AAPT_PARSER_FAILED);
        }
        return new ApexInfo(parser.getPackageName(), Long.parseLong(parser.getVersionCode()));
    }

    /**
     * Gets the keyword (e.g., 'tzdata' for com.android.tzdata.apex) from the apex package name.
     *
     * @param packageName The package name of the apex file.
     * @return a string The keyword of the apex package name.
     */
    protected String getModuleKeywordFromApexPackageName(String packageName) {
        String[] components = packageName.split("\\.");
        return components[components.length - 1];
    }

    /* Helper method to format List<ApexInfo> to List<String>. */
    private ArrayList<String> listApexInfo(List<ApexInfo> list) {
        ArrayList<String> res = new ArrayList<String>();
        for (ApexInfo testApexInfo : list) {
            res.add(testApexInfo.toString());
        }
        return res;
    }

    /* Checks if the app file is apex or not */
    private boolean isApex(File file) {
        if (file.getName().endsWith(APEX_SUFFIX)) {
            return true;
        }
        return false;
    }

    /** Checks if the apps need to be installed contains apex. */
    private boolean containsApex(Collection<File> testFileNames) {
        for (File filename : testFileNames) {
            if (filename.getName().endsWith(APEX_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the apps need to be installed contains apex.
     *
     * @param testFileNames The list of the test modules
     */
    private boolean containsApks(List<File> testFileNames) {
        for (File filename : testFileNames) {
            if (filename.getName().endsWith(SPLIT_APKS_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cleans up data/apex/active. data/apex/sessions, data/app-staging.
     *
     * @param device The test device
     */
    private void cleanUpStagedAndActiveSession(ITestDevice device)
            throws DeviceNotAvailableException {
        boolean reboot = false;
        if (!mTestApexInfoList.isEmpty()) {
            device.deleteFile(APEX_DATA_DIR + "*");
            device.deleteFile(STAGING_DATA_DIR + "*");
            device.deleteFile(SESSION_DATA_DIR + "*");
            reboot = true;
        } else {
            if (!device.executeShellV2Command("ls " + APEX_DATA_DIR).getStdout().isEmpty()) {
                device.deleteFile(APEX_DATA_DIR + "*");
                reboot = true;
            }
            if (!device.executeShellV2Command("ls " + STAGING_DATA_DIR).getStdout().isEmpty()) {
                device.deleteFile(STAGING_DATA_DIR + "*");
                reboot = true;
            }
            if (!device.executeShellV2Command("ls " + SESSION_DATA_DIR).getStdout().isEmpty()) {
                device.deleteFile(SESSION_DATA_DIR + "*");
                reboot = true;
            }
        }
        if (reboot) {
            CLog.i("Device Rebooting");
            device.reboot();
        }
    }

    /**
     * Creates the install args for the split .apks.
     *
     * @param splits The directory that split apk/apex get extracted to
     * @param device The test device
     * @return a {@link String} representing the install args for the split apks.
     */
    private String createInstallArgsForSplit(List<File> splits, ITestDevice device)
            throws TargetSetupError {
        String splitsArgs = "";
        for (File f : splits) {
            if (f.getName().endsWith(APEX_SUFFIX)) {
                ApexInfo apexInfo = retrieveApexInfo(f, device.getDeviceDescriptor());
                mTestApexInfoList.add(apexInfo);
            }
            if (f.getName().endsWith(APK_SUFFIX)) {
                mApkToInstall.add(parsePackageName(f));
            }
            if (!splitsArgs.isEmpty()) {
                splitsArgs += ":" + f.getAbsolutePath();
            } else {
                splitsArgs += f.getAbsolutePath();
            }
        }
        return splitsArgs;
    }

    /**
     * Collects apex info from the apex modules for activation check.
     *
     * @param testAppFileNames The list of the file names of the modules to install
     * @param testInfo The {@link TestInformation}
     * @return a list containing the apexinfo of the apex modules in the input file lists
     */
    protected List<ApexInfo> collectApexInfoFromApexModules(
            Map<File, String> testAppFileNames, TestInformation testInfo) throws TargetSetupError {
        List<ApexInfo> apexInfoList = new ArrayList<>();

        for (File appFile : testAppFileNames.keySet()) {
            if (isApex(appFile)) {
                ApexInfo apexInfo =
                        retrieveApexInfo(appFile, testInfo.getDevice().getDeviceDescriptor());
                apexInfoList.add(apexInfo);
            }
        }
        return apexInfoList;
    }

    /**
     * Get modules that failed to be activated.
     *
     * @param activatedApexes The set of the active apexes on device
     * @return a list containing the apexinfo of the input apex modules that failed to be activated.
     */
    protected List<ApexInfo> getModulesFailToActivate(Set<ApexInfo> activatedApexes)
            throws DeviceNotAvailableException, TargetSetupError {
        List<ApexInfo> failToActivateApex = new ArrayList<ApexInfo>();
        HashMap<String, ApexInfo> activatedApexInfo = new HashMap<>();
        for (ApexInfo info : activatedApexes) {
            activatedApexInfo.put(info.name, info);
        }
        for (ApexInfo testApexInfo : mTestApexInfoList) {
            if (!activatedApexInfo.containsKey(testApexInfo.name)) {
                failToActivateApex.add(testApexInfo);
            } else if (PACKAGES_WITH_INVALID_DUMP_INFO.contains(testApexInfo.name)) {
                // Skip checking version or sourceDir if we can't get the valid info.
                // ToDo(b/265785212): Remove this if bug fixed.
                continue;
            } else if (activatedApexInfo.get(testApexInfo.name).versionCode
                    != testApexInfo.versionCode) {
                failToActivateApex.add(testApexInfo);
            } else {
                String sourceDir = activatedApexInfo.get(testApexInfo.name).sourceDir;
                // Activated apex sourceDir starts with "/data"
                if (getDevice().checkApiLevelAgainstNextRelease(R_SDK_INT)
                        && !sourceDir.startsWith(ACTIVATED_APEX_SOURCEDIR_PREFIX, 1)) {
                    failToActivateApex.add(testApexInfo);
                }
            }
        }
        return failToActivateApex;
    }

    protected void addApksToTestFiles() {
        File[] filesUnderTrainFolder = mTrainFolderPath.listFiles();
        Arrays.sort(filesUnderTrainFolder, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : filesUnderTrainFolder) {
            if (f.getName().endsWith(".apks")) {
                getTestsFileName().add(f);
            }
        }
    }

    @VisibleForTesting
    protected String getBundletoolFileName() {
        return mBundletoolFilename;
    }

    @VisibleForTesting
    protected BundletoolUtil getBundletoolUtil() {
        return mBundletoolUtil;
    }

    @VisibleForTesting
    protected List<String> getApkInstalled() {
        return mApkInstalled;
    }

    @VisibleForTesting
    public void setSkipApexTearDown(boolean skip) {
        mSkipApexTearDown = skip;
    }

    @VisibleForTesting
    public void setIgnoreIfNotPreloaded(boolean skip) {
        mIgnoreIfNotPreloaded = skip;
    }

    @VisibleForTesting
    protected void addStagedReadyTimeoutForAdb(List<String> cmd) {
        if (mStagedReadyTimeoutMs > 0) {
            cmd.add(STAGED_READY_TIMEOUT_OPTION);
            cmd.add(Long.toString(mStagedReadyTimeoutMs));
        }
    }

    @VisibleForTesting
    protected void addTimeoutMillisForBundletool(List<String> extraArgs) {
        if (mStagedReadyTimeoutMs > 0) {
            extraArgs.add(TIMEOUT_MILLIS_OPTION + mStagedReadyTimeoutMs);
        }
    }
}
