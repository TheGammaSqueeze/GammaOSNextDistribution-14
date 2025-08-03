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

#include "hal/snoop_logger.h"

#include <arpa/inet.h>
#include <sys/stat.h>

#include <algorithm>
#include <bitset>
#include <chrono>
#include <sstream>

#include "common/circular_buffer.h"
#include "common/init_flags.h"
#include "common/strings.h"
#include "hal/snoop_logger_common.h"
#include "module_dumper_flatbuffer.h"
#include "os/files.h"
#include "os/log.h"
#include "os/parameter_provider.h"
#include "os/system_properties.h"

#ifdef USE_FAKE_TIMERS
#include "os/fake_timer/fake_timerfd.h"
using bluetooth::os::fake_timer::fake_timerfd_get_clock;
#endif

namespace bluetooth {
namespace hal {

// Adds L2CAP channel to acceptlist.
void FilterTracker::AddL2capCid(uint16_t local_cid, uint16_t remote_cid) {
  l2c_local_cid.insert(local_cid);
  l2c_remote_cid.insert(remote_cid);
}

// Sets L2CAP channel that RFCOMM uses.
void FilterTracker::SetRfcommCid(uint16_t local_cid, uint16_t remote_cid) {
  rfcomm_local_cid = local_cid;
  rfcomm_remote_cid = remote_cid;
}

// Remove L2CAP channel from acceptlist.
void FilterTracker::RemoveL2capCid(uint16_t local_cid, uint16_t remote_cid) {
  if (rfcomm_local_cid == local_cid) {
    rfcomm_channels.clear();
    rfcomm_channels.insert(0);
    rfcomm_local_cid = 0;
    rfcomm_remote_cid = 0;
  }

  l2c_local_cid.erase(local_cid);
  l2c_remote_cid.erase(remote_cid);
}

void FilterTracker::AddRfcommDlci(uint8_t channel) {
  rfcomm_channels.insert(channel);
}

bool FilterTracker::IsAcceptlistedL2cap(bool local, uint16_t cid) {
  const auto& set = local ? l2c_local_cid : l2c_remote_cid;
  return (set.find(cid) != set.end());
}

bool FilterTracker::IsRfcommChannel(bool local, uint16_t cid) {
  const auto& channel = local ? rfcomm_local_cid : rfcomm_remote_cid;
  return cid == channel;
}

bool FilterTracker::IsAcceptlistedDlci(uint8_t dlci) {
  return rfcomm_channels.find(dlci) != rfcomm_channels.end();
}

void ProfilesFilter::SetupProfilesFilter(bool pbap_filtered, bool map_filtered) {
  if (setup_done_flag) {
    return;
  }
  setup_done_flag = true;

  LOG_DEBUG("SetupProfilesFilter: pbap=%d, map=%d", pbap_filtered, map_filtered);

  for (int i = 0; i < FILTER_PROFILE_MAX; i++) {
    profiles[i].type = (profile_type_t)i;
    profiles[i].enabled = false;
    profiles[i].rfcomm_opened = false;
    profiles[i].l2cap_opened = false;
  }

  if (pbap_filtered) {
    profiles[FILTER_PROFILE_PBAP].enabled = profiles[FILTER_PROFILE_HFP_HS].enabled =
        profiles[FILTER_PROFILE_HFP_HF].enabled = true;
  }
  if (map_filtered) {
    profiles[FILTER_PROFILE_MAP].enabled = true;
  }
  ch_rfc_l = ch_rfc_r = ch_last = 0;

  PrintProfilesConfig();
}

bool ProfilesFilter::IsHfpProfile(bool local, uint16_t cid, uint8_t dlci) {
  profile_type_t profile = DlciToProfile(local, cid, dlci);
  return profile == FILTER_PROFILE_HFP_HS || profile == FILTER_PROFILE_HFP_HF;
}

bool ProfilesFilter::IsL2capFlowExt(bool local, uint16_t cid) {
  profile_type_t profile = CidToProfile(local, cid);
  if (profile >= 0) return profiles[profile].flow_ext_l2cap;
  return false;
}

bool ProfilesFilter::IsRfcommFlowExt(bool local, uint16_t cid, uint8_t dlci) {
  profile_type_t profile = DlciToProfile(local, cid, dlci);
  if (profile >= 0) current_profile = profile;
  return profiles[profile].flow_ext_rfcomm;
}

profile_type_t ProfilesFilter::CidToProfile(bool local, uint16_t cid) {
  uint16_t ch;
  for (int i = 0; i < FILTER_PROFILE_MAX; i++) {
    if (profiles[i].enabled && profiles[i].l2cap_opened) {
      ch = local ? profiles[i].lcid : profiles[i].rcid;
      if (ch == cid) {
        return (profile_type_t)i;
      }
    }
  }
  return FILTER_PROFILE_NONE;
}

profile_type_t ProfilesFilter::DlciToProfile(bool local, uint16_t cid, uint8_t dlci) {
  if (!IsRfcommChannel(local, cid)) return FILTER_PROFILE_NONE;

  for (int i = 0; i < FILTER_PROFILE_MAX; i++) {
    if (profiles[i].enabled && profiles[i].l2cap_opened && profiles[i].rfcomm_opened &&
        profiles[i].scn == (dlci >> 1)) {
      return (profile_type_t)i;
    }
  }
  return FILTER_PROFILE_NONE;
}

void ProfilesFilter::ProfileL2capOpen(
    profile_type_t profile, uint16_t lcid, uint16_t rcid, uint16_t psm, bool flow_ext) {
  if (profiles[profile].l2cap_opened == true) {
    LOG_DEBUG("l2cap for %d was already opened. Override it", profile);
  }
  LOG_DEBUG(
      "lcid:=%d, rcid=%d, psm=%d, flow_ext=%d, filter profile=%s",
      lcid,
      rcid,
      psm,
      flow_ext,
      ProfilesFilter::ProfileToString(profile).c_str());
  profiles[profile].lcid = lcid;
  profiles[profile].rcid = rcid;
  profiles[profile].psm = psm;
  profiles[profile].flow_ext_l2cap = flow_ext;
  profiles[profile].l2cap_opened = true;

  PrintProfilesConfig();
}

void ProfilesFilter::ProfileL2capClose(profile_type_t profile) {
  if (profile < 0 || profile >= FILTER_PROFILE_MAX) return;
  profiles[profile].l2cap_opened = false;
}

void ProfilesFilter::ProfileRfcommOpen(
    profile_type_t profile, uint16_t lcid, uint8_t dlci, uint16_t uuid, bool flow_ext) {
  if (profiles[profile].rfcomm_opened == true) {
    LOG_DEBUG("rfcomm for %d was already opened. Override it", profile);
  }
  LOG_DEBUG(
      "lcid:=%d, dlci=%d, uuid=%d, flow_ext=%d, filter profile=%s",
      lcid,
      dlci,
      uuid,
      flow_ext,
      ProfilesFilter::ProfileToString(profile).c_str());
  profiles[profile].rfcomm_uuid = uuid;
  profiles[profile].scn = (dlci >> 1);
  profiles[profile].flow_ext_rfcomm = flow_ext;
  profiles[profile].l2cap_opened = true;
  profiles[profile].rfcomm_opened = true;

  PrintProfilesConfig();
}

void ProfilesFilter::ProfileRfcommClose(profile_type_t profile) {
  if (profile < 0 || profile >= FILTER_PROFILE_MAX) return;
  profiles[profile].rfcomm_opened = false;
}

bool ProfilesFilter::IsRfcommChannel(bool local, uint16_t cid) {
  uint16_t channel = local ? ch_rfc_l : ch_rfc_r;
  return cid == channel;
}

void ProfilesFilter::PrintProfilesConfig() {
  for (int i = 0; i < FILTER_PROFILE_MAX; i++) {
    if (profiles[i].enabled) {
      LOG_DEBUG(
          "\ntype: %s"
          "\nenabled: %d, l2cap_opened: %d, rfcomm_opened: %d"
          "\nflow_ext_l2cap: %d, flow_ext_rfcomm: %d"
          "\nlcid: %d, rcid: %d, rfcomm_uuid: %d, psm: %d"
          "\nscn: %d\n",
          ProfilesFilter::ProfileToString(profiles[i].type).c_str(),
          profiles[i].enabled,
          profiles[i].l2cap_opened,
          profiles[i].rfcomm_opened,
          profiles[i].flow_ext_l2cap,
          profiles[i].flow_ext_rfcomm,
          profiles[i].lcid,
          profiles[i].rcid,
          profiles[i].rfcomm_uuid,
          profiles[i].psm,
          profiles[i].psm);
    }
  }
}

namespace {

// Epoch in microseconds since 01/01/0000.
constexpr uint64_t kBtSnoopEpochDelta = 0x00dcddb30f2f8000ULL;

// Qualcomm debug logs handle
constexpr uint16_t kQualcommDebugLogHandle = 0xedc;

// Number of bytes into a packet where you can find the value for a channel.
constexpr size_t ACL_CHANNEL_OFFSET = 0;
constexpr size_t ACL_LENGTH_OFFSET = 2;
constexpr size_t L2CAP_PDU_LENGTH_OFFSET = 4;
constexpr size_t L2CAP_CHANNEL_OFFSET = 6;
constexpr size_t L2CAP_CONTROL_OFFSET = 8;
constexpr size_t RFCOMM_CHANNEL_OFFSET = 8;
constexpr size_t RFCOMM_EVENT_OFFSET = 9;

// RFCOMM filtering consts
constexpr uint8_t RFCOMM_SABME = 0x2F;  // RFCOMM: Start Asynchronous Balanced Mode (startup cmd)
constexpr uint8_t RFCOMM_UA = 0x63;     // RFCOMM: Unnumbered Acknowledgement (rsp when connected)
constexpr uint8_t RFCOMM_UIH = 0xEF;    // RFCOMM: Unnumbered Information with Header check

constexpr uint8_t START_PACKET_BOUNDARY = 0x02;
constexpr uint8_t CONTINUATION_PACKET_BOUNDARY = 0x01;
constexpr uint16_t HANDLE_MASK = 0x0FFF;
auto GetBoundaryFlag = [](auto handle) { return (((handle) >> 12) & 0x0003); };

// ProfilesFilter consts
constexpr size_t ACL_HEADER_LENGTH = 4;
constexpr size_t BASIC_L2CAP_HEADER_LENGTH = 4;
constexpr uint8_t EXTRA_BUF_SIZE = 0x40;
constexpr uint16_t DEFAULT_PACKET_SIZE = 0x800;

constexpr uint8_t PROFILE_SCN_PBAP = 19;
constexpr uint8_t PROFILE_SCN_MAP = 26;

constexpr uint16_t PROFILE_PSM_PBAP = 0x1025;
constexpr uint16_t PROFILE_PSM_MAP = 0x1029;
constexpr uint16_t PROFILE_PSM_RFCOMM = 0x0003;

constexpr uint16_t PROFILE_UUID_PBAP = 0x112f;
constexpr uint16_t PROFILE_UUID_MAP = 0x1132;
constexpr uint16_t PROFILE_UUID_HFP_HS = 0x1112;
constexpr uint16_t PROFILE_UUID_HFP_HF = 0x111f;

uint64_t htonll(uint64_t ll) {
  if constexpr (isLittleEndian) {
    return static_cast<uint64_t>(htonl(ll & 0xffffffff)) << 32 | htonl(ll >> 32);
  } else {
    return ll;
  }
}

// The number of packets per btsnoop file before we rotate to the next file. As of right now there
// are two snoop files that are rotated through. The size can be dynamically configured by setting
// the relevant system property
constexpr size_t kDefaultBtSnoopMaxPacketsPerFile = 0xffff;

// We restrict the maximum packet size to 150 bytes
constexpr size_t kDefaultBtSnoozMaxBytesPerPacket = 150;
constexpr size_t kDefaultBtSnoozMaxPayloadBytesPerPacket =
    kDefaultBtSnoozMaxBytesPerPacket - sizeof(SnoopLogger::PacketHeaderType);

using namespace std::chrono_literals;
constexpr std::chrono::hours kBtSnoozLogLifeTime = 12h;
constexpr std::chrono::hours kBtSnoozLogDeleteRepeatingAlarmInterval = 1h;

std::mutex filter_tracker_list_mutex;
std::unordered_map<uint16_t, FilterTracker> filter_tracker_list;
std::unordered_map<uint16_t, uint16_t> local_cid_to_acl;

std::mutex a2dpMediaChannels_mutex;
std::vector<SnoopLogger::A2dpMediaChannel> a2dpMediaChannels;

std::mutex snoop_log_filters_mutex;

std::mutex profiles_filter_mutex;
std::unordered_map<int16_t, ProfilesFilter> profiles_filter_table;
constexpr const char* payload_fill_magic = "PROHIBITED";
constexpr const char* cpbr_pattern = "\x0d\x0a+CPBR:";
constexpr const char* clcc_pattern = "\x0d\x0a+CLCC:";
const uint32_t magic_pat_len = strlen(payload_fill_magic);
const uint32_t cpbr_pat_len = strlen(cpbr_pattern);
const uint32_t clcc_pat_len = strlen(clcc_pattern);

std::string get_btsnoop_log_path(std::string log_dir, bool filtered) {
  if (filtered) {
    log_dir.append(".filtered");
  }
  return log_dir;
}

std::string get_last_log_path(std::string log_file_path) {
  return log_file_path.append(".last");
}

void delete_btsnoop_files(const std::string& log_path) {
  LOG_INFO("Deleting logs if they exist");
  if (os::FileExists(log_path)) {
    if (!os::RemoveFile(log_path)) {
      LOG_ERROR("Failed to remove main log file at \"%s\"", log_path.c_str());
    }
  } else {
    LOG_INFO("Main log file does not exist at \"%s\"", log_path.c_str());
  }
  auto last_log_path = get_last_log_path(log_path);
  if (os::FileExists(last_log_path)) {
    if (!os::RemoveFile(last_log_path)) {
      LOG_ERROR("Failed to remove last log file at \"%s\"", log_path.c_str());
    }
  } else {
    LOG_INFO("Last log file does not exist at \"%s\"", log_path.c_str());
  }
}

void delete_old_btsnooz_files(const std::string& log_path, const std::chrono::milliseconds log_life_time) {
  auto opt_created_ts = os::FileCreatedTime(log_path);
  if (!opt_created_ts) return;
#ifdef USE_FAKE_TIMERS
  auto diff = fake_timerfd_get_clock() - file_creation_time;
  uint64_t log_lifetime = log_life_time.count();
  if (diff >= log_lifetime) {
#else
  using namespace std::chrono;
  auto created_tp = opt_created_ts.value();
  auto current_tp = std::chrono::system_clock::now();

  auto diff = duration_cast<milliseconds>(current_tp - created_tp);
  if (diff >= log_life_time) {
#endif
    delete_btsnoop_files(log_path);
  }
}

size_t get_btsnooz_packet_length_to_write(
    const HciPacket& packet, SnoopLogger::PacketType type, bool qualcomm_debug_log_enabled) {
  static const size_t kAclHeaderSize = 4;
  static const size_t kL2capHeaderSize = 4;
  static const size_t kL2capCidOffset = (kAclHeaderSize + 2);
  static const uint16_t kL2capSignalingCid = 0x0001;

  static const size_t kHciAclHandleOffset = 0;

  // Maximum amount of ACL data to log.
  // Enough for an RFCOMM frame up to the frame check;
  // not enough for a HID report or audio data.
  static const size_t kMaxBtsnoozAclSize = 14;

  // Calculate packet length to be included
  size_t included_length = 0;
  switch (type) {
    case SnoopLogger::PacketType::CMD:
    case SnoopLogger::PacketType::EVT:
      included_length = packet.size();
      break;

    case SnoopLogger::PacketType::ACL: {
      // Log ACL and L2CAP header by default
      size_t len_hci_acl = kAclHeaderSize + kL2capHeaderSize;
      // Check if we have enough data for an L2CAP header
      if (packet.size() > len_hci_acl) {
        uint16_t l2cap_cid =
            static_cast<uint16_t>(packet[kL2capCidOffset]) |
            static_cast<uint16_t>((static_cast<uint16_t>(packet[kL2capCidOffset + 1]) << static_cast<uint16_t>(8)));
        uint16_t hci_acl_packet_handle =
            static_cast<uint16_t>(packet[kHciAclHandleOffset]) |
            static_cast<uint16_t>((static_cast<uint16_t>(packet[kHciAclHandleOffset + 1]) << static_cast<uint16_t>(8)));
        hci_acl_packet_handle &= 0x0fff;

        if (l2cap_cid == kL2capSignalingCid) {
          // For the signaling CID, take the full packet.
          // That way, the PSM setup is captured, allowing decoding of PSMs down
          // the road.
          return packet.size();
        } else if (qualcomm_debug_log_enabled && hci_acl_packet_handle == kQualcommDebugLogHandle) {
          return packet.size();
        } else {
          // Otherwise, return as much as we reasonably can
          len_hci_acl = kMaxBtsnoozAclSize;
        }
      }
      included_length = std::min(len_hci_acl, packet.size());
      break;
    }

    case SnoopLogger::PacketType::ISO:
    case SnoopLogger::PacketType::SCO:
    default:
      // We are not logging SCO and ISO packets in snooz log as they may contain voice data
      break;
  }
  return std::min(included_length, kDefaultBtSnoozMaxPayloadBytesPerPacket);
}

}  // namespace

// system properties
const std::string SnoopLogger::kBtSnoopMaxPacketsPerFileProperty = "persist.bluetooth.btsnoopsize";
const std::string SnoopLogger::kIsDebuggableProperty = "ro.debuggable";
const std::string SnoopLogger::kBtSnoopLogModeProperty = "persist.bluetooth.btsnooplogmode";
const std::string SnoopLogger::kBtSnoopDefaultLogModeProperty = "persist.bluetooth.btsnoopdefaultmode";
const std::string SnoopLogger::kBtSnoopLogPersists = "persist.bluetooth.btsnooplogpersists";
// Truncates ACL packets (non-fragment) to fixed (MAX_HCI_ACL_LEN) number of bytes
const std::string SnoopLogger::kBtSnoopLogFilterHeadersProperty =
    "persist.bluetooth.snooplogfilter.headers.enabled";
// Discards A2DP media packets (non-split mode)
const std::string SnoopLogger::kBtSnoopLogFilterProfileA2dpProperty =
    "persist.bluetooth.snooplogfilter.profiles.a2dp.enabled";
// Filters MAP packets based on the filter mode
const std::string SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty =
    "persist.bluetooth.snooplogfilter.profiles.map";
// Filters PBAP and HFP packets (CPBR, CLCC) based on the filter mode
const std::string SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty =
    "persist.bluetooth.snooplogfilter.profiles.pbap";
// Truncates RFCOMM UIH packet to fixed (L2CAP_HEADER_SIZE) number of bytes
const std::string SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty =
    "persist.bluetooth.snooplogfilter.profiles.rfcomm.enabled";
const std::string SnoopLogger::kSoCManufacturerProperty = "ro.soc.manufacturer";

// persist.bluetooth.btsnooplogmode
const std::string SnoopLogger::kBtSnoopLogModeDisabled = "disabled";
const std::string SnoopLogger::kBtSnoopLogModeFiltered = "filtered";
const std::string SnoopLogger::kBtSnoopLogModeFull = "full";
// ro.soc.manufacturer
const std::string SnoopLogger::kSoCManufacturerQualcomm = "Qualcomm";

// PBAP, MAP and HFP packets filter mode - discard whole packet
const std::string SnoopLogger::kBtSnoopLogFilterProfileModeFullfillter = "fullfilter";
// PBAP, MAP and HFP packets filter mode - truncate to fixed length
const std::string SnoopLogger::kBtSnoopLogFilterProfileModeHeader = "header";
// PBAP, MAP and HFP packets filter mode - fill with a magic string, such as: "PROHIBITED"
const std::string SnoopLogger::kBtSnoopLogFilterProfileModeMagic = "magic";
// PBAP, MAP and HFP packets filter mode - disabled
const std::string SnoopLogger::kBtSnoopLogFilterProfileModeDisabled = "disabled";

std::string SnoopLogger::btsnoop_mode_;

// Consts accessible in unit tests
const size_t SnoopLogger::PACKET_TYPE_LENGTH = 1;
const size_t SnoopLogger::MAX_HCI_ACL_LEN = 14;
const uint32_t SnoopLogger::L2CAP_HEADER_SIZE = 8;

SnoopLogger::SnoopLogger(
    std::string snoop_log_path,
    std::string snooz_log_path,
    size_t max_packets_per_file,
    size_t max_packets_per_buffer,
    const std::string& btsnoop_mode,
    bool qualcomm_debug_log_enabled,
    const std::chrono::milliseconds snooz_log_life_time,
    const std::chrono::milliseconds snooz_log_delete_alarm_interval,
    bool snoop_log_persists)
    : snoop_log_path_(std::move(snoop_log_path)),
      snooz_log_path_(std::move(snooz_log_path)),
      max_packets_per_file_(max_packets_per_file),
      btsnooz_buffer_(max_packets_per_buffer),
      qualcomm_debug_log_enabled_(qualcomm_debug_log_enabled),
      snooz_log_life_time_(snooz_log_life_time),
      snooz_log_delete_alarm_interval_(snooz_log_delete_alarm_interval),
      snoop_log_persists(snoop_log_persists) {
  btsnoop_mode_ = btsnoop_mode;

  if (btsnoop_mode_ == kBtSnoopLogModeFiltered) {
    LOG_INFO("Snoop Logs filtered mode enabled");
    EnableFilters();
    // delete unfiltered logs
    delete_btsnoop_files(get_btsnoop_log_path(snoop_log_path_, false));
    // delete snooz logs
    delete_btsnoop_files(snooz_log_path_);
  } else if (btsnoop_mode_ == kBtSnoopLogModeFull) {
    LOG_INFO("Snoop Logs full mode enabled");
    if (!snoop_log_persists) {
      // delete filtered logs
      delete_btsnoop_files(get_btsnoop_log_path(snoop_log_path_, true));
      // delete snooz logs
      delete_btsnoop_files(snooz_log_path_);
    }
  } else {
    LOG_INFO("Snoop Logs disabled");
    // delete both filtered and unfiltered logs
    delete_btsnoop_files(get_btsnoop_log_path(snoop_log_path_, true));
    delete_btsnoop_files(get_btsnoop_log_path(snoop_log_path_, false));
  }

  snoop_logger_socket_thread_ = nullptr;
  socket_ = nullptr;
  // Add ".filtered" extension if necessary
  snoop_log_path_ = get_btsnoop_log_path(snoop_log_path_, btsnoop_mode_ == kBtSnoopLogModeFiltered);
}

void SnoopLogger::CloseCurrentSnoopLogFile() {
  std::lock_guard<std::recursive_mutex> lock(file_mutex_);
  if (btsnoop_ostream_.is_open()) {
    btsnoop_ostream_.flush();
    btsnoop_ostream_.close();
  }
  packet_counter_ = 0;
}

void SnoopLogger::OpenNextSnoopLogFile() {
  std::lock_guard<std::recursive_mutex> lock(file_mutex_);
  CloseCurrentSnoopLogFile();

  auto last_file_path = get_last_log_path(snoop_log_path_);

  if (os::FileExists(snoop_log_path_)) {
    if (!os::RenameFile(snoop_log_path_, last_file_path)) {
      LOG_ERROR(
          "Unabled to rename existing snoop log from \"%s\" to \"%s\"",
          snoop_log_path_.c_str(),
          last_file_path.c_str());
    }
  } else {
    LOG_INFO("Previous log file \"%s\" does not exist, skip renaming", snoop_log_path_.c_str());
  }

  mode_t prevmask = umask(0);
  // do not use std::ios::app as we want override the existing file
  btsnoop_ostream_.open(snoop_log_path_, std::ios::binary | std::ios::out);
#ifdef USE_FAKE_TIMERS
  file_creation_time = fake_timerfd_get_clock();
#endif
  if (!btsnoop_ostream_.good()) {
    LOG_ALWAYS_FATAL("Unable to open snoop log at \"%s\", error: \"%s\"", snoop_log_path_.c_str(), strerror(errno));
  }
  umask(prevmask);
  if (!btsnoop_ostream_.write(
          reinterpret_cast<const char*>(&SnoopLoggerCommon::kBtSnoopFileHeader),
          sizeof(SnoopLoggerCommon::FileHeaderType))) {
    LOG_ALWAYS_FATAL("Unable to write file header to \"%s\", error: \"%s\"", snoop_log_path_.c_str(), strerror(errno));
  }
  if (!btsnoop_ostream_.flush()) {
    LOG_ERROR("Failed to flush, error: \"%s\"", strerror(errno));
  }
}

void SnoopLogger::EnableFilters() {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered) {
    return;
  }
  std::lock_guard<std::mutex> lock(snoop_log_filters_mutex);
  for (auto itr = kBtSnoopLogFilterState.begin(); itr != kBtSnoopLogFilterState.end(); itr++) {
    auto filter_enabled_property = os::GetSystemProperty(itr->first);
    if (filter_enabled_property) {
      itr->second = filter_enabled_property.value() == "true";
    }
    LOG_INFO("%s: %d", itr->first.c_str(), itr->second);
  }
  for (auto itr = kBtSnoopLogFilterMode.begin(); itr != kBtSnoopLogFilterMode.end(); itr++) {
    auto filter_mode_property = os::GetSystemProperty(itr->first);
    if (filter_mode_property) {
      itr->second = filter_mode_property.value();
    } else {
      itr->second = SnoopLogger::kBtSnoopLogFilterProfileModeDisabled;
    }
    LOG_INFO("%s: %s", itr->first.c_str(), itr->second.c_str());
  }
}

void SnoopLogger::DisableFilters() {
  std::lock_guard<std::mutex> lock(snoop_log_filters_mutex);
  for (auto itr = kBtSnoopLogFilterState.begin(); itr != kBtSnoopLogFilterState.end(); itr++) {
    itr->second = false;
    LOG_INFO("%s, %d", itr->first.c_str(), itr->second);
  }
  for (auto itr = kBtSnoopLogFilterMode.begin(); itr != kBtSnoopLogFilterMode.end(); itr++) {
    itr->second = SnoopLogger::kBtSnoopLogFilterProfileModeDisabled;
    LOG_INFO("%s, %s", itr->first.c_str(), itr->second.c_str());
  }
}

bool SnoopLogger::IsFilterEnabled(std::string filter_name) {
  std::lock_guard<std::mutex> lock(snoop_log_filters_mutex);
  for (auto itr = kBtSnoopLogFilterState.begin(); itr != kBtSnoopLogFilterState.end(); itr++) {
    if (filter_name == itr->first) {
      return itr->second == true;
    }
  }
  for (auto itr = kBtSnoopLogFilterMode.begin(); itr != kBtSnoopLogFilterMode.end(); itr++) {
    if (filter_name == itr->first) {
      return itr->second != SnoopLogger::kBtSnoopLogFilterProfileModeDisabled;
    }
  }
  return false;
}

bool SnoopLogger::ShouldFilterLog(bool is_received, uint8_t* packet) {
  uint16_t conn_handle =
      ((((uint16_t)packet[ACL_CHANNEL_OFFSET + 1]) << 8) + packet[ACL_CHANNEL_OFFSET]) & 0x0fff;
  std::lock_guard<std::mutex> lock(filter_tracker_list_mutex);
  auto& filters = filter_tracker_list[conn_handle];
  uint16_t cid = (packet[L2CAP_CHANNEL_OFFSET + 1] << 8) + packet[L2CAP_CHANNEL_OFFSET];
  if (filters.IsRfcommChannel(is_received, cid)) {
    uint8_t rfcomm_event = packet[RFCOMM_EVENT_OFFSET] & 0b11101111;
    if (rfcomm_event == RFCOMM_SABME || rfcomm_event == RFCOMM_UA) {
      return false;
    }

    uint8_t rfcomm_dlci = packet[RFCOMM_CHANNEL_OFFSET] >> 2;
    if (!filters.IsAcceptlistedDlci(rfcomm_dlci)) {
      return true;
    }
  } else if (!filters.IsAcceptlistedL2cap(is_received, cid)) {
    return true;
  }

  return false;
}

void SnoopLogger::CalculateAclPacketLength(
    uint32_t& length, uint8_t* packet, bool /* is_received */) {
  uint32_t def_len =
      ((((uint16_t)packet[ACL_LENGTH_OFFSET + 1]) << 8) + packet[ACL_LENGTH_OFFSET]) +
      ACL_HEADER_LENGTH + PACKET_TYPE_LENGTH;
  constexpr uint16_t L2CAP_SIGNALING_CID = 0x0001;

  if (length == 0) {
    return;
  }

  uint16_t handle =
      ((((uint16_t)packet[ACL_CHANNEL_OFFSET + 1]) << 8) + packet[ACL_CHANNEL_OFFSET]);
  uint8_t boundary_flag = GetBoundaryFlag(handle);
  handle = handle & HANDLE_MASK;

  if (boundary_flag == START_PACKET_BOUNDARY) {
    uint16_t l2cap_cid = packet[L2CAP_CHANNEL_OFFSET] | (packet[L2CAP_CHANNEL_OFFSET + 1] << 8);
    if (l2cap_cid == L2CAP_SIGNALING_CID || handle == kQualcommDebugLogHandle) {
      length = def_len;
    } else {
      if (def_len < MAX_HCI_ACL_LEN) {
        length = def_len;
      } else {
        // Otherwise, return as much as we reasonably can
        length = MAX_HCI_ACL_LEN;
      }
    }
  }
}

uint32_t SnoopLogger::PayloadStrip(
    profile_type_t current_profile, uint8_t* packet, uint32_t hdr_len, uint32_t pl_len) {
  uint32_t len = 0;
  std::string profile_filter_mode = "";
  LOG_DEBUG(
      "current_profile=%s, hdr len=%d, total len=%d",
      ProfilesFilter::ProfileToString(current_profile).c_str(),
      hdr_len,
      pl_len);
  std::lock_guard<std::mutex> lock(snoop_log_filters_mutex);
  switch (current_profile) {
    case FILTER_PROFILE_PBAP:
    case FILTER_PROFILE_HFP_HF:
    case FILTER_PROFILE_HFP_HS:
      profile_filter_mode =
          kBtSnoopLogFilterMode[SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty];
      break;
    case FILTER_PROFILE_MAP:
      profile_filter_mode =
          kBtSnoopLogFilterMode[SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty];
      break;
    default:
      profile_filter_mode = kBtSnoopLogFilterProfileModeDisabled;
  }

  if (profile_filter_mode == SnoopLogger::kBtSnoopLogFilterProfileModeFullfillter) {
    return 0;
  } else if (profile_filter_mode == SnoopLogger::kBtSnoopLogFilterProfileModeHeader) {
    len = hdr_len;

    packet[ACL_LENGTH_OFFSET] = static_cast<uint8_t>(hdr_len - BASIC_L2CAP_HEADER_LENGTH);
    packet[ACL_LENGTH_OFFSET + 1] =
        static_cast<uint8_t>((hdr_len - BASIC_L2CAP_HEADER_LENGTH) >> 8);

    packet[L2CAP_PDU_LENGTH_OFFSET] =
        static_cast<uint8_t>(hdr_len - (ACL_HEADER_LENGTH + BASIC_L2CAP_HEADER_LENGTH));
    packet[L2CAP_PDU_LENGTH_OFFSET + 1] =
        static_cast<uint8_t>((hdr_len - (ACL_HEADER_LENGTH + BASIC_L2CAP_HEADER_LENGTH)) >> 8);

  } else if (profile_filter_mode == SnoopLogger::kBtSnoopLogFilterProfileModeMagic) {
    strcpy(reinterpret_cast<char*>(&packet[hdr_len]), payload_fill_magic);

    packet[ACL_LENGTH_OFFSET] =
        static_cast<uint8_t>(hdr_len + magic_pat_len - BASIC_L2CAP_HEADER_LENGTH);
    packet[ACL_LENGTH_OFFSET + 1] =
        static_cast<uint8_t>((hdr_len + magic_pat_len - BASIC_L2CAP_HEADER_LENGTH) >> 8);

    packet[L2CAP_PDU_LENGTH_OFFSET] = static_cast<uint8_t>(
        hdr_len + magic_pat_len - (ACL_HEADER_LENGTH + BASIC_L2CAP_HEADER_LENGTH));
    packet[L2CAP_PDU_LENGTH_OFFSET + 1] = static_cast<uint8_t>(
        (hdr_len + magic_pat_len - (ACL_HEADER_LENGTH + BASIC_L2CAP_HEADER_LENGTH)) >> 8);

    len = hdr_len + magic_pat_len;
  } else {
    // Return unchanged
    len = hdr_len + pl_len;
  }
  return len + PACKET_TYPE_LENGTH;  // including packet type byte
}

uint32_t SnoopLogger::FilterProfilesHandleHfp(
    uint8_t* packet, uint32_t length, uint32_t totlen, uint32_t offset) {
  if ((totlen - offset) > cpbr_pat_len) {
    if (memcmp(&packet[offset], cpbr_pattern, cpbr_pat_len) == 0) {
      length = offset + cpbr_pat_len + 1;
      packet[L2CAP_PDU_LENGTH_OFFSET] = offset + cpbr_pat_len - BASIC_L2CAP_HEADER_LENGTH;
      packet[L2CAP_PDU_LENGTH_OFFSET] =
          offset + cpbr_pat_len - (ACL_HEADER_LENGTH + BASIC_L2CAP_HEADER_LENGTH);
      return length;
    }

    if (memcmp(&packet[offset], clcc_pattern, clcc_pat_len) == 0) {
      length = offset + cpbr_pat_len + 1;
      packet[L2CAP_PDU_LENGTH_OFFSET] = offset + clcc_pat_len - BASIC_L2CAP_HEADER_LENGTH;
      packet[L2CAP_PDU_LENGTH_OFFSET] =
          offset + clcc_pat_len - (ACL_HEADER_LENGTH + BASIC_L2CAP_HEADER_LENGTH);
    }
  }

  return length;
}

void SnoopLogger::FilterProfilesRfcommChannel(
    uint8_t* packet,
    uint8_t& current_offset,
    uint32_t& length,
    profile_type_t& current_profile,
    bluetooth::hal::ProfilesFilter& filters,
    bool is_received,
    uint16_t l2cap_channel,
    uint32_t& offset,
    uint32_t total_length) {
  uint8_t addr, ctrl, pf;

  addr = packet[current_offset];
  current_offset += 1;
  ctrl = packet[RFCOMM_EVENT_OFFSET];
  current_offset += 1;

  pf = ctrl & 0x10;
  ctrl = ctrl & 0xef;
  addr >>= 2;
  if (ctrl != RFCOMM_UIH) {
    return;
  }
  current_profile = filters.DlciToProfile(is_received, l2cap_channel, addr);
  if (current_profile != FILTER_PROFILE_NONE) {
    uint16_t len;
    uint8_t ea;

    len = packet[current_offset];
    current_offset += 1;
    ea = len & 1;

    if (!ea) {
      current_offset += 1;
    }

    if (filters.IsRfcommFlowExt(is_received, l2cap_channel, addr) && pf) {
      current_offset += 1;  // credit byte
    }
    offset = current_offset;

    if ((filters).IsHfpProfile(is_received, l2cap_channel, addr)) {
      length = FilterProfilesHandleHfp(packet, length, total_length, offset);
    } else {
      length = PayloadStrip(current_profile, packet, offset, total_length - offset);
    }
  }
}

uint32_t SnoopLogger::FilterProfiles(bool is_received, uint8_t* packet) {
  bool frag;
  uint16_t handle, l2c_chan, l2c_ctl;
  uint32_t length, totlen, offset;
  uint8_t current_offset = 0;
  profile_type_t current_profile = FILTER_PROFILE_NONE;
  constexpr uint16_t L2CAP_SIGNALING_CID = 0x0001;

  std::lock_guard<std::mutex> lock(profiles_filter_mutex);

  handle = ((((uint16_t)packet[ACL_CHANNEL_OFFSET + 1]) << 8) + packet[ACL_CHANNEL_OFFSET]);
  frag = (GetBoundaryFlag(handle) == CONTINUATION_PACKET_BOUNDARY);

  handle = handle & HANDLE_MASK;
  current_offset += 2;

  length = (((uint16_t)packet[ACL_LENGTH_OFFSET + 1]) << 8) + packet[ACL_LENGTH_OFFSET];
  current_offset += 2;
  totlen = length + ACL_HEADER_LENGTH;
  length += PACKET_TYPE_LENGTH + ACL_HEADER_LENGTH;  // Additional byte is added for packet type

  l2c_chan = ((uint16_t)packet[L2CAP_CHANNEL_OFFSET + 1] << 8) + packet[L2CAP_CHANNEL_OFFSET];
  current_offset += 4;

  auto& filters = profiles_filter_table[handle];
  if (frag) {
    l2c_chan = filters.ch_last;
  } else {
    filters.ch_last = l2c_chan;
  }

  if (l2c_chan != L2CAP_SIGNALING_CID && handle != kQualcommDebugLogHandle) {
    if (filters.IsL2capFlowExt(is_received, l2c_chan)) {
      l2c_ctl = ((uint16_t)packet[L2CAP_CONTROL_OFFSET + 1] << 8) + packet[L2CAP_CONTROL_OFFSET];
      if (!(l2c_ctl & 1)) {                     // I-Frame
        if (((l2c_ctl >> 14) & 0x3) == 0x01) {  // Start of L2CAP SDU
          current_offset += 2;
        }
      }
    }
    offset = current_offset;
    current_profile = filters.CidToProfile(is_received, l2c_chan);
    if (current_profile != FILTER_PROFILE_NONE) {
      if (frag) {
        return PACKET_TYPE_LENGTH + ACL_HEADER_LENGTH;
      }
      return PayloadStrip(current_profile, packet, offset, totlen - offset);
    }

    if (filters.IsRfcommChannel(is_received, l2c_chan)) {
      FilterProfilesRfcommChannel(
          packet,
          current_offset,
          length,
          current_profile,
          filters,
          is_received,
          l2c_chan,
          offset,
          totlen);
    }
  }

  return length;
}

void SnoopLogger::AcceptlistL2capChannel(
    uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      !IsFilterEnabled(kBtSnoopLogFilterProfileRfcommProperty)) {
    return;
  }

