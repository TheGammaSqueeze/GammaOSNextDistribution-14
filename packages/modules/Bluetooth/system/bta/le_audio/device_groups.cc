/*
 * Copyright 2023 The Android Open Source Project
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA
 * - www.ehima.com
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

#include "device_groups.h"

#include "bta/include/bta_gatt_api.h"
#include "bta_csis_api.h"
#include "btif/include/btif_profile_storage.h"
#include "btm_iso_api.h"
#include "device/include/controller.h"
#include "internal_include/bt_trace.h"
#include "le_audio_set_configuration_provider.h"
#include "metrics_collector.h"

namespace le_audio {

using le_audio::types::ase;
using types::AseState;
using types::AudioContexts;
using types::AudioLocations;
using types::BidirectionalPair;
using types::CisState;
using types::CisType;
using types::DataPathState;
using types::LeAudioContextType;
using types::LeAudioCoreCodecConfig;

/* LeAudioDeviceGroup Class methods implementation */
void LeAudioDeviceGroup::AddNode(
    const std::shared_ptr<LeAudioDevice>& leAudioDevice) {
  leAudioDevice->group_id_ = group_id_;
  leAudioDevices_.push_back(std::weak_ptr<LeAudioDevice>(leAudioDevice));
  MetricsCollector::Get()->OnGroupSizeUpdate(group_id_, leAudioDevices_.size());
}

void LeAudioDeviceGroup::RemoveNode(
    const std::shared_ptr<LeAudioDevice>& leAudioDevice) {
  /* Group information cleaning in the device. */
  leAudioDevice->group_id_ = bluetooth::groups::kGroupUnknown;
  for (auto ase : leAudioDevice->ases_) {
    ase.active = false;
    ase.cis_conn_hdl = 0;
  }

  leAudioDevices_.erase(
      std::remove_if(
          leAudioDevices_.begin(), leAudioDevices_.end(),
          [&leAudioDevice](auto& d) { return d.lock() == leAudioDevice; }),
      leAudioDevices_.end());
  MetricsCollector::Get()->OnGroupSizeUpdate(group_id_, leAudioDevices_.size());
}

bool LeAudioDeviceGroup::IsEmpty(void) const {
  return leAudioDevices_.size() == 0;
}

bool LeAudioDeviceGroup::IsAnyDeviceConnected(void) const {
  return (NumOfConnected() != 0);
}

int LeAudioDeviceGroup::Size(void) const { return leAudioDevices_.size(); }

int LeAudioDeviceGroup::NumOfConnected(LeAudioContextType context_type) const {
  if (leAudioDevices_.empty()) return 0;

  bool check_context_type = (context_type != LeAudioContextType::RFU);
  AudioContexts type_set(context_type);

  /* return number of connected devices from the set*/
  return std::count_if(
      leAudioDevices_.begin(), leAudioDevices_.end(),
      [type_set, check_context_type](auto& iter) {
        auto dev = iter.lock();
        if (dev) {
          if (dev->conn_id_ == GATT_INVALID_CONN_ID) return false;
          if (dev->GetConnectionState() != DeviceConnectState::CONNECTED)
            return false;
          if (!check_context_type) return true;
          return dev->GetSupportedContexts().test_any(type_set);
        }
        return false;
      });
}

void LeAudioDeviceGroup::ClearSinksFromConfiguration(void) {
  LOG_INFO("Group %p, group_id %d", this, group_id_);

  auto direction = types::kLeAudioDirectionSink;
  stream_conf.stream_params.get(direction).clear();
  CodecManager::GetInstance()->ClearCisConfiguration(direction);
}

void LeAudioDeviceGroup::ClearSourcesFromConfiguration(void) {
  LOG_INFO("Group %p, group_id %d", this, group_id_);

  auto direction = types::kLeAudioDirectionSource;
  stream_conf.stream_params.get(direction).clear();
  CodecManager::GetInstance()->ClearCisConfiguration(direction);
}

void LeAudioDeviceGroup::ClearAllCises(void) {
  LOG_INFO("group_id: %d", group_id_);
  cig.cises.clear();
  ClearSinksFromConfiguration();
  ClearSourcesFromConfiguration();
}

void LeAudioDeviceGroup::UpdateCisConfiguration(uint8_t direction) {
  CodecManager::GetInstance()->UpdateCisConfiguration(
      cig.cises, stream_conf.stream_params.get(direction), direction);
}

void LeAudioDeviceGroup::Cleanup(void) {
  /* Bluetooth is off while streaming - disconnect CISes and remove CIG */
  if (GetState() == AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
    auto& sink_stream_locations =
        stream_conf.stream_params.sink.stream_locations;
    auto& source_stream_locations =
        stream_conf.stream_params.source.stream_locations;

    if (!sink_stream_locations.empty()) {
      for (const auto kv_pair : sink_stream_locations) {
        auto cis_handle = kv_pair.first;
        bluetooth::hci::IsoManager::GetInstance()->DisconnectCis(
            cis_handle, HCI_ERR_PEER_USER);

        /* Check the other direction if disconnecting bidirectional CIS */
        if (source_stream_locations.empty()) {
          continue;
        }
        source_stream_locations.erase(
            std::remove_if(
                source_stream_locations.begin(), source_stream_locations.end(),
                [&cis_handle](auto& pair) { return pair.first == cis_handle; }),
            source_stream_locations.end());
      }
    }

    /* Take care of the non-bidirectional CISes */
    if (!source_stream_locations.empty()) {
      for (auto [cis_handle, _] : source_stream_locations) {
        bluetooth::hci::IsoManager::GetInstance()->DisconnectCis(
            cis_handle, HCI_ERR_PEER_USER);
      }
    }
  }

  /* Note: CIG will stay in the controller. We cannot remove it here, because
   * Cises are not yet disconnected.
   * When user start Bluetooth, HCI Reset should remove it
   */

  leAudioDevices_.clear();
  ClearAllCises();
}

void LeAudioDeviceGroup::Deactivate(void) {
  for (auto* leAudioDevice = GetFirstActiveDevice(); leAudioDevice;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    for (auto* ase = leAudioDevice->GetFirstActiveAse(); ase;
         ase = leAudioDevice->GetNextActiveAse(ase)) {
      ase->active = false;
    }
  }
}

bool LeAudioDeviceGroup::Activate(
  LeAudioContextType context_type,
  const BidirectionalPair<AudioContexts>& metadata_context_types,
  BidirectionalPair<std::vector<uint8_t>> ccid_lists) {
  bool is_activate = false;
  for (auto leAudioDevice : leAudioDevices_) {
    if (leAudioDevice.expired()) continue;

    bool activated = leAudioDevice.lock()->ActivateConfiguredAses(
        context_type, metadata_context_types, ccid_lists);
    LOG_INFO("Device %s is %s",
             ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice.lock().get()->address_),
             activated ? "activated" : " not activated");
    if (activated) {
      if (!cig.AssignCisIds(leAudioDevice.lock().get())) {
        return false;
      }
      is_activate = true;
    }
  }
  return is_activate;
}

AudioContexts LeAudioDeviceGroup::GetSupportedContexts(int direction) const {
  AudioContexts context;
  for (auto& device : leAudioDevices_) {
    auto shared_dev = device.lock();
    if (shared_dev) {
      context |= shared_dev->GetSupportedContexts(direction);
    }
  }
  return context;
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstDevice(void) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [](auto& iter) { return !iter.expired(); });

  if (iter == leAudioDevices_.end()) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstDeviceWithAvailableContext(
    LeAudioContextType context_type) const {
  auto iter = std::find_if(
      leAudioDevices_.begin(), leAudioDevices_.end(),
      [&context_type](auto& iter) {
        if (iter.expired()) return false;
        return iter.lock()->GetAvailableContexts().test(context_type);
      });

  if ((iter == leAudioDevices_.end()) || (iter->expired())) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextDevice(
    LeAudioDevice* leAudioDevice) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  /* If reference device not found */
  if (iter == leAudioDevices_.end()) return nullptr;

  std::advance(iter, 1);
  /* If reference device is last in group */
  if (iter == leAudioDevices_.end()) return nullptr;

  if (iter->expired()) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextDeviceWithAvailableContext(
    LeAudioDevice* leAudioDevice, LeAudioContextType context_type) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  /* If reference device not found */
  if (iter == leAudioDevices_.end()) return nullptr;

  std::advance(iter, 1);
  /* If reference device is last in group */
  if (iter == leAudioDevices_.end()) return nullptr;

  iter = std::find_if(iter, leAudioDevices_.end(), [&context_type](auto& d) {
    if (d.expired())
      return false;
    else
      return d.lock()->GetAvailableContexts().test(context_type);
    ;
  });

  return (iter == leAudioDevices_.end()) ? nullptr : (iter->lock()).get();
}

bool LeAudioDeviceGroup::IsDeviceInTheGroup(
    LeAudioDevice* leAudioDevice) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  if ((iter == leAudioDevices_.end()) || (iter->expired())) return false;

  return true;
}

bool LeAudioDeviceGroup::IsGroupReadyToCreateStream(void) const {
  auto iter =
      std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(), [](auto& d) {
        if (d.expired())
          return false;
        else
          return !(((d.lock()).get())->IsReadyToCreateStream());
      });

  return iter == leAudioDevices_.end();
}

