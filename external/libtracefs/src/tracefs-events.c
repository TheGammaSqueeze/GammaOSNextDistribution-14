// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2008, 2009, 2010 Red Hat Inc, Steven Rostedt <srostedt@redhat.com>
 *
 * Updates:
 * Copyright (C) 2019, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */
#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <limits.h>

#include <kbuffer.h>

#include "tracefs.h"
#include "tracefs-local.h"

static struct follow_event *root_followers;
static int nr_root_followers;

static struct follow_event *root_missed_followers;
static int nr_root_missed_followers;

struct cpu_iterate {
	struct tracefs_cpu *tcpu;
	struct tep_record record;
	struct tep_event *event;
	struct kbuffer *kbuf;
	void *page;
	int psize;
	int cpu;
};

static int read_kbuf_record(struct cpu_iterate *cpu)
{
	unsigned long long ts;
	void *ptr;

	if (!cpu || !cpu->kbuf)
		return -1;
	ptr = kbuffer_read_event(cpu->kbuf, &ts);
	if (!ptr)
		return -1;

	memset(&cpu->record, 0, sizeof(cpu->record));
	cpu->record.ts = ts;
	cpu->record.size = kbuffer_event_size(cpu->kbuf);
	cpu->record.record_size = kbuffer_curr_size(cpu->kbuf);
	cpu->record.missed_events = kbuffer_missed_events(cpu->kbuf);
	cpu->record.cpu = cpu->cpu;
	cpu->record.data = ptr;
	cpu->record.ref_count = 1;

	kbuffer_next_event(cpu->kbuf, NULL);

	return 0;
}

int read_next_page(struct tep_handle *tep, struct cpu_iterate *cpu)
{
	enum kbuffer_long_size long_size;
	enum kbuffer_endian endian;
	int r;

	if (!cpu->tcpu)
		return -1;

	r = tracefs_cpu_buffered_read(cpu->tcpu, cpu->page, true);
	/*
	 * tracefs_cpu_buffered_read() only reads in full subbuffer size,
	 * but this wants partial buffers as well. If the function returns
	 * empty (-1 for EAGAIN), try tracefs_cpu_read() next, as that can
	 * read partially filled buffers too, but isn't as efficient.
	 */
	if (r <= 0)
		r = tracefs_cpu_read(cpu->tcpu, cpu->page, true);
	if (r <= 0)
		return -1;

	if (!cpu->kbuf) {
		if (tep_is_file_bigendian(tep))
			endian = KBUFFER_ENDIAN_BIG;
		else
			endian = KBUFFER_ENDIAN_LITTLE;

		if (tep_get_header_page_size(tep) == 8)
			long_size = KBUFFER_LSIZE_8;
		else
			long_size = KBUFFER_LSIZE_4;

		cpu->kbuf = kbuffer_alloc(long_size, endian);
		if (!cpu->kbuf)
			return -1;
	}

	kbuffer_load_subbuffer(cpu->kbuf, cpu->page);
	if (kbuffer_subbuffer_size(cpu->kbuf) > r) {
		tracefs_warning("%s: page_size > %d", __func__, r);
		return -1;
	}

	return 0;
}

int read_next_record(struct tep_handle *tep, struct cpu_iterate *cpu)
{
	int id;

	do {
		while (!read_kbuf_record(cpu)) {
			id = tep_data_type(tep, &(cpu->record));
			cpu->event = tep_find_event(tep, id);
			if (cpu->event)
				return 0;
		}
	} while (!read_next_page(tep, cpu));

	return -1;
}

/**
 * tracefs_follow_missed_events - Add callback for missed events for iterators
 * @instance: The instance to follow
 * @callback: The function to call when missed events is detected
 * @callback_data: The data to pass to @callback
 *
 * This attaches a callback to an @instance or the root instance if @instance
 * is NULL, where if tracefs_iterate_raw_events() is called, that if missed
 * events are detected, it will call @callback, with the following parameters:
 *  @event: The event pointer of the record with the missing events
 *  @record; The event instance of @event.
 *  @cpu: The cpu that the event happened on.
 *  @callback_data: The same as @callback_data passed to the function.
 *
 * If the count of missing events is available, @record->missed_events
 * will have a positive number holding the number of missed events since
 * the last event on the same CPU, or just -1 if that number is unknown
 * but missed events did happen.
 *
 * Returns 0 on success and -1 on error.
 */
int tracefs_follow_missed_events(struct tracefs_instance *instance,
				 int (*callback)(struct tep_event *,
						 struct tep_record *,
						 int, void *),
				 void *callback_data)
{
	struct follow_event **followers;
	struct follow_event *follower;
	struct follow_event follow;
	int *nr_followers;

	follow.event = NULL;
	follow.callback = callback;
	follow.callback_data = callback_data;

	if (instance) {
		followers = &instance->missed_followers;
		nr_followers = &instance->nr_missed_followers;
	} else {
		followers = &root_missed_followers;
		nr_followers = &nr_root_missed_followers;
	}
	follower = realloc(*followers, sizeof(*follower) *
			    ((*nr_followers) + 1));
	if (!follower)
		return -1;

	*followers = follower;
	follower[(*nr_followers)++] = follow;

	return 0;
}

