/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "chre/platform/platform_nanoapp.h"

#include "chre/core/event_loop_manager.h"
#include "chre/platform/assert.h"
#include "chre/platform/log.h"
#include "chre/platform/memory.h"
#include "chre/platform/shared/nanoapp_dso_util.h"
#include "chre/platform/shared/nanoapp_support_lib_dso.h"
#include "chre/platform/slpi/memory.h"
#include "chre/platform/slpi/power_control_util.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/system/napp_permissions.h"
#include "chre_api/chre/version.h"

#include "dlfcn.h"

#include <inttypes.h>
#include <string.h>

namespace chre {

PlatformNanoapp::~PlatformNanoapp() {
  closeNanoapp();
  if (mAppBinary != nullptr) {
    memoryFreeBigImage(mAppBinary);
  }
}

bool PlatformNanoapp::start() {
  // Invoke the start entry point after successfully opening the app
  if (!isUimgApp()) {
    slpiForceBigImage();
  }

  return openNanoapp() && mAppInfo->entryPoints.start();
}

void PlatformNanoapp::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                                  const void *eventData) {
  if (!isUimgApp()) {
    slpiForceBigImage();
  }

  mAppInfo->entryPoints.handleEvent(senderInstanceId, eventType, eventData);
}

void PlatformNanoapp::end() {
  if (!isUimgApp()) {
    slpiForceBigImage();
  }

  mAppInfo->entryPoints.end();
  closeNanoapp();
}

bool PlatformNanoappBase::setAppInfo(uint64_t appId, uint32_t appVersion,
                                     const char *appFilename,
                                     uint32_t targetApiVersion) {
  CHRE_ASSERT(!isLoaded());
  mExpectedAppId = appId;
  mExpectedAppVersion = appVersion;
  mExpectedTargetApiVersion = targetApiVersion;
  size_t appFilenameLen = strlen(appFilename) + 1;
  mAppFilename = static_cast<char *>(memoryAllocBigImage(appFilenameLen));

  bool success = false;
  if (mAppFilename == nullptr) {
    LOG_OOM();
  } else {
    memcpy(static_cast<void *>(mAppFilename), appFilename, appFilenameLen);
    success = true;
  }

  return success;
}

bool PlatformNanoappBase::reserveBuffer(uint64_t appId, uint32_t appVersion,
                                        uint32_t /* appFlags */,
                                        size_t appBinaryLen,
                                        uint32_t targetApiVersion) {
  CHRE_ASSERT(!isLoaded());
  bool success = false;
  constexpr size_t kMaxAppSize = 2 * 1024 * 1024;  // 2 MiB

  if (appBinaryLen > kMaxAppSize) {
    LOGE("Rejecting app size %zu above limit %zu", appBinaryLen, kMaxAppSize);
  } else {
    mAppBinary = memoryAllocBigImage(appBinaryLen);
    if (mAppBinary == nullptr) {
      LOGE("Couldn't allocate %zu byte buffer for nanoapp 0x%016" PRIx64,
           appBinaryLen, appId);
    } else {
      mExpectedAppId = appId;
      mExpectedAppVersion = appVersion;
      mExpectedTargetApiVersion = targetApiVersion;
      mAppBinaryLen = appBinaryLen;
      success = true;
    }
  }

  return success;
}

bool PlatformNanoappBase::copyNanoappFragment(const void *buffer,
                                              size_t bufferLen) {
  CHRE_ASSERT(!isLoaded());

  bool success = true;
  if (mBytesLoaded + bufferLen > mAppBinaryLen) {
    LOGE("Overflow: cannot load %zu bytes to %zu/%zu nanoapp binary buffer",
         bufferLen, mBytesLoaded, mAppBinaryLen);
    success = false;
  } else {
    uint8_t *binaryBuffer = static_cast<uint8_t *>(mAppBinary) + mBytesLoaded;
    memcpy(binaryBuffer, buffer, bufferLen);
    mBytesLoaded += bufferLen;
  }

  return success;
}

void PlatformNanoappBase::loadStatic(const struct chreNslNanoappInfo *appInfo) {
  CHRE_ASSERT(!isLoaded());
  mIsStatic = true;
  mAppInfo = appInfo;
}

bool PlatformNanoappBase::isLoaded() const {
  return (mIsStatic ||
          (mAppBinary != nullptr && mBytesLoaded == mAppBinaryLen) ||
          mDsoHandle != nullptr || mAppFilename != nullptr);
}

bool PlatformNanoappBase::isUimgApp() const {
  return mIsUimgApp;
}

void PlatformNanoappBase::closeNanoapp() {
  if (mDsoHandle != nullptr) {
    mAppInfo = nullptr;
    if (dlclose(mDsoHandle) != 0) {
      LOGE("dlclose failed: %s", dlerror());
    }
    mDsoHandle = nullptr;
  }
}

bool PlatformNanoappBase::openNanoapp() {
  bool success = false;

  if (mIsStatic) {
    success = true;
  } else if (mAppBinary != nullptr) {
    success = openNanoappFromBuffer();
  } else if (mAppFilename != nullptr) {
    success = openNanoappFromFile();
  } else {
    CHRE_ASSERT(false);
  }

  // Ensure any allocated memory hanging around is properly cleaned up.
  if (!success) {
    closeNanoapp();
  }

  // Save this flag locally since it may be referenced while the system is in
  // micro-image
  if (mAppInfo != nullptr) {
    mIsUimgApp = mAppInfo->isTcmNanoapp;
  }

  return success;
}

