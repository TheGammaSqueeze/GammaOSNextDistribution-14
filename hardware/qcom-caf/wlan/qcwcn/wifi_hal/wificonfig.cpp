/* Copyright (c) 2015, 2018 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
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
#define LOG_TAG  "WifiHAL"
#include <utils/Log.h>
#include <time.h>
#include <errno.h>
#include <stdlib.h>
#include <unistd.h>
#include <string>
#include <net/if.h>
#include <vector>
#include <sys/ioctl.h>
#include <net/if.h>
#include <net/if_arp.h>
#include <sys/socket.h>
#include "wificonfigcommand.h"
#include "ifaceeventhandler.h"


#define NUM_OF_SAR_LIMITS_SPECS 2
#define NUM_OF_SPEC_CHAINS 2



/* Implementation of the API functions exposed in wifi_config.h */
wifi_error wifi_extended_dtim_config_set(wifi_request_id id,
                                         wifi_interface_handle iface,
                                         int extended_dtim)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    ALOGV("%s: extended_dtim:%d", __FUNCTION__, extended_dtim);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            id,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);

    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_extended_dtim_config_set: failed to create NL msg. "
            "Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_extended_dtim_config_set: failed to set iface id. "
            "Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_extended_dtim_config_set: failed attr_start for "
            "VENDOR_DATA. Error:%d", ret);
        goto cleanup;
    }

    ret = wifiConfigCommand->put_u32(
                  QCA_WLAN_VENDOR_ATTR_CONFIG_DYNAMIC_DTIM, extended_dtim);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_extended_dtim_config_set(): failed to put vendor data. "
            "Error:%d", ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_extended_dtim_config_set(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}

int check_feature(enum qca_wlan_vendor_features feature, features_info *info)
{
    size_t idx = feature / 8;

    return (idx < info->flags_len) &&
            (info->flags[idx] & BIT(feature % 8));
}

static wifi_error wifi_get_country_code(wifi_interface_handle iface,
                                        char *country, int wiphy_index)
{
    int requestId;
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    wifi_handle wifiHandle = getWifiHandle(iface);


    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate it randomly.
     */
    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = wifiConfigCommand->create_generic(NL80211_CMD_GET_REG);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to create NL msg. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    wifiConfigCommand->put_u32(NL80211_ATTR_WIPHY, wiphy_index);

    ret = wifiConfigCommand->requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to request. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    strlcpy(country, wifiConfigCommand->getCountryCode(), 3);
    if (country[0] == '\0') {
        ALOGE("%s: country code fetch failed", __FUNCTION__);
        ret = (ret == WIFI_SUCCESS ? WIFI_ERROR_UNKNOWN : ret);
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}

static wifi_error wifi_get_wiphy_index(wifi_interface_handle iface,
                                       int *wiphy_index)
{
    int requestId;
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);


    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate it randomly.
     */
    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = wifiConfigCommand->create_generic(NL80211_CMD_GET_INTERFACE);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to create NL msg. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to set iface id. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    ret = wifiConfigCommand->requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to request. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    *wiphy_index = wifiConfigCommand->getWiphyIndex();
    if (*wiphy_index < 0) {
        ALOGE("%s: wiphy index fetch failed", __FUNCTION__);
        ret = (ret == WIFI_SUCCESS ? WIFI_ERROR_UNKNOWN : ret);
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}

/* Set the country code to driver. */
wifi_error wifi_set_country_code(wifi_interface_handle iface,
                                 const char* new_country_code)
{
    int requestId, count, wiphy_index;
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);
    char current_country_code[4];
    bool check_update = false;

    ALOGV("%s: new country code %s", __FUNCTION__, new_country_code);

    ret = wifi_get_wiphy_index(iface, &wiphy_index);
    if (ret != WIFI_SUCCESS)
        goto set_country;

    ret = wifi_get_country_code(iface, current_country_code, wiphy_index);
    if (ret != WIFI_SUCCESS)
        goto set_country;

    ALOGV("%s: current country code %s", __FUNCTION__, current_country_code);
    if (strncmp(current_country_code, new_country_code, 2) != 0)
        check_update = true;

set_country:
    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate it randomly.
     */
    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message with NL80211_CMD_REQ_SET_REG NL cmd. */
    ret = wifiConfigCommand->create_generic(NL80211_CMD_REQ_SET_REG);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_country_code: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    ret = wifiConfigCommand->put_string(NL80211_ATTR_REG_ALPHA2,
                                        new_country_code);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_country_code: put country code failed. Error:%d", ret);
        goto cleanup;
    }

    if (check_feature(QCA_WLAN_VENDOR_FEATURE_SELF_MANAGED_REGULATORY,
                      &info->driver_supported_features)) {
        ret = wifiConfigCommand->put_u32(NL80211_ATTR_USER_REG_HINT_TYPE,
                                         NL80211_USER_REG_HINT_CELL_BASE);
        if (ret != WIFI_SUCCESS) {
            ALOGE("wifi_set_country_code: put reg hint type failed. Error:%d",
                  ret);
            goto cleanup;
        }
    }


    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_country_code(): requestEvent Error:%d", ret);
        goto cleanup;
    }

    if (!check_update) {
        /* Wait for default time and return */
        usleep(WAIT_TIME_FOR_SET_REG_DOMAIN);
        goto cleanup;
    }

    count = 0;
    while (count < ITER_COUNT_FOR_SET_REG_DOMAIN) {

        /* Wait for longer duration for first iteration */
        if (count == 0)
            usleep(3 * WAIT_TIME_FOR_SET_REG_DOMAIN);
        else
            usleep(WAIT_TIME_FOR_SET_REG_DOMAIN);

        if (wifi_get_country_code(iface, current_country_code, wiphy_index) !=
            WIFI_SUCCESS) {
            ALOGE("%s: updated country code fetch failed", __FUNCTION__);
            break;
        }

        if (strncmp(current_country_code, new_country_code, 2) == 0) {
            ALOGV("%s: country code updated", __FUNCTION__);
            break;
        }

        ALOGV("%s: country code not updated. wait and check again",
              __FUNCTION__);
        count++;
    }

    if (count == ITER_COUNT_FOR_SET_REG_DOMAIN)
        ALOGE("%s: country code update timeout", __FUNCTION__);

