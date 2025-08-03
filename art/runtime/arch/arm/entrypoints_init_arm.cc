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
#include <string.h>

#include "arch/arm/asm_support_arm.h"
#include "base/bit_utils.h"
#include "entrypoints/entrypoint_utils.h"
#include "entrypoints/jni/jni_entrypoints.h"
#include "entrypoints/math_entrypoints.h"
#include "entrypoints/quick/quick_alloc_entrypoints.h"
#include "entrypoints/quick/quick_default_externs.h"
#include "entrypoints/quick/quick_default_init_entrypoints.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "interpreter/interpreter.h"

namespace art HIDDEN {

// Cast entrypoints.
extern "C" size_t artInstanceOfFromCode(mirror::Object* obj, mirror::Class* ref_class);

// Read barrier entrypoints.
// art_quick_read_barrier_mark_regX uses an non-standard calling
// convention: it expects its input in register X and returns its
// result in that same register, and saves and restores all
// caller-save registers.
extern "C" mirror::Object* art_quick_read_barrier_mark_reg00(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg01(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg02(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg03(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg04(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg05(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg06(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg07(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg08(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg09(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg10(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg11(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_reg12(mirror::Object*);

extern "C" mirror::Object* art_quick_read_barrier_mark_introspection(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_introspection_narrow(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_introspection_arrays(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_introspection_gc_roots_wide(mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_introspection_gc_roots_narrow(
    mirror::Object*);
extern "C" mirror::Object* art_quick_read_barrier_mark_introspection_intrinsic_cas(mirror::Object*);

// Used by soft float.
// Single-precision FP arithmetics.
extern "C" float fmodf(float a, float b);              // REM_FLOAT[_2ADDR]
// Double-precision FP arithmetics.
extern "C" double fmod(double a, double b);            // REM_DOUBLE[_2ADDR]

// Used by hard float.
extern "C" float art_quick_fmodf(float a, float b);    // REM_FLOAT[_2ADDR]
extern "C" double art_quick_fmod(double a, double b);  // REM_DOUBLE[_2ADDR]

// Integer arithmetics.
extern "C" int __aeabi_idivmod(int32_t, int32_t);  // [DIV|REM]_INT[_2ADDR|_LIT8|_LIT16]

// Long long arithmetics - REM_LONG[_2ADDR] and DIV_LONG[_2ADDR]
extern "C" int64_t __aeabi_ldivmod(int64_t, int64_t);

void UpdateReadBarrierEntrypoints(QuickEntryPoints* qpoints, bool is_active) {
  qpoints->SetReadBarrierMarkReg00(is_active ? art_quick_read_barrier_mark_reg00 : nullptr);
  qpoints->SetReadBarrierMarkReg01(is_active ? art_quick_read_barrier_mark_reg01 : nullptr);
  qpoints->SetReadBarrierMarkReg02(is_active ? art_quick_read_barrier_mark_reg02 : nullptr);
  qpoints->SetReadBarrierMarkReg03(is_active ? art_quick_read_barrier_mark_reg03 : nullptr);
  qpoints->SetReadBarrierMarkReg04(is_active ? art_quick_read_barrier_mark_reg04 : nullptr);
  qpoints->SetReadBarrierMarkReg05(is_active ? art_quick_read_barrier_mark_reg05 : nullptr);
  qpoints->SetReadBarrierMarkReg06(is_active ? art_quick_read_barrier_mark_reg06 : nullptr);
  qpoints->SetReadBarrierMarkReg07(is_active ? art_quick_read_barrier_mark_reg07 : nullptr);
  qpoints->SetReadBarrierMarkReg08(is_active ? art_quick_read_barrier_mark_reg08 : nullptr);
  qpoints->SetReadBarrierMarkReg09(is_active ? art_quick_read_barrier_mark_reg09 : nullptr);
  qpoints->SetReadBarrierMarkReg10(is_active ? art_quick_read_barrier_mark_reg10 : nullptr);
  qpoints->SetReadBarrierMarkReg11(is_active ? art_quick_read_barrier_mark_reg11 : nullptr);

  if (gUseReadBarrier && kUseBakerReadBarrier) {
    // For the alignment check, strip the Thumb mode bit.
    DCHECK_ALIGNED(reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection) - 1u,
                   256u);
    // Check the field narrow entrypoint offset from the introspection entrypoint.
    intptr_t narrow_diff =
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection_narrow) -
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection);
    DCHECK_EQ(BAKER_MARK_INTROSPECTION_FIELD_LDR_NARROW_ENTRYPOINT_OFFSET, narrow_diff);
    // Check array switch cases offsets from the introspection entrypoint.
    intptr_t array_diff =
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection_arrays) -
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection);
    DCHECK_EQ(BAKER_MARK_INTROSPECTION_ARRAY_SWITCH_OFFSET, array_diff);
    // Check the GC root entrypoint offsets from the introspection entrypoint.
    intptr_t gc_roots_wide_diff =
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection_gc_roots_wide) -
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection);
    DCHECK_EQ(BAKER_MARK_INTROSPECTION_GC_ROOT_LDR_WIDE_ENTRYPOINT_OFFSET, gc_roots_wide_diff);
    intptr_t gc_roots_narrow_diff =
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection_gc_roots_narrow) -
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection);
    DCHECK_EQ(BAKER_MARK_INTROSPECTION_GC_ROOT_LDR_NARROW_ENTRYPOINT_OFFSET, gc_roots_narrow_diff);
    intptr_t intrinsic_cas_diff =
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection_intrinsic_cas) -
        reinterpret_cast<intptr_t>(art_quick_read_barrier_mark_introspection);
    DCHECK_EQ(BAKER_MARK_INTROSPECTION_INTRINSIC_CAS_ENTRYPOINT_OFFSET, intrinsic_cas_diff);
    // The register 12, i.e. IP, is reserved, so there is no art_quick_read_barrier_mark_reg12.
    // We're using the entry to hold a pointer to the introspection entrypoint instead.
    qpoints->SetReadBarrierMarkReg12(
        is_active ? art_quick_read_barrier_mark_introspection : nullptr);
  }
}

void InitEntryPoints(JniEntryPoints* jpoints,
                     QuickEntryPoints* qpoints,
                     bool monitor_jni_entry_exit) {
  DefaultInitEntryPoints(jpoints, qpoints, monitor_jni_entry_exit);

  // Cast
  qpoints->SetInstanceofNonTrivial(artInstanceOfFromCode);
  qpoints->SetCheckInstanceOf(art_quick_check_instance_of);

  // Math
  qpoints->SetIdivmod(__aeabi_idivmod);
  qpoints->SetLdiv(__aeabi_ldivmod);
  qpoints->SetLmod(__aeabi_ldivmod);  // result returned in r2:r3
  qpoints->SetLmul(art_quick_mul_long);
  qpoints->SetShlLong(art_quick_shl_long);
  qpoints->SetShrLong(art_quick_shr_long);
  qpoints->SetUshrLong(art_quick_ushr_long);
  qpoints->SetFmod(art_quick_fmod);
  qpoints->SetFmodf(art_quick_fmodf);
  qpoints->SetD2l(art_quick_d2l);
  qpoints->SetF2l(art_quick_f2l);
  qpoints->SetL2f(art_quick_l2f);

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
  // The ARM StringCompareTo intrinsic does not call the runtime.
  qpoints->SetStringCompareTo(nullptr);
  qpoints->SetMemcpy(memcpy);

  // Read barrier.
  UpdateReadBarrierEntrypoints(qpoints, /*is_active=*/ false);
  qpoints->SetReadBarrierMarkReg12(nullptr);  // Cannot use register 12 (IP) to pass arguments.
  qpoints->SetReadBarrierMarkReg13(nullptr);  // Cannot use register 13 (SP) to pass arguments.
  qpoints->SetReadBarrierMarkReg14(nullptr);  // Cannot use register 14 (LR) to pass arguments.
  qpoints->SetReadBarrierMarkReg15(nullptr);  // Cannot use register 15 (PC) to pass arguments.
  // ARM has only 16 core registers.
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
  qpoints->SetReadBarrierSlow(artReadBarrierSlow);
  qpoints->SetReadBarrierForRootSlow(artReadBarrierForRootSlow);
}

}  // namespace art
