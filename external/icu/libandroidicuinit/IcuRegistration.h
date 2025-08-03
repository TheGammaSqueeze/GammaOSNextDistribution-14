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

#ifndef ICU_REGISTRATION_H_included
#define ICU_REGISTRATION_H_included

#include <memory>
#include <string>
#include <cstdio>


/**
 * def NO_ANDROID_LOGGING
 *   This flag turns off the usage of liblog, and logs into stderr instead.
 *   This is not expected to be used in Android platform build, but is useful
 *   when building this ICU4C for unbundled library or app.
 */
#if defined(__ANDROID__) && !defined(NO_ANDROID_LIBLOG)
  #include <android-base/logging.h>
  #include <android-base/unique_fd.h>
  #include <log/log.h>
  #define AICU_LOGE(...) ALOGE(__VA_ARGS__)
  #define AICU_LOGW(...) ALOGW(__VA_ARGS__)
  #define AICU_LOGD(...) ALOGD(__VA_ARGS__)
  #define AICU_LOGV(...) ALOGV(__VA_ARGS__)
#else
  // http://b/171371690 Avoid dependency on liblog and libbase on host for
  // downstream unbundled branches. In this case, liblog and libbase are not
  // very useful on host and we just try to avoid it here in our best effort.

  // Check if a host should log a message.
  //
  // This method checks the priority argument against the wildcard level set
  // in the ANDROID_LOG_TAGS environment variable. The priority specified
  // corresponds to the standard Android set:
  //
  //   V - verbose    D - debug
  //   I - info       W - warn
  //   E - error      F - fatal
  //
  // If the ANDROID_LOG_TAGS variable is not set then this method returns true.
  // Otherwise, the priority is compared to the level in ANDROID_LOG_TAGS.
  //
  // Example: if ANDROID_LOG_TAGS has the value "*:W" then this method will
  // return true if the priority is warn or above.
  bool AIcuHostShouldLog(char priority);

  #define AICU_LOG_PRINTLN(priority, ...)       \
    do {                                        \
      if (AIcuHostShouldLog(priority)) {        \
        fprintf(stderr, __VA_ARGS__);           \
        fputc('\n', stderr);                    \
      }                                         \
    } while (0)
  #define AICU_LOGE(...) AICU_LOG_PRINTLN('E', __VA_ARGS__)
  #define AICU_LOGW(...) AICU_LOG_PRINTLN('W', __VA_ARGS__)
  #define AICU_LOGD(...) AICU_LOG_PRINTLN('D', __VA_ARGS__)
  #define AICU_LOGV(...) AICU_LOG_PRINTLN('V', __VA_ARGS__)
  #ifndef CHECK
    #define CHECK(cond)       \
      if (!(cond)) {             \
        AICU_LOGE(#cond "\n");     \
        abort();              \
      }
  #endif
#endif

namespace androidicuinit {
namespace impl {

/*
 * Handles ICU data mapping for a single ICU .dat file.
 * The Create method handles mapping the file into memory and calling
 * udata_setCommonData(). The file is unmapped on object destruction.
 */
class IcuDataMap final {
 public:
  // Maps in ICU data at the path and call udata_setCommonData(), returning
  // null if it failed (prints error to ALOGE).
  static std::unique_ptr<IcuDataMap> Create(const std::string& path);
  // Unmaps the ICU data.
  ~IcuDataMap();

 private:
  IcuDataMap(const std::string& path)
      : path_(path), data_(nullptr), data_length_(0) {}
  bool TryMap();
  bool TryUnmap();

  std::string path_;    // Save for error messages.
  void* data_;          // Save for munmap.
  size_t data_length_;  // Save for munmap.

  // Disable copy constructor and assignment operator
  IcuDataMap(const IcuDataMap&) = delete;
  void operator=(const IcuDataMap&) = delete;
};

}  // namespace impl

/*
 * Handles the mapping of all ICU data files into memory for the various files
 * used on Android. All data files are unmapped on object destruction.
 */
class IcuRegistration final {
 public:
  static void Register();
  static void Deregister();

  // Required to be public so it can be destructed by unique_ptr.
  ~IcuRegistration();

 private:
  IcuRegistration();

  static bool pathExists(const std::string& path);
  static std::string getTimeZoneModulePath();
  static std::string getI18nModulePath();

  std::unique_ptr<impl::IcuDataMap> icu_datamap_from_tz_module_;
  std::unique_ptr<impl::IcuDataMap> icu_datamap_from_i18n_module_;

  // Disable copy constructor and assignment operator
  IcuRegistration(const IcuRegistration&) = delete;
  void operator=(const IcuRegistration&) = delete;
};

}  // namespace androidicuinit

/**
 * Initialize the ICU and load the data from .dat files from system image and
 * various mainline modules.
 * If ICU has already been registered, the function calls abort() and the process terminates.
 * This function is NOT thread-safe.
 */
void android_icu_register();

/**
 * Unregister and unload the data. After this call, user can re-register.
 */
void android_icu_deregister();

/**
 * @return true if ICU has been registered.
 */
bool android_icu_is_registered();


#endif  // ICU_REGISTRATION_H_included
