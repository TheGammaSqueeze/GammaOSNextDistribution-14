/*
 * Copyright (C) 2021 The Android Open Source Project
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
#define LOG_TAG "apex_shared_libraries_test"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/scopeguard.h>
#include <android-base/strings.h>
#include <android/dlext.h>
#include <dlfcn.h>
#include <fstab/fstab.h>
#include <gtest/gtest.h>
#include <link.h>

#include <filesystem>
#include <fstream>
#include <regex>
#include <sstream>
#include <string>

using android::base::GetBoolProperty;
using android::base::Split;
using android::base::StartsWith;
using android::fs_mgr::Fstab;
using android::fs_mgr::ReadFstabFromFile;

namespace fs = std::filesystem;

// No header available for these symbols
extern "C" struct android_namespace_t* android_get_exported_namespace(
    const char* name);

extern "C" struct android_namespace_t* android_create_namespace(
    const char* name, const char* ld_library_path,
    const char* default_library_path, uint64_t type,
    const char* permitted_when_isolated_path,
    struct android_namespace_t* parent);

#if !defined(__LP64__)
static constexpr const char LIB[] = "lib";
#else   // !__LP64__
static constexpr const char LIB[] = "lib64";
#endif  // !__LP64_

static constexpr const char kApexSharedLibsRoot[] = "/apex/sharedlibs";

// Before running the test, make sure that certain libraries are not pre-loaded
// in the test process.
void check_preloaded_libraries() {
  static constexpr const char* unwanted[] = {
      "libbase.so",
      "libcrypto.so",
  };

  std::ifstream f("/proc/self/maps");
  std::string line;
  while (std::getline(f, line)) {
    for (const char* lib : unwanted) {
      EXPECT_TRUE(line.find(lib) == std::string::npos)
          << "Library " << lib << " seems preloaded in the test process. "
          << "This is a potential error. Please remove direct or transitive "
          << "dependency to this library. You may debug this by running this "
          << "test with `export LD_DEBUG=1` and "
          << "`setprop debug.ld.all dlopen,dlerror`.";
    }
  }
}

TEST(apex_shared_libraries, symlink_libraries_loadable) {
  check_preloaded_libraries();

  Fstab fstab;
  ASSERT_TRUE(ReadFstabFromFile("/proc/mounts", &fstab));

  // Regex to use when checking if a mount is for an active APEX or not. Note
  // that non-active APEX mounts don't have the @<number> marker.
  std::regex active_apex_pattern(R"(/apex/(.*)@\d+)");

  // Traverse mount points to identify apexs.
  for (auto& entry : fstab) {
    std::cmatch m;
    if (!std::regex_match(entry.mount_point.c_str(), m, active_apex_pattern)) {
      continue;
    }
    // Linker namespace name of the apex com.android.foo is com_android_foo.
    std::string apex_namespace_name = m[1];
    std::replace(apex_namespace_name.begin(), apex_namespace_name.end(), '.',
                 '_');

    // Filter out any mount irrelevant (e.g. tmpfs)
    std::string dev_file = fs::path(entry.blk_device).filename();
    if (!StartsWith(dev_file, "loop") && !StartsWith(dev_file, "dm-")) {
      continue;
    }

    auto lib = fs::path(entry.mount_point) / LIB;
    if (!fs::is_directory(lib)) {
      continue;
    }

    for (auto& p : fs::directory_iterator(lib)) {
      std::error_code ec;
      if (!fs::is_symlink(p, ec)) {
        continue;
      }

      // We are only checking libraries pointing at a location inside
      // /apex/sharedlibs.
      auto target = fs::read_symlink(p.path(), ec);
      if (ec || !StartsWith(target.string(), kApexSharedLibsRoot)) {
        continue;
      }

      LOG(INFO) << "Checking " << p.path();

      // Symlink validity check.
      auto dest = fs::canonical(p.path(), ec);
      EXPECT_FALSE(ec) << "Failed to resolve " << p.path() << " (symlink to "
                       << target << "): " << ec;
      if (ec) {
        continue;
      }

      // Library loading validity check.
      dlerror();  // Clear any pending errors.
      android_namespace_t* ns =
          android_get_exported_namespace(apex_namespace_name.c_str());
      if (ns == nullptr) {
        LOG(INFO) << "Creating linker namespace " << apex_namespace_name;
        // In case the apex namespace doesn't exist (actually not accessible),
        // create a new one that can search libraries from the apex directory
        // and can load (but not search) from the shared lib APEX.
        std::string search_paths = lib;
        search_paths.push_back(':');
        // Adding "/system/lib[64]" is not ideal; we need to link to the
        // namespace that is capable of loading libs from the directory.
        // However, since the namespace (the `system` namespace) is not
        // exported, we can't make a link. Instead, we allow this new namespace
        // to search/load libraries from the directory.
        search_paths.append(std::string("/system/") + LIB);
        std::string permitted_paths = "/apex";
        ns = android_create_namespace(
            apex_namespace_name.c_str(),
            /* ld_library_path=*/nullptr,
            /* default_library_path=*/search_paths.c_str(),
            /* type=*/3,  // ISOLATED and SHARED
            /* permitted_when_isolated_path=*/permitted_paths.c_str(),
            /* parent=*/nullptr);
      }

      EXPECT_TRUE(ns != nullptr)
          << "Cannot find or create namespace " << apex_namespace_name;
      const android_dlextinfo dlextinfo = {
          .flags = ANDROID_DLEXT_USE_NAMESPACE,
          .library_namespace = ns,
      };

      void* handle = android_dlopen_ext(p.path().c_str(), RTLD_NOW, &dlextinfo);
      EXPECT_TRUE(handle != nullptr)
          << "Failed to load " << p.path() << " which is a symlink to "
          << target << ".\n"
          << "Reason: " << dlerror() << "\n"
          << "Make sure that the library is accessible.";
      if (handle == nullptr) {
        continue;
      }
      auto guard = android::base::make_scope_guard([&]() { dlclose(handle); });

      // Check that library is loaded and pointing to the realpath of the
      // library.
      auto dl_callback = [](dl_phdr_info* info, size_t /* size */, void* data) {
        auto dest = *static_cast<fs::path*>(data);
        if (info->dlpi_name == nullptr) {
          // This is linker imposing as libdl.so - skip it
          return 0;
        }
        int j;
        for (j = 0; j < info->dlpi_phnum; j++) {
          void* addr = (void*)(info->dlpi_addr + info->dlpi_phdr[j].p_vaddr);
          Dl_info dl_info;
          int rc = dladdr(addr, &dl_info);
          if (rc == 0) {
            continue;
          }
          if (dl_info.dli_fname) {
            auto libpath = fs::path(dl_info.dli_fname);
            if (libpath == dest) {
              // Library found!
              return 1;
            }
          }
        }

        return 0;
      };
      bool found = (dl_iterate_phdr(dl_callback, &dest) == 1);
      EXPECT_TRUE(found) << "Error verifying library symlink " << p.path()
                         << " which points to " << target
                         << " which resolves to file " << dest;
      if (found) {
        LOG(INFO) << "Verified that " << p.path()
                  << " correctly loads as library " << dest;
      }
    }
  }
}
