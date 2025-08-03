/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _USFSTL_SCHED_H_
#define _USFSTL_SCHED_H_
#include <stdint.h>
#include <inttypes.h>
#include <stdbool.h>
#include "assert.h"
#include "list.h"
#include "loop.h"

/*
 * usfstl's simple scheduler
 *
 * usfstl's concept of time is just a free-running counter. You can use
 * any units you like, but we recommend micro or nanoseconds, even
 * with nanoseconds you can simulate ~580 years in a uint64_t time.
 *
 * The scheduler is just a really basic concept, you enter "jobs"
 * and then call usfstl_sched_next() to run the next job. Usually,
 * this job would schedule another job and call usfstl_sched_next()
 * again, etc.
 *
 * The scheduler supports grouping jobs (currently into up to 32
 * groups) and then blocking certain groups from being executed in
 * the next iteration. This can be used, for example, to separate
 * in-SIM and out-of-SIM jobs, or threads from IRQs, etc. Note
 * that groups and priorities are two entirely separate concepts.
 *
 * In many cases, you'll probably be looking for usfstltask.h instead
 * as that allows you to simulate a cooperative multithreading system.
 * However, raw jobs may in that case be useful for things like e.g.
 * interrupts that come into the simulated system (if they're always
 * run-to-completion i.e. cannot yield to other jobs.)
 *
 * Additionally, the scheduler has APIs to integrate with another,
 * external, scheduler to synchronize multiple components.
 */

/**
 * struct usfstl_job - usfstl scheduler job
 * @time: time this job fires
 * @priority: priority of the job, in case of multiple happening
 *	at the same time; higher value means higher priority
 * @group: group value, in range 0-31
 * @name: job name
 * @data: job data
 * @callback: called when the job occurs
 */
struct usfstl_job {
	uint64_t start;
	uint32_t priority;
	uint8_t group;
	const char *name;

	void *data;
	void (*callback)(struct usfstl_job *job);

	/* private: */
	struct usfstl_list_entry entry;
};

/**
 * struct usfstl_scheduler - usfstl scheduler structure
 * @external_request: If external scheduler integration is required,
 *	set this function pointer appropriately to request the next
 *	run time from the external scheduler.
 * @external_wait: For external scheduler integration, this must wait
 *	for the previously requested runtime being granted, and you
 *	must call usfstl_sched_set_time() before returning from this
 *	function.
 * @external_sync_from: For external scheduler integration, return current
 *	time based on external time info.
 * @time_advanced: Set this to have logging (or similar) when time
 *	advances. Note that the argument is relative to the previous
 *	time, if you need the current absolute time use
 *	usfstl_sched_current_time(), subtract @delta from that to
 *	obtain the time prior to the current advance.
 *
 * Use USFSTL_SCHEDULER() to declare (and initialize) a scheduler.
 */
struct usfstl_scheduler {
	void (*external_request)(struct usfstl_scheduler *, uint64_t);
	void (*external_wait)(struct usfstl_scheduler *);
	uint64_t (*external_sync_from)(struct usfstl_scheduler *sched);
	void (*time_advanced)(struct usfstl_scheduler *, uint64_t delta);

/* private: */
	void (*next_time_changed)(struct usfstl_scheduler *);
	uint64_t current_time;
	uint64_t prev_external_sync, next_external_sync;

	struct usfstl_list joblist;
	struct usfstl_list pending_jobs;
	struct usfstl_job *allowed_job;

	uint32_t blocked_groups;
	uint8_t next_external_sync_set:1,
		prev_external_sync_set:1,
		waiting:1;

	struct {
		struct usfstl_loop_entry entry;
		uint64_t start;
		uint32_t nsec_per_tick;
		uint8_t timer_triggered:1,
			initialized:1;
	} wallclock;

	struct {
		struct usfstl_scheduler *parent;
		int64_t offset;
		uint32_t tick_ratio;
		struct usfstl_job job;
		bool waiting;
	} link;

	struct {
		void *ctrl;
	} ext;
};

#define USFSTL_SCHEDULER(name)						\
	struct usfstl_scheduler name = {				\
		.joblist = USFSTL_LIST_INIT(name.joblist),		\
		.pending_jobs = USFSTL_LIST_INIT(name.pending_jobs),	\
	}

#define usfstl_time_check(x) \
	({ uint64_t __t; typeof(x) __x; (void)(&__t == &__x); 1; })

/**
 * usfstl_time_cmp - compare time, wrap-around safe
 * @a: first time
 * @op: comparison operator (<, >, <=, >=)
 * @b: second time
 *
 * Returns: (a op b), e.g. usfstl_time_cmp(a, >=, b) returns (a >= b)
 *	while accounting for wrap-around
 */
#define usfstl_time_cmp(a, op, b)	\
	(usfstl_time_check(a) && usfstl_time_check(b) && \
	 (0 op (int64_t)((b) - (a))))

