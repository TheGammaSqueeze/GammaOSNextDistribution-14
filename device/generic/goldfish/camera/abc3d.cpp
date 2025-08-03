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

#include <vector>
#include <math.h>

#define GL_GLEXT_PROTOTYPES
#define EGL_EGLEXT_PROTOTYPES
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#undef EGL_EGLEXT_PROTOTYPES
#undef GL_GLEXT_PROTOTYPES

#include "abc3d.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace abc3d {
namespace {
constexpr char kTag[] = "abc3d";

float dot3(const float a3[], const float b3[]) {
    return a3[0] * b3[0] + a3[1] * b3[1] + a3[2] * b3[2];
}

/*
 * https://registry.khronos.org/OpenGL-Refpages/gl2.1/xhtml/gluLookAt.xml
 * https://registry.khronos.org/OpenGL-Refpages/gl2.1/xhtml/glTranslate.xml
 * This function takes `m44` (where zzz (assumed zero) and ooo (assumed one)
 * are ignored) and multiplies it by a translation matrix.
 *
 *           m44             translate
 *  [  s0  s1  s2 zzz ]   [ 1 0 0 -eyeX ]   [  s0  s1  s2 -dot3(m44[0:2],  eye3) ]
 *  [ up0 up1 up2 zzz ] * [ 0 1 0 -eyeY ] = [ up0 up1 up2 -dot3(m44[4:6],  eye3) ]
 *  [  b0  b1  b2 zzz ]   [ 0 0 1 -eyeZ ]   [  b0  b1  b2 -dot3(m44[8:10], eye3) ]
 *  [ zzz zzz zzz ooo ]   [ 0 0 0     1 ]   [   0   0   0                      1 ]
*/
void lookAtEyeCoordinates(float m44[], const float eye3[]) {
    m44[3]  = -dot3(&m44[0], eye3);
    m44[7]  = -dot3(&m44[4], eye3);
    m44[11] = -dot3(&m44[8], eye3);
    m44[12] = 0;
    m44[13] = 0;
    m44[14] = 0;
    m44[15] = 1;
}
}  // namespace

#define RETURN_CTOR_FAILED(S) \
    ALOGE("%s:%s:%d %s failed", kTag, __func__, __LINE__, S); return;

AutoImageKHR::AutoImageKHR(const EGLDisplay display, const EGLClientBuffer clientBuf)
        : mEglDisplay(display) {
    static const EGLint imageAttrs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    mEglImage = eglCreateImageKHR(
        display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuf, imageAttrs);
    if (mEglImage == EGL_NO_IMAGE_KHR) {
        RETURN_CTOR_FAILED("eglCreateImageKHR");
    }
}

AutoImageKHR::AutoImageKHR(AutoImageKHR&& rhs) noexcept
        : mEglDisplay(rhs.mEglDisplay)
        , mEglImage(std::exchange(rhs.mEglImage, EGL_NO_IMAGE_KHR)) {}

AutoImageKHR& AutoImageKHR::operator=(AutoImageKHR&& rhs) noexcept {
    if (this != &rhs) {
        mEglDisplay = rhs.mEglDisplay;
        mEglImage = std::exchange(rhs.mEglImage, EGL_NO_IMAGE_KHR);
    }
    return *this;
}

AutoImageKHR::~AutoImageKHR() {
    if (mEglImage != EGL_NO_IMAGE_KHR) {
        eglDestroyImageKHR(mEglDisplay, mEglImage);
    }
}

EglCurrentContext::EglCurrentContext(const EGLDisplay display)
        : mEglDisplay(display) {}

EglCurrentContext::EglCurrentContext(EglCurrentContext&& rhs) noexcept
        : mEglDisplay(std::exchange(rhs.mEglDisplay, EGL_NO_DISPLAY)) {}

EglCurrentContext& EglCurrentContext::operator=(EglCurrentContext&& rhs) noexcept {
    if (this != &rhs) {
        mEglDisplay = std::exchange(rhs.mEglDisplay, EGL_NO_DISPLAY);
    }
    return *this;
}

