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

#include "bta/le_audio/broadcaster/state_machine.h"

#include <bind_helpers.h>

#include <functional>
#include <iostream>

#include "base/functional/bind.h"
#include "base/functional/callback.h"
#include "base/logging.h"
#include "bta/le_audio/broadcaster/broadcaster_types.h"
#include "bta/le_audio/le_audio_types.h"
#include "common/strings.h"
#include "hci/le_advertising_manager.h"
#include "os/log.h"
#include "osi/include/properties.h"
#include "stack/include/btm_iso_api.h"

using bluetooth::common::ToString;
using bluetooth::hci::IsoManager;
using bluetooth::hci::iso_manager::big_create_cmpl_evt;
using bluetooth::hci::iso_manager::big_terminate_cmpl_evt;

using le_audio::CodecManager;
using le_audio::types::CodecLocation;

using namespace le_audio::broadcaster;

namespace {

// Advertising channels. These should be kept the same as those defined in the
// stack.
const int kAdvertisingChannel37 = (1 << 0);
const int kAdvertisingChannel38 = (1 << 1);
const int kAdvertisingChannel39 = (1 << 2);
const int kAdvertisingChannelAll =
    (kAdvertisingChannel37 | kAdvertisingChannel38 | kAdvertisingChannel39);

class BroadcastStateMachineImpl : public BroadcastStateMachine {
 public:
  BroadcastStateMachineImpl(BroadcastStateMachineConfig msg)
      : active_config_(std::nullopt),
        sm_config_(std::move(msg)),
        suspending_(false) {}

  ~BroadcastStateMachineImpl() {
    if (GetState() == State::STREAMING) TerminateBig();
    DestroyBroadcastAnnouncement();
    if (callbacks_) callbacks_->OnStateMachineDestroyed(GetBroadcastId());
  }

  bool Initialize() override {
    static constexpr uint8_t sNumBisMax = 31;

    if (sm_config_.codec_wrapper.GetNumChannels() > sNumBisMax) {
      LOG_ERROR(
          "Channel count of %d exceeds the maximum number of possible BISes, "
          "which is %d",
          sm_config_.codec_wrapper.GetNumChannels(), sNumBisMax);
      return false;
    }

    CreateBroadcastAnnouncement(
        sm_config_.is_public, sm_config_.broadcast_name,
        sm_config_.broadcast_id, sm_config_.public_announcement,
        sm_config_.announcement, sm_config_.streaming_phy);
    return true;
  }

  const BroadcastCodecWrapper& GetCodecConfig() const override {
    return sm_config_.codec_wrapper;
  }

  std::optional<BigConfig> const& GetBigConfig() const override {
    return active_config_;
  }

  BroadcastStateMachineConfig const& GetStateMachineConfig() const override {
    return sm_config_;
  }

  void RequestOwnAddress(
      base::Callback<void(uint8_t /* address_type*/, RawAddress /*address*/)>
          cb) override {
    uint8_t advertising_sid = GetAdvertisingSid();
    advertiser_if_->GetOwnAddress(advertising_sid, cb);
  }

  void RequestOwnAddress(void) override {
    auto broadcast_id = GetBroadcastId();
    RequestOwnAddress(
        base::Bind(&IBroadcastStateMachineCallbacks::OnOwnAddressResponse,
                   base::Unretained(this->callbacks_), broadcast_id));
  }

  RawAddress GetOwnAddress() override { return addr_; }

  uint8_t GetOwnAddressType() override { return addr_type_; }

  bluetooth::le_audio::BroadcastId GetBroadcastId() const override {
    return sm_config_.broadcast_id;
  }

  std::optional<bluetooth::le_audio::BroadcastCode> GetBroadcastCode()
      const override {
    return sm_config_.broadcast_code;
  }

  const bluetooth::le_audio::BasicAudioAnnouncementData&
  GetBroadcastAnnouncement() const override {
    return sm_config_.announcement;
  }

  bool IsPublicBroadcast() override { return sm_config_.is_public; }

  std::string GetBroadcastName() override { return sm_config_.broadcast_name; }