  LOG_DEBUG(
      "Acceptlisting l2cap channel: conn_handle=%d, local cid=%d, remote cid=%d",
      conn_handle,
      local_cid,
      remote_cid);
  std::lock_guard<std::mutex> lock(filter_tracker_list_mutex);

  // This will create the entry if there is no associated filter with the
  // connection.
  filter_tracker_list[conn_handle].AddL2capCid(local_cid, remote_cid);
}

void SnoopLogger::AcceptlistRfcommDlci(uint16_t conn_handle, uint16_t local_cid, uint8_t dlci) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      !IsFilterEnabled(kBtSnoopLogFilterProfileRfcommProperty)) {
    return;
  }

  LOG_DEBUG("Acceptlisting rfcomm channel: local cid=%d, dlci=%d", local_cid, dlci);
  std::lock_guard<std::mutex> lock(filter_tracker_list_mutex);

  filter_tracker_list[conn_handle].AddRfcommDlci(dlci);
}

void SnoopLogger::AddRfcommL2capChannel(
    uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      !IsFilterEnabled(kBtSnoopLogFilterProfileRfcommProperty)) {
    return;
  }

  LOG_DEBUG(
      "Rfcomm data going over l2cap channel: conn_handle=%d local cid=%d remote cid=%d",
      conn_handle,
      local_cid,
      remote_cid);
  std::lock_guard<std::mutex> lock(filter_tracker_list_mutex);

  filter_tracker_list[conn_handle].SetRfcommCid(local_cid, remote_cid);
  local_cid_to_acl.insert({local_cid, conn_handle});
}

