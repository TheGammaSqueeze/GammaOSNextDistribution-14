/*
 * Copyright (C) 2014 The Android Open Source Project
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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "sync.h"

#include <hardware_legacy/wifi_hal.h>
#include "nan_i.h"
#include "common.h"
#include "cpp_bindings.h"
#include <utils/Log.h>
#include <errno.h>
#include "nancommand.h"
#include "vendor_definitions.h"
#include "wificonfigcommand.h"
#include <ctype.h>
#include <openssl/sha.h>
#include <openssl/evp.h>

#ifdef __GNUC__
#define PRINTF_FORMAT(a,b) __attribute__ ((format (printf, (a), (b))))
#define STRUCT_PACKED __attribute__ ((packed))
#else
#define PRINTF_FORMAT(a,b)
#define STRUCT_PACKED
#endif

#define OUT_OF_BAND_SERVICE_INSTANCE_ID 0

//Singleton Static Instance
NanCommand* NanCommand::mNanCommandInstance  = NULL;

//Implementation of the functions exposed in nan.h
wifi_error nan_register_handler(wifi_interface_handle iface,
                                NanCallbackHandler handlers)
{
    // Obtain the singleton instance
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = NanCommand::instance(wifiHandle);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->setCallbackHandler(handlers);
    return ret;
}

wifi_error nan_get_version(wifi_handle handle,
                           NanVersion* version)
{
    *version = (NAN_MAJOR_VERSION <<16 | NAN_MINOR_VERSION << 8 | NAN_MICRO_VERSION);
    return WIFI_SUCCESS;
}

/*  Function to send enable request to the wifi driver.*/
wifi_error nan_enable_request(transaction_id id,
                              wifi_interface_handle iface,
                              NanEnableRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    NanCommand *t_nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanEnable(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanEnable Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

    if (ret == WIFI_SUCCESS) {
        t_nanCommand = NanCommand::instance(wifiHandle);
        if (t_nanCommand != NULL) {
            t_nanCommand->allocSvcParams();
        }
    }

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send disable request to the wifi driver.*/
wifi_error nan_disable_request(transaction_id id,
                               wifi_interface_handle iface)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    NanCommand *t_nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanDisable(id);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanDisable Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);

    if (ret == WIFI_SUCCESS) {
        t_nanCommand = NanCommand::instance(wifiHandle);
        if (t_nanCommand != NULL) {
            t_nanCommand->deallocSvcParams();
        }
    }

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send publish request to the wifi driver.*/
wifi_error nan_publish_request(transaction_id id,
                               wifi_interface_handle iface,
                               NanPublishRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanPublish(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanPublish Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send publish cancel to the wifi driver.*/
wifi_error nan_publish_cancel_request(transaction_id id,
                                      wifi_interface_handle iface,
                                      NanPublishCancelRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanPublishCancel(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanPublishCancel Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send Subscribe request to the wifi driver.*/
wifi_error nan_subscribe_request(transaction_id id,
                                 wifi_interface_handle iface,
                                 NanSubscribeRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanSubscribe(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanSubscribe Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to cancel subscribe to the wifi driver.*/
wifi_error nan_subscribe_cancel_request(transaction_id id,
                                        wifi_interface_handle iface,
                                        NanSubscribeCancelRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    NanCommand *t_nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanSubscribeCancel(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanSubscribeCancel Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

    if (ret == WIFI_SUCCESS) {
        t_nanCommand = NanCommand::instance(wifiHandle);
        if (t_nanCommand != NULL) {
            t_nanCommand->deleteServiceId(msg->subscribe_id,
                                          0, NAN_ROLE_SUBSCRIBER);
        }
    }

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send NAN follow up request to the wifi driver.*/
wifi_error nan_transmit_followup_request(transaction_id id,
                                         wifi_interface_handle iface,
                                         NanTransmitFollowupRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanTransmitFollowup(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanTransmitFollowup Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send NAN statistics request to the wifi driver.*/
wifi_error nan_stats_request(transaction_id id,
                             wifi_interface_handle iface,
                             NanStatsRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanStats(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanStats Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send NAN configuration request to the wifi driver.*/
wifi_error nan_config_request(transaction_id id,
                              wifi_interface_handle iface,
                              NanConfigRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanConfig(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanConfig Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send NAN request to the wifi driver.*/
wifi_error nan_tca_request(transaction_id id,
                           wifi_interface_handle iface,
                           NanTCARequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanTCA(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanTCA Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to send NAN Beacon sdf payload to the wifi driver.
    This instructs the Discovery Engine to begin publishing the
    received payload in any Beacon or Service Discovery Frame
    transmitted*/
wifi_error nan_beacon_sdf_payload_request(transaction_id id,
                                         wifi_interface_handle iface,
                                         NanBeaconSdfPayloadRequest* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanBeaconSdfPayload(id, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanBeaconSdfPayload Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

wifi_error nan_get_sta_parameter(transaction_id id,
                                 wifi_interface_handle iface,
                                 NanStaParameter* msg)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = NanCommand::instance(wifiHandle);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->getNanStaParameter(iface, msg);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: getNanStaParameter Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return ret;
}

/*  Function to get NAN capabilities */
wifi_error nan_get_capabilities(transaction_id id,
                                wifi_interface_handle iface)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanCapabilities(id);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanCapabilities Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

/*  Function to get NAN capabilities */
wifi_error nan_debug_command_config(transaction_id id,
                                   wifi_interface_handle iface,
                                   NanDebugParams debug,
                                   int debug_msg_length)
{
    wifi_error ret;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    if (debug_msg_length <= 0) {
        ALOGE("%s: Invalid debug message length = %d", __FUNCTION__,
                                                       debug_msg_length);
        return WIFI_ERROR_UNKNOWN;
    }

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                info->support_nan_ext_cmd?
                                QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = nanCommand->putNanDebugCommand(debug, debug_msg_length);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: putNanDebugCommand Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

wifi_error nan_initialize_vendor_cmd(wifi_interface_handle iface,
                                     NanCommand **nanCommand)
{
    wifi_error ret;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    if (nanCommand == NULL) {
        ALOGE("%s: Error nanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    *nanCommand = new NanCommand(wifiHandle,
                                 0,
                                 OUI_QCA,
                                 QCA_NL80211_VENDOR_SUBCMD_NDP);
    if (*nanCommand == NULL) {
        ALOGE("%s: Object creation failed", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    /* Create the message */
    ret = (*nanCommand)->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = (*nanCommand)->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    return WIFI_SUCCESS;

cleanup:
    delete *nanCommand;
    return ret;
}

wifi_error nan_data_interface_create(transaction_id id,
                                     wifi_interface_handle iface,
                                     char* iface_name)
{
    ALOGV("NAN_DP_INTERFACE_CREATE");
    wifi_error ret;
    struct nlattr *nlData;
    NanCommand *nanCommand = NULL;
    WiFiConfigCommand *wifiConfigCommand;
    wifi_handle handle = getWifiHandle(iface);
    hal_info *info = getHalInfo(handle);
    bool ndi_created = false;

    if (iface_name == NULL) {
        ALOGE("%s: Invalid Nan Data Interface Name. \n", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (!info || info->num_interfaces < 1) {
        ALOGE("%s: Error wifi_handle NULL or base wlan interface not present",
              __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    if (check_feature(QCA_WLAN_VENDOR_FEATURE_USE_ADD_DEL_VIRTUAL_INTF_FOR_NDI,
                      &info->driver_supported_features)) {
        wifiConfigCommand = new WiFiConfigCommand(handle,
                                                  get_requestid(), 0, 0);
        if (wifiConfigCommand == NULL) {
            ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
            return WIFI_ERROR_UNKNOWN;
        }
        wifiConfigCommand->create_generic(NL80211_CMD_NEW_INTERFACE);
        wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX,
                                   info->interfaces[0]->id);
        wifiConfigCommand->put_string(NL80211_ATTR_IFNAME, iface_name);
        wifiConfigCommand->put_u32(NL80211_ATTR_IFTYPE,
                                   NL80211_IFTYPE_STATION);
        /* Send the NL msg. */
        wifiConfigCommand->waitForRsp(false);
        ret = wifiConfigCommand->requestEvent();
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: Create intf failed, Error:%d", __FUNCTION__, ret);
            delete wifiConfigCommand;
            return ret;
        }
        ndi_created = true;
        delete wifiConfigCommand;
    }

    ret = nan_initialize_vendor_cmd(iface, &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        goto delete_ndi;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_CREATE) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            iface_name)) {
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;

delete_ndi:
    if (ndi_created && ret != WIFI_SUCCESS) {
        wifiConfigCommand = new WiFiConfigCommand(handle,
                                                  get_requestid(), 0, 0);
        if (wifiConfigCommand == NULL) {
            ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
            return ret;
        }
        wifiConfigCommand->create_generic(NL80211_CMD_DEL_INTERFACE);
        wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX,
                                   if_nametoindex(iface_name));
        /* Send the NL msg. */
        wifiConfigCommand->waitForRsp(false);
        if (wifiConfigCommand->requestEvent() != WIFI_SUCCESS)
            ALOGE("%s: Delete intf failed", __FUNCTION__);

        delete wifiConfigCommand;
    }
    return ret;
}

wifi_error nan_data_interface_delete(transaction_id id,
                                     wifi_interface_handle iface,
                                     char* iface_name)
{
    ALOGV("NAN_DP_INTERFACE_DELETE");
    wifi_error ret;
    struct nlattr *nlData;
    NanCommand *nanCommand = NULL;
    WiFiConfigCommand *wifiConfigCommand;
    wifi_handle handle = getWifiHandle(iface);
    hal_info *info = getHalInfo(handle);

    if (iface_name == NULL) {
        ALOGE("%s: Invalid Nan Data Interface Name. \n", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (!info || info->num_interfaces < 1) {
        ALOGE("%s: Error wifi_handle NULL or base wlan interface not present",
          __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        goto delete_ndi;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_DELETE) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            iface_name)) {
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;

delete_ndi:
    if ((check_feature(QCA_WLAN_VENDOR_FEATURE_USE_ADD_DEL_VIRTUAL_INTF_FOR_NDI,
                       &info->driver_supported_features)) &&
        if_nametoindex(iface_name)) {
        wifiConfigCommand = new WiFiConfigCommand(handle,
                                                  get_requestid(), 0, 0);
        if (wifiConfigCommand == NULL) {
            ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
            return WIFI_ERROR_UNKNOWN;
        }
        wifiConfigCommand->create_generic(NL80211_CMD_DEL_INTERFACE);
        wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX,
                                   if_nametoindex(iface_name));
        /* Send the NL msg. */
        wifiConfigCommand->waitForRsp(false);
        if (wifiConfigCommand->requestEvent() != WIFI_SUCCESS) {
            ALOGE("%s: Delete intf failed", __FUNCTION__);
        }
        delete wifiConfigCommand;
    }

    return ret;
}

/* Service ID using SHA256 */
static bool
ndp_create_service_id(const u8 *service_name,
                      u32 service_name_len, u8 *service_id)
{
    u8 out_service_id[NAN_SVC_HASH_SIZE] = {0};
    u8 *mod_service_name;
    unsigned char prop_oob_service_name[NAN_DEF_SVC_NAME_LEN + 1] =
                                                        "Wi-Fi Aware Data Path";
    unsigned char prop_oob_service_name_lowercase[NAN_DEF_SVC_NAME_LEN + 1] =
                                                        "wi-fi aware data path";
    bool is_default = false;
    int i;

    if (!service_name) {
        ALOGE("%s: NULL service name", __FUNCTION__);
        return false;
    }

    if (!service_name_len) {
        ALOGE("%s: Zero service name length", __FUNCTION__);
        return false;
    }

    if (!service_id) {
        ALOGE("%s: NULL service ID", __FUNCTION__);
        return false;
    }

    mod_service_name = (u8 *)malloc(service_name_len);
    if (!mod_service_name) {
        ALOGE("%s: malloc failed", __FUNCTION__);
        return false;
    }

    memset(mod_service_name, 0, service_name_len);
    memcpy(mod_service_name, service_name, service_name_len);
    if ((service_name_len == NAN_DEF_SVC_NAME_LEN) &&
        (!memcmp(mod_service_name, prop_oob_service_name, service_name_len)
         || !memcmp(mod_service_name,
                    prop_oob_service_name_lowercase, service_name_len)))
        is_default = true;

    for (i = 0; i < service_name_len; i++) {
    /*
     * As per NAN spec, the only acceptable singlebyte UTF-8 symbols for a
     * Service Name are alphanumeric values (A-Z, a-z, 0-9), the hyphen ('-'),
     * the underscore ('_'), and the period ('.').
     * These checks are added for all service names except the above defined
     * default service name.
     */
        if (!is_default && !isalnum(mod_service_name[i]) &&
            (mod_service_name[i] != '_') && (mod_service_name[i] != '-') &&
            (mod_service_name[i] != '.')) {
             free(mod_service_name);
             return false;
        }

        if ((mod_service_name[i] == ' ') && (is_default))
             goto end;

        /*
         * The service_name hash SHALL always be done on a lower-case
         * version of service_name which was passed down. Therefore,
         * before passing the service_name to the SHA256 function first
         * run through the string and call tolower on each byte.
         */
        mod_service_name[i] = tolower(mod_service_name[i]);
    }

end:
    SHA256(mod_service_name, service_name_len, out_service_id);
    /*
     * As per NAN spec, Service ID is the first 48 bits of the SHA-256 hash
     * of the Service Name
     */
    memcpy(service_id, out_service_id, NAN_SVC_ID_SIZE);

    free(mod_service_name);
    return true;
}

/*
 * PMK = PBKDF2(<pass phrase>, <Salt Version>||<Cipher Suite ID>||<Service ID>||
 *              <Publisher NMI>, 4096, 32)
 * ndp_passphrase_to_pmk: API to calculate the service ID and PMK.
 * @pmk: output value of Hash
 * @passphrase: secret key
 * @salt_version: 00
 * @csid: cipher suite ID: 01
 * As per NAN spec, below are the values defined for CSID attribute:
 *     1 - NCS-SK-128 Cipher Suite
 *     2 - NCS-SK-256 Cipher Suite
 *     3 - NCS-PK-2WDH-128 Cipher Suite
 *     4 - NCS-PK-2WDH-256 Cipher Suite
 *     Other values are reserved
 * @service_id: Hash value of SHA256 on service_name
 * @peer_mac: Publisher NAN Management Interface address
 * @iterations: 4096
 * @pmk_len: 32
 */
static int
ndp_passphrase_to_pmk(u32 cipher_type, u8 *pmk, u8 *passphrase,
                      u32 passphrase_len, u8 *service_name,
                      u32 service_name_len, u8 *svc_id, u8 *peer_mac)
{
    int result = 0;
    u8 pmk_hex[NAN_PMK_INFO_LEN] = {0};
    u8 salt[NAN_SECURITY_SALT_SIZE] = {0};
    u8 service_id[NAN_SVC_ID_SIZE] = {0};
    unsigned char *pos = NULL;
    unsigned char salt_version = 0;
    u8 csid;
    /* We read only first 3-bits, as only 1-4 values are expected currently */
    csid = (u8)(cipher_type & 0x7);
    if (csid == 0)
        csid = NAN_DEFAULT_NCS_SK;

    if (svc_id != NULL) {
        ALOGV("Service ID received from the pool");
        memcpy(service_id, svc_id, NAN_SVC_ID_SIZE);
    } else if (ndp_create_service_id((const u8 *)service_name,
                                     service_name_len, service_id) == false) {
        ALOGE("Failed to create service ID");
        return result;
    }

    pos = salt;
    /* salt version */
    *pos++ = salt_version;
    /* CSID */
    *pos++ = csid;
    /* Service ID */
    memcpy(pos, service_id, NAN_SVC_ID_SIZE);
    pos += NAN_SVC_ID_SIZE;
    /* Publisher NMI */
    memcpy(pos, peer_mac, NAN_MAC_ADDR_LEN);
    pos += NAN_MAC_ADDR_LEN;

    ALOGV("salt dump");
    hexdump(salt, NAN_SECURITY_SALT_SIZE);

    result = PKCS5_PBKDF2_HMAC((const char *)passphrase, passphrase_len, salt,
                               sizeof(salt), NAN_PMK_ITERATIONS,
                               (const EVP_MD *) EVP_sha256(),
                               NAN_PMK_INFO_LEN, pmk_hex);
    if (result)
        memcpy(pmk, pmk_hex, NAN_PMK_INFO_LEN);

    return result;
}

wifi_error nan_data_request_initiator(transaction_id id,
                                      wifi_interface_handle iface,
                                      NanDataPathInitiatorRequest* msg)
{
    ALOGV("NAN_DP_REQUEST_INITIATOR");
    wifi_error ret;
    struct nlattr *nlData, *nlCfgQos;
    NanCommand *nanCommand = NULL;
    NanCommand *t_nanCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);

    if (msg == NULL)
        return WIFI_ERROR_INVALID_ARGS;

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return ret;
    }

    t_nanCommand = NanCommand::instance(wifiHandle);
    if (t_nanCommand == NULL)
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);

    if ((msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) &&
        (msg->key_info.body.pmk_info.pmk_len == 0) &&
        (msg->key_info.body.passphrase_info.passphrase_len == 0)) {
        ALOGE("%s: Failed-Initiator req, missing pmk and passphrase",
               __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    if ((msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) &&
        (msg->requestor_instance_id == OUT_OF_BAND_SERVICE_INSTANCE_ID) &&
        (msg->service_name_len == 0)) {
        ALOGE("%s: Failed-Initiator req, missing service name for out of band request",
              __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_INITIATOR_REQUEST) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_INSTANCE_ID,
            msg->requestor_instance_id) ||
        nanCommand->put_bytes(
            QCA_WLAN_VENDOR_ATTR_NDP_PEER_DISCOVERY_MAC_ADDR,
            (char *)msg->peer_disc_mac_addr,
            NAN_MAC_ADDR_LEN) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            msg->ndp_iface)) {
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    if (msg->channel_request_type != NAN_DP_CHANNEL_NOT_REQUESTED) {
        if (nanCommand->put_u32 (
                QCA_WLAN_VENDOR_ATTR_NDP_CHANNEL_CONFIG,
                msg->channel_request_type) ||
            nanCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_NDP_CHANNEL,
                msg->channel)){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }

    if (msg->app_info.ndp_app_info_len != 0) {
        if (nanCommand->put_bytes(
                QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO,
                (char *)msg->app_info.ndp_app_info,
                msg->app_info.ndp_app_info_len)) {
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }

    if (msg->ndp_cfg.qos_cfg == NAN_DP_CONFIG_QOS) {
        nlCfgQos =
            nanCommand->attr_start(QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_QOS);
        if (!nlCfgQos){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
        /* TBD Qos Info */
        nanCommand->attr_end(nlCfgQos);
    }
    if (msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) {
        if (nanCommand->put_u32(QCA_WLAN_VENDOR_ATTR_NDP_CSID,
                msg->cipher_type)){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }
    if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK) {
        if (msg->key_info.body.pmk_info.pmk_len != NAN_PMK_INFO_LEN) {
            ret = WIFI_ERROR_UNKNOWN;
            ALOGE("%s: Invalid pmk len:%d", __FUNCTION__,
                  msg->key_info.body.pmk_info.pmk_len);
            goto cleanup;
        }
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PMK,
            (char *)msg->key_info.body.pmk_info.pmk,
            msg->key_info.body.pmk_info.pmk_len)){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    } else if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) {
        if (msg->key_info.body.passphrase_info.passphrase_len <
            NAN_SECURITY_MIN_PASSPHRASE_LEN ||
            msg->key_info.body.passphrase_info.passphrase_len >
            NAN_SECURITY_MAX_PASSPHRASE_LEN) {
            ret = WIFI_ERROR_UNKNOWN;
            ALOGE("%s: Invalid passphrase len:%d", __FUNCTION__,
                  msg->key_info.body.passphrase_info.passphrase_len);
            goto cleanup;
        }
        u8 *service_id = NULL;

        if (t_nanCommand != NULL)
            service_id = t_nanCommand->getServiceId(msg->requestor_instance_id,
                                                    NAN_ROLE_SUBSCRIBER);
        if (service_id == NULL)
            ALOGE("%s: Entry not found for Instance ID:%d",
                  __FUNCTION__, msg->requestor_instance_id);
        if (((service_id != NULL) || (msg->service_name_len)) &&
            ndp_passphrase_to_pmk(msg->cipher_type,
                                  msg->key_info.body.pmk_info.pmk,
                                  msg->key_info.body.passphrase_info.passphrase,
                                  msg->key_info.body.passphrase_info.passphrase_len,
                                  msg->service_name, msg->service_name_len,
                                  service_id, msg->peer_disc_mac_addr)) {
            msg->key_info.body.pmk_info.pmk_len = NAN_PMK_INFO_LEN;
            if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PMK,
                (char *)msg->key_info.body.pmk_info.pmk,
                msg->key_info.body.pmk_info.pmk_len)){
                if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PASSPHRASE,
                    (char *)msg->key_info.body.passphrase_info.passphrase,
                    msg->key_info.body.passphrase_info.passphrase_len)){
                    ret = WIFI_ERROR_UNKNOWN;
                    goto cleanup;
                }
            }
        } else if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PASSPHRASE,
                   (char *)msg->key_info.body.passphrase_info.passphrase,
                   msg->key_info.body.passphrase_info.passphrase_len)) {
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }
    if (msg->service_name_len) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_NAME,
            (char *)msg->service_name, msg->service_name_len)){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }
    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

wifi_error nan_data_indication_response(transaction_id id,
                                        wifi_interface_handle iface,
                                        NanDataPathIndicationResponse* msg)
{
    ALOGV("NAN_DP_INDICATION_RESPONSE");
    wifi_error ret;
    struct nlattr *nlData, *nlCfgQos;
    NanCommand *nanCommand = NULL;
    NanCommand *t_nanCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);

    if (msg == NULL)
        return WIFI_ERROR_INVALID_ARGS;

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return ret;
    }

    t_nanCommand = NanCommand::instance(wifiHandle);
    if (t_nanCommand == NULL)
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);

    if ((msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) &&
        (msg->key_info.body.pmk_info.pmk_len == 0) &&
        (msg->key_info.body.passphrase_info.passphrase_len == 0)) {
        ALOGE("%s: Failed-Initiator req, missing pmk and passphrase",
               __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_RESPONDER_REQUEST) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID,
            msg->ndp_instance_id) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            msg->ndp_iface) ||
        nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_RESPONSE_CODE,
            msg->rsp_code)) {
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }
    if (msg->app_info.ndp_app_info_len != 0) {
        if (nanCommand->put_bytes(
                QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO,
                (char *)msg->app_info.ndp_app_info,
                msg->app_info.ndp_app_info_len)) {
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }
    if (msg->ndp_cfg.qos_cfg == NAN_DP_CONFIG_QOS) {
        nlCfgQos =
            nanCommand->attr_start(QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_QOS);
        if (!nlCfgQos){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }

        /* TBD Qos Info */
        nanCommand->attr_end(nlCfgQos);
    }
    if (msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) {
        if (nanCommand->put_u32(QCA_WLAN_VENDOR_ATTR_NDP_CSID,
                msg->cipher_type)){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }
    if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK) {
        if (msg->key_info.body.pmk_info.pmk_len != NAN_PMK_INFO_LEN) {
            ret = WIFI_ERROR_UNKNOWN;
            ALOGE("%s: Invalid pmk len:%d", __FUNCTION__,
                  msg->key_info.body.pmk_info.pmk_len);
            goto cleanup;
        }
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PMK,
            (char *)msg->key_info.body.pmk_info.pmk,
            msg->key_info.body.pmk_info.pmk_len)){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    } else if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) {
        if (msg->key_info.body.passphrase_info.passphrase_len <
            NAN_SECURITY_MIN_PASSPHRASE_LEN ||
            msg->key_info.body.passphrase_info.passphrase_len >
            NAN_SECURITY_MAX_PASSPHRASE_LEN) {
            ret = WIFI_ERROR_UNKNOWN;
            ALOGE("%s: Invalid passphrase len:%d", __FUNCTION__,
                  msg->key_info.body.passphrase_info.passphrase_len);
            goto cleanup;
        }
        u8 *service_id = NULL;

        if (t_nanCommand != NULL)
            service_id = t_nanCommand->getServiceId(msg->ndp_instance_id,
                                                    NAN_ROLE_PUBLISHER);
        if (service_id == NULL)
            ALOGE("%s: Entry not found for Instance ID:%d",
                  __FUNCTION__, msg->ndp_instance_id);
        if (((service_id != NULL) || (msg->service_name_len)) &&
            (t_nanCommand != NULL) &&
            ndp_passphrase_to_pmk(msg->cipher_type,
                                  msg->key_info.body.pmk_info.pmk,
                                  msg->key_info.body.passphrase_info.passphrase,
                                  msg->key_info.body.passphrase_info.passphrase_len,
                                  msg->service_name, msg->service_name_len,
                                  service_id, t_nanCommand->getNmi())) {
            msg->key_info.body.pmk_info.pmk_len = NAN_PMK_INFO_LEN;
            if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PMK,
                (char *)msg->key_info.body.pmk_info.pmk,
                msg->key_info.body.pmk_info.pmk_len))
                if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PASSPHRASE,
                    (char *)msg->key_info.body.passphrase_info.passphrase,
                    msg->key_info.body.passphrase_info.passphrase_len)){
                    ret = WIFI_ERROR_UNKNOWN;
                    goto cleanup;
                }
        } else if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PASSPHRASE,
                   (char *)msg->key_info.body.passphrase_info.passphrase,
                   msg->key_info.body.passphrase_info.passphrase_len)) {
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }

    if (msg->service_name_len) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_NAME,
            (char *)msg->service_name, msg->service_name_len)){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
    }
    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

wifi_error nan_data_end(transaction_id id,
                        wifi_interface_handle iface,
                        NanDataPathEndRequest* msg)
{
    wifi_error ret;
    ALOGV("NAN_DP_END");
    struct nlattr *nlData;
    NanCommand *nanCommand = NULL;

    if (msg == NULL)
        return WIFI_ERROR_INVALID_ARGS;

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return ret;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_END_REQUEST) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_bytes(
            QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID_ARRAY,
            (char *)msg->ndp_instance_id,
            msg->num_ndp_instances * sizeof(u32))) {
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }
    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);

