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

#define LOG_TAG "NdkInputSurfaceJni"
#include <EGL/egl.h>
#include <EGL/eglext.h>

#include <android/native_window_jni.h>
#include <assert.h>
#include <jni.h>
#include <log/log.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglGetDisplay(JNIEnv * /*env*/, jclass /*clazz*/) {

    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        return 0;
    }

    EGLint major, minor;
    if (!eglInitialize(eglDisplay, &major, &minor)) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglDisplay);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglChooseConfig(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay) {

    // Configure EGL for recordable and OpenGL ES 2.0.  We want enough RGB bits
    // to minimize artifacts from possible YUV conversion.
    EGLint attribList[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL_NONE
    };

    EGLConfig configs[1];
    EGLint numConfigs[1];
    if (!eglChooseConfig(reinterpret_cast<EGLDisplay>(eglDisplay), attribList, configs, 1, numConfigs)) {
        return 0;
    }
    return reinterpret_cast<jlong>(configs[0]);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglCreateContext(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglConfig) {

    // Configure context for OpenGL ES 2.0.
    int attrib_list[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };

    EGLConfig eglContext = eglCreateContext(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLConfig>(eglConfig),
            EGL_NO_CONTEXT,
            attrib_list);

    if (eglGetError() != EGL_SUCCESS) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglContext);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_createEGLSurface(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglConfig, jlong nativeWindow) {

    int surfaceAttribs[] = {EGL_NONE};
    EGLSurface eglSurface = eglCreateWindowSurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLConfig>(eglConfig),
            reinterpret_cast<EGLNativeWindowType>(nativeWindow),
            surfaceAttribs);

    if (eglGetError() != EGL_SUCCESS) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglSurface);

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglMakeCurrent(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong eglContext) {

    return eglMakeCurrent(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLContext>(eglContext));

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglSwapBuffers(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    return eglSwapBuffers(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface));

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglPresentationTimeANDROID(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong nsecs) {

    return eglPresentationTimeANDROID(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLnsecsANDROID>(nsecs));

}

extern "C" jint Java_android_media_cts_NdkInputSurface_eglGetWidth(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    EGLint width;
    eglQuerySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            EGL_WIDTH,
            &width);

    return width;

}

extern "C" jint Java_android_media_cts_NdkInputSurface_eglGetHeight(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    EGLint height;
    eglQuerySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            EGL_HEIGHT,
            &height);

    return height;

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglDestroySurface(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    return eglDestroySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface));

}

extern "C" void Java_android_media_cts_NdkInputSurface_nativeRelease(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong eglContext, jlong nativeWindow) {

    if (eglDisplay != 0) {

        EGLDisplay _eglDisplay = reinterpret_cast<EGLDisplay>(eglDisplay);
        EGLSurface _eglSurface = reinterpret_cast<EGLSurface>(eglSurface);
        EGLContext _eglContext = reinterpret_cast<EGLContext>(eglContext);

        eglDestroySurface(_eglDisplay, _eglSurface);
        eglDestroyContext(_eglDisplay, _eglContext);
        eglReleaseThread();
        eglTerminate(_eglDisplay);

    }

    ANativeWindow_release(reinterpret_cast<ANativeWindow *>(nativeWindow));

}