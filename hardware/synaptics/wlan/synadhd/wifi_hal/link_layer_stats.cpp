/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Portions copyright (C) 2017 Broadcom Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdint.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <linux/rtnetlink.h>
#include <netpacket/packet.h>
#include <linux/filter.h>
#include <linux/errqueue.h>

#include <linux/pkt_sched.h>
#include <netlink/object-api.h>
#include <netlink/netlink.h>
#include <netlink/socket.h>
#include <netlink/handlers.h>

#include "sync.h"

#define LOG_TAG  "WifiHAL"

#include <utils/Log.h>

#include <hardware_legacy/wifi_hal.h>
#include "common.h"
#include "cpp_bindings.h"

typedef enum {
    ANDR_WIFI_ATTRIBUTE_INVALID        = 0,
    ANDR_WIFI_ATTRIBUTE_NUM_RADIO      = 1,
    ANDR_WIFI_ATTRIBUTE_STATS_INFO     = 2,
    ANDR_WIFI_ATTRIBUTE_STATS_MAX      = 3
} LINK_STAT_ATTRIBUTE;

/* Internal radio statistics structure in the driver */
typedef struct {
	wifi_radio radio;
	uint32_t on_time;
	uint32_t tx_time;
	uint32_t rx_time;
	uint32_t on_time_scan;
	uint32_t on_time_nbd;
	uint32_t on_time_gscan;
	uint32_t on_time_roam_scan;
	uint32_t on_time_pno_scan;
	uint32_t on_time_hs20;
	uint32_t num_channels;
	wifi_channel_stat channels[];
} wifi_radio_stat_internal;

typedef struct {
   wifi_radio radio;       // wifi radio (if multiple radio supported)
   u32 on_time;            // msecs the radio is awake
   u32 tx_time;            // msecs the radio is transmitting
   u32 num_tx_levels;      // number of radio transmit power levels
   u64 tx_time_per_levels; // pointer to an array of radio transmit per power levels in
                           // msecs accured over time
   u32 rx_time;            // msecs the radio is in active receive
   u32 on_time_scan;       // msecs the radio is awake due to all scan
   u32 on_time_nbd;        // msecs the radio is awake due to NAN
   u32 on_time_gscan;      // msecs the radio is awake due to G scan
   u32 on_time_roam_scan;  // msecs the radio is awake due to roam scan
   u32 on_time_pno_scan;   // msecs the radio is awake due to PNO scan
   u32 on_time_hs20;       // msecs the radio is awake due to HS2.0 scans and GAS exchange
   u32 num_channels;       // number of channels
   wifi_channel_stat channels[]; // channel statistics
} wifi_radio_stat_internal_v2;

enum {
    LSTATS_SUBCMD_GET_INFO = ANDROID_NL80211_SUBCMD_LSTATS_RANGE_START,
};

class GetLinkStatsCommand : public WifiCommand
{
    wifi_stats_result_handler mHandler;
public:
    GetLinkStatsCommand(wifi_interface_handle iface, wifi_stats_result_handler handler)
        : WifiCommand("GetLinkStatsCommand", iface, 0), mHandler(handler)
    { }

    virtual int create() {
        // ALOGI("Creating message to get link statistics; iface = %d", mIfaceInfo->id);

        int ret = mMsg.create(GOOGLE_OUI, LSTATS_SUBCMD_GET_INFO);
        if (ret < 0) {
            ALOGE("Failed to create %x - %d", LSTATS_SUBCMD_GET_INFO, ret);
            return ret;
        }

        return ret;
    }

protected:
    virtual int handleResponse(WifiEvent& reply) {
        void *data = NULL;
        wifi_radio_stat *radio_stat_ptr = NULL;
        u8 *iface_stat = NULL;
        u8 *radioStatsBuf = NULL, *output = NULL, *data_ptr = NULL;
        uint32_t total_size = 0, per_radio_size = 0, data_len = 0, rem_len = 0;
        int num_radios = 0, id = 0, subcmd = 0, len = 0;

        // ALOGI("In GetLinkStatsCommand::handleResponse");

        if (reply.get_cmd() != NL80211_CMD_VENDOR) {
            ALOGD("Ignoring reply with cmd = %d", reply.get_cmd());
            return NL_SKIP;
        }

        id = reply.get_vendor_id();
        subcmd = reply.get_vendor_subcmd();
        nlattr *vendor_data = reply.get_attribute(NL80211_ATTR_VENDOR_DATA);
        len = reply.get_vendor_data_len();

        ALOGV("Id = %0x, subcmd = %d, len = %d\n", id, subcmd, len);
        if (vendor_data == NULL || len == 0) {
            ALOGE("no vendor data in GetLinkStatCommand response; ignoring it");
            return NL_SKIP;
        }

        for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
            if (it.get_type() == ANDR_WIFI_ATTRIBUTE_NUM_RADIO) {
                num_radios = it.get_u32();
            } else if (it.get_type() == ANDR_WIFI_ATTRIBUTE_STATS_INFO) {
                data = it.get_data();
                data_len = it.get_len();
            } else {
                ALOGW("Ignoring invalid attribute type = %d, size = %d\n",
                it.get_type(), it.get_len());
            }
        }

