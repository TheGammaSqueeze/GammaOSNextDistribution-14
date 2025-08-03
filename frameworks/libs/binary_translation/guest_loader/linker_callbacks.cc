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

#include "berberis/guest_loader/guest_loader.h"

#include <cinttypes>
#include <string>

#include "berberis/base/tracing.h"

#include "guest_loader_impl.h"  // FindSymbol

namespace berberis {

namespace {

android_namespace_t* uninitialized_create_namespace(const char* /* name */,
                                                    const char* /* ld_library_path */,
                                                    const char* /* default_library_path */,
                                                    uint64_t /* type */,
                                                    const char* /* permitted_when_isolated_path */,
                                                    android_namespace_t* /* parent_namespace */,
                                                    const void* /* caller_addr */) {
  return nullptr;
}

void* uninitialized_dlopen_ext(const char* /* filename */,
                               int /* flags */,
                               const android_dlextinfo* /* extinfo */,
                               const void* /* caller_addr */) {
  return nullptr;
}

bool uninitialized_init_anonymous_namespace(const char* /* shared_libs_sonames */,
                                            const char* /* library_search_path */) {
  return false;
}

bool uninitialized_link_namespaces(android_namespace_t* /* namespace_from */,
                                   android_namespace_t* /* namespace_to */,
                                   const char* /* shared_libs_sonames */) {
  return false;
}

uintptr_t uninitialized_dl_unwind_find_exidx(uintptr_t /* pc */, int* /* pcount */) {
  return 0;
}

int uninitialized_dladdr(const void* /* addr */, Dl_info* /* info */) {
  return 0;
}

char* uninitialized_dlerror() {
  static char error_msg[] =
      "Linker callbacks are not initialized, likely because "
      "the loaded executable is a static executable";
  return error_msg;
}

void* uninitialized_dlsym(void* /* handle */,
                          const char* /* symbol */,
                          const void* /* caller_addr */) {
  return nullptr;
}

LinkerCallbacks g_uninitialized_callbacks{
    .create_namespace_fn_ = uninitialized_create_namespace,
    .dlopen_ext_fn_ = uninitialized_dlopen_ext,
    .init_anonymous_namespace_fn_ = uninitialized_init_anonymous_namespace,
    .link_namespaces_fn_ = uninitialized_link_namespaces,
    .dl_unwind_find_exidx_fn_ = uninitialized_dl_unwind_find_exidx,
    .dladdr_fn_ = uninitialized_dladdr,
    .dlerror_fn_ = uninitialized_dlerror,
    .dlsym_fn_ = uninitialized_dlsym,
};

}  // namespace

uintptr_t GuestLoader::DlUnwindFindExidx(uintptr_t pc, int* pcount) {
  TRACE("GuestLoader::DlUnwindFindExidx(pc=%p, pcount=%p)", reinterpret_cast<void*>(pc), pcount);
  return linker_callbacks_.dl_unwind_find_exidx_fn_(pc, pcount);
}

int GuestLoader::DlAddr(const void* addr, Dl_info* info) {
  TRACE("GuestLoader::DlAddr(addr=%p, info=%p)", addr, info);
  return linker_callbacks_.dladdr_fn_(addr, info);
}

void* GuestLoader::DlOpen(const char* libpath, int flags) {
  TRACE("GuestLoader::DlOpen(libpath=%s, flags=0x%x)", libpath, flags);
  return DlOpenExt(libpath, flags, nullptr);
}

void* GuestLoader::DlOpenExt(const char* libpath, int flags, const android_dlextinfo* extinfo) {
  TRACE("GuestLoader::DlOpen(libpath=\"%s\", flags=0x%x, extinfo=%p)", libpath, flags, extinfo);
  auto result = linker_callbacks_.dlopen_ext_fn_(libpath, flags, extinfo, caller_addr_);
  TRACE("GuestLoader::DlOpen(...) = %p", result);
  return result;
}

GuestAddr GuestLoader::DlSym(void* handle, const char* name) {
  TRACE("GuestLoader::DlSym(handle=%p, name=\"%s\")", handle, name);
  return ToGuestAddr(linker_callbacks_.dlsym_fn_(handle, name, caller_addr_));
}

const char* GuestLoader::DlError() {
  TRACE("GuestLoader::DlError()");
  return linker_callbacks_.dlerror_fn_();
}

bool GuestLoader::InitAnonymousNamespace(const char* public_ns_sonames,
                                         const char* anon_ns_library_path) {
  TRACE(
      "GuestLoader::InitAnonymousNamespace("
      "public_ns_sonames=\"%s\", "
      "anon_ns_library_path=\"%s\")",
      public_ns_sonames,
      anon_ns_library_path);
#if defined(__BIONIC__)
  SetTargetSdkVersion(android_get_application_target_sdk_version());
#endif
  return linker_callbacks_.init_anonymous_namespace_fn_(public_ns_sonames, anon_ns_library_path);
}

android_namespace_t* GuestLoader::CreateNamespace(const char* name,
                                                  const char* ld_library_path,
                                                  const char* default_library_path,
                                                  uint64_t type,
                                                  const char* permitted_when_isolated_path,
                                                  android_namespace_t* parent_ns) {
  TRACE(
      "GuestLoader::CreateNamespace("
      "name=\"%s\", "
      "ld_library_path=\"%s\", "
      "default_library_path=\"%s\", "
      "type=%" PRIx64
      ", "
      "permitted_when_isolated_path=\"%s\", "
      "parent_ns=%p)",
      name,
      ld_library_path,
      default_library_path,
      type,
      permitted_when_isolated_path,
      parent_ns);

#if defined(__BIONIC__)
  SetTargetSdkVersion(android_get_application_target_sdk_version());
#endif

  auto result = linker_callbacks_.create_namespace_fn_(name,
                                                       ld_library_path,
                                                       default_library_path,
                                                       type,
                                                       permitted_when_isolated_path,
                                                       parent_ns,
                                                       caller_addr_);
  TRACE("GuestLoader::CreateNamespace(...) .. = %p", result);
  return result;
}

android_namespace_t* GuestLoader::GetExportedNamespace(const char* name) {
  auto result = linker_callbacks_.get_exported_namespace_fn_(name);
  TRACE("GuestLoader::GetExportedNamespace(name=\"%s\") = %p", name, result);
  return result;
}

bool GuestLoader::LinkNamespaces(android_namespace_t* from,
                                 android_namespace_t* to,
                                 const char* shared_libs_sonames) {
  TRACE("GuestLoader::LinkNamespaces(from=%p, to=%p, shared_libs_sonames=\"%s\")",
        from,
        to,
        shared_libs_sonames);
  return linker_callbacks_.link_namespaces_fn_(from, to, shared_libs_sonames);
}

void GuestLoader::SetTargetSdkVersion(uint32_t target_sdk_version) {
  TRACE("GuestLoader::SetTargetSdkVersion(%u)", target_sdk_version);
  linker_callbacks_.set_application_target_sdk_version_fn_(target_sdk_version);
}

void InitializeLinkerCallbacksToStubs(LinkerCallbacks* linker_callbacks) {
  *linker_callbacks = g_uninitialized_callbacks;
}

bool InitializeLinkerCallbacks(LinkerCallbacks* linker_callbacks,
                               const LoadedElfFile& linker_elf_file,
                               std::string* error_msg) {
  return FindSymbol(linker_elf_file,
                    "__loader_android_create_namespace",
                    &linker_callbacks->create_namespace_fn_,
                    error_msg) &&
         FindSymbol(linker_elf_file,
                    "__loader_android_dlopen_ext",
                    &linker_callbacks->dlopen_ext_fn_,
                    error_msg) &&
         FindSymbol(linker_elf_file,
                    "__loader_android_get_exported_namespace",
                    &linker_callbacks->get_exported_namespace_fn_,
                    error_msg) &&
         FindSymbol(linker_elf_file,
                    "__loader_android_init_anonymous_namespace",
                    &linker_callbacks->init_anonymous_namespace_fn_,
                    error_msg) &&
         FindSymbol(linker_elf_file,
                    "__loader_android_link_namespaces",
                    &linker_callbacks->link_namespaces_fn_,
                    error_msg) &&
         FindSymbol(linker_elf_file,
                    "__loader_android_set_application_target_sdk_version",
                    &linker_callbacks->set_application_target_sdk_version_fn_,
                    error_msg) &&
         FindSymbol(linker_elf_file, "__loader_dladdr", &linker_callbacks->dladdr_fn_, error_msg) &&
         FindSymbol(
             linker_elf_file, "__loader_dlerror", &linker_callbacks->dlerror_fn_, error_msg) &&
         FindSymbol(linker_elf_file, "__loader_dlsym", &linker_callbacks->dlsym_fn_, error_msg);
}

}  // namespace berberis