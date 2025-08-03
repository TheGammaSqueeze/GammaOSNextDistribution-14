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

package com.android.ondevicepersonalization.services.data.vendor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalDataTest {
    @Test
    public void testBuilderAndEquals() {
        String key = "key";
        byte[] data = "data".getBytes();
        LocalData localData1 = new LocalData.Builder()
                .setData(data)
                .setKey(key)
                .build();
        assertArrayEquals(localData1.getData(), data);
        assertEquals(localData1.getKey(), key);
        LocalData localData2 = new LocalData.Builder(key, data).build();
        assertEquals(localData1, localData2);
        assertEquals(localData1.hashCode(), localData2.hashCode());
    }

    @Test
    public void testBuildTwiceThrows() {
        String key = "key";
        byte[] data = "data".getBytes();
        LocalData.Builder builder = new LocalData.Builder()
                .setData(data)
                .setKey(key);
        builder.build();
        assertThrows(IllegalStateException.class, () -> builder.build());
    }
}
