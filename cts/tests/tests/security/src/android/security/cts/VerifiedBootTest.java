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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PropertyUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VerifiedBootTest {
    private static final String TAG = "VerifiedBootTest";
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // This feature name check only applies to devices that first shipped with
        // SC or later.
        final int firstApiLevel =
                Math.min(PropertyUtil.getFirstApiLevel(), PropertyUtil.getVendorApiLevel());
        if (firstApiLevel >= Build.VERSION_CODES.S) {
            // Assumes every test in this file asserts a requirement of CDD section 9.
            assumeTrue("Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.",
                    mContext.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_SECURITY_MODEL_COMPATIBLE));
        }
    }

    private static boolean isLowRamExempt(PackageManager pm) {
        if (pm.hasSystemFeature(PackageManager.FEATURE_RAM_NORMAL)) {
            // No exemption for normal RAM
            return false;
        }
        return (PropertyUtil.getFirstApiLevel() < Build.VERSION_CODES.P);
    }

    /**
    * Asserts that Verified Boot is supported.
    *
    * A device is exempt if it launched on a pre-O_MR1 level.
    *
    * A device without the feature flag android.hardware.ram.normal is exempt if
    * it launched on a pre-P level.
    */
    @Test
    public void testVerifiedBootSupport() throws Exception {
        if (PropertyUtil.getFirstApiLevel() < Build.VERSION_CODES.O_MR1) {
            return;
        }
        PackageManager pm = mContext.getPackageManager();
        assertNotNull("PackageManager must not be null", pm);
        if (isLowRamExempt(pm)) {
            return;
        }
        assertTrue("Verified boot must be supported on the device",
                pm.hasSystemFeature(PackageManager.FEATURE_VERIFIED_BOOT));
    }
}
