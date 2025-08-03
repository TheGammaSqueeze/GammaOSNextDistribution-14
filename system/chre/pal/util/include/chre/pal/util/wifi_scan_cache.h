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

#ifndef CHRE_PAL_WIFI_SCAN_CACHE_H_
#define CHRE_PAL_WIFI_SCAN_CACHE_H_

/**
 * @file
 * Defines a WiFi scan caching library that may be used beneath the CHRE WiFi
 * PAL layer.
 *
 * This library should be used by the CHRE WiFi PAL implementation as a
 * convenience module to store completed WiFi scan results, and provide them to
 * CHRE as defined by the CHRE WiFi PAL API. If this library is used, there is
 * no need to invoke the chrePalWifiCallback functions related to WiFi scans
 * (e.g. scanResponseCallback()/scanEventCallback()) directly, as it will be
 * performed by the caching library.
 *
 * The memory footprint of this library can be controlled at compile-time using
 * macros. For instance, CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY can be adjusted to
 * reduce the size of the cache storage.
 *
 * @see chreWifiScanCacheScanEventBegin() for how to cache scan results.
 */

#include <stdbool.h>
#include <stdint.h>

#include "chre/pal/wifi.h"
#include "chre_api/chre/wifi.h"

#ifdef __cplusplus
extern "C" {
#endif

//! The number of chreWifiScanResult items that will be stored in the scan
//! cache library.
#ifndef CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY
#define CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY 255
#endif

//! The maximum value of chreWifiScanEvent.resultCount that will be used
//! in the scan cache library to send results to CHRE.
#ifndef CHRE_PAL_WIFI_SCAN_CACHE_MAX_RESULT_COUNT
#define CHRE_PAL_WIFI_SCAN_CACHE_MAX_RESULT_COUNT 20
#endif

/**
 * Initializes the WiFi scan cache.
 *
 * Only one scan cache library can be instantiated per compilation unit, and is
 * not thread safe.
 *
 * This method must be invoked as a result of a chrePalWiFiApi->open() call.
 *
 * @param systemApi A non-null pointer to the system API functions.
 * @param callbacks A non-null pointer to the callback functions.
 *
 * @return true if successfully initialized.
 */
bool chreWifiScanCacheInit(const struct chrePalSystemApi *systemApi,
                           const struct chrePalWifiCallbacks *callbacks);

/**
 * This method must be invoked as a result of a chrePalWifiApi->close() call.
 */
void chreWifiScanCacheDeinit(void);

/**
 * Begins the caching of results from a single WiFi scan.
 *
 * This method must be invoked as a result of a WLAN performing a WiFi scan
 * (either due to a request from CHRE or otherwise if scan monitoring is
 * enabled).
 *
 * When a WiFi scan is performed, the following is the expected flow:
 * 1. Invoke chreWifiScanCacheScanEventBegin() to start the cache.
 * 2. For each successful WiFi scan result, invoke
 * chreWifiScanCacheScanEventAdd(). If the scan is unsuccessful or no WiFi APs
 * are scanned, this step is optional.
 * 3. When the WiFi scan is completed (or failed), invoke
 * chreWifiScanCacheScanEventEnd().
 *
 * This function must not be invoked while a scan caching is currently taking
 * place (i.e. until chreWifiScanCacheScanEventEnd() is invoked).
 *
 * @param activeScanResult true if this WiFi scan was a result of an active WiFi
 * scan from CHRE (i.e. not a result of passive scan monitoring only). If true,
 * a scanResponseCallback will be invoked in chreWifiScanCacheScanEventEnd().
 *
 * @return true if the scan cache was successfully started.
 *
 * @see The fields of chreWifiScanEvent for the other parameters.
 */
bool chreWifiScanCacheScanEventBegin(enum chreWifiScanType scanType,
                                     uint8_t ssidSetSize,
                                     const uint32_t *scannedFreqList,
                                     uint16_t scannedFreqListLength,
                                     uint8_t radioChainPref,
                                     bool activeScanResult);

/**
 * Adds a WiFi scan result to the current WiFi scan event cache.
 *
 * This method must only be invoked after chreWifiScanCacheScanEventBegin()
 * and before chreWifiScanCacheScanEventEnd(), otherwise has no effect.
 * When this method is invoked, the provided result is stored in the current
 * WiFi scan cache. The cache library may drop scan results if it is out of
 * memory (decided by the CHRE_PAL_WIFI_SCAN_CACHE_CAPACITY value).
 *
 * The function does not obtain ownership of the provided pointer.
 *
 * This function must be invoked as soon as the scan result is available (i.e
 * the access point is detected). The chreWifiScanResult.ageMs field is
 * ignored by the scan cache library, and will be populated internally when
 * chreWifiScanCacheScanEventEnd() is invoked.
 *
 * @param result A non-null pointer to a WiFi scan result.
 *
 * @see chreWifiScanCacheScanEventBegin for the expected flow.
 */
void chreWifiScanCacheScanEventAdd(const struct chreWifiScanResult *result);

/**
 * Ends the caching of a single scan event.
 *
 * This method must be invoked when a WiFi scan event is fully completed. This
 * method will take the cached scan results and provide them (if any) to CHRE
 * through the chrePalWifiCallbacks provided in chreWifiScanCacheInit().
 *
 * Note that this function may be directly invoked after
 * chreWifiScanCacheScanEventBegin() without chreWifiScanCacheScanEventAdd() if
 * the scanning failed.
 *
 * @param errorCode The error code of the scan operation.
 *
 * @see chreWifiScanCacheScanEventBegin for the expected flow.
 */
void chreWifiScanCacheScanEventEnd(enum chreError errorCode);

/**
 * Checks if a new scan request from CHRE must be dispatched from the cache,
 * and dispatches them through the chrePalWifiCallbacks if appropriate.
 *
 * This method will look at the currently completed WiFi scans and checks if the
 * cache is within the maxScanAgeMs field of the scan parameter. If this method
 * returns false, the current cache does not meet the maxScanAgeMs requirement,
 * and the WLAN must perform a fresh scan.
 *
 * This method must be invoked when by the chrePalWifiApi->requestScan()
 * implementation to see if a cached WiFi scan event can be used. An example
 * usage is the following:
 *
 * if (chreWifiScanCacheDispatchFromCache(params)) {
 *   // Cached WiFi scan event was dispatched - no need to perform fresh scan
 *   // and can return a synchronous success.
 *   return true;
 * } else {
 *   // Perform WiFi scan
 * }
 *
 * @param params The chreWifiScanParams parameter of a scan request from CHRE.
 *
 * @return true if a scan was dispatched from the cache, false otherwise.
 */
bool chreWifiScanCacheDispatchFromCache(
    const struct chreWifiScanParams *params);

/**
 * Invoked to free a WiFi scan event produced by this caching library.
 *
 * This method must invoked by the chrePalWifiApi->releaseScanEvent()
 * implementation if the caching library was used to send WiFi scan events.
 */
void chreWifiScanCacheReleaseScanEvent(struct chreWifiScanEvent *event);

/**
 * Configures scan monitoring for the cache scan library.
 *
 * This method must be invoked by the chrePalWifiApi->configureScanMonitor()
 * implementation.
 *
 * If scan monitoring is enabled, the cache scan library will send WiFi scan
 * events provided through chreWifiScanCacheScanEventAdd() even if no
 * outstanding request from CHRE is present.
 *
 * @param enable true to enable WiFi scan monitoring, false otherwise.
 */
void chreWifiScanCacheConfigureScanMonitor(bool enable);

#ifdef __cplusplus
}
#endif

#endif  // CHRE_PAL_WIFI_SCAN_CACHE_H_
