/*
 * Copyright (C) 2016 The Android Open Source Project
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

// Note that to avoid always polluting the include paths of nanoapps, we use
// symlinks under the chre_nsl_internal include path to the "real" files, e.g.
// chre_nsl_internal/platform/shared maps to the same files that would normally
// be included via chre/platform/shared

#include "chre_nsl_internal/platform/shared/nanoapp_support_lib_dso.h"

#include <algorithm>

#include "chre/util/nanoapp/log.h"
#include "chre_api/chre.h"
#include "chre_nsl_internal/platform/shared/debug_dump.h"
#include "chre_nsl_internal/util/macros.h"
#include "chre_nsl_internal/util/system/napp_permissions.h"
#ifdef CHRE_NANOAPP_USES_WIFI
#include "chre_nsl_internal/util/system/wifi_util.h"
#endif

#ifndef LOG_TAG
#define LOG_TAG "[NSL]"
#endif  // LOG_TAG

/**
 * @file
 * The Nanoapp Support Library (NSL) that gets built with nanoapps to act as an
 * intermediary to the reference CHRE implementation. It provides hooks so the
 * app can be registered with the system, and also provides a layer where we can
 * implement cross-version compatibility features as needed.
 */

namespace {

constexpr uint32_t kNanoappPermissions = 0
// DO NOT USE this macro outside of specific CHQTS nanoapps. This is only used
// to allow testing of invalid permission declarations.
#ifdef CHRE_TEST_NANOAPP_PERMS
                                         | CHRE_TEST_NANOAPP_PERMS
#else
#ifdef CHRE_NANOAPP_USES_AUDIO
    | static_cast<uint32_t>(chre::NanoappPermissions::CHRE_PERMS_AUDIO)
#endif
#ifdef CHRE_NANOAPP_USES_BLE
    | static_cast<uint32_t>(chre::NanoappPermissions::CHRE_PERMS_BLE)
#endif
#ifdef CHRE_NANOAPP_USES_GNSS
    | static_cast<uint32_t>(chre::NanoappPermissions::CHRE_PERMS_GNSS)
#endif
#ifdef CHRE_NANOAPP_USES_WIFI
    | static_cast<uint32_t>(chre::NanoappPermissions::CHRE_PERMS_WIFI)
#endif
#ifdef CHRE_NANOAPP_USES_WWAN
    | static_cast<uint32_t>(chre::NanoappPermissions::CHRE_PERMS_WWAN)
#endif
#endif  // CHRE_TEST_NANOAPP_PERMS
    ;

#if defined(CHRE_SLPI_UIMG_ENABLED) || defined(CHRE_TCM_ENABLED)
constexpr int kIsTcmNanoapp = 1;
#else
constexpr int kIsTcmNanoapp = 0;
#endif  // CHRE_SLPI_UIMG_ENABLED

#if !defined(CHRE_NANOAPP_DISABLE_BACKCOMPAT) && defined(CHRE_NANOAPP_USES_GNSS)
// Return a v1.3+ GnssLocationEvent for the nanoapp when running on a v1.2-
// platform.
chreGnssLocationEvent translateLegacyGnssLocation(
    const chreGnssLocationEvent &legacyEvent) {
  // Copy v1.2- fields over to a v1.3+ event.
  chreGnssLocationEvent newEvent = {};
  newEvent.timestamp = legacyEvent.timestamp;
  newEvent.latitude_deg_e7 = legacyEvent.latitude_deg_e7;
  newEvent.longitude_deg_e7 = legacyEvent.longitude_deg_e7;
  newEvent.altitude = legacyEvent.altitude;
  newEvent.speed = legacyEvent.speed;
  newEvent.bearing = legacyEvent.bearing;
  newEvent.accuracy = legacyEvent.accuracy;
  newEvent.flags = legacyEvent.flags;

  // Unset flags that are defined in v1.3+ but not in v1.2-.
  newEvent.flags &= ~(CHRE_GPS_LOCATION_HAS_ALTITUDE_ACCURACY |
                      CHRE_GPS_LOCATION_HAS_SPEED_ACCURACY |
                      CHRE_GPS_LOCATION_HAS_BEARING_ACCURACY);
  return newEvent;
}

void nanoappHandleEventCompat(uint32_t senderInstanceId, uint16_t eventType,
                              const void *eventData) {
  if (eventType == CHRE_EVENT_GNSS_LOCATION &&
      chreGetApiVersion() < CHRE_API_VERSION_1_3) {
    chreGnssLocationEvent event = translateLegacyGnssLocation(
        *static_cast<const chreGnssLocationEvent *>(eventData));
    nanoappHandleEvent(senderInstanceId, eventType, &event);
  } else {
    nanoappHandleEvent(senderInstanceId, eventType, eventData);
  }
}
#endif

#if !defined(CHRE_NANOAPP_DISABLE_BACKCOMPAT) && \
    defined(CHRE_NANOAPP_USES_BLE) &&            \
    defined(CHRE_FIRST_SUPPORTED_API_VERSION) && \
    CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8
void reverseServiceDataUuid(struct chreBleGenericFilter *filter) {
  if (filter->type != CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE ||
      filter->len == 0) {
    return;
  }
  std::swap(filter->data[0], filter->data[1]);
  std::swap(filter->dataMask[0], filter->dataMask[1]);
  if (filter->len == 1) {
    filter->data[0] = 0x0;
    filter->dataMask[0] = 0x0;
    filter->len = 2;
  }
}

bool serviceDataFilterEndianSwapRequired(
    const struct chreBleScanFilter *filter) {
  if (chreGetApiVersion() >= CHRE_API_VERSION_1_8 || filter == nullptr) {
    return false;
  }
  for (size_t i = 0; i < filter->scanFilterCount; i++) {
    if (filter->scanFilters[i].type ==
            CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE &&
        filter->scanFilters[i].len > 0) {
      return true;
    }
  }
  return false;
}
#endif  // !defined(CHRE_NANOAPP_DISABLE_BACKCOMPAT) &&
        // defined(CHRE_NANOAPP_USES_BLE) &&
        // defined(CHRE_FIRST_SUPPORTED_API_VERSION) &&
        // CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8

}  // anonymous namespace

