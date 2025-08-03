/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _USFSTL_VHOST_PROTO_H_
#define _USFSTL_VHOST_PROTO_H_

#define MAX_REGIONS 8

// For simplicity, we only support snapshotting a fixed number of queues. Should
// be equal to HWSIM_NUM_VQS.
#define NUM_SNAPSHOT_QUEUES 2

/* these are from the vhost-user spec */

struct vhost_user_msg_hdr {
	uint32_t request;

#define VHOST_USER_MSG_FLAGS_VERSION	0x3
#define VHOST_USER_VERSION		  1
#define VHOST_USER_MSG_FLAGS_REPLY	0x4
#define VHOST_USER_MSG_FLAGS_NEED_REPLY	0x8
	uint32_t flags;

	uint32_t size;
};

struct vhost_user_region {
	uint64_t guest_phys_addr;
	uint64_t size;
	uint64_t user_addr;
	uint64_t mmap_offset;
};

struct vring_snapshot {
	int8_t enabled;
	int8_t sleeping;
	int8_t triggered;

	unsigned int num;
	uint64_t desc_guest_addr;
	uint64_t avail_guest_addr;
	uint64_t used_guest_addr;
	uint16_t last_avail_idx;
};

struct vhost_user_snapshot {
	struct vring_snapshot vrings[NUM_SNAPSHOT_QUEUES];
};


struct vhost_user_msg {
	struct vhost_user_msg_hdr hdr;
	union {
#define VHOST_USER_U64_VRING_IDX_MSK	0x7f
#define VHOST_USER_U64_NO_FD		0x80
		int8_t i8;
		uint64_t u64;
		struct {
			uint32_t idx, num;
		} vring_state;
		struct {
			uint32_t idx, flags;
			uint64_t descriptor;
			uint64_t used;
			uint64_t avail;
			uint64_t log;
		} vring_addr;
		struct {
			uint32_t n_regions;
			uint32_t reserved;
			struct vhost_user_region regions[MAX_REGIONS];
		} mem_regions;
		struct {
			uint32_t offset;
			uint32_t size;
#define VHOST_USER_CFG_SPACE_WRITABLE	0x1
#define VHOST_USER_CFG_SPACE_MIGRATION	0x2
			uint32_t flags;
			uint8_t payload[0];
		} cfg_space;
		struct {
			uint64_t idx_flags;
			uint64_t size;
			uint64_t offset;
		} vring_area;
		struct {
			int8_t bool_store;
			struct vhost_user_snapshot snapshot;
		}  __attribute__((packed)) snapshot_response;
		struct {
			struct vhost_user_snapshot snapshot;
		} __attribute__((packed)) restore_request;
	} __attribute__((packed)) payload;
};

#define VHOST_USER_GET_FEATURES			 1
#define VHOST_USER_SET_FEATURES			 2
#define VHOST_USER_SET_OWNER			 3
#define VHOST_USER_SET_MEM_TABLE		 5
#define VHOST_USER_SET_VRING_NUM		 8
#define VHOST_USER_SET_VRING_ADDR		 9
#define VHOST_USER_SET_VRING_BASE		10
#define VHOST_USER_SET_VRING_KICK		12
#define VHOST_USER_SET_VRING_CALL		13
#define VHOST_USER_GET_PROTOCOL_FEATURES	15
#define VHOST_USER_SET_VRING_ENABLE		18
#define VHOST_USER_SET_PROTOCOL_FEATURES	16
#define VHOST_USER_SET_SLAVE_REQ_FD		21
#define VHOST_USER_GET_CONFIG			24
#define VHOST_USER_VRING_KICK			35
#define VHOST_USER_GET_SHARED_MEMORY_REGIONS	41
#define VHOST_USER_SLEEP			42
#define VHOST_USER_WAKE				43
#define VHOST_USER_SNAPSHOT			44
#define VHOST_USER_RESTORE			45

#define VHOST_USER_SLAVE_CONFIG_CHANGE_MSG	 2
#define VHOST_USER_SLAVE_VRING_CALL		 4

#define VHOST_USER_F_PROTOCOL_FEATURES 30

#define VHOST_USER_PROTOCOL_F_MQ                    0
#define VHOST_USER_PROTOCOL_F_LOG_SHMFD             1
#define VHOST_USER_PROTOCOL_F_RARP                  2
#define VHOST_USER_PROTOCOL_F_REPLY_ACK             3
#define VHOST_USER_PROTOCOL_F_MTU                   4
#define VHOST_USER_PROTOCOL_F_SLAVE_REQ             5
#define VHOST_USER_PROTOCOL_F_CROSS_ENDIAN          6
#define VHOST_USER_PROTOCOL_F_CRYPTO_SESSION        7
#define VHOST_USER_PROTOCOL_F_PAGEFAULT             8
#define VHOST_USER_PROTOCOL_F_CONFIG                9
#define VHOST_USER_PROTOCOL_F_SLAVE_SEND_FD        10
#define VHOST_USER_PROTOCOL_F_H_OST_NOTIFIER        11
#define VHOST_USER_PROTOCOL_F_INFLIGHT_SHMFD       12
#define VHOST_USER_PROTOCOL_F_RESET_DEVICE         13
#define VHOST_USER_PROTOCOL_F_INBAND_NOTIFICATIONS 14

#endif // _USFSTL_VHOST_PROTO_H_
