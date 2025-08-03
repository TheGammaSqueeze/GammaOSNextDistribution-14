/*
 * Copyright 2021 The Android Open Source Project
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

#include <base/functional/callback.h>

#include "bta_le_audio_api.h"

class LeAudioClientImpl : public LeAudioClient {
 public:
  LeAudioClientImpl(void) = default;
  ~LeAudioClientImpl(void) override = default;

  void RemoveDevice(const RawAddress& address) override {}
  void Connect(const RawAddress& address) override {}
  void Disconnect(const RawAddress& address) override {}
  void GroupAddNode(const int group_id, const RawAddress& addr) override {}
  void GroupRemoveNode(const int group_id, const RawAddress& addr) override {}
  void GroupStream(const int group_id, const uint16_t content_type) override {}
  void GroupSuspend(const int group_id) override {}
  void GroupStop(const int group_id) override {}
  void GroupDestroy(const int group_id) override {}
  void GroupSetActive(const int group_id) override {}
  void SetCodecConfigPreference(
      int group_id,
      bluetooth::le_audio::btle_audio_codec_config_t input_codec_config,
      bluetooth::le_audio::btle_audio_codec_config_t output_codec_config)
      override {}
  void SetCcidInformation(int ccid, int context_type) override {}
  void SetInCall(bool in_call) override {}
  std::vector<RawAddress> GetGroupDevices(const int group_id) override {
    return {};
  }
};

void LeAudioClient::Initialize(
    bluetooth::le_audio::LeAudioClientCallbacks* callbacks,
    base::Closure initCb, base::Callback<bool()> hal_2_1_verifier,
    const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>&
        offloading_preference) {}
void LeAudioClient::Cleanup(void) {}
LeAudioClient* LeAudioClient::Get(void) { return nullptr; }
void LeAudioClient::DebugDump(int fd) {}
void LeAudioClient::AddFromStorage(const RawAddress& addr, bool autoconnect,
                                   int sink_audio_location,
                                   int source_audio_location,
                                   int sink_supported_context_types,
                                   int source_supported_context_types,
                                   const std::vector<uint8_t>& handles,
                                   const std::vector<uint8_t>& sink_pacs,
                                   const std::vector<uint8_t>& source_pacs,
                                   const std::vector<uint8_t>& ases) {}
bool LeAudioClient::GetHandlesForStorage(const RawAddress& addr,
                                         std::vector<uint8_t>& out) {
  return false;
}
bool LeAudioClient::GetSinkPacsForStorage(const RawAddress& addr,
                                          std::vector<uint8_t>& out) {
  return false;
}
bool LeAudioClient::GetSourcePacsForStorage(const RawAddress& addr,
                                            std::vector<uint8_t>& out) {
  return false;
}
bool LeAudioClient::GetAsesForStorage(const RawAddress& addr,
                                      std::vector<uint8_t>& out) {
  return false;
}
bool LeAudioClient::IsLeAudioClientRunning() { return false; }
bool LeAudioClient::IsLeAudioClientInStreaming() { return false; }
