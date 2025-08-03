/* Copyright (c) 2014, 2018 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "sync.h"
#define LOG_TAG  "WifiHAL"
#include <utils/Log.h>
#include <time.h>
#include <errno.h>

#include "ifaceeventhandler.h"
#include "common.h"

/* Used to handle NL command events from driver/firmware. */
IfaceEventHandlerCommand *mwifiEventHandler = NULL;

/* Set the interface event monitor handler*/
wifi_error wifi_set_iface_event_handler(wifi_request_id id,
                                        wifi_interface_handle iface,
                                        wifi_event_handler eh)
{
    wifi_handle wifiHandle = getWifiHandle(iface);

    /* Check if a similar request to set iface event handler was made earlier.
     * Right now we don't differentiate between the case where (i) the new
     * Request Id is different from the current one vs (ii) both new and
     * Request Ids are the same.
     */
    if (mwifiEventHandler)
    {
        if (id == mwifiEventHandler->get_request_id()) {
            ALOGE("%s: Iface Event Handler Set for request Id %d is still"
                "running. Exit", __func__, id);
            return WIFI_ERROR_TOO_MANY_REQUESTS;
        } else {
            ALOGE("%s: Iface Event Handler Set for a different Request "
                "Id:%d is requested. Not supported. Exit", __func__, id);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
    }

    mwifiEventHandler = new IfaceEventHandlerCommand(
                    wifiHandle,
                    id,
                    NL80211_CMD_REG_CHANGE);
    if (mwifiEventHandler == NULL) {
        ALOGE("%s: Error mwifiEventHandler NULL", __func__);
        return WIFI_ERROR_UNKNOWN;
    }
    mwifiEventHandler->setCallbackHandler(eh);

    return WIFI_SUCCESS;
}

/* Reset monitoring for the NL event*/
wifi_error wifi_reset_iface_event_handler(wifi_request_id id,
                                          wifi_interface_handle iface)
{
    if (mwifiEventHandler)
    {
        if (id == mwifiEventHandler->get_request_id()) {
            ALOGV("Delete Object mwifiEventHandler for id = %d", id);
            delete mwifiEventHandler;
            mwifiEventHandler = NULL;
        } else {
            ALOGE("%s: Iface Event Handler Set for a different Request "
                "Id:%d is requested. Not supported. Exit", __func__, id);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
    } else {
        ALOGV("Object mwifiEventHandler for id = %d already Deleted", id);
    }

    return WIFI_SUCCESS;
}

/* This function will be the main handler for the registered incoming
 * (from driver) Commads. Calls the appropriate callback handler after
 * parsing the vendor data.
 */
int IfaceEventHandlerCommand::handleEvent(WifiEvent &event)
{
    wifiEventHandler::handleEvent(event);

    switch(mSubcmd)
    {
        case NL80211_CMD_REG_CHANGE:
        {
            char code[2];
            memset(&code[0], 0, 2);
            if(tb[NL80211_ATTR_REG_ALPHA2])
            {
                memcpy(&code[0], (char *) nla_data(tb[NL80211_ATTR_REG_ALPHA2]), 2);
            } else {
                ALOGE("%s: NL80211_ATTR_REG_ALPHA2 not found", __func__);
            }
            ALOGV("Country : %c%c", code[0], code[1]);
            if(mHandler.on_country_code_changed)
            {
                mHandler.on_country_code_changed(code);
            }
        }
        break;
        default:
            ALOGV("NL Event : %d Not supported", mSubcmd);
    }

    return NL_SKIP;
}

IfaceEventHandlerCommand::IfaceEventHandlerCommand(wifi_handle handle, int id, u32 subcmd)
        : wifiEventHandler(handle, id, subcmd)
{
    ALOGV("wifiEventHandler %p constructed", this);
    registerHandler(mSubcmd);
    memset(&mHandler, 0, sizeof(wifi_event_handler));
    mEventData = NULL;
    mDataLen = 0;
}

IfaceEventHandlerCommand::~IfaceEventHandlerCommand()
{
    ALOGV("IfaceEventHandlerCommand %p destructor", this);
    unregisterHandler(mSubcmd);
}

void IfaceEventHandlerCommand::setCallbackHandler(wifi_event_handler nHandler)
{
    mHandler = nHandler;
}

int wifiEventHandler::get_request_id()
{
    return mRequestId;
}

int IfaceEventHandlerCommand::get_request_id()
{
    return wifiEventHandler::get_request_id();
}

wifiEventHandler::wifiEventHandler(wifi_handle handle, int id, u32 subcmd)
        : WifiCommand(handle, id)
{
    mRequestId = id;
    mSubcmd = subcmd;
    registerHandler(mSubcmd);
    ALOGV("wifiEventHandler %p constructed", this);
}

wifiEventHandler::~wifiEventHandler()
{
    ALOGV("wifiEventHandler %p destructor", this);
    unregisterHandler(mSubcmd);
}

int wifiEventHandler::handleEvent(WifiEvent &event)
{
    struct genlmsghdr *gnlh = event.header();
    mSubcmd = gnlh->cmd;
    nla_parse(tb, NL80211_ATTR_MAX, genlmsg_attrdata(gnlh, 0),
            genlmsg_attrlen(gnlh, 0), NULL);
    ALOGV("Got NL Event : %d from the Driver.", gnlh->cmd);

    return NL_SKIP;
}

WifihalGeneric::WifihalGeneric(wifi_handle handle, int id, u32 vendor_id,
                                  u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    hal_info *info = getHalInfo(handle);

    /* Initialize the member data variables here */
    mSet = 0;
    mSetSizeMax = 0;
    mSetSizePtr = NULL;
    mConcurrencySet = 0;
    filterVersion = 0;
    filterLength = 0;
    firmware_bus_max_size = 0;
    mCapa = &(info->capa);
    mfilter_packet_read_buffer = NULL;
    mfilter_packet_length = 0;
    res_size = 0;
    channel_buff = NULL;
    memset(&mDriverFeatures, 0, sizeof(mDriverFeatures));
    memset(&mRadarResultParams, 0, sizeof(RadarHistoryResultsParams));
}

WifihalGeneric::~WifihalGeneric()
{
    mCapa = NULL;
    if (mDriverFeatures.flags != NULL) {
        free(mDriverFeatures.flags);
        mDriverFeatures.flags = NULL;
    }
}

wifi_error WifihalGeneric::requestResponse()
{
    return WifiCommand::requestResponse(mMsg);
}

static u32 get_wifi_iftype_masks(u32 in_mask)
{
    u32 op_mask = 0;

    if (in_mask & BIT(NL80211_IFTYPE_STATION)) {
        op_mask |= BIT(WIFI_INTERFACE_STA);
        op_mask |= BIT(WIFI_INTERFACE_TDLS);
    }
    if (in_mask & BIT(NL80211_IFTYPE_AP))
        op_mask |= BIT(WIFI_INTERFACE_SOFTAP);
    if (in_mask & BIT(NL80211_IFTYPE_P2P_CLIENT))
        op_mask |= BIT(WIFI_INTERFACE_P2P_CLIENT);
    if (in_mask & BIT(NL80211_IFTYPE_P2P_GO))
        op_mask |= BIT(WIFI_INTERFACE_P2P_GO);
    if (in_mask & BIT(NL80211_IFTYPE_NAN))
        op_mask |= BIT(WIFI_INTERFACE_NAN);

    return op_mask;
}

static wifi_channel_width get_channel_width(u32 nl_width)
{
    switch(nl_width) {
    case NL80211_CHAN_WIDTH_20:
         return WIFI_CHAN_WIDTH_20;
    case NL80211_CHAN_WIDTH_40:
         return WIFI_CHAN_WIDTH_40;
    case NL80211_CHAN_WIDTH_80:
         return WIFI_CHAN_WIDTH_80;
    case NL80211_CHAN_WIDTH_160:
         return WIFI_CHAN_WIDTH_160;
    case NL80211_CHAN_WIDTH_80P80:
         return WIFI_CHAN_WIDTH_80P80;
    case NL80211_CHAN_WIDTH_5:
         return WIFI_CHAN_WIDTH_5;
    case NL80211_CHAN_WIDTH_10:
         return WIFI_CHAN_WIDTH_10;
    default:
         return WIFI_CHAN_WIDTH_INVALID;
    }
}

int WifihalGeneric::handle_response_usable_channels(struct nlattr *VendorData,
                                                    u32 mDataLen)
{
    struct nlattr *tb[QCA_WLAN_VENDOR_ATTR_USABLE_CHANNELS_MAX + 1];
    struct nlattr *curr_attr;
    wifi_usable_channel *chan_info = NULL;
    int rem;
    u32 currSize = 0;

    if (nla_parse(tb, QCA_WLAN_VENDOR_ATTR_USABLE_CHANNELS_MAX,
                  (struct nlattr *)mVendorData, mDataLen, NULL)) {
         ALOGE("Failed to parse NL channels list");
         return WIFI_ERROR_INVALID_ARGS;
    }

    if (!tb[QCA_WLAN_VENDOR_ATTR_USABLE_CHANNELS_CHAN_INFO]) {
         ALOGE("%s: USABLE_CHANNELS_CHAN_INFO not found", __FUNCTION__);
         return WIFI_ERROR_INVALID_ARGS;
    }

    for_each_nested_attribute(curr_attr,
                     tb[QCA_WLAN_VENDOR_ATTR_USABLE_CHANNELS_CHAN_INFO], rem) {
         struct nlattr *ch_info[QCA_WLAN_VENDOR_ATTR_CHAN_INFO_MAX + 1];

         if (currSize >= mSetSizeMax) {
              ALOGE("Got max channels %d completed", mSetSizeMax);
              break;
         }

         if (nla_parse_nested(ch_info, QCA_WLAN_VENDOR_ATTR_CHAN_INFO_MAX,
                              curr_attr, NULL)) {
              ALOGE("Failed to get usable channel info");
              return NL_SKIP;
         }

         chan_info = &channel_buff[currSize];
         if (!ch_info[QCA_WLAN_VENDOR_ATTR_CHAN_INFO_PRIMARY_FREQ]) {
              ALOGE("%s: CHAN_INFO_PRIMARY_FREQ not found",
                    __FUNCTION__);
              return NL_SKIP;
         }

         chan_info->freq = nla_get_u32(ch_info[
                                  QCA_WLAN_VENDOR_ATTR_CHAN_INFO_PRIMARY_FREQ]);
         if (!ch_info[QCA_WLAN_VENDOR_ATTR_CHAN_INFO_BANDWIDTH]) {
              ALOGE("%s: CHAN_INFO_BANDWIDTH not found",
                    __FUNCTION__);
              return NL_SKIP;
         }

         chan_info->width = get_channel_width(nla_get_u32(
                            ch_info[QCA_WLAN_VENDOR_ATTR_CHAN_INFO_BANDWIDTH]));
         if (!ch_info[QCA_WLAN_VENDOR_ATTR_CHAN_INFO_IFACE_MODE_MASK]) {
              ALOGE("%s: CHAN_INFO_IFACE_MODE_MASK not found",
                    __FUNCTION__);
              return NL_SKIP;
         }

         chan_info->iface_mode_mask = get_wifi_iftype_masks(nla_get_u32(
                      ch_info[QCA_WLAN_VENDOR_ATTR_CHAN_INFO_IFACE_MODE_MASK]));
         ALOGV("Primary freq %d BW %d iface mask %d", chan_info->freq,
               chan_info->width, chan_info->iface_mode_mask);
         currSize++;
    }

    res_size = currSize;
    ALOGV("%s: Result size %d", __FUNCTION__, res_size);

    return NL_SKIP;
}

int WifihalGeneric::handleResponse(WifiEvent &reply)
{
    ALOGV("Got a Wi-Fi HAL module message from Driver");
    int i = 0;
    WifiVendorCommand::handleResponse(reply);

    // Parse the vendordata and get the attribute
    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_GET_SUPPORTED_FEATURES:
            {
                struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_SET_MAX + 1];
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_FEATURE_SET_MAX,
                        (struct nlattr *)mVendorData,
                        mDataLen, NULL);

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_SET])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_FEATURE_SET not found", __func__);
                    return -EINVAL;
                }
                mSet = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_SET]);
                ALOGV("Supported feature set : %" PRIx64, mSet);

                break;
            }
        case QCA_NL80211_VENDOR_SUBCMD_GET_FEATURES:
            {
                struct nlattr *attr;
                struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_MAX + 1];
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_MAX,
                          (struct nlattr *)mVendorData, mDataLen, NULL);
                attr = tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_FLAGS];
                if (attr) {
                    int len = nla_len(attr);
                    mDriverFeatures.flags = (u8 *)malloc(len);
                    if (mDriverFeatures.flags != NULL) {
                        memcpy(mDriverFeatures.flags, nla_data(attr), len);
                        mDriverFeatures.flags_len = len;
                    }
                 }
                 break;
            }
        case QCA_NL80211_VENDOR_SUBCMD_GET_CONCURRENCY_MATRIX:
            {
                struct nlattr *tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_GET_CONCURRENCY_MATRIX_MAX + 1];
                nla_parse(tb_vendor,
                    QCA_WLAN_VENDOR_ATTR_GET_CONCURRENCY_MATRIX_MAX,
                    (struct nlattr *)mVendorData,mDataLen, NULL);

                if (tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_GET_CONCURRENCY_MATRIX_RESULTS_SET_SIZE]) {
                    u32 val;
                    val = nla_get_u32(
                        tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_GET_CONCURRENCY_MATRIX_RESULTS_SET_SIZE]);

                    ALOGV("%s: Num of concurrency combinations: %d",
                        __func__, val);
                    val = val > (unsigned int)mSetSizeMax ?
                          (unsigned int)mSetSizeMax : val;
                    *mSetSizePtr = val;

                    /* Extract the list of channels. */
                    if (*mSetSizePtr > 0 &&
                        tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_GET_CONCURRENCY_MATRIX_RESULTS_SET]) {
                        nla_memcpy(mConcurrencySet,
                            tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_GET_CONCURRENCY_MATRIX_RESULTS_SET],
                            sizeof(feature_set) * (*mSetSizePtr));
                    }

                    ALOGV("%s: Get concurrency matrix response received.",
                        __func__);
                    ALOGV("%s: Num of concurrency combinations : %d",
                        __func__, *mSetSizePtr);
                    ALOGV("%s: List of valid concurrency combinations is: ",
                        __func__);
                    for(i = 0; i < *mSetSizePtr; i++)
                    {
                        ALOGV("%" PRIx64, *(mConcurrencySet + i));
                    }
                }
            }
            break;
        case QCA_NL80211_VENDOR_SUBCMD_PACKET_FILTER:
            {
                int subCmd;
                struct nlattr *tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_MAX + 1];
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_MAX,
                        (struct nlattr *)mVendorData,
                        mDataLen, NULL);

                if (tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SUB_CMD])
                {
                    subCmd = nla_get_u32(
                           tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SUB_CMD]);
                } else {
                    /*
                     * The older drivers may not send PACKET_FILTER_SUB_CMD as
                     * they support QCA_WLAN_GET_PACKET_FILTER only.
                     */
                    subCmd = QCA_WLAN_GET_PACKET_FILTER;
                }
                if (subCmd == QCA_WLAN_GET_PACKET_FILTER) {
                    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_VERSION])
                    {
                        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_VERSION"
                              " not found", __FUNCTION__);
                        return -EINVAL;
                    }
                    filterVersion = nla_get_u32(
                           tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_VERSION]);
                    ALOGV("Current version : %u", filterVersion);

                    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SIZE])
                    {
                        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SIZE"
                              " not found", __FUNCTION__);
                        return -EINVAL;
                    }
                    filterLength = nla_get_u32(
                        tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SIZE]);
                    ALOGV("Max filter length Supported : %u", filterLength);
                } else if (subCmd == QCA_WLAN_READ_PACKET_FILTER) {

                   if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROGRAM])
                   {
                       ALOGE("%s: QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROGRAM"
                             " not found", __FUNCTION__);
                       return -EINVAL;
                   }
                   if (nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROGRAM])
                           < mfilter_packet_length)
                   {
                       ALOGE("%s: Expected packet filter length :%d but received only: %d bytes",
                             __FUNCTION__, mfilter_packet_length,
                             nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROGRAM]));
                       return -EINVAL;
                   }
                   memcpy(mfilter_packet_read_buffer,
                      nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROGRAM]),
                      mfilter_packet_length);
                   ALOGV("Filter Program length : %u", mfilter_packet_length);
                } else {
                       ALOGE("%s: Unknown APF sub command received",
                             __FUNCTION__);
                       return -EINVAL;
                }

            }
            break;
        case QCA_NL80211_VENDOR_SUBCMD_GET_BUS_SIZE:
            {
                struct nlattr *tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_DRV_INFO_MAX + 1];
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_DRV_INFO_MAX,
                          (struct nlattr *)mVendorData, mDataLen, NULL);

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_DRV_INFO_BUS_SIZE])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_DRV_INFO_BUS_SIZE"
                          " not found", __FUNCTION__);
                    return -EINVAL;
                }
                firmware_bus_max_size = nla_get_u32(
                       tb_vendor[QCA_WLAN_VENDOR_ATTR_DRV_INFO_BUS_SIZE]);
                ALOGV("Max BUS size Supported: %d", firmware_bus_max_size);
            }
            break;
        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_GET_CAPABILITIES:
            {
                struct nlattr *tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
                nla_parse(tbVendor, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
                          (struct nlattr *)mVendorData,mDataLen, NULL);

                if (wifiParseCapabilities(tbVendor) == WIFI_SUCCESS) {
                    ALOGV("%s: GSCAN Capabilities:\n"
                          "     max_ap_cache_per_scan:%d\n"
                          "     max_bssid_history_entries:%d\n"
                          "     max_hotlist_bssids:%d\n"
                          "     max_hotlist_ssids:%d\n"
                          "     max_rssi_sample_size:%d\n"
                          "     max_scan_buckets:%d\n"
                          "     max_scan_cache_size:%d\n"
                          "     max_scan_reporting_threshold:%d\n"
                          "     max_significant_wifi_change_aps:%d\n"
                          "     max_number_epno_networks:%d\n"
                          "     max_number_epno_networks_by_ssid:%d\n"
                          "     max_number_of_white_listed_ssid:%d.",
                          __FUNCTION__, mCapa->gscan_capa.max_ap_cache_per_scan,
                          mCapa->gscan_capa.max_bssid_history_entries,
                          mCapa->gscan_capa.max_hotlist_bssids,
                          mCapa->gscan_capa.max_hotlist_ssids,
                          mCapa->gscan_capa.max_rssi_sample_size,
                          mCapa->gscan_capa.max_scan_buckets,
                          mCapa->gscan_capa.max_scan_cache_size,
                          mCapa->gscan_capa.max_scan_reporting_threshold,
                          mCapa->gscan_capa.max_significant_wifi_change_aps,
                          mCapa->gscan_capa.max_number_epno_networks,
                          mCapa->gscan_capa.max_number_epno_networks_by_ssid,
                          mCapa->gscan_capa.max_number_of_white_listed_ssid);

                    ALOGV("%s: Roaming Capabilities:\n"
                          "    max_blacklist_size: %d\n"
                          "    max_whitelist_size: %d\n",
                          __FUNCTION__, mCapa->roaming_capa.max_blacklist_size,
                          mCapa->roaming_capa.max_whitelist_size);
                }
            }
            break;
        case QCA_NL80211_VENDOR_SUBCMD_USABLE_CHANNELS:
            return handle_response_usable_channels((struct nlattr *)mVendorData,
                                                   mDataLen);
        case QCA_NL80211_VENDOR_SUBCMD_GET_RADAR_HISTORY:
            {
                wifiParseRadarHistory();
            }
            break;
        case QCA_NL80211_VENDOR_SUBCMD_GET_SAR_CAPABILITY:
            {
                struct nlattr *tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_SAR_CAPABILITY_MAX + 1];
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_SAR_CAPABILITY_MAX,
                          (struct nlattr *)mVendorData,mDataLen, NULL);

                if (tb_vendor[QCA_WLAN_VENDOR_ATTR_SAR_CAPABILITY_VERSION])
                {
                    mInfo->sar_version = (qca_wlan_vendor_sar_version) nla_get_u32(tb_vendor[
                                               QCA_WLAN_VENDOR_ATTR_SAR_CAPABILITY_VERSION]);
                }
                ALOGV("%s: sar_version return %d", __func__, mInfo->sar_version);
            }
            break;
        default :
            ALOGE("%s: Wrong Wi-Fi HAL event received %d", __func__, mSubcmd);
    }
    return NL_SKIP;
}

