/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.cts.e2e;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.adservices.ondevicepersonalization.InferenceOutput;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class InferenceOutputTest {
    @Test
    public void build_success() {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[] {1.0f});
        InferenceOutput output = new InferenceOutput.Builder().setDataOutputs(outputData).build();

        Map<Integer, Object> data = output.getDataOutputs();
        float[] value = (float[]) data.get(0);
        assertEquals(value[0], 1.0f, 0.01f);
    }

    @Test
    public void build_addData_success() {
        float[] expected = new float[] {1.0f};
        InferenceOutput output = new InferenceOutput.Builder().addDataOutput(0, expected).build();

        Map<Integer, Object> data = output.getDataOutputs();
        float[] value = (float[]) data.get(0);

        assertTrue(Arrays.equals(value, expected));
    }
}
