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

#ifndef PMSR_H_
#define PMSR_H_

#include <stdint.h>
#include <stdbool.h>
#include <netlink/attr.h>
#include <linux/nl80211.h>

#include "ieee80211.h"
#include "list.h"

struct pmsr_channel {
	uint32_t center_freq;
	uint32_t freq_offset;
	enum nl80211_channel_type channel_type;
	enum nl80211_chan_width width;
	uint32_t center_freq1;
	uint32_t center_freq2;
};

struct pmsr_request_ftm {
	enum nl80211_preamble preamble;
	uint32_t burst_period;
	uint8_t asap:1,
	   request_lci:1,
	   request_civicloc:1,
	   trigger_based:1,
	   non_trigger_based:1,
	   lmr_feedback:1;
	uint8_t num_bursts_exp;
	uint8_t burst_duration;
	uint8_t ftms_per_burst;
	uint8_t ftmr_retries;
	uint8_t bss_color;
};

struct pmsr_request_peer {
	struct list_head list;

	uint8_t addr[ETH_ALEN];
	struct pmsr_channel channel;
	uint8_t report_ap_tsf:1;
	struct pmsr_request_ftm ftm;
};

struct pmsr_request {
	uint32_t timeout;

	// Only for mac randomization
	uint8_t mac_addr[ETH_ALEN];
	uint8_t mac_addr_mask[ETH_ALEN];

	uint32_t n_peers;

	// keeps pmsr_request_peer
	struct list_head peers;
};

/*
 * Parse pmsr request.
 *
 * Caller has responsibility to release peers in returned pmsr_request.
 */
int parse_pmsr_request(struct nlattr *req, struct pmsr_request *out);

#endif /* PMSR_H_ */
