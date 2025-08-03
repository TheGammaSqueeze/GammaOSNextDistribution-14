// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "GfxstreamEnd2EndTests.h"

namespace gfxstream {
namespace tests {
namespace {

using testing::Eq;
using testing::Gt;
using testing::HasSubstr;
using testing::IsEmpty;
using testing::IsTrue;
using testing::Le;
using testing::Not;

class GfxstreamEnd2EndGlTest : public GfxstreamEnd2EndTest {};

TEST_P(GfxstreamEnd2EndGlTest, BasicViewport) {
    constexpr const int width = 32;
    constexpr const int height = 32;

    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    SetUpEglContextAndSurface(2, width, height, &display, &context, &surface);

    GLint viewport[4] = {};
    mGl->glGetIntegerv(GL_VIEWPORT, viewport);

    EXPECT_THAT(viewport[0], Eq(0));
    EXPECT_THAT(viewport[1], Eq(0));
    EXPECT_THAT(viewport[2], Eq(width));
    EXPECT_THAT(viewport[3], Eq(height));

    TearDownEglContextAndSurface(display, context, surface);
}

TEST_P(GfxstreamEnd2EndGlTest, CreateWindowSurface) {

    EGLDisplay display = mGl->eglGetDisplay(EGL_DEFAULT_DISPLAY);
    ASSERT_THAT(display, Not(Eq(EGL_NO_DISPLAY)));

    int versionMajor = 0;
    int versionMinor = 0;
    ASSERT_THAT(mGl->eglInitialize(display, &versionMajor, &versionMinor), IsTrue());

    ASSERT_THAT(mGl->eglBindAPI(EGL_OPENGL_ES_API), IsTrue());

    // clang-format off
    static const EGLint configAttributes[] = {
        EGL_SURFACE_TYPE,    EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_NONE,
    };
    // clang-format on

    int numConfigs = 0;
    ASSERT_THAT(mGl->eglChooseConfig(display, configAttributes, nullptr, 1, &numConfigs), IsTrue());
    ASSERT_THAT(numConfigs, Gt(0));

    EGLConfig config = nullptr;
    ASSERT_THAT(mGl->eglChooseConfig(display, configAttributes, &config, 1, &numConfigs), IsTrue());
    ASSERT_THAT(config, Not(Eq(nullptr)));

    // clang-format off
    static const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE,
    };
    // clang-format on

    EGLContext context = mGl->eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    ASSERT_THAT(context, Not(Eq(EGL_NO_CONTEXT)));

    constexpr const int width = 32;
    constexpr const int height = 32;

    auto anw = mAnwHelper->createNativeWindowForTesting(mGralloc.get(), width, height);

    EGLSurface surface = mGl->eglCreateWindowSurface(display, config, anw, nullptr);
    ASSERT_THAT(surface, Not(Eq(EGL_NO_SURFACE)));

    ASSERT_THAT(mGl->eglMakeCurrent(display, surface, surface, context), IsTrue());

    constexpr const int iterations = 120;
    for (int i = 0; i < iterations; i++) {
        mGl->glViewport(0, 0, width, height);
        mGl->glClearColor(1.0f, 0.0f, static_cast<float>(i) / static_cast<float>(iterations), 1.0f);
        mGl->glClear(GL_COLOR_BUFFER_BIT);
        mGl->glFinish();
        mGl->eglSwapBuffers(display, surface);
    }

    ASSERT_THAT(mGl->eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT), IsTrue());
    ASSERT_THAT(mGl->eglDestroyContext(display, context), IsTrue());
    ASSERT_THAT(mGl->eglDestroySurface(display, surface), IsTrue());
    mAnwHelper->release(anw);

    TearDownGuest();
}

TEST_P(GfxstreamEnd2EndGlTest, SwitchContext) {
    constexpr const int width = 32;
    constexpr const int height = 32;

    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    SetUpEglContextAndSurface(2, width, height, &display, &context, &surface);

    ASSERT_THAT(mGl->eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT), IsTrue());
    for (int i = 0; i < 100; i++) {
        ASSERT_THAT(mGl->eglMakeCurrent(display, surface, surface, context), IsTrue());
        ASSERT_THAT(mGl->eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT), IsTrue());
    }

    TearDownEglContextAndSurface(display, context, surface);
}

