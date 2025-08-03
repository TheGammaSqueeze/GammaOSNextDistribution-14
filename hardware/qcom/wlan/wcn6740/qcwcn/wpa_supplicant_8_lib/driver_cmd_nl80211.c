/*
 * Driver interaction with extended Linux CFG8021
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * Alternatively, this software may be distributed under the terms of BSD
 * license.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *
 *    * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
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

#include "includes.h"
#include <sys/types.h>
#include <fcntl.h>
#include <net/if.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/ctrl.h>
#include <netlink/object-api.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/ctrl.h>
#include <linux/pkt_sched.h>

#include "common.h"
#include "linux_ioctl.h"
#include "driver_nl80211.h"
#include "wpa_supplicant_i.h"
#include "config.h"
#include "wpa_driver_common_lib.h"
#include "ap/hostapd.h"
#include "ap/sta_info.h"
#ifdef LINUX_EMBEDDED
#include <sys/ioctl.h>
#endif
#if defined(ANDROID) || defined(LINUX_EMBEDDED)
#include "android_drv.h"
#endif
#include "driver_cmd_nl80211_extn.h"

#define WPA_PS_ENABLED		0
#define WPA_PS_DISABLED		1
#define UNUSED(x)	(void)(x)
#define NL80211_ATTR_MAX_INTERNAL 256
#define CSI_STATUS_REJECTED      -1
#define CSI_STATUS_SUCCESS        0
#define ENHANCED_CFR_VER          2
#define CSI_GROUP_BITMAP          1
#define CSI_DEFAULT_GROUP_ID      0
#define CSI_FC_STYPE_BEACON       8
#define CSI_MGMT_BEACON           (1<<WLAN_FC_STYPE_BEACON)

#define TWT_SETUP_WAKE_INTVL_MANTISSA_MAX       0xFFFF
#define TWT_SETUP_WAKE_DURATION_MAX             0xFF
#define TWT_SETUP_WAKE_INTVL_EXP_MAX            31
#define TWT_WAKE_INTERVAL_TU_FACTOR		1024

#define TWT_SETUP_STR        "twt_session_setup"
#define TWT_TERMINATE_STR    "twt_session_terminate"
#define TWT_PAUSE_STR        "twt_session_pause"
#define TWT_RESUME_STR       "twt_session_resume"
#define TWT_NUDGE_STR        "twt_session_nudge"
#define TWT_GET_PARAMS_STR   "twt_session_get_params"
#define TWT_GET_STATS_STR    "twt_session_get_stats"
#define TWT_CLEAR_STATS_STR  "twt_session_clear_stats"
#define TWT_GET_CAP_STR      "twt_get_capability"
#define TWT_SET_PARAM_STR    "twt_set_param"

#define TWT_SETUP_STRLEN         strlen(TWT_SETUP_STR)
#define TWT_TERMINATE_STR_LEN    strlen(TWT_TERMINATE_STR)
#define TWT_PAUSE_STR_LEN        strlen(TWT_PAUSE_STR)
#define TWT_RESUME_STR_LEN       strlen(TWT_RESUME_STR)
#define TWT_NUDGE_STR_LEN        strlen(TWT_NUDGE_STR)
#define TWT_GET_PARAMS_STR_LEN   strlen(TWT_GET_PARAMS_STR)
#define TWT_GET_STATS_STR_LEN    strlen(TWT_GET_STATS_STR)
#define TWT_CLEAR_STATS_STR_LEN  strlen(TWT_CLEAR_STATS_STR)
#define TWT_GET_CAP_STR_LEN      strlen(TWT_GET_CAP_STR)
#define TWT_SET_PARAM_STR_LEN    strlen(TWT_SET_PARAM_STR)

#define TWT_CMD_NOT_EXIST -EINVAL
#define DEFAULT_IFNAME "wlan0"
#define TWT_RESP_BUF_LEN 512

#define SINGLE_SPACE_LEN 1
#define SINGLE_DIGIT_LEN 1

#define DIALOG_ID_STR           "dialog_id"
#define REQ_TYPE_STR            "req_type"
#define TRIG_TYPE_STR           "trig_type"
#define FLOW_TYPE_STR           "flow_type"
#define WAKE_INTR_EXP_STR       "wake_intr_exp"
#define PROTECTION_STR          "protection"
#define WAKE_TIME_STR           "wake_time"
#define WAKE_DUR_STR            "wake_dur"
#define WAKE_INTR_MANTISSA_STR  "wake_intr_mantissa"
#define BROADCAST_STR           "broadcast"
#define MIN_WAKE_INTVL_STR      "min_wake_intvl"
#define MAX_WAKE_INTVL_STR      "max_wake_intvl"
#define MIN_WAKE_DUR_STR        "min_wake_duration"
#define MAX_WAKE_DUR_STR        "max_wake_duration"
#define NEXT_TWT_STR            "next_twt"
#define NEXT2_TWT_STR           "next2_twt"
#define NEXT_TWT_SIZE_STR       "next_twt_size"
#define PAUSE_DURATION_STR      "pause_duration"
#define WAKE_TSF_STR            "wake_tsf"
#define ANNOUNCE_TIMEOUT_STR    "announce_timeout"
#define AP_AC_VALUE_STR         "ap_ac_value"
#define MAC_ADDRESS_STR         "mac_addr"

#define DIALOG_ID_STR_LEN               strlen(DIALOG_ID_STR)
#define REQ_TYPE_STR_LEN                strlen(REQ_TYPE_STR)
#define TRIG_TYPE_STR_LEN               strlen(TRIG_TYPE_STR)
#define FLOW_TYPE_STR_LEN               strlen(FLOW_TYPE_STR)
#define WAKE_INTR_EXP_STR_LEN           strlen(WAKE_INTR_EXP_STR)
#define PROTECTION_STR_LEN              strlen(PROTECTION_STR)
#define WAKE_TIME_STR_LEN               strlen(WAKE_TIME_STR)
#define WAKE_DUR_STR_LEN                strlen(WAKE_DUR_STR)
#define WAKE_INTR_MANTISSA_STR_LEN      strlen(WAKE_INTR_MANTISSA_STR)
#define BROADCAST_STR_LEN               strlen(BROADCAST_STR)
#define MIN_WAKE_INTVL_STR_LEN          strlen(MIN_WAKE_INTVL_STR)
#define MAX_WAKE_INTVL_STR_LEN          strlen(MAX_WAKE_INTVL_STR)
#define MIN_WAKE_DUR_STR_LEN            strlen(MIN_WAKE_DUR_STR)
#define MAX_WAKE_DUR_STR_LEN            strlen(MAX_WAKE_DUR_STR)
#define NEXT_TWT_STR_LEN		strlen(NEXT_TWT_STR)
#define NEXT2_TWT_STR_LEN		strlen(NEXT2_TWT_STR)
#define NEXT_TWT_SIZE_STR_LEN		strlen(NEXT_TWT_SIZE_STR)
#define PAUSE_DURATION_STR_LEN          strlen(PAUSE_DURATION_STR)
#define WAKE_TSF_STR_LEN                strlen(WAKE_TSF_STR)
#define ANNOUNCE_TIMEOUT_STR_LEN        strlen(ANNOUNCE_TIMEOUT_STR)
#define AP_AC_VALUE_STR_LEN             strlen(AP_AC_VALUE_STR)
#define MAC_ADDR_STR_LEN             	strlen(MAC_ADDRESS_STR)

#define MAC_ADDR_STR "%02x:%02x:%02x:%02x:%02x:%02x"
#define MAC_ADDR_ARRAY(a) (a)[0], (a)[1], (a)[2], (a)[3], (a)[4], (a)[5]

#define TWT_CTRL_EVENT           "CTRL-EVENT-TWT"
#define TWT_SETUP_RESP           "CTRL-EVENT-TWT SETUP"
#define TWT_TEARDOWN_RESP        "CTRL-EVENT-TWT TERMINATE"
#define TWT_PAUSE_RESP           "CTRL-EVENT-TWT PAUSE"
#define TWT_RESUME_RESP          "CTRL-EVENT-TWT RESUME"
#define TWT_NOTIFY_RESP          "CTRL-EVENT-TWT NOTIFY"
#define TWT_SETUP_RESP_LEN       strlen(TWT_SETUP_RESP)
#define TWT_TEARDOWN_RESP_LEN    strlen(TWT_TEARDOWN_RESP)
#define TWT_PAUSE_RESP_LEN       strlen(TWT_PAUSE_RESP)
#define TWT_RESUME_RESP_LEN      strlen(TWT_RESUME_RESP)
#define TWT_NOTIFY_RESP_LEN      strlen(TWT_NOTIFY_RESP)

static int twt_async_support = -1;

struct twt_setup_parameters {
	u8 dialog_id;
	u8 req_type;
	u8 trig_type;
	u8 flow_type;
	u8 wake_intr_exp;
	u8 protection;
	u32 wake_time;
	u32 wake_dur;
	u32 wake_intr_mantissa;
	u8 bcast;
	u32 min_wake_intvl;
	u32 max_wake_intvl;
	u32 min_wake_duration;
	u32 max_wake_duration;
	u64 wake_tsf;
	u32 announce_timeout_us;
};

struct twt_resume_parameters {
	u8 dialog_id;
	u8 next_twt;
	u32 next2_twt;
	u32 next_twt_size;
};

struct twt_nudge_parameters {
	u8 dialog_id;
	u32 wake_time;
	u32 next_twt_size;
};

struct twt_set_parameters {
	u8 ap_ac_value;
};

struct twt_resp_info {
	char *reply_buf;
	int reply_buf_len;
	enum qca_wlan_twt_operation twt_oper;
	struct wpa_driver_nl80211_data *drv;
};


static int wpa_driver_twt_async_resp_event(struct wpa_driver_nl80211_data *drv,
					   u32 vendor_id, u32 subcmd, u8 *data, size_t len);

/* ============ nl80211 driver extensions ===========  */
enum csi_state {
	CSI_STATE_STOP = 0,
	CSI_STATE_START,
};

struct csi_global_params {
	struct i802_bss *bss;
	enum csi_state current_state;
	char connected_bssid[MAC_ADDR_LEN];
	int transport_mode;
};

static struct csi_global_params g_csi_param = {0};

static wpa_driver_oem_cb_table_t *oem_cb_table = NULL;

#define MCC_QUOTA_MIN 10
#define MCC_QUOTA_MAX 90
/* Only one quota entry for now */
#define MCC_QUOTA_ENTRIES_MAX 1

struct mcc_quota {
       uint32_t if_idx;
       uint32_t quota;
};


static char *get_next_arg(char *cmd)
{
	char *pos = cmd;

	while (*pos != ' ' && *pos != '\0')
		pos++;

	return pos;
}

static int wpa_driver_cmd_set_ani_level(struct i802_bss *bss, int mode, int ofdmlvl)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *msg = NULL;
	struct nlattr *params = NULL;
	int ret = 0;

	if (!(msg = nl80211_drv_msg(drv, 0, NL80211_CMD_VENDOR)) ||
		nla_put_u32(msg, NL80211_ATTR_VENDOR_ID, OUI_QCA) ||
		nla_put_u32(msg, NL80211_ATTR_VENDOR_SUBCMD,
			QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION) ||
		!(params = nla_nest_start(msg, NL80211_ATTR_VENDOR_DATA)) ||
		nla_put_u8(msg, QCA_WLAN_VENDOR_ATTR_CONFIG_ANI_SETTING, mode)) {
			nlmsg_free(msg);
			return -1;
	}
	if(mode == QCA_WLAN_ANI_SETTING_FIXED) {
		if(nla_put(msg, QCA_WLAN_VENDOR_ATTR_CONFIG_ANI_LEVEL, sizeof(int32_t), &ofdmlvl)){
			nlmsg_free(msg);
			return -1;
		}
	}
	nla_nest_end(msg, params);
	ret = send_and_recv_msgs(drv, msg, NULL, NULL, NULL, NULL);
	if (!ret)
		return 0;
	wpa_printf(MSG_ERROR, "%s: Failed set_ani_level, ofdmlvl=%d, ret=%d",
		   __FUNCTION__, ofdmlvl, ret);
	return ret;
}

static int wpa_driver_cmd_set_congestion_report(struct i802_bss *bss, char *cmd)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *msg;
	struct nlattr *params = NULL;
	char *endptr = NULL;
	int ret;
	int enable = -1, threshold = -1, interval = -1;

	wpa_printf(MSG_INFO, "%s enter", __FUNCTION__);

	enable = strtol(cmd, &endptr, 10);
	if (enable != 0 && enable != 1) {
		wpa_printf(MSG_ERROR, "%s: invalid enable arg %d", __FUNCTION__, enable);
		return -EINVAL;
	}

	if (!(msg = nl80211_drv_msg(drv, 0, NL80211_CMD_VENDOR)) ||
		nla_put_u32(msg, NL80211_ATTR_VENDOR_ID, OUI_QCA) ||
		nla_put_u32(msg, NL80211_ATTR_VENDOR_SUBCMD,
		  QCA_NL80211_VENDOR_SUBCMD_MEDIUM_ASSESS) ||
		!(params = nla_nest_start(msg, NL80211_ATTR_VENDOR_DATA)) ||
		nla_put_u8(msg, QCA_WLAN_VENDOR_ATTR_MEDIUM_ASSESS_TYPE,
		  QCA_WLAN_MEDIUM_ASSESS_CONGESTION_REPORT) ||
		nla_put_u8(msg,
		  QCA_WLAN_VENDOR_ATTR_MEDIUM_ASSESS_CONGESTION_REPORT_ENABLE,
		  enable)) {
			nlmsg_free(msg);
			return -1;
	}
	if (enable == 1) {
		if (!(*endptr) ||
		  ((threshold = strtol(endptr, &endptr, 10)) < 0 || threshold > 100) ||
		  !(*endptr) ||
		  ((interval = strtol(endptr, &endptr, 10)) < 1 || interval > 30)) {
			wpa_printf(MSG_ERROR, "%s: args less or invalid", __FUNCTION__);
			nlmsg_free(msg);
			return -EINVAL;
		}
		if (nla_put_u8(msg,
		  QCA_WLAN_VENDOR_ATTR_MEDIUM_ASSESS_CONGESTION_REPORT_THRESHOLD,
		  threshold) || nla_put_u8(msg,
		  QCA_WLAN_VENDOR_ATTR_MEDIUM_ASSESS_CONGESTION_REPORT_INTERVAL,
		  interval)) {
			nlmsg_free(msg);
			return -1;
		}
	}
	nla_nest_end(msg, params);
	ret = send_and_recv_msgs(drv, msg, NULL, NULL, NULL, NULL);
	wpa_printf(MSG_INFO, "%s: set congestion report: enable=%d, threshold=%d,"
			"interval=%d", __FUNCTION__, enable, threshold, interval);
	if (!ret)
		return 0;
	wpa_printf(MSG_ERROR, "%s: Failed set congestion report, ret=%d", __FUNCTION__, ret);
	return ret;
}

static int wpa_driver_cmd_set_tx_power(struct i802_bss *bss, char *cmd)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *msg;
	char *endptr = NULL;
	int ret;
	int dbm, mbm;

	wpa_printf(MSG_INFO, "%s enter: dbm=%s", __FUNCTION__, cmd);

	dbm = strtol(cmd, &endptr, 10);
	if (*endptr || dbm < 0) {
		wpa_printf(MSG_ERROR, "%s: invalid dbm %d", __FUNCTION__, dbm);
		return -EINVAL;
	}
	mbm = dbm * 100;
	if (mbm < 0) { // integer overflow
		wpa_printf(MSG_ERROR, "%s: invalid mbm %d", __FUNCTION__, mbm);
		return -EINVAL;
	}

	if (!(msg = nl80211_drv_msg(drv, 0, NL80211_CMD_SET_WIPHY)) ||
	    nla_put_u32(msg, NL80211_ATTR_WIPHY_TX_POWER_SETTING,
		NL80211_TX_POWER_LIMITED) ||
	    nla_put_u32(msg, NL80211_ATTR_WIPHY_TX_POWER_LEVEL, mbm)) {
		nlmsg_free(msg);
		return -ENOBUFS;
	}

	ret = send_and_recv_msgs(drv, msg, NULL, NULL, NULL, NULL);
	if (!ret)
		return 0;

	wpa_printf(MSG_ERROR, "%s: Failed set_tx_power dbm=%d, ret=%d",
		   __FUNCTION__, dbm, ret);
	return ret;
}

/* Return type for setBand*/
enum {
	SEND_CHANNEL_CHANGE_EVENT = 0,
	DO_NOT_SEND_CHANNEL_CHANGE_EVENT,
};

typedef struct android_wifi_priv_cmd {
	char *buf;
	int used_len;
	int total_len;
} android_wifi_priv_cmd;

static int drv_errors = 0;

static void wpa_driver_notify_country_change(void *ctx, char *cmd)
{
	if ((os_strncasecmp(cmd, "COUNTRY", 7) == 0) ||
	    (os_strncasecmp(cmd, "SETBAND", 7) == 0)) {
		union wpa_event_data event;

		os_memset(&event, 0, sizeof(event));
		event.channel_list_changed.initiator = REGDOM_SET_BY_USER;
		if (os_strncasecmp(cmd, "COUNTRY", 7) == 0) {
			event.channel_list_changed.type = REGDOM_TYPE_COUNTRY;
			if (os_strlen(cmd) > 9) {
				event.channel_list_changed.alpha2[0] = cmd[8];
				event.channel_list_changed.alpha2[1] = cmd[9];
			}
		} else {
			event.channel_list_changed.type = REGDOM_TYPE_UNKNOWN;
		}
		wpa_supplicant_event(ctx, EVENT_CHANNEL_LIST_CHANGED, &event);
	}
}

static struct remote_sta_info g_sta_info = {0};
static struct bss_info g_bss_info = {0};

static struct nl_msg *prepare_nlmsg(struct wpa_driver_nl80211_data *drv,
				    char *ifname, int cmdid, int subcmd,
				    int flag)
{
	int res;
	struct nl_msg *nlmsg = nlmsg_alloc();
	int ifindex;

	if (nlmsg == NULL) {
		wpa_printf(MSG_ERROR,"Out of memory");
		return NULL;
	}

	genlmsg_put(nlmsg, /* pid = */ 0, /* seq = */ 0,
		    drv->global->nl80211_id, 0, flag, cmdid, 0);

	if (cmdid == NL80211_CMD_VENDOR) {
		res = nla_put_u32(nlmsg, NL80211_ATTR_VENDOR_ID, OUI_QCA);
		if (res < 0) {
			wpa_printf(MSG_ERROR,"Failed to put vendor id");
			goto cleanup;
		}

		res = nla_put_u32(nlmsg, NL80211_ATTR_VENDOR_SUBCMD, subcmd);
		if (res < 0) {
			wpa_printf(MSG_ERROR,"Failed to put vendor sub command");
			goto cleanup;
		}
	}

	if (ifname && (strlen(ifname) > 0))
		ifindex = if_nametoindex(ifname);
	else
		ifindex = if_nametoindex(DEFAULT_IFNAME);

	if (nla_put_u32(nlmsg, NL80211_ATTR_IFINDEX, ifindex) != 0) {
		wpa_printf(MSG_ERROR,"Failed to get iface index for iface: %s", ifname);
		goto cleanup;
	}

	return nlmsg;

cleanup:
	if (nlmsg)
		nlmsg_free(nlmsg);
	return NULL;
}

static struct nl_msg *prepare_vendor_nlmsg(struct wpa_driver_nl80211_data *drv,
					   char *ifname, int subcmd)
{
	return prepare_nlmsg(drv, ifname, NL80211_CMD_VENDOR, subcmd, 0);
}

static int parse_station_info(struct resp_info *info, struct nlattr *vendata,
			      int datalen)
{
	struct nlattr *tb_vendor[GET_STATION_INFO_MAX + 1];
	struct nlattr *attr, *attr1, *attr2;
	u8 *beacon_ies = NULL;
	size_t beacon_ies_len = 0;
	u8 seg1;

	g_bss_info.oui[0] = (OUI_QCA) & 0xFF;
	g_bss_info.oui[1] = ((OUI_QCA)>>8) & 0xFF;
	g_bss_info.oui[2] = ((OUI_QCA)>>16) & 0xFF;

	nla_parse(tb_vendor, GET_STATION_INFO_MAX,
		  vendata, datalen, NULL);

	attr = tb_vendor[QCA_WLAN_VENDOR_ATTR_GET_STATION_LINK_INFO_ATTR];
	if (attr) {
		struct nlattr *tb1[NL80211_ATTR_MAX + 1];

		nla_parse(tb1, NL80211_ATTR_MAX, nla_data(attr),
			  nla_len(attr), NULL);
		if (tb1[NL80211_ATTR_SSID] &&
		    (nla_len(tb1[NL80211_ATTR_SSID]) <= MAX_SSID_LEN)) {
			os_memcpy(g_bss_info.ssid, nla_data(tb1[NL80211_ATTR_SSID]),
					  nla_len(tb1[NL80211_ATTR_SSID]));
			g_bss_info.ssid[nla_len(tb1[NL80211_ATTR_SSID])] = '\0';
		} else {
			wpa_printf(MSG_ERROR,"NL80211_ATTR_SSID not found");
		}
		if (tb1[NL80211_ATTR_MAC]) {
			os_memcpy(g_bss_info.oui, nla_data(tb1[NL80211_ATTR_MAC]), OUI_LEN);
		} else {
			wpa_printf(MSG_ERROR,"NL80211_ATTR_MAC not found");
		}
		if (tb1[NL80211_ATTR_SURVEY_INFO]) {
			struct nlattr *tb2[NL80211_SURVEY_INFO_MAX + 1];

			nla_parse(tb2, NL80211_SURVEY_INFO_MAX,
				  nla_data(tb1[NL80211_ATTR_SURVEY_INFO]),
				  nla_len(tb1[NL80211_ATTR_SURVEY_INFO]), NULL);
			if (tb2[NL80211_SURVEY_INFO_FREQUENCY]) {
				g_bss_info.channel =
				nla_get_u32(tb2[NL80211_SURVEY_INFO_FREQUENCY]);
			} else {
				wpa_printf(MSG_ERROR,
				    "NL80211_SURVEY_INFO_FREQUENCY not found");
			}
			if (tb2[NL80211_SURVEY_INFO_NOISE]) {
				g_bss_info.noise =
				nla_get_u8(tb2[NL80211_SURVEY_INFO_NOISE]);
				g_bss_info.noise -= 100;
			} else {
				wpa_printf(MSG_ERROR,"NL80211_SURVEY_INFO_NOISE not found");
			}
		} else {
			wpa_printf(MSG_ERROR,"NL80211_ATTR_SURVEY_INFO not found");
		}

		if (tb1[NL80211_ATTR_STA_INFO]) {
			struct nlattr *tb2[NL80211_STA_INFO_MAX + 1];

			nla_parse(tb2, NL80211_STA_INFO_MAX,
				  nla_data(tb1[NL80211_ATTR_STA_INFO]),
				  nla_len(tb1[NL80211_ATTR_STA_INFO]), NULL);
			if (tb2[NL80211_STA_INFO_SIGNAL]) {
				g_bss_info.rssi =
				nla_get_u8(tb2[NL80211_STA_INFO_SIGNAL]);
				g_bss_info.rssi -= 100;
			} else {
				wpa_printf(MSG_ERROR,"NL80211_STA_INFO_SIGNAL not found");
			}
			g_bss_info.snr = g_bss_info.rssi - g_bss_info.noise;

			attr1 = tb2[NL80211_STA_INFO_TX_BITRATE];
			if (attr1) {
				struct nlattr *tb3[NL80211_RATE_INFO_MAX + 1];

				nla_parse(tb3, NL80211_RATE_INFO_MAX,
					  nla_data(attr1), nla_len(attr1),
					  NULL);
				if (tb3[NL80211_RATE_INFO_BITRATE32]) {
					g_bss_info.data_rate = nla_get_u32(
					tb3[NL80211_RATE_INFO_BITRATE32])/10;
				} else if (tb3[NL80211_RATE_INFO_BITRATE]) {
					g_bss_info.data_rate = nla_get_u16(
					tb3[NL80211_RATE_INFO_BITRATE])/10;
				}

			} else {
				wpa_printf(MSG_ERROR,"NL80211_STA_INFO_TX_BITRATE not found");
			}
		} else {
			wpa_printf(MSG_ERROR,"NL80211_ATTR_STA_INFO not found");
		}
	} else {
		wpa_printf(MSG_ERROR,
		   "QCA_WLAN_VENDOR_ATTR_GET_STATION_LINK_INFO_ATTR not found");
	}

	if (tb_vendor[GET_STATION_INFO_AKM]) {
		g_bss_info.akm = nla_get_u32(
			tb_vendor[GET_STATION_INFO_AKM]);
	}

	if (tb_vendor[QCA_WLAN_VENDOR_ATTR_802_11_MODE])
		g_bss_info.mode_80211 = nla_get_u32(
			tb_vendor[QCA_WLAN_VENDOR_ATTR_802_11_MODE]);

	attr = tb_vendor[GET_STATION_INFO_VHT_OPERATION];
	attr1 = tb_vendor[GET_STATION_INFO_HT_OPERATION];
	attr2 = tb_vendor[GET_STATION_INFO_HE_OPERATION];
	if (attr) {
		struct ieee80211_vht_operation *info = nla_data(attr);

		switch (info->vht_op_info_chwidth) {
		case CHANWIDTH_USE_HT:
			if (attr1) {
				struct ieee80211_ht_operation *info;

				info = nla_data(attr1);
				g_bss_info.bw = info->ht_param ? 40:20;
			}
			break;
		case CHANWIDTH_80MHZ:
			seg1 = info->vht_op_info_chan_center_freq_seg1_idx;
			if (seg1)
				/* Notifying 80P80 also as bandwidth = 160 */
				g_bss_info.bw = 160;
			else
				g_bss_info.bw = 80;
			break;
		case CHANWIDTH_160MHZ:
			g_bss_info.bw = 160;
			break;
		case CHANWIDTH_80P80MHZ:
			g_bss_info.bw = 160;
			break;
		default:
			wpa_printf(MSG_ERROR,"Invalid channel width received : %u",
						 info->vht_op_info_chwidth);
		}
	} else if (attr1) {
		struct ieee80211_ht_operation *info = nla_data(attr1);

		g_bss_info.bw = info->ht_param ? 40:20;
	} else
		g_bss_info.bw = 20;

	if (attr2) {
		struct ieee80211_he_operation *he_info = nla_data(attr2);
		uint8_t *opr, ch_bw = CHANNEL_BW_INVALID;

		/* Check optional field in he_info is present of not */
		if (!he_info || (nla_len(attr2) <=
		    (sizeof(he_info->he_oper_params) +
		    sizeof(he_info->he_mcs_nss_set)))) {
			he_info ? wpa_printf(MSG_ERROR,"Invalid he operation len: %d", nla_len(attr2)):
			wpa_printf(MSG_ERROR,"Invalid he_info: NULL");
			goto parse_beacon_ies;
		}

		opr = (uint8_t *)he_info;
		/* Point to operational field */
		opr += (sizeof(he_info->he_oper_params) +
			sizeof(he_info->he_mcs_nss_set));
		if (he_info->he_oper_params &
		    IEEE80211_HE_OPERATION_VHT_OPER_MASK) {
			ch_bw = opr[HE_OPER_VHT_CH_WIDTH_OFFSET];
			switch (ch_bw) {
			case CHANWIDTH_USE_HT:
				/* TO DO */
				break;
			case CHANWIDTH_80MHZ:
				seg1 = opr[HE_OPER_VHT_CENTER_FRQ_SEG1_OFFSET];
				if (seg1)
					/* Notifying 80P80 also as bandwidth = 160 */
					g_bss_info.bw = 160;
				else
					g_bss_info.bw = 80;
				break;
			case CHANWIDTH_160MHZ:
				g_bss_info.bw = 160;
				break;
			case CHANWIDTH_80P80MHZ:
				g_bss_info.bw = 160;
				break;
			default:
				break;
			}
			opr += (HE_OPER_VHT_MAX_OFFSET + 1);
		}

		if (he_info->he_oper_params &
		    IEEE80211_HE_OPERATION_CO_LOC_BSS_MASK) {
			opr += (HE_OPER_CO_LOCATED_MAX_OFFSET + 1);
		}

		if (he_info->he_oper_params &
		    IEEE80211_HE_OPERATION_6G_OPER_MASK) {
			ch_bw = (opr[HE_OPER_6G_PARAMS_OFFSET] &
				 HE_OPER_6G_PARAMS_SUB_CH_BW_MASK);
			switch (ch_bw) {
			case HE_CHANWIDTH_20MHZ:
				g_bss_info.bw = 20;
				break;
			case HE_CHANWIDTH_40MHZ:
				g_bss_info.bw = 40;
				break;
			case HE_CHANWIDTH_80MHZ:
				g_bss_info.bw = 80;
				break;
			case HE_CHANWIDTH_160MHZ:
				/* Notifying 80P80 also as bandwidth = 160 */
				g_bss_info.bw = 160;
				break;
			default:
				wpa_printf(MSG_ERROR,"Invalid channel width received : %u", ch_bw);
			}
		}

	}

parse_beacon_ies:
	attr = tb_vendor[GET_STATION_INFO_BEACON_IES];
	if (attr) {
		beacon_ies = nla_data(attr);

		beacon_ies_len = nla_len(attr);
		if (beacon_ies && beacon_ies_len > 12) {
			beacon_ies += 12;
			beacon_ies_len -= 12;
		}
	}

	if (tb_vendor[GET_STATION_INFO_DRIVER_DISCONNECT_REASON]) {
		g_bss_info.disc_reasn_code = nla_get_u32(tb_vendor[
			GET_STATION_INFO_DRIVER_DISCONNECT_REASON]);
	}
	snprintf(info->reply_buf, info->reply_buf_len,
		 "%02x%02x%02x %s %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %u %s %d %d %d %d %d %d %d %d %d %d %d %d %d",
		 g_bss_info.oui[0],
		 g_bss_info.oui[1],
		 g_bss_info.oui[2],
		 g_bss_info.ssid,
		 g_bss_info.channel,
		 g_bss_info.bw,
		 g_bss_info.rssi,
		 g_bss_info.data_rate,
		 g_bss_info.mode_80211,
		 -1,
		 -1,
		 -1,
		 g_bss_info.snr,
		 g_bss_info.noise,
		 g_bss_info.akm,
		 g_bss_info.roaming_count,
		 -1,
		 -1,
		 -1,
		 -1,
		 g_bss_info.disc_reasn_code,
		 info->country,
		 g_bss_info.ani_level,
		 -1,
		 -1,
		 -1,
		 g_bss_info.roam_trigger_reason,
		 g_bss_info.roam_fail_reason,
		 g_bss_info.roam_invoke_fail_reason,
		 g_bss_info.tsf_out_of_sync_count,
		 g_bss_info.latest_tx_power,
		 g_bss_info.latest_tx_rate,
		 g_bss_info.target_power_24g_1mbps,
		 g_bss_info.target_power_24g_6mbps,
		 g_bss_info.target_power_5g_6mbps);

	return 0;
}

