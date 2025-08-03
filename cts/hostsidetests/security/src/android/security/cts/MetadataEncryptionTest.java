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

package android.security.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host-side test for metadata encryption.  This is a host-side test because
 * the "ro.crypto.metadata.enabled" property is not exposed to apps.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class MetadataEncryptionTest extends BaseHostJUnit4Test {
    private ITestDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
    }

    /**
     * Test that metadata encryption is enabled.
     * To enable metadata encryption, see
     * https://source.android.com/security/encryption/metadata
     *
     * @throws Exception
     */
    @CddTest(requirement="9.9.3/C-1-5")
    @Test
    public void testMetadataEncryptionIsEnabled() throws Exception {
        assumeSecurityModelCompat();
        if (PropertyUtil.getFirstApiLevel(mDevice) <= 29) {
          return; // Requirement does not apply to devices running Q or earlier
        }
        if (PropertyUtil.propertyEquals(mDevice, "ro.crypto.type", "managed")) {
          // Android is running in a virtualized environment and the file
          // system is encrypted by the host system.
          // Note: All encryption-related CDD requirements still must be met,
          // but they can't be tested directly in this case.
          return;
        }
        assertTrue("Metadata encryption must be enabled",
            mDevice.getBooleanProperty("ro.crypto.metadata.enabled", false));
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
}
