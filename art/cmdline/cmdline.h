/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_CMDLINE_CMDLINE_H_
#define ART_CMDLINE_CMDLINE_H_

#include <stdio.h>
#include <stdlib.h>

#include <fstream>
#include <iostream>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "base/file_utils.h"
#include "base/logging.h"
#include "base/mutex.h"
#include "base/string_view_cpp20.h"
#include "base/utils.h"
#include "noop_compiler_callbacks.h"
#include "oat/oat_file_assistant_context.h"
#include "runtime.h"

#if !defined(NDEBUG)
#define DBG_LOG LOG(INFO)
#else
#define DBG_LOG LOG(DEBUG)
#endif

namespace art {

static Runtime* StartRuntime(const std::vector<std::string>& boot_image_locations,
                             InstructionSet instruction_set,
                             const std::vector<const char*>& runtime_args) {
  RuntimeOptions options;

  // We are more like a compiler than a run-time. We don't want to execute code.
  {
    static NoopCompilerCallbacks callbacks;
    options.push_back(std::make_pair("compilercallbacks", &callbacks));
  }

  // Boot image location.
  {
    std::string boot_image_option = "-Ximage:";
    if (!boot_image_locations.empty()) {
      boot_image_option += android::base::Join(boot_image_locations, ':');
    } else {
      boot_image_option += GetJitZygoteBootImageLocation();
    }
    options.push_back(std::make_pair(boot_image_option, nullptr));
  }

  // Instruction set.
  options.push_back(
      std::make_pair("imageinstructionset",
                     reinterpret_cast<const void*>(GetInstructionSetString(instruction_set))));

  // Explicit runtime args.
  for (const char* runtime_arg : runtime_args) {
    options.push_back(std::make_pair(runtime_arg, nullptr));
  }

  // None of the command line tools need sig chain. If this changes we'll need
  // to upgrade this option to a proper parameter.
  options.push_back(std::make_pair("-Xno-sig-chain", nullptr));
  if (!Runtime::Create(options, false)) {
    fprintf(stderr, "Failed to create runtime\n");
    return nullptr;
  }

  // Runtime::Create acquired the mutator_lock_ that is normally given away when we Runtime::Start,
  // give it away now and then switch to a more manageable ScopedObjectAccess.
  Thread::Current()->TransitionFromRunnableToSuspended(ThreadState::kNative);

  return Runtime::Current();
}

struct CmdlineArgs {
  enum ParseStatus {
    kParseOk,               // Parse successful. Do not set the error message.
    kParseUnknownArgument,  // Unknown argument. Do not set the error message.
    kParseError,            // Parse ok, but failed elsewhere. Print the set error message.
  };

  bool Parse(int argc, char** argv) {
    // Skip over argv[0].
    argv++;
    argc--;

    if (argc == 0) {
      fprintf(stderr, "No arguments specified\n");
      PrintUsage();
      return false;
    }

    std::string error_msg;
    for (int i = 0; i < argc; i++) {
      const char* const raw_option = argv[i];
      const std::string_view option(raw_option);
      if (StartsWith(option, "--boot-image=")) {
        Split(raw_option + strlen("--boot-image="), ':', &boot_image_locations_);
      } else if (StartsWith(option, "--instruction-set=")) {
        const char* const instruction_set_str = raw_option + strlen("--instruction-set=");
        instruction_set_ = GetInstructionSetFromString(instruction_set_str);
        if (instruction_set_ == InstructionSet::kNone) {
          fprintf(stderr, "Unsupported instruction set %s\n", instruction_set_str);
          PrintUsage();
          return false;
        }
      } else if (option == "--runtime-arg") {
        if (i + 1 == argc) {
          fprintf(stderr, "Missing argument for --runtime-arg\n");
          PrintUsage();
          return false;
        }
        ++i;
        runtime_args_.push_back(argv[i]);
      } else if (StartsWith(option, "--output=")) {
        output_name_ = std::string(option.substr(strlen("--output=")));
        const char* filename = output_name_.c_str();
        out_.reset(new std::ofstream(filename));
        if (!out_->good()) {
          fprintf(stderr, "Failed to open output filename %s\n", filename);
          PrintUsage();
          return false;
        }
        os_ = out_.get();
      } else {
        ParseStatus parse_status = ParseCustom(raw_option, option.length(), &error_msg);

        if (parse_status == kParseUnknownArgument) {
          fprintf(stderr, "Unknown argument %s\n", option.data());
        }

        if (parse_status != kParseOk) {
          fprintf(stderr, "%s\n", error_msg.c_str());
          PrintUsage();
          return false;
        }
      }
    }

    if (instruction_set_ == InstructionSet::kNone) {
      LOG(WARNING) << "No instruction set given, assuming " << GetInstructionSetString(kRuntimeISA);
      instruction_set_ = kRuntimeISA;
    }

    DBG_LOG << "will call parse checks";

    {
      ParseStatus checks_status = ParseChecks(&error_msg);
      if (checks_status != kParseOk) {
          fprintf(stderr, "%s\n", error_msg.c_str());
          PrintUsage();
          return false;
      }
    }

    return true;
  }

