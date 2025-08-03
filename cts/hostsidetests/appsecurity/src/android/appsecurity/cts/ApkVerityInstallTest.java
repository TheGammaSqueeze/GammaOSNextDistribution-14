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

package android.appsecurity.cts;

import static com.android.compatibility.common.util.PropertyUtil.getFirstApiLevel;
import static com.android.compatibility.common.util.PropertyUtil.getVendorApiLevel;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;
import android.security.Flags;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.DeviceParameterizedRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import junitparams.Parameters;

@Presubmit
@RunWith(DeviceParameterizedRunner.class)
@AppModeFull
@RequiresFlagsDisabled(Flags.FLAG_DEPRECATE_FSV_SIG)
@Ignore("b/303068306")
public final class ApkVerityInstallTest extends BaseAppSecurityTest {

    private static final String PACKAGE_NAME = "android.appsecurity.cts.apkveritytestapp";

    private static final String BASE_APK = "CtsApkVerityTestAppPrebuilt.apk";
    private static final String BASE_APK_DM = "CtsApkVerityTestAppPrebuilt.dm";
    private static final String SPLIT_APK = "CtsApkVerityTestAppSplitPrebuilt.apk";
    private static final String SPLIT_APK_DM = "CtsApkVerityTestAppSplitPrebuilt.dm";
    private static final String BAD_BASE_APK = "CtsApkVerityTestApp2Prebuilt.apk";
    private static final String BAD_BASE_APK_DM = "CtsApkVerityTestApp2Prebuilt.dm";
    private static final String FSV_SIG_SUFFIX = ".fsv_sig";
    private static final String ID_SIG_SUFFIX = ".idsig";
    private static final String APK_VERITY_STANDARD_MODE = "2";

    private static final boolean INCREMENTAL = true;
    private static final boolean NON_INCREMENTAL = false;

    private static final boolean SUPPORTED = true;
    private static final boolean UNSUPPORTED = false;

    private static final Map<String, String> ORIGINAL_TO_INSTALL_NAME = Map.of(
            BASE_APK, "base.apk",
            BASE_APK_DM, "base.dm",
            SPLIT_APK, "split_feature_x.apk",
            SPLIT_APK_DM, "split_feature_x.dm");

    private static final Object[] installSingle() {
        // Non-Incremental and Incremental.
        return new Boolean[][]{{NON_INCREMENTAL}, {INCREMENTAL}};
    }

    private static final Object[] installAndUpdate() {
        // Non-Incremental -> Non-Incremental: supported
        // Incremental -> Non-Incremental: supported
        // Incremental -> Incremental: supported
        // Non-Incremental -> Incremental: unsupported
        return new Boolean[][]{
                {NON_INCREMENTAL, NON_INCREMENTAL, SUPPORTED},
                {INCREMENTAL, NON_INCREMENTAL, SUPPORTED},
                {INCREMENTAL, INCREMENTAL, SUPPORTED},
                {NON_INCREMENTAL, INCREMENTAL, UNSUPPORTED}
        };
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();
        String apkVerityMode = device.getProperty("ro.apk_verity.mode");
        // Force opt-in, or on a supported device by requirement
        assumeTrue(APK_VERITY_STANDARD_MODE.equals(apkVerityMode)
                || device.getLaunchApiLevel() >= 30 /* R */);
        assumeSecurityModelCompat();
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        getDevice().uninstallPackage(PACKAGE_NAME);
    }

    @CddTest(requirement = "9.10/C-0-3")
    @Test
    @Parameters(method = "installSingle")
    public void testInstallBase(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(incremental, BASE_APK);
    }

