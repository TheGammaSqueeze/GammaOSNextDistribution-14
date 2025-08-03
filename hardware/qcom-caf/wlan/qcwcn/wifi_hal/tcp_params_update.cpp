/* Copyright (c) 2018-2019, The Linux Foundation. All rights reserved.
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

 * Changes from Qualcomm Innovation Center are provided under the following license:

 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#include "sync.h"

#include <utils/Log.h>

#include "wifi_hal.h"
#include "common.h"
#include "cpp_bindings.h"
#include "vendor_definitions.h"
#include <netlink/genl/genl.h>
#include <string.h>
#include <net/if.h>
#include "tcp_params_update.h"

TCPParamCommand::TCPParamCommand(wifi_handle handle, int id,
                    u32 vendor_id, u32 subcmd)
    : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    if (registerVendorHandler(vendor_id, subcmd)) {
        /* Error case should not happen print log */
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x subcmd=%u",
                        __FUNCTION__, vendor_id, subcmd);
    }
    memset(def_tcp_limit_output_bytes, 0, SIZE_TCP_PARAM);
    memset(def_tcp_adv_win_scale, 0, SIZE_TCP_PARAM);
    def_tcp_limit_output_bytes_valid = false;
    def_tcp_adv_win_scale_valid = false;
}

TCPParamCommand::~TCPParamCommand()
{
    unregisterVendorHandler(OUI_QCA, QCA_NL80211_VENDOR_SUBCMD_THROUGHPUT_CHANGE_EVENT);
}

TCPParamCommand *TCPParamCommand::instance(wifi_handle handle, wifi_request_id id)
{
    TCPParamCommand* mTCPParamCommandInstance;

    mTCPParamCommandInstance = new TCPParamCommand(handle, id, OUI_QCA,
                        QCA_NL80211_VENDOR_SUBCMD_THROUGHPUT_CHANGE_EVENT);
    return mTCPParamCommandInstance;
}

/* This function will be the main handler for incoming event.
 * Call the appropriate callback handler after parsing the vendor data.
 */
