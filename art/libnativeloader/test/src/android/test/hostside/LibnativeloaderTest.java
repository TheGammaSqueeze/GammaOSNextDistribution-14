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

package android.test.hostside;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;

import com.google.common.io.ByteStreams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Test libnativeloader behavior for apps and libs in various partitions by overlaying them over
 * the system partitions. Requires root.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class LibnativeloaderTest extends BaseHostJUnit4Test {
    private static final String TAG = "LibnativeloaderTest";
    private static final String CLEANUP_PATHS_KEY = TAG + ":CLEANUP_PATHS";
    private static final String LOG_FILE_NAME = "TestActivity.log";

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        DeviceContext ctx = new DeviceContext(testInfo);

        // A soft reboot is slow, so do setup for all tests and reboot once.

        File libContainerApk = ctx.mBuildHelper.getTestFile("library_container_app.apk");
        try (ZipFile libApk = new ZipFile(libContainerApk)) {
            ctx.pushExtendedPublicSystemOemLibs(libApk);
            ctx.pushExtendedPublicProductLibs(libApk);
            ctx.pushPrivateLibs(libApk);
        }
        ctx.pushSharedLib(
                "/system", "android.test.systemsharedlib", "libnativeloader_system_shared_lib.jar");
        ctx.pushSharedLib("/system_ext", "android.test.systemextsharedlib",
                "libnativeloader_system_ext_shared_lib.jar");
        ctx.pushSharedLib("/product", "android.test.productsharedlib",
                "libnativeloader_product_shared_lib.jar");
        ctx.pushSharedLib(
                "/vendor", "android.test.vendorsharedlib", "libnativeloader_vendor_shared_lib.jar");

        // "Install" apps in various partitions through plain adb push followed by a soft reboot. We
        // need them in these locations to test library loading restrictions, so for all except
        // loadlibrarytest_data_app we cannot use ITestDevice.installPackage for it since it only
        // installs in /data.

        // For testSystemPrivApp
        ctx.pushApk("loadlibrarytest_system_priv_app", "/system/priv-app");

        // For testSystemApp
        ctx.pushApk("loadlibrarytest_system_app", "/system/app");

        // For testSystemExtApp
        ctx.pushApk("loadlibrarytest_system_ext_app", "/system_ext/app");

        // For testProductApp
        ctx.pushApk("loadlibrarytest_product_app", "/product/app");

        // For testVendorApp
        ctx.pushApk("loadlibrarytest_vendor_app", "/vendor/app");

        ctx.softReboot();

        // For testDataApp. Install this the normal way after the system server restart.
        ctx.installPackage("loadlibrarytest_data_app");

        testInfo.properties().put(CLEANUP_PATHS_KEY, ctx.mCleanup.getPathList());
    }

    @AfterClassWithInfo
    public static void afterClassWithDevice(TestInformation testInfo) throws Exception {
        DeviceContext ctx = new DeviceContext(testInfo);

        // Uninstall loadlibrarytest_data_app.
        ctx.mDevice.uninstallPackage("android.test.app.data");

        String cleanupPathList = testInfo.properties().get(CLEANUP_PATHS_KEY);
        CleanupPaths cleanup = new CleanupPaths(ctx.mDevice, cleanupPathList);
        cleanup.cleanup();
    }

    @Test
    public void testSystemPrivApp() throws Exception {
        // There's currently no difference in the tests between /system/priv-app and /system/app, so
        // let's reuse the same one.
        runTests("android.test.app.system_priv", "android.test.app.SystemAppTest");
    }

    @Test
    public void testSystemApp() throws Exception {
        runTests("android.test.app.system", "android.test.app.SystemAppTest");
    }

    @Test
    public void testSystemExtApp() throws Exception {
        // /system_ext should behave the same as /system, so run the same test class there.
        runTests("android.test.app.system_ext", "android.test.app.SystemAppTest");
    }

    @Test
    public void testProductApp() throws Exception {
        runTests("android.test.app.product", "android.test.app.ProductAppTest");
    }

    @Test
    public void testVendorApp() throws Exception {
        runTests("android.test.app.vendor", "android.test.app.VendorAppTest");
    }

    @Test
    public void testDataApp() throws Exception {
        runTests("android.test.app.data", "android.test.app.DataAppTest");
    }

    private void runTests(String pkgName, String testClassName) throws Exception {
        DeviceContext ctx = new DeviceContext(getTestInformation());
        var options = new DeviceTestRunOptions(pkgName)
                              .setTestClassName(testClassName)
                              .addInstrumentationArg("libDirName", ctx.libDirName());
        runDeviceTests(options);
    }

    // Utility class that keeps track of a set of paths the need to be deleted after testing.
    private static class CleanupPaths {
        private ITestDevice mDevice;
        private List<String> mCleanupPaths;

        CleanupPaths(ITestDevice device) {
            mDevice = device;
            mCleanupPaths = new ArrayList<String>();
        }

        CleanupPaths(ITestDevice device, String pathList) {
            mDevice = device;
            mCleanupPaths = Arrays.asList(pathList.split(":"));
        }

        String getPathList() { return String.join(":", mCleanupPaths); }

        // Adds the given path, or its topmost nonexisting parent directory, to the list of paths to
        // clean up.
        void addPath(String devicePath) throws DeviceNotAvailableException {
            File path = new File(devicePath);
            while (true) {
                File parentPath = path.getParentFile();
                if (parentPath == null || mDevice.doesFileExist(parentPath.toString())) {
                    break;
                }
                path = parentPath;
            }
            String nonExistingPath = path.toString();
            if (!mCleanupPaths.contains(nonExistingPath)) {
                mCleanupPaths.add(nonExistingPath);
            }
        }

        void cleanup() throws DeviceNotAvailableException {
            // Clean up in reverse order in case several pushed files were in the same nonexisting
            // directory.
            for (int i = mCleanupPaths.size() - 1; i >= 0; --i) {
                mDevice.deleteFile(mCleanupPaths.get(i));
            }
        }
    }

    // Class for code that needs an ITestDevice. It may be instantiated both in tests and in
    // (Before|After)ClassWithInfo.
    private static class DeviceContext implements AutoCloseable {
        IInvocationContext mContext;
        ITestDevice mDevice;
        CompatibilityBuildHelper mBuildHelper;
        CleanupPaths mCleanup;
        private String mTestArch;

        DeviceContext(TestInformation testInfo) {
            mContext = testInfo.getContext();
            mDevice = testInfo.getDevice();
            mBuildHelper = new CompatibilityBuildHelper(testInfo.getBuildInfo());
            mCleanup = new CleanupPaths(mDevice);
        }

        public void close() throws DeviceNotAvailableException { mCleanup.cleanup(); }

        // Helper class to both push a library to device and record it in a public.libraries-xxx.txt
        // file.
        class PublicLibs {
            private ZipFile mLibApk;
            private List<String> mPublicLibs = new ArrayList<String>();

            PublicLibs(ZipFile libApk) {
                mLibApk = libApk;
            }

            void addLib(String libName, String destDir, String destName) throws Exception {
                pushNativeTestLib(mLibApk, libName, destDir + "/" + destName);
                mPublicLibs.add(destName);
            }

            void pushPublicLibrariesFile(String path) throws DeviceNotAvailableException {
                pushString(mPublicLibs.stream().collect(Collectors.joining("\n")) + "\n", path);
            }
        }

        void pushExtendedPublicSystemOemLibs(ZipFile libApk) throws Exception {
            var oem1Libs = new PublicLibs(libApk);
            // Push libsystem_extpub<n>.oem1.so for each test. Since we cannot unload them, we need
            // a fresh never-before-loaded library in each loadLibrary call.
            for (int i = 1; i <= 3; ++i) {
                oem1Libs.addLib("libsystem_testlib.so", "/system/${LIB}",
                        "libsystem_extpub" + i + ".oem1.so");
            }
            oem1Libs.addLib("libsystem_testlib.so", "/system/${LIB}", "libsystem_extpub.oem1.so");
            oem1Libs.pushPublicLibrariesFile("/system/etc/public.libraries-oem1.txt");

            var oem2Libs = new PublicLibs(libApk);
            oem2Libs.addLib("libsystem_testlib.so", "/system/${LIB}", "libsystem_extpub.oem2.so");
            // libextpub_nouses.oem2.so is a library that the test apps don't have
            // <uses-native-library> dependencies for.
            oem2Libs.addLib(
                    "libsystem_testlib.so", "/system/${LIB}", "libsystem_extpub_nouses.oem2.so");
            oem2Libs.pushPublicLibrariesFile("/system/etc/public.libraries-oem2.txt");
        }

        void pushExtendedPublicProductLibs(ZipFile libApk) throws Exception {
            var product1Libs = new PublicLibs(libApk);
            // Push libproduct_extpub<n>.product1.so for each test. Since we cannot unload them, we
            // need a fresh never-before-loaded library in each loadLibrary call.
            for (int i = 1; i <= 3; ++i) {
                product1Libs.addLib("libproduct_testlib.so", "/product/${LIB}",
                        "libproduct_extpub" + i + ".product1.so");
            }
            product1Libs.addLib(
                    "libproduct_testlib.so", "/product/${LIB}", "libproduct_extpub.product1.so");
            product1Libs.pushPublicLibrariesFile("/product/etc/public.libraries-product1.txt");
        }

        void pushPrivateLibs(ZipFile libApk) throws Exception {
            // Push the libraries once for each test. Since we cannot unload them, we need a fresh
            // never-before-loaded library in each loadLibrary call.
            //
            // Remember to update testPrivateLibsExist in TestUtils.java when
            // the number of libraries changes.
            for (int i = 1; i <= 10; ++i) {
                pushNativeTestLib(libApk, "libsystem_testlib.so",
                        "/system/${LIB}/libsystem_private" + i + ".so");
                pushNativeTestLib(libApk, "libsystem_testlib.so",
                        "/system_ext/${LIB}/libsystemext_private" + i + ".so");
                pushNativeTestLib(libApk, "libproduct_testlib.so",
                        "/product/${LIB}/libproduct_private" + i + ".so");
                pushNativeTestLib(libApk, "libvendor_testlib.so",
                        "/vendor/${LIB}/libvendor_private" + i + ".so");
            }
        }

        void pushSharedLib(String partitionDir, String packageName, String buildJarName)
                throws Exception {
            String path = partitionDir + "/framework/" + packageName + ".jar";
            pushFile(buildJarName, path);
            // This permissions xml file is necessary to make it possible to depend on the shared
            // library from the test app, even if it's in the same partition. It makes the library
            // public to apps in other partitions as well, which is more than we need, but that
            // being the case we test all shared libraries from all apps.
            pushString("<permissions>\n"
                            + "<library name=\"" + packageName + "\" file=\"" + path + "\" />\n"
                            + "</permissions>\n",
                    partitionDir + "/etc/permissions/" + packageName + ".xml");
        }

        void softReboot() throws DeviceNotAvailableException {
            assertCommandSucceeds("setprop dev.bootcomplete 0");
            assertCommandSucceeds("stop");
            assertCommandSucceeds("start");
            mDevice.waitForDeviceAvailable();
        }

        String getTestArch() throws DeviceNotAvailableException {
            if (mTestArch == null) {
                IAbi abi = mContext.getConfigurationDescriptor().getAbi();
                mTestArch = abi != null ? abi.getName()
                                        : assertCommandSucceeds("getprop ro.product.cpu.abi");
            }
            return mTestArch;
        }

        String libDirName() throws DeviceNotAvailableException {
            return getTestArch().contains("64") ? "lib64" : "lib";
        }

        // Pushes the given file contents to the device at the given destination path. destPath is
        // assumed to have no risk of overlapping with existing files, and is deleted in tearDown(),
        // along with any directory levels that had to be created.
        void pushString(String fileContents, String destPath) throws DeviceNotAvailableException {
            mCleanup.addPath(destPath);
            assertThat(mDevice.pushString(fileContents, destPath)).isTrue();
        }

        // Like pushString, but pushes a data file included in the host test.
        void pushFile(String fileName, String destPath) throws Exception {
            mCleanup.addPath(destPath);
            assertThat(mDevice.pushFile(mBuildHelper.getTestFile(fileName), destPath)).isTrue();
        }

        void pushApk(String apkBaseName, String destPath) throws Exception {
            pushFile(apkBaseName + ".apk",
                    destPath + "/" + apkBaseName + "/" + apkBaseName + ".apk");
        }

        // Like pushString, but extracts libnativeloader_testlib.so from the library_container_app
        // APK and pushes it to destPath. "${LIB}" is replaced with "lib" or "lib64" as appropriate.
        void pushNativeTestLib(ZipFile libApk, String libName, String destPath) throws Exception {
            String libApkPath = "lib/" + getTestArch() + "/" + libName;
            ZipEntry entry = libApk.getEntry(libApkPath);
            assertWithMessage("Failed to find " + libApkPath + " in library_container_app.apk")
                    .that(entry)
                    .isNotNull();

            File libraryTempFile;
            try (InputStream inStream = libApk.getInputStream(entry)) {
                libraryTempFile = writeStreamToTempFile(libName, inStream);
            }

            destPath = destPath.replace("${LIB}", libDirName());

            mCleanup.addPath(destPath);
            assertThat(mDevice.pushFile(libraryTempFile, destPath)).isTrue();
        }

        void installPackage(String apkBaseName) throws Exception {
            assertThat(mDevice.installPackage(mBuildHelper.getTestFile(apkBaseName + ".apk"),
                               false /* reinstall */))
                    .isNull();
        }

        String assertCommandSucceeds(String command) throws DeviceNotAvailableException {
            CommandResult result = mDevice.executeShellV2Command(command);
            assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
            // Remove trailing \n's.
            return result.getStdout().trim();
        }
    }

    static private File writeStreamToTempFile(String tempFileBaseName, InputStream inStream)
            throws Exception {
        File hostTempFile = File.createTempFile(tempFileBaseName, null);
        try (FileOutputStream outStream = new FileOutputStream(hostTempFile)) {
            ByteStreams.copy(inStream, outStream);
        }
        return hostTempFile;
    }
}
