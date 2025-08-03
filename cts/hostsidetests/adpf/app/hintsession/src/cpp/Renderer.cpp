/*
 * Copyright 2023 The Android Open Source Project
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
#include "Renderer.h"

#include <GLES3/gl3.h>
#include <android/imagedecoder.h>

#include <memory>
#include <numeric>
#include <string>
#include <vector>
// #include <iostream>

#include <chrono>

#include "AndroidOut.h"
#include "JNIManager.h"
#include "Shader.h"
#include "TextureAsset.h"
#include "Utility.h"
#include "android/performance_hint.h"

using namespace std::chrono_literals;
//! executes glGetString and outputs the result to logcat
#define PRINT_GL_STRING(s) \
    { aout << #s ": " << glGetString(s) << std::endl; }

/*!
 * @brief if glGetString returns a space separated list of elements, prints each one on a new line
 *
 * This works by creating an istringstream of the input c-style string. Then that is used to create
 * a vector -- each element of the vector is a new element in the input string. Finally a foreach
 * loop consumes this and outputs it to logcat using @a aout
 */
#define PRINT_GL_STRING_AS_LIST(s)                                                 \
    {                                                                              \
        std::istringstream extensionStream((const char *)glGetString(s));          \
        std::vector<std::string>                                                   \
                extensionList(std::istream_iterator<std::string>{extensionStream}, \
                              std::istream_iterator<std::string>());               \
        aout << #s ":\n";                                                          \
        for (auto &extension : extensionList) {                                    \
            aout << extension << "\n";                                             \
        }                                                                          \
        aout << std::endl;                                                         \
    }

//! Color for cornflower blue. Can be sent directly to glClearColor
#define CORNFLOWER_BLUE 100 / 255.f, 149 / 255.f, 237 / 255.f, 1

// Vertex shader, you'd typically load this from assets
static const char *vertex = R"vertex(#version 300 es
in vec3 inPosition;
in vec2 inUV;

out vec2 fragUV;

uniform mat4 uProjection;

void main() {
    fragUV = inUV;
    gl_Position = uProjection * vec4(inPosition, 1.0);
}
)vertex";

// Fragment shader, you'd typically load this from assets
static const char *fragment = R"fragment(#version 300 es
precision mediump float;

in vec2 fragUV;

uniform sampler2D uTexture;

out vec4 outColor;

void main() {
    outColor = texture(uTexture, fragUV);
}
)fragment";

/*!
 * Half the height of the projection matrix. This gives you a renderable area of height 4 ranging
 * from -2 to 2
 */
static constexpr float kProjectionHalfHeight = 2.f;

/*!
 * The near plane distance for the projection matrix. Since this is an orthographic projection
 * matrix, it's convenient to have negative values for sorting (and avoiding z-fighting at 0).
 */
static constexpr float kProjectionNearPlane = -1.f;

/*!
 * The far plane distance for the projection matrix. Since this is an orthographic porjection
 * matrix, it's convenient to have the far plane equidistant from 0 as the near plane.
 */
static constexpr float kProjectionFarPlane = 1.f;

Renderer::~Renderer() {
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
            context_ = EGL_NO_CONTEXT;
        }
        if (surface_ != EGL_NO_SURFACE) {
            eglDestroySurface(display_, surface_);
            surface_ = EGL_NO_SURFACE;
        }
        eglTerminate(display_);
        display_ = EGL_NO_DISPLAY;
    }
}

