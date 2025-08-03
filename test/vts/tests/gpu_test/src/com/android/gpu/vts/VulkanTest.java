/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.gpu.vts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.RequiresDevice;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.VsrTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/*
 * VTS test for Vulkan requirements.
 */
@RequiresDevice
@RunWith(DeviceJUnit4ClassRunner.class)
public class VulkanTest extends BaseHostJUnit4Test {
    private static final int VK_PHYSICAL_DEVICE_TYPE_CPU = 4;
    private static final int VULKAN_1_1_API_VERSION = 0x401000;
    private static final int VULKAN_1_3_API_VERSION = 0x403000;

    // Feature version corresponding to dEQP level for 2024-03-01.
    public static final int DEQP_LEVEL_FOR_V = 0x7E80301;

    // Feature version corresponding to dEQP level for 2023-03-01.
    public static final int DEQP_LEVEL_FOR_U = 0x7E70301;

    // Feature version corresponding to dEQP level for 2022-03-01.
    public static final int DEQP_LEVEL_FOR_T = 0x7E60301;

    // Feature version corresponding to dEQP level for 2021-03-01.
    public static final int DEQP_LEVEL_FOR_S = 0x7E50301;

    // Feature version corresponding to dEQP level for 2020-03-01.
    public static final int DEQP_LEVEL_FOR_R = 0x7E40301;

    private static final String VK_EXT_DEVICE_MEMORY_REPORT_EXTENSION_NAME =
            "VK_EXT_device_memory_report";
    private static final int VK_EXT_DEVICE_MEMORY_REPORT_SPEC_VERSION = 1;

    private static final String VK_EXT_GLOBAL_PRIORITY_EXTENSION_NAME = "VK_EXT_global_priority";
    private static final int VK_EXT_GLOBAL_PRIORITY_SPEC_VERSION = 1;
    private static final String VK_KHR_GLOBAL_PRIORITY_EXTENSION_NAME = "VK_KHR_global_priority";
    private static final int VK_KHR_GLOBAL_PRIORITY_SPEC_VERSION = 1;

    // the string to parse to confirm that Skia is using vulkan
    private static final String SKIA_PIPELINE = "Pipeline=Skia";
    private static final String SKIA_VULKAN_PIPELINE = "Pipeline=Skia (Vulkan)";

    private JSONObject[] mVulkanDevices;
    private JSONObject mVulkanProfiles;

    /**
     * Test specific setup
     */
    @Before
    public void setUp() throws Exception {
        final String output = getDevice().executeShellCommand("cmd gpu vkjson");
        final JSONArray vkjson;
        // vkjson output in Android N is JSONArray.
        if (ApiLevelUtil.isBefore(getDevice(), Build.OC)) {
            vkjson = (new JSONArray(output));
        } else {
            vkjson = (new JSONObject(output)).getJSONArray("devices");
        }
        mVulkanDevices = new JSONObject[vkjson.length()];
        for (int i = 0; i < vkjson.length(); i++) {
            mVulkanDevices[i] = vkjson.getJSONObject(i);
        }

        final String profiles = getDevice().executeShellCommand("cmd gpu vkprofiles");
        mVulkanProfiles = new JSONObject(profiles);
    }

    /**
     * 64-bit SoCs released with Q must support Vulkan 1.1.
     */
    @VsrTest(requirements = {"VSR-3.2.1-001.001"})
    @Test
    public void checkVulkan1_1Requirements() throws Exception {
        // Only test for new 64-bit SoCs that are Q and above.
        assumeTrue("Test does not apply for SoCs released before Q",
                PropertyUtil.getVsrApiLevel(getDevice()) >= Build.QT);
        assumeTrue("Test does not apply for 32-bit SoCs",
                getDevice().getProperty("ro.product.cpu.abi").contains("64"));

        assertTrue(mVulkanDevices.length > 0);

        int bestApiVersion = 0;
        for (JSONObject device : mVulkanDevices) {
            final int apiVersion = device.getJSONObject("properties").getInt("apiVersion");
            if (bestApiVersion < apiVersion) {
                bestApiVersion = apiVersion;
            }
        }
        assertTrue("Supported Vulkan version must be at least 1.1",
                bestApiVersion >= VULKAN_1_1_API_VERSION);
    }