void SnoopLogger::ClearL2capAcceptlist(
    uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      !IsFilterEnabled(kBtSnoopLogFilterProfileRfcommProperty)) {
    return;
  }

  LOG_DEBUG(
      "Clearing acceptlist from l2cap channel. conn_handle=%d local cid=%d remote cid=%d",
      conn_handle,
      local_cid,
      remote_cid);
  std::lock_guard<std::mutex> lock(filter_tracker_list_mutex);

  filter_tracker_list[conn_handle].RemoveL2capCid(local_cid, remote_cid);
}

bool SnoopLogger::IsA2dpMediaChannel(uint16_t conn_handle, uint16_t cid, bool is_local_cid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      !IsFilterEnabled(kBtSnoopLogFilterProfileA2dpProperty)) {
    return false;
  }

  std::lock_guard<std::mutex> lock(a2dpMediaChannels_mutex);
  auto iter = std::find_if(
      a2dpMediaChannels.begin(),
      a2dpMediaChannels.end(),
      [conn_handle, cid, is_local_cid](auto& el) {
        if (el.conn_handle != conn_handle) return false;

        if (is_local_cid) return el.local_cid == cid;

        return el.remote_cid == cid;
      });

  return iter != a2dpMediaChannels.end();
}

bool SnoopLogger::IsA2dpMediaPacket(bool is_received, uint8_t* packet) {
  uint16_t cid, conn_handle;
  bool is_local_cid = is_received;
  /*is_received signifies Rx packet so packet will have local_cid at offset 6
   * Tx packet with is_received as false and have remote_cid at the offset*/

  conn_handle = (uint16_t)((packet[0] + (packet[1] << 8)) & 0x0FFF);
  cid = (uint16_t)(packet[6] + (packet[7] << 8));

  return IsA2dpMediaChannel(conn_handle, cid, is_local_cid);
}