cleanup:
    delete nanCommand;
    return ret;
}

// Implementation related to nan class common functions
// Constructor
//Making the constructor private since this class is a singleton
NanCommand::NanCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    memset(&mHandler, 0,sizeof(mHandler));
    mNanVendorEvent = NULL;
    mNanDataLen = 0;
    mStaParam = NULL;
    memset(mNmiMac, 0, sizeof(mNmiMac));
    mStorePubParams = NULL;
    mStoreSubParams = NULL;
    mNanMaxPublishes = 0;
    mNanMaxSubscribes = 0;
    mNanDiscAddrIndDisabled = false;
}

NanCommand* NanCommand::instance(wifi_handle handle)
{
    hal_info *info;

    if (handle == NULL) {
        ALOGE("Handle is invalid");
        return NULL;
    }
    info = getHalInfo(handle);
    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return NULL;
    }

    if (mNanCommandInstance == NULL) {
        mNanCommandInstance = new NanCommand(handle, 0,
                                             OUI_QCA,
                                             info->support_nan_ext_cmd?
                                             QCA_NL80211_VENDOR_SUBCMD_NAN_EXT :
                                             QCA_NL80211_VENDOR_SUBCMD_NAN);
        ALOGV("NanCommand %p created", mNanCommandInstance);
        return mNanCommandInstance;
    } else {
        if (handle != getWifiHandle(mNanCommandInstance->mInfo)) {
            /* upper layer must have cleaned up the handle and reinitialized,
               so we need to update the same */
            ALOGI("Handle different, update the handle");
            mNanCommandInstance->mInfo = (hal_info *)handle;
        }
    }
    ALOGV("NanCommand %p created already", mNanCommandInstance);
    return mNanCommandInstance;
}

