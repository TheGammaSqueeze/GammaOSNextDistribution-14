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

package com.android.tests.odsign;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.CommandResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

/**
 * Test to check end-to-end odrefresh invocations, but without odsign and fs-verity involved.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class OdrefreshHostTest extends BaseHostJUnit4Test {
    private OdsignTestUtils mTestUtils;
    private DeviceState mDeviceState;

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);
        testUtils.installTestApex();
        testUtils.reboot();
    }

    @AfterClassWithInfo
    public static void afterClassWithDevice(TestInformation testInfo) throws Exception {
        OdsignTestUtils testUtils = new OdsignTestUtils(testInfo);
        testUtils.uninstallTestApex();
        testUtils.reboot();
    }

    @Before
    public void setUp() throws Exception {
        mTestUtils = new OdsignTestUtils(getTestInformation());
        mDeviceState = new DeviceState(getTestInformation());
        mDeviceState.backupArtifacts();
    }

    @After
    public void tearDown() throws Exception {
        mDeviceState.restore();
    }

    @Test
    public void verifyArtSamegradeUpdateTriggersCompilation() throws Exception {
        mDeviceState.simulateArtApexUpgrade();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifyOtherApexSamegradeUpdateTriggersCompilation() throws Exception {
        mDeviceState.simulateApexUpgrade();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifyBootClasspathOtaTriggersCompilation() throws Exception {
        mDeviceState.simulateBootClasspathOta();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifySystemServerOtaTriggersCompilation() throws Exception {
        mDeviceState.simulateSystemServerOta();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifyMissingArtifactTriggersCompilation() throws Exception {
        Set<String> missingArtifacts = simulateMissingArtifacts();
        Set<String> remainingArtifacts = new HashSet<>();
        remainingArtifacts.addAll(mTestUtils.getExpectedPrimaryBootImage());
        remainingArtifacts.addAll(mTestUtils.getExpectedBootImageMainlineExtension());
        remainingArtifacts.addAll(mTestUtils.getSystemServerExpectedArtifacts());
        remainingArtifacts.removeAll(missingArtifacts);

        mTestUtils.removeCompilationLogToAvoidBackoff();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        mTestUtils.assertNotModifiedAfter(remainingArtifacts, timeMs);
        mTestUtils.assertModifiedAfter(missingArtifacts, timeMs);
    }

    @Test
    public void verifyPhenotypeFlagChangeTriggersCompilation() throws Exception {
        // Simulate that the flag value is initially empty.
        mDeviceState.setPhenotypeFlag("odrefresh_test_toggle", null);

        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        mDeviceState.setPhenotypeFlag("odrefresh_test_toggle", "false");

        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should not be re-compiled.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        mDeviceState.setPhenotypeFlag("odrefresh_test_toggle", "true");

        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should be re-compiled.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Run odrefresh again with the flag unchanged.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should not be re-compiled.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        mDeviceState.setPhenotypeFlag("odrefresh_test_toggle", null);

        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should be re-compiled.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifySystemServerCompilerFilterOverrideChangeTriggersCompilation()
            throws Exception {
        mDeviceState.setPhenotypeFlag("systemservercompilerfilter_override", null);

        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should not be re-compiled.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        mDeviceState.setPhenotypeFlag("systemservercompilerfilter_override", "speed");

        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should be re-compiled.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Run odrefresh again with the flag unchanged.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should not be re-compiled.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        mDeviceState.setPhenotypeFlag("systemservercompilerfilter_override", "verify");

        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should be re-compiled.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifySystemPropertyMismatchTriggersCompilation() throws Exception {
        // Change a system property from empty to a value.
        mDeviceState.setProperty("dalvik.vm.foo", "1");
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should be re-compiled.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Run again with the same value.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should not be re-compiled.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Change the system property to another value.
        mDeviceState.setProperty("dalvik.vm.foo", "2");
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should be re-compiled.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Run again with the same value.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should not be re-compiled.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Change the system property to empty.
        mDeviceState.setProperty("dalvik.vm.foo", "");
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should be re-compiled.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Run again with the same value.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts should not be re-compiled.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifyNoCompilationWhenCacheIsGood() throws Exception {
        mTestUtils.removeCompilationLogToAvoidBackoff();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifyUnexpectedFilesAreCleanedUp() throws Exception {
        String unexpected = OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME + "/unexpected";
        getDevice().pushString("" /* contents */, unexpected);
        mTestUtils.runOdrefresh();

        assertThat(getDevice().doesFileExist(unexpected)).isFalse();
    }

    @Test
    public void verifyCacheInfoOmitsIrrelevantApexes() throws Exception {
        String cacheInfo = getDevice().pullFileContents(OdsignTestUtils.CACHE_INFO_FILE);

        // cacheInfo should list all APEXes that have compilable JARs and
        // none that do not.

        // This should always contain classpath JARs, that's the reason it exists.
        assertThat(cacheInfo).contains("name=\"com.android.sdkext\"");

        // This should never contain classpath JARs, it's the native runtime.
        assertThat(cacheInfo).doesNotContain("name=\"com.android.runtime\"");
    }

    @Test
    public void verifyCompilationOsMode() throws Exception {
        mTestUtils.removeCompilationLogToAvoidBackoff();
        mDeviceState.simulateApexUpgrade();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh("--compilation-os-mode");

        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        String cacheInfo = getDevice().pullFileContents(OdsignTestUtils.CACHE_INFO_FILE);
        assertThat(cacheInfo).contains("compilationOsMode=\"true\"");

        // Compilation OS does not write the compilation log to the host.
        mTestUtils.removeCompilationLogToAvoidBackoff();

        // Simulate the odrefresh invocation on the next boot.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // odrefresh should not re-compile anything.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertNotModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);
    }

    @Test
    public void verifyMinimalCompilation() throws Exception {
        mTestUtils.removeCompilationLogToAvoidBackoff();
        getDevice().executeShellV2Command(
                "rm -rf " + OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME);
        mTestUtils.runOdrefresh("--minimal");

        mTestUtils.restartZygote();

        // The minimal boot image should be loaded.
        mTestUtils.verifyZygotesLoadedMinimalBootImage();

        // Running the command again should not overwrite the minimal boot image.
        mTestUtils.removeCompilationLogToAvoidBackoff();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh("--minimal");

        Set<String> minimalZygoteArtifacts = mTestUtils.getExpectedMinimalBootImage();
        mTestUtils.assertNotModifiedAfter(minimalZygoteArtifacts, timeMs);

        // A normal odrefresh invocation should replace the minimal boot image with a full one.
        mTestUtils.removeCompilationLogToAvoidBackoff();
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        for (String artifact : minimalZygoteArtifacts) {
            assertWithMessage(
                    String.format(
                            "Artifact %s should be cleaned up while it still exists", artifact))
                    .that(getDevice().doesFileExist(artifact))
                    .isFalse();
        }

        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
    }

    @Test
    public void verifyCompilationFailureBackoff() throws Exception {
        mDeviceState.makeDex2oatFail();
        mDeviceState.simulateArtApexUpgrade();

        // Run odrefresh. It should encounter dex2oat failures.
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Artifacts don't exist because the compilation failed.
        mTestUtils.assertModifiedAfter(Set.of(OdsignTestUtils.CACHE_INFO_FILE), timeMs);
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedPrimaryBootImage());
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedBootImageMainlineExtension());
        mTestUtils.assertFilesNotExist(mTestUtils.getSystemServerExpectedArtifacts());

        // Run odrefresh again.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // It should not retry.
        mTestUtils.assertNotModifiedAfter(Set.of(OdsignTestUtils.CACHE_INFO_FILE), timeMs);

        // Simulate that the backoff time has passed.
        mTestUtils.removeCompilationLogToAvoidBackoff();

        // Run odrefresh again.
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Now it should retry.
        mTestUtils.assertModifiedAfter(Set.of(OdsignTestUtils.CACHE_INFO_FILE), timeMs);
    }

    /**
     * Regression test of CVE-2021-39689 (b/206090748): if the device doesn't have the odsign
     * security fix, there's a risk that the existing artifacts may be manipulated, and odsign will
     * mistakenly sign them. Therefore, odrefresh should clear all artifacts and regenerate them.
     * I.e., no matter the compilation succeeds or not, no existing artifacts should be left.
     *
     * On contrary, if the device has the odsign security fix, odrefresh should keep existing
     * artifacts (see {@link #verifyMissingArtifactTriggersCompilation}).
     */
    @Test
    public void verifyArtifactsClearedWhenNoPartialCompilation() throws Exception {
        // Remove arbitrary system server artifacts to trigger compilation.
        simulateMissingArtifacts();

        // The successful case.
        mTestUtils.removeCompilationLogToAvoidBackoff();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefreshNoPartialCompilation();

        // Existing artifacts should be replaced with new ones.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getSystemServerExpectedArtifacts(), timeMs);

        // Remove arbitrary system server artifacts to trigger compilation again.
        simulateMissingArtifacts();

        // The failed case.
        mDeviceState.makeDex2oatFail();
        mTestUtils.removeCompilationLogToAvoidBackoff();
        timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefreshNoPartialCompilation();

        // Existing artifacts should be gone.
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedPrimaryBootImage());
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedBootImageMainlineExtension());
        mTestUtils.assertFilesNotExist(mTestUtils.getSystemServerExpectedArtifacts());
    }

    /**
     * If the compilation is skipped because a previous attempt partially failed, odrefresh should
     * not clear existing artifacts.
     */
    @Test
    public void verifyArtifactsKeptWhenCompilationSkippedNoPartialCompilation() throws Exception {
        // Simulate that the compilation is partially failed.
        mDeviceState.simulateBadSystemServerJar();
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefreshNoPartialCompilation();

        // Verify the test setup: boot images are still generated.
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertModifiedAfter(mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);

        // Rerun odrefresh. The compilation is skipped this time.
        timeMs = mTestUtils.getCurrentTimeMs();
        CommandResult result = mTestUtils.runOdrefreshNoPartialCompilation();

        // Existing artifacts should be kept.
        mTestUtils.assertNotModifiedAfter(mTestUtils.getExpectedPrimaryBootImage(), timeMs);
        mTestUtils.assertNotModifiedAfter(
                mTestUtils.getExpectedBootImageMainlineExtension(), timeMs);

        // Note that the existing artifacts may be manipulated (CVE-2021-39689). Make sure odrefresh
        // returns `kOkay` rather than `kCompilationSuccess` or `kCompilationFailed`, so that odsign
        // only verifies the artifacts but not sign them.
        assertThat(result.getExitCode()).isEqualTo(0);
    }

    private Set<String> simulateMissingArtifacts() throws Exception {
        Set<String> missingArtifacts = new HashSet<>();
        String sample = mTestUtils.getSystemServerExpectedArtifacts().iterator().next();
        for (String extension : OdsignTestUtils.APP_ARTIFACT_EXTENSIONS) {
            String artifact = OdsignTestUtils.replaceExtension(sample, extension);
            getDevice().deleteFile(artifact);
            missingArtifacts.add(artifact);
        }
        return missingArtifacts;
    }
}
