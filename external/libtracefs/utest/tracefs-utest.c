// SPDX-License-Identifier: LGPL-2.1
/*
 * Copyright (C) 2020, VMware, Tzvetomir Stoyanov <tz.stoyanov@gmail.com>
 *
 */
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <dirent.h>
#include <ftw.h>
#include <libgen.h>
#include <kbuffer.h>
#include <pthread.h>

#include <sys/mount.h>

#include <CUnit/CUnit.h>
#include <CUnit/Basic.h>

#include "tracefs.h"

#define TRACEFS_SUITE		"tracefs library"
#define TEST_INSTANCE_NAME	"cunit_test_iter"
#define TEST_TRACE_DIR		"/tmp/trace_utest.XXXXXX"
#define TEST_ARRAY_SIZE		5000

#define ALL_TRACERS	"available_tracers"
#define CUR_TRACER	"current_tracer"
#define PER_CPU		"per_cpu"
#define TRACE_ON	"tracing_on"
#define TRACE_CLOCK	"trace_clock"

#define SQL_1_EVENT	"wakeup_1"
#define SQL_1_SQL	"select sched_switch.next_pid as woke_pid, sched_waking.common_pid as waking_pid from sched_waking join sched_switch on sched_switch.next_pid = sched_waking.pid"

#define SQL_2_EVENT	"wakeup_2"
#define SQL_2_SQL	"select woke.next_pid as woke_pid, wake.common_pid as waking_pid from sched_waking as wake join sched_switch as woke on woke.next_pid = wake.pid"

#define SQL_3_EVENT	"wakeup_lat"
#define SQL_3_SQL	"select sched_switch.next_prio as prio, end.prev_prio as pprio, (sched.sched_waking.common_timestamp.usecs - end.TIMESTAMP_USECS) as lat from sched_waking as start join sched_switch as end on start.pid = end.next_pid"

#define SQL_4_EVENT	"wakeup_lat_2"
#define SQL_4_SQL	"select start.pid, end.next_prio as prio, (end.TIMESTAMP_USECS - start.TIMESTAMP_USECS) as lat from sched_waking as start join sched_switch as end on start.pid = end.next_pid where (start.prio >= 1 && start.prio < 100) || !(start.pid >= 0 && start.pid <= 1) && end.prev_pid != 0"

#define SQL_5_EVENT	"irq_lat"
#define SQL_5_SQL	"select end.common_pid as pid, (end.common_timestamp.usecs - start.common_timestamp.usecs) as irq_lat from irq_disable as start join irq_enable as end on start.common_pid = end.common_pid, start.parent_offs == end.parent_offs where start.common_pid != 0"
#define SQL_5_START	"irq_disable"

#define DEBUGFS_DEFAULT_PATH "/sys/kernel/debug"
#define TRACEFS_DEFAULT_PATH "/sys/kernel/tracing"
#define TRACEFS_DEFAULT2_PATH "/sys/kernel/debug/tracing"

static struct tracefs_instance *test_instance;
static struct tep_handle *test_tep;
struct test_sample {
	int cpu;
	int value;
};
static struct test_sample test_array[TEST_ARRAY_SIZE];
static int test_found;
static unsigned long long last_ts;

static int test_callback(struct tep_event *event, struct tep_record *record,
			  int cpu, void *context)
{
	struct tep_format_field *field;
	struct test_sample *sample;
	int *cpu_test = (int *)context;
	int i;

	CU_TEST(last_ts <= record->ts);
	last_ts = record->ts;

	if (cpu_test && *cpu_test >= 0) {
		CU_TEST(*cpu_test == cpu);
	}
	CU_TEST(cpu == record->cpu);

	field = tep_find_field(event, "buf");
	if (field) {
		sample = ((struct test_sample *)(record->data + field->offset));
		for (i = 0; i < TEST_ARRAY_SIZE; i++) {
			if (test_array[i].value == sample->value &&
			    test_array[i].cpu == cpu) {
				test_array[i].value = 0;
				test_found++;
				break;
			}
		}
	}

	return 0;
}

static cpu_set_t *cpuset_save;
static cpu_set_t *cpuset;
static int cpu_size;

static void save_affinity(void)
{
	int cpus;

	cpus = sysconf(_SC_NPROCESSORS_CONF);
	cpuset_save = CPU_ALLOC(cpus);
	cpuset = CPU_ALLOC(cpus);
	cpu_size = CPU_ALLOC_SIZE(cpus);
	CU_TEST(cpuset_save != NULL && cpuset != NULL);
	CU_TEST(sched_getaffinity(0, cpu_size, cpuset_save) == 0);
}

static void thread_affinity(void)
{
	sched_setaffinity(0, cpu_size, cpuset_save);
}

static void reset_affinity(void)
{
	sched_setaffinity(0, cpu_size, cpuset_save);
	CPU_FREE(cpuset_save);
	CPU_FREE(cpuset);
}

static void set_affinity(int cpu)
{
	CPU_ZERO_S(cpu_size, cpuset);
	CPU_SET_S(cpu, cpu_size, cpuset);
	CU_TEST(sched_setaffinity(0, cpu_size, cpuset) == 0);
	sched_yield(); /* Force schedule */
}

static void test_iter_write(struct tracefs_instance *instance)
{
	char *path;
	int i, fd;
	int cpus;
	int ret;

	cpus = sysconf(_SC_NPROCESSORS_CONF);
	save_affinity();

	path = tracefs_instance_get_file(instance, "trace_marker");
	CU_TEST(path != NULL);
	fd = open(path, O_WRONLY);
	tracefs_put_tracing_file(path);
	CU_TEST(fd >= 0);

	for (i = 0; i < TEST_ARRAY_SIZE; i++) {
		test_array[i].cpu = rand() % cpus;
		test_array[i].value = random();
		if (!test_array[i].value)
			test_array[i].value++;
		CU_TEST(test_array[i].cpu < cpus);
		set_affinity(test_array[i].cpu);
		ret = write(fd, test_array + i, sizeof(struct test_sample));
		CU_TEST(ret == sizeof(struct test_sample));
	}

	reset_affinity();
	close(fd);
}


static void iter_raw_events_on_cpu(struct tracefs_instance *instance, int cpu)
{
	int cpus = sysconf(_SC_NPROCESSORS_CONF);
	cpu_set_t *cpuset = NULL;
	int cpu_size = 0;
	int check = 0;
	int ret;
	int i;

	if (cpu >= 0) {
		cpuset = CPU_ALLOC(cpus);
		cpu_size = CPU_ALLOC_SIZE(cpus);
		CPU_ZERO_S(cpu_size, cpuset);
		CPU_SET(cpu, cpuset);
	}
	test_found = 0;
	last_ts = 0;
	test_iter_write(instance);
	ret = tracefs_iterate_raw_events(test_tep, instance, cpuset, cpu_size,
					 test_callback, &cpu);
	CU_TEST(ret == 0);
	if (cpu < 0) {
		CU_TEST(test_found == TEST_ARRAY_SIZE);
	} else {
		for (i = 0; i < TEST_ARRAY_SIZE; i++) {
			if (test_array[i].cpu == cpu) {
				check++;
				CU_TEST(test_array[i].value == 0)
			} else {
				CU_TEST(test_array[i].value != 0)
			}
		}
		CU_TEST(test_found == check);
	}

	if (cpuset)
		CPU_FREE(cpuset);
}

static void test_instance_iter_raw_events(struct tracefs_instance *instance)
{
	int cpus = sysconf(_SC_NPROCESSORS_CONF);
	int ret;
	int i;

	ret = tracefs_iterate_raw_events(NULL, instance, NULL, 0, test_callback, NULL);
	CU_TEST(ret < 0);
	last_ts = 0;
	ret = tracefs_iterate_raw_events(test_tep, NULL, NULL, 0, test_callback, NULL);
	CU_TEST(ret == 0);
	ret = tracefs_iterate_raw_events(test_tep, instance, NULL, 0, NULL, NULL);
	CU_TEST(ret < 0);

	iter_raw_events_on_cpu(instance, -1);
	for (i = 0; i < cpus; i++)
		iter_raw_events_on_cpu(instance, i);
}

static void test_iter_raw_events(void)
{
	test_instance_iter_raw_events(test_instance);
}

#define RAND_STR_SIZE 20
#define RAND_ASCII "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
static const char *get_rand_str(void)
{
	static char str[RAND_STR_SIZE];
	static char sym[] = RAND_ASCII;
	struct timespec clk;
	int i;

	clock_gettime(CLOCK_REALTIME, &clk);
	srand(clk.tv_nsec);
	for (i = 0; i < RAND_STR_SIZE; i++)
		str[i] = sym[rand() % (sizeof(sym) - 1)];

	str[RAND_STR_SIZE - 1] = 0;
	return str;
}

struct marker_find {
	int data_offset;
	int event_id;
	int count;
	int len;
	void *data;
};

static int test_marker_callback(struct tep_event *event, struct tep_record *record,
				int cpu, void *context)
{
	struct marker_find *walk = context;

	if (!walk)
		return -1;
	if (event->id != walk->event_id)
		return 0;
	if (record->size < (walk->data_offset + walk->len))
		return 0;

	if (memcmp(walk->data, record->data + walk->data_offset, walk->len) == 0)
		walk->count++;

	return 0;
}

static bool find_test_marker(struct tracefs_instance *instance,
			     void *data, int len, int expected, bool raw)
{
	struct tep_format_field *field;
	struct tep_event *event;
	struct marker_find walk;
	int ret;

	if (raw) {
		event = tep_find_event_by_name(test_tep, "ftrace", "raw_data");
		if (event)
			field = tep_find_field(event, "id");

	} else {
		event = tep_find_event_by_name(test_tep, "ftrace", "print");
		if (event)
			field = tep_find_field(event, "buf");
	}

	if (!event || !field)
		return false;

	walk.data = data;
	walk.len = len;
	walk.count = 0;
	walk.event_id = event->id;
	walk.data_offset = field->offset;
	ret = tracefs_iterate_raw_events(test_tep, instance, NULL, 0,
					 test_marker_callback, &walk);
	CU_TEST(ret == 0);

	return walk.count == expected;
}