void NanCommand::cleanup()
{
    //free the VendorData
    if (mVendorData) {
        free(mVendorData);
    }
    mVendorData = NULL;
    //cleanup the mMsg
    mMsg.destroy();
}

NanCommand::~NanCommand()
{
    ALOGV("NanCommand %p destroyed", this);
}

int NanCommand::handleResponse(WifiEvent &reply){
    return NL_SKIP;
}

/* Save NAN Management Interface address */
void NanCommand::saveNmi(u8 *mac)
{
    memcpy(mNmiMac, mac, NAN_MAC_ADDR_LEN);
}

/* Get NAN Management Interface address */
u8 *NanCommand::getNmi()
{
    return mNmiMac;
}

/*
 * Save the service ID along with Subscribe/Publish ID and Instance ID, which
 * will be used later for Passphrase to PMK calculation.
 *
 * service_id - Service ID received from Firmware either in NAN/NDP Indication
 * sub_pub_handle - Subscribe/Publish ID received in NAN/NDP Indication
 * instance_id - Service/NDP instance ID received in NAN/NDP Indication
 * pool - Subscriber/Publisher entry based on NAN/NDP Indication
 */
void NanCommand::saveServiceId(u8 *service_id, u16 sub_pub_handle,
                               u32 instance_id, NanRole pool)
{
    int i;

    if ((service_id == NULL) || (!sub_pub_handle) || (!instance_id)) {
        ALOGE("%s: Null Parameter received, sub_pub_handle=%d instance_id=%d",
              __FUNCTION__, sub_pub_handle, instance_id);
        return;
    }
    switch(pool) {
    case NAN_ROLE_PUBLISHER:
        if ((mStorePubParams == NULL) || !mNanMaxPublishes)
            return;
        for (i = 0; i < mNanMaxPublishes; i++) {
            /* In 1:n case there can be multiple publish entries with same
             * publish ID, hence save the new entry if instance ID doesn't match
             * with the existing entries in the pool
             */
            if ((mStorePubParams[i].subscriber_publisher_id) &&
                (mStorePubParams[i].instance_id != instance_id))
                continue;

            memset(&mStorePubParams[i], 0, sizeof(mStorePubParams));
            memcpy(mStorePubParams[i].service_id, service_id, NAN_SVC_ID_SIZE);
            mStorePubParams[i].subscriber_publisher_id = sub_pub_handle;
            mStorePubParams[i].instance_id = instance_id;
            ALOGV("Added new entry in Publisher pool at index=%d with "
                  "Publish ID=%d and Instance ID=%d", i,
                  mStorePubParams[i].subscriber_publisher_id,
                  mStorePubParams[i].instance_id);
            return;
        }
        if (i == mNanMaxPublishes)
            ALOGV("No empty slot found in publisher pool, entry not saved");
    break;
    case NAN_ROLE_SUBSCRIBER:
        if ((mStoreSubParams == NULL) || !mNanMaxSubscribes)
            return;
        for (i = 0; i < mNanMaxSubscribes; i++) {
            /* In 1:n case there can be multiple subscribe entries with same
             * subscribe ID, hence save new entry if instance ID doesn't match
             * with the existing entries in the pool
             */
            if ((mStoreSubParams[i].subscriber_publisher_id) &&
                (mStoreSubParams[i].instance_id != instance_id))
                continue;

            memset(&mStoreSubParams[i], 0, sizeof(mStoreSubParams));
            memcpy(mStoreSubParams[i].service_id, service_id, NAN_SVC_ID_SIZE);
            mStoreSubParams[i].subscriber_publisher_id = sub_pub_handle;
            mStoreSubParams[i].instance_id = instance_id;
            ALOGV("Added new entry in Subscriber pool at index=%d with "
                  "Subscribe ID=%d and Instance ID=%d", i,
                  mStoreSubParams[i].subscriber_publisher_id,
                  mStoreSubParams[i].instance_id);
            return;
        }
        if (i == mNanMaxSubscribes)
            ALOGV("No empty slot found in subscriber pool, entry not saved");
    break;
    default:
        ALOGE("Invalid Pool: %d", pool);
    break;
    }
}

