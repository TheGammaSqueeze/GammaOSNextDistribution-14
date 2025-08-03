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

#include <flatbuffers/flatbuffers.h>

#include <functional>
#include <sstream>

namespace bluetooth {

// flatbuffers uses structs over class definition
struct DumpsysDataBuilder;
using DumpsysDataFinisher = std::function<void(DumpsysDataBuilder* dumpsys_data_builder)>;

extern bluetooth::DumpsysDataFinisher EmptyDumpsysDataFinisher;

class ModuleStateDumper {
 public:
  virtual ~ModuleStateDumper() = default;

  // Get relevant state data from the module
  virtual DumpsysDataFinisher GetDumpsysData(flatbuffers::FlatBufferBuilder* builder) const;
  virtual void GetDumpsysData() const;
  virtual void GetDumpsysData(int fd) const;
  virtual void GetDumpsysData(std::ostringstream& oss) const;
};

}  // namespace bluetooth
