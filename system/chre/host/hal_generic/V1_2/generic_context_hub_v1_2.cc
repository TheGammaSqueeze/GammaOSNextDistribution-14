/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define LOG_TAG "ContextHubHal"
#define LOG_NDEBUG 0

#include "generic_context_hub_v1_2.h"

#include "context_hub_settings_util.h"
#include "permissions_util.h"

#include <chrono>
#include <cinttypes>
#include <vector>

#include <log/log.h>
#include <unistd.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_2 {
namespace implementation {

using ::android::chre::HostProtocolHost;
using ::android::hardware::Return;
using ::android::hardware::contexthub::common::implementation::getFbsSetting;
using ::android::hardware::contexthub::common::implementation::
    getFbsSettingValue;
using ::android::hardware::contexthub::common::implementation::
    kSupportedPermissions;
using ::android::hardware::contexthub::common::implementation::
    stringVectorToHidl;
using ::android::hardware::contexthub::V1_X::implementation::
    IContextHubCallbackWrapperBase;
using ::android::hardware::contexthub::V1_X::implementation::
    IContextHubCallbackWrapperV1_2;
using ::flatbuffers::FlatBufferBuilder;

using V1_0::ContextHub;
using V1_1::SettingValue;
using V1_2::IContexthub;

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

Return<void> GenericContextHubV1_2::getHubs_1_2(
    IContexthub::getHubs_1_2_cb _hidl_cb) {
  std::vector<ContextHub> retHubs;
  getHubs([&retHubs](std::vector<ContextHub> hubs) { retHubs = hubs; });
  _hidl_cb(retHubs, stringVectorToHidl(kSupportedPermissions));

  return Void();
}

Return<Result> GenericContextHubV1_2::registerCallback_1_2(
    uint32_t hubId, const sp<IContexthubCallback> &cb) {
  sp<IContextHubCallbackWrapperBase> wrappedCallback;
  if (cb != nullptr) {
    wrappedCallback = new IContextHubCallbackWrapperV1_2(cb);
  }
  return registerCallbackCommon(hubId, wrappedCallback);
}

Return<void> GenericContextHubV1_2::onSettingChanged(V1_1::Setting setting,
                                                     SettingValue newValue) {
  return onSettingChanged_1_2(reinterpret_cast<V1_2::Setting &>(setting),
                              newValue);
}

Return<void> GenericContextHubV1_2::onSettingChanged_1_2(
    Setting setting, SettingValue newValue) {
  fbs::Setting fbsSetting;
  fbs::SettingState fbsState;
  if (getFbsSetting(setting, &fbsSetting) &&
      getFbsSettingValue(newValue, &fbsState)) {
    mConnection.sendSettingChangedNotification(fbsSetting, fbsState);
  }

  return Void();
}

}  // namespace implementation
}  // namespace V1_2
}  // namespace contexthub
}  // namespace hardware
}  // namespace android
