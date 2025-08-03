/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "berberis/kernel_api/exec_emulation.h"

#include <unistd.h>

#include <cstring>

#include "berberis/base/mmap.h"
#include "berberis/base/strings.h"

namespace berberis {

namespace {

bool IsPlatformVar(const char* s) {
  return StartsWith(s, "LD_CONFIG_FILE=") || StartsWith(s, "LD_LIBRARY_PATH=") ||
         StartsWith(s, "LD_DEBUG=") || StartsWith(s, "LD_PRELOAD=");
}

char* const* MangleGuestEnvp(ScopedMmap* dst, char* const* envp) {
  if (envp == nullptr) {
    return nullptr;
  }

  int env_count = 0;
  int text_size = 0;
  int mangle_count = 0;

  for (;; ++env_count) {
    char* env = envp[env_count];
    if (env == nullptr) {
      break;
    }

    if (IsPlatformVar(env)) {
      ++mangle_count;
    }

    text_size += strlen(env) + 1;  // count terminating '\0'
  }

  if (mangle_count == 0) {
    return envp;
  }

  auto [guest_prefix, guest_prefix_size] = GetGuestPlatformVarPrefixWithSize();

  size_t array_size = sizeof(char*) * (env_count + 1);    // text pointers + terminating nullptr
  dst->Init(array_size + text_size +                      // array + orig text
            guest_prefix_size * mangle_count);            // added prefixes

  char** new_array = static_cast<char**>(dst->data());
  char* new_text = static_cast<char*>(dst->data()) + array_size;

  for (int i = 0; i < env_count; ++i) {
    char* env = envp[i];
    new_array[i] = new_text;

    if (IsPlatformVar(env)) {
      strcpy(new_text, guest_prefix);
      new_text += guest_prefix_size;
    }

    strcpy(new_text, env);
    new_text += strlen(env) + 1;  // count terminating '\0'
  }

  new_array[env_count] = nullptr;  // add terminating nullptr

  return new_array;
}

}  // namespace

char** DemangleGuestEnvp(char** dst, char** envp) {
  auto [guest_prefix, guest_prefix_size] = GetGuestPlatformVarPrefixWithSize();

  for (; *envp; ++envp) {
    char* env = *envp;
    if (IsPlatformVar(env)) {
      continue;
    }
    if (StartsWith(env, guest_prefix) && IsPlatformVar(env + guest_prefix_size)) {
      env += guest_prefix_size;
    }
    *dst++ = env;
  }

  *dst++ = nullptr;
  return dst;
}

int ExecveForGuest(const char* filename, char* const argv[], char* const envp[]) {
  ScopedMmap new_envp;
  return execve(filename, argv, MangleGuestEnvp(&new_envp, envp));
}

}  // namespace berberis