static int call_missed_events(struct tracefs_instance *instance,
			      struct tep_event *event, struct tep_record *record, int cpu)
{
	struct follow_event *followers;
	int nr_followers;
	int ret = 0;
	int i;

	if (instance) {
		followers = instance->missed_followers;
		nr_followers = instance->nr_missed_followers;
	} else {
		followers = root_missed_followers;
		nr_followers = nr_root_missed_followers;
	}

	if (!followers)
		return 0;

	for (i = 0; i < nr_followers; i++) {
		ret |= followers[i].callback(event, record,
					     cpu, followers[i].callback_data);
	}

	return ret;
}

static int call_followers(struct tracefs_instance *instance,
			  struct tep_event *event, struct tep_record *record, int cpu)
{
	struct follow_event *followers;
	int nr_followers;
	int ret = 0;
	int i;

	if (record->missed_events)
		ret = call_missed_events(instance, event, record, cpu);
	if (ret)
		return ret;

	if (instance) {
		followers = instance->followers;
		nr_followers = instance->nr_followers;
	} else {
		followers = root_followers;
		nr_followers = nr_root_followers;
	}

	if (!followers)
		return 0;

	for (i = 0; i < nr_followers; i++) {
		if (followers[i].event == event)
			ret |= followers[i].callback(event, record,
						     cpu, followers[i].callback_data);
	}

	return ret;
}

static int read_cpu_pages(struct tep_handle *tep, struct tracefs_instance *instance,
			  struct cpu_iterate *cpus, int count,
			  int (*callback)(struct tep_event *,
					  struct tep_record *,
					  int, void *),
			  void *callback_context,
			  bool *keep_going)
{
	bool has_data = false;
	int ret;
	int i, j;

	for (i = 0; i < count; i++) {
		ret = read_next_record(tep, cpus + i);
		if (!ret)
			has_data = true;
	}

	while (has_data && *(volatile bool *)keep_going) {
		j = count;
		for (i = 0; i < count; i++) {
			if (!cpus[i].event)
				continue;
			if (j == count || cpus[j].record.ts > cpus[i].record.ts)
				j = i;
		}
		if (j < count) {
			if (call_followers(instance, cpus[j].event, &cpus[j].record, cpus[j].cpu))
				break;
			if (callback &&
			    callback(cpus[j].event, &cpus[j].record, cpus[j].cpu, callback_context))
				break;
			cpus[j].event = NULL;
			read_next_record(tep, cpus + j);
		} else {
			has_data = false;
		}
	}

	return 0;
}

static int open_cpu_files(struct tracefs_instance *instance, cpu_set_t *cpus,
			  int cpu_size, struct cpu_iterate **all_cpus, int *count)
{
	struct tracefs_cpu *tcpu;
	struct cpu_iterate *tmp;
	int nr_cpus;
	int cpu;
	int i = 0;

	*all_cpus = NULL;

	nr_cpus = sysconf(_SC_NPROCESSORS_CONF);
	for (cpu = 0; cpu < nr_cpus; cpu++) {
		if (cpus && !CPU_ISSET_S(cpu, cpu_size, cpus))
			continue;
		tcpu = tracefs_cpu_open(instance, cpu, true);
		tmp = realloc(*all_cpus, (i + 1) * sizeof(*tmp));
		if (!tmp) {
			i--;
			goto error;
		}

		*all_cpus = tmp;

		memset(tmp + i, 0, sizeof(*tmp));

		if (!tcpu)
			goto error;

		tmp[i].tcpu = tcpu;
		tmp[i].cpu = cpu;
		tmp[i].psize = tracefs_cpu_read_size(tcpu);
		tmp[i].page =  malloc(tmp[i].psize);

		if (!tmp[i++].page)
			goto error;
	}
	*count = i;
	return 0;
 error:
	tmp = *all_cpus;
	for (; i >= 0; i--) {
		tracefs_cpu_close(tmp[i].tcpu);
		free(tmp[i].page);
	}
	free(tmp);
	*all_cpus = NULL;
	return -1;
}

/**
 * tracefs_follow_event - Add callback for specific events for iterators
 * @tep: a handle to the trace event parser context
 * @instance: The instance to follow
 * @system: The system of the event to track
 * @event_name: The name of the event to track
 * @callback: The function to call when the event is hit in an iterator
 * @callback_data: The data to pass to @callback
 *
 * This attaches a callback to an @instance or the root instance if @instance
 * is NULL, where if tracefs_iterate_raw_events() is called, that if the specified
 * event is hit, it will call @callback, with the following parameters:
 *  @event: The event pointer that was found by @system and @event_name.
 *  @record; The event instance of @event.
 *  @cpu: The cpu that the event happened on.
 *  @callback_data: The same as @callback_data passed to the function.
 *
 * Returns 0 on success and -1 on error.
 */
