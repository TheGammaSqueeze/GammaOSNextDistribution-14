/*
 * Copyright 2021 The Android Open Source Project
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

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>
#include <android/hardware_buffer_jni.h>

#include <jni.h>

namespace {

const char kClassName[] = "com/android/car/internal/evs/GLES20CarEvsBufferRenderer";

EGLImageKHR gKHRImage = EGL_NO_IMAGE_KHR;

jboolean nativeUpdateTexture(JNIEnv* env, jobject /*thiz*/, jobject hardwareBufferObj,
                             jint textureId) {
    EGLDisplay eglCurrentDisplay = eglGetCurrentDisplay();
    if (gKHRImage != EGL_NO_IMAGE_KHR) {
        // Release a previous EGL image
        eglDestroyImageKHR(eglCurrentDisplay, gKHRImage);
        gKHRImage = EGL_NO_IMAGE_KHR;
    }

    // Get a native hardware buffer
    AHardwareBuffer* nativeBuffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBufferObj);
    if (!nativeBuffer) {
        return JNI_FALSE;
    }

    // Create EGL image from a native hardware buffer
    EGLClientBuffer eglBuffer = eglGetNativeClientBufferANDROID(nativeBuffer);
    EGLint eglImageAttributes[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    gKHRImage = eglCreateImageKHR(eglCurrentDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
                                  eglBuffer, eglImageAttributes);
    if (gKHRImage == EGL_NO_IMAGE_KHR) {
        return JNI_FALSE;
    }

    // Update the texture handle we already created to refer to this buffer
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    // Map texture
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, static_cast<GLeglImageOES>(gKHRImage));

    // Initialize the sampling properties
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    return JNI_TRUE;
}

}  // namespace

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Registers native methods
    static const JNINativeMethod methods[] = {
            {"nUpdateTexture", "(Landroid/hardware/HardwareBuffer;I)Z",
             reinterpret_cast<void*>(nativeUpdateTexture)},
    };

    jclass clazz = env->FindClass(kClassName);
    if (!clazz) {
        return JNI_ERR;
    }

    int result = env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    if (result != 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }

    gKHRImage = EGL_NO_IMAGE_KHR;
}
