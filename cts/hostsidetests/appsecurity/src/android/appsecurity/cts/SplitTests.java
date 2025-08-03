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

package android.appsecurity.cts;

import static android.appsecurity.cts.Utils.ABI_TO_APK;
import static android.appsecurity.cts.Utils.APK;
import static android.appsecurity.cts.Utils.APK_hdpi;
import static android.appsecurity.cts.Utils.APK_mdpi;
import static android.appsecurity.cts.Utils.APK_xhdpi;
import static android.appsecurity.cts.Utils.APK_xxhdpi;
import static android.appsecurity.cts.Utils.CLASS;
import static android.appsecurity.cts.Utils.PKG;

import static org.junit.Assert.assertNotNull;

import android.content.pm.Flags;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.platform.test.annotations.PlatinumTest;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.util.CpuFeatures;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.util.AbiUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Set;

/**
 * Tests that verify installing of various split APKs from host side.
 */
@Presubmit
@PlatinumTest(focusArea = "pm")
@RunWith(DeviceJUnit4ClassRunner.class)
public class SplitTests extends BaseAppSecurityTest {
    static final String PKG_NO_RESTART = "com.android.cts.norestart";
    static final String APK_NO_RESTART_BASE = "CtsNoRestartBase.apk";
    static final String APK_NO_RESTART_FEATURE = "CtsNoRestartFeature.apk";

    static final String APK_NEED_SPLIT_BASE = "CtsNeedSplitApp.apk";
    static final String APK_NEED_SPLIT_FEATURE_WARM = "CtsNeedSplitFeatureWarm.apk";
    static final String APK_NEED_SPLIT_CONFIG = "CtsNeedSplitApp_xxhdpi-v4.apk";

    static final String APK_REQUIRED_SPLIT_TYPE_BASE = "CtsRequiredSplitTypeSplitApp.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_BASE_UPDATED =
            "CtsRequiredSplitTypeSplitAppUpdated.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_DENSITY = "CtsSplitAppTypeDensity.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_LOCALE = "CtsSplitAppTypeLocale.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_FOO = "CtsSplitAppTypeFoo.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_MULTIPLE = "CtsSplitAppTypeMultiple.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_FEATURE = "CtsSplitAppTypeFeature.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_FEATURE_DATA = "CtsSplitAppTypeFeatureData.apk";
    static final String APK_REQUIRED_SPLIT_TYPE_FEATURE_FOO = "CtsSplitAppTypeFeatureFoo.apk";
    static final String APK_INVALID_REQUIRED_SPLIT_TYPE_BASE =
            "CtsInvalidRequiredSplitTypeSplitApp.apk";

    private static final String APK_v7 = "CtsSplitApp_v7.apk";
    private static final String APK_v23 = "CtsSplitApp_v23.apk";
    private static final String APK_fr = "CtsSplitApp_fr.apk";
    private static final String APK_de = "CtsSplitApp_de.apk";

    private static final String APK_NUMBER_PROVIDER_A = "CtsSplitApp_number_provider_a.apk";
    private static final String APK_NUMBER_PROVIDER_B = "CtsSplitApp_number_provider_b.apk";
    private static final String APK_NUMBER_PROXY = "CtsSplitApp_number_proxy.apk";

    private static final String APK_DIFF_REVISION = "CtsSplitAppDiffRevision.apk";
    private static final String APK_DIFF_REVISION_v7 = "CtsSplitAppDiffRevision_v7.apk";

    private static final String APK_DIFF_VERSION = "CtsSplitAppDiffVersion.apk";
    private static final String APK_DIFF_VERSION_v7 = "CtsSplitAppDiffVersion_v7.apk";

    private static final String APK_DIFF_CERT = "CtsSplitAppDiffCert.apk";
    private static final String APK_DIFF_CERT_v7 = "CtsSplitAppDiffCert_v7.apk";

    private static final String APK_FEATURE_WARM = "CtsSplitAppFeatureWarm.apk";
    private static final String APK_FEATURE_WARM_v7 = "CtsSplitAppFeatureWarm_v7.apk";
    private static final String APK_FEATURE_WARM_v23 = "CtsSplitAppFeatureWarm_v23.apk";

    private static final String APK_FEATURE_ROSE = "CtsSplitAppFeatureRose.apk";
    private static final String APK_FEATURE_ROSE_v23 = "CtsSplitAppFeatureRose_v23.apk";

