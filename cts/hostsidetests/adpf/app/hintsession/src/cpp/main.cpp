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

#include <android/performance_hint.h>
#include <assert.h>
#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <functional>
#include <map>
#include <random>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "AndroidOut.h"
#include "JNIManager.h"
#include "Renderer.h"
#include "Utility.h"

using namespace std::chrono_literals;

const constexpr auto kDrawingTimeout = 3s;
const constexpr int kSamples = 800;
const constexpr int kCalibrationSamples = 800;

Renderer *getRenderer(android_app *pApp) {
    return (pApp->userData) ? reinterpret_cast<Renderer *>(pApp->userData) : nullptr;
}

// Converts lists of numbers into strings, so they can be
// passed up to the Java code the results map.
template <typename T>
std::string serializeValues(const std::vector<T> &values) {
    std::stringstream stream;
    for (auto &&value : values) {
        stream << value;
        stream << ",";
    }
    std::string out = stream.str();
    out.pop_back(); // remove the last comma
    return out;
}

// Generalizes the loop used to draw frames so that it can be easily started and stopped
// back to back with different parameters, or after adjustments such as target time adjustments.
FrameStats drawFrames(int count, android_app *pApp, int &events, android_poll_source *&pSource,
                      std::string testName = "") {
    bool namedTest = testName.size() > 0;
    std::vector<int64_t> durations{};
    std::vector<int64_t> intervals{};

    auto drawStart = std::chrono::steady_clock::now();
    // Iter is -1 so we have a buffer frame before it starts, to eat any delay from time spent
    // between tests
    for (int iter = -1; iter < count && !pApp->destroyRequested;) {
        if (std::chrono::steady_clock::now() - drawStart > kDrawingTimeout) {
            aout << "Stops drawing on " << kDrawingTimeout.count() << "s timeout for test "
                 << (namedTest ? testName : "unnamed") << std::endl;
            break;
        }
        int retval = ALooper_pollOnce(0, nullptr, &events, (void **)&pSource);
        while (retval == ALOOPER_POLL_CALLBACK) {
            retval = ALooper_pollOnce(0, nullptr, &events, (void **)&pSource);
        }
        if (retval >= 0 && pSource) {
            pSource->process(pApp, pSource);
        }
        if (pApp->userData) {
            // Don't add metrics for buffer frames
            if (iter > -1) {
                thread_local auto lastStart = std::chrono::steady_clock::now();
                auto start = std::chrono::steady_clock::now();

                // Render a frame
                jlong spinTime = getRenderer(pApp)->render();
                getRenderer(pApp)->reportActualWorkDuration(spinTime);
                durations.push_back(spinTime);
                intervals.push_back((start - lastStart).count());
                lastStart = start;
            }
            ++iter;
        }
    }

    if (namedTest) {
        getRenderer(pApp)->addResult(testName + "_durations", serializeValues(durations));
        getRenderer(pApp)->addResult(testName + "_intervals", serializeValues(intervals));
    }

    return getRenderer(pApp)->getFrameStats(durations, intervals, testName);
}

FrameStats drawFramesWithTarget(int64_t targetDuration, int &events, android_app *pApp,
                                android_poll_source *&pSource, std::string testName = "") {
    getRenderer(pApp)->updateTargetWorkDuration(targetDuration);
    return drawFrames(kSamples, pApp, events, pSource, testName);
}

// Finds the test settings that best match this device, and returns the
// duration of the frame's work
double calibrate(int &events, android_app *pApp, android_poll_source *&pSource) {
    FrameStats calibration[2];
    getRenderer(pApp)->setNumHeads(1);

    // Find a number of heads that gives a work duration approximately equal
    // to 1/4 the vsync period. This gives enough time for the frame to finish
    // everything, while still providing enough overhead that differences are easy
    // to notice.
    calibration[0] = drawFrames(kCalibrationSamples, pApp, events, pSource);
    getRenderer(pApp)->setNumHeads(200);
    calibration[1] = drawFrames(kCalibrationSamples, pApp, events, pSource);

    double target = calibration[1].medianFrameInterval / 6.0;
    aout << "Goal duration: " << (int)target << std::endl;
    double perHeadDuration =
            (calibration[1].medianWorkDuration - calibration[0].medianWorkDuration) / 200.0;
    aout << "per-head duration: " << (int)perHeadDuration << std::endl;
    int heads = (target - static_cast<double>(calibration[0].medianWorkDuration)) / perHeadDuration;

    getRenderer(pApp)->addResult("goal_duration", std::to_string(static_cast<int>(target)));
    getRenderer(pApp)->addResult("heads_count", std::to_string(heads));

    getRenderer(pApp)->setNumHeads(std::max(heads, 1));
    return target;
}

