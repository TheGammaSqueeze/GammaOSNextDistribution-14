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

package android.compat.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.INativeDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Testing utilities for parsing *CLASSPATH environ variables and shared libs on a test device.
 */
public final class Classpaths {

    private Classpaths() {
    }

    public enum ClasspathType {
        BOOTCLASSPATH,
        DEX2OATBOOTCLASSPATH,
        SYSTEMSERVERCLASSPATH,
        STANDALONE_SYSTEMSERVER_JARS,
    }

    private static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    /** Returns on device filepaths to the jars that are part of a given classpath. */
    public static ImmutableList<String> getJarsOnClasspath(INativeDevice device,
            ClasspathType classpath) throws DeviceNotAvailableException {
        CommandResult shellResult = device.executeShellV2Command("echo $" + classpath);
        assertThat(shellResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(shellResult.getExitCode()).isEqualTo(0);

        String value = shellResult.getStdout().trim();
        assertThat(value).isNotEmpty();
        return ImmutableList.copyOf(value.split(":"));
    }

    /** Returns {@link SharedLibraryInfo} about the shared libs available on the test device. */
    public static ImmutableList<SharedLibraryInfo> getSharedLibraryInfos(ITestDevice device,
            IBuildInfo buildInfo) throws DeviceNotAvailableException, FileNotFoundException {
        runDeviceTests(device, buildInfo, SharedLibraryInfo.HELPER_APP_APK,
                SharedLibraryInfo.HELPER_APP_PACKAGE, SharedLibraryInfo.HELPER_APP_CLASS);
        String remoteFile = "/sdcard/shared-libs.txt";
        String content;
        try {
            content = device.pullFileContents(remoteFile);
        } finally {
            device.deleteFile(remoteFile);
        }
        return SharedLibraryInfo.getSharedLibraryInfos(content);
    }

    /** Returns classes defined a given jar file on the test device. */
    public static ImmutableSet<ClassDef> getClassDefsFromJar(File jar) throws IOException {
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(jar, Opcodes.getDefault());
        ImmutableSet.Builder<ClassDef> set = ImmutableSet.builder();
        for (String dexName : container.getDexEntryNames()) {
            DexBackedDexFile dexFile = container.getEntry(dexName).getDexFile();
            set.addAll(Objects.requireNonNull(dexFile).getClasses());
        }
        return set.build();
    }

    private static void runDeviceTests(ITestDevice device, IBuildInfo buildInfo, String apkName,
            String packageName, String className) throws DeviceNotAvailableException,
            FileNotFoundException {
        try {
            final CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
            final String installError = device.installPackage(buildHelper.getTestFile(apkName),
                    false);
            assertWithMessage("Failed to install %s due to: %s", apkName, installError).
                    that(installError).isNull();
            // Trigger helper app to collect and write info about shared libraries on the device.
            final RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                    TEST_RUNNER, device.getIDevice());
            testRunner.setClassName(className);
            final CollectingTestListener listener = new CollectingTestListener();
            assertThat(device.runInstrumentationTests(testRunner, listener)).isTrue();
            final TestRunResult result = listener.getCurrentRunResults();
            assertWithMessage("Failed to successfully run device tests for " + result.getName()
                    + ": " + result.getRunFailureMessage())
                    .that(result.isRunFailure()).isFalse();
            assertWithMessage("No tests were run!").that(result.getNumTests()).isGreaterThan(0);
            StringBuilder errorBuilder = new StringBuilder("on-device tests failed:\n");
            for (Map.Entry<TestDescription, TestResult> resultEntry :
                    result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            assertWithMessage(errorBuilder.toString()).that(result.hasFailedTests()).isFalse();
        } finally {
            device.uninstallPackage(packageName);
        }
    }

}
