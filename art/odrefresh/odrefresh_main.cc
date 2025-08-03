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

#include <sys/stat.h>

#include <string>
#include <string_view>
#include <unordered_map>

#include "android-base/parsebool.h"
#include "android-base/properties.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "arch/instruction_set.h"
#include "base/file_utils.h"
#include "base/globals.h"
#include "base/mem_map.h"
#include "base/stl_util.h"
#include "odr_common.h"
#include "odr_compilation_log.h"
#include "odr_config.h"
#include "odr_metrics.h"
#include "odrefresh.h"
#include "odrefresh/odrefresh.h"

namespace {

using ::android::base::GetProperty;
using ::android::base::ParseBool;
using ::android::base::ParseBoolResult;
using ::android::base::StartsWith;
using ::art::odrefresh::CompilationOptions;
using ::art::odrefresh::ExitCode;
using ::art::odrefresh::kCheckedSystemPropertyPrefixes;
using ::art::odrefresh::kSystemProperties;
using ::art::odrefresh::kSystemPropertySystemServerCompilerFilterOverride;
using ::art::odrefresh::OdrCompilationLog;
using ::art::odrefresh::OdrConfig;
using ::art::odrefresh::OdrMetrics;
using ::art::odrefresh::OnDeviceRefresh;
using ::art::odrefresh::QuotePath;
using ::art::odrefresh::ShouldDisablePartialCompilation;
using ::art::odrefresh::ShouldDisableRefresh;
using ::art::odrefresh::SystemPropertyConfig;
using ::art::odrefresh::SystemPropertyForeach;
using ::art::odrefresh::ZygoteKind;

void UsageMsgV(const char* fmt, va_list ap) {
  std::string error;
  android::base::StringAppendV(&error, fmt, ap);
  if (isatty(fileno(stderr))) {
    std::cerr << error << std::endl;
  } else {
    LOG(ERROR) << error;
  }
}

void UsageMsg(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  UsageMsgV(fmt, ap);
  va_end(ap);
}

NO_RETURN void ArgumentError(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  UsageMsgV(fmt, ap);
  va_end(ap);
  UsageMsg("Try '--help' for more information.");
  exit(EX_USAGE);
}

bool ParseZygoteKind(const char* input, ZygoteKind* zygote_kind) {
  std::string_view z(input);
  if (z == "zygote32") {
    *zygote_kind = ZygoteKind::kZygote32;
    return true;
  } else if (z == "zygote32_64") {
    *zygote_kind = ZygoteKind::kZygote32_64;
    return true;
  } else if (z == "zygote64_32") {
    *zygote_kind = ZygoteKind::kZygote64_32;
    return true;
  } else if (z == "zygote64") {
    *zygote_kind = ZygoteKind::kZygote64;
    return true;
  }
  return false;
}

std::string GetEnvironmentVariableOrDie(const char* name) {
  const char* value = getenv(name);
  LOG_ALWAYS_FATAL_IF(value == nullptr, "%s is not defined.", name);
  return value;
}

std::string GetEnvironmentVariableOrDefault(const char* name, std::string default_value) {
  const char* value = getenv(name);
  if (value == nullptr) {
    return default_value;
  }
  return value;
}

bool ArgumentMatches(std::string_view argument, std::string_view prefix, std::string* value) {
  if (android::base::StartsWith(argument, prefix)) {
    *value = std::string(argument.substr(prefix.size()));
    return true;
  }
  return false;
}

bool ArgumentEquals(std::string_view argument, std::string_view expected) {
  return argument == expected;
}

int InitializeConfig(int argc, char** argv, OdrConfig* config) {
  config->SetApexInfoListFile("/apex/apex-info-list.xml");
  config->SetArtBinDir(art::GetArtBinDir());
  config->SetBootClasspath(GetEnvironmentVariableOrDie("BOOTCLASSPATH"));
  config->SetDex2oatBootclasspath(GetEnvironmentVariableOrDie("DEX2OATBOOTCLASSPATH"));
  config->SetSystemServerClasspath(GetEnvironmentVariableOrDie("SYSTEMSERVERCLASSPATH"));
  config->SetStandaloneSystemServerJars(
      GetEnvironmentVariableOrDefault("STANDALONE_SYSTEMSERVER_JARS", /*default_value=*/""));
  config->SetIsa(art::kRuntimeISA);

  std::string zygote;
  int n = 1;
  for (; n < argc - 1; ++n) {
    const char* arg = argv[n];
    std::string value;
    if (ArgumentEquals(arg, "--compilation-os-mode")) {
      config->SetCompilationOsMode(true);
    } else if (ArgumentMatches(arg, "--dalvik-cache=", &value)) {
      art::OverrideDalvikCacheSubDirectory(value);
      config->SetArtifactDirectory(GetApexDataDalvikCacheDirectory(art::InstructionSet::kNone));
    } else if (ArgumentMatches(arg, "--zygote-arch=", &value)) {
      zygote = value;
    } else if (ArgumentMatches(arg, "--boot-image-compiler-filter=", &value)) {
      config->SetBootImageCompilerFilter(value);
    } else if (ArgumentMatches(arg, "--system-server-compiler-filter=", &value)) {
      config->SetSystemServerCompilerFilter(value);
    } else if (ArgumentMatches(arg, "--staging-dir=", &value)) {
      config->SetStagingDir(value);
    } else if (ArgumentEquals(arg, "--dry-run")) {
      config->SetDryRun();
    } else if (ArgumentMatches(arg, "--partial-compilation=", &value)) {
      config->SetPartialCompilation(ParseBool(value) == ParseBoolResult::kTrue);
    } else if (ArgumentEquals(arg, "--no-refresh")) {
      config->SetRefresh(false);
    } else if (ArgumentEquals(arg, "--minimal")) {
      config->SetMinimal(true);
    } else {
      ArgumentError("Unrecognized argument: '%s'", arg);
    }
  }

  if (zygote.empty()) {
    // Use ro.zygote by default, if not overridden by --zygote-arch flag.
    zygote = GetProperty("ro.zygote", {});
  }
  ZygoteKind zygote_kind;
  if (!ParseZygoteKind(zygote.c_str(), &zygote_kind)) {
    LOG(FATAL) << "Unknown zygote: " << QuotePath(zygote);
  }
  config->SetZygoteKind(zygote_kind);

  if (config->GetSystemServerCompilerFilter().empty()) {
    std::string filter = GetProperty("dalvik.vm.systemservercompilerfilter", "");
    filter = GetProperty(kSystemPropertySystemServerCompilerFilterOverride, filter);
    config->SetSystemServerCompilerFilter(filter);
  }

  if (!config->HasPartialCompilation() &&
      ShouldDisablePartialCompilation(
          GetProperty("ro.build.version.security_patch", /*default_value=*/""))) {
    config->SetPartialCompilation(false);
  }

  if (ShouldDisableRefresh(GetProperty("ro.build.version.sdk", /*default_value=*/""))) {
    config->SetRefresh(false);
  }

  return n;
}

void GetSystemProperties(std::unordered_map<std::string, std::string>* system_properties) {
  SystemPropertyForeach([&](const char* name, const char* value) {
    if (strlen(value) == 0) {
      return;
    }
    for (const char* prefix : kCheckedSystemPropertyPrefixes) {
      if (StartsWith(name, prefix)) {
        (*system_properties)[name] = value;
      }
    }
  });
  for (const SystemPropertyConfig& system_property_config : *kSystemProperties.get()) {
    (*system_properties)[system_property_config.name] =
        GetProperty(system_property_config.name, system_property_config.default_value);
  }
}

NO_RETURN void UsageHelp(const char* argv0) {
  std::string name(android::base::Basename(argv0));
  UsageMsg("Usage: %s [OPTION...] ACTION", name.c_str());
  UsageMsg("On-device refresh tool for boot classpath and system server");
  UsageMsg("following an update of the ART APEX.");
  UsageMsg("");
  UsageMsg("Valid ACTION choices are:");
  UsageMsg("");
  UsageMsg("--check          Check compilation artifacts are up-to-date based on metadata.");
  UsageMsg("--compile        Compile boot classpath and system_server jars when necessary.");
  UsageMsg("--force-compile  Unconditionally compile the bootclass path and system_server jars.");
  UsageMsg("--help           Display this help information.");
  UsageMsg("");
  UsageMsg("Available OPTIONs are:");
  UsageMsg("");
  UsageMsg("--dry-run");
  UsageMsg("--partial-compilation            Only generate artifacts that are out-of-date or");
  UsageMsg("                                 missing.");
  UsageMsg("--no-refresh                     Do not refresh existing artifacts.");
  UsageMsg("--compilation-os-mode            Indicate that odrefresh is running in Compilation");
  UsageMsg("                                 OS.");
  UsageMsg("--dalvik-cache=<DIR>             Write artifacts to .../<DIR> rather than");
  UsageMsg("                                 .../dalvik-cache");
  UsageMsg("--staging-dir=<DIR>              Write temporary artifacts to <DIR> rather than");
  UsageMsg("                                 .../staging");
  UsageMsg("--zygote-arch=<STRING>           Zygote kind that overrides ro.zygote");
  UsageMsg("--boot-image-compiler-filter=<STRING>");
  UsageMsg("                                 Compiler filter for the boot image. Default: ");
  UsageMsg("                                 speed-profile");
  UsageMsg("--system-server-compiler-filter=<STRING>");
  UsageMsg("                                 Compiler filter that overrides");
  UsageMsg("                                 dalvik.vm.systemservercompilerfilter");
  UsageMsg("--minimal                        Generate a minimal boot image only.");

  exit(EX_USAGE);
}

}  // namespace

