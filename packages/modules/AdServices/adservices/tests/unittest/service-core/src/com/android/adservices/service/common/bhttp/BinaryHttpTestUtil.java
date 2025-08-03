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

package com.android.adservices.service.common.bhttp;

import java.util.Arrays;

public class BinaryHttpTestUtil {

    static byte[] combineSections(byte[]... sections) {
        int pointer = 0;
        int totalSize = Arrays.stream(sections).mapToInt(s -> s.length).sum();
        byte[] result = new byte[totalSize];
        for (byte[] section : sections) {
            for (byte b : section) {
                result[pointer++] = b;
            }
        }
        return result;
    }
}