/*
 * Get the Service ID from the pool based on the Service/NDP instance ID that
 * will be used for Passphrase to PMK calculation in Initiator/Responder request
 *
 * instance_id - Service/NDP instance ID received in NAN/NDP Indication
 * pool - Subscriber/Publisher role based on the Initiator/Responder
 */
u8 *NanCommand::getServiceId(u32 instance_id, NanRole pool)
{
    int i;

    switch(pool) {
    case NAN_ROLE_PUBLISHER:
        if ((mStorePubParams == NULL) || (!instance_id) || !mNanMaxPublishes)
            return NULL;
        ALOGV("Getting Service ID from publisher pool for instance ID=%d", instance_id);
        for (i = 0; i < mNanMaxPublishes; i++) {
            if (mStorePubParams[i].instance_id == instance_id)
                return mStorePubParams[i].service_id;
        }
    break;
    case NAN_ROLE_SUBSCRIBER:
        if ((mStoreSubParams == NULL )|| (!instance_id) || !mNanMaxSubscribes)
            return NULL;
        ALOGV("Getting Service ID from subscriber pool for instance ID=%d", instance_id);
        for (i = 0; i < mNanMaxSubscribes; i++) {
            if (mStoreSubParams[i].instance_id == instance_id)
                return mStoreSubParams[i].service_id;
        }
    break;
    default:
        ALOGE("Invalid Pool: %d", pool);
    break;
    }
    return NULL;
}