cleanup:
    delete wifiConfigCommand;
    return ret;
}

/*
 * Set the powersave to driver.
 */
wifi_error wifi_set_qpower(wifi_interface_handle iface,
                                 u8 powersave)
{
    int requestId, ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    //hal_info *info = getHalInfo(wifiHandle);

    ALOGD("%s: %d", __FUNCTION__, powersave);

    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);

    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("wifi_set_qpower: failed to create NL msg. "
            "Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("wifi_set_qpower: failed to set iface id. "
            "Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_set_qpower: failed attr_start for "
            "VENDOR_DATA. Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u8(
        QCA_WLAN_VENDOR_ATTR_CONFIG_QPOWER, powersave)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_set_qpower(): failed to put vendor data. "
            "Error:%d", ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("wifi_set_qpower(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return (wifi_error)ret;

}

wifi_error wifi_set_beacon_wifi_iface_stats_averaging_factor(
                                                wifi_request_id id,
                                                wifi_interface_handle iface,
                                                u16 factor)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    ALOGV("%s factor:%u", __FUNCTION__, factor);
    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            id,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor: failed to "
            "create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor: failed to "
            "set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor: failed "
            "attr_start for VENDOR_DATA. Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
        QCA_WLAN_VENDOR_ATTR_CONFIG_STATS_AVG_FACTOR, factor)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor(): failed to "
            "put vendor data. Error:%d", ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor(): "
            "requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}

wifi_error wifi_set_guard_time(wifi_request_id id,
                               wifi_interface_handle iface,
                               u32 guard_time)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    ALOGV("%s : guard_time:%u", __FUNCTION__, guard_time);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            id,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_guard_time: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_guard_time: failed to set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_set_guard_time: failed attr_start for VENDOR_DATA. "
            "Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
        QCA_WLAN_VENDOR_ATTR_CONFIG_GUARD_TIME, guard_time)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_set_guard_time: failed to add vendor data.");
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_set_guard_time(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}


