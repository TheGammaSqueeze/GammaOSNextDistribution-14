// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2008, 2009, 2010 Red Hat Inc, Steven Rostedt <srostedt@redhat.com>
 *
 * Updates:
 * Copyright (C) 2021, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */
#include <stdlib.h>
#include <stdarg.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <regex.h>
#include <dirent.h>
#include <limits.h>
#include <pthread.h>
#include <errno.h>

#include "tracefs.h"
#include "tracefs-local.h"

__hidden pthread_mutex_t toplevel_lock = PTHREAD_MUTEX_INITIALIZER;

#define TRACE_CTRL		"tracing_on"
#define TRACE_FILTER		"set_ftrace_filter"
#define TRACE_NOTRACE		"set_ftrace_notrace"
#define TRACE_FILTER_LIST	"available_filter_functions"
#define CUR_TRACER		"current_tracer"

#define TRACERS \
	C(NOP,                  "nop"),			\
	C(CUSTOM,		"CUSTOM"),		\
	C(FUNCTION,             "function"),            \
	C(FUNCTION_GRAPH,       "function_graph"),      \
	C(IRQSOFF,              "irqsoff"),             \
	C(PREEMPTOFF,           "preemptoff"),          \
	C(PREEMPTIRQSOFF,       "preemptirqsoff"),      \
	C(WAKEUP,               "wakeup"),              \
	C(WAKEUP_RT,            "wakeup_rt"),	\
	C(WAKEUP_DL,            "wakeup_dl"),           \
	C(MMIOTRACE,            "mmiotrace"),           \
	C(HWLAT,                "hwlat"),               \
	C(BRANCH,               "branch"),              \
	C(BLOCK,                "block")

#undef C
#define C(a, b) b
const char *tracers[] = { TRACERS };

#undef C
#define C(a, b) TRACEFS_TRACER_##a
const int tracer_enums[] = { TRACERS };

/* File descriptor for Top level set_ftrace_filter  */
static int ftrace_filter_fd = -1;
static int ftrace_notrace_fd = -1;

static const char * const options_map[] = {
	"unknown",
	"annotate",
	"bin",
	"blk_cgname",
	"blk_cgroup",
	"blk_classic",
	"block",
	"context-info",
	"disable_on_free",
	"display-graph",
	"event-fork",
	"funcgraph-abstime",
	"funcgraph-cpu",
	"funcgraph-duration",
	"funcgraph-irqs",
	"funcgraph-overhead",
	"funcgraph-overrun",
	"funcgraph-proc",
	"funcgraph-tail",
	"func_stack_trace",
	"function-fork",
	"function-trace",
	"graph-time",
	"hex",
	"irq-info",
	"latency-format",
	"markers",
	"overwrite",
	"pause-on-trace",
	"printk-msg-only",
	"print-parent",
	"raw",
	"record-cmd",
	"record-tgid",
	"sleep-time",
	"stacktrace",
	"sym-addr",
	"sym-offset",
	"sym-userobj",
	"trace_printk",
	"userstacktrace",
	"verbose" };

static int trace_on_off(int fd, bool on)
{
	const char *val = on ? "1" : "0";
	int ret;

	ret = write(fd, val, 1);
	if (ret == 1)
		return 0;

	return -1;
}

static int trace_on_off_file(struct tracefs_instance *instance, bool on)
{
	int ret;
	int fd;

	fd = tracefs_instance_file_open(instance, TRACE_CTRL, O_WRONLY);
	if (fd < 0)
		return -1;
	ret = trace_on_off(fd, on);
	close(fd);

	return ret;
}

/**
 * tracefs_trace_is_on - Check if writing traces to the ring buffer is enabled
 * @instance: ftrace instance, can be NULL for the top instance
 *
 * Returns -1 in case of an error, 0 if tracing is disable or 1 if tracing
 * is enabled.
 */
int tracefs_trace_is_on(struct tracefs_instance *instance)
{
	long long res;

	if (tracefs_instance_file_read_number(instance, TRACE_CTRL, &res) == 0)
		return (int)res;

	return -1;
}

/**
 * tracefs_trace_on - Enable writing traces to the ring buffer of the given instance
 * @instance: ftrace instance, can be NULL for the top instance
 *
 * Returns -1 in case of an error or 0 otherwise
 */
int tracefs_trace_on(struct tracefs_instance *instance)
{
	return trace_on_off_file(instance, true);
}

