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

#ifndef CHRE_PLATFORM_TINYSYS_ATOMIC_BASE_IMPL_H_
#define CHRE_PLATFORM_TINYSYS_ATOMIC_BASE_IMPL_H_

#include "chre/platform/atomic.h"

namespace chre {

inline AtomicBool::AtomicBool(bool startingValue) {
  set(startingValue);
}

inline bool AtomicBool::operator=(bool desired) {
  set(desired);
  return desired;
}

inline bool AtomicBool::load() const {
  return get();
}

inline void AtomicBool::store(bool desired) {
  set(desired);
}

inline bool AtomicBool::exchange(bool desired) {
  return swap(desired);
}

inline AtomicUint32::AtomicUint32(uint32_t startingValue) {
  set(startingValue);
}

inline uint32_t AtomicUint32::operator=(uint32_t desired) {
  set(desired);
  return desired;
}

inline uint32_t AtomicUint32::load() const {
  return get();
}

inline void AtomicUint32::store(uint32_t desired) {
  set(desired);
}

inline uint32_t AtomicUint32::exchange(uint32_t desired) {
  return swap(desired);
}

inline uint32_t AtomicUint32::fetch_add(uint32_t arg) {
  return add(arg);
}

inline uint32_t AtomicUint32::fetch_increment() {
  return add(1);
}

inline uint32_t AtomicUint32::fetch_sub(uint32_t arg) {
  return sub(arg);
}

inline uint32_t AtomicUint32::fetch_decrement() {
  return sub(1);
}

}  // namespace chre

#endif  // CHRE_PLATFORM_TINYSYS_ATOMIC_BASE_IMPL_H_