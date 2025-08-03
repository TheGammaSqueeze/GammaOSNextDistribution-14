/*
 * Copyright (C) 2018-2020 The LineageOS Project
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

#ifdef LIVES_IN_SYSTEM
#define LOG_TAG "lineage.livedisplay@2.0-impl-sdm"
#else
#define LOG_TAG "vendor.lineage.livedisplay@2.0-impl-sdm"
#endif

#include "livedisplay/sdm/SDMController.h"

#include <android-base/logging.h>
#include <dlfcn.h>

#include <memory>

#define LOAD_SDM_FUNCTION(name) \
    fn_##name##_ = LoadFunction<disp_api_##name>(handle_.get(), "disp_api_" #name);

#define CLOSE_SDM_FUNCTION(name) fn_##name##_ = nullptr;

#define FOR_EACH_FUNCTION(MACRO)    \
    MACRO(init)                     \
    MACRO(deinit)                   \
    MACRO(get_num_display_modes)    \
    MACRO(get_display_modes)        \
    MACRO(get_active_display_mode)  \
    MACRO(set_active_display_mode)  \
    MACRO(set_default_display_mode) \
    MACRO(get_default_display_mode) \
    MACRO(get_global_pa_range)      \
    MACRO(get_global_pa_config)     \
    MACRO(set_global_pa_config)     \
    MACRO(get_feature_version)

#define CONTROLLER_CHECK(function, ...)      \
    if (fn_##function##_ == nullptr) {       \
        return ::android::NO_INIT;           \
    }                                        \
    int err = fn_##function##_(__VA_ARGS__); \
    if (err != 0) {                          \
        return ::android::BAD_VALUE;         \
    }                                        \
    return android::OK;

namespace {
constexpr const char* kSdmDispLibs[]{
#ifdef LIVES_IN_SYSTEM
        "libsdm-disp-apis.qti.so",
        "libsdm-disp-apis.so",
#else
        "libsdm-disp-vndapis.so",
#endif
};

constexpr const int kDisplayId = 0;

void* LoadDispApisLib() {
    void* handle = nullptr;

    for (auto&& lib : kSdmDispLibs) {
        handle = dlopen(lib, RTLD_NOW);
        if (handle != nullptr) {
            LOG(DEBUG) << "Loaded: " << lib;
            break;
        }
        LOG(INFO) << "Can not load " << lib << " (" << dlerror() << ")";
    }

    return handle;
}

template <typename Function>
Function LoadFunction(void* handle, const char* name) {
    void* fn = dlsym(handle, name);
    if (fn == nullptr) {
        LOG(ERROR) << "LoadFunction -- failed to load function " << name;
    }
    return reinterpret_cast<Function>(fn);
}
}  // anonymous namespace

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace sdm {

SDMController::SDMController()
    : handle_(LoadDispApisLib(), [this](void* p) {
          FOR_EACH_FUNCTION(CLOSE_SDM_FUNCTION)
          if (p != nullptr) {
              int err = dlclose(p);
              p = nullptr;
              if (err != 0) {
                  LOG(ERROR) << "Failed to close disp api lib.";
              }
          }
      }) {
    if (handle_ == nullptr) {
        // Terminate the program on failure
        LOG(FATAL) << "Failed to load SDM display lib, exiting. (" << dlerror() << ")";
    }

    FOR_EACH_FUNCTION(LOAD_SDM_FUNCTION)

    // Initialize SDM backend
    if (init() != android::OK) {
        // Terminate the program on failure
        LOG(FATAL) << "Failed to initialize SDM backend";
    }
}

SDMController::~SDMController() {
    deinit();
}

status_t SDMController::init() {
    CONTROLLER_CHECK(init, &hctx_, 0);
}

status_t SDMController::deinit() {
    CONTROLLER_CHECK(deinit, hctx_, 0);
}

status_t SDMController::getNumDisplayModes(int32_t* mode_cnt) {
    uint32_t flags = 0;
    CONTROLLER_CHECK(get_num_display_modes, hctx_, kDisplayId, 0, mode_cnt, &flags);
}

status_t SDMController::getDisplayModes(SdmDispMode* modes, int32_t mode_cnt) {
    uint32_t flags = 0;
    CONTROLLER_CHECK(get_display_modes, hctx_, kDisplayId, 0, modes, mode_cnt, &flags);
}

status_t SDMController::getActiveDisplayMode(int32_t* mode_id) {
    uint32_t mask = 0, flags = 0;
    CONTROLLER_CHECK(get_active_display_mode, hctx_, kDisplayId, mode_id, &mask, &flags);
}

status_t SDMController::setActiveDisplayMode(int32_t mode_id) {
    CONTROLLER_CHECK(set_active_display_mode, hctx_, kDisplayId, mode_id, 0);
}

status_t SDMController::setDefaultDisplayMode(int32_t mode_id) {
    CONTROLLER_CHECK(set_default_display_mode, hctx_, kDisplayId, mode_id, 0);
}

status_t SDMController::getDefaultDisplayMode(int32_t* mode_id) {
    uint32_t flags = 0;
    CONTROLLER_CHECK(get_default_display_mode, hctx_, kDisplayId, mode_id, &flags);
}

status_t SDMController::getGlobalPaRange(HsicRanges* range) {
    CONTROLLER_CHECK(get_global_pa_range, hctx_, kDisplayId, range);
}

status_t SDMController::getGlobalPaConfig(HsicConfig* cfg) {
    uint32_t enable = 0;
    CONTROLLER_CHECK(get_global_pa_config, hctx_, kDisplayId, &enable, cfg);
}

status_t SDMController::setGlobalPaConfig(HsicConfig* cfg) {
    CONTROLLER_CHECK(set_global_pa_config, hctx_, kDisplayId, 1 /* enable */, cfg);
}

status_t SDMController::getFeatureVersion(uint32_t feature_id, SdmFeatureVersion* ver) {
    uint32_t flags = 0;
    CONTROLLER_CHECK(get_feature_version, hctx_, feature_id, ver, &flags);
}

}  // namespace sdm
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
