/*
 * Copyright 2020 The Android Open Source Project
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

#include "Common.h"
#include "DefaultEngine.h"
#include "VideoInputManager.h"

#include <android-base/file.h>
#include <fuzzer/FuzzedDataProvider.h>
#include <gmock/gmock.h>

#include <stdio.h>
#include <unistd.h>

namespace android {
namespace automotive {
namespace computepipe {
namespace runner {
namespace input_manager {

using android::base::Dirname;

namespace {

class MockRunnerEvent : public RunnerEvent {
public:
    MOCK_CONST_METHOD0(isPhaseEntry, bool());
    MOCK_CONST_METHOD0(isTransitionComplete, bool());
    MOCK_CONST_METHOD0(isAborted, bool());
    MOCK_METHOD1(dispatchToComponent,
                 Status(const std::shared_ptr<RunnerComponentInterface>& iface));
};

enum INPUT_MGR_FUZZ_FUNCS { RUNNER_COMP_BASE_ENUM };

static std::shared_ptr<VideoInputManager> manager;

extern "C" int LLVMFuzzerInitialize(int* argc, char*** argv) {
    const std::string kFilename =
            Dirname(*argv[0]) + "/data/corpus/video_input_manager/centaur_1.mpg";
    if (access(kFilename.c_str(), F_OK) == -1) {
        std::cerr << "Video file does not exist!" << std::endl;
        exit(1);
    }

    // Initialize input config
    proto::InputConfig inputConf;
    proto::InputStreamConfig* streamConfig = inputConf.add_input_stream();
    proto::VideoFileConfig* videoConfig = streamConfig->mutable_video_config();
    videoConfig->set_file_path(kFilename);

    // Initialize callBack, which does nothing
    std::shared_ptr<InputEngineInterface> callBack = std::make_shared<engine::InputCallback>(
            0, [](int i) {},
            [](int i, int64_t j, const InputFrame& frame) { return Status::SUCCESS; });

    // Initialize manager
    manager = std::make_shared<VideoInputManager>(inputConf, inputConf /* redundant */, callBack);
    return 0;
}

// TODO(b/163138279, b/163138595) verify the fix for these two bugs
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);
    while (fdp.remaining_bytes() > test::kMaxFuzzerConsumedBytes) {
        switch (fdp.ConsumeIntegralInRange<uint32_t>(0, API_SUM - 1)) {
            case HANDLE_CONFIG_PHASE: {
                break;
            }
            case HANDLE_EXECUTION_PHASE: {
                testing::NiceMock<MockRunnerEvent> e;
                bool isTransitionComplete = fdp.ConsumeBool();
                bool isPhaseEntry = fdp.ConsumeBool();
                if (isTransitionComplete != isPhaseEntry) {
                    if (isTransitionComplete) {
                        EXPECT_CALL((e), isTransitionComplete()).WillOnce([isTransitionComplete]() {
                            return isTransitionComplete;
                        });
                        ON_CALL((e), isPhaseEntry()).WillByDefault([isPhaseEntry]() {
                            return isPhaseEntry;
                        });
                    } else if (isPhaseEntry) {
                        ON_CALL((e), isTransitionComplete())
                                .WillByDefault(
                                        [isTransitionComplete]() { return isTransitionComplete; });
                        EXPECT_CALL((e), isPhaseEntry()).WillOnce([isPhaseEntry]() {
                            return isPhaseEntry;
                        });
                    }
                    Status res = manager->handleExecutionPhase(e);
                    if (res == Status::SUCCESS && !isTransitionComplete && isPhaseEntry) {
                        sleep(3);  // waiting for resource to be released
                    } else {
                        usleep(10);
                    }
                }
                break;
            }
            case HANDLE_STOP_IMMEDIATE_PHASE: {
                testing::NiceMock<MockRunnerEvent> e;
                manager->handleStopImmediatePhase(e);
                break;
            }
            case HANDLE_STOP_WITH_FLUSH_PHASE: {
                testing::NiceMock<MockRunnerEvent> e;
                manager->handleStopWithFlushPhase(e);
                break;
            }
            case HANDLE_RESET_PHASE: {
                testing::NiceMock<MockRunnerEvent> e;
                manager->handleResetPhase(e);
                break;
            }
            default:
                LOG(ERROR) << "Unexpected option aborting...";
                break;
        }
    }
    return 0;
}

}  // namespace
}  // namespace input_manager
}  // namespace runner
}  // namespace computepipe
}  // namespace automotive
}  // namespace android
