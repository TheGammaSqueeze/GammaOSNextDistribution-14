/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.content.pm.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.Flags;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import dalvik.system.VMRuntime;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@AppModeFull(reason = "Instant applications cannot see any other application")
@RunWith(AndroidJUnit4.class)
public class PackageManagerMultiArchAppTest {

    private static final String TEST_APP_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_APP_APK_BASE = "CtsMultiArchApp";
    private static final String BITNESS_32 = "32";
    private static final String BITNESS_64 = "64";
    private static final String BITNESS_BOTH = "Both";
    private static final String BASE_ARCH_ARM = "arm";
    private static final String BASE_ARCH_X86 = "x86";
    private static final String BASE_ARCH_MIPS = "mips";

    // List of supported abi
    private static final String ABI_ARM_32 = "armeabi";
    private static final String ABI_ARM_V7A = "armeabi-v7a";
    private static final String ABI_ARM_64_V8A = "arm64-v8a";
    private static final String ABI_X86 = "x86";
    private static final String ABI_X86_64 = "x86_64";
    private static final String ABI_MIPS = "mips";
    private static final String ABI_MIPS64 = "mips64";
    private static final String ABI_RISCV64 = "riscv64";

    private static final String TEST_APP_PKG = "com.android.cts.multiarch.app";
    private static final String EXPECTED_FAILED_ERROR_MESSAGE =
            "don't support all the natively supported ABIs of the device";

    private static final Set<String> BITS_32_SET = new HashSet<>(Arrays.asList(
            "armeabi", "armeabi-v7a", "x86"));
    private static final Map<String, String> ABI_TO_BASE_ARCH = new LinkedHashMap<String, String>();

    private static String[] sDeviceSupported32Bits = null;
    private static String[] sDeviceSupported64Bits = null;
    private static String[] sSupportedEmulatedAbis = null;
    private static String sDeviceDefaultAbi = null;
    private static String sDeviceDefaultBitness = null;
    private static String sDeviceDefaultBaseArch = null;
    private static String sTestBaseArch = null;

    static {
        ABI_TO_BASE_ARCH.put(ABI_ARM_32, BASE_ARCH_ARM);
        ABI_TO_BASE_ARCH.put(ABI_ARM_V7A, BASE_ARCH_ARM);
        ABI_TO_BASE_ARCH.put(ABI_ARM_64_V8A, BASE_ARCH_ARM);
        ABI_TO_BASE_ARCH.put(ABI_X86, BASE_ARCH_X86);
        ABI_TO_BASE_ARCH.put(ABI_X86_64, BASE_ARCH_X86);
        ABI_TO_BASE_ARCH.put(ABI_MIPS, BASE_ARCH_MIPS);
        ABI_TO_BASE_ARCH.put(ABI_MIPS64, BASE_ARCH_MIPS);
        ABI_TO_BASE_ARCH.put(ABI_RISCV64, ABI_RISCV64);
    }

    private PackageManager mPackageManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static String[] getSupportedEmulatedAbis() throws Exception {
        if (sSupportedEmulatedAbis != null) {
            return sSupportedEmulatedAbis;
        }

        Set<String> abiSet = new ArraySet<>();
        getSupportedEmulatedAbis(getDeviceSupported64Abis(), abiSet);
        getSupportedEmulatedAbis(getDeviceSupported32Abis(), abiSet);
        sSupportedEmulatedAbis = abiSet.toArray(new String[0]);
        return sSupportedEmulatedAbis;
    }

    private static void getSupportedEmulatedAbis(String[] supportedAbis, Set<String> abiSet)
            throws Exception {
        for (int i = 0; i < supportedAbis.length; i++) {
            final String currentAbi = supportedAbis[i];
            // In presence of a native bridge this means the Abi is emulated.
            final String currentIsa = VMRuntime.getInstructionSet(currentAbi);
            if (!TextUtils.isEmpty(SystemProperties.get("ro.dalvik.vm.isa." + currentIsa))) {
                abiSet.add(currentAbi);
            }
        }
    }

