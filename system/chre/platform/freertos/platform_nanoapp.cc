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

#include "chre/platform/platform_nanoapp.h"

#include <dlfcn.h>
#include <cinttypes>

#include "chre/platform/assert.h"
#include "chre/platform/host_link.h"
#include "chre/platform/log.h"
#include "chre/platform/shared/authentication.h"
#include "chre/platform/shared/nanoapp_dso_util.h"
#include "chre/platform/shared/nanoapp_loader.h"
#include "chre/util/macros.h"
#include "chre/util/system/napp_header_utils.h"
#include "chre/util/system/napp_permissions.h"
#include "chre_api/chre/version.h"

namespace chre {
namespace {

#ifndef CHRE_NANOAPP_LOAD_ALIGNMENT
#define CHRE_NANOAPP_LOAD_ALIGNMENT 0
#endif

const char kDefaultAppVersionString[] = "<undefined>";
size_t kDefaultAppVersionStringSize = ARRAY_SIZE(kDefaultAppVersionString);

}  // namespace

PlatformNanoapp::~PlatformNanoapp() {
  closeNanoapp();

  if (mAppBinary != nullptr) {
    forceDramAccess();
    nanoappBinaryDramFree(mAppBinary);
  }
}

bool PlatformNanoapp::start() {
  //! Always force DRAM access when starting since nanoapps are loaded via DRAM.
  forceDramAccess();

  bool success = false;
  if (!openNanoapp()) {
    LOGE("Failed to open nanoapp");
  } else if (mAppInfo == nullptr) {
    LOGE("Null app info!");
  } else {
    success = mAppInfo->entryPoints.start();
  }

  return success;
}

void PlatformNanoapp::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                                  const void *eventData) {
  enableDramAccessIfRequired();
  mAppInfo->entryPoints.handleEvent(senderInstanceId, eventType, eventData);
}

void PlatformNanoapp::end() {
  enableDramAccessIfRequired();
  mAppInfo->entryPoints.end();
  closeNanoapp();
}

uint64_t PlatformNanoapp::getAppId() const {
  // TODO (karthikmb/stange): Ideally, we should store the metadata as
  // variables in TCM, to avoid bumping into DRAM for basic queries.
  enableDramAccessIfRequired();
  return (mAppInfo != nullptr) ? mAppInfo->appId : mExpectedAppId;
}

uint32_t PlatformNanoapp::getAppVersion() const {
  enableDramAccessIfRequired();
  return (mAppInfo != nullptr) ? mAppInfo->appVersion : mExpectedAppVersion;
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
  enableDramAccessIfRequired();
  return (mAppInfo != nullptr) ? mAppInfo->name : "Unknown";
}

uint32_t PlatformNanoapp::getTargetApiVersion() const {
  enableDramAccessIfRequired();
  return (mAppInfo != nullptr) ? mAppInfo->targetApiVersion
                               : mExpectedTargetApiVersion;
}

bool PlatformNanoapp::isSystemNanoapp() const {
  enableDramAccessIfRequired();
  return (mAppInfo != nullptr && mAppInfo->isSystemNanoapp);
}

void PlatformNanoapp::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  if (mAppInfo != nullptr) {
    enableDramAccessIfRequired();
    size_t versionLen = 0;
    const char *version = getAppVersionString(&versionLen);
    debugDump.print("%s (%s) @ build: %.*s", mAppInfo->name, mAppInfo->vendor,
                    static_cast<int>(versionLen), version);
  }
}

const char *PlatformNanoappBase::getAppVersionString(size_t *length) const {
  const char *versionString = kDefaultAppVersionString;
  *length = kDefaultAppVersionStringSize;
  enableDramAccessIfRequired();

  if (mAppUnstableId != nullptr) {
    size_t appVersionStringLength = strlen(mAppUnstableId);

    //! The unstable ID is expected to be in the format of
    //! <descriptor>=<nanoapp_name>@<build_id>. Use this expected layout
    //! knowledge to parse the string and only return the build ID portion that
    //! should be printed.
    size_t startOffset = appVersionStringLength;
    for (size_t i = 0; i < appVersionStringLength; i++) {
      size_t offset = i + 1;
      if (startOffset == appVersionStringLength && mAppUnstableId[i] == '@') {
        startOffset = offset;
      }
    }

    if (startOffset < appVersionStringLength) {
      versionString = &mAppUnstableId[startOffset];
      *length = appVersionStringLength - startOffset;
    }
  }

  return versionString;
}

