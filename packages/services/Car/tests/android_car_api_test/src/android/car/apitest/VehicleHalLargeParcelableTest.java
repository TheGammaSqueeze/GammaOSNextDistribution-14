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
package android.car.apitest;

import static android.hardware.automotive.vehicle.TestVendorProperty.ECHO_REVERSE_BYTES;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public final class VehicleHalLargeParcelableTest extends CarApiTestBase {
    // TODO(b/225401892): Change this to a VTS test once ECHO_REVERSE_BYTES is defined as a system
    // property.
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() {
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(
                android.car.Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL);
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testEchoReverseBytesSmallData() throws Exception {
        // 1k data should be sent through direct payload.
        echoReverseBytes(1024);
    }

    @Test
    public void testEchoReverseBytesLargeData() throws Exception {
        // 16k data should be sent through shared memory.
        echoReverseBytes(16 * 1024);
    }

    void echoReverseBytes(int size) throws IOException {
        String result = runShellCommand(mUiAutomation, "cmd car_service test-echo-reverse-bytes "
                + ECHO_REVERSE_BYTES + " " + size);
        assumeTrue("ECHO_REVERSE_BYTES is not supported", !result.contains("Test Skipped"));
        assertThat(result).contains("Test Succeeded");
    }
}
