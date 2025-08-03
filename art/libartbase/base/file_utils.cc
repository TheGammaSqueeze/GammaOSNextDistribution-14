/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "file_utils.h"

#include <inttypes.h>
#include <sys/stat.h>
#include <sys/types.h>

#ifndef _WIN32
#include <sys/wait.h>
#endif
#include <unistd.h>

// We need dladdr.
#if !defined(__APPLE__) && !defined(_WIN32)
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#define DEFINED_GNU_SOURCE
#endif
#include <dlfcn.h>
#include <libgen.h>
#ifdef DEFINED_GNU_SOURCE
#undef _GNU_SOURCE
#undef DEFINED_GNU_SOURCE
#endif
#endif

#include <memory>
#include <sstream>
#include <vector>

#include "android-base/file.h"
#include "android-base/logging.h"
#include "android-base/properties.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "base/bit_utils.h"
#include "base/globals.h"
#include "base/os.h"
#include "base/stl_util.h"
#include "base/unix_file/fd_file.h"
#include "base/utils.h"

#if defined(__APPLE__)
#include <crt_externs.h>
#include <sys/syscall.h>

#include "AvailabilityMacros.h"  // For MAC_OS_X_VERSION_MAX_ALLOWED
#endif

#if defined(__linux__)
#include <linux/unistd.h>
#endif

#ifdef ART_TARGET_ANDROID
#include "android-modules-utils/sdk_level.h"
#endif

