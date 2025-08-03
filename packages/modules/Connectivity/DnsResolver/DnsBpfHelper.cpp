/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "DnsBpfHelper"

#include "DnsBpfHelper.h"

#include <android-base/logging.h>
#include <android-modules-utils/sdk_level.h>

namespace android {
namespace net {

#define RETURN_IF_RESULT_NOT_OK(result)                                                            \
  do {                                                                                             \
    if (!result.ok()) {                                                                            \
      LOG(ERROR) << "L" << __LINE__ << " " << __func__ << ": " << strerror(result.error().code()); \
      return result.error();                                                                       \
    }                                                                                              \
  } while (0)

base::Result<void> DnsBpfHelper::init() {
  if (!android::modules::sdklevel::IsAtLeastT()) {
    LOG(ERROR) << __func__ << ": Unsupported before Android T.";
    return base::Error(EOPNOTSUPP);
  }

  RETURN_IF_RESULT_NOT_OK(mConfigurationMap.init(CONFIGURATION_MAP_PATH));
  RETURN_IF_RESULT_NOT_OK(mUidOwnerMap.init(UID_OWNER_MAP_PATH));
  RETURN_IF_RESULT_NOT_OK(mDataSaverEnabledMap.init(DATA_SAVER_ENABLED_MAP_PATH));
  return {};
}

base::Result<bool> DnsBpfHelper::isUidNetworkingBlocked(uid_t uid, bool metered) {
  if (is_system_uid(uid)) return false;
  if (!mConfigurationMap.isValid() || !mUidOwnerMap.isValid()) {
    LOG(ERROR) << __func__
               << ": BPF maps are not ready. Forgot to call ADnsHelper_init?";
    return base::Error(EUNATCH);
  }

  auto enabledRules = mConfigurationMap.readValue(UID_RULES_CONFIGURATION_KEY);
  RETURN_IF_RESULT_NOT_OK(enabledRules);

  auto value = mUidOwnerMap.readValue(uid);
  uint32_t uidRules = value.ok() ? value.value().rule : 0;

  // For doze mode, battery saver, low power standby.
  if (isBlockedByUidRules(enabledRules.value(), uidRules)) return true;

  // For data saver.
  // DataSaverEnabled map on V+ platforms is the only reliable source of information about the
  // current data saver status. While ConnectivityService offers two ways to update this map for U
  // and V+, the U- platform implementation can have delays, potentially leading to inaccurate
  // results. Conversely, the V+ platform implementation is synchronized with the actual data saver
  // state, making it a trustworthy source. Since this library primarily serves DNS resolvers,
  // relying solely on V+ data prevents erroneous blocking of DNS queries.
  if (android::modules::sdklevel::IsAtLeastV() && metered) {
    // The background data setting (PENALTY_BOX_MATCH) and unrestricted data usage setting
    // (HAPPY_BOX_MATCH) for individual apps override the system wide Data Saver setting.
    if (uidRules & PENALTY_BOX_MATCH) return true;
    if (uidRules & HAPPY_BOX_MATCH) return false;

    auto dataSaverSetting = mDataSaverEnabledMap.readValue(DATA_SAVER_ENABLED_KEY);
    RETURN_IF_RESULT_NOT_OK(dataSaverSetting);
    return dataSaverSetting.value();
  }

  return false;
}

}  // namespace net
}  // namespace android
