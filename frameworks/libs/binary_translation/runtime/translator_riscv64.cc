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

#include "translator_riscv64.h"
#include "berberis/runtime/translator.h"

#include <cstdint>
#include <cstdlib>
#include <tuple>

#include "berberis/assembler/machine_code.h"
#include "berberis/base/checks.h"
#include "berberis/base/config_globals.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_map_shadow.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/heavy_optimizer/riscv64/heavy_optimize_region.h"
#include "berberis/interpreter/riscv64/interpreter.h"
#include "berberis/lite_translator/lite_translate_region.h"
#include "berberis/runtime_primitives/code_pool.h"
#include "berberis/runtime_primitives/host_call_frame.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/profiler_interface.h"
#include "berberis/runtime_primitives/runtime_library.h"
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

namespace {

// Syntax sugar.
GuestCodeEntry::Kind kSpecialHandler = GuestCodeEntry::Kind::kSpecialHandler;
GuestCodeEntry::Kind kInterpreted = GuestCodeEntry::Kind::kInterpreted;
GuestCodeEntry::Kind kLightTranslated = GuestCodeEntry::Kind::kLightTranslated;
GuestCodeEntry::Kind kHeavyOptimized = GuestCodeEntry::Kind::kHeavyOptimized;

enum class TranslationMode {
  kInterpretOnly,
  kLiteTranslateOrFallbackToInterpret,
  kHeavyOptimizeOrFallbackToInterpret,
  kHeavyOptimizeOrFallbackToLiteTranslator,
  kLightTranslateThenHeavyOptimize,
  kTwoGear = kLightTranslateThenHeavyOptimize,
  kNumModes
};

TranslationMode g_translation_mode = TranslationMode::kLiteTranslateOrFallbackToInterpret;

void UpdateTranslationMode() {
  // Indices must match TranslationMode enum.
  constexpr const char* kTranslationModeNames[] = {"interpret-only",
                                                   "lite-translate-or-interpret",
                                                   "heavy-optimize-or-interpret",
                                                   "heavy-optimize-or-lite-translate",
                                                   "two-gear"};
  static_assert(static_cast<int>(TranslationMode::kNumModes) ==
                sizeof(kTranslationModeNames) / sizeof(char*));

  const char* config_mode = GetTranslationModeConfig();
  if (!config_mode) {
    return;
  }

  for (int i = 0; i < static_cast<int>(TranslationMode::kNumModes); i++) {
    if (0 == strcmp(config_mode, kTranslationModeNames[i])) {
      g_translation_mode = TranslationMode(i);
      TRACE("translation mode is manually set to '%s'", config_mode);
      return;
    }
  }

  LOG_ALWAYS_FATAL("Unrecognized translation mode '%s'", config_mode);
}

// Use aligned address of this variable as the default stop address for guest execution.
// It should never coincide with any guest address or address of a wrapped host symbol.
// Unwinder might examine nearby insns.
alignas(4) uint32_t g_native_bridge_call_guest[] = {
    // <native_bridge_call_guest>:
    0xd503201f,  // nop
    0xd503201f,  // nop  <--
    0xd503201f,  // nop
};

enum class TranslationGear {
  kFirst,
  kSecond,
};

uint8_t GetRiscv64InsnSize(GuestAddr pc) {
  constexpr uint16_t kInsnLenMask = uint16_t{0b11};
  if ((*ToHostAddr<uint16_t>(pc) & kInsnLenMask) != kInsnLenMask) {
    return 2;
  }
  return 4;
}

}  // namespace

HostCodePiece InstallTranslated(MachineCode* machine_code,
                                GuestAddr pc,
                                size_t size,
                                const char* prefix) {
  HostCode host_code = GetDefaultCodePoolInstance()->Add(machine_code);
  ProfilerLogGeneratedCode(host_code, machine_code->install_size(), pc, size, prefix);
  return {host_code, machine_code->install_size()};
}

void InitTranslator() {
  UpdateTranslationMode();
  InitHostCallFrameGuestPC(ToGuestAddr(g_native_bridge_call_guest + 1));
  InitInterpreter();
}

