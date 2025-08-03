/*
 *
 *  Copyright 2019 The Android Open Source Project
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

#include "neighbor/name_db.h"

namespace bluetooth {
namespace security {

class FakeNameDbModule : public neighbor::NameDbModule {
 public:
  FakeNameDbModule() {}

  void ListDependencies(ModuleList* /* list */) const override {}
  void Start() override {}
  void Stop() override {}
  std::string ToString() const override {
    return std::string("FakeNameDbModule");
  }

  void ReadRemoteNameRequest(
      hci::Address address, neighbor::ReadRemoteNameDbCallback callback, os::Handler* handler) override {
    handler->Call(std::move(callback), address, true);
  }

  bool IsNameCached(hci::Address /* address */) const {
    return true;
  }

  neighbor::RemoteName ReadCachedRemoteName(hci::Address /* address */) const {
    neighbor::RemoteName name = {'t', 'e', 's', 't'};
    return name;
  }
};

}  // namespace security
}  // namespace bluetooth
