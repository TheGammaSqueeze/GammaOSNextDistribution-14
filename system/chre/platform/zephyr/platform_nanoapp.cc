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

#include "chre/platform/assert.h"

namespace chre {

PlatformNanoapp::~PlatformNanoapp() {}

bool PlatformNanoapp::start() {
  bool success = false;

  if (mAppInfo != nullptr) {
    success = mAppInfo->entryPoints.start();
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
  return (mAppInfo != nullptr) ? mAppInfo->appId : mExpectedAppId;
}

uint32_t PlatformNanoapp::getAppVersion() const {
  return (mAppInfo != nullptr) ? mAppInfo->appVersion : mExpectedAppVersion;
}

uint32_t PlatformNanoapp::getTargetApiVersion() const {
  return (mAppInfo != nullptr) ? mAppInfo->targetApiVersion
                               : mExpectedTargetApiVersion;
}
bool PlatformNanoapp::isSystemNanoapp() const {
  return (mAppInfo != nullptr && mAppInfo->isSystemNanoapp);
}

bool PlatformNanoappBase::isLoaded() const {
  return mIsStatic;
}

void PlatformNanoappBase::loadStatic(const struct chreNslNanoappInfo *appInfo) {
  CHRE_ASSERT(!isLoaded());
  mIsStatic = true;
  mAppInfo = appInfo;
}

}  // namespace chre
