/*
 * Copyright (C) 2015, The Android Open Source Project
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

#include "options.h"

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/strings.h>
#include <getopt.h>
#include <stdlib.h>
#include <unistd.h>

#include <algorithm>
#include <iostream>
#include <sstream>
#include <string>

#include "aidl_language.h"
#include "logging.h"
#include "os.h"

using android::base::Join;
using android::base::Result;
using android::base::Split;
using android::base::StringReplace;
using android::base::Trim;
using std::endl;
using std::string;

#ifndef PLATFORM_SDK_VERSION
#define PLATFORM_SDK_VERSION "<UNKNOWN>"
#endif

namespace android {
namespace aidl {

string Options::GetUsage() const {
  std::ostringstream sstr;
  sstr << "AIDL Compiler: built for platform SDK version " << PLATFORM_SDK_VERSION << endl;
  sstr << "usage:" << endl
       << myname_ << " --lang={java|cpp|ndk|rust} [OPTION]... INPUT..." << endl
       << "   Generate Java, C++ or Rust files for AIDL file(s)." << endl
       << endl
       << myname_ << " --preprocess OUTPUT INPUT..." << endl
       << "   Create an AIDL file having declarations of AIDL file(s)." << endl
       << endl
       << myname_ << " --dumpapi --out=DIR INPUT..." << endl
       << "   Dump API signature of AIDL file(s) to DIR." << endl
       << endl
       << myname_ << " --checkapi[={compatible|equal}] OLD_DIR NEW_DIR" << endl
       << "   Check whether NEW_DIR API dump is {compatible|equal} extension " << endl
       << "   of the API dump OLD_DIR. Default: compatible" << endl
       << endl
       << myname_ << " --apimapping OUTPUT INPUT..." << endl
       << "   Generate a mapping of declared aidl method signatures to" << endl
       << "   the original line number. e.g.: " << endl
       << "       If line 39 of foo/bar/IFoo.aidl contains:"
       << "         void doFoo(int bar, String baz);" << endl
       << "       Then the result would be:" << endl
       << "         foo.bar.Baz|doFoo|int,String,|void" << endl
       << "         foo/bar/IFoo.aidl:39" << endl
       << endl;

  // Legacy option formats
  if (language_ == Options::Language::JAVA) {
    sstr << myname_ << " [OPTION]... INPUT [OUTPUT]" << endl
         << "   Generate a Java file for an AIDL file." << endl
         << endl;
  } else if (language_ == Options::Language::CPP) {
    sstr << myname_ << " [OPTION]... INPUT HEADER_DIR OUTPUT" << endl
         << "   Generate C++ headers and source for an AIDL file." << endl
         << endl;
  } else if (language_ == Options::Language::RUST) {
    sstr << myname_ << " [OPTION]... INPUT [OUTPUT]" << endl
         << "   Generate Rust file for an AIDL file." << endl
         << endl;
  }

  sstr << "OPTION:" << endl
       << "  -I DIR, --include=DIR" << endl
       << "          Use DIR as a search path for import statements of dependencies." << endl
       << "  -N DIR, --next_include=DIR" << endl
       << "          Use DIR as a search path for local import statements." << endl
       << "  -p FILE, --preprocessed=FILE" << endl
       << "          Include FILE which is created by --preprocess." << endl
       << "  -d FILE, --dep=FILE" << endl
       << "          Generate dependency file as FILE. Don't use this when" << endl
       << "          there are multiple input files. Use -a then." << endl
       << "  -o DIR, --out=DIR" << endl
       << "          Use DIR as the base output directory for generated files." << endl
       << "  -h DIR, --header_out=DIR" << endl
       << "          Generate C++ headers under DIR." << endl
       << "  --previous_api_dir=DIR" << endl
       << "          The aidl_api directory of the previous version of this interface." << endl
       << "  -a" << endl
       << "          Generate dependency file next to the output file with the" << endl
       << "          name based on the input file." << endl
       << "  -b" << endl
       << "          Trigger fail when trying to compile a parcelable declaration." << endl
       << "  --ninja" << endl
       << "          Generate dependency file in a format ninja understands." << endl
       << "  --rpc" << endl
       << "          (for Java) whether to generate support for RPC transactions." << endl
       << "  --structured" << endl
       << "          Whether this interface is defined exclusively in AIDL." << endl
       << "          It is therefore a candidate for stabilization." << endl
       << "  --stability=<level>" << endl
       << "          The stability requirement of this interface." << endl
       << "  --min_sdk_version=<version>" << endl
       << "          Minimum SDK version that the generated code should support." << endl
       << "          Defaults to " << DEFAULT_SDK_VERSION_JAVA << " for --lang=java, " << endl
       << "            " << DEFAULT_SDK_VERSION_CPP << " for --lang=cpp, " << endl
       << "            " << DEFAULT_SDK_VERSION_NDK << " for --lang=ndk, " << endl
       << "            " << DEFAULT_SDK_VERSION_RUST << " for --lang=rust, " << endl
       << "  --omit_invocation" << endl
       << "          Do not print full commandline in output. This is required " << endl
       << "          for certain build systems." << endl
       << "  -t, --trace" << endl
       << "          Include tracing code for systrace. Note that if either" << endl
       << "          the client or service code is not auto-generated by this" << endl
       << "          tool, that part will not be traced." << endl
       << "  --transaction_names" << endl
       << "          Generate transaction names." << endl
       << "  -v VER, --version=VER" << endl
       << "          Set the version of the interface and parcelable to VER." << endl
       << "          VER must be an interger greater than 0." << endl
       << "  --hash=HASH" << endl
       << "          Set the interface hash to HASH." << endl
       << "  --previous_hash=HASH" << endl
       << "          Set the interface previous version's hash to HASH." << endl
       << "  --log" << endl
       << "          Information about the transaction, e.g., method name, argument" << endl
       << "          values, execution time, etc., is provided via callback." << endl
       << "  -Werror" << endl
       << "          Turn warnings into errors." << endl
       << "  -Wno-error=<warning>" << endl
       << "          Turn the specified warning into a warning even if -Werror is specified."
       << endl
       << "  -W<warning>" << endl
       << "          Enable the specified warning." << endl
       << "  -Wno-<warning>" << endl
       << "          Disable the specified warning." << endl
       << "  -w" << endl
       << "          Disable all diagnostics. -w wins -Weverything" << endl
       << "  -Weverything" << endl
       << "          Enable all diagnostics." << endl
       << "  --help" << endl
       << "          Show this help." << endl
       << endl
       << "INPUT:" << endl
       << "  An AIDL file." << endl
       << endl
       << "OUTPUT:" << endl
       << "  Path to the generated Java or C++ source file. This is ignored when" << endl
       << "  -o or --out is specified or the number of the input files are" << endl
       << "  more than one." << endl
       << "  For Java, if omitted, Java source file is generated at the same" << endl
       << "  place as the input AIDL file," << endl
       << endl
       << "HEADER_DIR:" << endl
       << "  Path to where C++ headers are generated." << endl;
  return sstr.str();
}

string to_string(Options::Language language) {
  switch (language) {
    case Options::Language::CPP:
      return "cpp";
    case Options::Language::JAVA:
      return "java";
    case Options::Language::NDK:
      return "ndk";
    case Options::Language::RUST:
      return "rust";
    case Options::Language::CPP_ANALYZER:
      return "cpp-analyzer";
    case Options::Language::UNSPECIFIED:
      return "unspecified";
    default:
      AIDL_FATAL(AIDL_LOCATION_HERE)
          << "Unexpected Options::Language enumerator: " << static_cast<size_t>(language);
  }
}

bool Options::StabilityFromString(const std::string& stability, Stability* out_stability) {
  if (stability == "vintf") {
    *out_stability = Stability::VINTF;
    return true;
  }
  return false;
}

static const std::map<std::string, uint32_t> codeNameToVersion = {
    {"S", 31},
    {"Tiramisu", SDK_VERSION_Tiramisu},
    {"UpsideDownCake", SDK_VERSION_UpsideDownCake},
    {"VanillaIceCream", SDK_VERSION_current},
    // this is an alias for the latest in-development platform version
    {"current", SDK_VERSION_current},
    // this is an alias for use of all APIs, including those not in any API surface
    {"platform_apis", 10001},
};

Result<uint32_t> MinSdkVersionFromString(const std::string& str) {
  uint32_t num;
  if (!android::base::ParseUint(str, &num, 10000u /* max */)) {
    if (auto found = codeNameToVersion.find(str); found != codeNameToVersion.end()) {
      return found->second;
    }
    return Errorf("Invalid SDK version: {}", str);
  }
  return num;
}

