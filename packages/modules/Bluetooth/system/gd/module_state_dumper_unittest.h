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

#include <memory>
#include <string>

#include "module.h"
#include "module_mainloop.h"

using namespace bluetooth;

class StateDumperTestModule : public Module, public ModuleMainloop {
 public:
  static const bluetooth::ModuleFactory Factory;

 protected:
  bool IsStarted() const;

  void ListDependencies(bluetooth::ModuleList* /* list */) const override {}
  void Start() override;
  void Stop() override;
  std::string ToString() const override;

  DumpsysDataFinisher GetDumpsysData(flatbuffers::FlatBufferBuilder* builder) const override;
  void GetDumpsysData() const override;
  void GetDumpsysData(int fd) const override;
  void GetDumpsysData(std::ostringstream& oss) const override;

 private:
  struct PrivateImpl;
  std::shared_ptr<StateDumperTestModule::PrivateImpl> pimpl_;

  bool started_ = false;

  friend bluetooth::ModuleRegistry;
};
