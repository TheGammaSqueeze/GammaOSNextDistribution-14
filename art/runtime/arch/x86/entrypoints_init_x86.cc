/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <math.h>

#include "entrypoints/jni/jni_entrypoints.h"
#include "entrypoints/quick/quick_alloc_entrypoints.h"
#include "entrypoints/quick/quick_default_externs.h"
#include "entrypoints/quick/quick_default_init_entrypoints.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "interpreter/interpreter.h"

namespace art HIDDEN {

// Cast entrypoints.
extern "C" size_t art_quick_instance_of(mirror::Object* obj, mirror::Class* ref_class);

// Read barrier entrypoints.
// art_quick_read_barrier_mark_regX uses an non-standard calling
// convention: it expects its input in register X and returns its
// result in that same register, and saves and restores all
// caller-save registers.
extern "C" mirror::Object* art_quick_read_barrier_mark_reg00(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg01(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg02(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg03(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg05(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg06(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg07(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_slow(mirror::Object*, mirror::Object*, uint32_t);
extern "C" mirror::Object* art_quick_read_barrier_for_root_slow(GcRoot<mirror::Object>*);

void UpdateReadBarrierEntrypoints(QuickEntryPoints* qpoints, bool is_active) {
  qpoints->SetReadBarrierMarkReg00(is_active ? art_quick_read_barrier_mark_reg00 : nullptr);
  qpoints->SetReadBarrierMarkReg01(is_active ? art_quick_read_barrier_mark_reg01 : nullptr);
  qpoints->SetReadBarrierMarkReg02(is_active ? art_quick_read_barrier_mark_reg02 : nullptr);
  qpoints->SetReadBarrierMarkReg03(is_active ? art_quick_read_barrier_mark_reg03 : nullptr);
  qpoints->SetReadBarrierMarkReg05(is_active ? art_quick_read_barrier_mark_reg05 : nullptr);
  qpoints->SetReadBarrierMarkReg06(is_active ? art_quick_read_barrier_mark_reg06 : nullptr);
  qpoints->SetReadBarrierMarkReg07(is_active ? art_quick_read_barrier_mark_reg07 : nullptr);
}

void InitEntryPoints(JniEntryPoints* jpoints,
                     QuickEntryPoints* qpoints,
                     bool monitor_jni_entry_exit) {
  DefaultInitEntryPoints(jpoints, qpoints, monitor_jni_entry_exit);

  // Cast
  qpoints->SetInstanceofNonTrivial(art_quick_instance_of);
  qpoints->SetCheckInstanceOf(art_quick_check_instance_of);

  // More math.
  qpoints->SetCos(cos);
  qpoints->SetSin(sin);
  qpoints->SetAcos(acos);
  qpoints->SetAsin(asin);
  qpoints->SetAtan(atan);
  qpoints->SetAtan2(atan2);
  qpoints->SetPow(pow);
  qpoints->SetCbrt(cbrt);
  qpoints->SetCosh(cosh);
  qpoints->SetExp(exp);
  qpoints->SetExpm1(expm1);
  qpoints->SetHypot(hypot);
  qpoints->SetLog(log);
  qpoints->SetLog10(log10);
  qpoints->SetNextAfter(nextafter);
  qpoints->SetSinh(sinh);
  qpoints->SetTan(tan);
  qpoints->SetTanh(tanh);

  // Math
  qpoints->SetD2l(art_quick_d2l);
  qpoints->SetF2l(art_quick_f2l);
  qpoints->SetLdiv(art_quick_ldiv);
  qpoints->SetLmod(art_quick_lmod);
  qpoints->SetLmul(art_quick_lmul);
  qpoints->SetShlLong(art_quick_lshl);
  qpoints->SetShrLong(art_quick_lshr);
  qpoints->SetUshrLong(art_quick_lushr);

  // Intrinsics
  // qpoints->pIndexOf = nullptr;  // Not needed on x86
  qpoints->SetStringCompareTo(art_quick_string_compareto);
  qpoints->SetMemcpy(art_quick_memcpy);

  // Read barrier.
  UpdateReadBarrierEntrypoints(qpoints, /*is_active=*/ false);
  qpoints->SetReadBarrierMarkReg04(nullptr);  // Cannot use register 4 (ESP) to pass arguments.
  // x86 has only 8 core registers.
  qpoints->SetReadBarrierMarkReg08(nullptr);
  qpoints->SetReadBarrierMarkReg09(nullptr);
  qpoints->SetReadBarrierMarkReg10(nullptr);
  qpoints->SetReadBarrierMarkReg11(nullptr);
  qpoints->SetReadBarrierMarkReg12(nullptr);
  qpoints->SetReadBarrierMarkReg13(nullptr);
  qpoints->SetReadBarrierMarkReg14(nullptr);
  qpoints->SetReadBarrierMarkReg15(nullptr);
  qpoints->SetReadBarrierMarkReg16(nullptr);
  qpoints->SetReadBarrierMarkReg17(nullptr);
  qpoints->SetReadBarrierMarkReg18(nullptr);
  qpoints->SetReadBarrierMarkReg19(nullptr);
  qpoints->SetReadBarrierMarkReg20(nullptr);
  qpoints->SetReadBarrierMarkReg21(nullptr);
  qpoints->SetReadBarrierMarkReg22(nullptr);
  qpoints->SetReadBarrierMarkReg23(nullptr);
  qpoints->SetReadBarrierMarkReg24(nullptr);
  qpoints->SetReadBarrierMarkReg25(nullptr);
  qpoints->SetReadBarrierMarkReg26(nullptr);
  qpoints->SetReadBarrierMarkReg27(nullptr);
  qpoints->SetReadBarrierMarkReg28(nullptr);
  qpoints->SetReadBarrierMarkReg29(nullptr);
  qpoints->SetReadBarrierSlow(art_quick_read_barrier_slow);
  qpoints->SetReadBarrierForRootSlow(art_quick_read_barrier_for_root_slow);
}

}  // namespace art