/* Parses and extract capabilities results. */
wifi_error WifihalGeneric::wifiParseCapabilities(struct nlattr **tbVendor)
{
    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_CACHE_SIZE]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_CACHE_SIZE not found",
              __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_scan_cache_size = nla_get_u32(tbVendor[
                              QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_CACHE_SIZE]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_BUCKETS]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_BUCKETS not found",
              __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_scan_buckets = nla_get_u32(tbVendor[
                                 QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_BUCKETS]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_AP_CACHE_PER_SCAN]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_AP_CACHE_PER_SCAN not found",
              __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_ap_cache_per_scan = nla_get_u32(tbVendor[
                            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_AP_CACHE_PER_SCAN]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_RSSI_SAMPLE_SIZE]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_RSSI_SAMPLE_SIZE not found",
              __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_rssi_sample_size = nla_get_u32(tbVendor[
                             QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_RSSI_SAMPLE_SIZE]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_REPORTING_THRESHOLD]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_REPORTING_THRESHOLD not"
              " found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_scan_reporting_threshold = nla_get_u32(tbVendor[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SCAN_REPORTING_THRESHOLD]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_HOTLIST_BSSIDS]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_HOTLIST_BSSIDS not found",
              __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_hotlist_bssids = nla_get_u32(tbVendor[
                               QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_HOTLIST_BSSIDS]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SIGNIFICANT_WIFI_CHANGE_APS]
       ) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SIGNIFICANT_WIFI_CHANGE_APS "
              "not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_significant_wifi_change_aps = nla_get_u32(tbVendor[
                  QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_SIGNIFICANT_WIFI_CHANGE_APS]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_BSSID_HISTORY_ENTRIES]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_BSSID_HISTORY_ENTRIES not "
              "found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    mCapa->gscan_capa.max_bssid_history_entries = nla_get_u32(tbVendor[
                        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_BSSID_HISTORY_ENTRIES]);

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_HOTLIST_SSIDS]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_HOTLIST_SSIDS not found. Set"
              " to 0.", __FUNCTION__);
        mCapa->gscan_capa.max_hotlist_ssids = 0;
    } else {
        mCapa->gscan_capa.max_hotlist_ssids = nla_get_u32(tbVendor[
                                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_HOTLIST_SSIDS]);
    }

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_EPNO_NETS]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_EPNO_NETS not found. Set"
              " to 0.", __FUNCTION__);
        mCapa->gscan_capa.max_number_epno_networks = 0;
    } else {
        mCapa->gscan_capa.max_number_epno_networks
            = nla_get_u32(tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_EPNO_NETS
                                  ]);
    }

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_EPNO_NETS_BY_SSID]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_EPNO_NETS_BY_SSID not "
              "found. Set to 0.", __FUNCTION__);
        mCapa->gscan_capa.max_number_epno_networks_by_ssid = 0;
    } else {
        mCapa->gscan_capa.max_number_epno_networks_by_ssid = nla_get_u32(tbVendor[
                        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_EPNO_NETS_BY_SSID]);
    }

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_WHITELISTED_SSID]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_WHITELISTED_SSID not "
              "found. Set to 0.", __FUNCTION__);
        mCapa->gscan_capa.max_number_of_white_listed_ssid = 0;
        mCapa->roaming_capa.max_whitelist_size = 0;
    } else {
        mCapa->gscan_capa.max_number_of_white_listed_ssid = nla_get_u32(tbVendor[
                         QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX_NUM_WHITELISTED_SSID]);
        mCapa->roaming_capa.max_whitelist_size = mCapa->gscan_capa.max_number_of_white_listed_ssid;
    }

    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_MAX_NUM_BLACKLISTED_BSSID]) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_CAPABILITIES_MAX"
            "_NUM_BLACKLIST_BSSID not found. Set to 0.", __FUNCTION__);
        mCapa->roaming_capa.max_blacklist_size = 0;
    } else {
        mCapa->roaming_capa.max_blacklist_size = nla_get_u32(tbVendor[
                                      QCA_WLAN_VENDOR_ATTR_GSCAN_MAX_NUM_BLACKLISTED_BSSID]);
    }
    return WIFI_SUCCESS;
}