  const bluetooth::le_audio::PublicBroadcastAnnouncementData&
  GetPublicBroadcastAnnouncement() const override {
    return sm_config_.public_announcement;
  }

  void OnCreateAnnouncement(uint8_t advertising_sid, int8_t tx_power,
                            uint8_t status) {
    LOG_INFO("advertising_sid=%d tx_power=%d status=%d", advertising_sid,
             tx_power, status);

    /* If this callback gets called the advertising_sid is valid even though the
     * status can be other than SUCCESS.
     */
    advertising_sid_ = advertising_sid;

    if (status !=
        bluetooth::hci::AdvertisingCallback::AdvertisingStatus::SUCCESS) {
      LOG_ERROR("Creating Announcement failed");
      callbacks_->OnStateMachineCreateStatus(GetBroadcastId(), false);
      return;
    }

    /* Ext. advertisings are already on */
    SetState(State::CONFIGURED);

    callbacks_->OnStateMachineCreateStatus(GetBroadcastId(), true);
    callbacks_->OnStateMachineEvent(GetBroadcastId(), State::CONFIGURED);

    advertiser_if_->GetOwnAddress(
        advertising_sid,
        base::Bind(&BroadcastStateMachineImpl::OnAddressResponse,
                   base::Unretained(this)));
  }

  void OnEnableAnnouncement(bool enable, uint8_t status) {
    LOG_INFO("operation=%s, broadcast_id=%d, status=%d",
             (enable ? "enable" : "disable"), GetBroadcastId(), status);

    if (status ==
        bluetooth::hci::AdvertisingCallback::AdvertisingStatus::SUCCESS) {
      /* Periodic is enabled but without BIGInfo. Stream is suspended. */
      if (enable) {
        SetState(State::CONFIGURED);
        /* Target state is always STREAMING state - start it now. */
        ProcessMessage(Message::START);
      } else {
        /* User wanted to stop the announcement - report target state reached */
        SetState(State::STOPPED);
        callbacks_->OnStateMachineEvent(GetBroadcastId(), GetState());
      }
    } else {
      // Handle error case
      if (enable) {
        /* Error on enabling */
        SetState(State::STOPPED);
      } else {
        /* Error on disabling */
        SetState(State::CONFIGURED);
      }
      callbacks_->OnStateMachineEvent(GetBroadcastId(), GetState());
    }
  }

  void UpdatePublicBroadcastAnnouncement(
      uint32_t broadcast_id, const std::string& broadcast_name,
      const bluetooth::le_audio::PublicBroadcastAnnouncementData& announcement)
      override {
    std::vector<uint8_t> adv_data;
    PrepareAdvertisingData(true, broadcast_name, broadcast_id, announcement,
                           adv_data);

    sm_config_.broadcast_name = broadcast_name;
    sm_config_.public_announcement = announcement;
    advertiser_if_->SetData(advertising_sid_, false, adv_data,
                            base::DoNothing());
  }

  void UpdateBroadcastAnnouncement(
      bluetooth::le_audio::BasicAudioAnnouncementData announcement) override {
    std::vector<uint8_t> periodic_data;
    PreparePeriodicData(announcement, periodic_data);

    sm_config_.announcement = std::move(announcement);
    advertiser_if_->SetPeriodicAdvertisingData(advertising_sid_, periodic_data,
                                               base::DoNothing());
  }

  void ProcessMessage(Message msg, const void* data = nullptr) override {
    LOG_INFO("broadcast_id=%d, state=%s, message=%s", GetBroadcastId(),
             ToString(GetState()).c_str(), ToString(msg).c_str());
    switch (msg) {
      case Message::START:
        start_msg_handlers[StateMachine::GetState()](data);
        break;
      case Message::STOP:
        stop_msg_handlers[StateMachine::GetState()](data);
        break;
      case Message::SUSPEND:
        suspend_msg_handlers[StateMachine::GetState()](data);
        break;
    };
  }

  static IBroadcastStateMachineCallbacks* callbacks_;
  static BleAdvertiserInterface* advertiser_if_;

 private:
  std::optional<BigConfig> active_config_;
  BroadcastStateMachineConfig sm_config_;
  bool suspending_;

