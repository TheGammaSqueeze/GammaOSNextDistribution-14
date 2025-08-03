/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ART_OATDUMP_OATDUMP_TEST_H_
#define ART_OATDUMP_OATDUMP_TEST_H_

#include <sys/types.h>
#include <unistd.h>

#include <memory>
#include <sstream>
#include <string>
#include <type_traits>
#include <vector>

#include "arch/instruction_set.h"
#include "base/common_art_test.h"
#include "base/file_utils.h"
#include "base/os.h"
#include "common_runtime_test.h"
#include "gtest/gtest.h"

namespace art {

// Linking flavor.
enum class Flavor {
  kDynamic,  // oatdump(d), dex2oat(d)
  kStatic,   // oatdump(d)s, dex2oat(d)s
};

class OatDumpTest : public CommonRuntimeTest, public testing::WithParamInterface<Flavor> {
 protected:
  virtual void SetUp() {
    CommonRuntimeTest::SetUp();
    core_art_location_ = GetCoreArtLocation();
    core_oat_location_ = GetSystemImageFilename(GetCoreOatLocation().c_str(), kRuntimeISA);
    tmp_dir_ = GetScratchDir();
    if (GetParam() == Flavor::kStatic) {
      TEST_DISABLED_FOR_NON_STATIC_HOST_BUILDS();
    }

    // Prevent boot image inference to ensure consistent test behavior.
    unset_bootclasspath_ = std::make_unique<ScopedUnsetEnvironmentVariable>("BOOTCLASSPATH");
  }

  virtual void TearDown() {
    unset_bootclasspath_.reset();
    ClearDirectory(tmp_dir_.c_str(), /*recursive*/ false);
    ASSERT_EQ(rmdir(tmp_dir_.c_str()), 0);
    CommonRuntimeTest::TearDown();
  }

  std::string GetScratchDir() const {
    // ANDROID_DATA needs to be set
    CHECK_NE(static_cast<char*>(nullptr), getenv("ANDROID_DATA"));
    std::string dir = getenv("ANDROID_DATA");
    dir += "/oatdump-tmp-dir-XXXXXX";
    if (mkdtemp(&dir[0]) == nullptr) {
      PLOG(FATAL) << "mkdtemp(\"" << &dir[0] << "\") failed";
    }
    return dir;
  }

  // Returns path to the oatdump/dex2oat/dexdump binary.
  static std::string GetExecutableFilePath(const char* name,
                                           bool is_debug,
                                           bool is_static,
                                           bool bitness) {
    std::string path = GetArtBinDir() + '/' + name;
    if (is_debug) {
      path += 'd';
    }
    if (is_static) {
      path += 's';
    }
    if (bitness) {
      path += Is64BitInstructionSet(kRuntimeISA) ? "64" : "32";
    }
    return path;
  }

  static std::string GetExecutableFilePath(Flavor flavor, const char* name, bool bitness) {
    return GetExecutableFilePath(name, kIsDebugBuild, flavor == Flavor::kStatic, bitness);
  }

  enum Args {
    kArgImage = 1 << 0,      // --image=<boot-image>
    kArgAppImage = 1 << 1,   // --app-image=<app-image>
    kArgOatBcp = 1 << 2,     // --oat-file=<bcp-oat-file>
    kArgDexBcp = 1 << 3,     // --dex-file=<bcp-dex-file>
    kArgOatApp = 1 << 4,     // --oat-file=<app-oat-file>
    kArgSymbolize = 1 << 5,  // --symbolize=<bcp-oat-file>
    kArgDexApp = 1 << 6,     // --dex-file=<app-dex-file>

    // Runtime args.
    kArgBcp = 1 << 16,        // --runtime-arg -Xbootclasspath:<bcp>
    kArgBootImage = 1 << 17,  // --boot-image=<boot-image>
    kArgIsa = 1 << 18,        // --instruction-set=<isa>
  };

  enum Expects {
    kExpectImage = 1 << 0,
    kExpectOat = 1 << 1,
    kExpectCode = 1 << 2,
    kExpectBssMappingsForBcp = 1 << 3,
    kExpectBssOffsetsForBcp = 1 << 4,
  };

  static std::string GetAppBaseName() {
    // Use ProfileTestMultiDex as it contains references to boot image strings
    // that shall use different code for PIC and non-PIC.
    return "ProfileTestMultiDex";
  }

  std::string GetAppImageName() const { return tmp_dir_ + "/" + GetAppBaseName() + ".art"; }

  std::string GetAppOdexName() const { return tmp_dir_ + "/" + GetAppBaseName() + ".odex"; }