namespace art {

using android::base::GetBoolProperty;
using android::base::GetProperty;
using android::base::StringPrintf;

static constexpr const char* kClassesDex = "classes.dex";
static constexpr const char* kAndroidRootEnvVar = "ANDROID_ROOT";
static constexpr const char* kAndroidRootDefaultPath = "/system";
static constexpr const char* kAndroidSystemExtRootEnvVar = "SYSTEM_EXT_ROOT";
static constexpr const char* kAndroidSystemExtRootDefaultPath = "/system_ext";
static constexpr const char* kAndroidDataEnvVar = "ANDROID_DATA";
static constexpr const char* kAndroidDataDefaultPath = "/data";
static constexpr const char* kAndroidExpandEnvVar = "ANDROID_EXPAND";
static constexpr const char* kAndroidExpandDefaultPath = "/mnt/expand";
static constexpr const char* kAndroidArtRootEnvVar = "ANDROID_ART_ROOT";
static constexpr const char* kAndroidConscryptRootEnvVar = "ANDROID_CONSCRYPT_ROOT";
static constexpr const char* kAndroidI18nRootEnvVar = "ANDROID_I18N_ROOT";
static constexpr const char* kApexDefaultPath = "/apex/";
static constexpr const char* kArtApexDataEnvVar = "ART_APEX_DATA";
static constexpr const char* kBootImageStem = "boot";

// Get the "root" directory containing the "lib" directory where this instance
// of the libartbase library (which contains `GetRootContainingLibartbase`) is
// located:
// - on host this "root" is normally the Android Root (e.g. something like
//   "$ANDROID_BUILD_TOP/out/host/linux-x86/");
// - on target this "root" is normally the ART Root ("/apex/com.android.art").
// Return the empty string if that directory cannot be found or if this code is
// run on Windows or macOS.
static std::string GetRootContainingLibartbase() {
#if !defined(_WIN32) && !defined(__APPLE__)
  // Check where libartbase is from, and derive from there.
  Dl_info info;
  if (dladdr(reinterpret_cast<const void*>(&GetRootContainingLibartbase), /* out */ &info) != 0) {
    // Make a duplicate of the fname so dirname can modify it.
    UniqueCPtr<char> fname(strdup(info.dli_fname));

    char* dir1 = dirname(fname.get());  // This is the lib directory.
    char* dir2 = dirname(dir1);         // This is the "root" directory.
    if (OS::DirectoryExists(dir2)) {
      std::string tmp = dir2;  // Make a copy here so that fname can be released.
      return tmp;
    }
  }
#endif
  return "";
}

static const char* GetAndroidDirSafe(const char* env_var,
                                     const char* default_dir,
                                     bool must_exist,
                                     std::string* error_msg) {
  const char* android_dir = getenv(env_var);
  if (android_dir == nullptr) {
    if (!must_exist || OS::DirectoryExists(default_dir)) {
      android_dir = default_dir;
    } else {
      *error_msg = StringPrintf("%s not set and %s does not exist", env_var, default_dir);
      return nullptr;
    }
  }
  if (must_exist && !OS::DirectoryExists(android_dir)) {
    *error_msg = StringPrintf("Failed to find directory %s", android_dir);
    return nullptr;
  }
  return android_dir;
}

static const char* GetAndroidDir(const char* env_var,
                                 const char* default_dir,
                                 bool must_exist = true) {
  std::string error_msg;
  const char* dir = GetAndroidDirSafe(env_var, default_dir, must_exist, &error_msg);
  if (dir != nullptr) {
    return dir;
  } else {
    LOG(FATAL) << error_msg;
    UNREACHABLE();
  }
}

std::string GetAndroidRootSafe(std::string* error_msg) {
#ifdef _WIN32
  UNUSED(kAndroidRootEnvVar, kAndroidRootDefaultPath, GetRootContainingLibartbase);
  *error_msg = "GetAndroidRootSafe unsupported for Windows.";
  return "";
#else
  std::string local_error_msg;
  const char* dir = GetAndroidDirSafe(kAndroidRootEnvVar, kAndroidRootDefaultPath,
      /*must_exist=*/ true, &local_error_msg);
  if (dir == nullptr) {
    // On host, libartbase is currently installed in "$ANDROID_ROOT/lib"
    // (e.g. something like "$ANDROID_BUILD_TOP/out/host/linux-x86/lib". Use this
    // information to infer the location of the Android Root (on host only).
    //
    // Note that this could change in the future, if we decided to install ART
    // artifacts in a different location, e.g. within an "ART APEX" directory.
    if (!kIsTargetBuild) {
      std::string root_containing_libartbase = GetRootContainingLibartbase();
      if (!root_containing_libartbase.empty()) {
        return root_containing_libartbase;
      }
    }
    *error_msg = std::move(local_error_msg);
    return "";
  }

  return dir;
#endif
}

std::string GetAndroidRoot() {
  std::string error_msg;
  std::string ret = GetAndroidRootSafe(&error_msg);
  CHECK(!ret.empty()) << error_msg;
  return ret;
}

std::string GetSystemExtRootSafe(std::string* error_msg) {
#ifdef _WIN32
  UNUSED(kAndroidSystemExtRootEnvVar, kAndroidSystemExtRootDefaultPath);
  *error_msg = "GetSystemExtRootSafe unsupported for Windows.";
  return "";
#else
  const char* dir = GetAndroidDirSafe(kAndroidSystemExtRootEnvVar, kAndroidSystemExtRootDefaultPath,
      /*must_exist=*/ true, error_msg);
  return dir ? dir : "";
#endif
}

std::string GetSystemExtRoot() {
  std::string error_msg;
  std::string ret = GetSystemExtRootSafe(&error_msg);
  CHECK(!ret.empty()) << error_msg;
  return ret;
}

static std::string GetArtRootSafe(bool must_exist, /*out*/ std::string* error_msg) {
#ifdef _WIN32
  UNUSED(kAndroidArtRootEnvVar, kAndroidArtApexDefaultPath, GetRootContainingLibartbase);
  UNUSED(must_exist);
  *error_msg = "GetArtRootSafe unsupported for Windows.";
  return "";
#else
  // Prefer ANDROID_ART_ROOT if it's set.
  const char* android_art_root_from_env = getenv(kAndroidArtRootEnvVar);
  if (android_art_root_from_env != nullptr) {
    if (must_exist && !OS::DirectoryExists(android_art_root_from_env)) {
      *error_msg = StringPrintf(
          "Failed to find %s directory %s", kAndroidArtRootEnvVar, android_art_root_from_env);
      return "";
    }
    return android_art_root_from_env;
  }

  // On target, libartbase is normally installed in
  // "$ANDROID_ART_ROOT/lib(64)" (e.g. something like
  // "/apex/com.android.art/lib(64)". Use this information to infer the
  // location of the ART Root (on target only).
  if (kIsTargetBuild) {
    // *However*, a copy of libartbase may still be installed outside the
    // ART Root on some occasions, as ART target gtests install their binaries
    // and their dependencies under the Android Root, i.e. "/system" (see
    // b/129534335). For that reason, we cannot reliably use
    // `GetRootContainingLibartbase` to find the ART Root. (Note that this is
    // not really a problem in practice, as Android Q devices define
    // ANDROID_ART_ROOT in their default environment, and will instead use
    // the logic above anyway.)
    //
    // TODO(b/129534335): Re-enable this logic when the only instance of
    // libartbase on target is the one from the ART APEX.
    if ((false)) {
      std::string root_containing_libartbase = GetRootContainingLibartbase();
      if (!root_containing_libartbase.empty()) {
        return root_containing_libartbase;
      }
    }
  }

  // Try the default path.
  if (must_exist && !OS::DirectoryExists(kAndroidArtApexDefaultPath)) {
    *error_msg =
        StringPrintf("Failed to find default ART root directory %s", kAndroidArtApexDefaultPath);
    return "";
  }
  return kAndroidArtApexDefaultPath;
#endif
}

std::string GetArtRootSafe(std::string* error_msg) {
  return GetArtRootSafe(/* must_exist= */ true, error_msg);
}

std::string GetArtRoot() {
  std::string error_msg;
  std::string ret = GetArtRootSafe(&error_msg);
  if (ret.empty()) {
    LOG(FATAL) << error_msg;
    UNREACHABLE();
  }
  return ret;
}

std::string GetArtBinDir() {
  // Environment variable `ANDROID_ART_ROOT` is defined as
  // `$ANDROID_HOST_OUT/com.android.art` on host. However, host ART binaries are
  // still installed in `$ANDROID_HOST_OUT/bin` (i.e. outside the ART Root). The
  // situation is cleaner on target, where `ANDROID_ART_ROOT` is
  // `$ANDROID_ROOT/apex/com.android.art` and ART binaries are installed in
  // `$ANDROID_ROOT/apex/com.android.art/bin`.
  std::string android_art_root = kIsTargetBuild ? GetArtRoot() : GetAndroidRoot();
  return android_art_root + "/bin";
}

std::string GetAndroidDataSafe(std::string* error_msg) {
  const char* android_dir = GetAndroidDirSafe(kAndroidDataEnvVar,
                                              kAndroidDataDefaultPath,
                                              /* must_exist= */ true,
                                              error_msg);
  return (android_dir != nullptr) ? android_dir : "";
}

std::string GetAndroidData() { return GetAndroidDir(kAndroidDataEnvVar, kAndroidDataDefaultPath); }

std::string GetAndroidExpandSafe(std::string* error_msg) {
  const char* android_dir = GetAndroidDirSafe(kAndroidExpandEnvVar,
                                              kAndroidExpandDefaultPath,
                                              /*must_exist=*/true,
                                              error_msg);
  return (android_dir != nullptr) ? android_dir : "";
}

std::string GetAndroidExpand() {
  return GetAndroidDir(kAndroidExpandEnvVar, kAndroidExpandDefaultPath);
}

std::string GetArtApexData() {
  return GetAndroidDir(kArtApexDataEnvVar, kArtApexDataDefaultPath, /*must_exist=*/false);
}

static std::string GetPrebuiltPrimaryBootImageDir(const std::string& android_root) {
  return StringPrintf("%s/framework", android_root.c_str());
}

std::string GetPrebuiltPrimaryBootImageDir() {
  std::string android_root = GetAndroidRoot();
  if (android_root.empty()) {
    return "";
  }
  return GetPrebuiltPrimaryBootImageDir(android_root);
}

std::string GetFirstMainlineFrameworkLibraryFilename(std::string* error_msg) {
  const char* env_bcp = getenv("BOOTCLASSPATH");
  const char* env_dex2oat_bcp = getenv("DEX2OATBOOTCLASSPATH");
  if (env_bcp == nullptr || env_dex2oat_bcp == nullptr) {
    *error_msg = "BOOTCLASSPATH and DEX2OATBOOTCLASSPATH must not be empty";
    return "";
  }

  // DEX2OATBOOTCLASSPATH contains core libraries and framework libraries. We used to only compile
  // those libraries. Now we compile mainline framework libraries as well, and we have repurposed
  // DEX2OATBOOTCLASSPATH to indicate the separation between mainline framework libraries and other
  // libraries.
  std::string_view mainline_bcp(env_bcp);
  if (!android::base::ConsumePrefix(&mainline_bcp, env_dex2oat_bcp)) {
    *error_msg = "DEX2OATBOOTCLASSPATH must be a prefix of BOOTCLASSPATH";
    return "";
  }

  std::vector<std::string_view> mainline_bcp_jars;
  Split(mainline_bcp, ':', &mainline_bcp_jars);
  if (mainline_bcp_jars.empty()) {
    *error_msg = "No mainline framework library found";
    return "";
  }

  return std::string(mainline_bcp_jars[0]);
}

static std::string GetFirstMainlineFrameworkLibraryName(std::string* error_msg) {
  std::string filename = GetFirstMainlineFrameworkLibraryFilename(error_msg);
  if (filename.empty()) {
    return "";
  }

  std::string jar_name = android::base::Basename(filename);

  std::string_view library_name(jar_name);
  if (!android::base::ConsumeSuffix(&library_name, ".jar")) {
    *error_msg = "Invalid mainline framework jar: " + jar_name;
    return "";
  }

  return std::string(library_name);
}

// Returns true when no error occurs, even if the extension doesn't exist.
static bool MaybeAppendBootImageMainlineExtension(const std::string& android_root,
                                                  bool deny_system_files,
                                                  bool deny_art_apex_data_files,
                                                  /*inout*/ std::string* location,
                                                  /*out*/ std::string* error_msg) {
  if (!kIsTargetAndroid) {
    return true;
  }
  // Due to how the runtime determines the mapping between boot images and bootclasspath jars, the
  // name of the boot image extension must be in the format of
  // `<primary-boot-image-stem>-<first-library-name>.art`.
  std::string library_name = GetFirstMainlineFrameworkLibraryName(error_msg);
  if (library_name.empty()) {
    if (kRuntimeISA == InstructionSet::kRiscv64) {
      return true;
    }
    return false;
  }

  if (!deny_art_apex_data_files) {
    std::string mainline_extension_location =
        StringPrintf("%s/%s-%s.art",
                     GetApexDataDalvikCacheDirectory(InstructionSet::kNone).c_str(),
                     kBootImageStem,
                     library_name.c_str());
    std::string mainline_extension_path =
        GetSystemImageFilename(mainline_extension_location.c_str(), kRuntimeISA);
    if (OS::FileExists(mainline_extension_path.c_str(), /*check_file_type=*/true)) {
      *location += ":" + mainline_extension_location;
      return true;
    }
  }

  if (!deny_system_files) {
    std::string mainline_extension_location = StringPrintf(
        "%s/framework/%s-%s.art", android_root.c_str(), kBootImageStem, library_name.c_str());
    std::string mainline_extension_path =
        GetSystemImageFilename(mainline_extension_location.c_str(), kRuntimeISA);
    // It is expected that the file doesn't exist when the ART module is preloaded on an old source
    // tree that doesn't dexpreopt mainline BCP jars, so it shouldn't be considered as an error.
    if (OS::FileExists(mainline_extension_path.c_str(), /*check_file_type=*/true)) {
      *location += ":" + mainline_extension_location;
      return true;
    }
  }

  return true;
}

std::string GetDefaultBootImageLocationSafe(const std::string& android_root,
                                            bool deny_art_apex_data_files,
                                            std::string* error_msg) {
  constexpr static const char* kEtcBootImageProf = "etc/boot-image.prof";
  constexpr static const char* kMinimalBootImageStem = "boot_minimal";

  // If an update for the ART module has been been installed, a single boot image for the entire
  // bootclasspath is in the ART APEX data directory.
  if (kIsTargetBuild && !deny_art_apex_data_files) {
    const std::string boot_image =
        GetApexDataDalvikCacheDirectory(InstructionSet::kNone) + "/" + kBootImageStem + ".art";
    const std::string boot_image_filename = GetSystemImageFilename(boot_image.c_str(), kRuntimeISA);
    if (OS::FileExists(boot_image_filename.c_str(), /*check_file_type=*/true)) {
      // Boot image consists of two parts:
      //  - the primary boot image (contains the Core Libraries and framework libraries)
      //  - the boot image mainline extension (contains mainline framework libraries)
      // Typically
      // "/data/misc/apexdata/com.android.art/dalvik-cache/boot.art!/apex/com.android.art
      // /etc/boot-image.prof!/system/etc/boot-image.prof:
      // /data/misc/apexdata/com.android.art/dalvik-cache/boot-framework-adservices.art".
      std::string location = StringPrintf("%s!%s/%s!%s/%s",
                                          boot_image.c_str(),
                                          kAndroidArtApexDefaultPath,
                                          kEtcBootImageProf,
                                          android_root.c_str(),
                                          kEtcBootImageProf);
      if (!MaybeAppendBootImageMainlineExtension(android_root,
                                                 /*deny_system_files=*/true,
                                                 deny_art_apex_data_files,
                                                 &location,
                                                 error_msg)) {
        return "";
      }
      return location;
    } else if (errno == EACCES) {
      // Additional warning for potential SELinux misconfiguration.
      PLOG(ERROR) << "Default boot image check failed, could not stat: " << boot_image_filename;
    }

    // odrefresh can generate a minimal boot image, which only includes code from BCP jars in the
    // ART module, when it fails to generate a single boot image for the entire bootclasspath (i.e.,
    // full boot image). Use it if it exists.
    const std::string minimal_boot_image = GetApexDataDalvikCacheDirectory(InstructionSet::kNone) +
                                           "/" + kMinimalBootImageStem + ".art";
    const std::string minimal_boot_image_filename =
        GetSystemImageFilename(minimal_boot_image.c_str(), kRuntimeISA);
    if (OS::FileExists(minimal_boot_image_filename.c_str(), /*check_file_type=*/true)) {
      // Typically "/data/misc/apexdata/com.android.art/dalvik-cache/boot_minimal.art!/apex
      // /com.android.art/etc/boot-image.prof:/nonx/boot_minimal-framework.art!/system/etc
      // /boot-image.prof".
      return StringPrintf("%s!%s/%s:/nonx/%s-framework.art!%s/%s",
                          minimal_boot_image.c_str(),
                          kAndroidArtApexDefaultPath,
                          kEtcBootImageProf,
                          kMinimalBootImageStem,
                          android_root.c_str(),
                          kEtcBootImageProf);
    } else if (errno == EACCES) {
      // Additional warning for potential SELinux misconfiguration.
      PLOG(ERROR) << "Minimal boot image check failed, could not stat: " << boot_image_filename;
    }
  }

  // Boot image consists of two parts:
  //  - the primary boot image (contains the Core Libraries and framework libraries)
  //  - the boot image mainline extension (contains mainline framework libraries)
  // Typically "/system/framework/boot.art
  // !/apex/com.android.art/etc/boot-image.prof!/system/etc/boot-image.prof:
  // /system/framework/boot-framework-adservices.art".

  std::string location = StringPrintf("%s/%s.art!%s/%s!%s/%s",
                                      GetPrebuiltPrimaryBootImageDir(android_root).c_str(),
                                      kBootImageStem,
                                      kAndroidArtApexDefaultPath,
                                      kEtcBootImageProf,
                                      android_root.c_str(),
                                      kEtcBootImageProf);

#ifdef ART_TARGET_ANDROID
  // Prior to U, there was a framework extension.
  if (!android::modules::sdklevel::IsAtLeastU()) {
    location = StringPrintf("%s/%s.art!%s/%s:%s/framework/%s-framework.art!%s/%s",
                            GetPrebuiltPrimaryBootImageDir(android_root).c_str(),
                            kBootImageStem,
                            kAndroidArtApexDefaultPath,
                            kEtcBootImageProf,
                            android_root.c_str(),
                            kBootImageStem,
                            android_root.c_str(),
                            kEtcBootImageProf);
  }
#endif

  if (!MaybeAppendBootImageMainlineExtension(android_root,
                                             /*deny_system_files=*/false,
                                             deny_art_apex_data_files,
                                             &location,
                                             error_msg)) {
    return "";
  }
  return location;
}

std::string GetDefaultBootImageLocation(const std::string& android_root,
                                        bool deny_art_apex_data_files) {
  std::string error_msg;
  std::string location =
      GetDefaultBootImageLocationSafe(android_root, deny_art_apex_data_files, &error_msg);
  CHECK(!location.empty()) << error_msg;
  return location;
}

std::string GetJitZygoteBootImageLocation() {
  // Intentionally use a non-existing location so that the runtime will fail to find the boot image
  // and JIT bootclasspath with the given profiles.
  return "/nonx/boot.art!/apex/com.android.art/etc/boot-image.prof!/system/etc/boot-image.prof";
}

std::string GetBootImageLocationForDefaultBcp(bool no_boot_image,
                                              std::string user_defined_boot_image,
                                              bool deny_art_apex_data_files,
                                              std::string* error_msg) {
  if (no_boot_image) {
    return GetJitZygoteBootImageLocation();
  }
  if (!user_defined_boot_image.empty()) {
    return user_defined_boot_image;
  }
  std::string android_root = GetAndroidRootSafe(error_msg);
  if (!error_msg->empty()) {
    return "";
  }
  return GetDefaultBootImageLocationSafe(android_root, deny_art_apex_data_files, error_msg);
}

std::string GetBootImageLocationForDefaultBcpRespectingSysProps(std::string* error_msg) {
  bool no_boot_image =
      GetBoolProperty("persist.device_config.runtime_native_boot.profilebootclasspath",
                      GetBoolProperty("dalvik.vm.profilebootclasspath", /*default_value=*/false));
  std::string user_defined_boot_image = GetProperty("dalvik.vm.boot-image", /*default_value=*/"");
  bool deny_art_apex_data_files =
      !GetBoolProperty("odsign.verification.success", /*default_value=*/false);
  return GetBootImageLocationForDefaultBcp(
      no_boot_image, user_defined_boot_image, deny_art_apex_data_files, error_msg);
}

static /*constinit*/ std::string_view dalvik_cache_sub_dir = "dalvik-cache";

void OverrideDalvikCacheSubDirectory(std::string sub_dir) {
  static std::string overridden_dalvik_cache_sub_dir;
  overridden_dalvik_cache_sub_dir = std::move(sub_dir);
  dalvik_cache_sub_dir = overridden_dalvik_cache_sub_dir;
}

static std::string GetDalvikCacheDirectory(std::string_view root_directory,
                                           std::string_view sub_directory = {}) {
  std::stringstream oss;
  oss << root_directory << '/' << dalvik_cache_sub_dir;
  if (!sub_directory.empty()) {
    oss << '/' << sub_directory;
  }
  return oss.str();
}

void GetDalvikCache(const char* subdir,
                    const bool create_if_absent,
                    std::string* dalvik_cache,
                    bool* have_android_data,
                    bool* dalvik_cache_exists,
                    bool* is_global_cache) {
#ifdef _WIN32
  UNUSED(subdir);
  UNUSED(create_if_absent);
  UNUSED(dalvik_cache);
  UNUSED(have_android_data);
  UNUSED(dalvik_cache_exists);
  UNUSED(is_global_cache);
  LOG(FATAL) << "GetDalvikCache unsupported on Windows.";
#else
  CHECK(subdir != nullptr);
  std::string unused_error_msg;
  std::string android_data = GetAndroidDataSafe(&unused_error_msg);
  if (android_data.empty()) {
    *have_android_data = false;
    *dalvik_cache_exists = false;
    *is_global_cache = false;
    return;
  } else {
    *have_android_data = true;
  }
  const std::string dalvik_cache_root = GetDalvikCacheDirectory(android_data);
  *dalvik_cache = dalvik_cache_root + '/' + subdir;
  *dalvik_cache_exists = OS::DirectoryExists(dalvik_cache->c_str());
  *is_global_cache = (android_data == kAndroidDataDefaultPath);
  if (create_if_absent && !*dalvik_cache_exists && !*is_global_cache) {
    // Don't create the system's /data/dalvik-cache/... because it needs special permissions.
    *dalvik_cache_exists = ((mkdir(dalvik_cache_root.c_str(), 0700) == 0 || errno == EEXIST) &&
                            (mkdir(dalvik_cache->c_str(), 0700) == 0 || errno == EEXIST));
  }
#endif
}

// Returns a path formed by encoding the dex location into the filename. The path returned will be
// rooted at `cache_location`.
static bool GetLocationEncodedFilename(const char* location,
                                       const char* cache_location,
                                       std::string* filename,
                                       std::string* error_msg) {
  if (location[0] != '/') {
    *error_msg = StringPrintf("Expected path in location to be absolute: %s", location);
    return false;
  }
  std::string cache_file(&location[1]);  // skip leading slash
  if (!android::base::EndsWith(location, ".dex") && !android::base::EndsWith(location, ".art") &&
      !android::base::EndsWith(location, ".oat")) {
    cache_file += "/";
    cache_file += kClassesDex;
  }
  std::replace(cache_file.begin(), cache_file.end(), '/', '@');
  *filename = StringPrintf("%s/%s", cache_location, cache_file.c_str());
  return true;
}

bool GetDalvikCacheFilename(const char* location,
                            const char* cache_location,
                            std::string* filename,
                            std::string* error_msg) {
  return GetLocationEncodedFilename(location, cache_location, filename, error_msg);
}

std::string GetApexDataDalvikCacheDirectory(InstructionSet isa) {
  if (isa != InstructionSet::kNone) {
    return GetDalvikCacheDirectory(GetArtApexData(), GetInstructionSetString(isa));
  }
  return GetDalvikCacheDirectory(GetArtApexData());
}

static std::string GetApexDataDalvikCacheFilename(std::string_view dex_location,
                                                  InstructionSet isa,
                                                  bool is_boot_classpath_location,
                                                  std::string_view file_extension) {
  if (LocationIsOnApex(dex_location) && is_boot_classpath_location) {
    // We don't compile boot images for updatable APEXes.
    return {};
  }
  std::string apex_data_dalvik_cache = GetApexDataDalvikCacheDirectory(isa);
  if (!is_boot_classpath_location) {
    // Arguments: "/system/framework/xyz.jar", "arm", true, "odex"
    // Result:
    // "/data/misc/apexdata/com.android.art/dalvik-cache/arm/system@framework@xyz.jar@classes.odex"
    std::string result, unused_error_msg;
    GetDalvikCacheFilename(std::string{dex_location}.c_str(),
                           apex_data_dalvik_cache.c_str(),
                           &result,
                           &unused_error_msg);
    return ReplaceFileExtension(result, file_extension);
  } else {
    // Arguments: "/system/framework/xyz.jar", "x86_64", false, "art"
    // Results: "/data/misc/apexdata/com.android.art/dalvik-cache/x86_64/boot-xyz.jar@classes.art"
    std::string basename = android::base::Basename(std::string{dex_location});
    return apex_data_dalvik_cache + "/boot-" + ReplaceFileExtension(basename, file_extension);
  }
}

std::string GetApexDataOatFilename(std::string_view location, InstructionSet isa) {
  return GetApexDataDalvikCacheFilename(location, isa, /*is_boot_classpath_location=*/true, "oat");
}

std::string GetApexDataOdexFilename(std::string_view location, InstructionSet isa) {
  return GetApexDataDalvikCacheFilename(
      location, isa, /*is_boot_classpath_location=*/false, "odex");
}

std::string GetApexDataBootImage(std::string_view dex_location) {
  return GetApexDataDalvikCacheFilename(dex_location,
                                        InstructionSet::kNone,
                                        /*is_boot_classpath_location=*/true,
                                        kArtImageExtension);
}

std::string GetApexDataImage(std::string_view dex_location) {
  return GetApexDataDalvikCacheFilename(dex_location,
                                        InstructionSet::kNone,
                                        /*is_boot_classpath_location=*/false,
                                        kArtImageExtension);
}

std::string GetApexDataDalvikCacheFilename(std::string_view dex_location,
                                           InstructionSet isa,
                                           std::string_view file_extension) {
  return GetApexDataDalvikCacheFilename(
      dex_location, isa, /*is_boot_classpath_location=*/false, file_extension);
}

std::string GetVdexFilename(const std::string& oat_location) {
  return ReplaceFileExtension(oat_location, "vdex");
}

std::string GetDmFilename(const std::string& dex_location) {
  return ReplaceFileExtension(dex_location, "dm");
}

std::string GetSystemOdexFilenameForApex(std::string_view location, InstructionSet isa) {
  DCHECK(LocationIsOnApex(location));
  std::string dir = GetAndroidRoot() + "/framework/oat/" + GetInstructionSetString(isa);
  std::string result, error_msg;
  bool ret =
      GetLocationEncodedFilename(std::string{location}.c_str(), dir.c_str(), &result, &error_msg);
  // This should never fail. The function fails only if the location is not absolute, and a location
  // on /apex is always absolute.
  DCHECK(ret) << error_msg;
  return ReplaceFileExtension(result, "odex");
}

static void InsertIsaDirectory(const InstructionSet isa, std::string* filename) {
  // in = /foo/bar/baz
  // out = /foo/bar/<isa>/baz
  size_t pos = filename->rfind('/');
  CHECK_NE(pos, std::string::npos) << *filename << " " << isa;
  filename->insert(pos, "/", 1);
  filename->insert(pos + 1, GetInstructionSetString(isa));
}

std::string GetSystemImageFilename(const char* location, const InstructionSet isa) {
  // location = /system/framework/boot.art
  // filename = /system/framework/<isa>/boot.art
  std::string filename(location);
  InsertIsaDirectory(isa, &filename);
  return filename;
}

std::string ReplaceFileExtension(std::string_view filename, std::string_view new_extension) {
  const size_t last_ext = filename.find_last_of("./");
  std::string result;
  if (last_ext == std::string::npos || filename[last_ext] != '.') {
    result.reserve(filename.size() + 1 + new_extension.size());
    result.append(filename).append(".").append(new_extension);
  } else {
    result.reserve(last_ext + 1 + new_extension.size());
    result.append(filename.substr(0, last_ext + 1)).append(new_extension);
  }
  return result;
}

bool LocationIsOnArtApexData(std::string_view location) {
  const std::string art_apex_data = GetArtApexData();
  return android::base::StartsWith(location, art_apex_data);
}

bool LocationIsOnArtModule(std::string_view full_path) {
  std::string unused_error_msg;
  std::string module_path = GetArtRootSafe(/* must_exist= */ kIsTargetBuild, &unused_error_msg);
  if (module_path.empty()) {
    return false;
  }
  return android::base::StartsWith(full_path, module_path);
}

static bool StartsWithSlash(const char* str) {
  DCHECK(str != nullptr);
  return str[0] == '/';
}

static bool EndsWithSlash(const char* str) {
  DCHECK(str != nullptr);
  size_t len = strlen(str);
  return len > 0 && str[len - 1] == '/';
}

// Returns true if `full_path` is located in folder either provided with `env_var`
// or in `default_path` otherwise. The caller may optionally provide a `subdir`
// which will be appended to the tested prefix.
// `default_path` and the value of environment variable `env_var`
// are expected to begin with a slash and not end with one. If this ever changes,
// the path-building logic should be updated.
static bool IsLocationOn(std::string_view full_path,
                         const char* env_var,
                         const char* default_path,
                         const char* subdir = nullptr) {
  std::string unused_error_msg;
  const char* path = GetAndroidDirSafe(env_var,
                                       default_path,
                                       /* must_exist= */ kIsTargetBuild,
                                       &unused_error_msg);
  if (path == nullptr) {
    return false;
  }

  // Build the path which we will check is a prefix of `full_path`. The prefix must
  // end with a slash, so that "/foo/bar" does not match "/foo/barz".
  DCHECK(StartsWithSlash(path)) << path;
  std::string path_prefix(path);
  if (!EndsWithSlash(path_prefix.c_str())) {
    path_prefix.append("/");
  }
  if (subdir != nullptr) {
    // If `subdir` is provided, we assume it is provided without a starting slash
    // but ending with one, e.g. "sub/dir/". `path_prefix` ends with a slash at
    // this point, so we simply append `subdir`.
    DCHECK(!StartsWithSlash(subdir) && EndsWithSlash(subdir)) << subdir;
    path_prefix.append(subdir);
  }

  return android::base::StartsWith(full_path, path_prefix);
}

bool LocationIsOnSystemFramework(std::string_view full_path) {
  return IsLocationOn(full_path,
                      kAndroidRootEnvVar,
                      kAndroidRootDefaultPath,
                      /* subdir= */ "framework/");
}

bool LocationIsOnSystemExtFramework(std::string_view full_path) {
  return IsLocationOn(full_path,
                      kAndroidSystemExtRootEnvVar,
                      kAndroidSystemExtRootDefaultPath,
                      /* subdir= */ "framework/") ||
         // When the 'system_ext' partition is not present, builds will create
         // '/system/system_ext' instead.
         IsLocationOn(full_path,
                      kAndroidRootEnvVar,
                      kAndroidRootDefaultPath,
                      /* subdir= */ "system_ext/framework/");
}

bool LocationIsOnConscryptModule(std::string_view full_path) {
  return IsLocationOn(full_path, kAndroidConscryptRootEnvVar, kAndroidConscryptApexDefaultPath);
}

bool LocationIsOnI18nModule(std::string_view full_path) {
  return IsLocationOn(full_path, kAndroidI18nRootEnvVar, kAndroidI18nApexDefaultPath);
}

bool LocationIsOnApex(std::string_view full_path) {
  return android::base::StartsWith(full_path, kApexDefaultPath);
}

std::string_view ApexNameFromLocation(std::string_view full_path) {
  if (!android::base::StartsWith(full_path, kApexDefaultPath)) {
    return {};
  }
  size_t start = strlen(kApexDefaultPath);
  size_t end = full_path.find('/', start);
  if (end == std::string_view::npos) {
    return {};
  }
  return full_path.substr(start, end - start);
}

bool LocationIsOnSystem(const std::string& location) {
#ifdef _WIN32
  UNUSED(location);
  LOG(FATAL) << "LocationIsOnSystem is unsupported on Windows.";
  return false;
#else
  return android::base::StartsWith(location, GetAndroidRoot());
#endif
}

bool LocationIsOnSystemExt(const std::string& location) {
#ifdef _WIN32
  UNUSED(location);
  LOG(FATAL) << "LocationIsOnSystemExt is unsupported on Windows.";
  return false;
#else
  return IsLocationOn(location,
                      kAndroidSystemExtRootEnvVar,
                      kAndroidSystemExtRootDefaultPath) ||
         // When the 'system_ext' partition is not present, builds will create
         // '/system/system_ext' instead.
         IsLocationOn(location,
                      kAndroidRootEnvVar,
                      kAndroidRootDefaultPath,
                      /* subdir= */ "system_ext/");
#endif
}

bool LocationIsTrusted(const std::string& location, bool trust_art_apex_data_files) {
  if (LocationIsOnSystem(location) || LocationIsOnSystemExt(location)
        || LocationIsOnArtModule(location)) {
    return true;
  }
  return LocationIsOnArtApexData(location) & trust_art_apex_data_files;
}

bool ArtModuleRootDistinctFromAndroidRoot() {
  std::string error_msg;
  const char* android_root = GetAndroidDirSafe(kAndroidRootEnvVar,
                                               kAndroidRootDefaultPath,
                                               /* must_exist= */ kIsTargetBuild,
                                               &error_msg);
  const char* art_root = GetAndroidDirSafe(kAndroidArtRootEnvVar,
                                           kAndroidArtApexDefaultPath,
                                           /* must_exist= */ kIsTargetBuild,
                                           &error_msg);
  return (android_root != nullptr) && (art_root != nullptr) &&
         (std::string_view(android_root) != std::string_view(art_root));
}

int DupCloexec(int fd) {
#if defined(__linux__)
  return fcntl(fd, F_DUPFD_CLOEXEC, 0);
#else
  return dup(fd); // NOLINT
#endif
}

}  // namespace art
