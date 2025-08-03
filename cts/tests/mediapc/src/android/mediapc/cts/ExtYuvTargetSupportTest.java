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

package android.mediapc.cts;

import static org.junit.Assert.assertTrue;

import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class ExtYuvTargetSupportTest {
    private static final String LOG_TAG = ExtYuvTargetSupportTest.class.getSimpleName();

    private static final int WIDTH = 720;
    private static final int HEIGHT = 480;
    private final boolean mUseHighBitDepth;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void isPerformanceClassCandidate() {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();
    }

    public ExtYuvTargetSupportTest(boolean useHighBitDepth) {
        mUseHighBitDepth = useHighBitDepth;
    }

    @Parameterized.Parameters(name = "{index}_{0}")
    public static Collection<Object[]> inputParams() {
        Boolean[] useHighBitDepthArray = {false, true};
        Collection<Object[]> params = new ArrayList<>();
        for (Boolean useHighBitDepth : useHighBitDepthArray) {
            params.add(new Object[]{useHighBitDepth});
        }
        return params;
    }

    /**
     * Prepares EGL.
     */
    private void eglSetup(boolean useHighBitDepth) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        int eglColorSize = useHighBitDepth ? 10 : 8;
        int eglAlphaSize = useHighBitDepth ? 2 : 0;
        int[] attribList = {
                EGL14.EGL_RED_SIZE, eglColorSize,
                EGL14.EGL_GREEN_SIZE, eglColorSize,
                EGL14.EGL_BLUE_SIZE, eglColorSize,
                EGL14.EGL_ALPHA_SIZE, eglAlphaSize,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0) || numConfigs[0] == 0) {
            throw new RuntimeException(String.format(
                    "unable to find EGL config supporting renderable-type:ES2 "
                            + "surface-type:pbuffer r:%d g:%d b:%d a:%d",
                    eglColorSize, eglColorSize, eglColorSize, eglAlphaSize));
        }

        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0);

        Assert.assertNotEquals("failed to configure context", mEGLContext, EGL14.EGL_NO_CONTEXT);

        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        int[] surfaceAttribs = {
                EGL14.EGL_WIDTH, WIDTH,
                EGL14.EGL_HEIGHT, HEIGHT,
                EGL14.EGL_NONE
        };

        // Create a pbuffer surface.  By using this for output, we can use glReadPixels
        // to test values in the output.
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs, 0);
        checkEglError("eglCreatePbufferSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }
    }

    /**
     * Makes our EGL context and surface current.
     */
    private void makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Checks for EGL errors.
     */
    private void checkEglError(String msg) {
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.12/H-1-3"})
    public void testYuvTextureSampling() {

        eglSetup(mUseHighBitDepth);
        makeCurrent();

        String extensionList = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        boolean mEXTYuvTargetSupported = extensionList.contains("GL_EXT_YUV_target");

        assertTrue("GL_EXT_YUV_target extension is not present", mEXTYuvTargetSupported);

        EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
        EGL14.eglTerminate(mEGLDisplay);

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.ExtYuvTargetRequirement rExtensionSupported =
                pce.addExtYUVSupportReq();
        rExtensionSupported.setExtYuvTargetSupport(mEXTYuvTargetSupported);

        pce.submitAndCheck();
    }
}