int tracefs_follow_event(struct tep_handle *tep, struct tracefs_instance *instance,
			  const char *system, const char *event_name,
			  int (*callback)(struct tep_event *,
					  struct tep_record *,
					  int, void *),
			  void *callback_data)
{
	struct follow_event **followers;
	struct follow_event *follower;
	struct follow_event follow;
	int *nr_followers;

	if (!tep) {
		errno = EINVAL;
		return -1;
	}

	follow.event = tep_find_event_by_name(tep, system, event_name);
	if (!follow.event) {
		errno = ENOENT;
		return -1;
	}

	follow.callback = callback;
	follow.callback_data = callback_data;

	if (instance) {
		followers = &instance->followers;
		nr_followers = &instance->nr_followers;
	} else {
		followers = &root_followers;
		nr_followers = &nr_root_followers;
	}
	follower = realloc(*followers, sizeof(*follower) *
			    ((*nr_followers) + 1));
	if (!follower)
		return -1;

	*followers = follower;
	follower[(*nr_followers)++] = follow;

	return 0;
}

static bool top_iterate_keep_going;

/*
 * tracefs_iterate_raw_events - Iterate through events in trace_pipe_raw,
 *				per CPU trace buffers
 * @tep: a handle to the trace event parser context
 * @instance: ftrace instance, can be NULL for the top instance
 * @cpus: Iterate only through the buffers of CPUs, set in the mask.
 *	  If NULL, iterate through all CPUs.
 * @cpu_size: size of @cpus set
 * @callback: A user function, called for each record from the file
 * @callback_context: A custom context, passed to the user callback function
 *
 * If the @callback returns non-zero, the iteration stops - in that case all
 * records from the current page will be lost from future reads
 * The events are iterated in sorted order, oldest first.
 *
 * Returns -1 in case of an error, or 0 otherwise
 */
int tracefs_iterate_raw_events(struct tep_handle *tep,
				struct tracefs_instance *instance,
				cpu_set_t *cpus, int cpu_size,
				int (*callback)(struct tep_event *,
						struct tep_record *,
						int, void *),
				void *callback_context)
{
	bool *keep_going = instance ? &instance->iterate_keep_going :
				      &top_iterate_keep_going;
	struct follow_event *followers;
	struct cpu_iterate *all_cpus;
	int count = 0;
	int ret;
	int i;

	(*(volatile bool *)keep_going) = true;

	if (!tep)
		return -1;

	if (instance)
		followers = instance->followers;
	else
		followers = root_followers;
	if (!callback && !followers)
		return -1;

	ret = open_cpu_files(instance, cpus, cpu_size, &all_cpus, &count);
	if (ret < 0)
		goto out;
	ret = read_cpu_pages(tep, instance, all_cpus, count,
			     callback, callback_context,
			     keep_going);

out:
	if (all_cpus) {
		for (i = 0; i < count; i++) {
			kbuffer_free(all_cpus[i].kbuf);
			tracefs_cpu_close(all_cpus[i].tcpu);
			free(all_cpus[i].page);
		}
		free(all_cpus);
	}

	return ret;
}

/**
 * tracefs_iterate_stop - stop the iteration over the raw events.
 * @instance: ftrace instance, can be NULL for top tracing instance.
 */
void tracefs_iterate_stop(struct tracefs_instance *instance)
{
	if (instance)
		instance->iterate_keep_going = false;
	else
		top_iterate_keep_going = false;
}

static int add_list_string(char ***list, const char *name)
{
	char **tmp;

	tmp = tracefs_list_add(*list, name);
	if (!tmp) {
		tracefs_list_free(*list);
		*list = NULL;
		return -1;
	}

	*list = tmp;
	return 0;
}

__hidden char *trace_append_file(const char *dir, const char *name)
{
	char *file;
	int ret;

	ret = asprintf(&file, "%s/%s", dir, name);

	return ret < 0 ? NULL : file;
}

static int event_file(char **path, const char *system,
		      const char *event, const char *file)
{
	if (!system || !event || !file)
		return -1;

	return asprintf(path, "events/%s/%s/%s",
			system, event, file);
}

/**
 * tracefs_event_get_file - return a file in an event directory
 * @instance: The instance the event is in (NULL for top level)
 * @system: The system name that the event file is in
 * @event: The event name of the event
 * @file: The name of the file in the event directory.
 *
 * Returns a path to a file in the event director.
 * or NULL on error. The path returned must be freed with
 * tracefs_put_tracing_file().
 */
