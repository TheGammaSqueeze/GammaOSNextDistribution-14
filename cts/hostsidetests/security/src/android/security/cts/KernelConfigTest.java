/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.CpuFeatures;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Host-side kernel config tests.
 *
 * These tests analyze /proc/config.gz to verify that certain kernel config options are set.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class KernelConfigTest extends BaseHostJUnit4Test {

    private static final Map<ITestDevice, HashSet<String>> cachedConfigGzSet = new HashMap<>(1);

    private HashSet<String> configSet;

    private ITestDevice mDevice;
    private IBuildInfo mBuild;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        mBuild = getBuild();
        configSet = getDeviceConfig(mDevice, cachedConfigGzSet);
        // Assumes every test in this file asserts a requirement of CDD section 9.
        assumeSecurityModelCompat();
    }

    /*
     * IMPLEMENTATION DETAILS: Cache the configurations from /proc/config.gz on per-device basis
     * in case CTS is being run against multiple devices at the same time. This speeds up testing
     * by avoiding pulling/parsing the config file for each individual test
     */
    private static HashSet<String> getDeviceConfig(ITestDevice device,
            Map<ITestDevice, HashSet<String>> cache) throws Exception {
        if (!device.doesFileExist("/proc/config.gz")){
            throw new Exception();
        }
        HashSet<String> set;
        synchronized (cache) {
            set = cache.get(device);
        }
        if (set != null) {
            return set;
        }
        File file = File.createTempFile("config.gz", ".tmp");
        file.deleteOnExit();
        device.pullFile("/proc/config.gz", file);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
        set = new HashSet<String>(reader.lines().collect(Collectors.toList()));

        synchronized (cache) {
            cache.put(device, set);
        }
        return set;
    }

    /**
     * Test that the kernel has Stack Protector Strong enabled.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigStackProtectorStrong() throws Exception {
        assertTrue("Linux kernel must have Stack Protector enabled: " +
                "CONFIG_STACKPROTECTOR_STRONG=y or CONFIG_CC_STACKPROTECTOR_STRONG=y",
                configSet.contains("CONFIG_STACKPROTECTOR_STRONG=y") ||
                configSet.contains("CONFIG_CC_STACKPROTECTOR_STRONG=y"));
    }

    /**
     * Test that the kernel's executable code is read-only, read-only data is non-executable and
     * non-writable, and writable data is non-executable.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigROData() throws Exception {
        if (configSet.contains("CONFIG_UH_RKP=y"))
            return;

        assertTrue("Linux kernel must have RO data enabled: " +
                "CONFIG_DEBUG_RODATA=y or CONFIG_STRICT_KERNEL_RWX=y",
                configSet.contains("CONFIG_DEBUG_RODATA=y") ||
                configSet.contains("CONFIG_STRICT_KERNEL_RWX=y"));

        if (configSet.contains("CONFIG_MODULES=y")) {
            assertTrue("Linux kernel modules must also have RO data enabled: " +
                    "CONFIG_DEBUG_SET_MODULE_RONX=y or CONFIG_STRICT_MODULE_RWX=y",
                    configSet.contains("CONFIG_DEBUG_SET_MODULE_RONX=y") ||
                    configSet.contains("CONFIG_STRICT_MODULE_RWX=y"));
        }
    }

    /**
     * Test that the kernel implements static and dynamic object size bounds checking of copies
     * between user-space and kernel-space.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigHardenedUsercopy() throws Exception {
        if (PropertyUtil.getFirstApiLevel(mDevice) < 28) {
            return;
        }

        assertTrue("Linux kernel must have Hardened Usercopy enabled: CONFIG_HARDENED_USERCOPY=y",
                configSet.contains("CONFIG_HARDENED_USERCOPY=y"));
    }

    /**
     * Test that the kernel has PAN emulation enabled from architectures that support it.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigPAN() throws Exception {
        if (PropertyUtil.getFirstApiLevel(mDevice) < 28) {
            return;
        }

        if (CpuFeatures.isArm64(mDevice)) {
            assertTrue("Linux kernel must have PAN emulation enabled: " +
                    "CONFIG_ARM64_SW_TTBR0_PAN=y or CONFIG_ARM64_PAN=y",
                    (configSet.contains("CONFIG_ARM64_SW_TTBR0_PAN=y") ||
                    configSet.contains("CONFIG_ARM64_PAN=y")));
        } else if (CpuFeatures.isArm32(mDevice)) {
            assertTrue("Linux kernel must have PAN emulation enabled: " +
                    "CONFIG_CPU_SW_DOMAIN_PAN=y or CONFIG_CPU_TTBR0_PAN=y",
                    (configSet.contains("CONFIG_CPU_SW_DOMAIN_PAN=y") ||
                    configSet.contains("CONFIG_CPU_TTBR0_PAN=y")));
        }
    }

    private String getHardware() throws Exception {
        String hardware = "DEFAULT";
        String[] pathList = new String[]{"/proc/cpuinfo", "/sys/devices/soc0/soc_id"};
        String mitigationInfoMeltdown =
                mDevice.pullFileContents("/sys/devices/system/cpu/vulnerabilities/meltdown");
        String mitigationInfoSpectreV2 =
                mDevice.pullFileContents("/sys/devices/system/cpu/vulnerabilities/spectre_v2");

        if (mitigationInfoMeltdown != null && mitigationInfoSpectreV2 != null &&
            !mitigationInfoMeltdown.contains("Vulnerable") &&
            (!mitigationInfoSpectreV2.contains("Vulnerable") ||
              mitigationInfoSpectreV2.equals("Vulnerable: Unprivileged eBPF enabled\n")))
                return "VULN_SAFE";

        for (String nodeInfo : pathList) {
            if (!mDevice.doesFileExist(nodeInfo))
                continue;

            String nodeContent = mDevice.pullFileContents(nodeInfo);
            if (nodeContent == null)
                continue;

            for (String line : nodeContent.split("\n")) {
                /* Qualcomm SoCs */
                if (line.startsWith("Hardware")) {
                    String[] hardwareLine = line.split(" ");
                    hardware = hardwareLine[hardwareLine.length - 1];
                    break;
                }
                /* Samsung Exynos SoCs */
                else if (line.startsWith("EXYNOS")) {
                    hardware = line;
                    break;
                }
            }
        }
        /* TODO lookup other hardware as we get exemption requests. */
        return hardwareMitigations.containsKey(hardware) ? hardware : "DEFAULT";
    }

    private boolean doesFileExist(String filePath) throws Exception {
        String lsGrep = mDevice.executeShellCommand(String.format("ls \"%s\"", filePath));
        return lsGrep.trim().equals(filePath);
    }

    private static Map<String, String[]> createHardwareMitigations() {
        Map<String, String[]> result = new HashMap<>();
        result.put("VULN_SAFE", null);
        result.put("EXYNOS990", null);
        result.put("EXYNOS980", null);
        result.put("EXYNOS850", null);
        result.put("EXYNOS3830", null);
        result.put("EXYNOS9630", null);
        result.put("EXYNOS9830", null);
        result.put("EXYNOS7870", null);
        result.put("EXYNOS7880", null);
        result.put("EXYNOS7570", null);
        result.put("EXYNOS7872", null);
        result.put("EXYNOS7885", null);
        result.put("EXYNOS9610", null);
        result.put("Kirin980", null);
        result.put("Kirin970", null);
        result.put("Kirin810", null);
        result.put("Kirin710", null);
        result.put("MT6889Z/CZA", null);
        result.put("MT6889Z/CIZA", null);
        result.put("mt6873", null);
        result.put("MT6853V/TZA", null);
        result.put("MT6853V/TNZA", null);
        result.put("MT6833V/ZA", null);
        result.put("MT6833V/NZA", null);
        result.put("MT6833V/TZA", null);
        result.put("MT6833V/TNZA", null);
        result.put("MT6833V/MZA", null);
        result.put("MT6833V/MNZA", null);
        result.put("MT6877V/ZA", null);
        result.put("MT6877V/NZA", null);
        result.put("MT6877V/TZA", null);
        result.put("MT6877V/TNZA", null);
        result.put("MT6768V/WA", null);
        result.put("MT6768V/CA", null);
        result.put("MT6768V/WB", null);
        result.put("MT6768V/CB", null);
        result.put("MT6767V/WA", null);
        result.put("MT6767V/CA", null);
        result.put("MT6767V/WB", null);
        result.put("MT6767V/CB", null);
        result.put("MT6769V/WA", null);
        result.put("MT6769V/CA", null);
        result.put("MT6769V/WB", null);
        result.put("MT6769V/CB", null);
        result.put("MT6769V/WT", null);
        result.put("MT6769V/CT", null);
        result.put("MT6769V/WU", null);
        result.put("MT6769V/CU", null);
        result.put("MT6769V/WZ", null);
        result.put("MT6769V/CZ", null);
        result.put("MT6769V/WY", null);
        result.put("MT6769V/CY", null);
        result.put("SDMMAGPIE", null);
        result.put("SM6150", null);
        result.put("SM7150", null);
        result.put("SM7250", null);
        result.put("LITO", null);
        result.put("LAGOON", null);
        result.put("SM8150", null);
        result.put("SM8150P", null);
        result.put("SM8250", null);
        result.put("KONA", null);
        result.put("SDM429", null);
        result.put("SDM439", null);
        result.put("QM215", null);
        result.put("ATOLL", null);
        result.put("ATOLL-AB", null);
        result.put("SDM660", null);
        result.put("BENGAL", null);
        result.put("KHAJE", null);
        result.put("BENGAL-IOT", null);
        result.put("BENGALP-IOT", null);
        result.put("SCUBAIIOT", null);
        result.put("SCUBAPIIOT", null);
        result.put("DEFAULT", new String[]{"CONFIG_UNMAP_KERNEL_AT_EL0=y"});
        return result;
    }

    private Map<String, String[]> hardwareMitigations = createHardwareMitigations();

    private String[] lookupMitigations() throws Exception {
        return hardwareMitigations.get(getHardware());
    }

    /**
     * Test that the kernel has Spectre/Meltdown mitigations for architectures and kernel versions
     * that support it. Exempt platforms which are known to not be vulnerable.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigHardwareMitigations() throws Exception {
        String mitigations[];

        if (PropertyUtil.getFirstApiLevel(mDevice) < 28) {
            return;
        }

        if (CpuFeatures.isArm64(mDevice) && !CpuFeatures.kernelVersionLessThan(mDevice, 4, 4)) {
            mitigations = lookupMitigations();
            if (mitigations != null) {
                for (String mitigation : mitigations) {
                    assertTrue("Linux kernel must have " + mitigation + " enabled.",
                            configSet.contains(mitigation));
                }
            }
        } else if (CpuFeatures.isX86(mDevice)) {
            assertTrue("Linux kernel must have KPTI enabled: CONFIG_PAGE_TABLE_ISOLATION=y",
                    configSet.contains("CONFIG_PAGE_TABLE_ISOLATION=y"));
        }
    }

    /**
     * Test that the kernel enables static usermodehelper and sets
     * the path to an allowlisted path.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigDisableUsermodehelper() throws Exception {
        if (PropertyUtil.getFirstApiLevel(mDevice) < 30) {
            return;
        }

        final String ENABLE_CONFIG = "CONFIG_STATIC_USERMODEHELPER=y";
        final String PATH_CONFIG = "CONFIG_STATIC_USERMODEHELPER_PATH=";

        final Set<String> ALLOWED_PATH_PREFIXES = new HashSet<String>();
        ALLOWED_PATH_PREFIXES.add("/vendor/");
        ALLOWED_PATH_PREFIXES.add("/system/");
        ALLOWED_PATH_PREFIXES.add("/system_ext/");

        assertTrue("Linux kernel must enable static usermodehelper: " + ENABLE_CONFIG,
            configSet.contains(ENABLE_CONFIG));

        String configPath = null;

        for (String option : configSet) {
            if (option.startsWith(PATH_CONFIG)) {
                configPath = option;
            }
        }

        int index = configPath.indexOf('=');
        String path = configPath.substring(index+1).replace("\"", "");

        assertTrue("Linux kernel must specify an absolute path for static usermodehelper path",
            configPath.contains("..") == false);

        boolean pathIsWhitelisted = false;

        for (String allowedPath : ALLOWED_PATH_PREFIXES) {
            if (path.startsWith(allowedPath)) {
                pathIsWhitelisted = true;
                break;
            }
        }

        // Specifying no path, which disables usermodehelper, is also
        // valid.
        pathIsWhitelisted |= path.isEmpty();

        String whitelistedPathPrefixExample = "'" +
            String.join("', '", ALLOWED_PATH_PREFIXES) + "'";

        assertTrue("Linux kernel must specify a whitelisted static usermodehelper path, "
                   + "and it must be empty or start with one of the following "
                   + "prefixes: " + whitelistedPathPrefixExample, pathIsWhitelisted);
    }

    /**
     * Test that the kernel enables fs-verity.
     */
    @CddTest(requirement="9.10")
    @Test
    public void testConfigFsVerity() throws Exception {
        if (PropertyUtil.getFirstApiLevel(mDevice) < 30 &&
                PropertyUtil.getPropertyInt(mDevice, "ro.apk_verity.mode") != 2) {
            return;
        }
        assertTrue("Linux kernel must have fs-verity enabled: CONFIG_FS_VERITY=y",
                configSet.contains("CONFIG_FS_VERITY=y"));
    }

    private void assumeSecurityModelCompat() throws Exception {
        // This feature name check only applies to devices that first shipped with
        // SC or later.
        final int firstApiLevel = Math.min(PropertyUtil.getFirstApiLevel(mDevice),
                PropertyUtil.getVendorApiLevel(mDevice));
        if (firstApiLevel >= 31) {
            assumeTrue("Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.",
                    getDevice().hasFeature("feature:android.hardware.security.model.compatible"));
        }
    }

    /**
     * Test that the kernel is using kASLR.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigRandomizeBase() throws Exception {
        if (PropertyUtil.getFirstApiLevel(mDevice) < 33) {
            return;
        }

        if (CpuFeatures.isArm32(mDevice)) {
            return;
        }

        assertTrue("The kernel's base address must be randomized",
                configSet.contains("CONFIG_RANDOMIZE_BASE=y"));
    }

    /**
     * Test that CONFIG_VMAP_STACK is enabled on architectures that support it.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testConfigVmapStack() throws Exception {
        if (PropertyUtil.getFirstApiLevel(mDevice) < 33) {
            return;
        }

        if (!configSet.contains("CONFIG_HAVE_ARCH_VMAP_STACK=y")) {
            return;
        }

        assertTrue("CONFIG_VMAP_STACK must be enabled on architectures that support it.",
                configSet.contains("CONFIG_VMAP_STACK=y"));
    }
}
