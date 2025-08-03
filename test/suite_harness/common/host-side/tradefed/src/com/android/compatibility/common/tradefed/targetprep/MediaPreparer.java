/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.targetprep;

import com.android.annotations.VisibleForTesting;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.util.DynamicConfigFileReader;
import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.dependencies.ExternalDependency;
import com.android.tradefed.dependencies.IExternalDependency;
import com.android.tradefed.dependencies.connectivity.NetworkDependency;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.contentprovider.ContentProviderHandler;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Ensures that the appropriate media files exist on the device */
@OptionClass(alias = "media-preparer")
public class MediaPreparer extends BaseTargetPreparer
        implements IExternalDependency, IConfigurationReceiver {

    @Option(
        name = "local-media-path",
        description =
                "Absolute path of the media files directory, containing"
                        + "'bbb_short' and 'bbb_full' directories"
    )
    private String mLocalMediaPath = null;

    @Option(
        name = "skip-media-download",
        description = "Whether to skip the media files precondition"
    )
    private boolean mSkipMediaDownload = false;

    @Option(
            name = "simple-caching-semantics",
            description = "Whether to use the original, simple MediaPreparer caching semantics")
    private boolean mSimpleCachingSemantics = false;

    @Option(
            name = "media-download-only",
            description = "Only download media files; do not run instrumentation or copy files")
    private boolean mMediaDownloadOnly = false;

    @Option(
        name = "push-all",
        description =
                "Push everything downloaded to the device,"
                        + " use 'media-folder-name' to specify the destination dir name."
    )
    private boolean mPushAll = false;

    @Option(name = "dynamic-config-module",
            description = "For a target preparer, the 'module' of the configuration" +
            " is the test suite.")
    private String mDynamicConfigModule = "cts";

    @Option(name = "media-folder-name",
            description = "The name of local directory into which media" +
            " files will be downloaded, if option 'local-media-path' is not" +
            " provided. This directory will live inside the temp directory." +
            " If option 'push-all' is set, this is also the subdirectory name on device" +
            " where media files are pushed to")
    private String mMediaFolderName = MEDIA_FOLDER_NAME;

    @Option(name = "use-legacy-folder-structure",
            description = "Use legacy folder structure to store big buck bunny clips. When this " +
            "is set to false, name specified in media-folder-name will be used. Default: true")
    private boolean mUseLegacyFolderStructure = true;

    /*
     * The pathnames of the device's directories that hold media files for the tests.
     * These depend on the device's mount point, which is retrieved in the MediaPreparer's run
     * method.
     *
     * These fields are exposed for unit testing
     */
    protected String mBaseDeviceModuleDir;
    protected String mBaseDeviceShortDir;
    protected String mBaseDeviceFullDir;

    /*
     * Variables set by the MediaPreparerListener during retrieval of maximum media file
     * resolution. After the MediaPreparerApp has been instrumented on the device:
     *
     * testMetrics contains the string representation of the resolution
     * testFailures contains a stacktrace if retrieval of the resolution was unsuccessful
     */
    protected Resolution mMaxRes = null;
    protected String mFailureStackTrace = null;

    /*
     * Track the user being prepared through setUp to avoid re-querying it.
     */
    private int mCurrentUser = -1;

    /** The module level configuration to check the target preparers. */
    private IConfiguration mModuleConfiguration;

    /*
     * The default name of local directory into which media files will be downloaded, if option
     * "local-media-path" is not provided. This directory will live inside the temp directory.
     */
    protected static final String MEDIA_FOLDER_NAME = "android-cts-media";

    /* The key used to retrieve the media files URL from the dynamic configuration */
    private static final String MEDIA_FILES_URL_KEY = "media_files_url";

    /*
     * Info used to install and uninstall the MediaPreparerApp
     */
    private static final String APP_APK = "CtsMediaPreparerApp.apk";
    private static final String APP_PKG_NAME = "android.mediastress.cts.preconditions.app";

    /* Key to retrieve resolution string in metrics upon MediaPreparerListener.testEnded() */
    private static final String RESOLUTION_STRING_KEY = "resolution";

    protected static final Resolution[] RESOLUTIONS = {
            new Resolution(176, 144),
            new Resolution(480, 360),
            new Resolution(720, 480),
            new Resolution(1280, 720),
            new Resolution(1920, 1080)
    };

    /** {@inheritDoc} */
    @Override
    public Set<ExternalDependency> getDependencies() {
        Set<ExternalDependency> dependencies = new HashSet<>();
        if (!mSkipMediaDownload) {
            dependencies.add(new NetworkDependency());
        }
        return dependencies;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mModuleConfiguration = configuration;
    }

    /** Helper class for generating and retrieving width-height pairs */
    protected static final class Resolution {
        // regex that matches a resolution string
        private static final String PATTERN = "(\\d+)x(\\d+)";
        // group indices for accessing resolution width and height from a PATTERN-based Matcher
        private static final int WIDTH_INDEX = 1;
        private static final int HEIGHT_INDEX = 2;

        private final int width;
        private final int height;

        private Resolution(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private Resolution(String resolution) {
            Pattern pattern = Pattern.compile(PATTERN);
            Matcher matcher = pattern.matcher(resolution);
            matcher.find();
            this.width = Integer.parseInt(matcher.group(WIDTH_INDEX));
            this.height = Integer.parseInt(matcher.group(HEIGHT_INDEX));
        }

        @Override
        public String toString() {
            return String.format("%dx%d", width, height);
        }

        /** Returns the width of the resolution. */
        public int getWidth() {
            return width;
        }
    }

    public static File getDefaultMediaDir() {
        return new File(System.getProperty("java.io.tmpdir"), MEDIA_FOLDER_NAME);
    }

    protected File getMediaDir() {
        return new File(System.getProperty("java.io.tmpdir"), mMediaFolderName);
    }

    /*
     * Returns true if all necessary media files exist on the device, and false otherwise.
     *
     * This method is exposed for unit testing.
     */
    @VisibleForTesting
    protected boolean mediaFilesExistOnDevice(ITestDevice device)
            throws DeviceNotAvailableException {
        if (mPushAll) {
            return device.doesFileExist(mBaseDeviceModuleDir, mCurrentUser);
        }
        for (Resolution resolution : RESOLUTIONS) {
            if (resolution.width > mMaxRes.width) {
                break; // no need to check for resolutions greater than this
            }
            String deviceShortFilePath = mBaseDeviceShortDir + resolution.toString();
            String deviceFullFilePath = mBaseDeviceFullDir + resolution.toString();
            if (!device.doesFileExist(deviceShortFilePath, mCurrentUser)
                    || !device.doesFileExist(deviceFullFilePath, mCurrentUser)) {
                return false;
            }
        }
        return true;
    }

    protected static final String TOC_NAME = "contents.toc";

    /*
     * After downloading and unzipping the media files, mLocalMediaPath must be the path to the
     * directory containing 'bbb_short' and 'bbb_full' directories, as it is defined in its
     * description as an option.
     * After extraction, this directory exists one level below the the directory 'mediaFolder'.
     * If the 'mediaFolder' contains anything other than exactly one subdirectory, a
     * TargetSetupError is thrown. Otherwise, the mLocalMediaPath variable is set to the path of
     * this subdirectory.
     */
    private void updateLocalMediaPath(ITestDevice device, File mediaFolder)
            throws TargetSetupError {
        String[] entries = mediaFolder.list();

        // directory should contain:
        // -- content subdirectory
        // -- TOC (if we've run with the new caching semantics)
        // if we've run new semantics, old semantics should ignore the TOC if present.
        //
        if (entries.length == 0) {
            throw new TargetSetupError(
                    String.format("Unexpectedly empty directory %s", mediaFolder.getAbsolutePath()),
                    device.getDeviceDescriptor());
        } else if (entries.length > 2) {
            throw new TargetSetupError(String.format(
                    "Unexpected contents in directory %s", mediaFolder.getAbsolutePath()),
                    device.getDeviceDescriptor());
        }

        // choose the entry that represents the contents to be sent, not the TOC
        int slot = 0;
        if (entries[slot].equals(TOC_NAME)) {
            if (entries.length == 1) {
                throw new TargetSetupError(
                        String.format(
                                "Missing contents in directory %s", mediaFolder.getAbsolutePath()),
                        device.getDeviceDescriptor());
            }
            slot = 1;
        }
        mLocalMediaPath = new File(mediaFolder, entries[slot]).getAbsolutePath();
    }

    private void generateDirectoryToc(FileWriter myWriter, File myFolder, String leadingPath)
            throws IOException {
        String prefixPath;
        if (leadingPath.equals("")) {
            prefixPath = "";
        } else {
            prefixPath = leadingPath + File.separator;
        }
        for (String fileName : myFolder.list()) {
            // list myself
            myWriter.write(prefixPath + fileName + "\n");
            // and recurse if i'm a directory
            File oneFile = new File(myFolder, fileName);
            if (oneFile.isDirectory()) {
                String newLeading = prefixPath + fileName;
                generateDirectoryToc(myWriter, oneFile, newLeading);
            }
        }
    }

    /*
     * Copies the media files to the host from a predefined URL.
     *
     * Synchronize this method so that multiple shards won't download/extract
     * this file to the same location on the host. Only an issue in Android O and above,
     * where MediaPreparer is used for multiple, shardable modules.
     */
    private File downloadMediaToHost(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError {

        // Make sure the synchronization is on the class and not the object
        synchronized (MediaPreparer.class) {
            // Retrieve default directory for storing media files
            File mediaFolder = getMediaDir();

            // manage caching the content on the host side
            //
            if (mediaFolder.exists() && mediaFolder.list().length > 0) {
                // Folder has been created and populated by a previous MediaPreparer run.
                //

                if (mSimpleCachingSemantics) {
                    // old semantics: assumes all necessary media files exist inside
                    CLog.i("old cache semantics: local directory exists, all is well");
                    return mediaFolder;
                }

                CLog.i("new cache semantics: verify against a TOC");
                // new caching semantics:
                // verify that the contents are still present.
                // use the TOC file generated when first downloaded/unpacked.
                // if TOC or any files are missing -- redownload.
                //
                // we're chatty about why we decide to re-download

                boolean passing = true;
                BufferedReader tocReader = null;
                try {
                    File tocFile = new File(mediaFolder, TOC_NAME);
                    if (!tocFile.exists()) {
                        passing = false;
                        CLog.i(
                                "missing/inaccessible TOC: "
                                        + mediaFolder
                                        + File.separator
                                        + TOC_NAME);
                    } else {
                        tocReader = new BufferedReader(new FileReader(tocFile));
                        String line = tocReader.readLine();
                        while (line != null) {
                            File oneFile = new File(mediaFolder, line);
                            if (!oneFile.exists()) {
                                CLog.i(
                                        "missing TOC-listed file: "
                                                + mediaFolder
                                                + File.separator
                                                + line);
                                passing = false;
                                break;
                            }
                            line = tocReader.readLine();
                        }
                    }
                } catch (IOException | SecurityException | NullPointerException e) {
                    CLog.i("TOC or contents missing, redownload");
                    passing = false;
                } finally {
                    StreamUtil.close(tocReader);
                }

                if (passing) {
                    CLog.i("Host-cached copy is complete in " + mediaFolder);
                    return mediaFolder;
                }
            }

            // uncached (or broken cache), so download again

            mediaFolder.mkdirs();
            URL url;
            try {
                // Get download URL from dynamic configuration service
                String mediaUrlString =
                        DynamicConfigFileReader.getValueFromConfig(
                                buildInfo, mDynamicConfigModule, MEDIA_FILES_URL_KEY);
                url = new URL(mediaUrlString);
            } catch (IOException | XmlPullParserException e) {
                throw new TargetSetupError(
                        "Trouble finding media file download location with "
                                + "dynamic configuration",
                        e,
                        device.getDeviceDescriptor());
            }
            File mediaFolderZip = new File(mediaFolder.getAbsolutePath() + ".zip");
            FileWriter tocWriter = null;
            try {
                CLog.i("Downloading media files from %s", url.toString());
                URLConnection conn = url.openConnection();
                InputStream in = conn.getInputStream();
                mediaFolderZip.createNewFile();
                FileUtil.writeToFile(in, mediaFolderZip);
                CLog.i("Unzipping media files");
                ZipUtil.extractZip(new ZipFile(mediaFolderZip), mediaFolder);

                // create the TOC when running the new caching scheme
                if (!mSimpleCachingSemantics) {
                    // create a TOC, recursively listing all files/directories.
                    // used to verify all files still exist before we re-use a prior copy
                    CLog.i("Generating cache TOC");
                    File tocFile = new File(mediaFolder, TOC_NAME);
                    tocWriter = new FileWriter(tocFile, /*append*/ false);
                    generateDirectoryToc(tocWriter, mediaFolder, "");
                }

            } catch (IOException e) {
                FileUtil.recursiveDelete(mediaFolder);
                throw new TargetSetupError(
                        String.format(
                                "Failed to download and open media files on host machine at '%s'."
                                    + " These media files are required for compatibility tests.",
                                mediaFolderZip),
                        e,
                        device.getDeviceDescriptor(),
                        /* device side */ false);
            } finally {
                FileUtil.deleteFile(mediaFolderZip);
                StreamUtil.close(tocWriter);
            }
            return mediaFolder;
        }
    }

    /*
     * Pushes directories containing media files to the device for all directories that:
     * - are not already present on the device
     * - contain video files of a resolution less than or equal to the device's
     *       max video playback resolution
     *
     * This method is exposed for unit testing.
     */
    protected void copyMediaFiles(ITestDevice device) throws DeviceNotAvailableException {
        if (mPushAll) {
            copyAll(device);
            return;
        }
        copyVideoFiles(device);
    }

    // copy video files of a resolution <= the device's maximum video playback resolution
    protected void copyVideoFiles(ITestDevice device) throws DeviceNotAvailableException {
        for (Resolution resolution : RESOLUTIONS) {
            if (resolution.width > mMaxRes.width) {
                CLog.i("Media file copying complete");
                return;
            }
            String deviceShortFilePath = mBaseDeviceShortDir + resolution.toString();
            String deviceFullFilePath = mBaseDeviceFullDir + resolution.toString();
            if (!device.doesFileExist(deviceShortFilePath, mCurrentUser)
                    || !device.doesFileExist(deviceFullFilePath, mCurrentUser)) {
                CLog.i("Copying files of resolution %s to device", resolution.toString());
                String localShortDirName = "bbb_short/" + resolution.toString();
                String localFullDirName = "bbb_full/" + resolution.toString();
                File localShortDir = new File(mLocalMediaPath, localShortDirName);
                File localFullDir = new File(mLocalMediaPath, localFullDirName);
                // push short directory of given resolution, if not present on device
                if (!device.doesFileExist(deviceShortFilePath, mCurrentUser)) {
                    device.pushDir(localShortDir, deviceShortFilePath);
                }
                // push full directory of given resolution, if not present on device
                if (!device.doesFileExist(deviceFullFilePath, mCurrentUser)) {
                    device.pushDir(localFullDir, deviceFullFilePath);
                }
            }
        }
    }

    // copy everything from the host directory to the device
    protected void copyAll(ITestDevice device) throws DeviceNotAvailableException {
        if (!device.doesFileExist(mBaseDeviceModuleDir, mCurrentUser)) {
            CLog.i("Copying files to device");
            device.pushDir(new File(mLocalMediaPath), mBaseDeviceModuleDir);
        }
    }

    // Initialize directory strings where media files live on device
    protected void setMountPoint(ITestDevice device) {
        String mountPoint = device.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mBaseDeviceModuleDir = String.format("%s/test/%s/", mountPoint, mMediaFolderName);
        if (mUseLegacyFolderStructure) {
            mBaseDeviceShortDir = String.format("%s/test/bbb_short/", mountPoint);
            mBaseDeviceFullDir = String.format("%s/test/bbb_full/", mountPoint);
        } else {
            mBaseDeviceShortDir = String.format("%s/test/%s/bbb_short/", mountPoint,
                    mMediaFolderName);
            mBaseDeviceFullDir = String.format("%s/test/%s/bbb_full/", mountPoint,
                    mMediaFolderName);
        }
    }

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        IBuildInfo buildInfo = testInfo.getBuildInfo();
        mCurrentUser = device.getCurrentUser();
        if (mSkipMediaDownload) {
            CLog.i("Skipping media preparation");
            return; // skip this precondition
        }

        if (!mMediaDownloadOnly) {
            setMountPoint(device);
            if (!mPushAll) {
                setMaxRes(testInfo); // max resolution only applies to video files
            }
            if (mediaFilesExistOnDevice(device)) {
                // if files already on device, do nothing
                CLog.i("Media files found on the device");
                return;
            }
        }

        if (mLocalMediaPath == null) {
            // Option 'local-media-path' has not been defined
            // Get directory to store media files on this host
            File mediaFolder = downloadMediaToHost(device, buildInfo);
            // set mLocalMediaPath to extraction location of media files
            updateLocalMediaPath(device, mediaFolder);
        }
        CLog.i("Media files located on host at: " + mLocalMediaPath);
        if (!mMediaDownloadOnly) {
            copyMediaFiles(device);
        }
    }

    @VisibleForTesting
    protected void setUserId(int testUser) {
        mCurrentUser = testUser;
    }

    // Initialize maximum resolution of media files to copy
    @VisibleForTesting
    protected void setMaxRes(TestInformation testInfo)
            throws DeviceNotAvailableException, TargetSetupError {
        ITestInvocationListener listener = new MediaPreparerListener();
        ITestDevice device = testInfo.getDevice();
        IBuildInfo buildInfo = testInfo.getBuildInfo();
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        File apkFile = null;
        try {
            apkFile = buildHelper.getTestFile(APP_APK);
            if (!apkFile.exists()) {
                // handle both missing tests dir and missing APK in catch block
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException e) {
            throw new TargetSetupError(
                    String.format("Could not find '%s'", APP_APK),
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }
        if (device.getAppPackageInfo(APP_PKG_NAME) != null) {
            device.uninstallPackage(APP_PKG_NAME);
        }
        CLog.i("Instrumenting package %s:", APP_PKG_NAME);
        // We usually discourage from referencing the content provider utility
        // but in this case, the helper needs it installed.
        new ContentProviderHandler(device).setUp();
        AndroidJUnitTest instrTest = new AndroidJUnitTest();
        instrTest.setDevice(device);
        instrTest.setInstallFile(apkFile);
        instrTest.setPackageName(APP_PKG_NAME);
        String moduleName = getDynamicModuleName();
        if (moduleName != null) {
            instrTest.addInstrumentationArg("module-name", moduleName);
        }
        // AndroidJUnitTest requires a IConfiguration to work properly, add a stub to this
        // implementation to avoid an NPE.
        instrTest.setConfiguration(new Configuration("stub", "stub"));
        instrTest.run(testInfo, listener);
        if (mFailureStackTrace != null) {
            throw new TargetSetupError(
                    String.format(
                            "Retrieving maximum resolution failed with trace:\n%s",
                            mFailureStackTrace),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        } else if (mMaxRes == null) {
            throw new TargetSetupError(
                    String.format("Failed to pull resolution capabilities from device"),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    /* Special listener for setting MediaPreparer instance variable values */
    private class MediaPreparerListener implements ITestInvocationListener {

        @Override
        public void testEnded(TestDescription test, HashMap<String, Metric> metrics) {
            Metric resMetric = metrics.get(RESOLUTION_STRING_KEY);
            if (resMetric != null) {
                mMaxRes = new Resolution(resMetric.getMeasurements().getSingleString());
            }
        }

        @Override
        public void testFailed(TestDescription test, String trace) {
            mFailureStackTrace = trace;
        }
    }

    @VisibleForTesting
    protected String getDynamicModuleName() throws TargetSetupError {
        String moduleName = null;
        boolean sameDevice = false;
        for (IDeviceConfiguration deviceConfig : mModuleConfiguration.getDeviceConfig()) {
            for (ITargetPreparer prep : deviceConfig.getTargetPreparers()) {
                if (prep instanceof DynamicConfigPusher) {
                    moduleName = ((DynamicConfigPusher) prep).createModuleName();
                    if (sameDevice) {
                        throw new TargetSetupError(
                                "DynamicConfigPusher needs to be configured before MediaPreparer"
                                        + " in your module configuration.",
                                InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
                    }
                }
                if (prep.equals(this)) {
                    sameDevice = true;
                    if (moduleName != null) {
                        return moduleName;
                    }
                }
            }
            moduleName = null;
            sameDevice = false;
        }
        return null;
    }
}
