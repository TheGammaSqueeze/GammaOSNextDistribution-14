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
#include "berberis/lite_translator/lite_translate_region.h"

#include <cstdint>
#include <limits>
#include <tuple>

#include "berberis/assembler/machine_code.h"
#include "berberis/base/checks.h"
#include "berberis/code_gen_lib/code_gen_lib.h"
#include "berberis/decoder/riscv64/decoder.h"
#include "berberis/decoder/riscv64/semantics_player.h"
#include "berberis/guest_state/guest_addr.h"

#include "riscv64_to_x86_64/lite_translator.h"

namespace berberis {

namespace {

void Finalize(LiteTranslator* translator, GuestAddr pc) {
  translator->ExitRegion(pc);
  translator->as()->Finalize();
}

void GenIncrementProfileCounter(x86_64::Assembler* as, const LiteTranslateParams& params) {
  CHECK(params.counter_location);
  *params.counter_location = 0;

  // Ideally for thread safety the counter subtraction needs to be atomic. But,
  // our experiments showed that the overhead from LOCK prefix here is too high
  // (b/222363018#comment3).
  // The threshold for the counter is an heuristic though. The worst what can
  // happen due to racing threads is counter inadvertently rolling back to a
  // value a few increments before, which we consider acceptable.
  // WARNING: do not clobber rax, since insn_addr held in it is used in case of
  // the reached threshold.
  as->Movq(as->rcx, bit_cast<int64_t>(params.counter_location));
  static_assert(sizeof(*params.counter_location) == 4);
  as->Addl({.base = as->rcx}, 1);
  as->Cmpl({.base = as->rcx}, params.counter_threshold);
  as->Jcc(x86_64::Assembler::Condition::kGreater, params.counter_threshold_callback);
}

// Returns the success status and
// - in case of success, the pc of the next instruction past the translated region
// - in case of failure, the pc of the failed instruction
// Specifically, returnes input pc if we cannot translate even the first instruction.
std::tuple<bool, GuestAddr> TryLiteTranslateRegionImpl(GuestAddr start_pc,
                                                       GuestAddr end_pc,
                                                       MachineCode* machine_code,
                                                       LiteTranslateParams params) {
  CHECK_LT(start_pc, end_pc);
  LiteTranslator translator(machine_code, start_pc, params);
  SemanticsPlayer sem_player(&translator);
  Decoder decoder(&sem_player);

  if (params.enable_self_profiling) {
    GenIncrementProfileCounter(translator.as(), params);
  }

  while (translator.GetInsnAddr() != end_pc && !translator.is_region_end_reached()) {
    uint8_t insn_size = decoder.Decode(ToHostAddr<const uint16_t>(translator.GetInsnAddr()));
    if (!translator.success()) {
      return {false, translator.GetInsnAddr()};
    }
    translator.FreeTempRegs();
    translator.IncrementInsnAddr(insn_size);
  }

  Finalize(&translator, translator.GetInsnAddr());

  return {translator.success(), translator.GetInsnAddr()};
}

}  // namespace

bool LiteTranslateRange(GuestAddr start_pc,
                        GuestAddr end_pc,
                        MachineCode* machine_code,
                        LiteTranslateParams params) {
  auto [success, stop_pc] = TryLiteTranslateRegionImpl(start_pc, end_pc, machine_code, params);
  return success;
}

std::tuple<bool, GuestAddr> TryLiteTranslateRegion(GuestAddr start_pc,
                                                   MachineCode* machine_code,
                                                   LiteTranslateParams params) {
  // This effectively makes translating code at max guest address impossible, but we
  // assume that it's not practically significant.
  return TryLiteTranslateRegionImpl(
      start_pc, std::numeric_limits<GuestAddr>::max(), machine_code, params);
}

}  // namespace berberis
