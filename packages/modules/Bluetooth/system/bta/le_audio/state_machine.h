/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
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

#pragma once

#include <vector>

#include "btm_iso_api.h"
#include "btm_iso_api_types.h"
#include "device_groups.h"
#include "devices.h"
#include "hardware/bt_le_audio.h"
#include "le_audio_types.h"

namespace le_audio {

/* State machine interface */
class LeAudioGroupStateMachine {
 public:
  class Callbacks {
   public:
    virtual ~Callbacks() = default;

    virtual void StatusReportCb(
        int group_id, bluetooth::le_audio::GroupStreamStatus status) = 0;
    virtual void OnStateTransitionTimeout(int group_id) = 0;
    virtual void OnUpdatedCisConfiguration(int group_id, uint8_t direction) = 0;
    virtual void OnDeviceAutonomousStateTransitionTimeout(
        LeAudioDevice* leAudioDevice) = 0;
  };

  virtual ~LeAudioGroupStateMachine() = default;

  static void Initialize(Callbacks* state_machine_callbacks);
  static void Cleanup(void);
  static LeAudioGroupStateMachine* Get(void);

  virtual bool AttachToStream(
      LeAudioDeviceGroup* group, LeAudioDevice* leAudioDevice,
      types::BidirectionalPair<std::vector<uint8_t>> ccids) = 0;
  virtual bool StartStream(
      LeAudioDeviceGroup* group, types::LeAudioContextType context_type,
      const types::BidirectionalPair<types::AudioContexts>&
          metadata_context_types,
      types::BidirectionalPair<std::vector<uint8_t>> ccid_lists = {
          .sink = {}, .source = {}}) = 0;
  virtual void SuspendStream(LeAudioDeviceGroup* group) = 0;
  virtual bool ConfigureStream(
      LeAudioDeviceGroup* group, types::LeAudioContextType context_type,
      const types::BidirectionalPair<types::AudioContexts>&
          metadata_context_types,
      types::BidirectionalPair<std::vector<uint8_t>> ccid_lists = {
          .sink = {}, .source = {}}) = 0;
  virtual void StopStream(LeAudioDeviceGroup* group) = 0;
  virtual void ProcessGattCtpNotification(LeAudioDeviceGroup* group,
                                          uint8_t* value, uint16_t len) = 0;
  virtual void ProcessGattNotifEvent(uint8_t* value, uint16_t len,
                                     struct types::ase* ase,
                                     LeAudioDevice* leAudioDevice,
                                     LeAudioDeviceGroup* group) = 0;

  virtual void ProcessHciNotifOnCigCreate(
      LeAudioDeviceGroup* group, uint8_t status, uint8_t cig_id,
      std::vector<uint16_t> conn_handles) = 0;
  virtual void ProcessHciNotifOnCigRemove(uint8_t status,
                                          LeAudioDeviceGroup* group) = 0;
  virtual void ProcessHciNotifCisEstablished(
      LeAudioDeviceGroup* group, LeAudioDevice* leAudioDevice,
      const bluetooth::hci::iso_manager::cis_establish_cmpl_evt* event) = 0;
  virtual void ProcessHciNotifCisDisconnected(
      LeAudioDeviceGroup* group, LeAudioDevice* leAudioDevice,
      const bluetooth::hci::iso_manager::cis_disconnected_evt* event) = 0;
  virtual void ProcessHciNotifSetupIsoDataPath(LeAudioDeviceGroup* group,
                                               LeAudioDevice* leAudioDevice,
                                               uint8_t status,
                                               uint16_t conn_hdl) = 0;
  virtual void ProcessHciNotifRemoveIsoDataPath(LeAudioDeviceGroup* group,
                                                LeAudioDevice* leAudioDevice,
                                                uint8_t status,
                                                uint16_t conn_hdl) = 0;
  virtual void ProcessHciNotifIsoLinkQualityRead(
      LeAudioDeviceGroup* group, LeAudioDevice* leAudioDevice,
      uint8_t conn_handle, uint32_t txUnackedPackets, uint32_t txFlushedPackets,
      uint32_t txLastSubeventPackets, uint32_t retransmittedPackets,
      uint32_t crcErrorPackets, uint32_t rxUnreceivedPackets,
      uint32_t duplicatePackets) = 0;
  virtual void ProcessHciNotifAclDisconnected(LeAudioDeviceGroup* group,
                                              LeAudioDevice* leAudioDevice) = 0;
};
}  // namespace le_audio
