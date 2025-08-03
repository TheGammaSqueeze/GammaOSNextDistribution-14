/*
 *  Copyright 2018 Google, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <log/log_id.h>
#include <statslog.h>
#include <stdlib.h>
#include <string.h>
#include <stdlib.h>
#include <string.h>
#include <sys/endian.h>
#include <sys/param.h>
#include <sys/uio.h>
#include <time.h>
#include <unistd.h>

#include <string>

#include <lmkd.h>
#include <processgroup/processgroup.h>

#ifdef LMKD_LOG_STATS

#define STRINGIFY(x) STRINGIFY_INTERNAL(x)
#define STRINGIFY_INTERNAL(x) #x

/**
 * Used to make sure that the payload is always smaller than LMKD_REPLY_MAX_SIZE
 */
#define BUILD_BUG_ON(cond) ((void)sizeof(char[1 - 2 * !!(cond)]))

static bool enable_stats_log = property_get_bool("ro.lmk.log_stats", true);

struct proc {
    int pid;
    char taskname[MAX_TASKNAME_LEN];
    struct proc* pidhash_next;
};

#define PIDHASH_SZ 1024
static struct proc** pidhash = NULL;
#define pid_hashfn(x) ((((x) >> 8) ^ (x)) & (PIDHASH_SZ - 1))

static struct proc* pid_lookup(int pid) {
    struct proc* procp;

    if (!pidhash) return NULL;

    for (procp = pidhash[pid_hashfn(pid)]; procp && procp->pid != pid; procp = procp->pidhash_next)
        ;

    return procp;
}

static void memory_stat_parse_line(const char* line, struct memory_stat* mem_st) {
    char key[MAX_TASKNAME_LEN + 1];
    int64_t value;

    sscanf(line, "%" STRINGIFY(MAX_TASKNAME_LEN) "s  %" SCNd64 "", key, &value);

    if (strcmp(key, "total_") < 0) {
        return;
    }

    if (!strcmp(key, "total_pgfault"))
        mem_st->pgfault = value;
    else if (!strcmp(key, "total_pgmajfault"))
        mem_st->pgmajfault = value;
    else if (!strcmp(key, "total_rss"))
        mem_st->rss_in_bytes = value;
    else if (!strcmp(key, "total_cache"))
        mem_st->cache_in_bytes = value;
    else if (!strcmp(key, "total_swap"))
        mem_st->swap_in_bytes = value;
}

static int memory_stat_from_cgroup(struct memory_stat* mem_st, int pid, uid_t uid __unused) {
    std::string path;
    if (!CgroupGetAttributePathForTask("MemStats", pid, &path)) {
        ALOGE("Querying MemStats path failed");
        return -1;
    }

    FILE* fp = fopen(path.c_str(), "r");

    if (fp == NULL) {
        return -1;
    }

    char buf[LINE_MAX];
    while (fgets(buf, LINE_MAX, fp) != NULL) {
        memory_stat_parse_line(buf, mem_st);
    }
    fclose(fp);

    return 0;
}

static int memory_stat_from_procfs(struct memory_stat* mem_st, int pid) {
    char path[PATH_MAX];
    char buffer[PROC_STAT_BUFFER_SIZE];
    int fd, ret;

    snprintf(path, sizeof(path), PROC_STAT_FILE_PATH, pid);
    if ((fd = open(path, O_RDONLY | O_CLOEXEC)) < 0) {
        return -1;
    }

    ret = read(fd, buffer, sizeof(buffer));
    if (ret < 0) {
        close(fd);
        return -1;
    }
    close(fd);

    // field 10 is pgfault
    // field 12 is pgmajfault
    // field 22 is starttime
    int64_t pgfault = 0, pgmajfault = 0, starttime = 0;
    if (sscanf(buffer,
               "%*u %*s %*s %*d %*d %*d %*d %*d %*d %" SCNd64 " %*d "
               "%" SCNd64 " %*d %*u %*u %*d %*d %*d %*d %*d %*d "
               "%" SCNd64 "",
               &pgfault, &pgmajfault, &starttime) != 3) {
        return -1;
    }
    mem_st->pgfault = pgfault;
    mem_st->pgmajfault = pgmajfault;
    mem_st->process_start_time_ns = starttime * (NS_PER_SEC / sysconf(_SC_CLK_TCK));

    return 0;
}

struct memory_stat *stats_read_memory_stat(bool per_app_memcg, int pid, uid_t uid,
                                           int64_t rss_bytes, int64_t swap_bytes) {
    static struct memory_stat mem_st = {};
    if (!enable_stats_log) {
        return NULL;
    }

    if (per_app_memcg) {
        if (memory_stat_from_cgroup(&mem_st, pid, uid) == 0) {
            return &mem_st;
        }
    } else {
        if (memory_stat_from_procfs(&mem_st, pid) == 0) {
            mem_st.rss_in_bytes = rss_bytes;
            mem_st.swap_in_bytes = swap_bytes;
            return &mem_st;
        }
    }

    return NULL;
}

static void proc_insert(struct proc* procp) {
    if (!pidhash) {
        pidhash = static_cast<struct proc**>(calloc(PIDHASH_SZ, sizeof(*pidhash)));
    }

    int hval = pid_hashfn(procp->pid);
    procp->pidhash_next = pidhash[hval];
    pidhash[hval] = procp;
}

