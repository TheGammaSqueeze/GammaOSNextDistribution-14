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

#include <gmock/gmock.h>

#include "include/hardware/bluetooth.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace storage {

class BtifStorageInterface {
 public:
  virtual void AddLeaudioAutoconnect(RawAddress const& addr,
                                     bool autoconnect) = 0;
  virtual void LeAudioUpdatePacs(RawAddress const& addr) = 0;
  virtual void LeAudioUpdateAses(RawAddress const& addr) = 0;
  virtual void LeAudioUpdateHandles(RawAddress const& addr) = 0;
  virtual void SetLeAudioLocations(RawAddress const& addr,
                                   uint32_t sink_location,
                                   uint32_t source_location) = 0;
  virtual void SetLeAudioContexts(RawAddress const& addr, uint16_t sink_context,
                                  uint16_t source_context) = 0;
  virtual void ClearLeAudioServiceData(RawAddress const& addr) = 0;
  virtual void RemoveLeaudio(RawAddress const& addr) = 0;
  virtual void AddLeaudioHasDevice(const RawAddress& address,
                                   std::vector<uint8_t> presets_bin,
                                   uint8_t features, uint8_t active_preset) = 0;
  virtual void SetLeaudioHasPresets(const RawAddress& address,
                                    std::vector<uint8_t> presets_bin) = 0;
  virtual bool GetLeaudioHasFeatures(const RawAddress& address,
                                     uint8_t& features) = 0;
  virtual void SetLeaudioHasFeatures(const RawAddress& address,
                                     uint8_t features) = 0;
  virtual void SetLeaudioHasActivePreset(const RawAddress& address,
                                         uint8_t active_preset) = 0;
  virtual bool GetLeaudioHasPresets(const RawAddress& address,
                                    std::vector<uint8_t>& presets_bin,
                                    uint8_t& active_preset) = 0;
  virtual void RemoveLeaudioHas(const RawAddress& address) = 0;
  virtual bt_status_t GetRemoteDeviceProperty(const RawAddress* address,
                                              bt_property_t* property) = 0;

  virtual ~BtifStorageInterface() = default;
};

class MockBtifStorageInterface : public BtifStorageInterface {
 public:
  MOCK_METHOD((void), AddLeaudioAutoconnect,
              (RawAddress const& addr, bool autoconnect), (override));
  MOCK_METHOD((void), LeAudioUpdatePacs, (RawAddress const& addr), (override));
  MOCK_METHOD((void), LeAudioUpdateAses, (RawAddress const& addr), (override));
  MOCK_METHOD((void), LeAudioUpdateHandles, (RawAddress const& addr),
              (override));
  MOCK_METHOD((void), SetLeAudioLocations,
              (RawAddress const& addr, uint32_t sink_location,
               uint32_t source_location),
              (override));
  MOCK_METHOD((void), SetLeAudioContexts,
              (RawAddress const& addr, uint16_t sink_context,
               uint16_t source_context),
              (override));
  MOCK_METHOD((void), ClearLeAudioServiceData, (RawAddress const& addr),
              (override));
  MOCK_METHOD((void), RemoveLeaudio, (RawAddress const& addr), (override));
  MOCK_METHOD((void), AddLeaudioHasDevice,
              (const RawAddress& address, std::vector<uint8_t> presets_bin,
               uint8_t features, uint8_t active_preset),
              (override));
  MOCK_METHOD((bool), GetLeaudioHasPresets,
              (const RawAddress& address, std::vector<uint8_t>& presets_bin,
               uint8_t& active_preset),
              (override));
  MOCK_METHOD((void), SetLeaudioHasPresets,
              (const RawAddress& address, std::vector<uint8_t> presets_bin),
              (override));
  MOCK_METHOD((bool), GetLeaudioHasFeatures,
              (const RawAddress& address, uint8_t& features), (override));
  MOCK_METHOD((void), SetLeaudioHasFeatures,
              (const RawAddress& address, uint8_t features), (override));
  MOCK_METHOD((void), SetLeaudioHasActivePreset,
              (const RawAddress& address, uint8_t active_preset), (override));
  MOCK_METHOD((void), RemoveLeaudioHas, (const RawAddress& address),
              (override));
  MOCK_METHOD((bt_status_t), GetRemoteDeviceProperty,
              (const RawAddress* address, bt_property_t* property), (override));
};

/**
 * Set the {@link MockBifStorageInterface} for testing
 *
 * @param mock_btif_storage_interface pointer to mock btm security
 * internal interface, could be null
 */
void SetMockBtifStorageInterface(
    MockBtifStorageInterface* mock_btif_storage_interface);

}  // namespace storage
}  // namespace bluetooth