/**
 * tracefs_trace_off - Disable writing traces to the ring buffer of the given instance
 * @instance: ftrace instance, can be NULL for the top instance
 *
 * Returns -1 in case of an error or 0 otherwise
 */
int tracefs_trace_off(struct tracefs_instance *instance)
{
	return trace_on_off_file(instance, false);
}

/**
 * tracefs_trace_on_fd - Enable writing traces to the ring buffer
 * @fd: File descriptor to ftrace tracing_on file, previously opened
 *	with tracefs_trace_on_get_fd()
 *
 * Returns -1 in case of an error or 0 otherwise
 */
int tracefs_trace_on_fd(int fd)
{
	if (fd < 0)
		return -1;
	return trace_on_off(fd, true);
}

/**
 * tracefs_trace_off_fd - Disable writing traces to the ring buffer
 * @fd: File descriptor to ftrace tracing_on file, previously opened
 *	with tracefs_trace_on_get_fd()
 *
 * Returns -1 in case of an error or 0 otherwise
 */
int tracefs_trace_off_fd(int fd)
{
	if (fd < 0)
		return -1;
	return trace_on_off(fd, false);
}

/**
 * tracefs_option_name - Get trace option name from id
 * @id: trace option id
 *
 * Returns string with option name, or "unknown" in case of not known option id.
 * The returned string must *not* be freed.
 */
const char *tracefs_option_name(enum tracefs_option_id id)
{
	/* Make sure options map contains all the options */
	BUILD_BUG_ON(ARRAY_SIZE(options_map) != TRACEFS_OPTION_MAX);

	if (id < TRACEFS_OPTION_MAX)
		return options_map[id];

	return options_map[0];
}

/**
 * tracefs_option_id - Get trace option ID from name
 * @name: trace option name
 *
 * Returns trace option ID or TRACEFS_OPTION_INVALID in case of an error or
 * unknown option name.
 */
enum tracefs_option_id tracefs_option_id(const char *name)
{
	int i;

	if (!name)
		return TRACEFS_OPTION_INVALID;

	for (i = 0; i < TRACEFS_OPTION_MAX; i++) {
		if (strlen(name) == strlen(options_map[i]) &&
		    !strcmp(options_map[i], name))
			return i;
	}

	return TRACEFS_OPTION_INVALID;
}

const static struct tracefs_options_mask *
trace_get_options(struct tracefs_instance *instance, bool enabled)
{
	pthread_mutex_t *lock = trace_get_lock(instance);
	struct tracefs_options_mask *bitmask;
	enum tracefs_option_id id;
	unsigned long long set;
	char file[PATH_MAX];
	struct stat st;
	long long val;
	char *path;
	int ret;

	bitmask = enabled ? enabled_opts_mask(instance) :
			   supported_opts_mask(instance);

	for (id = 1; id < TRACEFS_OPTION_MAX; id++) {
		snprintf(file, PATH_MAX, "options/%s", options_map[id]);
		path = tracefs_instance_get_file(instance, file);
		if (!path)
			return NULL;

		set = 1;
		ret = stat(path, &st);
		if (ret < 0 || !S_ISREG(st.st_mode)) {
			set = 0;
		} else if (enabled) {
			ret = tracefs_instance_file_read_number(instance, file, &val);
			if (ret != 0 || val != 1)
				set = 0;
		}

		pthread_mutex_lock(lock);
		bitmask->mask = (bitmask->mask & ~(1ULL << (id - 1))) | (set << (id - 1));
		pthread_mutex_unlock(lock);

		tracefs_put_tracing_file(path);
	}


	return bitmask;
}

/**
 * tracefs_options_get_supported - Get all supported trace options in given instance
 * @instance: ftrace instance, can be NULL for the top instance
 *
 * Returns bitmask structure with all trace options, supported in given instance,
 * or NULL in case of an error.
 */
const struct tracefs_options_mask *
tracefs_options_get_supported(struct tracefs_instance *instance)
{
	return trace_get_options(instance, false);
}

/**
 * tracefs_options_get_enabled - Get all currently enabled trace options in given instance
 * @instance: ftrace instance, can be NULL for the top instance
 *
 * Returns bitmask structure with all trace options, enabled in given instance,
 * or NULL in case of an error.
 */
const struct tracefs_options_mask *
tracefs_options_get_enabled(struct tracefs_instance *instance)
{
	return trace_get_options(instance, true);
}