int TCPParamCommand::handleEvent(WifiEvent &event)
{
    wifi_error ret = WIFI_ERROR_UNKNOWN;
    WifiVendorCommand::handleEvent(event);

    u8 tpDirection, tpLevel;
    u32 tcpLimitOutputBytes;
    u8 tcpLimitOutputBytesFlag = 0;
    s8 tcpAdvWinScale;
    u8 tcpAdvWinScaleFlag = 0;
    u32 tcpDelackSeg;
    u8 tcpDelackSegFlag = 0;
    char value_to_str[100];
    int ret_val = 0;

    /* Parse the vendordata and get the attribute */
    switch(mSubcmd) {
    case QCA_NL80211_VENDOR_SUBCMD_THROUGHPUT_CHANGE_EVENT:
    {
        struct nlattr *tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_MAX + 1];

        nla_parse(tb, QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_MAX,
                    (struct nlattr *)mVendorData, mDataLen, NULL);

        if (!tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_DIRECTION] ||
            !tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_THROUGHPUT_LEVEL]) {
            ALOGE("Invalid event, didn't receive mandatory attributes");
            return WIFI_ERROR_INVALID_ARGS;
        }
        tpDirection = nla_get_u8(tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_DIRECTION]);
        tpLevel = nla_get_u8(tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_THROUGHPUT_LEVEL]);

        if (tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_TCP_LIMIT_OUTPUT_BYTES]) {
            tcpLimitOutputBytes = nla_get_u32(tb[
            QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_TCP_LIMIT_OUTPUT_BYTES]);
            tcpLimitOutputBytesFlag = 1;
        }

        if (tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_TCP_ADV_WIN_SCALE]) {
            tcpAdvWinScale = *(s8 *)nla_data(tb[
            QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_TCP_ADV_WIN_SCALE]);
            tcpAdvWinScaleFlag = 1;
        }

        if (tb[QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_TCP_DELACK_SEG]) {
            tcpDelackSeg = nla_get_u32(tb[
            QCA_WLAN_VENDOR_ATTR_THROUGHPUT_CHANGE_TCP_DELACK_SEG]);
            tcpDelackSegFlag = 1;
        }
        if (tpDirection == TP_CHANGE_RX) {
            switch(tpLevel) {
            case QCA_WLAN_THROUGHPUT_LEVEL_LOW:
            {
                if (def_tcp_adv_win_scale_valid)
                    wlan_service_set_tcp_adv_win_scale(def_tcp_adv_win_scale);
                wlan_service_set_tcp_use_userconfig("0");
            }
            break;
            case QCA_WLAN_THROUGHPUT_LEVEL_MEDIUM:
            case QCA_WLAN_THROUGHPUT_LEVEL_HIGH:
            {
                if (tcpAdvWinScaleFlag) {
                    ret_val = snprintf(value_to_str, sizeof(value_to_str), "%d",
                                            tcpAdvWinScale);
                    if (ret_val < 0 || ret_val >= (int)sizeof(value_to_str)) {
                        ALOGE("Error in converting value to string: %d", ret_val);
                        ret = WIFI_ERROR_UNKNOWN;
                        goto cleanup;
                    }
                    wlan_service_set_tcp_adv_win_scale(value_to_str);
                }
                if (tcpDelackSegFlag && wlan_service_set_tcp_use_userconfig("1") == 0) {
                    ret_val = snprintf(value_to_str, sizeof(value_to_str), "%d",
                                            tcpDelackSeg);
                    if (ret_val < 0 || ret_val >= (int)sizeof(value_to_str)) {
                        ALOGE("Error in converting value to string: %d", ret_val);
                        ret = WIFI_ERROR_UNKNOWN;
                        goto cleanup;
                    }
                    wlan_service_set_tcp_delack_seg(value_to_str);
                 }
            }
            break;
            default:
            {
                /* Error case should not happen print log */
                ALOGE("%s: Invalid throughput level value", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            }
        } else if (tpDirection == TP_CHANGE_TX) {
            switch(tpLevel) {
            case QCA_WLAN_THROUGHPUT_LEVEL_LOW:
            {
                if (def_tcp_limit_output_bytes_valid)
                    wlan_service_set_tcp_limit_output_bytes(
                                    def_tcp_limit_output_bytes);
            }
            break;
            case QCA_WLAN_THROUGHPUT_LEVEL_MEDIUM:
            case QCA_WLAN_THROUGHPUT_LEVEL_HIGH:
            {
                if (tcpLimitOutputBytesFlag) {
                    ret_val = snprintf(value_to_str, sizeof(value_to_str), "%d",
                                            tcpLimitOutputBytes);
                    if (ret_val < 0 || ret_val >= (int)sizeof(value_to_str)) {
                        ALOGE("Error in converting value to string: %d", ret_val);
                        ret = WIFI_ERROR_UNKNOWN;
                        goto cleanup;
                    }
                    wlan_service_set_tcp_limit_output_bytes(value_to_str);
                }
            }
            break;
            default:
            {
                /* Error case should not happen print log */
                ALOGE("%s: Invalid throughput level value", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            }
        } else {
            /* Error case should not happen print log */
            ALOGE("%s: Invalid throughput change direction", __FUNCTION__);
            return ret;
        }
        ret = WIFI_SUCCESS;
    }
    break;
    default:
        /* Error case should not happen print log */
        ALOGE("%s: Wrong subcmd received %d", __FUNCTION__, mSubcmd);
        break;
    }

    return ret;

cleanup:
    return ret;
}

wifi_error wifi_init_tcp_param_change_event_handler(wifi_interface_handle iface)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    TCPParamCommand *tcpParamCommand;

    if (wifiHandle == NULL) {
        ALOGE("%s: Interface Handle is invalid", __func__);
        return WIFI_ERROR_UNKNOWN;
    }

    hal_info *info = getHalInfo(wifiHandle);
    if (!info)
        return WIFI_ERROR_UNKNOWN;

    tcpParamCommand = TCPParamCommand::instance(wifiHandle, 0);
    if (tcpParamCommand == NULL) {
        ALOGE("%s: Error TcpParamCommand NULL", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    info->tcp_param_handler = (tcp_param_cmd_handler *)malloc(sizeof(tcp_param_cmd_handler));
    if (info->tcp_param_handler == NULL) {
        ALOGE("%s: Allocation of tcp handler failed",__FUNCTION__);
        delete tcpParamCommand;
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    info->tcp_param_handler->tcpParamCommand = tcpParamCommand;

    if (wlan_service_read_sys_param("/proc/sys/net/ipv4/tcp_limit_output_bytes",
                    tcpParamCommand->def_tcp_limit_output_bytes,
                    SIZE_TCP_PARAM) == 0) {
        tcpParamCommand->def_tcp_limit_output_bytes_valid = true;
    }

    if (wlan_service_read_sys_param("/proc/sys/net/ipv4/tcp_adv_win_scale",
                    tcpParamCommand->def_tcp_adv_win_scale,
                    SIZE_TCP_PARAM) == 0) {
        tcpParamCommand->def_tcp_adv_win_scale_valid = true;
    }

    return WIFI_SUCCESS;
}

void cleanupTCPParamCommand(hal_info *info) {

    TCPParamCommand *tcpParamCommand;

    if (info == NULL || info->tcp_param_handler == NULL)
        return;

    tcpParamCommand = info->tcp_param_handler->tcpParamCommand;

    if (tcpParamCommand) {
        if (tcpParamCommand->def_tcp_limit_output_bytes_valid)
            wlan_service_update_sys_param("/proc/sys/net/ipv4/tcp_limit_output_bytes",
                              tcpParamCommand->def_tcp_limit_output_bytes);
        wlan_service_update_sys_param("/proc/sys/net/ipv4/tcp_use_userconfig", "0");
        if (tcpParamCommand->def_tcp_adv_win_scale_valid)
            wlan_service_update_sys_param("/proc/sys/net/ipv4/tcp_adv_win_scale",
                              tcpParamCommand->def_tcp_adv_win_scale);
        delete tcpParamCommand;
    }

    free(info->tcp_param_handler);

    return;
}

/**
 * wlan_service_update_sys_param()
 * @path: path on the file system to be modified
 * @str:  value to be written to the path
 *
 * Generic function to update a system parameter
 * Return: WIFI_SUCCESS code if read is successful
 *         Eror code if read is failure
 */
wifi_error wlan_service_update_sys_param(const char *path, const char *str)
{
    int ret;
    FILE *fp;
    fp = fopen(path, "w");

    if (fp == NULL) {
        ALOGE("%s: unable to open %s", __FUNCTION__, path);
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGD("%s: %s %s", __FUNCTION__,  path, str);

    ret = fputs(str, fp);
    fclose(fp);

    if (ret < 0) {
        ALOGE("%s: failed to write %s to %s with err %d", __FUNCTION__, str, path, ret);
        return mapKernelErrortoWifiHalError(ret);
    }

    return WIFI_SUCCESS;
}

/**
 * wlan_service_read_sys_param()
 * @path: path on the file system to be read
 * @str:  value read from the path
 *
 * Generic function to read a system parameter
 * Return: WIFI_SUCCESS code if read is successful
 *         Eror code if read is failure
 */
wifi_error wlan_service_read_sys_param(const char *path, char *str, size_t max_size)
{
    size_t ret_len;
    FILE *fp = fopen(path, "r");

    if (fp == NULL) {
        ALOGE("%s: unable to open %s", __FUNCTION__, path);
        return WIFI_ERROR_UNKNOWN;
    }

    ret_len = fread(str, 1, max_size, fp);
    fclose(fp);

    if (ret_len == 0 || ret_len == max_size) {
        ALOGE("Faild to read %s, ret_len = %zu", path, ret_len);
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGD("%s: %s %s", __FUNCTION__,  path, str);
    return WIFI_SUCCESS;
}

int TCPParamCommand::wlan_service_set_tcp_adv_win_scale(char *str)
{
    return wlan_service_update_sys_param(
        "/proc/sys/net/ipv4/tcp_adv_win_scale", str);
}

int TCPParamCommand::wlan_service_set_tcp_use_userconfig(const char *str)
{
    return wlan_service_update_sys_param(
        "/proc/sys/net/ipv4/tcp_use_userconfig", str);
}

int TCPParamCommand::wlan_service_set_tcp_delack_seg(char *str)
{
    return wlan_service_update_sys_param(
        "/proc/sys/net/ipv4/tcp_delack_seg", str);
}

int TCPParamCommand::wlan_service_set_tcp_limit_output_bytes(char *str)
{
    return wlan_service_update_sys_param (
        "/proc/sys/net/ipv4/tcp_limit_output_bytes", str);
}