  /* Message handlers for each possible state */
  typedef std::function<void(const void*)> msg_handler_t;
  const std::array<msg_handler_t, BroadcastStateMachine::STATE_COUNT>
      start_msg_handlers{
          /* in STOPPED state */
          [this](const void*) {
            SetState(State::CONFIGURING);
            callbacks_->OnStateMachineEvent(GetBroadcastId(), GetState());
            EnableAnnouncement();
          },
          /* in CONFIGURING state */
          [](const void*) { /* Do nothing */ },
          /* in CONFIGURED state */
          [this](const void*) { CreateBig(); },
          /* in STOPPING state */
          [](const void*) { /* Do nothing */ },
          /* in STREAMING state */
          [](const void*) { /* Do nothing */ }};

  const std::array<msg_handler_t, BroadcastStateMachine::STATE_COUNT>
      stop_msg_handlers{
          /* in STOPPED state */
          [](const void*) { /* Already stopped */ },
          /* in CONFIGURING state */
          [](const void*) { /* Do nothing */ },
          /* in CONFIGURED state */
          [this](const void*) {
            SetState(State::STOPPING);
            callbacks_->OnStateMachineEvent(GetBroadcastId(), GetState());
            DisableAnnouncement();
          },
          /* in STOPPING state */
          [](const void*) { /* Do nothing */ },
          /* in STREAMING state */
          [this](const void*) {
            if ((active_config_ != std::nullopt) && !suspending_) {
              suspending_ = false;
              SetState(State::STOPPING);
              callbacks_->OnStateMachineEvent(GetBroadcastId(), GetState());
              TriggerIsoDatapathTeardown(active_config_->connection_handles[0]);
            }
          }};

  const std::array<msg_handler_t, BroadcastStateMachine::STATE_COUNT>
      suspend_msg_handlers{
          /* in STOPPED state */
          [](const void*) { /* Do nothing */ },
          /* in CONFIGURING state */
          [](const void*) { /* Do nothing */ },
          /* in CONFIGURED state */
          [](const void*) { /* Already suspended */ },
          /* in STOPPING state */
          [](const void*) { /* Do nothing */ },
          /* in STREAMING state */
          [this](const void*) {
            if ((active_config_ != std::nullopt) && !suspending_) {
              suspending_ = true;
              TriggerIsoDatapathTeardown(active_config_->connection_handles[0]);
            }
          }};

  const std::array<msg_handler_t, BroadcastStateMachine::STATE_COUNT>
      resume_msg_handlers{/* in STOPPED state */
                          [](const void*) { /* Do nothing */ },
                          /* in CONFIGURING state */
                          [](const void*) { /* Do nothing */ },
                          /* in CONFIGURED state */
                          [this](const void*) { CreateBig(); },
                          /* in STOPPING state */
                          [](const void*) { /* Do nothing */ },
                          /* in STREAMING state */
                          [](const void*) { /* Already streaming */ }};

  void OnAddressResponse(uint8_t addr_type, RawAddress addr) {
    LOG_INFO("own address=%s, type=%d", ADDRESS_TO_LOGGABLE_CSTR(addr), addr_type);
    addr_ = addr;
    addr_type_ = addr_type;
  }