static int trace_config_option(struct tracefs_instance *instance,
			       enum tracefs_option_id id, bool set)
{
	const char *set_str = set ? "1" : "0";
	char file[PATH_MAX];
	const char *name;

	name = tracefs_option_name(id);
	if (!name)
		return -1;

	snprintf(file, PATH_MAX, "options/%s", name);
	if (strlen(set_str) != tracefs_instance_file_write(instance, file, set_str))
		return -1;
	return 0;
}

/**
 * tracefs_option_enable - Enable trace option
 * @instance: ftrace instance, can be NULL for the top instance
 * @id: trace option id
 *
 * Returns -1 in case of an error or 0 otherwise
 */
int tracefs_option_enable(struct tracefs_instance *instance, enum tracefs_option_id id)
{
	return trace_config_option(instance, id, true);
}

/**
 * tracefs_option_disable - Disable trace option
 * @instance: ftrace instance, can be NULL for the top instance
 * @id: trace option id
 *
 * Returns -1 in case of an error or 0 otherwise
 */
int tracefs_option_disable(struct tracefs_instance *instance, enum tracefs_option_id id)
{
	return trace_config_option(instance, id, false);
}

/**
 * tracefs_option_is_supported - Check if an option is supported
 * @instance: ftrace instance, can be NULL for the top instance
 * @id: trace option id
 *
 * Returns true if an option with given id is supported by the system, false if
 * it is not supported.
 */
bool tracefs_option_is_supported(struct tracefs_instance *instance, enum tracefs_option_id id)
{
	const char *name = tracefs_option_name(id);
	char file[PATH_MAX];

	if (!name)
		return false;
	snprintf(file, PATH_MAX, "options/%s", name);
	return tracefs_file_exists(instance, file);
}

/**
 * tracefs_option_is_enabled - Check if an option is enabled in given instance
 * @instance: ftrace instance, can be NULL for the top instance
 * @id: trace option id
 *
 * Returns true if an option with given id is enabled in the given instance,
 * false if it is not enabled.
 */
bool tracefs_option_is_enabled(struct tracefs_instance *instance, enum tracefs_option_id id)
{
	const char *name = tracefs_option_name(id);
	char file[PATH_MAX];
	long long res;

	if (!name)
		return false;
	snprintf(file, PATH_MAX, "options/%s", name);
	if (!tracefs_instance_file_read_number(instance, file, &res) && res)
		return true;

	return false;
}

/**
 * tracefs_option_mask_is_set - Check if given option is set in the bitmask
 * @options: Options bitmask
 * @id: trace option id
 *
 * Returns true if an option with given id is set in the bitmask,
 * false if it is not set.
 */
bool tracefs_option_mask_is_set(const struct tracefs_options_mask *options,
			   enum tracefs_option_id id)
{
	if (id > TRACEFS_OPTION_INVALID)
		return options->mask & (1ULL << (id - 1));
	return false;
}

struct func_list {
	struct func_list	*next;
	char			*func;
	unsigned int		start;
	unsigned int		end;
};

struct func_filter {
	const char		*filter;
	regex_t			re;
	bool			set;
	bool			is_regex;
};

static bool is_regex(const char *str)
{
	int i;

	for (i = 0; str[i]; i++) {
		switch (str[i]) {
		case 'a' ... 'z':
		case 'A'...'Z':
		case '_':
		case '0'...'9':
		case '*':
		case '.':
			/* Dots can be part of a function name */
		case '?':
			continue;
		default:
			return true;
		}
	}
	return false;
}

static char *update_regex(const char *reg)
{
	int len = strlen(reg);
	char *str;

	if (reg[0] == '^' && reg[len - 1] == '$')
		return strdup(reg);

	str = malloc(len + 3);
	if (reg[0] == '^') {
		strcpy(str, reg);
	} else {
		str[0] = '^';
		strcpy(str + 1, reg);
		len++; /* add ^ */
	}
	if (str[len - 1] != '$')
		str[len++]= '$';
	str[len] = '\0';
	return str;
}

/*
 * Convert a glob into a regular expression.
 */
