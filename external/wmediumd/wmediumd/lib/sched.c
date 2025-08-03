/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#include <stddef.h>
#include <stdint.h>
#include <usfstl/assert.h>
#include <usfstl/sched.h>
#include <usfstl/list.h>
#include "internal.h"

uint64_t usfstl_sched_current_time(struct usfstl_scheduler *sched)
{
	uint64_t current_time;

	if (!sched->external_sync_from || !sched->waiting)
		return sched->current_time;

	current_time = sched->external_sync_from(sched);

	/* update current time after sync */
	usfstl_sched_set_time(sched, current_time);

	return current_time;
}

static bool usfstl_sched_external_request(struct usfstl_scheduler *sched,
					  uint64_t time)
{
	if (!sched->external_request)
		return false;

	/*
	 * If we received a next_external_sync point, we don't need to ask for
	 * runtime for anything earlier than that point, we're allowed to run.
	 * However, note that this only applies if we're not currently waiting,
	 * if we are in fact waiting for permission, then of course we need to
	 * ask for any earlier time, regardless of the next_external_sync, as
	 * we won't schedule until we get called to run, and that usually won't
	 * happen if we don't ask for it.
	 */
	if (!sched->waiting && sched->next_external_sync_set &&
	    usfstl_time_cmp(time, <, sched->next_external_sync))
		return false;

	/* If we asked for this time slot already, don't ask again but wait. */
	if (sched->prev_external_sync_set && time == sched->prev_external_sync)
		return true;

	sched->prev_external_sync = time;
	sched->prev_external_sync_set = 1;
	sched->external_request(sched, time);

	return true;
}

static void usfstl_sched_external_wait(struct usfstl_scheduler *sched)
{
	/*
	 * Once we wait for the external scheduler, we have to ask again
	 * even if for some reason we end up asking for the same time.
	 */
	sched->prev_external_sync_set = 0;
	sched->waiting = 1;
	sched->external_wait(sched);
	sched->waiting = 0;
}

void usfstl_sched_add_job(struct usfstl_scheduler *sched, struct usfstl_job *job)
{
	struct usfstl_job *tmp;

	USFSTL_ASSERT_TIME_CMP(job->start, >=, sched->current_time);
	USFSTL_ASSERT(!usfstl_job_scheduled(job),
		      "cannot add a job that's already scheduled");
	USFSTL_ASSERT_CMP(job->group, <, 32, "%u");

	if ((1 << job->group) & sched->blocked_groups &&
	    job != sched->allowed_job) {
		job->start = 0;
		usfstl_list_append(&sched->pending_jobs, &job->entry);
		return;
	}

	usfstl_for_each_list_item(tmp, &sched->joblist, entry) {
		if (usfstl_time_cmp(tmp->start, >, job->start))
			break;
		if (tmp->start == job->start &&
		    tmp->priority < job->priority)
			break;
	}

	/* insert after previous entry */
	if (!tmp)
		usfstl_list_append(&sched->joblist, &job->entry);
	else
		usfstl_list_insert_before(&tmp->entry, &job->entry);

	/*
	 * Request the new job's runtime from the external scheduler
	 * (if configured); if this job doesn't request any earlier
	 * runtime than previously requested, this does nothing. It
	 * may, however, request earlier runtime if this is due to
	 * an interrupt we got from outside while waiting for the
	 * external scheduler.
	 */
	usfstl_sched_external_request(sched, job->start);

	if (sched->next_time_changed)
		sched->next_time_changed(sched);
}

bool usfstl_job_scheduled(struct usfstl_job *job)
{
	return job->entry.next != NULL;
}

void usfstl_sched_del_job(struct usfstl_job *job)
{
	if (!usfstl_job_scheduled(job))
		return;

	usfstl_list_item_remove(&job->entry);
}

void _usfstl_sched_set_time(struct usfstl_scheduler *sched, uint64_t time)
{
	uint64_t delta;

	if (sched->current_time == time)
		return;

	// check that we at least don't move backwards
	USFSTL_ASSERT_TIME_CMP(time, >=, sched->current_time);

	delta = time - sched->current_time;
	sched->current_time = time;

	if (sched->time_advanced)
		sched->time_advanced(sched, delta);
}

void usfstl_sched_set_time(struct usfstl_scheduler *sched, uint64_t time)
{
	/*
	 * also check that we're not getting set to something later than what
	 * we requested, that'd be a bug since we want to run something at an
	 * earlier time than what we just got set to; unless we have nothing
	 * to do and thus don't care at all.
	 */
	USFSTL_ASSERT(usfstl_list_empty(&sched->joblist) ||
		      usfstl_time_cmp(time, <=, sched->prev_external_sync),
		      "scheduler time moves further (to %" PRIu64 ") than requested (%" PRIu64 ")",
		      time, sched->prev_external_sync);

	_usfstl_sched_set_time(sched, time);
}