  ::testing::AssertionResult GenerateAppOdexFile(Flavor flavor,
                                                 const std::vector<std::string>& args = {}) const {
    std::string dex2oat_path =
        GetExecutableFilePath(flavor, "dex2oat", /* bitness= */ kIsTargetBuild);
    std::vector<std::string> exec_argv = {
        dex2oat_path,
        "--runtime-arg",
        "-Xms64m",
        "--runtime-arg",
        "-Xmx64m",
        "--runtime-arg",
        "-Xnorelocate",
        "--runtime-arg",
        GetClassPathOption("-Xbootclasspath:", GetLibCoreDexFileNames()),
        "--runtime-arg",
        GetClassPathOption("-Xbootclasspath-locations:", GetLibCoreDexLocations()),
        "--boot-image=" + GetCoreArtLocation(),
        "--instruction-set=" + std::string(GetInstructionSetString(kRuntimeISA)),
        "--dex-file=" + GetTestDexFileName(GetAppBaseName().c_str()),
        "--oat-file=" + GetAppOdexName(),
        "--compiler-filter=speed",
    };
    exec_argv.insert(exec_argv.end(), args.begin(), args.end());

    auto post_fork_fn = []() {
      setpgid(0, 0);  // Change process groups, so we don't get reaped by ProcessManager.
                      // Ignore setpgid errors.
      return setenv("ANDROID_LOG_TAGS", "*:e", 1) == 0;  // We're only interested in errors and
                                                         // fatal logs.
    };

    std::string error_msg;
    ForkAndExecResult res = ForkAndExec(exec_argv, post_fork_fn, &error_msg);
    if (res.stage != ForkAndExecResult::kFinished) {
      return ::testing::AssertionFailure() << strerror(errno);
    }
    return res.StandardSuccess() ? ::testing::AssertionSuccess()
                                 : (::testing::AssertionFailure() << error_msg);
  }

