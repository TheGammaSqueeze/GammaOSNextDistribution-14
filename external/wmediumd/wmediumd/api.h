/*
 * Copyright (C) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _WMEDIUMD_API_H
#define _WMEDIUMD_API_H
#include <stdint.h>

#include "ieee80211.h"

enum wmediumd_message {
	/* invalid message */
	WMEDIUMD_MSG_INVALID,

	/* ACK, returned for each message for synchronisation */
	WMEDIUMD_MSG_ACK,

	/*
	 * Register/unregister for frames, this may be a pure control
	 * socket which doesn't want to see frames.
	 */
	WMEDIUMD_MSG_REGISTER,
	WMEDIUMD_MSG_UNREGISTER,

	/*
	 * netlink message, the data is the entire netlink message,
	 * this is used to communicate frame TX/RX in the familiar
	 * netlink format, to avoid having a special format
	 */
	WMEDIUMD_MSG_NETLINK,

	/* control message, see struct wmediumd_message_control */
	WMEDIUMD_MSG_SET_CONTROL,

	/*
	 * Indicates TX start if WMEDIUMD_RX_CTL_NOTIFY_TX_START is set,
	 * with struct wmediumd_tx_start as the payload.
	 */
	WMEDIUMD_MSG_TX_START,

	WMEDIUMD_MSG_GET_STATIONS,

	/*
	 * Set SNR between two nodes.
	 */
	WMEDIUMD_MSG_SET_SNR,

	/*
	 * Clear and reload configuration at specified path
	 */
	WMEDIUMD_MSG_RELOAD_CONFIG,

	/*
	 * Clear and reload configuration loaded before
	 */
	WMEDIUMD_MSG_RELOAD_CURRENT_CONFIG,

	/*
	 * Start packet capture. If a previous capture exists, the capture will
	 * be closed and a new capture will be started. Captured packets are
	 * saved at the specified path of wmediumd_start_pcap. The saved file
	 * has pcap capture file format.
	 */
	WMEDIUMD_MSG_START_PCAP,

	/*
	 * Stop packet capture
	 */
	WMEDIUMD_MSG_STOP_PCAP,

	WMEDIUMD_MSG_STATIONS_LIST,

	/*
	 * Set position of station.
	 */
	WMEDIUMD_MSG_SET_POSITION,

	/*
	 * Set LCI of station
	 */
	WMEDIUMD_MSG_SET_LCI,

	/*
	 * Set CIVIC loc of station
	 */
	WMEDIUMD_MSG_SET_CIVICLOC,
};

struct wmediumd_message_header {
	/* type of message - see enum wmediumd_message */
	uint32_t type;
	/* data length */
	uint32_t data_len;

	/* variable-length data according to the message type */
	uint8_t data[];
};

enum wmediumd_control_flags {
	WMEDIUMD_CTL_NOTIFY_TX_START		= 1 << 0,
	WMEDIUMD_CTL_RX_ALL_FRAMES		= 1 << 1,
};

struct wmediumd_message_control {
	uint32_t flags;

	/*
	 * For compatibility, wmediumd is meant to understand shorter
	 * (and ignore unknown parts of longer) control messages than
	 * what's sent to it, so always take care to have defaults as
	 * zero since that's what it assumes.
	 */
};

struct wmediumd_tx_start {
	/*
	 * The cookie is set only when telling the sender, otherwise
	 * it's set to 0.
	 */
	uint64_t cookie;
	uint32_t freq;
	uint32_t reserved[3];
};

#pragma pack(push, 1)
struct wmediumd_set_snr {
	/* MAC address of node 1 */
	uint8_t node1_mac[6];
	/* MAC address of node 2 */
	uint8_t node2_mac[6];
	/* New SNR between two nodes */
	uint8_t snr;
};
#pragma pack(pop)

struct wmediumd_load_config {
	/* path of wmediumd configuration file */
	char config_path[0];
};

struct wmediumd_start_pcap {
	char pcap_path[0];
};

#pragma pack(push, 1)
struct wmediumd_station_info {
	char addr[ETH_ALEN];
	char hwaddr[ETH_ALEN];

	double x;
	double y;

	/*
	 * Offsets to the null-terminating string data.
	 * They point outside of the struct wmediumd_station_info,
	 * and even struct wmediumd_station_infos for multiple stations.
	 */
	int lci_offset;
	int civicloc_offset;

	int tx_power;
};

struct wmediumd_station_infos {
	uint32_t count;
	struct wmediumd_station_info stations[0];
};

struct wmediumd_set_position {
	/* MAC address */
	uint8_t mac[6];
	/* X position of station */
	double x;
	/* Y position of station */
	double y;
};

struct wmediumd_set_tx_power {
	/* MAC address */
	uint8_t mac[6];
	/* TX power of station */
	int tx_power;
};

struct wmediumd_set_lci {
	/* MAC address */
	uint8_t mac[6];

	/* LCI */
	char lci[0];
};

struct wmediumd_set_civicloc {
	/* MAC address */
	uint8_t mac[6];

	/* CIVIC location */
	char civicloc[0];
};

#pragma pack(pop)

#endif /* _WMEDIUMD_API_H */
