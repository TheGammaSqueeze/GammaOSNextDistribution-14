#include "location/lbs/contexthub/nanoapps/nearby/nearby_extension.h"

#include <string.h>

#include "chre_api/chre.h"
#include "stddef.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][FILTER_EXTENSION]"

/**
 * Example advertisement data format.
 *
 * 0x02,  // byte length of flag
 * 0x01,  // type of ad data (flag)
 * 0x02,  // ad data (flag)
 * 0x05,  // byte length of manufacturer specific data
 * 0xff,  // type of ad data (manufacturer specific data)
 * 0xe0,  // ad data (manufacturer id[0])
 * 0x00,  // ad data (manufacturer id[1])
 * 0x78,  // ad data (manufacturer data for data filter)
 * 0x02,  // ad data (manufacturer data for delivery mode)
 */

static const uint16_t EXT_ADV_DATA_LEN = 9;
static const uint16_t EXT_ADV_DATA_FILTER_INDEX = 7;
static const uint16_t EXT_ADV_DELIVERY_MODE_INDEX = 8;
static const uint16_t EXT_FILTER_CONFIG_DATA_INDEX = 0;
static const uint16_t EXT_FILTER_CONFIG_DATA_MASK_INDEX = 1;
static uint8_t EXT_FILTER_DATA = 0;
static uint8_t EXT_FILTER_DATA_MASK = 0;
#define MAX_GENERIC_FILTER_COUNT 10
#define MAX_SERVICE_CONFIG_LEN 10

struct hwBleScanFilter {
  int8_t rssi_threshold;
  uint8_t scan_filter_count;
  struct chreBleGenericFilter scan_filters[MAX_GENERIC_FILTER_COUNT];
};
static struct hwBleScanFilter HW_SCAN_FILTER = {
    .rssi_threshold = CHRE_BLE_RSSI_THRESHOLD_NONE,
    .scan_filter_count = 0,
};
static uint8_t EXT_SERVICE_CONFIG[MAX_SERVICE_CONFIG_LEN] = {0};
const char kHostPackageName[] = "com.google.android.nearby.offload.reference";

uint32_t chrexNearbySetExtendedFilterConfig(
    const struct chreHostEndpointInfo *host_info,
    const struct chreBleScanFilter *scan_filter,
    const struct chrexNearbyExtendedFilterConfig *config,
    uint32_t *vendorStatusCode) {
  if (scan_filter == NULL ||
      scan_filter->scanFilterCount > MAX_GENERIC_FILTER_COUNT) {
    LOGE("Invalid scan_filter configuration");
    return CHREX_NEARBY_RESULT_INTERNAL_ERROR;
  }
  if (!host_info->isNameValid ||
      strcmp(host_info->packageName, kHostPackageName) != 0) {
    LOGE("Unknown package: %s", host_info->packageName);
    return CHREX_NEARBY_RESULT_UNKNOWN_PACKAGE;
  }
  // Performs a deep copy of the hardware scan filter structure.
  HW_SCAN_FILTER.rssi_threshold = scan_filter->rssiThreshold;
  HW_SCAN_FILTER.scan_filter_count = scan_filter->scanFilterCount;
  for (size_t i = 0; i < HW_SCAN_FILTER.scan_filter_count; ++i) {
    if (scan_filter->scanFilters[i].len > CHRE_BLE_DATA_LEN_MAX) {
      LOGE("Generic filter data length is too large %d",
           scan_filter->scanFilters[i].len);
      return CHREX_NEARBY_RESULT_INTERNAL_ERROR;
    }
    HW_SCAN_FILTER.scan_filters[i].type = scan_filter->scanFilters[i].type;
    HW_SCAN_FILTER.scan_filters[i].len = scan_filter->scanFilters[i].len;
    memcpy(HW_SCAN_FILTER.scan_filters[i].data,
           scan_filter->scanFilters[i].data,
           HW_SCAN_FILTER.scan_filters[i].len);
    memcpy(HW_SCAN_FILTER.scan_filters[i].dataMask,
           scan_filter->scanFilters[i].dataMask,
           HW_SCAN_FILTER.scan_filters[i].len);
    LOGD("hw scan filter[%zu]: ad type %d len %d", i,
         HW_SCAN_FILTER.scan_filters[i].type,
         HW_SCAN_FILTER.scan_filters[i].len);
  }
  EXT_FILTER_DATA = config->data[EXT_FILTER_CONFIG_DATA_INDEX];
  EXT_FILTER_DATA_MASK = config->data[EXT_FILTER_CONFIG_DATA_MASK_INDEX];
  *vendorStatusCode = 0;
  LOGD("Set EXT_FILTER_DATA 0x%02X", EXT_FILTER_DATA);
  LOGD("Set EXT_FILTER_DATA_MASK 0x%02X", EXT_FILTER_DATA_MASK);
  return CHREX_NEARBY_RESULT_OK;
}

uint32_t chrexNearbySetExtendedServiceConfig(
    const struct chreHostEndpointInfo *host_info,
    const struct chrexNearbyExtendedServiceConfig *config,
    uint32_t *vendorStatusCode) {
  if (!host_info->isNameValid ||
      strcmp(host_info->packageName, kHostPackageName) != 0) {
    LOGE("Unknown package: %s", host_info->packageName);
    return CHREX_NEARBY_RESULT_UNKNOWN_PACKAGE;
  }
  if (config->data_length > MAX_SERVICE_CONFIG_LEN) {
    return CHREX_NEARBY_RESULT_OUT_OF_RESOURCES;
  }
  // Performs a deep copy of the service configuration.
  memcpy(EXT_SERVICE_CONFIG, config->data, config->data_length);
  *vendorStatusCode = 0;
  LOGD("Set EXT_SERVICE_CONFIG 0x%02X", EXT_SERVICE_CONFIG[0]);
  return CHREX_NEARBY_RESULT_OK;
}

uint32_t chrexNearbyMatchExtendedFilter(
    const struct chreHostEndpointInfo *host_info,
    const struct chreBleAdvertisingReport *report) {
  if (!host_info->isNameValid ||
      strcmp(host_info->packageName, kHostPackageName) != 0 ||
      report->dataLength == 0) {
    return CHREX_NEARBY_FILTER_ACTION_IGNORE;
  }
  if (report->dataLength < EXT_ADV_DATA_LEN) {
    LOGD("data length %d is less than expected", report->dataLength);
    return CHREX_NEARBY_FILTER_ACTION_IGNORE;
  }
  uint8_t extData = report->data[EXT_ADV_DATA_FILTER_INDEX];
  int8_t deliveryMode = (int8_t)(report->data[EXT_ADV_DELIVERY_MODE_INDEX]);
  if ((extData & EXT_FILTER_DATA_MASK) !=
      (EXT_FILTER_DATA & EXT_FILTER_DATA_MASK)) {
    return CHREX_NEARBY_FILTER_ACTION_IGNORE;
  }
  switch (deliveryMode) {
    case CHREX_NEARBY_FILTER_ACTION_DELIVER_ON_WAKE:
      return CHREX_NEARBY_FILTER_ACTION_DELIVER_ON_WAKE;
    case CHREX_NEARBY_FILTER_ACTION_DELIVER_IMMEDIATELY:
      return CHREX_NEARBY_FILTER_ACTION_DELIVER_IMMEDIATELY;
    default:
      return CHREX_NEARBY_FILTER_ACTION_IGNORE;
  }
}
