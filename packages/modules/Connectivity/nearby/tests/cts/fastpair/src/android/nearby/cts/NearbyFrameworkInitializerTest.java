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

package android.nearby.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.nearby.NearbyFrameworkInitializer;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

// NearbyFrameworkInitializer was added in T
@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class NearbyFrameworkInitializerTest {

    @Test
    public void testServicesRegistered() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getContext();
        assertThat(ctx.getSystemService(Context.NEARBY_SERVICE)).isNotNull();
    }

    // registerServiceWrappers can only be called during initialization and should throw otherwise
    @Test(expected = IllegalStateException.class)
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testThrowsException() {
        NearbyFrameworkInitializer.registerServiceWrappers();
    }
}
