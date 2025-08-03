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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_PLATFORM_H_
#define BERBERIS_RUNTIME_PRIMITIVES_PLATFORM_H_

namespace berberis::host_platform {

#if defined(__i386__) && !defined(__x32__)
inline constexpr bool kIsX86_32 = true;
inline constexpr bool kIsX86_64 = false;
#endif
#if defined(__x86_64__) || defined(__x32__)
inline constexpr bool kIsX86_32 = false;
inline constexpr bool kIsX86_64 = true;
#endif
#if !defined(__i386__) && !defined(__x86_64__)
inline constexpr bool kIsX86_32 = false;
inline constexpr bool kIsX86_64 = false;
#endif
inline constexpr bool kIsX86 = (kIsX86_32 || kIsX86_64);

#if defined(__i386__) || defined(__x86_64__)
extern const struct PlatformCapabilities {
  bool kIsAuthenticAMD;
  bool kHasAES;
  bool kHasAVX;
  bool kHasBMI;
  bool kHasBMI2;
  bool kHasCLMUL;
  bool kHasF16C;
  bool kHasFMA;
  bool kHasFMA4;
  bool kHasLZCNT;
  bool kHasPDEP;
  bool kHasPOPCNT;
  bool kHasSHA;
  bool kHasSSE3;
  bool kHasSSSE3;
  bool kHasSSE4a;
  bool kHasSSE4_1;
  bool kHasSSE4_2;
} kPlatformCapabilities;
// These are "runtime constants": they can not be determined at compile
// time but each particular CPU has them set to true or false and that
// value can not ever change in the lifetime of a program.
inline const bool& kIsAuthenticAMD = kPlatformCapabilities.kIsAuthenticAMD;
inline const bool& kHasAES = kPlatformCapabilities.kHasAES;
inline const bool& kHasAVX = kPlatformCapabilities.kHasAVX;
inline const bool& kHasBMI = kPlatformCapabilities.kHasBMI;
inline const bool& kHasBMI2 = kPlatformCapabilities.kHasBMI2;
inline const bool& kHasCLMUL = kPlatformCapabilities.kHasCLMUL;
inline const bool& kHasF16C = kPlatformCapabilities.kHasF16C;
inline const bool& kHasFMA = kPlatformCapabilities.kHasFMA;
inline const bool& kHasFMA4 = kPlatformCapabilities.kHasFMA4;
inline const bool& kHasLZCNT = kPlatformCapabilities.kHasLZCNT;
inline const bool& kHasPDEP = kPlatformCapabilities.kHasPDEP;
inline const bool& kHasPOPCNT = kPlatformCapabilities.kHasPOPCNT;
inline const bool& kHasSHA = kPlatformCapabilities.kHasSHA;
inline const bool& kHasSSE3 = kPlatformCapabilities.kHasSSE3;
inline const bool& kHasSSSE3 = kPlatformCapabilities.kHasSSSE3;
inline const bool& kHasSSE4a = kPlatformCapabilities.kHasSSE4a;
inline const bool& kHasSSE4_1 = kPlatformCapabilities.kHasSSE4_1;
inline const bool& kHasSSE4_2 = kPlatformCapabilities.kHasSSE4_2;
#endif

}  // namespace berberis::host_platform

#endif  // BERBERIS_RUNTIME_PRIMITIVES_PLATFORM_H_
