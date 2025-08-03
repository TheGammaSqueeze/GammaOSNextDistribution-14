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

#ifndef CHRE_UTIL_SYSTEM_BLE_UTIL_H_
#define CHRE_UTIL_SYSTEM_BLE_UTIL_H_

#include "chre_api/chre.h"

namespace chre {

/**
 * Populates a legacy chreBleAdvertisingReport's fields with values from the
 * payload. The chreBleAdvertisingReport is based on the LE Extended Advertising
 * Report Event defined in the BT Core Spec v5.3, Vol 4, Part E,
 * Section 7.7.65.13. But for legacy LE Advertising Report Events (BT Core Spec
 * v5.3, Vol 4, Part E, Section 7.7.65.2), some of these fields are only
 * included in the payload. We parse out these fields to make it easier for the
 * nanoapp to access this data.
 *
 * @param report CHRE BLE Advertising Report
 */
void populateLegacyAdvertisingReportFields(chreBleAdvertisingReport &report);

}  // namespace chre

#endif  // CHRE_UTIL_SYSTEM_BLE_UTIL_H_
