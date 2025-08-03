/*
 * Copyright 2019 The Android Open Source Project
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

#include <stddef.h>
#include <stdint.h>
#include "fuzz/helpers.h"
#include "hci/acl_manager.h"
#include "hci/fuzz/fuzz_hci_layer.h"
#include "hci/hci_layer.h"
#include "module.h"
#include "os/fake_timer/fake_timerfd.h"
#include "os/log.h"

#include <fuzzer/FuzzedDataProvider.h>

using bluetooth::FuzzTestModuleRegistry;
using bluetooth::fuzz::GetArbitraryBytes;
using bluetooth::hci::AclManager;
using bluetooth::hci::HciLayer;
using bluetooth::hci::fuzz::FuzzHciLayer;
using bluetooth::os::fake_timer::fake_timerfd_advance;
using bluetooth::os::fake_timer::fake_timerfd_cap_at;
using bluetooth::os::fake_timer::fake_timerfd_reset;

constexpr int32_t kMinTimeAdvanced = 0;
/**
 * kMaxTotalTimeAdvanced value is referenced from
 * kDefaultConfigSaveDelay defined in storage_module.cc
 */
constexpr int32_t kMaxTotalTimeAdvanced = 3000;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider dataProvider(data, size);

  static FuzzTestModuleRegistry moduleRegistry = FuzzTestModuleRegistry();
  FuzzHciLayer* fuzzHci = moduleRegistry.Inject<FuzzHciLayer>(&HciLayer::Factory);
  fuzzHci->TurnOnAutoReply(&dataProvider);
  moduleRegistry.Start<AclManager>();
  fuzzHci->TurnOffAutoReply();
  uint64_t totalAdvanceTime = 0;

  while (dataProvider.remaining_bytes() > 0) {
    const uint8_t action = dataProvider.ConsumeIntegralInRange(0, 2);

    switch (action) {
      case 1: {
        uint64_t advanceTime = dataProvider.ConsumeIntegralInRange<uint64_t>(kMinTimeAdvanced, kMaxTotalTimeAdvanced);
        totalAdvanceTime += advanceTime;
        if (totalAdvanceTime < kMaxTotalTimeAdvanced) {
          fake_timerfd_advance(advanceTime);
        }
        break;
      }
      case 2: {
        fuzzHci->injectArbitrary(dataProvider);
        break;
      }
    }
  }

  moduleRegistry.WaitForIdleAndStopAll();
  fake_timerfd_reset();
  return 0;
}
