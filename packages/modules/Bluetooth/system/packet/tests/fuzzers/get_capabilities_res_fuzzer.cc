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

#include <fuzzer/FuzzedDataProvider.h>
#include <gtest/gtest.h>
#include <stdlib.h>
#include <string.h>

#include "avrcp_test_packets.h"
#include "capabilities_packet.h"
#include "packet_test_helper.h"

namespace bluetooth {
namespace avrcp {

// Test parsing a GetCapabilities Request
extern "C" int LLVMFuzzerTestOneInput(uint8_t* data, size_t size) {
  FuzzedDataProvider data_provider(data, size);
  auto builder = GetCapabilitiesResponseBuilder::MakeCompanyIdBuilder(
      data_provider.ConsumeIntegral<uint32_t>());
  builder->AddCompanyId(data_provider.ConsumeIntegral<uint32_t>());
  builder->AddCompanyId(data_provider.ConsumeIntegral<uint32_t>());

  builder = GetCapabilitiesResponseBuilder::MakeEventsSupportedBuilder(
      Event::PLAYBACK_STATUS_CHANGED);
  builder->AddEvent(Event::TRACK_CHANGED);
  builder->AddEvent(Event::PLAYBACK_POS_CHANGED);

  return 0;
}

}  // namespace avrcp
}  // namespace bluetooth