wifi_error wifi_select_SARv01_tx_power_scenario(wifi_interface_handle handle,
                                         wifi_power_scenario scenario)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);
    u32 bdf_file = 0;

    ALOGV("%s : power scenario:%d", __FUNCTION__, scenario);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            1,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_SAR_LIMITS);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_select_tx_power_scenario: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_select_tx_power_scenario: failed to set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_select_tx_power_scenario: failed attr_start for VENDOR_DATA. "
            "Error:%d", ret);
        goto cleanup;
    }

    switch (scenario) {
        case WIFI_POWER_SCENARIO_VOICE_CALL:
        case WIFI_POWER_SCENARIO_ON_HEAD_CELL_OFF:
            bdf_file = QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_BDF0;
            break;

        case WIFI_POWER_SCENARIO_ON_HEAD_CELL_ON:
            bdf_file = QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_BDF1;
            break;

        case WIFI_POWER_SCENARIO_ON_BODY_CELL_OFF:
            bdf_file = QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_BDF2;
            break;

        case WIFI_POWER_SCENARIO_ON_BODY_CELL_ON:
            bdf_file = QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_BDF3;
            break;

        default:
            ALOGE("wifi_select_tx_power_scenario: invalid scenario %d", scenario);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
                      QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SAR_ENABLE,
                      bdf_file)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("failed to put SAR_ENABLE");
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_select_tx_power_scenario(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}

wifi_error wifi_select_SARv02_tx_power_scenario(wifi_interface_handle handle,
                                         wifi_power_scenario scenario)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData, *nlSpecList, *nlSpec;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);
    u32 power_lim_idx = 0;

    ALOGV("%s : power scenario SARV2:%d", __FUNCTION__, scenario);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            1,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_SAR_LIMITS);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_select_tx_power_scenario: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_select_tx_power_scenario: failed to set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_select_tx_power_scenario: failed attr_start for VENDOR_DATA.");
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
                  QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SAR_ENABLE,
                  QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_V2_0)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("failed to put SAR_ENABLE");
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
                  QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_NUM_SPECS,
                  NUM_OF_SAR_LIMITS_SPECS)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("failed to put SAR_LIMITS_NUM_SPECS");
        goto cleanup;
    }

    switch (scenario) {
        case WIFI_POWER_SCENARIO_VOICE_CALL:
        case WIFI_POWER_SCENARIO_ON_HEAD_CELL_ON:
        case WIFI_POWER_SCENARIO_ON_HEAD_HOTSPOT:
        case WIFI_POWER_SCENARIO_ON_HEAD_HOTSPOT_MMW:

            power_lim_idx = 0;
            break;

        case WIFI_POWER_SCENARIO_ON_HEAD_CELL_OFF:
            power_lim_idx = 1;
            break;

        case WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT:
        case WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT_BT:
        case WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT_MMW:
        case WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT_BT_MMW:
            power_lim_idx = 2;
            break;

        case WIFI_POWER_SCENARIO_ON_BODY_CELL_ON:
            power_lim_idx = 3;
            break;

        case WIFI_POWER_SCENARIO_ON_BODY_CELL_ON_BT:
            power_lim_idx = 4;
            break;

        case WIFI_POWER_SCENARIO_ON_BODY_CELL_OFF:
        case WIFI_POWER_SCENARIO_ON_BODY_BT:
            power_lim_idx = 5;
            break;
        default:
            ALOGE("wifi_select_tx_power_scenario: invalid scenario %d", scenario);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto cleanup;
    }


    nlSpecList = wifiConfigCommand->attr_start(QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SPEC);
    if(!nlSpecList)
    {
        ALOGE("Cannot create spec list");
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }


    for (int i = 0; i < NUM_OF_SPEC_CHAINS; i++) {
        nlSpec = wifiConfigCommand->attr_start(0);
        if(!nlSpec) {
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }
        if(wifiConfigCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SPEC_CHAIN,
            i))
        {
            ALOGE("Failed to put: QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SPEC_CHAIN");
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }

        if(wifiConfigCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SPEC_POWER_LIMIT_INDEX,
            power_lim_idx))
        {
            ALOGE("Failed to put: QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SPEC_POWER_LIMIT_INDEX");
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }

        wifiConfigCommand->attr_end(nlSpec);
    }



    wifiConfigCommand->attr_end(nlSpecList);

    wifiConfigCommand->attr_end(nlData);
    ALOGV("wifi_select_tx_power_scenario %u selected", power_lim_idx);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_select_tx_power_scenario(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}


