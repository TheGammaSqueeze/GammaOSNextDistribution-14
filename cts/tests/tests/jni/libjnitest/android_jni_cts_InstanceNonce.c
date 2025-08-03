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

/*
 * Native implementation for the InstanceNonce class. See the test code
 * in JniInstanceTest for more info.
 */

#include <jni.h>

#include <stdbool.h>
#include <string.h>

#include "helper.h"

// public native void nop();
static void InstanceNonce_nop(JNIEnv *env, jobject this) {
    // This space intentionally left blank.
}

// public native void nopDlsym();
JNIEXPORT void Java_android_jni_cts_InstanceNonce_nopDlsym(JNIEnv *env,
        jobject this) {
    // This space intentionally left blank.
}

// @FastNative
// public native void nopFast();
static void InstanceNonce_nopFast(JNIEnv *env, jobject this) {
    // This space intentionally left blank.
}

// @FastNative
// public native void nopFastDlsym();
JNIEXPORT void Java_android_jni_cts_InstanceNonce_nopFastDlsym(JNIEnv *env,
        jobject this) {
    // This space intentionally left blank.
}

// public native boolean returnBoolean();
static jboolean InstanceNonce_returnBoolean(JNIEnv *env, jobject this) {
    return (jboolean) false;
}

// @FastNative
// public native boolean returnBooleanFast();
static jboolean InstanceNonce_returnBooleanFast(JNIEnv *env, jobject this) {
    return (jboolean) false;
}

// public native byte returnByte();
static jbyte InstanceNonce_returnByte(JNIEnv *env, jobject this) {
    return (jbyte) 123;
}

// @FastNative
// public native byte returnByteFast();
static jbyte InstanceNonce_returnByteFast(JNIEnv *env, jobject this) {
    return (jbyte) 123;
}

// public native short returnShort();
static jshort InstanceNonce_returnShort(JNIEnv *env, jobject this) {
    return (jshort) -12345;
}

// @FastNative
// public native short returnShortFast();
static jshort InstanceNonce_returnShortFast(JNIEnv *env, jobject this) {
    return (jshort) -12345;
}

// public native char returnChar();
static jchar InstanceNonce_returnChar(JNIEnv *env, jobject this) {
    return (jchar) 34567;
}

// @FastNative
// public native char returnCharFast();
static jchar InstanceNonce_returnCharFast(JNIEnv *env, jobject this) {
    return (jchar) 34567;
}

// public native int returnInt();
static jint InstanceNonce_returnInt(JNIEnv *env, jobject this) {
    return 12345678;
}

// @FastNative
// public native int returnIntFast();
static jint InstanceNonce_returnIntFast(JNIEnv *env, jobject this) {
    return 12345678;
}

// public native long returnLong();
static jlong InstanceNonce_returnLong(JNIEnv *env, jobject this) {
    return (jlong) -1098765432109876543LL;
}

// @FastNative
// public native long returnLongFast();
static jlong InstanceNonce_returnLongFast(JNIEnv *env, jobject this) {
    return (jlong) -1098765432109876543LL;
}

// public native float returnFloat();
static jfloat InstanceNonce_returnFloat(JNIEnv *env, jobject this) {
    return (jfloat) -98765.4321F;
}

// @FastNative
// public native float returnFloatFast();
static jfloat InstanceNonce_returnFloatFast(JNIEnv *env, jobject this) {
    return (jfloat) -98765.4321F;
}

// public native double returnDouble();
static jdouble InstanceNonce_returnDouble(JNIEnv *env, jobject this) {
    return 12345678.9;
}

// @FastNative
// public native double returnDoubleFast();
static jdouble InstanceNonce_returnDoubleFast(JNIEnv *env, jobject this) {
    return 12345678.9;
}

// public native Object returnNull();
static jobject InstanceNonce_returnNull(JNIEnv *env, jobject this) {
    return NULL;
}

// @FastNative
// public native Object returnNullFast();
static jobject InstanceNonce_returnNullFast(JNIEnv *env, jobject this) {
    return NULL;
}

// public native String returnString();
static jstring InstanceNonce_returnString(JNIEnv *env, jobject this) {
    return (*env)->NewStringUTF(env, "blort");
}

