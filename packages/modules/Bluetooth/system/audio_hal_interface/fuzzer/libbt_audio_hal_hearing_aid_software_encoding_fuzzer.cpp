/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <fuzzer/FuzzedDataProvider.h>

#include "audio_hal_interface/hearing_aid_software_encoding.h"
#include "osi/include/properties.h"

constexpr int32_t kRandomStringLength = 256;

extern "C" {
struct android_namespace_t* android_get_exported_namespace(const char*) {
  return nullptr;
}
}

static void source_init_delayed(void) {}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fdp(data, size);
  osi_property_set("persist.bluetooth.a2dp_offload.disabled",
                   fdp.PickValueInArray({"true", "false"}));
  std::string name = fdp.ConsumeRandomLengthString(kRandomStringLength);
  bluetooth::common::MessageLoopThread messageLoopThread(name);
  messageLoopThread.StartUp();
  messageLoopThread.DoInThread(FROM_HERE, base::BindOnce(&source_init_delayed));

  uint16_t delay = fdp.ConsumeIntegral<uint16_t>();
  bluetooth::audio::hearing_aid::set_remote_delay(delay);

  messageLoopThread.ShutDown();
  return 0;
}
