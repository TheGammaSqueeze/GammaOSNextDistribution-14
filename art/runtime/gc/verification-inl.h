/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef ART_RUNTIME_GC_VERIFICATION_INL_H_
#define ART_RUNTIME_GC_VERIFICATION_INL_H_

#include "verification.h"

#include "mirror/class-inl.h"

namespace art HIDDEN {
namespace gc {

template <ReadBarrierOption kReadBarrierOption>
bool Verification::IsValidClassUnchecked(mirror::Class* klass) const {
  mirror::Class* k1 = klass->GetClass<kVerifyNone, kReadBarrierOption>();
  if (!IsValidHeapObjectAddress(k1)) {
    return false;
  }
  // `k1` should be class class, take the class again to verify.
  // Note that this check may not be valid for the no image space
  // since the class class might move around from moving GC.
  mirror::Class* k2 = k1->GetClass<kVerifyNone, kReadBarrierOption>();
  if (!IsValidHeapObjectAddress(k2)) {
    return false;
  }
  return k1 == k2;
}

template <ReadBarrierOption kReadBarrierOption>
bool Verification::IsValidClass(mirror::Class* klass) const {
  if (!IsValidHeapObjectAddress(klass)) {
    return false;
  }
  return IsValidClassUnchecked<kReadBarrierOption>(klass);
}

template <ReadBarrierOption kReadBarrierOption>
bool Verification::IsValidObject(mirror::Object* obj) const {
  if (!IsValidHeapObjectAddress(obj)) {
    return false;
  }
  mirror::Class* klass = obj->GetClass<kVerifyNone, kReadBarrierOption>();
  return IsValidClass(klass);
}

}  // namespace gc
}  // namespace art

#endif  // ART_RUNTIME_GC_VERIFICATION_INL_H_
