// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2021 VMware Inc, Steven Rostedt <rostedt@goodmis.org>
 *
 * Updates:
 * Copyright (C) 2021, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */
#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>

#include "tracefs.h"
#include "tracefs-local.h"

#define KPROBE_EVENTS "kprobe_events"
#define KPROBE_DEFAULT_GROUP "kprobes"

static struct tracefs_dynevent *
kprobe_alloc(enum tracefs_dynevent_type type, const char *system, const char *event,
	     const char *addr, const char *format)
{
	struct tracefs_dynevent *kp;
	const char *sys = system;
	const char *ename = event;
	char *tmp;

	if (!addr) {
		errno = EBADMSG;
		return NULL;
	}
	if (!sys)
		sys = KPROBE_DEFAULT_GROUP;

	if (!event) {
		ename = strdup(addr);
		if (!ename)
			return NULL;
		tmp = strchr(ename, ':');
		if (tmp)
			*tmp = '\0';
	}

	kp = dynevent_alloc(type, sys, ename, addr, format);
	if (!event)
		free((char *)ename);

	return kp;
}

/**
 * tracefs_kprobe_alloc - Allocate new kprobe
 * @system: The system name (NULL for the default kprobes)
 * @event: The event to create (NULL to use @addr for the event)
 * @addr: The function and offset (or address) to insert the probe
 * @format: The format string to define the probe.
 *
 * Allocate a kprobe context that will be in the @system group (or kprobes if
 * @system is NULL). Have the name of @event (or @addr if @event is NULL). Will
 * be inserted to @addr (function name, with or without offset, or a address).
 * And the @format will define the format of the kprobe.
 *
 * See the Linux documentation file under:
 *  Documentation/trace/kprobetrace.rst
 *
 * The kprobe is not created in the system.
 *
 * Return a pointer to a kprobe context on success, or NULL on error.
 * The returned pointer must be freed with tracefs_dynevent_free()
 *
 * errno will be set to EBADMSG if addr is NULL.
 */
struct tracefs_dynevent *
tracefs_kprobe_alloc(const char *system, const char *event, const char *addr, const char *format)

{
	return kprobe_alloc(TRACEFS_DYNEVENT_KPROBE, system, event, addr, format);
}

/**
 * tracefs_kretprobe_alloc - Allocate new kretprobe
 * @system: The system name (NULL for the default kprobes)
 * @event: The event to create (NULL to use @addr for the event)
 * @addr: The function and offset (or address) to insert the retprobe
 * @format: The format string to define the retprobe.
 * @max: Maximum number of instances of the specified function that
 *	 can be probed simultaneously, or 0 for the default value.
 *
 * Allocate a kretprobe that will be in the @system group (or kprobes if
 * @system is NULL). Have the name of @event (or @addr if @event is
 * NULL). Will be inserted to @addr (function name, with or without
 * offset, or a address). And the @format will define the raw format
 * of the kprobe. See the Linux documentation file under:
 * Documentation/trace/kprobetrace.rst
 * The kretprobe is not created in the system.
 *
 * Return a pointer to a kprobe context on success, or NULL on error.
 * The returned pointer must be freed with tracefs_dynevent_free()
 *
 * errno will be set to EBADMSG if addr is NULL.
 */
struct tracefs_dynevent *
tracefs_kretprobe_alloc(const char *system, const char *event,
			const char *addr, const char *format, unsigned int max)
{
	struct tracefs_dynevent *kp;
	int ret;

	kp = kprobe_alloc(TRACEFS_DYNEVENT_KRETPROBE, system, event, addr, format);
	if (!kp)
		return NULL;

	if (!max)
		return kp;

	free(kp->prefix);
	kp->prefix = NULL;
	ret = asprintf(&kp->prefix, "r%d:", max);
	if (ret < 0)
		goto error;

	return kp;
error:
	tracefs_dynevent_free(kp);
	return NULL;
}

static int kprobe_raw(enum tracefs_dynevent_type type, const char *system,
		      const char *event, const char *addr, const char *format)
{
	static struct tracefs_dynevent *kp;
	int ret;

	kp = kprobe_alloc(type, system, event, addr, format);
	if (!kp)
		return -1;

	ret = tracefs_dynevent_create(kp);
	tracefs_dynevent_free(kp);

	return ret;
}

/**
 * tracefs_kprobe_raw - Create a kprobe using raw format
 * @system: The system name (NULL for the default kprobes)
 * @event: The event to create (NULL to use @addr for the event)
 * @addr: The function and offset (or address) to insert the probe
 * @format: The raw format string to define the probe.
 *
 * Create a kprobe that will be in the @system group (or kprobes if
 * @system is NULL). Have the name of @event (or @addr if @event is
 * NULL). Will be inserted to @addr (function name, with or without
 * offset, or a address). And the @format will define the raw format
 * of the kprobe. See the Linux documentation file under:
 * Documentation/trace/kprobetrace.rst
 *
 * Return 0 on success, or -1 on error.
 *   If the syntex of @format was incorrect, running
 *   tracefs_error_last(NULL) may show what went wrong.
 *
 * errno will be set to EBADMSG if addr or format is NULL.
 */
int tracefs_kprobe_raw(const char *system, const char *event,
		       const char *addr, const char *format)
{
	return kprobe_raw(TRACEFS_DYNEVENT_KPROBE, system, event, addr, format);
}

/**
 * tracefs_kretprobe_raw - Create a kretprobe using raw format
 * @system: The system name (NULL for the default kprobes)
 * @event: The event to create (NULL to use @addr for the event)
 * @addr: The function and offset (or address) to insert the retprobe
 * @format: The raw format string to define the retprobe.
 *
 * Create a kretprobe that will be in the @system group (or kprobes if
 * @system is NULL). Have the name of @event (or @addr if @event is
 * NULL). Will be inserted to @addr (function name, with or without
 * offset, or a address). And the @format will define the raw format
 * of the kprobe. See the Linux documentation file under:
 * Documentation/trace/kprobetrace.rst
 *
 * Return 0 on success, or -1 on error.
 *   If the syntex of @format was incorrect, running
 *   tracefs_error_last(NULL) may show what went wrong.
 *
 * errno will be set to EBADMSG if addr or format is NULL.
 */
int tracefs_kretprobe_raw(const char *system, const char *event,
			  const char *addr, const char *format)
{
	return kprobe_raw(TRACEFS_DYNEVENT_KRETPROBE, system, event, addr, format);
}