static int parse_get_feature_info(struct resp_info *info, struct nlattr *vendata,
				  int datalen)
{
	struct nlattr *tb_vendor[NUM_QCA_WLAN_VENDOR_FEATURES + 1];
	struct nlattr *attr;
	nla_parse(tb_vendor, NUM_QCA_WLAN_VENDOR_FEATURES,
		  vendata, datalen, NULL);
	attr = tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_FLAGS];
	if (attr) {
		snprintf(info->reply_buf, info->reply_buf_len, "%u",
			 nla_get_u32(attr));
		wpa_printf(MSG_DEBUG, "%s: driver supported feature info  = %s",
			   __func__, info->reply_buf);
	} else {
		snprintf(info->reply_buf, info->reply_buf_len, "FAIL");
		return -1;
	}
	return 0;
}
static int handle_response(struct resp_info *info, struct nlattr *vendata,
			   int datalen)
{
	switch (info->subcmd) {
	case QCA_NL80211_VENDOR_SUBCMD_GET_STATION:
		os_memset(info->reply_buf, 0, info->reply_buf_len);
		if (info->cmd_type == GETSTATSBSSINFO)
			parse_station_info(info, vendata, datalen);

		wpa_printf(MSG_INFO,"STAINFO: %s", info->reply_buf);
		break;
	case QCA_NL80211_VENDOR_SUBCMD_GET_FEATURES:
		os_memset(info->reply_buf, 0, info->reply_buf_len);
		parse_get_feature_info(info, vendata, datalen);
		break;
	default:
		wpa_printf(MSG_ERROR,"Unsupported response type: %d", info->subcmd);
		break;
	}
	return 0;
}

static int response_handler(struct nl_msg *msg, void *arg)
{
	struct genlmsghdr *mHeader;
	struct nlattr *mAttributes[NL80211_ATTR_MAX_INTERNAL + 1];
	struct nlattr *vendata;
	int datalen;
	struct resp_info *info = (struct resp_info *) arg;
	int status;

	mHeader = (struct genlmsghdr *)nlmsg_data(nlmsg_hdr(msg));
	nla_parse(mAttributes, NL80211_ATTR_MAX_INTERNAL,
			  genlmsg_attrdata(mHeader, 0),
			  genlmsg_attrlen(mHeader, 0), NULL);

	if (mAttributes[NL80211_ATTR_VENDOR_DATA]) {
		vendata = nla_data(mAttributes[NL80211_ATTR_VENDOR_DATA]);
		datalen = nla_len(mAttributes[NL80211_ATTR_VENDOR_DATA]);
		if (!vendata) {
			wpa_printf(MSG_ERROR,"Vendor data not found");
			return -1;
		}
		status = handle_response(info, vendata, datalen);
	} else {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_VENDOR_DATA not found");
		status = -1;
	}

	return status;
}

static int ack_handler(struct nl_msg *msg, void *arg)
{
	int *err = (int *)arg;

	*err = 0;
	return NL_STOP;
}

int wpa_driver_nl80211_oem_event(struct wpa_driver_nl80211_data *drv,
					   u32 vendor_id, u32 subcmd,
					   u8 *data, size_t len)
{
	int ret = -1, lib_n;
	if (wpa_driver_oem_initialize(&oem_cb_table) != WPA_DRIVER_OEM_STATUS_FAILURE &&
	    oem_cb_table) {
		for (lib_n = 0;
		     oem_cb_table[lib_n].wpa_driver_driver_cmd_oem_cb != NULL;
		     lib_n++) {
			if(oem_cb_table[lib_n].wpa_driver_nl80211_driver_oem_event) {
				ret = oem_cb_table[lib_n].wpa_driver_nl80211_driver_oem_event(
						drv, vendor_id,subcmd, data, len);
				if (ret == WPA_DRIVER_OEM_STATUS_SUCCESS ) {
					break;
				}  else if (ret == WPA_DRIVER_OEM_STATUS_ENOSUPP) {
					continue;
				}  else if (ret == WPA_DRIVER_OEM_STATUS_FAILURE) {
					wpa_printf(MSG_DEBUG, "%s: Received error: %d",
						__func__, ret);
					break;
				}
			}
		}
	}

	return ret;
}

static int wpa_driver_restart_csi(struct i802_bss *bss, int *status);

int wpa_driver_nl80211_driver_event(struct wpa_driver_nl80211_data *drv,
					   u32 vendor_id, u32 subcmd,
					   u8 *data, size_t len)
{
	int ret = -1;
	int status = -1;
	struct i802_bss *bss;

	ret = wpa_driver_nl80211_oem_event(drv, vendor_id, subcmd,
			data, len);

	if(ret != WPA_DRIVER_OEM_STATUS_ENOSUPP)
		return ret;

	switch(subcmd) {
		case QCA_NL80211_VENDOR_SUBCMD_CONFIG_TWT:
			ret = wpa_driver_twt_async_resp_event(drv, vendor_id, subcmd,
							      data, len);
			break;
		case QCA_NL80211_VENDOR_SUBCMD_KEY_MGMT_ROAM_AUTH:
			if(g_csi_param.current_state == CSI_STATE_START) {
				bss = get_bss_ifindex(drv, drv->ifindex);
				if(bss == NULL) {
					wpa_printf(MSG_DEBUG, "%s: bss is NULL",
							__func__);
					break;
				}
				if(wpa_driver_restart_csi(bss, &status))
					wpa_printf(MSG_DEBUG, "csi_restart failed %d",
						   status);
			}
			break;
		default:
			break;
	}
	return ret;
}

static int finish_handler(struct nl_msg *msg, void *arg)
{
	int *ret = (int *)arg;

	*ret = 0;
	return NL_SKIP;
}


static int error_handler(struct sockaddr_nl *nla, struct nlmsgerr *err,
						 void *arg)
{
	int *ret = (int *)arg;

	*ret = err->error;
	wpa_printf(MSG_ERROR,"%s received : %d - %s", __func__,
	      err->error, strerror(err->error));
	return NL_SKIP;
}


static int no_seq_check(struct nl_msg *msg, void *arg)
{
	return NL_OK;
}

static int send_nlmsg(struct nl_sock *cmd_sock, struct nl_msg *nlmsg,
		      nl_recvmsg_msg_cb_t customer_cb, void *arg)
{
	int err = 0;
	struct nl_cb *cb = nl_cb_alloc(NL_CB_DEFAULT);

	if (!cb)
		goto out;

	err = nl_send_auto_complete(cmd_sock, nlmsg);	/* send message */
	if (err < 0)
		goto out;

	err = 1;

	nl_cb_set(cb, NL_CB_SEQ_CHECK, NL_CB_CUSTOM, no_seq_check, NULL);
	nl_cb_err(cb, NL_CB_CUSTOM, error_handler, &err);
	nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler, &err);
	nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler, &err);
	if (customer_cb)
		nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM, customer_cb, arg);

	while (err > 0) {				   /* wait for reply */
		int res = nl_recvmsgs(cmd_sock, cb);

		if (res)
			wpa_printf(MSG_ERROR,"nl80211: %s->nl_recvmsgs failed: %d",
				 __func__, res);
	}
out:
	nl_cb_put(cb);
	if (nlmsg)
		nlmsg_free(nlmsg);
	return err;
}

static int chartohex(char c)
{
	int val = -1;

	if (c >= '0' && c <= '9')
		val = c - '0';
	else if (c >= 'a' && c <= 'f')
		val = c - 'a' + 10;
	else if (c >= 'A' && c <= 'F')
		val = c - 'A' + 10;

	return val;
}

static int convert_string_to_bytes(u8 *addr, const char *text, u16 max_bytes)
{
	u16 i = 0;
	int nibble;
	const char *temp = text;

	while (temp && *temp != '\0' && i < max_bytes) {
		nibble = chartohex(*temp++);
		if (nibble == -1)
			return -1;
		addr[i] = nibble << 4;
		nibble = chartohex(*temp++);
		if (nibble == -1)
			return -1;
		addr[i++] += nibble;
		if (*temp == ':')
			temp++;
	}

	return i;
}

/*
 * Client can send the cell switch mode in below format
 *
 * SETCELLSWITCHMODE <cs mode>
 *
 * examples:
 * For Default Mode   - "SETCELLSWITCHMODE 0"
 * To Disable Roaming - "SETCELLSWITCHMODE 1"
 * For Partial Scan   - "SETCELLSWITCHMODE 2"
 */
static int parse_and_populate_setcellswitchmode(struct nl_msg *nlmsg,
						    char *cmd)
{
	uint32_t all_trigger_bitmap, scan_scheme_bitmap;
	uint32_t cellswm;
	struct nlattr *config;

	cellswm = atoi(cmd);
	if (cellswm < 0 || cellswm > 2) {
		wpa_printf(MSG_ERROR,"Invalid cell switch mode: %d", cellswm);
		return -1;
	}
	wpa_printf(MSG_DEBUG, "cell switch mode: %d", cellswm);

	all_trigger_bitmap = QCA_ROAM_TRIGGER_REASON_PER |
			     QCA_ROAM_TRIGGER_REASON_BEACON_MISS |
			     QCA_ROAM_TRIGGER_REASON_POOR_RSSI |
			     QCA_ROAM_TRIGGER_REASON_BETTER_RSSI |
			     QCA_ROAM_TRIGGER_REASON_PERIODIC |
			     QCA_ROAM_TRIGGER_REASON_DENSE |
			     QCA_ROAM_TRIGGER_REASON_BTM |
			     QCA_ROAM_TRIGGER_REASON_BSS_LOAD |
			     QCA_ROAM_TRIGGER_REASON_USER_TRIGGER |
			     QCA_ROAM_TRIGGER_REASON_DEAUTH |
			     QCA_ROAM_TRIGGER_REASON_IDLE |
			     QCA_ROAM_TRIGGER_REASON_TX_FAILURES |
			     QCA_ROAM_TRIGGER_REASON_EXTERNAL_SCAN;

	scan_scheme_bitmap = QCA_ROAM_TRIGGER_REASON_PER |
			     QCA_ROAM_TRIGGER_REASON_BEACON_MISS |
			     QCA_ROAM_TRIGGER_REASON_POOR_RSSI |
			     QCA_ROAM_TRIGGER_REASON_BSS_LOAD |
			     QCA_ROAM_TRIGGER_REASON_BTM;

	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_ROAMING_SUBCMD,
		QCA_WLAN_VENDOR_ROAMING_SUBCMD_CONTROL_SET) ||
	    nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_ROAMING_REQ_ID, 1)) {
		wpa_printf(MSG_ERROR,"Failed to put: roam_subcmd/REQ_ID");
	}

	config = nla_nest_start(nlmsg,
			QCA_WLAN_VENDOR_ATTR_ROAMING_PARAM_CONTROL);
	if (config == NULL)
		goto fail;

	switch (cellswm){
	case 0:
		if (nla_put_u32(nlmsg, QCA_ATTR_ROAM_CONTROL_TRIGGERS, all_trigger_bitmap)) {
			wpa_printf(MSG_ERROR,"Failed to set: ROAM_CONTROL_TRIGGERS");
			goto fail;
		}
		break;
	case 1:
		if (nla_put_u32(nlmsg, QCA_ATTR_ROAM_CONTROL_TRIGGERS, 0)) {
			wpa_printf(MSG_ERROR,"Failed to unset: ROAM_CONTROL_TRIGGERS");
			goto fail;
		}
		break;
	case 2:
		if (nla_put_u32(nlmsg, QCA_ATTR_ROAM_CONTROL_TRIGGERS, all_trigger_bitmap) ||
		    nla_put_u32(nlmsg, QCA_ATTR_ROAM_CONTROL_SCAN_SCHEME_TRIGGERS, scan_scheme_bitmap)) {
			wpa_printf(MSG_ERROR,"Failed to set: ROAM_CONTROL_TRIGGERS_SCAN_SCHEME");
			goto fail;
		}
		break;
	}
	nla_nest_end(nlmsg, config);

	return 0;
fail:
	return -1;

}

static int populate_nlmsg(struct nl_msg *nlmsg, char *cmd,
			  enum get_info_cmd type)
{
	struct nlattr *attr;

	attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (attr == NULL)
		return -1;

	switch (type) {
	case GETSTATSBSSINFO:
		if (nla_put_flag(nlmsg,
				 QCA_WLAN_VENDOR_ATTR_GET_STATION_INFO)) {
			wpa_printf(MSG_ERROR,"Failed to put flag QCA_WLAN_VENDOR_ATTR_GET_STATION_INFO");
			return -1;
		}
		break;
	case SETCELLSWITCHMODE:
		if (parse_and_populate_setcellswitchmode(nlmsg, cmd) != 0) {
			wpa_printf(MSG_ERROR, "Failed to populate nlmsg");
			return -1;
		}
		break;
	default:
		wpa_printf(MSG_ERROR,"Unsupported command: %d", type);
		break;
	}

	nla_nest_end(nlmsg, attr);
	return 0;
}

static char *skip_white_space(char *cmd)
{
	char *pos = cmd;

	while (*pos == ' ')
		pos++;

	return pos;
}

void ap_sta_copy_supp_op_classes(const u8 *supp_op_classes,
				 size_t supp_op_classes_len) {
	if (!supp_op_classes)
		return;
	os_free(g_sta_info.supp_op_classes);
	g_sta_info.supp_op_classes = malloc(1 + supp_op_classes_len);
	if(!g_sta_info.supp_op_classes)
		return;
	g_sta_info.supp_op_classes[0] = supp_op_classes_len;
	os_memcpy(g_sta_info.supp_op_classes + 1, supp_op_classes,
		  supp_op_classes_len);
}

void ap_sta_copy_channels(const u8 *supp_channels,
				 size_t supp_channels_len) {
	if (!supp_channels)
		return;
	os_free(g_sta_info.supp_channels);
	g_sta_info.supp_channels = malloc(1 + supp_channels_len);
	if(!g_sta_info.supp_channels)
		return;
	g_sta_info.supp_channels[0] = supp_channels_len;
	os_memcpy(g_sta_info.supp_channels + 1, supp_channels,
		  supp_channels_len);
}

static void parse_ext_ie(const u8 *ie, int ie_len)
{
	u8 ext_id;

	if (ie_len < 1) {
		wpa_printf(MSG_ERROR,"parse error, ie_len = %d", ie_len);
		return;
	}

	ext_id = *ie++;
	ie_len--;

	switch (ext_id) {
	case WLAN_EID_EXT_HE_CAPABILITIES:
		wpa_printf(MSG_INFO,"HE supported");
		g_sta_info.flags.he_supported = 1;
		break;
	default:
		wpa_printf(MSG_DEBUG,"ext_id = %d", ext_id);
		break;
	}

	return;
}

static void parse_assoc_req_ies(const u8 *ies, int ies_len)
{
	int left = ies_len;
	const u8 *pos = ies;

	while (left >= 2) {
		u8 id, ie_len;
		id = *pos++;
		ie_len = *pos++;
		left -= 2;

		if (ie_len > left) {
			wpa_printf(MSG_ERROR,"parse error, id = %d, ie_len = %d, left = %d",
			      id, ie_len, left);
			return;
		}

		switch (id) {
		case WLAN_EID_SUPPORTED_OPERATING_CLASSES:
			ap_sta_copy_supp_op_classes(pos, ie_len);
			break;
		case WLAN_EID_SUPPORTED_CHANNELS:
			ap_sta_copy_channels(pos, ie_len);
			break;
		case WLAN_EID_HT_CAP:
			wpa_printf(MSG_INFO,"HT supported");
			g_sta_info.flags.ht_supported = 1;
			break;
		case WLAN_EID_VHT_CAP:
			wpa_printf(MSG_INFO,"VHT supported");
			g_sta_info.flags.vht_supported = 1;
			break;
		case WLAN_EID_EXTENSION:
			parse_ext_ie(pos, ie_len);
			break;
		default:
			break;
		}

		left -= ie_len;
		pos += ie_len;
	}

	if (left)
		wpa_printf(MSG_ERROR,"parse error, left = %d", left);
}

void op_class_band_conversion(u8 *op_classes) {
	int count = (g_sta_info.supp_op_classes[0]);
	int i = 1;
	int temp;

	if (count <= 1)
		g_sta_info.supported_band = 0;
	while((count-1) != 0) {
		temp = g_sta_info.supp_op_classes[i];
		if (temp >= 81 && temp <= 84)
			g_sta_info.supported_band |= BIT(0);
		else if (temp >= 115 && temp <= 130)
			g_sta_info.supported_band |= BIT(1);
		else if (temp >= 131 && temp <= 135)
			g_sta_info.supported_band |= BIT(2);
		i++;
		count--;
	}
}

void supp_channels_band_conversion(u8 *supp_channels) {
	int count = 0;
	int i = 1;
	int temp = 0;

	count = (g_sta_info.supp_channels[0]);
	if (count < 2)
		g_sta_info.supported_band = 0;

	while((count-1) >= 0) {
		temp = g_sta_info.supp_channels[i];
		if (temp >= 1 && temp <= 13)
			g_sta_info.supported_band |= BIT(0);
		else if (temp >= 32 && temp <= 173)
			g_sta_info.supported_band |= BIT(1);
		i += 2;
		count -= 2;
	}
}

static int fill_sta_info(struct remote_sta_info *sta_info,
			   char *buf, size_t buf_len)
{
	int ret;
	if (sta_info->num_sta == 1) {
		if (sta_info->show_band)
			ret = snprintf(buf, buf_len,
				 "%02x:%02x:%02x:%02x:%02x:%02x %d %d %04x %02x:%02x:%02x %d %d %d %d %d %d %d %d %d %s %d %d %d %d %d %d %d %d %d %d %d %d %d",
				 sta_info->mac_addr[0], sta_info->mac_addr[1],
				 sta_info->mac_addr[2], sta_info->mac_addr[3],
				 sta_info->mac_addr[4], sta_info->mac_addr[5],
				 sta_info->rx_retry_pkts, sta_info->rx_bcmc_pkts,
				 sta_info->cap, sta_info->mac_addr[0],
				 sta_info->mac_addr[1], sta_info->mac_addr[2],
				 sta_info->freq,
				 sta_info->bandwidth,
				 sta_info->rssi,
				 sta_info->data_rate,
				 sta_info->dot11_mode,
				 -1,
				 -1,
				 sta_info->reason,
				 sta_info->supported_mode,
				 sta_info->country,
				 sta_info->ani_level,
				 -1,
				 -1,
				 -1,
				 sta_info->roam_trigger_reason,
				 sta_info->roam_fail_reason,
				 sta_info->roam_invoke_fail_reason,
				 sta_info->tsf_out_of_sync_count,
				 sta_info->latest_tx_power,
				 sta_info->latest_tx_rate,
				 sta_info->target_power_24g_1mbps,
				 sta_info->target_power_24g_6mbps,
				 sta_info->target_power_5g_6mbps);
		else
			ret = snprintf(buf, buf_len,
				 "%02x:%02x:%02x:%02x:%02x:%02x %d %d %04x %02x:%02x:%02x %d %d %d %d %d %d %d %d %u %s %d %d %d %d %d %d %d %d %d %d %d %d %d",
				 sta_info->mac_addr[0], sta_info->mac_addr[1],
				 sta_info->mac_addr[2], sta_info->mac_addr[3],
				 sta_info->mac_addr[4], sta_info->mac_addr[5],
				 sta_info->rx_retry_pkts, sta_info->rx_bcmc_pkts,
				 sta_info->cap, sta_info->mac_addr[0],
				 sta_info->mac_addr[1], sta_info->mac_addr[2],
				 sta_info->freq,
				 sta_info->bandwidth,
				 sta_info->rssi,
				 sta_info->data_rate,
				 sta_info->supported_mode,
				 -1,
				 -1,
				 sta_info->reason,
				 sta_info->supported_band,
				 sta_info->country,
				 sta_info->ani_level,
				 -1,
				 -1,
				 -1,
				 sta_info->roam_trigger_reason,
				 sta_info->roam_fail_reason,
				 sta_info->roam_invoke_fail_reason,
				 sta_info->tsf_out_of_sync_count,
				 sta_info->latest_tx_power,
				 sta_info->latest_tx_rate,
				 sta_info->target_power_24g_1mbps,
				 sta_info->target_power_24g_6mbps,
				 sta_info->target_power_5g_6mbps);
	} else {
		ret = snprintf(buf, buf_len,
			 "%d %d %04x %d %d %d %d %d %d %d %d %d %s",
			 sta_info->rx_retry_pkts, sta_info->rx_bcmc_pkts,
			 -1, /* CAP */
			 -1, /* Channel */
			 -1, /* Bandwidth */
			 -1, /* Rssi */
			 -1, /* Data_rate */
			 -1, /* 11_mode */
			 -1,
			 -1,
			 -1, /* Reason */
			 -1, /* Support_mode */
			 sta_info->country);
	}
	return ret;
}

