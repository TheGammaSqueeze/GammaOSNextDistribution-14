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

#include <base/logging.h>
#include <fcntl.h>
#ifdef __ANDROID__
#include <statslog_bt.h>
#endif
#include <bluetooth/log.h>
#include <stdio.h>
#include <sys/stat.h>

#include <cerrno>

#include "btif/include/stack_manager_t.h"
#include "btif_bqr.h"
#include "btif_common.h"
#include "btif_storage.h"
#include "btm_api.h"
#include "btm_ble_api.h"
#include "common/init_flags.h"
#include "common/leaky_bonded_queue.h"
#include "common/time_util.h"
#include "core_callbacks.h"
#include "internal_include/bt_trace.h"
#include "osi/include/properties.h"
#include "raw_address.h"
#include "stack/btm/btm_dev.h"
#include "stack/include/bt_types.h"

namespace bluetooth {
namespace bqr {

using bluetooth::common::LeakyBondedQueue;
using std::chrono::system_clock;

// The instance of BQR event queue
static std::unique_ptr<LeakyBondedQueue<BqrVseSubEvt>> kpBqrEventQueue(
    new LeakyBondedQueue<BqrVseSubEvt>(kBqrEventQueueSize));

static uint16_t vendor_cap_supported_version;

class BluetoothQualityReportInterfaceImpl;
std::unique_ptr<BluetoothQualityReportInterface> bluetoothQualityReportInstance;

void BqrVseSubEvt::ParseBqrLinkQualityEvt(uint8_t length,
                                          const uint8_t* p_param_buf) {
  if (length < kLinkQualityParamTotalLen) {
    log::fatal(
        "Parameter total length: {} is abnormal. It shall be not shorter than: "
        "{}",
        length, kLinkQualityParamTotalLen);
    return;
  }

  STREAM_TO_UINT8(bqr_link_quality_event_.quality_report_id, p_param_buf);
  STREAM_TO_UINT8(bqr_link_quality_event_.packet_types, p_param_buf);
  STREAM_TO_UINT16(bqr_link_quality_event_.connection_handle, p_param_buf);
  STREAM_TO_UINT8(bqr_link_quality_event_.connection_role, p_param_buf);
  STREAM_TO_INT8(bqr_link_quality_event_.tx_power_level, p_param_buf);
  STREAM_TO_INT8(bqr_link_quality_event_.rssi, p_param_buf);
  STREAM_TO_UINT8(bqr_link_quality_event_.snr, p_param_buf);
  STREAM_TO_UINT8(bqr_link_quality_event_.unused_afh_channel_count,
                  p_param_buf);
  STREAM_TO_UINT8(bqr_link_quality_event_.afh_select_unideal_channel_count,
                  p_param_buf);
  STREAM_TO_UINT16(bqr_link_quality_event_.lsto, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.connection_piconet_clock,
                   p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.retransmission_count, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.no_rx_count, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.nak_count, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.last_tx_ack_timestamp, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.flow_off_count, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.last_flow_on_timestamp, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.buffer_overflow_bytes, p_param_buf);
  STREAM_TO_UINT32(bqr_link_quality_event_.buffer_underflow_bytes, p_param_buf);

  if (vendor_cap_supported_version >= kBqrVersion5_0) {
    if (length < kLinkQualityParamTotalLen + kISOLinkQualityParamTotalLen +
                     kVersion5_0ParamsTotalLen) {
      log::warn(
          "Parameter total length: {} is abnormal. "
          "vendor_cap_supported_version: {}  (>= kBqrVersion5_0={}), It should "
          "not be shorter than: {}",
          length, vendor_cap_supported_version, kBqrVersion5_0,
          kLinkQualityParamTotalLen + kISOLinkQualityParamTotalLen +
              kVersion5_0ParamsTotalLen);
    } else {
      STREAM_TO_BDADDR(bqr_link_quality_event_.bdaddr, p_param_buf);
      STREAM_TO_UINT8(bqr_link_quality_event_.cal_failed_item_count,
                      p_param_buf);
    }
  }

  if (vendor_cap_supported_version >= kBqrIsoVersion) {
    if (length < kLinkQualityParamTotalLen + kISOLinkQualityParamTotalLen) {
      log::warn(
          "Parameter total length: {} is abnormal. "
          "vendor_cap_supported_version: {}  (>= kBqrIsoVersion={}), It should "
          "not be shorter than: {}",
          length, vendor_cap_supported_version, kBqrIsoVersion,
          kLinkQualityParamTotalLen + kISOLinkQualityParamTotalLen);
    } else {
      STREAM_TO_UINT32(bqr_link_quality_event_.tx_total_packets, p_param_buf);
      STREAM_TO_UINT32(bqr_link_quality_event_.tx_unacked_packets, p_param_buf);
      STREAM_TO_UINT32(bqr_link_quality_event_.tx_flushed_packets, p_param_buf);
      STREAM_TO_UINT32(bqr_link_quality_event_.tx_last_subevent_packets,
                       p_param_buf);
      STREAM_TO_UINT32(bqr_link_quality_event_.crc_error_packets, p_param_buf);
      STREAM_TO_UINT32(bqr_link_quality_event_.rx_duplicate_packets,
                       p_param_buf);
    }
  }

  const auto now = system_clock::to_time_t(system_clock::now());
  localtime_r(&now, &tm_timestamp_);
}

void BqrVseSubEvt::WriteLmpLlTraceLogFile(int fd, uint8_t length,
                                          const uint8_t* p_param_buf) {
  const auto now = system_clock::to_time_t(system_clock::now());
  localtime_r(&now, &tm_timestamp_);

  STREAM_TO_UINT8(bqr_log_dump_event_.quality_report_id, p_param_buf);
  STREAM_TO_UINT16(bqr_log_dump_event_.connection_handle, p_param_buf);
  length -= kLogDumpParamTotalLen;
  bqr_log_dump_event_.vendor_specific_parameter = p_param_buf;

  std::stringstream ss_log;
  ss_log << "\n"
         << std::put_time(&tm_timestamp_, "%m-%d %H:%M:%S ")
         << "Handle: " << loghex(bqr_log_dump_event_.connection_handle)
         << " VSP: ";

  TEMP_FAILURE_RETRY(write(fd, ss_log.str().c_str(), ss_log.str().size()));
  TEMP_FAILURE_RETRY(
      write(fd, bqr_log_dump_event_.vendor_specific_parameter, length));
  LmpLlMessageTraceCounter++;
}

void BqrVseSubEvt::WriteBtSchedulingTraceLogFile(int fd, uint8_t length,
                                                 const uint8_t* p_param_buf) {
  const auto now = system_clock::to_time_t(system_clock::now());
  localtime_r(&now, &tm_timestamp_);

  STREAM_TO_UINT8(bqr_log_dump_event_.quality_report_id, p_param_buf);
  STREAM_TO_UINT16(bqr_log_dump_event_.connection_handle, p_param_buf);
  length -= kLogDumpParamTotalLen;
  bqr_log_dump_event_.vendor_specific_parameter = p_param_buf;

  std::stringstream ss_log;
  ss_log << "\n"
         << std::put_time(&tm_timestamp_, "%m-%d %H:%M:%S ")
         << "Handle: " << loghex(bqr_log_dump_event_.connection_handle)
         << " VSP: ";

  TEMP_FAILURE_RETRY(write(fd, ss_log.str().c_str(), ss_log.str().size()));
  TEMP_FAILURE_RETRY(
      write(fd, bqr_log_dump_event_.vendor_specific_parameter, length));
  BtSchedulingTraceCounter++;
}

std::string BqrVseSubEvt::ToString() const {
  std::stringstream ss;
  ss << QualityReportIdToString(bqr_link_quality_event_.quality_report_id)
     << ", Handle: " << loghex(bqr_link_quality_event_.connection_handle)
     << ", " << PacketTypeToString(bqr_link_quality_event_.packet_types) << ", "
     << ((bqr_link_quality_event_.connection_role == 0) ? "Central"
                                                        : "Peripheral ")
     << ", PwLv: " << std::to_string(bqr_link_quality_event_.tx_power_level)
     << ", RSSI: " << std::to_string(bqr_link_quality_event_.rssi)
     << ", SNR: " << std::to_string(bqr_link_quality_event_.snr)
     << ", UnusedCh: "
     << std::to_string(bqr_link_quality_event_.unused_afh_channel_count)
     << ", UnidealCh: "
     << std::to_string(bqr_link_quality_event_.afh_select_unideal_channel_count)
     << ", ReTx: "
     << std::to_string(bqr_link_quality_event_.retransmission_count)
     << ", NoRX: " << std::to_string(bqr_link_quality_event_.no_rx_count)
     << ", NAK: " << std::to_string(bqr_link_quality_event_.nak_count)
     << ", FlowOff: " << std::to_string(bqr_link_quality_event_.flow_off_count)
     << ", OverFlow: "
     << std::to_string(bqr_link_quality_event_.buffer_overflow_bytes)
     << ", UndFlow: "
     << std::to_string(bqr_link_quality_event_.buffer_underflow_bytes);
  if (vendor_cap_supported_version >= kBqrVersion5_0) {
    ss << ", RemoteDevAddr: "
       << bqr_link_quality_event_.bdaddr.ToColonSepHexString()
       << ", CalFailedItems: "
       << std::to_string(bqr_link_quality_event_.cal_failed_item_count);
  }
  if (vendor_cap_supported_version >= kBqrIsoVersion) {
    ss << ", TxTotal: "
       << std::to_string(bqr_link_quality_event_.tx_total_packets)
       << ", TxUnAcked: "
       << std::to_string(bqr_link_quality_event_.tx_unacked_packets)
       << ", TxFlushed: "
       << std::to_string(bqr_link_quality_event_.tx_flushed_packets)
       << ", TxLastSubEvent: "
       << std::to_string(bqr_link_quality_event_.tx_last_subevent_packets)
       << ", CRCError: "
       << std::to_string(bqr_link_quality_event_.crc_error_packets)
       << ", RxDuplicate: "
       << std::to_string(bqr_link_quality_event_.rx_duplicate_packets);
  }

  return ss.str();
}

std::string QualityReportIdToString(uint8_t quality_report_id) {
  switch (quality_report_id) {
    case QUALITY_REPORT_ID_MONITOR_MODE:
      return "Monitoring";
    case QUALITY_REPORT_ID_APPROACH_LSTO:
      return "Approach LSTO";
    case QUALITY_REPORT_ID_A2DP_AUDIO_CHOPPY:
      return "A2DP Choppy";
    case QUALITY_REPORT_ID_SCO_VOICE_CHOPPY:
      return "SCO Choppy";
    case QUALITY_REPORT_ID_LE_AUDIO_CHOPPY:
      return "LE Audio Choppy";
    case QUALITY_REPORT_ID_CONNECT_FAIL:
      return "Connect Fail";
    default:
      return "Invalid";
  }
}

std::string PacketTypeToString(uint8_t packet_type) {
  switch (packet_type) {
    case PACKET_TYPE_ID:
      return "ID";
    case PACKET_TYPE_NULL:
      return "NULL";
    case PACKET_TYPE_POLL:
      return "POLL";
    case PACKET_TYPE_FHS:
      return "FHS";
    case PACKET_TYPE_HV1:
      return "HV1";
    case PACKET_TYPE_HV2:
      return "HV2";
    case PACKET_TYPE_HV3:
      return "HV3";
    case PACKET_TYPE_DV:
      return "DV";
    case PACKET_TYPE_EV3:
      return "EV3";
    case PACKET_TYPE_EV4:
      return "EV4";
    case PACKET_TYPE_EV5:
      return "EV5";
    case PACKET_TYPE_2EV3:
      return "2EV3";
    case PACKET_TYPE_2EV5:
      return "2EV5";
    case PACKET_TYPE_3EV3:
      return "3EV3";
    case PACKET_TYPE_3EV5:
      return "3EV5";
    case PACKET_TYPE_DM1:
      return "DM1";
    case PACKET_TYPE_DH1:
      return "DH1";
    case PACKET_TYPE_DM3:
      return "DM3";
    case PACKET_TYPE_DH3:
      return "DH3";
    case PACKET_TYPE_DM5:
      return "DM5";
    case PACKET_TYPE_DH5:
      return "DH5";
    case PACKET_TYPE_AUX1:
      return "AUX1";
    case PACKET_TYPE_2DH1:
      return "2DH1";
    case PACKET_TYPE_2DH3:
      return "2DH3";
    case PACKET_TYPE_2DH5:
      return "2DH5";
    case PACKET_TYPE_3DH1:
      return "3DH1";
    case PACKET_TYPE_3DH3:
      return "3DH3";
    case PACKET_TYPE_3DH5:
      return "3DH5";
    case PACKET_TYPE_ISO:
      return "ISO";
    default:
      return "UnKnown ";
  }
}

void EnableBtQualityReport(bool is_enable) {
  log::info("is_enable: {}", logbool(is_enable));

  char bqr_prop_evtmask[PROPERTY_VALUE_MAX] = {0};
  char bqr_prop_interval_ms[PROPERTY_VALUE_MAX] = {0};
  char bqr_prop_vnd_quality_mask[PROPERTY_VALUE_MAX] = {0};
  char bqr_prop_vnd_trace_mask[PROPERTY_VALUE_MAX] = {0};
  osi_property_get(kpPropertyEventMask, bqr_prop_evtmask, "");
  osi_property_get(kpPropertyMinReportIntervalMs, bqr_prop_interval_ms, "");
  osi_property_get(kpPropertyVndQualityMask, bqr_prop_vnd_quality_mask, "");
  osi_property_get(kpPropertyVndTraceMask, bqr_prop_vnd_trace_mask, "");

  if (strlen(bqr_prop_evtmask) == 0 || strlen(bqr_prop_interval_ms) == 0) {
    log::warn(
        "Bluetooth Quality Report is disabled. bqr_prop_evtmask: {}, "
        "bqr_prop_interval_ms: {}",
        bqr_prop_evtmask, bqr_prop_interval_ms);
    return;
  }

  BqrConfiguration bqr_config = {};

  if (is_enable) {
    bqr_config.report_action = REPORT_ACTION_ADD;
    bqr_config.quality_event_mask =
        static_cast<uint32_t>(atoi(bqr_prop_evtmask));
    bqr_config.minimum_report_interval_ms =
        static_cast<uint16_t>(atoi(bqr_prop_interval_ms));
    bqr_config.vnd_quality_mask =
        static_cast<uint32_t>(atoi(bqr_prop_vnd_quality_mask));
    bqr_config.vnd_trace_mask =
        static_cast<uint32_t>(atoi(bqr_prop_vnd_trace_mask));
  } else {
    bqr_config.report_action = REPORT_ACTION_CLEAR;
    bqr_config.quality_event_mask = kQualityEventMaskAllOff;
    bqr_config.minimum_report_interval_ms = kMinReportIntervalNoLimit;
    bqr_config.vnd_quality_mask = 0;
    bqr_config.vnd_trace_mask = 0;
  }

  tBTM_BLE_VSC_CB cmn_vsc_cb;
  BTM_BleGetVendorCapabilities(&cmn_vsc_cb);
  vendor_cap_supported_version = cmn_vsc_cb.version_supported;

  log::info("Event Mask: {}, Interval: {}, vendor_cap_supported_version: {}",
            loghex(bqr_config.quality_event_mask),
            bqr_config.minimum_report_interval_ms,
            vendor_cap_supported_version);
  ConfigureBqr(bqr_config);
}

void ConfigureBqr(const BqrConfiguration& bqr_config) {
  if (bqr_config.report_action > REPORT_ACTION_CLEAR ||
      bqr_config.quality_event_mask > kQualityEventMaskAll ||
      bqr_config.minimum_report_interval_ms > kMinReportIntervalMaxMs) {
    log::fatal("Invalid Parameter, Action: {}, Mask: {}, Interval: {}",
               bqr_config.report_action, loghex(bqr_config.quality_event_mask),
               bqr_config.minimum_report_interval_ms);
    return;
  }

  log::info("Action: {}, Mask: {}, Interval: {}",
            loghex(static_cast<uint8_t>(bqr_config.report_action)),
            loghex(bqr_config.quality_event_mask),
            bqr_config.minimum_report_interval_ms);

  uint8_t param[sizeof(BqrConfiguration)];
  uint8_t* p_param = param;
  UINT8_TO_STREAM(p_param, bqr_config.report_action);
  UINT32_TO_STREAM(p_param, bqr_config.quality_event_mask);
  UINT16_TO_STREAM(p_param, bqr_config.minimum_report_interval_ms);
  if (vendor_cap_supported_version >= kBqrVndLogVersion) {
    UINT32_TO_STREAM(p_param, bqr_config.vnd_quality_mask);
    UINT32_TO_STREAM(p_param, bqr_config.vnd_trace_mask);
  }

  BTM_VendorSpecificCommand(HCI_CONTROLLER_BQR, p_param - param, param,
                            BqrVscCompleteCallback);
}

void BqrVscCompleteCallback(tBTM_VSC_CMPL* p_vsc_cmpl_params) {
  if (p_vsc_cmpl_params->param_len < 1) {
    log::error("The length of returned parameters is less than 1");
    return;
  }

  uint8_t* p_event_param_buf = p_vsc_cmpl_params->p_param_buf;
  uint8_t status = 0xff;
  uint8_t command_complete_param_len = 5;
  uint32_t current_vnd_quality_mask = 0;
  uint32_t current_vnd_trace_mask = 0;
  // [Return Parameter]         | [Size]   | [Purpose]
  // Status                     | 1 octet  | Command complete status
  // Current_Quality_Event_Mask | 4 octets | Indicates current bit mask setting
  // Vendor_Specific_Quality_Mask | 4 octets | vendor quality bit mask setting
  // Vendor_Specific_Trace_Mask | 4 octets | vendor trace bit mask setting
  STREAM_TO_UINT8(status, p_event_param_buf);
  if (status != HCI_SUCCESS) {
    log::error("Fail to configure BQR. status: {}", loghex(status));
    return;
  }

  if (vendor_cap_supported_version >= kBqrVndLogVersion) {
    command_complete_param_len = 13;
  }

  if (p_vsc_cmpl_params->param_len != command_complete_param_len) {
    log::fatal("The length of returned parameters is incorrect: {}",
               p_vsc_cmpl_params->param_len);
    return;
  }

  uint32_t current_quality_event_mask = kQualityEventMaskAllOff;
  STREAM_TO_UINT32(current_quality_event_mask, p_event_param_buf);

  if (vendor_cap_supported_version >= kBqrVndLogVersion) {
    STREAM_TO_UINT32(current_vnd_quality_mask, p_event_param_buf);
    STREAM_TO_UINT32(current_vnd_trace_mask, p_event_param_buf);
  }

  log::info("current event mask: {}, vendor quality: {}, vendor trace: {}",
            loghex(current_quality_event_mask),
            loghex(current_vnd_quality_mask), loghex(current_vnd_trace_mask));

  ConfigureBqrCmpl(current_quality_event_mask);
}

void ConfigBqrA2dpScoThreshold() {
  uint8_t param[20] = {0};
  uint8_t sub_opcode = 0x16;
  uint8_t* p_param = param;
  uint16_t a2dp_choppy_threshold = 0;
  uint16_t sco_choppy_threshold = 0;

  char bqr_prop_threshold[PROPERTY_VALUE_MAX] = {0};
  osi_property_get(kpPropertyChoppyThreshold, bqr_prop_threshold, "");

  sscanf(bqr_prop_threshold, "%hu,%hu", &a2dp_choppy_threshold,
         &sco_choppy_threshold);

  log::warn("a2dp_choppy_threshold: {}, sco_choppy_threshold: {}",
            a2dp_choppy_threshold, sco_choppy_threshold);

  UINT8_TO_STREAM(p_param, sub_opcode);

  // A2dp glitch ID
  UINT8_TO_STREAM(p_param, QUALITY_REPORT_ID_A2DP_AUDIO_CHOPPY);
  // A2dp glitch config data length
  UINT8_TO_STREAM(p_param, 2);
  // A2dp glitch threshold
  UINT16_TO_STREAM(p_param,
                   a2dp_choppy_threshold == 0 ? 1 : a2dp_choppy_threshold);

  // Sco glitch ID
  UINT8_TO_STREAM(p_param, QUALITY_REPORT_ID_SCO_VOICE_CHOPPY);
  // Sco glitch config data length
  UINT8_TO_STREAM(p_param, 2);
  // Sco glitch threshold
  UINT16_TO_STREAM(p_param,
                   sco_choppy_threshold == 0 ? 1 : sco_choppy_threshold);

  BTM_VendorSpecificCommand(HCI_VS_HOST_LOG_OPCODE, p_param - param, param,
                            NULL);
}

void ConfigureBqrCmpl(uint32_t current_evt_mask) {
  log::info("current_evt_mask: {}", loghex(current_evt_mask));
  // (Un)Register for VSE of Bluetooth Quality Report sub event
  tBTM_STATUS btm_status = BTM_BT_Quality_Report_VSE_Register(
      current_evt_mask > kQualityEventMaskAllOff, CategorizeBqrEvent);

  bool isBqrEnabled =
      bluetooth::common::InitFlags::IsBluetoothQualityReportCallbackEnabled();
  if (isBqrEnabled && current_evt_mask > kQualityEventMaskAllOff) {
    ConfigBqrA2dpScoThreshold();
  }

  if (btm_status != BTM_SUCCESS) {
    log::error("Fail to (un)register VSE of BQR sub event. status: {}",
               btm_status);
    return;
  }

  if (LmpLlMessageTraceLogFd != INVALID_FD &&
      (current_evt_mask & kQualityEventMaskLmpMessageTrace) == 0) {
    log::info("Closing LMP/LL log file.");
    close(LmpLlMessageTraceLogFd);
    LmpLlMessageTraceLogFd = INVALID_FD;
  }
  if (BtSchedulingTraceLogFd != INVALID_FD &&
      (current_evt_mask & kQualityEventMaskBtSchedulingTrace) == 0) {
    log::info("Closing Scheduling log file.");
    close(BtSchedulingTraceLogFd);
    BtSchedulingTraceLogFd = INVALID_FD;
  }
}

void CategorizeBqrEvent(uint8_t length, const uint8_t* p_bqr_event) {
  if (length == 0) {
    log::warn("Lengths of all of the parameters are zero.");
    return;
  }

  uint8_t quality_report_id = p_bqr_event[0];
  switch (quality_report_id) {
    case QUALITY_REPORT_ID_MONITOR_MODE:
    case QUALITY_REPORT_ID_APPROACH_LSTO:
    case QUALITY_REPORT_ID_A2DP_AUDIO_CHOPPY:
    case QUALITY_REPORT_ID_SCO_VOICE_CHOPPY:
    case QUALITY_REPORT_ID_LE_AUDIO_CHOPPY:
    case QUALITY_REPORT_ID_CONNECT_FAIL:
      if (length < kLinkQualityParamTotalLen) {
        log::fatal(
            "Parameter total length: {} is abnormal. It shall be not shorter "
            "than: {}",
            length, kLinkQualityParamTotalLen);
        return;
      }

      AddLinkQualityEventToQueue(length, p_bqr_event);
      break;

    // The Root Inflammation and Log Dump related event should be handled and
    // intercepted already.
    case QUALITY_REPORT_ID_VENDOR_SPECIFIC_QUALITY:
    case QUALITY_REPORT_ID_ROOT_INFLAMMATION:
    case QUALITY_REPORT_ID_LMP_LL_MESSAGE_TRACE:
    case QUALITY_REPORT_ID_BT_SCHEDULING_TRACE:
    case QUALITY_REPORT_ID_CONTROLLER_DBG_INFO:
    case QUALITY_REPORT_ID_VENDOR_SPECIFIC_TRACE:
      log::warn("Unexpected ID: {}", loghex(quality_report_id));
      break;

    default:
      log::warn("Unknown ID: {}", loghex(quality_report_id));
      break;
  }
}

void AddLinkQualityEventToQueue(uint8_t length,
                                const uint8_t* p_link_quality_event) {
  std::unique_ptr<BqrVseSubEvt> p_bqr_event = std::make_unique<BqrVseSubEvt>();
  RawAddress bd_addr;

  p_bqr_event->ParseBqrLinkQualityEvt(length, p_link_quality_event);

  log::warn("{}", *p_bqr_event);
  GetInterfaceToProfiles()->events->invoke_link_quality_report_cb(
      bluetooth::common::time_get_os_boottime_ms(),
      p_bqr_event->bqr_link_quality_event_.quality_report_id,
      p_bqr_event->bqr_link_quality_event_.rssi,
      p_bqr_event->bqr_link_quality_event_.snr,
      p_bqr_event->bqr_link_quality_event_.retransmission_count,
      p_bqr_event->bqr_link_quality_event_.no_rx_count,
      p_bqr_event->bqr_link_quality_event_.nak_count);

#ifdef __ANDROID__
  int ret = stats_write(
      BLUETOOTH_QUALITY_REPORT_REPORTED,
      p_bqr_event->bqr_link_quality_event_.quality_report_id,
      p_bqr_event->bqr_link_quality_event_.packet_types,
      p_bqr_event->bqr_link_quality_event_.connection_handle,
      p_bqr_event->bqr_link_quality_event_.connection_role,
      p_bqr_event->bqr_link_quality_event_.tx_power_level,
      p_bqr_event->bqr_link_quality_event_.rssi,
      p_bqr_event->bqr_link_quality_event_.snr,
      p_bqr_event->bqr_link_quality_event_.unused_afh_channel_count,
      p_bqr_event->bqr_link_quality_event_.afh_select_unideal_channel_count,
      p_bqr_event->bqr_link_quality_event_.lsto,
      p_bqr_event->bqr_link_quality_event_.connection_piconet_clock,
      p_bqr_event->bqr_link_quality_event_.retransmission_count,
      p_bqr_event->bqr_link_quality_event_.no_rx_count,
      p_bqr_event->bqr_link_quality_event_.nak_count,
      p_bqr_event->bqr_link_quality_event_.last_tx_ack_timestamp,
      p_bqr_event->bqr_link_quality_event_.flow_off_count,
      p_bqr_event->bqr_link_quality_event_.last_flow_on_timestamp,
      p_bqr_event->bqr_link_quality_event_.buffer_overflow_bytes,
      p_bqr_event->bqr_link_quality_event_.buffer_underflow_bytes);
  if (ret < 0) {
    log::warn("failed to log BQR event to statsd, error {}", ret);
  }
#else
  // TODO(abps) Metrics for non-Android build
#endif
  bool isBqrEnabled =
      bluetooth::common::InitFlags::IsBluetoothQualityReportCallbackEnabled();
  if (isBqrEnabled) {
    BluetoothQualityReportInterface* bqrItf =
        getBluetoothQualityReportInterface();

    if (bqrItf != NULL) {
      bd_addr = p_bqr_event->bqr_link_quality_event_.bdaddr;
      if (bd_addr.IsEmpty()) {
        tBTM_SEC_DEV_REC* dev = btm_find_dev_by_handle(
            p_bqr_event->bqr_link_quality_event_.connection_handle);
        if (dev != NULL) {
          bd_addr = dev->RemoteAddress();
        }
      }

      if (!bd_addr.IsEmpty()) {
        bqrItf->bqr_delivery_event(bd_addr, (uint8_t*)p_link_quality_event,
                                   length);
      } else {
        log::warn("failed to deliver BQR, bdaddr is empty");
      }
    } else {
      log::warn("failed to deliver BQR, bqrItf is NULL");
    }
  }

  kpBqrEventQueue->Enqueue(p_bqr_event.release());
}

void DumpLmpLlMessage(uint8_t length, const uint8_t* p_lmp_ll_message_event) {
  std::unique_ptr<BqrVseSubEvt> p_bqr_event = std::make_unique<BqrVseSubEvt>();

  if (LmpLlMessageTraceLogFd == INVALID_FD ||
      LmpLlMessageTraceCounter >= kLogDumpEventPerFile) {
    LmpLlMessageTraceLogFd = OpenLmpLlTraceLogFile();
  }
  if (LmpLlMessageTraceLogFd != INVALID_FD) {
    p_bqr_event->WriteLmpLlTraceLogFile(LmpLlMessageTraceLogFd, length,
                                        p_lmp_ll_message_event);
  }
}

int OpenLmpLlTraceLogFile() {
  if (rename(kpLmpLlMessageTraceLogPath, kpLmpLlMessageTraceLastLogPath) != 0 &&
      errno != ENOENT) {
    log::error("Unable to rename '{}' to '{}' : {}", kpLmpLlMessageTraceLogPath,
               kpLmpLlMessageTraceLastLogPath, strerror(errno));
  }

  mode_t prevmask = umask(0);
  int logfile_fd =
      open(kpLmpLlMessageTraceLogPath, O_WRONLY | O_CREAT | O_TRUNC,
           S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH);
  umask(prevmask);
  if (logfile_fd == INVALID_FD) {
    log::error("Unable to open '{}' : {}", kpLmpLlMessageTraceLogPath,
               strerror(errno));
  } else {
    LmpLlMessageTraceCounter = 0;
  }
  return logfile_fd;
}

void DumpBtScheduling(uint8_t length, const uint8_t* p_bt_scheduling_event) {
  std::unique_ptr<BqrVseSubEvt> p_bqr_event = std::make_unique<BqrVseSubEvt>();

  if (BtSchedulingTraceLogFd == INVALID_FD ||
      BtSchedulingTraceCounter == kLogDumpEventPerFile) {
    BtSchedulingTraceLogFd = OpenBtSchedulingTraceLogFile();
  }
  if (BtSchedulingTraceLogFd != INVALID_FD) {
    p_bqr_event->WriteBtSchedulingTraceLogFile(BtSchedulingTraceLogFd, length,
                                               p_bt_scheduling_event);
  }
}

int OpenBtSchedulingTraceLogFile() {
  if (rename(kpBtSchedulingTraceLogPath, kpBtSchedulingTraceLastLogPath) != 0 &&
      errno != ENOENT) {
    log::error("Unable to rename '{}' to '{}' : {}", kpBtSchedulingTraceLogPath,
               kpBtSchedulingTraceLastLogPath, strerror(errno));
  }

  mode_t prevmask = umask(0);
  int logfile_fd =
      open(kpBtSchedulingTraceLogPath, O_WRONLY | O_CREAT | O_TRUNC,
           S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH);
  umask(prevmask);
  if (logfile_fd == INVALID_FD) {
    log::error("Unable to open '{}' : {}", kpBtSchedulingTraceLogPath,
               strerror(errno));
  } else {
    BtSchedulingTraceCounter = 0;
  }
  return logfile_fd;
}

void DebugDump(int fd) {
  dprintf(fd, "\nBT Quality Report Events: \n");

  if (kpBqrEventQueue->Empty()) {
    dprintf(fd, "Event queue is empty.\n");
    return;
  }

  while (!kpBqrEventQueue->Empty()) {
    std::unique_ptr<BqrVseSubEvt> p_event(kpBqrEventQueue->Dequeue());

    bool warning = (p_event->bqr_link_quality_event_.rssi < kCriWarnRssi ||
                    p_event->bqr_link_quality_event_.unused_afh_channel_count >
                        kCriWarnUnusedCh);

    std::stringstream ss_timestamp;
    ss_timestamp << std::put_time(&p_event->tm_timestamp_, "%m-%d %H:%M:%S");

    dprintf(fd, "%c  %s %s\n", warning ? '*' : ' ', ss_timestamp.str().c_str(),
            p_event->ToString().c_str());
  }

  dprintf(fd, "\n");
}

static void btif_get_remote_version(const RawAddress& bd_addr,
                                    uint8_t& lmp_version,
                                    uint16_t& manufacturer,
                                    uint16_t& lmp_sub_version) {
  bt_property_t prop;
  bt_remote_version_t info;
  uint8_t tmp_lmp_ver = 0;
  uint16_t tmp_manufacturer = 0;
  uint16_t tmp_lmp_subver = 0;
  tBTM_STATUS status;

  status = BTM_ReadRemoteVersion(bd_addr, &tmp_lmp_ver, &tmp_manufacturer,
                                 &tmp_lmp_subver);
  if (status == BTM_SUCCESS &&
      (tmp_lmp_ver || tmp_manufacturer || tmp_lmp_subver)) {
    lmp_version = tmp_lmp_ver;
    manufacturer = tmp_manufacturer;
    lmp_sub_version = tmp_lmp_subver;
    return;
  }

  prop.type = BT_PROPERTY_REMOTE_VERSION_INFO;
  prop.len = sizeof(bt_remote_version_t);
  prop.val = (void*)&info;

  if (btif_storage_get_remote_device_property(&bd_addr, &prop) ==
      BT_STATUS_SUCCESS) {
    lmp_version = (uint8_t)info.version;
    manufacturer = (uint16_t)info.manufacturer;
    lmp_sub_version = (uint16_t)info.sub_ver;
  }
}

class BluetoothQualityReportInterfaceImpl
    : public bluetooth::bqr::BluetoothQualityReportInterface {
  ~BluetoothQualityReportInterfaceImpl() override = default;

  void init(BluetoothQualityReportCallbacks* callbacks) override {
    log::info("BluetoothQualityReportInterfaceImpl");
    this->callbacks = callbacks;
  }

  void bqr_delivery_event(const RawAddress& bd_addr,
                          const uint8_t* bqr_raw_data,
                          uint32_t bqr_raw_data_len) override {
    if (bqr_raw_data == NULL) {
      log::error("bqr data is null");
      return;
    }

    std::vector<uint8_t> raw_data;
    raw_data.insert(raw_data.begin(), bqr_raw_data,
                    bqr_raw_data + bqr_raw_data_len);

    if (vendor_cap_supported_version < kBqrVersion5_0 &&
        bqr_raw_data_len <
            kLinkQualityParamTotalLen + kVersion5_0ParamsTotalLen) {
      std::vector<uint8_t>::iterator it =
          raw_data.begin() + kLinkQualityParamTotalLen;
      /**
       * Insert zeros as remote address and calibration count
       * for BQR 5.0 incompatible devices
       */
      raw_data.insert(it, kVersion5_0ParamsTotalLen, 0);
    }

    uint8_t lmp_ver = 0;
    uint16_t lmp_subver = 0;
    uint16_t manufacturer_id = 0;
    btif_get_remote_version(bd_addr, lmp_ver, manufacturer_id, lmp_subver);

    log::info(
        "len: {}, addr: {}, lmp_ver: {}, manufacturer_id: {}, lmp_subver: {}",
        bqr_raw_data_len, ADDRESS_TO_LOGGABLE_CSTR(bd_addr), lmp_ver,
        manufacturer_id, lmp_subver);

    if (callbacks == nullptr) {
      log::error("callbacks is nullptr");
      return;
    }

    do_in_jni_thread(
        FROM_HERE,
        base::BindOnce(&bluetooth::bqr::BluetoothQualityReportCallbacks::
                           bqr_delivery_callback,
                       base::Unretained(callbacks), bd_addr, lmp_ver,
                       lmp_subver, manufacturer_id, std::move(raw_data)));
  }

 private:
  BluetoothQualityReportCallbacks* callbacks = nullptr;
};

BluetoothQualityReportInterface* getBluetoothQualityReportInterface() {
  if (!bluetoothQualityReportInstance) {
    bluetoothQualityReportInstance.reset(
        new BluetoothQualityReportInterfaceImpl());
  }

  return bluetoothQualityReportInstance.get();
}

}  // namespace bqr
}  // namespace bluetooth
