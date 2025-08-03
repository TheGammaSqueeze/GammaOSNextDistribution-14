/*
 * Copyright 2023 The Android Open Source Project
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

#define LOG_TAG "stack_power_tel"

#include "osi/include/stack_power_telemetry.h"

#include <base/logging.h>
#include <sys/stat.h>
#include <time.h>

#include <atomic>
#include <list>
#include <map>
#include <mutex>

#include "common/init_flags.h"
#include "os/log.h"
#include "osi/include/properties.h"
#include "stack/include/acl_api_types.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/btm_status.h"
#include "types/raw_address.h"

time_t get_current_time() { return time(0); }

namespace {

constexpr int64_t kTrafficLogTime = 120;  // 120seconds
constexpr uint8_t kLogEntriesSize{15};
constexpr std::string_view kLogPerChannelProperty =
    "bluetooth.powertelemetry.log_per_channel.enabled";
bool power_telemerty_enabled_ =
    bluetooth::common::init_flags::bluetooth_power_telemetry_is_enabled();

std::string GetTimeString(time_t tstamp) {
  char buffer[15];
  tm* nTm = localtime(&tstamp);
  strftime(buffer, 15, "%m-%d %H:%M:%S", nTm);
  return std::string(buffer);
}

enum class State {
  kDisconnected = 0,
  kConnected = 1,
};

enum class ChannelType {
  kUnknown = 0,
  kRfcomm = 1,
  kL2cap = 2,
};

ChannelType PsmToChannelType(const uint16_t& psm) {
  switch (psm) {
    case BT_PSM_RFCOMM:
      return ChannelType::kRfcomm;
      break;
  }
  return ChannelType::kL2cap;
}

struct Duration {
  time_t begin;
  time_t end;
};

struct DataTransfer {
  struct {
    int64_t bytes{0};
  } rx, tx;
};

struct LinkDetails {
  RawAddress bd_addr;
  uint16_t handle = 0;
  Duration duration;
  uint8_t tx_power_level = 0;
};

struct ChannelDetails {
  RawAddress bd_addr;
  int32_t psm = 0;
  struct {
    uint16_t cid = 0;
  } src, dst;
  State state = State::kDisconnected;
  ChannelType channel_type = ChannelType::kUnknown;
  DataTransfer data_transfer;
  Duration duration;
  struct {
    time_t last_data_sent;
  } rx, tx;
};

struct AclPacketDetails {
  struct {
    uint32_t pkt_count = 0;
    int64_t byte_count = 0;
  } rx, tx;
};

struct AdvDetails {
  Duration active;
};

struct ScanDetails {
  int32_t count = 0;
};

struct SniffData {
  RawAddress bd_addr;
  uint32_t sniff_count = 0, active_count = 0;
  time_t sniff_duration_ts = 0, active_duration_ts = 0;
  time_t last_mode_change_ts = get_current_time();
};

class LogDataContainer {
 public:
  struct Duration lifetime;
  std::map<RawAddress, std::list<ChannelDetails>> channel_map;
  DataTransfer l2c_data, rfc_data;
  std::map<uint16_t, SniffData> sniff_activity_map;
  struct {
    std::map<uint16_t, LinkDetails> link_details_map;
    std::list<LinkDetails> link_details_list;
  } acl, sco;
  std::list<AdvDetails> adv_list;
  ScanDetails scan_details, inq_scan_details, le_scan_details;
  AclPacketDetails acl_pkt_ds, hci_cmd_evt_ds;
};

}  // namespace

struct power_telemetry::PowerTelemetryImpl {
  PowerTelemetryImpl() {
    idx_containers = 0;
    traffic_logged_ts_ = get_current_time();
    log_per_channel_ = osi_property_get_bool(
        std::string(kLogPerChannelProperty).c_str(), false);
  }

  LogDataContainer& GetCurrentLogDataContainer() {
    return log_data_containers_[idx_containers];
  }

  void maybe_log_data() {
    if ((get_current_time() - traffic_logged_ts_) >= kTrafficLogTime) {
      LogDataTransfer();
    }
  }

  void LogDataTransfer();
  void RecordLogDataContainer();

  mutable std::mutex dumpsys_mutex_;
  LogDataContainer log_data_containers_[kLogEntriesSize];
  std::atomic_int idx_containers;
  time_t traffic_logged_ts_ = 0;
  struct {
    struct {
      int64_t bytes_ = 0;
    } rx, tx;
  } l2c, rfc;
  struct {
    uint32_t pkt_ = 0;
    int64_t len_ = 0;
  } rx, tx;

  struct {
    uint16_t count_;
  } scan, inq_scan, ble_adv, ble_scan;

  struct {
    uint32_t count_ = 0;
  } cmd, event;
  bool scan_timer_started_ = false;
  bool log_per_channel_ = false;
};

void power_telemetry::PowerTelemetryImpl::LogDataTransfer() {
  if (!power_telemerty_enabled_) return;

  LogDataContainer& ldc = GetCurrentLogDataContainer();

  if ((l2c.rx.bytes_ != 0) || (l2c.tx.bytes_ != 0)) {
    ldc.l2c_data = {
        .rx =
            {
                .bytes = l2c.rx.bytes_,
            },
        .tx =
            {
                .bytes = l2c.tx.bytes_,
            },
    };
    l2c = {};
  }

  if ((rfc.rx.bytes_ != 0) || (rfc.tx.bytes_ != 0)) {
    ldc.rfc_data = {
        .rx =
            {
                .bytes = rfc.rx.bytes_,
            },
        .tx =
            {
                .bytes = rfc.tx.bytes_,
            },
    };
    rfc = {};
  }

  if (scan.count_ != 0) {
    ldc.scan_details = {
        .count = scan.count_,
    };
    scan.count_ = 0;
  }

  if (inq_scan.count_ != 0) {
    ldc.inq_scan_details = {
        .count = inq_scan.count_,
    };
    inq_scan.count_ = 0;
  }

  if ((rx.pkt_ != 0) || (tx.pkt_ != 0)) {
    ldc.acl_pkt_ds = {
        .rx =
            {
                .pkt_count = rx.pkt_,
                .byte_count = rx.len_,
            },
        .tx =
            {
                .pkt_count = tx.pkt_,
                .byte_count = tx.len_,
            },
    };
    rx.pkt_ = tx.pkt_ = rx.len_ = tx.len_ = 0;
  }

  if ((cmd.count_ != 0) || (event.count_ != 0)) {
    ldc.hci_cmd_evt_ds = {
        .rx =
            {
                .pkt_count = event.count_,
            },
        .tx =
            {
                .pkt_count = cmd.count_,
            },
    };
    cmd.count_ = event.count_ = 0;
  }

  if (ble_scan.count_ != 0) {
    ldc.le_scan_details = {
        .count = ble_scan.count_,
    };
    ble_scan.count_ = 0;
  }

  ldc.lifetime.begin = traffic_logged_ts_;
  ldc.lifetime.end = get_current_time();

  traffic_logged_ts_ = get_current_time();
  RecordLogDataContainer();
}

void power_telemetry::PowerTelemetryImpl::RecordLogDataContainer() {
  if (!power_telemerty_enabled_) return;

  LogDataContainer& ldc = GetCurrentLogDataContainer();

  LOG_INFO(
      "bt_power: scan: %d, inqScan: %d, aclTx: %d, aclRx: %d, hciCmd: %d, "
      "hciEvt: %d, bleScan: %d",
      ldc.scan_details.count, ldc.inq_scan_details.count,
      ldc.acl_pkt_ds.tx.pkt_count, ldc.acl_pkt_ds.rx.pkt_count,
      ldc.hci_cmd_evt_ds.tx.pkt_count, ldc.hci_cmd_evt_ds.rx.pkt_count,
      ldc.le_scan_details.count);

  idx_containers++;
  if (idx_containers >= kLogEntriesSize) {
    idx_containers = 0;
  }

  log_data_containers_[idx_containers] = LogDataContainer();
}

power_telemetry::PowerTelemetry& power_telemetry::GetInstance() {
  static power_telemetry::PowerTelemetry power_telemetry;
  return power_telemetry;
}

power_telemetry::PowerTelemetry::PowerTelemetry() {
  pimpl_ = std::make_unique<PowerTelemetryImpl>();
}

void power_telemetry::PowerTelemetry::LogInqScanStarted() {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->inq_scan.count_++;
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogInqScanStopped() {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogBleScan(uint16_t num_resps) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->ble_scan.count_ += num_resps;
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogBleAdvStarted() {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  const time_t current_time = get_current_time();
  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
  ldc.adv_list.emplace_back(AdvDetails{.active.begin = current_time});
}

void power_telemetry::PowerTelemetry::LogBleAdvStopped() {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  const time_t current_time = get_current_time();

  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
  if (ldc.adv_list.size() == 0) {
    LOG_WARN("Empty advList. Skip LogBleAdvDetails.");
    return;
  }
  ldc.adv_list.back().active.end = current_time;
}

void power_telemetry::PowerTelemetry::LogTxPower(void* res) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  tBTM_TX_POWER_RESULT* result = (tBTM_TX_POWER_RESULT*)res;
  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();

  if (result->status != BTM_SUCCESS) {
    return;
  }

  for (auto it : ldc.acl.link_details_map) {
    uint16_t handle = it.first;
    LinkDetails lds = it.second;
    if (lds.bd_addr == result->rem_bda) {
      lds.tx_power_level = result->tx_power;
      ldc.acl.link_details_map[handle] = lds;
      break;
    }
  }
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogLinkDetails(uint16_t handle,
                                                     const RawAddress& bd_addr,
                                                     bool is_connected,
                                                     bool is_acl_link) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
  std::map<uint16_t, LinkDetails>& link_map =
      is_acl_link ? ldc.acl.link_details_map : ldc.sco.link_details_map;
  std::list<LinkDetails>& link_list =
      is_acl_link ? ldc.acl.link_details_list : ldc.sco.link_details_list;

  if (is_connected == false && link_map.count(handle) != 0) {
    LinkDetails link_details = link_map[handle];
    link_details.duration.end = get_current_time();
    link_list.push_back(link_details);
    link_map.erase(handle);
  } else if (is_connected == true) {
    link_map[handle] = {
        .bd_addr = bd_addr,
        .handle = handle,
        .duration.begin = get_current_time(),
        .tx_power_level = 0,
    };

    if (is_acl_link) {
      SniffData sniff_data;
      if (ldc.sniff_activity_map.count(handle) != 0) {
        ldc.sniff_activity_map.erase(handle);
      }
      sniff_data.bd_addr = bd_addr;
      sniff_data.active_count = 1;
      sniff_data.last_mode_change_ts = get_current_time();
      ldc.sniff_activity_map[handle] = sniff_data;
    }
  }

  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogHciCmdDetail() {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->cmd.count_++;
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogHciEvtDetail() {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->event.count_++;
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogSniffStarted(
    uint16_t handle, const RawAddress& bd_addr) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  const time_t current_timestamp = get_current_time();
  SniffData sniff_data;
  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
  if (ldc.sniff_activity_map.count(handle) == 0) {
    sniff_data.bd_addr = bd_addr;
  } else {
    sniff_data = ldc.sniff_activity_map[handle];
  }
  sniff_data.sniff_count++;
  sniff_data.active_duration_ts +=
      current_timestamp - sniff_data.last_mode_change_ts;
  sniff_data.last_mode_change_ts = get_current_time();
  ldc.sniff_activity_map[handle] = sniff_data;

  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogSniffStopped(
    uint16_t handle, const RawAddress& bd_addr) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  const time_t current_timestamp = get_current_time();
  SniffData sniff_data;
  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
  if (ldc.sniff_activity_map.count(handle) == 0) {
    sniff_data.bd_addr = bd_addr;
  } else {
    sniff_data = ldc.sniff_activity_map[handle];
  }
  sniff_data.active_count++;
  sniff_data.sniff_duration_ts +=
      current_timestamp - sniff_data.last_mode_change_ts;
  sniff_data.last_mode_change_ts = get_current_time();
  ldc.sniff_activity_map[handle] = sniff_data;

  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogScanStarted() {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->scan.count_++;
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogTxAclPktData(uint16_t len) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->tx.pkt_++;
  pimpl_->tx.len_ += len;
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogRxAclPktData(uint16_t len) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->rx.pkt_++;
  pimpl_->rx.len_ += len;
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogChannelConnected(
    uint16_t psm, int32_t src_id, int32_t dst_id, const RawAddress& bd_addr) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  std::list<ChannelDetails> channel_details_list;
  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
  const ChannelType channel_type = PsmToChannelType(psm);
  ChannelDetails channel_details = {
      .bd_addr = bd_addr,
      .psm = psm,
      .src.cid = static_cast<uint16_t>(src_id),
      .dst.cid = static_cast<uint16_t>(dst_id),
      .state = State::kConnected,
      .channel_type = channel_type,
      .data_transfer = {},
      .duration.begin = get_current_time(),
      .rx = {},
      .tx = {},
  };

  if (ldc.channel_map.count(bd_addr) == 0) {
    ldc.channel_map.insert(std::pair<RawAddress, std::list<ChannelDetails>>(
        bd_addr, std::list<ChannelDetails>({channel_details})));
  } else {
    ldc.channel_map[bd_addr].emplace_back(channel_details);
  }

  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogChannelDisconnected(
    uint16_t psm, int32_t src_id, int32_t dst_id, const RawAddress& bd_addr) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  std::list<ChannelDetails> channel_details_list;
  LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
  if (ldc.channel_map.count(bd_addr) == 0) {
    return;
  }

  const ChannelType channel_type = PsmToChannelType(psm);

  for (auto& channel_detail : ldc.channel_map[bd_addr]) {
    if (channel_detail.src.cid == src_id && channel_detail.dst.cid == dst_id &&
        channel_detail.channel_type == channel_type) {
      channel_detail.state = State::kDisconnected;
      channel_detail.duration.end = get_current_time();
      break;
    }
  }

  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogTxBytes(uint16_t psm, int32_t src_id,
                                                 int32_t dst_id,
                                                 const RawAddress& bd_addr,
                                                 int32_t num_bytes) {
  if (!power_telemerty_enabled_) return;

  const ChannelType channel_type = PsmToChannelType(psm);
  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  if (pimpl_->log_per_channel_ == true) {
    std::list<ChannelDetails> channel_details_list;
    LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
    if (ldc.channel_map.count(bd_addr) == 0) {
      return;
    }

    for (auto& channel_details : ldc.channel_map[bd_addr]) {
      if (channel_details.src.cid == src_id &&
          channel_details.dst.cid == dst_id &&
          channel_details.channel_type == channel_type) {
        channel_details.data_transfer.tx.bytes += num_bytes;
        channel_details.tx.last_data_sent = get_current_time();
        break;
      }
    }
  }
  if (channel_type == ChannelType::kRfcomm) {
    pimpl_->rfc.tx.bytes_ += num_bytes;
  } else {
    pimpl_->l2c.tx.bytes_ += num_bytes;
  }
  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::LogRxBytes(uint16_t psm, int32_t src_id,
                                                 int32_t dst_id,
                                                 const RawAddress& bd_addr,
                                                 int32_t num_bytes) {
  if (!power_telemerty_enabled_) return;

  const ChannelType channel_type = PsmToChannelType(psm);
  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  if (pimpl_->log_per_channel_ == true) {
    std::list<ChannelDetails> channel_details_list;
    LogDataContainer& ldc = pimpl_->GetCurrentLogDataContainer();
    if (ldc.channel_map.count(bd_addr) == 0) {
      return;
    }

    for (auto& channel_detail : ldc.channel_map[bd_addr]) {
      if (channel_detail.src.cid == src_id &&
          channel_detail.dst.cid == dst_id &&
          channel_detail.channel_type == channel_type) {
        channel_detail.data_transfer.rx.bytes += num_bytes;
        channel_detail.rx.last_data_sent = get_current_time();
        break;
      }
    }
  }

  switch (channel_type) {
    case ChannelType::kRfcomm:
      pimpl_->rfc.rx.bytes_ += num_bytes;
      break;
    case ChannelType::kL2cap:
      pimpl_->l2c.rx.bytes_ += num_bytes;
      break;
    case ChannelType::kUnknown:
      break;
  }

  pimpl_->maybe_log_data();
}

void power_telemetry::PowerTelemetry::Dumpsys(int32_t fd) {
  if (!power_telemerty_enabled_) return;

  std::lock_guard<std::mutex> lock(pimpl_->dumpsys_mutex_);
  pimpl_->RecordLogDataContainer();

  dprintf(fd, "\nPower Telemetry Data:\n");
  dprintf(fd, "\nBR/EDR Scan Events:\n");
  dprintf(fd, "%-22s %-22s %-15s\n", "StartTimeStamp", "EndTimeStamp",
          "Number of Scans");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    if (ldc.scan_details.count == 0) {
      continue;
    }
    dprintf(fd, "%-22s %-22s %-15d\n",
            GetTimeString(ldc.lifetime.begin).c_str(),
            GetTimeString(ldc.lifetime.end).c_str(), ldc.scan_details.count);
  }
  dprintf(fd, "\nBR/EDR InqScan Events:\n");
  dprintf(fd, "%-22s %-22s %-15s\n", "StartTimeStamp", "EndTimeStamp",
          "Number of InqScans");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    if (ldc.inq_scan_details.count == 0) {
      continue;
    }
    dprintf(
        fd, "%-22s %-22s %-15d\n", GetTimeString(ldc.lifetime.begin).c_str(),
        GetTimeString(ldc.lifetime.end).c_str(), ldc.inq_scan_details.count);
  }

  dprintf(fd, "\nACL Packet Details:\n");
  dprintf(fd, "%-22s %-22s %-12s %-12s %-12s %-12s\n", "StartTimeStamp",
          "EndTimeStamp", "Tx Packets", "Tx Bytes", "Rx Packets", "Rx Bytes");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    if ((ldc.acl_pkt_ds.tx.byte_count == 0) &&
        (ldc.acl_pkt_ds.rx.byte_count == 0)) {
      continue;
    }
    dprintf(fd, "%-22s %-22s %-12d %-12ld %-12d %-12ld\n",
            GetTimeString(ldc.lifetime.begin).c_str(),
            GetTimeString(ldc.lifetime.end).c_str(),
            ldc.acl_pkt_ds.tx.pkt_count, (long)ldc.acl_pkt_ds.tx.byte_count,
            ldc.acl_pkt_ds.rx.pkt_count, (long)ldc.acl_pkt_ds.rx.byte_count);
  }

  dprintf(fd, "\nHCI CMD/EVT Details:\n");
  dprintf(fd, "%-22s %-22s %-14s %-14s\n", "StartTimeStamp", "EndTimeStamp",
          "HCI Commands", "HCI Events");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    if ((ldc.hci_cmd_evt_ds.tx.pkt_count == 0) &&
        (ldc.hci_cmd_evt_ds.rx.pkt_count == 0)) {
      continue;
    }
    dprintf(fd, "%-22s %-22s %-14d %-14d\n",
            GetTimeString(ldc.lifetime.begin).c_str(),
            GetTimeString(ldc.lifetime.end).c_str(),
            ldc.hci_cmd_evt_ds.tx.pkt_count, ldc.hci_cmd_evt_ds.rx.pkt_count);
  }
  dprintf(fd, "\nBLE Scan Details:\n");
  dprintf(fd, "%-22s %-22s %-14s\n", "StartTimeStamp", "EndTimeStamp",
          "Number of scans");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    if (ldc.le_scan_details.count == 0) {
      continue;
    }
    dprintf(fd, "%-22s %-22s %-14d\n",
            GetTimeString(ldc.lifetime.begin).c_str(),
            GetTimeString(ldc.lifetime.end).c_str(), ldc.le_scan_details.count);
  }
  dprintf(fd, "\nL2CAP/RFCOMM Channel Events:\n");
  dprintf(fd, "%-19s %-7s %-7s %-7s %-8s %-22s", "RemoteAddress", "Type",
          "SrcId", "DstId", "PSM", "ConnectedTimeStamp");
  dprintf(fd, " %-22s %-14s ", "DisconnectedTimeStamp", "State");
  if (pimpl_->log_per_channel_ == true) {
    dprintf(fd, " %-10s %-10s %-22s %-22s", "TxBytes", "RxBytes",
            "LastTxTimeStamp", "LastRxTimeStamp");
  }
  dprintf(fd, "\n");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    for (auto& itr : ldc.channel_map) {
      const RawAddress& bd_addr = itr.first;
      std::list<ChannelDetails> channel_details_list = itr.second;
      for (auto& channel_details : channel_details_list) {
        dprintf(fd, "%-19s ", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        dprintf(fd, "%-7s %-7d %-7d %-8d %-22s %-22s %-14s",
                (channel_details.channel_type == ChannelType::kRfcomm)
                    ? "RFCOMM"
                    : "L2CAP",
                channel_details.src.cid, channel_details.dst.cid,
                channel_details.psm,
                GetTimeString(channel_details.duration.begin).c_str(),
                GetTimeString(channel_details.duration.end).c_str(),
                (channel_details.state == State::kDisconnected) ? "DISCONNECTED"
                                                                : "CONNECTED");
        if (pimpl_->log_per_channel_ == true) {
          dprintf(fd, "%-10ld %-10ld %-22s %-22s",
                  (long)channel_details.data_transfer.tx.bytes,
                  (long)channel_details.data_transfer.rx.bytes,
                  GetTimeString(channel_details.tx.last_data_sent).c_str(),
                  GetTimeString(channel_details.rx.last_data_sent).c_str());
        }
        dprintf(fd, "\n");
      }
    }
  }

  dprintf(fd, "\n\nBluetooth Data Traffic Details\n");
  dprintf(fd, "L2cap Data Traffic\n");
  dprintf(fd, "%-22s %-22s %-10s %-10s\n", "StartTime", "EndTime", "TxBytes",
          "RxBytes");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    if (ldc.l2c_data.tx.bytes == 0 && ldc.l2c_data.rx.bytes) {
      continue;
    }
    dprintf(fd, "%-22s %-22s %-10ld %-10ld\n",
            GetTimeString(ldc.lifetime.begin).c_str(),
            GetTimeString(ldc.lifetime.end).c_str(),
            (long)ldc.l2c_data.tx.bytes, (long)ldc.l2c_data.rx.bytes);
  }

  dprintf(fd, "\nRfcomm Data Traffic\n");
  dprintf(fd, "%-22s %-22s %-10s %-10s\n", "StartTime", "EndTime", "TxBytes",
          "RxBytes");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    if (ldc.rfc_data.tx.bytes == 0 && ldc.rfc_data.rx.bytes == 0) {
      continue;
    }
    dprintf(fd, "%-22s %-22s %-10ld %-10ld\n",
            GetTimeString(ldc.lifetime.begin).c_str(),
            GetTimeString(ldc.lifetime.end).c_str(),
            (long)ldc.rfc_data.tx.bytes, (long)ldc.rfc_data.rx.bytes);
  }

  dprintf(fd, "\n\nSniff Activity Details\n");
  dprintf(fd, "%-8s %-19s %-19s %-24s %-19s %-24s\n", "Handle", "BDADDR",
          "ActiveModeCount", "ActiveModeDuration(sec)", "SniffModeCount",
          "SniffModeDuration(sec)");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    for (auto itr : ldc.sniff_activity_map) {
      uint16_t handle = itr.first;
      SniffData sniff_data = itr.second;
      dprintf(fd, "%-8d %-19s %-19d %-24ld %-19d %-24ld\n", handle,
              ADDRESS_TO_LOGGABLE_CSTR(sniff_data.bd_addr),
              sniff_data.active_count, (long)sniff_data.active_duration_ts,
              sniff_data.sniff_count, (long)sniff_data.sniff_duration_ts);
    }
  }

  dprintf(fd, "\n\nACL Link Details\n");
  dprintf(fd, "%-6s %-19s %-22s %-22s %-8s\n", "handle", "BDADDR",
          "ConnectedTimeStamp", "DisconnectedTimeStamp", "TxPower");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    for (auto it : ldc.acl.link_details_map) {
      uint16_t handle = it.first;
      LinkDetails lds = it.second;
      dprintf(fd, "%-6d %-19s %-22s %-22s %-8d\n", handle,
              ADDRESS_TO_LOGGABLE_CSTR(lds.bd_addr),
              GetTimeString(lds.duration.begin).c_str(),
              GetTimeString(lds.duration.end).c_str(), lds.tx_power_level);
    }

    for (auto& it : ldc.acl.link_details_list) {
      dprintf(fd, "%-6d %-19s %-22s %-22s %-8d\n", it.handle,
              ADDRESS_TO_LOGGABLE_CSTR(it.bd_addr),
              GetTimeString(it.duration.begin).c_str(),
              GetTimeString(it.duration.end).c_str(), it.tx_power_level);
    }
  }
  dprintf(fd, "\nSCO Link Details\n");
  dprintf(fd, "%-6s %-19s %-22s %-22s\n", "handle", "BDADDR",
          "ConnectedTimeStamp", "DisconnectedTimeStamp");
  for (auto&& ldc : pimpl_->log_data_containers_) {
    for (auto& it : ldc.sco.link_details_list) {
      dprintf(fd, "%-6d %-19s %-22s %-22s\n", it.handle,
              ADDRESS_TO_LOGGABLE_CSTR(it.bd_addr),
              GetTimeString(it.duration.begin).c_str(),
              GetTimeString(it.duration.end).c_str());
    }
  }

  dprintf(fd, "\n\n");
}