static int get_sta_info_legacy_handler(struct nl_msg *msg, void *arg)
{
	struct genlmsghdr *msg_hdr;
	struct nlattr *tb[NL80211_ATTR_MAX_INTERNAL + 1];
	struct nlattr *tb_vendor[NL80211_ATTR_MAX_INTERNAL + 1];
	struct nlattr *vendor_data, *attr_link_info;
	int vendor_len;
	struct resp_info *info = (struct resp_info *)arg;
	u8 *assoc_req_ie = NULL;
	size_t assoc_req_ie_len = 0;

	if (!info) {
		wpa_printf(MSG_ERROR,"Invalid arg");
		return -1;
	}

	wpa_printf(MSG_INFO,"Recv STA info %02x:%02x:%02x:%02x:%02x:%02x",
	      info->mac_addr[0], info->mac_addr[1], info->mac_addr[2],
	      info->mac_addr[3], info->mac_addr[4], info->mac_addr[5]);

	msg_hdr = (struct genlmsghdr *)nlmsg_data(nlmsg_hdr(msg));
	nla_parse(tb, NL80211_ATTR_MAX_INTERNAL, genlmsg_attrdata(msg_hdr, 0),
		  genlmsg_attrlen(msg_hdr, 0), NULL);

	if (!tb[NL80211_ATTR_VENDOR_DATA]) {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_VENDOR_DATA parse error");
		return -1;
	}

	vendor_data = nla_data(tb[NL80211_ATTR_VENDOR_DATA]);
	vendor_len = nla_len(tb[NL80211_ATTR_VENDOR_DATA]);

	if (nla_parse(tb_vendor, NL80211_ATTR_MAX_INTERNAL,
		      vendor_data, vendor_len, NULL)) {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_VENDOR_DATA not found");
		return -1;
	}

	attr_link_info = tb_vendor[QCA_WLAN_VENDOR_ATTR_GET_STATION_LINK_INFO_ATTR];
	if (attr_link_info) {
		struct nlattr *tb_link_info[NL80211_ATTR_MAX + 1];
		struct nlattr *attr_survey_info, *attr_sta_info;

		nla_parse(tb_link_info, NL80211_ATTR_MAX, nla_data(attr_link_info),
			  nla_len(attr_link_info), NULL);
		attr_survey_info = tb_link_info[NL80211_ATTR_SURVEY_INFO];
		if (attr_survey_info) {
			struct nlattr *tb_survey_info[NL80211_SURVEY_INFO_MAX + 1];

			nla_parse(tb_survey_info, NL80211_SURVEY_INFO_MAX,
				  nla_data(attr_survey_info),
				  nla_len(attr_survey_info), NULL);
			if (tb_survey_info[NL80211_SURVEY_INFO_FREQUENCY]) {
				g_sta_info.freq =
					nla_get_u32(tb_survey_info[NL80211_SURVEY_INFO_FREQUENCY]);
				wpa_printf(MSG_INFO,"channel %d", g_sta_info.freq);
			}
		}

		attr_sta_info = tb_link_info[NL80211_ATTR_STA_INFO];
		if (attr_sta_info) {
			struct nlattr *tb_sta_info[NL80211_STA_INFO_MAX + 1];

			nla_parse(tb_sta_info, NL80211_STA_INFO_MAX,
				  nla_data(attr_sta_info),
				  nla_len(attr_sta_info), NULL);
			if (tb_sta_info[NL80211_STA_INFO_SIGNAL]) {
				g_sta_info.rssi = nla_get_u8(tb_sta_info[NL80211_STA_INFO_SIGNAL]);
				g_sta_info.rssi -= NOISE_FLOOR_DBM;
				wpa_printf(MSG_INFO,"rssi %d", g_sta_info.rssi);
			}
			if (tb_sta_info[NL80211_STA_INFO_TX_BITRATE]) {
				struct nlattr *tb_antenna_info[NL80211_RATE_INFO_MAX + 1];
				nla_parse(tb_antenna_info, NL80211_RATE_INFO_MAX,
					  nla_data(tb_sta_info[NL80211_STA_INFO_TX_BITRATE]),
					  nla_len(tb_sta_info[NL80211_STA_INFO_TX_BITRATE]),
					  NULL);
			}
		}

		if (tb_link_info[NL80211_ATTR_REASON_CODE]) {
			g_sta_info.reason =
				nla_get_u32(tb_link_info[NL80211_ATTR_REASON_CODE]);
			wpa_printf(MSG_INFO,"reason %d", g_sta_info.reason);
		}

		if (tb_link_info[NL80211_ATTR_STA_CAPABILITY]) {
			g_sta_info.cap =
				nla_get_u16(tb_link_info[NL80211_ATTR_STA_CAPABILITY]);
			wpa_printf(MSG_INFO,"cap %04x", g_sta_info.cap);
		}
	}

	if (tb_vendor[GET_STATION_INFO_REMOTE_LAST_RX_RATE]) {
		g_sta_info.data_rate =
			nla_get_u32(tb_vendor[GET_STATION_INFO_REMOTE_LAST_RX_RATE]);
		wpa_printf(MSG_INFO,"data_rate %d", g_sta_info.data_rate);
	}

	if (tb_vendor[GET_STATION_INFO_REMOTE_RX_RETRY_COUNT]) {
		g_sta_info.rx_retry_pkts +=
			nla_get_u32(tb_vendor[GET_STATION_INFO_REMOTE_RX_RETRY_COUNT]);
		wpa_printf(MSG_INFO,"rx_retry_pkts %d", g_sta_info.rx_retry_pkts);
	}

	if (tb_vendor[GET_STATION_INFO_REMOTE_RX_BC_MC_COUNT]) {
		g_sta_info.rx_bcmc_pkts +=
			nla_get_u32(tb_vendor[GET_STATION_INFO_REMOTE_RX_BC_MC_COUNT]);
		wpa_printf(MSG_INFO,"rx_bcmc_pkts %d", g_sta_info.rx_bcmc_pkts);
	}

	if (tb_vendor[GET_STATION_INFO_REMOTE_CH_WIDTH]) {
		g_sta_info.bandwidth =
			nla_get_u8(tb_vendor[GET_STATION_INFO_REMOTE_CH_WIDTH]);
		wpa_printf(MSG_INFO,"bandwidth %d", g_sta_info.bandwidth);
	}

	if (tb_vendor[QCA_WLAN_VENDOR_ATTR_802_11_MODE]) {
		g_sta_info.dot11_mode =
			nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_802_11_MODE]);
		wpa_printf(MSG_INFO,"dot11_mode %d", g_sta_info.dot11_mode);
	}

	if (tb_vendor[GET_STATION_INFO_REMOTE_SUPPORTED_MODE]) {
		g_sta_info.supported_mode =
			nla_get_u8(tb_vendor[GET_STATION_INFO_REMOTE_SUPPORTED_MODE]);
		wpa_printf(MSG_INFO,"supported_mode %d", g_sta_info.supported_mode);
	}

	if (tb_vendor[GET_STATION_INFO_ASSOC_REQ_IES]) {
		assoc_req_ie =
			nla_data(tb_vendor[GET_STATION_INFO_ASSOC_REQ_IES]);
		assoc_req_ie_len =
			nla_len(tb_vendor[GET_STATION_INFO_ASSOC_REQ_IES]);
	}

	parse_assoc_req_ies(assoc_req_ie, assoc_req_ie_len);

	if (g_sta_info.supp_op_classes) {
		op_class_band_conversion(g_sta_info.supp_op_classes);
		g_sta_info.show_band = true;
	}
	else if (g_sta_info.supp_channels) {
		supp_channels_band_conversion(g_sta_info.supp_channels);
		g_sta_info.show_band = true;
	}
	else
		wpa_printf(MSG_ERROR,"supp_op_classes and supp_channels both are null");

	g_sta_info.num_received_vendor_sta_info++;

	wpa_printf(MSG_INFO,"num_received_vendor_sta_info %d",
	      g_sta_info.num_received_vendor_sta_info);

	return 0;
}

static int
wpa_driver_send_get_sta_info_legacy_cmd(struct i802_bss *bss, u8 *mac,
					int *status)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *nlmsg;
	struct nlattr *attr;
	struct resp_info info;

	memset(&info, 0, sizeof(info));
	os_memcpy(&info.mac_addr[0], mac, MAC_ADDR_LEN);
	os_memcpy(&g_sta_info.mac_addr[0], mac, MAC_ADDR_LEN);

	nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
				     QCA_NL80211_VENDOR_SUBCMD_GET_STATION);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR,"Failed to allocate nl message");
		return -1;
	}

	attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (!attr) {
		nlmsg_free(nlmsg);
		return -1;
	}

	if (nla_put(nlmsg, QCA_WLAN_VENDOR_ATTR_GET_STATION_REMOTE,
		    MAC_ADDR_LEN, mac)) {
		wpa_printf(MSG_ERROR,"Failed to put QCA_WLAN_VENDOR_ATTR_GET_STATION_REMOTE");
		nlmsg_free(nlmsg);
		return -1;
	}

	nla_nest_end(nlmsg, attr);

	*status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
			     get_sta_info_legacy_handler, &info);
	if (*status != 0) {
		wpa_printf(MSG_ERROR,"Failed to send nl message with err %d", *status);
		return -1;
	}

	return 0;
}

static int get_sta_info_handler(struct nl_msg *msg, void *arg)
{
	struct genlmsghdr *msg_hdr;
	struct nlattr *tb[NL80211_ATTR_MAX_INTERNAL + 1];
	struct nlattr *tb_vendor[GET_STA_INFO_MAX + 1];
	struct nlattr *vendor_data;
	int vendor_len;
	struct resp_info *info = (struct resp_info *)arg;
	uint8_t mac_addr[MAC_ADDR_LEN];

	if (!info) {
		wpa_printf(MSG_ERROR,"Invalid arg");
		return -1;
	}

	msg_hdr = (struct genlmsghdr *)nlmsg_data(nlmsg_hdr(msg));
	nla_parse(tb, NL80211_ATTR_MAX_INTERNAL, genlmsg_attrdata(msg_hdr, 0),
		  genlmsg_attrlen(msg_hdr, 0), NULL);

	if (!tb[NL80211_ATTR_VENDOR_DATA]) {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_VENDOR_DATA not found");
		return -1;
	}

	vendor_data = nla_data(tb[NL80211_ATTR_VENDOR_DATA]);
	vendor_len = nla_len(tb[NL80211_ATTR_VENDOR_DATA]);

	if (nla_parse(tb_vendor, GET_STA_INFO_MAX,
		      vendor_data, vendor_len, NULL)) {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_VENDOR_DATA parse error");
		return -1;
	}

	if (tb_vendor[GET_STA_INFO_MAC]) {
		nla_memcpy(mac_addr,
			tb_vendor[GET_STA_INFO_MAC],MAC_ADDR_LEN);
		if (os_memcmp(mac_addr, info->mac_addr, MAC_ADDR_LEN)) {
			wpa_printf(MSG_ERROR,"MAC address mismatch");
			return -1;
		}
	}

	wpa_printf(MSG_INFO,"Recv STA info %02x:%02x:%02x:%02x:%02x:%02x",
	      info->mac_addr[0], info->mac_addr[1], info->mac_addr[2],
	      info->mac_addr[3], info->mac_addr[4], info->mac_addr[5]);

	if (tb_vendor[GET_STA_INFO_RX_RETRY_COUNT]) {
		g_sta_info.rx_retry_pkts +=
			nla_get_u32(tb_vendor[GET_STA_INFO_RX_RETRY_COUNT]);
		wpa_printf(MSG_INFO,"rx_retry_pkts %d", g_sta_info.rx_retry_pkts);
	}

	if (tb_vendor[GET_STA_INFO_RX_BC_MC_COUNT]) {
		g_sta_info.rx_bcmc_pkts +=
			nla_get_u32(tb_vendor[GET_STA_INFO_RX_BC_MC_COUNT]);
		wpa_printf(MSG_INFO,"rx_bcmc_pkts %d", g_sta_info.rx_bcmc_pkts);
	}

	if (tb_vendor[GET_STA_INFO_TX_RETRY_SUCCEED]) {
		g_sta_info.tx_pkts_retried +=
			nla_get_u32(tb_vendor[GET_STA_INFO_TX_RETRY_SUCCEED]);
		wpa_printf(MSG_INFO,"tx_pkts_retried %d", g_sta_info.tx_pkts_retried);
	}

	if (tb_vendor[GET_STA_INFO_TX_RETRY_EXHAUSTED]) {
		g_sta_info.tx_pkts_retry_exhausted +=
			nla_get_u32(tb_vendor[GET_STA_INFO_TX_RETRY_EXHAUSTED]);
		wpa_printf(MSG_INFO,"tx_pkts_retry_exhausted %d", g_sta_info.tx_pkts_retry_exhausted);
	}

	if (tb_vendor[GET_STA_INFO_TARGET_TX_TOTAL]) {
		g_sta_info.tx_pkts_fw_total +=
			nla_get_u32(tb_vendor[GET_STA_INFO_TARGET_TX_TOTAL]);
		wpa_printf(MSG_INFO,"tx_pkts_fw_total %d", g_sta_info.tx_pkts_fw_total);
	}

	if (tb_vendor[GET_STA_INFO_TARGET_TX_RETRY]) {
		g_sta_info.tx_pkts_fw_retries +=
			nla_get_u32(tb_vendor[GET_STA_INFO_TARGET_TX_RETRY]);
		wpa_printf(MSG_INFO,"tx_pkts_fw_retries %d", g_sta_info.tx_pkts_fw_retries);
	}

	if (tb_vendor[GET_STA_INFO_TARGET_TX_RETRY_EXHAUSTED]) {
		g_sta_info.tx_pkts_fw_retry_exhausted +=
			nla_get_u32(tb_vendor[GET_STA_INFO_TARGET_TX_RETRY_EXHAUSTED]);
		wpa_printf(MSG_INFO,"tx_pkts_fw_retry_exhausted %d", g_sta_info.tx_pkts_fw_retry_exhausted);
	}

	if (tb_vendor[GET_STA_INFO_ANI_LEVEL]) {
		g_sta_info.ani_level =
			nla_get_u32(tb_vendor[GET_STA_INFO_ANI_LEVEL]);
		wpa_printf(MSG_INFO,"ani_level %d", g_sta_info.ani_level);
	}

	if (tb_vendor[GET_STA_INFO_ROAM_TRIGGER_REASON]) {
		g_sta_info.roam_trigger_reason =
			nla_get_u32(tb_vendor[GET_STA_INFO_ROAM_TRIGGER_REASON]);
		wpa_printf(MSG_INFO,"roam_trigger_reason %d", g_sta_info.roam_trigger_reason);
	}

	if (tb_vendor[GET_STA_INFO_ROAM_FAIL_REASON]) {
		g_sta_info.roam_fail_reason =
			nla_get_u32(tb_vendor[GET_STA_INFO_ROAM_FAIL_REASON]);
		wpa_printf(MSG_INFO,"roam_fail_reason %d", g_sta_info.roam_fail_reason);
	}

	if (tb_vendor[GET_STA_INFO_ROAM_INVOKE_FAIL_REASON]) {
		g_sta_info.roam_invoke_fail_reason =
			nla_get_u32(tb_vendor[GET_STA_INFO_ROAM_INVOKE_FAIL_REASON]);
		wpa_printf(MSG_INFO,"roam_invoke_fail_reason %d", g_sta_info.roam_invoke_fail_reason);
	}

	if (tb_vendor[GET_STA_INFO_TSF_OUT_OF_SYNC_COUNT]) {
		g_sta_info.tsf_out_of_sync_count =
			nla_get_u32(tb_vendor[GET_STA_INFO_TSF_OUT_OF_SYNC_COUNT]);
		wpa_printf(MSG_INFO,"tsf_out_of_sync_count %d", g_sta_info.tsf_out_of_sync_count);
	}

	if (tb_vendor[GET_STA_INFO_LATEST_TX_POWER]) {
		g_sta_info.latest_tx_power =
			nla_get_u32(tb_vendor[GET_STA_INFO_LATEST_TX_POWER]);
		wpa_printf(MSG_INFO,"latest_tx_power %d", g_sta_info.latest_tx_power);
	}

	if (tb_vendor[GET_STA_INFO_LATEST_TX_RATE]) {
		g_sta_info.latest_tx_rate =
			nla_get_u32(tb_vendor[GET_STA_INFO_LATEST_TX_RATE]);
		wpa_printf(MSG_INFO,"latest_tx_rate %d", g_sta_info.latest_tx_rate);
	}

	if (tb_vendor[GET_STA_INFO_TARGET_POWER_24G_1MBPS]) {
		g_sta_info.target_power_24g_1mbps =
			nla_get_u32(tb_vendor[GET_STA_INFO_TARGET_POWER_24G_1MBPS]);
		wpa_printf(MSG_INFO,"target_power_24g_1mbps %d", g_sta_info.target_power_24g_1mbps);
	}

	if (tb_vendor[GET_STA_INFO_TARGET_POWER_24G_6MBPS]) {
		g_sta_info.target_power_24g_6mbps =
			nla_get_u32(tb_vendor[GET_STA_INFO_TARGET_POWER_24G_6MBPS]);
		wpa_printf(MSG_INFO,"target_power_24g_6mbps %d", g_sta_info.target_power_24g_6mbps);
	}

	if (tb_vendor[GET_STA_INFO_TARGET_POWER_5G_6MBPS]) {
		g_sta_info.target_power_5g_6mbps =
			nla_get_u32(tb_vendor[GET_STA_INFO_TARGET_POWER_5G_6MBPS]);
		wpa_printf(MSG_INFO,"target_power_5g_6mbps %d", g_sta_info.target_power_5g_6mbps);
	}

	if (tb_vendor[GET_STA_INFO_LATEST_RIX]) {
		g_sta_info.latest_rix =
			nla_get_u32(tb_vendor[GET_STA_INFO_LATEST_RIX]);
		wpa_printf(MSG_INFO,"latest_rix %d", g_sta_info.latest_rix);
	}


	g_sta_info.num_received_vendor_sta_info++;

	wpa_printf(MSG_INFO,"num_received_vendor_sta_info %d",
	      g_sta_info.num_received_vendor_sta_info);

	return 0;
}

static int wpa_driver_ioctl(struct i802_bss *bss, char *cmd,
				 char *buf, size_t buf_len, int *status,
				 struct wpa_driver_nl80211_data *drv) {
	struct ifreq ifr;
	android_wifi_priv_cmd priv_cmd;
	memset(&ifr, 0, sizeof(ifr));
	memset(&priv_cmd, 0, sizeof(priv_cmd));
	os_memcpy(buf, cmd, strlen(cmd) + 1);
	os_strlcpy(ifr.ifr_name, bss->ifname, IFNAMSIZ);
	priv_cmd.buf = buf;
	priv_cmd.used_len = buf_len;
	priv_cmd.total_len = buf_len;
	ifr.ifr_data = &priv_cmd;

	if ((ioctl(drv->global->ioctl_sock, SIOCDEVPRIVATE + 1, &ifr)) < 0) {
		wpa_printf(MSG_ERROR,"%s: failed to issue private commands\n", __func__);
		*status = 1;
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	} else {
		wpa_printf(MSG_ERROR,"Response: %s", buf);
		return WPA_DRIVER_OEM_STATUS_SUCCESS;
	}
}

static int wpa_driver_send_get_sta_info_cmd(struct i802_bss *bss, u8 *mac,
					    int *status, bool *new_cmd)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *nlmsg;
	struct nlattr *attr;
	struct resp_info info;

	memset(&info, 0, sizeof(info));
	os_memcpy(&info.mac_addr[0], mac, MAC_ADDR_LEN);
	os_memcpy(&g_sta_info.mac_addr[0], mac, MAC_ADDR_LEN);

	*new_cmd = true;


	nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
				     QCA_NL80211_VENDOR_SUBCMD_GET_STA_INFO);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR,"Failed to allocate nl message");
		return -1;
	}

	attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (!attr) {
		nlmsg_free(nlmsg);
		return -1;
	}

	if (nla_put(nlmsg, GET_STA_INFO_MAC,
		    MAC_ADDR_LEN, mac)) {
		wpa_printf(MSG_ERROR,"Failed to put GET_STA_INFO_MAC");
		nlmsg_free(nlmsg);
		return -1;
	}

	nla_nest_end(nlmsg, attr);

	*status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
			     get_sta_info_handler, &info);
	if (*status != 0) {
		wpa_printf(MSG_ERROR,"Failed to send nl message with err %d, retrying with legacy command", *status);
		*new_cmd = false;
		return wpa_driver_send_get_sta_info_legacy_cmd(bss,mac,status);
	}

	g_sta_info.num_request_vendor_sta_info++;
	wpa_printf(MSG_INFO,"num_request_vendor_sta_info %d",
	      g_sta_info.num_request_vendor_sta_info);

	return 0;
}

static int get_station_handler(struct nl_msg *msg, void *arg)
{
	struct genlmsghdr *msg_hdr;
	struct nlattr *tb[NL80211_ATTR_MAX + 1];
	struct nlattr *tb_sinfo[NL80211_STA_INFO_MAX + 1];
	struct nlattr *tb_rate[NL80211_RATE_INFO_MAX + 1];
	struct nlattr *sinfo_data, *attr;
	int sinfo_len, tmp, num_chain = 0;
	struct resp_info *info = (struct resp_info *)arg;
	uint8_t mac_addr[MAC_ADDR_LEN];

	if (!info) {
		wpa_printf(MSG_ERROR,"Invalid arg");
		return -1;
	}

	msg_hdr = (struct genlmsghdr *)nlmsg_data(nlmsg_hdr(msg));
	nla_parse(tb, NL80211_ATTR_MAX, genlmsg_attrdata(msg_hdr, 0),
		  genlmsg_attrlen(msg_hdr, 0), NULL);

	if (tb[NL80211_ATTR_MAC]) {
		nla_memcpy(mac_addr, tb[NL80211_ATTR_MAC], MAC_ADDR_LEN);
		if (os_memcmp(mac_addr, info->mac_addr, MAC_ADDR_LEN)) {
			wpa_printf(MSG_ERROR,"MAC address mismatch");
			return -1;
		}
	}

	wpa_printf(MSG_INFO,"Recv STA info %02x:%02x:%02x:%02x:%02x:%02x",
	      info->mac_addr[0], info->mac_addr[1], info->mac_addr[2],
	      info->mac_addr[3], info->mac_addr[4], info->mac_addr[5]);

	if (!tb[NL80211_ATTR_STA_INFO]) {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_STA_INFO not found");
		return -1;
	}

	sinfo_data = nla_data(tb[NL80211_ATTR_STA_INFO]);
	sinfo_len = nla_len(tb[NL80211_ATTR_STA_INFO]);

	if (nla_parse(tb_sinfo, NL80211_STA_INFO_MAX, sinfo_data, sinfo_len,
		      NULL)) {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_STA_INFO parse error");
		return -1;
	}

	/* No need to read for summary */
	if (g_sta_info.num_sta == 1) {
		if (tb[NL80211_ATTR_IE])
			parse_assoc_req_ies(nla_data(tb[NL80211_ATTR_IE]),
					    nla_len(tb[NL80211_ATTR_IE]));

		attr = tb_sinfo[NL80211_STA_INFO_TX_BITRATE];
		if (attr) {
			nla_parse(tb_rate, NL80211_RATE_INFO_MAX,
				  nla_data(attr), nla_len(attr), NULL);

			if (tb_rate[NL80211_RATE_INFO_BITRATE32]) {
				g_sta_info.tx_rate =
					nla_get_u32(tb_rate[NL80211_RATE_INFO_BITRATE32]);
				wpa_printf(MSG_INFO,"tx_rate %d", g_sta_info.tx_rate);
			}

			if (tb_rate[NL80211_RATE_INFO_160_MHZ_WIDTH])
				g_sta_info.bandwidth = QCA_VENDOR_WLAN_CHAN_WIDTH_160;
			else if (tb_rate[NL80211_RATE_INFO_80P80_MHZ_WIDTH])
				g_sta_info.bandwidth = QCA_VENDOR_WLAN_CHAN_WIDTH_80_80;
			else if (tb_rate[NL80211_RATE_INFO_80_MHZ_WIDTH])
				g_sta_info.bandwidth = QCA_VENDOR_WLAN_CHAN_WIDTH_80;
			else if (tb_rate[NL80211_RATE_INFO_40_MHZ_WIDTH])
				g_sta_info.bandwidth = QCA_VENDOR_WLAN_CHAN_WIDTH_40;
			else
				g_sta_info.bandwidth = QCA_VENDOR_WLAN_CHAN_WIDTH_20;
			wpa_printf(MSG_INFO,"bandwidth %d", g_sta_info.bandwidth);
		}

		attr = tb_sinfo[NL80211_STA_INFO_RX_BITRATE];
		if (attr) {
			nla_parse(tb_rate, NL80211_RATE_INFO_MAX,
				  nla_data(attr), nla_len(attr), NULL);
			if (tb_rate[NL80211_RATE_INFO_BITRATE32]) {
				g_sta_info.data_rate =
					nla_get_u32(tb_rate[NL80211_RATE_INFO_BITRATE32]);
				wpa_printf(MSG_INFO,"data_rate %d", g_sta_info.data_rate);
			}
		}

		if (tb_sinfo[NL80211_STA_INFO_SIGNAL_AVG]) {
			g_sta_info.rssi =
				nla_get_u8(tb_sinfo[NL80211_STA_INFO_SIGNAL_AVG]);
			g_sta_info.rssi -= NOISE_FLOOR_DBM;
			wpa_printf(MSG_INFO,"rssi %d", g_sta_info.rssi);
		}

		if (tb_sinfo[NL80211_STA_INFO_SIGNAL]) {
			g_sta_info.rx_lastpkt_rssi =
				nla_get_u8(tb_sinfo[NL80211_STA_INFO_SIGNAL]);
			g_sta_info.rx_lastpkt_rssi -= NOISE_FLOOR_DBM;
			wpa_printf(MSG_INFO,"rx_lastpkt_rssi %d", g_sta_info.rx_lastpkt_rssi);
		}

		if (tb_sinfo[NL80211_STA_INFO_CHAIN_SIGNAL_AVG]) {
			nla_for_each_nested(attr,
					    tb_sinfo[NL80211_STA_INFO_CHAIN_SIGNAL_AVG],
					    tmp) {
				if (num_chain >= WMI_MAX_CHAINS) {
					wpa_printf(MSG_ERROR,"WMI_MAX_CHAINS reached");
					break;
				}
				g_sta_info.avg_rssi_per_chain[num_chain] = nla_get_u8(attr);
				g_sta_info.avg_rssi_per_chain[num_chain] -= NOISE_FLOOR_DBM;
				wpa_printf(MSG_INFO,"avg_rssi_per_chain[%d] %d", num_chain,
				      g_sta_info.avg_rssi_per_chain[num_chain]);
				num_chain++;
			}
		}
	}

	if (tb_sinfo[NL80211_STA_INFO_TX_PACKETS]) {
		g_sta_info.tx_pkts_total +=
			nla_get_u32(tb_sinfo[NL80211_STA_INFO_TX_PACKETS]);
		g_sta_info.tx_pckts +=
			nla_get_u32(tb_sinfo[NL80211_STA_INFO_TX_PACKETS]);
		wpa_printf(MSG_INFO,"tx_pkts_total %d", g_sta_info.tx_pkts_total);
		wpa_printf(MSG_INFO,"tx_pckts %d", g_sta_info.tx_pckts);
	}

	if (tb_sinfo[NL80211_STA_INFO_TX_FAILED]) {
		g_sta_info.tx_failures +=
			nla_get_u32(tb_sinfo[NL80211_STA_INFO_TX_FAILED]);
		wpa_printf(MSG_INFO,"tx_failures %d", g_sta_info.tx_failures);
	}

	if (tb_sinfo[NL80211_STA_INFO_TX_RETRIES]) {
		g_sta_info.tx_pkts_retries +=
			nla_get_u32(tb_sinfo[NL80211_STA_INFO_TX_RETRIES]);
		wpa_printf(MSG_INFO,"tx_pkts_retries %d", g_sta_info.tx_pkts_retries);
	}


	g_sta_info.num_received_nl80211_sta_info++;

	wpa_printf(MSG_INFO,"num_received_nl80211_sta_info %d",
	      g_sta_info.num_received_nl80211_sta_info);

	return 0;
}