    /**
     * 64-bit SoCs released with U must support Vulkan 1.3.
     * All SoCs released with V must support Vulkan 1.3.
     */
    @VsrTest(requirements = {"VSR-3.2.1-001.003", "VSR-3.2.1-007"})
    @Test
    public void checkVulkan1_3Requirements() throws Exception {
        assumeTrue("Test does not apply for SoCs released before U",
                PropertyUtil.getVsrApiLevel(getDevice()) >= Build.UDC);
        assumeTrue("Test does not apply for automotive devices released before V",
                !FeatureUtil.isAutomotive(getDevice())
                        || PropertyUtil.getVsrApiLevel(getDevice()) > Build.UDC);

        // Don't test if an SoC released during U is 32 bit
        // If an SoC is released with V then both 32 and 64 bit are to be tested
        if (PropertyUtil.getVsrApiLevel(getDevice()) == Build.UDC) {
            assumeTrue("Test does not apply for 32-bit SoCs released with Android U",
                    getDevice().getProperty("ro.product.cpu.abi").contains("64"));
        }

        assertTrue(mVulkanDevices.length > 0);

        int bestApiVersion = 0;
        for (JSONObject device : mVulkanDevices) {
            final int apiVersion = device.getJSONObject("properties").getInt("apiVersion");
            if (bestApiVersion < apiVersion) {
                bestApiVersion = apiVersion;
            }
        }
        assertTrue("Supported Vulkan version must be at least 1.3",
                bestApiVersion >= VULKAN_1_3_API_VERSION);
    }

    /**
     * SoCs with only CPU Vulkan must properly set "ro.cpuvulkan.version" property.
     */
    @VsrTest(requirements = {"VSR-3.2.1-002"})
    @Test
    public void checkCpuVulkanRequirements() throws Exception {
        // Only test for new SoCs that are Q and above.
        assumeTrue("Test does not apply for SoCs released before Q",
                PropertyUtil.getVsrApiLevel(getDevice()) >= Build.QT);

        if (mVulkanDevices.length == 0) {
            return;
        }

        boolean hasOnlyCpuDevice = true;
        int bestApiVersion = 0;
        for (JSONObject device : mVulkanDevices) {
            if (device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_CPU) {
                hasOnlyCpuDevice = false;
            } else {
                final int apiVersion = device.getJSONObject("properties").getInt("apiVersion");
                if (bestApiVersion < apiVersion) {
                    bestApiVersion = apiVersion;
                }
            }
        }

        if (!hasOnlyCpuDevice) {
            return;
        }

        final int advertisedApiVersion =
                Integer.parseInt(getDevice().getProperty("ro.cpuvulkan.version"));
        assertEquals("Advertised CPU Vulkan api version " + advertisedApiVersion
                        + " doesn't match the best physical device api version " + bestApiVersion,
                bestApiVersion, advertisedApiVersion);
    }

    /**
     * Verify that FEATURE_VULKAN_DEQP_LEVEL (feature:android.software.vulkan.deqp.level) has a
     * sufficiently high version in relation to the vendor and first product API level.
     */
    @VsrTest(requirements = {"VSR-3.2.2-001", "VSR-3.2.2-002", "VSR-3.2.2-003", "VSR-3.2.2-004",
                     "VSR-3.2.2-005", "VSR-3.2.2-006"})
    @Test
    public void
    checkVulkanDeqpLevelIsHighEnough() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        assumeTrue("Test does not apply for API level lower than R", apiLevel >= Build.RVC);
        assumeTrue("Test does not apply for SoCs without Vulkan", mVulkanDevices.length > 0);