  void CreateBroadcastAnnouncement(
      bool is_public, const std::string& broadcast_name,
      bluetooth::le_audio::BroadcastId& broadcast_id,
      const bluetooth::le_audio::PublicBroadcastAnnouncementData&
          public_announcement,
      const bluetooth::le_audio::BasicAudioAnnouncementData& announcement,
      uint8_t streaming_phy) {
    LOG_INFO("is_public=%s, broadcast_name=%s, public_features=%d",
             (is_public ? "public" : "non-public"), broadcast_name.c_str(),
             public_announcement.features);
    if (advertiser_if_ != nullptr) {
      AdvertiseParameters adv_params;
      PeriodicAdvertisingParameters periodic_params;
      std::vector<uint8_t> adv_data;
      std::vector<uint8_t> periodic_data;

      PrepareAdvertisingData(is_public, broadcast_name, broadcast_id,
                             public_announcement, adv_data);
      PreparePeriodicData(announcement, periodic_data);

      adv_params.min_interval = 0x00A0; /* 160 * 0,625 = 100ms */
      adv_params.max_interval = 0x0140; /* 320 * 0,625 = 200ms */
      adv_params.advertising_event_properties = 0;
      adv_params.channel_map = kAdvertisingChannelAll;
      adv_params.tx_power = 8;
      adv_params.primary_advertising_phy = PHY_LE_1M;
      adv_params.secondary_advertising_phy = streaming_phy;
      adv_params.scan_request_notification_enable = 0;
      adv_params.own_address_type = kBroadcastAdvertisingType;

      periodic_params.max_interval = BroadcastStateMachine::kPaIntervalMax;
      periodic_params.min_interval = BroadcastStateMachine::kPaIntervalMin;
      periodic_params.periodic_advertising_properties = 0;
      periodic_params.enable = true;

      /* Status and timeout callbacks are handled by OnAdvertisingSetStarted()
       * which returns the status and handle to be used later in CreateBIG
       * command.
       */
      advertiser_if_->StartAdvertisingSet(
          kAdvertiserClientIdLeAudio, kLeAudioBroadcastRegId, base::DoNothing(),
          adv_params, adv_data, std::vector<uint8_t>(), periodic_params,
          periodic_data, 0 /* duration */, 0 /* maxExtAdvEvents */,
          base::DoNothing());
    }
  }

  void DestroyBroadcastAnnouncement() {
    advertiser_if_->Unregister(GetAdvertisingSid());
  }

  void EnableAnnouncement() {
    LOG_INFO("broadcast_id=%d", GetBroadcastId());
    // Callback is handled by OnAdvertisingEnabled() which returns the status
    advertiser_if_->Enable(GetAdvertisingSid(), true, base::DoNothing(), 0,
                           0, /* Enable until stopped */
                           base::DoNothing());
  }

  void CreateBig(void) {
    LOG_INFO("broadcast_id=%d", GetBroadcastId());
    /* TODO: Figure out how to decide on the currently hard-codded params. */
    struct bluetooth::hci::iso_manager::big_create_params big_params = {
        .adv_handle = GetAdvertisingSid(),
        .num_bis = sm_config_.codec_wrapper.GetNumChannels(),
        .sdu_itv = sm_config_.codec_wrapper.GetDataIntervalUs(),
        .max_sdu_size = sm_config_.codec_wrapper.GetMaxSduSize(),
        .max_transport_latency = sm_config_.qos_config.getMaxTransportLatency(),
        .rtn = sm_config_.qos_config.getRetransmissionNumber(),
        .phy = sm_config_.streaming_phy,
        .packing = 0x00, /* Sequencial */
        .framing = 0x00, /* Unframed */
        .enc = static_cast<uint8_t>(sm_config_.broadcast_code ? 1 : 0),
        .enc_code = sm_config_.broadcast_code ? *sm_config_.broadcast_code
                                              : std::array<uint8_t, 16>({0}),
    };

    IsoManager::GetInstance()->CreateBig(GetAdvertisingSid(),
                                         std::move(big_params));
  }

  void DisableAnnouncement(void) {
    LOG_INFO("broadcast_id=%d", GetBroadcastId());
    // Callback is handled by OnAdvertisingEnabled() which returns the status
    advertiser_if_->Enable(GetAdvertisingSid(), false, base::DoNothing(), 0, 0,
                           base::DoNothing());
  }

  void TerminateBig() {
    LOG_INFO("suspending=%d", suspending_);
    /* Terminate with reason: Connection Terminated By Local Host */
    IsoManager::GetInstance()->TerminateBig(GetAdvertisingSid(), 0x16);
  }

