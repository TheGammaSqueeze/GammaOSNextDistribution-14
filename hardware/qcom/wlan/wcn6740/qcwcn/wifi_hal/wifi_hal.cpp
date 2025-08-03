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

#include <stdint.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <linux/rtnetlink.h>
#include <netpacket/packet.h>
#include <linux/filter.h>
#include <linux/errqueue.h>
#include <linux-private/linux/fib_rules.h>
#include <linux/pkt_sched.h>
#include <netlink/object-api.h>
#include <netlink/netlink.h>
#include <netlink/socket.h>
#include <netlink-private/object-api.h>
#include <netlink-private/types.h>

#include "nl80211_copy.h"

#include <dirent.h>
#include <net/if.h>
#include <netinet/in.h>
#include <cld80211_lib.h>

#include <sys/types.h>
#include "list.h"
#include <unistd.h>

#include "sync.h"

#define LOG_TAG  "WifiHAL"

#include <hardware_legacy/wifi_hal.h>
#include "wifi_hal_ctrl.h"
#include "common.h"
#include "cpp_bindings.h"
#include "ifaceeventhandler.h"
#include "wifiloggercmd.h"
#include "tcp_params_update.h"

/*
 BUGBUG: normally, libnl allocates ports for all connections it makes; but
 being a static library, it doesn't really know how many other netlink
 connections are made by the same process, if connections come from different
 shared libraries. These port assignments exist to solve that
 problem - temporarily. We need to fix libnl to try and allocate ports across
 the entire process.
 */

#define WIFI_HAL_CMD_SOCK_PORT       644
#define WIFI_HAL_EVENT_SOCK_PORT     645

#define MAX_HW_VER_LENGTH 100
/*
 * Defines for wifi_wait_for_driver_ready()
 * Specify durations between polls and max wait time
 */
#define POLL_DRIVER_DURATION_US (100000)
#define POLL_DRIVER_MAX_TIME_MS (10000)

static int attach_monitor_sock(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg);

static int dettach_monitor_sock(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg);

static int register_monitor_sock(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg, int attach);

static int send_nl_data(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg);

static int internal_pollin_handler(wifi_handle handle, struct nl_sock *sock);

static void internal_event_handler_app(wifi_handle handle, int events,
                                       struct ctrl_sock *sock);

static void internal_event_handler(wifi_handle handle, int events,
                                   struct nl_sock *sock);
static int internal_valid_message_handler(nl_msg *msg, void *arg);
static int user_sock_message_handler(nl_msg *msg, void *arg);
static int wifi_get_multicast_id(wifi_handle handle, const char *name,
        const char *group);
static int wifi_add_membership(wifi_handle handle, const char *group);
static wifi_error wifi_init_interfaces(wifi_handle handle);
static wifi_error wifi_set_packet_filter(wifi_interface_handle iface,
                                         const u8 *program, u32 len);
static wifi_error wifi_get_packet_filter_capabilities(wifi_interface_handle handle,
                                              u32 *version, u32 *max_len);
static wifi_error wifi_read_packet_filter(wifi_interface_handle handle,
                                   u32 src_offset, u8 *host_dst, u32 length);
static wifi_error wifi_configure_nd_offload(wifi_interface_handle iface,
                                            u8 enable);
wifi_error wifi_get_wake_reason_stats(wifi_interface_handle iface,
                             WLAN_DRIVER_WAKE_REASON_CNT *wifi_wake_reason_cnt);
static int wifi_is_nan_ext_cmd_supported(wifi_interface_handle handle);

wifi_error
    wifi_init_tcp_param_change_event_handler(wifi_interface_handle iface);

wifi_error wifi_set_voip_mode(wifi_interface_handle iface, wifi_voip_mode mode);

/* Initialize/Cleanup */

wifi_interface_handle wifi_get_iface_handle(wifi_handle handle, char *name)
{
    hal_info *info = (hal_info *)handle;
    for (int i=0;i<info->num_interfaces;i++)
    {
        if (!strcmp(info->interfaces[i]->name, name))
        {
            return ((wifi_interface_handle )(info->interfaces)[i]);
        }
    }
    return NULL;
}

void wifi_socket_set_local_port(struct nl_sock *sock, uint32_t port)
{
    /* Release local port pool maintained by libnl and assign a own port
     * identifier to the socket.
     */
    nl_socket_set_local_port(sock, ((uint32_t)getpid() & 0x3FFFFFU) | (port << 22));
}

static nl_sock * wifi_create_nl_socket(int port, int protocol)
{
    // ALOGI("Creating socket");
    struct nl_sock *sock = nl_socket_alloc();
    if (sock == NULL) {
        ALOGE("Failed to create NL socket");
        return NULL;
    }

    wifi_socket_set_local_port(sock, port);

    if (nl_connect(sock, protocol)) {
        ALOGE("Could not connect handle");
        nl_socket_free(sock);
        return NULL;
    }

    return sock;
}

void wifi_create_ctrl_socket(hal_info *info)
{
#ifdef ANDROID
   struct group *grp_wifi;
   gid_t gid_wifi;
   struct passwd *pwd_system;
   uid_t uid_system;
#endif

    int flags;

    info->wifihal_ctrl_sock.s = socket(PF_UNIX, SOCK_DGRAM, 0);

    if (info->wifihal_ctrl_sock.s < 0) {
        ALOGE("socket(PF_UNIX): %s", strerror(errno));
        return;
    }
    memset(&info->wifihal_ctrl_sock.local, 0, sizeof(info->wifihal_ctrl_sock.local));

    info->wifihal_ctrl_sock.local.sun_family = AF_UNIX;

    snprintf(info->wifihal_ctrl_sock.local.sun_path,
             sizeof(info->wifihal_ctrl_sock.local.sun_path), "%s", WIFI_HAL_CTRL_IFACE);

    if (bind(info->wifihal_ctrl_sock.s, (struct sockaddr *) &info->wifihal_ctrl_sock.local,
             sizeof(info->wifihal_ctrl_sock.local)) < 0) {
        ALOGD("ctrl_iface bind(PF_UNIX) failed: %s",
               strerror(errno));
        if (connect(info->wifihal_ctrl_sock.s, (struct sockaddr *) &info->wifihal_ctrl_sock.local,
                    sizeof(info->wifihal_ctrl_sock.local)) < 0) {
                ALOGD("ctrl_iface exists, but does not"
                      " allow connections - assuming it was left"
                      "over from forced program termination");
                if (unlink(info->wifihal_ctrl_sock.local.sun_path) < 0) {
                   ALOGE("Could not unlink existing ctrl_iface socket '%s': %s",
                          info->wifihal_ctrl_sock.local.sun_path, strerror(errno));
                   goto out;

                }
                if (bind(info->wifihal_ctrl_sock.s ,
                         (struct sockaddr *) &info->wifihal_ctrl_sock.local,
                         sizeof(info->wifihal_ctrl_sock.local)) < 0) {
                        ALOGE("wifihal-ctrl-iface-init: bind(PF_UNIX): %s",
                               strerror(errno));
                        goto out;
                }
                ALOGD("Successfully replaced leftover "
                      "ctrl_iface socket '%s'", info->wifihal_ctrl_sock.local.sun_path);
        } else {
             ALOGI("ctrl_iface exists and seems to "
                   "be in use - cannot override it");
             ALOGI("Delete '%s' manually if it is "
                   "not used anymore", info->wifihal_ctrl_sock.local.sun_path);
             goto out;
        }
    }

    /*
     * Make socket non-blocking so that we don't hang forever if
     * target dies unexpectedly.
     */

#ifdef ANDROID
    if (chmod(info->wifihal_ctrl_sock.local.sun_path, S_IRWXU | S_IRWXG) < 0)
    {
      ALOGE("Failed to give permissions: %s", strerror(errno));
    }

    /* Set group even if we do not have privileges to change owner */
    grp_wifi = getgrnam("wifi");
    gid_wifi = grp_wifi ? grp_wifi->gr_gid : 0;
    pwd_system = getpwnam("system");
    uid_system = pwd_system ? pwd_system->pw_uid : 0;
    if (!gid_wifi || !uid_system) {
      ALOGE("Failed to get grp ids");
      unlink(info->wifihal_ctrl_sock.local.sun_path);
      goto out;
    }
    chown(info->wifihal_ctrl_sock.local.sun_path, -1, gid_wifi);
    chown(info->wifihal_ctrl_sock.local.sun_path, uid_system, gid_wifi);
#endif

    flags = fcntl(info->wifihal_ctrl_sock.s, F_GETFL);
    if (flags >= 0) {
        flags |= O_NONBLOCK;
        if (fcntl(info->wifihal_ctrl_sock.s, F_SETFL, flags) < 0) {
            ALOGI("fcntl(ctrl, O_NONBLOCK): %s",
                   strerror(errno));
            /* Not fatal, continue on.*/
        }
    }
  return;

out:
  close(info->wifihal_ctrl_sock.s);
  info->wifihal_ctrl_sock.s = 0;
  return;
}

int ack_handler(struct nl_msg *msg, void *arg)
{
    int *err = (int *)arg;
    *err = 0;
    return NL_STOP;
}

int finish_handler(struct nl_msg *msg, void *arg)
{
    int *ret = (int *)arg;
    *ret = 0;
    return NL_SKIP;
}

int error_handler(struct sockaddr_nl *nla,
                  struct nlmsgerr *err, void *arg)
{
    int *ret = (int *)arg;
    *ret = err->error;

    ALOGV("%s invoked with error: %d", __func__, err->error);
    return NL_SKIP;
}
static int no_seq_check(struct nl_msg *msg, void *arg)
{
    return NL_OK;
}

static wifi_error acquire_supported_features(wifi_interface_handle iface,
        feature_set *set)
{
    wifi_error ret;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    *set = 0;

    WifihalGeneric supportedFeatures(handle, 0,
            OUI_QCA,
            QCA_NL80211_VENDOR_SUBCMD_GET_SUPPORTED_FEATURES);

    /* create the message */
    ret = supportedFeatures.create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = supportedFeatures.set_iface_id(iinfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = supportedFeatures.requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse Error:%d",__func__, ret);
        goto cleanup;
    }

    supportedFeatures.getResponseparams(set);

cleanup:
    return ret;
}

static wifi_error acquire_driver_supported_features(wifi_interface_handle iface,
                                          features_info *driver_features)
{
    wifi_error ret;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);

    WifihalGeneric driverFeatures(handle, 0,
            OUI_QCA,
            QCA_NL80211_VENDOR_SUBCMD_GET_FEATURES);

    /* create the message */
    ret = driverFeatures.create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = driverFeatures.set_iface_id(iinfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = driverFeatures.requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse Error:%d",__func__, ret);
        goto cleanup;
    }

    driverFeatures.getDriverFeatures(driver_features);

cleanup:
    return mapKernelErrortoWifiHalError(ret);
}

static wifi_error wifi_get_capabilities(wifi_interface_handle handle)
{
    wifi_error ret;
    int requestId;
    WifihalGeneric *wifihalGeneric;
    wifi_handle wifiHandle = getWifiHandle(handle);
    hal_info *info = getHalInfo(wifiHandle);

    if (!(info->supported_feature_set & WIFI_FEATURE_GSCAN)) {
        ALOGE("%s: GSCAN is not supported by driver", __FUNCTION__);
        return WIFI_ERROR_NOT_SUPPORTED;
    }

    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate it randomly.
     */
    requestId = get_requestid();

    wifihalGeneric = new WifihalGeneric(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_GSCAN_GET_CAPABILITIES);
    if (!wifihalGeneric) {
        ALOGE("%s: Failed to create object of WifihalGeneric class", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ret = wifihalGeneric->wifiGetCapabilities(handle);

    delete wifihalGeneric;
    return ret;
}

static wifi_error wifi_get_sar_version(wifi_interface_handle handle)
{
    wifi_error ret;
    wifi_handle wifiHandle = getWifiHandle(handle);

    WifihalGeneric *sarVersion = new WifihalGeneric(
                            wifiHandle,
                            0,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_GET_SAR_CAPABILITY);
    if (!sarVersion) {
        ALOGE("%s: Failed to create object of WifihalGeneric class", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }


    ret = sarVersion->getSarVersion(handle);

    delete sarVersion;
    return ret;
}

static wifi_error get_firmware_bus_max_size_supported(
                                                wifi_interface_handle iface)
{
    wifi_error ret;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    hal_info *info = (hal_info *)handle;

    WifihalGeneric busSizeSupported(handle, 0,
                                    OUI_QCA,
                                    QCA_NL80211_VENDOR_SUBCMD_GET_BUS_SIZE);

    /* create the message */
    ret = busSizeSupported.create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = busSizeSupported.set_iface_id(iinfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = busSizeSupported.requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    info->firmware_bus_max_size = busSizeSupported.getBusSize();

cleanup:
    return ret;
}

static wifi_error wifi_init_user_sock(hal_info *info)
{
    struct nl_sock *user_sock =
        wifi_create_nl_socket(WIFI_HAL_USER_SOCK_PORT, NETLINK_USERSOCK);
    if (user_sock == NULL) {
        ALOGE("Could not create diag sock");
        return WIFI_ERROR_UNKNOWN;
    }

    /* Set the socket buffer size */
    if (nl_socket_set_buffer_size(user_sock, (256*1024), 0) < 0) {
        ALOGE("Could not set size for user_sock: %s",
                   strerror(errno));
        /* continue anyway with the default (smaller) buffer */
    }
    else {
        ALOGV("nl_socket_set_buffer_size successful for user_sock");
    }

    struct nl_cb *cb = nl_socket_get_cb(user_sock);
    if (cb == NULL) {
        ALOGE("Could not get cb");
        return WIFI_ERROR_UNKNOWN;
    }

    info->user_sock_arg = 1;
    nl_cb_set(cb, NL_CB_SEQ_CHECK, NL_CB_CUSTOM, no_seq_check, NULL);
    nl_cb_err(cb, NL_CB_CUSTOM, error_handler, &info->user_sock_arg);
    nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler, &info->user_sock_arg);
    nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler, &info->user_sock_arg);

    nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM, user_sock_message_handler, info);
    nl_cb_put(cb);

    int ret = nl_socket_add_membership(user_sock, 1);
    if (ret < 0) {
        ALOGE("Could not add membership");
        return WIFI_ERROR_UNKNOWN;
    }

    info->user_sock = user_sock;
    ALOGV("Initiialized diag sock successfully");
    return WIFI_SUCCESS;
}

static wifi_error wifi_init_cld80211_sock_cb(hal_info *info)
{
    struct nl_cb *cb = nl_socket_get_cb(info->cldctx->sock);
    if (cb == NULL) {
        ALOGE("Could not get cb");
        return WIFI_ERROR_UNKNOWN;
    }

    info->user_sock_arg = 1;
    nl_cb_set(cb, NL_CB_SEQ_CHECK, NL_CB_CUSTOM, no_seq_check, NULL);
    nl_cb_err(cb, NL_CB_CUSTOM, error_handler, &info->user_sock_arg);
    nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler, &info->user_sock_arg);
    nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler, &info->user_sock_arg);

    nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM, user_sock_message_handler, info);
    nl_cb_put(cb);

    return WIFI_SUCCESS;
}