bool LeAudioDeviceGroup::IsGroupReadyToSuspendStream(void) const {
  auto iter =
      std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(), [](auto& d) {
        if (d.expired())
          return false;
        else
          return !(((d.lock()).get())->IsReadyToSuspendStream());
      });

  return iter == leAudioDevices_.end();
}

bool LeAudioDeviceGroup::HaveAnyActiveDeviceInUnconfiguredState() const {
  auto iter =
      std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(), [](auto& d) {
        if (d.expired())
          return false;
        else
          return (((d.lock()).get())->HaveAnyUnconfiguredAses());
      });

  return iter != leAudioDevices_.end();
}

bool LeAudioDeviceGroup::HaveAllActiveDevicesAsesTheSameState(
    AseState state) const {
  auto iter = std::find_if(
      leAudioDevices_.begin(), leAudioDevices_.end(), [&state](auto& d) {
        if (d.expired())
          return false;
        else
          return !(((d.lock()).get())->HaveAllActiveAsesSameState(state));
      });

  return iter == leAudioDevices_.end();
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstActiveDevice(void) const {
  auto iter =
      std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(), [](auto& d) {
        if (d.expired())
          return false;
        else
          return ((d.lock()).get())->HaveActiveAse();
      });

  if (iter == leAudioDevices_.end() || iter->expired()) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextActiveDevice(
    LeAudioDevice* leAudioDevice) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  if (iter == leAudioDevices_.end() ||
      std::distance(iter, leAudioDevices_.end()) < 1)
    return nullptr;

  iter = std::find_if(std::next(iter, 1), leAudioDevices_.end(), [](auto& d) {
    if (d.expired())
      return false;
    else
      return ((d.lock()).get())->HaveActiveAse();
  });

  return (iter == leAudioDevices_.end()) ? nullptr : (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstActiveDeviceByCisAndDataPathState(
    CisState cis_state, DataPathState data_path_state) const {
  auto iter =
      std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                   [&data_path_state, &cis_state](auto& d) {
                     if (d.expired()) {
                       return false;
                     }

                     return (((d.lock()).get())
                                 ->GetFirstActiveAseByCisAndDataPathState(
                                     cis_state, data_path_state) != nullptr);
                   });

  if (iter == leAudioDevices_.end()) {
    return nullptr;
  }

  return iter->lock().get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextActiveDeviceByCisAndDataPathState(
    LeAudioDevice* leAudioDevice, CisState cis_state,
    DataPathState data_path_state) const {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired()) {
                               return false;
                             }

                             return d.lock().get() == leAudioDevice;
                           });

  if (std::distance(iter, leAudioDevices_.end()) < 1) {
    return nullptr;
  }

  iter = std::find_if(std::next(iter, 1), leAudioDevices_.end(),
                      [&cis_state, &data_path_state](auto& d) {
                        if (d.expired()) {
                          return false;
                        }

                        return (((d.lock()).get())
                                    ->GetFirstActiveAseByCisAndDataPathState(
                                        cis_state, data_path_state) != nullptr);
                      });

  if (iter == leAudioDevices_.end()) {
    return nullptr;
  }

  return iter->lock().get();
}

uint32_t LeAudioDeviceGroup::GetSduInterval(uint8_t direction) const {
  for (LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
       leAudioDevice != nullptr;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    struct ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
    if (!ase) continue;

    return ase->codec_config.GetAsCoreCodecConfig().GetFrameDurationUs();
  }

  return 0;
}

uint8_t LeAudioDeviceGroup::GetSCA(void) const {
  uint8_t sca = bluetooth::hci::iso_manager::kIsoSca0To20Ppm;

  for (const auto& leAudioDevice : leAudioDevices_) {
    uint8_t dev_sca =
        BTM_GetPeerSCA(leAudioDevice.lock()->address_, BT_TRANSPORT_LE);

    /* If we could not read SCA from the peer device or sca is 0,
     * then there is no reason to continue.
     */
    if ((dev_sca == 0xFF) || (dev_sca == 0)) return 0;

    /* The Slaves_Clock_Accuracy parameter shall be the worst-case sleep clock
     *accuracy of all the slaves that will participate in the CIG.
     */
    if (dev_sca < sca) {
      sca = dev_sca;
    }
  }

  return sca;
}

uint8_t LeAudioDeviceGroup::GetPacking(void) const {
  /* TODO: Decide about packing */
  return bluetooth::hci::kIsoCigPackingSequential;
}

uint8_t LeAudioDeviceGroup::GetFraming(void) const {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    struct ase* ase = leAudioDevice->GetFirstActiveAse();
    if (!ase) continue;

    do {
      if (ase->framing == types::kFramingUnframedPduUnsupported)
        return bluetooth::hci::kIsoCigFramingFramed;
    } while ((ase = leAudioDevice->GetNextActiveAse(ase)));
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return bluetooth::hci::kIsoCigFramingUnframed;
}

/* TODO: Preferred parameter may be other than minimum */
static uint16_t find_max_transport_latency(const LeAudioDeviceGroup* group,
                                           uint8_t direction) {
  uint16_t max_transport_latency = 0;

  for (LeAudioDevice* leAudioDevice = group->GetFirstActiveDevice();
       leAudioDevice != nullptr;
       leAudioDevice = group->GetNextActiveDevice(leAudioDevice)) {
    for (ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
         ase != nullptr;
         ase = leAudioDevice->GetNextActiveAseWithSameDirection(ase)) {
      if (!ase) break;

      if (max_transport_latency == 0) {
        // first assignment
        max_transport_latency = ase->max_transport_latency;
      } else if (ase->max_transport_latency < max_transport_latency) {
        if (ase->max_transport_latency != 0) {
          max_transport_latency = ase->max_transport_latency;
        } else {
          LOG_WARN("Trying to set latency back to 0, ASE ID %d", ase->id);
        }
      }
    }
  }

  if (max_transport_latency < types::kMaxTransportLatencyMin) {
    max_transport_latency = types::kMaxTransportLatencyMin;
  } else if (max_transport_latency > types::kMaxTransportLatencyMax) {
    max_transport_latency = types::kMaxTransportLatencyMax;
  }

  return max_transport_latency;
}

uint16_t LeAudioDeviceGroup::GetMaxTransportLatencyStom(void) const {
  return find_max_transport_latency(this, types::kLeAudioDirectionSource);
}

uint16_t LeAudioDeviceGroup::GetMaxTransportLatencyMtos(void) const {
  return find_max_transport_latency(this, types::kLeAudioDirectionSink);
}

uint32_t LeAudioDeviceGroup::GetTransportLatencyUs(uint8_t direction) const {
  if (direction == types::kLeAudioDirectionSink) {
    return transport_latency_mtos_us_;
  } else if (direction == types::kLeAudioDirectionSource) {
    return transport_latency_stom_us_;
  } else {
    LOG(ERROR) << __func__ << ", invalid direction";
    return 0;
  }
}

void LeAudioDeviceGroup::SetTransportLatency(
    uint8_t direction, uint32_t new_transport_latency_us) {
  uint32_t* transport_latency_us;

  if (direction == types::kLeAudioDirectionSink) {
    transport_latency_us = &transport_latency_mtos_us_;
  } else if (direction == types::kLeAudioDirectionSource) {
    transport_latency_us = &transport_latency_stom_us_;
  } else {
    LOG(ERROR) << __func__ << ", invalid direction";
    return;
  }

  if (*transport_latency_us == new_transport_latency_us) return;

  if ((*transport_latency_us != 0) &&
      (*transport_latency_us != new_transport_latency_us)) {
    LOG(WARNING) << __func__ << ", Different transport latency for group: "
                 << " old: " << static_cast<int>(*transport_latency_us)
                 << " [us], new: " << static_cast<int>(new_transport_latency_us)
                 << " [us]";
    return;
  }

  LOG(INFO) << __func__ << ", updated group " << static_cast<int>(group_id_)
            << " transport latency: "
            << static_cast<int>(new_transport_latency_us) << " [us]";
  *transport_latency_us = new_transport_latency_us;
}

uint8_t LeAudioDeviceGroup::GetRtn(uint8_t direction, uint8_t cis_id) const {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    auto ases_pair = leAudioDevice->GetAsesByCisId(cis_id);

    if (ases_pair.sink && direction == types::kLeAudioDirectionSink) {
      return ases_pair.sink->retrans_nb;
    } else if (ases_pair.source &&
               direction == types::kLeAudioDirectionSource) {
      return ases_pair.source->retrans_nb;
    }
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return 0;
}

uint16_t LeAudioDeviceGroup::GetMaxSduSize(uint8_t direction,
                                           uint8_t cis_id) const {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    auto ases_pair = leAudioDevice->GetAsesByCisId(cis_id);

    if (ases_pair.sink && direction == types::kLeAudioDirectionSink) {
      return ases_pair.sink->max_sdu_size;
    } else if (ases_pair.source &&
               direction == types::kLeAudioDirectionSource) {
      return ases_pair.source->max_sdu_size;
    }
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return 0;
}

