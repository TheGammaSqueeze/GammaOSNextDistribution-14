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

#include <string>

#include "berberis/base/file.h"
#include "berberis/guest_loader/guest_loader.h"
#include "berberis/runtime/berberis.h"

namespace berberis {

bool Run(const char* vdso_path,
         const char* loader_path,
         int argc,
         const char* argv[],
         char* envp[],
         std::string* error_msg) {
  InitBerberis();

  std::string executable_realpath;
  if (!Realpath(argv[0], &executable_realpath)) {
    *error_msg = std::string("Unable to get realpath of ") + argv[0];
    return false;
  }

  GuestLoader::StartExecutable(
      executable_realpath.c_str(), vdso_path, loader_path, argc, argv, envp, error_msg);

  return false;
}

}  // namespace berberis