EglCurrentContext::~EglCurrentContext() {
    if (mEglDisplay != EGL_NO_DISPLAY) {
        LOG_ALWAYS_FATAL_IF(!eglMakeCurrent(mEglDisplay, EGL_NO_SURFACE,
                                            EGL_NO_SURFACE, EGL_NO_CONTEXT));
    }
}

EglContext::EglContext(EglContext&& rhs) noexcept
        : mEglDisplay(std::exchange(rhs.mEglDisplay, EGL_NO_DISPLAY))
        , mEglContext(std::exchange(rhs.mEglContext, EGL_NO_CONTEXT))
        , mEglSurface(std::exchange(rhs.mEglSurface, EGL_NO_SURFACE)) {}

EglContext& EglContext::operator=(EglContext&& rhs) noexcept {
    if (this != &rhs) {
        mEglDisplay = std::exchange(rhs.mEglDisplay, EGL_NO_DISPLAY);
        mEglContext = std::exchange(rhs.mEglContext, EGL_NO_CONTEXT);
        mEglSurface = std::exchange(rhs.mEglSurface, EGL_NO_SURFACE);
    }
    return *this;
}

EglContext::~EglContext() {
    clear();
}

void EglContext::clear() {
    if (mEglSurface != EGL_NO_SURFACE) {
        eglDestroySurface(mEglDisplay, mEglSurface);
        mEglSurface = EGL_NO_SURFACE;
    }
    if (mEglContext != EGL_NO_CONTEXT) {
        eglDestroyContext(mEglDisplay, mEglContext);
        mEglContext = EGL_NO_CONTEXT;
    }
    if (mEglDisplay != EGL_NO_DISPLAY) {
        eglTerminate(mEglDisplay);
        mEglDisplay = EGL_NO_DISPLAY;
    }
}

EglCurrentContext EglContext::init() {
    if (mEglContext != EGL_NO_CONTEXT) {
        LOG_ALWAYS_FATAL_IF(!eglMakeCurrent(mEglDisplay, mEglSurface,
                                            mEglSurface, mEglContext));
        return EglCurrentContext(mEglDisplay);
    }

    const EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        return EglCurrentContext(FAILURE(EGL_NO_DISPLAY));
    }

    EGLint major, minor;
    if (!eglInitialize(display, &major, &minor)) {
        return EglCurrentContext(FAILURE(EGL_NO_DISPLAY));
    }
    ALOGD("%s:%d: Initialized EGL, version %d.%d", __func__, __LINE__,
          static_cast<int>(major), static_cast<int>(minor));

    static const EGLint configAttrs[] = {
        EGL_RENDERABLE_TYPE,    EGL_OPENGL_ES2_BIT,
        EGL_CONFIG_CAVEAT,      EGL_NONE,
        EGL_RED_SIZE,           8,
        EGL_GREEN_SIZE,         8,
        EGL_BLUE_SIZE,          8,
        EGL_ALPHA_SIZE,         8,
        EGL_NONE
    };

    EGLint numConfigs = 1;
    EGLConfig config = EGL_NO_CONFIG_KHR;
    if (!eglChooseConfig(display, configAttrs, &config, 1, &numConfigs) ||
            (config == EGL_NO_CONFIG_KHR) || (numConfigs != 1)) {
        eglTerminate(display);
        return EglCurrentContext(FAILURE(EGL_NO_DISPLAY));
    }

    static const EGLint contextAttrs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };
    const EGLContext context = eglCreateContext(display, config,
                                                EGL_NO_CONTEXT, contextAttrs);
    if (context == EGL_NO_CONTEXT) {
        eglTerminate(display);
        return EglCurrentContext(FAILURE(EGL_NO_DISPLAY));
    }

    EGLSurface surface = EGL_NO_SURFACE;
    if (!eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) {
        // EGL_KHR_surfaceless_context is not supported
        const EGLint surfaceAttrs[] = {
            EGL_WIDTH,  1,
            EGL_HEIGHT, 1,
            EGL_NONE
        };
        surface = eglCreatePbufferSurface(display, config, surfaceAttrs);
        if (surface == EGL_NO_SURFACE) {
            eglDestroyContext(display, context);
            eglTerminate(display);
            return EglCurrentContext(FAILURE(EGL_NO_DISPLAY));
        }

        if (!eglMakeCurrent(display, surface, surface, context)) {
            eglDestroySurface(display, surface);
            eglDestroyContext(display, context);
            eglTerminate(display);
            return EglCurrentContext(FAILURE(EGL_NO_DISPLAY));
        }
    }

    mEglDisplay = display;
    mEglContext = context;
    mEglSurface = surface;

    return EglCurrentContext(display);
}

