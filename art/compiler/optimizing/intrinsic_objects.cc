/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "intrinsic_objects.h"

#include "art_field-inl.h"
#include "base/casts.h"
#include "base/logging.h"
#include "intrinsics.h"
#include "oat/image.h"
#include "obj_ptr-inl.h"
#include "well_known_classes.h"

namespace art HIDDEN {

static constexpr size_t kIntrinsicObjectsOffset =
    enum_cast<size_t>(ImageHeader::kIntrinsicObjectsStart);

template <typename T>
static int32_t FillIntrinsicsObjects(
    ArtField* cache_field,
    ObjPtr<mirror::ObjectArray<mirror::Object>> live_objects,
    int32_t expected_low,
    int32_t expected_high,
    T&& type_check,
    int32_t index)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::ObjectArray<mirror::Object>> cache =
      ObjPtr<mirror::ObjectArray<mirror::Object>>::DownCast(
          cache_field->GetObject(cache_field->GetDeclaringClass()));
  int32_t length = expected_high - expected_low + 1;
  DCHECK_EQ(length, cache->GetLength());
  for (int32_t i = 0; i != length; ++i) {
    ObjPtr<mirror::Object> value = cache->GetWithoutChecks(i);
    live_objects->Set(index + i, value);
    type_check(value, expected_low + i);
  }
  return index + length;
}

void IntrinsicObjects::FillIntrinsicObjects(
    ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects, size_t start_index) {
  DCHECK_EQ(start_index, ImageHeader::kIntrinsicObjectsStart);
  int32_t index = dchecked_integral_cast<int32_t>(start_index);
#define FILL_OBJECTS(name, low, high, type, offset) \
  index = FillIntrinsicsObjects( \
      WellKnownClasses::java_lang_ ##name ##_ ##name ##Cache_cache, \
      boot_image_live_objects, \
      low, \
      high, \
      [](ObjPtr<mirror::Object> obj, int32_t expected) REQUIRES_SHARED(Locks::mutator_lock_) { \
        CHECK_EQ(expected, WellKnownClasses::java_lang_ ##name ##_value->Get ##name(obj)); \
      }, \
      index);
  BOXED_TYPES(FILL_OBJECTS)
#undef FILL_OBJECTS
  DCHECK_EQ(dchecked_integral_cast<size_t>(index), start_index + GetNumberOfIntrinsicObjects());
}

static bool HasIntrinsicObjects(
    ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(boot_image_live_objects != nullptr);
  uint32_t length = static_cast<uint32_t>(boot_image_live_objects->GetLength());
  DCHECK_GE(length, kIntrinsicObjectsOffset);
  return length != kIntrinsicObjectsOffset;
}

ObjPtr<mirror::Object> IntrinsicObjects::GetValueOfObject(
    ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects,
    size_t start_index,
    uint32_t index) {
  DCHECK(HasIntrinsicObjects(boot_image_live_objects));
  // No need for read barrier for boot image object or for verifying the value that was just stored.
  ObjPtr<mirror::Object> result =
      boot_image_live_objects->GetWithoutChecks<kVerifyNone, kWithoutReadBarrier>(
          kIntrinsicObjectsOffset + start_index + index);
  DCHECK(result != nullptr);
  return result;
}

MemberOffset IntrinsicObjects::GetValueOfArrayDataOffset(
    ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects,
    size_t start_index) {
  DCHECK(HasIntrinsicObjects(boot_image_live_objects));
  MemberOffset result =
      mirror::ObjectArray<mirror::Object>::OffsetOfElement(kIntrinsicObjectsOffset + start_index);
  DCHECK_EQ(GetValueOfObject(boot_image_live_objects, start_index, 0u),
            (boot_image_live_objects
                 ->GetFieldObject<mirror::Object, kVerifyNone, kWithoutReadBarrier>(result)));
  return result;
}

}  // namespace art
