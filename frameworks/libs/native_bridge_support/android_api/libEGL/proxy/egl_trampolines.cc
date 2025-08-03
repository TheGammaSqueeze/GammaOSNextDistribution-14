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

#include <string.h>  // strcmp

#include "EGL/egl.h"
#include "EGL/eglext.h"

#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_arguments.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/proxy_loader/proxy_library_builder.h"
#include "berberis/runtime_primitives/known_guest_function_wrapper.h"
#include "berberis/runtime_primitives/runtime_library.h"
#include "berberis/base/tracing.h"
#include "native_bridge_proxy/android_api/libEGL/gl_common_defs.h"

namespace berberis {

namespace {

// glDebugMessageCallback
// glDebugMessageCallbackARB
// glDebugMessageCallbackKHR
void DoCustomTrampolineWithThunk_glDebugMessageCallback(HostCode callee, ProcessState* state) {
  // Prototypes are not defined in EGL headers - even though library itself is supposed to know
  // about these functions.
  using Callback = void (*)(uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, void*, void*);
  using PFN_callee = void (*)(Callback, void* Param);
  PFN_callee callee_function = AsFuncPtr(callee);

  auto [guest_callback, param] = GuestParamsValues<PFN_callee>(state);

  Callback host_callback = WrapGuestFunction(guest_callback, "glDebugMessageCallback-callback");

  callee_function(host_callback, param);
}
const auto DoCustomTrampolineWithThunk_glDebugMessageCallbackARB =
    DoCustomTrampolineWithThunk_glDebugMessageCallback;
const auto DoCustomTrampolineWithThunk_glDebugMessageCallbackKHR =
    DoCustomTrampolineWithThunk_glDebugMessageCallback;

void RunGuest_glDebugMessageCallback(GuestAddr pc, GuestArgumentBuffer* buf) {
  // Prototypes are not defined in EGL headers - even though library itself is supposed to know
  // about these functions.
  using Callback = void (*)(uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, void*, void*);
  using PFN_callee = void (*)(Callback, void* Param);

  auto [callback, user_param] = HostArgumentsValues<PFN_callee>(buf);

  WrapHostFunction(callback, "glDebugMessageCallback_DEBUGPROC");
  RunGuestCall(pc, buf);
}

const auto RunGuest_glDebugMessageCallbackARB = RunGuest_glDebugMessageCallback;
const auto RunGuest_glDebugMessageCallbackKHR = RunGuest_glDebugMessageCallback;

void RunGuest_glGetPointerv(GuestAddr pc, GuestArgumentBuffer* buf) {
  // Note: we don't need to do any tricks here yet since when Host function is converted to guest
  // function it's actuall address doesn't change.
  RunGuestCall(pc, buf);
}

const auto RunGuest_glGetPointervEXT = RunGuest_glGetPointerv;
const auto RunGuest_glGetPointervKHR = RunGuest_glGetPointerv;

// glGetPointerv
// glGetPointervEXT
// glGetPointervKHR
void DoCustomTrampolineWithThunk_glGetPointerv(HostCode callee, ProcessState* state) {
  // Prototypes are not defined in EGL headers - even though library itself is supposed to know
  // about these functions.
  using PFN_callee = void (*)(uint32_t pname, void** params);
  PFN_callee callee_function = AsFuncPtr(callee);

  auto [pname, value] = GuestParamsValues<PFN_callee>(state);

  callee_function(pname, value);

  // This maybe any version of GLES, so compare to all possible key values in different versions.
  if (pname == EGL_DEBUG_CALLBACK_KHR || pname == GLES2_AND_GLES3_DEBUG_CALLBACK_FUNCTION_KHR) {
    // If callback is registered by guest, return the original guest address,
    // since guest code may expect that (b/71363904).
    GuestAddr guest_addr = SlowFindGuestAddrByWrapperAddr(*value);
    if (guest_addr) {
      *value = reinterpret_cast<void*>(guest_addr);
    }
  }
}
const auto DoCustomTrampolineWithThunk_glGetPointervEXT = DoCustomTrampolineWithThunk_glGetPointerv;
const auto DoCustomTrampolineWithThunk_glGetPointervKHR = DoCustomTrampolineWithThunk_glGetPointerv;

// Forward decl for android_api/egl/opengl_trampolines-inl.h:kOpenGLTrampolines.
void DoCustomTrampolineWithThunk_eglGetProcAddress(HostCode callee, ProcessState* state);
void RunGuest_eglGetProcAddress(GuestAddr pc, GuestArgumentBuffer* buf);

#include "opengl_trampolines-inl.h"  // generated file NOLINT [build/include]

bool WrapEglHostFunction(const char* name, HostCode function) {
  if (name == nullptr || function == nullptr) {
    return false;
  }

  // TODO(eaeltsin): kOpenGLTrampolines are sorted, might use binary search!
  for (const auto& t : kOpenGLTrampolines) {
    if (strcmp(name, t.name) == 0) {
      if (!t.marshal_and_call) {
        break;
      }

      WrapHostFunctionImpl(function, t.marshal_and_call, name);
      return true;
    }
  }

  return false;
}

HostCode WrapEglGuestFunction(const char* name, GuestAddr function) {
  if (name == nullptr || function == kNullGuestAddr) {
    return nullptr;
  }

  // TODO(eaeltsin): kOpenGLTrampolines are sorted, might use binary search!
  for (const auto& t : kOpenGLTrampolines) {
    if (strcmp(name, t.name) == 0) {
      if (!t.wrapper) {
        break;
      }

      return t.wrapper(function);
    }
  }

  return nullptr;
}

// void (*eglGetProcAddress(char const* procname))();
void DoCustomTrampolineWithThunk_eglGetProcAddress(HostCode callee, ProcessState* state) {
  using PFN_callee = decltype(&eglGetProcAddress);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [proc_name] = GuestParamsValues<PFN_callee>(state);

  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(proc_name);
  if (!ToGuestAddr(ret)) {
    return;
  }

  if (!WrapEglHostFunction(proc_name, ToHostCode(ret))) {
    // Host proc exists but we failed to wrap it.  That's not fatal error because application
    // may have a fallback code if certain GLES4-5-6 function is not available in our translator
    // but is provided by drivers... but we want to know about it from logs anyway.
    ALOGE("eglGetProcAddress(\"%s\") failed", static_cast<const char*>(proc_name));
    TRACE("eglGetProcAddress(\"%s\") failed", static_cast<const char*>(proc_name));
    ret = 0;
    return;
  }
}

void RunGuest_eglGetProcAddress(GuestAddr pc, GuestArgumentBuffer* buf) {
  auto [proc_name] = HostArgumentsValues<decltype(eglGetProcAddress)>(buf);
  RunGuestCall(pc, buf);
  auto&& [result] = HostResultReference<decltype(eglGetProcAddress)>(buf);
  if (!WrapEglHostFunction(proc_name, ToHostCode(GuestType(result)))) {
    result = nullptr;
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

using PFNEGLGETNEXTLAYERPROCADDRESSPROC = void* (*)(void*, const char*);

using EGLFuncPointer = __eglMustCastToProperFunctionPointerType;

using AndroidGLESLayer_InitializePtr =
    EGLAPI void (*)(void* layer_id, PFNEGLGETNEXTLAYERPROCADDRESSPROC get_next_layer_proc_address);
using AndroidGLESLayer_GetProcAddressPtr = EGLAPI void* (*)(const char* funcName,
                                                            EGLFuncPointer next);

void DoCustomTrampolineWithThunk_eglNextLayerProcAddressProc(HostCode callee, ProcessState* state) {
  auto [layer_id, proc_name] = GuestParamsValues<PFNEGLGETNEXTLAYERPROCADDRESSPROC>(state);
  PFNEGLGETNEXTLAYERPROCADDRESSPROC get_next_layer_proc_address = AsFuncPtr(callee);

  auto&& [ret] = GuestReturnReference<PFNEGLGETNEXTLAYERPROCADDRESSPROC>(state);
  ret = get_next_layer_proc_address(layer_id, proc_name);
  if (!ret) {
    return;
  }

  if (!WrapEglHostFunction(proc_name, ret)) {
    // Host proc exists but we failed to wrap it.
    // Host proc exists but we failed to wrap it.  That's not fatal error because application
    // may have a fallback code if certain GLES4-5-6 function is not available in our translator
    // but is provided by drivers... but we want to know about it from logs anyway.
    ALOGE("eglGetProcAddress(\"%s\") failed", static_cast<const char*>(proc_name));
    TRACE("eglGetProcAddress(\"%s\") failed", static_cast<const char*>(proc_name));
    ret = 0;
    return;
  }
}

void RunGuestAndroidGLESLayer_Initialize(GuestAddr pc, GuestArgumentBuffer* buf) {
  auto [layer_id, get_next_layer_proc_address] =
      HostArgumentsValues<AndroidGLESLayer_InitializePtr>(buf);
  WrapHostFunctionImpl(reinterpret_cast<void*>(get_next_layer_proc_address),
                       DoCustomTrampolineWithThunk_eglNextLayerProcAddressProc,
                       "RunGuestAndroidGLESLayer_Initialize");
  RunGuestCall(pc, buf);
}


void RunGuestAndroidGLESLayer_GetProcAddress(GuestAddr pc, GuestArgumentBuffer* buf) {
  auto [proc_name, get_next_layer_proc_address] =
      HostArgumentsValues<AndroidGLESLayer_GetProcAddressPtr>(buf);
  auto&& [host_result] = HostResultReference<AndroidGLESLayer_GetProcAddressPtr>(buf);
  if (get_next_layer_proc_address != nullptr &&
      !WrapEglHostFunction(proc_name, ToHostCode(GuestType(get_next_layer_proc_address)))) {
    host_result = const_cast<void*>(ToHostCode(GuestType(get_next_layer_proc_address)));
    return;
  }
  RunGuestCall(pc, buf);
  auto [guest_result] = GuestResultValue<AndroidGLESLayer_GetProcAddressPtr>(buf);
  host_result = const_cast<void*>(WrapEglGuestFunction(proc_name, ToGuestAddr(guest_result)));
}

extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {
  builder->Build("libEGL.so",
                 sizeof(kKnownTrampolines) / sizeof(kKnownTrampolines[0]),
                 kKnownTrampolines,
                 sizeof(kKnownVariables) / sizeof(kKnownVariables[0]),
                 kKnownVariables);
  RegisterKnownGuestFunctionWrapper("AndroidGLESLayer_Initialize", [](GuestAddr pc) {
    return WrapGuestFunctionImpl(pc,
                                 kGuestFunctionWrapperSignature<AndroidGLESLayer_InitializePtr>,
                                 RunGuestAndroidGLESLayer_Initialize,
                                 "AndroidGLESLayer_Initialize");
  });
  RegisterKnownGuestFunctionWrapper("AndroidGLESLayer_GetProcAddress", [](GuestAddr pc) {
    return WrapGuestFunctionImpl(pc,
                                 kGuestFunctionWrapperSignature<AndroidGLESLayer_GetProcAddressPtr>,
                                 RunGuestAndroidGLESLayer_GetProcAddress,
                                 "AndroidGLESLayer_GetProcAddress");
  });
}

}  // namespace

}  // namespace berberis
