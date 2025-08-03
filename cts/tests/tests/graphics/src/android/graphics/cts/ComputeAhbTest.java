/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.graphics.cts;

import android.content.res.AssetManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class ComputeAhbTest {

    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    // TODO: Generalize this to cover multiple formats at some point. However, this requires
    // different shader bytecode per format (storage image format is specified both
    // in the SPIRV and in the API)

    @Test
    public void testAhbComputeShaderWrite() throws Exception {
        verifyComputeShaderWrite(InstrumentationRegistry.getContext().getAssets());
    }


    private static native void verifyComputeShaderWrite(AssetManager assetManager);
}
