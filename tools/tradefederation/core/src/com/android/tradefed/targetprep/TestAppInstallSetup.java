/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;

import com.android.annotations.VisibleForTesting;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.IncrementalInstallSession.Builder;
import com.android.incfs.install.PendingBlock;
import com.android.incfs.install.adb.ddmlib.DeviceConnection;
import com.android.incfs.install.adb.ddmlib.DeviceLogger;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.observatory.IDiscoverDependencies;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.AaptParser.AaptVersion;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.BuildTestsZipUtils;
import com.android.utils.StdLogger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link ITargetPreparer} that installs one or more apps from a {@link
 * IDeviceBuildInfo#getTestsDir()} folder onto device.
 *
 * <p>This preparer will look in alternate directories if the tests zip does not exist or does not
 * contain the required apk. The search will go in order from the last alternative dir specified to
 * the first.
 */
@OptionClass(alias = "tests-zip-app")
public class TestAppInstallSetup extends BaseTargetPreparer
        implements IAbiReceiver, IDiscoverDependencies {

    /** The mode the apk should be install in. */
    private enum InstallMode {
        FULL,
        INSTANT,
    }

    // An error message that occurs when a test APK is already present on the DUT,
    // but cannot be updated. When this occurs, the package is removed from the
    // device so that installation can continue like normal.
    private static final String INSTALL_FAILED_UPDATE_INCOMPATIBLE =
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE";

    @VisibleForTesting static final String TEST_FILE_NAME_OPTION = "test-file-name";

    @Option(
            name = TEST_FILE_NAME_OPTION,
            description =
                    "the name of an apk file to be installed on device. Can be repeated. Items "
                            + "that are directories will have any APKs contained therein, "
                            + "including subdirectories, grouped by package name and installed.",
            importance = Importance.IF_UNSET)
    private List<File> mTestFiles = new ArrayList<>();

    // A string made of split apk file names divided by ",".
    // See "https://developer.android.com/studio/build/configure-apk-splits" on how to split
    // apk to several files.
    @Option(
            name = "split-apk-file-names",
            description =
                    "the split apk file names separted by comma that will be installed on device."
                        + " Can be repeated for multiple split apk sets.See"
                        + " https://developer.android.com/studio/build/configure-apk-splits on how"
                        + " to split apk to several files")
    private List<String> mSplitApkFileNames = new ArrayList<>();

    @VisibleForTesting static final String THROW_IF_NOT_FOUND_OPTION = "throw-if-not-found";

    @Option(
            name = THROW_IF_NOT_FOUND_OPTION,
            description = "Throw exception if the specified file is not found.")
    private boolean mThrowIfNoFile = true;

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = null;

    @Option(name = "install-arg",
            description = "Additional arguments to be passed to install command, "
                    + "including leading dash, e.g. \"-d\"")
    private Collection<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = "force-queryable",
            description = "Whether apks should be installed as force queryable.")
    private Boolean mForceQueryable = null;

    @Option(
            name = "cleanup-apks",
            description =
                    "Whether apks installed should be uninstalled after test. Note that the "
                            + "preparer does not verify if the apks are successfully removed.")
    private boolean mCleanup = true;

    @VisibleForTesting static final String CHECK_MIN_SDK_OPTION = "check-min-sdk";

    @Option(
            name = CHECK_MIN_SDK_OPTION,
            description =
                    "check app's min sdk prior to install and skip if device api level is too low.")
    private boolean mCheckMinSdk = false;

    /** @deprecated use test-file-name instead now that it is a File. */
    @Deprecated
    @Option(
            name = "alt-dir",
            description =
                    "Alternate directory to look for the apk if the apk is not in the tests "
                            + "zip file. For each alternate dir, will look in //, //data/app, "
                            + "//DATA/app, //DATA/app/apk_name/ and //DATA/priv-app/apk_name/. "
                            + "Can be repeated. Look for apks in last alt-dir first.")
    private List<File> mAltDirs = new ArrayList<>();

    /** @deprecated goes in pair with alt-dir which is deprecated */
    @Deprecated
    @Option(
            name = "alt-dir-behavior",
            description =
                    "The order of alternate directory to be used when searching for apks to "
                            + "install")
    private AltDirBehavior mAltDirBehavior = AltDirBehavior.FALLBACK;

    @Option(name = "instant-mode", description = "Whether or not to install apk in instant mode.")
    private boolean mInstantMode = false;

    @Option(name = "aapt-version", description = "The version of AAPT for APK parsing.")
    private AaptVersion mAaptVersion = AaptVersion.AAPT2;

    @Option(
            name = "force-install-mode",
            description =
                    "Force the preparer to ignore instant-mode option, and install in the"
                            + " requested mode.")
    private InstallMode mInstallationMode = null;

    @Option(
            name = "incremental",
            description =
                    "Performs an installation using incremental streaming. Given the"
                            + " non-deterministic nature of an incremental installation, it is not"
                            + " guaranteed that a test run with this option will yield the same"
                            + " results of previous or future invocations.")
    @VisibleForTesting
    protected boolean mIncrementalInstallation = false;

    @Option(
            name = "incremental-block-filter",
            description =
                    "Decimal representation of the percentage of data blocks"
                            + " to be filtered out during an incremental"
                            + " installation.")
    protected double mBlockFilterPercentage = 0.0;

    @Option(
            name = "incremental-install-timeout-secs",
            description =
                    "Specifies the maximum permitted duration of" + " an incremental installation.")
    protected int mIncrementalInstallTimeout = 1800;

    private IAbi mAbi = null;
    private Integer mUserId = null;
    private Boolean mGrantPermission = null;

    private Set<String> mPackagesInstalled = new HashSet<>();
    private TestInformation mTestInfo;
    @VisibleForTesting protected IncrementalInstallSession incrementalInstallSession;

    protected void setTestInformation(TestInformation testInfo) {
        mTestInfo = testInfo;
    }

    /** Adds a file or directory to the list of apks to installed. */
    public void addTestFile(File file) {
        mTestFiles.add(file);
    }

    /** Adds a file name to the list of apks to installed. */
    public void addTestFileName(String fileName) {
        addTestFile(new File(fileName));
    }

    /** Helper to parse an apk file with aapt. */
    @VisibleForTesting
    AaptParser doAaptParse(File apkFile) {
        return AaptParser.parse(apkFile);
    }

    @VisibleForTesting
    void clearTestFile() {
        mTestFiles.clear();
    }

    /**
     * Adds a set of file names divided by ',' in a string to be installed as split apks
     *
     * @param fileNames a string of file names divided by ','
     */
    public void addSplitApkFileNames(String fileNames) {
        mSplitApkFileNames.add(fileNames);
    }

    @VisibleForTesting
    void clearSplitApkFileNames() {
        mSplitApkFileNames.clear();
    }

    /** Returns a copy of the list of specified test apk names. */
    public List<File> getTestsFileName() {
        return mTestFiles;
    }

    /** Sets whether or not the installed apk should be cleaned on tearDown */
    public void setCleanApk(boolean shouldClean) {
        mCleanup = shouldClean;
    }

    /**
     * If the apk should be installed for a particular user, sets the id of the user to install for.
     */
    public void setUserId(int userId) {
        mUserId = userId;
    }

    /** If a userId is provided, grantPermission can be set for the apk installation. */
    public void setShouldGrantPermission(boolean shouldGrant) {
        mGrantPermission = shouldGrant;
    }

    /** Sets the version of AAPT for APK parsing. */
    public void setAaptVersion(AaptVersion aaptVersion) {
        mAaptVersion = aaptVersion;
    }

    /** Adds one apk installation arg to be used. */
    public void addInstallArg(String arg) {
        mInstallArgs.add(arg);
    }

    /**
     * The default value of the force queryable is true. Update it to false if the apk to be
     * installed should not be queryable.
     */
    public void setForceQueryable(boolean forceQueryable) {
        mForceQueryable = forceQueryable;
    }

    /**
     * Resolve the actual apk path based on testing artifact information inside build info.
     *
     * @param testInfo The {@link TestInformation} for the invocation.
     * @param apkFileName filename of the apk to install
     * @return a {@link File} representing the physical apk file on host or {@code null} if the file
     *     does not exist.
     */
    protected File getLocalPathForFilename(TestInformation testInfo, String apkFileName)
            throws TargetSetupError {
        try {
            return BuildTestsZipUtils.getApkFile(
                    testInfo.getBuildInfo(),
                    apkFileName,
                    mAltDirs,
                    mAltDirBehavior,
                    false /* use resource as fallback */,
                    null /* device signing key */);
        } catch (IOException ioe) {
            throw new TargetSetupError(
                    String.format(
                            "failed to resolve apk path for apk %s in build %s",
                            apkFileName, testInfo.getBuildInfo().toString()),
                    ioe,
                    testInfo.getDevice().getDeviceDescriptor(),
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }
    }

    /** @deprecated Temporary backward compatible callback. */
    @Deprecated
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", device);
        context.addDeviceBuildInfo("device", buildInfo);
        TestInformation backwardCompatible =
                TestInformation.newBuilder().setInvocationContext(context).build();
        setUp(backwardCompatible);
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        mTestInfo = testInfo;
        if (mTestFiles.isEmpty() && mSplitApkFileNames.isEmpty()) {
            CLog.i("No test apps to install, skipping");
            return;
        }
        // resolve abi flags
        if (mAbi != null && mForceAbi != null) {
            throw new IllegalStateException("cannot specify both abi flags: --abi and --force-abi");
        }

        // We are going to need several "ro.build" props, save some time (0.4 sec) by prefetching
        if (getDevice() instanceof NativeDevice) {
            ((NativeDevice) getDevice()).batchPrefetchStartupBuildProps();
        }
        String abiName = null;
        if (mAbi != null) {
            abiName = mAbi.getName();
        } else if (mForceAbi != null) {
            abiName = AbiFormatter.getDefaultAbi(getDevice(), mForceAbi);
        }
        // Set all the extra install args outside the loop to avoid adding them several times.
        if (abiName != null && testInfo.getDevice().getApiLevel() > 20) {
            mInstallArgs.add(String.format("--abi %s", abiName));
        }
        // Handle instant mode: if we are forced in one installation mode or not.
        // Some preparer are locked in one installation mode or another, they ignore the
        // 'instant-mode' option and stays in their mode.
        if (mInstallationMode != null) {
            if (InstallMode.INSTANT.equals(mInstallationMode)) {
                mInstallArgs.add("--instant");
            }
        } else {
            if (mInstantMode) {
                mInstallArgs.add("--instant");
            }
        }

        if (mUserId == null && testInfo.properties().get(RUN_TESTS_AS_USER_KEY) != null) {
            mUserId = Integer.parseInt(testInfo.properties().get(RUN_TESTS_AS_USER_KEY));
            if (!testInfo.getDevice().getUserInfos().containsKey(mUserId)) {
                CLog.w("User requested: %s doesn't exist on device. Ignoring it.", mUserId);
                mUserId = null;
            } else {
                CLog.d("Using user %s from testInfo properties.", mUserId);
            }
        }

        if (mForceQueryable == null) {
            // Do not add --force-queryable if the device api level >= 34. Ideally,
            // checkApiLevelAgainstNextRelease(34) should only return true for api 34 devices. But,
            // it also returns true for branches like the tm-xx-plus-aosp. Adding another condition
            // ro.build.id==TM to handle this special case.
            mForceQueryable =
                    !getDevice().checkApiLevelAgainstNextRelease(34)
                            || "TM".equals(getDevice().getBuildAlias());
        }
        if (mForceQueryable && getDevice().isAppEnumerationSupported()) {
            mInstallArgs.add("--force-queryable");
        }

        // Add bypass flag for low target sdk apps when installing on U+ devices
        if (getDevice().isBypassLowTargetSdkBlockSupported()) {
            mInstallArgs.add("--bypass-low-target-sdk-block");
        }

        for (File testAppName : mTestFiles) {
            Map<File, String> appFilesAndPackages =
                    resolveApkFiles(testInfo, findApkFiles(testAppName));
            installer(testInfo, appFilesAndPackages);
        }

        for (String testAppNames : mSplitApkFileNames) {
            List<String> apkNames = Arrays.asList(testAppNames.split(","));
            List<File> apkFileNames =
                    apkNames.stream().map(a -> new File(a)).collect(Collectors.toList());
            Map<File, String> appFilesAndPackages = resolveApkFiles(testInfo, apkFileNames);
            installer(testInfo, appFilesAndPackages);
        }
    }

    /**
     * Returns the device that the preparer should apply to.
     *
     * @throws TargetSetupError
     */
    public ITestDevice getDevice() throws TargetSetupError {
        return mTestInfo.getDevice();
    }

    public TestInformation getTestInfo() {
        return mTestInfo;
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /**
     * Sets whether or not --instant should be used when installing the apk. Will have no effect if
     * force-install-mode is set.
     */
    public final void setInstantMode(boolean mode) {
        mInstantMode = mode;
    }

    /** Returns whether or not instant mode installation has been enabled. */
    public final boolean isInstantMode() {
        return mInstantMode;
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        mTestInfo = testInfo;
        if (mCleanup && !(e instanceof DeviceNotAvailableException)) {
            for (String packageName : mPackagesInstalled) {
                try {
                    uninstallPackage(getDevice(), packageName);
                } catch (TargetSetupError tse) {
                    CLog.e(tse);
                }
            }
        }
    }

    /**
     * Set an alternate directory.
     */
    public void setAltDir(File altDir) {
        mAltDirs.add(altDir);
    }

    /**
     * Set an alternate directory behaviors.
     */
    public void setAltDirBehavior(AltDirBehavior altDirBehavior) {
        mAltDirBehavior = altDirBehavior;
    }

    /** Returns True if Apks will be cleaned up during tear down. */
    public boolean isCleanUpEnabled() {
        return mCleanup;
    }

    /**
     * Attempt to install an package or split package on the device.
     *
     * @param testInfo the {@link TestInformation} for the invocation
     * @param appFilesAndPackages The apks and their package to be installed.
     */
    protected void installer(TestInformation testInfo, Map<File, String> appFilesAndPackages)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();

        // TODO(hzalek): Consider changing resolveApkFiles's return to a Multimap to avoid building
        // it here.
        ImmutableListMultimap<String, File> packageToFiles =
                ImmutableListMultimap.copyOf(appFilesAndPackages.entrySet()).inverse();

        Builder builder = null;
        if (mIncrementalInstallation) {
            builder = getIncrementalInstallSessionBuilder();
        }

        for (Map.Entry<String, List<File>> e : Multimaps.asMap(packageToFiles).entrySet()) {
            if (mIncrementalInstallation) {
                CLog.d(
                        "Performing incremental installation of apk %s with %s ...",
                        e.getKey(), e.getValue());
                addPackageToIncrementalInstallSession(builder, e.getKey(), e.getValue());
                if (mCleanup) {
                    mPackagesInstalled.add(e.getKey());
                }
            } else {
                installSinglePackage(device, e.getKey(), e.getValue());
            }
        }

        if (mIncrementalInstallation && builder != null) {
            installPackageIncrementally(builder);
        }
    }

    private void installSinglePackage(
            ITestDevice testDevice, String packageName, List<File> apkFiles)
            throws TargetSetupError, DeviceNotAvailableException {

        if (apkFiles.isEmpty()) {
            return;
        }

        CLog.d("Installing apk %s with %s ...", packageName, apkFiles);
        String result = installPackage(testDevice, apkFiles);

        if (result != null) {
            if (result.startsWith(INSTALL_FAILED_UPDATE_INCOMPATIBLE)) {
                // Try to uninstall package and reinstall.
                uninstallPackage(testDevice, packageName);
                result = installPackage(testDevice, apkFiles);
            }
        }

        if (result != null) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to install %s with %s on %s. Reason: '%s'",
                            packageName, apkFiles, testDevice.getSerialNumber(), result),
                    testDevice.getDeviceDescriptor(),
                    DeviceErrorIdentifier.APK_INSTALLATION_FAILED);
        }

        if (mCleanup) {
            mPackagesInstalled.add(packageName);
        }
    }

    /** Helper to resolve some apk to their File and Package. */
    @VisibleForTesting
    protected Map<File, String> resolveApkFiles(TestInformation testInfo, List<File> apkFiles)
            throws TargetSetupError, DeviceNotAvailableException {
        Map<File, String> appFiles = new LinkedHashMap<>();
        ITestDevice device = testInfo.getDevice();
        for (File apkFile : apkFiles) {
            File testAppFile = null;
            if (apkFile.isAbsolute()) {
                testAppFile = apkFile;
            }
            if (testAppFile == null) {
                testAppFile = getLocalPathForFilename(testInfo, apkFile.getName());
            }
            if (testAppFile == null) {
                if (mThrowIfNoFile) {
                    throw new TargetSetupError(
                            String.format("Test app %s was not found.", apkFile.getName()),
                            device.getDeviceDescriptor(),
                            InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
                } else {
                    CLog.d("Test app %s was not found.", apkFile.getName());
                    continue;
                }
            }
            if (!testAppFile.canRead()) {
                if (mThrowIfNoFile) {
                    throw new TargetSetupError(
                            String.format("Could not read file %s.", testAppFile.toString()),
                            device.getDeviceDescriptor());
                } else {
                    CLog.d("Could not read file %s.", testAppFile.toString());
                    continue;
                }
            }

            if (mCheckMinSdk) {
                AaptParser aaptParser = doAaptParse(testAppFile);
                if (aaptParser == null) {
                    throw new TargetSetupError(
                            String.format(
                                    "Failed to extract info from `%s` using aapt",
                                    testAppFile.getAbsoluteFile().getName()),
                            device.getDeviceDescriptor());
                }
                if (device.getApiLevel() < aaptParser.getSdkVersion()) {
                    CLog.w(
                            "Skipping installing apk %s on device %s because "
                                    + "SDK level require is %d, but device SDK level is %d",
                            apkFile.toString(),
                            device.getSerialNumber(),
                            aaptParser.getSdkVersion(),
                            device.getApiLevel());
                } else {
                    appFiles.put(testAppFile, parsePackageName(testAppFile));
                }
            } else {
                appFiles.put(testAppFile, parsePackageName(testAppFile));
            }
        }
        return appFiles;
    }

    /**
     * Returns the provided file if not a directory or all APK files contained in the directory tree
     * rooted at the provided path otherwise.
     */
    private List<File> findApkFiles(File fileOrDirectory) throws TargetSetupError {

        if (!fileOrDirectory.isDirectory()) {
            return ImmutableList.of(fileOrDirectory);
        }

        List<File> apkFiles;

        try (Stream<Path> paths = Files.walk(fileOrDirectory.toPath())) {
            apkFiles =
                    paths.filter(p -> p.toString().endsWith(".apk"))
                            .filter(Files::isRegularFile)
                            .map(Path::toFile)
                            .collect(Collectors.toList());
        } catch (IOException e) {
            throw new TargetSetupError(
                    String.format(
                            "Could not list files of specified directory: %s", fileOrDirectory),
                    e,
                    null,
                    false,
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }

        if (mThrowIfNoFile && apkFiles.isEmpty()) {
            throw new TargetSetupError(
                    String.format(
                            "Could not find any files in specified directory: %s", fileOrDirectory),
                    null,
                    null,
                    false,
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }

        return apkFiles;
    }

    /**
     * Attempt to install a package or split package on the device.
     *
     * @param device the {@link ITestDevice} to install package
     * @param appFiles List of Files. If apkFiles contains only one apk file, the app will be
     *     installed as a whole package with single file. If apkFiles contains more than one name,
     *     the app will be installed as split apk with multiple files.
     */
    private String installPackage(ITestDevice device, List<File> appFiles)
            throws DeviceNotAvailableException {
        // Handle the different install use cases (with or without a user)
        if (mUserId == null) {
            if (appFiles.size() == 1) {
                return device.installPackage(
                        appFiles.get(0), true, mInstallArgs.toArray(new String[] {}));
            } else {
                return device.installPackages(
                        appFiles, true, mInstallArgs.toArray(new String[] {}));
            }
        } else if (mGrantPermission != null) {
            if (appFiles.size() == 1) {
                return device.installPackageForUser(
                        appFiles.get(0),
                        true,
                        mGrantPermission,
                        mUserId,
                        mInstallArgs.toArray(new String[] {}));
            } else {
                return device.installPackagesForUser(
                        appFiles,
                        true,
                        mGrantPermission,
                        mUserId,
                        mInstallArgs.toArray(new String[] {}));
            }
        } else {
            if (appFiles.size() == 1) {
                return device.installPackageForUser(
                        appFiles.get(0), true, mUserId, mInstallArgs.toArray(new String[] {}));
            } else {
                return device.installPackagesForUser(
                        appFiles, true, mUserId, mInstallArgs.toArray(new String[] {}));
            }
        }
    }

    /** Attempt to remove the package from the device. */
    protected void uninstallPackage(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        String msg;
        if (mUserId == null) {
            msg = device.uninstallPackage(packageName);
        } else {
            msg = device.uninstallPackageForUser(packageName, mUserId);
        }
        if (msg != null) {
            CLog.w(String.format("error uninstalling package '%s': %s", packageName, msg));
        }
        if (mIncrementalInstallation) {
            incrementalInstallSession.close();
        }
    }

    /** Get the package name from the test app. */
    protected String parsePackageName(File testAppFile) throws TargetSetupError {
        AaptParser parser = AaptParser.parse(testAppFile, mAaptVersion);
        if (parser == null) {
            throw new TargetSetupError(
                    String.format(
                            "AaptParser failed for file %s. The APK won't be installed",
                            testAppFile.getName()),
                    null,
                    null,
                    false, // Not device side error, doesn't need descriptor
                    DeviceErrorIdentifier.AAPT_PARSER_FAILED);
        }
        return parser.getPackageName();
    }

    /**
     * Add APKs from package to incremental installation session builder object.
     *
     * @param builder The Builder object for the incremental install session.
     * @param packageName The name of the package to be added.
     * @param packageFiles List of files to be added to builder object.
     * @throws TargetSetupError
     */
    private void addPackageToIncrementalInstallSession(
            Builder builder, String packageName, List<File> packageFiles) throws TargetSetupError {
        for (File apk : packageFiles) {
            Path apkPath = apk.toPath();
            Path apkSignaturePath = Paths.get(String.format("%s.idsig", apkPath.toString()));
            if (!apkSignaturePath.toFile().exists()) {
                throw new TargetSetupError(
                        String.format(
                                "Unable to retrieve v4 signature for file: %s",
                                apkPath.getFileName()),
                        getDevice().getDeviceDescriptor(),
                        InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
            }
            builder.addApk(apkPath, apkSignaturePath);
        }
    }

    /**
     * Start the incremental installation session for a test app.
     *
     * @param builder The Builder object for the incremental install session.
     * @throws TargetSetupError
     */
    @VisibleForTesting
    protected void installPackageIncrementally(Builder builder) throws TargetSetupError {
        try {
            incrementalInstallSession = builder.build();
            String deviceSerialNumber = getDevice().getSerialNumber();
            DeviceConnection.Factory deviceConnection =
                    DeviceConnection.getFactory(deviceSerialNumber);
            incrementalInstallSession.start(Executors.newCachedThreadPool(), deviceConnection);
            incrementalInstallSession.waitForInstallCompleted(
                    mIncrementalInstallTimeout, TimeUnit.SECONDS);
        } catch (InterruptedException | IOException e) {
            throw new TargetSetupError(
                    String.format("Failed to start incremental install session."),
                    e,
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.APK_INSTALLATION_FAILED);
        }
    }

    /** Initialize the session builder for installing a test app incrementally. */
    @VisibleForTesting
    protected Builder getIncrementalInstallSessionBuilder() {
        if (mGrantPermission != null && mGrantPermission) {
            mInstallArgs.add("-g");
        }

        if (mUserId != null) {
            mInstallArgs.add("--user");
            mInstallArgs.add(Integer.toString(mUserId));
        }

        Builder incrementalInstallSessionBuilder =
                new Builder()
                        .setLogger(new DeviceLogger(new StdLogger(StdLogger.Level.ERROR)))
                        .addExtraArgs(mInstallArgs.toArray(new String[] {}));

        // Add block filter to installation if a block filter percentage is specified.
        if (mBlockFilterPercentage > 0) {
            long randomSeed = new SecureRandom().nextLong();
            Random randomBlock = new Random(randomSeed);
            Map<Path, Set<Integer>> apkBlockMappings = new HashMap<>();

            CLog.i("Block filter seed: %d.", randomSeed);

            incrementalInstallSessionBuilder.setBlockFilter(
                    (PendingBlock b) -> {
                        Path apkPath = b.getPath();
                        synchronized (apkBlockMappings) {
                            // Generate block indexs to filter for APK installation.
                            if (!apkBlockMappings.containsKey(apkPath)) {
                                int blockCount = b.getFileBlockCount();
                                int numBlocks = (int) (blockCount * mBlockFilterPercentage);
                                Set<Integer> blocksToFilter = new HashSet<Integer>(numBlocks);
                                while (blocksToFilter.size() < numBlocks) {
                                    int blockIndex = randomBlock.nextInt(blockCount);
                                    blocksToFilter.add(blockIndex);
                                }
                                apkBlockMappings.put(apkPath, blocksToFilter);
                            }

                            return !apkBlockMappings.get(apkPath).contains(b.getBlockIndex());
                        }
                    });
        }

        return incrementalInstallSessionBuilder;
    }

    @Override
    public Set<String> reportDependencies() {
        Set<String> deps = new HashSet<String>();
        for (File f : getTestsFileName()) {
            if (!f.exists()) deps.add(f.getName());
        }
        for (String testAppNames : mSplitApkFileNames) {
            List<String> apkNames = Arrays.asList(testAppNames.split(","));
            deps.addAll(apkNames);
        }
        return deps;
    }
}
