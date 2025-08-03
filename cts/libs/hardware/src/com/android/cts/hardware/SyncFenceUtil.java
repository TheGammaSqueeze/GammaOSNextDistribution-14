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

package com.android.cts.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.hardware.SyncFence;
import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.EGLSync;
import android.opengl.GLES20;

public class SyncFenceUtil {
    public static SyncFence createUselessFence() {
        EGLDisplay eglDisplay = EGL15.EGL_NO_DISPLAY;
        EGLConfig eglConfig = null;
        EGLSurface eglPbuffer = EGL15.EGL_NO_SURFACE;
        EGLContext eglContext = EGL15.EGL_NO_CONTEXT;
        int error;

        eglDisplay = EGL15.eglGetPlatformDisplay(EGL15.EGL_PLATFORM_ANDROID_KHR,
            EGL14.EGL_DEFAULT_DISPLAY,
            new long[] {
                EGL14.EGL_NONE },
            0);
        if (eglDisplay == EGL15.EGL_NO_DISPLAY) {
            throw new RuntimeException("no EGL display");
        }
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("eglGetPlatformDisplay failed");
        }

        int[] major = new int[1];
        int[] minor = new int[1];
        if (!EGL14.eglInitialize(eglDisplay, major, 0, minor, 0)) {
            throw new RuntimeException("error in eglInitialize");
        }
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("eglInitialize failed");
        }

        // If we could rely on having EGL_KHR_surfaceless_context and EGL_KHR_context_no_config, we
        // wouldn't have to create a config or pbuffer at all.

        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(eglDisplay,
                new int[] {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE},
                0, configs, 0, 1, numConfigs, 0)) {
            throw new RuntimeException("eglChooseConfig failed");
        }
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("eglChooseConfig failed");
        }

        eglConfig = configs[0];

        eglPbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig,
              new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        if (eglPbuffer == EGL15.EGL_NO_SURFACE) {
            throw new RuntimeException("eglCreatePbufferSurface failed");
        }
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("eglCreatePbufferSurface failed");
        }

        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
              new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        if (eglContext == EGL15.EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext failed");
        }
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("eglCreateContext failed");
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("eglMakeCurrent failed");
        }

        SyncFence nativeFence = null;

        String eglExtensions = EGL14.eglQueryString(eglDisplay, EGL14.EGL_EXTENSIONS);
        if (eglExtensions.contains("EGL_ANDROID_native_fence_sync")) {
            EGLSync sync = EGL15.eglCreateSync(eglDisplay, EGLExt.EGL_SYNC_NATIVE_FENCE_ANDROID,
                    new long[] {
                        EGL14.EGL_NONE },
                    0);
            assertNotEquals(sync, EGL15.EGL_NO_SYNC);
            assertEquals(EGL14.EGL_SUCCESS, EGL14.eglGetError());

            nativeFence = EGLExt.eglDupNativeFenceFDANDROID(eglDisplay, sync);
            assertNotNull(nativeFence);
            assertEquals(EGL14.EGL_SUCCESS, EGL14.eglGetError());
            // If the fence isn't valid, trigger a flush & try again
            if (!nativeFence.isValid()) {
                GLES20.glFlush();
                assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError());

                // Have flushed, native fence should be populated
                nativeFence = EGLExt.eglDupNativeFenceFDANDROID(eglDisplay, sync);
                assertNotNull(nativeFence);
                assertTrue(nativeFence.isValid());
                assertEquals(EGL14.EGL_SUCCESS, EGL14.eglGetError());
            }

            assertTrue(EGL15.eglDestroySync(eglDisplay, sync));
        }

        EGL14.eglTerminate(eglDisplay);

        return nativeFence;
    }
}