char *tracefs_event_get_file(struct tracefs_instance *instance,
			     const char *system, const char *event,
			     const char *file)
{
	char *instance_path;
	char *path;
	int ret;

	ret = event_file(&path, system, event, file);
	if (ret < 0)
		return NULL;

	instance_path = tracefs_instance_get_file(instance, path);
	free(path);

	return instance_path;
}

/**
 * tracefs_event_file_read - read the content from an event file
 * @instance: The instance the event is in (NULL for top level)
 * @system: The system name that the event file is in
 * @event: The event name of the event
 * @file: The name of the file in the event directory.
 * @psize: the size of the content read.
 *
 * Reads the content of the event file that is passed via the
 * arguments and returns the content.
 *
 * Return a string containing the content of the file or NULL
 * on error. The string returned must be freed with free().
 */
char *tracefs_event_file_read(struct tracefs_instance *instance,
			      const char *system, const char *event,
			      const char *file, int *psize)
{
	char *content;
	char *path;
	int ret;

	ret = event_file(&path, system, event, file);
	if (ret < 0)
		return NULL;

	content = tracefs_instance_file_read(instance, path, psize);
	free(path);
	return content;
}

/**
 * tracefs_event_file_write - write to an event file
 * @instance: The instance the event is in (NULL for top level)
 * @system: The system name that the event file is in
 * @event: The event name of the event
 * @file: The name of the file in the event directory.
 * @str: The string to write into the file
 *
 * Writes the content of @str to a file in the instance directory.
 * The content of the file will be overwritten by @str.
 *
 * Return 0 on success, and -1 on error.
 */
int tracefs_event_file_write(struct tracefs_instance *instance,
			     const char *system, const char *event,
			     const char *file, const char *str)
{
	char *path;
	int ret;

	ret = event_file(&path, system, event, file);
	if (ret < 0)
		return -1;

	ret = tracefs_instance_file_write(instance, path, str);
	free(path);
	return ret;
}

/**
 * tracefs_event_file_append - write to an event file
 * @instance: The instance the event is in (NULL for top level)
 * @system: The system name that the event file is in
 * @event: The event name of the event
 * @file: The name of the file in the event directory.
 * @str: The string to write into the file
 *
 * Writes the content of @str to a file in the instance directory.
 * The content of @str will be appended to the content of the file.
 * The current content should not be lost.
 *
 * Return 0 on success, and -1 on error.
 */
int tracefs_event_file_append(struct tracefs_instance *instance,
			      const char *system, const char *event,
			      const char *file, const char *str)
{
	char *path;
	int ret;

	ret = event_file(&path, system, event, file);
	if (ret < 0)
		return -1;

	ret = tracefs_instance_file_append(instance, path, str);
	free(path);
	return ret;
}

/**
 * tracefs_event_file_clear - clear an event file
 * @instance: The instance the event is in (NULL for top level)
 * @system: The system name that the event file is in
 * @event: The event name of the event
 * @file: The name of the file in the event directory.
 *
 * Clears the content of the event file. That is, it is opened
 * with O_TRUNC and then closed.
 *
 * Return 0 on success, and -1 on error.
 */
int tracefs_event_file_clear(struct tracefs_instance *instance,
			     const char *system, const char *event,
			     const char *file)
{
	char *path;
	int ret;

	ret = event_file(&path, system, event, file);
	if (ret < 0)
		return -1;

	ret = tracefs_instance_file_clear(instance, path);
	free(path);
	return ret;
}

/**
 * tracefs_event_file_exits - test if a file exists
 * @instance: The instance the event is in (NULL for top level)
 * @system: The system name that the event file is in
 * @event: The event name of the event
 * @file: The name of the file in the event directory.
 *
 * Return true if the file exists, false if it odes not or
 * an error occurred.
 */
bool tracefs_event_file_exists(struct tracefs_instance *instance,
			       const char *system, const char *event,
			       const char *file)
{
	char *path;
	bool ret;

	if (event_file(&path, system, event, file) < 0)
		return false;

	ret = tracefs_file_exists(instance, path);
	free(path);
	return ret;
}

/**
 * tracefs_event_systems - return list of systems for tracing
 * @tracing_dir: directory holding the "events" directory
 *		 if NULL, top tracing directory is used
 *
 * Returns an allocated list of system names. Both the names and
 * the list must be freed with tracefs_list_free()
 * The list returned ends with a "NULL" pointer
 */
