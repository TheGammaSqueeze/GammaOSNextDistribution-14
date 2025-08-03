/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _USFSTL_VHOST_H_
#define _USFSTL_VHOST_H_

#include "list.h"
#include "sched.h"
#include "schedctrl.h"
#include "vhostproto.h"

struct usfstl_vhost_user_buf {
	unsigned int n_in_sg, n_out_sg;
	struct iovec *in_sg, *out_sg;
	size_t written;
	unsigned int idx;
	bool allocated;
};

struct usfstl_vhost_user_dev {
	uint64_t features, protocol_features;
	struct usfstl_vhost_user_server *server;
	void *data;
};

struct usfstl_vhost_user_ops {
	void (*connected)(struct usfstl_vhost_user_dev *dev);
	void (*handle)(struct usfstl_vhost_user_dev *dev,
		       struct usfstl_vhost_user_buf *buf,
		       unsigned int vring);
	void (*disconnected)(struct usfstl_vhost_user_dev *dev);
};

/**
 * struct usfstl_vhost_user_server: vhost-user device server
 */
struct usfstl_vhost_user_server {
	/**
	 * @ops: various ops for the server/devices
	 */
	const struct usfstl_vhost_user_ops *ops;

	/**
	 * @name: socket name to use
	 */
	char *socket;

	/**
	 * @interrupt_latency: interrupt latency to model, in
	 *	scheduler ticks (actual time then depends on
	 *	the scheduler unit)
	 */
	unsigned int interrupt_latency;

	/**
	 * @max_queues: max number of virt queues supported
	 */
	unsigned int max_queues;

	/**
	 * @input_queues: bitmap of input queues (where to handle interrupts)
	 */
	uint64_t input_queues;

	/**
	 * @scheduler: the scheduler to integrate with,
	 *	may be %NULL
	 */
	struct usfstl_scheduler *scheduler;

	/**
	 * @ctrl: external scheduler control to integrate with,
	 *	may be %NULL
	 */
	struct usfstl_sched_ctrl *ctrl;

	/**
	 * @features: user features
	 */
	uint64_t features;

	/**
	 * @protocol_features: protocol features
	 */
	uint64_t protocol_features;

	/**
	 * @config: config data, if supported
	 */
	const void *config;

	/**
	 * @config_len: length of config data
	 */
	size_t config_len;

	/**
	 * @data: arbitrary user data
	 */
	void *data;
};

/**
 * usfstl_vhost_user_server_start - start the server
 */
void usfstl_vhost_user_server_start(struct usfstl_vhost_user_server *server);

/**
 * usfstl_vhost_user_server_stop - stop the server
 *
 * Note that this doesn't stop the existing devices, and it thus
 * may be used from the connected callback, if only one connection
 * is allowed.
 */
void usfstl_vhost_user_server_stop(struct usfstl_vhost_user_server *server);

/**
 * usfstl_vhost_user_dev_notify - send a message on a vring
 * @dev: device to send to
 * @vring: vring index to send on
 * @buf: buffer to send
 * @buflen: length of the buffer
 */
void usfstl_vhost_user_dev_notify(struct usfstl_vhost_user_dev *dev,
				  unsigned int vring,
				  const uint8_t *buf, size_t buflen);

/**
 * usfstl_vhost_user_config_changed - notify host of a config change event
 * @dev: device to send to
 */
void usfstl_vhost_user_config_changed(struct usfstl_vhost_user_dev *dev);

/**
 * usfstl_vhost_user_to_va - translate address
 * @dev: device to translate address for
 * @addr: guest-side virtual addr
 */
void *usfstl_vhost_user_to_va(struct usfstl_vhost_user_dev *dev, uint64_t addr);

/**
 * usfstl_vhost_user_to_va - translate address
 * @dev: device to translate address for
 * @addr: host-side virtual addr
 */
uint64_t usfstl_vhost_user_to_phys(struct usfstl_vhost_user_dev *dev, uint64_t addr);

/**
 * usfstl_vhost_phys_to_va - translate address
 * @dev: device to translate address for
 * @addr: guest-side physical addr
 */
void *usfstl_vhost_phys_to_va(struct usfstl_vhost_user_dev *dev, uint64_t addr);

/* also some IOV helpers */
size_t iov_len(struct iovec *sg, unsigned int nsg);
size_t iov_fill(struct iovec *sg, unsigned int nsg,
		const void *buf, size_t buflen);
size_t iov_read(void *buf, size_t buflen,
		struct iovec *sg, unsigned int nsg);

#endif // _USFSTL_VHOST_H_
