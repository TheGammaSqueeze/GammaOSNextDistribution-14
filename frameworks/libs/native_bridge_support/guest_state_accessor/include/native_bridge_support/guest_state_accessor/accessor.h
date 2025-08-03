/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef NATIVE_BRIDGE_SUPPORT_GUEST_STATE_ACCESSOR_H_
#define NATIVE_BRIDGE_SUPPORT_GUEST_STATE_ACCESSOR_H_

#include <stdalign.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/cdefs.h>

__BEGIN_DECLS

// List of supported guest and host architecures
#define NATIVE_BRIDGE_ARCH_ARM 1
#define NATIVE_BRIDGE_ARCH_ARM64 2
#define NATIVE_BRIDGE_ARCH_RISCV64 4
#define NATIVE_BRIDGE_ARCH_X86 5
#define NATIVE_BRIDGE_ARCH_X86_64 6

#if defined(__LP64__)
struct NativeBridgeGuestRegsArm64 {
  uint64_t x[31];
  uint64_t ip;
  alignas(16) __uint128_t v[32];
};

struct NativeBridgeGuestRegsRiscv64 {
  uint64_t x[32];
  uint64_t f[32];
  alignas(16) __uint128_t v[32];
  uint64_t ip;
};
#endif

struct NativeBridgeGuestRegsArm {
  uint32_t r[16];
  alignas(16) uint64_t q[32];
};

// This structure represents guest registers for all supported architectures
// Use following fields depending on `arch` field value
// * NATIVE_BRIDGE_ARCH_ARM     -> .regs_arm
// * NATIVE_BRIDGE_ARCH_ARM64   -> .regs_arm64
// * NATIVE_BRIDGE_ARCH_RISCV64 -> .regs_riscv64
//
// Note that 64bit architectures are only supported for 64bit host platform.
struct NativeBridgeGuestRegs {
  uint64_t guest_arch;
  union {
#if defined(__LP64__)
    NativeBridgeGuestRegsArm64 regs_arm64;
    NativeBridgeGuestRegsRiscv64 regs_riscv64;
#endif
    NativeBridgeGuestRegsArm regs_arm;
  };
};

// Signature value for NativeBridgeGuestStateHeader::signature
#define NATIVE_BRIDGE_GUEST_STATE_SIGNATURE 0x5349'5245'4252'4542

// This is the header of guest_state, pointer to which is stored in
// TLS_SLOT_NATIVE_BRIDGE_GUEST_STATE and accessed by android debuggerd
// It can also be used by external debugging tools.
struct alignas(16) NativeBridgeGuestStateHeader {
  // Guest state signature for initial check must always be
  // equal to NATIVE_BRIDGE_GUEST_STATE_SIGNATURE
  uint64_t signature;
  // Guest and host architectures: defined as NATIVE_BRIDGE_ARCH_*
  uint32_t native_bridge_host_arch;
  uint32_t native_bridge_guest_arch;
  // The pointer and size are used by debugging/crash reporting tools to copy
  // the state from a (probably crashed) process.
  // The pointer to the implementation specific guest state.
  const void* guest_state_data;
  // Size of implementation specific representation of the guest state.
  size_t guest_state_data_size;
};

// Unsupported combination of guest and host architectures
#define NATIVE_BRIDGE_GUEST_STATE_ACCESSOR_ERROR_UNSUPPORTED_ARCH -1
// Unsupported provider
#define NATIVE_BRIDGE_GUEST_STATE_ACCESSOR_ERROR_UNSUPPORTED_PROVIDER -2
// Unsupported guest state version
#define NATIVE_BRIDGE_GUEST_STATE_ACCESSOR_ERROR_UNSUPPORTED_VERSION -3
// Invalid guest state
#define NATIVE_BRIDGE_GUEST_STATE_ACCESSOR_ERROR_INVALID_STATE -11

// Returns non-zero error code in case of error, 0 on success. Updates
// `guest_regs` structure with values from internal representation of
// the guest state.
//
// `guest_state_data` points to the implementation specific guest_state
int LoadGuestStateRegisters(const void* guest_state_data,
                            size_t guest_state_data_size,
                            NativeBridgeGuestRegs* guest_regs);

__END_DECLS

#endif  // NATIVE_BRIDGE_SUPPORT_GUEST_STATE_ACCESSOR_H_
