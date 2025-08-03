/*
 * Copyright 2020 The Android Open Source Project
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

#include <array>
#include <cstdint>
#include <memory>

#include "common/bind.h"
#include "hci/address.h"
#include "hci/hci_packets.h"
#include "module.h"

namespace bluetooth {
namespace neighbor {

using RemoteName = std::array<uint8_t, 248>;
using ReadRemoteNameDbCallback = common::OnceCallback<void(hci::Address address, bool success)>;

class NameDbModule : public bluetooth::Module {
 public:
  virtual void ReadRemoteNameRequest(hci::Address address, ReadRemoteNameDbCallback callback, os::Handler* handler);

  bool IsNameCached(hci::Address address) const;
  RemoteName ReadCachedRemoteName(hci::Address address) const;

  static const ModuleFactory Factory;

  NameDbModule();
  NameDbModule(const NameDbModule&) = delete;
  NameDbModule& operator=(const NameDbModule&) = delete;

  ~NameDbModule();

 protected:
  void ListDependencies(ModuleList* list) const override;
  void Start() override;
  void Stop() override;
  std::string ToString() const override {
    return std::string("NameDb");
  }

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;
};

}  // namespace neighbor
}  // namespace bluetooth