uint8_t LeAudioDeviceGroup::GetPhyBitmask(uint8_t direction) const {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  // local supported PHY's
  uint8_t phy_bitfield = bluetooth::hci::kIsoCigPhy1M;
  if (controller_get_interface()->SupportsBle2mPhy())
    phy_bitfield |= bluetooth::hci::kIsoCigPhy2M;

  if (!leAudioDevice) {
    LOG(ERROR) << "No active leaudio device for direction?: " << +direction;
    return phy_bitfield;
  }

  do {
    struct ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
    if (!ase) return phy_bitfield;

    do {
      if (direction == ase->direction) {
        phy_bitfield &= leAudioDevice->GetPhyBitmask();

        // A value of 0x00 denotes no preference
        if (ase->preferred_phy && (phy_bitfield & ase->preferred_phy)) {
          phy_bitfield &= ase->preferred_phy;
          LOG_DEBUG("Using ASE preferred phy 0x%02x",
                    static_cast<int>(phy_bitfield));
        } else {
          LOG_WARN(
              "ASE preferred 0x%02x has nothing common with phy_bitfield "
              "0x%02x ",
              static_cast<int>(ase->preferred_phy),
              static_cast<int>(phy_bitfield));
        }
      }
    } while ((ase = leAudioDevice->GetNextActiveAseWithSameDirection(ase)));
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return phy_bitfield;
}

uint8_t LeAudioDeviceGroup::GetTargetPhy(uint8_t direction) const {
  uint8_t phy_bitfield = GetPhyBitmask(direction);

  // prefer to use 2M if supported
  if (phy_bitfield & bluetooth::hci::kIsoCigPhy2M)
    return types::kTargetPhy2M;
  else if (phy_bitfield & bluetooth::hci::kIsoCigPhy1M)
    return types::kTargetPhy1M;
  else
    return 0;
}

bool LeAudioDeviceGroup::GetPresentationDelay(uint32_t* delay,
                                              uint8_t direction) const {
  uint32_t delay_min = 0;
  uint32_t delay_max = UINT32_MAX;
  uint32_t preferred_delay_min = delay_min;
  uint32_t preferred_delay_max = delay_max;

  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    struct ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
    if (!ase) continue;  // device has no active ASEs in this direction

    do {
      /* No common range check */
      if (ase->pres_delay_min > delay_max || ase->pres_delay_max < delay_min)
        return false;

      if (ase->pres_delay_min > delay_min) delay_min = ase->pres_delay_min;
      if (ase->pres_delay_max < delay_max) delay_max = ase->pres_delay_max;
      if (ase->preferred_pres_delay_min > preferred_delay_min)
        preferred_delay_min = ase->preferred_pres_delay_min;
      if (ase->preferred_pres_delay_max < preferred_delay_max &&
          ase->preferred_pres_delay_max != types::kPresDelayNoPreference)
        preferred_delay_max = ase->preferred_pres_delay_max;
    } while ((ase = leAudioDevice->GetNextActiveAseWithSameDirection(ase)));
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  if (preferred_delay_min <= preferred_delay_max &&
      preferred_delay_min > delay_min && preferred_delay_min < delay_max) {
    *delay = preferred_delay_min;
  } else {
    *delay = delay_min;
  }

  return true;
}

uint16_t LeAudioDeviceGroup::GetRemoteDelay(uint8_t direction) const {
  uint16_t remote_delay_ms = 0;
  uint32_t presentation_delay;

  if (!GetPresentationDelay(&presentation_delay, direction)) {
    /* This should never happens at stream request time but to be safe return
     * some sample value to not break streaming
     */
    return 100;
  }

  /* us to ms */
  remote_delay_ms = presentation_delay / 1000;
  remote_delay_ms += GetTransportLatencyUs(direction) / 1000;

  return remote_delay_ms;
}

bool LeAudioDeviceGroup::UpdateAudioContextAvailability(void) {
  LOG_DEBUG("%d", group_id_);
  auto old_contexts = GetAvailableContexts();
  SetAvailableContexts(GetLatestAvailableContexts());
  return old_contexts != GetAvailableContexts();
}

bool LeAudioDeviceGroup::UpdateAudioSetConfigurationCache(
    LeAudioContextType ctx_type) {
  const le_audio::set_configurations::AudioSetConfiguration* new_conf =
      FindFirstSupportedConfiguration(ctx_type);
  auto update_config = true;

  if (context_to_configuration_cache_map.count(ctx_type) != 0) {
    auto [is_valid, existing_conf] =
        context_to_configuration_cache_map.at(ctx_type);
    update_config = (new_conf != existing_conf);
    /* Just mark it as still valid */
    if (!update_config && !is_valid) {
      context_to_configuration_cache_map.at(ctx_type).first = true;
      return false;
    }
  }

  if (update_config) {
    context_to_configuration_cache_map[ctx_type] = std::pair(true, new_conf);
    LOG_INFO("config: %s -> %s", ToHexString(ctx_type).c_str(),
             (new_conf ? new_conf->name.c_str() : "(none)"));
  }
  return update_config;
}

void LeAudioDeviceGroup::InvalidateCachedConfigurations(void) {
  LOG_INFO(" Group id: %d", group_id_);
  context_to_configuration_cache_map.clear();
}

types::BidirectionalPair<AudioContexts>
LeAudioDeviceGroup::GetLatestAvailableContexts() const {
  types::BidirectionalPair<AudioContexts> contexts;
  for (const auto& device : leAudioDevices_) {
    auto shared_ptr = device.lock();
    if (shared_ptr &&
        shared_ptr->GetConnectionState() == DeviceConnectState::CONNECTED) {
      contexts.sink |=
          shared_ptr->GetAvailableContexts(types::kLeAudioDirectionSink);
      contexts.source |=
          shared_ptr->GetAvailableContexts(types::kLeAudioDirectionSource);
    }
  }
  return contexts;
}

bool LeAudioDeviceGroup::ReloadAudioLocations(void) {
  AudioLocations updated_snk_audio_locations_ =
      codec_spec_conf::kLeAudioLocationNotAllowed;
  AudioLocations updated_src_audio_locations_ =
      codec_spec_conf::kLeAudioLocationNotAllowed;

  for (const auto& device : leAudioDevices_) {
    if (device.expired() || (device.lock().get()->GetConnectionState() !=
                             DeviceConnectState::CONNECTED))
      continue;
    updated_snk_audio_locations_ |= device.lock().get()->snk_audio_locations_;
    updated_src_audio_locations_ |= device.lock().get()->src_audio_locations_;
  }

  /* Nothing has changed */
  if ((updated_snk_audio_locations_ == snk_audio_locations_) &&
      (updated_src_audio_locations_ == src_audio_locations_))
    return false;

  snk_audio_locations_ = updated_snk_audio_locations_;
  src_audio_locations_ = updated_src_audio_locations_;

  return true;
}

bool LeAudioDeviceGroup::ReloadAudioDirections(void) {
  uint8_t updated_audio_directions = 0x00;

  for (const auto& device : leAudioDevices_) {
    if (device.expired() || (device.lock().get()->GetConnectionState() !=
                             DeviceConnectState::CONNECTED))
      continue;
    updated_audio_directions |= device.lock().get()->audio_directions_;
  }

  /* Nothing has changed */
  if (updated_audio_directions == audio_directions_) return false;

  audio_directions_ = updated_audio_directions;

  return true;
}

bool LeAudioDeviceGroup::IsInTransition(void) const { return in_transition_; }

bool LeAudioDeviceGroup::IsStreaming(void) const {
  return current_state_ == AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING;
}

bool LeAudioDeviceGroup::IsReleasingOrIdle(void) const {
  return (target_state_ == AseState::BTA_LE_AUDIO_ASE_STATE_IDLE) ||
         (current_state_ == AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
}

bool LeAudioDeviceGroup::IsGroupStreamReady(void) const {
  bool is_device_ready = false;

  /* All connected devices must be ready */
  for (auto& weak : leAudioDevices_) {
    auto dev = weak.lock();
    if (!dev) return false;

    /* We are interested here in devices which are connected on profile level
     * and devices which are configured (meaning, have actived ASE(s))*/
    if (dev->GetConnectionState() == DeviceConnectState::CONNECTED &&
        dev->HaveActiveAse()) {
      if (!dev->IsReadyToStream()) {
        return false;
      }
      is_device_ready = true;
    }
  }
  return is_device_ready;
}

bool LeAudioDeviceGroup::HaveAllCisesDisconnected(void) const {
  for (auto const dev : leAudioDevices_) {
    if (dev.expired()) continue;
    if (dev.lock().get()->HaveAnyCisConnected()) return false;
  }
  return true;
}

uint8_t LeAudioDeviceGroup::CigConfiguration::GetFirstFreeCisId(
    CisType cis_type) const {
  LOG_INFO("Group: %p, group_id: %d cis_type: %d", group_, group_->group_id_,
           static_cast<int>(cis_type));
  for (size_t id = 0; id < cises.size(); id++) {
    if (cises[id].addr.IsEmpty() && cises[id].type == cis_type) {
      return id;
    }
  }
  return kInvalidCisId;
}

types::LeAudioConfigurationStrategy LeAudioDeviceGroup::GetGroupStrategy(
    int expected_group_size) const {
  /* Simple strategy picker */
  LOG_DEBUG(" Group %d size %d", group_id_, expected_group_size);
  if (expected_group_size > 1) {
    return types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE;
  }

  LOG_DEBUG("audio location 0x%04lx", snk_audio_locations_.to_ulong());
  if (!(snk_audio_locations_.to_ulong() &
        codec_spec_conf::kLeAudioLocationAnyLeft) ||
      !(snk_audio_locations_.to_ulong() &
        codec_spec_conf::kLeAudioLocationAnyRight)) {
    return types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE;
  }

  auto device = GetFirstDevice();
  auto channel_count_bitmap =
      device->GetSupportedAudioChannelCounts(types::kLeAudioDirectionSink);
  LOG_DEBUG("Supported channel counts for group %d (device %s) is %d",
            group_id_, ADDRESS_TO_LOGGABLE_CSTR(device->address_),
            channel_count_bitmap);
  if (channel_count_bitmap == 1) {
    return types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE;
  }

  return types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE;
}

int LeAudioDeviceGroup::GetAseCount(uint8_t direction) const {
  int result = 0;
  for (const auto& device_iter : leAudioDevices_) {
    result += device_iter.lock()->GetAseCount(direction);
  }

  return result;
}

void LeAudioDeviceGroup::CigConfiguration::GenerateCisIds(
    LeAudioContextType context_type) {
  LOG_INFO("Group %p, group_id: %d, context_type: %s", group_,
           group_->group_id_,
           bluetooth::common::ToString(context_type).c_str());

  if (cises.size() > 0) {
    LOG_INFO("CIS IDs already generated");
    return;
  }

  uint8_t cis_count_bidir = 0;
  uint8_t cis_count_unidir_sink = 0;
  uint8_t cis_count_unidir_source = 0;
  int csis_group_size = 0;

  if (bluetooth::csis::CsisClient::IsCsisClientRunning()) {
    csis_group_size =
        bluetooth::csis::CsisClient::Get()->GetDesiredSize(group_->group_id_);
  }
  /* If this is CSIS group, the csis_group_size will be > 0, otherwise -1.
   * If the last happen it means, group size is 1 */
  int group_size = csis_group_size > 0 ? csis_group_size : 1;

  set_configurations::get_cis_count(
      context_type, group_size, group_->GetGroupStrategy(group_size),
      group_->GetAseCount(types::kLeAudioDirectionSink),
      group_->GetAseCount(types::kLeAudioDirectionSource), cis_count_bidir,
      cis_count_unidir_sink, cis_count_unidir_source);

  uint8_t idx = 0;
  while (cis_count_bidir > 0) {
    struct le_audio::types::cis cis_entry = {
        .id = idx,
        .type = CisType::CIS_TYPE_BIDIRECTIONAL,
        .conn_handle = 0,
        .addr = RawAddress::kEmpty,
    };
    cises.push_back(cis_entry);
    cis_count_bidir--;
    idx++;
  }

  while (cis_count_unidir_sink > 0) {
    struct le_audio::types::cis cis_entry = {
        .id = idx,
        .type = CisType::CIS_TYPE_UNIDIRECTIONAL_SINK,
        .conn_handle = 0,
        .addr = RawAddress::kEmpty,
    };
    cises.push_back(cis_entry);
    cis_count_unidir_sink--;
    idx++;
  }

  while (cis_count_unidir_source > 0) {
    struct le_audio::types::cis cis_entry = {
        .id = idx,
        .type = CisType::CIS_TYPE_UNIDIRECTIONAL_SOURCE,
        .conn_handle = 0,
        .addr = RawAddress::kEmpty,
    };
    cises.push_back(cis_entry);
    cis_count_unidir_source--;
    idx++;
  }
}

bool LeAudioDeviceGroup::CigConfiguration::AssignCisIds(
    LeAudioDevice* leAudioDevice) {
  ASSERT_LOG(leAudioDevice, "invalid device");
  LOG_INFO("device: %s", ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));

  struct ase* ase = leAudioDevice->GetFirstActiveAse();
  if (!ase) {
    LOG_ERROR(" Device %s shouldn't be called without an active ASE",
              ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
    return false;
  }

  for (; ase != nullptr; ase = leAudioDevice->GetNextActiveAse(ase)) {
    uint8_t cis_id = kInvalidCisId;
    /* CIS ID already set */
    if (ase->cis_id != kInvalidCisId) {
      LOG_INFO("ASE ID: %d, is already assigned CIS ID: %d, type %d", ase->id,
               ase->cis_id, cises[ase->cis_id].type);
      if (!cises[ase->cis_id].addr.IsEmpty()) {
        LOG_INFO("Bi-Directional CIS already assigned");
        continue;
      }
      /* Reuse existing CIS ID if available*/
      cis_id = ase->cis_id;
    }

    /* First check if we have bidirectional ASEs. If so, assign same CIS ID.*/
    struct ase* matching_bidir_ase =
        leAudioDevice->GetNextActiveAseWithDifferentDirection(ase);

    for (; matching_bidir_ase != nullptr;
         matching_bidir_ase = leAudioDevice->GetNextActiveAseWithSameDirection(
             matching_bidir_ase)) {
      if ((matching_bidir_ase->cis_id != kInvalidCisId) &&
          (matching_bidir_ase->cis_id != cis_id)) {
        LOG_INFO("Bi-Directional CIS is already used. ASE Id: %d cis_id=%d",
                 matching_bidir_ase->id, matching_bidir_ase->cis_id);
        continue;
      }
      break;
    }

    if (matching_bidir_ase) {
      if (cis_id == kInvalidCisId) {
        cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_BIDIRECTIONAL);
      }

      if (cis_id != kInvalidCisId) {
        ase->cis_id = cis_id;
        matching_bidir_ase->cis_id = cis_id;
        cises[cis_id].addr = leAudioDevice->address_;

        LOG_INFO(
            " ASE ID: %d and ASE ID: %d, assigned Bi-Directional CIS ID: %d",
            +ase->id, +matching_bidir_ase->id, +ase->cis_id);
        continue;
      }

      LOG_WARN(
          " ASE ID: %d, unable to get free Bi-Directional CIS ID but maybe "
          "thats fine. Try using unidirectional.",
          ase->id);
    }

    if (ase->direction == types::kLeAudioDirectionSink) {
      if (cis_id == kInvalidCisId) {
        cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_UNIDIRECTIONAL_SINK);
      }

      if (cis_id == kInvalidCisId) {
        LOG_WARN(
            " Unable to get free Uni-Directional Sink CIS ID - maybe there is "
            "bi-directional available");
        /* This could happen when scenarios for given context type allows for
         * Sink and Source configuration but also only Sink configuration.
         */
        cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_BIDIRECTIONAL);
        if (cis_id == kInvalidCisId) {
          LOG_ERROR("Unable to get free Uni-Directional Sink CIS ID");
          return false;
        }
      }

      ase->cis_id = cis_id;
      cises[cis_id].addr = leAudioDevice->address_;
      LOG_INFO("ASE ID: %d, assigned Uni-Directional Sink CIS ID: %d", ase->id,
               ase->cis_id);
      continue;
    }

    /* Source direction */
    ASSERT_LOG(ase->direction == types::kLeAudioDirectionSource,
               "Expected Source direction, actual=%d", ase->direction);

    if (cis_id == kInvalidCisId) {
      cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_UNIDIRECTIONAL_SOURCE);
    }

    if (cis_id == kInvalidCisId) {
      /* This could happen when scenarios for given context type allows for
       * Sink and Source configuration but also only Sink configuration.
       */
      LOG_WARN(
          "Unable to get free Uni-Directional Source CIS ID - maybe there "
          "is bi-directional available");
      cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_BIDIRECTIONAL);
      if (cis_id == kInvalidCisId) {
        LOG_ERROR("Unable to get free Uni-Directional Source CIS ID");
        return false;
      }
    }

    ase->cis_id = cis_id;
    cises[cis_id].addr = leAudioDevice->address_;
    LOG_INFO("ASE ID: %d, assigned Uni-Directional Source CIS ID: %d", ase->id,
             ase->cis_id);
  }

  return true;
}

