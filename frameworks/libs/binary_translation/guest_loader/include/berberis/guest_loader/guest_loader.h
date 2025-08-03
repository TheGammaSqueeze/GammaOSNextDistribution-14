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

#ifndef BERBERIS_GUEST_LOADER_GUEST_LOADER_H_
#define BERBERIS_GUEST_LOADER_GUEST_LOADER_H_

#include <dlfcn.h>
#include <link.h>

#include <cstdint>  // uintptr_t
#include <string>

#include "berberis/guest_state/guest_addr.h"
#include "berberis/tiny_loader/loaded_elf_file.h"

#if defined(__BIONIC__)
#include <android/dlext.h>
#else
// We do not have <android/dlfcn.h> for non-bionic environments
// such as glibc this part is copy of necessary structures from
// <android/dlext.h>
struct android_namespace_t;

struct android_dlextinfo {
  uint64_t flags;
  void* reserved_addr;
  size_t reserved_size;
  int relro_fd;
  int library_fd;
  off64_t library_fd_offset;
  struct android_namespace_t* library_namespace;
};
#endif

namespace berberis {

struct LinkerCallbacks {
  using android_create_namespace_fn_t =
      android_namespace_t* (*)(const char* name,
                               const char* ld_library_path,
                               const char* default_library_path,
                               uint64_t type,
                               const char* permitted_when_isolated_path,
                               android_namespace_t* parent_namespace,
                               const void* caller_addr);
  using android_dlopen_ext_fn_t = void* (*)(const char* filename,
                                            int flags,
                                            const android_dlextinfo* extinfo,
                                            const void* caller_addr);
  using android_get_exported_namespace_fn_t = android_namespace_t* (*)(const char* name);
  using android_init_anonymous_namespace_fn_t = bool (*)(const char* shared_libs_sonames,
                                                         const char* library_search_path);
  using android_link_namespaces_fn_t = bool (*)(android_namespace_t* namespace_from,
                                                android_namespace_t* namespace_to,
                                                const char* shared_libs_sonames);
  using android_set_application_target_sdk_version_fn_t = void (*)(int target);
  using dl_unwind_find_exidx_fn_t = uintptr_t (*)(uintptr_t pc, int* pcount);
  using dladdr_fn_t = int (*)(const void* addr, Dl_info* info);
  using dlerror_fn_t = char* (*)();
  using dlsym_fn_t = void* (*)(void* handle, const char* symbol, const void* caller_addr);

  android_create_namespace_fn_t create_namespace_fn_ = nullptr;
  android_dlopen_ext_fn_t dlopen_ext_fn_ = nullptr;
  android_get_exported_namespace_fn_t get_exported_namespace_fn_ = nullptr;
  android_init_anonymous_namespace_fn_t init_anonymous_namespace_fn_ = nullptr;
  android_link_namespaces_fn_t link_namespaces_fn_ = nullptr;
  android_set_application_target_sdk_version_fn_t set_application_target_sdk_version_fn_ = nullptr;
  dl_unwind_find_exidx_fn_t dl_unwind_find_exidx_fn_ = nullptr;
  dladdr_fn_t dladdr_fn_ = nullptr;
  dlerror_fn_t dlerror_fn_ = nullptr;
  dlsym_fn_t dlsym_fn_ = nullptr;
};

// Loads loader vdso and initializes callbacks to loader symbols.
class GuestLoader {
 public:
  ~GuestLoader();

  // Should be called only once.
  static GuestLoader* StartAppProcessInNewThread(std::string* error_msg);

  // Initializes GuestLoader and starts the executable in the current thread.
  //
  // Note that this method returns only in the case of an error. Otherwise it
  // never returns.
  static void StartExecutable(const char* main_executable_path,
                              const char* vdso_path,
                              const char* loader_path,
                              size_t argc,
                              const char* argv[],
                              char* envp[],
                              std::string* error_msg);

  // If GetInstance() called before Initialize() it will return nullptr
  static GuestLoader* GetInstance();

  uintptr_t DlUnwindFindExidx(uintptr_t pc, int* pcount);
  int DlAddr(const void* addr, Dl_info* info);
  void* DlOpen(const char* libpath, int flags);
  void* DlOpenExt(const char* libpath, int flags, const android_dlextinfo* extinfo);
  GuestAddr DlSym(void* handle, const char* name);
  const char* DlError();
  bool InitAnonymousNamespace(const char* public_ns_sonames, const char* anon_ns_library_path);
  android_namespace_t* CreateNamespace(const char* name,
                                       const char* ld_library_path,
                                       const char* default_library_path,
                                       uint64_t type,
                                       const char* permitted_when_isolated_path,
                                       android_namespace_t* parent_ns);
  android_namespace_t* GetExportedNamespace(const char* name);
  bool LinkNamespaces(android_namespace_t* from,
                      android_namespace_t* to,
                      const char* shared_libs_sonames);
  void SetTargetSdkVersion(uint32_t target_sdk_version);

  const struct r_debug* FindRDebug() const;

 private:
  static GuestLoader* CreateInstance(const char* main_executable_path,
                                     const char* vdso_path,
                                     const char* loader_path,
                                     std::string* error_msg);

  GuestLoader();
  GuestLoader(const GuestLoader&) = delete;
  GuestLoader& operator=(const GuestLoader&) = delete;

  void StartGuestMainThread();
  void StartGuestExecutable(size_t argc, const char* argv[], char* envp[]);

  const char* main_executable_path_;
  LoadedElfFile executable_elf_file_;
  LoadedElfFile linker_elf_file_;
  LoadedElfFile vdso_elf_file_;
  const void* caller_addr_;
  LinkerCallbacks linker_callbacks_;
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_LOADER_GUEST_LOADER_H_