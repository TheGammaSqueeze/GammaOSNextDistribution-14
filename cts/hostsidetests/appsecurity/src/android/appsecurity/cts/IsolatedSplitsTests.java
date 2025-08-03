/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.appsecurity.cts;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.platform.test.annotations.Presubmit;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

@Presubmit
@RunWith(DeviceJUnit4ClassRunner.class)
public class IsolatedSplitsTests extends BaseAppSecurityTest {
    private static final String PKG = "com.android.cts.isolatedsplitapp";
    private static final String TEST_CLASS = PKG + ".SplitAppTest";

    /* The feature hierarchy looks like this:

        APK_BASE <- APK_FEATURE_A <- APK_FEATURE_B
            ^------ APK_FEATURE_C

     */
    private static final String APK_BASE = "CtsIsolatedSplitApp.apk";
    private static final String APK_BASE_pl = "CtsIsolatedSplitApp_pl.apk";
    private static final String APK_FEATURE_A = "CtsIsolatedSplitAppFeatureA.apk";
    private static final String APK_FEATURE_A_pl = "CtsIsolatedSplitAppFeatureA_pl.apk";
    private static final String APK_FEATURE_B = "CtsIsolatedSplitAppFeatureB.apk";
    private static final String APK_FEATURE_B_pl = "CtsIsolatedSplitAppFeatureB_pl.apk";
    private static final String APK_FEATURE_C = "CtsIsolatedSplitAppFeatureC.apk";
    private static final String APK_FEATURE_C_pl = "CtsIsolatedSplitAppFeatureC_pl.apk";
    private static final String APK_FEATURE_A_DiffRev = "CtsIsolatedSplitAppFeatureADiffRev.apk";

    private static final String APK_BASE_WITHOUT_EXTRACTING = APK_BASE;
    private static final String APK_FEATURE_JNI_WITHOUT_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsFalseJni.apk";
    private static final String APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsFalseNumberProviderA.apk";
    private static final String APK_FEATURE_PROVIDER_B_WITHOUT_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsFalseNumberProviderB.apk";
    private static final String APK_FEATURE_PROXY_WITHOUT_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsFalseNumberProxy.apk";

    private static final String APK_BASE_WITH_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsTrue.apk";
    private static final String APK_FEATURE_JNI_WITH_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsTrueJni.apk";
    private static final String APK_FEATURE_PROVIDER_A_WITH_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsTrueNumberProviderA.apk";
    private static final String APK_FEATURE_PROVIDER_B_WITH_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsTrueNumberProviderB.apk";
    private static final String APK_FEATURE_PROXY_WITH_EXTRACTING =
            "CtsIsolatedSplitAppExtractNativeLibsTrueNumberProxy.apk";