static int marker_vprint(struct tracefs_instance *instance, char *fmt, ...)
{
	va_list ap;
	int ret;

	va_start(ap, fmt);
	ret = tracefs_vprintf(instance, fmt, ap);
	va_end(ap);

	return ret;
}

#define MARKERS_WRITE_COUNT	100
static void test_instance_ftrace_marker(struct tracefs_instance *instance)
{
	const char *string = get_rand_str();
	unsigned int data = 0xdeadbeef;
	char *str;
	int i;

	CU_TEST(tracefs_print_init(instance) == 0);
	tracefs_print_close(instance);

	CU_TEST(tracefs_binary_init(instance) == 0);
	tracefs_binary_close(instance);

	for (i = 0; i < MARKERS_WRITE_COUNT; i++) {
		CU_TEST(tracefs_binary_write(instance, &data, sizeof(data)) == 0);
	}
	CU_TEST(find_test_marker(instance, &data, sizeof(data), MARKERS_WRITE_COUNT, true));

	for (i = 0; i < MARKERS_WRITE_COUNT; i++) {
		CU_TEST(tracefs_printf(instance, "Test marker: %s 0x%X", string, data) == 0);
	}
	asprintf(&str, "Test marker: %s 0x%X", string, data);
	CU_TEST(find_test_marker(instance, str, strlen(str), MARKERS_WRITE_COUNT, false));
	free(str);

	for (i = 0; i < MARKERS_WRITE_COUNT; i++) {
		CU_TEST(marker_vprint(instance, "Test marker V: %s 0x%X", string, data) == 0);
	}
	asprintf(&str, "Test marker V: %s 0x%X", string, data);
	CU_TEST(find_test_marker(instance, str, strlen(str), MARKERS_WRITE_COUNT, false));
	free(str);

	tracefs_print_close(instance);
	tracefs_binary_close(instance);
}

static void test_ftrace_marker(void)
{
	test_instance_ftrace_marker(test_instance);
}

static void test_instance_trace_sql(struct tracefs_instance *instance)
{
	struct tracefs_synth *synth;
	struct trace_seq seq;
	struct tep_handle *tep;
	struct tep_event *event;
	int ret;

	tep = tracefs_local_events(NULL);
	CU_TEST(tep != NULL);

	trace_seq_init(&seq);

	synth = tracefs_sql(tep, SQL_1_EVENT, SQL_1_SQL, NULL);
	CU_TEST(synth != NULL);
	ret = tracefs_synth_echo_cmd(&seq, synth);
	CU_TEST(ret == 0);
	tracefs_synth_free(synth);
	trace_seq_reset(&seq);

	synth = tracefs_sql(tep, SQL_2_EVENT, SQL_2_SQL, NULL);
	CU_TEST(synth != NULL);
	ret = tracefs_synth_echo_cmd(&seq, synth);
	CU_TEST(ret == 0);
	tracefs_synth_free(synth);
	trace_seq_reset(&seq);

	synth = tracefs_sql(tep, SQL_3_EVENT, SQL_3_SQL, NULL);
	CU_TEST(synth != NULL);
	ret = tracefs_synth_echo_cmd(&seq, synth);
	CU_TEST(ret == 0);
	tracefs_synth_free(synth);
	trace_seq_reset(&seq);

	synth = tracefs_sql(tep, SQL_4_EVENT, SQL_4_SQL, NULL);
	CU_TEST(synth != NULL);
	ret = tracefs_synth_echo_cmd(&seq, synth);
	CU_TEST(ret == 0);
	tracefs_synth_free(synth);
	trace_seq_reset(&seq);

	event = tep_find_event_by_name(tep, NULL, SQL_5_START);
	if (event) {
		synth = tracefs_sql(tep, SQL_5_EVENT, SQL_5_SQL, NULL);
		CU_TEST(synth != NULL);
		ret = tracefs_synth_echo_cmd(&seq, synth);
		CU_TEST(ret == 0);
		tracefs_synth_free(synth);
		trace_seq_reset(&seq);
	}

	tep_free(tep);
	trace_seq_destroy(&seq);
}

static void test_trace_sql(void)
{
	test_instance_trace_sql(test_instance);
}

struct test_cpu_data {
	struct tracefs_instance		*instance;
	struct tracefs_cpu		*tcpu;
	struct kbuffer			*kbuf;
	struct tep_handle		*tep;
	unsigned long long		missed_events;
	void				*buf;
	int				events_per_buf;
	int				bufsize;
	int				data_size;
	int				this_pid;
	int				fd;
	bool				done;
};

static void cleanup_trace_cpu(struct test_cpu_data *data)
{
	close(data->fd);
	tep_free(data->tep);
	tracefs_cpu_close(data->tcpu);
	free(data->buf);
	kbuffer_free(data->kbuf);
}

#define EVENT_SYSTEM "syscalls"
#define EVENT_NAME  "sys_enter_getppid"

static int setup_trace_cpu(struct tracefs_instance *instance, struct test_cpu_data *data)
{
	struct tep_format_field **fields;
	struct tep_event *event;
	char tmpfile[] = "/tmp/utest-libtracefsXXXXXX";
	int max = 0;
	int ret;
	int i;

	/* Make sure tracing is on */
	tracefs_trace_on(instance);

	memset (data, 0, sizeof(*data));

	data->instance = instance;

	data->fd = mkstemp(tmpfile);
	CU_TEST(data->fd >= 0);
	unlink(tmpfile);
	if (data->fd < 0)
		return -1;

	data->tep = tracefs_local_events(NULL);
	CU_TEST(data->tep != NULL);
	if (!data->tep)
		goto fail;

	data->tcpu = tracefs_cpu_open(instance, 0, true);
	CU_TEST(data->tcpu != NULL);
	if (!data->tcpu)
		goto fail;

	data->bufsize = tracefs_cpu_read_size(data->tcpu);

	data->buf = calloc(1, data->bufsize);
	CU_TEST(data->buf != NULL);
	if (!data->buf)
		goto fail;

	data->kbuf = kbuffer_alloc(sizeof(long) == 8, !tep_is_bigendian());
	CU_TEST(data->kbuf != NULL);
	if (!data->kbuf)
		goto fail;

	data->data_size = data->bufsize - kbuffer_start_of_data(data->kbuf);

	tracefs_instance_file_clear(instance, "trace");

	event = tep_find_event_by_name(data->tep, EVENT_SYSTEM, EVENT_NAME);
	CU_TEST(event != NULL);
	if (!event)
		goto fail;

	fields = tep_event_fields(event);
	CU_TEST(fields != NULL);
	if (!fields)
		goto fail;

	for (i = 0; fields[i]; i++) {
		int end = fields[i]->offset + fields[i]->size;
		if (end > max)
			max = end;
	}
	free(fields);

	CU_TEST(max != 0);
	if (!max)
		goto fail;

	data->events_per_buf = data->data_size / max;

	data->this_pid = getpid();
	ret = tracefs_event_enable(instance, EVENT_SYSTEM, EVENT_NAME);
	CU_TEST(ret == 0);
	if (ret)
		goto fail;


	save_affinity();
	set_affinity(0);

	return 0;
 fail:
	cleanup_trace_cpu(data);
	return -1;
}

static void shutdown_trace_cpu(struct test_cpu_data *data)
{
	struct tracefs_instance *instance = data->instance;
	int ret;

	reset_affinity();

	ret = tracefs_event_disable(instance, EVENT_SYSTEM, EVENT_NAME);
	CU_TEST(ret == 0);

	cleanup_trace_cpu(data);
}

static void call_getppid(int cnt)
{
	int i;

	for (i = 0; i < cnt; i++)
		getppid();
}

static void test_cpu_read(struct test_cpu_data *data, int expect)
{
	struct tracefs_cpu *tcpu = data->tcpu;
	struct kbuffer *kbuf = data->kbuf;
	struct tep_record record;
	void *buf = data->buf;
	unsigned long long ts;
	bool first = true;
	int pid;
	int ret;
	int cnt = 0;

	call_getppid(expect);

	for (;;) {
		ret = tracefs_cpu_read(tcpu, buf, false);
		CU_TEST(ret > 0 || !first);
		if (ret <= 0)
			break;
		first = false;
		ret = kbuffer_load_subbuffer(kbuf, buf);
		CU_TEST(ret == 0);
		for (;;) {
			record.data = kbuffer_read_event(kbuf, &ts);
			if (!record.data)
				break;
			record.ts = ts;
			pid = tep_data_pid(data->tep, &record);
			if (pid == data->this_pid)
				cnt++;
			kbuffer_next_event(kbuf, NULL);
		}
	}
	CU_TEST(cnt == expect);
}

static void test_instance_trace_cpu_read(struct tracefs_instance *instance)
{
	struct test_cpu_data data;

	if (setup_trace_cpu(instance, &data))
		return;

	test_cpu_read(&data, 1);
	test_cpu_read(&data, data.events_per_buf / 2);
	test_cpu_read(&data, data.events_per_buf);
	test_cpu_read(&data, data.events_per_buf + 1);
	test_cpu_read(&data, data.events_per_buf * 50);

	shutdown_trace_cpu(&data);
}

static void test_trace_cpu_read(void)
{
	test_instance_trace_cpu_read(NULL);
	test_instance_trace_cpu_read(test_instance);
}

struct follow_data {
	struct tep_event *sched_switch;
	struct tep_event *sched_waking;
	struct tep_event *function;
	int missed;
};