wifi_error wifi_select_tx_power_scenario(wifi_interface_handle handle,
                                         wifi_power_scenario scenario)
{

    wifi_handle wifiHandle = getWifiHandle(handle);
    hal_info *info = getHalInfo(wifiHandle);
    if (info == NULL) {
        ALOGE("%s: Error hal_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    ALOGV("wifi_select_tx_power_scenario: sarVer%u", (u32)info->sar_version);
    if  (info->sar_version == QCA_WLAN_VENDOR_SAR_VERSION_1)
        return wifi_select_SARv01_tx_power_scenario(handle,scenario);
    else if(info->sar_version == QCA_WLAN_VENDOR_SAR_VERSION_2 ||
              info->sar_version == QCA_WLAN_VENDOR_SAR_VERSION_3)
        return wifi_select_SARv02_tx_power_scenario(handle,scenario);
    else {
      ALOGE("wifi_select_tx_power_scenario %u invalid or not supported", (u32)info->sar_version);
      return WIFI_ERROR_UNKNOWN;
    }
}


wifi_error wifi_reset_tx_power_scenario(wifi_interface_handle handle)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            1,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_SAR_LIMITS);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_reset_tx_power_scenario: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_reset_tx_power_scenario: failed to set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("wifi_reset_tx_power_scenario: failed attr_start for VENDOR_DATA. "
            "Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SAR_ENABLE,
                               QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_NONE)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("failed to put SAR_ENABLE or NUM_SPECS");
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_reset_tx_power_scenario(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}

wifi_error wifi_set_thermal_mitigation_mode(wifi_handle handle,
                                            wifi_thermal_mode mode,
                                            u32 completion_window)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    u32 qca_vendor_thermal_level;
    hal_info *info = getHalInfo(handle);

    if (!info || info->num_interfaces < 1) {
         ALOGE("%s: Error wifi_handle NULL or base wlan interface not present",
               __FUNCTION__);
         return WIFI_ERROR_UNKNOWN;
    }

    wifiConfigCommand = new WiFiConfigCommand(
                            handle,
                            1,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_THERMAL_CMD);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error, Failed to create wifiConfigCommand", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to create thermal vendor command, Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    if (wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX,
                                   info->interfaces[0]->id)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: Failed to put iface id", __FUNCTION__);
        goto cleanup;
    }

    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: Failed in attr_start for VENDOR_DATA, Error:%d",
              __FUNCTION__, ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(QCA_WLAN_VENDOR_ATTR_THERMAL_CMD_VALUE,
                             QCA_WLAN_VENDOR_ATTR_THERMAL_CMD_TYPE_SET_LEVEL)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("Failed to put THERMAL_LEVEL command type");
        goto cleanup;
    }

    switch(mode) {
        case WIFI_MITIGATION_NONE:
            qca_vendor_thermal_level = QCA_WLAN_VENDOR_THERMAL_LEVEL_NONE;
            break;
        case WIFI_MITIGATION_LIGHT:
            qca_vendor_thermal_level = QCA_WLAN_VENDOR_THERMAL_LEVEL_LIGHT;
            break;
        case WIFI_MITIGATION_MODERATE:
            qca_vendor_thermal_level = QCA_WLAN_VENDOR_THERMAL_LEVEL_MODERATE;
            break;
        case WIFI_MITIGATION_SEVERE:
            qca_vendor_thermal_level = QCA_WLAN_VENDOR_THERMAL_LEVEL_SEVERE;
            break;
        case WIFI_MITIGATION_CRITICAL:
            qca_vendor_thermal_level = QCA_WLAN_VENDOR_THERMAL_LEVEL_CRITICAL;
            break;
        case WIFI_MITIGATION_EMERGENCY:
            qca_vendor_thermal_level = QCA_WLAN_VENDOR_THERMAL_LEVEL_EMERGENCY;
            break;
        default:
            ALOGE("Unknown thermal mitigation level %d", mode);
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
                             QCA_WLAN_VENDOR_ATTR_THERMAL_LEVEL,
                             qca_vendor_thermal_level)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("Failed to put thermal level");
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
                             QCA_WLAN_VENDOR_ATTR_THERMAL_COMPLETION_WINDOW,
                             completion_window)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("Failed to put thermal completion window");
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to set thermal level with Error: %d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}