// @FastNative
// public native String returnStringFast();
static jstring InstanceNonce_returnStringFast(JNIEnv *env, jobject this) {
    return (*env)->NewStringUTF(env, "blort");
}

// public native short[] returnShortArray();
static jshortArray InstanceNonce_returnShortArray(JNIEnv *env, jobject this) {
    static jshort contents[] = { 10, 20, 30 };

    jshortArray result = (*env)->NewShortArray(env, 3);

    if (result == NULL) {
        return NULL;
    }

    (*env)->SetShortArrayRegion(env, result, 0, 3, contents);
    return result;
}

// @FastNative
// public native short[] returnShortArrayFast();
static jshortArray InstanceNonce_returnShortArrayFast(JNIEnv *env,
        jobject this) {
    return InstanceNonce_returnShortArray(env, this);
}

// public String[] returnStringArray();
static jobjectArray InstanceNonce_returnStringArray(JNIEnv *env,
        jobject this) {
    static int indices[] = { 0, 50, 99 };
    static const char *contents[] = { "blort", "zorch", "fizmo" };

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");

    if ((*env)->ExceptionOccurred(env) != NULL) {
        return NULL;
    }

    if (stringClass == NULL) {
        throwException(env, "java/lang/AssertionError", "class String not found");
        return NULL;
    }

    jobjectArray result = (*env)->NewObjectArray(env, 100, stringClass, NULL);

    if (result == NULL) {
        return NULL;
    }

    jsize i;
    for (i = 0; i < 3; i++) {
        jstring s = (*env)->NewStringUTF(env, contents[i]);

        if (s == NULL) {
            return NULL;
        }

        (*env)->SetObjectArrayElement(env, result, indices[i], s);

        if ((*env)->ExceptionOccurred(env) != NULL) {
            return NULL;
        }
    }

    return result;
}

// @FastNative
// public String[] returnStringArrayFast();
static jobjectArray InstanceNonce_returnStringArrayFast(JNIEnv *env,
        jobject this) {
    return InstanceNonce_returnStringArray(env, this);
}

// public native Class returnThisClass();
static jobject InstanceNonce_returnThis(JNIEnv *env, jobject this) {
    return this;
}

// @FastNative
// public native Class returnThisClassFast();
static jobject InstanceNonce_returnThisFast(JNIEnv *env, jobject this) {
    return this;
}

// public native boolean takeBoolean(boolean v);
static jboolean InstanceNonce_takeBoolean(JNIEnv *env, jobject this,
        jboolean v) {
    return v == false;
}

// @FastNative
// public native boolean takeBooleanFast(boolean v);
static jboolean InstanceNonce_takeBooleanFast(JNIEnv *env, jobject this,
        jboolean v) {
    return v == false;
}

// public native boolean takeByte(byte v);
static jboolean InstanceNonce_takeByte(JNIEnv *env, jobject this, jbyte v) {
    return v == -99;
}

// @FastNative
// public native boolean takeByteFast(byte v);
static jboolean InstanceNonce_takeByteFast(JNIEnv *env, jobject this,
        jbyte v) {
    return v == -99;
}

// public native boolean takeShort(short v);
static jboolean InstanceNonce_takeShort(JNIEnv *env, jobject this, jshort v) {
    return v == 19991;
}

// @FastNative
// public native boolean takeShortFast(short v);
static jboolean InstanceNonce_takeShortFast(JNIEnv *env, jobject this,
        jshort v) {
    return v == 19991;
}

// public native boolean takeChar(char v);
static jboolean InstanceNonce_takeChar(JNIEnv *env, jobject this, jchar v) {
    return v == 999;
}

// @FastNative
// public native boolean takeCharFast(char v);
static jboolean InstanceNonce_takeCharFast(JNIEnv *env, jobject this,
        jchar v) {
    return v == 999;
}

// public native boolean takeInt(int v);
static jboolean InstanceNonce_takeInt(JNIEnv *env, jobject this, jint v) {
    return v == -999888777;
}

// @FastNative
// public native boolean takeIntFast(int v);
static jboolean InstanceNonce_takeIntFast(JNIEnv *env, jobject this, jint v) {
    return v == -999888777;
}

// public native boolean takeLong(long v);
static jboolean InstanceNonce_takeLong(JNIEnv *env, jobject this, jlong v) {
    return v == 999888777666555444LL;
}