static int switch_callback(struct tep_event *event, struct tep_record *record,
			   int cpu, void *data)
{
	struct follow_data *fdata = data;

	CU_TEST(cpu == record->cpu);
	CU_TEST(event->id == fdata->sched_switch->id);
	return 0;
}

static int waking_callback(struct tep_event *event, struct tep_record *record,
			   int cpu, void *data)
{
	struct follow_data *fdata = data;

	CU_TEST(cpu == record->cpu);
	CU_TEST(event->id == fdata->sched_waking->id);
	return 0;
}

static int function_callback(struct tep_event *event, struct tep_record *record,
			     int cpu, void *data)
{
	struct follow_data *fdata = data;

	CU_TEST(cpu == record->cpu);
	CU_TEST(event->id == fdata->function->id);
	return 0;
}

static int missed_callback(struct tep_event *event, struct tep_record *record,
			     int cpu, void *data)
{
	struct follow_data *fdata = data;

	fdata->missed = record->missed_events;
	return 0;
}

static int all_callback(struct tep_event *event, struct tep_record *record,
			int cpu, void *data)
{
	struct follow_data *fdata = data;

	CU_TEST(fdata->missed == record->missed_events);
	fdata->missed = 0;
	return 0;
}

static void *stop_thread(void *arg)
{
	struct tracefs_instance *instance = arg;

	sleep(1);
	tracefs_iterate_stop(instance);
	return NULL;
}

static void test_instance_follow_events(struct tracefs_instance *instance)
{
	struct follow_data fdata;
	struct tep_handle *tep;
	pthread_t thread;
	int ret;

	memset(&fdata, 0, sizeof(fdata));

	tep = tracefs_local_events(NULL);
	CU_TEST(tep != NULL);
	if (!tep)
		return;

	fdata.sched_switch = tep_find_event_by_name(tep, "sched", "sched_switch");
	CU_TEST(fdata.sched_switch != NULL);
	if (!fdata.sched_switch)
		return;

	fdata.sched_waking = tep_find_event_by_name(tep, "sched", "sched_waking");
	CU_TEST(fdata.sched_waking != NULL);
	if (!fdata.sched_waking)
		return;

	fdata.function = tep_find_event_by_name(tep, "ftrace", "function");
	CU_TEST(fdata.function != NULL);
	if (!fdata.function)
		return;

	ret = tracefs_follow_event(tep, instance, "sched", "sched_switch",
				   switch_callback, &fdata);
	CU_TEST(ret == 0);

	ret = tracefs_follow_event(tep, instance, "sched", "sched_waking",
				   waking_callback, &fdata);
	CU_TEST(ret == 0);

	ret = tracefs_follow_event(tep, instance, "ftrace", "function",
				   function_callback, &fdata);
	CU_TEST(ret == 0);

	ret = tracefs_follow_missed_events(instance, missed_callback, &fdata);
	CU_TEST(ret == 0);

	ret = tracefs_event_enable(instance, "sched", "sched_switch");
	CU_TEST(ret == 0);

	ret = tracefs_event_enable(instance, "sched", "sched_waking");
	CU_TEST(ret == 0);

	ret = tracefs_tracer_set(instance, TRACEFS_TRACER_FUNCTION);
	CU_TEST(ret == 0);

	pthread_create(&thread, NULL, stop_thread, instance);

	ret = tracefs_iterate_raw_events(tep, instance, NULL, 0, all_callback, &fdata);
	CU_TEST(ret == 0);

	pthread_join(thread, NULL);

	tracefs_tracer_clear(instance);
	tracefs_event_disable(instance, NULL, NULL);
}

static void test_follow_events(void)
{
	test_instance_follow_events(NULL);
	test_instance_follow_events(test_instance);
}

extern char *find_tracing_dir(bool debugfs, bool mount);
static void test_mounting(void)
{
	const char *tracing_dir;
	const char *debug_dir;
	struct stat st;
	char *save_tracing = NULL;
	char *save_debug = NULL;
	char *path;
	char *dir;
	int ret;

	/* First, unmount all instances of debugfs */
	do {
		dir = find_tracing_dir(true, false);
		if (dir) {
			ret = umount(dir);
			CU_TEST(ret == 0);
			if (ret < 0)
				return;
			/* Save the first instance that's not /sys/kernel/debug */
			if (!save_debug && strcmp(dir, DEBUGFS_DEFAULT_PATH) != 0)
				save_debug = dir;
			else
				free(dir);
		}
	} while (dir);

	/* Next, unmount all instances of tracefs */
	do {
		dir = find_tracing_dir(false, false);
		if (dir) {
			ret = umount(dir);
			CU_TEST(ret == 0);
			if (ret < 0)
				return;
			/* Save the first instance that's not in /sys/kernel/ */
			if (!save_tracing && strncmp(dir, "/sys/kernel/", 12) != 0)
				save_tracing = dir;
			else
				free(dir);
		}
	} while (dir);

	/* Mount first the tracing dir (which should mount at /sys/kernel/tracing */
	tracing_dir = tracefs_tracing_dir();
	CU_TEST(tracing_dir != NULL);
	if (tracing_dir != NULL) {
		CU_TEST(strcmp(tracing_dir, TRACEFS_DEFAULT_PATH) == 0 ||
			strcmp(tracing_dir, TRACEFS_DEFAULT2_PATH) == 0);
		if (strncmp(tracing_dir, "/sys/kernel/", 12) != 0)
			printf("Tracing directory mounted at '%s'\n",
			       tracing_dir);

		/* Make sure the directory has content.*/
		asprintf(&path, "%s/trace", tracing_dir);
		CU_TEST(stat(path, &st) == 0);
		free(path);
	}

	/* Now mount debugfs dir, which should mount at /sys/kernel/debug */
	debug_dir = tracefs_debug_dir();
	CU_TEST(debug_dir != NULL);
	if (debug_dir != NULL) {
		CU_TEST(strcmp(debug_dir, DEBUGFS_DEFAULT_PATH) == 0);
		if (strcmp(debug_dir, DEBUGFS_DEFAULT_PATH) != 0)
			printf("debug directory mounted at '%s'\n",
			       debug_dir);

		/* Make sure the directory has content.*/
		asprintf(&path, "%s/tracing", debug_dir);
		CU_TEST(stat(path, &st) == 0);
		free(path);
	}

	if (save_debug)
		mount("debugfs", save_debug, "debugfs", 0, NULL);

	if (save_tracing &&
	    (!save_debug || strncmp(save_debug, save_tracing, strlen(save_debug)) != 0))
		mount("tracefs", save_tracing, "tracefs", 0, NULL);

	free(save_debug);
	free(save_tracing);
}

static int read_trace_cpu_file(struct test_cpu_data *data)
{
	unsigned long long ts;
	struct tep_record record;
	struct kbuffer *kbuf = data->kbuf;
	void *buf = data->buf;
	bool first = true;
	int bufsize = data->bufsize;
	int fd = data->fd;
	int missed;
	int pid;
	int ret;
	int cnt = 0;

	ret = lseek64(fd, 0, SEEK_SET);
	CU_TEST(ret == 0);
	if (ret)
		return -1;

	for (;;) {
		ret = read(fd, buf, bufsize);
		CU_TEST(ret > 0 || !first);
		if (ret <= 0)
			break;
		first = false;

		ret = kbuffer_load_subbuffer(kbuf, buf);
		CU_TEST(ret == 0);
		missed = kbuffer_missed_events(kbuf);
		if (missed)
			printf("missed events %d\n", missed);
		for (;;) {
			record.data = kbuffer_read_event(kbuf, &ts);
			if (!record.data)
				break;
			record.ts = ts;
			pid = tep_data_pid(data->tep, &record);
			if (pid == data->this_pid)
				cnt++;
			kbuffer_next_event(kbuf, NULL);
		}
	}
	return ret == 0 ? cnt : ret;
}

static void *trace_cpu_thread(void *arg)
{
	struct test_cpu_data *data = arg;
	struct tracefs_cpu *tcpu = data->tcpu;
	int fd = data->fd;
	long ret = 0;

	thread_affinity();

	while (!data->done && ret >= 0) {
		ret = tracefs_cpu_write(tcpu, fd, false);
		if (ret < 0 && errno == EAGAIN)
			ret = 0;
	}
	if (ret >= 0 || errno == EAGAIN) {
		do {
			ret = tracefs_cpu_flush_write(tcpu, fd);
		} while (ret > 0);
	}

	return (void *)ret;
}

static void test_cpu_pipe(struct test_cpu_data *data, int expect)
{
	pthread_t thread;
	void *retval;
	long ret;
	int cnt;

	tracefs_instance_file_clear(data->instance, "trace");
	ftruncate(data->fd, 0);

	data->done = false;

	pthread_create(&thread, NULL, trace_cpu_thread, data);
	sleep(1);

	call_getppid(expect);

	data->done = true;
	tracefs_cpu_stop(data->tcpu);
	pthread_join(thread, &retval);
	ret = (long)retval;
	CU_TEST(ret >= 0);

	cnt = read_trace_cpu_file(data);

	CU_TEST(cnt == expect);
}

static void test_instance_trace_cpu_pipe(struct tracefs_instance *instance)
{
	struct test_cpu_data data;

	if (setup_trace_cpu(instance, &data))
		return;

	test_cpu_pipe(&data, 1);
	test_cpu_pipe(&data, data.events_per_buf / 2);
	test_cpu_pipe(&data, data.events_per_buf);
	test_cpu_pipe(&data, data.events_per_buf + 1);
	test_cpu_pipe(&data, data.events_per_buf * 1000);

	shutdown_trace_cpu(&data);
}

static void test_trace_cpu_pipe(void)
{
	test_instance_trace_cpu_pipe(NULL);
	test_instance_trace_cpu_pipe(test_instance);
}