char **tracefs_event_systems(const char *tracing_dir)
{
	struct dirent *dent;
	char **systems = NULL;
	char *events_dir;
	struct stat st;
	DIR *dir;
	int ret;

	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();

	if (!tracing_dir)
		return NULL;

	events_dir = trace_append_file(tracing_dir, "events");
	if (!events_dir)
		return NULL;

	/*
	 * Search all the directories in the events directory,
	 * and collect the ones that have the "enable" file.
	 */
	ret = stat(events_dir, &st);
	if (ret < 0 || !S_ISDIR(st.st_mode))
		goto out_free;

	dir = opendir(events_dir);
	if (!dir)
		goto out_free;

	while ((dent = readdir(dir))) {
		const char *name = dent->d_name;
		char *enable;
		char *sys;

		if (strcmp(name, ".") == 0 ||
		    strcmp(name, "..") == 0)
			continue;

		sys = trace_append_file(events_dir, name);
		ret = stat(sys, &st);
		if (ret < 0 || !S_ISDIR(st.st_mode)) {
			free(sys);
			continue;
		}

		enable = trace_append_file(sys, "enable");

		ret = stat(enable, &st);
		if (ret >= 0) {
			if (add_list_string(&systems, name) < 0)
				goto out_free;
		}
		free(enable);
		free(sys);
	}

	closedir(dir);

 out_free:
	free(events_dir);
	return systems;
}

/**
 * tracefs_system_events - return list of events for system
 * @tracing_dir: directory holding the "events" directory
 * @system: the system to return the events for
 *
 * Returns an allocated list of event names. Both the names and
 * the list must be freed with tracefs_list_free()
 * The list returned ends with a "NULL" pointer
 */
char **tracefs_system_events(const char *tracing_dir, const char *system)
{
	struct dirent *dent;
	char **events = NULL;
	char *system_dir = NULL;
	struct stat st;
	DIR *dir;
	int ret;

	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();

	if (!tracing_dir || !system)
		return NULL;

	asprintf(&system_dir, "%s/events/%s", tracing_dir, system);
	if (!system_dir)
		return NULL;

	ret = stat(system_dir, &st);
	if (ret < 0 || !S_ISDIR(st.st_mode))
		goto out_free;

	dir = opendir(system_dir);
	if (!dir)
		goto out_free;

	while ((dent = readdir(dir))) {
		const char *name = dent->d_name;
		char *event;

		if (strcmp(name, ".") == 0 ||
		    strcmp(name, "..") == 0)
			continue;

		event = trace_append_file(system_dir, name);
		ret = stat(event, &st);
		if (ret < 0 || !S_ISDIR(st.st_mode)) {
			free(event);
			continue;
		}

		if (add_list_string(&events, name) < 0)
			goto out_free;

		free(event);
	}

	closedir(dir);

 out_free:
	free(system_dir);

	return events;
}

/**
 * tracefs_tracers - returns an array of available tracers
 * @tracing_dir: The directory that contains the tracing directory
 *
 * Returns an allocate list of plugins. The array ends with NULL
 * Both the plugin names and array must be freed with tracefs_list_free()
 */
char **tracefs_tracers(const char *tracing_dir)
{
	char *available_tracers;
	struct stat st;
	char **plugins = NULL;
	char *buf;
	char *str, *saveptr;
	char *plugin;
	int slen;
	int len;
	int ret;

	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();

	if (!tracing_dir)
		return NULL;

	available_tracers = trace_append_file(tracing_dir, "available_tracers");
	if (!available_tracers)
		return NULL;

	ret = stat(available_tracers, &st);
	if (ret < 0)
		goto out_free;

	len = str_read_file(available_tracers, &buf, true);
	if (len <= 0)
		goto out_free;

	for (str = buf; ; str = NULL) {
		plugin = strtok_r(str, " ", &saveptr);
		if (!plugin)
			break;
		slen = strlen(plugin);
		if (!slen)
			continue;

		/* chop off any newlines */
		if (plugin[slen - 1] == '\n')
			plugin[slen - 1] = '\0';

		/* Skip the non tracers */
		if (strcmp(plugin, "nop") == 0 ||
		    strcmp(plugin, "none") == 0)
			continue;

		if (add_list_string(&plugins, plugin) < 0)
			break;
	}
	free(buf);

 out_free:
	free(available_tracers);

	return plugins;
}

static int load_events(struct tep_handle *tep,
		       const char *tracing_dir, const char *system, bool check)
{
	int ret = 0, failure = 0;
	char **events = NULL;
	struct stat st;
	int len = 0;
	int i;

	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();

	events = tracefs_system_events(tracing_dir, system);
	if (!events)
		return -ENOENT;

	for (i = 0; events[i]; i++) {
		char *format;
		char *buf;

		ret = asprintf(&format, "%s/events/%s/%s/format",
			       tracing_dir, system, events[i]);
		if (ret < 0) {
			failure = -ENOMEM;
			break;
		}

		ret = stat(format, &st);
		if (ret < 0)
			goto next_event;

		/* check if event is already added, to avoid duplicates */
		if (check && tep_find_event_by_name(tep, system, events[i]))
			goto next_event;

		len = str_read_file(format, &buf, true);
		if (len <= 0)
			goto next_event;

		ret = tep_parse_event(tep, buf, len, system);
		free(buf);
next_event:
		free(format);
		if (ret)
			failure = ret;
	}

	tracefs_list_free(events);
	return failure;
}

