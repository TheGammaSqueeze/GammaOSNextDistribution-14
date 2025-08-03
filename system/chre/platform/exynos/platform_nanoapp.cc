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

#include "chre/platform/platform_nanoapp.h"
#include "chre/platform/shared/libc/dlfcn.h"
#include "chre/platform/shared/memory.h"
#include "chre/platform/shared/nanoapp_dso_util.h"
#include "chre/util/system/napp_permissions.h"

#include <cinttypes>

namespace chre {

PlatformNanoapp::~PlatformNanoapp() {}

bool PlatformNanoappBase::reserveBuffer(uint64_t appId, uint32_t appVersion,
                                        uint32_t appFlags, size_t appBinaryLen,
                                        uint32_t targetApiVersion) {
  CHRE_ASSERT(!isLoaded());

  bool success = false;
  mAppBinary = memoryAlloc(appBinaryLen);

  // TODO(b/237819962): Check binary signature when authentication is
  // implemented.
  if (mAppBinary == nullptr) {
    LOG_OOM();
  } else {
    mExpectedAppId = appId;
    mExpectedAppVersion = appVersion;
    mExpectedTargetApiVersion = targetApiVersion;
    mAppBinaryLen = appBinaryLen;
    success = true;
  }

  return success;
}

bool PlatformNanoappBase::copyNanoappFragment(const void *buffer,
                                              size_t bufferLen) {
  CHRE_ASSERT(!isLoaded());

  bool success = true;

  if ((mBytesLoaded + bufferLen) > mAppBinaryLen) {
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

bool PlatformNanoapp::start() {
  bool success = false;

  if (!openNanoapp()) {
    LOGE("Failed to open the nanoapp");
  } else if (mAppInfo != nullptr) {
    success = mAppInfo->entryPoints.start();
  } else {
    LOGE("app info was null - unable to start nanoapp");
  }

  return success;
}

void PlatformNanoapp::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                                  const void *eventData) {
  mAppInfo->entryPoints.handleEvent(senderInstanceId, eventType, eventData);
}

void PlatformNanoapp::end() {
  mAppInfo->entryPoints.end();
}

uint64_t PlatformNanoapp::getAppId() const {
  return mAppInfo->appId;
}

uint32_t PlatformNanoapp::getAppVersion() const {
  return mAppInfo->appVersion;
}

uint32_t PlatformNanoapp::getTargetApiVersion() const {
  return mAppInfo->targetApiVersion;
}
bool PlatformNanoapp::isSystemNanoapp() const {
  return mAppInfo->isSystemNanoapp;
}

const char *PlatformNanoapp::getAppName() const {
  return (mAppInfo != nullptr) ? mAppInfo->name : "Unknown";
}

bool PlatformNanoappBase::isLoaded() const {
  return (mIsStatic ||
          (mAppBinary != nullptr && mBytesLoaded == mAppBinaryLen) ||
          mDsoHandle != nullptr);
}

void PlatformNanoappBase::loadStatic(const struct chreNslNanoappInfo *appInfo) {
  CHRE_ASSERT(!isLoaded());
  mIsStatic = true;
  mAppInfo = appInfo;
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

bool PlatformNanoappBase::verifyNanoappInfo() {
  bool success = false;

  if (mDsoHandle == nullptr) {
    LOGE("No nanoapp info to verify");
  } else {
    mAppInfo = static_cast<const struct chreNslNanoappInfo *>(
        dlsym(mDsoHandle, CHRE_NSL_DSO_NANOAPP_INFO_SYMBOL_NAME));
    if (mAppInfo == nullptr) {
      LOGE("Failed to find app info symbol");
    } else {
      mAppUnstableId = mAppInfo->appVersionString;
      if (mAppUnstableId == nullptr) {
        LOGE("Failed to find unstable ID symbol");
      } else {
        success = validateAppInfo(mExpectedAppId, mExpectedAppVersion,
                                  mExpectedTargetApiVersion, mAppInfo);
        if (!success) {
          mAppInfo = nullptr;
        } else {
          LOGI("Nanoapp loaded: %s (0x%016" PRIx64 ") version 0x%" PRIx32
               " (%s) uimg %d system %d",
               mAppInfo->name, mAppInfo->appId, mAppInfo->appVersion,
               mAppInfo->appVersionString, mAppInfo->isTcmNanoapp,
               mAppInfo->isSystemNanoapp);
          if (mAppInfo->structMinorVersion >=
              CHRE_NSL_NANOAPP_INFO_STRUCT_MINOR_VERSION_3) {
            LOGI("Nanoapp permissions: 0x%" PRIx32, mAppInfo->appPermissions);
          }
        }
      }
    }
  }
  return success;
}

bool PlatformNanoappBase::openNanoapp() {
  bool success = false;
  if (mIsStatic) {
    success = true;
  } else if (mAppBinary != nullptr) {
    if (mDsoHandle != nullptr) {
      LOGE("Trying to reopen an existing buffer");
    } else {
      mDsoHandle = dlopenbuf(mAppBinary, false /* mapIntoTcm */);
      success = verifyNanoappInfo();
    }
  }

  if (!success) {
    closeNanoapp();
  }

  if (mAppBinary != nullptr) {
    nanoappBinaryDramFree(mAppBinary);
    mAppBinary = nullptr;
  }

  return success;
}

void PlatformNanoappBase::closeNanoapp() {
  if (mDsoHandle != nullptr) {
    // Force DRAM access since dl* functions are only safe to call with DRAM
    // available.
    forceDramAccess();
    mAppInfo = nullptr;
    if (dlclose(mDsoHandle) != 0) {
      LOGE("dlclose failed");
    }
    mDsoHandle = nullptr;
  }
}

}  // namespace chre