static struct tracefs_dynevent **get_dynevents_check(enum tracefs_dynevent_type types, int count)
{
	struct tracefs_dynevent **devents;
	int i;

	devents = tracefs_dynevent_get_all(types, NULL);
	if (count) {
		CU_TEST(devents != NULL);
		if (!devents)
			return NULL;
		i = 0;
		while (devents[i])
			i++;
		CU_TEST(i == count);
	} else {
		CU_TEST(devents == NULL);
	}

	return devents;
}


struct test_synth {
	char *name;
	char *start_system;
	char *start_event;
	char *end_system;
	char *end_event;
	char *start_match_field;
	char *end_match_field;
	char *match_name;
};

static void test_synth_compare(struct test_synth *synth, struct tracefs_dynevent **devents)
{
	enum tracefs_dynevent_type stype;
	char *format;
	char *event;
	int i;

	for (i = 0; devents && devents[i]; i++) {
		stype = tracefs_dynevent_info(devents[i], NULL,
					      &event, NULL, NULL, &format);
		CU_TEST(stype == TRACEFS_DYNEVENT_SYNTH);
		CU_TEST(strcmp(event, synth[i].name) == 0);
		if (synth[i].match_name) {
			CU_TEST(strstr(format, synth[i].match_name) != NULL);
		}
		free(event);
		free(format);
	}
	CU_TEST(devents == NULL || devents[i] == NULL);
}

static void test_instance_synthetic(struct tracefs_instance *instance)
{
	struct test_synth sevents[] = {
		{"synth_1", "sched", "sched_waking", "sched", "sched_switch", "pid", "next_pid", "pid_match"},
		{"synth_2", "syscalls", "sys_enter_openat2", "syscalls", "sys_exit_openat2", "__syscall_nr", "__syscall_nr", "nr_match"},
	};
	int sevents_count = sizeof(sevents) / sizeof((sevents)[0]);
	struct tracefs_dynevent **devents;
	struct tracefs_synth **synth;
	struct tep_handle *tep;
	int ret;
	int i;

	synth = calloc(sevents_count + 1, sizeof(*synth));

	tep = tracefs_local_events(NULL);
	CU_TEST(tep != NULL);

	/* kprobes APIs */
	ret = tracefs_dynevent_destroy_all(TRACEFS_DYNEVENT_SYNTH, true);
	CU_TEST(ret == 0);
	get_dynevents_check(TRACEFS_DYNEVENT_SYNTH, 0);

	for (i = 0; i < sevents_count; i++) {
		synth[i] = tracefs_synth_alloc(tep,  sevents[i].name,
					       sevents[i].start_system, sevents[i].start_event,
					       sevents[i].end_system, sevents[i].end_event,
					       sevents[i].start_match_field, sevents[i].end_match_field,
					       sevents[i].match_name);
		CU_TEST(synth[i] != NULL);
	}

	get_dynevents_check(TRACEFS_DYNEVENT_SYNTH, 0);

	for (i = 0; i < sevents_count; i++) {
		ret = tracefs_synth_create(synth[i]);
		CU_TEST(ret == 0);
	}

	devents = get_dynevents_check(TRACEFS_DYNEVENT_SYNTH, sevents_count);
	CU_TEST(devents != NULL);
	test_synth_compare(sevents, devents);
	tracefs_dynevent_list_free(devents);

	for (i = 0; i < sevents_count; i++) {
		ret = tracefs_synth_destroy(synth[i]);
		CU_TEST(ret == 0);
	}

	get_dynevents_check(TRACEFS_DYNEVENT_SYNTH, 0);

	for (i = 0; i < sevents_count; i++)
		tracefs_synth_free(synth[i]);

	tep_free(tep);
	free(synth);
}

static void test_synthetic(void)
{
	test_instance_synthetic(test_instance);
}

static void test_trace_file(void)
{
	const char *tmp = get_rand_str();
	const char *tdir;
	struct stat st;
	char *file;

	tdir  = tracefs_tracing_dir();
	CU_TEST(tdir != NULL);
	CU_TEST(stat(tdir, &st) == 0);
	CU_TEST(S_ISDIR(st.st_mode));

	file = tracefs_get_tracing_file(NULL);
	CU_TEST(file == NULL);
	file = tracefs_get_tracing_file(tmp);
	CU_TEST(file != NULL);
	CU_TEST(stat(file, &st) != 0);
	tracefs_put_tracing_file(file);

	file = tracefs_get_tracing_file("trace");
	CU_TEST(file != NULL);
	CU_TEST(stat(file, &st) == 0);
	tracefs_put_tracing_file(file);
}

static void test_instance_file_read(struct tracefs_instance *inst, const char *fname)
{
	const char *tdir  = tracefs_tracing_dir();
	char buf[BUFSIZ];
	char *fpath;
	char *file;
	size_t fsize = 0;
	int size = 0;
	int fd;

	if (inst) {
		CU_TEST(asprintf(&fpath, "%s/instances/%s/%s",
			tdir, tracefs_instance_get_name(inst), fname) > 0);
	} else {
		CU_TEST(asprintf(&fpath, "%s/%s", tdir, fname) > 0);
	}

	memset(buf, 0, BUFSIZ);
	fd = open(fpath, O_RDONLY);
	CU_TEST(fd >= 0);
	fsize = read(fd, buf, BUFSIZ);
	CU_TEST(fsize >= 0);
	close(fd);
	buf[BUFSIZ - 1] = 0;

	file = tracefs_instance_file_read(inst, fname, &size);
	CU_TEST(file != NULL);
	CU_TEST(size == fsize);
	CU_TEST(strcmp(file, buf) == 0);

	free(fpath);
	free(file);
}

struct probe_test {
	enum tracefs_dynevent_type type;
	char *prefix;
	char *system;
	char *event;
	char *address;
	char *format;
};

static bool check_probes(struct probe_test *probes, int count,
			 struct tracefs_dynevent **devents, bool in_system,
			 struct tracefs_instance *instance, struct tep_handle *tep)
{
	enum tracefs_dynevent_type type;
	struct tep_event *tevent;
	char *ename;
	char *address;
	char *event;
	char *system;
	char *format;
	char *prefix;
	int found = 0;
	int ret;
	int i, j;

	for (i = 0; devents && devents[i]; i++) {
		type = tracefs_dynevent_info(devents[i], &system,
					     &event, &prefix, &address, &format);
		for (j = 0; j < count; j++) {
			if (type != probes[j].type)
				continue;
			if (probes[j].event)
				ename = probes[j].event;
			else
				ename = probes[j].address;
			if (strcmp(ename, event))
				continue;
			if (probes[j].system) {
				CU_TEST(strcmp(probes[j].system, system) == 0);
			}
			CU_TEST(strcmp(probes[j].address, address) == 0);
			if (probes[j].format) {
				CU_TEST(strcmp(probes[j].format, format) == 0);
			}
			if (probes[j].prefix) {
				CU_TEST(strcmp(probes[j].prefix, prefix) == 0);
			}
			ret = tracefs_event_enable(instance, system, event);
			if (in_system) {
				CU_TEST(ret == 0);
			} else {
				CU_TEST(ret != 0);
			}
			ret = tracefs_event_disable(instance, system, event);
			if (in_system) {
				CU_TEST(ret == 0);
			} else {
				CU_TEST(ret != 0);
			}

			tevent =  tracefs_dynevent_get_event(tep, devents[i]);
			if (in_system) {
				CU_TEST(tevent != NULL);
				if (tevent) {
					CU_TEST(strcmp(tevent->name, event) == 0);
					CU_TEST(strcmp(tevent->system, system) == 0);
				}
			} else {
				CU_TEST(tevent == NULL);
			}

			found++;
			break;
		}
		free(system);
		free(event);
		free(prefix);
		free(address);
		free(format);
	}

	CU_TEST(found == count);
	if (found != count)
		return false;

	return true;
}