void SnoopLogger::AddA2dpMediaChannel(
    uint16_t conn_handle, uint16_t local_cid, uint16_t remote_cid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      !IsFilterEnabled(kBtSnoopLogFilterProfileA2dpProperty)) {
    return;
  }

  if (!SnoopLogger::IsA2dpMediaChannel(conn_handle, local_cid, true)) {
    LOG_INFO(
        "Add A2DP media channel filtering. conn_handle=%d local cid=%d remote cid=%d",
        conn_handle,
        local_cid,
        remote_cid);
    std::lock_guard<std::mutex> lock(a2dpMediaChannels_mutex);
    a2dpMediaChannels.push_back({conn_handle, local_cid, remote_cid});
  }
}

void SnoopLogger::RemoveA2dpMediaChannel(uint16_t conn_handle, uint16_t local_cid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      !IsFilterEnabled(kBtSnoopLogFilterProfileA2dpProperty)) {
    return;
  }

  std::lock_guard<std::mutex> lock(a2dpMediaChannels_mutex);
  a2dpMediaChannels.erase(
      std::remove_if(
          a2dpMediaChannels.begin(),
          a2dpMediaChannels.end(),
          [conn_handle, local_cid](auto& el) {
            return (el.conn_handle == conn_handle && el.local_cid == local_cid);
          }),
      a2dpMediaChannels.end());
}

