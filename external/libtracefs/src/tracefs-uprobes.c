// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2022, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */
#include <stdlib.h>
#include <errno.h>

#include "tracefs.h"
#include "tracefs-local.h"

#define UPROBE_DEFAULT_GROUP "uprobes"

static struct tracefs_dynevent *
uprobe_alloc(enum tracefs_dynevent_type type, const char *system, const char *event,
	     const char *file, unsigned long long offset, const char *fetchargs)
{
	struct tracefs_dynevent *kp;
	char *target;

	if (!event || !file) {
		errno = EINVAL;
		return NULL;
	}

	if (!system)
		system = UPROBE_DEFAULT_GROUP;

	if (asprintf(&target, "%s:0x%0*llx", file, (int)(sizeof(void *) * 2), offset) < 0)
		return NULL;

	kp = dynevent_alloc(type, system, event, target, fetchargs);
	free(target);

	return kp;
}

/**
 * tracefs_uprobe_alloc - Allocate new user probe (uprobe)
 * @system: The system name (NULL for the default uprobes)
 * @event: The name of the event to create
 * @file: The full path to the binary file, where the uprobe will be set
 * @offset: Offset within the @file
 * @fetchargs: String with arguments, that will be fetched with the uprobe
 *
 * Allocate new uprobe context that will be in the @system group
 * (or uprobes if @system is NULL) and with @event name. The new uprobe will be
 * attached to @offset within the @file. The arguments described in @fetchargs
 * will fetched with the uprobe. See linux/Documentation/trace/uprobetracer.rst
 * for more details.
 *
 * The uprobe is not created in the system.
 *
 * Return a pointer to a uprobe context on success, or NULL on error.
 * The returned pointer must be freed with tracefs_dynevent_free()
 *
 */
struct tracefs_dynevent *
tracefs_uprobe_alloc(const char *system, const char *event,
		     const char *file, unsigned long long offset, const char *fetchargs)
{
	return uprobe_alloc(TRACEFS_DYNEVENT_UPROBE, system, event, file, offset, fetchargs);
}

/**
 * tracefs_uretprobe_alloc - Allocate new user return probe (uretprobe)
 * @system: The system name (NULL for the default uprobes)
 * @event: The name of the event to create
 * @file: The full path to the binary file, where the uretprobe will be set
 * @offset: Offset within the @file
 * @fetchargs: String with arguments, that will be fetched with the uretprobe
 *
 * Allocate mew uretprobe context that will be in the @system group
 * (or uprobes if @system is NULL) and with @event name. The new uretprobe will
 * be attached to @offset within the @file. The arguments described in @fetchargs
 * will fetched with the uprobe. See linux/Documentation/trace/uprobetracer.rst
 * for more details.
 *
 * The uretprobe is not created in the system.
 *
 * Return a pointer to a uretprobe context on success, or NULL on error.
 * The returned pointer must be freed with tracefs_dynevent_free()
 *
 */
struct tracefs_dynevent *
tracefs_uretprobe_alloc(const char *system, const char *event,
			const char *file, unsigned long long offset, const char *fetchargs)
{
	return uprobe_alloc(TRACEFS_DYNEVENT_URETPROBE, system, event, file, offset, fetchargs);
}