void LeAudioDeviceGroup::CigConfiguration::AssignCisConnHandles(
    const std::vector<uint16_t>& conn_handles) {
  LOG_INFO("num of cis handles %d", static_cast<int>(conn_handles.size()));
  for (size_t i = 0; i < cises.size(); i++) {
    cises[i].conn_handle = conn_handles[i];
    LOG_INFO("assigning cis[%d] conn_handle: %d", cises[i].id,
             cises[i].conn_handle);
  }
}

void LeAudioDeviceGroup::AssignCisConnHandlesToAses(
    LeAudioDevice* leAudioDevice) {
  ASSERT_LOG(leAudioDevice, "Invalid device");
  LOG_INFO("group: %p, group_id: %d, device: %s", this, group_id_,
           ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));

  /* Assign all CIS connection handles to ases */
  struct le_audio::types::ase* ase =
      leAudioDevice->GetFirstActiveAseByCisAndDataPathState(
          CisState::IDLE, DataPathState::IDLE);
  if (!ase) {
    LOG_WARN("No active ASE with Cis and Data path state set to IDLE");
    return;
  }

  for (; ase != nullptr;
       ase = leAudioDevice->GetFirstActiveAseByCisAndDataPathState(
           CisState::IDLE, DataPathState::IDLE)) {
    auto ases_pair = leAudioDevice->GetAsesByCisId(ase->cis_id);

    if (ases_pair.sink && ases_pair.sink->active) {
      ases_pair.sink->cis_conn_hdl = cig.cises[ase->cis_id].conn_handle;
      ases_pair.sink->cis_state = CisState::ASSIGNED;
    }
    if (ases_pair.source && ases_pair.source->active) {
      ases_pair.source->cis_conn_hdl = cig.cises[ase->cis_id].conn_handle;
      ases_pair.source->cis_state = CisState::ASSIGNED;
    }
  }
}