// @FastNative
// public native boolean takeLongFast(long v);
static jboolean InstanceNonce_takeLongFast(JNIEnv *env, jobject this,
        jlong v) {
    return v == 999888777666555444LL;
}

// public native boolean takeFloat(float v);
static jboolean InstanceNonce_takeFloat(JNIEnv *env, jobject this, jfloat v) {
    return v == -9988.7766F;
}

// @FastNative
// public native boolean takeFloatFast(float v);
static jboolean InstanceNonce_takeFloatFast(JNIEnv *env, jobject this,
        jfloat v) {
    return v == -9988.7766F;
}

// public native boolean takeDouble(double v);
static jboolean InstanceNonce_takeDouble(JNIEnv *env, jobject this,
        jdouble v) {
    return v == 999888777.666555;
}

// @FastNative
// public native boolean takeDoubleFast(double v);
static jboolean InstanceNonce_takeDoubleFast(JNIEnv *env, jobject this,
        jdouble v) {
    return v == 999888777.666555;
}

// public native boolean takeNull(Object v);
static jboolean InstanceNonce_takeNull(JNIEnv *env, jobject this, jobject v) {
    return v == NULL;
}

// @FastNative
// public native boolean takeNullFast(Object v);
static jboolean InstanceNonce_takeNullFast(JNIEnv *env, jobject this,
        jobject v) {
    return v == NULL;
}

// public native boolean takeString(String v);
static jboolean InstanceNonce_takeString(JNIEnv *env, jobject this,
        jstring v) {
    if (v == NULL) {
        return false;
    }
    
    const char *utf = (*env)->GetStringUTFChars(env, v, NULL);
    jboolean result = (strcmp("fuzzbot", utf) == 0);

    (*env)->ReleaseStringUTFChars(env, v, utf);
    return result;
}

// @FastNative
// public native boolean takeStringFast(String v);
static jboolean InstanceNonce_takeStringFast(JNIEnv *env, jobject this,
        jstring v) {
    return InstanceNonce_takeString(env, this, v);
}

// public native boolean takeThis(InstanceNonce v);
static jboolean InstanceNonce_takeThis(JNIEnv *env, jobject this, jobject v) {
    return (*env)->IsSameObject(env, this, v);
}

// @FastNative
// public native boolean takeThisFast(InstanceNonce v);
static jboolean InstanceNonce_takeThisFast(JNIEnv *env, jobject this,
        jobject v) {
    return (*env)->IsSameObject(env, this, v);
}

// public native boolean takeIntLong(int v1, long v2);
static jboolean InstanceNonce_takeIntLong(JNIEnv *env, jobject this,
        jint v1, jlong v2) {
    return (v1 == 914) && (v2 == 9140914091409140914LL);
}

// @FastNative
// public native boolean takeIntLongFast(int v1, long v2);
static jboolean InstanceNonce_takeIntLongFast(JNIEnv *env, jobject this,
        jint v1, jlong v2) {
    return (v1 == 914) && (v2 == 9140914091409140914LL);
}

// public native boolean takeLongInt(long v1, int v2);
static jboolean InstanceNonce_takeLongInt(JNIEnv *env, jobject this,
        jlong v1, jint v2) {
    return (v1 == -4321LL) && (v2 == 12341234);
}

// @FastNative
// public native boolean takeLongIntFast(long v1, int v2);
static jboolean InstanceNonce_takeLongIntFast(JNIEnv *env, jobject this,
        jlong v1, jint v2) {
    return (v1 == -4321LL) && (v2 == 12341234);
}

