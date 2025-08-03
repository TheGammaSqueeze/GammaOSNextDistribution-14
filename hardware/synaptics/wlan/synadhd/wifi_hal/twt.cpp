/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Portions copyright (C) 2020 Broadcom Limited
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

static const char *TwtCmdToString(int cmd);
static void EventGetAttributeData(u8 sub_event_type, nlattr *vendor_data);
typedef void *TwtRequest;

#define C2S(x)  case x: return #x;

typedef struct _twt_hal_info {
    void *twt_handle;
    void *twt_feature_request;
} twt_hal_info_t;

twt_hal_info_t twt_info;

#define TWT_HANDLE(twt_info)                  ((twt_info).twt_handle)
#define GET_TWT_HANDLE(twt_info)              ((TwtHandle *)twt_info.twt_handle)

#define WL_TWT_CAP_FLAGS_REQ_SUPPORT    (1u << 0u)
#define WL_TWT_CAP_FLAGS_RESP_SUPPORT   (1u << 1u)
#define WL_TWT_CAP_FLAGS_BTWT_SUPPORT   (1u << 2u)
#define WL_TWT_CAP_FLAGS_FLEX_SUPPORT   (1u << 3u)

class TwtHandle
{
    public:
        TwtCallbackHandler mHandlers;
        TwtHandle(wifi_handle handle, TwtCallbackHandler handlers):mHandlers(handlers)
    {}

};


static const char *TwtCmdToString(int cmd)
{
    switch (cmd) {
        C2S(TWT_SETUP_REQUEST);
        C2S(TWT_INFO_FRAME_REQUEST);
        C2S(TWT_TEAR_DOWN_REQUEST);
        default:
    return "UNKNOWN_NAN_CMD";
    }
}

static bool is_twt_sub_event(int sub_event_type)
{
    bool is_twt_event = false;
    switch (sub_event_type) {
        case TWT_SETUP_RESPONSE:
        case TWT_TEARDOWN_COMPLETION:
        case TWT_INFORM_FRAME:
        case TWT_NOTIFY:
            is_twt_event = true;
    }
    return is_twt_event;
}

