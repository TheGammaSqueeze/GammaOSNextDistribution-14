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

#define GL_GLEXT_PROTOTYPES
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/proxy_loader/proxy_library_builder.h"
#include "native_bridge_proxy/android_api/libEGL/gl_common_defs.h"

static_assert(GLES2_AND_GLES3_DEBUG_CALLBACK_FUNCTION_KHR == GL_DEBUG_CALLBACK_FUNCTION_KHR,
              "EGL assumption about GLES2 define is incorrect.");

namespace berberis {

namespace {

void DoCustomTrampoline_glDebugMessageCallbackKHR(HostCode /* callee */, ProcessState* state) {
  auto [guest_callback, param] = GuestParamsValues<decltype(glDebugMessageCallbackKHR)>(state);
  GLDEBUGPROCKHR host_callback =
      WrapGuestFunction(guest_callback, "glDebugMessageCallbackKHR-callback");
  glDebugMessageCallbackKHR(host_callback, param);
}

void DoCustomTrampoline_glGetPointervKHR(HostCode /* callee */, ProcessState* state) {
  auto [pname, value] = GuestParamsValues<decltype(glGetPointervKHR)>(state);

  glGetPointervKHR(pname, value);

  if (pname == GL_DEBUG_CALLBACK_FUNCTION_KHR) {
    // If callback is registered by guest, return the original guest address,
    // since guest code may expect that (b/71363904).
    GuestAddr guest_addr = SlowFindGuestAddrByWrapperAddr(*value);
    if (guest_addr) {
      *value = reinterpret_cast<void*>(guest_addr);
    }
  }
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

DEFINE_INIT_PROXY_LIBRARY("libGLESv2.so")

}  // namespace

}  // namespace berberis
