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

#pragma once

#include "audio/asrc/asrc_resampler.h"
#include "hci_hal.h"
#include "module.h"

namespace bluetooth::hal {

class NocpIsoEvents : public bluetooth::audio::asrc::ClockSource {
 public:
  NocpIsoEvents() = default;
  ~NocpIsoEvents() override;

  void Bind(bluetooth::audio::asrc::ClockHandler*) override;
};

class L2capCreditIndEvents : public bluetooth::audio::asrc::ClockSource {
 public:
  L2capCreditIndEvents() {}
  ~L2capCreditIndEvents() override;

  void Bind(bluetooth::audio::asrc::ClockHandler*) override;
  void Update(int link_id, uint16_t connection_handle, uint16_t stream_cid);
};

class LinkClocker : public ::bluetooth::Module {
 public:
  static const ModuleFactory Factory;

  void OnHciEvent(const HciPacket& packet);
  void OnAclDataReceived(const HciPacket& packet);

 protected:
  void ListDependencies(ModuleList*) const override{};
  void Start() override{};
  void Stop() override{};

  std::string ToString() const override {
    return std::string("LinkClocker");
  }

  LinkClocker();

 private:
  int cig_id_;
  int cis_handle_;
};

}  // namespace bluetooth::hal