bool PlatformNanoappBase::isLoaded() const {
  return (mIsStatic ||
          (mAppBinary != nullptr && mBytesLoaded == mAppBinaryLen) ||
          mDsoHandle != nullptr || mAppFilename != nullptr);
}

bool PlatformNanoappBase::isTcmApp() const {
  return mIsTcmNanoapp;
}

void PlatformNanoappBase::loadStatic(const struct chreNslNanoappInfo *appInfo) {
  CHRE_ASSERT(!isLoaded());
  mIsStatic = true;
  mAppInfo = appInfo;
}

bool PlatformNanoappBase::reserveBuffer(uint64_t appId, uint32_t appVersion,
                                        uint32_t appFlags, size_t appBinaryLen,
                                        uint32_t targetApiVersion) {
  CHRE_ASSERT(!isLoaded());

  forceDramAccess();

  bool success = false;
  mAppBinary =
      nanoappBinaryDramAlloc(appBinaryLen, CHRE_NANOAPP_LOAD_ALIGNMENT);

  bool isSigned = IS_BIT_SET(appFlags, CHRE_NAPP_HEADER_SIGNED);
  if (!isSigned) {
    LOGE("Unable to load unsigned nanoapps");
  } else if (mAppBinary == nullptr) {
    LOG_OOM();
  } else {
    bool tcmCapable = IS_BIT_SET(appFlags, CHRE_NAPP_HEADER_TCM_CAPABLE);
    mExpectedAppId = appId;
    mExpectedAppVersion = appVersion;
    mExpectedTargetApiVersion = targetApiVersion;
    mExpectedTcmCapable = tcmCapable;
    mAppBinaryLen = appBinaryLen;
    success = true;
  }

  return success;
}

bool PlatformNanoappBase::copyNanoappFragment(const void *buffer,
                                              size_t bufferLen) {
  CHRE_ASSERT(!isLoaded());

  forceDramAccess();

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
        if (success && mAppInfo->isTcmNanoapp != mExpectedTcmCapable) {
          success = false;
          LOGE("Expected TCM nanoapp %d found %d", mExpectedTcmCapable,
               mAppInfo->isTcmNanoapp);
        }

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

void PlatformNanoappBase::sendTokenDatabaseInfo() {
  auto *loader = reinterpret_cast<chre::NanoappLoader *>(mDsoHandle);
  uint32_t databaseOffset = 0;
  size_t databaseSize = 0;

  if (loader->getTokenDatabaseSectionInfo(&databaseOffset, &databaseSize)) {
    HostLinkBase::sendNanoappTokenDatabaseInfo(mExpectedAppId, databaseOffset,
                                               databaseSize);
  }
}

bool PlatformNanoappBase::openNanoapp() {
  bool success = false;
  if (mIsStatic) {
    success = true;
  } else if (mAppBinary != nullptr) {
    //! The true start of the binary will be after the authentication header.
    //! Use the returned value from authenticateBinary to ensure dlopenbuf has
    //! the starting address to a valid ELF.
    void *binaryStart = mAppBinary;
    if (!authenticateBinary(mAppBinary, mAppBinaryLen, &binaryStart)) {
      LOGE("Unable to authenticate 0x%" PRIx64 " not loading", mExpectedAppId);
    } else if (mDsoHandle != nullptr) {
      LOGE("Trying to reopen an existing buffer");
    } else {
      mDsoHandle = dlopenbuf(binaryStart, mExpectedTcmCapable);
      success = verifyNanoappInfo();
      if (success) {
        sendTokenDatabaseInfo();
      }
    }
  }

  if (!success) {
    closeNanoapp();
  }

  if (mAppBinary != nullptr) {
    nanoappBinaryDramFree(mAppBinary);
    mAppBinary = nullptr;
  }

  // Save this flag locally since it may be referenced while the system is in
  // TCM-only mode.
  if (mAppInfo != nullptr) {
    mIsTcmNanoapp = mAppInfo->isTcmNanoapp;
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
