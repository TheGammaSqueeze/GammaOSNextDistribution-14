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

// An implementation of the native-bridge interface for testing.

#include "NativeBridge7CriticalNative_lib.h"
#include "nativebridge/native_bridge.h"

// NativeBridgeCallbacks implementations
extern "C" bool native_bridge7_initialize(
    const android::NativeBridgeRuntimeCallbacks* /* art_cbs */,
    const char* /* app_code_cache_dir */,
    const char* /* isa */) {
  return true;
}

extern "C" void* native_bridge7_loadLibrary(const char* /* libpath */, int /* flag */) {
  return nullptr;
}

extern "C" void* native_bridge7_getTrampoline(void* /* handle */,
                                              const char* /* name */,
                                              const char* /* shorty */,
                                              uint32_t /* len */) {
  android::SetLegacyGetTrampolineCalled();
  return nullptr;
}

extern "C" void* native_bridge7_getTrampoline2(void* /* handle */,
                                               const char* /* name */,
                                               const char* /* shorty */,
                                               uint32_t /* len */,
                                               android::JNICallType jni_call_type) {
  android::SetGetTrampoline2Called(jni_call_type);
  return nullptr;
}

extern "C" bool native_bridge7_isSupported(const char* /* libpath */) { return false; }

extern "C" const struct android::NativeBridgeRuntimeValues* native_bridge7_getAppEnv(
    const char* /* abi */) {
  return nullptr;
}

extern "C" bool native_bridge7_isCompatibleWith(uint32_t version) {
  // For testing, allow 1-7, but disallow 8+.
  return version <= 7;
}

extern "C" android::NativeBridgeSignalHandlerFn native_bridge7_getSignalHandler(int /* signal */) {
  return nullptr;
}

extern "C" int native_bridge7_unloadLibrary(void* /* handle */) { return 0; }

extern "C" const char* native_bridge7_getError() { return nullptr; }

extern "C" bool native_bridge7_isPathSupported(const char* /* path */) { return true; }

extern "C" bool native_bridge7_initAnonymousNamespace(const char* /* public_ns_sonames */,
                                                      const char* /* anon_ns_library_path */) {
  return true;
}

extern "C" android::native_bridge_namespace_t* native_bridge7_createNamespace(
    const char* /* name */,
    const char* /* ld_library_path */,
    const char* /* default_library_path */,
    uint64_t /* type */,
    const char* /* permitted_when_isolated_path */,
    android::native_bridge_namespace_t* /* parent_ns */) {
  return nullptr;
}

extern "C" bool native_bridge7_linkNamespaces(android::native_bridge_namespace_t* /* from */,
                                              android::native_bridge_namespace_t* /* to */,
                                              const char* /* shared_libs_soname */) {
  return true;
}

extern "C" void* native_bridge7_loadLibraryExt(const char* /* libpath */,
                                               int /* flag */,
                                               android::native_bridge_namespace_t* /* ns */) {
  return nullptr;
}

extern "C" android::native_bridge_namespace_t* native_bridge7_getVendorNamespace() {
  return nullptr;
}

extern "C" android::native_bridge_namespace_t* native_bridge7_getExportedNamespace(
    const char* /* name */) {
  return nullptr;
}

extern "C" void native_bridge7_preZygoteFork() {}

android::NativeBridgeCallbacks NativeBridgeItf{
    // v1
    .version = 7,
    .initialize = &native_bridge7_initialize,
    .loadLibrary = &native_bridge7_loadLibrary,
    .getTrampoline = &native_bridge7_getTrampoline,
    .isSupported = &native_bridge7_isSupported,
    .getAppEnv = &native_bridge7_getAppEnv,
    // v2
    .isCompatibleWith = &native_bridge7_isCompatibleWith,
    .getSignalHandler = &native_bridge7_getSignalHandler,
    // v3
    .unloadLibrary = &native_bridge7_unloadLibrary,
    .getError = &native_bridge7_getError,
    .isPathSupported = &native_bridge7_isPathSupported,
    .initAnonymousNamespace = &native_bridge7_initAnonymousNamespace,
    .createNamespace = &native_bridge7_createNamespace,
    .linkNamespaces = &native_bridge7_linkNamespaces,
    .loadLibraryExt = &native_bridge7_loadLibraryExt,
    // v4
    &native_bridge7_getVendorNamespace,
    // v5
    &native_bridge7_getExportedNamespace,
    // v6
    &native_bridge7_preZygoteFork,
    // v7
    &native_bridge7_getTrampoline2};
