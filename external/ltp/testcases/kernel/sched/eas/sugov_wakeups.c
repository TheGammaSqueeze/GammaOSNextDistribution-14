/*
 * Copyright (c) 2018 Google, Inc.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This test looks for a high number of wakeups from the schedutil governor
 * threads.
 */

#define _GNU_SOURCE
#include <errno.h>
#include <pthread.h>
#include <sched.h>
#include <stdio.h>
#include <time.h>

#include "tst_test.h"
#include "tst_safe_file_ops.h"
#include "tst_safe_pthread.h"

#include "trace_parse.h"

#define TRACE_EVENTS "sched_switch"

#define MAX_WAKEUPS 100

#define SLEEP_SEC 10
static void run(void)
{
	int i;
	int num_sugov_wakeups = 0;

	tst_res(TINFO, "Observing sugov wakeups over %d sec, "
		"%d wakeups allowed\n", SLEEP_SEC, MAX_WAKEUPS);

	/* configure and enable tracing */
	tracefs_write("tracing_on", "0");
	tracefs_write("buffer_size_kb", "16384");
	tracefs_write("set_event", TRACE_EVENTS);
	tracefs_write("trace", "\n");
	tracefs_write("tracing_on", "1");

	sleep(SLEEP_SEC);

	/* disable tracing */
	tracefs_write("tracing_on", "0");
	LOAD_TRACE();

	for (i = 0; i < num_trace_records; i++) {
		if (trace[i].event_type == TRACE_RECORD_SCHED_SWITCH) {
			struct trace_sched_switch *t = trace[i].event_data;
			if (!strncmp("sugov:", t->next_comm, 6))
				num_sugov_wakeups++;
		}
	}
	printf("%d sugov wakeups occurred.\n", num_sugov_wakeups);
	if (num_sugov_wakeups > MAX_WAKEUPS)
		tst_res(TFAIL, "Too many wakeups from the schedutil "
			"governor.\n");
	else
		tst_res(TPASS, "Wakeups from schedutil governor were below "
			"threshold.\n");
}

static struct tst_test test = {
	.test_all = run,
	.setup = trace_setup,
	.cleanup = trace_cleanup,
};
