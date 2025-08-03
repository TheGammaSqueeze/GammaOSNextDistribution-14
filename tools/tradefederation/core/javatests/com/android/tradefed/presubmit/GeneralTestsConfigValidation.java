/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.presubmit;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.PushFilePreparer;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IsolatedHostTest;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.ValidateSuiteConfigHelper;
import com.android.tradefed.testtype.suite.params.ModuleParameters;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ModuleTestTypeUtil;

import com.google.common.base.Joiner;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation tests to run against the configuration in general-tests.zip to ensure they can all
 * parse.
 *
 * <p>Do not add to UnitTests.java. This is meant to run standalone.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class GeneralTestsConfigValidation implements IBuildReceiver {

    @Option(
            name = "config-extension",
            description = "The expected extension from configuration to check.")
    private String mConfigExtension = "config";

    @Option(
            name = "disallowed-test-type",
            description = "The disallowed test type for configs in general-tests.zip")
    private List<String> mDisallowedTestTypes = new ArrayList<>();

    private IBuildInfo mBuild;

    /**
     * List of the officially supported runners in general-tests. Any new addition should go through
     * a review to ensure all runners have a high quality bar.
     */
    private static final Set<String> SUPPORTED_TEST_RUNNERS =
            new HashSet<>(
                    Arrays.asList(
                            // Cts runners
                            "com.android.compatibility.common.tradefed.testtype.JarHostTest",
                            "com.android.compatibility.testtype.DalvikTest",
                            "com.android.compatibility.testtype.LibcoreTest",
                            "com.drawelements.deqp.runner.DeqpTestRunner",
                            // Tradefed runners
                            "com.android.tradefed.testtype.UiAutomatorTest",
                            "com.android.tradefed.testtype.InstrumentationTest",
                            "com.android.tradefed.testtype.AndroidJUnitTest",
                            "com.android.tradefed.testtype.HostTest",
                            "com.android.tradefed.testtype.GTest",
                            "com.android.tradefed.testtype.HostGTest",
                            "com.android.tradefed.testtype.GoogleBenchmarkTest",
                            "com.android.tradefed.testtype.IsolatedHostTest",
                            "com.android.tradefed.testtype.python.PythonBinaryHostTest",
                            "com.android.tradefed.testtype.binary.ExecutableHostTest",
                            "com.android.tradefed.testtype.binary.ExecutableTargetTest",
                            "com.android.tradefed.testtype.rust.RustBinaryHostTest",
                            "com.android.tradefed.testtype.rust.RustBinaryTest",
                            "com.android.tradefed.testtype.StubTest",
                            "com.android.tradefed.testtype.ArtRunTest",
                            "com.android.tradefed.testtype.ArtGTest",
                            "com.android.tradefed.testtype.mobly.MoblyBinaryHostTest",
                            "com.android.tradefed.testtype.pandora.PtsBotTest",
                            // VTS runners
                            "com.android.tradefed.testtype.binary.KernelTargetTest",
                            // Others
                            "com.google.android.deviceconfig.RebootTest",
                            "com.android.scenario.AppSetup"));

    /**
     * List of configs that will be exempted until they are converted to use MediaPreparers.
     * (b/274674920)
     */
    private static final Set<String> MEDIAPREPARER_EXEMPTED_CONFIGS =
            new HashSet<>(
                    Arrays.asList(
                            "OpusHeaderTest.config",
                            "AmrnbEncoderTest.config",
                            "AmrnbDecoderTest.config",
                            "AmrwbEncoderTest.config",
                            "AmrwbDecoderTest.config",
                            "HEVCUtilsUnitTest.config",
                            "ExtractorUnitTest.config",
                            "MediaTranscoderBenchmark.config",
                            "TimedTextUnitTest.config",
                            "VorbisDecoderTest.config",
                            "MediaTrackTranscoderBenchmark.config",
                            "ID3Test.config",
                            "ExtractorFactoryTest.config",
                            "MediaSampleReaderBenchmark.config",
                            "Mpeg4H263EncoderTest.config",
                            "Mp3DecoderTest.config",
                            "Mpeg2tsUnitTest.config",
                            "Mpeg4H263DecoderTest.config"));

    /** List of configs that will be exempted until b/274930471 is fixed. */
    private static final Set<String> EXEMPTED_PYTHON_TEST_MODULES =
            new HashSet<>(
                    Arrays.asList(
                            "aidl_integration_test.config",
                            "hidl_test.config",
                            "hidl_test_java.config",
                            "fmq_test.config"));
    /** List of configs to exclude until b/277261121 is fixed. */
    private static final Set<String> EXEMPTED_KERNEL_MODULES =
            new HashSet<>(
                    Arrays.asList(
                            "vts_ltp_test_arm_64.config",
                            "vts_ltp_test_arm_64_lowmem.config",
                            "vts_ltp_test_arm_64_hwasan.config",
                            "vts_ltp_test_arm_64_lowmem_hwasan.config",
                            "vts_ltp_test_arm.config",
                            "vts_ltp_test_arm_lowmem.config",
                            "vts_ltp_test_x86_64.config",
                            "vts_ltp_test_x86.config",
                            "vts_linux_kselftest_arm_64.config",
                            "vts_linux_kselftest_arm_32.config",
                            "vts_linux_kselftest_x86_64.config",
                            "vts_linux_kselftest_x86_32.config",
                            "vts_linux_kselftest_riscv_64.config"));

    /**
     * Temporarily exempt the current configs so that the test can be submitted to block new
     * configs.
     */
    private static final Set<String> TEMP_EXEMPTED_MODULES =
            new HashSet<>(
                    Arrays.asList(
                            "PtsStorageFuncTestCases.config",
                            "PtsPowerTestCases.config",
                            "PtsPerformanceLongTestCases.config",
                            "FirmwareDtboVerification.config",
                            "net_unittests_tester.config",
                            "PerfStressTests.config",
                            "binderHostDeviceTest.config",
                            "PerfUiGfxTests.config",
                            "PtsStorageUITestCases.config",
                            "PtsStoragePerfTestCases.config",
                            "PtsNgaTestCases.config",
                            "PerfUiMiscTests.config",
                            "GtsStatsdHostTestCases.config",
                            "PtsBackupHostSideTestCases.config",
                            "PtsStorageQualTestCases.config",
                            "PtsStoragePowerTestCases.config",
                            "PtsUipbUnitTests.config",
                            "PtsSensorHostTestCases.config",
                            "PerfCheckTests.config",
                            "cronet_unittests_tester.config",
                            "PerfUiPreconditionTest.config",
                            "PtsStorageLongTestCases.config",
                            "CtsAdServicesCUJTestCases.config",
                            "hwuimacro.config",
                            "libinputserialtracker_tests.config",
                            "MediaProviderTests.config",
                            "libsurfaceflinger_arc_test.config",
                            "PtsCoolingMapTests.config",
                            "hwuimicro.config",
                            "rustBinderTestService.config",
                            "hwui_unit_tests.config",
                            "libinputreader_arc_tests.config",
                            "PtsTpuPwrStateTests.config",
                            "CtsAdExtServicesCUJTestCases.config",
                            "InteractiveNeneTest.config",
                            "SdkSandboxPerfScenarioTests.config",
                            "libinputreporter_arc_tests.config",
                            "libwayland_service_tests.config",
                            "messagingtests.config",
                            "GtsPermissionTestCases.config",
                            "GtsReadLogStringTest.config",
                            "rustBinderTest.config",
                            "libsurfaceflinger_arc_backend_test.config",
                            "MicrodroidBenchmarkApp.config",
                            "OverlayHostTests.config",
                            "ComponentAliasTests.config",
                            "WMShellFlickerTests.config",
                            "AppEnumerationInternalTests.config",
                            "ComponentAliasTests2.config",
                            "ComponentAliasTests1.config",
                            "NeuralNetworksApiCrashTest.config",
                            "FrameworksServicesTests.config",
                            "MediaSampleQueueTests.config",
                            "HdrTranscodeTests.config",
                            "MediaSampleReaderNDKTests.config",
                            "MediaTrackTranscoderTests.config",
                            "PassthroughTrackTranscoderTests.config",
                            "MediaTranscoderTests.config",
                            "VideoTrackTranscoderTests.config",
                            "MediaSampleWriterTests.config",
                            "art-run-test-656-checker-simd-opt.config",
                            "PtsChreTestCases.config",
                            "chre_nanoapps_loaded.config",
                            "BiometricsMicrobenchmark.config",
                            "GoogleSearchPrebuiltDebug.config",
                            "SystemUIMicrobenchmark.config",
                            "PlatformScenarioTests.config",
                            "UiBenchMicrobenchmark_Internal.config",
                            "CellBroadcastReceiverGoogleUnitTests.config",
                            "fixed-appstartup-login-base.config",
                            "open-fixed-calculator.config",
                            "fixed-appstartup-base.config",
                            "open-prebuilt-maps.config",
                            "transition-coldlaunch-phone.config",
                            "transition-hot-applaunch-from-qs-base.config",
                            "open-fixed-messages-warm.config",
                            "transition-hotlaunch-gmail.config",
                            "open-fixed-chrome-hot.config",
                            "open-fixed-maps.config",
                            "open-prebuilt-photos.config",
                            "open-fixed-phone.config",
                            "prebuilt-appstartup-login-base.config",
                            "open-fixed-calculator-flicker.config",
                            "open-fixed-contacts.config",
                            "transition-hotlaunch-messages.config",
                            "open-fixed-gmail-hot.config",
                            "transition-hotlaunch-calculator.config",
                            "transition-hotlaunch-maps.config",
                            "open-fixed-gmail-warm.config",
                            "open-fixed-calculator-hot.config",
                            "open-prebuilt-gmail.config",
                            "transition-coldlaunch-chrome.config",
                            "open-fixed-chrome.config",
                            "transition-coldlaunch-maps.config",
                            "transition-coldlaunch-messages.config",
                            "transition-hotlaunch-from-qs-calculator.config",
                            "open-fixed-youtube.config",
                            "open-prebuilt-clock.config",
                            "open-prebuilt-youtube.config",
                            "transition-hotlaunch-phone.config",
                            "transition-hot-applaunch-from-qs-login-base.config",
                            "prebuilt-appstartup-base.config",
                            "open-prebuilt-contacts.config",
                            "transition-coldlaunch-gmail.config",
                            "open-fixed-calculator-warm.config",
                            "appstartup-base.config",
                            "transition-hotlaunch-from-qs-phone.config",
                            "AppMicrobenchmark.config",
                            "open-fixed-clock.config",
                            "open-prebuilt-phone.config",
                            "transition-hotlaunch-from-qs-gmail.config",
                            "open-prebuilt-calendar.config",
                            "open-fixed-chrome-warm.config",
                            "open-fixed-calendar.config",
                            "transition-hot-applaunch-login-base.config",
                            "transition-hotlaunch-chrome.config",
                            "open-prebuilt-calculator.config",
                            "open-fixed-phone-hot.config",
                            "transition-hot-applaunch-base.config",
                            "transition-coldlaunch-calculator.config",
                            "open-fixed-photos.config",
                            "transition-hotlaunch-from-qs-chrome.config",
                            "transition-hotlaunch-from-qs-maps.config",
                            "open-fixed-messages.config",
                            "open-prebuilt-camera.config",
                            "open-fixed-phone-warm.config",
                            "transition-hotlaunch-from-qs-messages.config",
                            "open-prebuilt-messages.config",
                            "open-fixed-messages-hot.config",
                            "open-fixed-camera.config",
                            "open-fixed-gmail.config",
                            "GoogleSearchPrebuiltDebugService.config",
                            "UiBenchJankTests_Internal.config",
                            "HubUIScenarioTests.config",
                            "LauncherMicrobenchmark.config",
                            "MultitaskingTests.config",
                            "art-run-test-156-register-dex-file-multi-loader.config",
                            "PtsKmsVBlankTestCases.config",
                            "PtsGemBltTestCases.config",
                            "PtsSyncobjBasicTestCases.config",
                            "PtsKmsAddfbBasicTestCases.config",
                            "PtsKmsAtomicTransitionTestCases.config",
                            "PtsKmsThroughputTestCases.config",
                            "CollectorsHelperTest.config",
                            "PtsKmsAtomicInterruptibleTestCases.config",
                            "PtsKmsAtomicTestCases.config",
                            "PtsKmsPropBlobTestCases.config",
                            "PtsSyncobjWaitTestCases.config",
                            "PtsKmsPropertiesTestCases.config",
                            "PtsKmsPlaneScalingTestCases.config",
                            "PtsCoreAuthTestCases.config",
                            "PtsCoreGetclientTestCases.config",
                            "PtsKmsGetfbTestCases.config",
                            "PtsKmsFlipTestCases.config",
                            "s2-geometry-library-java-tests.config"));

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    /** Get all the configuration copied to the build tests dir and check if they load. */
    @Test
    public void testConfigsLoad() throws Exception {
        List<String> errors = new ArrayList<>();
        Assume.assumeTrue(mBuild instanceof IDeviceBuildInfo);

        IConfigurationFactory configFactory = ConfigurationFactory.getInstance();
        List<File> configs = new ArrayList<>();
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) mBuild;
        File testsDir = deviceBuildInfo.getTestsDir();
        List<File> extraTestCasesDirs = Arrays.asList(testsDir);
        String configPattern = ".*\\." + mConfigExtension + "$";
        // include config files with same name, but with different contents (for example: host and
        // device variants of the same config).
        configs.addAll(
                ConfigurationUtil.getConfigNamesFileFromDirs(
                        null, extraTestCasesDirs, Arrays.asList(configPattern), true));
        for (File config : configs) {
            try {
                IConfiguration c =
                        configFactory.createConfigurationFromArgs(
                                new String[] {config.getAbsolutePath()});
                // All configurations in general-tests.zip should be module since they are generated
                // from AndroidTest.xml
                ValidateSuiteConfigHelper.validateConfig(c);

                for (IDeviceConfiguration dConfig : c.getDeviceConfig()) {
                    validatePreparers(c, config, dConfig.getTargetPreparers());
                }
                // Check that all the tests runners are well supported.
                checkRunners(c.getTests(), "general-tests");

                ConfigurationDescriptor cd = c.getConfigurationDescription();
                checkModuleParameters(c.getName(), cd.getMetaData(ITestSuite.PARAMETER_KEY));

                // Check for disallowed test types
                checkDisallowedTestType(c, mDisallowedTestTypes);

                // Add more checks if necessary
            } catch (ConfigurationException e) {
                errors.add(String.format("\t%s: %s", config.getName(), e.getMessage()));
            }
        }

        // If any errors report them in a final exception.
        if (!errors.isEmpty()) {
            throw new ConfigurationException(
                    String.format("Fail configuration check:\n%s", Joiner.on("\n").join(errors)));
        }
    }

    public static void validatePreparers(
            IConfiguration c, File config, List<ITargetPreparer> preparers) throws Exception {
        if (EXEMPTED_PYTHON_TEST_MODULES.contains(config.getName())) {
            LogUtil.CLog.w(
                    "Module %s is a python_test_host module. Ignoring until b/274930471 is fixed.s",
                    config.getName());
            return;
        }
        if (EXEMPTED_KERNEL_MODULES.contains(config.getName())) {
            LogUtil.CLog.w("Ignoring module %s until b/277261121 is fixed.s", config.getName());
            return;
        }
        if (MEDIAPREPARER_EXEMPTED_CONFIGS.contains(config.getName())) {
            LogUtil.CLog.w(
                    "Module %s is exempted until b/274674920 is fixed. Please Fix the config.",
                    config.getName());
            return;
        }
        if (TEMP_EXEMPTED_MODULES.contains(config.getName())) {
            LogUtil.CLog.w("Ignoring module %s temporarily.", config.getName());
            return;
        }
        for (ITargetPreparer preparer : preparers) {
            if (preparer instanceof TestAppInstallSetup) {
                List<File> apkNames = new ArrayList<>();
                TestAppInstallSetup installer = (TestAppInstallSetup) preparer;
                // Ensure clean up is enabled
                if (!installer.isCleanUpEnabled()) {
                    throw new ConfigurationException(
                            String.format("Config: %s should set cleanup-apks=true.", config));
                }
                apkNames.addAll(((TestAppInstallSetup) preparer).getTestsFileName());

                // Ensure all apk dependencies are specified
                for (File apk : apkNames) {
                    String apkName = apk.getName();
                    File apkFile = FileUtil.findFile(config.getParentFile(), apkName);
                    if (apkFile == null || !apkFile.exists()) {
                        throw new ConfigurationException(
                                String.format(
                                        "Module %s is trying to install %s which does not "
                                                + "exists in testcases/. Make sure that it's added "
                                                + "in the Android.bp file of the module under "
                                                + "'data' field.",
                                        config.getName(), apkName));
                    }
                }
            }
            if (preparer instanceof PushFilePreparer) {
                PushFilePreparer pusher = (PushFilePreparer) preparer;
                if (!pusher.isCleanUpEnabled()) {
                    throw new ConfigurationException(
                            String.format(
                                    "Config: %s should set cleanup=true for file pusher.", config));
                }
                boolean isPerfModule = ModuleTestTypeUtil.isPerformanceModule(c);
                for (File f : pusher.getPushSpecs(null).values()) {
                    String path = f.getPath();
                    // Use findFiles to also match top-level dir, which is a valid push spec
                    Set<String> toBePushed = FileUtil.findFiles(config.getParentFile(), path);
                    if (toBePushed.isEmpty()) {
                        // allow performance modules to specify dynamic links
                        if (isPerfModule) {
                            URI uri = new URI(path);
                            if (uri.getScheme() != null
                                    && (uri.getScheme().contains("gs")
                                            || uri.getScheme().contains("http"))) {
                                continue;
                            }
                        }
                        // See if binary files exists
                        File file32 = FileUtil.findFile(config.getParentFile(), path + "32");
                        File file64 = FileUtil.findFile(config.getParentFile(), path + "64");
                        if (file32 == null || file64 == null) {
                            throw new ConfigurationException(
                                    String.format(
                                            "File %s wasn't found in module dependencies while it's"
                                                    + " expected to be pushed as part of %s. Make"
                                                    + " sure that it's added in the Android.bp file"
                                                    + " of the module under 'data_device_bins_both'"
                                                    + " field if it's a binary file or under 'data'"
                                                    + " field for all other files.",
                                            path, config.getName()));
                        }
                    }
                }
            }
        }
    }

    public static void checkRunners(List<IRemoteTest> tests, String name)
            throws ConfigurationException {
        for (IRemoteTest test : tests) {
            // Check that all the tests runners are well supported.
            if (!SUPPORTED_TEST_RUNNERS.contains(test.getClass().getCanonicalName())) {
                throw new ConfigurationException(
                        String.format(
                                "testtype %s is not officially supported in %s. "
                                        + "The supported ones are: %s",
                                test.getClass().getCanonicalName(), name, SUPPORTED_TEST_RUNNERS));
            }
            if (test instanceof IsolatedHostTest
                    && ((IsolatedHostTest) test).useRobolectricResources()) {
                throw new ConfigurationException(
                        String.format(
                                "Robolectric tests aren't supported in general-tests yet. They"
                                        + " have their own setup."));
            }
        }
    }

    public static void checkModuleParameters(String configName, List<String> parameters)
            throws ConfigurationException {
        if (parameters == null) {
            return;
        }
        for (String param : parameters) {
            try {
                ModuleParameters.valueOf(param.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(
                        String.format(
                                "Config: %s includes an unknown parameter '%s'.",
                                configName, param));
            }
        }
    }

    /**
     * Check the {@link config} to ensure it's not declared as one of the {#link
     * disallowedTestTypes}.
     *
     * @param config The config to check.
     * @param ConfigurationException The disallowed test types to check against.
     * @throws ConfigurationException if the config is of disallowed test types.
     */
    public static void checkDisallowedTestType(
            IConfiguration config, List<String> disallowedTestTypes) throws ConfigurationException {
        if (disallowedTestTypes == null || disallowedTestTypes.isEmpty()) {
            return;
        }

        List<String> matched =
                ModuleTestTypeUtil.getMatchedConfigTestTypes(config, disallowedTestTypes);
        if (!matched.isEmpty()) {
            throw new ConfigurationException(
                    String.format(
                            "Config %s of test type '%s' is not allowed.",
                            config.getName(), Joiner.on(", ").join(matched)));
        }
    }
}