// Exported for testing only.
std::tuple<bool, HostCodePiece, size_t, GuestCodeEntry::Kind> TryLiteTranslateAndInstallRegion(
    GuestAddr pc,
    const LiteTranslateParams& params) {
  MachineCode machine_code;

  auto [success, stop_pc] = TryLiteTranslateRegion(pc, &machine_code, params);

  size_t size = stop_pc - pc;

  if (success) {
    return {true, InstallTranslated(&machine_code, pc, size, "lite"), size, kLightTranslated};
  }

  if (size == 0) {
    // Cannot translate even single instruction - the attempt failed.
    return {false, {}, 0, {}};
  }

  MachineCode another_machine_code;
  success = LiteTranslateRange(pc, stop_pc, &another_machine_code, params);
  CHECK(success);

  return {true,
          InstallTranslated(&another_machine_code, pc, size, "lite_range"),
          size,
          kLightTranslated};
}

// Exported for testing only.
std::tuple<bool, HostCodePiece, size_t, GuestCodeEntry::Kind> HeavyOptimizeRegion(GuestAddr pc) {
  MachineCode machine_code;
  auto [stop_pc, success, unused_number_of_processed_instructions] =
      HeavyOptimizeRegion(pc, &machine_code);
  size_t size = stop_pc - pc;
  if (success) {
    return {true, InstallTranslated(&machine_code, pc, size, "heavy"), size, kHeavyOptimized};
  }

  if (size == 0) {
    // Cannot translate even single instruction - the attempt failed.
    return {false, {}, 0, {}};
  }

  // Report success because we at least translated some instructions.
  return {true, InstallTranslated(&machine_code, pc, size, "heavy"), size, kHeavyOptimized};
}