static void test_kprobes_instance(struct tracefs_instance *instance)
{
	struct probe_test ktests[] = {
		{ TRACEFS_DYNEVENT_KPROBE, "p", NULL, "mkdir", "do_mkdirat", "path=+u0($arg2):ustring" },
		{ TRACEFS_DYNEVENT_KPROBE, "p", NULL, "close", "close_fd", NULL },
		{ TRACEFS_DYNEVENT_KPROBE, "p", "ptest", "open2", "do_sys_openat2",
				  "file=+u0($arg2):ustring flags=+0($arg3):x64" },
	};
	struct probe_test kretests[] = {
		{ TRACEFS_DYNEVENT_KRETPROBE, NULL, NULL, "retopen", "do_sys_openat2", "ret=$retval" },
		{ TRACEFS_DYNEVENT_KRETPROBE, NULL, NULL, NULL, "do_sys_open", "ret=$retval" },
	};
	int kretprobe_count = sizeof(kretests) / sizeof((kretests)[0]);
	int kprobe_count = sizeof(ktests) / sizeof((ktests)[0]);
	struct tracefs_dynevent **dkretprobe;
	struct tracefs_dynevent **dkprobe;
	struct tracefs_dynevent **devents;
	struct tep_handle *tep;
	char *tmp;
	int ret;
	int i;

	tep = tep_alloc();
	CU_TEST(tep != NULL);

	dkprobe = calloc(kprobe_count + 1, sizeof(*dkprobe));
	dkretprobe = calloc(kretprobe_count + 1, sizeof(*dkretprobe));

	/* Invalid parameters */
	CU_TEST(tracefs_kprobe_alloc("test", NULL, NULL, "test") == NULL);
	CU_TEST(tracefs_kretprobe_alloc("test", NULL, NULL, "test", 0) == NULL);
	CU_TEST(tracefs_dynevent_create(NULL) != 0);
	CU_TEST(tracefs_dynevent_info(NULL, &tmp, &tmp, &tmp, &tmp, &tmp) == TRACEFS_DYNEVENT_UNKNOWN);
	CU_TEST(tracefs_kprobe_raw("test", "test", NULL, "test") != 0);
	CU_TEST(tracefs_kretprobe_raw("test", "test", NULL, "test") != 0);

	/* kprobes APIs */
	ret = tracefs_dynevent_destroy_all(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, true);
	CU_TEST(ret == 0);
	get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, 0);

	for (i = 0; i < kprobe_count; i++) {
		dkprobe[i] = tracefs_kprobe_alloc(ktests[i].system, ktests[i].event,
						  ktests[i].address, ktests[i].format);
		CU_TEST(dkprobe[i] != NULL);
	}
	dkprobe[i] = NULL;
	get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, 0);
	CU_TEST(check_probes(ktests, kprobe_count, dkprobe, false, instance, tep));

	for (i = 0; i < kretprobe_count; i++) {
		dkretprobe[i] = tracefs_kretprobe_alloc(kretests[i].system, kretests[i].event,
							kretests[i].address, kretests[i].format, 0);
		CU_TEST(dkretprobe[i] != NULL);
	}
	dkretprobe[i] = NULL;
	get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, 0);
	CU_TEST(check_probes(kretests, kretprobe_count, dkretprobe, false, instance, tep));

	for (i = 0; i < kprobe_count; i++) {
		CU_TEST(tracefs_dynevent_create(dkprobe[i]) == 0);
	}
	devents = get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE,
				    kprobe_count);
	CU_TEST(check_probes(ktests, kprobe_count, devents, true, instance, tep));
	CU_TEST(check_probes(kretests, kretprobe_count, dkretprobe, false, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	for (i = 0; i < kretprobe_count; i++) {
		CU_TEST(tracefs_dynevent_create(dkretprobe[i]) == 0);
	}
	devents = get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE,
				    kprobe_count + kretprobe_count);
	CU_TEST(check_probes(ktests, kprobe_count, devents, true, instance, tep));
	CU_TEST(check_probes(kretests, kretprobe_count, devents, true, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	for (i = 0; i < kretprobe_count; i++) {
		CU_TEST(tracefs_dynevent_destroy(dkretprobe[i], false) == 0);
	}
	devents = get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE,
				    kprobe_count);
	CU_TEST(check_probes(ktests, kprobe_count, devents, true, instance, tep));
	CU_TEST(check_probes(kretests, kretprobe_count, dkretprobe, false, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	for (i = 0; i < kprobe_count; i++) {
		CU_TEST(tracefs_dynevent_destroy(dkprobe[i], false) == 0);
	}
	get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, 0);
	CU_TEST(check_probes(ktests, kprobe_count, dkprobe, false, instance, tep));
	CU_TEST(check_probes(kretests, kretprobe_count, dkretprobe, false, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	for (i = 0; i < kprobe_count; i++)
		tracefs_dynevent_free(dkprobe[i]);
	for (i = 0; i < kretprobe_count; i++)
		tracefs_dynevent_free(dkretprobe[i]);

	/* kprobes raw APIs */
	ret = tracefs_dynevent_destroy_all(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, true);
	CU_TEST(ret == 0);
	get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, 0);

	for (i = 0; i < kprobe_count; i++) {
		ret = tracefs_kprobe_raw(ktests[i].system, ktests[i].event,
					 ktests[i].address, ktests[i].format);
		CU_TEST(ret == 0);
	}

	devents = get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, kprobe_count);
	CU_TEST(check_probes(ktests, kprobe_count, devents, true, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	for (i = 0; i < kretprobe_count; i++) {
		ret = tracefs_kretprobe_raw(kretests[i].system, kretests[i].event,
					    kretests[i].address, kretests[i].format);
		CU_TEST(ret == 0);
	}

	devents = get_dynevents_check(TRACEFS_DYNEVENT_KPROBE, kprobe_count);
	CU_TEST(check_probes(ktests, kprobe_count, devents, true, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	devents = get_dynevents_check(TRACEFS_DYNEVENT_KRETPROBE, kretprobe_count);
	CU_TEST(check_probes(kretests, kretprobe_count, devents, true, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	devents = get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE,
				    kprobe_count + kretprobe_count);
	CU_TEST(check_probes(ktests, kprobe_count, devents, true, instance, tep));
	CU_TEST(check_probes(kretests, kretprobe_count, devents, true, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	ret = tracefs_dynevent_destroy_all(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, true);
	CU_TEST(ret == 0);
	get_dynevents_check(TRACEFS_DYNEVENT_KPROBE | TRACEFS_DYNEVENT_KRETPROBE, 0);
	free(dkretprobe);
	free(dkprobe);
	tep_free(tep);
}

static void test_kprobes(void)
{
	test_kprobes_instance(test_instance);
}

static void test_eprobes_instance(struct tracefs_instance *instance)
{
	struct probe_test etests[] = {
		{ TRACEFS_DYNEVENT_EPROBE, "e", NULL, "sopen_in", "syscalls.sys_enter_openat",
					   "file=+0($filename):ustring" },
		{ TRACEFS_DYNEVENT_EPROBE, "e", "etest", "sopen_out", "syscalls.sys_exit_openat",
					   "res=$ret:u64" },
	};
	int count = sizeof(etests) / sizeof((etests)[0]);
	struct tracefs_dynevent **deprobes;
	struct tracefs_dynevent **devents;
	struct tep_handle *tep;
	char *tsys, *tevent;
	char *tmp, *sav;
	int ret;
	int i;

	tep = tep_alloc();
	CU_TEST(tep != NULL);

	deprobes = calloc(count + 1, sizeof(*deprobes));

	/* Invalid parameters */
	CU_TEST(tracefs_eprobe_alloc("test", NULL, "test", "test", "test") == NULL);
	CU_TEST(tracefs_eprobe_alloc("test", "test", NULL, "test", "test") == NULL);
	CU_TEST(tracefs_eprobe_alloc("test", "test", "test", NULL, "test") == NULL);

	ret = tracefs_dynevent_destroy_all(TRACEFS_DYNEVENT_EPROBE, true);
	CU_TEST(ret == 0);
	get_dynevents_check(TRACEFS_DYNEVENT_EPROBE, 0);

	for (i = 0; i < count; i++) {
		tmp = strdup(etests[i].address);
		tsys = strtok_r(tmp, "./", &sav);
		tevent = strtok_r(NULL, "", &sav);
		deprobes[i] = tracefs_eprobe_alloc(etests[i].system, etests[i].event,
						   tsys, tevent, etests[i].format);
		free(tmp);
		CU_TEST(deprobes[i] != NULL);
	}
	deprobes[i] = NULL;

	get_dynevents_check(TRACEFS_DYNEVENT_EPROBE, 0);
	CU_TEST(check_probes(etests, count, deprobes, false, instance, tep));

	for (i = 0; i < count; i++) {
		CU_TEST(tracefs_dynevent_create(deprobes[i]) == 0);
	}

	devents = get_dynevents_check(TRACEFS_DYNEVENT_EPROBE, count);
	CU_TEST(check_probes(etests, count, devents, true, instance, tep));
	tracefs_dynevent_list_free(devents);
	devents = NULL;

	for (i = 0; i < count; i++) {
		CU_TEST(tracefs_dynevent_destroy(deprobes[i], false) == 0);
	}
	get_dynevents_check(TRACEFS_DYNEVENT_EPROBE, 0);
	CU_TEST(check_probes(etests, count, deprobes, false, instance, tep));

	for (i = 0; i < count; i++)
		tracefs_dynevent_free(deprobes[i]);

	free(deprobes);
	tep_free(tep);
}

static void test_eprobes(void)
{
	test_eprobes_instance(test_instance);
}

#define FOFFSET 1000ll
static void test_uprobes_instance(struct tracefs_instance *instance)
{
	struct probe_test utests[] = {
		{ TRACEFS_DYNEVENT_UPROBE, "p", "utest", "utest_u", NULL, "arg1=$stack2" },
		{ TRACEFS_DYNEVENT_URETPROBE, "r", "utest", "utest_r", NULL, "arg1=$retval" },
	};
	int count = sizeof(utests) / sizeof((utests)[0]);
	struct tracefs_dynevent **duprobes;
	struct tracefs_dynevent **duvents;
	char self[PATH_MAX] = { 0 };
	struct tep_handle *tep;
	char *target = NULL;
	int i;

	tep = tep_alloc();
	CU_TEST(tep != NULL);

	duprobes = calloc(count + 1, sizeof(*duvents));
	CU_TEST(duprobes != NULL);
	CU_TEST(readlink("/proc/self/exe", self, sizeof(self)) > 0);
	CU_TEST(asprintf(&target, "%s:0x%0*llx", self, (int)(sizeof(void *) * 2), FOFFSET) > 0);

	for (i = 0; i < count; i++)
		utests[i].address = target;

	/* Invalid parameters */
	CU_TEST(tracefs_uprobe_alloc(NULL, NULL, self, 0, NULL) == NULL);
	CU_TEST(tracefs_uprobe_alloc(NULL, "test", NULL, 0, NULL) == NULL);
	CU_TEST(tracefs_uretprobe_alloc(NULL, NULL, self, 0, NULL) == NULL);
	CU_TEST(tracefs_uretprobe_alloc(NULL, "test", NULL, 0, NULL) == NULL);

	for (i = 0; i < count; i++) {
		if (utests[i].type == TRACEFS_DYNEVENT_UPROBE)
			duprobes[i] = tracefs_uprobe_alloc(utests[i].system, utests[i].event,
							   self, FOFFSET, utests[i].format);
		else
			duprobes[i] = tracefs_uretprobe_alloc(utests[i].system, utests[i].event,
							      self, FOFFSET, utests[i].format);
		CU_TEST(duprobes[i] != NULL);
	}
	duprobes[i] = NULL;

	get_dynevents_check(TRACEFS_DYNEVENT_UPROBE | TRACEFS_DYNEVENT_URETPROBE, 0);
	CU_TEST(check_probes(utests, count, duprobes, false, instance, tep));

	for (i = 0; i < count; i++) {
		CU_TEST(tracefs_dynevent_create(duprobes[i]) == 0);
	}

	duvents = get_dynevents_check(TRACEFS_DYNEVENT_UPROBE | TRACEFS_DYNEVENT_URETPROBE, count);
	CU_TEST(check_probes(utests, count, duvents, true, instance, tep));
	tracefs_dynevent_list_free(duvents);

	for (i = 0; i < count; i++) {
		CU_TEST(tracefs_dynevent_destroy(duprobes[i], false) == 0);
	}
	get_dynevents_check(TRACEFS_DYNEVENT_UPROBE | TRACEFS_DYNEVENT_URETPROBE, 0);
	CU_TEST(check_probes(utests, count, duprobes, false, instance, tep));

	for (i = 0; i < count; i++)
		tracefs_dynevent_free(duprobes[i]);

	free(duprobes);
	free(target);
	tep_free(tep);
}

static void test_uprobes(void)
{
	test_uprobes_instance(test_instance);
}

static void test_instance_file(void)
{
	struct tracefs_instance *instance = NULL;
	struct tracefs_instance *second = NULL;
	const char *name = get_rand_str();
	const char *inst_name = NULL;
	const char *tdir;
	char *inst_file;
	char *inst_dir;
	struct stat st;
	char *file1;
	char *file2;
	char *tracer;
	char *fname;
	int size;
	int ret;

	tdir  = tracefs_tracing_dir();
	CU_TEST(tdir != NULL);
	CU_TEST(asprintf(&inst_dir, "%s/instances/%s", tdir, name) > 0);
	CU_TEST(stat(inst_dir, &st) != 0);

	CU_TEST(tracefs_instance_exists(name) == false);
	instance = tracefs_instance_create(name);
	CU_TEST(instance != NULL);
	CU_TEST(tracefs_instance_is_new(instance));
	second = tracefs_instance_create(name);
	CU_TEST(second != NULL);
	CU_TEST(!tracefs_instance_is_new(second));
	tracefs_instance_free(second);
	CU_TEST(tracefs_instance_exists(name) == true);
	CU_TEST(stat(inst_dir, &st) == 0);
	CU_TEST(S_ISDIR(st.st_mode));
	inst_name = tracefs_instance_get_name(instance);
	CU_TEST(inst_name != NULL);
	CU_TEST(strcmp(inst_name, name) == 0);

	fname = tracefs_instance_get_dir(NULL);
	CU_TEST(fname != NULL);
	CU_TEST(strcmp(fname, tdir) == 0);
	free(fname);

	fname = tracefs_instance_get_dir(instance);
	CU_TEST(fname != NULL);
	CU_TEST(strcmp(fname, inst_dir) == 0);
	free(fname);

	CU_TEST(asprintf(&fname, "%s/"ALL_TRACERS, tdir) > 0);
	CU_TEST(fname != NULL);
	inst_file = tracefs_instance_get_file(NULL, ALL_TRACERS);
	CU_TEST(inst_file != NULL);
	CU_TEST(strcmp(fname, inst_file) == 0);
	tracefs_put_tracing_file(inst_file);
	free(fname);

	CU_TEST(asprintf(&fname, "%s/instances/%s/"ALL_TRACERS, tdir, name) > 0);
	CU_TEST(fname != NULL);
	CU_TEST(stat(fname, &st) == 0);
	inst_file = tracefs_instance_get_file(instance, ALL_TRACERS);
	CU_TEST(inst_file != NULL);
	CU_TEST(strcmp(fname, inst_file) == 0);

	test_instance_file_read(NULL, ALL_TRACERS);
	test_instance_file_read(instance, ALL_TRACERS);

	file1 = tracefs_instance_file_read(instance, ALL_TRACERS, NULL);
	CU_TEST(file1 != NULL);
	tracer = strtok(file1, " ");
	CU_TEST(tracer != NULL);
	ret = tracefs_instance_file_write(instance, CUR_TRACER, tracer);
	CU_TEST(ret == strlen(tracer));
	file2 = tracefs_instance_file_read(instance, CUR_TRACER, &size);
	CU_TEST(file2 != NULL);
	CU_TEST(size >= strlen(tracer));
	CU_TEST(strncmp(file2, tracer, strlen(tracer)) == 0);
	free(file1);
	free(file2);

	tracefs_put_tracing_file(inst_file);
	free(fname);

	CU_TEST(tracefs_file_exists(NULL, (char *)name) == false);
	CU_TEST(tracefs_dir_exists(NULL, (char *)name) == false);
	CU_TEST(tracefs_file_exists(instance, (char *)name) == false);
	CU_TEST(tracefs_dir_exists(instance, (char *)name) == false);

	CU_TEST(tracefs_file_exists(NULL, CUR_TRACER) == true);
	CU_TEST(tracefs_dir_exists(NULL, CUR_TRACER) == false);
	CU_TEST(tracefs_file_exists(instance, CUR_TRACER) == true);
	CU_TEST(tracefs_dir_exists(instance, CUR_TRACER) == false);

	CU_TEST(tracefs_file_exists(NULL, PER_CPU) == false);
	CU_TEST(tracefs_dir_exists(NULL, PER_CPU) == true);
	CU_TEST(tracefs_file_exists(instance, PER_CPU) == false);
	CU_TEST(tracefs_dir_exists(instance, PER_CPU) == true);

	CU_TEST(tracefs_instance_destroy(NULL) != 0);
	CU_TEST(tracefs_instance_destroy(instance) == 0);
	CU_TEST(tracefs_instance_destroy(instance) != 0);
	tracefs_instance_free(instance);
	CU_TEST(stat(inst_dir, &st) != 0);
	free(inst_dir);
}

static bool check_fd_name(int fd, const char *dir, const char *name)
{
	char link[PATH_MAX + 1];
	char path[PATH_MAX + 1];
	struct stat st;
	char *file;
	int ret;

	snprintf(link, PATH_MAX, "/proc/self/fd/%d", fd);
	ret = lstat(link, &st);
	CU_TEST(ret == 0);
	if (ret < 0)
		return false;
	CU_TEST(S_ISLNK(st.st_mode));
	if (!S_ISLNK(st.st_mode))
		return false;
	ret = readlink(link, path, PATH_MAX);
	CU_TEST(ret > 0);
	if (ret > PATH_MAX || ret < 0)
		return false;
	path[ret] = 0;
	ret = strncmp(dir, path, strlen(dir));
	CU_TEST(ret == 0);
	if (ret)
		return false;
	file = basename(path);
	CU_TEST(file != NULL);
	if (!file)
		return false;
	ret = strcmp(file, name);
	CU_TEST(ret == 0);
	if (ret)
		return false;
	return true;
}

#define FLAGS_STR	"flags:"
static bool check_fd_mode(int fd, int mode)
{
	char path[PATH_MAX + 1];
	long fmode = -1;
	char *line = NULL;
	struct stat st;
	size_t len = 0;
	ssize_t size;
	FILE *file;
	int ret;

	snprintf(path, PATH_MAX, "/proc/self/fdinfo/%d", fd);
	ret = stat(path, &st);
	CU_TEST(ret == 0);
	if (ret < 0)
		return false;
	file = fopen(path, "r");
	if (!file)
		return false;
	while ((size = getline(&line, &len, file)) > 0) {
		if (strncmp(line, FLAGS_STR, strlen(FLAGS_STR)))
			continue;
		fmode = strtol(line + strlen(FLAGS_STR), NULL, 8);
		break;
	}
	free(line);
	fclose(file);
	if (fmode < 0 ||
	    (O_ACCMODE & fmode) != (O_ACCMODE & mode))
		return false;
	return true;
}

static void test_instance_file_fd(struct tracefs_instance *instance)
{
	const char *name = get_rand_str();
	const char *tdir = tracefs_instance_get_trace_dir(instance);
	long long res = -1;
	char rd[2];
	int fd;

	CU_TEST(tdir != NULL);
	fd = tracefs_instance_file_open(instance, name, -1);
	CU_TEST(fd == -1);
	fd = tracefs_instance_file_open(instance, TRACE_ON, O_RDONLY);
	CU_TEST(fd >= 0);

	CU_TEST(check_fd_name(fd, tdir, TRACE_ON));
	CU_TEST(check_fd_mode(fd, O_RDONLY));

	CU_TEST(tracefs_instance_file_read_number(instance, ALL_TRACERS, &res) != 0);
	CU_TEST(tracefs_instance_file_read_number(instance, name, &res) != 0);
	CU_TEST(tracefs_instance_file_read_number(instance, TRACE_ON, &res) == 0);
	CU_TEST((res == 0 || res == 1));
	CU_TEST(read(fd, &rd, 1) == 1);
	rd[1] = 0;
	CU_TEST(res == atoi(rd));

	close(fd);
}

static void test_file_fd(void)
{
	test_instance_file_fd(test_instance);
}

static void test_instance_tracing_onoff(struct tracefs_instance *instance)
{
	const char *tdir = tracefs_instance_get_trace_dir(instance);
	long long res = -1;
	int fd;

	CU_TEST(tdir != NULL);
	fd = tracefs_trace_on_get_fd(instance);
	CU_TEST(fd >= 0);
	CU_TEST(check_fd_name(fd, tdir, TRACE_ON));
	CU_TEST(check_fd_mode(fd, O_RDWR));
	CU_TEST(tracefs_instance_file_read_number(instance, TRACE_ON, &res) == 0);
	if (res == 1) {
		CU_TEST(tracefs_trace_is_on(instance) == 1);
		CU_TEST(tracefs_trace_off(instance) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 0);
		CU_TEST(tracefs_trace_on(instance) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 1);

		CU_TEST(tracefs_trace_off_fd(fd) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 0);
		CU_TEST(tracefs_trace_on_fd(fd) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 1);
	} else {
		CU_TEST(tracefs_trace_is_on(instance) == 0);
		CU_TEST(tracefs_trace_on(instance) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 1);
		CU_TEST(tracefs_trace_off(instance) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 0);

		CU_TEST(tracefs_trace_on_fd(fd) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 1);
		CU_TEST(tracefs_trace_off_fd(fd) == 0);
		CU_TEST(tracefs_trace_is_on(instance) == 0);
	}

	if (fd >= 0)
		close(fd);
}

static void test_tracing_onoff(void)
{
	test_instance_tracing_onoff(test_instance);
}

static bool check_option(struct tracefs_instance *instance,
			 enum tracefs_option_id id, bool exist, int enabled)
{
	const char *name = tracefs_option_name(id);
	char file[PATH_MAX];
	char *path = NULL;
	bool ret = false;
	bool supported;
	struct stat st;
	char buf[10];
	int fd = 0;
	int r;
	int rstat;

	CU_TEST(name != NULL);
	supported = tracefs_option_is_supported(instance, id);
	CU_TEST(supported == exist);
	if (supported != exist)
		goto out;
	snprintf(file, PATH_MAX, "options/%s", name);
	path = tracefs_instance_get_file(instance, file);
	CU_TEST(path != NULL);
	rstat = stat(path, &st);
	if (exist) {
		CU_TEST(rstat == 0);
		if (rstat != 0)
			goto out;
	} else {
		CU_TEST(stat(path, &st) == -1);
		if (rstat != -1)
			goto out;
	}

	fd = open(path, O_RDONLY);
	if (exist) {
		CU_TEST(fd >= 0);
		if (fd < 0)
			goto out;
	} else {
		CU_TEST(fd < 0);
		if (fd >= 0)
			goto out;
	}

	if (exist && enabled >= 0) {
		int val = enabled ? '1' : '0';

		r = read(fd, buf, 10);
		CU_TEST(r >= 1);
		CU_TEST(buf[0] == val);
		if (buf[0] != val)
			goto out;
	}

	ret = true;
out:
	tracefs_put_tracing_file(path);
	if (fd >= 0)
		close(fd);
	return ret;
}

static void test_instance_tracing_options(struct tracefs_instance *instance)
{
	const struct tracefs_options_mask *enabled;
	const struct tracefs_options_mask *all_copy;
	const struct tracefs_options_mask *all;
	enum tracefs_option_id i = 1;
	char file[PATH_MAX];
	const char *name;

	all = tracefs_options_get_supported(instance);
	all_copy = tracefs_options_get_supported(instance);
	enabled = tracefs_options_get_enabled(instance);
	CU_TEST(all != NULL);

	/* Invalid parameters test */
	CU_TEST(!tracefs_option_is_supported(instance, TRACEFS_OPTION_INVALID));
	CU_TEST(!tracefs_option_is_enabled(instance, TRACEFS_OPTION_INVALID));
	CU_TEST(tracefs_option_enable(instance, TRACEFS_OPTION_INVALID) == -1);
	CU_TEST(tracefs_option_disable(instance, TRACEFS_OPTION_INVALID) == -1);
	name = tracefs_option_name(TRACEFS_OPTION_INVALID);
	CU_TEST(!strcmp(name, "unknown"));
	/* Test all valid options */
	for (i = 1; i < TRACEFS_OPTION_MAX; i++) {
		name = tracefs_option_name(i);
		CU_TEST(name != NULL);
		CU_TEST(strcmp(name, "unknown"));
		snprintf(file, PATH_MAX, "options/%s", name);

		if (tracefs_option_mask_is_set(all, i)) {
			CU_TEST(check_option(instance, i, true, -1));
			CU_TEST(tracefs_option_is_supported(instance, i));
		} else {
			CU_TEST(check_option(instance, i, false, -1));
			CU_TEST(!tracefs_option_is_supported(instance, i));
		}

		if (tracefs_option_mask_is_set(enabled, i)) {
			CU_TEST(check_option(instance, i, true, 1));
			CU_TEST(tracefs_option_is_supported(instance, i));
			CU_TEST(tracefs_option_is_enabled(instance, i));
			CU_TEST(tracefs_option_disable(instance, i) == 0);
			CU_TEST(check_option(instance, i, true, 0));
			CU_TEST(tracefs_option_enable(instance, i) == 0);
			CU_TEST(check_option(instance, i, true, 1));
		} else if (tracefs_option_mask_is_set(all_copy, i)) {
			CU_TEST(check_option(instance, i, true, 0));
			CU_TEST(tracefs_option_is_supported(instance, i));
			CU_TEST(!tracefs_option_is_enabled(instance, i));
			CU_TEST(tracefs_option_enable(instance, i) == 0);
			CU_TEST(check_option(instance, i, true, 1));
			CU_TEST(tracefs_option_disable(instance, i) == 0);
			CU_TEST(check_option(instance, i, true, 0));
		}
	}
}

static void test_tracing_options(void)
{
	test_instance_tracing_options(test_instance);
}

static void exclude_string(char **strings, char *name)
{
	int i;

	for (i = 0; strings[i]; i++) {
		if (strcmp(strings[i], name) == 0) {
			free(strings[i]);
			strings[i] = strdup("/");
			return;
		}
	}
}

static void test_check_files(const char *fdir, char **files)
{
	struct dirent *dent;
	DIR *dir;
	int i;

	dir = opendir(fdir);
	CU_TEST(dir != NULL);

	while ((dent = readdir(dir)))
		exclude_string(files, dent->d_name);

	closedir(dir);

	for (i = 0; files[i]; i++)
		CU_TEST(files[i][0] == '/');
}

static void system_event(const char *tdir)
{

	char **systems;
	char **events;
	char *sdir = NULL;

	systems = tracefs_event_systems(tdir);
	CU_TEST(systems != NULL);

	events = tracefs_system_events(tdir, systems[0]);
	CU_TEST(events != NULL);

	asprintf(&sdir, "%s/events/%s", tdir, systems[0]);
	CU_TEST(sdir != NULL);
	test_check_files(sdir, events);
	free(sdir);
	sdir = NULL;

	asprintf(&sdir, "%s/events", tdir);
	CU_TEST(sdir != NULL);
	test_check_files(sdir, systems);

	tracefs_list_free(systems);
	tracefs_list_free(events);

	free(sdir);
}

static void test_system_event(void)
{
	const char *tdir;

	tdir  = tracefs_tracing_dir();
	CU_TEST(tdir != NULL);
	system_event(tdir);
}

static void test_instance_tracers(struct tracefs_instance *instance)
{
	const char *tdir;
	char **tracers;
	char *tfile;
	char *tracer;
	int i;

	tdir  = tracefs_instance_get_trace_dir(instance);
	CU_TEST(tdir != NULL);

	tracers = tracefs_tracers(tdir);
	CU_TEST(tracers != NULL);

	for (i = 0; tracers[i]; i++)
		CU_TEST(tracefs_tracer_available(tdir, tracers[i]));

	tfile = tracefs_instance_file_read(NULL, ALL_TRACERS, NULL);

	tracer = strtok(tfile, " ");
	while (tracer) {
		exclude_string(tracers, tracer);
		tracer = strtok(NULL, " ");
	}

	for (i = 0; tracers[i]; i++)
		CU_TEST(tracers[i][0] == '/');

	tracefs_list_free(tracers);
	free(tfile);
}

static void test_tracers(void)
{
	test_instance_tracers(test_instance);
}

static void test_check_events(struct tep_handle *tep, char *system, bool exist)
{
	struct dirent *dent;
	char file[PATH_MAX];
	char buf[1024];
	char *edir = NULL;
	const char *tdir;
	DIR *dir;
	int fd;

	tdir  = tracefs_tracing_dir();
	CU_TEST(tdir != NULL);

	asprintf(&edir, "%s/events/%s", tdir, system);
	dir = opendir(edir);
	CU_TEST(dir != NULL);

	while ((dent = readdir(dir))) {
		if (dent->d_name[0] == '.')
			continue;
		sprintf(file, "%s/%s/id", edir, dent->d_name);
		fd = open(file, O_RDONLY);
		if (fd < 0)
			continue;
		CU_TEST(read(fd, buf, 1024) > 0);
		if (exist) {
			CU_TEST(tep_find_event(tep, atoi(buf)) != NULL);
		} else {
			CU_TEST(tep_find_event(tep, atoi(buf)) == NULL);
		}

		close(fd);
	}

	closedir(dir);
	free(edir);

}

static void local_events(const char *tdir)
{
	struct tep_handle *tep;
	char **systems;
	char *lsystems[3];
	int i;

	tep = tracefs_local_events(tdir);
	CU_TEST(tep != NULL);

	systems = tracefs_event_systems(tdir);
	CU_TEST(systems != NULL);

	for (i = 0; systems[i]; i++)
		test_check_events(tep, systems[i], true);
	tep_free(tep);

	memset(lsystems, 0, sizeof(lsystems));
	for (i = 0; systems[i]; i++) {
		if (!lsystems[0])
			lsystems[0] = systems[i];
		else if (!lsystems[2])
			lsystems[2] = systems[i];
		else
			break;
	}

	if (lsystems[0] && lsystems[2]) {
		tep = tracefs_local_events_system(tdir,
						  (const char * const *)lsystems);
		CU_TEST(tep != NULL);
		test_check_events(tep, lsystems[0], true);
		test_check_events(tep, lsystems[2], false);
	}
	tep_free(tep);

	tep = tep_alloc();
	CU_TEST(tep != NULL);
	CU_TEST(tracefs_fill_local_events(tdir, tep, NULL) == 0);
	for (i = 0; systems[i]; i++)
		test_check_events(tep, systems[i], true);

	tep_free(tep);

	tracefs_list_free(systems);
}

static void test_local_events(void)
{
	const char *tdir;

	tdir  = tracefs_tracing_dir();
	CU_TEST(tdir != NULL);
	local_events(tdir);
}

struct test_walk_instance {
	struct tracefs_instance *instance;
	bool found;
};
#define WALK_COUNT 10
int test_instances_walk_cb(const char *name, void *data)
{
	struct test_walk_instance *instances  = (struct test_walk_instance *)data;
	int i;

	CU_TEST(instances != NULL);
	CU_TEST(name != NULL);

	for (i = 0; i < WALK_COUNT; i++) {
		if (!strcmp(name,
			    tracefs_instance_get_name(instances[i].instance))) {
			instances[i].found = true;
			break;
		}
	}

	return 0;
}

static void test_instances_walk(void)
{
	struct test_walk_instance instances[WALK_COUNT];
	int i;

	memset(instances, 0, WALK_COUNT * sizeof(struct test_walk_instance));
	for (i = 0; i < WALK_COUNT; i++) {
		instances[i].instance = tracefs_instance_create(get_rand_str());
		CU_TEST(instances[i].instance != NULL);
	}

	CU_TEST(tracefs_instances_walk(test_instances_walk_cb, instances) == 0);
	for (i = 0; i < WALK_COUNT; i++) {
		CU_TEST(instances[i].found);
		tracefs_instance_destroy(instances[i].instance);
		instances[i].found = false;
	}

	CU_TEST(tracefs_instances_walk(test_instances_walk_cb, instances) == 0);
	for (i = 0; i < WALK_COUNT; i++) {
		CU_TEST(!instances[i].found);
		tracefs_instance_free(instances[i].instance);
	}
}

static void current_clock_check(struct tracefs_instance *instance, const char *clock)
{
	int size = 0;
	char *clocks;
	char *str;

	clocks = tracefs_instance_file_read(instance, TRACE_CLOCK, &size);
	CU_TEST_FATAL(clocks != NULL);
	CU_TEST(size > strlen(clock));
	str = strstr(clocks, clock);
	CU_TEST(str != NULL);
	CU_TEST(str != clocks);
	CU_TEST(*(str - 1) == '[');
	CU_TEST(*(str + strlen(clock)) == ']');
	free(clocks);
}

static void test_instance_get_clock(struct tracefs_instance *instance)
{
	const char *clock;

	clock = tracefs_get_clock(instance);
	CU_TEST_FATAL(clock != NULL);
	current_clock_check(instance, clock);
	free((char *)clock);
}

static void test_get_clock(void)
{
	test_instance_get_clock(test_instance);
}

static void copy_trace_file(const char *from, char *to)
{
	int fd_from = -1;
	int fd_to = -1;
	char buf[512];
	int ret;

	fd_from = open(from, O_RDONLY);
	if (fd_from < 0)
		goto out;
	fd_to = open(to, O_WRONLY | O_TRUNC | O_CREAT, S_IRWXU | S_IRWXG);
	if (fd_to < 0)
		goto out;

	while ((ret = read(fd_from, buf, 512)) > 0) {
		if (write(fd_to, buf, ret) == -1)
			break;
	}

out:
	if (fd_to >= 0)
		close(fd_to);
	if (fd_from >= 0)
		close(fd_from);
}

static int trace_dir_base;
static char *trace_tmp_dir;
static int copy_trace_walk(const char *fpath, const struct stat *sb,
			   int typeflag, struct FTW *ftwbuf)
{
	char path[PATH_MAX];

	sprintf(path, "%s%s", trace_tmp_dir, fpath + trace_dir_base);

	switch (typeflag) {
	case FTW_D:
		mkdir(path, 0750);
		break;
	case FTW_F:
		copy_trace_file(fpath, path);
		break;
	default:
		break;
	}
	return 0;
}

static void dup_trace_dir(char *to, char *dir)
{
	const char *trace_dir = tracefs_tracing_dir();
	char file_from[PATH_MAX];
	char file_to[PATH_MAX];

	sprintf(file_from, "%s/%s", trace_dir, dir);
	sprintf(file_to, "%s/%s", to, dir);
	trace_tmp_dir = file_to;
	trace_dir_base = strlen(file_from);
	nftw(file_from, copy_trace_walk, 20, 0);
}

static void dup_trace_file(char *to, char *file)
{
	const char *trace_dir = tracefs_tracing_dir();
	char file_from[PATH_MAX];
	char file_to[PATH_MAX];

	sprintf(file_from, "%s/%s", trace_dir, file);
	sprintf(file_to, "%s/%s", to, file);
	copy_trace_file(file_from, file_to);
}

static char *copy_trace_dir(void)
{
	char template[] = TEST_TRACE_DIR;
	char *dname = mkdtemp(template);

	dup_trace_dir(dname, "events");
	dup_trace_dir(dname, "options");
	dup_trace_file(dname, TRACE_ON);
	dup_trace_file(dname, CUR_TRACER);
	dup_trace_file(dname, TRACE_CLOCK);
	dup_trace_file(dname, ALL_TRACERS);

	return strdup(dname);
}

static int del_trace_walk(const char *fpath, const struct stat *sb,
			  int typeflag, struct FTW *ftwbuf)
{
	remove(fpath);
	return 0;
}

void del_trace_dir(char *dir)
{
	nftw(dir, del_trace_walk, 20, FTW_DEPTH);
}

static void test_custom_trace_dir(void)
{
	char *tdir = "/tmp/custom_tracefs";
	struct tracefs_instance *instance;
	char *dname = copy_trace_dir();
	const char *trace_dir;
	char *tfile;

	instance = tracefs_instance_alloc(dname, NULL);
	CU_TEST(instance != NULL);

	system_event(dname);
	local_events(dname);
	test_instance_tracing_options(instance);
	test_instance_get_clock(instance);
	test_instance_file_fd(instance);
	test_instance_tracers(instance);

	tracefs_instance_free(instance);
	del_trace_dir(dname);
	free(dname);

	trace_dir = tracefs_tracing_dir();
	CU_TEST(trace_dir != NULL);
	CU_TEST(tracefs_set_tracing_dir(tdir) == 0);
	CU_TEST(strcmp(tdir, tracefs_tracing_dir()) == 0);
	tfile = tracefs_get_tracing_file("trace");
	CU_TEST(tfile != NULL);
	CU_TEST(strcmp(tdir, dirname(tfile)) == 0);
	free(tfile);

	CU_TEST(tracefs_set_tracing_dir(NULL) == 0);
	CU_TEST(strcmp(trace_dir, tracefs_tracing_dir()) == 0);
	tfile = tracefs_get_tracing_file("trace");
	CU_TEST(tfile != NULL);
	CU_TEST(strcmp(trace_dir, dirname(tfile)) == 0);
	free(tfile);
}

static int test_suite_destroy(void)
{
	tracefs_instance_destroy(test_instance);
	tracefs_instance_free(test_instance);
	tep_free(test_tep);
	return 0;
}

static int test_suite_init(void)
{
	const char *systems[] = {"ftrace", NULL};

	test_tep = tracefs_local_events_system(NULL, systems);
	if (test_tep == NULL)
		return 1;
	test_instance = tracefs_instance_create(TEST_INSTANCE_NAME);
	if (!test_instance)
		return 1;

	return 0;
}

void test_tracefs_lib(void)
{
	CU_pSuite suite = NULL;

	suite = CU_add_suite(TRACEFS_SUITE, test_suite_init, test_suite_destroy);
	if (suite == NULL) {
		fprintf(stderr, "Suite \"%s\" cannot be ceated\n", TRACEFS_SUITE);
		return;
	}

	CU_add_test(suite, "Test tracefs/debugfs mounting", test_mounting);
	CU_add_test(suite, "trace cpu read",
		    test_trace_cpu_read);
	CU_add_test(suite, "trace cpu pipe",
		    test_trace_cpu_pipe);
	CU_add_test(suite, "trace sql",
		    test_trace_sql);
	CU_add_test(suite, "tracing file / directory APIs",
		    test_trace_file);
	CU_add_test(suite, "instance file / directory APIs",
		    test_file_fd);
	CU_add_test(suite, "instance file descriptor",
		    test_instance_file);
	CU_add_test(suite, "systems and events APIs",
		    test_system_event);
	CU_add_test(suite, "tracefs_iterate_raw_events API",
		    test_iter_raw_events);

	/* Follow events test must be after the iterate raw events above */
	CU_add_test(suite, "Follow events", test_follow_events);

	CU_add_test(suite, "tracefs_tracers API",
		    test_tracers);
	CU_add_test(suite, "tracefs_local events API",
		    test_local_events);
	CU_add_test(suite, "tracefs_instances_walk API",
		    test_instances_walk);
	CU_add_test(suite, "tracefs_get_clock API",
		    test_get_clock);
	CU_add_test(suite, "tracing on / off",
		    test_tracing_onoff);
	CU_add_test(suite, "tracing options",
		    test_tracing_options);
	CU_add_test(suite, "custom system directory",
		    test_custom_trace_dir);
	CU_add_test(suite, "ftrace marker",
		    test_ftrace_marker);
	CU_add_test(suite, "kprobes", test_kprobes);
	CU_add_test(suite, "synthetic events", test_synthetic);
	CU_add_test(suite, "eprobes", test_eprobes);
	CU_add_test(suite, "uprobes", test_uprobes);
}