jlong Renderer::render() {
    // Check to see if the surface has changed size. This is _necessary_ to do every frame when
    // using immersive mode as you'll get no other notification that your renderable area has
    // changed.

    updateRenderArea();
    assert(display_ != nullptr);
    assert(surface_ != nullptr);
    assert(shader_ != nullptr);

    // When the renderable area changes, the fprojection matrix has to also be updated. This is true
    // even if you change from the sample orthographic projection matrix as your aspect ratio has
    // likely changed.
    if (shaderNeedsNewProjectionMatrix_) {
        // a placeholder projection matrix allocated on the stack. Column-major memory layout
        float projectionMatrix[16] = {0};

        // build an orthographic projection matrix for 2d rendering
        Utility::buildOrthographicMatrix(projectionMatrix, kProjectionHalfHeight,
                                         float(width_) / height_, kProjectionNearPlane,
                                         kProjectionFarPlane);

        // send the matrix to the shader
        // Note: the shader must be active for this to work.
        assert(projectionMatrix != nullptr);

        if (shader_ != nullptr) {
            shader_->setProjectionMatrix(projectionMatrix);
        }

        // make sure the matrix isn't generated every frame
        shaderNeedsNewProjectionMatrix_ = false;
    }

    // clear the color buffer
    glClear(GL_COLOR_BUFFER_BIT);

    // Rotate the models
    const std::chrono::steady_clock::duration rpm = 2s;
    static std::chrono::steady_clock::time_point startTime = std::chrono::steady_clock::now();
    std::chrono::steady_clock::time_point renderTime = std::chrono::steady_clock::now();
    // Figure out what angle the models need to be at
    std::chrono::steady_clock::duration offset = (renderTime - startTime) % rpm;
    auto spin = static_cast<double>(offset.count()) / static_cast<double>(rpm.count());

    // Render all the models. There's no depth testing in this sample so they're accepted in the
    // order provided. But the sample EGL setup requests a 24 bit depth buffer so you could
    // configure it at the end of initRenderer
    auto start = std::chrono::steady_clock::now();

    if (!heads_.empty()) {
        for (auto &model : heads_) {
            model.setRotation(M_PI * 2.0 * spin);
            shader_->drawModel(model);
        }
    }

    auto end = std::chrono::steady_clock::now();

    // Present the rendered image. This is an implicit glFlush.
    auto swapResult = eglSwapBuffers(display_, surface_);
    assert(swapResult == EGL_TRUE);
    return (end - start).count();
}

void Renderer::initRenderer() {
    // Choose your render attributes
    constexpr EGLint attribs[] = {EGL_RENDERABLE_TYPE,
                                  EGL_OPENGL_ES3_BIT,
                                  EGL_SURFACE_TYPE,
                                  EGL_WINDOW_BIT,
                                  EGL_BLUE_SIZE,
                                  8,
                                  EGL_GREEN_SIZE,
                                  8,
                                  EGL_RED_SIZE,
                                  8,
                                  EGL_DEPTH_SIZE,
                                  24,
                                  EGL_NONE};

    // The default display is probably what you want on Android
    auto display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(display, nullptr, nullptr);

    // figure out how many configs there are
    EGLint numConfigs;
    eglChooseConfig(display, attribs, nullptr, 0, &numConfigs);

    // get the list of configurations
    std::unique_ptr<EGLConfig[]> supportedConfigs(new EGLConfig[numConfigs]);
    eglChooseConfig(display, attribs, supportedConfigs.get(), numConfigs, &numConfigs);

    // Find a config we like.
    // Could likely just grab the first if we don't care about anything else in the config.
    // Otherwise hook in your own heuristic
    auto config =
            *std::find_if(supportedConfigs.get(), supportedConfigs.get() + numConfigs,
                          [&display](const EGLConfig &config) {
                              EGLint red, green, blue, depth;
                              if (eglGetConfigAttrib(display, config, EGL_RED_SIZE, &red) &&
                                  eglGetConfigAttrib(display, config, EGL_GREEN_SIZE, &green) &&
                                  eglGetConfigAttrib(display, config, EGL_BLUE_SIZE, &blue) &&
                                  eglGetConfigAttrib(display, config, EGL_DEPTH_SIZE, &depth)) {
                                  aout << "Found config with " << red << ", " << green << ", "
                                       << blue << ", " << depth << std::endl;
                                  return red == 8 && green == 8 && blue == 8 && depth == 24;
                              }
                              return false;
                          });

    // create the proper window surface
    EGLint format;
    eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format);
    EGLSurface surface = eglCreateWindowSurface(display, config, app_->window, nullptr);

    // Create a GLES 3 context
    EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, nullptr, contextAttribs);

    // get some window metrics
    auto madeCurrent = eglMakeCurrent(display, surface, surface, context);
    assert(madeCurrent);

    display_ = display;
    surface_ = surface;
    context_ = context;

    // make width and height invalid so it gets updated the first frame in @a updateRenderArea()
    width_ = -1;
    height_ = -1;

    PRINT_GL_STRING(GL_VENDOR);
    PRINT_GL_STRING(GL_RENDERER);
    PRINT_GL_STRING(GL_VERSION);
    PRINT_GL_STRING_AS_LIST(GL_EXTENSIONS);

    shader_ = std::unique_ptr<Shader>(
            Shader::loadShader(vertex, fragment, "inPosition", "inUV", "uProjection"));
    assert(shader_);

    // Note: there's only one shader in this demo, so I'll activate it here. For a more complex game
    // you'll want to track the active shader and activate/deactivate it as necessary
    shader_->activate();

    // setup any other gl related global states
    glClearColor(CORNFLOWER_BLUE);

    // enable alpha globally for now, you probably don't want to do this in a game
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    // get some demo models into memory
    // setNumHeads(1000);
}