static int wpa_driver_send_get_station_cmd(struct i802_bss *bss, u8 *mac,
					   int *status)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *nlmsg;
	struct resp_info info;

	memset(&info, 0, sizeof(info));
	os_memcpy(&info.mac_addr[0], mac, MAC_ADDR_LEN);
	os_memcpy(&g_sta_info.mac_addr[0], mac, MAC_ADDR_LEN);

	nlmsg = prepare_nlmsg(drv, bss->ifname, NL80211_CMD_GET_STATION, 0, 0);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR,"Failed to allocate nl message");
		return -1;
	}

	if (nla_put(nlmsg, NL80211_ATTR_MAC, MAC_ADDR_LEN, mac)) {
		wpa_printf(MSG_ERROR,"Failed to put NL80211_ATTR_MAC");
		nlmsg_free(nlmsg);
		return -1;
	}

	*status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
			     get_station_handler, &info);
	if (*status != 0) {
		wpa_printf(MSG_ERROR,"Failed to send nl message with err %d", *status);
		return -1;
	}

	g_sta_info.num_request_nl80211_sta_info++;
	wpa_printf(MSG_INFO,"num_request_nl80211_sta_info %d",
	      g_sta_info.num_request_nl80211_sta_info);

	return 0;
}

static int wpa_driver_get_sta_info(struct i802_bss *bss, u8 *mac,
				   int *status)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct hostapd_data *hapd = bss->ctx;
	struct sta_info *iter, *sta = NULL;
	bool new_cmd;
	int ret;
	char buf[MAX_DRV_CMD_SIZE];
	char *p;

	memset(buf, 0, sizeof(buf));

	ret = wpa_driver_send_get_sta_info_cmd(bss, mac, status, &new_cmd);
	if (ret < 0)
		return ret;

	if (new_cmd) {
		ret = wpa_driver_send_get_station_cmd(bss, mac, status);
		if (ret < 0)
			return ret;

		/* No need to read for summary */
		if (g_sta_info.num_sta == 1) {
			if (!hapd) {
				wpa_printf(MSG_ERROR,"hapd is NULL");
				return -1;
			}
			iter = hapd->sta_list;
			while (iter) {
				if (os_memcmp(mac, iter->addr, MAC_ADDR_LEN) == 0) {
					sta = iter;
					break;
				}
				iter = iter->next;
			}
			if (!sta) {
				wpa_printf(MSG_ERROR,"STA is not found");
				return -1;
			}

			g_sta_info.cap = sta->capability;
			wpa_printf(MSG_INFO,"cap %04x", g_sta_info.cap);
			g_sta_info.freq = (u32)hapd->iface->freq;
			wpa_printf(MSG_INFO,"freq %d", g_sta_info.freq);

			if (g_sta_info.flags.he_supported) {
				g_sta_info.dot11_mode = QCA_VENDOR_WLAN_802_11_MODE_AX;
				g_sta_info.supported_mode = QCA_VENDOR_WLAN_PHY_MODE_HE;
			} else if (g_sta_info.flags.vht_supported) {
				g_sta_info.dot11_mode = QCA_VENDOR_WLAN_802_11_MODE_AC;
				g_sta_info.supported_mode = QCA_VENDOR_WLAN_PHY_MODE_VHT;
			} else if (g_sta_info.flags.ht_supported) {
				g_sta_info.dot11_mode = QCA_VENDOR_WLAN_802_11_MODE_N;
				g_sta_info.supported_mode = QCA_VENDOR_WLAN_PHY_MODE_HT;
			} else {
				if (g_sta_info.freq < 4900) {
					if (hapd->iconf->hw_mode == HOSTAPD_MODE_IEEE80211B)
						g_sta_info.dot11_mode =
							QCA_VENDOR_WLAN_802_11_MODE_B;
					else
						g_sta_info.dot11_mode =
							QCA_VENDOR_WLAN_802_11_MODE_G;
				} else {
					g_sta_info.dot11_mode = QCA_VENDOR_WLAN_802_11_MODE_A;
				}
				g_sta_info.supported_mode = QCA_VENDOR_WLAN_PHY_MODE_LEGACY;
			}

			wpa_printf(MSG_INFO,"dot11_mode %d", g_sta_info.dot11_mode);
			wpa_printf(MSG_INFO,"supported_mode %d", g_sta_info.supported_mode);
		}
	}

	if(wpa_driver_ioctl(bss, "GETCOUNTRYREV", buf, sizeof(buf), &status, drv) == 0){
		p = strstr(buf, " ");
		if(p != NULL)
			memcpy(g_sta_info.country, (p+1), strlen(p+1)+1);//length of p including null
	}else{
	}

	wpa_printf(MSG_INFO,"STA information completed");

	return 0;
}

static int wpa_driver_get_all_sta_info(struct i802_bss *bss, int *status)
{
	struct hostapd_data *hapd = bss->ctx;
	struct sta_info *sta;
	int ret, total_ret = 0;

	if(bss->drv && bss->drv->nlmode != NL80211_IFTYPE_AP) {
		wpa_printf(MSG_ERROR,"Not a hapd interface");
		return -1;
	}

	if (!hapd) {
		wpa_printf(MSG_ERROR,"hapd is NULL");
		return -1;
	}

	g_sta_info.num_sta = hapd->num_sta;

	sta = hapd->sta_list;
	while (sta) {
		ret = wpa_driver_get_sta_info(bss, sta->addr, status);
		if (ret < 0)
			return ret;
		sta = sta->next;
		total_ret += ret;
	}

	wpa_printf(MSG_INFO,"All STAs information completed");

	return total_ret;
}

static int wpa_driver_handle_get_sta_info(struct i802_bss *bss, char *cmd,
					  char *buf, size_t buf_len,
					  int *status)
{
	u8 mac[MAC_ADDR_LEN];
	int ret;

	os_memset(&g_sta_info, 0, sizeof(g_sta_info));

	cmd = skip_white_space(cmd);
	if (strlen(cmd) >= MAC_ADDR_LEN * 2 + MAC_ADDR_LEN - 1
	    && convert_string_to_bytes(mac, cmd, MAC_ADDR_LEN) > 0) {
		g_sta_info.num_sta = 1;
		ret = wpa_driver_get_sta_info(bss, mac, status);
		if (ret < 0)
			return ret;
	} else {
		ret = wpa_driver_get_all_sta_info(bss, status);
		if (ret < 0)
			return ret;
	}

	if (ret == 0) {
		ret = fill_sta_info(&g_sta_info, buf, buf_len);
		wpa_printf(MSG_INFO,"%s", buf);
	} else {
		wpa_printf(MSG_ERROR,"Failed to get STA info, num_sta %d vendor_sent %d vendor_recv %d nl80211_send %d nl80211 recv %d",
		      g_sta_info.num_sta,
		      g_sta_info.num_request_vendor_sta_info,
		      g_sta_info.num_received_vendor_sta_info,
		      g_sta_info.num_request_nl80211_sta_info,
		      g_sta_info.num_received_nl80211_sta_info);
		wpa_printf(MSG_ERROR,"GETSTAINFO failed");
	}

	return ret;
}

static int thermal_info_handler(struct nl_msg *msg, void *arg)
{
	struct nlattr *tb[NL80211_ATTR_MAX + 1];
	struct genlmsghdr *gnlh = nlmsg_data(nlmsg_hdr(msg));
	int *param = arg;
	struct nlattr *nl_vendor;
	struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_MAX + 1];

	nla_parse(tb, NL80211_ATTR_MAX, genlmsg_attrdata(gnlh, 0),
	    genlmsg_attrlen(gnlh, 0), NULL);

	nl_vendor = tb[NL80211_ATTR_VENDOR_DATA];
	if (!nl_vendor || nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_MAX,
	    nla_data(nl_vendor), nla_len(nl_vendor), NULL)) {
		wpa_printf(MSG_ERROR, "%s: No vendor data found", __func__);
		return NL_SKIP;
	}

	if (tb_vendor[QCA_WLAN_VENDOR_ATTR_THERMAL_GET_TEMPERATURE_DATA])
		*param = (int) nla_get_u32(
		    tb_vendor[QCA_WLAN_VENDOR_ATTR_THERMAL_GET_TEMPERATURE_DATA]);
	else if (tb_vendor[QCA_WLAN_VENDOR_ATTR_THERMAL_LEVEL])
		*param = (int) nla_get_u32(
		    tb_vendor[QCA_WLAN_VENDOR_ATTR_THERMAL_LEVEL]);
	else
		wpa_printf(MSG_ERROR, "%s: failed to parse data", __func__);

	return NL_SKIP;
}

static int wpa_driver_cmd_get_thermal_info(struct i802_bss *bss, int *result, int attr)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *msg = NULL;
	struct nlattr *params = NULL;
	int ret = 0;

	if (!(msg = nl80211_drv_msg(drv, 0, NL80211_CMD_VENDOR)) ||
	    nla_put_u32(msg, NL80211_ATTR_VENDOR_ID, OUI_QCA) ||
	    nla_put_u32(msg, NL80211_ATTR_VENDOR_SUBCMD,
			QCA_NL80211_VENDOR_SUBCMD_THERMAL_CMD) ||
	    !(params = nla_nest_start(msg, NL80211_ATTR_VENDOR_DATA)) ||
	    nla_put_u32(msg, QCA_WLAN_VENDOR_ATTR_THERMAL_CMD_VALUE, attr)) {
		nlmsg_free(msg);
		return -1;
	}

	nla_nest_end(msg, params);
	ret = send_and_recv_msgs(drv, msg, thermal_info_handler, result, NULL, NULL);
	if (!ret)
		return 0;
	wpa_printf(MSG_ERROR, "%s: Failed get thermal info, ret=%d(%s)",
				__func__, ret, strerror(-ret));
	return ret;
}

static int get_scan_handler(struct nl_msg *msg, void *arg)
{
	struct genlmsghdr *msg_hdr;
	struct nlattr *attr[NL80211_ATTR_MAX + 1];
	struct resp_info *info = (struct resp_info *)arg;
	struct nlattr *bss_attr[NL80211_BSS_MAX + 1];
	char *bssid;
	static struct nla_policy get_scan_policy[NL80211_BSS_MAX + 1] = {
		[NL80211_BSS_BSSID] = {},
		[NL80211_BSS_FREQUENCY] = { .type = NLA_U32 },
		[NL80211_BSS_STATUS] = { .type = NLA_U32 },
		[NL80211_BSS_CHAN_WIDTH] = { .type = NLA_U32 },
	};

	if (!info) {
		wpa_printf(MSG_DEBUG, "resp_info is NULL");
		return NL_SKIP;
	}

	msg_hdr = (struct genlmsghdr *)nlmsg_data(nlmsg_hdr(msg));
	nla_parse(attr, NL80211_ATTR_MAX, genlmsg_attrdata(msg_hdr, 0),
		  genlmsg_attrlen(msg_hdr, 0), NULL);

	if (!attr[NL80211_ATTR_BSS]) {
		wpa_printf(MSG_DEBUG, "no bss info");
		return NL_SKIP;
	}

	if (nla_parse_nested(bss_attr, NL80211_BSS_MAX,
			     attr[NL80211_ATTR_BSS],
			     get_scan_policy)) {
		wpa_printf(MSG_DEBUG, "parse bss attr fail");
		return NL_SKIP;
	}

	if (!bss_attr[NL80211_BSS_BSSID])
		return NL_SKIP;

	if (!bss_attr[NL80211_BSS_STATUS])
		return NL_SKIP;

	if (nla_get_u32(bss_attr[NL80211_BSS_STATUS]) !=
	    NL80211_BSS_STATUS_ASSOCIATED)
		return NL_SKIP;

	bssid = nla_data(bss_attr[NL80211_BSS_BSSID]);
	os_memcpy(g_csi_param.connected_bssid, bssid, MAC_ADDR_LEN);

	wpa_printf(MSG_DEBUG, "get connected bss");
	if (bss_attr[NL80211_BSS_FREQUENCY])
		wpa_printf(MSG_DEBUG, "freq %d", nla_get_u32(bss_attr[NL80211_BSS_FREQUENCY]));

	if (bss_attr[NL80211_BSS_CHAN_WIDTH])
		wpa_printf(MSG_DEBUG, "BW %d", nla_get_u32(bss_attr[NL80211_BSS_CHAN_WIDTH]));

	return 0;
}

static int wpa_driver_send_get_scan_cmd(struct i802_bss *bss, int *status)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *nlmsg;
	struct resp_info info;

	os_memset(g_csi_param.connected_bssid, 0xff, MAC_ADDR_LEN);
	nlmsg = prepare_nlmsg(drv, bss->ifname, NL80211_CMD_GET_SCAN, 0, NLM_F_DUMP);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR, "Failed to allocate nl message");
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	*status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
			     get_scan_handler, &info);
	if (*status != 0) {
		wpa_printf(MSG_ERROR, "Failed to send nl message with err %d", *status);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	return WPA_DRIVER_OEM_STATUS_SUCCESS;
}

static int wpa_driver_start_csi_capture(struct i802_bss *bss, int *status,
					int transport_mode)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *nlmsg;
	struct nlattr *attr, *attr_table, *attr_entry;
	char ta_mask[MAC_ADDR_LEN] = {0xff, 0xff, 0xff, 0xff, 0xff, 0xff};

	nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
				     QCA_NL80211_VENDOR_SUBCMD_PEER_CFR_CAPTURE_CFG);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR, "Failed to allocate nl message");
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (!attr) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_VERSION,
		       ENHANCED_CFR_VER)) {
		wpa_printf(MSG_ERROR, "Failed to csi version");
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_DATA_TRANSPORT_MODE,
		       transport_mode)) {
		wpa_printf(MSG_ERROR, "Failed to set transport mode");
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_flag(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_ENABLE)) {
		wpa_printf(MSG_ERROR, "Failed to csi enable flag");
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_ENABLE_GROUP_BITMAP,
			CSI_GROUP_BITMAP)) {
		wpa_printf(MSG_ERROR, "Failed to csi group bitmap");
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_CAPTURE_TYPE,
			QCA_WLAN_VENDOR_CFR_TA_RA)) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	attr_table = nla_nest_start(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_GROUP_TABLE);
	if (!attr_table) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	attr_entry = nla_nest_start(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_GROUP_ENTRY);
	if (!attr_entry) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_GROUP_NUMBER,
			CSI_DEFAULT_GROUP_ID)) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_GROUP_MGMT_FILTER,
			CSI_MGMT_BEACON)) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_GROUP_TA,
		    MAC_ADDR_LEN, g_csi_param.connected_bssid)) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_GROUP_TA_MASK,
		    MAC_ADDR_LEN, ta_mask)) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}
	nla_nest_end(nlmsg, attr_entry);
	nla_nest_end(nlmsg, attr_table);
	nla_nest_end(nlmsg, attr);

	*status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg, NULL, NULL);
	if (*status != 0) {
		wpa_printf(MSG_ERROR, "Failed to send nl message with err %d", *status);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	g_csi_param.current_state = CSI_STATE_START;

	return WPA_DRIVER_OEM_STATUS_SUCCESS;
}

static int wpa_driver_stop_csi_capture(struct i802_bss *bss, int *status)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *nlmsg;
	struct nlattr *attr;

	nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
				     QCA_NL80211_VENDOR_SUBCMD_PEER_CFR_CAPTURE_CFG);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR, "Failed to allocate nl message");
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (!attr) {
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_CFR_VERSION,
			ENHANCED_CFR_VER)) {
		wpa_printf(MSG_ERROR, "Failed to csi version");
		nlmsg_free(nlmsg);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	wpa_printf(MSG_DEBUG, "send stop csi cmd");
	nla_nest_end(nlmsg, attr);
	*status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg, NULL, NULL);
	if (*status != 0) {
		wpa_printf(MSG_ERROR, "Failed to send nl message with err %d", *status);
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	g_csi_param.current_state = CSI_STATE_STOP;

	return WPA_DRIVER_OEM_STATUS_SUCCESS;
}

static void stop_csi_callback(int nsec)
{
	int status = 0;

	wpa_printf(MSG_DEBUG, "enter %s, nsec %d", __func__, nsec);

	wpa_driver_stop_csi_capture(g_csi_param.bss, &status);
	if (status)
		wpa_printf(MSG_ERROR, "Stop CSI failed");
}

static int wpa_driver_handle_csi_cmd(struct i802_bss *bss, char *cmd,
				     char *buf, size_t buf_len,
				     int *status)
{
	int csi_duration = -1;
	int transport_mode = -1;
	char *next_arg;

	cmd = skip_white_space(cmd);
	wpa_printf(MSG_DEBUG, "cmd:%s", cmd);
	if (os_strncasecmp(cmd, "start", 5) == 0) {
		next_arg = get_next_arg(cmd);
		csi_duration = atoi(next_arg);

		if (csi_duration < 0) {
			wpa_printf(MSG_ERROR, "Invalid duration");
			snprintf(buf, buf_len, "FAIL, Invalid duration");
			*status = CSI_STATUS_REJECTED;
			return WPA_DRIVER_OEM_STATUS_FAILURE;
		}

		wpa_driver_send_get_scan_cmd(bss, status);
		if (g_csi_param.connected_bssid[0] == 0xff) {
			wpa_printf(MSG_DEBUG, "Not connected");
			snprintf(buf, buf_len, "FAIL, Not connected");
			*status = CSI_STATUS_REJECTED;
			return WPA_DRIVER_OEM_STATUS_FAILURE;
		}

		if (g_csi_param.current_state == CSI_STATE_START) {
			wpa_driver_stop_csi_capture(bss, status);
			alarm(0);
		}

		g_csi_param.bss = bss;
		cmd += 6;
		next_arg = get_next_arg(cmd);
		if (*next_arg != '\0' && *next_arg == ' ')
			transport_mode = atoi(next_arg);

		if (transport_mode == 1 || transport_mode == -1)
			transport_mode = 1;
		g_csi_param.transport_mode = transport_mode;

		wpa_driver_start_csi_capture(bss, status, transport_mode);
		if (*status == 0 && csi_duration > 0) {
			signal(SIGALRM, stop_csi_callback);
			alarm(csi_duration);
			wpa_printf(MSG_DEBUG, "set alarm %ds done", csi_duration);
		}
	} else if (os_strncasecmp(cmd, "stop", 4) == 0) {
		if (g_csi_param.current_state != CSI_STATE_START)
			return WPA_DRIVER_OEM_STATUS_SUCCESS;

		wpa_driver_stop_csi_capture(bss, status);
		wpa_printf(MSG_DEBUG, "stop csi cmd");
	} else {
		wpa_printf(MSG_ERROR, "invalid command");
		*status = CSI_STATUS_REJECTED;
		snprintf(buf, buf_len, "FAIL, Invalid command");
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}

	return WPA_DRIVER_OEM_STATUS_SUCCESS;
}

static int wpa_driver_restart_csi(struct i802_bss *bss, int *status)
{
	wpa_driver_send_get_scan_cmd(bss, status);
	if (g_csi_param.connected_bssid[0] == 0xff) {
		wpa_printf(MSG_DEBUG, "%s: Not connected", __func__);
		*status = CSI_STATUS_REJECTED;
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}
	/* Stop CSI capture on previous bss */
	if(wpa_driver_stop_csi_capture(g_csi_param.bss, status)) {
		wpa_printf(MSG_DEBUG, "%s: csi stop failed", __func__);
	}
	g_csi_param.bss = bss;
	if(wpa_driver_start_csi_capture(g_csi_param.bss, status,
				g_csi_param.transport_mode)) {
		*status = CSI_STATUS_REJECTED;
		return WPA_DRIVER_OEM_STATUS_FAILURE;
	}
	*status = CSI_STATUS_SUCCESS;
	return WPA_DRIVER_OEM_STATUS_SUCCESS;
}

/**
 * twt_status_to_string() - Mapping twt response status into string
 * @status: enum qca_wlan_vendor_twt_status values
 *
 * This function will map the twt response status into corresponding
 * string.
 *
 * Return: pointer to the string
 */
static const char *twt_status_to_string(enum qca_wlan_vendor_twt_status status)
{
	switch (status) {
	case QCA_WLAN_VENDOR_TWT_STATUS_OK:
		return "QCA_WLAN_VENDOR_TWT_STATUS_OK";
	case QCA_WLAN_VENDOR_TWT_STATUS_TWT_NOT_ENABLED:
		return "QCA_WLAN_VENDOR_TWT_STATUS_TWT_NOT_ENABLED";
	case QCA_WLAN_VENDOR_TWT_STATUS_USED_DIALOG_ID:
		return "QCA_WLAN_VENDOR_TWT_STATUS_USED_DIALOG_ID";
	case QCA_WLAN_VENDOR_TWT_STATUS_SESSION_BUSY:
		return "QCA_WLAN_VENDOR_TWT_STATUS_SESSION_BUSY";
	case QCA_WLAN_VENDOR_TWT_STATUS_SESSION_NOT_EXIST:
		return "QCA_WLAN_VENDOR_TWT_STATUS_SESSION_NOT_EXIST";
	case QCA_WLAN_VENDOR_TWT_STATUS_NOT_SUSPENDED:
		return "QCA_WLAN_VENDOR_TWT_STATUS_NOT_SUSPENDED";
	case QCA_WLAN_VENDOR_TWT_STATUS_INVALID_PARAM:
		return "QCA_WLAN_VENDOR_TWT_STATUS_INVALID_PARAM";
	case QCA_WLAN_VENDOR_TWT_STATUS_NOT_READY:
		return "QCA_WLAN_VENDOR_TWT_STATUS_NOT_READY";
	case QCA_WLAN_VENDOR_TWT_STATUS_NO_RESOURCE:
		return "QCA_WLAN_VENDOR_TWT_STATUS_NO_RESOURCE";
	case QCA_WLAN_VENDOR_TWT_STATUS_NO_ACK:
		return "QCA_WLAN_VENDOR_TWT_STATUS_NO_ACK";
	case QCA_WLAN_VENDOR_TWT_STATUS_NO_RESPONSE:
		return "QCA_WLAN_VENDOR_TWT_STATUS_NO_RESPONSE";
	case QCA_WLAN_VENDOR_TWT_STATUS_DENIED:
		return "QCA_WLAN_VENDOR_TWT_STATUS_DENIED";
	case QCA_WLAN_VENDOR_TWT_STATUS_UNKNOWN_ERROR:
		return "QCA_WLAN_VENDOR_TWT_STATUS_UNKNOWN_ERROR";
	case QCA_WLAN_VENDOR_TWT_STATUS_ALREADY_SUSPENDED:
		return "QCA_WLAN_VENDOR_TWT_STATUS_ALREADY_SUSPENDED";
	case QCA_WLAN_VENDOR_TWT_STATUS_IE_INVALID:
	       return "QCA_WLAN_VENDOR_TWT_STATUS_IE_INVALID";
	case QCA_WLAN_VENDOR_TWT_STATUS_PARAMS_NOT_IN_RANGE:
		return "QCA_WLAN_VENDOR_TWT_STATUS_PARAMS_NOT_IN_RANGE";
	case QCA_WLAN_VENDOR_TWT_STATUS_PEER_INITIATED_TERMINATE:
		return "QCA_WLAN_VENDOR_TWT_STATUS_PEER_INITIATED_TERMINATE";
	case QCA_WLAN_VENDOR_TWT_STATUS_ROAM_INITIATED_TERMINATE:
		return "QCA_WLAN_VENDOR_TWT_STATUS_ROAM_INITIATED_TERMINATE";
	default:
		return "INVALID TWT STATUS";
	}
}

