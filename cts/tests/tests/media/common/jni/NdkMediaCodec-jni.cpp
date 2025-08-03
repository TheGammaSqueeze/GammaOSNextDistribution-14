/*
 * Copyright (C) 2022 The Android Open Source Project
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

/* Original code copied from NDK Native-media sample code */

//#define LOG_NDEBUG 0
#define LOG_TAG "NdkMediaCodec-jni"
#include <log/log.h>

#include <android/native_window_jni.h>
#include <assert.h>
#include <jni.h>
#include <string.h>
#include <unistd.h>


#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaCrypto.h"
#include "media/NdkMediaDataSource.h"
#include "media/NdkMediaFormat.h"
#include "media/NdkMediaMuxer.h"

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreateCodecByName(
        JNIEnv *env, jclass /*clazz*/, jstring name) {

    if (name == NULL) {
        return 0;
    }

    const char *tmp = env->GetStringUTFChars(name, NULL);
    if (tmp == NULL) {
        return 0;
    }

    AMediaCodec *codec = AMediaCodec_createCodecByName(tmp);
    if (codec == NULL) {
        env->ReleaseStringUTFChars(name, tmp);
        return 0;
    }

    env->ReleaseStringUTFChars(name, tmp);
    return reinterpret_cast<jlong>(codec);

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecDelete(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_delete(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecStart(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_start(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecStop(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_stop(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecConfigure(
        JNIEnv *env,
        jclass /*clazz*/,
        jlong codec,
        jstring mime,
        jint width,
        jint height,
        jint colorFormat,
        jint bitRate,
        jint frameRate,
        jint iFrameInterval,
        jobject csd0,
        jobject csd1,
        jint flags,
        jint lowLatency,
        jobject surface,
        jint range,
        jint standard,
        jint transfer) {

    AMediaFormat* format = AMediaFormat_new();
    if (format == NULL) {
        return false;
    }

    const char *tmp = env->GetStringUTFChars(mime, NULL);
    if (tmp == NULL) {
        AMediaFormat_delete(format);
        return false;
    }

    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, tmp);
    env->ReleaseStringUTFChars(mime, tmp);

    const char *keys[] = {
            AMEDIAFORMAT_KEY_WIDTH,
            AMEDIAFORMAT_KEY_HEIGHT,
            AMEDIAFORMAT_KEY_COLOR_FORMAT,
            AMEDIAFORMAT_KEY_BIT_RATE,
            AMEDIAFORMAT_KEY_FRAME_RATE,
            AMEDIAFORMAT_KEY_I_FRAME_INTERVAL,
            // need to specify the actual string, since this test needs
            // to run on API 29, where the symbol doesn't exist
            "low-latency", // AMEDIAFORMAT_KEY_LOW_LATENCY
            AMEDIAFORMAT_KEY_COLOR_RANGE,
            AMEDIAFORMAT_KEY_COLOR_STANDARD,
            AMEDIAFORMAT_KEY_COLOR_TRANSFER,
    };

    jint values[] = {width, height, colorFormat, bitRate, frameRate, iFrameInterval, lowLatency,
                     range, standard, transfer};
    for (size_t i = 0; i < sizeof(values) / sizeof(values[0]); i++) {
        if (values[i] >= 0) {
            AMediaFormat_setInt32(format, keys[i], values[i]);
        }
    }

    if (csd0 != NULL) {
        void *csd0Ptr = env->GetDirectBufferAddress(csd0);
        jlong csd0Size = env->GetDirectBufferCapacity(csd0);
        AMediaFormat_setBuffer(format, "csd-0", csd0Ptr, csd0Size);
    }

    if (csd1 != NULL) {
        void *csd1Ptr = env->GetDirectBufferAddress(csd1);
        jlong csd1Size = env->GetDirectBufferCapacity(csd1);
        AMediaFormat_setBuffer(format, "csd-1", csd1Ptr, csd1Size);
    }

    media_status_t err = AMediaCodec_configure(
            reinterpret_cast<AMediaCodec *>(codec),
            format,
            surface == NULL ? NULL : ANativeWindow_fromSurface(env, surface),
            NULL,
            flags);

    AMediaFormat_delete(format);
    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetInputSurface(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jobject surface) {

    media_status_t err = AMediaCodec_setInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            ANativeWindow_fromSurface(env, surface));

    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetNativeInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jlong nativeWindow) {

    media_status_t err = AMediaCodec_setInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            reinterpret_cast<ANativeWindow *>(nativeWindow));

    return err == AMEDIA_OK;

}

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreateInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec) {

    ANativeWindow *nativeWindow;
    media_status_t err = AMediaCodec_createInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            &nativeWindow);

     if (err == AMEDIA_OK) {
         return reinterpret_cast<jlong>(nativeWindow);
     }

     return 0;

}

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreatePersistentInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/) {

    ANativeWindow *nativeWindow;
    media_status_t err = AMediaCodec_createPersistentInputSurface(&nativeWindow);

     if (err == AMEDIA_OK) {
         return reinterpret_cast<jlong>(nativeWindow);
     }

     return 0;

}

extern "C" jstring Java_android_media_cts_NdkMediaCodec_AMediaCodecGetOutputFormatString(
        JNIEnv* env, jclass /*clazz*/, jlong codec) {

    AMediaFormat *format = AMediaCodec_getOutputFormat(reinterpret_cast<AMediaCodec *>(codec));
    const char *str = AMediaFormat_toString(format);
    jstring jstr = env->NewStringUTF(str);
    AMediaFormat_delete(format);
    return jstr;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSignalEndOfInputStream(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec) {

    media_status_t err = AMediaCodec_signalEndOfInputStream(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecReleaseOutputBuffer(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jint index, jboolean render) {

    media_status_t err = AMediaCodec_releaseOutputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            render);

    return err == AMEDIA_OK;

}

static jobject AMediaCodecGetBuffer(
        JNIEnv* env,
        jlong codec,
        jint index,
        uint8_t *(*getBuffer)(AMediaCodec*, size_t, size_t*)) {

    size_t bufsize;
    uint8_t *buf = getBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            &bufsize);

    return env->NewDirectByteBuffer(buf, bufsize);

}

extern "C" jobject Java_android_media_cts_NdkMediaCodec_AMediaCodecGetOutputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jint index) {

    return AMediaCodecGetBuffer(env, codec, index, AMediaCodec_getOutputBuffer);

}

extern "C" jlongArray Java_android_media_cts_NdkMediaCodec_AMediaCodecDequeueOutputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jlong timeoutUs) {

    AMediaCodecBufferInfo info;
    memset(&info, 0, sizeof(info));
    int status = AMediaCodec_dequeueOutputBuffer(
        reinterpret_cast<AMediaCodec *>(codec),
        &info,
        timeoutUs);

    jlong ret[5] = {0};
    ret[0] = status;
    ret[1] = 0; // NdkMediaCodec calls ABuffer::data, which already adds offset
    ret[2] = info.size;
    ret[3] = info.presentationTimeUs;
    ret[4] = info.flags;

    jlongArray jret = env->NewLongArray(5);
    env->SetLongArrayRegion(jret, 0, 5, ret);
    return jret;

}

extern "C" jobject Java_android_media_cts_NdkMediaCodec_AMediaCodecGetInputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jint index) {

    return AMediaCodecGetBuffer(env, codec, index, AMediaCodec_getInputBuffer);

}

extern "C" jint Java_android_media_cts_NdkMediaCodec_AMediaCodecDequeueInputBuffer(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jlong timeoutUs) {

    return AMediaCodec_dequeueInputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            timeoutUs);

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecQueueInputBuffer(
        JNIEnv* /*env*/,
        jclass /*clazz*/,
        jlong codec,
        jint index,
        jint offset,
        jint size,
        jlong presentationTimeUs,
        jint flags) {

    media_status_t err = AMediaCodec_queueInputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            offset,
            size,
            presentationTimeUs,
            flags);

    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetParameter(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jstring jkey, jint value) {

    AMediaFormat* params = AMediaFormat_new();
    if (params == NULL) {
        return false;
    }

    const char *key = env->GetStringUTFChars(jkey, NULL);
    if (key == NULL) {
        AMediaFormat_delete(params);
        return false;
    }

    AMediaFormat_setInt32(params, key, value);
    media_status_t err = AMediaCodec_setParameters(
            reinterpret_cast<AMediaCodec *>(codec),
            params);
    env->ReleaseStringUTFChars(jkey, key);
    AMediaFormat_delete(params);
    return err == AMEDIA_OK;

}