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

#include <dlfcn.h>
#include <stdint.h>

#include <android/imagedecoder.h>

#include "berberis/base/bit_util.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace berberis {

namespace {

// int AImageDecoder_setCrop(AImageDecoder*, ARect)
void DoCustomTrampoline_AImageDecoder_setCrop(HostCode /* callee */, ProcessState* state) {
#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM)
  // On arm, ARect is passed the same way as 4x int32_t.
  auto [decoder, left, top, right, bottom] =
      GuestParamsValues<int(AImageDecoder*, int32_t, int32_t, int32_t, int32_t)>(state);
#elif defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64) || defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64)
  // ARect is passed the same way as 2x int64_t.
  auto [decoder, left_and_top, right_and_bottom] =
      GuestParamsValues<int(AImageDecoder*, int64_t, int64_t)>(state);
  int32_t left = left_and_top;
  int32_t top = left_and_top >> 32;
  int32_t right = right_and_bottom;
  int32_t bottom = right_and_bottom >> 32;
#else
#error "Unknown guest arch"
#endif
  ARect crop{left, top, right, bottom};
  auto&& [ret] = GuestReturnReference<decltype(AImageDecoder_setCrop)>(state);
  ret = AImageDecoder_setCrop(decoder, crop);
}

// ARect AImageDecoderFrameInfo_getFrameRect(const AImageDecoderFrameInfo* info)
void DoCustomTrampoline_AImageDecoderFrameInfo_getFrameRect(HostCode /* callee */,
                                                            ProcessState* state) {
#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM)
  // ARect is returned by pointer which GuestArgumentInfo normally supports.
  using FuncType = decltype(AImageDecoderFrameInfo_getFrameRect);
  using ResType = ARect;
#elif defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64) || defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64)
  // ARect is returned in two integer registers which is equivalent to returning __uint128_t.
  using FuncType = __uint128_t(const AImageDecoderFrameInfo* info);
  using ResType = __uint128_t;
#else
#error "Unknown guest arch"
#endif
  auto [info] = GuestParamsValues<FuncType>(state);
  auto&& [ret] = GuestReturnReference<FuncType>(state);
  ret = bit_cast<ResType>(AImageDecoderFrameInfo_getFrameRect(info));
}

#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM) && defined(__i386__)

#include "trampolines_arm_to_x86-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64) && defined(__x86_64__)

#include "trampolines_arm64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64) && defined(__x86_64__)

#include "trampolines_riscv64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#else

#error "Unknown guest/host arch combination"

#endif

DEFINE_INIT_PROXY_LIBRARY("libjnigraphics.so")

}  // namespace

}  // namespace berberis