/**
 * check_for_twt_cmd() - Check if the command string is a TWT command
 * @cmd: Command string
 *
 * This function will identify a TWT operation in the command string
 * and return one of the values in enum qca_wlan_twt_operation.
 *
 * Return: A valid TWT opertion if found, or error if not found
 *
 */
static int check_for_twt_cmd(char **cmd)
{
	if (os_strncasecmp(*cmd, TWT_SETUP_STR, TWT_SETUP_STRLEN) == 0) {
		*cmd += (TWT_SETUP_STRLEN + 1);
		return QCA_WLAN_TWT_SET;
	} else if (os_strncasecmp(*cmd, TWT_TERMINATE_STR,
				  TWT_TERMINATE_STR_LEN) == 0) {
		*cmd += (TWT_TERMINATE_STR_LEN + 1);
		return QCA_WLAN_TWT_TERMINATE;
	} else if (os_strncasecmp(*cmd, TWT_PAUSE_STR, TWT_PAUSE_STR_LEN) == 0) {
		*cmd += (TWT_PAUSE_STR_LEN + 1);
		return QCA_WLAN_TWT_SUSPEND;
	} else if (os_strncasecmp(*cmd, TWT_RESUME_STR, TWT_RESUME_STR_LEN) == 0) {
		*cmd += (TWT_RESUME_STR_LEN + 1);
		return QCA_WLAN_TWT_RESUME;
	} else if (os_strncasecmp(*cmd, TWT_GET_PARAMS_STR,
				  TWT_GET_PARAMS_STR_LEN) == 0) {
		*cmd += (TWT_GET_PARAMS_STR_LEN + 1);
		return QCA_WLAN_TWT_GET;
	} else if (os_strncasecmp(*cmd, TWT_NUDGE_STR,
				  TWT_NUDGE_STR_LEN) == 0) {
		*cmd += (TWT_NUDGE_STR_LEN + 1);
		return QCA_WLAN_TWT_NUDGE;
	} else if (os_strncasecmp(*cmd, TWT_GET_STATS_STR,
				  TWT_GET_STATS_STR_LEN) == 0) {
		*cmd += (TWT_GET_STATS_STR_LEN + 1);
		return QCA_WLAN_TWT_GET_STATS;
	} else if (os_strncasecmp(*cmd, TWT_CLEAR_STATS_STR,
				  TWT_CLEAR_STATS_STR_LEN) == 0) {
		*cmd += (TWT_CLEAR_STATS_STR_LEN + 1);
		return QCA_WLAN_TWT_CLEAR_STATS;
	} else if (os_strncasecmp(*cmd, TWT_GET_CAP_STR,
				  TWT_GET_CAP_STR_LEN) == 0) {
		*cmd += (TWT_GET_CAP_STR_LEN + 1);
		return QCA_WLAN_TWT_GET_CAPABILITIES;
	} else if (os_strncasecmp(*cmd, TWT_SET_PARAM_STR,
				  TWT_SET_PARAM_STR_LEN) == 0) {
		*cmd += (TWT_SET_PARAM_STR_LEN + 1);
		return QCA_WLAN_TWT_SET_PARAM;
	} else {
		wpa_printf(MSG_DEBUG, "Not a TWT command");
		return TWT_CMD_NOT_EXIST;
	}
}

static u64 get_u64_from_string(char *cmd_string, int *ret)
{
	u64 val = 0;
	char *cmd = cmd_string;

	while (*cmd != ' ')
		cmd++;

	*ret = 0;
	errno = 0;
	val = strtoll(cmd_string, NULL, 10);
	if (errno == ERANGE || (errno != 0 && val == 0)) {
		wpa_printf(MSG_ERROR, "invalid value");
		*ret = -EINVAL;
        }
	return val;
}


static u32 get_u32_from_string(char *cmd_string, int *ret)
{
	u32 val = 0;
	char *cmd = cmd_string;

	while (*cmd != ' ')
		cmd++;

	*ret = 0;
	errno = 0;
	val = strtol(cmd_string, NULL, 10);
	if (errno == ERANGE || (errno != 0 && val == 0)) {
		wpa_printf(MSG_ERROR, "invalid value");
		*ret = -EINVAL;
        }
	return val;
}

static u8 get_u8_from_string(char *cmd_string, int *ret)
{
	char *cmd = cmd_string;
	u8 val = 0;

	while (*cmd != ' ')
		cmd++;

	*ret = 0;
	errno = 0;
	val = strtol(cmd_string, NULL, 10) & 0xFF;
	if (errno == ERANGE || (errno != 0 && val == 0)) {
		wpa_printf(MSG_ERROR, "invalid value");
		*ret = -EINVAL;
        }
	return val;
}

char *move_to_next_str(char *cmd)
{
	if (*cmd == '\0')
		return cmd;

	while (*cmd != ' ') {
		cmd++;
		if (*cmd == '\0')
			return cmd;
	}

	while (*cmd == ' ')
		cmd++;

	return cmd;
}

static int is_binary(u8 value) {
	if(value == 0 || value == 1)
		return 0;
	return -1;
}

static
void print_setup_cmd_values(struct twt_setup_parameters *twt_setup_params)
{
	wpa_printf(MSG_DEBUG, "TWT: setup dialog_id: %x",
		   twt_setup_params->dialog_id);
	wpa_printf(MSG_DEBUG, "TWT: setup req type: %d ",
		   twt_setup_params->req_type);
	wpa_printf(MSG_DEBUG, "TWT: setup trig type: %d ",
		   twt_setup_params->trig_type);
	wpa_printf(MSG_DEBUG, "TWT: setup flow type: 0x%x",
		   twt_setup_params->flow_type);
	wpa_printf(MSG_DEBUG, "TWT: setup wake exp: 0x%x",
		   twt_setup_params->wake_intr_exp);
	wpa_printf(MSG_DEBUG, "TWT: setup protection: 0x%x",
		   twt_setup_params->protection);
	wpa_printf(MSG_DEBUG, "TWT: setup wake time: 0x%x",
		   twt_setup_params->wake_time);
	wpa_printf(MSG_DEBUG, "TWT: setup wake dur: 0x%x",
		   twt_setup_params->wake_dur);
	wpa_printf(MSG_DEBUG, "TWT: setup wake intr mantissa: 0x%x",
		   twt_setup_params->wake_intr_mantissa);
	wpa_printf(MSG_DEBUG, "TWT: setup bcast: %d ",
		   twt_setup_params->bcast);
	wpa_printf(MSG_DEBUG, "TWT: min wake intvl: %d ",
		   twt_setup_params->min_wake_intvl);
	wpa_printf(MSG_DEBUG, "TWT: max wake intvl: %d ",
		   twt_setup_params->max_wake_intvl);
	wpa_printf(MSG_DEBUG, "TWT: min wake duration: %d ",
		   twt_setup_params->min_wake_duration);
	wpa_printf(MSG_DEBUG, "TWT: max wake duration: %d ",
		   twt_setup_params->max_wake_duration);
	wpa_printf(MSG_DEBUG, "TWT: wake tsf: 0x%lx ",
		   twt_setup_params->wake_tsf);
	wpa_printf(MSG_DEBUG, "TWT: announce timeout(in us): %u",
		   twt_setup_params->announce_timeout_us);
}

static int check_cmd_input(char *cmd_string)
{
	u32 cmd_string_len;

	if (!cmd_string) {
		wpa_printf(MSG_ERROR, "cmd string null");
		return -EINVAL;
	}

	cmd_string_len = strlen(cmd_string);

	wpa_printf(MSG_DEBUG, "TWT: cmd string - %s len = %u", cmd_string,
		   cmd_string_len);
	if (cmd_string_len < DIALOG_ID_STR_LEN + SINGLE_SPACE_LEN +
			     SINGLE_DIGIT_LEN) {
		wpa_printf(MSG_ERROR, "TWT: Dialog_id parameter missing");
		return -EINVAL;
	}

	return 0;
}

/**
 * process_twt_setup_cmd_string() - Process a TWT setup command string
 * @cmd: Command string
 * @twt_setup_params: TWT setup parameters to fill
 *
 * This function parses the TWT setup command and populates twt_setup_params.
 *
 * Return: 0 on successs, error if invalid params are found
 *
 */
static
int process_twt_setup_cmd_string(char *cmd,
				 struct twt_setup_parameters *twt_setup_params)
{
	int ret = 0;

	if (!twt_setup_params) {
		wpa_printf(MSG_ERROR, "cmd or twt_setup_params null");
		return -EINVAL;
	}

	if (check_cmd_input(cmd))
		return -EINVAL;

	wpa_printf(MSG_DEBUG, "process twt setup command string: %s", cmd);
	while (*cmd == ' ')
		cmd++;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) == 0) {
		cmd += (DIALOG_ID_STR_LEN + 1);
		twt_setup_params->dialog_id = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (os_strncasecmp(cmd, REQ_TYPE_STR, REQ_TYPE_STR_LEN) == 0) {
		cmd += (REQ_TYPE_STR_LEN + 1);
		twt_setup_params->req_type = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (os_strncasecmp(cmd, TRIG_TYPE_STR, TRIG_TYPE_STR_LEN) == 0) {
		cmd += (TRIG_TYPE_STR_LEN + 1);
		twt_setup_params->trig_type = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;

		if (is_binary(twt_setup_params->trig_type)) {
			wpa_printf(MSG_ERROR, "Invalid trigger type");
			return -EINVAL;
		}
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, FLOW_TYPE_STR, FLOW_TYPE_STR_LEN) == 0) {
		cmd += (FLOW_TYPE_STR_LEN + 1);
		twt_setup_params->flow_type = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;

		if (is_binary(twt_setup_params->flow_type)) {
			wpa_printf(MSG_ERROR, "Invalid flow type");
			return -EINVAL;
		}
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, WAKE_INTR_EXP_STR, WAKE_INTR_EXP_STR_LEN) == 0) {
		cmd += (WAKE_INTR_EXP_STR_LEN + 1);
		twt_setup_params->wake_intr_exp = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;

		if (twt_setup_params->wake_intr_exp >
		    TWT_SETUP_WAKE_INTVL_EXP_MAX) {
			wpa_printf(MSG_DEBUG, "Invalid wake_intr_exp %u",
				   twt_setup_params->wake_intr_exp);
			return -EINVAL;
		}
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, PROTECTION_STR, PROTECTION_STR_LEN) == 0) {
		cmd += (PROTECTION_STR_LEN + 1);
		twt_setup_params->protection = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;

		if (is_binary(twt_setup_params->protection)) {
			wpa_printf(MSG_ERROR, "Invalid protection value");
			return -EINVAL;
		}
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, WAKE_TIME_STR, WAKE_TIME_STR_LEN) == 0) {
		cmd += (WAKE_TIME_STR_LEN + 1);
		twt_setup_params->wake_time = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, WAKE_DUR_STR, WAKE_DUR_STR_LEN) == 0) {
		cmd += (WAKE_DUR_STR_LEN + 1);
		twt_setup_params->wake_dur = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;

		if (twt_setup_params->wake_dur == 0 ||
		    twt_setup_params->wake_dur > TWT_SETUP_WAKE_DURATION_MAX) {
			wpa_printf(MSG_ERROR, "Invalid wake_dura_us %u",
				   twt_setup_params->wake_dur);
			return -EINVAL;
		}

		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, WAKE_INTR_MANTISSA_STR,
		    WAKE_INTR_MANTISSA_STR_LEN) == 0) {
		cmd += (WAKE_INTR_MANTISSA_STR_LEN + 1);
		twt_setup_params->wake_intr_mantissa = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		if (twt_setup_params->wake_intr_mantissa >
		    TWT_SETUP_WAKE_INTVL_MANTISSA_MAX) {
			wpa_printf(MSG_ERROR, "Invalid wake_intr_mantissa %u",
				   twt_setup_params->wake_intr_mantissa);
			return -EINVAL;
		}

		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, BROADCAST_STR, BROADCAST_STR_LEN) == 0) {
		cmd += (BROADCAST_STR_LEN + 1);
		twt_setup_params->bcast = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;

		if (is_binary(twt_setup_params->bcast)) {
			wpa_printf(MSG_ERROR, "Invalid broadcast value");
			return -EINVAL;
		}
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, MIN_WAKE_INTVL_STR, MIN_WAKE_INTVL_STR_LEN) == 0) {
		cmd += (MIN_WAKE_INTVL_STR_LEN + 1);
		twt_setup_params->min_wake_intvl = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, MAX_WAKE_INTVL_STR, MAX_WAKE_INTVL_STR_LEN) == 0) {
		cmd += (MAX_WAKE_INTVL_STR_LEN + 1);
		twt_setup_params->max_wake_intvl = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, MIN_WAKE_DUR_STR, MIN_WAKE_DUR_STR_LEN) == 0) {
		cmd += (MIN_WAKE_DUR_STR_LEN + 1);
		twt_setup_params->min_wake_duration = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, MAX_WAKE_DUR_STR, MAX_WAKE_DUR_STR_LEN) == 0) {
		cmd += (MAX_WAKE_DUR_STR_LEN + 1);
		twt_setup_params->max_wake_duration = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, WAKE_TSF_STR, WAKE_TSF_STR_LEN) == 0) {
		cmd += (WAKE_TSF_STR_LEN + 1);
		twt_setup_params->wake_tsf = get_u64_from_string(cmd, &ret);
		if(ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	if (strncmp(cmd, ANNOUNCE_TIMEOUT_STR, ANNOUNCE_TIMEOUT_STR_LEN) == 0) {
		cmd += (ANNOUNCE_TIMEOUT_STR_LEN + 1);
		twt_setup_params->announce_timeout_us =
					get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	print_setup_cmd_values(twt_setup_params);

	return 0;
}

static
int prepare_twt_setup_nlmsg(struct nl_msg *nlmsg,
			    struct twt_setup_parameters *twt_setup_params)
{
	struct nlattr *twt_attr;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_SET)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put QCA_WLAN_TWT_SET");
		goto fail;
	}

	twt_attr = nla_nest_start(nlmsg,
				  QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (twt_attr == NULL)
		goto fail;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID,
		       twt_setup_params->dialog_id)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
		goto fail;
	}

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_REQ_TYPE,
		       twt_setup_params->req_type)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put req type");
		goto fail;
	}

	if (twt_setup_params->trig_type) {
		if (nla_put_flag(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_TRIGGER)
				 ) {
			wpa_printf(MSG_DEBUG, "TWT: Failed to put trig type");
			goto fail;
		}
	}

	/*0 - Announced/ 1 - Unannounced*/
	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_TYPE,
		       twt_setup_params->flow_type)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put flow type");
		goto fail;
	}

	if (nla_put_u8(nlmsg,
		       QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL_EXP,
		       twt_setup_params->wake_intr_exp)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put wake exp");
		goto fail;
	}

	if (twt_setup_params->protection) {
		if (nla_put_flag(nlmsg,
		    QCA_WLAN_VENDOR_ATTR_TWT_SETUP_PROTECTION)) {
			wpa_printf(MSG_DEBUG,
				   "TWT: Failed to add protection");
			goto fail;
		}
	}

	/*offset to add with TBTT after which 1st SP will start*/
	if (nla_put_u32(nlmsg,
			QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_TIME,
			twt_setup_params->wake_time)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put wake time");
		goto fail;
	}

	/*TWT Wake Duration in units of us, must be <= 65280*/
	if (nla_put_u32(nlmsg,
			QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_DURATION,
			twt_setup_params->wake_dur)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put wake dur");
		goto fail;
	}

	if (nla_put_u32(nlmsg,
		QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL2_MANTISSA,
		twt_setup_params->wake_intr_mantissa)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put wake intr mantissa");
		goto fail;
	}

	if (nla_put_u32(nlmsg,
		QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL_MANTISSA,
		twt_setup_params->wake_intr_mantissa/TWT_WAKE_INTERVAL_TU_FACTOR)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put wake intr mantissa");
		goto fail;
	}

	if (twt_setup_params->bcast) {
		if (nla_put_flag(nlmsg,
			QCA_WLAN_VENDOR_ATTR_TWT_SETUP_BCAST)) {
			wpa_printf(MSG_DEBUG, "TWT: Failed to put bcast");
			goto fail;
		}
	}

	if (nla_put_u32(nlmsg,
		QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MIN_WAKE_INTVL,
		twt_setup_params->min_wake_intvl)) {
		wpa_printf(MSG_ERROR, "TWT: Failed to put min wake intr ");
		goto fail;
	}

	if (nla_put_u32(nlmsg,
		QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX_WAKE_INTVL,
		twt_setup_params->max_wake_intvl)) {
		wpa_printf(MSG_ERROR,"TWT: Failed to put max wake intr");
		goto fail;
	}

	if (nla_put_u32(nlmsg,
		QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MIN_WAKE_DURATION,
		twt_setup_params->min_wake_duration)) {
		wpa_printf(MSG_ERROR,"TWT: Failed to put min wake dur");
		goto fail;
	}

	if (nla_put_u32(nlmsg,
		QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX_WAKE_DURATION,
		twt_setup_params->max_wake_duration)) {
		wpa_printf(MSG_ERROR,"TWT: Failed to put max wake dur");
		goto fail;
	}

	if (twt_setup_params->wake_tsf) {
		if (nla_put_u64(nlmsg,
			QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_TIME_TSF,
			twt_setup_params->wake_tsf)) {
			wpa_printf(MSG_ERROR,"TWT: Failed to put wake time tsf value");
			goto fail;
		}
	}

	if (twt_setup_params->announce_timeout_us) {
		if (nla_put_u32(nlmsg,
		   QCA_WLAN_VENDOR_ATTR_TWT_SETUP_ANNOUNCE_TIMEOUT,
		   twt_setup_params->announce_timeout_us)) {
			wpa_printf(MSG_ERROR, "TWT: Failed to put announce timeout value");
			goto fail;
		}
	}

	nla_nest_end(nlmsg, twt_attr);
	wpa_printf(MSG_DEBUG, "TWT: setup command nla end");
	return 0;

fail:
	return -EINVAL;
}

static int prepare_twt_terminate_nlmsg(struct nl_msg *nlmsg, char *cmd)
{
	u8 dialog_id;
	struct nlattr *twt_attr;
	int ret = 0;

	if(check_cmd_input(cmd))
		return -EINVAL;

	while(*cmd == ' ')
		cmd++;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) == 0) {
		cmd += (DIALOG_ID_STR_LEN + 1);
		dialog_id = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
	} else {
		wpa_printf(MSG_ERROR, "TWT: no dialog_id found");
		goto fail;
	}

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_TERMINATE)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put QCA_WLAN_TWT_TERMINATE");
		goto fail;
	}

	twt_attr = nla_nest_start(nlmsg,
				  QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (twt_attr == NULL)
		goto fail;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID,
		       dialog_id)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
		goto fail;
	}

	nla_nest_end(nlmsg, twt_attr);
	wpa_printf(MSG_DEBUG, "TWT: terminate sent with dialog_id: %x",
		   dialog_id);

	return 0;
fail:
	return -EINVAL;
}

static int prepare_twt_pause_nlmsg(struct nl_msg *nlmsg, char *cmd)
{
	u8 dialog_id;
	struct nlattr *twt_attr;
	int ret = 0;

	if(check_cmd_input(cmd))
		return -EINVAL;

	while(*cmd == ' ')
		cmd++;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) == 0) {
		cmd += (DIALOG_ID_STR_LEN + 1);
		dialog_id = get_u8_from_string(cmd, &ret);
		if(ret < 0)
			return ret;
	} else {
		wpa_printf(MSG_ERROR, "TWT: no dialog_id found");
		goto fail;
	}

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_SUSPEND)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put QCA_WLAN_TWT_TERMINATE");
		goto fail;
	}

	twt_attr = nla_nest_start(nlmsg,
				QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (twt_attr == NULL)
		goto fail;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID,
		       dialog_id)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
		goto fail;
	}

	nla_nest_end(nlmsg, twt_attr);
	wpa_printf(MSG_DEBUG, "TWT: pause sent with dialog_id: %x", dialog_id);

	return 0;
fail:
	return -EINVAL;
}

/**
 * process_twt_resume_cmd_string() - Process a TWT resume command string
 * @cmd: Command string
 * @resume_params: TWT resume parameters to fill
 *
 * This function parses the TWT resume command and populates resume_params.
 *
 * Return: 0 on successs, error if invalid params are found
 *
 */
static
int process_twt_resume_cmd_string(char *cmd,
				  struct twt_resume_parameters *resume_params)
{
	int ret = 0;

	if (!resume_params) {
		wpa_printf(MSG_ERROR, "TWT: cmd or resume_params null");
		return -EINVAL;
	}

	if(check_cmd_input(cmd))
		return -EINVAL;

	while(*cmd == ' ')
		cmd++;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) != 0) {
		wpa_printf(MSG_ERROR, "TWT: dialog ID parameter is required");
		return -EINVAL;
	}
	cmd += (DIALOG_ID_STR_LEN + 1);
	resume_params->dialog_id = get_u8_from_string(cmd, &ret);
	if (ret < 0)
		return ret;
	cmd = move_to_next_str(cmd);

	if (os_strncasecmp(cmd, NEXT_TWT_STR, NEXT_TWT_STR_LEN) == 0) {
		cmd += (NEXT_TWT_STR_LEN + 1);
		resume_params->next_twt = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		wpa_printf(MSG_DEBUG, "TWT: NEXT TWT %d", resume_params->next_twt);
		cmd = move_to_next_str(cmd);
	}

	if (os_strncasecmp(cmd, NEXT2_TWT_STR, NEXT2_TWT_STR_LEN) == 0) {
		cmd += (NEXT2_TWT_STR_LEN + 1);
		resume_params->next2_twt = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		wpa_printf(MSG_DEBUG, "TWT: NEXT2 TWT %d", resume_params->next2_twt);
		cmd = move_to_next_str(cmd);
	}

	if (os_strncasecmp(cmd, NEXT_TWT_SIZE_STR, NEXT_TWT_SIZE_STR_LEN) != 0) {
		wpa_printf(MSG_ERROR, "TWT: next_twt_size parameter is required");
		return -EINVAL;
	}
	cmd += (NEXT_TWT_SIZE_STR_LEN + 1);
	resume_params->next_twt_size = get_u32_from_string(cmd, &ret);
	if (ret < 0)
		return ret;

	return 0;
}

static
int prepare_twt_resume_nlmsg(struct nl_msg *nlmsg,
			     struct twt_resume_parameters *resume_params)
{
	struct nlattr *twt_attr;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_RESUME)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put QCA_WLAN_TWT_RESUME");
		return -EINVAL;
	}

	twt_attr = nla_nest_start(nlmsg,QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (twt_attr == NULL)
		return -EINVAL;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_RESUME_FLOW_ID,
		       resume_params->dialog_id)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
		return -EINVAL;
	}
	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_RESUME_NEXT_TWT,
		       resume_params->next_twt)) {
		wpa_printf(MSG_DEBUG, "TWT: next_twt");
		return -EINVAL;
	}
	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_RESUME_NEXT2_TWT,
			resume_params->next2_twt)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put next2_twt");
		return -EINVAL;
	}
	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_RESUME_NEXT_TWT_SIZE,
			resume_params->next_twt_size)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put next_twt_size");
		return -EINVAL;
	}
	nla_nest_end(nlmsg, twt_attr);

	wpa_printf(MSG_DEBUG,"TWT: resume dialog_id: 0x%x next_twt (us): 0x%x next2_twt (us): 0x%x next_twt_size: %u",
		   resume_params->dialog_id, resume_params->next_twt,
		   resume_params->next2_twt,resume_params->next_twt_size);

	return 0;
}

/**
 * process_twt_nudge_cmd_string()- processes command string for nudge command.
 *
 * @Param cmd: expects the command
 * @Param nudge_params: return parsed nudge parameters
 *
 * @Returns 0 on Success, -EINVAL on Failure
 */
static
int process_twt_nudge_cmd_string(char *cmd,
				 struct twt_nudge_parameters *nudge_params)
{
	int ret = 0;

	if (!nudge_params) {
		wpa_printf(MSG_ERROR, "TWT: nudge_params null");
		return -EINVAL;
	}

	if(check_cmd_input(cmd))
		return -EINVAL;

	while(*cmd == ' ')
		cmd++;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) != 0) {
		wpa_printf(MSG_ERROR, "TWT: dialog_id parameter is required");
		return -EINVAL;
	}
	cmd += (DIALOG_ID_STR_LEN + 1);
	nudge_params->dialog_id = get_u8_from_string(cmd, &ret);
	if (ret < 0)
		return ret;
	cmd = move_to_next_str(cmd);

	if (os_strncasecmp(cmd, PAUSE_DURATION_STR, PAUSE_DURATION_STR_LEN) == 0) {
		cmd += (PAUSE_DURATION_STR_LEN + 1);
		nudge_params->wake_time = get_u32_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		wpa_printf(MSG_DEBUG, "TWT: WAKE TIME %d", nudge_params->wake_time);
		cmd = move_to_next_str(cmd);
	}

	if (os_strncasecmp(cmd, NEXT_TWT_SIZE_STR, NEXT_TWT_SIZE_STR_LEN) != 0) {
		wpa_printf(MSG_ERROR, "TWT: next_twt_size parameter is required");
		return -EINVAL;
	}
	cmd += (NEXT_TWT_SIZE_STR_LEN + 1);
	nudge_params->next_twt_size = get_u32_from_string(cmd, &ret);
	if (ret < 0)
		return ret;

	return 0;
}

