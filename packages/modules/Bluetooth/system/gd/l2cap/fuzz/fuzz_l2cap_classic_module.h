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
// Authors: corbin.souffrant@leviathansecurity.com
//          dylan.katz@leviathansecurity.com

#pragma once

#include <fuzzer/FuzzedDataProvider.h>

#include "l2cap/classic/dynamic_channel_manager.h"
#include "l2cap/classic/l2cap_classic_module.h"

#include "fuzz_dynamic_channel_manager.h"
#include "fuzz_dynamic_channel_manager_impl.h"

namespace bluetooth {
namespace shim {
namespace {
class FuzzL2capClassicModule : public l2cap::classic::L2capClassicModule {
 public:
  std::unique_ptr<l2cap::classic::DynamicChannelManager> GetDynamicChannelManager() override {
    return std::make_unique<FuzzDynamicChannelManager>(*impl_);
  }

  void ListDependencies(ModuleList*) const override {}
  void Start() override;
  void Stop() override;

  std::unique_ptr<FuzzDynamicChannelManagerImpl> impl_;
};

void FuzzL2capClassicModule::Start() {
  impl_ = std::make_unique<FuzzDynamicChannelManagerImpl>();
}

void FuzzL2capClassicModule::Stop() {
  impl_.reset();
}
}  // namespace
}  // namespace shim
}  // namespace bluetooth
