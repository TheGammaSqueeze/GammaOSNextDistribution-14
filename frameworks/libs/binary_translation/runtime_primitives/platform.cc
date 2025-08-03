/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "berberis/runtime_primitives/platform.h"

#if defined(__i386__) || defined(__x86_64__)
#include <cpuid.h>
#endif

#include <cinttypes>

namespace berberis::host_platform {

namespace {

#if defined(__i386__) || defined(__x86_64__)
auto Init() {
  PlatformCapabilities platform_capabilities = {};
  unsigned int eax, ebx, ecx, edx;
  // Technically Zen,Zen+/Zen2 AMD CPUs support BMI2 and thus PDEP/PEXT instruction, but they are
  // not usable there: https://twitter.com/instlatx64/status/1322503571288559617
  // That's why we need special emulated CPUID flag for these instructions.
  bool use_pdep_if_present = true;
  __cpuid(0, eax, ebx, ecx, edx);
  if (((ebx == signature_AMD_ebx && ecx == signature_AMD_ecx && edx == signature_AMD_edx) ||
       (ebx == signature_HYGON_ebx && ecx == signature_HYGON_ecx && edx == signature_HYGON_edx))) {
    platform_capabilities.kIsAuthenticAMD = true;
    __cpuid(1, eax, ebx, ecx, edx);
    uint8_t family = (eax >> 8) & 0b1111;
    if (family == 0b1111) {
      family += (eax >> 20) & 0b11111111;
      if (family < 0x19) {
        use_pdep_if_present = false;
      }
    }
  } else {
    __cpuid(1, eax, ebx, ecx, edx);
  }
  platform_capabilities.kHasAES = ecx & bit_AES;
  platform_capabilities.kHasAVX = ecx & bit_AVX;
  platform_capabilities.kHasCLMUL = ecx & bit_PCLMUL;
  platform_capabilities.kHasF16C = ecx & bit_F16C;
  platform_capabilities.kHasFMA = ecx & bit_FMA;
  platform_capabilities.kHasPOPCNT = ecx & bit_POPCNT;
  platform_capabilities.kHasSSE3 = ecx & bit_SSE3;
  platform_capabilities.kHasSSSE3 = ecx & bit_SSSE3;
  platform_capabilities.kHasSSE4_1 = ecx & bit_SSE4_1;
  platform_capabilities.kHasSSE4_2 = ecx & bit_SSE4_2;
  __cpuid(0x80000001, eax, ebx, ecx, edx);
  platform_capabilities.kHasFMA4 = ecx & bit_FMA4;
  platform_capabilities.kHasLZCNT = ecx & bit_LZCNT;
  platform_capabilities.kHasSSE4a = ecx & bit_SSE4a;
  __cpuid_count(7, 0, eax, ebx, ecx, edx);
  platform_capabilities.kHasBMI = ebx & bit_BMI;
  platform_capabilities.kHasBMI2 = ebx & bit_BMI2;
  platform_capabilities.kHasPDEP = ebx & bit_BMI2 && use_pdep_if_present;
  platform_capabilities.kHasSHA = ebx & bit_SHA;
  return platform_capabilities;
}
#endif

}  // namespace

#if defined(__i386__) || defined(__x86_64__)
const PlatformCapabilities kPlatformCapabilities = Init();
#endif

}  // namespace berberis::host_platform
