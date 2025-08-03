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

#include "l2cap/le/dynamic_channel_manager.h"
#include "l2cap/le/fixed_channel_manager.h"
#include "l2cap/le/link_property_listener.h"
#include "l2cap/le/security_enforcement_interface.h"
#include "module.h"

namespace bluetooth {

namespace security {
class SecurityModule;
}

namespace l2cap {
namespace le {

class L2capLeModule : public bluetooth::Module {
 public:
  L2capLeModule();
  L2capLeModule(const L2capLeModule&) = delete;
  L2capLeModule& operator=(const L2capLeModule&) = delete;

  virtual ~L2capLeModule();

  /**
   * Get the api to the LE fixed channel l2cap module
   */
  virtual std::unique_ptr<FixedChannelManager> GetFixedChannelManager();

  /**
   * Get the api to the LE dynamic channel l2cap module
   */
  virtual std::unique_ptr<DynamicChannelManager> GetDynamicChannelManager();

  static const ModuleFactory Factory;

 protected:
  void ListDependencies(ModuleList* list) const override;

  void Start() override;

  void Stop() override;

  std::string ToString() const override;

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;

  friend security::SecurityModule;

  /**
   * Only for the LE security module to inject functionality to enforce security level for a connection. When LE
   * security module is stopping, inject nullptr. Note: We expect this only to be called during stack startup. This is
   * not synchronized.
   */
  virtual void InjectSecurityEnforcementInterface(SecurityEnforcementInterface* security_enforcement_interface);

  /**
   * Set the link property listener.
   * This is not synchronized.
   */
  virtual void SetLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener);
};

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