WiFiConfigCommand::WiFiConfigCommand(wifi_handle handle,
                                     int id, u32 vendor_id,
                                     u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    /* Initialize the member data variables here */
    mWaitforRsp = false;
    mRequestId = id;
    mWiphyIndex = -1;
    mCountryCode[0] = '\0';
}

WiFiConfigCommand::~WiFiConfigCommand()
{
    unregisterVendorHandler(mVendor_id, mSubcmd);
}

/* This function implements creation of Vendor command */
wifi_error WiFiConfigCommand::create()
{
    wifi_error ret = mMsg.create(NL80211_CMD_VENDOR, 0, 0);
    if (ret != WIFI_SUCCESS)
        return ret;

    /* Insert the oui in the msg */
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_ID, mVendor_id);
    if (ret != WIFI_SUCCESS)
        return ret;
    /* Insert the subcmd in the msg */
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_SUBCMD, mSubcmd);

    return ret;
}

/* This function implements creation of generic NL command */
wifi_error WiFiConfigCommand::create_generic(u8 cmdId)
{
    wifi_error ret = mMsg.create(cmdId, 0, 0);
    return ret;
}

void WiFiConfigCommand::waitForRsp(bool wait)
{
    mWaitforRsp = wait;
}

/* Callback handlers registered for nl message send */
static int error_handler_wifi_config(struct sockaddr_nl *nla,
                                     struct nlmsgerr *err,
                                     void *arg)
{
    struct sockaddr_nl *tmp;
    int *ret = (int *)arg;
    tmp = nla;
    *ret = err->error;
    ALOGE("%s: Error code:%d (%s)", __FUNCTION__, *ret, strerror(-(*ret)));
    return NL_STOP;
}

/* Callback handlers registered for nl message send */
static int ack_handler_wifi_config(struct nl_msg *msg, void *arg)
{
    int *ret = (int *)arg;
    struct nl_msg * a;

    a = msg;
    *ret = 0;
    return NL_STOP;
}

/* Callback handlers registered for nl message send */
static int finish_handler_wifi_config(struct nl_msg *msg, void *arg)
{
  int *ret = (int *)arg;
  struct nl_msg * a;

  a = msg;
  *ret = 0;
  return NL_SKIP;
}

/*
 * Override base class requestEvent and implement little differently here.
 * This will send the request message.
 * We don't wait for any response back in case of wificonfig,
 * thus no wait for condition.
 */
wifi_error WiFiConfigCommand::requestEvent()
{
    int status;
    wifi_error res = WIFI_SUCCESS;
    struct nl_cb *cb = NULL;

    cb = nl_cb_alloc(NL_CB_DEFAULT);
    if (!cb) {
        ALOGE("%s: Callback allocation failed",__FUNCTION__);
        res = WIFI_ERROR_OUT_OF_MEMORY;
        goto out;
    }

    status = nl_send_auto_complete(mInfo->cmd_sock, mMsg.getMessage());
    if (status < 0) {
        res = mapKernelErrortoWifiHalError(status);
        goto out;
    }
    status = 1;

    nl_cb_err(cb, NL_CB_CUSTOM, error_handler_wifi_config, &status);
    nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler_wifi_config,
        &status);
    nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler_wifi_config, &status);

    /* Err is populated as part of finish_handler. */
    while (status > 0) {
         nl_recvmsgs(mInfo->cmd_sock, cb);
    }

    if (status < 0) {
        res = mapKernelErrortoWifiHalError(status);
        goto out;
    }

    if (mWaitforRsp == true) {
        struct timespec abstime;
        abstime.tv_sec = 4;
        abstime.tv_nsec = 0;
        res = mCondition.wait(abstime);
        if (res == WIFI_ERROR_TIMED_OUT)
            ALOGE("%s: Time out happened.", __FUNCTION__);

        ALOGV("%s: Command invoked return value:%d, mWaitForRsp=%d",
            __FUNCTION__, res, mWaitforRsp);
    }