void SnoopLogger::SetRfcommPortOpen(
    uint16_t conn_handle, uint16_t local_cid, uint8_t dlci, uint16_t uuid, bool flow) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      (!IsFilterEnabled(kBtSnoopLogFilterProfilePbapModeProperty) &&
       !IsFilterEnabled(kBtSnoopLogFilterProfileMapModeProperty))) {
    return;
  }

  std::lock_guard<std::mutex> lock(profiles_filter_mutex);

  profile_type_t profile = FILTER_PROFILE_NONE;
  auto& filters = profiles_filter_table[conn_handle];
  {
    filters.SetupProfilesFilter(
        IsFilterEnabled(kBtSnoopLogFilterProfilePbapModeProperty),
        IsFilterEnabled(kBtSnoopLogFilterProfileMapModeProperty));
  }

  LOG_INFO(
      "RFCOMM port is opened: handle=%d(0x%x),"
      " lcid=%d(0x%x), dlci=%d(0x%x), uuid=%d(0x%x)%s",
      conn_handle,
      conn_handle,
      local_cid,
      local_cid,
      dlci,
      dlci,
      uuid,
      uuid,
      flow ? " Credit Based Flow Control enabled" : "");

  if (uuid == PROFILE_UUID_PBAP || (dlci >> 1) == PROFILE_SCN_PBAP) {
    profile = FILTER_PROFILE_PBAP;
  } else if (uuid == PROFILE_UUID_MAP || (dlci >> 1) == PROFILE_SCN_MAP) {
    profile = FILTER_PROFILE_MAP;
  } else if (uuid == PROFILE_UUID_HFP_HS) {
    profile = FILTER_PROFILE_HFP_HS;
  } else if (uuid == PROFILE_UUID_HFP_HF) {
    profile = FILTER_PROFILE_HFP_HF;
  }

  if (profile >= 0) {
    filters.ProfileRfcommOpen(profile, local_cid, dlci, uuid, flow);
  }
}

