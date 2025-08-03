/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.appmanifest.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Tests about uses-native-library tags that was introduced in Android S.
 *
 * The test reads the list of partner-defined public native shared libraries
 * (see <a href="https://source.android.com/devices/tech/config/namespaces_libraries#adding-additional-native-libraries)">
 * Adding additional native libraries</a>) and make sure that those are available to the apps
 * only when they are explicitly listed on the app manifest using the new tag. The libs not listed
 * are not available even though they are declared as public.
 *
 * This test also make sure that the new behavior is only for the new apps targeting Android S or
 * higher. Apps targeting Android 11 or lower still has access to all partner-defined public libs
 * regardless of the use of the tag.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class UsesNativeLibraryTestCase extends BaseHostJUnit4Test {
    // The list of partner-defined public native shared libraries
    private final Set<String> mPublicLibraries = new HashSet<>();

    // The list of public libs that we will make the test app to depend on
    private final Set<String> mSomePublicLibraries = new HashSet<>();

    // Remaining public libraries that shouldn't be available to new apps
    private final Set<String> mRemainingPublicLibraries = new HashSet<>();

    private File mWorkDir;

    // Name of a fake library that doesn't exist on the device
    private String mNonExistingLib;

    // Name of a library that actually exists on the device, but is not part of the public libraries
    private String mPrivateLib;

    // Values to enable/disable/reset the compat change gating
    private enum CompatChangeState {
        RESET,
        ENABLE,
        DISABLE
    }

    // The package name of apk from the buildTestApp
    private static final String TEST_APP_PACKAGE_NAME = "com.android.test.usesnativesharedlibrary";

    // The compat change id of the enforce native shared library dependencies
    private static final long ENFORCE_NATIVE_SHARED_LIBRARY_DEPENDENCIES = 142191088;

    @Before
    public void setUp() throws Exception {
        // extract "foo.so" from lines of foo.so ->  (so) foo.so
        Pattern pattern = Pattern.compile("(\\S+)\\s*->\\s*\\((\\S+)\\)\\s*(\\S+)");
        Arrays.stream(executeShellCommand("dumpsys package libraries").split("\n")).
                skip(1) /* for "Libraries:" header */ .
                map(line -> pattern.matcher(line.trim())).
                filter(matcher -> matcher.matches() && matcher.group(2).equals("so")).
                map(matcher -> matcher.group(1)).
                forEach(mPublicLibraries::add);

        // Pick first half of the public libraries
        mPublicLibraries.stream().
                limit(mPublicLibraries.size() / 2).
                forEach(mSomePublicLibraries::add);

        // ... and remainders
        mPublicLibraries.stream().
                filter(lib -> !mSomePublicLibraries.contains(lib)).
                forEach(mRemainingPublicLibraries::add);

        mNonExistingLib = "libnamethatneverexist.so";
        assertFalse(mPublicLibraries.contains(mNonExistingLib)); // unlikely!

        mPrivateLib = "libui.so"; // randomly chosen private lib
        assertTrue(getDevice().getFileEntry("/system/lib/" + mPrivateLib) != null ||
                   getDevice().getFileEntry("/system/lib64/" + mPrivateLib) != null);
        assertFalse(mPublicLibraries.contains(mPrivateLib));

        // The zip file contains all the tools and files for building a test app on demand. Extract
        // it to the work directory.
        try (ZipFile packageZip = new ZipFile(getTestInformation().getDependencyFile(
                    "CtsUseNativeLibraryBuildPackage.zip", false))) {
            mWorkDir = FileUtil.createTempDir("work");
            ZipUtil.extractZip(packageZip, mWorkDir);

            // Make sure executables are executable
            FileUtil.chmod(getFile("aapt2"), "u+x");
            FileUtil.chmod(getFile("merge_zips"), "u+x");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void cleanUp() throws Exception {
        FileUtil.recursiveDelete(mWorkDir);
        uninstallPackage(TEST_APP_PACKAGE_NAME);
        setCompatChange(ENFORCE_NATIVE_SHARED_LIBRARY_DEPENDENCIES, TEST_APP_PACKAGE_NAME,
                CompatChangeState.RESET);

    }

    private File getFile(String path) {
        return new File(mWorkDir, path);
    }

    private String executeShellCommand(String command) {
        try {
            return getDevice().executeShellCommand(command);
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<IFileEntry> getFileEntriesUnder(String path) {
        try {
            return getDevice().getFileEntry(path).getChildren(true).stream();
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<String> findPublicLibraryFilesUnder(String partition) {
        return getFileEntriesUnder(partition + "/etc").
                filter(fe -> {
                    // For vendor partition we only allow public.libraries.txt file.
                    // For other partitions, partner-added libs can be listed in
                    // public.libraries-<companyname>.txt files.
                    if (partition.equals("/vendor")) {
                        return fe.getName().equals("public.libraries.txt");
                    } else {
                        return fe.getName().startsWith("public.libraries-") &&
                                fe.getName().endsWith(".txt");
                    }
                }).
                map(fe -> fe.getFullPath());
    }

    /**
     * Tests if the native shared library list reported by the package manager is the same as
     * the public.libraries*.txt files in the partitions.
     */
    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testPublicLibrariesAreAllRegistered() throws DeviceNotAvailableException {
        Set<String> libraryNamesFromTxt =
                Stream.of("/system", "/system_ext", "/product", "/vendor").
                flatMap(dir -> findPublicLibraryFilesUnder(dir)).
                map(file -> executeShellCommand("cat " + file)).
                flatMap(lines -> Arrays.stream(lines.split("\n"))).
                filter(line -> {
                    // filter-out empty lines or comment lines that start with #
                    String strip = line.trim();
                    return !strip.isEmpty() && !strip.startsWith("#");
                }).
                // line format is "name [bitness]". Extract the name part.
                map(line -> line.trim().split("\\s+")[0]).
                collect(Collectors.toSet());

        assertEquals(mPublicLibraries, libraryNamesFromTxt);
    }

    /**
     * Creates an AndroidManifest.xml file from the template with the given api level and the list
     * of mandatory and optional native shared libraries
     */
    private File createManifestFileWithUsesNativeLibraryTags(File dir, int apiLevel,
            String[] requiredLibraries, String[] optionalLibraries) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getClass().getClassLoader().
                    getResourceAsStream("AndroidManifest_template.xml")))) {
            StringBuffer sb = new StringBuffer();
            String line = null;
            while( (line = reader.readLine()) != null) {
                sb.append(line);
            }
            String template = sb.toString();

            sb = new StringBuffer();
            for(String lib : requiredLibraries) {
                sb.append(String.format(
                        "<uses-native-library android:name=\"%s\"/>", lib));
            }
            for(String lib : optionalLibraries) {
                sb.append(String.format(
                        "<uses-native-library android:name=\"%s\" android:required=\"false\"/>",
                        lib));
            }

            String newContent = template.replace("%USES_LIBRARY%", sb.toString());
            newContent = newContent.replace("%TARGET_SDK_VERSION%", Integer.toString(apiLevel));

            File output = new File(dir, "AndroidManifest.xml");
            FileUtil.writeToFile(newContent, output);
            return output;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runCommand(String cmd) {
        CommandResult result = RunUtil.getDefault().runTimedCmd(100000, cmd.split(" "));
        if (result.getExitCode() != 0) {
            throw new RuntimeException(result.getStderr());
        }
    }

    private File buildTestApp(int apiLevel,
            String[] requiredLibraries,
            String[] optionalLibraries,
            String[] availableLibraries,
            String[] unavailableLibraries) throws IOException {
        File buildRoot = FileUtil.createTempDir("appbuild", mWorkDir);

        // Create available.txt and unavailable.txt files. They contain the list of native libs
        // that must be loadable and non-loadable. The Test app will fail if any of the lib in
        // available.txt is non-loadable, or if any of the lib in unavailable.txt is loadable.
        File assetDir = new File(buildRoot, "asset");
        assetDir.mkdir();
        File availableTxtFile = new File(assetDir, "available.txt");
        File unavailableTxtFile = new File(assetDir, "unavailable.txt");
        FileUtil.writeToFile(String.join("\n", availableLibraries), availableTxtFile, false);
        FileUtil.writeToFile(String.join("\n", unavailableLibraries), unavailableTxtFile, false);

        File manifestFile = createManifestFileWithUsesNativeLibraryTags(buildRoot, apiLevel,
                requiredLibraries, optionalLibraries);

        File resFile = new File(buildRoot, "package-res.apk");
        runCommand(String.format("%s link --manifest %s -I %s -A %s -o %s",
                getFile("aapt2"),
                manifestFile,
                getFile("android.jar"),
                assetDir,
                resFile));

        // Append the app code to the apk
        File unsignedApkFile = new File(buildRoot, "unsigned.apk");
        runCommand(String.format("%s %s %s %s",
                getFile("merge_zips"),
                unsignedApkFile,
                resFile,
                getFile("CtsUsesNativeLibraryTestApp.jar")));

        File signedApkFile = new File(buildRoot, "signed.apk");
        try {
            runCommand(String.format("java -Djava.library.path=%s -jar %s %s %s %s %s",
                    mWorkDir,
                    getFile("signapk.jar"),
                    getFile("testkey.x509.pem"),
                    getFile("testkey.pk8"),
                    unsignedApkFile,
                    signedApkFile));
        } catch (RuntimeException e) {
            String msg = "Failed to sign apk. This is likely due to the use of old JDK. Please "
                + "update JDK to the latest. If you are a Googler, see b/208923903#comment19.";
            throw new RuntimeException(msg, e);
        }

        return signedApkFile;
    }

    private boolean installTestApp(File testApp) throws Exception {
        // Explicit uninstallation is required because we might downgrade the target API level
        // from 31 to 30
        uninstallPackage(TEST_APP_PACKAGE_NAME);
        try {
            installPackage(testApp.toString());
            return true;
        } catch (TargetSetupError e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private void runInstalledTestApp() throws Exception {
        runDeviceTests(TEST_APP_PACKAGE_NAME,
                "com.android.test.usesnativesharedlibrary.LoadTest");
    }

    private void setCompatChange(long changeId, String packageName, CompatChangeState state)
            throws DeviceNotAvailableException {
        final StringBuilder cmd = new StringBuilder("am compat ");
        switch (state) {
            case RESET:
                cmd.append("reset ");
                break;

            case ENABLE:
                cmd.append("enable ");
                break;

            case DISABLE:
                cmd.append("disable ");
                break;

            default:
                throw new IllegalArgumentException("Invalid compat change state:" + state);
        }
        cmd.append(changeId).append(" ");
        cmd.append(packageName);
        getDevice().executeShellCommand(cmd.toString());
    }

    private static String[] add(Set<String> s, String...extra) {
        List<String> ret = new ArrayList<>();
        ret.addAll(s);
        ret.addAll(Arrays.asList(extra));
        return ret.toArray(new String[0]);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests for when apps depend on non-existing lib
    ///////////////////////////////////////////////////////////////////////////

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testOldAppDependsOnNonExistingLib() throws Exception {
        String[] requiredLibs = {mNonExistingLib};
        String[] optionalLibs = {};
        String[] availableLibs = add(mPublicLibraries); // old app has access to all public libs
        String[] unavailableLibs = {mNonExistingLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(30,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppDependsOnNonExistingLib() throws Exception {
        String[] requiredLibs = {mNonExistingLib};
        String[] optionalLibs = {};
        String[] availableLibs = {}; // new app doesn't have access to unlisted public libs
        String[] unavailableLibs = add(mPublicLibraries, mNonExistingLib, mPrivateLib);

        assertFalse(installTestApp(buildTestApp(31,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));

        // install failed, so can't run the on-device test
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppDependsOnNonExistingLib_withCompatEnabled_installFail()
            throws Exception {
        setCompatChange(ENFORCE_NATIVE_SHARED_LIBRARY_DEPENDENCIES, TEST_APP_PACKAGE_NAME,
                CompatChangeState.ENABLE);
        String[] requiredLibs = {mNonExistingLib};
        String[] optionalLibs = {};
        String[] availableLibs = {}; // new app doesn't have access to unlisted public libs
        String[] unavailableLibs = add(mPublicLibraries, mNonExistingLib, mPrivateLib);

        assertFalse(installTestApp(buildTestApp(31,
                requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppDependsOnNonExistingLib_withCompatDisabled_installSucceed()
            throws Exception {
        setCompatChange(ENFORCE_NATIVE_SHARED_LIBRARY_DEPENDENCIES, TEST_APP_PACKAGE_NAME,
                CompatChangeState.DISABLE);
        String[] requiredLibs = {mNonExistingLib};
        String[] optionalLibs = {};
        String[] availableLibs = {}; // new app doesn't have access to unlisted public libs
        String[] unavailableLibs = add(mPublicLibraries, mNonExistingLib, mPrivateLib);

        assertTrue(installTestApp(buildTestApp(31,
                requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testOldAppOptionallyDependsOnNonExistingLib() throws Exception {
        String[] requiredLibs = {};
        String[] optionalLibs = {mNonExistingLib};
        String[] availableLibs = add(mPublicLibraries); // old app has access to all public libs
        String[] unavailableLibs = {mNonExistingLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(30,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppOptionallyDependsOnNonExistingLib() throws Exception {
        String[] requiredLibs = {};
        String[] optionalLibs = {mNonExistingLib};
        String[] availableLibs = {}; // new app doesn't have access to unlisted public libs
        String[] unavailableLibs = {mNonExistingLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(31,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests for when apps depend on private lib
    ///////////////////////////////////////////////////////////////////////////

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testOldAppDependsOnPrivateLib() throws Exception {
        String[] requiredLibs = {mPrivateLib};
        String[] optionalLibs = {};
        String[] availableLibs = add(mPublicLibraries); // old app has access to all public libs
        String[] unavailableLibs = {mPrivateLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(30,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppDependsOnPrivateLib() throws Exception {
        String[] requiredLibs = {mPrivateLib};
        String[] optionalLibs = {};
        String[] availableLibs = {}; // new app doesn't have access to unlisted public libs
        String[] unavailableLibs = add(mPublicLibraries, mPrivateLib, mPrivateLib);

        assertFalse(installTestApp(buildTestApp(31,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));

        // install failed, so can't run the on-device test
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testOldAppOptionallyDependsOnPrivateLib() throws Exception {
        String[] requiredLibs = {};
        String[] optionalLibs = {mPrivateLib};
        String[] availableLibs = add(mPublicLibraries); // old app has access to all public libs
        String[] unavailableLibs = {mPrivateLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(30,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppOptionallyDependsOnPrivateLib() throws Exception {
        String[] requiredLibs = {};
        String[] optionalLibs = {mPrivateLib};
        String[] availableLibs = {}; // new app doesn't have access to unlisted public libs
        String[] unavailableLibs = {mPrivateLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(31,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests for when apps depend on all public libraries
    ///////////////////////////////////////////////////////////////////////////

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testOldAppDependsOnAllPublicLibraries() throws Exception {
        String[] requiredLibs = add(mPublicLibraries);
        String[] optionalLibs = {};
        String[] availableLibs = add(mPublicLibraries); // old app still has access to all libs
        String[] unavailableLibs = {mNonExistingLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(30,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppDependsOnAllPublicLibraries() throws Exception {
        String[] requiredLibs = add(mPublicLibraries);
        String[] optionalLibs = {};
        String[] availableLibs = add(mPublicLibraries); // new app now has access to all libs
        String[] unavailableLibs = {mNonExistingLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(31,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests for when apps depend on some public libraries
    ///////////////////////////////////////////////////////////////////////////

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testOldAppDependsOnSomePublicLibraries() throws Exception {
        // select the first half of the public lib
        String[] requiredLibs = add(mSomePublicLibraries);
        String[] optionalLibs = {};
        String[] availableLibs = add(mPublicLibraries); // old app still has access to all libs
        String[] unavailableLibs = {mNonExistingLib, mPrivateLib};

        assertTrue(installTestApp(buildTestApp(30,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppDependsOnSomePublicLibraries() throws Exception {
        String[] requiredLibs = add(mSomePublicLibraries);
        String[] optionalLibs = {};
        // new app has access to the listed libs only
        String[] availableLibs = add(mSomePublicLibraries);
        // And doesn't have access to the remaining public libs and of course non-existing
        // and private libs.
        String[] unavailableLibs = add(mRemainingPublicLibraries, mNonExistingLib, mPrivateLib);

        assertTrue(installTestApp(buildTestApp(31,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }

    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testNewAppOptionallyDependsOnSomePublicLibraries() throws Exception {
        // select the first half of the public lib
        String[] requiredLibs = {};
        String[] optionalLibs = add(mSomePublicLibraries);
        // new app has access to the listed libs only
        String[] availableLibs = add(mSomePublicLibraries);
        // And doesn't have access to the remaining public libs and of course non-existing
        // and private libs.
        String[] unavailableLibs = add(mRemainingPublicLibraries, mNonExistingLib, mPrivateLib);

        assertTrue(installTestApp(buildTestApp(31,
                        requiredLibs, optionalLibs, availableLibs, unavailableLibs)));
        runInstalledTestApp();
    }
}

