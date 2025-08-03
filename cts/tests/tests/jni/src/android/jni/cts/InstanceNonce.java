/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.jni.cts;

import dalvik.annotation.optimization.FastNative;

/**
 * Class with a bunch of native instance methods. These methods are called by
 * the various tests in {@link JniInstanceTest}.
 */
public class InstanceNonce {
    static {
        if (!JniTestCase.isCpuAbiNone()) {
            System.loadLibrary("jnitest");
        }
    }

    /**
     * Construct an instance.
     */
    public InstanceNonce() {
        // This space intentionally left blank.
    }

    // See JniInstanceTest for the expected behavior of these methods.

    public static native void missing();
    @FastNative
    public static native void missingFast();

    public static native void nopDlsym();
    @FastNative
    public static native void nopFastDlsym();

    public native void nop();
    @FastNative
    public native void nopFast();

    public native boolean returnBoolean();
    @FastNative
    public native boolean returnBooleanFast();

    public native byte returnByte();
    @FastNative
    public native byte returnByteFast();

    public native short returnShort();
    @FastNative
    public native short returnShortFast();

    public native char returnChar();
    @FastNative
    public native char returnCharFast();

    public native int returnInt();
    @FastNative
    public native int returnIntFast();

    public native long returnLong();
    @FastNative
    public native long returnLongFast();

    public native float returnFloat();
    @FastNative
    public native float returnFloatFast();

    public native double returnDouble();
    @FastNative
    public native double returnDoubleFast();

    public native Object returnNull();
    @FastNative
    public native Object returnNullFast();

    public native String returnString();
    @FastNative
    public native String returnStringFast();

    public native short[] returnShortArray();
    @FastNative
    public native short[] returnShortArrayFast();

    public native String[] returnStringArray();
    @FastNative
    public native String[] returnStringArrayFast();

    public native InstanceNonce returnThis();
    @FastNative
    public native InstanceNonce returnThisFast();

    public native boolean takeBoolean(boolean v);
    @FastNative
    public native boolean takeBooleanFast(boolean v);

    public native boolean takeByte(byte v);
    @FastNative
    public native boolean takeByteFast(byte v);

    public native boolean takeShort(short v);
    @FastNative
    public native boolean takeShortFast(short v);

    public native boolean takeChar(char v);
    @FastNative
    public native boolean takeCharFast(char v);

    public native boolean takeInt(int v);
    @FastNative
    public native boolean takeIntFast(int v);

    public native boolean takeLong(long v);
    @FastNative
    public native boolean takeLongFast(long v);

    public native boolean takeFloat(float v);
    @FastNative
    public native boolean takeFloatFast(float v);

    public native boolean takeDouble(double v);
    @FastNative
    public native boolean takeDoubleFast(double v);

    public native boolean takeNull(Object v);
    @FastNative
    public native boolean takeNullFast(Object v);

    public native boolean takeString(String v);
    @FastNative
    public native boolean takeStringFast(String v);

    public native boolean takeThis(InstanceNonce v);
    @FastNative
    public native boolean takeThisFast(InstanceNonce v);

    public native boolean takeIntLong(int v1, long v2);
    @FastNative
    public native boolean takeIntLongFast(int v1, long v2);

    public native boolean takeLongInt(long v1, int v2);
    @FastNative
    public native boolean takeLongIntFast(long v1, int v2);

    public native boolean takeOneOfEach(boolean v0, byte v1, short v2,
            char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);
    public native boolean takeOneOfEachDlsym(boolean v0, byte v1, short v2,
            char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);
    @FastNative
    public native boolean takeOneOfEachFast(boolean v0, byte v1, short v2,
            char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);
    @FastNative
    public native boolean takeOneOfEachFastDlsym(boolean v0, byte v1, short v2,
            char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);

    public native boolean takeCoolHandLuke(
            int v1, int v2, int v3, int v4,
            int v5, int v6, int v7, int v8, int v9,
            int v10, int v11, int v12, int v13, int v14,
            int v15, int v16, int v17, int v18, int v19,
            int v20, int v21, int v22, int v23, int v24,
            int v25, int v26, int v27, int v28, int v29,
            int v30, int v31, int v32, int v33, int v34,
            int v35, int v36, int v37, int v38, int v39,
            int v40, int v41, int v42, int v43, int v44,
            int v45, int v46, int v47, int v48, int v49,
            int v50);
    @FastNative
    public native boolean takeCoolHandLukeFast(
            int v1, int v2, int v3, int v4,
            int v5, int v6, int v7, int v8, int v9,
            int v10, int v11, int v12, int v13, int v14,
            int v15, int v16, int v17, int v18, int v19,
            int v20, int v21, int v22, int v23, int v24,
            int v25, int v26, int v27, int v28, int v29,
            int v30, int v31, int v32, int v33, int v34,
            int v35, int v36, int v37, int v38, int v39,
            int v40, int v41, int v42, int v43, int v44,
            int v45, int v46, int v47, int v48, int v49,
            int v50);
}
