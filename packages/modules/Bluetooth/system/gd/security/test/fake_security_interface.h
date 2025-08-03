/*
 *
 *  Copyright 2020 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
#include "l2cap/classic/link_security_interface.h"
#include "os/handler.h"

namespace bluetooth {
namespace security {

class FakeLinkSecurityInterface : public l2cap::classic::LinkSecurityInterface {
 public:
  FakeLinkSecurityInterface(l2cap::classic::LinkSecurityInterfaceListener* listener, hci::Address address)
      : listener_(listener), address_(address) {}

  hci::Address GetRemoteAddress() {
    return address_;
  }
  void Hold() override {}
  void EnsureAuthenticated() override{};

  void EnsureEncrypted() override {}

  void Release() override {
    // TODO(optedoblivion): Simulate the delay
    listener_->OnLinkDisconnected(address_);
  }
  void Disconnect() override {
    listener_->OnLinkDisconnected(address_);
  }
  uint16_t GetAclHandle() override {
    return 0;
  }

 private:
  l2cap::classic::LinkSecurityInterfaceListener* listener_ = nullptr;
  hci::Address address_;
};

class FakeSecurityInterface : public l2cap::classic::SecurityInterface {
 public:
  FakeSecurityInterface(os::Handler* handler, l2cap::classic::LinkSecurityInterfaceListener* listener)
      : handler_(handler), listener_(listener) {}
  ~FakeSecurityInterface() {}
  void InitiateConnectionForSecurity(hci::Address remote) override {
    listener_->OnLinkConnected(std::make_unique<FakeLinkSecurityInterface>(listener_, remote));
  };
  void Unregister() override {}

 private:
  os::Handler* handler_ __attribute__((unused));
  l2cap::classic::LinkSecurityInterfaceListener* listener_ __attribute__((unused));
};

}  // namespace security
}  // namespace bluetooth