    /** Returns the base architecture matching the abi. Null if the abi is not supported. */
    @Nullable
    private static String getBaseArchForAbi(@NonNull String abi) {
        Objects.requireNonNull(abi);
        if (abi.isEmpty()) {
            throw new IllegalArgumentException("Abi cannot be empty");
        }
        return ABI_TO_BASE_ARCH.get(abi);
    }

    @NonNull
    private static String getTestAppPath(@NonNull String abi, @NonNull String baseArch) {
        Objects.requireNonNull(abi);

        String bitness = BITNESS_64;
        if (getBitness(abi).equals(BITNESS_32)) {
            bitness = BITNESS_32;
        }

        return getTestApkPath(bitness, /* isTargetSDK33= */ false, baseArch);
    }

    @NonNull
    private static String getTestApkPath(@NonNull String abiBit) {
        return getTestApkPath(abiBit, /* isTargetSDK33= */ false, sTestBaseArch);
    }

    @NonNull
    private static String getTestApkPath(@NonNull String abiBit, boolean isTargetSDK33) {
        return getTestApkPath(abiBit, isTargetSDK33, sTestBaseArch);
    }

    @NonNull
    private static String getTestApkPath(@NonNull String abiBit, boolean isTargetSDK33,
            @NonNull String baseArch) {
        Objects.requireNonNull(abiBit);
        Objects.requireNonNull(baseArch);
        return TEST_APP_PATH + TEST_APP_APK_BASE + abiBit + (isTargetSDK33 ? "_targetSdk33_" : "_")
                + baseArch + ".apk";
    }

    @NonNull
    private static String getBitness(String abi) {
        return BITS_32_SET.contains(abi) ? BITNESS_32 : BITNESS_64;
    }

    @NonNull
    private static String[] getDeviceSupported32Abis() throws Exception {
        if (sDeviceSupported32Bits != null) {
            return sDeviceSupported32Bits;
        }

        sDeviceSupported32Bits = Build.SUPPORTED_32_BIT_ABIS;
        return sDeviceSupported32Bits;
    }

    @NonNull
    private static String[] getDeviceSupported64Abis() throws Exception {
        if (sDeviceSupported64Bits != null) {
            return sDeviceSupported64Bits;
        }

        sDeviceSupported64Bits = Build.SUPPORTED_64_BIT_ABIS;
        return sDeviceSupported64Bits;
    }

    @NonNull
    private static String getDeviceDefaultAbi() throws Exception {
        if (sDeviceDefaultAbi != null) {
            return sDeviceDefaultAbi;
        }
        sDeviceDefaultAbi = SystemProperties.get("ro.product.cpu.abi");
        return sDeviceDefaultAbi;
    }

    @NonNull
    private static String getDeviceDefaultBitness() throws Exception {
        if (sDeviceDefaultBitness != null) {
            return sDeviceDefaultBitness;
        }
        sDeviceDefaultBitness = getBitness(getDeviceDefaultAbi());
        return sDeviceDefaultBitness;
    }

    @NonNull
    private static String getDeviceDefaultBaseArch() throws Exception {
        if (sDeviceDefaultBaseArch != null) {
            return sDeviceDefaultBaseArch;
        }
        sDeviceDefaultBaseArch = getBaseArchForAbi(getDeviceDefaultAbi());
        assumeTrue("The default abi on the device is not supported.",
                sDeviceDefaultBaseArch != null);
        return sDeviceDefaultBaseArch;
    }