// public native boolean takeOneOfEach(boolean v0, byte v1, short v2,
//         char v3, int v4, long v5, String v6, float v7, double v8,
//         int[] v9);
static jboolean InstanceNonce_takeOneOfEach(JNIEnv *env, jobject this,
        jboolean v0, jbyte v1, jshort v2, jchar v3, jint v4, jlong v5,
        jstring v6, jfloat v7, jdouble v8, jintArray v9) {
    jsize length;
    jboolean result;
    
    if ((v0 != false) || (v1 != 1) || (v2 != 2) || (v3 != 3) ||
            (v4 != 4) || (v5 != 5) || (v7 != 7.0f) || (v8 != 8.0)) {
        return false;
    }

    length = (*env)->GetStringUTFLength(env, v6);

    if (length != 3) {
        throwException(env, "java/lang/AssertionError", "bad string length");
        return false;
    }

    const char *utf = (*env)->GetStringUTFChars(env, v6, NULL);
    result = (strncmp("six", utf, 3) == 0);

    (*env)->ReleaseStringUTFChars(env, v6, utf);

    if (! result) {
        return false;
    }

    length = (*env)->GetArrayLength(env, v9);
    if (length != 2) {
        throwException(env, "java/lang/AssertionError", "bad array length");
        return false;
    }

    jint *elements = (*env)->GetIntArrayElements(env, v9, NULL);
    result = (elements[0] == 9) && (elements[1] == 10);
    (*env)->ReleaseIntArrayElements(env, v9, elements, JNI_ABORT);

    return result;
}

// public native boolean takeOneOfEachDlsym(boolean v0, byte v1, short v2,
//         char v3, int v4, long v5, String v6, float v7, double v8,
//         int[] v9);
JNIEXPORT jboolean Java_android_jni_cts_InstanceNonce_takeOneOfEachDlsym(
        JNIEnv *env, jobject this, jboolean v0, jbyte v1, jshort v2, jchar v3,
        jint v4, jlong v5, jstring v6, jfloat v7, jdouble v8, jintArray v9) {
    return InstanceNonce_takeOneOfEach(
            env, this, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9);
}

// @FastNative
// public native boolean takeOneOfEachFast(boolean v0, byte v1, short v2,
//         char v3, int v4, long v5, String v6, float v7, double v8,
//         int[] v9);
static jboolean InstanceNonce_takeOneOfEachFast(JNIEnv *env, jobject this,
        jboolean v0, jbyte v1, jshort v2, jchar v3, jint v4, jlong v5,
        jstring v6, jfloat v7, jdouble v8, jintArray v9) {
    return InstanceNonce_takeOneOfEach(
            env, this, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9);
}

// @FastNative
// public native boolean takeOneOfEachFastDlsym(boolean v0, byte v1, short v2,
//         char v3, int v4, long v5, String v6, float v7, double v8,
//         int[] v9);
JNIEXPORT jboolean Java_android_jni_cts_InstanceNonce_takeOneOfEachFastDlsym(
        JNIEnv *env, jobject this, jboolean v0, jbyte v1, jshort v2, jchar v3,
        jint v4, jlong v5, jstring v6, jfloat v7, jdouble v8, jintArray v9) {
    return InstanceNonce_takeOneOfEach(
            env, this, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9);
}

// public native boolean takeCoolHandLuke(
//         int v1, int v2, int v3, int v4,
//         int v5, int v6, int v7, int v8, int v9,
//         int v10, int v11, int v12, int v13, int v14,
//         int v15, int v16, int v17, int v18, int v19,
//         int v20, int v21, int v22, int v23, int v24,
//         int v25, int v26, int v27, int v28, int v29,
//         int v30, int v31, int v32, int v33, int v34,
//         int v35, int v36, int v37, int v38, int v39,
//         int v40, int v41, int v42, int v43, int v44,
//         int v45, int v46, int v47, int v48, int v49,
//         int v50);
static jboolean InstanceNonce_takeCoolHandLuke(JNIEnv *env, jobject this,
        jint v1, jint v2, jint v3, jint v4,
        jint v5, jint v6, jint v7, jint v8, jint v9,
        jint v10, jint v11, jint v12, jint v13, jint v14,
        jint v15, jint v16, jint v17, jint v18, jint v19,
        jint v20, jint v21, jint v22, jint v23, jint v24,
        jint v25, jint v26, jint v27, jint v28, jint v29,
        jint v30, jint v31, jint v32, jint v33, jint v34,
        jint v35, jint v36, jint v37, jint v38, jint v39,
        jint v40, jint v41, jint v42, jint v43, jint v44,
        jint v45, jint v46, jint v47, jint v48, jint v49,
        jint v50) {
    return (v1 == 1) && (v2 == 2) && (v3 == 3) &&
        (v4 == 4) && (v5 == 5) && (v6 == 6) && (v7 == 7) &&
        (v8 == 8) && (v9 == 9) &&
        (v10 == 10) && (v11 == 11) && (v12 == 12) && (v13 == 13) &&
        (v14 == 14) && (v15 == 15) && (v16 == 16) && (v17 == 17) &&
        (v18 == 18) && (v19 == 19) &&
        (v20 == 20) && (v21 == 21) && (v22 == 22) && (v23 == 23) &&
        (v24 == 24) && (v25 == 25) && (v26 == 26) && (v27 == 27) &&
        (v28 == 28) && (v29 == 29) &&
        (v30 == 30) && (v31 == 31) && (v32 == 32) && (v33 == 33) &&
        (v34 == 34) && (v35 == 35) && (v36 == 36) && (v37 == 37) &&
        (v38 == 38) && (v39 == 39) &&
        (v40 == 40) && (v41 == 41) && (v42 == 42) && (v43 == 43) &&
        (v44 == 44) && (v45 == 45) && (v46 == 46) && (v47 == 47) &&
        (v48 == 48) && (v49 == 49) &&
        (v50 == 50);
}

