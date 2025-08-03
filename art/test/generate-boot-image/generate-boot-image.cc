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

#include <sys/stat.h>
#include <sysexits.h>

#include <algorithm>
#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>

#include "android-base/parsebool.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "arch/instruction_set.h"
#include "base/file_utils.h"
#include "base/globals.h"
#include "base/macros.h"
#include "base/os.h"
#include "base/testing.h"

namespace art {
namespace gbi {

namespace {

using ::android::base::ConsumePrefix;
using ::android::base::ConsumeSuffix;
using ::android::base::Join;
using ::android::base::ParseBool;
using ::android::base::ParseBoolResult;
using ::android::base::StringPrintf;
using ::art::testing::GetLibCoreDexFileNames;
using ::art::testing::GetLibCoreDexLocations;

constexpr const char* kUsage = R"(
A commandline tool to generate a primary boot image for testing.

Usage: generate-boot-image --output-dir=OUTPUT_DIR [OPTIONS]...

Supported options:
  --help: Print this text.
  --output-dir=OUTPUT_DIR: The directory to output the boot image. Required.
  --compiler-filter=COMPILER_FILTER: The compiler filter option to pass to dex2oat. Default: verify
  --use-profile=true|false: If true, use a profile. Default: true
  --dex2oat-bin=DEX2OAT_BIN: The path to the dex2oat binary. Required when running on host. Default
      on target: /apex/com.android.art/bin/dex2oat{32,64,32d,64d}
  --android-root=ANDROID_ROOT: The root directory to search for bootclasspath jars. The file
      structure under the root must be in the form of:
      /apex
        /com.android.art
          /javalib
            /core-oj.jar
            ...
        /com.android.i18n
          /javalib
            ...
        /com.android.conscrypt
          /javalib
            ...
      Required when running on host. Default on target: /
  --profile-file=PROFILE_FILE: The path to the profile file. Required when running on host and
      --use-profile is true. Default on target: /apex/com.android.art/etc/boot-image.prof
  --instruction-set=ISA: The instruction set option to pass to dex2oat. Required when running on
      host. The default on target is based on the ISA of this binary.
  --core-only=true|false: If true, only compile ART jars. Otherwise, also compile core-icu4j and
      conscrypt. Default: false
)";

struct Options {
  std::string output_dir = "";
  // Set the compiler filter to `verify` by default to make test preparation
  // faster.
  std::string compiler_filter = "verify";
  bool use_profile = true;
  std::string dex2oat_bin = "";
  std::string android_root = "";
  std::string profile_file = "";
  std::string instruction_set = "";
  bool core_only = false;
};

[[noreturn]] void Usage(const std::string& message) {
  LOG(ERROR) << message << '\n';
  std::cerr << message << "\n" << kUsage << "\n";
  exit(EX_USAGE);
}

std::string GetCompilerExecutable() {
  std::string compiler_executable = GetArtBinDir() + "/dex2oat";
  if (kIsDebugBuild) {
    compiler_executable += 'd';
  }
  compiler_executable += Is64BitInstructionSet(kRuntimeISA) ? "64" : "32";
  return compiler_executable;
}

// Joins a list of commandline args into a single string, where each part is quoted with double
// quotes. Note that this is a naive implementation that does NOT escape existing double quotes,
// which is fine since we don't have existing double quotes in the args in this particular use case
// and this code is never used in production.
std::string BuildCommand(const std::vector<std::string>& args) {
  std::string command = "";
  for (const std::string& arg : args) {
    if (!command.empty()) {
      command += " ";
    }
    command += '"' + arg + '"';
  }
  return command;
}

int GenerateBootImage(const Options& options) {
  std::vector<std::string> args;
  args.push_back(options.dex2oat_bin);

  std::vector<std::string> dex_files =
      GetLibCoreDexFileNames(options.android_root, options.core_only);
  std::vector<std::string> dex_locations = GetLibCoreDexLocations(options.core_only);
  args.push_back("--runtime-arg");
  args.push_back("-Xbootclasspath:" + Join(dex_files, ":"));
  args.push_back("--runtime-arg");
  args.push_back("-Xbootclasspath-locations:" + Join(dex_locations, ":"));
  for (const std::string& file : dex_files) {
    args.push_back("--dex-file=" + file);
  }
  for (const std::string& location : dex_locations) {
    args.push_back("--dex-location=" + location);
  }

  args.push_back("--instruction-set=" + options.instruction_set);
  args.push_back(StringPrintf("--base=0x%08x", ART_BASE_ADDRESS));
  args.push_back("--compiler-filter=" + options.compiler_filter);
  if (options.use_profile) {
    args.push_back("--profile-file=" + options.profile_file);
  }
  args.push_back("--avoid-storing-invocation");
  args.push_back("--generate-debug-info");
  args.push_back("--generate-build-id");
  args.push_back("--image-format=lz4hc");
  args.push_back("--strip");
  args.push_back("--android-root=out/empty");

  std::string path = ART_FORMAT("{}/{}", options.output_dir, options.instruction_set);
  if (!OS::DirectoryExists(path.c_str())) {
    CHECK_EQ(mkdir(path.c_str(), S_IRWXU), 0);
  }
  args.push_back(StringPrintf("--image=%s/boot.art", path.c_str()));
  args.push_back(StringPrintf("--oat-file=%s/boot.oat", path.c_str()));

  int exit_code = system(BuildCommand(args).c_str());
  if (exit_code != 0) {
    LOG(ERROR) << "dex2oat invocation failed. Exit code: " << exit_code;
  }
  return exit_code;
}

int Main(int argc, char** argv) {
  android::base::InitLogging(argv, android::base::LogdLogger(android::base::SYSTEM));

  Options options;
  for (int i = 1; i < argc; i++) {
    std::string_view arg{argv[i]};
    if (arg == "--help") {
      std::cerr << kUsage << "\n";
      exit(0);
    } else if (ConsumePrefix(&arg, "--output-dir=")) {
      options.output_dir = arg;
    } else if (ConsumePrefix(&arg, "--compiler-filter=")) {
      options.compiler_filter = arg;
    } else if (ConsumePrefix(&arg, "--use-profile=")) {
      ParseBoolResult result = ParseBool(arg);
      if (result == ParseBoolResult::kError) {
        Usage(ART_FORMAT("Unrecognized --use-profile value: '{}'", arg));
      }
      options.use_profile = result == ParseBoolResult::kTrue;
    } else if (ConsumePrefix(&arg, "--dex2oat-bin=")) {
      options.dex2oat_bin = arg;
    } else if (ConsumePrefix(&arg, "--android-root=")) {
      ConsumeSuffix(&arg, "/");
      options.android_root = arg;
    } else if (ConsumePrefix(&arg, "--profile-file=")) {
      options.profile_file = arg;
    } else if (ConsumePrefix(&arg, "--instruction-set=")) {
      options.instruction_set = arg;
    } else if (ConsumePrefix(&arg, "--core-only=")) {
      ParseBoolResult result = ParseBool(arg);
      if (result == ParseBoolResult::kError) {
        Usage(ART_FORMAT("Unrecognized --core-only value: '{}'", arg));
      }
      options.core_only = result == ParseBoolResult::kTrue;
    } else {
      Usage(ART_FORMAT("Unrecognized argument: '{}'", argv[i]));
    }
  }

  if (options.output_dir.empty()) {
    Usage("--output-dir must be specified");
  }

  if (options.dex2oat_bin.empty()) {
    if (kIsTargetBuild) {
      options.dex2oat_bin = GetCompilerExecutable();
    } else {
      Usage("--dex2oat-bin must be specified when running on host");
    }
  }

  if (options.android_root.empty()) {
    if (!kIsTargetBuild) {
      Usage("--android-root must be specified when running on host");
    }
  }

  if (options.use_profile && options.profile_file.empty()) {
    if (kIsTargetBuild) {
      options.profile_file = ART_FORMAT("{}/etc/boot-image.prof", GetArtRoot());
    } else {
      Usage("--profile-file must be specified when running on host and --use-profile is true");
    }
  }

  if (options.instruction_set.empty()) {
    if (kIsTargetBuild) {
      options.instruction_set = GetInstructionSetString(kRuntimeISA);
    } else {
      Usage("--instruction-set must be specified when running on host");
    }
  }

  return GenerateBootImage(options);
}

}  // namespace

}  // namespace gbi
}  // namespace art

int main(int argc, char** argv) { return art::gbi::Main(argc, argv); }