        if (num_radios) {
            rem_len = MAX_CMD_RESP_BUF_LEN;
            radioStatsBuf = (u8 *)malloc(MAX_CMD_RESP_BUF_LEN);
            if (!radioStatsBuf) {
                ALOGE("No memory\n");
                return NL_SKIP;
            }
            memset(radioStatsBuf, 0, MAX_CMD_RESP_BUF_LEN);
            output = radioStatsBuf;

            if (!data || !data_len) {
                ALOGE("%s: null data\n", __func__);
                return NL_SKIP;
            }

            data_ptr = (u8*)data;
            for (int i = 0; i < num_radios; i++) {
                rem_len -= per_radio_size;
                if (rem_len < per_radio_size) {
                    ALOGE("No data left for radio %d\n", i);
                    goto exit;
                }
                data_ptr = (u8*)data + total_size;
                if (!data_ptr) {
                    ALOGE("Invalid data for radio index = %d\n", i);
                    goto exit;
                }
                radio_stat_ptr =
                    convertToExternalRadioStatStructure((wifi_radio_stat_internal_v2*)data_ptr,
                        &per_radio_size);
                if (!radio_stat_ptr || !per_radio_size) {
                    ALOGE("No data for radio %d\n", i);
                    continue;
                }
                memcpy(output, radio_stat_ptr, per_radio_size);
                output += per_radio_size;
                total_size += per_radio_size;
            }

            iface_stat = ((u8*)data + total_size);
            if (!iface_stat || data_len < total_size) {
                ALOGE("No data for iface stats!!, data_len = %d, total_size = %d\n",
                    data_len, total_size);
                goto exit;
            }
            (*mHandler.on_link_stats_results)(id, (wifi_iface_stat *)iface_stat,
            num_radios, (wifi_radio_stat *)radioStatsBuf);
        } else {
            /* To be deprecated, adding it to keep it backward compatible */
            // ALOGD("GetLinkStatCommand: zero radio case\n");
            data = reply.get_vendor_data();
            if (!data) {
                ALOGE("Invalid vendor data received\n");
                return NL_SKIP;
            }

            num_radios = 1;
            data = reply.get_vendor_data();
            len = reply.get_vendor_data_len();
            if (!data || !len) {
                ALOGE("Invalid vendor data received\n");
                return NL_SKIP;
            }
            radio_stat_ptr =
                convertToExternalRadioStatStructureLegacy((wifi_radio_stat_internal *)data);
            if (!radio_stat_ptr) {
                ALOGE("Invalid stats pointer received\n");
                return NL_SKIP;
            }
            wifi_iface_stat *iface_stat =
                (wifi_iface_stat *)((char *)&((wifi_radio_stat_internal *)data)->channels
                    + radio_stat_ptr->num_channels * sizeof(wifi_channel_stat));
            (*mHandler.on_link_stats_results)(id, iface_stat, num_radios, radio_stat_ptr);
        }
exit:
        if (radio_stat_ptr) {
            free(radio_stat_ptr);
            radio_stat_ptr = NULL;
        }
        if (radioStatsBuf) {
            free(radioStatsBuf);
            radioStatsBuf = NULL;
        }
        return NL_OK;
    }