/*
 * Delete service ID entry from the pool based on the subscriber/Instance ID
 *
 * sub_handle - Subscriber ID received from the Subscribe Cancel
 * instance_id - NDP Instance ID received from the NDP End Indication
 */
void NanCommand::deleteServiceId(u16 sub_handle,
                                 u32 instance_id, NanRole pool)
{
    int i;

    switch(pool) {
    case NAN_ROLE_PUBLISHER:
        if ((mStorePubParams == NULL) || (!instance_id) || !mNanMaxPublishes)
            return;
        for (i = 0; i < mNanMaxPublishes; i++) {
            /* Delete all the entries that has the matching Instance ID */
            if (mStorePubParams[i].instance_id == instance_id) {
                ALOGV("Deleted entry at index=%d from publisher pool "
                      "with publish ID=%d and instance ID=%d", i,
                      mStorePubParams[i].subscriber_publisher_id,
                      mStorePubParams[i].instance_id);
                memset(&mStorePubParams[i], 0, sizeof(mStorePubParams));
            }
        }
    break;
    case NAN_ROLE_SUBSCRIBER:
        if ((mStoreSubParams == NULL) || (!sub_handle) || !mNanMaxSubscribes)
            return;
        for (i = 0; i < mNanMaxSubscribes; i++) {
            /* Delete all the entries that has the matching subscribe ID */
            if (mStoreSubParams[i].subscriber_publisher_id == sub_handle) {
                ALOGV("Deleted entry at index=%d from subsriber pool "
                      "with subscribe ID=%d and instance ID=%d", i,
                      mStoreSubParams[i].subscriber_publisher_id,
                      mStoreSubParams[i].instance_id);
                memset(&mStoreSubParams[i], 0, sizeof(mStoreSubParams));
            }
        }
    break;
    default:
        ALOGE("Invalid Pool: %d", pool);
    break;
    }
}