        // Map from API level to required dEQP level.
        final int requiredVulkanDeqpLevel;
        switch (apiLevel) {
            case Build.RVC:
                requiredVulkanDeqpLevel = DEQP_LEVEL_FOR_R;
                break;
            case Build.SC:
            case Build.SC_V2:
                requiredVulkanDeqpLevel = DEQP_LEVEL_FOR_S;
                break;
            case Build.TM:
                requiredVulkanDeqpLevel = DEQP_LEVEL_FOR_T;
                break;
            case Build.UDC:
                requiredVulkanDeqpLevel = DEQP_LEVEL_FOR_U;
                break;
            case Build.VIC:
                requiredVulkanDeqpLevel = DEQP_LEVEL_FOR_V;
                break;
            default:
                fail("Test should only run for API levels: R, S, Sv2, TM, UDC, VIC...");
                return;
        }

        // Check that the feature flag is present and its value is at least the required dEQP level.
        final String output = getDevice().executeShellCommand(String.format(
                "pm has-feature android.software.vulkan.deqp.level %d", requiredVulkanDeqpLevel));

        if (!output.trim().equals("true")) {
            final String message = String.format(
                    "Advertised Vulkan dEQP level feature is too low or does not exist.\n"
                            + "Expected:\nfeature:android.software.vulkan.deqp.level>=%d\n"
                            + "Actual:\n%s",
                    requiredVulkanDeqpLevel,
                    getDevice().executeShellCommand("pm list features | grep deqp"));

            fail(message);
        }
    }

    /**
     * For SoCs launching with Android 12 or higher, if the SoC supports Vulkan 1.1 or higher,
     * VK_EXT_device_memory_report extension must be supported.
     */
    @VsrTest(requirements = {"VSR-3.2.1-006"})
    @Test
    public void checkVulkanDeviceMemoryReportSupport() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        assumeTrue("Test does not apply for API level lower than S", apiLevel >= Build.SC);

        assumeTrue(
                "Test does not apply for SoCs without Vulkan support", mVulkanDevices.length > 0);

        for (int i = 0; i < mVulkanDevices.length; ++i) {
            final JSONObject device = mVulkanDevices[i];
            // Skip extension check if Vulkan device's API version is < 1.1.
            if (device.getJSONObject("properties").getInt("apiVersion") < VULKAN_1_1_API_VERSION) {
                continue;
            }

            final boolean isSupported =
                    hasExtension(device, VK_EXT_DEVICE_MEMORY_REPORT_EXTENSION_NAME,
                            VK_EXT_DEVICE_MEMORY_REPORT_SPEC_VERSION);

            if (!isSupported) {
                final String message =
                        String.format("Vulkan devices with API version >= 1.1 must support %s "
                                        + "but the device at index %d does not. "
                                        + "Check 'adb shell cmd gpu vkjson'.",
                                VK_EXT_DEVICE_MEMORY_REPORT_EXTENSION_NAME, i);

                fail(message);
            }
        }
    }

    /**
     * All SoCs released with V must support Skia Vulkan with HWUI
     */
    @VsrTest(requirements = {"VSR-3.2.1-009"})
    @Test
    public void checkSkiaVulkanSupport() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        assumeTrue("Test does not apply for SoCs launched before V", apiLevel >= Build.VIC);

        final String gfxinfo = getDevice().executeShellCommand("dumpsys gfxinfo");
        assertNotNull(gfxinfo);
        assertTrue(gfxinfo.length() > 0);

        int skiaDataIndex = gfxinfo.indexOf(SKIA_PIPELINE);
        assertTrue("The SoCs adb shell dumpsys gfxinfo must contain a Skia pipeline",
                skiaDataIndex >= 0);

        String tmpinfo = gfxinfo;
        while (skiaDataIndex != -1) {
            // Remove string before next Skia pipeline
            tmpinfo = tmpinfo.substring(skiaDataIndex);

            // Get the pipeline descriptor line
            final int newlinecharacter = tmpinfo.indexOf(System.getProperty("line.separator"));
            String line = tmpinfo.substring(0, newlinecharacter);

            // Confirm that the pipeline uses Vulkan
            assertTrue("All Skia pipelines must use Vulkan", line.equals(SKIA_VULKAN_PIPELINE));

            // Remove line and find next pipeline
            tmpinfo = tmpinfo.substring(newlinecharacter + 1);
            skiaDataIndex = tmpinfo.indexOf(SKIA_PIPELINE);
        }
    }

    /**
     * All SoCs released with V must support ABP 2022
     */
    @VsrTest(requirements = {"VSR-3.2.1-008"})
    @Test
    public void checkAndroidBaselineProfile2022Support() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        assumeTrue("Test does not apply for SoCs launched before V", apiLevel >= Build.VIC);

        boolean hasOnlyCpuDevice = true;
        for (JSONObject device : mVulkanDevices) {
            if (device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_CPU) {
                hasOnlyCpuDevice = false;
            }
        }

        if (hasOnlyCpuDevice) {
            return;
        }

        String supported = mVulkanProfiles.getString("VP_ANDROID_baseline_2022");
        assertEquals("This SoC must support VP_ANDROID_baseline_2022.", "SUPPORTED", supported);
    }

    /**
     * All SoCs released with V must support VPA15
     */
    @VsrTest(requirements = {"VSR-3.2.1-008"})
    @Test
    public void checkVpAndroid15MinimumsSupport() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        assumeTrue("Test does not apply for SoCs launched before V", apiLevel >= Build.VIC);

        boolean hasOnlyCpuDevice = true;
        for (JSONObject device : mVulkanDevices) {
            if (device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_CPU) {
                hasOnlyCpuDevice = false;
            }
        }

        if (hasOnlyCpuDevice) {
            return;
        }

        String supported = mVulkanProfiles.getString("VP_ANDROID_15_minimums");
        assertEquals("This SoC must support VP_ANDROID_15_minimums.", "SUPPORTED", supported);
    }

    /**
     * All SoCs released with V must support protectedMemory and VK_EXT_global_priority
     */
    @VsrTest(requirements = {"VSR-3.2.1-011"})
    @Test
    public void checkProtectedMemoryAndGlobalPrioritySupport() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        assumeTrue("Test does not apply for SoCs launched before V", apiLevel >= Build.VIC);

        assertTrue(mVulkanDevices.length > 0);

        for (JSONObject device : mVulkanDevices) {
            if (device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_CPU) {
                continue;
            }

            final boolean extGlobalPriority = hasExtension(device,
                    VK_EXT_GLOBAL_PRIORITY_EXTENSION_NAME, VK_EXT_GLOBAL_PRIORITY_SPEC_VERSION);
            final boolean khrGlobalPriority = hasExtension(device,
                    VK_KHR_GLOBAL_PRIORITY_EXTENSION_NAME, VK_KHR_GLOBAL_PRIORITY_SPEC_VERSION);
            assertTrue("All non-cpu Vulkan devices must support global_priority",
                    extGlobalPriority || khrGlobalPriority);

            final int protectedMemory =
                    device.getJSONObject("protectedMemoryFeatures").getInt("protectedMemory");
            assertTrue("All non-cpu Vulkan devices must support protectedMemory",
                    protectedMemory == 1);
        }
    }

    private boolean hasExtension(JSONObject device, String name, int minVersion) throws Exception {
        JSONArray extensions = device.getJSONArray("extensions");
        for (int i = 0; i < extensions.length(); i++) {
            JSONObject ext = extensions.getJSONObject(i);
            if (ext.getString("extensionName").equals(name)
                    && ext.getInt("specVersion") >= minVersion)
                return true;
        }
        return false;
    }
}