  void OnSetupIsoDataPath(uint8_t status, uint16_t conn_hdl) override {
    LOG_ASSERT(active_config_ != std::nullopt);

    if (status != 0) {
      LOG_ERROR("Failure creating data path. Tearing down the BIG now.");
      suspending_ = true;
      TerminateBig();
      return;
    }

    /* Look for the next BIS handle */
    auto handle_it = std::find_if(
        active_config_->connection_handles.begin(),
        active_config_->connection_handles.end(),
        [conn_hdl](const auto& handle) { return conn_hdl == handle; });
    LOG_ASSERT(handle_it != active_config_->connection_handles.end());
    handle_it = std::next(handle_it);

    if (handle_it == active_config_->connection_handles.end()) {
      /* It was the last BIS to set up - change state to streaming */
      SetState(State::STREAMING);
      callbacks_->OnStateMachineEvent(
          GetBroadcastId(), GetState(),
          &sm_config_.codec_wrapper.GetLeAudioCodecConfiguration());
    } else {
      /* Note: We would feed a watchdog here if we had one */
      /* There are more BISes to set up data path for */
      LOG_INFO("There is more data paths to set up.");
      TriggerIsoDatapathSetup(*handle_it);
    }
  }

  void OnRemoveIsoDataPath(uint8_t status, uint16_t conn_handle) override {
    LOG_ASSERT(active_config_ != std::nullopt);

    if (status != 0) {
      LOG_ERROR("Failure removing data path. Tearing down the BIG now.");
      TerminateBig();
      return;
    }

    /* Look for the next BIS handle */
    auto handle_it = std::find_if(
        active_config_->connection_handles.begin(),
        active_config_->connection_handles.end(),
        [conn_handle](const auto& handle) { return conn_handle == handle; });
    LOG_ASSERT(handle_it != active_config_->connection_handles.end());
    handle_it = std::next(handle_it);

    if (handle_it == active_config_->connection_handles.end()) {
      /* It was the last one to set up - start tearing down the BIG */
      TerminateBig();
    } else {
      /* Note: We would feed a watchdog here if we had one */
      /* There are more BISes to tear down data path for */
      LOG_INFO("There is more data paths to tear down.");
      TriggerIsoDatapathTeardown(*handle_it);
    }
  }

  void TriggerIsoDatapathSetup(uint16_t conn_handle) {
    LOG_INFO("conn_hdl=%d", conn_handle);
    LOG_ASSERT(active_config_ != std::nullopt);
    auto data_path_id = bluetooth::hci::iso_manager::kIsoDataPathHci;
    if (CodecManager::GetInstance()->GetCodecLocation() !=
        CodecLocation::HOST) {
      data_path_id = bluetooth::hci::iso_manager::kIsoDataPathPlatformDefault;
    }

    /* Note: If the LC3 encoding isn't in the controller side, the coding format
     * should be set to 'Transparent' and no codec configuration shall be sent
     * to the controller. 'codec_id_company' and 'codec_id_vendor' shall be
     * ignored if 'codec_id_format' is not set to 'Vendor'. We currently only
     * support the codecLocation in the Host or ADSP side.
     */
    auto codec_id = sm_config_.codec_wrapper.GetLeAudioCodecId();
    uint8_t hci_coding_format =
        (codec_id.coding_format == le_audio::types::kLeAudioCodingFormatLC3)
            ? bluetooth::hci::kIsoCodingFormatTransparent
            : bluetooth::hci::kIsoCodingFormatVendorSpecific;
    bluetooth::hci::iso_manager::iso_data_path_params param = {
        .data_path_dir = bluetooth::hci::iso_manager::kIsoDataPathDirectionIn,
        .data_path_id = data_path_id,
        .codec_id_format = hci_coding_format,
        .codec_id_company = codec_id.vendor_company_id,
        .codec_id_vendor = codec_id.vendor_codec_id,
        /* TODO: Implement HCI command to get the controller delay */
        .controller_delay = 0x00000000,
    };
    if (codec_id.coding_format != le_audio::types::kLeAudioCodingFormatLC3) {
      // TODO: Until the proper offloader support is added, pass all the params
      auto const& conn_handles = active_config_->connection_handles;

      auto it =
          std::find(conn_handles.begin(), conn_handles.end(), conn_handle);
      if (it != conn_handles.end()) {
        /* Find BIS index - BIS indices start at 1 */
        auto bis_idx = it - conn_handles.begin() + 1;

        /* Compose subgroup params with BIS params  */
        auto params = sm_config_.codec_wrapper.GetSubgroupCodecSpecData();
        params.Append(sm_config_.codec_wrapper.GetBisCodecSpecData(bis_idx));
        param.codec_conf = params.RawPacket();
      }
    }

    IsoManager::GetInstance()->SetupIsoDataPath(conn_handle, std::move(param));
  }