void LeAudioDeviceGroup::AssignCisConnHandlesToAses(void) {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  ASSERT_LOG(leAudioDevice, "Shouldn't be called without an active device.");

  LOG_INFO("Group %p, group_id %d", this, group_id_);

  /* Assign all CIS connection handles to ases */
  for (; leAudioDevice != nullptr;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    AssignCisConnHandlesToAses(leAudioDevice);
  }
}

void LeAudioDeviceGroup::CigConfiguration::UnassignCis(
    LeAudioDevice* leAudioDevice) {
  ASSERT_LOG(leAudioDevice, "Invalid device");

  LOG_INFO("Group %p, group_id %d, device: %s", group_, group_->group_id_,
           ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));

  for (struct le_audio::types::cis& cis_entry : cises) {
    if (cis_entry.addr == leAudioDevice->address_) {
      cis_entry.addr = RawAddress::kEmpty;
    }
  }
}

bool CheckIfStrategySupported(types::LeAudioConfigurationStrategy strategy,
                              const set_configurations::SetConfiguration& conf,
                              const LeAudioDevice& device) {
  /* Check direction and if audio location allows to create more cises to a
   * single device.
   */
  types::AudioLocations audio_locations =
      (conf.direction == types::kLeAudioDirectionSink)
          ? device.snk_audio_locations_
          : device.src_audio_locations_;

  LOG_DEBUG("strategy: %d, locations: %lu", (int)strategy,
            audio_locations.to_ulong());

  switch (strategy) {
    case types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE:
      return audio_locations.any();
    case types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE:
      if ((audio_locations.to_ulong() &
           codec_spec_conf::kLeAudioLocationAnyLeft) &&
          (audio_locations.to_ulong() &
           codec_spec_conf::kLeAudioLocationAnyRight))
        return true;
      else
        return false;
    case types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE: {
      if (!(audio_locations.to_ulong() &
            codec_spec_conf::kLeAudioLocationAnyLeft) ||
          !(audio_locations.to_ulong() &
            codec_spec_conf::kLeAudioLocationAnyRight))
        return false;

      auto channel_count_mask =
          device.GetSupportedAudioChannelCounts(conf.direction);
      auto requested_channel_count = conf.codec.params.GetAsCoreCodecConfig()
                                         .GetChannelCountPerIsoStream();
      LOG_DEBUG("Requested channel count: %d, supp. channel counts: %s",
                requested_channel_count, loghex(channel_count_mask).c_str());

      /* Return true if requested channel count is set in the supported channel
       * counts. In the channel_count_mask, bit 0 is set when 1 channel is
       * supported.
       */
      return ((1 << (requested_channel_count - 1)) & channel_count_mask);
    }
    default:
      return false;
  }

  return false;
}

/* This method check if group support given audio configuration
 * requirement for connected devices in the group and available ASEs
 * (no matter on the ASE state) and for given context type
 */
bool LeAudioDeviceGroup::IsAudioSetConfigurationSupported(
    const set_configurations::AudioSetConfiguration* audio_set_conf,
    LeAudioContextType context_type,
    types::LeAudioConfigurationStrategy required_snk_strategy) const {
  /* When at least one device supports the configuration context, configure
   * for these devices only. Otherwise configure for all devices - we will
   * not put this context into the metadata if not supported.
   */
  auto num_of_connected = NumOfConnected(context_type);
  if (num_of_connected == 0) {
    num_of_connected = NumOfConnected();
  }
  if (!set_configurations::check_if_may_cover_scenario(audio_set_conf,
                                                       num_of_connected)) {
    LOG_DEBUG(" cannot cover scenario  %s, num. of connected: %d",
              bluetooth::common::ToString(context_type).c_str(),
              +num_of_connected);
    return false;
  }

  /* TODO For now: set ase if matching with first pac.
   * 1) We assume as well that devices will match requirements in order
   *    e.g. 1 Device - 1 Requirement, 2 Device - 2 Requirement etc.
   * 2) ASEs should be active only if best (according to priority list) full
   *    scenarion will be covered.
   * 3) ASEs should be filled according to performance profile.
   */
  for (const auto& ent : (*audio_set_conf).confs) {
    LOG_DEBUG(" Looking for configuration: %s - %s",
              audio_set_conf->name.c_str(),
              (ent.direction == types::kLeAudioDirectionSink ? "snk" : "src"));

    uint8_t required_device_cnt = ent.device_cnt;
    uint8_t max_required_ase_per_dev =
        ent.ase_cnt / ent.device_cnt + (ent.ase_cnt % ent.device_cnt);
    uint8_t active_ase_num = 0;
    auto strategy = ent.strategy;

    LOG_DEBUG(
        " Number of devices: %d, number of ASEs: %d,  Max ASE per device: %d "
        "strategy: %d",
        +required_device_cnt, +ent.ase_cnt, +max_required_ase_per_dev,
        static_cast<int>(strategy));

    if (ent.direction == types::kLeAudioDirectionSink &&
        strategy != required_snk_strategy) {
      LOG_DEBUG(" Sink strategy mismatch group!=cfg.entry (%d!=%d)",
                static_cast<int>(required_snk_strategy),
                static_cast<int>(strategy));
      return false;
    }

    for (auto* device = GetFirstDevice();
         device != nullptr && required_device_cnt > 0;
         device = GetNextDevice(device)) {
      /* Skip if device has ASE configured in this direction already */

      if (device->ases_.empty()) continue;

      if (!device->GetCodecConfigurationSupportedPac(ent.direction,
                                                     ent.codec)) {
        LOG_DEBUG("Insufficient PAC");
        continue;
      }

      int needed_ase = std::min(static_cast<int>(max_required_ase_per_dev),
                                static_cast<int>(ent.ase_cnt - active_ase_num));

      if (!CheckIfStrategySupported(strategy, ent, *device)) {
        LOG_DEBUG("Strategy not supported");
        continue;
      }

      for (auto& ase : device->ases_) {
        if (ase.direction != ent.direction) continue;

        active_ase_num++;
        needed_ase--;

        if (needed_ase == 0) break;
      }

      if (needed_ase > 0) {
        LOG_DEBUG("Device has too less ASEs. Still needed ases %d", needed_ase);
        return false;
      }

      required_device_cnt--;
    }

    if (required_device_cnt > 0) {
      /* Don't left any active devices if requirements are not met */
      LOG_DEBUG(" could not configure all the devices");
      return false;
    }
  }

  /* when disabling 32k dual mic, for later join case, we need to
   * make sure the device is always choosing the config that its
   * sampling rate matches with the sampling rate which is used
   * when all devices in the group are connected.
   */
  bool dual_bidirection_swb_supported_ =
      AudioSetConfigurationProvider::Get()->IsDualBiDirSwbSupported();
  if (Size() > 1 &&
      AudioSetConfigurationProvider::Get()->CheckConfigurationIsBiDirSwb(
          *audio_set_conf)) {
    if (!dual_bidirection_swb_supported_ ||
        (CodecManager::GetInstance()->GetCodecLocation() ==
             types::CodecLocation::ADSP &&
         !CodecManager::GetInstance()->IsOffloadDualBiDirSwbSupported())) {
      /* two conditions
       * 1) dual bidirection swb is not supported for both software/offload
       * 2) offload not supported
       */
      return false;
    }
  }

  LOG_DEBUG("Chosen ASE Configuration for group: %d, configuration: %s",
            this->group_id_, audio_set_conf->name.c_str());
  return true;
}

/* This method should choose aproperiate ASEs to be active and set a cached
 * configuration for codec and qos.
 */