__hidden int trace_rescan_events(struct tep_handle *tep,
				const char *tracing_dir, const char *system)
{
	/* ToDo: add here logic for deleting removed events from tep handle */
	return load_events(tep, tracing_dir, system, true);
}

__hidden int trace_load_events(struct tep_handle *tep,
			       const char *tracing_dir, const char *system)
{
	return load_events(tep, tracing_dir, system, false);
}

__hidden struct tep_event *get_tep_event(struct tep_handle *tep,
					 const char *system, const char *name)
{
	struct tep_event *event;

	/* Check if event exists in the system */
	if (!tracefs_event_file_exists(NULL, system, name, "format"))
		return NULL;

	/* If the event is already loaded in the tep, return it */
	event = tep_find_event_by_name(tep, system, name);
	if (event)
		return event;

	/* Try to load any new events from the given system */
	if (trace_rescan_events(tep, NULL, system))
		return NULL;

	return tep_find_event_by_name(tep, system, name);
}

static int read_header(struct tep_handle *tep, const char *tracing_dir)
{
	struct stat st;
	char *header;
	char *buf;
	int len;
	int ret = -1;

	header = trace_append_file(tracing_dir, "events/header_page");

	ret = stat(header, &st);
	if (ret < 0)
		goto out;

	len = str_read_file(header, &buf, true);
	if (len <= 0)
		goto out;

	tep_parse_header_page(tep, buf, len, sizeof(long));

	free(buf);

	ret = 0;
 out:
	free(header);
	return ret;
}

static bool contains(const char *name, const char * const *names)
{
	if (!names)
		return false;
	for (; *names; names++)
		if (strcmp(name, *names) == 0)
			return true;
	return false;
}

static void load_kallsyms(struct tep_handle *tep)
{
	char *buf;

	if (str_read_file("/proc/kallsyms", &buf, false) <= 0)
		return;

	tep_parse_kallsyms(tep, buf);
	free(buf);
}

static int load_saved_cmdlines(const char *tracing_dir,
			       struct tep_handle *tep, bool warn)
{
	char *path;
	char *buf;
	int ret;

	path = trace_append_file(tracing_dir, "saved_cmdlines");
	if (!path)
		return -1;

	ret = str_read_file(path, &buf, false);
	free(path);
	if (ret <= 0)
		return -1;

	ret = tep_parse_saved_cmdlines(tep, buf);
	free(buf);

	return ret;
}

static void load_printk_formats(const char *tracing_dir,
				struct tep_handle *tep)
{
	char *path;
	char *buf;
	int ret;

	path = trace_append_file(tracing_dir, "printk_formats");
	if (!path)
		return;

	ret = str_read_file(path, &buf, false);
	free(path);
	if (ret <= 0)
		return;

	tep_parse_printk_formats(tep, buf);
	free(buf);
}

/*
 * Do a best effort attempt to load kallsyms, saved_cmdlines and
 * printk_formats. If they can not be loaded, then this will not
 * do the mappings. But this does not fail the loading of events.
 */
static void load_mappings(const char *tracing_dir,
			  struct tep_handle *tep)
{
	load_kallsyms(tep);

	/* If there's no tracing_dir no reason to go further */
	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();

	if (!tracing_dir)
		return;

	load_saved_cmdlines(tracing_dir, tep, false);
	load_printk_formats(tracing_dir, tep);
}

int tracefs_load_cmdlines(const char *tracing_dir, struct tep_handle *tep)
{

	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();

	if (!tracing_dir)
		return -1;

	return load_saved_cmdlines(tracing_dir, tep, true);
}

static int fill_local_events_system(const char *tracing_dir,
				    struct tep_handle *tep,
				    const char * const *sys_names,
				    int *parsing_failures)
{
	char **systems = NULL;
	int ret;
	int i;

	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();
	if (!tracing_dir)
		return -1;

	systems = tracefs_event_systems(tracing_dir);
	if (!systems)
		return -1;

	ret = read_header(tep, tracing_dir);
	if (ret < 0) {
		ret = -1;
		goto out;
	}

	if (parsing_failures)
		*parsing_failures = 0;

	for (i = 0; systems[i]; i++) {
		if (sys_names && !contains(systems[i], sys_names))
			continue;
		ret = trace_load_events(tep, tracing_dir, systems[i]);
		if (ret && parsing_failures)
			(*parsing_failures)++;
	}

	/* Include ftrace, as it is excluded for not having "enable" file */
	if (!sys_names || contains("ftrace", sys_names))
		trace_load_events(tep, tracing_dir, "ftrace");

	load_mappings(tracing_dir, tep);

	/* always succeed because parsing failures are not critical */
	ret = 0;
out:
	tracefs_list_free(systems);
	return ret;
}

