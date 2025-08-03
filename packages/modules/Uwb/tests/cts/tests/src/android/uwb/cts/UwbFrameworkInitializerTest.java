/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.uwb.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.uwb.UwbFrameworkInitializer;
import android.uwb.UwbManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

/**
 * Test of {@link UwbManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get UwbManager in instant app mode")
public class UwbFrameworkInitializerTest {
    private final Context mContext = InstrumentationRegistry.getContext();
    private UwbManager mUwbManager;

    @Before
    public void setup() throws Exception {
        mUwbManager = mContext.getSystemService(UwbManager.class);
        assumeTrue(UwbTestUtils.isUwbSupported(mContext));
        assertThat(mUwbManager).isNotNull();
    }

    /**
     * UwbFrameworkInitializer.registerServiceWrappers() should only be called by
     * SystemServiceRegistry during boot up when Uwb is first initialized. Calling this API at
     * any other time should throw an exception.
     */
    @Test
    public void testRegisterServiceWrappers_failsWhenCalledOutsideOfSystemServiceRegistry() {
        try {
            UwbFrameworkInitializer.registerServiceWrappers();
            fail("Expected exception when calling "
                    + "UwbFrameworkInitializer.registerServiceWrappers() outside of "
                    + "SystemServiceRegistry!");
        } catch (IllegalStateException expected) { }
    }
}
