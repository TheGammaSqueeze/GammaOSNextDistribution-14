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

package com.android.tools.layoutlib.java.nio;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/**
 * Delegate to fix differences between the Android and the JVM versions of java.nio.Buffer
 */
public class Buffer_Delegate {
    /**
     * The Android version of java.nio.Buffer has an extra final field called _elementSizeShift
     * that only depend on the implementation of the buffer. This method can be called instead
     * when wanting to access the value of that field on the JVM.
     */
    public static int elementSizeShift(Buffer buffer) {
        if (buffer instanceof ByteBuffer) {
            return 0;
        }
        if (buffer instanceof ShortBuffer || buffer instanceof CharBuffer) {
            return 1;
        }
        if (buffer instanceof IntBuffer || buffer instanceof FloatBuffer) {
            return 2;
        }
        if (buffer instanceof LongBuffer || buffer instanceof DoubleBuffer) {
            return 3;
        }
        return 0;
    }
}