// @FastNative
// public native boolean takeCoolHandLukeFast(
//         int v1, int v2, int v3, int v4,
//         int v5, int v6, int v7, int v8, int v9,
//         int v10, int v11, int v12, int v13, int v14,
//         int v15, int v16, int v17, int v18, int v19,
//         int v20, int v21, int v22, int v23, int v24,
//         int v25, int v26, int v27, int v28, int v29,
//         int v30, int v31, int v32, int v33, int v34,
//         int v35, int v36, int v37, int v38, int v39,
//         int v40, int v41, int v42, int v43, int v44,
//         int v45, int v46, int v47, int v48, int v49,
//         int v50);
static jboolean InstanceNonce_takeCoolHandLukeFast(JNIEnv *env, jobject this,
        jint v1, jint v2, jint v3, jint v4,
        jint v5, jint v6, jint v7, jint v8, jint v9,
        jint v10, jint v11, jint v12, jint v13, jint v14,
        jint v15, jint v16, jint v17, jint v18, jint v19,
        jint v20, jint v21, jint v22, jint v23, jint v24,
        jint v25, jint v26, jint v27, jint v28, jint v29,
        jint v30, jint v31, jint v32, jint v33, jint v34,
        jint v35, jint v36, jint v37, jint v38, jint v39,
        jint v40, jint v41, jint v42, jint v43, jint v44,
        jint v45, jint v46, jint v47, jint v48, jint v49,
        jint v50) {
    return InstanceNonce_takeCoolHandLuke(
            env, this, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10,
            v11, v12, v13, v14, v15, v16, v17, v18, v19, v20,
            v21, v22, v23, v24, v25, v26, v27, v28, v29, v30,
            v31, v32, v33, v34, v35, v36, v37, v38, v39, v40,
            v41, v42, v43, v44, v45, v46, v47, v48, v49, v50);
}