void Renderer::updateRenderArea() {
    EGLint width;
    eglQuerySurface(display_, surface_, EGL_WIDTH, &width);

    EGLint height;
    eglQuerySurface(display_, surface_, EGL_HEIGHT, &height);

    if (width != width_ || height != height_) {
        width_ = width;
        height_ = height;
        glViewport(0, 0, width, height);

        // make sure that we lazily recreate the projection matrix before we render
        shaderNeedsNewProjectionMatrix_ = true;
    }
}

void Renderer::addHead() {
    thread_local auto assetManager = app_->activity->assetManager;
    thread_local auto spAndroidRobotTexture = TextureAsset::loadAsset(assetManager, "android.png");
    thread_local std::vector<Vertex> vertices = {
            Vertex(Vector3{{0.3, 0.3, 0}}, Vector2{{0, 0}}),   // 0
            Vertex(Vector3{{-0.3, 0.3, 0}}, Vector2{{1, 0}}),  // 1
            Vertex(Vector3{{-0.3, -0.3, 0}}, Vector2{{1, 1}}), // 2
            Vertex(Vector3{{0.3, -0.3, 0}}, Vector2{{0, 1}})   // 3
    };
    thread_local std::vector<Index> indices = {0, 1, 2, 0, 2, 3};
    thread_local Model baseModel{vertices, indices, spAndroidRobotTexture};
    float angle = 2 * M_PI * (static_cast<float>(rand()) / static_cast<float>(RAND_MAX));
    float x = 1.5 * static_cast<float>(rand()) / static_cast<float>(RAND_MAX) - 0.75;
    float y = 3.0 * static_cast<float>(rand()) / static_cast<float>(RAND_MAX) - 1.5;
    Vector3 offset{{x, y, 0}};
    Model toAdd{baseModel};
    toAdd.move(offset);
    toAdd.setRotationOffset(angle);
    heads_.push_back(toAdd);
}

void Renderer::setNumHeads(int headCount) {
    if (headCount > heads_.size()) {
        int to_add = headCount - heads_.size();
        for (int i = 0; i < to_add; ++i) {
            addHead();
        }
    } else if (headCount < heads_.size()) {
        heads_.erase(heads_.begin() + headCount, heads_.end());
    }
}

bool Renderer::getAdpfSupported() {
    if (hintManager_ == nullptr) {
        hintManager_ = APerformanceHint_getManager();
    }
    long preferredRate = APerformanceHint_getPreferredUpdateRateNanos(hintManager_);
    results_["isHintSessionSupported"] = preferredRate < 0 ? "false" : "true";
    results_["preferredRate"] = std::to_string(preferredRate);
    return preferredRate >= 0;
}

