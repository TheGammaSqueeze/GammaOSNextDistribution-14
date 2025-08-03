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

#include "berberis/guest_os_primitives/guest_setjmp.h"

#include <csetjmp>
#include <cstdint>
#include <cstring>  // memcpy

#include "berberis/base/host_signal.h"
#include "berberis/guest_state/guest_state.h"

namespace berberis {

namespace {

// jmp_buf format is totally Bionic-private (see bionic/libc/arch-riscv64/bionic/setjmp.S)
// We don't have to use the original format as save/restore is only done here.
// Still, let's keep it compatible with some release as it might help debugging...
//
// word   name            description
// 0      sigflag/cookie  setjmp cookie in top 31 bits, signal mask flag in low bit
// 1      sigmask         64-bit signal mask
// 2      ra
// 3      sp
// 4      gp
// 5      s0
// ......
// 16     s11
// 17     fs0
// ......
// 28     fs11
// 29     checksum
// _JBLEN: defined in bionic/libc/include/setjmp.h

const int kJmpBufSigFlagAndCookieWord = 0;
const int kJmpBufSigMaskWord = 1;
const int kJmpBufRaWord = 2;
const int kJmpBufCoreBaseWord = 5;
const int kJmpBufFloatingPointBaseWord = 17;
const int kJmpBufChecksumWord = 29;
// jmp_buf should be at least 32 words long.
// Use the last word to store the address of the host jmp_buf.
const int kJmpBufHostBufWord = 31;

// jmp_buf cookie can be anything but 0 (see bionic/tests/setjmp_test.cpp: setjmp_cookie)
// ATTENTION: Keep low bit 0 for signal mask flag.
const uint64_t kJmpBufCookie = 0x123210ULL;

uint64_t CalcJumpBufChecksum(const uint64_t* buf) {
  uint64_t res = 0;
  for (int i = 0; i < kJmpBufChecksumWord; ++i) {
    res ^= buf[i];
  }
  return res;
}

}  // namespace

void SaveRegsToJumpBuf(const ThreadState* state, void* guest_jmp_buf, int save_sig_mask) {
  uint64_t* buf = reinterpret_cast<uint64_t*>(guest_jmp_buf);

  // Clear the buffer in case the format has gaps.
  memset(buf, 0, kJmpBufChecksumWord * sizeof(uint64_t));

  // Cookie, signal flag, signal mask
  buf[kJmpBufSigFlagAndCookieWord] = kJmpBufCookie;
  if (save_sig_mask) {
    buf[kJmpBufSigFlagAndCookieWord] |= 0x1;
    RTSigprocmaskSyscallOrDie(
        SIG_SETMASK, nullptr, reinterpret_cast<HostSigset*>(buf + kJmpBufSigMaskWord));
  }

  // Copy contiguous sets of registers below.
  constexpr size_t kXRegSize = sizeof(state->cpu.x[0]);
  constexpr size_t kFRegSize = sizeof(state->cpu.f[0]);

  // ra, sp, gp are contiguous.
  memcpy(buf + kJmpBufRaWord, state->cpu.x + RA, 3 * kXRegSize);

  // s0 - s1
  memcpy(buf + kJmpBufCoreBaseWord, state->cpu.x + S0, 2 * kXRegSize);
  // s2 - s11
  memcpy(buf + kJmpBufCoreBaseWord + 2, state->cpu.x + S2, 10 * kXRegSize);

  // fs0 - fs1
  memcpy(buf + kJmpBufFloatingPointBaseWord, state->cpu.f + FS0, 2 * kFRegSize);
  // fs2 - fs11
  memcpy(buf + kJmpBufFloatingPointBaseWord + 2, state->cpu.f + FS2, 10 * kFRegSize);

  // Checksum
  buf[kJmpBufChecksumWord] = CalcJumpBufChecksum(buf);
}

void RestoreRegsFromJumpBuf(ThreadState* state, void* guest_jmp_buf, int retval) {
  const uint64_t* buf = reinterpret_cast<const uint64_t*>(guest_jmp_buf);

  // Checksum
  if (buf[kJmpBufChecksumWord] != CalcJumpBufChecksum(buf)) {
    LOG_ALWAYS_FATAL("setjmp checksum mismatch");
  }

  // Cookie
  if ((buf[kJmpBufSigFlagAndCookieWord] & ~0x1) != kJmpBufCookie) {
    LOG_ALWAYS_FATAL("setjmp cookie mismatch");
  }

  // Signal mask
  if (buf[kJmpBufSigFlagAndCookieWord] & 0x1) {
    RTSigprocmaskSyscallOrDie(
        SIG_SETMASK, reinterpret_cast<const HostSigset*>(buf + kJmpBufSigMaskWord), nullptr);
  }

  // Copy contiguous sets of registers below.
  constexpr size_t kXRegSize = sizeof(state->cpu.x[0]);
  constexpr size_t kFRegSize = sizeof(state->cpu.f[0]);

  // ra, sp, gp
  memcpy(state->cpu.x + RA, buf + kJmpBufRaWord, 3 * kXRegSize);

  // s0 - s1
  memcpy(state->cpu.x + S0, buf + kJmpBufCoreBaseWord, 2 * kXRegSize);
  // s2 - s11
  memcpy(state->cpu.x + S2, buf + kJmpBufCoreBaseWord + 2, 10 * kXRegSize);

  // fs0 - fs1
  memcpy(state->cpu.f + S0, buf + kJmpBufFloatingPointBaseWord, 2 * kFRegSize);
  // fs2 - fs11
  memcpy(state->cpu.f + S2, buf + kJmpBufFloatingPointBaseWord + 2, 10 * kFRegSize);

  // Function return
  CPUState& cpu = state->cpu;
  SetInsnAddr(cpu, GetLinkRegister(cpu));
  SetXReg<A0>(cpu, retval);
}

jmp_buf** GetHostJmpBufPtr(void* guest_jmp_buf) {
  uint64_t* buf = reinterpret_cast<uint64_t*>(guest_jmp_buf);
  return reinterpret_cast<jmp_buf**>(buf + kJmpBufHostBufWord);
}

}  // namespace berberis