static void set_tep_cpus(const char *tracing_dir, struct tep_handle *tep)
{
	struct stat st;
	char path[PATH_MAX];
	int cpus = sysconf(_SC_NPROCESSORS_CONF);
	int max_cpu = 0;
	int ret;
	int i;

	if (!tracing_dir)
		tracing_dir = tracefs_tracing_dir();

	/*
	 * Paranoid: in case sysconf() above does not work.
	 * And we also only care about the number of tracing
	 * buffers that exist. If cpus is 32, but the top half
	 * is offline, there may only be 16 tracing buffers.
	 * That's what we want to know.
	 */
	for (i = 0; !cpus || i < cpus; i++) {
		snprintf(path, PATH_MAX, "%s/per_cpu/cpu%d", tracing_dir, i);
		ret = stat(path, &st);
		if (!ret && S_ISDIR(st.st_mode))
			max_cpu = i + 1;
		else if (i >= cpus)
			break;
	}

	if (!max_cpu)
		max_cpu = cpus;

	tep_set_cpus(tep, max_cpu);
}

/**
 * tracefs_local_events_system - create a tep from the events of the specified subsystem.
 *
 * @tracing_dir: The directory that contains the events.
 * @sys_name: Array of system names, to load the events from.
 * The last element from the array must be NULL
 *
 * Returns a tep structure that contains the tep local to
 * the system.
 */
struct tep_handle *tracefs_local_events_system(const char *tracing_dir,
					       const char * const *sys_names)
{
	struct tep_handle *tep = NULL;

	tep = tep_alloc();
	if (!tep)
		return NULL;

	if (fill_local_events_system(tracing_dir, tep, sys_names, NULL)) {
		tep_free(tep);
		tep = NULL;
	}

	set_tep_cpus(tracing_dir, tep);

	/* Set the long size for this tep handle */
	tep_set_long_size(tep, tep_get_header_page_size(tep));

	return tep;
}

/**
 * tracefs_local_events - create a tep from the events on system
 * @tracing_dir: The directory that contains the events.
 *
 * Returns a tep structure that contains the teps local to
 * the system.
 */
struct tep_handle *tracefs_local_events(const char *tracing_dir)
{
	return tracefs_local_events_system(tracing_dir, NULL);
}

/**
 * tracefs_fill_local_events - Fill a tep with the events on system
 * @tracing_dir: The directory that contains the events.
 * @tep: Allocated tep handler which will be filled
 * @parsing_failures: return number of failures while parsing the event files
 *
 * Returns whether the operation succeeded
 */
int tracefs_fill_local_events(const char *tracing_dir,
			       struct tep_handle *tep, int *parsing_failures)
{
	return fill_local_events_system(tracing_dir, tep,
					NULL, parsing_failures);
}

static bool match(const char *str, regex_t *re)
{
	return regexec(re, str, 0, NULL, 0) == 0;
}

enum event_state {
	STATE_INIT,
	STATE_ENABLED,
	STATE_DISABLED,
	STATE_MIXED,
	STATE_ERROR,
};

static int read_event_state(struct tracefs_instance *instance, const char *file,
			    enum event_state *state)
{
	char *val;
	int ret = 0;

	if (*state == STATE_ERROR)
		return -1;

	val = tracefs_instance_file_read(instance, file, NULL);
	if (!val)
		return -1;

	switch (val[0]) {
	case '0':
		switch (*state) {
		case STATE_INIT:
			*state = STATE_DISABLED;
			break;
		case STATE_ENABLED:
			*state = STATE_MIXED;
			break;
		default:
			break;
		}
		break;
	case '1':
		switch (*state) {
		case STATE_INIT:
			*state = STATE_ENABLED;
			break;
		case STATE_DISABLED:
			*state = STATE_MIXED;
			break;
		default:
			break;
		}
		break;
	case 'X':
		*state = STATE_MIXED;
		break;
	default:
		*state = TRACEFS_ERROR;
		ret = -1;
		break;
	}
	free(val);

	return ret;
}

static int enable_disable_event(struct tracefs_instance *instance,
				const char *system, const char *event,
				bool enable, enum event_state *state)
{
	const char *str = enable ? "1" : "0";
	char *system_event;
	int ret;

	ret = asprintf(&system_event, "events/%s/%s/enable", system, event);
	if (ret < 0)
		return ret;

	if (state)
		ret = read_event_state(instance, system_event, state);
	else
		ret = tracefs_instance_file_write(instance, system_event, str);
	free(system_event);

	return ret;
}

static int enable_disable_system(struct tracefs_instance *instance,
				 const char *system, bool enable,
				 enum event_state *state)
{
	const char *str = enable ? "1" : "0";
	char *system_path;
	int ret;

	ret = asprintf(&system_path, "events/%s/enable", system);
	if (ret < 0)
		return ret;

