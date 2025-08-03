/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "rust/topshim/hfp/hfp_shim.h"

#include "btif/include/btif_hf.h"
#include "common/strings.h"
#include "device/include/interop.h"
#include "include/hardware/bt_hf.h"
#include "os/log.h"
#include "src/profiles/hfp.rs.h"
#include "types/raw_address.h"

namespace rusty = ::bluetooth::topshim::rust;

namespace bluetooth {
namespace topshim {
namespace rust {
namespace internal {
static HfpIntf* g_hfpif;

static void connection_state_cb(bluetooth::headset::bthf_connection_state_t state, RawAddress* addr) {
  rusty::hfp_connection_state_callback(state, *addr);
}

static void audio_state_cb(bluetooth::headset::bthf_audio_state_t state, RawAddress* addr) {
  rusty::hfp_audio_state_callback(state, *addr);
}

static void volume_update_cb(uint8_t volume, RawAddress* addr) {
  rusty::hfp_volume_update_callback(volume, *addr);
}

static void mic_volume_update_cb(uint8_t volume, RawAddress* addr) {
  rusty::hfp_mic_volume_update_callback(volume, *addr);
}

static void vendor_specific_at_command_cb(char* at_string, RawAddress* addr) {
  rusty::hfp_vendor_specific_at_command_callback(::rust::String{at_string}, *addr);
}

static void battery_level_update_cb(uint8_t battery_level, RawAddress* addr) {
  rusty::hfp_battery_level_update_callback(battery_level, *addr);
}

static void indicator_query_cb(RawAddress* addr) {
  rusty::hfp_indicator_query_callback(*addr);
}

static void current_calls_query_cb(RawAddress* addr) {
  rusty::hfp_current_calls_query_callback(*addr);
}

static void answer_call_cb(RawAddress* addr) {
  rusty::hfp_answer_call_callback(*addr);
}

static void hangup_call_cb(RawAddress* addr) {
  rusty::hfp_hangup_call_callback(*addr);
}

static void dial_call_cb(char* number, RawAddress* addr) {
  rusty::hfp_dial_call_callback(::rust::String{number}, *addr);
}

static void call_hold_cb(bluetooth::headset::bthf_chld_type_t chld, RawAddress* addr) {
  rusty::CallHoldCommand chld_rs;
  switch (chld) {
    case bluetooth::headset::BTHF_CHLD_TYPE_RELEASEHELD:
      chld_rs = rusty::CallHoldCommand::ReleaseHeld;
      break;
    case bluetooth::headset::BTHF_CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD:
      chld_rs = rusty::CallHoldCommand::ReleaseActiveAcceptHeld;
      break;
    case bluetooth::headset::BTHF_CHLD_TYPE_HOLDACTIVE_ACCEPTHELD:
      chld_rs = rusty::CallHoldCommand::HoldActiveAcceptHeld;
      break;
    case bluetooth::headset::BTHF_CHLD_TYPE_ADDHELDTOCONF:
      chld_rs = rusty::CallHoldCommand::AddHeldToConf;
      break;
    default:
      ASSERT_LOG(false, "Unhandled enum value from C++");
  }
  rusty::hfp_call_hold_callback(chld_rs, *addr);
}

static headset::bthf_call_state_t from_rust_call_state(rusty::CallState state) {
  switch (state) {
    case rusty::CallState::Idle:
      return headset::BTHF_CALL_STATE_IDLE;
    case rusty::CallState::Incoming:
      return headset::BTHF_CALL_STATE_INCOMING;
    case rusty::CallState::Dialing:
      return headset::BTHF_CALL_STATE_DIALING;
    case rusty::CallState::Alerting:
      return headset::BTHF_CALL_STATE_ALERTING;
    case rusty::CallState::Active:
      return headset::BTHF_CALL_STATE_ACTIVE;
    case rusty::CallState::Held:
      return headset::BTHF_CALL_STATE_HELD;
    default:
      ASSERT_LOG(false, "Unhandled enum value from Rust");
  }
}

static void debug_dump_cb(
    bool active,
    uint16_t codec_id,
    int total_num_decoded_frames,
    double packet_loss_ratio,
    uint64_t begin_ts,
    uint64_t end_ts,
    const char* pkt_status_in_hex,
    const char* pkt_status_in_binary) {
  rusty::hfp_debug_dump_callback(
      active,
      codec_id,
      total_num_decoded_frames,
      packet_loss_ratio,
      begin_ts,
      end_ts,
      ::rust::String{pkt_status_in_hex},
      ::rust::String{pkt_status_in_binary});
}
}  // namespace internal

class DBusHeadsetCallbacks : public headset::Callbacks {
 public:
  static Callbacks* GetInstance(headset::Interface* headset) {
    static Callbacks* instance = new DBusHeadsetCallbacks(headset);
    return instance;
  }

