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

#define DYNEVENTS_EVENTS "dynamic_events"
#define KPROBE_EVENTS "kprobe_events"
#define UPROBE_EVENTS "uprobe_events"
#define SYNTH_EVENTS "synthetic_events"
#define DYNEVENTS_DEFAULT_GROUP "dynamic"

#define EVENT_INDEX(B)	(ffs(B) - 1)

struct dyn_events_desc;
static int dyn_generic_parse(struct dyn_events_desc *,
			     const char *, char *, struct tracefs_dynevent **);
static int dyn_synth_parse(struct dyn_events_desc *,
			   const char *, char *, struct tracefs_dynevent **);
static int dyn_generic_del(struct dyn_events_desc *, struct tracefs_dynevent *);
static int dyn_synth_del(struct dyn_events_desc *, struct tracefs_dynevent *);

struct dyn_events_desc {
	enum tracefs_dynevent_type type;
	const char *file;
	const char *prefix;
	int (*del)(struct dyn_events_desc *desc, struct tracefs_dynevent *dyn);
	int (*parse)(struct dyn_events_desc *desc, const char *group,
				char *line, struct tracefs_dynevent **ret_dyn);
} dynevents[] = {
	{TRACEFS_DYNEVENT_KPROBE, KPROBE_EVENTS, "p", dyn_generic_del, dyn_generic_parse},
	{TRACEFS_DYNEVENT_KRETPROBE, KPROBE_EVENTS, "r", dyn_generic_del, dyn_generic_parse},
	{TRACEFS_DYNEVENT_UPROBE, UPROBE_EVENTS, "p", dyn_generic_del, dyn_generic_parse},
	{TRACEFS_DYNEVENT_URETPROBE, UPROBE_EVENTS, "r", dyn_generic_del, dyn_generic_parse},
	{TRACEFS_DYNEVENT_EPROBE, "", "e", dyn_generic_del, dyn_generic_parse},
	{TRACEFS_DYNEVENT_SYNTH, SYNTH_EVENTS, "", dyn_synth_del, dyn_synth_parse},
};



static int dyn_generic_del(struct dyn_events_desc *desc, struct tracefs_dynevent *dyn)
{
	char *str;
	int ret;

	if (dyn->system)
		ret = asprintf(&str, "-:%s/%s", dyn->system, dyn->event);
	else
		ret = asprintf(&str, "-:%s", dyn->event);

	if (ret < 0)
		return -1;

	ret = tracefs_instance_file_append(NULL, desc->file, str);
	free(str);

	return ret < 0 ? ret : 0;
}

/**
 * tracefs_dynevent_free - Free a dynamic event context
 * @devent: Pointer to a dynamic event context
 *
 * The dynamic event, described by this context, is not
 * removed from the system by this API. It only frees the memory.
 */
void tracefs_dynevent_free(struct tracefs_dynevent *devent)
{
	if (!devent)
		return;
	free(devent->system);
	free(devent->event);
	free(devent->address);
	free(devent->format);
	free(devent->prefix);
	free(devent->trace_file);
	free(devent);
}

static void parse_prefix(char *word, char **prefix, char **system, char **name)
{
	char *sav;

	*prefix = NULL;
	*system = NULL;
	*name = NULL;

	*prefix = strtok_r(word, ":", &sav);
	*system = strtok_r(NULL, "/", &sav);
	if (!(*system))
		return;

	*name = strtok_r(NULL, " \t", &sav);
	if (!(*name)) {
		*name = *system;
		*system = NULL;
	}
}

/*
 * Parse lines from dynamic_events, kprobe_events and uprobe_events files
 * PREFIX[:[SYSTEM/]EVENT] [ADDRSS] [FORMAT]
 */