static char *make_regex(const char *glob)
{
	char *str;
	int cnt = 0;
	int i, j;

	for (i = 0; glob[i]; i++) {
		if (glob[i] == '*'|| glob[i] == '.')
			cnt++;
	}

	/* '^' + ('*'->'.*' or '.' -> '\.') + '$' + '\0' */
	str = malloc(i + cnt + 3);
	if (!str)
		return NULL;

	str[0] = '^';
	for (i = 0, j = 1; glob[i]; i++, j++) {
		if (glob[i] == '*')
			str[j++] = '.';
		/* Dots can be part of a function name */
		if (glob[i] == '.')
			str[j++] = '\\';
		str[j] = glob[i];
	}
	str[j++] = '$';
	str[j] = '\0';
	return str;
}

static bool match(const char *str, struct func_filter *func_filter)
{
	return regexec(&func_filter->re, str, 0, NULL, 0) == 0;
}

/*
 * Return 0 on success, -1 error writing, 1 on other errors.
 */
static int write_filter(int fd, const char *filter, const char *module)
{
	char *each_str = NULL;
	int write_size;
	int size;

	if (module)
		write_size = asprintf(&each_str, "%s:mod:%s ", filter, module);
	else
		write_size = asprintf(&each_str, "%s ", filter);

	if (write_size < 0)
		return 1;

	size = write(fd, each_str, write_size);
	free(each_str);

	/* compare written bytes*/
	if (size < write_size)
		return -1;

	return 0;
}

static int add_func(struct func_list ***next_func_ptr, unsigned int index)
{
	struct func_list **next_func = *next_func_ptr;
	struct func_list *func_list = *next_func;

	if (!func_list) {
		func_list = calloc(1, sizeof(*func_list));
		if (!func_list)
			return -1;
		func_list->start = index;
		func_list->end = index;
		*next_func = func_list;
		return 0;
	}

	if (index == func_list->end + 1) {
		func_list->end = index;
		return 0;
	}
	*next_func_ptr = &func_list->next;
	return add_func(next_func_ptr, index);
}

static int add_func_str(struct func_list ***next_func_ptr, const char *func)
{
	struct func_list **next_func = *next_func_ptr;
	struct func_list *func_list = *next_func;

	if (!func_list) {
		func_list = calloc(1, sizeof(*func_list));
		if (!func_list)
			return -1;
		func_list->func = strdup(func);
		if (!func_list->func)
			return -1;
		*next_func = func_list;
		return 0;
	}
	*next_func_ptr = &func_list->next;
	return add_func_str(next_func_ptr, func);
}

static void free_func_list(struct func_list *func_list)
{
	struct func_list *f;

	while (func_list) {
		f = func_list;
		func_list = f->next;
		free(f->func);
		free(f);
	}
}

enum match_type {
	FILTER_CHECK	= (1 << 0),
	FILTER_WRITE	= (1 << 1),
	FILTER_FUTURE	= (1 << 2),
	SAVE_STRING	= (1 << 2),
};

static int match_filters(int fd, struct func_filter *func_filter,
			 const char *module, struct func_list **func_list,
			 int flags)
{
	enum match_type type = flags & (FILTER_CHECK | FILTER_WRITE);
	bool save_str = flags & SAVE_STRING;
	bool future = flags & FILTER_FUTURE;
	bool mod_match = false;
	char *line = NULL;
	size_t size = 0;
	char *path;
	FILE *fp;
	int index = 0;
	int ret = 1;
	int mlen;

	path = tracefs_get_tracing_file(TRACE_FILTER_LIST);
	if (!path)
		return 1;

	fp = fopen(path, "r");
	tracefs_put_tracing_file(path);

	if (!fp)
		return 1;

	if (module)
		mlen = strlen(module);

	while (getline(&line, &size, fp) >= 0) {
		char *saveptr = NULL;
		char *tok, *mtok;
		int len = strlen(line);

		if (line[len - 1] == '\n')
			line[len - 1] = '\0';
		tok = strtok_r(line, " ", &saveptr);
		if (!tok)
			goto next;

		index++;

		if (module) {
			mtok = strtok_r(NULL, " ", &saveptr);
			if (!mtok)
				goto next;
			if ((strncmp(mtok + 1, module, mlen) != 0) ||
			    (mtok[mlen + 1] != ']'))
				goto next;
			if (future)
				mod_match = true;
		}
		switch (type) {
		case FILTER_CHECK:
			if (match(tok, func_filter)) {
				func_filter->set = true;
				if (save_str)
					ret = add_func_str(&func_list, tok);
				else
					ret = add_func(&func_list, index);
				if (ret)
					goto out;
			}
			break;
		case FILTER_WRITE:
			/* Writes only have one filter */
			if (match(tok, func_filter)) {
				ret = write_filter(fd, tok, module);
				if (ret)
					goto out;
			}
			break;
		default:
			/* Should never happen */
			ret = -1;
			goto out;

		}
	next:
		free(line);
		line = NULL;
		len = 0;
	}
 out:
	free(line);
	fclose(fp);

	/* If there was no matches and future was set, this is a success */
	if (future && !mod_match)
		ret = 0;

	return ret;
}