  DBusHeadsetCallbacks(headset::Interface* headset) : headset_(headset){};

  // headset::Callbacks
  void ConnectionStateCallback(headset::bthf_connection_state_t state, RawAddress* bd_addr) override {
    LOG_INFO("ConnectionStateCallback from %s", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    topshim::rust::internal::connection_state_cb(state, bd_addr);
  }

  void AudioStateCallback(headset::bthf_audio_state_t state, RawAddress* bd_addr) override {
    LOG_INFO("AudioStateCallback %u from %s", state, ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    topshim::rust::internal::audio_state_cb(state, bd_addr);
  }

  void VoiceRecognitionCallback(
      [[maybe_unused]] headset::bthf_vr_state_t state, [[maybe_unused]] RawAddress* bd_addr) override {}

  void AnswerCallCallback(RawAddress* bd_addr) override {
    topshim::rust::internal::answer_call_cb(bd_addr);
  }

  void HangupCallCallback(RawAddress* bd_addr) override {
    topshim::rust::internal::hangup_call_cb(bd_addr);
  }

  void VolumeControlCallback(headset::bthf_volume_type_t type, int volume, RawAddress* bd_addr) override {
    if (volume < 0) return;
    if (volume > 15) volume = 15;
    if (type == headset::bthf_volume_type_t::BTHF_VOLUME_TYPE_SPK) {
      LOG_INFO(
          "VolumeControlCallback (Spk) %d from %s", volume, ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
      topshim::rust::internal::volume_update_cb(volume, bd_addr);
    } else if (type == headset::bthf_volume_type_t::BTHF_VOLUME_TYPE_MIC) {
      LOG_INFO(
          "VolumeControlCallback (Mic) %d from %s", volume, ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
      topshim::rust::internal::mic_volume_update_cb(volume, bd_addr);
    }
  }

  void DialCallCallback(char* number, RawAddress* bd_addr) override {
    topshim::rust::internal::dial_call_cb(number, bd_addr);
  }

  void DtmfCmdCallback([[maybe_unused]] char tone, [[maybe_unused]] RawAddress* bd_addr) override {}

  void NoiseReductionCallback(
      [[maybe_unused]] headset::bthf_nrec_t nrec, [[maybe_unused]] RawAddress* bd_addr) override {}

  void WbsCallback(headset::bthf_wbs_config_t wbs, RawAddress* addr) override {
    LOG_INFO("WbsCallback %d from %s", wbs, ADDRESS_TO_LOGGABLE_CSTR(*addr));
    rusty::hfp_wbs_caps_update_callback(wbs == headset::BTHF_WBS_YES, *addr);
  }

  void SwbCallback(
      headset::bthf_swb_codec_t codec, headset::bthf_swb_config_t swb, RawAddress* addr) override {
    LOG_INFO("SwbCallback codec:%d, swb:%d from %s", codec, swb, ADDRESS_TO_LOGGABLE_CSTR(*addr));
    rusty::hfp_swb_caps_update_callback(
        (codec == headset::BTHF_SWB_CODEC_LC3 && swb == headset::BTHF_SWB_YES), *addr);
  }

  void AtChldCallback(headset::bthf_chld_type_t chld, RawAddress* bd_addr) override {
    topshim::rust::internal::call_hold_cb(chld, bd_addr);
  }

  void AtCnumCallback(RawAddress* bd_addr) override {
    // Send an OK response to HF to indicate that we have no subscriber info.
    // This is mandatory support for passing HFP/AG/NUM/BV-01-I.
    headset_->AtResponse(headset::BTHF_AT_RESPONSE_OK, 0, bd_addr);
  }

  void AtCindCallback(RawAddress* bd_addr) override {
    topshim::rust::internal::indicator_query_cb(bd_addr);
  }

  void AtCopsCallback(RawAddress* bd_addr) override {
    LOG_WARN("Respond +COPS: 0 to AT+COPS? from %s",
             ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    headset_->CopsResponse("", bd_addr);
  }

  void AtClccCallback(RawAddress* bd_addr) override {
    topshim::rust::internal::current_calls_query_cb(bd_addr);
  }

  void UnknownAtCallback(char* at_string, RawAddress* bd_addr) override {
    const std::string at_command = common::ToString(at_string);
    // We are able to support +XAPL, +IPHONEACCEV, and +XEVENT commands,
    // everything else will get an error reply.
    const bool is_xapl = at_command.find("+XAPL") != std::string::npos;
    const bool is_iphoneaccev = at_command.find("+IPHONEACCEV") != std::string::npos;
    const bool is_xevent = at_command.find("+XEVENT") != std::string::npos;
    if (!is_xapl && !is_iphoneaccev && !is_xevent) {
      LOG_WARN("Reply Error to UnknownAtCallback:%s", at_string);
      headset_->AtResponse(headset::BTHF_AT_RESPONSE_ERROR, 0, bd_addr);
      return;
    }

    if (is_xapl) {
      // Respond that we support battery level reporting only (2).
      headset_->FormattedAtResponse("+XAPL=iPhone,2", bd_addr);
    }

    // Ack all supported commands and bubble commands up for further processing
    // if desired.
    topshim::rust::internal::vendor_specific_at_command_cb(at_string, bd_addr);
    headset_->AtResponse(headset::BTHF_AT_RESPONSE_OK, 0, bd_addr);
  }

  void KeyPressedCallback([[maybe_unused]] RawAddress* bd_addr) override {}

  void AtBindCallback(char* at_string, RawAddress* bd_addr) override {
    LOG_WARN(
        "AT+BIND %s from addr %s: Bluetooth HF Indicators is not supported.",
        at_string,
        ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
  }

  void AtBievCallback(headset::bthf_hf_ind_type_t ind_id, int ind_value, RawAddress* bd_addr) override {
    switch (ind_id) {
      case headset::bthf_hf_ind_type_t::BTHF_HF_IND_ENHANCED_DRIVER_SAFETY:
        // We don't do anything with this but we do know what it is, send OK.
        headset_->AtResponse(headset::BTHF_AT_RESPONSE_OK, 0, bd_addr);
        break;
      case headset::bthf_hf_ind_type_t::BTHF_HF_IND_BATTERY_LEVEL_STATUS:
        topshim::rust::internal::battery_level_update_cb(ind_value, bd_addr);
        headset_->AtResponse(headset::BTHF_AT_RESPONSE_OK, 0, bd_addr);
        break;
      default:
        LOG_WARN(
            "AT+BIEV indicator %i with value %i from addr %s",
            ind_id,
            ind_value,
            ADDRESS_TO_LOGGABLE_CSTR(*bd_addr) );
        return;
    }
  }

  void AtBiaCallback(bool service, bool roam, bool signal, bool battery, RawAddress* bd_addr) override {
    LOG_WARN("AT+BIA=,,%d,%d,%d,%d,from addr %s", service, signal, roam,
             battery, ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
  }

  void DebugDumpCallback(
      bool active,
      uint16_t codec_id,
      int total_num_decoded_frames,
      double packet_loss_ratio,
      uint64_t begin_ts,
      uint64_t end_ts,
      const char* pkt_status_in_hex,
      const char* pkt_status_in_binary) override {
    LOG_WARN(
        "DebugDumpCallback %d %u %d %f %llu %llu %s %s",
        active,
        codec_id,
        total_num_decoded_frames,
        packet_loss_ratio,
        (unsigned long long)begin_ts,
        (unsigned long long)end_ts,
        pkt_status_in_hex,
        pkt_status_in_binary);
    topshim::rust::internal::debug_dump_cb(
        active,
        codec_id,
        total_num_decoded_frames,
        packet_loss_ratio,
        begin_ts,
        end_ts,
        pkt_status_in_hex,
        pkt_status_in_binary);
  }

 private:
  headset::Interface* headset_;
};

int HfpIntf::init() {
  return intf_->Init(DBusHeadsetCallbacks::GetInstance(intf_), 1, false);
}

uint32_t HfpIntf::connect(RawAddress addr) {
  return intf_->Connect(&addr);
}

int HfpIntf::connect_audio(RawAddress addr, bool sco_offload, int disabled_codecs) {
  intf_->SetScoOffloadEnabled(sco_offload);
  return intf_->ConnectAudio(&addr, disabled_codecs);
}

int HfpIntf::set_active_device(RawAddress addr) {
  return intf_->SetActiveDevice(&addr);
}

int HfpIntf::set_volume(int8_t volume, RawAddress addr) {
  return intf_->VolumeControl(headset::bthf_volume_type_t::BTHF_VOLUME_TYPE_SPK, volume, &addr);
}

uint32_t HfpIntf::set_mic_volume(int8_t volume, RawAddress addr) {
  return intf_->VolumeControl(headset::bthf_volume_type_t::BTHF_VOLUME_TYPE_MIC, volume, &addr);
}

uint32_t HfpIntf::disconnect(RawAddress addr) {
  return intf_->Disconnect(&addr);
}

int HfpIntf::disconnect_audio(RawAddress addr) {
  return intf_->DisconnectAudio(&addr);
}

uint32_t HfpIntf::device_status_notification(TelephonyDeviceStatus status, RawAddress addr) {
  return intf_->DeviceStatusNotification(
      status.network_available ? headset::BTHF_NETWORK_STATE_AVAILABLE
                               : headset::BTHF_NETWORK_STATE_NOT_AVAILABLE,
      status.roaming ? headset::BTHF_SERVICE_TYPE_ROAMING : headset::BTHF_SERVICE_TYPE_HOME,
      status.signal_strength,
      status.battery_level,
      &addr);
}

uint32_t HfpIntf::indicator_query_response(
    TelephonyDeviceStatus device_status, PhoneState phone_state, RawAddress addr) {
  return intf_->CindResponse(
      device_status.network_available ? 1 : 0,
      phone_state.num_active,
      phone_state.num_held,
      topshim::rust::internal::from_rust_call_state(phone_state.state),
      device_status.signal_strength,
      device_status.roaming ? 1 : 0,
      device_status.battery_level,
      &addr);
}

uint32_t HfpIntf::current_calls_query_response(
    const ::rust::Vec<CallInfo>& call_list, RawAddress addr) {
  for (const auto& c : call_list) {
    std::string number{c.number};
    intf_->ClccResponse(
        c.index,
        c.dir_incoming ? headset::BTHF_CALL_DIRECTION_INCOMING
                       : headset::BTHF_CALL_DIRECTION_OUTGOING,
        topshim::rust::internal::from_rust_call_state(c.state),
        /*mode=*/headset::BTHF_CALL_TYPE_VOICE,
        /*multi_party=*/headset::BTHF_CALL_MPTY_TYPE_SINGLE,
        number.c_str(),
        /*type=*/headset::BTHF_CALL_ADDRTYPE_UNKNOWN,
        &addr);
  }

  // NULL termination (Completes response)
  return intf_->ClccResponse(
      /*index=*/0,
      /*dir=*/(headset::bthf_call_direction_t)0,
      /*state=*/(headset::bthf_call_state_t)0,
      /*mode=*/(headset::bthf_call_mode_t)0,
      /*multi_party=*/(headset::bthf_call_mpty_type_t)0,
      /*number=*/"",
      /*type=*/(headset::bthf_call_addrtype_t)0,
      &addr);
}

uint32_t HfpIntf::phone_state_change(
    PhoneState phone_state, const ::rust::String& number_rs, RawAddress addr) {
  std::string number{number_rs};
  return intf_->PhoneStateChange(
      phone_state.num_active,
      phone_state.num_held,
      topshim::rust::internal::from_rust_call_state(phone_state.state),
      number.c_str(),
      /*type=*/(headset::bthf_call_addrtype_t)0,
      /*name=*/"",
      &addr);
}

uint32_t HfpIntf::simple_at_response(bool ok, RawAddress addr) {
  return intf_->AtResponse(
      (ok ? headset::BTHF_AT_RESPONSE_OK : headset::BTHF_AT_RESPONSE_ERROR), 0, &addr);
}

void HfpIntf::debug_dump() {
  intf_->DebugDump();
}

void HfpIntf::cleanup() {}

std::unique_ptr<HfpIntf> GetHfpProfile(const unsigned char* btif) {
  if (internal::g_hfpif) std::abort();

  const bt_interface_t* btif_ = reinterpret_cast<const bt_interface_t*>(btif);

  auto hfpif = std::make_unique<HfpIntf>(const_cast<headset::Interface*>(
      reinterpret_cast<const headset::Interface*>(btif_->get_profile_interface("handsfree"))));
  internal::g_hfpif = hfpif.get();

  return hfpif;
}

bool interop_insert_call_when_sco_start(RawAddress addr) {
  return interop_match_addr(interop_feature_t::INTEROP_INSERT_CALL_WHEN_SCO_START, &addr);
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