out:
    nl_cb_put(cb);
    /* Cleanup the mMsg */
    mMsg.destroy();
    return res;
}

int WiFiConfigCommand::getWiphyIndex()
{
    return mWiphyIndex;
}

const char * WiFiConfigCommand::getCountryCode()
{
    return mCountryCode;
}

wifi_error WiFiConfigCommand::requestResponse()
{
    return WifiCommand::requestResponse(mMsg);
}

int WiFiConfigCommand::handleResponse(WifiEvent &reply)
{
    struct nlattr **tb = reply.attributes();
    struct genlmsghdr *gnlh = reply.header();

    WifiVendorCommand::handleResponse(reply);

    if (gnlh->cmd == NL80211_CMD_GET_REG) {
        if (tb[NL80211_ATTR_REG_ALPHA2]) {
            strlcpy(mCountryCode,
                    (const char*) nla_data(tb[NL80211_ATTR_REG_ALPHA2]), 3);
            ALOGV("%s: reported country code: %s", __FUNCTION__, mCountryCode);
        }
    }

    if (gnlh->cmd == NL80211_CMD_NEW_INTERFACE) {
        if (tb[NL80211_ATTR_WIPHY]) {
            mWiphyIndex = nla_get_u32(tb[NL80211_ATTR_WIPHY]);
            ALOGV("%s: reported wiphy index: %d", __FUNCTION__, mWiphyIndex);
        }
    }

    return NL_SKIP;
}


static std::vector<std::string> added_ifaces;

static bool is_dynamic_interface(const char * ifname)
{
    for (const auto& iface : added_ifaces) {
        if (iface == std::string(ifname))
            return true;
    }
    return false;
}

void wifi_cleanup_dynamic_ifaces(wifi_handle handle)
{
    int len = added_ifaces.size();
    while (len--) {
        wifi_virtual_interface_delete(handle, added_ifaces.front().c_str());
    }
    added_ifaces.clear(); // could be redundent. But to be on safe side.
}

static wifi_error wifi_set_interface_mode(wifi_handle handle,
                                   const char* ifname,
                                   u32 iface_type)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;

    ALOGD("%s: ifname=%s iface_type=%u", __FUNCTION__, ifname, iface_type);

    wifiConfigCommand = new WiFiConfigCommand(handle, get_requestid(), 0, 0);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nl80211_iftype type;
    switch(iface_type) {
        case WIFI_INTERFACE_TYPE_STA:    /* IfaceType:STA */
            type = NL80211_IFTYPE_STATION;
            break;
        case WIFI_INTERFACE_TYPE_AP:    /* IfaceType:AP */
            type = NL80211_IFTYPE_AP;
            break;
        case WIFI_INTERFACE_TYPE_P2P:    /* IfaceType:P2P */
            type = NL80211_IFTYPE_P2P_DEVICE;
            break;
        case WIFI_INTERFACE_TYPE_NAN:    /* IfaceType:NAN */
            type = NL80211_IFTYPE_NAN;
            break;
        default:
            ALOGE("%s: Wrong interface type %u", __FUNCTION__, iface_type);
            ret = WIFI_ERROR_UNKNOWN;
            goto done;
            break;
    }
    wifiConfigCommand->create_generic(NL80211_CMD_SET_INTERFACE);
    wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX, if_nametoindex(ifname));
    wifiConfigCommand->put_u32(NL80211_ATTR_IFTYPE, type);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }

done:
    delete wifiConfigCommand;
    return ret;
}