static int check_available_filters(struct func_filter *func_filter,
				   const char *module,
				   struct func_list **func_list,
				   bool future)
{
	int flags = FILTER_CHECK | (future ? FILTER_FUTURE : 0);

	return match_filters(-1, func_filter, module, func_list, flags);
}


static int list_available_filters(struct func_filter *func_filter,
				   const char *module,
				   struct func_list **func_list)
{
	int flags = FILTER_CHECK | SAVE_STRING;

	return match_filters(-1, func_filter, module, func_list, flags);
}

static int set_regex_filter(int fd, struct func_filter *func_filter,
			    const char *module)
{
	return match_filters(fd, func_filter, module, NULL, FILTER_WRITE);
}

static int controlled_write(int fd, struct func_filter *func_filter,
			    const char *module)
{
	const char *filter = func_filter->filter;
	int ret;

	if (func_filter->is_regex)
		ret = set_regex_filter(fd, func_filter, module);
	else
		ret = write_filter(fd, filter, module);

	return ret;
}

static int init_func_filter(struct func_filter *func_filter, const char *filter)
{
	char *str;
	int ret;

	if (!(func_filter->is_regex = is_regex(filter)))
		str = make_regex(filter);
	else
		str = update_regex(filter);

	if (!str)
		return -1;

	ret = regcomp(&func_filter->re, str, REG_ICASE|REG_NOSUB);
	free(str);

	if (ret < 0)
		return -1;

	func_filter->filter = filter;
	return 0;
}

static int write_number(int fd, unsigned int start, unsigned int end)
{
	char buf[64];
	unsigned int i;
	int n, ret;

	for (i = start; i <= end; i++) {
		n = snprintf(buf, 64, "%d ", i);
		ret = write(fd, buf, n);
		if (ret < 0)
			return ret;
	}

	return 0;
}

/*
 * This will try to write the first number, if that fails, it
 * will assume that it is not supported and return 1.
 * If the first write succeeds, but a following write fails, then
 * the kernel does support this, but something else went wrong,
 * in this case, return -1.
 */
static int write_func_list(int fd, struct func_list *list)
{
	int ret;

	if (!list)
		return 0;

	ret = write_number(fd, list->start, list->end);
	if (ret)
		return 1; // try a different way
	list = list->next;
	while (list) {
		ret = write_number(fd, list->start, list->end);
		if (ret)
			return -1;
		list = list->next;
	}
	return 0;
}

static int update_filter(const char *filter_path, int *fd,
			 struct tracefs_instance *instance, const char *filter,
			 const char *module, unsigned int flags)
{
	struct func_filter func_filter;
	struct func_list *func_list = NULL;
	bool reset = flags & TRACEFS_FL_RESET;
	bool cont = flags & TRACEFS_FL_CONTINUE;
	bool future = flags & TRACEFS_FL_FUTURE;
	pthread_mutex_t *lock = trace_get_lock(instance);
	int open_flags;
	int ret = 1;

	/* future flag is only applicable to modules */
	if (future && !module) {
		errno = EINVAL;
		return 1;
	}

	pthread_mutex_lock(lock);

	/* RESET is only allowed if the file is not opened yet */
	if (reset && *fd >= 0) {
		errno = EBUSY;
		ret = -1;
		goto out;
	}

	/*
	 * Set EINVAL on no matching filter. But errno may still be modified
	 * on another type of failure (allocation or opening a file).
	 */
	errno = EINVAL;

	/* module set with NULL filter means to enable all functions in a module */
	if (module && !filter)
		filter = "*";

	if (!filter) {
		/* OK to call without filters if this is closing the opened file */
		if (!cont && *fd >= 0) {
			errno = 0;
			ret = 0;
			close(*fd);
			*fd = -1;
		}
		/* Also OK to call if reset flag is set */
		if (reset)
			goto open_file;

		goto out;
	}

	if (init_func_filter(&func_filter, filter) < 0)
		goto out;

	ret = check_available_filters(&func_filter, module, &func_list, future);
	if (ret)
		goto out_free;

 open_file:
	ret = 1;

	open_flags = reset ? O_TRUNC : O_APPEND;

	if (*fd < 0)
		*fd = open(filter_path, O_WRONLY | O_CLOEXEC | open_flags);
	if (*fd < 0)
		goto out_free;

	errno = 0;
	ret = 0;

	if (filter) {
		/*
		 * If future is set, and no functions were found, then
		 * set it directly.
		 */
		if (func_list)
			ret = write_func_list(*fd, func_list);
		else
			ret = 1;
		if (ret > 0)
			ret = controlled_write(*fd, &func_filter, module);
	}

	if (!cont) {
		close(*fd);
		*fd = -1;
	}

 out_free:
	if (filter)
		regfree(&func_filter.re);
	free_func_list(func_list);
 out:
	pthread_mutex_unlock(lock);

	return ret;
}