  virtual std::string GetUsage() const {
    std::string usage;

    usage +=  // Required.
        "  --boot-image=<file.art>: provide the image location for the boot class path.\n"
        "      Do not include the arch as part of the name, it is added automatically.\n"
        "      Example: --boot-image=/system/framework/boot.art\n"
        "               (specifies /system/framework/<arch>/boot.art as the image file)\n"
        "\n";
    usage += android::base::StringPrintf(  // Optional.
        "  --instruction-set=(arm|arm64|x86|x86_64): for locating the image\n"
        "      file based on the image location set.\n"
        "      Example: --instruction-set=x86\n"
        "      Default: %s\n"
        "\n",
        GetInstructionSetString(kRuntimeISA));
    usage +=
        "  --runtime-arg <argument> used to specify various arguments for the runtime\n"
        "      such as initial heap size, maximum heap size, and verbose output.\n"
        "      Use a separate --runtime-arg switch for each argument.\n"
        "      Example: --runtime-arg -Xms256m\n"
        "\n";
    usage +=  // Optional.
        "  --output=<file> may be used to send the output to a file.\n"
        "      Example: --output=/tmp/oatdump.txt\n"
        "\n";

    return usage;
  }

  // Specified by --runtime-arg -Xbootclasspath or default.
  std::vector<std::string> boot_class_path_;
  // Specified by --runtime-arg -Xbootclasspath-locations or default.
  std::vector<std::string> boot_class_path_locations_;
  // True if `boot_class_path_` is the default one.
  bool is_default_boot_class_path_ = false;
  // Specified by --boot-image or inferred.
  std::vector<std::string> boot_image_locations_;
  // Specified by --instruction-set.
  InstructionSet instruction_set_ = InstructionSet::kNone;
  // Runtime arguments specified by --runtime-arg.
  std::vector<const char*> runtime_args_;
  // Specified by --output.
  std::ostream* os_ = &std::cout;
  std::unique_ptr<std::ofstream> out_;  // If something besides cout is used
  std::string output_name_;

  virtual ~CmdlineArgs() {}

  // Checks for --boot-image location.
  bool ParseCheckBootImage(std::string* error_msg) {
    if (boot_image_locations_.empty()) {
      LOG(WARNING) << "--boot-image not specified. Starting runtime in imageless mode";
      return true;
    }

    const std::string& boot_image_location = boot_image_locations_[0];
    size_t file_name_idx = boot_image_location.rfind('/');
    if (file_name_idx == std::string::npos) {  // Prevent a InsertIsaDirectory check failure.
      *error_msg = "Boot image location must have a / in it";
      return false;
    }

    // Don't let image locations with the 'arch' in it through, since it's not a location.
    // This prevents a common error "Could not create an image space..." when initing the Runtime.
    if (file_name_idx > 0) {
      size_t ancestor_dirs_idx = boot_image_location.rfind('/', file_name_idx - 1);

      std::string parent_dir_name;
      if (ancestor_dirs_idx != std::string::npos) {
          parent_dir_name = boot_image_location.substr(/*pos=*/ancestor_dirs_idx + 1,
                                                       /*n=*/file_name_idx - ancestor_dirs_idx - 1);
      } else {
          parent_dir_name = boot_image_location.substr(/*pos=*/0,
                                                       /*n=*/file_name_idx);
      }

      DBG_LOG << "boot_image_location parent_dir_name was " << parent_dir_name;

      if (GetInstructionSetFromString(parent_dir_name.c_str()) != InstructionSet::kNone) {
          *error_msg = "Do not specify the architecture as part of the boot image location";
          return false;
      }
    }

    return true;
  }

  void PrintUsage() { fprintf(stderr, "%s", GetUsage().c_str()); }

  std::unique_ptr<OatFileAssistantContext> GetOatFileAssistantContext(std::string* error_msg) {
    if (boot_class_path_.empty()) {
      *error_msg = "Boot classpath is empty";
      return nullptr;
    }

    CHECK(!boot_class_path_locations_.empty());

    return std::make_unique<OatFileAssistantContext>(
        std::make_unique<OatFileAssistantContext::RuntimeOptions>(
            OatFileAssistantContext::RuntimeOptions{
                .image_locations = boot_image_locations_,
                .boot_class_path = boot_class_path_,
                .boot_class_path_locations = boot_class_path_locations_,
            }));
  }

 protected:
  virtual ParseStatus ParseCustom([[maybe_unused]] const char* raw_option,
                                  [[maybe_unused]] size_t raw_option_length,
                                  [[maybe_unused]] std::string* error_msg) {
    return kParseUnknownArgument;
  }

  virtual ParseStatus ParseChecks([[maybe_unused]] std::string* error_msg) {
    ParseBootclasspath();
    if (boot_image_locations_.empty()) {
      InferBootImage();
    }
    return kParseOk;
  }

