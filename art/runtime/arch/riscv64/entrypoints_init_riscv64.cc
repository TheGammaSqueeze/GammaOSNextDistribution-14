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

#include  <math.h>

#include "entrypoints/quick/quick_default_init_entrypoints.h"
#include "entrypoints/quick/quick_entrypoints.h"

namespace art HIDDEN {

// Cast entrypoints.
extern "C" size_t artInstanceOfFromCode(mirror::Object* obj, mirror::Class* ref_class);

// Read barrier entrypoints.
// art_quick_read_barrier_mark_regX uses an non-standard calling convention: it
// expects its input in register X and returns its result in that same register,
// and saves and restores all other registers.
// No read barrier for X0 (Zero), X1 (RA), X2 (SP), X3 (GP) and X4 (TP).
extern "C" mirror::Object* art_quick_read_barrier_mark_reg05(mirror::Object*);  // t0/x5
extern "C" mirror::Object* art_quick_read_barrier_mark_reg06(mirror::Object*);  // t1/x6
extern "C" mirror::Object* art_quick_read_barrier_mark_reg07(mirror::Object*);  // t2/x7
extern "C" mirror::Object* art_quick_read_barrier_mark_reg08(mirror::Object*);  // t3/x8
// No read barrier for X9 (S1/xSELF).
extern "C" mirror::Object* art_quick_read_barrier_mark_reg10(mirror::Object*);  // a0/x10
extern "C" mirror::Object* art_quick_read_barrier_mark_reg11(mirror::Object*);  // a1/x11
extern "C" mirror::Object* art_quick_read_barrier_mark_reg12(mirror::Object*);  // a2/x12
extern "C" mirror::Object* art_quick_read_barrier_mark_reg13(mirror::Object*);  // a3/x13
extern "C" mirror::Object* art_quick_read_barrier_mark_reg14(mirror::Object*);  // a4/x14
extern "C" mirror::Object* art_quick_read_barrier_mark_reg15(mirror::Object*);  // a5/x15
extern "C" mirror::Object* art_quick_read_barrier_mark_reg16(mirror::Object*);  // a6/x16
extern "C" mirror::Object* art_quick_read_barrier_mark_reg17(mirror::Object*);  // a7/x17
extern "C" mirror::Object* art_quick_read_barrier_mark_reg18(mirror::Object*);  // s2/x18
extern "C" mirror::Object* art_quick_read_barrier_mark_reg19(mirror::Object*);  // s3/x19
extern "C" mirror::Object* art_quick_read_barrier_mark_reg20(mirror::Object*);  // s4/x20
extern "C" mirror::Object* art_quick_read_barrier_mark_reg21(mirror::Object*);  // s5/x21
extern "C" mirror::Object* art_quick_read_barrier_mark_reg22(mirror::Object*);  // s6/x22
extern "C" mirror::Object* art_quick_read_barrier_mark_reg23(mirror::Object*);  // s7/x23
extern "C" mirror::Object* art_quick_read_barrier_mark_reg24(mirror::Object*);  // s8/x24
extern "C" mirror::Object* art_quick_read_barrier_mark_reg25(mirror::Object*);  // s9/x25
extern "C" mirror::Object* art_quick_read_barrier_mark_reg26(mirror::Object*);  // s10/x26
extern "C" mirror::Object* art_quick_read_barrier_mark_reg27(mirror::Object*);  // s11/x27
extern "C" mirror::Object* art_quick_read_barrier_mark_reg28(mirror::Object*);  // t3/x28
extern "C" mirror::Object* art_quick_read_barrier_mark_reg29(mirror::Object*);  // t4/x29
extern "C" mirror::Object* art_quick_read_barrier_mark_reg30(mirror::Object*);  // t5/x30
extern "C" mirror::Object* art_quick_read_barrier_mark_reg31(mirror::Object*);  // t6/x31

void UpdateReadBarrierEntrypoints(QuickEntryPoints* qpoints, bool is_active) {
  // No read barrier for X0 (Zero), X1 (RA), X2 (SP), X3 (GP) and X4 (TP).
  qpoints->SetReadBarrierMarkReg05(is_active ? art_quick_read_barrier_mark_reg05 : nullptr);
  qpoints->SetReadBarrierMarkReg06(is_active ? art_quick_read_barrier_mark_reg06 : nullptr);
  qpoints->SetReadBarrierMarkReg07(is_active ? art_quick_read_barrier_mark_reg07 : nullptr);
  qpoints->SetReadBarrierMarkReg08(is_active ? art_quick_read_barrier_mark_reg08 : nullptr);
  // No read barrier for X9 (S1/xSELF).
  qpoints->SetReadBarrierMarkReg10(is_active ? art_quick_read_barrier_mark_reg10 : nullptr);
  qpoints->SetReadBarrierMarkReg11(is_active ? art_quick_read_barrier_mark_reg11 : nullptr);
  qpoints->SetReadBarrierMarkReg12(is_active ? art_quick_read_barrier_mark_reg12 : nullptr);
  qpoints->SetReadBarrierMarkReg13(is_active ? art_quick_read_barrier_mark_reg13 : nullptr);
  qpoints->SetReadBarrierMarkReg14(is_active ? art_quick_read_barrier_mark_reg14 : nullptr);
  qpoints->SetReadBarrierMarkReg15(is_active ? art_quick_read_barrier_mark_reg15 : nullptr);
  qpoints->SetReadBarrierMarkReg16(is_active ? art_quick_read_barrier_mark_reg16 : nullptr);
  qpoints->SetReadBarrierMarkReg17(is_active ? art_quick_read_barrier_mark_reg17 : nullptr);
  qpoints->SetReadBarrierMarkReg18(is_active ? art_quick_read_barrier_mark_reg18 : nullptr);
  qpoints->SetReadBarrierMarkReg19(is_active ? art_quick_read_barrier_mark_reg19 : nullptr);
  qpoints->SetReadBarrierMarkReg20(is_active ? art_quick_read_barrier_mark_reg20 : nullptr);
  qpoints->SetReadBarrierMarkReg21(is_active ? art_quick_read_barrier_mark_reg21 : nullptr);
  qpoints->SetReadBarrierMarkReg22(is_active ? art_quick_read_barrier_mark_reg22 : nullptr);
  qpoints->SetReadBarrierMarkReg23(is_active ? art_quick_read_barrier_mark_reg23 : nullptr);
  qpoints->SetReadBarrierMarkReg24(is_active ? art_quick_read_barrier_mark_reg24 : nullptr);
  qpoints->SetReadBarrierMarkReg25(is_active ? art_quick_read_barrier_mark_reg25 : nullptr);
  qpoints->SetReadBarrierMarkReg26(is_active ? art_quick_read_barrier_mark_reg26 : nullptr);
  qpoints->SetReadBarrierMarkReg27(is_active ? art_quick_read_barrier_mark_reg27 : nullptr);
  qpoints->SetReadBarrierMarkReg28(is_active ? art_quick_read_barrier_mark_reg28 : nullptr);
  qpoints->SetReadBarrierMarkReg29(is_active ? art_quick_read_barrier_mark_reg29 : nullptr);
  // Note: Entrypoints for registers X30 (T5) and T31 (T6) are stored in entries
  // for X0 (Zero) and X1 (RA) because these are not valid registers for marking
  // and we currently have slots only up to register 29.
  qpoints->SetReadBarrierMarkReg00(is_active ? art_quick_read_barrier_mark_reg30 : nullptr);
  qpoints->SetReadBarrierMarkReg01(is_active ? art_quick_read_barrier_mark_reg31 : nullptr);
}

void InitEntryPoints(JniEntryPoints* jpoints,
                     QuickEntryPoints* qpoints,
                     bool monitor_jni_entry_exit) {
  DefaultInitEntryPoints(jpoints, qpoints, monitor_jni_entry_exit);

  // Cast
  qpoints->SetInstanceofNonTrivial(artInstanceOfFromCode);
  qpoints->SetCheckInstanceOf(art_quick_check_instance_of);

  // Math
  // TODO(riscv64): null entrypoints not needed for riscv64 - using generated code.
  qpoints->SetCmpgDouble(nullptr);
  qpoints->SetCmpgFloat(nullptr);
  qpoints->SetCmplDouble(nullptr);
  qpoints->SetCmplFloat(nullptr);
  qpoints->SetFmod(fmod);
  qpoints->SetL2d(nullptr);
  qpoints->SetFmodf(fmodf);
  qpoints->SetL2f(nullptr);
  qpoints->SetD2iz(nullptr);
  qpoints->SetF2iz(nullptr);
  qpoints->SetIdivmod(nullptr);
  qpoints->SetD2l(nullptr);
  qpoints->SetF2l(nullptr);
  qpoints->SetLdiv(nullptr);
  qpoints->SetLmod(nullptr);
  qpoints->SetLmul(nullptr);
  qpoints->SetShlLong(nullptr);
  qpoints->SetShrLong(nullptr);
  qpoints->SetUshrLong(nullptr);

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

  // Intrinsics
  qpoints->SetIndexOf(art_quick_indexof);
  // TODO(riscv64): More intrinsics.

  // Read barrier.
  UpdateReadBarrierEntrypoints(qpoints, /*is_active=*/ false);
  qpoints->SetReadBarrierSlow(artReadBarrierSlow);
  qpoints->SetReadBarrierForRootSlow(artReadBarrierForRootSlow);
}

}  // namespace art
