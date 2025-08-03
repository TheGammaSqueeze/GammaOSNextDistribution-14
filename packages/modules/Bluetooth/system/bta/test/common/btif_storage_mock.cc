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

#include "btif_storage_mock.h"

#include <base/logging.h>

static bluetooth::storage::MockBtifStorageInterface* btif_storage_interface =
    nullptr;

void bluetooth::storage::SetMockBtifStorageInterface(
    MockBtifStorageInterface* mock_btif_storage_interface) {
  btif_storage_interface = mock_btif_storage_interface;
}

void btif_storage_set_leaudio_autoconnect(RawAddress const& addr,
                                          bool autoconnect) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->AddLeaudioAutoconnect(addr, autoconnect);
}

void btif_storage_leaudio_update_pacs_bin(const RawAddress& addr) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->LeAudioUpdatePacs(addr);
}

void btif_storage_leaudio_update_ase_bin(const RawAddress& addr) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->LeAudioUpdateAses(addr);
}

void btif_storage_leaudio_update_handles_bin(const RawAddress& addr) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->LeAudioUpdateHandles(addr);
}

void btif_storage_set_leaudio_audio_location(const RawAddress& addr,
                                             uint32_t sink_location,
                                             uint32_t source_location) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->SetLeAudioLocations(addr, sink_location,
                                              source_location);
}

void btif_storage_set_leaudio_supported_context_types(
    const RawAddress& addr, uint16_t sink_supported_context_type,
    uint16_t source_supported_context_type) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->SetLeAudioContexts(addr, sink_supported_context_type,
                                             source_supported_context_type);
}

void btif_storage_leaudio_clear_service_data(RawAddress const& addr) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->ClearLeAudioServiceData(addr);
}

void btif_storage_remove_leaudio(RawAddress const& addr) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->RemoveLeaudio(addr);
}

void btif_storage_add_leaudio_has_device(const RawAddress& address,
                                         std::vector<uint8_t> presets_bin,
                                         uint8_t features,
                                         uint8_t active_preset) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->AddLeaudioHasDevice(address, presets_bin, features,
                                              active_preset);
};

bool btif_storage_get_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t>& presets_bin,
                                          uint8_t& active_preset) {
  if (btif_storage_interface)
    return btif_storage_interface->GetLeaudioHasPresets(address, presets_bin,
                                                        active_preset);

  return false;
};

void btif_storage_set_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t> presets_bin) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->SetLeaudioHasPresets(address, presets_bin);
}

bool btif_storage_get_leaudio_has_features(const RawAddress& address,
                                           uint8_t& features) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  return btif_storage_interface->GetLeaudioHasFeatures(address, features);
}

void btif_storage_set_leaudio_has_features(const RawAddress& address,
                                           uint8_t features) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->SetLeaudioHasFeatures(address, features);
}

void btif_storage_set_leaudio_has_active_preset(const RawAddress& address,
                                                uint8_t active_preset) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->SetLeaudioHasActivePreset(address, active_preset);
}

void btif_storage_remove_leaudio_has(const RawAddress& address) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  btif_storage_interface->RemoveLeaudioHas(address);
}

bt_status_t btif_storage_get_remote_device_property(const RawAddress* address,
                                                    bt_property_t* property) {
  LOG_ASSERT(btif_storage_interface) << "Mock storage module not set!";
  return btif_storage_interface->GetRemoteDeviceProperty(address, property);
}