static uint32_t DefaultMinSdkVersionForLang(const Options::Language lang) {
  switch (lang) {
    case Options::Language::CPP:
      return DEFAULT_SDK_VERSION_CPP;
    case Options::Language::JAVA:
      return DEFAULT_SDK_VERSION_JAVA;
    case Options::Language::NDK:
      return DEFAULT_SDK_VERSION_NDK;
    case Options::Language::RUST:
      return DEFAULT_SDK_VERSION_RUST;
    case Options::Language::CPP_ANALYZER:
      return DEFAULT_SDK_VERSION_CPP;
    case Options::Language::UNSPECIFIED:
      return DEFAULT_SDK_VERSION_JAVA;  // The safest option
    default:
      AIDL_FATAL(AIDL_LOCATION_HERE)
          << "Unexpected Options::Language enumerator: " << static_cast<size_t>(lang);
  }
}

Options Options::From(const string& cmdline) {
  vector<string> args = Split(cmdline, " ");
  return From(args);
}

Options Options::From(const vector<string>& args) {
  Options::Language lang = Options::Language::JAVA;
  int argc = args.size();
  if (argc >= 1 && args.at(0) == "aidl-cpp") {
    lang = Options::Language::CPP;
  }
  const char* argv[argc + 1];
  for (int i = 0; i < argc; i++) {
    argv[i] = args.at(i).c_str();
  }
  argv[argc] = nullptr;

  return Options(argc, argv, lang);
}