EglCurrentContext EglContext::getCurrentContext() {
    if (mEglContext == EGL_NO_CONTEXT) {
        return EglCurrentContext(EGL_NO_DISPLAY);
    } else if (eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
        return EglCurrentContext(mEglDisplay);
    } else {
        return EglCurrentContext(FAILURE(EGL_NO_DISPLAY));
    }
}

AutoTexture::AutoTexture(const GLenum target) {
    glGenTextures(1, &mTex);
    if (mTex) {
        glBindTexture(target, mTex);
    } else {
        RETURN_CTOR_FAILED("glGenTextures");
    }
}

AutoTexture::AutoTexture(const GLenum target,
                         const GLint internalformat,
                         const GLsizei width,
                         const GLsizei height,
                         const GLenum format,
                         const GLenum type,
                         const void* data) {
    glGenTextures(1, &mTex);
    if (mTex) {
        glBindTexture(target, mTex);
        glTexImage2D(target, 0, internalformat, width, height, 0, format, type, data);
    } else {
        RETURN_CTOR_FAILED("glGenTextures");
    }
}

AutoTexture::AutoTexture(AutoTexture&& rhs) noexcept
        : mTex(std::exchange(rhs.mTex, 0)) {}

AutoTexture& AutoTexture::operator=(AutoTexture&& rhs) noexcept {
    if (this != &rhs) {
        mTex = std::exchange(rhs.mTex, 0);
    }
    return *this;
}

AutoTexture::~AutoTexture() {
    clear();
}

void AutoTexture::clear() {
    if (mTex) {
        glDeleteTextures(1, &mTex);
        mTex = 0;
    }
}

AutoFrameBuffer::AutoFrameBuffer() {
    glGenFramebuffers(1, &mFBO);
    if (mFBO) {
        glBindFramebuffer(GL_FRAMEBUFFER, mFBO);
    } else {
        RETURN_CTOR_FAILED("glGenFramebuffers");
    }
}

AutoFrameBuffer::~AutoFrameBuffer() {
    if (mFBO) {
        glDeleteFramebuffers(1, &mFBO);
    }
}

AutoShader::AutoShader(AutoShader&& rhs) noexcept
        : mShader(std::exchange(rhs.mShader, 0)) {}

AutoShader& AutoShader::operator=(AutoShader&& rhs) noexcept {
    if (this != &rhs) {
        mShader = std::exchange(rhs.mShader, 0);
    }
    return *this;
}

AutoShader::~AutoShader() {
    if (mShader) {
        glDeleteShader(mShader);
    }
}

GLuint AutoShader::compile(const GLenum type, const char* text) {
    const GLuint shader = glCreateShader(type);
    if (!shader) {
        return FAILURE(0);
    }

    glShaderSource(shader, 1, &text, nullptr);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if(infoLen > 1) {
            std::vector<char> msg(infoLen + 1);
            glGetShaderInfoLog(shader, infoLen, nullptr, msg.data());
            msg[infoLen] = 0;
            ALOGE("%s:%d: error compiling shader '%s' (type=%d): '%s'",
                  __func__, __LINE__, text, type, msg.data());
        }
        glDeleteShader(shader);
        return FAILURE(0);
    }

    if (mShader) {
        glDeleteShader(mShader);
    }

    mShader = shader;
    return shader;
}

AutoProgram::AutoProgram(AutoProgram&& rhs) noexcept
        : mProgram(std::exchange(rhs.mProgram, 0)) {}

