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

#pragma once

#include <gmock/gmock.h>

#include "state_machine.h"

class MockBroadcastStateMachine
    : public le_audio::broadcaster::BroadcastStateMachine {
 public:
  MockBroadcastStateMachine(
      le_audio::broadcaster::BroadcastStateMachineConfig cfg,
      le_audio::broadcaster::IBroadcastStateMachineCallbacks* cb,
      AdvertisingCallbacks* adv_cb)
      : cfg(cfg), cb(cb), adv_cb(adv_cb) {
    advertising_sid_ = ++instance_counter_;

    ON_CALL(*this, Initialize).WillByDefault([this]() {
      this->cb->OnStateMachineCreateStatus(this->cfg.broadcast_id, result_);
      return result_;
    });

    ON_CALL(*this, ProcessMessage)
        .WillByDefault(
            [this](le_audio::broadcaster::BroadcastStateMachine::Message event,
                   const void* data) {
              const void* sent_data = nullptr;
              switch (event) {
                case Message::START:
                  if (result_) SetState(State::STREAMING);
                  sent_data =
                      &this->cfg.codec_wrapper.GetLeAudioCodecConfiguration();
                  break;
                case Message::STOP:
                  if (result_) SetState(State::STOPPED);
                  break;
                case Message::SUSPEND:
                  if (result_) SetState(State::CONFIGURED);
                  break;
              };
              this->cb->OnStateMachineEvent(this->cfg.broadcast_id, GetState(),
                                            sent_data);
            });

    ON_CALL(*this, GetBigConfig).WillByDefault(testing::ReturnRef(big_config_));

    ON_CALL(*this, RequestOwnAddress()).WillByDefault([this]() {
      this->cb->OnOwnAddressResponse(this->cfg.broadcast_id, 0, RawAddress());
    });

    ON_CALL(*this, GetCodecConfig())
        .WillByDefault(
            [this]() -> const le_audio::broadcaster::BroadcastCodecWrapper& {
              return this->cfg.codec_wrapper;
            });

    ON_CALL(*this, GetBroadcastId())
        .WillByDefault([this]() -> bluetooth::le_audio::BroadcastId {
          return this->cfg.broadcast_id;
        });

    ON_CALL(*this, GetOwnAddress()).WillByDefault([this]() -> RawAddress {
      return this->addr_;
    });

    ON_CALL(*this, GetOwnAddressType()).WillByDefault([this]() -> uint8_t {
      return this->addr_type_;
    });

    ON_CALL(*this, GetPaInterval()).WillByDefault([this]() -> uint8_t {
      return this->BroadcastStateMachine::GetPaInterval();
    });

    ON_CALL(*this, IsPublicBroadcast()).WillByDefault([this]() -> bool {
      return this->cfg.is_public;
    });

    ON_CALL(*this, GetBroadcastName()).WillByDefault([this]() -> std::string {
      return this->cfg.broadcast_name;
    });

    ON_CALL(*this, GetPublicBroadcastAnnouncement())
        .WillByDefault(
            [this]() -> bluetooth::le_audio::PublicBroadcastAnnouncementData& {
              return this->cfg.public_announcement;
            });
  };

  ~MockBroadcastStateMachine() {
    cb->OnStateMachineDestroyed(this->cfg.broadcast_id);
  }

  MOCK_METHOD((bool), Initialize, (), (override));
  MOCK_METHOD((const le_audio::broadcaster::BroadcastCodecWrapper&),
              GetCodecConfig, (), (const override));
  MOCK_METHOD((std::optional<le_audio::broadcaster::BigConfig> const&),
              GetBigConfig, (), (const override));
  MOCK_METHOD((le_audio::broadcaster::BroadcastStateMachineConfig const&),
              GetStateMachineConfig, (), (const override));
  MOCK_METHOD(
      (void), RequestOwnAddress,
      (base::Callback<void(uint8_t /* address_type*/, RawAddress /*address*/)>
           cb),
      (override));
  MOCK_METHOD((void), RequestOwnAddress, (), (override));
  MOCK_METHOD((RawAddress), GetOwnAddress, (), (override));
  MOCK_METHOD((uint8_t), GetOwnAddressType, (), (override));
  MOCK_METHOD((std::optional<bluetooth::le_audio::BroadcastCode>),
              GetBroadcastCode, (), (const override));
  MOCK_METHOD((bluetooth::le_audio::BroadcastId), GetBroadcastId, (),
              (const override));
  MOCK_METHOD((bool), IsPublicBroadcast, (), (override));
  MOCK_METHOD((std::string), GetBroadcastName, (), (override));
  MOCK_METHOD((bluetooth::le_audio::BasicAudioAnnouncementData&),
              GetBroadcastAnnouncement, (), (const override));
  MOCK_METHOD((bluetooth::le_audio::PublicBroadcastAnnouncementData&),
              GetPublicBroadcastAnnouncement, (), (const override));
  MOCK_METHOD((void), UpdateBroadcastAnnouncement,
              (bluetooth::le_audio::BasicAudioAnnouncementData announcement),
              (override));
  MOCK_METHOD((void), UpdatePublicBroadcastAnnouncement,
              (uint32_t broadcast_id, const std::string& broadcast_name,
               const bluetooth::le_audio::PublicBroadcastAnnouncementData&
                   announcement),
              (override));
  MOCK_METHOD((uint8_t), GetPaInterval, (), (const override));
  MOCK_METHOD((void), HandleHciEvent, (uint16_t event, void* data), (override));
  MOCK_METHOD((void), OnSetupIsoDataPath,
              (uint8_t status, uint16_t conn_handle), (override));
  MOCK_METHOD((void), OnRemoveIsoDataPath,
              (uint8_t status, uint16_t conn_handle), (override));
  MOCK_METHOD((void), ProcessMessage,
              (le_audio::broadcaster::BroadcastStateMachine::Message event,
               const void* data),
              (override));
  MOCK_METHOD((uint8_t), GetAdvertisingSid, (), (const override));
  MOCK_METHOD((void), OnCreateAnnouncement,
              (uint8_t advertising_sid, int8_t tx_power, uint8_t status),
              (override));
  MOCK_METHOD((void), OnEnableAnnouncement, (bool enable, uint8_t status),
              (override));

  bool result_ = true;
  std::optional<le_audio::broadcaster::BigConfig> big_config_ = std::nullopt;
  le_audio::broadcaster::BroadcastStateMachineConfig cfg;
  le_audio::broadcaster::IBroadcastStateMachineCallbacks* cb;
  AdvertisingCallbacks* adv_cb;
  void SetExpectedState(BroadcastStateMachine::State state) { SetState(state); }
  void SetExpectedResult(bool result) { result_ = result; }
  void SetExpectedBigConfig(
      std::optional<le_audio::broadcaster::BigConfig> big_cfg) {
    big_config_ = big_cfg;
  }

  static MockBroadcastStateMachine* last_instance_;
  static uint8_t instance_counter_;
  static MockBroadcastStateMachine* GetLastInstance() { return last_instance_; }
};