static std::string ComputeRawArgs(int argc, const char* const raw_argv[]) {
  std::vector<std::string> args;
  for (int i = 0; i < argc; i++) {
    // First pass. This is mostly for devs to understand where files come from, and
    // there may be more complicated rules, but we can at least do better than the
    // typical paste that would break args with spaces in them.
    args.push_back(StringReplace(raw_argv[i], " ", "\\ ", true));
  }

  return Join(args, " ");
}

static std::string ToCanonicalDirectory(const std::string& optarg) {
  std::string dir = Trim(optarg);
  if (!dir.empty() && dir.back() != OS_PATH_SEPARATOR) {
    dir.push_back(OS_PATH_SEPARATOR);
  }
  return dir;
}

Options Options::PlusImportDir(const std::string& import_dir) const {
  Options copy(*this);
  copy.import_dirs_.insert(ToCanonicalDirectory(import_dir));
  return copy;
}

Options::Options(int argc, const char* const raw_argv[], Options::Language default_lang)
    : myname_(argc >= 1 ? raw_argv[0] : "aidl"),
      raw_args_(ComputeRawArgs(argc, raw_argv)),
      language_(default_lang) {
  std::vector<const char*> argv = warning_options_.Parse(argc, raw_argv, error_message_);
  if (!Ok()) return;

  argc = argv.size();
  argv.push_back(nullptr);

  bool lang_option_found = false;
  optind = 0;
  while (true) {
    static struct option long_options[] = {
        {"lang", required_argument, 0, 'l'},
        {"preprocess", no_argument, 0, 's'},
        {"dumpapi", no_argument, 0, 'u'},
        {"no_license", no_argument, 0, 'x'},
        {"checkapi", optional_argument, 0, 'A'},
        {"apimapping", required_argument, 0, 'i'},
        {"include", required_argument, 0, 'I'},
        {"next_include", required_argument, 0, 'N'},
        {"preprocessed", required_argument, 0, 'p'},
        {"dep", required_argument, 0, 'd'},
        {"out", required_argument, 0, 'o'},
        {"header_out", required_argument, 0, 'h'},
        {"ninja", no_argument, 0, 'n'},
        {"rpc", no_argument, 0, 'r'},
        {"stability", required_argument, 0, 'Y'},
        {"omit_invocation", no_argument, 0, 'O'},
        {"min_sdk_version", required_argument, 0, 'm'},
        {"structured", no_argument, 0, 'S'},
        {"trace", no_argument, 0, 't'},
        {"transaction_names", no_argument, 0, 'c'},
        {"previous_api_dir", required_argument, 0, 'f'},
        {"version", required_argument, 0, 'v'},
        {"log", no_argument, 0, 'L'},
        {"hash", required_argument, 0, 'H'},
        {"previous_hash", required_argument, 0, 'P'},
        {"help", no_argument, 0, 'e'},
        {0, 0, 0, 0},
    };
    const int c = getopt_long(argc, const_cast<char* const*>(argv.data()),
                              "I:N:p:d:o:h:abtv:i:", long_options, nullptr);
    if (c == -1) {
      // no more options
      break;
    }
    switch (c) {
      case 'l':
        if (language_ == Options::Language::CPP) {
          // aidl-cpp can't set language. aidl-cpp exists only for backwards
          // compatibility.
          error_message_ << "aidl-cpp does not support --lang." << endl;
          return;
        } else {
          lang_option_found = true;
          string lang = Trim(optarg);
          if (lang == "java") {
            language_ = Options::Language::JAVA;
            task_ = Options::Task::COMPILE;
          } else if (lang == "cpp") {
            language_ = Options::Language::CPP;
            task_ = Options::Task::COMPILE;
          } else if (lang == "ndk") {
            language_ = Options::Language::NDK;
            task_ = Options::Task::COMPILE;
          } else if (lang == "rust") {
            language_ = Options::Language::RUST;
            task_ = Options::Task::COMPILE;
          } else if (lang == "cpp-analyzer") {
            language_ = Options::Language::CPP_ANALYZER;
            task_ = Options::Task::COMPILE;
          } else {
            error_message_ << "Unsupported language: '" << lang << "'" << endl;
            return;
          }
        }
        break;
      case 's':
        task_ = Options::Task::PREPROCESS;
        break;
      case 'u':
        task_ = Options::Task::DUMP_API;
        break;
      case 'x':
        dump_no_license_ = true;
        break;
      case 'A':
        task_ = Options::Task::CHECK_API;
        // to ensure that all parcelables in the api dumpes are structured
        structured_ = true;
        if (optarg) {
          if (strcmp(optarg, "compatible") == 0)
            check_api_level_ = CheckApiLevel::COMPATIBLE;
          else if (strcmp(optarg, "equal") == 0)
            check_api_level_ = CheckApiLevel::EQUAL;
          else {
            error_message_ << "Unsupported --checkapi level: '" << optarg << "'" << endl;
            return;
          }
        }
        break;
      case 'I': {
        // imports for dependencies
        import_dirs_.emplace(ToCanonicalDirectory(optarg));
        previous_import_dirs_.emplace(ToCanonicalDirectory(optarg));
        break;
      }
      case 'p':
        preprocessed_files_.emplace_back(Trim(optarg));
        break;
      case 'd':
        dependency_file_ = Trim(optarg);
        break;
      case 'o':
        output_dir_ = ToCanonicalDirectory(optarg);
        break;
      case 'N':
        import_dirs_.emplace(ToCanonicalDirectory(optarg));
        break;
      case 'f':
        previous_api_dir_ = ToCanonicalDirectory(optarg);
        previous_import_dirs_.emplace(ToCanonicalDirectory(optarg));
        break;
      case 'O':
        raw_args_ = "cmd not shown due to `--omit_invocation`";
        break;
      case 'h':
        output_header_dir_ = ToCanonicalDirectory(optarg);
        break;
      case 'n':
        dependency_file_ninja_ = true;
        break;
      case 'S':
        structured_ = true;
        break;
      case 'Y': {
        const string stability_str = Trim(optarg);
        if (!StabilityFromString(stability_str, &stability_)) {
          error_message_ << "Unrecognized stability level: '" << stability_str
                         << "'. Must be vintf." << endl;
          return;
        }
        break;
      }
      case 'm':
        if (auto ret = MinSdkVersionFromString(Trim(optarg)); ret.ok()) {
          min_sdk_version_ = *ret;
        } else {
          error_message_ << ret.error();
          return;
        }
        break;
      case 'r':
        gen_rpc_ = true;
        break;
      case 't':
        gen_traces_ = true;
        break;
      case 'a':
        auto_dep_file_ = true;
        break;
      case 'b':
        fail_on_parcelable_ = true;
        break;
      case 'c':
        gen_transaction_names_ = true;
        break;
      case 'v': {
        const string ver_str = Trim(optarg);
        int ver = atoi(ver_str.c_str());
        if (ver > 0) {
          version_ = ver;
        } else {
          error_message_ << "Invalid version number: '" << ver_str << "'. "
                         << "Version must be a positive natural number." << endl;
          return;
        }
        break;
      }
      case 'H':
        hash_ = Trim(optarg);
        break;
      case 'P':
        previous_hash_ = Trim(optarg);
        break;
      case 'L':
        gen_log_ = true;
        break;
      case 'e':
        std::cerr << GetUsage();
        task_ = Task::HELP;
        CHECK(Ok());
        return;
      case 'i':
        output_file_ = Trim(optarg);
        task_ = Task::DUMP_MAPPINGS;
        break;
      default:
        error_message_ << GetUsage();
        CHECK(!Ok());
        return;
    }
  }  // while

  // Positional arguments
  if (!lang_option_found && task_ == Options::Task::COMPILE) {
    // the legacy arguments format
    if (argc - optind <= 0) {
      error_message_ << "No input file" << endl;
      return;
    }
    if (language_ == Options::Language::JAVA || language_ == Options::Language::RUST) {
      input_files_.emplace_back(argv[optind++]);
      if (argc - optind >= 1) {
        output_file_ = argv[optind++];
      } else if (output_dir_.empty()) {
        // when output is omitted and -o option isn't set, the output is by
        // default set to the input file path with .aidl is replaced to .java.
        // If -o option is set, the output path is calculated by
        // GetOutputFilePath which returns "<output_dir>/<package/name>/
        // <typename>.java"
        output_file_ = input_files_.front();
        if (android::base::EndsWith(output_file_, ".aidl")) {
          output_file_ = output_file_.substr(0, output_file_.length() - strlen(".aidl"));
        }
        output_file_ += (language_ == Options::Language::JAVA) ? ".java" : ".rs";
      }
    } else if (IsCppOutput()) {
      input_files_.emplace_back(argv[optind++]);
      if (argc - optind < 2) {
        error_message_ << "No HEADER_DIR or OUTPUT." << endl;
        return;
      }
      output_header_dir_ = ToCanonicalDirectory(argv[optind++]);
      output_file_ = argv[optind++];
    }
    if (argc - optind > 0) {
      error_message_ << "Too many arguments: ";
      for (int i = optind; i < argc; i++) {
        error_message_ << " " << argv[i];
      }
      error_message_ << endl;
    }
  } else {
    // the new arguments format
    if (task_ == Options::Task::COMPILE || task_ == Options::Task::DUMP_API ||
        task_ == Options::Task::DUMP_MAPPINGS) {
      if (argc - optind < 1) {
        error_message_ << "No input file." << endl;
        return;
      }
    } else {
      if (argc - optind < 2) {
        error_message_ << "Insufficient arguments. At least 2 required, but "
                       << "got " << (argc - optind) << "." << endl;
        return;
      }
      if (task_ != Options::Task::CHECK_API) {
        output_file_ = argv[optind++];
      }
    }
    while (optind < argc) {
      input_files_.emplace_back(argv[optind++]);
    }
  }

  // filter out invalid combinations
  if (lang_option_found) {
    if (IsCppOutput() && task_ == Options::Task::COMPILE) {
      if (output_dir_.empty()) {
        error_message_ << "Output directory is not set. Set with --out." << endl;
        return;
      }
      if (output_header_dir_.empty()) {
        error_message_ << "Header output directory is not set. Set with "
                       << "--header_out." << endl;
        return;
      }
    }
    if (language_ == Options::Language::JAVA && task_ == Options::Task::COMPILE) {
      if (output_dir_.empty()) {
        error_message_ << "Output directory is not set. Set with --out." << endl;
        return;
      }
      if (!output_header_dir_.empty()) {
        error_message_ << "Header output directory is set, which does not make "
                       << "sense for Java." << endl;
        return;
      }
    }
    if (language_ == Options::Language::RUST && task_ == Options::Task::COMPILE) {
      if (output_dir_.empty()) {
        error_message_ << "Output directory is not set. Set with --out." << endl;
        return;
      }
      if (!output_header_dir_.empty()) {
        error_message_ << "Header output directory is set, which does not make "
                       << "sense for Rust." << endl;
        return;
      }
    }
  }
  if (!previous_api_dir_.empty()) {
    if (previous_hash_.empty()) {
      error_message_ << "--previous_hash must be set if --previous_api_dir is set" << endl;
      return;
    }
  } else {
    if (!previous_hash_.empty()) {
      error_message_ << "--previous_hash must not be set if --previous_api_dir is not set" << endl;
      return;
    }
  }

  if (task_ == Options::Task::COMPILE) {
    for (const string& input : input_files_) {
      if (!android::base::EndsWith(input, ".aidl")) {
        error_message_ << "Expected .aidl file for input but got '" << input << "'" << endl;
        return;
      }
    }
    if (!output_file_.empty() && input_files_.size() > 1) {
      error_message_ << "Multiple AIDL files can't be compiled to a single "
                     << "output file '" << output_file_ << "'. "
                     << "Use --out=DIR instead for output files." << endl;
      return;
    }
    if (!dependency_file_.empty() && input_files_.size() > 1) {
      error_message_ << "-d or --dep doesn't work when compiling multiple AIDL "
                     << "files. Use '-a' to generate dependency file next to "
                     << "the output file with the name based on the input "
                     << "file." << endl;
      return;
    }
    if (gen_log_ && (language_ != Options::Language::CPP && language_ != Options::Language::NDK)) {
      error_message_ << "--log is currently supported for either --lang=cpp or --lang=ndk" << endl;
      return;
    }
  }
  if (task_ == Options::Task::PREPROCESS) {
    if (version_ > 0) {
      error_message_ << "--version should not be used with '--preprocess'." << endl;
      return;
    }
  }
  if (task_ == Options::Task::CHECK_API) {
    if (input_files_.size() != 2) {
      error_message_ << "--checkapi requires two inputs for comparing, "
                     << "but got " << input_files_.size() << "." << endl;
      return;
    }
  }
  if (task_ == Options::Task::DUMP_API) {
    if (output_dir_.empty()) {
      error_message_ << "--dumpapi requires output directory. Use --out." << endl;
      return;
    }
  }
  if (task_ != Options::Task::COMPILE) {
    if (min_sdk_version_ != 0) {
      error_message_ << "--min_sdk_version is available only for compilation." << endl;
      return;
    }
    // For other tasks, use "current"
    min_sdk_version_ = MinSdkVersionFromString("current").value();
  }

  uint32_t default_ver = DefaultMinSdkVersionForLang(language_);
  if (min_sdk_version_ == 0) {  // --min_sdk_version flag not specified
    min_sdk_version_ = default_ver;
  } else if (min_sdk_version_ < default_ver) {
    error_message_ << "Min SDK version should at least be " << default_ver << "." << endl;
    return;
  }

  uint32_t rpc_version = MinSdkVersionFromString("Tiramisu").value();
  // note: we would like to always generate (Java) code to support RPC out of
  // the box, but doing so causes an unclear error for people trying to use RPC
  // - now we require them to add the gen_rpc build rule and get this clear message.
  if (gen_rpc_ && min_sdk_version_ < rpc_version) {
    error_message_ << "RPC code requires minimum SDK version of at least " << rpc_version << endl;
    return;
  }

  if (min_sdk_version_ >= rpc_version) gen_rpc_ = true;

  AIDL_FATAL_IF(!output_dir_.empty() && output_dir_.back() != OS_PATH_SEPARATOR, output_dir_);
  AIDL_FATAL_IF(!output_header_dir_.empty() && output_header_dir_.back() != OS_PATH_SEPARATOR,
                output_header_dir_);
}