static uint32_t get_frequency_from_channel(uint32_t channel, wlan_mac_band band)
{
  uint32_t freq = 0;

  switch (band)
  {
    case WLAN_MAC_2_4_BAND:
      if (!(channel >= 1 && channel <= 14))
        goto failure;
      //special handling for channel 14 by filling freq here
      if (channel == 14)
        freq = 2484;
      else
        freq = 2407 + (channel * 5);
      break;
    case WLAN_MAC_5_0_BAND:
      if (!((channel >= 34 && channel < 65) ||
          (channel > 99 && channel <= 196)))
        goto failure;
      freq = 5000 + (channel * 5);
      break;
    case WLAN_MAC_6_0_BAND:
      if (!(channel >= 1 && channel <= 233))
        goto failure;
      freq = 5950 + (channel * 5);
      break;
    default:
      break;
  }

failure:
  return freq;
}

static u32 get_nl_ifmask_from_coex_restriction_mask(u32 in_mask)
{
    u32 op_mask = 0;

    if (!in_mask)
       return op_mask;
    if (in_mask & SOFTAP)
         op_mask |= BIT(NL80211_IFTYPE_AP);
    if (in_mask & WIFI_DIRECT)
         op_mask |= BIT(NL80211_IFTYPE_P2P_GO);
    if (in_mask & WIFI_AWARE)
         op_mask |= BIT(NL80211_IFTYPE_NAN);

    return op_mask;
}

wifi_error wifi_set_coex_unsafe_channels(wifi_handle handle, u32 num_channels,
                                         wifi_coex_unsafe_channel *unsafeChannels,
                                         u32 restrictions)
{
    wifi_error ret = WIFI_ERROR_UNKNOWN;
    WifihalGeneric *cmd = NULL;
    struct nlattr *nl_data = NULL;
    struct nlattr *nl_attr_unsafe_chan = NULL;
    struct nlattr *unsafe_channels_attr = NULL;
    hal_info *info = NULL;
    int freq_cnt = 0;
    u32 *freq = (u32 *) malloc(sizeof(u32) * num_channels);
    u32 *power_cap_dbm = (u32 *) malloc(sizeof(u32) * num_channels);

    if (!freq || !power_cap_dbm) {
        ALOGE("%s: Failed to allocate memory", __FUNCTION__);
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto cleanup;
    }

    if (!handle) {
         ALOGE("%s: Error, wifi_handle NULL", __FUNCTION__);
         goto cleanup;
    }

    info = getHalInfo(handle);
    if (!info || info->num_interfaces < 1) {
         ALOGE("%s: Error, wifi_handle NULL or base wlan interface not present",
               __FUNCTION__);
         goto cleanup;
    }

    cmd = new WifihalGeneric(handle, get_requestid(), OUI_QCA,
                             QCA_NL80211_VENDOR_SUBCMD_AVOID_FREQUENCY_EXT);
    if (cmd == NULL) {
         ALOGE("%s: Error, created command NULL", __FUNCTION__);
         ret = WIFI_ERROR_OUT_OF_MEMORY;
         goto cleanup;
    }

    /* Create the NL message. */
    ret = cmd->create();
    if (ret < 0) {
         ALOGE("%s: failed to create NL msg due to error: (%d)",
               __FUNCTION__, ret);
         goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nl_data = cmd->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data) {
         ALOGE("%s: failed attr_start for NL80211_ATTR_VENDOR_DATA",
               __FUNCTION__);
         ret = WIFI_ERROR_OUT_OF_MEMORY;
         goto cleanup;
    }

    nl_attr_unsafe_chan = cmd->attr_start(
        QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_RANGE);
    if (!nl_attr_unsafe_chan) {
         ALOGE("%s: failed attr_start for"
               " QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_RANGE", __FUNCTION__);
         ret = WIFI_ERROR_OUT_OF_MEMORY;
         goto cleanup;
    }
    ALOGD("%s: num_channels:%d, restrictions:%x", __FUNCTION__, num_channels,
          restrictions);
    for (int i = 0; i < num_channels; i++)
    {
        u32 frequency = get_frequency_from_channel(unsafeChannels[i].channel,
                unsafeChannels[i].band);
        if (frequency != 0)
        {
          freq[freq_cnt] = frequency;
          power_cap_dbm[freq_cnt] = unsafeChannels[i].power_cap_dbm;
          freq_cnt++;
          ALOGV("%s: channel:%d, freq:%d, power_cap_dbm:%d, band:%d",
               __FUNCTION__, unsafeChannels[i].channel, frequency,
               unsafeChannels[i].power_cap_dbm, unsafeChannels[i].band);
        }
        else {
            ALOGV("%s: Invalid channel found, channel:%d, power_cap_dbm:%d, band:%d",
               __FUNCTION__, unsafeChannels[i].channel,
               unsafeChannels[i].power_cap_dbm, unsafeChannels[i].band);
        }
    }
    if (num_channels == 0) {
         unsafe_channels_attr = cmd->attr_start(0);
         if (!unsafe_channels_attr) {
              ALOGE("%s: failed attr_start for unsafe_channels_attr when"
                    " trying to clear usafe channels clear", __FUNCTION__);
              ret = WIFI_ERROR_OUT_OF_MEMORY;
              goto cleanup;
         }
         ret = cmd->put_u32(
               QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_START, 0);
         if (ret != WIFI_SUCCESS) {
              ALOGE("%s: Failed to put frequency start, ret:%d",
                    __FUNCTION__, ret);
              goto cleanup;
         }
         ret = cmd->put_u32(
               QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_END, 0);
         if (ret != WIFI_SUCCESS) {
              ALOGE("%s: Failed to put frequency end, ret:%d",
                    __FUNCTION__, ret);
              goto cleanup;
         }
         cmd->attr_end(unsafe_channels_attr);
    }
    else {
        if (!unsafeChannels) {
            ALOGE("%s: unsafe channels buffer should not be NULL when"
                  " there are unsafe channels", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto cleanup;
        }

        if(freq_cnt == 0)
        {
            ALOGE("%s: No valid frequency, ignore channel list", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto cleanup;
        }
        for (int i = 0; i < freq_cnt; i++) {
            unsafe_channels_attr = cmd->attr_start(i);
            if (!unsafe_channels_attr) {
                ALOGE("%s: failed attr_start for unsafe_channels_attr of"
                    " index:%d", __FUNCTION__, i);
                ret = WIFI_ERROR_OUT_OF_MEMORY;
                goto cleanup;
            }

            ret = cmd->put_u32(
                  QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_START, freq[i]);
            if (ret != WIFI_SUCCESS) {
                ALOGE("%s: Failed to put frequency start, ret:%d",
                      __FUNCTION__, ret);
                goto cleanup;
            }
            ret = cmd->put_u32(
                QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_END, freq[i]);
            if (ret != WIFI_SUCCESS) {
                ALOGE("%s: Failed to put frequency end, ret:%d",
                    __FUNCTION__, ret);
                goto cleanup;
            }
            /**
             * WIFI_COEX_NO_POWER_CAP (0x7FFFFFF) is specific to android
             * framework, this value denotes that framework/wifihal is not
             * providing any power cap and allow driver/firmware to operate on
             * current power cap dbm. As driver is supposed to work on with
             * LA/LE etc, we are skipping to send 0x7FFFFFF down to driver,
             * hence driver will be operating as per current power cap calculated
             * based on regulatory or other constraints.
             */
            if (power_cap_dbm[i] != WIFI_COEX_NO_POWER_CAP) {
                ret = cmd->put_s32(
                      QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_POWER_CAP_DBM,
                      power_cap_dbm[i]);
                if (ret != WIFI_SUCCESS) {
                    ALOGE("%s: Failed to put power_cap_dbm, ret:%d",
                          __FUNCTION__, ret);
                    goto cleanup;
                }
            }
            ALOGD("%s: freq:%d, power_cap_dbm:%d",
                   __FUNCTION__, freq[i], power_cap_dbm[i]);
            cmd->attr_end(unsafe_channels_attr);
        }
    }
    cmd->attr_end(nl_attr_unsafe_chan);
    if (num_channels > 0) {
        ret = cmd->put_u32(QCA_WLAN_VENDOR_ATTR_AVOID_FREQUENCY_IFACES_BITMASK,
                       get_nl_ifmask_from_coex_restriction_mask(restrictions));
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: Failed to put restrictions mask, ret:%d",
                  __FUNCTION__, ret);
            goto cleanup;
        }
    }
    cmd->attr_end(nl_data);

    /* Send the msg and wait for a response. */
    ret = cmd->requestResponse();
    if (ret != WIFI_SUCCESS) {
         ALOGE("%s: Error %d waiting for response.", __FUNCTION__, ret);
         goto cleanup;
    }

cleanup:
    if (cmd)
        delete cmd;
    if (freq)
        free (freq);
    if (power_cap_dbm)
        free (power_cap_dbm);
    return ret;
}

