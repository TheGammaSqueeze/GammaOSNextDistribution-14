/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.hardware.cts;

import static android.os.Build.VERSION_CODES;

import static com.android.compatibility.common.util.PropertyUtil.getFirstApiLevel;
import static com.android.compatibility.common.util.PropertyUtil.getVendorApiLevel;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that devices correctly declare the
 * {@link PackageManager#FEATURE_SECURITY_MODEL_COMPATIBLE} feature.
 */
@RunWith(AndroidJUnit4.class)
public class SecurityModelFeatureTest {
    private static final String ERROR_MSG = "Expected system feature missing. "
            + "The device must declare: " + PackageManager.FEATURE_SECURITY_MODEL_COMPATIBLE;
    private PackageManager mPackageManager;
    private boolean mHasSecurityFeature = false;

    @Before
    public void setUp() throws Exception {
        final int firstApiLevel = Math.min(getFirstApiLevel(), getVendorApiLevel());
        assumeTrue("Skipping test: it only applies to devices that first shipped with S or later.",
                   firstApiLevel >= VERSION_CODES.S);

        mPackageManager = InstrumentationRegistry.getTargetContext().getPackageManager();
        mHasSecurityFeature =
            mPackageManager.hasSystemFeature(PackageManager.FEATURE_SECURITY_MODEL_COMPATIBLE);
    }

    @Test
    @CddTest(requirement = "2.3.5/T-0-1")
    public void testTv() {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION));
        assertTrue(ERROR_MSG, mHasSecurityFeature);
    }

    @Test
    @CddTest(requirement = "2.4.5/W-0-1")
    public void testWatch() {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH));
        assertTrue(ERROR_MSG, mHasSecurityFeature);
    }

    @Test
    @CddTest(requirement = "2.5.5/A-0-1")
    public void testAuto() {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assertTrue(ERROR_MSG, mHasSecurityFeature);
    }

    // Handheld & tablet tested via CTS Verifier
}