TEST_P(GfxstreamEnd2EndGlTest, MappedMemory) {
    constexpr const int width = 32;
    constexpr const int height = 32;

    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    SetUpEglContextAndSurface(3, width, height, &display, &context, &surface);

    constexpr GLsizei kBufferSize = 64;

    GLuint buffer;
    mGl->glGenBuffers(1, &buffer);
    mGl->glBindBuffer(GL_ARRAY_BUFFER, buffer);
    mGl->glBufferData(GL_ARRAY_BUFFER, kBufferSize, 0, GL_DYNAMIC_DRAW);

    std::vector<uint8_t> bufferData(kBufferSize);
    for (uint8_t i = 0; i < kBufferSize; ++i) {
        bufferData[i] = i;
    }

    {
        auto* mappedBufferData = reinterpret_cast<uint8_t*>(
            mGl->glMapBufferRange(GL_ARRAY_BUFFER, 0, kBufferSize,
                                  GL_MAP_WRITE_BIT | GL_MAP_FLUSH_EXPLICIT_BIT));

        for (uint8_t i = 0; i < kBufferSize; ++i) {
            mappedBufferData[i] = bufferData[i];
        }

        mGl->glFlushMappedBufferRange(GL_ARRAY_BUFFER, 0, kBufferSize);
        mGl->glUnmapBuffer(GL_ARRAY_BUFFER);
    }

    {
        auto* mappedBufferData = reinterpret_cast<uint8_t*>(
            mGl->glMapBufferRange(GL_ARRAY_BUFFER, 0, kBufferSize, GL_MAP_READ_BIT));

        for (uint8_t i = 0; i < kBufferSize; ++i) {
            EXPECT_THAT(mappedBufferData[i], Eq(bufferData[i]));
        }

        mGl->glUnmapBuffer(GL_ARRAY_BUFFER);
    }

    mGl->glBindBuffer(GL_ARRAY_BUFFER, 0);
    mGl->glDeleteBuffers(1, &buffer);

    TearDownEglContextAndSurface(display, context, surface);
}