/*
 * Allocate the memory for the Subscribe and Publish pools using the Max values
 * mStorePubParams - Points the Publish pool
 * mStoreSubParams - Points the Subscribe pool
 */
void NanCommand::allocSvcParams()
{
    if (mNanMaxPublishes < NAN_DEF_PUB_SUB)
        mNanMaxPublishes = NAN_DEF_PUB_SUB;
    if (mNanMaxSubscribes < NAN_DEF_PUB_SUB)
        mNanMaxSubscribes = NAN_DEF_PUB_SUB;

    if ((mStorePubParams == NULL) && mNanMaxPublishes) {
        mStorePubParams =
        (NanStoreSvcParams *)malloc(mNanMaxPublishes*sizeof(NanStoreSvcParams));
        if (mStorePubParams == NULL) {
            ALOGE("%s: Publish pool malloc failed", __FUNCTION__);
            deallocSvcParams();
            return;
        }
        ALOGV("%s: Allocated the Publish pool for max %d entries",
              __FUNCTION__, mNanMaxPublishes);
    }
    if ((mStoreSubParams == NULL) && mNanMaxSubscribes) {
        mStoreSubParams =
        (NanStoreSvcParams *)malloc(mNanMaxSubscribes*sizeof(NanStoreSvcParams));
        if (mStoreSubParams == NULL) {
            ALOGE("%s: Subscribe pool malloc failed", __FUNCTION__);
            deallocSvcParams();
            return;
        }
        ALOGV("%s: Allocated the Subscribe pool for max %d entries",
              __FUNCTION__, mNanMaxSubscribes);
    }
}