bool LeAudioDeviceGroup::ConfigureAses(
    const set_configurations::AudioSetConfiguration* audio_set_conf,
    LeAudioContextType context_type,
    const types::BidirectionalPair<AudioContexts>& metadata_context_types,
    const types::BidirectionalPair<std::vector<uint8_t>>& ccid_lists) {
  /* When at least one device supports the configuration context, configure
   * for these devices only. Otherwise configure for all devices - we will
   * not put this context into the metadata if not supported.
   */
  auto num_of_connected = NumOfConnected(context_type);
  if (num_of_connected == 0) {
    num_of_connected = NumOfConnected();
  }
  if (!set_configurations::check_if_may_cover_scenario(audio_set_conf,
                                                       num_of_connected)) {
    return false;
  }

  bool reuse_cis_id =
      GetState() == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED;

  /* TODO For now: set ase if matching with first pac.
   * 1) We assume as well that devices will match requirements in order
   *    e.g. 1 Device - 1 Requirement, 2 Device - 2 Requirement etc.
   * 2) ASEs should be active only if best (according to priority list) full
   *    scenarion will be covered.
   * 3) ASEs should be filled according to performance profile.
   */

  // WARNING: This may look like the results stored here are unused, but it
  //          actually shares the intermediate values between the multiple
  //          configuration calls within the configuration loop.
  BidirectionalPair<types::AudioLocations> group_audio_locations_memo = {
      .sink = 0, .source = 0};

  for (const auto& ent : (*audio_set_conf).confs) {
    LOG_DEBUG(" Looking for requirements: %s,  - %s",
              audio_set_conf->name.c_str(),
              (ent.direction == 1 ? "snk" : "src"));

    uint8_t required_device_cnt = ent.device_cnt;
    uint8_t max_required_ase_per_dev =
        ent.ase_cnt / ent.device_cnt + (ent.ase_cnt % ent.device_cnt);
    uint8_t active_ase_num = 0;
    le_audio::types::LeAudioConfigurationStrategy strategy = ent.strategy;

    LOG_DEBUG(
        "Number of devices: %d number of ASEs: %d, Max ASE per device: %d "
        "strategy: %d",
        required_device_cnt, ent.ase_cnt, max_required_ase_per_dev,
        (int)strategy);

    auto configuration_closure = [&](LeAudioDevice* dev) -> void {
      /* For the moment, we configure only connected devices and when it is
       * ready to stream i.e. All ASEs are discovered and dev is reported as
       * connected
       */
      if (dev->GetConnectionState() != DeviceConnectState::CONNECTED) {
        LOG_WARN(
            "Device %s, in the state %s",
            ADDRESS_TO_LOGGABLE_CSTR(dev->address_),
            bluetooth::common::ToString(dev->GetConnectionState()).c_str());
        return;
      }

      if (!dev->ConfigureAses(ent, context_type, &active_ase_num,
                              group_audio_locations_memo,
                              metadata_context_types, ccid_lists, reuse_cis_id))
        return;

      required_device_cnt--;
    };

    // First use the devices claiming proper support
    for (auto* device = GetFirstDeviceWithAvailableContext(context_type);
         device != nullptr && required_device_cnt > 0;
         device = GetNextDeviceWithAvailableContext(device, context_type)) {
      configuration_closure(device);
    }
    // In case some devices do not support this scenario - us them anyway if
    // they are required for the scenario - we will not put this context into
    // their metadata anyway
    if (required_device_cnt > 0) {
      for (auto* device = GetFirstDevice();
           device != nullptr && required_device_cnt > 0;
           device = GetNextDevice(device)) {
        configuration_closure(device);
      }
    }

    if (required_device_cnt > 0) {
      /* Don't left any active devices if requirements are not met */
      LOG_ERROR(" could not configure all the devices");
      Deactivate();
      return false;
    }
  }

  LOG_INFO("Choosed ASE Configuration for group: %d, configuration: %s",
           group_id_, audio_set_conf->name.c_str());

  configuration_context_type_ = context_type;
  metadata_context_type_ = metadata_context_types;
  return true;
}

const set_configurations::AudioSetConfiguration*
LeAudioDeviceGroup::GetCachedConfiguration(
    LeAudioContextType context_type) const {
  if (context_to_configuration_cache_map.count(context_type) != 0) {
    return context_to_configuration_cache_map.at(context_type).second;
  }
  return nullptr;
}

const set_configurations::AudioSetConfiguration*
LeAudioDeviceGroup::GetActiveConfiguration(void) const {
  return GetCachedConfiguration(configuration_context_type_);
}

const set_configurations::AudioSetConfiguration*
LeAudioDeviceGroup::GetConfiguration(LeAudioContextType context_type) {
  if (context_type == LeAudioContextType::UNINITIALIZED) {
    return nullptr;
  }

  const set_configurations::AudioSetConfiguration* conf = nullptr;
  bool is_valid = false;

  /* Refresh the cache if there is no valid configuration */
  if (context_to_configuration_cache_map.count(context_type) != 0) {
    std::tie(is_valid, conf) =
        context_to_configuration_cache_map.at(context_type);
  }
  if (!is_valid || (conf == nullptr)) {
    UpdateAudioSetConfigurationCache(context_type);
  }

  return GetCachedConfiguration(context_type);
}

std::optional<LeAudioCodecConfiguration>
LeAudioDeviceGroup::GetCachedCodecConfigurationByDirection(
    LeAudioContextType context_type, uint8_t direction) const {
  const set_configurations::AudioSetConfiguration* audio_set_conf =
      GetCachedConfiguration(context_type);
  if (!audio_set_conf) return std::nullopt;

  LeAudioCodecConfiguration group_config = {0, 0, 0, 0};
  for (const auto& conf : audio_set_conf->confs) {
    if (conf.direction != direction) continue;

    if (group_config.sample_rate != 0 &&
        conf.codec.GetSamplingFrequencyHz() != group_config.sample_rate) {
      LOG(WARNING) << __func__
                   << ", stream configuration could not be "
                      "determined (sampling frequency differs) for direction: "
                   << loghex(direction);
      return std::nullopt;
    }
    group_config.sample_rate = conf.codec.GetSamplingFrequencyHz();

    if (group_config.data_interval_us != 0 &&
        conf.codec.GetDataIntervalUs() != group_config.data_interval_us) {
      LOG(WARNING) << __func__
                   << ", stream configuration could not be "
                      "determined (data interval differs) for direction: "
                   << loghex(direction);
      return std::nullopt;
    }
    group_config.data_interval_us = conf.codec.GetDataIntervalUs();

    if (group_config.bits_per_sample != 0 &&
        conf.codec.GetBitsPerSample() != group_config.bits_per_sample) {
      LOG(WARNING) << __func__
                   << ", stream configuration could not be "
                      "determined (bits per sample differs) for direction: "
                   << loghex(direction);
      return std::nullopt;
    }
    group_config.bits_per_sample = conf.codec.GetBitsPerSample();

    group_config.num_channels +=
        conf.codec.GetChannelCountPerIsoStream() * conf.device_cnt;
  }

  if (group_config.IsInvalid()) return std::nullopt;

  return group_config;
}

std::optional<LeAudioCodecConfiguration>
LeAudioDeviceGroup::GetCodecConfigurationByDirection(
    LeAudioContextType context_type, uint8_t direction) {
  const set_configurations::AudioSetConfiguration* conf = nullptr;
  bool is_valid = false;

  /* Refresh the cache if there is no valid configuration */
  if (context_to_configuration_cache_map.count(context_type) != 0) {
    std::tie(is_valid, conf) =
        context_to_configuration_cache_map.at(context_type);
  }
  if (!is_valid || (conf == nullptr)) {
    UpdateAudioSetConfigurationCache(context_type);
  }

  /* Return the cached value */
  return GetCachedCodecConfigurationByDirection(context_type, direction);
}

bool LeAudioDeviceGroup::IsAudioSetConfigurationAvailable(
    LeAudioContextType group_context_type) {
  return GetConfiguration(group_context_type) != nullptr;
}

bool LeAudioDeviceGroup::IsMetadataChanged(
    const BidirectionalPair<AudioContexts>& context_types,
    const BidirectionalPair<std::vector<uint8_t>>& ccid_lists) const {
  for (auto* leAudioDevice = GetFirstActiveDevice(); leAudioDevice;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    if (leAudioDevice->IsMetadataChanged(context_types, ccid_lists))
      return true;
  }

  return false;
}

bool LeAudioDeviceGroup::IsCisPartOfCurrentStream(uint16_t cis_conn_hdl) const {
  auto& sink_stream_locations = stream_conf.stream_params.sink.stream_locations;
  auto iter = std::find_if(
      sink_stream_locations.begin(), sink_stream_locations.end(),
      [cis_conn_hdl](auto& pair) { return cis_conn_hdl == pair.first; });

  if (iter != sink_stream_locations.end()) return true;

  auto& source_stream_locations =
      stream_conf.stream_params.source.stream_locations;
  iter = std::find_if(
      source_stream_locations.begin(), source_stream_locations.end(),
      [cis_conn_hdl](auto& pair) { return cis_conn_hdl == pair.first; });

  return (iter != source_stream_locations.end());
}