static JNINativeMethod methods[] = {
    // name, signature, function
    { "nop",               "()V", InstanceNonce_nop },
    { "nopFast",           "()V", InstanceNonce_nopFast },
    { "returnBoolean",     "()Z", InstanceNonce_returnBoolean },
    { "returnBooleanFast", "()Z", InstanceNonce_returnBooleanFast },
    { "returnByte",        "()B", InstanceNonce_returnByte },
    { "returnByteFast",    "()B", InstanceNonce_returnByteFast },
    { "returnShort",       "()S", InstanceNonce_returnShort },
    { "returnShortFast",   "()S", InstanceNonce_returnShortFast },
    { "returnChar",        "()C", InstanceNonce_returnChar },
    { "returnCharFast",    "()C", InstanceNonce_returnCharFast },
    { "returnInt",         "()I", InstanceNonce_returnInt },
    { "returnIntFast",     "()I", InstanceNonce_returnIntFast },
    { "returnLong",        "()J", InstanceNonce_returnLong },
    { "returnLongFast",    "()J", InstanceNonce_returnLongFast },
    { "returnFloat",       "()F", InstanceNonce_returnFloat },
    { "returnFloatFast",   "()F", InstanceNonce_returnFloatFast },
    { "returnDouble",      "()D", InstanceNonce_returnDouble },
    { "returnDoubleFast",  "()D", InstanceNonce_returnDoubleFast },
    { "returnNull",        "()Ljava/lang/Object;", InstanceNonce_returnNull },
    { "returnNullFast",    "()Ljava/lang/Object;",
      InstanceNonce_returnNullFast },
    { "returnString",      "()Ljava/lang/String;",
      InstanceNonce_returnString },
    { "returnStringFast",  "()Ljava/lang/String;",
      InstanceNonce_returnStringFast },
    { "returnShortArray",  "()[S", InstanceNonce_returnShortArray },
    { "returnShortArrayFast", "()[S", InstanceNonce_returnShortArrayFast },
    { "returnStringArray", "()[Ljava/lang/String;",
      InstanceNonce_returnStringArray },
    { "returnStringArrayFast", "()[Ljava/lang/String;",
      InstanceNonce_returnStringArrayFast },
    { "returnThis",        "()Landroid/jni/cts/InstanceNonce;",
      InstanceNonce_returnThis },
    { "returnThisFast",    "()Landroid/jni/cts/InstanceNonce;",
      InstanceNonce_returnThisFast },
    { "takeBoolean",       "(Z)Z", InstanceNonce_takeBoolean },
    { "takeBooleanFast",   "(Z)Z", InstanceNonce_takeBooleanFast },
    { "takeByte",          "(B)Z", InstanceNonce_takeByte },
    { "takeByteFast",      "(B)Z", InstanceNonce_takeByteFast },
    { "takeShort",         "(S)Z", InstanceNonce_takeShort },
    { "takeShortFast",     "(S)Z", InstanceNonce_takeShortFast },
    { "takeChar",          "(C)Z", InstanceNonce_takeChar },
    { "takeCharFast",      "(C)Z", InstanceNonce_takeCharFast },
    { "takeInt",           "(I)Z", InstanceNonce_takeInt },
    { "takeIntFast",       "(I)Z", InstanceNonce_takeIntFast },
    { "takeLong",          "(J)Z", InstanceNonce_takeLong },
    { "takeLongFast",      "(J)Z", InstanceNonce_takeLongFast },
    { "takeFloat",         "(F)Z", InstanceNonce_takeFloat },
    { "takeFloatFast",     "(F)Z", InstanceNonce_takeFloatFast },
    { "takeDouble",        "(D)Z", InstanceNonce_takeDouble },
    { "takeDoubleFast",    "(D)Z", InstanceNonce_takeDoubleFast },
    { "takeNull",          "(Ljava/lang/Object;)Z", InstanceNonce_takeNull },
    { "takeNullFast",      "(Ljava/lang/Object;)Z",
      InstanceNonce_takeNullFast },
    { "takeString",        "(Ljava/lang/String;)Z", InstanceNonce_takeString },
    { "takeStringFast",    "(Ljava/lang/String;)Z",
      InstanceNonce_takeStringFast },
    { "takeThis",          "(Landroid/jni/cts/InstanceNonce;)Z",
      InstanceNonce_takeThis },
    { "takeThisFast",      "(Landroid/jni/cts/InstanceNonce;)Z",
      InstanceNonce_takeThisFast },
    { "takeIntLong",       "(IJ)Z", InstanceNonce_takeIntLong },
    { "takeIntLongFast",   "(IJ)Z", InstanceNonce_takeIntLongFast },
    { "takeLongInt",       "(JI)Z", InstanceNonce_takeLongInt },
    { "takeLongIntFast",   "(JI)Z", InstanceNonce_takeLongIntFast },
    { "takeOneOfEach",     "(ZBSCIJLjava/lang/String;FD[I)Z",
      InstanceNonce_takeOneOfEach },
    { "takeOneOfEachFast", "(ZBSCIJLjava/lang/String;FD[I)Z",
      InstanceNonce_takeOneOfEachFast },
    { "takeCoolHandLuke",
      "(IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII)Z",
      InstanceNonce_takeCoolHandLuke },
    { "takeCoolHandLukeFast",
      "(IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII)Z",
      InstanceNonce_takeCoolHandLukeFast },
};

int register_InstanceNonce(JNIEnv *env) {
    return registerJniMethods(
            env, "android/jni/cts/InstanceNonce",
            methods, sizeof(methods) / sizeof(JNINativeMethod));
}
