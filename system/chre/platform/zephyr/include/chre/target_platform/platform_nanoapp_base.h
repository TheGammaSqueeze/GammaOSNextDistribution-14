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

#ifndef CHRE_PLATFORM_ZEPHYR_PLATFORM_NANOAPP_BASE_H_
#define CHRE_PLATFORM_ZEPHYR_PLATFORM_NANOAPP_BASE_H_

#include "chre/platform/shared/nanoapp_support_lib_dso.h"

namespace chre {

/**
 * @brief Platform specific base class for nanoapps.
 */
class PlatformNanoappBase {
 public:
  /**
   * Associate this Nanoapp instance with a nanoapp that is statically built
   * into the CHRE binary with the given app info structure.
   *
   * @param appInfo The app's information
   */
  void loadStatic(const struct ::chreNslNanoappInfo *appInfo);

  /**
   * @return true if the app's binary data is resident in memory or if the app's
   *         filename is saved, i.e. all binary fragments are loaded through
   *         copyNanoappFragment, loadFromFile/loadStatic() was successful
   */
  bool isLoaded() const;

 protected:
  /**
   * The app ID we received in the metadata alongside the nanoapp binary. This
   * is also included in (and checked against) mAppInfo.
   */
  uint64_t mExpectedAppId;

  /**
   * The application-defined version number we received in the metadata
   * alongside the nanoapp binary. This is also included in (and checked
   * against) mAppInfo.
   */
  uint32_t mExpectedAppVersion = 0;

  //! The app target API version in the metadata alongside the nanoapp binary.
  uint32_t mExpectedTargetApiVersion = 0;

  /**
   * Set to true if this app is built into the CHRE binary, and was loaded via
   * loadStatic(). In this case, the member variables above are not valid or
   * applicable.
   */
  bool mIsStatic = false;

  /** Pointer to the app info structure within this nanoapp */
  const struct ::chreNslNanoappInfo *mAppInfo = nullptr;
};

}  // namespace chre
#endif  // CHRE_PLATFORM_ZEPHYR_PLATFORM_NANOAPP_BASE_H_
