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

package android.adservices.cts;

import static org.junit.Assert.assertThrows;

import android.adservices.AdServicesFrameworkInitializer;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Cts Test of {@link AdServicesFrameworkInitializer}. */
@SmallTest
public class AdServicesFrameworkInitializerTest {
    /**
     * AdServicesFrameworkInitializer.registerServiceWrappers() should only be called by
     * SystemServiceRegistry during boot up. Calling this API at any other time should throw an
     * exception.
     */
    @Test
    public void testRegisterServiceWrappers() {
        assertThrows(
                IllegalStateException.class,
                AdServicesFrameworkInitializer::registerServiceWrappers);
    }
}