    private static final String APK_REVISION_A = "CtsSplitAppRevisionA.apk";
    private static final String APK_FEATURE_WARM_REVISION_A = "CtsSplitAppFeatureWarmRevisionA.apk";
    private static final String BITNESS_32 = "32";
    private static final String BITNESS_64 = "64";

    // Apk includes a provider and service declared in other split apk. And only could be tested in
    // instant app mode.
    static final String APK_INSTANT = "CtsSplitInstantApp.apk";

    static final HashMap<String, String> ABI_TO_REVISION_APK = new HashMap<>();

    static {
        ABI_TO_REVISION_APK.put("x86", "CtsSplitApp_revision12_x86.apk");
        ABI_TO_REVISION_APK.put("x86_64", "CtsSplitApp_revision12_x86_64.apk");
        ABI_TO_REVISION_APK.put("armeabi-v7a", "CtsSplitApp_revision12_armeabi-v7a.apk");
        ABI_TO_REVISION_APK.put("armeabi", "CtsSplitApp_revision12_armeabi.apk");
        ABI_TO_REVISION_APK.put("arm64-v8a", "CtsSplitApp_revision12_arm64-v8a.apk");
        ABI_TO_REVISION_APK.put("mips64", "CtsSplitApp_revision12_mips64.apk");
        ABI_TO_REVISION_APK.put("mips", "CtsSplitApp_revision12_mips.apk");
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private String mDeviceDefaultAbi = null;
    private String mDeviceDefaultBitness = null;
    private String mDeviceDefaultBaseArch = null;
    private String[] mDeviceSupported32BitSet = null;
    private String[] mDeviceSupported64BitSet = null;

    private String[] getDeviceSupported32AbiSet() throws Exception {
        if (mDeviceSupported32BitSet != null) {
            return mDeviceSupported32BitSet;
        }
        mDeviceSupported32BitSet = getDeviceSupportedAbiSet(BITNESS_32);
        return mDeviceSupported32BitSet;
    }

    private String[] getDeviceSupported64AbiSet() throws Exception {
        if (mDeviceSupported64BitSet != null) {
            return mDeviceSupported64BitSet;
        }
        mDeviceSupported64BitSet = getDeviceSupportedAbiSet(BITNESS_64);
        return mDeviceSupported64BitSet;
    }

    private String[] getDeviceSupportedAbiSet(String bitness) throws Exception {
        String abis = getDevice().getProperty("ro.product.cpu.abilist" + bitness);
        if (abis == null) {
            return new String[0];
        }
        return abis.split(",");
    }

    private String getDeviceDefaultAbi() throws Exception {
        if (mDeviceDefaultAbi != null) {
            return mDeviceDefaultAbi;
        }
        mDeviceDefaultAbi = getDevice().getProperty("ro.product.cpu.abi");
        return mDeviceDefaultAbi;
    }

    private String getDeviceDefaultBitness() throws Exception {
        if (mDeviceDefaultBitness != null) {
            return mDeviceDefaultBitness;
        }
        mDeviceDefaultBitness = AbiUtils.getBitness(getDeviceDefaultAbi());
        return mDeviceDefaultBitness;
    }

    private String getDeviceDefaultBaseArch() throws Exception {
        if (mDeviceDefaultBaseArch != null) {
            return mDeviceDefaultBaseArch;
        }
        mDeviceDefaultBaseArch = AbiUtils.getBaseArchForAbi(getDeviceDefaultAbi());
        return mDeviceDefaultBaseArch;
    }

    /*
     * Return true if the device supports both 32 bit and 64 bit ABIs. Otherwise, false.
     */
    private boolean isDeviceSupportedBothBitness() throws Exception {
        final String deviceDefaultBaseArch = getDeviceDefaultBaseArch();
        if (getDeviceDefaultBitness().equals(BITNESS_32)) {
            String[] abis64 = getDeviceSupported64AbiSet();
            for (int i = 0; i < abis64.length; i++) {
                if (AbiUtils.getBaseArchForAbi(abis64[i]).equals(deviceDefaultBaseArch)) {
                    return true;
                }
            }
        } else {
            String[] abis32 = getDeviceSupported32AbiSet();
            for (int i = 0; i < abis32.length; i++) {
                if (AbiUtils.getBaseArchForAbi(abis32[i]).equals(deviceDefaultBaseArch)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Before
    public void setUp() throws Exception {
        Utils.prepareSingleUser(getDevice());
        getDevice().uninstallPackage(PKG);
        getDevice().uninstallPackage(PKG_NO_RESTART);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PKG);
        getDevice().uninstallPackage(PKG_NO_RESTART);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testSingleBase_full() throws Exception {
        testSingleBase(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testSingleBase_instant() throws Exception {
        testSingleBase(true);
    }
    private void testSingleBase(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).run();
        runDeviceTests(PKG, CLASS, "testSingleBase");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDensitySingle_full() throws Exception {
        testDensitySingle(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDensitySingle_instant() throws Exception {
        testDensitySingle(true);
    }
    private void testDensitySingle(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_mdpi).run();
        runDeviceTests(PKG, CLASS, "testDensitySingle");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDensityAll_full() throws Exception {
        testDensityAll(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDensityAll_instant() throws Exception {
        testDensityAll(true);
    }
    private void testDensityAll(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_mdpi).addFile(APK_hdpi).addFile(APK_xhdpi)
                .addFile(APK_xxhdpi).run();
        runDeviceTests(PKG, CLASS, "testDensityAll");
    }

    /**
     * Install first with low-resolution resources, then add a split that offers
     * higher-resolution resources.
     */
    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDensityBest_full() throws Exception {
        testDensityBest(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDensityBest_instant() throws Exception {
        testDensityBest(true);
    }
    private void testDensityBest(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_mdpi).run();
        runDeviceTests(PKG, CLASS, "testDensityBest1");

        // Now splice in an additional split which offers better resources
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_xxhdpi).run();
        runDeviceTests(PKG, CLASS, "testDensityBest2");
    }

    /**
     * Verify that an API-based split can change enabled/disabled state of
     * manifest elements.
     */
    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testApi_full() throws Exception {
        testApi(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testApi_instant() throws Exception {
        testApi(true);
    }
    private void testApi(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v7).run();
        runDeviceTests(PKG, CLASS, "testApi");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testLocale_full() throws Exception {
        testLocale(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testLocale_instant() throws Exception {
        testLocale(true);
    }
    private void testLocale(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_de).addFile(APK_fr).run();
        runDeviceTests(PKG, CLASS, "testLocale");
    }

    /**
     * Install test app with <em>single</em> split that exactly matches the
     * currently active ABI. This also explicitly forces ABI when installing.
     */
    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeSingle_full() throws Exception {
        testNativeSingle(false, false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeSingle_instant() throws Exception {
        testNativeSingle(true, false);
    }

    private InstallMultiple getInstallMultiple(boolean instant, boolean useNaturalAbi) {
        final InstallMultiple installMultiple = new InstallMultiple(instant);
        if (useNaturalAbi) {
            return installMultiple.useNaturalAbi();
        }
        return installMultiple;
    }

    private void testNativeSingle_assertFail(boolean instant, boolean useNaturalAbi,
            String failure) throws Exception {
        Assume.assumeTrue(isDeviceSupportedBothBitness());
        final String abi = getAbi().getName();
        final String apk = ABI_TO_APK.get(abi);
        assertNotNull("Failed to find APK for ABI " + abi, apk);

        getInstallMultiple(instant, useNaturalAbi)
                .addFile(APK)
                .addFile(apk)
                .run(/* expectingSuccess= */ false, failure);
    }

    private void testNativeSingle(boolean instant, boolean useNaturalAbi) throws Exception {
        final String abi = getAbi().getName();
        final String apk = ABI_TO_APK.get(abi);
        final String revisionApk = ABI_TO_REVISION_APK.get(abi);
        assertNotNull("Failed to find APK for ABI " + abi, apk);

        getInstallMultiple(instant, useNaturalAbi).addFile(APK).addFile(apk).run();
        runDeviceTests(PKG, CLASS, "testNative");
        runDeviceTests(PKG, CLASS, "testNativeRevision_sub_shouldImplementBadly");
        getInstallMultiple(instant, useNaturalAbi).inheritFrom(PKG).addFile(revisionApk).run();
        runDeviceTests(PKG, CLASS, "testNativeRevision_sub_shouldImplementWell");

        getInstallMultiple(instant, useNaturalAbi).inheritFrom(PKG)
                .addFile(APK_NUMBER_PROVIDER_A)
                .addFile(APK_NUMBER_PROVIDER_B)
                .addFile(APK_NUMBER_PROXY).run();
        runDeviceTests(PKG, CLASS, "testNative_getNumberADirectly_shouldBeSeven");
        runDeviceTests(PKG, CLASS, "testNative_getNumberAViaProxy_shouldBeSeven");
        runDeviceTests(PKG, CLASS, "testNative_getNumberBDirectly_shouldBeEleven");
        runDeviceTests(PKG, CLASS, "testNative_getNumberBViaProxy_shouldBeEleven");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeSplitForEachSupportedAbi_full() throws Exception {
        testNativeForEachSupportedAbi(false);
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeSplitForEachSupportedAbi_instant() throws Exception {
        testNativeForEachSupportedAbi(true);
    }


    private void specifyAbiToTest(boolean instant, String abiListProperty, String testMethodName)
            throws FileNotFoundException, DeviceNotAvailableException {
        final String propertyAbiListValue = getDevice().getProperty(abiListProperty);
        final Set<String> supportedAbiSet =
                AbiUtils.parseAbiListFromProperty(propertyAbiListValue);
        for (String abi : supportedAbiSet) {
            String apk = ABI_TO_APK.get(abi);
            new InstallMultiple(instant, true).inheritFrom(PKG).addFile(apk).run();

            // Without specifying abi for executing "adb shell am",
            // a UnsatisfiedLinkError will happen.
            IAbi iAbi = new Abi(abi, AbiUtils.getBitness(abi));
            setAbi(iAbi);
            runDeviceTests(PKG, CLASS, testMethodName);
        }
    }

    private void testNativeForEachSupportedAbi(boolean instant)
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple(instant, true).addFile(APK).run();

        // make sure this device can run both 32 bit and 64 bit
        specifyAbiToTest(instant, "ro.product.cpu.abilist64", "testNative64Bit");
        specifyAbiToTest(instant, "ro.product.cpu.abilist32", "testNative32Bit");
    }

    /**
     * Install test app with <em>single</em> split that exactly matches the
     * currently active ABI. This variant <em>does not</em> force the ABI when
     * installing, instead exercising the system's ability to choose the ABI
     * through inspection of the installed app.
     */
    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    @RequiresFlagsDisabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testNativeSingleNatural_full() throws Exception {
        testNativeSingle(false, true);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    @RequiresFlagsDisabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testNativeSingleNatural_instant() throws Exception {
        testNativeSingle(true, true);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testNativeSingleNatural_full_fail() throws Exception {
        testNativeSingle_assertFail(/* instant= */ false, /* useNaturalAbi= */ true,
                "don't support all the natively supported ABIs of the device");
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testNativeSingleNatural_instant_fail() throws Exception {
        testNativeSingle_assertFail(/* instant= */ true, /* useNaturalAbi= */ true,
                "don't support all the natively supported ABIs of the device");
    }

    private void assumeNativeAbi() throws Exception {
        // Skip this test if not running on the device's native abi.
        Assume.assumeTrue(CpuFeatures.isNativeAbi(getDevice(), getAbi().getName()));
    }
    /**
     * Install test app with <em>all</em> possible ABI splits. This also
     * explicitly forces ABI when installing.
     */
    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeAll_full() throws Exception {
        assumeNativeAbi();
        testNativeAll(false, false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeAll_instant() throws Exception {
        testNativeAll(true, false);
    }
    private void testNativeAll(boolean instant, boolean useNaturalAbi) throws Exception {
        final InstallMultiple inst = getInstallMultiple(instant, useNaturalAbi).addFile(APK);
        for (String apk : ABI_TO_APK.values()) {
            inst.addFile(apk);
        }
        inst.run();
        runDeviceTests(PKG, CLASS, "testNative");
        runDeviceTests(PKG, CLASS, "testNativeRevision_sub_shouldImplementBadly");

        final InstallMultiple instInheritFrom =
                getInstallMultiple(instant, useNaturalAbi).inheritFrom(PKG);
        for (String apk : ABI_TO_REVISION_APK.values()) {
            instInheritFrom.addFile(apk);
        }
        instInheritFrom.addFile(APK_NUMBER_PROVIDER_A);
        instInheritFrom.addFile(APK_NUMBER_PROVIDER_B);
        instInheritFrom.addFile(APK_NUMBER_PROXY);
        instInheritFrom.run();
        runDeviceTests(PKG, CLASS, "testNativeRevision_sub_shouldImplementWell");

        runDeviceTests(PKG, CLASS, "testNative_getNumberADirectly_shouldBeSeven");
        runDeviceTests(PKG, CLASS, "testNative_getNumberAViaProxy_shouldBeSeven");
        runDeviceTests(PKG, CLASS, "testNative_getNumberBDirectly_shouldBeEleven");
        runDeviceTests(PKG, CLASS, "testNative_getNumberBViaProxy_shouldBeEleven");
    }

    /**
     * Install test app with <em>all</em> possible ABI splits. This variant
     * <em>does not</em> force the ABI when installing, instead exercising the
     * system's ability to choose the ABI through inspection of the installed
     * app.
     */
    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testNativeAllNatural_full() throws Exception {
        assumeNativeAbi();
        testNativeAll(false, true);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testNativeAllNatural_instant() throws Exception {
        testNativeAll(true, true);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDuplicateBase_full() throws Exception {
        testDuplicateBase(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDuplicateBase_instant() throws Exception {
        testDuplicateBase(true);
    }
    private void testDuplicateBase(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDuplicateSplit_full() throws Exception {
        testDuplicateSplit(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDuplicateSplit_instant() throws Exception {
        testDuplicateSplit(true);
    }
    private void testDuplicateSplit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v7).addFile(APK_v7).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffCert_full() throws Exception {
        testDiffCert(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffCert_instant() throws Exception {
        testDiffCert(true);
    }
    private void testDiffCert(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_DIFF_CERT_v7).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffCertInherit_full() throws Exception {
        testDiffCertInherit(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffCertInherit_instant() throws Exception {
        testDiffCertInherit(true);
    }
    private void testDiffCertInherit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).run();
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_DIFF_CERT_v7).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffVersion_full() throws Exception {
        testDiffVersion(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffVersion_instant() throws Exception {
        testDiffVersion(true);
    }
    private void testDiffVersion(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_DIFF_VERSION_v7).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffVersionInherit_full() throws Exception {
        testDiffVersionInherit(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffVersionInherit_instant() throws Exception {
        testDiffVersionInherit(true);
    }
    private void testDiffVersionInherit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).run();
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_DIFF_VERSION_v7).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffRevision_full() throws Exception {
        testDiffRevision(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffRevision_instant() throws Exception {
        testDiffRevision(true);
    }
    private void testDiffRevision(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_DIFF_REVISION_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_12");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffRevisionInheritBase_full() throws Exception {
        testDiffRevisionInheritBase(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffRevisionInheritBase_instant() throws Exception {
        testDiffRevisionInheritBase(true);
    }
    private void testDiffRevisionInheritBase(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_0");
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_DIFF_REVISION_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_12");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffRevisionInheritSplit_full() throws Exception {
        testDiffRevisionInheritSplit(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffRevisionInheritSplit_instant() throws Exception {
        testDiffRevisionInheritSplit(true);
    }
    private void testDiffRevisionInheritSplit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_0");
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_DIFF_REVISION).run();
        runDeviceTests(PKG, CLASS, "testRevision12_0");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testDiffRevisionDowngrade_full() throws Exception {
        testDiffRevisionDowngrade(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testDiffRevisionDowngrade_instant() throws Exception {
        testDiffRevisionDowngrade(true);
    }
    private void testDiffRevisionDowngrade(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_DIFF_REVISION_v7).run();
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_v7).runExpectingFailure();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testFeatureWarmBase_full() throws Exception {
        testFeatureWarmBase(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testFeatureWarmBase_instant() throws Exception {
        testFeatureWarmBase(true);
    }
    private void testFeatureWarmBase(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_FEATURE_WARM).run();
        runDeviceTests(PKG, CLASS, "testFeatureWarmBase");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testFeatureWarmApi_full() throws Exception {
        testFeatureWarmApi(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testFeatureWarmApi_instant() throws Exception {
        testFeatureWarmApi(true);
    }
    private void testFeatureWarmApi(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_FEATURE_WARM)
                .addFile(APK_FEATURE_WARM_v7).run();
        runDeviceTests(PKG, CLASS, "testFeatureWarmApi");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInheritUpdatedBase_full() throws Exception {
        testInheritUpdatedBase(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInheritUpdatedBase_instant() throws Exception {
        testInheritUpdatedBase(true);
    }
    public void testInheritUpdatedBase(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_FEATURE_WARM).run();
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_REVISION_A).run();
        runDeviceTests(PKG, CLASS, "testInheritUpdatedBase_withRevisionA", instant);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInheritUpdatedSplit_full() throws Exception {
        testInheritUpdatedSplit(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInheritUpdatedSplit_instant() throws Exception {
        testInheritUpdatedSplit(true);
    }
    private void testInheritUpdatedSplit(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_FEATURE_WARM).run();
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_FEATURE_WARM_REVISION_A).run();
        runDeviceTests(PKG, CLASS, "testInheritUpdatedSplit_withRevisionA", instant);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testFeatureWithoutRestart_full() throws Exception {
        testFeatureWithoutRestart(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testFeatureWithoutRestart_instant() throws Exception {
        testFeatureWithoutRestart(true);
    }
    private void testFeatureWithoutRestart(boolean instant) throws Exception {
        // always install as a full app; we're testing that the instant app can be
        // updated without restarting and need a broadcast receiver to ensure the
        // correct behaviour. So, this component must be visible to instant apps.
        new InstallMultiple().addFile(APK).run();

        new InstallMultiple(instant).addFile(APK_NO_RESTART_BASE).run();
        runDeviceTests(PKG, CLASS, "testBaseInstalled", instant);
        new InstallMultiple(instant)
                .addArg("--dont-kill")
                .inheritFrom(PKG_NO_RESTART)
                .addFile(APK_NO_RESTART_FEATURE)
                .run();
        runDeviceTests(PKG, CLASS, "testFeatureInstalled", instant);
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitMissing_full() throws Exception {
        testRequiredSplitMissing(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitMissing_instant() throws Exception {
        testRequiredSplitMissing(true);
    }
    private void testRequiredSplitMissing(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_NEED_SPLIT_BASE)
                .runExpectingFailure("INSTALL_FAILED_MISSING_SPLIT");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitInstalledFeatureWarm_full() throws Exception {
        testRequiredSplitInstalledFeatureWarm(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitInstalledFeatureWarm_instant() throws Exception {
        testRequiredSplitInstalledFeatureWarm(true);
    }
    private void testRequiredSplitInstalledFeatureWarm(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_NEED_SPLIT_BASE)
                .addFile(APK_NEED_SPLIT_FEATURE_WARM).run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitInstalledConfig_full() throws Exception {
        testRequiredSplitInstalledConfig(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitInstalledConfig_instant() throws Exception {
        testRequiredSplitInstalledConfig(true);
    }
    private void testRequiredSplitInstalledConfig(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_NEED_SPLIT_BASE).addFile(APK_NEED_SPLIT_CONFIG)
                .run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitRemoved_full() throws Exception {
        testRequiredSplitRemoved(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitRemoved_instant() throws Exception {
        testRequiredSplitRemoved(true);
    }
    private void testRequiredSplitRemoved(boolean instant) throws Exception {
        // start with a base and two splits
        new InstallMultiple(instant)
                .addFile(APK_NEED_SPLIT_BASE)
                .addFile(APK_NEED_SPLIT_FEATURE_WARM)
                .addFile(APK_NEED_SPLIT_CONFIG)
                .run();
        // it's okay to remove one of the splits
        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("feature_warm").run();
        // but, not to remove all of them
        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("config.xxhdpi")
                .runExpectingFailure("INSTALL_FAILED_MISSING_SPLIT");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitTypesInstalledIncomplete_full() throws Exception {
        testRequiredSplitTypesInstalledIncomplete(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitTypesInstalledIncomplete_instant() throws Exception {
        testRequiredSplitTypesInstalledIncomplete(true);
    }
    private void testRequiredSplitTypesInstalledIncomplete(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_REQUIRED_SPLIT_TYPE_BASE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_LOCALE)
                .runExpectingFailure("INSTALL_FAILED_MISSING_SPLIT");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInvalidRequiredSplitTypes_full() throws Exception {
        testInvalidRequiredSplitTypes(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInvalidRequiredSplitTypes_instant() throws Exception {
        testInvalidRequiredSplitTypes(true);
    }
    private void testInvalidRequiredSplitTypes(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_INVALID_REQUIRED_SPLIT_TYPE_BASE)
                .runExpectingFailure("INSTALL_PARSE_FAILED_MANIFEST_MALFORMED");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitTypesInstalledAll_full() throws Exception {
        testRequiredSplitTypesInstalledAll(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitTypesInstalledAll_instant() throws Exception {
        testRequiredSplitTypesInstalledAll(true);
    }
    private void testRequiredSplitTypesInstalledAll(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_REQUIRED_SPLIT_TYPE_BASE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_DENSITY)
                .addFile(APK_REQUIRED_SPLIT_TYPE_LOCALE)
                .run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitTypesInstalledMultipleOne_full() throws Exception {
        testRequiredSplitTypesInstalledMultipleOne(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitTypesInstalledMultipleOne_instant() throws Exception {
        testRequiredSplitTypesInstalledMultipleOne(true);
    }
    private void testRequiredSplitTypesInstalledMultipleOne(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK_REQUIRED_SPLIT_TYPE_BASE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_MULTIPLE)
                .run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitTypesRemoved_full() throws Exception {
        testRequiredSplitTypesRemoved(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitTypesRemoved_instant() throws Exception {
        testRequiredSplitTypesRemoved(true);
    }
    private void testRequiredSplitTypesRemoved(boolean instant) throws Exception {
        // start with a base and three splits
        new InstallMultiple(instant)
                .addFile(APK_REQUIRED_SPLIT_TYPE_BASE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_DENSITY)
                .addFile(APK_REQUIRED_SPLIT_TYPE_LOCALE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_FOO)
                .run();
        // it's okay to remove non-required split type
        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("config.foo").run();
        // but, not to remove a required one
        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("config.de")
                .runExpectingFailure("INSTALL_FAILED_MISSING_SPLIT");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInheritUpdatedBase_requiredSplitTypesMissing_full() throws Exception {
        testInheritUpdatedBase_requiredSplitTypesMissing(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInheritUpdatedBase_requiredSplitTypesMissing_instant() throws Exception {
        testInheritUpdatedBase_requiredSplitTypesMissing(true);
    }
    private void testInheritUpdatedBase_requiredSplitTypesMissing(boolean instant)
            throws Exception {
        // start with a base and the required splits
        new InstallMultiple(instant)
                .addFile(APK_REQUIRED_SPLIT_TYPE_BASE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_DENSITY)
                .addFile(APK_REQUIRED_SPLIT_TYPE_LOCALE)
                .run();
        // the updated base requires a new split type, but it's missing
        new InstallMultiple(instant).inheritFrom(PKG).addFile(APK_REQUIRED_SPLIT_TYPE_BASE_UPDATED)
                .runExpectingFailure("INSTALL_FAILED_MISSING_SPLIT");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testInheritUpdatedBase_requiredSplitTypesInstalled_full() throws Exception {
        testInheritUpdatedBase_requiredSplitTypesInstalled(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testInheritUpdatedBase_requiredSplitTypesInstalled_instant() throws Exception {
        testInheritUpdatedBase_requiredSplitTypesInstalled(true);
    }
    private void testInheritUpdatedBase_requiredSplitTypesInstalled(boolean instant)
            throws Exception {
        // start with a base and the required splits
        new InstallMultiple(instant)
                .addFile(APK_REQUIRED_SPLIT_TYPE_BASE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_DENSITY)
                .addFile(APK_REQUIRED_SPLIT_TYPE_LOCALE)
                .run();
        // the updated base requires a split type, and this split also requires another.
        new InstallMultiple(instant).inheritFrom(PKG)
                .addFile(APK_REQUIRED_SPLIT_TYPE_BASE_UPDATED)
                .addFile(APK_REQUIRED_SPLIT_TYPE_FEATURE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_FEATURE_DATA)
                .run();
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testRequiredSplitTypesFromSplit_full() throws Exception {
        testRequiredSplitTypesFromSplit(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testRequiredSplitTypesFromSplit_instant() throws Exception {
        testRequiredSplitTypesFromSplit(true);
    }
    private void testRequiredSplitTypesFromSplit(boolean instant) throws Exception {
        new InstallMultiple(instant)
                .addFile(APK_REQUIRED_SPLIT_TYPE_BASE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_DENSITY)
                .addFile(APK_REQUIRED_SPLIT_TYPE_LOCALE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_FEATURE)
                .addFile(APK_REQUIRED_SPLIT_TYPE_FEATURE_DATA)
                .addFile(APK_REQUIRED_SPLIT_TYPE_FEATURE_FOO)
                .run();
        // it's okay to remove non-required split type for the split
        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("feature_foo.foo").run();
        // but, not to remove a required one for the split
        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("feature_foo.data")
                .runExpectingFailure("INSTALL_FAILED_MISSING_SPLIT");
    }

    /**
     * Verify that installing a new version of app wipes code cache.
     */
    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testClearCodeCache_full() throws Exception {
        testClearCodeCache(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testClearCodeCache_instant() throws Exception {
        testClearCodeCache(true);
    }
    private void testClearCodeCache(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).run();
        runDeviceTests(PKG, CLASS, "testCodeCacheWrite");
        new InstallMultiple(instant).addArg("-r").addFile(APK_DIFF_VERSION).run();
        runDeviceTests(PKG, CLASS, "testCodeCacheRead");
    }

    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testComponentWithSplitName_instant() throws Exception {
        new InstallMultiple(true).addFile(APK_INSTANT).run();
        runDeviceTests(PKG, CLASS, "testComponentWithSplitName_singleBase");
        new InstallMultiple(true).inheritFrom(PKG).addFile(APK_FEATURE_WARM).run();
        runDeviceTests(PKG, CLASS, "testComponentWithSplitName_featureWarmInstalled");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testTheme_installBase_full() throws Exception {
        testTheme_installBase(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testTheme_installBase_instant() throws Exception {
        testTheme_installBase(true);
    }
    private void testTheme_installBase(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).run();
        runDeviceTests(PKG, CLASS, "launchBaseActivity_withThemeBase_baseApplied");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testTheme_installBaseV23_full() throws Exception {
        testTheme_installBaseV23(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testTheme_installBaseV23_instant() throws Exception {
        testTheme_installBaseV23(true);
    }
    private void testTheme_installBaseV23(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v23).run();
        runDeviceTests(PKG, CLASS, "launchBaseActivity_withThemeBaseLt_baseLtApplied");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testTheme_installFeatureWarm_full() throws Exception {
        testTheme_installFeatureWarm(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testTheme_installFeatureWarm_instant() throws Exception {
        testTheme_installFeatureWarm(true);
    }
    private void testTheme_installFeatureWarm(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_FEATURE_WARM).run();
        runDeviceTests(PKG, CLASS, "launchBaseActivity_withThemeWarm_warmApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeBase_baseApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeWarm_warmApplied");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testTheme_installFeatureWarmV23_full() throws Exception {
        testTheme_installFeatureWarmV23(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testTheme_installFeatureWarmV23_instant() throws Exception {
        testTheme_installFeatureWarmV23(true);
    }
    private void testTheme_installFeatureWarmV23(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v23).addFile(APK_FEATURE_WARM)
                .addFile(APK_FEATURE_WARM_v23).run();
        runDeviceTests(PKG, CLASS, "launchBaseActivity_withThemeWarmLt_warmLtApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeBaseLt_baseLtApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeWarmLt_warmLtApplied");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testTheme_installFeatureWarmV23_removeV23_full() throws Exception {
        testTheme_installFeatureWarmV23_removeV23(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testTheme_installFeatureWarmV23_removeV23_instant() throws Exception {
        testTheme_installFeatureWarmV23_removeV23(true);
    }
    private void testTheme_installFeatureWarmV23_removeV23(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v23).addFile(APK_FEATURE_WARM)
                .addFile(APK_FEATURE_WARM_v23).run();
        new InstallMultiple(instant).inheritFrom(PKG).removeSplit("config.v23")
                .removeSplit("feature_warm.config.v23").run();
        runDeviceTests(PKG, CLASS, "launchBaseActivity_withThemeWarm_warmApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeBase_baseApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeWarm_warmApplied");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testTheme_installFeatureWarmAndRose_full() throws Exception {
        testTheme_installFeatureWarmAndRose(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testTheme_installFeatureWarmAndRose_instant() throws Exception {
        testTheme_installFeatureWarmAndRose(true);
    }
    private void testTheme_installFeatureWarmAndRose(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_FEATURE_WARM)
                .addFile(APK_FEATURE_ROSE).run();
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeWarm_warmApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeRose_roseApplied");
        runDeviceTests(PKG, CLASS, "launchRoseActivity_withThemeWarm_warmApplied");
        runDeviceTests(PKG, CLASS, "launchRoseActivity_withThemeRose_roseApplied");
    }

    @Test
    @AppModeFull(reason = "'full' portion of the hostside test")
    public void testTheme_installFeatureWarmAndRoseV23_full() throws Exception {
        testTheme_installFeatureWarmAndRoseV23(false);
    }
    @Test
    @AppModeInstant(reason = "'instant' portion of the hostside test")
    public void testTheme_installFeatureWarmAndRoseV23_instant() throws Exception {
        testTheme_installFeatureWarmAndRoseV23(true);
    }
    private void testTheme_installFeatureWarmAndRoseV23(boolean instant) throws Exception {
        new InstallMultiple(instant).addFile(APK).addFile(APK_v23)
                .addFile(APK_FEATURE_WARM).addFile(APK_FEATURE_WARM_v23)
                .addFile(APK_FEATURE_ROSE).addFile(APK_FEATURE_ROSE_v23).run();
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeWarmLt_warmLtApplied");
        runDeviceTests(PKG, CLASS, "launchWarmActivity_withThemeRoseLt_roseLtApplied");
        runDeviceTests(PKG, CLASS, "launchRoseActivity_withThemeWarmLt_warmLtApplied");
        runDeviceTests(PKG, CLASS, "launchRoseActivity_withThemeRoseLt_roseLtApplied");
    }
}
