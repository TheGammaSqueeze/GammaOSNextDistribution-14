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
package android.extractnativelibs.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * TODO(b/147496159): add more tests.
 */
public class CtsExtractNativeLibsHostTestBase extends BaseHostJUnit4Test {
    static final String TEST_REMOTE_DIR = "/data/local/tmp/extract_native_libs_test";
    static final String TEST_APK_RESOURCE_PREFIX = "/prebuilt/";
    static final String TEST_HOST_TMP_DIR_PREFIX = "cts_extract_native_libs_host_test";

    static final String TEST_APK_NAME_BASE = "CtsExtractNativeLibsApp";
    static final String TEST_PKG_NAME_BASE = "com.android.cts.extractnativelibs.app";
    static final String TEST_NO_EXTRACT_PKG = TEST_PKG_NAME_BASE + ".noextract";
    static final String TEST_NO_EXTRACT_CLASS =
            TEST_NO_EXTRACT_PKG + ".ExtractNativeLibsFalseDeviceTest";
    static final String TEST_NO_EXTRACT_TEST = "testNativeLibsNotExtracted";

    static final String TEST_EXTRACT_PKG = TEST_PKG_NAME_BASE + ".extract";
    static final String TEST_EXTRACT_CLASS =
            TEST_EXTRACT_PKG + ".ExtractNativeLibsTrueDeviceTest";
    static final String TEST_EXTRACT_TEST = "testNativeLibsExtracted";

    static final String TEST_NATIVE_LIB_LOADED_TEST = "testNativeLibsLoaded";
    static final String IDSIG_SUFFIX = ".idsig";

    /** Setup test dir. */
    @Before
    public void setUp() throws Exception {
        getDevice().executeShellCommand("mkdir " + TEST_REMOTE_DIR);
    }

    /** Uninstall apps after tests. */
    @After
    public void cleanUp() throws Exception {
        uninstallPackage(getDevice(), TEST_NO_EXTRACT_PKG);
        uninstallPackage(getDevice(), TEST_EXTRACT_PKG);
        getDevice().executeShellCommand("rm -r " + TEST_REMOTE_DIR);
    }