/**
 * prepare_twt_nudge_nlmsg()- prepare twt_session_nudge command .
 *
 * @Param nlmsg: nl command buffer
 * @Param nudge_params: nudge parameters to prepare command
 *
 * @Returns 0 on Success, -EINVAL on Failure
 */
static
int prepare_twt_nudge_nlmsg(struct nl_msg *nlmsg,
			    struct twt_nudge_parameters *nudge_params)
{
	struct nlattr *twt_attr;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_NUDGE)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put twt operation");
		return -EINVAL;
	}

	twt_attr = nla_nest_start(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (twt_attr == NULL)
		return -EINVAL;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_NUDGE_FLOW_ID,
		       nudge_params->dialog_id)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
		return -EINVAL;
	}

	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_NUDGE_WAKE_TIME,
		        nudge_params->wake_time)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put wake_time");
		return -EINVAL;
	}

	if (nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_NUDGE_NEXT_TWT_SIZE,
			nudge_params->next_twt_size)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put next_twt_size");
		return -EINVAL;
	}
	nla_nest_end(nlmsg, twt_attr);

	wpa_printf(MSG_DEBUG,"TWT: nudge dialog_id: 0x%x wake_time(us): 0x%x next_twt_size: %u",
		   nudge_params->dialog_id, nudge_params->wake_time,
		   nudge_params->next_twt_size);

	return 0;
}

/**
 * process_twt_set_param_cmd_string()- processes command
 * string for set parameters command.
 *
 * @Param cmd: expects the command
 * @Param set_params: return parsed TWT set parameters
 *
 * @Returns 0 on Success, -EINVAL on Failure
 */
static
int process_twt_set_param_cmd_string(char *cmd,
				     struct twt_set_parameters *set_params)
{
	int ret = -EINVAL;

	if (!set_params) {
		wpa_printf(MSG_ERROR, "TWT: set_params null");
		return -EINVAL;
	}

	if (check_cmd_input(cmd))
		return -EINVAL;

	while (*cmd == ' ')
		cmd++;

	if (os_strncasecmp(cmd, AP_AC_VALUE_STR, AP_AC_VALUE_STR_LEN) == 0) {
		cmd += (AP_AC_VALUE_STR_LEN + 1);
		set_params->ap_ac_value = get_u8_from_string(cmd, &ret);
		wpa_printf(MSG_DEBUG, "TWT: AP AC VALUE: %d", set_params->ap_ac_value);
		if (ret < 0)
			return ret;
		cmd = move_to_next_str(cmd);
	}

	return ret;
}

/**
 * prepare_twt_set_param_nlmsg()- prepare twt_set_param command .
 *
 * @Param nlmsg: nl command buffer
 * @Param set_params: set parameters to prepare command
 *
 * @Returns 0 on Success, -EINVAL on Failure
 */
static
int prepare_twt_set_param_nlmsg(struct nl_msg *nlmsg,
			        struct twt_set_parameters *set_params)
{
	struct nlattr *twt_attr;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_SET_PARAM)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put twt operation");
		return -EINVAL;
	}

	twt_attr = nla_nest_start(nlmsg,
				  QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (!twt_attr)
		return -EINVAL;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SET_PARAM_AP_AC_VALUE,
		set_params->ap_ac_value)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put ap_ac_value");
		return -EINVAL;
	}

	nla_nest_end(nlmsg, twt_attr);

	wpa_printf(MSG_DEBUG, "TWT: set parameters -  ap_ac_value: %d",
		   set_params->ap_ac_value);

	return 0;
}

/**
 * prepare_twt_clear_stats_nlmsg()- prepare twt_session_clear_stats command.
 *
 * @Param nlmsg: nl command buffer
 * @Param cmd: command string
 *
 * @Returns 0 on Success, -EINVAL on Failure
 */
static int prepare_twt_clear_stats_nlmsg(struct nl_msg *nlmsg, char *cmd)
{
	struct nlattr *twt_attr;
	u8 dialog_id;
	int ret = 0;

	while(*cmd == ' ')
		cmd++;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_CLEAR_STATS)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put QCA_WLAN_TWT_CLEAR_STATS");
		goto fail;
	}

	twt_attr = nla_nest_start(nlmsg,
				  QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (!twt_attr)
		return -EINVAL;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) == 0) {
		cmd += DIALOG_ID_STR_LEN + 1;

		dialog_id = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_STATS_FLOW_ID,
			       dialog_id)) {
			wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
			return -EINVAL;
		}
		wpa_printf(MSG_DEBUG, "TWT: clear_stats dialog_id:%d", dialog_id);
		cmd = move_to_next_str(cmd);
	} else {
		wpa_printf(MSG_DEBUG, "TWT: dialog_id not found");
		goto fail;
	}

	nla_nest_end(nlmsg, twt_attr);
	return 0;
fail:
	return -EINVAL;
}

/**
 * prepare_twt_get_stats_nlmsg()- prepare twt_session_get_stats command.
 *
 * @Param nlmsg: nl command buffer
 * @Param cmd: command string
 *
 * @Returns 0 on Success, -EINVAL on Failure
 */
static int prepare_twt_get_stats_nlmsg(struct nl_msg *nlmsg, char *cmd)
{
	struct nlattr *twt_attr;
	u8 dialog_id;
	int ret = 0;

	while(*cmd == ' ')
		cmd++;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_GET_STATS)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put QCA_WLAN_TWT_GET_STATS");
		goto fail;
	}

	twt_attr = nla_nest_start(nlmsg,
				  QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (!twt_attr)
		return -EINVAL;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) == 0) {
		cmd += DIALOG_ID_STR_LEN + 1;

		dialog_id = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;
		if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_STATS_FLOW_ID,
			       dialog_id)) {
			wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
			return -EINVAL;
		}
		wpa_printf(MSG_DEBUG, "TWT: get_stats dialog_id:%d", dialog_id);
		cmd = move_to_next_str(cmd);
	} else {
		wpa_printf(MSG_DEBUG, "TWT: dialog_id not found");
		goto fail;
	}

	nla_nest_end(nlmsg, twt_attr);
	return 0;
fail:
	return -EINVAL;
}

static int prepare_twt_get_params_nlmsg(struct nl_msg *nlmsg, char *cmd)
{
	struct nlattr *twt_attr;
	u8 dialog_id;
	int ret = 0;
	uint8_t peer_mac[MAC_ADDR_LEN];

	while(*cmd == ' ')
		cmd++;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_GET)) {
		wpa_printf(MSG_DEBUG, "TWT: Failed to put QCA_WLAN_TWT_GET");
		goto fail;
	}

	twt_attr = nla_nest_start(nlmsg,
				  QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (!twt_attr)
		return -EINVAL;

	if (os_strncasecmp(cmd, DIALOG_ID_STR, DIALOG_ID_STR_LEN) == 0) {
		cmd += DIALOG_ID_STR_LEN + 1;

		dialog_id = get_u8_from_string(cmd, &ret);
		if (ret < 0)
			return ret;

		if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID,
			       dialog_id)) {
			wpa_printf(MSG_DEBUG, "TWT: Failed to put dialog_id");
			return -EINVAL;
		}
		wpa_printf(MSG_DEBUG, "TWT: get_param dialog_id:%d", dialog_id);
		cmd = move_to_next_str(cmd);
	} else {
		wpa_printf(MSG_ERROR, "TWT: dialog_id not found");
		goto fail;
	}


	if (os_strncasecmp(cmd, MAC_ADDRESS_STR, MAC_ADDR_STR_LEN) == 0) {
		cmd += MAC_ADDR_STR_LEN + 1;

		if (convert_string_to_bytes(peer_mac, cmd, MAC_ADDR_LEN) !=
		    MAC_ADDR_LEN) {
			wpa_printf(MSG_ERROR, "TWT: invalid mac address");
			goto fail;
		}

	        if (nla_put(nlmsg, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAC_ADDR,
			    MAC_ADDR_LEN, peer_mac)) {
			wpa_printf(MSG_ERROR, "TWT: Failed to put mac address");
			goto fail;
	        }
	}

	nla_nest_end(nlmsg, twt_attr);
	return 0;
fail:
	return -EINVAL;
}

/**
* prepare_twt_get_cap_nlmsg()- processes and packs get capability command.
* The command is expected in below format:
* TWT_GET_CAP
*
* @Param nlmsg: stores the nlmsg
* @Param cmd: expects the command
*
* @Returns 0 on Success, -EINVAL on Failure
*/
static int prepare_twt_get_cap_nlmsg(struct nl_msg *nlmsg, char *cmd)
{
	struct nlattr *twt_attr;

	if (nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION,
		       QCA_WLAN_TWT_GET_CAPABILITIES)) {
		wpa_printf(MSG_ERROR,"TWT: Failed to put QCA_WLAN_TWT_GET_CAPABILITIES");
		goto fail;
	}

	twt_attr = nla_nest_start(nlmsg,
				  QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS);
	if (twt_attr == NULL)
		goto fail;
	nla_nest_end(nlmsg, twt_attr);
	return 0;
fail:
	return -EINVAL;
}

static int pack_nlmsg_twt_params(struct nl_msg *twt_nl_msg, char *cmd,
				 enum qca_wlan_twt_operation type)
{
	struct nlattr *attr;
	int ret = 0;

	attr = nla_nest_start(twt_nl_msg, NL80211_ATTR_VENDOR_DATA);
	if (attr == NULL)
		return -EINVAL;

	switch (type) {
	case QCA_WLAN_TWT_SET:
	{
		struct twt_setup_parameters setup_params = {0};

		if (process_twt_setup_cmd_string(cmd, &setup_params))
			return -EINVAL;
		ret = prepare_twt_setup_nlmsg(twt_nl_msg, &setup_params);
		break;
	}
	case QCA_WLAN_TWT_TERMINATE:
		ret = prepare_twt_terminate_nlmsg(twt_nl_msg, cmd);
		break;
	case QCA_WLAN_TWT_SUSPEND:
		ret = prepare_twt_pause_nlmsg(twt_nl_msg, cmd);
		break;
	case QCA_WLAN_TWT_RESUME:
	{
		struct twt_resume_parameters resume_params = {0};

		if (process_twt_resume_cmd_string(cmd, &resume_params))
			return -EINVAL;
		ret = prepare_twt_resume_nlmsg(twt_nl_msg, &resume_params);
		break;
	}
	case QCA_WLAN_TWT_NUDGE:
	{
		struct twt_nudge_parameters nudge_params = {0};

		if (process_twt_nudge_cmd_string(cmd, &nudge_params))
			return -EINVAL;
		ret = prepare_twt_nudge_nlmsg(twt_nl_msg, &nudge_params);
		break;
	}
	case QCA_WLAN_TWT_SET_PARAM:
	{
		struct twt_set_parameters set_params = {0};

		if (process_twt_set_param_cmd_string(cmd, &set_params))
			return -EINVAL;
		ret = prepare_twt_set_param_nlmsg(twt_nl_msg, &set_params);
		break;
	}
	case QCA_WLAN_TWT_GET_CAPABILITIES:
		ret = prepare_twt_get_cap_nlmsg(twt_nl_msg, cmd);
		break;
	case QCA_WLAN_TWT_CLEAR_STATS:
		ret = prepare_twt_clear_stats_nlmsg(twt_nl_msg, cmd);
		break;
	case QCA_WLAN_TWT_GET_STATS:
		ret = prepare_twt_get_stats_nlmsg(twt_nl_msg, cmd);
		break;
	case QCA_WLAN_TWT_GET:
		ret = prepare_twt_get_params_nlmsg(twt_nl_msg, cmd);
		break;
	default:
		wpa_printf(MSG_DEBUG, "Unsupported command: %d", type);
		ret = -EINVAL;
		break;
	}

	if (!ret)
		nla_nest_end(twt_nl_msg, attr);

	return ret;
}

char *result_copy_to_buf(char *src, char *dst_buf, int *dst_len)
{
	int str_len, remaining = 0;

	remaining = *dst_len;
	str_len = strlen(src);
	remaining = remaining - (str_len + 1);

	if (remaining <= 0) {
		wpa_printf(MSG_ERROR, "destination buffer length not enough");
		return NULL;
	}
	os_memcpy(dst_buf, src, str_len);

	*dst_len = remaining;
	*(dst_buf + str_len) = ' ';

	return (dst_buf + str_len + 1);
}