static int dyn_generic_parse(struct dyn_events_desc *desc, const char *group,
			     char *line, struct tracefs_dynevent **ret_dyn)
{
	struct tracefs_dynevent *dyn;
	char *word;
	char *format = NULL;
	char *address = NULL;
	char *system;
	char *prefix;
	char *event;
	char *sav;

	if (strncmp(line, desc->prefix, strlen(desc->prefix)))
		return -1;

	word = strtok_r(line, " \t", &sav);
	if (!word || *word == '\0')
		return -1;

	parse_prefix(word, &prefix, &system, &event);
	if (!prefix)
		return -1;

	if (desc->type != TRACEFS_DYNEVENT_SYNTH) {
		address = strtok_r(NULL, " \t", &sav);
		if (!address || *address == '\0')
			return -1;
	}

	format = strtok_r(NULL, "", &sav);

	/* KPROBEs and UPROBEs share the same prefix, check the format */
	if (desc->type & (TRACEFS_DYNEVENT_UPROBE | TRACEFS_DYNEVENT_URETPROBE)) {
		if (!strchr(address, '/'))
			return -1;
	}

	if (group && (!system || strcmp(group, system) != 0))
		return -1;

	if (!ret_dyn)
		return 0;

	dyn = calloc(1, sizeof(*dyn));
	if (!dyn)
		return -1;

	dyn->type = desc->type;
	dyn->trace_file = strdup(desc->file);
	if (!dyn->trace_file)
		goto error;

	dyn->prefix = strdup(prefix);
	if (!dyn->prefix)
		goto error;

	if (system) {
		dyn->system = strdup(system);
		if (!dyn->system)
			goto error;
	}

	if (event) {
		dyn->event = strdup(event);
		if (!dyn->event)
			goto error;
	}

	if (address) {
		dyn->address = strdup(address);
		if (!dyn->address)
			goto error;
	}

	if (format) {
		dyn->format = strdup(format);
		if (!dyn->format)
			goto error;
	}

	*ret_dyn = dyn;
	return 0;
error:
	tracefs_dynevent_free(dyn);
	return -1;
}

static int dyn_synth_del(struct dyn_events_desc *desc, struct tracefs_dynevent *dyn)
{
	char *str;
	int ret;

	if (!strcmp(desc->file, DYNEVENTS_EVENTS))
		return dyn_generic_del(desc, dyn);

	ret = asprintf(&str, "!%s", dyn->event);
	if (ret < 0)
		return -1;

	ret = tracefs_instance_file_append(NULL, desc->file, str);
	free(str);

	return ret < 0 ? ret : 0;
}

/*
 * Parse lines from synthetic_events file
 * EVENT ARG [ARG]
 */
static int dyn_synth_parse(struct dyn_events_desc *desc, const char *group,
			   char *line, struct tracefs_dynevent **ret_dyn)
{
	struct tracefs_dynevent *dyn;
	char *format;
	char *event;
	char *sav;

	if (!strcmp(desc->file, DYNEVENTS_EVENTS))
		return dyn_generic_parse(desc, group, line, ret_dyn);

	/* synthetic_events file has slightly different syntax */
	event = strtok_r(line, " \t", &sav);
	if (!event || *event == '\0')
		return -1;

	format = strtok_r(NULL, "", &sav);
	if (!format || *format == '\0')
		return -1;

	if (!ret_dyn)
		return 0;

	dyn = calloc(1, sizeof(*dyn));
	if (!dyn)
		return -1;

	dyn->type = desc->type;
	dyn->trace_file = strdup(desc->file);
	if (!dyn->trace_file)
		goto error;

	dyn->event = strdup(event);
	if (!dyn->event)
		goto error;

	dyn->format = strdup(format+1);
	if (!dyn->format)
		goto error;

	*ret_dyn = dyn;
	return 0;
error:
	tracefs_dynevent_free(dyn);
	return -1;
}

static void init_devent_desc(void)
{
	int i;

	BUILD_BUG_ON(ARRAY_SIZE(dynevents) != EVENT_INDEX(TRACEFS_DYNEVENT_MAX));

	if (!tracefs_file_exists(NULL, DYNEVENTS_EVENTS))
		return;

	/* Use  ftrace dynamic_events, if available */
	for (i = 0; i < EVENT_INDEX(TRACEFS_DYNEVENT_MAX); i++)
		dynevents[i].file = DYNEVENTS_EVENTS;

	dynevents[EVENT_INDEX(TRACEFS_DYNEVENT_SYNTH)].prefix = "s";
}

static struct dyn_events_desc *get_devent_desc(enum tracefs_dynevent_type type)
{

	static bool init;

	if (type >= TRACEFS_DYNEVENT_MAX)
		return NULL;

	if (!init) {
		init_devent_desc();
		init = true;
	}

	return &dynevents[EVENT_INDEX(type)];
}

