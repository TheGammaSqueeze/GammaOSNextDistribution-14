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

#ifndef ART_RUNTIME_GC_VERIFICATION_H_
#define ART_RUNTIME_GC_VERIFICATION_H_

#include "base/macros.h"
#include "obj_ptr.h"
#include "offsets.h"
#include "read_barrier_option.h"

namespace art HIDDEN {

namespace mirror {
class Class;
class Object;
}  // namespace mirror

namespace gc {

namespace space {
class Space;
}  // namespace space

class Heap;

class Verification {
 public:
  explicit Verification(gc::Heap* heap) : heap_(heap) {}

  // Dump some debugging-relevant info about an object.
  std::string DumpObjectInfo(const void* obj, const char* tag) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Don't use ObjPtr for things that might not be aligned like the invalid reference.
  EXPORT void LogHeapCorruption(ObjPtr<mirror::Object> holder,
                                MemberOffset offset,
                                mirror::Object* ref,
                                bool fatal) const REQUIRES_SHARED(Locks::mutator_lock_);

  // Return true if the klass is likely to be a valid mirror::Class.
  // Returns true if the class is a valid mirror::Class or possibly spuriously.
  template <ReadBarrierOption kReadBarrierOption = kWithoutReadBarrier>
  bool IsValidClassUnchecked(mirror::Class* klass) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Return true if the klass is likely to be a valid mirror::Class.
  template <ReadBarrierOption kReadBarrierOption = kWithoutReadBarrier>
  bool IsValidClass(mirror::Class* klass) const REQUIRES_SHARED(Locks::mutator_lock_);
  // Return true if the obj is likely to be a valid obj with valid mirror::Class.
  template <ReadBarrierOption kReadBarrierOption = kWithoutReadBarrier>
  bool IsValidObject(mirror::Object* obj) const REQUIRES_SHARED(Locks::mutator_lock_);

  // Does not allow null, checks alignment.
  bool IsValidHeapObjectAddress(const void* addr, space::Space** out_space = nullptr) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Find the first path to the target from the root set. Should be called while paused since
  // visiting roots is not safe otherwise.
  EXPORT std::string FirstPathFromRootSet(ObjPtr<mirror::Object> target) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Does not check alignment, used by DumpRAMAroundAddress.
  bool IsAddressInHeapSpace(const void* addr, space::Space** out_space = nullptr) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Dump bytes of RAM before and after an address.
  std::string DumpRAMAroundAddress(uintptr_t addr, uintptr_t bytes) const
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  gc::Heap* const heap_;

  class BFSFindReachable;
  class CollectRootVisitor;
};

}  // namespace gc
}  // namespace art

#endif  // ART_RUNTIME_GC_VERIFICATION_H_