wifi_error WifihalGeneric::wifiParseRadarHistory() {
{
    // tbVendor
    struct nlattr *tbVendor[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_MAX + 1];
    int rem = 0, num_dfs_entries = 0;

    if (nla_parse(tbVendor, QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_MAX,
          (struct nlattr *)mVendorData,mDataLen, NULL)) {
        ALOGE("%s: nla_parse fail", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    if (!tbVendor[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_ENTRIES]) {
        ALOGE("%s: radar attr entries not present", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    // nested radar history
    struct nlattr *tb[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_MAX + 1];
    struct nlattr *attr;
    static struct nla_policy
      policy[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_MAX + 1] = {
            [QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_FREQ] = { .type = NLA_U32 },
            [QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_TIMESTAMP] = { .type = NLA_U64 },
            [QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_DETECTED] = { .type = NLA_FLAG },
    };
    radar_history_result *newEntry;
    radar_history_result *temp;
    u32 totalEntrySize = 0;
    u32 newEntrySize = sizeof(radar_history_result);

    nla_for_each_nested(attr,
            tbVendor[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_ENTRIES],
            rem) {
        if ((num_dfs_entries ++) > MAX_NUM_RADAR_HISTORY) {
            ALOGE("%s: exceeded max entries, drop others", __FUNCTION__);
            break;
        }
        if (nla_parse_nested(tb, QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_MAX,
                attr, policy)) {
            ALOGI("%s: nla_parse_nested fail", __FUNCTION__);
            continue;
        }
        if (!tb[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_FREQ]) {
            ALOGI("%s: radar attr freq not present", __FUNCTION__);
            continue;
        }
        if (!tb[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_TIMESTAMP]) {
            ALOGI("%s: radar attr timestamp not present", __FUNCTION__);
            continue;
        }

        // realloc buffer for new entry
        temp = (radar_history_result *) realloc(
                mRadarResultParams.entries, totalEntrySize + newEntrySize);
        if (temp == NULL) {
            ALOGE("%s: failed to realloc memory", __FUNCTION__);
            free(mRadarResultParams.entries);
            mRadarResultParams.entries = NULL;
            return WIFI_ERROR_OUT_OF_MEMORY;
        }
        mRadarResultParams.entries = temp;

        newEntry = (radar_history_result *)(
                (u8 *) mRadarResultParams.entries + totalEntrySize);
        memset(newEntry, 0, newEntrySize);
        totalEntrySize += newEntrySize;

        // save to current radar entry
        newEntry->freq = nla_get_u32(
                tb[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_FREQ]);
        newEntry->clock_boottime = nla_get_u64(
                tb[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_TIMESTAMP]);
        newEntry->radar_detected = false;
        if (tb[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_DETECTED]) {
             newEntry->radar_detected = nla_get_flag(
                     tb[QCA_WLAN_VENDOR_ATTR_RADAR_HISTORY_DETECTED]);
        }
        mRadarResultParams.num_entries ++;

        ALOGI("Radar history: freq:%d boottime: %" PRId64 " detected:%d",
                newEntry->freq,
                newEntry->clock_boottime,
                newEntry->radar_detected);
        }
    }

    return WIFI_SUCCESS;
}


void WifihalGeneric::getResponseparams(feature_set *pset)
{
    *pset = mSet;
}

void WifihalGeneric::getDriverFeatures(features_info *pfeatures)
{
    if (!pfeatures)
        return;

    if (mDriverFeatures.flags != NULL) {
        pfeatures->flags = (u8 *)malloc(mDriverFeatures.flags_len);
        if (pfeatures->flags) {
            memcpy(pfeatures->flags, mDriverFeatures.flags,
                   mDriverFeatures.flags_len);
            pfeatures->flags_len = mDriverFeatures.flags_len;
            return;
        }
    }

    pfeatures->flags_len = 0;
    pfeatures->flags = NULL;
}

void WifihalGeneric::setMaxSetSize(int set_size_max) {
    mSetSizeMax = set_size_max;
}

void WifihalGeneric::setConcurrencySet(feature_set set[]) {
    mConcurrencySet = set;
}

void WifihalGeneric::setSizePtr(int *set_size) {
    mSetSizePtr = set_size;
}

int WifihalGeneric::getFilterVersion() {
    return filterVersion;
}

int WifihalGeneric::getFilterLength() {
    return filterLength;
}
void WifihalGeneric::setPacketBufferParams(u8 *host_packet_buffer, int packet_length) {
    mfilter_packet_read_buffer = host_packet_buffer;
    mfilter_packet_length = packet_length;
}

int WifihalGeneric::getBusSize() {
    return firmware_bus_max_size;
}

void WifihalGeneric::set_channels_buff(wifi_usable_channel* channels)
{
    channel_buff = channels;
    memset(channel_buff, 0, sizeof(wifi_usable_channel) * mSetSizeMax);
}

u32 WifihalGeneric::get_results_size(void)
{
    return res_size;
}

wifi_error WifihalGeneric::wifiGetCapabilities(wifi_interface_handle handle)
{
    wifi_error ret;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(handle);

    /* Create the NL message. */
    ret = create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Failed to create NL message,  Error:%d", __FUNCTION__, ret);
        return ret;
    }

    /* Set the interface Id of the message. */
    ret = set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Failed to set interface Id of message, Error:%d", __FUNCTION__, ret);
        return ret;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        return WIFI_ERROR_OUT_OF_MEMORY;

    ret = put_u32(QCA_WLAN_VENDOR_ATTR_GSCAN_SUBCMD_CONFIG_PARAM_REQUEST_ID, mId);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Failed to add request_ID to NL command, Error:%d", __FUNCTION__, ret);
        return ret;
    }

    attr_end(nlData);

    ret = requestResponse();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: Failed to send request, Error:%d", __FUNCTION__, ret);

    return ret;
}

wifi_error WifihalGeneric::copyCachedRadarHistory(
        radar_history_result *resultBuf, int resultBufSize, int *numResults) {
    *numResults = 0;

    if (mRadarResultParams.entries) {
        radar_history_result *sEntry = NULL;
        radar_history_result *tEntry = NULL;
        u32 offset = 0;
        int i;

        for (i = 0; i < mRadarResultParams.num_entries; i ++) {
            if (resultBufSize < (offset + sizeof(radar_history_result))) {
                break;
            }

            sEntry = (radar_history_result *)(
                           (u8 *) mRadarResultParams.entries + offset);
            tEntry = (radar_history_result *)(
                           (u8 *) resultBuf + offset);
            memcpy(tEntry, sEntry, sizeof(radar_history_result));
            (*numResults) += 1;
            offset += sizeof(radar_history_result);
        }
    }

    return WIFI_SUCCESS;
}

void WifihalGeneric::freeCachedRadarHistory() {
    if (mRadarResultParams.entries) {
        free(mRadarResultParams.entries);
        mRadarResultParams.entries = NULL;
        mRadarResultParams.num_entries = 0;
    }
}

wifi_error WifihalGeneric::getSarVersion(wifi_interface_handle handle)
{
    wifi_error ret;
    interface_info *ifaceInfo = getIfaceInfo(handle);


    /* Create the NL message. */
    ret = create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Failed to create NL message,  Error:%d", __FUNCTION__, ret);
        return ret;
    }

    /* Set the interface Id of the message. */
    ret = set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Failed to set interface Id of message, Error:%d", __FUNCTION__, ret);
        return ret;
    }

    ret = requestResponse();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: Failed to send request, Error:%d", __FUNCTION__, ret);

    return ret;
}