private:
        wifi_radio_stat *convertToExternalRadioStatStructure(
                          wifi_radio_stat_internal_v2 *internal_stat_ptr,
                          uint32_t *per_radio_size) {
        wifi_radio_stat *external_stat_ptr = NULL;
        if (!internal_stat_ptr) {
            ALOGE("Incoming data is null\n");
        } else {
            uint32_t channel_size = internal_stat_ptr->num_channels * sizeof(wifi_channel_stat);
            *per_radio_size = offsetof(wifi_radio_stat_internal_v2, channels) + channel_size;
            external_stat_ptr = (wifi_radio_stat *)malloc(*per_radio_size);
            if (external_stat_ptr) {
                external_stat_ptr->radio = internal_stat_ptr->radio;
                external_stat_ptr->on_time = internal_stat_ptr->on_time;
                external_stat_ptr->tx_time = internal_stat_ptr->tx_time;
                external_stat_ptr->num_tx_levels = internal_stat_ptr->num_tx_levels;
                external_stat_ptr->tx_time_per_levels = NULL;
                external_stat_ptr->rx_time = internal_stat_ptr->rx_time;
                external_stat_ptr->on_time_scan = internal_stat_ptr->on_time_scan;
                external_stat_ptr->on_time_nbd = internal_stat_ptr->on_time_nbd;
                external_stat_ptr->on_time_gscan = internal_stat_ptr->on_time_gscan;
                external_stat_ptr->on_time_roam_scan = internal_stat_ptr->on_time_roam_scan;
                external_stat_ptr->on_time_pno_scan = internal_stat_ptr->on_time_pno_scan;
                external_stat_ptr->on_time_hs20 = internal_stat_ptr->on_time_hs20;
                external_stat_ptr->num_channels = internal_stat_ptr->num_channels;
                if (internal_stat_ptr->num_channels) {
                    memcpy(&(external_stat_ptr->channels), &(internal_stat_ptr->channels),
                        channel_size);
                }
            }
        }
        return external_stat_ptr;
    }

    wifi_radio_stat *convertToExternalRadioStatStructureLegacy(wifi_radio_stat_internal *internal_stat_ptr) {
        wifi_radio_stat *external_stat_ptr = NULL;
        if (!internal_stat_ptr) {
            ALOGE("Sta_ptr is null\n");
        } else {
            uint32_t channel_size = internal_stat_ptr->num_channels * sizeof(wifi_channel_stat);
            uint32_t total_size = sizeof(wifi_radio_stat) + channel_size;
            external_stat_ptr = (wifi_radio_stat *)malloc(total_size);
            if (external_stat_ptr) {
                external_stat_ptr->radio = internal_stat_ptr->radio;
                external_stat_ptr->on_time = internal_stat_ptr->on_time;
                external_stat_ptr->tx_time = internal_stat_ptr->tx_time;
                external_stat_ptr->rx_time = internal_stat_ptr->rx_time;
                external_stat_ptr->tx_time_per_levels = NULL;
                external_stat_ptr->num_tx_levels = 0;
                external_stat_ptr->on_time_scan = internal_stat_ptr->on_time_scan;
                external_stat_ptr->on_time_nbd = internal_stat_ptr->on_time_nbd;
                external_stat_ptr->on_time_gscan = internal_stat_ptr->on_time_gscan;
                external_stat_ptr->on_time_roam_scan = internal_stat_ptr->on_time_roam_scan;
                external_stat_ptr->on_time_pno_scan = internal_stat_ptr->on_time_pno_scan;
                external_stat_ptr->on_time_hs20 = internal_stat_ptr->on_time_hs20;
                external_stat_ptr->num_channels = internal_stat_ptr->num_channels;
                if (internal_stat_ptr->num_channels) {
                    memcpy(&(external_stat_ptr->channels), &(internal_stat_ptr->channels),
                        channel_size);
                }
            }
        }
        return external_stat_ptr;
    }
};

wifi_error wifi_get_link_stats(wifi_request_id id,
        wifi_interface_handle iface, wifi_stats_result_handler handler)
{
    GetLinkStatsCommand command(iface, handler);
    return (wifi_error) command.requestResponse();
}

wifi_error wifi_set_link_stats(
        wifi_interface_handle /* iface */, wifi_link_layer_params /* params */)
{
    /* Return success here since bcom HAL does not need set link stats. */
    return WIFI_SUCCESS;
}

wifi_error wifi_clear_link_stats(
        wifi_interface_handle /* iface */, u32 /* stats_clear_req_mask */,
        u32 * /* stats_clear_rsp_mask */, u8 /* stop_req */, u8 * /* stop_rsp */)
{
    /* Return success here since bcom HAL does not support clear link stats. */
    return WIFI_SUCCESS;
}
