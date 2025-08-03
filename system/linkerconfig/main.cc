/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <climits>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>

#include <errno.h>
#include <fcntl.h>
#include <getopt.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <android-base/result.h>
#include <android-base/strings.h>

#include "linkerconfig/apex.h"
#include "linkerconfig/apexconfig.h"
#include "linkerconfig/baseconfig.h"
#include "linkerconfig/configparser.h"
#include "linkerconfig/context.h"
#include "linkerconfig/environment.h"
#include "linkerconfig/legacy.h"
#include "linkerconfig/log.h"
#include "linkerconfig/namespacebuilder.h"
#include "linkerconfig/recovery.h"
#include "linkerconfig/variableloader.h"
#include "linkerconfig/variables.h"

using android::base::ErrnoError;
using android::base::Error;
using android::base::Join;
using android::base::Result;
using android::linkerconfig::contents::Context;
using android::linkerconfig::modules::ApexInfo;
using android::linkerconfig::modules::Configuration;

namespace {
const static struct option program_options[] = {
    {"apex", required_argument, 0, 'a'},
    {"target", required_argument, 0, 't'},
    {"strict", no_argument, 0, 's'},
#ifndef __ANDROID__
    {"root", required_argument, 0, 'r'},
    {"vndk", required_argument, 0, 'v'},
    {"product_vndk", required_argument, 0, 'p'},
    {"deprecate_vndk", no_argument, 0, 'd'},
    {"recovery", no_argument, 0, 'y'},
    {"treblelize", no_argument, 0, 'z'},
#endif
    {"help", no_argument, 0, 'h'},
    {0, 0, 0, 0}};

struct ProgramArgs {
  std::string target_apex;
  std::string target_directory;
  bool strict;
  std::string root;
  std::string vndk_version;
  std::string product_vndk_version;
  bool is_recovery;
  bool deprecate_vndk;
  bool is_treblelized;
};

[[noreturn]] void PrintUsage(int status = EXIT_SUCCESS) {
  std::cerr << "Usage : linkerconfig [--target <target_directory>]"
               " [--strict]"
               " --apex <name>"
#ifndef __ANDROID__
               " --root <root dir>"
               " --vndk <vndk version>"
               " --product_vndk <product vndk version>"
               " --recovery"
               " --treblelize"
#endif
               " [--help]"
            << std::endl;
  exit(status);
}

std::string RealPath(std::string_view path) {
  char resolved_path[PATH_MAX];
  if (realpath(path.data(), resolved_path) != nullptr) {
    return resolved_path;
  }
  PrintUsage(-1);
}

bool ParseArgs(int argc, char* argv[], ProgramArgs* args) {
  int parse_result;
  while ((parse_result = getopt_long(
              argc, argv, "a:t:sr:v:ep:hzyl", program_options, NULL)) != -1) {
    switch (parse_result) {
      case 'a':
        args->target_apex = optarg;
        break;
      case 't':
        args->target_directory = optarg;
        break;
      case 's':
        args->strict = true;
        break;
      case 'r':
        args->root = RealPath(optarg);
        break;
      case 'v':
        args->vndk_version = optarg;
        break;
      case 'p':
        args->product_vndk_version = optarg;
        break;
      case 'z':
        args->is_treblelized = true;
        break;
      case 'y':
        args->is_recovery = true;
        break;
      case 'h':
        PrintUsage();
      default:
        return false;
    }
  }

  if (optind < argc) {
    return false;
  }

  return true;
}

void LoadVariables(const ProgramArgs& args) {
#ifndef __ANDROID__
  if (!args.is_recovery && args.root == "") {
    PrintUsage();
  }
  android::linkerconfig::modules::Variables::AddValue("ro.vndk.version",
                                                      args.vndk_version);
  android::linkerconfig::modules::Variables::AddValue(
      "ro.product.vndk.version", args.product_vndk_version);

  if (args.is_treblelized) {
    android::linkerconfig::modules::Variables::AddValue("ro.treble.enabled",
                                                        "true");
  }
#endif
  if (!args.is_recovery) {
    android::linkerconfig::generator::LoadVariables(args.root);
  }
}

Result<void> WriteConfigurationToFile(Configuration& conf,
                                      std::string file_path) {
  std::ostream* out = &std::cout;
  std::ofstream file_out;

  if (file_path != "") {
    file_out.open(file_path);
    if (file_out.fail()) {
      return ErrnoError() << "Failed to open file " << file_path;
    }
    out = &file_out;
  }

  android::linkerconfig::modules::ConfigWriter config_writer;

  conf.WriteConfig(config_writer);
  *out << config_writer.ToString();
  if (!out->good()) {
    return ErrnoError() << "Failed to write content to " << file_path;
  }

  return {};
}

Result<void> UpdatePermission([[maybe_unused]] const std::string& file_path) {
#ifdef __ANDROID__
  if (fchmodat(AT_FDCWD,
               file_path.c_str(),
               S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH,
               AT_SYMLINK_NOFOLLOW) < 0) {
    return ErrnoError() << "Failed to update permission of " << file_path;
  }
#endif

  return {};
}

Context GetContext(const ProgramArgs& args) {
  Context ctx;
  if (args.strict) {
    ctx.SetStrictMode(true);
  }
  if (!args.target_apex.empty()) {
    ctx.SetTargetApex(args.target_apex);
  }
  if (!args.is_recovery) {
    auto apex_list = android::linkerconfig::modules::ScanActiveApexes(args.root);
    if (apex_list.ok()) {
      std::vector<ApexInfo> apex_modules;
      for (auto const& apex_item : *apex_list) {
        auto apex_info = apex_item.second;
        if (apex_info.has_bin || apex_info.has_lib) {
          apex_modules.push_back(std::move(apex_info));
        }
      }
      ctx.SetApexModules(std::move(apex_modules));
    } else {
      LOG(ERROR) << "Failed to scan APEX modules : " << apex_list.error();
    }
  }

  std::string system_config_path = args.root + "/system/etc/linker.config.pb";
  if (access(system_config_path.c_str(), F_OK) == 0) {
    auto system_config =
        android::linkerconfig::modules::ParseLinkerConfig(system_config_path);
    if (system_config.ok()) {
      ctx.SetSystemConfig(*system_config);
    } else {
      LOG(ERROR) << "Failed to read system config : " << system_config.error();
    }
  }

  std::string vendor_config_path = args.root + "/vendor/etc/linker.config.pb";
  if (access(vendor_config_path.c_str(), F_OK) == 0) {
    auto vendor_config =
        android::linkerconfig::modules::ParseLinkerConfig(vendor_config_path);
    if (vendor_config.ok()) {
      ctx.SetVendorConfig(*vendor_config);
    } else {
      LOG(ERROR) << "Failed to read vendor config : " << vendor_config.error();
    }
  }

  std::string product_config_path = args.root + "/product/etc/linker.config.pb";
  if (access(product_config_path.c_str(), F_OK) == 0) {
    auto product_config =
        android::linkerconfig::modules::ParseLinkerConfig(product_config_path);
    if (product_config.ok()) {
      ctx.SetProductConfig(*product_config);
    } else {
      LOG(ERROR) << "Failed to read product config : " << product_config.error();
    }
  }
  return ctx;
}

Configuration GetConfiguration(Context& ctx) {
  if (android::linkerconfig::modules::IsRecoveryMode()) {
    return android::linkerconfig::contents::CreateRecoveryConfiguration(ctx);
  }

  if (!android::linkerconfig::modules::IsTreblelizedDevice()) {
    return android::linkerconfig::contents::CreateLegacyConfiguration(ctx);
  }

  // Use base configuration in default
  return android::linkerconfig::contents::CreateBaseConfiguration(ctx);
}

Result<void> GenerateConfiguration(Configuration config,
                                   const std::string& dir_path,
                                   bool update_permission) {
  std::string file_path = "";
  if (dir_path != "") {
    file_path = dir_path + "/ld.config.txt";
  }

  auto write_config = WriteConfigurationToFile(config, file_path);
  if (!write_config.ok()) {
    return write_config;
  } else if (update_permission && file_path != "") {
    return UpdatePermission(file_path);
  }

  return {};
}

Result<void> GenerateBaseLinkerConfiguration(Context& ctx,
                                             const std::string& dir_path) {
  return GenerateConfiguration(GetConfiguration(ctx), dir_path, true);
}

Result<void> GenerateRecoveryLinkerConfiguration(Context& ctx,
                                                 const std::string& dir_path) {
  return GenerateConfiguration(
      android::linkerconfig::contents::CreateRecoveryConfiguration(ctx),
      dir_path,
      false);
}

Result<void> GenerateApexConfiguration(
    android::linkerconfig::contents::Context& ctx,
    const android::linkerconfig::modules::ApexInfo& target_apex,
    const std::string& base_dir) {
  if (!target_apex.has_bin) {
    return {};
  }

  std::string dir_path = base_dir + "/" + target_apex.name;
  if (auto ret = mkdir(dir_path.c_str(), 0755); ret != 0 && errno != EEXIST) {
    return ErrnoError() << "Failed to create directory " << dir_path;
  }

  return GenerateConfiguration(
      android::linkerconfig::contents::CreateApexConfiguration(ctx, target_apex),
      dir_path,
      true);
}

Result<void> GenerateApexConfiguration(
    android::linkerconfig::contents::Context& ctx, const std::string& apex_name,
    const std::string& base_dir) {
  auto end = std::end(ctx.GetApexModules());
  auto it = std::find_if(std::begin(ctx.GetApexModules()),
                         end,
                         [&apex_name](const auto& apex_info) {
                           return apex_info.name == apex_name;
                         });
  if (it == end) {
    return Error() << apex_name << " not found.";
  }
  return GenerateApexConfiguration(ctx, *it, base_dir);
}

void GenerateApexConfigurations(Context& ctx, const std::string& dir_path) {
  for (auto const& apex_item : ctx.GetApexModules()) {
    auto result = GenerateApexConfiguration(ctx, apex_item, dir_path);
    if (!result.ok()) {
      LOG(WARNING) << result.error();
    }
  }
}

void GenerateApexLibrariesConfig(Context& ctx, const std::string& dir_path) {
  if (dir_path == "") {
    return;
  }
  const std::string file_path = dir_path + "/apex.libraries.config.txt";
  std::ofstream out(file_path);
  for (auto const& apex_item : ctx.GetApexModules()) {
    if (!apex_item.jni_libs.empty()) {
      out << "jni " << apex_item.namespace_name << " "
          << Join(apex_item.jni_libs, ":") << '\n';
    }
    if (!apex_item.public_libs.empty()) {
      out << "public " << apex_item.namespace_name << " "
          << Join(apex_item.public_libs, ":") << '\n';
    }
  }
  out.close();
  UpdatePermission(file_path);
}

void ExitOnFailure(Result<void> task) {
  if (!task.ok()) {
    LOG(FATAL) << task.error();
    exit(EXIT_FAILURE);
  }
}

#ifdef __ANDROID__
struct CombinedLogger {
  android::base::LogdLogger logd;

