/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "verification-inl.h"

#include <iomanip>
#include <sstream>

#include <android-base/unique_fd.h>

#include "art_field-inl.h"
#include "base/file_utils.h"
#include "base/logging.h"
#include "mirror/class-inl.h"
#include "mirror/object-refvisitor-inl.h"

namespace art HIDDEN {
namespace gc {

std::string Verification::DumpRAMAroundAddress(uintptr_t addr, uintptr_t bytes) const {
  uintptr_t* dump_start = reinterpret_cast<uintptr_t*>(addr - bytes);
  uintptr_t* dump_end = reinterpret_cast<uintptr_t*>(addr + bytes);
  std::ostringstream oss;
  oss << " adjacent_ram=";

  {
    // Check if the RAM is accessible.
    android::base::unique_fd read_fd, write_fd;
    if (!android::base::Pipe(&read_fd, &write_fd)) {
      LOG(WARNING) << "Could not create pipe, RAM being dumped may be unaccessible";
    } else {
      size_t count = 2 * bytes;
      if (write(write_fd.get(), dump_start, count) != static_cast<ssize_t>(count)) {
        oss << "unaccessible";
        dump_start = dump_end;
      }
    }
  }

  for (const uintptr_t* p = dump_start; p < dump_end; ++p) {
    if (p == reinterpret_cast<uintptr_t*>(addr)) {
      // Marker of where the address is.
      oss << "|";
    }
    oss << std::hex << std::setfill('0') << std::setw(sizeof(uintptr_t) * 2) << *p << " ";
  }
  return oss.str();
}

std::string Verification::DumpObjectInfo(const void* addr, const char* tag) const {
  std::ostringstream oss;
  oss << tag << "=" << addr;
  if (IsValidHeapObjectAddress(addr)) {
    mirror::Object* obj = reinterpret_cast<mirror::Object*>(const_cast<void*>(addr));
    mirror::Class* klass = obj->GetClass<kVerifyNone, kWithoutReadBarrier>();
    oss << " klass=" << klass;
    if (IsValidClass(klass)) {
      oss << "(" << klass->PrettyClass() << ")";
      if (klass->IsArrayClass<kVerifyNone>()) {
        oss << " length=" << obj->AsArray<kVerifyNone>()->GetLength();
      }
    } else {
      oss << " <invalid address>";
    }
    space::Space* const space = heap_->FindSpaceFromAddress(addr);
    if (space != nullptr) {
      oss << " space=" << *space;
    }
    accounting::CardTable* card_table = heap_->GetCardTable();
    if (card_table->AddrIsInCardTable(addr)) {
      oss << " card=" << static_cast<size_t>(
          card_table->GetCard(reinterpret_cast<const mirror::Object*>(addr)));
    }
    // Dump adjacent RAM.
    oss << DumpRAMAroundAddress(reinterpret_cast<uintptr_t>(addr), 4 * kObjectAlignment);
  } else {
    oss << " <invalid address>";
  }
  return oss.str();
}

void Verification::LogHeapCorruption(ObjPtr<mirror::Object> holder,
                                     MemberOffset offset,
                                     mirror::Object* ref,
                                     bool fatal) const {
  // Highest priority logging first.
  // Buffer the output in the string stream since it is more important than the stack traces
  // and we want it to have log priority. The stack traces are printed from Runtime::Abort
  // which is called from LOG(FATAL) but before the abort message.
  std::ostringstream oss;
  oss << "GC tried to mark invalid reference " << ref << std::endl;
  oss << DumpObjectInfo(ref, "ref") << "\n";
  oss << DumpObjectInfo(holder.Ptr(), "holder") << "\n";
  if (holder != nullptr) {
    mirror::Class* holder_klass = holder->GetClass<kVerifyNone, kWithoutReadBarrier>();
    if (IsValidClass(holder_klass)) {
      oss << " field_offset=" << offset.Uint32Value();
      ArtField* field = holder->FindFieldByOffset(offset);
      if (field != nullptr) {
        oss << " name=" << field->GetName();
      }
    }
    mirror::HeapReference<mirror::Object>* addr = holder->GetFieldObjectReferenceAddr(offset);
    oss << " reference addr"
        << DumpRAMAroundAddress(reinterpret_cast<uintptr_t>(addr), 4 * kObjectAlignment);
  }
  Runtime::Current()->GetHeap()->DumpSpaces(oss);
  MemMap::DumpMaps(oss, /* terse= */ true);

  if (fatal) {
    LOG(FATAL) << oss.str();
  } else {
    LOG(FATAL_WITHOUT_ABORT) << oss.str();
  }
}

bool Verification::IsAddressInHeapSpace(const void* addr, space::Space** out_space) const {
  space::Space* const space = heap_->FindSpaceFromAddress(addr);
  if (space != nullptr) {
    if (out_space != nullptr) {
      *out_space = space;
    }
    return true;
  }
  return false;
}

bool Verification::IsValidHeapObjectAddress(const void* addr, space::Space** out_space) const {
  return IsAligned<kObjectAlignment>(addr) && IsAddressInHeapSpace(addr, out_space);
}

using ObjectSet = std::set<mirror::Object*>;
using WorkQueue = std::deque<std::pair<mirror::Object*, std::string>>;

// Use for visiting the GcRoots held live by ArtFields, ArtMethods, and ClassLoaders.
class Verification::BFSFindReachable {
 public:
  explicit BFSFindReachable(ObjectSet* visited) : visited_(visited) {}