  void TriggerIsoDatapathTeardown(uint16_t conn_handle) {
    LOG_INFO("conn_hdl=%d", conn_handle);
    LOG_ASSERT(active_config_ != std::nullopt);

    SetMuted(true);
    IsoManager::GetInstance()->RemoveIsoDataPath(
        conn_handle,
        bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput);
  }

  void HandleHciEvent(uint16_t event, void* data) override {
    switch (event) {
      case HCI_BLE_CREATE_BIG_CPL_EVT: {
        auto* evt = static_cast<big_create_cmpl_evt*>(data);

        if (evt->big_id != GetAdvertisingSid()) {
          LOG_ERROR("State=%s, Event=%d, Unknown big, big_id=%d",
                    ToString(GetState()).c_str(), event, evt->big_id);
          break;
        }

        if (evt->status == 0x00) {
          LOG_INFO("BIG create BIG complete, big_id=%d", evt->big_id);
          active_config_ = {
              .status = evt->status,
              .big_id = evt->big_id,
              .big_sync_delay = evt->big_sync_delay,
              .transport_latency_big = evt->transport_latency_big,
              .phy = evt->phy,
              .nse = evt->nse,
              .bn = evt->bn,
              .pto = evt->pto,
              .irc = evt->irc,
              .max_pdu = evt->max_pdu,
              .iso_interval = evt->iso_interval,
              .connection_handles = evt->conn_handles,
          };
          if (CodecManager::GetInstance()->GetCodecLocation() ==
              CodecLocation::ADSP) {
            callbacks_->OnBigCreated(evt->conn_handles);
          }
          TriggerIsoDatapathSetup(evt->conn_handles[0]);
        } else {
          LOG_ERROR(
              "State=%s Event=%d. Unable to create big, big_id=%d, status=%d",
              ToString(GetState()).c_str(), event, evt->big_id, evt->status);
        }
      } break;
      case HCI_BLE_TERM_BIG_CPL_EVT: {
        auto* evt = static_cast<big_terminate_cmpl_evt*>(data);

        LOG_INFO("BIG terminate BIG cmpl, reason=%d big_id=%d", evt->reason,
                 evt->big_id);

        if (evt->big_id != GetAdvertisingSid()) {
          LOG_ERROR("State=%s Event=%d, unknown adv.sid=%d",
                    ToString(GetState()).c_str(), event, evt->big_id);
          break;
        }

        active_config_ = std::nullopt;

        /* Go back to configured if BIG is inactive (we are still announcing) */
        SetState(State::CONFIGURED);

        /* Check if we got this HCI event due to STOP or SUSPEND message. */
        if (suspending_) {
          callbacks_->OnStateMachineEvent(GetBroadcastId(), GetState(), evt);
          suspending_ = false;
        } else {
          DisableAnnouncement();
        }
      } break;
      default:
        LOG_ERROR("State=%s Unknown event=%d", ToString(GetState()).c_str(),
                  event);
        break;
    }
  }
};

IBroadcastStateMachineCallbacks* BroadcastStateMachineImpl::callbacks_ =
    nullptr;
BleAdvertiserInterface* BroadcastStateMachineImpl::advertiser_if_ = nullptr;
} /* namespace */

std::unique_ptr<BroadcastStateMachine> BroadcastStateMachine::CreateInstance(
    BroadcastStateMachineConfig msg) {
  return std::make_unique<BroadcastStateMachineImpl>(std::move(msg));
}