 private:
  void ParseBootclasspath() {
    std::optional<std::string_view> bcp_str = std::nullopt;
    std::optional<std::string_view> bcp_location_str = std::nullopt;
    for (const char* arg : runtime_args_) {
      if (StartsWith(arg, "-Xbootclasspath:")) {
          bcp_str = arg + strlen("-Xbootclasspath:");
      }
      if (StartsWith(arg, "-Xbootclasspath-locations:")) {
          bcp_location_str = arg + strlen("-Xbootclasspath-locations:");
      }
    }

    if (bcp_str.has_value() && bcp_location_str.has_value()) {
      Split(*bcp_str, ':', &boot_class_path_);
      Split(*bcp_location_str, ':', &boot_class_path_locations_);
    } else if (bcp_str.has_value()) {
      Split(*bcp_str, ':', &boot_class_path_);
      boot_class_path_locations_ = boot_class_path_;
    } else {
      // Try the default.
      const char* env_value = getenv("BOOTCLASSPATH");
      if (env_value != nullptr && strlen(env_value) > 0) {
          Split(env_value, ':', &boot_class_path_);
          boot_class_path_locations_ = boot_class_path_;
          is_default_boot_class_path_ = true;
      }
    }
  }

  // Infers the boot image on a best-effort basis.
  // The inference logic aligns with installd/artd + dex2oat.
  void InferBootImage() {
    // The boot image inference only makes sense on device.
    if (!kIsTargetAndroid) {
      return;
    }

    // The inferred boot image can only be used with the default bootclasspath.
    if (boot_class_path_.empty() || !is_default_boot_class_path_) {
      return;
    }

    std::string error_msg;
    std::string boot_image = GetBootImageLocationForDefaultBcpRespectingSysProps(&error_msg);
    if (boot_image.empty()) {
      LOG(WARNING) << "Failed to infer boot image: " << error_msg;
      return;
    }

    LOG(INFO) << "Inferred boot image: " << boot_image;
    Split(boot_image, ':', &boot_image_locations_);

    // Verify the inferred boot image.
    std::unique_ptr<OatFileAssistantContext> ofa_context = GetOatFileAssistantContext(&error_msg);
    CHECK_NE(ofa_context, nullptr);
    size_t verified_boot_image_count = ofa_context->GetBootImageInfoList(instruction_set_).size();
    if (verified_boot_image_count != boot_image_locations_.size()) {
      LOG(WARNING) << "Failed to verify inferred boot image";
      boot_image_locations_.resize(verified_boot_image_count);
    }
  }
};

template <typename Args = CmdlineArgs>
struct CmdlineMain {
  int Main(int argc, char** argv) {
    Locks::Init();
    InitLogging(argv, Runtime::Abort);
    std::unique_ptr<Args> args = std::unique_ptr<Args>(CreateArguments());
    args_ = args.get();

    DBG_LOG << "Try to parse";

    if (args_ == nullptr || !args_->Parse(argc, argv)) {
      return EXIT_FAILURE;
    }

    bool needs_runtime = NeedsRuntime();
    std::unique_ptr<Runtime> runtime;


    if (needs_runtime) {
      std::string error_msg;
      if (!args_->ParseCheckBootImage(&error_msg)) {
        fprintf(stderr, "%s\n", error_msg.c_str());
        args_->PrintUsage();
        return EXIT_FAILURE;
      }
      runtime.reset(CreateRuntime(args.get()));
      if (runtime == nullptr) {
        return EXIT_FAILURE;
      }
      if (!ExecuteWithRuntime(runtime.get())) {
        return EXIT_FAILURE;
      }
    } else {
      if (!ExecuteWithoutRuntime()) {
        return EXIT_FAILURE;
      }
    }

    if (!ExecuteCommon()) {
      return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
  }

  // Override this function to create your own arguments.
  // Usually will want to return a subtype of CmdlineArgs.
  virtual Args* CreateArguments() {
    return new Args();
  }

  // Override this function to do something else with the runtime.
  virtual bool ExecuteWithRuntime(Runtime* runtime) {
    CHECK(runtime != nullptr);
    // Do nothing
    return true;
  }

  // Does the code execution need a runtime? Sometimes it doesn't.
  virtual bool NeedsRuntime() {
    return true;
  }

  // Do execution without having created a runtime.
  virtual bool ExecuteWithoutRuntime() {
    return true;
  }

  // Continue execution after ExecuteWith[out]Runtime
  virtual bool ExecuteCommon() {
    return true;
  }

  virtual ~CmdlineMain() {}

 protected:
  Args* args_ = nullptr;

 private:
  Runtime* CreateRuntime(CmdlineArgs* args) {
    CHECK(args != nullptr);

    return StartRuntime(args->boot_image_locations_, args->instruction_set_, args_->runtime_args_);
  }
};
}  // namespace art

#endif  // ART_CMDLINE_CMDLINE_H_
