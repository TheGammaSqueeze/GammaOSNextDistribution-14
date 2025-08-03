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

#ifndef ART_COMPILER_OPTIMIZING_INTRINSIC_OBJECTS_H_
#define ART_COMPILER_OPTIMIZING_INTRINSIC_OBJECTS_H_

#include "base/bit_field.h"
#include "base/bit_utils.h"
#include "base/macros.h"
#include "base/mutex.h"
#include "obj_ptr.h"
#include "offsets.h"

namespace art HIDDEN {

class ClassLinker;
class MemberOffset;
class Thread;

namespace mirror {
class Object;
template <class T> class ObjectArray;
}  // namespace mirror

#define BOXED_TYPES(V) \
  V(Byte, -128, 127, DataType::Type::kInt8, 0) \
  V(Short, -128, 127, DataType::Type::kInt16, kByteCacheLastIndex) \
  V(Character, 0, 127, DataType::Type::kUint16, kShortCacheLastIndex) \
  V(Integer, -128, 127, DataType::Type::kInt32, kCharacterCacheLastIndex)

#define DEFINE_BOXED_CONSTANTS(name, low, high, unused, start_index) \
  static constexpr size_t k ##name ##CacheLastIndex = start_index + (high - low + 1); \
  static constexpr size_t k ##name ##CacheFirstIndex = start_index;
  BOXED_TYPES(DEFINE_BOXED_CONSTANTS)

  static constexpr size_t kNumberOfBoxedCaches = kIntegerCacheLastIndex;
#undef DEFINE_BOXED_CONSTANTS

class IntrinsicObjects {
 public:
  enum class PatchType {
    kValueOfObject,
    kValueOfArray,

    kLast = kValueOfArray
  };

  static uint32_t EncodePatch(PatchType patch_type, uint32_t index = 0u) {
    return PatchTypeField::Encode(static_cast<uint32_t>(patch_type)) | IndexField::Encode(index);
  }

  static PatchType DecodePatchType(uint32_t intrinsic_data) {
    return static_cast<PatchType>(PatchTypeField::Decode(intrinsic_data));
  }

  static uint32_t DecodePatchIndex(uint32_t intrinsic_data) {
    return IndexField::Decode(intrinsic_data);
  }

  // Helpers returning addresses of objects, suitable for embedding in generated code.
#define DEFINE_BOXED_ACCESSES(name, unused1, unused2, unused3, start_index) \
  static ObjPtr<mirror::Object> Get ##name ##ValueOfObject( \
      ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects, \
      uint32_t index) REQUIRES_SHARED(Locks::mutator_lock_) { \
    return GetValueOfObject(boot_image_live_objects, k ##name ##CacheFirstIndex, index); \
  } \
  static MemberOffset Get ##name ##ValueOfArrayDataOffset( \
      ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects) \
      REQUIRES_SHARED(Locks::mutator_lock_) { \
    return GetValueOfArrayDataOffset(boot_image_live_objects, k ##name ##CacheFirstIndex); \
  }
  BOXED_TYPES(DEFINE_BOXED_ACCESSES)
#undef DEFINED_BOXED_ACCESSES

  EXPORT static void FillIntrinsicObjects(
      ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects, size_t start_index)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static constexpr size_t GetNumberOfIntrinsicObjects() {
    return kNumberOfBoxedCaches;
  }

  EXPORT static ObjPtr<mirror::Object> GetValueOfObject(
      ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects,
      size_t start_index,
      uint32_t index) REQUIRES_SHARED(Locks::mutator_lock_);

  EXPORT static MemberOffset GetValueOfArrayDataOffset(
      ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects,
      size_t start_index) REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  static constexpr size_t kPatchTypeBits =
      MinimumBitsToStore(static_cast<uint32_t>(PatchType::kLast));
  static constexpr size_t kIndexBits = BitSizeOf<uint32_t>() - kPatchTypeBits;
  using PatchTypeField = BitField<uint32_t, 0u, kPatchTypeBits>;
  using IndexField = BitField<uint32_t, kPatchTypeBits, kIndexBits>;
};

}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_INTRINSIC_OBJECTS_H_
