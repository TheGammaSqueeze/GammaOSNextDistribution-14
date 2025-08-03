/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_UTIL_SYSTEM_REF_BASE_H_
#define CHRE_UTIL_SYSTEM_REF_BASE_H_

#include <cstdint>

#include "chre/platform/assert.h"
#include "chre/platform/atomic.h"
#include "chre/platform/memory.h"

namespace chre {

/**
 * Base class for any type that needs to support reference counting.
 */
template <class T>
class RefBase {
 public:
  /**
   * Increments the reference count for this object.
   */
  void incRef() const {
    mRefCount.fetch_increment();
  }

  /**
   * Decrements the reference count for this object. If this invocation takes
   * the reference count to zero, the object will be destroyed and its memory
   * will be released.
   */
  void decRef() const {
    uint32_t refCount = mRefCount.fetch_decrement();
    CHRE_ASSERT(refCount > 0);
    if (refCount == 1) {
      T *obj = const_cast<T *>(static_cast<const T *>(this));
      obj->~T();
      memoryFree(obj);
    }
  }

 protected:
  /**
   * Destructor is protected so this object cannot be directly destroyed.
   */
  virtual ~RefBase() {}

 private:
  // Ref count should always start at 1 since something must reference this
  // object to have created it.
  mutable AtomicUint32 mRefCount{1};
};

}  // namespace chre

#endif  // CHRE_UTIL_SYSTEM_REF_BASE_H_