/**
 * tracefs_function_filter - filter the functions that are traced
 * @instance: ftrace instance, can be NULL for top tracing instance.
 * @filter: The filter to filter what functions are to be traced
 * @module: Module to be traced or NULL if all functions are to be examined.
 * @flags: flags on modifying the filter file
 *
 * @filter may be a full function name, a glob, or a regex. It will be
 * considered a regex, if there's any characters that are not normally in
 * function names or "*" or "?" for a glob.
 *
 * @flags:
 *   TRACEFS_FL_RESET - will clear the functions in the filter file
 *          before applying the @filter. This will error with -1
 *          and errno of EBUSY if this flag is set and a previous
 *          call had the same instance and TRACEFS_FL_CONTINUE set.
 *   TRACEFS_FL_CONTINUE - will keep the filter file open on return.
 *          The filter is updated on closing of the filter file.
 *          With this flag set, the file is not closed, and more filters
 *          may be added before they take effect. The last call of this
 *          function must be called without this flag for the filter
 *          to take effect.
 *   TRACEFS_FL_FUTURE - only applicable if "module" is set. If no match
 *          is made, and the module is not yet loaded, it will still attempt
 *          to write the filter plus the module; "<filter>:mod:<module>"
 *          to the filter file. Starting with Linux kernels 4.13, it is possible
 *          to load the filter file with module functions for a module that
 *          is not yet loaded, and when the module is loaded, it will then
 *          activate the module.
 *
 * Returns 0 on success, 1 if there was an error but the filtering has not
 *  yet started, -1 if there was an error but the filtering has started.
 *  If -1 is returned and TRACEFS_FL_CONTINUE was set, then this function
 *  needs to be called again without the TRACEFS_FL_CONTINUE flag to commit
 *  the changes and close the filter file.
 */
int tracefs_function_filter(struct tracefs_instance *instance, const char *filter,
			    const char *module, unsigned int flags)
{
	char *filter_path;
	int *fd;
	int ret;

	filter_path = tracefs_instance_get_file(instance, TRACE_FILTER);
	if (!filter_path)
		return -1;

	if (instance)
		fd = &instance->ftrace_filter_fd;
	else
		fd = &ftrace_filter_fd;

	ret = update_filter(filter_path, fd, instance, filter, module, flags);
	tracefs_put_tracing_file(filter_path);
	return ret;
}

/**
 * tracefs_function_notrace - filter the functions that are not to be traced
 * @instance: ftrace instance, can be NULL for top tracing instance.
 * @filter: The filter to filter what functions are not to be traced
 * @module: Module to be traced or NULL if all functions are to be examined.
 * @flags: flags on modifying the filter file
 *
 * See tracefs_function_filter, as this has the same functionality but
 * for adding to the "notrace" filter.
 */
int tracefs_function_notrace(struct tracefs_instance *instance, const char *filter,
			     const char *module, unsigned int flags)
{
	char *filter_path;
	int *fd;
	int ret;

	filter_path = tracefs_instance_get_file(instance, TRACE_NOTRACE);
	if (!filter_path)
		return -1;

	if (instance)
		fd = &instance->ftrace_notrace_fd;
	else
		fd = &ftrace_notrace_fd;

	ret = update_filter(filter_path, fd, instance, filter, module, flags);
	tracefs_put_tracing_file(filter_path);
	return ret;
}

