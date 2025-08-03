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

#include <dlfcn.h>

#include "location/lbs/contexthub/nanoapps/nearby/nearby_extension.h"
#include "third_party/contexthub/chre/util/include/chre/util/macros.h"

/**
 * Lazily calls dlsym to find the function pointer for a given function
 * (provided without quotes) in another library (i.e. the CHRE platform DSO),
 * caching and returning the result.
 */
#define CHRE_NSL_LAZY_LOOKUP(functionName)               \
  ({                                                     \
    static bool lookupPerformed = false;                 \
    static decltype(functionName) *funcPtr = nullptr;    \
    if (!lookupPerformed) {                              \
      funcPtr = reinterpret_cast<decltype(funcPtr)>(     \
          dlsym(RTLD_DEFAULT, STRINGIFY(functionName))); \
      lookupPerformed = true;                            \
    }                                                    \
    funcPtr;                                             \
  })

WEAK_SYMBOL
uint32_t chrexNearbySetExtendedFilterConfig(
    const struct chreHostEndpointInfo *host_info,
    const struct chreBleScanFilter *scan_filter,
    const struct chrexNearbyExtendedFilterConfig *config,
    uint32_t *vendorStatusCode) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chrexNearbySetExtendedFilterConfig);
  return (fptr != nullptr)
             ? fptr(host_info, scan_filter, config, vendorStatusCode)
             : chrexNearbyResult::CHREX_NEARBY_RESULT_FEATURE_NOT_SUPPORTED;
}

WEAK_SYMBOL
uint32_t chrexNearbySetExtendedServiceConfig(
    const struct chreHostEndpointInfo *host_info,
    const struct chrexNearbyExtendedServiceConfig *config,
    uint32_t *vendorStatusCode) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chrexNearbySetExtendedServiceConfig);
  return (fptr != nullptr)
             ? fptr(host_info, config, vendorStatusCode)
             : chrexNearbyResult::CHREX_NEARBY_RESULT_FEATURE_NOT_SUPPORTED;
}

WEAK_SYMBOL
uint32_t chrexNearbyMatchExtendedFilter(
    const struct chreHostEndpointInfo *host_info,
    const struct chreBleAdvertisingReport *report) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chrexNearbyMatchExtendedFilter);
  return (fptr != nullptr)
             ? fptr(host_info, report)
             : chrexNearbyFilterAction::CHREX_NEARBY_FILTER_ACTION_IGNORE;
}
