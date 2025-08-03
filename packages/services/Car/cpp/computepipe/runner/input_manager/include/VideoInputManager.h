// Copyright 2020 The Android Open Source Project
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
#ifndef COMPUTEPIPE_RUNNER_INPUT_MANAGER_INCLUDE_VIDEOINPUTMANAGER_H_
#define COMPUTEPIPE_RUNNER_INPUT_MANAGER_INCLUDE_VIDEOINPUTMANAGER_H_

#include <android-base/logging.h>

#include <vector>

#include "InputManager.h"
#include "VideoDecoder.h"
#include "types/Status.h"

namespace android {
namespace automotive {
namespace computepipe {
namespace runner {
namespace input_manager {

class VideoInputManager : public InputManager {
public:
    explicit VideoInputManager(const proto::InputConfig& inputConfig,
                               const proto::InputConfig& overrideConfig,
                               std::shared_ptr<InputEngineInterface> inputEngineInterface);

    ~VideoInputManager();

    static std::unique_ptr<VideoInputManager> createVideoInputManager(
            const proto::InputConfig& inputConfig, const proto::InputConfig& overrideConfig,
            std::shared_ptr<InputEngineInterface> inputEngineInterface);

    Status handleExecutionPhase(const RunnerEvent& e) override;

    Status handleStopImmediatePhase(const RunnerEvent& e) override;

    Status handleStopWithFlushPhase(const RunnerEvent& e) override;

    Status handleResetPhase(const RunnerEvent& e) override;

private:
    void populateDecoders();
    Status startDecoders();
    void resetDecoders();
    std::shared_ptr<InputEngineInterface> mEngine;

    proto::InputConfig mInputConfig;
    std::vector<std::unique_ptr<VideoDecoder>> mVideoDecoders;
};

}  // namespace input_manager
}  // namespace runner
}  // namespace computepipe
}  // namespace automotive
}  // namespace android

#endif  // COMPUTEPIPE_RUNNER_INPUT_MANAGER_INCLUDE_VIDEOINPUTMANAGER_H_
