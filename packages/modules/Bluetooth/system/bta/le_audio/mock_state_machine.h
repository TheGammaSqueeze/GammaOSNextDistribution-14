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

#include "state_machine.h"

class MockLeAudioGroupStateMachine : public le_audio::LeAudioGroupStateMachine {
 public:
  MOCK_METHOD(
      (bool), StartStream,
      (le_audio::LeAudioDeviceGroup * group,
       le_audio::types::LeAudioContextType context_type,
       const le_audio::types::BidirectionalPair<le_audio::types::AudioContexts>&
           metadata_context_types,
       le_audio::types::BidirectionalPair<std::vector<uint8_t>> ccid_list),
      (override));
  MOCK_METHOD((bool), AttachToStream,
              (le_audio::LeAudioDeviceGroup * group,
               le_audio::LeAudioDevice* leAudioDevice,
               le_audio::types::BidirectionalPair<std::vector<uint8_t>> ccids),
              (override));
  MOCK_METHOD((void), SuspendStream, (le_audio::LeAudioDeviceGroup * group),
              (override));
  MOCK_METHOD(
      (bool), ConfigureStream,
      (le_audio::LeAudioDeviceGroup * group,
       le_audio::types::LeAudioContextType context_type,
       const le_audio::types::BidirectionalPair<le_audio::types::AudioContexts>&
           metadata_context_types,
       le_audio::types::BidirectionalPair<std::vector<uint8_t>> ccid_lists),
      (override));
  MOCK_METHOD((void), StopStream, (le_audio::LeAudioDeviceGroup * group),
              (override));
  MOCK_METHOD((void), ProcessGattNotifEvent,
              (uint8_t * value, uint16_t len, le_audio::types::ase* ase,
               le_audio::LeAudioDevice* leAudioDevice,
               le_audio::LeAudioDeviceGroup* group),
              (override));

  MOCK_METHOD((void), ProcessGattCtpNotification,
              (le_audio::LeAudioDeviceGroup * group, uint8_t* value,
               uint16_t len),
              (override));
  MOCK_METHOD((void), ProcessHciNotifOnCigCreate,
              (le_audio::LeAudioDeviceGroup * group, uint8_t status,
               uint8_t cig_id, std::vector<uint16_t> conn_handles),
              (override));
  MOCK_METHOD((void), ProcessHciNotifOnCigRemove,
              (uint8_t status, le_audio::LeAudioDeviceGroup* group),
              (override));
  MOCK_METHOD(
      (void), ProcessHciNotifCisEstablished,
      (le_audio::LeAudioDeviceGroup * group,
       le_audio::LeAudioDevice* leAudioDevice,
       const bluetooth::hci::iso_manager::cis_establish_cmpl_evt* event),
      (override));
  MOCK_METHOD((void), ProcessHciNotifCisDisconnected,
              (le_audio::LeAudioDeviceGroup * group,
               le_audio::LeAudioDevice* leAudioDevice,
               const bluetooth::hci::iso_manager::cis_disconnected_evt* event),
              (override));
  MOCK_METHOD((void), ProcessHciNotifSetupIsoDataPath,
              (le_audio::LeAudioDeviceGroup * group,
               le_audio::LeAudioDevice* leAudioDevice, uint8_t status,
               uint16_t conn_hdl),
              (override));
  MOCK_METHOD((void), ProcessHciNotifRemoveIsoDataPath,
              (le_audio::LeAudioDeviceGroup * group,
               le_audio::LeAudioDevice* leAudioDevice, uint8_t status,
               uint16_t conn_hdl),
              (override));
  MOCK_METHOD((void), Initialize,
              (le_audio::LeAudioGroupStateMachine::Callbacks *
               state_machine_callbacks));
  MOCK_METHOD((void), Cleanup, ());
  MOCK_METHOD((void), ProcessHciNotifIsoLinkQualityRead,
              (le_audio::LeAudioDeviceGroup * group,
               le_audio::LeAudioDevice* leAudioDevice, uint8_t conn_handle,
               uint32_t txUnackedPackets, uint32_t txFlushedPackets,
               uint32_t txLastSubeventPackets, uint32_t retransmittedPackets,
               uint32_t crcErrorPackets, uint32_t rxUnreceivedPackets,
               uint32_t duplicatePackets),
              (override));
  MOCK_METHOD((void), ProcessHciNotifAclDisconnected,
              (le_audio::LeAudioDeviceGroup * group,
               le_audio::LeAudioDevice* leAudioDevice),
              (override));

  static void SetMockInstanceForTesting(MockLeAudioGroupStateMachine* machine);
};