AutoProgram& AutoProgram::operator=(AutoProgram&& rhs) noexcept {
    if (this != &rhs) {
        mProgram = std::exchange(rhs.mProgram, 0);
    }
    return *this;
}

AutoProgram::~AutoProgram() {
    clear();
}

void AutoProgram::clear() {
    if (mProgram) {
        glDeleteProgram(mProgram);
        mProgram = 0;
    }
}

bool AutoProgram::link(const GLuint vertexShader,
                       const GLuint fragmentShader) {
    const GLuint program = glCreateProgram();
    if (!program) {
        return FAILURE(false);
    }

    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLen);
        if(infoLen > 1) {
            std::vector<char> msg(infoLen + 1);
            glGetProgramInfoLog(program, infoLen, nullptr, msg.data());
            msg[infoLen] = 0;
            ALOGE("%s:%d: error linking shaders: '%s'",
                  __func__, __LINE__, msg.data());
        }

        glDeleteProgram(program);
        return FAILURE(false);
    }

    if (mProgram) {
        glDeleteProgram(mProgram);
    }

    mProgram = program;
    return true;
}

GLint AutoProgram::getAttribLocation(const char* name) const {
    if (mProgram > 0) {
        const GLint result = glGetAttribLocation(mProgram, name);
        return (result >= 0) ? result : FAILURE(-1);
    } else {
        return FAILURE(-1);
    }
}

GLint AutoProgram::getUniformLocation(const char* name) const {
    if (mProgram > 0) {
        const GLint result = glGetUniformLocation(mProgram, name);
        return (result >= 0) ? result : FAILURE(-1);
    } else {
        return FAILURE(-1);
    }
}

// https://registry.khronos.org/OpenGL-Refpages/gl2.1/xhtml/glFrustum.xml
void frustum(float m44[],
             const double left, const double right,
             const double bottom, const double top,
             const double near, const double far) {
    const double invWidth = 1.0 / (right - left);
    const double invHeight = 1.0 / (top - bottom);
    const double invDepth = 1.0 / (far - near);
    const double near2 = 2 * near;

    m44[0] = near2 * invWidth;
    m44[1] = 0;
    m44[2] = (right + left) * invWidth;
    m44[3] = 0;

    m44[4] = 0;
    m44[5] = near2 * invHeight;
    m44[6] = (top + bottom) * invHeight;
    m44[7] = 0;

    m44[8] = 0;
    m44[9] = 0;
    m44[10] = -(far + near) * invDepth;
    m44[11] = -far * near2 * invDepth;

    m44[12] = 0;
    m44[13] = 0;
    m44[14] = -1;
    m44[15] = 0;
}

/*
 * https://registry.khronos.org/OpenGL-Refpages/gl2.1/xhtml/gluLookAt.xml
 * https://en.wikipedia.org/wiki/Rotation_matrix#Basic_rotations
 *
 * Here we calculate {Side, Up, Backwards} from Euler angles in the XYZ order:
 *
 * [ 1,    0,     0 ]   [  cosY, 0, sinY ]   [ cosZ, -sinZ, 0 ]   [ sx, ux, bx ]
 * [ 0, cosX, -sinX ] * [     0, 1,    0 ] * [ sinZ,  cosZ, 0 ] = [ sy, uy, by ]
 * [ 0, sinX,  cosX ]   [ -sinY, 0, cosY ]   [    0,     0, 1 ]   [ sz, uz, bz ]
 *
 * We calculate `backwards` because the camera looks into the negative Z
 * direction, so instead of calculating camera's forward and negating it twice,
 * let's call it `backwards`.
 *
 * After multiplying the first two:
 * [         cosY,    0,         sinY ]
 * [  sinX * sinY, cosX, -sinX * cosY ]
 * [ -cosX * sinY, sinX,  cosX * cosY ]
 *
 * The final result:
 * [                       cosY * cosZ,                      -cosY * sinZ,         sinY ]
 * [  sinX * sinY * cosZ + cosX * sinZ, -sinX * sinY * sinZ + cosX * cosZ, -sinX * cosY ]
 * [ -cosX * sinY * cosZ + sinX * sinZ,  cosX * sinY * sinZ + sinX * cosZ,  cosX * cosY ]
 *
 * {Side, Up, Backwards} are the columns in the matrix above.
 */