void BroadcastStateMachine::Initialize(
    IBroadcastStateMachineCallbacks* callbacks,
    AdvertisingCallbacks* adv_callbacks) {
  BroadcastStateMachineImpl::callbacks_ = callbacks;
  /* Get gd le advertiser interface */
  BroadcastStateMachineImpl::advertiser_if_ =
      bluetooth::shim::get_ble_advertiser_instance();
  if (BroadcastStateMachineImpl::advertiser_if_ != nullptr) {
    LOG_INFO("Advertiser_instance acquired");
    BroadcastStateMachineImpl::advertiser_if_->RegisterCallbacksNative(
        adv_callbacks, kAdvertiserClientIdLeAudio);
  } else {
    LOG_ERROR("Could not acquire advertiser_instance!");
    BroadcastStateMachineImpl::advertiser_if_ = nullptr;
  }
}

namespace le_audio {
namespace broadcaster {

std::ostream& operator<<(std::ostream& os,
                         const BroadcastStateMachine::Message& msg) {
  static const char* char_value_[BroadcastStateMachine::MESSAGE_COUNT] = {
      "START", "SUSPEND", "STOP"};
  os << char_value_[static_cast<uint8_t>(msg)];
  return os;
}

std::ostream& operator<<(std::ostream& os,
                         const BroadcastStateMachine::State& state) {
  static const char* char_value_[BroadcastStateMachine::STATE_COUNT] = {
      "STOPPED", "CONFIGURING", "CONFIGURED", "STOPPING", "STREAMING"};
  os << char_value_[static_cast<uint8_t>(state)];
  return os;
}

std::ostream& operator<<(std::ostream& os,
                         const le_audio::broadcaster::BigConfig& config) {
  os << "\n";
  os << "        Status: 0x" << std::hex << +config.status << std::dec << "\n";
  os << "        BIG ID: " << +config.big_id << "\n";
  os << "        Sync delay: " << config.big_sync_delay << "\n";
  os << "        Transport Latency: " << config.transport_latency_big << "\n";
  os << "        Phy: " << +config.phy << "\n";
  os << "        Nse: " << +config.nse << "\n";
  os << "        Bn: " << +config.bn << "\n";
  os << "        Pto: " << +config.pto << "\n";
  os << "        Irc: " << +config.irc << "\n";
  os << "        Max pdu: " << config.max_pdu << "\n";
  os << "        Iso interval: " << config.iso_interval << "\n";
  os << "        Connection handles (BISes): [";
  for (auto& el : config.connection_handles) {
    os << std::hex << +el << std::dec << ":";
  }
  os << "]";
  return os;
}

std::ostream& operator<<(
    std::ostream& os,
    const le_audio::broadcaster::BroadcastStateMachineConfig& config) {
  const char* const PHYS[] = {"NONE", "1M", "2M", "CODED"};

  os << "\n";
  os << "        Broadcast ID: " << config.broadcast_id << "\n";
  os << "        Streaming PHY: "
     << ((config.streaming_phy > 3) ? std::to_string(config.streaming_phy)
                                    : PHYS[config.streaming_phy])
     << "\n";
  os << "        Codec Wrapper: " << config.codec_wrapper << "\n";
  os << "        Qos Config: " << config.qos_config << "\n";
  if (config.broadcast_code) {
    os << "        Broadcast Code: [";
    for (auto& el : *config.broadcast_code) {
      os << std::hex << +el << ":";
    }
    os << "]\n";
  } else {
    os << "        Broadcast Code: NONE\n";
  }

  std::vector<uint8_t> an_raw;
  ToRawPacket(config.announcement, an_raw);
  os << "        Announcement RAW: [";
  for (auto& el : an_raw) {
    os << std::hex << +el << ":";
  }
  os << "]";

  return os;
}

std::ostream& operator<<(
    std::ostream& os,
    const le_audio::broadcaster::BroadcastStateMachine& machine) {
  os << "    Broadcast state machine: {"
     << "      Advertising SID: " << +machine.GetAdvertisingSid() << "\n"
     << "      State: " << machine.GetState() << "\n";
  os << "      State Machine Config: " << machine.GetStateMachineConfig()
     << "\n";

  if (machine.GetBigConfig()) {
    os << "      BigConfig: " << *machine.GetBigConfig() << "\n";
  } else {
    os << "      BigConfig: NONE\n";
  }
  os << "    }\n";
  return os;
}

}  // namespace broadcaster
}  // namespace le_audio