  void operator()(android::base::LogId id, android::base::LogSeverity severity,
                  const char* tag, const char* file, unsigned int line,
                  const char* message) {
    logd(id, severity, tag, file, line, message);
    KernelLogger(id, severity, tag, file, line, message);
  }
};
#endif
}  // namespace

int main(int argc, char* argv[]) {
  android::base::InitLogging(argv
#ifdef __ANDROID__
                             ,
                             CombinedLogger()
#endif
  );

  ProgramArgs args = {};

  if (!ParseArgs(argc, argv, &args)) {
    PrintUsage(EXIT_FAILURE);
  }

  if (android::linkerconfig::modules::IsTreblelizedDevice() &&
      android::linkerconfig::modules::IsVndkLiteDevice()) {
    LOG(ERROR) << "Linkerconfig no longer supports VNDK-Lite configuration";
    exit(EXIT_FAILURE);
  }

  LoadVariables(args);
  Context ctx = GetContext(args);

  // when exec'ed from init, this is 077, which makes the subdirectories
  // inaccessible for others. set umask to 022 so that they can be
  // accessible.
  umask(022);

  if (args.is_recovery) {
    ExitOnFailure(
        GenerateRecoveryLinkerConfiguration(ctx, args.target_directory));
  } else if (args.target_apex != "") {
    ExitOnFailure(GenerateApexConfiguration(
        ctx, args.target_apex, args.target_directory));
  } else {
    ExitOnFailure(GenerateBaseLinkerConfiguration(ctx, args.target_directory));
    GenerateApexConfigurations(ctx, args.target_directory);
    GenerateApexLibrariesConfig(ctx, args.target_directory);
  }

  return EXIT_SUCCESS;
}