int write_tracer(int fd, const char *tracer)
{
	int ret;

	ret = write(fd, tracer, strlen(tracer));
	if (ret < strlen(tracer))
		return -1;
	return ret;
}

/**
 * tracefs_set_tracer - function to set the tracer
 * @instance: ftrace instance, can be NULL for top tracing instance
 * @tracer: The tracer enum that defines the tracer to be set
 * @t: A tracer name if TRACEFS_TRACER_CUSTOM is passed in for @tracer
 *
 * Set the tracer for the instance based on the tracefs_tracer enums.
 * If the user wishes to enable a tracer that is not defined by
 * the enum (new or custom kernel), the tracer can be set to
 * TRACEFS_TRACER_CUSTOM, and pass in a const char * name for
 * the tracer to set.
 *
 * Returns 0 on succes, negative on error.
 */

int tracefs_tracer_set(struct tracefs_instance *instance,
		       enum tracefs_tracers tracer, ...)
{
	char *tracer_path = NULL;
	const char *t = NULL;
	int ret = -1;
	int fd = -1;
	int i;

	if (tracer < 0 || tracer > ARRAY_SIZE(tracers)) {
		errno = EINVAL;
		return -1;
	}

	tracer_path = tracefs_instance_get_file(instance, CUR_TRACER);
	if (!tracer_path)
		return -1;

	fd = open(tracer_path, O_WRONLY);
	if (fd < 0) {
		errno = ENOENT;
		goto out;
	}

	if (tracer == TRACEFS_TRACER_CUSTOM) {
		va_list ap;

		va_start(ap, tracer);
		t = va_arg(ap, const char *);
		va_end(ap);
	} else if (tracer == tracer_enums[tracer]) {
		t = tracers[tracer];
	} else {
		for (i = 0; i < ARRAY_SIZE(tracer_enums); i++) {
			if (tracer == tracer_enums[i]) {
				t = tracers[i];
				break;
			}
		}
	}
	if (!t) {
		errno = EINVAL;
		goto out;
	}
	ret = write_tracer(fd, t);
	/*
	 * If the tracer does not exist, EINVAL is returned,
	 * but let the user know this as ENODEV.
	 */
	if (ret < 0 && errno == EINVAL)
		errno = ENODEV;
 out:
	tracefs_put_tracing_file(tracer_path);
	close(fd);
	return ret > 0 ? 0 : ret;
}

int  tracefs_tracer_clear(struct tracefs_instance *instance)
{
	return tracefs_tracer_set(instance, TRACEFS_TRACER_NOP);
}

static bool splice_safe(int fd, int pfd)
{
	int ret;

	errno = 0;
	ret = splice(pfd, NULL, fd, NULL,
		     10, SPLICE_F_NONBLOCK | SPLICE_F_MOVE);

	return !ret || (ret < 0 && errno == EAGAIN);
}

static ssize_t read_trace_pipe(bool *keep_going, int in_fd, int out_fd)
{
	char buf[BUFSIZ];
	ssize_t bread = 0;
	int ret;

	while (*(volatile bool *)keep_going) {
		int r;
		ret = read(in_fd, buf, BUFSIZ);
		if (ret <= 0)
			break;
		r = ret;
		ret = write(out_fd, buf, r);
		if (ret < 0)
			break;
		bread += ret;
		/*
		 * If the write does a partial write, then
		 * the iteration should stop. This can happen if
		 * the destination file system ran out of disk space.
		 * Sure, it probably lost a little from the read
		 * but there's not much more that can be
		 * done. Just return what was transferred.
		 */
		if (ret < r)
			break;
	}

	if (ret < 0 && (errno == EAGAIN || errno == EINTR))
		ret = 0;

	return ret < 0 ? ret : bread;
}

static bool top_pipe_keep_going;

/**
 * tracefs_trace_pipe_stream - redirect the stream of trace data to an output
 * file. The "splice" system call is used to moves the data without copying
 * between kernel address space and user address space. The user can interrupt
 * the streaming of the data by pressing Ctrl-c.
 * @fd: The file descriptor of the output file.
 * @instance: ftrace instance, can be NULL for top tracing instance.
 * @flags: flags for opening the trace_pipe file.
 *
 * Returns -1 in case of an error or number of bytes transferred otherwise.
 */
