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

package android.neuralnetworks.cts;

import android.content.res.AssetManager;
import androidx.test.InstrumentationRegistry;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ModelAssetTest {
    static {
        System.loadLibrary("ctsneuralnetworks_jni");
    }

    @Parameterized.Parameter(0)
    public String mDeviceName;

    @Parameters(name = "{0}")
    public static List<String> deviceNameList() {
        return Arrays.asList(getNnapiDevices());
    }

    @Test
    public void testCompileModelFromAsset() {
        AssetManager assets = InstrumentationRegistry.getContext().getAssets();
        nativeTestCompileFromAssetFile(assets, mDeviceName);
    }

    private static native String[] getNnapiDevices();

    private static native void nativeTestCompileFromAssetFile(
            AssetManager jAssetManager, String jDeviceName);
}
