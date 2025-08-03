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

#pragma once

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace abc3d {

struct CantCopyAssign {
    CantCopyAssign() = default;
    CantCopyAssign(const CantCopyAssign&) = delete;
    CantCopyAssign& operator=(const CantCopyAssign&) = delete;
};

struct CantCopyAssignMove : CantCopyAssign {
    CantCopyAssignMove() = default;
    CantCopyAssignMove(CantCopyAssignMove&&) = delete;
    CantCopyAssign& operator=(CantCopyAssign&&) = delete;
};

struct AutoImageKHR : CantCopyAssign {
    AutoImageKHR(EGLDisplay, EGLImageKHR);
    AutoImageKHR(AutoImageKHR&&) noexcept;
    AutoImageKHR& operator=(AutoImageKHR&&) noexcept;
    ~AutoImageKHR();

    bool ok() const { return mEglImage != EGL_NO_IMAGE_KHR; }
    EGLImageKHR get() const { return mEglImage; }

private:
    EGLDisplay mEglDisplay;
    EGLImageKHR mEglImage;
};

struct EglCurrentContext : CantCopyAssign {
    EglCurrentContext() = default;
    EglCurrentContext(EGLDisplay);
    EglCurrentContext(EglCurrentContext&&) noexcept;
    EglCurrentContext& operator=(EglCurrentContext&&) noexcept;
    ~EglCurrentContext();

    bool ok() const { return mEglDisplay != EGL_NO_DISPLAY; }

private:
    EGLDisplay mEglDisplay = EGL_NO_DISPLAY;
};

struct EglContext : CantCopyAssign {
    EglContext() = default;
    EglContext(EglContext&&) noexcept;
    EglContext& operator=(EglContext&&) noexcept;
    ~EglContext();

    EglCurrentContext init();
    EglCurrentContext getCurrentContext();
    EGLDisplay getDisplay() const { return mEglDisplay; }
    void clear();

private:
    EGLDisplay mEglDisplay = EGL_NO_DISPLAY;
    EGLContext mEglContext = EGL_NO_CONTEXT;
    EGLSurface mEglSurface = EGL_NO_SURFACE;
};

struct AutoTexture : CantCopyAssign {
    AutoTexture() = default;
    AutoTexture(GLenum target);
    AutoTexture(GLenum target,
                GLint internalformat,
                GLsizei width,
                GLsizei height,
                GLenum format,
                GLenum type,
                const void * data);
    AutoTexture(AutoTexture&&) noexcept;
    AutoTexture& operator=(AutoTexture&&) noexcept;
    ~AutoTexture();

    GLuint ok() const { return mTex != 0; }
    GLuint get() const { return mTex; }
    void clear();

private:
    GLuint mTex = 0;
};

struct AutoFrameBuffer : CantCopyAssignMove {
    AutoFrameBuffer();
    ~AutoFrameBuffer();

    GLuint ok() const { return mFBO != 0; }
    GLuint get() const { return mFBO; }

private:
    GLuint mFBO = 0;
};

struct AutoShader : CantCopyAssign {
    AutoShader() = default;
    AutoShader(AutoShader&&) noexcept;
    AutoShader& operator=(AutoShader&&) noexcept;
    ~AutoShader();

    GLuint compile(GLenum type, const char* text);
    GLuint get() const { return mShader; }

private:
    GLuint mShader = 0;
};

struct AutoProgram : CantCopyAssign {
    AutoProgram() = default;
    AutoProgram(AutoProgram&&) noexcept;
    AutoProgram& operator=(AutoProgram&&) noexcept;
    ~AutoProgram();

    bool link(GLuint vertexShader, GLuint fragmentShader);
    GLint getAttribLocation(const char* name) const;
    GLint getUniformLocation(const char* name) const;
    void clear();
    bool ok() const { return mProgram != 0; }
    GLuint get() const { return mProgram; }

private:
    GLuint mProgram = 0;
};

void frustum(float m44[], double left, double right,
             double bottom, double top, double nearVal, double farVal);
void lookAtXyzRot(float m44[], const float eye3[], const float rot3[]);
void mulM44(float m44[], const float lhs44[], const float rhs44[]);

}  // namespace abc3d
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
