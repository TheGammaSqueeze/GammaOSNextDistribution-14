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

#include "gtest/gtest.h"

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/runtime_primitives/memory_region_reservation.h"

namespace berberis {

namespace {

static_assert(sizeof(Reservation) >= 8, "Reservation size is too small");

TEST(MemoryRegionReservation, Smoke) {
  CPUState cpu{};

  constexpr uint32_t kTestVal = 0xf1234567;

  Reservation reservation = kTestVal;

  GuestAddr addr = ToGuestAddr(&reservation) + sizeof(uint32_t);

  ASSERT_EQ(0u, MemoryRegionReservation::Load<uint32_t>(&cpu, addr, std::memory_order_seq_cst));

  ASSERT_EQ(
      0u,
      MemoryRegionReservation::Store<uint32_t>(&cpu, addr, kTestVal, std::memory_order_seq_cst));

  ASSERT_EQ(reservation, (Reservation(kTestVal) << 32) | kTestVal);

  ASSERT_EQ(
      1u,
      MemoryRegionReservation::Store<uint32_t>(&cpu, addr, ~kTestVal, std::memory_order_seq_cst));

  ASSERT_EQ(reservation, (Reservation(kTestVal) << 32) | kTestVal);
}

TEST(MemoryRegionReservation, DoubleLoad) {
  CPUState cpu{};

  constexpr uint32_t kTestVal1 = 0xf1234567;
  constexpr uint32_t kTestVal2 = 0xdeadbeef;

  Reservation reservation_1 = kTestVal1;
  Reservation reservation_2 = kTestVal2;

  ASSERT_EQ(kTestVal1,
            MemoryRegionReservation::Load<uint32_t>(
                &cpu, ToGuestAddr(&reservation_1), std::memory_order_seq_cst));

  ASSERT_EQ(kTestVal2,
            MemoryRegionReservation::Load<uint32_t>(
                &cpu, ToGuestAddr(&reservation_2), std::memory_order_seq_cst));

  ASSERT_EQ(0u,
            MemoryRegionReservation::Store<uint32_t>(
                &cpu, ToGuestAddr(&reservation_2), kTestVal1, std::memory_order_seq_cst));

  ASSERT_EQ(kTestVal1, reservation_1);
  ASSERT_EQ(kTestVal1, reservation_2);
}

TEST(MemoryRegionReservation, Steal) {
  CPUState cpu_1{};
  CPUState cpu_2{};

  constexpr uint32_t kTestVal1 = 0xf1234567;
  constexpr uint32_t kTestVal2 = 0xdeadbeef;
  constexpr uint32_t kTestVal3 = 0xabcdefab;

  Reservation reservation = kTestVal1;

  ASSERT_EQ(kTestVal1,
            MemoryRegionReservation::Load<uint32_t>(
                &cpu_1, ToGuestAddr(&reservation), std::memory_order_seq_cst));

  ASSERT_EQ(kTestVal1,
            MemoryRegionReservation::Load<uint32_t>(
                &cpu_2, ToGuestAddr(&reservation), std::memory_order_seq_cst));

  ASSERT_EQ(1u,
            MemoryRegionReservation::Store<uint32_t>(
                &cpu_1, ToGuestAddr(&reservation), kTestVal2, std::memory_order_seq_cst));

  ASSERT_EQ(0u,
            MemoryRegionReservation::Store<uint32_t>(
                &cpu_2, ToGuestAddr(&reservation), kTestVal3, std::memory_order_seq_cst));

  ASSERT_EQ(kTestVal3, reservation);
}

TEST(MemoryRegionReservation, StealEqual) {
  CPUState cpu_1{};
  CPUState cpu_2{};

  constexpr uint32_t kTestVal1 = 0xf1234567;
  constexpr uint32_t kTestVal2 = 0xdeadbeef;

  Reservation reservation = kTestVal1;

  ASSERT_EQ(kTestVal1,
            MemoryRegionReservation::Load<uint32_t>(
                &cpu_1, ToGuestAddr(&reservation), std::memory_order_seq_cst));

  ASSERT_EQ(kTestVal1,
            MemoryRegionReservation::Load<uint32_t>(
                &cpu_2, ToGuestAddr(&reservation), std::memory_order_seq_cst));

  ASSERT_EQ(0u,
            MemoryRegionReservation::Store<uint32_t>(
                &cpu_2, ToGuestAddr(&reservation), kTestVal1, std::memory_order_seq_cst));

  ASSERT_EQ(1u,
            MemoryRegionReservation::Store<uint32_t>(
                &cpu_1, ToGuestAddr(&reservation), kTestVal2, std::memory_order_seq_cst));

  ASSERT_EQ(kTestVal1, reservation);
}

}  // namespace

}  // namespace berberis