    boolean isIncrementalInstallSupported() throws Exception {
        return "true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery"));
    }

    static String getTestApkName(boolean isExtractNativeLibs, String abiSuffix) {
        return TEST_APK_NAME_BASE + (isExtractNativeLibs ? "True" : "False") + abiSuffix + ".apk";
    }

    static String getTestPackageName(boolean isExtractNativeLibs) {
        return isExtractNativeLibs ? TEST_EXTRACT_PKG : TEST_NO_EXTRACT_PKG;
    }

    static String getTestClassName(boolean isExtractNativeLibs) {
        return isExtractNativeLibs ? TEST_EXTRACT_CLASS : TEST_NO_EXTRACT_CLASS;
    }

    final void installPackage(boolean isIncremental, String apkName) throws Exception {
        installPackage(isIncremental, apkName, "");
    }

    final void installPackage(boolean isIncremental, String apkName, String abi) throws Exception {
        if (isIncremental) {
            installPackageIncremental(apkName, abi);
        } else {
            installPackageLegacy(apkName, abi);
        }
    }

    final boolean checkNativeLibDir(boolean isExtractNativeLibs, String abi) throws Exception {
        if (isExtractNativeLibs) {
            return checkExtractedNativeLibDirForAbi(abi);
        } else {
            return runDeviceTests(
                    TEST_NO_EXTRACT_PKG, TEST_NO_EXTRACT_CLASS, TEST_NO_EXTRACT_TEST);
        }
    }

    File getFileFromResource(String filenameInResources) throws Exception {
        String fullResourceName = TEST_APK_RESOURCE_PREFIX + filenameInResources;
        File tempDir = FileUtil.createTempDir(TEST_HOST_TMP_DIR_PREFIX);
        File file = new File(tempDir, filenameInResources);
        InputStream in = getClass().getResourceAsStream(fullResourceName);
        if (in == null) {
            throw new IllegalArgumentException("Resource not found: " + fullResourceName);
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buf = new byte[65536];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            out.write(buf, 0, chunkSize);
        }
        out.close();
        return file;
    }

    private boolean runDeviceTestsWithArgs(String pkgName, String testClassName,
            String testMethodName, Map<String, String> testArgs) throws Exception {
        final String testRunner = "androidx.test.runner.AndroidJUnitRunner";
        final long defaultTestTimeoutMs = 60 * 1000L;
        final long defaultMaxTimeoutToOutputMs = 60 * 1000L; // 1min
        return runDeviceTests(getDevice(), testRunner, pkgName, testClassName, testMethodName,
                null, defaultTestTimeoutMs, defaultMaxTimeoutToOutputMs,
                0L, true, false, testArgs);
    }

    private void installPackageLegacy(String apkFileName, String abi)
            throws DeviceNotAvailableException, TargetSetupError {
        SuiteApkInstaller installer = new SuiteApkInstaller();
        installer.addTestFileName(apkFileName);
        final String abiFlag = createAbiFlag(abi);
        if (!abiFlag.isEmpty()) {
            installer.addInstallArg(abiFlag);
        }
        try {
            installer.setUp(getTestInformation());
        } catch (BuildError e) {
            throw new TargetSetupError(e.getMessage(), e, getDevice().getDeviceDescriptor());
        }
    }

    private boolean checkExtractedNativeLibDirForAbi(String abiSuffix) throws Exception {
        final String libAbi = getExpectedLibAbi(abiSuffix);
        assertNotNull(libAbi);
        final String expectedSubDirArg = "expectedSubDir";
        final String expectedNativeLibSubDir = AbiUtils.getArchForAbi(libAbi);
        final Map<String, String> testArgs = new HashMap<>();
        testArgs.put(expectedSubDirArg, expectedNativeLibSubDir);
        return runDeviceTestsWithArgs(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS, TEST_EXTRACT_TEST,
                testArgs);
    }

    /** Given the abi included in the APK, predict which abi libs will be installed
     * @param abiSuffix "64" means the APK contains only 64-bit native libs
     *                  "32" means the APK contains only 32-bit native libs
     *                  "Both" means the APK contains both 32-bit and 64-bit native libs
     * @return an ABI string from AbiUtils.ABI_*
     * @return an ABI string from AbiUtils.ABI_*
     */
    final String getExpectedLibAbi(String abiSuffix) {
        final String testAbi = getAbi().getName();
        final String testBitness = AbiUtils.getBitness(testAbi);
        final String libBitness;
        // Use 32-bit native libs if test only supports 32-bit or APK only has 32-libs native libs
        if (abiSuffix.equals("32") || testBitness.equals("32")) {
            libBitness = "32";
        } else {
            libBitness = "64";
        }
        final Set<String> libAbis = AbiUtils.getAbisForArch(AbiUtils.getBaseArchForAbi(testAbi));
        for (String libAbi : libAbis) {
            if (AbiUtils.getBitness(libAbi).equals(libBitness)) {
                return libAbi;
            }
        }
        return null;
    }

    /**
     * Get ABIs supported by the test APKs. For example, if the test ABI is "armeabi-v7a", the test
     * APKs should include native libs for "armeabi-v7a" and/or "arm64-v8a".
     */
    final Set<String> getApkAbis() throws Exception {
        String testApkBaseArch = AbiUtils.getArchForAbi(getAbi().getName());
        return AbiUtils.getAbisForArch(testApkBaseArch);
    }

    final String getDeviceAbi() throws Exception {
        return getDevice().getProperty("ro.product.cpu.abi");
    }

    /**
     * Device ABIs might includes native bridge ABIs that are different from base arch (e.g.,
     * emulators with NDK translations).
     */
    final Set<String> getDeviceAbis() throws Exception {
        String[] deviceAbis = getDevice().getProperty("ro.product.cpu.abilist").split(",");
        return new HashSet<>(Arrays.asList(deviceAbis));
    }

    final Set<String> getDeviceAbiSuffixes() throws Exception {
        HashSet<String> abiSuffixes = new HashSet<String>();
        for (String abi : getDeviceAbis()) {
            abiSuffixes.add(AbiUtils.getBitness(abi));
        }
        return abiSuffixes;
    }

    private void installPackageIncremental(String apkName, String abi) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(apkName);
        assertNotNull(apk);
        final File v4Signature = buildHelper.getTestFile(apkName + IDSIG_SUFFIX);
        assertNotNull(v4Signature);
        installPackageIncrementalFromFiles(apk, v4Signature, abi);
    }

    private String installPackageIncrementalFromFiles(File apk, File v4Signature, String abi)
            throws Exception {
        final String remoteApkPath = TEST_REMOTE_DIR + "/" + apk.getName();
        final String remoteIdsigPath = remoteApkPath + IDSIG_SUFFIX;
        assertTrue(getDevice().pushFile(apk, remoteApkPath));
        assertTrue(getDevice().pushFile(v4Signature, remoteIdsigPath));
        return getDevice().executeShellCommand("pm install-incremental "
                + createAbiFlag(abi)
                + " -t -g " + remoteApkPath);
    }

    private String createAbiFlag(String abi) {
        return abi.isEmpty() ? "" : ("--abi " + abi);
    }

    final String installIncrementalPackageFromResource(String apkFilenameInRes)
            throws Exception {
        final File apkFile = getFileFromResource(apkFilenameInRes);
        final File v4SignatureFile = getFileFromResource(
                apkFilenameInRes + IDSIG_SUFFIX);
        return installPackageIncrementalFromFiles(apkFile, v4SignatureFile, "");
    }
}
