/*
 * Copyright 2020 The Android Open Source Project
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

#define LOG_TAG "AImageDecoderTest"

#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/imagedecoder.h>
#include <android/rect.h>

#include "NativeTestHelpers.h"

#include <cstdlib>
#include <cstring>
#include <initializer_list>

#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static void testNullDecoder(JNIEnv* env, jobject) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wnonnull"
    ASSERT_EQ(ANDROID_IMAGE_DECODER_BAD_PARAMETER, AImageDecoder_advanceFrame(nullptr));

    ASSERT_EQ(ANDROID_IMAGE_DECODER_BAD_PARAMETER, AImageDecoder_rewind(nullptr));

    AImageDecoder_setInternallyHandleDisposePrevious(nullptr, true);
    AImageDecoder_setInternallyHandleDisposePrevious(nullptr, false);
#pragma clang diagnostic pop
}

static void testToString(JNIEnv* env, jobject) {
    struct {
        int resultCode;
        const char* string;
    } map[] = {
        { ANDROID_IMAGE_DECODER_SUCCESS,            "ANDROID_IMAGE_DECODER_SUCCESS" },
        { ANDROID_IMAGE_DECODER_INCOMPLETE,         "ANDROID_IMAGE_DECODER_INCOMPLETE" },
        { ANDROID_IMAGE_DECODER_ERROR,              "ANDROID_IMAGE_DECODER_ERROR" },
        { ANDROID_IMAGE_DECODER_INVALID_CONVERSION, "ANDROID_IMAGE_DECODER_INVALID_CONVERSION" },
        { ANDROID_IMAGE_DECODER_INVALID_SCALE,      "ANDROID_IMAGE_DECODER_INVALID_SCALE" },
        { ANDROID_IMAGE_DECODER_BAD_PARAMETER,      "ANDROID_IMAGE_DECODER_BAD_PARAMETER" },
        { ANDROID_IMAGE_DECODER_INVALID_INPUT,      "ANDROID_IMAGE_DECODER_INVALID_INPUT" },
        { ANDROID_IMAGE_DECODER_SEEK_ERROR,         "ANDROID_IMAGE_DECODER_SEEK_ERROR" },
        { ANDROID_IMAGE_DECODER_INTERNAL_ERROR,     "ANDROID_IMAGE_DECODER_INTERNAL_ERROR" },
        { ANDROID_IMAGE_DECODER_UNSUPPORTED_FORMAT, "ANDROID_IMAGE_DECODER_UNSUPPORTED_FORMAT" },
        { ANDROID_IMAGE_DECODER_FINISHED,           "ANDROID_IMAGE_DECODER_FINISHED" },
        { ANDROID_IMAGE_DECODER_INVALID_STATE,      "ANDROID_IMAGE_DECODER_INVALID_STATE" },
    };

    for (const auto& item : map) {
        const char* str = AImageDecoder_resultToString(item.resultCode);
        ASSERT_EQ(0, strcmp(item.string, str));
    }

    for (int i : { ANDROID_IMAGE_DECODER_SUCCESS + 1,
                   ANDROID_IMAGE_DECODER_INVALID_STATE - 1,
                   2, 7, 37, 42 }) {
        ASSERT_EQ(nullptr, AImageDecoder_resultToString(i));
    }
}

static jlong openAsset(JNIEnv* env, jobject, jobject jAssets, jstring jFile) {
    AAssetManager* nativeManager = AAssetManager_fromJava(env, jAssets);
    const char* file = env->GetStringUTFChars(jFile, nullptr);
    AAsset* asset = AAssetManager_open(nativeManager, file, AASSET_MODE_UNKNOWN);
    if (!asset) {
        fail(env, "Could not open %s", file);
    } else {
        ALOGD("Testing %s", file);
    }
    env->ReleaseStringUTFChars(jFile, file);
    return reinterpret_cast<jlong>(asset);
}

static void closeAsset(JNIEnv*, jobject, jlong asset) {
    AAsset_close(reinterpret_cast<AAsset*>(asset));
}

static jlong createFromAsset(JNIEnv* env, jobject, jlong asset) {
    AImageDecoder* decoder = nullptr;
    int result = AImageDecoder_createFromAAsset(reinterpret_cast<AAsset*>(asset), &decoder);
    if (ANDROID_IMAGE_DECODER_SUCCESS != result || !decoder) {
        fail(env, "Failed to create AImageDecoder with %s!",
             AImageDecoder_resultToString(result));
    }
    return reinterpret_cast<jlong>(decoder);
}

static jint getWidth(JNIEnv*, jobject, jlong decoder) {
    const auto* info = AImageDecoder_getHeaderInfo(reinterpret_cast<AImageDecoder*>(decoder));
    return AImageDecoderHeaderInfo_getWidth(info);
}

static jint getHeight(JNIEnv*, jobject, jlong decoder) {
    const auto* info = AImageDecoder_getHeaderInfo(reinterpret_cast<AImageDecoder*>(decoder));
    return AImageDecoderHeaderInfo_getHeight(info);
}

static void deleteDecoder(JNIEnv*, jobject, jlong decoder) {
    AImageDecoder_delete(reinterpret_cast<AImageDecoder*>(decoder));
}

static jint setTargetSize(JNIEnv*, jobject, jlong decoder_ptr, jint width, jint height) {
    return AImageDecoder_setTargetSize(reinterpret_cast<AImageDecoder*>(decoder_ptr),
                                       width, height);
}

static jint setCrop(JNIEnv*, jobject, jlong decoder_ptr, jint left, jint top,
                    jint right, jint bottom) {
    return AImageDecoder_setCrop(reinterpret_cast<AImageDecoder*>(decoder_ptr),
                                 {left, top, right, bottom});
}

static void decode(JNIEnv* env, jobject, jlong decoder_ptr, jobject jBitmap, jint expected) {
    auto* decoder = reinterpret_cast<AImageDecoder*>(decoder_ptr);
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, jBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        fail(env, "Failed to getInfo on a Bitmap!");
        return;
    }

    void* pixels;
    if (AndroidBitmap_lockPixels(env, jBitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        fail(env, "Failed to lock pixels!");
        return;
    }

    const int result = AImageDecoder_decodeImage(decoder, pixels, info.stride,
                                                 info.stride * info.height);
    if (result != expected) {
        fail(env, "Unexpected result from AImageDecoder_decodeImage: %s",
             AImageDecoder_resultToString(result));
        // Don't return yet, so we can unlockPixels.
    }

    if (AndroidBitmap_unlockPixels(env, jBitmap) != ANDROID_BITMAP_RESULT_SUCCESS) {
        const char* msg = "Failed to unlock pixels!";
        if (env->ExceptionCheck()) {
            // Do not attempt to throw an Exception while one is pending.
            ALOGE("%s", msg);
        } else {
            fail(env, msg);
        }
    }
}

static jint advanceFrame(JNIEnv*, jobject, jlong decoder_ptr) {
    auto* decoder = reinterpret_cast<AImageDecoder*>(decoder_ptr);
    return AImageDecoder_advanceFrame(decoder);
}

static jint rewind_decoder(JNIEnv*, jobject, jlong decoder) {
    return AImageDecoder_rewind(reinterpret_cast<AImageDecoder*>(decoder));
}

static jint setUnpremultipliedRequired(JNIEnv*, jobject, jlong decoder, jboolean required) {
    return AImageDecoder_setUnpremultipliedRequired(reinterpret_cast<AImageDecoder*>(decoder),
                                                    required);
}

static jint setAndroidBitmapFormat(JNIEnv*, jobject, jlong decoder, jint format) {
    return AImageDecoder_setAndroidBitmapFormat(reinterpret_cast<AImageDecoder*>(decoder),
                                                format);
}

static jint setDataSpace(JNIEnv*, jobject, jlong decoder, jint dataSpace) {
    return AImageDecoder_setDataSpace(reinterpret_cast<AImageDecoder*>(decoder),
                                      dataSpace);
}

static jlong createFrameInfo(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(AImageDecoderFrameInfo_create());
}

static void deleteFrameInfo(JNIEnv*, jobject, jlong frameInfo) {
    AImageDecoderFrameInfo_delete(reinterpret_cast<AImageDecoderFrameInfo*>(frameInfo));
}

static jint getFrameInfo(JNIEnv*, jobject, jlong decoder, jlong frameInfo) {
    return AImageDecoder_getFrameInfo(reinterpret_cast<AImageDecoder*>(decoder),
                                      reinterpret_cast<AImageDecoderFrameInfo*>(frameInfo));
}

static void testNullFrameInfo(JNIEnv* env, jobject, jobject jAssets, jstring jFile) {
    AImageDecoderFrameInfo_delete(nullptr);

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wnonnull"
    {
        auto* frameInfo = AImageDecoderFrameInfo_create();
        ASSERT_EQ(ANDROID_IMAGE_DECODER_BAD_PARAMETER, AImageDecoder_getFrameInfo(nullptr,
                                                                                  frameInfo));
        AImageDecoderFrameInfo_delete(frameInfo);
    }
    {
        auto asset = openAsset(env, nullptr, jAssets, jFile);
        auto decoder = createFromAsset(env, nullptr, asset);
        AImageDecoderFrameInfo* info = nullptr;
        ASSERT_EQ(ANDROID_IMAGE_DECODER_BAD_PARAMETER, getFrameInfo(env, nullptr, decoder,
                reinterpret_cast<jlong>(info)));

        deleteDecoder(env, nullptr, decoder);
        closeAsset(env, nullptr, asset);
    }
    {
        ARect rect = AImageDecoderFrameInfo_getFrameRect(nullptr);
        ASSERT_EQ(0, rect.left);
        ASSERT_EQ(0, rect.top);
        ASSERT_EQ(0, rect.right);
        ASSERT_EQ(0, rect.bottom);
    }

    ASSERT_EQ(ANDROID_IMAGE_DECODER_BAD_PARAMETER, AImageDecoderFrameInfo_getDuration(nullptr));
    ASSERT_FALSE(AImageDecoderFrameInfo_hasAlphaWithinBounds(nullptr));
    ASSERT_EQ(ANDROID_IMAGE_DECODER_BAD_PARAMETER, AImageDecoderFrameInfo_getDisposeOp(nullptr));
    ASSERT_EQ(ANDROID_IMAGE_DECODER_BAD_PARAMETER, AImageDecoderFrameInfo_getBlendOp(nullptr));
#pragma clang diagnostic pop
}

static jlong getDuration(JNIEnv*, jobject, jlong frameInfo) {
    return AImageDecoderFrameInfo_getDuration(reinterpret_cast<AImageDecoderFrameInfo*>(frameInfo));
}

static void testGetFrameRect(JNIEnv* env, jobject, jlong jFrameInfo, jint expectedLeft,
                             jint expectedTop, jint expectedRight, jint expectedBottom) {
    auto* frameInfo = reinterpret_cast<AImageDecoderFrameInfo*>(jFrameInfo);
    ARect rect = AImageDecoderFrameInfo_getFrameRect(frameInfo);
    if (rect.left != expectedLeft || rect.top != expectedTop || rect.right != expectedRight
        || rect.bottom != expectedBottom) {
        fail(env, "Mismatched frame rect! Expected: %i %i %i %i Actual: %i %i %i %i", expectedLeft,
             expectedTop, expectedRight, expectedBottom, rect.left, rect.top, rect.right,
             rect.bottom);
    }
}

static jboolean getFrameAlpha(JNIEnv*, jobject, jlong frameInfo) {
    return AImageDecoderFrameInfo_hasAlphaWithinBounds(
            reinterpret_cast<AImageDecoderFrameInfo*>(frameInfo));
}

static jboolean getAlpha(JNIEnv*, jobject, jlong decoder) {
    const auto* info = AImageDecoder_getHeaderInfo(reinterpret_cast<AImageDecoder*>(decoder));
    return AImageDecoderHeaderInfo_getAlphaFlags(info) != ANDROID_BITMAP_FLAGS_ALPHA_OPAQUE;
}

static jint getDisposeOp(JNIEnv*, jobject, jlong frameInfo) {
    return AImageDecoderFrameInfo_getDisposeOp(
            reinterpret_cast<AImageDecoderFrameInfo*>(frameInfo));
}

static jint getBlendOp(JNIEnv*, jobject, jlong frameInfo) {
    return AImageDecoderFrameInfo_getBlendOp(
            reinterpret_cast<AImageDecoderFrameInfo*>(frameInfo));
}

static jint getRepeatCount(JNIEnv*, jobject, jlong decoder) {
    return AImageDecoder_getRepeatCount(reinterpret_cast<AImageDecoder*>(decoder));
}

static void setHandleDisposePrevious(JNIEnv*, jobject, jlong decoder, jboolean handle) {
    AImageDecoder_setInternallyHandleDisposePrevious(reinterpret_cast<AImageDecoder*>(decoder),
                                                     handle);
}

#define ASSET_MANAGER "Landroid/content/res/AssetManager;"
#define STRING "Ljava/lang/String;"
#define BITMAP "Landroid/graphics/Bitmap;"

static JNINativeMethod gMethods[] = {
    { "nTestNullDecoder", "()V", (void*) testNullDecoder },
    { "nTestToString", "()V", (void*) testToString },
    { "nOpenAsset", "(" ASSET_MANAGER STRING ")J", (void*) openAsset },
    { "nCloseAsset", "(J)V", (void*) closeAsset },
    { "nCreateFromAsset", "(J)J", (void*) createFromAsset },
    { "nGetWidth", "(J)I", (void*) getWidth },
    { "nGetHeight", "(J)I", (void*) getHeight },
    { "nDeleteDecoder", "(J)V", (void*) deleteDecoder },
    { "nSetTargetSize", "(JII)I", (void*) setTargetSize },
    { "nSetCrop", "(JIIII)I", (void*) setCrop },
    { "nDecode", "(J" BITMAP "I)V", (void*) decode },
    { "nAdvanceFrame", "(J)I", (void*) advanceFrame },
    { "nRewind", "(J)I", (void*) rewind_decoder },
    { "nSetUnpremultipliedRequired", "(JZ)I", (void*) setUnpremultipliedRequired },
    { "nSetAndroidBitmapFormat", "(JI)I", (void*) setAndroidBitmapFormat },
    { "nSetDataSpace", "(JI)I", (void*) setDataSpace },
    { "nCreateFrameInfo", "()J", (void*) createFrameInfo },
    { "nDeleteFrameInfo", "(J)V", (void*) deleteFrameInfo },
    { "nGetFrameInfo", "(JJ)I", (void*) getFrameInfo },
    { "nTestNullFrameInfo", "(" ASSET_MANAGER STRING ")V", (void*) testNullFrameInfo },
    { "nGetDuration", "(J)J", (void*) getDuration },
    { "nTestGetFrameRect", "(JIIII)V", (void*) testGetFrameRect },
    { "nGetFrameAlpha", "(J)Z", (void*) getFrameAlpha },
    { "nGetAlpha", "(J)Z", (void*) getAlpha },
    { "nGetDisposeOp", "(J)I", (void*) getDisposeOp },
    { "nGetBlendOp", "(J)I", (void*) getBlendOp },
    { "nGetRepeatCount", "(J)I", (void*) getRepeatCount },
    { "nSetHandleDisposePrevious", "(JZ)V", (void*) setHandleDisposePrevious },
};

int register_android_uirendering_cts_AImageDecoderTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/uirendering/cts/testclasses/AImageDecoderTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}