void EventGetAttributeData(u8 sub_event_type, nlattr *vendor_data)
{
    u8 attr_type = 0;

    switch (sub_event_type) {
        case TWT_SETUP_RESPONSE:
            TwtSetupResponse setup_response;
            for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                attr_type = it.get_type();
                switch (attr_type) {
                    case TWT_ATTRIBUTE_CONFIG_ID:
                        ALOGI("config_id = %u\n", it.get_u8());
                        setup_response.config_id = it.get_u8();
                        break;
                    case TWT_ATTRIBUTE_NEG_TYPE:
                        ALOGI("neg type = %u\n", it.get_u8());
                        setup_response.negotiation_type = it.get_u8();
                        break;
                    case TWT_ATTRIBUTE_REASON_CODE:
                        setup_response.reason_code = (TwtSetupReasonCode)it.get_u8();
                        ALOGI("reason code = %u\n", setup_response.reason_code);
                        break;
                    case TWT_ATTRIBUTE_STATUS:
                        setup_response.status = it.get_u8();
                        ALOGI("status = %u\n", setup_response.status);
                        break;
                    case TWT_ATTRIBUTE_TRIGGER_TYPE:
                        setup_response.trigger_type = it.get_u8();
                        ALOGI("trigger type = %u\n", setup_response.trigger_type);
                        break;
                    case TWT_ATTRIBUTE_WAKE_DUR_US:
                        setup_response.wake_dur_us = it.get_u32();
                        ALOGI("wake_dur_us = %d\n", setup_response.wake_dur_us);
                        break;
                    case TWT_ATTRIBUTE_WAKE_INT_US:
                        setup_response.wake_int_us = it.get_u32();
                        ALOGI("wake_int_us = %d\n", setup_response.wake_int_us);
                        break;
                     case TWT_ATTRIBUTE_WAKE_TIME_OFF_US:
                         setup_response.wake_time_off_us = it.get_u32();
                         ALOGI("wake_time_off_us = %d\n", setup_response.wake_time_off_us);
                         break;
                     default:
                         if (attr_type != TWT_ATTRIBUTE_SUB_EVENT) {
                             ALOGE("Unknown attr_type: %d\n", attr_type);
                         }
                         break;
                }
            }
            GET_TWT_HANDLE(twt_info)->mHandlers.EventTwtSetupResponse(&setup_response);
            break;
        case TWT_TEARDOWN_COMPLETION:
            TwtTeardownCompletion teardown_event;
            for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                attr_type = it.get_type();
                switch (attr_type) {
                    case TWT_ATTRIBUTE_CONFIG_ID:
                        ALOGI("config_id = %u\n", it.get_u8());
                        teardown_event.config_id = it.get_u8();
                        break;
                    case TWT_ATTRIBUTE_STATUS:
                        teardown_event.status = it.get_u8();
                        ALOGI("status = %u\n", teardown_event.status);
                        break;
                    case TWT_ATTRIBUTE_ALL_TWT:
                        teardown_event.all_twt = it.get_u8();
                        ALOGI("all_twt = %d\n", teardown_event.all_twt);
                        break;
                    case TWT_ATTRIBUTE_REASON_CODE:
                        teardown_event.reason = (TwtTeardownReason)it.get_u8();
                        ALOGI("reason = %u\n", teardown_event.reason);
                        break;
                    default:
                        if (attr_type != TWT_ATTRIBUTE_SUB_EVENT) {
                            ALOGE("Unknown attr_type: %d\n", attr_type);
                        }
                        break;
                }
            }
            GET_TWT_HANDLE(twt_info)->mHandlers.EventTwtTeardownCompletion(&teardown_event);
            break;
        case TWT_INFORM_FRAME:
            TwtInfoFrameReceived info_frame_event;
            for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                attr_type = it.get_type();
                switch (attr_type) {
                    case TWT_ATTRIBUTE_CONFIG_ID:
                        ALOGI("config_id = %u\n", it.get_u8());
                        info_frame_event.config_id = it.get_u8();
                        break;
                    case TWT_ATTRIBUTE_REASON_CODE:
                        info_frame_event.reason = (TwtInfoFrameReason)it.get_u8();
                        ALOGI("reason = %u\n", info_frame_event.reason);
                        break;
                    case TWT_ATTRIBUTE_STATUS:
                        info_frame_event.status = it.get_u8();
                        ALOGI("status = %u\n", info_frame_event.status);
                        break;
                    case TWT_ATTRIBUTE_ALL_TWT:
                        info_frame_event.all_twt = it.get_u8();
                        ALOGI("all_twt = %d\n", info_frame_event.all_twt);
                        break;
                    case TWT_ATTRIBUTE_RESUMED:
                        info_frame_event.twt_resumed = it.get_u8();
                        ALOGI("twt_resumed = %u\n", info_frame_event.twt_resumed);
                        break;
                    default:
                        if (attr_type != TWT_ATTRIBUTE_SUB_EVENT) {
                            ALOGE("Unknown attr_type: %d\n", attr_type);
                        }
                        break;
                }
            }
            GET_TWT_HANDLE(twt_info)->mHandlers.EventTwtInfoFrameReceived(&info_frame_event);
            break;
        case TWT_NOTIFY:
            TwtDeviceNotify notif_event;
            for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                attr_type = it.get_type();
                switch (attr_type) {
                    case TWT_ATTRIBUTE_NOTIFICATION:
                        notif_event.notification = (TwtNotification)it.get_u8();
                        ALOGI("notification = %u\n", notif_event.notification);
                        break;
                    default:
                        if (attr_type != TWT_ATTRIBUTE_SUB_EVENT) {
                            ALOGE("Unknown attr_type: %d\n", attr_type);
                        }
                        break;
                }
            }
            GET_TWT_HANDLE(twt_info)->mHandlers.EventTwtDeviceNotify(&notif_event);
            break;
        default:
            ALOGE("Unknown event_type: %d\n", sub_event_type);
            break;
    }
    return;
}