bool PlatformNanoappBase::openNanoappFromBuffer() {
  CHRE_ASSERT(mAppBinary != nullptr);
  CHRE_ASSERT_LOG(mDsoHandle == nullptr, "Re-opening nanoapp");

  // Populate a filename string (just a requirement of the dlopenbuf API)
  constexpr size_t kMaxFilenameLen = 17;
  char filename[kMaxFilenameLen];
  snprintf(filename, sizeof(filename), "%016" PRIx64, mExpectedAppId);

  mDsoHandle = dlopenbuf(filename, static_cast<const char *>(mAppBinary),
                         static_cast<int>(mAppBinaryLen), RTLD_NOW);
  memoryFreeBigImage(mAppBinary);
  mAppBinary = nullptr;

  return verifyNanoappInfo();
}

bool PlatformNanoappBase::openNanoappFromFile() {
  CHRE_ASSERT(mAppFilename != nullptr);
  CHRE_ASSERT_LOG(mDsoHandle == nullptr, "Re-opening nanoapp");

  mDsoHandle = dlopen(mAppFilename, RTLD_NOW);
  memoryFreeBigImage(mAppFilename);
  mAppFilename = nullptr;

  return verifyNanoappInfo();
}

bool PlatformNanoappBase::verifyNanoappInfo() {
  bool success = false;

  if (mDsoHandle == nullptr) {
    LOGE("No nanoapp info to verify: %s", dlerror());
  } else {
    mAppInfo = static_cast<const struct chreNslNanoappInfo *>(
        dlsym(mDsoHandle, CHRE_NSL_DSO_NANOAPP_INFO_SYMBOL_NAME));
    if (mAppInfo == nullptr) {
      LOGE("Failed to find app info symbol: %s", dlerror());
    } else {
      success = validateAppInfo(mExpectedAppId, mExpectedAppVersion,
                                mExpectedTargetApiVersion, mAppInfo);
      if (!success) {
        mAppInfo = nullptr;
      } else {
        LOGI("Nanoapp loaded: %s (0x%016" PRIx64 ") version 0x%" PRIx32
             " (%s) uimg %d system %d",
             mAppInfo->name, mAppInfo->appId, mAppInfo->appVersion,
             getAppVersionString(), mAppInfo->isTcmNanoapp,
             mAppInfo->isSystemNanoapp);
        if (mAppInfo->structMinorVersion >=
            CHRE_NSL_NANOAPP_INFO_STRUCT_MINOR_VERSION_3) {
          LOGI("Nanoapp permissions: 0x%" PRIx32, mAppInfo->appPermissions);
        }
      }
    }
  }

  return success;
}

const char *PlatformNanoappBase::getAppVersionString() const {
  const char *versionString = "<undefined>";
  if (mAppInfo != nullptr && mAppInfo->structMinorVersion >= 2 &&
      mAppInfo->appVersionString != NULL) {
    size_t appVersionStringLength = strlen(mAppInfo->appVersionString);

    size_t offset = 0;
    for (size_t i = 0; i < appVersionStringLength; i++) {
      size_t newOffset = i + 1;
      if (mAppInfo->appVersionString[i] == '@' &&
          newOffset < appVersionStringLength) {
        offset = newOffset;
        break;
      }
    }

    versionString = &mAppInfo->appVersionString[offset];
  }

  return versionString;
}

uint64_t PlatformNanoapp::getAppId() const {
  return (mAppInfo != nullptr) ? mAppInfo->appId : mExpectedAppId;
}

uint32_t PlatformNanoapp::getAppVersion() const {
  return (mAppInfo != nullptr) ? mAppInfo->appVersion : mExpectedAppVersion;
}

uint32_t PlatformNanoapp::getTargetApiVersion() const {
  return (mAppInfo != nullptr) ? mAppInfo->targetApiVersion
                               : mExpectedTargetApiVersion;
}

bool PlatformNanoapp::supportsAppPermissions() const {
  return (mAppInfo != nullptr) ? (mAppInfo->structMinorVersion >=
                                  CHRE_NSL_NANOAPP_INFO_STRUCT_MINOR_VERSION_3)
                               : false;
}

uint32_t PlatformNanoapp::getAppPermissions() const {
  return (supportsAppPermissions())
             ? mAppInfo->appPermissions
             : static_cast<uint32_t>(chre::NanoappPermissions::CHRE_PERMS_NONE);
}

const char *PlatformNanoapp::getAppName() const {
  return (mAppInfo != nullptr) ? mAppInfo->name : "Unknown";
}

bool PlatformNanoapp::isSystemNanoapp() const {
  // Right now, we assume that system nanoapps are always static nanoapps. Since
  // mAppInfo can only be null either prior to loading the app (in which case
  // this function is not expected to return a valid value anyway), or when a
  // dynamic nanoapp is not running, "false" is the correct return value in that
  // case.
  return (mAppInfo != nullptr) ? mAppInfo->isSystemNanoapp : false;
}

void PlatformNanoapp::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  if (mAppInfo != nullptr) {
    debugDump.print("%s (%s) @ %s", mAppInfo->name, mAppInfo->vendor,
                    getAppVersionString());
  }
}

}  // namespace chre