/**
 * dynevent_alloc - Allocate new dynamic event
 * @type: Type of the dynamic event
 * @system: The system name (NULL for the default dynamic)
 * @event: Name of the event
 * @addr: The function and offset (or address) to insert the probe
 * @format: The format string to define the probe.
 *
 * Allocate a dynamic event context that will be in the @system group
 * (or dynamic if @system is NULL). Have the name of @event and
 * will be associated to @addr, if applicable for that event type
 * (function name, with or without offset, or a address). And the @format will
 * define the format of the kprobe.
 * The dynamic event is not created in the system.
 *
 * Return a pointer to a dynamic event context on success, or NULL on error.
 * The returned pointer must be freed with tracefs_dynevent_free()
 *
 * errno will be set to EINVAL if event is NULL.
 */
__hidden struct tracefs_dynevent *
dynevent_alloc(enum tracefs_dynevent_type type, const char *system,
	       const char *event, const char *address, const char *format)
{
	struct tracefs_dynevent *devent;
	struct dyn_events_desc *desc;

	if (!event) {
		errno = EINVAL;
		return NULL;
	}

	desc = get_devent_desc(type);
	if (!desc || !desc->file) {
		errno = ENOTSUP;
		return NULL;
	}

	devent = calloc(1, sizeof(*devent));
	if (!devent)
		return NULL;

	devent->type = type;
	devent->trace_file = strdup(desc->file);
	if (!devent->trace_file)
		goto err;

	if (!system)
		system = DYNEVENTS_DEFAULT_GROUP;
	devent->system = strdup(system);
	if (!devent->system)
		goto err;

	devent->event = strdup(event);
	if (!devent->event)
		goto err;

	devent->prefix = strdup(desc->prefix);
	if (!devent->prefix)
		goto err;

	if (address) {
		devent->address = strdup(address);
		if (!devent->address)
			goto err;
	}
	if (format) {
		devent->format = strdup(format);
		if (!devent->format)
			goto err;
	}

	return devent;
err:
	tracefs_dynevent_free(devent);
	return NULL;
}

/**
 * tracefs_dynevent_create - Create a dynamic event in the system
 * @devent: Pointer to a dynamic event context, describing the event
 *
 * Return 0 on success, or -1 on error.
 */
int tracefs_dynevent_create(struct tracefs_dynevent *devent)
{
	char *str;
	int ret;

	if (!devent)
		return -1;

	if (devent->system && devent->system[0])
		ret = asprintf(&str, "%s%s%s/%s %s %s\n",
				devent->prefix, strlen(devent->prefix) ? ":" : "",
				devent->system, devent->event,
				devent->address ? devent->address : "",
				devent->format ? devent->format : "");
	else
		ret = asprintf(&str, "%s%s%s %s %s\n",
				devent->prefix, strlen(devent->prefix) ? ":" : "",
				devent->event,
				devent->address ? devent->address : "",
				devent->format ? devent->format : "");
	if (ret < 0)
		return -1;

	ret = tracefs_instance_file_append(NULL, devent->trace_file, str);
	free(str);

	return ret < 0 ? ret : 0;
}

static void disable_events(const char *system, const char *event,
			   char **list)
{
	struct tracefs_instance *instance;
	int i;

	/*
	 * Note, this will not fail even on error.
	 * That is because even if something fails, it may still
	 * work enough to clear the kprobes. If that's the case
	 * the clearing after the loop will succeed and the function
	 * is a success, even though other parts had failed. If
	 * one of the kprobe events is enabled in one of the
	 * instances that fail, then the clearing will fail too
	 * and the function will return an error.
	 */

	tracefs_event_disable(NULL, system, event);
	/* No need to test results */

	if (!list)
		return;

	for (i = 0; list[i]; i++) {
		instance = tracefs_instance_alloc(NULL, list[i]);
		/* If this fails, try the next one */
		if (!instance)
			continue;
		tracefs_event_disable(instance, system, event);
		tracefs_instance_free(instance);
	}
}

/**
 * tracefs_dynevent_destroy - Remove a dynamic event from the system
 * @devent: A dynamic event context, describing the dynamic event that will be deleted.
 * @force: Will attempt to disable all events before removing them.
 *
 * The dynamic event context is not freed by this API. It only removes the event from the system.
 * If there are any enabled events, and @force is not set, then it will error with -1 and errno
 * to be EBUSY.
 *
 * Return 0 on success, or -1 on error.
 */
int tracefs_dynevent_destroy(struct tracefs_dynevent *devent, bool force)
{
	struct dyn_events_desc *desc;
	char **instance_list;

	if (!devent)
		return -1;

	if (force) {
		instance_list = tracefs_instances(NULL);
		disable_events(devent->system, devent->event, instance_list);
		tracefs_list_free(instance_list);
	}

	desc = get_devent_desc(devent->type);
	if (!desc)
		return -1;

	return desc->del(desc, devent);
}