    @CddTest(requirement = "9.10/C-0-3")
    @Test
    @Parameters(method = "installSingle")
    public void testInstallBaseWithWrongSignature(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        InstallMultiple install = new InstallMultiple(incremental)
                .addFile(BAD_BASE_APK)
                .addFile(BAD_BASE_APK + FSV_SIG_SUFFIX);

        // S with IncFsV1 silently skips fs-verity signatures.
        boolean expectingSuccess = incremental && !isIncrementalDeliveryV2Feature();
        install.run(expectingSuccess);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installSingle")
    public void testInstallBaseWithSplit(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(incremental, BASE_APK, SPLIT_APK);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installSingle")
    @Ignore("b/301117615#comment5")
    public void testInstallBaseWithDm(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(incremental, BASE_APK, BASE_APK_DM);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installSingle")
    @Ignore("b/301117615#comment5")
    public void testInstallEverything(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(incremental, BASE_APK, BASE_APK_DM, SPLIT_APK, SPLIT_APK_DM);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installAndUpdate")
    public void testInstallSplitOnly(boolean installIncremental, boolean updateIncremental,
            boolean isSupported) throws Exception {
        assumePreconditions(installIncremental || updateIncremental);
        new InstallMultiple(installIncremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(installIncremental, BASE_APK);

        new InstallMultiple(updateIncremental)
                .inheritFrom(PACKAGE_NAME)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .run(isSupported);
        if (isSupported) {
            verifyFsverityInstall(updateIncremental, BASE_APK, SPLIT_APK);
        }
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installAndUpdate")
    public void testInstallSplitOnlyMissingSignature(boolean installIncremental,
            boolean updateIncremental, boolean isSupported) throws Exception {
        assumePreconditions(installIncremental || updateIncremental);
        new InstallMultiple(installIncremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(installIncremental, BASE_APK);

        InstallMultiple install = new InstallMultiple(updateIncremental)
                .inheritFrom(PACKAGE_NAME)
                .addFile(SPLIT_APK);

        // S with IncFsV1 silently skips fs-verity signatures.
        boolean expectingSuccess =
                isSupported && installIncremental && !isIncrementalDeliveryV2Feature();
        install.run(expectingSuccess);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installAndUpdate")
    public void testInstallSplitOnlyWithoutBaseSignature(boolean installIncremental,
            boolean updateIncremental, boolean isSupported) throws Exception {
        assumePreconditions(installIncremental || updateIncremental);
        new InstallMultiple(installIncremental)
                .addFile(BASE_APK)
                .run();

        new InstallMultiple(updateIncremental)
                .inheritFrom(PACKAGE_NAME)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .run(isSupported);
        if (isSupported) {
            verifyFsverityInstall(updateIncremental, SPLIT_APK);
        }
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installAndUpdate")
    public void testInstallSplitAndSignatureForBase(boolean installIncremental,
            boolean updateIncremental, boolean isSupported) throws Exception {
        assumePreconditions(installIncremental || updateIncremental);
        new InstallMultiple(installIncremental)
                .addFile(BASE_APK)
                .run();

        new InstallMultiple(updateIncremental)
                .inheritFrom(PACKAGE_NAME)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .run(isSupported);
        if (isSupported) {
            verifyFsverityInstall(updateIncremental, BASE_APK);
        }
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installAndUpdate")
    public void testUpdateBaseWithSignature(boolean installIncremental, boolean updateIncremental,
            boolean isSupported) throws Exception {
        assumePreconditions(installIncremental || updateIncremental);
        new InstallMultiple(installIncremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(installIncremental, BASE_APK);

        new InstallMultiple(updateIncremental)
                .inheritFrom(PACKAGE_NAME)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run(isSupported);
        verifyFsverityInstall(updateIncremental, BASE_APK);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installSingle")
    @Ignore("b/301117615#comment5")
    public void testInstallBaseWithFsvSigAndSplitWithout(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX)
                .runExpectingFailure();
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installSingle")
    @Ignore("b/301117615#comment5")
    public void testInstallDmWithFsvSig(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(incremental, BASE_APK_DM, SPLIT_APK_DM);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installSingle")
    @Ignore("b/301117615#comment5")
    public void testInstallDmWithMissingFsvSig(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        InstallMultiple installer = new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM);
        installer.run();
        verifyFsverityInstall(incremental, BASE_APK_DM);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installSingle")
    @Ignore("b/301117615#comment5")
    public void testInstallSplitWithFsvSigAndBaseWithout(boolean incremental) throws Exception {
        assumePreconditions(incremental);
        InstallMultiple installer = new InstallMultiple(incremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX);
        installer.run();
        verifyFsverityInstall(incremental, BASE_APK_DM, SPLIT_APK_DM);
    }

    @CddTest(requirement = "9.10/C-0-3,C-0-5")
    @Test
    @Parameters(method = "installAndUpdate")
    public void testInstallBaseWithFsvSigThenSplitWithout(boolean installIncremental,
            boolean updateIncremental, boolean isSupported) throws Exception {
        assumePreconditions(installIncremental || updateIncremental);
        new InstallMultiple(installIncremental)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(installIncremental, BASE_APK);

        new InstallMultiple(updateIncremental)
                .addFile(SPLIT_APK)
                .runExpectingFailure();
    }

    @Test
    public void testInstallBaseIncrementally() throws Exception {
        assumeTrue(hasIncrementalDeliveryFeature());
        new InstallMultiple(/*incremental=*/true)
                .addFile(BASE_APK)
                .run();
    }

    @Test
    public void testInstallBaseIncrementallyWithFsvSig() throws Exception {
        assumeTrue(isIncrementalDeliveryV2Feature());
        new InstallMultiple(/*incremental=*/true)
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(true, BASE_APK);
    }

    @Test
    public void testInstallBaseIncrementallyWithFsvSigAndIdSig() throws Exception {
        assumeTrue(isIncrementalDeliveryV2Feature());
        new InstallMultiple(/*incremental=*/true)
                .addFile(BASE_APK)
                .pushFile(BASE_APK + ID_SIG_SUFFIX)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(true, BASE_APK);
    }

    @Test
    public void testInstallBaseIncrementallyWithIdSigAndWrongFsvSig() throws Exception {
        assumeTrue(isIncrementalDeliveryV2Feature());
        new InstallMultiple(/*incremental=*/true)
                .addFile(BASE_APK)
                .pushFile(BASE_APK + ID_SIG_SUFFIX)
                .renameAndAddFile(BAD_BASE_APK + FSV_SIG_SUFFIX, BASE_APK + FSV_SIG_SUFFIX)
                .runExpectingFailure();
    }

    @Test
    public void testInstallBaseIncrementallyWithWrongIdSigAndFsvSig() throws Exception {
        assumeTrue(isIncrementalDeliveryV2Feature());
        new InstallMultiple(/*incremental=*/true)
                .addFile(BASE_APK)
                .renameAndPushFile(BAD_BASE_APK + ID_SIG_SUFFIX, BASE_APK + ID_SIG_SUFFIX)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .runExpectingFailure();
    }

    private void assumePreconditions(boolean requiresIncremental) throws Exception {
        if (requiresIncremental) {
            assumeTrue(hasIncrementalDeliveryFeature());
        }
    }

    private boolean hasIncrementalDeliveryFeature() throws Exception {
        return "true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery"));
    }

    private boolean isIncrementalDeliveryV2Feature() throws Exception {
        return "true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery 2"));
    }

    private void assumeSecurityModelCompat() throws DeviceNotAvailableException {
        // This feature name check only applies to devices that first shipped with
        // SC or later.
        final int firstApiLevel =
                Math.min(getFirstApiLevel(getDevice()), getVendorApiLevel(getDevice()));
        if (firstApiLevel >= 31) {
            assumeTrue("Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.",
                    getDevice().hasFeature("feature:android.hardware.security.model.compatible"));
        }
    }

    void verifyFsverityInstall(boolean incremental, String... files) throws Exception {
        if (incremental && !isIncrementalDeliveryV2Feature()) {
            return;
        }

        DeviceTestRunOptions options = new DeviceTestRunOptions(PACKAGE_NAME);
        options.setTestClassName(PACKAGE_NAME + ".InstalledFilesCheck");
        options.setTestMethodName("testFilesHaveFsverity");
        options.addInstrumentationArg("Number",
                Integer.toString(files.length));
        for (int i = 0; i < files.length; ++i) {
            String installName = ORIGINAL_TO_INSTALL_NAME.get(files[i]);
            if (installName == null) {
                fail("Install name is not defined for " + files[i]);
            }
            options.addInstrumentationArg("File" + i, installName);
        }
        runDeviceTests(options);
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        InstallMultiple(boolean incremental) throws Exception {
            super(getDevice(), getBuild(), getAbi());
            if (incremental) {
                useIncremental();
            }
        }

        @Override
        protected String deriveRemoteName(String originalName, int index) {
            return originalName;
        }
    }
}
