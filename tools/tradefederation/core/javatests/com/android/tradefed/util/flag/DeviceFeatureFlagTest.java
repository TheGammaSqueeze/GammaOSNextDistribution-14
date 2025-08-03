/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.util.flag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeviceFeatureFlag}. */
@RunWith(JUnit4.class)
public class DeviceFeatureFlagTest {
    private static final String VALID_FLAG = "namespace/package.flag=value";
    private static final String INVALID_FLAG = "invalid flag";

    @Test
    public void testConstructor_validFlag_setsFlagAttributes() {
        DeviceFeatureFlag deviceFeatureFlag = new DeviceFeatureFlag(VALID_FLAG);
        assertEquals("namespace", deviceFeatureFlag.getNamespace());
        assertEquals("package.flag", deviceFeatureFlag.getFlagName());
        assertEquals("value", deviceFeatureFlag.getFlagValue());
    }

    @Test
    public void testConstructor_invalidFlag_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceFeatureFlag(INVALID_FLAG));
    }
}
