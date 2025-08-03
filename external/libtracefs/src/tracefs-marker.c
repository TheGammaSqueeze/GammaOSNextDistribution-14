// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2021, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */

#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <pthread.h>

#include "tracefs.h"
#include "tracefs-local.h"

/* File descriptors for Top level trace markers */
static int ftrace_marker_fd = -1;
static int ftrace_marker_raw_fd = -1;

static inline int *get_marker_fd(struct tracefs_instance *instance, bool raw)
{
	if (raw)
		return instance ? &instance->ftrace_marker_raw_fd : &ftrace_marker_raw_fd;
	return instance ? &instance->ftrace_marker_fd : &ftrace_marker_fd;
}

static int marker_init(struct tracefs_instance *instance, bool raw)
{
	const char *file = raw ? "trace_marker_raw" : "trace_marker";
	pthread_mutex_t *lock = trace_get_lock(instance);
	int *fd = get_marker_fd(instance, raw);
	int ret;

	if (*fd >= 0)
		return 0;

	/*
	 * The mutex is only to hold the integrity of the file descriptor
	 * to prevent opening it more than once, or closing the same
	 * file descriptor more than once. It does not protect against
	 * one thread closing the file descriptor and another thread
	 * writing to it. That is up to the application to prevent
	 * from happening.
	 */
	pthread_mutex_lock(lock);
	/* The file could have been opened since we taken the lock */
	if (*fd < 0)
		*fd = tracefs_instance_file_open(instance, file, O_WRONLY | O_CLOEXEC);

	ret = *fd < 0 ? -1 : 0;
	pthread_mutex_unlock(lock);

	return ret;
}

static void marker_close(struct tracefs_instance *instance, bool raw)
{
	pthread_mutex_t *lock = trace_get_lock(instance);
	int *fd = get_marker_fd(instance, raw);

	pthread_mutex_lock(lock);
	if (*fd >= 0) {
		close(*fd);
		*fd = -1;
	}
	pthread_mutex_unlock(lock);
}

static int marker_write(struct tracefs_instance *instance, bool raw, void *data, int len)
{
	int *fd = get_marker_fd(instance, raw);
	int ret;

	/*
	 * The lock does not need to be taken for writes. As a write
	 * does not modify the file descriptor. It's up to the application
	 * to prevent it from being closed if another thread is doing a write.
	 */
	if (!data || len < 1)
		return -1;
	if (*fd < 0) {
		ret = marker_init(instance, raw);
		if (ret < 0)
			return ret;
	}

	ret = write(*fd, data, len);

	return ret == len ? 0 : -1;
}

/**
 * tracefs_print_init - Open trace marker of selected instance for writing
 * @instance: ftrace instance, can be NULL for top tracing instance.
 *
 * Returns 0 if the trace marker is opened successfully, or -1 in case of an error
 */
int tracefs_print_init(struct tracefs_instance *instance)
{
	return marker_init(instance, false);
}

/**
 * tracefs_vprintf - Write a formatted string in the trace marker
 * @instance: ftrace instance, can be NULL for top tracing instance.
 * @fmt: pritnf formatted string
 * @ap: list of arguments for the formatted string
 *
 * If the trace marker of the desired instance is not open already,
 * this API will open it for writing. It will stay open until
 * tracefs_print_close() is called.
 *
 * Returns 0 if the string is written correctly, or -1 in case of an error
 */
int tracefs_vprintf(struct tracefs_instance *instance, const char *fmt, va_list ap)
{
	char *str = NULL;
	int ret;

	ret = vasprintf(&str, fmt, ap);
	if (ret < 0)
		return ret;
	ret = marker_write(instance, false, str, strlen(str));
	free(str);

	return ret;
}

/**
 * tracefs_printf - Write a formatted string in the trace marker
 * @instance: ftrace instance, can be NULL for top tracing instance.
 * @fmt: pritnf formatted string with variable arguments ...
 *
 * If the trace marker of the desired instance is not open already,
 * this API will open it for writing. It will stay open until
 * tracefs_print_close() is called.
 *
 * Returns 0 if the string is written correctly, or -1 in case of an error
 */
int tracefs_printf(struct tracefs_instance *instance, const char *fmt, ...)
{
	va_list ap;
	int ret;

	va_start(ap, fmt);
	ret = tracefs_vprintf(instance, fmt, ap);
	va_end(ap);

	return ret;
}

/**
 * tracefs_print_close - Close trace marker of selected instance
 * @instance: ftrace instance, can be NULL for top tracing instance.
 *
 * Closes the trace marker, previously opened with any of the other tracefs_print APIs
 */
void tracefs_print_close(struct tracefs_instance *instance)
{
	marker_close(instance, false);
}

/**
 * tracefs_binary_init - Open raw trace marker of selected instance for writing
 * @instance: ftrace instance, can be NULL for top tracing instance.
 *
 * Returns 0 if the raw trace marker is opened successfully, or -1 in case of an error
 */
int tracefs_binary_init(struct tracefs_instance *instance)
{
	return marker_init(instance, true);
}

/**
 * tracefs_binary_write - Write binary data in the raw trace marker
 * @instance: ftrace instance, can be NULL for top tracing instance.
 * @data: binary data, that is going to be written in the trace marker
 * @len: length of the @data
 *
 * If the raw trace marker of the desired instance is not open already,
 * this API will open it for writing. It will stay open until
 * tracefs_binary_close() is called.
 *
 * Returns 0 if the data is written correctly, or -1 in case of an error
 */
int tracefs_binary_write(struct tracefs_instance *instance, void *data, int len)
{
	return marker_write(instance, true, data, len);
}

/**
 * tracefs_binary_close - Close raw trace marker of selected instance
 * @instance: ftrace instance, can be NULL for top tracing instance.
 *
 * Closes the raw trace marker, previously opened with any of the other tracefs_binary APIs
 */
void tracefs_binary_close(struct tracefs_instance *instance)
{
	marker_close(instance, true);
}
