/*
 * Driver interaction with extended Linux CFG80211
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
#include "common.h"
#include "wpa_driver_common_lib.h"
#include "qca-vendor_copy.h"
#include "driver_cmd_nl80211_common.h"
#include "driver_cmd_nl80211_extn.h"
#include <netlink/genl/genl.h>
#include <netlink/genl/ctrl.h>
#include <netlink/object-api.h>
#include <linux/pkt_sched.h>
#include <net/if.h>

#define SR_RESP_BUF_LEN 512

/**
 * wpa_driver_check_for_sr_cmd() - check and return spatial reuse operation command
 *
 * @cmd: spatial reuse operation command
 * @sr_cmd : spatial reuse operation attribute
 *
 * Return: returns enum qca_wlan_sr_operation on success, error code on failure.
 */
static int wpa_driver_check_for_sr_cmd(char *cmd, enum qca_wlan_sr_operation *sr_cmd)
{

	if (os_strncasecmp(cmd, "enable", 6) == 0)
		*sr_cmd = QCA_WLAN_SR_OPERATION_SR_ENABLE;
	else if (os_strncasecmp(cmd, "disable", 7) == 0)
		*sr_cmd = QCA_WLAN_SR_OPERATION_SR_DISABLE;
	else if (os_strncasecmp(cmd, "sr_prohibit_enable", 18) == 0)
		*sr_cmd = QCA_WLAN_SR_OPERATION_PSR_AND_NON_SRG_OBSS_PD_PROHIBIT;
	else if (os_strncasecmp(cmd, "sr_prohibit_disable", 19) == 0)
		*sr_cmd = QCA_WLAN_SR_OPERATION_PSR_AND_NON_SRG_OBSS_PD_ALLOW;
	else if (os_strncasecmp(cmd, "getstats", 8) == 0)
		*sr_cmd = QCA_WLAN_SR_OPERATION_GET_STATS;
	else if (os_strncasecmp(cmd, "clearstats", 10) == 0)
		*sr_cmd = QCA_WLAN_SR_OPERATION_CLEAR_STATS;
	else if (os_strncasecmp(cmd, "getparams", 9) == 0) {
		cmd += 9;
		cmd = skip_white_space(cmd);
		if (*cmd != '\0')
			return -EINVAL;
		else
			*sr_cmd = QCA_WLAN_SR_OPERATION_GET_PARAMS;
	} else {
		wpa_printf(MSG_ERROR, "Unknown SR command:%s\n", cmd);
		return -EINVAL;
	}
	return 0;
}

/**
 * parse_sr_get_stats_response() - Parse the spatial reuse getstats response received from driver
 *
 * @info: structure to store driver response
 * @vendata: vendor data
 * @datalen: vendor data length
 *
 * Return: returns 0 on Success, error code on invalid response
 */