    private static boolean isBaseArchSupportedInAbis(@NonNull String baseArch,
            @NonNull String[] abis) {
        Objects.requireNonNull(baseArch);
        Objects.requireNonNull(abis);

        for (int i = 0; i < abis.length; i++) {
            if (baseArch.equals(getBaseArchForAbi(abis[i]))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeviceSupportsEmulatedAbi() throws Exception {
        return getSupportedEmulatedAbis().length > 0;
    }

    /*
     * Return true if the device supports both 32 bit and 64 bit ABIs. Otherwise, false.
     */
    private static boolean isDeviceSupportedBothBitness() throws Exception {
        if (getDeviceDefaultBitness().equals(BITNESS_32)) {
            return isBaseArchSupportedInAbis(getDeviceDefaultBaseArch(),
                    getDeviceSupported64Abis());
        } else {
            return isBaseArchSupportedInAbis(getDeviceDefaultBaseArch(),
                    getDeviceSupported32Abis());
        }
    }

    private static boolean isSupportedBaseArch(@Nullable String baseArch) {
        return BASE_ARCH_ARM.equals(baseArch) || BASE_ARCH_X86.equals(baseArch);
    }

    private boolean isInstalled() {
        try {
            PackageInfo pi = mPackageManager.getPackageInfo(TEST_APP_PKG, 0);
            return pi != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @NonNull
    private String installPackage(@NonNull String apkPath) {
        Objects.requireNonNull(apkPath);
        return SystemUtil.runShellCommand("pm install " + apkPath);
    }

    @NonNull
    private void uninstallPackage(@NonNull String packageName) {
        Objects.requireNonNull(packageName);
        SystemUtil.runShellCommand("pm uninstall " + packageName);
    }

    /** Uninstall app before tests. */
    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = context.getPackageManager();
        sTestBaseArch = getDeviceDefaultBaseArch();
        uninstallPackage(TEST_APP_PKG);
    }

    /** Uninstall app after tests. */
    @After
    public void cleanUp() throws Exception {
        uninstallPackage(TEST_APP_PKG);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp32_notMatchAllNativelyAbis_fail() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        String result = installPackage(getTestApkPath(BITNESS_32));

        assertThat(result).contains(EXPECTED_FAILED_ERROR_MESSAGE);
        assertThat(isInstalled()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp64_notMatchAllNativelyAbis_fail() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        String result = installPackage(getTestApkPath(BITNESS_64));

        assertThat(result).contains(EXPECTED_FAILED_ERROR_MESSAGE);
        assertThat(isInstalled()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp32_targetSdk33_success() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        installPackage(getTestApkPath(BITNESS_32, /* isTargetSDK33= */ true));

        assertThat(isInstalled()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp64_targetSdk33_success() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        installPackage(getTestApkPath(BITNESS_64, /* isTargetSDK33= */ true));

        assertThat(isInstalled()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp_emulatedAbiNoNativelyAbi_fail() throws Exception {
        assumeTrue(isDeviceSupportsEmulatedAbi());
        final String firstEmulatedAbi = getSupportedEmulatedAbis()[0];
        final String baseArch = getBaseArchForAbi(firstEmulatedAbi);
        assumeTrue(isSupportedBaseArch(baseArch));

        String result = installPackage(getTestAppPath(firstEmulatedAbi, baseArch));

        assertThat(result).contains(EXPECTED_FAILED_ERROR_MESSAGE);
        assertThat(isInstalled()).isFalse();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp_emulatedAbiNoNativelyAbi_success() throws Exception {
        assumeTrue(isDeviceSupportsEmulatedAbi());
        final String firstEmulatedAbi = getSupportedEmulatedAbis()[0];
        final String baseArch = getBaseArchForAbi(firstEmulatedAbi);
        assumeTrue(isSupportedBaseArch(baseArch));

        installPackage(getTestAppPath(firstEmulatedAbi, baseArch));

        assertThat(isInstalled()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchAppBoth_targetSdk33_success() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        installPackage(getTestApkPath(BITNESS_BOTH, /* isTargetSDK33= */ true));

        assertThat(isInstalled()).isTrue();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp32_success() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        installPackage(getTestApkPath(BITNESS_32));

        assertThat(isInstalled()).isTrue();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_FORCE_MULTI_ARCH_NATIVE_LIBS_MATCH)
    public void testInstallMultiArchApp64_success() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        installPackage(getTestApkPath(BITNESS_64));

        assertThat(isInstalled()).isTrue();
    }

    @Test
    public void testInstallMultiArchAppBoth_success() throws Exception {
        assumeTrue(isDeviceSupportedBothBitness());

        installPackage(getTestApkPath(BITNESS_BOTH));

        assertThat(isInstalled()).isTrue();
    }
}