//! Used to determine the given unstable ID that was provided when building this
//! nanoapp, if any. The symbol is placed in its own section so it can be
//! stripped to determine if the nanoapp changed compared to a previous version.
//! We also align the variable to match the minimum alignment of the surrounding
//! sections, since for compilers with a default size-1 alignment, there might
//! be a spill-over from the previous segment if not zero-padded, when we
//! attempt to read the string.
extern "C" DLL_EXPORT const char _chreNanoappUnstableId[]
    __attribute__((section(".unstable_id"))) __attribute__((aligned(8))) =
        NANOAPP_UNSTABLE_ID;

extern "C" DLL_EXPORT const struct chreNslNanoappInfo _chreNslDsoNanoappInfo = {
    /* magic */ CHRE_NSL_NANOAPP_INFO_MAGIC,
    /* structMinorVersion */ CHRE_NSL_NANOAPP_INFO_STRUCT_MINOR_VERSION,
    /* isSystemNanoapp */ NANOAPP_IS_SYSTEM_NANOAPP,
    /* isTcmNanoapp */ kIsTcmNanoapp,
    /* reservedFlags */ 0,
    /* reserved */ 0,
    /* targetApiVersion */ CHRE_API_VERSION,

    // These values are supplied by the build environment.
    /* vendor */ NANOAPP_VENDOR_STRING,
    /* name */ NANOAPP_NAME_STRING,
    /* appId */ NANOAPP_ID,
    /* appVersion */ NANOAPP_VERSION,
    /* entryPoints */
    {
        /* start */ nanoappStart,
#if !defined(CHRE_NANOAPP_DISABLE_BACKCOMPAT) && defined(CHRE_NANOAPP_USES_GNSS)
        /* handleEvent */ nanoappHandleEventCompat,
#else
        /* handleEvent */ nanoappHandleEvent,
#endif
        /* end */ nanoappEnd,
    },
    /* appVersionString */ _chreNanoappUnstableId,
    /* appPermissions */ kNanoappPermissions,
};

const struct chreNslNanoappInfo *getChreNslDsoNanoappInfo() {
  return &_chreNslDsoNanoappInfo;
}

// The code section below provides default implementations for new symbols
// introduced in CHRE API v1.2+ to provide binary compatibility with previous
// CHRE implementations. Note that we don't presently include symbols for v1.1,
// as the current known set of CHRE platforms that use this NSL implementation
// are all v1.1+.
// If a nanoapp knows that it is only targeting the latest platform version, it
// can define the CHRE_NANOAPP_DISABLE_BACKCOMPAT flag, so this indirection will
// be avoided at the expense of a nanoapp not being able to load at all on prior
// implementations.

#if !defined(CHRE_NANOAPP_DISABLE_BACKCOMPAT)

#if !defined(CHRE_FIRST_SUPPORTED_API_VERSION)
#error "CHRE_FIRST_SUPPORTED_API_VERSION must be defined for this platform"
#elif CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_1
#error "CHRE_FIRST_SUPPORTED_API_VERSION must be at least CHRE_API_VERSION_1_1"
#endif  // !defined(CHRE_FIRST_SUPPORTED_API_VERSION)

