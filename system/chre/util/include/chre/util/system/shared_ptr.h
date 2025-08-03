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

#ifndef CHRE_UTIL_SYSTEM_SHARED_PTR_H_
#define CHRE_UTIL_SYSTEM_SHARED_PTR_H_

#include <cstddef>

namespace chre {

/**
 * Wraps a pointer to a dynamically allocated object and manages the underlying
 * memory. The goal is to be similar to std::shared_ptr, but we do not support
 * custom deleters - deletion is always done via memoryFree().
 *
 * NOTE: Be very careful to avoid circular SharedPtr references since this can
 * cause leaks that are hard to debug. For a full list of caveats and tips,
 * check out system/core/libutils/include/utils/RefBase.h.
 */
template <typename ObjectType>
class SharedPtr {
 public:
  /**
   * Pointer type of ObjectType.
   */
  typedef ObjectType *pointer;

  /**
   * Construct a SharedPtr instance that does not own any object.
   */
  SharedPtr();

  /**
   * Constructs a SharedPtr instance that owns the given object, and will free
   * its memory when all SharedPtr references have been destroyed.
   *
   * @param object Pointer to an object allocated via memoryAlloc. It is not
   *        valid for this object's memory to come from any other source,
   *        including the stack, or static allocation on the heap.
   */
  SharedPtr(ObjectType *object);

  /**
   * Constructs a new SharedPtr via moving the Object reference from another
   * SharedPtr.
   *
   * @param other SharedPtr instance to move into this object
   */
  SharedPtr(SharedPtr<ObjectType> &&other);

  /**
   * Constructs a new SharedPtr via moving the Object reference from another
   * SharedPtr. This constructor allows conversion (ie: upcast) to another type
   * if possible.
   *
   * @param other SharedPtr instance to move and convert into this object.
   */
  template <typename OtherObjectType>
  SharedPtr(SharedPtr<OtherObjectType> &&other);

  /**
   * Constructs a new SharedPtr by creating a new reference to 'other' so each
   * SharedPtr will have its own reference.
   *
   * @param other SharedPtr instance containing data to be referenced by this
   *     pointer.
   */
  SharedPtr(const SharedPtr &other);

  /**
   * Constructs a new SharedPtr via creating a new reference to the Object so
   * each SharedPtr will have its own ref. This constructor allows conversion
   * (ie: upcast) to another type if possible.
   *
   * @param other SharedPtr instance containing data to be referenced by this
   *     pointer.
   */
  template <typename OtherObjectType>
  SharedPtr(const SharedPtr<OtherObjectType> &other);

  /**
   * Deconstructs the object (if necessary) and releases associated memory if
   * no other references to the memory exist.
   */
  ~SharedPtr();

  /**
   * Determines if this SharedPtr owns an object, or references null.
   *
   * @return true if get() returns nullptr
   */
  bool isNull() const;

  /**
   * @return A pointer to the underlying object, or nullptr if this object is
   *         not currently valid.
   */
  ObjectType *get() const;

  /**
   * Replaces the object referenced by the SharedPtr by an object pointed by a
   * given pointer. Also calls the dereferences the associated memory of the
   * previously referenced object. Invoking this method on the object managed by
   * the SharedPtr, obtained via get(), is illegal.
   *
   * @param object the object to be referenced by the SharedPtr
   */
  void reset(ObjectType *object);

  /**
   * Dereferences the object owned by the SharedPtr. If necessary, calls the
   * destructor and releases the associated memory of the previously referenced
   * object.
   */
  void reset();

  /**
   * @return A pointer to the underlying object.
   */
  ObjectType *operator->() const;

  /**
   * @return A reference to the underlying object.
   */
  ObjectType &operator*() const;

  /**
   * @param index The index of an object in the underlying array object.
   * @return A reference to the underlying object at an index.
   */
  ObjectType &operator[](size_t index) const;

  /**
   * Copy assignment operator. The new SharedPtr object has a new reference to
   * the underlying object and the existing SharedPtr object retains its
   * reference.
   *
   * @param other The other object being copied.
   * @return A reference to the newly copied object.
   */
  SharedPtr<ObjectType> &operator=(const SharedPtr<ObjectType> &other);

  /**
   * Move assignment operator. Ownership of this object is transferred and the
   * other object is left in an invalid state.
   *
   * @param other The other object being moved.
   * @return A reference to the newly moved object.
   */
  SharedPtr<ObjectType> &operator=(SharedPtr<ObjectType> &&other);

  /**
   * Two SharedPtr compare equal (==) if their stored pointers compare equal,
   * and not equal (!=) otherwise.
   *
   * @param other The other object being compared.
   * @return true if the other's pointer is same as the underlying pointer,
   * otherwise false.
   */
  bool operator==(const SharedPtr<ObjectType> &other) const;

  /**
   * Two SharedPtr compare equal (==) if their stored pointers compare equal,
   * and not equal (!=) otherwise.
   *
   * @param other The other object being compared.
   * @return true if the other's pointer is different than the underlying
   * pointer, otherwise false.
   */
  bool operator!=(const SharedPtr<ObjectType> &other) const;

 private:
  // Befriend this class to itself to allow the templated conversion constructor
  // permission to access mObject below.
  template <typename OtherObjectType>
  friend class SharedPtr;

  //! A pointer to the underlying storage for this object.
  ObjectType *mObject = nullptr;
};

/**
 * Allocates and constructs a new object of type ObjectType on the heap, and
 * returns a SharedPtr that references the object. This function is similar to
 * std::make_shared.
 *
 * @param args The arguments to pass to the object's constructor.
 */
template <typename ObjectType, typename... Args>
SharedPtr<ObjectType> MakeShared(Args &&...args);

/**
 * Just like MakeShared(), except it zeros out any allocated memory. Intended to
 * be used for creating objects that have trivial constructors (e.g. C structs)
 * but should start with a known state.
 */
template <typename ObjectType>
SharedPtr<ObjectType> MakeSharedZeroFill();

}  // namespace chre

#include "chre/util/system/shared_ptr_impl.h"

#endif  // CHRE_UTIL_SYSTEM_SHARED_PTR_H_