void SnoopLogger::SetRfcommPortClose(
    uint16_t handle, uint16_t local_cid, uint8_t dlci, uint16_t uuid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      (!IsFilterEnabled(kBtSnoopLogFilterProfilePbapModeProperty) &&
       !IsFilterEnabled(kBtSnoopLogFilterProfileMapModeProperty))) {
    return;
  }

  std::lock_guard<std::mutex> lock(profiles_filter_mutex);

  auto& filters = profiles_filter_table[handle];
  LOG_INFO(
      "RFCOMM port is closed: handle=%d(0x%x),"
      " lcid=%d(0x%x), dlci=%d(0x%x), uuid=%d(0x%x)",
      handle,
      handle,
      local_cid,
      local_cid,
      dlci,
      dlci,
      uuid,
      uuid);

  filters.ProfileRfcommClose(filters.DlciToProfile(true, local_cid, dlci));
}

void SnoopLogger::SetL2capChannelOpen(
    uint16_t handle, uint16_t local_cid, uint16_t remote_cid, uint16_t psm, bool flow) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      (!IsFilterEnabled(kBtSnoopLogFilterProfilePbapModeProperty) &&
       !IsFilterEnabled(kBtSnoopLogFilterProfileMapModeProperty))) {
    return;
  }

  std::lock_guard<std::mutex> lock(profiles_filter_mutex);
  profile_type_t profile = FILTER_PROFILE_NONE;
  auto& filters = profiles_filter_table[handle];
  {
    filters.SetupProfilesFilter(
        IsFilterEnabled(kBtSnoopLogFilterProfilePbapModeProperty),
        IsFilterEnabled(kBtSnoopLogFilterProfileMapModeProperty));
  }

  LOG_INFO(
      "L2CAP channel is opened: handle=%d(0x%x), lcid=%d(0x%x),"
      " rcid=%d(0x%x), psm=0x%x%s",
      handle,
      handle,
      local_cid,
      local_cid,
      remote_cid,
      remote_cid,
      psm,
      flow ? " Standard or Enhanced Control enabled" : "");

  if (psm == PROFILE_PSM_RFCOMM) {
    filters.ch_rfc_l = local_cid;
    filters.ch_rfc_r = remote_cid;
  } else if (psm == PROFILE_PSM_PBAP) {
    profile = FILTER_PROFILE_PBAP;
  } else if (psm == PROFILE_PSM_MAP) {
    profile = FILTER_PROFILE_MAP;
  }

  if (profile >= 0) {
    filters.ProfileL2capOpen(profile, local_cid, remote_cid, psm, flow);
  }
}

