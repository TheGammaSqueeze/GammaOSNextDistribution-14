/*
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

#pragma once

#include <base/logging.h>

#include <map>
#include <memory>
#include <optional>
#include <tuple>
#include <utility>  // for std::pair
#include <vector>

#ifdef __ANDROID__
#include <android/sysprop/BluetoothProperties.sysprop.h>
#endif

#include "audio_hal_client/audio_hal_client.h"
#include "bta_groups.h"
#include "btm_iso_api_types.h"
#include "common/strings.h"
#include "gatt_api.h"
#include "le_audio_log_history.h"
#include "le_audio_types.h"
#include "os/log.h"
#include "osi/include/alarm.h"
#include "osi/include/properties.h"
#include "raw_address.h"

namespace le_audio {

// Maps to BluetoothProfile#LE_AUDIO
#define LE_AUDIO_PROFILE_CONSTANT 22

/* Enums */
enum class DeviceConnectState : uint8_t {
  /* Initial state */
  DISCONNECTED,
  /* When ACL connected, encrypted, CCC registered and initial characteristics
     read is completed */
  CONNECTED,
  /* Used when device is unbonding (RemoveDevice() API is called) */
  REMOVING,
  /* Disconnecting */
  DISCONNECTING,
  /* Disconnecting for recover - after that we want direct connect to be
     initiated */
  DISCONNECTING_AND_RECOVER,
  /* 2 states below are used when user creates connection. Connect API is
     called. */
  CONNECTING_BY_USER,
  /* Always used after CONNECTING_BY_USER */
  CONNECTED_BY_USER_GETTING_READY,
  /* 2 states are used when autoconnect was used for the connection.*/
  CONNECTING_AUTOCONNECT,
  /* Always used after CONNECTING_AUTOCONNECT */
  CONNECTED_AUTOCONNECT_GETTING_READY,
};

std::ostream& operator<<(std::ostream& os, const DeviceConnectState& state);

/* Class definitions */

/* LeAudioDevice class represents GATT server device with ASCS, PAC services as
 * mandatory. Device may contain multiple ASEs, PACs, audio locations. ASEs from
 * multiple devices may be formed in group.
 *
 * Device is created after connection or after storage restoration.
 *
 * Active device means that device has at least one ASE which will participate
 * in any state transition of state machine. ASEs and devices will be activated
 * according to requested by upper context type.
 */
class LeAudioDevice {
 public:
  RawAddress address_;

  DeviceConnectState connection_state_;
  bool known_service_handles_;
  bool notify_connected_after_read_;
  bool closing_stream_for_disconnection_;
  bool autoconnect_flag_;
  uint16_t conn_id_;
  uint16_t mtu_;
  bool encrypted_;
  int group_id_;
  bool csis_member_;
  int cis_failed_to_be_established_retry_cnt_;
  std::bitset<16> tmap_role_;

  uint8_t audio_directions_;
  types::AudioLocations snk_audio_locations_;
  types::AudioLocations src_audio_locations_;

  types::PublishedAudioCapabilities snk_pacs_;
  types::PublishedAudioCapabilities src_pacs_;

  struct types::hdl_pair snk_audio_locations_hdls_;
  struct types::hdl_pair src_audio_locations_hdls_;
  struct types::hdl_pair audio_avail_hdls_;
  struct types::hdl_pair audio_supp_cont_hdls_;
  std::vector<struct types::ase> ases_;
  struct types::hdl_pair ctp_hdls_;
  uint16_t tmap_role_hdl_;
  std::string model_name_;
  bool allowlist_flag_;

  alarm_t* link_quality_timer;
  uint16_t link_quality_timer_data;

  LeAudioDevice(const RawAddress& address_, DeviceConnectState state,
                int group_id = bluetooth::groups::kGroupUnknown)
      : address_(address_),
        connection_state_(state),
        known_service_handles_(false),
        notify_connected_after_read_(false),
        closing_stream_for_disconnection_(false),
        autoconnect_flag_(false),
        conn_id_(GATT_INVALID_CONN_ID),
        mtu_(0),
        encrypted_(false),
        group_id_(group_id),
        csis_member_(false),
        cis_failed_to_be_established_retry_cnt_(0),
        audio_directions_(0),
        model_name_(""),
        allowlist_flag_(false),
        link_quality_timer(nullptr),
        dsa_({{DsaMode::DISABLED},
              types::DataPathState::IDLE,
              GATT_INVALID_CONN_ID}) {}
  ~LeAudioDevice(void);