// /*!
//  * Handles commands sent to this Android application
//  * @param pApp the app the commands are coming from
//  * @param cmd the command to handle
//  */
void handle_cmd(android_app *pApp, int32_t cmd) {
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            pApp->userData = new Renderer(pApp);
            break;
        case APP_CMD_TERM_WINDOW:
            // The window is being destroyed. Use this to clean up your userData to avoid leaking
            // resources.
            //
            // We have to check if userData is assigned just in case this comes in really quickly
            if (pApp->userData) {
                auto *pRenderer = getRenderer(pApp);
                Utility::setFailure("App was closed while running!", pRenderer);
            }
            break;
        default:
            break;
    }
}

void android_main(struct android_app *pApp) {
    app_dummy();

    // Register an event handler for Android events
    pApp->onAppCmd = handle_cmd;

    JNIManager &manager = JNIManager::getInstance();
    manager.setApp(pApp);

    int events;
    android_poll_source *pSource = nullptr;

    // Ensure renderer is initialized
    drawFrames(1, pApp, events, pSource);

    bool supported = getRenderer(pApp)->getAdpfSupported();

    if (!supported) {
        JNIManager::sendResultsToJava(getRenderer(pApp)->getResults());
        return;
    }

    std::this_thread::sleep_for(10s);
    getRenderer(pApp)->setNumHeads(100);
    // Run an initial load to get the CPU active and stable
    drawFrames(kCalibrationSamples, pApp, events, pSource);

    FrameStats initialStats = drawFrames(kSamples, pApp, events, pSource);

    std::vector<pid_t> tids;
    tids.push_back(gettid());
    getRenderer(pApp)->startHintSession(tids, 6 * initialStats.medianWorkDuration);
    if (!getRenderer(pApp)->isHintSessionRunning()) {
        Utility::setFailure("Session failed to start!", getRenderer(pApp));
    }

    // Do an initial load with the session to let CPU settle
    drawFrames(kCalibrationSamples / 2, pApp, events, pSource);

    double calibratedTarget = calibrate(events, pApp, pSource);

    auto testNames = JNIManager::getInstance().getTestNames();
    std::set<std::string> testSet{testNames.begin(), testNames.end()};
    std::vector<std::function<void()>> tests;

    FrameStats baselineStats = drawFrames(kSamples, pApp, events, pSource, "baseline");

    double calibrationAccuracy = 1.0 -
            (abs(static_cast<double>(baselineStats.medianWorkDuration) - calibratedTarget) /
             calibratedTarget);
    getRenderer(pApp)->addResult("calibration_accuracy", std::to_string(calibrationAccuracy));

    const int64_t lightTarget = 6 * baselineStats.medianWorkDuration;

    // Used to figure out efficiency score on actual runs
    getRenderer(pApp)->setBaselineMedian(baselineStats.medianWorkDuration);

    // Set heavy target to be slightly smaller than the baseline to ensure a boost is necessary
    const int64_t heavyTarget = (3 * baselineStats.medianWorkDuration) / 4;

    if (testSet.count("heavy_load") > 0) {
        tests.push_back(
                [&]() { drawFramesWithTarget(heavyTarget, events, pApp, pSource, "heavy_load"); });
    }

    if (testSet.count("light_load") > 0) {
        tests.push_back(
                [&]() { drawFramesWithTarget(lightTarget, events, pApp, pSource, "light_load"); });
    }

    if (testSet.count("transition_load") > 0) {
        tests.push_back([&]() {
            drawFramesWithTarget(lightTarget, events, pApp, pSource, "transition_load_1");
            drawFramesWithTarget(heavyTarget, events, pApp, pSource, "transition_load_2");
            drawFramesWithTarget(lightTarget, events, pApp, pSource, "transition_load_3");
        });
    }

    std::shuffle(tests.begin(), tests.end(), std::default_random_engine{});

    for (auto &test : tests) {
        test();
    }

    JNIManager::sendResultsToJava(getRenderer(pApp)->getResults());
}