void SnoopLogger::SetL2capChannelClose(uint16_t handle, uint16_t local_cid, uint16_t remote_cid) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered ||
      (!IsFilterEnabled(kBtSnoopLogFilterProfilePbapModeProperty) &&
       !IsFilterEnabled(kBtSnoopLogFilterProfileMapModeProperty))) {
    return;
  }

  std::lock_guard<std::mutex> lock(profiles_filter_mutex);

  auto& filters = profiles_filter_table[handle];

  LOG_INFO(
      "L2CAP channel is closed: handle=%d(0x%x), lcid=%d(0x%x),"
      " rcid=%d(0x%x)",
      handle,
      handle,
      local_cid,
      local_cid,
      remote_cid,
      remote_cid);

  filters.ProfileL2capClose(filters.CidToProfile(true, local_cid));
}

void SnoopLogger::FilterCapturedPacket(
    HciPacket& packet,
    Direction direction,
    PacketType type,
    uint32_t& length,
    PacketHeaderType header) {
  if (btsnoop_mode_ != kBtSnoopLogModeFiltered || type != PacketType::ACL) {
    return;
  }

  if (IsFilterEnabled(kBtSnoopLogFilterProfileA2dpProperty)) {
    if (IsA2dpMediaPacket(direction == Direction::INCOMING, (uint8_t*)packet.data())) {
      length = 0;
      return;
    }
  }

  if (IsFilterEnabled(kBtSnoopLogFilterHeadersProperty)) {
    CalculateAclPacketLength(length, (uint8_t*)packet.data(), direction == Direction::INCOMING);
  }

  if (IsFilterEnabled(kBtSnoopLogFilterProfilePbapModeProperty) ||
      IsFilterEnabled(kBtSnoopLogFilterProfileMapModeProperty)) {
    // If HeadersFiltered applied, do not use ProfilesFiltered
    if (length == ntohl(header.length_original)) {
      if (packet.size() + EXTRA_BUF_SIZE > DEFAULT_PACKET_SIZE) {
        // Add additional bytes for magic string in case
        // payload length is less than the length of magic string.
        packet.resize((size_t)(packet.size() + EXTRA_BUF_SIZE));
      }

      length = FilterProfiles(direction == Direction::INCOMING, (uint8_t*)packet.data());
      if (length == 0) return;
    }
  }

  if (IsFilterEnabled(kBtSnoopLogFilterProfileRfcommProperty)) {
    bool shouldFilter =
        SnoopLogger::ShouldFilterLog(direction == Direction::INCOMING, (uint8_t*)packet.data());
    if (shouldFilter) {
      length = L2CAP_HEADER_SIZE + PACKET_TYPE_LENGTH;
    }
  }
}

void SnoopLogger::Capture(HciPacket& packet, Direction direction, PacketType type) {
  uint64_t timestamp_us =
      std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch())
          .count();
  std::bitset<32> flags = 0;
  switch (type) {
    case PacketType::CMD:
      flags.set(0, false);
      flags.set(1, true);
      break;
    case PacketType::ACL:
    case PacketType::ISO:
    case PacketType::SCO:
      flags.set(0, direction == Direction::INCOMING);
      flags.set(1, false);
      break;
    case PacketType::EVT:
      flags.set(0, true);
      flags.set(1, true);
      break;
  }
  uint32_t length = packet.size() + /* type byte */ PACKET_TYPE_LENGTH;
  PacketHeaderType header = {.length_original = htonl(length),
                             .length_captured = htonl(length),
                             .flags = htonl(static_cast<uint32_t>(flags.to_ulong())),
                             .dropped_packets = 0,
                             .timestamp = htonll(timestamp_us + kBtSnoopEpochDelta),
                             .type = static_cast<uint8_t>(type)};
  {
    std::lock_guard<std::recursive_mutex> lock(file_mutex_);
    if (btsnoop_mode_ == kBtSnoopLogModeDisabled) {
      // btsnoop disabled, log in-memory btsnooz log only
      std::stringstream ss;
      size_t included_length = get_btsnooz_packet_length_to_write(packet, type, qualcomm_debug_log_enabled_);
      header.length_captured = htonl(included_length + /* type byte */ PACKET_TYPE_LENGTH);
      if (!ss.write(reinterpret_cast<const char*>(&header), sizeof(PacketHeaderType))) {
        LOG_ERROR("Failed to write packet header for btsnooz, error: \"%s\"", strerror(errno));
      }
      if (!ss.write(reinterpret_cast<const char*>(packet.data()), included_length)) {
        LOG_ERROR("Failed to write packet payload for btsnooz, error: \"%s\"", strerror(errno));
      }
      btsnooz_buffer_.Push(ss.str());
      return;
    }

    FilterCapturedPacket(packet, direction, type, length, header);

    if (length == 0) {
      return;
    } else if (length != ntohl(header.length_original)) {
      header.length_captured = htonl(length);
    }

    packet_counter_++;
    if (packet_counter_ > max_packets_per_file_) {
      OpenNextSnoopLogFile();
    }
    if (!btsnoop_ostream_.write(reinterpret_cast<const char*>(&header), sizeof(PacketHeaderType))) {
      LOG_ERROR("Failed to write packet header for btsnoop, error: \"%s\"", strerror(errno));
    }
    if (!btsnoop_ostream_.write(reinterpret_cast<const char*>(packet.data()), length - 1)) {
      LOG_ERROR("Failed to write packet payload for btsnoop, error: \"%s\"", strerror(errno));
    }

    if (socket_ != nullptr) {
      socket_->Write(&header, sizeof(PacketHeaderType));
      socket_->Write(packet.data(), (size_t)(length - 1));
    }

    // std::ofstream::flush() pushes user data into kernel memory. The data will be written even if this process
    // crashes. However, data will be lost if there is a kernel panic, which is out of scope of BT snoop log.
    // NOTE: std::ofstream::write() followed by std::ofstream::flush() has similar effect as UNIX write(fd, data, len)
    //       as write() syscall dumps data into kernel memory directly
    if (!btsnoop_ostream_.flush()) {
      LOG_ERROR("Failed to flush, error: \"%s\"", strerror(errno));
    }
  }
}

