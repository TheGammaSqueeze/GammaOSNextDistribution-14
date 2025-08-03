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

#include "dumpsys/reflection_schema.h"

#include <string>

#include "bundler_schema_generated.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/idl.h"
#include "os/log.h"

using namespace bluetooth;

dumpsys::ReflectionSchema::ReflectionSchema(const std::string& pre_bundled_schema)
    : pre_bundled_schema_(pre_bundled_schema) {
  bundled_schema_ = flatbuffers::GetRoot<bluetooth::dumpsys::BundledSchema>(pre_bundled_schema_.data());
  ASSERT(bundled_schema_ != nullptr);
}

int dumpsys::ReflectionSchema::GetNumberOfBundledSchemas() const {
  return bundled_schema_->map()->size();
}

std::string dumpsys::ReflectionSchema::GetTitle() const {
  return bundled_schema_->title()->str();
}

std::string dumpsys::ReflectionSchema::GetRootName() const {
  return bundled_schema_->root_name()->str();
}

const reflection::Schema* dumpsys::ReflectionSchema::GetRootReflectionSchema() const {
  return FindInReflectionSchema(GetRootName());
}

const reflection::Schema* dumpsys::ReflectionSchema::FindInReflectionSchema(const std::string& name) const {
  const flatbuffers::Vector<flatbuffers::Offset<bluetooth::dumpsys::BundledSchemaMap>>* map = bundled_schema_->map();

  for (auto it = map->cbegin(); it != map->cend(); ++it) {
    if (it->name()->str() == name) {
      flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t*>(it->data()->Data()), it->data()->size());
      if (!reflection::VerifySchemaBuffer(verifier)) {
        LOG_WARN("Unable to verify schema buffer name:%s", name.c_str());
        return nullptr;
      }
      return reflection::GetSchema(it->data()->Data());
    }
  }
  return nullptr;
}

void dumpsys::ReflectionSchema::PrintReflectionSchema() const {
  const flatbuffers::Vector<flatbuffers::Offset<bluetooth::dumpsys::BundledSchemaMap>>* map = bundled_schema_->map();
  LOG_INFO(
      "  Bundled schema title:%s root_name:%s",
      bundled_schema_->title()->c_str(),
      bundled_schema_->root_name()->c_str());
  for (auto it = map->cbegin(); it != map->cend(); ++it) {
    LOG_INFO("    schema:%s", it->name()->c_str());
  }
}

bool dumpsys::ReflectionSchema::VerifyReflectionSchema() const {
  const flatbuffers::Vector<flatbuffers::Offset<bluetooth::dumpsys::BundledSchemaMap>>* map = bundled_schema_->map();

  for (auto it = map->cbegin(); it != map->cend(); ++it) {
    flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t*>(it->data()->Data()), it->data()->size());
    if (!reflection::VerifySchemaBuffer(verifier)) {
      return false;
    }
  }
  return true;
}