std::vector<const char*> WarningOptions::Parse(int argc, const char* const raw_argv[],
                                               ErrorMessage& error_message) {
  std::vector<const char*> remains;
  for (int i = 0; i < argc; i++) {
    auto arg = raw_argv[i];
    if (strcmp(arg, "-Weverything") == 0) {
      enable_all_ = true;
    } else if (strcmp(arg, "-Werror") == 0) {
      as_errors_ = true;
    } else if (strcmp(arg, "-w") == 0) {
      disable_all_ = true;
    } else if (base::StartsWith(arg, "-Wno-error=")) {
      no_errors_.insert(arg + strlen("-Wno-error="));
    } else if (base::StartsWith(arg, "-Wno-")) {
      disabled_.insert(arg + strlen("-Wno-"));
    } else if (base::StartsWith(arg, "-W")) {
      enabled_.insert(arg + strlen("-W"));
    } else {
      remains.push_back(arg);
    }
  }

  for (const auto& names : {no_errors_, disabled_, enabled_}) {
    for (const auto& name : names) {
      if (kAllDiagnostics.count(name) == 0) {
        error_message << "unknown warning: " << name << "\n";
        return {};
      }
    }
  }

  return remains;
}

DiagnosticMapping WarningOptions::GetDiagnosticMapping() const {
  DiagnosticMapping mapping;
  for (const auto& [_, d] : kAllDiagnostics) {
    bool enabled = d.default_enabled;
    if (enable_all_ || enabled_.find(d.name) != enabled_.end()) {
      enabled = true;
    }
    if (disable_all_ || disabled_.find(d.name) != disabled_.end()) {
      enabled = false;
    }

    DiagnosticSeverity severity = DiagnosticSeverity::DISABLED;
    if (enabled) {
      severity = DiagnosticSeverity::WARNING;
      if (as_errors_ && no_errors_.find(d.name) == no_errors_.end()) {
        severity = DiagnosticSeverity::ERROR;
      }
    }
    mapping.Severity(d.id, severity);
  }
  return mapping;
}

}  // namespace aidl
}  // namespace android
