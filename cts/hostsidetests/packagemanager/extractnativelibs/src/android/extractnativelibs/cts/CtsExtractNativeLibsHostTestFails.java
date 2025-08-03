/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.extractnativelibs.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test failure cases
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsExtractNativeLibsHostTestFails extends CtsExtractNativeLibsHostTestBase {
    private static final String TEST_NO_EXTRACT_MISALIGNED_APK =
            "CtsExtractNativeLibsAppFalseWithMisalignedLib.apk";

    @Override
    public void setUp() throws Exception {
        // Skip incremental installations for non-incremental devices
        assumeTrue(isIncrementalInstallSupported());
        super.setUp();
    }
    /**
     * Test with a app that has extractNativeLibs=false but with mis-aligned lib files,
     * using Incremental install.
     */
    @Test
    @AppModeFull
    public void testExtractNativeLibsIncrementalFails() throws Exception {
        String result = installIncrementalPackageFromResource(TEST_NO_EXTRACT_MISALIGNED_APK);
        assertTrue(result.contains("Failed to extract native libraries"));
        assertFalse(isPackageInstalled(TEST_NO_EXTRACT_PKG));
    }
}
