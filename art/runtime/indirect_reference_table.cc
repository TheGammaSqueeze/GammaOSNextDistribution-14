/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include "indirect_reference_table-inl.h"

#include "base/bit_utils.h"
#include "base/globals.h"
#include "base/mutator_locked_dumpable.h"
#include "base/systrace.h"
#include "base/utils.h"
#include "indirect_reference_table.h"
#include "jni/java_vm_ext.h"
#include "jni/jni_internal.h"
#include "mirror/object-inl.h"
#include "nth_caller_visitor.h"
#include "object_callbacks.h"
#include "reference_table.h"
#include "runtime-inl.h"
#include "scoped_thread_state_change-inl.h"
#include "thread.h"

#include <cstdlib>

namespace art HIDDEN {

static constexpr bool kDebugIRT = false;

// Maximum table size we allow.
static constexpr size_t kMaxTableSizeInBytes = 128 * MB;

const char* GetIndirectRefKindString(IndirectRefKind kind) {
  switch (kind) {
    case kJniTransition:
      return "JniTransition";
    case kLocal:
      return "Local";
    case kGlobal:
      return "Global";
    case kWeakGlobal:
      return "WeakGlobal";
  }
  return "IndirectRefKind Error";
}

void IndirectReferenceTable::AbortIfNoCheckJNI(const std::string& msg) {
  // If -Xcheck:jni is on, it'll give a more detailed error before aborting.
  JavaVMExt* vm = Runtime::Current()->GetJavaVM();
  if (!vm->IsCheckJniEnabled()) {
    // Otherwise, we want to abort rather than hand back a bad reference.
    LOG(FATAL) << msg;
  } else {
    LOG(ERROR) << msg;
  }
}

// Mmap an "indirect ref table region. Table_bytes is a multiple of a page size.
static inline MemMap NewIRTMap(size_t table_bytes, std::string* error_msg) {
  MemMap result = MemMap::MapAnonymous("indirect ref table",
                                       table_bytes,
                                       PROT_READ | PROT_WRITE,
                                       /*low_4gb=*/ false,
                                       error_msg);
  if (!result.IsValid() && error_msg->empty()) {
      *error_msg = "Unable to map memory for indirect ref table";
  }
  return result;
}

IndirectReferenceTable::IndirectReferenceTable(IndirectRefKind kind)
    : table_mem_map_(),
      table_(nullptr),
      kind_(kind),
      top_index_(0u),
      max_entries_(0u),
      current_num_holes_(0) {
  CHECK_NE(kind, kJniTransition);
  CHECK_NE(kind, kLocal);
}

bool IndirectReferenceTable::Initialize(size_t max_count, std::string* error_msg) {
  CHECK(error_msg != nullptr);

  // Overflow and maximum check.
  CHECK_LE(max_count, kMaxTableSizeInBytes / sizeof(IrtEntry));

  const size_t table_bytes = RoundUp(max_count * sizeof(IrtEntry), gPageSize);
  table_mem_map_ = NewIRTMap(table_bytes, error_msg);
  if (!table_mem_map_.IsValid()) {
    DCHECK(!error_msg->empty());
    return false;
  }

  table_ = reinterpret_cast<IrtEntry*>(table_mem_map_.Begin());
  // Take into account the actual length.
  max_entries_ = table_bytes / sizeof(IrtEntry);
  return true;
}

IndirectReferenceTable::~IndirectReferenceTable() {
}

void IndirectReferenceTable::ConstexprChecks() {
  // Use this for some assertions. They can't be put into the header as C++ wants the class
  // to be complete.

  // Check kind.
  static_assert((EncodeIndirectRefKind(kLocal) & (~kKindMask)) == 0, "Kind encoding error");
  static_assert((EncodeIndirectRefKind(kGlobal) & (~kKindMask)) == 0, "Kind encoding error");
  static_assert((EncodeIndirectRefKind(kWeakGlobal) & (~kKindMask)) == 0, "Kind encoding error");
  static_assert(DecodeIndirectRefKind(EncodeIndirectRefKind(kLocal)) == kLocal,
                "Kind encoding error");
  static_assert(DecodeIndirectRefKind(EncodeIndirectRefKind(kGlobal)) == kGlobal,
                "Kind encoding error");
  static_assert(DecodeIndirectRefKind(EncodeIndirectRefKind(kWeakGlobal)) == kWeakGlobal,
                "Kind encoding error");

  // Check serial.
  static_assert(DecodeSerial(EncodeSerial(0u)) == 0u, "Serial encoding error");
  static_assert(DecodeSerial(EncodeSerial(1u)) == 1u, "Serial encoding error");
  static_assert(DecodeSerial(EncodeSerial(2u)) == 2u, "Serial encoding error");
  static_assert(DecodeSerial(EncodeSerial(3u)) == 3u, "Serial encoding error");

  // Table index.
  static_assert(DecodeIndex(EncodeIndex(0u)) == 0u, "Index encoding error");
  static_assert(DecodeIndex(EncodeIndex(1u)) == 1u, "Index encoding error");
  static_assert(DecodeIndex(EncodeIndex(2u)) == 2u, "Index encoding error");
  static_assert(DecodeIndex(EncodeIndex(3u)) == 3u, "Index encoding error");

  // Distinguishing between local and (weak) global references.
  static_assert((GetGlobalOrWeakGlobalMask() & EncodeIndirectRefKind(kJniTransition)) == 0u);
  static_assert((GetGlobalOrWeakGlobalMask() & EncodeIndirectRefKind(kLocal)) == 0u);
  static_assert((GetGlobalOrWeakGlobalMask() & EncodeIndirectRefKind(kGlobal)) != 0u);
  static_assert((GetGlobalOrWeakGlobalMask() & EncodeIndirectRefKind(kWeakGlobal)) != 0u);
}

// Holes:
//
// To keep the IRT compact, we want to fill "holes" created by non-stack-discipline Add & Remove
// operation sequences. For simplicity and lower memory overhead, we do not use a free list or
// similar. Instead, we scan for holes, with the expectation that we will find holes fast as they
// are usually near the end of the table (see the header, TODO: verify this assumption). To avoid
// scans when there are no holes, the number of known holes should be tracked.

static size_t CountNullEntries(const IrtEntry* table, size_t to) {
  size_t count = 0;
  for (size_t index = 0u; index != to; ++index) {
    if (table[index].GetReference()->IsNull()) {
      count++;
    }
  }
  return count;
}

ALWAYS_INLINE
static inline void CheckHoleCount(IrtEntry* table,
                                  size_t exp_num_holes,
                                  size_t top_index) {
  if (kIsDebugBuild) {
    size_t count = CountNullEntries(table, top_index);
    CHECK_EQ(exp_num_holes, count) << " topIndex=" << top_index;
  }
}

IndirectRef IndirectReferenceTable::Add(ObjPtr<mirror::Object> obj, std::string* error_msg) {
  if (kDebugIRT) {
    LOG(INFO) << "+++ Add: top_index=" << top_index_
              << " holes=" << current_num_holes_;
  }

  CHECK(obj != nullptr);
  VerifyObject(obj);
  DCHECK(table_ != nullptr);

  if (top_index_ == max_entries_) {
    // TODO: Fill holes before reporting error.
    std::ostringstream oss;
    oss << "JNI ERROR (app bug): " << kind_ << " table overflow "
        << "(max=" << max_entries_ << ")"
        << MutatorLockedDumpable<IndirectReferenceTable>(*this);
    *error_msg = oss.str();
    return nullptr;
  }

  CheckHoleCount(table_, current_num_holes_, top_index_);

  // We know there's enough room in the table.  Now we just need to find
  // the right spot.  If there's a hole, find it and fill it; otherwise,
  // add to the end of the list.
  IndirectRef result;
  size_t index;
  if (current_num_holes_ > 0) {
    DCHECK_GT(top_index_, 1U);
    // Find the first hole; likely to be near the end of the list.
    IrtEntry* p_scan = &table_[top_index_ - 1];
    DCHECK(!p_scan->GetReference()->IsNull());
    --p_scan;
    while (!p_scan->GetReference()->IsNull()) {
      DCHECK_GT(p_scan, table_);
      --p_scan;
    }
    index = p_scan - table_;
    current_num_holes_--;
  } else {
    // Add to the end.
    index = top_index_;
    ++top_index_;
  }
  table_[index].Add(obj);
  result = ToIndirectRef(index);
  if (kDebugIRT) {
    LOG(INFO) << "+++ added at " << ExtractIndex(result) << " top=" << top_index_
              << " holes=" << current_num_holes_;
  }

  DCHECK(result != nullptr);
  return result;
}

// Removes an object. We extract the table offset bits from "iref"
// and zap the corresponding entry, leaving a hole if it's not at the top.
// Returns "false" if nothing was removed.
bool IndirectReferenceTable::Remove(IndirectRef iref) {
  if (kDebugIRT) {
    LOG(INFO) << "+++ Remove: top_index=" << top_index_
              << " holes=" << current_num_holes_;
  }

  // TODO: We should eagerly check the ref kind against the `kind_` instead of postponing until
  // `CheckEntry()` below. Passing the wrong kind shall currently result in misleading warnings.

  const uint32_t top_index = top_index_;

  DCHECK(table_ != nullptr);

  const uint32_t idx = ExtractIndex(iref);
  if (idx >= top_index) {
    // Bad --- stale reference?
    LOG(WARNING) << "Attempt to remove invalid index " << idx
                 << " (top=" << top_index << ")";
    return false;
  }

  CheckHoleCount(table_, current_num_holes_, top_index_);

  if (idx == top_index - 1) {
    // Top-most entry.  Scan up and consume holes.

    if (!CheckEntry("remove", iref, idx)) {
      return false;
    }

    *table_[idx].GetReference() = GcRoot<mirror::Object>(nullptr);
    if (current_num_holes_ != 0) {
      uint32_t collapse_top_index = top_index;
      while (--collapse_top_index > 0u && current_num_holes_ != 0) {
        if (kDebugIRT) {
          ScopedObjectAccess soa(Thread::Current());
          LOG(INFO) << "+++ checking for hole at " << collapse_top_index - 1 << " val="
                    << table_[collapse_top_index - 1].GetReference()->Read<kWithoutReadBarrier>();
        }
        if (!table_[collapse_top_index - 1].GetReference()->IsNull()) {
          break;
        }
        if (kDebugIRT) {
          LOG(INFO) << "+++ ate hole at " << (collapse_top_index - 1);
        }
        current_num_holes_--;
      }
      top_index_ = collapse_top_index;

      CheckHoleCount(table_, current_num_holes_, top_index_);
    } else {
      top_index_ = top_index - 1;
      if (kDebugIRT) {
        LOG(INFO) << "+++ ate last entry " << top_index - 1;
      }
    }
  } else {
    // Not the top-most entry.  This creates a hole.  We null out the entry to prevent somebody
    // from deleting it twice and screwing up the hole count.
    if (table_[idx].GetReference()->IsNull()) {
      LOG(INFO) << "--- WEIRD: removing null entry " << idx;
      return false;
    }
    if (!CheckEntry("remove", iref, idx)) {
      return false;
    }

    *table_[idx].GetReference() = GcRoot<mirror::Object>(nullptr);
    current_num_holes_++;
    CheckHoleCount(table_, current_num_holes_, top_index_);
    if (kDebugIRT) {
      LOG(INFO) << "+++ left hole at " << idx << ", holes=" << current_num_holes_;
    }
  }

  return true;
}

void IndirectReferenceTable::Trim() {
  ScopedTrace trace(__PRETTY_FUNCTION__);
  DCHECK(table_mem_map_.IsValid());
  const size_t top_index = Capacity();
  uint8_t* release_start = AlignUp(reinterpret_cast<uint8_t*>(&table_[top_index]), gPageSize);
  uint8_t* release_end = static_cast<uint8_t*>(table_mem_map_.BaseEnd());
  DCHECK_GE(reinterpret_cast<uintptr_t>(release_end), reinterpret_cast<uintptr_t>(release_start));
  DCHECK_ALIGNED_PARAM(release_end, gPageSize);
  DCHECK_ALIGNED_PARAM(release_end - release_start, gPageSize);
  if (release_start != release_end) {
    madvise(release_start, release_end - release_start, MADV_DONTNEED);
  }
}

void IndirectReferenceTable::VisitRoots(RootVisitor* visitor, const RootInfo& root_info) {
  BufferedRootVisitor<kDefaultBufferedRootCount> root_visitor(visitor, root_info);
  for (size_t i = 0, capacity = Capacity(); i != capacity; ++i) {
    GcRoot<mirror::Object>* ref = table_[i].GetReference();
    if (!ref->IsNull()) {
      root_visitor.VisitRoot(*ref);
      DCHECK(!ref->IsNull());
    }
  }
}

void IndirectReferenceTable::SweepJniWeakGlobals(IsMarkedVisitor* visitor) {
  CHECK_EQ(kind_, kWeakGlobal);
  MutexLock mu(Thread::Current(), *Locks::jni_weak_globals_lock_);
  Runtime* const runtime = Runtime::Current();
  for (size_t i = 0, capacity = Capacity(); i != capacity; ++i) {
    GcRoot<mirror::Object>* entry = table_[i].GetReference();
    // Need to skip null here to distinguish between null entries and cleared weak ref entries.
    if (!entry->IsNull()) {
      mirror::Object* obj = entry->Read<kWithoutReadBarrier>();
      mirror::Object* new_obj = visitor->IsMarked(obj);
      if (new_obj == nullptr) {
        new_obj = runtime->GetClearedJniWeakGlobal();
      }
      *entry = GcRoot<mirror::Object>(new_obj);
    }
  }
}

void IndirectReferenceTable::Dump(std::ostream& os) const {
  os << kind_ << " table dump:\n";
  ReferenceTable::Table entries;
  for (size_t i = 0; i < Capacity(); ++i) {
    ObjPtr<mirror::Object> obj = table_[i].GetReference()->Read<kWithoutReadBarrier>();
    if (obj != nullptr) {
      obj = table_[i].GetReference()->Read();
      entries.push_back(GcRoot<mirror::Object>(obj));
    }
  }
  ReferenceTable::Dump(os, entries);
}

size_t IndirectReferenceTable::FreeCapacity() const {
  return max_entries_ - top_index_;
}

}  // namespace art
