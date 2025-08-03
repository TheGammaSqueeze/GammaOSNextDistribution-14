// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <android-base/logging.h>

#include "EvsInputManager.h"
#include "InputManager.h"
#include "VideoInputManager.h"

namespace android {
namespace automotive {
namespace computepipe {
namespace runner {
namespace input_manager {
namespace {

enum InputManagerType {
    EVS = 0,
    IMAGES,
    VIDEO,
};

// Helper function to determine the type of input manager to be created from the
// input config.
// TODO(b/147803315): Implement the actual algorithm to determine the input manager to be
// used. Right now, only EVS manager is enabled, so that is used.
InputManagerType getInputManagerType(const proto::InputStreamConfig& streamConfig) {
    switch (streamConfig.type()) {
        case proto::InputStreamConfig::CAMERA:
            return InputManagerType::EVS;
        case proto::InputStreamConfig::IMAGE_FILES:
            return InputManagerType::IMAGES;
        case proto::InputStreamConfig::VIDEO_FILE:
            return InputManagerType::VIDEO;
    }
}

}  // namespace
std::unique_ptr<InputManager> InputManagerFactory::createInputManager(
        const proto::InputConfig& config, const proto::InputConfig& overrideConfig,
        std::shared_ptr<InputEngineInterface> inputEngineInterface) {
    // Check that all streams in the config have same type.
    for (int i = 1; i < config.input_stream_size(); i++) {
        if (config.input_stream(i).type() != config.input_stream(0).type()) {
            LOG(ERROR) << "Invalid input configuration with config id " << config.config_id()
                       << ". All streams must have same type.";
            return nullptr;
        }
    }
    InputManagerType inputManagerType = getInputManagerType(config.input_stream(0));
    std::unique_ptr<InputManager> inputManager = nullptr;
    switch (inputManagerType) {
        case InputManagerType::EVS:
            inputManager = EvsInputManager::createEvsInputManager(config, overrideConfig,
                                                                  inputEngineInterface);
            break;
        case InputManagerType::VIDEO:
            inputManager = VideoInputManager::createVideoInputManager(config, overrideConfig,
                                                                      inputEngineInterface);
            break;
        default:
            return nullptr;
    }
    return inputManager;
}

}  // namespace input_manager
}  // namespace runner
}  // namespace computepipe
}  // namespace automotive
}  // namespace android
