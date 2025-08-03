/*
 * Copyright (c) 2022 The Android Open Source Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

#include "pmsr.h"

#include <errno.h>
#include <stdio.h>

static int parse_pmsr_request_ftm(struct nlattr *req,
				  struct pmsr_request_ftm *out)
{
	struct nlattr *tb[NL80211_PMSR_FTM_REQ_ATTR_MAX + 1];

	nla_parse_nested(tb, NL80211_PMSR_FTM_REQ_ATTR_MAX, req, NULL);

	if (tb[NL80211_PMSR_FTM_REQ_ATTR_PREAMBLE])
		out->preamble =
			nla_get_u32(tb[NL80211_PMSR_FTM_REQ_ATTR_PREAMBLE]);

	if (tb[NL80211_PMSR_FTM_REQ_ATTR_BURST_PERIOD])
		out->burst_period =
			nla_get_u32(tb[NL80211_PMSR_FTM_REQ_ATTR_BURST_PERIOD]);

	out->asap = !!tb[NL80211_PMSR_FTM_REQ_ATTR_ASAP];

	if (tb[NL80211_PMSR_FTM_REQ_ATTR_NUM_BURSTS_EXP])
		out->num_bursts_exp =
			nla_get_u32(tb[NL80211_PMSR_FTM_REQ_ATTR_NUM_BURSTS_EXP]);

	if (tb[NL80211_PMSR_FTM_REQ_ATTR_BURST_DURATION])
		out->burst_duration =
			nla_get_u32(tb[NL80211_PMSR_FTM_REQ_ATTR_BURST_DURATION]);

	if (tb[NL80211_PMSR_FTM_REQ_ATTR_FTMS_PER_BURST])
		out->ftms_per_burst =
			nla_get_u32(tb[NL80211_PMSR_FTM_REQ_ATTR_FTMS_PER_BURST]);

	if (tb[NL80211_PMSR_FTM_REQ_ATTR_NUM_FTMR_RETRIES])
		out->ftmr_retries =
			nla_get_u32(tb[NL80211_PMSR_FTM_REQ_ATTR_NUM_FTMR_RETRIES]);

	out->request_lci = !!tb[NL80211_PMSR_FTM_REQ_ATTR_REQUEST_LCI];

	out->request_civicloc =
		!!tb[NL80211_PMSR_FTM_REQ_ATTR_REQUEST_CIVICLOC];

	out->trigger_based =
		!!tb[NL80211_PMSR_FTM_REQ_ATTR_TRIGGER_BASED];

	out->non_trigger_based =
		!!tb[NL80211_PMSR_FTM_REQ_ATTR_NON_TRIGGER_BASED];

	out->lmr_feedback =
		!!tb[NL80211_PMSR_FTM_REQ_ATTR_LMR_FEEDBACK];

	if (tb[NL80211_PMSR_FTM_REQ_ATTR_BSS_COLOR])
		out->bss_color =
			nla_get_u8(tb[NL80211_PMSR_FTM_REQ_ATTR_BSS_COLOR]);

	return 0;
}

int parse_pmsr_channel(struct nlattr *req,
		       struct pmsr_channel *out)
{
	struct nlattr *nla;
	int rem;

	if (!req)
		return -EINVAL;

	nla_for_each_attr(nla, req, nla_len(req), rem) {
		switch (nla_type(nla)) {
		case NL80211_ATTR_WIPHY_FREQ:
			out->center_freq = nla_get_u32(nla);
			break;
		case NL80211_ATTR_WIPHY_FREQ_OFFSET:
			out->freq_offset = nla_get_u32(nla);
			break;
		case NL80211_ATTR_WIPHY_CHANNEL_TYPE:
			out->channel_type = nla_get_u32(nla);
			break;
		case NL80211_ATTR_CHANNEL_WIDTH:
			out->width = nla_get_u32(nla);
			break;
		case NL80211_ATTR_CENTER_FREQ1:
			out->center_freq1 = nla_get_u32(nla);
			break;
		case NL80211_ATTR_CENTER_FREQ2:
			out->center_freq2 = nla_get_u32(nla);
			break;
		default:
			printf("%s: unknown attributes\n", __func__);
		}
	}

	return 0;
}

static int parse_pmsr_request_peer(struct nlattr *req,
				   struct pmsr_request_peer *out)
{
	struct nlattr *tb[NL80211_PMSR_PEER_ATTR_MAX + 1];
	struct nlattr *tb_req[NL80211_PMSR_REQ_ATTR_MAX + 1];
	struct nlattr *nla;
	int err, rem;

	if (!req)
		return EINVAL;

	err = nla_parse_nested(tb, NL80211_PMSR_PEER_ATTR_MAX,
			       req, NULL);
	if (err) {
		printf("%s: Failed to parse PMSR peer\n", __func__);
		return err;
	}

	if (!tb[NL80211_PMSR_PEER_ATTR_ADDR]) {
		printf("%s: Failed to parse PMSR peer. Missing peer address\n", __func__);
		return -EINVAL;
	}

	memcpy(out->addr, nla_data(tb[NL80211_PMSR_PEER_ATTR_ADDR]), ETH_ALEN);

	err = parse_pmsr_channel(tb[NL80211_PMSR_PEER_ATTR_CHAN],
				 &out->channel);
	if (err)
		return err;

	err = nla_parse_nested(tb_req, NL80211_PMSR_REQ_ATTR_MAX,
			       tb[NL80211_PMSR_PEER_ATTR_REQ], NULL);
	if (err)
		return err;

	if (tb_req[NL80211_PMSR_REQ_ATTR_GET_AP_TSF])
		out->report_ap_tsf = true;

	if (!tb_req[NL80211_PMSR_REQ_ATTR_DATA]) {
		printf("%s: missing NL80211_PMSR_REQ_ATTR_DATA\n", __func__);
		return -EINVAL;
	}

	nla_for_each_nested(nla, tb_req[NL80211_PMSR_REQ_ATTR_DATA], rem) {
		switch (nla_type(nla)) {
		case NL80211_PMSR_TYPE_FTM:
			err = parse_pmsr_request_ftm(nla, &out->ftm);
			if (err)
				return err;
			break;
		default:
			printf("%s: unsupported measurement type\n", __func__);
			return -EINVAL;
		}
	}

	return 0;
}

int parse_pmsr_request(struct nlattr *req,
		       struct pmsr_request *out)
{
	struct nlattr *nla;
	struct pmsr_request_peer *peer, *tmp;
	int rem;
	int err = 0;

	if (!req)
		return -EINVAL;

	INIT_LIST_HEAD(&out->peers);
	out->n_peers = 0;

	req = nla_find(nla_data(req), nla_len(req), NL80211_ATTR_PEER_MEASUREMENTS);

	nla_for_each_nested(nla, req, rem) {
		switch (nla_type(nla)) {
		case NL80211_ATTR_TIMEOUT:
			out->timeout = nla_get_u32(nla);
			break;
		case NL80211_ATTR_MAC:
			memcpy(out->mac_addr, nla_data(nla), ETH_ALEN);
			break;
		case NL80211_ATTR_MAC_MASK:
			memcpy(out->mac_addr_mask, nla_data(nla), ETH_ALEN);
			break;
		case NL80211_PMSR_ATTR_PEERS:
			peer = calloc(1, sizeof(struct pmsr_request_peer));
			list_add(&peer->list, &out->peers);
			err = parse_pmsr_request_peer(nla, peer);
			if (err)
				goto out_err;
			out->n_peers++;
			break;
		}
	}

out_err:
	if (err)
		list_for_each_entry_safe(peer, tmp, &out->peers, list) {
			list_del(&peer->list);
			free(peer);
		}
	return err;
}

