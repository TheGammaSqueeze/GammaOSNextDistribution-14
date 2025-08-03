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

#include "storage/mutation.h"

#include "os/log.h"

namespace bluetooth {
namespace storage {

Mutation::Mutation(ConfigCache* config, ConfigCache* memory_only_config)
    : config_(config), memory_only_config_(memory_only_config) {
  ASSERT(config_ != nullptr);
  ASSERT(memory_only_config_ != nullptr);
}

void Mutation::Add(MutationEntry entry) {
  switch (entry.property_type) {
    case MutationEntry::PropertyType::NORMAL:
      if (entry.entry_type != MutationEntry::EntryType::SET) {
        // When an item is removed from normal config, it must be removed from temp config as well
        memory_only_config_entries_.emplace(entry);
      }
      normal_config_entries_.emplace(std::move(entry));
      break;
    case MutationEntry::PropertyType::MEMORY_ONLY:
      memory_only_config_entries_.emplace(std::move(entry));
      break;
      // do not write a default case so that when a new enum is defined, compilation would fail automatically
  }
}

void Mutation::Commit() {
  config_->Commit(normal_config_entries_);
  memory_only_config_->Commit(memory_only_config_entries_);
}

}  // namespace storage
}  // namespace bluetooth