ssize_t tracefs_trace_pipe_stream(int fd, struct tracefs_instance *instance,
				 int flags)
{
	bool *keep_going = instance ? &instance->pipe_keep_going :
				      &top_pipe_keep_going;
	const char *file = "trace_pipe";
	int brass[2], in_fd, ret = -1;
	int sflags = flags & O_NONBLOCK ? SPLICE_F_NONBLOCK : 0;
	off_t data_size;
	ssize_t bread = 0;

	(*(volatile bool *)keep_going) = true;

	in_fd = tracefs_instance_file_open(instance, file, O_RDONLY | flags);
	if (in_fd < 0) {
		tracefs_warning("Failed to open 'trace_pipe'.");
		return ret;
	}

	if(pipe(brass) < 0) {
		tracefs_warning("Failed to open pipe.");
		goto close_file;
	}

	data_size = fcntl(brass[0], F_GETPIPE_SZ);
	if (data_size <= 0) {
		tracefs_warning("Failed to open pipe (size=0).");
		goto close_all;
	}

	/* Test if the output is splice safe */
	if (!splice_safe(fd, brass[0])) {
		bread = read_trace_pipe(keep_going, in_fd, fd);
		ret = 0; /* Force return of bread */
		goto close_all;
	}

	errno = 0;

	while (*(volatile bool *)keep_going) {
		ret = splice(in_fd, NULL,
			     brass[1], NULL,
			     data_size, sflags);
		if (ret < 0)
			break;

		ret = splice(brass[0], NULL,
			     fd, NULL,
			     data_size, sflags);
		if (ret < 0)
			break;
		bread += ret;
	}

	/*
	 * Do not return error in the case when the "splice" system call
	 * was interrupted by the user (pressing Ctrl-c).
	 * Or if NONBLOCK was specified.
	 */
	if (!keep_going || errno == EAGAIN || errno == EINTR)
		ret = 0;

 close_all:
	close(brass[0]);
	close(brass[1]);
 close_file:
	close(in_fd);

	return ret ? ret : bread;
}

/**
 * tracefs_trace_pipe_print - redirect the stream of trace data to "stdout".
 * The "splice" system call is used to moves the data without copying
 * between kernel address space and user address space.
 * @instance: ftrace instance, can be NULL for top tracing instance.
 * @flags: flags for opening the trace_pipe file.
 *
 * Returns -1 in case of an error or number of bytes transferred otherwise.
 */

ssize_t tracefs_trace_pipe_print(struct tracefs_instance *instance, int flags)
{
	return tracefs_trace_pipe_stream(STDOUT_FILENO, instance, flags);
}

/**
 * tracefs_trace_pipe_stop - stop the streaming of trace data.
 * @instance: ftrace instance, can be NULL for top tracing instance.
 */
void tracefs_trace_pipe_stop(struct tracefs_instance *instance)
{
	if (instance)
		instance->pipe_keep_going = false;
	else
		top_pipe_keep_going = false;
}

/**
 * tracefs_filter_functions - return a list of available functons that can be filtered
 * @filter: The filter to filter what functions to list (can be NULL for all)
 * @module: Module to be traced or NULL if all functions are to be examined.
 * @list: The list to return the list from (freed by tracefs_list_free() on success)
 *
 * Returns a list of function names that match @filter and @module. If both
 * @filter and @module is NULL, then all available functions that can be filtered
 * will be returned. (Note, there can be duplicates, if there are more than
 * one function with the same name.
 *
 * On success, zero is returned, and @list contains a list of functions that were
 * found, and must be freed with tracefs_list_free().
 * On failure, a negative number is returned, and @list is ignored.
 */
int tracefs_filter_functions(const char *filter, const char *module, char ***list)
{
	struct func_filter func_filter;
	struct func_list *func_list = NULL, *f;
	char **funcs = NULL;
	int ret;

	if (!filter)
		filter = ".*";

	ret = init_func_filter(&func_filter, filter);
	if (ret < 0)
		return ret;

	ret = list_available_filters(&func_filter, module, &func_list);
	if (ret < 0)
		goto out;

	ret = -1;
	for (f = func_list; f; f = f->next) {
		char **tmp;

		tmp = tracefs_list_add(funcs, f->func);
		if (!tmp) {
			tracefs_list_free(funcs);
			goto out;
		}
		funcs = tmp;
	}

	*list = funcs;
	ret = 0;
out:
	regfree(&func_filter.re);
	free_func_list(func_list);
	return ret;
}