static int get_all_dynevents(enum tracefs_dynevent_type type, const char *system,
			     struct tracefs_dynevent ***ret_all)
{
	struct dyn_events_desc *desc;
	struct tracefs_dynevent *devent, **tmp, **all = NULL;
	char *content;
	int count = 0;
	char *line;
	char *next;
	int ret;

	desc = get_devent_desc(type);
	if (!desc)
		return -1;

	content = tracefs_instance_file_read(NULL, desc->file, NULL);
	if (!content)
		return -1;

	line = content;
	do {
		next = strchr(line, '\n');
		if (next)
			*next = '\0';
		ret = desc->parse(desc, system, line, ret_all ? &devent : NULL);
		if (!ret) {
			if (ret_all) {
				tmp = realloc(all, (count + 1) * sizeof(*tmp));
				if (!tmp)
					goto error;
				all = tmp;
				all[count] = devent;
			}
			count++;
		}
		line = next + 1;
	} while (next);

	free(content);
	if (ret_all)
		*ret_all = all;
	return count;

error:
	free(content);
	free(all);
	return -1;
}

/**
 * tracefs_dynevent_list_free - Deletes an array of pointers to dynamic event contexts
 * @events: An array of pointers to dynamic event contexts. The last element of the array
 *	    must be a NULL pointer.
 */
void tracefs_dynevent_list_free(struct tracefs_dynevent **events)
{
	int i;

	if (!events)
		return;

	for (i = 0; events[i]; i++)
		tracefs_dynevent_free(events[i]);

	free(events);
}

/**
 * tracefs_dynevent_get_all - return an array of pointers to dynamic events of given types
 * @types: Dynamic event type, or bitmask of dynamic event types. If 0 is passed, all types
 *	   are considered.
 * @system: Get events from that system only. If @system is NULL, events from all systems
 *	    are returned.
 *
 * Returns an array of pointers to dynamic events of given types that exist in the system.
 * The array must be freed with tracefs_dynevent_list_free(). If there are no events a NULL
 * pointer is returned.
 */
struct tracefs_dynevent **
tracefs_dynevent_get_all(unsigned int types, const char *system)
{
	struct tracefs_dynevent **events, **tmp, **all_events = NULL;
	int count, all = 0;
	int i;

	for (i = 1; i < TRACEFS_DYNEVENT_MAX; i <<= 1) {
		if (types) {
			if (i > types)
				break;
			if (!(types & i))
				continue;
		}
		count = get_all_dynevents(i, system, &events);
		if (count > 0) {
			tmp = realloc(all_events, (all + count + 1) * sizeof(*tmp));
			if (!tmp)
				goto error;
			all_events = tmp;
			memcpy(all_events + all, events, count * sizeof(*events));
			all += count;
			/* Add a NULL pointer at the end */
			all_events[all] = NULL;
			free(events);
		}
	}

	return all_events;

error:
	if (all_events) {
		for (i = 0; i < all; i++)
			free(all_events[i]);
		free(all_events);
	}
	return NULL;
}

/**
 * tracefs_dynevent_get - return a single dynamic event if it exists
 * @type; Dynamic event type
 * @system: Get events from that system only. May be NULL.
 * @event: Get event of the system type (may not be NULL)
 *
 * Returns the dynamic event of the given @type and @system for with the @event
 * name. If @system is NULL, it will return the first dynamic event that it finds
 * that matches the @event name.
 *
 * The returned event must be freed with tracefs_dynevent_free().
 * NULL is returned if no event match is found, or other error.
 */
struct tracefs_dynevent *
tracefs_dynevent_get(enum tracefs_dynevent_type type, const char *system,
		     const char *event)
{
	struct tracefs_dynevent **events;
	struct tracefs_dynevent *devent = NULL;
	int count;
	int i;

	if (!event) {
		errno = -EINVAL;
		return NULL;
	}

	count = get_all_dynevents(type, system, &events);
	if (count <= 0)
		return NULL;

	for (i = 0; i < count; i++) {
		if (strcmp(events[i]->event, event) == 0)
			break;
	}
	if (i < count) {
		devent = events[i];
		events[i] = NULL;
	}

	tracefs_dynevent_list_free(events);

	return devent;
}

