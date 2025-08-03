/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.healthconnect.cts;

import static android.healthconnect.cts.utils.TestUtils.isHardwareSupported;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.health.connect.HealthServicesInitializer;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

public class HealthServicesInitializerTest {
    /**
     * HealthServicesInitializer.registerServiceWrappers() should only be called by
     * SystemServiceRegistry during boot up. Calling this API at any other time should throw an
     * exception.
     */
    @Test
    public void testRegisterServiceThrowsException() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if the hardware not supported
        if (!isHardwareSupported(context)) {
            return;
        }
        assertThrows(
                IllegalStateException.class, HealthServicesInitializer::registerServiceWrappers);
    }

    /**
     * context.getSystemService(Context.HEALTHCONNECT_SERVICE) returns the services on supported
     * devices.
     */
    @Test
    public void testHealthServiceRegistered() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Object service = context.getSystemService(Context.HEALTHCONNECT_SERVICE);
        if (isHardwareSupported(context)) {
            assertThat(service).isNotNull();
        } else {
            assertThat(service).isNull();
        }
    }
}
