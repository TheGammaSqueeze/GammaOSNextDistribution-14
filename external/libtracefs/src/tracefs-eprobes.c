// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2021, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */
#include <stdlib.h>
#include <errno.h>

#include "tracefs.h"
#include "tracefs-local.h"

#define EPROBE_DEFAULT_GROUP "eprobes"

/**
 * tracefs_eprobe_alloc - Allocate new eprobe
 * @system: The system name (NULL for the default eprobes)
 * @event: The name of the event to create
 * @target_system: The system of the target event
 * @target_event: The name of the target event
 * @fetchargs: String with arguments, that will be fetched from @target_event
 *
 * Allocate an eprobe context that will be in the @system group (or eprobes if
 * @system is NULL). Have the name of @event. The new eprobe will be attached to
 * given @target_event which is in the given @target_system. The arguments
 * described in @fetchargs will fetched from the @target_event.
 *
 * The eprobe is not created in the system.
 *
 * Return a pointer to a eprobe context on success, or NULL on error.
 * The returned pointer must be freed with tracefs_dynevent_free()
 *
 */
struct tracefs_dynevent *
tracefs_eprobe_alloc(const char *system, const char *event,
		     const char *target_system, const char *target_event, const char *fetchargs)
{
	struct tracefs_dynevent *kp;
	char *target;

	if (!event || !target_system || !target_event) {
		errno = EINVAL;
		return NULL;
	}

	if (!system)
		system = EPROBE_DEFAULT_GROUP;

	if (asprintf(&target, "%s.%s", target_system, target_event) < 0)
		return NULL;

	kp = dynevent_alloc(TRACEFS_DYNEVENT_EPROBE, system, event, target, fetchargs);
	free(target);

	return kp;
}