wifi_error wifi_set_dtim_config(wifi_interface_handle handle, u32 multiplier)
{
    wifi_error ret = WIFI_ERROR_INVALID_ARGS;
    WifihalGeneric *cmd = NULL;
    struct nlattr *nlData = NULL;
    interface_info *ifaceInfo = NULL;
    wifi_handle wifiHandle = NULL;

    if (!handle) {
         ALOGE("%s: Error, wifi_interface_handle NULL", __FUNCTION__);
         goto cleanup;
    }
    ALOGD("%s: multiplier:%d", __FUNCTION__, multiplier);
    wifiHandle = getWifiHandle(handle);
    cmd = new WifihalGeneric(wifiHandle, get_requestid(), OUI_QCA,
                             QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (cmd == NULL) {
        ALOGE("%s: Error WifihalGeneric NULL", __FUNCTION__);
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto cleanup;
    }

    /* Create the NL message. */
    ret = cmd->create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to create NL msg. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ifaceInfo = getIfaceInfo(handle);
    if (!ifaceInfo) {
        ALOGE("%s: getIfaceInfo is NULL", __FUNCTION__);
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = cmd->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to set iface id. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = cmd->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ALOGE("%s: failed attr_start for VENDOR_DATA", __FUNCTION__);
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto cleanup;
    }

    ret = cmd->put_u32(QCA_WLAN_VENDOR_ATTR_CONFIG_DYNAMIC_DTIM, multiplier);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: failed to put vendor data. Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    cmd->attr_end(nlData);

    /* Send the NL msg. */
    ret = cmd->requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    if (cmd)
        delete cmd;
    return ret;
}

static u32 get_nl_band_mask(u32 in_mask)
{
    u32 op_mask = 0;

    if (in_mask & WLAN_MAC_2_4_BAND)
         op_mask |= BIT(NL80211_BAND_2GHZ);
    if (in_mask & WLAN_MAC_5_0_BAND)
         op_mask |= BIT(NL80211_BAND_5GHZ);
    if (in_mask & WLAN_MAC_6_0_BAND)
         op_mask |= BIT(NL80211_BAND_6GHZ);
    if (in_mask & WLAN_MAC_60_0_BAND)
         op_mask |= BIT(NL80211_BAND_60GHZ);

    return op_mask;
}

static u32 get_nl_iftype_mode_masks(u32 in_mask)
{
    u32 op_mask = 0;

    if (in_mask & BIT(WIFI_INTERFACE_STA) ||
        in_mask & BIT(WIFI_INTERFACE_TDLS))
         op_mask |= BIT(NL80211_IFTYPE_STATION);
    if (in_mask & BIT(WIFI_INTERFACE_SOFTAP))
         op_mask |= BIT(NL80211_IFTYPE_AP);
    if (in_mask & BIT(WIFI_INTERFACE_P2P_CLIENT))
         op_mask |= BIT(NL80211_IFTYPE_P2P_CLIENT);
    if (in_mask & BIT(WIFI_INTERFACE_P2P_GO))
         op_mask |= BIT(NL80211_IFTYPE_P2P_GO);
    if (in_mask & BIT(WIFI_INTERFACE_NAN))
         op_mask |= BIT(NL80211_IFTYPE_NAN);

    return op_mask;
}

static u32 get_vendor_filter_mask(u32 in_mask)
{
    u32 op_mask = 0;

    if (in_mask & WIFI_USABLE_CHANNEL_FILTER_CELLULAR_COEXISTENCE)
         op_mask |= BIT(QCA_WLAN_VENDOR_FILTER_CELLULAR_COEX);
    if (in_mask & WIFI_USABLE_CHANNEL_FILTER_CONCURRENCY)
         op_mask |= BIT(QCA_WLAN_VENDOR_FILTER_WLAN_CONCURRENCY);

    return op_mask;
}

wifi_error wifi_get_usable_channels(wifi_handle handle, u32 band_mask,
                                    u32 iface_mode_mask, u32 filter_mask,
                                    u32 max_size, u32* size,
                                    wifi_usable_channel* channels)
{
    wifi_error ret = WIFI_ERROR_UNKNOWN;
    WifihalGeneric *cmd = NULL;
    struct nlattr *nl_data = NULL;
    hal_info *info = NULL;
    u32 band = 0, iface_mask = 0, filter = 0;

    if (!handle) {
         ALOGE("%s: Error, wifi_handle NULL", __FUNCTION__);
         goto cleanup;
    }

    info = getHalInfo(handle);
    if (!info || info->num_interfaces < 1) {
         ALOGE("%s: Error, wifi_handle NULL or base wlan interface not present",
               __FUNCTION__);
         goto cleanup;
    }

    if (!max_size) {
         ALOGE("%s: max channel size is zero", __FUNCTION__);
         ret = WIFI_ERROR_INVALID_ARGS;
         goto cleanup;
    }

    if (!channels) {
         ALOGE("%s: user input channel buffer NULL", __FUNCTION__);
         ret = WIFI_ERROR_INVALID_ARGS;
         goto cleanup;
    }

    cmd = new WifihalGeneric(handle, get_requestid(), OUI_QCA,
                             QCA_NL80211_VENDOR_SUBCMD_USABLE_CHANNELS);
    if (cmd == NULL) {
         ALOGE("%s: Error, created command NULL", __FUNCTION__);
         ret = WIFI_ERROR_OUT_OF_MEMORY;
         goto cleanup;
    }

    /* Create the NL message. */
    ret = cmd->create();
    if (ret < 0) {
         ALOGE("%s: failed to create NL msg due to error: (%d)",
               __FUNCTION__, ret);
         goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nl_data = cmd->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data) {
         ALOGE("%s: failed attr_start for VENDOR_DATA due to error",
               __FUNCTION__);
         ret = WIFI_ERROR_OUT_OF_MEMORY;
         goto cleanup;
    }

    band = get_nl_band_mask(band_mask);
    ret = cmd->put_u32(QCA_WLAN_VENDOR_ATTR_USABLE_CHANNELS_BAND_MASK,
                       band);
    if (ret != WIFI_SUCCESS) {
         ALOGE("%s: failed to put vendor data due to error:%d",
               __FUNCTION__, ret);
         goto cleanup;
    }

    iface_mask = get_nl_iftype_mode_masks(iface_mode_mask);
    ret = cmd->put_u32(QCA_WLAN_VENDOR_ATTR_USABLE_CHANNELS_IFACE_MODE_MASK,
                       iface_mask);
    if (ret != WIFI_SUCCESS) {
         ALOGE("%s: failed to put vendor data due to error:%d",
               __FUNCTION__, ret);
         goto cleanup;
    }

    filter = get_vendor_filter_mask(filter_mask);
    ret = cmd->put_u32(QCA_WLAN_VENDOR_ATTR_USABLE_CHANNELS_FILTER_MASK,
                       filter);
    if (ret != WIFI_SUCCESS) {
         ALOGE("%s: failed to put vendor data due to error:%d",
               __FUNCTION__, ret);
         goto cleanup;
    }

    cmd->attr_end(nl_data);

    /* Populate the input received from caller/framework. */
    cmd->setMaxSetSize(max_size);
    cmd->set_channels_buff(channels);

    /* Send the msg and wait for a response. */
    ret = cmd->requestResponse();
    if (ret != WIFI_SUCCESS) {
         ALOGE("%s: Error %d waiting for response.", __FUNCTION__, ret);
         goto cleanup;
    }

    *size = cmd->get_results_size();

cleanup:
    if (cmd)
        delete cmd;
    return ret;
}

/*initialize function pointer table with Qualcomm HAL API*/
wifi_error init_wifi_vendor_hal_func_table(wifi_hal_fn *fn) {
    if (fn == NULL) {
        return WIFI_ERROR_UNKNOWN;
    }

    fn->wifi_initialize = wifi_initialize;
    fn->wifi_wait_for_driver_ready = wifi_wait_for_driver_ready;
    fn->wifi_cleanup = wifi_cleanup;
    fn->wifi_event_loop = wifi_event_loop;
    fn->wifi_get_supported_feature_set = wifi_get_supported_feature_set;
    fn->wifi_get_concurrency_matrix = wifi_get_concurrency_matrix;
    fn->wifi_set_scanning_mac_oui =  wifi_set_scanning_mac_oui;
    fn->wifi_get_ifaces = wifi_get_ifaces;
    fn->wifi_get_iface_name = wifi_get_iface_name;
    fn->wifi_set_iface_event_handler = wifi_set_iface_event_handler;
    fn->wifi_reset_iface_event_handler = wifi_reset_iface_event_handler;
    fn->wifi_start_gscan = wifi_start_gscan;
    fn->wifi_stop_gscan = wifi_stop_gscan;
    fn->wifi_get_cached_gscan_results = wifi_get_cached_gscan_results;
    fn->wifi_set_bssid_hotlist = wifi_set_bssid_hotlist;
    fn->wifi_reset_bssid_hotlist = wifi_reset_bssid_hotlist;
    fn->wifi_set_significant_change_handler = wifi_set_significant_change_handler;
    fn->wifi_reset_significant_change_handler = wifi_reset_significant_change_handler;
    fn->wifi_get_gscan_capabilities = wifi_get_gscan_capabilities;
    fn->wifi_set_link_stats = wifi_set_link_stats;
    fn->wifi_get_link_stats = wifi_get_link_stats;
    fn->wifi_clear_link_stats = wifi_clear_link_stats;
    fn->wifi_get_valid_channels = wifi_get_valid_channels;
    fn->wifi_rtt_range_request = wifi_rtt_range_request;
    fn->wifi_rtt_range_cancel = wifi_rtt_range_cancel;
    fn->wifi_get_rtt_capabilities = wifi_get_rtt_capabilities;
    fn->wifi_rtt_get_responder_info = wifi_rtt_get_responder_info;
    fn->wifi_enable_responder = wifi_enable_responder;
    fn->wifi_disable_responder = wifi_disable_responder;
    fn->wifi_set_nodfs_flag = wifi_set_nodfs_flag;
    fn->wifi_start_logging = wifi_start_logging;
    fn->wifi_set_epno_list = wifi_set_epno_list;
    fn->wifi_reset_epno_list = wifi_reset_epno_list;
    fn->wifi_set_country_code = wifi_set_country_code;
    fn->wifi_enable_tdls = wifi_enable_tdls;
    fn->wifi_disable_tdls = wifi_disable_tdls;
    fn->wifi_get_tdls_status = wifi_get_tdls_status;
    fn->wifi_get_tdls_capabilities = wifi_get_tdls_capabilities;
    fn->wifi_get_firmware_memory_dump = wifi_get_firmware_memory_dump;
    fn->wifi_set_log_handler = wifi_set_log_handler;
    fn->wifi_reset_log_handler = wifi_reset_log_handler;
    fn->wifi_set_alert_handler = wifi_set_alert_handler;
    fn->wifi_reset_alert_handler = wifi_reset_alert_handler;
    fn->wifi_get_firmware_version = wifi_get_firmware_version;
    fn->wifi_get_ring_buffers_status = wifi_get_ring_buffers_status;
    fn->wifi_get_logger_supported_feature_set = wifi_get_logger_supported_feature_set;
    fn->wifi_get_ring_data = wifi_get_ring_data;
    fn->wifi_get_driver_version = wifi_get_driver_version;
    fn->wifi_set_passpoint_list = wifi_set_passpoint_list;
    fn->wifi_reset_passpoint_list = wifi_reset_passpoint_list;
    fn->wifi_set_lci = wifi_set_lci;
    fn->wifi_set_lcr = wifi_set_lcr;
    fn->wifi_start_sending_offloaded_packet =
            wifi_start_sending_offloaded_packet;
    fn->wifi_stop_sending_offloaded_packet = wifi_stop_sending_offloaded_packet;
    fn->wifi_start_rssi_monitoring = wifi_start_rssi_monitoring;
    fn->wifi_stop_rssi_monitoring = wifi_stop_rssi_monitoring;
    fn->wifi_nan_enable_request = nan_enable_request;
    fn->wifi_nan_disable_request = nan_disable_request;
    fn->wifi_nan_publish_request = nan_publish_request;
    fn->wifi_nan_publish_cancel_request = nan_publish_cancel_request;
    fn->wifi_nan_subscribe_request = nan_subscribe_request;
    fn->wifi_nan_subscribe_cancel_request = nan_subscribe_cancel_request;
    fn->wifi_nan_transmit_followup_request = nan_transmit_followup_request;
    fn->wifi_nan_stats_request = nan_stats_request;
    fn->wifi_nan_config_request = nan_config_request;
    fn->wifi_nan_tca_request = nan_tca_request;
    fn->wifi_nan_beacon_sdf_payload_request = nan_beacon_sdf_payload_request;
    fn->wifi_nan_register_handler = nan_register_handler;
    fn->wifi_nan_get_version = nan_get_version;
    fn->wifi_set_packet_filter = wifi_set_packet_filter;
    fn->wifi_get_packet_filter_capabilities = wifi_get_packet_filter_capabilities;
    fn->wifi_read_packet_filter = wifi_read_packet_filter;
    fn->wifi_nan_get_capabilities = nan_get_capabilities;
    fn->wifi_nan_data_interface_create = nan_data_interface_create;
    fn->wifi_nan_data_interface_delete = nan_data_interface_delete;
    fn->wifi_nan_data_request_initiator = nan_data_request_initiator;
    fn->wifi_nan_data_indication_response = nan_data_indication_response;
    fn->wifi_nan_data_end = nan_data_end;
    fn->wifi_configure_nd_offload = wifi_configure_nd_offload;
    fn->wifi_get_driver_memory_dump = wifi_get_driver_memory_dump;
    fn->wifi_get_wake_reason_stats = wifi_get_wake_reason_stats;
    fn->wifi_start_pkt_fate_monitoring = wifi_start_pkt_fate_monitoring;
    fn->wifi_get_tx_pkt_fates = wifi_get_tx_pkt_fates;
    fn->wifi_get_rx_pkt_fates = wifi_get_rx_pkt_fates;
    fn->wifi_get_roaming_capabilities = wifi_get_roaming_capabilities;
    fn->wifi_configure_roaming = wifi_configure_roaming;
    fn->wifi_enable_firmware_roaming = wifi_enable_firmware_roaming;
    fn->wifi_select_tx_power_scenario = wifi_select_tx_power_scenario;
    fn->wifi_reset_tx_power_scenario = wifi_reset_tx_power_scenario;
    fn->wifi_set_radio_mode_change_handler = wifi_set_radio_mode_change_handler;
    /* Customers will uncomment when they want to set qpower*/
    //fn->wifi_set_qpower = wifi_set_qpower;
    fn->wifi_virtual_interface_create = wifi_virtual_interface_create;
    fn->wifi_virtual_interface_delete = wifi_virtual_interface_delete;
    fn->wifi_set_latency_mode = wifi_set_latency_mode;
    fn->wifi_set_thermal_mitigation_mode = wifi_set_thermal_mitigation_mode;
    fn->wifi_multi_sta_set_primary_connection = wifi_multi_sta_set_primary_connection;
    fn->wifi_multi_sta_set_use_case = wifi_multi_sta_set_use_case;
    fn->wifi_set_coex_unsafe_channels = wifi_set_coex_unsafe_channels;
    fn->wifi_set_dtim_config = wifi_set_dtim_config;
    fn->wifi_set_voip_mode = wifi_set_voip_mode;
    fn->wifi_get_usable_channels = wifi_get_usable_channels;

    return WIFI_SUCCESS;
}

static void cld80211lib_cleanup(hal_info *info)
{
    if (!info->cldctx)
        return;
    cld80211_remove_mcast_group(info->cldctx, "host_logs");
    cld80211_remove_mcast_group(info->cldctx, "fw_logs");
    cld80211_remove_mcast_group(info->cldctx, "per_pkt_stats");
    cld80211_remove_mcast_group(info->cldctx, "diag_events");
    cld80211_remove_mcast_group(info->cldctx, "fatal_events");
    cld80211_remove_mcast_group(info->cldctx, "oem_msgs");
    exit_cld80211_recv(info->cldctx);
    cld80211_deinit(info->cldctx);
    info->cldctx = NULL;
}

static int wifi_get_iface_id(hal_info *info, const char *iface)
{
    int i;
    for (i = 0; i < info->num_interfaces; i++)
        if (!strcmp(info->interfaces[i]->name, iface))
            return i;
    return -1;
}

wifi_error wifi_initialize(wifi_handle *handle)
{
    wifi_error ret = WIFI_ERROR_UNKNOWN;
    wifi_interface_handle iface_handle;
    struct nl_sock *cmd_sock = NULL;
    struct nl_sock *event_sock = NULL;
    struct nl_cb *cb = NULL;
    int status = 0;
    int index;
    char hw_ver_type[MAX_HW_VER_LENGTH];
    char *hw_name = NULL;

    ALOGI("Initializing wifi");
    hal_info *info = (hal_info *)malloc(sizeof(hal_info));
    if (info == NULL) {
        ALOGE("Could not allocate hal_info");
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    memset(info, 0, sizeof(*info));

    cmd_sock = wifi_create_nl_socket(WIFI_HAL_CMD_SOCK_PORT,
                                                     NETLINK_GENERIC);
    if (cmd_sock == NULL) {
        ALOGE("Failed to create command socket port");
        ret = WIFI_ERROR_UNKNOWN;
        goto unload;
    }

    /* Set the socket buffer size */
    if (nl_socket_set_buffer_size(cmd_sock, (256*1024), 0) < 0) {
        ALOGE("Could not set nl_socket RX buffer size for cmd_sock: %s",
                   strerror(errno));
        /* continue anyway with the default (smaller) buffer */
    }

    event_sock =
        wifi_create_nl_socket(WIFI_HAL_EVENT_SOCK_PORT, NETLINK_GENERIC);
    if (event_sock == NULL) {
        ALOGE("Failed to create event socket port");
        ret = WIFI_ERROR_UNKNOWN;
        goto unload;
    }

    /* Set the socket buffer size */
    if (nl_socket_set_buffer_size(event_sock, (256*1024), 0) < 0) {
        ALOGE("Could not set nl_socket RX buffer size for event_sock: %s",
                   strerror(errno));
        /* continue anyway with the default (smaller) buffer */
    }

    cb = nl_socket_get_cb(event_sock);
    if (cb == NULL) {
        ALOGE("Failed to get NL control block for event socket port");
        ret = WIFI_ERROR_UNKNOWN;
        goto unload;
    }

    info->event_sock_arg = 1;
    nl_cb_set(cb, NL_CB_SEQ_CHECK, NL_CB_CUSTOM, no_seq_check, NULL);
    nl_cb_err(cb, NL_CB_CUSTOM, error_handler, &info->event_sock_arg);
    nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler, &info->event_sock_arg);
    nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler, &info->event_sock_arg);

    nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM, internal_valid_message_handler,
            info);
    nl_cb_put(cb);

    info->cmd_sock = cmd_sock;
    info->event_sock = event_sock;
    info->clean_up = false;
    info->in_event_loop = false;

    info->event_cb = (cb_info *)malloc(sizeof(cb_info) * DEFAULT_EVENT_CB_SIZE);
    if (info->event_cb == NULL) {
        ALOGE("Could not allocate event_cb");
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto unload;
    }
    info->alloc_event_cb = DEFAULT_EVENT_CB_SIZE;
    info->num_event_cb = 0;

    info->nl80211_family_id = genl_ctrl_resolve(cmd_sock, "nl80211");
    if (info->nl80211_family_id < 0) {
        ALOGE("Could not resolve nl80211 familty id");
        ret = WIFI_ERROR_UNKNOWN;
        goto unload;
    }

    pthread_mutex_init(&info->cb_lock, NULL);
    pthread_mutex_init(&info->pkt_fate_stats_lock, NULL);

    *handle = (wifi_handle) info;

    wifi_add_membership(*handle, "scan");
    wifi_add_membership(*handle, "mlme");
    wifi_add_membership(*handle, "regulatory");
    wifi_add_membership(*handle, "vendor");

    info->wifihal_ctrl_sock.s = 0;

    wifi_create_ctrl_socket(info);

    //! Initailise the monitoring clients list
    INITIALISE_LIST(&info->monitor_sockets);

    info->cldctx = cld80211_init();
    if (info->cldctx != NULL) {
        info->user_sock = info->cldctx->sock;
        ret = wifi_init_cld80211_sock_cb(info);
        if (ret != WIFI_SUCCESS) {
            ALOGE("Could not set cb for CLD80211 family");
            goto cld80211_cleanup;
        }

        status = cld80211_add_mcast_group(info->cldctx, "host_logs");
        if (status) {
            ALOGE("Failed to add mcast group host_logs :%d", status);
            goto cld80211_cleanup;
        }
        status = cld80211_add_mcast_group(info->cldctx, "fw_logs");
        if (status) {
            ALOGE("Failed to add mcast group fw_logs :%d", status);
            goto cld80211_cleanup;
        }
        status = cld80211_add_mcast_group(info->cldctx, "per_pkt_stats");
        if (status) {
            ALOGE("Failed to add mcast group per_pkt_stats :%d", status);
            goto cld80211_cleanup;
        }
        status = cld80211_add_mcast_group(info->cldctx, "diag_events");
        if (status) {
            ALOGE("Failed to add mcast group diag_events :%d", status);
            goto cld80211_cleanup;
        }
        status = cld80211_add_mcast_group(info->cldctx, "fatal_events");
        if (status) {
            ALOGE("Failed to add mcast group fatal_events :%d", status);
            goto cld80211_cleanup;
        }

        if(info->wifihal_ctrl_sock.s > 0)
        {
          status = cld80211_add_mcast_group(info->cldctx, "oem_msgs");
          if (status) {
             ALOGE("Failed to add mcast group oem_msgs :%d", status);
             goto cld80211_cleanup;
          }
        }
    } else {
        ret = wifi_init_user_sock(info);
        if (ret != WIFI_SUCCESS) {
            ALOGE("Failed to alloc user socket");
            goto unload;
        }
    }

    ret = wifi_init_interfaces(*handle);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to init interfaces");
        goto unload;
    }

    if (info->num_interfaces == 0) {
        ALOGE("No interfaces found");
        ret = WIFI_ERROR_UNINITIALIZED;
        goto unload;
    }

    index = wifi_get_iface_id(info, "wlan0");
    if (index == -1) {
        int i;
        for (i = 0; i < info->num_interfaces; i++)
        {
            free(info->interfaces[i]);
        }
        ALOGE("%s no iface with wlan0", __func__);
        ret = WIFI_ERROR_UNKNOWN;
        goto unload;
    }
    iface_handle = (wifi_interface_handle)info->interfaces[index];

    ret = acquire_supported_features(iface_handle,
            &info->supported_feature_set);
    if (ret != WIFI_SUCCESS) {
        ALOGI("Failed to get supported feature set : %d", ret);
        //acquire_supported_features failure is acceptable condition as legacy
        //drivers might not support the required vendor command. So, do not
        //consider it as failure of wifi_initialize
        ret = WIFI_SUCCESS;
    }

    ret = acquire_driver_supported_features(iface_handle,
                                  &info->driver_supported_features);
    if (ret != WIFI_SUCCESS) {
        ALOGI("Failed to get vendor feature set : %d", ret);
        ret = WIFI_SUCCESS;
    }

    ret =  wifi_get_logger_supported_feature_set(iface_handle,
                         &info->supported_logger_feature_set);
    if (ret != WIFI_SUCCESS)
        ALOGE("Failed to get supported logger feature set: %d", ret);

    ret =  wifi_get_firmware_version(iface_handle, hw_ver_type,
                                     MAX_HW_VER_LENGTH);
    if (ret == WIFI_SUCCESS) {
        hw_name = strstr(hw_ver_type, "HW:");
        if (hw_name) {
            hw_name += strlen("HW:");
            if (strncmp(hw_name, "QCA6174", 7) == 0)
               info->pkt_log_ver = PKT_LOG_V1;
            else
               info->pkt_log_ver = PKT_LOG_V2;
        } else {
           info->pkt_log_ver = PKT_LOG_V0;
        }
        ALOGV("%s: hardware version type %d", __func__, info->pkt_log_ver);
    } else {
        ALOGE("Failed to get firmware version: %d", ret);
    }

    ret = get_firmware_bus_max_size_supported(iface_handle);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to get supported bus size, error : %d", ret);
        info->firmware_bus_max_size = 1520;
    }

    ret = wifi_logger_ring_buffers_init(info);
    if (ret != WIFI_SUCCESS)
        ALOGE("Wifi Logger Ring Initialization Failed");

    ret = wifi_get_capabilities(iface_handle);
    if (ret != WIFI_SUCCESS)
        ALOGE("Failed to get wifi Capabilities, error: %d", ret);

    info->pkt_stats = (struct pkt_stats_s *)malloc(sizeof(struct pkt_stats_s));
    if (!info->pkt_stats) {
        ALOGE("%s: malloc Failed for size: %zu",
                __FUNCTION__, sizeof(struct pkt_stats_s));
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto unload;
    }

    info->rx_buf_size_allocated = MAX_RXMPDUS_PER_AMPDU * MAX_MSDUS_PER_MPDU
                                  * PKT_STATS_BUF_SIZE;

    info->rx_aggr_pkts =
        (wifi_ring_buffer_entry  *)malloc(info->rx_buf_size_allocated);
    if (!info->rx_aggr_pkts) {
        ALOGE("%s: malloc Failed for size: %d",
                __FUNCTION__, info->rx_buf_size_allocated);
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        info->rx_buf_size_allocated = 0;
        goto unload;
    }
    memset(info->rx_aggr_pkts, 0, info->rx_buf_size_allocated);

    info->exit_sockets[0] = -1;
    info->exit_sockets[1] = -1;

    if (socketpair(AF_UNIX, SOCK_STREAM, 0, info->exit_sockets) == -1) {
        ALOGE("Failed to create exit socket pair");
        ret = WIFI_ERROR_UNKNOWN;
        goto unload;
    }

    ALOGV("Initializing Gscan Event Handlers");
    ret = initializeGscanHandlers(info);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Initializing Gscan Event Handlers Failed");
        goto unload;
    }

    ret = initializeRSSIMonitorHandler(info);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Initializing RSSI Event Handler Failed");
        goto unload;
    }

    ret = initializeRadioHandler(info);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Initializing Radio Event handler Failed");
        goto unload;
    }

    ret = wifi_init_tcp_param_change_event_handler(iface_handle);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Initializing TCP param change event Handler Failed");
        goto unload;
    }

    ALOGV("Initialized Wifi HAL Successfully; vendor cmd = %d Supported"
            " features : %" PRIx64, NL80211_CMD_VENDOR, info->supported_feature_set);

    if (wifi_is_nan_ext_cmd_supported(iface_handle))
        info->support_nan_ext_cmd = true;
    else
        info->support_nan_ext_cmd = false;

    ALOGV("support_nan_ext_cmd is %d",
          info->support_nan_ext_cmd);

    ret = wifi_get_sar_version(iface_handle);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to  get  SAR Version, Setting it to default.");
        info->sar_version = QCA_WLAN_VENDOR_SAR_VERSION_1;
        ret = WIFI_SUCCESS;
    }

