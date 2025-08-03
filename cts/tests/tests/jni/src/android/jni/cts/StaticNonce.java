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

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

/**
 * Class with a bunch of native static methods. These methods are called by
 * the various tests in {@link JniStaticTest}.
 */
public class StaticNonce {
    static {
        if (!JniTestCase.isCpuAbiNone()) {
            System.loadLibrary("jnitest");
        }
    }

    /**
     * Construct an instance.
     */
    public StaticNonce() {
        // This space intentionally left blank.
    }

    // See JniStaticTest for the expected behavior of these methods.

    public static native void missing();
    @FastNative
    public static native void missingFast();
    @CriticalNative
    public static native void missingCritical();

    public static native void nop();
    public static native void nopDlsym();
    @FastNative
    public static native void nopFast();
    @FastNative
    public static native void nopFastDlsym();
    @CriticalNative
    public static native void nopCritical();
    @CriticalNative
    public static native void nopCriticalDlsym();

    public static native boolean returnBoolean();
    @FastNative
    public static native boolean returnBooleanFast();
    @CriticalNative
    public static native boolean returnBooleanCritical();

    public static native byte returnByte();
    @FastNative
    public static native byte returnByteFast();
    @CriticalNative
    public static native byte returnByteCritical();

    public static native short returnShort();
    @FastNative
    public static native short returnShortFast();
    @CriticalNative
    public static native short returnShortCritical();

    public static native char returnChar();
    @FastNative
    public static native char returnCharFast();
    @CriticalNative
    public static native char returnCharCritical();

    public static native int returnInt();
    @FastNative
    public static native int returnIntFast();
    @CriticalNative
    public static native int returnIntCritical();

    public static native long returnLong();
    @FastNative
    public static native long returnLongFast();
    @CriticalNative
    public static native long returnLongCritical();

    public static native float returnFloat();
    @FastNative
    public static native float returnFloatFast();
    @CriticalNative
    public static native float returnFloatCritical();

    public static native double returnDouble();
    @FastNative
    public static native double returnDoubleFast();
    @CriticalNative
    public static native double returnDoubleCritical();

    public static native Object returnNull();
    @FastNative
    public static native Object returnNullFast();

    public static native String returnString();
    @FastNative
    public static native String returnStringFast();

    public static native short[] returnShortArray();
    @FastNative
    public static native short[] returnShortArrayFast();

    public static native String[] returnStringArray();
    @FastNative
    public static native String[] returnStringArrayFast();

    public static native Class returnThisClass();
    @FastNative
    public static native Class returnThisClassFast();

    public static native StaticNonce returnInstance();
    @FastNative
    public static native StaticNonce returnInstanceFast();

    public static native boolean takeBoolean(boolean v);
    @FastNative
    public static native boolean takeBooleanFast(boolean v);
    @CriticalNative
    public static native boolean takeBooleanCritical(boolean v);

    public static native boolean takeByte(byte v);
    @FastNative
    public static native boolean takeByteFast(byte v);
    @CriticalNative
    public static native boolean takeByteCritical(byte v);

    public static native boolean takeShort(short v);
    @FastNative
    public static native boolean takeShortFast(short v);
    @CriticalNative
    public static native boolean takeShortCritical(short v);

    public static native boolean takeChar(char v);
    @FastNative
    public static native boolean takeCharFast(char v);
    @CriticalNative
    public static native boolean takeCharCritical(char v);

    public static native boolean takeInt(int v);
    @FastNative
    public static native boolean takeIntFast(int v);
    @CriticalNative
    public static native boolean takeIntCritical(int v);

    public static native boolean takeLong(long v);
    @FastNative
    public static native boolean takeLongFast(long v);
    @CriticalNative
    public static native boolean takeLongCritical(long v);

    public static native boolean takeFloat(float v);
    @FastNative
    public static native boolean takeFloatFast(float v);
    @CriticalNative
    public static native boolean takeFloatCritical(float v);

    public static native boolean takeDouble(double v);
    @FastNative
    public static native boolean takeDoubleFast(double v);
    @CriticalNative
    public static native boolean takeDoubleCritical(double v);

    public static native boolean takeNull(Object v);
    @FastNative
    public static native boolean takeNullFast(Object v);

    public static native boolean takeString(String v);
    @FastNative
    public static native boolean takeStringFast(String v);

    public static native boolean takeThisClass(Class v);
    @FastNative
    public static native boolean takeThisClassFast(Class v);

    public static native boolean takeIntLong(int v1, long v2);
    @FastNative
    public static native boolean takeIntLongFast(int v1, long v2);
    @CriticalNative
    public static native boolean takeIntLongCritical(int v1, long v2);

    public static native boolean takeLongInt(long v1, int v2);
    @FastNative
    public static native boolean takeLongIntFast(long v1, int v2);
    @CriticalNative
    public static native boolean takeLongIntCritical(long v1, int v2);

    public static native boolean takeOneOfEach(boolean v0, byte v1, short v2,
            char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);
    public static native boolean takeOneOfEachDlsym(boolean v0, byte v1,
            short v2, char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);
    @FastNative
    public static native boolean takeOneOfEachFast(boolean v0, byte v1,
            short v2, char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);
    @FastNative
    public static native boolean takeOneOfEachFastDlsym(boolean v0, byte v1,
            short v2, char v3, int v4, long v5, String v6, float v7, double v8,
            int[] v9);
    @CriticalNative
    public static native boolean takeOneOfEachCritical(boolean v0, byte v1,
            short v2, char v3, int v4, long v5, float v6, double v7);
    @CriticalNative
    public static native boolean takeOneOfEachCriticalDlsym(boolean v0,
            byte v1, short v2, char v3, int v4, long v5, float v6, double v7);

    public static native boolean takeCoolHandLuke(
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
    public static native boolean takeCoolHandLukeFast(
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
    @CriticalNative
    public static native boolean takeCoolHandLukeCritical(
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

    @CriticalNative
    public static native boolean takeCoolHandLukeWithFloatsCritical(
            int v1, int v2, int v3, int v4, int v5,
            double v6, double v7, double v8, double v9, double v10,
            double v11, double v12, double v13, double v14);

}