/*
 * Reallocate the memory for Subscribe and Publish pools using the Max values
 * mStorePubParams - Points the Publish pool
 * mStoreSubParams - Points the Subscribe pool
 */
void NanCommand::reallocSvcParams(NanRole pool)
{
    switch(pool) {
    case NAN_ROLE_PUBLISHER:
        if ((mStorePubParams != NULL) && mNanMaxPublishes) {
            mStorePubParams =
            (NanStoreSvcParams *)realloc(mStorePubParams,
                                         mNanMaxPublishes*sizeof(NanStoreSvcParams));
            if (mStorePubParams == NULL) {
                ALOGE("%s: Publish pool realloc failed", __FUNCTION__);
                deallocSvcParams();
                return;
            }
            ALOGV("%s: Reallocated the Publish pool for max %d entries",
                   __FUNCTION__, mNanMaxPublishes);
        }
    break;
    case NAN_ROLE_SUBSCRIBER:
        if ((mStoreSubParams != NULL) && mNanMaxSubscribes) {
            mStoreSubParams =
            (NanStoreSvcParams *)realloc(mStoreSubParams,
                                         mNanMaxSubscribes*sizeof(NanStoreSvcParams));
            if (mStoreSubParams == NULL) {
                ALOGE("%s: Subscribe pool realloc failed", __FUNCTION__);
                deallocSvcParams();
                return;
            }
            ALOGV("%s: Reallocated the Subscribe pool for max %d entries",
                  __FUNCTION__, mNanMaxSubscribes);
        }
    break;
    default:
        ALOGE("Invalid Pool: %d", pool);
    break;
    }
}

/*
 * Deallocate the Subscribe and Publish pools
 * mStorePubParams - Points the Publish pool
 * mStoreSubParams - Points the Subscribe pool
 */
void NanCommand::deallocSvcParams()
{
    if (mStorePubParams != NULL) {
        free(mStorePubParams);
        mStorePubParams = NULL;
        ALOGV("%s: Deallocated Publish pool", __FUNCTION__);
    }
    if (mStoreSubParams != NULL) {
        free(mStoreSubParams);
        mStoreSubParams = NULL;
        ALOGV("%s: Deallocated Subscribe pool", __FUNCTION__);
    }
}

wifi_error NanCommand::setCallbackHandler(NanCallbackHandler nHandler)
{
    wifi_error res;
    mHandler = nHandler;
    res = registerVendorHandler(mVendor_id, QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (res != WIFI_SUCCESS) {
        //error case should not happen print log
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x"
              "subcmd=QCA_NL80211_VENDOR_SUBCMD_NAN", __FUNCTION__, mVendor_id);
        return res;
    }

    res = registerVendorHandler(mVendor_id, QCA_NL80211_VENDOR_SUBCMD_NDP);
    if (res != WIFI_SUCCESS) {
        //error case should not happen print log
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x"
              "subcmd=QCA_NL80211_VENDOR_SUBCMD_NDP", __FUNCTION__, mVendor_id);
        return res;
    }
    return res;
}

/* This function implements creation of Vendor command */
wifi_error NanCommand::create() {
    wifi_error ret = mMsg.create(NL80211_CMD_VENDOR, 0, 0);
    if (ret != WIFI_SUCCESS)
        goto out;

    /* Insert the oui in the msg */
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_ID, mVendor_id);
    if (ret != WIFI_SUCCESS)
        goto out;
    /* Insert the subcmd in the msg */
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_SUBCMD, mSubcmd);

out:
    if (ret != WIFI_SUCCESS)
        mMsg.destroy();
    return ret;
}