cld80211_cleanup:
    if (status != 0 || ret != WIFI_SUCCESS) {
        ret = WIFI_ERROR_UNKNOWN;
        cld80211lib_cleanup(info);
    }
unload:
    if (ret != WIFI_SUCCESS) {
        if (cmd_sock)
            nl_socket_free(cmd_sock);
        if (event_sock)
            nl_socket_free(event_sock);
        if (info) {
            if (info->cldctx) {
                cld80211lib_cleanup(info);
            } else if (info->user_sock) {
                nl_socket_free(info->user_sock);
            }
            if (info->pkt_stats) free(info->pkt_stats);
            if (info->rx_aggr_pkts) free(info->rx_aggr_pkts);
            if (info->wifihal_ctrl_sock.s) close(info->wifihal_ctrl_sock.s);
            wifi_logger_ring_buffers_deinit(info);
            cleanupGscanHandlers(info);
            cleanupRSSIMonitorHandler(info);
            cleanupRadioHandler(info);
            cleanupTCPParamCommand(info);
            free(info->event_cb);
            if (info->driver_supported_features.flags) {
                free(info->driver_supported_features.flags);
                info->driver_supported_features.flags = NULL;
            }
            free(info);
        }
    }

    return ret;
}

#ifdef WIFI_DRIVER_STATE_CTRL_PARAM
static int wifi_update_driver_state(const char *state) {
    struct timespec ts;
    int len, fd, ret = 0, count = 5;
    ts.tv_sec = 0;
    ts.tv_nsec = 200 * 1000000L;
    do {
        if (access(WIFI_DRIVER_STATE_CTRL_PARAM, W_OK) == 0)
            break;
        nanosleep(&ts, (struct timespec *)NULL);
    } while (--count > 0); /* wait at most 1 second for completion. */
    if (count == 0) {
        ALOGE("Failed to access driver state control param %s, %d at %s",
              strerror(errno), errno, WIFI_DRIVER_STATE_CTRL_PARAM);
        return -1;
    }
    fd = TEMP_FAILURE_RETRY(open(WIFI_DRIVER_STATE_CTRL_PARAM, O_WRONLY));
    if (fd < 0) {
        ALOGE("Failed to open driver state control param at %s",
              WIFI_DRIVER_STATE_CTRL_PARAM);
        close(fd);
        return -1;
    }
    len = strlen(state) + 1;
    if (TEMP_FAILURE_RETRY(write(fd, state, len)) != len) {
        ALOGE("Failed to write driver state control param at %s",
              WIFI_DRIVER_STATE_CTRL_PARAM);
        close(fd);
        ret = -1;
    }
    close(fd);
    return ret;
}
#endif

wifi_error wifi_wait_for_driver_ready(void)
{
    // This function will wait to make sure basic client netdev is created
    // Function times out after 10 seconds
    int count = (POLL_DRIVER_MAX_TIME_MS * 1000) / POLL_DRIVER_DURATION_US;
    FILE *fd;

#if defined(WIFI_DRIVER_STATE_CTRL_PARAM) && defined(WIFI_DRIVER_STATE_ON)
    if (wifi_update_driver_state(WIFI_DRIVER_STATE_ON) < 0) {
        return WIFI_ERROR_UNKNOWN;
    }
#endif

    do {
        if ((fd = fopen("/sys/class/net/wlan0", "r")) != NULL) {
            fclose(fd);
            return WIFI_SUCCESS;
        }
        usleep(POLL_DRIVER_DURATION_US);
    } while(--count > 0);

    ALOGE("Timed out wating on Driver ready ... ");
    return WIFI_ERROR_TIMED_OUT;
}

