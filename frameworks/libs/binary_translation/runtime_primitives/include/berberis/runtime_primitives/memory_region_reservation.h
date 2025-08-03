/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_MEMORY_REGION_RESERVATION_H_
#define BERBERIS_RUNTIME_PRIMITIVES_MEMORY_REGION_RESERVATION_H_

#include <climits>  // CHAR_BITS
#include <cstdint>
#include <cstring>  // memcpy

#include <atomic>

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_arch.h"

namespace berberis {

class MemoryRegionReservation {
 public:
  // Returns previously reserved address.
  static GuestAddr Clear(CPUState* cpu) {
    GuestAddr previous_address = cpu->reservation_address;
    cpu->reservation_address = kNullGuestAddr;
    return previous_address;
  }

  template <typename Type>
  static Type Load(CPUState* cpu, GuestAddr addr, std::memory_order mem_order) {
    static_assert(sizeof(Type) <= sizeof(cpu->reservation_value),
                  "Type is too big for reservation");

    GuestAddr aligned_addr = addr - (addr % sizeof(Reservation));
    cpu->reservation_address = aligned_addr;

    cpu->reservation_value = ReservationLoad(cpu, aligned_addr, mem_order);

    // Extract the result from the region.
    return static_cast<Type>(cpu->reservation_value >> ((addr - aligned_addr) * CHAR_BIT));
  }

  // Returns 0 on successful store or 1 otherwise.
  template <typename Type>
  static uint32_t Store(CPUState* cpu, GuestAddr addr, Type value, std::memory_order mem_order) {
    static_assert(sizeof(Type) <= sizeof(cpu->reservation_value),
                  "Type is too big for reservation");

    GuestAddr reservation_address = Clear(cpu);

    GuestAddr aligned_addr = addr - (addr % sizeof(Reservation));
    if (aligned_addr != reservation_address) {
      return 1;
    }

    auto cur_value = cpu->reservation_value;
    auto new_value = cpu->reservation_value;

    // Embed value into new region value.
    memcpy(reinterpret_cast<char*>(&new_value) + (addr - aligned_addr), &value, sizeof(Type));

    return ReservationExchange(cpu, aligned_addr, cur_value, new_value, mem_order) ? 0 : 1;
  }

  using Entry = std::atomic<void*>;

  static void SetOwner(GuestAddr aligned_addr, void* cpu);
  static Entry* TryLock(GuestAddr aligned_addr, void* cpu);
  static void Unlock(Entry* entry);

 private:
  static Reservation ReservationLoad(void* cpu,
                                     GuestAddr aligned_addr,
                                     std::memory_order mem_order);
  static bool ReservationExchange(void* cpu,
                                  GuestAddr aligned_addr,
                                  Reservation expected,
                                  Reservation value,
                                  std::memory_order mem_order);
};

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_MEMORY_REGION_RESERVATION_H_
