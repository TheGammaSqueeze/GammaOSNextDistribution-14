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

package com.android.server.cts.device.statsdatom;

import android.content.Context;
import android.os.PowerManager;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

public class PowerManagerTests {
    private static final String TAG = PowerManagerTests.class.getSimpleName();

    @Test
    public void testGetCurrentThermalStatus() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        powerManager.getCurrentThermalStatus();
    }

    @Test
    public void testGetThermalHeadroom() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        powerManager.getThermalHeadroom(10);
    }

    @Test
    public void testGetThermalHeadroomThresholds() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        powerManager.getThermalHeadroomThresholds();
    }
}