void stats_remove_taskname(int pid) {
    if (!enable_stats_log || !pidhash) {
        return;
    }

    int hval = pid_hashfn(pid);
    struct proc* procp;
    struct proc* prevp;

    for (procp = pidhash[hval], prevp = NULL; procp && procp->pid != pid;
         procp = procp->pidhash_next)
        prevp = procp;

    if (!procp)
        return;

    if (!prevp)
        pidhash[hval] = procp->pidhash_next;
    else
        prevp->pidhash_next = procp->pidhash_next;

    free(procp);
}

void stats_store_taskname(int pid, const char* taskname) {
    if (!enable_stats_log || !taskname) {
        return;
    }

    struct proc* procp = pid_lookup(pid);
    if (procp != NULL) {
        if (strcmp(procp->taskname, taskname) == 0) {
            return;
        }
        stats_remove_taskname(pid);
    }
    procp = static_cast<struct proc*>(malloc(sizeof(struct proc)));
    procp->pid = pid;
    strncpy(procp->taskname, taskname, MAX_TASKNAME_LEN - 1);
    procp->taskname[MAX_TASKNAME_LEN - 1] = '\0';
    proc_insert(procp);
}

void stats_purge_tasknames() {
    if (!enable_stats_log || !pidhash) {
        return;
    }

    struct proc* procp;
    struct proc* next;
    int i;
    for (i = 0; i < PIDHASH_SZ; i++) {
        procp = pidhash[i];
        while (procp) {
            next = procp->pidhash_next;
            free(procp);
            procp = next;
        }
    }
    memset(pidhash, 0, PIDHASH_SZ * sizeof(*pidhash));
}

const char* stats_get_task_name(int pid) {
    struct proc* proc = pid_lookup(pid);
    return proc ? proc->taskname : NULL;
}

/**
 * Writes int32 in a machine independent way
 * https://docs.oracle.com/javase/7/docs/api/java/io/DataOutput.html#writeInt(int)
 */
static inline size_t pack_int32(LMK_KILL_OCCURRED_PACKET packet,
                                size_t index,
                                int32_t value) {
    int32_t* int_buffer = (int32_t*)(packet + index);

    *int_buffer = htonl(value);

    return index + sizeof(int32_t);
}

/**
 * Writes int64 in a machine independent way
 * https://docs.oracle.com/javase/7/docs/api/java/io/DataOutput.html#writeLong(long)
 */
static inline size_t pack_int64(LMK_KILL_OCCURRED_PACKET packet,
                                size_t index,
                                int64_t value) {
    int64_t* int64_buffer = (int64_t*)(packet + index);

    *int64_buffer = htonq(value);

    return index + sizeof(int64_t);
}

/**
 * Writes ANSI string in a machine independent way
 * https://docs.oracle.com/javase/7/docs/api/java/io/DataOutput.html#writeShort(int)
 * 2 bytes str len following n chars
 * to be read on the Java side with
 * https://docs.oracle.com/javase/7/docs/api/java/io/DataInput.html#readUTF()
 * Truncates the value string & packs up to MAX_TASKNAME_LEN - 1 chars
 */
static inline size_t pack_string(LMK_KILL_OCCURRED_PACKET packet,
                                 size_t index,
                                 const char* value) {
    const size_t len_proc_name = MIN(strlen(value), MAX_TASKNAME_LEN - 1);
    int16_t* short_buffer = (int16_t*)(packet + index);
    *short_buffer = htons((int16_t)len_proc_name);

    char* byte_buffer = (char*)(short_buffer + 1);
    strncpy(byte_buffer, value, MAX_TASKNAME_LEN - 1);
    byte_buffer[MAX_TASKNAME_LEN - 1] = '\0';

    return index + sizeof(int16_t) + len_proc_name + 1;
}

size_t lmkd_pack_set_kill_occurred(LMK_KILL_OCCURRED_PACKET packet,
                                   struct kill_stat *kill_stat,
                                   struct memory_stat *mem_stat) {
    BUILD_BUG_ON(sizeof(LMK_KILL_OCCURRED_PACKET) > LMKD_REPLY_MAX_SIZE);

    if (!enable_stats_log) {
        return 0;
    }

    int32_t index = 0;
    index = pack_int32(packet, index, LMK_STAT_KILL_OCCURRED);

    if (mem_stat) {
        index = pack_int64(packet, index, mem_stat->pgfault);
        index = pack_int64(packet, index, mem_stat->pgmajfault);
        index = pack_int64(packet, index, mem_stat->rss_in_bytes);
        index = pack_int64(packet, index, mem_stat->cache_in_bytes);
        index = pack_int64(packet, index, mem_stat->swap_in_bytes);
        index = pack_int64(packet, index, mem_stat->process_start_time_ns);
    } else {
        index = pack_int64(packet, index, -1);
        index = pack_int64(packet, index, -1);
        index = pack_int64(packet, index, -1);
        index = pack_int64(packet, index, -1);
        index = pack_int64(packet, index, -1);
        index = pack_int64(packet, index, -1);
    }

    index = pack_int32(packet, index, kill_stat->uid);
    index = pack_int32(packet, index, kill_stat->oom_score);
    index = pack_int32(packet, index, kill_stat->min_oom_score);
    index = pack_int32(packet, index, (int)kill_stat->free_mem_kb);
    index = pack_int32(packet, index, (int)kill_stat->free_swap_kb);
    index = pack_int32(packet, index, (int)kill_stat->kill_reason);
    index = pack_int32(packet, index, kill_stat->thrashing);
    index = pack_int32(packet, index, kill_stat->max_thrashing);

    index = pack_string(packet, index, kill_stat->taskname);
    return index;
}

#endif /* LMKD_LOG_STATS */
