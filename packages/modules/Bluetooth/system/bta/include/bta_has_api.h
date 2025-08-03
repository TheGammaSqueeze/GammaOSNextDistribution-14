/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <base/functional/callback.h>

#include <string>

#include "hardware/bt_has.h"

namespace le_audio {
namespace has {
class HasClient {
 public:
  virtual ~HasClient() = default;

  static void Initialize(bluetooth::has::HasClientCallbacks* callbacks,
                         base::Closure initCb);
  static void CleanUp();
  static HasClient* Get();
  static void DebugDump(int fd);
  static bool IsHasClientRunning();
  static void AddFromStorage(const RawAddress& addr, uint8_t features,
                             uint16_t is_acceptlisted);
  virtual void Connect(const RawAddress& addr) = 0;
  virtual void Disconnect(const RawAddress& addr) = 0;
  virtual void SelectActivePreset(
      std::variant<RawAddress, int> addr_or_group_id, uint8_t preset_index) = 0;
  virtual void NextActivePreset(
      std::variant<RawAddress, int> addr_or_group_id) = 0;
  virtual void PreviousActivePreset(
      std::variant<RawAddress, int> addr_or_group_id) = 0;
  virtual void GetPresetInfo(const RawAddress& addr, uint8_t preset_index) = 0;
  virtual void SetPresetName(std::variant<RawAddress, int> addr_or_group_id,
                             uint8_t preset_index, std::string name) = 0;
};

}  // namespace has
}  // namespace le_audio