  // Run the test with custom arguments.
  ::testing::AssertionResult Exec(Flavor flavor,
                                  std::underlying_type_t<Args> args,
                                  const std::vector<std::string>& extra_args,
                                  std::underlying_type_t<Expects> expects,
                                  bool expect_failure = false) const {
    std::string file_path = GetExecutableFilePath(flavor, "oatdump", /* bitness= */ false);

    if (!OS::FileExists(file_path.c_str())) {
      return ::testing::AssertionFailure() << file_path << " should be a valid file path";
    }

    std::vector<std::string> expected_prefixes;
    if ((expects & kExpectImage) != 0) {
      expected_prefixes.push_back("IMAGE LOCATION:");
      expected_prefixes.push_back("IMAGE BEGIN:");
      expected_prefixes.push_back("kDexCaches:");
    }
    if ((expects & kExpectOat) != 0) {
      expected_prefixes.push_back("LOCATION:");
      expected_prefixes.push_back("MAGIC:");
      expected_prefixes.push_back("DEX FILE COUNT:");
    }
    if ((expects & kExpectCode) != 0) {
      // Code and dex code do not show up if list only.
      expected_prefixes.push_back("DEX CODE:");
      expected_prefixes.push_back("CODE:");
      expected_prefixes.push_back("StackMap");
    }
    if ((expects & kExpectBssMappingsForBcp) != 0) {
      expected_prefixes.push_back("Entries for BCP DexFile");
    }
    if ((expects & kExpectBssOffsetsForBcp) != 0) {
      expected_prefixes.push_back("Offsets for BCP DexFile");
    }

    std::vector<std::string> exec_argv = {file_path};
    if ((args & kArgSymbolize) != 0) {
      exec_argv.push_back("--symbolize=" + core_oat_location_);
      exec_argv.push_back("--output=" + core_oat_location_ + ".symbolize");
    }
    if ((args & kArgBcp) != 0) {
      exec_argv.push_back("--runtime-arg");
      exec_argv.push_back(GetClassPathOption("-Xbootclasspath:", GetLibCoreDexFileNames()));
      exec_argv.push_back("--runtime-arg");
      exec_argv.push_back(
          GetClassPathOption("-Xbootclasspath-locations:", GetLibCoreDexLocations()));
    }
    if ((args & kArgIsa) != 0) {
      exec_argv.push_back("--instruction-set=" + std::string(GetInstructionSetString(kRuntimeISA)));
    }
    if ((args & kArgBootImage) != 0) {
      exec_argv.push_back("--boot-image=" + GetCoreArtLocation());
    }
    if ((args & kArgImage) != 0) {
      exec_argv.push_back("--image=" + GetCoreArtLocation());
    }
    if ((args & kArgAppImage) != 0) {
      exec_argv.push_back("--app-image=" + GetAppImageName());
    }
    if ((args & kArgOatBcp) != 0) {
      exec_argv.push_back("--oat-file=" + core_oat_location_);
    }
    if ((args & kArgDexBcp) != 0) {
      exec_argv.push_back("--dex-file=" + GetLibCoreDexFileNames()[0]);
    }
    if ((args & kArgOatApp) != 0) {
      exec_argv.push_back("--oat-file=" + GetAppOdexName());
    }
    if ((args & kArgDexApp) != 0) {
      exec_argv.push_back("--dex-file=" + GetTestDexFileName(GetAppBaseName().c_str()));
    }
    exec_argv.insert(exec_argv.end(), extra_args.begin(), extra_args.end());

    std::vector<bool> found(expected_prefixes.size(), false);
    auto line_handle_fn = [&found, &expected_prefixes](const char* line, size_t line_len) {
      if (line_len == 0) {
        return;
      }
      // Check contents.
      for (size_t i = 0; i < expected_prefixes.size(); ++i) {
        const std::string& expected = expected_prefixes[i];
        if (!found[i] &&
            line_len >= expected.length() &&
            memcmp(line, expected.c_str(), expected.length()) == 0) {
          found[i] = true;
        }
      }
    };

    static constexpr size_t kLineMax = 256;
    char line[kLineMax] = {};
    size_t line_len = 0;
    size_t total = 0;
    bool ignore_next_line = false;
    std::vector<char> error_buf;  // Buffer for debug output on error. Limited to 1M.
    auto line_buf_fn = [&](char* buf, size_t len) {
      total += len;

      if (len == 0 && line_len > 0 && !ignore_next_line) {
        // Everything done, handle leftovers.
        line_handle_fn(line, line_len);
      }

      if (len > 0) {
        size_t pos = error_buf.size();
        if (pos < MB) {
          error_buf.insert(error_buf.end(), buf, buf + len);
        }
      }

      while (len > 0) {
        // Copy buf into the free tail of the line buffer, and move input buffer along.
        size_t copy = std::min(kLineMax - line_len, len);
        memcpy(&line[line_len], buf, copy);
        buf += copy;
        len -= copy;

        // Skip spaces up to len, return count of removed spaces. Declare a lambda for reuse.
        auto trim_space = [&line](size_t len) {
          size_t spaces = 0;
          for (; spaces < len && isspace(line[spaces]); ++spaces) {}
          if (spaces > 0) {
            memmove(&line[0], &line[spaces], len - spaces);
          }
          return spaces;
        };
        // There can only be spaces if we freshly started a line.
        if (line_len == 0) {
          copy -= trim_space(copy);
        }

        // Scan for newline characters.
        size_t index = line_len;
        line_len += copy;
        while (index < line_len) {
          if (line[index] == '\n') {
            // Handle line.
            if (!ignore_next_line) {
              line_handle_fn(line, index);
            }
            // Move the rest to the front, but trim leading spaces.
            line_len -= index + 1;
            memmove(&line[0], &line[index + 1], line_len);
            line_len -= trim_space(line_len);
            index = 0;
            ignore_next_line = false;
          } else {
            index++;
          }
        }

        // Handle a full line without newline characters. Ignore the "next" line, as it is the
        // tail end of this.
        if (line_len == kLineMax) {
          if (!ignore_next_line) {
            line_handle_fn(line, kLineMax);
          }
          line_len = 0;
          ignore_next_line = true;
        }
      }
    };

    auto post_fork_fn = []() {
      setpgid(0, 0);  // Change process groups, so we don't get reaped by ProcessManager.
                      // Ignore setpgid failures.
      return setenv("ANDROID_LOG_TAGS", "*:e", 1) == 0;  // We're only interested in errors and
                                                         // fatal logs.
    };

    ForkAndExecResult res = ForkAndExec(exec_argv, post_fork_fn, line_buf_fn);
    if (res.stage != ForkAndExecResult::kFinished) {
      return ::testing::AssertionFailure() << strerror(errno);
    }
    error_buf.push_back(0);  // Make data a C string.

    if (!res.StandardSuccess()) {
      if (expect_failure && WIFEXITED(res.status_code)) {
        // Avoid crash as valid exit.
        return ::testing::AssertionSuccess();
      }
      std::ostringstream cmd;
      std::copy(exec_argv.begin(), exec_argv.end(), std::ostream_iterator<std::string>(cmd, " "));
      LOG(ERROR) << "Output: " << error_buf.data();  // Output first as it might be extremely  long.
      LOG(ERROR) << "Failed command: " << cmd.str();  // Useful to reproduce the failure separately.
      return ::testing::AssertionFailure() << "Did not terminate successfully: " << res.status_code;
    } else if (expect_failure) {
      return ::testing::AssertionFailure() << "Expected failure";
    }

    if ((args & kArgSymbolize) != 0) {
      EXPECT_EQ(total, 0u);
    } else {
      EXPECT_GT(total, 0u);
    }

    bool result = true;
    std::ostringstream oss;
    for (size_t i = 0; i < expected_prefixes.size(); ++i) {
      if (!found[i]) {
        oss << "Did not find prefix " << expected_prefixes[i] << std::endl;
        result = false;
      }
    }
    if (!result) {
      oss << "Processed bytes " << total << ":" << std::endl;
    }

    return result ? ::testing::AssertionSuccess()
                  : (::testing::AssertionFailure() << oss.str() << error_buf.data());
  }

  std::string tmp_dir_;

 private:
  std::string core_art_location_;
  std::string core_oat_location_;
  std::unique_ptr<ScopedUnsetEnvironmentVariable> unset_bootclasspath_;
};

}  // namespace art

#endif  // ART_OATDUMP_OATDUMP_TEST_H_
