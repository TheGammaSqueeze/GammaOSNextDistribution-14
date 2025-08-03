/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_ZEPHYR_ATOMIC_BASE_IMPL_H_
#define CHRE_PLATFORM_ZEPHYR_ATOMIC_BASE_IMPL_H_

#include <zephyr/sys/atomic.h>

#include "chre/platform/atomic.h"

namespace chre {

inline AtomicBool::AtomicBool(bool starting_value) {
  value = ATOMIC_INIT(starting_value);
}

inline bool AtomicBool::operator=(bool desired) {
  return atomic_set(&value, desired);
}

inline bool AtomicBool::load() const {
  return atomic_get(&value);
}

inline void AtomicBool::store(bool desired) {
  atomic_set(&value, desired);
}

inline bool AtomicBool::exchange(bool desired) {
  return atomic_set(&value, desired);
}

inline AtomicUint32::AtomicUint32(uint32_t starting_value) {
  value = ATOMIC_INIT(starting_value);
}

inline uint32_t AtomicUint32::operator=(uint32_t desired) {
  return atomic_set(&value, desired);
}

inline uint32_t AtomicUint32::load() const {
  return atomic_get(&value);
}

inline void AtomicUint32::store(uint32_t desired) {
  atomic_set(&value, desired);
}

inline uint32_t AtomicUint32::exchange(uint32_t desired) {
  return atomic_set(&value, desired);
}

inline uint32_t AtomicUint32::fetch_add(uint32_t arg) {
  return atomic_add(&value, arg);
}

inline uint32_t AtomicUint32::fetch_increment() {
  return atomic_inc(&value);
}

inline uint32_t AtomicUint32::fetch_sub(uint32_t arg) {
  return atomic_sub(&value, arg);
}

inline uint32_t AtomicUint32::fetch_decrement() {
  return atomic_dec(&value);
}

}  // namespace chre

#endif  // CHRE_PLATFORM_ZEPHYR_ATOMIC_BASE_IMPL_H_