#include <dlfcn.h>

namespace {

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8
// Populate chreNanoappInfo for CHRE API pre v1.8.
void populateChreNanoappInfoPre18(struct chreNanoappInfo *info) {
  info->rpcServiceCount = 0;
  info->rpcServices = nullptr;
  memset(&info->reserved, 0, sizeof(info->reserved));
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8 */

}  // namespace

/**
 * Lazily calls dlsym to find the function pointer for a given function
 * (provided without quotes) in another library (i.e. the CHRE platform DSO),
 * caching and returning the result.
 */
#define CHRE_NSL_LAZY_LOOKUP(functionName)            \
  ({                                                  \
    static bool lookupPerformed = false;              \
    static decltype(functionName) *funcPtr = nullptr; \
    if (!lookupPerformed) {                           \
      funcPtr = reinterpret_cast<decltype(funcPtr)>(  \
          dlsym(RTLD_NEXT, STRINGIFY(functionName))); \
      lookupPerformed = true;                         \
    }                                                 \
    funcPtr;                                          \
  })

#ifdef CHRE_NANOAPP_USES_AUDIO

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2
WEAK_SYMBOL
bool chreAudioGetSource(uint32_t handle, struct chreAudioSource *audioSource) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreAudioGetSource);
  return (fptr != nullptr) ? fptr(handle, audioSource) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2
WEAK_SYMBOL
bool chreAudioConfigureSource(uint32_t handle, bool enable,
                              uint64_t bufferDuration,
                              uint64_t deliveryInterval) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreAudioConfigureSource);
  return (fptr != nullptr)
             ? fptr(handle, enable, bufferDuration, deliveryInterval)
             : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2
WEAK_SYMBOL
bool chreAudioGetStatus(uint32_t handle, struct chreAudioSourceStatus *status) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreAudioGetStatus);
  return (fptr != nullptr) ? fptr(handle, status) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2 */

#endif /* CHRE_NANOAPP_USES_AUDIO */

#ifdef CHRE_NANOAPP_USES_BLE

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
uint32_t chreBleGetCapabilities() {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleGetCapabilities);
  return (fptr != nullptr) ? fptr() : CHRE_BLE_CAPABILITIES_NONE;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
uint32_t chreBleGetFilterCapabilities() {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleGetFilterCapabilities);
  return (fptr != nullptr) ? fptr() : CHRE_BLE_FILTER_CAPABILITIES_NONE;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_7
WEAK_SYMBOL
bool chreBleFlushAsync(const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleFlushAsync);
  return (fptr != nullptr) ? fptr(cookie) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_7 */

// NOTE: The backward compatibility provided by this stub is only needed below
// CHRE v1.8 so we check the first API version for the platform against v1.8.
#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8
WEAK_SYMBOL
bool chreBleStartScanAsync(chreBleScanMode mode, uint32_t reportDelayMs,
                           const struct chreBleScanFilter *filter) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleStartScanAsync);
  if (fptr == nullptr) {
    return false;
  } else if (!serviceDataFilterEndianSwapRequired(filter)) {
    return fptr(mode, reportDelayMs, filter);
  }
  // For nanoapps compiled against v1.8+ working with earlier versions of CHRE,
  // convert service data filters to big-endian format.
  chreBleScanFilter convertedFilter = *filter;
  auto genericFilters = static_cast<chreBleGenericFilter *>(
      chreHeapAlloc(sizeof(chreBleGenericFilter) * filter->scanFilterCount));
  if (genericFilters == nullptr) {
    LOG_OOM();
    return false;
  }
  memcpy(genericFilters, filter->scanFilters,
         filter->scanFilterCount * sizeof(chreBleGenericFilter));
  for (size_t i = 0; i < filter->scanFilterCount; i++) {
    reverseServiceDataUuid(&genericFilters[i]);
  }
  convertedFilter.scanFilters = genericFilters;
  bool success = fptr(mode, reportDelayMs, &convertedFilter);
  chreHeapFree(const_cast<chreBleGenericFilter *>(convertedFilter.scanFilters));
  return success;
}
#endif /* CRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_9
WEAK_SYMBOL
bool chreBleStartScanAsyncV1_9(chreBleScanMode mode, uint32_t reportDelayMs,
                               const struct chreBleScanFilterV1_9 *filter,
                               const void *cookie) {
  if (chreGetApiVersion() < CHRE_API_VERSION_1_9) {
    return false;
  }
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleStartScanAsyncV1_9);
  if (fptr == nullptr) {
    return false;
  }
  return fptr(mode, reportDelayMs, filter, cookie);
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_9 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
bool chreBleStopScanAsync() {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleStopScanAsync);
  return (fptr != nullptr) ? fptr() : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_9