static void usfstl_sched_forward(struct usfstl_scheduler *sched, uint64_t until)
{
	USFSTL_ASSERT_TIME_CMP(until, >=, sched->current_time);

	if (usfstl_sched_external_request(sched, until)) {
		usfstl_sched_external_wait(sched);
		/*
		 * The external_wait() method must call
		 * usfstl_sched_set_time() before returning,
		 * so we don't in this case.
		 */
		return;
	}

	_usfstl_sched_set_time(sched, until);
}

void usfstl_sched_start(struct usfstl_scheduler *sched)
{
	if (usfstl_sched_external_request(sched, sched->current_time))
		usfstl_sched_external_wait(sched);
}

struct usfstl_job *usfstl_sched_next(struct usfstl_scheduler *sched)
{
	while (true) {
		struct usfstl_job *job = usfstl_sched_next_pending(sched, NULL);

		if (!job) {
			/*
			 * If external scheduler is active, we might get here
			 * with nothing to do, so we just need to wait for an
			 * external input/job which will add a job to our
			 * scheduler.
			 *
			 * Due to the fact that we don't have any API for
			 * cancelling external time requests, we might have
			 * requested time from the external scheduler for a
			 * job that subsequently got removed, ending up here
			 * without a job, or one further in the future which
			 * would cause usfstl_sched_forward() to wait again.
			 *
			 * Additionally, we might only remove the job we just
			 * found during the usfstl_sched_forward() below, if
			 * that causes the main loop to run and we detect an
			 * event that causes a job removal (such as a client
			 * disconnecting from a server), so the job pointer we
			 * have might go stale. Hence, all of this needs to be
			 * checked in the overall loop.
			 */
			if (sched->external_request) {
				usfstl_sched_external_wait(sched);
				continue;
			}
			break;
		}

		/*
		 * Forward, but only if job isn't in the past - this
		 * can happen if some job was inserted while we
		 * were in fact waiting for the external scheduler, i.e.
		 * some sort of external job happened while we thought
		 * there was nothing to do.
		 */
		if (usfstl_time_cmp(job->start, >, sched->current_time))
			usfstl_sched_forward(sched, job->start);

		/*
		 * Some sort of external job might have come to us (while
		 * we were stuck waiting for the external scheduler), and
		 * might have inserted an earlier job into the timeline.
		 * If it's not this job's turn yet, reinsert it and check
		 * what's up next in the next loop iteration.
		 *
		 * Also, 'job' might now have been removed, see above.
		 */
		if (usfstl_sched_next_pending(sched, NULL) != job)
			continue;

		/*
		 * Otherwise we've actually reached this job, so remove
		 * and call it.
		 */
		usfstl_sched_del_job(job);
		job->callback(job);
		return job;
	}

	/*
	 * We must not get here, if there's no job whatsoever the
	 * simulation has basically ended in an undefined state, even
	 * the main thread can no longer make progress.
	 */
	USFSTL_ASSERT(0, "scheduling while there's nothing to do");
}

void usfstl_sched_set_sync_time(struct usfstl_scheduler *sched, uint64_t time)
{
	USFSTL_ASSERT_TIME_CMP(time, >=, sched->current_time);
	sched->next_external_sync = time;
	sched->next_external_sync_set = 1;
}

static void usfstl_sched_block_job(struct usfstl_scheduler *sched,
				 struct usfstl_job *job)
{
	usfstl_sched_del_job(job);
	usfstl_list_append(&sched->pending_jobs, &job->entry);
}

struct usfstl_job *usfstl_sched_next_pending(struct usfstl_scheduler *sched,
					     struct usfstl_job *job)
{
	return job ? usfstl_next_item(&sched->joblist, job, struct usfstl_job, entry) :
		     usfstl_list_first_item(&sched->joblist, struct usfstl_job, entry);
}

static void usfstl_sched_remove_blocked_jobs(struct usfstl_scheduler *sched)
{
	struct usfstl_job *job = NULL, *next;

	usfstl_for_each_list_item_continue_safe(job, next, &sched->joblist,
					      entry) {
		if (job == sched->allowed_job)
			continue;
		if ((1 << job->group) & sched->blocked_groups)
			usfstl_sched_block_job(sched, job);
	}
}