static int wifi_add_membership(wifi_handle handle, const char *group)
{
    hal_info *info = getHalInfo(handle);

    int id = wifi_get_multicast_id(handle, "nl80211", group);
    if (id < 0) {
        ALOGE("Could not find group %s", group);
        return id;
    }

    int ret = nl_socket_add_membership(info->event_sock, id);
    if (ret < 0) {
        ALOGE("Could not add membership to group %s", group);
    }

    return ret;
}

static void internal_cleaned_up_handler(wifi_handle handle)
{
    hal_info *info = getHalInfo(handle);
    wifi_cleaned_up_handler cleaned_up_handler = info->cleaned_up_handler;
    wifihal_mon_sock_t *reg, *tmp;

    if (info->cmd_sock != 0) {
        nl_socket_free(info->cmd_sock);
        nl_socket_free(info->event_sock);
        info->cmd_sock = NULL;
        info->event_sock = NULL;
    }

    if (info->wifihal_ctrl_sock.s != 0) {
        close(info->wifihal_ctrl_sock.s);
        unlink(info->wifihal_ctrl_sock.local.sun_path);
        info->wifihal_ctrl_sock.s = 0;
    }

   list_for_each_entry_safe(reg, tmp, &info->monitor_sockets, list) {
        del_from_list(&reg->list);
        free(reg);
    }

    if (info->interfaces) {
        for (int i = 0; i < info->num_interfaces; i++)
            free(info->interfaces[i]);
        free(info->interfaces);
    }

    if (info->cldctx != NULL) {
        cld80211lib_cleanup(info);
    } else if (info->user_sock != 0) {
        nl_socket_free(info->user_sock);
        info->user_sock = NULL;
    }

    if (info->pkt_stats)
        free(info->pkt_stats);
    if (info->rx_aggr_pkts)
        free(info->rx_aggr_pkts);
    wifi_logger_ring_buffers_deinit(info);
    cleanupGscanHandlers(info);
    cleanupRSSIMonitorHandler(info);
    cleanupRadioHandler(info);
    cleanupTCPParamCommand(info);

    if (info->num_event_cb)
        ALOGE("%d events were leftover without being freed",
              info->num_event_cb);
    free(info->event_cb);

    if (info->exit_sockets[0] >= 0) {
        close(info->exit_sockets[0]);
        info->exit_sockets[0] = -1;
    }

    if (info->exit_sockets[1] >= 0) {
        close(info->exit_sockets[1]);
        info->exit_sockets[1] = -1;
    }

    if (info->pkt_fate_stats) {
        free(info->pkt_fate_stats);
        info->pkt_fate_stats = NULL;
    }

    if (info->driver_supported_features.flags) {
        free(info->driver_supported_features.flags);
        info->driver_supported_features.flags = NULL;
    }

    (*cleaned_up_handler)(handle);
    pthread_mutex_destroy(&info->cb_lock);
    pthread_mutex_destroy(&info->pkt_fate_stats_lock);
    free(info);
}

void wifi_cleanup(wifi_handle handle, wifi_cleaned_up_handler handler)
{
    if (!handle) {
        ALOGE("Handle is null");
        return;
    }

    hal_info *info = getHalInfo(handle);
    info->cleaned_up_handler = handler;
    // Remove the dynamically created interface during wifi cleanup.
    wifi_cleanup_dynamic_ifaces(handle);

    TEMP_FAILURE_RETRY(write(info->exit_sockets[0], "E", 1));

    // Ensure wifi_event_loop() exits by setting clean_up to true.
    info->clean_up = true;
    ALOGI("Sent msg on exit sock to unblock poll()");
}



static int validate_cld80211_msg(nlmsghdr *nlh, int family, int cmd)
{
    //! Enhance this API
    struct genlmsghdr *hdr;
    hdr = (genlmsghdr *)nlmsg_data(nlh);

    if (nlh->nlmsg_len > DEFAULT_PAGE_SIZE - sizeof(wifihal_ctrl_req_t))
    {
      ALOGE("%s: Invalid nlmsg length", __FUNCTION__);
      return -1;
    }
    if(hdr->cmd == WLAN_NL_MSG_OEM)
    {
      ALOGV("%s: FAMILY ID : %d ,NL CMD : %d received", __FUNCTION__,
             nlh->nlmsg_type, hdr->cmd);

      //! Update pid with the wifihal pid
      nlh->nlmsg_pid = getpid();
      return 0;
    }
    else
    {
      ALOGE("%s: NL CMD : %d received is not allowed", __FUNCTION__, hdr->cmd);
      return -1;
    }
}


static int validate_genl_msg(nlmsghdr *nlh, int family, int cmd)
{
    //! Enhance this API
    struct genlmsghdr *hdr;
    hdr = (genlmsghdr *)nlmsg_data(nlh);

    if (nlh->nlmsg_len > DEFAULT_PAGE_SIZE - sizeof(wifihal_ctrl_req_t))
    {
      ALOGE("%s: Invalid nlmsg length", __FUNCTION__);
      return -1;
    }
    if(hdr->cmd == NL80211_CMD_FRAME ||
       hdr->cmd == NL80211_CMD_REGISTER_ACTION)
    {
      ALOGV("%s: FAMILY ID : %d ,NL CMD : %d received", __FUNCTION__,
             nlh->nlmsg_type, hdr->cmd);
      return 0;
    }
    else
    {
      ALOGE("%s: NL CMD : %d received is not allowed", __FUNCTION__, hdr->cmd);
      return -1;
    }
}

static int send_nl_data(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg)
{
    hal_info *info = getHalInfo(handle);
    struct nl_msg *msg = NULL;
    int retval = -1;

    //! attach monitor socket if it was not it the list
    if(ctrl_msg->monsock_len)
    {
      retval = attach_monitor_sock(handle, ctrl_msg);
      if(retval)
        goto nl_out;
    }

    msg = nlmsg_alloc();
    if (!msg)
    {
       ALOGE("%s: Memory allocation failed \n", __FUNCTION__);
       goto nl_out;
    }

    if (ctrl_msg->data_len > nlmsg_get_max_size(msg))
    {
        ALOGE("%s: Invalid ctrl msg length \n", __FUNCTION__);
        retval = -1;
        goto nl_out;
    }
    memcpy((char *)msg->nm_nlh, (char *)ctrl_msg->data, ctrl_msg->data_len);

   if(ctrl_msg->family_name == GENERIC_NL_FAMILY)
   {
     //! Before sending the received gennlmsg to kernel,
     //! better to have checks for allowed commands
     retval = validate_genl_msg(msg->nm_nlh, ctrl_msg->family_name, ctrl_msg->cmd_id);
     if (retval < 0)
         goto nl_out;

     retval = nl_send_auto_complete(info->event_sock, msg);    /* send message */
     if (retval < 0)
     {
       ALOGE("%s: nl_send_auto_complete - failed : %d \n", __FUNCTION__, retval);
       goto nl_out;
     }
     ALOGI("%s: sent gennl msg of len: %d to driver\n", __FUNCTION__, ctrl_msg->data_len);
     retval = internal_pollin_handler(handle, info->event_sock);
  }
  else if (ctrl_msg->family_name == CLD80211_FAMILY)
  {
    if (info->cldctx != NULL)
    {
      //! Before sending the received cld80211 msg to kernel,
      //! better to have checks for allowed commands
      retval = validate_cld80211_msg(msg->nm_nlh, ctrl_msg->family_name, ctrl_msg->cmd_id);
      if (retval < 0)
         goto nl_out;

      retval = cld80211_send_msg(info->cldctx, msg);
      if (retval != 0)
      {
        ALOGE("%s: send cld80211 message - failed\n", __FUNCTION__);
        goto nl_out;
      }
      ALOGI("%s: sent cld80211 msg of len: %d to driver\n", __FUNCTION__, ctrl_msg->data_len);
    }
    else
    {
      ALOGE("%s: cld80211 ctx not present \n", __FUNCTION__);
    }
  }
  else
  {
    ALOGE("%s: Unknown family name : %d \n", __FUNCTION__, ctrl_msg->family_name);
    retval = -1;
  }
nl_out:
  if (msg)
  {
    nlmsg_free(msg);
  }
  return retval;
}

static int register_monitor_sock(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg, int attach)
{
    hal_info *info = getHalInfo(handle);

    wifihal_mon_sock_t *reg, *nreg;
    char *match = NULL;
    unsigned int match_len = 0;
    unsigned int type;

    //! For Register Action frames, compare the match length and match buffer.
    //! For other registrations such as oem messages,
    //! diag messages check for respective commands

    if((ctrl_msg->family_name == GENERIC_NL_FAMILY) &&
       (ctrl_msg->cmd_id == NL80211_CMD_REGISTER_ACTION))
    {
       struct genlmsghdr *genlh;
       struct  nlmsghdr *nlh = (struct  nlmsghdr *)ctrl_msg->data;
       genlh = (struct genlmsghdr *)nlmsg_data(nlh);
       struct nlattr *nlattrs[NL80211_ATTR_MAX + 1];

       if (nlh->nlmsg_len > DEFAULT_PAGE_SIZE - sizeof(*ctrl_msg))
       {
         ALOGE("%s: Invalid nlmsg length", __FUNCTION__);
         return -1;
       }
       if (nla_parse(nlattrs, NL80211_ATTR_MAX, genlmsg_attrdata(genlh, 0),
                 genlmsg_attrlen(genlh, 0), NULL))
       {
         ALOGE("unable to parse nl attributes");
         return -1;
       }
       if (!nlattrs[NL80211_ATTR_FRAME_TYPE])
       {
         ALOGD("No Valid frame type");
       }
       else
       {
         type = nla_get_u16(nlattrs[NL80211_ATTR_FRAME_TYPE]);
       }
       if (!nlattrs[NL80211_ATTR_FRAME_MATCH])
       {
         ALOGE("No Frame Match");
         return -1;
       }
       else
       {
         match_len = nla_len(nlattrs[NL80211_ATTR_FRAME_MATCH]);
         match = (char *)nla_data(nlattrs[NL80211_ATTR_FRAME_MATCH]);

         list_for_each_entry(reg, &info->monitor_sockets, list) {

           int mlen = min(match_len, reg->match_len);

           if (reg->match_len == 0)
               continue;

           if (memcmp(reg->match, match, mlen) == 0) {

              if((ctrl_msg->monsock_len == reg->monsock_len) &&
                 (memcmp((char *)&reg->monsock, (char *)&ctrl_msg->monsock, ctrl_msg->monsock_len) == 0))
              {
                if(attach)
                {
                  ALOGE(" %s :Action frame already registered for this client ", __FUNCTION__);
                  return -2;
                }
                else
                {
                  del_from_list(&reg->list);
                  free(reg);
                  return 0;
                }
              }
              else
              {
                //! when action frame registered for other client,
                //! you can't attach or dettach for new client
                ALOGE(" %s :Action frame registered for other client ", __FUNCTION__);
                return -2;
              }
           }
         }
       }
    }
    else
    {
      list_for_each_entry(reg, &info->monitor_sockets, list) {

         //! Checking for monitor sock in the list :

         //! For attach request :
         //! if sock is not present, then it is a new entry , so add to list.
         //! if sock is present,  and cmd_id does not match, add another entry to list.
         //! if sock is present, and cmd_id matches, return 0.

         //! For dettach req :
         //! if sock is not present, return error -2.
         //! if sock is present,  and cmd_id does not match, return error -2.
         //! if sock is present, and cmd_id matches, delete entry and return 0.

         if (ctrl_msg->monsock_len != reg->monsock_len)
             continue;

         if (memcmp((char *)&reg->monsock, (char *)&ctrl_msg->monsock, ctrl_msg->monsock_len) == 0) {

            if((reg->family_name == ctrl_msg->family_name) && (reg->cmd_id == ctrl_msg->cmd_id))
            {
               if(!attach)
               {
                 del_from_list(&reg->list);
                 free(reg);
               }
               return 0;
            }
         }
      }
    }

    if(attach)
    {
       if (ctrl_msg->monsock_len > sizeof(struct sockaddr_un))
       {
         ALOGE("%s: Invalid monitor socket length \n", __FUNCTION__);
         return -3;
       }

       nreg = (wifihal_mon_sock_t *)malloc(sizeof(*reg) + match_len);
        if (!nreg)
           return -1;

       memset((char *)nreg, 0, sizeof(*reg) + match_len);
       nreg->family_name = ctrl_msg->family_name;
       nreg->cmd_id = ctrl_msg->cmd_id;
       nreg->monsock_len = ctrl_msg->monsock_len;
       memcpy((char *)&nreg->monsock, (char *)&ctrl_msg->monsock, ctrl_msg->monsock_len);

       if(match_len && match)
       {
         nreg->match_len = match_len;
         memcpy(nreg->match, match, match_len);
       }
       add_to_list(&nreg->list, &info->monitor_sockets);
    }
    else
    {
       //! Not attached, so cant be dettached
       ALOGE("%s: Dettaching the unregistered socket \n", __FUNCTION__);
       return -2;
    }

   return 0;
}

static int attach_monitor_sock(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg)
{
   return register_monitor_sock(handle, ctrl_msg, 1);
}

static int dettach_monitor_sock(wifi_handle handle, wifihal_ctrl_req_t *ctrl_msg)
{
   return register_monitor_sock(handle, ctrl_msg, 0);
}