template <TranslationGear kGear = TranslationGear::kFirst>
void TranslateRegion(GuestAddr pc) {
  TranslationCache* cache = TranslationCache::GetInstance();

  GuestCodeEntry* entry;
  if constexpr (kGear == TranslationGear::kFirst) {
    entry = cache->AddAndLockForTranslation(pc, 0);
  } else {
    CHECK(g_translation_mode == TranslationMode::kTwoGear);
    entry = cache->LockForGearUpTranslation(pc);
  }
  if (!entry) {
    return;
  }

  GuestMapShadow* guest_map_shadow = GuestMapShadow::GetInstance();

  // First check if the instruction would be in executable memory if it is compressed.  This
  // prevents dereferencing unknown memory to determine the size of the instruction.
  constexpr uint8_t kMinimumInsnSize = 2;
  if (!guest_map_shadow->IsExecutable(pc, kMinimumInsnSize)) {
    cache->SetTranslatedAndUnlock(pc, entry, kMinimumInsnSize, kSpecialHandler, {kEntryNoExec, 0});
    return;
  }

  // Now check the rest of the instruction based on its size.  It is now safe to dereference the
  // memory at pc because at least two bytes are within known executable memory.
  uint8_t first_insn_size = GetRiscv64InsnSize(pc);
  if (first_insn_size > kMinimumInsnSize &&
      !guest_map_shadow->IsExecutable(pc + kMinimumInsnSize, first_insn_size - kMinimumInsnSize)) {
    cache->SetTranslatedAndUnlock(pc, entry, first_insn_size, kSpecialHandler, {kEntryNoExec, 0});
    return;
  }

  bool success;
  HostCodePiece host_code_piece;
  size_t size;
  GuestCodeEntry::Kind kind;
  if (g_translation_mode == TranslationMode::kInterpretOnly) {
    std::tie(host_code_piece, size, kind) =
        std::make_tuple(HostCodePiece{kEntryInterpret, 0}, first_insn_size, kInterpreted);
  } else if (g_translation_mode == TranslationMode::kLiteTranslateOrFallbackToInterpret) {
    std::tie(success, host_code_piece, size, kind) = TryLiteTranslateAndInstallRegion(pc);
    if (!success) {
      std::tie(host_code_piece, size, kind) =
          std::make_tuple(HostCodePiece{kEntryInterpret, 0}, first_insn_size, kInterpreted);
    }
  } else if (g_translation_mode == TranslationMode::kTwoGear && kGear == TranslationGear::kFirst) {
    std::tie(success, host_code_piece, size, kind) = TryLiteTranslateAndInstallRegion(
        pc, {.enable_self_profiling = true, .counter_location = &(entry->invocation_counter)});
    if (!success) {
      // Heavy supports more insns than lite, so try to heavy optimize. If that fails, then
      // fallback to interpret.
      std::tie(success, host_code_piece, size, kind) = HeavyOptimizeRegion(pc);
      if (!success) {
        std::tie(host_code_piece, size, kind) =
            std::make_tuple(HostCodePiece{kEntryInterpret, 0}, first_insn_size, kInterpreted);
      }
    }
  } else if (g_translation_mode == TranslationMode::kHeavyOptimizeOrFallbackToInterpret ||
             g_translation_mode == TranslationMode::kHeavyOptimizeOrFallbackToLiteTranslator ||
             (g_translation_mode == TranslationMode::kTwoGear &&
              kGear == TranslationGear::kSecond)) {
    std::tie(success, host_code_piece, size, kind) = HeavyOptimizeRegion(pc);
    if (!success) {
      if (g_translation_mode == TranslationMode::kHeavyOptimizeOrFallbackToInterpret ||
          // Lite might fail since not all insns are implemented. Fallback to interpret.
          (g_translation_mode == TranslationMode::kTwoGear && kGear == TranslationGear::kSecond)) {
        std::tie(host_code_piece, size, kind) =
            std::make_tuple(HostCodePiece{kEntryInterpret, 0}, first_insn_size, kInterpreted);
      } else if (g_translation_mode == TranslationMode::kHeavyOptimizeOrFallbackToLiteTranslator) {
        std::tie(success, host_code_piece, size, kind) =
            TryLiteTranslateAndInstallRegion(pc, {.enable_self_profiling = false});
        // Lite might fail since not all insns are implemented. Fallback to interpret.
        if (!success) {
          std::tie(host_code_piece, size, kind) =
              std::make_tuple(HostCodePiece{kEntryInterpret, 0}, first_insn_size, kInterpreted);
        }
      }
    }
  } else {
    LOG_ALWAYS_FATAL("Unsupported translation mode %u", g_translation_mode);
  }

  // Now that we know the size of the translated block, make sure the entire memory block has
  // executable permission before saving it to the cache.
  // TODO(b/232598137): installing kEntryNoExec for the *current* pc is completely incorrect as
  // we've checked that it's executable above. The straightforward thing to do would be to
  // check executability of each instruction while translating, and generating signal raise
  // for non-executable ones. This handles the case when region contains conditional branch
  // to non-executable code.
  if (!guest_map_shadow->IsExecutable(pc, size)) {
    TRACE("setting partly executable region at [0x%zx, 0x%zx) as not executable!", pc, pc + size);
    cache->SetTranslatedAndUnlock(pc, entry, size, kSpecialHandler, {kEntryNoExec, 0});
    return;
  }

  cache->SetTranslatedAndUnlock(pc, entry, size, kind, host_code_piece);
}

// A wrapper to export a template function.
void TranslateRegionAtFirstGear(GuestAddr pc) {
  TranslateRegion<TranslationGear::kFirst>(pc);
}

// ATTENTION: This symbol gets called directly, without PLT. To keep text
// sharable we should prevent preemption of this symbol, so do not export it!
// TODO(b/232598137): may be set default visibility to protected instead?
extern "C" __attribute__((used, __visibility__("hidden"))) void berberis_HandleNotTranslated(
    ThreadState* state) {
  TranslateRegion(state->cpu.insn_addr);
}

extern "C" __attribute__((used, __visibility__("hidden"))) void berberis_HandleInterpret(
    ThreadState* state) {
  InterpretInsn(state);
}

extern "C" __attribute__((used, __visibility__("hidden"))) const void* berberis_GetDispatchAddress(
    ThreadState* state) {
  CHECK(state);
  if (ArePendingSignalsPresent(*state)) {
    return kEntryExitGeneratedCode;
  }
  return TranslationCache::GetInstance()->GetHostCodePtr(state->cpu.insn_addr)->load();
}

extern "C" __attribute__((used, __visibility__("hidden"))) void
berberis_HandleLightCounterThresholdReached(ThreadState* state) {
  CHECK(g_translation_mode == TranslationMode::kTwoGear);
  TranslateRegion<TranslationGear::kSecond>(state->cpu.insn_addr);
}

}  // namespace berberis
