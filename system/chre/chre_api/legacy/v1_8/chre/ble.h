/*
 * Copyright (C) 2021 The Android Open Source Project
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
#ifndef CHRE_BLE_H_
#define CHRE_BLE_H_

/**
 * @file
 * CHRE BLE (Bluetooth Low Energy, Bluetooth LE) API.
 * The CHRE BLE API currently supports BLE scanning features.
 *
 * The features in the CHRE BLE API are a subset and adaptation of Android
 * capabilities as described in the Android BLE API and HCI requirements.
 * ref:
 * https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview
 * ref: https://source.android.com/devices/bluetooth/hci_requirements
 */

#include <chre/common.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * The set of flags returned by chreBleGetCapabilities().
 *
 * @defgroup CHRE_BLE_CAPABILITIES
 * @{
 */
//! No BLE APIs are supported
#define CHRE_BLE_CAPABILITIES_NONE UINT32_C(0)

//! CHRE supports BLE scanning
#define CHRE_BLE_CAPABILITIES_SCAN UINT32_C(1 << 0)

//! CHRE BLE supports batching of scan results, either through Android-specific
//! HCI (OCF: 0x156), or by the CHRE framework, internally.
//! @since v1.7 Platforms with this capability must also support flushing scan
//! results during a batched scan.
#define CHRE_BLE_CAPABILITIES_SCAN_RESULT_BATCHING UINT32_C(1 << 1)

//! CHRE BLE scan supports best-effort hardware filtering. If filtering is
//! available, chreBleGetFilterCapabilities() returns a bitmap indicating the
//! specific filtering capabilities that are supported.
//! To differentiate best-effort vs. no filtering, the following requirement
//! must be met for this flag:
//! If only one nanoapp is requesting BLE scans and there are no BLE scans from
//! the AP, only filtered results will be provided to the nanoapp.
#define CHRE_BLE_CAPABILITIES_SCAN_FILTER_BEST_EFFORT UINT32_C(1 << 2)

//! CHRE BLE supports reading the RSSI of a specified LE-ACL connection handle.
#define CHRE_BLE_CAPABILITIES_READ_RSSI UINT32_C(1 << 3)
/** @} */

/**
 * The set of flags returned by chreBleGetFilterCapabilities().
 *
 * The representative bit for each filtering capability is based on the sub-OCF
 * of the Android filtering HCI vendor-specific command (LE_APCF_Command, OCF:
 * 0x0157) for that particular filtering capability, as found in
 * https://source.android.com/devices/bluetooth/hci_requirements
 *
 * For example, the Service Data filter has a sub-command of 0x7; hence
 * the filtering capability is indicated by (1 << 0x7).
 *
 * @defgroup CHRE_BLE_FILTER_CAPABILITIES
 * @{
 */
//! No CHRE BLE filters are supported
#define CHRE_BLE_FILTER_CAPABILITIES_NONE UINT32_C(0)

//! CHRE BLE supports RSSI filters
#define CHRE_BLE_FILTER_CAPABILITIES_RSSI UINT32_C(1 << 1)

//! CHRE BLE supports Manufacturer Data filters (Corresponding HCI OCF: 0x0157,
//! Sub-command: 0x06)
//! @since v1.8
#define CHRE_BLE_FILTER_CAPABILITIES_MANUFACTURER_DATA UINT32_C(1 << 6)

//! CHRE BLE supports Service Data filters (Corresponding HCI OCF: 0x0157,
//! Sub-command: 0x07)
#define CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA UINT32_C(1 << 7)
/** @} */

/**
 * Produce an event ID in the block of IDs reserved for BLE.
 *
 * Valid input range is [0, 15]. Do not add new events with ID > 15
 * (see chre/event.h)
 *
 * @param offset Index into BLE event ID block; valid range is [0, 15].
 *
 * @defgroup CHRE_BLE_EVENT_ID
 * @{
 */
#define CHRE_BLE_EVENT_ID(offset) (CHRE_EVENT_BLE_FIRST_EVENT + (offset))

/**
 * nanoappHandleEvent argument: struct chreAsyncResult
 *
 * Communicates the asynchronous result of a request to the BLE API. The
 * requestType field in {@link #chreAsyncResult} is set to a value from enum
 * chreBleRequestType.
 *
 * This is used for results of async config operations which need to
 * interop with lower level code (potentially in a different thread) or send an
 * HCI command to the FW and wait on the response.
 */
#define CHRE_EVENT_BLE_ASYNC_RESULT CHRE_BLE_EVENT_ID(0)

/**
 * nanoappHandleEvent argument: struct chreBleAdvertisementEvent
 *
 * Provides results of a BLE scan.
 */
#define CHRE_EVENT_BLE_ADVERTISEMENT CHRE_BLE_EVENT_ID(1)

/**
 * nanoappHandleEvent argument: struct chreAsyncResult
 *
 * Indicates that a flush request made via chreBleFlushAsync() is complete, and
 * all batched advertisements resulting from the flush have been delivered via
 * preceding CHRE_EVENT_BLE_ADVERTISEMENT events.
 *
 * @since v1.7
 */
#define CHRE_EVENT_BLE_FLUSH_COMPLETE CHRE_BLE_EVENT_ID(2)

/**
 * nanoappHandleEvent argument: struct chreBleReadRssiEvent
 *
 * Provides the RSSI of an LE ACL connection following a call to
 * chreBleReadRssiAsync().
 *
 * @since v1.8
 */
#define CHRE_EVENT_BLE_RSSI_READ CHRE_BLE_EVENT_ID(3)

/**
 * nanoappHandleEvent argument: struct chreBatchCompleteEvent
 *
 * This event is generated if the platform enabled batching, and when all
 * events in a single batch has been delivered (for example, batching
 * CHRE_EVENT_BLE_ADVERTISEMENT events if the platform has
 * CHRE_BLE_CAPABILITIES_SCAN_RESULT_BATCHING enabled, and a non-zero
 * reportDelayMs in chreBleStartScanAsync() was accepted).
 *
 * If the nanoapp receives a CHRE_EVENT_BLE_SCAN_STATUS_CHANGE with a non-zero
 * reportDelayMs and enabled set to true, then this event must be generated.
 *
 * @since v1.8
 */
#define CHRE_EVENT_BLE_BATCH_COMPLETE CHRE_BLE_EVENT_ID(4)

/**
 * nanoappHandleEvent argument: struct chreBleScanStatus
 *
 * This event is generated when the values in chreBleScanStatus changes.
 *
 * @since v1.8
 */
#define CHRE_EVENT_BLE_SCAN_STATUS_CHANGE CHRE_BLE_EVENT_ID(5)

// NOTE: Do not add new events with ID > 15
/** @} */

/**
 * Maximum BLE (legacy) advertisement payload data length, in bytes
 * This is calculated by subtracting 2 (type + len) from 31 (max payload).
 */
#define CHRE_BLE_DATA_LEN_MAX (29)

/**
 * BLE device address length, in bytes.
 */
#define CHRE_BLE_ADDRESS_LEN (6)

/**
 * RSSI value (int8_t) indicating no RSSI threshold.
 */
#define CHRE_BLE_RSSI_THRESHOLD_NONE (-128)

/**
 * RSSI value (int8_t) indicating no RSSI value available.
 */
#define CHRE_BLE_RSSI_NONE (127)

/**
 * Tx power value (int8_t) indicating no Tx power value available.
 */
#define CHRE_BLE_TX_POWER_NONE (127)

/**
 * Indicates ADI field was not provided in advertisement.
 */
#define CHRE_BLE_ADI_NONE (0xFF)

/**
 * The CHRE BLE advertising event type is based on the BT Core Spec v5.2,
 * Vol 4, Part E, Section 7.7.65.13, LE Extended Advertising Report event,
 * Event_Type.
 *
 * Note: helper functions are provided to avoid bugs, e.g. a nanoapp doing
 * (eventTypeAndDataStatus == ADV_IND) instead of properly masking off reserved
 * and irrelevant bits.
 *
 * @defgroup CHRE_BLE_EVENT
 * @{
 */
// Extended event types
#define CHRE_BLE_EVENT_MASK_TYPE (0x1f)
#define CHRE_BLE_EVENT_TYPE_FLAG_CONNECTABLE (1 << 0)
#define CHRE_BLE_EVENT_TYPE_FLAG_SCANNABLE (1 << 1)
#define CHRE_BLE_EVENT_TYPE_FLAG_DIRECTED (1 << 2)
#define CHRE_BLE_EVENT_TYPE_FLAG_SCAN_RSP (1 << 3)
#define CHRE_BLE_EVENT_TYPE_FLAG_LEGACY (1 << 4)

// Data status
#define CHRE_BLE_EVENT_MASK_DATA_STATUS (0x3 << 5)
#define CHRE_BLE_EVENT_DATA_STATUS_COMPLETE (0x0 << 5)
#define CHRE_BLE_EVENT_DATA_STATUS_MORE_DATA_PENDING (0x1 << 5)
#define CHRE_BLE_EVENT_DATA_STATUS_DATA_TRUNCATED (0x2 << 5)

// Legacy event types
#define CHRE_BLE_EVENT_TYPE_LEGACY_ADV_IND                                  \
  (CHRE_BLE_EVENT_TYPE_FLAG_LEGACY | CHRE_BLE_EVENT_TYPE_FLAG_CONNECTABLE | \
   CHRE_BLE_EVENT_TYPE_FLAG_SCANNABLE)
#define CHRE_BLE_EVENT_TYPE_LEGACY_DIRECT_IND \
  (CHRE_BLE_EVENT_TYPE_FLAG_LEGACY | CHRE_BLE_EVENT_TYPE_FLAG_CONNECTABLE)
#define CHRE_BLE_EVENT_TYPE_LEGACY_ADV_SCAN_IND \
  (CHRE_BLE_EVENT_TYPE_FLAG_LEGACY | CHRE_BLE_EVENT_TYPE_FLAG_SCANNABLE)
#define CHRE_BLE_EVENT_TYPE_LEGACY_ADV_NONCONN_IND \
  (CHRE_BLE_EVENT_TYPE_FLAG_LEGACY)
#define CHRE_BLE_EVENT_TYPE_LEGACY_SCAN_RESP_ADV_IND \
  (CHRE_BLE_EVENT_TYPE_FLAG_SCAN_RSP | CHRE_BLE_EVENT_TYPE_LEGACY_ADV_IND)
#define CHRE_BLE_EVENT_TYPE_LEGACY_SCAN_RESP_ADV_SCAN_IND \
  (CHRE_BLE_EVENT_TYPE_FLAG_SCAN_RSP | CHRE_BLE_EVENT_TYPE_LEGACY_ADV_SCAN_IND)
/** @} */

/**
 * The maximum amount of time allowed to elapse between the call to
 * chreBleFlushAsync() and when CHRE_EVENT_BLE_FLUSH_COMPLETE is delivered to
 * the nanoapp on a successful flush.
 */
#define CHRE_BLE_FLUSH_COMPLETE_TIMEOUT_NS (5 * CHRE_NSEC_PER_SEC)

/**
 * Indicates a type of request made in this API. Used to populate the resultType
 * field of struct chreAsyncResult sent with CHRE_EVENT_BLE_ASYNC_RESULT.
 */
enum chreBleRequestType {
  CHRE_BLE_REQUEST_TYPE_START_SCAN = 1,
  CHRE_BLE_REQUEST_TYPE_STOP_SCAN = 2,
  CHRE_BLE_REQUEST_TYPE_FLUSH = 3,      //!< @since v1.7
  CHRE_BLE_REQUEST_TYPE_READ_RSSI = 4,  //!< @since v1.8
};

/**
 * CHRE BLE scan modes identify functional scan levels without specifying or
 * guaranteeing particular scan parameters (e.g. duty cycle, interval, radio
 * chain).
 *
 * The actual scan parameters may be platform dependent and may change without
 * notice in real time based on contextual cues, etc.
 *
 * Scan modes should be selected based on use cases as described.
 */
enum chreBleScanMode {
  //! A background scan level for always-running ambient applications.
  //! A representative duty cycle may be between 3 - 10 % (tentative, and
  //! with no guarantees).
  CHRE_BLE_SCAN_MODE_BACKGROUND = 1,

  //! A foreground scan level to be used for short periods.
  //! A representative duty cycle may be between 10 - 20 % (tentative, and
  //! with no guarantees).
  CHRE_BLE_SCAN_MODE_FOREGROUND = 2,

  //! A very high duty cycle scan level to be used for very short durations.
  //! A representative duty cycle may be between 50 - 100 % (tentative, and
  //! with no guarantees).
  CHRE_BLE_SCAN_MODE_AGGRESSIVE = 3,
};

/**
 * Selected AD Types are available among those defined in the Bluetooth spec.
 * Assigned Numbers, Generic Access Profile.
 * ref: https://www.bluetooth.com/specifications/assigned-numbers/
 */
enum chreBleAdType {
  //! Service Data with 16-bit UUID
  //! @deprecated as of v1.8
  //! TODO(b/285207430): Remove this enum once CHRE has been updated.
  CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16 = 0x16,

  //! Service Data with 16-bit UUID
  //! @since v1.8 CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16 was renamed
  //! CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE to reflect that nanoapps
  //! compiled against v1.8+ should use OTA format for service data filters.
  CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE = 0x16,

  //! Manufacturer Specific Data
  //! @since v1.8
  CHRE_BLE_AD_TYPE_MANUFACTURER_DATA = 0xff,
};

/**
 * Generic scan filters definition based on AD Type, mask, and values. The
 * maximum data length is limited to the maximum possible legacy advertisement
 * payload data length (29 bytes).
 *
 * The filter is matched when
 *   data & dataMask == advData & dataMask
 * where advData is the advertisement packet data for the specified AD type.
 *
 * The CHRE generic filter structure represents a generic filter on an AD Type
 * as defined in the Bluetooth spec Assigned Numbers, Generic Access Profile
 * (ref: https://www.bluetooth.com/specifications/assigned-numbers/). This
 * generic structure is used by the Advertising Packet Content Filter
 * (APCF) HCI generic AD type sub-command 0x09 (ref:
 * https://source.android.com/devices/bluetooth/hci_requirements#le_apcf_command).
 *
 * Note that the CHRE implementation may not support every kind of filter that
 * can be represented by this structure. Use chreBleGetFilterCapabilities() to
 * discover supported filtering capabilities at runtime.
 *
 * @since v1.8 The data and dataMask must be in OTA format. The nanoapp support
 * library will handle converting the data and dataMask values to the correct
 * format if a pre v1.8 version of CHRE is running.
 *
 * NOTE: CHRE versions 1.6 and 1.7 expect the 2-byte UUID prefix in
 * CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16 to be given as big-endian. This
 * was corrected in v1.8 to match the OTA format and Bluetooth specification,
 * which uses little-endian. This enum has been removed and replaced with
 * CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16_LE to ensure that nanoapps
 * compiled against v1.8 and later specify their UUID filter using
 * little-endian. Nanoapps compiled against v1.7 or earlier should continue to
 * use big-endian, as CHRE must provide cross-version compatibility for all
 * possible version combinations.
 *
 * Example 1: To filter on a 16 bit service data UUID of 0xFE2C, the following
 * settings would be used:
 *   type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16
 *   len = 2
 *   data = {0x2C, 0xFE}
 *   dataMask = {0xFF, 0xFF}
 *
 * Example 2: To filter for manufacturer data of 0x12, 0x34 from Google (0x00E0),
 * the following settings would be used:
 *   type = CHRE_BLE_AD_TYPE_MANUFACTURER_DATA
 *   len = 4
 *   data = {0xE0, 0x00, 0x12, 0x34}
 *   dataMask = {0xFF, 0xFF, 0xFF, 0xFF}
 *
 * Refer to "Supplement to the Bluetooth Core Specification for details (v9,
 * Part A, Section 1.4)" for details regarding the manufacturer data format.
 */
struct chreBleGenericFilter {
  //! Acceptable values among enum chreBleAdType
  uint8_t type;

  /**
   * Length of data and dataMask. AD payloads shorter than this length will not
   * be matched by the filter. Length must be greater than 0.
   */
  uint8_t len;

  //! Used in combination with dataMask to filter an advertisement
  uint8_t data[CHRE_BLE_DATA_LEN_MAX];

  //! Used in combination with data to filter an advertisement
  uint8_t dataMask[CHRE_BLE_DATA_LEN_MAX];
};

/**
 * CHRE Bluetooth LE scan filters are based on a combination of an RSSI
 * threshold and generic scan filters as defined by AD Type, mask, and values.
 *
 * CHRE-provided filters are implemented in a best-effort manner, depending on
 * HW capabilities of the system and available resources. Therefore, provided
 * scan results may be a superset of the specified filters. Nanoapps should try
 * to take advantage of CHRE scan filters as much as possible, but must design
 * their logic as to not depend on CHRE filtering.
 *
 * The syntax of CHRE scan filter definitions are based on the Android
 * Advertising Packet Content Filter (APCF) HCI requirement subtype 0x08
 * ref:
 * https://source.android.com/devices/bluetooth/hci_requirements#le_apcf_command-set_filtering_parameters_sub_cmd
 * and AD Types as defined in the Bluetooth spec Assigned Numbers, Generic
 * Access Profile
 * ref: https://www.bluetooth.com/specifications/assigned-numbers/
 *
 * Even though the scan filters are defined in a generic manner, CHRE Bluetooth
 * is expected to initially support only a limited set of AD Types.
 */
struct chreBleScanFilter {
  //! RSSI threshold filter (Corresponding HCI OCF: 0x0157, Sub: 0x01), where
  //! advertisements with RSSI values below this threshold may be disregarded.
  //! An rssiThreshold value of CHRE_BLE_RSSI_THRESHOLD_NONE indicates no RSSI
  //! filtering.
  int8_t rssiThreshold;

  //! Number of generic scan filters provided in the scanFilters array.
  //! A scanFilterCount value of 0 indicates no generic scan filters.
  uint8_t scanFilterCount;

  //! Pointer to an array of scan filters. If the array contains more than one
  //! entry, advertisements matching any of the entries will be returned
  //! (functional OR).
  const struct chreBleGenericFilter *scanFilters;
};

/**
 * CHRE BLE advertising address type is based on the BT Core Spec v5.2, Vol 4,
 * Part E, Section 7.7.65.13, LE Extended Advertising Report event,
 * Address_Type.
 */
enum chreBleAddressType {
  //! Public device address.
  CHRE_BLE_ADDRESS_TYPE_PUBLIC = 0x00,

  //! Random device address.
  CHRE_BLE_ADDRESS_TYPE_RANDOM = 0x01,

  //! Public identity address (corresponds to resolved private address).
  CHRE_BLE_ADDRESS_TYPE_PUBLIC_IDENTITY = 0x02,

  //! Random (static) Identity Address (corresponds to resolved private
  //! address)
  CHRE_BLE_ADDRESS_TYPE_RANDOM_IDENTITY = 0x03,

  //! No address provided (anonymous advertisement).
  CHRE_BLE_ADDRESS_TYPE_NONE = 0xff,
};

/**
 * CHRE BLE physical (PHY) channel encoding type, if supported, is based on the
 * BT Core Spec v5.2, Vol 4, Part E, Section 7.7.65.13, LE Extended Advertising
 * Report event, entries Primary_PHY and Secondary_PHY.
 */
enum chreBlePhyType {
  //! No packets on this PHY (only on the secondary channel), or feature not
  //! supported.
  CHRE_BLE_PHY_NONE = 0x00,

  //! LE 1 MBPS PHY encoding.
  CHRE_BLE_PHY_1M = 0x01,

  //! LE 2 MBPS PHY encoding (only on the secondary channel).
  CHRE_BLE_PHY_2M = 0x02,

  //! LE long-range coded PHY encoding.
  CHRE_BLE_PHY_CODED = 0x03,
};

/**
 * The CHRE BLE Advertising Report event is based on the BT Core Spec v5.2,
 * Vol 4, Part E, Section 7.7.65.13, LE Extended Advertising Report event, with
 * the following differences:
 *
 * 1) A CHRE timestamp field, which can be useful if CHRE is batching results.
 * 2) Reordering of the rssi and periodicAdvertisingInterval fields for memory
 *    alignment (prevent padding).
 * 3) Addition of four reserved bytes to reclaim padding.
 */
struct chreBleAdvertisingReport {
  //! The base timestamp, in nanoseconds, in the same time base as chreGetTime()
  uint64_t timestamp;

  //! @see CHRE_BLE_EVENT
  uint8_t eventTypeAndDataStatus;

  //! Advertising address type as defined in enum chreBleAddressType
  uint8_t addressType;

  //! Advertising device address
  uint8_t address[CHRE_BLE_ADDRESS_LEN];

  //! Advertiser PHY on primary advertising physical channel, if supported, as
  //! defined in enum chreBlePhyType.
  uint8_t primaryPhy;

  //! Advertiser PHY on secondary advertising physical channel, if supported, as
  //! defined in enum chreBlePhyType.
  uint8_t secondaryPhy;

  //! Value of the Advertising SID subfield in the ADI field of the PDU among
  //! the range of [0, 0x0f].
  //! CHRE_BLE_ADI_NONE indicates no ADI field was provided.
  //! Other values are reserved.
  uint8_t advertisingSid;

  //! Transmit (Tx) power in dBm. Typical values are [-127, 20].
  //! CHRE_BLE_TX_POWER_NONE indicates Tx power not available.
  int8_t txPower;

  //! Interval of the periodic advertising in 1.25 ms intervals, i.e.
  //! time = periodicAdvertisingInterval * 1.25 ms
  //! 0 means no periodic advertising. Minimum value is otherwise 6 (7.5 ms).
  uint16_t periodicAdvertisingInterval;

  //! RSSI in dBm. Typical values are [-127, 20].
  //! CHRE_BLE_RSSI_NONE indicates RSSI is not available.
  int8_t rssi;

  //! Direct address type (i.e. only accept connection requests from a known
  //! peer device) as defined in enum chreBleAddressType.
  uint8_t directAddressType;

  //! Direct address (i.e. only accept connection requests from a known peer
  //! device).
  uint8_t directAddress[CHRE_BLE_ADDRESS_LEN];

  //! Length of data field. Acceptable range is [0, 62] for legacy and
  //! [0, 255] for extended advertisements.
  uint16_t dataLength;

  //! dataLength bytes of data, or null if dataLength is 0. This represents
  //! the ADV_IND payload, optionally concatenated with SCAN_RSP, as indicated
  //! by eventTypeAndDataStatus.
  const uint8_t *data;

  //! Reserved for future use; set to 0
  uint32_t reserved;
};

/**
 * A CHRE BLE Advertising Event can contain any number of CHRE BLE Advertising
 * Reports (i.e. advertisements).
 */
struct chreBleAdvertisementEvent {
  //! Reserved for future use; set to 0
  uint16_t reserved;

  //! Number of advertising reports in this event
  uint16_t numReports;

  //! Array of length numReports
  const struct chreBleAdvertisingReport *reports;
};

/**
 * The RSSI read on a particular LE connection handle, based on the parameters
 * in BT Core Spec v5.3, Vol 4, Part E, Section 7.5.4, Read RSSI command
 */
struct chreBleReadRssiEvent {
  //! Structure which contains the cookie associated with the original request,
  //! along with an error code that indicates request success or failure.
  struct chreAsyncResult result;

  //! The handle upon which CHRE attempted to read RSSI.
  uint16_t connectionHandle;

  //! The RSSI of the last packet received on this connection, if valid
  //! (-127 to 20)
  int8_t rssi;
};

/**
 * Describes the current status of the BLE request in the platform.
 *
 * @since v1.8
 */
struct chreBleScanStatus {
  //! The currently configured report delay in the scan configuration.
  //! If enabled is false, this value does not have meaning.
  uint32_t reportDelayMs;

  //! True if the BLE scan is currently enabled. This can be set to false
  //! if BLE scan was temporarily disabled (e.g. BT subsystem is down,
  //! or due to user settings).
  bool enabled;

  //! Reserved for future use - set to zero.
  uint8_t reserved[3];
};

/**
 * Retrieves a set of flags indicating the BLE features supported by the
 * current CHRE implementation. The value returned by this function must be
 * consistent for the entire duration of the nanoapp's execution.
 *
 * The client must allow for more flags to be set in this response than it knows
 * about, for example if the implementation supports a newer version of the API
 * than the client was compiled against.
 *
 * @return A bitmask with zero or more CHRE_BLE_CAPABILITIES_* flags set. @see
 *         CHRE_BLE_CAPABILITIES
 *
 * @since v1.6
 */
uint32_t chreBleGetCapabilities(void);

/**
 * Retrieves a set of flags indicating the BLE filtering features supported by
 * the current CHRE implementation. The value returned by this function must be
 * consistent for the entire duration of the nanoapp's execution.
 *
 * The client must allow for more flags to be set in this response than it knows
 * about, for example if the implementation supports a newer version of the API
 * than the client was compiled against.
 *
 * @return A bitmask with zero or more CHRE_BLE_FILTER_CAPABILITIES_* flags set.
 *         @see CHRE_BLE_FILTER_CAPABILITIES
 *
 * @since v1.6
 */
uint32_t chreBleGetFilterCapabilities(void);

/**
 * Helper function to extract event type from eventTypeAndDataStatus as defined
 * in the BT Core Spec v5.2, Vol 4, Part E, Section 7.7.65.13, LE Extended
 * Advertising Report event, entry Event_Type.
 *
 * @see CHRE_BLE_EVENT
 *
 * @param eventTypeAndDataStatus Combined event type and data status
 *
 * @return The event type portion of eventTypeAndDataStatus
 */
static inline uint8_t chreBleGetEventType(uint8_t eventTypeAndDataStatus) {
  return (eventTypeAndDataStatus & CHRE_BLE_EVENT_MASK_TYPE);
}

/**
 * Helper function to extract data status from eventTypeAndDataStatus as defined
 * in the BT Core Spec v5.2, Vol 4, Part E, Section 7.7.65.13, LE Extended
 * Advertising Report event, entry Event_Type.
 *
 * @see CHRE_BLE_EVENT
 *
 * @param eventTypeAndDataStatus Combined event type and data status
 *
 * @return The data status portion of eventTypeAndDataStatus
 */
static inline uint8_t chreBleGetDataStatus(uint8_t eventTypeAndDataStatus) {
  return (eventTypeAndDataStatus & CHRE_BLE_EVENT_MASK_DATA_STATUS);
}

/**
 * Helper function to to combine an event type with a data status to create
 * eventTypeAndDataStatus as defined in the BT Core Spec v5.2, Vol 4, Part E,
 * Section 7.7.65.13, LE Extended Advertising Report event, entry Event_Type.
 *
 * @see CHRE_BLE_EVENT
 *
 * @param eventType Event type
 * @param dataStatus Data status
 *
 * @return A combined eventTypeAndDataStatus
 */
static inline uint8_t chreBleGetEventTypeAndDataStatus(uint8_t eventType,
                                                       uint8_t dataStatus) {
  return ((eventType & CHRE_BLE_EVENT_MASK_TYPE) |
          (dataStatus & CHRE_BLE_EVENT_MASK_DATA_STATUS));
}

/**
 * Nanoapps must define CHRE_NANOAPP_USES_BLE somewhere in their build
 * system (e.g. Makefile) if the nanoapp needs to use the following BLE APIs.
 * In addition to allowing access to these APIs, defining this macro will also
 * ensure CHRE enforces that all host clients this nanoapp talks to have the
 * required Android permissions needed to access BLE functionality by adding
 * metadata to the nanoapp.
 */
#if defined(CHRE_NANOAPP_USES_BLE) || !defined(CHRE_IS_NANOAPP_BUILD)

/**
 * Start Bluetooth LE (BLE) scanning on CHRE.
 *
 * The result of the operation will be delivered asynchronously via the CHRE
 * event CHRE_EVENT_BLE_ASYNC_RESULT.
 *
 * The scan results will be delivered asynchronously via the CHRE event
 * CHRE_EVENT_BLE_ADVERTISEMENT.
 *
 * If CHRE_USER_SETTING_BLE_AVAILABLE is disabled, CHRE is expected to return an
 * async result with error CHRE_ERROR_FUNCTION_DISABLED. If this setting is
 * enabled, the Bluetooth subsystem may still be powered down in the scenario
 * where the main Bluetooth toggle is disabled, but the Bluetooth scanning
 * setting is enabled, and there is no request for BLE to be enabled at the
 * Android level. In this scenario, CHRE will return an async result with error
 * CHRE_ERROR_FUNCTION_DISABLED.
 *
 * To ensure that Bluetooth remains powered on in this settings configuration so
 * that a nanoapp can scan, the nanoapp's Android host entity should use the
 * BluetoothAdapter.enableBLE() API to register this request with the Android
 * Bluetooth stack.
 *
 * If chreBleStartScanAsync() is called while a previous scan has been started,
 * the previous scan will be stopped first and replaced with the new scan.
 *
 * Note that some corresponding Android parameters are missing from the CHRE
 * API, where the following default or typical parameters are used:
 * Callback type: CALLBACK_TYPE_ALL_MATCHES
 * Result type: SCAN_RESULT_TYPE_FULL
 * Match mode: MATCH_MODE_AGGRESSIVE
 * Number of matches per filter: MATCH_NUM_MAX_ADVERTISEMENT
 * Legacy-only: false
 * PHY type: PHY_LE_ALL_SUPPORTED
 *
 * For v1.8 and greater, a CHRE_EVENT_BLE_SCAN_STATUS_CHANGE will be generated
 * if the values in chreBleScanStatus changes as a result of this call.
 *
 * @param mode Scanning mode selected among enum chreBleScanMode
 * @param reportDelayMs Maximum requested batching delay in ms. 0 indicates no
 *                      batching. Note that the system may deliver results
 *                      before the maximum specified delay is reached.
 * @param filter Pointer to the requested best-effort filter configuration as
 *               defined by struct chreBleScanFilter. The ownership of filter
 *               and its nested elements remains with the caller, and the caller
 *               may release it as soon as chreBleStartScanAsync() returns.
 *
 * @return True to indicate that the request was accepted. False otherwise.
 *
 * @since v1.6
 */
bool chreBleStartScanAsync(enum chreBleScanMode mode, uint32_t reportDelayMs,
                           const struct chreBleScanFilter *filter);
/**
 * Stops a CHRE BLE scan.
 *
 * The result of the operation will be delivered asynchronously via the CHRE
 * event CHRE_EVENT_BLE_ASYNC_RESULT.
 *
 * @return True to indicate that the request was accepted. False otherwise.
 *
 * @since v1.6
 */
bool chreBleStopScanAsync(void);

/**
 * Requests to immediately deliver batched scan results. The nanoapp must
 * have an active BLE scan request. If a request is accepted, it will be treated
 * as though the reportDelayMs has expired for a batched scan. Upon accepting
 * the request, CHRE works to immediately deliver scan results currently kept in
 * batching memory, if any, via regular CHRE_EVENT_BLE_ADVERTISEMENT events,
 * followed by a CHRE_EVENT_BLE_FLUSH_COMPLETE event.
 *
 * If the underlying system fails to complete the flush operation within
 * CHRE_BLE_FLUSH_COMPLETE_TIMEOUT_NS, CHRE will send a
 * CHRE_EVENT_BLE_FLUSH_COMPLETE event with CHRE_ERROR_TIMEOUT.
 *
 * If multiple flush requests are made prior to flush completion, then the
 * requesting nanoapp will receive all batched samples existing at the time of
 * the latest flush request. In this case, the number of
 * CHRE_EVENT_BLE_FLUSH_COMPLETE events received must equal the number of flush
 * requests made.
 *
 * If chreBleStopScanAsync() is called while a flush operation is in progress,
 * it is unspecified whether the flush operation will complete successfully or
 * return an error, such as CHRE_ERROR_FUNCTION_DISABLED, but in any case,
 * CHRE_EVENT_BLE_FLUSH_COMPLETE must still be delivered. The same applies if
 * the Bluetooth user setting is disabled during a flush operation.
 *
 * If called while running on a CHRE API version below v1.7, this function
 * returns false and has no effect.
 *
 * @param cookie An opaque value that will be included in the chreAsyncResult
 *               sent as a response to this request.
 *
 * @return True to indicate the request was accepted. False otherwise.
 *
 * @since v1.7
 */
bool chreBleFlushAsync(const void *cookie);

/**
 * Requests to read the RSSI of a peer device on the given LE connection
 * handle.
 *
 * If the request is accepted, the response will be delivered in a
 * CHRE_EVENT_BLE_RSSI_READ event with the same cookie.
 *
 * The request may be rejected if resources are not available to service the
 * request (such as if too many outstanding requests already exist). If so, the
 * client may retry later.
 *
 * Note that the connectionHandle is valid only while the connection remains
 * active. If a peer device disconnects then reconnects, the handle may change.
 * BluetoothDevice#getConnectionHandle() can be used from the Android framework
 * to get the latest handle upon reconnection.
 *
 * @param connectionHandle
 * @param cookie An opaque value that will be included in the chreAsyncResult
 *               embedded in the response to this request.
 * @return True if the request has been accepted and dispatched to the
 *         controller. False otherwise.
 *
 * @since v1.8
 *
 */
bool chreBleReadRssiAsync(uint16_t connectionHandle, const void *cookie);

/**
 * Retrieves the current state of the BLE scan on the platform.
 *
 * @param status A non-null pointer to where the scan status will be
 *               populated.
 *
 * @return True if the status was obtained successfully.
 *
 * @since v1.8
 */
bool chreBleGetScanStatus(struct chreBleScanStatus *status);

/**
 * Definitions for handling unsupported CHRE BLE scenarios.
 */
#else  // defined(CHRE_NANOAPP_USES_BLE) || !defined(CHRE_IS_NANOAPP_BUILD)

#define CHRE_BLE_PERM_ERROR_STRING                                       \
  "CHRE_NANOAPP_USES_BLE must be defined when building this nanoapp in " \
  "order to refer to "

#define chreBleStartScanAsync(...) \
  CHRE_BUILD_ERROR(CHRE_BLE_PERM_ERROR_STRING "chreBleStartScanAsync")

#define chreBleStopScanAsync(...) \
  CHRE_BUILD_ERROR(CHRE_BLE_PERM_ERROR_STRING "chreBleStopScanAsync")

#define chreBleFlushAsync(...) \
  CHRE_BUILD_ERROR(CHRE_BLE_PERM_ERROR_STRING "chreBleFlushAsync")

#define chreBleReadRssiAsync(...) \
  CHRE_BUILD_ERROR(CHRE_BLE_PERM_ERROR_STRING "chreBleReadRssiAsync")

#endif  // defined(CHRE_NANOAPP_USES_BLE) || !defined(CHRE_IS_NANOAPP_BUILD)

#ifdef __cplusplus
}
#endif

#endif /* CHRE_BLE_H_ */