TEST_P(GfxstreamEnd2EndGlTest, ContextStrings) {
    EGLDisplay display = mGl->eglGetDisplay(EGL_DEFAULT_DISPLAY);
    ASSERT_THAT(display, Not(Eq(EGL_NO_DISPLAY)));

    int versionMajor = 0;
    int versionMinor = 0;
    ASSERT_THAT(mGl->eglInitialize(display, &versionMajor, &versionMinor), IsTrue());

    ASSERT_THAT(mGl->eglBindAPI(EGL_OPENGL_ES_API), IsTrue());

    // clang-format off
    static const EGLint configAttributes[] = {
        EGL_SURFACE_TYPE,    EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_NONE,
    };
    // clang-format on

    int numConfigs = 0;
    ASSERT_THAT(mGl->eglChooseConfig(display, configAttributes, nullptr, 1, &numConfigs), IsTrue());
    ASSERT_THAT(numConfigs, Gt(0));

    EGLConfig config = nullptr;
    ASSERT_THAT(mGl->eglChooseConfig(display, configAttributes, &config, 1, &numConfigs), IsTrue());
    ASSERT_THAT(config, Not(Eq(nullptr)));

    // clang-format off
    static const EGLint gles1ContextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 1,
        EGL_NONE,
    };
    // clang-format on

    EGLContext gles1Context = mGl->eglCreateContext(display, config, EGL_NO_CONTEXT, gles1ContextAttribs);
    ASSERT_THAT(gles1Context, Not(Eq(EGL_NO_CONTEXT)));

    // clang-format off
    static const EGLint gles2ContextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE,
    };
    // clang-format on

    EGLContext gles2Context = mGl->eglCreateContext(display, config, EGL_NO_CONTEXT, gles2ContextAttribs);
    ASSERT_THAT(gles2Context, Not(Eq(EGL_NO_CONTEXT)));

    constexpr const int width = 32;
    constexpr const int height = 32;

    // clang-format off
    static const EGLint surfaceAttributes[] = {
        EGL_WIDTH,  width,
        EGL_HEIGHT, height,
        EGL_NONE,
    };
    // clang-format on

    EGLSurface surface = mGl->eglCreatePbufferSurface(display, config, surfaceAttributes);
    ASSERT_THAT(surface, Not(Eq(EGL_NO_SURFACE)));

    {
        ASSERT_THAT(mGl->eglMakeCurrent(display, surface, surface, gles2Context), IsTrue());
        const auto versionString = (const char*)mGl->glGetString(GL_VERSION);
        const auto extensionString = (const char*)mGl->glGetString(GL_EXTENSIONS);
        EXPECT_THAT(versionString, HasSubstr("ES 3"));
        EXPECT_THAT(extensionString, Not(HasSubstr("OES_draw_texture")));
    }
    {
        ASSERT_THAT(mGl->eglMakeCurrent(display, surface, surface, gles1Context), IsTrue());
        const auto versionString = (const char*)mGl->glGetString(GL_VERSION);
        const auto extensionString = (const char*)mGl->glGetString(GL_EXTENSIONS);
        EXPECT_THAT(versionString, HasSubstr("ES-CM"));
        EXPECT_THAT(extensionString, HasSubstr("OES_draw_texture"));
    }
    {
        ASSERT_THAT(mGl->eglMakeCurrent(display, surface, surface, gles2Context), IsTrue());
        const auto versionString = (const char*)mGl->glGetString(GL_VERSION);
        const auto extensionString = (const char*)mGl->glGetString(GL_EXTENSIONS);
        EXPECT_THAT(versionString, HasSubstr("ES 3"));
        EXPECT_THAT(extensionString, Not(HasSubstr("OES_draw_texture")));
    }

    ASSERT_THAT(mGl->eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT), IsTrue());
    ASSERT_THAT(mGl->eglDestroyContext(display, gles1Context), IsTrue());
    ASSERT_THAT(mGl->eglDestroyContext(display, gles2Context), IsTrue());
    ASSERT_THAT(mGl->eglDestroySurface(display, surface), IsTrue());
}

TEST_P(GfxstreamEnd2EndGlTest, FramebufferFetchShader) {
    constexpr const int width = 32;
    constexpr const int height = 32;

    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    SetUpEglContextAndSurface(3, width, height, &display, &context, &surface);

    const std::string extensionsString = (const char*)mGl->glGetString(GL_EXTENSIONS);
    ASSERT_THAT(extensionsString, Not(IsEmpty()));

    const bool supportsFramebufferFetch =
        extensionsString.find("GL_EXT_shader_framebuffer_fetch") != std::string::npos;

    const std::string shaderSource = R"(\
#version 300 es
#extension GL_EXT_shader_framebuffer_fetch : require
precision highp float;
in vec3 color_varying;
out vec4 fragColor;
void main() {
    fragColor = vec4(color_varying, 1.0);
}
    )";
    auto result = SetUpShader(GL_FRAGMENT_SHADER, shaderSource);
    if (result.ok()) {
        ASSERT_THAT(supportsFramebufferFetch, Eq(GL_TRUE));
        mGl->glDeleteShader(*result);
    } else {
        ASSERT_THAT(supportsFramebufferFetch, Eq(GL_FALSE));
    }

    TearDownEglContextAndSurface(display, context, surface);
}

TEST_P(GfxstreamEnd2EndGlTest, ConstantMatrixShader) {
    constexpr const int width = 32;
    constexpr const int height = 32;

    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    SetUpEglContextAndSurface(2, width, height, &display, &context, &surface);

    const std::string shaderSource = R"(\
#version 300 es
precision mediump float;
in highp vec4 dEQP_Position;
out vec2 out0;

void main() {
    const mat4x2 matA = mat4x2( 2.0,  4.0,   8.0,  16.0,
                               32.0, 64.0, 128.0, 256.0);
    const mat4x2 matB = mat4x2(1.0 /  2.0, 1.0 /  4.0, 1.0 /   8.0, 1.0 /  16.0,
                               1.0 / 32.0, 1.0 / 64.0, 1.0 / 128.0, 1.0 / 256.0);
    mat4x2 result = matrixCompMult(matA, matB);

    out0 = result * vec4(1.0, 1.0, 1.0, 1.0);
    gl_Position = dEQP_Position;
}
    )";

    auto result = SetUpShader(GL_VERTEX_SHADER, shaderSource);
    ASSERT_THAT(result, IsOk());
    mGl->glDeleteShader(result.value());

    TearDownEglContextAndSurface(display, context, surface);
}

