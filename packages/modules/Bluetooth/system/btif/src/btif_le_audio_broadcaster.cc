/*
 * Copyright 2019 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
 * www.ehima.com
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

#include <base/functional/bind.h>
#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_le_audio.h>

#include "bta_le_audio_api.h"
#include "bta_le_audio_broadcaster_api.h"
#include "btif_common.h"
#include "stack/include/main_thread.h"

using base::Bind;
using base::Unretained;
using bluetooth::le_audio::BroadcastId;
using bluetooth::le_audio::BroadcastState;
using bluetooth::le_audio::LeAudioBroadcasterCallbacks;
using bluetooth::le_audio::LeAudioBroadcasterInterface;

namespace {
class LeAudioBroadcasterInterfaceImpl;
std::unique_ptr<LeAudioBroadcasterInterface> leAudioBroadcasterInstance;

class LeAudioBroadcasterInterfaceImpl : public LeAudioBroadcasterInterface,
                                        public LeAudioBroadcasterCallbacks {
  ~LeAudioBroadcasterInterfaceImpl() override = default;

  void Initialize(LeAudioBroadcasterCallbacks* callbacks) override {
    this->callbacks_ = callbacks;
    do_in_main_thread(
        FROM_HERE,
        Bind(&LeAudioBroadcaster::Initialize, this, base::Bind([]() -> bool {
          return LeAudioHalVerifier::SupportsLeAudioBroadcast();
        })));
  }

  void CreateBroadcast(
      bool is_public, std::string broadcast_name,
      std::optional<bluetooth::le_audio::BroadcastCode> broadcast_code,
      std::vector<uint8_t> public_metadata,
      std::vector<uint8_t> subgroup_quality,
      std::vector<std::vector<uint8_t>> subgroup_metadata) override {
    do_in_main_thread(FROM_HERE, Bind(&LeAudioBroadcaster::CreateAudioBroadcast,
                                      Unretained(LeAudioBroadcaster::Get()),
                                      is_public, broadcast_name, broadcast_code,
                                      std::move(public_metadata),
                                      std::move(subgroup_quality),
                                      std::move(subgroup_metadata)));
  }

  void UpdateMetadata(
      uint32_t broadcast_id, std::string broadcast_name,
      std::vector<uint8_t> public_metadata,
      std::vector<std::vector<uint8_t>> subgroup_metadata) override {
    do_in_main_thread(FROM_HERE,
                      Bind(&LeAudioBroadcaster::UpdateMetadata,
                           Unretained(LeAudioBroadcaster::Get()), broadcast_id,
                           broadcast_name, std::move(public_metadata),
                           std::move(subgroup_metadata)));
  }

  void StartBroadcast(uint32_t broadcast_id) override {
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::StartAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void StopBroadcast(uint32_t broadcast_id) override {
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::StopAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void PauseBroadcast(uint32_t broadcast_id) override {
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::SuspendAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void DestroyBroadcast(uint32_t broadcast_id) override {
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::DestroyAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void GetBroadcastMetadata(uint32_t broadcast_id) override {
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::GetBroadcastMetadata,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void OnBroadcastCreated(uint32_t broadcast_id, bool success) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioBroadcasterCallbacks::OnBroadcastCreated,
                          Unretained(callbacks_), broadcast_id, success));
  }

  void OnBroadcastDestroyed(uint32_t broadcast_id) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioBroadcasterCallbacks::OnBroadcastDestroyed,
                          Unretained(callbacks_), broadcast_id));
  }

  void OnBroadcastStateChanged(uint32_t broadcast_id,
                               BroadcastState state) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioBroadcasterCallbacks::OnBroadcastStateChanged,
                          Unretained(callbacks_), broadcast_id, state));
  }

  void OnBroadcastMetadataChanged(uint32_t broadcast_id,
                                  const bluetooth::le_audio::BroadcastMetadata&
                                      broadcast_metadata) override {
    do_in_jni_thread(
        FROM_HERE,
        Bind(&LeAudioBroadcasterCallbacks::OnBroadcastMetadataChanged,
             Unretained(callbacks_), broadcast_id, broadcast_metadata));
  }

  void Stop(void) override {
    do_in_main_thread(FROM_HERE, Bind(&LeAudioBroadcaster::Stop));
  }

  void Cleanup(void) override {
    do_in_main_thread(FROM_HERE, Bind(&LeAudioBroadcaster::Cleanup));
  }

 private:
  LeAudioBroadcasterCallbacks* callbacks_;
};

} /* namespace */

LeAudioBroadcasterInterface* btif_le_audio_broadcaster_get_interface() {
  if (!leAudioBroadcasterInstance) {
    leAudioBroadcasterInstance.reset(new LeAudioBroadcasterInterfaceImpl());
  }

  return leAudioBroadcasterInstance.get();
}
