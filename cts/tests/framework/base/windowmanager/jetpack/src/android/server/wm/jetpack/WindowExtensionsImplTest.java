/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm.jetpack;

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.EXTENSION_VERSION_1;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getExtensionVersion;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link androidx.window.extensions.WindowExtensionsImpl} implementation.
 * Verifies that the extensions API level is aligned or higher than the current level.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerJetpackTestCases:WindowExtensionsImplTest
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class WindowExtensionsImplTest {

    @ApiTest(apis = {"androidx.window.extensions.WindowExtensions#getVendorApiLevel"})
    @Test
    public void testVerifiesExtensionVendorApiLevel() {
        assumeExtensionSupportedDevice();
        assertTrue(getExtensionVersion().compareTo(EXTENSION_VERSION_1) >= 0);
    }
}