// This function will be the main handler for incoming event
// QCA_NL80211_VENDOR_SUBCMD_NAN
//Call the appropriate callback handler after parsing the vendor data.
int NanCommand::handleEvent(WifiEvent &event)
{
    WifiVendorCommand::handleEvent(event);
    ALOGV("%s: Subcmd=%u Vendor data len received:%d",
          __FUNCTION__, mSubcmd, mDataLen);
    hexdump(mVendorData, mDataLen);

    if (mSubcmd == QCA_NL80211_VENDOR_SUBCMD_NAN){
        // Parse the vendordata and get the NAN attribute
        struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_MAX + 1];
        nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_MAX,
                  (struct nlattr *)mVendorData,
                  mDataLen, NULL);
        // Populating the mNanVendorEvent and mNanDataLen to point to NAN data.
        mNanVendorEvent = (char *)nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_NAN]);
        mNanDataLen = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_NAN]);

        if (isNanResponse()) {
            //handleNanResponse will parse the data and call
            //the response callback handler with the populated
            //NanResponseMsg
            handleNanResponse();
        } else {
            //handleNanIndication will parse the data and call
            //the corresponding Indication callback handler
            //with the corresponding populated Indication event
            handleNanIndication();
        }
    } else if (mSubcmd == QCA_NL80211_VENDOR_SUBCMD_NDP) {
        // Parse the vendordata and get the NAN attribute
        u32 ndpCmdType;
        struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_PARAMS_MAX + 1];
        nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_NDP_PARAMS_MAX,
                  (struct nlattr *)mVendorData,
                  mDataLen, NULL);

        if (tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD]) {
            ndpCmdType =
                nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD]);
                ALOGD("%s: NDP Cmd Type : val 0x%x",
                      __FUNCTION__, ndpCmdType);
                switch (ndpCmdType) {
                case QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_CREATE:
                    handleNdpResponse(NAN_DP_INTERFACE_CREATE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_DELETE:
                    handleNdpResponse(NAN_DP_INTERFACE_DELETE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_INITIATOR_RESPONSE:
                    handleNdpResponse(NAN_DP_INITIATOR_RESPONSE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_RESPONDER_RESPONSE:
                    handleNdpResponse(NAN_DP_RESPONDER_RESPONSE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_END_RESPONSE:
                    handleNdpResponse(NAN_DP_END, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_REQUEST_IND:
                case QCA_WLAN_VENDOR_ATTR_NDP_CONFIRM_IND:
                case QCA_WLAN_VENDOR_ATTR_NDP_END_IND:
                case QCA_WLAN_VENDOR_ATTR_NDP_SCHEDULE_UPDATE_IND:
                    handleNdpIndication(ndpCmdType, tb_vendor);
                    break;
                default:
                    ALOGE("%s: Invalid NDP subcmd response received %d",
                          __FUNCTION__, ndpCmdType);
                }
        }
    } else {
        //error case should not happen print log
        ALOGE("%s: Wrong NAN subcmd received %d", __FUNCTION__, mSubcmd);
    }
    mNanVendorEvent = NULL;
    return NL_SKIP;
}

/*Helper function to Write and Read TLV called in indication as well as request */
u16 NANTLV_WriteTlv(pNanTlv pInTlv, u8 *pOutTlv)
{
    u16 writeLen = 0;
    u16 i;

    if (!pInTlv)
    {
        ALOGE("NULL pInTlv");
        return writeLen;
    }

    if (!pOutTlv)
    {
        ALOGE("NULL pOutTlv");
        return writeLen;
    }

    *pOutTlv++ = pInTlv->type & 0xFF;
    *pOutTlv++ = (pInTlv->type & 0xFF00) >> 8;
    writeLen += 2;

    ALOGV("WRITE TLV type %u, writeLen %u", pInTlv->type, writeLen);

    *pOutTlv++ = pInTlv->length & 0xFF;
    *pOutTlv++ = (pInTlv->length & 0xFF00) >> 8;
    writeLen += 2;

    ALOGV("WRITE TLV length %u, writeLen %u", pInTlv->length, writeLen);

    for (i=0; i < pInTlv->length; ++i)
    {
        *pOutTlv++ = pInTlv->value[i];
    }

    writeLen += pInTlv->length;
    ALOGV("WRITE TLV value, writeLen %u", writeLen);
    return writeLen;
}

u16 NANTLV_ReadTlv(u8 *pInTlv, pNanTlv pOutTlv, int inBufferSize)
{
    u16 readLen = 0;

    if (!pInTlv)
    {
        ALOGE("NULL pInTlv");
        return readLen;
    }

    if (!pOutTlv)
    {
        ALOGE("NULL pOutTlv");
        return readLen;
    }

    if(inBufferSize < NAN_TLV_HEADER_SIZE) {
        ALOGE("Insufficient length to process TLV header, inBufferSize = %d",
              inBufferSize);
        return readLen;
    }

    pOutTlv->type = *pInTlv++;
    pOutTlv->type |= *pInTlv++ << 8;
    readLen += 2;

    ALOGV("READ TLV type %u, readLen %u", pOutTlv->type, readLen);

    pOutTlv->length = *pInTlv++;
    pOutTlv->length |= *pInTlv++ << 8;
    readLen += 2;

    if(pOutTlv->length > (u16)(inBufferSize - NAN_TLV_HEADER_SIZE)) {
        ALOGE("Insufficient length to process TLV header, inBufferSize = %d",
              inBufferSize);
        return readLen;
    }

    ALOGV("READ TLV length %u, readLen %u", pOutTlv->length, readLen);

    if (pOutTlv->length) {
        pOutTlv->value = pInTlv;
        readLen += pOutTlv->length;
    } else {
        pOutTlv->value = NULL;
    }

    ALOGV("READ TLV  readLen %u", readLen);
    return readLen;
}

u8* addTlv(u16 type, u16 length, const u8* value, u8* pOutTlv)
{
   NanTlv nanTlv;
   u16 len;

   nanTlv.type = type;
   nanTlv.length = length;
   nanTlv.value = (u8*)value;

   len = NANTLV_WriteTlv(&nanTlv, pOutTlv);
   return (pOutTlv + len);
}