TEST_P(GfxstreamEnd2EndGlTest, Draw) {
    constexpr const int width = 32;
    constexpr const int height = 32;

    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    SetUpEglContextAndSurface(2, width, height, &display, &context, &surface);

    const std::string vertSource = R"(\
#version 300 es
precision highp float;

layout (location = 0) in vec2 pos;
layout (location = 1) in vec3 color;

uniform mat4 transform;

out vec3 color_varying;

void main() {
    gl_Position = transform * vec4(pos, 0.0, 1.0);
    color_varying = (transform * vec4(color, 1.0)).xyz;
}
    )";

    const std::string fragSource = R"(\
#version 300 es
precision highp float;

in vec3 color_varying;

out vec4 fragColor;

void main() {
    fragColor = vec4(color_varying, 1.0);
}
    )";

    auto programResult = SetUpProgram(vertSource, fragSource);
    ASSERT_THAT(programResult, IsOk());
    auto program = programResult.value();

    GLint transformUniformLocation = mGl->glGetUniformLocation(program, "transform");
    mGl->glEnableVertexAttribArray(0);
    mGl->glEnableVertexAttribArray(1);

    struct VertexAttributes {
        float position[2];
        float color[3];
    };
    const VertexAttributes vertexAttrs[] = {
        // clang-format off
        { { -0.5f, -0.5f,}, { 0.2, 0.1, 0.9, }, },
        { {  0.5f, -0.5f,}, { 0.8, 0.3, 0.1, }, },
        { {  0.0f,  0.5f,}, { 0.1, 0.9, 0.6, }, },
        // clang-format on
    };

    GLuint buffer;
    mGl->glGenBuffers(1, &buffer);
    mGl->glBindBuffer(GL_ARRAY_BUFFER, buffer);
    mGl->glBufferData(GL_ARRAY_BUFFER, sizeof(vertexAttrs), vertexAttrs, GL_STATIC_DRAW);

    mGl->glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, sizeof(VertexAttributes), 0);
    mGl->glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, sizeof(VertexAttributes), (GLvoid*)offsetof(VertexAttributes, color));

    mGl->glUseProgram(program);

    mGl->glViewport(0, 0, 1, 1);

    mGl->glClearColor(0.2f, 0.2f, 0.3f, 0.0f);
    mGl->glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    const float matrix[16] = {
        // clang-format off
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
        // clang-format on
    };

    constexpr uint32_t kDrawIterations = 200;
    for (uint32_t i = 0; i < kDrawIterations; i++) {
        mGl->glUniformMatrix4fv(transformUniformLocation, 1, GL_FALSE, matrix);
        mGl->glBindBuffer(GL_ARRAY_BUFFER, buffer);
        mGl->glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    mGl->glFinish();

    mGl->glBindBuffer(GL_ARRAY_BUFFER, 0);
    mGl->glDeleteBuffers(1, &buffer);

    mGl->glUseProgram(0);
    mGl->glDeleteProgram(program);

    TearDownEglContextAndSurface(display, context, surface);
}

INSTANTIATE_TEST_CASE_P(GfxstreamEnd2EndTests, GfxstreamEnd2EndGlTest,
                        ::testing::ValuesIn({
                            TestParams{
                                .with_gl = true,
                                .with_vk = false,
                            },
                            TestParams{
                                .with_gl = true,
                                .with_vk = true,
                            },
                        }),
                        &GetTestName);

}  // namespace
}  // namespace tests
}  // namespace gfxstream