/**
 * USFSTL_ASSERT_TIME_CMP - assert that the time comparison holds
 * @a: first time
 * @op: comparison operator
 * @b: second time
 */
#define USFSTL_ASSERT_TIME_CMP(a, op, b) do {				\
	uint64_t _a = a;						\
	uint64_t _b = b;						\
	if (!usfstl_time_cmp(_a, op, _b))				\
		usfstl_abort(__FILE__, __LINE__,			\
			     "usfstl_time_cmp(" #a ", " #op ", " #b ")",\
			     "  " #a " = %" PRIu64 "\n"			\
			     "  " #b " = %" PRIu64 "\n",		\
			     _a, _b);					\
} while (0)

/**
 * usfstl_sched_current_time - return current time
 * @sched: the scheduler to operate with
 */
uint64_t usfstl_sched_current_time(struct usfstl_scheduler *sched);

/**
 * usfstl_sched_add_job - add job execution
 * @sched: the scheduler to operate with
 * @job: job to add
 *
 * Add an job to the execution queue, at the time noted
 * inside the job.
 */
void usfstl_sched_add_job(struct usfstl_scheduler *sched,
			  struct usfstl_job *job);

/**
 * @usfstl_sched_del_job - remove an job
 * @sched: the scheduler to operate with
 * @job: job to remove
 *
 * Remove an job from the execution queue, if present.
 */
void usfstl_sched_del_job(struct usfstl_job *job);

/**
 * usfstl_sched_start - start the scheduler
 * @sched: the scheduler to operate with
 *
 * Start the scheduler, which initializes the scheduler data
 * and syncs with the external scheduler if necessary.
 */
void usfstl_sched_start(struct usfstl_scheduler *sched);

/**
 * usfstl_sched_next - call next job
 * @sched: the scheduler to operate with
 *
 * Go into the scheduler, forward time to the next job,
 * and call its callback.
 *
 * Returns the job that was run.
 */
struct usfstl_job *usfstl_sched_next(struct usfstl_scheduler *sched);

/**
 * usfstl_job_scheduled - check if an job is scheduled
 * @job: the job to check
 *
 * Returns: %true if the job is on the schedule list, %false otherwise.
 */
bool usfstl_job_scheduled(struct usfstl_job *job);

/**
 * usfstl_sched_next_pending - get first/next pending job
 * @sched: the scheduler to operate with
 * @job: %NULL or previously returned job
 *
 * This is used to implement usfstl_sched_for_each_pending()
 * and usfstl_sched_for_each_pending_safe().
 */
struct usfstl_job *usfstl_sched_next_pending(struct usfstl_scheduler *sched,
					 struct usfstl_job *job);

#define usfstl_sched_for_each_pending(sched, job) \
	for (job = usfstl_sched_next_pending(sched, NULL); job; \
	     job = usfstl_sched_next_pending(sched, job))

#define usfstl_sched_for_each_pending_safe(sched, job, tmp) \
	for (job = usfstl_sched_next_pending(sched, NULL), \
	     tmp = usfstl_sched_next_pending(sched, job); \
	     job; \
	     job = tmp, tmp = usfstl_sched_next_pending(sched, tmp))

struct usfstl_sched_block_data {
	uint32_t groups;
	struct usfstl_job *job;
};

/**
 * usfstl_sched_block_groups - block groups from executing
 * @sched: the scheduler to operate with
 * @groups: groups to block, ORed with the currently blocked groups
 * @job: single job that's allowed anyway, e.g. if the caller is
 *	part of the blocked group and must be allowed to continue
 * @save: save data, use with usfstl_sched_restore_groups()
 */
void usfstl_sched_block_groups(struct usfstl_scheduler *sched, uint32_t groups,
			       struct usfstl_job *job,
			       struct usfstl_sched_block_data *save);

/**
 * usfstl_sched_restore_groups - restore blocked groups
 * @sched: the scheduler to operate with
 * @restore: data saved during usfstl_sched_block_groups()
 */
void usfstl_sched_restore_groups(struct usfstl_scheduler *sched,
				 struct usfstl_sched_block_data *restore);

/**
 * usfstl_sched_set_time - set time from external source
 * @sched: the scheduler to operate with
 * @time: time
 *
 * Set the scheduler time from the external source, use this
 * before returning from the sched_external_wait() method but also when
 * injecting any other kind of job like an interrupt from an
 * external source (only applicable when running with external
 * scheduling and other external interfaces.)
 */
void usfstl_sched_set_time(struct usfstl_scheduler *sched, uint64_t time);