void SnoopLogger::DumpSnoozLogToFile(const std::vector<std::string>& data) const {
  std::lock_guard<std::recursive_mutex> lock(file_mutex_);
  if (btsnoop_mode_ != kBtSnoopLogModeDisabled) {
    LOG_DEBUG("btsnoop log is enabled, skip dumping btsnooz log");
    return;
  }

  auto last_file_path = get_last_log_path(snooz_log_path_);

  if (os::FileExists(snooz_log_path_)) {
    if (!os::RenameFile(snooz_log_path_, last_file_path)) {
      LOG_ERROR(
          "Unabled to rename existing snooz log from \"%s\" to \"%s\"",
          snooz_log_path_.c_str(),
          last_file_path.c_str());
    }
  } else {
    LOG_INFO("Previous log file \"%s\" does not exist, skip renaming", snooz_log_path_.c_str());
  }

  mode_t prevmask = umask(0);
  // do not use std::ios::app as we want override the existing file
  std::ofstream btsnooz_ostream(snooz_log_path_, std::ios::binary | std::ios::out);
  if (!btsnooz_ostream.good()) {
    LOG_ALWAYS_FATAL("Unable to open snoop log at \"%s\", error: \"%s\"", snooz_log_path_.c_str(), strerror(errno));
  }
  umask(prevmask);
  if (!btsnooz_ostream.write(
          reinterpret_cast<const char*>(&SnoopLoggerCommon::kBtSnoopFileHeader),
          sizeof(SnoopLoggerCommon::FileHeaderType))) {
    LOG_ALWAYS_FATAL("Unable to write file header to \"%s\", error: \"%s\"", snooz_log_path_.c_str(), strerror(errno));
  }
  for (const auto& packet : data) {
    if (!btsnooz_ostream.write(packet.data(), packet.size())) {
      LOG_ERROR("Failed to write packet payload for btsnooz, error: \"%s\"", strerror(errno));
    }
  }
  if (!btsnooz_ostream.flush()) {
    LOG_ERROR("Failed to flush, error: \"%s\"", strerror(errno));
  }
}

void SnoopLogger::ListDependencies(ModuleList* /* list */) const {
  // We have no dependencies
}

void SnoopLogger::Start() {
  std::lock_guard<std::recursive_mutex> lock(file_mutex_);
  if (btsnoop_mode_ != kBtSnoopLogModeDisabled) {
    OpenNextSnoopLogFile();

    if (btsnoop_mode_ == kBtSnoopLogModeFiltered) {
      EnableFilters();
    }

    auto snoop_logger_socket = std::make_unique<SnoopLoggerSocket>(&syscall_if);
    snoop_logger_socket_thread_ =
        std::make_unique<SnoopLoggerSocketThread>(std::move(snoop_logger_socket));
    auto thread_started_future = snoop_logger_socket_thread_->Start();
    thread_started_future.wait();
    if (thread_started_future.get()) {
      RegisterSocket(snoop_logger_socket_thread_.get());
    } else {
      snoop_logger_socket_thread_->Stop();
      snoop_logger_socket_thread_.reset();
      snoop_logger_socket_thread_ = nullptr;
    }
  }
  alarm_ = std::make_unique<os::RepeatingAlarm>(GetHandler());
  alarm_->Schedule(
      common::Bind(&delete_old_btsnooz_files, snooz_log_path_, snooz_log_life_time_), snooz_log_delete_alarm_interval_);
}

void SnoopLogger::Stop() {
  std::lock_guard<std::recursive_mutex> lock(file_mutex_);
  LOG_DEBUG("Closing btsnoop log data at %s", snoop_log_path_.c_str());
  CloseCurrentSnoopLogFile();

  if (snoop_logger_socket_thread_ != nullptr) {
    snoop_logger_socket_thread_->Stop();
    snoop_logger_socket_thread_.reset();
    snoop_logger_socket_thread_ = nullptr;
    socket_ = nullptr;
  }

  btsnoop_mode_.clear();
  // Disable all filters
  DisableFilters();

  // Cancel the alarm
  alarm_->Cancel();
  alarm_.reset();
  // delete any existing snooz logs
  if (!snoop_log_persists) {
    delete_btsnoop_files(snooz_log_path_);
  }
}

DumpsysDataFinisher SnoopLogger::GetDumpsysData(
    flatbuffers::FlatBufferBuilder* /* builder */) const {
  LOG_DEBUG("Dumping btsnooz log data to %s", snooz_log_path_.c_str());
  DumpSnoozLogToFile(btsnooz_buffer_.Pull());
  return EmptyDumpsysDataFinisher;
}

size_t SnoopLogger::GetMaxPacketsPerFile() {
  // Allow override max packet per file via system property
  auto max_packets_per_file = kDefaultBtSnoopMaxPacketsPerFile;
  {
    auto max_packets_per_file_prop = os::GetSystemProperty(kBtSnoopMaxPacketsPerFileProperty);
    if (max_packets_per_file_prop) {
      auto max_packets_per_file_number = common::Uint64FromString(max_packets_per_file_prop.value());
      if (max_packets_per_file_number) {
        max_packets_per_file = max_packets_per_file_number.value();
      }
    }
  }
  return max_packets_per_file;
}

size_t SnoopLogger::GetMaxPacketsPerBuffer() {
  // We want to use at most 256 KB memory for btsnooz log for release builds
  // and 512 KB memory for userdebug/eng builds
  auto is_debuggable = os::GetSystemPropertyBool(kIsDebuggableProperty, false);

  size_t btsnooz_max_memory_usage_bytes = (is_debuggable ? 1024 : 256) * 1024;
  // Calculate max number of packets based on max memory usage and max packet size
  return btsnooz_max_memory_usage_bytes / kDefaultBtSnoozMaxBytesPerPacket;
}

std::string SnoopLogger::GetBtSnoopMode() {
  // Default mode is FILTERED on userdebug/eng build, DISABLED on user build.
  // In userdebug/eng build, it can also be overwritten by modifying the global setting
  std::string default_mode = kBtSnoopLogModeDisabled;
  {
    auto is_debuggable = os::GetSystemPropertyBool(kIsDebuggableProperty, false);
    if (is_debuggable) {
      auto default_mode_property = os::GetSystemProperty(kBtSnoopDefaultLogModeProperty);
      if (default_mode_property) {
        default_mode = std::move(default_mode_property.value());
      } else {
        default_mode = kBtSnoopLogModeFiltered;
      }
    }
  }

  // Get the actual mode if exist
  std::string btsnoop_mode = default_mode;
  {
    auto btsnoop_mode_prop = os::GetSystemProperty(kBtSnoopLogModeProperty);
    if (btsnoop_mode_prop) {
      btsnoop_mode = std::move(btsnoop_mode_prop.value());
    }
  }

  // If Snoop Logger already set up, return current mode
  bool btsnoop_mode_empty = btsnoop_mode_.empty();
  LOG_INFO("btsnoop_mode_empty: %d", btsnoop_mode_empty);
  if (!btsnoop_mode_empty) {
    return btsnoop_mode_;
  }

  return btsnoop_mode;
}

void SnoopLogger::RegisterSocket(SnoopLoggerSocketInterface* socket) {
  std::lock_guard<std::recursive_mutex> lock(file_mutex_);
  socket_ = socket;
}

bool SnoopLogger::IsBtSnoopLogPersisted() {
  auto is_debuggable = os::GetSystemPropertyBool(kIsDebuggableProperty, false);
  return is_debuggable && os::GetSystemPropertyBool(kBtSnoopLogPersists, false);
}

bool SnoopLogger::IsQualcommDebugLogEnabled() {
  // Check system prop if the soc manufacturer is Qualcomm
  bool qualcomm_debug_log_enabled = false;
  {
    auto soc_manufacturer_prop = os::GetSystemProperty(kSoCManufacturerProperty);
    qualcomm_debug_log_enabled = soc_manufacturer_prop.has_value() &&
                                 common::StringTrim(soc_manufacturer_prop.value()) == kSoCManufacturerQualcomm;
  }
  return qualcomm_debug_log_enabled;
}

const ModuleFactory SnoopLogger::Factory = ModuleFactory([]() {
  return new SnoopLogger(
      os::ParameterProvider::SnoopLogFilePath(),
      os::ParameterProvider::SnoozLogFilePath(),
      GetMaxPacketsPerFile(),
      GetMaxPacketsPerBuffer(),
      GetBtSnoopMode(),
      IsQualcommDebugLogEnabled(),
      kBtSnoozLogLifeTime,
      kBtSnoozLogDeleteRepeatingAlarmInterval,
      IsBtSnoopLogPersisted());
});

}  // namespace hal
}  // namespace bluetooth