WEAK_SYMBOL
bool chreBleStopScanAsyncV1_9(const void *cookie) {
  if (chreGetApiVersion() < CHRE_API_VERSION_1_9) {
    return false;
  }
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleStopScanAsyncV1_9);
  if (fptr == nullptr) {
    return false;
  }
  return fptr(cookie);
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_9 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8
WEAK_SYMBOL
bool chreBleReadRssiAsync(uint16_t connectionHandle, const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleReadRssiAsync);
  return (fptr != nullptr) ? fptr(connectionHandle, cookie) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8
WEAK_SYMBOL
bool chreBleGetScanStatus(struct chreBleScanStatus *status) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreBleGetScanStatus);
  return (fptr != nullptr) ? fptr(status) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8 */

#endif /* CHRE_NANOAPP_USES_BLE */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2
WEAK_SYMBOL
void chreConfigureHostSleepStateEvents(bool enable) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreConfigureHostSleepStateEvents);
  if (fptr != nullptr) {
    fptr(enable);
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2
WEAK_SYMBOL
bool chreIsHostAwake(void) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreIsHostAwake);
  return (fptr != nullptr) ? fptr() : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2 */

#ifdef CHRE_NANOAPP_USES_GNSS

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2
WEAK_SYMBOL
bool chreGnssConfigurePassiveLocationListener(bool enable) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreGnssConfigurePassiveLocationListener);
  return (fptr != nullptr) ? fptr(enable) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2 */

#endif /* CHRE_NANOAPP_USES_GNSS */

#ifdef CHRE_NANOAPP_USES_WIFI

// NOTE: The backward compatibility provided by this stub is only needed below
// CHRE v1.5 so we check the first API version for the platform against v1.5.
#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5
WEAK_SYMBOL
bool chreWifiRequestScanAsync(const struct chreWifiScanParams *params,
                              const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreWifiRequestScanAsync);

  if (fptr == nullptr) {
    // Should never happen
    return false;
  } else if (chreGetApiVersion() < CHRE_API_VERSION_1_5) {
    const struct chreWifiScanParams legacyParams =
        chre::translateToLegacyWifiScanParams(params);
    return fptr(&legacyParams, cookie);
  } else {
    return fptr(params, cookie);
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2
WEAK_SYMBOL
bool chreWifiRequestRangingAsync(const struct chreWifiRangingParams *params,
                                 const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreWifiRequestRangingAsync);
  return (fptr != nullptr) ? fptr(params, cookie) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_2 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
bool chreWifiNanRequestRangingAsync(
    const struct chreWifiNanRangingParams *params, const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreWifiNanRequestRangingAsync);
  return (fptr != nullptr) ? fptr(params, cookie) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
bool chreWifiNanSubscribe(struct chreWifiNanSubscribeConfig *config,
                          const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreWifiNanSubscribe);
  return (fptr != nullptr) ? fptr(config, cookie) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
bool chreWifiNanSubscribeCancel(uint32_t subscriptionID) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreWifiNanSubscribeCancel);
  return (fptr != nullptr) ? fptr(subscriptionID) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#endif /* CHRE_NANOAPP_USES_WIFI */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5
WEAK_SYMBOL
bool chreSensorFind(uint8_t sensorType, uint8_t sensorIndex, uint32_t *handle) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreSensorFind);
  if (fptr != nullptr) {
    return fptr(sensorType, sensorIndex, handle);
  } else if (sensorIndex == 0) {
    return chreSensorFindDefault(sensorType, handle);
  } else {
    return false;
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_3
WEAK_SYMBOL
bool chreSensorConfigureBiasEvents(uint32_t sensorHandle, bool enable) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreSensorConfigureBiasEvents);
  return (fptr != nullptr) ? fptr(sensorHandle, enable) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_3 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_3
WEAK_SYMBOL
bool chreSensorGetThreeAxisBias(uint32_t sensorHandle,
                                struct chreSensorThreeAxisData *bias) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreSensorGetThreeAxisBias);
  return (fptr != nullptr) ? fptr(sensorHandle, bias) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_3 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_3
WEAK_SYMBOL
bool chreSensorFlushAsync(uint32_t sensorHandle, const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreSensorFlushAsync);
  return (fptr != nullptr) ? fptr(sensorHandle, cookie) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_3 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_4
