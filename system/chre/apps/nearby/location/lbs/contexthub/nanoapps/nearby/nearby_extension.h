#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_NEARBY_EXTENSION_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_NEARBY_EXTENSION_H_

#include <chre.h>

#ifdef __cplusplus
extern "C" {
#endif

//! Contains vendor-defined data for configuring vendor library filtering
struct chrexNearbyExtendedFilterConfig {
  size_t data_length;   //!< Number of bytes in data
  const uint8_t *data;  //!< Vendor-defined payload
};

//! Contains vendor-defined data for configuring vendor service in library
struct chrexNearbyExtendedServiceConfig {
  size_t data_length;   //!< Number of bytes in data
  const uint8_t *data;  //!< Vendor-defined payload
};

enum chrexNearbyResult {
  //! Operation completed successfully
  CHREX_NEARBY_RESULT_OK = 0,

  //! This device does not support vendor extended filtering
  CHREX_NEARBY_RESULT_FEATURE_NOT_SUPPORTED = 1,

  //! A general/unknown failure occurred while trying to perform the operation
  CHREX_NEARBY_RESULT_INTERNAL_ERROR = 2,

  //! No vendor library was found matching the Android package that made the
  //! request
  CHREX_NEARBY_RESULT_UNKNOWN_PACKAGE = 3,

  //! The system does not have enough resources available to complete the
  //! request
  CHREX_NEARBY_RESULT_OUT_OF_RESOURCES = 4,

  //! The operation failed due to an error in the vendor-specific library. Refer
  //! to
  //! the vendor status code for details.
  CHREX_NEARBY_RESULT_VENDOR_SPECIFIC_ERROR = 128,
};

#define CHREX_NEARBY_VENDOR_STATUS_UNKNOWN (0)

/**
 * Configures vendor-defined filtering sent by a vendor/OEM service on the host.
 * This is called by the Nearby nanoapp when it receives a
 * ChreNearbyExtendedFilter message, and the result is sent back to the host
 * endpoint that made the request. Note that extended filters are disabled by
 * default and automatically disabled if the vendor/OEM service disconnects from
 * ContextHubService, so the vendor/OEM service must send a configuration
 * request at initialization time to register the extended filter in the system,
 * even if there is no configuration payload. The pointers supplied for the
 * parameters are not guaranteed to be valid after this call. The OEM library
 * should perform a deep copy of the structure if we want to store them in the
 * library.
 *
 * @param host_info The meta data for a host end point that sent the message,
 *     obtained from chreHostEndpointInfo. The implementation must ensure that
 *     messages from a given host end point are only provided to the vendor
 *     library explicitly associated with that host end point.
 * @param scan_filter Contains hardware scan filter configured from the host.
 *     The library is responsible for filtering received reports using hardware
 *     filters.
 * @param config Configuration data in a vendor-defined format.
 * @param[out] vendorStatusCode Optional vendor-defined status code that will be
 *     returned to the vendor service regardless of the return value of this
 *     function. The value 0 is reserved to indicate that a vendor status code
 * was not provided or is not relevant. All other values have a vendor-defined
 *     meaning.
 * @return A value from enum chrexNearbyResult.
 */
uint32_t chrexNearbySetExtendedFilterConfig(
    const struct chreHostEndpointInfo *host_info,
    const struct chreBleScanFilter *scan_filter,
    const struct chrexNearbyExtendedFilterConfig *config,
    uint32_t *vendorStatusCode);

/**
 * Configures vendor-defined service data sent by a vendor/OEM service on the
 * host. This is called by the Nearby nanoapp when it receives a
 * ChreNearbyExtendedService message, and the result is sent back to the host
 * endpoint that made the request. The pointers supplied for the
 * parameters are not guaranteed to be valid after this call. The OEM library
 * should perform a deep copy of the structure if we want to store them in the
 * library.
 *
 * @param host_info The meta data for a host end point that sent the message,
 *     obtained from chreHostEndpointInfo. The implementation must ensure that
 *     messages from a given host end point are only provided to the vendor
 *     library explicitly associated with that host end point.
 * @param config Configuration data in a vendor-defined format.
 * @param[out] vendorStatusCode Optional vendor-defined status code that will be
 *     returned to the vendor service regardless of the return value of this
 *     function. The value 0 is reserved to indicate that a vendor status code
 * was not provided or is not relevant. All other values have a vendor-defined
 *     meaning.
 * @return A value from enum chrexNearbyResult.
 */
uint32_t chrexNearbySetExtendedServiceConfig(
    const struct chreHostEndpointInfo *host_info,
    const struct chrexNearbyExtendedServiceConfig *config,
    uint32_t *vendorStatusCode);

enum chrexNearbyFilterAction {
  //! Ignore/drop this advertising report
  CHREX_NEARBY_FILTER_ACTION_IGNORE = 0,

  //! Deliver to the vendor client when the host processor is awake, either on
  //! the
  //! next wakeup, or immediately if it is currently awake.
  //! If the host is asleep, advertisement data is temporarily stored in a
  //! buffer.
  //! If a duplicate advertisement already exists in the buffer (same sending
  //! address and payload), then it is updated rather than storing another copy.
  CHREX_NEARBY_FILTER_ACTION_DELIVER_ON_WAKE = 1,

  //! Deliver to the vendor client immediately, waking up the host processor if
  //! it
  //! is currently asleep. Triggering a wake has a power impact, so this option
  //! should be used sparingly, with special care taken to avoid repeated
  //! wakeups.
  CHREX_NEARBY_FILTER_ACTION_DELIVER_IMMEDIATELY = 2,
};

/**
 * Forwards a BLE advertisement to the extended filter associated with the given
 * package for matching. The Nearby nanoapp will call this function for each
 * package that has sent a ChreNearbyExtendedFilterConfig message and maintains
 * an active connection to ContextHubService (incl. via PendingIntent). In other
 * words, extended filtering for a given package is activated by sending
 * ChreNearbyExtendedFilterConfig to the Nearby nanoapp and deactivated when the
 * Nearby nanoapp is notified that the host endpoint has disconnected. The
 * pointers supplied for the parameters are not guaranteed to be valid after
 * this call. The OEM library should perform a deep copy of the structure if we
 * want to store them in the library.
 *
 * @param host_info The meta data for a host end point with an active extended
 *     filtering configuration, where the result will be sent if it is matched.
 * @param report Contains data for a BLE advertisement.
 * @return A value from enum chrexNearbyFilterAction.
 */
// TODO(b/305277310): Pass OEM extension API version to OEM library
uint32_t chrexNearbyMatchExtendedFilter(
    const struct chreHostEndpointInfo *host_info,
    const struct chreBleAdvertisingReport *report);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_NEARBY_EXTENSION_H_