    @Before
    public void setUp() throws Exception {
        Utils.prepareSingleUser(getDevice());
        getDevice().uninstallPackage(PKG);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PKG);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInstallBase_full() throws Exception {
        testInstallBase(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInstallBase_instant() throws Exception {
        testInstallBase(true);
    }

    private void testInstallBase(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInstallBaseAndConfigSplit_full() throws Exception {
        testInstallBaseAndConfigSplit(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInstallBaseAndConfigSplit_instant() throws Exception {
        testInstallBaseAndConfigSplit(true);
    }

    private void testInstallBaseAndConfigSplit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_BASE_pl).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadPolishLocale");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInstallMissingDependency_usesSplit_full() throws Exception {
        testInstallMissingDependency_usesSplit(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInstallMissingDependency_usesSplit_instant() throws Exception {
        testInstallMissingDependency_usesSplit(true);
    }

    private void testInstallMissingDependency_usesSplit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_B).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInstallMissingDependency_configForSplit_full() throws Exception {
        testInstallMissingDependency_configForSplit(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInstallMissingDependency_configForSplit_instant() throws Exception {
        testInstallMissingDependency_configForSplit(true);
    }

    private void testInstallMissingDependency_configForSplit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(
                APK_FEATURE_A_pl).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "b/109878606; instant applications can't send broadcasts to manifest "
            + "receivers")
    public void testInstallOneFeatureSplit_full() throws Exception {
        testInstallOneFeatureSplit(false);
    }

    private void testInstallOneFeatureSplit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_A).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureADefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureAReceivers");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInstallOneFeatureSplitAndConfigSplits_full() throws Exception {
        testInstallOneFeatureSplitAndConfigSplits(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInstallOneFeatureSplitAndConfigSplits_instant() throws Exception {
        testInstallOneFeatureSplitAndConfigSplits(true);
    }

    private void testInstallOneFeatureSplitAndConfigSplits(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_A).addFile(APK_BASE_pl)
                .addFile(APK_FEATURE_A_pl).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadPolishLocale");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureAPolishLocale");
    }

    @Test
    @AppModeFull(reason = "b/109878606; instant applications can't send broadcasts to manifest "
            + "receivers")
    public void testInstallDependentFeatureSplits_full() throws Exception {
        testInstallDependentFeatureSplits(false);
    }

    private void testInstallDependentFeatureSplits(boolean instant) throws Exception {
        new InstallMultiple(instant)
                .addFile(APK_BASE).addFile(APK_FEATURE_A).addFile(APK_FEATURE_B).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureADefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureBDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureAAndBReceivers");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInstallDependentFeatureSplitsAndConfigSplits_full() throws Exception {
        testInstallDependentFeatureSplitsAndConfigSplits(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInstallDependentFeatureSplitsAndConfigSplits_instant() throws Exception {
        testInstallDependentFeatureSplitsAndConfigSplits(true);
    }

    private void testInstallDependentFeatureSplitsAndConfigSplits(boolean instant)
            throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_A).addFile(APK_FEATURE_B)
                .addFile(APK_BASE_pl).addFile(APK_FEATURE_A_pl).addFile(APK_FEATURE_B_pl).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadPolishLocale");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureAPolishLocale");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureBPolishLocale");
    }

    @Test
    @AppModeFull(reason = "b/109878606; instant applications can't send broadcasts to manifest "
            + "receivers")
    public void testInstallAllFeatureSplits_full() throws Exception {
        testInstallAllFeatureSplits(false);
    }

    private void testInstallAllFeatureSplits(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_A).addFile(APK_FEATURE_B)
                .addFile(APK_FEATURE_C).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureADefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureBDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureCDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureAAndBAndCReceivers");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInstallAllFeatureSplitsAndConfigSplits_full() throws Exception {
        testInstallAllFeatureSplitsAndConfigSplits(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInstallAllFeatureSplitsAndConfigSplits_instant() throws Exception {
        testInstallAllFeatureSplitsAndConfigSplits(true);
    }

    private void testInstallAllFeatureSplitsAndConfigSplits(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_A).addFile(APK_FEATURE_B)
                .addFile(APK_FEATURE_C).addFile(APK_BASE_pl).addFile(APK_FEATURE_A_pl)
                .addFile(APK_FEATURE_C_pl).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureADefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureBDefault");
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureCDefault");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testSplitsInheritInstall_full() throws Exception {
        testSplitsInheritInstall(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testSplitsInheritInstall_instant() throws Exception {
        testSplitsInheritInstall(true);
    }

    private void testSplitsInheritInstall(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_A).addFile(APK_FEATURE_B)
                .addFile(APK_FEATURE_C).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureADefault");

        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_FEATURE_A_DiffRev).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureADiffRevision");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testSplitsRemoved_full() throws Exception {
        testSplitsRemoved(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testSplitsRemoved_instant() throws Exception {
        testSplitsRemoved(true);
    }

    private void testSplitsRemoved(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_BASE).addFile(APK_FEATURE_A).addFile(APK_FEATURE_B)
                .addFile(APK_FEATURE_C).run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldLoadFeatureCDefault");

        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("feature_c").run();
        Utils.runDeviceTestsAsCurrentUser(getDevice(), PKG, TEST_CLASS,
                "shouldNotFoundFeatureC");
    }

    private InstallMultiple configureInstallMultiple(boolean instant, String...apks)
            throws FileNotFoundException {
        InstallMultiple installMultiple = new InstallMultiple(instant);
        for (String apk : apks) {
            installMultiple.addFile(apk);
        }
        return installMultiple;
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseFalse_splitTrue_full()
            throws Exception {
        configureInstallMultiple(false, APK_BASE_WITHOUT_EXTRACTING,
                APK_FEATURE_JNI_WITH_EXTRACTING, APK_FEATURE_PROXY_WITH_EXTRACTING,
                APK_FEATURE_PROVIDER_A_WITH_EXTRACTING).runExpectingFailure(
                "INSTALL_FAILED_INVALID_APK");
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseFalse_splitTrue_instant()
            throws Exception {
        configureInstallMultiple(true, APK_BASE_WITHOUT_EXTRACTING, APK_FEATURE_JNI_WITH_EXTRACTING,
                APK_FEATURE_PROXY_WITH_EXTRACTING,
                APK_FEATURE_PROVIDER_A_WITH_EXTRACTING).runExpectingFailure(
                "INSTALL_FAILED_INVALID_APK");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseFalse_splitFalse_full()
            throws Exception {
        configureInstallMultiple(false, APK_BASE_WITHOUT_EXTRACTING,
                APK_FEATURE_JNI_WITHOUT_EXTRACTING, APK_FEATURE_PROXY_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING).run();
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseFalse_splitFalse_instant()
            throws Exception {
        configureInstallMultiple(true, APK_BASE_WITHOUT_EXTRACTING,
                APK_FEATURE_JNI_WITHOUT_EXTRACTING, APK_FEATURE_PROXY_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING).run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseTrue_splitTrue_full()
            throws Exception {
        configureInstallMultiple(false, APK_BASE_WITH_EXTRACTING,
                APK_FEATURE_JNI_WITH_EXTRACTING,
                APK_FEATURE_PROXY_WITH_EXTRACTING, APK_FEATURE_PROVIDER_A_WITH_EXTRACTING).run();
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseTrue_splitTrue_instant()
            throws Exception {
        configureInstallMultiple(true, APK_BASE_WITH_EXTRACTING, APK_FEATURE_JNI_WITH_EXTRACTING,
                APK_FEATURE_PROXY_WITH_EXTRACTING, APK_FEATURE_PROVIDER_A_WITH_EXTRACTING).run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseTrue_splitFalse_full()
            throws Exception {
        configureInstallMultiple(false, APK_BASE_WITH_EXTRACTING,
                APK_FEATURE_JNI_WITHOUT_EXTRACTING, APK_FEATURE_PROXY_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING).run();
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeInstallable_extractNativeLibs_baseTrue_splitFalse_instant()
            throws Exception {
        configureInstallMultiple(true, APK_BASE_WITH_EXTRACTING, APK_FEATURE_JNI_WITHOUT_EXTRACTING,
                APK_FEATURE_PROXY_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING).run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testAccessNativeSymbol_bothBaseAndSplitExtracting_full() throws Exception {
        testAccessNativeSymbol(false, true, APK_BASE_WITH_EXTRACTING,
                APK_FEATURE_JNI_WITH_EXTRACTING, APK_FEATURE_PROVIDER_A_WITH_EXTRACTING,
                APK_FEATURE_PROVIDER_B_WITH_EXTRACTING, APK_FEATURE_PROXY_WITH_EXTRACTING);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testAccessNativeSymbol_bothBaseAndSplitExtracting_instant() throws Exception {
        testAccessNativeSymbol(true, true, APK_BASE_WITH_EXTRACTING,
                APK_FEATURE_JNI_WITH_EXTRACTING, APK_FEATURE_PROVIDER_A_WITH_EXTRACTING,
                APK_FEATURE_PROVIDER_B_WITH_EXTRACTING, APK_FEATURE_PROXY_WITH_EXTRACTING);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testAccessNativeSymbol_onlyBaseExtracting_full() throws Exception {
        testAccessNativeSymbol(false, true, APK_BASE_WITH_EXTRACTING,
                APK_FEATURE_JNI_WITHOUT_EXTRACTING, APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_B_WITHOUT_EXTRACTING, APK_FEATURE_PROXY_WITHOUT_EXTRACTING);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testAccessNativeSymbol_onlyBaseExtracting_instant() throws Exception {
        testAccessNativeSymbol(true, true, APK_BASE_WITH_EXTRACTING,
                APK_FEATURE_JNI_WITHOUT_EXTRACTING, APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_B_WITHOUT_EXTRACTING, APK_FEATURE_PROXY_WITHOUT_EXTRACTING);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testAccessNativeSymbol_neitherBaseNorSplitExtracting_full() throws Exception {
        testAccessNativeSymbol(false, false, APK_BASE_WITHOUT_EXTRACTING,
                APK_FEATURE_JNI_WITHOUT_EXTRACTING, APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_B_WITHOUT_EXTRACTING, APK_FEATURE_PROXY_WITHOUT_EXTRACTING);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testAccessNativeSymbol_neitherBaseNorSplitExtracting_instant() throws Exception {
        testAccessNativeSymbol(true, false, APK_BASE_WITHOUT_EXTRACTING,
                APK_FEATURE_JNI_WITHOUT_EXTRACTING, APK_FEATURE_PROVIDER_A_WITHOUT_EXTRACTING,
                APK_FEATURE_PROVIDER_B_WITHOUT_EXTRACTING, APK_FEATURE_PROXY_WITHOUT_EXTRACTING);
    }

    private void testAccessNativeSymbol(boolean instant, boolean expectedLoadedLibrary,
            String baseApk, String jniApk, String providerAApk, String providerBApk,
            String providerProxyApk) throws Exception {
        configureInstallMultiple(instant, baseApk, jniApk, providerAApk, providerBApk,
                providerProxyApk).run();
        if (expectedLoadedLibrary) {
            runDeviceTests(PKG, TEST_CLASS, "testNative_getNumberAViaProxy_shouldBeSeven");
            runDeviceTests(PKG, TEST_CLASS, "testNative_getNumberBDirectly_shouldBeEleven");
            runDeviceTests(PKG, TEST_CLASS, "testNative_getNumberADirectly_shouldBeSeven");
            runDeviceTests(PKG, TEST_CLASS, "testNative_getNumberBViaProxy_shouldBeEleven");
        } else {
            runDeviceTests(PKG, TEST_CLASS, "testNative_cannotLoadSharedLibrary");
            runDeviceTests(
                    PKG,
                    TEST_CLASS,
                    "testNativeSplit_withoutExtractLibs_nativeLibraryCannotBeLoaded");
        }
    }
}