static int internal_pollin_handler_app(wifi_handle handle,  struct ctrl_sock *sock)
{
    int retval = -1;
    int res;
    struct sockaddr_un from;
    socklen_t fromlen = sizeof(from);
    wifihal_ctrl_req_t *ctrl_msg;
    wifihal_ctrl_sync_rsp_t ctrl_reply;

    ctrl_msg = (wifihal_ctrl_req_t *)malloc(DEFAULT_PAGE_SIZE);
    if(ctrl_msg == NULL)
    {
      ALOGE ("Memory allocation failure");
      return -1;
    }

    memset((char *)ctrl_msg, 0, DEFAULT_PAGE_SIZE);

    res = recvfrom(sock->s, (char *)ctrl_msg, DEFAULT_PAGE_SIZE, 0,
                   (struct sockaddr *)&from, &fromlen);
    if (res < 0) {
        ALOGE("recvfrom(ctrl_iface): %s",
               strerror(errno));
        if(ctrl_msg)
           free(ctrl_msg);

        return 0;
    }
    switch(ctrl_msg->ctrl_cmd)
    {
       case WIFIHAL_CTRL_MONITOR_ATTACH:
         retval = attach_monitor_sock(handle, ctrl_msg);
       break;
       case WIFIHAL_CTRL_MONITOR_DETTACH:
         retval = dettach_monitor_sock(handle, ctrl_msg);
       break;
       case WIFIHAL_CTRL_SEND_NL_DATA:
         retval = send_nl_data(handle, ctrl_msg);
       break;
       default:
       break;
    }

    ctrl_reply.ctrl_cmd = ctrl_msg->ctrl_cmd;
    ctrl_reply.family_name = ctrl_msg->family_name;
    ctrl_reply.cmd_id = ctrl_msg->cmd_id;
    ctrl_reply.status = retval;

    if(ctrl_msg)
       free(ctrl_msg);

    if (sendto(sock->s, (char *)&ctrl_reply, sizeof(ctrl_reply), 0, (struct sockaddr *)&from,
               fromlen) < 0) {
                  int _errno = errno;
                  ALOGE("socket send failed : %d",_errno);

       if (_errno == ENOBUFS || _errno == EAGAIN) {
           /*
            * The socket send buffer could be full. This
            * may happen if client programs are not
            * receiving their pending messages. Close and
            * reopen the socket as a workaround to avoid
            * getting stuck being unable to send any new
            * responses.
            */
          }
        }
      return res;
}

static int internal_pollin_handler(wifi_handle handle, struct nl_sock *sock)
{
    struct nl_cb *cb = nl_socket_get_cb(sock);

    int res = nl_recvmsgs(sock, cb);
    if(res)
        ALOGE("Error :%d while reading nl msg", res);
    nl_cb_put(cb);
    return res;
}

static void internal_event_handler_app(wifi_handle handle, int events,
                                    struct ctrl_sock *sock)
{
    if (events & POLLERR) {
        ALOGE("Error reading from wifi_hal ctrl socket");
        internal_pollin_handler_app(handle, sock);
    } else if (events & POLLHUP) {
        ALOGE("Remote side hung up");
    } else if (events & POLLIN) {
        //ALOGI("Found some events!!!");
        internal_pollin_handler_app(handle, sock);
    } else {
        ALOGE("Unknown event - %0x", events);
    }
}

static void internal_event_handler(wifi_handle handle, int events,
                                   struct nl_sock *sock)
{
    if (events & POLLERR) {
        ALOGE("Error reading from socket");
        internal_pollin_handler(handle, sock);
    } else if (events & POLLHUP) {
        ALOGE("Remote side hung up");
    } else if (events & POLLIN) {
        //ALOGI("Found some events!!!");
        internal_pollin_handler(handle, sock);
    } else {
        ALOGE("Unknown event - %0x", events);
    }
}

static bool exit_event_handler(int fd) {
    char buf[4];
    memset(buf, 0, sizeof(buf));

    TEMP_FAILURE_RETRY(read(fd, buf, sizeof(buf)));
    ALOGI("exit_event_handler, buf=%s", buf);
    if (strncmp(buf, "E", 1) == 0) {
       return true;
    }

    return false;
}

/* Run event handler */
void wifi_event_loop(wifi_handle handle)
{
    hal_info *info = getHalInfo(handle);
    if (info->in_event_loop) {
        return;
    } else {
        info->in_event_loop = true;
    }

    pollfd pfd[4];
    memset(&pfd, 0, 4*sizeof(pfd[0]));

    pfd[0].fd = nl_socket_get_fd(info->event_sock);
    pfd[0].events = POLLIN;

    pfd[1].fd = nl_socket_get_fd(info->user_sock);
    pfd[1].events = POLLIN;

    pfd[2].fd = info->exit_sockets[1];
    pfd[2].events = POLLIN;

    if(info->wifihal_ctrl_sock.s > 0) {
      pfd[3].fd = info->wifihal_ctrl_sock.s ;
      pfd[3].events = POLLIN;
    }
    /* TODO: Add support for timeouts */

    do {
        pfd[0].revents = 0;
        pfd[1].revents = 0;
        pfd[2].revents = 0;
        pfd[3].revents = 0;
        //ALOGI("Polling sockets");
        int result = poll(pfd, 4, -1);
        if (result < 0) {
            ALOGE("Error polling socket");
        } else {
            if (pfd[0].revents & (POLLIN | POLLHUP | POLLERR)) {
                internal_event_handler(handle, pfd[0].revents, info->event_sock);
            }
            if (pfd[1].revents & (POLLIN | POLLHUP | POLLERR)) {
                internal_event_handler(handle, pfd[1].revents, info->user_sock);
            }
            if ((info->wifihal_ctrl_sock.s > 0) && (pfd[3].revents & (POLLIN | POLLHUP | POLLERR))) {
                internal_event_handler_app(handle, pfd[3].revents, &info->wifihal_ctrl_sock);
            }
            if (pfd[2].revents & POLLIN) {
                if (exit_event_handler(pfd[2].fd)) {
                    break;
                }
            }
        }
        rb_timerhandler(info);
    } while (!info->clean_up);
    internal_cleaned_up_handler(handle);
    ALOGI("wifi_event_loop() exits success");
}

static int user_sock_message_handler(nl_msg *msg, void *arg)
{
    wifi_handle handle = (wifi_handle)arg;
    hal_info *info = getHalInfo(handle);

    diag_message_handler(info, msg);

    return NL_OK;
}

static int internal_valid_message_handler(nl_msg *msg, void *arg)
{
    wifi_handle handle = (wifi_handle)arg;
    hal_info *info = getHalInfo(handle);

    WifiEvent event(msg);
    int res = event.parse();
    if (res < 0) {
        ALOGE("Failed to parse event: %d", res);
        return NL_SKIP;
    }

    int cmd = event.get_cmd();
    uint32_t vendor_id = 0;
    int subcmd = 0;

    if (cmd == NL80211_CMD_VENDOR) {
        vendor_id = event.get_u32(NL80211_ATTR_VENDOR_ID);
        subcmd = event.get_u32(NL80211_ATTR_VENDOR_SUBCMD);
        /* Restrict printing GSCAN_FULL_RESULT which is causing lot
           of logs in bug report */
        if (subcmd != QCA_NL80211_VENDOR_SUBCMD_GSCAN_FULL_SCAN_RESULT) {
            ALOGI("event received %s, vendor_id = 0x%0x, subcmd = 0x%0x",
                  event.get_cmdString(), vendor_id, subcmd);
        }
    }
    else if((info->wifihal_ctrl_sock.s > 0) && (cmd == NL80211_CMD_FRAME))
    {
       struct genlmsghdr *genlh;
       struct  nlmsghdr *nlh = nlmsg_hdr(msg);
       genlh = (struct genlmsghdr *)nlmsg_data(nlh);
       struct nlattr *nlattrs[NL80211_ATTR_MAX + 1];

       wifihal_ctrl_event_t *ctrl_evt;
       char *buff;
       wifihal_mon_sock_t *reg;

       nla_parse(nlattrs, NL80211_ATTR_MAX, genlmsg_attrdata(genlh, 0),
                 genlmsg_attrlen(genlh, 0), NULL);

       if (!nlattrs[NL80211_ATTR_FRAME])
       {
         ALOGD("No Frame body");
         return WIFI_SUCCESS;
       }
       ctrl_evt = (wifihal_ctrl_event_t *)malloc(sizeof(*ctrl_evt) + nlh->nlmsg_len);
       if(ctrl_evt == NULL)
       {
         ALOGE("Memory allocation failure");
         return -1;
       }
       memset((char *)ctrl_evt, 0, sizeof(*ctrl_evt) + nlh->nlmsg_len);
       ctrl_evt->family_name = GENERIC_NL_FAMILY;
       ctrl_evt->cmd_id = cmd;
       ctrl_evt->data_len = nlh->nlmsg_len;
       memcpy(ctrl_evt->data, (char *)nlh, ctrl_evt->data_len);


       buff = (char *)nla_data(nlattrs[NL80211_ATTR_FRAME]) + 24; //! Size of Wlan80211FrameHeader

       list_for_each_entry(reg, &info->monitor_sockets, list) {

                 if (memcmp(reg->match, buff, reg->match_len))
                     continue;

                 /* found match! */
                 /* Indicate the received Action frame to respective client */
                 ALOGI("send gennl msg of len : %d to apps", ctrl_evt->data_len);
                 if (sendto(info->wifihal_ctrl_sock.s, (char *)ctrl_evt,
                            sizeof(*ctrl_evt) + ctrl_evt->data_len,
                            0, (struct sockaddr *)&reg->monsock, reg->monsock_len) < 0)
                 {
                   int _errno = errno;
                   ALOGE("socket send failed : %d",_errno);

                   if (_errno == ENOBUFS || _errno == EAGAIN) {
                   }
                 }

        }
        free(ctrl_evt);
    }

    else {
        ALOGV("event received %s", event.get_cmdString());
    }

    // event.log();

    bool dispatched = false;

    pthread_mutex_lock(&info->cb_lock);

    for (int i = 0; i < info->num_event_cb; i++) {
        if (cmd == info->event_cb[i].nl_cmd) {
            if (cmd == NL80211_CMD_VENDOR
                && ((vendor_id != info->event_cb[i].vendor_id)
                || (subcmd != info->event_cb[i].vendor_subcmd)))
            {
                /* event for a different vendor, ignore it */
                continue;
            }

            cb_info *cbi = &(info->event_cb[i]);
            pthread_mutex_unlock(&info->cb_lock);
            if (cbi->cb_func) {
                (*(cbi->cb_func))(msg, cbi->cb_arg);
                dispatched = true;
            }
            return NL_OK;
        }
    }

#ifdef QC_HAL_DEBUG
    if (!dispatched) {
        ALOGI("event ignored!!");
    }
#endif

    pthread_mutex_unlock(&info->cb_lock);
    return NL_OK;
}

////////////////////////////////////////////////////////////////////////////////

class GetMulticastIdCommand : public WifiCommand
{
private:
    const char *mName;
    const char *mGroup;
    int   mId;
public:
    GetMulticastIdCommand(wifi_handle handle, const char *name,
            const char *group) : WifiCommand(handle, 0)
    {
        mName = name;
        mGroup = group;
        mId = -1;
    }

    int getId() {
        return mId;
    }

    virtual wifi_error create() {
        int nlctrlFamily = genl_ctrl_resolve(mInfo->cmd_sock, "nlctrl");
        // ALOGI("ctrl family = %d", nlctrlFamily);
        wifi_error ret = mMsg.create(nlctrlFamily, CTRL_CMD_GETFAMILY, 0, 0);
        if (ret != WIFI_SUCCESS)
            return ret;

        ret = mMsg.put_string(CTRL_ATTR_FAMILY_NAME, mName);
        return ret;
    }

    virtual int handleResponse(WifiEvent& reply) {

        // ALOGI("handling reponse in %s", __func__);

        struct nlattr **tb = reply.attributes();
        struct nlattr *mcgrp = NULL;
        int i;

        if (!tb[CTRL_ATTR_MCAST_GROUPS]) {
            ALOGI("No multicast groups found");
            return NL_SKIP;
        } else {
            // ALOGI("Multicast groups attr size = %d",
            // nla_len(tb[CTRL_ATTR_MCAST_GROUPS]));
        }

        for_each_attr(mcgrp, tb[CTRL_ATTR_MCAST_GROUPS], i) {

            // ALOGI("Processing group");
            struct nlattr *tb2[CTRL_ATTR_MCAST_GRP_MAX + 1];
            nla_parse(tb2, CTRL_ATTR_MCAST_GRP_MAX, (nlattr *)nla_data(mcgrp),
                nla_len(mcgrp), NULL);
            if (!tb2[CTRL_ATTR_MCAST_GRP_NAME] || !tb2[CTRL_ATTR_MCAST_GRP_ID])
            {
                continue;
            }

            char *grpName = (char *)nla_data(tb2[CTRL_ATTR_MCAST_GRP_NAME]);
            int grpNameLen = nla_len(tb2[CTRL_ATTR_MCAST_GRP_NAME]);

            // ALOGI("Found group name %s", grpName);

            if (strncmp(grpName, mGroup, grpNameLen) != 0)
                continue;

            mId = nla_get_u32(tb2[CTRL_ATTR_MCAST_GRP_ID]);
            break;
        }

        return NL_SKIP;
    }

};

static int wifi_get_multicast_id(wifi_handle handle, const char *name,
        const char *group)
{
    GetMulticastIdCommand cmd(handle, name, group);
    int res = cmd.requestResponse();
    if (res < 0)
        return res;
    else
        return cmd.getId();
}

/////////////////////////////////////////////////////////////////////////

static bool is_wifi_interface(const char *name)
{
    // filter out bridge interface
    if (strstr(name, "br") != NULL) {
        return false;
    }

    if (strncmp(name, "wlan", 4) != 0 && strncmp(name, "p2p", 3) != 0
        && strncmp(name, "wifi", 4) != 0
        && strncmp(name, "swlan", 5) != 0) {
        /* not a wifi interface; ignore it */
        return false;
    } else {
        return true;
    }
}

