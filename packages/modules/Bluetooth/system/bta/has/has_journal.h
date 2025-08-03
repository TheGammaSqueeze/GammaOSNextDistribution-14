/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <sys/time.h>
#include <time.h>

#include <list>
#include <variant>

#include "common/time_util.h"
#include "has_ctp.h"

/* Journal and journal entry classes used by the state dumping functionality. */
namespace le_audio {
namespace has {
static constexpr uint8_t kHasJournalNumRecords = 20;

struct HasJournalRecord {
  /* Indicates which value the `event` contains (due to ambiguous uint8_t) */
  bool is_operation : 1, is_notification : 1, is_features_change : 1,
      is_active_preset_change : 1;
  std::variant<HasCtpOp, HasCtpNtf, uint8_t> event;
  struct timespec timestamp;

  /* Operation context handle to match on GATT write response */
  void* op_context_handle;

  /* Status of the operation to be set once it gets completed */
  uint8_t op_status;

  HasJournalRecord(const HasCtpOp& op, void* context)
      : event(op), op_context_handle(context) {
    clock_gettime(CLOCK_REALTIME, &timestamp);
    is_operation = true;
    is_notification = false;
    is_features_change = false;
    is_active_preset_change = false;
  }

  HasJournalRecord(const HasCtpNtf& ntf) : event(ntf) {
    clock_gettime(CLOCK_REALTIME, &timestamp);
    is_operation = false;
    is_notification = true;
    is_features_change = false;
    is_active_preset_change = false;
  }

  HasJournalRecord(uint8_t value, bool is_feat_change) : event(value) {
    clock_gettime(CLOCK_REALTIME, &timestamp);
    is_operation = false;
    is_notification = false;
    if (is_feat_change) {
      is_active_preset_change = false;
      is_features_change = true;
    } else {
      is_active_preset_change = true;
      is_features_change = false;
    }
  }
};
std::ostream& operator<<(std::ostream& os, const HasJournalRecord& r);

template <class valT, size_t cache_max>
class CacheList {
 public:
  valT& Append(valT data) {
    items_.push_front(std::move(data));

    if (items_.size() > cache_max) {
      items_.pop_back();
    }

    return items_.front();
  }

  using iterator = typename std::list<valT>::iterator;
  iterator begin(void) { return items_.begin(); }
  iterator end(void) { return items_.end(); }

  using const_iterator = typename std::list<valT>::const_iterator;
  const_iterator begin(void) const { return items_.begin(); }
  const_iterator end(void) const { return items_.end(); }

  void Erase(iterator it) {
    if (it != items_.end()) items_.erase(it);
  }

  void Clear(void) { items_.clear(); }
  bool isEmpty(void) { return items_.empty(); }

 private:
  typename std::list<valT> items_;
};

using HasJournal = CacheList<HasJournalRecord, kHasJournalNumRecords>;
}  // namespace has
}  // namespace le_audio
