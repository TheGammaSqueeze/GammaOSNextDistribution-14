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

#include <memory>

#include "l2cap/classic/dynamic_channel_manager.h"
#include "l2cap/classic/fixed_channel_manager.h"
#include "l2cap/classic/link_property_listener.h"
#include "l2cap/classic/link_security_interface.h"
#include "l2cap/classic/security_enforcement_interface.h"
#include "module.h"

namespace bluetooth {

namespace security {
class SecurityModule;
}

namespace l2cap {
namespace classic {

class L2capClassicModule : public bluetooth::Module {
 public:
  L2capClassicModule();
  L2capClassicModule(const L2capClassicModule&) = delete;
  L2capClassicModule& operator=(const L2capClassicModule&) = delete;

  virtual ~L2capClassicModule();

  /**
   * Get the api to the classic fixed channel l2cap module
   */
  virtual std::unique_ptr<FixedChannelManager> GetFixedChannelManager();

  /**
   * Get the api to the classic dynamic channel l2cap module
   */
  virtual std::unique_ptr<DynamicChannelManager> GetDynamicChannelManager();

  static const ModuleFactory Factory;
  /**
   * Only for the classic security module to inject functionality to enforce security level for a connection. When
   * classic security module is stopping, inject nullptr. Note: We expect this only to be called during stack startup.
   * This is not synchronized.
   */
  virtual void InjectSecurityEnforcementInterface(SecurityEnforcementInterface* security_enforcement_interface);

  /**
   * Get the interface for Security Module to access link function.
   * Security Module needs to register the callback for ACL link connected and disconnected. When connected, either by
   * incoming or by outgoing connection request, Security Module receives a LinkSecurityInterface proxy, which can be
   * used to access some link functionlities.
   */
  virtual SecurityInterface* GetSecurityInterface(os::Handler* handler, LinkSecurityInterfaceListener* listener);

  friend security::SecurityModule;

  /**
   * Set the link property listener.
   * This is not synchronized.
   */
  virtual void SetLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener);

 protected:
  void ListDependencies(ModuleList* list) const override;

  void Start() override;

  void Stop() override;

  std::string ToString() const override;

  DumpsysDataFinisher GetDumpsysData(flatbuffers::FlatBufferBuilder* builder) const override;  // Module

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;
};

}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
