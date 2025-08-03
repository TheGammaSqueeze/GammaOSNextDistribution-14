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

#include "storage/mutation_entry.h"

#include "os/log.h"

namespace bluetooth {
namespace storage {

MutationEntry::MutationEntry(
    EntryType entry_type_param,
    PropertyType property_type_param,
    std::string section_param,
    std::string property_param,
    std::string value_param)
    : entry_type(entry_type_param),
      property_type(property_type_param),
      section(std::move(section_param)),
      property(std::move(property_param)),
      value(std::move(value_param)) {
  switch (entry_type) {
    case EntryType::SET:
      ASSERT_LOG(!section.empty(), "section cannot be empty for EntryType::SET");
      ASSERT_LOG(!property.empty(), "property cannot be empty for EntryType::SET");
      ASSERT_LOG(!value.empty(), "value cannot be empty for EntryType::SET");
      break;
    case EntryType::REMOVE_PROPERTY:
      ASSERT_LOG(!section.empty(), "section cannot be empty for EntryType::REMOVE_PROPERTY");
      ASSERT_LOG(!property.empty(), "property cannot be empty for EntryType::REMOVE_PROPERTY");
      break;
    case EntryType::REMOVE_SECTION:
      ASSERT_LOG(!section.empty(), "section cannot be empty for EntryType::REMOVE_SECTION");
      break;
      // do not write a default case so that when a new enum is defined, compilation would fail automatically
  }
}

}  // namespace storage
}  // namespace bluetooth