void HandleTwtEvent(nlattr *vendor_data) {
    u8 sub_event_type = 0;
    u8 event_type = 0;

    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
        event_type = it.get_type();
        if (event_type == TWT_ATTRIBUTE_SUB_EVENT) {
            sub_event_type = it.get_u8();
            if (is_twt_sub_event(sub_event_type)) {
                EventGetAttributeData(sub_event_type, vendor_data);
            }
        }
    }
    return;
}

class TwtEventCap : public WifiCommand
{
    public:
        TwtEventCap(wifi_interface_handle iface, int id)
            : WifiCommand("TwtCommand", iface, id)
        {}

        int start()
        {
            registerTwtVendorEvents();
            return WIFI_SUCCESS;
        }

        int handleResponse(WifiEvent& reply) {
            return NL_SKIP;
        }

        void registerTwtVendorEvents()
        {
            registerVendorHandler(BRCM_OUI, BRCM_VENDOR_EVENT_TWT);
        }

        void unregisterTwtVendorEvents()
        {
            unregisterVendorHandler(BRCM_OUI, BRCM_VENDOR_EVENT_TWT);
        }

        int handleEvent(WifiEvent& event) {
            u16 attr_type;
            TwtEventType twt_event;

            nlattr *vendor_data = event.get_attribute(NL80211_ATTR_VENDOR_DATA);
            int len = event.get_vendor_data_len();
            int event_id = event.get_vendor_subcmd();

            ALOGI("EventCapture: Received TWT event: %d\n", event_id);
            if (!vendor_data || len == 0) {
                ALOGE("No event data found");
                return NL_SKIP;
            }

            switch (event_id) {
                case BRCM_VENDOR_EVENT_TWT: {
                    ALOGE("Handle TWT event: %d\n", event_id);
                    HandleTwtEvent(vendor_data);
                    break;
                }
                default:
                    break;
            }
            return NL_SKIP;
        }
};

/* To see event prints in console */
wifi_error twt_event_check_request(transaction_id id, wifi_interface_handle iface)
{
    TwtEventCap *cmd = new TwtEventCap(iface, id);
    if (cmd == NULL) {
        return WIFI_ERROR_NOT_SUPPORTED;
    }
    return (wifi_error)cmd->start();
}

//////////////////////////////////////////////////////////////////////////
class GetTwtCapabilitiesCommand : public WifiCommand
{
    TwtCapabilitySet *mCapabilities;
public:
    GetTwtCapabilitiesCommand(wifi_interface_handle iface, TwtCapabilitySet *capabilities)
        : WifiCommand("GetTwtCapabilitiesCommand", iface, 0), mCapabilities(capabilities)
    {
        memset(mCapabilities, 0, sizeof(*mCapabilities));
    }

