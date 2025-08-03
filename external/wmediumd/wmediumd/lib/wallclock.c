/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#include <stddef.h>
#include <stdint.h>
#include <time.h>
#include <sys/timerfd.h>
#include <usfstl/sched.h>
#include <usfstl/loop.h>

void usfstl_sched_wallclock_handle_fd(struct usfstl_loop_entry *entry)
{
	struct usfstl_scheduler *sched;
	uint64_t v;

	sched = container_of(entry, struct usfstl_scheduler, wallclock.entry);

	USFSTL_ASSERT_EQ((int)read(entry->fd, &v, sizeof(v)), (int)sizeof(v), "%d");
	sched->wallclock.timer_triggered = 1;
}

static void usfstl_sched_wallclock_initialize(struct usfstl_scheduler *sched)
{
	struct timespec now = {};

	USFSTL_ASSERT_EQ(clock_gettime(CLOCK_MONOTONIC, &now), 0, "%d");

	sched->wallclock.start = (uint64_t)now.tv_sec * 1000000000 + now.tv_nsec;
	sched->wallclock.initialized = 1;
}

void usfstl_sched_wallclock_request(struct usfstl_scheduler *sched, uint64_t time)
{
	struct itimerspec itimer = {};
	unsigned int nsec_per_tick = sched->wallclock.nsec_per_tick;
	uint64_t waketime;

	USFSTL_ASSERT(nsec_per_tick != 0);

	if (!sched->wallclock.initialized)
		usfstl_sched_wallclock_initialize(sched);

	waketime = sched->wallclock.start + nsec_per_tick * time;

	itimer.it_value.tv_sec = waketime / 1000000000;
	itimer.it_value.tv_nsec = waketime % 1000000000;

	USFSTL_ASSERT_EQ(timerfd_settime(sched->wallclock.entry.fd,
				       TFD_TIMER_ABSTIME, &itimer, NULL),
		       0, "%d");
}

void usfstl_sched_wallclock_wait(struct usfstl_scheduler *sched)
{
	sched->wallclock.timer_triggered = 0;

	usfstl_loop_register(&sched->wallclock.entry);

	while (!sched->wallclock.timer_triggered)
		usfstl_loop_wait_and_handle();

	usfstl_loop_unregister(&sched->wallclock.entry);

	usfstl_sched_set_time(sched, sched->prev_external_sync);
}

void usfstl_sched_wallclock_init(struct usfstl_scheduler *sched,
				 unsigned int ns_per_tick)
{
	USFSTL_ASSERT(!sched->external_request && !sched->external_wait);

	sched->external_request = usfstl_sched_wallclock_request;
	sched->external_wait = usfstl_sched_wallclock_wait;

	sched->wallclock.entry.fd = timerfd_create(CLOCK_MONOTONIC, 0);
	USFSTL_ASSERT(sched->wallclock.entry.fd >= 0);

	sched->wallclock.entry.handler = usfstl_sched_wallclock_handle_fd;

	sched->wallclock.nsec_per_tick = ns_per_tick;
}

void usfstl_sched_wallclock_exit(struct usfstl_scheduler *sched)
{
	USFSTL_ASSERT(sched->external_request == usfstl_sched_wallclock_request &&
		    sched->external_wait == usfstl_sched_wallclock_wait);

	sched->external_request = NULL;
	sched->external_wait = NULL;
	close(sched->wallclock.entry.fd);
}

static void _usfstl_sched_wallclock_sync_real(struct usfstl_scheduler *sched)
{
	struct timespec now = {};
	uint64_t nowns;

	USFSTL_ASSERT_EQ(clock_gettime(CLOCK_MONOTONIC, &now), 0, "%d");

	nowns = (uint64_t)now.tv_sec * 1000000000 + now.tv_nsec;
	nowns -= sched->wallclock.start;
	usfstl_sched_set_time(sched, nowns / sched->wallclock.nsec_per_tick);
}

static void usfstl_sched_wallclock_sync_real(void *data)
{
	_usfstl_sched_wallclock_sync_real(data);
}

void usfstl_sched_wallclock_wait_and_handle(struct usfstl_scheduler *sched)
{
	void (*old_fn)(void *);
	void *old_data;

	if (usfstl_sched_next_pending(sched, NULL))
		return;

	if (!sched->wallclock.initialized) {
		/*
		 * At least once at the beginning we should schedule
		 * and initialize from an initial request, not here.
		 */
		usfstl_loop_wait_and_handle();
		return;
	}

	old_fn = g_usfstl_loop_pre_handler_fn;
	old_data = g_usfstl_loop_pre_handler_fn_data;
	g_usfstl_loop_pre_handler_fn = usfstl_sched_wallclock_sync_real;
	g_usfstl_loop_pre_handler_fn_data = sched;

	usfstl_loop_wait_and_handle();

	g_usfstl_loop_pre_handler_fn = old_fn;
	g_usfstl_loop_pre_handler_fn_data = old_data;
}
