/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various integration tests for dex to oat compilation, with or without profiles.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AdbRootDependentCompilationTest extends BaseHostJUnit4Test {
    private static final String APPLICATION_PACKAGE = "android.compilation.cts";
    private static final String APP_USED_BY_OTHER_APP_PACKAGE =
            "android.compilation.cts.appusedbyotherapp";
    private static final String APP_USING_OTHER_APP_PACKAGE =
            "android.compilation.cts.appusingotherapp";
    private static final String STATUS_CHECKER_PKG = "android.compilation.cts.statuscheckerapp";
    private static final int PERMISSIONS_LENGTH = 10;
    private static final int READ_OTHER = 7;
    private static final String PACKAGE_DEX_USAGE_PATH = "/data/system/package-dex-usage.pb";
    private static final String PACKAGE_DEX_USAGE_BACKUP_PATH =
            "/data/local/tmp/package-dex-usage.pb.bak";

    enum ProfileLocation {
        CUR("/data/misc/profiles/cur/0/"),
        REF("/data/misc/profiles/ref/");

        private String directory;

        ProfileLocation(String directory) {
            this.directory = directory;
        }

        public String getDirectory(String packageName) {
            return directory + packageName;
        }

        public String getPath(String packageName) {
            return directory + packageName + "/primary.prof";
        }
    }

    private ITestDevice mDevice;
    private Utils mUtils;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        mUtils = new Utils(getTestInformation());

        mUtils.installFromResources(getAbi(), "/CtsCompilationApp.apk");
    }

    @After
    public void tearDown() throws Exception {
        mDevice.uninstallPackage(APPLICATION_PACKAGE);
        mDevice.uninstallPackage(APP_USED_BY_OTHER_APP_PACKAGE);
        mDevice.uninstallPackage(APP_USING_OTHER_APP_PACKAGE);
    }

    /**
     * Tests compilation using {@code -r bg-dexopt -f}.
     */
    @Test
    public void testCompile_bgDexopt() throws Exception {
        resetProfileState(APPLICATION_PACKAGE);

        // Copy the profile to the reference location so that the bg-dexopt
        // can actually do work if it's configured to speed-profile.
        for (ProfileLocation profileLocation : EnumSet.of(ProfileLocation.REF)) {
            writeSystemManagedProfile(
                    "/CtsCompilationApp.prof", profileLocation, APPLICATION_PACKAGE);
        }

        // Usually "speed-profile"
        String expectedInstallFilter =
                Objects.requireNonNull(mDevice.getProperty("pm.dexopt.install"));
        if (expectedInstallFilter.equals("speed-profile")) {
            // If the filter is speed-profile but no profile is present, the compiler
            // will change it to verify.
            expectedInstallFilter = "verify";
        }
        // Usually "speed-profile"
        String expectedBgDexoptFilter =
                Objects.requireNonNull(mDevice.getProperty("pm.dexopt.bg-dexopt"));

        String odexPath = getOdexFilePath(APPLICATION_PACKAGE);
        assertEquals(expectedInstallFilter, getCompilerFilter(odexPath));

        // Without -f, the compiler would only run if it judged the bg-dexopt filter to
        // be "better" than the install filter. However manufacturers can change those
        // values so we don't want to depend here on the resulting filter being better.
        executeCompile(APPLICATION_PACKAGE, "-r", "bg-dexopt", "-f");

        assertEquals(expectedBgDexoptFilter, getCompilerFilter(odexPath));
    }

    /*
     The tests below test the remaining combinations of the "ref" (reference) and
     "cur" (current) profile being available. The "cur" profile gets moved/merged
     into the "ref" profile when it differs enough; as of 2016-05-10, "differs
     enough" is based on number of methods and classes in profile_assistant.cc.

     No nonempty profile exists right after an app is installed.
     Once the app runs, a profile will get collected in "cur" first but
     may make it to "ref" later. While the profile is being processed by
     profile_assistant, it may only be available in "ref".
     */

    @Test
    public void testCompile_noProfile() throws Exception {
        compileWithProfilesAndCheckFilter(false /* expectOdexChange */,
                EnumSet.noneOf(ProfileLocation.class));
    }

    @Test
    public void testCompile_curProfile() throws Exception {
        compileWithProfilesAndCheckFilter(true  /* expectOdexChange */,
                EnumSet.of(ProfileLocation.CUR));
        assertTrue("ref profile should have been created by the compiler",
                mDevice.doesFileExist(ProfileLocation.REF.getPath(APPLICATION_PACKAGE)));
    }

    @Test
    public void testCompile_refProfile() throws Exception {
        compileWithProfilesAndCheckFilter(true /* expectOdexChange */,
                 EnumSet.of(ProfileLocation.REF));
        // expect a change in odex because the of the change form
        // verify -> speed-profile
    }

    @Test
    public void testCompile_curAndRefProfile() throws Exception {
        compileWithProfilesAndCheckFilter(true /* expectOdexChange */,
                EnumSet.of(ProfileLocation.CUR, ProfileLocation.REF));
        // expect a change in odex because the of the change form
        // verify -> speed-profile
    }

    /**
     * Tests how compilation of an app used by other apps is handled.
     */
    @Test
    public void testCompile_usedByOtherApps() throws Exception {
        mUtils.installFromResources(getAbi(), "/AppUsedByOtherApp.apk", "/AppUsedByOtherApp_1.dm");
        mUtils.installFromResources(getAbi(), "/AppUsingOtherApp.apk");

        String odexFilePath = getOdexFilePath(APP_USED_BY_OTHER_APP_PACKAGE);
        // Initially, the app should be compiled with the cloud profile, and the odex file should be
        // public.
        assertThat(getCompilerFilter(odexFilePath)).isEqualTo("speed-profile");
        assertFileIsPublic(odexFilePath);
        assertThat(getCompiledMethods(odexFilePath))
                .containsExactly("android.compilation.cts.appusedbyotherapp.MyActivity.method2()");

        // Simulate that the app profile has changed.
        resetProfileState(APP_USED_BY_OTHER_APP_PACKAGE);
        writeSystemManagedProfile(
                "/AppUsedByOtherApp_2.prof", ProfileLocation.REF, APP_USED_BY_OTHER_APP_PACKAGE);

        executeCompile(APP_USED_BY_OTHER_APP_PACKAGE, "-m", "speed-profile", "-f");
        // Right now, the app hasn't been used by any other app yet. It should be compiled with the
        // new profile, and the odex file should be private.
        assertThat(getCompilerFilter(odexFilePath)).isEqualTo("speed-profile");
        assertFileIsPrivate(odexFilePath);
        assertThat(getCompiledMethods(odexFilePath)).containsExactly(
                "android.compilation.cts.appusedbyotherapp.MyActivity.method1()",
                "android.compilation.cts.appusedbyotherapp.MyActivity.method2()");

        executeCompile(APP_USED_BY_OTHER_APP_PACKAGE, "-m", "verify");
        // The app should not be re-compiled with a worse compiler filter even if the odex file can
        // be public after then.
        assertThat(getCompilerFilter(odexFilePath)).isEqualTo("speed-profile");

        DeviceTestRunOptions options = new DeviceTestRunOptions(APP_USING_OTHER_APP_PACKAGE);
        options.setTestClassName(APP_USING_OTHER_APP_PACKAGE + ".UsingOtherAppTest");
        options.setTestMethodName("useOtherApp");
        runDeviceTests(options);

        executeCompile(APP_USED_BY_OTHER_APP_PACKAGE, "-m", "speed-profile");
        // Now, the app has been used by any other app. It should be compiled with the cloud
        // profile, and the odex file should be public.
        assertThat(getCompilerFilter(odexFilePath)).isEqualTo("speed-profile");
        assertFileIsPublic(odexFilePath);
        assertThat(getCompiledMethods(odexFilePath))
                .containsExactly("android.compilation.cts.appusedbyotherapp.MyActivity.method2()");
    }

    @Test
    public void testSecondaryDexUseLoading() throws Exception {
        mUtils.assertCommandSucceeds(
                String.format("cp %s %s", PACKAGE_DEX_USAGE_PATH, PACKAGE_DEX_USAGE_BACKUP_PATH));
        try {
            mUtils.pushFromResource("/package-dex-usage.pb", PACKAGE_DEX_USAGE_PATH);
            applyPackageDexUsageChanges();

            String dump = mUtils.assertCommandSucceeds("pm art dump " + STATUS_CHECKER_PKG);
            Utils.dumpDoesNotContainDexFile(dump, "bad_1.apk");
            Utils.dumpDoesNotContainDexFile(dump, "bad_2.apk");
            Utils.dumpDoesNotContainDexFile(dump, "bad_3.apk");
            Utils.dumpDoesNotContainDexFile(dump, "bad_4.apk");
            Utils.dumpContainsDexFile(dump, "good_1.apk");
            Utils.dumpContainsDexFile(dump, "good_2.apk");
            Utils.dumpContainsDexFile(dump, "good_3.apk");
        } finally {
            mUtils.assertCommandSucceeds(String.format(
                    "cp %s %s", PACKAGE_DEX_USAGE_BACKUP_PATH, PACKAGE_DEX_USAGE_PATH));
            applyPackageDexUsageChanges();
        }
    }

    /**
     * Places the profile in the specified locations, recompiles (without -f)
     * and checks the compiler-filter in the odex file.
     */
    private void compileWithProfilesAndCheckFilter(boolean expectOdexChange,
            Set<ProfileLocation> profileLocations) throws Exception {
        resetProfileState(APPLICATION_PACKAGE);

        executeCompile(APPLICATION_PACKAGE, "-m", "speed-profile", "-f");
        String odexFilePath = getOdexFilePath(APPLICATION_PACKAGE);
        String initialOdexFileContents = mDevice.pullFileContents(odexFilePath);
        // validity check
        assertWithMessage("empty odex file").that(initialOdexFileContents.length())
                .isGreaterThan(0);

        for (ProfileLocation profileLocation : profileLocations) {
            writeSystemManagedProfile(
                    "/CtsCompilationApp.prof", profileLocation, APPLICATION_PACKAGE);
        }
        executeCompile(APPLICATION_PACKAGE, "-m", "speed-profile");

        // Confirm the compiler-filter used in creating the odex file
        String compilerFilter = getCompilerFilter(odexFilePath);

        // Without profiles, the compiler filter should be verify.
        String expectedCompilerFilter = profileLocations.isEmpty() ? "verify" : "speed-profile";
        assertEquals("compiler-filter", expectedCompilerFilter, compilerFilter);

        String odexFileContents = mDevice.pullFileContents(odexFilePath);
        boolean odexChanged = !initialOdexFileContents.equals(odexFileContents);
        if (odexChanged && !expectOdexChange) {
            String msg = String.format(Locale.US, "Odex file without filters (%d bytes) "
                    + "unexpectedly different from odex file (%d bytes) compiled with filters: %s",
                    initialOdexFileContents.length(), odexFileContents.length(), profileLocations);
            fail(msg);
        } else if (!odexChanged && expectOdexChange) {
            fail("odex file should have changed when recompiling with " + profileLocations);
        }
    }

    private void resetProfileState(String packageName) throws Exception {
        mDevice.executeShellV2Command("rm -f " + ProfileLocation.REF.getPath(packageName));
        mDevice.executeShellV2Command("truncate -s 0 " + ProfileLocation.CUR.getPath(packageName));
    }

    /**
     * Invokes the dex2oat compiler on the client.
     *
     * @param compileOptions extra options to pass to the compiler on the command line
     */
    private void executeCompile(String packageName, String... compileOptions) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList("cmd", "package", "compile"));
        command.addAll(Arrays.asList(compileOptions));
        command.add(packageName);
        String[] commandArray = command.toArray(new String[0]);
        mUtils.assertCommandSucceeds(commandArray);
    }

    /**
     * Writes the given profile in binary format in a system-managed directory on the device, and
     * sets appropriate owner.
     */
    private void writeSystemManagedProfile(String profileResourceName, ProfileLocation location,
            String packageName) throws Exception {
        String targetPath = location.getPath(packageName);
        // Get the owner of the parent directory so we can set it on the file
        String targetDir = location.getDirectory(packageName);
        assertTrue("Directory " + targetDir + " not found", mDevice.doesFileExist(targetDir));
        // In format group:user so we can directly pass it to chown.
        String owner = assertCommandOutputsLines(1, "stat", "-c", "%U:%g", targetDir)[0];

        mUtils.pushFromResource(profileResourceName, targetPath);

        // System managed profiles are by default private, unless created from an external profile
        // such as a cloud profile.
        mUtils.assertCommandSucceeds("chmod", "640", targetPath);
        mUtils.assertCommandSucceeds("chown", owner, targetPath);
    }

    /**
     * Parses the value for the key "compiler-filter" out of the output from
     * {@code oatdump --header-only}.
     */
    private String getCompilerFilter(String odexFilePath) throws Exception {
        String[] response = mUtils.assertCommandSucceeds(
                                          "oatdump", "--header-only", "--oat-file=" + odexFilePath)
                                    .split("\n");
        String prefix = "compiler-filter =";
        for (String line : response) {
            line = line.trim();
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        fail("No occurence of \"" + prefix + "\" in: " + Arrays.toString(response));
        return null;
    }

    /**
     * Returns a list of methods that have native code in the odex file.
     */
    private List<String> getCompiledMethods(String odexFilePath) throws Exception {
        // Matches "    CODE: (code_offset=0x000010e0 size=198)...".
        Pattern codePattern = Pattern.compile("^\\s*CODE:.*size=(\\d+)");

        // Matches
        // "  0: void android.compilation.cts.appusedbyotherapp.R.<init>() (dex_method_idx=7)".
        Pattern methodPattern =
                Pattern.compile("((?:\\w+\\.)+[<>\\w]+\\(.*?\\)).*dex_method_idx=\\d+");

        String[] response =
                mUtils.assertCommandSucceeds("oatdump", "--oat-file=" + odexFilePath).split("\n");
        ArrayList<String> compiledMethods = new ArrayList<>();
        String currentMethod = null;
        int currentMethodIndent = -1;
        for (int i = 0; i < response.length; i++) {
            // While in a method block.
            while (currentMethodIndent != -1 && i < response.length
                    && getIndent(response[i]) > currentMethodIndent) {
                Matcher matcher = codePattern.matcher(response[i]);
                // The method has code whose size > 0.
                if (matcher.find() && Long.parseLong(matcher.group(1)) > 0) {
                    compiledMethods.add(currentMethod);
                }
                i++;
            }

            if (i >= response.length) {
                break;
            }

            currentMethod = null;
            currentMethodIndent = -1;

            Matcher matcher = methodPattern.matcher(response[i]);
            if (matcher.find()) {
                currentMethod = matcher.group(1);
                currentMethodIndent = getIndent(response[i]);
            }
        }
        return compiledMethods;
    }

    /**
     * Returns the number of leading spaces.
     */
    private int getIndent(String str) {
        int indent = 0;
        while (indent < str.length() && str.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    /**
     * Returns the path to the application's base.odex file that should have
     * been created by the compiler.
     */
    private String getOdexFilePath(String packageName) throws Exception {
        // Something like "package:/data/app/android.compilation.cts-1/base.apk"
        String pathSpec = assertCommandOutputsLines(1, "pm", "path", packageName)[0];
        Matcher matcher = Pattern.compile("^package:(.+/)base\\.apk$").matcher(pathSpec);
        boolean found = matcher.find();
        assertTrue("Malformed spec: " + pathSpec, found);
        String apkDir = matcher.group(1);
        // E.g. /data/app/android.compilation.cts-1/oat/arm64/base.odex
        String result = assertCommandOutputsLines(1, "find", apkDir, "-name", "base.odex")[0];
        assertTrue("odex file not found: " + result, mDevice.doesFileExist(result));
        return result;
    }

    private String[] assertCommandOutputsLines(int numLinesOutputExpected, String... command)
            throws Exception {
        String output = mUtils.assertCommandSucceeds(command);
        // "".split() returns { "" }, but we want an empty array
        String[] lines = output.equals("") ? new String[0] : output.split("\n");
        assertEquals(
                String.format(Locale.US, "Expected %d lines output, got %d running %s: %s",
                        numLinesOutputExpected, lines.length, Arrays.toString(command),
                        Arrays.toString(lines)),
                numLinesOutputExpected, lines.length);
        return lines;
    }

    private void assertFileIsPublic(String path) throws Exception {
        String permissions = getPermissions(path);
        assertWithMessage("Expected " + path + " to be public, got " + permissions)
                .that(permissions.charAt(READ_OTHER)).isEqualTo('r');
    }

    private void assertFileIsPrivate(String path) throws Exception {
        String permissions = getPermissions(path);
        assertWithMessage("Expected " + path + " to be private, got " + permissions)
                .that(permissions.charAt(READ_OTHER)).isEqualTo('-');
    }

    private String getPermissions(String path) throws Exception {
        String permissions = mDevice.getFileEntry(path).getPermissions();
        assertWithMessage("Invalid permissions string " + permissions).that(permissions.length())
                .isEqualTo(PERMISSIONS_LENGTH);
        return permissions;
    }

    private void applyPackageDexUsageChanges() throws Exception {
        mUtils.assertCommandSucceeds(
                String.format("chown system:system %s", PACKAGE_DEX_USAGE_PATH));
        mUtils.assertCommandSucceeds(String.format("chmod 600 %s", PACKAGE_DEX_USAGE_PATH));
        mUtils.assertCommandSucceeds(String.format("restorecon %s", PACKAGE_DEX_USAGE_PATH));
        mUtils.softReboot();
    }
}