    virtual int create() {
        ALOGD("Creating message to get twt capabilities; iface\n");

        int ret = mMsg.create(GOOGLE_OUI, TWT_SUBCMD_GETCAPABILITY);
        if (ret < 0) {
            ALOGE("Failed to send the twt cap cmd, err = %d\n", ret);
        }
        ALOGD("Success to send twt cap cmd, err = %d\n", ret);
        return ret;
    }

private:
    TwtCapability parseTwtCap(uint32_t twt_peer_cap) {
        TwtCapability cap;
        cap.requester_supported = (twt_peer_cap & WL_TWT_CAP_FLAGS_REQ_SUPPORT) ? 1 : 0;
        cap.responder_supported = (twt_peer_cap & WL_TWT_CAP_FLAGS_RESP_SUPPORT) ? 1 : 0;
        cap.broadcast_twt_supported = (twt_peer_cap & WL_TWT_CAP_FLAGS_BTWT_SUPPORT) ? 1 : 0;
        cap.flexibile_twt_supported = (twt_peer_cap & WL_TWT_CAP_FLAGS_FLEX_SUPPORT) ? 1 : 0;
        return cap;
    }

protected:
    virtual int handleResponse(WifiEvent& reply) {

        ALOGI("In GetTwtCapabilitiesCommand::handleResponse");

        if (reply.get_cmd() != NL80211_CMD_VENDOR) {
            ALOGD("Ignoring reply with cmd = %d", reply.get_cmd());
            return NL_SKIP;
        }

        int id = reply.get_vendor_id();
        int subcmd = reply.get_vendor_subcmd();
        uint32_t twt_device_cap, twt_peer_cap;

        nlattr *data = reply.get_attribute(NL80211_ATTR_VENDOR_DATA);
        int len = reply.get_vendor_data_len();

        ALOGD("Id = %0x, subcmd = %d, len = %d", id, subcmd, len);
        if (data == NULL || len == 0) {
            ALOGE("no vendor data in GetTwtCapabilitiesCommand response; ignoring it\n");
            return NL_SKIP;
        }

        for (nl_iterator it(data); it.has_next(); it.next()) {
            switch (it.get_type()) {
                case TWT_ATTRIBUTE_DEVICE_CAP:
                    twt_device_cap = it.get_u32();
                    mCapabilities->device_capability = parseTwtCap(twt_device_cap);
                    break;
                case TWT_ATTRIBUTE_PEER_CAP:
                    twt_peer_cap = it.get_u32();
                    mCapabilities->peer_capability = parseTwtCap(twt_peer_cap);
                    break;
                default:
                    ALOGE("Ignoring invalid attribute type = %d, size = %d\n",
                            it.get_type(), it.get_len());
                    break;
            }
        }

        ALOGE("Out GetTwtCapabilitiesCommand::handleResponse\n");
        return NL_OK;
    }
};