static int parse_sr_get_stats_response(struct resp_info *info, struct nlattr *vendata, int datalen)
{

	int cmd_id, ret;
	u32 non_srg_tx_opportunities_count = 0, non_srg_tx_ppdu_tried_count = 0,
	non_srg_tx_ppdu_success_count = 0, srg_tx_opportunities_count = 0,
	srg_tx_ppdu_tried_count = 0, srg_tx_ppdu_success_count = 0;

	struct nlattr *sr_attr[QCA_WLAN_VENDOR_ATTR_SR_STATS_MAX + 1];

	ret = nla_parse_nested(sr_attr, QCA_WLAN_VENDOR_ATTR_SR_STATS_MAX, vendata, NULL);
	if (ret) {
		wpa_printf(MSG_ERROR, "SR stats nla_parse fail, error:%d\n", ret);
		return ret;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_STATS_NON_SRG_TX_OPPORTUNITIES_COUNT;
	if (sr_attr[cmd_id])
		non_srg_tx_opportunities_count = nla_get_u32(sr_attr[cmd_id]);

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_STATS_NON_SRG_TX_PPDU_TRIED_COUNT;
	if (sr_attr[cmd_id])
		non_srg_tx_ppdu_tried_count = nla_get_u32(sr_attr[cmd_id]);

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_STATS_NON_SRG_TX_PPDU_SUCCESS_COUNT;
	if (sr_attr[cmd_id])
		non_srg_tx_ppdu_success_count = nla_get_u32(sr_attr[cmd_id]);

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_STATS_SRG_TX_OPPORTUNITIES_COUNT;
	if (sr_attr[cmd_id])
		srg_tx_opportunities_count = nla_get_u32(sr_attr[cmd_id]);

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_STATS_SRG_TX_PPDU_TRIED_COUNT;
	if (sr_attr[cmd_id])
		srg_tx_ppdu_tried_count = nla_get_u32(sr_attr[cmd_id]);

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_STATS_SRG_TX_PPDU_SUCCESS_COUNT;
	if (sr_attr[cmd_id])
		srg_tx_ppdu_success_count = nla_get_u32(sr_attr[cmd_id]);

	if (!info->reply_buf || !info->reply_buf_len) {
		ret = -ENOBUFS;
		wpa_printf(MSG_ERROR, "%s:buffer is NULL, error:%d\n", __func__, ret);
		return ret;
	}

	ret = os_snprintf(info->reply_buf, info->reply_buf_len,
			  "non_srg_tx_opportunities_count : %u\n"
			  "non_srg_tx_ppdu_tried_count : %u\nnon_srg_tx_ppdu_success_count : %u\n"
			  "srg_tx_opportunities_count : %u\nsrg_tx_ppdu_tried_count : %u\n"
			  "srg_tx_ppdu_success_count : %u\n", non_srg_tx_opportunities_count,
			  non_srg_tx_ppdu_tried_count, non_srg_tx_ppdu_success_count,
			  srg_tx_opportunities_count, srg_tx_ppdu_tried_count,
			  srg_tx_ppdu_success_count);

	if (os_snprintf_error(info->reply_buf_len, ret)) {
		wpa_printf(MSG_ERROR, "%s:Fail to print buffer, error:%d\n", __func__, ret);
		return ret;
	}

	return 0;
}

static int parse_sr_get_params_response(struct resp_info *info,
					struct nlattr *vendata, int datalen)
{
	int cmd_id, ret;
	u8 srg_pd_offset_min = 0, srg_pd_offset_max = 0,
	non_srg_pd_offset_max = 0, hesiga_val15_enable = 1,
	non_srg_pd_disallow = 1;
	struct nlattr *sr_attr[QCA_WLAN_VENDOR_ATTR_SR_PARAMS_MAX + 1];

	ret = nla_parse_nested(sr_attr, QCA_WLAN_VENDOR_ATTR_SR_PARAMS_MAX,
			       vendata, NULL);
	if (ret) {
		wpa_printf(MSG_ERROR, "SR params: nla_parse fail, error: %d", ret);
		return ret;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_SRG_OBSS_PD_MIN_OFFSET;
	if (sr_attr[cmd_id])
		srg_pd_offset_min = nla_get_u8(sr_attr[cmd_id]);
	else
		wpa_printf(MSG_INFO, "SR params: SRG PD min offset not found");

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_SRG_OBSS_PD_MAX_OFFSET;
	if (sr_attr[cmd_id])
		srg_pd_offset_max = nla_get_u8(sr_attr[cmd_id]);
	else
		wpa_printf(MSG_INFO, "SR params: SRG PD max offset not found");

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_NON_SRG_OBSS_PD_MAX_OFFSET;
	if (sr_attr[cmd_id])
		non_srg_pd_offset_max = nla_get_u8(sr_attr[cmd_id]);
	else
		wpa_printf(MSG_INFO, "SR params: Non SRG PD max offset not found");

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_HESIGA_VAL15_ENABLE;
	if (!sr_attr[cmd_id]) {
		wpa_printf(MSG_INFO, "SR params: Hesiga Val15 is not enabled by AP");
		hesiga_val15_enable = 0;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_NON_SRG_OBSS_PD_DISALLOW;
	if (!sr_attr[cmd_id]) {
		wpa_printf(MSG_INFO, "SR params: non SRG PD is not allowed by AP");
		non_srg_pd_disallow = 0;
	}

	ret = os_snprintf(info->reply_buf, info->reply_buf_len,
			"srg_obss_pd_min_offset: %u\nsrg_obss_pd_max_offset: %u\n"
			"non_srg_obss_pd_max_offset: %u\nhesiga_val15_enable: %u\n"
			"non_srg_pd_disallow: %u", srg_pd_offset_min,
			srg_pd_offset_max, non_srg_pd_offset_max,
			hesiga_val15_enable, non_srg_pd_disallow);
	if (os_snprintf_error(info->reply_buf_len, ret)) {
		wpa_printf(MSG_ERROR, "SR params: Failed to put in buffer, error: %d", ret);
		return ret;
	}
	return 0;
}

/**
 * sr_response_unpack() - unpack the spatial reuse command response received from driver
 *
 * @info: structure to store driver response
 * @vendata: vendor data
 * @datalen: vendor data length
 *
 * Return: returns 0 on Success, error code on invalid response
 */
static int sr_response_unpack(struct resp_info *info, struct nlattr *vendata, int datalen)
{

	int ret;
	if (!info) {
		wpa_printf(MSG_ERROR, "%s:Invalid arguments\n", __func__);
		return -EINVAL;
	}
	switch (info->cmd_oper) {
	case QCA_WLAN_SR_OPERATION_GET_STATS:
		ret = parse_sr_get_stats_response(info, vendata, datalen);
		if (ret)
			wpa_printf(MSG_ERROR, "Unpacking SR stats failed, error:%d", ret);
		break;
	case QCA_WLAN_SR_OPERATION_GET_PARAMS:
		ret = parse_sr_get_params_response(info, vendata, datalen);
		if (ret)
			wpa_printf(MSG_ERROR, "Unpacking SR params failed, error:%d", ret);
		break;
	default:
		ret = -EINVAL;
		wpa_printf(MSG_ERROR, "Unsupported SR command:%d, error:%d",
			   info->cmd_oper, ret);
		break;
	}
	return ret;
}

/**
 * sr_response_handler() - handle spatial reuse commands response received from driver
 *
 * @info: structure to store driver response
 * @vendata: vendor data
 * @datalen: vendor data length
 *
 * Return: returns 0 on Success, error code on invalid response
 */
int sr_response_handler(struct resp_info *info, struct nlattr *vendata, int datalen)
{

	int ret;
	struct wpa_driver_nl80211_data *drv;

	if (!info || !info->drv) {
		wpa_printf(MSG_ERROR, "%s:Invalid arguments\n", __func__);
		return -EINVAL;
	}

	drv = info->drv;

	ret = sr_response_unpack(info, vendata, datalen);
	switch (info->cmd_oper) {
	case QCA_WLAN_SR_OPERATION_GET_STATS:
		if (!ret)
			wpa_msg(drv->ctx, MSG_INFO, "CTRL-EVENT-SR STATS RESPONSE\n"
				"%s", info->reply_buf);
		else
			wpa_msg(drv->ctx, MSG_ERROR, "CTRL-EVENT-SR STATS RESPONSE\n"
				" %s : Error = %d", info->reply_buf, ret);
		break;
	case QCA_WLAN_SR_OPERATION_GET_PARAMS:
		if (!ret)
			wpa_msg(drv->ctx, MSG_INFO, "CTRL-EVENT-SR PARAMS RESPONSE\n"
				"%s", info->reply_buf);
		else
			wpa_msg(drv->ctx, MSG_ERROR, "CTRL-EVENT-SR PARAMS RESPONSE\n"
				" %s : Error = %d", info->reply_buf, ret);
		break;
	}
	return ret;
}

static int pack_sr_enable_nlmsg(struct nl_msg *nlmsg, char *cmd)
{

	struct nlattr *sr_attr;
	s8 is_srg_pd_cmd = 0, is_non_srg_pd_cmd = 0;
	s32 pd_thres = 0, cmd_id;
	int ret;

	cmd += 6;
	cmd = skip_white_space(cmd);
	if (*cmd == '\0')
		return 0;

	sr_attr = nla_nest_start(nlmsg, QCA_WLAN_VENDOR_ATTR_SR_PARAMS);
	if (!sr_attr)
		return -ENOMEM;

	for (int i = 0; i < 2; i++) {
		if (os_strncasecmp(cmd, "srg_pd_threshold ", 17) == 0) {
			cmd += 17;
			cmd = skip_white_space(cmd);
			pd_thres = get_s32_from_string(cmd, &ret);
			if (ret < 0 || pd_thres < OBSS_PD_THRESHOLD_MIN ||
			    pd_thres > OBSS_PD_THRESHOLD_MAX) {
				wpa_printf(MSG_ERROR, "Invalid SRG PD threshold: %d", pd_thres);
				return -EINVAL;
			}
			cmd_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_SRG_PD_THRESHOLD;
			if (nla_put_s32(nlmsg, cmd_id, pd_thres)) {
				wpa_printf(MSG_ERROR, "Failed to put SRG PD threshold");
				return -ENOMEM;
			}
			is_srg_pd_cmd++;
		} else if (os_strncasecmp(cmd, "non_srg_pd_threshold ", 21) == 0) {
			cmd += 21;
			cmd = skip_white_space(cmd);
			pd_thres = get_s32_from_string(cmd, &ret);
			if (ret < 0 || pd_thres < OBSS_PD_THRESHOLD_MIN ||
			    pd_thres > OBSS_PD_THRESHOLD_MAX) {
				wpa_printf(MSG_ERROR, "Invalid Non-SRG PD threshold: %d", pd_thres);
				return -EINVAL;
			}
			cmd_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_NON_SRG_PD_THRESHOLD;
			if (nla_put_s32(nlmsg, cmd_id, pd_thres)) {
				wpa_printf(MSG_ERROR, "Failed to put Non-SRG PD threshold");
				return -ENOMEM;
			}
			is_non_srg_pd_cmd++;
		} else if (*cmd == '\0')
			break;
		else
			return -EINVAL;

		if (is_srg_pd_cmd > 1 || is_non_srg_pd_cmd > 1)
			return -EINVAL;

		cmd = get_next_arg(cmd);
		cmd = skip_white_space(cmd);
	}
	nla_nest_end(nlmsg, sr_attr);
	return 0;
}

/**
 * wpa_driver_sr_cmd() - handle the spatial reuse commands
 *
 * @bss: nl data
 * @cmd: spatial reuse vendor command
 * @buf: stores the response
 * @buf_len: length of the response buffer
 *
 * Return: returns 0 on Success, error code on invalid response
 */
int wpa_driver_sr_cmd(struct i802_bss *bss, char *cmd, char *buf, size_t buf_len)
{

	struct wpa_driver_nl80211_data *drv;
	struct nl_msg *nlmsg = NULL;
	struct resp_info info;
	enum qca_wlan_sr_operation sr_cmd;
	int status;
	struct nlattr *attr;

	cmd = skip_white_space(cmd);
	status = wpa_driver_check_for_sr_cmd(cmd, &sr_cmd);
	if (status == -EINVAL) {
		wpa_printf(MSG_ERROR, "Invalid SR command, error:%d\n", status);
		return status;
	}

	if (!buf || !buf_len) {
		status = -ENOBUFS;
		wpa_printf(MSG_ERROR, "%s:buffer is NULL, error:%d\n", __func__, status);
		return status;
	}

	if (!bss || !bss->drv) {
		wpa_printf(MSG_ERROR, "%s:Invalid arguments\n", __func__);
		return -EINVAL;
	}

	drv = bss->drv;
	os_memset(&info, 0, sizeof(struct resp_info));
	os_memset(buf, 0, buf_len);
	info.cmd_oper = sr_cmd;
	info.reply_buf = buf;
	info.reply_buf_len = buf_len;
	info.drv = drv;
	info.subcmd = QCA_NL80211_VENDOR_SUBCMD_SR;

	nlmsg = prepare_vendor_nlmsg(drv, bss->ifname, QCA_NL80211_VENDOR_SUBCMD_SR);
	if (!nlmsg) {
		status = -ENOMEM;
		wpa_printf(MSG_ERROR, "Fail to allocate nlmsg for SR command:%d, error:%d\n",
			   sr_cmd, status);
		return status;
	}
	attr = nla_nest_start(nlmsg, NL80211_ATTR_VENDOR_DATA);
	if (!attr) {
		status = -ENOMEM;
		wpa_printf(MSG_ERROR, "Fail to create attribute for SR command:%d, error:%d\n",
			   sr_cmd, status);
		goto nlmsg_fail;
	}
	switch (sr_cmd) {
		case QCA_WLAN_SR_OPERATION_SR_ENABLE:
			status = pack_sr_enable_nlmsg(nlmsg, cmd);
			if (status < 0) {
				wpa_printf(MSG_ERROR, "SR enable command failed: %d,"
					   "error:%d", sr_cmd, status);
				goto nlmsg_fail;
			}
		case QCA_WLAN_SR_OPERATION_SR_DISABLE:
		case QCA_WLAN_SR_OPERATION_PSR_AND_NON_SRG_OBSS_PD_PROHIBIT:
		case QCA_WLAN_SR_OPERATION_PSR_AND_NON_SRG_OBSS_PD_ALLOW:
		case QCA_WLAN_SR_OPERATION_GET_STATS:
		case QCA_WLAN_SR_OPERATION_CLEAR_STATS:
		case QCA_WLAN_SR_OPERATION_GET_PARAMS:
			status = nla_put_u8(nlmsg, QCA_WLAN_VENDOR_ATTR_SR_OPERATION, sr_cmd);
			if (status) {
				wpa_printf(MSG_ERROR, "Fail to put SR command:%d, error:%d\n",
					   sr_cmd, status);
				goto nlmsg_fail;
			}
			nla_nest_end(nlmsg, attr);
			break;
		default:
			status = -EINVAL;
			wpa_printf(MSG_ERROR, "Unsupported SR command:%d, error:%d\n",
				   sr_cmd, status);
			goto nlmsg_fail;
	}
	if (sr_cmd == QCA_WLAN_SR_OPERATION_GET_STATS ||
	    sr_cmd == QCA_WLAN_SR_OPERATION_GET_PARAMS)
		status = send_nlmsg((struct nl_sock *)drv->global->nl,
				    nlmsg, response_handler, &info);
	else
		status = send_nlmsg((struct nl_sock *)drv->global->nl,
				    nlmsg, NULL, NULL);

	if (status) {
		wpa_printf(MSG_ERROR, "Fail to send nlmsg SR command:%d to driver, error:%d\n",
			   sr_cmd, status);
		return status;
	}
	return 0;

nlmsg_fail:
	nlmsg_free(nlmsg);
	return status;
}

/**
 * parse_sr_resume_event()- parse and  prints the Spatial Reuse Resume event
 * receive from driver when it has resumed SR feature after disabling it.
 *
 * @tb: vendor nl data
 * @buf: stores the response
 * @buf_len: length of the response buffer
 *
 * Return: returns 0 on Success, error code on invalid response
 */
static int parse_sr_resume_event(struct nlattr **tb, char *buf, int buf_len)
{

	int ret, attr_id;
	u32 reason_code;
	s32 srg_pd_thres = 0, non_srg_pd_thres = 0;
	char temp[SR_RESP_BUF_LEN];
	struct nlattr *params_attr[QCA_WLAN_VENDOR_ATTR_SR_PARAMS_MAX + 1];

	if (!buf || !buf_len) {
		ret = -ENOBUFS;
		goto buf_fail;
	}

	ret = nla_parse_nested(params_attr, QCA_WLAN_VENDOR_ATTR_SR_PARAMS_MAX,
			       tb[QCA_WLAN_VENDOR_ATTR_SR_PARAMS], NULL);
	if (ret) {
		wpa_printf(MSG_ERROR, "Parsing of SR Resume event nlmsg failed"
			   "error:%d\n", ret);
		return ret;
	}

	attr_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_REASON_CODE;
	if (params_attr[attr_id]) {
		reason_code = nla_get_u8(params_attr[attr_id]);
		os_memset(temp, 0, SR_RESP_BUF_LEN);
		if (reason_code == QCA_WLAN_SR_REASON_CODE_ROAMING) {
			ret = os_snprintf(temp, SR_RESP_BUF_LEN,
					  "SR Resume - Reason : Roaming (code : %u)\n",
					  reason_code);
			if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
				goto snprintf_fail;
		} else if (reason_code == QCA_WLAN_SR_REASON_CODE_CONCURRENCY) {
			ret = os_snprintf(temp, SR_RESP_BUF_LEN,
					  "SR Resume - Reason : Concurrency (code : %u)\n",
					  reason_code);
			if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
				goto snprintf_fail;
		} else {
			ret = os_snprintf(temp, SR_RESP_BUF_LEN,
					  "SR Resume - Reason : Invalid Reason (code : %u)\n",
					  reason_code);
			if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
				goto snprintf_fail;
		}
		buf = result_copy_to_buf(temp, buf, &buf_len);

		if (!buf) {
			ret = -EINVAL;
			goto buf_fail;
		}
	} else
		wpa_printf(MSG_ERROR, "SR Resume - Reason code not found\n");

	attr_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_SRG_PD_THRESHOLD;
	if (params_attr[attr_id]) {
		srg_pd_thres = nla_get_s32(params_attr[attr_id]);
		os_memset(temp, 0, SR_RESP_BUF_LEN);
		ret = os_snprintf(temp, SR_RESP_BUF_LEN, "srg_pd_thres:%u", srg_pd_thres);
		if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
			goto snprintf_fail;
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf) {
			ret = -EINVAL;
			goto buf_fail;
		}
	} else
		wpa_printf(MSG_ERROR, "SR Resume - SRG PD threshold not found\n");

	attr_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_NON_SRG_PD_THRESHOLD;
	if (params_attr[attr_id]) {
		non_srg_pd_thres = nla_get_s32(params_attr[attr_id]);
		os_memset(temp, 0, SR_RESP_BUF_LEN);
		ret = os_snprintf(temp, SR_RESP_BUF_LEN, "non_srg_pd_thres:%u", non_srg_pd_thres);
		if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
			goto snprintf_fail;
		buf = result_copy_to_buf(temp, buf, &buf_len);
		if (!buf) {
			ret = -EINVAL;
			goto buf_fail;
		}
	} else
		wpa_printf(MSG_ERROR, "SR Resume - non SRG PD threshold not found\n");

	*buf = '\0';

	return 0;

buf_fail:
	wpa_printf(MSG_ERROR, "%s:buffer is NULL, error:%d\n", __func__, ret);
	return ret;

snprintf_fail:
	wpa_printf(MSG_ERROR, "%s:Fail to print the buffer, error:%d\n", __func__, ret);
	return ret;
}

/**
 * parse_sr_suspend_event()- parse and prints the Spatial Reuse Suspend event
 * receive from driver event when driver has disabled SR feature.
 *
 * @tb: vendor nl data
 * @buf: stores the response
 * @buf_len: length of the response buffer
 *
 * @Return: returns 0 on Success, error code on invalid response
 */
static int parse_sr_suspend_event(struct nlattr **tb, char *buf, int buf_len)
{

	int ret, attr_id;
	u32 reason_code;
	char temp[SR_RESP_BUF_LEN];
	struct nlattr *params_attr[QCA_WLAN_VENDOR_ATTR_SR_PARAMS_MAX + 1];

	if (!buf || !buf_len) {
		ret = -ENOBUFS;
		goto buf_fail;
	}

	ret = nla_parse_nested(params_attr, QCA_WLAN_VENDOR_ATTR_SR_PARAMS_MAX,
			       tb[QCA_WLAN_VENDOR_ATTR_SR_PARAMS], NULL);
	if (ret) {
		wpa_printf(MSG_ERROR, "Parsing of SR Suspend event nlmsg failed,"
			   "error:%d\n", ret);
		return ret;
	}

	attr_id = QCA_WLAN_VENDOR_ATTR_SR_PARAMS_REASON_CODE;
	if (params_attr[attr_id]) {
		reason_code = nla_get_u8(params_attr[attr_id]);
		os_memset(temp, 0, SR_RESP_BUF_LEN);

		if (reason_code == QCA_WLAN_SR_REASON_CODE_ROAMING) {
			ret = os_snprintf(temp, SR_RESP_BUF_LEN,
					  "SR Suspend - Reason : Roaming (code : %u)\n",
					  reason_code);
			if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
				goto snprintf_fail;
		} else if (reason_code == QCA_WLAN_SR_REASON_CODE_CONCURRENCY) {
			ret = os_snprintf(temp, SR_RESP_BUF_LEN,
					  "SR Suspend - Reason : Concurrency (code : %u)\n",
					  reason_code);
			if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
				goto snprintf_fail;
		} else {
			ret = os_snprintf(temp, SR_RESP_BUF_LEN,
					  "SR Suspend - Reason : Invalid Reason (code : %u)\n",
					  reason_code);
			if (os_snprintf_error(SR_RESP_BUF_LEN, ret))
				goto snprintf_fail;
		}
		buf = result_copy_to_buf(temp, buf, &buf_len);

		if (!buf) {
			ret = -EINVAL;
			goto buf_fail;
		}
	} else
		wpa_printf(MSG_ERROR, "SR Suspend - Reason code not found\n");

	*buf = '\0';
	return 0;

buf_fail:
	wpa_printf(MSG_ERROR, "%s:buffer is NULL, error:%d\n", __func__, ret);
	return ret;

snprintf_fail:
	wpa_printf(MSG_ERROR, "%s:Fail to print the buffer, error:%d\n", __func__, ret);
	return ret;
}

/**
 * wpa_driver_sr_event()- handle Spatial Reuse event
 * receive from the driver.
 *
 * @drv: wpa_driver_nl80211_data
 * @vendor_id: vendor id for vendor specific command
 * @subcmd: subcmd as defined by enum qca_nl80211_vendor_subcmds
 * @data: vendor data
 * @len: vendor data length
 *
 * @Returns: returns 0 on Success, error code on Failure
 */
int wpa_driver_sr_event(struct wpa_driver_nl80211_data *drv,
				   u32 vendor_id, u32 subcmd, u8 *data, size_t len)
{

	int ret = 0, cmd_id;
	size_t buf_len = SR_RESP_BUF_LEN;
	char *buf;
	struct nlattr *tb[QCA_WLAN_VENDOR_ATTR_SR_MAX + 1];
	enum qca_wlan_sr_operation sr_operation_type;

	buf = (char *)os_malloc(SR_RESP_BUF_LEN);
	if (!buf) {
		ret = -ENOMEM;
		wpa_printf(MSG_ERROR, "Fail to allocate buffer for SR cmd:%d, error:%d\n",
			   subcmd, ret);
		return ret;
	}

	if (!drv || !len) {
		wpa_printf(MSG_ERROR, "%s:Invalid arguments\n", __func__);
		goto fail;
	}

	ret = nla_parse(tb, QCA_WLAN_VENDOR_ATTR_SR_MAX, (struct nlattr *) data, len, NULL);
	if (ret) {
		wpa_printf(MSG_ERROR, "Fail to parse SR attribute, error:%d\n", ret);
		goto fail;
	}

	cmd_id = QCA_WLAN_VENDOR_ATTR_SR_OPERATION;
	if (tb[cmd_id])
		sr_operation_type = nla_get_u8(tb[cmd_id]);
	else {
		ret = -1;
		wpa_printf(MSG_ERROR, "SR operation attribute not found, error:%d\n", ret);
		goto fail;
	}

	switch (sr_operation_type) {
		case QCA_WLAN_SR_OPERATION_SR_RESUME:
			ret = parse_sr_resume_event(tb, buf, buf_len);
			if (ret) {
				wpa_printf(MSG_ERROR, "Unpacking of SR Resume nlmsg failed,"
					   " error:%d\n", ret);
				goto fail;
			}
			break;
		case QCA_WLAN_SR_OPERATION_SR_SUSPEND:
			ret = parse_sr_suspend_event(tb, buf, buf_len);
			if (ret) {
				wpa_printf(MSG_ERROR, "Unpacking of SR Suspend nlmsg failed,"
					   " error:%d\n", ret);
				goto fail;
			}
			break;
		default:
			ret = -EINVAL;
			wpa_printf(MSG_ERROR, "SR command:%d event parsing failed, error:%d\n",
				   sr_operation_type, ret);
			goto fail;
	}

	if (!ret)
		wpa_msg(drv->ctx, MSG_INFO, "CTRL-EVENT-SR\n" "%s", buf);
	else
		wpa_msg(drv->ctx, MSG_INFO, "CTRL-EVENT-SR\n" "%s:Error = %d", buf, ret);

fail:
	free(buf);
	return ret;
}