static int get_interface(const char *name, interface_info *info)
{
    strlcpy(info->name, name, (IFNAMSIZ + 1));
    info->id = if_nametoindex(name);
    // ALOGI("found an interface : %s, id = %d", name, info->id);
    return WIFI_SUCCESS;
}

wifi_error wifi_init_interfaces(wifi_handle handle)
{
    hal_info *info = (hal_info *)handle;

    struct dirent *de;

    DIR *d = opendir("/sys/class/net");
    if (d == 0)
        return WIFI_ERROR_UNKNOWN;

    int n = 0;
    while ((de = readdir(d))) {
        if (de->d_name[0] == '.')
            continue;
        if (is_wifi_interface(de->d_name) ) {
            n++;
        }
    }

    closedir(d);

    d = opendir("/sys/class/net");
    if (d == 0)
        return WIFI_ERROR_UNKNOWN;

    info->interfaces = (interface_info **)malloc(sizeof(interface_info *) * n);
    if (info->interfaces == NULL) {
        ALOGE("%s: Error info->interfaces NULL", __func__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    int i = 0;
    while ((de = readdir(d))) {
        if (de->d_name[0] == '.')
            continue;
        if (is_wifi_interface(de->d_name)) {
            interface_info *ifinfo
                = (interface_info *)malloc(sizeof(interface_info));
            if (ifinfo == NULL) {
                ALOGE("%s: Error ifinfo NULL", __func__);
                while (i > 0) {
                    free(info->interfaces[i-1]);
                    i--;
                }
                free(info->interfaces);
                return WIFI_ERROR_OUT_OF_MEMORY;
            }
            if (get_interface(de->d_name, ifinfo) != WIFI_SUCCESS) {
                free(ifinfo);
                continue;
            }
            ifinfo->handle = handle;
            info->interfaces[i] = ifinfo;
            i++;
        }
    }

    closedir(d);

    info->num_interfaces = n;

    return WIFI_SUCCESS;
}

wifi_error wifi_get_ifaces(wifi_handle handle, int *num,
        wifi_interface_handle **interfaces)
{
    hal_info *info = (hal_info *)handle;

    /* In case of dynamic interface add/remove, interface handles need to be
     * updated so that, interface specific APIs could be instantiated.
     * Reload here to get interfaces which are dynamically added. */

    if (info->num_interfaces > 0) {
        for (int i = 0; i < info->num_interfaces; i++)
            free(info->interfaces[i]);
        free(info->interfaces);
    }

    wifi_error ret = wifi_init_interfaces(handle);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to init interfaces while wifi_get_ifaces");
        return ret;
    }

    *interfaces = (wifi_interface_handle *)info->interfaces;
    *num = info->num_interfaces;

    return WIFI_SUCCESS;
}

wifi_error wifi_get_iface_name(wifi_interface_handle handle, char *name,
        size_t size)
{
    interface_info *info = (interface_info *)handle;
    strlcpy(name, info->name, size);
    return WIFI_SUCCESS;
}

/* Get the supported Feature set */
wifi_error wifi_get_supported_feature_set(wifi_interface_handle iface,
        feature_set *set)
{
    int ret = 0;
    wifi_handle handle = getWifiHandle(iface);
    *set = 0;
    hal_info *info = getHalInfo(handle);

    ret = acquire_supported_features(iface, set);
    if (ret != WIFI_SUCCESS) {
        *set = info->supported_feature_set;
        ALOGV("Supported feature set acquired at initialization : %" PRIx64, *set);
    } else {
        info->supported_feature_set = *set;
        ALOGV("Supported feature set acquired : %" PRIx64, *set);
    }
    return WIFI_SUCCESS;
}

wifi_error wifi_get_concurrency_matrix(wifi_interface_handle handle,
                                       int set_size_max,
                                       feature_set set[], int *set_size)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifihalGeneric *vCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);

    if (set == NULL) {
        ALOGE("%s: NULL set pointer provided. Exit.",
            __func__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    vCommand = new WifihalGeneric(wifiHandle, 0,
            OUI_QCA,
            QCA_NL80211_VENDOR_SUBCMD_GET_CONCURRENCY_MATRIX);
    if (vCommand == NULL) {
        ALOGE("%s: Error vCommand NULL", __func__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    /* Create the message */
    ret = vCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    ret = vCommand->put_u32(
          QCA_WLAN_VENDOR_ATTR_GET_CONCURRENCY_MATRIX_CONFIG_PARAM_SET_SIZE_MAX,
          set_size_max);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    vCommand->attr_end(nlData);

    /* Populate the input received from caller/framework. */
    vCommand->setMaxSetSize(set_size_max);
    vCommand->setSizePtr(set_size);
    vCommand->setConcurrencySet(set);

    ret = vCommand->requestResponse();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestResponse() error: %d", __func__, ret);

cleanup:
    delete vCommand;
    if (ret)
        *set_size = 0;
    return ret;
}


wifi_error wifi_set_nodfs_flag(wifi_interface_handle handle, u32 nodfs)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);

    vCommand = new WifiVendorCommand(wifiHandle, 0,
            OUI_QCA,
            QCA_NL80211_VENDOR_SUBCMD_NO_DFS_FLAG);
    if (vCommand == NULL) {
        ALOGE("%s: Error vCommand NULL", __func__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    /* Create the message */
    ret = vCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    /* Add the fixed part of the mac_oui to the nl command */
    ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_SET_NO_DFS_FLAG, nodfs);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    vCommand->attr_end(nlData);

    ret = vCommand->requestResponse();
    /* Don't check response since we aren't expecting one */

cleanup:
    delete vCommand;
    return ret;
}

wifi_error wifi_start_sending_offloaded_packet(wifi_request_id id,
                                               wifi_interface_handle iface,
                                               u16 ether_type,
                                               u8 *ip_packet,
                                               u16 ip_packet_len,
                                               u8 *src_mac_addr,
                                               u8 *dst_mac_addr,
                                               u32 period_msec)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;

    ret = initialize_vendor_cmd(iface, id,
                                QCA_NL80211_VENDOR_SUBCMD_OFFLOADED_PACKETS,
                                &vCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __func__);
        return ret;
    }

    ALOGV("ether type 0x%04x\n", ether_type);
    ALOGV("ip packet length : %u\nIP Packet:", ip_packet_len);
    hexdump(ip_packet, ip_packet_len);
    ALOGV("Src Mac Address: " MAC_ADDR_STR "\nDst Mac Address: " MAC_ADDR_STR
          "\nPeriod in msec : %u", MAC_ADDR_ARRAY(src_mac_addr),
          MAC_ADDR_ARRAY(dst_mac_addr), period_msec);

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    ret = vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_SENDING_CONTROL,
            QCA_WLAN_OFFLOADED_PACKETS_SENDING_START);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_REQUEST_ID,
            id);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_ETHER_PROTO_TYPE,
            ether_type);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->put_bytes(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_IP_PACKET_DATA,
            (const char *)ip_packet, ip_packet_len);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->put_addr(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_SRC_MAC_ADDR,
            src_mac_addr);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->put_addr(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_DST_MAC_ADDR,
            dst_mac_addr);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_PERIOD,
            period_msec);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    vCommand->attr_end(nlData);

    ret = vCommand->requestResponse();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

cleanup:
    delete vCommand;
    return ret;
}

wifi_error wifi_stop_sending_offloaded_packet(wifi_request_id id,
                                              wifi_interface_handle iface)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;

    ret = initialize_vendor_cmd(iface, id,
                                QCA_NL80211_VENDOR_SUBCMD_OFFLOADED_PACKETS,
                                &vCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __func__);
        return ret;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    ret = vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_SENDING_CONTROL,
            QCA_WLAN_OFFLOADED_PACKETS_SENDING_STOP);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_OFFLOADED_PACKETS_REQUEST_ID,
            id);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    vCommand->attr_end(nlData);

    ret = vCommand->requestResponse();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

cleanup:
    delete vCommand;
    return ret;
}

#define PACKET_FILTER_ID 0

static wifi_error wifi_set_packet_filter(wifi_interface_handle iface,
                                         const u8 *program, u32 len)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;
    u32 current_offset = 0;
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    /* len=0 clears the filters in driver/firmware */
    if (len != 0 && program == NULL) {
        ALOGE("%s: No valid program provided. Exit.",
            __func__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    do {
        ret = initialize_vendor_cmd(iface, get_requestid(),
                                    QCA_NL80211_VENDOR_SUBCMD_PACKET_FILTER,
                                    &vCommand);
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: Initialization failed", __FUNCTION__);
            return ret;
        }

        /* Add the vendor specific attributes for the NL command. */
        nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
        if (!nlData){
            ret = WIFI_ERROR_UNKNOWN;
            goto cleanup;
        }

        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SUB_CMD,
                                QCA_WLAN_SET_PACKET_FILTER);
        if (ret != WIFI_SUCCESS)
            goto cleanup;
        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_ID,
                                PACKET_FILTER_ID);
        if (ret != WIFI_SUCCESS)
            goto cleanup;
        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SIZE,
                                len);
        if (ret != WIFI_SUCCESS)
            goto cleanup;
        ret = vCommand->put_u32(
                            QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_CURRENT_OFFSET,
                            current_offset);
        if (ret != WIFI_SUCCESS)
            goto cleanup;

        if (len) {
            ret = vCommand->put_bytes(
                                     QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROGRAM,
                                     (char *)&program[current_offset],
                                     min(info->firmware_bus_max_size,
                                     len-current_offset));
            if (ret!= WIFI_SUCCESS) {
                ALOGE("%s: failed to put program", __FUNCTION__);
                goto cleanup;
            }
        }

        vCommand->attr_end(nlData);

        ret = vCommand->requestResponse();
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: requestResponse Error:%d",__func__, ret);
            goto cleanup;
        }

        /* destroy the object after sending each fragment to driver */
        delete vCommand;
        vCommand = NULL;

        current_offset += min(info->firmware_bus_max_size, len);
    } while (current_offset < len);

    info->apf_enabled = !!len;

cleanup:
    if (vCommand)
        delete vCommand;
    return ret;
}

static wifi_error wifi_get_packet_filter_capabilities(
                wifi_interface_handle handle, u32 *version, u32 *max_len)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifihalGeneric *vCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);

    if (version == NULL || max_len == NULL) {
        ALOGE("%s: NULL version/max_len pointer provided. Exit.",
            __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    vCommand = new WifihalGeneric(wifiHandle, 0,
            OUI_QCA,
            QCA_NL80211_VENDOR_SUBCMD_PACKET_FILTER);
    if (vCommand == NULL) {
        ALOGE("%s: Error vCommand NULL", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    /* Create the message */
    ret = vCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SUB_CMD,
                            QCA_WLAN_GET_PACKET_FILTER);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    vCommand->attr_end(nlData);

    ret = vCommand->requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse() error: %d", __FUNCTION__, ret);
        if (ret == WIFI_ERROR_NOT_SUPPORTED) {
            /* Packet filtering is not supported currently, so return version
             * and length as 0
             */
            ALOGI("Packet filtering is not supprted");
            *version = 0;
            *max_len = 0;
            ret = WIFI_SUCCESS;
        }
        goto cleanup;
    }

    *version = vCommand->getFilterVersion();
    *max_len = vCommand->getFilterLength();
cleanup:
    delete vCommand;
    return ret;
}


static wifi_error wifi_configure_nd_offload(wifi_interface_handle iface,
                                            u8 enable)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;

    ret = initialize_vendor_cmd(iface, get_requestid(),
                                QCA_NL80211_VENDOR_SUBCMD_ND_OFFLOAD,
                                &vCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __func__);
        return ret;
    }

    ALOGV("ND offload : %s", enable?"Enable":"Disable");

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    ret = vCommand->put_u8(QCA_WLAN_VENDOR_ATTR_ND_OFFLOAD_FLAG, enable);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    vCommand->attr_end(nlData);

    ret = vCommand->requestResponse();

cleanup:
    delete vCommand;
    return ret;
}

/**
 * Copy 'len' bytes of raw data from host memory at source address 'program'
 * to APF (Android Packet Filter) working memory starting at offset 'dst_offset'.
 * The size of the program lenght passed to the interpreter is set to
 * 'progaram_lenght'
 *
 * The implementation is allowed to tranlate this wrtie into a series of smaller
 * writes,but this function is not allowed to return untill all write operations
 * have been completed
 * additionally visible memory not targeted by this function must remain
 * unchanged

 * @param dst_offset write offset in bytes relative to the beginning of the APF
 * working memory with logical address 0X000. Must be a multiple of 4
 *
 * @param program host memory to copy bytes from. Must be 4B aligned
 *
 * @param len the number of bytes to copy from the bost into the APF working
 * memory
 *
 * @param program_length new length of the program instructions in bytes to pass
 * to the interpreter
 */