	if (state)
		ret = read_event_state(instance, system_path, state);
	else
		ret = tracefs_instance_file_write(instance, system_path, str);
	free(system_path);

	return ret;
}

static int enable_disable_all(struct tracefs_instance *instance,
			      bool enable)
{
	const char *str = enable ? "1" : "0";
	int ret;

	ret = tracefs_instance_file_write(instance, "events/enable", str);
	return ret < 0 ? ret : 0;
}

static int make_regex(regex_t *re, const char *match)
{
	int len = strlen(match);
	char str[len + 3];
	char *p = &str[0];

	if (!len || match[0] != '^')
		*(p++) = '^';

	strcpy(p, match);
	p += len;

	if (!len || match[len-1] != '$')
		*(p++) = '$';

	*p = '\0';

	return regcomp(re, str, REG_ICASE|REG_NOSUB);
}

static int event_enable_disable(struct tracefs_instance *instance,
				const char *system, const char *event,
				bool enable, enum event_state *state)
{
	regex_t system_re, event_re;
	char **systems;
	char **events = NULL;
	int ret = -1;
	int s, e;

	/* Handle all events first */
	if (!system && !event)
		return enable_disable_all(instance, enable);

	systems = tracefs_event_systems(NULL);
	if (!systems)
		goto out_free;

	if (system) {
		ret = make_regex(&system_re, system);
		if (ret < 0)
			goto out_free;
	}
	if (event) {
		ret = make_regex(&event_re, event);
		if (ret < 0) {
			if (system)
				regfree(&system_re);
			goto out_free;
		}
	}

	ret = -1;
	for (s = 0; systems[s]; s++) {
		if (system && !match(systems[s], &system_re))
			continue;

		/* Check for the short cut first */
		if (!event) {
			ret = enable_disable_system(instance, systems[s], enable, state);
			if (ret < 0)
				break;
			ret = 0;
			continue;
		}

		events = tracefs_system_events(NULL, systems[s]);
		if (!events)
			continue; /* Error? */

		for (e = 0; events[e]; e++) {
			if (!match(events[e], &event_re))
				continue;
			ret = enable_disable_event(instance, systems[s],
						   events[e], enable, state);
			if (ret < 0)
				break;
			ret = 0;
		}
		tracefs_list_free(events);
		events = NULL;
	}
	if (system)
		regfree(&system_re);
	if (event)
		regfree(&event_re);

 out_free:
	tracefs_list_free(systems);
	tracefs_list_free(events);
	return ret;
}

/**
 * tracefs_event_enable - enable specified events
 * @instance: ftrace instance, can be NULL for the top instance
 * @system: A regex of a system (NULL to match all systems)
 * @event: A regex of the event in the system (NULL to match all events)
 *
 * This will enable events that match the @system and @event.
 * If both @system and @event are NULL, then it will enable all events.
 * If @system is NULL, it will look at all systems for matching events
 * to @event.
 * If @event is NULL, then it will enable all events in the systems
 * that match @system.
 *
 * Returns 0 on success, and -1 if it encountered an error,
 * or if no events matched. If no events matched, then -1 is set
 * but errno will not be.
 */
int tracefs_event_enable(struct tracefs_instance *instance,
			 const char *system, const char *event)
{
	return event_enable_disable(instance, system, event, true, NULL);
}

int tracefs_event_disable(struct tracefs_instance *instance,
			  const char *system, const char *event)
{
	return event_enable_disable(instance, system, event, false, NULL);
}

/**
 * tracefs_event_is_enabled - return if the event is enabled or not
 * @instance: ftrace instance, can be NULL for the top instance
 * @system: The name of the system to check
 * @event: The name of the event to check
 *
 * Checks is an event or multiple events are enabled.
 *
 * If @system is NULL, then it will check all the systems where @event is
 * a match.
 *
 * If @event is NULL, then it will check all events where @system is a match.
 *
 * If both @system and @event are NULL, then it will check all events
 *
 * Returns TRACEFS_ALL_ENABLED if all matching are enabled.
 * Returns TRACEFS_SOME_ENABLED if some are enabled and some are not
 * Returns TRACEFS_ALL_DISABLED if none of the events are enabled.
 * Returns TRACEFS_ERROR if there is an error reading the events.
 */
enum tracefs_enable_state
tracefs_event_is_enabled(struct tracefs_instance *instance,
			 const char *system, const char *event)
{
	enum event_state state = STATE_INIT;
	int ret;

	ret = event_enable_disable(instance, system, event, false, &state);

	if (ret < 0)
		return TRACEFS_ERROR;

	switch (state) {
	case STATE_ENABLED:
		return TRACEFS_ALL_ENABLED;
	case STATE_DISABLED:
		return TRACEFS_ALL_DISABLED;
	case STATE_MIXED:
		return TRACEFS_SOME_ENABLED;
	default:
		return TRACEFS_ERROR;
	}
}
