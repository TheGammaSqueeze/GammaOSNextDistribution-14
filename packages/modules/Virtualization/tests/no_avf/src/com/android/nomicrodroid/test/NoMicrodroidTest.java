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

package com.android.nomicrodroid.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.content.pm.PackageManager;
import android.system.virtualmachine.VirtualMachineManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests to validate that devices without support for AVF (Android Virtualization Framework) are set
 * up correctly.
 */
@RunWith(JUnit4.class)
public class NoMicrodroidTest {

    @Before
    public void setUp() {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
        assume().withMessage("Device supports AVF")
                .that(pm.hasSystemFeature(PackageManager.FEATURE_VIRTUALIZATION_FRAMEWORK))
                .isFalse();
    }

    @CddTest(requirements = {"9.17/C-1-1"})
    @Test
    public void testVirtualMachineManagerLookupReturnsNull() {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThat(ctx.getSystemService(VirtualMachineManager.class)).isNull();
    }
}