  void operator()(mirror::Object* obj, MemberOffset offset, [[maybe_unused]] bool is_static) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    ArtField* field = obj->FindFieldByOffset(offset);
    Visit(obj->GetFieldObject<mirror::Object>(offset),
          field != nullptr ? field->GetName() : "");
  }

  void VisitRootIfNonNull(mirror::CompressedReference<mirror::Object>* root) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    if (!root->IsNull()) {
      VisitRoot(root);
    }
  }

  void VisitRoot(mirror::CompressedReference<mirror::Object>* root) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    Visit(root->AsMirrorPtr(), "!nativeRoot");
  }

  void Visit(mirror::Object* ref, const std::string& field_name) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    if (ref != nullptr && visited_->insert(ref).second) {
      new_visited_.emplace_back(ref, field_name);
    }
  }

  const WorkQueue& NewlyVisited() const {
    return new_visited_;
  }

 private:
  ObjectSet* visited_;
  mutable WorkQueue new_visited_;
};

class Verification::CollectRootVisitor : public SingleRootVisitor {
 public:
  CollectRootVisitor(ObjectSet* visited, WorkQueue* work) : visited_(visited), work_(work) {}

  void VisitRoot(mirror::Object* obj, const RootInfo& info)
      override REQUIRES_SHARED(Locks::mutator_lock_) {
    if (obj != nullptr && visited_->insert(obj).second) {
      std::ostringstream oss;
      oss << info.ToString() << " = " << obj << "(" << obj->PrettyTypeOf() << ")";
      work_->emplace_back(obj, oss.str());
    }
  }

 private:
  ObjectSet* const visited_;
  WorkQueue* const work_;
};

std::string Verification::FirstPathFromRootSet(ObjPtr<mirror::Object> target) const {
  Runtime* const runtime =  Runtime::Current();
  std::set<mirror::Object*> visited;
  std::deque<std::pair<mirror::Object*, std::string>> work;
  {
    CollectRootVisitor root_visitor(&visited, &work);
    runtime->VisitRoots(&root_visitor, kVisitRootFlagAllRoots);
  }
  while (!work.empty()) {
    auto pair = work.front();
    work.pop_front();
    if (pair.first == target) {
      return pair.second;
    }
    BFSFindReachable visitor(&visited);
    pair.first->VisitReferences(visitor, VoidFunctor());
    for (auto&& pair2 : visitor.NewlyVisited()) {
      std::ostringstream oss;
      mirror::Object* obj = pair2.first;
      oss << pair.second << " -> " << obj << "(" << obj->PrettyTypeOf() << ")." << pair2.second;
      work.emplace_back(obj, oss.str());
    }
  }
  return "<no path found>";
}

}  // namespace gc
}  // namespace art
