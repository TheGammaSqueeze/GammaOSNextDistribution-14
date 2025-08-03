/*
 * Copyright (C) 2017 The Android Open Source Project
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

/*
 * Tests accessibility of platform native libraries
 */

#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <libgen.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/user.h>
#include <unistd.h>

#include <string>

#include "nativehelper/scoped_local_ref.h"
#include "nativehelper/scoped_utf_chars.h"

static constexpr const char* kTestLibName = "libjni_test_dlclose.so";

static bool run_test(std::string* error_msg) {
  void* handle = dlopen(kTestLibName, RTLD_NOW);
  if (handle == nullptr) {
    *error_msg = dlerror();
    return false;
  }

  void* taxicab_number = dlsym(handle, "dlopen_testlib_taxicab_number");
  if (taxicab_number == nullptr) {
    *error_msg = dlerror();
    dlclose(handle);
    return false;
  }

  dlclose(handle);

  static const size_t kPageSize = getpagesize();
  uintptr_t page_start = reinterpret_cast<uintptr_t>(taxicab_number) & ~(kPageSize - 1);
  if (mprotect(reinterpret_cast<void*>(page_start), kPageSize, PROT_NONE) == 0) {
    *error_msg = std::string("The library \"") +
                 kTestLibName +
                 "\" has not been unloaded on dlclose()";
    return false;
  }

  if (errno != ENOMEM) {
    *error_msg = std::string("Unexpected error on mprotect: ") + strerror(errno);
    return false;
  }

  return true;
}

extern "C" JNIEXPORT jstring JNICALL Java_android_jni_cts_BasicLoaderTestHelper_nativeRunTests(
        JNIEnv* env) {
  std::string error_str;

  if (!run_test(&error_str)) {
    return env->NewStringUTF(error_str.c_str());
  }

  return nullptr;
}

