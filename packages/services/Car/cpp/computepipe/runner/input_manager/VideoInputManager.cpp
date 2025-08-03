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
#include <android-base/logging.h>

#include <fcntl.h>
#include <chrono>

#include "VideoInputManager.h"

namespace android {
namespace automotive {
namespace computepipe {
namespace runner {
namespace input_manager {

VideoInputManager::VideoInputManager(const proto::InputConfig& inputConfig,
                                     const proto::InputConfig& /*overrideConfig*/,
                                     std::shared_ptr<InputEngineInterface> inputEngineInterface) :
      mEngine(inputEngineInterface),
      mInputConfig(inputConfig) {}

VideoInputManager::~VideoInputManager() {}

std::unique_ptr<VideoInputManager> VideoInputManager::createVideoInputManager(
        const proto::InputConfig& inputConfig, const proto::InputConfig& overrideConfig,
        std::shared_ptr<InputEngineInterface> inputEngineInterface) {
    return std::make_unique<VideoInputManager>(inputConfig, overrideConfig, inputEngineInterface);
}

Status VideoInputManager::handleExecutionPhase(const RunnerEvent& e) {
    if (e.isTransitionComplete()) {
        return Status::SUCCESS;
    }

    if (e.isPhaseEntry()) {
        populateDecoders();

        if (mVideoDecoders.empty() || mVideoDecoders.size() != mInputConfig.input_stream_size()) {
            resetDecoders();
            LOG(ERROR) << "Created " << mVideoDecoders.size() << " video decoders, while expecting "
                       << mInputConfig.input_stream_size() << " video decoders.";
            return Status::INTERNAL_ERROR;
        }
        Status status = startDecoders();
        if (status != Status::SUCCESS) {
            resetDecoders();
        }
        return status;
    }
    // e.isPhaseAborted();
    resetDecoders();
    return Status::SUCCESS;
}

Status VideoInputManager::handleStopImmediatePhase(const RunnerEvent& /*e*/) {
    resetDecoders();
    return Status::SUCCESS;
}

Status VideoInputManager::handleStopWithFlushPhase(const RunnerEvent& /*e*/) {
    resetDecoders();
    return Status::SUCCESS;
}

Status VideoInputManager::handleResetPhase(const RunnerEvent& /*e*/) {
    resetDecoders();
    return Status::SUCCESS;
}

void VideoInputManager::populateDecoders() {
    for (const auto& config : mInputConfig.input_stream()) {
        if (config.has_video_config() && config.video_config().has_file_path() &&
            !config.video_config().file_path().empty()) {
            mVideoDecoders.emplace_back(std::make_unique<VideoDecoder>(config, mEngine));
        }
    }
}

Status VideoInputManager::startDecoders() {
    if (mVideoDecoders.empty()) {
        return Status::INTERNAL_ERROR;
    }

    float decoder0Rate = mVideoDecoders[0]->getPlaybackFrameRate();
    for (int i = 1; i < mVideoDecoders.size(); i++) {
        float currentDecoderRate = mVideoDecoders[i]->getPlaybackFrameRate();
        if (std::fabs(decoder0Rate - currentDecoderRate) >  std::numeric_limits<float>::epsilon()) {
            LOG(ERROR) << "Different playback frame rate between streams (0, " << i
                       << "), frame rates - " << decoder0Rate << ", " << currentDecoderRate;
            return Status::INTERNAL_ERROR;
        }
    }
    auto timePoint = std::chrono::system_clock::now();
    int64_t timestamp = std::chrono::time_point_cast<std::chrono::microseconds>(timePoint)
                                .time_since_epoch()
                                .count();
    LOG(ERROR) << "Initial time is " << timestamp;
    for (int i = 0; i < mVideoDecoders.size(); i++) {
        mVideoDecoders[i]->setInitialTimestamp(timestamp);
        Status status = mVideoDecoders[i]->startDecoding();
        if (status != Status::SUCCESS) {
            return status;
        }
    }
    return Status::SUCCESS;
}

void VideoInputManager::resetDecoders() {
    for (int i = 0; i < mVideoDecoders.size(); i++) {
        mVideoDecoders[i]->stopDecoding();
    }
    mVideoDecoders.clear();
}

}  // namespace input_manager
}  // namespace runner
}  // namespace computepipe
}  // namespace automotive
}  // namespace android
