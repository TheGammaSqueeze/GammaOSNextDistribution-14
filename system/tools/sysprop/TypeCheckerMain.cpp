/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define LOG_TAG "sysprop_type_checker_main"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

#include <getopt.h>

#include "Common.h"
#include "TypeChecker.h"

using android::base::Result;
using android::properties::ParsePropertyInfoFile;
using android::properties::PropertyInfoEntry;

namespace {

struct Arguments {
  std::vector<std::string> api_paths;
  std::vector<std::string> context_paths;
};

[[noreturn]] void PrintUsage(const char* exe_name) {
  std::printf("Usage: %s [--api api_path]... [--context context_path]...\n",
              exe_name);
  std::exit(EXIT_FAILURE);
}

Result<Arguments> ParseArgs(int argc, char* argv[]) {
  Arguments ret;
  for (;;) {
    static struct option long_options[] = {
        {"api", required_argument, 0, 'a'},
        {"context", required_argument, 0, 'c'},
    };

    int opt = getopt_long_only(argc, argv, "", long_options, nullptr);
    if (opt == -1) break;

    switch (opt) {
      case 'a':
        ret.api_paths.emplace_back(optarg);
        break;
      case 'c':
        ret.context_paths.emplace_back(optarg);
        break;
      default:
        PrintUsage(argv[0]);
    }
  }

  if (optind < argc) {
    return Errorf("Unknown arguments");
  }

  if (ret.api_paths.empty() || ret.context_paths.empty()) {
    return Errorf("both api files and context files must be specified");
  }

  return ret;
}
}  // namespace

int main(int argc, char* argv[]) {
  Arguments args;
  if (auto res = ParseArgs(argc, argv); res.ok()) {
    args = std::move(*res);
  } else {
    LOG(ERROR) << argv[0] << ": " << res.error();
    PrintUsage(argv[0]);
  }

  sysprop::SyspropLibraryApis api;

  // read all api files and merge them into one SyspropLibraryApis
  for (auto& api_path : args.api_paths) {
    if (auto res = ParseApiFile(api_path); res.ok()) {
      api.MergeFrom(*res);
    } else {
      LOG(FATAL) << "parsing sysprop_library API file " << api_path
                 << " failed: " << res.error();
    }
  }

  std::vector<PropertyInfoEntry> entries;

  // read all context files and parse entries
  for (auto& context_path : args.context_paths) {
    std::string contents;
    if (!android::base::ReadFileToString(context_path, &contents)) {
      LOG(FATAL) << "Could not read properties from '" << context_path << "'";
    }

    std::vector<std::string> errors;
    ParsePropertyInfoFile(contents, true, &entries, &errors);
    if (!errors.empty()) {
      for (const auto& error : errors) {
        LOG(ERROR) << "Could not read line from '" << context_path
                   << "': " << error;
      }
      LOG(FATAL) << "Could not parse properties from '" << context_path << "'";
    }
  }

  if (auto res = CheckPropertyTypes(api, entries); !res.ok()) {
    LOG(ERROR) << "sysprop_library type check failed:\n\n" << res.error();
    return EXIT_FAILURE;
  }
}
