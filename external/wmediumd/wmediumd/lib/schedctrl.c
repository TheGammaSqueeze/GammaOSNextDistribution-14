/*
 * Copyright (C) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#include <usfstl/uds.h>
#include <usfstl/schedctrl.h>
#include <linux/um_timetravel.h>
#include "internal.h"
#include <stdio.h>
#include <stdlib.h>

static void _usfstl_sched_ctrl_send_msg(struct usfstl_sched_ctrl *ctrl,
					enum um_timetravel_ops op,
					uint64_t time, uint32_t seq)
{
	struct um_timetravel_msg msg = {
		.op = op,
		.seq = seq,
		.time = time,
	};

	USFSTL_ASSERT_EQ((int)write(ctrl->fd, &msg, sizeof(msg)),
			 (int)sizeof(msg), "%d");
}

static void usfstl_sched_ctrl_sock_read(int fd, void *data)
{
	struct usfstl_sched_ctrl *ctrl = data;
	struct um_timetravel_msg msg;
	int sz = read(fd, &msg, sizeof(msg));
	uint64_t time;

	USFSTL_ASSERT_EQ(sz, (int)sizeof(msg), "%d");

	switch (msg.op) {
	case UM_TIMETRAVEL_ACK:
		if (msg.seq == ctrl->expected_ack_seq) {
			ctrl->acked = 1;
			ctrl->ack_time = msg.time;
		}
		return;
	case UM_TIMETRAVEL_RUN:
		time = DIV_ROUND_UP(msg.time - ctrl->offset,
				    ctrl->nsec_per_tick);
		usfstl_sched_set_time(ctrl->sched, time);
		ctrl->waiting = 0;
		break;
	case UM_TIMETRAVEL_FREE_UNTIL:
		/* round down here, so we don't overshoot */
		time = (msg.time - ctrl->offset) / ctrl->nsec_per_tick;
		usfstl_sched_set_sync_time(ctrl->sched, time);
		break;
	case UM_TIMETRAVEL_START:
	case UM_TIMETRAVEL_REQUEST:
	case UM_TIMETRAVEL_WAIT:
	case UM_TIMETRAVEL_GET:
	case UM_TIMETRAVEL_UPDATE:
	case UM_TIMETRAVEL_GET_TOD:
		USFSTL_ASSERT(0);
		return;
	}

	_usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_ACK, 0, msg.seq);
}

static void usfstl_sched_ctrl_send_msg(struct usfstl_sched_ctrl *ctrl,
				       enum um_timetravel_ops op,
				       uint64_t time)
{
	static uint32_t seq, old_expected;

	do {
		seq++;
	} while (seq == 0);

	_usfstl_sched_ctrl_send_msg(ctrl, op, time, seq);
	old_expected = ctrl->expected_ack_seq;
	ctrl->expected_ack_seq = seq;

	USFSTL_ASSERT_EQ((int)ctrl->acked, 0, "%d");

	/*
	 * Race alert!
	 *
	 * UM_TIMETRAVEL_WAIT basically passes the run "token" to the
	 * controller, which passes it to another participant of the
	 * simulation. This other participant might immediately send
	 * us another message on a different channel, e.g. if this
	 * code is used in a vhost-user device.
	 *
	 * If here we were to use use usfstl_loop_wait_and_handle(),
	 * we could actually get and process the vhost-user message
	 * before the ACK for the WAIT message here, depending on the
	 * (host) kernel's message ordering and select() handling etc.
	 *
	 * To avoid this, directly read the ACK message for the WAIT,
	 * without handling any other sockets (first).
	 */
	if (op == UM_TIMETRAVEL_WAIT) {
		usfstl_sched_ctrl_sock_read(ctrl->fd, ctrl);
		USFSTL_ASSERT(ctrl->acked);
	}

	while (!ctrl->acked)
		usfstl_loop_wait_and_handle();
	ctrl->acked = 0;
	ctrl->expected_ack_seq = old_expected;

	if (op == UM_TIMETRAVEL_GET) {
		if (ctrl->frozen) {
			uint64_t local;

			local = ctrl->sched->current_time * ctrl->nsec_per_tick;
			ctrl->offset = ctrl->ack_time - local;
		} else {
			uint64_t time;

			time = DIV_ROUND_UP(ctrl->ack_time - ctrl->offset,
					    ctrl->nsec_per_tick);
			usfstl_sched_set_time(ctrl->sched, time);
		}
	}
}

