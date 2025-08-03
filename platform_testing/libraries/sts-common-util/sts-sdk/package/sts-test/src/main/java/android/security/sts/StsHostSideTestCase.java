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

package android.security.sts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.sts.common.CommandUtil;
import com.android.sts.common.MallocDebug;
import com.android.sts.common.NativePoc;
import com.android.sts.common.NativePocCrashAsserter;
import com.android.sts.common.NativePocStatusAsserter;
import com.android.sts.common.ProcessUtil;
import com.android.sts.common.RegexUtils;
import com.android.sts.common.SystemUtil;
import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class StsHostSideTestCase extends NonRootSecurityTestCase {

    static final String TEST_APP = "sts_test_app_package.apk";
    static final String TEST_PKG = "android.security.sts.sts_test_app_package";
    static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";

    /** An app test, which uses this host Java test to launch an Android instrumented test */
    @Test
    public void testWithApp() throws Exception {
        ITestDevice device = getDevice();
        assertTrue("could not disable root", device.disableAdbRoot());
        uninstallPackage(device, TEST_PKG);

        installPackage(TEST_APP);
        runDeviceTests(TEST_PKG, TEST_CLASS, "testDeviceSideMethod");
    }

    /**
     * A native PoC test, which uses this host Java test to push an executable with resources and
     * execute with environment variables and more. This API uses a "NativePocAsserter" that handles
     * the most common ways to retrieve data from the native PoC. It can be overloaded to handle the
     * specific side-effect that your PoC generates.
     */
    @Test
    public void testWithNativePoc() throws Exception {
        NativePoc.builder()
                // the name of the PoC
                .pocName("native-poc")
                // extra files pushed to the device
                .resources("res.txt")
                // command-line arguments for the PoC
                .args("res.txt", "vulnerable")
                // other options allow different linker paths for library shims
                .useDefaultLdLibraryPath(true)
                // test ends with ASSUMPTION_FAILURE if not EXIT_OK
                .assumePocExitSuccess(true)
                // run code after the PoC is executed for cleanup or other
                .after(r -> getDevice().executeShellV2Command("ls -l /"))
                // fail if the poc returns exit status 113
                .asserter(NativePocStatusAsserter.assertNotVulnerableExitCode())
                .build()
                .run(this);
    }

    /** Run native PoCs with Malloc Debug memory checking enabled */
    @Test
    public void testWithMallocDebug() throws Exception {
        // Set up Malloc Debug for this test, which may be required if the vulnerability needs
        // memory checking to crash. This is useful when an ASan/HWASan/MTE build is not available.
        // https://android.googlesource.com/platform/bionic/+/master/libc/malloc_debug/README.md
        // Note: enabling malloc debug requires root but it can be turned off after
        assumeTrue("could not enable root for malloc debug", getDevice().enableAdbRoot());
        try (AutoCloseable mallocDebug =
                MallocDebug.withLibcMallocDebugOnNewProcess(
                        getDevice(),
                        "backtrace guard", // malloc debug options
                        "native-poc" // process name
                        )) {
            assumeTrue("could not disable root", getDevice().disableAdbRoot());

            // run a native PoC
            NativePoc.builder()
                    .pocName("native-poc")
                    .args("memory_corrupt")
                    .build() // add more as needed
                    .run(this);
        }
    }

    /** Run code after applying device settings */
    @Test
    public void testWithSetting() throws Exception {
        // allow reflection, which is not a security boundary
        try (AutoCloseable setting =
                SystemUtil.withSetting(getDevice(), "global", "hidden_api_policy", "1")) {
            // run app
            installPackage(TEST_APP);
            runDeviceTests(TEST_PKG, TEST_CLASS, "testDeviceSideMethod");
        }
    }

    /** Link a native PoC against a vulnerable system library */
    @Test
    public void testWithVulnerableLibrary() throws Exception {
        // get the path of the vulnerable library
        assumeTrue("could not enable root to find library path", getDevice().enableAdbRoot());
        Optional<IFileEntry> libFileEntry =
                ProcessUtil.findFileLoadedByProcess(
                        getDevice(),
                        "mediaserver",
                        Pattern.compile(Pattern.quote("libmediaplayerservice.so")));
        assumeTrue("shared library not loaded by target process", libFileEntry.isPresent());
        assumeTrue("could not disable root", getDevice().disableAdbRoot());

        // attack the service
        NativePoc.builder()
                .pocName("native-poc")
                // pass the library path to the PoC
                .args(libFileEntry.get().getFullPath())
                .assumePocExitSuccess(false) // example returns EXIT_FAILURE if not enough args
                .asserter(
                        NativePocCrashAsserter.assertNoCrash(
                                new TombstoneUtils.Config()
                                        // Because the vulnerability is in the shared library, the
                                        // process crash is the PoC.
                                        .setProcessPatterns(Pattern.compile("native-poc"))))
                .build()
                .run(this);
    }

    /** Match a log against a known vulnerable pattern regex */
    @Test
    public void testWithLogMessage() throws Exception {
        // this is only for dmesg/logcat messages that are not controlled by the test.

        // attack the device, which can be native poc, echo to socket, send intent, app, etc
        NativePoc.builder()
                .pocName("native-poc")
                .assumePocExitSuccess(false) // example returns EXIT_FAILURE if no args
                .build() // add more as needed
                .run(this);

        assumeTrue("could not enable root to collect dmesg", getDevice().enableAdbRoot());
        String dmesg = CommandUtil.runAndCheck(getDevice(), "dmesg -c").getStdout();
        assumeTrue("could not disable root", getDevice().disableAdbRoot());

        // It's preferred to use this for matching text because the regex has a timeout to
        // protect against catastrophic backtracking. It also formats the test assert message.
        RegexUtils.assertNotContainsMultiline(
                "Call trace:.*?__arm_lpae_unmap.*?kgsl_iommu_unmap", dmesg);
    }

    /** Install and run an app as a secondary user */
    @Test
    public void testWithSecondaryUser() throws Exception {
        try (AutoCloseable su = new UserUtils.SecondaryUser(getDevice()).withUser()) {
            installPackage(TEST_APP);
            runDeviceTests(TEST_PKG, TEST_CLASS, "testDeviceSideMethod");
        }
    }
}