void LeAudioDeviceGroup::RemoveCisFromStreamIfNeeded(
    LeAudioDevice* leAudioDevice, uint16_t cis_conn_hdl) {
  LOG_INFO(" CIS Connection Handle: %d", cis_conn_hdl);

  if (!IsCisPartOfCurrentStream(cis_conn_hdl)) return;

  /* Cache the old values for comparison */
  auto old_sink_channels = stream_conf.stream_params.sink.num_of_channels;
  auto old_source_channels = stream_conf.stream_params.source.num_of_channels;

  for (auto dir :
       {types::kLeAudioDirectionSink, types::kLeAudioDirectionSource}) {
    auto& params = stream_conf.stream_params.get(dir);
    params.stream_locations.erase(
        std::remove_if(
            params.stream_locations.begin(), params.stream_locations.end(),
            [leAudioDevice, &cis_conn_hdl, &params, dir](auto& pair) {
              if (!cis_conn_hdl) {
                cis_conn_hdl = pair.first;
              }
              auto ases_pair = leAudioDevice->GetAsesByCisConnHdl(cis_conn_hdl);
              if (ases_pair.get(dir) && cis_conn_hdl == pair.first) {
                params.num_of_devices--;
                params.num_of_channels -=
                    ases_pair.get(dir)
                        ->codec_config.GetAsCoreCodecConfig()
                        .GetChannelCountPerIsoStream();
                params.audio_channel_allocation &= ~pair.second;
              }
              return (ases_pair.get(dir) && cis_conn_hdl == pair.first);
            }),
        params.stream_locations.end());
  }

  LOG_INFO(
      " Sink Number Of Devices: %d"
      ", Sink Number Of Channels: %d"
      ", Source Number Of Devices: %d"
      ", Source Number Of Channels: %d",
      stream_conf.stream_params.sink.num_of_devices,
      stream_conf.stream_params.sink.num_of_channels,
      stream_conf.stream_params.source.num_of_devices,
      stream_conf.stream_params.source.num_of_channels);

  if (stream_conf.stream_params.sink.num_of_channels == 0) {
    ClearSinksFromConfiguration();
  }

  if (stream_conf.stream_params.source.num_of_channels == 0) {
    ClearSourcesFromConfiguration();
  }

  /* Update CodecManager CIS configuration */
  if (old_sink_channels > stream_conf.stream_params.sink.num_of_channels) {
    CodecManager::GetInstance()->UpdateCisConfiguration(
        cig.cises,
        stream_conf.stream_params.get(le_audio::types::kLeAudioDirectionSink),
        le_audio::types::kLeAudioDirectionSink);
  }
  if (old_source_channels > stream_conf.stream_params.source.num_of_channels) {
    CodecManager::GetInstance()->UpdateCisConfiguration(
        cig.cises,
        stream_conf.stream_params.get(le_audio::types::kLeAudioDirectionSource),
        le_audio::types::kLeAudioDirectionSource);
  }

  cig.UnassignCis(leAudioDevice);
}

bool LeAudioDeviceGroup::IsPendingConfiguration(void) const {
  return stream_conf.pending_configuration;
}

void LeAudioDeviceGroup::SetPendingConfiguration(void) {
  stream_conf.pending_configuration = true;
}

void LeAudioDeviceGroup::ClearPendingConfiguration(void) {
  stream_conf.pending_configuration = false;
}

void LeAudioDeviceGroup::Disable(int gatt_if) {
  is_enabled_ = false;

  for (auto& device_iter : leAudioDevices_) {
    if (!device_iter.lock()->autoconnect_flag_) {
      continue;
    }

    auto connection_state = device_iter.lock()->GetConnectionState();
    auto address = device_iter.lock()->address_;

    btif_storage_set_leaudio_autoconnect(address, false);
    device_iter.lock()->autoconnect_flag_ = false;

    LOG_INFO("Group %d in state %s. Removing %s from background connect",
             group_id_, bluetooth::common::ToString(GetState()).c_str(),
             ADDRESS_TO_LOGGABLE_CSTR(address));

    BTA_GATTC_CancelOpen(gatt_if, address, false);

    if (connection_state == DeviceConnectState::CONNECTING_AUTOCONNECT) {
      device_iter.lock()->SetConnectionState(DeviceConnectState::DISCONNECTED);
    }
  }
}

void LeAudioDeviceGroup::Enable(int gatt_if,
                                tBTM_BLE_CONN_TYPE reconnection_mode) {
  is_enabled_ = true;
  for (auto& device_iter : leAudioDevices_) {
    if (device_iter.lock()->autoconnect_flag_) {
      continue;
    }

    auto address = device_iter.lock()->address_;
    auto connection_state = device_iter.lock()->GetConnectionState();

    btif_storage_set_leaudio_autoconnect(address, true);
    device_iter.lock()->autoconnect_flag_ = true;

    LOG_INFO("Group %d in state %s. Adding %s from background connect",
             group_id_, bluetooth::common::ToString(GetState()).c_str(),
             ADDRESS_TO_LOGGABLE_CSTR(address));

    if (connection_state == DeviceConnectState::DISCONNECTED) {
      BTA_GATTC_Open(gatt_if, address, reconnection_mode, false);
      device_iter.lock()->SetConnectionState(
          DeviceConnectState::CONNECTING_AUTOCONNECT);
    }
  }
}

bool LeAudioDeviceGroup::IsEnabled(void) const { return is_enabled_; };

void LeAudioDeviceGroup::AddToAllowListNotConnectedGroupMembers(int gatt_if) {
  for (const auto& device_iter : leAudioDevices_) {
    auto connection_state = device_iter.lock()->GetConnectionState();
    if (connection_state == DeviceConnectState::CONNECTED ||
        connection_state == DeviceConnectState::CONNECTING_BY_USER ||
        connection_state ==
            DeviceConnectState::CONNECTED_BY_USER_GETTING_READY ||
        connection_state ==
            DeviceConnectState::CONNECTED_AUTOCONNECT_GETTING_READY) {
      continue;
    }

    auto address = device_iter.lock()->address_;
    LOG_INFO("Group %d in state %s. Adding %s to allow list ", group_id_,
             bluetooth::common::ToString(GetState()).c_str(),
             ADDRESS_TO_LOGGABLE_CSTR(address));

    /* When adding set members to allow list, let use direct connect first.
     * When it fails (i.e. device is not advertising), it will go to background
     * connect. We are doing that because for background connect, stack is using
     * slow scan parameters for connection which might delay connecting
     * available members.
     */
    BTA_GATTC_CancelOpen(gatt_if, address, false);
    BTA_GATTC_Open(gatt_if, address, BTM_BLE_DIRECT_CONNECTION, false);
    device_iter.lock()->SetConnectionState(
        DeviceConnectState::CONNECTING_AUTOCONNECT);
  }
}

void LeAudioDeviceGroup::ApplyReconnectionMode(
    int gatt_if, tBTM_BLE_CONN_TYPE reconnection_mode) {
  for (const auto& device_iter : leAudioDevices_) {
    BTA_GATTC_CancelOpen(gatt_if, device_iter.lock()->address_, false);
    BTA_GATTC_Open(gatt_if, device_iter.lock()->address_, reconnection_mode,
                   false);
    LOG_INFO("Group %d in state %s. Adding %s to default reconnection mode ",
             group_id_, bluetooth::common::ToString(GetState()).c_str(),
             ADDRESS_TO_LOGGABLE_CSTR(device_iter.lock()->address_));
    device_iter.lock()->SetConnectionState(
        DeviceConnectState::CONNECTING_AUTOCONNECT);
  }
}

bool LeAudioDeviceGroup::IsConfiguredForContext(
    LeAudioContextType context_type) const {
  /* Check if all connected group members are configured */
  if (GetConfigurationContextType() != context_type) {
    return false;
  }

  /* Check if used configuration is same as the active one.*/
  return (stream_conf.conf == GetActiveConfiguration());
}