static int
unpack_twt_get_params_resp(struct nlattr **tb, char *buf, int buf_len)
{
	int cmd_id, val, len;
	uint8_t exp;
	uint32_t value;
	unsigned long wake_tsf;
	char temp[TWT_RESP_BUF_LEN];
	char *start = buf;

	os_memset(temp, 0, TWT_RESP_BUF_LEN);

	/* Mac Address */
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAC_ADDR;
	if (!tb[cmd_id]) {
		wpa_printf(MSG_ERROR, "twt_get_params resp: no mac_addr");
		return -EINVAL;
	}
	os_snprintf(temp, TWT_RESP_BUF_LEN, "<mac_addr " MAC_ADDR_STR,
		    MAC_ADDR_ARRAY((char *)nla_data(tb[cmd_id])));
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* Flow Id */
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID;
	if (!tb[cmd_id]) {
		wpa_printf(MSG_ERROR, "twt_get_params resp: no dialog_id");
		return -EINVAL;
	}
	val = nla_get_u8(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "dialog_id %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;


	/* Broadcast */
	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_BCAST;
	if (tb[cmd_id])
		val = nla_get_flag(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "bcast %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* Trigger Type */
	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_TRIGGER;
	if (tb[cmd_id])
		val = nla_get_flag(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "trig_type %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* Announce */
	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_TYPE;
	if (tb[cmd_id])
		val = nla_get_flag(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "flow_type %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* Protection */
	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_PROTECTION;
	if (tb[cmd_id])
		val = nla_get_flag(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "protection %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* info */
	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_TWT_INFO_ENABLED;
	if (tb[cmd_id])
		val = nla_get_flag(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "info_enabled %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* Wake Duration */
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_DURATION;
	if (!tb[cmd_id]) {
		wpa_printf(MSG_ERROR, "twt_get_params resp: no wake duration");
		return -EINVAL;
	}
	value = nla_get_u32(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_dur %d", value);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* Wake Interval Mantissa */
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL2_MANTISSA;
	if (!tb[cmd_id]) {
		cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL_MANTISSA;
		if (!tb[cmd_id]) {
			wpa_printf(MSG_ERROR, "twt_get_params resp: no wake mantissa");
			return -EINVAL;
		}
		value = nla_get_u32(tb[cmd_id]);
		value = value * TWT_WAKE_INTERVAL_TU_FACTOR;
	} else {
		value = nla_get_u32(tb[cmd_id]);
	}
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_intvl_mantis %d", value);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* Wake Interval Exponent */
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL_EXP;
	if (!tb[cmd_id]) {
		wpa_printf(MSG_ERROR, "twt_get_params resp: no wake intvl exp");
		return -EINVAL;
	}
	exp = nla_get_u8(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_intvl_exp %d", exp);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* TSF Value */
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_TIME_TSF;
	if (!tb[cmd_id]) {
		wpa_printf(MSG_ERROR, "twt_get_params resp: no wake time tsf");
		return -EINVAL;
	}
	wake_tsf = nla_get_u64(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_time_tsf 0x%lx>", wake_tsf);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_STATE;
	if (!tb[cmd_id]) {
		wpa_printf(MSG_ERROR, "twt_get_params resp: no state info");
		return -EINVAL;
	}
	value = nla_get_u32(tb[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "state %d", value);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_RESPONDER_PM_MODE;
	if (tb[cmd_id]) {
		val = nla_get_u8(tb[cmd_id]);

		os_snprintf(temp, TWT_RESP_BUF_LEN, "responder_pm %d", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}

	len = (buf - start);
	*buf = '\0';

	return len;
}

static int wpa_get_twt_setup_resp_val(struct nlattr **tb2, char *buf,
				      int buf_len)
{
	uint32_t wake_intvl_exp = 0, wake_intvl_mantis = 0;
	int cmd_id, val;
	uint32_t value;
	unsigned long wake_tsf;
	char temp[TWT_RESP_BUF_LEN];

	buf = result_copy_to_buf(TWT_SETUP_RESP, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	os_memset(temp, 0, TWT_RESP_BUF_LEN);
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "TWT dialog id missing");
		return -EINVAL;
	}

	val = nla_get_u8(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "dialog_id %d ", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_STATUS;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "TWT resp status missing");
		return -EINVAL;
	}

	val = nla_get_u8(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "status %d ", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	os_memset(temp, 0, TWT_RESP_BUF_LEN);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "(%s)", twt_status_to_string(val));
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	/* In case of failure only status is updated */
	if (val != QCA_WLAN_VENDOR_TWT_STATUS_OK) {
		*buf = '\0';
		return 0;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_RESP_TYPE;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "TWT resp type missing");
		return -EINVAL;
	}
	val = nla_get_u8(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "resp_reason %d ", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL_EXP;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "TWT_SETUP_WAKE_INTVL_EXP is must");
		return -EINVAL;
	}
	wake_intvl_exp = nla_get_u8(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_intvl_exp %d ",
		    wake_intvl_exp);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_BCAST;
	if (tb2[cmd_id])
		val = nla_get_flag(tb2[cmd_id]);

	os_snprintf(temp, TWT_RESP_BUF_LEN, "bcast %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_TRIGGER;
	if (tb2[cmd_id])
		val = nla_get_flag(tb2[cmd_id]);

	os_snprintf(temp, TWT_RESP_BUF_LEN, "trig_type %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_TYPE;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "TWT_SETUP_FLOW_TYPE is must");
		return -EINVAL;
	}
	val = nla_get_u8(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "flow_type %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_PROTECTION;
	if (tb2[cmd_id])
		val = nla_get_flag(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "protection %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	value = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_TIME;
	if (tb2[cmd_id])
		value = nla_get_u32(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_time 0x%x", value);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_DURATION;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "TWT_SETUP_WAKE_DURATION is must");
		return -EINVAL;
	}
	value = nla_get_u32(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_dur %d", value);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL2_MANTISSA;
	if (!tb2[cmd_id]) {
		cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_INTVL_MANTISSA;
		if (!tb2[cmd_id]) {
			wpa_printf(MSG_ERROR, "SETUP_WAKE_INTVL_MANTISSA is must");
			return -EINVAL;
		}
		wake_intvl_mantis = nla_get_u32(tb2[cmd_id]);
		wake_intvl_mantis = wake_intvl_mantis * TWT_WAKE_INTERVAL_TU_FACTOR;
	} else {
		wake_intvl_mantis = nla_get_u32(tb2[cmd_id]);
	}
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_intvl %d", wake_intvl_mantis);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	wake_tsf = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_WAKE_TIME_TSF;
	if (tb2[cmd_id])
		wake_tsf = nla_get_u64(tb2[cmd_id]);
	os_snprintf(temp, TWT_RESP_BUF_LEN, "wake_tsf 0x%lx", wake_tsf);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_TWT_INFO_ENABLED;
	if (tb2[cmd_id])
		val = nla_get_flag(tb2[cmd_id]);

	os_snprintf(temp, TWT_RESP_BUF_LEN, "info_enabled %d", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	val = 0;
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_RESPONDER_PM_MODE;
	if (tb2[cmd_id]) {
		val = nla_get_u8(tb2[cmd_id]);

		os_snprintf(temp, TWT_RESP_BUF_LEN, "responder_pm %d", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}

	*buf = '\0';

	return 0;
}

/**
 * unpack_twt_get_params_nlmsg()- unpacks and prints the twt get_param
 * response recieved from driver synchronously for twt_session_get_params.
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -1 on Failure
 */
static int unpack_twt_get_params_nlmsg(struct nl_msg **tb, char *buf, int buf_len)
{
	int ret, rem, id, len = 0, num_twt_sessions = 0;
	struct nlattr *config_attr[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX + 1];
	struct nlattr *setup_attr[QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX + 1];
	struct nlattr *attr;

	if (nla_parse_nested(config_attr, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX,
			     tb[NL80211_ATTR_VENDOR_DATA], NULL)) {
		wpa_printf(MSG_ERROR, "twt_get_params: nla_parse_nested fail");
		return -EINVAL;
	}

	id = QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS;
	attr = config_attr[id];
	if (!attr) {
		wpa_printf(MSG_ERROR, "twt_get_params: config_twt_params fail");
		return -EINVAL;
	}

	num_twt_sessions = 0;
	nla_for_each_nested(attr, config_attr[id], rem) {
		num_twt_sessions++;
		if (nla_parse(setup_attr, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX,
			      nla_data(attr), nla_len(attr), NULL)) {
			wpa_printf(MSG_ERROR, "twt_get_params: nla_parse fail");
			return -EINVAL;
		}
		ret = unpack_twt_get_params_resp(setup_attr, buf + len,
						 buf_len - len);
		if (ret < 0)
			return ret;
		len += ret;
	}

	wpa_printf(MSG_ERROR, "twt_get_params: number of twt sessions = %d",
		   num_twt_sessions);

	return 0;
}

/**
 * wpa_get_twt_stats_resp_val()- parse twt get_stats response
 * recieved from driver synchronously for twt_session_get_stats.
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -1 on Failure
 */
static int wpa_get_twt_stats_resp_val(struct nlattr **tb2, char *buf,
				      int buf_len)
{
	int cmd_id, len;
	u32  val = 0;
	u8 val1 = 0;
	char temp[TWT_RESP_BUF_LEN];
	char *start = buf;

	os_memset(temp, 0, TWT_RESP_BUF_LEN);

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_FLOW_ID;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats flow id missing", __func__);
	} else {
		val1 = nla_get_u8(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "flow_id %u", val1);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT stats flow id : %u", val1);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_NUM_SP_ITERATIONS;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats num sp iterations missing", __func__);
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "num_sp_iteration %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT num sp Iterations : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_MIN_WAKE_DURATION;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats min wake duration missing", __func__);
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "min_wake_dur %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT min wake duration : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_MAX_WAKE_DURATION;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats max wake duration missing", __func__);
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "max_wake_dur %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT Max wake duration : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_SESSION_WAKE_DURATION;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats sess_wake_dur missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "session_wake_dur %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT stats session wake duration : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_AVG_WAKE_DURATION;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats avg_wake_dur missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "avg_wake_dur %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT stats avg wake duration : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_AVERAGE_TX_MPDU;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats average tx mpdu missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "tx_mpdu %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT stats average tx mpdu : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_AVERAGE_RX_MPDU;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats average rx mpdu missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "rx_mpdu %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT stats average rx mpdu : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_AVERAGE_TX_PACKET_SIZE;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats average tx packet size missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "tx_pkt_size %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT stats average tx packet size : %u", val);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_STATS_AVERAGE_RX_PACKET_SIZE;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR,"%s TWT stats average rx packet size missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u32(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "rx_pkt_size %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
		wpa_printf(MSG_INFO,"TWT stats average rx packet size : %u", val);
	}
	len = (buf - start);
	*buf = '\0';

	return len;
}

/**
 * unpack_twt_get_stats_nlmsg()- unpacks and prints the twt get_stats
 * response recieved from driver synchronously for twt_session_get_stats.
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -1 on Failure
 */
static
int unpack_twt_get_stats_nlmsg(struct nl_msg **tb, char *buf, int buf_len)
{
	int ret, rem, id, len = 0, num_twt_sessions = 0;
	struct nlattr *config_attr[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX + 1];
	struct nlattr *setup_attr[QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX + 1];
	struct nlattr *attr;

	if (nla_parse_nested(config_attr, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX,
			     tb[NL80211_ATTR_VENDOR_DATA], NULL)) {
		wpa_printf(MSG_ERROR, "twt_get_stats: nla_parse_nested fail");
		return -EINVAL;
	}

	id = QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS;
	attr = config_attr[id];
	if (!attr) {
		wpa_printf(MSG_ERROR, "twt_get_stats: config_twt_params fail");
		return -EINVAL;
	}

	num_twt_sessions = 0;
	nla_for_each_nested(attr, config_attr[id], rem) {
		num_twt_sessions++;
		if (nla_parse(setup_attr, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX,
				nla_data(attr), nla_len(attr), NULL)) {
			wpa_printf(MSG_ERROR, "twt_get_stats: nla_parse fail");
			return -EINVAL;
		}
		ret = wpa_get_twt_stats_resp_val(setup_attr, buf + len,
		                                 buf_len - len);
		if (ret < 0)
			return ret;
		len += ret;
	}
	wpa_printf(MSG_INFO,"twt_get_stats: number of twt sessions = %d", num_twt_sessions);

	return 0;
}

static int wpa_get_twt_capabilities_resp_val(struct nlattr **tb2, char *buf,
					     int buf_len)
{
	int cmd_id;
	u16 msb, lsb;
	u32 val;
	char temp[TWT_RESP_BUF_LEN];

	os_memset(temp, 0, TWT_RESP_BUF_LEN);

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_CAPABILITIES_SELF;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_INFO,"%s TWT self capabilities missing", __func__);
		return -EINVAL;
	} else {
		msb = nla_get_u16(tb2[cmd_id]);
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_CAPABILITIES_PEER;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_INFO,"%s TWT peer capabilities missing", __func__);
		return -EINVAL;
	} else {
		lsb  = nla_get_u16(tb2[cmd_id]);
	}
	wpa_printf(MSG_INFO,"TWT self_capab:%d, TWT peer_capab:%d", msb, lsb);
	val = (msb << 16) | lsb;
	os_snprintf(temp, TWT_RESP_BUF_LEN, "0x%x", val);
	buf = result_copy_to_buf(temp, buf, &buf_len);
	if (!buf)
		return -EINVAL;
	*buf = '\0';

	return 0;
}

/**
 * unpack_twt_get_capab_nlmsg()- unpacks and prints the twt get capabilities
 * response recieved from driver synchronously for TWT_GET_CAP command.
 * The response is printed in below hex-format:
 * 0xHIGHLOW
 * HIGH: Self capabilities
 * LOW:  Peer capabilities
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -1 on Failure
 */
static int unpack_twt_get_capab_nlmsg(struct nl_msg **tb, char *buf, int buf_len)
{
	int ret, id;
	struct nlattr *config_attr[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX + 1];
	struct nlattr *setup_attr[QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX + 1];
	struct nlattr *attr;

	if (nla_parse_nested(config_attr, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX,
			     tb[NL80211_ATTR_VENDOR_DATA], NULL)) {
		wpa_printf(MSG_ERROR, "twt_get_capability: nla_parse_nested fail");
		return -EINVAL;
	}

	id = QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS;
	attr = config_attr[id];
	if (!attr) {
		wpa_printf(MSG_ERROR, "twt_get_capability: config_twt_params fail");
		return -EINVAL;
	}

	if (nla_parse(setup_attr, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX,
		      nla_data(attr), nla_len(attr), NULL)) {
		wpa_printf(MSG_ERROR, "twt_get_capability: nla_parse fail");
		return -EINVAL;
	}

	ret = wpa_get_twt_capabilities_resp_val(setup_attr, buf, buf_len);
	return ret;
}

/**
 * unpack_twt_setup_nlmsg()- unpacks twt_session_setup response recieved
 * The response is printed in below format:
 * CTRL-EVENT-TWT SETUP dialog_id <dialog_id> status <status> ..
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -EINVAL on invalid response
 */
static int unpack_twt_setup_nlmsg(struct nlattr **tb, char *buf, int buf_len)
{
	int ret = 0;
	struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX + 1];

	if (nla_parse_nested(tb2, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX,
			     tb[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS], NULL)) {
		wpa_printf(MSG_ERROR, "nla_parse failed\n");
		return -EINVAL;
	}

	ret = wpa_get_twt_setup_resp_val(tb2, buf, buf_len);

	return ret;
}

static int unpack_nlmsg_twt_params(struct nl_msg *twt_nl_msg,
				   enum qca_wlan_twt_operation type,
				   char *buf, int buf_len)
{
	int ret = 0;
	struct nlattr *tb[NL80211_ATTR_MAX + 1];
	struct genlmsghdr *gnlh = nlmsg_data(nlmsg_hdr(twt_nl_msg));

	nla_parse(tb, NL80211_ATTR_MAX, genlmsg_attrdata(gnlh, 0),
		  genlmsg_attrlen(gnlh, 0), NULL);

	switch (type) {
	case QCA_WLAN_TWT_GET:
		ret = unpack_twt_get_params_nlmsg(tb, buf, buf_len);
		break;
	case QCA_WLAN_TWT_GET_STATS:
		ret = unpack_twt_get_stats_nlmsg(tb, buf, buf_len);
		break;
	case QCA_WLAN_TWT_GET_CAPABILITIES:
		ret = unpack_twt_get_capab_nlmsg(tb, buf, buf_len);
		break;
	default:
		wpa_printf(MSG_DEBUG, "Unsupported command: %d", type);
		ret = -EINVAL;
		break;
	}

	return ret;
}

static int twt_response_handler(struct nl_msg *msg, void *arg)
{
	struct twt_resp_info *info = (struct twt_resp_info *) arg;
	struct wpa_driver_nl80211_data *drv = NULL;
	int ret;

	drv = info->drv;
	ret = unpack_nlmsg_twt_params(msg, info->twt_oper, info->reply_buf,
				      info->reply_buf_len);
	wpa_printf(MSG_DEBUG, "%s - twt_oper %d", __func__, info->twt_oper);
	if (!ret)
		wpa_msg(drv->ctx, MSG_INFO,
			TWT_CTRL_EVENT " %s : OK", info->reply_buf);
	else
		wpa_msg(drv->ctx, MSG_INFO,
			TWT_CTRL_EVENT " %s : Error = %d",
			info->reply_buf, ret);

	return ret;
}

struct features_info {
	u8 *flags;
	size_t flags_len;
};

static
int check_feature(enum qca_wlan_vendor_features feature,
		  struct features_info *info)
{
	size_t idx = feature / 8;

	return (idx < info->flags_len) &&
		(info->flags[idx] & BIT(feature % 8));
}

/* features_info_handler() - parse sync response for get_feature cmd
 *
 * @param msg: nl_msg buffer
 * @Param arg: feature infor
 *
 * @Returns 0 on Success, error code on invalid response
 */
static
int features_info_handler(struct nl_msg *msg, void *arg)
{
	struct genlmsghdr *mHeader;
	struct nlattr *mAttributes[NL80211_ATTR_MAX_INTERNAL + 1];
	struct nlattr *vendata, *attr;
	int datalen;

	struct features_info *info = (struct features_info *) arg;
	int status = 0;


	mHeader = (struct genlmsghdr *)nlmsg_data(nlmsg_hdr(msg));
	nla_parse(mAttributes, NL80211_ATTR_MAX_INTERNAL,
			  genlmsg_attrdata(mHeader, 0),
			  genlmsg_attrlen(mHeader, 0), NULL);

	if (mAttributes[NL80211_ATTR_VENDOR_DATA]) {
		vendata = nla_data(mAttributes[NL80211_ATTR_VENDOR_DATA]);
		datalen = nla_len(mAttributes[NL80211_ATTR_VENDOR_DATA]);
		if (!vendata) {
			wpa_printf(MSG_ERROR,"Vendor data not found");
			return -1;
		}
		struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_MAX + 1];

		nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_MAX,
				vendata, datalen, NULL);

		attr = tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_FLAGS];
		if (attr) {
			int len = nla_len(attr);
			info->flags = os_malloc(len);
			if (info->flags != NULL) {
				os_memcpy(info->flags, nla_data(attr), len);
				info->flags_len = len;
			}
		} else {
			wpa_printf(MSG_ERROR,"VENDOR_ATTR_FEATURE_FLAGS not found");
		}
	} else {
		wpa_printf(MSG_ERROR,"NL80211_ATTR_VENDOR_DATA not found");
		status = -1;
	}

	return status;
}

/* pack_nlmsg_vendor_feature_hdr() - pack get_features command
 *
 * @param drv_nl_msg: nl_msg buffer
 * @Param drv: nl data
 * @Param ifname: interface name
 *
 * @Returns 0 on Success, error code on invalid response
 */
static
int pack_nlmsg_vendor_feature_hdr(struct nl_msg *drv_nl_msg,
				  struct wpa_driver_nl80211_data *drv,
				  char *ifname)
{
	int ret;
	int ifindex;

	genlmsg_put(drv_nl_msg, NL_AUTO_PORT, NL_AUTO_SEQ,
		    drv->global->nl80211_id, 0, 0,
		    NL80211_CMD_VENDOR, 0);

	ret = nla_put_u32(drv_nl_msg, NL80211_ATTR_VENDOR_ID, OUI_QCA);
	if (ret < 0) {
		wpa_printf(MSG_ERROR, "Failed to put vendor id");
		return ret;
	}

	ret = nla_put_u32(drv_nl_msg, NL80211_ATTR_VENDOR_SUBCMD,
			  QCA_NL80211_VENDOR_SUBCMD_GET_FEATURES);
	if (ret < 0) {
		wpa_printf(MSG_DEBUG, "nl put twt vendor subcmd failed");
		return ret;
	}

	if (ifname && (strlen(ifname) > 0))
		ifindex = if_nametoindex(ifname);
	else
		ifindex = if_nametoindex(DEFAULT_IFNAME);

	ret = nla_put_u32(drv_nl_msg, NL80211_ATTR_IFINDEX, ifindex);
	if (ret < 0) {
		wpa_printf(MSG_DEBUG, "nl put iface: %s failed", ifname);
		return ret;
	}
	return ret;
}

/* check_wifi_twt_async_feature() - check if driver supports twt async feature
 *
 * @Param drv: nl data
 * @Param ifname: interface name
 *
 * @Returns 1 if twt async feature is supported, 0 otherwise
 */
static int check_wifi_twt_async_feature(struct wpa_driver_nl80211_data *drv,
					char *ifname)
{
	struct nl_msg *nlmsg;
	struct features_info info;
	int ret;

	if (twt_async_support != -1) {
		return twt_async_support;
	}

        nlmsg = nlmsg_alloc();
	if (!nlmsg) {
		wpa_printf(MSG_ERROR, "nlmg alloc failure");
		return -ENOMEM;
	}

	pack_nlmsg_vendor_feature_hdr(nlmsg, drv, ifname);
	os_memset(&info, 0, sizeof(info));
	ret = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
			 features_info_handler, &info);

	if (ret || !info.flags) {
		nlmsg_free(nlmsg);
		return 0;
	}

	if (check_feature(QCA_WLAN_VENDOR_FEATURE_TWT_ASYNC_SUPPORT, &info)) {
		twt_async_support = 1;
	} else {
		twt_async_support = 0;
	}

	os_free(info.flags);
	return twt_async_support;
}

static int wpa_driver_twt_cmd_handler(struct wpa_driver_nl80211_data *drv,
				      char *ifname,
				      enum qca_wlan_twt_operation twt_oper,
				      char *param, char *buf,
				      size_t buf_len, int *status)
{
	struct nl_msg *twt_nl_msg;
	struct twt_resp_info reply_info;
	int ret = 0;

	if (!param) {
		wpa_printf(MSG_ERROR, "%s:TWT cmd args missing\n", __func__);
		return -EINVAL;
	}

	if (!buf) {
		wpa_printf(MSG_ERROR, "buf is NULL");
		return -EINVAL;
	}

	wpa_printf(MSG_DEBUG, "TWT DRIVER cmd: %s", param);

	memset(&reply_info, 0, sizeof(struct twt_resp_info));
	os_memset(buf, 0, buf_len);

	reply_info.twt_oper = twt_oper;
	reply_info.reply_buf = buf;
	reply_info.reply_buf_len = buf_len;
	reply_info.drv = drv;

	twt_nl_msg = prepare_nlmsg(drv, ifname, NL80211_CMD_VENDOR,
				   QCA_NL80211_VENDOR_SUBCMD_CONFIG_TWT, 0);
	if (!twt_nl_msg) {
		ret = -EINVAL;
		goto err_msg;
	}

	ret = pack_nlmsg_twt_params(twt_nl_msg, param, twt_oper);
	if (ret) {
		nlmsg_free(twt_nl_msg);
		goto err_msg;
	}

	switch(twt_oper) {
	case QCA_WLAN_TWT_GET:
	case QCA_WLAN_TWT_GET_CAPABILITIES:
	case QCA_WLAN_TWT_GET_STATS:
		*status = send_nlmsg((struct nl_sock *)drv->global->nl,
				     twt_nl_msg, twt_response_handler,
				     &reply_info);
		if (*status != 0) {
			wpa_printf(MSG_ERROR, "Failed to send nlmsg - err %d", *status);
			ret = -EINVAL;
		}
		break;
	case QCA_WLAN_TWT_CLEAR_STATS:
		*status = send_nlmsg((struct nl_sock *)drv->global->nl,
				      twt_nl_msg, NULL, NULL);
		if (*status != 0) {
			wpa_printf(MSG_ERROR, "Failed to send nlmsg - err %d", *status);
			ret = -EINVAL;
		}
		break;
	case QCA_WLAN_TWT_SET:
	case QCA_WLAN_TWT_TERMINATE:
	case QCA_WLAN_TWT_SUSPEND:
	case QCA_WLAN_TWT_RESUME:
	case QCA_WLAN_TWT_NUDGE:
	case QCA_WLAN_TWT_SET_PARAM:
		if(check_wifi_twt_async_feature(drv, ifname) == 0) {
			wpa_printf(MSG_ERROR, "Asynchronous TWT Feature is missing");
			ret = -EINVAL;
		} else {
			*status = send_nlmsg((struct nl_sock *)drv->global->nl,
					     twt_nl_msg, NULL, NULL);
			if (*status != 0) {
				wpa_printf(MSG_ERROR, "Failed to send nlmsg - err %d", *status);
				ret = -EINVAL;
			}
		}
		break;
	default:
		wpa_printf(MSG_ERROR, "nlmg send failure");
		ret = -EINVAL;
		goto err_msg;
	}

err_msg:
	wpa_printf(MSG_ERROR, "sent nlmsg - status %d", *status);
	return ret;
}

/**
 * unpack_twt_terminate_event()- unpacks twt_session_terminate response recieved
 * The response is printed in below format:
 * CTRL-EVENT-TWT TERMINATE dialog_id <dialog_id> status <status>
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -EINVAL on invalid response
 */
static
int unpack_twt_terminate_event(struct nlattr **tb, char *buf, int buf_len)
{
	int cmd_id;
	u8 val;
	char temp[TWT_RESP_BUF_LEN];
	struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX + 1];

	if (nla_parse_nested(tb2, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX,
			     tb[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS], NULL)) {
		wpa_printf(MSG_ERROR, "nla_parse failed");
		return -EINVAL;
	}

	buf = result_copy_to_buf(TWT_TEARDOWN_RESP, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	os_memset(temp, 0, TWT_TEARDOWN_RESP_LEN);
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "%s TWT dialog id missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u8(tb2[cmd_id]);
		if(val == 255) {
			val = 0;
		}
		os_snprintf(temp, TWT_RESP_BUF_LEN, "dialog_id %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_STATUS;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "%s TWT resp status missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u8(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "status %u ", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;

		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "(%s)", twt_status_to_string(val));
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}
	*buf = '\0';

	return 0;
}

/**
 * unpack_twt_suspend_event()- unpacks twt_session_pause response recieved
 * The response is printed in below format:
 * CTRL-EVENT-TWT PAUSE dialog_id <dialog_id> status <status>
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -EINVAL on invalid response
 */
static
int unpack_twt_suspend_event(struct nlattr **tb, char *buf, int buf_len)
{
	int cmd_id;
	u8 val;
	char temp[TWT_RESP_BUF_LEN];
	struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX + 1];

	if (nla_parse_nested(tb2, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX,
			     tb[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS], NULL)) {
		wpa_printf(MSG_ERROR, "nla_parse failed");
		return -1;
	}

	buf = result_copy_to_buf(TWT_PAUSE_RESP, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	os_memset(temp, 0, TWT_PAUSE_RESP_LEN);
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_FLOW_ID;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "%s TWT dialog id missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u8(tb2[cmd_id]);
		if(val == 255) {
			val = 0;
		}
		os_snprintf(temp, TWT_RESP_BUF_LEN, "dialog_id %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_STATUS;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "%s TWT resp status missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u8(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "status %u ", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;

		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "(%s)", twt_status_to_string(val));
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}
	*buf = '\0';

	return 0;
}

/**
 * unpack_twt_resume_event()- unpacks twt_session_resume response recieved
 * The response is printed in below format:
 * CTRL-EVENT-TWT RESUME dialog_id <dialog_id> status <status>
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -EINVAL on invalid response
 */
static
int unpack_twt_resume_event(struct nlattr **tb, char *buf, int buf_len)
{
	int cmd_id;
	u8 val;
	char temp[TWT_RESP_BUF_LEN];
	struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX + 1];

	if (nla_parse_nested(tb2, QCA_WLAN_VENDOR_ATTR_TWT_SETUP_MAX,
			     tb[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_PARAMS], NULL)) {
		wpa_printf(MSG_ERROR, "nla_parse failed");
		return -1;
	}

	buf = result_copy_to_buf(TWT_RESUME_RESP, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	os_memset(temp, 0, TWT_RESUME_RESP_LEN);
	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_RESUME_FLOW_ID;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "%s TWT dialog id missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u8(tb2[cmd_id]);
		if(val == 255) {
			val = 0;
		}
		os_snprintf(temp, TWT_RESP_BUF_LEN, "dialog_id %u", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_TWT_SETUP_STATUS;
	if (!tb2[cmd_id]) {
		wpa_printf(MSG_ERROR, "%s TWT resp status missing", __func__);
		return -EINVAL;
	} else {
		val = nla_get_u8(tb2[cmd_id]);
		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "status %u ", val);
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;

		os_memset(temp, 0, TWT_RESP_BUF_LEN);
		os_snprintf(temp, TWT_RESP_BUF_LEN, "(%s)", twt_status_to_string(val));
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf)
			return -EINVAL;
	}
	*buf = '\0';

	return 0;
}

/**
 * unpack_twt_notify_event()- prints the twt notify response recieved from driver
 * asynchronously for QCA_WLAN_TWT_SETUP_READY_NOTIFY event.
 * The response is printed in below format:
 * CTRL-EVENT-TWT NOTIFY
 *
 * @Param tb: vendor nl data
 * @Param buf: stores the response
 * @Param buf_len: length of the response buffer
 *
 * @Returns 0 on Success, -EINVAL on invalid response
 */
int unpack_twt_notify_event(struct nlattr **tb, char *buf, int buf_len)
{
	char temp[TWT_RESP_BUF_LEN];

	os_memset(temp, 0, TWT_RESP_BUF_LEN);

	buf = result_copy_to_buf(TWT_NOTIFY_RESP, buf, &buf_len);
	if (!buf)
		return -EINVAL;

	*buf = '\0';
        return 0;
}

/**
 * wpa_driver_twt_async_resp_handler()- handler for asynchronous twt vendor event
 * recieved from the driver.
 *
 * @Param drv- wpa_driver_nl80211_data
 * @Param vendor_id- vendor id for vendor specific command
 * @Param subcmd- subcmd as defined by enum qca_nl80211_vendor_subcmds
 * @Param data- vendor data
 * @Param len- vendor data length
 *
 * @Returns 0 for Success, -1 for Failure
 */
static int wpa_driver_twt_async_resp_event(struct wpa_driver_nl80211_data *drv,
					   u32 vendor_id, u32 subcmd, u8 *data, size_t len)
{
	int ret = 0;
	char *buf;
	buf = (char *)malloc(TWT_RESP_BUF_LEN);
	int buf_len = TWT_RESP_BUF_LEN;
	struct nlattr *tb[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX + 1];
	u8 twt_operation_type;

	if (!buf)
		return -1;

	ret = nla_parse(tb, QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_MAX,
			(struct nlattr *) data, len, NULL);
	if (ret) {
		wpa_printf(MSG_ERROR, "nla_parse failed %d", ret);
		goto fail;
	}

	if (!buf) {
		wpa_printf(MSG_ERROR, "buf not allocated");
		return -1;
	}

	memset(buf, 0, TWT_RESP_BUF_LEN);

	twt_operation_type = nla_get_u8(tb[QCA_WLAN_VENDOR_ATTR_CONFIG_TWT_OPERATION]);

	switch(twt_operation_type) {
	case QCA_WLAN_TWT_SET:
		ret = unpack_twt_setup_nlmsg(tb, buf, buf_len);
		break;
	case QCA_WLAN_TWT_TERMINATE:
		ret = unpack_twt_terminate_event(tb, buf, buf_len);
		break;
	case QCA_WLAN_TWT_SUSPEND:
		ret = unpack_twt_suspend_event(tb, buf, buf_len);
		break;
	case QCA_WLAN_TWT_RESUME:
		ret = unpack_twt_resume_event(tb, buf, buf_len);
		break;
	case QCA_WLAN_TWT_SETUP_READY_NOTIFY:
		ret = unpack_twt_notify_event(tb, buf, buf_len);
		break;
	default:
		ret = -1;
	}

	if(ret) {
		wpa_printf(MSG_ERROR, "Async event parsing failed for operation %d",
			   twt_operation_type);
		goto fail;
	}
	wpa_printf(MSG_ERROR,"%s", buf);
	wpa_msg(drv->ctx, MSG_INFO, "%s", buf);
fail:
	free(buf);
	return ret;
}

static int wpa_driver_form_clear_mcc_quota_msg(struct i802_bss *bss,
					       char *cmd)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nlattr *nl_attr;
	struct nl_msg *nlmsg;
	uint32_t if_index = 0;
	int ret;

	/* First comes interface name - optional */
	if (os_strncasecmp(cmd, "iface", 5) == 0) {
		char *iface;
		cmd = move_to_next_str(cmd);
		/* null terminate the iface name in the cmd string */
		iface = strchr(cmd, ' ');
		if (iface == NULL) {
			wpa_printf(MSG_ERROR, "mcc_quota: iface is not found"
				   " in cmd string");
			return -EINVAL;
		}
		*iface = '\0';
		iface = cmd;
		errno = 0;
		if_index = if_nametoindex(cmd);
		if (if_index == 0) {
			wpa_printf(MSG_ERROR, "mcc_quota: iface %s not found %d",
				   cmd, errno);
			return -EINVAL;
		}
		wpa_printf(MSG_INFO, "mcc_quota: ifindex %u", if_index);
		cmd += strlen(iface) + 1;
	}

	nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
				     QCA_NL80211_VENDOR_SUBCMD_MCC_QUOTA);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR, "mcc_quota: Failed to allocate nl message");
		return -ENOMEM;
	}

	nl_attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (!nl_attr) {
		wpa_printf(MSG_ERROR, "mcc_quota: Failed to alloc nlattr");
		ret = -ENOMEM;
		goto fail;
	}
	/* Put the quota type */
	ret = nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_MCC_QUOTA_TYPE,
			  QCA_WLAN_VENDOR_MCC_QUOTA_TYPE_CLEAR);
	if (ret) {
		wpa_printf(MSG_ERROR, "mcc_quota: Failed to add type attr %d", ret);
		goto fail;
	}

	if (if_index) {
		ret = nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_MCC_QUOTA_IFINDEX, if_index);
		if (ret) {
			wpa_printf(MSG_ERROR, "mcc_quota: Failed to add if_index attr %d", ret);
			goto fail;
		}
	}
	nla_nest_end(nlmsg, nl_attr);

	ret = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg, NULL, NULL);

	if (ret) {
		wpa_printf(MSG_ERROR, "mcc_quota: Error sending nlmsg %d", ret);
		return ret;
	}

	return 0;
fail:
	nlmsg_free(nlmsg);
	return ret;
}

static int wpa_driver_form_set_mcc_quota_msg(struct i802_bss *bss,
					     char *cmd)
{
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct mcc_quota mccquota[MCC_QUOTA_ENTRIES_MAX];
	uint32_t if_index, quota;
	struct nlattr *nl_attr, *mcc_attr_list;
	struct nlattr *mcc_attr;
	struct nl_msg *nlmsg;
	int ret, entry, i;

	wpa_printf(MSG_INFO, "mcc_quota: %s", cmd);

	entry = 0;
	while (*cmd != '\0') {
		if (entry >= MCC_QUOTA_ENTRIES_MAX) {
			wpa_printf(MSG_INFO, "mcc_quota: Only %d entries accepted", entry);
			break;
		}
		/* First comes interface name */
		if (os_strncasecmp(cmd, "iface", 5) == 0) {
			char *iface;
			cmd = move_to_next_str(cmd);
			/* null terminate the iface name in the cmd string */
			iface = strchr(cmd, ' ');
			if (iface == NULL) {
				wpa_printf(MSG_ERROR, "mcc_quota: iface is not"
					   " found in cmd string");
				return -EINVAL;
			}
			*iface = '\0';
			iface = cmd;
			errno = 0;
			if_index = if_nametoindex(cmd);
			if (if_index == 0) {
				wpa_printf(MSG_ERROR, "mcc_quota: iface %s not found %d",
						cmd, errno);
				return -EINVAL;
			}
			wpa_printf(MSG_INFO, "mcc_quota: ifindex %u", if_index);
			cmd += strlen(iface) + 1;
		} else {
			wpa_printf(MSG_ERROR, "mcc_quota: Iface name not in order");
			return -EINVAL;
		}

		/* Second comes quota value */
		if (os_strncasecmp(cmd, "quota", 5) == 0) {
			cmd = move_to_next_str(cmd);
			quota = get_u32_from_string(cmd, &ret);
			if (ret < 0)
				return ret;
		} else {
			wpa_printf(MSG_ERROR, "mcc_quota: Quota not in order");
			return -EINVAL;
		}

		if (quota < MCC_QUOTA_MIN || quota > MCC_QUOTA_MAX) {
			wpa_printf(MSG_ERROR, "mcc_quota: Invalid quota value %u", quota);
			return -EINVAL;
		}

		mccquota[entry].if_idx = if_index;
		mccquota[entry].quota = quota;
		cmd = move_to_next_str(cmd);
		entry++;
	}
	wpa_printf(MSG_INFO, "mcc_quota: Entries : %d", entry);
	if (entry < 1) {
		wpa_printf(MSG_ERROR, "mcc_quota: No valid entries?");
		return -EINVAL;
	}

	nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
				     QCA_NL80211_VENDOR_SUBCMD_MCC_QUOTA);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR,"mcc_quota: Failed to allocate nl message");
		return -ENOMEM;
	}

	nl_attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (nl_attr == NULL) {
		wpa_printf(MSG_ERROR, "mcc_quota: Failed to alloc nlattr");
		ret = -ENOMEM;
		goto fail;
	}

	/* Put the quota type */
	ret = nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_MCC_QUOTA_TYPE,
			  QCA_WLAN_VENDOR_MCC_QUOTA_TYPE_FIXED);
	if (ret) {
		wpa_printf(MSG_ERROR, "mcc_quota: Failed to add type attr %d", ret);
		goto fail;
	}

	mcc_attr_list = nla_nest_start(nlmsg, QCA_WLAN_VENDOR_ATTR_MCC_QUOTA_ENTRIES);
	if (!mcc_attr_list) {
		wpa_printf(MSG_ERROR, "mcc_quota: Failed to alloc mcc_attr_list");
		ret = -ENOMEM;
		goto fail;
	}

	for (i = 0; i < entry  && entry <= MCC_QUOTA_ENTRIES_MAX; i++) {
		/* Nest the (iface ,quota) */
		mcc_attr = nla_nest_start(nlmsg, QCA_WLAN_VENDOR_ATTR_MCC_QUOTA_ENTRIES);
		if (mcc_attr == NULL) {
			wpa_printf(MSG_ERROR, "mcc_quota: Failed to alloc mccattr");
			ret = -ENOMEM;
			goto fail;
		}
		ret = nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_MCC_QUOTA_IFINDEX, mccquota[i].if_idx);
		if (ret) {
			wpa_printf(MSG_ERROR, "mcc_quota: Failed to add if_index attr %d", ret);
			goto fail;
		}

		ret = nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_MCC_QUOTA_CHAN_TIME_PERCENTAGE, mccquota[i].quota);
		if (ret) {
			wpa_printf(MSG_ERROR, "mcc_quota: Failed to add quota attr %d", ret);
			goto fail;
		}

		nla_nest_end(nlmsg, mcc_attr);
	}
	nla_nest_end(nlmsg, mcc_attr_list);

	nla_nest_end(nlmsg, nl_attr);

	ret = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg, NULL, NULL);

	if (ret) {
		wpa_printf(MSG_ERROR, "mcc_quota: Error sending nlmsg %d", ret);
		return ret;
	}

	return 0;