void lookAtXyzRot(float m44[], const float eye3[], const float rot3[]) {
    const double sinX = sin(rot3[0]);
    const double cosX = cos(rot3[0]);
    const double sinY = sin(rot3[1]);
    const double cosY = cos(rot3[1]);
    const double sinZ = sin(rot3[2]);
    const double cosZ = cos(rot3[2]);

    m44[0]  = cosY * cosZ;
    m44[1]  = sinX * sinY * cosZ + cosX * sinZ;
    m44[2]  = -cosX * sinY * cosZ + sinX * sinZ;
    m44[4]  = -cosY * sinZ;
    m44[5]  = -sinX * sinY * sinZ + cosX * cosZ;
    m44[6]  = cosX * sinY * sinZ + sinX * cosZ;
    m44[8]  = sinY;
    m44[9]  = -sinX * cosY;
    m44[10] = cosX * cosY;
    lookAtEyeCoordinates(m44, eye3);
}

void mulM44(float m44[], const float lhs44[], const float rhs44[]) {
    m44[0] = lhs44[0] * rhs44[0] + lhs44[1] * rhs44[4] + lhs44[2] * rhs44[8] + lhs44[3] * rhs44[12];
    m44[1] = lhs44[0] * rhs44[1] + lhs44[1] * rhs44[5] + lhs44[2] * rhs44[9] + lhs44[3] * rhs44[13];
    m44[2] = lhs44[0] * rhs44[2] + lhs44[1] * rhs44[6] + lhs44[2] * rhs44[10] + lhs44[3] * rhs44[14];
    m44[3] = lhs44[0] * rhs44[3] + lhs44[1] * rhs44[7] + lhs44[2] * rhs44[11] + lhs44[3] * rhs44[15];

    m44[4] = lhs44[4] * rhs44[0] + lhs44[5] * rhs44[4] + lhs44[6] * rhs44[8] + lhs44[7] * rhs44[12];
    m44[5] = lhs44[4] * rhs44[1] + lhs44[5] * rhs44[5] + lhs44[6] * rhs44[9] + lhs44[7] * rhs44[13];
    m44[6] = lhs44[4] * rhs44[2] + lhs44[5] * rhs44[6] + lhs44[6] * rhs44[10] + lhs44[7] * rhs44[14];
    m44[7] = lhs44[4] * rhs44[3] + lhs44[5] * rhs44[7] + lhs44[6] * rhs44[11] + lhs44[7] * rhs44[15];

    m44[8] = lhs44[8] * rhs44[0] + lhs44[9] * rhs44[4] + lhs44[10] * rhs44[8] + lhs44[11] * rhs44[12];
    m44[9] = lhs44[8] * rhs44[1] + lhs44[9] * rhs44[5] + lhs44[10] * rhs44[9] + lhs44[11] * rhs44[13];
    m44[10] = lhs44[8] * rhs44[2] + lhs44[9] * rhs44[6] + lhs44[10] * rhs44[10] + lhs44[11] * rhs44[14];
    m44[11] = lhs44[8] * rhs44[3] + lhs44[9] * rhs44[7] + lhs44[10] * rhs44[11] + lhs44[11] * rhs44[15];

    m44[12] = lhs44[12] * rhs44[0] + lhs44[13] * rhs44[4] + lhs44[14] * rhs44[8] + lhs44[15] * rhs44[12];
    m44[13] = lhs44[12] * rhs44[1] + lhs44[13] * rhs44[5] + lhs44[14] * rhs44[9] + lhs44[15] * rhs44[13];
    m44[14] = lhs44[12] * rhs44[2] + lhs44[13] * rhs44[6] + lhs44[14] * rhs44[10] + lhs44[15] * rhs44[14];
    m44[15] = lhs44[12] * rhs44[3] + lhs44[13] * rhs44[7] + lhs44[14] * rhs44[11] + lhs44[15] * rhs44[15];
}

}  // namespace abc3d
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
