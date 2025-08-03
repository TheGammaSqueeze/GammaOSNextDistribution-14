/*
 * Copyright 2016 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that the Vulkan loader is present, supports the required extensions, and that system
 * features accurately indicate the capabilities of the Vulkan driver if one exists.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class VulkanFeaturesTest {

    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static final String TAG = VulkanFeaturesTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Require patch version 3 for Vulkan 1.0: It was the first publicly available version,
    // and there was an important bugfix relative to 1.0.2.
    private static final int VULKAN_1_0 = 0x00400003; // 1.0.3
    private static final int VULKAN_1_1 = 0x00401000; // 1.1.0
    private static final int VULKAN_1_2 = 0x00402000; // 1.2.0
    private static final int VULKAN_1_3 = 0x00403000; // 1.3.0

    private static final String VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME =
            "VK_ANDROID_external_memory_android_hardware_buffer";
    private static final int VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_SPEC_VERSION = 2;

    private static final String VK_KHR_SURFACE = "VK_KHR_surface";
    private static final int VK_KHR_SURFACE_SPEC_VERSION = 25;

    private static final String VK_KHR_ANDROID_SURFACE = "VK_KHR_android_surface";
    private static final int VK_KHR_ANDROID_SURFACE_SPEC_VERSION = 6;

    private static final String VK_KHR_SWAPCHAIN = "VK_KHR_swapchain";
    private static final int VK_KHR_SWAPCHAIN_SPEC_VERSION = 68;

    private static final String VK_KHR_MAINTENANCE1 = "VK_KHR_maintenance1";
    private static final int VK_KHR_MAINTENANCE1_SPEC_VERSION = 1;

    private static final String VK_KHR_INCREMENTAL_PRESENT = "VK_KHR_incremental_present";
    private static final int VK_KHR_INCREMENTAL_PRESENT_SPEC_VERSION = 1;

    private static final int VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT = 0x8;
    private static final int VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT = 0x10;
    private static final int VK_PHYSICAL_DEVICE_TYPE_CPU = 4;

    private static final int API_LEVEL_BEFORE_ANDROID_HARDWARE_BUFFER_REQ = 28;

    private static final int DEQP_LEVEL_FOR_V = 0x7E80301;
    private static final int DEQP_LEVEL_FOR_U = 0x7E70301;
    private static final int DEQP_LEVEL_FOR_T = 0x7E60301;
    private static final int DEQP_LEVEL_FOR_S = 0x7E50301;
    private static final int DEQP_LEVEL_FOR_R = 0x7E40301;
    private static final int DEQP_LEVEL_BEFORE_R = 0;

    private static final Map<Integer, String[]> DEQP_EXTENSIONS_MAP = new ArrayMap<>();
    static {
        DEQP_EXTENSIONS_MAP.put(
                DEQP_LEVEL_FOR_V,
                new String[] {
                    "VK_KHR_cooperative_matrix",
                    "VK_KHR_maintenance5",
                    "VK_KHR_map_memory2",
                    "VK_KHR_ray_tracing_position_fetch",
                    "VK_ANDROID_external_format_resolve"});
        DEQP_EXTENSIONS_MAP.put(
                DEQP_LEVEL_FOR_U,
                new String[] {
                    "VK_KHR_fragment_shader_barycentric",
                    "VK_KHR_ray_tracing_maintenance1",
                    "VK_KHR_video_decode_h264",
                    "VK_KHR_video_decode_h265",
                    "VK_KHR_video_decode_queue",
                    "VK_KHR_video_queue",
                    "VK_GOOGLE_user_type"});
        DEQP_EXTENSIONS_MAP.put(
                DEQP_LEVEL_FOR_T,
                new String[] {
                    "VK_KHR_dynamic_rendering",
                    "VK_KHR_format_feature_flags2",
                    "VK_KHR_global_priority",
                    "VK_KHR_maintenance4",
                    "VK_KHR_portability_subset",
                    "VK_KHR_present_id",
                    "VK_KHR_present_wait",
                    "VK_KHR_shader_subgroup_uniform_control_flow",
                    "VK_KHR_portability_enumeration"});
        DEQP_EXTENSIONS_MAP.put(
                DEQP_LEVEL_FOR_S,
                new String[] {
                    "VK_KHR_copy_commands2",
                    "VK_KHR_shader_terminate_invocation",
                    "VK_KHR_ray_tracing_pipeline",
                    "VK_KHR_ray_query",
                    "VK_KHR_acceleration_structure",
                    "VK_KHR_pipeline_library",
                    "VK_KHR_deferred_host_operations",
                    "VK_KHR_fragment_shading_rate",
                    "VK_KHR_zero_initialize_workgroup_memory",
                    "VK_KHR_workgroup_memory_explicit_layout",
                    "VK_KHR_synchronization2",
                    "VK_KHR_shader_integer_dot_product"});
        DEQP_EXTENSIONS_MAP.put(
                DEQP_LEVEL_FOR_R,
                new String[] {
                    "VK_KHR_swapchain",
                    "VK_KHR_swapchain_mutable_format",
                    "VK_KHR_display_swapchain",
                    "VK_KHR_sampler_mirror_clamp_to_edge",
                    "VK_KHR_external_memory_win32",
                    "VK_KHR_external_memory_fd",
                    "VK_KHR_win32_keyed_mutex",
                    "VK_KHR_external_semaphore_win32",
                    "VK_KHR_external_semaphore_fd",
                    "VK_KHR_push_descriptor",
                    "VK_KHR_shader_float16_int8",
                    "VK_KHR_incremental_present",
                    "VK_KHR_8bit_storage",
                    "VK_KHR_create_renderpass2",
                    "VK_KHR_shared_presentable_image",
                    "VK_KHR_external_fence_win32",
                    "VK_KHR_external_fence_fd",
                    "VK_KHR_image_format_list",
                    "VK_KHR_driver_properties",
                    "VK_KHR_shader_float_controls",
                    "VK_KHR_depth_stencil_resolve",
                    "VK_KHR_draw_indirect_count",
                    "VK_KHR_shader_atomic_int64",
                    "VK_KHR_vulkan_memory_model",
                    "VK_KHR_uniform_buffer_standard_layout",
                    "VK_KHR_imageless_framebuffer",
                    "VK_KHR_shader_subgroup_extended_types",
                    "VK_KHR_buffer_device_address",
                    "VK_KHR_separate_depth_stencil_layouts",
                    "VK_KHR_timeline_semaphore",
                    "VK_KHR_spirv_1_4",
                    "VK_KHR_pipeline_executable_properties",
                    "VK_KHR_shader_clock",
                    "VK_KHR_performance_query",
                    "VK_KHR_shader_non_semantic_info",
                    "VK_KHR_surface",
                    "VK_KHR_display",
                    "VK_KHR_xlib_surface",
                    "VK_KHR_xcb_surface",
                    "VK_KHR_wayland_surface",
                    "VK_KHR_mir_surface",
                    "VK_KHR_android_surface",
                    "VK_KHR_win32_surface",
                    "VK_KHR_get_surface_capabilities2",
                    "VK_KHR_get_display_properties2",
                    "VK_KHR_surface_protected_capabilities",
                    "VK_GOOGLE_decorate_string",
                    "VK_GOOGLE_hlsl_functionality1"});
        DEQP_EXTENSIONS_MAP.put(
                DEQP_LEVEL_BEFORE_R,
                new String[] {
                    "VK_KHR_multiview",
                    "VK_KHR_device_group",
                    "VK_KHR_shader_draw_parameters",
                    "VK_KHR_maintenance1",
                    "VK_KHR_external_memory",
                    "VK_KHR_external_semaphore",
                    "VK_KHR_16bit_storage",
                    "VK_KHR_descriptor_update_template",
                    "VK_KHR_external_fence",
                    "VK_KHR_maintenance2",
                    "VK_KHR_variable_pointers",
                    "VK_KHR_dedicated_allocation",
                    "VK_KHR_storage_buffer_storage_class",
                    "VK_KHR_relaxed_block_layout",
                    "VK_KHR_get_memory_requirements2",
                    "VK_KHR_sampler_ycbcr_conversion",
                    "VK_KHR_bind_memory2",
                    "VK_KHR_maintenance3",
                    "VK_KHR_get_physical_device_properties2",
                    "VK_KHR_device_group_creation",
                    "VK_KHR_external_memory_capabilities",
                    "VK_KHR_external_semaphore_capabilities",
                    "VK_KHR_external_fence_capabilities",
                    "VK_ANDROID_external_memory_android_hardware_buffer",
                    "VK_GOOGLE_display_timing"});
    }

    private PackageManager mPm;
    private FeatureInfo mVulkanHardwareLevel = null;
    private FeatureInfo mVulkanHardwareVersion = null;
    private FeatureInfo mVulkanHardwareCompute = null;
    private FeatureInfo mVulkanDeqpLevel = null;
    private JSONObject mVkJSON = null;
    private JSONObject mVulkanDevices[];
    private JSONObject mBestDevice = null;

    @Before
    public void setup() throws Throwable {
        mPm = InstrumentationRegistry.getTargetContext().getPackageManager();
        FeatureInfo features[] = mPm.getSystemAvailableFeatures();
        if (features != null) {
            for (FeatureInfo feature : features) {
                if (PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL.equals(feature.name)) {
                    mVulkanHardwareLevel = feature;
                    if (DEBUG) {
                        Log.d(TAG, feature.name + "=" + feature.version);
                    }
                } else if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) {
                    mVulkanHardwareVersion = feature;
                    if (DEBUG) {
                        Log.d(TAG, feature.name + "=0x" + Integer.toHexString(feature.version));
                    }
                } else if (PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE.equals(feature.name)) {
                    mVulkanHardwareCompute = feature;
                    if (DEBUG) {
                        Log.d(TAG, feature.name + "=" + feature.version);
                    }
                } else if (PackageManager.FEATURE_VULKAN_DEQP_LEVEL.equals(feature.name)) {
                    mVulkanDeqpLevel = feature;
                    if (DEBUG) {
                        Log.d(TAG, feature.name + "=" + feature.version);
                    }
                }
            }
        }

        mVkJSON = new JSONObject(nativeGetVkJSON());
        mVulkanDevices = getVulkanDevices(mVkJSON);
        mBestDevice = getBestDevice();
    }

    @CddTest(requirement = "7.1.4.2/C-1-1,C-2-1")
    @Test
    public void testVulkanHardwareFeatures() throws JSONException {
        if (DEBUG) {
            Log.d(TAG, "Inspecting " + mVulkanDevices.length + " devices");
        }
        if (mVulkanDevices.length == 0) {
            assertNull("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL +
                       " is supported, but no Vulkan physical devices are available",
                       mVulkanHardwareLevel);
            assertNull("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_VERSION +
                       " is supported, but no Vulkan physical devices are available",
                       mVulkanHardwareLevel);
            assertNull("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                       " is supported, but no Vulkan physical devices are available",
                       mVulkanHardwareCompute);
            return;
        }

        if (hasOnlyCpuDevice()) {
            return;
        }

        assertNotNull("Vulkan physical devices are available, but system feature " +
                      PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL + " is not supported",
                      mVulkanHardwareLevel);
        assertNotNull("Vulkan physical devices are available, but system feature " +
                      PackageManager.FEATURE_VULKAN_HARDWARE_VERSION + " is not supported",
                      mVulkanHardwareVersion);
        if (mVulkanHardwareLevel == null || mVulkanHardwareVersion == null) {
            return;
        }

        assertTrue("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL +
                   " version " + mVulkanHardwareLevel.version + " is not one of the defined " +
                   " versions [0..1]",
                   mVulkanHardwareLevel.version >= 0 && mVulkanHardwareLevel.version <= 1);
        assertTrue("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_VERSION +
                   " version 0x" + Integer.toHexString(mVulkanHardwareVersion.version) + " is not" +
                   " one of the versions allowed",
                   isHardwareVersionAllowed(mVulkanHardwareVersion.version));
        if (mVulkanHardwareCompute != null) {
            assertTrue("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                       " version " + mVulkanHardwareCompute.version +
                       " is not one of the versions allowed",
                       mVulkanHardwareCompute.version == 0);
        }

        int bestDeviceLevel = determineHardwareLevel(mBestDevice);
        int bestComputeLevel = determineHardwareCompute(mBestDevice);
        int bestDeviceVersion = determineHardwareVersion(mBestDevice);

        assertEquals("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL +
            " version " + mVulkanHardwareLevel.version + " doesn't match best physical device " +
            " hardware level " + bestDeviceLevel,
            bestDeviceLevel, mVulkanHardwareLevel.version);
        assertTrue(
            "System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_VERSION +
            " version 0x" + Integer.toHexString(mVulkanHardwareVersion.version) +
            " isn't close enough (same major and minor version, less or equal patch version)" +
            " to best physical device version 0x" + Integer.toHexString(bestDeviceVersion),
            isVersionCompatible(bestDeviceVersion, mVulkanHardwareVersion.version));
        if (mVulkanHardwareCompute == null) {
            assertEquals("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                " not present, but required features are supported",
                bestComputeLevel, -1);
        } else {
            assertEquals("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                " version " + mVulkanHardwareCompute.version +
                " doesn't match best physical device (version: " + bestComputeLevel + ")",
                bestComputeLevel, mVulkanHardwareCompute.version);
        }
    }

    @CddTest(requirement = "3.3.1/C-0-12")
    @Test
    public void testVulkanApplicationBinaryInterfaceRequirements() throws JSONException {
        assumeTrue("Skipping because Vulkan is not supported", mVulkanHardwareVersion != null);

        if (hasOnlyCpuDevice()) {
            return;
        }

        assertTrue("Devices must support the core Vulkan 1.1",
                mVulkanHardwareVersion.version >= VULKAN_1_1);
    }

    @CddTest(requirement = "7.1.4.2/C-1-3")
    @Test
    public void testVulkanApiForEachDevice() throws JSONException {
        for (JSONObject device : mVulkanDevices) {
            assertTrue("All enumerated VPhysicalDevice must support Vulkan 1.1",
                    determineHardwareVersion(device) >= VULKAN_1_1);
        }
    }

    @CddTest(requirement = "7.1.4.2/C-3-1")
    @Test
    public void testVulkan1_1Requirements() throws JSONException {
        if (mVulkanHardwareVersion == null || mVulkanHardwareVersion.version < VULKAN_1_1
                || !PropertyUtil.isVendorApiLevelNewerThan(
                        API_LEVEL_BEFORE_ANDROID_HARDWARE_BUFFER_REQ)) {
            return;
        }
        assertTrue("Devices with Vulkan 1.1 must support sampler YCbCr conversion",
                mBestDevice.getJSONObject("samplerYcbcrConversionFeatures")
                           .getInt("samplerYcbcrConversion") != 0);

        if (hasOnlyCpuDevice()) {
            return;
        }
        assertTrue("Devices with Vulkan 1.1 must support " +
                VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME +
                " (version >= " + VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_SPEC_VERSION +
                ")",
                hasDeviceExtension(mBestDevice,
                    VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
                    VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_SPEC_VERSION));
        assertTrue("Devices with Vulkan 1.1 must support SYNC_FD external semaphores",
                hasHandleType(mBestDevice.getJSONArray("externalSemaphoreProperties"),
                    VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT,
                    "externalSemaphoreFeatures", 0x3 /* importable + exportable */));
        assertTrue("Devices with Vulkan 1.1 must support SYNC_FD external fences",
                hasHandleType(mBestDevice.getJSONArray("externalFenceProperties"),
                    VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT,
                    "externalFenceFeatures", 0x3 /* importable + exportable */));
    }

    @CddTest(requirement = "7.1.4.2/C-1-7, 3.3.1/C-0-12")
    @Test
    public void testVulkanRequiredExtensions() throws JSONException {
        assumeTrue("Skipping because Vulkan is not supported", mVulkanDevices.length > 0);

        assertVulkanInstanceExtension(VK_KHR_SURFACE, VK_KHR_SURFACE_SPEC_VERSION);
        assertVulkanInstanceExtension(VK_KHR_ANDROID_SURFACE, VK_KHR_ANDROID_SURFACE_SPEC_VERSION);

        assertVulkanDeviceExtension(VK_KHR_SWAPCHAIN, VK_KHR_SWAPCHAIN_SPEC_VERSION);
        assertVulkanDeviceExtension(VK_KHR_INCREMENTAL_PRESENT,
                VK_KHR_INCREMENTAL_PRESENT_SPEC_VERSION);
        assertVulkanDeviceExtension(VK_KHR_MAINTENANCE1, VK_KHR_MAINTENANCE1_SPEC_VERSION);
    }

    @CddTest(requirement = "7.9.2/C-1-5")
    @Test
    public void testVulkanVersionForVrHighPerformance() {
        if (!mPm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE))
            return;
        assertTrue(
            "VR high-performance devices must support Vulkan 1.0 with Hardware Level 0, " +
            "but this device does not.",
            mVulkanHardwareVersion != null && mVulkanHardwareVersion.version >= VULKAN_1_0 &&
            mVulkanHardwareLevel != null && mVulkanHardwareLevel.version >= 0);
    }

    @CddTest(requirement = "7.1.4.2/C-1-11")
    @Test
    public void testVulkanBlockedExtensions() throws JSONException {
        assertNoVulkanDeviceExtension("VK_KHR_performance_query");
        assertNoVulkanDeviceExtension("VK_KHR_video_queue");
        assertNoVulkanDeviceExtension("VK_KHR_video_decode_queue");
        assertNoVulkanDeviceExtension("VK_KHR_video_encode_queue");
    }

    @CddTest(requirement = "7.1.4.2")
    @Test
    public void testVulkanVariantSupport() throws JSONException {
        assumeTrue("Skipping because Vulkan is not supported", mVulkanHardwareVersion != null);

        int expectedVariant = 0x0;
        int actualVariant = (mVulkanHardwareVersion.version >> 29) & 0x7;
        assertEquals(expectedVariant, actualVariant);
    }

    @CddTest(requirement = "7.1.4.2/C-1-14")
    @Test
    public void testVulkanExposedDeviceExtensions() throws JSONException {
        assumeTrue("Skipping because Vulkan is not supported", mVulkanHardwareVersion != null);

        if (hasOnlyCpuDevice()) {
            return;
        }

        // Determine the set of device-side extensions that can be exposed
        //   Note this only includes VK_KHR, VK_GOOGLE, VK_ANDROID
        final int deviceDeqpLevel = mVulkanDeqpLevel.version;
        Set<String> allowedDeviceExtensions = new HashSet<String>();
        for (Integer level : DEQP_EXTENSIONS_MAP.keySet()) {
            if (deviceDeqpLevel >= level) {
                allowedDeviceExtensions.addAll(Arrays.asList(DEQP_EXTENSIONS_MAP.get(level)));
            }
        }

        // Get the set of all device-side extensions exposed by the device
        final JSONArray deviceExtensions = mBestDevice.getJSONArray("extensions");
        // Search for any device extensions that should not be exposed
        Set<String> untestedExtensions = new HashSet<String>();
        for (int i = 0; i < deviceExtensions.length(); i++) {
            JSONObject extension = deviceExtensions.getJSONObject(i);
            String deviceExtension = extension.getString("extensionName");
            boolean vk_android = deviceExtension.startsWith("VK_ANDROID");
            boolean vk_google = deviceExtension.startsWith("VK_GOOGLE");
            boolean vk_khr = deviceExtension.startsWith("VK_KHR");
            if (!vk_android && !vk_google && !vk_khr) {
                if (DEBUG) {
                    Log.d(TAG, "Device extension exposed is not KHR, GOOGLE, or ANDROID: "
                            + deviceExtension);
                }
                continue;
            }
            if (!allowedDeviceExtensions.contains(deviceExtension)) {
                if (DEBUG) {
                    Log.d(TAG, "Device extension exposed on device not found in dEQP level "
                            + deviceDeqpLevel + ": " + deviceExtension);
                }
                untestedExtensions.add(deviceExtension);
            }
        }

        assertEquals("This device exposes the extensions:\n" + untestedExtensions
                + "\n that are not tested under its claimed dEQP level: " + deviceDeqpLevel,
                0, untestedExtensions.size());
    }

    private static native String nativeGetABPSupport();
    private static native String nativeGetABPCpuOnlySupport();

    @CddTest(requirement = "7.1.4.2/C-1-13")
    @Test
    public void testAndroidBaselineProfile2021Support() throws JSONException {
        assumeTrue("Skipping because Vulkan is not supported", mVulkanHardwareVersion != null);

        if (!hasOnlyCpuDevice()) {
            assertEquals("This device must support the ABP 2021.", "", nativeGetABPSupport());
        } else {
            assertEquals("This device must support the ABP 2021.", "",
                    nativeGetABPCpuOnlySupport());
        }
    }

    private JSONObject getBestDevice() throws JSONException {
        JSONObject bestDevice = null;
        int bestDeviceLevel = -1;
        int bestComputeLevel = -1;
        int bestDeviceVersion = -1;
        for (JSONObject device : mVulkanDevices) {
            int level = determineHardwareLevel(device);
            int compute = determineHardwareCompute(device);
            int version = determineHardwareVersion(device);
            if (DEBUG) {
                Log.d(TAG, device.getJSONObject("properties").getString("deviceName") +
                    ": level=" + level + " compute=" + compute +
                    " version=0x" + Integer.toHexString(version));
            }
            if (level >= bestDeviceLevel && compute >= bestComputeLevel &&
                    version >= bestDeviceVersion) {
                bestDevice = device;
                bestDeviceLevel = level;
                bestComputeLevel = compute;
                bestDeviceVersion = version;
            }
        }
        return bestDevice;
    }

    private boolean hasOnlyCpuDevice() throws JSONException {
        for (JSONObject device : mVulkanDevices) {
            if (device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_CPU) {
                return false;
            }
        }
        return true;
    }

    private int determineHardwareLevel(JSONObject device) throws JSONException {
        JSONObject features = device.getJSONObject("features");
        boolean textureCompressionETC2 = features.getInt("textureCompressionETC2") != 0;
        boolean fullDrawIndexUint32 = features.getInt("fullDrawIndexUint32") != 0;
        boolean imageCubeArray = features.getInt("imageCubeArray") != 0;
        boolean independentBlend = features.getInt("independentBlend") != 0;
        boolean geometryShader = features.getInt("geometryShader") != 0;
        boolean tessellationShader = features.getInt("tessellationShader") != 0;
        boolean sampleRateShading = features.getInt("sampleRateShading") != 0;
        boolean textureCompressionASTC_LDR = features.getInt("textureCompressionASTC_LDR") != 0;
        boolean fragmentStoresAndAtomics = features.getInt("fragmentStoresAndAtomics") != 0;
        boolean shaderImageGatherExtended = features.getInt("shaderImageGatherExtended") != 0;
        boolean shaderUniformBufferArrayDynamicIndexing = features.getInt("shaderUniformBufferArrayDynamicIndexing") != 0;
        boolean shaderSampledImageArrayDynamicIndexing = features.getInt("shaderSampledImageArrayDynamicIndexing") != 0;
        if (!textureCompressionETC2) {
            return -1;
        }
        if (!fullDrawIndexUint32 ||
            !imageCubeArray ||
            !independentBlend ||
            !geometryShader ||
            !tessellationShader ||
            !sampleRateShading ||
            !textureCompressionASTC_LDR ||
            !fragmentStoresAndAtomics ||
            !shaderImageGatherExtended ||
            !shaderUniformBufferArrayDynamicIndexing ||
            !shaderSampledImageArrayDynamicIndexing) {
            return 0;
        }
        return 1;
    }

    private int determineHardwareCompute(JSONObject device) throws JSONException {
        boolean variablePointers = false;
        try {
            variablePointers = device.getJSONObject("variablePointerFeatures")
                                             .getInt("variablePointers") != 0;
        } catch (JSONException exp) {
            try {
                variablePointers = device.getJSONObject("VK_KHR_variable_pointers")
                                                 .getJSONObject("variablePointerFeaturesKHR")
                                                 .getInt("variablePointers") != 0;
            }  catch (JSONException exp2) {
                variablePointers = false;
            }
        }
        JSONObject limits = device.getJSONObject("properties").getJSONObject("limits");
        int maxPerStageDescriptorStorageBuffers = limits.getInt("maxPerStageDescriptorStorageBuffers");
        if (DEBUG) {
            Log.d(TAG, device.getJSONObject("properties").getString("deviceName") +
                ": variablePointers=" + variablePointers +
                " maxPerStageDescriptorStorageBuffers=" + maxPerStageDescriptorStorageBuffers);
        }
        if (!variablePointers || maxPerStageDescriptorStorageBuffers < 16)
            return -1;
        return 0;
    }

    private int determineHardwareVersion(JSONObject device) throws JSONException {
        return device.getJSONObject("properties").getInt("apiVersion");
    }

    private boolean isVersionCompatible(int expected, int actual) {
        int expectedVariant = (expected >> 29) & 0x7;
        int expectedMajor   = (expected >> 22) & 0x7F;
        int expectedMinor   = (expected >> 12) & 0x3FF;
        int expectedPatch   = (expected >>  0) & 0xFFF;
        int actualVariant = (actual >> 29) & 0x7;
        int actualMajor   = (actual >> 22) & 0x7F;
        int actualMinor   = (actual >> 12) & 0x3FF;
        int actualPatch   = (actual >>  0) & 0xFFF;
        return (actualVariant == expectedVariant)
            && (actualMajor == expectedMajor)
            && (actualMinor == expectedMinor)
            && (actualPatch <= expectedPatch);
    }

    private boolean isHardwareVersionAllowed(int actual) {
        // Limit which system feature hardware versions are allowed. If a new major/minor version
        // is released, we don't want devices claiming support for it until tests for the new
        // version are available. And only claiming support for a base patch level per major/minor
        // pair reduces fragmentation seen by developers. Patch-level changes are supposed to be
        // forwards and backwards compatible; if a developer *really* needs to alter behavior based
        // on the patch version, they can do so at runtime, but must be able to handle previous
        // patch versions.
        final int[] ALLOWED_HARDWARE_VERSIONS = {
            VULKAN_1_0,
            VULKAN_1_1,
            VULKAN_1_2,
            VULKAN_1_3,
        };
        for (int expected : ALLOWED_HARDWARE_VERSIONS) {
            if (actual == expected) {
                return true;
            }
        }
        return false;
    }

    private void assertVulkanDeviceExtension(final String name, final int minVersion)
            throws JSONException {
        assertTrue(
                String.format(
                        "Devices with Vulkan must support device extension %s (version >= %d)",
                        name,
                        minVersion),
                hasDeviceExtension(mBestDevice, name, minVersion));
    }

    private void assertNoVulkanDeviceExtension(final String name)
            throws JSONException {
        for (JSONObject device : mVulkanDevices) {
            assertTrue(
                    String.format("Devices must not support Vulkan device extension %s", name),
                    !hasDeviceExtension(device, name, 0));
        }
    }

    private void assertVulkanInstanceExtension(final String name, final int minVersion)
            throws JSONException {
        assertTrue(
                String.format(
                        "Devices with Vulkan must support instance extension %s (version >= %d)",
                        name,
                        minVersion),
                hasInstanceExtension(name, minVersion));
    }

    private static boolean hasDeviceExtension(
            final JSONObject device,
            final String name,
            final int minVersion) throws JSONException {
        final JSONArray deviceExtensions = device.getJSONArray("extensions");
        return hasExtension(deviceExtensions, name, minVersion);
    }

    private boolean hasInstanceExtension(
            final String name,
            final int minVersion) throws JSONException {
        // Instance extensions are in the top-level vkjson object.
        final JSONArray instanceExtensions = mVkJSON.getJSONArray("extensions");
        return hasExtension(instanceExtensions, name, minVersion);
    }

    private static boolean hasExtension(
            final JSONArray extensions,
            final String name,
            final int minVersion) throws JSONException {
        for (int i = 0; i < extensions.length(); i++) {
            JSONObject ext = extensions.getJSONObject(i);
            if (ext.getString("extensionName").equals(name) &&
                    ext.getInt("specVersion") >= minVersion)
                return true;
        }
        return false;
    }

    private boolean hasHandleType(JSONArray handleTypes, int type,
            String featuresName, int requiredFeatures) throws JSONException {
        for (int i = 0; i < handleTypes.length(); i++) {
            JSONArray typeRecord = handleTypes.getJSONArray(i);
            if (typeRecord.getInt(0) == type) {
                JSONObject typeInfo = typeRecord.getJSONObject(1);
                if ((typeInfo.getInt(featuresName) & requiredFeatures) == requiredFeatures)
                    return true;
            }
        }
        return false;
    }

    private static native String nativeGetVkJSON();

    private static JSONObject[] getVulkanDevices(final JSONObject vkJSON) throws JSONException {
        JSONArray devicesArray = vkJSON.getJSONArray("devices");
        JSONObject[] devices = new JSONObject[devicesArray.length()];
        for (int i = 0; i < devicesArray.length(); i++) {
            devices[i] = devicesArray.getJSONObject(i);
        }
        return devices;
    }
}