  void SetConnectionState(DeviceConnectState state);
  DeviceConnectState GetConnectionState(void);
  void ClearPACs(void);
  void RegisterPACs(std::vector<struct types::acs_ac_record>* apr_db,
                    std::vector<struct types::acs_ac_record>* apr);
  struct types::ase* GetAseByValHandle(uint16_t val_hdl);
  int GetAseCount(uint8_t direction);
  struct types::ase* GetFirstActiveAse(void);
  struct types::ase* GetFirstActiveAseByDirection(uint8_t direction);
  struct types::ase* GetNextActiveAseWithSameDirection(
      struct types::ase* base_ase);
  struct types::ase* GetNextActiveAseWithDifferentDirection(
      struct types::ase* base_ase);
  struct types::ase* GetFirstActiveAseByCisAndDataPathState(
      types::CisState cis_state, types::DataPathState data_path_state);
  struct types::ase* GetFirstInactiveAse(uint8_t direction,
                                         bool reconnect = false);
  struct types::ase* GetFirstAseWithState(uint8_t direction,
                                          types::AseState state);
  struct types::ase* GetNextActiveAse(struct types::ase* ase);
  struct types::ase* GetAseToMatchBidirectionCis(struct types::ase* ase);
  types::BidirectionalPair<struct types::ase*> GetAsesByCisConnHdl(
      uint16_t conn_hdl);
  types::BidirectionalPair<struct types::ase*> GetAsesByCisId(uint8_t cis_id);
  bool HaveActiveAse(void);
  bool HaveAllActiveAsesSameState(types::AseState state);
  bool HaveAllActiveAsesSameDataPathState(types::DataPathState state) const;
  bool HaveAnyUnconfiguredAses(void);
  bool IsReadyToCreateStream(void);
  bool IsReadyToStream(void) const {
    return HaveAllActiveAsesCisEst() &&
           HaveAllActiveAsesSameDataPathState(types::DataPathState::CONFIGURED);
  }
  bool IsReadyToSuspendStream(void);
  bool HaveAllActiveAsesCisEst(void) const;
  bool HaveAnyCisConnected(void);
  const struct types::acs_ac_record* GetCodecConfigurationSupportedPac(
      uint8_t direction,
      const set_configurations::CodecConfigSetting& codec_capability_setting);
  uint8_t GetSupportedAudioChannelCounts(uint8_t direction) const;
  uint8_t GetPhyBitmask(void);
  bool ConfigureAses(
      const le_audio::set_configurations::SetConfiguration& ent,
      types::LeAudioContextType context_type,
      uint8_t* number_of_already_active_group_ase,
      types::BidirectionalPair<types::AudioLocations>&
          group_audio_locations_out,
      const types::BidirectionalPair<types::AudioContexts>&
          metadata_context_types,
      const types::BidirectionalPair<std::vector<uint8_t>>& ccid_lists,
      bool reuse_cis_id);

  inline types::AudioContexts GetSupportedContexts(
      int direction = types::kLeAudioDirectionBoth) const {
    ASSERT_LOG(direction <= (types::kLeAudioDirectionBoth),
               "Invalid direction used.");

    if (direction < types::kLeAudioDirectionBoth)
      return supp_contexts_.get(direction);
    else
      return types::get_bidirectional(supp_contexts_);
  }
  inline void SetSupportedContexts(
      types::BidirectionalPair<types::AudioContexts> contexts) {
    supp_contexts_ = contexts;
  }

  inline types::AudioContexts GetAvailableContexts(
      int direction = types::kLeAudioDirectionBoth) const {
    ASSERT_LOG(direction <= (types::kLeAudioDirectionBoth),
               "Invalid direction used.");

    if (direction < types::kLeAudioDirectionBoth)
      return avail_contexts_.get(direction);
    else
      return types::get_bidirectional(avail_contexts_);
  }
  void SetAvailableContexts(
      types::BidirectionalPair<types::AudioContexts> cont_val);

  void DeactivateAllAses(void);
  bool ActivateConfiguredAses(
      types::LeAudioContextType context_type,
      const types::BidirectionalPair<types::AudioContexts>&
          metadata_context_types,
      types::BidirectionalPair<std::vector<uint8_t>> ccid_lists);
  void SetMetadataToAse(
      struct types::ase* ase,
      const types::BidirectionalPair<types::AudioContexts>&
          metadata_context_types,
      types::BidirectionalPair<std::vector<uint8_t>> ccid_lists);

  void PrintDebugState(void);
  void DumpPacsDebugState(std::stringstream& stream);
  void Dump(int fd);

  void DisconnectAcl(void);
  std::vector<uint8_t> GetMetadata(types::AudioContexts context_type,
                                   const std::vector<uint8_t>& ccid_list);
  bool IsMetadataChanged(
      const types::BidirectionalPair<types::AudioContexts>& context_types,
      const types::BidirectionalPair<std::vector<uint8_t>>& ccid_lists);

  void GetDeviceModelName(void);
  void UpdateDeviceAllowlistFlag(void);
  DsaModes GetDsaModes(void);
  types::DataPathState GetDsaDataPathState(void);
  void SetDsaDataPathState(types::DataPathState state);
  uint16_t GetDsaCisHandle(void);
  void SetDsaCisHandle(uint16_t cis_handle);

 private:
  types::BidirectionalPair<types::AudioContexts> avail_contexts_;
  types::BidirectionalPair<types::AudioContexts> supp_contexts_;
  struct {
    DsaModes modes;
    types::DataPathState state;
    uint16_t cis_handle;
  } dsa_;

  static constexpr char kLeAudioDeviceAllowListProp[] =
      "persist.bluetooth.leaudio.allow_list";

  void DumpPacsDebugState(std::stringstream& stream,
                          types::PublishedAudioCapabilities pacs);
};

/* LeAudioDevices class represents a wraper helper over all devices in le audio
 * implementation. It allows to operate on device from a list (vector container)
 * using determinants like address, connection id etc.
 */
class LeAudioDevices {
 public:
  void Add(const RawAddress& address, le_audio::DeviceConnectState state,
           int group_id = bluetooth::groups::kGroupUnknown);
  void Remove(const RawAddress& address);
  LeAudioDevice* FindByAddress(const RawAddress& address) const;
  std::shared_ptr<LeAudioDevice> GetByAddress(const RawAddress& address) const;
  LeAudioDevice* FindByConnId(uint16_t conn_id) const;
  LeAudioDevice* FindByCisConnHdl(uint8_t cig_id, uint16_t conn_hdl) const;
  void SetInitialGroupAutoconnectState(int group_id, int gatt_if,
                                       tBTM_BLE_CONN_TYPE reconnection_mode,
                                       bool current_dev_autoconnect_flag);
  size_t Size(void) const;
  void Dump(int fd, int group_id) const;
  void Cleanup(tGATT_IF client_if);

 private:
  std::vector<std::shared_ptr<LeAudioDevice>> leAudioDevices_;
};

}  // namespace le_audio
