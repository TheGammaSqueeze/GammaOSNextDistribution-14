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
#pragma once

#include <EGL/egl.h>
#include <android/performance_hint.h>
#include <jni.h>

#include <chrono>
#include <map>
#include <memory>
#include <optional>

#include "Model.h"
#include "Shader.h"
#include "external/android_native_app_glue.h"

struct android_app;

struct FrameStats {
    // Median of the durations
    int64_t medianWorkDuration;
    // Median of the intervals
    int64_t medianFrameInterval;
    // Standard deviation of a given run
    double deviation;
    // The total number of frames that exceeded target
    std::optional<int64_t> exceededCount;
    // The percent of frames that exceeded target
    std::optional<double> exceededFraction;
    // Efficiency of a given run is calculated by how close to min(target, baseline) the median is
    std::optional<double> efficiency;
};

class Renderer {
public:
    /*!
     * @param pApp the android_app this Renderer belongs to, needed to configure GL
     */
    inline Renderer(android_app *pApp)
          : app_(pApp),
            display_(EGL_NO_DISPLAY),
            surface_(EGL_NO_SURFACE),
            context_(EGL_NO_CONTEXT),
            width_(0),
            height_(0),
            shaderNeedsNewProjectionMatrix_(true) {
        initRenderer();
    }

    virtual ~Renderer();

    /*!
     * Renders all the models in the renderer, returns time spent waiting for CPU work
     * to finish.
     */
    jlong render();

    void startHintSession(std::vector<pid_t> &threads, int64_t target);
    void closeHintSession();
    void reportActualWorkDuration(int64_t duration);
    void updateTargetWorkDuration(int64_t target);
    bool isHintSessionRunning();
    int64_t getTargetWorkDuration();

    /*!
     * Sets the number of android "heads" in the scene, these are used to create a synthetic
     * workload that scales with performance, and by adjusting the number of them, the test can
     * adjust the amount of stress to place the system under.
     */
    void setNumHeads(int headCount);

    /*!
     * Adds an entry to the final result map that gets passed up to the Java side of the app, and
     * eventually to the test runner.
     */
    void addResult(std::string name, std::string value);

    /*!
     * Retrieve the results map.
     */
    std::map<std::string, std::string> &getResults();

    /*!
     * Informs the test whether ADPF is supported on a given device.
     */
    bool getAdpfSupported();

    /*
     * Finds the test settings that best match this device, and returns the
     * duration of the frame's work
     */
    double calibrate(int &events, android_poll_source *pSource);

    /*!
     * Sets the baseline median, used to determine efficiency score
     */
    void setBaselineMedian(int64_t median);

    /*!
     * Calculates the above frame stats for a given run
     */
    FrameStats getFrameStats(std::vector<int64_t> &durations, std::vector<int64_t> &intervals,
                             std::string &testName);

private:
    /*!
     * Performs necessary OpenGL initialization. Customize this if you want to change your EGL
     * context or application-wide settings.
     */
    void initRenderer();

    /*!
     * @brief we have to check every frame to see if the framebuffer has changed in size. If it has,
     * update the viewport accordingly
     */
    void updateRenderArea();

    /*!
     * Adds an android "head" to the scene.
     */
    void addHead();

    android_app *app_;
    EGLDisplay display_;
    EGLSurface surface_;
    EGLContext context_;
    EGLint width_;
    EGLint height_;
    APerformanceHintSession *hintSession_ = nullptr;
    APerformanceHintManager *hintManager_ = nullptr;
    int64_t lastTarget_ = 0;
    int64_t baselineMedian_ = 0;

    bool shaderNeedsNewProjectionMatrix_;

    std::unique_ptr<Shader> shader_;
    std::vector<Model> heads_;

    // Hold on to the results object in the renderer, so
    // we can reach the data anywhere in the rendering step.
    std::map<std::string, std::string> results_;
};
