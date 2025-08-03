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

package android.os.cts;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RestrictedBuildTest;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;
/**
 * Runs the host side tests for Environment.java
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class BuildHostTests extends BaseHostJUnit4Test {

    private static final String RO_DEBUGGABLE = "ro.debuggable";
    private static final String RO_SECURE = "ro.secure";
    private static final String RO_BUILD_TYPE = " ro.build.type";
    private static final String EXPECTED_BUILD_VARIANT = "user";

    @Test
    @RestrictedBuildTest
    public void testIsSecureUserBuild() throws DeviceNotAvailableException {
        assertEquals("Must be a user build", EXPECTED_BUILD_VARIANT,
                getDevice().getProperty(RO_BUILD_TYPE));
        assertEquals("Must be a non-debuggable build", "0", getDevice().getProperty(RO_DEBUGGABLE));
        assertEquals("Must be a secure build", "1", getDevice().getProperty(RO_SECURE));
    }
}
