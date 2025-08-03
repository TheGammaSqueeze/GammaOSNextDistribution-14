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

#include "berberis/runtime_primitives/memory_region_reservation.h"

#include <array>
#include <atomic>

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_arch.h"

namespace berberis {

namespace {

template <bool flag = false>
void static_bad_size() {
  static_assert(flag, "Expected Reservation to be of size 8 or 16");
}

template <typename ReservationType>
inline ReservationType MemoryRegionReservationLoadTemplate(GuestAddr addr,
                                                           std::memory_order mem_order) {
  if constexpr (sizeof(ReservationType) == 16) {
    // Intel doesn't have atomic 128-bit load other that CMPXCHG16B, which is also
    // a store, and doesn't work for read-only memory. We only support guests that
    // are similar to x86 in that a 128-bit load is two atomic 64-bit loads.
    ReservationType low =
        std::atomic_load_explicit(ToHostAddr<std::atomic<uint64_t>>(addr), mem_order);
    ReservationType high =
        std::atomic_load_explicit(ToHostAddr<std::atomic<uint64_t>>(addr + 8), mem_order);
    return (high << 64) | low;
  } else if constexpr (sizeof(ReservationType) == 8) {
    // Starting from i486 all accesses for all instructions are atomic when they are used for
    // naturally-aligned variables of uint8_t, uint16_t and uint32_t types.  But situation is not so
    // straightforward when we are dealing with uint64_t.
    //
    // This is what Intel manual says about atomicity of 64-bit memory operations:
    //   The Pentium processor (and newer processors since) guarantees that the following additional
    //   memory operations will always be carried out atomically:
    //     * Reading or writing a quadword aligned on a 64-bit boundary
    //
    // AMD manual says the same thing:
    //   Single load or store operations (from instructions that do just a single load or store) are
    //   naturally atomic on any AMD64 processor as long as they do not cross an aligned 8-byte
    //   boundary. Accesses up to eight bytes in size which do cross such a boundary may be
    //   performed atomically using certain instructions with a lock prefix, such as XCHG, CMPXCHG
    //   or CMPXCHG8B, as long as all such accesses are done using the same technique.
    //
    // Fortunately, the RISC-V ISA manual agrees as well - only accesses to naturally aligned memory
    // are required to be performed atomically.
    //
    // Thus using regular x86 movq is good enough for emulation of RISC-V behavior.
    //
    // But std::atomic<uint64_t> would always use heavy "lock chmpxchg8b" operation on IA32 platform
    // because uint64_t is not guaranteed to be naturally-aligned on IA32!
    //
    // Not only is this slow, but this fails when we are accessing read-only memory!
    //
    // Use raw "movq" assembler instruction to circumvent that limitation of IA32 ABI.
    ReservationType reservation;
    __asm__ __volatile__("movq (%1),%0" : "=x"(reservation) : "r"(addr));
    return reservation;
  } else {
    static_bad_size();
  }
}

inline Reservation MemoryRegionReservationLoad(GuestAddr addr, std::memory_order mem_order) {
  return MemoryRegionReservationLoadTemplate<Reservation>(addr, mem_order);
}

MemoryRegionReservation::Entry& GetEntry(GuestAddr addr) {
  static constexpr size_t kHashSize = 4096;
  static std::array<MemoryRegionReservation::Entry, kHashSize> g_owners;

  return g_owners[(addr / sizeof(Reservation)) % kHashSize];
}

// Special owner to disallow stealing. Only used when exclusive store is in progress.
int g_fake_cpu;
constexpr void* kLockedOwner = &g_fake_cpu;

}  // namespace

void MemoryRegionReservation::SetOwner(GuestAddr aligned_addr, void* cpu) {
  auto& entry = GetEntry(aligned_addr);

  // Try stealing. Fails if another thread is doing an exclusive store or wins a race.
  // If stealing fails, then the subsequent exclusive store fails as well.
  auto prev = entry.load();
  if (prev != kLockedOwner) {
    entry.compare_exchange_strong(prev, cpu);
  }
}

MemoryRegionReservation::Entry* MemoryRegionReservation::TryLock(GuestAddr aligned_addr,
                                                                 void* cpu) {
  auto& entry = GetEntry(aligned_addr);

  // Try locking. Fails if Load failed to steal the address or the address was stolen afterwards.
  if (!entry.compare_exchange_strong(cpu, kLockedOwner)) {
    return nullptr;
  }

  return &entry;
}

void MemoryRegionReservation::Unlock(MemoryRegionReservation::Entry* entry) {
  // No need to compare and swap as the locked address cannot be stolen.
  entry->store(nullptr);
}

Reservation MemoryRegionReservation::ReservationLoad(void* cpu,
                                                     GuestAddr aligned_addr,
                                                     std::memory_order mem_order) {
  SetOwner(aligned_addr, cpu);

  // ATTENTION!
  // For region size <= 8, region load is atomic, so this always returns a consistent value.
  // For region size > 8, region load is NOT atomic! The returned value might be inconsistent.
  //
  // If, to load a 16-byte value atomically, the guest architecture suggests to perform a 16-byte
  // exclusive load and then an exclusive store of the loaded value. The loaded value can be used
  // only if the exclusive store succeeds.
  //
  // If developers are aware of the above and do not use the result of 16-byte exclusive load
  // without a subsequent check by an exclusive store, an inconsistent return value here is safe.
  // Too bad if this is not the case...
  return MemoryRegionReservationLoad(aligned_addr, mem_order);
}

bool MemoryRegionReservation::ReservationExchange(void* cpu,
                                                  GuestAddr aligned_addr,
                                                  Reservation expected,
                                                  Reservation value,
                                                  std::memory_order mem_order) {
  auto* entry = TryLock(aligned_addr, cpu);

  if (!entry) {
    return false;
  }

  bool written = std::atomic_compare_exchange_strong_explicit(
      ToHostAddr<std::atomic<Reservation>>(aligned_addr),
      &expected,
      value,
      mem_order,
      std::memory_order_relaxed);

  Unlock(entry);

  return written;
}

}  // namespace berberis
