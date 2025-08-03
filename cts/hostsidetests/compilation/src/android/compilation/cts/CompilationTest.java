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

package android.compilation.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.compilation.cts.annotation.CtsTestCase;
import android.content.pm.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceParameterizedRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.Pair;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.regex.Pattern;

import junitparams.Parameters;

/**
 * Compilation tests that don't require root access.
 */
@RunWith(DeviceParameterizedRunner.class)
@CtsTestCase
public class CompilationTest extends BaseHostJUnit4Test {
    private static final String STATUS_CHECKER_PKG = "android.compilation.cts.statuscheckerapp";
    private static final String STATUS_CHECKER_CLASS =
            "android.compilation.cts.statuscheckerapp.StatusCheckerAppTest";
    private static final String TEST_APP_PKG = "android.compilation.cts";
    private static final String TEST_APP_APK_RES = "/CtsCompilationApp.apk";
    private static final String TEST_APP_DM_RES = "/CtsCompilationApp.dm";
    private static final String TEST_APP_WITH_GOOD_PROFILE_RES =
            "/CtsCompilationApp_with_good_profile.apk";
    private static final String TEST_APP_WITH_BAD_PROFILE_RES =
            "/CtsCompilationApp_with_bad_profile.apk";
    private static final String TEST_APP_2_PKG = "android.compilation.cts.appusedbyotherapp";
    private static final String TEST_APP_2_APK_RES = "/AppUsedByOtherApp.apk";
    private static final String TEST_APP_2_DM_RES = "/AppUsedByOtherApp_1.dm";

