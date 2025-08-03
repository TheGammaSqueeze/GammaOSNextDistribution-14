//
// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <action.h>
#include <action_manager.h>
#include <action_parser.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/strings.h>
#include <apex_file.h>
#include <check_builtins.h>
#include <getopt.h>
#include <parser.h>
#include <pwd.h>
#include <service_list.h>
#include <service_parser.h>
#include <stdio.h>
#include <stdlib.h>

#include <iostream>
#include <string_view>

using ::apex::proto::ApexManifest;

// Fake getpwnam for host execution, used by the init::ServiceParser.
passwd* getpwnam(const char*) {
  static char fake_buf[] = "fake";
  static passwd fake_passwd = {
      .pw_name = fake_buf,
      .pw_dir = fake_buf,
      .pw_shell = fake_buf,
      .pw_uid = 123,
      .pw_gid = 123,
  };
  return &fake_passwd;
}

namespace android {
namespace apex {
namespace {

static const std::vector<std::string> partitions = {"system", "system_ext",
                                                    "product", "vendor", "odm"};

void PrintUsage() {
  printf(R"(usage: host_apex_verifier [options]

Tests APEX file(s) for correctness.

Options:
  --deapexer=PATH             Use the deapexer binary at this path when extracting APEXes.
  --debugfs=PATH              Use the debugfs binary at this path when extracting APEXes.
  --fsckerofs=PATH            Use the fsck.erofs binary at this path when extracting APEXes.
  --sdk_version=INT           The active system SDK version used when filtering versioned
                              init.rc files.
for checking all APEXes:
  --out_system=DIR            Path to the factory APEX directory for the system partition.
  --out_system_ext=DIR        Path to the factory APEX directory for the system_ext partition.
  --out_product=DIR           Path to the factory APEX directory for the product partition.
  --out_vendor=DIR            Path to the factory APEX directory for the vendor partition.
  --out_odm=DIR               Path to the factory APEX directory for the odm partition.

for checking a single APEX:
  --apex=PATH                 Path to the target APEX.
)");
}

// Validate any init rc files inside the APEX.
void CheckInitRc(const std::string& apex_dir, const ApexManifest& manifest,
                 int sdk_version) {
  init::Parser parser;
  init::ServiceList service_list = init::ServiceList();
  parser.AddSectionParser("service", std::make_unique<init::ServiceParser>(
                                         &service_list, nullptr, std::nullopt));
  const init::BuiltinFunctionMap& function_map = init::GetBuiltinFunctionMap();
  init::Action::set_function_map(&function_map);
  init::ActionManager action_manager = init::ActionManager();
  parser.AddSectionParser(
      "on", std::make_unique<init::ActionParser>(&action_manager, nullptr));
  std::string init_dir_path = apex_dir + "/etc";
  std::vector<std::string> init_configs;
  std::unique_ptr<DIR, decltype(&closedir)> init_dir(
      opendir(init_dir_path.c_str()), closedir);
  if (init_dir) {
    dirent* entry;
    while ((entry = readdir(init_dir.get()))) {
      if (base::EndsWith(entry->d_name, "rc")) {
        init_configs.push_back(init_dir_path + "/" + entry->d_name);
      }
    }
  }
  // TODO(b/225380016): Extend this tool to check all init.rc files
  // in the APEX, possibly including different requirements depending
  // on the SDK version.
  for (const auto& c :
       init::FilterVersionedConfigs(init_configs, sdk_version)) {
    auto result = parser.ParseConfigFile(c);
    if (!result.ok()) {
      LOG(FATAL) << result.error();
    }
  }

  for (const auto& service : service_list) {
    // Ensure the service path points inside this APEX.
    auto service_path = service->args()[0];
    if (!base::StartsWith(service_path, "/apex/" + manifest.name())) {
      LOG(FATAL) << "Service " << service->name()
                 << " has path outside of the APEX: " << service_path;
    }
    LOG(INFO) << service->name() << ": " << service_path;
  }

  // The parser will fail if there are any unsupported actions.
  if (parser.parse_error_count() > 0) {
    exit(EXIT_FAILURE);
  }
}

// Extract and validate a single APEX.
void ScanApex(const std::string& deapexer, int sdk_version,
              const std::string& apex_path) {
  LOG(INFO) << "Checking APEX " << apex_path;

  auto apex = OR_FATAL(ApexFile::Open(apex_path));
  ApexManifest manifest = apex.GetManifest();

  auto extracted_apex = TemporaryDir();
  std::string extracted_apex_dir = extracted_apex.path;
  std::string deapexer_command =
      deapexer + " extract " + apex_path + " " + extracted_apex_dir;
  auto code = system(deapexer_command.c_str());
  if (code != 0) {
    LOG(FATAL) << "Error running deapexer command \"" << deapexer_command
               << "\": " << code;
  }

  CheckInitRc(extracted_apex_dir, manifest, sdk_version);
}

// Scan the factory APEX files in the partition apex dir.
// Scans APEX files directly, rather than flattened ${PRODUCT_OUT}/apex/
// directories. This allows us to check:
//   - Prebuilt APEXes which do not flatten to that path.
//   - Multi-installed APEXes, where only the default
//     APEX may flatten to that path.
//   - Extracted target_files archives which may not contain
//     flattened <PARTITON>/apex/ directories.
void ScanPartitionApexes(const std::string& deapexer, int sdk_version,
                         const std::string& partition_dir) {
  LOG(INFO) << "Scanning partition factory APEX dir " << partition_dir;

  std::unique_ptr<DIR, decltype(&closedir)> apex_dir(
      opendir(partition_dir.c_str()), closedir);
  if (!apex_dir) {
    LOG(WARNING) << "Unable to open dir " << partition_dir;
    return;
  }

  dirent* entry;
  while ((entry = readdir(apex_dir.get()))) {
    if (base::EndsWith(entry->d_name, ".apex") ||
        base::EndsWith(entry->d_name, ".capex")) {
      ScanApex(deapexer, sdk_version, partition_dir + "/" + entry->d_name);
    }
  }
}

}  // namespace

int main(int argc, char** argv) {
  android::base::SetMinimumLogSeverity(android::base::ERROR);
  android::base::InitLogging(argv, &android::base::StdioLogger);

  std::string deapexer, debugfs, fsckerofs;
  // Use ANDROID_HOST_OUT for convenience
  const char* host_out = getenv("ANDROID_HOST_OUT");
  if (host_out) {
    deapexer = std::string(host_out) + "/bin/deapexer";
    debugfs = std::string(host_out) + "/bin/debugfs_static";
    fsckerofs = std::string(host_out) + "/bin/fsck.erofs";
  }
  int sdk_version = INT_MAX;
  std::map<std::string, std::string> partition_map;
  std::string apex;

  while (true) {
    static const struct option long_options[] = {
        {"help", no_argument, nullptr, 'h'},
        {"deapexer", required_argument, nullptr, 0},
        {"debugfs", required_argument, nullptr, 0},
        {"fsckerofs", required_argument, nullptr, 0},
        {"sdk_version", required_argument, nullptr, 0},
        {"out_system", required_argument, nullptr, 0},
        {"out_system_ext", required_argument, nullptr, 0},
        {"out_product", required_argument, nullptr, 0},
        {"out_vendor", required_argument, nullptr, 0},
        {"out_odm", required_argument, nullptr, 0},
        {"apex", required_argument, nullptr, 0},
        {nullptr, 0, nullptr, 0},
    };

    int option_index;
    int arg = getopt_long(argc, argv, "h", long_options, &option_index);

    if (arg == -1) {
      break;
    }

    switch (arg) {
      case 0: {
        std::string_view name(long_options[option_index].name);
        if (name == "deapexer") {
          deapexer = optarg;
        }
        if (name == "debugfs") {
          debugfs = optarg;
        }
        if (name == "fsckerofs") {
          fsckerofs = optarg;
        }
        if (name == "sdk_version") {
          if (!base::ParseInt(optarg, &sdk_version)) {
            PrintUsage();
            return EXIT_FAILURE;
          }
        }
        if (name == "apex") {
          apex = optarg;
        }
        for (const auto& p : partitions) {
          if (name == "out_" + p) {
            partition_map[p] = optarg;
          }
        }
        break;
      }
      case 'h':
        PrintUsage();
        return EXIT_SUCCESS;
      default:
        LOG(ERROR) << "getopt returned invalid result: " << arg;
        return EXIT_FAILURE;
    }
  }

  argc -= optind;
  argv += optind;

  if (argc != 0) {
    PrintUsage();
    return EXIT_FAILURE;
  }
  if (deapexer.empty() || debugfs.empty() || fsckerofs.empty()) {
    PrintUsage();
    return EXIT_FAILURE;
  }
  deapexer += " --debugfs_path " + debugfs;
  deapexer += " --fsckerofs_path " + fsckerofs;

  if (!!apex.empty() + !!partition_map.empty() != 1) {
    PrintUsage();
    return EXIT_FAILURE;
  }

  init::InitializeHostPropertyInfoArea({});

  if (!partition_map.empty()) {
    for (const auto& p : partition_map) {
      ScanPartitionApexes(deapexer, sdk_version, p.second);
    }
  } else {
    ScanApex(deapexer, sdk_version, apex);
  }
  return EXIT_SUCCESS;
}

}  // namespace apex
}  // namespace android

int main(int argc, char** argv) { return android::apex::main(argc, argv); }
