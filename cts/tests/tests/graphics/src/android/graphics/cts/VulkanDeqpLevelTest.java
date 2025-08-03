/*
 * Copyright 2020 The Android Open Source Project
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

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that feature flag android.software.vulkan.deqp.level is present and that it has an
 * acceptable value.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot access ro.board.* system properties")
public class VulkanDeqpLevelTest {

    private static final int MINIMUM_VULKAN_DEQP_LEVEL = 0x07E30301; // Corresponds to 2019-03-01

    // Require patch version 3 for Vulkan 1.0: It was the first publicly available version,
    // and there was an important bugfix relative to 1.0.2.
    private static final int VULKAN_1_0 = 0x00400003; // 1.0.3

    private FeatureInfo mVulkanHardwareVersion = null;
    private FeatureInfo mFeatureVulkanDeqpLevel = null;

    @Before
    public void setup() {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
        final FeatureInfo[] features = pm.getSystemAvailableFeatures();
        if (features != null) {
            for (FeatureInfo feature : features) {
                if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) {
                    mVulkanHardwareVersion = feature;
                } else if (PackageManager.FEATURE_VULKAN_DEQP_LEVEL.equals(feature.name)) {
                    mFeatureVulkanDeqpLevel = feature;
                }
            }
        }
    }

    @CddTest(requirement = "7.1.4.2/C-1-8,C-1-9")
    @Test
    public void testVulkanDeqpLevel() {
        assumeTrue(
                "Test only applies for API level >= 30 (Android 11)",
                PropertyUtil.getVsrApiLevel() >= 30);

        assumeTrue(
                "Test does not apply if Vulkan 1.0 or higher is not supported",
                mVulkanHardwareVersion != null && mVulkanHardwareVersion.version >= VULKAN_1_0);

        if (mFeatureVulkanDeqpLevel == null
                || mFeatureVulkanDeqpLevel.version < MINIMUM_VULKAN_DEQP_LEVEL) {
            String message = String.format(
                    "Feature %s must be present and have at least version %d.",
                    PackageManager.FEATURE_VULKAN_DEQP_LEVEL, MINIMUM_VULKAN_DEQP_LEVEL);
            message += "\nActual feature value: " + mFeatureVulkanDeqpLevel;
            fail(message);
        }
    }
}
