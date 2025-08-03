/*
 * Copyright (C) 2023 Google LLC.
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.GmsTest;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.VsrTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * VTS test for GPU profiling requirements.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class GpuProfilingTest extends BaseHostJUnit4Test {
    private static final int VK_PHYSICAL_DEVICE_TYPE_CPU = 4;
    private static final int VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU = 3;

    private JSONObject[] mVulkanDevices;

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
    }

    /**
     * Devices launched in Android 12 and beyond must support GPU profiling.
     */
    @VsrTest(requirements = {"VSR-5.1-002"})
    @Test
    public void checkGpuProfilingRequirements() throws Exception {
        final int apiLevel = Util.getVendorApiLevelOrFirstProductApiLevel(getDevice());

        // Only test for 64-bits devices launched in Android 12 and above.
        assumeTrue("Test does not apply for API level lower than S", apiLevel >= Build.SC);
        assumeTrue("Test does not apply for 32-bits devices",
                getDevice().getProperty("ro.product.cpu.abi").contains("64"));
        assumeTrue("Test does not apply for non-handheld devices", Util.isHandheld(getDevice()));
        assumeFalse(
                "Test does not apply for devices with only CPU Vulkan support", hasOnlyCpuDevice());
        assumeFalse("Test does not apply for devices with only virtual GPU Vulkan support",
                hasOnlyVirtualGpuDevice());

        assertTrue("API level S must support GPU profiling",
                PropertyUtil.propertyEquals(getDevice(), "graphics.gpu.profiler.support", "true"));
    }

    private boolean hasOnlyCpuDevice() throws Exception {
        for (JSONObject device : mVulkanDevices) {
            if (device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_CPU) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOnlyVirtualGpuDevice() throws Exception {
        for (JSONObject device : mVulkanDevices) {
            if (device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU) {
                return false;
            }
        }
        return true;
    }
}