static void usfstl_sched_restore_job(struct usfstl_scheduler *sched,
				     struct usfstl_job *job)
{
	usfstl_sched_del_job(job);
	if (usfstl_time_cmp(job->start, <, sched->current_time))
		job->start = sched->current_time;
	usfstl_sched_add_job(sched, job);
}

static void usfstl_sched_restore_blocked_jobs(struct usfstl_scheduler *sched)
{
	struct usfstl_job *job = NULL, *next;

	usfstl_for_each_list_item_continue_safe(job, next, &sched->pending_jobs,
					      entry) {
		if (job == sched->allowed_job ||
		    !((1 << job->group) & sched->blocked_groups))
			usfstl_sched_restore_job(sched, job);
	}
}

void usfstl_sched_block_groups(struct usfstl_scheduler *sched, uint32_t groups,
			       struct usfstl_job *job,
			       struct usfstl_sched_block_data *save)
{
	save->groups = sched->blocked_groups;
	save->job = sched->allowed_job;

	// it makes no sense to allow a job unless its group is blocked
	USFSTL_ASSERT(!job || (1 << job->group) & groups,
		    "allowed job group %d must be part of blocked groups (0x%x\n)",
		    job->group, groups);

	sched->blocked_groups |= groups;
	sched->allowed_job = job;

	usfstl_sched_remove_blocked_jobs(sched);
}

void usfstl_sched_restore_groups(struct usfstl_scheduler *sched,
				 struct usfstl_sched_block_data *restore)
{
	sched->blocked_groups = restore->groups;
	sched->allowed_job = restore->job;

	usfstl_sched_restore_blocked_jobs(sched);
	usfstl_sched_remove_blocked_jobs(sched);
}

static void usfstl_sched_link_job_callback(struct usfstl_job *job)
{
	struct usfstl_scheduler *sched = job->data;

	sched->link.waiting = false;
}

static uint64_t usfstl_sched_link_external_sync_from(struct usfstl_scheduler *sched)
{
	uint64_t parent_time;

	parent_time = usfstl_sched_current_time(sched->link.parent);

	return DIV_ROUND_UP(parent_time - sched->link.offset,
			    sched->link.tick_ratio);
}

static void usfstl_sched_link_external_wait(struct usfstl_scheduler *sched)
{
	sched->link.waiting = true;

	while (sched->link.waiting)
		usfstl_sched_next(sched->link.parent);

	usfstl_sched_set_time(sched, usfstl_sched_current_time(sched));
}

static void usfstl_sched_link_external_request(struct usfstl_scheduler *sched,
					       uint64_t time)
{
	uint64_t parent_time;
	struct usfstl_job *job = &sched->link.job;

	parent_time = sched->link.tick_ratio * time + sched->link.offset;

	usfstl_sched_del_job(job);
	job->start = parent_time;
	usfstl_sched_add_job(sched->link.parent, job);
}

void usfstl_sched_link(struct usfstl_scheduler *sched,
		       struct usfstl_scheduler *parent,
		       uint32_t tick_ratio)
{
	struct usfstl_job *job;

	USFSTL_ASSERT(tick_ratio, "a ratio must be set");
	USFSTL_ASSERT(!sched->link.parent, "must not be linked");

	USFSTL_ASSERT_EQ(sched->external_request, NULL, "%p");
	sched->external_request = usfstl_sched_link_external_request;

	USFSTL_ASSERT_EQ(sched->external_wait, NULL, "%p");
	sched->external_wait = usfstl_sched_link_external_wait;

	USFSTL_ASSERT_EQ(sched->external_sync_from, NULL, "%p");
	sched->external_sync_from = usfstl_sched_link_external_sync_from;

	sched->link.tick_ratio = tick_ratio;
	sched->link.parent = parent;

	sched->link.job.callback = usfstl_sched_link_job_callback;
	sched->link.job.data = sched;

	/* current_time = (parent_time - offset) / tick_ratio */
	sched->link.offset = sched->link.parent->current_time -
		sched->current_time * sched->link.tick_ratio;

	/* if we have a job already, request to run it */
	job = usfstl_sched_next_pending(sched, NULL);
	if (job)
		usfstl_sched_external_request(sched, job->start);
}

void usfstl_sched_unlink(struct usfstl_scheduler *sched)
{
	USFSTL_ASSERT(sched->link.parent, "must be linked");

	sched->external_sync_from = NULL;
	sched->external_wait = NULL;
	sched->external_request = NULL;

	usfstl_sched_del_job(&sched->link.job);
	memset(&sched->link, 0, sizeof(sched->link));
}