/**
 * usfstl_sched_set_sync_time - set next external sync time
 * @sched: the scheduler to operate with
 * @time: time
 *
 * When cooperating with an external scheduler, it may be good to
 * avoid ping-pong all the time, if there's nothing to do. This
 * function facilitates that.
 *
 * Call it before returning from the sched_external_wait() method and
 * the scheduler will not sync for each internal job again, but
 * only when the next internal job would be at or later than the
 * time given as the argument here.
 *
 * Note that then you also have to coordinate with the external
 * scheduler every time there's any interaction with any other
 * component also driven by the external scheduler.
 *
 * To understand this, consider the following timeline, where the
 * letters indicate scheduler jobs:
 *  - component 1: A      C D E F
 *  - component 2:   B             G
 * Without calling this function, component 1 will always sync
 * with the external scheduler in component 2, for every job
 * it has. However, after the sync for job/time C, component
 * 2 knows that it will not have any job until G, so if it
 * tells component 1 (whatever RPC there is calls this function)
 * then component 1 will sync again only after F.
 *
 * However, this also necessitates that whenever the components
 * interact, this function could be called again. If you imagine
 * jobs C-F to just be empty ticks that do nothing then this
 * might not happen. However, if one of them causes interaction
 * with component 2 (say a network packet in the simulation) the
 * interaction may cause a new job to be inserted into the
 * scheduler timeline of component 2. Let's say, for example,
 * the job D was a transmission from component 1 to 2, and in
 * component 2 that causes an interrupt and a rescheduling. The
 * above timeline will thus change to:
 *  - component 1: A      C D E F
 *  - component 2:   B       N     G
 * inserting the job N. Thus, this very interaction needs to
 * call this function again with the time of N which will be at
 * or shortly after the time of D, rather than at G.
 *
 * This optimises things if jobs C-F don't cause interaction
 * with other components, and if considered properly in the RPC
 * protocol will not cause any degradation.
 *
 * If not supported, just never call this function and each and
 * every job will require external synchronisation.
 */
void usfstl_sched_set_sync_time(struct usfstl_scheduler *sched, uint64_t time);

/**
 * g_usfstl_top_scheduler - top level scheduler
 *
 * There can be multiple schedulers in an usfstl binary, in particular
 * when the multi-binary support code is used. In any case, this will
 * will point to the top-level scheduler to facilitate another level
 * of integration if needed.
 */
extern struct usfstl_scheduler *g_usfstl_top_scheduler;

/**
 * usfstl_sched_wallclock_init - initialize wall-clock integration
 * @sched: the scheduler to initialize, it must not have external
 *	integration set up yet
 * @ns_per_tick: nanoseconds per scheduler tick
 *
 * You can use this function to set up a scheduler to run at roughly
 * wall clock speed (per the @ns_per_tick setting).
 *
 * This is compatible with the usfstlloop abstraction, so you can also
 * add other things to the event loop and they'll be handled while
 * the scheduler is waiting for time to pass.
 *
 * NOTE: This is currently Linux-only.
 */
void usfstl_sched_wallclock_init(struct usfstl_scheduler *sched,
				 unsigned int ns_per_tick);

/**
 * usfstl_sched_wallclock_exit - remove wall-clock integration
 * @sched: scheduler to remove wall-clock integration from
 *
 * This releases any resources used for the wall-clock integration.
 */
void usfstl_sched_wallclock_exit(struct usfstl_scheduler *sched);

/**
 * usfstl_sched_wallclock_wait_and_handle - wait for external events
 * @sched: scheduler that's integrated with the wallclock
 *
 * If no scheduler events are pending, this will wait for external
 * events using usfstl_wait_and_handle() and synchronize the time it
 * took for such an event to arrive into the given scheduler.
 */
void usfstl_sched_wallclock_wait_and_handle(struct usfstl_scheduler *sched);

/**
 * usfstl_sched_link - link a scheduler to another one
 * @sched: the scheduler to link, must not already use the external
 *	request methods, of course. Should also not be running.
 * @parent: the parent scheduler to link to
 * @tick_ratio: "tick_ratio" parent ticks == 1 of our ticks;
 *	e.g. 1000 for if @sched should have microseconds, while @parent
 *	uses nanoseconds.
 *
 * This links two schedulers together, and requesting any runtime in the
 * inner scheduler (@sched) depends on the parent scheduler (@parent)
 * granting it.
 *
 * Time in the inner scheduler is adjusted in two ways:
 * 1) there's a "tick_ratio" as described above
 * 2) at the time of linking, neither scheduler changes its current
 *    time, instead an offset between the two is maintained, so the
 *    inner scheduler can be at e.g. zero and be linked to a parent
 *    that has already been running for a while.
 */
void usfstl_sched_link(struct usfstl_scheduler *sched,
		       struct usfstl_scheduler *parent,
		       uint32_t tick_ratio);

/**
 * usfstl_sched_unlink - unlink a scheduler again
 * @sched: the scheduler to unlink, must be linked
 */
void usfstl_sched_unlink(struct usfstl_scheduler *sched);

#endif // _USFSTL_SCHED_H_