    private Utils mUtils;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        mUtils = new Utils(getTestInformation());
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_APP_PKG);
        getDevice().uninstallPackage(TEST_APP_2_PKG);
    }

    @Test
    public void testCompile() throws Exception {
        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("checkStatus")
                              .setDisableHiddenApiCheck(true);

        mUtils.assertCommandSucceeds("pm compile -m speed -f " + STATUS_CHECKER_PKG);
        options.addInstrumentationArg("compiler-filter", "speed")
                .addInstrumentationArg("compilation-reason", "cmdline")
                .addInstrumentationArg("is-verified", "true")
                .addInstrumentationArg("is-optimized", "true")
                .addInstrumentationArg("is-fully-compiled", "true");
        assertThat(runDeviceTests(options)).isTrue();

        mUtils.assertCommandSucceeds("pm compile -m verify -f " + STATUS_CHECKER_PKG);
        options.addInstrumentationArg("compiler-filter", "verify")
                .addInstrumentationArg("compilation-reason", "cmdline")
                .addInstrumentationArg("is-verified", "true")
                .addInstrumentationArg("is-optimized", "false")
                .addInstrumentationArg("is-fully-compiled", "false");
        assertThat(runDeviceTests(options)).isTrue();

        mUtils.assertCommandSucceeds("pm delete-dexopt " + STATUS_CHECKER_PKG);
        options.addInstrumentationArg("compiler-filter", "run-from-apk")
                .addInstrumentationArg("compilation-reason", "unknown")
                .addInstrumentationArg("is-verified", "false")
                .addInstrumentationArg("is-optimized", "false")
                .addInstrumentationArg("is-fully-compiled", "false");
        assertThat(runDeviceTests(options)).isTrue();
    }

    // TODO(b/258223472): Remove this test once ART Service is the only dexopt implementation.
    @Test
    public void testArtService() throws Exception {
        assertThat(getDevice().getProperty("dalvik.vm.useartservice")).isEqualTo("true");

        mUtils.installFromResources(getAbi(), TEST_APP_APK_RES, TEST_APP_DM_RES);

        // Clear all user data, including the profile.
        mUtils.assertCommandSucceeds("pm clear " + TEST_APP_PKG);

        // Overwrite the artifacts compiled with the profile.
        mUtils.assertCommandSucceeds("pm compile -m verify -f " + TEST_APP_PKG);

        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        assertThat(dump).contains("[status=verify]");
        assertThat(dump).doesNotContain("[status=speed-profile]");

        dump = mUtils.assertCommandSucceeds("dumpsys package " + TEST_APP_PKG);
        assertThat(dump).contains("[status=verify]");
        assertThat(dump).doesNotContain("[status=speed-profile]");

        // Compile the app. It should use the profile in the DM file.
        mUtils.assertCommandSucceeds("pm compile -m speed-profile " + TEST_APP_PKG);

        dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        assertThat(dump).doesNotContain("[status=verify]");
        assertThat(dump).contains("[status=speed-profile]");

        dump = mUtils.assertCommandSucceeds("dumpsys package " + TEST_APP_PKG);
        assertThat(dump).doesNotContain("[status=verify]");
        assertThat(dump).contains("[status=speed-profile]");
    }

    @Test
    @Parameters({"secondary.jar", "secondary"})
    public void testCompileSecondaryDex(String filename) throws Exception {
        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("createAndLoadSecondaryDex")
                              .addInstrumentationArg("secondary-dex-filename", filename);
        assertThat(runDeviceTests(options)).isTrue();

        // Verify that the secondary dex file is recorded.
        String dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), ".*?");

        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m speed -f " + STATUS_CHECKER_PKG);
        dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "speed");

        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m verify -f " + STATUS_CHECKER_PKG);
        dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "verify");

        mUtils.assertCommandSucceeds("pm delete-dexopt " + STATUS_CHECKER_PKG);
        dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "run-from-apk");
    }

    @Test
    public void testCompileSecondaryDexUnsupportedClassLoader() throws Exception {
        String filename = "secondary-unsupported-clc.jar";
        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("createAndLoadSecondaryDexUnsupportedClassLoader")
                              .addInstrumentationArg("secondary-dex-filename", filename);
        assertThat(runDeviceTests(options)).isTrue();

        // "speed" should be downgraded to "verify" because the CLC is unsupported.
        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m speed -f " + STATUS_CHECKER_PKG);
        String dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "verify");
    }

    @Test
    public void testSecondaryDexReporting() throws Exception {
        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("testSecondaryDexReporting")
                              .setDisableHiddenApiCheck(true);
        assertThat(runDeviceTests(options)).isTrue();

        String dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_1.apk");
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_2.apk");
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_3.apk");
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_4.apk");
        Utils.dumpContainsDexFile(dump, "reported_good_1.apk");
        Utils.dumpContainsDexFile(dump, "reported_good_2.apk");
        Utils.dumpContainsDexFile(dump, "reported_good_3.apk");

        // Check that ART Service doesn't crash on various operations after invalid dex paths and
        // class loader contexts are reported.
        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m verify -f " + STATUS_CHECKER_PKG);
        mUtils.assertCommandSucceeds("pm art clear-app-profiles " + STATUS_CHECKER_PKG);
        mUtils.assertCommandSucceeds("pm art cleanup");
    }

    @Test
    public void testGetDexFileOutputPaths() throws Exception {
        mUtils.assertCommandSucceeds("pm compile -m verify -f " + STATUS_CHECKER_PKG);

        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("testGetDexFileOutputPaths")
                              .setDisableHiddenApiCheck(true);
        assertThat(runDeviceTests(options)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testExternalProfileValidationOk() throws Exception {
        mUtils.installFromResources(getAbi(), TEST_APP_APK_RES, TEST_APP_DM_RES);
    }

    /** Verifies that adb install-multiple fails when the APK and the DM file don't match. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testExternalProfileValidationFailed() throws Exception {
        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResources(getAbi(), TEST_APP_APK_RES, TEST_APP_2_DM_RES);
        });
        assertThat(throwable).hasMessageThat().contains(
                "Error occurred during dexopt when processing external profiles:");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testExternalProfileValidationMultiPackageOk() throws Exception {
        mUtils.installFromResourcesMultiPackage(getAbi(),
                List.of(List.of(Pair.create(TEST_APP_APK_RES, TEST_APP_DM_RES)),
                        List.of(Pair.create(TEST_APP_2_APK_RES, TEST_APP_2_DM_RES))));
    }

    /**
     * Verifies that adb install-multi-package fails when the mismatch happens on one of the APK-DM
     * pairs.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testExternalProfileValidationMultiPackageFailed() throws Exception {
        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResourcesMultiPackage(getAbi(),
                    List.of(List.of(Pair.create(TEST_APP_APK_RES, TEST_APP_DM_RES)),
                            List.of(Pair.create(TEST_APP_2_APK_RES, TEST_APP_DM_RES))));
        });

        assertThat(Utils.countSubstringOccurrence(throwable.getMessage(),
                           "Error occurred during dexopt when processing external profiles:"))
                .isEqualTo(1);
    }

    @Test
    public void testEmbeddedProfileOk() throws Exception {
        mUtils.assumeSysPropTrue("dalvik.vm.features.embedded_profile");
        mUtils.installFromResources(getAbi(), TEST_APP_WITH_GOOD_PROFILE_RES);
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "speed-profile");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testEmbeddedProfileFailed() throws Exception {
        mUtils.assumeSysPropTrue("dalvik.vm.features.embedded_profile");
        Throwable throwable = assertThrows(Throwable.class,
                () -> { mUtils.installFromResources(getAbi(), TEST_APP_WITH_BAD_PROFILE_RES); });
        assertThat(throwable).hasMessageThat().contains(
                "Error occurred during dexopt when processing external profiles:");
    }

    /**
     * Verifies that adb install-multi-package fails with multiple error messages when multiple
     * APK-DM mismatches happen.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testExternalProfileValidationMultiPackageFailedMultipleErrors() throws Exception {
        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResourcesMultiPackage(getAbi(),
                    List.of(List.of(Pair.create(TEST_APP_APK_RES, TEST_APP_2_DM_RES)),
                            List.of(Pair.create(TEST_APP_2_APK_RES, TEST_APP_DM_RES))));
        });

        assertThat(Utils.countSubstringOccurrence(throwable.getMessage(),
                           "Error occurred during dexopt when processing external profiles:"))
                .isEqualTo(2);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testIgnoreDexoptProfile() throws Exception {
        // Both the APK and the DM have a good profile, but ART Service should use none of them.
        mUtils.installFromResourcesWithArgs(getAbi(), List.of("--ignore-dexopt-profile"),
                List.of(Pair.create(TEST_APP_WITH_GOOD_PROFILE_RES, TEST_APP_DM_RES)));
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "verify");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_USE_ART_SERVICE_V2)
    public void testIgnoreDexoptProfileNoValidation() throws Exception {
        // Both the APK and the DM have a bad profile, but ART Service should not complain.
        mUtils.installFromResourcesWithArgs(getAbi(), List.of("--ignore-dexopt-profile"),
                List.of(Pair.create(TEST_APP_WITH_BAD_PROFILE_RES, TEST_APP_2_DM_RES)));
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "verify");
    }

    private void checkDexoptStatus(String dump, String dexfilePattern, String statusPattern) {
        // Matches the dump output typically being:
        //     /data/user/0/android.compilation.cts.statuscheckerapp/secondary.jar
        //       x86_64: [status=speed] [reason=cmdline] [primary-abi]
        // The pattern is intentionally minimized to be as forward compatible as possible.
        // TODO(b/283447251): Use a machine-readable format.
        assertThat(dump).containsMatch(
                Pattern.compile(String.format("[\\s/](%s)(\\s[^\\n]*)?\\n[^\\n]*\\[status=(%s)\\]",
                        dexfilePattern, statusPattern)));
    }
}