wifi_error wifi_write_packet_filter(wifi_interface_handle iface,
                                         u32 dst_offset, const u8 *program,
                                         u32 len, u32 program_length)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;
    u32 current_offset = 0;
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    /* len=0 clears the filters in driver/firmware */
    if (len != 0 && program == NULL) {
        ALOGE("%s: No valid program provided. Exit.",
            __func__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    do {
        ret = initialize_vendor_cmd(iface, get_requestid(),
                                    QCA_NL80211_VENDOR_SUBCMD_PACKET_FILTER,
                                    &vCommand);
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: Initialization failed", __FUNCTION__);
            return ret;
        }

        /* Add the vendor specific attributes for the NL command. */
        nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
        if (!nlData){
             ret = WIFI_ERROR_UNKNOWN;
             goto cleanup;
        }

        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SUB_CMD,
                                 QCA_WLAN_WRITE_PACKET_FILTER);
        if (ret != WIFI_SUCCESS)
            goto cleanup;
        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_ID,
                                PACKET_FILTER_ID);
        if (ret != WIFI_SUCCESS)
            goto cleanup;
        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SIZE,
                                len);
        if (ret != WIFI_SUCCESS)
            goto cleanup;
        ret = vCommand->put_u32(
                            QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_CURRENT_OFFSET,
                            dst_offset + current_offset);
        if (ret != WIFI_SUCCESS)
            goto cleanup;
        ret = vCommand->put_u32(
                           QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROG_LENGTH,
                            program_length);
        if (ret != WIFI_SUCCESS)
            goto cleanup;

        ret = vCommand->put_bytes(
                                 QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_PROGRAM,
                                 (char *)&program[current_offset],
                                 min(info->firmware_bus_max_size,
                                 len - current_offset));
        if (ret!= WIFI_SUCCESS) {
            ALOGE("%s: failed to put program", __FUNCTION__);
            goto cleanup;
        }

        vCommand->attr_end(nlData);

        ret = vCommand->requestResponse();
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: requestResponse Error:%d",__func__, ret);
            goto cleanup;
        }

        /* destroy the object after sending each fragment to driver */
        delete vCommand;
        vCommand = NULL;

        current_offset += min(info->firmware_bus_max_size,
                                         len - current_offset);
    } while (current_offset < len);

cleanup:
    if (vCommand)
        delete vCommand;
    return ret;
}

wifi_error wifi_enable_packet_filter(wifi_interface_handle handle,
                                        u32 enable)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;
    u32 subcmd;
    wifi_handle wifiHandle = getWifiHandle(handle);
    hal_info *info = getHalInfo(wifiHandle);

    ret = initialize_vendor_cmd(handle, get_requestid(),
                                QCA_NL80211_VENDOR_SUBCMD_PACKET_FILTER,
                                &vCommand);

    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __func__);
        return ret;
    }
    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    subcmd = enable ? QCA_WLAN_ENABLE_PACKET_FILTER :
                      QCA_WLAN_DISABLE_PACKET_FILTER;
    ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SUB_CMD,
                            subcmd);
    if (ret != WIFI_SUCCESS)
            goto cleanup;

    vCommand->attr_end(nlData);
    ret = vCommand->requestResponse();

    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse() error: %d", __FUNCTION__, ret);
        goto cleanup;
    }

    info->apf_enabled = !!enable;

cleanup:
    delete vCommand;
    return ret;

}

/**
 * Copy 'length' bytes of raw data from APF (Android Packet Filter) working
 * memory  to host memory starting at offset src_offset into host memory
 * pointed to by host_dst.
 * Memory can be text, data or some combination of the two. The implementiion is
 * allowed to translate this read into a series of smaller reads, but this
 * function is not allowed to return untill all the reads operations
 * into host_dst have been completed.
 *
 * @param src_offset offset in bytes of destination memory within APF working
 * memory
 *
 * @param host_dst host memory to copy into. Must be 4B aligned.
 *
 * @param length the number of bytes to copy from the APF working memory to the
 * host.
 */

static wifi_error wifi_read_packet_filter(wifi_interface_handle handle,
                                          u32 src_offset, u8 *host_dst, u32 length)
{
    wifi_error ret = WIFI_ERROR_UNKNOWN;
    struct nlattr *nlData;
    WifihalGeneric *vCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);
    hal_info *info = getHalInfo(wifiHandle);

    /* Length to be passed to this function should be non-zero
     * Return invalid argument if length is passed as zero
     */
    if (length == 0)
        return  WIFI_ERROR_INVALID_ARGS;

    /*Temporary varibles to support the read complete length in chunks */
    u8 *temp_host_dst;
    u32 remainingLengthToBeRead, currentLength;
    u8 apf_locally_disabled = 0;

    /*Initializing the temporary variables*/
    temp_host_dst = host_dst;
    remainingLengthToBeRead = length;

    if (info->apf_enabled) {
        /* Disable APF only when not disabled by framework before calling
         * wifi_read_packet_filter()
         */
        ret = wifi_enable_packet_filter(handle, 0);
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: Failed to disable APF", __FUNCTION__);
            return ret;
        }
        apf_locally_disabled = 1;
    }
    /**
     * Read the complete length in chunks of size less or equal to firmware bus
     * max size
     */
    while (remainingLengthToBeRead)
    {
        vCommand = new WifihalGeneric(wifiHandle, 0, OUI_QCA,
                                      QCA_NL80211_VENDOR_SUBCMD_PACKET_FILTER);

        if (vCommand == NULL) {
            ALOGE("%s: Error vCommand NULL", __FUNCTION__);
            ret = WIFI_ERROR_OUT_OF_MEMORY;
            break;
        }

        /* Create the message */
        ret = vCommand->create();
        if (ret != WIFI_SUCCESS)
            break;
        ret = vCommand->set_iface_id(ifaceInfo->name);
        if (ret != WIFI_SUCCESS)
            break;
        /* Add the vendor specific attributes for the NL command. */
        nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
        if (!nlData)
            break;
        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SUB_CMD,
                                QCA_WLAN_READ_PACKET_FILTER);
        if (ret != WIFI_SUCCESS)
            break;

        currentLength = min(remainingLengthToBeRead, info->firmware_bus_max_size);

        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_SIZE,
                                currentLength);
        if (ret != WIFI_SUCCESS)
            break;
        ret = vCommand->put_u32(QCA_WLAN_VENDOR_ATTR_PACKET_FILTER_CURRENT_OFFSET,
                                src_offset);
        if (ret != WIFI_SUCCESS)
            break;

        vCommand->setPacketBufferParams(temp_host_dst, currentLength);
        vCommand->attr_end(nlData);
        ret = vCommand->requestResponse();

        if (ret != WIFI_SUCCESS) {
            ALOGE("%s: requestResponse() error: %d current_len = %u, src_offset = %u",
                  __FUNCTION__, ret, currentLength, src_offset);
            break;
        }

        remainingLengthToBeRead -= currentLength;
        temp_host_dst += currentLength;
        src_offset += currentLength;
        delete vCommand;
        vCommand = NULL;
    }

    /* Re enable APF only when disabled above within this API */
    if (apf_locally_disabled) {
        wifi_error status;
        status = wifi_enable_packet_filter(handle, 1);
        if (status != WIFI_SUCCESS)
            ALOGE("%s: Failed to enable APF", __FUNCTION__);
        /* Prefer to return read status if read fails */
        if (ret == WIFI_SUCCESS)
            ret = status;
    }

    delete vCommand;
    return ret;
}

class GetSupportedVendorCmd : public WifiCommand
{
private:
    u32 mVendorCmds[256];
    int mNumOfVendorCmds;

public:
    GetSupportedVendorCmd(wifi_handle handle) : WifiCommand(handle, 0)
    {
        mNumOfVendorCmds = 0;
        memset(mVendorCmds, 0, 256);
    }

    virtual wifi_error create() {
        int nl80211_id = genl_ctrl_resolve(mInfo->cmd_sock, "nl80211");
        wifi_error ret = mMsg.create(nl80211_id, NL80211_CMD_GET_WIPHY, NLM_F_DUMP, 0);
        mMsg.put_flag(NL80211_ATTR_SPLIT_WIPHY_DUMP);

        return ret;
    }

    virtual wifi_error requestResponse() {
        return WifiCommand::requestResponse(mMsg);
    }
    virtual wifi_error set_iface_id(const char* name) {
        unsigned ifindex = if_nametoindex(name);
        return mMsg.set_iface_id(ifindex);
    }

    virtual int handleResponse(WifiEvent& reply) {
        struct nlattr **tb = reply.attributes();

        if (tb[NL80211_ATTR_VENDOR_DATA]) {
            struct nlattr *nl;
            int rem, i = 0;

            for_each_attr(nl, tb[NL80211_ATTR_VENDOR_DATA], rem) {
                struct nl80211_vendor_cmd_info *vinfo;
                if (nla_len(nl) != sizeof(*vinfo)) {
                    ALOGE("Unexpected vendor data info found in attribute");
                    continue;
                }
                vinfo = (struct nl80211_vendor_cmd_info *)nla_data(nl);
                if (vinfo->vendor_id == OUI_QCA) {
                    mVendorCmds[i] = vinfo->subcmd;
                    i++;
                }
            }
            mNumOfVendorCmds = i;
        }
        return NL_SKIP;
    }

    int isVendorCmdSupported(u32 cmdId) {
        int i, ret;

        ret = 0;
        for (i = 0; i < mNumOfVendorCmds; i++) {
            if (cmdId == mVendorCmds[i]) {
                ret = 1;
                break;
            }
        }

        return ret;
    }
};

static int wifi_is_nan_ext_cmd_supported(wifi_interface_handle iface_handle)
{
    wifi_error ret;
    wifi_handle handle = getWifiHandle(iface_handle);
    interface_info *info = getIfaceInfo(iface_handle);
    GetSupportedVendorCmd cmd(handle);

    ret = cmd.create();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: create command failed", __func__);
        return 0;
    }

    ret = cmd.set_iface_id(info->name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: set iface id failed", __func__);
        return 0;
    }

    ret = cmd.requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to query nan_ext command support, ret=%d", ret);
        return 0;
    } else {
        return cmd.isVendorCmdSupported(QCA_NL80211_VENDOR_SUBCMD_NAN_EXT);
    }
}

wifi_error wifi_get_radar_history(wifi_interface_handle handle,
       radar_history_result *resultBuf, int resultBufSize, int *numResults)
{
    wifi_error ret;
    struct nlattr *nlData;
    WifihalGeneric *vCommand = NULL;
    interface_info *ifaceInfo = NULL;
    wifi_handle wifiHandle = NULL;

    ALOGI("%s: enter", __FUNCTION__);

    if (!handle) {
        ALOGE("%s: Error, wifi_interface_handle NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ifaceInfo = getIfaceInfo(handle);
    if (!ifaceInfo) {
        ALOGE("%s: Error, interface_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    wifiHandle = getWifiHandle(handle);
    if (!wifiHandle) {
        ALOGE("%s: Error, wifi_handle NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    if (resultBuf == NULL || numResults == NULL) {
        ALOGE("%s: Error, resultsBuf/numResults NULL pointer", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    vCommand = new WifihalGeneric(wifiHandle, 0,
            OUI_QCA,
            QCA_NL80211_VENDOR_SUBCMD_GET_RADAR_HISTORY);
    if (vCommand == NULL) {
        ALOGE("%s: Error vCommand NULL", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    /* Create the message */
    ret = vCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = vCommand->set_iface_id(ifaceInfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }

    vCommand->attr_end(nlData);

    ret = vCommand->requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse() error: %d", __FUNCTION__, ret);
        goto cleanup;
    }

    /* No more data, copy the parsed results into the caller's results buffer */
    ret = vCommand->copyCachedRadarHistory(
            resultBuf, resultBufSize, numResults);

cleanup:
    vCommand->freeCachedRadarHistory();
    delete vCommand;
    return ret;
}

#define SIZEOF_TLV_HDR 4
#define OEM_DATA_TLV_TYPE_HEADER 1
#define OEM_DATA_CMD_SET_SKIP_CAC   18

struct oem_data_header {
    u16 cmd_id;
    u16 request_idx;
};

static int wifi_add_oem_data_head(int cmd_id, u8* oem_buf, size_t max)
{
    struct oem_data_header oem_hdr;
    oem_hdr.cmd_id = cmd_id;
    oem_hdr.request_idx = 0;

    if ((SIZEOF_TLV_HDR + sizeof(oem_hdr)) > max) {
        return 0;
    }

    wifi_put_le16(oem_buf, OEM_DATA_TLV_TYPE_HEADER);
    oem_buf += 2;
    wifi_put_le16(oem_buf, sizeof(oem_hdr));
    oem_buf += 2;
    memcpy(oem_buf, (u8 *)&oem_hdr, sizeof(oem_hdr));
    oem_buf += sizeof(oem_hdr);

    return (SIZEOF_TLV_HDR + sizeof(oem_hdr));
}


/**
 * This cmd takes effect on the interface the cmd is sent to.
 * This cmd loses effect when interface is down. (i.e. set mac addr)
 */
wifi_error wifi_disable_next_cac(wifi_interface_handle handle) {
    wifi_error ret;
    interface_info *ifaceInfo = NULL;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;
    u8 oem_buf[16];
    int oem_buf_len = 0;

    if (!handle) {
        ALOGE("%s: Error, wifi_interface_handle NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ifaceInfo = getIfaceInfo(handle);
    if (!ifaceInfo) {
        ALOGE("%s: Error, interface_info NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGI("%s: enter - iface=%s", __FUNCTION__, ifaceInfo->name);
    oem_buf_len = wifi_add_oem_data_head(
            OEM_DATA_CMD_SET_SKIP_CAC, oem_buf, sizeof(oem_buf));
    if (oem_buf_len <= 0) {
        ALOGE("%s: fill oem data head failed, cmd=%d", __func__,
                OEM_DATA_CMD_SET_SKIP_CAC);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = initialize_vendor_cmd(handle, get_requestid(),
                                QCA_NL80211_VENDOR_SUBCMD_OEM_DATA,
                                &vCommand);

    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __func__);
        return ret;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto cleanup;
    }

    ret = vCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_OEM_DATA_CMD_DATA,
                              (char *)oem_buf, oem_buf_len);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    vCommand->attr_end(nlData);
    ret = vCommand->requestResponse();

    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse() error: %d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    delete vCommand;
    return ret;
}