wifi_error wifi_virtual_interface_create(wifi_handle handle,
                                         const char* ifname,
                                         wifi_interface_type iface_type)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    hal_info *info = getHalInfo(handle);
    if (!info || info->num_interfaces < 1) {
        ALOGE("%s: Error wifi_handle NULL or base wlan interface not present", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    // Already exists and set interface mode only
    if (if_nametoindex(ifname) != 0) {
        return wifi_set_interface_mode(handle, ifname, iface_type);
    }

    ALOGD("%s: ifname=%s create", __FUNCTION__, ifname);

    wifiConfigCommand = new WiFiConfigCommand(handle, get_requestid(), 0, 0);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    nl80211_iftype type;
    switch(iface_type) {
        case WIFI_INTERFACE_TYPE_STA:    /* IfaceType:STA */
            type = NL80211_IFTYPE_STATION;
            break;
        case WIFI_INTERFACE_TYPE_AP:    /* IfaceType:AP */
            type = NL80211_IFTYPE_AP;
            break;
        case WIFI_INTERFACE_TYPE_P2P:    /* IfaceType:P2P */
            type = NL80211_IFTYPE_P2P_DEVICE;
            break;
        case WIFI_INTERFACE_TYPE_NAN:    /* IfaceType:NAN */
            type = NL80211_IFTYPE_NAN;
            break;
        default:
            ALOGE("%s: Wrong interface type %u", __FUNCTION__, iface_type);
            ret = WIFI_ERROR_UNKNOWN;
            goto done;
            break;
    }
    wifiConfigCommand->create_generic(NL80211_CMD_NEW_INTERFACE);
    wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX,info->interfaces[0]->id);
    wifiConfigCommand->put_string(NL80211_ATTR_IFNAME, ifname);
    wifiConfigCommand->put_u32(NL80211_ATTR_IFTYPE, type);
    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: requestEvent Error:%d", __FUNCTION__,ret);
    }
    // Update dynamic interface list
    added_ifaces.push_back(std::string(ifname));
    if (iface_type == WIFI_INTERFACE_TYPE_STA) {
         int sock = socket(AF_INET, SOCK_DGRAM, 0);
         if(sock < 0) {
             ret = WIFI_ERROR_UNKNOWN;
             ALOGE("%s :socket error, Failed to bring up iface \n", __func__);
             goto done;
        }
        struct ifreq ifr;
        memset(&ifr, 0, sizeof(ifr));
        strlcpy(ifr.ifr_name, ifname, IFNAMSIZ);
        if (ioctl(sock, SIOCGIFFLAGS, &ifr) != 0) {
            ret = WIFI_ERROR_UNKNOWN;
            ALOGE("%s :Could not read interface %s flags \n", __func__, ifname);
            goto done;
        }
        ifr.ifr_flags |= IFF_UP;
        if (ioctl(sock, SIOCSIFFLAGS, &ifr) != 0) {
            ret = WIFI_ERROR_UNKNOWN;
            ALOGE("%s :Could not bring iface %s up \n", __func__, ifname);
        }
    }

done:
    delete wifiConfigCommand;
    return ret;
}

wifi_error wifi_virtual_interface_delete(wifi_handle handle,
                                         const char* ifname)
{
    wifi_error ret;
    WiFiConfigCommand *wifiConfigCommand;
    if (!handle) {
        ALOGE("%s: Error wifi_handle NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGD("%s: ifname=%s delete", __FUNCTION__, ifname);
    if (if_nametoindex(ifname) && !is_dynamic_interface(ifname)) {
         // Do not remove interface if it was not added dynamically.
         return WIFI_SUCCESS;
    }
    wifiConfigCommand = new WiFiConfigCommand(handle, get_requestid(), 0, 0);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    wifiConfigCommand->create_generic(NL80211_CMD_DEL_INTERFACE);
    wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX, if_nametoindex(ifname));
    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__,ret);
    }
    // Update dynamic interface list
    added_ifaces.erase(std::remove(added_ifaces.begin(), added_ifaces.end(), std::string(ifname)),
                           added_ifaces.end());

    delete wifiConfigCommand;
    return ret;
}

/**
 * Set latency level
 */
wifi_error wifi_set_latency_mode(wifi_interface_handle iface,
                                 wifi_latency_mode mode)
{
    int requestId, ret = 0;
    u16 level;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    ALOGD("%s: %d", __FUNCTION__, mode);

    if (!(info->supported_feature_set & WIFI_FEATURE_SET_LATENCY_MODE)) {
        ALOGE("%s: Latency Mode is not supported by driver", __FUNCTION__);
        return WIFI_ERROR_NOT_SUPPORTED;
    };

    switch (mode) {
    case WIFI_LATENCY_MODE_NORMAL:
        level = QCA_WLAN_VENDOR_ATTR_CONFIG_LATENCY_LEVEL_NORMAL;
        break;
    case WIFI_LATENCY_MODE_LOW:
        level = QCA_WLAN_VENDOR_ATTR_CONFIG_LATENCY_LEVEL_ULTRALOW;
        break;
    default:
        ALOGI("%s: Unsupported latency mode=%d, resetting to NORMAL!", __FUNCTION__, mode);
        level = QCA_WLAN_VENDOR_ATTR_CONFIG_LATENCY_LEVEL_NORMAL;
        break;
    }

    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);

    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("%s: failed to create NL msg. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("%s: failed to set iface id. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: failed attr_start for VENDOR_DATA. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u16(
        QCA_WLAN_VENDOR_ATTR_CONFIG_LATENCY_LEVEL, level)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: failed to put vendor data. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return (wifi_error)ret;
}

