/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "log/log.h"

static void __attribute__((constructor)) ctor() {
  // Load a library that should be available to system libraries through a
  // linked namespace (i.e. is not directly in /system/${LIB}), and that is not
  // in public.libraries.txt. We use a real one to avoid having to set up an
  // APEX test fixture and rerun linkerconfig.
  void* h = dlopen("libandroidicu.so", RTLD_NOW);
  if (h == nullptr) {
    LOG_ALWAYS_FATAL("Failed to load dependency: %s", dlerror());
  }
  dlclose(h);
}