void Renderer::startHintSession(std::vector<int32_t> &tids, int64_t target) {
    if (hintManager_ == nullptr) {
        hintManager_ = APerformanceHint_getManager();
    }
    if (hintSession_ == nullptr && hintManager_ != nullptr) {
        lastTarget_ = target;
        hintSession_ =
                APerformanceHint_createSession(hintManager_, tids.data(), tids.size(), target);
        if (hintSession_ == nullptr) {
            Utility::setFailure("Failed to create session", this);
        }
    }
}

void Renderer::reportActualWorkDuration(int64_t duration) {
    if (isHintSessionRunning()) {
        int ret = APerformanceHint_reportActualWorkDuration(hintSession_, duration);
        if (ret < 0) {
            Utility::setFailure("Failed to report actual work duration with code " +
                                        std::to_string(ret),
                                this);
        }
    }
}

void Renderer::updateTargetWorkDuration(int64_t target) {
    lastTarget_ = target;
    if (isHintSessionRunning()) {
        int ret = APerformanceHint_updateTargetWorkDuration(hintSession_, target);
        if (ret < 0) {
            Utility::setFailure("Failed to update target duration with code " + std::to_string(ret),
                                this);
        }
    }
}

int64_t Renderer::getTargetWorkDuration() {
    return lastTarget_;
}

bool Renderer::isHintSessionRunning() {
    return hintSession_ != nullptr;
}

void Renderer::closeHintSession() {
    APerformanceHint_closeSession(hintSession_);
}

void Renderer::addResult(std::string name, std::string value) {
    results_[name] = value;
}

std::map<std::string, std::string> &Renderer::getResults() {
    return results_;
}

void Renderer::setBaselineMedian(int64_t median) {
    baselineMedian_ = median;
}

template <typename T>
T getMedian(std::vector<T> values) {
    std::sort(values.begin(), values.end());
    return values[values.size() / 2];
}

FrameStats Renderer::getFrameStats(std::vector<int64_t> &durations, std::vector<int64_t> &intervals,
                                   std::string &testName) {
    FrameStats stats;
    // Double precision is int-precise up to 2^52 so we should be fine for this range
    double sum = std::accumulate(durations.begin(), durations.end(), 0);
    double mean = static_cast<double>(sum) / static_cast<double>(durations.size());
    int dropCount = 0;
    double varianceSum = 0;
    for (int64_t &duration : durations) {
        if (isHintSessionRunning() && duration > lastTarget_) {
            ++dropCount;
        }
        varianceSum += (duration - mean) * (duration - mean);
    }
    if (durations.size() > 0) {
        stats.medianWorkDuration = getMedian(durations);
    }
    if (intervals.size() > 0) {
        stats.medianFrameInterval = getMedian(intervals);
    }
    stats.deviation = std::sqrt(varianceSum / static_cast<double>(durations.size() - 1));
    if (isHintSessionRunning()) {
        stats.exceededCount = dropCount;
        stats.exceededFraction =
                static_cast<double>(dropCount) / static_cast<double>(durations.size());
        stats.efficiency = static_cast<double>(sum) /
                static_cast<double>(durations.size() * std::min(lastTarget_, baselineMedian_));
    }

    if (testName.size() > 0) {
        addResult(testName + "_median", std::to_string(stats.medianWorkDuration));
        addResult(testName + "_median_interval", std::to_string(stats.medianFrameInterval));
        addResult(testName + "_deviation", std::to_string(stats.deviation));
        if (isHintSessionRunning()) {
            addResult(testName + "_target", std::to_string(getTargetWorkDuration()));
            addResult(testName + "_target_exceeded_count", std::to_string(*stats.exceededCount));
            addResult(testName + "_target_exceeded_fraction",
                      std::to_string(*stats.exceededFraction));
            addResult(testName + "_efficiency", std::to_string(*stats.efficiency));
        }
    }

    return stats;
}
