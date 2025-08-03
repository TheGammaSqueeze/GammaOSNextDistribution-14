/*
 * Copyright (C) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _USFSTL_SCHEDCTRL_H_
#define _USFSTL_SCHEDCTRL_H_
#include <stdint.h>
#include <inttypes.h>
#include "loop.h"
#include "sched.h"

struct usfstl_sched_ctrl {
	struct usfstl_scheduler *sched;
	uint64_t ack_time;
	int64_t offset;
	uint32_t nsec_per_tick;
	int fd;
	unsigned int waiting:1, acked:1, frozen:1, started:1;
	uint32_t expected_ack_seq;
};

void usfstl_sched_ctrl_start(struct usfstl_sched_ctrl *ctrl,
			     const char *socket,
			     uint32_t nsec_per_tick,
			     uint64_t client_id,
			     struct usfstl_scheduler *sched);
void usfstl_sched_ctrl_sync_to(struct usfstl_sched_ctrl *ctrl);
void usfstl_sched_ctrl_sync_from(struct usfstl_sched_ctrl *ctrl);
void usfstl_sched_ctrl_stop(struct usfstl_sched_ctrl *ctrl);

/**
 * usfstl_sched_ctrl_set_frozen - freeze/thaw scheduler interaction
 * @ctrl: scheduler control
 * @frozen: whether or not the scheduler interaction is frozen
 *
 * When the scheduler control connection is frozen, then any remote
 * updates will not reflect in the local scheduler, but instead will
 * just modify the offset vs. the remote.
 *
 * This allows scheduling repeatedly at "time zero" while the remote
 * side is actually running, e.g. to ensure pre-firmware boot hardware
 * simulation doesn't affect the firmware simulation time.
 */
void usfstl_sched_ctrl_set_frozen(struct usfstl_sched_ctrl *ctrl, bool frozen);

#endif // _USFSTL_SCHEDCTRL_H_