bool LeAudioDeviceGroup::IsAudioSetConfigurationSupported(
    LeAudioDevice* leAudioDevice,
    const set_configurations::AudioSetConfiguration* audio_set_conf) const {
  for (const auto& ent : (*audio_set_conf).confs) {
    LOG_INFO("Looking for requirements: %s - %s", audio_set_conf->name.c_str(),
             (ent.direction == 1 ? "snk" : "src"));
    auto pac = leAudioDevice->GetCodecConfigurationSupportedPac(ent.direction,
                                                                ent.codec);
    if (pac != nullptr) {
      LOG_INFO("Configuration is supported by device %s",
               ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
      return true;
    }
  }

  LOG_INFO("Configuration is NOT supported by device %s",
           ADDRESS_TO_LOGGABLE_CSTR(leAudioDevice->address_));
  return false;
}

const set_configurations::AudioSetConfiguration*
LeAudioDeviceGroup::FindFirstSupportedConfiguration(
    LeAudioContextType context_type) const {
  const set_configurations::AudioSetConfigurations* confs =
      AudioSetConfigurationProvider::Get()->GetConfigurations(context_type);

  LOG_DEBUG("context type: %s,  number of connected devices: %d",
            bluetooth::common::ToString(context_type).c_str(),
            +NumOfConnected());

  auto num_of_connected = NumOfConnected(context_type);
  if (num_of_connected == 0) {
    num_of_connected = NumOfConnected();
  }
  /* Filter out device set for all scenarios */
  if (!set_configurations::check_if_may_cover_scenario(confs,
                                                       num_of_connected)) {
    LOG_DEBUG(", group is unable to cover scenario");
    return nullptr;
  }

  /* Filter out device set for each end every scenario */

  auto required_snk_strategy = GetGroupStrategy(Size());
  for (const auto& conf : *confs) {
    if (IsAudioSetConfigurationSupported(conf, context_type,
                                         required_snk_strategy)) {
      LOG_DEBUG("found: %s", conf->name.c_str());
      return conf;
    }
  }

  return nullptr;
}

/* This method should choose aproperiate ASEs to be active and set a cached
 * configuration for codec and qos.
 */
bool LeAudioDeviceGroup::Configure(
    LeAudioContextType context_type,
    const types::BidirectionalPair<AudioContexts>& metadata_context_types,
    types::BidirectionalPair<std::vector<uint8_t>> ccid_lists) {
  auto conf = GetConfiguration(context_type);
  if (!conf) {
    LOG_ERROR(
        ", requested context type: %s , is in mismatch with cached available "
        "contexts ",
        bluetooth::common::ToString(context_type).c_str());
    return false;
  }

  LOG_DEBUG(" setting context type: %s",
            bluetooth::common::ToString(context_type).c_str());

  if (!ConfigureAses(conf, context_type, metadata_context_types, ccid_lists)) {
    LOG_ERROR(
        ", requested context type: %s , is in mismatch with cached available "
        "contexts",
        bluetooth::common::ToString(context_type).c_str());
    return false;
  }

  /* Store selected configuration at once it is chosen.
   * It might happen it will get unavailable in some point of time
   */
  stream_conf.conf = conf;
  return true;
}

LeAudioDeviceGroup::~LeAudioDeviceGroup(void) { this->Cleanup(); }

void LeAudioDeviceGroup::PrintDebugState(void) const {
  auto* active_conf = GetActiveConfiguration();
  std::stringstream debug_str;

  debug_str << "\n Groupd id: " << group_id_
            << (is_enabled_ ? " enabled" : " disabled")
            << ", state: " << bluetooth::common::ToString(GetState())
            << ", target state: "
            << bluetooth::common::ToString(GetTargetState())
            << ", cig state: " << bluetooth::common::ToString(cig.GetState())
            << ", \n group supported contexts: "
            << bluetooth::common::ToString(GetSupportedContexts())
            << ", \n group available contexts: "
            << bluetooth::common::ToString(GetAvailableContexts())
            << ", \n configuration context type: "
            << bluetooth::common::ToString(GetConfigurationContextType())
            << ", \n active configuration name: "
            << (active_conf ? active_conf->name : " not set");

  if (cig.cises.size() > 0) {
    LOG_INFO("\n Allocated CISes: %d", static_cast<int>(cig.cises.size()));
    for (auto cis : cig.cises) {
      LOG_INFO("\n cis id: %d, type: %d, conn_handle %d, addr: %s", cis.id,
               cis.type, cis.conn_handle, cis.addr.ToString().c_str());
    }
  }

  if (GetFirstActiveDevice() != nullptr) {
    uint32_t sink_delay = 0;
    uint32_t source_delay = 0;
    GetPresentationDelay(&sink_delay, le_audio::types::kLeAudioDirectionSink);
    GetPresentationDelay(&source_delay,
                         le_audio::types::kLeAudioDirectionSource);
    auto phy_mtos = GetPhyBitmask(le_audio::types::kLeAudioDirectionSink);
    auto phy_stom = GetPhyBitmask(le_audio::types::kLeAudioDirectionSource);
    auto max_transport_latency_mtos = GetMaxTransportLatencyMtos();
    auto max_transport_latency_stom = GetMaxTransportLatencyStom();
    auto sdu_mts = GetSduInterval(le_audio::types::kLeAudioDirectionSink);
    auto sdu_stom = GetSduInterval(le_audio::types::kLeAudioDirectionSource);

    debug_str << "\n presentation_delay for sink (speaker): " << +sink_delay
              << " us, presentation_delay for source (microphone): "
              << +source_delay << "us, \n MtoS transport latency:  "
              << +max_transport_latency_mtos
              << ", StoM transport latency: " << +max_transport_latency_stom
              << ", \n MtoS Phy: " << loghex(phy_mtos)
              << ", MtoS sdu: " << loghex(phy_stom)
              << " \n MtoS sdu: " << +sdu_mts << ", StoM sdu: " << +sdu_stom;
  }

  LOG_INFO("%s", debug_str.str().c_str());

  for (const auto& device_iter : leAudioDevices_) {
    device_iter.lock()->PrintDebugState();
  }
}

void LeAudioDeviceGroup::Dump(int fd, int active_group_id) const {
  bool is_active = (group_id_ == active_group_id);
  std::stringstream stream, stream_pacs;
  auto* active_conf = GetActiveConfiguration();

  stream << "\n    == Group id: " << group_id_
         << (is_enabled_ ? " enabled" : " disabled")
         << " == " << (is_active ? ",\tActive\n" : ",\tInactive\n")
         << "      state: " << GetState()
         << ",\ttarget state: " << GetTargetState()
         << ",\tcig state: " << cig.GetState() << "\n"
         << "      group supported contexts: " << GetSupportedContexts() << "\n"
         << "      group available contexts: " << GetAvailableContexts() << "\n"
         << "      configuration context type: "
         << bluetooth::common::ToString(GetConfigurationContextType()).c_str()
         << "\n"
         << "      active configuration name: "
         << (active_conf ? active_conf->name : " not set") << "\n"
         << "      stream configuration: "
         << (stream_conf.conf != nullptr ? stream_conf.conf->name : " unknown ")
         << "\n"
         << "      codec id: " << +(stream_conf.codec_id.coding_format)
         << ",\tpending_configuration: " << stream_conf.pending_configuration
         << "\n"
         << "      num of devices(connected): " << Size() << "("
         << NumOfConnected() << ")\n"
         << ",     num of sinks(connected): "
         << stream_conf.stream_params.sink.num_of_devices << "("
         << stream_conf.stream_params.sink.stream_locations.size() << ")\n"
         << "      num of sources(connected): "
         << stream_conf.stream_params.source.num_of_devices << "("
         << stream_conf.stream_params.source.stream_locations.size() << ")\n"
         << "      allocated CISes: " << static_cast<int>(cig.cises.size());

  if (cig.cises.size() > 0) {
    stream << "\n\t == CISes == ";
    for (auto cis : cig.cises) {
      stream << "\n\t cis id: " << static_cast<int>(cis.id)
             << ",\ttype: " << static_cast<int>(cis.type)
             << ",\tconn_handle: " << static_cast<int>(cis.conn_handle)
             << ",\taddr: " << ADDRESS_TO_LOGGABLE_STR(cis.addr);
    }
    stream << "\n\t ====";
  }

  if (GetFirstActiveDevice() != nullptr) {
    uint32_t sink_delay;
    if (GetPresentationDelay(&sink_delay,
                             le_audio::types::kLeAudioDirectionSink)) {
      stream << "\n      presentation_delay for sink (speaker): " << sink_delay
             << " us";
    }

    uint32_t source_delay;
    if (GetPresentationDelay(&source_delay,
                             le_audio::types::kLeAudioDirectionSource)) {
      stream << "\n      presentation_delay for source (microphone): "
             << source_delay << " us";
    }
  }

  stream << "\n      == devices: ==";

  dprintf(fd, "%s", stream.str().c_str());

  for (const auto& device_iter : leAudioDevices_) {
    device_iter.lock()->Dump(fd);
  }

  for (const auto& device_iter : leAudioDevices_) {
    auto device = device_iter.lock();
    stream_pacs << "\n\taddress: " << device->address_;
    device->DumpPacsDebugState(stream_pacs);
  }
  dprintf(fd, "%s", stream_pacs.str().c_str());
}

LeAudioDeviceGroup* LeAudioDeviceGroups::Add(int group_id) {
  /* Get first free group id */
  if (FindById(group_id)) {
    LOG(ERROR) << __func__
               << ", group already exists, id: " << loghex(group_id);
    return nullptr;
  }

  return (groups_.emplace_back(std::make_unique<LeAudioDeviceGroup>(group_id)))
      .get();
}

void LeAudioDeviceGroups::Remove(int group_id) {
  auto iter = std::find_if(
      groups_.begin(), groups_.end(),
      [&group_id](auto const& group) { return group->group_id_ == group_id; });

  if (iter == groups_.end()) {
    LOG(ERROR) << __func__ << ", no such group_id: " << group_id;
    return;
  }

  groups_.erase(iter);
}

LeAudioDeviceGroup* LeAudioDeviceGroups::FindById(int group_id) const {
  auto iter = std::find_if(
      groups_.begin(), groups_.end(),
      [&group_id](auto const& group) { return group->group_id_ == group_id; });

  return (iter == groups_.end()) ? nullptr : iter->get();
}

void LeAudioDeviceGroups::Cleanup(void) {
  for (auto& g : groups_) {
    g->Cleanup();
  }

  groups_.clear();
}

void LeAudioDeviceGroups::Dump(int fd, int active_group_id) const {
  /* Dump first active group */
  for (auto& g : groups_) {
    if (g->group_id_ == active_group_id) {
      g->Dump(fd, active_group_id);
      break;
    }
  }

  /* Dump non active group */
  for (auto& g : groups_) {
    if (g->group_id_ != active_group_id) {
      g->Dump(fd, active_group_id);
    }
  }
}

bool LeAudioDeviceGroups::IsAnyInTransition(void) const {
  for (auto& g : groups_) {
    if (g->IsInTransition()) {
      DLOG(INFO) << __func__ << " group: " << g->group_id_
                 << " is in transition";
      return true;
    }
  }
  return false;
}

size_t LeAudioDeviceGroups::Size() const { return (groups_.size()); }

std::vector<int> LeAudioDeviceGroups::GetGroupsIds(void) const {
  std::vector<int> result;

  for (auto const& group : groups_) {
    result.push_back(group->group_id_);
  }

  return result;
}

}  // namespace le_audio