/**
 * tracefs_dynevent_destroy_all - removes all dynamic events of given types from the system
 * @types: Dynamic event type, or bitmask of dynamic event types. If 0 is passed, all types
 *	   are considered.
 * @force: Will attempt to disable all events before removing them.
 *
 * Will remove all dynamic events of the given types from the system. If there are any enabled
 * events, and @force is not set, then the removal of these will fail. If @force is set, then
 * it will attempt to disable all the events in all instances before removing them.
 *
 * Returns zero if all requested events are removed successfully, or -1 if some of them are not
 * removed.
 */
int tracefs_dynevent_destroy_all(unsigned int types, bool force)
{
	struct tracefs_dynevent **all;
	int ret = 0;
	int i;

	all = tracefs_dynevent_get_all(types, NULL);
	if (!all)
		return 0;

	for (i = 0; all[i]; i++) {
		if (tracefs_dynevent_destroy(all[i], force))
			ret = -1;
	}

	tracefs_dynevent_list_free(all);

	return ret;
}

/**
 * dynevent_get_count - Count dynamic events of given types and system
 * @types: Dynamic event type, or bitmask of dynamic event types. If 0 is passed, all types
 *	   are considered.
 * @system: Count events from that system only. If @system is NULL, events from all systems
 *	    are counted.
 *
 * Return the count of requested dynamic events
 */
__hidden int dynevent_get_count(unsigned int types, const char *system)
{
	int count, all = 0;
	int i;

	for (i = 1; i < TRACEFS_DYNEVENT_MAX; i <<= 1) {
		if (types) {
			if (i > types)
				break;
			if (!(types & i))
				continue;
		}
		count = get_all_dynevents(i, system, NULL);
		if (count > 0)
			all += count;
	}

	return all;
}

static enum tracefs_dynevent_type
dynevent_info(struct tracefs_dynevent *dynevent, char **system,
	      char **event, char **prefix, char **addr, char **format)
{
	char **lv[] = { system, event, prefix, addr, format };
	char **rv[] = { &dynevent->system, &dynevent->event, &dynevent->prefix,
			&dynevent->address, &dynevent->format };
	int i;

	for (i = 0; i < ARRAY_SIZE(lv); i++) {
		if (lv[i]) {
			if (*rv[i]) {
				*lv[i] = strdup(*rv[i]);
				if (!*lv[i])
					goto error;
			} else {
				*lv[i] = NULL;
			}
		}
	}

	return dynevent->type;

error:
	for (i--; i >= 0; i--) {
		if (lv[i])
			free(*lv[i]);
	}

	return TRACEFS_DYNEVENT_UNKNOWN;
}

/**
 * tracefs_dynevent_info - return details of a dynamic event
 * @dynevent: A dynamic event context, describing given dynamic event.
 * @group: return, group in which the dynamic event is configured
 * @event: return, name of the dynamic event
 * @prefix: return, prefix string of the dynamic event
 * @addr: return, the function and offset (or address) of the dynamic event
 * @format: return, the format string of the dynamic event
 *
 * Returns the type of the dynamic event, or TRACEFS_DYNEVENT_UNKNOWN in case of an error.
 * Any of the @group, @event, @prefix, @addr and @format parameters are optional.
 * If a valid pointer is passed, in case of success - a string is allocated and returned.
 * These strings must be freed with free().
 */
enum tracefs_dynevent_type
tracefs_dynevent_info(struct tracefs_dynevent *dynevent, char **system,
		      char **event, char **prefix, char **addr, char **format)
{
	if (!dynevent)
		return TRACEFS_DYNEVENT_UNKNOWN;

	return dynevent_info(dynevent, system, event, prefix, addr, format);
}

/**
 * tracefs_dynevent_get_event - return tep event representing the given dynamic event
 * @tep: a handle to the trace event parser context that holds the events
 * @dynevent: a dynamic event context, describing given dynamic event.
 *
 * Returns a pointer to a tep event describing the given dynamic event. The pointer
 * is managed by the @tep handle and must not be freed. In case of an error, or in case
 * the requested dynamic event is missing in the @tep handler - NULL is returned.
 */
struct tep_event *
tracefs_dynevent_get_event(struct tep_handle *tep, struct tracefs_dynevent *dynevent)
{
	if (!tep || !dynevent || !dynevent->event)
		return NULL;

	return get_tep_event(tep, dynevent->system, dynevent->event);
}
