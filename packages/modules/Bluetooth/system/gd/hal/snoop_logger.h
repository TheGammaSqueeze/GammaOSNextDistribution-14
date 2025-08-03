/*
 * Copyright 2019 The Android Open Source Project
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

#include <fstream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "common/circular_buffer.h"
#include "hal/hci_hal.h"
#include "hal/snoop_logger_socket_interface.h"
#include "hal/snoop_logger_socket_thread.h"
#include "hal/syscall_wrapper_impl.h"
#include "module.h"
#include "os/repeating_alarm.h"

namespace bluetooth {
namespace hal {

#ifdef USE_FAKE_TIMERS
static uint64_t file_creation_time;
#endif

class FilterTracker {
 public:
  // NOTE: 1 is used as a static CID for L2CAP signaling
  std::unordered_set<uint16_t> l2c_local_cid = {1};
  std::unordered_set<uint16_t> l2c_remote_cid = {1};
  uint16_t rfcomm_local_cid = 0;
  uint16_t rfcomm_remote_cid = 0;
  std::unordered_set<uint16_t> rfcomm_channels = {0};

  // Adds L2C channel to acceptlist.
  void AddL2capCid(uint16_t local_cid, uint16_t remote_cid);

  // Sets L2CAP channel that RFCOMM uses.
  void SetRfcommCid(uint16_t local_cid, uint16_t remote_cid);

  // Remove L2C channel from acceptlist.
  void RemoveL2capCid(uint16_t local_cid, uint16_t remote_cid);

  void AddRfcommDlci(uint8_t channel);

  bool IsAcceptlistedL2cap(bool local, uint16_t cid);

  bool IsRfcommChannel(bool local, uint16_t cid);

  bool IsAcceptlistedDlci(uint8_t dlci);
};

typedef enum {
  FILTER_PROFILE_NONE = -1,
  FILTER_PROFILE_PBAP = 0,
  FILTER_PROFILE_HFP_HS,
  FILTER_PROFILE_HFP_HF,
  FILTER_PROFILE_MAP,
  FILTER_PROFILE_MAX,
} profile_type_t;

class ProfilesFilter {
 public:
  void SetupProfilesFilter(bool pbap_filtered, bool map_filtered);

  bool IsHfpProfile(bool local, uint16_t cid, uint8_t dlci);

  bool IsL2capMatch(bool local, uint16_t cid);

  bool IsL2capFlowExt(bool local, uint16_t cid);

  bool IsRfcommMatch(bool local, uint16_t cid, uint8_t dlci);

  bool IsRfcommFlowExt(bool local, uint16_t cid, uint8_t dlci);

  profile_type_t CidToProfile(bool local, uint16_t cid);

  profile_type_t DlciToProfile(bool local, uint16_t cid, uint8_t dlci);

  void ProfileL2capOpen(
      profile_type_t profile, uint16_t lcid, uint16_t rcid, uint16_t psm, bool flow_ext);

  void ProfileL2capClose(profile_type_t profile);

  void ProfileRfcommOpen(
      profile_type_t profile, uint16_t lcid, uint8_t dlci, uint16_t uuid, bool flow_ext);

  void ProfileRfcommClose(profile_type_t profile);

  bool IsRfcommChannel(bool local, uint16_t cid);

  void PrintProfilesConfig();

  static inline std::string ProfileToString(profile_type_t profile) {
    switch (profile) {
      case FILTER_PROFILE_NONE:
        return "FILTER_PROFILE_NONE";
      case FILTER_PROFILE_PBAP:
        return "FILTER_PROFILE_PBAP";
      case FILTER_PROFILE_HFP_HS:
        return "FILTER_PROFILE_HFP_HS";
      case FILTER_PROFILE_HFP_HF:
        return "FILTER_PROFILE_HFP_HF";
      case FILTER_PROFILE_MAP:
        return "FILTER_PROFILE_MAP";
      default:
        return "[Unknown profile_type_t]";
    }
  }

  uint16_t ch_rfc_l, ch_rfc_r;  // local & remote L2CAP channel for RFCOMM
  uint16_t ch_last;             // last channel seen for fragment packet

 private:
  bool setup_done_flag = false;
  struct {
    profile_type_t type;
    bool enabled, l2cap_opened, rfcomm_opened;
    bool flow_ext_l2cap, flow_ext_rfcomm;
    uint16_t lcid, rcid, rfcomm_uuid, psm;
    uint8_t scn;
  } profiles[FILTER_PROFILE_MAX];
  profile_type_t current_profile;
};

class SnoopLogger : public ::bluetooth::Module {
 public:
  static const ModuleFactory Factory;

  static const std::string kBtSnoopMaxPacketsPerFileProperty;
  static const std::string kIsDebuggableProperty;
  static const std::string kBtSnoopLogModeProperty;
  static const std::string kBtSnoopLogPersists;
  static const std::string kBtSnoopDefaultLogModeProperty;
  static const std::string kBtSnoopLogFilterHeadersProperty;
  static const std::string kBtSnoopLogFilterProfileA2dpProperty;
  static const std::string kBtSnoopLogFilterProfileMapModeProperty;
  static const std::string kBtSnoopLogFilterProfilePbapModeProperty;
  static const std::string kBtSnoopLogFilterProfileRfcommProperty;
  static const std::string kSoCManufacturerProperty;

  static const std::string kBtSnoopLogModeDisabled;
  static const std::string kBtSnoopLogModeFiltered;
  static const std::string kBtSnoopLogModeFull;

  static const std::string kSoCManufacturerQualcomm;

  static const std::string kBtSnoopLogFilterProfileModeFullfillter;
  static const std::string kBtSnoopLogFilterProfileModeHeader;
  static const std::string kBtSnoopLogFilterProfileModeMagic;
  static const std::string kBtSnoopLogFilterProfileModeDisabled;

  std::unordered_map<std::string, bool> kBtSnoopLogFilterState = {
      {kBtSnoopLogFilterHeadersProperty, false},
      {kBtSnoopLogFilterProfileA2dpProperty, false},
      {kBtSnoopLogFilterProfileRfcommProperty, false}};

  std::unordered_map<std::string, std::string> kBtSnoopLogFilterMode = {
      {kBtSnoopLogFilterProfilePbapModeProperty, kBtSnoopLogFilterProfileModeDisabled},
      {kBtSnoopLogFilterProfileMapModeProperty, kBtSnoopLogFilterProfileModeDisabled}};

  // Put in header for test
  struct PacketHeaderType {
    uint32_t length_original;
    uint32_t length_captured;
    uint32_t flags;
    uint32_t dropped_packets;
    uint64_t timestamp;
    uint8_t type;
  } __attribute__((__packed__));

  // Struct for caching info about L2CAP Media Channel
  struct A2dpMediaChannel {
    uint16_t conn_handle;
    uint16_t local_cid;
    uint16_t remote_cid;
  };

  // Returns the maximum number of packets per file
  // Changes to this value is only effective after restarting Bluetooth
  static size_t GetMaxPacketsPerFile();

  static size_t GetMaxPacketsPerBuffer();

  // Get snoop logger mode based on current system setup
  // Changes to this values is only effective after restarting Bluetooth
  static std::string GetBtSnoopMode();

  // Returns whether the soc manufacturer is Qualcomm
  // Changes to this value is only effective after restarting Bluetooth
  static bool IsQualcommDebugLogEnabled();

  // Returns whether snoop log persists even after restarting Bluetooth
  static bool IsBtSnoopLogPersisted();

  // Has to be defined from 1 to 4 per btsnoop format
  enum PacketType {
    CMD = 1,
    ACL = 2,
    SCO = 3,
    EVT = 4,
    ISO = 5,
  };

  enum Direction {
    INCOMING,
    OUTGOING,
  };

  void Capture(HciPacket& packet, Direction direction, PacketType type);

  // Set a L2CAP channel as acceptlisted, allowing packets with that L2CAP CID
  // to show up in the snoop logs.
  void AcceptlistL2capChannel(uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid);

  // Set a RFCOMM dlci as acceptlisted, allowing packets with that RFCOMM CID
  // to show up in the snoop logs. The local_cid is used to associate it with
  // its corrisponding ACL connection. The dlci is the channel with direction
  // so there is no chance of a collision if two services are using the same
  // channel but in different directions.
  void AcceptlistRfcommDlci(uint16_t conn_handle, uint16_t local_cid, uint8_t dlci);

  // Indicate that the provided L2CAP channel is being used for RFCOMM.
  // If packets with the provided L2CAP CID are encountered, they will be
  // filtered on RFCOMM based on channels provided to |filter_rfcomm_channel|.
  void AddRfcommL2capChannel(uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid);

  // Clear an L2CAP channel from being filtered.
  void ClearL2capAcceptlist(uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid);

  // Cache A2DP Media Channel info for filtering media packets.
  void AddA2dpMediaChannel(uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid);

  // Remove A2DP Media Channel cache
  void RemoveA2dpMediaChannel(uint16_t conn_handle, uint16_t local_cid);

  // New RFCOMM port is opened.
  void SetRfcommPortOpen(
      uint16_t conn_handle, uint16_t local_cid, uint8_t dlci, uint16_t uuid, bool flow);
  // RFCOMM port is closed.
  void SetRfcommPortClose(uint16_t handle, uint16_t local_cid, uint8_t dlci, uint16_t uuid);

  // New L2CAP channel is opened.
  void SetL2capChannelOpen(
      uint16_t handle, uint16_t local_cid, uint16_t remote_cid, uint16_t psm, bool flow);
  // L2CAP channel is closed.
  void SetL2capChannelClose(uint16_t handle, uint16_t local_cid, uint16_t remote_cid);

  void RegisterSocket(SnoopLoggerSocketInterface* socket);

 protected:
  // Packet type length
  static const size_t PACKET_TYPE_LENGTH;
  // The size of the L2CAP header. All information past this point is removed from
  // a filtered packet.
  static const uint32_t L2CAP_HEADER_SIZE;
  // Max packet data size when headersfiltered option enabled
  static const size_t MAX_HCI_ACL_LEN;

  void ListDependencies(ModuleList* list) const override;
  void Start() override;
  void Stop() override;
  DumpsysDataFinisher GetDumpsysData(flatbuffers::FlatBufferBuilder* builder) const override;
  std::string ToString() const override {
    return std::string("SnoopLogger");
  }

  SnoopLogger(
      std::string snoop_log_path,
      std::string snooz_log_path,
      size_t max_packets_per_file,
      size_t max_packets_per_buffer,
      const std::string& btsnoop_mode,
      bool qualcomm_debug_log_enabled,
      const std::chrono::milliseconds snooz_log_life_time,
      const std::chrono::milliseconds snooz_log_delete_alarm_interval,
      bool snoop_log_persists);
  void CloseCurrentSnoopLogFile();
  void OpenNextSnoopLogFile();
  void DumpSnoozLogToFile(const std::vector<std::string>& data) const;
  // Enable filters according to their sysprops
  void EnableFilters();
  // Disable all filters
  void DisableFilters();
  // Check if the filter is enabled. Pass filter name as a string.
  bool IsFilterEnabled(std::string filter_name);
  // Check if packet should be filtered (rfcommchannelfiltered mode)
  bool ShouldFilterLog(bool is_received, uint8_t* packet);
  // Calculate packet length (snoopheadersfiltered mode)
  void CalculateAclPacketLength(uint32_t& length, uint8_t* packet, bool is_received);
  // Strip packet's payload (profilesfiltered mode)
  uint32_t PayloadStrip(
      profile_type_t current_profile, uint8_t* packet, uint32_t hdr_len, uint32_t pl_len);
  // Filter profile packet according to its filtering mode
  uint32_t FilterProfiles(bool is_received, uint8_t* packet);
  // Check if packet is A2DP media packet (a2dppktsfiltered mode)
  bool IsA2dpMediaPacket(bool is_received, uint8_t* packet);
  // Chec if channel is cached in snoop logger for filtering (a2dppktsfiltered mode)
  bool IsA2dpMediaChannel(uint16_t conn_handle, uint16_t cid, bool is_local_cid);
  // Handle HFP filtering while profilesfiltered enabled
  uint32_t FilterProfilesHandleHfp(
      uint8_t* packet, uint32_t length, uint32_t totlen, uint32_t offset);
  void FilterProfilesRfcommChannel(
      uint8_t* packet,
      uint8_t& current_offset,
      uint32_t& length,
      profile_type_t& current_profile,
      bluetooth::hal::ProfilesFilter& filters,
      bool is_received,
      uint16_t l2cap_channel,
      uint32_t& offset,
      uint32_t total_length);
  void FilterCapturedPacket(
      HciPacket& packet,
      Direction direction,
      PacketType type,
      uint32_t& length,
      PacketHeaderType header);

  std::unique_ptr<SnoopLoggerSocketThread> snoop_logger_socket_thread_;

 private:
  static std::string btsnoop_mode_;
  std::string snoop_log_path_;
  std::string snooz_log_path_;
  std::ofstream btsnoop_ostream_;
  size_t max_packets_per_file_;
  common::CircularBuffer<std::string> btsnooz_buffer_;
  bool qualcomm_debug_log_enabled_ = false;
  size_t packet_counter_ = 0;
  mutable std::recursive_mutex file_mutex_;
  std::unique_ptr<os::RepeatingAlarm> alarm_;
  std::chrono::milliseconds snooz_log_life_time_;
  std::chrono::milliseconds snooz_log_delete_alarm_interval_;
  SnoopLoggerSocketInterface* socket_;
  SyscallWrapperImpl syscall_if;
  bool snoop_log_persists = false;
};

}  // namespace hal
}  // namespace bluetooth
