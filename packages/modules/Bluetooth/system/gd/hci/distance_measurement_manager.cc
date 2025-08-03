/*
 * Copyright 2022 The Android Open Source Project
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
#include "hci/distance_measurement_manager.h"

#include <android_bluetooth_flags.h>
#include <math.h>

#include <complex>
#include <unordered_map>

#include "common/strings.h"
#include "hci/acl_manager.h"
#include "hci/distance_measurement_interface.h"
#include "hci/event_checkers.h"
#include "hci/hci_layer.h"
#include "module.h"
#include "os/handler.h"
#include "os/log.h"
#include "os/repeating_alarm.h"
#include "packet/packet_view.h"

namespace bluetooth {
namespace hci {

const ModuleFactory DistanceMeasurementManager::Factory =
    ModuleFactory([]() { return new DistanceMeasurementManager(); });
static constexpr uint16_t kIllegalConnectionHandle = 0xffff;
static constexpr uint8_t kTxPowerNotAvailable = 0xfe;
static constexpr int8_t kRSSIDropOffAt1M = 41;
static constexpr uint8_t kCsMaxTxPower = 12;  // 12 dBm
static constexpr CsSyncAntennaSelection kCsSyncAntennaSelection = CsSyncAntennaSelection::ANTENNA_2;
static constexpr uint8_t kConfigId = 0x01;  // Use 0x01 to create config and enable procedure
static constexpr uint8_t kMinMainModeSteps = 0x02;
static constexpr uint8_t kMaxMainModeSteps = 0x05;
static constexpr uint8_t kMainModeRepetition = 0x00;  // No repetition
static constexpr uint8_t kMode0Steps =
    0x03;  // Maximum number of mode-0 steps to increase success subevent rate
static constexpr uint8_t kChannelMapRepetition = 0x01;  // No repetition
static constexpr uint8_t kCh3cJump = 0x03;              // Skip 3 Channels
static constexpr uint16_t kMaxProcedureLen = 0xFFFF;    // 40.959375s
static constexpr uint16_t kMinProcedureInterval = 0x01;
static constexpr uint16_t kMaxProcedureInterval = 0xFF;
static constexpr uint16_t kMaxProcedureCount = 0x01;
static constexpr uint32_t kMinSubeventLen = 0x0004E2;         // 1250us
static constexpr uint32_t kMaxSubeventLen = 0x3d0900;         // 4s
static constexpr uint8_t kToneAntennaConfigSelection = 0x07;  // 2x2
static constexpr uint8_t kTxPwrDelta = 0x00;
static constexpr uint8_t kProcedureDataBufferSize = 0x10;  // Buffer size of Procedure data
static constexpr uint8_t kReportWithNoAbort = 0x00;

struct DistanceMeasurementManager::impl {
  struct CsProcedureData {
    CsProcedureData(uint16_t procedure_counter, uint8_t num_antenna_paths)
        : counter(procedure_counter), num_antenna_paths(num_antenna_paths) {
      local_status = CsProcedureDoneStatus::PARTIAL_RESULTS;
      remote_status = CsProcedureDoneStatus::PARTIAL_RESULTS;
      // In ascending order of antenna position with tone extension data at the end
      uint16_t num_tone_data = num_antenna_paths + 1;
      for (uint8_t i = 0; i < num_tone_data; i++) {
        std::vector<std::complex<double>> empty_complex_vector;
        tone_pct_initiator.push_back(empty_complex_vector);
        tone_pct_reflector.push_back(empty_complex_vector);
        std::vector<uint8_t> empty_vector;
        tone_quality_indicator_initiator.push_back(empty_vector);
        tone_quality_indicator_reflector.push_back(empty_vector);
      }
    }
    // Procedure counter
    uint16_t counter;
    // Number of antenna paths (1 to 4) reported in the procedure
    uint8_t num_antenna_paths;
    // Frequency Compensation indicates fractional frequency offset (FFO) value of initiator, in
    // 0.01ppm
    std::vector<uint16_t> frequency_compensation;
    // The channel indices of every step in a CS procedure (in time order)
    std::vector<uint8_t> step_channel;
    // Measured Frequency Offset from mode 0, relative to the remote device, in 0.01ppm
    std::vector<uint16_t> measured_freq_offset;
    // Initiator's PCT (complex value) measured from mode-2 or mode-3 steps in a CS procedure (in
    // time order)
    std::vector<std::vector<std::complex<double>>> tone_pct_initiator;
    // Reflector's PCT (complex value) measured from mode-2 or mode-3 steps in a CS procedure (in
    // time order)
    std::vector<std::vector<std::complex<double>>> tone_pct_reflector;
    std::vector<std::vector<uint8_t>> tone_quality_indicator_initiator;
    std::vector<std::vector<uint8_t>> tone_quality_indicator_reflector;
    CsProcedureDoneStatus local_status;
    CsProcedureDoneStatus remote_status;
    // If the procedure is aborted by either the local or remote side.
    bool aborted = false;
  };

  ~impl() {}
  void start(os::Handler* handler, hci::HciLayer* hci_layer, hci::AclManager* acl_manager) {
    handler_ = handler;
    hci_layer_ = hci_layer;
    acl_manager_ = acl_manager;
    hci_layer_->RegisterLeEventHandler(
        hci::SubeventCode::TRANSMIT_POWER_REPORTING,
        handler_->BindOn(this, &impl::on_transmit_power_reporting));
    if (!IS_FLAG_ENABLED(channel_sounding_in_stack)) {
      LOG_INFO("IS_FLAG_ENABLED channel_sounding_in_stack: false");
      return;
    }
    distance_measurement_interface_ = hci_layer_->GetDistanceMeasurementInterface(
        handler_->BindOn(this, &DistanceMeasurementManager::impl::handle_event));
    distance_measurement_interface_->EnqueueCommand(
        LeCsReadLocalSupportedCapabilitiesBuilder::Create(),
        handler_->BindOnceOn(this, &impl::on_cs_read_local_supported_capabilities));
  }

  void stop() {
    hci_layer_->UnregisterLeEventHandler(hci::SubeventCode::TRANSMIT_POWER_REPORTING);
  }

  void register_distance_measurement_callbacks(DistanceMeasurementCallbacks* callbacks) {
    distance_measurement_callbacks_ = callbacks;
  }

  void start_distance_measurement(
      const Address& address, uint16_t interval, DistanceMeasurementMethod method) {
    LOG_INFO("Address:%s, method:%d", ADDRESS_TO_LOGGABLE_CSTR(address), method);
    uint16_t connection_handle = acl_manager_->HACK_GetLeHandle(address);

    // Remove this check if we support any connection less method
    if (connection_handle == kIllegalConnectionHandle) {
      LOG_WARN("Can't find any LE connection for %s", ADDRESS_TO_LOGGABLE_CSTR(address));
      distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
          address, REASON_NO_LE_CONNECTION, method);
      return;
    }

    switch (method) {
      case METHOD_AUTO:
      case METHOD_RSSI: {
        if (rssi_trackers.find(address) == rssi_trackers.end()) {
          rssi_trackers[address].handle = connection_handle;
          rssi_trackers[address].interval_ms = interval;
          rssi_trackers[address].remote_tx_power = kTxPowerNotAvailable;
          rssi_trackers[address].started = false;
          rssi_trackers[address].repeating_alarm = std::make_unique<os::RepeatingAlarm>(handler_);
          hci_layer_->EnqueueCommand(
              LeReadRemoteTransmitPowerLevelBuilder::Create(
                  acl_manager_->HACK_GetLeHandle(address), 0x01),
              handler_->BindOnceOn(
                  this, &impl::on_read_remote_transmit_power_level_status, address));
        } else {
          rssi_trackers[address].interval_ms = interval;
        }
      } break;
      case METHOD_CS: {
        start_distance_measurement_with_cs(address, connection_handle, interval);
      } break;
    }
  }

  void start_distance_measurement_with_cs(
      const Address& cs_remote_address, uint16_t connection_handle, uint16_t interval) {
    LOG_INFO(
        "connection_handle: %d, address: %s",
        connection_handle,
        ADDRESS_TO_LOGGABLE_CSTR(cs_remote_address));
    if (!IS_FLAG_ENABLED(channel_sounding_in_stack)) {
      LOG_ERROR("Channel Sounding is not enabled");
      distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
          cs_remote_address, REASON_INTERNAL_ERROR, METHOD_CS);
      return;
    }

    if (cs_trackers_.find(connection_handle) != cs_trackers_.end() &&
        cs_trackers_[connection_handle].address != cs_remote_address) {
      LOG_WARN("Remove old tracker for %s ", ADDRESS_TO_LOGGABLE_CSTR(cs_remote_address));
      cs_trackers_.erase(connection_handle);
    }

    if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
      // Create a cs tracker with role initiator
      cs_trackers_[connection_handle].address = cs_remote_address;
      // TODO: Check ROLE via CS config. (b/304295768)
      cs_trackers_[connection_handle].role = CsRole::INITIATOR;
      cs_trackers_[connection_handle].repeating_alarm =
          std::make_unique<os::RepeatingAlarm>(handler_);
    }
    cs_trackers_[connection_handle].interval_ms = interval;
    cs_trackers_[connection_handle].waiting_for_start_callback = true;

    if (!cs_trackers_[connection_handle].setup_complete) {
      send_le_cs_read_remote_supported_capabilities(connection_handle);
      return;
    }
    if (!cs_trackers_[connection_handle].config_set) {
      send_le_cs_create_config(connection_handle);
      return;
    }
    LOG_INFO(
        "enable cs procedure regularly with interval: %d ms",
        cs_trackers_[connection_handle].interval_ms);
    cs_trackers_[connection_handle].repeating_alarm->Cancel();
    send_le_cs_procedure_enable(connection_handle, Enable::ENABLED);
    cs_trackers_[connection_handle].repeating_alarm->Schedule(
        common::Bind(
            &impl::send_le_cs_procedure_enable,
            common::Unretained(this),
            connection_handle,
            Enable::ENABLED),
        std::chrono::milliseconds(cs_trackers_[connection_handle].interval_ms));
  }

  void stop_distance_measurement(const Address& address, DistanceMeasurementMethod method) {
    LOG_INFO("Address:%s, method:%d", ADDRESS_TO_LOGGABLE_CSTR(address), method);
    switch (method) {
      case METHOD_AUTO:
      case METHOD_RSSI: {
        if (rssi_trackers.find(address) == rssi_trackers.end()) {
          LOG_WARN("Can't find rssi tracker for %s ", ADDRESS_TO_LOGGABLE_CSTR(address));
        } else {
          hci_layer_->EnqueueCommand(
              LeSetTransmitPowerReportingEnableBuilder::Create(
                  rssi_trackers[address].handle, 0x00, 0x00),
              handler_->BindOnce(check_complete<LeSetTransmitPowerReportingEnableCompleteView>));
          rssi_trackers[address].repeating_alarm->Cancel();
          rssi_trackers[address].repeating_alarm.reset();
          rssi_trackers.erase(address);
        }
      } break;
      case METHOD_CS: {
        uint16_t connection_handle = acl_manager_->HACK_GetLeHandle(address);
        if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
          LOG_WARN("Can't find CS tracker for %s ", ADDRESS_TO_LOGGABLE_CSTR(address));
        } else {
          cs_trackers_[connection_handle].repeating_alarm->Cancel();
          cs_trackers_[connection_handle].repeating_alarm.reset();
          send_le_cs_procedure_enable(connection_handle, Enable::DISABLED);
          cs_trackers_.erase(connection_handle);
        }
      } break;
    }
  }

  void send_read_rssi(const Address& address) {
    if (rssi_trackers.find(address) == rssi_trackers.end()) {
      LOG_WARN("Can't find rssi tracker for %s ", ADDRESS_TO_LOGGABLE_CSTR(address));
      return;
    }
    uint16_t connection_handle = acl_manager_->HACK_GetLeHandle(address);
    if (connection_handle == kIllegalConnectionHandle) {
      LOG_WARN("Can't find connection for %s ", ADDRESS_TO_LOGGABLE_CSTR(address));
      if (rssi_trackers.find(address) != rssi_trackers.end()) {
        distance_measurement_callbacks_->OnDistanceMeasurementStopped(
            address, REASON_NO_LE_CONNECTION, METHOD_RSSI);
        rssi_trackers[address].repeating_alarm->Cancel();
        rssi_trackers[address].repeating_alarm.reset();
        rssi_trackers.erase(address);
      }
      return;
    }

    hci_layer_->EnqueueCommand(
        ReadRssiBuilder::Create(connection_handle),
        handler_->BindOnceOn(this, &impl::on_read_rssi_complete, address));
  }

  void handle_event(LeMetaEventView event) {
    if (!event.IsValid()) {
      LOG_ERROR("Received invalid LeMetaEventView");
      return;
    }
    switch (event.GetSubeventCode()) {
      case hci::SubeventCode::LE_CS_TEST_END_COMPLETE:
      case hci::SubeventCode::LE_CS_READ_REMOTE_FAE_TABLE_COMPLETE: {
        LOG_WARN("Unhandled subevent %s", hci::SubeventCodeText(event.GetSubeventCode()).c_str());
      } break;
      case hci::SubeventCode::LE_CS_SUBEVENT_RESULT_CONTINUE:
      case hci::SubeventCode::LE_CS_SUBEVENT_RESULT: {
        on_cs_subevent(event);
      } break;
      case hci::SubeventCode::LE_CS_PROCEDURE_ENABLE_COMPLETE: {
        on_cs_procedure_enable_complete(LeCsProcedureEnableCompleteView::Create(event));
      } break;
      case hci::SubeventCode::LE_CS_CONFIG_COMPLETE: {
        on_cs_config_complete(LeCsConfigCompleteView::Create(event));
      } break;
      case hci::SubeventCode::LE_CS_SECURITY_ENABLE_COMPLETE: {
        on_cs_security_enable_complete(LeCsSecurityEnableCompleteView::Create(event));
      } break;
      case hci::SubeventCode::LE_CS_READ_REMOTE_SUPPORTED_CAPABILITIES_COMPLETE: {
        on_cs_read_remote_supported_capabilities_complete(
            LeCsReadRemoteSupportedCapabilitiesCompleteView::Create(event));
      } break;
      default:
        LOG_INFO("Unknown subevent %s", hci::SubeventCodeText(event.GetSubeventCode()).c_str());
    }
  }

  void send_le_cs_read_remote_supported_capabilities(uint16_t connection_handle) {
    hci_layer_->EnqueueCommand(
        LeCsReadRemoteSupportedCapabilitiesBuilder::Create(connection_handle),
        handler_->BindOnce(check_status<LeCsReadRemoteSupportedCapabilitiesStatusView>));
  }

  void send_le_cs_security_enable(uint16_t connection_handle) {
    hci_layer_->EnqueueCommand(
        LeCsSecurityEnableBuilder::Create(connection_handle),
        handler_->BindOnce(check_status<LeCsSecurityEnableStatusView>));
  }

  void send_le_cs_set_default_settings(uint16_t connection_handle) {
    uint8_t role_enable = (1 << (uint8_t)CsRole::INITIATOR) | 1 << ((uint8_t)CsRole::REFLECTOR);
    hci_layer_->EnqueueCommand(
        LeCsSetDefaultSettingsBuilder::Create(
            connection_handle,
            role_enable,
            kCsSyncAntennaSelection,
            kCsMaxTxPower  // max_tx_power
            ),
        handler_->BindOnceOn(this, &impl::on_cs_set_default_settings_complete));
  }

  void send_le_cs_create_config(uint16_t connection_handle) {
    auto channel_vector = common::FromHexString("1FFFFFFFFFFFFC7FFFFC");  // use all 72 Channel
    std::array<uint8_t, 10> channel_map;
    std::copy(channel_vector->begin(), channel_vector->end(), channel_map.begin());
    std::reverse(channel_map.begin(), channel_map.end());
    hci_layer_->EnqueueCommand(
        LeCsCreateConfigBuilder::Create(
            connection_handle,
            kConfigId,
            CsCreateContext::BOTH_LOCAL_AND_REMOTE_CONTROLLER,
            CsMainModeType::MODE_2,
            CsSubModeType::UNUSED,
            kMinMainModeSteps,
            kMaxMainModeSteps,
            kMainModeRepetition,
            kMode0Steps,
            CsRole::INITIATOR,
            CsConfigRttType::RTT_WITH_128_BIT_RANDOM_SEQUENCE,
            CsSyncPhy::LE_1M_PHY,
            channel_map,
            kChannelMapRepetition,
            CsChannelSelectionType::TYPE_3B,
            CsCh3cShape::HAT_SHAPE,
            kCh3cJump,
            Enable::DISABLED),
        handler_->BindOnce(check_status<LeCsCreateConfigStatusView>));
  }

  void send_le_cs_set_procedure_parameters(uint16_t connection_handle) {
    CsPreferredPeerAntenna preferred_peer_antenna;
    hci_layer_->EnqueueCommand(
        LeCsSetProcedureParametersBuilder::Create(
            connection_handle,
            kConfigId,
            kMaxProcedureLen,
            kMinProcedureInterval,
            kMaxProcedureInterval,
            kMaxProcedureCount,
            kMinSubeventLen,
            kMaxSubeventLen,
            kToneAntennaConfigSelection,
            CsPhy::LE_1M_PHY,
            kTxPwrDelta,
            preferred_peer_antenna),
        handler_->BindOnceOn(this, &impl::on_cs_set_procedure_parameters));
  }

  void send_le_cs_procedure_enable(uint16_t connection_handle, Enable enable) {
    if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
      LOG_WARN("Can't find cs tracker for connection %d", connection_handle);
      return;
    }
    Address address = cs_trackers_[connection_handle].address;
    // Check if the connection still exists
    uint16_t connection_handle_from_acl_manager = acl_manager_->HACK_GetLeHandle(address);
    if (connection_handle_from_acl_manager == kIllegalConnectionHandle) {
      LOG_WARN("Can't find connection for %s ", ADDRESS_TO_LOGGABLE_CSTR(address));
      distance_measurement_callbacks_->OnDistanceMeasurementStopped(
          address, REASON_NO_LE_CONNECTION, METHOD_CS);
      cs_trackers_[connection_handle].repeating_alarm->Cancel();
      cs_trackers_[connection_handle].repeating_alarm.reset();
      cs_trackers_.erase(connection_handle);
      return;
    }
    hci_layer_->EnqueueCommand(
        LeCsProcedureEnableBuilder::Create(connection_handle, kConfigId, enable),
        handler_->BindOnce(check_status<LeCsProcedureEnableStatusView>));
  }

  void on_cs_read_local_supported_capabilities(CommandCompleteView view) {
    auto complete_view = LeCsReadLocalSupportedCapabilitiesCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_WARN("Get invalid LeCsReadLocalSupportedCapabilitiesComplete");
      is_channel_sounding_supported_ = false;
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(complete_view.GetStatus());
      LOG_WARN(
          "Received LeCsReadLocalSupportedCapabilitiesComplete with error code %s",
          error_code.c_str());
      is_channel_sounding_supported_ = false;
      return;
    }
    is_channel_sounding_supported_ = true;
    cs_subfeature_supported_ = complete_view.GetOptionalSubfeaturesSupported();
  }

  void on_cs_read_remote_supported_capabilities_complete(
      LeCsReadRemoteSupportedCapabilitiesCompleteView event_view) {
    if (!event_view.IsValid()) {
      LOG_WARN("Get invalid LeCsReadRemoteSupportedCapabilitiesCompleteView");
      return;
    } else if (event_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(event_view.GetStatus());
      LOG_WARN(
          "Received LeCsReadRemoteSupportedCapabilitiesCompleteView with error code %s",
          error_code.c_str());
      return;
    }
    uint16_t connection_handle = event_view.GetConnectionHandle();
    send_le_cs_set_default_settings(event_view.GetConnectionHandle());
    if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
      // Create a cs tracker with role reflector
      // TODO: Check ROLE via CS config. (b/304295768)
      cs_trackers_[connection_handle].role = CsRole::REFLECTOR;
    } else {
      send_le_cs_security_enable(connection_handle);
    }

    if (event_view.GetOptionalSubfeaturesSupported().phase_based_ranging_ == 0x01) {
      cs_trackers_[connection_handle].remote_support_phase_based_ranging = true;
    }
    LOG_INFO(
        "connection_handle:%d, num_antennas_supported:%d, max_antenna_paths_supported:%d, "
        "roles_supported:%s, phase_based_ranging_supported: %d ",
        event_view.GetConnectionHandle(),
        event_view.GetNumAntennasSupported(),
        event_view.GetMaxAntennaPathsSupported(),
        event_view.GetRolesSupported().ToString().c_str(),
        event_view.GetOptionalSubfeaturesSupported().phase_based_ranging_);
  }

  void on_cs_set_default_settings_complete(CommandCompleteView view) {
    auto complete_view = LeCsSetDefaultSettingsCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_WARN("Get invalid LeCsSetDefaultSettingsComplete");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(complete_view.GetStatus());
      LOG_WARN("Received LeCsSetDefaultSettingsComplete with error code %s", error_code.c_str());
      return;
    }
  }

  void on_cs_security_enable_complete(LeCsSecurityEnableCompleteView event_view) {
    if (!event_view.IsValid()) {
      LOG_WARN("Get invalid LeCsSecurityEnableCompleteView");
      return;
    } else if (event_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(event_view.GetStatus());
      LOG_WARN("Received LeCsSecurityEnableCompleteView with error code %s", error_code.c_str());
      return;
    }
    uint16_t connection_handle = event_view.GetConnectionHandle();
    if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
      LOG_WARN("Can't find cs tracker for connection_handle %d", connection_handle);
      return;
    }
    cs_trackers_[connection_handle].setup_complete = true;
    LOG_INFO(
        "Setup phase complete, connection_handle: %d, address: %s",
        connection_handle,
        ADDRESS_TO_LOGGABLE_CSTR(cs_trackers_[connection_handle].address));
    if (cs_trackers_[connection_handle].role == CsRole::INITIATOR) {
      send_le_cs_create_config(connection_handle);
    }
  }

  void on_cs_config_complete(LeCsConfigCompleteView event_view) {
    if (!event_view.IsValid()) {
      LOG_WARN("Get invalid LeCsConfigCompleteView");
      return;
    } else if (event_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(event_view.GetStatus());
      LOG_WARN("Received LeCsConfigCompleteView with error code %s", error_code.c_str());
      return;
    }
    uint16_t connection_handle = event_view.GetConnectionHandle();
    if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
      LOG_WARN("Can't find cs tracker for connection_handle %d", connection_handle);
      return;
    }
    if (event_view.GetAction() == CsAction::CONFIG_REMOVED) {
      return;
    }
    LOG_INFO("Get %s", event_view.ToString().c_str());
    cs_trackers_[connection_handle].role = event_view.GetRole();
    cs_trackers_[connection_handle].config_set = true;
    cs_trackers_[connection_handle].main_mode_type = event_view.GetMainModeType();
    cs_trackers_[connection_handle].sub_mode_type = event_view.GetSubModeType();
    cs_trackers_[connection_handle].rtt_type = event_view.GetRttType();

    if (cs_trackers_[connection_handle].role == CsRole::INITIATOR) {
      send_le_cs_set_procedure_parameters(event_view.GetConnectionHandle());
    }
  }

  void on_cs_set_procedure_parameters(CommandCompleteView view) {
    auto complete_view = LeCsSetProcedureParametersCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_WARN("Get Invalid LeCsSetProcedureParametersCompleteView");
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(complete_view.GetStatus());
      LOG_WARN(
          "Received LeCsSetProcedureParametersCompleteView with error code %s", error_code.c_str());
      return;
    }
    uint16_t connection_handle = complete_view.GetConnectionHandle();
    if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
      LOG_WARN("Can't find cs tracker for connection_handle %d", connection_handle);
      return;
    }

    if (cs_trackers_[connection_handle].role == CsRole::INITIATOR) {
      LOG_INFO(
          "enable cs procedure regularly with interval: %d ms",
          cs_trackers_[connection_handle].interval_ms);
      cs_trackers_[connection_handle].repeating_alarm->Cancel();
      send_le_cs_procedure_enable(connection_handle, Enable::ENABLED);
      cs_trackers_[connection_handle].repeating_alarm->Schedule(
          common::Bind(
              &impl::send_le_cs_procedure_enable,
              common::Unretained(this),
              connection_handle,
              Enable::ENABLED),
          std::chrono::milliseconds(cs_trackers_[connection_handle].interval_ms));
    }
  }

  void on_cs_procedure_enable_complete(LeCsProcedureEnableCompleteView event_view) {
    ASSERT(event_view.IsValid());
    uint16_t connection_handle = event_view.GetConnectionHandle();
    if (event_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(event_view.GetStatus());
      LOG_WARN("Received LeCsProcedureEnableCompleteView with error code %s", error_code.c_str());
      if (cs_trackers_.find(connection_handle) != cs_trackers_.end() &&
          cs_trackers_[connection_handle].waiting_for_start_callback) {
        cs_trackers_[connection_handle].waiting_for_start_callback = false;
        distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
            cs_trackers_[connection_handle].address, REASON_INTERNAL_ERROR, METHOD_CS);
      }
      return;
    }

    if (event_view.GetState() == Enable::ENABLED) {
      LOG_DEBUG("Procedure enabled, %s", event_view.ToString().c_str());
      if (cs_trackers_.find(connection_handle) != cs_trackers_.end() &&
          cs_trackers_[connection_handle].waiting_for_start_callback) {
        cs_trackers_[connection_handle].waiting_for_start_callback = false;
        distance_measurement_callbacks_->OnDistanceMeasurementStarted(
            cs_trackers_[connection_handle].address, METHOD_CS);
      }
    }
  }

  void on_cs_subevent(LeMetaEventView event) {
    if (!event.IsValid()) {
      LOG_ERROR("Received invalid LeMetaEventView");
      return;
    }

    // Common data for LE_CS_SUBEVENT_RESULT and LE_CS_SUBEVENT_RESULT_CONTINUE,
    uint16_t connection_handle = 0;
    uint8_t abort_reason = kReportWithNoAbort;
    CsProcedureDoneStatus procedure_done_status;
    CsSubeventDoneStatus subevent_done_status;
    std::vector<LeCsResultDataStructure> result_data_structures;
    if (event.GetSubeventCode() == SubeventCode::LE_CS_SUBEVENT_RESULT) {
      auto cs_event_result = LeCsSubeventResultView::Create(event);
      if (!cs_event_result.IsValid()) {
        LOG_WARN("Get invalid LeCsSubeventResultView");
        return;
      }
      connection_handle = cs_event_result.GetConnectionHandle();
      abort_reason = cs_event_result.GetAbortReason();
      procedure_done_status = cs_event_result.GetProcedureDoneStatus();
      subevent_done_status = cs_event_result.GetSubeventDoneStatus();
      result_data_structures = cs_event_result.GetResultDataStructures();
      init_cs_procedure_data(
          connection_handle,
          cs_event_result.GetProcedureCounter(),
          cs_event_result.GetNumAntennaPaths(),
          true);
      CsProcedureData* procedure_data =
          get_procedure_data(connection_handle, cs_event_result.GetProcedureCounter());
      if (procedure_data != nullptr && cs_trackers_[connection_handle].role == CsRole::INITIATOR) {
        procedure_data->frequency_compensation.push_back(
            cs_event_result.GetFrequencyCompensation());
      }
    } else {
      auto cs_event_result = LeCsSubeventResultContinueView::Create(event);
      if (!cs_event_result.IsValid()) {
        LOG_WARN("Get invalid LeCsSubeventResultContinueView");
        return;
      }
      connection_handle = cs_event_result.GetConnectionHandle();
      abort_reason = cs_event_result.GetAbortReason();
      procedure_done_status = cs_event_result.GetProcedureDoneStatus();
      subevent_done_status = cs_event_result.GetSubeventDoneStatus();
      result_data_structures = cs_event_result.GetResultDataStructures();
    }

    uint16_t counter = cs_trackers_[connection_handle].local_counter;
    LOG_DEBUG(
        "Connection_handle %d, procedure_done_status: %s, subevent_done_status: %s, "
        "counter: "
        "%d",
        connection_handle,
        CsProcedureDoneStatusText(procedure_done_status).c_str(),
        CsSubeventDoneStatusText(subevent_done_status).c_str(),
        counter);

    if (procedure_done_status == CsProcedureDoneStatus::ABORTED ||
        subevent_done_status == CsSubeventDoneStatus::ABORTED) {
      LOG_WARN(
          "Received CS Subevent with abort reason: %02x, connection_handle:%d, counter:%d",
          abort_reason,
          connection_handle,
          counter);
    }

    CsProcedureData* procedure_data = get_procedure_data(connection_handle, counter);
    if (procedure_data == nullptr) {
      return;
    }

    if (abort_reason != kReportWithNoAbort) {
      // Even the procedure is aborted, we should keep following process and
      // handle it when all corresponding remote data received.
      procedure_data->aborted = true;
    } else {
      parse_cs_result_data(
          result_data_structures, *procedure_data, cs_trackers_[connection_handle].role);
    }
    // Update procedure status
    procedure_data->local_status = procedure_done_status;
    check_cs_procedure_complete(procedure_data, connection_handle);
  }

  void init_cs_procedure_data(
      uint16_t connection_handle,
      uint16_t procedure_counter,
      uint8_t num_antenna_paths,
      bool local) {
    if (cs_trackers_.find(connection_handle) == cs_trackers_.end()) {
      LOG_WARN("Can't find any tracker for %d", connection_handle);
      return;
    }
    // Update procedure count
    if (local) {
      cs_trackers_[connection_handle].local_counter = procedure_counter;
    } else {
      cs_trackers_[connection_handle].remote_counter = procedure_counter;
    }

    std::vector<CsProcedureData>& data_list = cs_trackers_[connection_handle].procedure_data_list;
    for (CsProcedureData procedure_data : data_list) {
      if (procedure_data.counter == procedure_counter) {
        // Data already exist, return
        return;
      }
    }
    LOG_INFO("Create data for procedure_counter: %d", procedure_counter);
    data_list.emplace_back(procedure_counter, num_antenna_paths);

    if (data_list.size() > kProcedureDataBufferSize) {
      LOG_WARN("buffer full, drop procedure data with counter: %d", data_list.front().counter);
      data_list.erase(data_list.begin());
    }
  }

  CsProcedureData* get_procedure_data(uint16_t connection_handle, uint16_t counter) {
    std::vector<CsProcedureData>& data_list = cs_trackers_[connection_handle].procedure_data_list;
    CsProcedureData* procedure_data = nullptr;
    for (uint8_t i = 0; i < data_list.size(); i++) {
      if (data_list[i].counter == counter) {
        procedure_data = &data_list[i];
        break;
      }
    }
    if (procedure_data == nullptr) {
      LOG_WARN("Can't find data for connection_handle:%d, counter: %d", connection_handle, counter);
    }
    return procedure_data;
  }

  void check_cs_procedure_complete(CsProcedureData* procedure_data, uint16_t connection_handle) {
    if (procedure_data->local_status == CsProcedureDoneStatus::ALL_RESULTS_COMPLETE &&
        procedure_data->remote_status == CsProcedureDoneStatus::ALL_RESULTS_COMPLETE &&
        !procedure_data->aborted) {
      LOG_DEBUG(
          "Procedure complete counter:%d data size:%d, main_mode_type:%d, sub_mode_type:%d",
          (uint16_t)procedure_data->counter,
          (uint16_t)procedure_data->step_channel.size(),
          (uint16_t)cs_trackers_[connection_handle].main_mode_type,
          (uint16_t)cs_trackers_[connection_handle].sub_mode_type);
    }

    // If the procedure is completed or aborted, delete all previous data
    if (procedure_data->local_status != CsProcedureDoneStatus::PARTIAL_RESULTS &&
        procedure_data->remote_status != CsProcedureDoneStatus::PARTIAL_RESULTS) {
      std::vector<CsProcedureData>& data_list = cs_trackers_[connection_handle].procedure_data_list;
      while (data_list.begin()->counter != procedure_data->counter) {
        LOG_DEBUG("Delete obsolete procedure data, counter:%d", data_list.begin()->counter);
        data_list.erase(data_list.begin());
      }
    }
  }

  void parse_cs_result_data(
      std::vector<LeCsResultDataStructure> result_data_structures,
      CsProcedureData& procedure_data,
      CsRole role) {
    uint8_t num_antenna_paths = procedure_data.num_antenna_paths;
    for (auto result_data_structure : result_data_structures) {
      uint16_t mode = result_data_structure.step_mode_;
      uint16_t step_channel = result_data_structure.step_channel_;
      LOG_VERBOSE(
          "mode: %d, channel: %d, data_length: %d",
          mode,
          step_channel,
          (uint16_t)result_data_structure.step_data_.size());

      // Parse data into structs from an iterator
      auto bytes = std::make_shared<std::vector<uint8_t>>();
      if (mode == 0x02 || mode == 0x03) {
        // Add one byte for the length of Tone_PCT[k], Tone_Quality_Indicator[k]
        bytes->emplace_back(num_antenna_paths + 1);
      }
      bytes->reserve(bytes->size() + result_data_structure.step_data_.size());
      bytes->insert(
          bytes->end(),
          result_data_structure.step_data_.begin(),
          result_data_structure.step_data_.end());
      Iterator<packet::kLittleEndian> iterator(bytes);
      switch (mode) {
        case 0: {
          if (role == CsRole::INITIATOR) {
            LeCsMode0InitatorData tone_data_view;
            auto after = LeCsMode0InitatorData::Parse(&tone_data_view, iterator);
            if (after == iterator) {
              LOG_WARN("Received invalid mode %d data, role:%s", mode, CsRoleText(role).c_str());
              print_raw_data(result_data_structure.step_data_);
              continue;
            }
            LOG_VERBOSE("step_data: %s", tone_data_view.ToString().c_str());
            procedure_data.measured_freq_offset.push_back(tone_data_view.measured_freq_offset_);
          } else {
            LeCsMode0ReflectorData tone_data_view;
            auto after = LeCsMode0ReflectorData::Parse(&tone_data_view, iterator);
            if (after == iterator) {
              LOG_WARN("Received invalid mode %d data, role:%s", mode, CsRoleText(role).c_str());
              print_raw_data(result_data_structure.step_data_);
              continue;
            }
            LOG_VERBOSE("step_data: %s", tone_data_view.ToString().c_str());
          }
        } break;
        case 2: {
          LeCsMode2Data tone_data_view;
          auto after = LeCsMode2Data::Parse(&tone_data_view, iterator);
          if (after == iterator) {
            LOG_WARN("Received invalid mode %d data, role:%s", mode, CsRoleText(role).c_str());
            print_raw_data(result_data_structure.step_data_);
            continue;
          }
          LOG_VERBOSE("step_data: %s", tone_data_view.ToString().c_str());
          if (role == CsRole::INITIATOR) {
            procedure_data.step_channel.push_back(step_channel);
          }
          auto tone_data = tone_data_view.tone_data_;
          uint8_t permutation_index = tone_data_view.antenna_permutation_index_;
          // Parse in ascending order of antenna position with tone extension data at the end
          uint16_t num_tone_data = num_antenna_paths + 1;
          for (uint16_t k = 0; k < num_tone_data; k++) {
            uint8_t antenna_path = k == num_antenna_paths
                                       ? num_antenna_paths
                                       : cs_antenna_permutation_array_[permutation_index][k] - 1;
            double i_value = get_iq_value(tone_data[k].i_sample_);
            double q_value = get_iq_value(tone_data[k].q_sample_);
            uint8_t tone_quality_indicator = tone_data[k].tone_quality_indicator_;
            LOG_VERBOSE("antenna_path %d, %f, %f", (uint16_t)(antenna_path + 1), i_value, q_value);
            if (role == CsRole::INITIATOR) {
              procedure_data.tone_pct_initiator[antenna_path].emplace_back(i_value, q_value);
              procedure_data.tone_quality_indicator_initiator[antenna_path].emplace_back(
                  tone_quality_indicator);
            } else {
              procedure_data.tone_pct_reflector[antenna_path].emplace_back(i_value, q_value);
              procedure_data.tone_quality_indicator_reflector[antenna_path].emplace_back(
                  tone_quality_indicator);
            }
          }
        } break;
        case 1:
        case 3:
          LOG_DEBUG("Unsupported mode: %d ", mode);
          break;
        default: {
          LOG_WARN("Invalid mode %d ", mode);
        }
      }
    }
  }

  double get_iq_value(uint16_t sample) {
    int16_t signed_sample = convert_to_signed(sample, 12);
    double value = 1.0 * signed_sample / 2048;
    return value;
  }

  int16_t convert_to_signed(uint16_t num_unsigned, uint8_t bits) {
    unsigned msb_mask = 1 << (bits - 1);  // setup a mask for most significant bit
    int16_t num_signed = num_unsigned;
    if ((num_signed & msb_mask) != 0) {
      num_signed |= ~(msb_mask - 1);  // extend the MSB
    }
    return num_signed;
  }

  void print_raw_data(std::vector<uint8_t> raw_data) {
    std::string raw_data_str = "";
    auto for_end = raw_data.size() - 1;
    for (size_t i = 0; i < for_end; i++) {
      char buff[10];
      snprintf(buff, sizeof(buff), "%02x ", (uint8_t)raw_data[i]);
      std::string buffAsStdStr = buff;
      raw_data_str.append(buffAsStdStr);
      if (i % 100 == 0 && i != 0) {
        LOG_VERBOSE("%s", raw_data_str.c_str());
        raw_data_str = "";
      }
    }
    char buff[10];
    snprintf(buff, sizeof(buff), "%02x", (uint8_t)raw_data[for_end]);
    std::string buffAsStdStr = buff;
    raw_data_str.append(buffAsStdStr);
    LOG_VERBOSE("%s", raw_data_str.c_str());
  }

  void on_read_remote_transmit_power_level_status(Address address, CommandStatusView view) {
    auto status_view = LeReadRemoteTransmitPowerLevelStatusView::Create(view);
    if (!status_view.IsValid()) {
      LOG_WARN("Invalid LeReadRemoteTransmitPowerLevelStatus event");
      distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
          address, REASON_INTERNAL_ERROR, METHOD_RSSI);
      rssi_trackers.erase(address);
    } else if (status_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(status_view.GetStatus());
      LOG_WARN(
          "Received LeReadRemoteTransmitPowerLevelStatus with error code %s", error_code.c_str());
      distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
          address, REASON_INTERNAL_ERROR, METHOD_RSSI);
      rssi_trackers.erase(address);
    }
  }

  void on_transmit_power_reporting(LeMetaEventView event) {
    auto event_view = LeTransmitPowerReportingView::Create(event);
    if (!event_view.IsValid()) {
      LOG_WARN("Dropping invalid LeTransmitPowerReporting event");
      return;
    }

    if (event_view.GetReason() == ReportingReason::LOCAL_TRANSMIT_POWER_CHANGED) {
      LOG_WARN("Dropping local LeTransmitPowerReporting event");
      return;
    }

    Address address = Address::kEmpty;
    for (auto& rssi_tracker : rssi_trackers) {
      if (rssi_tracker.second.handle == event_view.GetConnectionHandle()) {
        address = rssi_tracker.first;
      }
    }

    if (address.IsEmpty()) {
      LOG_WARN("Can't find rssi tracker for connection %d", event_view.GetConnectionHandle());
      return;
    }

    auto status = event_view.GetStatus();
    if (status != ErrorCode::SUCCESS) {
      LOG_WARN(
          "Received LeTransmitPowerReporting with error code %s", ErrorCodeText(status).c_str());
    } else {
      rssi_trackers[address].remote_tx_power = event_view.GetTransmitPowerLevel();
    }

    if (event_view.GetReason() == ReportingReason::READ_COMMAND_COMPLETE &&
        !rssi_trackers[address].started) {
      if (status == ErrorCode::SUCCESS) {
        hci_layer_->EnqueueCommand(
            LeSetTransmitPowerReportingEnableBuilder::Create(
                event_view.GetConnectionHandle(), 0x00, 0x01),
            handler_->BindOnceOn(
                this, &impl::on_set_transmit_power_reporting_enable_complete, address));
      } else {
        LOG_WARN("Read remote transmit power level fail");
        distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
            address, REASON_INTERNAL_ERROR, METHOD_RSSI);
        rssi_trackers.erase(address);
      }
    }
  }

  void on_set_transmit_power_reporting_enable_complete(Address address, CommandCompleteView view) {
    auto complete_view = LeSetTransmitPowerReportingEnableCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_WARN("Invalid LeSetTransmitPowerReportingEnableComplete event");
      distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
          address, REASON_INTERNAL_ERROR, METHOD_RSSI);
      rssi_trackers.erase(address);
      return;
    } else if (complete_view.GetStatus() != ErrorCode::SUCCESS) {
      std::string error_code = ErrorCodeText(complete_view.GetStatus());
      LOG_WARN(
          "Received LeSetTransmitPowerReportingEnableComplete with error code %s",
          error_code.c_str());
      distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
          address, REASON_INTERNAL_ERROR, METHOD_RSSI);
      rssi_trackers.erase(address);
      return;
    }

    if (rssi_trackers.find(address) == rssi_trackers.end()) {
      LOG_WARN("Can't find rssi tracker for %s", ADDRESS_TO_LOGGABLE_CSTR(address));
      distance_measurement_callbacks_->OnDistanceMeasurementStartFail(
          address, REASON_INTERNAL_ERROR, METHOD_RSSI);
      rssi_trackers.erase(address);
    } else {
      LOG_INFO("Track rssi for address %s", ADDRESS_TO_LOGGABLE_CSTR(address));
      rssi_trackers[address].started = true;
      distance_measurement_callbacks_->OnDistanceMeasurementStarted(address, METHOD_RSSI);
      rssi_trackers[address].repeating_alarm->Schedule(
          common::Bind(&impl::send_read_rssi, common::Unretained(this), address),
          std::chrono::milliseconds(rssi_trackers[address].interval_ms));
    }
  }

  void on_read_rssi_complete(Address address, CommandCompleteView view) {
    auto complete_view = ReadRssiCompleteView::Create(view);
    if (!complete_view.IsValid()) {
      LOG_WARN("Dropping invalid read RSSI complete event ");
      return;
    }
    if (rssi_trackers.find(address) == rssi_trackers.end()) {
      LOG_WARN("Can't find rssi tracker for %s", ADDRESS_TO_LOGGABLE_CSTR(address));
      return;
    }
    double remote_tx_power = (int8_t)rssi_trackers[address].remote_tx_power;
    int8_t rssi = complete_view.GetRssi();
    double pow_value = (remote_tx_power - rssi - kRSSIDropOffAt1M) / 20.0;
    double distance = pow(10.0, pow_value);
    distance_measurement_callbacks_->OnDistanceMeasurementResult(
        address,
        distance * 100,
        distance * 100,
        -1,
        -1,
        -1,
        -1,
        DistanceMeasurementMethod::METHOD_RSSI);
  }

  struct RSSITracker {
    uint16_t handle;
    uint16_t interval_ms;
    uint8_t remote_tx_power;
    bool started;
    std::unique_ptr<os::RepeatingAlarm> repeating_alarm;
  };

  struct CsTracker {
    Address address;
    uint16_t local_counter;
    uint16_t remote_counter;
    CsRole role;
    bool setup_complete = false;
    bool config_set = false;
    CsMainModeType main_mode_type;
    CsSubModeType sub_mode_type;
    CsRttType rtt_type;
    bool remote_support_phase_based_ranging = false;
    std::vector<CsProcedureData> procedure_data_list;
    uint16_t interval_ms;
    bool waiting_for_start_callback = false;
    std::unique_ptr<os::RepeatingAlarm> repeating_alarm;
  };

  os::Handler* handler_;
  hci::HciLayer* hci_layer_;
  hci::AclManager* acl_manager_;
  bool is_channel_sounding_supported_ = false;
  hci::DistanceMeasurementInterface* distance_measurement_interface_;
  std::unordered_map<Address, RSSITracker> rssi_trackers;
  std::unordered_map<uint16_t, CsTracker> cs_trackers_;
  DistanceMeasurementCallbacks* distance_measurement_callbacks_;
  CsOptionalSubfeaturesSupported cs_subfeature_supported_;
  // Antenna path permutations. See Channel Sounding CR_PR for the details.
  uint8_t cs_antenna_permutation_array_[24][4] = {
      {1, 2, 3, 4}, {2, 1, 3, 4}, {1, 3, 2, 4}, {3, 1, 2, 4}, {3, 2, 1, 4}, {2, 3, 1, 4},
      {1, 2, 4, 3}, {2, 1, 4, 3}, {1, 4, 2, 3}, {4, 1, 2, 3}, {4, 2, 1, 3}, {2, 4, 1, 3},
      {1, 4, 3, 2}, {4, 1, 3, 2}, {1, 3, 4, 2}, {3, 1, 4, 2}, {3, 4, 1, 2}, {4, 3, 1, 2},
      {4, 2, 3, 1}, {2, 4, 3, 1}, {4, 3, 2, 1}, {3, 4, 2, 1}, {3, 2, 4, 1}, {2, 3, 4, 1}};
};

DistanceMeasurementManager::DistanceMeasurementManager() {
  pimpl_ = std::make_unique<impl>();
}

DistanceMeasurementManager::~DistanceMeasurementManager() = default;

void DistanceMeasurementManager::ListDependencies(ModuleList* list) const {
  list->add<hci::HciLayer>();
  list->add<hci::AclManager>();
}

void DistanceMeasurementManager::Start() {
  pimpl_->start(GetHandler(), GetDependency<hci::HciLayer>(), GetDependency<AclManager>());
}

void DistanceMeasurementManager::Stop() {
  pimpl_->stop();
}

std::string DistanceMeasurementManager::ToString() const {
  return "Distance Measurement Manager";
}

void DistanceMeasurementManager::RegisterDistanceMeasurementCallbacks(
    DistanceMeasurementCallbacks* callbacks) {
  CallOn(pimpl_.get(), &impl::register_distance_measurement_callbacks, callbacks);
}

void DistanceMeasurementManager::StartDistanceMeasurement(
    const Address& address, uint16_t interval, DistanceMeasurementMethod method) {
  CallOn(pimpl_.get(), &impl::start_distance_measurement, address, interval, method);
}

void DistanceMeasurementManager::StopDistanceMeasurement(
    const Address& address, DistanceMeasurementMethod method) {
  CallOn(pimpl_.get(), &impl::stop_distance_measurement, address, method);
}

}  // namespace hci
}  // namespace bluetooth