/**
 * Set STA + STA primary iface connection
 */
wifi_error wifi_multi_sta_set_primary_connection(wifi_handle handle,
                                 wifi_interface_handle iface)
{
    int requestId, ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    if (!handle) {
        ALOGE("%s: Error wifi_handle NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);

    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            handle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);

    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("%s: failed to create NL msg. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("%s: failed to set iface id. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: failed attr_start for VENDOR_DATA. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u8(
        QCA_WLAN_VENDOR_ATTR_CONFIG_CONCURRENT_STA_PRIMARY, 1)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: failed to put vendor data. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return (wifi_error)ret;
}

/**
 * Set STA + STA use case
 */
wifi_error wifi_multi_sta_set_use_case(wifi_handle handle,
                                       wifi_multi_sta_use_case case_info)
{
    int requestId, ret = 0;
    u8 use_case;
    WiFiConfigCommand *wifiConfigCommand;
    if (!handle) {
        ALOGE("%s: Error wifi_handle NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    struct nlattr *nlData;
    hal_info *info = getHalInfo(handle);
    if (!info || info->num_interfaces < 1) {
        ALOGE("%s: Error wifi_handle NULL or base wlan interface not present", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGD("%s: %d", __FUNCTION__, case_info);

    switch (case_info) {
    case WIFI_DUAL_STA_TRANSIENT_PREFER_PRIMARY:
        use_case = QCA_WLAN_CONCURRENT_STA_POLICY_PREFER_PRIMARY;
        break;
    case WIFI_DUAL_STA_NON_TRANSIENT_UNBIASED:
        use_case = QCA_WLAN_CONCURRENT_STA_POLICY_UNBIASED;
        break;
    default:
        ALOGE("%s: Unknown use case %d", __FUNCTION__, case_info);
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;;
    }

    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            handle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_CONCURRENT_MULTI_STA_POLICY);

    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("%s: failed to create NL msg. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    if (wifiConfigCommand->put_u32(NL80211_ATTR_IFINDEX,
                                   info->interfaces[0]->id)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: Failed to put iface id", __FUNCTION__);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: failed attr_start for VENDOR_DATA. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u8(
        QCA_WLAN_VENDOR_ATTR_CONCURRENT_STA_POLICY_CONFIG, use_case)) {
        ret = WIFI_ERROR_UNKNOWN;
        ALOGE("%s: failed to put use_case. Error:%d",
            __FUNCTION__, ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return (wifi_error)ret;
}

/**
 * Invoked to set voip optimization mode for the provided STA iface
 */
 wifi_error wifi_set_voip_mode(wifi_interface_handle iface, wifi_voip_mode mode)
{
    int requestId;
    WiFiConfigCommand *wifiConfigCommand;
    wifi_error ret;

    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);

    wifi_handle wifiHandle = getWifiHandle(iface);
    if (!wifiHandle) {
        ALOGE("%s: Error wifi_handle NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    requestId = get_requestid();
    ALOGV("%s: voip mode=%d", __FUNCTION__, mode);
    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);

    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("%s: failed to create NL msg. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("%s: failed to set iface id. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ALOGE("%s: failed attr_start for VENDOR_DATA", __FUNCTION__);
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    if (wifiConfigCommand->put_u8(QCA_WLAN_VENDOR_ATTR_CONFIG_WFC_STATE,
        (mode==WIFI_VOIP_MODE_VOICE) ? 1 : 0)) {
        ALOGE("%s: failed to put vendor data", __FUNCTION__);
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return ret;
}