fail:
	nlmsg_free(nlmsg);
	return ret;
}

int wpa_driver_cmd_send_mcc_quota(struct i802_bss *bss,
				  char *cmd)
{
	int ret;

	wpa_printf(MSG_INFO, "mcc_quota: %s", cmd);

	if (os_strncasecmp(cmd, "set", 3) == 0) {
		cmd = move_to_next_str(cmd);
		ret = wpa_driver_form_set_mcc_quota_msg(bss, cmd);
		return ret;
	}

	if (os_strncasecmp(cmd, "clear", 5) == 0) {
		cmd = move_to_next_str(cmd);
		ret = wpa_driver_form_clear_mcc_quota_msg(bss, cmd);
		return ret;
	}

	wpa_printf(MSG_ERROR, "mcc_quota: Unknown operation");
	return -EINVAL;
}

static int wpa_driver_form_flush_queue_config_msg(struct i802_bss *bss, char *cmd)
{
	uint32_t tid_mask = 0, flush_policy = 0;
	u8 mac[MAC_ADDR_LEN], ac_mask = 0;
	struct nl_msg *nlmsg = NULL;
	struct nlattr *nl_attr;
	char *ptr = cmd;
	int ret;

	wpa_printf(MSG_DEBUG, "flush_queue_config: %s", cmd);

	/* First parameter: MAC address of peer */
	if (os_strncasecmp(cmd, "peer", 4) == 0) {
		cmd = move_to_next_str(cmd);
		if ((strlen(cmd) < (MAC_ADDR_LEN * 2 + MAC_ADDR_LEN - 1)) ||
		    convert_string_to_bytes(mac, cmd, MAC_ADDR_LEN) !=
		    MAC_ADDR_LEN) {
			wpa_printf(MSG_ERROR, "flush_queue_config: Invalid MAC address");
			wpa_printf(MSG_ERROR, "flush_queue_config cmd: %s", ptr);
			return -EINVAL;
		}
		cmd = move_to_next_str(cmd);
	} else {
		wpa_printf(MSG_ERROR, "flush_queue_config: peer MAC address is missing");
		wpa_printf(MSG_ERROR, "flush_queue_config cmd: %s", ptr);
		return -EINVAL;
	}

	/* Second parameter: flush config */
	if (os_strncasecmp(cmd, "policy", 6) == 0) {
		cmd = move_to_next_str(cmd);
		flush_policy = get_u32_from_string(cmd, &ret);
		if (ret < 0) {
			wpa_printf(MSG_ERROR, "flush_queue_config: Invalid flush policy");
			wpa_printf(MSG_ERROR, "flush_queue_config cmd: %s", ptr);
			return ret;
		}
		cmd = move_to_next_str(cmd);
	}

	/* Third parameter: Follow QCA_WLAN_VENDOR_ATTR_AC  for ac bit mask */
	if (os_strncasecmp(cmd, "ac", 2) == 0) {
		cmd = move_to_next_str(cmd);
		ac_mask = get_u8_from_string(cmd, &ret);
		if (ret < 0) {
			wpa_printf(MSG_ERROR, "flush_queue_config: AC mask error");
			wpa_printf(MSG_ERROR, "flush_queue_config cmd: %s", ptr);
			return ret;
		}

		if (!(ac_mask & (BIT(QCA_WLAN_VENDOR_TOS_BK) | BIT(QCA_WLAN_VENDOR_TOS_BE) |
				 BIT(QCA_WLAN_VENDOR_TOS_VI) | BIT(QCA_WLAN_VENDOR_TOS_VO)))) {
			wpa_printf(MSG_ERROR, "flush_queue_config: Invalid AC mask");
			wpa_printf(MSG_ERROR, "flush_queue_config cmd: %s", ptr);
			return -EINVAL;
		}
		cmd = move_to_next_str(cmd);
	}

	/* Fourth parameter: tid mask */
	if (os_strncasecmp(cmd, "tid", 3) == 0) {
		cmd = move_to_next_str(cmd);
		tid_mask = get_u32_from_string(cmd, &ret);
		if (ret < 0) {
			wpa_printf(MSG_ERROR, "flush_queue_config: TID mask error");
			wpa_printf(MSG_ERROR, "flush_queue_config cmd: %s", ptr);
			return ret;
		}
		cmd = move_to_next_str(cmd);
	}

	if (!tid_mask && !ac_mask) {
		wpa_printf(MSG_ERROR, "flush_queue_config: Neither TID not AC mask provided");
		wpa_printf(MSG_ERROR, "flush_queue_config cmd: %s", ptr);
		return -EINVAL;
	}

	nlmsg =
	prepare_vendor_nlmsg(bss->drv, bss->ifname,
			     QCA_NL80211_VENDOR_SUBCMD_PEER_FLUSH_PENDING);
	if (!nlmsg) {
		wpa_printf(MSG_ERROR, "flush_queue_config: Failed to allocate nl message");
		return -ENOMEM;
	}

	nl_attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (!nl_attr) {
		wpa_printf(MSG_ERROR, "flush_queue_config: Failed to alloc nlattr");
		ret = -ENOMEM;
		goto fail;
	}

	/* Put the peer MAC address */
	ret = nla_put(nlmsg, QCA_WLAN_VENDOR_ATTR_PEER_ADDR, MAC_ADDR_LEN, mac);
	if (ret) {
		wpa_printf(MSG_ERROR, "flush_queue_config: Error add hw addr attr %d", ret);
		goto fail;
	}

	/* Put the flush_policy */
	ret = nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_FLUSH_PENDING_POLICY,
			  flush_policy);
	if (ret) {
		wpa_printf(MSG_ERROR,
			   "flush_queue_config: Error add policy attr %d", ret);
		goto fail;
	}

	if (tid_mask) {
		/* Put the tid mask */
		ret = nla_put_u32(nlmsg, QCA_WLAN_VENDOR_ATTR_TID_MASK,
				  tid_mask);
		if (ret) {
			wpa_printf(MSG_ERROR, "flush_queue_config: Error add tid mask attr %d", ret);
			goto fail;
		}
	} else {
		/* Put the ac mask */
		ret = nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_AC, ac_mask);
		if (ret) {
			wpa_printf(MSG_ERROR, "flush_queue_config: Error add ac attr %d", ret);
			goto fail;
		}
	}

	nla_nest_end(nlmsg, nl_attr);
	ret = send_nlmsg((struct nl_sock *)bss->drv->global->nl, nlmsg,
			 NULL, NULL);
	if (ret) {
		wpa_printf(MSG_ERROR, "flush_queue_config: Error sending nlmsg %d", ret);
		return ret;
	}
	return 0;
fail:
	if (nlmsg)
		nlmsg_free(nlmsg);
	return ret;
}

int wpa_driver_cmd_send_peer_flush_queue_config(struct i802_bss *bss, char *cmd)
{
	int ret;

	if (os_strncasecmp(cmd, "set", 3) == 0) {
		cmd = move_to_next_str(cmd);
		ret = wpa_driver_form_flush_queue_config_msg(bss, cmd);
		return ret;
	}

	wpa_printf(MSG_ERROR, "peer_flush_config: Unknown operation");
	return -EINVAL;
}

int wpa_driver_nl80211_driver_cmd(void *priv, char *cmd, char *buf,
				  size_t buf_len )
{
	struct i802_bss *bss = priv;
	struct wpa_driver_nl80211_data *drv = NULL;
	struct wpa_driver_nl80211_data *driver;
	struct ifreq ifr;
	android_wifi_priv_cmd priv_cmd;
	int ret = 0, status = 0, lib_n = 0;

	if (bss) {
		drv = bss->drv;
	} else {
		if (os_strncasecmp(cmd, "SET_AP_SUSPEND", 14)) {
			wpa_printf(MSG_ERROR, "%s: bss is NULL for cmd %s\n",
				   __func__, cmd);
			return -EINVAL;
		}
	}

	if (wpa_driver_oem_initialize(&oem_cb_table) != WPA_DRIVER_OEM_STATUS_FAILURE &&
	    oem_cb_table) {

		for (lib_n = 0;
		     oem_cb_table[lib_n].wpa_driver_driver_cmd_oem_cb != NULL;
		     lib_n++)
		{
			ret = oem_cb_table[lib_n].wpa_driver_driver_cmd_oem_cb(
					priv, cmd, buf, buf_len, &status);
			if (ret == WPA_DRIVER_OEM_STATUS_SUCCESS ) {
				return strlen(buf);
			} else if (ret == WPA_DRIVER_OEM_STATUS_ENOSUPP) {
				continue;
			} else if ((ret == WPA_DRIVER_OEM_STATUS_FAILURE) &&
				   (status != 0)) {
				wpa_printf(MSG_DEBUG, "%s: Received error: %d",
						__func__, status);
				return status;
			}
		}
		/* else proceed with legacy handling as below */
	}

	if (!drv) {
		wpa_printf(MSG_ERROR, "%s: drv is NULL for cmd %s\n",
			   __func__, cmd);
		return -EINVAL;
	}

	if (os_strcasecmp(cmd, "START") == 0) {
		dl_list_for_each(driver, &drv->global->interfaces, struct wpa_driver_nl80211_data, list) {
			linux_set_iface_flags(drv->global->ioctl_sock, driver->first_bss->ifname, 1);
			wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "STARTED");
		}
	} else if (os_strcasecmp(cmd, "MACADDR") == 0) {
		u8 macaddr[ETH_ALEN] = {};

		ret = linux_get_ifhwaddr(drv->global->ioctl_sock, bss->ifname, macaddr);
		if (!ret)
			ret = os_snprintf(buf, buf_len,
					  "Macaddr = " MACSTR "\n", MAC2STR(macaddr));
	} else if (os_strncasecmp(cmd, "SET_CONGESTION_REPORT ", 22) == 0) {
		return wpa_driver_cmd_set_congestion_report(priv, cmd + 22);
	} else if (os_strncasecmp(cmd, "SET_TXPOWER ", 12) == 0) {
		return wpa_driver_cmd_set_tx_power(priv, cmd + 12);
	} else if (os_strncasecmp(cmd, "CSI", 3) == 0) {
		cmd += 3;
		return wpa_driver_handle_csi_cmd(bss, cmd, buf, buf_len, &status);
	} else if(os_strncasecmp(cmd, "GETSTATSBSSINFO", 15) == 0) {

		struct resp_info info,info2;
		struct nl_msg *nlmsg;
		struct nlattr *attr;

		os_memset(&g_bss_info, 0, sizeof(struct bss_info));

		memset(&info, 0, sizeof(struct resp_info));
		memset(&info2, 0, sizeof(struct resp_info));

		info.subcmd = QCA_NL80211_VENDOR_SUBCMD_GET_STATION;
		info.cmd_type = GETSTATSBSSINFO;
		char *p;
		if(wpa_driver_ioctl(bss, "GETCOUNTRYREV", buf, buf_len, &status, drv) == 0){
			p = strstr(buf, " ");
			if(p != NULL)
				memcpy(info.country, (p+1), strlen(p+1)+1);//length of p including null
		}
		cmd += 16;
		os_memset(buf, 0, buf_len);

		u8 mac[MAC_ADDR_LEN];

		cmd = skip_white_space(cmd);

		if (strlen(cmd) >= MAC_ADDR_LEN * 2 + MAC_ADDR_LEN - 1
		    && convert_string_to_bytes(mac, cmd, MAC_ADDR_LEN) > 0) {
			wpa_printf(MSG_INFO,"invoking QCA_NL80211_VENDOR_SUBCMD_GET_STA_INFO to retrieve new attributes");
			os_memcpy(&info2.mac_addr[0], mac, MAC_ADDR_LEN);
			nlmsg = prepare_vendor_nlmsg(bss->drv, bss->ifname,
						     QCA_NL80211_VENDOR_SUBCMD_GET_STA_INFO);
			if (!nlmsg) {
				wpa_printf(MSG_ERROR,"Failed to allocate nl message");
				return -1;
			}

			attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
			if (!attr) {
				nlmsg_free(nlmsg);
				return -1;
			}

			if (nla_put(nlmsg, GET_STA_INFO_MAC,
				    MAC_ADDR_LEN, mac)) {
				wpa_printf(MSG_ERROR,"Failed to put GET_STA_INFO_MAC");
				nlmsg_free(nlmsg);
				return -1;
			}

			nla_nest_end(nlmsg, attr);

			ret = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
					     get_sta_info_handler, &info2);
			if (ret != 0) {
				if (ret == -EOPNOTSUPP) {
					wpa_printf(MSG_INFO,"Command is not supported, sending -1 for all new vendor attributes");
				} else {
					wpa_printf(MSG_ERROR,"Failed to send nl message with err %d", ret);
					return -1;
				}
				g_bss_info.ani_level = -1;
				g_bss_info.roam_trigger_reason = -1;
				g_bss_info.roam_fail_reason = -1;
				g_bss_info.roam_invoke_fail_reason = -1;
				g_bss_info.tsf_out_of_sync_count = -1;
				g_bss_info.latest_tx_power = -1;
				g_bss_info.latest_tx_rate = -1;
				g_bss_info.target_power_24g_1mbps = -1;
				g_bss_info.target_power_24g_6mbps = -1;
				g_bss_info.target_power_5g_6mbps = -1;
			} else {
				wpa_printf(MSG_INFO,"Command successfully invoked");
				g_bss_info.ani_level = g_sta_info.ani_level;
				g_bss_info.roam_trigger_reason = g_sta_info.roam_trigger_reason;
				g_bss_info.roam_fail_reason = g_sta_info.roam_fail_reason;
				g_bss_info.roam_invoke_fail_reason = g_sta_info.roam_invoke_fail_reason;
				g_bss_info.tsf_out_of_sync_count = g_sta_info.tsf_out_of_sync_count;
				g_bss_info.latest_tx_power = g_sta_info.latest_tx_power;
				g_bss_info.latest_tx_rate = g_sta_info.latest_tx_rate;
				g_bss_info.target_power_24g_1mbps = g_sta_info.target_power_24g_1mbps;
				g_bss_info.target_power_24g_6mbps = g_sta_info.target_power_24g_6mbps;
				g_bss_info.target_power_5g_6mbps = g_sta_info.target_power_5g_6mbps;
			}
		}

		info.reply_buf = buf;
		info.reply_buf_len = buf_len;

		nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
					     info.subcmd);
		if (!nlmsg) {
			wpa_printf(MSG_ERROR,"Failed to allocate nl message");
			return -1;
		}

		if (populate_nlmsg(nlmsg, cmd, info.cmd_type)) {
			wpa_printf(MSG_ERROR,"Failed to populate nl message");
			nlmsg_free(nlmsg);
			return -1;
		}

		status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
				     response_handler, &info);
		if (status != 0) {
			wpa_printf(MSG_ERROR,"Failed to send nl message with err %d", status);
			return -1;
		}

		return strlen(info.reply_buf);
	} else if (os_strncasecmp(cmd, "GETSTATSSTAINFO", 15) == 0) {
		cmd += 15;
		return wpa_driver_handle_get_sta_info(bss, cmd, buf, buf_len,
						      &status);
	} else if (os_strncasecmp(cmd, "SETCELLSWITCHMODE", 17) == 0) {
		cmd += 17;
		struct resp_info info;
		struct nl_msg *nlmsg;

		memset(&info, 0, sizeof(struct resp_info));

		info.subcmd = QCA_NL80211_VENDOR_SUBCMD_ROAM;
		info.cmd_type = SETCELLSWITCHMODE;

		nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
					     info.subcmd);
		if (!nlmsg) {
			wpa_printf(MSG_ERROR,"Failed to allocate nl message");
			return WPA_DRIVER_OEM_STATUS_FAILURE;
		}

		if (populate_nlmsg(nlmsg, cmd, info.cmd_type)) {
			wpa_printf(MSG_ERROR,"Failed to populate nl message");
			nlmsg_free(nlmsg);
			return WPA_DRIVER_OEM_STATUS_FAILURE;
		}

		status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
				     NULL, NULL);
		if (status != 0) {
			wpa_printf(MSG_ERROR,"Failed to send nl message with err %d", status);
			return WPA_DRIVER_OEM_STATUS_FAILURE;
		}

		return WPA_DRIVER_OEM_STATUS_SUCCESS;
	} else if (os_strncasecmp(cmd, "SET_ANI_LEVEL ", 14) == 0) {
		char *endptr = NULL;
		int mode = 0;
		int ofdmlvl = 0;
		mode = strtol(cmd + 14, &endptr, 10);
		if (mode == 1) {
			if(!(*endptr)) {
				wpa_printf(MSG_ERROR, "%s: failed to set ani setting,\
			          invalid cmd: %s\n", __func__, cmd);
				return -EINVAL;
			}
			ofdmlvl = strtol(endptr, NULL, 10);
		}
		return wpa_driver_cmd_set_ani_level(priv, mode, ofdmlvl);
	} else if (os_strncasecmp(cmd, "GET_THERMAL_INFO", 16) == 0) {
		int temperature = -1;
		int thermal_state = -1;
		int ret, ret2;

		ret = wpa_driver_cmd_get_thermal_info(priv, &temperature,
		    QCA_WLAN_VENDOR_ATTR_THERMAL_CMD_TYPE_GET_TEMPERATURE);
		if (ret)
			return -1;
		ret2 = wpa_driver_cmd_get_thermal_info(priv, &thermal_state,
		    QCA_WLAN_VENDOR_ATTR_THERMAL_CMD_TYPE_GET_LEVEL);
		if (ret2)
			return -1;

		snprintf(buf, buf_len, "%d %d", temperature, thermal_state);
		return strlen(buf);
	} else if (os_strncasecmp(cmd, "GET_DRIVER_SUPPORTED_FEATURES", 29) == 0) {
		struct resp_info info;
		struct nl_msg *nlmsg;
		memset(&info, 0, sizeof(struct resp_info));
		info.subcmd = QCA_NL80211_VENDOR_SUBCMD_GET_FEATURES;
		os_memset(buf, 0, buf_len);
		info.reply_buf = buf;
		info.reply_buf_len = buf_len;
		nlmsg = prepare_vendor_nlmsg(drv, bss->ifname,
		                             info.subcmd);
		if (!nlmsg) {
		        wpa_printf(MSG_ERROR,"Failed to allocate nl message");
		        return -1;
		}

		status = send_nlmsg((struct nl_sock *)drv->global->nl, nlmsg,
		                     response_handler, &info);
		if (status != 0) {
		        wpa_printf(MSG_ERROR,"Failed to send nl message with err %d", status);
		        return -1;
		}

		return WPA_DRIVER_OEM_STATUS_SUCCESS;
	} else if ((ret = check_for_twt_cmd(&cmd)) != TWT_CMD_NOT_EXIST) {
		enum qca_wlan_twt_operation twt_oper = ret;
		u8 is_twt_feature_supported = 0;

		if (oem_cb_table) {
			for (lib_n = 0;
			     oem_cb_table[lib_n].wpa_driver_driver_cmd_oem_cb != NULL;
			     lib_n++)
			{
				if (oem_cb_table[lib_n].wpa_driver_oem_feature_check_cb) {
					if (oem_cb_table[lib_n].wpa_driver_oem_feature_check_cb(FEATURE_TWT_SUPPORT))
						is_twt_feature_supported = 1;
						break;
				}
			}
		}

		if (is_twt_feature_supported) {
			wpa_printf(MSG_ERROR, "%s: TWT feature already supported by oem lib %d\n", __func__, lib_n);
			ret = -EINVAL;
		} else {
			ret = wpa_driver_twt_cmd_handler(drv, bss->ifname, twt_oper, cmd, buf, buf_len,
							 &status);
			if (ret)
				ret = os_snprintf(buf, buf_len, "TWT failed for operation %d", twt_oper);
		}
	} else if (os_strncasecmp(cmd, "MCC_QUOTA", 9) == 0) {
		/* DRIVER MCC_QUOTA set iface <name> quota <val>
		 * DRIVER MCC_QUOTA clear iface <name>
		 */
		/* Move cmd by string len and space */
		cmd += 10;
		return wpa_driver_cmd_send_mcc_quota(priv, cmd);
	} else if (os_strncasecmp(cmd, "FLUSH_QUEUE_CONFIG", 18) == 0) {
		/* DRIVER FLUSH_QUEUE_CONFIG set peer <mac addr> policy <val>
		 * tid <tid mask> ac <ac mask>
		 */
		/* Move cmd by string len and space */
		cmd += 19;
		return wpa_driver_cmd_send_peer_flush_queue_config(priv, cmd);
	} else { /* Use private command */
		memset(&ifr, 0, sizeof(ifr));
		memset(&priv_cmd, 0, sizeof(priv_cmd));
		os_memcpy(buf, cmd, strlen(cmd) + 1);
		os_strlcpy(ifr.ifr_name, bss->ifname, IFNAMSIZ);

		priv_cmd.buf = buf;
		priv_cmd.used_len = buf_len;
		priv_cmd.total_len = buf_len;
		ifr.ifr_data = &priv_cmd;

		if ((ret = ioctl(drv->global->ioctl_sock, SIOCDEVPRIVATE + 1, &ifr)) < 0) {
			wpa_printf(MSG_ERROR, "%s: failed to issue private commands, ret:%d, errno:%d\n", __func__, ret, errno);
		} else {
			drv_errors = 0;
			if((os_strncasecmp(cmd, "SETBAND", 7) == 0) &&
				ret == DO_NOT_SEND_CHANNEL_CHANGE_EVENT) {
				return 0;
			}

			ret = 0;
			if ((os_strcasecmp(cmd, "LINKSPEED") == 0) ||
			    (os_strcasecmp(cmd, "RSSI") == 0) ||
			    (os_strstr(cmd, "GET") != NULL))
				ret = strlen(buf);
			else if (os_strcasecmp(cmd, "P2P_DEV_ADDR") == 0)
				wpa_printf(MSG_DEBUG, "%s: P2P: Device address ("MACSTR")",
					__func__, MAC2STR(buf));
			else if (os_strcasecmp(cmd, "P2P_SET_PS") == 0)
				wpa_printf(MSG_DEBUG, "%s: P2P: %s ", __func__, buf);
			else if (os_strcasecmp(cmd, "P2P_SET_NOA") == 0)
				wpa_printf(MSG_DEBUG, "%s: P2P: %s ", __func__, buf);
			else if (os_strcasecmp(cmd, "STOP") == 0) {
				wpa_printf(MSG_DEBUG, "%s: %s ", __func__, buf);
				dl_list_for_each(driver, &drv->global->interfaces, struct wpa_driver_nl80211_data, list) {
					linux_set_iface_flags(drv->global->ioctl_sock, driver->first_bss->ifname, 0);
					wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "STOPPED");
				}
			}
			else
				wpa_printf(MSG_DEBUG, "%s %s len = %d, %zu", __func__, buf, ret, buf_len);
			wpa_driver_notify_country_change(drv->ctx, cmd);
		}
	}
	return ret;
}

int wpa_driver_set_p2p_noa(void *priv, u8 count, int start, int duration)
{
	char buf[MAX_DRV_CMD_SIZE];
	char reply_buf[MAX_DRV_CMD_SIZE];

	memset(buf, 0, sizeof(buf));
	memset(reply_buf, 0, sizeof(reply_buf));
	wpa_printf(MSG_DEBUG, "%s: Entry", __func__);
	snprintf(buf, sizeof(buf), "P2P_SET_NOA %d %d %d", count, start, duration);
	return wpa_driver_nl80211_driver_cmd(priv, buf, reply_buf, sizeof(reply_buf));
}

int wpa_driver_get_p2p_noa(void *priv, u8 *buf, size_t len)
{
	UNUSED(priv), UNUSED(buf), UNUSED(len);
	/* Return 0 till we handle p2p_presence request completely in the driver */
	return 0;
}

int wpa_driver_set_p2p_ps(void *priv, int legacy_ps, int opp_ps, int ctwindow)
{
	char buf[MAX_DRV_CMD_SIZE];
	char reply_buf[MAX_DRV_CMD_SIZE];

	memset(buf, 0, sizeof(buf));
	memset(reply_buf, 0, sizeof(reply_buf));
	wpa_printf(MSG_DEBUG, "%s: Entry", __func__);
	snprintf(buf, sizeof(buf), "P2P_SET_PS %d %d %d", legacy_ps, opp_ps, ctwindow);
	return wpa_driver_nl80211_driver_cmd(priv, buf, reply_buf, sizeof(reply_buf));
}

int wpa_driver_set_ap_wps_p2p_ie(void *priv, const struct wpabuf *beacon,
				 const struct wpabuf *proberesp,
				 const struct wpabuf *assocresp)
{
	UNUSED(priv), UNUSED(beacon), UNUSED(proberesp), UNUSED(assocresp);
	return 0;
}
