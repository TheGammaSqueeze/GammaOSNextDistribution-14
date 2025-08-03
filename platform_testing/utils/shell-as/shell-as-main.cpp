/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <iostream>
#include <memory>
#include <string>

#include "./command-line.h"
#include "./context.h"
#include "./execute.h"

int main(const int argc, char* const argv[]) {
  bool verbose = false;
  auto context = std::make_unique<shell_as::SecurityContext>();
  char* const* execute_arguments = nullptr;
  if (!shell_as::ParseOptions(argc, argv, &verbose, context.get(),
                              &execute_arguments)) {
    return 1;
  }

  if (verbose) {
    std::cerr << "Dropping privileges to:" << std::endl;
    std::cerr << "\tuser ID = "
              << (context->user_id.has_value()
                      ? std::to_string(context->user_id.value())
                      : "<no value>")
              << std::endl;

    std::cerr << "\tgroup ID = "
              << (context->group_id.has_value()
                      ? std::to_string(context->group_id.value())
                      : "<no value>")
              << std::endl;

    std::cerr << "\tsupplementary group IDs = ";
    if (!context->supplementary_group_ids.has_value()) {
      std::cerr << "<no value>";
    } else {
      for (auto& id : context->supplementary_group_ids.value()) {
        std::cerr << id << " ";
      }
    }
    std::cerr << std::endl;

    std::cerr << "\tSELinux = "
              << (context->selinux_context.has_value()
                      ? context->selinux_context.value()
                      : "<no value>")
              << std::endl;

    std::cerr << "\tseccomp = ";
    if (!context->seccomp_filter.has_value()) {
      std::cerr << "<no value>";
    } else {
      switch (context->seccomp_filter.value()) {
        case shell_as::kAppFilter:
          std::cerr << "app";
          break;
        case shell_as::kAppZygoteFilter:
          std::cerr << "app-zygote";
          break;
        case shell_as::kSystemFilter:
          std::cerr << "system";
          break;
      }
    }
    std::cerr << std::endl;

    std::cerr << "\tcapabilities = ";
    if (!context->capabilities.has_value()) {
      std::cerr << "<no value>";
    } else {
      std::cerr << "'" << cap_to_text(context->capabilities.value(), nullptr)
                << "'";
    }
    std::cerr << std::endl;
  }

  return !shell_as::ExecuteInContext(execute_arguments, context.get());
}
