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

#include <string>

#include "bundler_schema_generated.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/idl.h"

namespace bluetooth {
namespace dumpsys {

class ReflectionSchema {
 public:
  ReflectionSchema(const std::string& pre_bundled_schema);

  std::string GetTitle() const;
  std::string GetRootName() const;
  int GetNumberOfBundledSchemas() const;

  bool VerifyReflectionSchema() const;
  const reflection::Schema* GetRootReflectionSchema() const;
  const reflection::Schema* FindInReflectionSchema(const std::string& name) const;
  void PrintReflectionSchema() const;

 private:
  const BundledSchema* bundled_schema_;
  const std::string pre_bundled_schema_;
};

}  // namespace dumpsys
}  // namespace bluetooth
