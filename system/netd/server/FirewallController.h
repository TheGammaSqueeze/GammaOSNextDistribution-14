/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef _FIREWALL_CONTROLLER_H
#define _FIREWALL_CONTROLLER_H

#include <sys/types.h>
#include <mutex>
#include <set>
#include <string>
#include <vector>

#include "NetdConstants.h"

namespace android {
namespace net {

/*
 * Simple firewall that drops all packets except those matching explicitly
 * defined ALLOW rules.
 *
 * Methods in this class must be called when holding a write lock on |lock|, and may not call
 * any other controller without explicitly managing that controller's lock. There are currently
 * no such methods.
 */
class FirewallController {
public:
  FirewallController();

  int setupIptablesHooks(void);

  int setFirewallType(FirewallType);
  int resetFirewall(void);
  int isFirewallEnabled(void);

  /* Match traffic going in/out over the given iface. */
  int setInterfaceRule(const char*, FirewallRule);
  /* Match traffic owned by given UID. This is specific to a particular chain. */
  int setUidRule(ChildChain, int, FirewallRule);

  static std::string makeCriticalCommands(IptablesTarget target, const char* chainName);

  static const char* TABLE;

  static const char* LOCAL_INPUT;
  static const char* LOCAL_OUTPUT;
  static const char* LOCAL_FORWARD;

  static const char* ICMPV6_TYPES[];

  std::mutex lock;

protected:
  friend class FirewallControllerTest;
  static int (*execIptablesRestore)(IptablesTarget target, const std::string& commands);

private:
  FirewallType mFirewallType;
  std::set<std::string> mIfaceRules;
  int flushRules(void);
};

}  // namespace net
}  // namespace android

#endif