int main(int argc, char** argv) {
  // odrefresh is launched by `init` which sets the umask of forked processed to
  // 077 (S_IRWXG | S_IRWXO). This blocks the ability to make files and directories readable
  // by others and prevents system_server from loading generated artifacts.
  umask(S_IWGRP | S_IWOTH);

  // Explicitly initialize logging (b/201042799).
  android::base::InitLogging(argv, android::base::LogdLogger(android::base::SYSTEM));

  OdrConfig config(argv[0]);
  int n = InitializeConfig(argc, argv, &config);

  art::MemMap::Init();  // Needed by DexFileLoader.

  argv += n;
  argc -= n;
  if (argc != 1) {
    ArgumentError("Expected 1 argument, but have %d.", argc);
  }

  GetSystemProperties(config.MutableSystemProperties());

  OdrMetrics metrics(config.GetArtifactDirectory());
  OnDeviceRefresh odr(config);

  std::string_view action(argv[0]);
  CompilationOptions compilation_options;
  if (action == "--check") {
    // Fast determination of whether artifacts are up to date.
    ExitCode exit_code = odr.CheckArtifactsAreUpToDate(metrics, &compilation_options);
    // Normally, `--check` should not write metrics. If compilation is not required, there's no need
    // to write metrics; if compilation is required, `--compile` will write metrics. Therefore,
    // `--check` should only write metrics when things went wrong.
    metrics.SetEnabled(exit_code != ExitCode::kOkay && exit_code != ExitCode::kCompilationRequired);
    return exit_code;
  } else if (action == "--compile") {
    ExitCode exit_code = odr.CheckArtifactsAreUpToDate(metrics, &compilation_options);
    if (exit_code != ExitCode::kCompilationRequired) {
      // No compilation required, so only write metrics when things went wrong.
      metrics.SetEnabled(exit_code != ExitCode::kOkay);
      return exit_code;
    }
    if (config.GetSystemProperties().GetBool("dalvik.vm.disable-odrefresh",
                                             /*default_value=*/false)) {
      LOG(INFO) << "Compilation skipped because it's disabled by system property";
      return ExitCode::kOkay;
    }
    OdrCompilationLog compilation_log;
    if (!compilation_log.ShouldAttemptCompile(metrics.GetTrigger())) {
      LOG(INFO) << "Compilation skipped because it was attempted recently";
      return ExitCode::kOkay;
    }
    // Compilation required, so always write metrics.
    metrics.SetEnabled(true);
    ExitCode compile_result = odr.Compile(metrics, compilation_options);
    compilation_log.Log(metrics.GetArtApexVersion(),
                        metrics.GetArtApexLastUpdateMillis(),
                        metrics.GetTrigger(),
                        compile_result);
    return compile_result;
  } else if (action == "--force-compile") {
    // Clean-up existing files.
    if (!odr.RemoveArtifactsDirectory()) {
      metrics.SetStatus(OdrMetrics::Status::kIoError);
      return ExitCode::kCleanupFailed;
    }
    return odr.Compile(metrics, CompilationOptions::CompileAll(odr));
  } else if (action == "--help") {
    UsageHelp(argv[0]);
  } else {
    ArgumentError("Unknown argument: %s", action.data());
  }
}
