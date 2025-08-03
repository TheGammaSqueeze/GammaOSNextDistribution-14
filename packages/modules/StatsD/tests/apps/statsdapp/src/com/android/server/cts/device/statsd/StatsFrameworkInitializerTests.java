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

package com.android.server.cts.device.statsd;

import android.os.StatsFrameworkInitializer;
import org.junit.Test;

import static org.junit.Assert.fail;

public class StatsFrameworkInitializerTests {
    @Test
    public void testRegisterServiceWrappers_expectFail() {
        try {
            StatsFrameworkInitializer.registerServiceWrappers();
            fail("Expected exception when calling "
                    + "StatsFrameworkInitializer.registerServiceWrappers() outside of "
                    + "SystemServiceRegistry!");
        } catch (IllegalStateException expected) {}
    }
}
