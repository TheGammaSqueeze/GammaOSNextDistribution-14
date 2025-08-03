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

#include <cstdio>
#include <string>

#include "berberis/program_runner/program_runner.h"

// Basic program runner meant to be used by binfmt_misc utility.

int main(int argc, const char* argv[], char* envp[]) {
  if (argc < 3) {
    printf("Usage: %s /full/path/to/program program [args...]", argv[0]);
    return 0;
  }

  std::string error_msg;
  if (!berberis::Run(
          /* vdso_path */ nullptr,
          /* loader_path */ nullptr,
          argc - 2,
          &argv[2],
          envp,
          &error_msg)) {
    fprintf(stderr, "Error running %s: %s", argv[1], error_msg.c_str());
    return -1;
  }
}