static void usfstl_sched_ctrl_request(struct usfstl_scheduler *sched, uint64_t time)
{
	struct usfstl_sched_ctrl *ctrl = sched->ext.ctrl;

	if (!ctrl->started)
		return;

	usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_REQUEST,
				   time * ctrl->nsec_per_tick + ctrl->offset);
}

static void usfstl_sched_ctrl_wait(struct usfstl_scheduler *sched)
{
	struct usfstl_sched_ctrl *ctrl = sched->ext.ctrl;

	ctrl->waiting = 1;
	usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_WAIT, -1);

	while (ctrl->waiting)
		usfstl_loop_wait_and_handle();
}

#define JOB_ASSERT_VAL(j) (j) ? (j)->name : "<NULL>"

void usfstl_sched_ctrl_start(struct usfstl_sched_ctrl *ctrl,
			     const char *socket,
			     uint32_t nsec_per_tick,
			     uint64_t client_id,
			     struct usfstl_scheduler *sched)
{
	struct usfstl_job *job;

	USFSTL_ASSERT_EQ(ctrl->sched, NULL, "%p");
	USFSTL_ASSERT_EQ(sched->ext.ctrl, NULL, "%p");

	memset(ctrl, 0, sizeof(*ctrl));

	/*
	 * The remote side assumes we start at 0, so if we don't have 0 right
	 * now keep the difference in our own offset (in nsec).
	 */
	ctrl->offset = -sched->current_time * nsec_per_tick;

	ctrl->nsec_per_tick = nsec_per_tick;
	ctrl->sched = sched;
	sched->ext.ctrl = ctrl;

	USFSTL_ASSERT_EQ(usfstl_sched_next_pending(sched, NULL),
			 (struct usfstl_job *)NULL, "%s", JOB_ASSERT_VAL);
	USFSTL_ASSERT_EQ(sched->external_request, NULL, "%p");
	USFSTL_ASSERT_EQ(sched->external_wait, NULL, "%p");

	sched->external_request = usfstl_sched_ctrl_request;
	sched->external_wait = usfstl_sched_ctrl_wait;

	ctrl->fd = usfstl_uds_connect(socket, usfstl_sched_ctrl_sock_read,
				      ctrl);

	/* tell the other side we're starting  */
	usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_START, client_id);
	ctrl->started = 1;

	/* if we have a job already, request it */
	job = usfstl_sched_next_pending(sched, NULL);
	if (job)
		usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_REQUEST,
					   job->start * nsec_per_tick);

	/*
	 * At this point, we're allowed to do further setup work and can
	 * request schedule time etc. but must eventually start scheduling
	 * the linked scheduler - the remote side is blocked until we do.
	 */
}

void usfstl_sched_ctrl_sync_to(struct usfstl_sched_ctrl *ctrl)
{
	uint64_t time;

	USFSTL_ASSERT(ctrl->started, "cannot sync to scheduler until started");

	time = usfstl_sched_current_time(ctrl->sched) * ctrl->nsec_per_tick;
	time += ctrl->offset;

	usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_UPDATE, time);
}

void usfstl_sched_ctrl_sync_from(struct usfstl_sched_ctrl *ctrl)
{
	if (!ctrl->started)
		return;
	usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_GET, -1);
}

void usfstl_sched_ctrl_stop(struct usfstl_sched_ctrl *ctrl)
{
	USFSTL_ASSERT_EQ(ctrl, ctrl->sched->ext.ctrl, "%p");
	usfstl_sched_ctrl_send_msg(ctrl, UM_TIMETRAVEL_WAIT, -1);
	usfstl_uds_disconnect(ctrl->fd);
	ctrl->sched->ext.ctrl = NULL;
	ctrl->sched->external_request = NULL;
	ctrl->sched->external_wait = NULL;
	ctrl->sched = NULL;
}

void usfstl_sched_ctrl_set_frozen(struct usfstl_sched_ctrl *ctrl, bool frozen)
{
	ctrl->frozen = frozen;
}