WEAK_SYMBOL
void chreConfigureDebugDumpEvent(bool enable) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreConfigureDebugDumpEvent);
  if (fptr != nullptr) {
    fptr(enable);
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_4 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_4
WEAK_SYMBOL
void chreDebugDumpLog(const char *formatStr, ...) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(platform_chreDebugDumpVaLog);
  if (fptr != nullptr) {
    va_list args;
    va_start(args, formatStr);
    fptr(formatStr, args);
    va_end(args);
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_4 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5
WEAK_SYMBOL
bool chreSendMessageWithPermissions(void *message, size_t messageSize,
                                    uint32_t messageType, uint16_t hostEndpoint,
                                    uint32_t messagePermissions,
                                    chreMessageFreeFunction *freeCallback) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreSendMessageWithPermissions);
  if (fptr != nullptr) {
    return fptr(message, messageSize, messageType, hostEndpoint,
                messagePermissions, freeCallback);
  } else {
    return chreSendMessageToHostEndpoint(message, messageSize, messageType,
                                         hostEndpoint, freeCallback);
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_10
WEAK_SYMBOL
bool chreSendReliableMessageAsync(void *message, size_t messageSize,
                                  uint32_t messageType, uint16_t hostEndpoint,
                                  uint32_t messagePermissions,
                                  chreMessageFreeFunction *freeCallback,
                                  const void *cookie) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreSendReliableMessageAsync);
  if (fptr != nullptr) {
    return fptr(message, messageSize, messageType, hostEndpoint,
                messagePermissions, freeCallback, cookie);
  } else {
    return false;
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_10 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5
WEAK_SYMBOL
int8_t chreUserSettingGetState(uint8_t setting) {
  int8_t settingState = CHRE_USER_SETTING_STATE_UNKNOWN;
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreUserSettingGetState);
  if (fptr != nullptr) {
    settingState = fptr(setting);
  }
  return settingState;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5
WEAK_SYMBOL
void chreUserSettingConfigureEvents(uint8_t setting, bool enable) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreUserSettingConfigureEvents);
  if (fptr != nullptr) {
    fptr(setting, enable);
  }
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
bool chreConfigureHostEndpointNotifications(uint16_t hostEndpointId,
                                            bool enable) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreConfigureHostEndpointNotifications);
  return (fptr != nullptr) ? fptr(hostEndpointId, enable) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
bool chrePublishRpcServices(struct chreNanoappRpcService *services,
                            size_t numServices) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chrePublishRpcServices);
  return (fptr != nullptr) ? fptr(services, numServices) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6
WEAK_SYMBOL
bool chreGetHostEndpointInfo(uint16_t hostEndpointId,
                             struct chreHostEndpointInfo *info) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreGetHostEndpointInfo);
  return (fptr != nullptr) ? fptr(hostEndpointId, info) : false;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_6 */

// NOTE: The backward compatibility provided by this stub is only needed below
// CHRE v1.8 so we check the first API version for the platform against v1.8.
#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8
bool chreGetNanoappInfoByAppId(uint64_t appId, struct chreNanoappInfo *info) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreGetNanoappInfoByAppId);
  bool success = (fptr != nullptr) ? fptr(appId, info) : false;
  if (success && chreGetApiVersion() < CHRE_API_VERSION_1_8) {
    populateChreNanoappInfoPre18(info);
  }
  return success;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8 */

// NOTE: The backward compatibility provided by this stub is only needed below
// CHRE v1.8 so we check the first API version for the platform against v1.8.
#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8
bool chreGetNanoappInfoByInstanceId(uint32_t instanceId,
                                    struct chreNanoappInfo *info) {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreGetNanoappInfoByInstanceId);
  bool success = (fptr != nullptr) ? fptr(instanceId, info) : false;
  if (success && chreGetApiVersion() < CHRE_API_VERSION_1_8) {
    populateChreNanoappInfoPre18(info);
  }
  return success;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_8 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_10
WEAK_SYMBOL
uint32_t chreGetCapabilities() {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreGetCapabilities);
  return (fptr != nullptr) ? fptr() : CHRE_CAPABILITIES_NONE;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_10 */

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_10
WEAK_SYMBOL
uint32_t chreGetMessageToHostMaxSize() {
  auto *fptr = CHRE_NSL_LAZY_LOOKUP(chreGetMessageToHostMaxSize);
  return (fptr != nullptr) ? fptr() : CHRE_MESSAGE_TO_HOST_MAX_SIZE;
}
#endif /* CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_10 */

#endif  // !defined(CHRE_NANOAPP_DISABLE_BACKCOMPAT)