/* API to get TWT capability */
wifi_error twt_get_capability(wifi_interface_handle iface,
        TwtCapabilitySet *twt_cap_set)
{
    if (iface == NULL) {
        ALOGE("twt_get_capability: NULL iface pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (twt_cap_set == NULL) {
        ALOGE("twt_get_capability: NULL capabilities pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    GetTwtCapabilitiesCommand command(iface, twt_cap_set);
    return (wifi_error) command.requestResponse();
}

//////////////////////////////////////////////////////////////////////////
class GetTwtStatsCommand : public WifiCommand
{
    TwtStats* mStats;
    u8 mConfig_id;
public:
    GetTwtStatsCommand(wifi_interface_handle iface, u8 config_id, TwtStats *stats)
        : WifiCommand("GetTwtStatsCommand", iface, 0), mConfig_id(config_id), mStats(stats)
    {
        memset(mStats, 0, sizeof(*mStats));
    }

    virtual int create() {
        ALOGD("Creating message to get twt stats; iface = %d", mIfaceInfo->id);

        int ret = mMsg.create(GOOGLE_OUI, TWT_SUBCMD_GETSTATS);
        if (ret < 0) {
            return ret;
        }

        nlattr *data = mMsg.attr_start(NL80211_ATTR_VENDOR_DATA);
        ret = mMsg.put_u8(TWT_ATTRIBUTE_CONFIG_ID, mConfig_id);
        if (ret < 0) {
             ALOGE("Failed to set mConfig_id %d\n", mConfig_id);
             return ret;
        }

        ALOGI("Successfully configured config id %d\n", mConfig_id);
        mMsg.attr_end(data);
        return WIFI_SUCCESS;
    }

protected:
    virtual int handleResponse(WifiEvent& reply) {

        ALOGI("In GetTwtStatsCommand::handleResponse");

        if (reply.get_cmd() != NL80211_CMD_VENDOR) {
            ALOGD("Ignoring reply with cmd = %d", reply.get_cmd());
            return NL_SKIP;
        }

        int id = reply.get_vendor_id();
        int subcmd = reply.get_vendor_subcmd();

        nlattr *data = reply.get_attribute(NL80211_ATTR_VENDOR_DATA);
        int len = reply.get_vendor_data_len();

        ALOGD("Id = %0x, subcmd = %d, len = %d", id, subcmd, len);
        if (data == NULL || len == 0) {
            ALOGE("no vendor data in GetTwtStatsCommand response; ignoring it\n");
            return NL_SKIP;
        }

        for (nl_iterator it(data); it.has_next(); it.next()) {
            switch (it.get_type()) {
                case TWT_ATTRIBUTE_CONFIG_ID:
                    mStats->config_id = it.get_u8();
                    break;
                case TWT_ATTRIBUTE_AVG_PKT_NUM_TX:
                    mStats->avg_pkt_num_tx = it.get_u32();
                    break;
                case TWT_ATTRIBUTE_AVG_PKT_NUM_RX:
                    mStats->avg_pkt_num_rx = it.get_u32();
                    break;
                case TWT_ATTRIBUTE_AVG_PKT_SIZE_TX:
                    mStats->avg_tx_pkt_size = it.get_u32();
                    break;
                case TWT_ATTRIBUTE_AVG_PKT_SIZE_RX:
                    mStats->avg_rx_pkt_size = it.get_u32();
                    break;
                case TWT_ATTRIBUTE_AVG_EOSP_DUR:
                    mStats->avg_eosp_dur_us = it.get_u32();
                    break;
                case TWT_ATTRIBUTE_EOSP_COUNT:
                    mStats->eosp_count = it.get_u32();
                    break;
                case TWT_ATTRIBUTE_NUM_SP:
                    mStats->num_sp = it.get_u32();
                    break;
                default:
                    ALOGE("Ignoring invalid attribute type = %d, size = %d\n",
                            it.get_type(), it.get_len());
                    break;
            }
        }

        return NL_OK;
    }
};

/* API to get TWT stats */
wifi_error twt_get_stats(wifi_interface_handle iface, u8 config_id, TwtStats* stats)
{
    if (iface == NULL) {
        ALOGE("twt_get_stats: NULL iface pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (stats == NULL) {
        ALOGE("TwtCapabilitySet: NULL capabilities pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    GetTwtStatsCommand command(iface, config_id, stats);
    return (wifi_error) command.requestResponse();
}

//////////////////////////////////////////////////////////////////////////////////////
class ClearTwtStatsCommand : public WifiCommand
{
    u8 mConfig_id;
public:
    ClearTwtStatsCommand(wifi_interface_handle iface, u8 config_id)
        : WifiCommand("ClearTwtStatsCommand", iface, 0), mConfig_id(config_id)
    {
    }

    virtual int create() {
        ALOGD("Creating message to clear twt stats; config_id = %d\n", mConfig_id);

        int ret = mMsg.create(GOOGLE_OUI, TWT_SUBCMD_CLR_STATS);
        if (ret < 0) {
            return ret;
        }

        nlattr *data = mMsg.attr_start(NL80211_ATTR_VENDOR_DATA);
        ret = mMsg.put_u8(TWT_ATTRIBUTE_CONFIG_ID, mConfig_id);
        if (ret < 0) {
             ALOGE("Failed to set mConfig_id %d\n", mConfig_id);
             return ret;
        }

        ALOGI("Successfully configured config id %d\n", mConfig_id);
        mMsg.attr_end(data);
        return WIFI_SUCCESS;
    }

protected:
    virtual int handleResponse(WifiEvent& reply) {
        ALOGD("In ClearTwtStatsCommand::handleResponse");
        /* Nothing to do on response! */
        return NL_SKIP;
    }
};

/* API to clear TWT stats */
wifi_error twt_clear_stats(wifi_interface_handle iface, u8 config_id)
{
    if (iface == NULL || !config_id) {
        ALOGE("twt_clear_stats: NULL iface pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }
    ALOGE("twt_clear_stats: config id: %d\n", config_id);

    ClearTwtStatsCommand command(iface, config_id);
    return (wifi_error) command.requestResponse();
}

////////////////////////////////////////////////////////////////////////////////
class TwtFeatureRequest : public WifiCommand
{
    TwtRequest reqContext;
    TwtRequestType mType;

    public:
    TwtFeatureRequest(wifi_interface_handle iface,
            TwtRequest params, TwtRequestType cmdType)
        : WifiCommand("TwtFeatureRequest", iface, 0), reqContext(params), mType(cmdType)
    {
    }

    int createRequest(WifiRequest& request)
    {
        ALOGI("TWT CMD: %s\n", TwtCmdToString(mType));
        if (mType == TWT_SETUP_REQUEST) {
            return createTwtSetupRequest(request, (TwtSetupRequest *)reqContext);
        } else if (mType == TWT_INFO_FRAME_REQUEST) {
            return createInfoFrameRequest(request, (TwtInfoFrameRequest *)reqContext);
        } else if (mType == TWT_TEAR_DOWN_REQUEST) {
            return createTearDownRequest(request, (TwtTeardownRequest *)reqContext);
        } else {
            ALOGE("%s: Unknown TWT request: %d\n", __func__, mType);
            return WIFI_ERROR_UNKNOWN;
        }

        return WIFI_SUCCESS;
    }

    int createTwtSetupRequest(WifiRequest& request, TwtSetupRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, TWT_SUBCMD_SETUP_REQUEST);
        if (result < 0) {
            ALOGE("%s Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        /* If handle is 0xFFFF, then update instance_id in response of this request
         * otherwise, update not needed
         */
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);
        if (mParams->config_id) {
            result = request.put_u8(TWT_ATTRIBUTE_CONFIG_ID, mParams->config_id);
            if (result < 0) {
                ALOGE("%s: Failed to fill config_id = %d, result = %d\n",
                    __func__, mParams->config_id, result);
                return result;
            }
        }

        if (mParams->negotiation_type) {
            result = request.put_u8(TWT_ATTRIBUTE_NEG_TYPE, mParams->negotiation_type);
            if (result < 0) {
                ALOGE("%s: Failed to fill negotiation_type = %d, result = %d\n",
                    __func__, mParams->negotiation_type, result);
                return result;
            }
        }
        if (mParams->trigger_type) {
            result = request.put_u8(TWT_ATTRIBUTE_TRIGGER_TYPE, mParams->trigger_type);
            if (result < 0) {
                ALOGE("%s: Failed to fill trigger_type = %d, result = %d\n",
                    __func__, mParams->trigger_type, result);
                return result;
            }
        }
        if (mParams->wake_dur_us) {
            result = request.put_u32(TWT_ATTRIBUTE_WAKE_DUR_US, mParams->wake_dur_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill wake_dur_us = %d, result = %d\n",
                    __func__, mParams->wake_dur_us, result);
                return result;
            }
        }
        if (mParams->wake_int_us) {
            result = request.put_u32(TWT_ATTRIBUTE_WAKE_INT_US, mParams->wake_int_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill wake_int_us = %d, result = %d\n",
                    __func__, mParams->wake_int_us, result);
                return result;
            }
        }
        if (mParams->wake_int_min_us) {
            result = request.put_u32(TWT_ATTRIBUTE_WAKE_INT_MIN_US, mParams->wake_int_min_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill wake_int_min_us = %d, result = %d\n",
                    __func__, mParams->wake_int_min_us, result);
                return result;
            }
        }
        if (mParams->wake_int_max_us) {
            result = request.put_u32(TWT_ATTRIBUTE_WAKE_INT_MAX_US, mParams->wake_int_max_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill wake_int_max_us = %d, result = %d\n",
                    __func__, mParams->wake_int_max_us, result);
                return result;
            }
        }
        if (mParams->wake_dur_min_us) {
            result = request.put_u32(TWT_ATTRIBUTE_WAKE_DUR_MIN_US, mParams->wake_dur_min_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill wake_dur_min_us = %d, result = %d\n",
                    __func__, mParams->wake_dur_min_us, result);
                return result;
            }
        }
        if (mParams->wake_dur_max_us) {
            result = request.put_u32(TWT_ATTRIBUTE_WAKE_DUR_MAX_US, mParams->wake_dur_max_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill wake_dur_max_us = %d, result = %d\n",
                    __func__, mParams->wake_dur_max_us, result);
                return result;
            }
        }
        if (mParams->avg_pkt_size) {
            result = request.put_u32(TWT_ATTRIBUTE_AVG_PKT_SIZE, mParams->avg_pkt_size);
            if (result < 0) {
                ALOGE("%s: Failed to fill avg_pkt_size = %d, result = %d\n",
                    __func__, mParams->avg_pkt_size, result);
                return result;
            }
        }
        if (mParams->avg_pkt_num) {
            result = request.put_u32(TWT_ATTRIBUTE_AVG_PKT_NUM, mParams->avg_pkt_num);
            if (result < 0) {
                ALOGE("%s: Failed to fill avg_pkt_num = %d, result = %d\n",
                    __func__, mParams->avg_pkt_num, result);
                return result;
            }
        }
        if (mParams->wake_time_off_us) {
            result = request.put_u32(TWT_ATTRIBUTE_WAKE_TIME_OFF_US, mParams->wake_time_off_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill wake_time_off_us = %d, result = %d\n",
                    __func__, mParams->wake_time_off_us, result);
                return result;
            }
        }
        request.attr_end(data);

        ALOGI("Returning successfully\n");
        return result;
    }

    int createInfoFrameRequest(WifiRequest& request, TwtInfoFrameRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, TWT_SUBCMD_INFO_FRAME_REQUEST);
        if (result < 0) {
            ALOGE("%s: Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);
        if (mParams->config_id) {
            result = request.put_u8(TWT_ATTRIBUTE_CONFIG_ID, mParams->config_id);
            if (result < 0) {
                ALOGE("%s: Failed to fill config_id = %d, result = %d\n",
                    __func__, mParams->config_id, result);
                return result;
            }
        }
        if (mParams->resume_time_us) {
            result = request.put_u32(TWT_ATTRIBUTE_RESUME_TIME_US, mParams->resume_time_us);
            if (result < 0) {
                ALOGE("%s: Failed to fill resume_time_us = %d, result = %d\n",
                    __func__, mParams->resume_time_us, result);
                return result;
            }
        }
        if (mParams->all_twt) {
            result = request.put_u8(TWT_ATTRIBUTE_ALL_TWT, mParams->all_twt);
            if (result < 0) {
                ALOGE("%s: Failed to fill all_twt = %d, result = %d\n",
                    __func__, mParams->all_twt, result);
                return result;
            }
        }
        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int createTearDownRequest(WifiRequest& request, TwtTeardownRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, TWT_SUBCMD_TEAR_DOWN_REQUEST);
        if (result < 0) {
            ALOGE("%s: Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);
        if (mParams->config_id) {
            result = request.put_u8(TWT_ATTRIBUTE_CONFIG_ID, mParams->config_id);
            if (result < 0) {
                ALOGE("%s: Failed to fill config_id = %d, result = %d\n",
                    __func__, mParams->config_id, result);
                return result;
            }
        }
        if (mParams->negotiation_type) {
            result = request.put_u8(TWT_ATTRIBUTE_NEG_TYPE, mParams->negotiation_type);
            if (result < 0) {
                ALOGE("%s: Failed to fill negotiation_type = %d, result = %d\n",
                        __func__, mParams->negotiation_type, result);
                return result;
            }
        }
        if (mParams->all_twt) {
            result = request.put_u8(TWT_ATTRIBUTE_ALL_TWT, mParams->all_twt);
            if (result < 0) {
                ALOGE("%s: Failed to fill all_twt = %d, result = %d\n",
                        __func__, mParams->all_twt, result);
                return result;
            }
        }
        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int open()
    {
        WifiRequest request(familyId(), ifaceId());
        int result = createRequest(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: failed to create setup request; result = %d", __func__, result);
            return result;
        }

        result = requestResponse(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: failed to configure setup; result = %d", __func__, result);
            return result;
        }

        request.destroy();
        return WIFI_SUCCESS;
    }

    void registerTwtVendorEvents()
    {
        registerVendorHandler(BRCM_OUI, BRCM_VENDOR_EVENT_TWT);
    }

    void unregisterTwtVendorEvents()
    {
        unregisterVendorHandler(BRCM_OUI, BRCM_VENDOR_EVENT_TWT);
    }

    virtual int handleResponse(WifiEvent& reply) {
         ALOGD("Request complete!");
        /* Nothing to do on response! */
        return NL_SKIP;
    }

    int handleEvent(WifiEvent& event) {
        u16 attr_type;
        TwtEventType twt_event;

        nlattr *vendor_data = event.get_attribute(NL80211_ATTR_VENDOR_DATA);
        int len = event.get_vendor_data_len();
        int event_id = event.get_vendor_subcmd();
        ALOGI("Received TWT event: %d\n", event_id);

        if (!vendor_data || len == 0) {
            ALOGE("No event data found");
            return NL_SKIP;
        }

        switch (event_id) {
            case BRCM_VENDOR_EVENT_TWT: {
                HandleTwtEvent(vendor_data);
                break;
            }
            default:
                ALOGE("Unknown event: %d\n", event_id);
                break;
        }
        return NL_SKIP;
    }

};

void twt_deinit_handler()
{
    if (twt_info.twt_feature_request) {
        /* register for Twt vendor events with info mac class*/
        TwtFeatureRequest *cmd_event = (TwtFeatureRequest*)(twt_info.twt_feature_request);
        cmd_event->unregisterTwtVendorEvents();
        delete (TwtFeatureRequest*)twt_info.twt_feature_request;
        twt_info.twt_feature_request = NULL;
    }
    if (TWT_HANDLE(twt_info)) {
        delete GET_TWT_HANDLE(twt_info);
        TWT_HANDLE(twt_info) = NULL;
    }
    ALOGI("wifi twt internal clean up done");
    return;
}

wifi_error twt_register_handler(wifi_interface_handle iface,
        TwtCallbackHandler handlers)
{
    wifi_handle handle = getWifiHandle(iface);
    if (TWT_HANDLE(twt_info)) {
        /* cleanup and re-register */
        twt_deinit_handler();
    }
    memset(&twt_info, 0, sizeof(twt_info));
    TWT_HANDLE(twt_info) = new TwtHandle(handle, handlers);
    twt_info.twt_feature_request =
        (void*)new TwtFeatureRequest(iface, NULL, TWT_LAST);
    NULL_CHECK_RETURN(twt_info.twt_feature_request,
        "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    TwtFeatureRequest *cmd_event = (TwtFeatureRequest*)(twt_info.twt_feature_request);
    cmd_event->registerTwtVendorEvents();
    return WIFI_SUCCESS;
}

wifi_error twt_setup_request(wifi_interface_handle iface, TwtSetupRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    TwtFeatureRequest *cmd;
    TwtRequestType cmdType = TWT_SETUP_REQUEST;

    cmd = new TwtFeatureRequest(iface, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
}

wifi_error twt_info_frame_request(wifi_interface_handle iface, TwtInfoFrameRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    TwtFeatureRequest *cmd;
    TwtRequestType cmdType = TWT_INFO_FRAME_REQUEST;

    cmd = new TwtFeatureRequest(iface, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
}

wifi_error twt_teardown_request(wifi_interface_handle iface, TwtTeardownRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    TwtFeatureRequest *cmd;
    TwtRequestType cmdType = TWT_TEAR_DOWN_REQUEST;

    cmd = new TwtFeatureRequest(iface, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
}
